package pl.vd.achievements;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Holder inventarza GUI osiągnięć - rozpoznaje kliknięcia w AchievementsListener. */
public class AchievementsHolder implements InventoryHolder {

    public enum View { HUB, CATEGORY }

    /** Skąd otwarto GUI: przycisk na dole huba działa inaczej (Powrót / Zamknij). */
    public enum Source { SHOP, COMMAND }

    private final View view;
    private final Source source;
    private final AchievementCategory category; // null dla widoku HUB
    private int scrollOffset = 0; // o ile osiągnięć przesunięto widok (przewijanie strzałkami)
    private Inventory inv;

    public AchievementsHolder(View view, Source source) {
        this(view, source, null);
    }

    public AchievementsHolder(View view, Source source, AchievementCategory category) {
        this.view = view;
        this.source = source;
        this.category = category;
    }

    public View getView() { return view; }

    public Source getSource() { return source; }

    public AchievementCategory getCategory() { return category; }

    public int getScrollOffset() { return scrollOffset; }
    public void setScrollOffset(int scrollOffset) { this.scrollOffset = scrollOffset; }

    public void setInventory(Inventory inv) { this.inv = inv; }

    @Override
    public Inventory getInventory() { return inv; }
}
