package pl.vd.listeners;

import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Wyłącza „chwilową odporność” (i-frame'y — vanilla invulnerability ticks) dla MOBÓW
 * trafianych przez gracza. Dzięki temu każdy cios gracza — miecz, łuk, shuriken itd. —
 * zadaje pełne obrażenia, nawet przy bardzo szybkich seriach trafień.
 * Gracze i wieśniacy zachowują i-frame'y (inaczej rój mobów niszczyłby gracza natychmiast).
 */
public class NoDamageTicksListener implements Listener {

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        if (victim instanceof Player || victim instanceof Villager || victim instanceof ArmorStand) return;

        // tylko obrażenia zadane przez gracza (bezpośrednio albo pociskiem gracza)
        Entity damager = event.getDamager();
        boolean byPlayer = damager instanceof Player;
        if (!byPlayer && damager instanceof Projectile projectile
                && projectile.getShooter() instanceof Player) {
            byPlayer = true;
        }
        if (!byPlayer) return;

        // maximumNoDamageTicks=0 sprawia, że po każdym trafieniu mob NIE dostaje odporności —
        // kolejne ciosy zawsze przechodzą; noDamageTicks=0 czyści ewentualną bieżącą odporność
        victim.setMaximumNoDamageTicks(0);
        victim.setNoDamageTicks(0);
    }
}
