package pl.vd.vslab;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class VerticalSlabManager {

    /** Niewielki margines, żeby wizualna płytka nie "migotała" (z-fighting) z sąsiednimi prawdziwymi blokami. */
    private static final float EPSILON = 0.002f;

    /** Wszystkie postawione w tej sesji wizualne płytki (do /vslab undo i /vslab clear). */
    private final List<BlockDisplay> placed = new ArrayList<>();

    /** Płytka -> blok, w którym została postawiona (dokładne dopasowanie przy usuwaniu). */
    private final Map<BlockDisplay, Location> origins = new HashMap<>();

    /** Domyślne przesunięcie (nudge) stawiania płytek - patrz /vslab offset. */
    private final Vector3f offset = new Vector3f(0f, 0f, 0f);

    public void setOffset(double x, double y, double z) {
        offset.set((float) x, (float) y, (float) z);
    }

    public Vector3f getOffset() {
        return offset;
    }

    /**
     * Stawia wizualną, pionową "połówkę bloku" (BlockDisplay) w podanej lokalizacji bloku,
     * przylegającą do wskazanej ściany (NORTH/SOUTH/EAST/WEST), bez obrotu.
     *
     * Najlepiej sprawdza się w pustym miejscu (powietrzu) - jeśli w tej lokalizacji stoi już
     * prawdziwy, pełny blok, wizualna płytka będzie w niego częściowo "wbita".
     */
    public BlockDisplay place(Location blockLocation, Material material, BlockFace face) {
        return place(blockLocation, material, face, 0f);
    }

    /**
     * Stawia wizualną, pionową "połówkę bloku" (BlockDisplay) w podanej lokalizacji bloku,
     * przylegającą do wskazanej ściany (NORTH/SOUTH/EAST/WEST), z opcjonalnym obrotem
     * wokół osi Y (w stopniach).
     *
     * Obrót dodatni = zgodnie ze wskazówkami zegara (patrząc z góry, jak yaw w Minecrafcie):
     * - 90/180/270 - płytka na sąsiednich ścianach (np. EAST + 90 = SOUTH),
     * - 45/135/... - płytka ukośna (po przekątnej bloku),
     * - 0 - dokładnie jak bez obrotu.
     */
    public BlockDisplay place(Location blockLocation, Material material, BlockFace face, float rotationDegrees) {
        return place(blockLocation, material, face, rotationDegrees, offset);
    }

    /**
     * Stawia płytkę z dodatkowym przesunięciem (offset) względem narożnika bloku -
     * dzięki temu można stawiać nie tylko "w środku", ale też na krawędzi/części
     * bloku, w dowolnym miejscu (współrzędne nie muszą być całkowite).
     */
    public BlockDisplay place(Location blockLocation, Material material, BlockFace face, float rotationDegrees,
                              Vector3f offset) {
        Location origin = blockLocation.getBlock().getLocation().add(offset.x, offset.y, offset.z);
        BlockDisplay display = origin.getWorld().spawn(origin, BlockDisplay.class);
        display.setBlock(Bukkit.createBlockData(material));

        // Oś, w której "ściskamy" blok do połowy grubości (X dla wschód/zachód, Z dla północ/południe)
        float scaleX = 1f, scaleZ = 1f;
        float offsetX = 0f, offsetZ = 0f;

        switch (face) {
            case EAST -> { scaleX = 0.5f; offsetX = 0.5f + EPSILON; }
            case WEST -> { scaleX = 0.5f; offsetX = -EPSILON; }
            case SOUTH -> { scaleZ = 0.5f; offsetZ = 0.5f + EPSILON; }
            case NORTH -> { scaleZ = 0.5f; offsetZ = -EPSILON; }
            default -> throw new IllegalArgumentException("Obsługiwane kierunki: NORTH, SOUTH, EAST, WEST");
        }

        // Obrót całej "płytki" (skala + przesunięcie) wokół osi Y bloku.
        // Znak minus, żeby dodatni kąt kręcił zgodnie ze wskazówkami zegara (konwencja yaw).
        double rad = -Math.toRadians(rotationDegrees);
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);

        // Przesunięcie obraca się razem z płytką, żeby została w obrębie swojego bloku.
        float tx = offsetX * cos - offsetZ * sin;
        float tz = offsetX * sin + offsetZ * cos;

        Vector3f translation = new Vector3f(tx, 0f, tz);
        Vector3f scale = new Vector3f(scaleX, 1f, scaleZ);
        Transformation transformation = new Transformation(
                translation,
                new AxisAngle4f((float) rad, 0f, 1f, 0f), // obrót wokół osi Y
                scale,
                new AxisAngle4f(0f, 0f, 0f, 1f)
        );
        display.setTransformation(transformation);

        placed.add(display);
        origins.put(display, blockLocation.getBlock().getLocation());
        return display;
    }

    /**
     * Usuwa ostatnią postawioną wizualną płytkę (cofanie pojedynczego /vslab).
     * Zwraca true, jeśli udało się cokolwiek usunąć.
     */
    public boolean undo() {
        for (int i = placed.size() - 1; i >= 0; i--) {
            BlockDisplay display = placed.remove(i);
            if (display != null && !display.isDead()) {
                origins.remove(display);
                display.remove();
                return true;
            }
        }
        return false;
    }

    /** Usuwa wizualną płytkę w podanej lokalizacji bloku (dowolny materiał). */
    public boolean removeAt(Location blockLocation) {
        return findAndRemove(blockLocation.getBlock(), null);
    }

    /** Usuwa wizualną płytkę z konkretnego materiału w podanej lokalizacji bloku. */
    public boolean removeAt(Location blockLocation, Material material) {
        return findAndRemove(blockLocation.getBlock(), material);
    }

    /**
     * Znajduje i usuwa płytkę w bloku (opcjonalnie tylko z danego materiału).
     * Najpierw szuka wśród postawionych w tej sesji po ZAPAMIĘTANEJ pozycji bloku
     * (dzięki temu działa też z offsetem, który mógłby przesunąć lokalizację
     * encji do sąsiedniego bloku), potem skanuje okolicę (płytki sprzed restartu),
     * pomijając pasażerów - żeby nie usunąć przypadkiem części /stojak.
     */
    private boolean findAndRemove(Block block, Material material) {
        Iterator<Map.Entry<BlockDisplay, Location>> it = origins.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockDisplay, Location> en = it.next();
            BlockDisplay display = en.getKey();
            if (display == null || display.isDead()) {
                it.remove();
                continue;
            }
            if (material != null && display.getBlock().getMaterial() != material) continue;
            Location o = en.getValue();
            if (o.getBlockX() == block.getX() && o.getBlockY() == block.getY()
                    && o.getBlockZ() == block.getZ()) {
                display.remove();
                it.remove();
                placed.remove(display);
                return true;
            }
        }
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        for (Entity entity : block.getWorld().getNearbyEntities(center, 2, 2, 2)) {
            if (!(entity instanceof BlockDisplay display) || display.getVehicle() != null) continue;
            if (material != null && display.getBlock().getMaterial() != material) continue;
            if (display.getLocation().getBlockX() == block.getX()
                    && display.getLocation().getBlockY() == block.getY()
                    && display.getLocation().getBlockZ() == block.getZ()) {
                display.remove();
                placed.remove(display);
                origins.remove(display);
                return true;
            }
        }
        return false;
    }

    /**
     * Usuwa wszystkie wizualne płytki we wszystkich wczytanych światach -
     * zarówno te postawione w tej sesji, jak i starsze (np. sprzed restartu serwera,
     * które przetrwały w chunkach jako encje). Zwraca liczbę usuniętych płytek.
     */
    public int clearAll() {
        int count = 0;
        for (BlockDisplay display : placed) {
            if (display != null && !display.isDead()) {
                display.remove();
                count++;
            }
        }
        placed.clear();
        origins.clear();
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof BlockDisplay display && !display.isDead()) {
                    display.remove();
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Usuwa wszystkie wizualne płytki z podanego bloku we wszystkich wczytanych
     * światach - i te postawione w tej sesji, i starsze (sprzed restartu).
     * Płytki będące pasażerami stojaków (części /stojak, wieżyczki) są pomijane,
     * żeby nie usunąć przypadkiem dekoracji pluginu. Zwraca liczbę usuniętych.
     */
    public int clearAll(Material material) {
        int count = 0;
        // 1) płytki postawione w tej sesji (śledzone w pamięci) - zawsze vslab
        Iterator<Map.Entry<BlockDisplay, Location>> it = origins.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockDisplay, Location> en = it.next();
            BlockDisplay display = en.getKey();
            if (display == null || display.isDead()) {
                it.remove();
                continue;
            }
            if (display.getBlock().getMaterial() != material) continue;
            display.remove();
            it.remove();
            placed.remove(display);
            count++;
        }
        // 2) starsze płytki (np. sprzed restartu, poza mapą pamięci) - skan świata,
        //    pomijając pasażerów stojaków, żeby nie ruszać części /stojak
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (!(entity instanceof BlockDisplay display) || display.isDead()) continue;
                if (display.getVehicle() != null) continue;
                if (display.getBlock().getMaterial() != material) continue;
                display.remove();
                count++;
            }
        }
        return count;
    }

    /** Czy ta płytka należy do /vslab (pomijana przez /stojak sprzataj). */
    public boolean isManaged(BlockDisplay display) {
        return placed.contains(display) || origins.containsKey(display);
    }

    public BlockFace parseFace(String raw, BlockFace fallback) {
        if (raw == null) return fallback;
        return switch (raw.toLowerCase()) {
            case "north", "n" -> BlockFace.NORTH;
            case "south", "s" -> BlockFace.SOUTH;
            case "east", "e" -> BlockFace.EAST;
            case "west", "w" -> BlockFace.WEST;
            default -> fallback;
        };
    }
}
