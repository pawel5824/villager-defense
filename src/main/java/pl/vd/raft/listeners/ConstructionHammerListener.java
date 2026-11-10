package pl.vd.raft.listeners;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Blokuje obrażenia z Młota budowlanego (Mace).
 * Młot budowlany służy tylko do budowy, nie do atakowania.
 * Rozpoznanie przez ItemManager.isConstructionHammer (CMD 4002 lub nazwa) —
 * odporne na stare egzemplarze i drobne różnice meta.
 */
public class ConstructionHammerListener implements Listener {

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() != Material.MACE) return;
        
        pl.vd.raft.items.ItemManager im = pl.vd.raft.RaftPlugin.getInstance().getItemManager();
        if (im == null || !im.isConstructionHammer(item)) return;
        
        // Blokuj obrażenia z młota budowlanego
        event.setCancelled(true);
    }
}
