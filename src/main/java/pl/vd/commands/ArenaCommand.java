package pl.vd.commands;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import pl.vd.VillagerDefensePlugin;
import pl.vd.arena.Arena;

import java.util.ArrayList;
import java.util.List;

/**
 * Komenda /arena - komendy dostępne WYŁĄCZNIE dla operatora (uprawnienie vd.admin)
 * i przeznaczone do używania NA arenie.
 *
 * /arena addpoints <liczba> [gracz] - dodaje punkty sesji (te za zabijanie mobów,
 * do wydania w sklepie) do testów bez zmieniania cen. Bez podanego gracza punkty
 * dostaje operator, który musi grać wtedy na jakiejś arenie.
 */
public class ArenaCommand implements CommandExecutor, TabCompleter {

    private final VillagerDefensePlugin plugin;

    public ArenaCommand(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("vd.admin")) {
            sender.sendMessage(Component.text("§cBrak uprawnień."));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(Component.text("§7Użyj: /arena addpoints <liczba> [gracz]"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "addpoints", "addpoint", "punkty" -> addPoints(sender, args);
            default -> sender.sendMessage(Component.text("§cNieznana podkomenda. Użyj: /arena addpoints <liczba> [gracz]"));
        }
        return true;
    }

    /**
     * Dodaje punkty sesji graczowi grającemu na arenie (bez podanego gracza - operatorowi).
     * Punkty sesji są z bieżącej gry, więc gracz musi być w danej chwili na arenie.
     */
    private void addPoints(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("§cUżyj: /arena addpoints <liczba> [gracz]"));
            return;
        }
        int amount;
        try {
            amount = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("§cTo nie jest liczba: " + args[1]));
            return;
        }
        Player target;
        if (args.length >= 3) {
            target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                sender.sendMessage(Component.text("§cNie ma gracza online: " + args[2]));
                return;
            }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage(Component.text("§cPodaj gracza: /arena addpoints <liczba> <gracz>"));
            return;
        }
        Arena arena = findArenaOf(target);
        if (arena == null) {
            sender.sendMessage(Component.text("§c" + target.getName() + " nie gra teraz na żadnej arenie "
                    + "(punkty sesji są z bieżącej gry - dołącz najpierw do areny)."));
            return;
        }
        arena.addSessionPoints(target.getUniqueId(), amount);
        pl.vd.util.AntiSpam.send(target, (amount >= 0 ? "§a+" : "§c") + amount + " pkt §7(admin)");
        sender.sendMessage(Component.text("§aDodano §e" + amount + " §apkt dla §f" + target.getName()
                + " §7(ma teraz: §e" + arena.getSessionPoints(target.getUniqueId()) + "§7)."));
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
            options.add("addpoints");
        } else if (args.length == 2 && isAddPoints(args[0])) {
            options.addAll(List.of("10", "50", "100", "500", "1000"));
        } else if (args.length == 3 && isAddPoints(args[0])) {
            for (Player p : Bukkit.getOnlinePlayers()) options.add(p.getName());
        }
        return options;
    }

    private boolean isAddPoints(String sub) {
        String s = sub.toLowerCase();
        return s.equals("addpoints") || s.equals("addpoint") || s.equals("punkty");
    }
}
