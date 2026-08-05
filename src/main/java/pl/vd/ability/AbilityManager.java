package pl.vd.ability;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import pl.vd.VillagerDefensePlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AbilityManager {

    private final VillagerDefensePlugin plugin;
    private final NamespacedKey abilityKey;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public AbilityManager(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
        this.abilityKey = new NamespacedKey(plugin, "ability_type");
    }

    // ---------- Tworzenie / rozpoznawanie przedmiotów-umiejętności ----------

    public ItemStack createAbilityItem(AbilityType type) {
        ItemStack item = switch (type) {
            case LASER_WAND -> new ItemStack(Material.BLAZE_ROD);
        };
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName(type));
            meta.setLore(lore(type));
            meta.getPersistentDataContainer().set(abilityKey, PersistentDataType.STRING, type.name());
            meta.setUnbreakable(true);
            item.setItemMeta(meta);
        }
        return item;
    }

    public AbilityType getAbilityType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String raw = item.getItemMeta().getPersistentDataContainer().get(abilityKey, PersistentDataType.STRING);
        if (raw == null) return null;
        try {
            return AbilityType.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String displayName(AbilityType type) {
        return switch (type) {
            case LASER_WAND -> "§dRóżdżka Ognia";
        };
    }

    private List<String> lore(AbilityType type) {
        return switch (type) {
            case LASER_WAND -> List.of("§7PPM: wystrzel laser", "§7Obrażenia: §c2.5", "§7Odnowienie: §f1.0s");
        };
    }

    // ---------- Cooldowny ----------

    private long cooldownMillis(AbilityType type) {
        return switch (type) {
            case LASER_WAND -> 1000L;
        };
    }

    public boolean isOnCooldown(Player player) {
        Long last = cooldowns.get(player.getUniqueId());
        return last != null && System.currentTimeMillis() < last;
    }

    public long getRemainingCooldownSeconds(Player player) {
        Long last = cooldowns.get(player.getUniqueId());
        if (last == null) return 0;
        return Math.max(0, (last - System.currentTimeMillis()) / 1000L + 1);
    }

    private void setCooldown(Player player, AbilityType type) {
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + cooldownMillis(type));
    }

    // ---------- Wykonanie umiejętności ----------

    public void use(Player player, AbilityType type) {
        if (isOnCooldown(player)) {
            player.sendMessage(Component.text("§cUmiejętność odnawia się jeszcze " + getRemainingCooldownSeconds(player) + "s"));
            return;
        }
        switch (type) {
            case LASER_WAND -> fireLaser(player);
        }
        setCooldown(player, type);
    }

    private static final double LASER_RANGE = 25.0;
    private static final double LASER_DAMAGE = 2.5;

    private void fireLaser(Player player) {
        World world = player.getWorld();
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();

        RayTraceResult result = world.rayTraceEntities(eye, direction, LASER_RANGE, 0.4,
                entity -> entity != player && entity instanceof LivingEntity);

        Location endPoint = (result != null) ? result.getHitPosition().toLocation(world)
                : eye.clone().add(direction.clone().multiply(LASER_RANGE));

        drawBeam(world, eye, endPoint);
        world.playSound(eye, Sound.ENTITY_BLAZE_SHOOT, 1f, 1.4f);

        if (result != null && result.getHitEntity() instanceof LivingEntity target) {
            target.damage(LASER_DAMAGE, player);
            world.playSound(target.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.8f);
        }
    }

    private void drawBeam(World world, Location start, Location end) {
        Vector direction = end.toVector().subtract(start.toVector());
        double length = direction.length();
        if (length < 0.01) return;
        direction.normalize();

        double step = 0.3;
        for (double d = 0; d < length; d += step) {
            Location point = start.clone().add(direction.clone().multiply(d));
            world.spawnParticle(Particle.END_ROD, point, 1, 0, 0, 0, 0);
        }
    }
}
