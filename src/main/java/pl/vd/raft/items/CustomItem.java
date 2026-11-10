package pl.vd.raft.items;

import org.bukkit.inventory.ItemStack;

/**
 * Reprezentuje customowy przedmiot w grze.
 */
public class CustomItem {

    private final String id;
    private final ItemStack itemStack;
    private final int maxStackSize;

    public CustomItem(String id, ItemStack itemStack, int maxStackSize) {
        this.id = id;
        this.itemStack = itemStack;
        this.maxStackSize = maxStackSize;
    }

    public String getId() { return id; }
    public ItemStack getItemStack() { return itemStack; }
    public int getMaxStackSize() { return maxStackSize; }

    /**
     * Tworzy kopię przedmiotu.
     */
    public ItemStack createStack(int amount) {
        ItemStack stack = itemStack.clone();
        stack.setAmount(amount);
        return stack;
    }
}
