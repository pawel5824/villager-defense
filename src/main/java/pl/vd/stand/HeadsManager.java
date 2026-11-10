package pl.vd.stand;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import pl.vd.VillagerDefensePlugin;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.*;

/**
 * Zarządza customowymi główkami (skullami) z teksturami base64.
 * Tekstury wczytywane z heads.yml (zasoby pluginu).
 *
 * Użycie w grze:
 *   /stojak funkcja głowka <nazwa>  — daje główkę do ekwipunku
 */
public class HeadsManager {

    private final Map<String, HeadData> heads = new LinkedHashMap<>();

    public HeadsManager(VillagerDefensePlugin plugin) {
        load(plugin);
    }

    /**
     * Zwraca główkę z podaną teksturą jako item.
     */
    public ItemStack getHead(String name) {
        HeadData data = heads.get(name.toLowerCase());
        if (data == null) return null;

        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta == null) return skull;

        meta.setDisplayName(data.displayName);

        // ustaw custom teksturę przez reflection (com.mojang.authlib.GameProfile
        // nie jest na compile classpath - tylko runtime)
        try {
            Object gameProfile = createGameProfile(data.texture);
            if (gameProfile != null) {
                Field profileField = meta.getClass().getDeclaredField("profile");
                profileField.setAccessible(true);
                profileField.set(meta, gameProfile);
            }
        } catch (Exception e) {
            meta.setLore(List.of("§7Tekstura: " + name));
        }

        skull.setItemMeta(meta);
        return skull;
    }

    public Set<String> getHeadNames() {
        return Collections.unmodifiableSet(heads.keySet());
    }

    public List<String> getMatchingNames(String partial) {
        List<String> matches = new ArrayList<>();
        for (String name : heads.keySet()) {
            if (name.toLowerCase().startsWith(partial.toLowerCase())) {
                matches.add(name);
            }
        }
        return matches;
    }

    public boolean hasHead(String name) {
        return heads.containsKey(name.toLowerCase());
    }

    // --- reflection: GameProfile + Property ---

    private Object createGameProfile(String base64Texture) throws Exception {
        // GameProfile(UUID, String)
        Class<?> gpClass = Class.forName("com.mojang.authlib.GameProfile");
        Constructor<?> gpCtor = gpClass.getConstructor(UUID.class, String.class);
        Object profile = gpCtor.newInstance(UUID.randomUUID(), "CustomHead");

        // Property("textures", base64)
        Class<?> propClass = Class.forName("com.mojang.authlib.properties.Property");
        Constructor<?> propCtor = propClass.getConstructor(String.class, String.class, String.class);
        Object property = propCtor.newInstance("textures", base64Texture, "");

        // profile.getProperties().put("textures", property)
        Field propsField = gpClass.getDeclaredField("properties");
        propsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) propsField.get(profile);
        props.put("textures", property);

        return profile;
    }

    // --- ładowanie ---

    private void load(VillagerDefensePlugin plugin) {
        File file = new File(plugin.getDataFolder(), "heads.yml");
        if (!file.exists()) {
            plugin.saveResource("heads.yml", false);
        }

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        for (String key : cfg.getKeys(false)) {
            ConfigurationSection section = cfg.getConfigurationSection(key);
            if (section == null) continue;
            String displayName = section.getString("name", "§f" + key);
            String texture = section.getString("texture", "");
            if (!texture.isEmpty()) {
                heads.put(key.toLowerCase(), new HeadData(displayName, texture));
            }
        }
    }

    private static class HeadData {
        final String displayName;
        final String texture;

        HeadData(String displayName, String texture) {
            this.displayName = displayName;
            this.texture = texture;
        }
    }
}
