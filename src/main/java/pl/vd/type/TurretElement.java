package pl.vd.type;

import org.bukkit.Material;

/**
 * Żywioł wieżyczki maga. W jednym miejscu trzyma wszystko, co odróżnia
 * warianty: ikonę w GUI ulepszeń, nazwę, zapis stojaka (preset) i cenę.
 * Pod przyszłe plany dźwiękowe wystarczy dodać tu pola (np. dźwięk strzału),
 * a całe GUI i obsługa zakupów zadziałają bez zmian.
 */
public enum TurretElement {

    ICE("§bWieża Lodu", "wiezyczka_maga_lod", 220, Material.PACKED_ICE),
    FIRE("§cWieża Ognia", "wiezyczka_maga_ogien", 400, Material.MAGMA_BLOCK),
    POISON("§aWieża Trucizny", "wiezyczka_maga_trutka", 280, Material.EMERALD_BLOCK),
    ELECTRIC("§eWieża Elektryczna", "wiezyczka_maga_elektryk", 340, Material.GLOWSTONE);

    private final String name;
    private final String preset;
    private final int price;
    private final Material material;

    TurretElement(String name, String preset, int price, Material material) {
        this.name = name;
        this.preset = preset;
        this.price = price;
        this.material = material;
    }

    public String getName() {
        return name;
    }

    /** Nazwa zapisu stojaka (plik w stojaki/*.yml). */
    public String getPreset() {
        return preset;
    }

    /** Cena w punktach sesji. */
    public int getPrice() {
        return price;
    }

    public Material getMaterial() {
        return material;
    }

    /** Żywioł dla nazwy zapisu stojaka, albo null, gdy to nie wieżyczka maga. */
    public static TurretElement fromPreset(String preset) {
        if (preset == null) return null;
        for (TurretElement e : values()) {
            if (e.preset.equals(preset)) return e;
        }
        return null;
    }
}
