package pl.vd.achievements;

import org.bukkit.Material;

/** Kategorie osiągnięć (ikony w górnym rzędzie huba). */
public enum AchievementCategory {

    WALKA(0, "ach-cat-walka", Material.DIAMOND_SWORD),                     // w grze slot 1
    OBRONA(1, "ach-cat-obrona", Material.SHIELD),                          // w grze slot 2
    PRZETRWANIE(2, "ach-cat-przetrwanie", Material.POTION),                // w grze slot 3 (mikstura leczenia)
    EKONOMIA(3, "ach-cat-ekonomia", Material.COMPASS),                     // w grze slot 4
    KLASY(4, "ach-cat-klasy", Material.BOW),                               // w grze slot 5
    LUPY(5, "ach-cat-lupy", Material.WAXED_EXPOSED_COPPER_CHEST),          // w grze slot 6
    EKSPLORACJA(6, "ach-cat-eksploracja", Material.LEATHER_BOOTS),         // w grze slot 7
    SPOLECZNE(7, "ach-cat-spoleczne", Material.PLAYER_HEAD),               // w grze slot 8
    UKRYTE(8, "ach-cat-ukryte", Material.BARRIER);                         // w grze slot 9

    private final int slot;
    private final String nameKey;
    private final Material material;

    AchievementCategory(int slot, String nameKey, Material material) {
        this.slot = slot;
        this.nameKey = nameKey;
        this.material = material;
    }

    public int getSlot() { return slot; }
    public String getNameKey() { return nameKey; }
    public Material getMaterial() { return material; }

    /** Kategoria zajmująca dany slot w hubie (albo null, gdy slot nie jest kategorią). */
    public static AchievementCategory forSlot(int slot) {
        for (AchievementCategory c : values()) {
            if (c.slot == slot) return c;
        }
        return null;
    }
}
