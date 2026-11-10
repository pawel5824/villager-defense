package pl.vd.raft.items;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;

/**
 * Reprezentuje baterię jako item w grze.
 * Każda bateria ma określoną ilość energii.
 */
public class BateriaItem {

    // Typy baterii
    public enum BateriaType {
        MALA("Bateria mała", 50, 6001, "§e"),
        SREDNIA("Bateria średnia", 175, 6002, "§6"),
        DUZA("Bateria duża", 325, 6003, "§c");

        private final String displayName;
        private final int maxEnergy;
        private final int customModelData;
        private final String color;

        BateriaType(String displayName, int maxEnergy, int customModelData, String color) {
            this.displayName = displayName;
            this.maxEnergy = maxEnergy;
            this.customModelData = customModelData;
            this.color = color;
        }

        public String getDisplayName() { return displayName; }
        public int getMaxEnergy() { return maxEnergy; }
        public int getCustomModelData() { return customModelData; }
        public String getColor() { return color; }
    }

    private final BateriaType type;
    private int currentEnergy;

    public BateriaItem(BateriaType type) {
        this.type = type;
        this.currentEnergy = type.getMaxEnergy();
    }

    public BateriaItem(BateriaType type, int currentEnergy) {
        this.type = type;
        this.currentEnergy = Math.min(currentEnergy, type.getMaxEnergy());
    }

    /**
     * Tworzy ItemStack reprezentujący baterię.
     * Zapisuje aktualny stan energii w PersistentDataContainer.
     */
    public ItemStack toItemStack() {
        ItemStack item = new ItemStack(Material.COPPER_INGOT);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(type.getColor() + type.getDisplayName());
        meta.setCustomModelData(type.getCustomModelData());
        int percent = type.getMaxEnergy() > 0 ? (int) ((double) currentEnergy * 100 / type.getMaxEnergy()) : 0;
        String pctColor = percent >= 75 ? "§a" : percent >= 50 ? "§e" : percent >= 25 ? "§6" : "§c";
        meta.setLore(Arrays.asList(
            pctColor + percent + "%"
        ));
        
        // Zapisz energię w PersistentDataContainer
        JavaPlugin plugin = getPlugin();
        if (plugin != null) {
            NamespacedKey energyKey = new NamespacedKey(plugin, "battery_energy");
            meta.getPersistentDataContainer().set(energyKey, PersistentDataType.INTEGER, currentEnergy);
        }

        item.setItemMeta(meta);
        return item;
    }
    
    /**
     * Pobiera plugin do PersistentDataContainer.
     */
    private static JavaPlugin getPlugin() {
        for (org.bukkit.plugin.Plugin p : org.bukkit.Bukkit.getPluginManager().getPlugins()) {
            if (p instanceof JavaPlugin javaPlugin) {
                if (javaPlugin instanceof pl.vd.VillagerDefensePlugin) {
                    return javaPlugin;
                }
            }
        }
        return null;
    }

    /**
     * Sprawdza czy item jest baterią.
     */
    public static boolean isBateria(ItemStack item) {
        if (item == null || item.getType() != Material.COPPER_INGOT) return false;
        if (!item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasCustomModelData()) return false;

        int cmd = meta.getCustomModelData();
        return cmd >= 6001 && cmd <= 6003;
    }

    /**
     * Pobiera typ baterii z itemu.
     */
    public static BateriaType getTypeFromItem(ItemStack item) {
        if (!isBateria(item)) return null;
        int cmd = item.getItemMeta().getCustomModelData();
        return switch (cmd) {
            case 6001 -> BateriaType.MALA;
            case 6002 -> BateriaType.SREDNIA;
            case 6003 -> BateriaType.DUZA;
            default -> null;
        };
    }

    /**
     * Tworzy baterię z itemu.
     * Odczytuje zapisany stan energii z PersistentDataContainer.
     */
    public static BateriaItem fromItemStack(ItemStack item) {
        BateriaType type = getTypeFromItem(item);
        if (type == null) return null;
        
        // Spróbuj odczytać zapisaną energię
        JavaPlugin plugin = getPlugin();
        if (plugin != null && item.hasItemMeta()) {
            NamespacedKey energyKey = new NamespacedKey(plugin, "battery_energy");
            PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
            if (container.has(energyKey, PersistentDataType.INTEGER)) {
                int savedEnergy = container.getOrDefault(energyKey, PersistentDataType.INTEGER, type.getMaxEnergy());
                return new BateriaItem(type, savedEnergy);
            }
        }
        
        // Fallback - pełna energia
        return new BateriaItem(type);
    }

    // Gettery
    public BateriaType getType() { return type; }
    public int getCurrentEnergy() { return currentEnergy; }
    public int getMaxEnergy() { return type.getMaxEnergy(); }
    public boolean hasEnergy() { return currentEnergy > 0; }
    
    // Setter dla energii
    public void setCurrentEnergy(int energy) {
        this.currentEnergy = Math.min(energy, type.getMaxEnergy());
    }

    // Zużycie energii
    public boolean useEnergy(int amount) {
        if (currentEnergy < amount) return false;
        currentEnergy -= amount;
        return true;
    }

    // Dodawanie energii
    public void addEnergy(int amount) {
        currentEnergy = Math.min(currentEnergy + amount, type.getMaxEnergy());
    }
}
