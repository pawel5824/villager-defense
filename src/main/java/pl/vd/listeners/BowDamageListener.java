package pl.vd.listeners;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import pl.vd.VillagerDefensePlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Własne obrażenia łuku (strzał gracza) = SUMA obrażeń od naciągu i od dystansu:
 * - naciąg: wyliczany z prędkości strzały w momencie wystrzelenia (pełny naciąg = 3.0),
 *   wartości z config (bow-damage.draw-immediate/medium/max), płynne przejście 0-100%,
 * - dystans: odległość od miejsca wystrzelenia (Projectile#getOrigin) do trafienia,
 *   progi z config (bow-damage.distance),
 * - mnożnik zaklęcia Power (nakłada się na sumę), a kryt z CritListener mnoży wynik.
 */
public class BowDamageListener implements Listener {

    private final VillagerDefensePlugin plugin;
    private final org.bukkit.NamespacedKey powerKey;
    private final org.bukkit.NamespacedKey fortuneKey;
    private final List<double[]> distanceTiers = new ArrayList<>(); // {maxDist, damage}

    public BowDamageListener(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
        this.powerKey = new org.bukkit.NamespacedKey(plugin, "bow_power");
        this.fortuneKey = new org.bukkit.NamespacedKey(plugin, "vd_fortune_level");
        for (var m : plugin.getConfig().getMapList("bow-damage.distance")) {
            Number maxDist = (Number) m.get("max-dist");
            Number dmg = (Number) m.get("damage");
            if (maxDist == null || dmg == null) continue;
            distanceTiers.add(new double[]{maxDist.doubleValue(), dmg.doubleValue()});
        }
        // domyślne progi, gdy sekcji nie ma w config.yml (albo jest pusta)
        if (distanceTiers.isEmpty()) {
            distanceTiers.add(new double[]{3, 1.0});
            distanceTiers.add(new double[]{7, 1.8});
            distanceTiers.add(new double[]{12, 2.3});
            distanceTiers.add(new double[]{17, 3.0});
            distanceTiers.add(new double[]{Double.MAX_VALUE, 3.7});
        }
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("bow-damage.enabled", true);
    }

    /** Przy wystrzeleniu: ustaw obrażenia od naciągu i zapamiętaj mnożnik Power w PDC strzały. */
    @EventHandler
    public void onLaunch(ProjectileLaunchEvent event) {
        if (!enabled()) return;
        if (!(event.getEntity() instanceof AbstractArrow arrow)) return;
        if (!(arrow.getShooter() instanceof Player shooter)) return;

        // prędkość strzały jest proporcjonalna do naciągu (pełny naciąg = 3.0)
        double speed = arrow.getVelocity().length();
        double charge = Math.min(1.0, speed / 3.0);
        arrow.setDamage(drawDamage(charge));

        // mnożnik Power z łuku w ręce w momencie strzału (Power: +25% x (poziom+1))
        double powerMult = 1.0;
        ItemStack bow = shooter.getInventory().getItemInMainHand();
        if (bow.getType() == Material.BOW || bow.getType() == Material.CROSSBOW) {
            int level = bow.getEnchantmentLevel(Enchantment.POWER);
            if (level > 0) powerMult = 1.0 + 0.25 * (level + 1);
        }
        arrow.getPersistentDataContainer().set(powerKey, PersistentDataType.DOUBLE, powerMult);

        // poziom zaklęcia Szczęście (w sklepie = Fortune) z łuku/kuszy - zapisany
        // na pocisku, żeby CritListener mógł doliczyć bonus do szansy na kryta
        // (działa nawet po zmianie broni w locie strzały). Trójząb POMIJAMY:
        // rzucany nie dostaje kryta od szczęścia - Szczęście na trójzębie działa
        // tylko jako cios wręcz.
        int fortuneLevel = 0;
        if (bow.getType() == Material.BOW || bow.getType() == Material.CROSSBOW) {
            fortuneLevel = bow.getEnchantmentLevel(Enchantment.FORTUNE);
        }
        arrow.getPersistentDataContainer().set(fortuneKey, PersistentDataType.INTEGER, fortuneLevel);
    }

    /** Przy trafieniu: dodaj obrażenia dystansowe i pomnóż przez Power (przed krytem). */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!enabled()) return;
        if (!(event.getDamager() instanceof AbstractArrow arrow)) return;
        if (!(arrow.getShooter() instanceof Player)) return;

        PersistentDataContainer pdc = arrow.getPersistentDataContainer();
        if (!pdc.has(powerKey)) return; // strzała nie wystrzelona przez nasz system (np. szkielet)

        double draw = arrow.getDamage();
        double distance = 0;
        Location origin = arrow.getOrigin();
        if (origin != null) {
            distance = origin.distance(arrow.getLocation());
        }
        double distanceDmg = distanceDamage(distance);
        double powerMult = pdc.get(powerKey, PersistentDataType.DOUBLE);

        event.setDamage((draw + distanceDmg) * powerMult);
    }

    private double drawDamage(double charge) {
        double imm = plugin.getConfig().getDouble("bow-damage.draw-immediate", 1.0);
        double med = plugin.getConfig().getDouble("bow-damage.draw-medium", 2.5);
        double max = plugin.getConfig().getDouble("bow-damage.draw-max", 4.5);
        if (charge <= 0) return imm;
        if (charge >= 1) return max;
        if (charge <= 0.5) return imm + (med - imm) * (charge / 0.5);
        return med + (max - med) * ((charge - 0.5) / 0.5);
    }

    private double distanceDamage(double dist) {
        for (double[] tier : distanceTiers) {
            if (dist <= tier[0]) return tier[1];
        }
        return distanceTiers.isEmpty() ? 1.0 : distanceTiers.get(distanceTiers.size() - 1)[1];
    }
}
