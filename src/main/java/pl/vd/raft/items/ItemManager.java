package pl.vd.raft.items;

import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pl.vd.raft.RaftPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Zarządza customowymi przedmiotami w trybie Raft.
 */
public class ItemManager {

    private final RaftPlugin plugin;
    private final Map<String, CustomItem> customItems = new HashMap<>();

    public ItemManager(RaftPlugin plugin) {
        this.plugin = plugin;
        initializeItems();
    }

    /**
     * Inicjalizuje wszystkie customowe przedmioty.
     */
    private void initializeItems() {
        // Narzędzia
        registerItem("wooden_spear", createWoodenSpear());
        registerItem("stone_spear", createStoneSpear());
        registerItem("iron_spear", createIronSpear());
        registerItem("wooden_axe", createWoodenAxe());
        registerItem("stone_axe", createStoneAxe());
        registerItem("iron_axe", createIronAxe());
        registerItem("diamond_axe", createDiamondAxe());

        registerItem("normal_fishing_rod", createNormalFishingRod());
        registerItem("reinforced_fishing_rod", createReinforcedFishingRod());
        registerItem("solid_fishing_rod", createSolidFishingRod());
        registerItem("diving_mask", createDivingMask());
        registerItem("oxygen_bottle", createOxygenBottle());
        registerItem("breathing_mask", createBreathingMask());
        registerItem("shears", createShears());
        registerItem("construction_hammer", createConstructionHammer());
        registerItem("hook", createStoneHook());

        // Bronie
        registerItem("simple_bow", createSimpleBow());
        registerItem("net_launcher", createNetLauncher());

        // Strzały
        registerItem("stone_arrow", createStoneArrow());
        registerItem("iron_arrow", createIronArrow());
        registerItem("titan_arrow", createTitanArrow());

        // Materiały
        registerItem("plank", createPlank());
        registerItem("rope", createRope());
        registerItem("nail", createNail());
        registerItem("net_canister", createNetCanister());
        registerItem("plastic", createPlastic());
        registerItem("seaweed", createSeaweed());
        registerItem("odpadki", createOdpadki());

        // Jedzenie
        registerItem("shark_meat", createSharkMeat());
        registerItem("coconut", createCoconut());
        registerItem("potato", createPotato());
        registerItem("beetroot", createBeetroot());

        // Woda
        registerItem("zawias", createZawias());
        registerItem("mokra_cegla", createMokraCegla());
        registerItem("sucha_cegla", createSuchaCegla());

        // Materiały
        registerItem("feather", createFeather());
        registerItem("leather", createLeather());
        registerItem("wool", createWool());
        registerItem("ogrom_malz", createOgromMalz());
        registerItem("wybuchowa_maz", createWybuchowaMaz());
        registerItem("wybuchowy_proszek", createWybuchowyProszek());
        registerItem("sand", createSand());
        registerItem("kakao", createKakao());

        // Skrzynki na tratwę (GUI młota → kategoria Tratwy)
        registerItem("chest_small", createChestSmall());
        registerItem("chest_medium", createChestMedium());
        registerItem("chest_large", createChestLarge());
        registerItem("dirty_water", createDirtyWater());
        registerItem("clean_water", createCleanWater());
        registerItem("cup", createCup());

        // Budowlane
        registerItem("wooden_foundation", createWoodenFoundation());
        registerItem("stone_foundation", createStoneFoundation());
        registerItem("collection_net", createCollectionNet());
        registerItem("wooden_stairs", createWoodenStairs());
        registerItem("wooden_support", createWoodenSupport());
        registerItem("wooden_floor", createWoodenFloor());
        registerItem("rope_ladder", createRopeLadder());
        registerItem("stone", createStone());
        registerItem("leaves", createLeaves());

        // Specjalne
        registerItem("binoculars", createBinoculars());
        registerItem("radio", createRadio());
        registerItem("antena", createAntena());
        registerItem("zagiel", createZagiel());
        registerItem("plytka_elektroniczna", createPlytkaElektroniczna());
        
        // Baterie
        registerItem("bateria_mala", createBateriaMala());
        registerItem("bateria_srednia", createBateriaSrednia());
        registerItem("bateria_duza", createBateriaDuza());

        // ─── Raft Physics (z plugin.jar) ───
        registerItem("raft_plank", createRaftPlank());
        registerItem("raft_deck", createRaftDeck());
        registerItem("rowing_paddle", createRowingPaddle());

        // ─── Latarenki w kolorach (CMD 9001-9010) ───
        for (LatarenkaColor c : LATARENKA_COLORS) {
            registerItem("latarenka_" + c.id(), createLatarenka(c));
        }
    }

    // === Narzędzia ===



    private CustomItem createWoodenSpear() {
        ItemStack item = new ItemStack(Material.WOODEN_SPEAR);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6Drewniana Włócznia");
        meta.setCustomModelData(7001);
        item.setItemMeta(meta);
        return new CustomItem("wooden_spear", item, 0);
    }

    private CustomItem createStoneSpear() {
        ItemStack item = new ItemStack(Material.STONE_SPEAR);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§bKamienna włócznia");
        meta.setCustomModelData(7002);
        item.setItemMeta(meta);
        return new CustomItem("stone_spear", item, 0);
    }

    private CustomItem createIronSpear() {
        ItemStack item = new ItemStack(Material.IRON_SPEAR);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§fŻelazna włócznia");
        meta.setCustomModelData(7003);
        item.setItemMeta(meta);
        return new CustomItem("iron_spear", item, 0);
    }

    private CustomItem createWoodenAxe() {
        ItemStack item = new ItemStack(Material.WOODEN_AXE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6Drewniana Siekiera");
        meta.setCustomModelData(8006);
        item.setItemMeta(meta);
        return new CustomItem("wooden_axe", item, 0);
    }

    private CustomItem createStoneAxe() {
        ItemStack item = new ItemStack(Material.STONE_AXE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§7Kamienna Siekiera");
        meta.setCustomModelData(8007);
        item.setItemMeta(meta);
        return new CustomItem("stone_axe", item, 0);
    }

    private CustomItem createIronAxe() {
        ItemStack item = new ItemStack(Material.IRON_AXE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§fŻelazna Siekiera");
        meta.setCustomModelData(8008);
        item.setItemMeta(meta);
        return new CustomItem("iron_axe", item, 0);
    }

    private CustomItem createDiamondAxe() {
        ItemStack item = new ItemStack(Material.DIAMOND_AXE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§bDiamentowa Siekiera");
        meta.setCustomModelData(8009);
        item.setItemMeta(meta);
        return new CustomItem("diamond_axe", item, 0);
    }



    private CustomItem createNormalFishingRod() {
        ItemStack item = new ItemStack(Material.FISHING_ROD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6Zwyczajna wędka");
        meta.setCustomModelData(7004);
        item.setItemMeta(meta);
        // Wytrzymałość: 10 użycień (maxUses - durability)
        item.setDurability((short) (item.getType().getMaxDurability() - 10));
        return new CustomItem("normal_fishing_rod", item, 1);
    }

    private CustomItem createReinforcedFishingRod() {
        ItemStack item = new ItemStack(Material.FISHING_ROD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6Wzmocniona wędka");
        meta.setCustomModelData(7005);
        item.setItemMeta(meta);
        // Wytrzymałość: 30 użycień (maxUses - durability)
        item.setDurability((short) (item.getType().getMaxDurability() - 30));
        return new CustomItem("reinforced_fishing_rod", item, 1);
    }

    private CustomItem createSolidFishingRod() {
        ItemStack item = new ItemStack(Material.FISHING_ROD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6Solidna wędka");
        meta.setCustomModelData(7006);
        item.setItemMeta(meta);
        // Wytrzymałość: 90 użycień (maxUses - durability)
        item.setDurability((short) (item.getType().getMaxDurability() - 90));
        return new CustomItem("solid_fishing_rod", item, 1);
    }

    private CustomItem createStoneHook() {
        // Baza: WĘDKA (FISHING_ROD) — klient natywnie rysuje linkę od ręki do
        // spławika. CMD 8010 rezerwuje miejsce na własny model (gdy powstanie,
        // dodaj próg w assets/minecraft/items/fishing_rod.json).
        ItemStack item = new ItemStack(Material.FISHING_ROD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§7Hak");
        meta.setCustomModelData(plugin.getParent().getConfig()
                .getInt("raft-hook-custom-model-data", 8010));
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        item.setItemMeta(meta);
        return new CustomItem("hook", item, 1);
    }

    private CustomItem createDivingMask() {
        ItemStack item = new ItemStack(Material.LEATHER_HELMET);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§9Maska do nurkowania");
        meta.setCustomModelData(7007);
        item.setItemMeta(meta);
        return new CustomItem("diving_mask", item, 0);
    }

    private CustomItem createOxygenBottle() {
        ItemStack item = new ItemStack(Material.LEATHER_HELMET);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§bButelka z Tlenem");
        meta.setCustomModelData(1001);
        // Usuń domyślną obronę hełmu (leather = 1 armor)
        meta.removeAttributeModifier(Attribute.ARMOR);
        item.setItemMeta(meta);
        // Wytrzymałość: 60 użycień
        item.setDurability((short) (item.getType().getMaxDurability() - 60));
        return new CustomItem("oxygen_bottle", item, 1);
    }

    private CustomItem createBreathingMask() {
        ItemStack item = new ItemStack(Material.TURTLE_HELMET);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§bMaska oddechowa");
        meta.setCustomModelData(1002);
        // Zamień obronę z 2 na 1
        meta.removeAttributeModifier(Attribute.ARMOR);
        meta.addAttributeModifier(Attribute.ARMOR,
                new org.bukkit.attribute.AttributeModifier(
                        java.util.UUID.randomUUID(), "generic.armor", 1,
                        org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER,
                        org.bukkit.inventory.EquipmentSlotGroup.HEAD));
        item.setItemMeta(meta);
        // Wytrzymałość: 150 użycień
        item.setDurability((short) (item.getType().getMaxDurability() - 150));
        return new CustomItem("breathing_mask", item, 1);
    }

    private CustomItem createShears() {
        ItemStack item = new ItemStack(Material.SHEARS);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6Nożyce");
        meta.setCustomModelData(7008);
        item.setItemMeta(meta);
        // Wytrzymałość: 20 użycień
        item.setDurability((short) (item.getType().getMaxDurability() - 20));
        return new CustomItem("shears", item, 1);
    }

    private CustomItem createConstructionHammer() {
        ItemStack item = new ItemStack(Material.MACE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6Młot budowlany");
        meta.setCustomModelData(4002);
        // TYLKO prędkość ataku — młot ma być narzędziem, nie bronią.
        // ATTACK_SPEED: +100% (MULTIPLY_SCALAR_1) na szybkości waniliowego młota
        // (0.6/s → 1.2/s) = natychmiastowe CLI-knięcia bez odnowienia.
        // Obrażenia (bicie) BEZ zmian — waniliowe mace (5 pkt) zostają.
        var mod = new org.bukkit.attribute.AttributeModifier(
                new org.bukkit.NamespacedKey(plugin.getParent(), "raft_hammer_speed"),
                1.0,
                org.bukkit.attribute.AttributeModifier.Operation.ADD_SCALAR,
                org.bukkit.inventory.EquipmentSlotGroup.MAINHAND);
        meta.addAttributeModifier(Attribute.ATTACK_SPEED, mod);
        item.setItemMeta(meta);
        return new CustomItem("construction_hammer", item, 0);
    }

    // === Bronie ===



    private CustomItem createSimpleBow() {
        ItemStack item = new ItemStack(Material.BOW);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6Prosty łuk");
        meta.setCustomModelData(7009);
        item.setItemMeta(meta);
        // Wytrzymałość: 120 użycień
        item.setDurability((short) (item.getType().getMaxDurability() - 120));
        return new CustomItem("simple_bow", item, 1);
    }

    private CustomItem createNetLauncher() {
        ItemStack item = new ItemStack(Material.CROSSBOW);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6Wyrzutnia sieci");
        meta.setCustomModelData(2001);
        item.setItemMeta(meta);
        // Wytrzymałość: 8 użycień
        item.setDurability((short) (item.getType().getMaxDurability() - 8));
        return new CustomItem("net_launcher", item, 1);
    }

    // === Strzały ===

    private CustomItem createStoneArrow() {
        ItemStack item = new ItemStack(Material.TIPPED_ARROW, 16);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§7Kamienna strzała");
        meta.setCustomModelData(3001);
        meta.setMaxStackSize(32);
        meta.addEnchant(org.bukkit.enchantments.Enchantment.SHARPNESS, 1, true);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return new CustomItem("stone_arrow", item, 1);
    }

    private CustomItem createIronArrow() {
        ItemStack item = new ItemStack(Material.TIPPED_ARROW, 16);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§fMetalowa strzała");
        meta.setCustomModelData(3002);
        meta.setMaxStackSize(32);
        meta.addEnchant(org.bukkit.enchantments.Enchantment.SHARPNESS, 2, true);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return new CustomItem("iron_arrow", item, 1);
    }

    private CustomItem createTitanArrow() {
        ItemStack item = new ItemStack(Material.TIPPED_ARROW, 16);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§bTytanowa strzała");
        meta.setCustomModelData(3003);
        meta.setMaxStackSize(32);
        meta.addEnchant(org.bukkit.enchantments.Enchantment.SHARPNESS, 3, true);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return new CustomItem("titan_arrow", item, 1);
    }

    // === Materiały ===

    private CustomItem createPlank() {
        ItemStack item = new ItemStack(Material.OAK_PLANKS, 4);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6Deska");
        meta.setCustomModelData(7010);
        item.setItemMeta(meta);
        return new CustomItem("plank", item, 0);
    }

    private CustomItem createRope() {
        // „Lina" — baza LEAD (pack: override CMD 1002 → raft:item/lina_custom).
        ItemStack item = new ItemStack(Material.LEAD, 2);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§eLina");
        meta.setCustomModelData(1002);
        // Model z resource packa raft-hud: assets/raft/models/item/lead.json
        // (override custom_model_data 1002 → raft:item/lina_custom).
        // UWAGA (wnioski z Odpadków): definicja assets/raft/items/lead.json
        // NIE istnieje w packu, więc komponent item_model dałby „missing texture".
        // CMD-override działa bez niego (model bazowy LEAD istnieje waniliowo).
        item.setItemMeta(meta);
        return new CustomItem("rope", item, 0);
    }

    private CustomItem createNail() {
        ItemStack item = new ItemStack(Material.IRON_NUGGET, 3);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§7Gwoździe");
        meta.setCustomModelData(7012);
        item.setItemMeta(meta);
        return new CustomItem("nail", item, 0);
    }

    private CustomItem createNetCanister() {
        ItemStack item = new ItemStack(Material.FIREWORK_ROCKET);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§eKanister z siecí");
        meta.setCustomModelData(2002);
        item.setItemMeta(meta);
        return new CustomItem("net_canister", item, 16);
    }



    private CustomItem createPlastic() {
        ItemStack item = new ItemStack(Material.FIREWORK_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§aPlastik");
        meta.setCustomModelData(7013);
        item.setItemMeta(meta);
        return new CustomItem("plastic", item, 0);
    }

    private CustomItem createSeaweed() {
        ItemStack item = new ItemStack(Material.KELP);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§aWodorosty");
        meta.setCustomModelData(7014);
        item.setItemMeta(meta);
        return new CustomItem("seaweed", item, 0);
    }

    private CustomItem createOdpadki() {
        ItemStack item = new ItemStack(Material.PRISMARINE_SHARD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§7Odpadki");
        meta.setCustomModelData(1007);
        // Świadomie BEZ custom tekstury: komponent item_model wskazywał na
        // raft:prismarine_shard, ale definicja assets/raft/items/prismarine_shard.json
        // nie istnieje w packu → klient renderował „missing texture” (fioletowo-
        // czarna szachownica). Bez item_model item używa wanilijnego modelu
        // PRISMARINE_SHARD i wygląda poprawnie.
        item.setItemMeta(meta);
        return new CustomItem("odpadki", item, 0);
    }



    private CustomItem createSharkMeat() {
        ItemStack item = new ItemStack(Material.BEEF);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§cMięso Rekina");
        meta.setCustomModelData(7015);
        item.setItemMeta(meta);
        return new CustomItem("shark_meat", item, 0);
    }

    private CustomItem createCoconut() {
        ItemStack item = new ItemStack(Material.COCOA_BEANS);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§aZiarna kakaowe");
        meta.setCustomModelData(7016);
        // BEZ FoodComponent — ziarna kakaowe nie są jadalne (COCOA_BEANS nie ma
        // waniliowego jedzenia, a FoodComponent umożliwiał zjadanie).
        item.setItemMeta(meta);
        return new CustomItem("coconut", item, 0);
    }

    // === Woda ===

    private CustomItem createPotato() {
        ItemStack item = new ItemStack(Material.POTATO);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§eZiemniak");
        meta.setCustomModelData(7022);
        // FoodComponent — jadalny (jak waniliowy surowy ziemniak)
        org.bukkit.inventory.meta.components.FoodComponent food = meta.getFood();
        food.setNutrition(1);
        food.setSaturation(0.3f);
        meta.setFood(food);
        item.setItemMeta(meta);
        return new CustomItem("potato", item, 0);
    }

    private CustomItem createBeetroot() {
        ItemStack item = new ItemStack(Material.BEETROOT);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§cBurak");
        meta.setCustomModelData(7023);
        // FoodComponent — jadalny (jak waniliowy burak)
        org.bukkit.inventory.meta.components.FoodComponent food = meta.getFood();
        food.setNutrition(1);
        food.setSaturation(1.2f);
        meta.setFood(food);
        item.setItemMeta(meta);
        return new CustomItem("beetroot", item, 0);
    }

    private CustomItem createZawias() {
        ItemStack item = new ItemStack(Material.IRON_TRAPDOOR);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§7Zawias");
        meta.setCustomModelData(7029);
        item.setItemMeta(meta);
        return new CustomItem("zawias", item, 0);
    }

    private CustomItem createMokraCegla() {
        ItemStack item = new ItemStack(Material.RESIN_BRICK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6Mokra cegła");
        meta.setCustomModelData(7030);
        item.setItemMeta(meta);
        return new CustomItem("mokra_cegla", item, 0);
    }

    private CustomItem createSuchaCegla() {
        ItemStack item = new ItemStack(Material.BRICK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6Sucha cegła");
        meta.setCustomModelData(7031);
        item.setItemMeta(meta);
        return new CustomItem("sucha_cegla", item, 0);
    }

    private CustomItem createFeather() {
        // Nazwa waniliowa zostaje — bez displayName
        ItemStack item = new ItemStack(Material.FEATHER);
        return new CustomItem("feather", item, 0);
    }

    private CustomItem createLeather() {
        // Nazwa waniliowa zostaje — bez displayName
        ItemStack item = new ItemStack(Material.LEATHER);
        return new CustomItem("leather", item, 0);
    }

    private CustomItem createWool() {
        // Nazwa waniliowa zostaje — bez displayName (biała wełna)
        ItemStack item = new ItemStack(Material.WHITE_WOOL);
        return new CustomItem("wool", item, 0);
    }

    private CustomItem createOgromMalz() {
        ItemStack item = new ItemStack(Material.NAUTILUS_SHELL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§bOgrom małż");
        meta.setCustomModelData(7032);
        item.setItemMeta(meta);
        return new CustomItem("ogrom_malz", item, 0);
    }

    private CustomItem createWybuchowaMaz() {
        ItemStack item = new ItemStack(Material.CYAN_DYE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§3Wybuchowa maź");
        meta.setCustomModelData(7033);
        item.setItemMeta(meta);
        return new CustomItem("wybuchowa_maz", item, 0);
    }

    private CustomItem createWybuchowyProszek() {
        ItemStack item = new ItemStack(Material.GUNPOWDER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§7Wybuchowy proszek");
        meta.setCustomModelData(7034);
        item.setItemMeta(meta);
        return new CustomItem("wybuchowy_proszek", item, 0);
    }

    private CustomItem createSand() {
        // Nazwa waniliowa zostaje — bez displayName (żółty piasek)
        ItemStack item = new ItemStack(Material.SAND);
        return new CustomItem("sand", item, 0);
    }

    private CustomItem createKakao() {
        ItemStack item = new ItemStack(Material.SWEET_BERRIES);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6Kakao");
        meta.setCustomModelData(7037);
        // Świadomie BEZ tekstury/modelu w packu — CMD(7037) zarezerwowany,
        // a item używa na razie waniliowego wyglądu SWEET_BERRIES.
        item.setItemMeta(meta);
        return new CustomItem("kakao", item, 0);
    }

    private CustomItem createChestSmall() {
        ItemStack item = new ItemStack(Material.TRAPPED_CHEST);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6Mała skrzynka");
        meta.setCustomModelData(7035);
        List<String> lore = new ArrayList<>();
        lore.add("§7Pojemność: §f18 slotów");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return new CustomItem("chest_small", item, 0);
    }

    private CustomItem createChestMedium() {
        // Nazwa waniliowa zostaje — bez displayName („Skrzynia")
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        List<String> lore = new ArrayList<>();
        lore.add("§7Pojemność: §f36 slotów");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return new CustomItem("chest_medium", item, 0);
    }

    private CustomItem createChestLarge() {
        ItemStack item = new ItemStack(Material.WAXED_COPPER_CHEST);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6Duża skrzynia");
        meta.setCustomModelData(7036);
        List<String> lore = new ArrayList<>();
        lore.add("§7Pojemność: §f54 sloty");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return new CustomItem("chest_large", item, 0);
    }

    private CustomItem createDirtyWater() {
        ItemStack item = new ItemStack(Material.POTION);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§8Brudna Woda");
        meta.setCustomModelData(7017);
        item.setItemMeta(meta);
        // Ustaw typ mikstury na mdłą (MUNDANE)
        if (item.getItemMeta() instanceof org.bukkit.inventory.meta.PotionMeta pm) {
            pm.setBasePotionType(org.bukkit.potion.PotionType.MUNDANE);
            item.setItemMeta(pm);
        }
        return new CustomItem("dirty_water", item, 0);
    }

    private CustomItem createCleanWater() {
        ItemStack item = new ItemStack(Material.POTION);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§bCzysta Woda");
        meta.setCustomModelData(7018);
        item.setItemMeta(meta);
        // Ustaw typ mikstury na wodę (PotionType.WATER)
        if (item.getItemMeta() instanceof org.bukkit.inventory.meta.PotionMeta pm) {
            pm.setBasePotionType(org.bukkit.potion.PotionType.WATER);
            item.setItemMeta(pm);
        }
        return new CustomItem("clean_water", item, 0);
    }

    private CustomItem createCup() {
        ItemStack item = new ItemStack(Material.BOWL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§7Miska");
        meta.setCustomModelData(7019);
        item.setItemMeta(meta);
        return new CustomItem("cup", item, 0);
    }

    // === Budowlane ===

    private CustomItem createWoodenFoundation() {
        ItemStack item = new ItemStack(Material.OAK_PLANKS, 9);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6Drewniana Podłoga");
        meta.setCustomModelData(7020);
        item.setItemMeta(meta);
        return new CustomItem("wooden_foundation", item, 0);
    }

    private CustomItem createStoneFoundation() {
        ItemStack item = new ItemStack(Material.COBBLESTONE, 9);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§7Kamienna Podłoga");
        meta.setCustomModelData(7021);
        item.setItemMeta(meta);
        return new CustomItem("stone_foundation", item, 0);
    }

    private CustomItem createCollectionNet() {
        ItemStack item = new ItemStack(Material.EXPOSED_COPPER_GRATE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§bSieć zbierająca");
        meta.setCustomModelData(8010);
        item.setItemMeta(meta);
        return new CustomItem("collection_net", item, 0);
    }

    /**
     * Drewniane schody (kategoria „Inne" w GUI młota) — komórka
     * {@link pl.vd.raft.entity.RaftEntity.CellType#STAIRS} stawiana NA pokładzie.
     * Kierunek obraca się kółkiem myszy / presetami 1–4 (RaftBuildListener,
     * docs/RAFT_BALANCE.md §1a). BlockData schodów tworzy
     * {@code RaftEntity.dataFor} (OAK_STAIRS + facing), więc postawiona komórka,
     * podgląd-duch i podświetlenie siekiery rysują ten sam obrót.
     */
    private CustomItem createWoodenStairs() {
        ItemStack item = new ItemStack(Material.OAK_STAIRS);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6Drewniane schody");
        meta.setCustomModelData(7038);
        meta.setLore(List.of(
                "§7Budowlane: postaw na pokładzie tratwy.",
                "§7Kółko myszy obraca o 90° (w górę — w prawo),",
                "§7klawisze 1–4 ustawiają kierunek N/E/S/W."
        ));
        item.setItemMeta(meta);
        return new CustomItem("wooden_stairs", item, 0);
    }

    /**
     * PIĘTROWA podłoga (kategoria „Inne" w GUI młota) — jedyny element, którym
     * da się postawić platformę piętra (poziom Y=3, `RaftEntity.floorLevel()`).
     * Świadomie NIE da się jej postawić jako pokład tratwy na wodzie
     * (`RaftBuildListener.isDeckBuild` = false) — podgląd-duch w ogóle się nie
     * pokazuje, więc gracz nie rozbuduje z niej tratwy. Stawianie: celuj w słupek
     * podpory (pierwsza komórka piętra) albo w istniejące piętro (dobudowa w bok,
     * zawsze w 4 komórkach wokół podpory). Materiał komórki to deski (OAK_PLANKS),
     * tak samo jak przy drewnianym fundamencie.
     */
    private CustomItem createWoodenFloor() {
        ItemStack item = new ItemStack(Material.OAK_PLANKS, 9);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6Podłoga");
        meta.setCustomModelData(7040);
        meta.setLore(List.of(
                "§7Budowlane: TYLKO piętro (górna podłoga).",
                "§7Stawiaj celując w słupek podpory albo w gotowe piętro.",
                "§7Maks. 1 blok od podpory — dalej postaw kolejną podporę.",
                "",
                "§8Na wodzie (jako pokład tratwy) się NIE postawi."
        ));
        item.setItemMeta(meta);
        return new CustomItem("wooden_floor", item, 0);
    }

    /**
     * Podpora piętra (kategoria „Inne" w GUI młota) — płotek rozciągnięty
     * w pionie (config.yml: raft-support.height-blocks), stawiany na NAROŻNIKU
     * 4 komórek. Nie zajmuje komórki siatki i NIE ma kolizji: to wizual, który
     * „trzyma" piętro (1 encja na podporę — RaftSupportRenderer). Zasady pięter
     * pilnuje RaftBuildListener/RaftEntity.isCellSupported.
     */
    private CustomItem createWoodenSupport() {
        ItemStack item = new ItemStack(Material.OAK_FENCE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6Podpora piętra");
        meta.setCustomModelData(7039);
        meta.setLore(List.of(
                "§7Element budowlany: stawiasz na NAROŻNIKU 4 bloków.",
                "§7Piętro dobudujesz max 1 blok od podpory,",
                "§7dalej postaw kolejną podporę (węzły co 2 kratki).",
                "",
                "§8Bez kolizji — przechodzisz przez słupek."
        ));
        item.setItemMeta(meta);
        return new CustomItem("wooden_support", item, 0);
    }

    private CustomItem createRopeLadder() {
        ItemStack item = new ItemStack(Material.LADDER, 3);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§eDrabina Linowa");
        meta.setCustomModelData(7025);
        item.setItemMeta(meta);
        return new CustomItem("rope_ladder", item, 0);
    }

    private CustomItem createStone() {
        ItemStack item = new ItemStack(Material.COBBLESTONE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§7Kamień");
        meta.setCustomModelData(7026);
        item.setItemMeta(meta);
        return new CustomItem("stone", item, 0);
    }

    private CustomItem createLeaves() {
        ItemStack item = new ItemStack(Material.AZALEA_LEAVES);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§2Liście");
        meta.setCustomModelData(7027);
        item.setItemMeta(meta);
        return new CustomItem("leaves", item, 0);
    }

    // === Specjalne ===

    private CustomItem createBinoculars() {
        ItemStack item = new ItemStack(Material.SPYGLASS);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6Lornetka");
        meta.setCustomModelData(7028);
        item.setItemMeta(meta);
        return new CustomItem("binoculars", item, 0);
    }

    private CustomItem createRadio() {
        ItemStack item = new ItemStack(Material.HEAVY_CORE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§eRadio");
        meta.setCustomModelData(4001);
        item.setItemMeta(meta);
        return new CustomItem("radio", item, 0);
    }

    /**
     * Antena (faza 2 projektu anten, docs/ANTENY_ODBIORNIK.md).
     * CMD 4004 — numeracja ręczna (AGENTS.md pkt 5): 4001 radio, 4002 młot,
     * 4003 duża beczka, 4004 antena. PPM w pokład tratwy = stawia antenę
     * (AntennaPlaceListener). Model w packu (heavy_core.json) do dorzucenia
     * przy eksporcie z Blockbencna — póki co waniliowy heavy_core.
     */
    private CustomItem createAntena() {
        ItemStack item = new ItemStack(Material.HEAVY_CORE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§bAntena");
        meta.setLore(java.util.Arrays.asList(
                "§7Postaw na pokładzie tratwy w zasięgu",
                "§78 bloków od odbiornika (ten sam poziom).",
                "§7Odbiornik potrzebuje 3 anten.",
                "",
                "§ePPM w pokład — postaw"
        ));
        meta.setCustomModelData(4004);
        item.setItemMeta(meta);
        return new CustomItem("antena", item, 0);
    }

    private CustomItem createPlytkaElektroniczna() {
        ItemStack item = new ItemStack(Material.COMPARATOR);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§bPłytka elektroniczna");
        meta.setCustomModelData(5001);
        item.setItemMeta(meta);
        return new CustomItem("plytka_elektroniczna", item, 16);
    }
    
    // === Baterie ===
    
    private CustomItem createBateriaMala() {
        ItemStack item = new pl.vd.raft.items.BateriaItem(
            pl.vd.raft.items.BateriaItem.BateriaType.MALA).toItemStack();
        return new CustomItem("bateria_mala", item, 0);
    }
    
    private CustomItem createBateriaSrednia() {
        ItemStack item = new pl.vd.raft.items.BateriaItem(
            pl.vd.raft.items.BateriaItem.BateriaType.SREDNIA).toItemStack();
        return new CustomItem("bateria_srednia", item, 0);
    }
    
    private CustomItem createBateriaDuza() {
        ItemStack item = new pl.vd.raft.items.BateriaItem(
            pl.vd.raft.items.BateriaItem.BateriaType.DUZA).toItemStack();
        return new CustomItem("bateria_duza", item, 0);
    }

    // === Raft Physics (z plugin.jar) ===

    private CustomItem createRaftPlank() {
        ItemStack item = new ItemStack(Material.OAK_PLANKS, 4);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6Deska Tratwowa");
        meta.setCustomModelData(8001);
        item.setItemMeta(meta);
        return new CustomItem("raft_plank", item, 64);
    }

    private CustomItem createRaftDeck() {
        ItemStack item = new ItemStack(Material.OAK_PLANKS, 2);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§ePodłoga Tratwy");
        meta.setCustomModelData(8002);
        item.setItemMeta(meta);
        return new CustomItem("raft_deck", item, 64);
    }

    /**
     * Żagiel tratwy (CMD 4005 — numeracja ręczna, AGENTS.md pkt 5: 4001 radio,
     * 4002 odbiornik, 4003 duża beczka, 4004 antena, 4005 żagiel). Zastąpił
     * martwe przedmioty waniliowe „Maszt" (8003) i „Żagiel" (8004).
     *
     * PPM żaglem w pokład tratwy stawia maszt z żaglem (SailPlaceListener —
     * komponenty MAST + SAIL, zapisywane w raft-saves.yml), PPM w komórkę
     * stojącego żagla (nawet pustą ręką) rozwija go/zwija — napęd z wiatru albo
     * z prądu wody (RaftPhysicsEngine), Shift + PPM żaglem demontuje. Geometrię
     * rysuje mod z paczki
     * (heavy_core.json → raft:device → raft:rafthud/zagiel); na razie stub.
     */
    private CustomItem createZagiel() {
        ItemStack item = new ItemStack(Material.HEAVY_CORE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§fŻagiel");
        meta.setLore(java.util.Arrays.asList(
                "§7Postaw na pokładzie tratwy — maszt z żaglem.",
                "§7PPM w żagiel: rozwiń / zwiń",
                "§7(napęd z wiatru albo z prądu wody).",
                "§7Shift + PPM: demontuj.",
                "",
                "§ePPM w pokład — postaw"
        ));
        meta.setCustomModelData(4005);
        item.setItemMeta(meta);
        return new CustomItem("zagiel", item, 16);
    }

    private CustomItem createRowingPaddle() {
        ItemStack item = new ItemStack(Material.GOLDEN_SHOVEL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6Wiosło");
        meta.setCustomModelData(8005);
        // Unbreakable
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        // Attack damage = 2, speed = 1.2
        meta.addAttributeModifier(org.bukkit.attribute.Attribute.ATTACK_DAMAGE,
                new org.bukkit.attribute.AttributeModifier(
                        java.util.UUID.randomUUID(), "generic.attack_damage", 2,
                        org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER,
                        org.bukkit.inventory.EquipmentSlotGroup.MAINHAND));
        meta.addAttributeModifier(org.bukkit.attribute.Attribute.ATTACK_SPEED,
                new org.bukkit.attribute.AttributeModifier(
                        java.util.UUID.randomUUID(), "generic.attack_speed", 1.2,
                        org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER,
                        org.bukkit.inventory.EquipmentSlotGroup.MAINHAND));
        item.setItemMeta(meta);
        return new CustomItem("rowing_paddle", item, 1);
    }

    // === Latarenki w kolorach ===

    /**
     * Jeden kolor latarenki.
     *<p>
     * CustomModelData wybiera model w resource packu raft-hud:
     * {@code pack/assets/minecraft/items/lantern.json} robi range_dispatch po
     * {@code custom_model_data} i wskazuje na {@code raft:item/latarenka_<id>}.
     * Waniliowa latarenka nie ma CMD, więc trafia w „fallback” i wygląda waniliowo.
     */
    public record LatarenkaColor(String id, int customModelData, String colorCode, String label) {}

    /** Kolejność = kolejność na liście w komendzie. CMD muszą być rosnące. */
    public static final List<LatarenkaColor> LATARENKA_COLORS = List.of(
            new LatarenkaColor("fioletowa", 9001, "§d", "fioletowa"),
            new LatarenkaColor("czerwona", 9002, "§c", "czerwona"),
            new LatarenkaColor("zielona", 9003, "§a", "zielona"),
            new LatarenkaColor("niebieska", 9004, "§9", "niebieska"),
            new LatarenkaColor("zolta", 9005, "§e", "żółta"),
            new LatarenkaColor("biala", 9006, "§f", "biała"),
            new LatarenkaColor("pomaranczowa", 9007, "§6", "pomarańczowa"),
            new LatarenkaColor("rozowa", 9008, "§d", "różowa"),
            new LatarenkaColor("morska", 9009, "§3", "morska"),
            new LatarenkaColor("szara", 9010, "§8", "szara")
    );

    /** Dodatkowe nazwy kolorów (angielskie i warianty z polskimi znakami). */
    private static final Map<String, List<String>> LATARENKA_ALIASES = Map.ofEntries(
            Map.entry("fioletowa", List.of("purple", "violet", "fiolet")),
            Map.entry("czerwona", List.of("red", "czerwony")),
            Map.entry("zielona", List.of("green", "zielony")),
            Map.entry("niebieska", List.of("blue", "niebieski")),
            Map.entry("zolta", List.of("yellow", "żółta", "zolta")),
            Map.entry("biala", List.of("white", "biały", "biala")),
            Map.entry("pomaranczowa", List.of("orange", "pomarańczowa")),
            Map.entry("rozowa", List.of("pink", "różowa")),
            Map.entry("morska", List.of("cyan", "sea", "aqua", "turkusowa")),
            Map.entry("szara", List.of("gray", "grey", "szary"))
    );

    /**
     * Tworzy latarenkę w danym kolorze — ZWYKŁA waniliowa latarenka (Material.LANTERN)
     * z CustomModelData i kolorową nazwą. Celowo bez PDC i bez własnej logiki, więc
     * stawianie, zdejmowanie i stackowanie działa dokładnie jak w wanilii.
     */
    private CustomItem createLatarenka(LatarenkaColor c) {
        ItemStack item = new ItemStack(Material.LANTERN);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(c.colorCode() + "Latarenka " + c.label());
            meta.setCustomModelData(c.customModelData());
            item.setItemMeta(meta);
        }
        return new CustomItem("latarenka_" + c.id(), item, 64);
    }

    /**
     * Znajduje kolor po nazwie: „fioletowa”, „latarenka_fioletowa” albo „purple”.
     * Zwraca null, jeśli nazwa nie pasuje do żadnego koloru.
     */
    public static LatarenkaColor findLatarenkaColor(String input) {
        if (input == null) return null;
        String s = input.trim().toLowerCase(java.util.Locale.ROOT);
        if (s.startsWith("latarenka_")) s = s.substring("latarenka_".length());
        for (LatarenkaColor c : LATARENKA_COLORS) {
            if (c.id().equals(s)) return c;
            List<String> aliases = LATARENKA_ALIASES.get(c.id());
            if (aliases != null && aliases.contains(s)) return c;
        }
        return null;
    }

    /** ID wszystkich latarenek — do tab-completion i wiadomości pomocy. */
    public static List<String> latarenkaItemIds() {
        List<String> ids = new ArrayList<>();
        for (LatarenkaColor c : LATARENKA_COLORS) ids.add("latarenka_" + c.id());
        return ids;
    }

    // === Metody pomocnicze ===

    private void registerItem(String id, CustomItem item) {
        customItems.put(id, item);
    }

    public CustomItem getItem(String id) {
        return customItems.get(id);
    }

    public ItemStack createItem(String id) {
        CustomItem custom = customItems.get(id);
        if (custom == null) return null;
        return custom.getItemStack().clone();
    }

    public boolean isCustomItem(ItemStack item, String id) {
        if (item == null || !item.hasItemMeta()) return false;
        CustomItem custom = customItems.get(id);
        if (custom == null) return false;
        return item.getItemMeta().equals(custom.getItemStack().getItemMeta());
    }

    /**
     * Rozpoznanie Młota budowlanego odporne na stare egzemplarze: pełne
     * porównanie ItemMeta (isCustomItem) zawodzi, gdy meta przedmiotu się
     * różni (np. egzemplarz sprzed zmian lore/nazw). Młot to MACE z CMD 4002
     * albo z naszą nazwą — wystarczy którekolwiek.
     */
    public boolean isConstructionHammer(ItemStack item) {
        if (item == null || item.getType() != Material.MACE) return false;
        if (!item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta.hasCustomModelData() && meta.getCustomModelData() == 4002) return true;
        return meta.hasDisplayName() && meta.getDisplayName().contains("Młot budowlany");
    }

    public Map<String, CustomItem> getAllItems() {
        return customItems;
    }
}
