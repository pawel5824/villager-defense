package pl.vd.raft.crafting;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.NamespacedKey;
import pl.vd.raft.RaftPlugin;

import java.util.HashMap;
import java.util.Map;

/**
 * Zarządza customowymi recepturami craftingowymi.
 */
public class RecipeManager {

    private final RaftPlugin plugin;
    private final Map<String, ShapedRecipe> shapedRecipes = new HashMap<>();
    private final Map<String, ShapelessRecipe> shapelessRecipes = new HashMap<>();

    public RecipeManager(RaftPlugin plugin) {
        this.plugin = plugin;
        initializeRecipes();
    }

    /**
     * Inicjalizuje wszystkie receptury.
     *
     * RECEPTURY CRAFTINGU WYŁĄCZONE (decyzja: inna funkcja wytwarzania — GUI
     * „Wytwarzanie"). Wszystkie metody register*Recipe() zostały w kodzie
     * (jako gotowe wzorce przyszłego GUI), ale nie są już wywoływane — waniliowy
     * stół/ekwipunek nie wytwarza już przedmiotów rafta.
     */
    private void initializeRecipes() {
    }

    // === Narzędzia ===

    private void registerWoodenSpearRecipe() {
        NamespacedKey key = new NamespacedKey(plugin.getParent(), "wooden_spear");
        ShapedRecipe recipe = new ShapedRecipe(key, plugin.getItemManager().createItem("wooden_spear"));
        recipe.shape(
            " W",
            " S",
            " S"
        );
        recipe.setIngredient('W', Material.OAK_PLANKS);
        recipe.setIngredient('S', Material.STICK);
        plugin.getServer().addRecipe(recipe);
        shapedRecipes.put("wooden_spear", recipe);
    }

    private void registerStoneSpearRecipe() {
        NamespacedKey key = new NamespacedKey(plugin.getParent(), "stone_spear");
        ShapedRecipe recipe = new ShapedRecipe(key, plugin.getItemManager().createItem("stone_spear"));
        recipe.shape(
            " F",
            " S",
            " S"
        );
        recipe.setIngredient('F', Material.FLINT);
        recipe.setIngredient('S', Material.STICK);
        plugin.getServer().addRecipe(recipe);
        shapedRecipes.put("stone_spear", recipe);
    }



    private void registerWoodenAxeRecipe() {
        NamespacedKey key = new NamespacedKey(plugin.getParent(), "wooden_axe");
        ShapedRecipe recipe = new ShapedRecipe(key, plugin.getItemManager().createItem("wooden_axe"));
        recipe.shape(
            "PP",
            "PS",
            " S"
        );
        recipe.setIngredient('P', Material.OAK_PLANKS);
        recipe.setIngredient('S', Material.STICK);
        plugin.getServer().addRecipe(recipe);
        shapedRecipes.put("wooden_axe", recipe);
    }

    private void registerStoneAxeRecipe() {
        NamespacedKey key = new NamespacedKey(plugin.getParent(), "stone_axe");
        ShapedRecipe recipe = new ShapedRecipe(key, plugin.getItemManager().createItem("stone_axe"));
        recipe.shape(
            "CC",
            "CS",
            " S"
        );
        recipe.setIngredient('C', Material.COBBLESTONE);
        recipe.setIngredient('S', Material.STICK);
        plugin.getServer().addRecipe(recipe);
        shapedRecipes.put("stone_axe", recipe);
    }

    private void registerIronAxeRecipe() {
        NamespacedKey key = new NamespacedKey(plugin.getParent(), "iron_axe");
        ShapedRecipe recipe = new ShapedRecipe(key, plugin.getItemManager().createItem("iron_axe"));
        recipe.shape(
            "II",
            "IS",
            " S"
        );
        recipe.setIngredient('I', Material.IRON_INGOT);
        recipe.setIngredient('S', Material.STICK);
        plugin.getServer().addRecipe(recipe);
        shapedRecipes.put("iron_axe", recipe);
    }

    private void registerDiamondAxeRecipe() {
        NamespacedKey key = new NamespacedKey(plugin.getParent(), "diamond_axe");
        ShapedRecipe recipe = new ShapedRecipe(key, plugin.getItemManager().createItem("diamond_axe"));
        recipe.shape(
            "DD",
            "DS",
            " S"
        );
        recipe.setIngredient('D', Material.DIAMOND);
        recipe.setIngredient('S', Material.STICK);
        plugin.getServer().addRecipe(recipe);
        shapedRecipes.put("diamond_axe", recipe);
    }

    private void registerDivingMaskRecipe() {
        NamespacedKey key = new NamespacedKey(plugin.getParent(), "diving_mask");
        ShapedRecipe recipe = new ShapedRecipe(key, plugin.getItemManager().createItem("diving_mask"));
        recipe.shape(
            "LLL",
            "L L"
        );
        recipe.setIngredient('L', Material.LEATHER);
        plugin.getServer().addRecipe(recipe);
        shapedRecipes.put("diving_mask", recipe);
    }

    private void registerOxygenBottleRecipe() {
        NamespacedKey key = new NamespacedKey(plugin.getParent(), "oxygen_bottle");
        ShapelessRecipe recipe = new ShapelessRecipe(key, plugin.getItemManager().createItem("oxygen_bottle"));
        recipe.addIngredient(Material.GLASS_BOTTLE);
        recipe.addIngredient(Material.SUGAR);
        plugin.getServer().addRecipe(recipe);
        shapelessRecipes.put("oxygen_bottle", recipe);
    }

    private void registerHookRecipe() {
        NamespacedKey key = new NamespacedKey(plugin.getParent(), "hook");
        ItemStack result = plugin.getItemManager().createItem("hook");
        if (result == null) return;
        ShapelessRecipe recipe = new ShapelessRecipe(key, result);
        ItemStack rope = plugin.getItemManager().createItem("rope");
        if (rope != null) recipe.addIngredient(rope);
        recipe.addIngredient(Material.FLINT);
        plugin.getServer().addRecipe(recipe);
        shapelessRecipes.put("hook", recipe);
    }

    // === Bronie ===

    // === Materiały ===

    private void registerRopeRecipe() {
        NamespacedKey key = new NamespacedKey(plugin.getParent(), "rope");
        ShapelessRecipe recipe = new ShapelessRecipe(key, plugin.getItemManager().createItem("rope"));
        // Lina (rope) teraz z 2× Lina(LEAD)? — nie: lina powstaje z 2× sznur waniliowy
        recipe.addIngredient(Material.STRING);
        recipe.addIngredient(Material.STRING);
        plugin.getServer().addRecipe(recipe);
        shapelessRecipes.put("rope", recipe);
    }

    private void registerNailRecipe() {
        NamespacedKey key = new NamespacedKey(plugin.getParent(), "nail");
        ShapelessRecipe recipe = new ShapelessRecipe(key, plugin.getItemManager().createItem("nail"));
        recipe.addIngredient(Material.IRON_NUGGET);
        recipe.addIngredient(Material.IRON_NUGGET);
        plugin.getServer().addRecipe(recipe);
        shapelessRecipes.put("nail", recipe);
    }





    private void registerSharkMeatRecipe() {
        NamespacedKey key = new NamespacedKey(plugin.getParent(), "shark_meat");
        ShapelessRecipe recipe = new ShapelessRecipe(key, plugin.getItemManager().createItem("shark_meat"));
        recipe.addIngredient(Material.BEEF);
        plugin.getServer().addRecipe(recipe);
        shapelessRecipes.put("shark_meat", recipe);
    }

    // === Woda ===

    private void registerDirtyWaterRecipe() {
        NamespacedKey key = new NamespacedKey(plugin.getParent(), "dirty_water");
        ShapelessRecipe recipe = new ShapelessRecipe(key, plugin.getItemManager().createItem("dirty_water"));
        recipe.addIngredient(Material.WATER_BUCKET);
        plugin.getServer().addRecipe(recipe);
        shapelessRecipes.put("dirty_water", recipe);
    }

    private void registerCleanWaterRecipe() {
        NamespacedKey key = new NamespacedKey(plugin.getParent(), "clean_water");
        ShapelessRecipe recipe = new ShapelessRecipe(key, plugin.getItemManager().createItem("clean_water"));
        recipe.addIngredient(Material.GLASS_BOTTLE);
        recipe.addIngredient(Material.SUGAR);
        plugin.getServer().addRecipe(recipe);
        shapelessRecipes.put("clean_water", recipe);
    }

    // === Budowlane ===

    private void registerWoodenFoundationRecipe() {
        NamespacedKey key = new NamespacedKey(plugin.getParent(), "wooden_foundation");
        ShapedRecipe recipe = new ShapedRecipe(key, plugin.getItemManager().createItem("wooden_foundation"));
        recipe.shape(
            "PPP",
            "PPP",
            "PPP"
        );
        recipe.setIngredient('P', Material.OAK_PLANKS);
        plugin.getServer().addRecipe(recipe);
        shapedRecipes.put("wooden_foundation", recipe);
    }

    private void registerStoneFoundationRecipe() {
        NamespacedKey key = new NamespacedKey(plugin.getParent(), "stone_foundation");
        ShapedRecipe recipe = new ShapedRecipe(key, plugin.getItemManager().createItem("stone_foundation"));
        recipe.shape(
            "CCC",
            "CCC",
            "CCC"
        );
        recipe.setIngredient('C', Material.COBBLESTONE);
        plugin.getServer().addRecipe(recipe);
        shapedRecipes.put("stone_foundation", recipe);
    }

    private void registerCollectionNetRecipe() {
        NamespacedKey key = new NamespacedKey(plugin.getParent(), "collection_net");
        ShapedRecipe recipe = new ShapedRecipe(key, plugin.getItemManager().createItem("collection_net"));
        recipe.shape(
            "SSS",
            "S S",
            "SSS"
        );
        recipe.setIngredient('S', Material.STRING);
        plugin.getServer().addRecipe(recipe);
        shapedRecipes.put("collection_net", recipe);
    }

    private void registerRopeLadderRecipe() {
        NamespacedKey key = new NamespacedKey(plugin.getParent(), "rope_ladder");
        ShapedRecipe recipe = new ShapedRecipe(key, plugin.getItemManager().createItem("rope_ladder"));
        recipe.shape(
            "S",
            "L",
            "S"
        );
        recipe.setIngredient('S', Material.STICK);
        recipe.setIngredient('L', Material.LEAD); // „Lina" (rope) zamiast sznura
        plugin.getServer().addRecipe(recipe);
        shapedRecipes.put("rope_ladder", recipe);
    }

    // === Specjalne ===

    private void registerBinocularsRecipe() {
        NamespacedKey key = new NamespacedKey(plugin.getParent(), "binoculars");
        ShapedRecipe recipe = new ShapedRecipe(key, plugin.getItemManager().createItem("binoculars"));
        recipe.shape(
            "GG",
            "SS"
        );
        recipe.setIngredient('G', Material.GLASS);
        recipe.setIngredient('S', Material.STICK);
        plugin.getServer().addRecipe(recipe);
        shapedRecipes.put("binoculars", recipe);
    }

    /**
     * Pobiera recepturę po ID.
     */
    public ShapedRecipe getShapedRecipe(String id) {
        return shapedRecipes.get(id);
    }

    /**
     * Pobiera recepturę shapeless po ID.
     */
    public ShapelessRecipe getShapelessRecipe(String id) {
        return shapelessRecipes.get(id);
    }

    /**
     * Sprawdza czy receptura istnieje.
     */
    public boolean hasRecipe(String id) {
        return shapedRecipes.containsKey(id) || shapelessRecipes.containsKey(id);
    }

    /**
     * Usuwa recepturę.
     */
    public void removeRecipe(String id) {
        ShapedRecipe shaped = shapedRecipes.remove(id);
        if (shaped != null) {
            plugin.getServer().removeRecipe(shaped.getKey());
        }
        ShapelessRecipe shapeless = shapelessRecipes.remove(id);
        if (shapeless != null) {
            plugin.getServer().removeRecipe(shapeless.getKey());
        }
    }
}
