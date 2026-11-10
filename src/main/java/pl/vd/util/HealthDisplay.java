package pl.vd.util;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;

public final class HealthDisplay {

    private static Attribute maxHealthAttribute;

    // styl wyświetlania HP: "bar" (kwadraciki █░ + liczby) lub "hearts" (serca ❤)
    private static String style = "bar";

    private HealthDisplay() {}

    /** Ustawia styl z configu: "bar" albo "hearts". Nieznane wartości ignoruje. */
    public static void setStyle(String newStyle) {
        if (newStyle != null && (newStyle.equalsIgnoreCase("bar") || newStyle.equalsIgnoreCase("hearts"))) {
            style = newStyle.toLowerCase();
        }
    }

    public static String getStyle() { return style; }

    private static Attribute maxHealthAttribute() {
        if (maxHealthAttribute == null) {
            maxHealthAttribute = Bukkit.getRegistry(Attribute.class).get(NamespacedKey.minecraft("max_health"));
        }
        return maxHealthAttribute;
    }

    /**
     * Aktualizuje nazwę moba, żeby nad głową pokazywać HP
     * (np. "§a[20/20]") albo serca (np. "§a❤ 20|20").
     */
    public static void update(LivingEntity entity) {
        double health = Math.max(0, entity.getHealth());

        double maxHealth;
        Attribute attr = maxHealthAttribute();
        if (attr != null && entity.getAttribute(attr) != null) {
            // bazowa wartość max HP (tę ustawiają ulepszenia fal w MobSpawnerze)
            maxHealth = entity.getAttribute(attr).getBaseValue();
        } else {
            // fallback: gdyby atrybut był niedostępny, nie pokazuj "pełnego" HP na stałe
            maxHealth = entity.getMaxHealth();
        }
        if (maxHealth <= 0) maxHealth = 1;

        int cur = (int) Math.ceil(health);
        int max = (int) Math.ceil(maxHealth);
        double ratio = health / maxHealth;

        // kolor liczby wg stanu HP:
        // zielony >= 70%, pomarańczowy 20-69%, czerwony < 20%
        String color = ratio >= 0.70 ? "§a" : (ratio >= 0.20 ? "§6" : "§c");

        if ("hearts".equalsIgnoreCase(style)) {
            entity.setCustomName(color + "❤ " + cur + "§7|" + color + max);
        } else {
            // kwadraciki (pasek wypełnienia) + liczby, np. "§a██████§7 [20/20]"
            int filled = Math.max(0, Math.min(6, (int) Math.round(ratio * 6)));
            String bar = color + "█".repeat(filled) + "§7" + "█".repeat(6 - filled);
            entity.setCustomName(bar + " §7[" + cur + "/" + max + "]");
        }
        entity.setCustomNameVisible(true);
    }
}
