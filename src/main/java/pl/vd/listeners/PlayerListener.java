package pl.vd.listeners;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import pl.vd.VillagerDefensePlugin;
import pl.vd.arena.Arena;
import pl.vd.arena.ArenaState;
import pl.vd.kits.KitManager;

public class PlayerListener implements Listener {

    private final VillagerDefensePlugin plugin;

    public PlayerListener(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Arena arena = findArenaOf(player);
        if (arena != null) {
            plugin.getGameManager().removePlayer(player, arena);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.COMPASS) return;

        Player player = event.getPlayer();
        Arena arena = findArenaOf(player);
        if (arena == null || arena.getState() != ArenaState.INGAME) return;

        plugin.getShopGUI().openMain(player, arena);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();

        if (KitManager.KIT_GUI_TITLE.equals(title)) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;
            for (var kit : plugin.getKitManager().getKits()) {
                if (kit.getIcon().getType() == clicked.getType()
                        && kit.getIcon().getItemMeta().getDisplayName().equals(
                                clicked.getItemMeta() != null ? clicked.getItemMeta().getDisplayName() : "")) {
                    plugin.getKitManager().setChosenKit(player, kit.getId());
                    player.sendMessage(Component.text("§aWybrano kit: " + kit.getDisplayName()));
                    player.closeInventory();
                    return;
                }
            }
        }
    }

    private Arena findArenaOf(Player player) {
        for (Arena arena : plugin.getArenaManager().getArenas().values()) {
            if (arena.isPlaying(player)) return arena;
        }
        return null;
    }
}
