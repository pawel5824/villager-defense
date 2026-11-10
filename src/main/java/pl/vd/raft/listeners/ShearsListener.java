package pl.vd.raft.listeners;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Obsługa nożyc — spadek durability o 1 za każde użycie.
 */
public class ShearsListener implements Listener {

    private static final String SHEARS_NAME = "§6Nożyce";

    @EventHandler
    public void onShearEntity(PlayerShearEntityEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!isShears(item)) return;

        damageShears(player, item);
    }



    private void damageShears(Player player, ItemStack item) {
        short durability = item.getDurability();
        short maxDur = item.getType().getMaxDurability();
        short newDurability = (short) (durability + 1);

        if (newDurability >= maxDur) {
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

    private boolean isShears(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        if (item.getType() != Material.SHEARS) return false;
        if (!item.getItemMeta().hasDisplayName()) return false;
        return item.getItemMeta().getDisplayName().equals(SHEARS_NAME);
    }
}
