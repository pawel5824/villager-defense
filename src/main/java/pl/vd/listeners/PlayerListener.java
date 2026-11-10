package pl.vd.listeners;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import pl.vd.VillagerDefensePlugin;
import pl.vd.arena.Arena;
import pl.vd.kits.KitManager;
import pl.vd.util.WeatherUtil;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PlayerListener implements Listener {

    private final VillagerDefensePlugin plugin;

    // gracze, którzy otworzyli podgląd przedmiotów kitu z GUI podglądu operatora
    // (żeby przycisk „wróć” trafił z powrotem do właściwego GUI)
    private final Set<UUID> previewDetail = new HashSet<>();

    public PlayerListener(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
    }

    /** Po wejściu na serwer przywraca graczowi zapisaną porę dnia i pogodę
     *  (ustawienia z panelu „Pogoda" w sklepie - trwałe między sesjami). */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        var data = plugin.getPlayerDataManager().get(player.getUniqueId(), player.getName());
        WeatherUtil.applyTimeOfDay(player, data.getSetting(WeatherUtil.TIME_KEY, "normalna"));
        WeatherUtil.applyWeather(player, data.getSetting(WeatherUtil.WEATHER_KEY, "normalna"));
        // jawnie wybrany kit wraca z players.yml (bez tego restart dawał losowy kit)
        plugin.getKitManager().restoreChoice(player);
        // pokaż pasek XP (daily reward) - opóźnienie 1 tick żeby klient się załadował
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            plugin.getPlayerXPBar().show(player);
            // daj kompas do lobby na pierwszy pusty slot
            giveLobbyCompass(player);
        }, 1L);
    }

    /** Po zmianie świata (np. teleport na arenę) też przywraca porę dnia/pogodę,
     *  bo serwer przy zmianie świata wysyła graczowi czas/pogodę nowego świata.
     *  Zarządza też kompasem lobby: wejście do świata lobby = dostaje kompas,
     *  wyjście ze świata lobby = kompas znika (niezależnie od sposobu przejścia). */
    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        var data = plugin.getPlayerDataManager().get(player.getUniqueId(), player.getName());
        WeatherUtil.applyTimeOfDay(player, data.getSetting(WeatherUtil.TIME_KEY, "normalna"));
        WeatherUtil.applyWeather(player, data.getSetting(WeatherUtil.WEATHER_KEY, "normalna"));
        org.bukkit.Location lobby = plugin.getLobbyLocation();
        if (lobby != null && lobby.getWorld() != null && player.getWorld().equals(lobby.getWorld())) {
            giveLobbyCompass(player);
        } else {
            removeLobbyCompass(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        previewDetail.remove(player.getUniqueId());
        plugin.getShopGUI().clearPreviewArena(player.getUniqueId());
        Arena arena = findArenaOf(player);
        if (arena != null) {
            plugin.getGameManager().removePlayer(player, arena);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getPlayer().getGameMode() == org.bukkit.GameMode.SPECTATOR) return;
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.COMPASS) return;
        if (item.getItemMeta() == null || !item.getItemMeta().hasDisplayName()) return;

        Player player = event.getPlayer();

        // kompas lobby — otwiera GUI z wyborem trybu gry
        if (isLobbyCompass(item)) {
            event.setCancelled(true);
            plugin.getGameModeGUI().open(player);
            return;
        }

        // kompas sklepu — działa na arenie
        Arena arena = findArenaOf(player);
        if (arena == null) return;

        plugin.getShopGUI().openMain(player, arena);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();

        // GUI podglądu przedmiotów kitu (27 slotów, 3x9) - tylko do oglądania
        if (KitManager.isKitDetailTitle(player, title)) {
            event.setCancelled(true);
            int raw = event.getRawSlot();
            String kitId = plugin.getKitManager().findKitByDetailTitle(player, title);
            if (raw == 22) {
                // nether star: wróć do wyboru kitów (normalny lub podgląd operatora)
                if (previewDetail.remove(player.getUniqueId())) {
                    plugin.getKitManager().openKitSelectorPreview(player);
                } else {
                    plugin.getKitManager().openKitSelector(player);
                }
            } else if (raw == 21 && kitId != null) {
                // poprzednia klasa (bez wracania do wyboru)
                String prev = plugin.getKitManager().getPrevKitId(kitId);
                if (prev != null) plugin.getKitManager().openKitDetail(player, prev);
            } else if (raw == 23 && kitId != null) {
                // następna klasa (bez wracania do wyboru)
                String next = plugin.getKitManager().getNextKitId(kitId);
                if (next != null) plugin.getKitManager().openKitDetail(player, next);
            }
            return;
        }

        if (KitManager.isKitPreviewTitle(player, title)) {
            // tryb podglądu dla operatora: kliknięcie (LPM lub PPM) na ikonę klasy
            // otwiera podgląd przedmiotów, reszta kliknięć tylko blokowana
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            String kitId = clicked == null ? null : findKitBySelectorIcon(player, clicked);
            if (kitId != null) {
                previewDetail.add(player.getUniqueId());
                plugin.getKitManager().openKitDetail(player, kitId);
            }
            return;
        }

        if (KitManager.isKitSelectorTitle(player, title)) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;
            String kitId = findKitBySelectorIcon(player, clicked);
            if (kitId == null) return;
            if (event.isLeftClick()) {
                // LPM: wybór kitu
                plugin.getKitManager().setChosenKit(player, kitId);
                pl.vd.util.Lang.send(player, "kit-selected",
                        plugin.getKitManager().getKit(kitId).localizedName(player));
                player.closeInventory();
                // wybór kitu w poczekalni = od razu na arenę z kitem w ekwipunku;
                // gracze bez wyboru zostają w lobby i dostają losowy kit na starcie
                plugin.getGameManager().onKitChosen(player);
            } else if (event.isRightClick()) {
                // PPM: podgląd przedmiotów kitu
                previewDetail.remove(player.getUniqueId());
                plugin.getKitManager().openKitDetail(player, kitId);
            }
        }
    }

    /**
     * Po wypiciu PITNEJ mikstury na arenie usuwa pustą szklaną butelkę, którą
     * vanilla zostawia w ręce gracza. Tylko pitne mikstury (POTION) - miotane
     * i lejące się rozbijają się i nie zostawiają butelki.
     */
    @EventHandler
    public void onConsumePotion(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.POTION) return;
        Player player = event.getPlayer();
        if (findArenaOf(player) == null) return; // tylko na arenie
        // butelka pojawia się w ręce zaraz PO tym zdarzeniu, więc usuwamy ją w następnym ticku
        Bukkit.getScheduler().runTask(plugin, () -> removeDrinkBottle(player));
    }

    /** Usuwa jedną szklaną butelkę z ręki/ekwipunku gracza (po wypiciu mikstury). */
    private void removeDrinkBottle(Player player) {
        ItemStack main = player.getInventory().getItemInMainHand();
        if (main.getType() == Material.GLASS_BOTTLE) {
            if (main.getAmount() > 1) {
                main.setAmount(main.getAmount() - 1);
                player.getInventory().setItemInMainHand(main);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
            return;
        }
        ItemStack off = player.getInventory().getItemInOffHand();
        if (off.getType() == Material.GLASS_BOTTLE) {
            if (off.getAmount() > 1) {
                off.setAmount(off.getAmount() - 1);
                player.getInventory().setItemInOffHand(off);
            } else {
                player.getInventory().setItemInOffHand(null);
            }
            return;
        }
        // zapas: butelka wylądowała gdzie indziej - usuń jedną skądkolwiek
        player.getInventory().removeItem(new ItemStack(Material.GLASS_BOTTLE, 1));
    }

    /** Znajduje ID kitu po ikonie w GUI wyboru (porównanie typu + nazwy w języku gracza). */
    private String findKitBySelectorIcon(Player player, ItemStack clicked) {
        for (var kit : plugin.getKitManager().getKits()) {
            ItemStack selectorIcon = plugin.getKitManager().getKitSelectorIcon(player, kit.getId());
            if (selectorIcon.getType() == clicked.getType()
                    && selectorIcon.getItemMeta() != null
                    && selectorIcon.getItemMeta().getDisplayName().equals(
                            clicked.getItemMeta() != null ? clicked.getItemMeta().getDisplayName() : "")) {
                return kit.getId();
            }
        }
        return null;
    }

    /** Blokuje przeciąganie (drag & drop) przedmiotów w podglądzie kitów i w GUI przedmiotów kitu. */
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();
        if (KitManager.isKitPreviewTitle(player, title)
                || KitManager.isKitDetailTitle(player, title)) {
            event.setCancelled(true);
        }
    }

    /** Blokuje ruch gracza podczas przerwy na arenie. */
    @EventHandler
    public void onMoveDuringPause(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Arena arena = findArenaOf(player);
        if (arena == null) return;
        if (!plugin.getPauseManager().isPaused(arena)) return;
        // anuluj ruch jeśli gracz zmienia blok pozycji (nie obrót)
        org.bukkit.Location from = event.getFrom();
        org.bukkit.Location to = event.getTo();
        if (to != null && (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ())) {
            event.setTo(from);
        }
    }

    /**
     * Daje graczowi kompas do lobby na pierwszy pusty slot w hotbarze.
     * Kompas otwiera GUI z wyborem trybu gry.
     */
    /** Rozpoznaje kompas lobby („Tryby gier”) niezależnie od slotu. */
    public boolean isLobbyCompass(ItemStack item) {
        if (item == null || item.getType() != Material.COMPASS) return false;
        if (!item.hasItemMeta() || item.getItemMeta() == null) return false;
        var meta = item.getItemMeta();
        return meta.hasDisplayName() && meta.getDisplayName().contains("Tryby gier");
    }

    public void giveLobbyCompass(Player player) {
        // sprawdź czy gracz jest w lobby serwera
        org.bukkit.Location lobby = plugin.getLobbyLocation();
        if (lobby == null || lobby.getWorld() == null) return;
        if (!player.getWorld().equals(lobby.getWorld())) return;

        // sprawdź czy gracz już ma kompas lobby
        for (int i = 0; i < 9; i++) {
            if (isLobbyCompass(player.getInventory().getItem(i))) {
                return; // już ma kompas
            }
        }

        // szukaj pierwszego pustego slotu
        int emptySlot = player.getInventory().firstEmpty();
        if (emptySlot == -1 || emptySlot > 8) return; // brak miejsca w hotbarze

        // twórz kompas
        ItemStack compass = new ItemStack(Material.COMPASS);
        var meta = compass.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("§6§lTryby gier"));
            meta.lore(java.util.List.of(
                    Component.text("§7Kliknij aby wybrać tryb gry"),
                    Component.text(""),
                    Component.text("§eVillager Defense §7- obrona wieśniaków"),
                    Component.text("§eRaft §7- przetrwanie na oceanie")
            ));
            compass.setItemMeta(meta);
        }
        player.getInventory().setItem(emptySlot, compass);
    }

    /** Usuwa kompas lobby z ekwipunku gracza. */
    public void removeLobbyCompass(Player player) {
        for (int i = 0; i < 9; i++) {
            if (isLobbyCompass(player.getInventory().getItem(i))) {
                player.getInventory().setItem(i, null);
                return;
            }
        }
    }

    private Arena findArenaOf(Player player) {
        for (Arena arena : plugin.getArenaManager().getArenas().values()) {
            if (arena.isPlaying(player)) return arena;
        }
        return null;
    }
}
