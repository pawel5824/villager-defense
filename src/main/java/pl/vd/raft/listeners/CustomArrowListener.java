package pl.vd.raft.listeners;

import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Obsługa custom strzał — modyfikacja obrażeń + usuwanie efektów.
 *
 * Kamienna strzała: 10 pkt
 * Metalowa strzała: 15 pkt
 * Tytanowa strzała: 20 pkt
 */
public class CustomArrowListener implements Listener {

    private static final String STONE_NAME = "§7Kamienna strzała";
    private static final String IRON_NAME = "§fMetalowa strzała";
    private static final String TITAN_NAME = "§bTytanowa strzała";

    private static final int STONE_DAMAGE = 10;
    private static final int IRON_DAMAGE = 15;
    private static final int TITAN_DAMAGE = 20;

    /**
     * Modyfikuj obrażenia gdy strzała trafi moba/gracza.
     */
    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Projectile projectile)) return;
        if (!(projectile instanceof Arrow arrow)) return;
        if (!(arrow.getShooter() instanceof Player player)) return;

        int customDamage = getCustomDamage(player);
        if (customDamage < 0) return; // nie jest custom strzałą

        // Ustaw stałe obrażenia (nie modyfikuj — zamień)
        event.setDamage(customDamage);
    }

    /**
     * Usuń efekty potion gdy strzała trafi w coś.
     */
    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow arrow)) return;
        if (!(arrow.getShooter() instanceof Player player)) return;

        if (getCustomDamage(player) < 0) return;

        // Usuń wszystkie efekty potion ze strzały
        arrow.clearCustomEffects();
        arrow.setBasePotionType(null);
    }

    /**
     * Sprawdza czy gracz trzyma custom strzałę i zwraca obrażenia.
     * Zwraca -1 jeśli to nie jest custom strzała.
     */
    private int getCustomDamage(Player player) {
        // Sprawdź strzałę w Main Hand (łuk strzela z slotu, w którym była strzała)
        // Vanilla strzela z slotu z największą ilością strzał
        ItemStack arrows = findArrows(player);
        if (arrows == null) return -1;

        if (!arrows.hasItemMeta() || !arrows.getItemMeta().hasDisplayName()) return -1;
        String name = arrows.getItemMeta().getDisplayName();

        if (name.equals(STONE_NAME)) return STONE_DAMAGE;
        if (name.equals(IRON_NAME)) return IRON_DAMAGE;
        if (name.equals(TITAN_NAME)) return TITAN_DAMAGE;

        return -1;
    }

    /**
     * Znajduje custom strzałę w ekwipunku gracza.
     */
    private ItemStack findArrows(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;
            if (item.getType() != org.bukkit.Material.TIPPED_ARROW) continue;
            if (!item.hasItemMeta()) continue;
            if (!item.getItemMeta().hasDisplayName()) continue;
            String name = item.getItemMeta().getDisplayName();
            if (name.equals(STONE_NAME) || name.equals(IRON_NAME) || name.equals(TITAN_NAME)) {
                return item;
            }
        }
        return null;
    }
}
