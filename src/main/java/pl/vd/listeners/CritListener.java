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

        // ZAKLĘCIE SZCZĘŚCIE (w sklepie = Fortune, fortune_1): przy ciosie wręcz
        // każdy poziom dodaje crits.luck-bonus-per-level %. Strzały z łuku/kuszy
        // mają osobną tabelę poziomów: crits.ranged-luck-bonus-by-level.
        // Poziom strzału jest zapisany na pocisku przy strzale (BowDamageListener),
        // żeby działał nawet po zmianie broni w locie strzały. Rzucany trójząb
        // NIE dostaje bonusu - Szczęście na trójzębie działa tylko jako cios wręcz.
        double luckPerLevel = plugin.getConfig().getDouble("crits.luck-bonus-per-level", 5.0);
        if (ranged) {
            // Łuk/kusza (także proca): Szczęście liczy się od DYSTANSU strzału -
            // za każde crits.ranged-luck-blocks-per-step bloków dodaje bonus
            // zależny od poziomu: I = 3%, II = 4%, III = 5%, IV = 6%, V = 7%.
            if (event.getDamager() instanceof PersistentDataHolder holder) {
                int level = holder.getPersistentDataContainer().getOrDefault(
                        fortuneKey, PersistentDataType.INTEGER, 0);
                if (level > 0) {
                    double stepBlocks = plugin.getConfig()
                            .getDouble("crits.ranged-luck-blocks-per-step", 3.0);
                    double bonusPerStep = rangedLuckBonus(level);
                    double distance = 0;
                    if (event.getDamager() instanceof org.bukkit.entity.AbstractArrow arrow
                            && arrow.getOrigin() != null
                            && arrow.getOrigin().getWorld() == victim.getWorld()) {
                        distance = arrow.getOrigin().distance(arrow.getLocation());
                    }
                    if (stepBlocks > 0 && bonusPerStep > 0) {
                        chance += Math.floor(distance / stepBlocks) * bonusPerStep;
                    } else if (luckPerLevel > 0) {
                        chance += level * luckPerLevel; // awaryjnie: stare, płaskie
                    }
                }
            }
        } else if (luckPerLevel > 0) {
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

    /**
     * Bonus do szansy na kryt za każde pełne 3 bloki strzału, zależny od poziomu
     * Szczęścia z tabeli crits.ranged-luck-bonus-by-level (I = 3% ... V = 7%).
     * Poziom spoza tabeli bierze skrajną wartość; brak tabeli w configu daje
     * wzór poziom + 2 (te same wartości dla I-V).
     */
    private double rangedLuckBonus(int level) {
        var table = plugin.getConfig().getDoubleList("crits.ranged-luck-bonus-by-level");
        if (!table.isEmpty()) {
            int idx = Math.max(1, Math.min(level, table.size())) - 1;
            return table.get(idx);
        }
        return Math.max(1, Math.min(level, 5)) + 2.0;
    }

    private Player extractPlayerDamager(org.bukkit.entity.Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }
}
