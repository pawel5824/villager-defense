package pl.vd.achievements;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import pl.vd.VillagerDefensePlugin;
import pl.vd.arena.Arena;

import java.util.List;

/** Obsługa kliknięć w GUI osiągnięć (hub i kategorie = jedno i to samo GUI). */
public class AchievementsListener implements Listener {

    private final VillagerDefensePlugin plugin;

    public AchievementsListener(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof AchievementsHolder holder)) return;

        event.setCancelled(true);
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(top)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        int slot = event.getSlot();
        // strzałki przewijania (działają w hubie i w kategoriach - to samo GUI)
        if (slot == AchievementsGUI.SCROLL_UP_SLOT) {
            if (event.isShiftClick()) {
                scrollTo(player, holder, top, 0); // shift+klik = od razu na samą górę
            } else {
                scroll(player, holder, top, -AchievementsGUI.ROW);
            }
            return;
        }
        if (slot == AchievementsGUI.SCROLL_DOWN_SLOT) {
            if (event.isShiftClick()) {
                // shift+klik = od razu na sam dół listy (ostatnia możliwa pozycja)
                List<Achievement> list = plugin.getAchievementsGUI().getViewList(holder);
                scrollTo(player, holder, top, Math.max(0, list.size() - AchievementsGUI.PER_PAGE));
            } else {
                scroll(player, holder, top, AchievementsGUI.ROW);
            }
            return;
        }

        if (holder.getView() == AchievementsHolder.View.HUB) {
            if (slot == AchievementsGUI.EXIT_SLOT) {
                handleExit(player, holder.getSource());
                return;
            }
            AchievementCategory cat = AchievementCategory.forSlot(slot);
            if (cat != null) {
                plugin.getAchievementsGUI().openCategory(player, cat, holder.getSource());
            }
        } else if (holder.getView() == AchievementsHolder.View.CATEGORY) {
            if (slot == AchievementsGUI.EXIT_SLOT) {
                handleExit(player, holder.getSource());
                return;
            }
            AchievementCategory cat = AchievementCategory.forSlot(slot);
            if (cat != null) {
                // przełączanie kategorii bez wychodzenia - to samo GUI
                plugin.getAchievementsGUI().openCategory(player, cat, holder.getSource());
                return;
            }

        }
    }

    /** Przewija listę osiągnięć o 1 rząd (9) - strzałki gasną na krańcach listy. */
    private void scroll(Player player, AchievementsHolder holder, Inventory top, int delta) {
        List<Achievement> list = plugin.getAchievementsGUI().getViewList(holder);
        int offset = holder.getScrollOffset();
        if (delta > 0 && offset + AchievementsGUI.PER_PAGE >= list.size()) return; // już na dole
        if (delta < 0 && offset <= 0) return; // już na górze
        holder.setScrollOffset(offset + delta);
        plugin.getAchievementsGUI().updateView(top, player, holder);
    }

    /** Przeskakuje od razu na wskazaną pozycję (shift+klik strzałki = góra/dół listy). */
    private void scrollTo(Player player, AchievementsHolder holder, Inventory top, int offset) {
        if (holder.getScrollOffset() == offset) return;
        holder.setScrollOffset(offset);
        plugin.getAchievementsGUI().updateView(top, player, holder);
    }

    /** Przycisk na dole: ze sklepu = „Powrót" (do sklepu), z komendy = „Zamknij". */
    private void handleExit(Player player, AchievementsHolder.Source source) {
        if (source == AchievementsHolder.Source.SHOP) {
            Arena arena = plugin.getShopGUI().resolveArena(player);
            if (arena != null) plugin.getShopGUI().openMain(player, arena);
            else player.closeInventory();
        } else {
            player.closeInventory();
        }
    }
}
