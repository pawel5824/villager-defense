package pl.vd.brush;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import pl.vd.VillagerDefensePlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class BrushManager {

    private final VillagerDefensePlugin plugin;
    private final NamespacedKey toolKey;
    private final Map<UUID, BrushSettings> settings = new HashMap<>();
    private final Map<UUID, List<BlockSnapshot>> lastUndo = new HashMap<>();
    private final Random random = new Random();

    public BrushManager(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
        this.toolKey = new NamespacedKey(plugin, "brush_tool");
    }

    public BrushSettings getSettings(Player player) {
        return settings.computeIfAbsent(player.getUniqueId(), id -> new BrushSettings());
    }

    public boolean canUndo(Player player) {
        List<BlockSnapshot> snapshots = lastUndo.get(player.getUniqueId());
        return snapshots != null && !snapshots.isEmpty();
    }

    public int undo(Player player) {
        List<BlockSnapshot> snapshots = lastUndo.remove(player.getUniqueId());
        if (snapshots == null || snapshots.isEmpty()) return 0;

        for (int i = snapshots.size() - 1; i >= 0; i--) {
            BlockSnapshot snapshot = snapshots.get(i);
            Block block = snapshot.location().getBlock();
            block.setBlockData(snapshot.blockData(), false);
        }
        return snapshots.size();
    }

    private void recordUndo(Player player, List<BlockSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            lastUndo.remove(player.getUniqueId());
        } else {
            lastUndo.put(player.getUniqueId(), snapshots);
        }
    }

    private static record BlockSnapshot(Location location, BlockData blockData) {}


    // ---------- Narzędzie (motyka) ----------

    public ItemStack createBrushTool() {
        ItemStack item = new ItemStack(Material.WOODEN_HOE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6Pędzel Budowlany");
        meta.setLore(List.of(
                "§7Kliknij PPM w blok, aby użyć",
                "§7aktualnie wybranego brusha.",
                "§7Skonfiguruj go komendą §f/brush"
        ));
        meta.getPersistentDataContainer().set(toolKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isBrushTool(ItemStack item) {
        if (item == null || item.getType() != Material.WOODEN_HOE || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(toolKey, PersistentDataType.BYTE);
    }

    public void giveTool(Player player) {
        player.getInventory().addItem(createBrushTool());
    }

    // ---------- Stosowanie brushy ----------

    public void apply(Player player, Block clicked) {
        BrushSettings s = getSettings(player);
        Location center = clicked.getLocation();
        List<BlockSnapshot> snapshots = new ArrayList<>();

        switch (s.getType()) {
            case SPHERE -> applySphere(center, s.getRadius(), s.getPrimaryMaterial(), snapshots);
            case RANDOM -> applyRandom(center, s.getRadius(), s.getRandomMaterials(), snapshots);
            case OVERLAY -> applyOverlay(center, s.getRadius(), s.getOverlayThickness(),
                    s.getPrimaryMaterial(), s.getTopMaterial(), snapshots);
            case SMOOTH -> applySmooth(center, s.getRadius(), s.getSmoothIterations(), snapshots);
        }

        recordUndo(player, snapshots);
    }

    private void applySphere(Location center, int radius, Material material, List<BlockSnapshot> snapshots) {
        World world = center.getWorld();
        int bx = center.getBlockX(), by = center.getBlockY(), bz = center.getBlockZ();
        double rSquared = (radius + 0.5) * (radius + 0.5);

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x * x + y * y + z * z <= rSquared) {
                        Block block = world.getBlockAt(bx + x, by + y, bz + z);
                        snapshots.add(new BlockSnapshot(block.getLocation(), block.getBlockData()));
                        block.setType(material, false);
                    }
                }
            }
        }
    }

    private void applyRandom(Location center, int radius, List<Material> materials, List<BlockSnapshot> snapshots) {
        if (materials.isEmpty()) return;
        World world = center.getWorld();
        int bx = center.getBlockX(), by = center.getBlockY(), bz = center.getBlockZ();
        double rSquared = (radius + 0.5) * (radius + 0.5);

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x * x + y * y + z * z <= rSquared) {
                        Block block = world.getBlockAt(bx + x, by + y, bz + z);
                        snapshots.add(new BlockSnapshot(block.getLocation(), block.getBlockData()));
                        Material pick = materials.get(random.nextInt(materials.size()));
                        block.setType(pick, false);
                    }
                }
            }
        }
    }

    /** Kula z "twardym rdzeniem" (core) i zewnętrzną warstwą (top) o grubości thickness. */
    private void applyOverlay(Location center, int radius, int thickness, Material core, Material top, List<BlockSnapshot> snapshots) {
        World world = center.getWorld();
        int bx = center.getBlockX(), by = center.getBlockY(), bz = center.getBlockZ();
        double outerR = radius + 0.5;
        double innerR = Math.max(0, radius - thickness) + 0.5;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    double dist = Math.sqrt(x * x + y * y + z * z);
                    if (dist <= outerR) {
                        Block block = world.getBlockAt(bx + x, by + y, bz + z);
                        snapshots.add(new BlockSnapshot(block.getLocation(), block.getBlockData()));
                        Material mat = dist > innerR ? top : core;
                        block.setType(mat, false);
                    }
                }
            }
        }
    }

    /** Prosty algorytm wygładzania: uśrednia wysokość terenu wokół kolumn w promieniu. */
    private void applySmooth(Location center, int radius, int iterations, List<BlockSnapshot> snapshots) {
        World world = center.getWorld();
        int cx = center.getBlockX(), cz = center.getBlockZ();

        for (int iter = 0; iter < iterations; iter++) {
            int[][] heights = new int[radius * 2 + 1][radius * 2 + 1];
            Material[][] surfaceMaterial = new Material[radius * 2 + 1][radius * 2 + 1];

            // Zbierz aktualne wysokości terenu w obszarze
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    int worldX = cx + x, worldZ = cz + z;
                    int h = world.getHighestBlockYAt(worldX, worldZ);
                    heights[x + radius][z + radius] = h;
                    surfaceMaterial[x + radius][z + radius] = world.getBlockAt(worldX, h, worldZ).getType();
                }
            }

            // Dla każdej kolumny wylicz średnią z sąsiednich (promień 1) i dostosuj
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x * x + z * z > (radius + 0.5) * (radius + 0.5)) continue;

                    int sum = 0, count = 0;
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            int nx = x + dx, nz = z + dz;
                            if (nx >= -radius && nx <= radius && nz >= -radius && nz <= radius) {
                                sum += heights[nx + radius][nz + radius];
                                count++;
                            }
                        }
                    }
                    int avg = Math.round((float) sum / count);
                    int current = heights[x + radius][z + radius];
                    Material fillMaterial = surfaceMaterial[x + radius][z + radius];
                    int worldX = cx + x, worldZ = cz + z;

                    if (current > avg) {
                        for (int y = avg + 1; y <= current; y++) {
                            Block block = world.getBlockAt(worldX, y, worldZ);
                            snapshots.add(new BlockSnapshot(block.getLocation(), block.getBlockData()));
                            block.setType(Material.AIR, false);
                        }
                    } else if (current < avg) {
                        for (int y = current + 1; y <= avg; y++) {
                            Block block = world.getBlockAt(worldX, y, worldZ);
                            snapshots.add(new BlockSnapshot(block.getLocation(), block.getBlockData()));
                            block.setType(fillMaterial, false);
                        }
                    }
                }
            }
        }
    }
}
