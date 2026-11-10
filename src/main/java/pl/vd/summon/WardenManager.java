package pl.vd.summon;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Warden;
import pl.vd.VillagerDefensePlugin;
import pl.vd.stand.ArmorStandTuner;
import pl.vd.type.Faction;
import pl.vd.util.AntiSpam;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Zarządza Nadzorcami (warden) przywoływanymi z jajka „Przywołaj Nadzorcę".
 * Nadzorca pojawia się 2 bloki od gracza, jest oznaczony tagiem vd_sojusz
 * (gracze nie mogą go zranić - handler w EntityListener) i NIE ma zbroi.
 * Warden z natury wykrywa dźwięki/wibracje i podchodzi do źródła; tutaj co
 * kilka ticków czyścimy mu gniew wobec graczy/wieśniaków/sojuszników i
 * wymuszamy cel na najbliższym mobie z areny - dzięki temu zachowuje się jak
 * zwykły agresywny mob (atakuje tylko moby z fal), a nie goni za hałasami.
 */
public class WardenManager {

    private final VillagerDefensePlugin plugin;
    private final Set<UUID> wardens = new HashSet<>();
    /** Zasięg wypatrywania mobów (bloki). */
    private static final int RANGE = 16;
    /** Odległość przywołania od gracza (bloki). */
    private static final double SUMMON_DISTANCE = 2.0;
    /** Aktualizacja celu co 5 ticków (0,25 s). */
    private static final int TICK_INTERVAL = 5;

    public WardenManager(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, TICK_INTERVAL);
    }

    /** Przywołuje Nadzorcę 2 bloki od gracza (losowy kierunek) - sojusznika bez
     *  zbroi, agresywnego tylko wobec mobów z areny. */
    public void spawnWarden(Player player) {
        World world = player.getWorld();
        Location loc = DefenderManager.summonSpot(player, SUMMON_DISTANCE);
        Warden w = world.spawn(loc, Warden.class);
        if (w == null) return;
        w.setPersistent(true); // nie znika po oddaleniu gracza
        w.addScoreboardTag(ArmorStandTuner.TAG_ALLIED); // sojusznik pluginu
        wardens.add(w.getUniqueId());
        plugin.getGameManager().registerPlayerSummon(w, player); // zniknie po grze
        AntiSpam.sendActionBar(player, "§aNadzorca przywołany!");
    }

    private void tick() {
        if (wardens.isEmpty()) return;
        for (UUID id : List.copyOf(wardens)) {
            Entity e = Bukkit.getEntity(id);
            if (!(e instanceof Warden w) || w.isDead()) {
                wardens.remove(id);
                continue;
            }
            // „wyłączona wibracja": czyść gniew wobec wszystkiego, co NIE jest
            // mobem z fali (gracze, wieśniacy, przywołane stwory). Wieśniacy areny
            // mają metadane vd-arena jak moby z fali, więc Faction rozstrzyga to za nas.
            LivingEntity angry = w.getEntityAngryAt();
            if (angry != null && !Faction.of(angry).isWave()) {
                w.clearAnger(angry);
                w.setTarget(null);
            }
            // wymuś cel: najbliższy mob z areny
            LivingEntity target = nearestArenaMob(w);
            if (target != null) {
                w.setTarget(target);
            } else {
                // brak mobów: PATROLUJ - CIĄGŁY ruch trzyma wardena w pogotowiu.
                // Waniliowy warden zakopuje się (schodzi pod ziemię i znika), gdy
                // przez dłuższą chwilę jest spokojny i STOI w miejscu - dlatego
                // ZAWSZE dajemy mu świeży cel marszu (nie tylko, gdy poprzedni się
                // skończył): nawigacja nigdy nie bywa „ukończona", więc goal
                // zakopywania nie może wystartować.
                w.setTarget(null);
                w.getPathfinder().moveTo(randomPatrolSpot(w), 0.6);
                // siatka bezpieczeństwa: gdyby marsz się nie powiódł (cel w ścianie
                // albo warden utknął w ziemi), podciągnij go na powierzchnię, żeby
                // nigdy nie stał bezczynnie w miejscu.
                if (!w.getPathfinder().hasPath()) {
                    Location up = w.getLocation().clone();
                    for (int dy = 0; dy < 8; dy++) {
                        up.add(0, 1, 0);
                        if (up.getBlock().isPassable() && up.clone().add(0, 1, 0).getBlock().isPassable()) {
                            w.teleport(up);
                            break;
                        }
                    }
                }
            }
        }
    }

    /** Losowy punkt patrolowania (2-8 bloków wokół wardena, na jego wysokości). */
    private Location randomPatrolSpot(Warden w) {
        Location c = w.getLocation();
        double a = Math.random() * Math.PI * 2;
        double r = 2 + Math.random() * 6;
        return c.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r);
    }

    /** Najbliższy mob z fali (Faction.WAVE) w zasięgu, albo null.
     *  Wieśniacy areny mają te same metadane co moby z fali, dlatego Faction
     *  rozstrzyga to za nas (jak w isTargetable wieżyczek) - warden nigdy nie
     *  celuje w bronionych wieśniaków ani wędrownych kupców. */
    private LivingEntity nearestArenaMob(Warden w) {
        LivingEntity best = null;
        double bestSq = RANGE * RANGE;
        for (Entity e : w.getNearbyEntities(RANGE, RANGE, RANGE)) {
            if (!(e instanceof LivingEntity le)) continue;
            if (!Faction.of(le).isWave()) continue; // tylko moby z fali
            double d = e.getLocation().distanceSquared(w.getLocation());
            if (d < bestSq) {
                bestSq = d;
                best = le;
            }
        }
        return best;
    }
}
