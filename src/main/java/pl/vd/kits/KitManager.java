package pl.vd.kits;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import pl.vd.ability.AbilityManager;
import pl.vd.ability.AbilityType;
import pl.vd.shield.ShieldManager;
import pl.vd.util.ItemBuilder;

import java.util.*;

public class KitManager {

    public static final String KIT_GUI_TITLE = "Wybierz kit";

    private final Map<String, Kit> kits = new LinkedHashMap<>();
    private final Map<UUID, String> chosenKit = new HashMap<>();
    private final AbilityManager abilityManager;
    private final ShieldManager shieldManager;

    public KitManager(AbilityManager abilityManager, ShieldManager shieldManager) {
        this.abilityManager = abilityManager;
        this.shieldManager = shieldManager;
        registerDefaults();
    }

    private void registerDefaults() {
        // Wojownik - mocny w zwarciu
        {
            Material copperSwordMat = findMaterial("COPPER_SWORD", "STONE_SWORD", "WOODEN_SWORD");
            ItemStack icon = new ItemBuilder(copperSwordMat != null ? copperSwordMat : Material.WOODEN_SWORD, 1).name("§cWojownik").build();
            ItemStack sword = new ItemBuilder(copperSwordMat != null ? copperSwordMat : Material.STONE_SWORD, 1).name("§fMiecz Wojownika").build();
            Enchantment ench_sharp = Enchantment.getByName("DAMAGE_ALL");
            if (ench_sharp != null) sword.addUnsafeEnchantment(ench_sharp, 1);
            makeUnbreakable(sword);
            makeUnbreakable(icon);

            ItemStack helmet = new ItemStack(Material.CHAINMAIL_HELMET);
            makeUnbreakable(helmet);
            Material copperChestMat = findMaterial("COPPER_CHESTPLATE", "IRON_CHESTPLATE", "CHAINMAIL_CHESTPLATE");
            ItemStack chest = new ItemStack(copperChestMat != null ? copperChestMat : Material.IRON_CHESTPLATE);
            Enchantment ench_prot = Enchantment.getByName("PROTECTION_ENVIRONMENTAL");
            if (ench_prot != null) chest.addUnsafeEnchantment(ench_prot, 1);
            makeUnbreakable(chest);

            ItemStack leggings = new ItemStack(Material.CHAINMAIL_LEGGINGS);
            makeUnbreakable(leggings);
            ItemStack boots = new ItemStack(Material.CHAINMAIL_BOOTS);
            makeUnbreakable(boots);

            List<ItemStack> refill = List.of(
                    new ItemBuilder(Material.COOKED_MUTTON, 5).name("§6Pieczona baranina").build(),
                    createSplashPotion(PotionType.STRENGTH, "§cMikstura siły I", List.of("§7Siła I przez 3:00"))
            );

            kits.put("wojownik", new Kit(
                    "wojownik", "§cWojownik",
                    icon,
                    List.of(sword),
                    refill,
                    helmet,
                    chest,
                    leggings,
                    boots
            ));
        }

        // Łucznik - dystans
        {
            ItemStack icon = new ItemBuilder(Material.BOW, 1).name("§aŁucznik").build();
            ItemStack bow = new ItemStack(Material.BOW, 1);
            ItemMeta bowMeta = bow.getItemMeta();
            if (bowMeta != null) {
                bowMeta.setDisplayName("§fŁuk");
                Enchantment ench_power = Enchantment.getByName("ARROW_DAMAGE");
                if (ench_power != null) bowMeta.addEnchant(ench_power, 1, true);
                bowMeta.setUnbreakable(true);
                bow.setItemMeta(bowMeta);
            }
            makeUnbreakable(icon);
            Material arrowMat = findMaterial("SPECTRAL_ARROW", "ARROW");
            ItemStack arrows = new ItemStack(arrowMat != null ? arrowMat : Material.ARROW, 16);
            ItemMeta arrowsMeta = arrows.getItemMeta();
            if (arrowsMeta != null) arrowsMeta.setDisplayName("§fWidmowe strzały");
            arrows.setItemMeta(arrowsMeta);

            // custom dagger (Sztylet)
            ItemStack dagger = new ItemStack(Material.WOODEN_SWORD, 1);
            ItemMeta daggerMeta = dagger.getItemMeta();
            if (daggerMeta != null) {
                daggerMeta.setDisplayName("§cSztylet");
                // Try to add attribute modifiers via reflection for compatibility
                tryAddAttributeModifier(daggerMeta, "GENERIC_ATTACK_DAMAGE", 3.0, "ADD_NUMBER", "HAND", "dagger_dmg");
                tryAddAttributeModifier(daggerMeta, "GENERIC_ATTACK_SPEED", 2.0, "ADD_NUMBER", "HAND", "dagger_speed");
                dagger.setItemMeta(daggerMeta);
            }
            makeUnbreakable(dagger);

            ItemStack helmet = new ItemStack(Material.CHAINMAIL_HELMET);
            makeUnbreakable(helmet);
            ItemStack chest = new ItemStack(Material.LEATHER_CHESTPLATE);
            ItemMeta chestMeta = chest.getItemMeta();
            if (chestMeta instanceof LeatherArmorMeta) {
                LeatherArmorMeta lam = (LeatherArmorMeta) chestMeta;
                lam.setColor(Color.RED);
                lam.setDisplayName("§cSkórzany napierśnik");
                chest.setItemMeta(lam);
            }
            makeUnbreakable(chest);

            List<ItemStack> refill = List.of(
                    new ItemBuilder(Material.COOKED_CHICKEN, 5).name("§6Pieczony kurczak").build()
            );

            kits.put("lucznik", new Kit(
                    "lucznik", "§aŁucznik",
                    icon,
                    List.of(bow, arrows, dagger),
                    refill,
                    helmet,
                    chest,
                    null,
                    null
            ));
        }

        // Tank - wytrzymały, chroni wieśniaków
        {
            ItemStack icon = shieldManager.createKnockbackShield();
            makeUnbreakable(icon);
            ItemStack sword = new ItemBuilder(Material.STONE_SWORD, 1).name("§fMiecz").build();
            Enchantment ench_sharp2 = Enchantment.getByName("DAMAGE_ALL");
            if (ench_sharp2 != null) sword.addUnsafeEnchantment(ench_sharp2, 1);
            makeUnbreakable(sword);

            Material copperHelmMat = findMaterial("COPPER_HELMET", "IRON_HELMET", "CHAINMAIL_HELMET");
            ItemStack helmet = new ItemStack(copperHelmMat != null ? copperHelmMat : Material.IRON_HELMET);
            makeUnbreakable(helmet);
            ItemStack chest = new ItemStack(Material.IRON_CHESTPLATE);
            Enchantment ench_prot2 = Enchantment.getByName("PROTECTION_ENVIRONMENTAL");
            if (ench_prot2 != null) chest.addUnsafeEnchantment(ench_prot2, 1);
            makeUnbreakable(chest);
            ItemStack leggings = new ItemStack(Material.CHAINMAIL_LEGGINGS);
            makeUnbreakable(leggings);
            ItemStack boots = new ItemStack(Material.IRON_BOOTS);
            Enchantment ench_prot3 = Enchantment.getByName("PROTECTION_ENVIRONMENTAL");
            if (ench_prot3 != null) boots.addUnsafeEnchantment(ench_prot3, 1);
            makeUnbreakable(boots);

            List<ItemStack> refill = List.of(
                    new ItemBuilder(Material.BAKED_POTATO, 5).name("§6Pieczony ziemniak").build()
            );

            kits.put("tank", new Kit(
                    "tank", "§9Tank",
                    icon,
                    List.of(sword, icon),
                    refill,
                    helmet,
                    chest,
                    leggings,
                    boots
            ));
        }

        // Magik - dystansowe obrażenia magiczne, różdżka z laserem (2.5 dmg, cooldown)
        {
            ItemStack icon = abilityManager.createAbilityItem(AbilityType.LASER_WAND);
            makeUnbreakable(icon);
            ItemStack wand = abilityManager.createAbilityItem(AbilityType.LASER_WAND);
            makeUnbreakable(wand);
            ItemStack chest = new ItemStack(Material.LEATHER_CHESTPLATE);
            makeUnbreakable(chest);
            ItemStack boots = new ItemStack(Material.LEATHER_BOOTS);
            makeUnbreakable(boots);

            kits.put("magik", new Kit(
                    "magik", "§5Magik",
                    icon,
                    List.of(wand),
                    List.of(),
                    null,
                    chest,
                    null,
                    boots
            ));
        }

        // Medyk - nowy kit
        {
            ItemStack icon = new ItemBuilder(Material.IRON_SWORD, 1).name("§bMedyk").build();
            ItemStack scalpel = new ItemStack(Material.IRON_SWORD, 1);
            ItemMeta scalpelMeta = scalpel.getItemMeta();
            if (scalpelMeta != null) {
                scalpelMeta.setDisplayName("§cSkalpel");
                Enchantment ench_sharp = Enchantment.getByName("DAMAGE_ALL");
                if (ench_sharp != null) scalpelMeta.addEnchant(ench_sharp, 2, true);
                // Try to add scalpel attack speed via reflection for compatibility
                tryAddAttributeModifier(scalpelMeta, "GENERIC_ATTACK_SPEED", 1.0, "ADD_NUMBER", "HAND", "scalpel_speed");
                scalpel.setItemMeta(scalpelMeta);
            }
            makeUnbreakable(scalpel);

            // full leather armor with colors and decoration in lore
            ItemStack helm = new ItemStack(Material.LEATHER_HELMET);
            ItemStack chestp = new ItemStack(Material.LEATHER_CHESTPLATE);
            ItemStack legs = new ItemStack(Material.LEATHER_LEGGINGS);
            ItemStack bootsL = new ItemStack(Material.LEATHER_BOOTS);
            if (helm.getItemMeta() instanceof LeatherArmorMeta) {
                LeatherArmorMeta m = (LeatherArmorMeta) helm.getItemMeta();
                m.setColor(Color.RED);
                m.setDisplayName("§cHełm - Nurt");
                m.setLore(List.of("§7Zdobienie: Nurt (redstone)") );
                helm.setItemMeta(m);
            }
            if (chestp.getItemMeta() instanceof LeatherArmorMeta) {
                LeatherArmorMeta m = (LeatherArmorMeta) chestp.getItemMeta();
                m.setColor(Color.WHITE);
                m.setDisplayName("§fNapierśnik - Wartownik");
                m.setLore(List.of("§7Zdobienie: Wartownik (żelazny)") );
                chestp.setItemMeta(m);
            }
            if (legs.getItemMeta() instanceof LeatherArmorMeta) {
                LeatherArmorMeta m = (LeatherArmorMeta) legs.getItemMeta();
                m.setColor(Color.RED);
                m.setDisplayName("§cSpodnie - Iglica");
                m.setLore(List.of("§7Zdobienie: Iglica (redstone)") );
                legs.setItemMeta(m);
            }
            if (bootsL.getItemMeta() instanceof LeatherArmorMeta) {
                LeatherArmorMeta m = (LeatherArmorMeta) bootsL.getItemMeta();
                m.setColor(Color.WHITE);
                m.setDisplayName("§fButy - Nit");
                m.setLore(List.of("§7Zdobienie: Nit (żelazny)") );
                bootsL.setItemMeta(m);
            }
            makeUnbreakable(helm); makeUnbreakable(chestp); makeUnbreakable(legs); makeUnbreakable(bootsL);

            // refill items
            List<ItemStack> medRefill = new ArrayList<>();
            medRefill.add(new ItemBuilder(Material.GOLDEN_APPLE, 2).name("§6Złote jabłko").build());
            // create potions via getByName for compatibility
            org.bukkit.potion.PotionEffectType healType = org.bukkit.potion.PotionEffectType.getByName("HEAL");
            if (healType != null) {
                medRefill.add(createCustomSplashPotion(List.of(new PotionEffect(healType, 1, 1)), "§cMikstura leczenia II", List.of("§7Leczenie II (instant)")));
                medRefill.add(createCustomSplashPotion(List.of(new PotionEffect(healType, 1, 1)), "§cMikstura leczenia II", List.of("§7Leczenie II (instant)"))); // second
            }
            org.bukkit.potion.PotionEffectType regenType = org.bukkit.potion.PotionEffectType.getByName("REGENERATION");
            if (regenType != null) medRefill.add(createCustomSplashPotion(List.of(new PotionEffect(regenType, 90*20, 0)), "§9Mikstura regeneracji I", List.of("§7Regeneracja I przez 90s")));
            org.bukkit.potion.PotionEffectType slowType = org.bukkit.potion.PotionEffectType.getByName("SLOW");
            org.bukkit.potion.PotionEffectType resistType = org.bukkit.potion.PotionEffectType.getByName("DAMAGE_RESISTANCE");
            List<PotionEffect> turtleEffects = new ArrayList<>();
            if (slowType != null) turtleEffects.add(new PotionEffect(slowType, 40*20, 3));
            if (resistType != null) turtleEffects.add(new PotionEffect(resistType, 40*20, 2));
            if (!turtleEffects.isEmpty()) medRefill.add(createCustomSplashPotion(turtleEffects, "§eMikstura żółwiego mistrza", List.of("§7Spowolnienie IV + Odporność III przez 40s")));
            medRefill.add(new ItemBuilder(Material.SWEET_BERRIES, 8).name("§6Słodkie jagody").build());

            kits.put("medyk", new Kit(
                    "medyk", "§bMedyk",
                    icon,
                    List.of(scalpel),
                    medRefill,
                    helm,
                    chestp,
                    legs,
                    bootsL
            ));
        }
    }

    private ItemStack createSplashPotion(PotionType type, String name, List<String> lore) {
        ItemStack potion = new ItemStack(Material.SPLASH_POTION, 1);
        PotionMeta meta = (PotionMeta) potion.getItemMeta();
        if (meta != null) {
            meta.setBasePotionData(new PotionData(type, false, false));
            meta.setDisplayName(name);
            meta.setLore(lore);
            potion.setItemMeta(meta);
        }
        return potion;
    }

    private ItemStack createCustomSplashPotion(List<PotionEffect> effects, String name, List<String> lore) {
        ItemStack potion = new ItemStack(Material.SPLASH_POTION, 1);
        PotionMeta meta = (PotionMeta) potion.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            for (PotionEffect eff : effects) {
                meta.addCustomEffect(eff, true);
            }
            potion.setItemMeta(meta);
        }
        return potion;
    }

    private void makeUnbreakable(ItemStack item) {
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setUnbreakable(true);
            item.setItemMeta(meta);
        }
    }

    // Reflection based helper to add attribute modifiers when available across API versions
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void tryAddAttributeModifier(ItemMeta meta, String attributeName, double amount, String operationName, String equipmentSlotName, String modifierIdSuffix) {
        if (meta == null) return;
        try {
            Class<?> attrClass = Class.forName("org.bukkit.attribute.Attribute");
            Class<?> attrModClass = Class.forName("org.bukkit.attribute.AttributeModifier");
            Class<?> operationClass = Class.forName("org.bukkit.attribute.AttributeModifier$Operation");
            Class<?> equipSlotClass = Class.forName("org.bukkit.inventory.EquipmentSlot");

            // enum constants
            Object attrEnum = java.lang.Enum.valueOf((Class) attrClass, attributeName);
            Object opEnum = java.lang.Enum.valueOf((Class) operationClass, operationName);
            Object slotEnum = java.lang.Enum.valueOf((Class) equipSlotClass, equipmentSlotName);

            // find constructor: try (UUID, String, double, Operation, EquipmentSlot) then (UUID, String, double, Operation)
            java.lang.reflect.Constructor<?> chosenCtor = null;
            for (java.lang.reflect.Constructor<?> c : attrModClass.getConstructors()) {
                Class<?>[] params = c.getParameterTypes();
                if (params.length == 5 && params[0] == java.util.UUID.class && params[1] == String.class && params[2] == double.class) {
                    chosenCtor = c; break;
                }
                if (params.length == 4 && params[0] == java.util.UUID.class && params[1] == String.class && params[2] == double.class) {
                    chosenCtor = c; break;
                }
            }
            if (chosenCtor == null) return;

            Object modifier;
            if (chosenCtor.getParameterTypes().length == 5) {
                modifier = chosenCtor.newInstance(java.util.UUID.randomUUID(), "vd_" + modifierIdSuffix, amount, opEnum, slotEnum);
            } else {
                modifier = chosenCtor.newInstance(java.util.UUID.randomUUID(), "vd_" + modifierIdSuffix, amount, opEnum);
            }

            java.lang.reflect.Method addMethod = meta.getClass().getMethod("addAttributeModifier", attrClass, attrModClass);
            addMethod.invoke(meta, attrEnum, modifier);
        } catch (Throwable ignored) {
            // ignore if API not present or signature differs
        }
    }

    public void refillKitItems(Player player, String kitId) {
        Kit kit = kits.getOrDefault(kitId, kits.get("wojownik"));
        PlayerInventory inv = player.getInventory();
        for (ItemStack template : kit.getRefillItems()) {
            int currentAmount = countMatchingItems(inv, template);
            int missing = template.getAmount() - currentAmount;
            if (missing <= 0) continue;

            ItemStack add = template.clone();
            add.setAmount(missing);
            inv.addItem(add);
        }
    }

    private int countMatchingItems(PlayerInventory inventory, ItemStack template) {
        int count = 0;
        for (ItemStack item : inventory.getContents()) {
            if (item == null || item.getType() == Material.AIR) continue;
            if (itemsMatch(item, template)) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private boolean itemsMatch(ItemStack item, ItemStack template) {
        if (item.getType() != template.getType()) return false;
        ItemMeta itemMeta = item.getItemMeta();
        ItemMeta templateMeta = template.getItemMeta();

        if (templateMeta != null) {
            if (templateMeta.hasDisplayName()) {
                if (itemMeta == null || !templateMeta.getDisplayName().equals(itemMeta.getDisplayName())) {
                    return false;
                }
            }
            if (templateMeta.hasLore()) {
                if (itemMeta == null || !templateMeta.getLore().equals(itemMeta.getLore())) {
                    return false;
                }
            }
        }
        return true;
    }

    public Collection<Kit> getKits() { return kits.values(); }

    private Material findMaterial(String... names) {
        for (String n : names) {
            try {
                Material m = Material.matchMaterial(n);
                if (m != null) return m;
            } catch (Throwable ignored) {}
        }
        return null;
    }
    public Kit getKit(String id) { return kits.get(id); }

    public void setChosenKit(Player player, String kitId) {
        chosenKit.put(player.getUniqueId(), kitId);
    }

    public String getChosenKitId(Player player) {
        return chosenKit.getOrDefault(player.getUniqueId(), "wojownik");
    }

    public void clearChoice(Player player) {
        chosenKit.remove(player.getUniqueId());
    }

    public void openKitSelector(Player player) {
        // Fixed 9-slot layout: 0: medyk, 1: empty, 2: wojownik, 3: empty, 4: łucznik, 5: empty, 6: tank, 7: empty, 8: magik
        Inventory inv = Bukkit.createInventory(null, 9, KIT_GUI_TITLE);

        // helper to get kit icon or placeholder
        ItemStack medIcon = getIconForKitOrPlaceholder("medyk");
        ItemStack wojIcon = getIconForKitOrPlaceholder("wojownik");
        ItemStack luczIcon = getIconForKitOrPlaceholder("lucznik");
        ItemStack tankIcon = getIconForKitOrPlaceholder("tank");
        ItemStack magIcon = getIconForKitOrPlaceholder("magik");

        inv.setItem(0, medIcon);
        inv.setItem(1, null);
        inv.setItem(2, wojIcon);
        inv.setItem(3, null);
        inv.setItem(4, luczIcon);
        inv.setItem(5, null);
        inv.setItem(6, tankIcon);
        inv.setItem(7, null);
        inv.setItem(8, magIcon);

        player.openInventory(inv);
    }

    private ItemStack getIconForKitOrPlaceholder(String kitId) {
        Kit k = kits.get(kitId);
        if (k == null) return new ItemBuilder(Material.BARRIER, 1).name("§cBrak kita").build();
        ItemStack icon = k.getIcon();
        if (icon == null || icon.getType() == Material.AIR) return new ItemBuilder(Material.BARRIER, 1).name("§cBrak ikony").build();
        return icon.clone();
    }

    public void giveKit(Player player, String kitId) {
        Kit kit = kits.getOrDefault(kitId, kits.get("wojownik"));
        PlayerInventory inv = player.getInventory();
        inv.clear();
        // debug log which kit and items are given
        try {
            java.util.logging.Logger logger = Bukkit.getLogger();
            logger.info("[VD] giveKit: giving kit '" + kitId + "' to player " + player.getName());
            int idx = 0;
            for (ItemStack it : kit.getItems()) {
                if (it == null) {
                    logger.info("[VD] giveKit: item[" + idx + "] is null");
                } else {
                    logger.info("[VD] giveKit: item[" + idx + "] type=" + it.getType() + " amt=" + it.getAmount() + " name=" + (it.hasItemMeta() && it.getItemMeta().hasDisplayName() ? it.getItemMeta().getDisplayName() : "<noname>"));
                }
                idx++;
            }
        } catch (Throwable ignored) {}

        // give main items (weapons/abilities)
        List<ItemStack> items = kit.getItems();
        if (items != null && !items.isEmpty()) {
            // place primary weapon in slot 0
            ItemStack primary = items.get(0);
            if (primary != null) inv.setItem(0, primary.clone());
            // add other items into inventory (from slot 1 up)
            for (int i = 1; i < items.size(); i++) {
                ItemStack it = items.get(i);
                if (it != null) inv.addItem(it.clone());
            }
        }

        // set armor
        if (kit.getHelmet() != null) inv.setHelmet(kit.getHelmet().clone());
        if (kit.getChestplate() != null) inv.setChestplate(kit.getChestplate().clone());
        if (kit.getLeggings() != null) inv.setLeggings(kit.getLeggings().clone());
        if (kit.getBoots() != null) inv.setBoots(kit.getBoots().clone());

        // safety: if this is warrior kit and no sword was given (compatibility issues), give a default stone sword
        try {
            if ("wojownik".equalsIgnoreCase(kitId)) {
                boolean hasSword = false;
                for (ItemStack it : inv.getContents()) {
                    if (it == null) continue;
                    String name = it.getType().name();
                    if (name.endsWith("_SWORD")) { hasSword = true; break; }
                }
                if (!hasSword) {
                    ItemStack fallbackSword = new ItemStack(Material.STONE_SWORD, 1);
                    ItemMeta fm = fallbackSword.getItemMeta();
                    if (fm != null) fm.setDisplayName("§fMiecz Wojownika");
                    fallbackSword.setItemMeta(fm);
                    makeUnbreakable(fallbackSword);
                    inv.setItem(0, fallbackSword);
                    Bukkit.getLogger().info("[VD] giveKit: warrior had no sword, gave fallback STONE_SWORD to " + player.getName());
                }
            }
        } catch (Throwable ignored) {}

        // give refill items (food/potions) immediately
        refillKitItems(player, kitId);

        // arrange potions left-to-right in hotbar (slots 1..7), and put food in slot 8
        try {
            int hotbarIndex = 1; // start after primary weapon
            ItemStack foodCandidate = null;
            // collect current inventory items
            for (int slot = 0; slot < inv.getSize(); slot++) {
                ItemStack it = inv.getItem(slot);
                if (it == null) continue;
                if (it.getType() == Material.SPLASH_POTION) {
                    // move to hotbar at hotbarIndex
                    inv.setItem(slot, null);
                    if (hotbarIndex >= 8) hotbarIndex = 7;
                    inv.setItem(hotbarIndex, it);
                    hotbarIndex = Math.min(7, hotbarIndex + 1);
                } else if (isFood(it.getType())) {
                    // choose as foodCandidate (place at slot 8 later)
                    if (foodCandidate == null) {
                        foodCandidate = it.clone();
                        inv.remove(it);
                    }
                }
            }
            if (foodCandidate != null) {
                inv.setItem(8, foodCandidate);
            }
        } catch (Throwable ignored) {}

        // Kompas do otwierania sklepu w trakcie gry (umieszczony obok jedzenia)
        inv.setItem(7, new ItemBuilder(Material.COMPASS, 1).name("§6Sklep (kliknij PPM)").build());

        // special: tank gets shield in off-hand if the kit provides a shield item
        try {
            if (kit.getId().equals("tank")) {
                // try to find a shield-like item in kit items or icon
                ItemStack shield = null;
                for (ItemStack it : kit.getItems()) {
                    if (it == null) continue;
                    String tname = it.getType().name();
                    if (tname.contains("SHIELD") || tname.contains("SHIELD")) { shield = it; break; }
                }
                if (shield == null && kit.getIcon() != null) shield = kit.getIcon();
                if (shield != null) inv.setItemInOffHand(shield.clone());
            }
        } catch (Throwable ignored) {}
    }

    private boolean isFood(Material m) {
        return switch (m) {
            case COOKED_CHICKEN, COOKED_MUTTON, BAKED_POTATO, COOKED_BEEF, COOKED_PORKCHOP, SWEET_BERRIES, GLOW_BERRIES -> true;
            case GOLDEN_APPLE, APPLE -> true;
            default -> false;
        };
    }
}
