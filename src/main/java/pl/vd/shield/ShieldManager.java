package pl.vd.shield;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import pl.vd.VillagerDefensePlugin;

import java.util.List;

public class ShieldManager {

    /** O ile mocniejsze odepchnięcie w porównaniu do zwykłego blokowania (mnożnik). */
    public static final double KNOCKBACK_STRENGTH = 1.2;
    public static final double KNOCKBACK_VERTICAL = 0.40;

    private final NamespacedKey shieldKey;

    public ShieldManager(VillagerDefensePlugin plugin) {
        this.shieldKey = new NamespacedKey(plugin, "knockback_shield");
    }

    public ItemStack createKnockbackShield() {
        ItemStack item = new ItemStack(Material.SHIELD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§9Tank"); // nazwa kitu w kolorze kitu
        // bez lore - ma zostać tylko nazwa i „Niezniszczalne”
        meta.getPersistentDataContainer().set(shieldKey, PersistentDataType.BYTE, (byte) 1);
        meta.setUnbreakable(true);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isKnockbackShield(ItemStack item) {
        if (item == null || item.getType() != Material.SHIELD || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(shieldKey, PersistentDataType.BYTE);
    }
}
