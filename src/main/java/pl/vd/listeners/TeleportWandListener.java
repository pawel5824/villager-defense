package pl.vd.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import pl.vd.VillagerDefensePlugin;

public class TeleportWandListener implements Listener {

    private final VillagerDefensePlugin plugin;

    public TeleportWandListener(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) return;
        Player player = event.getPlayer();
        if (!player.hasPermission("vd.admin")) return;
        if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR) return; // duch niczego nie używa
        if (!plugin.getTeleportWandManager().isWand(player.getInventory().getItemInMainHand())) return;
        event.setCancelled(true);
        plugin.getTeleportWandManager().teleportSelf(player, player.isSneaking());
    }

    @EventHandler
    public void onHitPlayer(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player actor)) return;
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!actor.hasPermission("vd.admin")) return;
        if (actor.getGameMode() == org.bukkit.GameMode.SPECTATOR) return; // duch niczego nie używa
        if (!plugin.getTeleportWandManager().isWand(actor.getInventory().getItemInMainHand())) return;
        event.setCancelled(true);
        plugin.getTeleportWandManager().teleportPlayer(actor, victim);
    }
}
