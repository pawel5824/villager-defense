package pl.vd.listeners;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.scheduler.BukkitRunnable;
import pl.vd.VillagerDefensePlugin;

import java.util.*;

/**
 * Permanentny ogień — włączony per-world komendą /def ogien_staly on|off.
 * Ogień nigdy nie gaśnie i nie spala bloków (jak netherrack, ale na każdym bloku).
 *
 * Mechanika:
 *  - BlockBurnEvent → anuluj (ogień nie niszczy bloków)
 *  - co 1s sprawdzaj wszystkie bloki z ogniem w świecie → jeśli ogień gaśnie, od-nów
 *  - ·particle ·dym ·crackles — oznaczone bloki mają wieczny ogień
 */
public class PermanentFireListener implements Listener {

    private final VillagerDefensePlugin plugin;

    /** Światy z włączonym permanentnym ogniem. */
    private final Set<String> enabledWorlds = new HashSet<>();

    /** Co ile ticków sprawdzać i odnawiać ogień (20 = 1 sekunda). */
    private static final long CHECK_INTERVAL = 20L;

    public PermanentFireListener(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
    }

    /** Włącza/wyłącza permanentny ogień w świecie gracza. */
    public boolean toggle(World world) {
        String name = world.getName();
        if (enabledWorlds.contains(name)) {
            enabledWorlds.remove(name);
            return false; // wyłączone
        } else {
            enabledWorlds.add(name);
            startChecker();
            return true; // włączone
        }
    }

    /** Czy permanentny ogień jest włączony w danym świecie? */
    public boolean isEnabled(World world) {
        return enabledWorlds.contains(world.getName());
    }

    /** Zwraca listę światów z permanentnym ogniem. */
    public Set<String> getEnabledWorlds() {
        return Collections.unmodifiableSet(enabledWorlds);
    }

    // --- Eventy ---

    @EventHandler
    public void onBlockBurn(BlockBurnEvent event) {
        Block block = event.getBlock();
        if (!enabledWorlds.contains(block.getWorld().getName())) return;
        // anuluj spalanie bloku — ogień nie niszczy niczego
        event.setCancelled(true);
    }

    @EventHandler
    public void onBlockIgnite(BlockIgniteEvent event) {
        // pozwól na zapalanie (gracz z flint and steel, lava itd.)
        // ogień się zapali, a checker go utrzyma
    }

    // --- Checker: odnawiający gaśniejący ogień ---

    private boolean checkerRunning = false;

    private void startChecker() {
        if (checkerRunning) return;
        if (enabledWorlds.isEmpty()) return;
        checkerRunning = true;

        new BukkitRunnable() {
            @Override
            public void run() {
                if (enabledWorlds.isEmpty()) {
                    checkerRunning = false;
                    cancel();
                    return;
                }
                for (String worldName : enabledWorlds) {
                    World world = plugin.getServer().getWorld(worldName);
                    if (world == null) continue;
                    reigniteFires(world);
                }
            }
        }.runTaskTimer(plugin, CHECK_INTERVAL, CHECK_INTERVAL);
    }

    /**
     * Przeszukuje chunki w świecie i odnawia ogień na blokach,
     * które mają ogień nad sobą, ale same są palnymi blokami (nie netherrack).
     * Logika: jeśli blok ma Material.FIRE nad nim, ale sam nie jest netherrack/magma
     * i jest w świecie z permanentnym ogniem → zapal go ponownie.
     */
    private void reigniteFires(World world) {
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();
        for (var chunk : world.getLoadedChunks()) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = minY; y < maxY; y++) {
                        Block block = chunk.getBlock(x, y, z);
                        if (block.getType() == Material.FIRE) {
                            Block below = block.getRelative(org.bukkit.block.BlockFace.DOWN);
                            if (isNetherrackLike(below.getType())) continue;
                            block.setType(Material.FIRE, false);
                        }
                    }
                }
            }
        }
    }

    /** Bloki, na których ogień jest naturalnie wieczny (vanilla). */
    private boolean isNetherrackLike(Material mat) {
        return mat == Material.NETHERRACK || mat == Material.MAGMA_BLOCK
                || mat == Material.SOUL_SOIL || mat == Material.SOUL_SAND;
    }
}
