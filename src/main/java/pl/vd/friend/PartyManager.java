package pl.vd.friend;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import pl.vd.VillagerDefensePlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Zarządza party (drużynami) graczy.
 * Dane zapisywane w plugins/ServerPlugin/parties.yml.
 *
 * Format pliku:
 *   parties:
 *     <partyId-uuid>:
 *       leader: <uuid>
 *       members:
 *         - <uuid>
 *   invites:
 *     <target-uuid>:
 *       <inviter-uuid>:
 *         party: <partyId-uuid>
 *         from: <inviter-name>
 *         time: <epoch-millis>
 */
public class PartyManager {

    private static final long INVITE_EXPIRY_MS = 60_000L; // 60 sekund na akceptację

    private final File file;
    private final VillagerDefensePlugin plugin;

    // partyId (UUID leadera) → zestaw UUID członków (łącznie z leaderem)
    private final Map<UUID, Set<UUID>> parties = new ConcurrentHashMap<>();

    // pending invites: targetUUID → (inviterUUID → Invite)
    private final Map<UUID, Map<UUID, Invite>> pendingInvites = new ConcurrentHashMap<>();

    public PartyManager(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "parties.yml");
        load();
    }

    /**
     * Tworzy nowe party z podanym graczem jako leaderem.
     */
    public UUID createParty(UUID leaderUuid, String leaderName) {
        Set<UUID> members = new HashSet<>();
        members.add(leaderUuid);
        parties.put(leaderUuid, members);
        save();
        return leaderUuid;
    }

    /**
     * Wysyła zaproszenie do party.
     */
    public InviteResult invite(UUID inviterUuid, String inviterName, UUID targetUuid, String targetName) {
        if (inviterUuid.equals(targetUuid)) return InviteResult.SELF;

        UUID partyId = findPartyId(inviterUuid);
        if (partyId == null) return InviteResult.NO_PARTY;

        UUID targetParty = findPartyId(targetUuid);
        if (targetParty != null) return InviteResult.ALREADY_IN_PARTY;

        Map<UUID, Invite> targetInvites = pendingInvites.computeIfAbsent(targetUuid, k -> new ConcurrentHashMap<>());
        Invite existing = targetInvites.get(inviterUuid);
        if (existing != null && !existing.isExpired() && existing.partyId.equals(partyId)) {
            return InviteResult.ALREADY_INVITED;
        }

        targetInvites.put(inviterUuid, new Invite(partyId, inviterName, System.currentTimeMillis()));
        return InviteResult.OK;
    }

    /**
     * Akceptuje zaproszenie od konkretnego gracza.
     */
    public UUID acceptInvite(UUID playerUuid, UUID inviterUuid) {
        Map<UUID, Invite> targetInvites = pendingInvites.get(playerUuid);
        if (targetInvites == null) return null;

        Invite invite = targetInvites.remove(inviterUuid);
        if (invite == null || invite.isExpired()) {
            if (targetInvites.isEmpty()) pendingInvites.remove(playerUuid);
            return null;
        }
        if (targetInvites.isEmpty()) pendingInvites.remove(playerUuid);

        leaveParty(playerUuid);

        Set<UUID> members = parties.get(invite.partyId);
        if (members == null) {
            members = new HashSet<>();
            members.add(playerUuid);
            parties.put(playerUuid, members);
            save();
            return playerUuid;
        }
        members.add(playerUuid);
        save();
        return invite.partyId;
    }

    /**
     * Akceptuje pierwsze (jedyne) pending invite — dla kompatybilności wstecznej.
     */
    public UUID acceptInvite(UUID playerUuid) {
        Map<UUID, Invite> targetInvites = pendingInvites.get(playerUuid);
        if (targetInvites == null || targetInvites.isEmpty()) return null;

        // znajdź pierwszy nie-expired
        for (Map.Entry<UUID, Invite> entry : targetInvites.entrySet()) {
            if (!entry.getValue().isExpired()) {
                return acceptInvite(playerUuid, entry.getKey());
            }
        }
        pendingInvites.remove(playerUuid);
        return null;
    }

    /**
     * Odrzuca zaproszenie od konkretnego gracza.
     */
    public void declineInvite(UUID playerUuid, UUID inviterUuid) {
        Map<UUID, Invite> targetInvites = pendingInvites.get(playerUuid);
        if (targetInvites != null) {
            targetInvites.remove(inviterUuid);
            if (targetInvites.isEmpty()) pendingInvites.remove(playerUuid);
        }
    }

    /**
     * Odrzuca wszystkie pending invite.
     */
    public void declineAllInvites(UUID playerUuid) {
        pendingInvites.remove(playerUuid);
    }

    /**
     * Czy gracz ma jakiekolwiek nie-expired invite.
     */
    public boolean hasPendingInvite(UUID playerUuid) {
        Map<UUID, Invite> targetInvites = pendingInvites.get(playerUuid);
        if (targetInvites == null) return false;
        // wyczyść expired
        targetInvites.entrySet().removeIf(e -> e.getValue().isExpired());
        if (targetInvites.isEmpty()) {
            pendingInvites.remove(playerUuid);
            return false;
        }
        return true;
    }

    /**
     * Zwraca wszystkie pending invite gracza (nie-expired).
     */
    public Map<UUID, Invite> getPendingInvites(UUID playerUuid) {
        Map<UUID, Invite> targetInvites = pendingInvites.get(playerUuid);
        if (targetInvites == null) return Collections.emptyMap();
        targetInvites.entrySet().removeIf(e -> e.getValue().isExpired());
        if (targetInvites.isEmpty()) {
            pendingInvites.remove(playerUuid);
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(targetInvites);
    }

    /**
     * Zwraca invite od konkretnego gracza lub null.
     */
    public Invite getPendingInvite(UUID playerUuid, UUID inviterUuid) {
        Map<UUID, Invite> targetInvites = pendingInvites.get(playerUuid);
        if (targetInvites == null) return null;
        Invite invite = targetInvites.get(inviterUuid);
        if (invite != null && invite.isExpired()) {
            targetInvites.remove(inviterUuid);
            if (targetInvites.isEmpty()) pendingInvites.remove(playerUuid);
            return null;
        }
        return invite;
    }

    /**
     * Znajduje ID party dla gracza (UUID leadera) lub null.
     */
    public UUID findPartyId(UUID playerUuid) {
        for (Map.Entry<UUID, Set<UUID>> entry : parties.entrySet()) {
            if (entry.getValue().contains(playerUuid)) return entry.getKey();
        }
        return null;
    }

    public boolean isLeader(UUID playerUuid) {
        Set<UUID> members = parties.get(playerUuid);
        return members != null && members.contains(playerUuid);
    }

    public Set<UUID> getMembers(UUID partyId) {
        return parties.getOrDefault(partyId, Collections.emptySet());
    }

    public UUID leaveParty(UUID playerUuid) {
        UUID partyId = findPartyId(playerUuid);
        if (partyId == null) return null;

        Set<UUID> members = parties.get(partyId);
        if (members == null) return null;

        members.remove(playerUuid);

        if (members.isEmpty()) {
            parties.remove(partyId);
        } else if (partyId.equals(playerUuid)) {
            UUID newLeader = members.iterator().next();
            Set<UUID> copy = new HashSet<>(members);
            parties.remove(partyId);
            parties.put(newLeader, copy);
        }

        save();
        return partyId;
    }

    public boolean isInParty(UUID playerUuid) {
        return findPartyId(playerUuid) != null;
    }

    public void disbandParty(UUID partyId) {
        parties.remove(partyId);
        // usuń pending invites do tego party (we wszystkich mapach)
        for (Map<UUID, Invite> targetInvites : pendingInvites.values()) {
            targetInvites.entrySet().removeIf(e -> e.getValue().partyId.equals(partyId));
        }
        pendingInvites.entrySet().removeIf(e -> e.getValue().isEmpty());
        save();
    }

    // ---------- persistence ----------

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection partiesSection = cfg.getConfigurationSection("parties");
        if (partiesSection != null) {
            for (String idStr : partiesSection.getKeys(false)) {
                UUID partyId;
                try { partyId = UUID.fromString(idStr); } catch (IllegalArgumentException e) { continue; }
                List<String> memberStrs = partiesSection.getStringList(idStr + ".members");
                Set<UUID> members = new HashSet<>();
                for (String s : memberStrs) {
                    try { members.add(UUID.fromString(s)); } catch (IllegalArgumentException ignored) {}
                }
                if (!members.isEmpty()) {
                    parties.put(partyId, members);
                }
            }
        }

        ConfigurationSection invitesSection = cfg.getConfigurationSection("invites");
        if (invitesSection != null) {
            for (String targetStr : invitesSection.getKeys(false)) {
                UUID targetUuid;
                try { targetUuid = UUID.fromString(targetStr); } catch (IllegalArgumentException e) { continue; }
                ConfigurationSection targetSection = invitesSection.getConfigurationSection(targetStr);
                if (targetSection == null) continue;
                Map<UUID, Invite> inviterMap = new ConcurrentHashMap<>();
                for (String inviterStr : targetSection.getKeys(false)) {
                    UUID inviterUuid;
                    try { inviterUuid = UUID.fromString(inviterStr); } catch (IllegalArgumentException e) { continue; }
                    ConfigurationSection invSection = targetSection.getConfigurationSection(inviterStr);
                    if (invSection == null) continue;
                    UUID partyId;
                    try { partyId = UUID.fromString(invSection.getString("party", "")); } catch (IllegalArgumentException e) { continue; }
                    String from = invSection.getString("from", "");
                    long time = invSection.getLong("time", 0);
                    inviterMap.put(inviterUuid, new Invite(partyId, from, time));
                }
                if (!inviterMap.isEmpty()) {
                    pendingInvites.put(targetUuid, inviterMap);
                }
            }
        }
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, Set<UUID>> entry : parties.entrySet()) {
            String path = "parties." + entry.getKey().toString();
            cfg.set(path + ".leader", entry.getKey().toString());
            cfg.set(path + ".members", entry.getValue().stream().map(UUID::toString).toList());
        }
        for (Map.Entry<UUID, Map<UUID, Invite>> targetEntry : pendingInvites.entrySet()) {
            String basePath = "invites." + targetEntry.getKey().toString();
            for (Map.Entry<UUID, Invite> inviterEntry : targetEntry.getValue().entrySet()) {
                String path = basePath + "." + inviterEntry.getKey().toString();
                cfg.set(path + ".party", inviterEntry.getValue().partyId.toString());
                cfg.set(path + ".from", inviterEntry.getValue().inviterName);
                cfg.set(path + ".time", inviterEntry.getValue().timestamp);
            }
        }
        try {
            file.getParentFile().mkdirs();
            cfg.save(file);
        } catch (IOException ignored) {}
    }

    // ---------- inner classes ----------

    public record Invite(UUID partyId, String inviterName, long timestamp) {
        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > INVITE_EXPIRY_MS;
        }
        public long secondsLeft() {
            long left = INVITE_EXPIRY_MS - (System.currentTimeMillis() - timestamp);
            return Math.max(0, left / 1000);
        }
    }

    public enum InviteResult {
        OK, ALREADY_INVITED, ALREADY_IN_PARTY, NO_PARTY, SELF
    }
}
