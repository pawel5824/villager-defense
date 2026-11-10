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
    // gracze, których dane wczytano z players.yml przy starcie - tylko NOWI gracze
    // (bez wpisu w pliku) dostają domyślny język angielski w ustawieniach pluginu
    private final Set<UUID> knownPlayers = new HashSet<>();

    public PlayerDataManager(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "players.yml");
        load();
    }

    public PlayerData get(UUID uuid, String name) {
        return cache.computeIfAbsent(uuid, id -> {
            PlayerData data = new PlayerData(id, name);
            // nowy gracz na serwerze: domyślny język angielski w pluginie
            // (może zmienić w Ustawieniach -> Język; istniejący gracze bez zmian)
            if (!knownPlayers.contains(id)) {
                data.setSetting("language", "angielski");
            }
            return data;
        });
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
        knownPlayers.clear();
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
            // osobne ustawienia gracza (dźwięki, cząsteczki itd.)
            ConfigurationSection sett = s.getConfigurationSection("settings");
            if (sett != null) {
                for (String k : sett.getKeys(false)) {
                    data.setSetting(k, sett.getString(k, "off"));
                }
            }
            cache.put(uuid, data);
            knownPlayers.add(uuid);
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
            for (Map.Entry<String, String> e : data.getSettings().entrySet()) {
                cfg.set(path + ".settings." + e.getKey(), e.getValue());
            }
        }
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Nie udało się zapisać players.yml: " + e.getMessage());
        }
    }
}
