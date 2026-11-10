package pl.vd.raft.listeners;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Auto-pickup przedmiotów z wyspy/tratwy:
 *  1. Przedmiot trafia prosto do ekwipunku gracza
 *  2. Jeśli eq pełne → spawn ZWYKŁEGO dropu (Item) jak wyrzuconego klawiszem Q:
 *     marker to przedmiot terakota, a nad nim TextDisplay "[liczba] nazwa"
 *  3. Wejście na marker (jak w wanilli) → prawdziwy przedmiot do ekwipunku
 *  4. TTL 2min → auto-usunięcie
 *
 * Marker jest zwykłym Item, więc wygląda i zachowuje się jak drop waniliowy
 * (leży/kołysze się na wodzie). Podniesienie terakoty jest anulowane — zamiast
 * niej gracz dostaje oryginalny przedmiot, który pickup reprezentuje.
 */
public class ItemPickupManager implements Listener {

    private static final String META_DISPLAY = "vd_pickup_display";
    private static final String META_ITEM = "vd_pickup_item";
    private static final String META_QTY = "vd_pickup_qty";

    /** Czas życia display entity (ticki). */
    private static final long PICKUP_TTL_TICKS = 2400; // 2 minuty

    /** Mapa: entityID markera (Item) → (marker, text display, przedmiot). */
    private final Map<Integer, PickupData> pickupMap = new HashMap<>();

    private final pl.vd.raft.RaftPlugin plugin;

    public ItemPickupManager(pl.vd.raft.RaftPlugin plugin) {
        this.plugin = plugin;
    }

    // ══════════════════════════════════════════════════════════════
    //  PUBLICZNE API — TreeBreakManager wywołuje to bezpośrednio
    // ══════════════════════════════════════════════════════════════

    /**
     * Próbuje dodać przedmiot do ekwipunku gracza.
     * Jeśli eq pełne → spawn dropu-markera na świecie (terakota + "[n] nazwa").
     */
    public void tryPickup(Player player, ItemStack item) {
        // Najpierw sprawdź czy jest miejsce
        HashMap<Integer, ItemStack> leftOver = player.getInventory().addItem(item.clone());
        if (leftOver.isEmpty()) {
            // Wszystko się zmieściło — gotowe
            return;
        }

        // Częściowo się zmieściło — reszta idzie na marker-drop
        for (ItemStack remaining : leftOver.values()) {
            spawnPickupDisplay(player, remaining);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  BLOCK BREAK — auto-pickup zniszczonych bloków
    // ══════════════════════════════════════════════════════════════

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        World world = block.getWorld();

        // Tylko świat Raft
        String raftWorldName = plugin.getConfig().getString("raft-world-name", "RAFT");
        if (!world.getName().equalsIgnoreCase(raftWorldName)) return;

        // Sprawdź czy blok jest na raftcie (nie na highpie itp.)
        if (!plugin.getIslandManager().isOnRaft(player)) return;

        // Anuluj vanilla drops
        event.setDropItems(false);

        // Stwórz drop z tego bloku
        List<ItemStack> drops = new ArrayList<>(block.getDrops(player.getInventory().getItemInMainHand()));
        for (ItemStack drop : drops) {
            tryPickup(player, drop);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  PODNIESIENIE — wejście na marker (jak drop waniliowy)
    // ══════════════════════════════════════════════════════════════

    @EventHandler
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        Item item = event.getItem();
        if (!item.hasMetadata(META_DISPLAY)) return;

        // Marker to tylko „opakowanie" — zamiast terakoty gracz dostaje
        // oryginalny przedmiot, który ten pickup reprezentuje.
        event.setCancelled(true);

        PickupData data = pickupMap.get(item.getEntityId());
        if (data == null) return;
        if (data.item == null || data.item.getType() == Material.AIR) {
            removeEntities(pickupMap.remove(item.getEntityId()));
            return;
        }

        // Dźwięk podniesienia
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1.2f);

        // Dodaj prawdziwy przedmiot do ekwipunku
        HashMap<Integer, ItemStack> leftOver = player.getInventory().addItem(data.item.clone());
        if (leftOver.isEmpty()) {
            // Zmieściło się — marker znika
            removeEntities(pickupMap.remove(item.getEntityId()));
            return;
        }
        // Eq dalej pełne — zaktualizuj zapas markera (ilość mogła się zmienić)
        ItemStack rest = leftOver.values().iterator().next();
        PickupData updated = new PickupData(data.itemEntity, data.textDisplay, rest.clone());
        pickupMap.put(item.getEntityId(), updated);
        item.setMetadata(META_ITEM, new FixedMetadataValue(plugin.getParent(), rest.clone()));
        item.setMetadata(META_QTY, new FixedMetadataValue(plugin.getParent(), rest.getAmount()));
        if (updated.textDisplay != null && updated.textDisplay.isValid()) {
            updated.textDisplay.setText(label(updated.item));
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  SPAWN DROPU-MARKERA
    // ══════════════════════════════════════════════════════════════

    private void spawnPickupDisplay(Player player, ItemStack item) {
        World world = player.getWorld();
        // Drop u stóp gracza — dokładnie jak wyrzucony klawiszem Q
        Location loc = player.getLocation().clone().add(0, 0.1, 0);

        // 1. Marker: zwykła terakota jako Item (drop waniliowy). Unikalny tag NBT
        //    zapobiega sklejaniu się markerów w jeden stos (i z cudzą terakotą).
        ItemStack marker = new ItemStack(Material.TERRACOTTA);
        ItemMeta meta = marker.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin.getParent(), "vd_pickup_marker"),
                    PersistentDataType.STRING, UUID.randomUUID().toString());
            marker.setItemMeta(meta);
        }

        Item itemEntity = world.spawn(loc, Item.class, e -> {
            e.setItemStack(marker);
            e.setPickupDelay(10);
            e.setCanMobPickup(false);
            e.setPersistent(false); // nie zapisuj do chunku — marker to wizual tymczasowy
            e.setMetadata(META_DISPLAY, new FixedMetadataValue(plugin.getParent(), true));
            e.setMetadata(META_ITEM, new FixedMetadataValue(plugin.getParent(), item.clone()));
            e.setMetadata(META_QTY, new FixedMetadataValue(plugin.getParent(), item.getAmount()));
        });

        // Lekki „podskok" jak przy wyrzuceniu przedmiotu (potem spoczywa)
        itemEntity.setVelocity(new Vector(
                (Math.random() - 0.5) * 0.15, 0.2, (Math.random() - 0.5) * 0.15));

        // 2. TextDisplay — napis "[liczba] nazwa" nad markerem
        TextDisplay textDisplay = world.spawn(loc.clone().add(0, 0.8, 0), TextDisplay.class, e -> {
            e.setText(label(item));
            e.setAlignment(TextDisplay.TextAlignment.CENTER);
            e.setBillboard(Display.Billboard.CENTER);
            e.setBackgroundColor(Color.fromARGB(0, 0, 0, 0)); // przezroczyste tło
            e.setPersistent(false);
        });

        // Zapisz do mapy (klucz: id encji markera-Item)
        pickupMap.put(itemEntity.getEntityId(),
                new PickupData(itemEntity, textDisplay, item.clone()));

        // Auto-usunięcie po 2 minutach
        Bukkit.getScheduler().runTaskLater(plugin.getParent(), () -> {
            PickupData data = pickupMap.remove(itemEntity.getEntityId());
            if (data != null) {
                removeEntities(data);
            }
        }, PICKUP_TTL_TICKS);
    }

    /** Format etykiety: "[(liczba)x] Nazwa przedmiotu" (np. "[5x] Oak Log"). */
    private String label(ItemStack item) {
        String name = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                ? item.getItemMeta().getDisplayName()
                : formatMaterialName(item.getType());
        return "[" + item.getAmount() + "x] " + name;
    }

    // ══════════════════════════════════════════════════════════════
    //  CZYSZCZENIE
    // ══════════════════════════════════════════════════════════════

    private void removeEntities(PickupData data) {
        if (data == null) return;
        if (data.itemEntity != null && data.itemEntity.isValid()) data.itemEntity.remove();
        if (data.textDisplay != null && data.textDisplay.isValid()) data.textDisplay.remove();
    }

    /** Usuwa wszystkie aktywne pickup display (np. przy restarcie gry). */
    public void clearAll() {
        for (PickupData data : pickupMap.values()) {
            removeEntities(data);
        }
        pickupMap.clear();
    }

    // ══════════════════════════════════════════════════════════════
    //  NARZĘDZIA
    // ══════════════════════════════════════════════════════════════

    /** Formatuje nazwę materialu: OAK_LOG → Oak Log */
    private String formatMaterialName(Material mat) {
        String name = mat.name().toLowerCase().replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        for (String word : name.split(" ")) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)))
                  .append(word.substring(1))
                  .append(' ');
            }
        }
        return sb.toString().trim();
    }

    // ══════════════════════════════════════════════════════════════
    //  DANE
    // ══════════════════════════════════════════════════════════════

    private record PickupData(
        Item itemEntity,
        TextDisplay textDisplay,
        ItemStack item
    ) {}
}
