package pl.vd.commands;

import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import pl.vd.VillagerDefensePlugin;
import pl.vd.arena.Arena;
import pl.vd.player.PlayerData;

import java.util.ArrayList;
import java.util.List;

public class VDCommand implements CommandExecutor, TabCompleter {

    private final VillagerDefensePlugin plugin;

    public VDCommand(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("§7Użyj: /vd <join|leave|create|setlobby|addspawn|addvillager|setend|setminmax|delete|kit|top|stats|list|reload>"));
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "join" -> {
                if (!(sender instanceof Player player)) return true;
                if (args.length < 2) { player.sendMessage(Component.text("§cUżyj: /vd join <arena>")); return true; }
                Arena arena = plugin.getArenaManager().getArena(args[1]);
                if (arena == null) { player.sendMessage(Component.text("§cNie ma takiej areny.")); return true; }
                try {
                    boolean ok = plugin.getGameManager().addPlayer(player, arena);
                    if (!ok) player.sendMessage(Component.text("§cNie udało się dołączyć do gry."));
                } catch (Exception ex) {
                    player.sendMessage(Component.text("§cWystąpił nieoczekiwany błąd przy próbie dołączenia do areny."));
                    plugin.getLogger().severe("Error while adding player to arena: " + ex.getMessage());
                    ex.printStackTrace();
                }
            }
            case "leave" -> {
                if (!(sender instanceof Player player)) return true;
                Arena arena = findArenaOf(player);
                if (arena == null) { player.sendMessage(Component.text("§cNie grasz obecnie na żadnej arenie.")); return true; }
                plugin.getGameManager().removePlayer(player, arena);
                player.sendMessage(Component.text("§7Opuściłeś grę."));
            }
            case "kit" -> {
                if (!(sender instanceof Player player)) return true;
                plugin.getKitManager().openKitSelector(player);
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
                sender.sendMessage(Component.text("§6Areny: §7" + String.join(", ", plugin.getArenaManager().getArenas().keySet())));
            }

            // --- Komendy administracyjne ---
            case "create" -> {
                if (!sender.hasPermission("vd.admin")) return noPerm(sender);
                if (args.length < 2) { sender.sendMessage(Component.text("§cUżyj: /vd create <nazwa>")); return true; }
                if (plugin.getArenaManager().getArena(args[1]) != null) {
                    sender.sendMessage(Component.text("§cArena o tej nazwie już istnieje.")); return true;
                }
                plugin.getArenaManager().createArena(args[1]);
                sender.sendMessage(Component.text("§aUtworzono arenę: " + args[1]));
            }
            case "setlobby" -> {
                if (!sender.hasPermission("vd.admin") || !(sender instanceof Player player)) return noPerm(sender);
                Arena arena = requireArena(sender, args);
                if (arena == null) return true;
                arena.setLobbyLocation(player.getLocation());
                plugin.getArenaManager().save();
                player.sendMessage(Component.text("§aUstawiono lobby dla areny " + arena.getName()));
            }
            case "setend" -> {
                if (!sender.hasPermission("vd.admin") || !(sender instanceof Player player)) return noPerm(sender);
                Arena arena = requireArena(sender, args);
                if (arena == null) return true;
                arena.setEndLocation(player.getLocation());
                plugin.getArenaManager().save();
                player.sendMessage(Component.text("§aUstawiono lokalizację końcową dla areny " + arena.getName()));
            }
            case "addspawn" -> {
                if (!sender.hasPermission("vd.admin") || !(sender instanceof Player player)) return noPerm(sender);
                Arena arena = requireArena(sender, args);
                if (arena == null) return true;
                arena.getPlayerSpawns().add(player.getLocation());
                plugin.getArenaManager().save();
                player.sendMessage(Component.text("§aDodano punkt spawnu gracza (" + arena.getPlayerSpawns().size() + ") dla areny " + arena.getName()));
            }
            case "addvillager" -> {
                if (!sender.hasPermission("vd.admin") || !(sender instanceof Player player)) return noPerm(sender);
                Arena arena = requireArena(sender, args);
                if (arena == null) return true;
                arena.getVillagerSpawns().add(player.getLocation());
                plugin.getArenaManager().save();
                player.sendMessage(Component.text("§aDodano punkt spawnu wieśniaka (" + arena.getVillagerSpawns().size() + ") dla areny " + arena.getName()));
            }
            case "setminmax" -> {
                if (!sender.hasPermission("vd.admin")) return noPerm(sender);
                if (args.length < 4) { sender.sendMessage(Component.text("§cUżyj: /vd setminmax <arena> <min> <max>")); return true; }
                Arena arena = plugin.getArenaManager().getArena(args[1]);
                if (arena == null) { sender.sendMessage(Component.text("§cNie ma takiej areny.")); return true; }
                arena.setMinPlayers(Integer.parseInt(args[2]));
                arena.setMaxPlayers(Integer.parseInt(args[3]));
                plugin.getArenaManager().save();
                sender.sendMessage(Component.text("§aUstawiono min/max graczy dla " + arena.getName()));
            }
            case "delete", "remove", "removearena" -> {
                if (!sender.hasPermission("vd.admin")) return noPerm(sender);
                if (args.length < 2) { sender.sendMessage(Component.text("§cUżyj: /vd delete <arena>")); return true; }
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
                plugin.getShopGUI().loadFromConfig();
                plugin.getArenaManager().load();
                sender.sendMessage(Component.text("§aPrzeładowano konfigurację."));
            }
            default -> sender.sendMessage(Component.text("§cNieznana podkomenda."));
        }
        return true;
    }

    private Arena requireArena(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendMessage(Component.text("§cUżyj: /vd " + args[0] + " <arena>")); return null; }
        Arena arena = plugin.getArenaManager().getArena(args[1]);
        if (arena == null) sender.sendMessage(Component.text("§cNie ma areny o nazwie: " + args[1]));
        return arena;
    }

    private boolean noPerm(CommandSender sender) {
        sender.sendMessage(Component.text("§cBrak uprawnień."));
        return true;
    }

    private Arena findArenaOf(Player player) {
        for (Arena arena : plugin.getArenaManager().getArenas().values()) {
            if (arena.isPlaying(player)) return arena;
        }
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(List.of("join", "leave", "kit", "top", "stats", "list",
                    "create", "setlobby", "setend", "addspawn", "addvillager", "setminmax", "delete", "reload"));
        } else if (args.length == 2) {
            options.addAll(plugin.getArenaManager().getArenas().keySet());
        }        return options;
    }
}
