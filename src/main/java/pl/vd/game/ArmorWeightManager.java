package pl.vd.game;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import pl.vd.VillagerDefensePlugin;
import pl.vd.arena.Arena;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Obciążenie zbroi na arenie.
 *
 * Kara zależy od materiału i założonych części, a nie od punktów ochrony:
 * hełm 15%, klata 40%, spodnie 30%, buty 15% pełnej kary zestawu.
 * Wartości pełnych zestawów są w config.yml (armor-weight.*), dzięki czemu
 * można je zmienić bez ruszania kodu.
 *
 * Na gracza działa JEDEN modifier prędkości (ADD_SCALAR, wartość ujemna),
 * a nie osobny efekt Slowness. Mikstury Szybkości mnożą się później.
 * Kara obowiązuje tylko na arenie; w lobby i na tratwie gracz chodzi normalnie.
 */
public class ArmorWeightManager {

    private static final double HELMET_SHARE = 0.15;
    private static final double CHEST_SHARE = 0.40;
    private static final double LEGS_SHARE = 0.30;
    private static final double BOOTS_SHARE = 0.15;

    private final VillagerDefensePlugin plugin;
    private final NamespacedKey modifierKey;
    private BukkitTask task;

    public ArmorWeightManager(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
        this.modifierKey = new NamespacedKey(plugin, "armor_weight");
    }

    /** Przelicza co 10 ticków, czyli co 0,5 s - zmiana zbroi działa prawie od razu. */
    public void start() {
        if (task != null) return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, 20L, 10L);
    }

    /** Zatrzymuje zadanie i zdejmuje karę ze wszystkich graczy online. */
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            remove(player);
        }
    }

    private void refreshAll() {
        Set<UUID> arenaPlayers = new HashSet<>();
        for (Arena arena : plugin.getArenaManager().getArenas().values()) {
            arenaPlayers.addAll(arena.getPlayers());
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (arenaPlayers.contains(player.getUniqueId())) {
                apply(player, penalty(player));
            } else {
                remove(player);
            }
        }
    }

    /** Kara w procentach dla aktualnie założonego zestawu. */
    private double penalty(Player player) {
        var inventory = player.getInventory();
        return piecePenalty(inventory.getHelmet(), HELMET_SHARE)
                + piecePenalty(inventory.getChestplate(), CHEST_SHARE)
                + piecePenalty(inventory.getLeggings(), LEGS_SHARE)
                + piecePenalty(inventory.getBoots(), BOOTS_SHARE);
    }

    private double piecePenalty(ItemStack item, double share) {
        if (item == null || item.getType() == Material.AIR) return 0;
        return fullSetPenalty(item.getType()) * share;
    }

    /** Pełna kara zestawu z configu; nieznany materiał = 0. */
    private double fullSetPenalty(Material material) {
        String name = material.name();
        String key;
        double fallback;

        if (name.startsWith("LEATHER_")) {
            key = "leather";
            fallback = 2.5;
        } else if (name.startsWith("CHAINMAIL_")) {
            key = "chainmail";
            fallback = 4.0;
        } else if (name.startsWith("COPPER_")) {
            key = "copper";
            fallback = 7.0;
        } else if (name.startsWith("GOLDEN_")) {
            key = "gold";
            fallback = 9.0;
        } else if (name.startsWith("IRON_")) {
            key = "iron";
            fallback = 13.0;
        } else if (name.startsWith("DIAMOND_")) {
            key = "diamond";
            fallback = 17.0;
        } else if (name.startsWith("NETHERITE_")) {
            key = "netherite";
            fallback = 21.0;
        } else {
            return 0;
        }

        return Math.max(0, plugin.getConfig().getDouble("armor-weight." + key, fallback));
    }

    /**
     * Ustawia dokładnie jedną karę. Jeśli wartość się nie zmieniła, modifier
     * zostaje bez ruszania - dzięki temu nie ma migotania prędkości co 0,5 s.
     */
    private void apply(Player player, double penalty) {
        AttributeInstance attribute = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (attribute == null) return;

        double expected = -penalty / 100.0;
        boolean present = false;
        for (AttributeModifier modifier : new ArrayList<>(attribute.getModifiers())) {
            if (!modifierKey.equals(modifier.getKey())) continue;
            if (!present
                    && modifier.getOperation() == AttributeModifier.Operation.ADD_SCALAR
                    && Math.abs(modifier.getAmount() - expected) < 1.0E-6) {
                present = true;
            } else {
                attribute.removeModifier(modifier);
            }
        }
        if (!present && penalty > 0) {
            attribute.addTransientModifier(new AttributeModifier(
                    modifierKey, expected, AttributeModifier.Operation.ADD_SCALAR));
        }
    }

    private void remove(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (attribute == null) return;
        for (AttributeModifier modifier : new ArrayList<>(attribute.getModifiers())) {
            if (modifierKey.equals(modifier.getKey())) {
                attribute.removeModifier(modifier);
            }
        }
    }
}
