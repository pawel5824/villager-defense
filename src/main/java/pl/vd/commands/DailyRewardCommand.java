package pl.vd.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pl.vd.VillagerDefensePlugin;
import pl.vd.lobby.DailyRewardManager;
import net.kyori.adventure.text.Component;

/**
 * /codzienna lub /daily — odbiór codziennej nagrody (exp).
 *
 * Komunikaty:
 *   PL: "Odebrano dzienną nagrodę! +X XP (streak: Y dni)"
 *   PL: "Odebrałeś/aś już codzienną skrzynię!"
 */
public class DailyRewardCommand implements CommandExecutor {

    private final VillagerDefensePlugin plugin;

    public DailyRewardCommand(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("§cTa komenda jest tylko dla graczy."));
            return true;
        }

        DailyRewardManager drm = plugin.getDailyRewardManager();

        if (drm.canClaim(player)) {
            int oldStreak = drm.getStreak(player);
            if (drm.claim(player)) {
                int newStreak = drm.getStreak(player);
                int bonus = drm.getStreakBonus(newStreak);
                int total = 5 + bonus;

                StringBuilder msg = new StringBuilder();
                msg.append("§6⭐ §eOdebrano dzienną nagrodę! §7+§a").append(total).append(" XP");

                if (newStreak > 1) {
                    msg.append(" §7(§estreak: §f").append(newStreak).append(" §7dni§7)");
                } else if (newStreak == 1 && oldStreak > 0) {
                    msg.append(" §7(§cstreak zerwany!§7)");
                }

                player.sendMessage(Component.text(msg.toString()));
                plugin.getPlayerXPBar().refresh(player);
            }
        } else {
            String remaining = drm.getRemainingTime(player);
            player.sendMessage(Component.text("§cOdebrałeś/aś już codzienną skrzynię! §7Następna: " + remaining));
        }

        return true;
    }
}
