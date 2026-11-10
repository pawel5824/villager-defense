package pl.vd.kits;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import pl.vd.VillagerDefensePlugin;
import pl.vd.ability.AbilityManager;
import pl.vd.ability.AbilityType;
import pl.vd.shield.ShieldManager;
import pl.vd.shop.ShopGUI;
import pl.vd.util.ItemBuilder;
import pl.vd.util.Lang;

import java.util.*;

public class KitManager {

    public static final String KIT_GUI_TITLE = "Wybierz kit";
    public static final String KIT_PREVIEW_TITLE = "Podgląd kitów";
    public static final String KIT_DETAIL_TITLE_PREFIX = "Podgląd kitu: ";

    /** Czy tytuł GUI to wybór kitu (po polsku lub w języku gracza)? */
    public static boolean isKitSelectorTitle(Player player, String title) {
        return KIT_GUI_TITLE.equals(title) || Lang.get(player, "kit-gui-title").equals(title);
    }

    /** Czy tytuł GUI to podgląd kitów (po polsku lub w języku gracza)? */
    public static boolean isKitPreviewTitle(Player player, String title) {
        return KIT_PREVIEW_TITLE.equals(title) || Lang.get(player, "kit-preview-title").equals(title);
    }

    /** Czy tytuł GUI to podgląd kitu (prefiks po polsku lub w języku gracza)? */
    public static boolean isKitDetailTitle(Player player, String title) {
        return title.startsWith(KIT_DETAIL_TITLE_PREFIX)
                || title.startsWith(Lang.get(player, "kit-detail-title-prefix"));
    }

    /** Klucz jawnego wyboru kitu w players.yml (settings.chosen-kit). */
    private static final String CHOSEN_KIT_SETTING = "chosen-kit";

    private final Map<String, Kit> kits = new LinkedHashMap<>();
    private final Map<UUID, String> chosenKit = new HashMap<>();

    // gracze, którzy JAWNIE wybrali kit w GUI (losowo nadany kit NIE jest tu zapisywany)
    private final Set<UUID> explicitChoice = new HashSet<>();
    private final VillagerDefensePlugin plugin;
    private final AbilityManager abilityManager;
    private final ShieldManager shieldManager;

    public KitManager(VillagerDefensePlugin plugin, AbilityManager abilityManager, ShieldManager shieldManager) {
        this.plugin = plugin;
        this.abilityManager = abilityManager;
        this.shieldManager = shieldManager;
        registerDefaults();
    }

    private void registerDefaults() {
        // Wojownik
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
                    createSplashPotion(PotionType.STRENGTH, "§cMikstura siły I", null) // efekt widać z danych mikstury
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

        // Łucznik
        {
            ItemStack icon = new ItemBuilder(Material.BOW, 1).name("§aŁucznik").build();
            ItemStack bow = new ItemStack(Material.BOW, 1);
            ItemMeta bowMeta = bow.getItemMeta();
            if (bowMeta != null) {
                bowMeta.setDisplayName("§fŁuk");
                Enchantment ench_power = Enchantment.getByName("ARROW_DAMAGE");
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
                tryAddAttributeModifier(daggerMeta, "GENERIC_ATTACK_SPEED", 2.4, "ADD_NUMBER", "HAND", "dagger_speed");
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

        // Tank
        {
            ItemStack icon = shieldManager.createKnockbackShield();
            makeUnbreakable(icon);
            ItemStack sword = new ItemBuilder(Material.STONE_SWORD, 1).name("§fMiecz").build(); // bez zaklęć
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

            // tylko miecz w ekwipunku - tarcza trafia wyłącznie do drugiej ręki (offhand)
            kits.put("tank", new Kit(
                    "tank", "§9Tank",
                    icon,
                    List.of(sword),
                    refill,
                    helmet,
                    chest,
                    leggings,
                    boots
            ));
        }

        // Magik
        {
            ItemStack icon = abilityManager.createAbilityItem(AbilityType.LASER_WAND);
            makeUnbreakable(icon);
            ItemStack wand = abilityManager.createAbilityItem(AbilityType.LASER_WAND);
            makeUnbreakable(wand);
            ItemStack chest = new ItemStack(Material.LEATHER_CHESTPLATE);
            makeUnbreakable(chest);
            ItemStack boots = new ItemStack(Material.LEATHER_BOOTS);
            makeUnbreakable(boots);

            List<ItemStack> refill = List.of(
                    new ItemBuilder(Material.APPLE, 5).name("§6Jabłko").build()
            );

            kits.put("magik", new Kit(
                    "magik", "§5Magik",
                    icon,
                    List.of(wand),
                    refill,
                    null,
                    chest,
                    null,
                    boots
            ));
        }

        // Medyk
        {
            ItemStack icon = new ItemBuilder(Material.IRON_SWORD, 1).name("§bMedyk").build();
            ItemStack scalpel = new ItemStack(Material.IRON_SWORD, 1);
            ItemMeta scalpelMeta = scalpel.getItemMeta();
            if (scalpelMeta != null) {
                scalpelMeta.setDisplayName("§cSkalpel");
                Enchantment ench_sharp = Enchantment.getByName("DAMAGE_ALL");
                if (ench_sharp != null) scalpelMeta.addEnchant(ench_sharp, 2, true);
                // Try to add scalpel attack speed via reflection for compatibility
                tryAddAttributeModifier(scalpelMeta, "GENERIC_ATTACK_SPEED", 0.9, "ADD_NUMBER", "HAND", "scalpel_speed");
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
                m.setDisplayName("§cHełm");
                helm.setItemMeta(m);
            }
            if (chestp.getItemMeta() instanceof LeatherArmorMeta) {
                LeatherArmorMeta m = (LeatherArmorMeta) chestp.getItemMeta();
                m.setColor(Color.WHITE);
                m.setDisplayName("§fNapierśnik");
                chestp.setItemMeta(m);
            }
            if (legs.getItemMeta() instanceof LeatherArmorMeta) {
                LeatherArmorMeta m = (LeatherArmorMeta) legs.getItemMeta();
                m.setColor(Color.RED);
                m.setDisplayName("§cSpodnie");
                legs.setItemMeta(m);
            }
            if (bootsL.getItemMeta() instanceof LeatherArmorMeta) {
                LeatherArmorMeta m = (LeatherArmorMeta) bootsL.getItemMeta();
                m.setColor(Color.WHITE);
                m.setDisplayName("§fButy");
                bootsL.setItemMeta(m);
            }
            makeUnbreakable(helm); makeUnbreakable(chestp); makeUnbreakable(legs); makeUnbreakable(bootsL);

            // refill items - słodkie jagody zaraz za bronią (w podglądzie obok
            // skalpela), potem złote jabłko i mikstury
            List<ItemStack> medRefill = new ArrayList<>();
            medRefill.add(new ItemBuilder(Material.SWEET_BERRIES, 8).name("§6Słodkie jagody").build());
            medRefill.add(new ItemBuilder(Material.GOLDEN_APPLE, 1).name("§6Złote jabłko").build());
            // create potions via getByName for compatibility
            org.bukkit.potion.PotionEffectType healType = org.bukkit.potion.PotionEffectType.getByName("HEAL");
            if (healType != null) {
                medRefill.add(createCustomSplashPotion(List.of(new PotionEffect(healType, 1, 1)), "§cMikstura leczenia II", null));
                medRefill.add(createCustomSplashPotion(List.of(new PotionEffect(healType, 1, 1)), "§cMikstura leczenia II", null)); // second
            }
            org.bukkit.potion.PotionEffectType regenType = org.bukkit.potion.PotionEffectType.getByName("REGENERATION");
            if (regenType != null) medRefill.add(createCustomSplashPotion(List.of(new PotionEffect(regenType, 90*20, 0)), "§9Mikstura regeneracji I", null));

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

        // Assassin (Asasyn)
        {
            ItemStack shuriken = createShuriken(SHURIKEN_MAX);
            ItemStack icon = createShuriken(1); // ikona kitu = shuriken

            ItemStack helmet = new ItemStack(Material.LEATHER_HELMET);
            makeUnbreakable(helmet);
            ItemStack chest = new ItemStack(Material.LEATHER_CHESTPLATE);
            makeUnbreakable(chest);
            ItemStack leggings = new ItemStack(Material.CHAINMAIL_LEGGINGS);
            makeUnbreakable(leggings);
            ItemStack boots = new ItemStack(Material.CHAINMAIL_BOOTS);
            makeUnbreakable(boots);

            List<ItemStack> refill = List.of(
                    new ItemBuilder(Material.COOKED_RABBIT, 5).name("§6Pieczony królik").build()
            );

            kits.put("assassin", new Kit(
                    "assassin", "§8Asasyn",
                    icon,
                    List.of(shuriken),
                    refill,
                    helmet,
                    chest,
                    leggings,
                    boots
            ));
        }

        // Inżynier
        {
            ItemStack icon = new ItemBuilder(Material.OAK_TRAPDOOR, 1).name("§6Inżynier").build();
            ItemStack sword = new ItemBuilder(Material.WOODEN_SWORD, 1).name("§fDrewniany miecz Inżyniera").build();
            Enchantment ench_knock = Enchantment.getByName("KNOCKBACK");
            if (ench_knock != null) sword.addUnsafeEnchantment(ench_knock, 1);
            makeUnbreakable(sword);
            makeUnbreakable(icon);

            ItemStack helmet = new ItemStack(Material.IRON_HELMET);
            Enchantment ench_prot = Enchantment.getByName("PROTECTION_ENVIRONMENTAL");
            if (ench_prot != null) helmet.addUnsafeEnchantment(ench_prot, 1);
            makeUnbreakable(helmet);
            ItemStack chest = new ItemStack(Material.LEATHER_CHESTPLATE);
            makeUnbreakable(chest);
            ItemStack leggings = new ItemStack(Material.LEATHER_LEGGINGS);
            makeUnbreakable(leggings);
            ItemStack boots = new ItemStack(Material.LEATHER_BOOTS);
            makeUnbreakable(boots);

            List<ItemStack> refill = List.of(
                    new ItemBuilder(Material.COOKED_SALMON, 5).name("§6Pieczony łosoś").build()
            );

            kits.put("inzynier", new Kit(
                    "inzynier", "§6Inżynier",
                    icon,
                    List.of(sword),
                    refill,
                    helmet,
                    chest,
                    leggings,
                    boots
            ));
        }
    }

    // ---------- Assassin: shuriken (śnieżka) ----------

    public static final String SHURIKEN_NAME = "§fShuriken";
    public static final int SHURIKEN_MAX = 5;

    /** Tworzy shuriken (śnieżka) z nazwą. */
    public ItemStack createShuriken(int amount) {
        return new ItemBuilder(Material.SNOWBALL, amount)
                .name(SHURIKEN_NAME)
                .build();
    }

    public boolean isAssassin(Player player) {
        return "assassin".equalsIgnoreCase(getChosenKitId(player));
    }

    /** Liczy shurikeny gracza: sloty ekwipunku + przedmiot na kursorze myszy (po nazwie). */
    public int countShurikens(Player player) {
        ItemStack template = createShuriken(1);
        int count = countMatchingItems(player.getInventory(), template);
        ItemStack cursor = player.getItemOnCursor();
        if (cursor != null && cursor.getType() != Material.AIR && itemsMatch(cursor, template)) {
            count += cursor.getAmount();
        }
        return count;
    }

    /**
     * Dodaje 1 shuriken do ekwipunku, ale tylko gdy suma (sloty + kursor) nie przekracza
     * maksimum. Po dodaniu wymusza twardy limit — nadmiar zdejmuje najpierw z kursora,
     * potem z ostatnich slotów, żeby exploit „shurikeny na myszce” nie dawał więcej niż 5.
     */
    public void addShuriken(Player player) {
        ItemStack template = createShuriken(1);
        if (countShurikens(player) >= SHURIKEN_MAX) return;
        player.getInventory().addItem(template);
        enforceShurikenCap(player, template);
    }

    private void enforceShurikenCap(Player player, ItemStack template) {
        int excess = countShurikens(player) - SHURIKEN_MAX;
        if (excess <= 0) return;

        // 1) najpierw z kursora myszy (tam ukrywał się exploit)
        ItemStack cursor = player.getItemOnCursor();
        if (cursor != null && cursor.getType() != Material.AIR && itemsMatch(cursor, template)) {
            int take = Math.min(excess, cursor.getAmount());
            cursor.setAmount(cursor.getAmount() - take);
            player.setItemOnCursor(cursor.getAmount() <= 0 ? new ItemStack(Material.AIR) : cursor);
            excess -= take;
        }

        // 2) potem z ekwipunku — od tyłu (ostatnie sloty, offhand)
        if (excess <= 0) return;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = contents.length - 1; i >= 0 && excess > 0; i--) {
            ItemStack item = contents[i];
            if (item == null || item.getType() == Material.AIR || !itemsMatch(item, template)) continue;
            int take = Math.min(excess, item.getAmount());
            item.setAmount(item.getAmount() - take);
            if (item.getAmount() <= 0) player.getInventory().setItem(i, null);
            excess -= take;
        }
    }

    private ItemStack createSplashPotion(PotionType type, String name, List<String> lore) {
        ItemStack potion = new ItemStack(Material.SPLASH_POTION, 1);
        PotionMeta meta = (PotionMeta) potion.getItemMeta();
        if (meta != null) {
            meta.setBasePotionData(new PotionData(type, false, false));
            meta.setDisplayName(name);
            if (lore != null && !lore.isEmpty()) meta.setLore(lore);
            potion.setItemMeta(meta);
        }
        return potion;
    }

    private ItemStack createCustomSplashPotion(List<PotionEffect> effects, String name, List<String> lore) {
        ItemStack potion = new ItemStack(Material.SPLASH_POTION, 1);
        PotionMeta meta = (PotionMeta) potion.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && !lore.isEmpty()) meta.setLore(lore);
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
            if (isFood(add.getType())) {
                // jedzenie zawsze ląduje na 8. slocie paska (indeks 7)
                ItemStack current = inv.getItem(7);
                if (current == null || current.getType() == Material.AIR) {
                    inv.setItem(7, add);
                } else if (current.isSimilar(add)) {
                    if (current.getAmount() + add.getAmount() <= current.getMaxStackSize()) {
                        current.setAmount(current.getAmount() + add.getAmount());
                        inv.setItem(7, current);
                    } else {
                        inv.addItem(add);
                    }
                } else {
                    inv.addItem(add);
                }
            } else {
                inv.addItem(add);
            }
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
        explicitChoice.add(player.getUniqueId());
        // Wybór kitu przeżywa restart serwera (players.yml → settings.chosen-kit) —
        // mapa chosenKit żyje tylko w pamięci, więc po restarcie gracz dostawał
        // losowy kit, mimo że jawnie coś wybrał.
        pl.vd.player.PlayerData data = plugin.getPlayerDataManager()
                .get(player.getUniqueId(), player.getName());
        data.setSetting(CHOSEN_KIT_SETTING, kitId);
        plugin.getPlayerDataManager().saveAll();
    }

    /**
     * Przywraca jawnie wybrany kit z players.yml (wołane przy wejściu gracza).
     * Bez tego po restarcie serwera wybór gracza był zapominany, a kolejna gra
     * przydzielała losowy kit (losowy wybór NIE jest zapisywany — patrz
     * {@link #clearRandomChoice}).
     */
    public void restoreChoice(Player player) {
        pl.vd.player.PlayerData data = plugin.getPlayerDataManager()
                .get(player.getUniqueId(), player.getName());
        String saved = data.getSetting(CHOSEN_KIT_SETTING, null);
        if (saved == null || !kits.containsKey(saved)) return;
        chosenKit.put(player.getUniqueId(), saved);
        explicitChoice.add(player.getUniqueId());
    }

    public String getChosenKitId(Player player) {
        return chosenKit.getOrDefault(player.getUniqueId(), "wojownik");
    }

    public void clearChoice(Player player) {
        chosenKit.remove(player.getUniqueId());
        explicitChoice.remove(player.getUniqueId());
        pl.vd.player.PlayerData data = plugin.getPlayerDataManager()
                .get(player.getUniqueId(), player.getName());
        data.getSettings().remove(CHOSEN_KIT_SETTING);
        plugin.getPlayerDataManager().saveAll();
    }

    /**
     * Zwraca ID kitu gracza: wybrany w GUI, a jeśli gracz niczego nie wybrał —
     * losowy kit (zapamiętywany na czas trwania gry, żeby mechaniki typu shuriken
     * czy dash działały stabilnie w trakcie rundy).
     */
    public String resolveKitId(Player player) {
        String chosen = chosenKit.get(player.getUniqueId());
        if (chosen != null) return chosen;

        List<String> ids = new ArrayList<>(kits.keySet());
        String random = ids.get(new Random().nextInt(ids.size()));
        chosenKit.put(player.getUniqueId(), random);
        Kit kit = kits.get(random);
        Lang.send(player, "kit-random", kit != null ? kit.localizedName(player) : random);
        return random;
    }

    /**
     * Po zakończeniu gry zapomina losowo nadany kit (jeśli gracz nie wybrał go jawnie),
     * żeby przy kolejnej grze losowanie odbyło się od nowa.
     */
    public void clearRandomChoice(Player player) {
        if (explicitChoice.contains(player.getUniqueId())) return;
        chosenKit.remove(player.getUniqueId());
    }

    public void openKitSelector(Player player) {
        player.openInventory(createKitSelectorInventory(player, Lang.get(player, "kit-gui-title")));
    }

    /**
     * Otwiera GUI wyboru kitów w trybie PODGLĄDU (dla operatora): gracz widzi kity,
     * ale kliknięcia nie zmieniają wybranego kitu (obsługiwane w PlayerListener).
     */
    public void openKitSelectorPreview(Player player) {
        player.openInventory(createKitSelectorInventory(player, Lang.get(player, "kit-preview-title")));
    }

    /**
     * Otwiera 27-slotowe (3x9) GUI z podglądem przedmiotów danego kitu:
     * rząd 1 - broń/przedmioty, rząd 2 - jedzenie i mikstury, rząd 3 - zbroja.
     * GUI jest wyłącznie do oglądania (kliknięcia blokowane w PlayerListener).
     */
    public void openKitDetail(Player player, String kitId) {
        Kit kit = kits.get(kitId);
        if (kit == null) return;
        player.openInventory(createKitDetailInventory(player, kit));
    }

    private Inventory createKitDetailInventory(Player player, Kit kit) {
        Inventory inv = Bukkit.createInventory(null, 27,
                Lang.get(player, "kit-detail-title-prefix") + kit.localizedName(player));

        // Rząd 1 (sloty 0-8): szara szyba
        ItemStack glass = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE, 1).name(" ").build();
        for (int i = 0; i < 9; i++) inv.setItem(i, glass);

        // Zbroja (sloty 9-12): hełm -> napierśnik -> spodnie -> buty;
        // brakującej części nie ma - slot zostaje pusty.
        ItemStack[] armor = { kit.getHelmet(), kit.getChestplate(), kit.getLeggings(), kit.getBoots() };
        for (int i = 0; i < armor.length; i++) {
            if (armor[i] != null) inv.setItem(9 + i, armor[i].clone());
        }

        // Broń i przedmioty klasy: podstawowa broń w slocie 13, druga w 14 itd.,
        // a po nich mikstury i jedzenie (refill) - do końca rzędu 2 (slot 17).
        List<ItemStack> display = new ArrayList<>();
        if (kit.getItems() != null) display.addAll(kit.getItems());
        if ("tank".equals(kit.getId())) {
            ItemStack shield = shieldManager.createKnockbackShield();
            if (shield != null) display.add(shield); // tarcza (druga ręka) po broni
        }
        if (kit.getRefillItems() != null) display.addAll(kit.getRefillItems());

        // identyczne przedmioty (np. 2x mikstura leczenia II u Medyka) pokazujemy
        // w podglądzie jako jeden egzemplarz z podtytułem „2x" - tylko wyświetlanie,
        // ekwipunek gracza dostaje tyle, ile jest w definicji kitu.
        List<ItemStack> merged = new ArrayList<>();
        for (ItemStack it : display) {
            if (it == null) continue;
            String key = it.getItemMeta() != null ? it.getItemMeta().getDisplayName() : it.getType().name();
            ItemStack shown = null;
            for (ItemStack s : merged) {
                String k2 = s.getItemMeta() != null ? s.getItemMeta().getDisplayName() : s.getType().name();
                if (k2.equals(key)) { shown = s; break; }
            }
            if (shown != null) {
                ItemMeta m = shown.getItemMeta();
                if (m != null) {
                    List<String> lore = m.getLore() == null ? new ArrayList<>() : new ArrayList<>(m.getLore());
                    if (!lore.contains("§72x")) lore.add("§72x");
                    m.setLore(lore);
                    shown.setItemMeta(m);
                }
            } else {
                merged.add(it.clone());
            }
        }

        int slot = 13;
        for (ItemStack it : merged) {
            if (slot > 17) break;
            inv.setItem(slot, it);
            slot++;
        }

        // Rząd 3 (sloty 18-26): 3x szyba | poprzednia | powrót (nether star) | następna | 3x szyba
        for (int i : new int[]{18, 19, 20, 24, 25, 26}) inv.setItem(i, glass);
        inv.setItem(21, new ItemBuilder(Material.ARROW, 1).name(Lang.get(player, "kit-prev")).build());
        inv.setItem(22, new ItemBuilder(Material.NETHER_STAR, 1).name(Lang.get(player, "kit-back")).build());
        inv.setItem(23, new ItemBuilder(Material.ARROW, 1).name(Lang.get(player, "kit-next")).build());

        return inv;
    }

    // Kolejność klas przy nawigacji strzałkami (zgodna z układem GUI wyboru kitów)
    private static final List<String> KIT_NAV_ORDER = List.of("wojownik", "tank", "lucznik", "inzynier", "assassin", "magik", "medyk");

    /** Zwraca ID poprzedniej klasy w kolejności nawigacji (z zawinięciem). */
    public String getPrevKitId(String kitId) {
        int idx = KIT_NAV_ORDER.indexOf(kitId);
        if (idx < 0) return null;
        return KIT_NAV_ORDER.get((idx - 1 + KIT_NAV_ORDER.size()) % KIT_NAV_ORDER.size());
    }

    /** Zwraca ID następnej klasy w kolejności nawigacji (z zawinięciem). */
    public String getNextKitId(String kitId) {
        int idx = KIT_NAV_ORDER.indexOf(kitId);
        if (idx < 0) return null;
        return KIT_NAV_ORDER.get((idx + 1) % KIT_NAV_ORDER.size());
    }

    /** Znajduje ID kitu po tytule GUI podglądu (w języku gracza), albo null. */
    public String findKitByDetailTitle(Player player, String title) {
        for (Kit k : kits.values()) {
            if (title.equals(Lang.get(player, "kit-detail-title-prefix") + k.localizedName(player))
                    || title.equals(KIT_DETAIL_TITLE_PREFIX + k.getDisplayName())) {
                return k.getId();
            }
        }
        return null;
    }

    private Inventory createKitSelectorInventory(Player player, String title) {
        // 9-slot (1 rząd): ikony klas na slotach 1-7. LPM = wybór kitu,
        // PPM = podgląd przedmiotów (rozróżniane w PlayerListener).
        // W trybie PODGLĄDU (tytuł „Podgląd kitów”) wybór jest zablokowany -
        // ikony dostają lore „tylko podgląd” zamiast podpowiedzi o wyborze.
        boolean preview = isKitPreviewTitle(player, title);
        Inventory inv = Bukkit.createInventory(null, 9, title);
        for (int i = 0; i < KIT_NAV_ORDER.size(); i++) {
            inv.setItem(i + 1, getKitSelectorIcon(player, KIT_NAV_ORDER.get(i), preview));
        }
        return inv;
    }

    /**
     * Ikona kitu w GUI wyboru - czysta: tylko nazwa klasy, bez lore (opisów,
     * obrażeń, odnowienia) i bez dodatkowych linii typu "Niezniszczalne".
     * W trybie podglądu (preview=true) zamiast podpowiedzi o wyborze pokazuje
     * informację, że wybór jest zablokowany.
     */
    /** Ikona kitu dla gracza (nazwa klasy w jego języku). */
    public ItemStack getKitSelectorIcon(Player player, String kitId) {
        return getKitSelectorIcon(player, kitId, false);
    }

    public ItemStack getKitSelectorIcon(Player player, String kitId, boolean preview) {
        Kit k = kits.get(kitId);
        if (k == null) return new ItemBuilder(Material.BARRIER, 1).name(Lang.get(player, "kit-missing")).build();
        ItemStack icon = k.getIcon();
        if (icon == null || icon.getType() == Material.AIR) return new ItemBuilder(Material.BARRIER, 1).name(Lang.get(player, "kit-no-icon")).build();
        ItemStack clean = icon.clone();
        ItemMeta meta = clean.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(k.localizedName(player)); // tylko nazwa klasy (w języku gracza)
            // podpowiedź w GUI: normalnie LPM = wybór kitu, PPM = podgląd;
            // w czystym podglądzie wybór jest zablokowany i to widać na ikonie
            meta.setLore(preview
                    ? List.of("", Lang.get(player, "kit-preview-only"))
                    : List.of(
                            "",
                            Lang.get(player, "kit-lpm-hint"),
                            Lang.get(player, "kit-ppm-hint")
                    ));
            // chowamy wszystkie dodatkowe linie (też „Niezniszczalne”) -
            // ikona pokazuje wyłącznie nazwę klasy, jak u Medyka
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES,
                    ItemFlag.HIDE_ENCHANTS,
                    ItemFlag.HIDE_UNBREAKABLE,
                    ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            clean.setItemMeta(meta);
        }
        return clean;
    }

    public void giveKit(Player player, String kitId) {
        Kit kit = kits.getOrDefault(kitId, kits.get("wojownik"));
        PlayerInventory inv = player.getInventory();
        inv.clear();

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
                }
            }
        } catch (Throwable ignored) {}

        // give refill items (food/potions) immediately
        refillKitItems(player, kitId);

        // Ustaw przedmioty na pasku: broń na 1. slocie (indeks 0), mikstury na slotach 2-7,
        // jedzenie na 8. slocie (indeks 7), kompas (sklep) na 9. slocie (indeks 8)
        try {
            int hotbarIndex = 1; // start after primary weapon
            ItemStack foodCandidate = null;
            // collect current inventory items
            for (int slot = 0; slot < inv.getSize(); slot++) {
                ItemStack it = inv.getItem(slot);
                if (it == null) continue;
                if (it.getType() == Material.SPLASH_POTION) {
                    // move to hotbar at hotbarIndex (slots 2-7)
                    inv.setItem(slot, null);
                    if (hotbarIndex >= 7) hotbarIndex = 6;
                    inv.setItem(hotbarIndex, it);
                    hotbarIndex = Math.min(6, hotbarIndex + 1);
                } else if (isFood(it.getType())) {
                    // choose as foodCandidate (place at slot 8 later)
                    if (foodCandidate == null) {
                        foodCandidate = it.clone();
                        inv.remove(it);
                    }
                }
            }
            if (foodCandidate != null) {
                inv.setItem(7, foodCandidate); // 8. slot paska
            }
        } catch (Throwable ignored) {}

        // Kompas do otwierania sklepu w trakcie gry (9. slot paska - indeks 8)
        inv.setItem(8, new ItemBuilder(Material.COMPASS, 1).name("§6Sklep (kliknij PPM)").build());

        // własny układ paska gracza (Ustawienia -> Ustawienia klas), jeśli zapisany
        try {
            applyPlayerHotbarLayout(player, kitId, inv);
        } catch (Throwable ignored) {}

        // special: tank gets shield in off-hand if the kit provides a shield item
        try {
            if (kit.getId().equals("tank")) {
                // try to find a shield-like item in kit items or icon
                ItemStack shield = null;
                for (ItemStack it : kit.getItems()) {
                    if (it == null) continue;
                    if (it.getType().name().contains("SHIELD")) { shield = it; break; }
                }
                if (shield == null && kit.getIcon() != null) shield = kit.getIcon();
                if (shield != null) inv.setItemInOffHand(shield.clone());
            }
        } catch (Throwable ignored) {}
    }

    /** Stosuje zapisany przez gracza układ paska (sloty 9-17 z GUI „Ustawienia
     *  klas") dla danej klasy: przedmioty trafiają na sloty paska 0-8 wg zapisu
     *  w players.yml (klucz kit-slots-<klasa>). Brak zapisu = domyślny układ.
     *  Przedmioty znajdowane są po sygnaturze Materiał|nazwa i zamieniane
     *  miejscami. */
    private void applyPlayerHotbarLayout(Player player, String kitId, PlayerInventory inv) {
        String raw = plugin.getPlayerDataManager().get(player.getUniqueId(), player.getName())
                .getSetting(ShopGUI.kitSlotsSettingKey(kitId));
        if (raw == null || raw.isEmpty()) return;
        String[] sigs = raw.split(",", -1);
        if (sigs.length != 9) return;
        for (int i = 0; i < 9; i++) {
            String sig = sigs[i];
            if (sig == null || sig.isEmpty()) continue;
            ItemStack current = inv.getItem(i);
            if (current != null && sig.equals(ShopGUI.kitSlotSignature(current))) continue; // już na miejscu
            for (int j = 0; j < 36; j++) {
                if (j == i) continue;
                ItemStack it = inv.getItem(j);
                if (it == null || it.getType() == Material.AIR) continue;
                if (sig.equals(ShopGUI.kitSlotSignature(it))) {
                    inv.setItem(i, it);
                    inv.setItem(j, current); // zamień miejscami (albo opróżnij)
                    break;
                }
            }
        }
    }

    /**
     * Odwzorowuje układ PASKĄ (9 slotów, 0-8) tak, jak trafi on do ekwipunku
     * gracza przy starcie areny (giveKit): broń na 0, mikstury na 1-6, jedzenie
     * na 7, kompas (sklep) na 8. Bez zbroi. Używane w GUI układania przedmiotów.
     */
    public ItemStack[] getHotbarPreview(String kitId) {
        Kit kit = kits.getOrDefault(kitId, kits.get("wojownik"));
        ItemStack[] inv = new ItemStack[36];

        // broń i przedmioty kitu: podstawowa broń na 0, reszta do pierwszych wolnych slotów
        List<ItemStack> items = kit.getItems();
        if (items != null && !items.isEmpty()) {
            if (items.get(0) != null) inv[0] = items.get(0).clone();
            for (int i = 1; i < items.size(); i++) {
                if (items.get(i) != null) addToInventory(inv, items.get(i).clone());
            }
        }
        // refill (jak refillKitItems): jedzenie na slot 7, reszta do wolnych slotów
        if (kit.getRefillItems() != null) {
            for (ItemStack t : kit.getRefillItems()) {
                if (t == null) continue;
                if (isFood(t.getType())) {
                    if (inv[7] == null) {
                        inv[7] = t.clone();
                    } else if (inv[7].isSimilar(t)
                            && inv[7].getAmount() + t.getAmount() <= inv[7].getMaxStackSize()) {
                        inv[7].setAmount(inv[7].getAmount() + t.getAmount());
                    } else {
                        addToInventory(inv, t.clone());
                    }
                } else {
                    addToInventory(inv, t.clone());
                }
            }
        }
        // przebudowa paska: mikstury na 1-6, pierwsze jedzenie na 7
        int hotbarIndex = 1;
        ItemStack foodCandidate = null;
        for (int i = 0; i < inv.length; i++) {
            ItemStack it = inv[i];
            if (it == null) continue;
            if (it.getType() == Material.SPLASH_POTION) {
                inv[i] = null;
                if (hotbarIndex >= 7) hotbarIndex = 6;
                inv[hotbarIndex] = it;
                hotbarIndex = Math.min(6, hotbarIndex + 1);
            } else if (isFood(it.getType())) {
                if (foodCandidate == null) {
                    foodCandidate = it.clone();
                    inv[i] = null;
                }
            }
        }
        if (foodCandidate != null) inv[7] = foodCandidate;
        // kompas (sklep) na 8
        inv[8] = new ItemBuilder(Material.COMPASS, 1).name("§6Sklep (kliknij PPM)").build();

        ItemStack[] hotbar = new ItemStack[9];
        System.arraycopy(inv, 0, hotbar, 0, 9);
        return hotbar;
    }

    private void addToInventory(ItemStack[] inv, ItemStack it) {
        for (int i = 0; i < inv.length; i++) {
            if (inv[i] == null) {
                inv[i] = it;
                return;
            }
        }
    }

    private boolean isFood(Material m) {
        return switch (m) {
            case COOKED_CHICKEN, COOKED_MUTTON, BAKED_POTATO, COOKED_BEEF, COOKED_PORKCHOP, COOKED_RABBIT, COOKED_SALMON, SWEET_BERRIES, GLOW_BERRIES -> true;
            case GOLDEN_APPLE, APPLE -> true;
            default -> false;
        };
    }
}
