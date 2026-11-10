package pl.vd.lobby;

import org.bukkit.configuration.file.YamlConfiguration;
import pl.vd.VillagerDefensePlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

/**
 * Zarządza NPC w lobby — spawnuje, usuwa, zapisuje/odczytuje z YAML.
 */
public class LobbyNPCManager {

    private final VillagerDefensePlugin plugin;
    private final Map<String, LobbyNPC> npcs = new LinkedHashMap<>();
    private final File file;

    public LobbyNPCManager(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "lobby_npcs.yml");
    }

    /** Ładuje NPC z pliku i spawnuje je. */
    public void loadAll() {
        if (!file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        var section = cfg.getConfigurationSection("npcs");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            var map = section.getConfigurationSection(key);
            if (map == null) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> mapValues = (Map<String, Object>) map.getValues(false);
            LobbyNPC npc = LobbyNPC.fromMap(mapValues, plugin);
            if (npc != null) {
                npcs.put(key, npc);
                npc.spawn(plugin);
            }
        }
        plugin.getLogger().log(Level.INFO, "Załadowano {0} NPC lobby.", npcs.size());
    }

    /** Zapisuje wszystkie NPC do pliku. */
    public void saveAll() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<String, LobbyNPC> e : npcs.entrySet()) {
            cfg.createSection("npcs." + e.getKey(), e.getValue().toMap());
        }
        try {
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Nie udało się zapisać lobby_npcs.yml", ex);
        }
    }

    /** Dodaje nowego NPC (klucz = mała nazwa bez spacji). */
    public LobbyNPC addNPC(String key, LobbyNPC npc) {
        npcs.put(key, npc);
        npc.spawn(plugin);
        saveAll();
        return npc;
    }

    /** Usuwa NPC po kluczu. */
    public boolean removeNPC(String key) {
        LobbyNPC npc = npcs.remove(key);
        if (npc != null) {
            npc.despawn();
            saveAll();
            return true;
        }
        return false;
    }

    /** Znajduje NPC po kluczu. */
    public LobbyNPC getNPC(String key) {
        return npcs.get(key);
    }

    /** Zwraca wszystkie klucze NPC. */
    public Set<String> getKeys() {
        return npcs.keySet();
    }

    /** Zwraca wszystkie NPC. */
    public Collection<LobbyNPC> getAll() {
        return npcs.values();
    }

    /** Usuwa wszystkie NPC i czyści. */
    public void removeAll() {
        for (LobbyNPC npc : npcs.values()) npc.despawn();
        npcs.clear();
        saveAll();
    }

    /** Spawnuje wszystkie NPC (po reload). */
    public void spawnAll() {
        for (LobbyNPC npc : npcs.values()) npc.spawn(plugin);
    }
}
