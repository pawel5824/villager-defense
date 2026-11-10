package pl.vd.type;

import org.bukkit.Material;

import java.util.EnumSet;
import java.util.Set;

/**
 * Rodzaj broni rozpoznawany w GUI zaklęć (i wszędzie, gdzie trzeba wiedzieć,
 * czy przedmiot to miecz/siekiera/broń dystansowa). Zamiast trzech osobnych
 * zbiorów materiałów pytamy: {@code WeaponType.of(materiał)} albo
 * {@code WeaponType.SWORD.matches(materiał)}.
 *
 * Materiały budowane przez matchMaterial, więc brakujące w danej wersji MC
 * są pomijane bez błędu (jak w starych zbiorach SWORD/AXE/BOW_MATERIALS).
 */
public enum WeaponType {

    SWORD("Miecz",
            "WOODEN_SWORD", "STONE_SWORD", "COPPER_SWORD", "IRON_SWORD",
            "GOLDEN_SWORD", "DIAMOND_SWORD", "NETHERITE_SWORD"),
    AXE("Siekiera",
            "WOODEN_AXE", "STONE_AXE", "COPPER_AXE", "IRON_AXE",
            "DIAMOND_AXE"),
    RANGED("Broń dystansowa",
            "BOW", "CROSSBOW", "TRIDENT");

    private final String displayName;
    private final Set<Material> materials = EnumSet.noneOf(Material.class);

    WeaponType(String displayName, String... materialNames) {
        this.displayName = displayName;
        for (String name : materialNames) {
            Material m = Material.matchMaterial(name);
            if (m != null) materials.add(m);
        }
    }

    /** Nazwa pokazywana w GUI (np. „Miecz", „Broń dystansowa"). */
    public String getDisplayName() {
        return displayName;
    }

    /** Czy ten materiał to broń tego typu. */
    public boolean matches(Material m) {
        return m != null && materials.contains(m);
    }

    /** Typ broni dla materiału, albo null, gdy to nie broń. */
    public static WeaponType of(Material m) {
        if (m == null) return null;
        for (WeaponType t : values()) {
            if (t.materials.contains(m)) return t;
        }
        return null;
    }
}
