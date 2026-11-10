package pl.vd.raft.latarenka;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import pl.vd.VillagerDefensePlugin;

/**
 * Obsługuje PPM przedmiotem-latarenką → postawienie latarenki w świecie Raft.
 */
public class LatarenkaListener implements Listener {

    private final VillagerDefensePlugin plugin;
    private final LatarenkaManager manager;

    public LatarenkaListener(VillagerDefensePlugin plugin, LatarenkaManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        // Tylko prawy klik bloku
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        EquipmentSlot hand = event.getHand();
        if (hand == null) return;

        // Tylko prawa ręka (żeby nie blokować off-hand)
        if (hand != EquipmentSlot.HAND) return;

        ItemStack item = (hand == EquipmentSlot.HAND)
                ? player.getInventory().getItemInMainHand()
                : player.getInventory().getItemInOffHand();

        if (item.getType() == Material.AIR) return;

        // Sprawdź czy to latarenka pluginu
        String presetName = manager.getPresetFromItem(item);
        if (presetName == null) return;

        // Sprawdź czy gracz jest w świecie Raft
        String worldName = plugin.getConfig().getString("raft-world-name", "RAFT");
        if (!player.getWorld().getName().equalsIgnoreCase(worldName)) {
            return;
        }

        // Anuluj domyślne placing bloku (żeby armor stand się nie postawił)
        event.setCancelled(true);

        // Postaw latarenkę na klikniętym bloku
        Block target = event.getClickedBlock();
        if (target == null) return;

        boolean ok = manager.placeLatarenkaAt(presetName, target, player);
        if (ok) {
            // Zużyj 1 przedmiot
            if (item.getAmount() > 1) {
                item.setAmount(item.getAmount() - 1);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
        }
        // Brak komunikatów o powodzeniu/porażce (zasada "wszędzie cicho", patrz AGENTS.md).
    }
}
