package pl.vd.listeners;

import com.destroystokyo.paper.event.player.PlayerStartSpectatingEntityEvent;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import pl.vd.VillagerDefensePlugin;

/**
 * Tryb SPECTATOR (duch po śmierci na arenie, a także każdy spectator) to tryb
 * oglądania - NIE wolno w nim niczego używać: żadnych umiejętności (różdżka
 * magika itd.), żadnych interakcji z mobami/blokami/encjami, żadnych obrażeń.
 * Dodatkowo blokowane jest WCHODZENIE w moby (kliknięcie PPM na moba jako
 * oglądanie z jego perspektywy) - spectator nie może "wsiąść" w moba.
 */
public class SpectatorGuardListener implements Listener {

    private final VillagerDefensePlugin plugin;

    public SpectatorGuardListener(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
    }

    /** Spectator nie wchodzi w moby/encje (oglądanie z ich perspektywy). */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onStartSpectating(PlayerStartSpectatingEntityEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.SPECTATOR) {
            event.setCancelled(true);
        }
    }

    /** Spectator nie używa NICZEGO: prawy/lewy klik, przedmioty, bloki, encje. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getPlayer().getGameMode() == GameMode.SPECTATOR) {
            event.setCancelled(true);
        }
    }

    /** Spectator nie wchodzi w interakcję z encjami (moby, stojaki, gracze). */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getPlayer().getGameMode() == GameMode.SPECTATOR) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (event.getPlayer().getGameMode() == GameMode.SPECTATOR) {
            event.setCancelled(true);
        }
    }

    /** Spectator nie niszczy ani nie stawia bloków. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBreak(BlockBreakEvent event) {
        if (event.getPlayer().getGameMode() == GameMode.SPECTATOR) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlace(BlockPlaceEvent event) {
        if (event.getPlayer().getGameMode() == GameMode.SPECTATOR) {
            event.setCancelled(true);
        }
    }

    /** Spectator nie zadaje obrażeń (też przez pociski). */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        org.bukkit.entity.Entity dmg = event.getDamager();
        Player p = dmg instanceof Player pp ? pp
                : dmg instanceof org.bukkit.entity.Projectile pr && pr.getShooter() instanceof Player s ? s : null;
        if (p != null && p.getGameMode() == GameMode.SPECTATOR) {
            event.setCancelled(true);
        }
    }

    /** Spectator nie może sam zginąć w normalny sposób (tylko /hub itd.). */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p && p.getGameMode() == GameMode.SPECTATOR) {
            event.setCancelled(true);
        }
    }
}
