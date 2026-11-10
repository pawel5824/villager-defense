package pl.vd.commands;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import pl.vd.VillagerDefensePlugin;
import pl.vd.arena.Arena;
import pl.vd.player.PlayerData;
import pl.vd.supply.SupplyDropManager;
import pl.vd.world.VoidGenerator;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class VDCommand implements CommandExecutor, TabCompleter {

    private final VillagerDefensePlugin plugin;

    public VDCommand(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // na arenie wszystkie /vd ... są ZABLOKOWANE (gracz może je wpisać mimo braku
        // podpowiedzi na czacie) - komenda po cichu nic nie robi. Wyjście: /hub.
        // Wyjątek: OP może zaplanować restart także stojąc na arenie.
        if (sender instanceof Player player && isOnArena(player)
                && !(args.length >= 1 && args[0].equalsIgnoreCase("restart") && player.hasPermission("vd.admin"))) {
            return true;
        }
        if (args.length == 0) {
            return true; // bez argumentów - nic nie pokazuj (komunikat użycia usunięty)
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "join" -> {
                if (!(sender instanceof Player player)) return true;
                if (args.length < 2) { pl.vd.util.AntiSpam.send(player, "§cUżyj: /" + label + " join <arena>"); return true; }
                Arena arena = plugin.getArenaManager().getArena(args[1]);
                if (arena == null) { pl.vd.util.AntiSpam.send(player, "§cNie ma takiej areny."); return true; }
                try {
                    boolean ok = plugin.getGameManager().addPlayer(player, arena);
                    if (!ok) pl.vd.util.AntiSpam.send(player, "§cNie udało się dołączyć do gry.");
                } catch (Exception ex) {
                    pl.vd.util.AntiSpam.send(player, "§cWystąpił nieoczekiwany błąd przy próbie dołączenia do areny.");
                    plugin.getLogger().severe("Error while adding player to arena: " + ex.getMessage());
                    ex.printStackTrace();
                }
            }
            case "leave" -> {
                if (!(sender instanceof Player player)) return true;
                Arena arena = findArenaOf(player);
                if (arena == null) { pl.vd.util.AntiSpam.send(player, "§cNie grasz obecnie na żadnej arenie."); return true; }
                plugin.getGameManager().removePlayer(player, arena);
                pl.vd.util.AntiSpam.send(player, "§7Opuściłeś grę.");
            }
            case "kit" -> {
                if (!(sender instanceof Player player)) return true;
                boolean onArena = findArenaOf(player) != null;
                if (args.length >= 2 && args[1].equalsIgnoreCase("preview") || !onArena) {
                    // /def kit poza areną: czysty podgląd kitów (GUI 1x9) - wybór
                    // klasy zablokowany, kliknięcia tylko otwierają podgląd przedmiotów.
                    // Wybór kitu możliwy wyłącznie na arenie (lobby/pole).
                    plugin.getKitManager().openKitSelectorPreview(player);
                    if (!onArena) {
                        pl.vd.util.AntiSpam.send(player, "§7Podgląd kitów - wybór klasy możliwy tylko na arenie.");
                    }
                } else {
                    plugin.getKitManager().openKitSelector(player);
                }
            }
            case "kitpreview", "previewkit" -> {
                // Podgląd GUI z kitami dla operatora — kliknięcia nic nie zmieniają
                if (!sender.hasPermission("vd.admin") || !(sender instanceof Player player)) return noPerm(sender);
                plugin.getKitManager().openKitSelectorPreview(player);
            }
            case "top" -> {
                List<PlayerData> top = plugin.getPlayerDataManager().getTop(
                        plugin.getConfig().getInt("leaderboard.top-size", 10));
                sender.sendMessage(Component.text("§6=== TOP Villager Defense ==="));
                int i = 1;
                for (PlayerData data : top) {
                    sender.sendMessage(Component.text("§e" + i + ". §f" + data.getLastKnownName()
                            + " §7- §a" + data.getTotalPoints() + " pkt §7(zabójstw: " + data.getTotalKills() + ")"));
                    i++;
                }
            }
            case "stats" -> {
                if (!(sender instanceof Player player)) return true;
                PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId(), player.getName());
                player.sendMessage(Component.text("§6=== Twoje statystyki ==="));
                player.sendMessage(Component.text("§7Punkty łącznie: §e" + data.getTotalPoints()));
                player.sendMessage(Component.text("§7Zabójstwa łącznie: §e" + data.getTotalKills()));
                player.sendMessage(Component.text("§7Rozegrane gry: §e" + data.getGamesPlayed()));
                player.sendMessage(Component.text("§7Najwyższa runda: §e" + data.getHighestWave()));
            }
            case "list" -> {
                sender.sendMessage(Component.text("§6Areny: §7" + String.join(", ", persistentArenaNames())));
            }
            // --- Komendy administracyjne ---
            case "create" -> {
                if (!sender.hasPermission("vd.admin")) return noPerm(sender);
                if (args.length < 2) { sender.sendMessage(Component.text("§cUżyj: /" + label + " create <nazwa>")); return true; }
                if (plugin.getArenaManager().getArena(args[1]) != null) {
                    sender.sendMessage(Component.text("§cArena o tej nazwie już istnieje.")); return true;
                }
                plugin.getArenaManager().createArena(args[1]);
                sender.sendMessage(Component.text("§aUtworzono arenę: " + args[1]));
            }
            case "setlobby" -> {
                if (!sender.hasPermission("vd.admin") || !(sender instanceof Player player)) return noPerm(sender);
                Arena arena = requireArena(sender, args, label);
                if (arena == null) return true;
                arena.setLobbyLocation(player.getLocation());
                plugin.getArenaManager().save();
                pl.vd.util.AntiSpam.send(player, "§aUstawiono lobby dla areny " + arena.getName());
            }
            case "setend" -> {
                if (!sender.hasPermission("vd.admin") || !(sender instanceof Player player)) return noPerm(sender);
                Arena arena = requireArena(sender, args, label);
                if (arena == null) return true;
                arena.setEndLocation(player.getLocation());
                plugin.getArenaManager().save();
                pl.vd.util.AntiSpam.send(player, "§aUstawiono lokalizację końcową dla areny " + arena.getName());
            }
            case "addplayerspawn", "addspawn" -> {
                // punkty, gdzie teleportują się GRACZE na start rundy
                if (!sender.hasPermission("vd.admin") || !(sender instanceof Player player)) return noPerm(sender);
                Arena arena = requireArena(sender, args, label);
                if (arena == null) return true;
                arena.getPlayerSpawns().add(player.getLocation());
                plugin.getArenaManager().save();
                pl.vd.util.AntiSpam.send(player, "§aDodano punkt spawnu gracza (" + arena.getPlayerSpawns().size() + ") dla areny " + arena.getName());
            }
            case "addvillagerspawn", "addvillager" -> {
                // punkty, gdzie spawnują się WIEŚNIACY (do obrony)
                if (!sender.hasPermission("vd.admin") || !(sender instanceof Player player)) return noPerm(sender);
                Arena arena = requireArena(sender, args, label);
                if (arena == null) return true;
                arena.getVillagerSpawns().add(player.getLocation());
                plugin.getArenaManager().save();
                pl.vd.util.AntiSpam.send(player, "§aDodano punkt spawnu wieśniaka (" + arena.getVillagerSpawns().size() + ") dla areny " + arena.getName());
            }
            case "addzombiespawn", "addmobspawn" -> {
                // Osobne punkty spawnu MOBÓW (zombie itp.) - niezależne od wieśniaków.
                // Jeśli nie zostaną ustawione, moby spawnują się przy wieśniakach (jak wcześniej).
                if (!sender.hasPermission("vd.admin") || !(sender instanceof Player player)) return noPerm(sender);
                Arena arena = requireArena(sender, args, label);
                if (arena == null) return true;
                arena.getMobSpawns().add(player.getLocation());
                plugin.getArenaManager().save();
                pl.vd.util.AntiSpam.send(player, "§aDodano punkt spawnu zombie (" + arena.getMobSpawns().size() + ") dla areny " + arena.getName());
            }
            case "removeplayerspawn" -> {
                // usuwa punkt spawnu gracza: podanym numerem albo najbliższym graczowi
                if (!sender.hasPermission("vd.admin") || !(sender instanceof Player player)) return noPerm(sender);
                Arena arena = requireArena(sender, args, label);
                if (arena == null) return true;
                if (removeSpawnPoint(player, arena.getPlayerSpawns(), args, label, "gracza", arena.getName())) {
                    plugin.getArenaManager().save();
                    pl.vd.util.AntiSpam.send(player, "§aUsunięto punkt spawnu gracza. Zostało: " + arena.getPlayerSpawns().size());
                }
            }
            case "removevillagerspawn" -> {
                if (!sender.hasPermission("vd.admin") || !(sender instanceof Player player)) return noPerm(sender);
                Arena arena = requireArena(sender, args, label);
                if (arena == null) return true;
                if (removeSpawnPoint(player, arena.getVillagerSpawns(), args, label, "wieśniaka", arena.getName())) {
                    plugin.getArenaManager().save();
                    pl.vd.util.AntiSpam.send(player, "§aUsunięto punkt spawnu wieśniaka. Zostało: " + arena.getVillagerSpawns().size());
                }
            }
            case "removezombiespawn" -> {
                if (!sender.hasPermission("vd.admin") || !(sender instanceof Player player)) return noPerm(sender);
                Arena arena = requireArena(sender, args, label);
                if (arena == null) return true;
                if (removeSpawnPoint(player, arena.getMobSpawns(), args, label, "zombie", arena.getName())) {
                    plugin.getArenaManager().save();
                    pl.vd.util.AntiSpam.send(player, "§aUsunięto punkt spawnu zombie. Zostało: " + arena.getMobSpawns().size());
                }
            }
            case "spawninfo", "spawnpoints" -> {
                // pokazuje wszystkie punkty spawnu areny (lobby, end, gracze, wieśniacy, zombie)
                if (!sender.hasPermission("vd.admin")) return noPerm(sender);
                Arena arena = requireArena(sender, args, label);
                if (arena == null) return true;
                sender.sendMessage(Component.text("§6=== Spawny areny §f" + arena.getName() + " §6==="));
                sender.sendMessage(Component.text("§7Lobby: §f" + fmtLoc(arena.getLobbyLocation())));
                sender.sendMessage(Component.text("§7Koniec (po grze): §f" + fmtLoc(arena.getEndLocation())));
                sender.sendMessage(Component.text("§7Min/Max graczy: §f" + arena.getMinPlayers() + "/" + arena.getMaxPlayers()));
                sender.sendMessage(Component.text("§7Poziom trudności: §f" + difficultyName(arena)
                        + " §7(fale do zwycięstwa: §f" + plugin.getGameManager().maxWaves(arena) + ")"));
                sender.sendMessage(Component.text("§ePunkty graczy (" + arena.getPlayerSpawns().size() + "):"));
                listSpawns(sender, arena.getPlayerSpawns());
                sender.sendMessage(Component.text("§ePunkty wieśniaków (" + arena.getVillagerSpawns().size() + "):"));
                listSpawns(sender, arena.getVillagerSpawns());
                sender.sendMessage(Component.text("§ePunkty zombie (" + arena.getMobSpawns().size() + "):"));
                listSpawns(sender, arena.getMobSpawns());
                sender.sendMessage(Component.text("§7Wskazówka: usuwasz numerem, np. /" + label + " removezombiespawn " + arena.getName() + " 2"));
            }
            case "setminmax" -> {
                if (!sender.hasPermission("vd.admin")) return noPerm(sender);
                if (args.length < 4) { sender.sendMessage(Component.text("§cUżyj: /" + label + " setminmax <arena> <min> <max>")); return true; }
                Arena arena = plugin.getArenaManager().getArena(args[1]);
                if (arena == null) { sender.sendMessage(Component.text("§cNie ma takiej areny.")); return true; }
                arena.setMinPlayers(Integer.parseInt(args[2]));
                arena.setMaxPlayers(Integer.parseInt(args[3]));
                plugin.getArenaManager().save();
                sender.sendMessage(Component.text("§aUstawiono min/max graczy dla " + arena.getName()));
            }
            case "instancja", "template", "szablon" -> {
                // /def instancja <arena> on|off - oznacz arenę jako szablon systemu instancji.
                // Gracze dołączający do szablonu trafiają do osobnych kopii mapy (instancji),
                // tworzonych w locie i usuwanych po zakończeniu gry.
                if (!sender.hasPermission("vd.admin")) return noPerm(sender);
                if (args.length < 3) {
                    sender.sendMessage(Component.text("§cUżyj: /" + label + " instancja <arena> on|off"));
                    return true;
                }
                Arena instArena = plugin.getArenaManager().getArena(args[1]);
                if (instArena == null) { sender.sendMessage(Component.text("§cNie ma takiej areny.")); return true; }
                if (instArena.isInstance()) {
                    sender.sendMessage(Component.text("§cTo arena-instancja - szablonem może być tylko zwykła arena."));
                    return true;
                }
                String mode = args[2].toLowerCase();
                boolean on = mode.equals("on") || mode.equals("tak") || mode.equals("wlacz");
                boolean off = mode.equals("off") || mode.equals("nie") || mode.equals("wylacz");
                if (!on && !off) {
                    sender.sendMessage(Component.text("§cUżyj: /" + label + " instancja <arena> on|off"));
                    return true;
                }
                instArena.setTemplate(on);
                plugin.getArenaManager().save();
                if (on) {
                    sender.sendMessage(Component.text("§aArena §e" + instArena.getName()
                            + "§a jest teraz szablonem - każda grupa graczy dostanie własną kopię mapy."));
                } else {
                    sender.sendMessage(Component.text("§7Arena §e" + instArena.getName()
                            + "§7 nie jest już szablonem."));
                }
            }
            case "trudnosc", "difficulty" -> {
                // /def trudnosc <arena> <latwy|normalny|trudny> - poziom trudności areny.
                // Każdy poziom ma WŁASNĄ liczbę fal do zwycięstwa (config difficulties.<poziom>.waves,
                // widoczną na pasku jako "Fala: X/Y"). Można dodać własne poziomy w config.yml.
                if (!sender.hasPermission("vd.admin")) return noPerm(sender);
                if (args.length < 3) {
                    sender.sendMessage(Component.text("§cUżyj: /" + label + " trudnosc <arena> <latwy|normalny|trudny>"));
                    return true;
                }
                Arena arena = plugin.getArenaManager().getArena(args[1]);
                if (arena == null) { sender.sendMessage(Component.text("§cNie ma takiej areny.")); return true; }
                String diff = args[2].toLowerCase();
                ConfigurationSection diffs = plugin.getConfig().getConfigurationSection("difficulties");
                Set<String> diffKeys = diffs != null ? diffs.getKeys(false) : Set.of();
                if (!diffKeys.contains(diff)) {
                    sender.sendMessage(Component.text("§cNie ma takiego poziomu trudności. Dostępne: §e"
                            + String.join("§7, §e", diffKeys)));
                    return true;
                }
                arena.setDifficulty(diff);
                plugin.getArenaManager().save();
                int waves = plugin.getGameManager().maxWaves(arena);
                sender.sendMessage(Component.text("§aUstawiono poziom trudności areny §e" + arena.getName()
                        + "§a na §e" + difficultyName(arena) + "§a (fale do zwycięstwa: §e" + waves + "§a)."));
            }
            case "delete", "remove", "removearena" -> {
                if (!sender.hasPermission("vd.admin")) return noPerm(sender);
                if (args.length < 2) { sender.sendMessage(Component.text("§cUżyj: /" + label + " delete <arena>")); return true; }
                Arena arena = plugin.getArenaManager().getArena(args[1]);
                if (arena == null) { sender.sendMessage(Component.text("§cNie ma takiej areny.")); return true; }
                if (plugin.getArenaManager().removeArena(args[1])) {
                    sender.sendMessage(Component.text("§aUsunięto arenę: " + args[1]));
                } else {
                    sender.sendMessage(Component.text("§cNie udało się usunąć areny: " + args[1]));
                }
            }
            case "reload" -> {
                if (!sender.hasPermission("vd.admin")) return noPerm(sender);
                plugin.reloadConfig();
                pl.vd.util.HealthDisplay.setStyle(plugin.getConfig().getString("health-display-style", "bar"));
                plugin.getShopGUI().loadFromConfig();
                plugin.getArenaManager().load();
                // Ustawienia tratwy (raft-collision + raft-debris) — bez rekompilacji
                if (plugin.getRaftPlugin() != null) plugin.getRaftPlugin().reloadRaftConfig();
                sender.sendMessage(Component.text("§aPrzeładowano konfigurację."));
            }
            case "restart" -> {
                if (!sender.hasPermission("vd.admin")) return noPerm(sender);
                // /def restart anuluj - odwołaj zaplanowany restart
                if (args.length >= 2 && (args[1].equalsIgnoreCase("anuluj") || args[1].equalsIgnoreCase("cancel")
                        || args[1].equalsIgnoreCase("stop"))) {
                    if (plugin.getRestartManager().cancel()) {
                        sender.sendMessage(Component.text("§aRestart anulowany."));
                    } else {
                        sender.sendMessage(Component.text("§7Nie ma zaplanowanego restartu."));
                    }
                    return true;
                }
                int seconds = plugin.getConfig().getInt("restart.default-seconds", 30);
                if (args.length >= 2) {
                    try {
                        seconds = Integer.parseInt(args[1]);
                    } catch (NumberFormatException ignored) {
                    }
                }
                seconds = Math.max(1, Math.min(600, seconds));
                if (!plugin.getRestartManager().schedule(seconds)) {
                    sender.sendMessage(Component.text("§cRestart już jest zaplanowany - anulujesz go komendą /def restart anuluj."));
                    return true;
                }
                // Bez potwierdzenia na czacie — odliczanie widać tylko na ekranie (title).
            }
            case "ustawlobby" -> {
                if (!sender.hasPermission("vd.admin")) return noPerm(sender);
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("§cTa komenda jest tylko dla graczy - stań w miejscu lobby i wpisz /def ustawlobby."));
                    return true;
                }
                org.bukkit.Location loc = player.getLocation();
                String w = loc.getWorld() == null ? "" : loc.getWorld().getName();
                plugin.getConfig().set("lobby.world", w);
                plugin.getConfig().set("lobby.x", loc.getX());
                plugin.getConfig().set("lobby.y", loc.getY());
                plugin.getConfig().set("lobby.z", loc.getZ());
                plugin.getConfig().set("lobby.yaw", (double) loc.getYaw());
                plugin.getConfig().set("lobby.pitch", (double) loc.getPitch());
                plugin.saveConfig();
                sender.sendMessage(Component.text("§aLobby ustawione: §f" + w + " §7(" + (int) loc.getX()
                        + ", " + (int) loc.getY() + ", " + (int) loc.getZ() + ")§a. Przetestuj komendą /lobby."));
            }
            case "zrzut_drop", "zrzutdrop", "supplydrop" -> {
                // Wymusza pojawienie się Zrzutu Zaopatrzenia na arenie
                if (!sender.hasPermission("vd.admin")) return noPerm(sender);
                Arena arena = null;
                if (args.length >= 2 && !args[1].startsWith("~")) {
                    arena = plugin.getArenaManager().getArena(args[1]);
                    if (arena == null) { sender.sendMessage(Component.text("§cNie ma areny o nazwie: " + args[1])); return true; }
                } else if (sender instanceof Player player) {
                    arena = findArenaOf(player);
                }
                if (arena == null) {
                    if (!plugin.getArenaManager().getArenas().isEmpty()) {
                        arena = plugin.getArenaManager().getArenas().values().iterator().next();
                    }
                }
                if (arena == null) { sender.sendMessage(Component.text("§cUżyj: /" + label + " zrzut_drop [arena] [x y z]")); return true; }

                // sprawdź czy podano kordynaty (args[2]..args[4])
                if (sender instanceof Player player && args.length >= 5) {
                    try {
                        double cx = parseCoord(args[2], player.getLocation().getBlockX());
                        double cy = parseCoord(args[3], player.getLocation().getBlockY());
                        double cz = parseCoord(args[4], player.getLocation().getBlockZ());
                        org.bukkit.Location target = new org.bukkit.Location(player.getWorld(), cx, cy, cz);
                        SupplyDropManager.DropResult result = plugin.getSupplyDropManager().forceDropAt(arena, target);
                        switch (result) {
                            case SUCCESS -> sender.sendMessage(Component.text("§aZrzut zaopatrzenia leci na §f" + (int)cx + ", " + (int)cy + ", " + (int)cz + "§a!"));
                            case NO_WORLD -> sender.sendMessage(Component.text("§cNie znaleziono świata areny."));
                            case NO_LANDING_SPOT -> sender.sendMessage(Component.text("§cBłąd zrzutu."));
                        }
                    } catch (NumberFormatException e) {
                        sender.sendMessage(Component.text("§cBłędne kordynaty. Podaj liczby: x y z"));
                    }
                    return true;
                }

                // bez kordynatów — normalny zrzut w losowe miejsce
                SupplyDropManager.DropResult result = plugin.getSupplyDropManager().forceDrop(arena);
                switch (result) {
                    case SUCCESS -> sender.sendMessage(Component.text("§aZrzut zaopatrzenia pojawił się na arenie " + arena.getName()));
                    case NO_WORLD -> sender.sendMessage(Component.text("§cNie udało się wywołać zrzutu: nie znaleziono świata areny."));
                    case NO_LANDING_SPOT -> sender.sendMessage(Component.text("§cNie udało się wywołać zrzutu: brak miejsca lądowania."));
                }
            }
            case "miejsce_zrzutu", "dropzone" -> {
                // Wyznaczanie NIEkwadratowego terenu zrzutu (wielokąt) + DZIURY (obszary,
                // w których zrzut NIE ląduje) - WŁASNE dla KAŻDEJ areny, zapis w arenas.yml
                // (globalny config.yml to tylko fallback, gdy arena nie ma własnego terenu):
                //   /def miejsce_zrzutu [arena] dodaj [x z] | usun | czysc | lista | podglad
                //   /def miejsce_zrzutu [arena] dziura nowa | dodaj [x z] | usun [numer] | czysc | lista | podglad [numer]
                // bez nazwy areny = arena, na której stoisz/grasz (z konsoli trzeba podać nazwę)
                if (!sender.hasPermission("vd.admin")) return noPerm(sender);
                SupplyDropManager drops = plugin.getSupplyDropManager();
                if (args.length < 2) {
                    sender.sendMessage(Component.text("§7Użyj: /" + label + " miejsce_zrzutu [arena] <dodaj|usun|czysc|lista|podglad|dziura>"));
                    return true;
                }
                // ustalenie areny: jawna nazwa (args[1]) ALBO arena, na której gra wysyłający
                Arena zoneArena = plugin.getArenaManager().getArena(args[1]);
                int subIdx = 1;
                if (zoneArena == null) {
                    if (sender instanceof Player zonePlayer) zoneArena = findArenaOf(zonePlayer);
                    if (zoneArena == null) {
                        sender.sendMessage(Component.text("§cPodaj nazwę areny albo wejdź na arenę. Użyj: /" + label
                                + " miejsce_zrzutu <arena> <dodaj|usun|czysc|lista|podglad|dziura>"));
                        return true;
                    }
                } else {
                    subIdx = 2;
                }
                if (args.length < subIdx + 1) {
                    sender.sendMessage(Component.text("§7Użyj: /" + label + " miejsce_zrzutu [" + zoneArena.getName()
                            + "] <dodaj|usun|czysc|lista|podglad|dziura>"));
                    return true;
                }
                if (isHoleWord(args[subIdx])) {
                    if (args.length < subIdx + 2) {
                        sender.sendMessage(Component.text("§7Użyj: /" + label + " miejsce_zrzutu [" + zoneArena.getName()
                                + "] dziura <nowa|dodaj|usun|czysc|lista|podglad>"));
                        return true;
                    }
                    switch (args[subIdx + 1].toLowerCase()) {
                        case "nowa", "new", "nowy" -> { return dropHoleNew(sender, drops, zoneArena); }
                        case "dodaj", "add", "punkt" -> { return dropHoleAdd(sender, label, args, drops, zoneArena, subIdx + 2); }
                        case "usun", "remove", "cofnij" -> { return dropHoleRemove(sender, label, args, drops, zoneArena, subIdx + 2); }
                        case "czysc", "clear", "wyczysc" -> { return dropHoleClear(sender, drops, zoneArena); }
                        case "lista", "list" -> { return dropHoleList(sender, label, drops, zoneArena); }
                        case "podglad", "podgląd", "preview", "pokaz" -> { return dropHolePreview(sender, label, args, drops, zoneArena, subIdx + 2); }
                        default -> sender.sendMessage(Component.text("§cNieznana podkomenda. Użyj: /" + label + " miejsce_zrzutu ["
                                + zoneArena.getName() + "] dziura <nowa|dodaj|usun|czysc|lista|podglad>"));
                    }
                    return true;
                }
                switch (args[subIdx].toLowerCase()) {
                    case "dodaj", "add", "punkt" -> { return dropVertexAdd(sender, label, args, drops, zoneArena, subIdx + 1); }
                    case "usun", "remove", "cofnij" -> { return dropVertexRemove(sender, drops, zoneArena); }
                    case "czysc", "clear", "wyczysc" -> { return dropVertexClear(sender, drops, zoneArena); }
                    case "lista", "list" -> { return dropVertexList(sender, label, drops, zoneArena); }
                    case "podglad", "podgląd", "preview", "pokaz" -> { return dropVertexPreview(sender, drops, zoneArena); }
                    default -> sender.sendMessage(Component.text("§cNieznana podkomenda. Użyj: /" + label + " miejsce_zrzutu ["
                            + zoneArena.getName() + "] <dodaj|usun|czysc|lista|podglad|dziura>"));
                }
                return true;
            }
            case "shop_arena", "shoparena" -> {
                // Otwiera sklep areny z DOWOLNEGO miejsca na serwerze, bez podawania nazwy:
                // sklep areny, na której operator gra, a jeśli nie gra - pierwszej wczytanej.
                if (!sender.hasPermission("vd.admin") || !(sender instanceof Player player)) return noPerm(sender);
                Arena arena = findArenaOf(player);
                if (arena == null && !plugin.getArenaManager().getArenas().isEmpty()) {
                    arena = plugin.getArenaManager().getArenas().values().iterator().next();
                }
                if (arena == null) {
                    pl.vd.util.AntiSpam.send(player, "§cNie ma żadnej wczytanej areny.");
                    return true;
                }
                plugin.getShopGUI().setPreviewArena(player.getUniqueId(), arena);
                plugin.getShopGUI().openMain(player, arena);
                pl.vd.util.AntiSpam.send(player, "§aOtwieram sklep areny §f" + arena.getName() + " §7(podgląd)");
            }
            case "create_world", "createworld" -> {
                if (!sender.hasPermission("vd.admin")) return noPerm(sender);
                if (args.length < 2) { sender.sendMessage(Component.text("§cUżyj: /" + label + " create_world <nazwa>")); return true; }
                String worldName = args[1];

                if (Bukkit.getWorld(worldName) != null) {
                    sender.sendMessage(Component.text("§cŚwiat o tej nazwie już jest wczytany. Użyj /" + label + " tpworld " + worldName));
                    return true;
                }

                // Zabezpieczenie: jeśli folder świata już istnieje na dysku (np. z poprzedniej
                // sesji serwera), nie generujemy go od nowa (i tak by to nic nie dało, bo
                // WorldCreator#createWorld() wczyta istniejące dane i zignoruje generator) -
                // zamiast tego po prostu go wczytujemy i informujemy o tym.
                if (worldFolderExists(worldName)) {
                    sender.sendMessage(Component.text("§eŚwiat \"" + worldName + "\" już istnieje na dysku - wczytuję go zamiast tworzyć nowy."));
                    World existing = loadExistingWorld(worldName);
                    if (existing == null) {
                        sender.sendMessage(Component.text("§cNie udało się wczytać istniejącego świata."));
                        return true;
                    }
                    sender.sendMessage(Component.text("§aŚwiat \"" + worldName + "\" wczytany."));
                    if (sender instanceof Player player) {
                        player.teleport(existing.getSpawnLocation());
                        pl.vd.util.AntiSpam.send(player, "§7Teleportowano do wczytanego świata.");
                    }
                    return true;
                }

                sender.sendMessage(Component.text("§7Tworzę pusty świat \"" + worldName + "\"... to może chwilę potrwać."));

                World world = new WorldCreator(worldName)
                        .generator(new VoidGenerator())
                        .environment(World.Environment.NORMAL)
                        .createWorld();

                if (world == null) {
                    sender.sendMessage(Component.text("§cNie udało się utworzyć świata."));
                    return true;
                }

                world.setSpawnLocation(0, 100, 0);
                world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
                world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
                world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
                world.setTime(6000);

                sender.sendMessage(Component.text("§aŚwiat \"" + worldName + "\" utworzony (pusty, bez terenu)."));
                if (sender instanceof Player player) {
                    player.teleport(world.getSpawnLocation());
                    pl.vd.util.AntiSpam.send(player, "§7Teleportowano do nowego świata.");
                }
            }
            case "delete_world" -> {
                if (!sender.hasPermission("vd.admin") || !(sender instanceof Player player)) return noPerm(sender);
                if (args.length < 2) { pl.vd.util.AntiSpam.send(player, "§cUżyj: /" + label + " delete_world <nazwa>"); return true; }
                String worldName = args[1];

                // zabezpieczenie: nie usuwaj głównego świata
                if (worldName.equalsIgnoreCase("world") || worldName.equalsIgnoreCase("world_nether") || worldName.equalsIgnoreCase("world_the_end")) {
                    pl.vd.util.AntiSpam.send(player, "§cNie można usunąć głównego świata serwera!");
                    return true;
                }

                World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    // świat nie jest wczytany — sprawdź czy folder istnieje na dysku
                    if (worldFolderExists(worldName)) {
                        // folder istnieje ale świat nie jest wczytany — usuń folder
                        File container = Bukkit.getWorldContainer();
                        File worldFolder = new File(container, worldName);
                        if (deleteDirectory(worldFolder)) {
                            pl.vd.util.AntiSpam.send(player, "§aUsunięto folder świata \"" + worldName + "\".");
                        } else {
                            pl.vd.util.AntiSpam.send(player, "§cNie udało się usunąć folderu świata.");
                        }
                    } else {
                        pl.vd.util.AntiSpam.send(player, "§cNie ma takiego świata (folder ani wczytany świat). Folder: worldName");
                    }
                    return true;
                }

                // świat jest wczytany — wylij wszystkich graczy do lobby
                org.bukkit.Location lobby = plugin.getLobbyLocation();
                for (org.bukkit.entity.Entity entity : world.getEntities()) {
                    if (entity instanceof Player p) {
                        if (lobby != null) {
                            p.teleport(lobby);
                        } else {
                            p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
                        }
                    }
                }

                // wyładuj świat (bez zapisywania)
                boolean unloaded = Bukkit.unloadWorld(world, false);
                if (!unloaded) {
                    pl.vd.util.AntiSpam.send(player, "§cNie udało się wyładować świata.");
                    return true;
                }

                // usuń folder
                File container = Bukkit.getWorldContainer();
                File worldFolder = new File(container, worldName);
                if (deleteDirectory(worldFolder)) {
                    pl.vd.util.AntiSpam.send(player, "§aŚwiat \"" + worldName + "\" usunięty.");
                } else {
                    pl.vd.util.AntiSpam.send(player, "§eŚwiat wyładowany, ale nie udało się usunąć folderu. Usuń ręcznie.");
                }
            }
            case "tparena" -> {
                if (!sender.hasPermission("vd.admin") || !(sender instanceof Player player)) return noPerm(sender);
                if (args.length < 2) { pl.vd.util.AntiSpam.send(player, "§cUżyj: /" + label + " tparena <arena>"); return true; }
                Arena arena = plugin.getArenaManager().getArena(args[1]);
                if (arena == null) {
                    pl.vd.util.AntiSpam.send(player, "§cNie ma takiej areny. Lista: §f"
                            + String.join(", ", plugin.getArenaManager().getArenas().keySet()));
                    return true;
                }
                Location dest = arena.getLobbyLocation() != null ? arena.getLobbyLocation() : arena.getEndLocation();
                if (dest == null || dest.getWorld() == null) {
                    pl.vd.util.AntiSpam.send(player, "§cArena \"" + args[1] + "\" nie ma ustawionej lokalizacji (lobby/end).");
                    return true;
                }
                player.teleport(dest);
                pl.vd.util.AntiSpam.send(player, "§aTeleportowano do areny: " + args[1]);
            }
            case "tpworld" -> {
                if (!sender.hasPermission("vd.admin") || !(sender instanceof Player player)) return noPerm(sender);
                if (args.length < 2) { pl.vd.util.AntiSpam.send(player, "§cUżyj: /" + label + " tpworld <nazwa>"); return true; }
                String worldName = args[1];

                World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    // Świat nie jest aktualnie wczytany do pamięci - sprawdź, czy istnieje
                    // na dysku (np. po restarcie serwera) i jeśli tak, wczytaj go automatycznie.
                    if (worldFolderExists(worldName)) {
                        pl.vd.util.AntiSpam.send(player, "§7Świat \"" + worldName + "\" nie był wczytany - wczytuję go teraz...");
                        world = loadExistingWorld(worldName);
                    }
                }

                if (world == null) {
                    // Jeśli to nie świat, ale arena o tej nazwie - teleportuj do areny (lobby albo end).
                    Arena arena = plugin.getArenaManager().getArena(worldName);
                    if (arena != null) {
                        Location dest = arena.getLobbyLocation() != null ? arena.getLobbyLocation() : arena.getEndLocation();
                        if (dest != null && dest.getWorld() != null) {
                            player.teleport(dest);
                            pl.vd.util.AntiSpam.send(player, "§aTo nie świat, ale arena - teleportowano do areny: " + worldName);
                            return true;
                        }
                    }
                    File container = Bukkit.getWorldContainer();
                    File target = new File(container, worldName);
                    String cleanPath;
                    try {
                        cleanPath = target.getCanonicalPath();
                    } catch (java.io.IOException e) {
                        cleanPath = target.getAbsolutePath();
                    }
                    pl.vd.util.AntiSpam.send(player, "§cNie ma takiego świata. Szukałem folderu: §7" + cleanPath);
                    pl.vd.util.AntiSpam.send(player, "§cSprawdź, czy świat istnieje jako folder w głównym folderze serwera");
                    pl.vd.util.AntiSpam.send(player, "§7lub jako dimensja wewnątrz innego świata (world/dimensions/...).");
                    pl.vd.util.AntiSpam.send(player, "§7Lista wczytanych światów: §f" + String.join(", ", Bukkit.getWorlds().stream().map(World::getName).toList()));
                    pl.vd.util.AntiSpam.send(player, "§7Jeśli chodziło Ci o arenę: §f/" + label + " tparena <nazwa>");
                    return true;
                }

                player.teleport(world.getSpawnLocation());
            }
            case "animacja_zrzutu" -> {
                // /def animacja_zrzutu <nazwa> [x y z] - test animacji znikania zrzutu:
                // stawia blok zrzutu i po 5 s odtwarza wybraną animację (usunie blok)
                if (!sender.hasPermission("vd.admin") || !(sender instanceof Player player)) return noPerm(sender);
                if (args.length < 2) {
                    pl.vd.util.AntiSpam.send(player, "§cPodaj nazwę animacji. Dostępne: §f"
                            + String.join("§7, §f", SupplyDropManager.despawnAnimationNames()));
                    return true;
                }
                String anim = args[1].toLowerCase();
                if (!SupplyDropManager.isValidDespawnAnimation(anim)) {
                    pl.vd.util.AntiSpam.send(player, "§cNieznana animacja '§f" + anim
                            + "§c'. Dostępne: §f" + String.join("§7, §f", SupplyDropManager.despawnAnimationNames()));
                    return true;
                }
                // cel: podane współrzędne [x y z] albo blok, na który patrzysz
                // (blok zrzutu staje NA nim - na wierzchu bloku docelowego)
                Location target;
                if (args.length >= 5) {
                    try {
                        target = new Location(player.getWorld(),
                                Integer.parseInt(args[2]), Integer.parseInt(args[3]), Integer.parseInt(args[4]));
                    } catch (NumberFormatException e) {
                        pl.vd.util.AntiSpam.send(player, "§cNieprawidłowe współrzędne - podaj liczby całkowite: x y z.");
                        return true;
                    }
                } else {
                    Block looked = player.getTargetBlockExact(100);
                    if (looked == null) {
                        pl.vd.util.AntiSpam.send(player, "§cNie patrzysz na żaden blok.");
                        return true;
                    }
                    // współrzędne bloku nad patrzonym blokiem (LICZBY CAŁKOWITE - bez +0.5,
                    // bo animacje same liczą środek komórki; +0.5/+0.5 tutaj dawałoby skrzynię
                    // przesuniętą o pół bloka po skosie)
                    target = looked.getLocation().add(0, 1, 0);
                }
                target.getBlock().setType(Material.WAXED_EXPOSED_COPPER_CHEST, false);
                pl.vd.util.AntiSpam.send(player, "§aPostawiono blok zrzutu - animacja '§f" + anim
                        + "§a' uruchomi się za 5 sekund.");
                Location finalTarget = target.clone();
                plugin.getServer().getScheduler().runTaskLater(plugin,
                        () -> plugin.getSupplyDropManager().playDespawnAnimation(anim,
                                finalTarget.getWorld(), finalTarget), 100L);
            }
            case "bariera", "barrier" -> handleBarrier(sender, label, args);
            case "ogien_staly", "firepermanent" -> handlePermanentFire(sender, label, args);
            case "set_lobby_serwera" -> {
                if (!sender.hasPermission("vd.admin") || !(sender instanceof Player player)) return noPerm(sender);
                Location loc = player.getLocation();
                plugin.getConfig().set("lobby.world", loc.getWorld().getName());
                plugin.getConfig().set("lobby.x", loc.getX());
                plugin.getConfig().set("lobby.y", loc.getY());
                plugin.getConfig().set("lobby.z", loc.getZ());
                plugin.getConfig().set("lobby.yaw", (double) loc.getYaw());
                plugin.getConfig().set("lobby.pitch", (double) loc.getPitch());
                plugin.saveConfig();
                pl.vd.util.AntiSpam.send(player, "§aLobby serwera ustawione!§7 (" + loc.getWorld().getName()
                        + " " + (int)loc.getX() + " " + (int)loc.getY() + " " + (int)loc.getZ() + ")");
            }
            case "lobby" -> handleLobby(sender, label, args);
            default -> sender.sendMessage(Component.text("§cNieznana podkomenda."));
        }
        return true;
    }

    // ====================== /def bariera ======================

    private void handleBarrier(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("§cTa komenda jest tylko dla graczy."));
            return;
        }
        if (args.length < 2) {
            sendBarrierUsage(player, label);
            return;
        }

        pl.vd.barrier.BarrierManager bm = plugin.getBarrierManager();
        String sub = args[1].toLowerCase();

        switch (sub) {
            case "set1" -> {
                // zaznacz róg 1 (pozycja gracza)
                Location loc = player.getLocation();
                barrierPos1.put(player.getUniqueId(), loc.clone());
                pl.vd.util.AntiSpam.send(player, "§aZaznaczono róg 1: §f"
                        + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
            }
            case "set2" -> {
                // zaznacz róg 2 (pozycja gracza)
                Location loc = player.getLocation();
                barrierPos2.put(player.getUniqueId(), loc.clone());
                pl.vd.util.AntiSpam.send(player, "§aZaznaczono róg 2: §f"
                        + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
            }
            case "save" -> {
                // zapisz barierę z zaznaczonych rogów
                if (args.length < 3) {
                    pl.vd.util.AntiSpam.send(player, "§cUżyj: /" + label + " bariera save <nazwa>");
                    return;
                }
                Location p1 = barrierPos1.get(player.getUniqueId());
                Location p2 = barrierPos2.get(player.getUniqueId());
                if (p1 == null || p2 == null) {
                    pl.vd.util.AntiSpam.send(player, "§cNajpierw zaznacz oba rogi: /" + label + " bariera set1 i set2");
                    return;
                }
                if (!p1.getWorld().equals(p2.getWorld())) {
                    pl.vd.util.AntiSpam.send(player, "§cOba rogi muszą być w tym samym świecie!");
                    return;
                }
                String name = args[2];
                bm.addZone(name, p1.getWorld().getName(),
                        p1.getBlockX(), p1.getBlockY(), p1.getBlockZ(),
                        p2.getBlockX(), p2.getBlockY(), p2.getBlockZ());
                pl.vd.util.AntiSpam.send(player, "§aZapisano barierę §f" + name + "§a!");
                barrierPos1.remove(player.getUniqueId());
                barrierPos2.remove(player.getUniqueId());
            }
            case "list" -> {
                var zones = bm.getZones();
                if (zones.isEmpty()) {
                    pl.vd.util.AntiSpam.send(player, "§7Brak barier.");
                    return;
                }
                pl.vd.util.AntiSpam.send(player, "§6=== Bariery (" + zones.size() + ") ===");
                for (var entry : zones.entrySet()) {
                    pl.vd.util.AntiSpam.send(player, "  §f" + entry.getKey() + " §7- " + entry.getValue().description());
                }
            }
            case "usun", "remove", "del" -> {
                if (args.length < 3) {
                    pl.vd.util.AntiSpam.send(player, "§cUżyj: /" + label + " bariera usun <nazwa>");
                    return;
                }
                boolean removed = bm.removeZone(args[2]);
                if (removed) {
                    pl.vd.util.AntiSpam.send(player, "§aUsunięto barierę §f" + args[2] + "§a.");
                } else {
                    pl.vd.util.AntiSpam.send(player, "§cNie znaleziono bariery §f" + args[2] + "§c.");
                }
            }
            case "info" -> {
                if (args.length < 3) {
                    pl.vd.util.AntiSpam.send(player, "§cUżyj: /" + label + " bariera info <nazwa>");
                    return;
                }
                pl.vd.barrier.BarrierManager.BarrierZone zone = bm.getZone(args[2]);
                if (zone == null) {
                    pl.vd.util.AntiSpam.send(player, "§cNie znaleziono bariery §f" + args[2] + "§c.");
                    return;
                }
                pl.vd.util.AntiSpam.send(player, "§6Bariera §f" + args[2]);
                pl.vd.util.AntiSpam.send(player, "  " + zone.description());
            }
            default -> sendBarrierUsage(player, label);
        }
    }

    /** Pozycje rogów zapisane per gracz (tymczasowe, do /def bariera set1/set2). */
    private final java.util.Map<java.util.UUID, Location> barrierPos1 = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, Location> barrierPos2 = new java.util.HashMap<>();

    private void sendBarrierUsage(Player player, String label) {
        pl.vd.util.AntiSpam.send(player, "§6Użycie: /" + label + " bariera <podkomenda>");
        pl.vd.util.AntiSpam.send(player, "  §fset1 §7— zaznacz róg 1 (pozycja gracza)");
        pl.vd.util.AntiSpam.send(player, "  §fset2 §7— zaznacz róg 2 (pozycja gracza)");
        pl.vd.util.AntiSpam.send(player, "  §fsave <nazwa> §7— zapisz barierę");
        pl.vd.util.AntiSpam.send(player, "  §flista §7— pokaż wszystkie bariery");
        pl.vd.util.AntiSpam.send(player, "  §fusun <nazwa> §7— usuń barierę");
        pl.vd.util.AntiSpam.send(player, "  §finfo <nazwa> §7— szczegóły bariery");
    }

    // ====================== /def ogien_staly ======================

    private void handlePermanentFire(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("§cTa komenda jest tylko dla graczy."));
            return;
        }

        pl.vd.listeners.PermanentFireListener pfl = plugin.getPermanentFireListener();

        if (args.length < 2) {
            // pokaż status
            boolean enabled = pfl.isEnabled(player.getWorld());
            pl.vd.util.AntiSpam.send(player, enabled
                    ? "§6Ogień stały: §aWŁĄCZONY §7w tym świecie"
                    : "§6Ogień stały: §cWYŁĄCZONY §7w tym świecie");
            pl.vd.util.AntiSpam.send(player, "§7Użycie: /" + label + " ogien_staly on|off");
            return;
        }

        String sub = args[1].toLowerCase();
        if (sub.equals("on") || sub.equals("wlacz") || sub.equals("1")) {
            boolean was = pfl.isEnabled(player.getWorld());
            if (!was) pfl.toggle(player.getWorld());
            pl.vd.util.AntiSpam.send(player, "§aOgień stały §aWŁĄCZONY §7w świecie §f" + player.getWorld().getName() + "§7.");
        } else if (sub.equals("off") || sub.equals("wylacz") || sub.equals("0")) {
            boolean was = pfl.isEnabled(player.getWorld());
            if (was) pfl.toggle(player.getWorld());
            pl.vd.util.AntiSpam.send(player, "§cOgień stały §cWYŁĄCZONY §7w świecie §f" + player.getWorld().getName() + "§7.");
        } else {
            pl.vd.util.AntiSpam.send(player, "§cUżyj: /" + label + " ogien_staly on|off");
        }
    }

    /**
     * Zwraca faktyczną nazwę folderu świata na dysku pasującą do podanej nazwy
     * (bez względu na wielkość liter), albo null jeśli taki folder nie istnieje.
     * Uznajemy folder za "istniejący świat" jeśli zawiera level.dat LUB folder region
     * (na wypadek gdyby level.dat nie zdążył się zapisać przy awaryjnym wyłączeniu serwera).
     */
    private String findWorldFolderName(String worldName) {
        File container = Bukkit.getWorldContainer();
        File exact = new File(container, worldName);
        if (isValidWorldFolder(exact)) return exact.getName();

        File[] children = container.listFiles();
        if (children != null) {
            for (File f : children) {
                if (f.getName().equalsIgnoreCase(worldName) && isValidWorldFolder(f)) {
                    return f.getName();
                }
            }
        }

        // Nowy format Paper (1.21.5+ / 26.x): dodatkowe światy są przechowywane
        // jako dimensje wewnątrz innego świata, np. <world>/dimensions/minecraft/<nazwa>.
        // Uwaga: World#getWorldFolder() wskazuje w tym formacie folder dimensji
        // (np. world/dimensions/minecraft/overworld), więc szukamy folderu z "dimensions".
        // Zwracamy nazwę świata - WorldCreator sam odnajdzie właściwe dane.
        for (World w : Bukkit.getWorlds()) {
            File dimsRoot = pl.vd.util.WorldUtil.findDimensionsRoot(w);
            if (dimsRoot == null) continue;
            File dims = new File(dimsRoot, "dimensions");
            File[] namespaces = dims.listFiles();
            if (namespaces == null) continue;
            for (File ns : namespaces) {
                if (!ns.isDirectory()) continue;
                File[] dimsInNs = ns.listFiles();
                if (dimsInNs == null) continue;
                for (File d : dimsInNs) {
                    if (d.isDirectory() && d.getName().equalsIgnoreCase(worldName) && isValidWorldFolder(d)) {
                        return d.getName();
                    }
                }
            }
        }
        return null;
    }

    private boolean isValidWorldFolder(File folder) {
        if (!folder.isDirectory()) return false;
        return new File(folder, "level.dat").exists()
                || new File(folder, "region").isDirectory()
                || new File(folder, "data").isDirectory(); // dimensje nowego formatu mają folder data/
    }

    private boolean worldFolderExists(String worldName) {
        return findWorldFolderName(worldName) != null;
    }

    /** Rekurencyjnie usuwa folder i jego zawartość. */
    private boolean deleteDirectory(File dir) {
        if (dir == null || !dir.exists()) return true;
        if (dir.isDirectory()) {
            File[] children = dir.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (!deleteDirectory(child)) return false;
                }
            }
        }
        return dir.delete();
    }

    /**
     * Wczytuje do pamięci świat, który już fizycznie istnieje na dysku (np. utworzony
     * we wcześniejszej sesji serwera). Używa dokładnie takiej samej ścieżki jak
     * /def createworld (z generatorem), bo na niektórych wersjach Paper tworzenie
     * świata bez generatora zwraca null dla światów zapisanych jako dimensje.
     * Gdy dane już istnieją, generator jest i tak ignorowany.
     */
    private World loadExistingWorld(String worldName) {
        String realName = findWorldFolderName(worldName);
        if (realName == null) return null;
        try {
            return new WorldCreator(realName)
                    .generator(new VoidGenerator())
                    .environment(World.Environment.NORMAL)
                    .createWorld();
        } catch (Exception ex) {
            plugin.getLogger().severe("Nie udało się wczytać świata " + worldName + ": " + ex.getMessage());
            ex.printStackTrace();
            return null;
        }
    }

    private Arena requireArena(CommandSender sender, String[] args, String label) {
        if (args.length < 2) { sender.sendMessage(Component.text("§cUżyj: /" + label + " " + args[0] + " <arena>")); return null; }
        Arena arena = plugin.getArenaManager().getArena(args[1]);
        if (arena == null) sender.sendMessage(Component.text("§cNie ma areny o nazwie: " + args[1]));
        return arena;
    }

    /** Parsuje współrzędną: liczba albo ~offset (względna do gracza). */
    private double parseCoord(String input, int playerCoord) {
        if (input.startsWith("~")) {
            String offset = input.substring(1);
            if (offset.isEmpty()) return playerCoord;
            return playerCoord + Double.parseDouble(offset);
        }
        return Double.parseDouble(input);
    }

    private boolean noPerm(CommandSender sender) {
        sender.sendMessage(Component.text("§cBrak uprawnień."));
        return true;
    }

    /** Nazwa poziomu trudności areny (w języku z Lang; własne poziomy = surowy klucz z config.yml). */
    private String difficultyName(Arena arena) {
        String diff = arena.getDifficulty();
        return switch (diff) {
            case "latwy" -> pl.vd.util.Lang.getPl("trudnosc-latwy");
            case "normalny" -> pl.vd.util.Lang.getPl("trudnosc-normalny");
            case "trudny" -> pl.vd.util.Lang.getPl("trudnosc-trudny");
            default -> diff;
        };
    }

    private Arena findArenaOf(Player player) {
        for (Arena arena : plugin.getArenaManager().getArenas().values()) {
            if (arena.isPlaying(player)) return arena;
        }
        return null;
    }

    /**
     * Usuwa jeden punkt spawnu: podanym numerem (args[2]) albo najbliższym graczowi.
     * Wysyła komunikaty o błędach. Zwraca true, gdy punkt został usunięty.
     */
    private boolean removeSpawnPoint(Player player, List<Location> points, String[] args, String label, String what, String arenaName) {
        if (points.isEmpty()) {
            pl.vd.util.AntiSpam.send(player, "§cArena " + arenaName + " nie ma punktów spawnu " + what + ".");
            return false;
        }
        int index = -1;
        if (args.length >= 3) {
            try {
                index = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                pl.vd.util.AntiSpam.send(player, "§cUżyj: /" + label + " " + args[0] + " <arena> [numer]");
                return false;
            }
            if (index < 0 || index >= points.size()) {
                pl.vd.util.AntiSpam.send(player, "§cNie ma punktu o numerze " + index + " (jest " + points.size()
                        + "). Sprawdź /" + label + " spawninfo " + arenaName);
                return false;
            }
            points.remove(index);
            return true;
        }
        // bez numeru: usuń najbliższy punkt w świecie gracza
        Location loc = player.getLocation();
        int best = -1;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < points.size(); i++) {
            Location p = points.get(i);
            if (p == null || p.getWorld() == null || !p.getWorld().equals(loc.getWorld())) continue;
            double d = p.distanceSquared(loc);
            if (d < bestDist) { bestDist = d; best = i; }
        }
        if (best < 0) {
            pl.vd.util.AntiSpam.send(player, "§cBrak punktów w Twoim świecie - podaj numer (patrz /" + label
                    + " spawninfo " + arenaName + ")");
            return false;
        }
        points.remove(best);
        return true;
    }

    /** Wypisuje punkty spawnu z numerami do czatu. */
    private void listSpawns(CommandSender sender, List<Location> points) {
        if (points.isEmpty()) {
            sender.sendMessage(Component.text("  §7brak"));
            return;
        }
        int i = 0;
        for (Location p : points) {
            sender.sendMessage(Component.text("  §f" + i + ". §7"
                    + (p.getWorld() != null ? p.getWorld().getName() : "?")
                    + " §fx=" + (int) p.getX() + " y=" + (int) p.getY() + " z=" + (int) p.getZ()));
            i++;
        }
    }

    private String fmtLoc(Location loc) {
        if (loc == null || loc.getWorld() == null) return "§7brak";
        return "§f" + loc.getWorld().getName() + " §fx=" + (int) loc.getX() + " y=" + (int) loc.getY() + " z=" + (int) loc.getZ();
    }



    /** Czy gracz jest aktualnie na jakiejś arenie (czeka lub gra). */
    private boolean isOnArena(Player player) {
        for (Arena arena : plugin.getArenaManager().getArenas().values()) {
            if (arena.isPlaying(player)) return true;
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(List.of("join", "leave", "kit", "kitpreview", "top", "stats", "list",
                    "create", "setlobby", "setend", "addplayerspawn", "addvillagerspawn", "addzombiespawn",
                    "removeplayerspawn", "removevillagerspawn", "removezombiespawn", "spawninfo", "setminmax",
                    "instancja", "trudnosc", "delete", "reload", "restart", "ustawlobby",
                    "create_world", "delete_world", "tpworld", "tparena", "shop_arena", "zrzutdrop", "supplydrop",
                    "miejsce_zrzutu", "dropzone", "animacja_zrzutu", "bariera", "ogien_staly", "zrzut_drop", "set_lobby_serwera", "lobby"));
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("kit")) {
                options.add("preview"); // /def kit preview - podgląd bez wyboru
            } else if (sub.equals("tpworld") || sub.equals("create_world") || sub.equals("createworld") || sub.equals("delete_world")) {
                options.addAll(listAvailableWorlds());
            } else if (sub.equals("animacja_zrzutu")) {
                options.addAll(SupplyDropManager.despawnAnimationNames());
            } else if (sub.equals("miejsce_zrzutu") || sub.equals("dropzone")) {
                // podkomendy + nazwy aren (można wyznaczać teren konkretnej areny z dowolnego miejsca)
                options.addAll(List.of("dodaj", "usun", "czysc", "lista", "podglad", "dziura"));
                options.addAll(persistentArenaNames());
            } else if (sub.equals("instancja") || sub.equals("template") || sub.equals("szablon")) {
                options.addAll(List.of("on", "off"));
            } else if (sub.equals("restart")) {
                options.add("3"); // TAB = szybki restart na 3 s (podpowiedź na pierwszym miejscu)
                options.add("anuluj");
            } else if (sub.equals("bariera") || sub.equals("barrier")) {
                options.addAll(List.of("set1", "set2", "save", "list", "usun", "info"));
            } else if (sub.equals("ogien_staly") || sub.equals("firepermanent")) {
                options.addAll(List.of("on", "off"));
            } else if (sub.equals("lobby")) {
                options.addAll(List.of("spawn", "npc"));
            } else if (args.length == 3 && (args[0].equalsIgnoreCase("bariera") || args[0].equalsIgnoreCase("barrier"))) {
                String sub2 = args[1].toLowerCase();
                if (sub2.equals("usun") || sub2.equals("remove") || sub2.equals("del") || sub2.equals("info")) {
                    options.addAll(plugin.getBarrierManager().getZones().keySet());
                }
            } else {
                options.addAll(persistentArenaNames());
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("lobby") && args[1].equalsIgnoreCase("npc")) {
            options.addAll(List.of("spawn", "usun", "lista"));
        } else if (args.length == 4 && args[0].equalsIgnoreCase("lobby") && args[1].equalsIgnoreCase("npc")
                && (args[2].equalsIgnoreCase("usun") || args[2].equalsIgnoreCase("del"))) {
            // podpowiedź nazw istniejących NPC
            options.addAll(plugin.getLobbyNPCManager().getKeys());
        } else if (args.length == 5 && args[0].equalsIgnoreCase("lobby") && args[1].equalsIgnoreCase("npc")
                && args[2].equalsIgnoreCase("spawn")) {
            // podpowiedź trybów gry
            options.addAll(List.of("villagerdefense", "raft"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("trudnosc")) {
            // podpowiedź poziomów trudności z config.yml (difficulties)
            ConfigurationSection diffs = plugin.getConfig().getConfigurationSection("difficulties");
            if (diffs != null) options.addAll(diffs.getKeys(false));
        } else if (args.length == 3 && (args[0].equalsIgnoreCase("miejsce_zrzutu") || args[0].equalsIgnoreCase("dropzone"))) {
            if (isHoleWord(args[1])) {
                options.addAll(List.of("nowa", "dodaj", "usun", "czysc", "lista", "podglad"));
            } else if (plugin.getArenaManager().getArena(args[1]) != null) {
                options.addAll(List.of("dodaj", "usun", "czysc", "lista", "podglad", "dziura"));
            }
        } else if (args.length == 4 && (args[0].equalsIgnoreCase("miejsce_zrzutu") || args[0].equalsIgnoreCase("dropzone"))) {
            if (isHoleWord(args[1]) && isHoleNumberWord(args[2])) {
                int holes = holeCountForCompletion(sender, null);
                for (int i = 1; i <= holes; i++) options.add(String.valueOf(i));
            } else if (plugin.getArenaManager().getArena(args[1]) != null && isHoleWord(args[2])) {
                options.addAll(List.of("nowa", "dodaj", "usun", "czysc", "lista", "podglad"));
            }
        } else if (args.length == 5 && (args[0].equalsIgnoreCase("miejsce_zrzutu") || args[0].equalsIgnoreCase("dropzone"))
                && plugin.getArenaManager().getArena(args[1]) != null
                && isHoleWord(args[2]) && isHoleNumberWord(args[3])) {
            int holes = holeCountForCompletion(sender, args[1]);
            for (int i = 1; i <= holes; i++) options.add(String.valueOf(i));
        } else if (args.length >= 3 && args.length <= 5 && args[0].equalsIgnoreCase("animacja_zrzutu")) {
            // podpowiedź współrzędnych bloku, na który patrzysz (x, wierzch Y, z)
            String[] coords = targetCoords(sender);
            if (coords != null) {
                String coord = coords[args.length - 3];
                String partial = args[args.length - 1];
                if (partial == null || partial.isEmpty() || coord.startsWith(partial)) {
                    options.add(coord);
                }
            }
        }
        return options;
    }

    // ===================== /def miejsce_zrzutu [arena] - wielokąt terenu =====================

    private boolean dropVertexAdd(CommandSender sender, String label, String[] args, SupplyDropManager drops, Arena arena, int coordIdx) {
        int x, z;
        if (args.length >= coordIdx + 2) {
            try {
                x = Integer.parseInt(args[coordIdx]);
                z = Integer.parseInt(args[coordIdx + 1]);
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("§cPodaj liczby: /" + label + " miejsce_zrzutu " + arena.getName() + " dodaj <x> <z> (albo bez nich = miejsce, w którym stoisz)."));
                return true;
            }
        } else {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("§cUżyj: /" + label + " miejsce_zrzutu " + arena.getName() + " dodaj <x> <z> (z konsoli trzeba podać koordynaty)."));
                return true;
            }
            x = player.getLocation().getBlockX();
            z = player.getLocation().getBlockZ();
        }
        drops.addZonePolygonVertex(arena, x, z);
        int n = arena.getDropPolygon().size();
        sender.sendMessage(Component.text("§a[Arena §e" + arena.getName() + "§a] Dodano wierzchołek " + n + " wielokąta: §e" + x + " " + z
                + "§a. Min. 3 punkty (obecnie " + n + "). Podgląd: /" + label + " miejsce_zrzutu " + arena.getName() + " podglad"));
        return true;
    }

    private boolean dropVertexRemove(CommandSender sender, SupplyDropManager drops, Arena arena) {
        if (!drops.removeLastZonePolygonVertex(arena)) {
            sender.sendMessage(Component.text("§7Brak punktów do usunięcia."));
            return true;
        }
        sender.sendMessage(Component.text("§7Usunięto ostatni wierzchołek wielokąta areny §e" + arena.getName()
                + "§7. Zostało: §e" + arena.getDropPolygon().size()));
        return true;
    }

    private boolean dropVertexClear(CommandSender sender, SupplyDropManager drops, Arena arena) {
        drops.clearZonePolygon(arena);
        sender.sendMessage(Component.text("§7Wyczyszczono wielokąt areny §e" + arena.getName()
                + "§7 - zrzut znów losuje w prostokącie min/max z configu."));
        return true;
    }

    private boolean dropVertexList(CommandSender sender, String label, SupplyDropManager drops, Arena arena) {
        List<int[]> poly = arena.getDropPolygon();
        if (poly.isEmpty()) {
            sender.sendMessage(Component.text("§7Arena §e" + arena.getName() + "§7 nie ma własnego wielokąta - używa globalnego"
                    + " z config.yml (albo prostokąta min/max). Dodaj punkty: /" + label + " miejsce_zrzutu " + arena.getName() + " dodaj"));
        } else {
            StringBuilder sb = new StringBuilder("§eWielokąt zrzutu areny §f" + arena.getName() + "§e (§f" + poly.size() + "§e):");
            for (int[] p : poly) sb.append(" §f[").append(p[0]).append(", ").append(p[1]).append("]");
            sender.sendMessage(Component.text(sb.toString()));
            if (poly.size() < 3) {
                sender.sendMessage(Component.text("§7Min. 3 punkty - dodaj jeszcze " + (3 - poly.size()) + "."));
            }
        }
        return true;
    }

    private boolean dropVertexPreview(CommandSender sender, SupplyDropManager drops, Arena arena) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("§cPodgląd partiklesów wymaga bycia w grze (wysokość chmury = wysokość gracza)."));
            return true;
        }
        List<int[]> poly = arena.getDropPolygon();
        if (poly.isEmpty()) {
            sender.sendMessage(Component.text("§7Arena §e" + arena.getName() + "§7 nie ma własnego wielokąta - nie ma czego pokazać."));
            return true;
        }
        drops.showPolygonParticles(player, poly, false);
        sender.sendMessage(Component.text("§aBiała chmura (wielokąt areny §e" + arena.getName() + "§a, " + poly.size()
                + " pkt) - 20 s na Twojej wysokości: §e" + player.getLocation().getBlockY()));
        return true;
    }

    // ===================== /def miejsce_zrzutu [arena] dziura - WIELE wykluczonych obszarów =====================

    private boolean dropHoleNew(CommandSender sender, SupplyDropManager drops, Arena arena) {
        drops.startNewZoneHole(arena);
        int n = arena.getDropHoles().size();
        sender.sendMessage(Component.text("§a[Arena §e" + arena.getName() + "§a] Nowa dziura nr §e" + n
                + "§a. Dodawaj wierzchołki dookoła obszaru: /def miejsce_zrzutu " + arena.getName() + " dziura dodaj (min. 3 pkt)."));
        return true;
    }

    private boolean dropHoleAdd(CommandSender sender, String label, String[] args, SupplyDropManager drops, Arena arena, int coordIdx) {
        int x, z;
        if (args.length >= coordIdx + 2) {
            try {
                x = Integer.parseInt(args[coordIdx]);
                z = Integer.parseInt(args[coordIdx + 1]);
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("§cPodaj liczby: /" + label + " miejsce_zrzutu " + arena.getName() + " dziura dodaj <x> <z> (albo bez nich = miejsce, w którym stoisz)."));
                return true;
            }
        } else {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("§cUżyj: /" + label + " miejsce_zrzutu " + arena.getName() + " dziura dodaj <x> <z> (z konsoli trzeba podać koordynaty)."));
                return true;
            }
            x = player.getLocation().getBlockX();
            z = player.getLocation().getBlockZ();
        }
        int idx = drops.addZoneHoleVertex(arena, x, z);
        int size = drops.zoneHoleAt(arena, idx).size();
        sender.sendMessage(Component.text("§a[Arena §e" + arena.getName() + "§a] Dodano wierzchołek " + size + " do dziury nr §e" + (idx + 1)
                + "§a: §e" + x + " " + z + "§a. Min. 3 punkty. Lista: /" + label + " miejsce_zrzutu " + arena.getName() + " dziura lista"));
        return true;
    }

    private boolean dropHoleRemove(CommandSender sender, String label, String[] args, SupplyDropManager drops, Arena arena, int numIdx) {
        if (args.length >= numIdx + 1) {
            try {
                int n = Integer.parseInt(args[numIdx]);
                if (drops.removeZoneHole(arena, n - 1)) {
                    sender.sendMessage(Component.text("§7[Arena §e" + arena.getName() + "§7] Usunięto całą dziurę nr §e" + n
                            + "§7. Zostało dziur: §e" + arena.getDropHoles().size()));
                } else {
                    sender.sendMessage(Component.text("§cBrak dziury nr " + n + " na arenie " + arena.getName()
                            + ". Lista: /" + label + " miejsce_zrzutu " + arena.getName() + " dziura lista"));
                }
                return true;
            } catch (NumberFormatException ignored) {
                sender.sendMessage(Component.text("§cPodaj numer dziury: /" + label + " miejsce_zrzutu " + arena.getName() + " dziura usun <numer>"));
                return true;
            }
        }
        if (!drops.removeLastZoneHoleVertex(arena)) {
            sender.sendMessage(Component.text("§7Brak punktów do usunięcia (dziury areny " + arena.getName() + " są puste)."));
            return true;
        }
        sender.sendMessage(Component.text("§7Usunięto ostatni wierzchołek ostatniej dziury areny §e" + arena.getName()
                + "§7. Lista: /" + label + " miejsce_zrzutu " + arena.getName() + " dziura lista"));
        return true;
    }

    private boolean dropHoleClear(CommandSender sender, SupplyDropManager drops, Arena arena) {
        drops.clearZoneHoles(arena);
        sender.sendMessage(Component.text("§7Usunięto wszystkie dziury areny §e" + arena.getName()
                + "§7 - zrzut może znowu lądować w tych obszarach."));
        return true;
    }

    private boolean dropHoleList(CommandSender sender, String label, SupplyDropManager drops, Arena arena) {
        List<List<int[]>> holes = arena.getDropHoles();
        if (holes.isEmpty()) {
            sender.sendMessage(Component.text("§7Arena §e" + arena.getName() + "§7 nie ma własnych dziur. Zacznij nową: /" + label
                    + " miejsce_zrzutu " + arena.getName() + " dziura nowa, potem dodaj wierzchołki dookoła obszaru (min. 3)."));
            return true;
        }
        sender.sendMessage(Component.text("§eDziury zrzutu areny §f" + arena.getName() + "§e (§f" + holes.size() + "§e):"));
        int i = 1;
        for (List<int[]> h : holes) {
            StringBuilder sb = new StringBuilder(" §fDziura " + i + "§e (§f" + h.size() + " pkt" + (h.size() < 3 ? " - NIEAKTYWNA" : "") + "§e):");
            for (int[] p : h) sb.append(" §7[").append(p[0]).append(", ").append(p[1]).append("]");
            sender.sendMessage(Component.text(sb.toString()));
            i++;
        }
        return true;
    }

    private boolean dropHolePreview(CommandSender sender, String label, String[] args, SupplyDropManager drops, Arena arena, int numIdx) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("§cPodgląd partiklesów wymaga bycia w grze (wysokość chmury = wysokość gracza)."));
            return true;
        }
        int idx;
        if (args.length >= numIdx + 1) {
            try {
                idx = Integer.parseInt(args[numIdx]) - 1;
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("§cPodaj numer dziury: /" + label + " miejsce_zrzutu " + arena.getName() + " dziura podglad <numer>"));
                return true;
            }
        } else {
            idx = arena.getDropHoles().size() - 1;
        }
        List<int[]> hole = drops.zoneHoleAt(arena, idx);
        if (hole.isEmpty()) {
            sender.sendMessage(Component.text("§cNie ma takiej dziury na arenie " + arena.getName()
                    + ". Lista: /" + label + " miejsce_zrzutu " + arena.getName() + " dziura lista"));
            return true;
        }
        drops.showPolygonParticles(player, hole, true);
        sender.sendMessage(Component.text("§cCzerwona chmura (dziura nr " + (idx + 1) + " areny §e" + arena.getName() + "§c, " + hole.size()
                + " pkt) - 20 s na Twojej wysokości: §e" + player.getLocation().getBlockY()));
        return true;
    }

    /** Czy słowo oznacza „dziurę" (wykluczony obszar)? */
    private boolean isHoleWord(String s) {
        return s != null && (s.equalsIgnoreCase("dziura") || s.equalsIgnoreCase("wyklucz")
                || s.equalsIgnoreCase("exclude") || s.equalsIgnoreCase("przeszkoda"));
    }

    /** Czy słowo to operacja na NUMERZE dziury (usun/podglad)? */
    private boolean isHoleNumberWord(String s) {
        return s != null && (s.equalsIgnoreCase("usun") || s.equalsIgnoreCase("remove") || s.equalsIgnoreCase("cofnij")
                || s.equalsIgnoreCase("podglad") || s.equalsIgnoreCase("podgląd")
                || s.equalsIgnoreCase("preview") || s.equalsIgnoreCase("pokaz"));
    }

    /** Liczba dziur areny do podpowiedzi (jawna nazwa albo arena gracza). */
    private int holeCountForCompletion(CommandSender sender, String namedArena) {
        if (namedArena != null) {
            Arena a = plugin.getArenaManager().getArena(namedArena);
            if (a != null) return a.getDropHoles().size();
        }
        if (sender instanceof Player p) {
            Arena a = findArenaOf(p);
            if (a != null) return a.getDropHoles().size();
        }
        return 0;
    }

    /** Współrzędne bloku, na który patrzy gracz (X, wierzch bloku Y, Z). */
    private String[] targetCoords(CommandSender sender) {
        if (!(sender instanceof Player player)) return null;
        Block block = player.getTargetBlockExact(100);
        if (block == null) return null;
        return new String[]{
                String.valueOf(block.getX()),
                String.valueOf(block.getY() + 1),
                String.valueOf(block.getZ())
        };
    }

    /** Nazwy TRWAŁYCH aren (bez tymczasowych instancji) - do list i podpowiedzi. */
    private List<String> persistentArenaNames() {
        List<String> out = new ArrayList<>();
        for (Arena a : plugin.getArenaManager().getArenas().values()) {
            if (!a.isInstance()) out.add(a.getName());
        }
        return out;
    }

    // ====================== /def lobby npc ======================

    private void handleLobby(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("§cTa komenda jest tylko dla graczy."));
            return;
        }
        if (args.length < 2) {
            pl.vd.util.AntiSpam.send(player, "§cUżyj: /" + label + " lobby npc <spawn|usun|lista|tp> [nazwa] [tryb] [skora] "
                    + "| §e/" + label + " lobby spawn");
            return;
        }
        String sub = args[1].toLowerCase();

        switch (sub) {
            case "spawn", "tp" -> {
                // teleport do lobby
                org.bukkit.World lobbyWorld = player.getWorld();
                player.teleport(lobbyWorld.getSpawnLocation());
                pl.vd.util.AntiSpam.send(player, "§aTeleportowano do lobby.");
            }
            case "npc" -> handleLobbyNPC(player, label, args);
            default -> pl.vd.util.AntiSpam.send(player, "§cUżyj: /" + label + " lobby npc <spawn|usun|lista> [nazwa] [tryb] [skora]");
        }
    }

    private void handleLobbyNPC(Player player, String label, String[] args) {
        if (args.length < 3) {
            pl.vd.util.AntiSpam.send(player, "§cUżyj: /" + label + " lobby npc <spawn|usun|lista> [nazwa] [tryb] [skora]"
                    + "\n§7Tryby: §evillagerdefense§7, §eraft"
                    + "\n§7Skóra: base64 tekstury lub nick gracza");
            return;
        }
        String action = args[2].toLowerCase();
        pl.vd.lobby.LobbyNPCManager mgr = plugin.getLobbyNPCManager();

        switch (action) {
            case "spawn", "dodaj", "add" -> {
                if (args.length < 4) {
                    pl.vd.util.AntiSpam.send(player, "§cUżyj: /" + label + " lobby npc spawn <nazwa> [tryb] [skora]" );
                    return;
                }
                String npcName = args[3];
                String gameMode = args.length >= 5 ? args[4] : null;
                String skin = args.length >= 6 ? args[5] : null;
                // jeśli skin to nick gracza → pobierz teksturę
                if (skin != null && !skin.startsWith("eyJ")) {
                    // nick gracza → pobierz texture z Mojang API
                    try {
                        com.destroystokyo.paper.profile.PlayerProfile pp =
                                Bukkit.createProfile(skin);
                        pp.completeFromCache();
                        // nie mamy tu bezpośredniego dostępu do textures, zapiszemy nick
                        // i resolver w LobbyNPC.spawn() — na razie zapisz nick
                        skin = "player:" + skin;
                    } catch (Exception ignored) {}
                }
                pl.vd.lobby.LobbyNPC npc = new pl.vd.lobby.LobbyNPC(
                        npcName, player.getLocation(), skin, gameMode);
                mgr.addNPC(npcName.toLowerCase(), npc);
                pl.vd.util.AntiSpam.send(player, "§aNPC §e" + npcName + " §azostal utworzony!"
                        + (gameMode != null ? " §7Tryb: §e" + gameMode : ""));
            }
            case "usun", "del", "remove" -> {
                if (args.length < 4) {
                    pl.vd.util.AntiSpam.send(player, "§cUżyj: /" + label + " lobby npc usun <nazwa>");
                    return;
                }
                String key = args[3].toLowerCase();
                if (mgr.removeNPC(key)) {
                    pl.vd.util.AntiSpam.send(player, "§aNPC §e" + key + " §ausuniety.");
                } else {
                    pl.vd.util.AntiSpam.send(player, "§cNie znaleziono NPC o nazwie: §e" + key);
                }
            }
            case "lista", "list" -> {
                var keys = mgr.getKeys();
                if (keys.isEmpty()) {
                    pl.vd.util.AntiSpam.send(player, "§7Brak NPC w lobby.");
                } else {
                    StringBuilder sb = new StringBuilder("§6NPC w lobby:\n");
                    for (String key : keys) {
                        pl.vd.lobby.LobbyNPC n = mgr.getNPC(key);
                        sb.append("§7- §e").append(key)
                          .append(" §7[tryb: §f").append(n.getGameMode() != null ? n.getGameMode() : "brak")
                          .append("§7]");
                    }
                    player.sendMessage(Component.text(sb.toString()));
                }
            }
            default -> pl.vd.util.AntiSpam.send(player, "§cUżyj: /" + label + " lobby npc <spawn|usun|lista>");
        }
    }

    /**
     * Nazwy światów do podpowiedzi: wczytane do pamięci + istniejące na dysku
     * (foldery na poziomie serwera oraz dimensje w nowym formacie Paper).
     * Pomija wbudowane nazwy dimensji (overworld/the_nether/the_end), żeby nie
     * proponować nazw, których tpworld nie potrafi poprawnie wczytać.
     */
    private List<String> listAvailableWorlds() {
        List<String> builtin = List.of("overworld", "the_nether", "the_end");
        List<String> out = new ArrayList<>();
        for (World w : Bukkit.getWorlds()) out.add(w.getName());

        File container = Bukkit.getWorldContainer();
        File[] children = container.listFiles();
        if (children != null) {
            for (File f : children) {
                if (f.isDirectory() && isValidWorldFolder(f) && !out.contains(f.getName())) {
                    out.add(f.getName());
                }
            }
        }

        for (World w : Bukkit.getWorlds()) {
            File dimsRoot = pl.vd.util.WorldUtil.findDimensionsRoot(w);
            if (dimsRoot == null) continue;
            File dims = new File(dimsRoot, "dimensions");
            File[] namespaces = dims.listFiles();
            if (namespaces == null) continue;
            for (File ns : namespaces) {
                if (!ns.isDirectory()) continue;
                File[] worlds = ns.listFiles();
                if (worlds == null) continue;
                for (File f : worlds) {
                    String name = f.getName();
                    if (!f.isDirectory() || builtin.contains(name) || out.contains(name)) continue;
                    if (isValidWorldFolder(f)) out.add(name);
                }
            }
        }
        return out.stream().sorted().toList();
    }
}
