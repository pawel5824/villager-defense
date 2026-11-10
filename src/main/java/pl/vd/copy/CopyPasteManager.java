package pl.vd.copy;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import pl.vd.brush.BlockSnapshot;
import pl.vd.brush.BrushManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CopyPasteManager {

    private final BrushManager brushManager;
    private final Map<UUID, Location> pos1 = new HashMap<>();
    private final Map<UUID, Location> pos2 = new HashMap<>();
    private final Map<UUID, List<RelativeBlock>> clipboards = new HashMap<>();

    public CopyPasteManager(BrushManager brushManager) {
        this.brushManager = brushManager;
    }

    // ---------- Zaznaczanie ----------

    public void setPos1(Player player, Location location) {
        pos1.put(player.getUniqueId(), location.getBlock().getLocation());
    }

    public void setPos2(Player player, Location location) {
        pos2.put(player.getUniqueId(), location.getBlock().getLocation());
    }

    public Location getPos1(Player player) { return pos1.get(player.getUniqueId()); }
    public Location getPos2(Player player) { return pos2.get(player.getUniqueId()); }

    // ---------- Kopiowanie ----------

    /** Zwraca liczbę skopiowanych bloków, albo -1 jeśli zaznaczenie jest niepoprawne (np. różne światy / brak punktów). */
    public int copy(Player player) {
        Location p1 = pos1.get(player.getUniqueId());
        Location p2 = pos2.get(player.getUniqueId());
        if (p1 == null || p2 == null) return -1;
        if (p1.getWorld() == null || !p1.getWorld().equals(p2.getWorld())) return -1;

        World world = p1.getWorld();
        int minX = Math.min(p1.getBlockX(), p2.getBlockX());
        int minY = Math.min(p1.getBlockY(), p2.getBlockY());
        int minZ = Math.min(p1.getBlockZ(), p2.getBlockZ());
        int maxX = Math.max(p1.getBlockX(), p2.getBlockX());
        int maxY = Math.max(p1.getBlockY(), p2.getBlockY());
        int maxZ = Math.max(p1.getBlockZ(), p2.getBlockZ());

        // Punkt odniesienia do wklejania = pozycja gracza w momencie kopiowania
        Location origin = player.getLocation();
        List<RelativeBlock> blocks = new ArrayList<>();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    int dx = x - origin.getBlockX();
                    int dy = y - origin.getBlockY();
                    int dz = z - origin.getBlockZ();
                    blocks.add(new RelativeBlock(dx, dy, dz, block.getBlockData()));
                }
            }
        }

        clipboards.put(player.getUniqueId(), blocks);
        return blocks.size();
    }

    // ---------- Wklejanie ----------

    public boolean hasClipboard(Player player) {
        return clipboards.containsKey(player.getUniqueId());
    }

    /** Wkleja schowek względem aktualnej pozycji gracza. Zwraca liczbę wklejonych bloków (0 jeśli brak schowka). */
    public int paste(Player player) {
        List<RelativeBlock> blocks = clipboards.get(player.getUniqueId());
        if (blocks == null || blocks.isEmpty()) return 0;

        Location origin = player.getLocation();
        World world = origin.getWorld();
        List<BlockSnapshot> snapshots = new ArrayList<>();

        for (RelativeBlock rel : blocks) {
            Block target = world.getBlockAt(
                    origin.getBlockX() + rel.dx(),
                    origin.getBlockY() + rel.dy(),
                    origin.getBlockZ() + rel.dz()
            );
            snapshots.add(new BlockSnapshot(target.getLocation(), target.getBlockData()));
            target.setBlockData(rel.data(), false);
        }

        // Wklejenie liczy się jako jedna akcja we wspólnym stosie cofania z /brush undo
        brushManager.pushUndo(player, snapshots);
        return blocks.size();
    }

    public int clipboardSize(Player player) {
        List<RelativeBlock> blocks = clipboards.get(player.getUniqueId());
        return blocks == null ? 0 : blocks.size();
    }

    // ---------- Transformacje schowka (obrót / lustro) ----------

    /** Obraca zawartość schowka o 90*times stopni (1=90°, 2=180°, 3=270°). Zwraca false, jeśli schowek pusty. */
    public boolean rotateClipboard(Player player, int quarterTurns) {
        List<RelativeBlock> blocks = clipboards.get(player.getUniqueId());
        if (blocks == null || blocks.isEmpty()) return false;

        int turns = ((quarterTurns % 4) + 4) % 4; // znormalizuj do 0-3
        for (int i = 0; i < turns; i++) {
            blocks = ClipboardTransformer.rotate90(blocks);
        }
        clipboards.put(player.getUniqueId(), blocks);
        return true;
    }

    /** Odbija zawartość schowka lustrzanie wzdłuż osi X lub Z. Zwraca false, jeśli schowek pusty. */
    public boolean mirrorClipboard(Player player, ClipboardTransformer.MirrorAxis axis) {
        List<RelativeBlock> blocks = clipboards.get(player.getUniqueId());
        if (blocks == null || blocks.isEmpty()) return false;

        clipboards.put(player.getUniqueId(), ClipboardTransformer.mirror(blocks, axis));
        return true;
    }
}
