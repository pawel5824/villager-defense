package pl.vd.raft.latarenka;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import pl.vd.VillagerDefensePlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Komenda /latarenka postaw <nazwa> — stawia customową latarenkę w świecie Raft.
 * Działa TYLKO w świecie Raft.
 */
public class LatarenkaCommand implements CommandExecutor, TabCompleter {

    private final VillagerDefensePlugin plugin;
    private final LatarenkaManager manager;

    public LatarenkaCommand(VillagerDefensePlugin plugin, LatarenkaManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cTa komenda jest dostępna tylko dla graczy!");
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

        String sub = args[0].toLowerCase();

        return switch (sub) {
            case "postaw" -> handlePostaw(player, args);
            case "daj" -> handleDaj(player, args);
            case "list", "lista" -> handleList(player);
            case "usun", "remove" -> handleRemove(player);
            case "cleanup", "czysc" -> handleCleanup(player);
            case "help" -> { sendHelp(player); yield true; }
            default -> {
                player.sendMessage("§cNieznana komenda! Użyj /" + label + " help");
                yield true;
            }
        };
    }

    /**
     * /latarenka postaw <nazwa> — stawia latarenkę na bloku na który patrzysz.
     * Działa TYLKO w świecie Raft.
     */
    private boolean handlePostaw(Player player, String[] args) {
        // Sprawdź czy gracz jest w świecie Raft
        if (!isRaftWorld(player)) {
            pl.vd.util.AntiSpam.send(player, "§cTa komenda działa tylko w świecie Raft!");
            return true;
        }

        if (args.length < 2) {
            pl.vd.util.AntiSpam.send(player, "§cUżyj: /latarenka postaw <nazwa>");
            pl.vd.util.AntiSpam.send(player, "§7Dostępne presety: §e" + String.join(", ", manager.listPresets()));
            return true;
        }

        String presetName = args[1];

        // Sprawdź czy preset istnieje
        if (manager.loadPreset(presetName) == null) {
            pl.vd.util.AntiSpam.send(player, "§cNie ma presetu latarenki: §e" + presetName);
            pl.vd.util.AntiSpam.send(player, "§7Dostępne presety: §e" + String.join(", ", manager.listPresets()));
            return true;
        }

        // Postaw latarenkę
        boolean ok = manager.placeLatarenka(presetName, player);
        if (ok) {
            pl.vd.util.AntiSpam.send(player, "§aPostawiono latarenkę: §e" + manager.getDisplayName(presetName));
        } else {
            pl.vd.util.AntiSpam.send(player, "§cNie udało się postawić latarenki.");
        }
        return true;
    }

    /**
     * /latarenka daj <nazwa> — daje do ekwipunku armor_stand z nazwą presetu.
     * Prawy klik = postawienie latarenki.
     */
    private boolean handleDaj(Player player, String[] args) {
        if (args.length < 2) {
            pl.vd.util.AntiSpam.send(player, "§cUżyj: /latarenka daj <nazwa>");
            pl.vd.util.AntiSpam.send(player, "§7Dostępne presety: §e" + String.join(", ", manager.listPresets()));
            return true;
        }

        String presetName = args[1];
        if (manager.loadPreset(presetName) == null) {
            pl.vd.util.AntiSpam.send(player, "§cNie ma presetu latarenki: §e" + presetName);
            pl.vd.util.AntiSpam.send(player, "§7Dostępne presety: §e" + String.join(", ", manager.listPresets()));
            return true;
        }

        org.bukkit.inventory.ItemStack item = manager.createLatarenkaItem(presetName);
        if (item == null) {
            pl.vd.util.AntiSpam.send(player, "§cNie udało się stworzyć przedmiotu.");
            return true;
        }

        // Dodaj do ekwipunku (lub wyrzuć jeśli pełny)
        java.util.HashMap<Integer, org.bukkit.inventory.ItemStack> leftover = player.getInventory().addItem(item);
        if (!leftover.isEmpty()) {
            // Ekwipunek pełny — wyrzuć na ziemię
            player.getWorld().dropItemNaturally(player.getLocation(), leftover.get(0));
            pl.vd.util.AntiSpam.send(player, "§eEkwipunek pełny! Latarenka wyrzucona na ziemię.");
        } else {
            pl.vd.util.AntiSpam.send(player, "§aDostałeś latarenkę: §e" + manager.getDisplayName(presetName) + " §7(PPM = postaw)");
        }
        return true;
    }

    /** /latarenka lista — lista dostępnych presetów. */
    private boolean handleList(Player player) {
        List<String> presets = manager.listPresets();
        if (presets.isEmpty()) {
            pl.vd.util.AntiSpam.send(player, "§7Brak presetów latarenek w folderze latarenki/");
            return true;
        }
        player.sendMessage("§6=== Dostępne latarenki ===");
        for (String name : presets) {
            player.sendMessage("§e- §f" + name + " §7(" + manager.getDisplayName(name) + "§7)");
        }
        return true;
    }

    /** /latarenka usun — usuwa latarenkę na którą patrzy gracz. */
    private boolean handleRemove(Player player) {
        if (!isRaftWorld(player)) {
            pl.vd.util.AntiSpam.send(player, "§cTa komenda działa tylko w świecie Raft!");
            return true;
        }
        boolean removed = manager.removeLookedAt(player);
        if (removed) {
            pl.vd.util.AntiSpam.send(player, "§aUsunięto latarenkę.");
        } else {
            pl.vd.util.AntiSpam.send(player, "§cNie patrzysz na latarenkę pluginu.");
        }
        return true;
    }

    /** /latarenka czysc — usuwa wszystkie latarenki pluginu ze świata Raft. */
    private boolean handleCleanup(Player player) {
        String worldName = plugin.getConfig().getString("raft-world-name", "RAFT");
        int removed = manager.cleanupAll(worldName);
        pl.vd.util.AntiSpam.send(player, "§aUsunięto §e" + removed + " §alatarenek ze świata Raft.");
        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage("§6=== /latarenka — komendy ===");
        player.sendMessage("§e/latarenka postaw <nazwa> §7- postaw latarenkę (tylko Raft)");
        player.sendMessage("§e/latarenka daj <nazwa> §7- daj latarenkę do ekwipunku (PPM = postaw)");
        player.sendMessage("§e/latarenka lista §7- lista dostępnych presetów");
        player.sendMessage("§e/latarenka usun §7- usuń latarenki w pobliżu");
        player.sendMessage("§e/latarenka czysc §7- usuń wszystkie latarenki ze świata Raft");
    }

    /** Sprawdza czy gracz jest w świecie Raft. */
    private boolean isRaftWorld(Player player) {
        String worldName = plugin.getConfig().getString("raft-world-name", "RAFT");
        return player.getWorld().getName().equalsIgnoreCase(worldName);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("postaw");
            completions.add("daj");
            completions.add("lista");
            completions.add("usun");
            completions.add("czysc");
            completions.add("help");
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("postaw") || args[0].equalsIgnoreCase("daj"))) {
            completions.addAll(manager.listPresets());
        }

        String lastArg = args[args.length - 1].toLowerCase();
        completions.removeIf(s -> !s.toLowerCase().startsWith(lastArg));
        return completions;
    }
}
