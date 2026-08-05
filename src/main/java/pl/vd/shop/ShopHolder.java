package pl.vd.shop;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class ShopHolder implements InventoryHolder {

    public enum ViewType { MAIN, CATEGORY }

    private final ViewType viewType;
    private final ShopCategory category; // null jeśli MAIN
    private Inventory inventory;

    public ShopHolder(ViewType viewType, ShopCategory category) {
        this.viewType = viewType;
        this.category = category;
    }

    public void setInventory(Inventory inventory) { this.inventory = inventory; }

    public ViewType getViewType() { return viewType; }
    public ShopCategory getCategory() { return category; }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
