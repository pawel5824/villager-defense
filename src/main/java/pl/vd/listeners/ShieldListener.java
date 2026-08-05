package pl.vd.listeners;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.util.Vector;
import pl.vd.VillagerDefensePlugin;
import pl.vd.shield.ShieldManager;

public class ShieldListener implements Listener {

    private final VillagerDefensePlugin plugin;

    public ShieldListener(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlock(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player defender)) return;
        if (!defender.isBlocking()) return;
        if (!(event.getDamager() instanceof LivingEntity attacker)) return;

        ShieldManager shieldManager = plugin.getShieldManager();
        boolean hasShield = shieldManager.isKnockbackShield(defender.getInventory().getItemInMainHand())
                || shieldManager.isKnockbackShield(defender.getInventory().getItemInOffHand());
        if (!hasShield) return;

        Vector push = attacker.getLocation().toVector().subtract(defender.getLocation().toVector());
        push.setY(0);
        if (push.lengthSquared() < 0.0001) push = defender.getLocation().getDirection().multiply(-1);
        push.normalize().multiply(ShieldManager.KNOCKBACK_STRENGTH);
        push.setY(ShieldManager.KNOCKBACK_VERTICAL);

        attacker.setVelocity(attacker.getVelocity().add(push));
    }
}
