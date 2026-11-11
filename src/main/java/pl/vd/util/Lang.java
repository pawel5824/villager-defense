package pl.vd.util;

import org.bukkit.entity.Player;
import pl.vd.VillagerDefensePlugin;

import java.util.HashMap;
import java.util.Map;

/**
 * System tłumaczeń PL/EN per gracz. Każdy komunikat ma klucz i dwie wersje
 * (polską i angielską); przy wysyłce wybierana jest wersja wg ustawienia
 * gracza (panel Ustawienia -> Język, klucz „language" w players.yml).
 * Dynamiczne fragmenty podstawiane są przez {0}, {1}, ... w kolejności.
 */
public final class Lang {

    private static VillagerDefensePlugin plugin;

    private static final Map<String, String> PL = new HashMap<>();
    private static final Map<String, String> EN = new HashMap<>();

    static {
        // ---------- przebieg gry ----------
        put("arena-not-ready", "§cTa arena nie jest jeszcze skonfigurowana. {0}",
                "§cThis arena is not configured yet. {0}");
        put("missing-list", "§7(brakuje: {0})", "§7(missing: {0})");
        put("missing-lobby-end", "lobby/end", "lobby/end");
        put("missing-player-spawns", "spawny graczy", "player spawns");
        put("missing-villager-spawns", "spawny wieśniaków", "villager spawns");
        put("game-in-progress", "§cGra już trwa na tej arenie.",
                "§cA game is already in progress on this arena.");
        put("arena-full", "§cArena jest pełna.", "§cThis arena is full.");
        put("instance-created", "§aDołączono do nowej instancji mapy §e{0}§a.",
                "§aJoined a new instance of map §e{0}§a.");
        put("instance-limit", "§cTrwa już zbyt wiele gier na tej mapie. Spróbuj ponownie za chwilę.",
                "§cToo many games are running on this map right now. Try again in a moment.");
        put("join-broadcast", "§e{0} §7dołączył do gry ({1}/{2})",
                "§e{0} §7joined the game ({1}/{2})");
        put("join-accelerated", "§e{0} dołączył - start przyspieszony! Start za §f{1}s",
                "§e{0} joined - start accelerated! Start in §f{1}s");
        put("start-countdown", "§7Start za §e{0}s", "§7Start in §e{0}s");
        put("game-started", "§aGra wystartowała! Chrońcie wieśniaków!",
                "§aGame started! Protect the villagers!");
        put("round-started", "§6Runda {0} rozpoczęta!", "§6Round {0} has started!");
        put("round-finished", "§aRunda {0} zakończona!", "§aRound {0} finished!");
        put("next-round-in", "§7Kolejna runda za §e{0} sekund", "§7Next round in §e{0} seconds");
        put("win", "§aWygraliście! Przetrwaliście {0} rund!",
                "§aYou won! You survived {0} rounds!");
        put("win-single", "§aWygrałeś! Przetrwałeś {0} rund!",
                "§aYou won! You survived {0} rounds!");
        put("lose", "§cPrzegraliście na rundzie {0}. Wieśniacy zginęli lub gracze opuścili grę.",
                "§cYou lost on round {0}. The villagers died or the players left the game.");
        put("session-points", "§7Łącznie w tej grze zdobyto: §e{0} pkt.",
                "§7Total points earned this game: §e{0} pts.");
        put("teleported-with-kit", "§aTeleportowano na arenę z kitem: §f{0}",
                "§aTeleported to the arena with kit: §f{0}");

        // ---------- zrzut zaopatrzenia ----------
        put("supply-coming-screen", "§fZrzut zaopatrzenia nadchodzi!",
                "§fSupply drop incoming!");
        put("supply-landed", "§6✈ §fZrzut zaopatrzenia wylądował! §7(X: {0}, Z: {1}) — szukajcie skrzyni!",
                "§6✈ §fSupply drop landed! §7(X: {0}, Z: {1}) — look for the chest!");
        put("supply-landed-screen", "§fZrzut zaopatrzenia pojawił się na arenie",
                "§fSupply drop has appeared on the arena");
        put("supply-received", "§aOtrzymałeś/aś ze zrzutu: §f{0}",
                "§aYou received from the supply drop: §f{0}");
        put("supply-click-chest", "§6✈ §fSkrzynia zrzutu wylądowała - kliknij ją, żeby odebrać łup!",
                "§6✈ §fSupply drop landed - click the chest to collect!");
        put("supply-slot-title", "§8Zrzut zaopatrzenia", "§8Supply Drop");
        put("supply-not-owner", "§cTo nie Twój zrzut zaopatrzenia.",
                "§cThis isn't your supply drop.");
        put("supply-collected", "§e{0} §fodebrał łup ze zrzutu",
                "§e{0} §fcollected the supply drop loot");
        put("shop-item-cooked_salmon", "§fPieczony łosoś", "§fCooked Salmon");
        put("shop-item-iron_ingot", "§fŻelazna sztabka", "§fIron Ingot");
        put("shop-item-gold_ingot", "§fZłota sztabka", "§fGold Ingot");

        // ---------- scoreboard ----------
        put("sb-wave", "Fala: {0}", "Wave: {0}");
        put("sb-points", "Punkty: {0}", "Points: {0}");
        put("sb-kills", "Zabójstwa: {0}", "Kills: {0}");
        put("sb-time", "Czas: {0}", "Time: {0}");

        // ---------- punkty za zabójstwa ----------
        put("kill-points", "§a+{0} pkt", "§a+{0} pts");
        put("kill-points-turret", "§a+{0} pkt (wieżyczka)", "§a+{0} pts (turret)");

        // ---------- sklep ----------
        put("turret-gone", "§cWieżyczka już nie istnieje.", "§cThe turret no longer exists.");
        put("turret-preset-missing", "§cBrak zapisu stojaka: §e{0}§c.",
                "§cMissing stand preset: §e{0}§c.");
        put("not-enough-points-turret", "§cNie masz wystarczająco punktów! Potrzebujesz §e{0} pkt§c.",
                "§cNot enough points! You need §e{0} pts§c.");
        put("turret-upgraded", "§aUlepszono wieżyczkę do: §e{0}§a (-{1} pkt).",
                "§aTurret upgraded to: §e{0}§a (-{1} pts).");
        put("not-enough-points", "§cNie masz wystarczająco punktów!",
                "§cYou don't have enough points!");
        put("effect-gained", "§aOtrzymano efekt: {0} {1}", "§aEffect gained: {0} {1}");
        put("cannot-upgrade-more", "§cNie można go już ulepszyć",
                "§cIt can't be upgraded any further");
        put("bought", "§aKupiono!", "§aPurchased!");
        put("wolf-summoned", "§aWilk przywołany z zbroją wilka!",
                "§aWolf summoned with wolf armor!");
        put("snowman-summoned", "§aBałwan przywołany!", "§aSnowman summoned!");
        put("no-weapon", "§cNie masz takiej broni w ekwipunku.",
                "§cYou don't have that weapon in your inventory.");
        put("max-enchant-level", "§cTo zaklęcie ma już maksymalny poziom (§e{0}§c).",
                "§cThis enchantment is already at max level (§e{0}§c).");
        put("max-level", "§cMaksymalny poziom tego zaklęcia to §e{0}§c.",
                "§cMax level of this enchantment is §e{0}§c.");
        put("enchant-upgraded", "§aUlepszono: §f{0} {1}", "§aUpgraded: §f{0} {1}");
        put("must-wear-boots", "§cMusisz mieć założone buty", "§cYou must be wearing boots");
        put("need-bow", "§cMusisz posiadać łuk, aby go ulepszyć.",
                "§cYou need a bow to upgrade it.");
        put("need-sword", "§cMusisz posiadać miecz, aby go ulepszyć.",
                "§cYou need a sword to upgrade it.");

        // ---------- kity: nazwy klas ----------
        put("kit-wojownik-name", "§cWojownik", "§cWarrior");
        put("kit-lucznik-name", "§aŁucznik", "§aArcher");
        put("kit-tank-name", "§9Tank", "§9Tank");
        put("kit-magik-name", "§5Magik", "§5Mage");
        put("kit-medyk-name", "§bMedyk", "§bMedic");
        put("kit-assassin-name", "§8Asasyn", "§8Assassin");
        put("kit-inzynier-name", "§6Inżynier", "§6Engineer");

        // ---------- kity: GUI i komunikaty wyboru ----------
        put("kit-gui-title", "Wybierz kit", "Choose kit");
        put("kit-preview-title", "Podgląd kitów", "Kit preview");
        put("kit-detail-title-prefix", "Podgląd kitu: ", "Kit preview: ");
        put("kit-selected", "§aWybrano kit: {0}", "§aKit selected: {0}");
        put("kit-random", "§eNie wybrałeś kitu — otrzymujesz losowy: §f{0}",
                "§eYou didn't pick a kit — getting a random one: §f{0}");
        put("kit-no-select-in-preview", "§cW podglądzie nie wybierzesz klasy - wróć i kliknij klasę LPM.",
                "§cYou can't pick a class in the preview - go back and click the class with LMB.");
        put("kit-preview-only", "§7Tylko podgląd - wybór zablokowany",
                "§7Preview only - selection blocked");
        put("kit-lpm-hint", "§7LPM: wybierz kit", "§7LMB: select kit");
        put("kit-ppm-hint", "§7PPM: podgląd kitu", "§7RMB: preview kit");
        put("kit-back", "§e← Wróć do wyboru kitów", "§e← Back to kit selection");
        put("kit-prev", "§e◀ Poprzednia klasa", "§e◀ Previous class");
        put("kit-next", "§eNastępna klasa ▶", "§eNext class ▶");
        put("kit-close", "§cZamknij", "§cClose");
        put("kit-select-only", "§8Wybór tylko z listy kitów", "§8Selection only from the kit list");
        put("kit-select-only-lore", "§7Wróć do wyboru i kliknij klasę LPM",
                "§7Go back and click the class with LMB");
        put("kit-missing", "§cBrak kita", "§cMissing kit");
        put("kit-no-icon", "§cBrak ikony", "§cNo icon");

        // ---------- sklep: kategorie ----------
        put("shop-cat-food", "§6Jedzenie", "§6Food");
        put("shop-cat-swords", "§cBronie", "§cWeapons");
        put("shop-cat-bows", "§aStrzały", "§aArrows");
        put("shop-cat-armor", "§7Zbroje", "§7Armor");
        put("shop-cat-potions", "§dMikstury", "§dPotions");
        put("shop-cat-other", "§5Inne", "§5Other");
        put("shop-cat-effects", "§eStałe efekty", "§ePermanent effects");
        put("shop-cat-enchants", "§bZaklęcia", "§bEnchantments");
        put("shop-title", "§8Sklep - {0}", "§8Shop - {0}");
        put("shop-title-custom-arrows", "§8Sklep - Strzały", "§8Shop - Arrows");
        put("shop-title-custom-arrows-sub", "§8Strzały - Niestandardowe Strzały", "§8Arrows - Custom Arrows");
        put("shop-custom-arrows-open", "§6Niestandardowe strzały", "§6Custom Arrows");
        put("shop-click-category", "§7Kliknij, aby otworzyć kategorię",
                "§7Click to open the category");
        put("shop-mine", "Mina lądowa", "Land mine");
        put("shop-firework", "§fFajerwerk", "§fFirework");
        put("shop-title-main", "§8Sklep", "§8Shop");
        put("shop-food-bulk-title", "§8Kup hurtowo: {0}", "§8Buy in bulk: {0}");
        put("shop-food-bulk-exit-lore", "§7Kliknij, aby zamknąć ten panel",
                "§7Click to close this panel");
        put("shop-exit", "§cWyjdź", "§cExit");
        put("shop-exit-lore", "§7Kliknij, aby zamknąć panel ulepszeń",
                "§7Click to close the upgrade panel");
        put("shop-price", "§7Cena: §e{0} pkt", "§7Price: §e{0} pts");
        put("shop-amount", "§7Ilość: §f{0}", "§7Amount: §f{0}");
        put("shop-max-level", "§7Maksymalny poziom: §f{0}", "§7Max level: §f{0}");
        put("shop-click-buy", "§aKliknij, aby kupić", "§aClick to buy");
        put("shop-class-locked", "§cNie dla Twojej klasy (§e{0}§c)",
                "§cNot for your class (§e{0}§c)");
        put("enchant-not-for-item", "§cZaklęcie nie pasuje do tego przedmiotu",
                "§cThis enchantment doesn't fit this item");
        put("shop-click-upgrade", "§aKliknij, aby ulepszyć", "§aClick to upgrade");
        put("shop-next-page", "§eNastępna strona", "§eNext page");
        put("shop-prev-page", "§ePoprzednia strona", "§ePrevious page");
        put("shop-scroll-lore", "§7Kliknij, aby przewinąć", "§7Click to scroll");
        put("shop-page", "§7Strona {0}/{1}", "§7Page {0}/{1}");
        put("shop-points-name", "§aTwoje punkty", "§aYour points");
        put("shop-points-lore", "§7Masz obecnie: §e{0} pkt", "§7You currently have: §e{0} pts");
        put("restart-countdown", "§e§lRESTART §7za §f{0} §7s", "§e§lRESTART §7in §f{0} §7s");
        put("restart-now", "§e§lSerwer się restartuje!", "§e§lServer is restarting!");
        put("restart-cancelled", "§eRestart anulowany.", "§eRestart cancelled.");
        put("restart-teleport", "§eSerwer się restartuje - przenoszę do lobby...", "§eServer is restarting - moving you to the lobby...");
        put("shop-mine-lore", "§7Pułapka - wybucha, gdy podejdzie mob",
                "§7Mine - explodes when a mob gets close");
        put("shop-turret-range", "§7Zasięg: §f{0} bloków", "§7Range: §f{0} blocks");
        put("shop-turret-damage", "§7Obrażenia: §f{0}/s", "§7Damage: §f{0}/s");
        put("shop-turret-hp", "§7Ilość życia: §f{0}", "§7Health: §f{0}");
        put("shop-mage-damage", "§7Obrażenia: §f{0} na strzał (co {1} s)",
                "§7Damage: §f{0} per shot (every {1} s)");
        put("shop-turret-freeze", "§bZamraża moby", "§bFreezes mobs");
        put("shop-turret-poison", "§aTrucizna: 1 HP/s przez 8 s", "§aPoison: 1 HP/s for 8 s");
        put("shop-turret-fire", "§cPodpala moby na 8 s", "§cSets mobs on fire for 8 s");
        put("shop-turret-chain", "§eŁańcuch elektryczny (100/65/35%)",
                "§eElectric chain (100/65/35%)");
        put("turret-upgrade-title", "§8Ulepszenia wieżyczki", "§8Turret upgrades");
        put("enchant-target-title", "§8Zaklęcia - wybierz część", "§8Enchantments - choose a part");
        put("shop-enchant-hint1", "§7Kliknij część zbroi lub narzędzie,",
                "§7Click an armor piece or tool,");
        put("shop-enchant-hint2", "§7aby je ulepszyć tym zaklęciem.",
                "§7to upgrade it with this enchantment.");
        put("shop-no-item", "§cBrak założonego przedmiotu", "§cNo item equipped");
        put("shop-click-enchant", "§aKliknij, aby ulepszyć tym zaklęciem",
                "§aClick to upgrade with this enchantment");
        put("shop-slot-helmet", "Hełm", "Helmet");
        put("shop-slot-chestplate", "Napierśnik", "Chestplate");
        put("shop-slot-leggings", "Spodnie", "Leggings");
        put("shop-slot-boots", "Buty", "Boots");
        put("shop-slot-trident", "Trójząb", "Trident");
        put("shop-slot-sword", "Miecz", "Sword");
        put("shop-slot-axe", "Siekiera", "Axe");
        put("shop-slot-bow", "Łuk", "Bow");
        put("shop-slot-crossbow", "Kusza", "Crossbow");

        // ---------- sklep: przedmioty (słownik po id z config.yml) ----------
        put("shop-item-snow_golem_spawn_egg", "§fPrzywołaj Bałwana", "§fSummon Snowman");
        put("shop-item-wolf_spawn_egg", "§fPrzywołaj Wilka", "§fSummon Wolf");
        put("shop-item-vindicator_spawn_egg", "§fPrzywołaj Obrońce", "§fSummon Vindicator");
        put("shop-item-iron_golem_spawn_egg", "§fPrzywołaj Golema", "§fSummon Golem");
        put("shop-item-copper_golem_1", "§fGolem Wojownik", "§fWarrior Golem");
        put("shop-item-copper_golem_2", "§fGolem Łucznik", "§fArcher Golem");
        put("shop-item-copper_golem_3", "§fGolem Magik", "§fMage Golem");
        put("shop-item-copper_golem_4", "§fGolem Medyk", "§fMedic Golem");
        put("shop-item-copper_golem_5", "§fGolem Inżynier", "§fEngineer Golem");
        put("shop-item-copper_golem_6", "§fGolem Spawner Mobów", "§fMob Spawner Golem");
        put("shop-item-copper_golem_7", "§fGolem Losowości", "§fRandomness Golem");
        put("shop-item-warden_spawn_egg", "§fPrzywołaj Nadzorce", "§fSummon Warden");
        put("shop-item-wiezyczka", "Zwykła Wieżyczka Strzelnicza", "Basic Shooting Turret");
        put("shop-item-wiezyczka_maga", "Wieżyczka Magii", "Magic Turret");
        put("shop-item-peleryna_cienia", "§fPeleryna Cienia", "§fCloak of Shadows");
        put("shop-item-speed", "§fSzybkość", "§fSpeed");
        put("shop-item-strength", "§fSiła", "§fStrength");
        put("shop-item-regeneration", "§fRegeneracja", "§fRegeneration");
        put("shop-item-jump_boost", "§fZwiększony Skok", "§fJump Boost");
        put("shop-item-fire_resistance", "§fOgnioodporność", "§fFire Resistance");
        put("shop-item-resistance", "§fOdporność", "§fResistance");
        put("shop-item-luck", "§fSzczęście", "§fLuck");
        put("shop-item-proca", "§fProca", "§fSlingshot");
        put("shop-item-karabin", "§fKarabin snajperski", "§fSniper Rifle");

        // ---------- karabin snajperski (customowa broń) ----------
        put("weapon-sniper-name", "§fKarabin snajperski", "§fSniper Rifle");
        put("weapon-sniper-lore-zoom", "§7PPM: przybliżenie (lupa)", "§7RMB: zoom (magnifier)");
        put("weapon-sniper-lore-shoot", "§7LPM: strzał (poza przybliżeniem)", "§7LMB: shoot (outside zoom)");
        put("weapon-sniper-lore-damage", "§7Obrażenia: §c9", "§7Damage: §c9");
        put("weapon-sniper-lore-range", "§7Zasięg: §fnieograniczony", "§7Range: §funlimited");
        put("weapon-sniper-lore-cooldown", "§7Odnowienie: §f3s", "§7Cooldown: §f3s");
        put("weapon-sniper-reloading", "§7Przeładowywanie...", "§7Reloading...");
        put("weapon-sniper-no-shoot-zoomed", "§7Zdejmij palec z PPM, żeby strzelić.",
                "§7Release RMB to shoot.");
        put("weapon-sniper-only-ingame", "§cKarabinem można strzelać tylko w trakcie gry.",
                "§cYou can only shoot the rifle during a game.");

        // ---------- sklep: przedmioty bez name: w configu (automatyczne nazwy) ----------
        // jedzenie
        put("shop-item-bread", "§fChleb", "§fBread");
        put("shop-item-baked_potato", "§fPieczony ziemniak", "§fBaked Potato");
        put("shop-item-cooked_porkchop", "§fPieczony schab", "§fCooked Porkchop");
        put("shop-item-cooked_beef", "§fPieczona wołowina", "§fCooked Beef");
        put("shop-item-cooked_mutton", "§fPieczona baranina", "§fCooked Mutton");
        put("shop-item-cooked_chicken", "§fPieczony kurczak", "§fCooked Chicken");
        put("shop-item-cooked_rabbit", "§fPieczony królik", "§fCooked Rabbit");
        put("shop-item-golden_carrot", "§fZłota marchewka", "§fGolden Carrot");
        put("shop-item-golden_apple", "§fZłote jabłko", "§fGolden Apple");
        put("shop-item-enchanted_golden_apple", "§fZaklęte złote jabłko", "§fEnchanted Golden Apple");
        // miecze, siekiery i broń
        put("shop-item-wooden_sword", "§fMiedziany miecz", "§fCopper Sword");
        put("shop-item-stone_sword", "§fKamienny miecz", "§fStone Sword");
        put("shop-item-iron_sword", "§fŻelazny miecz", "§fIron Sword");
        put("shop-item-diamond_sword", "§fDiamentowy miecz", "§fDiamond Sword");
        put("shop-item-netherite_sword", "§fNetherytowy miecz", "§fNetherite Sword");
        put("shop-item-iron_axe", "§fŻelazna siekiera", "§fIron Axe");
        put("shop-item-diamond_axe", "§fDiamentowa siekiera", "§fDiamond Axe");
        put("shop-item-netherite_axe", "§fNetherytowa siekiera", "§fNetherite Axe");
        put("shop-item-trident", "§fTrójząb", "§fTrident");
        put("shop-item-shield", "§fTarcza", "§fShield");
        put("shop-item-bow", "§fŁuk", "§fBow");
        put("shop-item-crossbow", "§fKusza", "§fCrossbow");
        put("shop-item-arrow", "§fStrzała", "§fArrow");
        put("shop-item-spectral_arrow", "§fWidmowa strzała", "§fSpectral Arrow");
        // zbroje
        put("shop-item-copper_helmet", "§fMiedziany hełm", "§fCopper Helmet");
        put("shop-item-copper_chestplate", "§fMiedziany napierśnik", "§fCopper Chestplate");
        put("shop-item-copper_leggings", "§fMiedziane nagolenniki", "§fCopper Leggings");
        put("shop-item-copper_boots", "§fMiedziane buty", "§fCopper Boots");
        put("shop-item-gold_helmet", "§fZłoty hełm", "§fGolden Helmet");
        put("shop-item-gold_chestplate", "§fZłoty napierśnik", "§fGolden Chestplate");
        put("shop-item-gold_leggings", "§fZłote nagolenniki", "§fGolden Leggings");
        put("shop-item-gold_boots", "§fZłote buty", "§fGolden Boots");
        put("shop-item-chainmail_helmet", "§fKolczugowy hełm", "§fChainmail Helmet");
        put("shop-item-chainmail_chestplate", "§fKolczugowy napierśnik", "§fChainmail Chestplate");
        put("shop-item-chainmail_leggings", "§fKolczugowe nagolenniki", "§fChainmail Leggings");
        put("shop-item-chainmail_boots", "§fKolczugowe buty", "§fChainmail Boots");
        put("shop-item-iron_helmet", "§fŻelazny hełm", "§fIron Helmet");
        put("shop-item-iron_chestplate", "§fŻelazny napierśnik", "§fIron Chestplate");
        put("shop-item-iron_leggings", "§fŻelazne nagolenniki", "§fIron Leggings");
        put("shop-item-iron_boots", "§fŻelazne buty", "§fIron Boots");
        put("shop-item-diamond_helmet", "§fDiamentowy hełm", "§fDiamond Helmet");
        put("shop-item-diamond_chestplate", "§fDiamentowy napierśnik", "§fDiamond Chestplate");
        put("shop-item-diamond_leggings", "§fDiamentowe nagolenniki", "§fDiamond Leggings");
        put("shop-item-diamond_boots", "§fDiamentowe buty", "§fDiamond Boots");
        put("shop-item-netherite_helmet", "§fNetherytowy hełm", "§fNetherite Helmet");
        put("shop-item-netherite_chestplate", "§fNetherytowy napierśnik", "§fNetherite Chestplate");
        put("shop-item-netherite_leggings", "§fNetherytowe nagolenniki", "§fNetherite Leggings");
        put("shop-item-netherite_boots", "§fNetherytowe buty", "§fNetherite Boots");
        // strzały z efektami (mikstury w strzałach)
        put("shop-item-speed_arrow", "§fStrzała szybkości", "§fArrow of Speed");
        put("shop-item-strength_arrow", "§fStrzała siły", "§fArrow of Strength");
        put("shop-item-regeneration_arrow", "§fStrzała regeneracji", "§fArrow of Regeneration");
        put("shop-item-healing_arrow_1", "§fStrzała leczenia", "§fArrow of Healing");
        put("shop-item-healing_arrow_2", "§fStrzała leczenia II", "§fArrow of Healing II");
        put("shop-item-slowness_arrow", "§fStrzała spowolnienia", "§fArrow of Slowness");
        put("shop-item-weakness_arrow", "§fStrzała osłabienia", "§fArrow of Weakness");
        put("shop-item-poison_arrow", "§fStrzała trucizny", "§fArrow of Poison");
        put("shop-item-luck_arrow", "§fStrzała szczęścia", "§fArrow of Luck");
        put("shop-item-slow_falling_arrow", "§fStrzała powolnego spadania", "§fArrow of Slow Falling");
        // zaklęcia (książki) - nazwy jak w vanilla MC
        put("shop-item-sharpness_1", "§bOstrość", "§bSharpness");
        put("shop-item-smite_1", "§bPogromca nieumarłych", "§bSmite");
        put("shop-item-bane_of_arthropods_1", "§bZmora stawonogów", "§bBane of Arthropods");
        put("shop-item-fire_aspect_1", "§bZaklęty ogień", "§bFire Aspect");
        put("shop-item-sweeping_edge_1", "§bSzerokie ostrze", "§bSweeping Edge");
        put("shop-item-knockback_1", "§bOdrzut", "§bKnockback");
        put("shop-item-fortune_1", "§bSzczęście", "§bFortune");
        put("shop-item-power_1", "§bMoc", "§bPower");
        put("shop-item-punch_1", "§bUderzenie", "§bPunch");
        put("shop-item-quick_charge_1", "§bSzybkie ładowanie", "§bQuick Charge");
        put("shop-item-piercing_1", "§bPrzeszycie", "§bPiercing");
        put("shop-item-multishot_1", "§bWielostrzał", "§bMultishot");
        put("shop-item-protection_1", "§bOchrona", "§bProtection");
        put("shop-item-fire_protection_1", "§bOchrona przed ogniem", "§bFire Protection");
        put("shop-item-projectile_protection_1", "§bOchrona przed pociskami", "§bProjectile Protection");
        put("shop-item-thorns_1", "§bCiernie", "§bThorns");
        put("shop-item-feather_falling_1", "§bŁagodny upadek", "§bFeather Falling");
        // mikstury (pitne)
        put("shop-item-healing_1", "§fMikstura leczenia", "§fPotion of Healing");
        put("shop-item-healing_2", "§fMikstura leczenia II", "§fPotion of Healing II");
        put("shop-item-speed_1", "§fMikstura szybkości", "§fPotion of Swiftness");
        put("shop-item-speed_2", "§fMikstura szybkości II", "§fPotion of Swiftness II");
        put("shop-item-jump_1", "§fMikstura skoku", "§fPotion of Leaping");
        put("shop-item-jump_2", "§fMikstura skoku II", "§fPotion of Leaping II");
        put("shop-item-regeneration_1", "§fMikstura regeneracji", "§fPotion of Regeneration");
        put("shop-item-regeneration_2", "§fMikstura regeneracji II", "§fPotion of Regeneration II");
        put("shop-item-strength_1", "§fMikstura siły", "§fPotion of Strength");
        put("shop-item-strength_2", "§fMikstura siły II", "§fPotion of Strength II");
        put("shop-item-turtle_master", "§fMikstura żółwiego mistrza", "§fPotion of the Turtle Master");
        put("shop-item-slowness_long", "§fMikstura spowolnienia", "§fPotion of Slowness");
        put("shop-item-slowness_strong", "§fMikstura silnego spowolnienia", "§fPotion of Strong Slowness");
        put("shop-item-weakness_long", "§fMikstura osłabienia", "§fPotion of Weakness");
        // mikstury (miotane)
        put("shop-item-healing_splash_1", "§fMiotana mikstura leczenia", "§fSplash Potion of Healing");
        put("shop-item-healing_splash_2", "§fMiotana mikstura leczenia II", "§fSplash Potion of Healing II");
        put("shop-item-luck_splash", "§fMiotana mikstura szczęścia", "§fSplash Potion of Luck");
        put("shop-item-weakness_splash", "§fMiotana mikstura osłabienia", "§fSplash Potion of Weakness");
        // inne
        put("shop-item-fishing_rod", "§fWędka", "§fFishing Rod");
        put("shop-item-ender_pearl", "§fPerła Endu", "§fEnder Pearl");
        put("shop-item-cobweb", "§fPajęczyna", "§fCobweb");
        put("shop-item-wind_charge", "§fŁadunek wiatru", "§fWind Charge");
        put("shop-item-totem_of_undying", "§fTotem nieśmiertelności", "§fTotem of Undying");
        put("shop-item-tnt", "§fTNT", "§fTNT");

        // ---------- nazwy wieżyczek maga (GUI ulepszeń) ----------
        put("turret-ice", "§bWieża Lodu", "§bIce Tower");
        put("turret-fire", "§cWieża Ognia", "§cFire Tower");
        put("turret-poison", "§aWieża Trucizny", "§aPoison Tower");
        put("turret-electric", "§eWieża Elektryczna", "§eElectric Tower");

        // ---------- ustawienia: panel i elementy wspólne ----------
        put("set-title", "§8Ustawienia", "§8Settings");
        put("set-particles", "§6Ustawienia cząsteczek", "§6Particle Settings");
        put("set-sounds", "§6Dźwięki", "§6Sounds");
        put("set-notifications", "§6Powiadomienia", "§6Notifications");
        put("set-visibility", "§6Widoczności", "§6Visibility");
        put("set-weather", "§6Pogoda", "§6Weather");
        put("set-kit-slots", "§6Ustawienia klas", "§6Kit Settings");
        put("set-language", "§6Język", "§6Language");
        put("set-compass", "§6Ustawienia kupowania/sklepu", "§6Shop Settings");
        put("set-click-to-open", "§7Kliknij, aby otworzyć", "§7Click to open");
        put("set-click-to-change", "§7Kliknij, aby zmienić", "§7Click to change");
        put("set-custom", "§dNiestandardowe", "§dCustom");
        put("set-on", "§aWłącz", "§aOn");
        put("set-off", "§cWyłącz", "§cOff");
        put("set-vis-on", "§aWłączone", "§aOn");
        put("set-vis-off", "§cWyłączone", "§cOff");
        put("set-settings-icon", "§6Ustawienia", "§6Settings");
        put("set-settings-icon-lore", "§7Kliknij, aby otworzyć ustawienia", "§7Click to open settings");
        put("set-exit-shop", "§cWyjdź ze sklepu", "§cExit the shop");
        put("set-exit-shop-lore", "§7Kliknij, aby zamknąć sklep", "§7Click to close the shop");
        put("set-back", "§cPowrót", "§cBack");
        put("set-back-shop-lore", "§7Kliknij, aby wrócić do menu sklepu", "§7Click to go back to the shop menu");
        put("set-back-settings-lore", "§7Kliknij, aby wrócić do ustawień", "§7Click to go back to settings");
        put("set-exit", "§cWyjdź z ustawień", "§cExit settings");
        put("set-exit-lore", "§7Kliknij, aby zamknąć ustawienia", "§7Click to close settings");
        put("set-prev-category", "§7Poprzednia kategoria", "§7Previous category");
        put("set-prev-category-lore", "§7Kliknij, aby przejść do poprzedniej kategorii", "§7Click to go to the previous category");
        put("set-next-category", "§7Następna kategoria", "§7Next category");
        put("set-next-category-lore", "§7Kliknij, aby przejść do następnej kategorii", "§7Click to go to the next category");

        // ---------- osiągnięcia ----------
        put("achievements-title", "§6Osiągnięcia", "§6Achievements");
        put("achievements-icon-lore", "§7Lista osiągnięć Villager Defense", "§7Villager Defense achievement list");
        put("achievements-soon", "§eOsiągnięcia już wkrótce!", "§eAchievements coming soon!");
        put("ach-title", "§8Osiągnięcia", "§8Achievements");
        put("ach-title-category", "§8Osiągnięcia - {0}", "§8Achievements - {0}");
        put("ach-cat-walka", "§6Walka", "§6Combat");
        put("ach-cat-obrona", "§6Obrona", "§6Defense");
        put("ach-cat-przetrwanie", "§6Przetrwanie", "§6Survival");
        put("ach-cat-lupy", "§6Łupy", "§6Loot");
        put("ach-cat-ekonomia", "§6Ekonomia", "§6Economy");
        put("ach-cat-klasy", "§6Klasy", "§6Classes");
        put("ach-cat-eksploracja", "§6Eksploracja", "§6Exploration");
        put("ach-cat-spoleczne", "§6Społeczne", "§6Social");
        put("ach-cat-ukryte", "§6Ukryte", "§6Hidden");
        put("ach-cat-click-lore", "§7Kliknij, aby zobaczyć osiągnięcia", "§7Click to view achievements");
        put("ach-scroll-up", "§7Przewiń do góry", "§7Scroll up");
        put("ach-scroll-up-lore", "§eLPM §7: Przewiń do góry", "§eLMB §7: Scroll up");
        put("ach-scroll-up-lore2", "§eShift + LPM §7: na początek", "§eShift + LMB §7: to the beginning");
        put("ach-scroll-down", "§7Przewiń w dół", "§7Scroll down");
        put("ach-scroll-down-lore", "§eLPM §7: Przewiń w dół", "§eLMB §7: Scroll down");
        put("ach-scroll-down-lore2", "§eShift + LPM §7: na koniec", "§eShift + LMB §7: to the end");
        put("ach-counter-lore", "§7Odblokowane: §e{0}§7/§e{1} §7({2}%)", "§7Unlocked: §e{0}§7/§e{1} §7({2}%)");
        put("ach-progress-lore", "§7Postęp: §e{0}§7/§e{1} §7({2}%)", "§7Progress: §e{0}§7/§e{1} §7({2}%)");
        put("ach-progress-percent", "§7Postęp: §e{0}%", "§7Progress: §e{0}%");
        put("ach-unlocked-lore", "§aUkończone!", "§aCompleted!");
        put("ach-hidden-name", "§8???", "§8???");
        put("ach-hidden-desc", "§7Tajne osiągnięcie — odblokuj, aby je odkryć", "§7Hidden achievement — unlock to reveal it");

        // nazwy osiągnięć (bez koloru - kolor nadaje kod w GUI: zielone = ukończone)
        put("ach-name-kills-1", "Pierwsze zabójstwo", "First Kill");
        put("ach-name-kills-2", "Łowca", "Hunter");
        put("ach-name-kills-3", "Rzeźnik", "Butcher");
        put("ach-name-kills-4", "Pogromca", "Slayer");
        put("ach-name-defend-1", "Obrońca", "Protector");
        put("ach-name-defend-2", "Budowniczy", "Builder");
        put("ach-name-defend-3", "Snajper", "Sniper");
        put("ach-name-defend-4", "Ani jednego", "None Left Behind");
        put("ach-name-wave-1", "Pierwsza fala", "First Wave");
        put("ach-name-wave-2", "Weteran", "Veteran");
        put("ach-name-wave-3", "Niezłomny", "Unbreakable");
        put("ach-name-wave-4", "Tytan", "Titan");
        put("ach-name-eco-1", "Pierwszy grosz", "First Coin");
        put("ach-name-eco-2", "Skąpiec", "Miser");
        put("ach-name-eco-3", "Krezus", "Croesus");
        put("ach-name-eco-4", "Milioner areny", "Arena Millionaire");
        put("ach-name-class-1", "Rekrut", "Recruit");
        put("ach-name-class-2", "Żołnierz", "Soldier");
        put("ach-name-class-3", "Weteran klas", "Class Veteran");
        put("ach-name-class-4", "Mistrz klasy", "Class Master");
        put("ach-name-loot-1", "Pierwszy zrzut", "First Drop");
        put("ach-name-loot-2", "Łowca łupów", "Loot Hunter");
        put("ach-name-loot-3", "Szczęściarz", "Lucky One");
        put("ach-name-loot-4", "Kolekcjoner", "Collector");
        put("ach-name-explore-1", "Pierwsze kroki", "First Steps");
        put("ach-name-explore-2", "Odkrywca", "Explorer");
        put("ach-name-explore-3", "Wędrowiec", "Wanderer");
        put("ach-name-explore-4", "Znawca map", "Map Expert");
        put("ach-name-social-1", "Współpraca", "Teamwork");
        put("ach-name-social-2", "Przyjaciel", "Friend");
        put("ach-name-social-3", "Lider", "Leader");
        put("ach-name-social-4", "Zgrana ekipa", "Squad");
        put("ach-name-hidden-1", "?", "?");
        put("ach-name-hidden-2", "??", "??");
        put("ach-name-hidden-3", "???", "???");
        put("ach-name-hidden-4", "????", "????");

        // opisy osiągnięć
        put("ach-desc-kills-1", "§7Zabij pierwszego moba", "§7Kill your first mob");
        put("ach-desc-kills-2", "§7Zabij łącznie 100 mobów", "§7Kill 100 mobs in total");
        put("ach-desc-kills-3", "§7Zabij łącznie 500 mobów", "§7Kill 500 mobs in total");
        put("ach-desc-kills-4", "§7Zabij łącznie 1500 mobów", "§7Kill 1,500 mobs in total");
        put("ach-desc-defend-1", "§7Wygraj grę bez strat wieśniaków", "§7Win a game without losing villagers");
        put("ach-desc-defend-2", "§7Postaw łącznie 10 wieżyczek", "§7Place 10 turrets in total");
        put("ach-desc-defend-3", "§7Zabij 50 mobów wieżyczkami", "§7Kill 50 mobs with turrets");
        put("ach-desc-defend-4", "§7Przetrwaj 25 fal bez strat wieśniaków", "§7Survive 25 waves without losing villagers");
        put("ach-desc-wave-1", "§7Przetrwaj 5 fal", "§7Survive 5 waves");
        put("ach-desc-wave-2", "§7Przetrwaj 25 fal", "§7Survive 25 waves");
        put("ach-desc-wave-3", "§7Przetrwaj 50 fal", "§7Survive 50 waves");
        put("ach-desc-wave-4", "§7Przetrwaj 100 fal", "§7Survive 100 waves");
        put("ach-desc-eco-1", "§7Zdobądź łącznie 100 punktów", "§7Earn 100 points in total");
        put("ach-desc-eco-2", "§7Zdobądź łącznie 1000 punktów", "§7Earn 1,000 points in total");
        put("ach-desc-eco-3", "§7Zdobądź łącznie 5000 punktów", "§7Earn 5,000 points in total");
        put("ach-desc-eco-4", "§7Zdobądź łącznie 10 000 punktów", "§7Earn 10,000 points in total");
        put("ach-desc-class-1", "§7Zagraj pierwszą grę", "§7Play your first game");
        put("ach-desc-class-2", "§7Zagraj łącznie 25 gier", "§7Play 25 games in total");
        put("ach-desc-class-3", "§7Zagraj łącznie 100 gier", "§7Play 100 games in total");
        put("ach-desc-class-4", "§7Wygraj 10 gier jednym kitem", "§7Win 10 games with a single kit");
        put("ach-desc-loot-1", "§7Otwórz pierwszy zrzut zaopatrzenia", "§7Open your first supply drop");
        put("ach-desc-loot-2", "§7Otwórz łącznie 25 zrzutów zaopatrzenia", "§7Open 25 supply drops in total");
        put("ach-desc-loot-3", "§7Otwórz łącznie 100 zrzutów zaopatrzenia", "§7Open 100 supply drops in total");
        put("ach-desc-loot-4", "§7Kup 10 przedmiotów w jednej grze", "§7Buy 10 items in a single game");
        put("ach-desc-explore-1", "§7Zagraj pierwszą grę", "§7Play your first game");
        put("ach-desc-explore-2", "§7Odwiedź 3 różne areny", "§7Visit 3 different arenas");
        put("ach-desc-explore-3", "§7Przebiegnij łącznie 10 km", "§7Run a total of 10 km");
        put("ach-desc-explore-4", "§7Odwiedź wszystkie areny", "§7Visit every arena");
        put("ach-desc-social-1", "§7Wygraj grę z 2+ graczami", "§7Win a game with 2+ players");
        put("ach-desc-social-2", "§7Zagraj z 10 różnymi graczami", "§7Play with 10 different players");
        put("ach-desc-social-3", "§7Stwórz drużynę z 4+ graczy", "§7Create a party with 4+ players");
        put("ach-desc-social-4", "§7Wygraj 10 gier w drużynie", "§7Win 10 games in a party");
        put("ach-desc-hidden-1", "§7Tajne", "§7Hidden");
        put("ach-desc-hidden-2", "§7Tajne", "§7Hidden");
        put("ach-desc-hidden-3", "§7Tajne", "§7Hidden");
        put("ach-desc-hidden-4", "§7Tajne", "§7Hidden");

        // poziomy (2 dodatkowe rzędy w kategoriach) - {0}=poziom, {1}=wymaganie
        put("ach-tier-walka", "Pogromca poziom {0}", "Slayer level {0}");
        put("ach-tier-walka-desc", "§7Zabij łącznie §e{1}§7 mobów", "§7Kill §e{1}§7 mobs in total");
        put("ach-tier-obrona", "Twierdza poziom {0}", "Fortress level {0}");
        put("ach-tier-obrona-desc", "", "");
        put("ach-tier-przetrwanie", "Niezłomny poziom {0}", "Unbreakable level {0}");
        put("ach-tier-przetrwanie-desc", "§7Przetrwaj §e{1}§7 fal", "§7Survive §e{1}§7 waves");
        put("ach-tier-ekonomia", "Krezus poziom {0}", "Croesus level {0}");
        put("ach-tier-ekonomia-desc", "§7Zdobądź łącznie §e{1}§7 punktów", "§7Earn §e{1}§7 points in total");
        put("ach-tier-klasy", "Weteran poziom {0}", "Veteran level {0}");
        put("ach-tier-klasy-desc", "§7Zagraj łącznie §e{1}§7 gier", "§7Play §e{1}§7 games in total");
        put("ach-tier-eksploracja", "Odkrywca poziom {0}", "Explorer level {0}");
        put("ach-tier-eksploracja-desc", "", "");
        put("ach-tier-ukryte", "???", "???");
        put("ach-tier-ukryte-desc", "§7Tajne", "§7Hidden");

        put("ach-exit", "§cWyjdź", "§cExit");
        put("ach-back", "§cWróć", "§cBack");
        put("ach-back-shop", "§cPowrót", "§cBack");
        put("ach-close", "§cZamknij", "§cClose");

        // ---------- ustawienia: dźwięki ----------
        put("set-sounds-title", "§8Dźwięki", "§8Sounds");
        put("set-sound-gui", "Dźwięki gui", "GUI sounds");
        put("set-sound-arena-mobs", "Dźwięki mobów z areny", "Arena mob sounds");
        put("set-sound-player-mobs", "Dźwięki mobów po stronie gracza", "Player-side mob sounds");
        put("set-sound-turrets", "Dźwięki wieżyczek", "Turret sounds");
        put("set-sound-custom-items", "Dźwięki niestandardowych przedmiotów", "Custom item sounds");
        put("set-sound-classes", "Dźwięki klas", "Class sounds");
        put("set-sound-supply", "Dźwięki zrzutu zaopatrzenia", "Supply drop sounds");
        put("set-sound-supply-lore-1", "§7Kliknij, aby otworzyć", "§7Click to open");
        put("set-sound-supply-lore-2", "§7ustawienia głośności faz zrzutu", "§7volume settings for the drop phases");

        // ---------- ustawienia: cząsteczki ----------
        put("set-particles-title", "§8Cząsteczki", "§8Particles");
        put("set-particle-general", "Ogólne ustawienie cząsteczek", "General particle setting");
        put("set-particle-turrets", "Cząsteczki wieżyczek", "Turret particles");
        put("set-particle-classes", "Cząsteczki klas", "Class particles");
        put("set-particle-players", "Cząsteczki przy graczach", "Player particles");
        put("set-particle-custom-abilities", "Cząsteczki niestandardowych umiejętności", "Custom ability particles");
        put("set-particle-fireworks", "Cząsteczki fajerwerków", "Firework particles");
        put("set-particle-supply", "Cząsteczki zrzutu zaopatrzenia", "Supply drop particles");

        // ---------- ustawienia: dźwięki zrzutu zaopatrzenia ----------
        put("set-supply-sounds-title", "§8Dźwięki - Zrzut zaopatrzenia", "§8Sounds - Supply Drop");
        put("set-supply-landing", "Lądowanie zrzutu", "Drop landing");
        put("set-supply-landed", "Wylądowanie zrzutu", "Drop landed");
        put("set-supply-despawn", "Znikanie zrzutu", "Drop despawning");
        put("set-supply-slotmachine", "Automat łupu", "Slot machine");

        // ---------- ustawienia: powiadomienia ----------
        put("set-notifications-title", "§8Powiadomienia", "§8Notifications");
        put("set-notif-chat", "Powiadomienia na czacie", "Chat notifications");
        put("set-notif-actionbar", "Powiadomienia na action barze", "Action bar notifications");
        put("set-notif-screen", "Powiadomienia na ekranie", "Screen notifications");

        // ---------- ustawienia: widoczności ----------
        put("set-visibility-title", "§8Widoczności", "§8Visibility");
        put("set-vis-particles", "Widoczność cząsteczek", "Particle visibility");
        put("set-vis-actionbar", "Widoczność action bara", "Action bar visibility");
        put("set-vis-bossbar", "Widoczność boss bara", "Boss bar visibility");
        put("set-vis-mob-hp", "Widoczność punktów zdrowia mobów", "Mob health bar visibility");
        put("set-vis-turret-hp", "Widoczność punktów zdrowia wieżyczek", "Turret health bar visibility");
        put("set-vis-villager-hp", "Widoczność punktów zdrowia osadników", "Villager health bar visibility");

        // ---------- ustawienia: pogoda ----------
        put("set-weather-title", "§8Pogoda", "§8Weather");
        put("set-time-clock", "§6Pora dnia", "§6Time of Day");
        put("set-weather-clock", "§6Zmiana pogody", "§6Weather Change");
        put("set-time-hint", "§8LPM: następna pora dnia §8· §8PPM: poprzednia",
                "§8LMB: next time of day §8· §8RMB: previous");
        put("set-weather-hint", "§8LPM: następna pogoda §8· §8PPM: poprzednia",
                "§8LMB: next weather §8· §8RMB: previous");
        put("set-current", "§7(obecnie)", "§7(current)");
        put("set-time-changed", "§aPora dnia: §e{0}", "§aTime of day: §e{0}");
        put("set-weather-changed", "§aPogoda: §e{0}", "§aWeather: §e{0}");
        put("set-weather-ranek", "Ranek", "Morning");
        put("set-weather-poludnie", "Południe", "Noon");
        put("set-weather-wieczor", "Wieczór", "Evening");
        put("set-weather-noc", "Noc", "Night");
        put("set-weather-slonecznie", "Słonecznie", "Sunny");
        put("set-weather-deszcz", "Deszcz", "Rain");
        put("set-weather-burza", "Burza", "Thunderstorm");
        put("set-weather-normalna", "Normalna", "Normal");

        // ---------- ustawienia: język ----------
        put("set-language-title", "§8Język", "§8Language");
        put("set-lang-polish", "Język polski", "Polish");
        put("set-lang-english", "Język angielski", "English");
        put("set-lang-selected", "§a✓ Wybrany język", "§a✓ Selected language");
        put("set-lang-click", "§7Kliknij, aby wybrać", "§7Click to select");
        put("set-language-changed", "§aWybrano język: §e{0}", "§aLanguage set to: §e{0}");

        // ---------- ustawienia: sloty klas ----------
        put("set-kit-slots-title", "§8Ustawienia klas", "§8Kit Settings");
        put("set-kit-slots-sign", "§eWybierz klasę, aby ustawić własne ustawienie przedmiotów",
                "§ePick a class to set your own item layout");
        put("set-kit-slots-sign-lore-1", "§7Ustaw przedmioty na wybranych przez ciebie miejscach,",
                "§7Place items in the positions you choose,");
        put("set-kit-slots-sign-lore-2", "§7aby zapisać pojawianie się przedmiotów na arenie",
                "§7to save where items appear on the arena");
        put("set-kit-slots-sign-lore-3", "§7dla poszczególnych klas", "§7for each kit class");
        put("set-kit-edit-hint", "§ePrzestawianie przedmiotów", "§eMoving items");
        put("set-kit-edit-hint-lore-1", "§7Weź przedmiot na myszkę (LPM/PPM)", "§7Pick up an item (LMB/RMB)");
        put("set-kit-edit-hint-lore-2", "§7i postaw go w innym miejscu paska.", "§7and place it elsewhere on the bar.");
        put("set-kit-edit-hint-lore-3", "§7Zapisz = zapamiętaj układ,", "§7Save = remember the layout,");
        put("set-kit-edit-hint-lore-4", "§7Reset = domyślny układ klasy", "§7Reset = default kit layout");
        put("set-kit-save", "§aZapisz", "§aSave");
        put("set-kit-reset", "§cReset", "§cReset");
        put("set-kit-slots-saved", "§aZapisano układ przedmiotów dla klasy §e{0}",
                "§aSaved item layout for kit §e{0}");
        put("set-kit-slots-reset", "§aPrzywrócono domyślny układ dla klasy §e{0}",
                "§aRestored default layout for kit §e{0}");
        put("set-kit-missing-icon", "§cBrak kitu", "§cMissing kit");

        // ---------- BossBar: fala X/Y + stan areny ----------
        put("bossbar-mobs-left", "§6Fala: §e{0}/{1} §7— §cPozostałe moby do pokonania: §e{2}",
                "§6Wave: §e{0}/{1} §7— §cMobs left to defeat: §e{2}");
        put("bossbar-break", "§6Fala: §e{0}/{1} §7— §aNastępna fala za §e{2} §a{3}",
                "§6Wave: §e{0}/{1} §7— §aNext wave in §e{2} §a{3}");
        put("bossbar-waiting", "§6Fala: §e{0}/{1} §7— §aCzekanie na graczy: §e{2}§7/§e{3}",
                "§6Wave: §e{0}/{1} §7— §aWaiting for players: §e{2}§7/§e{3}");
        put("bossbar-starting", "§6Fala: §e{0}/{1} §7— §aStart za §e{2} §a{3}",
                "§6Wave: §e{0}/{1} §7— §aStarting in §e{2} §a{3}");

        // ---------- poziomy trudności aren ----------
        put("trudnosc-latwy", "Łatwy", "Easy");
        put("trudnosc-normalny", "Normalny", "Normal");
        put("trudnosc-trudny", "Trudny", "Hard");
    }

    private Lang() {
    }

    private static void put(String key, String pl, String en) {
        PL.put(key, pl);
        EN.put(key, en);
    }

    /** Wywoływane przy starcie pluginu (razem z AntiSpam.init). */
    public static void init(VillagerDefensePlugin plugin) {
        Lang.plugin = plugin;
    }

    /** Czy gracz ma wybrany język angielski (panel Ustawienia -> Język)? */
    public static boolean isEnglish(Player player) {
        if (player == null || plugin == null) return false;
        var data = plugin.getPlayerDataManager().get(player.getUniqueId(), player.getName());
        return "angielski".equals(data.getSetting("language", "polski"));
    }

    /** Komunikat po kluczu w języku gracza, z podstawieniem {0}, {1}, ... */
    public static String get(Player player, String key, Object... args) {
        return format((isEnglish(player) ? EN : PL).getOrDefault(key, PL.getOrDefault(key, key)), args);
    }

    /** Komunikat po kluczu po polsku (gdy nie ma gracza), z podstawieniem {0}, {1}, ... */
    public static String getPl(String key, Object... args) {
        return format(PL.getOrDefault(key, key), args);
    }

    /** Komunikat po kluczu w języku gracza; gdy klucza nie ma w słowniku - zwraca
     *  fallback (np. nazwę przedmiotu z config.yml). */
    public static String getOr(Player player, String key, String fallback, Object... args) {
        String v = (isEnglish(player) ? EN : PL).get(key);
        if (v == null) return fallback;
        return format(v, args);
    }

    /** Forma słowa „sekunda" dla liczby w języku gracza: 1 sekundę, 2-4 sekundy,
     *  5+ sekund (angielski: 1 second / N seconds). */
    public static String secondsWord(Player player, int n) {
        if (isEnglish(player)) return n == 1 ? "second" : "seconds";
        if (n == 1) return "sekundę";
        int d = n % 10;
        int dd = n % 100;
        if (d >= 2 && d <= 4 && !(dd >= 12 && dd <= 14)) return "sekundy";
        return "sekund";
    }

    /** Podstawia {0}, {1}, ... w tekście. */
    public static String format(String message, Object... args) {
        if (message == null || args == null || args.length == 0) return message;
        String out = message;
        for (int i = 0; i < args.length; i++) {
            out = out.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return out;
    }

    /** Wysyła komunikat graczowi (AntiSpam) w jego języku. */
    public static void send(Player player, String key, Object... args) {
        AntiSpam.send(player, get(player, key, args));
    }

    /** Wysyła komunikat na action bar gracza w jego języku. */
    public static void sendActionBar(Player player, String key, Object... args) {
        AntiSpam.sendActionBar(player, get(player, key, args));
    }

    /** Wysyła TYTUŁ na ekran gracza w jego języku (kanał „Powiadomienia na
     *  ekranie" - pomijany, gdy gracz go wyłączy). subtitleKey może być null. */
    public static void sendTitle(Player player, String titleKey, String subtitleKey, Object... args) {
        AntiSpam.sendTitle(player,
                get(player, titleKey, args),
                subtitleKey == null ? "" : get(player, subtitleKey, args));
    }
}
