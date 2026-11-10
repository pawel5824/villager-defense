package pl.vd.raft.listeners;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Blokuje przytrzymanie prawego przycisku (charge) dla włóczni.
 * Włócznie nie wyciągają się do przodu — działają tylko при lewym kliknięciu.
 */
public class SpearBlockListener implements Listener {

    private static final Set<String> BLOCKED_SPEARS = Set.of(
            "§6Drewniana Włócznia",
            "§bKamienna Włócznia",
            "§fŻelazna Włócznia"
    );

    /** Cooldown między atakami (ms). 1.25s = 1250ms */
    private static final long ATTACK_COOLDOWN_MS = 1250;

    /** Mapa cooldownów: UUID gracza → timestamp ostatniego ataku (ms). */
    private final Map<UUID, Long> cooldownMap = new ConcurrentHashMap<>();

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        ItemStack item = event.getItem();
        if (item == null) return;
        if (!isSpear(item.getType())) return;
        if (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) return;
        if (!BLOCKED_SPEARS.contains(item.getItemMeta().getDisplayName())) return;

        event.setCancelled(true);
    }

    /**
     * Cooldown between spear attacks — 1.25s.
     */
    @EventHandler
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || !isSpear(item.getType())) return;
        if (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) return;
        if (!BLOCKED_SPEARS.contains(item.getItemMeta().getDisplayName())) return;

        long now = System.currentTimeMillis();
        Long lastAttack = cooldownMap.get(player.getUniqueId());
        if (lastAttack != null && (now - lastAttack) < ATTACK_COOLDOWN_MS) {
            event.setCancelled(true);
            return;
        }
        cooldownMap.put(player.getUniqueId(), now);
    }

    private boolean isSpear(Material mat) {
        return mat.name().endsWith("_SPEAR");
    }
}
