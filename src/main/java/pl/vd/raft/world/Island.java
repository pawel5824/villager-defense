package pl.vd.raft.world;

import org.bukkit.Location;

/**
 * Reprezentuje wyspę w świecie Raft.
 */
public class Island {

    private final Location center;
    private final IslandType type;
    private final int radius;
    private final int height;
    private boolean explored;
    private final String name;

    public Island(Location center, IslandType type) {
        this.center = center;
        this.type = type;
        this.radius = type.getDefaultRadius();
        this.height = type.getDefaultHeight();
        this.explored = false;
        this.name = type.getDefaultName() + "_" + (int)(Math.random() * 1000);
    }

    /**
     * Sprawdza czy dana lokalizacja jest w pobliżu wyspy.
     */
    public boolean isNearby(Location location, double distance) {
        if (!location.getWorld().equals(center.getWorld())) return false;
        return location.distance(center) <= distance;
    }

    /**
     * Sprawdza czy dana lokalizacja jest na wyspie.
     */
    public boolean contains(Location location) {
        if (!location.getWorld().equals(center.getWorld())) return false;
        double distance = location.distance(center);
        return distance <= radius;
    }

    /**
     * Oznacza wyspę jako eksplorowaną.
     */
    public void markExplored() {
        this.explored = true;
    }

    // Gettery
    public Location getCenter() { return center; }
    public IslandType getType() { return type; }
    public int getRadius() { return radius; }
    public int getHeight() { return height; }
    public boolean isExplored() { return explored; }
    public String getName() { return name; }
}
