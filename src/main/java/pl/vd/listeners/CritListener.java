package pl.vd.listeners;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataHolder;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import pl.vd.VillagerDefensePlugin;
import pl.vd.type.WeaponType;

/**
 * Krytyczne obrażenia bez podskoku: każde trafienie gracza ma małą szansę
 * (domyślnie 5%) stać się ciosem krytycznym, który zadaje bonusowe obrażenia
 * i - jak waniliowy kryt z wyskoku - nie odrzuca trafionego moba.
 */
public class CritListener implements Listener {

    private final VillagerDefensePlugin plugin;
    // poziom zaklęcia Szczęście (w sklepie = Fortune) z łuku/kuszy, zapisany na
    // pocisku przy strzale (BowDamageListener) - ten sam klucz w obu listenerach
    private final org.bukkit.NamespacedKey fortuneKey;

    public CritListener(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
        this.fortuneKey = new org.bukkit.NamespacedKey(plugin, "vd_fortune_level");
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        Player attacker = extractPlayerDamager(event.getDamager());
        if (attacker == null) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        if (victim instanceof Player || victim instanceof Villager || victim instanceof ArmorStand) return;
        if (event.isCancelled()) return;

        // Ciosy krytyczne (bez skoku) z trybu VD nie działają w świecie Raft -
        // tam walka ma być waniliowa. (equalsIgnoreCase — Paper zapisuje foldery
        // światów małymi literami: „RAFT" → „raft").
        String raftWorldName = plugin.getConfig().getString("raft-world-name", "RAFT");
        if (victim.getWorld().getName().equalsIgnoreCase(raftWorldName)) return;

        // pociski (strzały, trójzęby, wind charge) mają osobną konfigurację krytów: crits.ranged-*
        boolean ranged = event.getDamager() instanceof Projectile;
        double chance;
        double multiplier;
        if (ranged) {
            if (!plugin.getConfig().getBoolean("crits.ranged-enabled", true)) return;
            chance = plugin.getConfig().getDouble("crits.ranged-chance-percent", 4.0);
            multiplier = plugin.getConfig().getDouble("crits.ranged-damage-multiplier", 1.5);
        } else {
            chance = plugin.getConfig().getDouble("crits.chance-percent", 4.0);
            multiplier = plugin.getConfig().getDouble("crits.damage-multiplier", 1.5);
        }

        // ZAKLĘCIE SZCZĘŚCIE (w sklepie = Fortune, fortune_1): każdy poziom dodaje
        // crits.luck-bonus-per-level % do szansy na kryta. Działa na: mieczu/siekierze/
        // trójzębie jako CIOS WRĘCZ (poziom z broni w ręce) i na łuku/kuszy (poziom
        // zapisany na pocisku przy strzale w BowDamageListener, żeby działało nawet
        // po zmianie broni w locie strzały). Rzucany trójząb NIE dostaje bonusu -
        // Szczęście na trójzębie działa tylko jako cios wręcz.
        double luckPerLevel = plugin.getConfig().getDouble("crits.luck-bonus-per-level", 5.0);
        if (luckPerLevel > 0) {
            if (ranged) {
                if (event.getDamager() instanceof PersistentDataHolder holder) {
                    int level = holder.getPersistentDataContainer().getOrDefault(
                            fortuneKey, PersistentDataType.INTEGER, 0);
                    if (level > 0) chance += level * luckPerLevel;
                }
            } else {
                ItemStack weapon = attacker.getInventory().getItemInMainHand();
                if (weapon != null && weapon.getType() != Material.AIR) {
                    WeaponType wt = WeaponType.of(weapon.getType());
                    boolean luckWeapon = wt == WeaponType.SWORD || wt == WeaponType.AXE
                            || weapon.getType() == Material.TRIDENT; // trójząb tylko wręcz
                    if (luckWeapon) {
                        int level = weapon.getEnchantmentLevel(Enchantment.FORTUNE);
                        if (level > 0) chance += level * luckPerLevel;
                    }
                }
            }
        }
        // Szczęście na zbroi: każdy poziom na dowolnej części dodaje
        // crits.armor-luck-bonus-per-level % (domyślnie 2,5%), a poziomy
        // z hełmu, klaty, spodni i butów SUMUJĄ się.
        double armorLuckPerLevel = plugin.getConfig()
                .getDouble("crits.armor-luck-bonus-per-level", 2.5);
        if (armorLuckPerLevel > 0) {
            int armorLevels = 0;
            for (ItemStack piece : new ItemStack[]{
                    attacker.getInventory().getHelmet(),
                    attacker.getInventory().getChestplate(),
                    attacker.getInventory().getLeggings(),
                    attacker.getInventory().getBoots()}) {
                if (piece == null || piece.getType() == Material.AIR) continue;
                armorLevels += piece.getEnchantmentLevel(Enchantment.FORTUNE);
            }
            if (armorLevels > 0) chance += armorLevels * armorLuckPerLevel;
        }

        if (chance <= 0 || Math.random() * 100.0 >= chance) return;

        event.setDamage(event.getDamage() * multiplier);

        // efekt cząsteczek przy trafieniu krytycznym (czerwone iskry + magiczny połysk)
        if (plugin.getConfig().getBoolean("crits.particle-enabled", true)) {
            int count = plugin.getConfig().getInt("crits.particle-count", 20);
            Location loc = victim.getLocation().add(0, victim.getHeight() * 0.5, 0);
            victim.getWorld().spawnParticle(Particle.CRIT, loc, count, 0.4, 0.4, 0.4, 0.05);
            victim.getWorld().spawnParticle(Particle.ENCHANTED_HIT, loc, count / 2, 0.3, 0.3, 0.3, 0.05);
        }

        // bez odrzutu (tylko walka wręcz - strzały mają własny, minimalny odrzut)
        if (!ranged && plugin.getConfig().getBoolean("crits.no-knockback", true)) {
            // odrzut jest nakładany po zakończeniu eventu - zerujemy prędkość
            // w następnym ticku, żeby mob nie "podskoczył" od ciosu
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (victim.isValid()) {
                    victim.setVelocity(new Vector(0, victim.getVelocity().getY(), 0));
                }
            });
        }

        pl.vd.util.AntiSpam.send(attacker, "§c§lKRYT! §7(" + (int) Math.round(event.getDamage()) + " obrażeń)");
    }

    private Player extractPlayerDamager(org.bukkit.entity.Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }
}
