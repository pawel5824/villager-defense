package pl.vd.raft.settings;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import pl.vd.raft.RaftPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Podmenu DŹWIĘKÓW trybu Raft — GUI 6×9 (54 sloty), otwierane z panelu
 * ustawień (klik w „Dźwięki”). Wzór: globalny przełącznik na górze + kategorie
 * dźwięków w jednym rzędzie + nether star „Wyjdź” (kod 49, w grze 50).
 *
 *   5  → 4   Wszystkie dźwięki (master on/off, klucz sounds-enabled)
 *   20 → 19  Hak           (sounds-hook)
 *   21 → 20  Sieć          (sounds-net)
 *   22 → 21  Śmieci/beczki (sounds-debris)
 *   23 → 22  Drzewa        (sounds-trees)
 *   24 → 23  Urządzenia    (sounds-devices)
 *   25 → 24  Budowanie     (sounds-building)
 *   26 → 25  Zbieranie     (sounds-pickup)
 *   50 → 49  Wyjdź (nether star)
 *
 * Zapis: raft-players.yml (settings) przy aktywnej sesji, inaczej pamięć —
 * wspólny magazyn z panelem ustawień (UstawieniaGUI.saveValue). NA RAZIE to
 * przechowalnia stanu: realne wyciszanie playSound w managerach będzie
 * podłączane po kolei w kolejnych krokach. Kliki anulowane, zero czatu/logu.
 */
public class DzwiekiGUI implements Listener {

    /** Tytuł GUI. */
    public static final String GUI_TITLE = "§8Dźwięki";

    public static final int GUI_SIZE = 54; // 6×9

    /** Globalny włącznik (w grze slot 5). */
    public static final int SLOT_MASTER = 4;
    /** Kategoria i=0..6 siedzi na kodzie 19+i (w grze 20–26). */
    public static final int SLOT_CATEGORIES = 19;
    /** Nether star „Wyjdź” (kod 49, w grze 50). */
    public static final int SLOT_EXIT = 49;

    /** Kategoria dźwięków: klucz ustawienia, ikona, nazwa. */
    private record Category(String key, Material material, String name) {}

    private static final Category[] CATEGORIES = {
            new Category("sounds-hook", Material.FISHING_ROD, "Hak"),
            new Category("sounds-net", Material.STRING, "Sieć zbierająca"),
            new Category("sounds-debris", Material.BARREL, "Śmieci i beczki"),
            new Category("sounds-trees", Material.OAK_LOG, "Drzewa"),
            new Category("sounds-devices", Material.LIGHTNING_ROD, "Urządzenia"),
            new Category("sounds-building", Material.IRON_AXE, "Budowanie"),
            new Category("sounds-pickup", Material.HOPPER, "Zebrane przedmioty"),
    };

    private final RaftPlugin plugin;
    /** Wspólny magazyn ustawień (sesja/pamięć) — należy do panelu ustawień. */
    private final UstawieniaGUI ustawienia;

    public DzwiekiGUI(RaftPlugin plugin, UstawieniaGUI ustawienia) {
        this.plugin = plugin;
        this.ustawienia = ustawienia;
    }

    // ─────────────────────── OTWIERANIE ─────────────────────────

    /** Buduje i otwiera GUI dźwięków. */
    public void open(Player player) {
        DzwiekiHolder holder = new DzwiekiHolder();
        Inventory inv = plugin.getParent().getServer().createInventory(
                holder, GUI_SIZE, GUI_TITLE);
        holder.setInventory(inv);

        ItemStack glass = UstawieniaGUI.createGlass();
        for (int slot = 0; slot < GUI_SIZE; slot++) {
            inv.setItem(slot, glass);
        }

        inv.setItem(SLOT_MASTER, masterIcon(player));
        for (int i = 0; i < CATEGORIES.length; i++) {
            inv.setItem(SLOT_CATEGORIES + i, categoryIcon(player, CATEGORIES[i]));
        }
        inv.setItem(SLOT_EXIT, UstawieniaGUI.createExitIcon());

        player.openInventory(inv);
    }

    // ─────────────────────── IKONY ─────────────────────────

    /** Master: wszystkie dźwięki trybu Raft (klucz sounds-enabled). */
    private ItemStack masterIcon(Player player) {
        boolean on = "on".equals(ustawienia.settingValue(player,
                UstawieniaGUI.SETTING_SOUNDS, "on"));
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Wszystkie dźwięki trybu Raft");
        lore.add("");
        lore.add(on ? ChatColor.GREEN + "Stan: włączony"
                : ChatColor.RED + "Stan: wyłączony");
        lore.add(ChatColor.YELLOW + "Kliknij, aby przełączyć");
        return icon(Material.NOTE_BLOCK,
                (on ? ChatColor.GREEN + "§l" : ChatColor.RED + "§l") + "Wszystkie dźwięki",
                lore);
    }

    /** Kategoria: ikona + stan w podpowiedzi (domyślnie włączona). */
    private ItemStack categoryIcon(Player player, Category category) {
        boolean on = "on".equals(ustawienia.settingValue(player, category.key(), "on"));
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Dźwięki: " + ChatColor.WHITE + category.name());
        lore.add("");
        lore.add(on ? ChatColor.GREEN + "Stan: włączony"
                : ChatColor.RED + "Stan: wyłączony");
        lore.add(ChatColor.YELLOW + "Kliknij, aby przełączyć");
        return icon(category.material(),
                ChatColor.GOLD + category.name(), lore);
    }

    /** Wspólny builder ikon (jak w panelu ustawień). */
    private ItemStack icon(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    // ─────────────────────── OBSŁUGA KLIKÓW ─────────────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof DzwiekiHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != event.getInventory()) return;

        int slot = event.getRawSlot();
        if (slot == SLOT_MASTER) {
            toggle(player, UstawieniaGUI.SETTING_SOUNDS);
            event.getInventory().setItem(SLOT_MASTER, masterIcon(player));
        } else if (slot == SLOT_EXIT) {
            player.closeInventory();
        } else if (slot >= SLOT_CATEGORIES && slot < SLOT_CATEGORIES + CATEGORIES.length) {
            Category category = CATEGORIES[slot - SLOT_CATEGORIES];
            toggle(player, category.key());
            event.getInventory().setItem(slot, categoryIcon(player, category));
        }
        // Pozostałe sloty (szyby) — kliknięcia anulowane.
    }

    /** Przełącznik on/off przez wspólny magazyn panelu ustawień. */
    private void toggle(Player player, String key) {
        boolean on = "on".equals(ustawienia.settingValue(player, key, "on"));
        ustawienia.saveValue(player, key, on ? "off" : "on");
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof DzwiekiHolder) {
            event.setCancelled(true);
        }
    }

    /**
     * Holder GUI dźwięków — rozpoznaje nasze GUI w InventoryClickEvent
     * bez porównywania tytułów (wzorzec z panelu ustawień).
     */
    public static final class DzwiekiHolder implements InventoryHolder {
        private Inventory inventory;

        void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
