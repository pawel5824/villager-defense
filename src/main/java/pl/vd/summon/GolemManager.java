package pl.vd.summon;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
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
 * Zarządza Golemami przywoływanymi z jajka „Przywołaj Golema" (iron golem).
 * Golem stoi po stronie gracza (tag vd_sojusz), jest NIEWRAŻLIWY na obrażenia
 * (nie można mu zadawać obrażeń) i dostaje widoczną zbroję: kolczasty hełm,
 * żelazny napierśnik, kolczaste spodnie i żelazne buty - bez enchantów.
 * Naturalnie atakuje moby z areny (waniliowe AI golema), a tick poniżej pilnuje,
 * żeby NIE atakował sojuszników (np. Obrońcy-vindicatora).
 */
public class GolemManager {

    private final VillagerDefensePlugin plugin;
    private final Set<UUID> golems = new HashSet<>();
    /** Częstotliwość pilnowania celu (2 ticki = 0,1 s). */
    private static final int TICK_INTERVAL = 2;

    public GolemManager(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, TICK_INTERVAL);
    }

    /** Przywołuje Golema DOKŁADNIE 1 blok od gracza (losowy kierunek) - sojusznika,
     *  niewrażliwego na obrażenia, z widoczną zbroją bez enchantów. */
    public void spawnGolem(Player player) {
        World world = player.getWorld();
        Location loc = DefenderManager.summonSpot(player);
        IronGolem g = world.spawn(loc, IronGolem.class);
        if (g == null) return;
        g.setInvulnerable(true); // nie można mu zadawać obrażeń
        g.addScoreboardTag(ArmorStandTuner.TAG_ALLIED); // sojusznik pluginu
        giveArmor(g);
        golems.add(g.getUniqueId());
        plugin.getGameManager().registerPlayerSummon(g, player); // zniknie po grze
        AntiSpam.sendActionBar(player, "§aGolem przywołany!");
    }

    /** Widoczna zbroja bez enchantów: kolczasty hełm, żelazny napierśnik,
     *  kolczaste spodnie, żelazne buty. Nic nie wypada po śmierci. */
    private void giveArmor(IronGolem g) {
        org.bukkit.inventory.EntityEquipment eq = g.getEquipment();
        eq.setItem(EquipmentSlot.HEAD, new ItemStack(org.bukkit.Material.CHAINMAIL_HELMET), false);
        eq.setItem(EquipmentSlot.CHEST, new ItemStack(org.bukkit.Material.IRON_CHESTPLATE), false);
        eq.setItem(EquipmentSlot.LEGS, new ItemStack(org.bukkit.Material.CHAINMAIL_LEGGINGS), false);
        eq.setItem(EquipmentSlot.FEET, new ItemStack(org.bukkit.Material.IRON_BOOTS), false);
        for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            eq.setDropChance(s, 0f);
        }
    }

    /** Golem nie atakuje sojuszników (np. Obrońcy-vindicatora) - cel na sojuszniku
     *  jest czyszczony; dodatkowo EntityListener anuluje takie obrażenia. */
    private void tick() {
        if (golems.isEmpty()) return;
        for (UUID id : List.copyOf(golems)) {
            Entity e = Bukkit.getEntity(id);
            if (!(e instanceof IronGolem g) || g.isDead()) {
                golems.remove(id);
                continue;
            }
            LivingEntity t = g.getTarget();
            if (t != null && Faction.of(t) == Faction.FRIENDLY) {
                g.setTarget(null);
            }
        }
    }
}
