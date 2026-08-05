package pl.vd.kits;

import org.bukkit.inventory.ItemStack;

import java.util.List;

public class Kit {

    private final String id;
    private final String displayName;
    private final ItemStack icon;
    private final List<ItemStack> items;
    private final List<ItemStack> refillItems;
    private final ItemStack helmet, chestplate, leggings, boots;

    public Kit(String id, String displayName, ItemStack icon, List<ItemStack> items,
               List<ItemStack> refillItems, ItemStack helmet,
               ItemStack chestplate, ItemStack leggings, ItemStack boots) {
        this.id = id;
        this.displayName = displayName;
        this.icon = icon;
        this.items = items;
        this.refillItems = refillItems != null ? refillItems : List.of();
        this.helmet = helmet;
        this.chestplate = chestplate;
        this.leggings = leggings;
        this.boots = boots;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public ItemStack getIcon() { return icon; }
    public List<ItemStack> getItems() { return items; }
    public List<ItemStack> getRefillItems() { return refillItems; }
    public ItemStack getHelmet() { return helmet; }
    public ItemStack getChestplate() { return chestplate; }
    public ItemStack getLeggings() { return leggings; }
    public ItemStack getBoots() { return boots; }
}
