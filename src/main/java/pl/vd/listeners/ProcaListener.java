package pl.vd.listeners;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Vector;
import pl.vd.VillagerDefensePlugin;

import java.util.List;

/**
 * Proca - kusza z szybkim ładowaniem I (stałe, nieulepszalne), która zamiast
 * strzał wystrzeliwuje lecący bruk. Pociskiem jest Snowball (niewidzialny,
 * z wbudowaną detekcją trafień i obsługą wielostrzału), a widocznym brukiem
 * - mały FallingBlock podążający za pociskiem. Obrażenia bruku: 2.2 pkt.
 * Proca jest identyfikowana po nazwie "§6Proca".
 */
public class ProcaListener implements Listener {

    public static final String PROCA_NAME = "§6Proca";
    public static final String PROCA_METADATA = "vd-proca";

    private static final double PROJECTILE_SPEED = 3.15; // prędkość jak strzała z kuszy
    private static final double DAMAGE = 2.2;
    private static final double MULTISHOT_SPREAD_DEG = 10.0; // rozrzut 10 stopni jak w vanilla

    private final VillagerDefensePlugin plugin;

    public ProcaListener(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
    }

    /** Tworzy przedmiot Proca (kusza + szybkie ładowanie I). */
    public static ItemStack createProcaItem() {
        ItemStack proca = new ItemStack(Material.CROSSBOW, 1);
        ItemMeta meta = proca.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(PROCA_NAME);
            meta.setLore(List.of(
                    "§7Kusza z szybkim ładowaniem I (stałe),",
                    "§7która strzela lecącym brukiem.",
                    "§7Obrażenia: §f" + DAMAGE + " pkt",
                    "§7Wielostrzał (Multishot) działa normalnie."));
            meta.setUnbreakable(true);
            proca.setItemMeta(meta);
        }
        proca.addUnsafeEnchantment(Enchantment.QUICK_CHARGE, 1);
        return proca;
    }

    /** Czy przedmiot to Proca? */
    public static boolean isProca(ItemStack item) {
        if (item == null || item.getType() != Material.CROSSBOW) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasDisplayName() && PROCA_NAME.equals(meta.getDisplayName());
    }

    @EventHandler
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack bow = event.getBow();
        if (!isProca(bow)) return;

        // anuluj zwykłą strzałę - wystrzelimy bruk (snowball + widoczny kamień)
        event.setCancelled(true);

        boolean multishot = bow.containsEnchantment(Enchantment.MULTISHOT);

        Location eye = player.getEyeLocation();
        Vector baseDir = player.getLocation().getDirection().normalize();

        if (multishot) {
            // wielostrzał: 3 bruki rozrzucone o +-10 stopni (jak vanilla)
            fireProca(eye, player, baseDir.clone().multiply(PROJECTILE_SPEED));
            fireProca(eye, player, rotateYaw(baseDir.clone(), -MULTISHOT_SPREAD_DEG).multiply(PROJECTILE_SPEED));
            fireProca(eye, player, rotateYaw(baseDir.clone(), MULTISHOT_SPREAD_DEG).multiply(PROJECTILE_SPEED));
        } else {
            fireProca(eye, player, baseDir.multiply(PROJECTILE_SPEED));
        }
    }

    private void fireProca(Location eye, Player player, Vector velocity) {
        // niewidzialny pocisk (snowball) - ma wbudowaną detekcję trafień
        Snowball ball = eye.getWorld().spawn(eye, Snowball.class);
        ball.setShooter(player);
        ball.setVelocity(velocity);
        ball.setMetadata(PROCA_METADATA, new FixedMetadataValue(plugin, true));
        ball.setGravity(false);

        // widoczny lecący bruk podążający za pociskiem
        FallingBlock stone = eye.getWorld().spawn(eye, FallingBlock.class);
        BlockData cobble = Material.COBBLESTONE.createBlockData();
        stone.setBlockData(cobble);
        stone.setVelocity(velocity);
        stone.setGravity(false);
        stone.setDropItem(false);
        stone.setHurtEntities(false);
        stone.setMetadata(PROCA_METADATA, new FixedMetadataValue(plugin, true));

        // co tick bruk podąża za pociskiem; gdy pocisk zniknie (trafienie/ściana) - usuń kamień
        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (!ball.isValid()) {
                stone.remove();
                task.cancel();
                return;
            }
            stone.teleport(ball.getLocation());
        }, 1L, 1L);
    }

    /** Obraca wektor wokół osi Y o podany kąt w stopniach. */
    private Vector rotateYaw(Vector v, double degrees) {
        double rad = Math.toRadians(degrees);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        double x = v.getX();
        double z = v.getZ();
        v.setX(x * cos + z * sin);
        v.setZ(-x * sin + z * cos);
        return v;
    }

    @EventHandler
    public void onProjectileDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Snowball ball)) return;
        if (!ball.hasMetadata(PROCA_METADATA)) return;
        if (!(event.getEntity() instanceof LivingEntity)) return;
        // stałe obrażenia bruku (2.2 pkt) zamiast domyślnych obrażeń snowballa
        event.setDamage(DAMAGE);
    }
}
