package pl.vd.raft.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import pl.vd.raft.RaftPlugin;
import pl.vd.raft.entity.RaftEntity;
import pl.vd.raft.entity.RaftManager;
import pl.vd.raft.items.ItemManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Skrzynki na tratwie (CellType.CHEST_18 / CHEST_36 / CHEST_54) — komórki
 * stawiane młotem budowlanym, z własnym GUI o zadanej pojemności:
 *
 *  - Mała skrzynka (chest_small, TRAPPED_CHEST)  — 18 slotów (GUI 9×2),
 *  - Skrzynia     (chest_medium, CHEST)          — 36 slotów (GUI 9×4),
 *  - Duża skrzynia (chest_large, WAXED_COPPER_CHEST) — 54 sloty (GUI 9×6).
 *
 * Otwieranie:
 *  - PPM w skrzynkę (raycast po komórkach, jak sieć zbierająca) otwiera GUI.
 *  - Młot budowlany w ręce ma pierwszeństwo (shift+PPM = GUI młota, PPM z
 *    aktywnym duchem budowy = potwierdzenie stawiania) — klik młotem NIE
 *    otwiera skrzynki.
 *  - Poza zasięgiem (4 bloki) GUI się nie otworzy.
 *
 * Zawartość:
 *  - Klikanie w GUI jest W PEŁNI WANILIOWE: klik bierze stack na kursor, klik
 *    w slot go odkłada, działają drag, swap i shift-klik (nic nie przenosi się
 *    „samo" — wcześniej każde kliknięcie było anulowane i robiło własny transfer
 *    od razu do ekwipunku/skrzynki, więc przy pustym kursorze przedmiot wpadał
 *    do ekwipunku jak przy shift-kliku).
 *  - Stan GUI to źródło prawdy sesji: lustro co 2 ticki przenosi go do skrzynki
 *    (i odświeża inne otwarte GUI tej samej skrzynki), a zamknięcie GUI robi
 *    ostatni zapis. Gdy skrzynka zniknie w trakcie, GUI zamyka się samo.
 *  - Jedna skrzynka = JEDEN współdzielony kontener dla wszystkich graczy, którzy
 *    ją otworzą (jak waniliowa skrzynia). Każdy gracz z własną kopią mógłby
 *    wyjąć ten sam przedmiot w tej samej chwili (duplikacja), zanim lustro
 *    zdążyłoby zapisać zmianę.
 *  - Zawartość jest zapisywana razem z tratwą (raft-saves.yml) i wypada do
 *    wody przy rozbiorce komórki (RaftEntity.removeBlock / destroy).
 */
public class RaftChestListener implements Listener {

    /** Zasięg otwierania skrzynki (bloki od gracza). */
    private static final double OPEN_RANGE = 4.0;

    /** Kontekst otwartego GUI: której skrzynki dotyczy. */
    private static final class GuiCtx {
        final UUID raftId;
        final String cellKey;

        GuiCtx(UUID raftId, String cellKey) {
            this.raftId = raftId;
            this.cellKey = cellKey;
        }
    }

    private final RaftPlugin plugin;
    /** Gracz -> kontekst otwartego GUI skrzynki. */
    private final Map<UUID, GuiCtx> openGuis = new HashMap<>();
    /**
     * Współdzielone GUI: "raftId|kluczKomórki" -> Inventory. Jedna skrzynka =
     * jeden kontener, więc kliknięcia różnych graczy trafiają w tę samą tablicę
     * slotów (wanilia rozsyła zmiany każdemu widzowi sama).
     */
    private final Map<String, Inventory> sharedGuis = new HashMap<>();

    public RaftChestListener(RaftPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Startuje lustro GUI → skrzynka (co 2 ticki). Bez tego zapis działałby
     * dopiero przy zamknięciu GUI, a przy zamknięciu okna gry zmiany z ostatnich
     * milisekund mogłyby przepaść.
     */
    public void start() {
        plugin.getParent().getServer().getScheduler().runTaskTimer(
                plugin.getParent(), this::mirrorTick, 2L, 2L);
    }

    // ─────────────────────── TYTUŁY / ROZMIARY ────────────────────

    /** Tytuł GUI dla danego typu skrzynki. */
    public static String titleFor(RaftEntity.CellType type) {
        return switch (type) {
            case CHEST_18 -> "§8[ §6Skrzynka (18) §8]";
            case CHEST_36 -> "§8[ §6Skrzynia (36) §8]";
            case CHEST_54 -> "§8[ §6Duża skrzynia (54) §8]";
            default -> "§8[ §6Skrzynia §8]";
        };
    }

    /** Rozmiar GUI = pojemność skrzynki (9×2 / 9×4 / 9×6). */
    private static int guiSizeFor(RaftEntity.CellType type) {
        return type.chestSlots();
    }

    // ─────────────────────── OTWIERANIE (PPM) ─────────────────────

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRightClickChest(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();

        // Młot budowlany ma pierwszeństwo: shift+PPM = GUI młota, PPM z duchem
        // budowy = potwierdzenie stawiania. Klik młotem nie otwiera skrzynki.
        ItemManager im = plugin.getItemManager();
        if (im != null && im.isConstructionHammer(player.getInventory().getItemInMainHand())) {
            return;
        }
        // Przedmiot budowlany w ręce (podłogi, sieci, skrzynki) = tryb budowania:
        // klik stawia element obok, nie otwiera skrzynki.
        RaftBuildListener buildEarly = plugin.getRaftBuildListener();
        if (im != null && buildEarly != null
                && buildEarly.buildItemId(im, player.getInventory().getItemInMainHand()) != null) {
            return;
        }

        RaftEntity raft = RaftManager.getInstance().getNearestRaft(player, OPEN_RANGE);
        if (raft == null) return;
        RaftBuildListener build = plugin.getRaftBuildListener();
        if (build == null) return;

        Vector hit = build.raycastCell(raft,
                player.getEyeLocation().toVector(), player.getEyeLocation().getDirection());
        if (hit == null) return;
        RaftEntity.CellType type = raft.getCellType(hit);
        if (type == null || !type.isChest()) return;

        event.setCancelled(true);
        openChest(player, raft, hit);
    }

    /**
     * Zapasowa ścieżka: PPM bezpośrednio w encję komórki-skrzynki (niewidzialną
     * kolizję-shulker albo wizual). Skrzynia stoi NA pokładzie, więc z bliska
     * klient wysyła interakcję z encją, a nie PPM w powietrze — bez tej ścieżki
     * kliknięcie skrzyni nie otwierało GUI (wzór: sieć zbierająca).
     */
    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();

        ItemManager im = plugin.getItemManager();
        // Młot ma pierwszeństwo: shift+PPM = GUI młota, PPM z duchem = budowa.
        if (im != null && im.isConstructionHammer(player.getInventory().getItemInMainHand())) {
            return;
        }
        // Przedmiot budowlany w ręce = tryb budowania (klik stawia, nie otwiera).
        RaftBuildListener build = plugin.getRaftBuildListener();
        if (im != null && build != null
                && build.buildItemId(im, player.getInventory().getItemInMainHand()) != null) {
            return;
        }

        Entity e = event.getRightClicked();
        if (!RaftEntity.isRaftEntity(e)) return;
        UUID raftId = RaftEntity.raftIdOf(e);
        if (raftId == null) return;
        RaftEntity raft = findRaft(raftId);
        if (raft == null) return;

        // Kolizja (shulker) stoi 0.5 pod środkiem komórki, wizual na środku
        boolean collider = e.getScoreboardTags().contains(RaftEntity.TAG_COLLIDER);
        Vector center = raft.getCenter().toVector();
        Location el = e.getLocation();
        for (Vector off : raft.getBlockOffsets()) {
            RaftEntity.CellType type = raft.getCellType(off);
            if (type == null || !type.isChest()) continue;
            double y = center.getY() + off.getY() - (collider ? 0.5 : 0.0);
            if (Math.abs(el.getX() - (center.getX() + off.getX())) < 0.6
                    && Math.abs(el.getZ() - (center.getZ() + off.getZ())) < 0.6
                    && Math.abs(el.getY() - y) < 0.75) {
                event.setCancelled(true);
                openChest(player, raft, off);
                return;
            }
        }
    }

    /** Otwiera GUI skrzynki dla danej komórki. */
    private void openChest(Player player, RaftEntity raft, Vector offset) {
        RaftEntity.CellType type = raft.getCellType(offset);
        if (type == null || !type.isChest()) return;

        String cellKey = RaftEntity.keyOf(offset);
        openGuis.put(player.getUniqueId(), new GuiCtx(raft.getRaftId(), cellKey));

        String key = guiKey(raft.getRaftId(), cellKey);
        Inventory inv = sharedGuis.get(key);
        if (inv == null || inv.getSize() != guiSizeFor(type)) {
            // Pierwszy widz tej skrzyni (albo skrzynka zmieniła typ) — nowy
            // kontener wypełniony aktualną zawartością.
            inv = Bukkit.createInventory(null, guiSizeFor(type), titleFor(type));
            fill(inv, raft.getChestContents(offset));
            sharedGuis.put(key, inv);
        }
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.5f, 1.0f);
    }

    // ─────────────────────── KLIKNIĘCIA W GUI ─────────────────────

    /**
     * Kliknięcia w GUI skrzynki są W PEŁNI WANILIOWE — nic nie anulujemy i nie
     * przenosimy przedmiotów za gracza: klik bierze stack na kursor, klik w slot
     * go odkłada, działają drag, swap i shift-klik. Wcześniej każde kliknięcie
     * było anulowane i robiło własny transfer „od razu do ekwipunku/skrzynki",
     * więc przy pustym kursorze przedmiot wpadał do ekwipunku sam (jak shift-klik),
     * a przy zajętym kursorze zostawiał go bez zmian — stąd różnica, którą było
     * widać w grze.
     * Stan GUI przenosi do skrzynki lustro (co 2 ticki) i ostatni zapis przy
     * zamknięciu GUI. Tu tylko pilnujemy, że GUI nadal należy do żywej skrzyni;
     * gdy zniknęła — zamykamy okno, żeby nie wyprowadzić wirtualnej kopii.
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        GuiCtx ctx = openGuis.get(player.getUniqueId());
        if (ctx == null) return;
        if (resolve(ctx, event.getView().getTitle()) == null) {
            openGuis.remove(player.getUniqueId());
            player.closeInventory();
        }
    }

    /** Zapisuje stan otwartych GUI do skrzynek (lustro; co 2 ticki). */
    private void mirrorTick() {
        pruneShared();
        if (openGuis.isEmpty()) return;
        for (Map.Entry<UUID, GuiCtx> e : new ArrayList<>(openGuis.entrySet())) {
            Player player = Bukkit.getPlayer(e.getKey());
            if (player == null || !player.isOnline()) {
                openGuis.remove(e.getKey());
                continue;
            }
            Inventory top = player.getOpenInventory().getTopInventory();
            String title = player.getOpenInventory().getTitle();
            if (resolve(e.getValue(), title) == null) {
                // Skrzynka zniknęła (rozbiórka/zniszczenie tratwy) — zamknij okno,
                // żeby nie zostawić graczowi wirtualnej kopii zawartości.
                openGuis.remove(e.getKey());
                player.closeInventory();
                continue;
            }
            persist(player, e.getValue(), top, title);
        }
    }

    /**
     * Przenosi bieżący stan GUI do skrzynki (bez zmian = bez zapisu) i odświeża
     * GUI innych graczy otwarte na tej samej skrzynce, żeby widzieli te same
     * przedmioty (GUI to osobne kopie — bez tego drugi gracz mógłby zabrać
     * przedmiot, którego już nie ma).
     */
    private void persist(Player player, GuiCtx ctx, Inventory top, String title) {
        Resolved r = resolve(ctx, title);
        if (r == null) {
            openGuis.remove(player.getUniqueId());
            return;
        }
        List<ItemStack> snapshot = compactView(top);
        if (sameContents(snapshot, r.raft.getChestContents(r.offset))) return;
        List<ItemStack> overflow = r.raft.setChestContents(r.offset, snapshot);
        for (ItemStack rest : overflow) {
            player.getWorld().dropItemNaturally(player.getLocation(), rest);
        }
        refreshOthers(player.getUniqueId(), r);
    }

    /** Kontekst GUI rozłożony na żywą skrzynkę (null = GUI już nieaktualne). */
    private record Resolved(RaftEntity raft, Vector offset, RaftEntity.CellType type) {}

    private Resolved resolve(GuiCtx ctx, String title) {
        RaftEntity raft = findRaft(ctx.raftId);
        if (raft == null) return null;
        Vector offset = offsetOf(raft, ctx.cellKey);
        RaftEntity.CellType type = offset != null ? raft.getCellType(offset) : null;
        if (offset == null || type == null || !type.isChest()
                || !title.equals(titleFor(type))) {
            return null;
        }
        return new Resolved(raft, offset, type);
    }

    /** Niepuste sloty GUI w kolejności (wpisy skrzynki muszą być gęste). */
    private static List<ItemStack> compactView(Inventory inv) {
        List<ItemStack> list = new ArrayList<>();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s == null || s.getType().isAir() || s.getAmount() <= 0) continue;
            list.add(s.clone());
        }
        return list;
    }

    private static boolean sameContents(List<ItemStack> a, List<ItemStack> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            ItemStack x = a.get(i);
            ItemStack y = b.get(i);
            if (x == null || y == null) return false;
            if (x.getAmount() != y.getAmount() || !x.isSimilar(y)) return false;
        }
        return true;
    }

    /**
     * Odświeża GUI innych graczy otwarte na tej samej skrzynce. Gdy kontener jest
     * współdzielony (a jest — patrz {@link #sharedGuis}), wanilia rozsyła zmiany
     * sama i nic tu nie robimy; zapasowo działa dla osobnych kopii.
     */
    private void refreshOthers(UUID selfId, Resolved r) {
        String key = RaftEntity.keyOf(r.offset);
        Inventory shared = sharedGuis.get(guiKey(r.raft.getRaftId(), key));
        List<ItemStack> contents = r.raft.getChestContents(r.offset);
        for (Map.Entry<UUID, GuiCtx> e : new ArrayList<>(openGuis.entrySet())) {
            if (e.getKey().equals(selfId)) continue;
            GuiCtx other = e.getValue();
            if (!other.raftId.equals(r.raft.getRaftId()) || !other.cellKey.equals(key)) continue;
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null || !p.isOnline()) continue;
            Inventory top = p.getOpenInventory().getTopInventory();
            if (top.equals(shared)) continue; // wspólny kontener — wanilia sama wysyła zmiany
            if (!titleFor(r.type).equals(p.getOpenInventory().getTitle())) continue;
            fill(top, contents);
        }
    }

    /** Wypełnia GUI zawartością skrzynki (clear + wpisy; nadmiar wpisów pomijany). */
    private static void fill(Inventory inv, List<ItemStack> contents) {
        inv.clear();
        for (int i = 0; i < contents.size() && i < inv.getSize(); i++) {
            inv.setItem(i, contents.get(i));
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        GuiCtx ctx = openGuis.remove(player.getUniqueId());
        pruneShared();
        if (ctx == null) return;
        // Ostatni zapis: zamknięcie przychodzi po wszystkich kliknięciach, więc
        // stan GUI jest już ostateczny (przedmiot z kursora wanilia sama oddaje
        // graczowi — kursor nie jest częścią zawartości skrzyni).
        persist(player, ctx, event.getView().getTopInventory(), event.getView().getTitle());
    }

    // ─────────────────────── POMOCNICZE ───────────────────────────

    /** Klucz współdzielonego kontenera skrzynki (tratwa + komórka). */
    private static String guiKey(UUID raftId, String cellKey) {
        return raftId + "|" + cellKey;
    }

    /**
     * Usuwa z cache kontenery, których nikt już nie ogląda (zamknięte GUI,
     * rozebrana skrzynka, zniszczona tratwa). Widzowie z listy kontenera chronią
     * przed wyrzuceniem GUI, o którym nasz kontekst już nie wie (np. wyjście z gry).
     */
    private void pruneShared() {
        if (sharedGuis.isEmpty()) return;
        java.util.Iterator<Map.Entry<String, Inventory>> it = sharedGuis.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Inventory> e = it.next();
            if (isWatched(e.getKey())) continue;
            if (isBeingViewed(e.getValue())) continue;
            it.remove();
        }
    }

    /**
     * Czy ktoś ma jeszcze otwarty ten kontener. Przy jakimkolwiek problemie
     * uznajemy, że tak (lepiej zostawić wpis w cache niż oddać graczowi drugą
     * kopię tej samej skrzyni).
     */
    private static boolean isBeingViewed(Inventory inv) {
        try {
            return !inv.getViewers().isEmpty();
        } catch (Exception e) {
            return true;
        }
    }

    /** Czy istnieje otwarty kontekst GUI wskazujący na ten kontener. */
    private boolean isWatched(String key) {
        for (GuiCtx ctx : openGuis.values()) {
            if (guiKey(ctx.raftId, ctx.cellKey).equals(key)) return true;
        }
        return false;
    }

    private Vector offsetOf(RaftEntity raft, String cellKey) {
        for (Vector off : raft.getBlockOffsets()) {
            if (RaftEntity.keyOf(off).equals(cellKey)) return off;
        }
        return null;
    }

    private RaftEntity findRaft(UUID raftId) {
        for (RaftEntity raft : RaftManager.getInstance().getRafts()) {
            if (raft.getRaftId().equals(raftId)) return raft;
        }
        return null;
    }
}
