package pl.vd.listeners;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Zamienia wypadające z kury jajka na niebieskie jajka.
 * Kura zamiast zwykłego jajka wypuszcza niebieskie jajko (egg z niebieską nazwą).
 */
public class ChickenEggListener implements Listener {

    @EventHandler
    public void onChickenDropEgg(EntityDropItemEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Chicken)) return;

        Item dropped = event.getItemDrop();
        if (dropped.getItemStack().getType() != Material.EGG) return;

        // Anuluj zwykłe jajko
        event.setCancelled(true);

        // Stwórz niebieskie jajko
        ItemStack blueEgg = new ItemStack(Material.EGG, 1);
        ItemMeta meta = blueEgg.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "Niebieskie Jajko");
            meta.setLore(java.util.Collections.singletonList(ChatColor.GRAY + "Jajko z niebieską skorupką"));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            blueEgg.setItemMeta(meta);
        }

        // Upuść niebieskie jajko w tym samym miejscu
        entity.getWorld().dropItemNaturally(dropped.getLocation(), blueEgg);
    }
}
