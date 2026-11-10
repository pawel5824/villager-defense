package pl.vd.raft.blocks;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GUI dla urządzeń z bateriami.
 * Otwiera się po PPM na urządzenie (gdy jest włożona bateria).
 */
public class DeviceGUI implements Listener {

    // Mapa otwartych GUI: playerUUID -> DeviceBlock2x2
    private final Map<java.util.UUID, DeviceBlock2x2> openGUIs = new HashMap<>();

    // Sloty w GUI
    private static final int SLOT_BATTERY_INFO = 10;   // informacje o baterii
    private static final int SLOT_MAP = 12;             // mapa wysp
    private static final int SLOT_COMPASS = 14;         // kompas/kierunek
    private static final int SLOT_SETTINGS = 28;        // ustawienia
    private static final int SLOT_CLOSE = 31;           // zamknij
    private static final int SLOT_STATS = 32;           // statystyki

    // Nazwa GUI
    private static final String GUI_TITLE = "§8[ §bModuł Odbiorczy §8]";

    /** Ile wysp pokazuje mapa (reszta tylko jako licznik) — faza 3. */
    private static final int MAP_ISLAND_LIMIT = 5;

    /**
     * Otwiera GUI dla urządzenia.
     */
    public void openGUI(Player player, DeviceBlock2x2 device) {
        Inventory inventory = Bukkit.createInventory(null, 45, GUI_TITLE);

        renderItems(inventory, device);

        // Dekoracje - szkło (tylko puste sloty, więc nie zasłania paneli)
        for (int i = 0; i < 45; i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, createGlassPane());
            }
        }

        // Zapisz otwarte GUI
        openGUIs.put(player.getUniqueId(), device);

        // Otwórz dla gracza
        player.openInventory(inventory);
    }

    /**
     * Wypełnia sloty GUI aktualnym stanem urządzenia (jedno miejsce prawdy dla
     * openGUI i odświeżeń — faza 3: mapa i kompas zależą od liczby anten).
     */
    private void renderItems(Inventory inventory, DeviceBlock2x2 device) {
        inventory.setItem(SLOT_BATTERY_INFO, createBatteryInfoItem(device));
        inventory.setItem(SLOT_MAP, createMapItem(device));
        inventory.setItem(SLOT_COMPASS, createCompassItem(device));
        inventory.setItem(SLOT_SETTINGS, createSettingsItem());
        inventory.setItem(SLOT_CLOSE, createCloseItem());
        inventory.setItem(SLOT_STATS, createStatsItem(device));
    }

    /**
     * Tworzy item informacji o baterii.
     */
    private ItemStack createBatteryInfoItem(DeviceBlock2x2 device) {
        ItemStack item = new ItemStack(Material.BLAZE_POWDER);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§6⚡ Informacje o baterii");

        List<String> lore = new ArrayList<>();
        if (device.hasBattery()) {
            int energy = device.getEnergy();
            int maxEnergy = device.getDeviceType().getDefaultMaxEnergy();
            float percent = (float) energy / maxEnergy * 100;

            lore.add("§7Stan: §aWładowana");
            lore.add("§7Energia: §e" + energy + " / " + maxEnergy);
            lore.add("§7Poziom: §e" + String.format("%.1f", percent) + "%");

            // Pasek postępu
            int filled = (int) (percent / 10);
            StringBuilder bar = new StringBuilder("§7[");
            for (int i = 0; i < 10; i++) {
                if (i < filled) {
                    bar.append("§a█");
                } else {
                    bar.append("§8░");
                }
            }
            bar.append("§7]");
            lore.add(bar.toString());

            lore.add("");
            lore.add("§eKliknij aby wyjąć baterię");
        } else {
            lore.add("§7Stan: §cPusta");
            lore.add("§7Brak baterii w slocie");
            lore.add("");
            lore.add("§7Trzymaj baterię w ręce");
            lore.add("§7i kliknij aby włożyć");
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Tworzy item mapy. Faza 3 (docs/ANTENY_ODBIORNIK.md): mapa pokazuje wyspy
     * dopiero przy 3/3 podłączonych antenach i zasilonym odbiorniku. Bez tego
     * jest szarą ikoną z licznikiem — zero komunikatów na czacie (AGENTS.md §1),
     * cały stan widać wyłącznie w lore tego przedmiotu.
     */
    private ItemStack createMapItem(DeviceBlock2x2 device) {
        boolean unlocked = device.canSeeIslands();
        ItemStack item = new ItemStack(unlocked ? Material.FILLED_MAP : Material.MAP);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(unlocked
                ? "§3🗺️ Mapa wysp"
                : "§8🗺️ Mapa wysp §7(§e" + device.getAntennaCount() + "§7/§e3§7)");

        List<String> lore = new ArrayList<>();
        lore.add("§7Anteny: §e" + device.getAntennaCount() + " §7/ §e3");
        lore.add("");

        if (unlocked) {
            List<pl.vd.raft.world.Island> islands = islandsByDistance(device.getBaseLocation());
            if (islands.isEmpty()) {
                lore.add("§7Brak wysp w okolicy odbiornika.");
            } else {
                int shown = Math.min(MAP_ISLAND_LIMIT, islands.size());
                for (int i = 0; i < shown; i++) {
                    pl.vd.raft.world.Island island = islands.get(i);
                    int distance = (int) Math.round(island.getCenter().distance(device.getBaseLocation()));
                    lore.add((island.isExplored() ? "§8✔ §f" : "§8• §f") + island.getName()
                            + " §8— §e" + distance + " m §8" + compassDirection(device.getBaseLocation(), island.getCenter()));
                }
                if (islands.size() > shown) {
                    lore.add("§8…i " + (islands.size() - shown) + " dalszych");
                }
            }
            lore.add("");
            lore.add("§eKliknij aby odświeżyć mapę");
        } else {
            lore.add("§cWymaga §e3§c anten.");
            int missing = Math.max(0, DeviceBlock2x2.MAX_ANTENNAS - device.getAntennaCount());
            if (missing > 0) {
                lore.add("§7Podłącz jeszcze §e" + missing + " " + (missing == 1 ? "antenę" : "anteny") + "§7.");
            }
            if (!device.hasBattery() || device.getEnergy() <= 0) {
                lore.add("§7Włóż naładowaną baterię.");
            }
            lore.add("");
            lore.add("§7Antenę stawiasz przedmiotem");
            lore.add("§7\"§bAntena§7\" w pokładzie, do §e8§7 bloków");
            lore.add("§7od odbiornika i na tym samym");
            lore.add("§7poziomie.");
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Tworzy item kompasu. Ten sam gate co mapa — kompas celuje w wyspę, więc
     * bez 3/3 anten ustawianie go byłoby obejściem mechaniki anten (faza 3).
     */
    private ItemStack createCompassItem(DeviceBlock2x2 device) {
        boolean unlocked = device.canSeeIslands();
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(unlocked
                ? "§6🧭 Kierunek do wysp"
                : "§8🧭 Kierunek do wysp §7(§e" + device.getAntennaCount() + "§7/§e3§7)");

        List<String> lore = new ArrayList<>();
        lore.add("§7Anteny: §e" + device.getAntennaCount() + " §7/ §e3");
        lore.add("");

        if (unlocked) {
            lore.add("§7Wskaż kierunek");
            lore.add("§7do najbliższej wyspy.");
            lore.add("");
            lore.add("§eKliknij aby ustawić kompas");
        } else {
            lore.add("§cWymaga §e3§c anten.");
            lore.add("");
            lore.add("§8Kompas milczy, dopóki odbiornik");
            lore.add("§8nie ma kompletu anten.");
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Wyspy posortowane rosnąco po odległości od punktu (świat musi się zgadzać).
     * Faza 3: to jedyne źródło danych dla mapy — raport na czacie jest wycięty.
     */
    private List<pl.vd.raft.world.Island> islandsByDistance(org.bukkit.Location from) {
        pl.vd.raft.RaftPlugin raftPlugin = pl.vd.raft.RaftPlugin.getInstance();
        if (raftPlugin == null) return new ArrayList<>();

        pl.vd.raft.world.IslandManager islandManager = raftPlugin.getIslandManager();
        if (islandManager == null) return new ArrayList<>();

        List<pl.vd.raft.world.Island> islands = new ArrayList<>(islandManager.getIslands().values());
        islands.removeIf(island -> island.getCenter().getWorld() == null
                || island.getCenter().getWorld() != from.getWorld());
        islands.sort(java.util.Comparator.comparingDouble(island -> island.getCenter().distance(from)));
        return islands;
    }

    /** Kierunek świata (8 stron) z punktu „from" do „to" — skrót jak na kompasie. */
    private String compassDirection(org.bukkit.Location from, org.bukkit.Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double angle = Math.toDegrees(Math.atan2(dx, -dz)); // 0° = północ (-Z)
        if (angle < 0) angle += 360;
        String[] dirs = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        return dirs[(int) Math.round(angle / 45.0) % 8];
    }

    /**
     * Tworzy item ustawień.
     */
    private ItemStack createSettingsItem() {
        ItemStack item = new ItemStack(Material.REDSTONE);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§c⚙️ Ustawienia");

        List<String> lore = new ArrayList<>();
        lore.add("§7Konfiguruj urządzenie.");
        lore.add("");
        lore.add("§7Coming soon...");

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Tworzy item zamykający.
     */
    private ItemStack createCloseItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§c❌ Zamknij");

        List<String> lore = new ArrayList<>();
        lore.add("§7Zamknij panel urządzenia");

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Tworzy item statystyk.
     */
    private ItemStack createStatsItem(DeviceBlock2x2 device) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§a📊 Statystyki");

        List<String> lore = new ArrayList<>();
        lore.add("§7Typ: §e" + device.getDeviceType().getDisplayName());
        lore.add("§7Opis: §7" + device.getDeviceType().getDescription());
        lore.add("§7Max energia: §e" + device.getDeviceType().getDefaultMaxEnergy());
        lore.add("§7Energia/użycie: §e" + device.getDeviceType().getEnergyPerUse());
        lore.add("§7Anteny: §e" + device.getAntennaCount() + " §7/ §e" + DeviceBlock2x2.MAX_ANTENNAS);
        lore.add("");
        lore.add("§7Pozycja: §e" + formatLocation(device.getBaseLocation()));

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Tworzy dekoracyjną szybkę.
     */
    private ItemStack createGlassPane() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(" ");
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Formatuje lokalizację do czytelnego tekstu.
     */
    private String formatLocation(org.bukkit.Location loc) {
        return loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();
    }

    // ========== OBSŁUGA KLIKNIĘĆ ==========

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;

        // Anuluj domyślne zachowanie (nie bierz itemów)
        event.setCancelled(true);

        int slot = event.getRawSlot();
        DeviceBlock2x2 device = openGUIs.get(player.getUniqueId());
        if (device == null) return;

        switch (slot) {
            case SLOT_BATTERY_INFO -> handleBatteryClick(player, device);
            case SLOT_MAP -> handleMapClick(player, device);
            case SLOT_COMPASS -> handleCompassClick(player, device);
            case SLOT_SETTINGS -> handleSettingsClick(player, device);
            case SLOT_CLOSE -> handleCloseClick(player);
            case SLOT_STATS -> handleStatsClick(player, device);
        }
    }

    /**
     * Kliknięcie w baterię - wyjąć/włożyć.
     */
    private void handleBatteryClick(Player player, DeviceBlock2x2 device) {
        device.onBatterySlotClick(player);
        // Odśwież GUI
        refreshGUI(player, device);
    }

    /**
     * Kliknięcie w mapę - pokaż odległości do wysp.
     */
    private void handleMapClick(Player player, DeviceBlock2x2 device) {
        // Faza 3: bez 3/3 anten mapa jest zablokowana — nic się nie dzieje
        // (żadnego komunikatu, zgodnie z AGENTS.md §1).
        if (!device.canSeeIslands()) return;

        // Odświeżenie odległości bez zamykania GUI.
        refreshGUI(player, device);
    }

    /**
     * Kliknięcie w kompas - ustaw kierunek.
     */
    private void handleCompassClick(Player player, DeviceBlock2x2 device) {
        // Faza 3: ten sam gate co mapa — bez kompletu anten kompas nie działa.
        // Sprawdzamy PRZED zamknięciem GUI, żeby zablokowany przycisk nie zamykał okna.
        if (!device.canSeeIslands()) return;

        // Najbliższa wyspa (mapa jest odblokowana, więc lista istnieje).
        List<pl.vd.raft.world.Island> islands = islandsByDistance(player.getLocation());
        if (islands.isEmpty()) return;

        player.closeInventory();
        openGUIs.remove(player.getUniqueId());

        // Ustaw kompas na najbliższą wyspę (bez komunikatu na czacie — AGENTS.md pkt 1)
        player.setCompassTarget(islands.get(0).getCenter());
    }

    /**
     * Kliknięcie w ustawienia.
     */
    private void handleSettingsClick(Player player, DeviceBlock2x2 device) {
        // Bez komunikatu na czacie (AGENTS.md pkt 1) — przycisk nic nie robi.
    }

    /**
     * Kliknięcie w zamknij.
     */
    private void handleCloseClick(Player player) {
        player.closeInventory();
        openGUIs.remove(player.getUniqueId());
    }

    /**
     * Kliknięcie w statystyki.
     */
    private void handleStatsClick(Player player, DeviceBlock2x2 device) {
        // Bez komunikatu na czacie (AGENTS.md pkt 1) — te same dane są w opisie
        // przedmiotu "§a📊 Statystyki" w GUI, więc nic nie ginie.
    }

    /**
     * Odświeża GUI (po zmianie stanu baterii).
     */
    private void refreshGUI(Player player, DeviceBlock2x2 device) {
        if (player.getOpenInventory() == null) return;
        if (!player.getOpenInventory().getTitle().equals(GUI_TITLE)) return;

        Inventory inventory = player.getOpenInventory().getTopInventory();
        renderItems(inventory, device);
    }

    /**
     * Zamyka GUI gdy gracz zamyka ekwipunek.
     */
    @EventHandler
    public void onInventoryClose(InventoryClickEvent event) {
        // To jesthandler dla zamknięcia - nie kliknięcia
        // Zamknięciem zajmuje się osobny event
    }

    /**
     * Usuwa gracza z listy otwartych GUI.
     */
    public void removeOpenGUI(java.util.UUID playerUUID) {
        openGUIs.remove(playerUUID);
    }
}
