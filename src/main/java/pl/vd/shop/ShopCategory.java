package pl.vd.shop;

import org.bukkit.Material;
import org.bukkit.entity.Player;

public enum ShopCategory {
    FOOD("food", Material.COOKED_BEEF, "§6Jedzenie"),
    SWORDS("swords", findMaterial("GOLDEN_SWORD", "GOLD_SWORD", "IRON_SWORD"), "§cBronie"),
    BOWS("bows", Material.SPECTRAL_ARROW, "§aStrzały"),
    ARMOR("armor", Material.IRON_CHESTPLATE, "§7Zbroje"),
    POTIONS("potions", Material.POTION, "§dMikstury"),
    OTHER("other", Material.ENDER_PEARL, "§5Inne"),
    EFFECTS("effects", Material.BEACON, "§eStałe efekty"),
    ENCHANTS("enchants", Material.BOOK, "§bZaklęcia");

    private final String configKey;
    private final Material icon;
    private final String displayName;

    ShopCategory(String configKey, Material icon, String displayName) {
        this.configKey = configKey;
        this.icon = icon;
        this.displayName = displayName;
    }

    public String getConfigKey() { return configKey; }
    public Material getIcon() { return icon; }
    public String getDisplayName() { return displayName; }

    /** Nazwa kategorii w języku gracza (panel Ustawienia -> Język). */
    public String localizedName(Player player) {
        return pl.vd.util.Lang.get(player, "shop-cat-" + configKey);
    }

    private static Material findMaterial(String... names) {
        for (String name : names) {
            Material material = Material.matchMaterial(name);
            if (material != null) return material;
        }
        return Material.IRON_SWORD;
    }
}
