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
import pl.vd.raft.music.MuzykaCommand;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Panel USTAWIEŃ trybu Raft — totalnie inny niż na trybie VD.
 *
 * GUI 4×9 (36 slotów) z szarymi szybami jak tło; kliknięcia i przeciągania
 * są anulowane, nic z niego nie wypadnie i nic nie da się włożyć. Ikony
 * (slot w GRZE → slot w KODZIE):
 *
 *   11 → 10  Dźwięki        (note block) — przełącznik on/off (stan w lore)
 *   13 → 12  Powiadomienia  (dzwonek)    — przełącznik on/off (stan w lore)
 *   15 → 14  Muzyka         (płyta)      — otwiera TO SAMO GUI co /muzyka
 *   17 → 16  Język          (książka)    — polski/angielski, klik = zmiana
 *
 * Dźwięki i powiadomienia siedzą w raft-players.yml (PlayerManager.setSetting,
 * zapis natychmiastowy) — na razie przechowują stan; realne wyciszanie dźwięków
 * pluginu (guardy przy playSound) podłączane będzie po kolei w kolejnych krokach.
 *
 * Język zapisuje się do players.yml VD (PlayerDataManager, klucz "language",
 * wartości "polski"/"angielski" — te same co panel VD), więc Lang.isEnglish()
 * działa od razu w OBU trybach: jedno kliknięcie zmienia język globalnie.
 *
 * Panel otwiera /ustawienia w świecie Raft (SettingsCommand) — bez czatu,
 * bez logu (zasada ciszy).
 */
public class UstawieniaGUI implements Listener {

    /** Tytuł GUI (identyfikacja holdera przy obsłudze klików). */
    public static final String GUI_TITLE = "§8Ustawienia";

    // ─── Sloty w KODZIE (w grze +1: 11, 13, 15, 17) ───
    public static final int SLOT_SOUNDS = 10;
    public static final int SLOT_NOTIFICATIONS = 12;
    public static final int SLOT_MUSIC = 14;
    public static final int SLOT_LANGUAGE = 16;
    /** Nether star „Wyjdź" na środku dołu (w grze slot 32). */
    public static final int SLOT_EXIT = 31;

    public static final int GUI_SIZE = 36; // 4×9

    // ─── Klucze ustawień gracza (raft-players.yml → settings) ───
    public static final String SETTING_SOUNDS = "sounds-enabled";        // on/off
    public static final String SETTING_NOTIFICATIONS = "notifications-enabled"; // on/off

    private final RaftPlugin plugin;

    /** Zapasowy magazyn ustawień w pamięci — gdy gracz nie ma jeszcze aktywnej
     *  sesji Raft (tworzenie sesji odpalałoby powitanie i podmianę ekwipunku;
     *  wzorzec jak w CraftingCommand.setLastCategory). */
    private final Map<java.util.UUID, Map<String, String>> memory = new HashMap<>();

    /** Podmenu Dźwięki (ustawiane przez RaftPlugin po konstrukcji obu GUI). */
    private DzwiekiGUI dzwiekiGui;

    public UstawieniaGUI(RaftPlugin plugin) {
        this.plugin = plugin;
    }

    /** Podpinanie podmenu dźwięków (cykl konstrukcyjny w RaftPlugin). */
    public void setDzwiekiGui(DzwiekiGUI dzwiekiGui) {
        this.dzwiekiGui = dzwiekiGui;
    }

    // ─────────────────────── OTWIERANIE ─────────────────────────

    /** Buduje i otwiera panel ustawień dla gracza (świat Raft). */
    public void open(Player player) {
        UstawieniaHolder holder = new UstawieniaHolder();
        Inventory inv = plugin.getParent().getServer().createInventory(
                holder, GUI_SIZE, GUI_TITLE);
        holder.setInventory(inv);

        // szare szyby jako tło (wzorzec z panelu VD)
        ItemStack glass = createGlass();
        for (int slot = 0; slot < GUI_SIZE; slot++) {
            inv.setItem(slot, glass);
        }

        inv.setItem(SLOT_SOUNDS, soundsIcon(player));
        inv.setItem(SLOT_NOTIFICATIONS, notificationsIcon(player));
        inv.setItem(SLOT_MUSIC, musicIcon());
        inv.setItem(SLOT_LANGUAGE, languageIcon(player));
        inv.setItem(SLOT_EXIT, createExitIcon());

        player.openInventory(inv);
    }

    // ─────────────────────── IKONY ─────────────────────────

    /** Wartość ustawienia: z sesji (raft-players.yml), a gdy sesji brak —
     *  z zapasowego magazynu w pamięci. PUBLICZNE: korzysta z tego podmenu
     *  Dźwięki (DzwiekiGUI) — wspólny magazyn ustawień. */
    public String settingValue(Player player, String key, String def) {
        var session = plugin.getPlayerManager().getSession(player.getUniqueId());
        if (session != null) return session.getSetting(key, def);
        return memory.getOrDefault(player.getUniqueId(), Map.of()).getOrDefault(key, def);
    }

    /** Zapis ustawienia: do sesji (natychmiastowy zapis do pliku), a gdy sesji
     *  brak — tylko do pamięci. Nigdy nie tworzy sesji. */
    public void saveValue(Player player, String key, String value) {
        var session = plugin.getPlayerManager().getSession(player.getUniqueId());
        if (session != null) {
            session.setSetting(key, value);
        } else {
            memory.computeIfAbsent(player.getUniqueId(), id -> new HashMap<>()).put(key, value);
        }
    }

    /** Note block — Dźwięki; pokazuje master stan i otwiera podmenu dźwięków. */
    private ItemStack soundsIcon(Player player) {
        boolean on = "on".equals(settingValue(player, SETTING_SOUNDS, "on"));
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Wszystkie dźwięki trybu Raft");
        lore.add("");
        lore.add(on ? ChatColor.GREEN + "Stan: włączony"
                : ChatColor.RED + "Stan: wyłączony");
        lore.add(ChatColor.YELLOW + "Kliknij, aby otworzyć (kategorie)");
        return icon(Material.NOTE_BLOCK, ChatColor.GOLD + "Dźwięki", lore);
    }

    /** Dzwonek — Powiadomienia; lore pokazuje aktualny stan (włączony/wyłączony). */
    private ItemStack notificationsIcon(Player player) {
        boolean on = "on".equals(settingValue(player, SETTING_NOTIFICATIONS, "on"));
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Powiadomienia trybu Raft");
        lore.add("");
        lore.add(on ? ChatColor.GREEN + "Stan: włączony"
                : ChatColor.RED + "Stan: wyłączony");
        lore.add(ChatColor.YELLOW + "Kliknij, aby przełączyć");
        return icon(Material.BELL, ChatColor.GOLD + "Powiadomienia", lore);
    }

    /** Płyta — Muzyka; klik otwiera TO SAMO GUI co komenda /muzyka. */
    private ItemStack musicIcon() {
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Głośność, ton i częstotliwość muzyki");
        lore.add("");
        lore.add(ChatColor.YELLOW + "Kliknij, aby otworzyć");
        return icon(Material.MUSIC_DISC_CAT, ChatColor.GOLD + "Muzyka", lore);
    }

    /**
     * Książka — Język (polski/angielski). Stan czytany z players.yml VD
     * (klucz "language") — to samo źródło co Lang.isEnglish(), więc lore
     * zawsze pokazuje język faktycznie używany przez komunikaty pluginu.
     */
    private ItemStack languageIcon(Player player) {
        boolean english = pl.vd.util.Lang.isEnglish(player);
        List<String> lore = new ArrayList<>();
        lore.add(english ? ChatColor.GREEN + "✓ Angielski" : ChatColor.GRAY + "Angielski");
        lore.add(english ? ChatColor.GRAY + "Polski" : ChatColor.GREEN + "✓ Polski");
        lore.add("");
        lore.add(ChatColor.YELLOW + "Kliknij, aby zmienić");
        return icon(Material.WRITABLE_BOOK, ChatColor.GOLD + "Język", lore);
    }

    /** Nether star „Wyjdź” — zamyka GUI (wzorzec z panelu VD); STATYCZNE:
     *  korzysta z tego też podmenu Dźwięki. */
    public static ItemStack createExitIcon() {
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Zamyka ustawienia");
        return buildIcon(Material.NETHER_STAR, ChatColor.RED + "Wyjdź", lore);
    }

    /** Szyba tła (pusta nazwa — czysty wygląd slotów); STATYCZNE: podmenu Dźwięki. */
    public static ItemStack createGlass() {
        return buildIcon(Material.GRAY_STAINED_GLASS_PANE, " ", new ArrayList<>());
    }

    /** Wspólny builder ikon (ChatColor + setDisplayName jak w CraftingCommand). */
    private static ItemStack buildIcon(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Wspólny builder ikon (ChatColor + setDisplayName jak w CraftingCommand). */
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
        if (!(event.getInventory().getHolder() instanceof UstawieniaHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        // Reaguj tylko na kliknięcia w NASZE GUI (nie w ekwipunek gracza).
        if (event.getClickedInventory() != event.getInventory()) return;

        int slot = event.getRawSlot();
        switch (slot) {
            case SLOT_SOUNDS -> {
                // Podmenu dźwięków (GUI 6×9: master + kategorie + Wyjdź).
                if (dzwiekiGui != null) dzwiekiGui.open(player);
            }
            case SLOT_NOTIFICATIONS -> toggleSetting(player, SETTING_NOTIFICATIONS);
            case SLOT_MUSIC -> {
                // To samo GUI co /muzyka — jeden kod obsługi, jeden wygląd.
                MuzykaCommand muzyka = findMuzykaCommand();
                if (muzyka != null) {
                    muzyka.openGui(player);
                }
            }
            case SLOT_LANGUAGE -> toggleLanguage(player);
            case SLOT_EXIT -> player.closeInventory();
            default -> { /* szyby i puste sloty — nic */ }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof UstawieniaHolder) {
            event.setCancelled(true);
        }
    }

    /**
     * Przełącznik on/off: zapis (raft-players.yml przy aktywnej sesji, inaczej
     * pamięć) + odświeżenie ikony w GUI — bez żadnych komunikatów (zasada ciszy).
     */
    private void toggleSetting(Player player, String key) {
        boolean on = "on".equals(settingValue(player, key, "on"));
        saveValue(player, key, on ? "off" : "on");
        refreshIcon(player, key.equals(SETTING_SOUNDS) ? SLOT_SOUNDS : SLOT_NOTIFICATIONS);
    }

    /**
     * Zmiana języka polski ↔ angielski. Zapis do players.yml VD (klucz
     * "language", wartości "polski"/"angielski" — jak w panelu VD), więc
     * Lang.isEnglish() od razu widzi zmianę w obu trybach. GUI odświeża się
     * na żywo, bez czatu (zasada ciszy).
     */
    private void toggleLanguage(Player player) {
        pl.vd.VillagerDefensePlugin parent = (pl.vd.VillagerDefensePlugin) plugin.getParent();
        var data = parent.getPlayerDataManager()
                .get(player.getUniqueId(), player.getName());
        boolean english = pl.vd.util.Lang.isEnglish(player);
        data.setSetting(pl.vd.shop.ShopGUI.LANG_KEY,
                english ? pl.vd.shop.ShopGUI.LANG_POLISH : pl.vd.shop.ShopGUI.LANG_ENGLISH);
        parent.getPlayerDataManager().saveAll();
        inv(player).setItem(SLOT_LANGUAGE, languageIcon(player));
    }

    /** Podmienia jedną ikonę w otwartym GUI (odświeżenie stanu na żywo). */
    private void refreshIcon(Player player, int slot) {
        if (slot == SLOT_SOUNDS) {
            inv(player).setItem(slot, soundsIcon(player));
        } else if (slot == SLOT_NOTIFICATIONS) {
            inv(player).setItem(slot, notificationsIcon(player));
        }
    }

    /** Otwarty inventory gracza, jeśli to nasz panel (null w przeciwnym razie). */
    private Inventory inv(Player player) {
        Inventory open = player.getOpenInventory().getTopInventory();
        return open.getHolder() instanceof UstawieniaHolder ? open : null;
    }

    /**
     * Znajduje zarejestrowaną komendę /muzyka (executor = MuzykaCommand),
     * żeby panel otwierał dokładnie to samo GUI co komenda.
     */
    private MuzykaCommand findMuzykaCommand() {
        if (plugin.getParent().getCommand("muzyka") != null) {
            if (plugin.getParent().getCommand("muzyka").getExecutor()
                    instanceof MuzykaCommand muzyka) {
                return muzyka;
            }
        }
        return null;
    }

    /**
     * Holder GUI ustawień Rafta — rozpoznaje nasze GUI w InventoryClickEvent
     * bez porównywania tytułów (wzorzec z CraftingCommand/MuzykaCommand).
     */
    public static final class UstawieniaHolder implements InventoryHolder {
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
