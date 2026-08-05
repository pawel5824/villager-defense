package pl.vd.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public final class LocationUtil {

    private LocationUtil() {}

    public static String serialize(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        return loc.getWorld().getName() + "," + loc.getX() + "," + loc.getY() + "," + loc.getZ()
                + "," + loc.getYaw() + "," + loc.getPitch();
    }

    public static Location deserialize(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String[] parts = raw.split(",");
        if (parts.length < 5) return null;
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) return null;
        double x = Double.parseDouble(parts[1]);
        double y = Double.parseDouble(parts[2]);
        double z = Double.parseDouble(parts[3]);
        float yaw = Float.parseFloat(parts[4]);
        float pitch = parts.length > 5 ? Float.parseFloat(parts[5]) : 0f;
        return new Location(world, x, y, z, yaw, pitch);
    }
}
