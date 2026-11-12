package pl.vd.supply;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Chest;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Firework;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import pl.vd.util.ItemBuilder;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import pl.vd.VillagerDefensePlugin;
import pl.vd.arena.Arena;
import pl.vd.arena.ArenaState;
import pl.vd.stand.StandPreset;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Zrzut Zaopatrzenia (supply drop):
 * - co "interval-seconds" (domyślnie 180s = 3 minuty) podczas gry na arenie
 *   jest szansa "chance-percent" (domyślnie 10%) na zrzut,
 * - skrzynia SPADA z nieba (FallingBlock) w losowym miejscu w obrębie zasięgu
 *   areny (kordynaty X/Z z config.yml - ustaw je tak, aby pokrywały całą mapę),
 * - od razu po wywołaniu zrzutu na ziemi pojawiają się cząsteczki (pierścień +
 *   słup dymu) pokazujące miejsce lądowania - widoczne z daleka, zanim skrzynia doleci,
 * - przy lądowaniu gracze słyszą dźwięk „spadającego" pocisku + wybuch pyłu,
 * - po "lifetime-seconds" (domyślnie 60s) skrzynia znika z areny,
 * - skrzynie są automatycznie sprzątane po zakończeniu gry / wyłączeniu serwera.
 */
public class SupplyDropManager implements Listener {

    private final VillagerDefensePlugin plugin;
    private final NamespacedKey dropKey;

    private BukkitTask task;
    // nazwa balonu z ostatniego zrzutu - żeby nie wylosować dwa razy tego samego koloru pod rząd
    private String lastBalloonName;

    // ostatni moment "rzutu" (roll) dla każdej areny - od tego liczymy interwał
    private final Map<String, Long> lastRoll = new HashMap<>();
    // aktywne skrzynie zrzutów per arena (blok + zadanie usunięcia)
    private final Map<String, List<ActiveChest>> activeChests = new HashMap<>();
    // spadające skrzynie (FallingBlock) oczekujące na lądowanie
    private final Map<UUID, PendingDrop> pendingDrops = new HashMap<>();
    // animacje znikania skrzyń (odlot jak fajerwerka): UUID skrzyni -> zadanie
    private final Map<UUID, BukkitTask> animations = new HashMap<>();
    // otwarte automaty łupu: gracz -> zadanie przewijania (żeby nie otworzyć dwóch naraz)
    private final Map<UUID, SlotMachineRunnable> slotMachines = new HashMap<>();

    // wysokość, z której spada skrzynia nad ziemią
    private static final int FALL_BLOCKS = 160;
    // stała prędkość spadania: 160 bloków / 40 s = 4,0 bloka/s = 0,20 bloka/tick.
    // Nadpisujemy prędkość co tick, żeby grawitacja jej nie przyspieszała.
    private static final double FALL_SPEED = 0.20;
    // animacja znikania (fajerwerka): skrzynia leci do góry 46 ticków (~2,3 s -
    // 50 bloków zamiast 66, czyli ~15 niżej), potem wystrzeliwuje fajerwerka
    private static final int FLIGHT_3_TICKS = 46;
    private static final double FLY_SPEED = 1.1; // bloków na tick w górę
    private static final double CHEST_DRIFT = 0.3; // dryf skrzyni po wystrzeleniu fajerwerek (bloki/tick)
    private static final int FIREWORKS_COUNT = 1;  // ile fajerwerek leci PROSTO w górę
    private static final int DIAGONAL_FIREWORKS = 4; // ile leci NA UKOS (6° od pionu)
    private static final float DIAGONAL_ANGLE_DEG = 6f; // kąt nachylenia od pionu
    private static final double DIAGONAL_SPEED = 0.6; // prędkość startowa fajerwerek na ukos - tylko składowa POZIOMA (bloki/tick)
    // bezpiecznik: fajerwerka power 0 eksploduje po ~10-25 tickach; po tym czasie
    // sprzątamy skrzynię na siłę, gdyby wybuch jednak nie nastąpił
    private static final int FIREWORK_MAX_TICKS = 40;
    // animacja znikania (enderman): czas jedzenia owocu refrenusu jak w grze (32 ticki);
    // cząsteczki portalu lecą PO zniknięciu skrzyni 1,6 s (32 ticki), ale startują
    // 0,25 s (5 ticków) WCZEŚNIEJ - jeszcze zanim skrzynia zniknie
    private static final int EAT_TICKS = 32;
    private static final int EAT_AFTER_TICKS = 32;
    private static final int EAT_LEAD_TICKS = 5; // o ile wcześniej startują cząsteczki (0,25 s)
    // animacja znikania (trucizna/spalenie): o ile niżej zapada się skrzynia przy śmierci
    // (jak ciało moba/gracza po przewróceniu na bok)
    private static final float DEATH_SINK = 0.45f;
    // skala czerwonego szkła poświaty obrażeń w truciźnie (0.93 = minimalnie mniejsze
    // od bloku, żeby nie wystawało poza skrzynię)
    private static final float FLASH_SCALE = 0.93f;
    // o ile niżej siedzi szkło poświaty względem środka skrzyni (0,05 bloka w dół)
    private static final float FLASH_Y_OFFSET = 0.05f;
    // watchdog: jeśli skrzynia nie wylądowała w 45 s (np. rozładowany chunk), sprzątamy
    private static final long WATCHDOG_TICKS = 45L * 20L;

    // ===================== AUTOMAT ŁUPU (slot machine) =====================
    // GUI zrzutu: 5x9 = 45 slotów. Przewijające się sloty (bębny) to 2-6, 11-15,
    // 20-24, 29-33, 39-43 - ikony lecą OD GÓRY DO DOŁU, a wylosowane przedmioty
    // zatrzymują się na środkowym rzędzie (sloty 20-24) i trafiają do ekwipunku.
    private static final int SLOTMACHINE_SIZE = 45;
    private static final int SLOTMACHINE_COLUMNS = 5; // 5 kolumn = 5 wygranych
    private static final int SLOTMACHINE_ROWS = 5;
    /** Pierwszy slot każdego rzędu bębnów: 2-6, 11-15, 20-24, 29-33, 39-43.
     *  slot(r,c) = SLOTMACHINE_ROW_START[r] + c. Dolny rząd (39-43) jest
     *  przesunięty o 1 w prawo względem pozostałych - ukośne bębny jak
     *  w prawdziwym automacie (kolumny: 2,11,20,29,39 / 3,12,21,30,40 / ...). */
    private static final int[] SLOTMACHINE_ROW_START = {2, 11, 20, 29, 39};
    /** Kroki przewijania każdej kolumny - lewa zatrzymuje się pierwsza, prawa ostatnia. */
    private static final int[] SLOTMACHINE_STEPS = {12, 16, 20, 24, 26};
    /** Opóźnienia kolejnych kroków w tickach: szybkie na start, coraz wolniejsze
     *  (zwalnianie jak w prawdziwym automacie); całość ~5 s. */
    private static final int[] SLOTMACHINE_DELAYS = {1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3,
            4, 4, 4, 4, 5, 5, 5, 5, 6, 6, 6, 6, 7, 7};
    /** Opóźnienie wręczenia łupu po zatrzymaniu OSTATNIEJ kolumny: 1 sekunda
     *  (gracz najpierw widzi wynik, dopiero potem przedmioty lecą do ekwipunku). */
    private static final int SLOTMACHINE_REWARD_DELAY_TICKS = 20;
    /** Ile ticków PO WRĘCZENIU przedmiotów gracz ma na samodzielne wyjście z GUI,
     *  zanim zamknie się ono samo - 8 s. Skrzynia znika dopiero po wyjściu. */
    private static final int SLOTMACHINE_RESULT_KEEP_TICKS = 160;

    private static class ActiveChest {
        final Block block;
        BukkitTask despawnTask; // odliczanie zniknięcia (anulowane, gdy łup odbierze automat)
        final UUID owner;              // właściciel zrzutu (null = łup czeka w skrzyni)
        final List<ItemStack> loot;    // wylosowane przedmioty (dla automatu łupu)
        final Arena arena;
        final org.bukkit.block.BlockState originalBlock; // zapisany oryginalny blok (do przywrócenia po despawnie)
        ActiveChest(Block block, UUID owner, List<ItemStack> loot, Arena arena, org.bukkit.block.BlockState originalBlock) {
            this.block = block;
            this.owner = owner;
            this.loot = loot;
            this.arena = arena;
            this.originalBlock = originalBlock;
        }
    }

    private static class PendingDrop {
        final Arena arena;
        final Location landing;
        final List<ItemStack> loot;
        final BukkitTask markerTask;   // cząsteczki miejsca lądowania
        final BukkitTask velocityTask; // utrzymuje stałą prędkość spadania
        final UUID owner;              // gracz, który dostaje łup do ekwipunku (null = skrzynia)
        final Display visual;          // wyświetlacz skrzyni (widoczny z daleka)
        final List<ArmorStand> balloons; // balony ze stojaków (zrzut_balon_*) nad skrzynią
        PendingDrop(Arena arena, Location landing, List<ItemStack> loot,
                    BukkitTask markerTask, BukkitTask velocityTask, UUID owner,
                    Display visual, List<ArmorStand> balloons) {
            this.arena = arena;
            this.landing = landing;
            this.loot = loot;
            this.markerTask = markerTask;
            this.velocityTask = velocityTask;
            this.owner = owner;
            this.visual = visual;
            this.balloons = balloons;
        }
    }

    private static final String CFG = "supply-drop";

    public SupplyDropManager(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
        this.dropKey = new NamespacedKey(plugin, "vd_supply_drop");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /** Uruchamia cykliczne sprawdzanie (co 1 sekundę). */
    public void start() {
        if (task != null) return;
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    /** Zatrzymuje zadania i sprząta wszystko (przy wyłączaniu serwera). */
    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        // zamknij automaty łupu (graczom nic nie dajemy przy wyłączaniu - skrzynie sprzątamy niżej)
        for (SlotMachineRunnable m : new ArrayList<>(slotMachines.values())) {
            m.cancel();
        }
        slotMachines.clear();
        // usuń spadające skrzynie, ich znaczniki, wyświetlacze i balony
        for (UUID id : new ArrayList<>(pendingDrops.keySet())) {
            PendingDrop d = pendingDrops.remove(id);
            if (d == null) continue;
            if (d.markerTask != null) d.markerTask.cancel();
            if (d.velocityTask != null) d.velocityTask.cancel();
            if (d.visual != null && d.visual.isValid()) d.visual.remove();
            removeBalloons(d.balloons);
            Entity ent = plugin.getServer().getEntity(id);
            if (ent != null) ent.remove();
        }
        for (Arena arena : plugin.getArenaManager().getArenas().values()) {
            cleanupArenaChests(arena);
        }
        cancelAnimations();
    }

    /** Anuluje animacje znikania i usuwa lecące skrzynie (sprzątanie areny/serwera). */
    private void cancelAnimations() {
        for (UUID id : new ArrayList<>(animations.keySet())) {
            BukkitTask t = animations.remove(id);
            if (t != null) t.cancel();
            Entity e = plugin.getServer().getEntity(id);
            if (e != null) e.remove();
        }
    }

    private boolean isEnabled() {
        return plugin.getConfig().getBoolean(CFG + ".enabled", true);
    }

    private void tick() {
        if (!isEnabled()) return;
        long intervalMs = Math.max(1L, plugin.getConfig().getInt(CFG + ".interval-seconds", 180)) * 1000L;
        double chance = plugin.getConfig().getDouble(CFG + ".chance-percent", 25.0); // 180 s / 25% = ~12 min
        long now = System.currentTimeMillis();

        for (Arena arena : plugin.getArenaManager().getArenas().values()) {
            if (arena.getState() != ArenaState.INGAME) continue;
            if (arena.getPlayers().isEmpty()) continue;

            long last = lastRoll.getOrDefault(arena.getName(), now);
            if (now - last < intervalMs) continue;

            lastRoll.put(arena.getName(), now); // interwał mija zawsze - szansa jest losowana niezależnie
            if (chance <= 0) continue;
            if (new Random().nextDouble() * 100.0 >= chance) continue;

            // zrzut dla KAŻDEGO gracza na arenie - każdy ma swój losowy łup, który
            // po wylądowaniu trafia od razu do jego ekwipunku (komunikat na czacie)
            List<Location> used = new ArrayList<>();
            for (UUID uuid : arena.getPlayers()) {
                spawnDrop(arena, used, uuid);
            }
        }
    }

    /** Wynik wymuszonego zrzutu (komenda administracyjna /def zrzutdrop). */
    public enum DropResult {
        SUCCESS,           // zrzut wywołany
        NO_WORLD,          // brak świata areny (config supply-drop.world / brak punktów spawnu)
        NO_LANDING_SPOT    // brak miejsca lądowania w zasięgu koordynatów z config.yml
    }

    /**
     * Wymusza pojawienie się zrzutu na arenie (komenda administracyjna /def zrzutdrop).
     */
    public DropResult forceDrop(Arena arena) {
        // komenda admina: właścicielem łupu jest pierwszy gracz areny (albo nikt)
        UUID owner = arena.getPlayers().isEmpty() ? null : arena.getPlayers().iterator().next();
        return spawnDrop(arena, new ArrayList<>(), owner);
    }

    /**
     * Wymusza zrzut na DOKŁADNEJ pozycji (komenda admina z kordynatami).
     * Wykorzystuje ten sam kod co spawnDrop, ale zamiast losowej lokalizacji
     * używa podanej (z pominięciem findLandingSpot).
     */
    public DropResult forceDropAt(Arena arena, Location target) {
        World world = resolveWorld(arena);
        if (world == null) return DropResult.NO_WORLD;
        UUID owner = arena.getPlayers().isEmpty() ? null : arena.getPlayers().iterator().next();
        DropResult result = spawnDropAt(arena, target, owner);
        if (result == DropResult.SUCCESS) {
            plugin.getLogger().info("[VD] Zrzut na żądanie na arenie " + arena.getName()
                    + " (X: " + target.getBlockX() + ", Y: " + target.getBlockY() + ", Z: " + target.getBlockZ() + ")");
        }
        return result;
    }

    /** Jak spawnDrop, ale z lokacją lądowania podaną zamiast losowanej. */
    private DropResult spawnDropAt(Arena arena, Location landing, UUID owner) {
        World world = resolveWorld(arena);
        if (world == null) return DropResult.NO_WORLD;

        List<ItemStack> loot = buildLoot();

        BukkitTask markerTask = plugin.getServer().getScheduler().runTaskTimer(plugin,
                () -> spawnLandingMarker(world, landing), 0L, 10L);

        plugin.getGameManager().playSoundNearby(landing, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 0.9f,
                pl.vd.shop.ShopGUI.SOUND_SUPPLY_LANDING_KEY);

        int spawnY = Math.min(landing.getBlockY() + FALL_BLOCKS, world.getMaxHeight() - 5);
        Location spawnLoc = new Location(world, landing.getX(), spawnY, landing.getZ());

        FallingBlock fb = world.spawnFallingBlock(spawnLoc, Material.WAXED_EXPOSED_COPPER_CHEST.createBlockData());
        fb.setDropItem(false);
        fb.setHurtEntities(false);
        fb.getPersistentDataContainer().set(dropKey, PersistentDataType.BYTE, (byte) 1);

        BlockDisplay vis = world.spawn(spawnLoc, BlockDisplay.class);
        vis.setBlock(Material.WAXED_EXPOSED_COPPER_CHEST.createBlockData());
        vis.setTransformation(new Transformation(
                new Vector3f(0f, 0f, 0f),
                new AxisAngle4f(0f, 0f, 0f, 1f),
                new Vector3f(1f, 1f, 1f),
                new AxisAngle4f(0f, 0f, 0f, 1f)));
        vis.setGlowColorOverride(Color.ORANGE);
        vis.setGlowing(true);

        int balloonHeight = Math.max(2, plugin.getConfig().getInt(CFG + ".balloon-height", 6));
        List<StandPreset> balloonPresets = findBalloonPresets();
        final List<ArmorStand> balloons = new ArrayList<>();
        if (!balloonPresets.isEmpty()) {
            StandPreset chosen = balloonPresets.get(new Random().nextInt(balloonPresets.size()));
            ArmorStand b = spawnBalloon(world, spawnLoc.clone().add(0, balloonHeight, 0), chosen);
            if (b != null) balloons.add(b);
        }

        final int bh = balloonHeight;
        // display i balon liczą pozycję samodzielnie (z .5), nie podążają za FallingBlock (snapuje do siatki)
        final double startX = spawnLoc.getX();
        final double startZ = spawnLoc.getZ();
        final double startY = spawnLoc.getY();
        final int[] tickCounter = {0};
        BukkitTask velocityTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (fb.isValid()) {
                fb.setVelocity(new Vector(0, -FALL_SPEED, 0));
                tickCounter[0]++;
                double currentY = startY - (tickCounter[0] * FALL_SPEED);
                if (vis.isValid()) vis.teleport(new Location(world, startX, currentY, startZ));
                for (ArmorStand bal : balloons) {
                    if (bal != null && bal.isValid()) bal.teleport(new Location(world, startX, currentY + bh, startZ));
                }
            }
        }, 0L, 1L);

        pendingDrops.put(fb.getUniqueId(),
                new PendingDrop(arena, landing, loot, markerTask, velocityTask, owner, vis, balloons));

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            PendingDrop d = pendingDrops.remove(fb.getUniqueId());
            if (d == null) return;
            if (d.markerTask != null) d.markerTask.cancel();
            if (d.velocityTask != null) d.velocityTask.cancel();
            if (d.visual != null && d.visual.isValid()) d.visual.remove();
            removeBalloons(d.balloons);
            if (fb.isValid()) fb.remove();
        }, WATCHDOG_TICKS);

        broadcastTitle(arena, "supply-coming-screen");
        return DropResult.SUCCESS;
    }

    private DropResult spawnDrop(Arena arena) {
        return spawnDrop(arena, new ArrayList<>(), null);
    }

    /** Tworzy jeden zrzut na arenie; „used" to już zajęte miejsca lądowania
     *  (żeby kilka skrzyń nie spadło w to samo miejsce), a „owner" to gracz,
     *  który otrzyma łup od razu do ekwipunku (null = łup zostaje w skrzyni). */
    private DropResult spawnDrop(Arena arena, List<Location> used, UUID owner) {
        World world = resolveWorld(arena);
        if (world == null) return DropResult.NO_WORLD;

        int minX = plugin.getConfig().getInt(CFG + ".min-x", -50);
        int maxX = plugin.getConfig().getInt(CFG + ".max-x", 50);
        int minZ = plugin.getConfig().getInt(CFG + ".min-z", -50);
        int maxZ = plugin.getConfig().getInt(CFG + ".max-z", 50);
        if (minX > maxX) { int t = minX; minX = maxX; maxX = t; }
        if (minZ > maxZ) { int t = minZ; minZ = maxZ; maxZ = t; }

        // teren zrzutu: WŁASNY areny (arenas.yml), a gdy go nie ma - globalny (config.yml)
        Location landing = findLandingSpot(world, zonePolygon(arena), zoneHoles(arena), minX, maxX, minZ, maxZ, used);
        if (landing == null) {
            plugin.getLogger().warning("[VD] Nie znaleziono miejsca na zrzut zaopatrzenia na arenie " + arena.getName()
                    + " (world=" + world.getName() + ", X " + minX + ".." + maxX + ", Z " + minZ + ".." + maxZ + ")");
            return DropResult.NO_LANDING_SPOT;
        }
        used.add(landing);

        List<ItemStack> loot = buildLoot();

        // 1) cząsteczki na ziemi pokazujące miejsce lądowania (widoczne z daleka,
        //    zanim skrzynia doleci) - powtarzane co 10 ticków aż do lądowania
        BukkitTask markerTask = plugin.getServer().getScheduler().runTaskTimer(plugin,
                () -> spawnLandingMarker(world, landing), 0L, 10L);

        // dźwięk „zrzut nadchodzi" przy miejscu lądowania - per gracz z głośnością
        // fazy (Ustawienia -> Dźwięki -> Zrzut zaopatrzenia -> Lądowanie zrzutu)
        plugin.getGameManager().playSoundNearby(landing, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 0.9f,
                pl.vd.shop.ShopGUI.SOUND_SUPPLY_LANDING_KEY);

        // 2) spadająca skrzynia z góry
        int spawnY = Math.min(landing.getBlockY() + FALL_BLOCKS, world.getMaxHeight() - 5);
        Location spawnLoc = new Location(world, landing.getX(), spawnY, landing.getZ());

        // REALNY spadający blok: tylko do wykrywania lądowania (EntityChangeBlockEvent).
        FallingBlock fb = world.spawnFallingBlock(spawnLoc, Material.WAXED_EXPOSED_COPPER_CHEST.createBlockData());
        fb.setDropItem(false);
        fb.setHurtEntities(false);
        fb.getPersistentDataContainer().set(dropKey, PersistentDataType.BYTE, (byte) 1);

        // WYŚWIETLACZ skrzyni w STANDARDOWYM rozmiarze (skala 1.0 = 1 blok): zwykły
        // FallingBlock spadający z 160 bloków bywał niewidoczny (render distance /
        // culling), a ten wyświetlacz jest gwarantowanie renderowany i ma pomarańczową
        // poświatę, więc widać go z daleka. Bez nazwy - sam blok skrzyni z poświatą.
        // Podąża za prawdziwym blokiem i znika przy lądowaniu.
        BlockDisplay vis = world.spawn(spawnLoc, BlockDisplay.class);
        vis.setBlock(Material.WAXED_EXPOSED_COPPER_CHEST.createBlockData());
        vis.setTransformation(new Transformation(
                new Vector3f(0f, 0f, 0f),
                new AxisAngle4f(0f, 0f, 0f, 1f),
                new Vector3f(1f, 1f, 1f),
                new AxisAngle4f(0f, 0f, 0f, 1f)));
        vis.setGlowColorOverride(Color.ORANGE);
        vis.setGlowing(true);

        // BALON: LOSOWY stojak balonu z całego serwera (custom name zaczyna się od
        // "zrzut_balon_", np. zrzut_balon_bialy - kolor na końcu nazwy) leci nad
        // skrzynią (1 losowy kolor na zrzut) i znika, gdy skrzynia wyląduje.
        int balloonHeight = Math.max(2, plugin.getConfig().getInt(CFG + ".balloon-height", 6));
        List<StandPreset> balloonPresets = findBalloonPresets();
        final List<ArmorStand> balloons = new ArrayList<>();
        if (!balloonPresets.isEmpty()) {
            StandPreset chosen;
            if (balloonPresets.size() == 1) {
                chosen = balloonPresets.get(0); // tylko jeden kolor - nie ma wyboru
            } else {
                // losuj, ale NIE ten sam kolor co w poprzednim zrzucie
                StandPreset candidate;
                do {
                    candidate = balloonPresets.get(new Random().nextInt(balloonPresets.size()));
                } while (candidate.name != null && candidate.name.equals(lastBalloonName));
                chosen = candidate;
            }
            lastBalloonName = chosen.name;
            ArmorStand b = spawnBalloon(world, spawnLoc.clone().add(0, balloonHeight, 0), chosen);
            if (b != null) balloons.add(b);
        }

        // stała prędkość spadania: nadpisujemy prędkość co tick, żeby grawitacja
        // nie przyspieszała skrzyni - 0,32 bloka/tick = 160 bloków w 25 s; wyświetlacz
        // skrzyni i balon podążają za prawdziwym blokiem
        // display i balon liczą pozycję samodzielnie (z .5), nie podążają za FallingBlock (snapuje do siatki)
        final double startX2 = spawnLoc.getX();
        final double startZ2 = spawnLoc.getZ();
        final double startY2 = spawnLoc.getY();
        final int[] tickCounter2 = {0};
        final int bh2 = balloonHeight;
        BukkitTask velocityTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (fb.isValid()) {
                fb.setVelocity(new Vector(0, -FALL_SPEED, 0));
                tickCounter2[0]++;
                double currentY = startY2 - (tickCounter2[0] * FALL_SPEED);
                if (vis.isValid()) vis.teleport(new Location(world, startX2, currentY, startZ2));
                for (ArmorStand b : balloons) {
                    if (b != null && b.isValid()) {
                        b.teleport(new Location(world, startX2, currentY + bh2, startZ2));
                    }
                }
            }
        }, 0L, 1L);

        pendingDrops.put(fb.getUniqueId(),
                new PendingDrop(arena, landing, loot, markerTask, velocityTask, owner, vis, balloons));

        // watchdog: gdyby skrzynia nigdy nie wylądowała (rozładowany chunk / próżnia)
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            PendingDrop d = pendingDrops.remove(fb.getUniqueId());
            if (d == null) return;
            if (d.markerTask != null) d.markerTask.cancel();
            if (d.velocityTask != null) d.velocityTask.cancel();
            if (d.visual != null && d.visual.isValid()) d.visual.remove();
            removeBalloons(d.balloons);
            if (fb.isValid()) fb.remove();
        }, WATCHDOG_TICKS);

        // zapowiedź nadchodzącego zrzutu - na EKRANIE (tytuł), nie na czacie
        broadcastTitle(arena, "supply-coming-screen");
        plugin.getLogger().info("[VD] Zrzut zaopatrzenia na arenie " + arena.getName()
                + " (X: " + landing.getBlockX() + ", Z: " + landing.getBlockZ() + ")");
        return DropResult.SUCCESS;
    }

    /**
     * Spawnuje balon (wygląd ze stojaka) nad skrzynią zrzutu: niewidoczny stojak
     * z doczepionymi częściami presetu, bez grawitacji - spada razem ze skrzynią
     * (teleportowany co tick w velocityTask). Zwraca stojak albo null.
     */
    private ArmorStand spawnBalloon(World world, Location loc, StandPreset preset) {
        if (loc.getY() >= world.getMaxHeight() - 1) {
            plugin.getLogger().warning("[VD] Balon za wysoko - obniż supply-drop.balloon-height (max "
                    + (world.getMaxHeight() - 1) + ")");
            return null;
        }
        ArmorStand stand = world.spawn(loc, ArmorStand.class);
        stand.setVisible(false);
        stand.setSmall(true);
        stand.setBasePlate(false);
        stand.setArms(false);
        stand.setGravity(false);
        stand.setInvulnerable(true);
        stand.setSilent(true);
        stand.setCustomNameVisible(false);
        preset.applyTo(stand); // nakłada zapisany wygląd (może nadpisać small/visible)
        for (StandPreset.PartData part : preset.parts) {
            plugin.getArmorStandTuner().attachPart(stand, part);
        }
        return stand;
    }

    /** Znajduje balony (nazwa zaczyna się od "zrzut_balon_", kolor na końcu) -
     *  najpierw z ZAPISANYCH PRESETÓW (/stojak save zrzut_balon_<kolor> -> plik
     *  w stojaki/*.yml), a gdy żadnego nie ma - z żywych stojaków o takiej nazwie
     *  w załadowanych światach. Zrzut niczego nie usuwa: tylko klonuje wygląd,
     *  a po lądowaniu sprząta same klony. Pusta lista = brak balonu. */
    private List<StandPreset> findBalloonPresets() {
        List<StandPreset> out = new ArrayList<>();
        for (String name : plugin.getStandPresetManager().listPresets()) {
            if (!name.toLowerCase(Locale.ROOT).startsWith("zrzut_balon_")) continue;
            StandPreset preset = plugin.getStandPresetManager().loadPreset(name);
            if (preset != null && !preset.parts.isEmpty()) out.add(preset);
        }
        if (!out.isEmpty()) return out;
        // fallback: żywe stojaki o nazwie zrzut_balon_* (gdy ktoś nie zapisał plików)
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                if (!(e instanceof ArmorStand stand) || stand.isDead()) continue;
                String n = stand.getCustomName();
                if (n == null) continue;
                String plain = org.bukkit.ChatColor.stripColor(n);
                if (plain == null || !plain.toLowerCase(Locale.ROOT).startsWith("zrzut_balon_")) continue;
                plugin.getArmorStandTuner().rebuildParts(stand); // śledzenie części także po restarcie
                StandPreset preset = StandPreset.fromStand(plain, stand, 1.0,
                        plugin.getArmorStandTuner().getParts(stand));
                if (!preset.parts.isEmpty()) out.add(preset);
            }
        }
        return out;
    }

    /** Sprząta stojak balonu razem z częściami (bez zostawiania duchów). */
    private void removeBalloon(ArmorStand balloon) {
        if (balloon == null || balloon.isDead()) return;
        plugin.getArmorStandTuner().forgetParts(balloon);
        if (balloon.isValid()) balloon.remove();
    }

    /** Sprząta WSZYSTKIE balony (wszystkie kolory) razem z częściami. */
    private void removeBalloons(List<ArmorStand> balloons) {
        if (balloons == null) return;
        for (ArmorStand b : balloons) removeBalloon(b);
    }

    /** Szuka losowego miejsca w zasięgu areny z suchym lądem pod spodem (nie w próżni,
     *  nie na wodzie/lawie). Gdy polygon ma >= 3 wierzchołki, losuje WEWNĄTRZ wielokąta
     *  (dowolny, niekwadratowy kształt terenu) - w przeciwnym razie w prostokącie
     *  min/max z configu. Punkty wewnątrz DZIUR (każda = osobny wielokąt excluded) są
     *  zawsze pomijane. */
    private Location findLandingSpot(World world, List<int[]> polygon, List<List<int[]>> holes,
                                     int minX, int maxX, int minZ, int maxZ, List<Location> used) {
        Random random = new Random();
        boolean usePolygon = polygon != null && polygon.size() >= 3;
        boolean useExcluded = holes != null && !holes.isEmpty();
        // bounding box wielokąta - w nim losujemy i odrzucamy punkty poza wielokątem
        int bMinX = minX, bMaxX = maxX, bMinZ = minZ, bMaxZ = maxZ;
        if (usePolygon) {
            for (int[] p : polygon) {
                bMinX = Math.min(bMinX, p[0]);
                bMaxX = Math.max(bMaxX, p[0]);
                bMinZ = Math.min(bMinZ, p[1]);
                bMaxZ = Math.max(bMaxZ, p[1]);
            }
        }
        // więcej prób niż dla prostokąta - część losowań wypada poza wielokątem / w dziurze
        for (int attempt = 0; attempt < (usePolygon || useExcluded ? 60 : 8); attempt++) {
            int cx = bMinX + random.nextInt(bMaxX - bMinX + 1);
            int cz = bMinZ + random.nextInt(bMaxZ - bMinZ + 1);
            if (usePolygon && !inPolygon(polygon, cx, cz)) continue; // poza terenem
            if (useExcluded && inExcludedHoles(holes, cx, cz)) continue; // w wykluczonej dziurze
            int cy = world.getHighestBlockYAt(cx, cz);
            if (cy <= world.getMinHeight() + 4) continue; // próżnia - brak terenu
            Material ground = world.getBlockAt(cx, cy, cz).getType();
            if (ground == Material.WATER || ground == Material.LAVA || !ground.isSolid()) continue; // brak suchego lądu
            // woda/lawa NAD lądem (na Paper getHighestBlockYAt pomija wodę, więc ocean
            // zwróciłby dno): kolumna od ziemi w górę musi być sucha
            boolean liquidAbove = false;
            for (int y2 = cy + 1; y2 <= world.getMaxHeight(); y2++) {
                Material m = world.getBlockAt(cx, y2, cz).getType();
                if (m == Material.WATER || m == Material.LAVA) { liquidAbove = true; break; }
                if (m == Material.AIR || m == Material.CAVE_AIR || m == Material.VOID_AIR
                        || m == Material.SEAGRASS || m == Material.TALL_SEAGRASS
                        || m == Material.KELP || m == Material.KELP_PLANT) continue;
                break; // pierwszy nie-ciecz/nie-roślina nad ziemią - suchy ląd
            }
            if (liquidAbove) continue;
            if (tooCloseToUsed(cx, cz, used)) continue;   // inna skrzynia spada już blisko
            return new Location(world, cx + 0.5, cy + 1, cz + 0.5);
        }
        return null;
    }

    /** Czy punkt (x,z) leży wewnątrz którejkolwiek z AKTYWNYCH dziur (>= 3 punkty)? */
    private static boolean inExcludedHoles(List<List<int[]>> holes, double x, double z) {
        for (List<int[]> hole : holes) {
            if (hole.size() >= 3 && inPolygon(hole, x, z)) return true;
        }
        return false;
    }

    /** Czy punkt (x,z) leży wewnątrz wielokąta (ray casting)? */
    private static boolean inPolygon(List<int[]> polygon, double x, double z) {
        boolean inside = false;
        int n = polygon.size();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            int[] a = polygon.get(i);
            int[] b = polygon.get(j);
            boolean crosses = (a[1] > z) != (b[1] > z)
                    && x < (b[0] - a[0]) * (z - a[1]) / (b[1] - a[1]) + a[0];
            if (crosses) inside = !inside;
        }
        return inside;
    }

    // ===================== teren zrzutu: per ARENA (arenas.yml) z fallbackiem na config.yml =====================

    /** Globalny wielokąt z config.yml (fallback dla aren bez własnego terenu). */
    public List<int[]> loadPolygon() {
        return parsePoints(plugin.getConfig().get(CFG + ".polygon"));
    }

    /** Globalne dziury z config.yml (fallback dla aren bez własnego terenu). */
    public List<List<int[]>> loadExcludedHoles() {
        return parseHoles(plugin.getConfig().get(CFG + ".excluded"));
    }

    /** Czy globalny wielokąt ma co najmniej 3 wierzchołki (można w nim losować)? */
    public boolean hasPolygon() {
        return loadPolygon().size() >= 3;
    }

    /** Parsuje wartość z configu (lista [x, z]) na listę punktów. */
    public static List<int[]> parsePoints(Object raw) {
        List<int[]> points = new ArrayList<>();
        if (!(raw instanceof List<?> list)) return points;
        for (Object o : list) {
            if (!(o instanceof List<?> p) || p.size() < 2) continue;
            try {
                points.add(new int[]{Integer.parseInt(String.valueOf(p.get(0))),
                        Integer.parseInt(String.valueOf(p.get(1)))});
            } catch (NumberFormatException ignored) {}
        }
        return points;
    }

    /** Parsuje wartość z configu na listę DZIUR (każda = osobny wielokąt [x, z]).
     *  Stary format pojedynczej dziury (płaska lista punktów [[x,z],[x,z],...]) jest
     *  automatycznie zamieniany na 1 dziurę, więc nic nie trzeba przenosić ręcznie. */
    public static List<List<int[]>> parseHoles(Object raw) {
        List<List<int[]>> holes = new ArrayList<>();
        if (!(raw instanceof List<?> list)) return holes;
        for (Object o : list) {
            // nowy format: każdy element to wielokąt = lista punktów [x, z]
            if (o instanceof List<?> poly && !poly.isEmpty() && poly.get(0) instanceof List<?>) {
                List<int[]> points = parsePoints(poly);
                if (!points.isEmpty()) holes.add(points);
            }
        }
        if (!holes.isEmpty()) return holes;
        // stary format: płaska lista punktów -> jedna dziura
        List<int[]> single = parsePoints(list);
        if (!single.isEmpty()) holes.add(single);
        return holes;
    }

    /** Lista [x, z] gotowa do zapisu w configu. */
    public static List<List<Integer>> serializePoints(List<int[]> points) {
        List<List<Integer>> out = new ArrayList<>();
        for (int[] p : points) out.add(List.of(p[0], p[1]));
        return out;
    }

    /** Lista dziur (wielokątów) gotowa do zapisu w configu. */
    public static List<List<List<Integer>>> serializeHoles(List<List<int[]>> holes) {
        List<List<List<Integer>>> out = new ArrayList<>();
        for (List<int[]> h : holes) out.add(serializePoints(h));
        return out;
    }

    // ---- edycja WŁASNEGO terenu areny (zapis do arenas.yml przez ArenaManager) ----

    /** Wielokąt używany przez zrzut na danej arenie: własny areny, a gdy go nie ma - globalny. */
    public List<int[]> zonePolygon(Arena arena) {
        return arena.hasDropZone() ? arena.getDropPolygon() : loadPolygon();
    }

    /** Dziury używane przez zrzut na danej arenie: własne areny, a gdy ich nie ma - globalne. */
    public List<List<int[]>> zoneHoles(Arena arena) {
        return arena.hasDropZone() ? arena.getDropHoles() : loadExcludedHoles();
    }

    public void addZonePolygonVertex(Arena arena, int x, int z) {
        arena.getDropPolygon().add(new int[]{x, z});
        plugin.getArenaManager().save();
    }

    public boolean removeLastZonePolygonVertex(Arena arena) {
        List<int[]> poly = arena.getDropPolygon();
        if (poly.isEmpty()) return false;
        poly.remove(poly.size() - 1);
        plugin.getArenaManager().save();
        return true;
    }

    /** Czyści wielokąt areny (wraca do prostokąta min/max z configu). */
    public void clearZonePolygon(Arena arena) {
        arena.getDropPolygon().clear();
        plugin.getArenaManager().save();
    }

    /** Zaczyna NOWĄ dziurę areny (pusta, na końcu listy). */
    public void startNewZoneHole(Arena arena) {
        arena.getDropHoles().add(new ArrayList<>());
        plugin.getArenaManager().save();
    }

    /** Dodaje wierzchołek do OSTATNIEJ dziury areny (albo tworzy pierwszą, gdy brak).
     *  Zwraca numer dziury (0-based), do której dodano. */
    public int addZoneHoleVertex(Arena arena, int x, int z) {
        List<List<int[]>> holes = arena.getDropHoles();
        if (holes.isEmpty()) holes.add(new ArrayList<>());
        holes.get(holes.size() - 1).add(new int[]{x, z});
        plugin.getArenaManager().save();
        return holes.size() - 1;
    }

    /** Usuwa ostatni wierzchołek OSTATNIEJ dziury areny (pusta dziura znika). */
    public boolean removeLastZoneHoleVertex(Arena arena) {
        List<List<int[]>> holes = arena.getDropHoles();
        if (holes.isEmpty()) return false;
        List<int[]> last = holes.get(holes.size() - 1);
        if (last.isEmpty()) return false;
        last.remove(last.size() - 1);
        if (last.isEmpty()) holes.remove(holes.size() - 1);
        plugin.getArenaManager().save();
        return true;
    }

    /** Usuwa CAŁĄ dziurę o danym numerze (0-based). Zwraca true, gdy usunięto. */
    public boolean removeZoneHole(Arena arena, int index) {
        List<List<int[]>> holes = arena.getDropHoles();
        if (index < 0 || index >= holes.size()) return false;
        holes.remove(index);
        plugin.getArenaManager().save();
        return true;
    }

    /** Zwraca dziurę areny o danym numerze (0-based) albo pustą listę. */
    public List<int[]> zoneHoleAt(Arena arena, int index) {
        List<List<int[]>> holes = arena.getDropHoles();
        if (index < 0 || index >= holes.size()) return new ArrayList<>();
        return holes.get(index);
    }

    /** Usuwa wszystkie dziury areny. */
    public void clearZoneHoles(Arena arena) {
        arena.getDropHoles().clear();
        plugin.getArenaManager().save();
    }

    /** Wizualne zaznaczenie wielokąta w grze: biała chmura (Particle.CLOUD) w pozycji
     *  KAŻDEGO wierzchołka, na wysokości Y gracza, unosząca się w miejscu przez 20 s.
     *  Dla dziury (excluded=true) partikle są CZERWONE (Particle.DUST - chmura nie ma
     *  wariantu kolorowego), żeby było widać, że ten obszar jest wykluczony. */
    public void showPolygonParticles(Player player, List<int[]> points, boolean excluded) {
        if (player == null || points == null || points.isEmpty()) return;
        World world = player.getWorld();
        double y = player.getLocation().getY(); // wysokość partiklesów = wysokość gracza
        List<Location> locs = new ArrayList<>();
        for (int[] p : points) {
            locs.add(new Location(world, p[0] + 0.5, y, p[1] + 0.5));
        }
        new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                if (tick >= 400) { // 20 sekund (20 ticków/sekundę)
                    cancel();
                    return;
                }
                for (Location loc : locs) {
                    if (excluded) {
                        // czerwone drobinki zamiast chmury - CLOUD nie da się pokolorować
                        world.spawnParticle(Particle.DUST, loc, 1, 0, 0, 0, 0,
                                new Particle.DustOptions(Color.RED, 0.8f));
                    } else {
                        world.spawnParticle(Particle.CLOUD, loc, 1, 0, 0, 0, 0); // bez ruchu - w miejscu
                    }
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** Czy punkt (cx,cz) jest bliżej niż 4 bloki od już zajętego miejsca lądowania? */
    private boolean tooCloseToUsed(int cx, int cz, List<Location> used) {
        for (Location loc : used) {
            if (Math.abs(loc.getBlockX() - cx) <= 4 && Math.abs(loc.getBlockZ() - cz) <= 4) return true;
        }
        return false;
    }

    /** Mały okrąg ognia na ziemi nad miejscem lądowania (FLAME) - dokładnie
     *  w obrębie JEDNEGO bloku, widoczny z daleka; bez pionowej wiązki.
     *  Powtarzane co 10 ticków, dopóki skrzynia nie wyląduje. */
    private void spawnLandingMarker(World world, Location landing) {
        // landing z findLandingSpot ma już x+0.5 / z+0.5 (środek bloku). Nie dodawaj
        // drugi raz 0.5 - inaczej pierścień trafia na styk czterech bloków.
        Location center = new Location(world,
                landing.getBlockX() + 0.5, landing.getY() + 0.15, landing.getBlockZ() + 0.5);
        spawnFlameCircle(world, center);
    }

    /** Okrąg cząsteczek ognia (FLAME) wpisany w jeden blok - tylko te cząsteczki. */
    private void spawnFlameCircle(World world, Location center) {
        final double radius = 0.45; // < 0.5, żeby płomienie nie wchodziły na sąsiednie bloki
        for (int i = 0; i < 12; i++) {
            double angle = 2 * Math.PI * i / 12.0;
            world.spawnParticle(Particle.FLAME,
                    center.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius),
                    1, 0, 0, 0, 0.01);
        }
    }

    /** Lądowanie spadającej skrzyni: stawiamy blok, wsypujemy łupy, dźwięk + wybuch pyłu. */
    @EventHandler
    public void onFallingBlockLand(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof FallingBlock fb)) return;
        if (!fb.getPersistentDataContainer().has(dropKey, PersistentDataType.BYTE)) return;
        PendingDrop drop = pendingDrops.remove(fb.getUniqueId());
        if (drop == null) return;

        event.setCancelled(true); // sami stawiamy skrzynię (żeby mieć pełną kontrolę)
        if (drop.markerTask != null) drop.markerTask.cancel();
        if (drop.velocityTask != null) drop.velocityTask.cancel(); // przestań pchać skrzynię w dół
        if (drop.visual != null && drop.visual.isValid()) drop.visual.remove(); // wyświetlacz znika, staje prawdziwa
        removeBalloons(drop.balloons); // balony kończą lot razem ze skrzynią

        Block block = event.getBlock();
        org.bukkit.block.BlockState originalBlock = block.getState(); // zapisz oryginalny blok (trawa itd.)
        block.setType(Material.WAXED_EXPOSED_COPPER_CHEST, false);

        // dźwięk „wylądowania" per gracz z jego głośnością fazy (Ustawienia ->
        // Dźwięki -> Zrzut zaopatrzenia -> Wylądowanie zrzutu); dźwięk gra i przy
        // miejscu lądowania (dla graczy w pobliżu) i przy każdym graczu areny, żeby
        // usłyszeli go niezależnie od odległości.
        Location loc = block.getLocation().clone().add(0.5, 0.5, 0.5);
        World world = loc.getWorld();
        plugin.getGameManager().playSoundNearby(loc, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.6f,
                pl.vd.shop.ShopGUI.SOUND_SUPPLY_LANDED_KEY);
        // okrąg ognia wokół lądującej skrzyni (FLAME, wpisany w jej blok)
        spawnFlameCircle(world, loc.clone().add(0, 0.55, 0));
        for (UUID uuid : drop.arena.getPlayers()) {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;
            int vol = plugin.getGameManager().soundVolume(p, pl.vd.shop.ShopGUI.SOUND_SUPPLY_LANDED_KEY);
            if (vol <= 0) continue;
            p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f * vol / 100f, 0.6f);
        }

        broadcast(drop.arena, "supply-landed", block.getX(), block.getZ());
        // powiadomienie na EKRANIE dla wszystkich graczy areny (kanał „na ekranie"
        // z Ustawień -> Powiadomienia; pomijane, gdy gracz je wyłączył)
        broadcastTitle(drop.arena, "supply-landed-screen");
        plugin.getLogger().info("[VD] Zrzut wylądował na arenie " + drop.arena.getName());

        // Właściciel ONLINE: skrzynia CZEKA na jego kliknięcie - wtedy otwiera się
        // automat łupu (przewijanie od góry do dołu, zatrzymanie na wylosowanych
        // przedmiotach na slotach 20-24). Łup NIE trafia do skrzyni - jest wręczany
        // dopiero po zatrzymaniu automatu; skrzynia znika po lifetime-seconds, gdy
        // właściciel nie zdąży jej kliknąć.
        Player owner = drop.owner != null ? plugin.getServer().getPlayer(drop.owner) : null;
        if (owner == null || !owner.isOnline()) {
            // brak właściciela online - łup zostaje w skrzyni (zwykłe otwieranie)
            if (block.getState() instanceof Container container) {
                Inventory inv = container.getInventory();
                for (ItemStack item : drop.loot) {
                    inv.addItem(item);
                }
            }
        } else {
            pl.vd.util.Lang.send(owner, "supply-click-chest");
        }

        ActiveChest chest = new ActiveChest(block, drop.owner, drop.loot, drop.arena, originalBlock);
        int lifetime = Math.max(5, plugin.getConfig().getInt(CFG + ".lifetime-seconds", 60));
        chest.despawnTask = plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> despawnChest(chest), lifetime * 20L);
        activeChests.computeIfAbsent(drop.arena.getName(), k -> new ArrayList<>()).add(chest);
    }

    /** Sprząta skrzynię zrzutu: anuluje odliczanie, odgrywa animację znikania, przywraca oryginalny blok i usuwa z listy. */
    private void despawnChest(ActiveChest chest) {
        if (chest.despawnTask != null) chest.despawnTask.cancel();
        Block block = chest.block;
        if (block.getType() == Material.WAXED_EXPOSED_COPPER_CHEST) {
            playRandomDespawnAnimation(block.getWorld(), block.getLocation());
        }
        // przywróć oryginalny blok (trawa, piasek itd.)
        if (chest.originalBlock != null) {
            try {
                chest.originalBlock.update(true, false);
            } catch (Exception ignored) {}
        }
        List<ActiveChest> list = activeChests.get(chest.arena.getName());
        if (list != null) {
            list.removeIf(c -> c.block.equals(block));
            if (list.isEmpty()) activeChests.remove(chest.arena.getName());
        }
    }

    // ===================== AUTOMAT ŁUPU: otwieranie skrzyni =====================

    /** Prawy klik na skrzyni zrzutu: właściciel otwiera automat łupu (zamiast
     *  zwykłego GUI skrzyni). Skrzynie bez właściciela (gracz offline) otwierają
     *  się normalnie - łup czeka w środku. */
    @EventHandler
    public void onChestInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.WAXED_EXPOSED_COPPER_CHEST) return;
        ActiveChest chest = findChest(block);
        if (chest == null) return;
        if (chest.owner == null) return; // zwykła skrzynia z łupem (właściciel offline)
        event.setCancelled(true); // nie otwieraj waniliowego GUI skrzyni
        Player player = event.getPlayer();
        if (!chest.owner.equals(player.getUniqueId())) {
            pl.vd.util.AntiSpam.sendActionBar(player, pl.vd.util.Lang.get(player, "supply-not-owner"));
            return;
        }
        openSlotMachine(player, chest);
    }

    private ActiveChest findChest(Block block) {
        for (List<ActiveChest> list : activeChests.values()) {
            for (ActiveChest c : list) {
                if (c.block.equals(block)) return c;
            }
        }
        return null;
    }

    /** Slot bębna: rząd (0-4) x kolumna (0-4). Sloty przewijania: 2-6, 11-15,
     *  20-24, 29-33, 39-43 (dolny rząd przesunięty o 1 w prawo); środkowy rząd
     *  (20-24) to wygrane. */
    private static int slotMachineSlot(int row, int col) {
        return SLOTMACHINE_ROW_START[row] + col;
    }

    /** Otwiera GUI automatu łupu dla właściciela skrzyni. */
    private void openSlotMachine(Player player, ActiveChest chest) {
        if (slotMachines.containsKey(player.getUniqueId())) return; // już kręci się jeden
        if (chest.block.getType() != Material.WAXED_EXPOSED_COPPER_CHEST) return;
        SlotMachineHolder holder = new SlotMachineHolder(chest, player);
        Inventory inv = plugin.getServer().createInventory(holder, SLOTMACHINE_SIZE,
                net.kyori.adventure.text.Component.text(pl.vd.util.Lang.get(player, "supply-slot-title")));
        holder.inventory = inv;

        ItemStack glass = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE, 1).name(" ").build();
        for (int i = 0; i < SLOTMACHINE_SIZE; i++) inv.setItem(i, glass);
        Random random = new Random();
        for (int c = 0; c < SLOTMACHINE_COLUMNS; c++) {
            for (int r = 0; r < SLOTMACHINE_ROWS; r++) {
                inv.setItem(slotMachineSlot(r, c), randomLootItem(random));
            }
        }
        player.openInventory(inv);

        SlotMachineRunnable machine = new SlotMachineRunnable(holder);
        slotMachines.put(player.getUniqueId(), machine);
        machine.runTaskLater(plugin, 1L);
    }

    /** Kliknięcia w GUI automatu są zablokowane (przedmioty tylko do podglądu). */
    @EventHandler
    public void onSlotMachineClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof SlotMachineHolder)) return;
        event.setCancelled(true);
    }

    /** Zamknięcie GUI w trakcie przewijania = wręcz łup od razu (nic nie przepada). */
    @EventHandler
    public void onSlotMachineClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof SlotMachineHolder holder)) return;
        SlotMachineRunnable machine = slotMachines.get(holder.player.getUniqueId());
        if (machine != null && !machine.done) {
            machine.finish();
        }
    }

    /** Holder GUI automatu łupu - trzyma skrzynię i gracza, żeby po zamknięciu
     *  GUI można było wręczyć wygrane i sprzątnąć skrzynię. */
    private static class SlotMachineHolder implements InventoryHolder {
        final ActiveChest chest;
        final Player player;
        Inventory inventory;
        SlotMachineHolder(ActiveChest chest, Player player) {
            this.chest = chest;
            this.player = player;
        }
        @Override
        public Inventory getInventory() { return inventory; }
    }

    /** Przewijanie bębnów automatu: ikony lecą OD GÓRY DO DOŁU (nowa losowa na
     *  górze, reszta w dół), kroki są coraz wolniejsze, a kolumny zatrzymują się
     *  kolejno (od lewej) - wtedy środkowy rząd (sloty 20-24) pokazuje wylosowane
     *  przedmioty. Po zatrzymaniu wszystkich bębnów łup trafia do ekwipunku gracza,
     *  a skrzynia znika z animacją. Zamknięcie GUI w trakcie = wręczenie od razu. */
    private class SlotMachineRunnable extends BukkitRunnable {
        final SlotMachineHolder holder;
        final Random random = new Random();
        final int[] stepsLeft;
        int delayIdx = 0;
        int tick = 0;
        boolean done = false;
        boolean itemsGiven = false; // czy wygrane już wręczone (po zatrzymaniu albo po ESC)

        SlotMachineRunnable(SlotMachineHolder holder) {
            this.holder = holder;
            this.stepsLeft = SLOTMACHINE_STEPS.clone();
        }

        @Override
        public void run() {
            if (done) return;
            Inventory inv = holder.inventory;
            if (inv == null || !holder.player.isOnline()) {
                finish(); // gracz zniknął - daj łup (albo zostaw w skrzyni)
                return;
            }
            tick++;
            boolean anySpinning = false;
            for (int c = 0; c < SLOTMACHINE_COLUMNS; c++) {
                if (stepsLeft[c] <= 0) continue;
                stepsLeft[c]--;
                if (stepsLeft[c] == 0) {
                    // kolumna zatrzymuje się: środkowy rząd = wygrana, reszta = losowe
                    for (int r = 0; r < SLOTMACHINE_ROWS; r++) {
                        ItemStack icon = r == 2 ? holder.chest.loot.get(c) : randomLootItem(random);
                        inv.setItem(slotMachineSlot(r, c), icon);
                    }
                    playMachineSound(Sound.UI_BUTTON_CLICK, 1.4f);
                } else {
                    scrollColumn(inv, c);
                }
                anySpinning = true;
            }
            if (!anySpinning) {
                // OSTATNIA kolumna się zatrzymała: gracz NIE dostaje przedmiotów po
                // wylosowaniu pierwszej - wszystkie (razem) trafiają do ekwipunku po
                // 1 sekundzie od końca animacji. GUI zostaje otwarte (gracz widzi
                // wynik), a animacja znikania skrzyni zacznie się dopiero po wyjściu.
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (done) return; // wyjście w międzyczasie (ESC) już wręczyło łup
                    if (holder.player.isOnline()) {
                        giveItemsToPlayer();
                        itemsGiven = true;
                    } else {
                        // gracz zniknął w przerwie przed wręczeniem - łup do skrzyni
                        finish();
                        return;
                    }
                    // Bezpiecznik: od momentu WRĘCZENIA przedmiotów gracz ma 8 s,
                    // żeby wyjść sam - potem GUI zamknie się samo (wyzwoli
                    // onSlotMachineClose -> finish() i zniknięcie skrzyni).
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        if (done) return;
                        Player p = holder.player;
                        if (p.isOnline() && p.getOpenInventory().getTopInventory() == holder.inventory) {
                            p.closeInventory(); // onSlotMachineClose -> finish()
                        }
                    }, SLOTMACHINE_RESULT_KEEP_TICKS);
                }, SLOTMACHINE_REWARD_DELAY_TICKS);
                return;
            }
            // dźwięk przewijania (co drugi krok - szybkie kliknięcia na starcie)
            if (tick % 2 == 0) playMachineSound(Sound.UI_BUTTON_CLICK, 1.8f);
            int delay = SLOTMACHINE_DELAYS[Math.min(delayIdx, SLOTMACHINE_DELAYS.length - 1)];
            delayIdx++;
            runTaskLater(plugin, delay);
        }

        /** Przesuwa kolumnę o jeden krok w dół: nowa losowa ikona na górze, reszta niżej. */
        private void scrollColumn(Inventory inv, int c) {
            for (int r = SLOTMACHINE_ROWS - 1; r >= 1; r--) {
                inv.setItem(slotMachineSlot(r, c), inv.getItem(slotMachineSlot(r - 1, c)));
            }
            inv.setItem(slotMachineSlot(0, c), randomLootItem(random));
        }

        /** Dźwięk automatu per gracz (cichy; wyciszany fazą „Automat łupu"
         *  w Ustawieniach -> Dźwięki -> Zrzut zaopatrzenia). */
        private void playMachineSound(Sound sound, float pitch) {
            Player p = holder.player;
            if (!p.isOnline()) return;
            int vol = plugin.getGameManager().soundVolume(p, pl.vd.shop.ShopGUI.SOUND_SUPPLY_SLOTMACHINE_KEY);
            if (vol <= 0) return;
            p.playSound(p.getLocation(), sound, 0.35f * vol / 100f, pitch);
        }

        /** Wręcza wylosowane przedmioty do ekwipunku gracza (nadmiar przy pełnym
         *  ekwipunku WYLATUJE na ziemię - widoczny wyrzut w górę) + komunikat
         *  na czacie i dźwięk wygranej. */
        private void giveItemsToPlayer() {
            Player p = holder.player;
            if (!p.isOnline()) return;
            StringBuilder list = new StringBuilder();
            for (ItemStack item : holder.chest.loot) {
                if (item == null) continue;
                java.util.Map<Integer, ItemStack> left = p.getInventory().addItem(item.clone());
                for (ItemStack rest : left.values()) {
                    // brak miejsca - przedmiot wylatuje na ziemię (rozpęd w górę,
                    // żeby wyrzut był wyraźnie widoczny)
                    org.bukkit.entity.Item dropped = p.getWorld().dropItemNaturally(p.getLocation(), rest);
                    dropped.setVelocity(dropped.getVelocity().add(new Vector(0, 0.35, 0)));
                }
                if (list.length() > 0) list.append("§7, ");
                list.append("§f").append(item.getAmount()).append("x ").append(lootItemName(p, item));
            }
            pl.vd.util.Lang.send(p, "supply-received", list.toString());
            playMachineSound(Sound.ENTITY_PLAYER_LEVELUP, 1.0f);
        }

        /** Koniec (wyjście z GUI albo bezpiecznik): wręcz wygrane, jeśli jeszcze nie
         *  zostały wręczone, i sprzątnij skrzynię (raz - idempotentne). */
        private void finish() {
            if (done) return;
            done = true;
            slotMachines.remove(holder.player.getUniqueId());
            cancel();
            Player p = holder.player;
            boolean collected = false;
            if (!itemsGiven) {
                itemsGiven = true;
                if (p.isOnline()) {
                    giveItemsToPlayer();
                    collected = true;
                } else {
                    // gracz zniknął w trakcie - łup zostaje w skrzyni do końca jej życia
                    if (holder.chest.block.getState() instanceof Container container) {
                        for (ItemStack item : holder.chest.loot) {
                            if (item != null) container.getInventory().addItem(item);
                        }
                    }
                }
            } else {
                collected = true; // przedmioty wręczone już po zatrzymaniu bębnów
            }
            // PO WYJŚCIU z GUI - pokaż innym graczom areny na action barze, kto odebrał łup
            if (collected && p.isOnline()) {
                broadcastCollected(p, holder.chest.arena);
            }
            despawnChest(holder.chest);
        }
    }

    /** Losowa animacja znikania skrzyni (każda skrzynia dostaje inną). */
    private void playRandomDespawnAnimation(World world, Location loc) {
        switch (new Random().nextInt(6)) {
            case 0 -> launchFireworkAnimation(world, loc);
            case 1 -> playEnderAnimation(world, loc);
            case 2 -> playBuryAnimation(world, loc);
            case 3 -> playEatPoisonAnimation(world, loc);
            case 4 -> playBurnAnimation(world, loc);
            default -> playBalloonAnimation(world, loc);
        }
    }

    /** Nazwy animacji znikania do komendy /def animacja_zrzutu (kanoniczne PL). */
    public static List<String> despawnAnimationNames() {
        return List.of("fajerwerka", "ender", "zakopanie", "trucizna", "spalenie", "balon", "losowa");
    }

    /** Czy podana nazwa to znana animacja znikania (PL + EN aliasy). */
    public static boolean isValidDespawnAnimation(String name) {
        return switch (name.toLowerCase()) {
            case "fajerwerka", "firework", "ender", "enderman", "zakopanie", "bury",
                 "trucizna", "poison", "spalenie", "burn", "balon", "balloon",
                 "losowa", "random" -> true;
            default -> false;
        };
    }

    /** Dev (/def animacja_zrzutu): odtwarza wybraną animację znikania na bloku w
     *  podanej lokalizacji - blok na tej pozycji jest usuwany, jak przy zwykłym
     *  znikaniu skrzyni zrzutu. Zwraca false, gdy nazwa animacji jest nieznana. */
    public boolean playDespawnAnimation(String name, World world, Location loc) {
        String n = name.toLowerCase();
        if (!isValidDespawnAnimation(n)) return false;
        switch (n) {
            case "fajerwerka", "firework" -> launchFireworkAnimation(world, loc);
            case "ender", "enderman" -> playEnderAnimation(world, loc);
            case "zakopanie", "bury" -> playBuryAnimation(world, loc);
            case "trucizna", "poison" -> playEatPoisonAnimation(world, loc);
            case "spalenie", "burn" -> playBurnAnimation(world, loc);
            case "balon", "balloon" -> playBalloonAnimation(world, loc);
            default -> playRandomDespawnAnimation(world, loc); // losowa/random
        }
        return true;
    }

    /**
     * ANIMACJA ZNIKANIA (fajerwerka): skrzynia leci w górę ~50 bloków ze śladem
     * iskier i NIE zatrzymuje się - na szczycie wystrzeliwuje 1 fajerwerkę prosto
     * w górę i 4 NA UKOS pod kątem 6° od pionu (każda w inną stronę świata), wszystkie
     * power 0 = szybki wybuch, w losowych kształtach i niepowtarzających się kolorach -
     * razem wyglądają jak większy wybuch wokół skrzyni; po wystrzeleniu skrzynia leci
     * dalej wolniejszym dryfem, żeby zostać przy wybuchach; znika dokładnie w chwili
     * pierwszego wybuchu, w środku eksplozji (bez sztucznych cząsteczek eksplozji -
     * wybuch to same partiklesy fajerwerki).
     * Dźwięki per gracz z głośnością fazy „Znikanie zrzutu".
     */
    private void launchFireworkAnimation(World world, Location loc) {
        Random random = new Random();
        loc.getBlock().setType(Material.AIR, false); // blok znika, leci sam „duch" skrzyni
        // BlockDisplay w ŚRODKU komórki z przesunięciem -0,5 (blok renderuje się od
        // swojej pozycji, więc po przesunięciu zajmuje dokładnie komórkę skrzyni) -
        // duch siedzi tam, gdzie stała prawdziwa skrzynia, i leci z tego samego miejsca
        Location center = loc.clone().add(0.5, 0.5, 0.5); // środek komórki (BlockDisplay + partiklesy)
        BlockDisplay chest = world.spawn(center, BlockDisplay.class, d -> {
            d.setBlock(Material.WAXED_EXPOSED_COPPER_CHEST.createBlockData());
            d.setTransformation(new Transformation(
                    new Vector3f(-0.5f, -0.5f, -0.5f),
                    new Quaternionf(),
                    new Vector3f(1f, 1f, 1f),
                    new Quaternionf()));
            d.setBrightness(null); // światło otoczenia jak zwykły blok - wizualnie się nie odróżnia
        });

        // jedno zadanie: skrzynia wznosi się (teleport w górę co tick) ze śladem iskier
        // i NIE zatrzymuje się; na szczycie wystrzeliwuje 1 fajerwerkę prosto w górę
        // i 4 na ukos (6° od pionu), po czym leci dalej WOLNIEJSZYM dryfem, żeby
        // zostać przy wybuchach i zniknąć w środku pierwszego z nich (bez sztucznych
        // cząsteczek eksplozji - wybuch to same partiklesy fajerwerki; bez dźwięku TNT -
        // fajerwerka sama gra swój dźwięk wybuchu)
        final List<Firework> fws = new ArrayList<>();
        BukkitRunnable anim = new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                if (!chest.isValid()) {
                    cancel();
                    animations.remove(chest.getUniqueId());
                    return;
                }
                if (tick == FLIGHT_3_TICKS) {
                    // 1 fajerwerka leci PROSTO w górę, a 4 dodatkowe lecą NA UKOS pod
                    // kątem 6° od pionu (każda w inną stronę świata) - razem wyglądają
                    // jak większy wybuch fajerwerek wokół skrzyni (power 0 = szybki wybuch)
                    fws.add(spawnFirework(world, chest.getLocation(), null, random));
                    for (int i = 0; i < DIAGONAL_FIREWORKS; i++) {
                        // kierunek poziomy: równo dookoła (4 strony świata) z lekkim rozrzutem
                        double dir = (i / (double) DIAGONAL_FIREWORKS) * Math.PI * 2
                                + (random.nextDouble() - 0.5) * 0.3;
                        // ukośne fajerwerki mają tylko lekki rozpęd NA BOKI (kąt 6° od
                        // pionu) - BEZ dodatkowego rozpędu w górę: wznoszą się dokładnie
                        // tak samo jak pionowa (fizyka fajerwerki sama je unosi), więc
                        // wybuchają na tej samej wysokości, a nie wyżej od niej
                        double vh = DIAGONAL_SPEED * Math.sin(Math.toRadians(DIAGONAL_ANGLE_DEG));
                        double vy = 0;
                        Vector vel = new Vector(Math.cos(dir) * vh, vy, Math.sin(dir) * vh);
                        fws.add(spawnFirework(world, chest.getLocation(), vel, random));
                    }
                }
                // skrzynia leci w górę bez przerwy: szybko, a po wystrzeleniu fajerwerek
                // wolniejszym dryfem (żeby nie oddalić się od wybuchów)
                double speed = tick < FLIGHT_3_TICKS ? FLY_SPEED : CHEST_DRIFT;
                chest.teleport(chest.getLocation().add(0, speed, 0));
                world.spawnParticle(Particle.FIREWORK, chest.getLocation(),
                        3, 0.1, 0.1, 0.1, 0.02);
                if (tick >= FLIGHT_3_TICKS) {
                    // pierwszy wybuch = skrzynia znika w środku eksplozji
                    for (Firework f : fws) {
                        if (!f.isValid()) {
                            chest.remove();
                            cancel();
                            animations.remove(chest.getUniqueId());
                            return;
                        }
                    }
                }
                if (tick >= FLIGHT_3_TICKS + FIREWORK_MAX_TICKS) {
                    // bezpiecznik: gdyby fajerwerki nie wybuchły - sprzątamy i tak
                    for (Firework f : fws) if (f.isValid()) f.remove();
                    chest.remove();
                    cancel();
                    animations.remove(chest.getUniqueId());
                    return;
                }
                tick++;
            }
        };
        animations.put(chest.getUniqueId(), anim.runTaskTimer(plugin, 0L, 1L));

        // dźwięk startu fajerwerki - per gracz z głośnością fazy „Znikanie zrzutu"
        plugin.getGameManager().playSoundNearby(center, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH,
                1.0f, 1.0f, pl.vd.shop.ShopGUI.SOUND_SUPPLY_DESPAWN_KEY);
    }

    /** Tworzy fajerwerkę (power 0 = szybki wybuch) w losowym kształcie i kolorach;
     *  z zadaną prędkością początkową (velocity) albo prosto w górę (null). */
    private Firework spawnFirework(World world, Location loc, Vector velocity, Random random) {
        Firework f = world.spawn(loc, Firework.class);
        FireworkMeta meta = f.getFireworkMeta();
        FireworkEffect.Type[] shapes = FireworkEffect.Type.values();
        FireworkEffect effect = FireworkEffect.builder()
                .with(shapes[random.nextInt(shapes.length)])
                .withColor(randomDistinctColors(random, 3 + random.nextInt(3)))
                .withFade(randomDistinctColors(random, 2))
                .flicker(random.nextBoolean())
                .trail(random.nextBoolean())
                .build();
        meta.addEffect(effect);
        meta.setPower(0);
        f.setFireworkMeta(meta);
        if (velocity != null) f.setVelocity(velocity);
        return f;
    }

    /**
     * ANIMACJA ZNIKANIA (enderman): fizyczna skrzynia znika OD RAZU, a zamiast
     * niej staje wizualna (BlockDisplay w środku komórki, dokładnie tam, gdzie
     * stała); przedmiot o nazwie „Owoc Refrenusu" wisi PRZED skrzynią, na jej
     * przedniej ścianie (strona z zamkiem), delikatnie niżej niż środek ściany,
     * i stoi w PIONIE jak ikona na slocie (render GUI, bez pochyleń - tylko
     * obrót wokół osi Y, żeby patrzył na zewnątrz skrzyni); grają odgłosy
     * jedzenia (czas jak w grze: 32 ticki). Cząsteczki portalu endermana pojawiają
     * się 0,25 s PRZED zniknięciem skrzyni (jeszcze przy ostatnim kęsie) i lecą
     * jeszcze 1,6 s PO zniknięciu. Bez odgłosów teleportu i bez wielkiego wiru
     * portalu. Dźwięki per gracz
     * z głośnością fazy „Znikanie zrzutu".
     */
    private void playEnderAnimation(World world, Location loc) {
        Random random = new Random();
        // przód skrzyni: ściana z zamkiem (czytamy ZANIM zdejmiemy blok; domyślnie
        // NORTH, a jak blok nie jest już skrzynią - zostaje NORTH)
        BlockData blockData = loc.getBlock().getBlockData();
        BlockFace face = blockData instanceof Chest chestData ? chestData.getFacing() : BlockFace.NORTH;
        float fx = face.getModX();
        float fz = face.getModZ();

        // fizyczna skrzynia znika od razu - na jej miejscu staje wizualna
        loc.getBlock().setType(Material.AIR, false);
        Location center = loc.clone().add(0.5, 0.5, 0.5); // środek komórki bloku
        BlockDisplay chest = world.spawn(center, BlockDisplay.class, d -> {
            d.setBlock(Material.WAXED_EXPOSED_COPPER_CHEST.createBlockData());
            d.setTransformation(new Transformation(
                    new Vector3f(-0.5f, -0.5f, -0.5f),
                    new Quaternionf(),
                    new Vector3f(1f, 1f, 1f),
                    new Quaternionf()));
            d.setBrightness(null); // światło otoczenia jak zwykły blok - wizualnie się nie odróżnia
        });

        // owoc refrenusu wisi PRZED skrzynią: środek ściany + lekkie wysunięcie
        // (0,08), delikatnie niżej niż środek (-0,1); render GUI = jak ikona na
        // slocie (stoi w pionie), a obrót tylko wokół osi Y - żeby patrzył na zewnątrz
        final ItemDisplay fruit = world.spawn(
                center.clone().add(fx * 0.58f, -0.1, fz * 0.58f), ItemDisplay.class, d -> {
            d.setItemStack(new ItemBuilder(Material.CHORUS_FRUIT, 1).name("§fOwoc Refrenusu").build());
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GUI);
            // GUI renderuje ikonę pionowo jak na slocie - tylko obrót wokół osi Y,
            // żeby patrzyła na zewnątrz przedniej ściany skrzyni
            Quaternionf rot = faceOutwardRotation(face);
            d.setTransformation(new Transformation(
                    new Vector3f(0f, 0f, 0f),
                    rot,
                    new Vector3f(0.5f, 0.5f, 0.5f),
                    new Quaternionf()));
            d.setBrightness(new Display.Brightness(15, 15));
        });

        final UUID animKey = chest.getUniqueId();
        BukkitRunnable anim = new BukkitRunnable() {
            int tick = 0;
            boolean eaten = false; // skrzynia zniknęła - lecą jeszcze cząsteczki portalu
            @Override
            public void run() {
                if (!eaten && !chest.isValid()) { // skrzynia zniknęła przed zjedzeniem
                    cancel();
                    animations.remove(animKey);
                    if (fruit.isValid()) fruit.remove();
                    return;
                }
                if (!eaten && tick >= EAT_TICKS) { // zjedzone - skrzynia znika
                    eaten = true;
                    chest.remove();
                    fruit.remove();
                    plugin.getGameManager().playSoundNearby(center, Sound.ENTITY_PLAYER_BURP,
                            0.8f, 1.0f, pl.vd.shop.ShopGUI.SOUND_SUPPLY_DESPAWN_KEY);
                }
                if (eaten && tick >= EAT_TICKS + EAT_AFTER_TICKS) { // 1,6 s cząsteczek - koniec
                    cancel();
                    animations.remove(animKey);
                    return;
                }
                // odgłosy jedzenia (jak w grze: chrup co 4 ticki) - tylko póki skrzynia stoi
                if (!eaten && tick % 4 == 0) {
                    plugin.getGameManager().playSoundNearby(center, Sound.ENTITY_GENERIC_EAT,
                            0.7f, 0.9f + random.nextFloat() * 0.2f, pl.vd.shop.ShopGUI.SOUND_SUPPLY_DESPAWN_KEY);
                }
                // cząsteczki portalu endermana: startują 0,25 s PRZED zniknięciem skrzyni
                // (jeszcze przy ostatnim kęsie) i lecą 1,6 s PO zniknięciu - w miejscu,
                // gdzie stała skrzynia
                if (eaten || tick >= EAT_TICKS - EAT_LEAD_TICKS) {
                    world.spawnParticle(Particle.PORTAL, center.clone().add(0, 0.83, 0),
                            3, 0.3, 0.15, 0.3, 0.02);
                }
                tick++;
            }
        };
        animations.put(animKey, anim.runTaskTimer(plugin, 0L, 1L));
    }

    /**
     * ANIMACJA ZNIKANIA (zakopanie, ~6,3 s): 4× dźwięk kopania ziemi (o 40%
     * szybszy - pitch 1.4) + cząsteczki ziemi, a PO dźwiękach skrzynia opada do
     * bloku krokami: kąt ~9 stopni jest STAŁY w trakcie każdego opadania (bez
     * bujania), ale przy każdym kolejnym kroku zmienia się na przeciwny (raz
     * w lewo, raz w prawo); skrzynia przechyla się wokół własnego ŚRODKA (obrót
     * wokół narożnika sprawiał, że przy jednym z ukosów róg wbijał się głębiej
     * i skrzynia wyglądała na niżej, a trzeci ukos powtarzał pierwszy); co krok
     * chwilę się zatrzymuje (cichy dźwięk niszczenia ziemi, bez pełnego
     * rozwalenia bloku); gdy jest w pełni w ziemi, robi się NIEWIDOCZNA (żeby nie
     * było jej widać od spodu przez przezroczyste bloki / pod występem), a dźwięki
     * i cząsteczki lecą dalej do końca animacji. Dźwięki per gracz z głośnością
     * fazy „Znikanie zrzutu".
     */
    private void playBuryAnimation(World world, Location loc) {
        // BlockDisplay spawnujemy w ŚRODKU komórki z przesunięciem -0,5: blok renderuje
        // się od pozycji encji, więc po przesunięciu zajmuje dokładnie komórkę skrzyni,
        // a obrót (pochylenie) przebiega wokół ŚRODKA skrzyni - nie wokół jej narożnika.
        // Dzięki temu przechylenie w lewo i w prawo jest symetryczne (oba dolne rogi
        // schodzą w ziemię tak samo) i skrzynia nie wygląda na niżej przy drugim ukosie.
        Location center = loc.clone().add(0.5, 0.5, 0.5); // środek komórki (BlockDisplay + partiklesy)
        loc.getBlock().setType(Material.AIR, false);

        BlockDisplay chest = world.spawn(center, BlockDisplay.class, d -> {
            d.setBlock(Material.WAXED_EXPOSED_COPPER_CHEST.createBlockData());
            d.setTransformation(new Transformation(
                    new Vector3f(-0.5f, -0.5f, -0.5f),
                    new Quaternionf(),
                    new Vector3f(1f, 1f, 1f),
                    new Quaternionf()));
            d.setBrightness(null); // światło otoczenia jak zwykły blok - wizualnie się nie odróżnia
        });

        final BlockData dirt = Material.DIRT.createBlockData();
        final Location start = loc.clone(); // narożnik bloku (dźwięki i cząsteczki ziemi)
        // od środka skrzyni do ~0,25 pod środkiem dolnego bloku; na końcu skrzynia jest
        // schowana w solidnych blokach nawet pochylona o 9° (przechylone rogi wystają
        // wtedy tylko ~0,08 bloka ponad/poniżej środka - schodzą w ziemię symetrycznie)
        final double totalSink = 1.25;
        final float tilt = (float) Math.toRadians(9); // ~9 stopni na bok
        // opadanie krokami: 5 kroków po 0,25 bloka; krok = 6 ticków opadania
        // + 10 ticków pauzy, a kąt przechyla się na przemian w lewo i w prawo
        final int steps = 5;
        final double stepSink = totalSink / steps;
        final int sinkTicks = 6;
        final int pauseTicks = 10;
        final int stepTicks = sinkTicks + pauseTicks;

        BukkitRunnable anim = new BukkitRunnable() {
            int tick = 0;
            int step = 0;      // który krok opadania (0..4)
            int stepTick = 0;  // tick w obrębie kroku (opadanie + pauza)
            // kąt kroku: stały w trakcie opadania, ale przy każdym kolejnym kroku
            // zmienia się na przeciwny (parzyste kroki +9°, nieparzyste -9°)
            float tiltNow = tilt;
            double sunk = 0;     // jak głęboko już opadła
            boolean hidden = false; // czy skrzynia już schowana w ziemi (usunięta)
            @Override
            public void run() {
                // skrzynia usunięta celowo (hidden) - animacja dźwięków leci dalej;
                // jeśli zniknęła z innego powodu - sprzątamy całość
                if (!chest.isValid() && !hidden) {
                    cancel();
                    animations.remove(chest.getUniqueId());
                    return;
                }
                if (tick >= 140) { // bezpiecznik (animacja kończy się sama w ~125 ticku)
                    cancel();
                    animations.remove(chest.getUniqueId());
                    chest.remove();
                    return;
                }
                // 4× dźwięk kopania ziemi (co 15 ticków = 0,75 s) + cząsteczki ziemi (nie dużo);
                // pitch 1.4 = dźwięk o 40% szybszy
                if (tick % 15 == 0 && tick <= 45) {
                    plugin.getGameManager().playSoundNearby(start,
                            Sound.BLOCK_GRASS_BREAK, 0.7f, 1.4f, pl.vd.shop.ShopGUI.SOUND_SUPPLY_DESPAWN_KEY);
                    world.spawnParticle(Particle.BLOCK, start, 6, 0.3, 0.3, 0.3, 0.05, dirt);
                }
                // PO dźwiękach: opadanie krokami - kąt STAŁY w trakcie opadania (bez
                // bujania), ale zmienia się na przeciwny przy każdym kolejnym kroku
                if (tick > 45) {
                    if (stepTick < sinkTicks) {
                        // faza opadania: schodzi głębiej, kąt kroku pozostaje stały
                        float p = (stepTick + 1) / (float) sinkTicks;
                        sunk = Math.min(totalSink, step * stepSink + stepSink * p);
                    } else if (stepTick == sinkTicks) {
                        // skrzynia się zatrzymała - dźwięk niszczenia ziemi (jak przy kopaniu
                        // dirta, ale bez pełnego rozwalenia bloku)
                        plugin.getGameManager().playSoundNearby(start,
                                Sound.BLOCK_GRASS_HIT, 0.7f, 0.9f, pl.vd.shop.ShopGUI.SOUND_SUPPLY_DESPAWN_KEY);
                    }
                    // skrzynia w pełni schowana w ziemi (0,92 = pełny blok minus margines
                    // ~0,08 na pochylenie 9°): USUWAMY ją, żeby nie było jej widać od spodu
                    // (przez przezroczyste bloki jak szkło albo gdy pod blokiem jest
                    // jaskinia/występ); dźwięki i cząsteczki lecą dalej do końca animacji
                    if (!hidden && sunk >= 0.92) {
                        hidden = true;
                        chest.remove();
                    }
                    // pauza: pozycja zostaje na chwilę bez zmian (kąt stały); obrót
                    // wokół ŚRODKA skrzyni (translacja kompensuje obrót) - bez tego
                    // przechylenie szłoby wokół narożnika i jeden ukos wbijałby róg
                    // głębiej, a drugi unosił
                    if (!hidden) {
                        chest.teleport(center.clone().subtract(0, sunk, 0));
                        Quaternionf rot = new Quaternionf().rotationZ(tiltNow);
                        chest.setTransformation(new Transformation(
                                centerRotationTranslation(rot),
                                rot,
                                new Vector3f(1f, 1f, 1f),
                                new Quaternionf()));
                    }
                    stepTick++;
                    if (stepTick >= stepTicks) {
                        stepTick = 0;
                        step++;
                        if (step >= steps) { // całkowicie schowana - znika
                            cancel();
                            animations.remove(chest.getUniqueId());
                            chest.remove(); // znika w blokach - bez dźwięku
                            return;
                        }
                        // następny krok - kąt na PRZECIWNĄ stronę (ten sam, po drugiej stronie)
                        tiltNow = (step % 2 == 0 ? tilt : -tilt);
                    }
                }
                tick++;
            }
        };
        animations.put(chest.getUniqueId(), anim.runTaskTimer(plugin, 0L, 1L)); // co tick
    }

    /**
     * ANIMACJA ZNIKANIA (jedzenie rozdymki / zatrucie, ~7,3 s): skrzynia
     * „bierze w niewidzialną rękę” rozdymkę (pufferfish), je ją (odgłosy jedzenia
     * jak w grze: chrup co 4 ticki przez cały czas jedzenia - 1,6 s), po 5
     * zadanych obrażeniach (jak trucizna, ale o 40% szybciej: co 18 ticków)
     * dostaje czerwoną poświatę (tak samo jak w animacji spalenia) i dźwięk
     * użądlenia rozdymki, na końcu animację śmierci gracza: przewraca się na bok
     * (obrót wokół DOLNEJ krawędzi na ziemi, jak naprawdę padający blok - kładzie
     * się na ziemi i zapada o 0,45 bloka, jak ciało moba/gracza) z dźwiękiem
     * śmierci i po chwili (0,3 s) znika. Bez cząsteczek.
     * Dźwięki per gracz z głośnością fazy „Znikanie zrzutu”.
     */
    private void playEatPoisonAnimation(World world, Location loc) {
        Random random = new Random();
        // przód skrzyni (ściana z zamkiem) czytamy ZANIM zdejmiemy blok - dla
        // rozdymki wiszącej przed skrzynią (domyślnie NORTH)
        BlockData blockData = loc.getBlock().getBlockData();
        BlockFace face = blockData instanceof Chest chestData ? chestData.getFacing() : BlockFace.NORTH;
        float fx = face.getModX();
        float fz = face.getModZ();

        // BlockDisplay w ŚRODKU komórki z przesunięciem -0,5: blok renderuje się od
        // swojej pozycji, więc po przesunięciu zajmuje dokładnie komórkę skrzyni;
        // obrót przy śmierci idzie wokół ŚRODKA (translacja kompensuje obrót)
        Location center = loc.clone().add(0.5, 0.5, 0.5); // środek komórki (BlockDisplay + partiklesy)
        loc.getBlock().setType(Material.AIR, false);

        BlockDisplay chest = world.spawn(center, BlockDisplay.class, d -> {
            d.setBlock(Material.WAXED_EXPOSED_COPPER_CHEST.createBlockData());
            d.setTransformation(new Transformation(
                    new Vector3f(-0.5f, -0.5f, -0.5f),
                    new Quaternionf(),
                    new Vector3f(1f, 1f, 1f),
                    new Quaternionf()));
            d.setBrightness(null); // światło otoczenia jak zwykły blok - wizualnie się nie odróżnia
        });

        // rozdymka wisi PRZED skrzynią (jak owoc refrenusu w enderze): środek ściany
        // + lekkie wysunięcie, delikatnie niżej, o połowę mniejsza i w PIONIE jak
        // ikona na slocie (render GUI - nie leży płasko jak na ziemi)
        ItemDisplay fish = world.spawn(
                center.clone().add(fx * 0.58f, -0.1, fz * 0.58f), ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(Material.PUFFERFISH));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GUI);
            d.setTransformation(new Transformation(
                    new Vector3f(0f, 0f, 0f),
                    faceOutwardRotation(face),
                    new Vector3f(0.5f, 0.5f, 0.5f),
                    new Quaternionf()));
            d.setBrightness(new Display.Brightness(15, 15));
        });

        final UUID animKey = chest.getUniqueId();
        BukkitRunnable anim = new BukkitRunnable() {
            int tick = 0;
            boolean deathSound = false;
            float fallSign = 0f; // losowa strona przewrócenia: +1 (lewo) albo -1 (prawo)
            BlockDisplay flash = null; // aktywna czerwona szyba obrażeń (podąża za skrzynią)
            int flashTicks = 0;        // ile ticków szyba ma zostać
            @Override
            public void run() {
                if (!chest.isValid()) {
                    cancel();
                    animations.remove(animKey);
                    if (fish.isValid()) fish.remove();
                    if (flash != null && flash.isValid()) flash.remove();
                    return;
                }
                if (tick >= 146) { // ~7,3 s - koniec (0,3 s po przewróceniu skrzyni)
                    cancel();
                    animations.remove(animKey);
                    chest.remove();
                    if (flash != null && flash.isValid()) flash.remove(); // szkło znika razem ze skrzynią
                    return;
                }

                // FAZA JEDZENIA (0-32 ticki = 1,6 s): odgłosy jedzenia jak w grze -
                // chrup co 4 ticki przez cały czas (tyle, ile faktycznie trwa jedzenie
                // rozdymki przez gracza); rozdymka stoi w miejscu (bez kołysania)
                if (tick <= 32) {
                    if (tick % 4 == 0) {
                        plugin.getGameManager().playSoundNearby(center, Sound.ENTITY_GENERIC_EAT,
                                0.7f, 0.9f + random.nextFloat() * 0.2f, pl.vd.shop.ShopGUI.SOUND_SUPPLY_DESPAWN_KEY);
                    }
                } else if (tick == 33) {
                    if (fish.isValid()) fish.remove(); // rozdymka zjedzona
                }

                // ZATRUCIE: 5 obrażeń co 18 ticków (o 40% szybciej niż trucizna w grze,
                // która działa co 25 ticków) - ticki 58/76/94/112/130; zamiast wstrząsu
                // i zielonych cząsteczek - czerwona poświata jak w animacji spalenia
                // (blok robi się delikatnie czerwony) + dźwięk użądlenia rozdymki
                if (tick >= 58 && tick <= 130 && (tick - 58) % 18 == 0) {
                    plugin.getGameManager().playSoundNearby(center, Sound.ENTITY_PUFFER_FISH_STING,
                            1.0f, 0.9f, pl.vd.shop.ShopGUI.SOUND_SUPPLY_DESPAWN_KEY);
                    // nowa szyba obrażeń (stara usuwana) - będzie podążać za skrzynią
                    if (flash != null && flash.isValid()) flash.remove();
                    flash = spawnFlashEntity(world, loc, Material.RED_STAINED_GLASS, FLASH_SCALE);
                    // ostatnie uderzenie (130) zbiega się ze śmiercią: to szkło NIE gaśnie
                    // po 8 tickach, tylko śledzi skrzynię przez cały przewrót i znika razem z nią
                    flashTicks = tick == 130 ? Integer.MAX_VALUE : 8;
                }

                // ŚMIERĆ: skrzynia przewraca się na bok (losowo lewo/prawo) - obrót wokół
                // DOLNEJ krawędzi po stronie upadku (pivot na ziemi, jak naprawdę padający
                // blok): od 0° do 90° w 0,5 s z naturalnym przyspieszeniem (p²), przy czym
                // skrzynia ZAPADA SIĘ przy tym o 0,45 bloka niżej (jak ciało moba/gracza
                // przy śmierci), potem chwilę leży na boku i znika
                float zRot = 0f;
                float sink = 0f;
                if (tick >= 130) { // ANIMACJA ŚMIERCI GRACZA: przewrócenie na bok (0,5 s)
                    if (fallSign == 0f) fallSign = random.nextBoolean() ? 1f : -1f; // losowo lewo/prawo
                    float p = Math.min(1.0f, (tick - 130 + 1) / 10.0f);
                    zRot = (float) (Math.toRadians(90) * p * p) * fallSign;
                    sink = DEATH_SINK * p * p; // zapadanie w ziemię wraz z przewrotem
                    if (!deathSound) {
                        deathSound = true;
                        plugin.getGameManager().playSoundNearby(center, Sound.ENTITY_PLAYER_DEATH,
                                1.0f, 1.0f, pl.vd.shop.ShopGUI.SOUND_SUPPLY_DESPAWN_KEY);
                    }
                }
                if (zRot != 0f) {
                    // przewrócenie wokół dolnej krawędzi na ziemi - skrzynia naprawdę
                    // kładzie się na bok (bez zapadania w ziemię i ślizgania w bok);
                    // zapadnięcie o DEATH_SINK sprawia, że wygląda jak ciało po śmierci
                    Quaternionf rot = new Quaternionf().rotationZ(-zRot);
                    chest.setTransformation(tipOverTransformation(rot, fallSign, sink, 1f, 0f));
                }
                // szyba obrażeń podąża za skrzynią (pozycja + obrót, także przy śmierci);
                // poza śmiercią wygasa po 8 tickach, a ostatnia (od uderzenia w chwili
                // śmierci) zostaje i znika razem ze skrzynią
                if (flash != null) {
                    if (!flash.isValid()) {
                        flash = null;
                    } else if (tick < 130 && --flashTicks <= 0) {
                        flash.remove();
                        flash = null;
                    } else {
                        flash.teleport(chest.getLocation());
                        if (zRot != 0f) {
                            Quaternionf rot = new Quaternionf().rotationZ(-zRot);
                            flash.setTransformation(tipOverTransformation(rot, fallSign, sink, FLASH_SCALE, FLASH_Y_OFFSET));
                        }
                    }
                }
                tick++;
            }
        };
        animations.put(animKey, anim.runTaskTimer(plugin, 0L, 1L));
    }

    /**
     * ANIMACJA ZNIKANIA (spłonięcie, ~6,3 s): skrzynia „wyciąga zapalniczkę”
     * (flint & steel) i podpala się - od ticku 10 pali się (cząsteczki ognia
     * i dymu), po 5 otrzymanych obrażeniach (co 20 ticków - dokładnie tempo
     * palenia w grze) dostaje czerwoną poświatę jak przy obrażeniach (zamiast
     * wstrząsu) i ginie identycznie jak w animacji z rozdymką: przewraca się na
     * bok (obrót wokół DOLNEJ krawędzi na ziemi - kładzie się na ziemi i zapada
     * o 0,45 bloka, jak ciało moba/gracza) z dźwiękiem śmierci; czerwone szkło
     * (0.93) śledzi skrzynię i znika razem z nią po chwili (0,3 s). Dźwięki per
     * gracz z głośnością fazy „Znikanie zrzutu”.
     */
    private void playBurnAnimation(World world, Location loc) {
        Random random = new Random();
        // przód skrzyni (ściana z zamkiem) czytamy ZANIM zdejmiemy blok - dla
        // zapalniczki wiszącej przed skrzynią (domyślnie NORTH)
        BlockData blockData = loc.getBlock().getBlockData();
        BlockFace face = blockData instanceof Chest chestData ? chestData.getFacing() : BlockFace.NORTH;
        float fx = face.getModX();
        float fz = face.getModZ();

        // BlockDisplay w ŚRODKU komórki z przesunięciem -0,5: blok renderuje się od
        // swojej pozycji, więc po przesunięciu zajmuje dokładnie komórkę skrzyni;
        // obrót przy śmierci idzie wokół ŚRODKA (translacja kompensuje obrót)
        Location center = loc.clone().add(0.5, 0.5, 0.5); // środek komórki (BlockDisplay + partiklesy)
        loc.getBlock().setType(Material.AIR, false);

        BlockDisplay chest = world.spawn(center, BlockDisplay.class, d -> {
            d.setBlock(Material.WAXED_EXPOSED_COPPER_CHEST.createBlockData());
            d.setTransformation(new Transformation(
                    new Vector3f(-0.5f, -0.5f, -0.5f),
                    new Quaternionf(),
                    new Vector3f(1f, 1f, 1f),
                    new Quaternionf()));
            d.setBrightness(null); // światło otoczenia jak zwykły blok - wizualnie się nie odróżnia
        });

        // „zapalniczka” wisi PRZED skrzynią (jak owoc refrenusu w enderze): środek
        // ściany + lekkie wysunięcie, delikatnie niżej, o połowę mniejsza i w pionie
        // jak ikona na slocie (render GUI)
        ItemDisplay lighter = world.spawn(
                center.clone().add(fx * 0.58f, -0.1, fz * 0.58f), ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(Material.FLINT_AND_STEEL));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GUI);
            d.setTransformation(new Transformation(
                    new Vector3f(0f, 0f, 0f),
                    faceOutwardRotation(face),
                    new Vector3f(0.5f, 0.5f, 0.5f),
                    new Quaternionf()));
            d.setBrightness(new Display.Brightness(15, 15));
        });

        final UUID animKey = chest.getUniqueId();
        BukkitRunnable anim = new BukkitRunnable() {
            int tick = 0;
            boolean deathSound = false;
            float fallSign = 0f; // losowa strona przewrócenia: +1 (lewo) albo -1 (prawo)
            BlockDisplay flash = null; // aktywna czerwona szyba obrażeń (podąża za skrzynią)
            int flashTicks = 0;        // ile ticków szyba ma zostać
            @Override
            public void run() {
                if (!chest.isValid()) {
                    cancel();
                    animations.remove(animKey);
                    if (lighter.isValid()) lighter.remove();
                    if (flash != null && flash.isValid()) flash.remove();
                    return;
                }
                if (tick >= 126) { // ~6,3 s - koniec (0,3 s po przewróceniu skrzyni)
                    cancel();
                    animations.remove(animKey);
                    chest.remove();
                    if (flash != null && flash.isValid()) flash.remove(); // szkło znika razem ze skrzynią
                    return;
                }

                // WYCIĄGNIĘCIE ZAPALNICZKI (0-10 ticków): dźwięk kliknięcia, kołysze się
                if (tick == 0) {
                    plugin.getGameManager().playSoundNearby(center, Sound.ITEM_FLINTANDSTEEL_USE,
                            1.0f, 1.0f, pl.vd.shop.ShopGUI.SOUND_SUPPLY_DESPAWN_KEY);
                }
                if (tick <= 10) {
                    if (lighter.isValid()) {
                        lighter.teleport(center.clone().add(
                                fx * 0.58f, -0.1 + 0.05 * Math.sin(tick / 3.0), fz * 0.58f));
                    }
                    if (tick == 10) { // PODPALENIE: zapalniczka znika, iskry zapłonu
                        if (lighter.isValid()) lighter.remove();
                        world.spawnParticle(Particle.FLAME, center.clone().add(0, 0.8, 0),
                                16, 0.4, 0.4, 0.4, 0.06);
                    }
                }

                // PALENIE: cząsteczki ognia i dymu unoszą się cały czas po podpaleniu
                if (tick > 10) {
                    world.spawnParticle(Particle.FLAME, center.clone().add(
                                    random.nextDouble() * 0.8 - 0.4, random.nextDouble() * 1.2, random.nextDouble() * 0.8 - 0.4),
                            2, 0, 0.05, 0, 0.02);
                    world.spawnParticle(Particle.SMOKE, center.clone().add(0, 1, 0),
                            1, 0.2, 0.4, 0.2, 0.02);
                }

                // 5 OBRAŻEŃ OD OGNIA co 20 ticków (jak w grze: palenie zadaje 1 obrażenie
                // na sekundę) - ticki 30/50/70/90/110; zamiast wstrząsu - czerwona poświata
                if (tick >= 30 && tick <= 110 && (tick - 30) % 20 == 0) {
                    plugin.getGameManager().playSoundNearby(center, Sound.ENTITY_PLAYER_HURT_ON_FIRE,
                            1.0f, 0.9f, pl.vd.shop.ShopGUI.SOUND_SUPPLY_DESPAWN_KEY);
                    world.spawnParticle(Particle.FLAME, center.clone().add(0, 1, 0),
                            18, 0.5, 0.5, 0.5, 0.12);
                    // nowa szyba obrażeń (stara usuwana) - będzie podążać za skrzynią
                    if (flash != null && flash.isValid()) flash.remove();
                    flash = spawnFlashEntity(world, loc, Material.RED_STAINED_GLASS, FLASH_SCALE);
                    // ostatnie uderzenie (110) zbiega się ze śmiercią: to szkło NIE gaśnie
                    // po 8 tickach, tylko śledzi skrzynię przez cały przewrót i znika razem z nią
                    flashTicks = tick == 110 ? Integer.MAX_VALUE : 8;
                }
                // ŚMIERĆ: skrzynia przewraca się na bok (losowo lewo/prawo) - obrót wokół
                // DOLNEJ krawędzi po stronie upadku (pivot na ziemi, jak naprawdę padający
                // blok): od 0° do 90° w 0,5 s z naturalnym przyspieszeniem (p²), przy czym
                // skrzynia ZAPADA SIĘ przy tym o 0,45 bloka niżej (jak ciało moba/gracza
                // przy śmierci), potem chwilę leży na boku i znika
                float zRot = 0f;
                float sink = 0f;
                if (tick >= 110) { // ANIMACJA ŚMIERCI GRACZA: przewrócenie na bok (0,5 s)
                    if (fallSign == 0f) fallSign = random.nextBoolean() ? 1f : -1f; // losowo lewo/prawo
                    float p = Math.min(1.0f, (tick - 110 + 1) / 10.0f);
                    zRot = (float) (Math.toRadians(90) * p * p) * fallSign;
                    sink = DEATH_SINK * p * p; // zapadanie w ziemię wraz z przewrotem
                    if (!deathSound) {
                        deathSound = true;
                        plugin.getGameManager().playSoundNearby(center, Sound.ENTITY_PLAYER_DEATH,
                                1.0f, 1.0f, pl.vd.shop.ShopGUI.SOUND_SUPPLY_DESPAWN_KEY);
                    }
                }
                if (zRot != 0f) {
                    // przewrócenie wokół dolnej krawędzi na ziemi - skrzynia naprawdę
                    // kładzie się na bok (bez zapadania w ziemię i ślizgania w bok);
                    // zapadnięcie o DEATH_SINK sprawia, że wygląda jak ciało po śmierci
                    Quaternionf rot = new Quaternionf().rotationZ(-zRot);
                    chest.setTransformation(tipOverTransformation(rot, fallSign, sink, 1f, 0f));
                }
                // szyba obrażeń podąża za skrzynią (pozycja + obrót, także przy śmierci);
                // poza śmiercią wygasa po 8 tickach, a ostatnia (od uderzenia w chwili
                // śmierci) zostaje i znika razem ze skrzynią
                if (flash != null) {
                    if (!flash.isValid()) {
                        flash = null;
                    } else if (tick < 110 && --flashTicks <= 0) {
                        flash.remove();
                        flash = null;
                    } else {
                        flash.teleport(chest.getLocation());
                        if (zRot != 0f) {
                            Quaternionf rot = new Quaternionf().rotationZ(-zRot);
                            flash.setTransformation(tipOverTransformation(rot, fallSign, sink, FLASH_SCALE, FLASH_Y_OFFSET));
                        }
                    }
                }
                tick++;
            }
        };
        animations.put(animKey, anim.runTaskTimer(plugin, 0L, 1L));
    }

    /** Tworzy szybę poświaty obrażeń nad blokiem (BlockDisplay w środku komórki
     *  z przesunięciem -scale/2 - szyba zajmuje tę samą komórkę co skrzynia, w skali
     *  `scale` (1 = pełny blok, 0.93 = minimalnie mniejsza), 0,05 niżej niż środek
     *  skrzyni i obraca się razem z nią wokół środka). Nie zarządza czasem życia szyby. */
    private BlockDisplay spawnFlashEntity(World world, Location blockLoc, Material glass, float scale) {
        float half = scale / 2f;
        return world.spawn(blockLoc.clone().add(0.5, 0.5, 0.5), BlockDisplay.class, d -> {
            d.setBlock(glass.createBlockData());
            d.setTransformation(new Transformation(
                    new Vector3f(-half, -half - FLASH_Y_OFFSET, -half),
                    new Quaternionf(),
                    new Vector3f(scale, scale, scale),
                    new Quaternionf()));
            d.setBrightness(new Display.Brightness(15, 15));
        });
    }

    /** Translacja, która utrzymuje środek bloku BlockDisplay w środku komórki przy
     *  obrocie: blok renderuje się od narożnika, więc sama rotacja kręciłaby go wokół
     *  narożnika (jeden kierunek przewracania wbijałby go w ziemię, drugi unosił).
     *  T = -R·(0.5, 0.5, 0.5) cofa przesunięcie środka modelu przy obrocie R. */
    private static Vector3f centerRotationTranslation(Quaternionf rot) {
        return new Vector3f(0.5f, 0.5f, 0.5f).rotate(rot).negate();
    }

    /** Translacja + obrót PRZEWRÓCENIA skrzyni wokół DOLNEJ krawędzi (pivot na ziemi,
     *  po stronie, w którą pada): skrzynia naprawdę kładzie się na bok - bez obrotu
     *  wokół własnego środka w powietrzu (który zapadał ją w ziemię i ślizgał w bok).
     *  T = (P - E) - R·(P - E) + R·T0, gdzie P - E to pivot względem pozycji encji,
     *  a T0 = (-scale/2, -scale/2 - dy, -scale/2) to bazowa translacja bloku w komórce.
     *  WAŻNE: dy jest liczone w UKŁADZIE MODELU (przed obrotem R), więc obraca się
     *  razem ze skrzynią - dzięki temu szkło nie „odpływa” od skrzyni podczas przewrotu,
     *  tylko cały czas siedzi w tej samej pozycji względem niej (wcześniej odejmowanie
     *  dy PO obrocie w przestrzeni świata rozjeżdżało szkło ze skrzynią przy kładzeniu
     *  się na bok). sink to dodatkowe zapadnięcie w dół (jak ciało moba/gracza przy
     *  śmierci, w przestrzeni świata - identyczne dla skrzyni i szkła), a scale to
     *  skala bloku (1 = pełny, 0.93 = minimalnie mniejsza szyba), a dy to dodatkowe
     *  obniżenie (0 dla skrzyni, FLASH_Y_OFFSET dla szkła). */
    private static Transformation tipOverTransformation(Quaternionf rot, float fallSign, float sink, float scale, float dy) {
        Vector3f pivotRel = new Vector3f(fallSign * 0.5f, -0.5f, 0f); // dolna krawędź po stronie upadku
        Vector3f rotPivot = rot.transform(new Vector3f(pivotRel), new Vector3f());
        Vector3f rotBase = rot.transform(new Vector3f(-scale / 2f, -scale / 2f - dy, -scale / 2f), new Vector3f());
        Vector3f t = new Vector3f(pivotRel).sub(rotPivot).add(rotBase);
        t.y -= sink;
        return new Transformation(t, rot, new Vector3f(scale, scale, scale), new Quaternionf());
    }

    /** Obrót ikony (render GUI: pion, przodem na południe) tak, żeby patrzyła na
     *  zewnątrz przedniej ściany skrzyni (strona z zamkiem; domyślnie NORTH). */
    private static Quaternionf faceOutwardRotation(BlockFace face) {
        return switch (face) {
            case SOUTH -> new Quaternionf();
            case EAST -> new Quaternionf().rotationY((float) Math.toRadians(90));
            case WEST -> new Quaternionf().rotationY((float) Math.toRadians(-90));
            default -> new Quaternionf().rotationY((float) Math.toRadians(180)); // NORTH
        };
    }



    /**
     * ANIMACJA ZNIKANIA (balon, 3 s): skrzynia stoi dokładnie tam, gdzie stała
     * jako blok (BlockDisplay w środku komórki, skala wokół środka), powoli
     * pęcznieje - skala rośnie do 1,5x - i unosi się do 2 bloków w górę, bez
     * kołysania i skrętów (jak nadmuchiwany balon), a na końcu „pęka jak
     * balon”: dźwięk pęknięcia (najbliższy w grze - złożenie jajka), małe
     * kolorowe cząsteczki (konfetti) i obłoczek - wszystko przy skrzyni na tej
     * wysokości. Dźwięk per gracz z głośnością fazy „Znikanie zrzutu”.
     */
    private void playBalloonAnimation(World world, Location loc) {
        Random random = new Random();
        // BlockDisplay w ŚRODKU komórki z przesunięciem -0,5: blok renderuje się od
        // pozycji encji, więc po przesunięciu zajmuje dokładnie komórkę fizycznej
        // skrzyni, a skalowanie rośnie wokół ŚRODKA skrzyni (nie od narożnika -
        // wcześniej skala trafiała w translację i skrzynia jechała po skosie w górę)
        Location center = loc.clone().add(0.5, 0.5, 0.5); // środek komórki (BlockDisplay + partiklesy)
        loc.getBlock().setType(Material.AIR, false);

        BlockDisplay chest = world.spawn(center, BlockDisplay.class, d -> {
            d.setBlock(Material.WAXED_EXPOSED_COPPER_CHEST.createBlockData());
            d.setTransformation(new Transformation(
                    new Vector3f(-0.5f, -0.5f, -0.5f),
                    new Quaternionf(),
                    new Vector3f(1f, 1f, 1f),
                    new Quaternionf()));
            d.setBrightness(null); // światło otoczenia jak zwykły blok - wizualnie się nie odróżnia
        });

        final Color[] balloonColors = {
                Color.RED, Color.ORANGE, Color.YELLOW, Color.LIME,
                Color.AQUA, Color.BLUE, Color.FUCHSIA, Color.WHITE
        };
        final UUID animKey = chest.getUniqueId();
        BukkitRunnable anim = new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                if (!chest.isValid()) {
                    cancel();
                    animations.remove(animKey);
                    return;
                }
                if (tick >= 60) { // 3 s - PĘKNIĘCIE BALONA (skrzynia ~2 bloki nad ziemią)
                    cancel();
                    animations.remove(animKey);
                    // pęknięcie tam, gdzie wisi skrzynia (po końcowym unoszeniu: 2 bloki)
                    Location pop = center.clone().add(0, 2.0, 0);
                    // dźwięk pęknięcia (najbliższy balonowemu w grze) + obłoczek
                    plugin.getGameManager().playSoundNearby(pop, Sound.ENTITY_CHICKEN_EGG,
                            1.2f, 1.4f, pl.vd.shop.ShopGUI.SOUND_SUPPLY_DESPAWN_KEY);
                    world.spawnParticle(Particle.POOF, pop, 12, 0.5, 0.5, 0.5, 0.05);
                    // małe kolorowe cząsteczki - konfetti rozsypujące się na boki
                    for (int i = 0; i < 30; i++) {
                        Color c = balloonColors[random.nextInt(balloonColors.length)];
                        world.spawnParticle(Particle.DUST, pop, 1,
                                (random.nextDouble() - 0.5) * 1.6,
                                (random.nextDouble() - 0.5) * 1.6,
                                (random.nextDouble() - 0.5) * 1.6,
                                0.15, new Particle.DustOptions(c, 0.5f));
                    }
                    chest.remove();
                    return;
                }
                // pęcznienie przez 3 s: skala rośnie do 1,5x wokół środka skrzyni,
                // a skrzynia unosi się do 2 bloków w górę (bez obrotów i kołysania)
                double progress = tick / 60.0;
                float scale = (float) (1.0 + 0.5 * progress);
                float rise = (float) (2.0 * progress); // do 2 bloków w górę
                // translacja = -skala/2: blok renderuje się od narożnika, więc bez tego
                // urósłby tylko w górę i w bok; z -skala/2 rośnie symetrycznie wokół środka
                chest.setTransformation(new Transformation(
                        new Vector3f(-scale / 2f, -scale / 2f + rise, -scale / 2f),
                        new Quaternionf(),
                        new Vector3f(scale, scale, scale),
                        new Quaternionf()));
                tick++;
            }
        };
        animations.put(animKey, anim.runTaskTimer(plugin, 0L, 1L));
    }



    /** Losowa lista kolorów fajerwerka - zawsze bez powtórek. */
    private List<Color> randomDistinctColors(Random random, int count) {
        List<Color> pool = new ArrayList<>(List.of(
                Color.WHITE, Color.SILVER, Color.GRAY, Color.RED, Color.ORANGE,
                Color.YELLOW, Color.LIME, Color.GREEN, Color.TEAL, Color.AQUA,
                Color.BLUE, Color.NAVY, Color.PURPLE, Color.FUCHSIA, Color.MAROON,
                Color.BLACK));
        Collections.shuffle(pool, random);
        return new ArrayList<>(pool.subList(0, Math.min(count, pool.size())));
    }

    /** Nazwa przedmiotu łupu w języku gracza (własna nazwa albo słownik PL/EN). */
    private String lootItemName(Player player, ItemStack item) {
        if (item.getItemMeta() != null && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        String id = item.getType().name().toLowerCase(java.util.Locale.ROOT);
        String fallback = id.replace('_', ' ');
        if (!fallback.isEmpty()) {
            fallback = Character.toUpperCase(fallback.charAt(0)) + fallback.substring(1);
        }
        return pl.vd.util.Lang.getOr(player, "shop-item-" + id, fallback);
    }

    /** Usuwa skrzynie, spadające skrzynie i znaczniki danej areny (koniec gry / sprzątanie). */
    public void cleanupArena(Arena arena) {
        lastRoll.remove(arena.getName());
        // spadające skrzynie tej areny
        for (UUID id : new ArrayList<>(pendingDrops.keySet())) {
            PendingDrop d = pendingDrops.get(id);
            if (d == null || d.arena != arena) continue;
            pendingDrops.remove(id);
            if (d.markerTask != null) d.markerTask.cancel();
            Entity ent = plugin.getServer().getEntity(id);
            if (ent != null) ent.remove();
        }
        cleanupArenaChests(arena);
        cancelAnimations(); // animacje są krótkie, ale przy końcu areny sprzątamy wszystkie
    }

    private void cleanupArenaChests(Arena arena) {
        List<ActiveChest> list = activeChests.remove(arena.getName());
        if (list == null) return;
        for (ActiveChest c : list) {
            c.despawnTask.cancel();
            if (c.block.getType() == Material.WAXED_EXPOSED_COPPER_CHEST) {
                c.block.setType(Material.AIR, false);
            }
        }
    }

    /** Świat areny: z konfiguracji "world", a jeśli puste - świat pierwszego punktu spawnu graczy. */
    private World resolveWorld(Arena arena) {
        String worldName = plugin.getConfig().getString(CFG + ".world", "");
        if (worldName != null && !worldName.isBlank()) {
            World w = plugin.getServer().getWorld(worldName);
            if (w != null) return w;
        }
        if (!arena.getPlayerSpawns().isEmpty()) {
            Location any = arena.getPlayerSpawns().get(0);
            if (any != null && any.getWorld() != null) return any.getWorld();
        }
        return null;
    }

    /** Losowy zestaw łupów: dokładnie 5 przedmiotów (po jednym na bęben automatu). */
    private List<ItemStack> buildLoot() {
        Random random = new Random();
        int count = SLOTMACHINE_COLUMNS; // 5 - po jednym na kolumnę automatu
        List<ItemStack> loot = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ItemStack item = randomLootItem(random);
            if (item != null) loot.add(item);
        }
        return loot;
    }

    private ItemStack randomLootItem(Random random) {
        // (materiał, min, max, waga) - waga = szansa względna wyboru
        Object[][] pool = {
                {Material.COOKED_BEEF, 3, 8, 10},
                {Material.COOKED_CHICKEN, 3, 8, 10},
                {Material.COOKED_MUTTON, 3, 8, 10},
                {Material.BAKED_POTATO, 4, 10, 10},
                {Material.COOKED_SALMON, 3, 8, 8},
                {Material.ARROW, 8, 16, 12},
                {Material.SPECTRAL_ARROW, 8, 16, 10},
                {Material.IRON_INGOT, 2, 5, 8},
                {Material.GOLD_INGOT, 2, 5, 7},
                {Material.GOLDEN_APPLE, 1, 2, 6},
                {Material.ENDER_PEARL, 1, 3, 6},
                {Material.WIND_CHARGE, 2, 6, 6},
                {Material.COBWEB, 1, 3, 5},
                {Material.SHIELD, 1, 1, 4},
                {Material.TOTEM_OF_UNDYING, 1, 1, 2},
        };
        int totalWeight = 0;
        for (Object[] o : pool) totalWeight += (Integer) o[3];
        int roll = random.nextInt(totalWeight);
        for (Object[] o : pool) {
            roll -= (Integer) o[3];
            if (roll < 0) {
                Material mat = (Material) o[0];
                int min = (Integer) o[1];
                int max = (Integer) o[2];
                int amount = min + random.nextInt(max - min + 1);
                return new ItemStack(mat, amount);
            }
        }
        return new ItemStack(Material.COOKED_BEEF, 5);
    }

    /** Wysyła TYTUŁ na ekran wszystkim graczom areny w ich języku (kanał
     *  „Powiadomienia na ekranie" - pomijany, gdy gracz go wyłączył). */
    private void broadcastTitle(Arena arena, String titleKey) {
        for (UUID uuid : arena.getPlayers()) {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p == null) continue;
            pl.vd.util.Lang.sendTitle(p, titleKey, null);
        }
    }

    /** Pokazuje INNYM graczom areny na action barze, kto odebrał łup ze zrzutu
     *  (kanał „Action bar" z Ustawień -> Powiadomienia; pomijany, gdy wyłączony). */
    private void broadcastCollected(Player collector, Arena arena) {
        for (UUID uuid : arena.getPlayers()) {
            if (uuid.equals(collector.getUniqueId())) continue; // tylko INNI gracze
            Player p = plugin.getServer().getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;
            var data = plugin.getPlayerDataManager().get(uuid, p.getName());
            if (data.getSettingInt(pl.vd.shop.ShopGUI.NOTIF_ACTIONBAR_KEY, 100) <= 0) continue;
            pl.vd.util.Lang.sendActionBar(p, "supply-collected", collector.getName());
        }
    }

    /** Wysyła ogłoszenie areny w JĘZYKU każdego gracza (klucz komunikatu + argumenty). */
    private void broadcast(Arena arena, String key, Object... args) {
        for (UUID uuid : arena.getPlayers()) {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p == null) continue;
            // gracz z wyłączonym kanałem czatu (Ustawienia -> Powiadomienia) nie widzi ogłoszeń
            var data = plugin.getPlayerDataManager().get(uuid, p.getName());
            if (data.getSettingInt("notif-chat", 100) <= 0) continue;
            p.sendMessage(net.kyori.adventure.text.Component.text(pl.vd.util.Lang.get(p, key, args)));
        }
    }
}
