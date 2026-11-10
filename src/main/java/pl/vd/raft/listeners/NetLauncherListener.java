package pl.vd.raft.listeners;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Obsługa Wyrzutni sieci — spadek durability o 1 gdy strzał trafi moba.
 */
public class NetLauncherListener implements Listener {

    private static final String LAUNCHER_NAME = "§6Wyrzutnia sieci";

    @EventHandler
    public void onProjectileHitEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Projectile projectile)) return;
        if (projectile.getShooter() instanceof Player player) {
            ItemStack item = player.getInventory().getItemInMainHand();
            if (!isNetLauncher(item)) return;

            // Spadek durability o 1
            short durability = item.getDurability();
            short maxDur = item.getType().getMaxDurability();
            short newDurability = (short) (durability + 1);

            if (newDurability >= maxDur) {
                // Wyrzutnia się kończy
                player.getInventory().setItemInMainHand(null);
                player.getWorld().playSound(
                    player.getLocation(),
                    org.bukkit.Sound.ENTITY_ITEM_BREAK,
                    1.0f, 1.0f
                );
            } else {
                item.setDurability(newDurability);
            }
        }
    }

    /** Sprawdza czy item to Wyrzutnia sieci. */
    private boolean isNetLauncher(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        if (item.getType() != Material.CROSSBOW) return false;
        if (!item.getItemMeta().hasDisplayName()) return false;
        if (!item.getItemMeta().hasCustomModelData()) return false;
        return item.getItemMeta().getDisplayName().equals(LAUNCHER_NAME)
                && item.getItemMeta().getCustomModelData() == 2001;
    }
}
