package pl.vd.commands;

import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import pl.vd.VillagerDefensePlugin;
import pl.vd.util.AntiSpam;

public class TpWandCommand implements CommandExecutor, TabCompleter {

    private final VillagerDefensePlugin plugin;

    public TpWandCommand(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("§cTa komenda działa tylko w grze."));
            return true;
        }
        if (!player.hasPermission("vd.admin")) {
            AntiSpam.send(player, "§cBrak uprawnień.");
            return true;
        }
        plugin.getTeleportWandManager().give(player);
        return true;
    }

    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return java.util.Collections.emptyList();
    }
}
