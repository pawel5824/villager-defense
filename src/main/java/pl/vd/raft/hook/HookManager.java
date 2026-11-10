package pl.vd.raft.hook;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import pl.vd.raft.RaftPlugin;
import pl.vd.raft.entity.RaftEntity;
import pl.vd.raft.world.OceanDebrisManager;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hak z liną (przedmiot „Hak" — FISHING_ROD z custom_model_data), mechanika Raft:
 *
 *  - RZUT: PPM hakiem w ręce rzuca linkę W PEŁNI WANILIOWO (PlayerFishEvent FISHING
 *    nie jest anulowany, klient sam rysuje linę i animację rzutu). HookManager
 *    przejmuje waniliowy spławik: tag, zero szansy brania i nieskończona
 *    żywotność — spławik leci normalnie (fizyka wanilii), aż sam spocznie.
 *  - SPOCZYNEK: gdy spławik przestanie się ruszać nad wodą — hak „siada" na
 *    wodzie (particles); nad lądem/wyspą zahacza się o blok. Spławik NIGDY nie
 *    zostaje przypięty do niewidzialnej kolizji tratwy (shulkery): taki zaczep
 *    jest od razu zdejmowany, a hak leci dalej z ostatnią prędkością lotu —
 *    jak wanilia, swobodnie, aż wleci do wody.
 *  - NABIJANIE: pływające przedmioty w pobliżu haka spoczywającego na wodzie
 *    nabijają się na hak (do 8 sztuk) — dźwięk podnoszenia.
 *  - ZWINIĘCIE: PPM (tap) zwija hak do gracza — nabite przedmioty trafiają do
 *    ekwipunku (nadmiar wypada obok). Pusty hak po prostu wraca.
 *  - Gracz z modem raft-hud TRZYMAJĄCY PPM płynnie przyciąga hak (razem z
 *    nabitymi przedmiotami) do siebie — hak płynie, DOPÓKI przycisk jest
 *    trzymany; puszczenie = hak zostaje w wodzie i łapie dalej.
 *  - Shift+PPM (dla każdego) = NATYCHMIASTOWE zwinięcie haka razem z nabitymi
 *    przedmiotami (jak waniliowe zwinięcie wędki — szybki powrót do gracza).
 *  - Gracz BEZ moda (sterowanie WYRÓWNANE z modem): tap PPM STARTUJE płynne
 *    przyciąganie haka, drugi tap STOPUJE go w miejscu (hak dalej łapie);
 *    Shift+PPM = natychmiastowe zwinięcie. Wanilia nie wysyła „puszczenia",
 *    więc zamiast trzymania — dwa stany przełączane tapem.
 *  - Zmiana slotu / wyjęcie haka z ręki / rozłączenie: hak wraca, a nabite
 *    przedmioty trafiają do ekwipunku gracza (albo wypadają przy haku).
 *
 * Wcześniejsze wersje anulowały waniliowy rzut i prowadziły spławik ręcznie —
 * klient nie wchodził w stan łowienia, więc lina się nie rysowała („nie mogę
 * wyrzucić linki na wodę"), a symulacja lotu rozjeżdżała się z fizyką wanilii.
 * Teraz rzut i lot są w 100% waniliowe; my tylko przejmujemy spławik po rzucie.
 */
public final class HookManager implements Listener {

    /** Domyślne custom_model_data przedmiotu Hak (baza: FISHING_ROD). */
    public static final int DEFAULT_HOOK_CMD = 8010;

    /** Tag oznaczający nasze spławiki (żeby odróżnić od waniliowych). */
    private static final String HOOK_TAG = "raft_hook";

    /** Prędkość powrotu haka przy zwykłym zwijaniu (bloki/tick). */
    private static final double REEL_SPEED = 1.4;
    /** Prędkość powrotu haka przy Shift+PPM (natychmiastowe zwinięcie). */
    private static final double REEL_FAST_SPEED = 4.0;
    /** Maks. LICZBA SZTUK, które zmieszczą się na jednym haku. */
    private static final int HOOK_CAPACITY = 8;
    /** Promień łapania przedmiotów w XZ (od haka).
     * Zmniejszony o 2/3 względem wcześniejszych ~1.6 → ~0.53,
     * potem zwiększony o 2/10 bloka na prośbę (minimalnie większy zasięg).
     */
    private static final double CATCH_RADIUS_XZ = 1.6 / 3.0 + 0.2;
    /** Promień łapania przedmiotów w pionie.
     * Zmniejszony o 2/3 względem ~1.2 → ~0.4, potem +2/10 bloka.
     */
    private static final double CATCH_RADIUS_Y = 1.2 / 3.0 + 0.2;
    /** Ile ticków trwa animacja zwijania haka do gracza. */
    private static final int REEL_TICKS = 8;

    /** Tag wizualu nabitego przedmiotu (identyfikacja + sprzątanie). */
    private static final String HOOK_VISUAL_TAG = "raft_hook_visual";
    /**
     * Rozmiar wizualu zwykłego przedmiotu na haku — TA SAMA skala co śmieci
     * na oceanie (OceanDebrisManager.DEBRIS_GHOST_SCALE = 0.45), żeby złapany
     * przedmiot wyglądał identycznie jak pływający.
     */
    private static final float HOOK_VISUAL_SCALE = 0.45f;

    /**
     * Próg „doganiania” zamrożonego wizualu (bloki): gdy zakotwiczony spławik
     * realnie odjedzie od wizualu dalej niż tyle (resztkowy poślizg po wodzie
     * po rzucie pod kątem, dryf), wizual zostaje przeteleportowany do haka —
     * przedmiot zawsze wisi dokładnie na jego środku. Mikro-ruchy wody
     * (bujanie spławika) poniżej tego progu są ignorowane — wizual pozostaje
     * optycznie zamrożony.
     */
    private static final double VISUAL_FOLLOW_SNAP = 0.05;
    private static final double VISUAL_FOLLOW_SNAP_SQ = VISUAL_FOLLOW_SNAP * VISUAL_FOLLOW_SNAP;

    /**
     * Transformacja wizualu przedmiotu: identyczna jak „duch” śmiecia na
     * oceanie (domyślny kontekst, ta sama skala). WAŻNE: klient SAM centruje
     * modele ItemDisplay (wbudowane −0.5 w rendererze), więc translacja X/Z
     * musi być ZERO — jakikolwiek offset tu realnie ODSUWA model od środka
     * haka. Y = 0.125 → model lekko nad linią wody, jak pływający śmieć.
     */
    private static final Transformation HOOK_ITEM_TRANSFORM = new Transformation(
            new Vector3f(0f, 0.125f, 0f),
            new Quaternionf(),
            new Vector3f(HOOK_VISUAL_SCALE, HOOK_VISUAL_SCALE, HOOK_VISUAL_SCALE),
            new Quaternionf());

    /**
     * Transformacja wizualu beczki: PEŁNOWYMIAROWY blok 1×1×1, środek dokładnie
     * na środku haka (takie same rozmiary jak pływająca beczka na oceanie).
     */
    private static final Transformation HOOK_BARREL_TRANSFORM = new Transformation(
            new Vector3f(-0.5f, -0.5f, -0.5f),
            new Quaternionf(),
            new Vector3f(1f, 1f, 1f),
            new Quaternionf());

    /**
     * Transformacja wizualu DUŻEJ beczki (ItemDisplay z custom modelem):
     * pełna skala (1.0) — identyczny rozmiar jak pływająca na oceanie.
     * Lekko podniesiona (+0.25), bo model wystaje pod środkiem.
     */
    private static final Transformation HOOK_BIG_BARREL_TRANSFORM = new Transformation(
            new Vector3f(0f, 0.25f, 0f),
            new Quaternionf(),
            new Vector3f(1f, 1f, 1f),
            new Quaternionf());

    /** Poniżej tej prędkości (bloki/tick) spławik uznajemy za „spoczywający". */
    private static final double SETTLE_SPEED = 0.08;
    /** Tyle kolejnych ticków musi być wolny, zanim uznamy, że spoczął. */
    private static final int SETTLE_TICKS = 5;
    /**
     * Jak wysoko nad taflą wody (bloki) może unosić się spławik, by uznać, że
     * siedzi NA wodzie (vanilla unosi spławik minimalnie nad powierzchnię).
     * Spławik wyżej = zawieszony na kolizji (krawędź/ściana tratwy) — nie
     * kotwiczymy go w powietrzu (to był bug „hak zatrzymuje się i opada").
     */
    private static final double SURFACE_SLACK = 0.8;
    /** Tyle ticków spławik musi stać nieruchomo, by uznać go za zawieszonego. */
    private static final int STUCK_TICKS = 15;
    /** Jak głęboko (bloki) szukać wody pod zawieszonym spławikiem. */
    private static final int WATER_SCAN_DEPTH = 3;
    /** Co ile ticków odświeżamy żywotność spławika (zapobiega auto-zwinięciu). */
    private static final int LIFETIME_REFRESH = 20;

    private final RaftPlugin plugin;
    private final int hookCmd;
    private final KeyStateReceiver keyStates;
    private final Map<UUID, ActiveHook> hooks = new HashMap<>();
    /** Wizualy nabitych przedmiotów — jeden Display na nabity przedmiot (stos). */
    private final Map<UUID, List<org.bukkit.entity.Entity>> hookVisuals = new HashMap<>();
    private int tickCounter;

    public HookManager(RaftPlugin plugin) {
        this.plugin = plugin;
        this.hookCmd = plugin.getParent().getConfig()
                .getInt("raft-hook-custom-model-data", DEFAULT_HOOK_CMD);
        // Rejestruj wyciszacz waniliowego plusku spławika (ProtocolLib; bez
        // niego działa fallback: plusk zostaje). Tylko świat Raft.
        pl.vd.raft.listeners.RaftHookSoundMuter.register(
                plugin.getParent(),
                plugin.getParent().getConfig().getString("raft-world-name", "RAFT"));

        this.keyStates = new KeyStateReceiver(plugin);
    }

    public void start() {
        plugin.getParent().getServer().getScheduler().runTaskTimer(
                plugin.getParent(), this::tick, 1L, 1L);
    }

    /** Dostęp do odbiornika stanu przycisków moda — do wykrywania graczy z modem. */
    public KeyStateReceiver getKeyStateReceiver() {
        return keyStates;
    }

    /** Czy przedmiot to Hak (wędka FISHING_ROD z naszym custom_model_data). */
    public boolean isHookItem(ItemStack item) {
        if (item == null || item.getType() != Material.FISHING_ROD) return false;
        if (!item.hasItemMeta() || !item.getItemMeta().hasCustomModelData()) return false;
        return item.getItemMeta().getCustomModelData() == hookCmd;
    }

    // ─────────────────────── EVENTS ───────────────────────────

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.isCancelled()) return; // klik obsłużony wcześniej (np. GUI sieci zbierającej)
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        if (!isHookItem(event.getItem())) return;

        Player player = event.getPlayer();
        ActiveHook existing = hooks.get(player.getUniqueId());

        // Brak aktywnego haka — NIE anulujemy: waniliowy rzut wędką odpala
        // PlayerFishEvent(FISHING), które przejmuje HookManager (adoptHook).
        // Dzięki temu klient rysuje linkę — to sedno poprawnego rzutu.
        if (existing == null) {
            return;
        }

        // Gracz z modem: PPM zjedzone — o ruchu haka decyduje tick na podstawie
        // Gracz z modem: PPM zjedzone — o ruchu haka decyduje tick na podstawie
        // stanu przycisków (trzymany PPM = płynne przybliżanie). Wyjątek:
        // Shift+PPM = NATYCHMIASTOWE zwinięcie haka z przedmiotami (jak wanilia).
        KeyStateReceiver.KeyState keys = keyStates.get(player.getUniqueId());
        if (keys != null) {
            if (player.isSneaking()) {
                reelIn(player, existing, true);
            }
            event.setCancelled(true);
            return;
        }

        // Gracz bez moda (sterowanie wyrównane): Shift+PPM = natychmiastowe
        // zwinięcie; tap PPM = start/stop PŁYNNIEGO przyciągania (toggle),
        // bo wanilia nie wysyła „puszczenia" przycisku — trzymania nie da się
        // wykryć, więc zamiast niego dwa stany przełączane tapem.
        event.setCancelled(true);
        if (player.isSneaking()) {
            reelIn(player, existing, true);
        } else if (!existing.returning) {
            existing.pulling = !existing.pulling;
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        keyStates.onJoin(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        removeHook(playerId);
        keyStates.onQuit(playerId);
    }

    /**
     * Waniliowy rzut wędką (stan FISHING) z hakiem w ręce = nasz rzut: przejmujemy
     * spławik (tag, zero brania, nieskończona żywotność), ale NIE ruszamy jego
     * fizyki — leci normalnie i spoczywa tam, gdzie chce wanilia.
     */
    @EventHandler
    public void onPlayerFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        FishHook hook = event.getHook();

        switch (event.getState()) {
            case FISHING -> {
                if (!isHookItem(player.getInventory().getItemInMainHand())
                        && !isHookItem(player.getInventory().getItemInOffHand())) {
                    return; // zwykła wędka — zostaw wanilię
                }
                adoptHook(player, hook);
            }
            case CAUGHT_ENTITY -> {
                // Nasz hak nie łapie mobów/graczy — anuluj (np. trafienie w ptaka).
                if (hook != null && hook.getScoreboardTags().contains(HOOK_TAG)) {
                    event.setCancelled(true);
                }
            }
            case REEL_IN, CAUGHT_FISH, FAILED_ATTEMPT -> {
                // Wanilia sama zwija spławik (awaryjnie) — oddaj nabite i posprzątaj.
                ActiveHook active = hooks.remove(player.getUniqueId());
                if (active != null && hook != null
                        && hook.getScoreboardTags().contains(HOOK_TAG)) {
                    removeHookVisuals(player.getUniqueId());
                    deliverItems(player, active.attached);
                    deliverBarrelLoot(player, active.pendingBarrels);
                    deliverBigBarrelLoot(player, active.pendingBigBarrels);
                    active.attached.clear();
                    active.pendingBarrels = 0;
                    active.pendingBigBarrels = 0;
                    cleanupEntity(player, hook);
                }
            }
            default -> {
                // IN_GROUND itd. — nic.
            }
        }
    }

    /**
     * Zeruje licznik żywotności spławika (NMS reflection), żeby wanilia nie
     * zwineła go sama po ~minucie. Wywoływane co LIFETIME_REFRESH ticków.
     */
    private void neutralizeLifetime(FishHook hook) {
        try {
            Class<?> craftEntity = Class.forName("org.bukkit.craftbukkit.entity.CraftEntity");
            Object nms = craftEntity.getMethod("getHandle").invoke(hook);
            Field lifetime = nms.getClass().getDeclaredField("lifetime");
            lifetime.setAccessible(true);
            lifetime.setInt(nms, 0);
        } catch (Exception ignored) {
            // NMS się zmienił — spławik po prostu zniknie po chwili (fallback).
        }
    }

    /**
     * Czyści referencję „aktywny spławik" u gracza (NMS), jeśli wskazuje na nasz
     * spławik. Bez tego po ręcznym usunięciu encji gracz nie mógłby rzucić
     * ponownie, dopóki wanilia sama nie zwolniłaby referencji.
     */
    private void clearOwnerFishing(Player owner, FishHook hookEntity) {
        try {
            Object handle = hookEntity.getClass().getMethod("getHandle").invoke(hookEntity);
            Class<?> craftPlayer = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer");
            Object playerHandle = craftPlayer.getMethod("getHandle").invoke(owner);
            Class<?> clazz = playerHandle.getClass();
            while (clazz != null) {
                try {
                    Field fishing = clazz.getDeclaredField("fishing");
                    fishing.setAccessible(true);
                    if (fishing.get(playerHandle) == handle) {
                        fishing.set(playerHandle, null);
                    }
                    return;
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                }
            }
        } catch (Exception ignored) {
            // NMS się zmienił — wanilia i tak zwolni referencję sama.
        }
    }

    // ─────────────────────── PRZEJĘCIE RZUTU ──────────────────

    /** Przejmuje świeżo rzucony waniliowy spławik (fizyka zostaje waniliowa). */
    private void adoptHook(Player player, FishHook hookEntity) {
        UUID playerId = player.getUniqueId();

        // Hak ma być CAŁKOWICIE cichy — żaden dźwięk rzutu (nasz ani waniliowy;
        // waniliowe dźwięki spławika wycisza RaftHookSoundMuter przez ProtocolLib).

        // Gdyby istniał stary stan (np. podwójny rzut) — posprzątaj.
        ActiveHook old = hooks.remove(playerId);
        if (old != null) {
            cleanupHook(old);
        }

        World world = hookEntity.getWorld();
        hookEntity.addScoreboardTag(HOOK_TAG);
        hookEntity.setBiteChance(0.0);            // nigdy nie „bierze" (nie łowi ryb)
        hookEntity.setWaitTime(Integer.MAX_VALUE); // brak prób brania / plusków
        neutralizeLifetime(hookEntity);

        ActiveHook hook = new ActiveHook(playerId, hookEntity.getUniqueId(), world);
        hooks.put(playerId, hook);
        // Bez dźwięku rzutu (cisza haka).
    }

    // ─────────────────────── TICK ─────────────────────────────

    private void tick() {
        tickCounter++;

        Iterator<Map.Entry<UUID, ActiveHook>> it = hooks.entrySet().iterator();
        while (it.hasNext()) {
            ActiveHook hook = it.next().getValue();
            if (!updateHook(hook)) {
                cleanupHook(hook);
                it.remove();
            }
        }
    }

    /** Zwraca false, gdy hak ma zniknąć (cleanup odda nabite przedmioty). */
    private boolean updateHook(ActiveHook hook) {
        Player owner = Bukkit.getPlayer(hook.playerId);
        if (owner == null || !owner.isOnline() || owner.isDead()
                || owner.getWorld() != hook.world) {
            return false;
        }
        // Zmiana slotu / wyjęcie haka z ręki = hak wraca do gracza.
        if (!isHookItem(owner.getInventory().getItemInMainHand())) {
            return false;
        }

        org.bukkit.entity.Entity entity = hook.world.getEntity(hook.entityId);
        if (entity == null || entity.isDead()) {
            return false; // spławik zniknął (odczepiony/despawn) — posprzątaj
        }

        // Wanilia przypina spławik do pierwszej żywej encji z hitboxem na drodze
        // — a tratwa to CAŁA z niewidzialnych shulkerów-kolizji. Niski rzut
        // trafiał w taki hitbox i spławik wisiał „na niewidzialnej ścianie".
        // Odczepiamy natychmiast (patrz releaseRaftAttach).
        if (entity instanceof FishHook fh) releaseRaftAttach(hook, fh);

        Location loc = entity.getLocation();
        hook.lastPos = loc.toVector();

        // Czy wizualy mają podążać za spławikiem? Tylko gdy spławik się RUSZA
        // (lot po rzucie, zwijanie, przyciąganie trzymanym PPM). Po zakotwiczeniu
        // haka wizualy są „zamrożone" — stoją nieruchomo: waniliowy spławik buja
        // się na wodzie i nie przenosimy tego bujania na przedmioty na haku.
        boolean visualsMoving = !hook.anchored || hook.returning || hook.pulling;
        if (hook.anchored && hook.onWater && !hook.returning && !hook.pulling) {
            KeyStateReceiver.KeyState keys = keyStates.get(owner.getUniqueId());
            if (keys != null && keys.use) visualsMoving = true; // trzymany PPM = przyciąganie (mod)
        }
        hook.visualsMoving = visualsMoving;

        // Wizualy nabitych przedmiotów: podążają za środkiem spławika w ruchu,
        // a na zakotwiczonym haku stoją zamrożone w miejscu.
        updateHookVisuals(hook, entity);

        if (hook.returning) {
            return updateReturn(hook, owner, entity);
        }

        // Toggle-pull (gracze bez moda): hak płynie do gracza, dopóki gracz
        // nie tapnie ponownie (drugi tap = stop w miejscu). Działa w locie
        // i po zakotwiczeniu; na wodzie wciąż nabija przedmioty po drodze.
        if (hook.pulling) {
            holdReel(hook, owner, entity);
            if (hook.onWater) {
                catchItems(hook, owner);
            }
            return true;
        }

        // Odśwież żywotność, żeby wanilia nie zwineła haka po ~minucie.
        if (tickCounter % LIFETIME_REFRESH == 0 && entity instanceof FishHook fh) {
            neutralizeLifetime(fh);
        }

        if (!hook.anchored) {
            return updateSettle(hook, entity);
        }
        return updateAnchored(hook, owner, entity);
    }

    // ─────────────────────── ZWIJANIE (REEL) ──────────────────

    /**
     * Zaczyna zwijać hak do gracza; nabite przedmioty trafią do ekwipunku.
     * fast=true = natychmiastowy powrót (Shift+PPM, jak waniliowe zwinięcie).
     */
    private void reelIn(Player player, ActiveHook hook, boolean fast) {
        if (hook.returning) return;
        hook.pulling = false;
        hook.returning = true;
        hook.reelTicks = REEL_TICKS;
        hook.reelFast = fast;
        // Bez dźwięku zwijania (cisza haka).
    }

    private boolean updateReturn(ActiveHook hook, Player owner,
                                 org.bukkit.entity.Entity entity) {
        hook.reelTicks--;
        Location eye = owner.getEyeLocation();
        Vector toEye = eye.toVector().subtract(entity.getLocation().toVector());
        double dist = toEye.length();
        if (hook.reelTicks <= 0 || dist < 1.2) {
            deliverItems(owner, hook.attached);
            deliverBarrelLoot(owner, hook.pendingBarrels);
            deliverBigBarrelLoot(owner, hook.pendingBigBarrels);
            hook.attached.clear();
            hook.pendingBarrels = 0;
            hook.pendingBigBarrels = 0;
            return false; // hak dotarł — cleanup usunie spławik
        }
        // Spławik płynie do gracza (klient rysuje zwijaną linę).
        double step = hook.reelFast ? REEL_FAST_SPEED : REEL_SPEED;
        entity.teleport(entity.getLocation().add(toEye.normalize().multiply(
                Math.min(step, dist))));
        return true;
    }

    /** Wkłada nabite przedmioty do ekwipunku gracza; nadmiar wypada obok.
     *  BEZ dźwięku podniesienia — jedyny dźwięk cyklu haka to plusk przy
     *  nabiciu przedmiotu na hak (jak ustalone; beczki mają swoje pluski). */
    private void deliverItems(Player player, List<ItemStack> items) {
        if (items == null || items.isEmpty()) return;
        for (ItemStack stack : items) {
            Map<Integer, ItemStack> left = player.getInventory().addItem(stack);
            for (ItemStack rest : left.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), rest);
            }
        }
    }

    // ─────────────────── SPOCZYNEK PO RZUCIE ──────────────────

    /**
     * Czeka, aż waniliowy spławik przestanie się ruszać. Gdy spocznie NA WODZIE
     * (w wodzie albo tuż nad taflą) — hak siada na wodzie (particles); na twardym
     * bloku — zahacza się. Spławik zawieszony na kolizji tratwy (niewidzialne
     * shulkery-deski: vanilla „przypina" go do hitboxa i sam nie spadnie) jest
     * uwalniany — hak opada do wody obok tratwy zamiast wisić w powietrzu.
     */
    private boolean updateSettle(ActiveHook hook, org.bukkit.entity.Entity entity) {
        Vector now = entity.getLocation().toVector();
        // Prędkość z PRAWDZIWEJ zmiany pozycji między tickami: hook.lastPos jest
        // już nadpisany bieżącą pozycją w updateHook, więc różnica do niego
        // zawsze wychodziła 0 („spoczynek" łapał spławik jeszcze w locie).
        Vector prev = hook.prevPos;
        double speed = prev == null ? 99.0 : prev.distance(now);
        hook.prevPos = now.clone();

        if (speed < SETTLE_SPEED) {
            hook.slowTicks++;
        } else {
            hook.slowTicks = 0;
        }
        // Ostatni wektor lotu — po odczepie od kolizji tratwy spławik wraca do
        // lotu z tą prędkością (przy zaczepie wanilia zeruje jego ruch).
        if (prev != null) {
            Vector delta = now.clone().subtract(prev);
            if (speed > SETTLE_SPEED && delta.lengthSquared() > 1.0E-6) {
                hook.lastVel = delta;
            }
        }
        hook.lastPos = now;

        if (hook.slowTicks < SETTLE_TICKS) {
            return true; // jeszcze leci / kołysze się
        }

        Location loc = entity.getLocation();
        Block at = loc.getBlock();
        Block below = at.getY() > entity.getWorld().getMinHeight()
                ? entity.getWorld().getBlockAt(at.getX(), at.getY() - 1, at.getZ())
                : null;

        // Siadanie na wodzie: spławik W wodzie albo tuż NAD taflą (vanilla unosi
        // go minimalnie nad powierzchnię). Celowo NIE kotwiczymy spławika wiszącego
        // wysoko nad wodą — to znak, że jest przypięty do kolizji (krawędź/ściana
        // tratwy) i wyglądało to jak „zamrożenie w powietrzu + upadek".
        boolean onSurface = below != null && below.getType() == Material.WATER
                && loc.getY() - (below.getY() + 1.0) <= SURFACE_SLACK;
        if (at.getType() == Material.WATER || onSurface) {
            anchorHook(hook, entity, loc, true);
            return true;
        }

        // Ląd/wyspa (prawdziwy blok pod spławikiem) — zahacza się o blok.
        if (below != null && below.getType().isSolid()) {
            anchorHook(hook, entity, loc, false);
            return true;
        }

        // Spławik zawieszony w powietrzu (przypięty przez wanilię do hitboxa).
        if (hook.slowTicks >= STUCK_TICKS) {
            unstickStuckHook(hook, entity, loc);
        }
        return true;
    }

    /**
     * Zakotwicza hak w miejscu spoczynku. Zatrzymuje spławik DOKŁADNIE w miejscu:
     * bez tego resztkowy poślizg po wodzie (rzut pod kątem, nie od góry) sunął
     * hakiem jeszcze ~0.2–0.3 bloka po zakotwiczeniu, a zamrożone wizualy
     * przedmiotów zostawały w punkcie złapania — wyglądały, jakby wisiały OBOK haka.
     */
    private void anchorHook(ActiveHook hook, org.bukkit.entity.Entity entity,
                            Location loc, boolean onWater) {
        hook.anchored = true;
        hook.onWater = onWater;
        entity.setVelocity(new Vector(0, 0, 0));
        if (onWater) {
            // Particles zostają (wizual), dźwięk plusku usunięty (cisza haka).
            entity.getWorld().spawnParticle(Particle.SPLASH, loc, 14, 0.4, 0.2, 0.4);
        }
        // Ląd: BEZ particles — spławik lądujący na kolizjach tratwy (shulkery /
        // stojaki) wyglądał, jakby „krytycznie" trafiał w nie (Particle.CRIT).
    }

    /**
     * Uwalnia spławik przypięty przez wanilię do niewidzialnej kolizji tratwy
     * (shulkery tworzące deski/ściany). Rozróżnienie:
     *  - hitbox CIĄGNIE się ponad spławik (bok krawędzi/ściany) → upuść haka
     *    do wody obok tratwy (woda szukana prosto w dół, przez powietrze),
     *  - spoczynek NA desce (góra hitboxa ≈ spławik) → zostaw, jak dotychczas.
     */
    private void unstickStuckHook(ActiveHook hook, org.bukkit.entity.Entity entity,
                                  Location loc) {
        boolean sideWall = false;
        boolean onRaftTop = false;
        for (org.bukkit.entity.Entity e : entity.getWorld().getNearbyEntities(
                loc, 1.2, 1.6, 1.2)) {
            if (!e.getScoreboardTags().contains(RaftEntity.TAG_COLLIDER)) continue;
            double bbTop = e.getBoundingBox().getMaxY();
            double bbBottom = e.getBoundingBox().getMinY();
            if (loc.getY() >= bbBottom - 0.05 && bbTop - loc.getY() > 0.4) {
                sideWall = true; // bok krawędzi/ściany — spławik przypięty od boku
            }
            if (Math.abs(bbTop - loc.getY()) <= 0.35) {
                onRaftTop = true; // góra deski ≈ spławik — spoczywa NA tratwie
            }
        }
        if (sideWall && !onRaftTop) {
            Block water = findWaterBelow(loc, WATER_SCAN_DEPTH);
            if (water != null) {
                Location surface = new Location(entity.getWorld(), loc.getX(),
                        water.getY() + 1.05, loc.getZ(), loc.getYaw(), loc.getPitch());
                entity.teleport(surface);
                entity.setVelocity(new Vector(0, 0, 0));
                hook.slowTicks = 0; // niech spokojnie osiądzie taflę i zakotwiczy się
                return;
            }
        }
        hook.slowTicks = 0; // reszta: wisi na bloku/desce (jak dotychczas) — czekaj
    }

    /**
     * Wanilia przypina spławik do pierwszej żywej encji z hitboxem na drodze
     * (`FishingHook.canHitEntity`: żywe encje + przedmioty), a tratwa jest CAŁA
     * zbudowana z niewidzialnych shulkerów-kolizji (deski/ściany). Niski rzut
     * trafiał w taki hitbox, wanilia zerowała prędkość i snapowała spławik do
     * jego środka — hak wisiał w powietrzu jak zahaczony o „niewidzialną ścianę",
     * a wysoki rzut (ponad hitboxy) leciał normalnie. Haka nie wolno przypinać
     * do tratwy: odczepiamy od razu i puszczamy dalej z ostatnią prędkością lotu,
     * więc zachowuje się jak w wanilii — leci swobodnie, aż wleci do wody.
     * Encje spoza tratwy (moby, przedmioty) zostają waniliowe.
     */
    private void releaseRaftAttach(ActiveHook hook, FishHook fh) {
        org.bukkit.entity.Entity hooked = fh.getHookedEntity();
        if (hooked == null || !RaftEntity.isRaftEntity(hooked)) return;
        fh.setHookedEntity(null); // wanilia wraca do FLYING w następnym ticku
        Vector v = hook.lastVel != null ? hook.lastVel.clone() : null;
        if (v == null || v.lengthSquared() < 1.0E-6) {
            v = new Vector(0, -0.12, 0); // brak zapamiętanego lotu — swobodny opad
        }
        fh.setVelocity(v);
        // Krok wzdłuż lotu: po zaczepie spławik siedzi w ŚRODKU hitboxa, więc bez
        // tego odczepiałby się i zaczepiał o tę samą kolizję co tick.
        fh.teleport(fh.getLocation().add(v.clone().normalize().multiply(0.35)));
        hook.slowTicks = 0;
        hook.prevPos = null; // prędkość mierzona od nowego miejsca
        hook.lastPos = fh.getLocation().toVector();
    }

    /** Szuka wody prosto w dół (przez powietrze) do maxDown bloków; null gdy brak. */
    private Block findWaterBelow(Location loc, int maxDown) {
        World world = loc.getWorld();
        int x = loc.getBlockX();
        int z = loc.getBlockZ();
        for (int dy = 1; dy <= maxDown; dy++) {
            int y = loc.getBlockY() - dy;
            if (y < world.getMinHeight()) return null;
            Block b = world.getBlockAt(x, y, z);
            if (b.getType() == Material.WATER) return b;
            if (!b.getType().isAir()) return null; // przeszkoda (wyspa/klif)
        }
        return null;
    }

    // ─────────────────── SPOCZYNEK NA WODZIE ──────────────────

    private boolean updateAnchored(ActiveHook hook, Player owner,
                                   org.bukkit.entity.Entity entity) {
        if (hook.onWater) {
            // Trzymany PPM (gracz z modem) = hak płynie w stronę gracza;
            // puszczenie przycisku = hak zostaje w wodzie. Shift+PPM zwija
            // natychmiast przy kliknięciu (onInteract), więc tu już nie ma go.
            KeyStateReceiver.KeyState keys = keyStates.get(owner.getUniqueId());
            if (keys != null && keys.use) {
                holdReel(hook, owner, entity);
            }
        }

        // Pływające przedmioty nabijają się na hak (tylko na wodzie).
        if (hook.onWater) {
            catchItems(hook, owner);
        }
        return true;
    }

    /**
     * Trzymany PPM (gracz z modem): hak płynie do gracza, dopóki przycisk jest
     * wciśnięty. Puszczenie = nic się nie dzieje (hak zostaje tam, gdzie jest
     * i dalej łapie przedmioty). Po dopłynięciu — przedmioty do ekwipunku.
     */
    private void holdReel(ActiveHook hook, Player owner,
                          org.bukkit.entity.Entity entity) {
        Location eye = owner.getEyeLocation();
        Vector toEye = eye.toVector().subtract(entity.getLocation().toVector());
        double dist = toEye.length();
        if (dist < 1.2) {
            deliverItems(owner, hook.attached);
            deliverBarrelLoot(owner, hook.pendingBarrels);
            deliverBigBarrelLoot(owner, hook.pendingBigBarrels);
            hook.attached.clear();
            hook.pendingBarrels = 0;
            hook.pendingBigBarrels = 0;
            hook.pulling = false;
            hook.returning = true; // dotarł — za chwilę zniknie
            hook.reelTicks = 2;
            return;
        }
        entity.teleport(entity.getLocation().add(
                toEye.normalize().multiply(Math.min(1.3, dist * 0.5))));
    }

    /** Nabija pływające przedmioty na hak, aż do HOOK_CAPACITY sztuk. */
    private void catchItems(ActiveHook hook, Player owner) {
        int total = 0;
        for (ItemStack s : hook.attached) total += s.getAmount();
        if (total >= HOOK_CAPACITY) return;

        org.bukkit.entity.Entity entity = hook.world.getEntity(hook.entityId);
        if (entity == null) return;
        Location loc = entity.getLocation();

        for (org.bukkit.entity.Entity e : hook.world.getNearbyEntities(
                loc, CATCH_RADIUS_XZ, CATCH_RADIUS_Y, CATCH_RADIUS_XZ)) {
            if (total >= HOOK_CAPACITY) break;

            // Beczka (BlockDisplay) nabija się na hak jako WISZĄCA beczka
            // (pełnowymiarowy wizual bloku na środku spławika). Loot NIE jest
            // losowany teraz — dopiero przy zwinięciu haka (deliverBarrelLoot)
            // trafia wtedy do ekwipunku gracza, z tej samej tabeli szans co
            // rozbicie prawym klikiem (rollBarrelLoot).
            if (!(e instanceof Item)) {
                if (e.isDead() || !e.isValid()) continue;
                if (e.getScoreboardTags().contains(OceanDebrisManager.TAG_BARREL)) {
                    boolean big = OceanDebrisManager.isBigBarrel(e);
                    if (big) {
                        hook.pendingBigBarrels++;
                    } else {
                        hook.pendingBarrels++;
                    }
                    e.remove();
                    total += 1;
                    // Wyrazisty plusk w momencie złapania beczki na hak.
                    hook.world.playSound(loc, Sound.ENTITY_PLAYER_SPLASH, 0.9f, 1.3f);
                }
                continue;
            }
            Item item = (Item) e;
            if (item.isDead() || !item.isValid()) continue;

            ItemStack stack = item.getItemStack();
            if (stack == null || stack.getType() == Material.AIR) continue;

            int take = Math.min(HOOK_CAPACITY - total, stack.getAmount());
            if (take <= 0) continue;

            ItemStack toAdd = stack.clone();
            toAdd.setAmount(take);

            // Scal z identycznym stackiem już nabitym.
            boolean merged = false;
            for (ItemStack s : hook.attached) {
                if (s.isSimilar(toAdd)) {
                    s.setAmount(s.getAmount() + take);
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                hook.attached.add(toAdd);
            }

            if (stack.getAmount() > take) {
                stack.setAmount(stack.getAmount() - take);
                item.setItemStack(stack);
            } else {
                item.remove();
            }

            total += take;
            // Ten sam dźwięk co przy złapaniu beczki: plusk wody („słychać
            // nabijanie" każdego przedmiotu, nie tylko beczki).
            hook.world.playSound(loc, Sound.ENTITY_PLAYER_SPLASH, 0.9f, 1.3f);
        }
    }

    // ─────────────────── WIZUALY NABITYCH PRZEDMIOTÓW ──────────

    /**
     * Utrzymuje wizualy nabitych przedmiotów: po JEDNYM Display na każdy
     * wpis w {@code attached}, każdy IDEALNIE NA ŚRODKU spławika (przedmioty
     * mogą na siebie nachodzić). Beczka = pełnowymiarowy BlockDisplay 1×1×1,
     * zwykły przedmiot = mały ItemDisplay (jak drop w ręce).
     */
    private void updateHookVisuals(ActiveHook hook, org.bukkit.entity.Entity hookEntity) {
        List<org.bukkit.entity.Entity> visuals = hookVisuals.computeIfAbsent(
                hook.playerId, k -> new ArrayList<>());

        int wantTotal = hook.attached.size()
                + hook.pendingBarrels + hook.pendingBigBarrels;

        // Pozycja haka BEZ rotacji (yaw=0/pitch=0): wizualy nie dziedziczą
        // obrotu spławika (waniliowy spławik kręci się w locie i kładł
        // przedmioty w losowej rotacji/przechyleniu).
        Location anchor = hookVisualLocation(hookEntity);

        // Dociągnij liczbę wizualów do liczby nabitych przedmiotów i beczek.
        while (visuals.size() < wantTotal) {
            int idx = visuals.size();
            org.bukkit.entity.Entity created;
            if (idx < hook.attached.size()) {
                created = spawnHookVisual(anchor, hook.attached.get(idx));
            } else if (idx < hook.attached.size() + hook.pendingBigBarrels) {
                created = spawnHookVisual(anchor, OceanDebrisManager.createBigBarrelStack());
            } else {
                created = spawnHookVisual(anchor, BARREL_STACK());
            }
            if (created == null) break;
            visuals.add(created);
        }
        // Usuń nadmiarowe (po oddaniu przedmiotów przy zwijaniu itd.).
        while (visuals.size() > wantTotal) {
            org.bukkit.entity.Entity last = visuals.remove(visuals.size() - 1);
            if (last != null && last.isValid()) last.remove();
        }
        // Synchronizuj model i pozycję każdego wizualu (co tick).
        for (int i = 0; i < visuals.size(); i++) {
            ItemStack stack;
            boolean bigBarrel = false;
            if (i < hook.attached.size()) {
                stack = hook.attached.get(i);
            } else if (i < hook.attached.size() + hook.pendingBigBarrels) {
                stack = OceanDebrisManager.createBigBarrelStack(); // nadmiarowe = wiszące DUŻE beczki
                bigBarrel = true;
            } else {
                stack = BARREL_STACK(); // nadmiarowe wizualy = wiszące beczki
            }
            org.bukkit.entity.Entity visual = visuals.get(i);
            boolean wantItemDisplay = stack.getType() != Material.BARREL || bigBarrel;
            if (visual == null || !visual.isValid() || visual.isDead()
                    || visual.getWorld() != hook.world
                    || wantItemDisplay != (visual instanceof ItemDisplay)) {
                // Brak / martwy / zły typ (przedmiot ↔ beczka) — przeładowanie.
                if (visual != null && visual.isValid()) visual.remove();
                org.bukkit.entity.Entity created = spawnHookVisual(anchor, stack, bigBarrel);
                if (created != null) visuals.set(i, created);
                continue;
            }
            if (visual instanceof ItemDisplay itemDisp) {
                ItemStack cur = itemDisp.getItemStack();
                if (cur == null || !cur.isSimilar(stack)) {
                    itemDisp.setItemStack(stack.clone());
                }
            }
            // Podążanie za hakiem: w locie / zwijaniu / przyciąganiu — co tick.
            // Na zakotwiczonym haku wizual jest „zamrożony” (mikro-bujanie wody
            // go nie rusza), ALE gdy spławik realnie odjedzie od wizualu dalej
            // niż VISUAL_FOLLOW_SNAP (poślizg, dryf) — wizual dogania hak, więc
            // przedmiot zawsze wisi dokładnie na jego środku.
            if (hook.visualsMoving
                    || visual.getLocation().distanceSquared(anchor) > VISUAL_FOLLOW_SNAP_SQ) {
                visual.teleport(anchor);
            }
        }
    }

    /**
     * Pozycja haka bez rotacji (yaw=0, pitch=0): wizual ma stać „na wprost",
     * a nie dziedziczyć obrotu spławika (waniliowy spławik kręci się w locie
     * i kładł przedmioty w losowej rotacji/przechyleniu).
     */
    private Location hookVisualLocation(org.bukkit.entity.Entity hookEntity) {
        Location loc = hookEntity.getLocation().clone();
        loc.setYaw(0f);
        loc.setPitch(0f);
        return loc;
    }

    /** Spawnuje wizual pojedynczego przedmiotu na haku (item albo beczka). */
    private org.bukkit.entity.Entity spawnHookVisual(Location center, ItemStack stack) {
        return spawnHookVisual(center, stack, false);
    }

    /**
     * Spawnuje wizual pojedynczego przedmiotu na haku.
     * {@code bigBarrel} = DUŻA beczka (ItemDisplay z custom modelem, CMD 4003).
     */
    private org.bukkit.entity.Entity spawnHookVisual(Location center, ItemStack stack,
                                                     boolean bigBarrel) {
        World world = center.getWorld();
        if (world == null || stack == null) return null;
        try {
            if (bigBarrel) {
                // DUŻA beczka: ItemDisplay z customowym modelem z resource packa
                // (HEAVY_CORE + custom_model_data 4003), pełna skala — jak na oceanie.
                return world.spawn(center, ItemDisplay.class, disp -> {
                    disp.setItemStack(OceanDebrisManager.createBigBarrelStack());
                    disp.setTransformation(HOOK_BIG_BARREL_TRANSFORM);
                    disp.setGravity(false);
                    disp.setGlowing(false);
                    disp.setSilent(true);
                    disp.setInvulnerable(true);
                    disp.setPersistent(false);
                    disp.addScoreboardTag(HOOK_VISUAL_TAG);
                });
            }
            if (stack.getType() == Material.BARREL) {
                // Beczka: pełnowymiarowy blok, środek = środek haka,
                // wieczko DO GÓRY (facing=up, jak pływająca beczka na oceanie).
                return world.spawn(center, BlockDisplay.class, disp -> {
                    org.bukkit.block.data.type.Barrel bd =
                            (org.bukkit.block.data.type.Barrel) Material.BARREL.createBlockData();
                    bd.setFacing(org.bukkit.block.BlockFace.UP);
                    disp.setBlock(bd);
                    disp.setTransformation(HOOK_BARREL_TRANSFORM);
                    disp.setGravity(false);
                    disp.setGlowing(false);
                    disp.setSilent(true);
                    disp.setInvulnerable(true);
                    disp.setPersistent(false);
                    disp.addScoreboardTag(HOOK_VISUAL_TAG);
                });
            }
            // Zwykły przedmiot: ItemDisplay z modelem z resource packa —
            // domyślny kontekst (jak „duch” śmiecia na oceanie), dzięki czemu
            // rozmiar i wyśrodkowanie są identyczne z pływającym śmieciem.
            return world.spawn(center, ItemDisplay.class, disp -> {
                disp.setItemStack(stack.clone());
                // BEZ billboardu i BEZ FIXED: przedmiot ma STAŁĄ orientację w
                // świecie („zamrożony” na haku, zawsze pionowo — żadnego
                // obracania za głową gracza, żadnego FIXED-offsetu modelu).
                disp.setTransformation(HOOK_ITEM_TRANSFORM);
                disp.setGravity(false);
                disp.setGlowing(false);
                disp.setSilent(true);
                disp.setInvulnerable(true);
                disp.setPersistent(false);
                disp.addScoreboardTag(HOOK_VISUAL_TAG);
            });
        } catch (Exception e) {
            plugin.getLogger().warning("[Raft] Nie udało się stworzyć wizualu na haku: " + e);
            return null;
        }
    }

    /** Wzorzec stacka beczki (do wizualu wiszącej beczki na haku). */
    private static ItemStack BARREL_STACK() {
        return new ItemStack(Material.BARREL, 1);
    }

    /**
     * Otwiera wiszące beczki przy zwinięciu haka: loot losowany TERAZ (z tej
     * samej tabeli co rozbicie/wyjście — rollBarrelLoot) trafia do ekwipunku;
     * nadmiar wypada obok gracza. Cicho — bez dźwięków i particles.
     */
    private void deliverBarrelLoot(Player player, int pendingBarrels) {
        deliverLoot(player, pendingBarrels, () -> {
            var deb = plugin.getOceanDebrisManager();
            return deb != null ? deb.rollBarrelLoot() : java.util.List.of();
        });
    }

    /** Otwiera wiszące DUŻE beczki przy zwinięciu haka (WŁASNA tabela lootu). */
    private void deliverBigBarrelLoot(Player player, int pendingBigBarrels) {
        deliverLoot(player, pendingBigBarrels, () -> {
            var deb = plugin.getOceanDebrisManager();
            return deb != null ? deb.rollBigBarrelLoot() : java.util.List.of();
        });
    }

    /**
     * Otwiera wiszące beczki przy zwinięciu haka: loot losowany TERAZ (z tej
     * samej tabeli co rozbicie/wyjście) trafia do ekwipunku; nadmiar wypada
     * obok gracza. Cicho — bez dźwięków i particles.
     */
    private void deliverLoot(Player player, int count,
                             java.util.function.Supplier<java.util.List<ItemStack>> lootSupplier) {
        if (count <= 0) return;
        for (int i = 0; i < count; i++) {
            for (ItemStack stack : lootSupplier.get()) {
                Map<Integer, ItemStack> left = player.getInventory().addItem(stack);
                for (ItemStack rest : left.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), rest);
                }
            }
        }
    }

    /** Usuwa wszystkie wizualy przedmiotów danego haka. */
    private void removeHookVisuals(UUID playerId) {
        List<org.bukkit.entity.Entity> visuals = hookVisuals.remove(playerId);
        if (visuals == null) return;
        for (org.bukkit.entity.Entity visual : visuals) {
            if (visual != null && visual.isValid()) visual.remove();
        }
        visuals.clear();
    }

    // ─────────────────────── SPRZĄTANIE ───────────────────────

    /** Usuwa hak gracza (despawn spławika + oddanie przedmiotów). */
    public void removeHook(UUID playerId) {
        ActiveHook hook = hooks.remove(playerId);
        if (hook != null) cleanupHook(hook);
    }

    private void cleanupHook(ActiveHook hook) {
        removeHookVisuals(hook.playerId);
        // Wiszące beczki: loot losowany i oddawany także przy awaryjnym
        // sprzątaniu (wyjęcie haka z ręki, rozłączenie, śmierć gracza itd.).
        if (hook.pendingBarrels > 0 || hook.pendingBigBarrels > 0) {
            Player barrelOwner = Bukkit.getPlayer(hook.playerId);
            if (barrelOwner != null && barrelOwner.isOnline()
                    && barrelOwner.getWorld() == hook.world) {
                deliverBarrelLoot(barrelOwner, hook.pendingBarrels);
                deliverBigBarrelLoot(barrelOwner, hook.pendingBigBarrels);
            } else {
                var deb = plugin.getOceanDebrisManager();
                Location dropAt = hook.lastPos != null
                        ? hook.lastPos.toLocation(hook.world)
                        : (barrelOwner != null ? barrelOwner.getLocation() : null);
                if (deb != null && dropAt != null) {
                    for (int i = 0; i < hook.pendingBarrels; i++) {
                        for (ItemStack stack : deb.rollBarrelLoot()) {
                            hook.world.dropItemNaturally(dropAt, stack);
                        }
                    }
                    for (int i = 0; i < hook.pendingBigBarrels; i++) {
                        for (ItemStack stack : deb.rollBigBarrelLoot()) {
                            hook.world.dropItemNaturally(dropAt, stack);
                        }
                    }
                }
            }
            hook.pendingBarrels = 0;
            hook.pendingBigBarrels = 0;
        }
        // Oddaj nabite przedmioty, zanim spławik zniknie.
        if (!hook.attached.isEmpty()) {
            Player owner = Bukkit.getPlayer(hook.playerId);
            if (owner != null && owner.isOnline() && owner.getWorld() == hook.world) {
                deliverItems(owner, hook.attached);
            } else {
                Location dropAt = hook.lastPos != null
                        ? hook.lastPos.toLocation(hook.world)
                        : null;
                for (ItemStack s : hook.attached) {
                    if (dropAt != null) {
                        hook.world.dropItemNaturally(dropAt, s);
                    } else if (owner != null) {
                        owner.getWorld().dropItemNaturally(owner.getLocation(), s);
                    }
                }
            }
            hook.attached.clear();
        }

        org.bukkit.entity.Entity entity = hook.world.getEntity(hook.entityId);
        if (entity != null && entity.isValid() && !entity.isDead()) {
            Player owner = Bukkit.getPlayer(hook.playerId);
            if (owner != null && entity instanceof FishHook fh) {
                clearOwnerFishing(owner, fh);
            }
            entity.remove();
        }

    }

    /** Despawn encji spławika i zwolnienie referencji u gracza. */
    private void cleanupEntity(Player owner, FishHook hookEntity) {
        clearOwnerFishing(owner, hookEntity);
        if (hookEntity.isValid() && !hookEntity.isDead()) {
            hookEntity.remove();
        }
    }

    // ─────────────────────── STAN HAKA ────────────────────────

    private static final class ActiveHook {
        final UUID playerId;
        final UUID entityId;      // waniliowy spławik (FishHook)
        final World world;
        final List<ItemStack> attached = new ArrayList<>();
        /** Wiszące beczki (otwierane przy zwinięciu haka). */
        int pendingBarrels = 0;
        /** Wiszące DUŻE beczki (custom model, własna tabela lootu). */
        int pendingBigBarrels = 0;
        Vector lastPos;           // ostatnia znana pozycja spławika
        Vector prevPos;           // pozycja z poprzedniego ticku (pomiar prędkości)
        Vector lastVel;           // ostatni wektor lotu (do wznowienia po odczepie)
        boolean anchored = false;
        boolean onWater = false;  // hak siedzi na wodzie (łapie przedmioty)
        boolean returning = false;
        boolean reelFast = false; // natychmiastowy powrót (Shift+PPM)
        /** Toggle-pull graczy bez moda: true = płynne przyciąganie aktywne. */
        boolean pulling = false;
        boolean visualsMoving = false; // wizualy podążają za spławikiem (ruch)
        int reelTicks = 0;
        int slowTicks = 0;        // ile ticków spławik stoi w miejscu

        ActiveHook(UUID playerId, UUID entityId, World world) {
            this.playerId = playerId;
            this.entityId = entityId;
            this.world = world;
        }
    }
}
