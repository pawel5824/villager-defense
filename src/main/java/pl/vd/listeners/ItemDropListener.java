package pl.vd.listeners;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Set;

/**
 * Blokuje wyrzucanie ważnych przedmiotów z kitów i sklepu:
 * broń (miecze, siekiery, łuki, kusze, trójząb, wędka, różdżka, tarcza),
 * zbroja, kompas (sklep), mikstury oraz przedmioty specjalne ze sklepu
 * (ender perła, totem nieśmiertelności).
 *
 * Zbroja jest DODATKOWO przypięta do slotu zbroi: nie da się jej zdjąć,
 * przenieść, zamienić ani wyrzucić żadnym sposobem (klik, shift-klik,
 * klawisz liczbowy, Q, drag & drop) - sloty zbroi wykrywane są przez
 * SlotType.ARMOR, więc działa to w każdym widoku i wersji API.
 */
public class ItemDropListener implements Listener {

    private static final Set<Material> PROTECTED = new HashSet<>();

    /** Materiały zbroi - używane do blokowania drag & drop na slotach zbroi. */
    private static final Set<Material> ARMOR = new HashSet<>();

    static {
        // --- broń i narzędzia ---
        // miecze (w tym copper sword z nowszych wersji)
        addMaterial("WOODEN_SWORD", "STONE_SWORD", "IRON_SWORD", "GOLDEN_SWORD",
                "DIAMOND_SWORD", "NETHERITE_SWORD", "COPPER_SWORD");
        // siekiery
        addMaterial("WOODEN_AXE", "STONE_AXE", "IRON_AXE",
                "DIAMOND_AXE", "COPPER_AXE");
        // łuki, kusze, trójząb, wędka
        addMaterial("BOW", "CROSSBOW", "TRIDENT", "FISHING_ROD");
        // różdżka magika, tarcza tanka
        addMaterial("BLAZE_ROD", "SHIELD");
        // shuriken assassina (śnieżka)
        addMaterial("SNOWBALL");

        // --- kompas (sklep) ---
        addMaterial("COMPASS");

        // --- zbroja ---
        addArmorMaterial("LEATHER_HELMET", "LEATHER_CHESTPLATE", "LEATHER_LEGGINGS", "LEATHER_BOOTS");
        addArmorMaterial("CHAINMAIL_HELMET", "CHAINMAIL_CHESTPLATE", "CHAINMAIL_LEGGINGS", "CHAINMAIL_BOOTS");
        addArmorMaterial("IRON_HELMET", "IRON_CHESTPLATE", "IRON_LEGGINGS", "IRON_BOOTS");
        addArmorMaterial("GOLDEN_HELMET", "GOLDEN_CHESTPLATE", "GOLDEN_LEGGINGS", "GOLDEN_BOOTS");
        addArmorMaterial("DIAMOND_HELMET", "DIAMOND_CHESTPLATE", "DIAMOND_LEGGINGS", "DIAMOND_BOOTS");
        addArmorMaterial("NETHERITE_HELMET", "NETHERITE_CHESTPLATE", "NETHERITE_LEGGINGS", "NETHERITE_BOOTS");
        addArmorMaterial("COPPER_HELMET", "COPPER_CHESTPLATE", "COPPER_LEGGINGS", "COPPER_BOOTS");

        // --- mikstury ---
        addMaterial("POTION", "SPLASH_POTION", "LINGERING_POTION");

        // --- przedmioty specjalne ze sklepu ---
        addMaterial("ENDER_PEARL", "TOTEM_OF_UNDYING");
    }

    private static void addMaterial(String... names) {
        for (String name : names) {
            try {
                Material m = Material.matchMaterial(name);
                if (m != null) PROTECTED.add(m);
            } catch (Throwable ignored) {}
        }
    }

    private static void addArmorMaterial(String... names) {
        for (String name : names) {
            try {
                Material m = Material.matchMaterial(name);
                if (m != null) {
                    ARMOR.add(m);
                    PROTECTED.add(m);
                }
            } catch (Throwable ignored) {}
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        ItemStack dropped = event.getItemDrop().getItemStack();
        if (dropped == null) return;
        if (PROTECTED.contains(dropped.getType())) {
            // Cicho: przedmiot zostaje w ekwipunku, bez komunikatu na czacie (§1).
            event.setCancelled(true);
        }
    }

    /**
     * Zbroja jest przypięta do slotu zbroi - nie można jej zdjąć żadnym sposobem.
     * Wyjątek: w świecie RAFT zbroja może być zdejmowana.
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (isRaftWorld(player)) return; // w RAFT zdejmowanie dozwolone
        if (event.getSlotType() == InventoryType.SlotType.ARMOR) {
            event.setCancelled(true);
            pl.vd.util.AntiSpam.send(player, "§cNie możesz zdjąć zbroi!");
        }
    }

    /**
     * Blokuje przeciąganie przedmiotów na sloty zbroi (drag & drop).
     * Wyjątek: w świecie RAFT drag & drop na slotach zbroi dozwolony.
     */
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (isRaftWorld(player)) return; // w RAFT dozwolone
        for (int rawSlot : event.getRawSlots()) {
            Inventory inv = event.getView().getInventory(rawSlot);
            if (inv == null) continue;
            if (inv.equals(player.getInventory())) {
                int slot = event.getView().convertSlot(rawSlot);
                if (slot >= 36 && slot <= 39) { // buty, spodnie, klata, hełm
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    /** Sprawdza czy gracz jest w świecie RAFT. */
    private boolean isRaftWorld(Player player) {
        org.bukkit.plugin.Plugin plugin = org.bukkit.Bukkit.getPluginManager().getPlugin("ServerPlugin");
        if (plugin == null) return false;
        String worldName = plugin.getConfig().getString("raft-world-name", "RAFT");
        // equalsIgnoreCase — Paper zapisuje foldery światów małymi literami
        return player.getWorld().getName().equalsIgnoreCase(worldName);
    }
}
