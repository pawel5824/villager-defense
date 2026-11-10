package pl.vd.barrier;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import pl.vd.VillagerDefensePlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Zarządza niewidzialnymi barierami (strefami blokującymi ruch graczy).
 * Bez bloków na świecie — czysto kodowo (eventy + współrzędne w pliku YAML).
 *
 * Plik: plugins/ServerPlugin/barriers.yml
 * Format:
 *   barriers:
 *     <nazwa>:
 *       world: <nazwa-swiat>
 *       x1, y1, z1: <współrzędne rogu 1>
 *       x2, y2, z2: <współrzędne rogu 2>
 */
public class BarrierManager {

    private final File file;
    private final Map<String, BarrierZone> zones = new LinkedHashMap<>();

    public BarrierManager(VillagerDefensePlugin plugin) {
        this.file = new File(plugin.getDataFolder(), "barriers.yml");
        load();
    }

    /**
     * Dodaje nową barierę (prostopadłościan z dwóch rogów).
     */
    public boolean addZone(String name, String world, int x1, int y1, int z1, int x2, int y2, int z2) {
        zones.put(name.toLowerCase(), new BarrierZone(world, x1, y1, z1, x2, y2, z2));
        save();
        return true;
    }

    /**
     * Usuwa barierę po nazwie.
     */
    public boolean removeZone(String name) {
        boolean removed = zones.remove(name.toLowerCase()) != null;
        if (removed) save();
        return removed;
    }

    /**
     * Zwraca barierę po nazwie lub null.
     */
    public BarrierZone getZone(String name) {
        return zones.get(name.toLowerCase());
    }

    /**
     * Wszystkie strefy.
     */
    public Map<String, BarrierZone> getZones() {
        return Collections.unmodifiableMap(zones);
    }

    /**
     * Sprawdza czy dana pozycja jest WEWNĄTRZ jakiejkolwiek bariery.
     */
    public boolean isBlocked(String world, double x, double y, double z) {
        for (BarrierZone zone : zones.values()) {
            if (zone.contains(world, x, y, z)) return true;
        }
        return false;
    }

    /**
     * Zwraca nazwę bariery, która blokuje daną pozycję, lub null.
     */
    public String findBlockingZone(String world, double x, double y, double z) {
        for (Map.Entry<String, BarrierZone> entry : zones.entrySet()) {
            if (entry.getValue().contains(world, x, y, z)) return entry.getKey();
        }
        return null;
    }

    /**
     * Zwraca najbliższy bezpieczny punkt w obrębie Y gracza (wypycha na krawędź bariery).
     */
    public org.bukkit.Location getSafePosition(org.bukkit.Location loc) {
        String world = loc.getWorld().getName();
        double x = loc.getX(), y = loc.getY(), z = loc.getZ();
        for (BarrierZone zone : zones.values()) {
            if (!zone.contains(world, x, y, z)) continue;
            // wypchnij na krawędź bariery
            double minX = Math.min(zone.x1, zone.x2) - 0.5;
            double maxX = Math.max(zone.x1, zone.x2) + 0.5;
            double minZ = Math.min(zone.z1, zone.z2) - 0.5;
            double maxZ = Math.max(zone.z1, zone.z2) + 0.5;

            // znajdź najbliższy punkt poza barierą
            double newX = x;
            double newZ = z;
            if (x >= minX && x <= maxX) {
                // X w środku — wypchnij w stronę najbliższego brzegu
                double distLeft = x - minX;
                double distRight = maxX - x;
                newX = distLeft < distRight ? minX - 0.5 : maxX + 0.5;
            }
            if (z >= minZ && z <= maxZ) {
                double distFront = z - minZ;
                double distBack = maxZ - z;
                newZ = distFront < distBack ? minZ - 0.5 : maxZ + 0.5;
            }
            return new org.bukkit.Location(loc.getWorld(), newX, y, newZ);
        }
        return loc;
    }

    // ---------- persistence ----------

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = cfg.getConfigurationSection("barriers");
        if (section == null) return;

        for (String name : section.getKeys(false)) {
            ConfigurationSection z = section.getConfigurationSection(name);
            if (z == null) continue;
            String world = z.getString("world", "world");
            int x1 = z.getInt("x1"), y1 = z.getInt("y1"), z1 = z.getInt("z1");
            int x2 = z.getInt("x2"), y2 = z.getInt("y2"), z2 = z.getInt("z2");
            zones.put(name.toLowerCase(), new BarrierZone(world, x1, y1, z1, x2, y2, z2));
        }
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<String, BarrierZone> entry : zones.entrySet()) {
            String path = "barriers." + entry.getKey();
            BarrierZone z = entry.getValue();
            cfg.set(path + ".world", z.world);
            cfg.set(path + ".x1", z.x1);
            cfg.set(path + ".y1", z.y1);
            cfg.set(path + ".z1", z.z1);
            cfg.set(path + ".x2", z.x2);
            cfg.set(path + ".y2", z.y2);
            cfg.set(path + ".z2", z.z2);
        }
        try {
            file.getParentFile().mkdirs();
            cfg.save(file);
        } catch (IOException ignored) {}
    }

    // ---------- inner class ----------

    public static class BarrierZone {
        public final String world;
        public final int x1, y1, z1;
        public final int x2, y2, z2;

        public BarrierZone(String world, int x1, int y1, int z1, int x2, int y2, int z2) {
            this.world = world;
            this.x1 = x1; this.y1 = y1; this.z1 = z1;
            this.x2 = x2; this.y2 = y2; this.z2 = z2;
        }

        /**
         * Sprawdza czy pozycja jest wewnątrz strefy.
         */
        public boolean contains(String world, double x, double y, double z) {
            if (!this.world.equals(world)) return false;
            int minX = Math.min(x1, x2), maxX = Math.max(x1, x2);
            int minY = Math.min(y1, y2), maxY = Math.max(y1, y2);
            int minZ = Math.min(z1, z2), maxZ = Math.max(z1, z2);
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }

        public String description() {
            int minX = Math.min(x1, x2), maxX = Math.max(x1, x2);
            int minY = Math.min(y1, y2), maxY = Math.max(y1, y2);
            int minZ = Math.min(z1, z2), maxZ = Math.max(z1, z2);
            int sx = maxX - minX + 1;
            int sy = maxY - minY + 1;
            int sz = maxZ - minZ + 1;
            return "§7" + world + " §f(" + minX + "," + minY + "," + minZ + ") → ("
                    + maxX + "," + maxY + "," + maxZ + ") §7[" + sx + "x" + sy + "x" + sz + "]";
        }
    }
}
