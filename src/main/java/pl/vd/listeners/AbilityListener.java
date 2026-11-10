package pl.vd.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import pl.vd.VillagerDefensePlugin;
import pl.vd.ability.AbilityType;
import pl.vd.arena.Arena;
import pl.vd.arena.ArenaState;

public class AbilityListener implements Listener {

    private final VillagerDefensePlugin plugin;

    public AbilityListener(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        AbilityType type = plugin.getAbilityManager().getAbilityType(event.getItem());
        if (type == null) return;

        event.setCancelled(true);
        Player player = event.getPlayer();

        // duch po śmierci (spectator) NIE może używać umiejętności - żadnych strzałów z różdżki
        if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
            return;
        }

        Arena arena = findArenaOf(player);
        if (arena == null || arena.getState() != ArenaState.INGAME) {
            pl.vd.util.AntiSpam.send(player, "§cTej umiejętności można używać tylko w trakcie gry.");
            return;
        }

        plugin.getAbilityManager().use(player, type);
    }

    private Arena findArenaOf(Player player) {
        for (Arena arena : plugin.getArenaManager().getArenas().values()) {
            if (arena.isPlaying(player)) return arena;
        }
        return null;
    }
}
