package pl.vd.shop;

import org.bukkit.Bukkit;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public class ShopHolder implements InventoryHolder {

    public enum ViewType { MAIN, CATEGORY, CUSTOM_ARROWS, ENCHANT_TARGET, TURRET_UPGRADE, FOOD_BULK, SETTINGS, SOUND_SETTINGS, PARTICLE_SETTINGS, SUPPLY_SOUNDS, NOTIFICATION_SETTINGS, VISIBILITY_SETTINGS, FLOOR_SETTINGS, LANG_SETTINGS, KIT_SLOTS_SETTINGS, KIT_SLOTS_EDIT, COMPASS_SETTINGS }

    private final ViewType viewType;
    private final ShopCategory category; // null jeśli MAIN
    private final ShopItem selectedItem; // wybrane zaklęcie dla ENCHANT_TARGET
    private final int page;              // numer strony kategorii (0 = pierwsza)
    /** UUID wieżyczki, dla której otwarto panel ulepszeń (do podmiany przy kliku). */
    private String turretUuid;
    /** ID klasy, dla której otwarto GUI układania przedmiotów (KIT_SLOTS_EDIT). */
    private String kitId;
    private Inventory inventory;

    public ShopHolder(ViewType viewType, ShopCategory category) {
        this(viewType, category, null, 0);
    }

    public ShopHolder(ViewType viewType, ShopCategory category, ShopItem selectedItem) {
        this(viewType, category, selectedItem, 0);
    }

    public ShopHolder(ViewType viewType, ShopCategory category, ShopItem selectedItem, int page) {
        this.viewType = viewType;
        this.category = category;
        this.selectedItem = selectedItem;
        this.page = page;
    }

    public void setInventory(Inventory inventory) { this.inventory = inventory; }

    public void setTurretUuid(String turretUuid) { this.turretUuid = turretUuid; }

    public void setKitId(String kitId) { this.kitId = kitId; }
    public String getKitId() { return kitId; }

    /** Wieżyczka, dla której otwarto panel ulepszeń (null, gdy już zniknęła). */
    public ArmorStand getTurretStand() {
        if (turretUuid == null) return null;
        try {
            Entity e = Bukkit.getEntity(UUID.fromString(turretUuid));
            return e instanceof ArmorStand s ? s : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public ViewType getViewType() { return viewType; }
    public ShopCategory getCategory() { return category; }
    public ShopItem getSelectedItem() { return selectedItem; }
    public int getPage() { return page; }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
