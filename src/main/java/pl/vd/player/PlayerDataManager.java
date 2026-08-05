package pl.vd.player;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import pl.vd.VillagerDefensePlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class PlayerDataManager {

    private final VillagerDefensePlugin plugin;
    private final File file;
    private final Map<UUID, PlayerData> cache = new HashMap<>();

    public PlayerDataManager(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "players.yml");
        load();
    }

    public PlayerData get(UUID uuid, String name) {
        return cache.computeIfAbsent(uuid, id -> new PlayerData(id, name));
    }

    public List<PlayerData> getTop(int size) {
        return cache.values().stream()
                .sorted(Comparator.comparingInt(PlayerData::getTotalPoints).reversed())
                .limit(size)
                .collect(Collectors.toList());
    }

    public void load() {
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Nie udało się utworzyć players.yml: " + e.getMessage());
            }
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("players");
        cache.clear();
        if (root == null) return;

        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) continue;
            UUID uuid = UUID.fromString(key);
            PlayerData data = new PlayerData(uuid, s.getString("name", "?"));
            data.addPoints(s.getInt("points", 0));
            data.addKills(s.getInt("kills", 0));
            for (int i = 0; i < s.getInt("games", 0); i++) data.addGamePlayed();
            data.updateHighestWave(s.getInt("highest-wave", 0));
            cache.put(uuid, data);
        }
    }

    public void saveAll() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (PlayerData data : cache.values()) {
            String path = "players." + data.getUuid();
            cfg.set(path + ".name", data.getLastKnownName());
            cfg.set(path + ".points", data.getTotalPoints());
            cfg.set(path + ".kills", data.getTotalKills());
            cfg.set(path + ".games", data.getGamesPlayed());
            cfg.set(path + ".highest-wave", data.getHighestWave());
        }
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Nie udało się zapisać players.yml: " + e.getMessage());
        }
    }
}
