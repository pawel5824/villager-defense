package pl.vd.listeners;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Przywraca bloki pod pajęczynami po ich usunięciu.
 *
 * Gdy gracz stawia pajęczynę → zapamiętaj blok pod spodem (np. GRASS_BLOCK).
 * Gdy pajęczyna zostanie usunięta → przywróć zapisany blok.
 *
 * Działa na kopii mapy (instancji) — szablon nigdy nie jest modyfikowany,
 * więc po restarcie gry bloki wracają do stanu początkowego.
 */
public class CobwebRestoreListener implements Listener {

    /**
     * Zapisane bloki pod pajęczynami: "world:x:y:z" →typ bloku do przywrócenia.
     * Klucz = pozycja BLOKU POD pajęczyną, wartość = zapisany typ.
     */
    private final Map<String, Material> savedBlocks = new ConcurrentHashMap<>();

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Block placed = event.getBlock();

        // tylko pajęczyny
        if (placed.getType() != Material.COBWEB) return;

        // zapisz blok pod spodem
        Block below = placed.getRelative(org.bukkit.block.BlockFace.DOWN);
        if (below.getType() != Material.AIR && below.getType() != Material.CAVE_AIR
                && below.getType() != Material.VOID_AIR) {
            String key = blockKey(below);
            // nie nadpisuj jeśli już jest zapisany (np. kilka pajęczyn na sobie)
            savedBlocks.putIfAbsent(key, below.getType());
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block broken = event.getBlock();

        // tylko pajęczyny
        if (broken.getType() != Material.COBWEB) return;

        // sprawdź blok pod spodem — tam mogliśmy zapisać oryginalny blok
        Block below = broken.getRelative(org.bukkit.block.BlockFace.DOWN);
        String key = blockKey(below);
        Material saved = savedBlocks.remove(key);
        if (saved != null && saved != below.getType()) {
            below.setType(saved, false);
        }

        // sprawdź też sam blok pajęczyny (gdy pajęczyna była na Y=0 itp.)
        String selfKey = blockKey(broken);
        Material savedSelf = savedBlocks.remove(selfKey);
        if (savedSelf != null) {
            broken.setType(savedSelf, false);
        }
    }

    /**
     * Czyści zapisane bloki (wołane po zakończeniu gry).
     */
    public void clear() {
        savedBlocks.clear();
    }

    private String blockKey(Block block) {
        return block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }
}
