package pl.vd.raft.spawn;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Random;

/**
 * Spawna przedmioty na wodzie w kierunku ruchu gracza.
 * 
 * Użycie:
 *   WaterItemSpawner spawner = new WaterItemSpawner();
 *   spawner.spawnAhead(player, Arrays.asList(Material.OAK_PLANKS, Material.COBBLESTONE));
 */
public class WaterItemSpawner {
    
    private final Random random = new Random();
    
    // Konfiguracja
    private int minDistance = 30;    // minimalna odległość przed graczem
    private int maxDistance = 50;    // maksymalna odległość
    private int sideOffset = 4;      // losowy offset w bok (±4 bloki)
    private int itemCount = 3;       // ile przedmiotów spawnować naraz
    private int itemLifespan = 600;  // czas życia (ticks) = 30 sekund
    
    /**
     * Spawnuje przedmioty na wodzie przed graczem w kierunku jego ruchu.
     * 
     * @param player gracz, względem którego liczymy kierunek
     * @param items lista przedmiotów do losowego wybrania
     */
    public void spawnAhead(Player player, java.util.List<Material> items) {
        World world = player.getWorld();
        Vector direction = player.getLocation().getDirection().setY(0).normalize();
        
        for (int i = 0; i < itemCount; i++) {
            // Losowa odległość przed graczem
            int distance = minDistance + random.nextInt(maxDistance - minDistance + 1);
            
            // Losowy offset w bok (prostopadle do kierunku ruchu)
            Vector side = new Vector(-direction.getZ(), 0, direction.getX());
            int sideOffsetAmount = -sideOffset + random.nextInt(sideOffset * 2 + 1);
            
            // Oblicz pozycję docelową
            Vector target = player.getLocation().toVector()
                .add(direction.multiply(distance))
                .add(side.multiply(sideOffsetAmount));
            
            Location spawnLoc = new Location(world, target.getX(), 0, target.getZ());
            
            // Znajdź poziom wody na tej pozycji
            spawnLoc.setY(world.getHighestBlockYAt(spawnLoc) - 1);
            
            // Sprawdź czy to woda
            if (spawnLoc.getBlock().getType() != Material.WATER) {
                // Szukaj wody w okolicy (±3 bloki w dół/górę)
                boolean found = false;
                for (int y = -3; y <= 3; y++) {
                    Location check = spawnLoc.clone().add(0, y, 0);
                    if (check.getBlock().getType() == Material.WATER) {
                        spawnLoc = check;
                        found = true;
                        break;
                    }
                }
                if (!found) continue; // Brak wody - pomijamy
            }
            
            // Losowy przedmiot z listy
            Material material = items.get(random.nextInt(items.size()));
            
            // Spawn item entity
            Item item = world.dropItem(spawnLoc, new org.bukkit.inventory.ItemStack(material));
            item.setPickupDelay(20); // 1 sekunda delay przed podniesieniem
            
            // Opcjonalnie: ustaw czas życia
            // item.setTicksLived(itemLifespan);
        }
    }
    
    // Gettery/Settery do konfiguracji
    public void setMinDistance(int min) { this.minDistance = min; }
    public void setMaxDistance(int max) { this.maxDistance = max; }
    public void setSideOffset(int offset) { this.sideOffset = offset; }
    public void setItemCount(int count) { this.itemCount = count; }
}