package pl.vd.listeners;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import pl.vd.VillagerDefensePlugin;
import pl.vd.arena.Arena;

/**
 * Podczas przerwy (/przerwa) moby i wieśniacy areny są zamrożeni — ten listener
 * nie pozwala ich bić ani zabijać. Anulujemy KAŻDE obrażenia zamrożonego bytu
 * areny (od gracza, pocisku, ognia, upadku), bo przerwa ma zatrzymać rozgrywkę,
 * a nie tylko ruch. Gracze nadal mogą dostawać obrażenia.
 */
public class PauseListener implements Listener {

    private final VillagerDefensePlugin plugin;

    public PauseListener(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        Entity victim = event.getEntity();
        if (victim instanceof Player) return;

        Arena arena = arenaOf(victim);
        if (arena == null) return;
        if (!plugin.getPauseManager().isPaused(arena)) return;

        event.setCancelled(true);
    }

    /** Arena, do której należy mob/wieśniak (null = byt spoza areny). */
    private Arena arenaOf(Entity entity) {
        for (Arena arena : plugin.getArenaManager().getArenas().values()) {
            if (arena.getSpawnedMobs().contains(entity) || arena.getLivingVillagers().contains(entity)) {
                return arena;
            }
        }
        return null;
    }
}
