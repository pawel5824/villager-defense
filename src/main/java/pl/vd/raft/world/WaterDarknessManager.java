package pl.vd.raft.world;

import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import pl.vd.raft.RaftPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Manager stref ciemnej wody w trybie Raft.
 *
 * Tworzy strefy na mapie, w których woda stopniowo ciemnieje wraz z głębokością
 * poprzez zmianę biome water fog color na coraz ciemniejsze oceany.
 *
 * Zapis/odczyt: plugins/ServerPlugin/water-darkness.yml
 */
public class WaterDarknessManager {

    private final RaftPlugin plugin;
    private final Map<String, WaterDarknessZone> zones = new LinkedHashMap<>();

    public WaterDarknessManager(RaftPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    // ==================== STREFY ====================

    /** Dodaj nową strefę ciemnej wody. */
    public boolean addZone(WaterDarknessZone zone) {
        zones.put(zone.getName().toLowerCase(), zone);
        saveConfig();
        plugin.getLogger().info("[WaterDarkness] Dodano strefę: " + zone);
        return true;
    }

    /** Usuń strefę. */
    public boolean removeZone(String name) {
        WaterDarknessZone removed = zones.remove(name.toLowerCase());
        if (removed != null) {
            saveConfig();
            plugin.getLogger().info("[WaterDarkness] Usunięto strefę: " + name);
            return true;
        }
        return false;
    }

    /** Pobierz strefę po nazwie. */
    public WaterDarknessZone getZone(String name) {
        return zones.get(name.toLowerCase());
    }

    /** Wszystkie strefy. */
    public Collection<WaterDarknessZone> getZones() {
        return zones.values();
    }

    // ==================== BIOME COLORS ====================

    /**
     * Zainicjalizuj kolory biome water fog w strefie. Ustawia progressive ciemniejsze
     * kolory na różnych poziomach Y. Wywoływane raz z komendy.
     */
    public void initializeBiomeColors(WaterDarknessZone zone) {
        World world = plugin.getServer().getWorld(zone.getWorld());
        if (world == null) {
            plugin.getLogger().warning("[WaterDarkness] Świat " + zone.getWorld() + " nie istnieje!");
            return;
        }

        int range = zone.getSurfaceY() - zone.getMaxDepthY();
        if (range <= 0) return;

        // Podziel strefę na pasy (co 4 bloki = jeden section biome)
        for (int y = zone.getSurfaceY(); y >= zone.getMaxDepthY(); y -= 4) {
            double ratio = (double)(zone.getSurfaceY() - y) / range;
            ratio = Math.max(0, Math.min(1, ratio));

            Biome biome = getBiomeForDepth(ratio);

            for (int x = zone.getMinX(); x <= zone.getMaxX(); x++) {
                for (int z = zone.getMinZ(); z <= zone.getMaxZ(); z++) {
                    world.setBiome(x, y, z, biome);
                    if (y + 1 <= zone.getSurfaceY()) world.setBiome(x, y + 1, z, biome);
                    if (y + 2 <= zone.getSurfaceY()) world.setBiome(x, y + 2, z, biome);
                    if (y + 3 <= zone.getSurfaceY()) world.setBiome(x, y + 3, z, biome);
                }
            }
        }

        plugin.getLogger().info("[WaterDarkness] Zainicjalizowano kolory biome w strefie " + zone.getName());
    }

    /**
     * Przywróć oryginalne biomy w strefie (DEEP_OCEAN, jak cała reszta mapy Raft).
     */
    public void restoreBiomeColors(WaterDarknessZone zone) {
        World world = plugin.getServer().getWorld(zone.getWorld());
        if (world == null) return;

        for (int x = zone.getMinX(); x <= zone.getMaxX(); x++) {
            for (int z = zone.getMinZ(); z <= zone.getMaxZ(); z++) {
                for (int y = zone.getMaxDepthY(); y <= zone.getSurfaceY(); y++) {
                    world.setBiome(x, y, z, Biome.DEEP_OCEAN);
                }
            }
        }

        plugin.getLogger().info("[WaterDarkness] Przywrócono biomy w strefie " + zone.getName());
    }

    /** Wybierz biome na podstawie głębokości (0.0 = powierzchnia, 1.0 = dno). */
    private Biome getBiomeForDepth(double ratio) {
        if (ratio < 0.15) return Biome.WARM_OCEAN;
        if (ratio < 0.30) return Biome.LUKEWARM_OCEAN;
        if (ratio < 0.45) return Biome.OCEAN;
        if (ratio < 0.60) return Biome.COLD_OCEAN;
        if (ratio < 0.75) return Biome.DEEP_LUKEWARM_OCEAN;
        if (ratio < 0.90) return Biome.DEEP_COLD_OCEAN;
        return Biome.DEEP_OCEAN;
    }

    // ==================== ZAPIS / ODCZYT ====================

    public void saveConfig() {
        File file = new File(plugin.getDataFolder(), "water-darkness.yml");
        YamlConfiguration cfg = new YamlConfiguration();

        for (WaterDarknessZone zone : zones.values()) {
            String path = "zones." + zone.getName();
            cfg.set(path + ".world", zone.getWorld());
            cfg.set(path + ".min-x", zone.getMinX());
            cfg.set(path + ".max-x", zone.getMaxX());
            cfg.set(path + ".min-z", zone.getMinZ());
            cfg.set(path + ".max-z", zone.getMaxZ());
            cfg.set(path + ".surface-y", zone.getSurfaceY());
            cfg.set(path + ".max-depth-y", zone.getMaxDepthY());
            cfg.set(path + ".enabled", zone.isEnabled());
        }

        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("[WaterDarkness] Nie udało się zapisać water-darkness.yml: " + e.getMessage());
        }
    }

    public void loadConfig() {
        File file = new File(plugin.getDataFolder(), "water-darkness.yml");
        if (!file.exists()) return;

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection zonesSection = cfg.getConfigurationSection("zones");
        if (zonesSection == null) return;

        zones.clear();
        for (String key : zonesSection.getKeys(false)) {
            ConfigurationSection s = zonesSection.getConfigurationSection(key);
            if (s == null) continue;

            try {
                WaterDarknessZone zone = new WaterDarknessZone(
                        key,
                        s.getString("world", "RAFT"),
                        s.getInt("min-x"), s.getInt("max-x"),
                        s.getInt("min-z"), s.getInt("max-z"),
                        s.getInt("surface-y", 80),
                        s.getInt("max-depth-y", -63)
                );
                zone.setEnabled(s.getBoolean("enabled", true));
                zones.put(key, zone);
            } catch (Exception e) {
                plugin.getLogger().warning("[WaterDarkness] Błąd wczytywania strefy " + key + ": " + e.getMessage());
            }
        }

        plugin.getLogger().info("[WaterDarkness] Wczytano " + zones.size() + " stref ciemnej wody.");
    }

    // ==================== INFO ====================

    public int getZoneCount() { return zones.size(); }
}
