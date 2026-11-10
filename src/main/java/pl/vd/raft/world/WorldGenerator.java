package pl.vd.raft.world;

import io.papermc.paper.datapack.Datapack;
import io.papermc.paper.datapack.DatapackManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import pl.vd.raft.RaftPlugin;

import java.io.*;
import java.nio.file.*;
import java.util.Random;

/**
 * Zarządzanie światem Raft.
 *
 * Priorytet: jeśli folder świata "RAFT" już istnieje (np. wrzucony ręcznie
 * z gotowym oceanem), plugin go po prostu ładuje. Jeśli nie — generuje
 * ocean za pomocą DeepOceanGenerator (fallback).
 *
 * Jak stworzyć własny świat:
 *   1. Użyj superflat z generatorem wody (np. through Amidst albo MC本身)
 *   2. Wrzuć folder świata do katalogu serwera jako "RAFT"
 *   3. Uruchom serwer — plugin wykryje istniejący świat
 */
public class WorldGenerator {

    private final RaftPlugin plugin;
    private final Random random = new Random();
    private World raftWorld;

    /** Poziom morza. */
    public static final int SEA_LEVEL = DeepOceanGenerator.SEA_LEVEL;

    /** Limity wysokości świata Raft, na które zadeklarowany jest datapack (patrz pack.mcmeta). */
    private static final int HEIGHT_MIN_Y = -64;
    private static final int HEIGHT_TOTAL = 352; // Y -64..287
    /** Folder datapacka w <level-name>/datapacks i jego id w repozytorium Papera. */
    private static final String HEIGHT_PACK_DIR = "raft-height";
    private static final String HEIGHT_PACK_ID = "file/" + HEIGHT_PACK_DIR;
    /** Ścieżka, do której ostatnio celował deploy (null = nie deployowaliśmy). */
    private Path heightPackDir;

    public WorldGenerator(RaftPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Ładuje lub generuje świat Raft.
     * Jeśli folder świata istnieje — ładuje go (bez generowania).
     * Jeśli nie — generuje ocean za pomocą DeepOceanGenerator.
     */
    public World loadOrCreateWorld(String worldName) {
        // Datapack z limitami wysokości (Y -64..287, height=352) — domyślnie wyłączony,
        // szczegóły i pułapki: syncHeightDatapack() oraz komentarz w config.yml.
        syncHeightDatapack();

        // Sprawdź czy świat już istnieje (np. wrzucony ręcznie)
        World existing = plugin.getServer().getWorld(worldName);
        if (existing != null) {
            plugin.getLogger().info("[WorldGenerator] Świat '" + worldName + "' już istnieje — ładuję.");
            raftWorld = existing;
            applyWorldSettings(raftWorld);
            return raftWorld;
        }

        // Świat nie jest załadowany — ale jego folder może istnieć na dysku pod inną
        // wielkością liter (Paper zapisuje dimensje małymi literami: "RAFT" -> "raft").
        // Użyj rzeczywistej nazwy folderu, żeby nie stworzyć duplikatu świata.
        String actualName = findExistingWorldFolder(worldName);
        String nameToLoad = actualName != null ? actualName : worldName;

        World existingOnDisk = plugin.getServer().getWorld(nameToLoad);
        if (existingOnDisk != null) {
            plugin.getLogger().info("[WorldGenerator] Świat '" + nameToLoad + "' już istnieje — ładuję.");
            raftWorld = existingOnDisk;
            applyWorldSettings(raftWorld);
            return raftWorld;
        }

        // Świat nie jest załadowany — wczytaj lub wygeneruj z DeepOceanGenerator.
        // Generator ma znaczenie także dla ISTNIEJĄCEGO folderu: odpowiada za
        // generowanie NOWYCH chunków (bez niego nowe chunki byłyby pustką).
        plugin.getLogger().info("[WorldGenerator] Świat '" + nameToLoad + "' nie jest załadowany — wczytuję/generuję z DeepOceanGenerator...");
        WorldCreator creator = new WorldCreator(nameToLoad);
        creator.environment(World.Environment.NORMAL);
        creator.seed(random.nextLong());
        creator.generator(new DeepOceanGenerator());

        raftWorld = creator.createWorld();
        if (raftWorld == null) {
            plugin.getLogger().severe("Nie udało się wygenerować świata Raft!");
            return null;
        }

        applyWorldSettings(raftWorld);
        plugin.getLogger().info("[WorldGenerator] Świat Raft gotowy!");

        return raftWorld;
    }

    /** Ustawienia świata niezależne od sposobu generowania. */
    private void applyWorldSettings(World world) {
        world.setGameRuleValue("doDaylightCycle", "true");
        world.setGameRuleValue("doWeatherCycle", "true");
        world.setGameRuleValue("doMobSpawning", "false");

        // View distance i simulation distance (jak view-distance=8, simulation-distance=3 w server.properties)
        int viewDistance = plugin.getConfig().getInt("raft.view-distance", 8);
        int simulationDistance = plugin.getConfig().getInt("raft.simulation-distance", 3);
        world.setViewDistance(viewDistance);
        world.setSimulationDistance(simulationDistance);
        plugin.getLogger().info("[WorldGenerator] View distance: " + viewDistance + ", Simulation distance: " + simulationDistance);

        // Realna wysokość świata kontra deklaracja datapacka — jedyny uczciwy sposób,
        // żeby powiedzieć, czy limity wysokości faktycznie się załadowały.
        verifyHeightLimits(world);

        // World border — ogranicz rozmiar świata, żeby nie generować
        // chunków w nieskończoność (oszczędność dysku i RAM).
        int mapSize = plugin.getConfig().getInt("raft.map-size", 1000);
        WorldBorder border = world.getWorldBorder();
        border.setCenter(new Location(world, 0, 0, 0));
        border.setSize(mapSize);
        plugin.getLogger().info("[WorldGenerator] World border ustawiony: " + mapSize + " bloków");
    }

    /**
     * Tworzy startowy raft (5x5) na poziomie morza.
     * Wywoływane tylko przy generowaniu nowego świata.
     */
    private void createStartingRaft(World world) {
        int centerX = 0;
        int centerZ = 0;
        int centerY = SEA_LEVEL;

        // Ponton 5x5
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                Block block = world.getBlockAt(centerX + x, centerY, centerZ + z);
                block.setType(Material.OAK_PLANKS);
            }
        }

        // Maszt
        for (int y = 1; y <= 4; y++) {
            world.getBlockAt(centerX, centerY + y, centerZ).setType(Material.OAK_FENCE);
        }

        // Żagiel
        world.getBlockAt(centerX + 1, centerY + 2, centerZ).setType(Material.WHITE_WOOL);
        world.getBlockAt(centerX + 1, centerY + 3, centerZ).setType(Material.WHITE_WOOL);
        world.getBlockAt(centerX + 2, centerY + 3, centerZ).setType(Material.WHITE_WOOL);

        // Workbench
        world.getBlockAt(centerX - 1, centerY + 1, centerZ - 1).setType(Material.CRAFTING_TABLE);

        // Skrzynia
        world.getBlockAt(centerX + 1, centerY + 1, centerZ - 1).setType(Material.CHEST);
    }

    /**
     * Szuka na dysku folderu świata o podanej nazwie (niezależnie od wielkości liter):
     * najpierw w głównym katalogu serwera, potem w dimensjach wczytanych światów
     * (nowy format Paper: &lt;root&gt;/dimensions/&lt;ns&gt;/&lt;nazwa&gt;).
     * Zwraca rzeczywistą nazwę folderu albo null, gdy nie istnieje.
     */
    private String findExistingWorldFolder(String worldName) {
        File container = Bukkit.getWorldContainer();
        File[] roots = container.listFiles();
        if (roots == null) return null;

        // 1) top-level foldery światów
        for (File f : roots) {
            if (!f.isDirectory() || !f.getName().equalsIgnoreCase(worldName)) continue;
            if (new File(f, "level.dat").exists() || new File(f, "region").isDirectory()
                    || new File(f, "data").isDirectory()) {
                return f.getName();
            }
        }

        // 2) dimensje wewnątrz wczytanych światów (nowy format Paper)
        for (File root : roots) {
            if (!root.isDirectory()) continue;
            File[] namespaces = new File(root, "dimensions").listFiles();
            if (namespaces == null) continue;
            for (File ns : namespaces) {
                if (!ns.isDirectory()) continue;
                File[] worlds = ns.listFiles();
                if (worlds == null) continue;
                for (File w : worlds) {
                    if (w.isDirectory() && w.getName().equalsIgnoreCase(worldName)) {
                        return w.getName();
                    }
                }
            }
        }
        return null;
    }

    public World getRaftWorld() {
        return raftWorld;
    }

    /**
     * Wdraża (albo tylko pilnuje) datapack "raft-height" z limitami wysokości świata.
     *
     * GDZIE: serwer czyta datapacki WYŁĄCZNIE z folderu poziomu
     * ({@code <level-name>/datapacks}), nie z katalogu serwera — dowód w kodzie 26.3:
     * {@code ServerPacksSource.createPackRepository(levelPath.resolve(LevelResource.DATAPACK_DIR))}.
     * Poprzednie wersje pluginu pisały do {@code <serwer>/datapacks}, więc datapack
     * nigdy się nie wczytywał (level.dat nie miał go na liście DataPacks.Enabled —
     * była tam tylko wanilia, file/bukkit i paper).
     *
     * KIEDY: plik jest przepisywany, gdy różni się od tego z JAR-a, więc podbicie
     * formatu przy zmianie wersji Minecrafta samo się propaguje na serwer.
     *
     * UWAGA (sprawdzone w sandboxie na Paper 26.3): zły plik dimension_type to nie
     * kosmetyka, a BRICK serwera — vanilla przerywa start komunikatem "Failed to load
     * datapacks, can't proceed with server load". Stary, płaski schemat wymiaru
     * (`natural`, `piglin_safe`, `bed_works`, `has_raids`, `effects`,
     * `monster_spawn_light_level.value`) NIE parsuje się już w 26.x: wymagane są
     * `attributes`, `timelines`, `has_ender_dragon_fight`, a uniform ma płaskie
     * `min_inclusive`/`max_inclusive`. Szablon bierzemy z waniliowego
     * `data/minecraft/dimension_type/overworld.json` danej wersji, zmieniając tylko
     * `height`/`logical_height`. Co gorsza: przy zepsutym pliku plugin nie zdąży go
     * naprawić (serwer nie dochodzi do włączania pluginów), więc poprawki testuj
     * najpierw na kopii serwera.
     *
     * UWAGA: datapack nadpisuje wymiar typu {@code minecraft:overworld}, który dzielą
     * wszystkie światy NORMAL na serwerze, a zmiana height działa TYLKO przy tworzeniu
     * nowego świata. Dlatego deploy jest za flagą {@code raft-height-datapack}
     * (domyślnie false w config.yml).
     */
    private void syncHeightDatapack() {
        File container = Bukkit.getWorldContainer();
        File levelDir = findLevelDir(container);
        Path packDir = levelDir.toPath().resolve("datapacks").resolve(HEIGHT_PACK_DIR);
        heightPackDir = packDir;

        // Stara, NIGDY nieczytana lokalizacja: poprzednie wersje pluginu piszą tu datapack.
        // Nie kasujemy cudzych plików — tylko mówimy wprost, że są bezużyteczne.
        Path legacyDir = container.toPath().resolve("datapacks").resolve(HEIGHT_PACK_DIR);
        if (Files.isRegularFile(legacyDir.resolve("pack.mcmeta"))) {
            plugin.getLogger().warning("[WorldGenerator] Datapack leży w " + legacyDir
                    + ", a serwer tego folderu NIE czyta (datapacki wczytuje tylko "
                    + levelDir.getName() + "/datapacks). Tę kopię można usunąć.");
        }

        if (!plugin.getConfig().getBoolean("raft-height-datapack", false)) {
            if (Files.isRegularFile(packDir.resolve("pack.mcmeta"))) {
                plugin.getLogger().info("[WorldGenerator] Datapack raft-height leży już w " + packDir
                        + " (raft-height-datapack=false — plugin go nie odświeża).");
            }
            return;
        }

        try {
            boolean metaChanged = writeResourceIfChanged("datapacks/raft-height/pack.mcmeta",
                    packDir.resolve("pack.mcmeta"));
            boolean dimChanged = writeResourceIfChanged(
                    "datapacks/raft-height/data/minecraft/dimension_type/overworld.json",
                    packDir.resolve("data/minecraft/dimension_type/overworld.json"));

            if (metaChanged || dimChanged) {
                plugin.getLogger().info("[WorldGenerator] Datapack raft-height wdrożony do: " + packDir
                        + " (pack.mcmeta: " + (metaChanged ? "zaktualizowany" : "bez zmian")
                        + ", dimension_type: " + (dimChanged ? "zaktualizowany" : "bez zmian") + ")");
            } else {
                plugin.getLogger().info("[WorldGenerator] Datapack raft-height aktualny w: " + packDir);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[WorldGenerator] Nie udało się wdrożyć datapacka wysokości: " + e.getMessage());
        }
    }

    /**
     * Folder poziomu — ten, którego podkatalog {@code datapacks} czyta serwer.
     * Szukamy po pliku {@code level.dat}, żeby nie polegać na server.properties.
     */
    private File findLevelDir(File container) {
        File[] children = container.listFiles();
        if (children != null) {
            for (File f : children) {
                if (f.isDirectory() && new File(f, "level.dat").isFile()) return f;
            }
        }
        return new File(container, "world");
    }

    /**
     * Sprawdza i loguje, czy limity wysokości FAKTYCZNIE działają na tym świecie:
     * realna wysokość świata z API kontra to, co deklaruje datapack, plus stan
     * datapacka w repozytorium Papera (obecny / zgodny / włączony).
     */
    private void verifyHeightLimits(World world) {
        int minY = world.getMinHeight();
        int maxYExclusive = world.getMaxHeight();
        int height = maxYExclusive - minY;
        boolean applied = minY == HEIGHT_MIN_Y && height == HEIGHT_TOTAL;

        plugin.getLogger().info("[WorldGenerator] Wysokość świata '" + world.getName() + "': Y "
                + minY + ".." + (maxYExclusive - 1) + " (height=" + height + ") — "
                + (applied
                    ? "limity z datapacka raft-height DZIAŁAJĄ"
                    : "domyślne limity wanilii; datapack deklaruje Y " + HEIGHT_MIN_Y + ".."
                      + (HEIGHT_MIN_Y + HEIGHT_TOTAL - 1)));

        DatapackManager manager = Bukkit.getDatapackManager();
        if (manager == null) return;

        Datapack pack = manager.getPack(HEIGHT_PACK_ID);
        if (pack == null) {
            // Świeżo wgrany plik nie był jeszcze na liście przy starcie serwera
            // (repozytorium buduje się przed włączeniem pluginów) — próbujemy odświeżyć.
            manager.refreshPacks();
            pack = manager.getPack(HEIGHT_PACK_ID);
        }
        if (pack == null) {
            if (heightPackDir != null && Files.isRegularFile(heightPackDir.resolve("pack.mcmeta"))) {
                plugin.getLogger().warning("[WorldGenerator] Datapack " + HEIGHT_PACK_ID
                        + " leży na dysku, ale nie ma go w repozytorium — zadziała od następnego startu serwera.");
            }
            return;
        }

        Datapack.Compatibility compatibility = pack.getCompatibility();
        if (compatibility != Datapack.Compatibility.COMPATIBLE) {
            plugin.getLogger().warning("[WorldGenerator] Datapack " + HEIGHT_PACK_ID
                    + " ma format niezgodny z tą wersją Minecrafta (" + compatibility
                    + ") — popraw min_format/max_format w pack.mcmeta na format danych tej wersji.");
        }
        if (!pack.isEnabled()) {
            plugin.getLogger().warning("[WorldGenerator] Datapack " + HEIGHT_PACK_ID
                    + " jest WYŁĄCZONY — limity wysokości nie działają. Włącz: /datapack enable \""
                    + HEIGHT_PACK_ID + "\"");
            return;
        }
        if (!applied && compatibility == Datapack.Compatibility.COMPATIBLE) {
            plugin.getLogger().warning("[WorldGenerator] Datapack jest aktywny, ale świat '"
                    + world.getName() + "' ma wysokość z chwili utworzenia — zmiana height"
                    + " w dimension type działa tylko dla NOWEGO świata.");
        }
    }

    /**
     * Kopiuje zasób z JAR-a do pliku tylko wtedy, gdy zawartość faktycznie się różni
     * (dzięki temu podbicie formatu przy zmianie wersji Minecrafta samo trafia na serwer).
     *
     * @return true, gdy plik został zapisany
     */
    private boolean writeResourceIfChanged(String resourcePath, Path target) throws IOException {
        byte[] data;
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                plugin.getLogger().warning("[WorldGenerator] Nie znaleziono zasobu: " + resourcePath);
                return false;
            }
            data = is.readAllBytes();
        }
        if (Files.isRegularFile(target) && java.util.Arrays.equals(data, Files.readAllBytes(target))) {
            return false;
        }
        Files.createDirectories(target.getParent());
        Files.write(target, data);
        return true;
    }
}
