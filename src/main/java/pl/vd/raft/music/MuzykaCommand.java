package pl.vd.raft.music;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import pl.vd.raft.RaftPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Komenda /muzyka — ustawienia muzyki trybu Raft PER GRACZ.
 *
 * Bez argumentów otwiera GUI 6×9 (54 sloty): rzędy 2–5 = PŁYTY utworów z
 * configu (każdy utwór na własnej waniliowej płycie; LPM = wybierz utwór,
 * Shift+LPM = wróć do losowania), dolny rząd: betonowy SUWAK GŁOŚNOŚCI
 * (LPM +1, Shift+LPM +10, PPM −1, Shift+PPM −10; beton = poziom: zielony 100%
 * → czerwony 0%), lejek CZĘSTOTLIWOŚCI, cugle ZAPĘTLENIA i nether star
 * „Wyjdź”. Wszystkie kliknięcia są anulowane, więc nic z GUI nie
 * wyciągniesz. Ustawienia zmienia się też podkomendami:
 *
 *   /muzyka wlacz                 — włącza muzykę (pierwszy utwór za chwilę)
 *   /muzyka wylacz                — wyłącza i ucina aktualny utwór
 *   /muzyka glosnosc <0–100>      — głośność (100 = pełna)
 *   /muzyka ton <50–200>          — ton = prędkość (100 = oryginał)
 *   /muzyka czestotliwosc [wartość] — natychmiastowo/często/normalnie/
 *                                     rzadko/nigdy (bez wartości = cykl);
 *                                     „natychmiastowo” = utwór gra do końca,
 *                                     a następny rusza od razu (długości
 *                                     z configu raft-music, patrz docs/MUZYKA.md)
 *   /muzyka status                — podgląd aktualnych ustawień
 *
 * Działa TYLKO w świecie Raft (jak /wytwarzanie); poza nim komunikat.
 * Ustawienia siedzą w raft-players.yml (PlayerSession settings), więc są
 * osobne dla każdego gracza i przetrwają restart serwera.
 *
 * Podpowiedzi w podkomendach są jasne (zakresy, wartości) — to odpowiedź
 * na świadomą akcję gracza, nie spam; nic nie leci do logu serwera.
 */
public class MuzykaCommand implements CommandExecutor, TabCompleter, Listener {

    /** Tytuł GUI (identyfikacja holdera przy obsłudze klików). */
    public static final String GUI_TITLE = "Muzyka";

    /** GUI 6×9 (54 sloty). */
    public static final int GUI_SIZE = 54;
    /** Suwak głośności (kod 53, w grze 54): betony poziomu (czerwony 0% →
     *  zielony 100%), LPM/PPM ±1, Shift ±10. */
    public static final int SLOT_VOLUME = 53;
    /** Lejek — częstotliwość (kod 46, w grze 47): LPM w górę, PPM w dół. */
    public static final int SLOT_FREQ = 46;
    /** Cugle — zapętlenie (kod 47, w grze 48): klik = włącz/wyłącz. */
    public static final int SLOT_LOOP = 47;
    /** Nether star „Wyjdź" (kod 49, w grze 50). */
    public static final int SLOT_EXIT = 49;
    // Kody 9–44 (w grze 10–45, rzędy 2–5) — PŁYTY utworów z configu (do 36;
    // utwór i = kod 9+i). Klik: LPM = wybierz, Shift+LPM = wróć do losowania.

    private final RaftPlugin plugin;
    private final MusicManager music;

    public MuzykaCommand(RaftPlugin plugin, MusicManager music) {
        this.plugin = plugin;
        this.music = music;
    }

    // ─────────────────────── KOMENDA ─────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Ta komenda jest tylko dla graczy.");
            return true;
        }

        // Tylko świat Raft (nazwa z configu: raft-world-name, domyślnie RAFT)
        String raftWorldName = plugin.getConfig().getString("raft-world-name", "RAFT");
        if (!player.getWorld().getName().equalsIgnoreCase(raftWorldName)) {
            player.sendMessage(ChatColor.RED + "Ta komenda działa tylko w trybie Raft.");
            return true;
        }

        // /muzyka — GUI 6×9 (suwak głośności + Wyjdź; rzędy 2–4 rezerwa na płyty)
        if (args.length == 0) {
            if (!music.isMasterEnabled()) {
                player.sendMessage(ChatColor.RED + "Muzyka jest wyłączona na serwerze.");
                return true;
            }
            openGui(player);
            return true;
        }

        switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
            case "wlacz", "włącz", "on" -> {
                music.setEnabled(player, true);
                player.sendMessage(ChatColor.GREEN + "Muzyka włączona.");
            }
            case "wylacz", "wyłącz", "off" -> {
                music.setEnabled(player, false);
                player.sendMessage(ChatColor.YELLOW + "Muzyka wyłączona.");
            }
            case "glosnosc", "głośność", "volume" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Użycie: /muzyka glosnosc <0-100>");
                    return true;
                }
                try {
                    int percent = Integer.parseInt(args[1]);
                    float volume = Math.max(0f, Math.min(100, percent)) / 100f;
                    music.setVolume(player, volume);
                    player.sendMessage(ChatColor.YELLOW + "Głośność: "
                            + Math.round(volume * 100) + "%");
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "Użycie: /muzyka glosnosc <0-100>");
                }
            }
            case "ton", "pitch" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Użycie: /muzyka ton <50-200>");
                    return true;
                }
                try {
                    int percent = Integer.parseInt(args[1]);
                    float pitch = Math.max(0.5f, Math.min(2f, percent / 100f));
                    music.setPitch(player, pitch);
                    player.sendMessage(ChatColor.YELLOW + "Ton/prędkość: "
                            + Math.round(pitch * 100) + "%");
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "Użycie: /muzyka ton <50-200>");
                }
            }
            case "czestotliwosc", "częstotliwość", "freq" -> {
                if (args.length >= 2) {
                    String normalized = args[1].toLowerCase(java.util.Locale.ROOT)
                            .replace("ł", "l").replace("Ł", "l");
                    music.setFrequency(player, normalized);
                    player.sendMessage(ChatColor.YELLOW + "Częstotliwość: "
                            + freqLabel(music.frequencyOf(player)));
                } else {
                    String next = music.cycleFrequency(player);
                    player.sendMessage(ChatColor.YELLOW + "Częstotliwość: "
                            + freqLabel(next));
                }
            }
            case "status" -> sendStatus(player);
            default -> player.sendMessage(ChatColor.RED
                    + "Użycie: /muzyka [wlacz|wylacz|glosnosc|ton|czestotliwosc|status]");
        }
        return true;
    }

    /** Wypisuje aktualne ustawienia muzyki gracza. */
    private void sendStatus(Player player) {
        player.sendMessage(ChatColor.GOLD + "— Muzyka Raft —");
        player.sendMessage(ChatColor.YELLOW + "Stan: "
                + (music.isEnabled(player)
                        ? ChatColor.GREEN + "włączona" : ChatColor.RED + "wyłączona"));
        player.sendMessage(ChatColor.YELLOW + "Głośność: " + ChatColor.WHITE
                + Math.round(music.volumeOf(player) * 100) + "%");
        player.sendMessage(ChatColor.YELLOW + "Ton/prędkość: " + ChatColor.WHITE
                + Math.round(music.pitchOf(player) * 100) + "%");
        player.sendMessage(ChatColor.YELLOW + "Częstotliwość: " + ChatColor.WHITE
                + freqLabel(music.frequencyOf(player)));
        String selected = music.getSelectedTrack(player);
        player.sendMessage(ChatColor.YELLOW + "Następny utwór: " + ChatColor.WHITE
                + (selected != null ? selected + " (jednorazowo)"
                : music.isLoopEnabled(player) ? "ostatnio grany (zapętlenie)"
                : "losowanie"));
    }

    /** Etykieta częstotliwości z polskimi znakami. */
    private String freqLabel(String value) {
        for (int i = 0; i < MusicManager.FREQ_VALUES.length; i++) {
            if (MusicManager.FREQ_VALUES[i].equals(value)) {
                return MusicManager.FREQ_LABELS[i];
            }
        }
        return value;
    }

    // ─────────────────────── TAB-COMPLETION ───────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>(List.of(
                    "wlacz", "wylacz", "glosnosc", "ton", "czestotliwosc", "status"));
            out.removeIf(s -> !s.startsWith(args[0].toLowerCase(java.util.Locale.ROOT)));
            return out;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("czestotliwosc")) {
            List<String> out = new ArrayList<>(List.of(MusicManager.FREQ_VALUES));
            out.removeIf(s -> !s.startsWith(args[1].toLowerCase(java.util.Locale.ROOT)));
            return out;
        }
        return List.of();
    }

    // ───────────────────────── GUI (6×9) ─────────────────────

    /**
     * Otwiera GUI 6×9 (54 sloty): płyty utworów + suwak głośności, częstotliwość,
     * zapętlenie, wyjście. Kliknięcia i przeciągania są anulowane, więc nic
     * z niego nie wypadnie i nic nie da się włożyć.
     *
     * PUBLICZNE: to samo GUI otwiera panel ustawień Rafta (UstawieniaGUI,
     * slot „Muzyka") — dzięki temu /muzyka i panel mają identyczny widok.
     */
    public void openGui(Player player) {
        MuzykaHolder holder = new MuzykaHolder();
        Inventory inv = plugin.getServer().createInventory(holder, GUI_SIZE, GUI_TITLE);
        holder.setInventory(inv);
        // Płyty utworów z configu (PAPIER z CMD): i-ty utwór na kodzie 9+i
        // (rzędy 2–5; 5. rząd celowo zostaje pusty, dopóki utworów nie przybędzie).
        java.util.List<String> trackList = music.getTracks();
        for (int i = 0; i < trackList.size() && i < MAX_TRACKS; i++) {
            inv.setItem(9 + i, trackChip(player, trackList.get(i), i));
        }

        inv.setItem(SLOT_VOLUME, volumeIcon(player));
        inv.setItem(SLOT_FREQ, freqIcon(player));
        inv.setItem(SLOT_LOOP, loopIcon(player));
        inv.setItem(SLOT_EXIT, exitIcon());
        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MuzykaHolder)) return;
        event.setCancelled(true); // nic nie wyciągniesz i nic nie włożysz
        if (!(event.getWhoClicked() instanceof Player player)) return;
        // Reaguj tylko na kliknięcia w NASZE GUI (nie w ekwipunek gracza).
        if (event.getClickedInventory() != event.getInventory()) return;

        int slot = event.getRawSlot();
        if (slot == SLOT_VOLUME) {
            int delta;
            if (event.isLeftClick()) delta = event.isShiftClick() ? 10 : 1;
            else if (event.isRightClick()) delta = event.isShiftClick() ? -10 : -1;
            else return; // inne typy kliknięć (np. klawisze 1–9) — nic

            int percent = Math.round(music.volumeOf(player) * 100f);
            int next = Math.max(0, Math.min(100, percent + delta));
            music.setVolume(player, next / 100f);
            event.getInventory().setItem(SLOT_VOLUME, volumeIcon(player)); // żywy podgląd
        } else if (slot == SLOT_EXIT) {
            player.closeInventory();
        } else if (slot == SLOT_FREQ) {
            // Lejek: LPM = wybór w GÓRĘ listy (bliżej „natychmiastowo”),
            // PPM = w DÓŁ (bliżej „nigdy”). Na krańcach zostaje.
            boolean up = event.isLeftClick();
            boolean down = event.isRightClick();
            if (!up && !down) return;
            String current = music.frequencyOf(player);
            int index = 0;
            for (int i = 0; i < MusicManager.FREQ_VALUES.length; i++) {
                if (MusicManager.FREQ_VALUES[i].equals(current)) index = i;
            }
            int next = up ? Math.max(0, index - 1)
                    : Math.min(MusicManager.FREQ_VALUES.length - 1, index + 1);
            if (next != index) {
                music.setFrequency(player, MusicManager.FREQ_VALUES[next]);
            }
            event.getInventory().setItem(SLOT_FREQ, freqIcon(player)); // żywy podgląd
        } else if (slot == SLOT_LOOP) {
            // Cugle: klik = przełącz zapętlenie ostatnio granego utworu.
            music.setLoop(player, !music.isLoopEnabled(player));
            event.getInventory().setItem(SLOT_LOOP, loopIcon(player)); // żywy podgląd
        } else if (slot >= 9 && slot <= 44) {
            // Płyta utworu: LPM = wybierz ten utwór; Shift+LPM na wybranej płycie
            // = wróć do losowania (czysty wybór).
            int index = slot - 9;
            java.util.List<String> trackList = music.getTracks();
            if (index < trackList.size()) {
                String track = trackList.get(index);
                if (event.isShiftClick() && track.equals(music.getSelectedTrack(player))) {
                    music.setSelectedTrack(player, null);
                } else if (event.isLeftClick()) {
                    music.setSelectedTrack(player, track);
                } else {
                    return;
                }
                event.getInventory().setItem(slot, trackChip(player, track, index));
            }
        }
        // Pozostałe puste sloty — kliknięcia anulowane.
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof MuzykaHolder) {
            event.setCancelled(true);
        }
    }

    /** Wyjście z serwera: zapomina stan muzyki gracza (mapy w MusicManager
     *  nie rosłyby wtedy z każdym gościem, a nieaktualna wysłana głośność
     *  tłumiłaby jej ponowną wysyłkę po powrocie z restartowanym klientem). */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        music.forget(event.getPlayer());
    }

    // ─────────────────────── IKONY GUI ───────────────────────

    /**
     * Suwak głośności jako BETON POZIOMU (kolor = najbliższy próg od dołu):
     * zielony 100%, jasnozielony 75%, żółty 50%, pomarańczowy 25%, czerwony 0%
     * (głośność wyłączona). Nazwa pokazuje dokładny %, lore pasek 10 segmentów.
     */
    private ItemStack volumeIcon(Player player) {
        int percent = Math.round(music.volumeOf(player) * 100f);
        if (percent > 100) percent = 100;
        if (percent < 0) percent = 0;

        Material concrete;
        String level;
        if (percent >= 100) {
            concrete = Material.GREEN_CONCRETE;
            level = "100%";
        } else if (percent >= 75) {
            concrete = Material.LIME_CONCRETE;
            level = "75%";
        } else if (percent >= 50) {
            concrete = Material.YELLOW_CONCRETE;
            level = "50%";
        } else if (percent >= 25) {
            concrete = Material.ORANGE_CONCRETE;
            level = "25%";
        } else {
            concrete = Material.RED_CONCRETE;
            level = "0%";
        }

        int filled = Math.round(percent / 10f);
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            bar.append(i < filled ? ChatColor.GREEN + "■" : ChatColor.DARK_GRAY + "■");
        }

        List<String> lore = new ArrayList<>();
        lore.add(bar.toString());
        lore.add("");
        lore.add(ChatColor.GRAY + "LPM: §a+1" + ChatColor.GRAY + "   Shift+LPM: §a+10");
        lore.add(ChatColor.GRAY + "PPM: §c-1" + ChatColor.GRAY + "   Shift+PPM: §c-10");
        if (percent == 0) {
            lore.add("");
            lore.add(ChatColor.RED + "Głośność wyłączona");
        }
        return icon(concrete, ChatColor.GOLD + "Głośność: §e" + percent + "%"
                + ChatColor.GRAY + " (" + level + ")", lore);
    }

    /** Lejek — częstotliwość: lista opcji, WYBRANA pogrubiona (nazwa i lista). */
    private ItemStack freqIcon(Player player) {
        String current = music.frequencyOf(player);

        List<String> lore = new ArrayList<>();
        for (int i = 0; i < MusicManager.FREQ_VALUES.length; i++) {
            String label = MusicManager.FREQ_LABELS[i];
            if (MusicManager.FREQ_VALUES[i].equals(current)) {
                lore.add(ChatColor.YELLOW + "§l▶ " + label); // wybrana — pogrubiona
            } else {
                lore.add(ChatColor.GRAY + "  " + label);
            }
        }
        lore.add("");
        lore.add(ChatColor.GRAY + "LPM: §a wybór w górę");
        lore.add(ChatColor.GRAY + "PPM: §c wybór w dół");

        // Wybrana wartość pogrubiona także w nazwie przedmiotu.
        return icon(Material.HOPPER,
                ChatColor.GOLD + "Częstotliwość: §e§l" + freqLabel(current), lore);
    }

    /** Płyta utworu = PAPIER z własnym CustomModelData (6100 + i): bez modelu
     *  wygląda jak zwykły papier (nigdy „missing texture”), a gdy właściciel
     *  dorzuci własną teksturę (PNG + model + definicja), przedmiot ją podbierze
     *  sam — po CMD. Każdy utwór ma WYRĄBNIANY numer CMD, więc tekstury można
     *  robić pojedynczo w dowolnej kolejności. */
    public static final int DISC_CMD_BASE = 6100;
    /** Maksymalna liczba utworów w GUI (rzędy 2–5 kodu, poza rządem przycisków). */
    public static final int MAX_TRACKS = 36;

    /** Płyta utworu: wybrany utwór = ZIELONY + POŁYSK zaklęcia (bez enchantu
     *  w tooltipie — setEnchantmentGlintOverride, wzorzec z CraftingCommand)
     *  + „▶ WYBRANY”; Shift+LPM = losowanie. */
    private ItemStack trackChip(Player player, String track, int index) {
        boolean selected = track.equals(music.getSelectedTrack(player));
        // Krótka nazwa: ostatni człon identyfikatora (raft:music.utwor_1 → utwor_1).
        String shortName = track.contains(".")
                ? track.substring(track.lastIndexOf('.') + 1) : track;

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + track);
        lore.add("");
        lore.add(selected ? ChatColor.GREEN + "§l▶ ZAGRA TERAZ (raz)"
                : ChatColor.GRAY + "Klik: zagra ten utwór teraz (raz)");
        lore.add("");
        lore.add(ChatColor.GRAY + "LPM: §a zagraj raz"
                + ChatColor.GRAY + "   Shift+LPM: §7 losowanie");
        // Marker techniczny: CMD do podpięcia własnej tekstury (Paint → PNG).
        lore.add(ChatColor.DARK_GRAY + "cmd: " + discCmd(index));

        ItemStack item = icon(Material.PAPER,
                (selected ? ChatColor.GREEN + "§l" : ChatColor.GOLD + "") + shortName, lore);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(discCmd(index));
            // Wizualny znacznik wyboru: połysk jak zaklęty, ale ZERO enchantów
            // (nic nie dopisuje się do tooltipa) — tylko wybrany utwór świeci.
            if (selected) {
                meta.setEnchantmentGlintOverride(true);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    /** CMD płyty i-tego utworu (6101, 6102, …) — stałe mapowanie utwór→CMD. */
    private static int discCmd(int index) {
        return DISC_CMD_BASE + 1 + index;
    }

    /** Cugle (lina z pętlą) — zapętlenie: stan widoczny w nazwie i podpowiedzi;
     *  przy „ZAPĘTLONE” nazwa ma poświatę (§l) i zielony kolor. */
    private ItemStack loopIcon(Player player) {
        boolean loop = music.isLoopEnabled(player);

        List<String> lore = new ArrayList<>();
        lore.add(loop ? ChatColor.GREEN + "§l✓ ZAPĘTLONE"
                : ChatColor.DARK_GRAY + "✗ Nie zapętlone");
        lore.add("");
        lore.add(ChatColor.GRAY + "Powtarza utwór w kółko");
        lore.add(ChatColor.GRAY + "(płyta zagra raz, cugla odpowiada za powtarzanie)");
        lore.add("");
        lore.add(ChatColor.YELLOW + "Kliknij, aby " + (loop ? "wyłączyć" : "włączyć"));

        // Stan czytelny od razu z nazwy przedmiotu.
        return icon(Material.LEAD, loop
                ? ChatColor.GREEN + "§lZapętlenie"
                : ChatColor.GOLD + "Zapętlenie", lore);
    }

    /** Nether star „Wyjdź" — zamyka GUI muzyki. */
    private ItemStack exitIcon() {
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Zamyka ustawienia muzyki");
        return icon(Material.NETHER_STAR, ChatColor.RED + "Wyjdź", lore);
    }

    /** Wspólny builder ikon (ChatColor + setDisplayName jak w panelu ustawień). */
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

    /**
     * Holder GUI muzyki — rozpoznaje nasze GUI w InventoryClickEvent
     * bez porównywania tytułów (wzorzec z CraftingCommand).
     */
    public static final class MuzykaHolder implements InventoryHolder {
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
