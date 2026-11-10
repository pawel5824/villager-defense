package pl.vd.listeners;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.block.BlockBurnEvent;
import pl.vd.VillagerDefensePlugin;
import pl.vd.arena.Arena;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class BlockListener implements Listener {

    private final VillagerDefensePlugin plugin;

    /** Postawione przez graczy pajęczyny (świat -> pozycje) - sprzątane po grze. */
    private final Map<String, Set<Location>> placedCobwebs = new ConcurrentHashMap<>();

    public BlockListener(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Usuwa wszystkie postawione pajęczyny w danym świecie (po zakończeniu gry).
     */
    public void removePlacedCobwebs(World world) {
        if (world == null) return;
        Set<Location> locs = placedCobwebs.remove(world.getName());
        if (locs == null) return;
        for (Location loc : locs) {
            if (loc.getWorld() != null && loc.getWorld().equals(world)
                    && loc.getBlock().getType() == Material.COBWEB) {
                loc.getBlock().setType(Material.AIR);
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (isInArena(event.getPlayer())) {
            // Cicho: gracz widzi tylko, że blok się nie niszczy (§1 - zero komunikatów).
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (isInArena(event.getPlayer())) {
            // na arenie można stawiać wyłącznie pajęczynę (do spowalniania mobów)
            if (event.getBlockPlaced().getType() == Material.COBWEB) {
                // zapamiętaj, żeby posprzątać ją po zakończeniu gry
                Location loc = event.getBlockPlaced().getLocation();
                placedCobwebs.computeIfAbsent(loc.getWorld().getName(), k -> ConcurrentHashMap.newKeySet()).add(loc);
                return; // dozwolone
            }
            event.setCancelled(true);
            pl.vd.util.AntiSpam.send(event.getPlayer(), "§cNa arenie możesz stawiać tylko pajęczynę.");
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
