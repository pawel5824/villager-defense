package pl.vd.raft.world;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import pl.vd.raft.RaftPlugin;
import pl.vd.raft.entity.RaftEntity;
import pl.vd.raft.entity.RaftManager;
import pl.vd.raft.items.ItemManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;    /**
     * Pole śmieci oceanicznych — pływających przedmiotów i beczek PRZED tratwą,
     * do złapania przez sieć zbierającą lub ręcznie (beczki — prawym klikiem).
     *
     * Zasady:
     *  - Obsługa TYLKO dla tratw, przy których są gracze (tryb FULL/FAR).
     *  - Punkt odniesienia = najbliższy blok tratwy W KIERUNKU dryfu (kierunek
     *    prądu wody, raft.getDirection()) — od tego bloku liczymy odległość.
     *  - Manager UTRZYMUJE przed dziobem 13–28 pojedynczych obiektów (dolny i
     *    górny pułap konfigurowalne: min/max-items-ahead). Gdy jest ich mniej
     *    niż dolny pułap, dosypuje POJEDYNCZE przedmioty (1 sztuka na spawn),
     *    każdy w losowym miejscu okna przed dziobem: raz z lewej, kilka kratek
     *    dalej z prawej, potem na środku — bez sztywnych rzędów, jak swobodnie
     *    dryfujące śmieci.
     *  - Cel uzupełnienia losowany między min a max, więc ilość bywa różna
     *    (nie zawsze 28), ale nigdy nie spada poniżej dolnego pułapu i nigdy
     *    nie przekracza górnego.
     *  - Formy: pojedyncze śmieci (liście / deski / plastik — custom itemy) oraz
     *    BECZKA (pływający blok) z własnym lootem (każdy przedmiot ma % szansy).
     *  - Beczka: prawy klik rozbija ją i wypada loot (custom itemy). Sieć łapie
     *    tylko małe przedmioty — beczki to osobne obiekty (przyszły „hak").
     *  - Sprzątanie: przepłynięte przedmioty (daleko za dziobem) i wyrzucone poza
     *    korytarz dryfu znikają, więc liczba nie rośnie w nieskończoność;
     *    beczki dodatkowo znikają po BARREL_LIFETIME_MS, itemy mają waniliowy
     *    despawn.
     *  - Ustawienia: config.yml → raft-debris (loadConfig; start / /def reload).
     */
public class OceanDebrisManager implements Listener {

    /** Tag przedmiotów-śmieci (liczenie + ewentualne czyszczenie). */
    public static final String TAG_DEBRIS = "ocean_debris";
    /** Tag beczek (interakcja, liczenie). */
    public static final String TAG_BARREL = "ocean_barrel";
    /** Tag DUŻEJ beczki (custom model, CMD 4003) — dodatkowy do TAG_BARREL. */
    public static final String TAG_BIG_BARREL = "ocean_big_barrel";
    /** Tag wizualnego „ducha" (ItemDisplay) przedmiotu-śmiecia. */
    private static final String TAG_DEBRIS_GHOST = "ocean_debris_ghost";

    // ─── Konfiguracja — stałe techniczne (reszta w config.yml, raft-debris) ───

    /** Co ile ticków sprawdzamy/uzupełniamy pole śmieci (40 = 2 s). */
    private static final int CHECK_INTERVAL_TICKS = 40;
    /** Co ile ticków kołyszemy beczkami (2 = 10 Hz). */
    private static final int BOB_INTERVAL_TICKS = 2;
    /** Co ile ticków odświeżamy wizual ItemDisplay pływających śmieci. */
    private static final int GHOST_INTERVAL_TICKS = 2;
    /** Żywotność beczki (ms) — znika, gdy nikt jej nie rozbije. */
    private static final long BARREL_LIFETIME_MS = 10 * 60_000L;
    /** Zasięg zebrania beczki wejściem w nią (XZ od środka beczki). */
    private static final double BARREL_WALK_PICKUP_XZ = 0.9;
    /** Zasięg pionowy zebrania beczki wejściem w nią (od środka beczki). */
    private static final double BARREL_WALK_PICKUP_Y = 2.0;
    /** Zasięg XZ zebrania DUŻEJ beczki wejściem w nią (jest ~1.75× większa od zwykłej). */
    private static final double BIG_BARREL_WALK_PICKUP_XZ = 1.5;
    /** Zasięg pionowy zebrania DUŻEJ beczki wejściem w nią. */
    private static final double BIG_BARREL_WALK_PICKUP_Y = 2.5;

    // ─── Ustawienia z config.yml (sekcja raft-debris) — wczytuje loadConfig() ───

    /** Czy generować pole śmieci przed tratwami. */
    private boolean debrisEnabled = true;
    /** Dolny pułap: gdy przed dziobem jest mniej — dosypujemy pojedyncze sztuki. */
    private int minItemsAhead = 13;
    /** Górny pułap: nigdy nie więcej niż tyle obiektów przed dziobem. */
    private int maxItemsAhead = 28;
    /** Odległość najbliższego spawnu od dziobu (bloki) — jak w oryginale. */
    private double nearestDist = 40.0;
    /** Odległość najdalszego spawnu od dziobu (bloki) — jak w oryginale. */
    private double farthestDist = 70.0;
    /** Maks. boczne rozrzucenie od osi dryfu (bloki; „raz lewa, raz prawa"). */
    private double lateralSpread = 14.0;
    /** Szansa, że pojedynczy obiekt to beczka zamiast przedmiotu. */
    private double barrelChance = 0.09;
    /** Szansa, że pojedynczy obiekt to DUŻA beczka (custom model) zamiast przedmiotu. */
    private double bigBarrelChance = 0.015;
    /** Obiekty dalej niż tyle bloków ZA dziobem znikają (nie zaśmiecają oceanu). */
    private double behindCleanupDist = 25.0;
    /** Maks. pojedynczych sztuk dosypanych w jednym sprawdzeniu (co 2 s). */
    private int maxItemsPerFill = 6;

    /** Wagi pojedynczych śmieci (suma 100). */
    private static final Map<String, Integer> DEBRIS_WEIGHTS = Map.of(
            "plank", 30,
            "leaves", 30,
            "plastic", 25,
            "odpadki", 15
    );

    /**
     * Loot beczki: każdy wpis to custom item (custom()) albo waniliowy materiał
     * (vanilla()) z MAPĄ ilości: liczba sztuk -> % szansy (suma % = całkowita
     * szansa na wypadnięcie; przy dropie ilość losowana wg wag). Sieć i tak
     * łapie każdy przedmiot — nazwa (PL/EN) nie ma znaczenia.
     */
    private static final List<LootEntry> BARREL_LOOT = List.of(
            LootEntry.custom("plank", 40.0, Map.of(1, 26.0, 2, 33.0, 3, 22.0, 4, 8.0, 5, 2.0, 6, 0.7)),
            LootEntry.custom("leaves", 33.0, Map.of(1, 40.0, 2, 28.0, 3, 15.0, 4, 7.0, 5, 1.0, 6, 0.3)),
            LootEntry.custom("plastic", 27.0, Map.of(1, 38.0, 2, 21.0, 3, 10.0, 4, 3.0, 5, 0.5)),
            LootEntry.custom("rope", 8.0, Map.of(1, 25.0, 2, 12.0, 3, 2.0, 4, 0.8)),
            LootEntry.custom("stone", 18.0, Map.of(1, 25.0, 2, 11.0, 3, 4.0, 4, 0.6)),
            LootEntry.custom("potato", 6.0, Map.of(1, 25.0, 2, 12.0, 3, 2.0, 4, 0.8)),

            LootEntry.custom("beetroot", 6.0, Map.of(1, 25.0, 2, 12.0, 3, 2.0, 4, 0.8)),

            LootEntry.custom("coconut", 5.0, Map.of(1, 25.0, 2, 12.0, 3, 2.0, 4, 0.8)),
            LootEntry.custom("odpadki", 23.0, Map.of(1, 36.0, 2, 20.0, 3, 8.0, 4, 2.0, 5, 0.4))
    );
    /** Maks. liczba przedmiotów z jednej beczki. */
    private static final int MAX_BARREL_DROPS = 5;

    /**
     * Loot DUŻEJ beczki — NA RAZIE PUSTY (użytkownik poda tabelę później).
     * Format identyczny jak BARREL_LOOT: LootEntry.custom("id", %szansy,
     * Map<ilość, %>) albo LootEntry.vanilla(material, Map<ilość, %>).
     */
    private static final List<LootEntry> BIG_BARREL_LOOT = List.of();
    /** Maks. liczba przedmiotów z jednej DUŻEJ beczki. */
    private static final int MAX_BIG_BARREL_DROPS = 8;

    /** Pozycja + wiek beczki (do kołysania i sprzątania). */
    private static final class Barrel {
        final UUID entityId;
        final World world;
        final double anchorY;
        final double x, z;
        final long spawnedAt;
        final double phase;
        /** Duża beczka (custom model)? */
        final boolean big;

        Barrel(UUID entityId, World world, double x, double y, double z, boolean big) {
            this.entityId = entityId;
            this.world = world;
            this.anchorY = y;
            this.x = x;
            this.z = z;
            this.spawnedAt = System.currentTimeMillis();
            this.phase = Math.random() * Math.PI * 2;
            this.big = big;
        }
    }

    /** Pozycja w lootcie beczki: custom item (customId) albo waniliowy materiał
     *  (material) + mapa „ilość sztuk -> % szansy" (suma % = całkowita szansa
     *  na wypadnięcie; przy dropie ilość losowana wg tych wag). */
    private record LootEntry(String customId, Material material, Double chancePercent,
                             Map<Integer, Double> amounts) {
        static LootEntry custom(String id, Map<Integer, Double> amounts) {
            // brama = suma wag ilości (jak dotychczas)
            return new LootEntry(id, null, null, amounts);
        }

        static LootEntry custom(String id, double basePct, Map<Integer, Double> amounts) {
            // osobna szansa bazowa na wypadnięcie + rozkład ilości przy dropie
            return new LootEntry(id, null, basePct, amounts);
        }

        static LootEntry vanilla(Material mat, Map<Integer, Double> amounts) {
            return new LootEntry(null, mat, null, amounts);
        }

        /** Losuje wynik: 0 = przedmiot nie wypadł; >0 = ile sztuk wypada. */
        int rollAmount() {
            double totalWeight = 0;
            for (double w : amounts.values()) totalWeight += w;
            double gate = chancePercent != null ? chancePercent : totalWeight;
            if (Math.random() * 100.0 >= gate) return 0; // nie wypadł
            double pick = Math.random() * totalWeight;
            double cumulative = 0;
            for (Map.Entry<Integer, Double> e : amounts.entrySet()) {
                cumulative += e.getValue();
                if (pick < cumulative) return e.getKey();
            }
            return amounts.keySet().iterator().next();
        }
    }

    private final RaftPlugin plugin;
    /** entityId beczki -> dane (do kołysania / sprzątania / rozbijania). */
    private final Map<UUID, Barrel> barrels = new HashMap<>();
    /** entityId DUŻEJ beczki -> dane (osobny rejestr, ten sam cykl życia). */
    private final Map<UUID, Barrel> bigBarrels = new HashMap<>();
    /** entityId przedmiotu-śmiecia (Item) -> jego wizual ItemDisplay (duży zasięg). */
    private final Map<Integer, ItemDisplay> debrisGhosts = new HashMap<>();

    public OceanDebrisManager(RaftPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    /**
     * Wczytuje ustawienia pola śmieci z configu pluginu (sekcja raft-debris):
     * ile przedmiotów utrzymywać przed dziobem, odstępy rzędów, linie, szansę
     * na beczkę itd. Wołane przy starcie oraz po /def reload — zmiany w
     * config.yml działają bez rekompilacji.
     */
    public void loadConfig() {
        try {
            org.bukkit.configuration.file.FileConfiguration cfg = plugin.getConfig();
            org.bukkit.configuration.ConfigurationSection s = cfg.getConfigurationSection("raft-debris");
            if (s == null) return; // brak sekcji — wartości domyślne

            debrisEnabled = s.getBoolean("enabled", debrisEnabled);
            minItemsAhead = Math.max(1, Math.min(80, s.getInt("min-items-ahead", minItemsAhead)));
            maxItemsAhead = Math.max(minItemsAhead,
                    Math.min(120, s.getInt("max-items-ahead", maxItemsAhead)));
            nearestDist = Math.max(5.0, s.getDouble("nearest-dist", nearestDist));
            farthestDist = Math.max(nearestDist + 15.0,
                    s.getDouble("farthest-dist", farthestDist));
            lateralSpread = Math.max(2.0, Math.min(40.0, s.getDouble("lateral-spread", lateralSpread)));
            behindCleanupDist = Math.max(10.0, s.getDouble("behind-cleanup-dist", behindCleanupDist));
            barrelChance = Math.max(0.0, Math.min(0.9,
                    s.getDouble("barrel-chance-percent", barrelChance * 100) / 100.0));
            bigBarrelChance = Math.max(0.0, Math.min(0.9,
                    s.getDouble("big-barrel-chance-percent", bigBarrelChance * 100) / 100.0));
            maxItemsPerFill = Math.max(1, Math.min(20, s.getInt("max-items-per-fill", maxItemsPerFill)));
        } catch (Exception e) {
            plugin.getLogger().warning(
                    "[Raft] Nie udało się wczytać sekcji raft-debris z configu — "
                    + "zostają dotychczasowe ustawienia (" + e + ")");
        }
    }

    /** Startuje timery (wywoływane po rejestracji listenera). */
    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin.getParent(), this::tickSpawn, 40L, CHECK_INTERVAL_TICKS);
        Bukkit.getScheduler().runTaskTimer(plugin.getParent(), this::tickBarrels, 20L, BOB_INTERVAL_TICKS);
        Bukkit.getScheduler().runTaskTimer(plugin.getParent(), this::tickDebrisGhosts, 10L, GHOST_INTERVAL_TICKS);
    }

    // ─────────────────────── SPAWN ŚMIECI ──────────────────────────

    private void tickSpawn() {
        for (RaftEntity raft : RaftManager.getInstance().getRafts()) {
            try {
                maintainDebrisField(raft);
            } catch (Exception e) {
                // pojedynczy błąd nie może wywalić całego ticka
                plugin.getLogger().warning("[Raft] Błąd pola śmieci: " + e);
            }
        }
    }

    /**
     * Utrzymuje „pole śmieci" przed dziobem tratwy:
     *  1. liczy przedmioty i beczki w korytarzu PRZED dziobem (wzdłuż dryfu),
     *  2. sprząta te, które zostały daleko za tratwą albo daleko poza korytarzem
     *     (np. po zmianie kierunku prądu) — ocean się nie zaśmieca,
     *  3. gdy przed dziobem jest mniej niż minItemsAhead, DOSYPUJE pojedyncze
     *     przedmioty (1 sztuka na spawn) w losowych miejscach okna 40–70 bloków
     *     przed dziobem: raz z lewej, kilka kratek dalej z prawej, potem na
     *     środku — bez sztywnych rzędów. Przedmioty mogą wypaść blisko siebie,
     *     a czasem jeden na drugim (generator nie wymusza odstępów). Cel
     *     uzupełnienia jest losowany z przedziału minItemsAhead..maxItemsAhead
     *     (domyślnie 13–28), więc ilość nigdy nie spada poniżej dolnego pułapu
     *     i nigdy nie przekracza górnego.
     */
    private void maintainDebrisField(RaftEntity raft) {
        World world = raft.getState().getWorld();
        if (world == null || !debrisEnabled) return;

        // Tylko tratwy, przy których ktoś jest (FROZEN = nikogo w promieniu)
        if (raft.getTickMode() == RaftEntity.TickMode.FROZEN) return;

        Vector dir = raft.getDirection();
        if (dir == null || dir.lengthSquared() < 1e-6) return; // tratwa stoi
        dir = dir.clone().normalize();

        // Punkt odniesienia: najbliższy blok tratwy W KIERUNKU płynięcia
        Location origin = leadingCellLocation(raft, dir);
        if (origin == null) return;

        double scanRadius = Math.max(farthestDist, behindCleanupDist) + 20.0;
        double corridorHalf = lateralSpread + 2.0;

        // ── 1+2. Przelicz + posprzątaj śmieci wokół dziobu ────────────────
        java.util.List<double[]> occupied = new ArrayList<>();
        int current = 0;
        for (Entity e : world.getNearbyEntities(origin, scanRadius, 40, scanRadius)) {
            if (e.isDead() || !e.isValid()) continue;
            boolean isDebris = e.getScoreboardTags().contains(TAG_DEBRIS);
            boolean isBarrel = e.getScoreboardTags().contains(TAG_BARREL);
            if (!isDebris && !isBarrel) continue;

            // Pozycja względem dziobu: wzdłuż dryfu i w bok
            Location loc = e.getLocation();
            double dx = loc.getX() - origin.getX();
            double dz = loc.getZ() - origin.getZ();
            double forward = dx * dir.getX() + dz * dir.getZ();
            double lateral = Math.abs(dx * (-dir.getZ()) + dz * dir.getX());

            // Sprzątanie: przepłynięte (daleko za dziobem) albo wyrzucone daleko
            // poza korytarz dryfu (np. po zmianie kierunku prądu)
            if (forward < -behindCleanupDist || lateral > corridorHalf + 12.0) {
                removeDebris(e);
                continue;
            }

            if (forward >= -1.0 && forward <= farthestDist && lateral <= corridorHalf) {
                current++;
                occupied.add(new double[]{loc.getX(), loc.getZ()});
            }
        }

        // ── 3. Dosypuj pojedyncze sztuki, aż będzie >= wylosowanego celu ────
        if (current >= minItemsAhead || current >= maxItemsAhead) return;
        int target = minItemsAhead
                + (int) (Math.random() * (maxItemsAhead - minItemsAhead + 1));
        int budget = maxItemsPerFill;
        int guard = 0;
        while (current < target && current < maxItemsAhead && budget > 0 && guard++ < 300) {
            if (trySpawnSingleItem(world, origin, dir, occupied)) {
                current++;
                budget--;
            }
        }
    }

    /**
     * Losuje miejsce na JEDEN przedmiot przed dziobem: losowa odległość
     * (nearest..farthest, domyślnie 40–70) i losowa strona (lewa/środek/prawa,
     * ±lateralSpread), tylko nad otwartą wodą. Bez wymuszania odstępów — nowy
     * przedmiot może wypaść tuż obok istniejącego, a czasem na tym samym
     * miejscu (jeden na drugim); decyduje wyłącznie generator. Zwraca true,
     * gdy udało się postawić obiekt.
     */
    private boolean trySpawnSingleItem(World world, Location origin, Vector dir,
                                       java.util.List<double[]> occupied) {
        double perpX = -dir.getZ();
        double perpZ = dir.getX();

        for (int attempt = 0; attempt < 8; attempt++) {
            double forward = nearestDist + Math.random() * (farthestDist - nearestDist);
            double lateral = (Math.random() * 2 - 1) * lateralSpread;
            double px = origin.getX() + dir.getX() * forward + perpX * lateral;
            double pz = origin.getZ() + dir.getZ() * forward + perpZ * lateral;

            int x = (int) Math.floor(px);
            int z = (int) Math.floor(pz);
            if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
            double surface = findWaterSurface(world, x, z);
            if (surface < 0 || !isOpenWater(world, x, z)) continue;

            Location loc = new Location(world, x + 0.5, surface, z + 0.5);
            double roll = Math.random();
            if (roll < bigBarrelChance) {
                spawnBigBarrel(world, loc);
            } else if (roll < bigBarrelChance + barrelChance) {
                spawnBarrel(world, loc);
            } else {
                spawnDebrisItem(world, loc);
            }
            occupied.add(new double[]{px, pz});
            return true;
        }
        return false;
    }

    /** Blok tratwy najbardziej wysunięty w kierunku płynięcia (pozycja świata). */
    private Location leadingCellLocation(RaftEntity raft, Vector dir) {
        Vector center = raft.getCenter().toVector();
        Vector best = null;
        double bestScore = -Double.MAX_VALUE;
        for (Vector offset : raft.getBlockOffsets()) {
            double score = offset.getX() * dir.getX() + offset.getZ() * dir.getZ();
            if (score > bestScore) {
                bestScore = score;
                best = offset;
            }
        }
        if (best == null) return null;
        return new Location(raft.getState().getWorld(),
                center.getX() + best.getX(),
                center.getY() + best.getY(),
                center.getZ() + best.getZ());
    }

    /** Usuwa obiekt pola (beczkę albo przedmiot) ze świata i z rejestrów. */
    private void removeDebris(Entity e) {
        if (e.getScoreboardTags().contains(TAG_BIG_BARREL)) {
            bigBarrels.remove(e.getUniqueId());
        } else if (e.getScoreboardTags().contains(TAG_BARREL)) {
            barrels.remove(e.getUniqueId());
        } else if (e.getScoreboardTags().contains(TAG_DEBRIS)) {
            // Kasuj też wizualnego ducha (ItemDisplay) przedmiotu-śmiecia
            ItemDisplay ghost = debrisGhosts.remove(e.getEntityId());
            if (ghost != null && ghost.isValid() && !ghost.isDead()) ghost.remove();
        }
        if (!e.isDead()) e.remove();
    }



    /** Otwarta woda: przynajmniej 3 z 4 sąsiadów też ma powierzchnię wody. */
    private boolean isOpenWater(World world, int x, int z) {
        int open = 0;
        open += findWaterSurface(world, x + 1, z) > 0 ? 1 : 0;
        open += findWaterSurface(world, x - 1, z) > 0 ? 1 : 0;
        open += findWaterSurface(world, x, z + 1) > 0 ? 1 : 0;
        open += findWaterSurface(world, x, z - 1) > 0 ? 1 : 0;
        return open >= 3;
    }

    /**
     * Szuka powierzchni otwartej wody w kolumnie (x, z). Zwraca Y górnej
     * powierzchni wody (blokWody.getY() + 1) albo -1, gdy w kolumnie nie ma
     * otwartej wody (ląd, wyspa, roślinność nad wodą, kałuża 1 blok).
     */
    private double findWaterSurface(World world, int x, int z) {
        for (int y = world.getMaxHeight() - 1; y > world.getMinHeight(); y--) {
            Material t = world.getBlockAt(x, y, z).getType();
            if (t == Material.WATER) {
                // Wymagaj głębszej wody pod spodem (nie kałuży)
                if (world.getBlockAt(x, y - 1, z).getType() != Material.WATER) return -1;
                return y + 1.0;
            }
            if (t != Material.AIR && t != Material.CAVE_AIR) {
                return -1; // ląd / roślinność / cokolwiek nad wodą
            }
        }
        return -1;
    }

    // ─────────────────── ŚMIECI: WIZUAL Z DUŻEGO ZASIĘGU ──────────

    /**
     * Prawdziwy Item-śmieć ma domyślny zasięg renderowania ~32 bloki (Paper
     * entity-tracking-range.item), przez co śmieci z okna 40–70 bloków przed
     * dziobem były niewidoczne do ostatniej chwili. Dlatego każdy śmieć ma
     * dodatkowo ItemDisplay („ducha") — display entity renderują się z dużej
     * odległości (128+ bloków, jak beczki). ItemDisplay jest CZYSTO WIZUALNY:
     * łapanie siecią, podnoszenie i sprzątanie działają na prawdziwym Itemie,
     * więc cała logika pola śmieci działa bez zmian.
     *
     * WAŻNE: klient renderuje encje Item NAWET z flagą Invisible (renderer
     * itemów ignoruje niewidzialność i dodaje własne klienckie kołysanie) —
     * setInvisible nie ukrywa prawdziwego śmiecia i gracz widziałby DWIE
     * deski: ruszającą się (prawdziwy Item) i nieruchomą (duch). Dlatego
     * prawdziwy Item jest ukrywany u każdego gracza przez Player#hideEntity
     * (Paper nie wysyła już tej encji do klienta), a widoczny zostaje tylko
     * duch ItemDisplay. Ukrycie robimy cyklicznie (tickDebrisGhosts), przy
     * spawnie i przy wejściu gracza do świata RAFT — hideEntity jest
     * idempotentne, więc powtórne wołania nic nie kosztują.
     */
    private void tickDebrisGhosts() {
        String raftWorldName = plugin.getConfig().getString("raft-world-name", "RAFT");
        java.util.Set<Integer> alive = new java.util.HashSet<>();

        // Przejście po WSZYSTKICH światach, nie tylko po tego z tratwami przy
        // graczach — duch ma istnieć także dla śmieci w chunkach poolowanych.
        for (World w : plugin.getParent().getServer().getWorlds()) {
            if (!w.getName().equalsIgnoreCase(raftWorldName)) continue;
            for (Item item : w.getEntitiesByClass(Item.class)) {
                if (item.isDead() || !item.isValid()) continue;
                if (!item.getScoreboardTags().contains(TAG_DEBRIS)) continue;

                alive.add(item.getEntityId());

                // Prawdziwy item: tylko logika — bez fizyki wody (zero
                // grawitacji / prędkości), żeby nie odbijał się i nie wbijał
                // w powierzchnię; kołysanie robi „duch".
                if (item.hasGravity()) item.setGravity(false);
                org.bukkit.util.Vector vel = item.getVelocity();
                if (vel.getX() != 0 || vel.getY() != 0 || vel.getZ() != 0) {
                    item.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                }
                // Ukryj prawdziwy Item u wszystkich graczy w tym świecie
                // (setInvisible NIE działa na encje Item — klient i tak by go
                // rysował z własnym kołysaniem). Widoczny jest tylko duch.
                hideDebrisFromPlayers(item);

                ItemDisplay ghost = debrisGhosts.get(item.getEntityId());
                if (ghost == null || !ghost.isValid() || ghost.isDead()) {
                    if (ghost != null) debrisGhosts.remove(item.getEntityId());
                    ghost = spawnDebrisGhost(item);
                    if (ghost == null) continue; // ponów w następnym przebiegu
                }

                // Lustro pozycji prawdziwego itemu BEZ kołysania — przedmioty
                // oceaniczne mają leżeć nieruchomo na powierzchni (kołysanie
                // zostaje tylko beczkom, patrz tickBarrels)
                ghost.teleport(item.getLocation());
                // Aktywuj duchy ze starą transformacją (np. po zmianie skali/
                // translacji w kodzie) — bez respawnu, przy okazji ticka.
                if (!ghost.getTransformation().equals(DEBRIS_GHOST_TRANSFORM)) {
                    ghost.setTransformation(DEBRIS_GHOST_TRANSFORM);
                }
            }
        }

        // Sprzątanie duchów, których przedmiot zniknął (sieć, pickup, despawn…)
        debrisGhosts.entrySet().removeIf(en -> {
            ItemDisplay ghost = en.getValue();
            boolean orphan = !alive.contains(en.getKey());
            if (ghost == null || !ghost.isValid() || ghost.isDead() || orphan) {
                if (ghost != null && ghost.isValid() && !ghost.isDead()) ghost.remove();
                return true;
            }
            return false;
        });
    }

    /** Ukrywa prawdziwy Item-śmieć u wszystkich graczy w jego świecie. */
    private void hideDebrisFromPlayers(Item item) {
        for (Player p : item.getWorld().getPlayers()) {
            p.hideEntity(plugin.getParent(), item);
        }
    }

    /** Ukrywa u gracza wszystkie śmieci, które widzi (przy wejściu do świata). */
    private void hideAllDebrisFrom(Player p) {
        for (Item item : p.getWorld().getEntitiesByClass(Item.class)) {
            if (!item.isDead() && item.isValid()
                    && item.getScoreboardTags().contains(TAG_DEBRIS)) {
                p.hideEntity(plugin.getParent(), item);
            }
        }
    }

    private boolean isRaftWorld(World w) {
        String raftWorldName = plugin.getConfig().getString("raft-world-name", "RAFT");
        return raftWorldName != null && w.getName().equalsIgnoreCase(raftWorldName);
    }

    /** Gracz wszedł do świata RAFT — ukryj przed nim prawdziwe śmiecie. */
    @EventHandler
    public void onPlayerEnterRaftWorld(PlayerChangedWorldEvent event) {
        Player p = event.getPlayer();
        if (isRaftWorld(p.getWorld())) {
            hideAllDebrisFrom(p);
        }
    }

    /** Gracz dołączył do serwera w świecie RAFT — ukryj przed nim śmiecie. */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        if (isRaftWorld(p.getWorld())) {
            hideAllDebrisFrom(p);
        }
    }

    /** Tworzy ItemDisplay („ducha") dla przedmiotu-śmiecia i zapamiętuje go. */
    private ItemDisplay spawnDebrisGhost(Item item) {
        World world = item.getWorld();
        if (world == null || !world.isChunkLoaded(
                (int) Math.floor(item.getLocation().getX()) >> 4,
                (int) Math.floor(item.getLocation().getZ()) >> 4)) return null;
        try {
            ItemDisplay display = world.spawn(item.getLocation(), ItemDisplay.class, d -> {
                d.setItemStack(item.getItemStack());
                d.setTransformation(DEBRIS_GHOST_TRANSFORM);
                d.setGravity(false);
                d.setGlowing(false);
                d.setSilent(true);
                d.setInvulnerable(true);
                d.setPersistent(false); // nie zapisuj — odtworzymy po załadowaniu chunku
                d.addScoreboardTag(TAG_DEBRIS_GHOST);
            });
            if (display != null) {
                debrisGhosts.put(item.getEntityId(), display);
            }
            return display;
        } catch (Exception e) {
            plugin.getLogger().warning("[Raft] Nie udało się stworzyć wizualu śmiecia: " + e);
            return null;
        }
    }

    // ─────────────────────── SPAWN FORMY ───────────────────────────

    /**
     * Wysokość spoczynku przedmiotu-śmiecia: stopa AABB itemu ~0.15 bloka NAD
     * płaszczyzną wody. Item ma WYŁĄCZONĄ grawitację (patrz spawnDebrisItem),
     * więc zostaje dokładnie w tym miejscu — nie wbija się w wodę i nie odbija
     * przy pojawieniu; kołysanie na fali robi wizualny ItemDisplay („duch").
     */
    private static final double DEBRIS_REST_Y = 0.15;

    /** Spawnuje pojedynczy pływający przedmiot (custom item). */
    private void spawnDebrisItem(World world, Location loc) {
        String id = rollWeighted(DEBRIS_WEIGHTS);
        ItemStack stack = plugin.getItemManager() != null ? plugin.getItemManager().createItem(id) : null;
        if (stack == null) return;
        stack.setAmount(1);

        Location spawnLoc = loc.clone().add(0, DEBRIS_REST_Y, 0);
        Item item = world.spawn(spawnLoc, Item.class, i -> {
            i.setItemStack(stack);
            i.setPickupDelay(0);
            i.addScoreboardTag(TAG_DEBRIS);
            // Zero grawitacji i zero prędkości — item pojawia się OD RAZU w
            // pozycji spoczynku na powierzchni: waniliowa fizyka wody nie wbija
            // go w głąb i nie „odbija" przy wejściu. Kołysanie na fali robi
            // wizualny ItemDisplay (duch), który go odzwierciedla.
            i.setGravity(false);
            i.setVelocity(new Vector(0, 0, 0));
        });
        if (item != null) {
            // Ukryj prawdziwy Item u graczy OD RAZU (setInvisible nie działa
            // na encje Item — klient rysuje je z własnym kołysaniem). Widoczny
            // jest tylko duch ItemDisplay (ticker potem dalej synchronizuje).
            hideDebrisFromPlayers(item);
            spawnDebrisGhost(item); // natychmiastowy wizual (ticker potem synchronizuje)
        }
    }

    /**
     * Spawnuje beczkę — pływający blok (BlockDisplay BARREL) na powierzchni,
     * lekko zanurzony jak komórka tratwy. Prawy klik rozbija ją (loot z %).
     */
    private void spawnBarrel(World world, Location loc) {
        double y = loc.getY() + RaftEntity.FLOAT_OFFSET; // środek bloku na linii wody
        Location spawnLoc = new Location(world, loc.getX(), y, loc.getZ());

        BlockDisplay display = world.spawn(spawnLoc, BlockDisplay.class, d -> {
            // Wieczko beczki DO GÓRY (domyślny facing=north kładł beczkę na boku).
            org.bukkit.block.data.type.Barrel bd =
                    (org.bukkit.block.data.type.Barrel) Material.BARREL.createBlockData();
            bd.setFacing(org.bukkit.block.BlockFace.UP);
            d.setBlock(bd);
            d.setTransformation(FULL_BLOCK);
            d.setGlowing(false);
            d.setInvulnerable(true);
            d.setSilent(true);
            d.setGravity(false);
            d.setPersistent(true); // przetrwa wyładowanie chunku
            d.addScoreboardTag(TAG_BARREL);
        });
        if (display != null) {
            barrels.put(display.getUniqueId(), new Barrel(display.getUniqueId(),
                    world, loc.getX(), y, loc.getZ(), false));
        }
    }

    private static final Transformation FULL_BLOCK = new Transformation(
            new Vector3f(-0.5f, -0.5f, -0.5f),
            new Quaternionf(),
            new Vector3f(1f, 1f, 1f),
            new Quaternionf());

    /** Skala DUŻEJ beczki (custom model ma 23/16 = 1.4375 bloka wysokości). */
    private static final float BIG_BARREL_SCALE = 1.0f;

    /**
     * Transformacja DUŻEJ beczki (ItemDisplay z custom modelem CMD 4003).
     * Model jest już wycentrowany wokół (0,0,0) w X/Z (geometria −4..20
     * = centrum ~7.3 => 7.3−7.5 = −0.2 px ≈ −0.0125 bloka — pomijalne),
     * więc translacja ZERO (klient i tak centruje modele ItemDisplay).
     * Model pionowo rozciąga się −10..+16.5 px względem środka — beczka
     * siedzi ~0.14 bloka w wodzie i ~0.63 bloka wystaje nad powierzchnię.
     */
    private static final Transformation BIG_BARREL_TRANSFORM = new Transformation(
            new Vector3f(0f, 0f, 0f),
            new Quaternionf(),
            new Vector3f(BIG_BARREL_SCALE, BIG_BARREL_SCALE, BIG_BARREL_SCALE),
            new Quaternionf());

    /** Stack DUŻEJ beczki (HEAVY_CORE + custom_model_data 4003 -> model raft:item/duza_beczka). */
    public static final int BIG_BARREL_CMD = 4003;

    public static ItemStack createBigBarrelStack() {
        ItemStack item = new ItemStack(Material.HEAVY_CORE, 1);
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6Duża beczka");
            meta.setCustomModelData(BIG_BARREL_CMD);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Spawnuje DUŻĄ beczkę — pływający ItemDisplay z customowym modelem
     * (resource pack, CMD 4003) na powierzchni wody. Zachowuje się jak zwykła
     * beczka: wejście w nią = zebranie, hak może ją wyłapać. Ma WŁASNĄ
     * tabelę lootu (BIG_BARREL_LOOT — na razie pusta).
     */
    private void spawnBigBarrel(World world, Location loc) {
        double y = loc.getY() + RaftEntity.FLOAT_OFFSET; // środek na linii wody (jak zwykła beczka)
        Location spawnLoc = new Location(world, loc.getX(), y, loc.getZ());

        ItemDisplay display = world.spawn(spawnLoc, ItemDisplay.class, d -> {
            d.setItemStack(createBigBarrelStack());
            d.setTransformation(BIG_BARREL_TRANSFORM);
            d.setGlowing(false);
            d.setInvulnerable(true);
            d.setSilent(true);
            d.setGravity(false);
            d.setPersistent(true); // przetrwa wyładowanie chunku (jak zwykła beczka)
            d.addScoreboardTag(TAG_BARREL);      // wspólne: interakcja/hak/liczenie
            d.addScoreboardTag(TAG_BIG_BARREL);  // rozróżnienie: własna tabela lootu + zasięg
        });
        if (display != null) {
            bigBarrels.put(display.getUniqueId(), new Barrel(display.getUniqueId(),
                    world, loc.getX(), y, loc.getZ(), true));
        }
    }

    /**
     * Rozmiar wizualu śmiecia („ducha") — 1.5× poprzednich 0.3 bloka (przedmioty
     * na oceanie były trochę za małe). Beczka NIE podlega tej skali (zostaje
     * pełnym blokiem 1×1×1). Wysokość dolnej krawędzi (0.125) bez zmian, żeby
     * przedmiot nie „wbijał się" w wodę po powiększeniu.
     */
    private static final float DEBRIS_GHOST_SCALE = 0.45f;

    /** Transformacja „ducha" śmiecia: powiększony przedmiot. WAŻNE: klient SAM
     *  centruje modele ItemDisplay (wbudowane −0.5 w rendererze), więc
     *  translacja X/Z = ZERO (dowolny offset realnie odsuwa model od pozycji
     *  przedmiotu — przez to pickup/hak celowały obok tego, co widać).
     *  Y = 0.125 → model lekko nad linią wody. */
    private static final Transformation DEBRIS_GHOST_TRANSFORM = new Transformation(
            new Vector3f(0f, 0.125f, 0f),
            new Quaternionf(),
            new Vector3f(DEBRIS_GHOST_SCALE, DEBRIS_GHOST_SCALE, DEBRIS_GHOST_SCALE),
            new Quaternionf());

    // ─────────────────────── BECZKI: KOŁYSANIE ─────────────────────

    private void tickBarrels() {
        long now = System.currentTimeMillis();
        tickBarrelMap(barrels, now);
        tickBarrelMap(bigBarrels, now);
    }

    /** Wspólny tick jednego rejestru beczek: sprzątanie, zebranie, kołysanie. */
    private void tickBarrelMap(java.util.Map<UUID, Barrel> map, long now) {
        map.entrySet().removeIf(e -> {
            Barrel b = e.getValue();
            Entity ent = Bukkit.getEntity(b.entityId);
            if (ent == null || ent.isDead() || !ent.isValid()) return true;
            // Sprzątanie po czasie życia (liczone tylko, gdy chunk załadowany)
            if (now - b.spawnedAt > BARREL_LIFETIME_MS) {
                ent.remove();
                return true;
            }
            return false;
        });

        for (Barrel b : List.copyOf(map.values())) {
            Entity ent = Bukkit.getEntity(b.entityId);
            if (ent == null || ent.isDead() || !ent.isValid()) continue;
            if (!b.world.isChunkLoaded((int) Math.floor(b.x) >> 4, (int) Math.floor(b.z) >> 4)) continue;

            // Wejście gracza w beczkę = natychmiastowe zebranie: beczka znika,
            // a loot trafia prosto do ekwipunku (jak przy wyłowieniu hakiem).
            // Duża beczka jest większa — szerszy zasięg zebrania.
            double pickupXZ = b.big ? BIG_BARREL_WALK_PICKUP_XZ : BARREL_WALK_PICKUP_XZ;
            double pickupY = b.big ? BIG_BARREL_WALK_PICKUP_Y : BARREL_WALK_PICKUP_Y;
            Player picker = null;
            for (Entity near : b.world.getNearbyEntities(
                    ent.getLocation(), pickupXZ, pickupY, pickupXZ)) {
                if (near instanceof Player p && p.isOnline() && !p.isDead() && p.isValid()) {
                    picker = p;
                    break;
                }
            }
            if (picker != null) {
                collectBarrelByPlayer(ent, picker);
                continue;
            }

            // Delikatne kołysanie na fali (Y)
            double bob = Math.sin((System.currentTimeMillis() + b.phase) / 300.0) * 0.05;
            ent.teleport(new Location(b.world, b.x, b.anchorY + bob, b.z));
        }
    }

    // ─────────────── PICKUP ŚMIECI — SKRÓCONY ZASIĘG GRACZA ───────

    /**
     * Wanilia pozwala graczowi podnieść Item z ~1.0 bloka (AABB itemu + "zasięg
     * dotyku" w poziomie). Śmieci oceaniczne mają mieć zasięg zmniejszony o 2/3
     * (≈0.33 bloka od krawędzi AABB itemu), więc gdy gracz podpłynął "na około",
     * ale nie dotyka przedmiotu niemal bezpośrednio — anulujemy pickup. Wanilia
     * ponawia próbę co tick, więc przedmiot podniesie się dopiero w węższym
     * promieniu. Dotyczy TYLKO śmieci (TAG_DEBRIS) w świecie Raft — loot z
     * beczek i zwykłe dropy działają po waniliowemu.
     */
    private static final double OCEAN_PICKUP_RANGE_XZ = 0.33;

    @EventHandler
    public void onOceanDebrisPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        Item item = event.getItem();
        if (!item.getScoreboardTags().contains(TAG_DEBRIS)) return;

        String raftWorldName = plugin.getConfig().getString("raft-world-name", "RAFT");
        if (!item.getWorld().getName().equalsIgnoreCase(raftWorldName)) return;

        // Odległość w XZ od środka gracza do środka itemu; AABB itemu ma ~0.25
        // szerokości (±0.125), więc odejmuję je, by liczyć "od krawędzi".
        double dx = item.getLocation().getX() - player.getLocation().getX();
        double dz = item.getLocation().getZ() - player.getLocation().getZ();
        double distXZ = Math.hypot(dx, dz) - 0.125;
        if (distXZ > OCEAN_PICKUP_RANGE_XZ) {
            event.setCancelled(true);
        }
    }

    // ─────────────────────── ROZBIJANIE BECZKI ─────────────────────

    /**
     * Zebranie beczki przez wejście w nią (kolizja gracza, sprawdzane co 2
     * ticki): beczka znika, a loot losowany z TEJ SAMEJ tabeli co przy
     * rozbijaniu prawym klikiem i wyławianiu hakiem (rollBarrelLoot) trafia
     * prosto do ekwipunku gracza — nadmiar wypada obok gracza.
     * Cicho — bez dźwięku rozbicia i bez particles.
     */
    private void collectBarrelByPlayer(Entity ent, Player player) {
        boolean big = isBigBarrel(ent);
        barrels.remove(ent.getUniqueId());
        bigBarrels.remove(ent.getUniqueId());
        Location loc = ent.getLocation();
        World world = loc.getWorld();
        ent.remove();

        java.util.List<ItemStack> loot = big ? rollBigBarrelLoot() : rollBarrelLoot();
        if (loot.isEmpty()) {
            return; // pusta beczka — nic się nie dzieje
        }
        for (ItemStack stack : loot) {
            java.util.Map<Integer, ItemStack> left = player.getInventory().addItem(stack);
            for (ItemStack rest : left.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), rest);
            }
        }
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.2f);
    }

    @EventHandler
    public void onInteractBarrel(PlayerInteractEntityEvent event) {
        Entity e = event.getRightClicked();
        if (!e.getScoreboardTags().contains(TAG_BARREL)) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        boolean big = isBigBarrel(e);
        barrels.remove(e.getUniqueId());
        bigBarrels.remove(e.getUniqueId());

        Location loc = e.getLocation();
        e.remove();

        // Efekt rozbicia
        World world = loc.getWorld();
        world.playSound(loc, Sound.BLOCK_WOOD_BREAK, 1.0f, 0.8f);
        world.spawnParticle(Particle.BLOCK, loc.clone().add(0, 0.5, 0), 24,
                0.5, 0.5, 0.5, Material.OAK_PLANKS.createBlockData());

        // Loot: każdy przedmiot losuje ilość (0 = nie wypadł); duża beczka
        // ma WŁASNĄ tabelę (na razie pustą)
        java.util.List<ItemStack> loot = big ? rollBigBarrelLoot() : rollBarrelLoot();
        for (ItemStack stack : loot) {
            Item item = world.dropItemNaturally(loc.clone().add(0, 0.5, 0), stack);
            if (item != null) {
                item.setVelocity(new Vector((Math.random() - 0.5) * 0.4, 0.3,
                        (Math.random() - 0.5) * 0.4));
            }
        }

        // Rzadko: pusta beczka (nic nie wypadło) — cichy despawn
        if (loot.isEmpty()) {
            world.playSound(loc, Sound.ENTITY_ITEM_BREAK, 0.6f, 0.9f);
        }
    }

    // ─────────────────────── POMOCNICZE ────────────────────────────

    /**
     * Losuje zawartość beczki wg tabeli BARREL_LOOT (identyczne szanse jak przy
     * rozbijaniu prawym klikiem). Zwraca listę stacków (max MAX_BARREL_DROPS);
     * pusta lista = pusta beczka. Używane też przez HookManager przy wyławianiu
     * beczki hakiem — loot trafia wtedy wprost do ekwipunku gracza.
     */
    public java.util.List<ItemStack> rollBarrelLoot() {
        return rollLootTable(BARREL_LOOT, MAX_BARREL_DROPS);
    }

    /**
     * Losuje zawartość DUŻEJ beczki z jej WŁASNEJ tabeli (BIG_BARREL_LOOT —
     * na razie pusta; wypełnisz później w jednym miejscu). Wywoływane przy
     * zebraniu wejściem, rozbiciu prawym klikiem i wyłowieniu hakiem.
     */
    public java.util.List<ItemStack> rollBigBarrelLoot() {
        return rollLootTable(BIG_BARREL_LOOT, MAX_BIG_BARREL_DROPS);
    }

    /** Wspólny generator lootu beczek (tabela + maks. liczba dropów). */
    private java.util.List<ItemStack> rollLootTable(List<LootEntry> table, int maxDrops) {
        java.util.List<ItemStack> out = new ArrayList<>();
        ItemManager im = plugin.getItemManager();
        if (im == null) return out;
        int dropped = 0;
        for (LootEntry entry : table) {
            if (dropped >= maxDrops) break;
            int amount = entry.rollAmount();
            if (amount <= 0) continue;
            ItemStack stack;
            if (entry.customId() != null) {
                stack = im.createItem(entry.customId());
            } else {
                stack = new ItemStack(entry.material(), 1);
            }
            if (stack == null) continue;
            stack.setAmount(amount);
            out.add(stack);
            dropped++;
        }
        return out;
    }

    /** Czy encja to DUŻA beczka (custom model z resource packa)? */
    public static boolean isBigBarrel(Entity e) {
        return e != null && e.getScoreboardTags().contains(TAG_BIG_BARREL);
    }

    /** Czy stack to DUŻA beczka (HEAVY_CORE + custom_model_data)? */
    public static boolean isBigBarrelStack(ItemStack stack) {
        if (stack == null || stack.getType() != Material.HEAVY_CORE) return false;
        org.bukkit.inventory.meta.ItemMeta meta = stack.getItemMeta();
        return meta != null && meta.hasCustomModelData()
                && meta.getCustomModelData() == BIG_BARREL_CMD;
    }

    /** Losuje id przedmiotu wg wag (Map<id, waga>). */
    private String rollWeighted(Map<String, Integer> weights) {
        int total = 0;
        for (int w : weights.values()) total += w;
        int roll = (int) (Math.random() * total);
        int cumulative = 0;
        for (Map.Entry<String, Integer> en : weights.entrySet()) {
            cumulative += en.getValue();
            if (roll < cumulative) return en.getKey();
        }
        return weights.keySet().iterator().next();
    }
}
