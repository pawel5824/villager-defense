package pl.vd.raft.listeners;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import pl.vd.raft.RaftPlugin;

/**
 * Blokuje napełnianie wiadra wodą (prawym kliknięciem na blok wody).
 * Pozwala jedynie na zbieranie mleka od krów.
 */
public class BucketListener implements Listener {

    private final RaftPlugin plugin;

    public BucketListener(RaftPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null) return;
        if (item.getType() != Material.BUCKET) return;

        Action action = event.getAction();

        // Blokuj napełnianie wiadra wodą (prawym kliknięciem na blok wody)
        if (action == Action.RIGHT_CLICK_BLOCK) {
            if (event.getClickedBlock() != null) {
                Material clickedBlock = event.getClickedBlock().getType();
                // Blokuj wodę (źródła i strumienie)
                if (clickedBlock == Material.WATER || clickedBlock == Material.WATER_CAULDRON) {
                    event.setCancelled(true);
                    // Bez komunikatu na czacie (AGENTS.md pkt 1) — celowo.
                }
            }
        }

        // Pozwól na zbieranie mleka (prawym kliknięciem na krowę)
        // To jest domyślne zachowanie Minecraft - nie musimy nic robić,
        // bo mleko jest zbierane przez interact z cow, nie z blokiem
    }
}
