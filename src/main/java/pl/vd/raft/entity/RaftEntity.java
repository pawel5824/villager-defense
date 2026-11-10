package pl.vd.raft.entity;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Shulker;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import pl.vd.raft.physics.RaftPhysicsEngine;

import java.util.*;

/**
 * RaftEntity - tratwa ktora pływa i dryfuje z pradem wody.
 *
 * Architektura kolizji = DOKŁADNIE jak w BlockShips (sprawdzone w ich źródle):
 *
 *  KAZDA komorka tratwy ma:
 *   1) BlockDisplay (widoczna deska 3D) — sub-pixel, plynny ruch
 *   2) ArmorStand z marker(true) — niewidzialny "carrier" BEZ wlasnej kolizji
 *   3) Shulker jako PASAZER tego markera — niewidzialny, collidable, 1×1×1.
 *
 *  DLACZEGO pasazer?: pasazer NIE MOZE byc odepchniety przez gracza (jego
 *  pozycja jest zablokowana do pojazdu). Wolny shulker gracz moze odepchac
 *  (mob vs gracz pchaja sie nawzajem) — przez to przechodzil przez tratwe.
 *  Pasazer-shulker jest nie do odepchniecia = twarda, stabilna podloga.
 *
 *  Marker ma rozmiar 0 → pasazer siedzi DOKLADNIE na pozycji markera (bez
 *  unoszenia). UWAGA (v8): AABB zamknietego shulkera to 1×1×1 liczone OD
 *  POZYCJI W GÓRĘ (pozycja = spód hitboxu), wiec pare (marker+shulker)
 *  kotwiczymy 0.5 bloka POD srodkiem komorki (COLLIDER_Y_OFFSET) — wtedy
 *  box shulkera pokrywa sie dokladnie z widoczna deska (BlockDisplay).
 *
 *  Ruch: teleport CARRIERA co tick (pasazer podaza automatycznie, server-side
 *  i client-side). Shulkera NIE ruszamy bezposrednio (jak ostrzega BlockShips:
 *  powoduje jitter/snapping).
 */
public class RaftEntity {

    /** Tagi scoreboard do identyfikacji entity tratwy (cleanup, ochrona). */
    public static final String TAG_COLLIDER = "raft_collider";
    public static final String TAG_VISUAL = "raft_visual";

    /**
     * Znacznik wersji — widoczny w /raft debug. Dzieki niemu od razu widac,
     * ktory jar faktycznie dziala na serwerze.
     */
    public static final String BUILD = "marker+pasażer v46 — LOD + persistencja + typy komórek + cienkie ściany + GUI młota + podgląd-duch stawiania + kolizja ześlizgowa (kąt prądu) + próbki na cały obrys pokładu w DWÓCH warstwach (woda + ląd równo z wodą) + backstop na wjazd kadłuba w ląd + konfigurowalna kolizja (raft-collision) + pole śmieci 13–28 sztuk 40–70, rozrzut ±14, spokojne pływanie (raft-debris) + wizual zawartości sieci: ikona + licznik [n] + pickup przy pełnym eq = drop terakoty jak z Q z etykietą [nx] nazwa + skrzynie stają NA pokładzie (komórka nad deską) + budowa i potwierdzenie z PPM w kolizję/powietrze (fix isCancelled) + hak odczepiany od niewidzialnych kolizji tratwy (swobodny lot do wody) + skrzynie: waniliowe klikanie w GUI (kursor, drag, shift-klik) i lustro stanu GUI do skrzyni co 2 ticki + jedna skrzynka = jeden współdzielony kontener GUI (bez duplikacji przy dwóch widzach) + urządzenia: zapis obrotu (rotation) w devices.yml, v2 (wcześniej yaw=0, więc po restarcie stały na South) + jawnie wybrany kit w players.yml (settings.chosen-kit) + obrót budowli: kółko myszy w trybie budowy (góra = 90° w prawo, dół = 90° w lewo), presety kierunku 1–4 (N/E/S/W), Shift+kółko = kierunek z patrzenia, Alt+kółko z modem (bit2 w raft:key_state), domyślny kierunek z patrzenia, komórki obrotowe (skrzynie/schody/ściany) trzymają kierunek w raft-saves.yml + drewniane schody (kategoria „Inne” w GUI młota): przedmiot wooden_stairs (OAK_STAIRS, CMD 7038) stawiany na pokładzie, obrót kierunkiem jak skrzynie + podpora piętra wooden_support (CMD 7039): słupek 1 encja / ZERO kolizji, stawiany w narożniku 4 komórek, trzyma piętro (platforma TYLKO w 4 komórkach wokół słupka, węzły co 2 kratki), zapis węzłów w raft-saves.yml (supports) + belki podpiętrza: rama z belek (1 cienki BlockDisplay na krawędź komórki piętra, wspólne krawędzie deduplikowane) tuż pod platformą, łączy wierzch słupka z 4 komórkami wokół podpory (config raft-support.beams/beam-material) — platforma nie wygląda na wiszącą w powietrzu + rozbiórka podpory: zdjęcie słupka siekierą ZAWALA piętro, które trzymał (komórki bez podparcia + ich belki + to, co na nich stało — zawartość skrzyń wpada do wody), więc w powietrzu nic nie zostaje (RaftEntity.pruneUnsupportedCells) + sprzątanie „nic nie wisi w powietrzu” też przy ZWYKŁEJ rozbiórce komórki siekierą (skrzynia nie zostaje na pustce po rozbiciu deski pod nią) + runięcie z OPÓŹNIENIEM (config raft-support.collapse-delay-ticks, domyślnie 12 ticków = 0,6 s) i ODZYSKIWANIEM MATERIAŁU: każda zawalona komórka wypada do wody jako przedmiot (dźwięk + cząsteczki z jej materiału; podstawa tratwy i pokład zostają) + „Podłoga” (wooden_floor, CMD 7040, kategoria „Inne” w GUI młota): przedmiot PIĘTROWY — platformę piętra stawiasz nim TYLKO na poziomie piętra (celuj w słupek podpory albo w gotowe piętro), a na wodzie jako pokład tratwy nie postawisz go w ogóle (podgląd-duch się nie pokazuje: RaftBuildListener.isDeckBuild); drewniany/kamienny fundament dalej działa i jako pokład, i jako piętro + przepis na Podłogę w GUI /wytwarzanie (kategoria „Materiały / Zasoby”: 4× deska + 1× lina, jak Żagiel — gracz zdobywa piętro bez komendy)";

    /**
     * Typ komórki budowlanej tratwy. Rozróżniamy je, bo rządzą się innymi
     * zasadami:
     *  - RAFT   — podstawowa tratwa (2×2), nie do usunięcia (to „korzeń" tratwy),
     *  - FLOOR  — podłoga/podkład (wooden_foundation / stone_foundation) — rozbudowa pokładu,
     *  - WALL   — płaska ściana 1 blok nad pokładem (wooden_wall),
     *  - STAIRS — schody z kierunkiem (wooden_stairs),
     *  - NET    — sieć zbierająca (collection_net): łapie pływające przedmioty,
     *             magazyn maks. NET_CAPACITY sztuk.
     */
    public enum CellType {
        RAFT("Tratwa"),
        FLOOR("Podłoga"),
        WALL("Ściana"),
        STAIRS("Schody"),
        NET("Sieć"),
        CHEST_18("Mała skrzynka"),
        CHEST_36("Skrzynia"),
        CHEST_54("Duża skrzynia");

        private final String label;

        CellType(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        /** Czy komórka jest skrzynką z własnym GUI (pojemność slotów). */
        public boolean isChest() {
            return this == CHEST_18 || this == CHEST_36 || this == CHEST_54;
        }

        /** Liczba slotów skrzynki (18/36/54); 0 dla komórek nieskrzynkowych. */
        public int chestSlots() {
            return switch (this) {
                case CHEST_18 -> 18;
                case CHEST_36 -> 36;
                case CHEST_54 -> 54;
                default -> 0;
            };
        }
    }

    /**
     * Maksymalna liczba komórek na jedną tratwę (config: raft-max-cells-per-raft).
     * Każda komórka = 3 encje (BlockDisplay + marker + shulker), więc limit
     * chroni przed tratwą o tysiącach encji. Ustawiany w RaftPlugin.onEnable.
     */
    public static int MAX_CELLS = 150;

    /** Maksymalna pojemność jednej sieci (CellType.NET) — w sztukach łącznie. */
    public static final int NET_CAPACITY = 10;

    /**
     * Kształt komórki: grubość (bloki) + wysokość (bloki). Pełna komórka = (1, 1).
     * Grubość < 1 = płaska ściana (BlockDisplay spłaszczony/rozciągnięty);
     * wysokość > 1 = wysoka ściana z ukrytą parą kolizji wyżej (shulker jest
     * tylko 1×1×1, więc 2 bloki wysokości = 2 pary marker+shulker).
     */
    public record CellShape(float thickness, int height) {
        public static final CellShape FULL = new CellShape(1f, 1);
    }

    /** Domyślna grubość cienkiej ściany (Faza 4): 0.125 bloka — „krawędź deski". */
    public static final float THIN_WALL_THICKNESS = 0.125f;
    /** Domyślna wysokość wysokiej ściany: 2 bloki. */
    public static final int TALL_WALL_HEIGHT = 2;
    /** Maksymalna wysokość ściany po piętrowaniu (wooden_wall na górze ściany). */
    public static final int MAX_WALL_HEIGHT = 4;

    /**
     * Pionowe zakotwiczenie pary kolizji (marker + shulker-pasażer) względem
     * środka komórki/deski.
     *
     * Zamknięty shulker (peek 0, attach DOWN) ma AABB 1×1×1 liczoną OD SWOJEJ
     * POZYCJI W GÓRĘ (sprawdzone w bytecode 26.2, Shulker.makeBoundingBox →
     * getProgressDeltaAabb: box (−0.5,0,−0.5)-(0.5,1,0.5) przesunięty do pozycji;
     * przy peek 0 zero ekspansji). Pozycja entity to SPÓD hitboxu. Deska
     * (BlockDisplay) zajmuje sześcian [środek−0.5, środek+0.5], więc żeby box
     * shulkera pokrywał się DOKŁADNIE z deską (był „na środku bloku"), marker
     * i shulker muszą stać 0.5 bloka POD środkiem komórki.
     *
     * Poprzednio kotwiczono je w środku komórki → box wystawał pół bloka NAD
     * deskę (gracz stał „w powietrzu" nad pokładem, kolizja nie pokrywała deski).
     */
    private static final double COLLIDER_Y_OFFSET = -0.5;

    /** Jak głęboko (w blokach) tratwa ma siedzieć w wodzie: 0.6 = 6/10 bloka pod powierzchnią. */
    public static final double SUBMERGED_DEPTH = 0.6;

    /**
     * Lokalna wysokość komponentów masztu/żagla nad środkiem tratwy.
     *
     * Pokład = 0.5: kostka BlockDisplay deski i box shulkera-kolizji pokrywają
     * [środek−0.5, środek+0.5], więc chodzi się po Y = 0.5 i TAM stoi podstawa
     * masztu. Model żagla u gracza z modem kotwiczy origin grupy `zagiel`
     * (8,0,8 w pikselach) dokładnie na tej pozycji, a pivot animacji rozwijania
     * jest „przy pokładzie” (`zagiel.anim.json`) — kotwica wyżej wieszałaby
     * maszt w powietrzu. Wcześniej było 1.0 (pół bloka NAD pokładem) i tak samo
     * rysował mesh; poprawione 2026-10-02 razem z fallbackiem serwerowym.
     *
     * Komponent SAIL to nośnik X/Z komórki żagla (`getSailCell`) i metadana
     * zapisu — faktyczną geometrię płótna rysują mesh moda / fallback serwera.
     */
    private static final double MAST_BASE_Y = 0.5;
    private static final double SAIL_PLANE_Y = 1.0;

    /**
     * Pozycja ŚRODKA tratwy względem powierzchni wody (FLOAT_OFFSET ujemny =
     * środek poniżej linii wody). Deska ma 1 blok wysokości ([środek−0.5,
     * środek+0.5]), więc środek = powierzchnia − (SUBMERGED_DEPTH − 0.5) = −0.1
     * daje dokładnie 6/10 bloka zanurzonego: spód desek 0.6 pod linią wody,
     * pokład 0.4 nad nią.
     */
    public static final double FLOAT_OFFSET = 0.5 - SUBMERGED_DEPTH;

    // ── Faza 1 — LOD: tryb tickowania zależny od obecności graczy ────────────
    // Odległości mierzymy do OBRYSU pokładu (AABB komórek), a nie do środka —
    // dzięki temu gracz stojący na drugim końcu dużej tratwy nadal jest „blisko".

    /** Pełny tick (20 Hz): gracz na pokładzie lub w tym promieniu od obrysu tratwy. */
    public static final double FULL_TICK_RANGE = 20.0;
    /** Obserwacja z daleka: w tym promieniu tratwa dryfuje, ale encje sync co FAR_SYNC_INTERVAL ticków. */
    public static final double LOD_OBSERVE_RANGE = 50.0;
    /** Co ile ticków synchronizujemy encje w trybie dalekim (4 = 5 Hz). */
    public static final int FAR_SYNC_INTERVAL = 4;
    /** Co ile ticków w trybie FROZEN dogrywamy brakujące encje (bez ruszania tratwy). */
    private static final int FROZEN_MAINTENANCE_INTERVAL = 40;

    // ── Faza 2 — wygaszanie desek BlockDisplay (culling) ──────────────────────
    // Deski to czysty wizual (1 encja na komórkę). Trzymamy je w świecie tylko,
    // gdy ktoś może je widzieć; poza zasięgiem znikają i wracają przy sync().
    // Histereza (spawn niżej niż cull) chroni przed migotaniem na granicy.

    /** W tym promieniu od obrysu tratwy deski są (od)tworzone. */
    public static final double DISPLAY_SPAWN_RANGE = 128.0;
    /** Powyżej tego promienia deski są wygaszane (usuwane ze świata). */
    public static final double DISPLAY_CULL_RANGE = 160.0;

    private static final double DISPLAY_SPAWN_SQ = DISPLAY_SPAWN_RANGE * DISPLAY_SPAWN_RANGE;
    private static final double DISPLAY_CULL_SQ = DISPLAY_CULL_RANGE * DISPLAY_CULL_RANGE;

    /** Wyjściowa tratwa: 2×2 deski dębowe (jak w grze Raft). */
    private static final Vector[] INITIAL_BLOCKS = {
            new Vector(-0.5, 0, -0.5),
            new Vector(0.5, 0, -0.5),
            new Vector(-0.5, 0, 0.5),
            new Vector(0.5, 0, 0.5)
    };

    private final UUID raftId;
    private final UUID owner; // wlasciciel tratwy (null = tratwa systemowa)
    private final World world;

    // ─── Stan + fizyka ───
    private final RaftState state;
    private final RaftPhysicsEngine physicsEngine;
    private final RaftMovementController movementController;

    // ─── Komorki tratwy ───
    private final List<Vector> blockOffsets = new ArrayList<>();
    private final Map<String, Material> cellMaterials = new HashMap<>();
    /** key komorki -> typ komórki (tratwa/podłoga/ściana/schody) */
    private final Map<String, CellType> cellTypes = new HashMap<>();
    /** key komorki -> kierunek (tylko schody; NORTH dla reszty) */
    private final Map<String, BlockFace> cellFacings = new HashMap<>();
    /** key komorki -> gotowy BlockData (cache — bez alokacji co tick w syncCells) */
    private final Map<String, org.bukkit.block.data.BlockData> cellDataCache = new HashMap<>();
    /** key komorki -> uszkodzenie (liczba uderzeń siekierą; przetrwa restart w zapisie). */
    private final Map<String, Integer> cellDamage = new HashMap<>();
    /** key komorki (tylko sieci NET) -> złapane przedmioty (każdy wpis = stack tego samego typu). */
    private final Map<String, List<ItemStack>> cellContents = new HashMap<>();
    /** key komorki -> kształt (grubość/wysokość); brak = pełna komórka (1, 1) */
    private final Map<String, CellShape> cellShapes = new HashMap<>();
    /** key komorki -> transformacja displaya (tylko cienkie ściany; reszta = pełny blok) */
    private final Map<String, Transformation> cellTransformCache = new HashMap<>();
    /** key = keyOf(pozycji pary) -> ukryta para kolizji wysokiej ściany */
    private final Map<String, HiddenPair> hiddenPairs = new HashMap<>();
    /** key komorki -> shulker (kolizja, pasazer markera) */
    private final Map<String, Shulker> colliders = new HashMap<>();
    /** key komorki -> marker armor stand (carrier — rusza sie, shulker podaza) */
    private final Map<String, ArmorStand> carriers = new HashMap<>();
    private final RaftVisualRenderer visualRenderer;
    /** Fallback żagla dla graczy BEZ moda (maszt ItemDisplay + płótno BlockDisplay). */
    private final RaftSailFallbackRenderer sailFallbackRenderer;
    /** Podpory pięter (item wooden_support): 1 encja na podporę, ZERO kolizji. */
    private final RaftSupportRenderer supportRenderer;
    /**
     * Węzły podpór pięter — narożniki 4 komórek (lokalny offset, Y = podstawa
     * słupka na pokładzie = 0.5). Trzymane OSOBNO od komórek, bo podpora nie
     * zajmuje komórki siatki: stoi dokładnie na jej narożniku (patrz
     * {@link #addSupport}).
     */
    private final List<Vector> supports = new ArrayList<>();
    /** Maksymalna liczba podpór na tratwę (2× komórki — podpory są tanie). */
    public static int MAX_SUPPORTS = 300;
    /**
     * Wysokość podpory w blokach (config: raft-support.height-blocks). Ustawiane
     * razem z wizualem w RaftPlugin.reloadRaftConfig — od tego zależy, czy
     * podpora „dosięga" piętra (patrz {@link #isCellSupported}).
     */
    public static double SUPPORT_HEIGHT = 2.0;
    /** Lokalne Y podstawy słupka = wierzch pokładu (komórka pokładu to [-0.5, 0.5]). */
    public static final double SUPPORT_BASE_Y = 0.5;

    private boolean isMoving = true;

    /** Przesuniecie poziome tratwy w ostatnim ticku (do /raft debug). */
    private double lastTickDisplacement = 0.0;

    // ── Faza 1 — stan LOD ───────────────────────────────────────────────────

    /** Tryby tickowania tratwy wg obecności graczy (patrz tick()). */
    public enum TickMode { FULL, FAR, FROZEN }

    /** Ostatnio zmierzony tryb LOD (do /raft debug i logów). */
    private TickMode tickMode = TickMode.FULL;
    /** Poprzedni tryb — do wykrywania przejść (reset kadencji, rekalibracja przenośnika). */
    private TickMode lastTickMode = TickMode.FULL;
    /** Ticki od ostatniego sync encji w trybie FAR (kadencja 5 Hz). */
    private int ticksSinceFarSync = 0;
    /** Ticki spędzone w trybie FROZEN (dogrywanie encji co FROZEN_MAINTENANCE_INTERVAL). */
    private int frozenTicks = 0;
    /** Kwadrat odległości najbliższego gracza od obrysu tratwy (ostatni pomiar). */
    private double nearestPlayerDistanceSq = Double.MAX_VALUE;
    /** Czy deski BlockDisplay są aktualnie w świecie (tryb FROZEN — culling). */
    private boolean frozenDisplaysPresent = true;
    /** Obrys pokładu z ostatniego ticka — dla filtra pakietów LOD (bez ponownych obliczeń). */
    private double[] cachedDeckBoundsXZ = null;

    // ───────────────────────── KONSTRUKTOR ─────────────────────────

    public RaftEntity(World world, Location location, Vector direction) {
        this(world, location, direction, null);
    }

    public RaftEntity(World world, Location location, Vector direction, UUID owner) {
        this(world, location, direction, owner, null);
    }

    /**
     * Faza 3 — odtworzenie zapisanej tratwy: ten sam raftId (adopcja kolizji
     * przetrwałych w chunkach), komórki i komponenty z zapisu (RaftPersistence).
     */
    public RaftEntity(World world, Location location, Vector direction, UUID owner,
                      RaftPersistence.SaveData save) {
        this(world, location, direction, owner,
                save != null ? save.raftId : null,
                save != null ? save.cells : null,
                save != null ? save.components : null,
                save != null ? save.netContents : null,
                save != null ? save.chestContents : null,
                save != null && save.sailOpen,
                save != null ? save.supports : null);
    }

    private RaftEntity(World world, Location location, Vector direction, UUID owner,
                       UUID raftId, List<RaftPersistence.Cell> cells,
                       List<RaftPersistence.ComponentData> components,
                       Map<String, List<String>> netContents,
                       Map<String, List<String>> chestContents, boolean sailOpen,
                       List<Vector> savedSupports) {
        this.raftId = raftId != null ? raftId : UUID.randomUUID();
        this.owner = owner;
        this.world = world;
        this.state = new RaftState(world, location);
        this.physicsEngine = new RaftPhysicsEngine(state);

        // Prąd wody: kierunek * bazowa predkosc (reszte ustawia RaftManager co tick)
        if (direction != null && direction.lengthSquared() > 0) {
            physicsEngine.setGlobalCurrent(direction.clone().normalize().multiply(1.0));
        }

        this.movementController = new RaftMovementController(this);
        this.visualRenderer = new RaftVisualRenderer(state, this.raftId);
        this.sailFallbackRenderer = new RaftSailFallbackRenderer(this, this.raftId);
        this.supportRenderer = new RaftSupportRenderer(this, this.raftId);

        if (cells == null || cells.isEmpty()) {
            // Nowa tratwa: początkowe komorki 2×2
            for (Vector offset : INITIAL_BLOCKS) {
                addInitialCell(offset);
            }
        } else {
            // Przywracana tratwa: odtwórz dokładnie zapisane komórki (materiały,
            // typy i kierunki schodów). Stare zapisy bez typu → FLOOR.
            for (RaftPersistence.Cell cell : cells) {
                Vector offset = new Vector(cell.x, cell.y, cell.z);
                blockOffsets.add(offset);
                Material mat = cell.material == null ? Material.OAK_PLANKS
                        : Material.matchMaterial(cell.material);
                String key = keyOf(offset);
                mat = mat != null ? mat : Material.OAK_PLANKS;
                cellMaterials.put(key, mat);
                CellType type = parseCellType(cell.type);
                cellTypes.put(key, type);
                BlockFace facing = null;
                if (isOrientable(type) && cell.facing != null) {
                    try {
                        facing = BlockFace.valueOf(cell.facing);
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                if (facing != null) cellFacings.put(key, facing);
                cellDataCache.put(key, dataFor(mat, type, facing));

                // Uszkodzenie komórki (uderzenia siekierą) z zapisu — brak pola = 0
                int dmg = cell.damage != null ? parseInt(cell.damage, 0) : 0;
                if (dmg > 0) cellDamage.put(key, dmg);

                // Kształt (grubość/wysokość) — stare zapisy bez tych pól = pełna komórka
                CellShape shape = new CellShape(
                        cell.thickness != null ? parseFloat(cell.thickness, 1f) : 1f,
                        cell.height != null ? Math.max(1, parseInt(cell.height, 1)) : 1);
                cellShapes.put(key, shape);
                if (type == CellType.WALL && shape.thickness() < 1f) {
                    cellTransformCache.put(key, shapeTransform(type,
                            facing != null ? facing : BlockFace.NORTH, shape));
                }
            }
        }

        // Zawartość sieci z zapisu (base64 ItemStacków) — tylko dla sieci NET
        if (netContents != null) {
            for (Map.Entry<String, List<String>> e : netContents.entrySet()) {
                List<ItemStack> items = new ArrayList<>();
                for (String b64 : e.getValue()) {
                    try {
                        byte[] bytes = java.util.Base64.getDecoder().decode(b64);
                        ItemStack s = ItemStack.deserializeBytes(bytes);
                        if (s != null && s.getType() != Material.AIR) items.add(s);
                    } catch (Exception ignored) {
                        // uszkodzony wpis — pomiń
                    }
                }
                if (!items.isEmpty()) cellContents.put(e.getKey(), items);
            }
        }

        // Zawartość skrzyń z zapisu (v6, base64) — bez tego przedmioty schowane
        // w skrzyni ginęły przy restarcie serwera.
        if (chestContents != null) {
            for (Map.Entry<String, List<String>> e : chestContents.entrySet()) {
                List<ItemStack> items = new ArrayList<>();
                for (String b64 : e.getValue()) {
                    try {
                        byte[] bytes = java.util.Base64.getDecoder().decode(b64);
                        ItemStack s = ItemStack.deserializeBytes(bytes);
                        if (s != null && s.getType() != Material.AIR) items.add(s);
                    } catch (Exception ignored) {
                        // uszkodzony wpis — pomiń
                    }
                }
                if (!items.isEmpty()) cellContents.put(e.getKey(), items);
            }
        }

        // Spawnuj wizualizacje (BlockDisplay per deska) i kolizje (carrier+shulker)
        spawnCells();

        // Komponenty (pokład/maszt/żagiel) z zapisu
        if (components != null) {
            for (RaftPersistence.ComponentData comp : components) {
                if (comp.name == null || comp.name.isBlank()) continue;
                Vector offset = new Vector(comp.x, comp.y, comp.z);
                // Maszt/żagiel: Y to STAŁA konwencja, a nie dana z zapisu (stare
                // zapisy trzymały maszt na 1.0 — pół bloka nad pokładem — więc
                // wyrównujemy je do aktualnych stałych). X/Z komórki zostają.
                if (RaftState.MAST.equals(comp.name)) offset.setY(MAST_BASE_Y);
                else if (RaftState.SAIL.equals(comp.name)) offset.setY(SAIL_PLANE_Y);
                state.addComponent(comp.name, new RaftState.Component(comp.name, offset));
            }
        }

        // Podpory pięter z zapisu (raft-saves.yml: supports) — bez nich piętro
        // traciło podparcie po restarcie serwera.
        if (savedSupports != null) {
            for (Vector node : savedSupports) {
                if (node == null) continue;
                supports.add(node.clone());
            }
        }

        // Stan żagla z zapisu: rozwinięty → silnik fizyki bierze napęd z wiatru
        // zamiast z prądu wody (RaftPhysicsEngine.computeDrive).
        state.setSailOpen(sailOpen);

        // Żagiel z zapisu: dograj też serwerowy fallback (spawnCells wyżej nie
        // widział jeszcze komponentów, więc pierwszy sync go nie stworzył).
        syncAllVisuals();

        RaftManager.getInstance().registerRaft(this);

        // Log diagnostyczny — czy tratwa i kolizje na pewno powstaly
        org.bukkit.Bukkit.getLogger().info(
                "[Raft] " + (cells == null || cells.isEmpty() ? "Utworzono" : "Przywrócono")
                + " tratwę " + this.raftId.toString().substring(0, 8)
                + " dla gracza " + (owner == null ? "(system)" : owner)
                + " na (" + String.format("%.1f, %.1f, %.1f)", location.getX(), location.getY(), location.getZ())
                + " | komórki: " + blockOffsets.size()
                + " | shulkery żywe w świecie: " + countValidCollidersInWorld());

        // Efekt spawnu
        world.spawnParticle(Particle.CLOUD, location.clone().add(0, 0.5, 0), 10, 0.5, 0.2, 0.5);
    }

    /** Ile naszych shulkerów-kolizji faktycznie zyje w swiecie (do logu). */
    private int countValidCollidersInWorld() {
        int count = 0;
        for (Entity e : world.getEntitiesByClass(Shulker.class)) {
            if (e.getScoreboardTags().contains(TAG_COLLIDER) && !e.isDead() && e.isValid()) count++;
        }
        return count;
    }

    private void addInitialCell(Vector offset) {
        blockOffsets.add(offset.clone());
        String key = keyOf(offset);
        cellMaterials.put(key, Material.OAK_PLANKS);
        cellTypes.put(key, CellType.RAFT);
        cellDataCache.put(key, dataFor(Material.OAK_PLANKS, CellType.RAFT, null));
    }

    /**
     * Czy komórka tego typu ma kierunek (facing)? Obrotowe są schody, cienkie
     * ściany i skrzynie (latch przodu) — reszta typów (podłoga, sieć, tratwa)
     * kierunek ignoruje. Jedno źródło prawdy dla stawiania, zapisu i podglądu.
     */
    public static boolean isOrientable(CellType type) {
        return type == CellType.STAIRS || type == CellType.WALL
                || (type != null && type.isChest());
    }

    /**
     * BlockData komórki: schody dostają kierunek, skrzynie obrót przodu
     * (latch), reszta zwykły materiał. Publiczne, bo TEGO SAMEGO wywołania
     * używa podgląd-duch (RaftBuildPreview) i podświetlenie siekiery
     * (RaftAxeListener) — inaczej duch pokazywałby inny obrót niż stanie.
     */
    public static org.bukkit.block.data.BlockData dataFor(Material mat, CellType type, BlockFace facing) {
        if (type == CellType.STAIRS) {
            org.bukkit.block.data.type.Stairs stairs =
                    (org.bukkit.block.data.type.Stairs) Material.OAK_STAIRS.createBlockData();
            stairs.setFacing(facing != null ? facing : BlockFace.NORTH);
            return stairs;
        }
        org.bukkit.block.data.BlockData data = mat.createBlockData();
        if (facing != null && isOrientable(type)
                && data instanceof org.bukkit.block.data.Directional directional) {
            try {
                directional.setFacing(facing);
            } catch (IllegalArgumentException ignored) {
                // kierunek nieobsługiwany przez ten blok (np. UP/DOWN) — zostaje domyślny
            }
        }
        return data;
    }

    /** Parsuje typ komórki z zapisu (nieznane/brak → FLOOR). */
    private static CellType parseCellType(String s) {
        if (s == null) return CellType.FLOOR;
        try {
            return CellType.valueOf(s);
        } catch (IllegalArgumentException e) {
            return CellType.FLOOR;
        }
    }

    private static float parseFloat(String s, float def) {
        if (s == null) return def;
        try {
            return Float.parseFloat(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static int parseInt(String s, int def) {
        if (s == null) return def;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /**
     * Transformacja displaya komórki: pełna komórka = blok [−0.5, 0.5]³ jak zawsze;
     * cienka ściana = spłaszczony (grubość t) i rozciągnięty (wysokość h) blok.
     * Grubość idzie wzdłuż osi patrzenia (facing N/S → cienka w Z, E/W → w X),
     * spód ściany opiera się o dół komórki: [−0.5, −0.5+h].
     */
    public static Transformation shapeTransform(CellType type, BlockFace facing, CellShape shape) {
        if (type != CellType.WALL || shape == null || shape.thickness() >= 1f) {
            return new Transformation(
                    new Vector3f(-0.5f, -0.5f, -0.5f), new Quaternionf(),
                    new Vector3f(1f, 1f, 1f), new Quaternionf());
        }
        boolean thinX = facing == BlockFace.EAST || facing == BlockFace.WEST;
        float t = Math.max(0.05f, shape.thickness());
        float h = Math.max(1f, shape.height());
        if (thinX) {
            return new Transformation(
                    new Vector3f(-t / 2f, -0.5f, -0.5f), new Quaternionf(),
                    new Vector3f(t, h, 1f), new Quaternionf());
        }
        return new Transformation(
                new Vector3f(-0.5f, -0.5f, -t / 2f), new Quaternionf(),
                new Vector3f(1f, h, t), new Quaternionf());
    }

    /** Czy komórka to wysoka cienka ściana (wymaga ukrytej górnej pary kolizji). */
    private boolean isTallWall(String key) {
        CellShape s = cellShapes.get(key);
        return cellTypes.get(key) == CellType.WALL
                && s != null && s.height() >= 2 && s.thickness() < 1f;
    }

    private void spawnCells() {
        syncAllVisuals();
        ensureColliders();
    }

    // ─────────────────────── GLOWNY TICK ───────────────────────────

    public void tick() {
        if (world == null) return;

        Vector pos = state.getPosition();
        // Nie tikuj tratwy w rozładowanym chunku — entity i tak nie istnieja.
        if (!world.isChunkLoaded(Math.floorDiv((int) Math.floor(pos.getX()), 16),
                Math.floorDiv((int) Math.floor(pos.getZ()), 16))) {
            return;
        }

        // ── Faza 1 — LOD: tryb tickowania wg obecności graczy ────────────────
        // FULL   (20 Hz): gracz NA POKŁADZIE lub w promieniu FULL_TICK_RANGE od
        //   obrysu tratwy → pełny tick: fizyka + przenośnik graczy + sync encji
        //   co tick (płynny ruch pod stopami gracza).
        // FAR    (5 Hz):  gracz obserwuje tratwę z daleka (FULL_TICK_RANGE..
        //   LOD_OBSERVE_RANGE) → fizyka (dryf/pływalność) działa co tick, ale
        //   encje (deski BlockDisplay + kolizje) synchronizują pozycje tylko co
        //   FAR_SYNC_INTERVAL ticków — ~75% mniej teleportów/pakietów ruchu.
        // FROZEN (0 Hz):  żaden gracz nie jest w promieniu LOD_OBSERVE_RANGE →
        //   tratwa zamrożona: zero fizyki, zero syncu — stoi w miejscu i nic nie
        //   wysyła (co jakiś czas tylko dogrywamy brakujące encje po reloadzie).
        TickMode mode = determineTickMode();
        tickMode = mode;

        if (mode != lastTickMode) {
            // Przejście między trybami: zresetuj kadencje (nowy tryb ma zacząć
            // od razu, nie w środku cyklu) i przeskaluj przenośnik graczy — nie
            // działał w FAR/FROZEN, więc jego baza pomiaru delty jest nieaktualna.
            ticksSinceFarSync = FAR_SYNC_INTERVAL - 1; // pierwszy tick FAR = natychmiastowy sync
            frozenTicks = 0;
            if (mode == TickMode.FULL) {
                movementController.recalibrate();
            }
            if (mode == TickMode.FROZEN) {
                // Ustaw faktyczną obecność desek (FULL/FAR zawsze je odtwarzają) —
                // culling w FROZEN podejmuje decyzję na tej podstawie.
                frozenDisplaysPresent = visualRenderer.isAlive();
            }
            lastTickMode = mode;
        }

        switch (mode) {
            case FROZEN -> tickFrozen();
            case FAR -> tickFar();
            case FULL -> tickFull();
        }
    }

    /**
     * Tryb FULL (20 Hz): pełny tick — dokładnie jak przed Fazą 1 (fizyka,
     * sync wszystkich encji i przenośnik niosący graczy na pokładzie).
     */
    private void tickFull() {
        Vector before = state.getPosition(); // do pomiaru przesuniecia w tym ticku

        // Uzupełnij brakujace/martwe kolizje (po rozladowaniu chunku)
        ensureColliders();

        // Dryf z prądem / pływalność (wspólne z trybem FAR)
        advanceSimulation();

        // Przesun wszystkie entity do aktualnych pozycji komorek (plynnie)
        syncCells();

        // v9 — RUCHOMA PODLOGA: vanilla NIE niesie gracza stojacego NA poziomo
        // przesuwajacej sie platformie (kolizje teleportowane nie pchaja, a kolizje
        // „sunace" po prostu wysuwaja sie spod stop). Dlatego graczy na pokladzie
        // niesiemy niewidzialnym przenosnikiem: co tick dostaja velocity dokladnie
        // rowne przesunieciu tratwy (XZ). Zaden teleport gracza, zadna wymuszona
        // pozycja — gracz normalnie chodzi/skacze, a tratwa go niesie.
        // (Tylko FULL — gracz na tratwie zawsze kwalifikuje się do tego trybu.)
        movementController.tick();

        // Pomiar: ile faktycznie przesunela sie tratwa w tym ticku (poziomo)
        Vector after = state.getPosition();
        lastTickDisplacement = Math.hypot(after.getX() - before.getX(), after.getZ() - before.getZ());

        // DIAGNOSTYKA (config: raft-debug-diag, domyślnie WYŁĄCZONA): pozycja/vel/
        // prad/powierzchnia co 20 ticków (1 s). Tylko w FULL — gdy nikt nie stoi
        // blisko tratwy, logi milczą i tak.
        if (debugDiag && ++diagCounter % 20 == 0) {
            Vector v = state.getLinearVelocity();
            Vector c = physicsEngine.getGlobalCurrent();
            double surf = findWaterSurfaceTop();
            // Pomiar offsetu pasazera: roznica Y shulker vs jego marker (czy shulker
            // siedzi dokladnie na markerze, czy vanilla sadza go wyzej).
            String pairOffset = "?";
            for (java.util.Map.Entry<String, ArmorStand> e : carriers.entrySet()) {
                ArmorStand m = e.getValue();
                Shulker sh = colliders.get(e.getKey());
                if (m != null && m.isValid() && !m.isDead()
                        && sh != null && sh.isValid() && !sh.isDead()) {
                    pairOffset = String.format("%.2f", sh.getLocation().getY() - m.getLocation().getY());
                    break;
                }
            }
            org.bukkit.Bukkit.getLogger().info(String.format(
                    "[Raft] diag %s [%s]: pos=(%.2f, %.2f, %.2f) vel=(%.3f, %.3f, %.3f)"
                    + " prad=(%.2f, %.2f) powierzchnia=%s przesun=%s offsetY=%s najbliżej=%s",
                    raftId.toString().substring(0, 8),
                    tickMode,
                    after.getX(), after.getY(), after.getZ(),
                    v.getX(), v.getY(), v.getZ(),
                    c.getX(), c.getZ(),
                    Double.isNaN(surf) ? "BRAK" : String.format("%.1f", surf),
                    String.format("%.4f", lastTickDisplacement),
                    pairOffset,
                    nearestPlayerLabel()));
        }
    }

    /**
     * Tryb FAR (obserwacja z daleka, 5 Hz): fizyka działa co tick (dryf zostaje
     * dokładny — tratwa zatrzymuje się na brzegu tak samo jak wcześniej), ale
     * encje — deski BlockDisplay i kolizje — synchronizują pozycje tylko co
     * FAR_SYNC_INTERVAL ticków. Nikt nie stoi wtedy na tratwie ani przy niej
     * (taki gracz = tryb FULL), więc przenośnik i sync co tick są zbędne.
     */
    private void tickFar() {
        ensureColliders();
        advanceSimulation();

        if (++ticksSinceFarSync >= FAR_SYNC_INTERVAL) {
            ticksSinceFarSync = 0;
            syncCells(); // 4 ticki ruchu naraz (5 Hz) — z daleka wygląda płynnie
        }
    }

    /**
     * Tryb FROZEN (0 Hz): nikt nie obserwuje tratwy z bliska — fizyka i sync
     * stoją, tratwa czeka w miejscu (zero pakietów ruchu do klientów). Co jakiś
     * czas tylko DOGRYWAMY brakujące encje (np. po przeładowaniu chunku zniknęły
     * BlockDisplay persistent=false), nie ruszając tratwą — pozycja stoi, więc
     * sync przy okazji jest czystym no-opem. Faza 2: poza DISPLAY_CULL_RANGE
     * wygaszamy deski BlockDisplay (czysty wizual, histereza z DISPLAY_SPAWN_RANGE),
     * a kolizje zostają — są tanie, zapisywane do chunku i niezbędne po powrocie.
     */
    private void tickFrozen() {
        if (frozenDisplaysPresent) {
            // Mamy deski w świecie: trzymamy je do DISPLAY_CULL_RANGE i cyklicznie
            // dogrywamy brakujące encje (kolizje po reloadzie chunku).
            if (nearestPlayerDistanceSq <= DISPLAY_CULL_SQ) {
                if (++frozenTicks >= FROZEN_MAINTENANCE_INTERVAL) {
                    frozenTicks = 0;
                    ensureColliders();
                    syncCells();
                }
                return;
            }
            // Nikt nie jest w zasięgu wyświetlania — wygaś deski (czysty wizual).
            // Kolizje zostają: są tanie, zapisywane do chunku i niezbędne po powrocie.
            frozenDisplaysPresent = false;
            frozenTicks = 0;
            visualRenderer.cull();
            sailFallbackRenderer.cull();
            supportRenderer.cull();
            return;
        }

        // Desek nie ma: odtwarzamy od DISPLAY_SPAWN_RANGE (histereza — spawn niżej
        // niż cull, żeby gracz na granicy nie wywoływał migotania). Respawn od razu,
        // nie w następnym cyklu konserwacji.
        if (nearestPlayerDistanceSq <= DISPLAY_SPAWN_SQ) {
            frozenDisplaysPresent = true;
            frozenTicks = 0;
            ensureColliders();
            syncCells(); // sync tworzy brakujące BlockDisplay
        } else if (++frozenTicks >= FROZEN_MAINTENANCE_INTERVAL) {
            frozenTicks = 0; // daleko i bez desek — tylko odhaczaj czas
        }
    }

    /** Fizyka dryfu + pływalność — wspólna dla FULL i FAR (nie rusza encji). */
    private void advanceSimulation() {
        if (isMoving) {
            stepDrift();
        }

        // Plywalnosc: utrzymuj tratwe na powierzchni wody (Y)
        doBuoyancy();
    }

    /**
     * Dryf tratwy + kolizja ześlizgowa z lądem (wyspy, skały, brzeg — każdy
     * stały blok na linii wody).
     *
     * Napęd bierze się z silnika fizyki (RaftPhysicsEngine.getDriveVector()):
     * żagiel ROZWINIĘTY → wiatr (globalWind z RaftManagera), żagiel ZWINIĘTY
     * albo brak żagla → prąd wody (globalCurrent). Cała logika kolizji poniżej
     * liczy się na tym wektorze, więc tratwa ześlizguje się z brzegu identycznie
     * pod żaglem i na prądzie.
     *
     * Gdy napęd napiera tratwą na przeszkodę, tratwa NIE zatrzymuje się już
     * twardo (jak wcześniej), tylko ślizga się wzdłuż brzegu. Kierunek ślizgu
     * wyznacza oś, w którą napęd pcha tratwę DO przeszkody (ta oś = normalna
     * brzegu → blokowana), a prędkość ślizgu zależy WYŁĄCZNIE od kąta napędu
     * względem tej normalnej (0° = prosto w brzeg, 90° = napęd wzdłuż brzegu)
     * i jest mapowana na tabelę zadaną przez gracza (patrz slidePercent):
     *   1-10° → 7% prędkości dryfu, 11-20° → 18%, 21-30° → 29%,
     *   31-40° → 40%, 41-50° → 51%, 51-60° → 62%, 61-70° → 73%,
     *   71-80° → 84%, 81-89° → 95%, 90° → 100% (pełny dryf, nic nie zmienia).
     * Przy kącie 0° (napęd centralnie prosto w wyspę) tratwa praktycznie nie
     * płynie. Zwolnienie działa WYŁĄCZNIE przy kontakcie z blokiem — na
     * otwartej wodzie tratwa zawsze płynie z pełną prędkością napędu.
     */
    private void stepDrift() {
        Vector drive = physicsEngine.getDriveVector();
        if (drive == null || drive.lengthSquared() < 1e-8) {
            physicsEngine.tick();
            return;
        }

        // Otwarta woda: przed tratwą (w kierunku napędu) nie ma stałych bloków.
        if (!probeBlocked(drive.clone().normalize())) {
            physicsEngine.tick();
            return;
        }

        // Kontakt z lądem — sprawdź osobno osie X i Z (jak kolizja łódki):
        boolean blockedX = Math.abs(drive.getX()) > 1e-4
                && probeBlocked(new Vector(Math.signum(drive.getX()), 0, 0));
        boolean blockedZ = Math.abs(drive.getZ()) > 1e-4
                && probeBlocked(new Vector(0, 0, Math.signum(drive.getZ())));

        Vector vel = state.getLinearVelocity();

        // BACKSTOP: tratwa już NACHODZI na ląd (np. po zmianie kierunku prądu,
        // wciśnięta w róg zatoki albo po ześlizgu w płyciźnie) — nie pogłębiaj
        // wjazdu. Bez tego tratwa potrafiła „przechodzić" przez bloki, gdy
        // próbki przed krawędzią przeskoczyły ląd (wąski róg / plaża równo
        // z wodą). Stoi, dopóki prąd nie zmieni kierunku albo gracz nie odbije.
        if (hullOverlapsLand()) {
            vel.setX(0);
            vel.setZ(0);
            state.setLinearVelocity(vel);
            return; // silnik nie tickuje — tratwa nie wjedzie głębiej
        }

        // Róg / zatoka: obie osie zablokowane → tratwa praktycznie stoi (czeka,
        // aż prąd/wiatr zmieni kierunek albo gracz odbije tratwę wiosłami).
        if (blockedX && blockedZ) {
            vel.setX(0);
            vel.setZ(0);
            state.setLinearVelocity(vel);
            return; // silnik nie tickuje — napęd nie ma jak ruszyć tratwy
        }

        if (blockedX || blockedZ) {
            // Normalna brzegu = zablokowana oś. Kąt napędu (prądu/wiatru)
            // względem normalnej (w stopniach 0..90) wyznacza % prędkości
            // ślizgu wzdłuż brzegu.
            double angleDeg;
            double alongX;
            double alongZ;
            if (blockedX) {
                angleDeg = Math.toDegrees(Math.acos(
                        Math.max(0, Math.min(1, Math.abs(drive.getX()) / drive.length()))));
                alongX = 0;
                alongZ = Math.signum(drive.getZ());
            } else {
                angleDeg = Math.toDegrees(Math.acos(
                        Math.max(0, Math.min(1, Math.abs(drive.getZ()) / drive.length()))));
                alongX = Math.signum(drive.getX());
                alongZ = 0;
            }

            double slideSpeed = drive.length() * slidePercent(angleDeg) / 100.0;

            // Zerujemy tylko składową pchającą tratwę w przeszkodę; silnik
            // dostaje napęd wyłącznie wzdłuż brzegu (o prędkości slideSpeed) i
            // płynnie rozpędza/wygasza tratwę do tej prędkości.
            if (blockedX) vel.setX(0);
            if (blockedZ) vel.setZ(0);
            state.setLinearVelocity(vel);

            Vector gated = new Vector(alongX * slideSpeed, 0, alongZ * slideSpeed);
            physicsEngine.setDriveOverride(gated);
            physicsEngine.tick();
            return;
        }

        // Dotyk tylko samym rogiem/po skosie (żadna oś nie jest jeszcze w pełni
        // zablokowana): wygaszamy pęd w kierunku wyspy i pełzniemy minimalnie.
        // Najbliższy blok wyspy i tak wejdzie w zasięg próbek przy którymś z
        // kolejnych ticków i przełączy tratwę na ślizg wzdłuż brzegu — tratwa
        // nigdy nie przecina wyspy na skos.
        vel.multiply(0.25);
        state.setLinearVelocity(vel);
        physicsEngine.setDriveOverride(drive.clone().multiply(CORNER_CRAWL_FACTOR));
        physicsEngine.tick();
    }

    /**
     * Prędkość ślizgu wzdłuż brzegu, w % normalnego dryfu — wg kąta prądu
     * względem normalnej brzegu (0° = prosto w brzeg/wyspę, 90° = wzdłuż).
     * Tabela domyślnego zachowania (środek zakresu → wartość, między progami
     * liniowa interpolacja, żeby prędkość nie skakała o krok na granicy kąta):
     * <pre>
     *   1-10°    ->   7% prędkości
     *   11-20°   ->  18% prędkości
     *   21-30°   ->  29% prędkości
     *   31-40°   ->  40% prędkości
     *   41-50°   ->  51% prędkości
     *   51-60°   ->  62% prędkości
     *   61-70°   ->  73% prędkości
     *   71-80°   ->  84% prędkości
     *   81-89°   ->  95% prędkości
     *   90°      -> 100% prędkości (pełny dryf, nic się nie zmienia)
     * </pre>
     * Tabela jest NADPISYWANA z configu (raft-collision → slide-speeds) przez
     * loadCollisionConfig — zmiana procentów nie wymaga rekompilacji.
     */
    private static double slidePercent(double angleDeg) {
        double[] angles = collisionSlideAngles;
        double[] values = collisionSlidePercents;
        if (angles == null || values == null
                || angles.length != values.length || angles.length < 2) {
            return 0; // uszkodzona tabela — tratwa stoi przy dotknięciu
        }
        if (angleDeg <= angles[0]) return values[0];
        double lastAngle = angles[angles.length - 1];
        if (angleDeg >= lastAngle) return values[values.length - 1];
        for (int i = 1; i < angles.length; i++) {
            double a0 = angles[i - 1];
            double a1 = angles[i];
            if (a1 <= a0) continue; // ochrona przed złą konfiguracją
            if (angleDeg <= a1) {
                double v0 = values[i - 1];
                double v1 = values[i];
                return v0 + (v1 - v0) * (angleDeg - a0) / (a1 - a0);
            }
        }
        return values[values.length - 1];
    }

    /**
     * Wczytuje ustawienia kolizji ześlizgowej tratwy z configu pluginu
     * (sekcja raft-collision) i podmienia statyczne tabele (collisionProbeGaps,
     * collisionSlideAngles/collisionSlidePercents). Wołane przy starcie pluginu
     * oraz po /def reload — zmiany w config.yml działają bez rekompilacji:
     * <pre>
     * raft-collision:
     *   probe-gaps:            # odległości próbek przed krawędzią tratwy [bloki]
     *     - 0.4
     *     - 1.0
     *   slide-speeds:          # kąt prądu [stopnie]: % prędkości dryfu
     *     0: 0
     *     5.5: 7
     *     90: 100
     * </pre>
     * Brak sekcji albo nieprawidłowe wartości są ignorowane — zostają
     * dotychczasowe (domyślne) ustawienia.
     */
    public static void loadCollisionConfig(org.bukkit.configuration.file.FileConfiguration cfg) {
        if (cfg == null) return;
        try {
            org.bukkit.configuration.ConfigurationSection root =
                    cfg.getConfigurationSection("raft-collision");
            if (root == null) return; // brak sekcji — wartości domyślne

            // --- probe-gaps: lista dodatnich odległości próbek ---
            java.util.List<?> rawGaps = root.getList("probe-gaps");
            if (rawGaps != null && !rawGaps.isEmpty()) {
                java.util.List<Double> gaps = new java.util.ArrayList<>();
                for (Object o : rawGaps) {
                    if (o instanceof Number n) {
                        double d = n.doubleValue();
                        if (Double.isFinite(d) && d > 0) gaps.add(d);
                    }
                }
                if (!gaps.isEmpty()) {
                    double[] arr = new double[gaps.size()];
                    for (int i = 0; i < arr.length; i++) arr[i] = gaps.get(i);
                    collisionProbeGaps = arr;
                }
            }

            // --- slide-speeds: mapa "kąt (stopnie): % prędkości dryfu" ---
            org.bukkit.configuration.ConfigurationSection speeds =
                    root.getConfigurationSection("slide-speeds");
            if (speeds != null) {
                java.util.TreeMap<Double, Double> points = new java.util.TreeMap<>();
                for (String key : speeds.getKeys(false)) {
                    try {
                        double angle = Double.parseDouble(key);
                        double percent = speeds.getDouble(key, -1);
                        if (Double.isFinite(angle) && angle >= 0 && angle <= 90
                                && Double.isFinite(percent) && percent >= 0 && percent <= 100) {
                            points.put(angle, percent);
                        }
                    } catch (NumberFormatException ignored) {
                        // klucz nie jest liczbą — pomiń
                    }
                }
                if (points.size() >= 2) {
                    double[] angles = new double[points.size()];
                    double[] percents = new double[points.size()];
                    int i = 0;
                    for (java.util.Map.Entry<Double, Double> e : points.entrySet()) {
                        angles[i] = e.getKey();
                        percents[i] = e.getValue();
                        i++;
                    }
                    collisionSlideAngles = angles;
                    collisionSlidePercents = percents;
                }
            }
        } catch (Exception e) {
            org.bukkit.Bukkit.getLogger().warning(
                    "[Raft] Nie udało się wczytać sekcji raft-collision z configu — "
                    + "zostają dotychczasowe ustawienia kolizji (" + e + ")");
        }
    }

    // ── Faza 1 — wykrywanie obecności graczy (LOD) ──────────────────────────

    /**
     * Określa tryb LOD na ten tick: pozycja najbliższego gracza w świecie tratwy
     * vs. odległość od OBRYSU pokładu (AABB komórek). Liczenie od obrysu, a nie
     * od środka, sprawia, że gracz stojący na drugim końcu dużej tratwy (daleko
     * od jej środka) nadal dostaje pełne 20 Hz.
     */
    private TickMode determineTickMode() {
        double[] bounds = deckBoundsXZ();
        cachedDeckBoundsXZ = bounds; // do filtra pakietów LOD w tym samym ticku
        if (bounds == null) return TickMode.FROZEN; // tratwa bez komórek — nikogo nie pomieści

        double fullSq = FULL_TICK_RANGE * FULL_TICK_RANGE;
        double lodSq = LOD_OBSERVE_RANGE * LOD_OBSERVE_RANGE;

        TickMode mode = TickMode.FROZEN;
        double nearestSq = Double.MAX_VALUE;

        for (Player p : world.getPlayers()) {
            double dx = axisDistance(p.getLocation().getX(), bounds[0], bounds[1]);
            double dz = axisDistance(p.getLocation().getZ(), bounds[2], bounds[3]);
            double distSq = dx * dx + dz * dz;
            if (distSq < nearestSq) nearestSq = distSq;

            if (distSq <= fullSq) {
                // Ktoś na pokładzie / bliżej niż FULL_TICK_RANGE — reszta graczy nieistotna
                nearestPlayerDistanceSq = nearestSq;
                return TickMode.FULL;
            }
            if (distSq <= lodSq) mode = TickMode.FAR;
        }
        nearestPlayerDistanceSq = nearestSq;
        return mode;
    }

    /** Odległość (XZ) najbliższego gracza od pokładu w metrach (do /raft debug i logów). */
    private String nearestPlayerLabel() {
        if (nearestPlayerDistanceSq >= Double.MAX_VALUE / 2) return "brak";
        return String.format("%.1f m", Math.sqrt(nearestPlayerDistanceSq));
    }

    /**
     * Obrys pokładu tratwy w świecie: [minX, maxX, minZ, maxZ] po zewnętrznych
     * krawędziach komórek (offset ± 0.5). null, gdy tratwa nie ma komórek.
     */
    private double[] deckBoundsXZ() {
        if (blockOffsets.isEmpty()) return null;
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (Vector o : blockOffsets) {
            minX = Math.min(minX, o.getX() - 0.5);
            maxX = Math.max(maxX, o.getX() + 0.5);
            minZ = Math.min(minZ, o.getZ() - 0.5);
            maxZ = Math.max(maxZ, o.getZ() + 0.5);
        }
        Vector p = state.getPosition();
        return new double[]{p.getX() + minX, p.getX() + maxX, p.getZ() + minZ, p.getZ() + maxZ};
    }

    /** 0, gdy wartość jest w [min, max]; inaczej odległość do najbliższej krawędzi. */
    private static double axisDistance(double v, double min, double max) {
        if (v < min) return min - v;
        if (v > max) return v - max;
        return 0;
    }

    /**
     * Kwadrat odległości (XZ) punktu od obrysu pokładu — dla filtra pakietów LOD
     * (RaftPacketLOD). Używa obrysu zbuforowanego w bieżącym ticku (liczonego od
     * AABB komórek, nie od środka — duże tratwy traktowane poprawnie).
     */
    public double distanceSqToDeckXZ(double x, double z) {
        double[] bounds = cachedDeckBoundsXZ;
        if (bounds == null) {
            bounds = deckBoundsXZ();
            if (bounds == null) return Double.MAX_VALUE; // tratwa bez komórek
        }
        double dx = axisDistance(x, bounds[0], bounds[1]);
        double dz = axisDistance(z, bounds[2], bounds[3]);
        return dx * dx + dz * dz;
    }

    /** Bieżący tryb LOD (dla filtra pakietów i /raft debug). */
    public TickMode getTickMode() {
        return tickMode;
    }

    /** Czy encja należy do tratwy (kolizja lub deska BlockDisplay). */
    public static boolean isRaftEntity(Entity e) {
        if (e == null || e.isDead()) return false;
        return e.getScoreboardTags().contains(TAG_COLLIDER)
                || e.getScoreboardTags().contains(TAG_VISUAL);
    }

    /**
     * Wyciąga UUID tratwy z tagów encji ("raft_<uuid>") lub null, gdy encja nie
     * ma takiego tagu. Używane przez sprzątanie orphanów i filtr pakietów LOD.
     */
    public static UUID raftIdOf(Entity e) {
        if (e == null) return null;
        for (String tag : e.getScoreboardTags()) {
            if (tag.startsWith("raft_") && tag.length() > "raft_".length()) {
                try {
                    return UUID.fromString(tag.substring("raft_".length()));
                } catch (IllegalArgumentException ignored) {
                    // raft_collider / raft_visual itp. — to nie UUID
                }
            }
        }
        return null;
    }

    /** Licznik do diagnostyki co 20 ticków. */
    private int diagCounter = 0;

    /**
     * Diagnostyka tratwy w konsoli/logach (config: raft-debug-diag, domyślnie
     * false). Gdy false, per-sekundowy log „[Raft] diag" w trybie FULL jest
     * pomijany — konsola zostaje czysta. Włącz tylko do debugowania fizyki.
     * Ustawiane przy starcie pluginu i po /def reload — patrz loadDiagConfig.
     */
    public static volatile boolean debugDiag = false;

    /**
     * Wczytuje flagę diagnostyki z configu rodzica (raft-debug-diag, domyślnie
     * false). Wołane z RaftPlugin.reloadRaftConfig() przy starcie i po /def reload.
     */
    public static void loadDiagConfig(org.bukkit.configuration.file.FileConfiguration cfg) {
        debugDiag = cfg != null && cfg.getBoolean("raft-debug-diag", false);
    }

    // ─────────────────────── PLYWALNOSC ─────────────────────────────

    /**
     * Plywalnosc: cel Y tratwy = powierzchnia wody + FLOAT_OFFSET, czyli przy
     * SUBMERGED_DEPTH = 0.6 srodek siedzi 0.1 bloka PONIZEJ linii wody, a deski
     * sa zanurzone dokladnie w 6/10 (spod 0.6 pod woda, poklad 0.4 nad nia —
     * jak prawdziwa tratwa w grze Raft). Plyynne doplywanie bez skokow (max
     * 0.12 bloka/tick) i bez oscylacji (martwa strefa 0.002).
     */
    private void doBuoyancy() {
        double surface = findWaterSurfaceTop();
        if (Double.isNaN(surface)) return; // brak wody pod tratwa (np. brzeg) — zostaje

        double target = surface + FLOAT_OFFSET;
        Vector pos = state.getPosition();
        double dy = target - pos.getY();
        if (Math.abs(dy) < 0.002) return;

        double step = Math.copySign(Math.min(Math.abs(dy), 0.12), dy);
        pos.setY(pos.getY() + step);
        state.setPosition(pos);
    }

    /**
     * Szuka powierzchni wody (top najwyzszego bloku wody) w kolumnie nad tratwa.
     * Zwraca Y powierzchni (blokWody.getY()+1) lub NaN gdy nad tratwa nie ma wody.
     */
    private double findWaterSurfaceTop() {
        Vector pos = state.getPosition();
        int bx = (int) Math.floor(pos.getX());
        int bz = (int) Math.floor(pos.getZ());
        int startY = (int) Math.ceil(pos.getY()) + 2;
        int endY = startY - 24;

        for (int y = startY; y > endY; y--) {
            Block b = world.getBlockAt(bx, y, bz);
            if (b.getType() == Material.WATER) {
                return y + 1.0;
            }
            if (b.getType().isSolid()) {
                break; // lad nad woda — brak wolnej kolumny wody
            }
        }
        return Double.NaN;
    }

    // ─────────────────────── KOLIZJA Z OTOCZENIEM ───────────────────

    /**
     * Współczynnik pełzania (ułamek prądu) przy kontakcie tylko „po skosie"
     * (np. sam róg wyspy) — zamiast twardego zatrzymania tratwa przeciska się
     * bardzo wolno, aż próbki wykryją oś, wzdłuż której może sunąć.
     */
    private static final double CORNER_CRAWL_FACTOR = 0.35;

    /**
     * Odległości próbek przed najdalszą krawędzią tratwy (w blokach): bliska
     * (0.4 — „dotknięcie") i daleka (1.0 — wcześniejsze wykrycie, żeby tratwa
     * zwalniała/ślizgała się, zanim wjedzie w przeszkodę).
     *
     * NADPISYWANE z configu (sekcja raft-collision → probe-gaps) przy starcie
     * pluginu i po /def reload — patrz loadCollisionConfig.
     */
    public static double[] collisionProbeGaps = {0.4, 1.0};

    /**
     * Punkty tabeli ślizgu: kąty prądu w stopniach (0° = prąd prosto w wyspę,
     * 90° = prąd wzdłuż brzegu). NADPISYWANE z configu (raft-collision →
     * slide-speeds) — patrz loadCollisionConfig.
     */
    public static double[] collisionSlideAngles =
            {0, 5.5, 15.5, 25.5, 35.5, 45.5, 55.5, 65.5, 75.5, 85, 90};

    /**
     * Punkty tabeli ślizgu: % normalnej prędkości dryfu dla kątów z
     * collisionSlideAngles (ta sama długość, indeks w indeks).
     */
    public static double[] collisionSlidePercents =
            {0, 7, 18, 29, 40, 51, 62, 73, 84, 95, 100};

    /**
     * Czy w kierunku `dir` (znormalizowany wektor poziomy) tratwa ma przed sobą
     * stały blok (wyspa, skała, brzeg) na wysokości linii wody?
     *
     * Próbkujemy CAŁY OB RYS pokładu, nie tylko środek: bierzemy poziome
     * kolumny WSZYSTKICH komórek tratwy (bez duplikatów dla pięter/ścian
     * stojących nad sobą), rzutujemy je na najdalszą w kierunku `dir` linię
     * frontu i sprawdzamy bloki collisionProbeGaps (config raft-collision,
     * domyślnie 0.4 i 1.0) przed tą linią. Dzięki temu:
     *  - każda komórka pokładu ma swoją próbkę — duża tratwa wykrywa wyspę
     *    CAŁĄ swoją krawędzią (stary kod próbkował tylko ±0.9 wokół środka),
     *  - wąska skała/rafa mniejsza od tratwy też jest wykrywana (jeśli tylko
     *    leży przed którąkolwiek kolumną komórek),
     *  - próbki nigdy nie „wpadają" w samą tratwę (mierzone od jej krawędzi),
     *  - próbkujemy DWIE warstwy, które obejmują kadłub tratwy: warstwę nad
     *    powierzchnią wody (indeks probeY — trawa/skała wystająca nad wodę)
     *    i warstwę NA poziomie wody (probeY-1 — plaża/wydma równa z wodą,
     *    której wcześniejsza wersja NIE wykrywała, przez co tratwa wjeżdżała
     *    kadłubem w płaski brzeg).
     */
    private boolean probeBlocked(Vector dir) {
        if (dir == null || dir.lengthSquared() < 1e-6) return false;
        if (blockOffsets.isEmpty()) return false;

        Vector pos = state.getPosition();
        // Górna warstwa: indeks bloku, na którym leży powierzchnia wody
        // (floor(powierzchnia) = floor(pos.y - FLOAT_OFFSET)).
        int probeY = (int) Math.floor(pos.getY() - FLOAT_OFFSET);
        int[] layers = {probeY, probeY - 1};

        double dirX = dir.getX();
        double dirZ = dir.getZ();

        // Poziome kolumny komórek (x,z bez duplikatów) + najdalszy rzut środków
        // komórek na kierunek `dir` (= linia frontu tratwy).
        List<double[]> columns = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        double front = Double.NEGATIVE_INFINITY;
        for (Vector off : blockOffsets) {
            String key = ((int) Math.floor(off.getX() + 0.5)) + ","
                    + ((int) Math.floor(off.getZ() + 0.5));
            if (!seen.add(key)) continue; // piętro nad pokładem = ta sama kolumna
            columns.add(new double[]{off.getX(), off.getZ()});
            front = Math.max(front, off.getX() * dirX + off.getZ() * dirZ);
        }

        // Odległość środka komórki (kwadrat 1×1) do jej krawędzi w kierunku
        // `dir`: 0.5 bloka w osiach, więcej po skosie (do 0.707 przy 45°).
        double lead = 0.5 / Math.max(Math.abs(dirX), Math.abs(dirZ));

        for (double gap : collisionProbeGaps) {
            for (double[] c : columns) {
                // Próbka: kolumna wyrównana do linii frontu + krawędź + odstęp.
                double dist = (front - (c[0] * dirX + c[1] * dirZ)) + lead + gap;
                double px = pos.getX() + c[0] + dirX * dist;
                double pz = pos.getZ() + c[1] + dirZ * dist;
                int bx = (int) Math.floor(px);
                int bz = (int) Math.floor(pz);
                for (int layer : layers) {
                    Block b = world.getBlockAt(bx, layer, bz);
                    if (b.getType().isSolid() && b.getType() != Material.ICE) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Czy kadłub tratwy (obrys pokładu na linii wody) NACHODZI teraz na stały
     * blok lądu? Backstop kolizji: sprawdza obie warstwy, które obejmuje kadłub
     * (ta nad wodą i ta na poziomie wody) pod KOLUMNAMI komórek tratwy — jeśli
     * jakikolwiek stały blok jest już pod spodem/na wysokości kadłuba, tratwa
     * stoi i nie wjeżdża głębiej (próbki przed krawędzią tego nie złapią, bo ląd
     * jest już POD tratwą).
     */
    private boolean hullOverlapsLand() {
        if (blockOffsets.isEmpty()) return false;
        Vector pos = state.getPosition();
        int probeY = (int) Math.floor(pos.getY() - FLOAT_OFFSET);
        int[] layers = {probeY, probeY - 1};

        Set<String> seen = new HashSet<>();
        for (Vector off : blockOffsets) {
            String key = ((int) Math.floor(off.getX() + 0.5)) + ","
                    + ((int) Math.floor(off.getZ() + 0.5));
            if (!seen.add(key)) continue; // piętro = ta sama kolumna
            int bx = (int) Math.floor(pos.getX() + off.getX());
            int bz = (int) Math.floor(pos.getZ() + off.getZ());
            for (int layer : layers) {
                Block b = world.getBlockAt(bx, layer, bz);
                if (b.getType().isSolid() && b.getType() != Material.ICE) {
                    return true;
                }
            }
        }
        return false;
    }

    // ─────────────── KOLIZJE (SHULKER PASAZER MARKERA) ───────────────

    private void ensureColliders() {
        // Wyrzuc pary pod starymi/obcymi kluczami (np. z poprzednich wersji
        // pluginu, ktore przetrwaly w pamieci) — nie naleza do zadnej komorki.
        // Przy okazji usuwamy te entity ze swiata (zeby nie zostawac duchem).
        Set<String> validKeys = new HashSet<>();
        for (Vector offset : blockOffsets) validKeys.add(keyOf(offset));

        carriers.entrySet().removeIf(e -> {
            if (validKeys.contains(e.getKey())) return false;
            ArmorStand as = e.getValue();
            if (as != null && !as.isDead()) as.remove();
            return true;
        });
        colliders.entrySet().removeIf(e -> {
            if (validKeys.contains(e.getKey())) return false;
            Shulker s = e.getValue();
            if (s != null && !s.isDead()) s.remove();
            return true;
        });

        for (Vector offset : blockOffsets) {
            String key = keyOf(offset);
            ensureCollider(key, offset);
        }

        // Ukryte pary kolizji wysokich ścian (poziomy ponad podstawą ściany)
        ensureHiddenPairs();
    }

    /**
     * Upewnia sie, ze komorka ma zywa pare carrier(marker)+shulker(pasazer).
     * Jesli entity zginely/rozladowano chunk — odtwarza (przejmujac istniejace
     * po tagu, zeby nie robic duplikatow).
     *
     * UWAGA (bug v4): adopcja istniejacego shulkera MUSI byc po DOKLADNEJ
     * pozycji komorki. Komorki tratwy leza 1 blok od siebie — szukanie w
     * promieniu kilku blokow powodowalo, ze kazda kolejna komorka kradla pare
     * pierwszej (wszystkie 4 klucze wskazywaly JEDEN shulker + JEDEN marker,
     * wiec w swiecie byla 1 kolizja zamiast 4).
     */
    private void ensureCollider(String key, Vector offset) {
        ArmorStand carrier = carriers.get(key);
        Shulker shulker = colliders.get(key);

        boolean carrierOk = carrier != null && !carrier.isDead() && carrier.isValid() && carrier.getWorld().equals(world);
        boolean shulkerOk = shulker != null && !shulker.isDead() && shulker.isValid() && shulker.getWorld().equals(world);

        if (carrierOk && shulkerOk) {
            // Upewnij sie, ze shulker nadal jest pasazerem markera
            if (!carrier.getPassengers().contains(shulker)) {
                carrier.addPassenger(shulker);
            }
            return;
        }

        Vector pos = state.getPosition();
        double x = pos.getX() + offset.getX();
        // AABB zamknietego shulkera = 1×1×1 OD pozycji w górę → kotwica 0.5 POD
        // srodkiem deski, zeby box pokrywal dokladnie widoczna deske.
        double y = pos.getY() + offset.getY() + COLLIDER_Y_OFFSET;
        double z = pos.getZ() + offset.getZ();

        // Sprobuj przejac istniejaca pare po tagu (po rozladowaniu chunku).
        // Tylko z DOKLADNEJ pozycji tej komorki (max 0.6 bloka) — inaczej
        // sasiednia komorka (1 blok dalej) kradnie cudza pare.
        Shulker existingShulker = findExistingCollider(x, y, z);
        if (existingShulker != null && existingShulker.isValid() && !existingShulker.isDead()) {
            Entity vehicle = existingShulker.getVehicle();
            ArmorStand existingCarrier = (vehicle instanceof ArmorStand as && as.isValid() && !as.isDead()) ? as : null;

            // Nie adoptuj pary, ktora juz jest zarejestrowana pod INNA komorka
            // (pojawia sie, gdy cos poszlo nie tak) — spawnowij swieza.
            if (existingCarrier != null && carriers.containsValue(existingCarrier)) {
                spawnPair(key, x, y, z);
                return;
            }

            if (existingCarrier == null) {
                // shulker istnieje, ale bez markera — spawnowij nowy marker i wsadz go
                existingCarrier = spawnCarrier(x, y, z);
                existingCarrier.addPassenger(existingShulker);
            }
            carriers.put(key, existingCarrier);
            colliders.put(key, existingShulker);
            return;
        }

        // Pelny spawn nowej pary
        spawnPair(key, x, y, z);
    }

    private Shulker findExistingCollider(double x, double y, double z) {
        String raftTag = "raft_" + raftId;
        Location loc = new Location(world, x, y, z);
        // Maly promien + ciasny test odleglosci: szukamy shulkera STOJACEGO
        // DOKLADNIE na pozycji tej komorki, nigdy sasiada.
        for (Entity e : world.getNearbyEntities(loc, 0.75, 0.75, 0.75)) {
            if (e instanceof Shulker s && s.isValid() && !s.isDead()) {
                if (s.getScoreboardTags().contains(raftTag)
                        && s.getLocation().distanceSquared(loc) < 0.36) { // < 0.6 bloka
                    return s;
                }
            }
        }
        return null;
    }

    private void spawnPair(String key, double x, double y, double z) {
        spawnPairInto(carriers, colliders, key, x, y, z);
    }

    /** Spawnuje parę marker+shulker do podanych map (używane przez spawnPair). */
    private void spawnPairInto(Map<String, ArmorStand> carrierMap, Map<String, Shulker> colliderMap,
                               String key, double x, double y, double z) {
        ArmorStand carrier = spawnCarrier(x, y, z);
        Shulker shulker = spawnShulker(x, y, z);

        // Shulker jako pasazer markera — pozycja zablokowana, nie do odepchniecia
        carrier.addPassenger(shulker);

        carrierMap.put(key, carrier);
        colliderMap.put(key, shulker);

        org.bukkit.Bukkit.getLogger().info(
                "[Raft] komórka " + key + " → shulker #" + (shulker == null ? "NULL" : shulker.getEntityId())
                + " na markerze #" + (carrier == null ? "NULL" : carrier.getEntityId())
                + " @ (" + String.format("%.2f, %.2f, %.2f)", x, y, z));
    }

    /**
     * Upewnia się, że KAŻDA wysoka ściana ma ukryte pary kolizji na poziomach
     * y+1 .. y+wysokość−1 (bez displayów — shulker jest tylko 1×1×1, więc
     * 2 bloki wysokości = 2 pary). Pozycja, na której stoi już prawdziwa
     * komórka, jest pomijana (ta komórka ma własną kolizję) — dzięki temu nie
     * ma duplikatów, gdy coś stoi nad/na ścianie.
     */
    private void ensureHiddenPairs() {
        Set<String> needed = new HashSet<>();
        for (Vector offset : blockOffsets) {
            String key = keyOf(offset);
            if (!isTallWall(key)) continue;
            CellShape s = cellShapes.get(key);
            for (int k = 1; k < s.height(); k++) {
                Vector p = offset.clone().add(new Vector(0, k, 0));
                if (cellTypes.containsKey(keyOf(p))) continue; // prawdziwa komórka — ma własną parę
                needed.add(keyOf(p));
            }
        }

        // Usuń pary, których pozycja przestała być pokryta (np. po usunięciu
        // komórki-podstawy albo postawieniu prawdziwej komórki na tej pozycji)
        hiddenPairs.entrySet().removeIf(e -> {
            if (needed.contains(e.getKey())) return false;
            HiddenPair hp = e.getValue();
            if (hp.carrier != null && !hp.carrier.isDead()) hp.carrier.remove();
            if (hp.shulker != null && !hp.shulker.isDead()) hp.shulker.remove();
            return true;
        });

        // Dograj brakujące pary
        for (Vector offset : blockOffsets) {
            String key = keyOf(offset);
            if (!isTallWall(key)) continue;
            CellShape s = cellShapes.get(key);
            for (int k = 1; k < s.height(); k++) {
                Vector p = offset.clone().add(new Vector(0, k, 0));
                String pk = keyOf(p);
                if (!needed.contains(pk)) continue;
                HiddenPair hp = hiddenPairs.get(pk);
                if (hp == null) {
                    hp = new HiddenPair(p);
                    hiddenPairs.put(pk, hp);
                }
                ensureHiddenPair(hp);
            }
        }
    }

    /** Upewnia się, że jedna ukryta para ma żywe encje (adoptuje po tagu). */
    private void ensureHiddenPair(HiddenPair hp) {
        if (hp.carrier != null && !hp.carrier.isDead() && hp.carrier.isValid()
                && hp.shulker != null && !hp.shulker.isDead() && hp.shulker.isValid()
                && hp.carrier.getPassengers().contains(hp.shulker)) {
            return;
        }

        Vector pos = state.getPosition();
        double x = pos.getX() + hp.offset.getX();
        double y = pos.getY() + hp.offset.getY() + COLLIDER_Y_OFFSET;
        double z = pos.getZ() + hp.offset.getZ();

        Shulker existing = findExistingCollider(x, y, z);
        if (existing != null && existing.isValid() && !existing.isDead()) {
            Entity vehicle = existing.getVehicle();
            ArmorStand existingCarrier = (vehicle instanceof ArmorStand as && as.isValid() && !as.isDead()) ? as : null;
            if (existingCarrier == null) {
                existingCarrier = spawnCarrier(x, y, z);
                existingCarrier.addPassenger(existing);
            }
            hp.carrier = existingCarrier;
            hp.shulker = existing;
            return;
        }
        hp.carrier = spawnCarrier(x, y, z);
        hp.shulker = spawnShulker(x, y, z);
        hp.carrier.addPassenger(hp.shulker);
    }

    /** Ukryta para kolizji wysokiej ściany (pozycja + encje). */
    private static final class HiddenPair {
        final Vector offset;
        ArmorStand carrier;
        Shulker shulker;

        HiddenPair(Vector offset) {
            this.offset = offset;
        }
    }

    /**
     * Carrier: ArmorStand z marker(true) — ZERO wlasnej kolizji i rozmiaru.
     * Marker ma wysokosc 0, wiec pasazer siedzi dokladnie na jego pozycji.
     */
    private ArmorStand spawnCarrier(double x, double y, double z) {
        return world.spawn(new Location(world, x, y, z), ArmorStand.class, a -> {
            a.setMarker(true);
            a.setInvisible(true);
            a.setInvulnerable(true);
            a.setGravity(false);
            a.setSilent(true);
            a.setPersistent(true);
            a.setCustomNameVisible(false);
            a.addScoreboardTag(TAG_COLLIDER);
            a.addScoreboardTag("raft_" + raftId);
        });
    }

    /**
     * Shulker: kolizja DOKŁADNIE jak w BlockShips (ich ShipInstance.spawnCollisionShulkers):
     *   setPeek(0) — ZAMKNIĘTY (zero animacji otwierania, zero pchania graczy)
     *   setAttachedFace(DOWN) — stabilizacja przed waniliową walidacją przyczepności
     * AABB zamkniętego shulkera (sprawdzone w bytecode 26.2, Shulker.getProgressDeltaAabb)
     * to 1×1×1 liczone OD POZYCJI W GÓRĘ (pozycja entity = spód hitboxu). Dlatego para
     * (marker+shulker) jest kotwiczona 0.5 bloka POD środkiem deski (COLLIDER_Y_OFFSET)
     * — wtedy box shulkera pokrywa się dokładnie z deską BlockDisplay [środek−0.5, środek+0.5]
     * i stopy gracza lądują równo na górnej powierzchni deski (wcześniej box wystawał
     * pół bloka nad deskę — gracz stał „w powietrzu").
     * Półotwarty peek(0.5)+UP powodował: wystający w dół hitbox, animacje otwierania
     * i pchanie gracza do góry („4 kratki wyżej" + zablokowana kamera).
     */
    private Shulker spawnShulker(double x, double y, double z) {
        Shulker spawned = world.spawn(new Location(world, x, y, z), Shulker.class, s -> {
            s.setAI(false);
            s.setGravity(false);
            s.setSilent(true);
            s.setPersistent(true);
            s.setCustomNameVisible(false);
            s.setCollidable(true);
            s.setInvisible(true);
            s.setPeek(0); // ZAMKNIĘTY — jak BlockShips (bez otwierania i pchania)
            s.setAttachedFace(BlockFace.DOWN); // jak BlockShips (stabilizacja)
            s.addScoreboardTag(TAG_COLLIDER);
            s.addScoreboardTag("raft_" + raftId);
        });

        if (spawned == null || spawned.isDead() || !spawned.isValid()) {
            org.bukkit.Bukkit.getLogger().warning(
                    "[Raft] UWAGA: shulker-kolizja NIE weszła do świata na ("
                    + String.format("%.1f, %.1f, %.1f)", x, y, z)
                    + " — spawn został anulowany przez inny plugin?");
        }
        return spawned;
    }

    // ─────────────────────── SYNC ENTITIES DO POZYCJI ───────────────

    /**
     * Przesuwa entity do aktualnych pozycji komorek: teleportuje CARRIERY
     * (markery) — pasazer-shulker podaza automatycznie i nie jest odepchniety.
     *
     * UWAGA: marker (i przez to shulker-kolizja) celuje 0.5 bloka POD srodkiem
     * komorki (COLLIDER_Y_OFFSET) — AABB zamknietego shulkera to 1×1×1 od pozycji
     * w górę, wiec wtedy box == dokladnie deska. BlockDisplay (deska) zostaje na
     * srodku komorki.
     *
     * ZRODLO PRAWDY = lista blockOffsets (wektory). Klucze sluza TYLKO jako
     * etykiety lookupu w mapach — pozycja docelowa liczona jest z wektora
     * offsetu, NIGDY z parsowania klucza (parsowanie kluczy lamalo sie przy
     * starych/obcych formatach kluczy i teleportowalo kolizje np. na Y+5000).
     */
    private void syncCells() {
        Vector pos = state.getPosition();

        for (Vector offset : blockOffsets) {
            String key = keyOf(offset);
            ArmorStand carrier = carriers.get(key);
            if (carrier == null || carrier.isDead() || !carrier.isValid()) continue;

            Shulker shulker = colliders.get(key);
            if (shulker != null && shulker.isValid() && !shulker.isDead()
                    && !carrier.getPassengers().contains(shulker)) {
                carrier.addPassenger(shulker);
            }

            // Kotwica kolizji = 0.5 pod srodkiem deski (COLLIDER_Y_OFFSET) —
            // box shulkera (1×1×1 od pozycji w górę) = dokladnie deska.
            RaftEntitySync.moveSmoothly(carrier,
                    pos.getX() + offset.getX(),
                    pos.getY() + offset.getY() + COLLIDER_Y_OFFSET,
                    pos.getZ() + offset.getZ());
        }

        // Ukryte pary wysokich ścian — podążają za swoimi pozycjami (bez displaya)
        for (HiddenPair hp : hiddenPairs.values()) {
            if (hp.carrier == null || hp.carrier.isDead() || !hp.carrier.isValid()) continue;
            RaftEntitySync.moveSmoothly(hp.carrier,
                    pos.getX() + hp.offset.getX(),
                    pos.getY() + hp.offset.getY() + COLLIDER_Y_OFFSET,
                    pos.getZ() + hp.offset.getZ());
        }

        syncAllVisuals();
    }

    /**
     * Sync wszystkich wizuali tratwy: deski (BlockDisplay) + wizual zawartości
     * sieci zbierających (ikona + licznik nad komórkami NET). Wołane z każdego
     * miejsca zmieniającego układ komórek (tick, dodawanie/rozbieranie) — dzięki
     * temu ikona/licznik od razu reagują na złapanie/odbranie przedmiotu i znikają
     * przy rozbiórce sieci.
     */
    private void syncAllVisuals() {
        Map<String, ItemStack> netIcons = new HashMap<>();
        Map<String, Integer> netCounts = new HashMap<>();
        collectNetVisuals(netIcons, netCounts);
        visualRenderer.sync(blockOffsets, cellDataCache, cellTransformCache, netIcons, netCounts);
        // Fallback żagla dla graczy bez moda — ten sam cykl życia, co deski.
        sailFallbackRenderer.sync();
        // Podpory pięter (1 display na podporę, bez kolizji) — ten sam cykl życia.
        supportRenderer.sync();
    }

    /**
     * Zbiera wizualną zawartość sieci zbierających dla renderera:
     *  - ikona = stack najliczniejszego typu w sieci (gracz widzi 1 przedmiot),
     *  - licznik = suma sztuk wszystkich wpisów (np. 7 -> "[7]").
     */
    private void collectNetVisuals(Map<String, ItemStack> icons, Map<String, Integer> counts) {
        for (Vector offset : blockOffsets) {
            if (!isNetCell(offset)) continue;
            String key = keyOf(offset);
            List<ItemStack> contents = cellContents.get(key);
            int total = 0;
            ItemStack best = null;
            int bestAmount = 0;
            if (contents != null) {
                for (ItemStack s : contents) {
                    if (s == null || s.getAmount() <= 0) continue;
                    total += s.getAmount();
                    if (s.getAmount() > bestAmount) {
                        bestAmount = s.getAmount();
                        best = s;
                    }
                }
            }
            if (best != null) icons.put(key, best.clone());
            counts.put(key, total);
        }
    }

    // ─────────────────────── DODAWANIE / USUWANIE BLOKOW ───────────

    public boolean addBlock(Vector offset, Material material) {
        return addCell(offset, material, CellType.FLOOR, null);
    }

    /**
     * Dodaje komórkę budowlaną (Faza 4): typ decyduje o wyglądzie (schody z
     * kierunkiem) i zasadach (RAFT nie da się usunąć). Zwraca false, gdy limit
     * komórek lub miejsce zajęte.
     */
    public boolean addCell(Vector offset, Material material, CellType type, BlockFace facing) {
        return addCell(offset, material, type, facing, 1f, 1);
    }

    // ─────────────────────── PODPORY PIĘTER ────────────────────────

    /**
     * Dodaje podporę piętra w WĘŹLE siatki — narożniku 4 komórek („na końcu
     * 4 bloków"). Węzeł to lokalny offset z połówkami (X/Z = 0.5, 1.5, ...),
     * Y = {@link #SUPPORT_BASE_Y} (wierzch pokładu). Podpora NIE zajmuje
     * komórki siatki i NIE ma kolizji — to wizual, który „trzyma" piętro.
     * Zwraca false, gdy w węźle już stoi podpora albo przekroczono limit.
     */
    public boolean addSupport(Vector node) {
        if (node == null) return false;
        if (supports.size() >= MAX_SUPPORTS) return false;
        for (Vector existing : supports) {
            if (existing.distanceSquared(node) < 1.0e-4) return false;
        }
        supports.add(node.clone());
        syncAllVisuals();
        return true;
    }

    /**
     * Komórka, która runęła po utracie podparcia: lokalny offset (środek
     * komórki) + materiał, z jakiego była — dźwięk i cząsteczki rozbiórki
     * zależą od materiału, a po usunięciu komórki już go nie odczytamy.
     */
    public record CollapsedCell(Vector offset, Material material) {}

    /**
     * Usuwa podporę z węzła (z tolerancją — gracz celuje z bliska, nie w punkt).
     * RAZEM Z PODPORĄ RUNIE piętro, które trzymała — ale po krótkiej chwili
     * ({@link #COLLAPSE_DELAY_TICKS}), żeby gracz zobaczył, że słupka już nie ma:
     * runięcie sprząta komórki bez podparcia, zabiera belki podpiętrza (są
     * pochodną komórek), to, co na nich stało (skrzynie/schody), i wypuszcza
     * materiał do odzyskania. Zwraca false, gdy w węźle nie było podpory.
     */
    public boolean removeSupport(Vector node) {
        if (node == null) return false;
        Iterator<Vector> it = supports.iterator();
        while (it.hasNext()) {
            Vector existing = it.next();
            if (existing.distanceSquared(node) < 0.25) { // 0.5 bloka tolerancji
                it.remove();
                syncAllVisuals();
                scheduleCollapse();
                return true;
            }
        }
        return false;
    }

    // ─────────────────── RUNIĘCIE BEZ PODPARCIA ─────────────────

    /**
     * Opóźnienie runięcia komórek po utracie podparcia (ticki; 0 = od razu).
     * config: raft-support.collapse-delay-ticks. Krótka chwila, w której widać
     * już brak słupka, a platforma jeszcze wisi — potem spada.
     */
    public static int COLLAPSE_DELAY_TICKS = 12;

    /** Czy runięcie jest już zaplanowane (żeby nie planować kilku naraz). */
    private boolean collapseScheduled;
    /** Tratwa zniszczona — zaplanowane runięcie nie ma już czego sprzątać. */
    private boolean destroyed;

    /**
     * Planuje runięcie komórek bez podparcia po {@link #COLLAPSE_DELAY_TICKS}
     * tickach. W chwili wykonania tratwa liczy wszystko OD NOWA — gdy gracz
     * zdąży postawić nową podporę, piętro zostaje (uratowane), a gdy nic już nie
     * wisi, runięcie jest cichym no-opem.
     */
    public void scheduleCollapse() {
        if (destroyed || collapseScheduled) return;
        if (COLLAPSE_DELAY_TICKS <= 0) {
            collapseNow();
            return;
        }
        pl.vd.raft.RaftPlugin raftPlugin = pl.vd.raft.RaftPlugin.getInstance();
        org.bukkit.plugin.Plugin scheduler = raftPlugin != null ? raftPlugin.getParent() : null;
        if (scheduler == null || !scheduler.isEnabled()) {
            collapseNow(); // brak schedulera (np. wyłączanie pluginu) — rób od razu
            return;
        }
        collapseScheduled = true;
        Bukkit.getScheduler().runTaskLater(scheduler, this::collapseNow, COLLAPSE_DELAY_TICKS);
    }

    /**
     * Faktyczne runięcie: komórki bez podparcia znikają, a materiał z każdej z
     * nich WPADA DO WODY jako przedmiot do odzyskania (1× materiał komórki +
     * osobno zawartość sieci/skrzyni, jak przy zwykłej rozbiórce). Każda
     * runięta komórka gra dźwięk rozbijanego bloku + cząsteczki ze swojego
     * materiału (komórka jest już odpięta od tratwy, więc materiał przychodzi
     * w {@link CollapsedCell}).
     */
    private void collapseNow() {
        collapseScheduled = false;
        if (destroyed) return;
        List<CollapsedCell> collapsed = pruneUnsupportedCells();
        if (collapsed.isEmpty() || world == null) return;
        Vector pos = state.getPosition();
        for (CollapsedCell cell : collapsed) {
            Material mat = cell.material() != null ? cell.material() : Material.OAK_PLANKS;
            Location loc = new Location(world,
                    pos.getX() + cell.offset().getX(),
                    pos.getY() + cell.offset().getY(),
                    pos.getZ() + cell.offset().getZ());
            boolean stone = mat == Material.COBBLESTONE || mat == Material.STONE;
            world.playSound(loc, stone ? Sound.BLOCK_STONE_BREAK : Sound.BLOCK_WOOD_BREAK,
                    1.0f, 0.9f);
            world.spawnParticle(Particle.BLOCK, loc, 12, 0.35, 0.25, 0.35,
                    mat.createBlockData());
            world.dropItemNaturally(loc, new ItemStack(mat, 1));
        }
    }

    /**
     * Sprząta komórki, które straciły podparcie — zasada „nic nie wisi
     * w powietrzu":
     *  - komórka PIĘTRA (Y = {@link #floorLevel()}, gdy piętra są włączone)
     *    musi trzymać się podpory ({@link #holdsCell}); bez słupka znika razem
     *    ze swoimi belkami,
     *  - komórka NAD pokładem (Y > 0 — skrzynie, schody) musi mieć komórkę
     *    DOKŁADNIE pod sobą; gdy podstawa zniknęła, ona też znika (zawartość
     *    skrzyni wypada do wody — tak samo, jak przy rozbiórce siekierą),
     *  - pokład (Y = 0) niczego nie wymaga — trzyma go woda.
     * Usuwa warstwami od góry, aż nic już nie wisi (runięcie piętra zabiera
     * wszystko, co na nim stało). Zwraca usunięte komórki z ich materiałami.
     */
    public List<CollapsedCell> pruneUnsupportedCells() {
        List<CollapsedCell> removed = new ArrayList<>();
        double level = floorLevel();
        boolean changed = true;
        while (changed) {
            changed = false;
            List<Vector> doomed = new ArrayList<>();
            for (Vector cell : blockOffsets) {
                String key = keyOf(cell);
                if (cellTypes.get(key) == CellType.RAFT) continue; // korzeń tratwy zostaje
                if (!cellIsAnchored(cell, level, hasCellBelow(cell), isCellSupported(cell))) {
                    doomed.add(cell);
                }
            }
            for (Vector cell : doomed) {
                Material mat = detachCell(cell, false);
                if (mat != null) {
                    removed.add(new CollapsedCell(cell.clone(), mat));
                    changed = true;
                }
            }
        }
        if (!removed.isEmpty()) {
            ensureHiddenPairs();
            syncAllVisuals();
        }
        return removed;
    }

    /**
     * Czysta reguła „nic nie wisi w powietrzu" (bez stanu tratwy — testowalna
     * osobno): komórka piętra musi trzymać się podpory, komórka nad pokładem
     * musi mieć komórkę pod sobą, a pokład trzyma woda.
     */
    public static boolean cellIsAnchored(Vector cell, double floorLevel,
                                         boolean hasCellBelow, boolean supportedBySupport) {
        if (cell == null) return true;
        if (floorLevel > 0 && Math.abs(cell.getY() - floorLevel) < 0.01) {
            return supportedBySupport;
        }
        if (cell.getY() > 0.01) return hasCellBelow;
        return true;
    }

    /** Czy dokładnie pod tą komórką (Y − 1) stoi inna komórka tratwy. */
    private boolean hasCellBelow(Vector cell) {
        for (Vector other : blockOffsets) {
            if (Math.abs(other.getX() - cell.getX()) < 0.01
                    && Math.abs(other.getZ() - cell.getZ()) < 0.01
                    && Math.abs(other.getY() - (cell.getY() - 1.0)) < 0.01) {
                return true;
            }
        }
        return false;
    }

    /** Podpory tratwy (kopie — bezpieczne dla wołających). */
    public List<Vector> getSupports() {
        List<Vector> out = new ArrayList<>(supports.size());
        for (Vector node : supports) out.add(node.clone());
        return out;
    }

    /** Czy w tym węźle stoi już podpora (tolerancja 0.05 bloka). */
    public boolean hasSupportAt(Vector node) {
        if (node == null) return false;
        for (Vector existing : supports) {
            if (Math.abs(existing.getX() - node.getX()) < 0.05
                    && Math.abs(existing.getY() - node.getY()) < 0.05
                    && Math.abs(existing.getZ() - node.getZ()) < 0.05) return true;
        }
        return false;
    }

    /**
     * Poziom PIĘTRA w osi Y (środek komórki platformy): wierzch pokładu +
     * wysokość słupka + pół komórki. Przy domyślnym słupku 2 bloki: 0.5 + 2 +
     * 0.5 = 3. 0 = podpory/piętra wyłączone w configu. Jedno źródło prawdy dla
     * reguły pięter (RaftBuildListener) i belek podpiętrza (RaftSupportRenderer).
     */
    public static double floorLevel() {
        if (SUPPORT_HEIGHT <= 0) return 0;
        return SUPPORT_BASE_Y + SUPPORT_HEIGHT + 0.5;
    }

    /**
     * Czy komórka ma podparcie piętra: w jednym z jej 4 narożników stoi podpora,
     * której słupek DOSIĘGA spodu tej komórki. To jedyna reguła pięter — platformę
     * dostają WYŁĄCZNIE 4 komórki wokół podpory (narożnik testowany wprost),
     * a dalej gracz musi postawić kolejną podporę (węzły co 2 kratki).
     */
    public boolean isCellSupported(Vector cellOffset) {
        if (cellOffset == null || supports.isEmpty()) return false;
        for (Vector node : supports) {
            if (holdsCell(node, cellOffset, SUPPORT_HEIGHT)) return true;
        }
        return false;
    }

    /**
     * Czysta reguła pięter (bez stanu tratwy — testowalna osobno): podpora
     * w węźle {@code node} trzyma komórkę {@code cellOffset} wtedy i tylko
     * wtedy, gdy węzeł jest JEDNYM Z 4 NAROŻNIKÓW tej komórki (czyli komórka
     * leży „wokół podpory" — 4 strony wokół słupka) ORAZ słupek sięga od
     * podstawy do spodu tej komórki. Komórka obok (np. druga kratka od słupka)
     * nie ma węzła w narożniku → jej piętro trzeba oprzeć o własną podporę.
     */
    public static boolean holdsCell(Vector node, Vector cellOffset, double height) {
        if (node == null || cellOffset == null) return false;
        if (Math.abs(Math.abs(node.getX() - cellOffset.getX()) - 0.5) > 0.05) return false;
        if (Math.abs(Math.abs(node.getZ() - cellOffset.getZ()) - 0.5) > 0.05) return false;
        double bottom = cellOffset.getY() - 0.5; // offset = środek komórki
        // Słupek rośnie od podstawy w górę — musi dojść do spodu komórki
        double top = node.getY() + height;
        return node.getY() <= bottom + 1.0e-6 && top >= bottom - 1.0e-6;
    }

    /** Nazwa typu podpory w zapisie/diagnostyce (raft-saves.yml → supports). */
    public static final String SUPPORT_TYPE = "support";

    /**
     * Dodaje komórkę budowlaną (Faza 4): typ decyduje o wyglądzie (schody z
     * kierunkiem, cienka ściana z grubością/wysokością) i zasadach (RAFT nie da
     * się usunąć). Wysoka ściana dostaje ukrytą górną parę kolizji (shulker jest
     * tylko 1×1×1). Zwraca false, gdy limit komórek lub miejsce zajęte.
     */
    public boolean addCell(Vector offset, Material material, CellType type, BlockFace facing,
                           float thickness, int height) {
        if (blockOffsets.size() >= MAX_CELLS) return false;
        for (Vector existing : blockOffsets) {
            if (existing.distanceSquared(offset) < 0.001) return false;
        }
        blockOffsets.add(offset.clone());
        String key = keyOf(offset);
        cellMaterials.put(key, material != null ? material : Material.OAK_PLANKS);
        cellTypes.put(key, type != null ? type : CellType.FLOOR);
        if (isOrientable(type) && facing != null) cellFacings.put(key, facing);
        cellDataCache.put(key, dataFor(material != null ? material : Material.OAK_PLANKS,
                type != null ? type : CellType.FLOOR, facing));

        CellShape shape = new CellShape(Math.max(0.05f, thickness), Math.max(1, height));
        cellShapes.put(key, shape);
        if (type == CellType.WALL && shape.thickness() < 1f) {
            cellTransformCache.put(key, shapeTransform(type,
                    facing != null ? facing : BlockFace.NORTH, shape));
        }

        Vector pos = state.getPosition();
        spawnPair(key,
                pos.getX() + offset.getX(),
                pos.getY() + offset.getY() + COLLIDER_Y_OFFSET,
                pos.getZ() + offset.getZ());
        // Wysoka ściana — dograj ukryte pary na wyższych poziomach (bez duplikatów:
        // pozycja zajęta przez prawdziwą komórkę jest pomijana, a ewentualna stara
        // ukryta para na pozycji tej komórki zostaje posprzątana)
        ensureHiddenPairs();
        syncAllVisuals();
        return true;
    }

    /**
     * Wydłuża istniejącą cienką ścianę o kolejne poziomy (piętrowanie wooden_wall
     * na górze ściany) — zamiast stawiać drugą komórkę w to samo miejsce, co
     * dawałoby nakładające się displaye i dublowane kolizje. Zwraca false, gdy
     * komórka nie jest cienką ścianą albo przekroczono MAX_WALL_HEIGHT.
     */
    public boolean growWall(Vector offset, int newHeight) {
        String key = keyOf(offset);
        CellShape s = cellShapes.get(key);
        if (s == null || cellTypes.get(key) != CellType.WALL) return false;
        if (newHeight <= s.height() || newHeight > MAX_WALL_HEIGHT) return false;
        cellShapes.put(key, new CellShape(s.thickness(), newHeight));
        cellTransformCache.put(key, shapeTransform(CellType.WALL,
                cellFacings.getOrDefault(key, BlockFace.NORTH), cellShapes.get(key)));
        ensureHiddenPairs();
        syncAllVisuals();
        return true;
    }

    public boolean removeBlock(Vector offset) {
        if (offset == null) return false;
        // guardy=true: korzenia tratwy (komórki RAFT) nie da się usunąć, a
        // ostatniej komórki też nie (tratwa by zniknęła).
        if (detachCell(offset, true) == null) return false;
        // Ukryte pary tej ściany znikają; przy okazji dograj pary, które
        // ta komórka mogła wcześniej „przykrywać" (ściana pod spodem)
        ensureHiddenPairs();
        syncAllVisuals();
        // Nic nie może wisieć: to, co stało na rozbitej komórce (np. skrzynia
        // na rozbitej desce albo piętro po zdjęciu podpory), runie po chwili.
        scheduleCollapse();
        return true;
    }

    /**
     * Odpina komórkę od tratwy: siatkę, dane (materiał/typ/kierunek/kształt/
     * uszkodzenie), kolizje (shulker + marker) i zawartość (sieć/skrzynka —
     * wypada do wody). Zwraca materiał usuniętej komórki albo {@code null}, gdy
     * nic nie stało w tym miejscu. Displaye odświeża wołający
     * ({@code syncAllVisuals}), więc po rozbiórce piętra znikają też belki.
     *
     * @param guards true = zasady rozbiórki graczem (nie rusza korzenia tratwy
     *               ani ostatniej komórki); false = sprzątanie wewnętrzne
     *               (runięcie piętra po zdjęciu podpory).
     */
    private Material detachCell(Vector offset, boolean guards) {
        Iterator<Vector> it = blockOffsets.iterator();
        while (it.hasNext()) {
            Vector existing = it.next();
            if (existing.distanceSquared(offset) >= 0.001) continue;
            String key = keyOf(existing);
            Material material = cellMaterials.get(key);
            if (guards && (cellTypes.get(key) == CellType.RAFT || blockOffsets.size() <= 1)) {
                return null;
            }

            it.remove();
            cellMaterials.remove(key);
            cellTypes.remove(key);
            cellFacings.remove(key);
            cellDataCache.remove(key);
            cellShapes.remove(key);
            cellTransformCache.remove(key);
            cellDamage.remove(key);
            // Sieć/skrzynka: zawartość wypada do wody
            dropCellContents(key, existing);

            Shulker shulker = colliders.remove(key);
            if (shulker != null && !shulker.isDead()) shulker.remove();
            ArmorStand carrier = carriers.remove(key);
            if (carrier != null && !carrier.isDead()) carrier.remove();
            return material != null ? material : Material.OAK_PLANKS;
        }
        return null;
    }

    public int getBlockCount() {
        return blockOffsets.size();
    }

    public List<Vector> getBlockOffsets() {
        return Collections.unmodifiableList(new ArrayList<>(blockOffsets));
    }

    /** Materiał deski komórki (do zapisu stanu tratwy). */
    public Material getCellMaterial(Vector offset) {
        Material mat = cellMaterials.get(keyOf(offset));
        return mat != null ? mat : Material.OAK_PLANKS;
    }

    /** Uszkodzenie komórki — ile uderzeń siekierą już przyjęła (0 = pełna wytrzymałość). */
    public int getCellDamage(Vector offset) {
        return cellDamage.getOrDefault(keyOf(offset), 0);
    }

    /** Ustawia uszkodzenie komórki (0 = naprawiona). Zapisywane razem z tratwą. */
    public void setCellDamage(Vector offset, int damage) {
        if (damage <= 0) {
            cellDamage.remove(keyOf(offset));
        } else {
            cellDamage.put(keyOf(offset), damage);
        }
    }

    // ─────────────────── SIEĆ ZBIERAJĄCA (CellType.NET) ────────────

    /** Czy komórka to sieć zbierająca. */
    public boolean isNetCell(Vector offset) {
        return cellTypes.get(keyOf(offset)) == CellType.NET;
    }

    /** Ile sztuk przedmiotów trzyma sieć (suma amountów wszystkich wpisów). */
    public int getNetCount(Vector offset) {
        List<ItemStack> list = cellContents.get(keyOf(offset));
        int total = 0;
        if (list != null) {
            for (ItemStack s : list) total += s.getAmount();
        }
        return total;
    }

    /** Kopia zawartości sieci (wpisy = stacki tego samego typu). */
    public List<ItemStack> getNetContents(Vector offset) {
        List<ItemStack> list = cellContents.get(keyOf(offset));
        if (list == null || list.isEmpty()) return List.of();
        List<ItemStack> copy = new ArrayList<>(list.size());
        for (ItemStack s : list) copy.add(s.clone());
        return copy;
    }

    /**
     * Wkłada stack do sieci (łączy z podobnymi typami, szanuje pojemność
     * NET_CAPACITY sztuk łącznie). Zwraca liczbę sztuk, których NIE udało się
     * zmieścić (0 = wszystko weszło).
     */
    public int storeInNet(Vector offset, ItemStack stack) {
        if (stack == null || stack.getAmount() <= 0 || !isNetCell(offset)) return 0;
        String key = keyOf(offset);
        List<ItemStack> list = cellContents.computeIfAbsent(key, k -> new ArrayList<>());

        int toStore = stack.getAmount();
        // 1) dołącz do istniejących wpisów tego samego typu (do max stacku wpisu)
        for (ItemStack entry : list) {
            if (toStore <= 0) break;
            if (!entry.isSimilar(stack)) continue;
            int space = Math.min(entry.getMaxStackSize(), NET_CAPACITY - totalAmount(list))
                    - entry.getAmount();
            if (space <= 0) continue;
            int add = Math.min(space, toStore);
            entry.setAmount(entry.getAmount() + add);
            toStore -= add;
        }
        // 2) reszta jako nowe wpisy (limit: pojemność sieci i max stack przedmiotu)
        while (toStore > 0) {
            int space = NET_CAPACITY - totalAmount(list);
            if (space <= 0) break;
            int add = Math.min(space, Math.min(toStore, stack.getMaxStackSize()));
            ItemStack copy = stack.clone();
            copy.setAmount(add);
            list.add(copy);
            toStore -= add;
        }
        if (list.isEmpty()) cellContents.remove(key);
        return toStore;
    }

    /**
     * Wyjmuje wpis (stack) o danym indeksie z sieci. Zwraca stack albo null,
     * gdy indeks nie istnieje. Indeksy = kolejność wpisów w getNetContents.
     */
    public ItemStack takeFromNet(Vector offset, int index) {
        String key = keyOf(offset);
        List<ItemStack> list = cellContents.get(key);
        if (list == null || index < 0 || index >= list.size()) return null;
        ItemStack taken = list.remove(index);
        if (list.isEmpty()) cellContents.remove(key);
        return taken;
    }

    private static int totalAmount(List<ItemStack> list) {
        int total = 0;
        for (ItemStack s : list) total += s.getAmount();
        return total;
    }

    // ───────────── SKRZYNKI (CellType.CHEST_18/36/54) ──────────

    /** Czy komórka jest skrzynką z GUI (chest_small/medium/large). */
    public boolean isChestCell(Vector offset) {
        CellType t = cellTypes.get(keyOf(offset));
        return t != null && t.isChest();
    }

    /** Kopia zawartości skrzynki (wpisy = stacki; do pojemności typu). */
    public List<ItemStack> getChestContents(Vector offset) {
        List<ItemStack> list = cellContents.get(keyOf(offset));
        if (list == null || list.isEmpty()) return List.of();
        List<ItemStack> copy = new ArrayList<>(list.size());
        for (ItemStack s : list) copy.add(s.clone());
        return copy;
    }

    /**
     * Wkłada stack do skrzynki (łączy z podobnymi typami; pojemność = liczba
     * slotów typu skrzynki — tyle maksymalnie wpisów). Zwraca liczbę sztuk,
     * których NIE udało się zmieścić (0 = wszystko weszło).
     */
    public int storeInChest(Vector offset, ItemStack stack) {
        if (stack == null || stack.getAmount() <= 0) return 0;
        String key = keyOf(offset);
        CellType type = cellTypes.get(key);
        if (type == null || !type.isChest()) return stack.getAmount();
        int capacity = type.chestSlots();
        List<ItemStack> list = cellContents.computeIfAbsent(key, k -> new ArrayList<>());

        int toStore = stack.getAmount();
        // 1) dołącz do istniejących wpisów tego samego typu (do max stacku)
        for (ItemStack entry : list) {
            if (toStore <= 0) break;
            if (!entry.isSimilar(stack)) continue;
            int space = entry.getMaxStackSize() - entry.getAmount();
            if (space <= 0) continue;
            int add = Math.min(space, toStore);
            entry.setAmount(entry.getAmount() + add);
            toStore -= add;
        }
        // 2) reszta jako nowe wpisy (limit: sloty skrzynki, każdy do max stacku)
        while (toStore > 0) {
            if (list.size() >= capacity) break;
            int add = Math.min(toStore, stack.getMaxStackSize());
            ItemStack copy = stack.clone();
            copy.setAmount(add);
            list.add(copy);
            toStore -= add;
        }
        if (list.isEmpty()) cellContents.remove(key);
        return toStore;
    }

    /**
     * Wyjmuje wpis (stack) o danym indeksie ze skrzynki. Zwraca stack albo
     * null, gdy indeks nie istnieje. Indeksy = kolejność w getChestContents.
     */
    public ItemStack takeFromChest(Vector offset, int index) {
        String key = keyOf(offset);
        CellType type = cellTypes.get(key);
        if (type == null || !type.isChest()) return null;
        List<ItemStack> list = cellContents.get(key);
        if (list == null || index < 0 || index >= list.size()) return null;
        ItemStack taken = list.remove(index);
        if (list.isEmpty()) cellContents.remove(key);
        return taken;
    }

    /**
     * Ustawia zawartość skrzynki na stan z otwartego GUI (lista = niepuste sloty
     * GUI w kolejności). Puste sloty są pomijane, bo wpisy w cellContents muszą
     * być gęste (null w liście wysypywał `isSimilar` przy dokładaniu i
     * `dropCellContents` przy rozbiórce). Stacki większe niż max stack są
     * dzielone, a nadmiar ponad pojemność typu wraca jako lista do wywołującego
     * (w praktyce puste — GUI ma dokładnie tyle slotów, ile pojemność skrzynki).
     */
    public List<ItemStack> setChestContents(Vector offset, List<ItemStack> slots) {
        String key = keyOf(offset);
        CellType type = cellTypes.get(key);
        if (type == null || !type.isChest()) return List.of();
        int capacity = type.chestSlots();
        List<ItemStack> compact = new ArrayList<>();
        List<ItemStack> overflow = new ArrayList<>();
        if (slots != null) {
            for (ItemStack s : slots) {
                if (s == null || s.getType().isAir() || s.getAmount() <= 0) continue;
                ItemStack copy = s.clone();
                while (copy.getAmount() > copy.getMaxStackSize()) {
                    ItemStack part = copy.clone();
                    part.setAmount(copy.getMaxStackSize());
                    copy.setAmount(copy.getAmount() - copy.getMaxStackSize());
                    addChestEntry(compact, overflow, part, capacity);
                }
                addChestEntry(compact, overflow, copy, capacity);
            }
        }
        if (compact.isEmpty()) {
            cellContents.remove(key);
        } else {
            cellContents.put(key, compact);
        }
        return overflow;
    }

    /** Dodaje wpis do skrzynki albo do nadmiaru, gdy brakuje miejsca. */
    private static void addChestEntry(List<ItemStack> compact, List<ItemStack> overflow,
                                      ItemStack stack, int capacity) {
        if (compact.size() < capacity) {
            compact.add(stack);
        } else {
            overflow.add(stack);
        }
    }

    /**
     * Wyrzuca zawartość komórki (sieć lub skrzynka) do świata
     * (rozbiórka/zniszczenie komórki).
     */
    private void dropCellContents(String key, Vector offset) {
        List<ItemStack> list = cellContents.remove(key);
        if (list == null || list.isEmpty()) return;
        Vector pos = state.getPosition();
        Location loc = new Location(world,
                pos.getX() + offset.getX(),
                pos.getY() + offset.getY(),
                pos.getZ() + offset.getZ());
        for (ItemStack s : list) {
            world.dropItemNaturally(loc, s);
        }
    }

    /** Typ komórki (tratwa/podłoga/ściana/schody) lub null, gdy puste. */
    public CellType getCellType(Vector offset) {
        return cellTypes.get(keyOf(offset));
    }

    /** Kierunek komórki (schody/ściany) lub null. */
    public BlockFace getCellFacing(Vector offset) {
        return cellFacings.get(keyOf(offset));
    }

    /** Grubość komórki w blokach (1 = pełna; < 1 = płaska ściana). */
    public float getCellThickness(Vector offset) {
        CellShape s = cellShapes.get(keyOf(offset));
        return s == null ? 1f : s.thickness();
    }

    /** Wysokość komórki w blokach (1 = zwykła; 2 = wysoka ściana). */
    public int getCellHeight(Vector offset) {
        CellShape s = cellShapes.get(keyOf(offset));
        return s == null ? 1 : s.height();
    }

    /** Obrys pokładu w świecie (XZ) — publiczny dla sprzątania orphanów. */
    public double[] deckBoundsWorldXZ() {
        return deckBoundsXZ();
    }

    // ─────────────────────── STEROWANIE ───────────────────────────

    public void setDirection(Vector d) {
        if (d == null || d.lengthSquared() < 1e-6) return;
        physicsEngine.setGlobalCurrent(d.clone().normalize());
    }

    public void setMoving(boolean m) {
        this.isMoving = m;
        if (!m) {
            state.setLinearVelocity(new Vector(0, 0, 0));
        }
    }

    // ─────────────────────── CLEANUP ───────────────────────────────

    public void destroy() {
        destroyed = true; // zaplanowane runięcie nie ma już czego sprzątać
        // Sieci/skrzynki: zawartość wypada do wody (rozbiórka całej tratwy)
        for (Map.Entry<String, List<ItemStack>> e : List.copyOf(cellContents.entrySet())) {
            Vector off = offsetFromKey(e.getKey());
            if (off != null) dropCellContents(e.getKey(), off);
        }
        for (ArmorStand carrier : carriers.values()) {
            if (carrier != null && !carrier.isDead()) carrier.remove();
        }
        carriers.clear();
        for (Shulker shulker : colliders.values()) {
            if (shulker != null && !shulker.isDead()) shulker.remove();
        }
        colliders.clear();
        // Ukryte pary wysokich ścian
        for (HiddenPair hp : hiddenPairs.values()) {
            if (hp.carrier != null && !hp.carrier.isDead()) hp.carrier.remove();
            if (hp.shulker != null && !hp.shulker.isDead()) hp.shulker.remove();
        }
        hiddenPairs.clear();
        visualRenderer.cleanup();
        sailFallbackRenderer.cleanup();
        supportRenderer.cleanup();
        RaftManager.getInstance().unregisterRaft(this);
    }

    // ─────────────────── KOMPONENTY (zgodnosc API) ─────────────────

    public void addDeck() {
        state.addComponent(RaftState.DECK, new RaftState.Component(RaftState.DECK, new Vector(0, 0.5, 0)));
    }

    /**
     * Stawia maszt z żaglem na wskazanej komórce pokładu (komponenty MAST + SAIL,
     * lokalny offset = komórka, Y jak w konwencji komponentów: pokład = 0.5).
     * Jeden żagiel na tratwę — gdy żagiel już stoi, zwraca false (nie nadpisujemy).
     * Maszt staje tylko na PEŁNYM pokładzie (RAFT/FLOOR), nie na ścianie, sieci
     * ani skrzyni. Zwraca true, gdy komponenty zostały dodane.
     */
    public boolean mountSail(Vector cellOffset) {
        if (cellOffset == null || state.hasSail() || state.hasMast()) return false;
        CellType type = getCellType(cellOffset);
        if (type != CellType.FLOOR && type != CellType.RAFT) return false;
        double x = cellOffset.getX();
        double z = cellOffset.getZ();
        state.addComponent(RaftState.MAST,
                new RaftState.Component(RaftState.MAST, new Vector(x, MAST_BASE_Y, z)));
        state.addComponent(RaftState.SAIL,
                new RaftState.Component(RaftState.SAIL, new Vector(x, SAIL_PLANE_Y, z)));
        state.setSailOpen(false); // świeżo postawiony żagiel jest zwinięty
        syncAllVisuals(); // fallback (gracze bez moda) widoczny od razu, bez czekania na tick
        return true;
    }

    /**
     * Demontuje maszt z żaglem (komponenty MAST + SAIL). Tratwa wraca na napęd
     * z prądu wody. Zwraca true, gdy było co zdejmować.
     */
    public boolean demountSail() {
        boolean had = state.hasSail() || state.hasMast();
        state.removeComponent(RaftState.SAIL);
        state.removeComponent(RaftState.MAST);
        state.setSailOpen(false);
        syncAllVisuals(); // zdejmij też serwerowy fallback żagla
        return had;
    }

    /**
     * Komórka pokładu, na której stoi żagiel (offset komórki), albo null, gdy
     * żagla nie ma. Komponent SAIL trzyma lokalny X/Z komórki — dopasowujemy je
     * do listy komórek, więc klik w żagiel rozpoznajemy bez modelu geometrii.
     */
    public Vector getSailCell() {
        RaftState.Component sail = state.getComponent(RaftState.SAIL);
        if (sail == null) return null;
        Vector off = sail.getLocalOffset();
        for (Vector cell : blockOffsets) {
            if (Math.abs(cell.getX() - off.getX()) < 0.01
                    && Math.abs(cell.getZ() - off.getZ()) < 0.01) {
                return cell.clone();
            }
        }
        return null;
    }

    /**
     * Światowa pozycja PODSTAWY MASZTU (kotwica modelu żagla) albo null, gdy
     * tratwa nie ma żagla. Z tej pozycji rysuje się model: origin = podstawa
     * masztu, oś Y w górę, obrót z yaw tratwy (patrz RaftSailPacketSender).
     */
    public Vector getSailAnchorWorld() {
        RaftState.Component mast = state.getComponent(RaftState.MAST);
        if (mast == null) return null;
        Vector off = mast.getLocalOffset();
        Vector pos = state.getPosition();
        return new Vector(pos.getX() + off.getX(), pos.getY() + off.getY(), pos.getZ() + off.getZ());
    }

    /** Kąt tratwy w stopniach (obrót modelu żagla w świecie). */
    public double getYawDegrees() {
        return Math.toDegrees(state.getYaw());
    }

    /**
     * Najbliższa środkowi tratwy komórka PEŁNEGO pokładu (RAFT/FLOOR) albo null.
     * Używane przez ścieżkę diagnostyczną (/raft zagiel otwarty) — postawienie
     * żagla z przedmiotu wskazuje komórkę raycastem (SailPlaceListener).
     */
    private Vector centralDeckCell() {
        Vector best = null;
        double bestD = Double.MAX_VALUE;
        for (Vector off : blockOffsets) {
            CellType type = getCellType(off);
            if (type != CellType.FLOOR && type != CellType.RAFT) continue;
            double d = off.getX() * off.getX() + off.getZ() * off.getZ();
            if (d < bestD) {
                bestD = d;
                best = off;
            }
        }
        return best;
    }

    /** Czy tratwa ma żagiel (komponent SAIL). */
    public boolean hasSail() {
        return state.hasSail();
    }

    /** Czy żagiel jest rozwinięty (otwarty). */
    public boolean isSailOpen() {
        return state.isSailOpen();
    }

    /** Czy tratwa płynie pod żaglem (ma żagiel i jest on rozwinięty) → napęd z wiatru. */
    public boolean isSailing() {
        return state.isSailing();
    }

    /**
     * Rozwija (true) albo zwija (false) żagiel. Stan żagla decyduje, czy silnik
     * fizyki bierze napęd z wiatru, czy z prądu wody (RaftPhysicsEngine).
     * Ścieżka diagnostyczna: rozwinięcie tratwy BEZ żagla stawia go na środkowej
     * komórce pokładu (normalnie robi to SailPlaceListener z przedmiotu CMD 4005).
     */
    public void setSailOpen(boolean open) {
        if (open && !state.hasSail()) {
            Vector cell = centralDeckCell();
            if (cell != null) mountSail(cell);
        }
        state.setSailOpen(open);
        syncAllVisuals(); // płótno fallbacku rozkłada się/zwija razem ze stanem
    }

    // ─────────────────────── GETTERY ──────────────────────────────

    public UUID getRaftId() {
        return raftId;
    }

    public UUID getOwner() {
        return owner;
    }

    public Location getCenter() {
        Vector pos = state.getPosition();
        return new Location(world, pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * Kierunek, w którym tratwa faktycznie dryfuje — czyli kierunek NAPĘDU:
     * żagiel rozwinięty → z wiatrem, żagiel zwinięty/brak → z prądem wody.
     * Używane m.in. do pola śmieci przed dziobem i diagnostyki /raft debug.
     */
    public Vector getDirection() {
        Vector drive = physicsEngine.getDriveVector();
        if (drive == null || drive.lengthSquared() < 1e-6) return new Vector(0, 0, 0);
        return drive.clone().normalize();
    }

    /** Aktualna predkosc pozioma tratwy (bloki/sekunde). */
    public double getSpeed() {
        Vector vel = state.getLinearVelocity();
        return Math.sqrt(vel.getX() * vel.getX() + vel.getZ() * vel.getZ());
    }

    public boolean isMoving() {
        return isMoving;
    }

    public RaftState getState() {
        return state;
    }

    public RaftPhysicsEngine getPhysicsEngine() {
        return physicsEngine;
    }

    public RaftMovementController getMovementController() {
        return movementController;
    }

    /**
     * Informacje diagnostyczne o stanie tratwy (do /raft debug).
     */
    public String debugInfo() {
        Vector pos = state.getPosition();
        Vector vel = state.getLinearVelocity();
        Vector current = physicsEngine.getGlobalCurrent();
        Vector wind = physicsEngine.getGlobalWind();
        Vector drive = physicsEngine.getDriveVector();
        StringBuilder sb = new StringBuilder();
        sb.append("§7build: §a").append(BUILD).append("\n")
                .append("§7tryb LOD: §f").append(tickMode)
                .append(" §7| najbliższy gracz: §f").append(nearestPlayerLabel())
                .append(" §7| pełny tick < §f").append((int) FULL_TICK_RANGE)
                .append(" §7m, sync co §f").append(FAR_SYNC_INTERVAL)
                .append(" §7ticki do §f").append((int) LOD_OBSERVE_RANGE)
                .append(" §7m, freeze powyżej\n")
                .append("§7ID: §f").append(raftId.toString().substring(0, 8))
                .append(" §7| komórki: §f").append(blockOffsets.size())
                .append(" §7| deski(display): §f").append(visualRenderer.getDisplayCount())
                .append(" §7| typy: §f").append(cellTypeSummary())
                .append(" §7| pozycja: §f(").append(String.format("%.2f, %.2f, %.2f", pos.getX(), pos.getY(), pos.getZ())).append(")\n")
                .append("§7prąd: §f(").append(String.format("%.2f, %.2f", current.getX(), current.getZ()))
                .append(") §7| żagiel: §f")
                .append(!state.hasSail() ? "brak" : (state.isSailOpen() ? "rozwinięty" : "zwinięty"))
                .append(" §7| wiatr: §f(").append(String.format("%.2f, %.2f", wind.getX(), wind.getZ()))
                .append(") §7| napęd: §f(").append(String.format("%.2f, %.2f", drive.getX(), drive.getZ()))
                .append(") §7| prędkość: §f").append(String.format("%.2f b/s", getSpeed()))
                .append(" §7| vel: §f(").append(String.format("%.3f, %.3f", vel.getX(), vel.getZ()))
                .append(" §7| ruch/tick: §f").append(String.format("%.4f bloka", lastTickDisplacement))
                .append(" §7(≈ ").append(String.format("%.1f cm)\n", lastTickDisplacement * 100));

        int alive = 0, dead = 0;
        int i = 0;
        Set<Integer> uniqueShulkerIds = new HashSet<>();
        for (Map.Entry<String, Shulker> entry : colliders.entrySet()) {
            Shulker s = entry.getValue();
            ArmorStand carrier = carriers.get(entry.getKey());
            i++;
            if (s == null || s.isDead() || !s.isValid()) {
                dead++;
                sb.append("§c  shulker ").append(i).append(" [").append(entry.getKey()).append("]: MARTWY/brak\n");
                continue;
            }
            alive++;
            uniqueShulkerIds.add(s.getEntityId());
            Vector offset = offsetFromKey(entry.getKey());
            double ex = pos.getX() + (offset == null ? 0 : offset.getX());
            // Kolizja stoi 0.5 pod srodkiem deski (kotwica COLLIDER_Y_OFFSET)
            double ey = pos.getY() + (offset == null ? 0 : offset.getY()) + COLLIDER_Y_OFFSET;
            double ez = pos.getZ() + (offset == null ? 0 : offset.getZ());
            Location loc = s.getLocation();
            boolean okPos = Math.abs(loc.getX() - ex) < 0.2 && Math.abs(loc.getY() - ey) < 0.2 && Math.abs(loc.getZ() - ez) < 0.2;
            sb.append("  §fshulker ").append(i)
                    .append(" §7[#").append(s.getEntityId()).append("]")
                    .append(" §7na §f(").append(String.format("%.2f, %.2f, %.2f", loc.getX(), loc.getY(), loc.getZ()))
                    .append(okPos ? ") §aOK" : ") §c≠ cel (" + String.format("%.2f, %.2f, %.2f", ex, ey, ez) + ")")
                    .append(" §7| marker: §f").append(carrier != null && carrier.isValid() && !carrier.isDead()
                            ? "ok" : "§cBRAK")
                    .append(" §7| pasażer: §f").append(carrier != null && carrier.getPassengers().contains(s) ? "tak" : "§cNIE")
                    .append("\n");
        }
        sb.append("§7kolizje: §a").append(alive).append(" §7żywe / §c").append(dead).append(" §7martwe")
                .append(" §7| unikalne shulkery: §a").append(uniqueShulkerIds.size())
                .append(" §7(musi być §a").append(blockOffsets.size())
                .append("§7 — jeden na komórkę)\n");

        // Czy jakikolwiek gracz w poblizu koliduje z naszymi shulkerami?
        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(new Location(world, pos.getX(), pos.getY(), pos.getZ())) > 36) continue;
            boolean overlapping = false;
            for (Shulker s : colliders.values()) {
                if (s == null || s.isDead() || !s.isValid()) continue;
                if (p.getBoundingBox().overlaps(s.getBoundingBox())) {
                    overlapping = true;
                    break;
                }
            }
            sb.append("§7gracz §f").append(p.getName())
                    .append(" §7na §f(").append(String.format("%.1f, %.1f, %.1f", p.getLocation().getX(), p.getLocation().getY(), p.getLocation().getZ()))
                    .append(") §7koliduje z shulkerem: §f").append(overlapping ? "§aTAK" : "§cNIE")
                    .append("\n");
        }
        return sb.toString();
    }

    /** Zestawienie typów komórek do /raft debug (np. „5T/12P/8Ś/3S"). */
    private String cellTypeSummary() {
        int raftCells = 0, floorCells = 0, wallCells = 0, stairsCells = 0, netCells = 0, chestCells = 0;
        for (CellType t : cellTypes.values()) {
            switch (t) {
                case RAFT -> raftCells++;
                case FLOOR -> floorCells++;
                case WALL -> wallCells++;
                case STAIRS -> stairsCells++;
                case NET -> netCells++;
                case CHEST_18, CHEST_36, CHEST_54 -> chestCells++;
            }
        }
        return raftCells + "T/" + floorCells + "P/" + wallCells + "Ś/" + stairsCells + "S/" + netCells + "N/" + chestCells + "K";
    }

    // ─────────────────────── POMOCNICZE ─────────────────────────────

    /**
     * Klucz komorki — kanoniczny format "x,y,z" (CZYSTE LICZBY, bez formatowania
     * locale! String.format(%.4f) na polskiej maszynie daje przecinek dziesietny
     * (\"0,5000\"), co lamalo parsowanie offsetFromKey i teleportowalo kolizje
     * do kosmicznych wspolrzednych). Komorki leza na siatce 0.5 — mnozymy przez
     * 10000 i zaokraglamy, wiec klucz jest zawsze jednoznaczny.
     */
    public static String keyOf(Vector offset) {
        long x = Math.round(offset.getX() * 10000);
        long y = Math.round(offset.getY() * 10000);
        long z = Math.round(offset.getZ() * 10000);
        return x + "," + y + "," + z;
    }

    private Vector offsetFromKey(String key) {
        try {
            String[] parts = key.split(",");
            if (parts.length < 3) return null;
            return new Vector(Long.parseLong(parts[0]) / 10000.0,
                    Long.parseLong(parts[1]) / 10000.0,
                    Long.parseLong(parts[2]) / 10000.0);
        } catch (Exception e) {
            return null;
        }
    }
}
