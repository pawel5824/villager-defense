package pl.vd.raft.entity;

import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;

/**
 * RaftListener - ochrona entity tworzacych tratwe (shulkery-kolizje +
 * BlockDisplay-deski). Wszystkie entity tratwy maja tagi scoreboard:
 *  - RaftEntity.TAG_COLLIDER ("raft_collider") - shulkery
 *  - RaftEntity.TAG_VISUAL    ("raft_visual")   - BlockDisplay
 *
 * Zabezpieczenia:
 *  - nie mozna ich zniszczyc (obrazenia anulowane)
 *  - nie mozna z nimi interagowac (przypadkowy klik w niewidzialna kolizje)
 */
public class RaftListener implements Listener {

    private boolean isRaftEntity(Entity entity) {
        if (entity == null || entity.isDead()) return false;
        return entity.getScoreboardTags().contains(RaftEntity.TAG_COLLIDER)
                || entity.getScoreboardTags().contains(RaftEntity.TAG_VISUAL);
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (isRaftEntity(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (isRaftEntity(event.getEntity())) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (isRaftEntity(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }
}
