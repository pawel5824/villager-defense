package pl.vd.arena;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import pl.vd.VillagerDefensePlugin;
import pl.vd.supply.SupplyDropManager;
import pl.vd.util.LocationUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public class ArenaManager implements Listener {

    private final VillagerDefensePlugin plugin;
    private final Map<String, Arena> arenas = new LinkedHashMap<>();
    private final File file;
    private YamlConfiguration cfg;

    // Surowe, jeszcze niezdeserializowane lokacje (świat nie był wczytany przy starcie pluginu).
    // Trzymamy je, żeby NIGDY nie zgubić danych: są ponawiane po każdym wczytaniu świata
    // (WorldLoadEvent) i zapisywane razem z resztą konfiguracji, dopóki się nie rozwiążą.
    private final Map<String, String> pendingLobby = new LinkedHashMap<>();
    private final Map<String, String> pendingEnd = new LinkedHashMap<>();
    private final Map<String, List<String>> pendingPlayerSpawns = new LinkedHashMap<>();
    private final Map<String, List<String>> pendingVillagerSpawns = new LinkedHashMap<>();
    private final Map<String, List<String>> pendingMobSpawns = new LinkedHashMap<>();

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

    /**
     * Rejestruje arenę-instancję (tymczasowy klon szablonu) BEZ zapisu do arenas.yml.
     * Wywoływane przez InstanceManager po wypełnieniu wszystkich pól instancji -
     * inaczej pierwszy save() zapisałby pustą arenę do pliku.
     */
    public void registerInstance(Arena arena) {
        arenas.put(arena.getName().toLowerCase(), arena);
    }

    public boolean removeArena(String name) {
        if (arenas.remove(name.toLowerCase()) != null) {
            pendingLobby.remove(name.toLowerCase());
            pendingEnd.remove(name.toLowerCase());
            pendingPlayerSpawns.remove(name.toLowerCase());
            pendingVillagerSpawns.remove(name.toLowerCase());
            pendingMobSpawns.remove(name.toLowerCase());
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
        pendingLobby.clear();
        pendingEnd.clear();
        pendingPlayerSpawns.clear();
        pendingVillagerSpawns.clear();
        pendingMobSpawns.clear();

        ConfigurationSection root = cfg.getConfigurationSection("arenas");
        if (root == null) return;

        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) continue;
            Arena arena = new Arena(key);
            String lobbyRaw = s.getString("lobby");
            String endRaw = s.getString("end");
            Location lobby = deserializeOrWarn(key, "lobby", lobbyRaw);
            Location end = deserializeOrWarn(key, "end", endRaw);
            if (lobby != null) {
                arena.setLobbyLocation(lobby);
            } else if (lobbyRaw != null && !lobbyRaw.isBlank()) {
                pendingLobby.put(key.toLowerCase(), lobbyRaw);
            }
            if (end != null) {
                arena.setEndLocation(end);
            } else if (endRaw != null && !endRaw.isBlank()) {
                pendingEnd.put(key.toLowerCase(), endRaw);
            }
            arena.setMinPlayers(s.getInt("min-players", 2));
            arena.setMaxPlayers(s.getInt("max-players", 8));
            arena.setDifficulty(s.getString("difficulty", "normalny"));
            arena.setTemplate(s.getBoolean("template", false));

            // własny teren zrzutu zaopatrzenia tej areny (pusty = użyje globalnego z config.yml)
            arena.getDropPolygon().addAll(SupplyDropManager.parsePoints(s.get("supply-drop.polygon")));
            arena.getDropHoles().addAll(SupplyDropManager.parseHoles(s.get("supply-drop.excluded")));

            for (String loc : s.getStringList("player-spawns")) {
                var l = deserializeOrWarn(key, "spawn gracza", loc);
                if (l != null) arena.getPlayerSpawns().add(l);
                else pendingPlayerSpawns.computeIfAbsent(key.toLowerCase(), k -> new ArrayList<>()).add(loc);
            }
            for (String loc : s.getStringList("villager-spawns")) {
                var l = deserializeOrWarn(key, "spawn wieśniaka", loc);
                if (l != null) arena.getVillagerSpawns().add(l);
                else pendingVillagerSpawns.computeIfAbsent(key.toLowerCase(), k -> new ArrayList<>()).add(loc);
            }
            for (String loc : s.getStringList("mob-spawns")) {
                var l = deserializeOrWarn(key, "spawn mobów", loc);
                if (l != null) arena.getMobSpawns().add(l);
                else pendingMobSpawns.computeIfAbsent(key.toLowerCase(), k -> new ArrayList<>()).add(loc);
            }
            arena.setState(ArenaState.WAITING);
            arenas.put(key.toLowerCase(), arena);
        }
    }

    /**
     * Deserializuje lokację areny; gdy świat nie jest wczytany, loguje ostrzeżenie i zwraca null.
     * Dane surowe nie są gubione — trafiają do map "pending" i są ponawiane po wczytaniu świata.
     */
    private org.bukkit.Location deserializeOrWarn(String arena, String what, String raw) {
        org.bukkit.Location loc = LocationUtil.deserialize(raw);
        if (loc == null && raw != null && !raw.isBlank()) {
            plugin.getLogger().warning("Arena " + arena + ": nie wczytano jeszcze " + what
                    + " (świat nie wczytany?) - ponowię po wczytaniu świata: " + raw);
        }
        return loc;
    }

    public void save() {
        cfg = new YamlConfiguration();
        for (Arena arena : arenas.values()) {
            // instancje (klony szablonów) są TYMCZASOWE - nigdy nie trafiają do arenas.yml
            if (arena.isInstance()) continue;
            String key = arena.getName().toLowerCase();
            String path = "arenas." + arena.getName();

            Location lobby = arena.getLobbyLocation();
            cfg.set(path + ".lobby", lobby != null ? LocationUtil.serialize(lobby) : pendingLobby.get(key));
            Location end = arena.getEndLocation();
            cfg.set(path + ".end", end != null ? LocationUtil.serialize(end) : pendingEnd.get(key));
            cfg.set(path + ".min-players", arena.getMinPlayers());
            cfg.set(path + ".max-players", arena.getMaxPlayers());
            cfg.set(path + ".difficulty", arena.getDifficulty());
            if (arena.isTemplate()) cfg.set(path + ".template", true);

            cfg.set(path + ".player-spawns", mergedSpawns(arena.getPlayerSpawns(), pendingPlayerSpawns.get(key)));
            cfg.set(path + ".villager-spawns", mergedSpawns(arena.getVillagerSpawns(), pendingVillagerSpawns.get(key)));
            cfg.set(path + ".mob-spawns", mergedSpawns(arena.getMobSpawns(), pendingMobSpawns.get(key)));

            // własny teren zrzutu (zapisujemy tylko, gdy arena ma cokolwiek aktywnego)
            if (arena.hasDropZone()) {
                cfg.set(path + ".supply-drop.polygon", SupplyDropManager.serializePoints(arena.getDropPolygon()));
                cfg.set(path + ".supply-drop.excluded", SupplyDropManager.serializeHoles(arena.getDropHoles()));
            }
        }
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Nie udało się zapisać arenas.yml: " + e.getMessage());
        }
    }

    /** Lista zapisu: rzeczywiste (już zdeserializowane) lokacje + surowe oczekujące na świat. */
    private List<String> mergedSpawns(List<Location> real, List<String> pending) {
        List<String> out = real.stream().map(LocationUtil::serialize).toList();
        if (pending == null || pending.isEmpty()) return out;
        List<String> merged = new ArrayList<>(out);
        merged.addAll(pending);
        return merged;
    }

    /** Wywoływane po każdym wczytaniu świata: próbuje rozwiązać oczekujące lokacje. */
    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        retryPending();
    }

    public void retryPending() {
        boolean changed = false;

        for (Map.Entry<String, String> e : new ArrayList<>(pendingLobby.entrySet())) {
            Arena arena = arenas.get(e.getKey());
            Location l = LocationUtil.deserialize(e.getValue());
            if (arena != null && l != null) {
                arena.setLobbyLocation(l);
                pendingLobby.remove(e.getKey());
                changed = true;
                plugin.getLogger().info("Arena " + arena.getName() + ": wczytano lobby (świat " + l.getWorld().getName() + ")");
            }
        }
        for (Map.Entry<String, String> e : new ArrayList<>(pendingEnd.entrySet())) {
            Arena arena = arenas.get(e.getKey());
            Location l = LocationUtil.deserialize(e.getValue());
            if (arena != null && l != null) {
                arena.setEndLocation(l);
                pendingEnd.remove(e.getKey());
                changed = true;
                plugin.getLogger().info("Arena " + arena.getName() + ": wczytano lokalizację końcową (świat " + l.getWorld().getName() + ")");
            }
        }

        changed |= resolveSpawns(pendingPlayerSpawns, (a, l) -> a.getPlayerSpawns().add(l), "spawnów graczy");
        changed |= resolveSpawns(pendingVillagerSpawns, (a, l) -> a.getVillagerSpawns().add(l), "spawnów wieśniaków");
        changed |= resolveSpawns(pendingMobSpawns, (a, l) -> a.getMobSpawns().add(l), "spawnów mobów");

        if (changed) save();
    }

    private boolean resolveSpawns(Map<String, List<String>> pending, BiConsumer<Arena, Location> addTo, String what) {
        boolean changed = false;
        for (Map.Entry<String, List<String>> e : new ArrayList<>(pending.entrySet())) {
            Arena arena = arenas.get(e.getKey());
            if (arena == null) continue;
            Iterator<String> it = e.getValue().iterator();
            while (it.hasNext()) {
                String raw = it.next();
                Location l = LocationUtil.deserialize(raw);
                if (l != null) {
                    addTo.accept(arena, l);
                    it.remove();
                    changed = true;
                    plugin.getLogger().info("Arena " + arena.getName() + ": wczytano " + what
                            + " (świat " + l.getWorld().getName() + ")");
                }
            }
            if (e.getValue().isEmpty()) pending.remove(e.getKey());
        }
        return changed;
    }
}
