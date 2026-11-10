package pl.vd.restart;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import pl.vd.VillagerDefensePlugin;

import java.util.Collection;
import java.util.List;

/**
 * Komenda /restart zarejestrowana przez API Brigadier Papera (LifecycleEvents.COMMANDS).
 * Nadpisuje komendę Spigota (org.spigotmc.RestartCommand) W DISPATCHERZE BRIGADIER -
 * rejestracja przez plugin.yml nie działała, bo Bukkit przy konflikcie nazw chował
 * naszą komendę pod prefiksem (villagerdefense:restart) i /restart zostawało komendą
 * Spigota z domyślnym podpowiadaniem nicków graczy.
 *
 * Dzięki Brigadier: bez podpowiadania nicków, a TAB podpowiada "3" (albo "anuluj") -
 * dokładnie jak /def restart.
 *
 * Zachowuje się DOKŁADNIE jak /def restart [sekundy]:
 *  - /restart            -> restart za restart.default-seconds (domyślnie 30 s) -
 *                           odliczanie TYLKO na ekranie (title), gracze wracają do lobby,
 *                           światy są zapisywane, a serwer wstaje sam (restart.bat),
 *  - /restart <sekundy>  -> to samo, ale za podaną liczbę sekund (1-600),
 *  - /restart anuluj     -> odwołuje zaplanowany restart.
 *
 * Wcześniej komenda IGNOROWAŁA argumenty i od razu wołała Bukkit.spigot().restart() -
 * stąd „chcę dodać czas, a serwer restartuje się natychmiast".
 */
public class RestartBrigadierCommand implements BasicCommand {

    private final VillagerDefensePlugin plugin;

    public RestartBrigadierCommand(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        if (!sender.hasPermission("vd.admin")) {
            sender.sendMessage(Component.text("§cBrak uprawnień do restartu."));
            return;
        }

        // /restart anuluj - odwołaj zaplanowany restart
        if (args.length >= 1 && (args[0].equalsIgnoreCase("anuluj") || args[0].equalsIgnoreCase("cancel")
                || args[0].equalsIgnoreCase("stop"))) {
            if (plugin.getRestartManager().cancel()) {
                sender.sendMessage(Component.text("§aRestart anulowany."));
            } else {
                sender.sendMessage(Component.text("§7Nie ma zaplanowanego restartu."));
            }
            return;
        }

        int seconds = plugin.getConfig().getInt("restart.default-seconds", 30);
        if (args.length >= 1) {
            try {
                seconds = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {
                // nie-liczba = domyślny czas z configu (jak /def restart)
            }
        }
        seconds = Math.max(1, Math.min(600, seconds));
        if (!plugin.getRestartManager().schedule(seconds)) {
            sender.sendMessage(Component.text("§cRestart już jest zaplanowany - anulujesz go komendą /restart anuluj."));
            return;
        }
        // Bez potwierdzenia na czacie — odliczanie widać tylko na ekranie (title).
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        // "3" na pierwszym miejscu = TAB od razu wypełnia 3 sekundy
        return List.of("3", "anuluj");
    }
}
