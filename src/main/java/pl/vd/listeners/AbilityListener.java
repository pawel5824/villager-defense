package pl.vd.listeners;

import net.kyori.adventure.text.Component;
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

        Arena arena = findArenaOf(player);
        if (arena == null || arena.getState() != ArenaState.INGAME) {
            player.sendMessage(Component.text("§cTej umiejętności można używać tylko w trakcie gry."));
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
