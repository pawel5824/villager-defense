package pl.vd.listeners;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import pl.vd.VillagerDefensePlugin;
import pl.vd.barrier.BarrierManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Blokuje ruch graczy do stref barier (niewidzialne, bez bloków).
 * Teleportuje o 0.5 bloka od strony wejścia na najbliższy stały blok.
 * Wejście od góry → wróć do ostatniej bezpiecznej pozycji (przed skokiem).
 */
public class BarrierListener implements Listener {

    private final VillagerDefensePlugin plugin;

    /** Ostatnia znana BEZPIECZNA pozycja gracza (przed wejściem w barierę). */
    private final Map<UUID, Location> lastSafePosition = new ConcurrentHashMap<>();

    public BarrierListener(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        BarrierManager bm = plugin.getBarrierManager();
        String worldName = to.getWorld().getName();
        boolean toBlocked = bm.isBlocked(worldName, to.getX(), to.getY(), to.getZ());
        boolean fromBlocked = bm.isBlocked(worldName, from.getX(), from.getY(), from.getZ());

        if (toBlocked) {
            // gracz wchodzi do bariery
            Location safe;

            if (!fromBlocked && isFallingIntoBarrier(from, to)) {
                // wchodzi od GÓRY → wróć do ostatniej bezpiecznej pozycji
                safe = lastSafePosition.getOrDefault(player.getUniqueId(), from);
            } else if (!fromBlocked) {
                // wchodzi z boku → teleportuj 0.5 bloka od strony wejścia
                safe = getSideEjection(from, to, bm, worldName);
            } else {
                // już w barierze → wypchnij do najbliższego brzegu
                safe = getSideEjection(from, to, bm, worldName);
            }

            // znajdź najbliższy stały blok pod stopami
            safe = findGround(safe);
            event.setTo(safe);
            return;
        }

        // gracz poza barierą → zapisz bezpieczną pozycję
        lastSafePosition.put(player.getUniqueId(), from.clone());
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        if (to == null) return;

        BarrierManager bm = plugin.getBarrierManager();
        if (!bm.isBlocked(to.getWorld().getName(), to.getX(), to.getY(), to.getZ())) return;

        event.setCancelled(true);
        Location safe = lastSafePosition.getOrDefault(player.getUniqueId(), event.getFrom());
        safe = findGround(safe);
        player.teleport(safe);
    }

    /**
     * Sprawdza czy gracz wchodzi od GÓRY (Y maleje, X/Z bez zmian lub minimalne).
     */
    private boolean isFallingIntoBarrier(Location from, Location to) {
        double dy = from.getY() - to.getY(); // >0 = spada
        double dx = Math.abs(to.getX() - from.getX());
        double dz = Math.abs(to.getZ() - from.getZ());
        // spada i X/Z się prawie nie zmieniają = wchodzi od góry
        return dy > 0.3 && dx < 0.5 && dz < 0.5;
    }

    /**
     * Wypycha gracza 0.5 bloka od bariery w kierunku, z którego przyszedł.
     */
    private Location getSideEjection(Location from, Location to, BarrierManager bm, String worldName) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();

        // kierunek wejścia → wypchnij w odwrotną stronę
        double pushX = 0, pushZ = 0;
        if (Math.abs(dx) > Math.abs(dz)) {
            pushX = dx > 0 ? -0.03 : 0.03;
        } else {
            pushZ = dz > 0 ? -0.03 : 0.03;
        }

        double newX = to.getX() + pushX;
        double newZ = to.getZ() + pushZ;

        // sprawdź czy nowa pozycja nie jest w barierze
        if (bm.isBlocked(worldName, newX, to.getY(), newZ)) {
            // spróbuj drugą oś
            newX = to.getX();
            newZ = to.getZ();
            if (Math.abs(dx) > Math.abs(dz)) {
                newZ = dz >= 0 ? to.getZ() - 0.03 : to.getZ() + 0.03;
            } else {
                newX = dx >= 0 ? to.getX() - 0.03 : to.getX() + 0.03;
            }
        }

        return new Location(to.getWorld(), newX, to.getY(), newZ);
    }

    /**
     * Znajduje najbliższy stały blok (nie powietrze, nie woda) pod daną pozycją.
     */
    private Location findGround(Location loc) {
        if (loc == null || loc.getWorld() == null) return loc;
        Block block = loc.getBlock();

        // jeśli bloczek na pozycji gracza jest stały, szukaj od góry
        // jeśli nie — szukaj w dół
        int x = block.getX();
        int z = block.getZ();
        int startY = block.getY();

        // szukaj w dół od pozycji gracza do Y=-64
        for (int y = startY; y >= loc.getWorld().getMinHeight(); y--) {
            Block check = loc.getWorld().getBlockAt(x, y, z);
            if (check.getType().isSolid()) {
                // stały blok znaleziony — ustaw gracza na wierzchu
                return new Location(loc.getWorld(), loc.getX(), y + 1.0, loc.getZ(),
                        loc.getYaw(), loc.getPitch());
            }
        }

        // nie znaleziono podłoża — zostaw jak jest
        return loc;
    }
}
