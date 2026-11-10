package pl.vd.listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import pl.vd.VillagerDefensePlugin;
import pl.vd.game.MobSpawner;
import pl.vd.util.HealthDisplay;

public class HealthDisplayListener implements Listener {

    private final VillagerDefensePlugin plugin;

    public HealthDisplayListener(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        if (!entity.hasMetadata(MobSpawner.METADATA_KEY)) return;

        // Zdrowie aktualizuje się dopiero PO przetworzeniu eventu, więc odczytujemy je tick później
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (entity.isValid() && !entity.isDead()) {
                HealthDisplay.update(entity);
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onRegain(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        if (!entity.hasMetadata(MobSpawner.METADATA_KEY)) return;

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (entity.isValid() && !entity.isDead()) {
                HealthDisplay.update(entity);
            }
        });
    }
}
