package pl.vd.raft.world;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import pl.vd.raft.RaftPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Zarządza wyspami w świecie Raft.
 * Wyspy są generowane proceduralnie lub tworzone ręcznie.
 * Wyspy są zapisywane/odczytywane z pliku YAML.
 */
public class IslandManager {

    private final RaftPlugin plugin;
    private final Map<String, Island> islands = new HashMap<>();
    private final Map<UUID, Location> playerRafts = new HashMap<>();
    private File islandFile;
    private FileConfiguration islandConfig;

    public IslandManager(RaftPlugin plugin) {
        this.plugin = plugin;
        this.islandFile = new File(plugin.getDataFolder(), "islands.yml");
        this.islandConfig = YamlConfiguration.loadConfiguration(islandFile);
        loadIslands();
    }

    /**
     * Generuje nową wyspę w podanej lokalizacji.
     */
    public Island generateIsland(Location center, IslandType type) {
        Island island = new Island(center, type);
        String id = "island_" + islands.size();
        islands.put(id, island);
        saveIslands();
        return island;
    }

    /**
     * Dodaje istniejącą wyspę (z generatora).
     */
    public void addIsland(String id, Island island) {
        islands.put(id, island);
    }

    /**
     * Zapisuje wszystkie wyspy do pliku.
     */
    public void saveIslands() {
        // Wyczyść starą konfigurację
        islandConfig = YamlConfiguration.loadConfiguration(islandFile);

        // Zapisz wyspy
        for (Map.Entry<String, Island> entry : islands.entrySet()) {
            String path = "islands." + entry.getKey();
            Island island = entry.getValue();

            islandConfig.set(path + ".type", island.getType().name());
            islandConfig.set(path + ".center.world", island.getCenter().getWorld().getName());
            islandConfig.set(path + ".center.x", island.getCenter().getX());
            islandConfig.set(path + ".center.y", island.getCenter().getY());
            islandConfig.set(path + ".center.z", island.getCenter().getZ());
            islandConfig.set(path + ".radius", island.getRadius());
            islandConfig.set(path + ".height", island.getHeight());
            islandConfig.set(path + ".explored", island.isExplored());
            islandConfig.set(path + ".name", island.getName());
        }

        try {
            islandConfig.save(islandFile);
            plugin.getLogger().info("[IslandManager] Zapisano " + islands.size() + " wysp do islands.yml");
        } catch (IOException e) {
            plugin.getLogger().severe("[IslandManager] Nie udało się zapisać wysp: " + e.getMessage());
        }
    }

    /**
     * Wczytuje wyspy z pliku.
     */
    private void loadIslands() {
        if (!islandFile.exists()) {
            plugin.getLogger().info("[IslandManager] Brak pliku islands.yml —开始 z pustą listą wysp.");
            return;
        }

        // Upewnij się, że sekcja istnieje
        if (!islandConfig.contains("islands")) {
            plugin.getLogger().info("[IslandManager] Plik islands.yml istnieje, ale brak sekcji 'islands'.");
            return;
        }

        var section = islandConfig.getConfigurationSection("islands");
        if (section == null) return;

        int count = 0;
        for (String key : section.getKeys(false)) {
            String path = "islands." + key;
            try {
                String typeName = islandConfig.getString(path + ".type");
                if (typeName == null) continue;

                IslandType type = IslandType.valueOf(typeName);

                String worldName = islandConfig.getString(path + ".center.world");
                World world = plugin.getServer().getWorld(worldName);
                if (world == null) {
                    plugin.getLogger().warning("[IslandManager] Świat '" + worldName + "' nie istnieje, pomijam wyspę " + key);
                    continue;
                }

                double x = islandConfig.getDouble(path + ".center.x");
                double y = islandConfig.getDouble(path + ".center.y");
                double z = islandConfig.getDouble(path + ".center.z");
                Location center = new Location(world, x, y, z);

                Island island = new Island(center, type);

                // Przywróć explored
                if (islandConfig.getBoolean(path + ".explored", false)) {
                    island.markExplored();
                }

                islands.put(key, island);
                count++;
            } catch (Exception e) {
                plugin.getLogger().warning("[IslandManager] Błąd wczytywania wyspy " + key + ": " + e.getMessage());
            }
        }

        plugin.getLogger().info("[IslandManager] Wczytano " + count + " wysp z islands.yml");
    }

    /**
     * Tworzy raft (ponton) dla gracza.
     */
    public void createRaft(Player player) {
        Location spawn = player.getLocation();
        playerRafts.put(player.getUniqueId(), spawn);
        // TODO: Zbuduj ponton z bloków
    }

    /**
     * Zwraca lokalizację raftu gracza.
     */
    public Location getRaftLocation(Player player) {
        return playerRafts.get(player.getUniqueId());
    }

    /**
     * Sprawdza czy gracz jest na swoim raftcie.
     */
    public boolean isOnRaft(Player player) {
        Location raft = playerRafts.get(player.getUniqueId());
        if (raft == null) return false;

        Location playerLoc = player.getLocation();
        double distance = playerLoc.distance(raft);
        return distance < 5.0; // Raft ma promień 5 bloków
    }

    /**
     * Przesuwa raft gracza w kierunku sterowania.
     */
    public void moveRaft(Player player, double directionX, double directionZ) {
        if (!isOnRaft(player)) return;

        Location raft = playerRafts.get(player.getUniqueId());
        raft.add(directionX, 0, directionZ);

        // Sprawdź czy raft nie wpłynął na wyspę
        checkIslandCollision(player);
    }

    /**
     * Sprawdza kolizję raftu z wyspami.
     */
    private void checkIslandCollision(Player player) {
        Location raft = playerRafts.get(player.getUniqueId());
        if (raft == null) return;

        for (Island island : islands.values()) {
            if (island.isNearby(raft, 10.0)) {
                // Raft zbliżył się do wyspy - umożliw eksplorację.
                // Bez komunikatu na czacie (AGENTS.md pkt 1) — celowo.
                // TODO: Otwórz menu eksploracji wyspy
            }
        }
    }

    /**
     * Zwraca listę wszystkich wysp.
     */
    public Map<String, Island> getIslands() {
        return islands;
    }

    /**
     * Zwraca liczbę wysp.
     */
    public int getIslandCount() {
        return islands.size();
    }
}
