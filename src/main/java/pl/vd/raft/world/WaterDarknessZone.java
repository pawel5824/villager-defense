package pl.vd.raft.world;

import org.bukkit.Location;

/**
 * Strefa ciemnej wody — obszar na mapie Raft, w którym woda stopniowo ciemnieje
 * wraz z głębokością poprzez zmianę biome water fog color.
 */
public class WaterDarknessZone {

    private final String name;
    private final String world;
    private int minX, maxX, minZ, maxZ;
    private int surfaceY;    // poziom powierzchni wody (np. 80)
    private int maxDepthY;   // maksymalna głębokość (np. -62, bedrock na -61)
    private boolean enabled;

    public WaterDarknessZone(String name, String world,
                             int minX, int maxX, int minZ, int maxZ,
                             int surfaceY, int maxDepthY) {
        this.name = name;
        this.world = world;
        this.minX = Math.min(minX, maxX);
        this.maxX = Math.max(minX, maxX);
        this.minZ = Math.min(minZ, maxZ);
        this.maxZ = Math.max(minZ, maxZ);
        this.surfaceY = surfaceY;
        this.maxDepthY = maxDepthY;
        this.enabled = true;
    }

    /** Sprawdza czy lokacja jest w strefie (X/Z). */
    public boolean contains(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        if (!loc.getWorld().getName().equals(world)) return false;
        int x = loc.getBlockX(), z = loc.getBlockZ();
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    /** Sprawdza czy lokacja jest w strefie i pod wodą (Y <= surfaceY). */
    public boolean isUnderwater(Location loc) {
        return contains(loc) && loc.getBlockY() <= surfaceY;
    }

    // --- Getters/Setters ---

    public String getName() { return name; }
    public String getWorld() { return world; }
    public int getMinX() { return minX; }
    public int getMaxX() { return maxX; }
    public int getMinZ() { return minZ; }
    public int getMaxZ() { return maxZ; }
    public int getSurfaceY() { return surfaceY; }
    public int getMaxDepthY() { return maxDepthY; }
    public boolean isEnabled() { return enabled; }

    public void setSurfaceY(int surfaceY) { this.surfaceY = surfaceY; }
    public void setMaxDepthY(int maxDepthY) { this.maxDepthY = maxDepthY; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getHeight() { return surfaceY - maxDepthY; }
    public int getSizeX() { return maxX - minX + 1; }
    public int getSizeZ() { return maxZ - minZ + 1; }

    @Override
    public String toString() {
        return "WaterDarknessZone{" + name + " [" + minX + "," + maxX + "]x[" + minZ + "," + maxZ
                + "] Y:" + maxDepthY + "-" + surfaceY + "}";
    }
}
