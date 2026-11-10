package pl.vd.listeners;

import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import pl.vd.VillagerDefensePlugin;

/**
 * Życie i zniszczenie wieżyczek (/stojak, preset z hp: ...).
 * Wieżyczkę może zniszczyć TYLKO mob (potwór) - bezpośrednio albo strzałem
 * (np. strzała szkieleta). Gracze, wybuchy, ogień, lawa itd. nie zadają jej
 * żadnych obrażeń. Vanillowe HP stojaka nie jest ruszane - obrażenia idą na
 * własne HP wieżyczki (PDC), a przy 0 wieżyczka znika (ArmorStandTuner.damageTurret).
 * Obrażenia ZADAWANE przez wieżyczkę (bez widocznej strzały) i aggro mobów
 * obsługuje ArmorStandTuner.spawnTurretShot / aggroTurretAttackers.
 */
public class TurretListener implements Listener {

    private final VillagerDefensePlugin plugin;

    public TurretListener(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof ArmorStand stand)) return;
        if (!plugin.getArmorStandTuner().isDestructibleTurret(stand)) return;
        // nie ruszamy vanillowego HP stojaka - wieżyczka ma własne
        event.setCancelled(true);
        if (event instanceof EntityDamageByEntityEvent by) {
            Entity src = by.getDamager();
            boolean fromMonster = src instanceof Monster
                    || (src instanceof Projectile pr && pr.getShooter() instanceof Monster);
            if (fromMonster) {
                plugin.getArmorStandTuner().damageTurret(stand, by.getFinalDamage());
            }
        }
        // wszystko inne (gracz, wybuch, ogień) - zero obrażeń
    }
}
