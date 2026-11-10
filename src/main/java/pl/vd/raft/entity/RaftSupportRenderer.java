package pl.vd.raft.entity;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Podpory pięter (item {@code wooden_support}) + BELKI PODPIĘTRZA — wizual
 * stojący pod platformą piętra. Świadomie:
 *
 *  - **ZERO kolizji** — podpora i belki są wizualem; nie tworzymy markera ani
 *    shulkera-kolizji (decyzja: 1 encja na element, gracz przechodzi przez
 *    podporę bez zaczepiania),
 *  - **1 encja na słupek** — {@link BlockDisplay} z materiału z configu,
 *    rozciągnięty w pionie do {@code raft-support.height-blocks},
 *  - **1 encja na belkę** (krawędź komórki piętra) — cienki BlockDisplay
 *    rozciągnięty wzdłuż krawędzi; wspólna krawędź dwóch komórek to JEDNA
 *    belka (deduplikacja po kluczu), więc pełna rama podpiętrza kosztuje ~2–3
 *    encje na komórkę piętra.
 *
 * Geometria słupka: węzeł (narożnik 4 komórek) to środek słupka. BlockDisplay
 * rysuje blok OD SWOJEJ POZYCJI ([pos, pos+1], transformacja skaluje od tego
 * min-narożnika), dlatego pozycja displaya to (węzeł − 0.5 w X/Z, y węzła),
 * dzięki czemu słupek płotka (który w modelu stoi na środku bloku) wypada
 * dokładnie w narożniku. Skala Y = height / 1.5 dla płotków/murków (ich model ma
 * 1.5 bloka) albo = height dla zwykłych bloków.
 *
 * Geometria belek (jak w oryginalnym Rafcie — platforma jest „opięta" ramą, a nie
 * wisi w powietrzu): każda komórka piętra dostaje 4 belki na swoich krawędziach,
 * tuż POD sobą (górna płaszczyzna belki styka się ze spodem komórki, czyli z
 * wierzchem słupka). Belki sąsiednich komórek stykają się na wspólnych
 * krawędziach i razem z wierzchem słupka tworzą ciągłą kratownicę, która
 * rozchodzi się od podpory na cztery strony (boki komórek wokół słupka).
 *
 * Cykl życia: encje odtwarza każdy {@code RaftEntity.syncAllVisuals()} (tak jak
 * deski i fallback żagla), a przy rozładowaniu chunku znikają i wracają przy
 * najbliższym sync. {@link #cull()} usuwa je, gdy nikogo nie ma w pobliżu.
 */
public final class RaftSupportRenderer {

    /** Domyślny materiał wizualu podpory (płotek). */
    private static final Material DEFAULT_MATERIAL = Material.OAK_FENCE;
    /** Domyślna wysokość wizualu w blokach (pokój 2 kratki: pokład → piętro). */
    private static final double DEFAULT_HEIGHT = 2.0;
    /** Domyślny materiał belek podpiętrza (pełny blok — inaczej wygląd się rozjedzie). */
    private static final Material DEFAULT_BEAM_MATERIAL = Material.DARK_OAK_PLANKS;

    /** Wysokość modelu w blokach dla materiałów typu płotek/murek. */
    private static final double FENCE_MODEL_HEIGHT = 1.5;

    /** Grubość belki podpiętrza (oś Y, w blokach) — belka „wisi" pod komórką. */
    public static final double BEAM_HEIGHT = 0.25;
    /** Szerokość belki w poprzek krawędzi (osie X/Z, w blokach). */
    public static final double BEAM_WIDTH = 0.25;

    // ── Konfiguracja (config.yml: raft-support.*; /def reload bez rekompilacji) ──

    private static Material material = DEFAULT_MATERIAL;
    private static double heightBlocks = DEFAULT_HEIGHT;
    private static int customModelData = 0;
    private static Material modelItem = DEFAULT_MATERIAL;
    /** Generacja konfiguracji — po zmianie stare displaye są odtwarzane od nowa. */
    private static long configGeneration = 1;

    private static Material beamMaterial = DEFAULT_BEAM_MATERIAL;
    private static boolean beamsEnabled = true;
    /** Generacja konfiguracji BELEK (osobno od słupków — zmiana odtwarza tylko belki). */
    private static long beamGeneration = 1;

    /**
     * Wczytuje ustawienia wizualu podpory. Wołane przy starcie pluginu i po
     * /def reload (RaftPlugin.reloadRaftConfig).
     */
    public static void configure(Material material, double heightBlocks,
                                 int customModelData, Material modelItem) {
        Material newMaterial = material != null && material.isBlock() ? material : DEFAULT_MATERIAL;
        double newHeight = heightBlocks > 0 ? heightBlocks : DEFAULT_HEIGHT;
        Material newItem = modelItem != null ? modelItem : newMaterial;
        if (newMaterial == RaftSupportRenderer.material
                && Math.abs(newHeight - RaftSupportRenderer.heightBlocks) < 1e-6
                && customModelData == RaftSupportRenderer.customModelData
                && newItem == RaftSupportRenderer.modelItem) {
            return; // bez zmian — nie ruszamy encji
        }
        RaftSupportRenderer.material = newMaterial;
        RaftSupportRenderer.heightBlocks = newHeight;
        RaftSupportRenderer.customModelData = Math.max(0, customModelData);
        RaftSupportRenderer.modelItem = newItem;
        configGeneration++;
    }

    /**
     * Wczytuje ustawienia BELEK podpiętrza (config: raft-support.beams +
     * beam-material). Niepoprawny/nieistniejący materiał → domyślny pełny blok.
     */
    public static void configureBeams(Material material, boolean enabled) {
        Material newMaterial = material != null && material.isBlock()
                ? material : DEFAULT_BEAM_MATERIAL;
        if (newMaterial == beamMaterial && enabled == beamsEnabled) return;
        beamMaterial = newMaterial;
        beamsEnabled = enabled;
        beamGeneration++;
    }

    /** Czy belki podpiętrza są włączone w configu (raft-support.beams). */
    public static boolean beamsEnabled() {
        return beamsEnabled;
    }

    /** Materiał belek podpiętrza (config: raft-support.beam-material). */
    public static Material beamMaterial() {
        return beamMaterial;
    }

    /** Wysokość wizualu w blokach (do zasad pięter w RaftEntity/RaftBuildListener). */
    public static double visualHeight() {
        return heightBlocks;
    }

    /** Czy podpora używa custom modelu (ItemDisplay) zamiast bloku. */
    public static boolean usesCustomModel() {
        return customModelData > 0;
    }

    /** Materiał wizualu — podgląd-duch stawiania pokazuje ten sam blok. */
    public static Material visualMaterial() {
        return material;
    }

    // ── Belki podpiętrza: czysta geometria (testowalna bez serwera) ──

    /**
     * Belka podpiętrza — odcinek o długości jednej komórki leżący na krawędzi
     * komórki piętra (siatka 0.5). {@code alongX} = belka biegnie w X (wtedy
     * {@code line} to Z krawędzi, a {@code start} to minimalne X); inaczej biegnie
     * w Z ({@code line} = X krawędzi, {@code start} = minimalne Z). {@code y} to
     * lokalne Y MIN-narożnika belki (spód komórki piętra − grubość belki).
     */
    public record Beam(boolean alongX, double line, double start, double y) {

        /** Klucz krawędzi — dwie komórki dzielące krawędź dają JEDNĄ belkę. */
        public String key() {
            return (alongX ? "X|" : "Z|") + quantize(line) + "|" + quantize(start);
        }

        /** Lokalne X min-narożnika displaya (belka wystaje pół szerokości na krawędź). */
        public double minX() {
            return alongX ? start : line - BEAM_WIDTH / 2.0;
        }

        /** Lokalne Z min-narożnika displaya. */
        public double minZ() {
            return alongX ? line - BEAM_WIDTH / 2.0 : start;
        }

        private static long quantize(double v) {
            return Math.round(v * 2.0); // siatka 0.5 — klucz odporny na szum zmiennoprzecinkowy
        }
    }

    /**
     * Krawędzie komórek PIĘTRA → unikalne belki (rama podpiętrza). Komórki
     * nie-pięter (pokład, ściany, skrzynie) i komórki poza poziomem piętra są
     * pomijane; wspólne krawędzie dwóch komórek trafiają do mapy raz.
     */
    public static Map<String, Beam> beamsFor(List<Vector> cellOffsets,
                                             Predicate<Vector> isFloorCell) {
        Map<String, Beam> out = new HashMap<>();
        double level = RaftEntity.floorLevel();
        if (level <= 0 || cellOffsets == null) return out;
        for (Vector cell : cellOffsets) {
            if (cell == null || Math.abs(cell.getY() - level) > 0.01) continue;
            if (!isFloorCell.test(cell)) continue;
            addCellBeams(out, cell);
        }
        return out;
    }

    /** Cztery krawędzie jednej komórki piętra jako belki (klucze deduplikują sąsiadów). */
    private static void addCellBeams(Map<String, Beam> out, Vector cell) {
        double y = cell.getY() - 0.5 - BEAM_HEIGHT; // belka tuż pod spodem komórki
        double x0 = cell.getX() - 0.5;
        double z0 = cell.getZ() - 0.5;
        double x1 = cell.getX() + 0.5;
        double z1 = cell.getZ() + 0.5;
        put(out, new Beam(true, z0, x0, y));  // krawędź północna (biegnie w X)
        put(out, new Beam(true, z1, x0, y));  // krawędź południowa
        put(out, new Beam(false, x0, z0, y)); // krawędź zachodnia (biegnie w Z)
        put(out, new Beam(false, x1, z0, y)); // krawędź wschodnia
    }

    private static void put(Map<String, Beam> out, Beam beam) {
        out.putIfAbsent(beam.key(), beam);
    }

    // ── Stan per tratwa ──

    private final RaftEntity raft;
    private final java.util.UUID raftId;
    /** keyOf(węzeł) -> display podpory */
    private final Map<String, Display> displays = new HashMap<>();
    /** key krawędzi -> display belki podpiętrza */
    private final Map<String, Display> beams = new HashMap<>();
    /** Generacja konfiguracji, z jaką powstały obecne displaye. */
    private long generation = configGeneration;
    /** Generacja konfiguracji belek, z jaką powstały obecne belki. */
    private long beamGenerationSeen = beamGeneration;

    public RaftSupportRenderer(RaftEntity raft, java.util.UUID raftId) {
        this.raft = raft;
        this.raftId = raftId;
    }

    // ─────────────────────── SYNC ─────────────────────────────────

    /**
     * Doprowadza wizual podpór i belek do stanu tratwy: tworzy brakujące
     * displaye, przesuwa istniejące i usuwa te po zdjętych podporach/komórkach.
     */
    public void sync() {
        World world = raft.getState().getWorld();
        if (world == null) return;

        // Zmiana config.yml (materiał/wysokość/model) — odtwórz wszystkie displaye
        if (generation != configGeneration) {
            generation = configGeneration;
            removeAll(displays);
        }
        if (beamGenerationSeen != beamGeneration) {
            beamGenerationSeen = beamGeneration;
            removeAll(beams);
        }

        Vector pos = raft.getState().getPosition();
        syncColumns(world, pos);
        syncBeams(world, pos);
    }

    /** Słupki podpór — jedna encja na węzeł. */
    private void syncColumns(World world, Vector pos) {
        Set<String> needed = new HashSet<>();

        for (Vector node : raft.getSupports()) {
            String key = RaftEntity.keyOf(node);
            needed.add(key);

            // BlockDisplay rysuje blok [pos, pos+1] — środek słupka (węzeł) ma
            // wypaść na narożniku, więc pozycja to węzeł − 0.5 w X/Z.
            double x = pos.getX() + node.getX() - 0.5;
            double y = pos.getY() + node.getY();
            double z = pos.getZ() + node.getZ() - 0.5;

            Display display = displays.get(key);
            if (!alive(display, world)) {
                display = spawnColumn(world, x, y, z);
                displays.put(key, display);
            } else {
                RaftEntitySync.moveSmoothly(display, x, y, z);
            }
        }

        removeStale(displays, needed);
    }

    /**
     * Belki podpiętrza — rama pod komórkami PIĘTRA. Rysujemy ją dla każdej
     * komórki piętra (każda z nich stoi wokół swojej podpory — tak pilnuje
     * reguła pięter), więc słupki i belki tworzą razem jedną kratownicę:
     * wierzch słupka spotyka się z czterema belkami rozchodzącymi się na boki.
     */
    private void syncBeams(World world, Vector pos) {
        Map<String, Beam> wanted = beamsEnabled
                ? beamsFor(raft.getBlockOffsets(),
                        cell -> raft.getCellType(cell) == RaftEntity.CellType.FLOOR)
                : Map.of();

        for (Map.Entry<String, Beam> entry : wanted.entrySet()) {
            Beam beam = entry.getValue();
            double x = pos.getX() + beam.minX();
            double y = pos.getY() + beam.y();
            double z = pos.getZ() + beam.minZ();

            Display display = beams.get(entry.getKey());
            if (!alive(display, world)) {
                display = spawnBeam(world, x, y, z, beam.alongX());
                beams.put(entry.getKey(), display);
            } else {
                RaftEntitySync.moveSmoothly(display, x, y, z);
            }
        }

        removeStale(beams, wanted.keySet());
    }

    /** Usuwa wizual (podpora zdjęta / tratwa zniszczona). */
    public void cleanup() {
        removeAll(displays);
        removeAll(beams);
    }

    /**
     * Chowaz wizual podpór i belek, gdy nikogo nie ma w pobliżu (czysty wizual —
     * odtworzy go najbliższy sync, tak jak deski tratwy).
     */
    public void cull() {
        removeAll(displays);
        removeAll(beams);
    }

    private static void removeAll(Map<String, Display> map) {
        for (Display display : map.values()) {
            if (display != null && !display.isDead()) display.remove();
        }
        map.clear();
    }

    /** Usuwa displaye, których nie ma już na liście potrzebnych. */
    private static void removeStale(Map<String, Display> map, Set<String> needed) {
        Iterator<Map.Entry<String, Display>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Display> entry = it.next();
            if (needed.contains(entry.getKey())) continue;
            Display display = entry.getValue();
            if (display != null && !display.isDead()) display.remove();
            it.remove();
        }
    }

    // ─────────────────────── SPAWN ────────────────────────────────

    private Location location(World world, double x, double y, double z) {
        return new Location(world, x, y, z);
    }

    /** Słupek podpory (BlockDisplay z materiału z configu lub ItemDisplay z modelem). */
    private Display spawnColumn(World world, double x, double y, double z) {
        Location loc = location(world, x, y, z);
        if (usesCustomModel()) {
            return world.spawn(loc, ItemDisplay.class, d -> {
                ItemStack item = new ItemStack(modelItem, 1);
                var meta = item.getItemMeta();
                if (meta != null) {
                    meta.setCustomModelData(customModelData);
                    item.setItemMeta(meta);
                }
                d.setItemStack(item);
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                d.setTransformation(modelTransform());
                d.setBillboard(Display.Billboard.FIXED);
                applyCommonFlags(d);
            });
        }
        return world.spawn(loc, BlockDisplay.class, d -> {
            d.setBlock(material.createBlockData());
            d.setTransformation(blockTransform());
            d.setBillboard(Display.Billboard.FIXED);
            applyCommonFlags(d);
        });
    }

    /** Belka podpiętrza — cienki BlockDisplay rozciągnięty wzdłuż krawędzi komórki. */
    private Display spawnBeam(World world, double x, double y, double z, boolean alongX) {
        return world.spawn(location(world, x, y, z), BlockDisplay.class, d -> {
            d.setBlock(beamMaterial.createBlockData());
            d.setTransformation(beamTransform(alongX));
            d.setBillboard(Display.Billboard.FIXED);
            applyCommonFlags(d);
        });
    }

    /**
     * Transformacja płotka/murka: rozciągnięcie w pionie od PODSTAWY bloku
     * (model zaczyna się w min-narożniku, więc skala rośnie w górę). Model
     * płotka ma 1.5 bloka — przy height=2 skala Y = 1.333, dzięki czemu słupek
     * sięga dokładnie od pokładu (y+0.5) do spodu piętra.
     */
    private static Transformation blockTransform() {
        double modelHeight = FENCE_MODEL_HEIGHT;
        String name = material.name();
        if (!(name.endsWith("_FENCE") || name.endsWith("_WALL"))) {
            modelHeight = 1.0; // zwykły blok/kloc — model ma pełną wysokość
        }
        float scaleY = (float) (heightBlocks / modelHeight);
        return new Transformation(
                new Vector3f(0f, 0f, 0f), new Quaternionf(),
                new Vector3f(1f, scaleY, 1f), new Quaternionf());
    }

    /**
     * Transformacja belki: pełny blok spłaszczony do grubości {@link #BEAM_HEIGHT}
     * (oś Y) i szerokości {@link #BEAM_WIDTH} w poprzek krawędzi, długość 1 blok
     * wzdłuż krawędzi. Skala idzie od min-narożnika modelu (czyli od pozycji
     * displaya), dlatego belka rośnie w +X/+Z i w górę — dokładnie tam, gdzie ma.
     */
    private static Transformation beamTransform(boolean alongX) {
        float h = (float) BEAM_HEIGHT;
        float w = (float) BEAM_WIDTH;
        // Płotek/murek ma model 1.5 bloka wysokości — kompensacja jak przy słupku.
        String name = beamMaterial.name();
        float hScale = (name.endsWith("_FENCE") || name.endsWith("_WALL"))
                ? (float) (BEAM_HEIGHT / FENCE_MODEL_HEIGHT) : h;
        return new Transformation(
                new Vector3f(0f, 0f, 0f), new Quaternionf(),
                alongX ? new Vector3f(1f, hScale, w) : new Vector3f(w, hScale, 1f),
                new Quaternionf());
    }

    /**
     * Transformacja custom modelu: model z Blockbencha liczony od ŚRODKA
     * podstawy (origin = 0,0,0 w środku kratki, wysokość 1 blok), dlatego
     * przesuwamy go w górę o height/2 i skalujemy w pionie.
     */
    private static Transformation modelTransform() {
        float h = (float) heightBlocks;
        return new Transformation(
                new Vector3f(0f, h / 2f, 0f), new Quaternionf(),
                new Vector3f(1f, h, 1f), new Quaternionf());
    }

    private void applyCommonFlags(Display d) {
        d.setGravity(false);
        d.setInvulnerable(true);
        d.setSilent(true);
        d.setPersistent(false); // znika przy rozładowaniu chunku — odtworzy go sync
        d.addScoreboardTag(RaftEntity.TAG_VISUAL);
        d.addScoreboardTag("raft_" + raftId);
    }

    /** Czy wizual istnieje i jest w tym samym świecie. */
    private static boolean alive(Entity entity, World world) {
        return entity != null && !entity.isDead() && entity.isValid()
                && world.equals(entity.getWorld());
    }
}
