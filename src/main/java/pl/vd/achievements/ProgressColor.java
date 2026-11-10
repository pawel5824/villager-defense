package pl.vd.achievements;

import org.bukkit.Material;

/**
 * Kolor szyby osiągnięcia wg procentu postępu:
 *   czerwona      0-33%   (początek)
 *   pomarańczowa  34-66%  (w połowie)
 *   jasnozielona  67-99%  (prawie ukończone)
 *   zielona       100%    (ukończone)
 */
public enum ProgressColor {

    RED(0, 33, Material.RED_STAINED_GLASS_PANE),
    ORANGE(34, 66, Material.ORANGE_STAINED_GLASS_PANE),
    LIME(67, 99, Material.LIME_STAINED_GLASS_PANE),
    GREEN(100, 100, Material.GREEN_STAINED_GLASS_PANE);

    private final int min;
    private final int max;
    private final Material material;

    ProgressColor(int min, int max, Material material) {
        this.min = min;
        this.max = max;
        this.material = material;
    }

    /** Materiał szyby dla danego procentu postępu (0-100, wartości spoza zakresu są przycinane). */
    public static Material paneForPercent(int percent) {
        percent = Math.max(0, Math.min(100, percent));
        for (ProgressColor c : values()) {
            if (percent >= c.min && percent <= c.max) return c.material;
        }
        return RED.material;
    }
}
