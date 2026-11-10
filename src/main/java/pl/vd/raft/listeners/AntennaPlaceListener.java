package pl.vd.raft.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import pl.vd.raft.RaftPlugin;
import pl.vd.raft.blocks.AntennaManager;
import pl.vd.raft.items.ItemManager;

/**
 * Faza 2 projektu anten: PPM przedmiotem „antena" w pokład tratwy stawia antenę.
 * Decyzje właściciela: stawianie przedmiotem PPM, zasięg 8 XZ + ΔY=0, zerwanie
 * natychmiast, zero komunikatów (AGENTS.md pkt 1) — porażka = po prostu nic.
 */
public class AntennaPlaceListener implements Listener {

    private final RaftPlugin plugin;
    private final AntennaManager antennaManager;

    public AntennaPlaceListener(RaftPlugin plugin, AntennaManager antennaManager) {
        this.plugin = plugin;
        this.antennaManager = antennaManager;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        if (event.getClickedBlock() == null) return;

        ItemStack item = event.getItem();
        if (item == null) return;

        ItemManager im = plugin.getItemManager();
        if (im == null || !im.isCustomItem(item, "antena")) return;

        event.setCancelled(true); // nigdy nie stawiaj waniliowego bloku / nie klikaj rzeczy

        AntennaManager.PlaceResult result =
                antennaManager.place(event.getClickedBlock().getLocation(), event.getPlayer().getUniqueId());
        if (!result.ok) {
            // Bez komunikatu (AGENTS.md pkt 1) — reason zostaje w result.reason (diagnostyka).
            return;
        }

        // Zużyj 1 przedmiot z ręki
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            event.getPlayer().getInventory().setItemInMainHand(null);
        }

        // Natychmiastowy broadcast — mod dorysowuje antenę od razu (faza 1 kontrakt).
        pl.vd.raft.net.RaftReceiversPacketSender.getInstance().broadcastAll(null);
        // Zapis — antena przetrwa restart.
        pl.vd.raft.blocks.DevicePersistence.saveAll(plugin.getDeviceRegistry());
    }
}
