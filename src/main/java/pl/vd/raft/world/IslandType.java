package pl.vd.raft.world;

/**
 * Typy wysp w świecie Raft.
 */
public enum IslandType {
    // Wyspy başowe
    SANDY_ISLAND("Sandy Island", 50, 20, 0.3),
    ROCKY_ISLAND("Rocky Island", 40, 30, 0.2),
    FOREST_ISLAND("Forest Island", 60, 25, 0.25),
    MOUNTAIN_ISLAND("Mountain Island", 35, 40, 0.15),
    VOLCANIC_ISLAND("Volcanic Island", 30, 50, 0.1),

    // Specjalne wyspy
    RUINS_ISLAND("Ancient Ruins", 45, 20, 0.2),
    TREASURE_ISLAND("Treasure Island", 25, 15, 0.35),
    MONSTER_ISLAND("Monster Island", 55, 35, 0.1),
    MYSTIC_ISLAND("Mystic Island", 40, 25, 0.2),
    WRECK_ISLAND("Shipwreck Island", 35, 20, 0.25),

    // BOSS wyspy
    DRAGON_ISLAND("Dragon's Lair", 70, 45, 0.05),
    PIRATE_ISLAND("Pirate Fortress", 60, 40, 0.08),
    UNDEAD_ISLAND("Undead Island", 50, 35, 0.12);

    private final String defaultName;
    private final int defaultRadius;
    private final int defaultHeight;
    private final double rarity;

    IslandType(String defaultName, int defaultRadius, int defaultHeight, double rarity) {
        this.defaultName = defaultName;
        this.defaultRadius = defaultRadius;
        this.defaultHeight = defaultHeight;
        this.rarity = rarity;
    }

    public String getDefaultName() { return defaultName; }
    public int getDefaultRadius() { return defaultRadius; }
    public int getDefaultHeight() { return defaultHeight; }
    public double getRarity() { return rarity; }

    /**
     * Losuje typ wyspy na podstawie rzadkości.
     */
    public static IslandType randomType() {
        double random = Math.random();
        double cumulative = 0.0;

        for (IslandType type : values()) {
            cumulative += type.rarity;
            if (random <= cumulative) {
                return type;
            }
        }
        return SANDY_ISLAND; // Fallback
    }
}
