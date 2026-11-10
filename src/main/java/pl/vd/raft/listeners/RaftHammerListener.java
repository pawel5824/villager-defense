package pl.vd.raft.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pl.vd.raft.RaftPlugin;
import pl.vd.raft.entity.RaftEntity;
import pl.vd.raft.items.ItemManager;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Młot budowlany (construction_hammer) — SHIFT+prawy przycisk myszy (w
 * powietrzu LUB w blok) otwiera GUI młota. Zwykły PPM bez shifta nic nie robi
 * (z aktywnym duchem budowy potwierdza stawianie — obsługuje RaftBuildPreview).
 * Nawigacja:
 *
 *   Pasek 9×1 (kategorie):
 *     slot 0 — „Tratwy"   (dębowa płyta)
 *     slot 1 — „Inne"     (tarcza strzelecka)
 *     sloty 2–8 — szkło (kolejne kategorie)
 *
 *   Zawartość kategorii: „Tratwy" = podłogi + sieć + skrzynie, „Inne" =
 *   elementy obrotowe i podpory (drewniane schody — kierunek kółkiem/presetami,
 *   podpora piętra — patrz RaftBuildListener, docs/RAFT_BALANCE.md §1a i §1b).
 *
 *   Kliknięcie kategorii otwiera jej stronę 9×4 (36 slotów). Kliknięcie
 *   PRZEDMIOTU na stronie ZAMYKA GUI i uruchamia podgląd-ducha stawiania
 *   (RaftBuildPreview): świecący blok w miejscu, gdzie stanie komórka — prawy
 *   klik młotem zatwierdza. Zmiana przedmiotu / ponowne shift+PPM anuluje.
 */
public class RaftHammerListener implements Listener {

    /** Tytuł głównego paska GUI młota. */
    public static final String GUI_TITLE = "§8[ §6Młot budowlany §8]";

    /** Rozmiar paska kategorii (9×1) i stron kategorii (9×4). */
    private static final int BAR_SIZE = 9;
    private static final int PAGE_SIZE = 36; // 9×4
    /** Slot przycisku „Powrót" na stronie kategorii (prawy dolny róg). */
    private static final int SLOT_BACK = 35;

    /** Kategorie budowania w młocie. */
    private enum HammerCategory {
        TRATWY(0, "Tratwy", Material.OAK_SLAB),
        INNE(1, "Inne", Material.TARGET);

        final int slot;
        final String label;
        final Material icon;

        HammerCategory(int slot, String label, Material icon) {
            this.slot = slot;
            this.label = label;
            this.icon = icon;
        }

        String pageTitle() {
            return "§8[ §6Młot — " + label + " §8]";
        }

        static HammerCategory bySlot(int slot) {
            for (HammerCategory c : values()) {
                if (c.slot == slot) return c;
            }
            return null;
        }

        static HammerCategory byTitle(String title) {
            for (HammerCategory c : values()) {
                if (c.pageTitle().equals(title)) return c;
            }
            return null;
        }
    }

    /**
     * Przedmioty na stronach kategorii (9×4): slot -> id przedmiotu z ItemManager.
     * Klik w przedmiot = wybór do budowy (zamyka GUI, startuje podgląd-duch).
     */
    private final Map<HammerCategory, Map<Integer, String>> pageItems =
            new EnumMap<>(HammerCategory.class);

    private final RaftPlugin plugin;

    public RaftHammerListener(RaftPlugin plugin) {
        this.plugin = plugin;
        initPageItems();
    }

    /** Rozmieszczenie przedmiotów po stronach kategorii (środkowy rząd: sloty 9–17). */
    private void initPageItems() {
        Map<Integer, String> tratwy = new HashMap<>();
        tratwy.put(10, "wooden_foundation"); // Drewniana Podłoga
        tratwy.put(11, "stone_foundation");  // Kamienna Podłoga
        tratwy.put(12, "collection_net");    // Sieć zbierająca
        tratwy.put(13, "chest_small");       // Mała skrzynka (18 slotów)
        tratwy.put(14, "chest_medium");      // Skrzynia (36 slotów)
        tratwy.put(15, "chest_large");       // Duża skrzynia (54 sloty)
        pageItems.put(HammerCategory.TRATWY, tratwy);

        // INNE — elementy budowlane niebędące pokładem: schody (obrotowe
        // kółkiem/presetami 1–4, tak jak skrzynie), podpora piętra i piętrowa
        // Podłoga (stawiana WYŁĄCZNIE na piętrze — na wodzie się nie pokazuje).
        Map<Integer, String> inne = new HashMap<>();
        inne.put(10, "wooden_stairs");  // Drewniane schody
        inne.put(11, "wooden_support"); // Podpora piętra (narożnik 4 komórek)
        inne.put(12, "wooden_floor");   // Podłoga (tylko piętro)
        pageItems.put(HammerCategory.INNE, inne);
    }

    // ─────────────────────── OTWIERANIE GUI ───────────────────────

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        // UWAGA (klasyk Bukkit): dla kliknięć W POWIETRZE wanilia domyślnie
        // ustawia useInteractedBlock = DENY, a isCancelled() zwraca true, gdy
        // KTÓRYKOLWIEK z dwóch wyników jest DENY — czyli isCancelled() jest
        // zawsze true dla powietrza i „zjada" klik. Sprawdzamy wyniki rozdzielnie:
        //   blok      — czy inny listener zjadł kliknięcie w blok,
        //   powietrze — czy inny listener zjadł użycie przedmiotu.
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (event.useInteractedBlock() == org.bukkit.event.Event.Result.DENY) return;
        } else if (event.useItemInHand() == org.bukkit.event.Event.Result.DENY) {
            return;
        }
        Player player = event.getPlayer();

        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) return;

        ItemManager im = plugin.getItemManager();
        if (im == null) return;
        if (!im.isConstructionHammer(item)) return;

        RaftBuildPreview preview = plugin.getRaftBuildPreview();
        // GUI otwiera WYŁĄCZNIE shift+PPM. Bez shifta: z aktywnym duchem budowy
        // PPM potwierdza stawianie (obsługuje RaftBuildPreview), bez ducha — nic.
        if (!player.isSneaking()) {
            return;
        }

        event.setCancelled(true);

        // Otwarcie GUI anuluje aktywny podgląd-duch (gracz wybierze blok od nowa)
        if (preview != null) preview.cancel(player);

        openBar(player);
    }

    /**
     * Zapasowa ścieżka: shift+PPM w ENCJĘ tratwy (kolizję-shulker albo deskę).
     * Z bliska klient wysyła interakcję z encją, a nie PPM w powietrze — bez
     * tego GUI młota nie otwierało się, gdy gracz celował w pokład.
     */
    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        if (!player.isSneaking()) return; // GUI otwiera WYŁĄCZNIE shift+PPM
        if (!RaftEntity.isRaftEntity(event.getRightClicked())) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        ItemManager im = plugin.getItemManager();
        if (im == null || !im.isConstructionHammer(item)) return;

        event.setCancelled(true);

        // Otwarcie GUI anuluje aktywny podgląd-duch (gracz wybierze blok od nowa)
        RaftBuildPreview preview = plugin.getRaftBuildPreview();
        if (preview != null) preview.cancel(player);

        openBar(player);
    }

    /** Otwiera pasek kategorii (9×1). */
    public void openBar(Player player) {
        Inventory inventory = Bukkit.createInventory(null, BAR_SIZE, GUI_TITLE);

        for (HammerCategory c : HammerCategory.values()) {
            inventory.setItem(c.slot, categoryItem(c));
        }
        // Pozostałe sloty — dekoracyjne szkło (kolejne kategorie trafią tu później)
        for (int i = 3; i < BAR_SIZE; i++) {
            inventory.setItem(i, glassPane());
        }

        player.openInventory(inventory);
    }

    /** Otwiera stronę kategorii (9×4): tło + przedmioty kategorii + „Powrót". */
    public void openCategoryPage(Player player, HammerCategory category) {
        Inventory inventory = Bukkit.createInventory(null, PAGE_SIZE, category.pageTitle());

        // Dekoracyjne tło
        for (int i = 0; i < PAGE_SIZE; i++) {
            inventory.setItem(i, glassPane());
        }
        inventory.setItem(SLOT_BACK, backItem());

        // Przedmioty kategorii — ikony z ItemManager (klik = wybór do budowy)
        ItemManager im = plugin.getItemManager();
        if (im != null) {
            for (Map.Entry<Integer, String> entry : pageItems.getOrDefault(category, Map.of()).entrySet()) {
                inventory.setItem(entry.getKey(), pageItemIcon(im, entry.getValue()));
            }
        }

        player.openInventory(inventory);
    }

    // ─────────────────────── ITEMY GUI ────────────────────────────

    /** Ikona kategorii w pasku 9×1. */
    private ItemStack categoryItem(HammerCategory category) {
        ItemStack item = new ItemStack(category.icon);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6" + category.label);
        List<String> lore = new ArrayList<>();
        lore.add("§7Kliknij aby otworzyć kategorię");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /** Przycisk powrotu do paska kategorii. */
    private ItemStack backItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§c← Powrót");
        List<String> lore = new ArrayList<>();
        lore.add("§7Wróć do paska kategorii młota");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /** Ikona przedmiotu na stronie kategorii (custom item + podpowiedź wyboru). */
    private ItemStack pageItemIcon(ItemManager im, String itemId) {
        ItemStack item = im.createItem(itemId);
        if (item == null) return glassPane();
        ItemMeta meta = item.getItemMeta();
        List<String> lore = meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add("");
        lore.add("§7Kliknij aby wybrać do budowy");
        lore.add("§8PPM młotem potwierdza postawienie");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /** Dekoracyjna szybka (jak w innych GUI pluginu). */
    private ItemStack glassPane() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(" ");
        item.setItemMeta(meta);
        return item;
    }

    // ─────────────────────── OBSŁUGA KLIKNIĘĆ ─────────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();

        // Pasek kategorii (9×1): klik w kategorię otwiera jej stronę 9×4
        if (title.equals(GUI_TITLE)) {
            event.setCancelled(true);
            HammerCategory category = HammerCategory.bySlot(event.getRawSlot());
            if (category != null) {
                openCategoryPage(player, category);
            }
            return;
        }

        // Strona kategorii (9×4)
        HammerCategory category = HammerCategory.byTitle(title);
        if (category != null) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot == SLOT_BACK) {
                openBar(player);
                return;
            }
            String itemId = pageItems.getOrDefault(category, Map.of()).get(slot);
            if (itemId == null) return;

            // Wybór bloku: zamknij GUI i pokaż podgląd-ducha stawiania
            RaftBuildPreview preview = plugin.getRaftBuildPreview();
            player.closeInventory();
            if (preview != null) {
                preview.select(player, itemId);
            }
        }
    }
}