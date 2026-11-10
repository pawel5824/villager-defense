package pl.vd.raft.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import pl.vd.raft.RaftPlugin;
import pl.vd.raft.entity.RaftEntity;
import pl.vd.raft.entity.RaftManager;
import pl.vd.raft.entity.RaftPersistence;
import pl.vd.raft.items.ItemManager;

/**
 * Żagiel tratwy z przedmiotu „żagiel" (CMD 4005) — wzorzec
 * {@link AntennaPlaceListener}: PPM przedmiotem, przedmiot zużywany, zapis od
 * razu, zero komunikatów (AGENTS.md pkt 1 — porażka = po prostu nic).
 *
 * Zasady:
 *  - PPM przedmiotem w POKŁAD tratwy (komórka RAFT/FLOOR) stawia maszt z żaglem
 *    na klikniętej komórce (komponenty MAST + SAIL, zapis w raft-saves.yml).
 *    Świeżo postawiony żagiel jest ZWINIĘTY — tratwa płynie prądem wody.
 *  - PPM (dowolnym przedmiotem albo pustą ręką) w KOMÓRKĘ stojącego żagla
 *    rozwija go albo zwija — dokładnie jak w oryginalnym Rafcie, gdzie żagiel
 *    obsługuje się kliknięciem w niego, a nie trzymanym przedmiotem. Dzięki
 *    temu postawienie żagla ostatnią sztuką nie zostawia gracza bez sposobu,
 *    żeby go rozwinąć.
 *  - Shift + PPM przedmiotem „żagiel" demontuje maszt z żaglem (bez zwrotu
 *    przedmiotu — jak rozbiórka komórek siekierą); tratwa wraca na prąd wody.
 *  - Jeden żagiel na tratwę (komponenty w RaftState są kluczowane nazwą).
 *
 * Geometrii jeszcze nie ma (stub w paczce), więc widać tylko efekt fizyczny
 * i stan w /raft debug.
 */
public class SailPlaceListener implements Listener {

    /** Zasięg liczenia się tratwy — jak budowanie (6 bloków). */
    private static final double PLACE_RANGE = RaftBuildListener.BUILD_RANGE;

    private final RaftPlugin plugin;

    public SailPlaceListener(RaftPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK
                && event.getAction() != Action.RIGHT_CLICK_AIR) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.isCancelled()) return; // inny listener obsłużył ten klik

        ItemManager im = plugin.getItemManager();
        Player player = event.getPlayer();
        RaftEntity raft = RaftManager.getInstance().getNearestRaft(player, PLACE_RANGE);
        if (raft == null) return;

        ItemStack item = event.getItem(); // null przy pustej ręce
        boolean sailInHand = im != null && im.isCustomItem(item, "zagiel");

        // 1. Żagiel stoi: rozkładanie/zwijanie i demontaż.
        //    Klik anulujemy tylko wtedy, gdy naprawdę coś robimy — inaczej
        //    trzymanie żagla w ręce blokowałoby skrzynie i sieci na tratwie.
        if (raft.hasSail()) {
            if (sailInHand && player.isSneaking()) {
                event.setCancelled(true); // nigdy nie stawiaj waniliowego heavy_core
                if (raft.demountSail()) RaftPersistence.save(raft);
                return;
            }
            Vector hit = raycastCell(raft, player);
            if (hit == null || !isSailCell(raft, hit)) return;
            event.setCancelled(true);
            raft.setSailOpen(!raft.isSailOpen());
            RaftPersistence.save(raft);
            return;
        }

        // 2. Stawianie: tylko przedmiotem „żagiel".
        if (!sailInHand) return;

        RaftBuildListener build = plugin.getRaftBuildListener();
        if (build == null) return;
        Vector hit = raycastCell(raft, player);
        if (hit == null) hit = build.nearestCellTo(raft, player.getLocation().toVector());
        if (hit == null) return;

        // Maszt stoi tylko na pokładzie (y = 0 to deska; ściany mają y > 0),
        // a mountSail odrzuca sieci/skrzynie/schody.
        if (hit.getY() != 0) return;
        if (!raft.mountSail(hit)) return;

        event.setCancelled(true); // nigdy nie stawiaj waniliowego heavy_core w świecie
        consume(player, item);
        RaftPersistence.save(raft);
    }

    /**
     * Zabezpieczenie: żagiel stawia się PPM, więc gdyby Bukkit zamienił klik
     * w postawienie bloku (np. w ścianę wyspy), anuluj — heavy_core nie może
     * zostać w świecie.
     */
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemManager im = plugin.getItemManager();
        if (im != null && im.isCustomItem(event.getItemInHand(), "zagiel")) {
            event.setCancelled(true);
        }
    }

    /** Raycast po komórkach tratwy (ten sam, co przy budowaniu i skrzyniach). */
    private Vector raycastCell(RaftEntity raft, Player player) {
        RaftBuildListener build = plugin.getRaftBuildListener();
        if (build == null) return null;
        return build.raycastCell(raft, player.getEyeLocation().toVector(),
                player.getEyeLocation().getDirection());
    }

    /** Czy trafiona komórka to komórka, na której stoi żagiel? */
    private boolean isSailCell(RaftEntity raft, Vector hit) {
        Vector sailCell = raft.getSailCell();
        if (sailCell == null) return false;
        return Math.abs(sailCell.getX() - hit.getX()) < 0.01
                && Math.abs(sailCell.getZ() - hit.getZ()) < 0.01;
    }

    /** Zużywa 1 sztukę przedmiotu z ręki gracza. */
    private void consume(Player player, ItemStack item) {
        if (item == null) return;
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }
    }
}
