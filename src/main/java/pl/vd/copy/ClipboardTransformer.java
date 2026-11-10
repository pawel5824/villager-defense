package pl.vd.copy;

import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.Rotatable;
import org.bukkit.block.BlockFace;

import java.util.ArrayList;
import java.util.List;

/**
 * Przekształcenia schowka kopiuj-wklej: obrót o 90 stopni (wokół osi pionowej Y)
 * oraz lustrzane odbicie wzdłuż osi X lub Z.
 *
 * Pozycje bloków są przekształcane zawsze w 100% poprawnie. Orientacja samego bloku
 * (w którą stronę są zwrócone schody, kłody, tabliczki itd.) jest przekształcana
 * dla najpopularniejszych typów danych bloku (Directional, Orientable, Rotatable).
 * Bardzo egzotyczne bloki mogą nie obrócić swojej orientacji idealnie, ale ich
 * POZYCJA w strukturze zawsze będzie poprawna.
 */
public final class ClipboardTransformer {

    private ClipboardTransformer() {}

    /** 16-punktowy kompas w kolejności zgodnej z ruchem wskazówek zegara (jak w Rotatable#getRotation). */
    private static final BlockFace[] COMPASS_16 = {
            BlockFace.NORTH, BlockFace.NORTH_NORTH_EAST, BlockFace.NORTH_EAST, BlockFace.EAST_NORTH_EAST,
            BlockFace.EAST, BlockFace.EAST_SOUTH_EAST, BlockFace.SOUTH_EAST, BlockFace.SOUTH_SOUTH_EAST,
            BlockFace.SOUTH, BlockFace.SOUTH_SOUTH_WEST, BlockFace.SOUTH_WEST, BlockFace.WEST_SOUTH_WEST,
            BlockFace.WEST, BlockFace.WEST_NORTH_WEST, BlockFace.NORTH_WEST, BlockFace.NORTH_NORTH_WEST
    };

    // ================= OBRÓT O 90 STOPNI (zgodnie z ruchem wskazówek zegara, patrząc z góry) =================

    public static List<RelativeBlock> rotate90(List<RelativeBlock> input) {
        List<RelativeBlock> result = new ArrayList<>(input.size());
        for (RelativeBlock rb : input) {
            int newDx = -rb.dz();
            int newDz = rb.dx();
            BlockData rotatedData = rotateBlockData(rb.data());
            result.add(new RelativeBlock(newDx, rb.dy(), newDz, rotatedData));
        }
        return result;
    }

    private static BlockData rotateBlockData(BlockData data) {
        BlockData clone = data.clone();

        if (clone instanceof Directional directional) {
            BlockFace face = directional.getFacing();
            BlockFace rotated = rotateCardinal(face);
            if (directional.getFaces().contains(rotated)) {
                directional.setFacing(rotated);
            }
        }
        if (clone instanceof Orientable orientable) {
            // Obrót o 90 stopni wokół osi Y zamienia oś X <-> Z, oś Y bez zmian
            switch (orientable.getAxis()) {
                case X -> orientable.setAxis(org.bukkit.Axis.Z);
                case Z -> orientable.setAxis(org.bukkit.Axis.X);
                default -> { /* Y bez zmian */ }
            }
        }
        if (clone instanceof Rotatable rotatable) {
            int index = indexOf16(rotatable.getRotation());
            if (index >= 0) {
                rotatable.setRotation(COMPASS_16[(index + 4) % 16]);
            }
        }
        return clone;
    }

    private static BlockFace rotateCardinal(BlockFace face) {
        return switch (face) {
            case NORTH -> BlockFace.EAST;
            case EAST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            case WEST -> BlockFace.NORTH;
            default -> face; // UP/DOWN i inne bez zmian
        };
    }

    // ================= LUSTRZANE ODBICIE =================

    public enum MirrorAxis { X, Z }

    public static List<RelativeBlock> mirror(List<RelativeBlock> input, MirrorAxis axis) {
        List<RelativeBlock> result = new ArrayList<>(input.size());
        for (RelativeBlock rb : input) {
            int newDx = axis == MirrorAxis.X ? -rb.dx() : rb.dx();
            int newDz = axis == MirrorAxis.Z ? -rb.dz() : rb.dz();
            BlockData mirrored = mirrorBlockData(rb.data(), axis);
            result.add(new RelativeBlock(newDx, rb.dy(), newDz, mirrored));
        }
        return result;
    }

    private static BlockData mirrorBlockData(BlockData data, MirrorAxis axis) {
        BlockData clone = data.clone();

        if (clone instanceof Directional directional) {
            BlockFace face = directional.getFacing();
            BlockFace mirrored = mirrorCardinal(face, axis);
            if (directional.getFaces().contains(mirrored)) {
                directional.setFacing(mirrored);
            }
        }
        // Orientable (kłody itp.) - oś nie zmienia się przy odbiciu lustrzanym, więc bez zmian

        if (clone instanceof Rotatable rotatable) {
            int index = indexOf16(rotatable.getRotation());
            if (index >= 0) {
                int mirroredIndex = axis == MirrorAxis.X
                        ? (16 - index) % 16   // odbicie wzdłuż X - wschód/zachód zamieniają się miejscami
                        : (24 - index) % 16;  // odbicie wzdłuż Z - północ/południe zamieniają się miejscami
                rotatable.setRotation(COMPASS_16[mirroredIndex]);
            }
        }
        return clone;
    }

    private static BlockFace mirrorCardinal(BlockFace face, MirrorAxis axis) {
        if (axis == MirrorAxis.X) {
            // odbicie wschód-zachód
            if (face == BlockFace.EAST) return BlockFace.WEST;
            if (face == BlockFace.WEST) return BlockFace.EAST;
        } else {
            // odbicie północ-południe
            if (face == BlockFace.NORTH) return BlockFace.SOUTH;
            if (face == BlockFace.SOUTH) return BlockFace.NORTH;
        }
        return face;
    }

    private static int indexOf16(BlockFace face) {
        for (int i = 0; i < COMPASS_16.length; i++) {
            if (COMPASS_16[i] == face) return i;
        }
        return -1;
    }
}
