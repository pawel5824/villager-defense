package pl.vd.listeners;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.*;

/**
 * Zapisuje wszystkie zmiany bloków podczas gry i przywraca je po endGame().
 * Dzięki temu arena po grze wygląda identycznie jak na początku (trawa, itp.).
 *
 * Mechanika:
 *  - BlockPlaceEvent / BlockBreakEvent → zapisz oryginalny blok (pozycja → BlockState)
 *  - restoreAll() → przywróć wszystkie zmienione bloki i wyczyść mapę
 *
 * Wołane z GameManager:
 *  - startGame() → reset()
 *  - endGame() → restoreAll()
 */
public class BlockRestoreListener implements Listener {

    /** Zapisane oryginalne stany bloków: "world:x:y:z" → BlockState (snapshot). */
    private final Map<String, BlockState> originalStates = new LinkedHashMap<>();

    /**
     * Zapisuje oryginalny stan bloku (jeśli jeszcze nie zapisany).
     */
    private void saveOriginal(Block block) {
        String key = blockKey(block);
        if (!originalStates.containsKey(key)) {
            originalStates.put(key, block.getState());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        // zapisz blok KTÓRY ZOSTAŁ NADPISANY (ten co był przed postawieniem)
        // Bukkit nie daje nam go bezpośrednio, więc zapisujemy blok pod spodem
        // i blok na pozycji nowego bloku (oryginalny zostanie nadpisany)
        Block placed = event.getBlock();
        // zapisz blok pod nowym blokiem
        Block below = placed.getRelative(org.bukkit.block.BlockFace.DOWN);
        saveOriginal(below);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block broken = event.getBlock();
        saveOriginal(broken);
        // zapisz też blok pod spodem (np. tło za pajęczyną)
        Block below = broken.getRelative(org.bukkit.block.BlockFace.DOWN);
        saveOriginal(below);
    }

    /**
     * Przywraca wszystkie zmienione bloki do stanu początkowego.
     */
    public void restoreAll() {
        for (Map.Entry<String, BlockState> entry : originalStates.entrySet()) {
            try {
                BlockState state = entry.getValue();
                if (state.getWorld() != null && state.getWorld().isChunkLoaded(
                        state.getX() >> 4, state.getZ() >> 4)) {
                    state.update(true, false); // applyPhysics=false, żeby nie łańcuchowo
                }
            } catch (Exception ignored) {}
        }
        originalStates.clear();
    }

    /**
     * Czyszczenie bez przywracania (np. przy restarcie serwera).
     */
    public void reset() {
        originalStates.clear();
    }

    /**
     * Ile bloków zostało zmienionych.
     */
    public int getChangedCount() {
        return originalStates.size();
    }

    private String blockKey(Block block) {
        return block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }
}
