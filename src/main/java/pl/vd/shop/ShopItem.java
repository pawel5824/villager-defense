package pl.vd.shop;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

public class ShopItem {

    private final String id;
    private final ShopItemType type;
    private final int price;

    // ITEM
    private Material material;
    private int amount = 1;
    /** Własna nazwa przedmiotu w sklepie (name w configu) - np. dla klas. */
    private String displayName;

    // Konkretny slot w GUI (-1 = automatyczne umieszczenie w kolejnym wolnym miejscu)
    private int slot = -1;

    // EFFECT
    private PotionEffectType effectType;
    private int amplifier;

    // ENCHANT
    private Enchantment enchantment;
    private int enchantLevel;
    /** Limit poziomu tego zaklęcia w sklepie (0 = domyślny globalny MAX_ENCHANT_LEVEL). */
    private int maxLevel;

    // POTION (zwykła, waniliowa mikstura; material może nadpisać np. TIPPED_ARROW)
    private PotionType potionType;
    private boolean splash;

    // FIREWORK (fajerwerk - wysokość lotu)
    private int power = 1;

    public ShopItem(String id, ShopItemType type, int price) {
        this.id = id;
        this.type = type;
        this.price = price;
    }

    public String getId() { return id; }
    public ShopItemType getType() { return type; }
    public int getPrice() { return price; }

    public Material getMaterial() { return material; }
    public void setMaterial(Material material) { this.material = material; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public int getAmount() { return amount; }
    public void setAmount(int amount) { this.amount = amount; }

    public int getSlot() { return slot; }
    public void setSlot(int slot) { this.slot = slot; }

    public PotionEffectType getEffectType() { return effectType; }
    public void setEffectType(PotionEffectType effectType) { this.effectType = effectType; }

    public int getAmplifier() { return amplifier; }
    public void setAmplifier(int amplifier) { this.amplifier = amplifier; }

    public Enchantment getEnchantment() { return enchantment; }
    public void setEnchantment(Enchantment enchantment) { this.enchantment = enchantment; }

    public int getEnchantLevel() { return enchantLevel; }
    public void setEnchantLevel(int enchantLevel) { this.enchantLevel = enchantLevel; }

    public int getMaxLevel() { return maxLevel; }
    public void setMaxLevel(int maxLevel) { this.maxLevel = maxLevel; }

    public PotionType getPotionType() { return potionType; }
    public void setPotionType(PotionType potionType) { this.potionType = potionType; }

    public boolean isSplash() { return splash; }
    public void setSplash(boolean splash) { this.splash = splash; }

    public int getPower() { return power; }
    public void setPower(int power) { this.power = power; }

    /** Materiał używany jako ikona w GUI (dla EFFECT/ENCHANT to nie to samo co "material" transakcji). */
    public Material getIconMaterial() {
        return switch (type) {
            case ITEM -> material != null ? material : Material.STONE;
            case EFFECT -> Material.POTION;
            case ENCHANT -> Material.ENCHANTED_BOOK;
            case MINE -> Material.SULFUR_CUBE_SPAWN_EGG;
            case TURRET -> Material.ARMOR_STAND;
            case POTION -> Material.POTION;
            case FIREWORK -> Material.FIREWORK_ROCKET;
        };
    }
}
