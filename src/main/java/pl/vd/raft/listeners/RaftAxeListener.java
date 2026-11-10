package pl.vd.raft.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import pl.vd.raft.RaftPlugin;
import pl.vd.raft.entity.RaftEntity;
import pl.vd.raft.entity.RaftManager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Siekiera — rozbiórka tratwy (Faza 5).
 *
 * Zasady:
 *  - Gdy gracz trzyma siekierę w ręce, wszystkie rozbieralne komórki najbliższej
 *    tratwy świecą na zielono — dokładnie jak podgląd-duch młota budowlanego
 *    (RaftBuildPreview). Podświetlenie podąża za tratwą i znika, gdy gracz
 *    zmieni przedmiot / wyjdzie z zasięgu / umrze.
 *  - LEWY klik (w powietrzu lub na blok) celujący w komórkę tratwy zadaje jej
 *    uderzenie (raycast po siatce komórek, jak przy budowaniu); trafienie w
 *    niewidzialną kolizję tratwy (shulker) też się liczy.
 *  - Uszkodzenie komórki jest STAŁE i zapisywane razem z tratwą (raft-saves.yml),
 *    więc przetrwa restart serwera; znika dopiero po zniszczeniu komórki.
 *    Komórka ma JEDEN wspólny licznik (nie per gracz) — przygotowane pod
 *    przyszłą naprawę młotem.
 *  - Po osiągnięciu progu komórka znika (RaftEntity.removeBlock).
 *  - Liczba uderzeń zależy od siekiery: drewniana 3, kamienna 3, żelazna 2,
 *    diamentowa 2 (inne siekiery = 3).
 *  - Komórki bazowe tratwy (RAFT — „korzeń" 2×2) i ostatnia komórka są
 *    chronione — dokładnie tak, jak w RaftEntity.removeBlock — więc nie świecą
 *    i nie da się ich rozwalić.
 *  - Każde uderzenie zużywa 1 pkt wytrzymałości siekiery (te same wartości co
 *    przy ścinaniu drzew, TreeBreakManager.AXE_DURABILITY).
 *
 * Na chwilę obecną jedynym celem rozbiórki jest tratwa — drzewa mają osobną
 * mechanikę (TreeBreakManager).
 */
public class RaftAxeListener implements Listener {

    /** Zielony: komórkę można rozebrać (jak podgląd-duch młota). */
    private static final Color VALID = Color.fromRGB(85, 255, 85);

    /** Zasięg rozbiórki od środka tratwy. */
    private static final double DEMOLISH_RANGE = 6.0;

    /** Cooldown między uderzeniami (ms). */
    private static final long HIT_COOLDOWN_MS = 500;

    /** Po ilu uderzeniach znika komórka — wg typu siekiery. */
    private static final Map<Material, Integer> HITS_REQUIRED = Map.of(
            Material.WOODEN_AXE, 3,
            Material.STONE_AXE, 3,
            Material.IRON_AXE, 2,
            Material.DIAMOND_AXE, 2
    );

    /** Prefiks tagu podświetlenia: „raft_axe_<raftId>:<cellKey>". */
    private static final String TAG_PREFIX = "raft_axe_";

    private final RaftPlugin plugin;

    /** Gracz -> podświetlenia jego komórek (key „raftId:cellKey" -> display). */
    private final Map<UUID, Map<String, BlockDisplay>> highlights = new HashMap<>();
    /** Gracz -> timestamp ostatniego uderzenia (cooldown). */
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public RaftAxeListener(RaftPlugin plugin) {
        this.plugin = plugin;
        // Podświetlenie aktualizowane co 2 ticki — jak podgląd-duch młota
        Bukkit.getScheduler().runTaskTimer(plugin.getParent(), this::tick, 5L, 2L);
    }

    // ─────────────────────── TICK (co 2 ticki) ────────────────────

    private void tick() {
        // Sprzątanie: gracz offline / martwy / nie trzyma już siekiery
        highlights.entrySet().removeIf(e -> {
            Player p = Bukkit.getPlayer(e.getKey());
            boolean stale = p == null || !p.isOnline() || p.isDead()
                    || !isAxe(p.getInventory().getItemInMainHand());
            if (stale) removeDisplays(e.getValue());
            return stale;
        });

        for (UUID uuid : highlights.keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) update(p);
        }
    }

    private boolean isAxe(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        return item.getType().name().endsWith("_AXE");
    }

    // ─────────────────────── PODŚWIETLENIE ────────────────────────

    private void update(Player player) {
        RaftEntity raft = RaftManager.getInstance().getNearestRaft(player, DEMOLISH_RANGE);
        Map<String, BlockDisplay> displays =
                highlights.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());

        if (raft == null) {
            removeDisplays(displays);
            return;
        }

        Set<String> needed = new HashSet<>();
        Vector pos = raft.getCenter().toVector();

        for (Vector offset : raft.getBlockOffsets()) {
            if (!isDestroyable(raft, offset)) continue;

            String key = raft.getRaftId() + ":" + RaftEntity.keyOf(offset);
            needed.add(key);

            Material mat = raft.getCellMaterial(offset);
            RaftEntity.CellType type = raft.getCellType(offset);
            BlockFace facing = raft.getCellFacing(offset);
            BlockData data = dataFor(mat, type, facing);
            Transformation transform = RaftEntity.shapeTransform(type,
                    facing != null ? facing : BlockFace.NORTH,
                    new RaftEntity.CellShape(raft.getCellThickness(offset),
                            raft.getCellHeight(offset)));

            Location loc = new Location(player.getWorld(),
                    pos.getX() + offset.getX(),
                    pos.getY() + offset.getY(),
                    pos.getZ() + offset.getZ());

            BlockDisplay display = displays.get(key);
            if (display == null || display.isDead() || !display.isValid()
                    || !display.getWorld().equals(player.getWorld())) {
                displays.put(key, spawn(player.getWorld(), loc, data, transform, key));
            } else {
                display.teleport(loc);
                if (!data.equals(display.getBlock())) {
                    display.setBlock(data);
                }
            }
        }

        // Usuń podświetlenia komórek, które zniknęły / przestały być rozbieralne
        displays.keySet().removeIf(key -> {
            if (needed.contains(key)) return false;
            BlockDisplay d = displays.get(key);
            if (d != null && !d.isDead()) d.remove();
            return true;
        });
    }

    /** Czy komórkę da się rozebrać (chronione: korzeń RAFT i ostatnia komórka). */
    private boolean isDestroyable(RaftEntity raft, Vector offset) {
        RaftEntity.CellType type = raft.getCellType(offset);
        if (type == null || type == RaftEntity.CellType.RAFT || raft.getBlockCount() <= 1) {
            return false;
        }
        return true;
    }

    private BlockDisplay spawn(World world, Location loc, BlockData data,
                               Transformation transform, String tag) {
        return world.spawn(loc, BlockDisplay.class, d -> {
            d.setBlock(data);
            d.setTransformation(transform);
            d.setGlowing(true);
            d.setGlowColorOverride(VALID);
            d.setInvulnerable(true);
            d.setSilent(true);
            d.setGravity(false);
            d.setPersistent(false); // znika przy rozładowaniu chunku — odtworzymy
            d.addScoreboardTag(TAG_PREFIX + tag); // raftId:cellKey — do trafień
        });
    }

    /**
     * BlockData podświetlenia: WSPÓLNA metoda encji tratwy, więc rozbiórka
     * pokazuje ten sam kształt i obrót (schody, skrzynie z obróconym przodem),
     * co postawiona komórka.
     */
    private BlockData dataFor(Material mat, RaftEntity.CellType type, BlockFace facing) {
        return RaftEntity.dataFor(mat != null ? mat : Material.OAK_PLANKS, type, facing);
    }

    private void removeDisplays(Map<String, BlockDisplay> displays) {
        for (BlockDisplay d : displays.values()) {
            if (d != null && !d.isDead()) d.remove();
        }
        displays.clear();
    }

    // ─────────────────────── UDERZENIA ────────────────────────────

    /** Lewy klik (powietrze/blok): raycast po siatce komórek tratwy. */
    @EventHandler
    public void onLeftClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_AIR
                && event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!isAxe(item)) return;

        RaftEntity raft = RaftManager.getInstance().getNearestRaft(player, DEMOLISH_RANGE);
        if (raft == null) return;
        RaftBuildListener build = plugin.getRaftBuildListener();
        if (build == null) return;

        Vector eye = player.getEyeLocation().toVector();
        Vector dir = player.getEyeLocation().getDirection();
        Vector hit = build.raycastCell(raft, eye, dir);
        if (hit == null) {
            // Promień minął komórki — może trafił w PODPORĘ piętra (słupek
            // stoi na narożniku komórek i nie jest komórką). Jedno uderzenie
            // siekierą zdejmuje podporę (nie ma wytrzymałości, jak ściana).
            Vector node = build.raycastSupport(raft, eye, dir);
            if (node != null) {
                event.setCancelled(true);
                // RAZEM Z PODPORĄ RUNIE piętro, które trzymała (i to, co na nim
                // stało), ale po krótkiej chwili (raft-support.collapse-delay-
                // ticks) — tratwa sama sprząta wtedy komórki bez podparcia,
                // gra dźwięk i wypuszcza materiał. Tu tylko efekt zdjęcia słupka.
                if (raft.removeSupport(node)) {
                    Vector pos = raft.getCenter().toVector();
                    Location loc = new Location(player.getWorld(),
                            pos.getX() + node.getX(),
                            pos.getY() + node.getY() + 0.5,
                            pos.getZ() + node.getZ());
                    loc.getWorld().playSound(loc, Sound.BLOCK_WOOD_BREAK, 1.0f, 1.1f);
                }
            }
            return;
        }

        // Kliknięcie w komórkę tratwy — nie pozwól przypadkiem bić bloków świata
        event.setCancelled(true);
        registerHit(player, item, raft, hit);
    }

    /**
     * Trafienie bezpośrednio w encję tratwy: nasze podświetlenie (tag z kluczem
     * komórki) albo niewidzialną kolizję-shulkera. Zabezpieczenie na wypadek,
     * gdyby kliknięcie poszło „w encję" zamiast „w powietrze".
     */
    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!isAxe(item)) return;

        Entity e = event.getEntity();
        RaftEntity raft = null;
        Vector offset = null;

        // 1) Nasze podświetlenie — tag niesie raftId i klucz komórki
        for (String tag : e.getScoreboardTags()) {
            if (!tag.startsWith(TAG_PREFIX)) continue;
            String body = tag.substring(TAG_PREFIX.length()); // raftId:cellKey
            int sep = body.indexOf(':');
            if (sep <= 0) continue;
            UUID raftId;
            try {
                raftId = UUID.fromString(body.substring(0, sep));
            } catch (IllegalArgumentException ex) {
                continue;
            }
            String cellKey = body.substring(sep + 1);
            raft = findRaft(raftId);
            if (raft == null) return;
            for (Vector off : raft.getBlockOffsets()) {
                if (RaftEntity.keyOf(off).equals(cellKey)) {
                    offset = off;
                    break;
                }
            }
            break;
        }

        // 2) Niewidzialna kolizja tratwy (shulker z tagiem raft_<uuid>)
        if (raft == null && RaftEntity.isRaftEntity(e)
                && e.getScoreboardTags().contains(RaftEntity.TAG_COLLIDER)) {
            UUID raftId = RaftEntity.raftIdOf(e);
            raft = raftId != null ? findRaft(raftId) : null;
            if (raft != null) offset = findCellAt(raft, e);
        }

        if (raft == null || offset == null) return;
        event.setCancelled(true);
        registerHit(player, item, raft, offset);
    }

    /** Zadaje komórce tratwy jedno uderzenie siekierą (z cooldownem i postępem). */
    private void registerHit(Player player, ItemStack item, RaftEntity raft, Vector offset) {
        if (!isDestroyable(raft, offset)) return;

        // Cooldown
        long now = System.currentTimeMillis();
        Long last = cooldowns.get(player.getUniqueId());
        if (last != null && now - last < HIT_COOLDOWN_MS) return;
        cooldowns.put(player.getUniqueId(), now);

        String fullKey = raft.getRaftId() + ":" + RaftEntity.keyOf(offset);

        // Uszkodzenie trzyma sama tratwa (wspólny licznik, zapisywany z nią —
        // przetrwa restart; zero po naprawie młotem w przyszłości)
        int hits = raft.getCellDamage(offset) + 1;
        raft.setCellDamage(offset, hits);

        // Zużycie wytrzymałości siekiery (te same wartości co przy ścinaniu drzew)
        TreeBreakManager.damageAxe(player, item);

        int required = HITS_REQUIRED.getOrDefault(item.getType(), 3);
        playHitFeedback(player, raft, offset, hits, required);

        if (hits >= required) {
            Vector pos = raft.getCenter().toVector();
            Location loc = new Location(player.getWorld(),
                    pos.getX() + offset.getX(),
                    pos.getY() + offset.getY(),
                    pos.getZ() + offset.getZ());
            Material mat = raft.getCellMaterial(offset);
            boolean stone = mat == Material.COBBLESTONE || mat == Material.STONE;

            raft.removeBlock(offset); // czyści też uszkodzenie komórki
            loc.getWorld().playSound(loc,
                    stone ? Sound.BLOCK_STONE_BREAK : Sound.BLOCK_WOOD_BREAK, 1.0f, 0.9f);

            // Podświetlenie znika od razu (następny tick i tak by je usunął)
            Map<String, BlockDisplay> displays = highlights.get(player.getUniqueId());
            if (displays != null) {
                BlockDisplay d = displays.remove(fullKey);
                if (d != null && !d.isDead()) d.remove();
            }
        }
    }

    /** Dźwięk + cząsteczki uderzenia (pitch rośnie z postępem). */
    private void playHitFeedback(Player player, RaftEntity raft, Vector offset,
                                 int hits, int required) {
        Vector pos = raft.getCenter().toVector();
        Location loc = new Location(player.getWorld(),
                pos.getX() + offset.getX(),
                pos.getY() + offset.getY(),
                pos.getZ() + offset.getZ());
        float progress = (float) hits / required;

        Material mat = raft.getCellMaterial(offset);
        boolean stone = mat == Material.COBBLESTONE || mat == Material.STONE;
        loc.getWorld().playSound(loc,
                stone ? Sound.BLOCK_STONE_HIT : Sound.BLOCK_WOOD_HIT,
                0.8f, 0.8f + progress * 0.6f);

        loc.getWorld().spawnParticle(Particle.BLOCK, loc.clone().add(0.5, 0.5, 0.5),
                (int) (progress * 8) + 2, 0.3, 0.3, 0.3, mat.createBlockData());
    }

    // ─────────────────────── POMOCNICZE ───────────────────────────

    private RaftEntity findRaft(UUID raftId) {
        for (RaftEntity raft : RaftManager.getInstance().getRafts()) {
            if (raft.getRaftId().equals(raftId)) return raft;
        }
        return null;
    }

    /** Szuka komórki tratwy po pozycji encji-kolizji (marker stoi 0.5 pod środkiem komórki). */
    private Vector findCellAt(RaftEntity raft, Entity e) {
        Vector center = raft.getCenter().toVector();
        for (Vector off : raft.getBlockOffsets()) {
            double x = center.getX() + off.getX();
            double y = center.getY() + off.getY() - 0.5; // COLLIDER_Y_OFFSET markera
            double z = center.getZ() + off.getZ();
            if (Math.abs(e.getLocation().getX() - x) < 0.6
                    && Math.abs(e.getLocation().getY() - y) < 0.6
                    && Math.abs(e.getLocation().getZ() - z) < 0.6) {
                return off;
            }
        }
        return null;
    }
}