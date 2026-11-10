package pl.vd.friend;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import pl.vd.VillagerDefensePlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Zarządza listą znajomych graczy.
 * Dane zapisywane w plugins/ServerPlugin/friends.yml.
 *
 * Format pliku:
 *   friends:
 *     <uuid-gracza>:
 *       name: NazwaGracza
 *       list:
 *         - <uuid-znajomego>
 */
public class FriendManager {

    private final File file;
    private final Map<UUID, Set<UUID>> friends = new HashMap<>();
    private final Map<UUID, String> nameCache = new HashMap<>(); // uuid → ostatnia znana nazwa

    public FriendManager(VillagerDefensePlugin plugin) {
        this.file = new File(plugin.getDataFolder(), "friends.yml");
        load();
    }

    /**
     * Dodaje znajomego (obustronnie).
     * Zwraca true jeśli dodano, false jeśli już byli znajomymi lub podano siebie.
     */
    public boolean addFriend(UUID uuid, String name, UUID friendUuid, String friendName) {
        if (uuid.equals(friendUuid)) return false;

        Set<UUID> list = friends.computeIfAbsent(uuid, k -> new HashSet<>());
        if (list.contains(friendUuid)) return false;

        list.add(friendUuid);
        nameCache.put(uuid, name);
        nameCache.put(friendUuid, friendName);

        // obustronnie
        friends.computeIfAbsent(friendUuid, k -> new HashSet<>()).add(uuid);

        save();
        return true;
    }

    /**
     * Usuwa znajomego (obustronnie).
     */
    public boolean removeFriend(UUID uuid, UUID friendUuid) {
        Set<UUID> list = friends.get(uuid);
        if (list == null || !list.contains(friendUuid)) return false;

        list.remove(friendUuid);
        Set<UUID> otherList = friends.get(friendUuid);
        if (otherList != null) otherList.remove(uuid);

        save();
        return true;
    }

    /**
     * Zwraca listę UUID znajomych gracza.
     */
    public Set<UUID> getFriends(UUID uuid) {
        return friends.getOrDefault(uuid, Collections.emptySet());
    }

    /**
     * Czy dwóch graczy jest znajomymi.
     */
    public boolean areFriends(UUID uuid1, UUID uuid2) {
        Set<UUID> list = friends.get(uuid1);
        return list != null && list.contains(uuid2);
    }

    /**
     * Zwraca znaną nazwę gracza (cache z pliku) lub null.
     */
    public String getCachedName(UUID uuid) {
        return nameCache.get(uuid);
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("friends");
        if (root == null) return;

        for (String uuidStr : root.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(uuidStr);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            ConfigurationSection section = root.getConfigurationSection(uuidStr);
            if (section == null) continue;

            String name = section.getString("name", "");
            nameCache.put(uuid, name);

            List<String> list = section.getStringList("list");
            Set<UUID> set = new HashSet<>();
            for (String s : list) {
                try {
                    set.add(UUID.fromString(s));
                } catch (IllegalArgumentException ignored) {}
            }
            friends.put(uuid, set);
        }
    }

    private void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, Set<UUID>> entry : friends.entrySet()) {
            String uuidStr = entry.getKey().toString();
            cfg.set("friends." + uuidStr + ".name", nameCache.getOrDefault(entry.getKey(), ""));
            cfg.set("friends." + uuidStr + ".list", entry.getValue().stream()
                    .map(UUID::toString).toList());
        }
        try {
            file.getParentFile().mkdirs();
            cfg.save(file);
        } catch (IOException e) {
            // cichy błąd - nie wyłącza pluginu
        }
    }
}
