package pl.vd;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import pl.vd.util.ItemBuilder;
import java.util.Arrays;

public class ClassSelectionGUI {
    public static final String TITLE = "Wybłoś klasy";

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 9, TITLE); // 1x9 slotów
        addClassesToInventory(inv, player);
        player.openInventory(inv);
    }

    private void addClassesToInventory(Inventory inv, Player player) {
        // Definiuj klasy i ich materiały (np. ikony)
        String[] classNames = {"Medyk", "Wojownik", "łucznik", "Tank", "Magik"};
        Material[] materials = {Material.POTION, Material.IRON_SWORD, Material.BOW, Material.IRON_CHESTPLATE, Material.BOOK};

        for (int i = 0; i < classNames.length; i++) {
            int slot = i * 2; // Każy każy zajmuje 2 sloty (klasa + pusty)
            if (slot < 9) {
                ItemStack item = new ItemBuilder(materials[i], 1)
                        .name("\u00a7f" + classNames[i])
                        .lore(Arrays.asList("\u00a77Kliknij, aby wybrać"))
                        .build();
                inv.setItem(slot, item);
                // Pusty slot po klasie
                if (slot + 1 < 9) inv.setItem(slot + 1, null);
            }
        }
    }
}
