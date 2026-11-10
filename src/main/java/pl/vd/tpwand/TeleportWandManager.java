package pl.vd.tpwand;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import pl.vd.VillagerDefensePlugin;
import pl.vd.util.AntiSpam;

import java.util.List;

/**
 * Różdżka teleportacji (patyk ze znacznikiem PDC):
 * - prawy klik = teleport do miejsca, na które patrzysz (góra patrzonego bloku),
 * - sneak + prawy klik = teleport przez ścianę (dokładny punkt przed ścianą),
 * - lewy klik na gracza = teleportuje go do miejsca, na które patrzysz.
 */
public class TeleportWandManager {

    private final VillagerDefensePlugin plugin;
    private final NamespacedKey key;

    public TeleportWandManager(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "tpwand");
    }

    public boolean isWand(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(key);
    }

    public void give(Player player) {
        ItemStack item = new ItemStack(Material.STICK);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        meta.displayName(Component.text("§bRóżdżka teleportacji"));
        meta.lore(List.of(
                Component.text("§7Prawy klik - teleport do miejsca, na które patrzysz"),
                Component.text("§7Sneak + prawy klik - teleport przez ścianę (dokładny punkt)"),
                Component.text("§7Lewy klik na gracza - teleportuje go do miejsca, na które patrzysz")));
        meta.setUnbreakable(true);
        item.setItemMeta(meta);
        player.getInventory().setItemInMainHand(item);
        AntiSpam.send(player, "§aDostałeś różdżkę teleportacji (prawy klik = skok).");
    }

    public void teleportSelf(Player player, boolean exact) {
        Location dest = destination(player, exact);
        if (dest == null) {
            AntiSpam.send(player, "§cNie ma gdzie teleportować (patrzysz w niebo?).");
            return;
        }
        dest.setYaw(player.getLocation().getYaw());
        dest.setPitch(player.getLocation().getPitch());
        player.teleport(dest);
        AntiSpam.send(player, "§aTeleport!");
    }

    public void teleportPlayer(Player actor, Player victim) {
        Location dest = destination(actor, false);
        if (dest == null) {
            AntiSpam.send(actor, "§cNie ma gdzie teleportować (patrzysz w niebo?).");
            return;
        }
        dest.setYaw(victim.getLocation().getYaw());
        dest.setPitch(victim.getLocation().getPitch());
        victim.teleport(dest);
        AntiSpam.send(actor, "§aTeleportowano §e" + victim.getName() + "§a.");
        AntiSpam.send(victim, "§aZostałeś teleportowany przez różdżkę.");
    }

    /** Miejsce docelowe: góra patrzonego bloku, punkt przed ścianą (exact) albo skok w przód. */
    private Location destination(Player player, boolean exact) {
        Block block = player.getTargetBlockExact(120);
        if (block == null) {
            // patrzysz w niebo - skok do przodu
            Vector dir = player.getEyeLocation().getDirection();
            Location hop = player.getEyeLocation().clone().add(dir.multiply(30));
            hop.setY(hop.getY() - 1.6);
            return hop;
        }
        if (exact) {
            // punkt przed ścianą, na którą patrzymy (teleport "przez ścianę")
            Location eye = player.getEyeLocation();
            Location center = block.getLocation().add(0.5, 0.5, 0.5);
            double dist = Math.max(eye.distance(center) - 0.6, 0.2);
            Location dest = eye.clone().add(eye.getDirection().normalize().multiply(dist));
            dest.setY(dest.getY() - 1.6);
            return dest;
        }
        // góra patrzonego bloku
        return block.getLocation().add(0.5, 1.0, 0.5);
    }
}
