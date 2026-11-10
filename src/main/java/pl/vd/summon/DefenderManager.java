package pl.vd.summon;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vindicator;
import org.bukkit.inventory.ItemStack;
import pl.vd.VillagerDefensePlugin;
import pl.vd.stand.ArmorStandTuner;
import pl.vd.type.Faction;
import pl.vd.util.AntiSpam;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Zarządza „Obrońcami" przywoływanymi z jajka „Przywołaj Obrońce" (vindicator).
 * Obrońca stoi po stronie gracza (tag vd_sojusz), NIE atakuje graczy ani
 * wieśniaków, a celuje wyłącznie w moby z areny (metadane vd-arena). Co
 * TICK_INTERVAL ticków wymuszamy mu najbliższy cel w zasięgu i czyścimy cel,
 * gdyby wymierzył w sojusznika. Dodatkowo EntityListener anuluje obrażenia,
 * które Obrońca zadałby graczowi/wieśniakowi/sojusznikowi.
 */
public class DefenderManager {

    private final VillagerDefensePlugin plugin;
    private final Set<UUID> defenders = new HashSet<>();
    /** Zasięg wypatrywania mobów (bloki). */
    private static final int RANGE = 16;
    /** Aktualizacja celu co 5 ticków (0,25 s). */
    private static final int TICK_INTERVAL = 5;

    public DefenderManager(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, TICK_INTERVAL);
    }

    /** Losowy punkt DOKŁADNIE 1 blok od gracza (z próbą ominięcia ścian;
     *  fallback: pozycja gracza). */
    public static Location summonSpot(Player player) {
        return summonSpot(player, 1.0);
    }

    /** Losowy punkt DOKŁADNIE o podaną odległość od gracza (z próbą ominięcia
     *  ścian; fallback: pozycja gracza). */
    public static Location summonSpot(Player player, double distance) {
        Location base = player.getLocation();
        for (int attempt = 0; attempt < 8; attempt++) {
            double a = Math.random() * Math.PI * 2;
            Location cand = base.clone().add(Math.cos(a) * distance, 0, Math.sin(a) * distance);
            if (cand.getBlock().isPassable()) {
                return cand;
            }
        }
        return base;
    }

    /** Przywołuje Obrońcę (vindicatora) obok gracza - sojusznika, który atakuje
     *  tylko moby z areny. Dostaje WIDOCZNĄ żelazną zbroję (hełm + napierśnik),
     *  niezniszczalną, bez enchantów. */
    public void spawnDefender(Player player) {
        World world = player.getWorld();
        Location loc = summonSpot(player);
        Vindicator v = world.spawn(loc, Vindicator.class);
        if (v == null) return;
        v.setPersistent(true); // nie znika po oddaleniu gracza
        v.addScoreboardTag(ArmorStandTuner.TAG_ALLIED); // sojusznik pluginu
        giveDefenderArmor(v);
        defenders.add(v.getUniqueId());
        plugin.getGameManager().registerPlayerSummon(v, player); // zniknie po grze
        AntiSpam.sendActionBar(player, "§aObrońca przywołany!");
    }

    /** Żelazny hełm + napierśnik (widoczne na vindicatorze), NIEZNISZCZALNE
     *  (atrybut, nie enchant), bez żadnych enchantów; nic nie wypada po śmierci. */
    private void giveDefenderArmor(Vindicator v) {
        org.bukkit.inventory.EntityEquipment eq = v.getEquipment();
        ItemStack helm = new ItemStack(org.bukkit.Material.IRON_HELMET);
        org.bukkit.inventory.meta.ItemMeta hm = helm.getItemMeta();
        if (hm != null) {
            hm.setUnbreakable(true);
            helm.setItemMeta(hm);
        }
        ItemStack chest = new ItemStack(org.bukkit.Material.IRON_CHESTPLATE);
        org.bukkit.inventory.meta.ItemMeta cm = chest.getItemMeta();
        if (cm != null) {
            cm.setUnbreakable(true);
            chest.setItemMeta(cm);
        }
        eq.setItem(org.bukkit.inventory.EquipmentSlot.HEAD, helm, false);
        eq.setItem(org.bukkit.inventory.EquipmentSlot.CHEST, chest, false);
        eq.setDropChance(org.bukkit.inventory.EquipmentSlot.HEAD, 0f);
        eq.setDropChance(org.bukkit.inventory.EquipmentSlot.CHEST, 0f);
    }

    private void tick() {
        if (defenders.isEmpty()) return;
        for (UUID id : List.copyOf(defenders)) {
            Entity e = Bukkit.getEntity(id);
            if (!(e instanceof Vindicator v) || v.isDead()) {
                defenders.remove(id);
                continue;
            }
            LivingEntity target = nearestArenaMob(v);
            if (target != null) {
                v.setTarget(target);
            } else if (v.getTarget() != null && !Faction.of(v.getTarget()).isWave()) {
                v.setTarget(null); // nie atakuje sojuszników (graczy, wieśniaków, przywołań)
            }
        }
    }

    /** Najbliższy mob z fali (Faction.WAVE) w zasięgu, albo null.
     *  Wieśniacy areny mają te same metadane co moby z fali, dlatego Faction
     *  rozstrzyga to za nas (jak w isTargetable wieżyczek) - Obrońca nigdy nie
     *  celuje w bronionych wieśniaków ani wędrownych kupców. */
    private LivingEntity nearestArenaMob(Vindicator v) {
        LivingEntity best = null;
        double bestSq = RANGE * RANGE;
        for (Entity e : v.getNearbyEntities(RANGE, RANGE, RANGE)) {
            if (!(e instanceof LivingEntity le)) continue;
            if (!Faction.of(le).isWave()) continue; // tylko moby z fali
            double d = e.getLocation().distanceSquared(v.getLocation());
            if (d < bestSq) {
                bestSq = d;
                best = le;
            }
        }
        return best;
    }
}
