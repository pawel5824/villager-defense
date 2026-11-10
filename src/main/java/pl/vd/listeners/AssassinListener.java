package pl.vd.listeners;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import pl.vd.VillagerDefensePlugin;
import pl.vd.arena.Arena;
import pl.vd.arena.ArenaState;
import pl.vd.kits.KitManager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Obsługa kitu Assassin (Asasyn):
 * - shuriken (śnieżka) zadaje 3.5 obrażeń przy trafieniu moba (zeruje i-frame'y moba,
 *   więc każda trafiona śnieżka zadaje pełne obrażenia niezależnie od odstępu),
 * - co 0.8 s gracz odzyskuje 1 shuriken, ale maksymalnie może mieć 5 w ekwipunku,
 * - dash: 2 razy naciśnięcie W w odstępie 0.1–1.0 s → pęd 4 kratki do przodu
 *   (seria małych teleportów, 0.5 bloka/tick — omija serwerowe kontrole ruchu,
 *   które cofały gracza przy daszu przez setVelocity), cooldown 5 s; brak drugiego
 *   naciśnięcia w ciągu 1 s anuluje umiejętność.
 */
public class AssassinListener implements Listener {

    private static final double SHURIKEN_DAMAGE = 3.5;
    private static final long REGEN_TICKS = 16L; // 0.8 s

    // --- Dash ---
    private static final double DASH_DISTANCE = 4.0;
    // krok dashu w blokach na tick — 4 bloki w 8 tickach (0.4 s). Dash robimy serią
    // MAŁYCH TELEPORTÓW (nie prędkością): serwerowe kontrole ruchu (spigot.yml:
    // moved-wrongly-threshold / moved-too-quickly-multiplier) cofają gracza, gdy ruch
    // przez setVelocity jest za szybki — teleport to pozycja autorytatywna, więc omija je.
    private static final double DASH_STEP_PER_TICK = 0.5;
    private static final long DASH_COOLDOWN_MS = 5000L;
    private static final long TAP_MIN_MS = 100L;
    private static final long TAP_MAX_MS = 1000L; // drugie W można nacisnąć do 1 s po pierwszym
    private static final long TAP_CANCEL_TICKS = 20L; // 1.0 s (anulowanie „uzbrojonego” tapa)

    private final VillagerDefensePlugin plugin;

    private final Map<UUID, Long> taps = new HashMap<>();
    private final Map<UUID, Boolean> movingForward = new HashMap<>();
    private final Map<UUID, Long> lastDash = new HashMap<>();
    // gracze aktualnie w trakcie dashu (ruch z pędu nie może być nowym naciśnięciem W)
    private final Set<UUID> dashing = new HashSet<>();

    public AssassinListener(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::regenShurikens, REGEN_TICKS, REGEN_TICKS);
    }

    @EventHandler
    public void onSnowballHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball snowball)) return;
        if (!(snowball.getShooter() instanceof Player shooter)) return;
        if (shooter.getGameMode() == org.bukkit.GameMode.SPECTATOR) return; // duch nie rzuca shurikenami
        if (!isAssassinInGame(shooter)) return;
        if (!(event.getHitEntity() instanceof LivingEntity target)) return;
        if (target instanceof Player || target instanceof Villager) return;

        // źródłem jest śnieżka (pocisk), żeby CritListener traktował to jako obrażenia dystansowe.
        // Zerujemy chwilową odporność moba (i-frame'y), żeby KAŻDA trafiona śnieżka zadawała
        // pełne obrażenia — nawet przy bardzo szybkim rzucaniu wszystkich shurikenów naraz.
        target.setNoDamageTicks(0);
        target.damage(SHURIKEN_DAMAGE, snowball);
    }

    // ---------- Dash: wykrywanie podwójnego naciśnięcia W ----------

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!isAssassinInGame(player)) return;
        UUID id = player.getUniqueId();

        // ruch wynikający z pędu dashu nie jest naciśnięciem W
        if (dashing.contains(id)) {
            movingForward.put(id, true);
            return;
        }

        // teleporty (np. z innego źródła) mają duży skok — pomijamy je
        if (event.getFrom().getWorld() != event.getTo().getWorld()
                || event.getFrom().distanceSquared(event.getTo()) > 1.0) {
            movingForward.remove(id);
            return;
        }

        boolean forward = isMovingForward(player, event.getFrom(), event.getTo());
        boolean wasForward = movingForward.getOrDefault(id, false);
        movingForward.put(id, forward);

        // tylko przejście "stał → ruszył do przodu" liczy się jako naciśnięcie W
        if (forward && !wasForward) {
            onWPress(player);
        }
    }

    /** Dodatkowy sygnał: sprint (podwójne W w vanilla) potwierdza trwający tap. */
    @EventHandler
    public void onSprintToggle(PlayerToggleSprintEvent event) {
        if (!event.isSprinting()) return;
        Player player = event.getPlayer();
        if (!isAssassinInGame(player)) return;
        if (dashing.contains(player.getUniqueId())) return; // w trakcie dashu - nie odpalaj drugiego
        Long first = taps.get(player.getUniqueId());
        if (first == null) return;
        long elapsed = System.currentTimeMillis() - first;
        if (elapsed >= TAP_MIN_MS && elapsed <= TAP_MAX_MS) {
            taps.remove(player.getUniqueId());
            tryDash(player);
        }
    }

    private void onWPress(Player player) {
        long now = System.currentTimeMillis();
        UUID id = player.getUniqueId();
        Long first = taps.get(id);
        if (first == null) {
            taps.put(id, now);
            scheduleTapCancel(id, now);
            return;
        }
        long elapsed = now - first;
        if (elapsed >= TAP_MIN_MS && elapsed <= TAP_MAX_MS) {
            // podwójne W w oknie 0.1–1.0 s → dash!
            taps.remove(id);
            tryDash(player);
        } else if (elapsed > TAP_MAX_MS) {
            // poprzedni tap wygasł — ten jest nowym pierwszym tapem
            taps.put(id, now);
            scheduleTapCancel(id, now);
        }
        // elapsed < TAP_MIN_MS: za szybko, ignorujemy (drgania)
    }

    /** Anuluje „uzbrojony” tap po 1 s, jeśli nie było drugiego naciśnięcia. */
    private void scheduleTapCancel(UUID id, long tapTime) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (taps.getOrDefault(id, 0L) == tapTime) {
                taps.remove(id);
            }
        }, TAP_CANCEL_TICKS);
    }

    private boolean isMovingForward(Player player, Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double distSq = dx * dx + dz * dz;
        if (distSq < 0.0004) return false; // za mały ruch

        double yaw = Math.toRadians(to.getYaw());
        double fx = -Math.sin(yaw);
        double fz = Math.cos(yaw);
        double len = Math.sqrt(distSq);
        double dot = (dx * fx + dz * fz) / len;
        return dot > 0.5;
    }

    // ---------- Dash: wykonanie ----------

    private void tryDash(Player player) {
        if (dashing.contains(player.getUniqueId())) return; // już w trakcie dashu
        long now = System.currentTimeMillis();
        long cooldownUntil = lastDash.getOrDefault(player.getUniqueId(), 0L);
        if (now < cooldownUntil) {
            long secs = (cooldownUntil - now) / 1000L + 1;
            pl.vd.util.AntiSpam.send(player, "§cDash gotowy za " + secs + "s");
            return;
        }
        lastDash.put(player.getUniqueId(), now + DASH_COOLDOWN_MS);
        performDash(player);
    }

    private void performDash(Player player) {
        Location loc = player.getLocation();
        Vector dir = loc.getDirection().setY(0);
        if (dir.lengthSquared() < 0.0001) dir = new Vector(1, 0, 0);
        dir.normalize();

        // cel 2.5 bloka do przodu — sprawdzamy ściany co 0.5 bloka (jak wcześniej)
        Location target = loc.clone();
        double remaining = DASH_DISTANCE;
        double step = 0.5;
        while (remaining >= step) {
            Location next = target.clone().add(dir.clone().multiply(step));
            if (isBlocked(next) || isBlocked(next.clone().add(0, 1, 0))) break;
            target = next;
            remaining -= step;
        }
        if (loc.distanceSquared(target) < 0.01) return; // brak miejsca na dash
        Location finalTarget = target.clone(); // stała kopia dla lambdy (target zmienia się w pętli)

        // wyczyść stan tapa i oznacz gracza jako „w dashu” — ruch z dashu nie może
        // zostać zaliczony jako kolejne naciśnięcie W
        taps.remove(player.getUniqueId());
        dashing.add(player.getUniqueId());

        // dźwięk startu dashu - per gracz (ustawienie „Klasy" w Dźwiękach)
        plugin.getGameManager().playSoundNearby(loc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.6f, 1.3f,
                pl.vd.shop.ShopGUI.SOUND_ABILITIES_KEY);

        // DASH JAKO SERIA MAŁYCH TELEPORTÓW (nie prędkość!): serwerowe kontrole ruchu
        // (spigot.yml: moved-wrongly-threshold / moved-too-quickly-multiplier) cofają
        // gracza (rubber-band), gdy dash nadaje dużą prędkość przez setVelocity — gracz
        // „teleportuje” do przodu i wraca na start. Teleport to pozycja autorytatywna,
        // więc omija te kontrole: dash jest stabilny i dokładny (2.5 bloka).
        boolean wasSprinting = player.isSprinting();
        player.setSprinting(false);

        Vector from = loc.toVector();
        Vector to = target.toVector();
        double dist = from.distance(to);
        int steps = Math.max(1, (int) Math.ceil(dist / DASH_STEP_PER_TICK));
        Vector perStep = to.clone().subtract(from).normalize().multiply(dist / steps);

        int[] i = {0};
        BukkitTask[] holder = new BukkitTask[1];
        holder[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || !player.isValid()) {
                dashing.remove(player.getUniqueId());
                holder[0].cancel();
                return;
            }
            i[0]++;
            Location next;
            if (i[0] >= steps) {
                // ostatni krok — cel dokładnie; zachowaj aktualne spojrzenie gracza
                next = finalTarget.clone();
                next.setYaw(player.getLocation().getYaw());
                next.setPitch(player.getLocation().getPitch());
            } else {
                // krok o wektor w stronę celu od AKTUALNEJ pozycji (nie kumuluje błędu)
                next = player.getLocation().clone().add(perStep);
            }
            player.teleport(next);
            player.setFallDistance(0f); // brak obrażeń od upadku podczas dashu

            if (i[0] >= steps) {
                dashing.remove(player.getUniqueId());
                player.setSprinting(wasSprinting);
                holder[0].cancel();
                return;
            }

            // ślad cząsteczek za graczem
            player.getWorld().spawnParticle(Particle.CLOUD,
                    player.getLocation().add(0, 0.2, 0), 2, 0.12, 0.12, 0.12, 0.02);
        }, 0L, 1L);
    }

    private boolean isBlocked(Location loc) {
        if (loc.getY() < loc.getWorld().getMinHeight()) return true;
        Material type = loc.getBlock().getType();
        return type.isSolid() || type == Material.WATER || type == Material.LAVA;
    }

    private void regenShurikens() {
        for (Arena arena : plugin.getArenaManager().getArenas().values()) {
            if (arena.getState() != ArenaState.INGAME) continue;
            for (var uuid : arena.getPlayers()) {
                Player player = plugin.getServer().getPlayer(uuid);
                if (player == null || !player.isOnline()) continue;
                if (!plugin.getKitManager().isAssassin(player)) continue;
                if (plugin.getKitManager().countShurikens(player) < KitManager.SHURIKEN_MAX) {
                    plugin.getKitManager().addShuriken(player);
                }
            }
        }
    }

    private boolean isAssassinInGame(Player player) {
        if (!plugin.getKitManager().isAssassin(player)) return false;
        for (Arena arena : plugin.getArenaManager().getArenas().values()) {
            if (arena.isPlaying(player) && arena.getState() == ArenaState.INGAME) return true;
        }
        return false;
    }
}
