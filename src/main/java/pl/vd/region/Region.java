package pl.vd.region;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.Objects;

/**
 * Region areny: pudełko (cuboid) z dwóch punktów w jednym świecie. Zapisany do
 * pliku, może służyć później np. do rozstawiania przedmiotów albo eventów.
 */
public class Region {

    private final String name;
    private final String world;
    private int minX, minY, minZ;
    private int maxX, maxY, maxZ;

    public Region(String name, String world, Location p1, Location p2) {
        this.name = name;
        this.world = world;
        setBounds(p1, p2);
    }

    public Region(String name, String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.name = name;
        this.world = world;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    private void setBounds(Location p1, Location p2) {
        minX = Math.min(p1.getBlockX(), p2.getBlockX());
        minY = Math.min(p1.getBlockY(), p2.getBlockY());
        minZ = Math.min(p1.getBlockZ(), p2.getBlockZ());
        maxX = Math.max(p1.getBlockX(), p2.getBlockX());
        maxY = Math.max(p1.getBlockY(), p2.getBlockY());
        maxZ = Math.max(p1.getBlockZ(), p2.getBlockZ());
    }

    public String getName() { return name; }
    public String getWorld() { return world; }
    public int getMinX() { return minX; }
    public int getMinY() { return minY; }
    public int getMinZ() { return minZ; }
    public int getMaxX() { return maxX; }
    public int getMaxY() { return maxY; }
    public int getMaxZ() { return maxZ; }

    public int sizeX() { return maxX - minX + 1; }
    public int sizeY() { return maxY - minY + 1; }
    public int sizeZ() { return maxZ - minZ + 1; }
    public long volume() { return (long) sizeX() * sizeY() * sizeZ(); }

    public boolean contains(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        if (!loc.getWorld().getName().equals(world)) return false;
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    /** Środek pudełka (do stawiania przedmiotów / teleportów). */
    public Location center(World w) {
        if (w == null || !w.getName().equals(world)) return null;
        return new Location(w,
                (minX + maxX + 1) / 2.0,
                (minY + maxY + 1) / 2.0,
                (minZ + maxZ + 1) / 2.0);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Region r)) return false;
        return name.equals(r.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
