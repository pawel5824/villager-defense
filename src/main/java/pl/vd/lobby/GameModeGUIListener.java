package pl.vd.lobby;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import pl.vd.VillagerDefensePlugin;

/**
 * Obsługuje kliknięcia w GUI wyboru trybu gry.
 */
public class GameModeGUIListener implements Listener {

    private final VillagerDefensePlugin plugin;

    public GameModeGUIListener(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (!plugin.getGameModeGUI().isOurGUI(e.getView().getTitle())) return;

        e.setCancelled(true); // blokuj przenoszenie przedmiotów

        plugin.getGameModeGUI().handleClick(player, e.getRawSlot());
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent e) {
        if (!plugin.getGameModeGUI().isOurGUI(e.getView().getTitle())) return;
        e.setCancelled(true); // blokuj drag and drop
    }
}
