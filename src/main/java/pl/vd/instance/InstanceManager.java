package pl.vd.instance;

import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import pl.vd.VillagerDefensePlugin;
import pl.vd.arena.Arena;
import pl.vd.arena.ArenaState;
import pl.vd.util.Lang;
import pl.vd.util.WorldUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * System instancji: gdy arena jest oznaczona jako SZABLON (/def instancja <arena> on),
 * każdy gracz dołączający trafia do osobnej KOPII mapy. Mapa szablonowa jest klonowana
 * w locie (Paper WorldCreator.copy), a z konfiguracji szablonu tworzona jest arena-instancja
 * (te same spawny, przeniesione do sklonowanego świata). Po zakończeniu gry instancja jest
 * usuwana: arena znika z menedżera, świat jest wyładowywany, a folder kasowany.
 *
 * Instancje nigdy nie są zapisywane do arenas.yml (ArenaManager.save() je pomija).
 * Pozostałości po awarii serwera (foldery *_inst&lt;numer&gt;) są kasowane przy starcie
 * (cleanupLeftovers, wywoływane przed wczytaniem światów).
 */
public class InstanceManager {

    private final VillagerDefensePlugin plugin;

    /** Licznik nazw instancji per szablon (np. desert_inst1, desert_inst2, ...). */
    private final Map<String, Integer> counters = new HashMap<>();

    /** Nazwa folderu/dimensji świata instancji: <szablon>_inst<numer>. */
    private static final String INSTANCE_SUFFIX = "_inst\\d+";

    public InstanceManager(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Do jakiej areny trafi gracz dołączający do SZABLONU:
     * - istniejąca instancja czekająca w lobby (WAITING/STARTING, z wolnym miejscem),
     * - inaczej NOWA instancja (klon świata + kopia areny),
     * - limit równoczesnych instancji (instances.max-per-template) - null = odmowa,
     * - szablon nieskonfigurowany - zwraca sam szablon (normalny przepływ pokaże błąd).
     */
    public Arena resolveForJoin(Player player, Arena template) {
        if (!template.isReadyToUse()) return template;
        // dołącz do instancji, która już czeka w lobby i ma wolne miejsca
        for (Arena a : plugin.getArenaManager().getArenas().values()) {
            if (!a.isInstance()) continue;
            if (!template.getName().equalsIgnoreCase(a.getTemplateName())) continue;
            if (a.getState() == ArenaState.WAITING || a.getState() == ArenaState.STARTING) {
                if (a.getPlayers().size() < a.getMaxPlayers()) return a;
            }
        }
        int max = plugin.getConfig().getInt("instances.max-per-template", 5);
        if (max > 0 && activeInstanceCount(template) >= max) return null;
        return createInstance(template, player);
    }

    /** Liczba aktywnych instancji danego szablonu. */
    public int activeInstanceCount(Arena template) {
        int n = 0;
        for (Arena a : plugin.getArenaManager().getArenas().values()) {
            if (a.isInstance() && template.getName().equalsIgnoreCase(a.getTemplateName())) n++;
        }
        return n;
    }

    /**
     * Tworzy nową instancję szablonu: klonuje świat (WorldCreator.copy), tworzy arenę
     * z tą samą konfiguracją (spawny przeniesione do klona) i zwraca ją. null = błąd.
     */
    public Arena createInstance(Arena template, Player joiner) {
        World src = templateWorld(template);
        if (src == null) {
            plugin.getLogger().warning("Nie można utworzyć instancji areny " + template.getName()
                    + " - nie znaleziono świata szablonu.");
            return null;
        }
        String name = nextInstanceName(template);
        plugin.getLogger().info("Klonuję świat " + src.getName() + " jako instancję " + name
                + " (szablon " + template.getName() + ")...");
        long t0 = System.currentTimeMillis();
        World clone = null;
        try {
            clone = new WorldCreator(name).copy(src).createWorld();
        } catch (Exception ex) {
            plugin.getLogger().severe("Błąd klonowania świata instancji " + name + ": " + ex.getMessage());
            ex.printStackTrace();
        }
        if (clone == null) {
            plugin.getLogger().severe("Nie udało się utworzyć świata instancji " + name + ".");
            return null;
        }
        configureWorld(clone);

        Arena inst = new Arena(name);
        inst.setTemplateName(template.getName());
        inst.setState(ArenaState.WAITING);
        inst.setMinPlayers(template.getMinPlayers());
        inst.setMaxPlayers(template.getMaxPlayers());
        inst.setDifficulty(template.getDifficulty());
        inst.setLobbyLocation(rebase(template.getLobbyLocation(), clone));
        inst.setEndLocation(rebase(template.getEndLocation(), clone));
        for (Location l : template.getPlayerSpawns()) inst.getPlayerSpawns().add(rebase(l, clone));
        for (Location l : template.getVillagerSpawns()) inst.getVillagerSpawns().add(rebase(l, clone));
        for (Location l : template.getMobSpawns()) inst.getMobSpawns().add(rebase(l, clone));
        // własny teren zrzutu (współrzędne X/Z - takie same w klonie świata)
        inst.getDropPolygon().addAll(template.getDropPolygon());
        inst.getDropHoles().addAll(template.getDropHoles());
        plugin.getArenaManager().registerInstance(inst);

        plugin.getLogger().info("Instancja " + name + " utworzona w "
                + (System.currentTimeMillis() - t0) + " ms.");
        if (joiner != null) Lang.send(joiner, "instance-created", name);
        return inst;
    }

    /** Sprząta instancję po zakończeniu gry / opuszczeniu jej przez ostatniego gracza. */
    public void maybeDispose(Arena arena) {
        if (!arena.isInstance()) return;
        if (arena.getState() == ArenaState.INGAME || arena.getState() == ArenaState.STARTING) return;
        if (!arena.getPlayers().isEmpty()) return;
        dispose(arena);
    }

    /**
     * Usuwa instancję: arena znika z menedżera, świat jest wyładowywany (bez zapisu -
     * klon jest jednorazowy), a folder kasowany. Bezpieczna wielokrotnie (idempotentna).
     */
    public void dispose(Arena arena) {
        if (!arena.isInstance()) return;
        if (plugin.getArenaManager().getArena(arena.getName()) == null) return;
        plugin.getLogger().info("Zamykam instancję " + arena.getName() + " (szablon "
                + arena.getTemplateName() + ")...");
        plugin.getArenaManager().removeArena(arena.getName());

        World world = instanceWorld(arena);
        if (world == null) return;
        // na wszelki wypadek wyrzuć graczy, którzy zostali w świecie instancji
        for (Player p : new ArrayList<>(world.getPlayers())) {
            p.teleport(fallbackLocation(arena));
        }
        File folder = world.getWorldFolder();
        boolean unloaded = Bukkit.unloadWorld(world, false);
        if (!unloaded) {
            plugin.getLogger().warning("Nie udało się wyładować świata instancji " + world.getName()
                    + " - jego folder zostanie posprzątany przy następnym starcie serwera.");
            return;
        }
        scheduleFolderDeletion(folder, world.getName());
    }

    /** Zamyka WSZYSTKIE aktywne instancje (wyłączanie serwera). */
    public void disposeAll() {
        for (Arena a : new ArrayList<>(plugin.getArenaManager().getArenas().values())) {
            if (a.isInstance()) dispose(a);
        }
    }

    /**
     * Kasuje przy starcie serwera foldery pozostałe po instancjach (np. po crashu).
     * Musi być wywołane PRZED wczytaniem światów (loadCustomWorlds), żeby pozostałości
     * nie zostały załadowane jako zwykłe światy. Skaruje foldery na poziomie serwera
     * oraz dimensje w nowym formacie Paper (WorldUtil).
     */
    public static void cleanupLeftovers(VillagerDefensePlugin plugin) {
        File container = Bukkit.getWorldContainer();
        File[] rootFolders = container.listFiles();
        if (rootFolders != null) {
            for (File f : rootFolders) {
                if (f.isDirectory() && f.getName().matches(".*" + INSTANCE_SUFFIX)) {
                    deleteFolderNow(f);
                    plugin.getLogger().info("Usunięto pozostałość po instancji (awaria?): " + f.getName());
                }
            }
        }
        for (World w : Bukkit.getWorlds()) {
            File dimsRoot = WorldUtil.findDimensionsRoot(w);
            if (dimsRoot == null) continue;
            File dims = new File(dimsRoot, "dimensions");
            File[] namespaces = dims.listFiles();
            if (namespaces == null) continue;
            for (File ns : namespaces) {
                if (!ns.isDirectory()) continue;
                File[] worlds = ns.listFiles();
                if (worlds == null) continue;
                for (File f : worlds) {
                    if (f.isDirectory() && f.getName().matches(".*" + INSTANCE_SUFFIX)) {
                        deleteFolderNow(f);
                        plugin.getLogger().info("Usunięto pozostałość po instancji (awaria?): " + f.getName());
                    }
                }
            }
        }
    }

    // ===================== pomocnicze =====================

    private String nextInstanceName(Arena template) {
        int n = counters.merge(template.getName().toLowerCase(), 1, Integer::sum);
        return template.getName() + "_inst" + n;
    }

    /** Świat szablonu: lobby -> end -> spawn graczy -> spawn wieśniaków. */
    private World templateWorld(Arena template) {
        if (template.getLobbyLocation() != null && template.getLobbyLocation().getWorld() != null)
            return template.getLobbyLocation().getWorld();
        if (template.getEndLocation() != null && template.getEndLocation().getWorld() != null)
            return template.getEndLocation().getWorld();
        if (!template.getPlayerSpawns().isEmpty() && template.getPlayerSpawns().get(0).getWorld() != null)
            return template.getPlayerSpawns().get(0).getWorld();
        if (!template.getVillagerSpawns().isEmpty() && template.getVillagerSpawns().get(0).getWorld() != null)
            return template.getVillagerSpawns().get(0).getWorld();
        return null;
    }

    /** Świat instancji (po lokacjach skopiowanych z szablonu). */
    private World instanceWorld(Arena arena) {
        if (arena.getLobbyLocation() != null && arena.getLobbyLocation().getWorld() != null)
            return arena.getLobbyLocation().getWorld();
        if (arena.getEndLocation() != null && arena.getEndLocation().getWorld() != null)
            return arena.getEndLocation().getWorld();
        if (!arena.getPlayerSpawns().isEmpty() && arena.getPlayerSpawns().get(0).getWorld() != null)
            return arena.getPlayerSpawns().get(0).getWorld();
        return null;
    }

    /** Ta sama pozycja w innym świecie (świeżo sklonowanym). */
    private Location rebase(Location loc, World w) {
        if (loc == null) return null;
        return new Location(w, loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
    }

    /** Gdzie trafia gracz, gdy świat instancji znika (lokalizacja końcowa szablonu). */
    private Location fallbackLocation(Arena instance) {
        Arena t = plugin.getArenaManager().getArena(instance.getTemplateName());
        if (t != null && t.getEndLocation() != null) return t.getEndLocation();
        if (t != null && t.getLobbyLocation() != null) return t.getLobbyLocation();
        return Bukkit.getWorlds().get(0).getSpawnLocation();
    }

    /** Ustawia reguły świata minigry (jak /def createworld). */
    private void configureWorld(World w) {
        w.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        w.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        w.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        w.setTime(6000);
        w.setStorm(false);
        w.setThundering(false);
    }

    /**
     * Kasowanie folderu świata po wyładowaniu: próby co 5 ticków (świat potrafi
     * zwalniać pliki z opóźnieniem), bez blokowania głównego wątku. Po ~40 próbach
     * (10 s) rezygnuje - sprzątanie przy następnym starcie.
     */
    private void scheduleFolderDeletion(File folder, String worldName) {
        if (folder == null || !folder.exists()) return;
        Bukkit.getScheduler().runTaskTimer(plugin, new BukkitRunnable() {
            private int tries = 0;

            @Override
            public void run() {
                boolean done = !folder.exists() || deleteRecursively(folder);
                if (done || ++tries > 40) {
                    cancel();
                    if (!done) {
                        plugin.getLogger().warning("Nie udało się usunąć folderu świata instancji "
                                + worldName + " (" + folder.getAbsolutePath()
                                + ") - zostanie posprzątany przy następnym starcie serwera.");
                    }
                }
            }
        }, 5L, 5L);
    }

    private boolean deleteRecursively(File file) {
        if (file == null) return true;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                if (!deleteRecursively(child)) return false;
            }
        }
        return file.delete();
    }

    private static void deleteFolderNow(File folder) {
        File[] children = folder.listFiles();
        if (children != null) {
            for (File child : children) deleteFolderNow(child);
        }
        folder.delete();
    }
}
