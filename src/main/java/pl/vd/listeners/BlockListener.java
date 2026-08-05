package pl.vd.listeners;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.block.BlockBurnEvent;
import pl.vd.VillagerDefensePlugin;
import pl.vd.arena.Arena;

public class BlockListener implements Listener {

    private final VillagerDefensePlugin plugin;

    public BlockListener(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (isInArena(event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("§cNie możesz niszczyć bloków w arenie."));
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (isInArena(event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("§cNie możesz stawiać bloków w arenie."));
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        if (event == null || event.blockList() == null) return;
        // if explosion occurs within any arena, cancel its block changes
        for (Arena arena : plugin.getArenaManager().getArenas().values()) {
            // check if any block is inside arena villager or player spawns (simple heuristic)
            if (arena.getPlayerSpawns().isEmpty() && arena.getVillagerSpawns().isEmpty()) continue;
            var sample = !arena.getPlayerSpawns().isEmpty() ? arena.getPlayerSpawns().get(0) : arena.getVillagerSpawns().get(0);
            if (sample == null) continue;
            for (var block : event.blockList()) {
                if (block.getLocation().getWorld().equals(sample.getWorld())) {
                    // cancel block damage inside arena world altogether
                    event.blockList().clear();
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onBlockBurn(BlockBurnEvent event) {
        if (event == null || event.getBlock() == null) return;
        for (Arena arena : plugin.getArenaManager().getArenas().values()) {
            if (arena.getPlayerSpawns().isEmpty() && arena.getVillagerSpawns().isEmpty()) continue;
            var sample = !arena.getPlayerSpawns().isEmpty() ? arena.getPlayerSpawns().get(0) : arena.getVillagerSpawns().get(0);
            if (sample == null) continue;
            if (event.getBlock().getWorld().equals(sample.getWorld())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private boolean isInArena(Player player) {
        for (Arena arena : plugin.getArenaManager().getArenas().values()) {
            if (arena.isPlaying(player)) return true;
        }
        return false;
    }
}
