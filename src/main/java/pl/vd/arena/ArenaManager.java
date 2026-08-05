package pl.vd.arena;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import pl.vd.VillagerDefensePlugin;
import pl.vd.util.LocationUtil;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class ArenaManager {

    private final VillagerDefensePlugin plugin;
    private final Map<String, Arena> arenas = new LinkedHashMap<>();
    private final File file;
    private YamlConfiguration cfg;

    public ArenaManager(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "arenas.yml");
        load();
    }

    public Map<String, Arena> getArenas() { return arenas; }

    public Arena getArena(String name) {
        return arenas.get(name.toLowerCase());
    }

    public Arena createArena(String name) {
        Arena arena = new Arena(name);
        arenas.put(name.toLowerCase(), arena);
        save();
        return arena;
    }

    public boolean removeArena(String name) {
        if (arenas.remove(name.toLowerCase()) != null) {
            save();
            return true;
        }
        return false;
    }

    public void load() {
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Nie udało się utworzyć arenas.yml: " + e.getMessage());
            }
        }
        cfg = YamlConfiguration.loadConfiguration(file);
        arenas.clear();

        ConfigurationSection root = cfg.getConfigurationSection("arenas");
        if (root == null) return;

        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) continue;
            Arena arena = new Arena(key);
            arena.setLobbyLocation(LocationUtil.deserialize(s.getString("lobby")));
            arena.setEndLocation(LocationUtil.deserialize(s.getString("end")));
            arena.setMinPlayers(s.getInt("min-players", 2));
            arena.setMaxPlayers(s.getInt("max-players", 8));

            for (String loc : s.getStringList("player-spawns")) {
                var l = LocationUtil.deserialize(loc);
                if (l != null) arena.getPlayerSpawns().add(l);
            }
            for (String loc : s.getStringList("villager-spawns")) {
                var l = LocationUtil.deserialize(loc);
                if (l != null) arena.getVillagerSpawns().add(l);
            }
            arena.setState(ArenaState.WAITING);
            arenas.put(key.toLowerCase(), arena);
        }
    }

    public void save() {
        cfg = new YamlConfiguration();
        for (Arena arena : arenas.values()) {
            String path = "arenas." + arena.getName();
            cfg.set(path + ".lobby", LocationUtil.serialize(arena.getLobbyLocation()));
            cfg.set(path + ".end", LocationUtil.serialize(arena.getEndLocation()));
            cfg.set(path + ".min-players", arena.getMinPlayers());
            cfg.set(path + ".max-players", arena.getMaxPlayers());

            cfg.set(path + ".player-spawns", arena.getPlayerSpawns().stream()
                    .map(LocationUtil::serialize).toList());
            cfg.set(path + ".villager-spawns", arena.getVillagerSpawns().stream()
                    .map(LocationUtil::serialize).toList());
        }
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Nie udało się zapisać arenas.yml: " + e.getMessage());
        }
    }
}
