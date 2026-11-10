package pl.vd.raft.core;

import org.bukkit.entity.Player;
import pl.vd.raft.RaftPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Zarządza stanem gry - fazami dnia, czasem gry i statystykami graczy.
 */
public class GameManager {

    private final RaftPlugin plugin;
    private final Map<UUID, PlayerData> playerData = new HashMap<>();
    private GamePhase currentPhase = GamePhase.DAY;
    private long gameTick = 0;

    /**
     * Okres petli statystyk w tickach (20 = raz na sekunde).
     * Ta sama stala trafia do updateStats jako czas, ktory uplynal — dzieki temu tempo
     * drenazu pragnienia jest naprawde "na sekunde" i zmiana tego okresu nie zmieni
     * balansu po cichu.
     */
    private static final long STATS_INTERVAL_TICKS = 20L;

    public GameManager(RaftPlugin plugin) {
        this.plugin = plugin;
        startGameLoop();
    }

    private void startGameLoop() {
        plugin.getServer().getScheduler().runTaskTimer(plugin.getParent(), () -> {
            gameTick++;
            updateDayNightCycle();
            updatePlayerStats();
        }, 0L, STATS_INTERVAL_TICKS); // Co sekundę
    }

    private void updateDayNightCycle() {
        // Cykl dnia/nocy - zmiana faz co X ticków
        if (gameTick % 6000 == 0) { // Co 5 minut w real time
            currentPhase = currentPhase.next();
            broadcastPhaseChange();
        }
    }

    private void updatePlayerStats() {
        for (UUID uuid : playerData.keySet()) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null && player.isOnline()) {
                PlayerData data = playerData.get(uuid);
                data.updateStats(player, STATS_INTERVAL_TICKS / 20.0);
                // Wyslij statystyki do klienta Fabric
                pl.vd.raft.net.RaftStatsPacketSender.getInstance().sendStats(player, data);
            }
        }
    }

    private void broadcastPhaseChange() {
        // Faza zmienia się cicho, bez powiadomień w czacie
    }

    public void joinGame(Player player) {
        UUID uuid = player.getUniqueId();
        if (!playerData.containsKey(uuid)) {
            playerData.put(uuid, new PlayerData(plugin, uuid));
        }
        PlayerData data = playerData.get(uuid);
        data.onJoin(player);
        // Dodaj do HUD dopiero teraz — gracz faktycznie gra w Raft
        plugin.getHudManager().addPlayer(player);
    }

    public void leaveGame(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerData data = playerData.remove(uuid);
        if (data != null) {
            data.onLeave(player);
        }
        // Usuń z HUD — gracz opuścił Raft
        plugin.getHudManager().removePlayer(player);
        // Jawnie zamknij sesję statystyk: pakiety raft:stats i tak by ustaly (gracz znika
        // z playerData), ale klient dowiaduje się o tym dopiero po TTL. Pakiet session_end
        // mówi mu "wróć do wanilii TERAZ" — bez tej sekundy ze starymi paskami.
        pl.vd.raft.net.RaftStatsPacketSender.getInstance().sendSessionEnd(player);
        // WAŻNE: usunięcie z playerData zatrzymuje też pakiet raft:stats
        // (updatePlayerStats iteruje po tej mapie). Bez tego klient z modem
        // dostawał statystyki dalej — także w lobby — i trzymał customowy HUD.
    }

    public GamePhase getCurrentPhase() {
        return currentPhase;
    }

    public long getGameTick() {
        return gameTick;
    }

    public PlayerData getPlayerData(UUID uuid) {
        return playerData.get(uuid);
    }

    public void shutdown() {
        // Zapisz dane wszystkich graczy
        for (Map.Entry<UUID, PlayerData> entry : playerData.entrySet()) {
            entry.getValue().save();
        }
    }
}
