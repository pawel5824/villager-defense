package pl.vd.commands;

import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pl.vd.VillagerDefensePlugin;
import pl.vd.arena.Arena;

/**
 * Komenda /ustawienia (alias /settings) - otwiera panel ustawien gracza.
 *
 * W trybie Raft (swiat z configu raft-world-name) otwiera totalnie inny,
 * wlasny panel Rafta (UstawieniaGUI 4x9: Dzwieki, Powiadomienia, Muzyka,
 * Jezyk); poza Raftem - klasyczny panel VD z ShopGUI (54 sloty).
 */
public class SettingsCommand implements CommandExecutor {

    private final VillagerDefensePlugin plugin;

    public SettingsCommand(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("§cTa komenda dziala tylko w grze."));
            return true;
        }

        // Tryb Raft: osobny panel (GUI 4x9), totalnie inny niz panel VD.
        String raftWorldName = plugin.getConfig().getString("raft-world-name", "RAFT");
        if (player.getWorld().getName().equalsIgnoreCase(raftWorldName)) {
            var raft = plugin.getRaftPlugin();
            var gui = raft != null ? raft.getUstawieniaGui() : null;
            if (gui != null) {
                gui.open(player);
                return true;
            }
        }

        // Probuj znalezc arena, ale nie wymagaj
        Arena arena = plugin.getShopGUI().resolveArena(player);
        if (arena == null && !plugin.getArenaManager().getArenas().isEmpty()) {
            arena = plugin.getArenaManager().getArenas().values().iterator().next();
        }

        // Otwieraj ustawienia nawet bez areny
        plugin.getShopGUI().openSettings(player, arena, false);
        return true;
    }
}
