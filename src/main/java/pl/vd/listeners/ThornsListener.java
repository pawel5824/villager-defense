package pl.vd.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import pl.vd.VillagerDefensePlugin;
import pl.vd.arena.Arena;
import pl.vd.game.MobSpawner;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Ciernie (Thorns) w trybie "tylko odbicie" (config: thorns.reflect-only).
 *
 * Gdy MOB z areny uderzy gracza, odbicie Cierni zadaje obrażenia WYŁĄCZNIE mobowi
 * (1-4, szansa = thorns.chance-per-level za każdy poziom Cierni, max 100%). Na czas
 * waniliowego przetwarzania uderzenia zdejmujemy ze zbroi gracza enchant Cierni
 * i zakładamy go z powrotem zaraz po zdarzeniu, dzięki czemu gracz nie dostaje
 * ŻADNYCH dodatkowych skutków ubocznych Cierni - tylko mob zostaje zraniony.
 */
public class ThornsListener implements Listener {

    private final VillagerDefensePlugin plugin;
    private final Random random = new Random();

    public ThornsListener(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMobHitsPlayer(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) return;
        if (!plugin.getConfig().getBoolean("thorns.reflect-only", true)) return;

        if (!(event.getEntity() instanceof Player player)) return;
        if (!(event.getDamager() instanceof LivingEntity mob)) return;
        // tylko moby z areny (nie gracze, nie wieśniacy)
        if (!mob.hasMetadata(MobSpawner.METADATA_KEY)) return;
        if (findArenaOf(player) == null) return;

        int thornsLevel = thornsLevel(player);
        if (thornsLevel <= 0) return;

        // szansa na odbicie za KAŻDY poziom Cierni (config: thorns.chance-per-level)
        int chancePerLevel = Math.max(1, plugin.getConfig().getInt("thorns.chance-per-level", 7));

        // Zdejmij Ciernie na czas tego uderzenia - waniliowy mechanizm Cierni
        // (w tym ewentualne dodatkowe obrażenia dla gracza) w ogóle nie zadziała.
        List<ArmorThorns> stripped = stripThorns(player);
        try {
            // Nasze własne odbicie: szansa chance-per-level % za każdy poziom, 1-4 obrażeń.
            if (random.nextInt(100) < Math.min(100, thornsLevel * chancePerLevel)) {
                int damage = 1 + random.nextInt(4);
                // obrażenia WYŁĄCZNIE dla moba - gracz nic nie dostaje
                mob.damage(damage, player);
            }
        } finally {
            reapplyThorns(player, stripped);
        }
    }

    private record ArmorThorns(ItemStack item, int level) {}

    /** Łączny poziom Cierni na całej zbroi gracza (0 = brak Cierni). */
    private int thornsLevel(Player player) {
        Enchantment thorns = getThornsEnchant();
        if (thorns == null) return 0;
        int total = 0;
        for (ItemStack piece : player.getInventory().getArmorContents()) {
            if (piece != null && piece.getType() != Material.AIR) {
                total += piece.getEnchantmentLevel(thorns);
            }
        }
        return total;
    }

    /** Zdejmuje Ciernie ze wszystkich części zbroi gracza i zwraca, co i w jakim poziomie zdjęto. */
    private List<ArmorThorns> stripThorns(Player player) {
        Enchantment thorns = getThornsEnchant();
        List<ArmorThorns> removed = new ArrayList<>();
        if (thorns == null) return removed;
        for (ItemStack piece : player.getInventory().getArmorContents()) {
            if (piece == null || piece.getType() == Material.AIR) continue;
            int level = piece.getEnchantmentLevel(thorns);
            if (level > 0) {
                piece.removeEnchantment(thorns);
                removed.add(new ArmorThorns(piece, level));
            }
        }
        return removed;
    }

    /** Zakłada z powrotem zdjęte Ciernie (te same poziomy). */
    private void reapplyThorns(Player player, List<ArmorThorns> removed) {
        Enchantment thorns = getThornsEnchant();
        if (thorns == null) return;
        for (ArmorThorns at : removed) {
            if (at.item().getType() != Material.AIR) {
                at.item().addUnsafeEnchantment(thorns, at.level());
            }
        }
    }

    private Enchantment getThornsEnchant() {
        try {
            return Bukkit.getRegistry(Enchantment.class)
                    .get(new NamespacedKey(NamespacedKey.MINECRAFT, "thorns"));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Arena findArenaOf(Player player) {
        for (Arena arena : plugin.getArenaManager().getArenas().values()) {
            if (arena.isPlaying(player)) return arena;
        }
        return null;
    }
}
