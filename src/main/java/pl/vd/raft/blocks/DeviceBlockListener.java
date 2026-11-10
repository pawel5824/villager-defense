package pl.vd.raft.blocks;

import org.bukkit.block.BlockFace;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listener obsługujący interakcje z urządzeniami.
 * Obsługuje zarówno DeviceBlock2x2 (2×2) jak i WindmillDevice (2×2×6).
 */
public class DeviceBlockListener implements Listener {

    // Mapa aktywnych urządzeń: entityUUID -> DeviceBlock2x2
    private final Map<UUID, DeviceBlock2x2> devices = new ConcurrentHashMap<>();

    // Mapa aktywnych wiatraków: entityUUID -> WindmillDevice
    private final Map<UUID, WindmillDevice> windmills = new ConcurrentHashMap<>();

    // ===== DeviceBlock2x2 (stare urządzenie 2×2) =====

    /** Rejestruje urządzenie 2×2 w listenerze */
    public void registerDevice(DeviceBlock2x2 device) {
        if (device.getBatterySlot() != null) {
            devices.put(device.getBatterySlot().getUniqueId(), device);
        }
        if (device.getFunctionalSlot() != null) {
            devices.put(device.getFunctionalSlot().getUniqueId(), device);
        }
    }

    /** Usuwa urządzenie 2×2 z listenera */
    public void unregisterDevice(DeviceBlock2x2 device) {
        if (device.getBatterySlot() != null) {
            devices.remove(device.getBatterySlot().getUniqueId());
        }
        if (device.getFunctionalSlot() != null) {
            devices.remove(device.getFunctionalSlot().getUniqueId());
        }
    }

    // ===== WindmillDevice (wiatrak 2×2×6) =====

    /** Rejestruje wiatrak w listenerze (4 sloty baterii) */
    public void registerWindmill(WindmillDevice windmill) {
        for (UUID uuid : windmill.getBatterySlotUUIDs()) {
            windmills.put(uuid, windmill);
        }
    }

    /** Usuwa wiatrak z listenera */
    public void unregisterWindmill(WindmillDevice windmill) {
        for (UUID uuid : windmill.getBatterySlotUUIDs()) {
            windmills.remove(uuid);
        }
    }

    /** Czyści wszystkie urządzenia */
    public void clearAll() {
        devices.clear();
        windmills.clear();
    }

    /**
     * Faza 1 projektu anten: ochrona nośnika-beczki (facing=down, open=true).
     * To jedyny stan beczki, który pack renderuje jako powietrze — otwarcie GUI
     * przełączyłoby open na false (beczka by się pokazała), a zniszczenie ubiło
     * BlockEntity i rendering urządzenia. Zwykłe beczki (inny stan) działają normalnie.
     */
    private boolean isCarrierBlock(org.bukkit.block.Block block) {
        return block.getBlockData() instanceof org.bukkit.block.data.type.Barrel barrel
                && barrel.getFacing() == org.bukkit.block.BlockFace.DOWN
                && barrel.isOpen();
    }

    @EventHandler
    public void onCarrierInteract(org.bukkit.event.player.PlayerInteractEvent event) {
        org.bukkit.block.Block block = event.getClickedBlock();
        if (block == null || !isCarrierBlock(block)) return;
        // Zwykły PPM otworzyłby GUI beczki (i po zamknięciu odsłonił blok).
        event.setCancelled(true);
    }

    @EventHandler
    public void onCarrierBreak(org.bukkit.event.block.BlockBreakEvent event) {
        org.bukkit.block.Block block = event.getBlock();
        if (isCarrierBlock(block)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onCarrierExplode(org.bukkit.event.block.BlockExplodeEvent event) {
        event.blockList().removeIf(this::isCarrierBlock);
    }

    @EventHandler
    public void onCarrierEntityExplode(org.bukkit.event.entity.EntityExplodeEvent event) {
        event.blockList().removeIf(this::isCarrierBlock);
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Interaction interaction)) return;

        Player player = event.getPlayer();
        UUID interactionId = interaction.getUniqueId();

        // Sprawdź wiatraki (2×2×6)
        WindmillDevice windmill = windmills.get(interactionId);
        if (windmill != null) {
            int slotIndex = windmill.getSlotIndexByUUID(interactionId);
            if (slotIndex >= 0) {
                windmill.onBatterySlotClick(player, slotIndex);
                event.setCancelled(true);
                return;
            }
        }

        // Sprawdź urządzenia 2×2
        DeviceBlock2x2 device = devices.get(interactionId);
        if (device == null) return;

        if (interaction.equals(device.getBatterySlot())) {
            device.onBatterySlotClick(player);
            event.setCancelled(true);
        } else if (interaction.equals(device.getFunctionalSlot())) {
            device.onFunctionalSlotClick(player);
            event.setCancelled(true);
        }
    }
}
