package pl.vd.shop;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import pl.vd.VillagerDefensePlugin;
import pl.vd.arena.Arena;
import pl.vd.util.ItemBuilder;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class ShopGUI {

    private static final int SIZE = 54;

    // Główne menu - wyjście na środku dołu (slot 49), szmaragd w prawym rogu (slot 53)
    private static final int STAR_SLOT    = 49;
    private static final int POINTS_SLOT  = 53;

    // Podmenu kategorii - strzałka powrotu na środku dołu (slot 49), szmaragd w prawym rogu (slot 53)
    private static final int BACK_SLOT    = 49;

    // Sloty przedmiotów w podmenu kategorii - górne 4 rzędy (0-35), bez dolnego rzędu
    private static final int ITEMS_MAX_SLOT = 35;

    // Sloty kategorii w menu głównym (dokładne pozycje wg życzenia)
    private static final Map<ShopCategory, Integer> CATEGORY_SLOTS = new EnumMap<>(ShopCategory.class);

    static {
        CATEGORY_SLOTS.put(ShopCategory.FOOD,     10);
        CATEGORY_SLOTS.put(ShopCategory.SWORDS,   12);
        CATEGORY_SLOTS.put(ShopCategory.BOWS,     14);
        CATEGORY_SLOTS.put(ShopCategory.ARMOR,    16);
        CATEGORY_SLOTS.put(ShopCategory.POTIONS,  28);
        CATEGORY_SLOTS.put(ShopCategory.OTHER,    30);
        CATEGORY_SLOTS.put(ShopCategory.EFFECTS,  32);
        CATEGORY_SLOTS.put(ShopCategory.ENCHANTS, 34);
    }

    private final VillagerDefensePlugin plugin;
    private final Map<ShopCategory, Map<Integer, ShopItem>> categoryItems = new EnumMap<>(ShopCategory.class);

    public ShopGUI(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
        loadFromConfig();
    }

    // ---------- Wczytywanie z config.yml ----------

    public void loadFromConfig() {
        categoryItems.clear();
        ConfigurationSection categoriesSection = plugin.getConfig().getConfigurationSection("shop.categories");
        if (categoriesSection == null) return;

        for (ShopCategory category : ShopCategory.values()) {
            List<ShopItem> parsed = new ArrayList<>();
            ConfigurationSection catSection = categoriesSection.getConfigurationSection(category.getConfigKey());
            if (catSection != null) {
                ConfigurationSection itemsSection = catSection.getConfigurationSection("items");
                if (itemsSection != null) {
                    for (String id : itemsSection.getKeys(false)) {
                        ConfigurationSection s = itemsSection.getConfigurationSection(id);
                        if (s == null) continue;
                        ShopItem item = parseItem(id, s);
                        if (item != null) parsed.add(item);
                    }
                }
            }

            // Najpierw umieść przedmioty z jawnie podanym slotem, potem resztę w wolnych miejscach po kolei
            Map<Integer, ShopItem> slots = new java.util.HashMap<>();
            List<ShopItem> autoPlace = new ArrayList<>();
            for (ShopItem item : parsed) {
                if (item.getSlot() >= 0 && item.getSlot() < 45 && !slots.containsKey(item.getSlot())) {
                    slots.put(item.getSlot(), item);
                } else {
                    autoPlace.add(item);
                }
            }
            int nextFree = 0;
            for (ShopItem item : autoPlace) {
                while (slots.containsKey(nextFree) && nextFree < 45) nextFree++;
                if (nextFree >= 45) break;
                slots.put(nextFree, item);
                nextFree++;
            }

            categoryItems.put(category, slots);
        }
    }

    private ShopItem parseItem(String id, ConfigurationSection s) {
        ShopItemType type;
        try {
            type = ShopItemType.valueOf(s.getString("type", "ITEM").toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
        int price = s.getInt("price", 10);
        ShopItem item = new ShopItem(id, type, price);
        item.setSlot(s.getInt("slot", -1));

        switch (type) {
            case ITEM -> {
                Material mat = Material.matchMaterial(s.getString("material", "STONE"));
                item.setMaterial(mat != null ? mat : Material.STONE);
                item.setAmount(s.getInt("amount", 1));
            }
            case EFFECT -> {
                PotionEffectType effect = PotionEffectType.getByName(s.getString("effect", "SPEED"));
                item.setEffectType(effect != null ? effect : PotionEffectType.SPEED);
                item.setAmplifier(s.getInt("amplifier", 0));
            }
            case ENCHANT -> {
                Enchantment ench = Bukkit.getRegistry(Enchantment.class).get(
                    new NamespacedKey(NamespacedKey.MINECRAFT, s.getString("enchantment", "sharpness").toLowerCase())
                );
                item.setEnchantment(ench);
                item.setEnchantLevel(s.getInt("level", 1));
            }
        }
        return item;
    }

    // ---------- Otwieranie GUI ----------

    public void openMain(Player player, Arena arena) {
        ShopHolder holder = new ShopHolder(ShopHolder.ViewType.MAIN, null);
        Inventory inv = Bukkit.createInventory(holder, SIZE, Component.text("§8Sklep - Kategorie"));
        holder.setInventory(inv);

        fillGlass(inv);
        inv.setItem(STAR_SLOT, createStar());
        inv.setItem(POINTS_SLOT, createPointsItem(arena.getSessionPoints(player.getUniqueId())));

        for (Map.Entry<ShopCategory, Integer> entry : CATEGORY_SLOTS.entrySet()) {
            inv.setItem(entry.getValue(), createCategoryIcon(entry.getKey()));
        }

        player.openInventory(inv);
    }

    public void openCategory(Player player, Arena arena, ShopCategory category) {
        ShopHolder holder = new ShopHolder(ShopHolder.ViewType.CATEGORY, category);
        Inventory inv = Bukkit.createInventory(holder, SIZE, Component.text("§8Sklep - " + category.getDisplayName()));
        holder.setInventory(inv);

        fillGlass(inv);
        inv.setItem(BACK_SLOT, createBackButton());
        inv.setItem(POINTS_SLOT, createPointsItem(arena.getSessionPoints(player.getUniqueId())));

        Map<Integer, ShopItem> items = categoryItems.getOrDefault(category, Map.of());
        for (Map.Entry<Integer, ShopItem> entry : items.entrySet()) {
            inv.setItem(entry.getKey(), createShopItemIcon(entry.getValue()));
        }

        player.openInventory(inv);
    }

    // ---------- Pomocnicze metody dla listenerów ----------

    public ShopCategory getCategoryBySlot(int slot) {
        return CATEGORY_SLOTS.entrySet().stream()
                .filter(e -> e.getValue() == slot)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    public ShopItem getItemBySlot(ShopCategory category, int slot) {
        if (slot < 0 || slot >= 45) return null;
        return categoryItems.getOrDefault(category, Map.of()).get(slot);
    }

    public boolean isStarSlot(int slot)   { return slot == STAR_SLOT; }
    public boolean isBackSlot(int slot)   { return slot == BACK_SLOT; }
    public boolean isPointsSlot(int slot) { return slot == POINTS_SLOT; }

    // ---------- Zakup ----------

    public void purchase(Player player, Arena arena, ShopItem item) {
        int points = arena.getSessionPoints(player.getUniqueId());
        if (points < item.getPrice()) {
            player.sendMessage(Component.text("§cNie masz wystarczająco punktów! (masz " + points
                    + ", potrzeba " + item.getPrice() + ")"));
            return;
        }

        switch (item.getType()) {
            case ITEM -> {
                ItemStack given = new ItemStack(item.getMaterial(), Math.max(1, item.getAmount()));
                player.getInventory().addItem(given);
            }
            case EFFECT -> {
                player.addPotionEffect(new PotionEffect(
                        item.getEffectType(), Integer.MAX_VALUE, item.getAmplifier(), true, false));
            }
            case ENCHANT -> {
                ItemStack held = player.getInventory().getItemInMainHand();
                if (held.getType() == Material.AIR) {
                    player.sendMessage(Component.text("§cMusisz trzymać przedmiot w ręce, aby go ulepszyć."));
                    return;
                }
                if (item.getEnchantment() == null) {
                    player.sendMessage(Component.text("§cTo ulepszenie jest niepoprawnie skonfigurowane."));
                    return;
                }
                held.addUnsafeEnchantment(item.getEnchantment(), item.getEnchantLevel());
            }
        }

        arena.addSessionPoints(player.getUniqueId(), -item.getPrice());
        player.sendMessage(Component.text("§aKupiono! Pozostało: §e"
                + arena.getSessionPoints(player.getUniqueId()) + " pkt"));
    }

    // ---------- Budowanie przedmiotów GUI ----------

    private void fillGlass(Inventory inv) {
        ItemStack glass = createGlass();
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, glass);
        }
    }

    private ItemStack createGlass() {
        return new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE, 1).name(" ").build();
    }

    private ItemStack createStar() {
        return new ItemBuilder(Material.NETHER_STAR, 1)
                .name("§cWyjdź ze sklepu")
                .lore(List.of("§7Kliknij, aby zamknąć sklep"))
                .build();
    }

    private ItemStack createBackButton() {
        return new ItemBuilder(Material.ARROW, 1)
                .name("§eWróć do kategorii")
                .lore(List.of("§7Kliknij, aby wrócić do menu głównego"))
                .build();
    }

    private ItemStack createPointsItem(int points) {
        return new ItemBuilder(Material.EMERALD, 1)
                .name("§aTwoje punkty")
                .lore(List.of("§7Masz obecnie: §e" + points + " pkt"))
                .build();
    }

    private ItemStack createCategoryIcon(ShopCategory category) {
        return new ItemBuilder(category.getIcon(), 1)
                .name(category.getDisplayName())
                .lore(List.of("§7Kliknij, aby otworzyć kategorię"))
                .build();
    }

    private ItemStack createShopItemIcon(ShopItem item) {
        List<String> lore = new ArrayList<>();
        lore.add("§7Cena: §e" + item.getPrice() + " pkt");
        switch (item.getType()) {
            case ITEM    -> lore.add("§7Ilość: §f" + item.getAmount());
            case EFFECT  -> lore.add("§7Efekt: §f"
                    + prettify(item.getEffectType().getName()) + " "
                    + toRoman(item.getAmplifier() + 1));
            case ENCHANT -> lore.add("§7Ulepszenie: §f"
                    + prettify(item.getEnchantment().getKey().getKey()) + " "
                    + toRoman(item.getEnchantLevel()));
        }
        lore.add("");
        lore.add("§aKliknij, aby kupić");

        String name = switch (item.getType()) {
            case ITEM    -> "§f" + prettify(item.getMaterial().name());
            case EFFECT  -> "§f" + prettify(item.getEffectType().getName());
            case ENCHANT -> "§f" + prettify(item.getEnchantment().getKey().getKey());
        };

        return new ItemBuilder(item.getIconMaterial(), 1).name(name).lore(lore).build();
    }

    private String prettify(String id) {
        String[] parts = id.replace("_", " ").split(" ");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty())
                sb.append(Character.toUpperCase(part.charAt(0)))
                  .append(part.substring(1).toLowerCase())
                  .append(" ");
        }
        return sb.toString().trim();
    }

    private String toRoman(int number) {
        String[] romans = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return (number >= 0 && number < romans.length) ? romans[number] : String.valueOf(number);
    }
}
