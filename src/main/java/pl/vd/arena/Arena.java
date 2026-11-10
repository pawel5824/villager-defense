package pl.vd.arena;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class Arena {

    private final String name;
    private ArenaState state = ArenaState.EDITING;

    // system instancji: szablon = arena, z której klonowane są kopie mapy;
    // instancja = arena tymczasowa utworzona z szablonu (templateName != null),
    // usuwana po zakończeniu gry (nigdy nie zapisywana do arenas.yml)
    private boolean template = false;
    private String templateName = null;

    private Location lobbyLocation;
    private Location endLocation;
    private final List<Location> playerSpawns = new ArrayList<>();
    private final List<Location> villagerSpawns = new ArrayList<>();
    // osobne punkty spawnu mobów (opcjonalne - jeśli puste, moby spawnują się przy wieśniakach)
    private final List<Location> mobSpawns = new ArrayList<>();

    private int minPlayers = 2;
    private int maxPlayers = 8;

    // poziom trudności areny (latwy/normalny/trudny lub własny z config.yml);
    // każdy poziom ma własną liczbę fal do zwycięstwa (config difficulties.<poziom>.waves)
    private String difficulty = "normalny";

    // WŁASNY teren zrzutu zaopatrzenia tej areny (wielokąt + dziury). Pusty = arena
    // używa globalnego terenu z config.yml (supply-drop.polygon / supply-drop.excluded).
    // Zapisywany w arenas.yml pod arenas.<nazwa>.supply-drop.
    private final List<int[]> dropPolygon = new ArrayList<>();
    private final List<List<int[]>> dropHoles = new ArrayList<>();

    private final List<UUID> players = new ArrayList<>();
    private final List<UUID> spectators = new ArrayList<>();
    private final List<Villager> livingVillagers = new ArrayList<>();
    private final List<LivingEntity> spawnedMobs = new ArrayList<>();
    // gracze już postawieni na polu areny: wybrali kit w poczekalni i dostali go
    // od razu do ekwipunku (teleport na arenę po wyborze). Start rundy ich nie
    // teleportuje ponownie ani nie nadpisuje ekwipunku.
    private final Set<UUID> onField = new HashSet<>();

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

    public List<int[]> getDropPolygon() { return dropPolygon; }

    public List<List<int[]>> getDropHoles() { return dropHoles; }

    /** Czy arena ma WŁASNY teren zrzutu (cokolwiek aktywnego: wielokąt >= 3 pkt
     *  albo co najmniej jedna dziura >= 3 pkt)? Jeśli nie - używany jest globalny. */
    public boolean hasDropZone() {
        if (dropPolygon.size() >= 3) return true;
        for (List<int[]> h : dropHoles) {
            if (h.size() >= 3) return true;
        }
        return false;
    }

    public String getName() { return name; }

    public ArenaState getState() { return state; }
    public void setState(ArenaState state) { this.state = state; }

    /** Czy arena jest SZABLONEM (dołączanie klonuje ją do osobnej instancji)? */
    public boolean isTemplate() { return template; }
    public void setTemplate(boolean template) { this.template = template; }

    /** Nazwa szablonu, z którego powstała ta instancja (null = zwykła arena). */
    public String getTemplateName() { return templateName; }
    public void setTemplateName(String templateName) { this.templateName = templateName; }

    /** Czy arena jest TYMCZASOWĄ instancją (klonem szablonu)? */
    public boolean isInstance() { return templateName != null; }

    public Location getLobbyLocation() { return lobbyLocation; }
    public void setLobbyLocation(Location lobbyLocation) { this.lobbyLocation = lobbyLocation; }

    public Location getEndLocation() { return endLocation; }
    public void setEndLocation(Location endLocation) { this.endLocation = endLocation; }

    public List<Location> getPlayerSpawns() { return playerSpawns; }
    public List<Location> getVillagerSpawns() { return villagerSpawns; }
    public List<Location> getMobSpawns() { return mobSpawns; }

    /**
     * Punkty spawnu graczy. Jeśli nie zostały ustawione, używamy lokalizacji
     * końcowej (fallback), żeby gra mogła wystartować od razu po skonfigurowaniu
     * areny (np. samym /def setend).
     */
    public List<Location> effectivePlayerSpawns() {
        if (!playerSpawns.isEmpty()) return playerSpawns;
        if (endLocation != null) return List.of(endLocation);
        return playerSpawns;
    }

    /** Punkty spawnu wieśniaków; fallback do lokalizacji końcowej (jak przy graczach). */
    public List<Location> effectiveVillagerSpawns() {
        if (!villagerSpawns.isEmpty()) return villagerSpawns;
        if (endLocation != null) return List.of(endLocation);
        return villagerSpawns;
    }

    public int getMinPlayers() { return minPlayers; }
    public void setMinPlayers(int minPlayers) { this.minPlayers = minPlayers; }

    public int getMaxPlayers() { return maxPlayers; }
    public void setMaxPlayers(int maxPlayers) { this.maxPlayers = maxPlayers; }

    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }

    public List<UUID> getPlayers() { return players; }
    public List<UUID> getSpectators() { return spectators; }

    public void markOnField(UUID uuid) { onField.add(uuid); }
    public boolean isOnField(UUID uuid) { return onField.contains(uuid); }
    public void unmarkOnField(UUID uuid) { onField.remove(uuid); }
    public List<Villager> getLivingVillagers() { return livingVillagers; }
    public List<LivingEntity> getSpawnedMobs() { return spawnedMobs; }

    public int getRound() { return round; }
    public void setRound(int round) { this.round = round; }

    public int getCountdown() { return countdown; }
    public void setCountdown(int countdown) { this.countdown = countdown; }

    public long getStartedAtMillis() { return startedAtMillis; }
    public void setStartedAtMillis(long startedAtMillis) { this.startedAtMillis = startedAtMillis; }

    public boolean isReadyToUse() {
        // lobby jest wymagane, ale jeśli go brakuje, arena może użyć lokalizacji końcowej jako fallbacku;
        // tak samo spawny graczy/wieśniaków mogą spaść na lokalizację końcową
        return (lobbyLocation != null || endLocation != null)
                && !effectivePlayerSpawns().isEmpty() && !effectiveVillagerSpawns().isEmpty();
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
        onField.clear();
    }

    public boolean isPlaying(Player p) {
        return players.contains(p.getUniqueId());
    }
}
