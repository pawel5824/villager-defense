package pl.vd.raft.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Item;
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
 * Sieć zbierająca (CellType.NET) — komórka tratwy, która łapie pływające
 * przedmioty, gdy tratwa na nie „najedzie".
 *
 * Łapanie:
 *  - Co 2 ticki sprawdzamy Item w promieniu ~1.2 bloka od każdej sieci (na
 *    wysokości komórki — powierzchnia wody). Łapie WSZYSTKO, co pływa.
 *  - Sieć trzyma maks. RaftEntity.NET_CAPACITY (10) sztuk łącznie; te same
 *    typy łączą się w jedną pozycję. Pełna sieć nie łapie (przedmioty płyną dalej).
 *  - Zawartość jest zapisywana razem z tratwą (raft-saves.yml) — przetrwa restart.
 *
 * Odbiór:
 *  - Prawy klik w sieć (OBOJĘTNIE czym w ręce) otwiera GUI 9×1 z zawartością.
 *    Klik w przedmiot zabiera cały stack do ekwipunku (nadmiar wraca do sieci).
 *  - Shift + prawy klik w sieć zabiera WSZYSTKO do ekwipunku bez otwierania GUI.
 *  - Pojemność sieci zostaje 10 sztuk (NET_CAPACITY); GUI ma 9 slotów, ale >9
 *    różnych przedmiotów w jednej sieci się nie zdarza (na razie).
 *  - WYJĄTEK: Młot budowlany w ręce — klik w sieć nic nie robi (na razie
 *    blokada tylko dla młota), żeby przypadkowy PPM przy budowie nie otwierał
 *    GUI sieci. Młot zachowuje swoje akcje (shift+PPM = GUI młota, PPM z duchem
 *    = potwierdzenie budowy).
 */
public class CollectionNetListener implements Listener {

    /** Tytuł GUI sieci. */
    public static final String GUI_TITLE = "§8[ §bSieć zbierająca §8]";

    private static final int GUI_SIZE = 9; // 9×1

    /** Promień łapania w XZ (bloki od środka komórki). */
    private static final double CATCH_RADIUS_XZ = 1.15;
    /** Promień łapania w pionie (komórka stoi na powierzchni wody). */
    private static final double CATCH_RADIUS_Y = 1.5;

    /** Kontekst otwartego GUI: do której sieci należy. */
    private static final class GuiCtx {
        final UUID raftId;
        final String cellKey;

        GuiCtx(UUID raftId, String cellKey) {
            this.raftId = raftId;
            this.cellKey = cellKey;
        }
    }

    private final RaftPlugin plugin;
    /** Gracz -> kontekst otwartego GUI. */
    private final Map<UUID, GuiCtx> openGuis = new HashMap<>();

    public CollectionNetListener(RaftPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin.getParent(), this::tick, 10L, 2L);
    }

    // ─────────────────────── ŁAPANIE (co 2 ticki) ─────────────────

    private void tick() {
        for (RaftEntity raft : RaftManager.getInstance().getRafts()) {
            World world = raft.getState().getWorld();
            if (world == null) continue;
            Vector center = raft.getCenter().toVector();

            for (Vector offset : raft.getBlockOffsets()) {
                if (!raft.isNetCell(offset)) continue;
                if (raft.getNetCount(offset) >= RaftEntity.NET_CAPACITY) continue; // pełna

                Location loc = new Location(world,
                        center.getX() + offset.getX(),
                        center.getY() + offset.getY(),
                        center.getZ() + offset.getZ());

                for (org.bukkit.entity.Entity e : world.getNearbyEntities(
                        loc, CATCH_RADIUS_XZ, CATCH_RADIUS_Y, CATCH_RADIUS_XZ)) {
                    if (!(e instanceof Item item)) continue;
                    if (item.isDead() || !item.isValid()) continue;

                    ItemStack stack = item.getItemStack();
                    if (stack == null || stack.getType() == Material.AIR) continue;

                    // Przedmiot musi faktycznie dotykać sieci (nie tylko rogu boxu)
                    if (item.getLocation().distance(loc) > CATCH_RADIUS_XZ) continue;

                    int space = RaftEntity.NET_CAPACITY - raft.getNetCount(offset);
                    if (space <= 0) break; // sieć wypełniła się w trakcie tej rundy

                    int take = Math.min(space, stack.getAmount());
                    if (take <= 0) continue;

                    ItemStack toStore = stack.clone();
                    toStore.setAmount(take);
                    int leftover = raft.storeInNet(offset, toStore); // powinno być 0 (take ≤ space)

                    if (take - leftover <= 0) continue;
                    world.playSound(loc, Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.4f);

                    // Usuń złapane ze świata; reszta stacku zostaje na wodzie
                    if (stack.getAmount() > take) {
                        stack.setAmount(stack.getAmount() - take);
                        item.setItemStack(stack);
                    } else {
                        item.remove();
                    }
                }
            }
        }
    }

    // ─────────────── OBSŁUGA KLIKNIĘCIA W SIEĆ (PPM / shift+PPM) ──

    /**
     * Prawy klik w komórkę sieci — bez shifta otwiera GUI 9×1, ze shiftem
     * zabiera całą zawartość do ekwipunku (bez GUI). Działa z dowolnym
     * przedmiotem w ręce POZA Młotem budowlanym i przedmiotami budowlanymi
     * (podłoga/sieć/skrzynka — wtedy działa tryb budowania; patrz
     * holdsHammer/holdsBuildItem). Anulujemy klik, żeby inne akcje PPM
     * (podgląd-duch, przedmioty, hak) nie odpalały się równocześnie z obsługą
     * sieci — pierwszeństwo ma budowanie (sprawdzone wyżej).
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRightClickNet(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        if (holdsHammer(player)) return; // młot: klik w sieć ma nic nie robić (na razie)
        if (holdsBuildItem(player)) return; // przedmiot budowlany = tryb budowania

        RaftEntity raft = RaftManager.getInstance().getNearestRaft(player, 6.0);
        if (raft == null) return;
        RaftBuildListener build = plugin.getRaftBuildListener();
        if (build == null) return;

        Vector hit = build.raycastCell(raft,
                player.getEyeLocation().toVector(), player.getEyeLocation().getDirection());
        if (hit == null || !raft.isNetCell(hit)) return;

        event.setCancelled(true); // klik „zjedzony" przez sieć
        if (player.isSneaking()) {
            takeAll(player, raft, hit); // shift+PPM — bez otwierania GUI
        } else {
            openNet(player, raft, hit);
        }
    }

    /**
     * Zapasowa ścieżka: klik bezpośrednio w encję sieci (wizual BlockDisplay lub
     * niewidzialna kolizja) — gdy celownik trafił „w encję" zamiast obok niej.
     * RaftListener i tak anuluje interakcję z encjami tratwy, więc tylko
     * obsługujemy sieć (GUI / shift+PPM = zabierz wszystko).
     */
    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (holdsHammer(player)) return; // młot: klik w sieć ma nic nie robić (na razie)
        if (holdsBuildItem(player)) return; // przedmiot budowlany = tryb budowania

        org.bukkit.entity.Entity e = event.getRightClicked();
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
            if (!raft.isNetCell(off)) continue;
            double y = center.getY() + off.getY() - (collider ? 0.5 : 0.0);
            if (Math.abs(el.getX() - (center.getX() + off.getX())) < 0.6
                    && Math.abs(el.getZ() - (center.getZ() + off.getZ())) < 0.6
                    && Math.abs(el.getY() - y) < 0.75) {
                event.setCancelled(true);
                if (player.isSneaking()) {
                    takeAll(player, raft, off); // shift+PPM — bez GUI
                } else {
                    openNet(player, raft, off);
                }
                return;
            }
        }
    }

    /** Otwiera GUI sieci dla danej komórki. */
    private void openNet(Player player, RaftEntity raft, Vector offset) {
        String cellKey = RaftEntity.keyOf(offset);
        openGuis.put(player.getUniqueId(), new GuiCtx(raft.getRaftId(), cellKey));
        openGui(player, raft, cellKey);
    }

    /** Otwiera (lub odświeża) GUI z zawartością sieci. */
    private void openGui(Player player, RaftEntity raft, String cellKey) {
        Inventory inv = Bukkit.createInventory(null, GUI_SIZE, GUI_TITLE);
        refresh(inv, raft, cellKey);
        player.openInventory(inv);
    }

    /** Wypełnia GUI aktualną zawartością sieci. */
    private void refresh(Inventory inv, RaftEntity raft, String cellKey) {
        inv.clear();
        List<ItemStack> contents = raft.getNetContents(offsetOf(raft, cellKey));
        for (int i = 0; i < contents.size() && i < GUI_SIZE; i++) {
            inv.setItem(i, contents.get(i));
        }
    }

    // ───────────────── ZABRANIE WSZYSTKIEGO (shift+PPM, bez GUI) ──

    /**
     * Czy gracz trzyma w ręce przedmiot budowlany (podłoga/sieć/skrzynka) —
     * wtedy klik w sieć obsługuje tryb budowania (stawianie komórki), a nie
     * GUI sieci (spójnie ze skrzynkami).
     */
    private boolean holdsBuildItem(Player player) {
        ItemManager im = plugin.getItemManager();
        RaftBuildListener build = plugin.getRaftBuildListener();
        return im != null && build != null
                && build.buildItemId(im, player.getInventory().getItemInMainHand()) != null;
    }

    /** Czy gracz trzyma w ręce Młot budowlany (jego kliki w sieć są blokowane). */
    private boolean holdsHammer(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        ItemManager im = plugin.getItemManager();
        return im != null && im.isConstructionHammer(item);
    }

    /** Zabiera CAŁĄ zawartość sieci do ekwipunku gracza (shift+PPM). Nadmiar,
     * który się nie zmieścił, wraca do sieci (po opróżnieniu zawsze ma miejsce;
     * drop to tylko awaryjny backstop).
     */
    private void takeAll(Player player, RaftEntity raft, Vector offset) {
        List<ItemStack> entries = new ArrayList<>();
        ItemStack entry;
        while ((entry = raft.takeFromNet(offset, 0)) != null) {
            entries.add(entry);
        }
        if (entries.isEmpty()) {
            // Bez komunikatu na czacie (AGENTS.md pkt 1) — celowo.
            return;
        }

        int moved = 0;
        for (ItemStack s : entries) {
            Map<Integer, ItemStack> left = player.getInventory().addItem(s);
            int leftAmount = 0;
            for (ItemStack rest : left.values()) {
                leftAmount += rest.getAmount();
                int remain = raft.storeInNet(offset, rest);
                if (remain > 0) {
                    rest.setAmount(remain);
                    player.getWorld().dropItemNaturally(player.getLocation(), rest);
                }
            }
            moved += s.getAmount() - leftAmount;
        }
        if (moved > 0) {
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.2f);
        }
    }

    // ─────────────────────── KLIKNIĘCIA W GUI ─────────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;
        if (event.getClickedInventory() == null) return;

        // Nie pozwól niczego wkładać do sieci
        event.setCancelled(true);

        GuiCtx ctx = openGuis.get(player.getUniqueId());
        if (ctx == null) return;
        RaftEntity raft = findRaft(ctx.raftId);
        if (raft == null) return;
        Vector offset = offsetOf(raft, ctx.cellKey);
        if (offset == null || !raft.isNetCell(offset)) return;

        // Klik w przedmiot (górny inventory) = zabierz cały stack
        if (event.getClickedInventory().equals(player.getOpenInventory().getTopInventory())) {
            int slot = event.getSlot();
            List<ItemStack> contents = raft.getNetContents(offset);
            if (slot < 0 || slot >= contents.size()) return;

            ItemStack taken = raft.takeFromNet(offset, slot);
            if (taken == null) return;

            Map<Integer, ItemStack> left = player.getInventory().addItem(taken);
            if (!left.isEmpty()) {
                // Ekwipunek pełny — nadmiar wraca do sieci (albo wypada awaryjnie)
                for (ItemStack rest : left.values()) {
                    int restAmount = raft.storeInNet(offset, rest);
                    if (restAmount > 0) {
                        rest.setAmount(restAmount);
                        player.getWorld().dropItemNaturally(player.getLocation(), rest);
                    }
                }
            }
            refresh(player.getOpenInventory().getTopInventory(), raft, ctx.cellKey);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            openGuis.remove(player.getUniqueId());
        }
    }

    // ─────────────────────── POMOCNICZE ───────────────────────────

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
