package pl.vd.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pl.vd.VillagerDefensePlugin;
import pl.vd.arena.Arena;

/**
 * /hub - teleport do lobby areny VD (tylko na arenie)
 * /lobby - teleport do lobby serwera (globalne)
 * /przerwa - głosowanie na przerwę w grze
 */
public class HubCommand implements CommandExecutor {

    private final VillagerDefensePlugin plugin;

    public HubCommand(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(net.kyori.adventure.text.Component.text("§cTa komenda jest tylko dla graczy."));
            return true;
        }

        String cmdName = command.getName().toLowerCase();
        if (cmdName.equals("przerwa")) {
            return handlePause(player, label, args);
        }

        // /hub - tylko na arenie VD, teleport do lobby areny
        if (cmdName.equals("hub")) {
            Arena arena = findArenaOf(player);
            if (arena == null) {
                return true;
            }
            // sprawdź czy jest lobby areny
            org.bukkit.Location arenaLobby = arena.getLobbyLocation();
            if (arenaLobby == null) {
                arenaLobby = arena.getEndLocation();
            }
            if (arenaLobby == null) {
                pl.vd.util.AntiSpam.send(player, "§cBrak lobby dla tej areny.");
                return true;
            }
            player.teleport(arenaLobby);
            pl.vd.util.AntiSpam.send(player, "§aTeleportowano do lobby areny!");
            return true;
        }

        // /lobby - teleport do lobby serwera (globalne)
        if (cmdName.equals("lobby")) {
            // jeśli jest na arenie, opuść ją
            Arena arena = findArenaOf(player);
            if (arena != null) {
                plugin.getGameManager().removePlayer(player, arena);
                pl.vd.util.AntiSpam.send(player, "§7Opuściłeś arenę.");
            }

            org.bukkit.Location lobby = plugin.getLobbyLocation();
            if (lobby == null) {
                pl.vd.util.AntiSpam.send(player, "§cNie ustawiono lobby serwera. Użyj: /def set_lobby_serwera");
                return true;
            }

            player.teleport(lobby);
            pl.vd.util.AntiSpam.send(player, "§aTeleportowano do lobby serwera!");
            // daj kompas do lobby
            plugin.getPlayerListener().giveLobbyCompass(player);
            return true;
        }

        return true;
    }

    private boolean handlePause(Player player, String label, String[] args) {
        Arena arena = findArenaOf(player);
        if (arena == null) {
            player.sendMessage(net.kyori.adventure.text.Component.text("§cNie jesteś na arenie."));
            return true;
        }

        pl.vd.game.PauseManager pm = plugin.getPauseManager();

        if (args.length == 0) {
            if (pm.isPaused(arena)) {
                player.sendMessage(net.kyori.adventure.text.Component.text("§6⏸ §eGra jest spauzowana."));
            } else if (pm.isVoting(arena)) {
                player.sendMessage(net.kyori.adventure.text.Component.text("§6🗳 §eTrwa głosowanie na przerwę. Zagłosuj: §f/przerwa tak §7lub §f/przerwa nie"));
            } else {
                player.sendMessage(net.kyori.adventure.text.Component.text("§6Użycie: §f/" + label + " <czas> §7(1, 5, 10, 20, 30 minut)"));
                player.sendMessage(net.kyori.adventure.text.Component.text("§7Pozostałe przerwy: §f" + pm.remainingPauses(arena) + "§7/§f3"));
            }
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("anuluj") || sub.equals("cancel")) {
            if (args.length >= 2) {
                String subVote = args[1].toLowerCase();
                if (subVote.equals("tak") || subVote.equals("yes") || subVote.equals("y")) {
                    pm.voteCancel(player, arena, true);
                    return true;
                }
                if (subVote.equals("nie") || subVote.equals("no") || subVote.equals("n")) {
                    pm.voteCancel(player, arena, false);
                    return true;
                }
            }
            pm.startCancelVote(player, arena);
            return true;
        }

        if (sub.equals("tak") || sub.equals("yes") || sub.equals("y")) {
            pm.vote(player, arena, true);
            return true;
        }
        if (sub.equals("nie") || sub.equals("no") || sub.equals("n")) {
            pm.vote(player, arena, false);
            return true;
        }

        int duration;
        try {
            duration = Integer.parseInt(sub);
        } catch (NumberFormatException e) {
            player.sendMessage(net.kyori.adventure.text.Component.text("§cPodaj liczbę minut: 1, 5, 10, 20 lub 30."));
            return true;
        }

        int seconds = duration * 60;
        pm.startVote(player, arena, seconds);
        return true;
    }

    private Arena findArenaOf(Player player) {
        for (Arena arena : plugin.getArenaManager().getArenas().values()) {
            if (arena.isPlaying(player)) return arena;
        }
        return null;
    }
}
