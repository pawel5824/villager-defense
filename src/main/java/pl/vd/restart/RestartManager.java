package pl.vd.restart;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import pl.vd.VillagerDefensePlugin;
import pl.vd.arena.Arena;
import pl.vd.util.Lang;

import java.util.UUID;

/**
 * Automatyczny restart serwera z odliczaniem na ekranie (komenda /def restart [sekundy]).
 *
 * Po odliczeniu WSZYSCY gracze (także na arenach - najpierw wychodzą z gry z powrotem
 * ekwipunku, potem lądują w lobby serwera - sekcja lobby w config.yml), światy i dane
 * są zapisywane, a serwer wykonuje restart. Paper uruchamia wtedy skrypt
 * restart-script ze spigot.yml (u nas restart.bat), który odpala start.bat od nowa -
 * serwer wstaje sam, bez ręcznego włączania.
 */
public class RestartManager {

    private final VillagerDefensePlugin plugin;
    private BukkitTask task;
    private int secondsLeft;
    private boolean scheduled;

    public RestartManager(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isScheduled() {
        return scheduled;
    }

    public int getSecondsLeft() {
        return secondsLeft;
    }

    /** Planuje restart za podaną liczbę sekund; false, gdy odliczanie już trwa. */
    public boolean schedule(int seconds) {
        if (scheduled) return false;
        scheduled = true;
        secondsLeft = Math.max(1, seconds);
        // Odliczanie TYLKO na ekranie (title) — od razu pokaż pełny czas,
        // bez żadnych komunikatów na czacie.
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle("§c§lRESTART", "§7za §f" + secondsLeft + " §7s", 5, 40, 10);
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        return true;
    }

    /** Anuluje zaplanowany restart; false, gdy nic nie było zaplanowane. */
    public boolean cancel() {
        if (!scheduled) return false;
        scheduled = false;
        if (task != null) {
            task.cancel();
            task = null;
        }
        broadcast("restart-cancelled");
        return true;
    }

    /** Cicho wyłącza odliczanie (np. przy wyłączaniu pluginu) - bez komunikatu. */
    public void shutdown() {
        scheduled = false;
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        secondsLeft--;
        if (secondsLeft <= 0) {
            shutdown();
            executeRestart();
            return;
        }
        // Odliczanie TYLKO na ekranie (title), co sekundę, przez cały czas —
        // żadnych komunikatów na czacie.
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle("§c§lRESTART", "§7za §f" + secondsLeft + " §7s", 0, 25, 5);
        }
    }

    /** Koniec odliczania: gracze do lobby, zapis światów, restart serwera. */
    private void executeRestart() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                Arena arena = findArenaOf(p);
                if (arena != null) {
                    plugin.getGameManager().removePlayer(p, arena); // ekwipunek wraca, gracz schodzi z areny
                }
                org.bukkit.Location lobby = plugin.getLobbyLocation();
                if (lobby != null) {
                    p.teleport(lobby);
                }
                Lang.send(p, "restart-teleport");
            } catch (Throwable ignored) {
                // pojedynczy gracz nie może zablokować restartu
            }
        }
        broadcast("restart-now");

        // zapisz światy i dane, zanim serwer się zamknie
        try {
            for (World w : Bukkit.getWorlds()) {
                w.save();
            }
            plugin.getPlayerDataManager().saveAll();
        } catch (Throwable ignored) {}

        // restart przez Paper: wykona skrypt restart-script ze spigot.yml (restart.bat),
        // który odpala nowy proces serwera - bez ręcznego włączania. Krótkie opóźnienie,
        // żeby gracze zdążyli zobaczyć teleport/komunikat.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                Bukkit.spigot().restart();
            } catch (Throwable t) {
                Bukkit.shutdown();
            }
        }, 5L);
    }

    /** Wiadomość do wszystkich graczy (z uwzględnieniem ustawień powiadomień). */
    private void broadcast(String key, Object... args) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            var data = plugin.getPlayerDataManager().get(p.getUniqueId(), p.getName());
            if (data.getSettingInt("notif-chat", 100) <= 0) continue;
            p.sendMessage(Component.text(Lang.get(p, key, args)));
        }
        Bukkit.getConsoleSender().sendMessage(Component.text(Lang.getPl(key, args)));
    }

    private Arena findArenaOf(Player player) {
        for (Arena arena : plugin.getArenaManager().getArenas().values()) {
            if (arena.isPlaying(player)) return arena;
        }
        return null;
    }

    /** UUID-y graczy na arenach (pomocnicze - do ewentualnych testów). */
    @SuppressWarnings("unused")
    private java.util.Set<UUID> arenaPlayers() {
        java.util.Set<UUID> uuids = new java.util.HashSet<>();
        for (Arena arena : plugin.getArenaManager().getArenas().values()) {
            uuids.addAll(arena.getPlayers());
        }
        return uuids;
    }
}
