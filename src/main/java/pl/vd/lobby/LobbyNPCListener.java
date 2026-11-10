package pl.vd.lobby;

import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import pl.vd.VillagerDefensePlugin;
import pl.vd.util.AntiSpam;

/**
 * Obsługuje kliknięcie NPC w lobby → otwiera GUI z wyborem trybu gry.
 */
public class LobbyNPCListener implements Listener {

    private final VillagerDefensePlugin plugin;

    public LobbyNPCListener(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof ArmorStand stand)) return;
        if (!stand.getScoreboardTags().contains("vd_lobby_npc")) return;
        if (e.getHand() != EquipmentSlot.HAND) return;

        Player player = e.getPlayer();

        LobbyNPC npc = findByStand(stand);
        if (npc == null) return;

        // otwórz GUI z wyborem trybu gry
        plugin.getGameModeGUI().open(player);
    }

    private LobbyNPC findByStand(ArmorStand stand) {
        for (LobbyNPC npc : plugin.getLobbyNPCManager().getAll()) {
            if (npc.isSpawned() && npc.getStand() != null
                    && npc.getStand().equals(stand)) {
                return npc;
            }
        }
        return null;
    }
}
