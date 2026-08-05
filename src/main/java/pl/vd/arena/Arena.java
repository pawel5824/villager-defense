package pl.vd.arena;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Arena {

    private final String name;
    private ArenaState state = ArenaState.EDITING;

    private Location lobbyLocation;
    private Location endLocation;
    private final List<Location> playerSpawns = new ArrayList<>();
    private final List<Location> villagerSpawns = new ArrayList<>();

    private int minPlayers = 2;
    private int maxPlayers = 8;

    private final List<UUID> players = new ArrayList<>();
    private final List<UUID> spectators = new ArrayList<>();
    private final List<Villager> livingVillagers = new ArrayList<>();
    private final List<LivingEntity> spawnedMobs = new ArrayList<>();

    // punkty zdobyte w TRWAJĄCEJ grze (resetowane po zakończeniu rundy gry)
    private final Map<UUID, Integer> sessionPoints = new HashMap<>();
    private final Map<UUID, Integer> sessionKills = new HashMap<>();

    private int round = 0;
    private int countdown = 0;

    // start time for current session (milliseconds)
    private long startedAtMillis = 0;

    public Arena(String name) {
        this.name = name;
    }

    public String getName() { return name; }

    public ArenaState getState() { return state; }
    public void setState(ArenaState state) { this.state = state; }

    public Location getLobbyLocation() { return lobbyLocation; }
    public void setLobbyLocation(Location lobbyLocation) { this.lobbyLocation = lobbyLocation; }

    public Location getEndLocation() { return endLocation; }
    public void setEndLocation(Location endLocation) { this.endLocation = endLocation; }

    public List<Location> getPlayerSpawns() { return playerSpawns; }
    public List<Location> getVillagerSpawns() { return villagerSpawns; }

    public int getMinPlayers() { return minPlayers; }
    public void setMinPlayers(int minPlayers) { this.minPlayers = minPlayers; }

    public int getMaxPlayers() { return maxPlayers; }
    public void setMaxPlayers(int maxPlayers) { this.maxPlayers = maxPlayers; }

    public List<UUID> getPlayers() { return players; }
    public List<UUID> getSpectators() { return spectators; }
    public List<Villager> getLivingVillagers() { return livingVillagers; }
    public List<LivingEntity> getSpawnedMobs() { return spawnedMobs; }

    public int getRound() { return round; }
    public void setRound(int round) { this.round = round; }

    public int getCountdown() { return countdown; }
    public void setCountdown(int countdown) { this.countdown = countdown; }

    public long getStartedAtMillis() { return startedAtMillis; }
    public void setStartedAtMillis(long startedAtMillis) { this.startedAtMillis = startedAtMillis; }

    public boolean isReadyToUse() {
        return lobbyLocation != null && !playerSpawns.isEmpty() && !villagerSpawns.isEmpty();
    }

    public void addSessionPoints(UUID uuid, int amount) {
        sessionPoints.merge(uuid, amount, Integer::sum);
    }

    public void addSessionKill(UUID uuid) {
        sessionKills.merge(uuid, 1, Integer::sum);
    }

    public int getSessionPoints(UUID uuid) {
        return sessionPoints.getOrDefault(uuid, 0);
    }

    public int getSessionKills(UUID uuid) {
        return sessionKills.getOrDefault(uuid, 0);
    }

    public void resetSession() {
        sessionPoints.clear();
        sessionKills.clear();
        livingVillagers.clear();
        spawnedMobs.clear();
        round = 0;
        countdown = 0;
        players.clear();
        spectators.clear();
    }

    public boolean isPlaying(Player p) {
        return players.contains(p.getUniqueId());
    }
}
