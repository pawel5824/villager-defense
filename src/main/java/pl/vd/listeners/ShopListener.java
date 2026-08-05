package pl.vd.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import pl.vd.VillagerDefensePlugin;
import pl.vd.arena.Arena;
import pl.vd.shop.ShopCategory;
import pl.vd.shop.ShopHolder;
import pl.vd.shop.ShopItem;

public class ShopListener implements Listener {

    private final VillagerDefensePlugin plugin;

    public ShopListener(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof ShopHolder holder)) return;

        event.setCancelled(true);
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(top)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        int slot = event.getSlot();
        var shopGUI = plugin.getShopGUI();

        Arena arena = findArenaOf(player);
        if (arena == null) {
            player.closeInventory();
            return;
        }

        if (shopGUI.isPointsSlot(slot)) return; // tylko podgląd, nieklikalne

        if (holder.getViewType() == ShopHolder.ViewType.MAIN) {
            if (shopGUI.isStarSlot(slot)) {
                player.closeInventory();
                return;
            }
            ShopCategory category = shopGUI.getCategoryBySlot(slot);
            if (category != null) {
                shopGUI.openCategory(player, arena, category);
            }
            return;
        }

        // CATEGORY view
        ShopCategory category = holder.getCategory();
        if (shopGUI.isBackSlot(slot)) {
            shopGUI.openMain(player, arena);
            return;
        }

        ShopItem item = shopGUI.getItemBySlot(category, slot);
        if (item != null) {
            shopGUI.purchase(player, arena, item);
            shopGUI.openCategory(player, arena, category); // odśwież widok (zaktualizowane punkty)
        }
    }

    private Arena findArenaOf(Player player) {
        for (Arena arena : plugin.getArenaManager().getArenas().values()) {
            if (arena.isPlaying(player)) return arena;
        }
        return null;
    }
}
