package pl.vd.lobby;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import pl.vd.VillagerDefensePlugin;
import net.kyori.adventure.text.Component;

/**
 * Obsługuje kliknięcie skrzyni daily reward w lobby.
 * Skrzynia = WAXED_EXPOSED_COPPER_CHEST z tagiem "vd_daily_reward".
 */
public class DailyRewardListener implements Listener {

    private final VillagerDefensePlugin plugin;

    public DailyRewardListener(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = e.getClickedBlock();
        if (block == null) return;

        // sprawdź czy to skrzynia daily reward
        if (block.getType() != Material.WAXED_EXPOSED_COPPER_CHEST) return;
        if (!block.hasMetadata("vd_daily_reward")) return;

        Player player = e.getPlayer();
        e.setCancelled(true); // blokuj otwieranie skrzyni

        DailyRewardManager drm = plugin.getDailyRewardManager();

        if (drm.canClaim(player)) {
            int oldStreak = drm.getStreak(player);
            // odbierz nagrodę
            if (drm.claim(player)) {
                int newStreak = drm.getStreak(player);
                int bonus = drm.getStreakBonus(newStreak);
                int total = 5 + bonus;

                // wiadomość na czacie
                StringBuilder msg = new StringBuilder();
                msg.append("§6⭐ §eOdebrano dzienną nagrodę! §7+§a").append(total).append(" XP");

                if (newStreak > 1) {
                    msg.append("\n§7Streak: §e").append(newStreak).append(" §7dni §7(§a+").append(bonus).append(" §7bonus)");
                } else if (newStreak == 1 && oldStreak > 0) {
                    msg.append("\n§7Streak §czerwany! §7Zaczynasz od nowa.");
                }

                player.sendMessage(Component.text(msg.toString()));
                // odśwież pasek expa
                plugin.getPlayerXPBar().refresh(player);
            }
        } else {
            // cooldown trwa - czas na czacie
            String remaining = drm.getRemainingTime(player);
            player.sendMessage(Component.text("§cOdebrałeś/aś już codzienną skrzynię! §7Następna: " + remaining));
        }
    }
}
