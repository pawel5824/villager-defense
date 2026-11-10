package pl.vd.region;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import pl.vd.VillagerDefensePlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * System regionów areny: zaznaczasz pudełko (pos1/pos2), zapisujesz je pod
 * nazwą do pliku (regiony.yml) i możesz później odczytać w grze (info, daj)
 * albo wykorzystać w kodzie (contains/center). Plik leży w
 * plugins/ServerPlugin/regiony.yml - tam dowiesz się dokładnie, gdzie jest
 * region i jakie ma wymiary.
 */
public class RegionManager {

    private final VillagerDefensePlugin plugin;
    private final File file;
    private final YamlConfiguration cfg = new YamlConfiguration();

    private final Map<String, Region> regions = new LinkedHashMap<>();
    private final Map<UUID, Location[]> selections = new ConcurrentHashMap<>();

    public RegionManager(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "regiony.yml");
        load();
    }

    // ---------- Zaznaczanie ----------

    public void setPos1(Player player, Location loc) {
        selections.computeIfAbsent(player.getUniqueId(), k -> new Location[2])[0] = loc.getBlock().getLocation();
    }

    public void setPos2(Player player, Location loc) {
        selections.computeIfAbsent(player.getUniqueId(), k -> new Location[2])[1] = loc.getBlock().getLocation();
    }

    public Location getPos1(Player player) {
        Location[] sel = selections.get(player.getUniqueId());
        return sel != null ? sel[0] : null;
    }

    public Location getPos2(Player player) {
        Location[] sel = selections.get(player.getUniqueId());
        return sel != null ? sel[1] : null;
    }

    // ---------- Regiony ----------

    public Region getRegion(String name) {
        return regions.get(name.toLowerCase());
    }

    public List<Region> getRegions() {
        return new ArrayList<>(regions.values());
    }

    public boolean hasRegion(String name) {
        return regions.containsKey(name.toLowerCase());
    }

    /**
     * Zapisuje region z zaznaczenia gracza. Zwraca null przy błędzie, albo
     * gotowy region po udanym zapisie (zapisuje też do pliku).
     */
    public Region saveRegion(Player player, String name) {
        Location p1 = getPos1(player);
        Location p2 = getPos2(player);
        if (p1 == null || p2 == null) return null;
        if (p1.getWorld() == null || !p1.getWorld().equals(p2.getWorld())) return null;

        Region region = new Region(name, p1.getWorld().getName(), p1, p2);
        regions.put(name.toLowerCase(), region);
        save();
        return region;
    }

    public boolean removeRegion(String name) {
        if (regions.remove(name.toLowerCase()) != null) {
            save();
            return true;
        }
        return false;
    }

    // ---------- Zapis / odczyt ----------

    public void load() {
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Nie udało się utworzyć regiony.yml: " + e.getMessage());
            }
        }
        try {
            cfg.load(file);
        } catch (Exception e) {
            plugin.getLogger().severe("Nie udało się wczytać regiony.yml: " + e.getMessage());
            return;
        }
        regions.clear();
        ConfigurationSection root = cfg.getConfigurationSection("regions");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) continue;
            try {
                Region region = new Region(
                        s.getString("name", key),
                        s.getString("world", ""),
                        s.getInt("pos1.x"), s.getInt("pos1.y"), s.getInt("pos1.z"),
                        s.getInt("pos2.x"), s.getInt("pos2.y"), s.getInt("pos2.z"));
                regions.put(key.toLowerCase(), region);
            } catch (Exception e) {
                plugin.getLogger().warning("Pominięto uszkodzony region " + key + ": " + e.getMessage());
            }
        }
    }

    public void save() {
        cfg.set("regions", null);
        ConfigurationSection root = cfg.createSection("regions");
        for (Region r : regions.values()) {
            ConfigurationSection s = root.createSection(r.getName().toLowerCase());
            s.set("name", r.getName());
            s.set("world", r.getWorld());
            s.set("pos1.x", r.getMinX());
            s.set("pos1.y", r.getMinY());
            s.set("pos1.z", r.getMinZ());
            s.set("pos2.x", r.getMaxX());
            s.set("pos2.y", r.getMaxY());
            s.set("pos2.z", r.getMaxZ());
        }
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Nie udało się zapisać regiony.yml: " + e.getMessage());
        }
    }

    /** Pomocnicze: szuka świata o podanej nazwie (do center/info). */
    public World worldByName(String name) {
        return plugin.getServer().getWorld(name);
    }
}
