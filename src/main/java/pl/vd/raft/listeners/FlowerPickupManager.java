package pl.vd.raft.listeners;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

/**
 * Prawy klik na kwiatki → automatycznie do ekwipunku.
 * Obsługiwane kwiatki: poppy, cornflower, dandelion, lily_of_the_valley, wither_rose.
 */
public class FlowerPickupManager implements Listener {

    /** Zbiór materiałów kwiatków do podnoszenia prawym kliknięciem. */
    private static final Set<Material> PICKUP_FLOWERS = Set.of(
            Material.FIRE_CORAL_FAN,
            Material.TUBE_CORAL_FAN,
            Material.HORN_CORAL_FAN,
            Material.BUBBLE_CORAL_FAN,
            Material.BRAIN_CORAL_FAN
    );

    /** Zbiór materiałów koralowców (do blokowania śmiertelności). */
    private static final Set<Material> CORAL_FANS = Set.of(
            Material.FIRE_CORAL_FAN,
            Material.TUBE_CORAL_FAN,
            Material.HORN_CORAL_FAN,
            Material.BUBBLE_CORAL_FAN,
            Material.BRAIN_CORAL_FAN,
            Material.DEAD_FIRE_CORAL_FAN,
            Material.DEAD_TUBE_CORAL_FAN,
            Material.DEAD_HORN_CORAL_FAN,
            Material.DEAD_BUBBLE_CORAL_FAN,
            Material.DEAD_BRAIN_CORAL_FAN
    );

    private final pl.vd.raft.RaftPlugin plugin;

    public FlowerPickupManager(pl.vd.raft.RaftPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onRightClickBlock(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block clicked = event.getClickedBlock();
        if (clicked == null) return;
        if (!PICKUP_FLOWERS.contains(clicked.getType())) return;

        // Sprawdź czy świat to RAFT
        String raftWorldName = plugin.getConfig().getString("raft-world-name", "RAFT");
        if (!clicked.getWorld().getName().equalsIgnoreCase(raftWorldName)) return;

        Player player = event.getPlayer();
        Material flowerType = clicked.getType();

        // Usuń kwiatek z bloku
        clicked.setType(Material.AIR, false);

        // Dodaj do ekwipunku
        ItemStack flowerItem = new ItemStack(flowerType, 1);
        java.util.HashMap<Integer, ItemStack> leftOver = player.getInventory().addItem(flowerItem);

        if (!leftOver.isEmpty()) {
            // Eq pełne — wyrzuć na ziemię
            for (ItemStack remaining : leftOver.values()) {
                clicked.getWorld().dropItemNaturally(clicked.getLocation(), remaining);
            }
        }

        // Dźwięk podniesienia
        player.getWorld().playSound(
                player.getLocation(),
                org.bukkit.Sound.ENTITY_ITEM_PICKUP,
                0.8f,
                1.2f
        );

        event.setCancelled(true);
    }

    // ══════════════════════════════════════════════════════════════
    //  Blokuj śmiertelność koralowców poza wodą
    // ══════════════════════════════════════════════════════════════

    @EventHandler
    public void onBlockFade(BlockFadeEvent event) {
        Block block = event.getBlock();
        if (!CORAL_FANS.contains(block.getType())) return;

        String raftWorldName = plugin.getConfig().getString("raft-world-name", "RAFT");
        if (!block.getWorld().getName().equalsIgnoreCase(raftWorldName)) return;

        event.setCancelled(true);
    }

}
