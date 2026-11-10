package pl.vd.raft.entity;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import pl.vd.raft.RaftPlugin;
import pl.vd.raft.hook.KeyStateReceiver;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Serwerowy fallback żagla tratwy dla graczy BEZ moda raft-hud.
 *
 * Gracz z modem widzi żagiel jako MESH rysowany przez klienta (kanał
 * {@code raft:sails} + {@code RaftSailWorldRenderer}) — zero encji. Gracz bez
 * moda nie ma czym tego narysować, więc serwer stawia mu w świecie dwie
 * display-encje:
 *
 *  - {@link ItemDisplay} — MASZT (vaniliowy blok-przedmiot przeskalowany do
 *    cienkiego słupa),
 *  - {@link BlockDisplay} — PŁÓTNO (cienka płyta wełny; rozwinięte = szeroka,
 *    zwinięte = wąska rolka przy maszcie).
 *
 * Kotwica i obrót są DOKŁADNIE te same, co w pakiecie {@code raft:sails}:
 * {@link RaftEntity#getSailAnchorWorld()} (światowa podstawa masztu — środek
 * komórki żagla, {@code MAST_BASE_Y} nad środkiem tratwy = górna
 * powierzchnia deski) oraz
 * {@link RaftEntity#getYawDegrees()} (obrót tratwy). Stan rozwinięcia bierze
 * się z {@link RaftEntity#isSailOpen()} — ten sam bit, który dostaje klient
 * moda. Dzięki temu fallback stoi w tym samym miejscu i tak samo się rozkłada,
 * jak żagiel gracza z modem.
 *
 * Rozpoznanie moda (dwie ścieżki, obie z kanałów moda):
 *  - {@link KeyStateReceiver#has(UUID)} — gracz z modem wysyła {@code raft:key_state}
 *    (od razu po wejściu), więc serwer wie, kto ma mesh,
 *  - {@code Player#getListeningPluginChannels()} — klient moda rejestruje odbiorniki
 *    clientbound (m.in. {@code raft:sails}), więc widać go jeszcze przed pierwszym
 *    pakietem {@code raft:key_state} (bez tego gracz z modem mrugał fallbackiem
 *    przez pierwsze ticki po dołączeniu).
 * U takich graczy fallback jest CHOWANY ({@code Player#hideEntity}), żeby nie widzieli
 * podwójnego żagla.
 *
 * Cykl życia: encje są odtwarzane w każdym {@code RaftEntity.syncAllVisuals()}
 * (co tick w FULL, co 4 ticki w FAR — jak deski tratwy), wygaszane razem
 * z deskami w trybie FROZEN ({@link #cull()}) i sprzątane przy rozbiórce
 * tratwy ({@link #cleanup()}). {@code persistent=false}, więc po rozładowaniu
 * chunku znikają i wracają przy najbliższym sync — albo od razu, gdy żagiel
 * zostanie zdjęty/założony (RaftEntity woła syncAllVisuals przy zmianie).
 */
public final class RaftSailFallbackRenderer {

    // ─── Zasięg pokazywania (spójny z deskami tratwy) ───────────────────────

    /** W tym promieniu od kotwicy pokazujemy fallback (jak DISPLAY_SPAWN_RANGE desek). */
    private static final double VIEW_RANGE = RaftEntity.DISPLAY_SPAWN_RANGE;
    private static final double VIEW_RANGE_SQ = VIEW_RANGE * VIEW_RANGE;

    /**
     * Co ile ticków WYMUSZAMY ponowne ukrycie u gracza z modem. hideEntity jest
     * idempotentne (Paper woła zdarzenie tylko przy realnej zmianie stanu), a to
     * bezpiecznik na wypadek ponownego zarejestrowania encji w trackerze gracza
     * (np. po wyjściu i powrocie w zasięg) — bez niego gracz z modem mógłby
     * zobaczyć fallback obok mesha.
     */
    private static final int HIDE_REAPPLY_INTERVAL = 20;

    /**
     * Kanały, po których poznajemy klienta z modem raft-hud: mod rejestruje odbiorniki
     * clientbound ({@code raft:sails} — mesh żagla, {@code raft:stats} — paski HUD,
     * {@code raft:offsets}, {@code raft:receivers}, {@code raft:musicvol},
     * {@code raft:session_end}), a na liście zostają też kanały serverbound
     * ({@code raft:key_state}, {@code raft:open_crafting}) — część klientów ogłasza
     * je razem z resztą.
     */
    private static final String[] MOD_CHANNELS = {
            "raft:key_state",
            "raft:open_crafting",
            "raft:sails",
            "raft:stats",
            "raft:offsets",
            "raft:receivers",
            "raft:musicvol",
            "raft:session_end"
    };
    /** Co ile ticków ponawiamy sprawdzenie kanałów, gdy moda jeszcze nie wykryto. */
    private static final int CHANNEL_CHECK_INTERVAL = 40;

    // ─── Geometria fallbacku (lokalnie: +Z = dziób tratwy, kotwica = podstawa masztu NA POKŁADZIE) ───
    //
    // Proporcje bierzemy z modelu paczki `raft:item/zagiel_fallback` (maszt 2×14 px,
    // płótno 12 px wysokie i 9 px długie) przeskalowanego ×2.5, żeby żagiel miał
    // rozmiar świata, a nie rozmiar trzymany w ręce. Grubość płótna 1 px = 0.0625
    // bloka — jak w siatce moda (`zagiel.json`). Wszystkie wymiary są liczone OD
    // PODSTAWY MASZTU (= kotwica pakietu `raft:sails` = górna powierzchnia deski),
    // więc fallback i mesh moda stoją w tej samej kolumnie i tej samej skali.

    /** Wysokość masztu (bloki) — 14 px modelu × 2.5. */
    private static final float MAST_HEIGHT = 2.5f;
    /**
     * Ile podstawy masztu wpuszczamy w deskę (bloki): dolna ściana słupa leżałaby
     * dokładnie w płaszczyźnie pokładu (z-fighting przy patrzeniu pod kątem),
     * więc kotwiczymy go 2 cm niżej — w desce tego nie widać, a migotanie znika.
     */
    private static final float MAST_SINK = 0.02f;
    /** Grubość masztu (bloki) — 4 px (model ma 2 px; pogrubiony, żeby nie ginął z dystansu). */
    private static final float MAST_WIDTH = 0.25f;

    /** Środek płótna nad podstawą masztu (bloki) — płótno wisi od 0.35 do 2.25. */
    private static final float CLOTH_CENTER_Y = 1.3f;
    /** Wysokość płótna (bloki) — 12 px modelu × 2.5. */
    private static final float CLOTH_HEIGHT = 1.9f;
    /** Szerokość płótna ROZWINIĘTEGO (wzdłuż belki tratwy) — 9 px modelu × 2.5. */
    private static final float CLOTH_OPEN_WIDTH = 1.4f;
    /** Szerokość płótna ZWINIĘTEGO (rolka przy maszcie). */
    private static final float CLOTH_ROLLED_WIDTH = 0.16f;
    /** Grubość płótna (bloki) — 1 px, jak w siatce moda. */
    private static final float CLOTH_THICKNESS = 0.0625f;
    /** Odsunięcie płótna ZA maszt (lokalne −Z = rufa), żeby nie przecinało masztu. */
    private static final float CLOTH_OFFSET_Z = -0.17f;
    /** Ile ticków trwa przejście płótna rozwinięte ↔ zwinięte (interpolacja klienta). */
    private static final int CLOTH_FURL_TICKS = 4;

    /**
     * Transformacja masztu: pełny blok 1×1×1 (model przedmiotu-bloku jest
     * wyśrodkowany na encji) przeskalowany do słupa 4 px × 2.5 bloka i podniesiony
     * o połowę wysokości (minus wpuszczenie w deskę), żeby PODSTAWA stała
     * dokładnie na kotwicy, czyli na górnej powierzchni pokładu.
     */
    private static final Transformation MAST_TRANSFORM = new Transformation(
            new Vector3f(0f, MAST_HEIGHT / 2f - MAST_SINK, 0f),
            new Quaternionf(),
            new Vector3f(MAST_WIDTH, MAST_HEIGHT, MAST_WIDTH),
            new Quaternionf());

    /** Przedmiot masztu — pełny blok (bez moda i bez paczki), czytelny jako drewniany słup. */
    private static final Material MAST_ITEM = Material.STRIPPED_OAK_LOG;
    /** Blok płótna — waniliowa biała wełna czyta się jak żagiel. */
    private static final Material CLOTH_BLOCK = Material.WHITE_WOOL;

    // ─── Stan ───────────────────────────────────────────────────────────────

    private final RaftEntity raft;
    private final UUID raftId;

    /** Maszt (ItemDisplay) i płótno (BlockDisplay); null = brak żagla / po cullingu. */
    private ItemDisplay mast;
    private BlockDisplay cloth;
    /** Ostatnio ustawiony stan płótna (null = jeszcze nie ustawiany). */
    private Boolean clothOpen;

    /** playerId -> czy MASZT jest aktualnie pokazany temu graczowi. */
    private final Map<UUID, Boolean> mastShown = new HashMap<>();
    /** playerId -> czy PŁÓTNO jest aktualnie pokazane temu graczowi. */
    private final Map<UUID, Boolean> clothShown = new HashMap<>();
    /** Licznik ticków sync — do okresowego wymuszania ukrycia u graczy z modem. */
    private int ticks;

    /** playerId -> wykryto moda po kanałach klienta (raz wykryte zostaje do wylogowania). */
    private final Map<UUID, Boolean> channelMod = new HashMap<>();
    /** playerId -> tick ostatniego sprawdzenia kanałów (gdy moda jeszcze nie wykryto). */
    private final Map<UUID, Integer> channelCheckedTick = new HashMap<>();

    public RaftSailFallbackRenderer(RaftEntity raft, UUID raftId) {
        this.raft = raft;
        this.raftId = raftId;
    }

    // ─────────────────────── SYNC (co tick / zmiana żagla) ──────────────────

    /**
     * Utrzymuje fallback w zgodzie ze stanem żagla tratwy: tworzy brakujące
     * encje, przesuwa je na kotwicę, obraca yawem tratwy, ustawia stan płótna
     * i pilnuje widoczności per gracz. Gdy tratwa nie ma żagla — sprząta.
     */
    public void sync() {
        RaftPlugin plugin = RaftPlugin.getInstance();
        World world = raft.getState().getWorld();
        if (plugin == null || world == null) return;

        Vector anchor = raft.hasSail() ? raft.getSailAnchorWorld() : null;
        if (anchor == null) {
            remove(); // żagiel zdjęty — fallback nie ma czego pokazywać
            return;
        }

        ticks++;
        float yaw = (float) raft.getYawDegrees();
        boolean open = raft.isSailOpen();

        // Encje odtwarzamy tak samo jak deski: persistent=false znika z chunkiem,
        // więc sync musi je dograć (świeżo zespawnowane encje są niewidoczne —
        // widoczność ustawiamy od zera w updateVisibility).
        if (!alive(mast, world)) {
            mast = spawnMast(world, anchor, yaw);
            mastShown.clear();
        } else {
            move(mast, anchor, yaw);
        }
        if (!alive(cloth, world)) {
            cloth = spawnCloth(world, anchor, yaw, open);
            clothOpen = open;
            clothShown.clear();
        } else {
            move(cloth, anchor, yaw);
            if (clothOpen == null || clothOpen != open) {
                cloth.setTransformation(clothTransform(open));
                clothOpen = open;
            }
        }

        updateVisibility(plugin, world, anchor);
    }

    // ─────────────────────── SPAWN ENCJI ────────────────────────────────────

    /** Maszt: ItemDisplay z blokiem-przedmiotem, skalowany do cienkiego słupa. */
    private ItemDisplay spawnMast(World world, Vector anchor, float yaw) {
        Location loc = anchorLocation(world, anchor, yaw);
        return world.spawn(loc, ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(MAST_ITEM, 1));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setTransformation(MAST_TRANSFORM);
            d.setBillboard(Display.Billboard.FIXED);
            applyCommonDisplayFlags(d);
        });
    }

    /** Płótno: cienka płyta wełny — szeroka po rozwinięciu, rolka po zwinięciu. */
    private BlockDisplay spawnCloth(World world, Vector anchor, float yaw, boolean open) {
        Location loc = anchorLocation(world, anchor, yaw);
        return world.spawn(loc, BlockDisplay.class, d -> {
            d.setBlock(CLOTH_BLOCK.createBlockData());
            d.setTransformation(clothTransform(open));
            d.setBillboard(Display.Billboard.FIXED);
            // Rozwijanie/zwijanie ma być przejściem (jak animacja mesha u gracza
            // z modem), a nie skokiem — klient interpoluje zmianę transformacji.
            d.setInterpolationDuration(CLOTH_FURL_TICKS);
            applyCommonDisplayFlags(d);
        });
    }

    /**
     * Wspólne flagi displaya mówiące „to wizual tratwy": nie zapisuje się do
     * chunku, nie da się go zabić, jest cichy, ma tagi tratwy (ochrona przed
     * zniszczeniem + sprzątanie osieroconych encji). Kluczowe:
     * {@code setVisibleByDefault(false)} — encja startuje NIEWIDOCZNA dla
     * wszystkich, więc gracze z modem nigdy nie dostają jej nawet w pakiecie
     * spawnu; widoczność dla graczy bez moda ustawia {@code updateVisibility}
     * przez {@code showEntity}.
     */
    private void applyCommonDisplayFlags(Display d) {
        d.setGravity(false);
        d.setInvulnerable(true);
        d.setSilent(true);
        d.setPersistent(false);
        d.setVisibleByDefault(false);
        d.addScoreboardTag(RaftEntity.TAG_VISUAL);
        d.addScoreboardTag("raft_" + raftId);
    }

    private static Location anchorLocation(World world, Vector anchor, float yaw) {
        return new Location(world, anchor.getX(), anchor.getY(), anchor.getZ(), yaw, 0f);
    }

    // ─────────────────────── RUCH I OBRÓT ───────────────────────────────────

    /**
     * Przesuwa display na kotwicę i ustawia yaw tratwy. NIE używamy
     * {@link RaftEntitySync#moveSmoothly} — tamten teleport nie niesie yaw
     * (Location bez rotacji), a żagiel musi być obrócony razem z tratwą.
     * Gdy pozycja i kąt się nie zmieniły, nie teleportujemy (zero pakietów).
     */
    private static void move(Display display, Vector anchor, float yaw) {
        Location loc = display.getLocation();
        double dx = anchor.getX() - loc.getX();
        double dy = anchor.getY() - loc.getY();
        double dz = anchor.getZ() - loc.getZ();
        boolean turned = Math.abs(wrapDegrees(loc.getYaw() - yaw)) > 0.05f;
        if (!turned && Math.abs(dx) < 1e-4 && Math.abs(dy) < 1e-4 && Math.abs(dz) < 1e-4) {
            return;
        }
        display.teleport(anchorLocation(display.getWorld(), anchor, yaw));
    }

    /** Normalizuje różnicę kątów do zakresu −180..180 (żeby nie kręcić w kółko). */
    private static float wrapDegrees(float degrees) {
        float d = degrees % 360f;
        if (d >= 180f) d -= 360f;
        if (d < -180f) d += 360f;
        return d;
    }

    /** Transformacja płótna: cienka płyta za masztem, szerokość zależna od stanu. */
    private static Transformation clothTransform(boolean open) {
        float width = open ? CLOTH_OPEN_WIDTH : CLOTH_ROLLED_WIDTH;
        return new Transformation(
                new Vector3f(0f, CLOTH_CENTER_Y, CLOTH_OFFSET_Z),
                new Quaternionf(),
                new Vector3f(width, CLOTH_HEIGHT, CLOTH_THICKNESS),
                new Quaternionf());
    }

    private static boolean alive(Entity entity, World world) {
        return entity != null && !entity.isDead() && entity.isValid()
                && world.equals(entity.getWorld());
    }

    // ─────────────────────── WIDOCZNOŚĆ PER GRACZ ───────────────────────────

    /**
     * Fallback widzą WYŁĄCZNIE gracze bez moda. Dla każdego gracza w świecie
     * w zasięgu pokazujemy go (showEntity) albo chowamy (hideEntity) — zależnie
     * od {@link #modded(Player, KeyStateReceiver)} (raft:key_state +
     * getListeningPluginChannels). Stan pamiętamy, żeby nie wołać API co tick;
     * wpisy graczy, którzy wyszli z zasięgu, czyścimy, bo po powrocie ich klient
     * może dostać encję od nowa (re-track) i widoczność trzeba odtworzyć.
     */
    private void updateVisibility(RaftPlugin plugin, World world, Vector anchor) {
        KeyStateReceiver keys = plugin.getHookManager() != null
                ? plugin.getHookManager().getKeyStateReceiver() : null;
        // Zegar świata, nie lokalny licznik sync — w trybie FAR sync chodzi co 4 ticki,
        // a bezpiecznik ma być raz na sekundę realnego czasu.
        boolean hideTick = world.getFullTime() % HIDE_REAPPLY_INTERVAL == 0;
        Set<UUID> inRange = new HashSet<>();

        for (Player player : world.getPlayers()) {
            UUID playerId = player.getUniqueId();
            Location loc = player.getLocation();
            double dx = loc.getX() - anchor.getX();
            double dz = loc.getZ() - anchor.getZ();
            if (dx * dx + dz * dz > VIEW_RANGE_SQ) continue;
            inRange.add(playerId);

            boolean show = !modded(player, keys);
            // Bezpiecznik: u gracza z modem co sekundę wymuszamy ukrycie (obok mesha
            // nie może zostać serwerowy fallback, nawet po re-tracku encji).
            boolean forceHide = hideTick && !show;
            apply(player, plugin, mast, playerId, show, mastShown, forceHide);
            apply(player, plugin, cloth, playerId, show, clothShown, forceHide);
        }

        // Poza zasięgiem/światem — zapomnij stan (po powrocie odtworzymy go od zera).
        mastShown.keySet().retainAll(inRange);
        clothShown.keySet().retainAll(inRange);
        channelMod.keySet().retainAll(inRange);
        channelCheckedTick.keySet().retainAll(inRange);
    }

    /**
     * Czy gracz ma moda raft-hud. Najpierw pewniak — {@code raft:key_state}
     * (pakiet od moda); dopiero potem (i nie częściej niż raz na
     * {@link #CHANNEL_CHECK_INTERVAL} ticków) kanały klienta, które mod
     * rejestruje już przy wejściu. Wynik pozytywny pamiętamy.
     */
    private boolean modded(Player player, KeyStateReceiver keys) {
        UUID playerId = player.getUniqueId();
        if (keys != null && keys.has(playerId)) return true;

        if (Boolean.TRUE.equals(channelMod.get(playerId))) return true;
        Integer checked = channelCheckedTick.get(playerId);
        if (checked != null && ticks - checked < CHANNEL_CHECK_INTERVAL) return false;
        channelCheckedTick.put(playerId, ticks);

        if (listensOnModChannel(player)) {
            channelMod.put(playerId, Boolean.TRUE);
            return true;
        }
        return false;
    }

    /** Czy klient gracza nasłuchuje któregoś z kanałów moda (getListeningPluginChannels). */
    private static boolean listensOnModChannel(Player player) {
        try {
            Set<String> channels = player.getListeningPluginChannels();
            if (channels == null || channels.isEmpty()) return false;
            for (String channel : MOD_CHANNELS) {
                if (channels.contains(channel)) return true;
            }
        } catch (RuntimeException ignored) {
            // API kanałów niedostępne — zostaje wykrywanie po raft:key_state.
        }
        return false;
    }

    /**
     * Pokazuje/chowa encję u gracza, gdy stan faktycznie się zmienił (albo gdy
     * {@code force} wymusza ponowne ukrycie). Zdarzenia Papera lecą tylko przy
     * realnej zmianie widoczności, więc powtórne hideEntity jest darmowe.
     */
    private void apply(Player player, RaftPlugin plugin, Entity entity, UUID playerId,
                       boolean show, Map<UUID, Boolean> state, boolean force) {
        if (entity == null || entity.isDead() || !entity.isValid()
                || !entity.getWorld().equals(player.getWorld())) {
            return;
        }
        Boolean current = state.get(playerId);
        if (!force && current != null && current.booleanValue() == show) return;
        try {
            if (show) {
                player.showEntity(plugin.getParent(), entity);
            } else {
                player.hideEntity(plugin.getParent(), entity);
            }
            state.put(playerId, show);
        } catch (RuntimeException ignored) {
            // Gracz w trakcie przejścia między światami / wylogowania — pomiń.
        }
    }

    // ─────────────────────── SPRZĄTANIE ─────────────────────────────────────

    /**
     * Wygasza fallback (tryb FROZEN poza zasięgiem wyświetlania albo brak żagla).
     * Wróci automatycznie przy najbliższym sync, jak deski tratwy.
     */
    public void cull() {
        remove();
    }

    /** Sprzątanie przy rozbiórce tratwy — to samo co {@link #cull()}. */
    public void cleanup() {
        remove();
    }

    private void remove() {
        if (mast != null && !mast.isDead()) mast.remove();
        if (cloth != null && !cloth.isDead()) cloth.remove();
        mast = null;
        cloth = null;
        clothOpen = null;
        mastShown.clear();
        clothShown.clear();
    }
}
