package pl.vd.commands;

import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pl.vd.VillagerDefensePlugin;

/**
 * Komenda /osiagniecia (alias /achievements) - otwiera GUI z osiągnięciami
 * gracza (ekran główny z kategoriami). Kliknięcie kategorii pokaże na razie
 * zapowiedź - ekrany kategorii z listą osiągnięć powstaną w kolejnym kroku.
 */
public class AchievementsCommand implements CommandExecutor {

    private final VillagerDefensePlugin plugin;

    public AchievementsCommand(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("§cTa komenda działa tylko w grze."));
            return true;
        }
        plugin.getAchievementsGUI().open(player, pl.vd.achievements.AchievementsHolder.Source.COMMAND);
        return true;
    }
}
