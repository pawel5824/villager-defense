package pl.vd.kits;

import org.bukkit.Material;

import java.util.Locale;

/**
 * Kolejność zbroi w sklepie: skóra (0) → miedź → złoto → chainmail → żelazo
 * → diament → netheryt. Jedno miejsce, z którego korzysta parser klas i sklep.
 */
public final class ArmorTier {

    /** Brak limitu w class-rules.yml (domyślnie dla starych plików). */
    public static final int NO_LIMIT = -1;
    /** Wartość zwracana przez {@link #parse(String)} dla nieznanej nazwy. */
    public static final int UNKNOWN = -2;
    /** Skóra lub materiał, który nie jest zbroją tierową. */
    public static final int NONE = 0;

    public static final int COPPER = 1;
    public static final int GOLD = 2;
    public static final int CHAINMAIL = 3;
    public static final int IRON = 4;
    public static final int DIAMOND = 5;
    public static final int NETHERITE = 6;

    private ArmorTier() {
    }

    /** Tier założonej/kupowanej części; 0 dla skóry i przedmiotów spoza zbroi. */
    public static int of(Material material) {
        if (material == null) return NONE;
        String name = material.name();
        if (!isArmor(name)) return NONE;
        if (name.startsWith("LEATHER_")) return NONE;
        if (name.startsWith("COPPER_")) return COPPER;
        if (name.startsWith("GOLDEN_")) return GOLD;
        if (name.startsWith("CHAINMAIL_")) return CHAINMAIL;
        if (name.startsWith("IRON_")) return IRON;
        if (name.startsWith("DIAMOND_")) return DIAMOND;
        if (name.startsWith("NETHERITE_")) return NETHERITE;
        return NONE;
    }

    /** Nazwa z class-rules.yml; akceptuje polskie i angielskie warianty. */
    public static int parse(String raw) {
        if (raw == null || raw.isBlank()) return NO_LIMIT;
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "leather", "skora", "skóra" -> NONE;
            case "copper", "miedz", "miedź" -> COPPER;
            case "gold", "golden", "zloto", "złoto" -> GOLD;
            case "chainmail", "kolczuga", "kolczasta" -> CHAINMAIL;
            case "iron", "zelazo", "żelazo" -> IRON;
            case "diamond", "diament" -> DIAMOND;
            case "netherite", "netheryt" -> NETHERITE;
            default -> UNKNOWN;
        };
    }

    public static String name(int tier) {
        return switch (tier) {
            case NO_LIMIT -> "bez limitu";
            case NONE -> "skóra";
            case COPPER -> "miedź";
            case GOLD -> "złoto";
            case CHAINMAIL -> "chainmail";
            case IRON -> "żelazo";
            case DIAMOND -> "diament";
            case NETHERITE -> "netheryt";
            default -> "nieznana";
        };
    }

    private static boolean isArmor(String name) {
        return name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS");
    }
}