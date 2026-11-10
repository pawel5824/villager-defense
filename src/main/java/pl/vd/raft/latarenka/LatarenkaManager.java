package pl.vd.raft.latarenka;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import pl.vd.VillagerDefensePlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Zarządza presetami customowych latarenek.
 * Pliki YAML w plugins/ServerPlugin/latarenki/<nazwa>.yml
 *
 * Format YAML:
 * <pre>
 * display-name: "&dLatarenka fioletowa"
 * block: "minecraft:light"
 * light-level: 15
 * model-block: "minecraft:barrel"     # blok używany jako model w block_display
 * model-texture: "custom:lantern_fioletowa"  # tekstura (resource pack)
 * </pre>
 */
public class LatarenkaManager {

    private final VillagerDefensePlugin plugin;
    private final File folder;
    private final NamespacedKey keyLatarenka;

    /** Cache presetów (nazwa -> YAML). */
    private final Map<String, YamlConfiguration> cache = new HashMap<>();

    public LatarenkaManager(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "latarenki");
        this.keyLatarenka = new NamespacedKey(plugin, "latarenka");

        if (!folder.exists()) {
            folder.mkdirs();
            createDefaultPresets();
        }
    }

    /** Tworzy domyślne przykładowe presety. */
    private void createDefaultPresets() {
        saveDefault("fioletowa", "&dLatarenka fioletowa", 15);
        saveDefault("czerwona", "&cLatarenka czerwona", 15);
        saveDefault("zielona", "&aLatarenka zielona", 15);
        saveDefault("niebieska", "&9Latarenka niebieska", 15);
        saveDefault("zolta", "&eLatarenka żółta", 15);
        saveDefault("biala", "&fLatarenka biała", 15);
        saveDefault("pomaranczowa", "&6Latarenka pomarańczowa", 15);
        saveDefault("rozowa", "&dLatarenka różowa", 15);
        plugin.getLogger().info("[Latarenka] Utworzono " + listPresets().size() + " domyślnych presetów w latarenki/");
    }

    private void saveDefault(String name, String displayName, int lightLevel) {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("display-name", displayName);
        cfg.set("block", "minecraft:light");
        cfg.set("light-level", lightLevel);
        cfg.set("model-block", "minecraft:barrel");
        cfg.set("model-texture", "custom:lantern_" + name);
        try {
            cfg.save(new File(folder, name + ".yml"));
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "[Latarenka] Nie udało się zapisać presetu " + name, e);
        }
    }

    /** Ładuje preset z pliku (z cache). */
    public YamlConfiguration loadPreset(String name) {
        if (cache.containsKey(name)) return cache.get(name);
        File file = new File(folder, sanitize(name) + ".yml");
        if (!file.exists()) return null;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        cache.put(name, cfg);
        return cfg;
    }

    /** Lista nazw wszystkich presetów. */
    public List<String> listPresets() {
        List<String> names = new ArrayList<>();
        File[] files = folder.listFiles((dir, n) -> n.endsWith(".yml"));
        if (files != null) {
            for (File f : files) {
                names.add(f.getName().replaceFirst("\\.yml$", ""));
            }
        }
        Collections.sort(names);
        return names;
    }

    /** Wyświetlana nazwa presetu. */
    public String getDisplayName(String name) {
        YamlConfiguration cfg = loadPreset(name);
        if (cfg == null) return name;
        return cfg.getString("display-name", name);
    }

    /**
     * Stawia customową latarenkę w świecie Raft.
     * Używa block_display entity + light block do oświetlenia.
     */
    public boolean placeLatarenka(String name, Player player) {
        YamlConfiguration cfg = loadPreset(name);
        if (cfg == null) return false;

        // Znajdź blok na który patrzy gracz
        Block target = player.getTargetBlockExact(100);
        if (target == null) {
            target = player.getLocation().getBlock().getRelative(player.getFacing());
        }

        Location loc = target.getLocation().add(0.5, 0.5, 0.5);

        // 1. Postaw niewidoczny blok light (oświetlenie vanilla)
        Block lightBlock = target.getRelative(0, 1, 0);
        if (lightBlock.getType() == Material.AIR) {
            int lightLevel = cfg.getInt("light-level", 15);
            try {
                BlockData lightData = createLightBlockData(lightLevel);
                if (lightData != null) {
                    lightBlock.setBlockData(lightData);
                }
            } catch (Exception e) {
                // light block może nie być dostępny na wszystkich wersjach
                plugin.getLogger().fine("[Latarenka] light block niedostępny: " + e.getMessage());
            }
        }

        // 2. Spawn block_display entity z custom modelem
        Location spawnLoc = target.getLocation().add(0.5, 1.0, 0.5);
        BlockDisplay display = (BlockDisplay) target.getWorld().spawnEntity(spawnLoc, EntityType.BLOCK_DISPLAY);

        // Ustaw model bloku
        String modelBlock = cfg.getString("model-block", "minecraft:barrel");
        try {
            Material mat = Material.matchMaterial(modelBlock);
            if (mat != null) {
                display.setBlock(mat.createBlockData());
            } else {
                display.setBlock(Material.BARRIER.createBlockData());
            }
        } catch (Exception e) {
            display.setBlock(Material.BARRIER.createBlockData());
        }

        // Skaluj do rozmiaru latarenki (0.5 bloku)
        display.setTransformation(new Transformation(
                new Vector3f(-0.25f, -0.25f, -0.25f), // translation
                new AxisAngle4f(0, 0, 0, 1),          // left rotation
                new Vector3f(0.5f, 0.5f, 0.5f),       // scale
                new AxisAngle4f(0, 0, 0, 1)           // right rotation
        ));

        // Oznacz entity jako latarenkę pluginu (do sprzątania)
        PersistentDataContainer pdc = display.getPersistentDataContainer();
        pdc.set(keyLatarenka, PersistentDataType.STRING, name);

        // Nazwa nad latarenką
        display.customName(net.kyori.adventure.text.Component.text(getDisplayName(name)));

        // Niewidoczny, nieinteraktywny
        display.setViewRange(0.0f);
        display.setInvulnerable(true);
        display.setPersistent(true);

        return true;
    }

    /**
     * Stawia latarenkę na konkretnym bloku (używane przez listener PPM).
     */
    public boolean placeLatarenkaAt(String name, Block target, Player player) {
        YamlConfiguration cfg = loadPreset(name);
        if (cfg == null) return false;

        // 1. Postaw niewidoczny blok light (oświetlenie vanilla)
        Block lightBlock = target.getRelative(0, 1, 0);
        if (lightBlock.getType() == Material.AIR) {
            int lightLevel = cfg.getInt("light-level", 15);
            try {
                BlockData lightData = createLightBlockData(lightLevel);
                if (lightData != null) {
                    lightBlock.setBlockData(lightData);
                }
            } catch (Exception e) {
                plugin.getLogger().fine("[Latarenka] light block niedostępny: " + e.getMessage());
            }
        }

        // 2. Spawn block_display entity z custom modelem
        Location spawnLoc = target.getLocation().add(0.5, 1.0, 0.5);
        BlockDisplay display = (BlockDisplay) target.getWorld().spawnEntity(spawnLoc, EntityType.BLOCK_DISPLAY);

        String modelBlock = cfg.getString("model-block", "minecraft:barrel");
        try {
            Material mat = Material.matchMaterial(modelBlock);
            if (mat != null) {
                display.setBlock(mat.createBlockData());
            } else {
                display.setBlock(Material.BARRIER.createBlockData());
            }
        } catch (Exception e) {
            display.setBlock(Material.BARRIER.createBlockData());
        }

        display.setTransformation(new Transformation(
                new Vector3f(-0.25f, -0.25f, -0.25f),
                new AxisAngle4f(0, 0, 0, 1),
                new Vector3f(0.5f, 0.5f, 0.5f),
                new AxisAngle4f(0, 0, 0, 1)
        ));

        PersistentDataContainer pdc = display.getPersistentDataContainer();
        pdc.set(keyLatarenka, PersistentDataType.STRING, name);

        display.customName(net.kyori.adventure.text.Component.text(getDisplayName(name)));
        display.setViewRange(0.0f);
        display.setInvulnerable(true);
        display.setPersistent(true);

        return true;
    }

    /** Tworzy BlockData dla light block z podanym poziomem światła. */
    private BlockData createLightBlockData(int level) {
        try {
            // Paper/1.19.4+ ma Material.LIGHT
            Material lightMat = Material.matchMaterial("minecraft:light");
            if (lightMat != null) {
                BlockData bd = lightMat.createBlockData();
                // Ustaw level przez reflection lub property
                // BlockData light_0, light_1, ... light_15 = alternative
                return org.bukkit.Bukkit.createBlockData(lightMat, "[level=" + Math.min(15, Math.max(0, level)) + "]");
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Usuwa latarenkę (BlockDisplay), na którą patrzy gracz.
     */
    public boolean removeLookedAt(Player player) {
        for (Entity e : player.getNearbyEntities(10, 10, 10)) {
            if (!(e instanceof BlockDisplay bd)) continue;
            PersistentDataContainer pdc = bd.getPersistentDataContainer();
            if (!pdc.has(keyLatarenka)) continue;

            Location pLoc = player.getEyeLocation();
            Location eLoc = bd.getLocation().add(0, 0.5, 0);
            double dist = pLoc.distance(eLoc);
            if (dist > 10) continue;

            org.bukkit.util.Vector toEntity = eLoc.toVector().subtract(pLoc.toVector()).normalize();
            org.bukkit.util.Vector lookDir = pLoc.getDirection().normalize();
            double dot = lookDir.dot(toEntity);

            if (dot > 0.95) {
                bd.remove();
                return true;
            }
        }
        return false;
    }

    /** Usuwa wszystkie latarenki pluginu z aktualnego chunka/obszaru gracza. */
    public int cleanupNearby(Player player, int radius) {
        int removed = 0;
        for (Entity e : player.getNearbyEntities(radius, radius, radius)) {
            if (e instanceof BlockDisplay bd) {
                PersistentDataContainer pdc = bd.getPersistentDataContainer();
                if (pdc.has(keyLatarenka)) {
                    bd.remove();
                    removed++;
                }
            }
        }
        return removed;
    }

    /** Usuwa wszystkie latarenki pluginu ze świata Raft. */
    public int cleanupAll(String raftWorldName) {
        org.bukkit.World world = org.bukkit.Bukkit.getWorld(raftWorldName);
        if (world == null) return 0;
        int removed = 0;
        for (Entity e : world.getEntitiesByClass(BlockDisplay.class)) {
            PersistentDataContainer pdc = e.getPersistentDataContainer();
            if (pdc.has(keyLatarenka)) {
                e.remove();
                removed++;
            }
        }
        return removed;
    }

    /** Klucz PDC do oznaczania przedmiotów-latarenek. */
    public NamespacedKey getKeyLatarenka() { return keyLatarenka; }

    /**
     * Tworzy przedmiot armor_stand z nazwą presetu latarenki.
     * Gracz może postawić latarenkę prawym kliknięciem.
     */
    public org.bukkit.inventory.ItemStack createLatarenkaItem(String presetName) {
        YamlConfiguration cfg = loadPreset(presetName);
        if (cfg == null) return null;

        org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(Material.ARMOR_STAND);
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        meta.displayName(net.kyori.adventure.text.Component.text("§6✦ " + getDisplayName(presetName) + " §7(PPM = postaw)"));

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keyLatarenka, PersistentDataType.STRING, presetName);

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Sprawdza czy przedmiot to latarenka pluginu i zwraca nazwę presetu.
     */
    public String getPresetFromItem(org.bukkit.inventory.ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        if (pdc.has(keyLatarenka, PersistentDataType.STRING)) {
            return pdc.get(keyLatarenka, PersistentDataType.STRING);
        }
        return null;
    }

    public File getFolder() { return folder; }

    private static String sanitize(String name) {
        return name.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
    }
}
