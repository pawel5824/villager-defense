package pl.vd.sign;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import pl.vd.VillagerDefensePlugin;
import pl.vd.arena.Arena;
import pl.vd.util.LocationUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class SignManager {

    private final VillagerDefensePlugin plugin;
    private final File file;
    private final Map<Location, String> signs = new LinkedHashMap<>(); // lokalizacja tabliczki -> nazwa areny
    /** Tabliczki rotacji map: lokalizacja -> pula map + aktualnie wyświetlana mapa. */
    private final Map<Location, MapRotation> rotationSigns = new LinkedHashMap<>();

    /** Stan tabliczki rotacji: pula map + aktualnie wyświetlana mapa. */
    public static class MapRotation {
        public final List<String> pool = new ArrayList<>();
        public String current;
    }

    private final Random random = new Random();

    public SignManager(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "signs.yml");
        load();
    }

    // ---------- Rejestracja ----------

    public void register(Location location, String arenaName) {
        rotationSigns.remove(location); // jedna tabliczka = jeden typ
        signs.put(location, arenaName);
        save();
    }

    /** Rejestruje tabliczkę rotacji map (pula + losowa mapa startowa). Zwraca wylosowaną mapę startową. */
    public String registerRotation(Location location, List<String> pool) {
        signs.remove(location);
        MapRotation rot = new MapRotation();
        rot.pool.addAll(pool);
        rot.current = pickRandom(rot.pool, null);
        rotationSigns.put(location, rot);
        save();
        return rot.current;
    }

    public void unregister(Location location) {
        signs.remove(location);
        rotationSigns.remove(location);
        save();
    }

    /** Arena do dołączenia po kliknięciu tabliczki (zwykła albo aktualna mapa rotacji). */
    public String getJoinArenaName(Location location) {
        String direct = signs.get(location);
        if (direct != null) return direct;
        MapRotation rot = rotationSigns.get(location);
        return rot != null ? rot.current : null;
    }

    public boolean isRotationSign(Location location) {
        return rotationSigns.containsKey(location);
    }

    // ---------- Rotacja map ----------

    /**
     * Obrót mapy: gdy gra na mapie wyświetlanej na tabliczce wystartowała,
     * tabliczka losuje inną mapę z puli (także dla instancji - porównuje szablon).
     */
    public void onGameStarted(Arena arena) {
        if (rotationSigns.isEmpty()) return;
        String mapName = arena.isInstance() && arena.getTemplateName() != null
                ? arena.getTemplateName() : arena.getName();
        boolean changed = false;
        for (Map.Entry<Location, MapRotation> e : rotationSigns.entrySet()) {
            MapRotation rot = e.getValue();
            if (rot.current == null || !rot.current.equalsIgnoreCase(mapName)) continue;
            String next = pickRandom(rot.pool, mapName);
            if (next != null) {
                rot.current = next;
                changed = true;
            }
        }
        if (changed) {
            save();
            updateAllDisplays();
        }
    }

    /** Losowa mapa z puli (istniejąca w menedżerze), różna od „exclude". null = brak kandydatów. */
    private String pickRandom(List<String> pool, String exclude) {
        List<String> candidates = new ArrayList<>();
        for (String name : pool) {
            if (name.equalsIgnoreCase(exclude)) continue;
            if (plugin.getArenaManager().getArena(name) == null) continue;
            candidates.add(name);
        }
        if (candidates.isEmpty()) return null;
        return candidates.get(random.nextInt(candidates.size()));
    }

    // ---------- Zapis / odczyt ----------

    public void load() {
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Nie udało się utworzyć signs.yml: " + e.getMessage());
            }
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        signs.clear();
        rotationSigns.clear();

        ConfigurationSection section = cfg.getConfigurationSection("signs");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection s = section.getConfigurationSection(key);
                if (s == null) continue;
                Location loc = LocationUtil.deserialize(s.getString("location"));
                String arenaName = s.getString("arena");
                if (loc != null && arenaName != null) {
                    signs.put(loc, arenaName);
                }
            }
        }

        ConfigurationSection rotSection = cfg.getConfigurationSection("rotation-signs");
        if (rotSection != null) {
            for (String key : rotSection.getKeys(false)) {
                ConfigurationSection s = rotSection.getConfigurationSection(key);
                if (s == null) continue;
                Location loc = LocationUtil.deserialize(s.getString("location"));
                List<String> maps = s.getStringList("maps");
                String current = s.getString("current");
                if (loc == null || maps.isEmpty()) continue;
                MapRotation rot = new MapRotation();
                rot.pool.addAll(maps);
                rot.current = (current != null && maps.contains(current)) ? current : pickRandom(rot.pool, null);
                rotationSigns.put(loc, rot);
            }
        }
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        int i = 0;
        for (Map.Entry<Location, String> entry : signs.entrySet()) {
            String path = "signs.sign" + i;
            cfg.set(path + ".location", LocationUtil.serialize(entry.getKey()));
            cfg.set(path + ".arena", entry.getValue());
            i++;
        }
        int j = 0;
        for (Map.Entry<Location, MapRotation> entry : rotationSigns.entrySet()) {
            String path = "rotation-signs.sign" + j;
            cfg.set(path + ".location", LocationUtil.serialize(entry.getKey()));
            cfg.set(path + ".maps", entry.getValue().pool);
            cfg.set(path + ".current", entry.getValue().current);
            j++;
        }
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Nie udało się zapisać signs.yml: " + e.getMessage());
        }
    }

    // ---------- Odświeżanie wyświetlanych linii ----------

    public void updateAllDisplays() {
        if (signs.isEmpty() && rotationSigns.isEmpty()) return; // brak tabliczek — nic do odświeżenia
        // kopie kluczy: updateDisplay może wyrejestrować zniszczoną tabliczkę w trakcie iteracji
        for (Location loc : new ArrayList<>(signs.keySet())) {
            updateDisplay(loc, signs.get(loc));
        }
        for (Location loc : new ArrayList<>(rotationSigns.keySet())) {
            MapRotation rot = rotationSigns.get(loc);
            // aktualna mapa zniknęła z serwera - wylosuj inną z puli
            if (rot.current != null && plugin.getArenaManager().getArena(rot.current) == null) {
                String next = pickRandom(rot.pool, null);
                if (next != null) {
                    rot.current = next;
                    save();
                }
            }
            updateDisplay(loc, rot.current); // null = w puli nie ma już żadnej istniejącej mapy
        }
    }

    public void updateDisplay(Location loc, String arenaName) {
        if (loc.getWorld() == null || !loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) return;

        if (!(loc.getBlock().getState() instanceof Sign sign)) {
            unregister(loc); // tabliczka została zniszczona/zniknęła
            return;
        }

        var side = sign.getSide(Side.FRONT);

        if (arenaName == null) {
            // tabliczka rotacji, której pula nie zawiera już żadnej istniejącej mapy
            if (!isRotationSign(loc)) return;
            side.line(0, Component.text("§d[Mapa]"));
            side.line(1, Component.text("§f?"));
            side.line(2, Component.text("§cBrak map w puli"));
            side.line(3, Component.text(""));
            sign.update();
            return;
        }

        Arena arena = plugin.getArenaManager().getArena(arenaName);
        int count = 0, max = 0;

        if (arena != null && arena.isTemplate()) {
            // Szablon: gracze grają w instancjach (klonach) - zsumuj liczbę graczy.
            max = arena.getMaxPlayers();
            for (Arena a : plugin.getArenaManager().getArenas().values()) {
                if (!a.isInstance() || !arena.getName().equalsIgnoreCase(a.getTemplateName())) continue;
                count += a.getPlayers().size();
            }
        } else if (arena != null) {
            count = arena.getPlayers().size();
            max = arena.getMaxPlayers();
        }

        side.line(0, Component.text(isRotationSign(loc) ? "§d[Mapa]" : "§b[Arena]"));
        side.line(1, Component.text("§f" + arenaName));
        side.line(2, Component.text(arena == null ? "§cArena usunięta" : "§f(" + count + "/" + max + ") §7graczy"));
        side.line(3, Component.text(arena == null ? "" : "§aKliknij"));
        sign.update();
    }
}
