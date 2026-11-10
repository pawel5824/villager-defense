package pl.vd.raft.player;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import pl.vd.raft.RaftPlugin;

import java.util.*;

/**
 * Zarządza sesjami graczy i ich danymi.
 * Obsługuje party (grupy do max-party-size graczy) na jednym świecie.
 * Chunki członków party nakładają się — obciążenie jak dla 1-2 graczy.
 */
public class PlayerManager {

    private final RaftPlugin plugin;
    private final Map<UUID, PlayerSession> sessions = new HashMap<>();

    /** host UUID -> set UUID członków party (bez hosta). */
    private final Map<UUID, Set<UUID>> parties = new HashMap<>();
    /** member UUID -> host UUID (odwrotny indeks). */
    private final Map<UUID, UUID> memberToHost = new HashMap<>();

    public PlayerManager(RaftPlugin plugin) {
        this.plugin = plugin;
    }

    /** Maksymalna liczba graczy w jednej sesji Raft. */
    public int getMaxPartySize() {
        FileConfiguration cfg = plugin.getConfig();
        int max = cfg.getInt("raft-max-party-size", 3);
        return Math.max(1, max);
    }

    /**
     * Rozpocznij sesję gracza (host).
     */
    public void startSession(Player player) {
        UUID uuid = player.getUniqueId();
        if (!sessions.containsKey(uuid)) {
            sessions.put(uuid, new PlayerSession(plugin, uuid));
        }
        PlayerSession session = sessions.get(uuid);
        session.onJoin(player);
        // host nie jest członkiem żadnego party
    }

    /**
     * Dołącz do sesji istniejącego gracza (gość).
     * @return true jeśli dołączono, false jeśli limit party lub brak hosta.
     */
    public boolean joinSession(Player joiner, UUID hostId) {
        UUID joinerId = joiner.getUniqueId();
        if (sessions.containsKey(joinerId)) return false; // już w sesji

        PlayerSession hostSession = sessions.get(hostId);
        if (hostSession == null) return false; // host nie jest w sesji

        int partySize = getPartySize(hostId);
        if (partySize >= getMaxPartySize()) return false; // limit osiągnięty

        // dodaj do party
        parties.computeIfAbsent(hostId, k -> new HashSet<>()).add(joinerId);
        memberToHost.put(joinerId, hostId);

        // utwórz sesję dla gościa (dane wczytane z YAML)
        sessions.put(joinerId, new PlayerSession(plugin, joinerId));
        sessions.get(joinerId).onJoin(joiner);

        // teleportuj gościa do hosta
        Player host = plugin.getServer().getPlayer(hostId);
        if (host != null && host.isOnline()) {
            joiner.teleport(host.getLocation());
        }

        return true;
    }

    /**
     * Zakończ sesję gracza.
     */
    public void endSession(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerSession session = sessions.get(uuid);
        if (session != null) {
            session.onLeave(player);
            session.save();
        }
        sessions.remove(uuid);

        // usuń z party (jeśli jest hostem — usuń cały party; jeśli członkiem — tylko z listy)
        Set<UUID> partyMembers = parties.remove(uuid);
        if (partyMembers != null) {
            for (UUID memberId : partyMembers) {
                memberToHost.remove(memberId);
            }
        }
        UUID hostId = memberToHost.remove(uuid);
        if (hostId != null) {
            Set<UUID> hostParty = parties.get(hostId);
            if (hostParty != null) hostParty.remove(uuid);
        }
    }

    /**
     * Liczba graczy w party (host + członkowie).
     */
    public int getPartySize(UUID hostId) {
        Set<UUID> members = parties.get(hostId);
        return 1 + (members != null ? members.size() : 0);
    }

    /**
     * Znajdź hosta danego gracza (lub siebie samego jeśli jest hostem).
     */
    public UUID findHost(UUID memberId) {
        UUID host = memberToHost.get(memberId);
        return host != null ? host : memberId;
    }

    /**
     * Sprawdź czy gracz jest w tej samej sesji co inny gracz.
     */
    public boolean isInSameSession(UUID a, UUID b) {
        UUID hostA = findHost(a);
        UUID hostB = findHost(b);
        return hostA.equals(hostB);
    }

    /** Pobierz sesję gracza. */
    public PlayerSession getSession(Player player) {
        return sessions.get(player.getUniqueId());
    }

    /** Pobierz sesję gracza po UUID. */
    public PlayerSession getSession(UUID uuid) {
        return sessions.get(uuid);
    }

    /** Sprawdź czy gracz ma aktywną sesję. */
    public boolean hasActiveSession(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    /** Zapisz wszystkie sesje. */
    public void saveAll() {
        for (PlayerSession session : sessions.values()) {
            session.save();
        }
    }

    /** Pobierz wartość ustawienia gracza. */
    public String getSetting(Player player, String key, String defaultValue) {
        PlayerSession session = getSession(player);
        if (session == null) return defaultValue;
        return session.getSetting(key, defaultValue);
    }

    /** Ustaw wartość ustawienia gracza. */
    public void setSetting(Player player, String key, String value) {
        PlayerSession session = getSession(player);
        if (session == null) {
            startSession(player);
            session = getSession(player);
        }
        if (session != null) {
            session.setSetting(key, value);
        }
    }

    /** Wyłącz wszystkie sesje. */
    public void shutdown() {
        for (PlayerSession session : sessions.values()) {
            session.save();
        }
        sessions.clear();
        parties.clear();
        memberToHost.clear();
    }
}
