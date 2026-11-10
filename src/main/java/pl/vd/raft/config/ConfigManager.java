package pl.vd.raft.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import pl.vd.raft.RaftPlugin;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

/**
 * Zarządza plikami konfiguracyjnymi pluginu.
 */
public class ConfigManager {

    private final RaftPlugin plugin;
    private FileConfiguration config;
    private File configFile;

    public ConfigManager(RaftPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    /**
     * Wczytuje główną konfigurację.
     */
    private void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();
    }

    /**
     * Ładzie konfigurację wysp.
     */
    public FileConfiguration loadIslandsConfig() {
        File file = new File(plugin.getDataFolder(), "islands.yml");
        if (!file.exists()) {
            plugin.saveResource("islands.yml", false);
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    /**
     * Zapisuje konfigurację wysp.
     */
    public void saveIslandsConfig(FileConfiguration config) {
        File file = new File(plugin.getDataFolder(), "islands.yml");
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Nie udało się zapisać islands.yml", e);
        }
    }

    /**
     * Ładzie konfigurację questów.
     */
    public FileConfiguration loadQuestsConfig() {
        File file = new File(plugin.getDataFolder(), "quests.yml");
        if (!file.exists()) {
            plugin.saveResource("quests.yml", false);
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    /**
     * Ładzie konfigurację fabuły.
     */
    public FileConfiguration loadStoryConfig() {
        File file = new File(plugin.getDataFolder(), "story.yml");
        if (!file.exists()) {
            plugin.saveResource("story.yml", false);
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    /**
     * Pobiera wartość z głównej konfiguracji.
     */
    public String getString(String path) {
        return config.getString(path);
    }

    /**
     * Pobiera wartość int z głównej konfiguracji.
     */
    public int getInt(String path) {
        return config.getInt(path);
    }

    /**
     * Pobiera wartość boolean z głównej konfiguracji.
     */
    public boolean getBoolean(String path) {
        return config.getBoolean(path);
    }

    /**
     * Pobiera wartość double z głównej konfiguracji.
     */
    public double getDouble(String path) {
        return config.getDouble(path);
    }

    /**
     * Ustawia wartość w głównej konfiguracji.
     */
    public void set(String path, Object value) {
        config.set(path, value);
        plugin.saveConfig();
    }

    /**
     * Przeładowuje konfigurację.
     */
    public void reload() {
        loadConfig();
    }
}
