package pl.vd.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.server.TabCompleteEvent;
import pl.vd.VillagerDefensePlugin;
import pl.vd.arena.Arena;

import java.util.Set;

/**
 * Gracze będący na arenie (w lobby, w grze lub jako widz) mogą używać tylko
 * komend /hub, /lobby oraz /przerwa. Dodatkowo operator (vd.admin) może na
 * arenie używać /arena. Wszystkie pozostałe komendy są blokowane na trzech
 * poziomach:
 * - wykonanie: PlayerCommandPreprocessEvent (komenda nie zostanie uruchomiona),
 * - podpowiedzi taba: TabCompleteEvent (tylko dozwolone są proponowane),
 * - paleta komend klienta: PlayerCommandSendEvent (komendy /def, /vd,
 *   /vdefense itd. w ogóle nie są pokazywane graczowi po wpisaniu "/").
 * Dzięki temu na arenie nie da się użyć żadnej komendy poza tymi.
 */
public class CommandRestrictionListener implements Listener {

    /** Komendy dozwolone na arenie dla każdego (bez ukośnika, małymi literami). */
    private static final Set<String> ALLOWED_ON_ARENA = Set.of("hub", "lobby", "przerwa", "ustawienia", "osiagniecia", "report", "settings", "achievements");

    /** Dodatkowe komendy dozwolone TYLKO podczas przerwy (ustawienia, osiagniecia, report). */
    private static final Set<String> PAUSE_ONLY_COMMANDS = Set.of("ustawienia", "osiagniecia", "report", "settings", "achievements");

    /** Komendy pluginu, które NA ARENIE może używać wyłącznie operator (vd.admin). */
    private static final Set<String> ADMIN_ARENA_COMMANDS = Set.of("arena");

    private final VillagerDefensePlugin plugin;

    public CommandRestrictionListener(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        Arena arena = findArenaOf(player);
        if (arena == null) return;

        String cmd = event.getMessage().substring(1).split(" ")[0].toLowerCase();
        if (ALLOWED_ON_ARENA.contains(cmd)) return;
        // /arena na arenie może używać wyłącznie operator (vd.admin)
        if (ADMIN_ARENA_COMMANDS.contains(cmd) && player.hasPermission("vd.admin")) return;
        // podczas przerwy dodatkowe komendy: ustawienia, osiagniecia, report
        if (PAUSE_ONLY_COMMANDS.contains(cmd) && plugin.getPauseManager().isPaused(arena)) return;

        // cicho blokuj wszystkie inne komendy (bez komunikatu)
        event.setCancelled(true);
    }

    @EventHandler
    public void onTabComplete(TabCompleteEvent event) {
        if (!(event.getSender() instanceof Player player)) return;
        Arena arena = findArenaOf(player);
        if (arena == null) return;

        String buffer = event.getBuffer();

        // jeśli gracz pisze argumenty komendy, nie pokazuj nic
        if (buffer.indexOf(' ') >= 0) {
            event.setCompletions(java.util.List.of());
            return;
        }

        // podpowiadaj wyłącznie dozwolone komendy (operatorowi dodatkowo /arena)
        boolean slash = buffer.startsWith("/");
        String prefix = (slash ? buffer.substring(1) : buffer).toLowerCase();
        java.util.List<String> suggestions = new java.util.ArrayList<>();
        for (String cmd : ALLOWED_ON_ARENA) {
            if (cmd.startsWith(prefix)) {
                suggestions.add(slash ? "/" + cmd : cmd);
            }
        }
        // podczas przerwy podpowiedzi dodatkowych komend
        if (plugin.getPauseManager().isPaused(arena)) {
            for (String cmd : PAUSE_ONLY_COMMANDS) {
                if (cmd.startsWith(prefix)) {
                    suggestions.add(slash ? "/" + cmd : cmd);
                }
            }
        }
        if (player.hasPermission("vd.admin")) {
            for (String cmd : ADMIN_ARENA_COMMANDS) {
                if (cmd.startsWith(prefix)) {
                    suggestions.add(slash ? "/" + cmd : cmd);
                }
            }
        }
        event.setCompletions(suggestions);
    }

    @EventHandler
    public void onCommandSend(PlayerCommandSendEvent event) {
        Player player = event.getPlayer();
        Arena arena = findArenaOf(player);
        if (arena == null) return;

        boolean paused = plugin.getPauseManager().isPaused(arena);

        // usuń z palety komend wszystko poza dozwolonymi, aby /def, /vd,
        // /vdefense itp. w ogóle nie pojawiały się graczowi na arenie
        // (operatorowi zostaw dodatkowo /arena; podczas przerwy dodatkowo komendy PAUSE_ONLY)
        event.getCommands().removeIf(cmd -> {
            String c = cmd.toLowerCase();
            if (ALLOWED_ON_ARENA.contains(c)) return false;
            if (paused && PAUSE_ONLY_COMMANDS.contains(c)) return false;
            return !(player.hasPermission("vd.admin") && ADMIN_ARENA_COMMANDS.contains(c));
        });
    }

    private Arena findArenaOf(Player player) {
        for (Arena arena : plugin.getArenaManager().getArenas().values()) {
            if (arena.isPlaying(player) || arena.getSpectators().contains(player.getUniqueId())) {
                return arena;
            }
        }
        return null;
    }
}
