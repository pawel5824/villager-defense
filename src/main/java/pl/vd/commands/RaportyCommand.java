package pl.vd.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import pl.vd.reports.ReportManager;

public class RaportyCommand implements CommandExecutor {

    private final ReportManager reportManager;

    public RaportyCommand(ReportManager reportManager) {
        this.reportManager = reportManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("vd.admin")) {
            sender.sendMessage(org.bukkit.ChatColor.RED + "Brak uprawnień.");
            return true;
        }
        int page = 1;
        if (args.length >= 1) {
            try { page = Integer.parseInt(args[0]); } catch (NumberFormatException ignored) {}
        }
        reportManager.showReports(sender, page);
        return true;
    }
}
