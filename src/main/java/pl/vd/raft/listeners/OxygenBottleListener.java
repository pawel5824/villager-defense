package pl.vd.raft.listeners;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Obsługa Butelki z Tlenem (LEATHER_HELMET z custom texture).
 *
 * Zasady:
 *  - Co sekundę spada durability o 1 gdy gracz jest w wodzie
 *  - Gdy durability = 0 → butelka znika
 *  - Gracz nie może założyć butelki ręcznie (vanilla restriction)
 */
public class OxygenBottleListener implements Listener {

    private static final String BOTTLE_NAME = "§bButelka z Tlenem";
    private static final String MASK_NAME = "§bMaska oddechowa";
    private static final long TICK_INTERVAL = 20L; // 1 sekunda = 20 ticków

    private final pl.vd.raft.RaftPlugin plugin;

    public OxygenBottleListener(pl.vd.raft.RaftPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Rozpocznij Timer — co sekundę sprawdzaj graczy w wodzie.
     */
    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin.getParent(), () -> {
            String raftWorldName = plugin.getConfig().getString("raft-world-name", "RAFT");

            for (Player player : plugin.getServer().getOnlinePlayers()) {
                if (!player.getWorld().getName().equalsIgnoreCase(raftWorldName)) continue;

                // Ustaw max tlen na 30 sekund (600 ticków) — globally
                if (player.getMaximumAir() != 600) {
                    player.setMaximumAir(600);
                }

                // Usuń efekt Water Breathing z turtle helmet (Maska oddechowa)
                ItemStack helmet = player.getInventory().getHelmet();
                if (helmet != null && helmet.getType() == Material.TURTLE_HELMET && isBreathingItem(helmet)) {
                    if (player.hasPotionEffect(org.bukkit.potion.PotionEffectType.WATER_BREATHING)) {
                        player.removePotionEffect(org.bukkit.potion.PotionEffectType.WATER_BREATHING);
                    }
                }

                if (!player.isInWater()) continue;
                if (helmet == null || helmet.getType() == Material.AIR) continue;
                if (!isBreathingItem(helmet)) continue;

                // Spadek durability o 1
                short durability = helmet.getDurability();
                short maxDur = helmet.getType().getMaxDurability();
                short newDurability = (short) (durability + 1);

                if (newDurability >= maxDur) {
                    // Butelka się kończy — zdejmij z głowy
                    player.getInventory().setHelmet(null);
                    player.getWorld().playSound(
                        player.getLocation(),
                        org.bukkit.Sound.ENTITY_ITEM_BREAK,
                        1.0f, 1.0f
                    );
                } else {
                    helmet.setDurability(newDurability);
                }
            }
        }, TICK_INTERVAL, TICK_INTERVAL);
    }

    /**
     * Blokuj_EQUIPMENT — nie pozwól zakładać butelki na slot hełmu ręcznie.
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null) return;
        if (!isBreathingItem(clicked)) return;

        // Blokuj equipowanie na armor slot
        int rawSlot = event.getRawSlot();
        if (rawSlot == 5) { // 5 = helmet slot w vanilla inventory
            event.setCancelled(true);
        }
    }

    /** Sprawdza czy item to Butelka z Tlenem lub Maska oddechowa. */
    private boolean isBreathingItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        if (!item.getItemMeta().hasDisplayName()) return false;
        String name = item.getItemMeta().getDisplayName();
        if (name.equals(BOTTLE_NAME) && item.getType() == Material.LEATHER_HELMET) return true;
        if (name.equals(MASK_NAME) && item.getType() == Material.TURTLE_HELMET) return true;
        return false;
    }
}
