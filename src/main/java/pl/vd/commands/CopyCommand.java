package pl.vd.commands;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import pl.vd.VillagerDefensePlugin;
import pl.vd.copy.ClipboardTransformer;

import java.util.ArrayList;
import java.util.List;

public class CopyCommand implements CommandExecutor, TabCompleter {

    private final VillagerDefensePlugin plugin;

    public CopyCommand(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("§cTa komenda działa tylko w grze."));
            return true;
        }
        if (!player.hasPermission("vd.admin")) {
            pl.vd.util.AntiSpam.send(player, "§cBrak uprawnień.");
            return true;
        }
        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        var manager = plugin.getCopyPasteManager();
        String sub = args[0].toLowerCase();

        switch (sub) {
            case "pos1" -> {
                Location loc = targetOrStandingBlock(player);
                manager.setPos1(player, loc);
                pl.vd.util.AntiSpam.send(player, "§aPunkt 1 ustawiony: §7"
                        + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
            }
            case "pos2" -> {
                Location loc = targetOrStandingBlock(player);
                manager.setPos2(player, loc);
                pl.vd.util.AntiSpam.send(player, "§aPunkt 2 ustawiony: §7"
                        + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
            }
            case "copy" -> {
                int count = manager.copy(player);
                if (count < 0) {
                    pl.vd.util.AntiSpam.send(player, "§cUstaw najpierw /copy pos1 i /copy pos2 (w tym samym świecie).");
                } else {
                    pl.vd.util.AntiSpam.send(player, "§aSkopiowano " + count + " bloków. Użyj §f/copy paste§a, aby wkleić.");
                }
            }
            case "paste" -> {
                if (!manager.hasClipboard(player)) {
                    pl.vd.util.AntiSpam.send(player, "§cSchowek jest pusty - najpierw użyj /copy copy.");
                    return true;
                }
                int count = manager.paste(player);
                pl.vd.util.AntiSpam.send(player, "§aWklejono " + count + " bloków (miejsce odniesienia: Twoja pozycja w momencie kopiowania). "
                        + "§7Cofniesz to komendą /brush undo.");
            }
            case "rotate" -> {
                if (!manager.hasClipboard(player)) {
                    pl.vd.util.AntiSpam.send(player, "§cSchowek jest pusty - najpierw użyj /copy copy.");
                    return true;
                }
                int degrees = args.length >= 2 ? parseIntOr(args[1], 90) : 90;
                int quarterTurns = degrees / 90;
                if (degrees % 90 != 0 || quarterTurns == 0) {
                    pl.vd.util.AntiSpam.send(player, "§cUżyj: /copy rotate <90|180|270>");
                    return true;
                }
                manager.rotateClipboard(player, quarterTurns);
                pl.vd.util.AntiSpam.send(player, "§aObrócono schowek o " + degrees + "°.");
            }
            case "mirror" -> {
                if (!manager.hasClipboard(player)) {
                    pl.vd.util.AntiSpam.send(player, "§cSchowek jest pusty - najpierw użyj /copy copy.");
                    return true;
                }
                if (args.length < 2 || (!args[1].equalsIgnoreCase("x") && !args[1].equalsIgnoreCase("z"))) {
                    pl.vd.util.AntiSpam.send(player, "§cUżyj: /copy mirror <x|z>");
                    return true;
                }
                var axis = args[1].equalsIgnoreCase("x")
                        ? ClipboardTransformer.MirrorAxis.X
                        : ClipboardTransformer.MirrorAxis.Z;
                manager.mirrorClipboard(player, axis);
                pl.vd.util.AntiSpam.send(player, "§aOdbito schowek lustrzanie wzdłuż osi " + args[1].toUpperCase() + ".");
            }
            default -> sendHelp(player);
        }
        return true;
    }

    /** Blok na który gracz patrzy (do 100 bloków), a jak nic nie widać - blok pod jego nogami. */
    private Location targetOrStandingBlock(Player player) {
        Block target = player.getTargetBlockExact(100);
        if (target != null) return target.getLocation();
        return player.getLocation().getBlock().getLocation();
    }

    private int parseIntOr(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("§6=== /copy - Kopiuj-Wklej ==="));
        player.sendMessage(Component.text("§f/copy pos1 §7- ustaw pierwszy róg (blok na który patrzysz)"));
        player.sendMessage(Component.text("§f/copy pos2 §7- ustaw drugi róg"));
        player.sendMessage(Component.text("§f/copy copy §7- skopiuj zaznaczony region do schowka"));
        player.sendMessage(Component.text("§f/copy paste §7- wklej schowek względem Twojej obecnej pozycji"));
        player.sendMessage(Component.text("§f/copy rotate <90|180|270> §7- obróć schowek"));
        player.sendMessage(Component.text("§f/copy mirror <x|z> §7- odbij schowek lustrzanie"));
        player.sendMessage(Component.text("§7Wklejenie można cofnąć komendą §f/brush undo"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(List.of("pos1", "pos2", "copy", "paste", "rotate", "mirror"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("rotate")) {
            options.addAll(List.of("90", "180", "270"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("mirror")) {
            options.addAll(List.of("x", "z"));
        }
        return options;
    }
}
