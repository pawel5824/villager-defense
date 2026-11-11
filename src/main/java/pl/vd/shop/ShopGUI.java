package pl.vd.shop;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowman;
import org.bukkit.entity.Wolf;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import pl.vd.VillagerDefensePlugin;
import pl.vd.arena.Arena;
import pl.vd.kits.Kit;
import pl.vd.stand.ArmorStandTuner;
import pl.vd.stand.StandPreset;
import pl.vd.type.TurretElement;
import pl.vd.type.WeaponType;
import pl.vd.util.ItemBuilder;
import pl.vd.util.Lang;
import pl.vd.util.WeatherUtil;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class ShopGUI {

    // Bukkit pozwala custom inventory maksymalnie na 54 sloty (6 rzędów po 9) -
    // każda kategoria, w tym zbroja, używa tego samego rozmiaru
    private static final int SIZE = 54;

    // Główne menu - wyjście na środku dołu (slot 49), szmaragd w prawym rogu (slot 53),
    // ustawienia w lewym dolnym rogu (slot 45)
    private static final int STAR_SLOT    = 49;
    private static final int POINTS_SLOT  = 53;
    private static final int SETTINGS_SLOT = 45;
    // ikona osiągnięć tuż obok Ustawień (lewy dolny róg) - GUI na razie w zapowiedzi
    public static final int ACHIEVEMENTS_SLOT = 46;

    // Panel ustawień: 6x9 = 54 sloty, powrót (nether star) na środku dołu (slot 49),
    // BEZ emeralda punktów w prawym dolnym rogu.
    private static final int SETTINGS_SIZE = 54;
    // Sloty ikon opcji w panelu ustawień
    public static final int SETTINGS_PARTICLES_SLOT = 12;
    public static final int SETTINGS_SOUNDS_SLOT    = 14;
    public static final int SETTINGS_NOTIF_SLOT     = 16;
    public static final int SETTINGS_VISIBILITY_SLOT = 28; // redstone_lamp „Widoczności"
    public static final int SETTINGS_FLOOR_SLOT     = 10; // ikona „Pogoda" (zegar), w grze slot 11
    public static final int SETTINGS_KIT_SLOTS_SLOT = 32;
    public static final int SETTINGS_LANG_SLOT      = 34;
    public static final int SETTINGS_COMPASS_SLOT   = 30; // kompas (w grze slot 31) otwiera panel kompasu

    // Panel dźwięków (podmenu ustawień): 6x9 = 54 sloty, powrót (nether star)
    // na środku dołu do USTAWIEŃ, bez emeralda punktów.
    private static final int SOUND_SETTINGS_SIZE = 54;
    // Przełączniki dźwięków: slot -> (klucz ustawienia w players.yml)
    public static final int    SOUND_MOBS_SLOT       = 10;
    public static final String SOUND_MOBS_KEY        = "sound-mobs";
    public static final int    SOUND_TURRETS_SLOT    = 12;
    public static final String SOUND_TURRETS_KEY     = "sound-turrets";
    public static final int    SOUND_EXPLOSIONS_SLOT = 14;
    public static final String SOUND_EXPLOSIONS_KEY  = "sound-explosions";
    public static final int    SOUND_ALLIES_SLOT    = 28;
    public static final String SOUND_ALLIES_KEY     = "sound-allies";
    public static final int    SOUND_ABILITIES_SLOT = 30;
    public static final String SOUND_ABILITIES_KEY  = "sound-abilities";
    // „Dźwięki klas" (16) ma działający suwak na 25 (16 + 9, jeden rząd niżej);
    // „Dźwięki zrzutu zaopatrzenia" (43) otwiera podmenu.
    public static final int    SOUND_CLASSES_SLOT      = 16;
    public static final String SOUND_CLASSES_KEY       = "sound-classes";
    public static final int    SOUND_SUPPLY_LABEL_SLOT = 43;

    // Wskaźniki stanu pod note blockami (jeden rząd niżej, slot + 9): beton
    // pokazuje GŁOŚNOŚĆ kategorii w % - czerwony 0%, pomarańczowy 25%, żółty
    // 50%, jasnozielony 75%, zielony 100%. Kliknięcie cykluje 0->25->...->100->0.
    public static final int    SOUND_MOBS_INDICATOR_SLOT       = 19;
    public static final int    SOUND_TURRETS_INDICATOR_SLOT    = 21;
    public static final int    SOUND_EXPLOSIONS_INDICATOR_SLOT = 23;
    public static final int    SOUND_CLASSES_INDICATOR_SLOT    = 25;
    public static final int    SOUND_ALLIES_INDICATOR_SLOT     = 37;
    public static final int    SOUND_ABILITIES_INDICATOR_SLOT  = 39;

    // Progi głośności dźwięków (klikanie betonu przechodzi przez nie w kółko).
    public static final int[] SOUND_VOLUME_STEPS = {0, 25, 50, 75, 100};

    /** Kolejny próg głośności po kliknięciu (0 -> 25 -> 50 -> 75 -> 100 -> 0). */
    public static int nextSoundVolume(int current) {
        return current >= 100 ? 0 : ((current / 25) + 1) * 25;
    }

    /** Poprzedni próg głośności po kliknięciu PPM (100 -> 75 -> 50 -> 25 -> 0 -> 100). */
    public static int previousSoundVolume(int current) {
        return current <= 0 ? 100 : ((current / 25) - 1) * 25;
    }

    // Panel cząsteczek (podmenu ustawień): 6x9 = 54 sloty, powrót (nether star)
    // na środku dołu do USTAWIEŃ, bez emeralda punktów. Firework star = kategoria
    // cząsteczek, betonowy wskaźnik pod spodem (slot + 9) pokazuje ILOŚĆ w % -
    // czerwony 0%, pomarańczowy 25%, żółty 50%, jasnozielony 75%, zielony 100%.
    // Kliknięcie betonu zmienia ilość o jeden próg (100->75->...->0->100);
    // globalny suwak „Ogólne" (slot 19) ma między 100% a 0% opcję NIESTANDARDOWE.
    private static final int PARTICLE_SETTINGS_SIZE = 54;
    public static final int    PARTICLE_GENERAL_SLOT       = 10;
    public static final String PARTICLE_GENERAL_KEY        = "particles-general";
    public static final int    PARTICLE_TURRETS_SLOT       = 12;
    public static final String PARTICLE_TURRETS_KEY        = "particles-turrets";
    public static final int    PARTICLE_CLASSES_SLOT       = 14;
    public static final String PARTICLE_CLASSES_KEY        = "particles-classes";
    public static final int    PARTICLE_PLAYERS_SLOT       = 16;
    public static final String PARTICLE_PLAYERS_KEY        = "particles-players";
    public static final int    PARTICLE_CUSTOM_SLOT        = 30;
    public static final String PARTICLE_CUSTOM_KEY         = "particles-custom";
    public static final int    PARTICLE_FIREWORKS_SLOT     = 32;
    public static final String PARTICLE_FIREWORKS_KEY      = "particles-fireworks";
    public static final int    PARTICLE_SUPPLY_SLOT        = 34;
    public static final String PARTICLE_SUPPLY_KEY         = "particles-supply";

    // Wskaźniki ilości cząsteczek (jeden rząd niżej, slot + 9)
    public static final int    PARTICLE_GENERAL_INDICATOR_SLOT   = 19;
    public static final int    PARTICLE_TURRETS_INDICATOR_SLOT   = 21;
    public static final int    PARTICLE_CLASSES_INDICATOR_SLOT   = 23;
    public static final int    PARTICLE_PLAYERS_INDICATOR_SLOT   = 25;
    public static final int    PARTICLE_CUSTOM_INDICATOR_SLOT    = 39;
    public static final int    PARTICLE_FIREWORKS_INDICATOR_SLOT = 41;
    public static final int    PARTICLE_SUPPLY_INDICATOR_SLOT    = 43;

    // Progi ilości cząsteczek (klikanie betonu zmniejsza o jeden próg)
    public static final int[] PARTICLE_LEVELS = {0, 25, 50, 75, 100};

    /** Wartość globalnego suwaka „Ogólne" (slot 19) oznaczająca tryb
     *  NIESTANDARDOWE: każda kategoria ma własną wartość (różowy beton).
     *  Jedyna kategoria z tą opcją - pozostałe suwaki mają tylko 0/25/50/75/100. */
    public static final int PARTICLE_GENERAL_CUSTOM = -1;

    // Wszystkie klucze kategorii cząsteczek (do globalnego suwaka „Ogólne")
    public static final List<String> PARTICLE_KEYS = List.of(
            PARTICLE_GENERAL_KEY, PARTICLE_TURRETS_KEY, PARTICLE_CLASSES_KEY,
            PARTICLE_PLAYERS_KEY, PARTICLE_CUSTOM_KEY, PARTICLE_FIREWORKS_KEY,
            PARTICLE_SUPPLY_KEY);

    /** Poprzedni (mniejszy) próg ilości cząsteczek po kliknięciu PPM (100 -> 75 -> 50 -> 25 -> 0 -> 100). */
    public static int previousParticleLevel(int current) {
        int idx = Math.min(PARTICLE_LEVELS.length - 1, Math.max(0, current / 25));
        return PARTICLE_LEVELS[(idx - 1 + PARTICLE_LEVELS.length) % PARTICLE_LEVELS.length];
    }

    /** Następny (większy) próg ilości cząsteczek po kliknięciu LPM (0 -> 25 -> 50 -> 75 -> 100 -> 0). */
    public static int nextParticleLevel(int current) {
        int idx = Math.min(PARTICLE_LEVELS.length - 1, Math.max(0, current / 25));
        return PARTICLE_LEVELS[(idx + 1) % PARTICLE_LEVELS.length];
    }

    /** Następny stan globalnego suwaka „Ogólne" (slot 19) po kliknięciu LPM:
     *  0 -> 25 -> 50 -> 75 -> 100 -> NIESTANDARDOWE -> 0. Opcja NIESTANDARDOWE
     *  siedzi między 100% a 0%. custom = suwak jest teraz w trybie NIESTANDARDOWE. */
    public static int nextGeneralParticleState(int current, boolean custom) {
        if (custom) return 0;
        if (current >= 100) return PARTICLE_GENERAL_CUSTOM;
        return ((current / 25) + 1) * 25;
    }

    /** Poprzedni stan globalnego suwaka „Ogólne" (slot 19) po kliknięciu PPM:
     *  0 -> NIESTANDARDOWE -> 100 -> 75 -> 50 -> 25 -> 0. custom = suwak jest
     *  teraz w trybie NIESTANDARDOWE. */
    public static int previousGeneralParticleState(int current, boolean custom) {
        if (custom) return 100;
        if (current <= 0) return PARTICLE_GENERAL_CUSTOM;
        return ((current / 25) - 1) * 25;
    }

    /** Klucz ustawienia kategorii cząsteczek dla slota wskaźnika (albo null). */
    public String particleKeyForIndicatorSlot(int slot) {
        return switch (slot) {
            case PARTICLE_GENERAL_INDICATOR_SLOT   -> PARTICLE_GENERAL_KEY;
            case PARTICLE_TURRETS_INDICATOR_SLOT   -> PARTICLE_TURRETS_KEY;
            case PARTICLE_CLASSES_INDICATOR_SLOT   -> PARTICLE_CLASSES_KEY;
            case PARTICLE_PLAYERS_INDICATOR_SLOT   -> PARTICLE_PLAYERS_KEY;
            case PARTICLE_CUSTOM_INDICATOR_SLOT    -> PARTICLE_CUSTOM_KEY;
            case PARTICLE_FIREWORKS_INDICATOR_SLOT -> PARTICLE_FIREWORKS_KEY;
            case PARTICLE_SUPPLY_INDICATOR_SLOT    -> PARTICLE_SUPPLY_KEY;
            default -> null;
        };
    }

    // Podmenu dźwięków zrzutu zaopatrzenia: 4x9 = 36 slotów, powrót (nether star)
    // na środku dołu (slot 31) do panelu DŹWIĘKÓW, bez emeralda punktów.
    private static final int SUPPLY_SOUNDS_SIZE = 36;
    // Miedziane skrzynie faz zrzutu (w grze sloty 11/13/15/17) i betonowe wskaźniki
    // głośności pod nimi (jeden rząd niżej, slot + 9).
    public static final int SUPPLY_LANDING_SLOT = 10; // „Lądowanie zrzutu"
    public static final int SUPPLY_LANDED_SLOT  = 12; // „Wylądowanie zrzutu"
    public static final int SUPPLY_DESPAWN_SLOT = 14; // „Znikanie zrzutu"
    public static final int SUPPLY_SLOTMACHINE_SLOT = 16; // „Automat łupu"
    public static final int SUPPLY_LANDING_INDICATOR_SLOT = 19;
    public static final int SUPPLY_LANDED_INDICATOR_SLOT  = 21;
    public static final int SUPPLY_DESPAWN_INDICATOR_SLOT = 23;
    public static final int SUPPLY_SLOTMACHINE_INDICATOR_SLOT = 25;

    // Klucze ustawień głośności dźwięków poszczególnych faz zrzutu (0-100, per gracz)
    public static final String SOUND_SUPPLY_LANDING_KEY = "sound-supply-landing";
    public static final String SOUND_SUPPLY_LANDED_KEY  = "sound-supply-landed";
    public static final String SOUND_SUPPLY_DESPAWN_KEY = "sound-supply-despawn";
    public static final String SOUND_SUPPLY_SLOTMACHINE_KEY = "sound-supply-slotmachine";

    /** Klucz ustawienia dźwięku fazy zrzutu dla slota wskaźnika (albo null). */
    public static String supplySoundKeyForIndicatorSlot(int slot) {
        return switch (slot) {
            case SUPPLY_LANDING_INDICATOR_SLOT     -> SOUND_SUPPLY_LANDING_KEY;
            case SUPPLY_LANDED_INDICATOR_SLOT      -> SOUND_SUPPLY_LANDED_KEY;
            case SUPPLY_DESPAWN_INDICATOR_SLOT     -> SOUND_SUPPLY_DESPAWN_KEY;
            case SUPPLY_SLOTMACHINE_INDICATOR_SLOT -> SOUND_SUPPLY_SLOTMACHINE_KEY;
            default -> null;
        };
    }

    // Panel WIDOCZNOŚCI (podmenu ustawień): 6x9 = 54 sloty, powrót (nether star
    // na środku dołu, slot 49) do USTAWIEŃ, bez emeralda punktów.
    private static final int VISIBILITY_SETTINGS_SIZE = 54;
    // Redstone lampy - kategorie widoczności (w grze sloty 12/14/16/30/32/34)
    // i betonowe wskaźniki pod nimi (slot + 9) pokazują % widoczności.
    public static final int VISIBILITY_PARTICLES_SLOT       = 11; // „Widoczność cząsteczek"
    public static final int VISIBILITY_ACTIONBAR_SLOT       = 13; // „Widoczność action bara"
    public static final int VISIBILITY_BOSSBAR_SLOT         = 15; // „Widoczność boss bara"
    public static final int VISIBILITY_MOB_HP_SLOT          = 29; // „Widoczność punktów zdrowia mobów"
    public static final int VISIBILITY_TURRET_HP_SLOT       = 31; // „Widoczność punktów zdrowia wieżyczek"
    public static final int VISIBILITY_VILLAGER_HP_SLOT     = 33; // „Widoczność punktów zdrowia osadników"
    public static final int VISIBILITY_PARTICLES_INDICATOR_SLOT   = 20;
    public static final int VISIBILITY_ACTIONBAR_INDICATOR_SLOT   = 22;
    public static final int VISIBILITY_BOSSBAR_INDICATOR_SLOT     = 24;
    public static final int VISIBILITY_MOB_HP_INDICATOR_SLOT      = 38;
    public static final int VISIBILITY_TURRET_HP_INDICATOR_SLOT   = 40;
    public static final int VISIBILITY_VILLAGER_HP_INDICATOR_SLOT = 42;

    // Klucze ustawień widoczności (0-100, per gracz, zapis w players.yml)
    public static final String VISIBILITY_PARTICLES_KEY   = "visibility-particles";
    public static final String VISIBILITY_ACTIONBAR_KEY   = "visibility-actionbar";
    public static final String VISIBILITY_BOSSBAR_KEY     = "visibility-bossbar";
    public static final String VISIBILITY_MOB_HP_KEY      = "visibility-mob-hp";
    public static final String VISIBILITY_TURRET_HP_KEY   = "visibility-turret-hp";
    public static final String VISIBILITY_VILLAGER_HP_KEY = "visibility-villager-hp";

    /** Klucz ustawienia widoczności dla slota wskaźnika (albo null). */
    public static String visibilityKeyForIndicatorSlot(int slot) {
        return switch (slot) {
            case VISIBILITY_PARTICLES_INDICATOR_SLOT   -> VISIBILITY_PARTICLES_KEY;
            case VISIBILITY_ACTIONBAR_INDICATOR_SLOT   -> VISIBILITY_ACTIONBAR_KEY;
            case VISIBILITY_BOSSBAR_INDICATOR_SLOT     -> VISIBILITY_BOSSBAR_KEY;
            case VISIBILITY_MOB_HP_INDICATOR_SLOT      -> VISIBILITY_MOB_HP_KEY;
            case VISIBILITY_TURRET_HP_INDICATOR_SLOT   -> VISIBILITY_TURRET_HP_KEY;
            case VISIBILITY_VILLAGER_HP_INDICATOR_SLOT -> VISIBILITY_VILLAGER_HP_KEY;
            default -> null;
        };
    }

    // Podmenu POWIADOMIEŃ: 4x9 = 36 slotów, powrót (nether star na środku dołu,
    // slot 31) do panelu USTAWIEŃ, bez emeralda punktów.
    private static final int NOTIF_SETTINGS_SIZE = 36;
    // Dzwony kanałów powiadomień (w grze sloty 12/14/16) i betonowe wskaźniki
    // pod nimi (jeden rząd niżej, slot + 9).
    public static final int NOTIF_CHAT_SLOT       = 11; // „Powiadomienia na czacie"
    public static final int NOTIF_ACTIONBAR_SLOT  = 13; // „Powiadomienia na action barze"
    public static final int NOTIF_SCREEN_SLOT     = 15; // „Powiadomienia na ekranie"
    public static final int NOTIF_CHAT_INDICATOR_SLOT      = 20;
    public static final int NOTIF_ACTIONBAR_INDICATOR_SLOT = 22;
    public static final int NOTIF_SCREEN_INDICATOR_SLOT    = 24;

    // Klucze ustawień kanałów powiadomień (100 = włączone, 0 = wyłączone, per gracz)
    public static final String NOTIF_CHAT_KEY       = "notif-chat";
    public static final String NOTIF_ACTIONBAR_KEY  = "notif-actionbar";
    public static final String NOTIF_SCREEN_KEY     = "notif-screen";

    /** Klucz ustawienia kanału powiadomień dla slota wskaźnika (albo null). */
    public static String notifKeyForIndicatorSlot(int slot) {
        return switch (slot) {
            case NOTIF_CHAT_INDICATOR_SLOT      -> NOTIF_CHAT_KEY;
            case NOTIF_ACTIONBAR_INDICATOR_SLOT -> NOTIF_ACTIONBAR_KEY;
            case NOTIF_SCREEN_INDICATOR_SLOT    -> NOTIF_SCREEN_KEY;
            default -> null;
        };
    }

    /** Przełącza kanał powiadomień: 100% (włączone) <-> 0% (wyłączone). */
    public static int toggleNotificationLevel(int current) {
        return current > 0 ? 0 : 100;
    }

    // Podmenu ustawień POGODY: 3x9 = 27 slotów, powrót (nether star na środku
    // dołu, slot 22) do panelu USTAWIEŃ, bez emeralda punktów.
    private static final int FLOOR_SETTINGS_SIZE = 27;
    // Zegary do wyboru pogody (w grze sloty 12/16)
    public static final int FLOOR_CLOCK_SLOT_1 = 11;
    public static final int FLOOR_CLOCK_SLOT_2 = 15;

    // Podmenu ustawień JĘZYKA: 3x9 = 27 slotów, powrót (nether star na środku
    // dołu, slot 22) do panelu USTAWIEŃ, bez emeralda punktów.
    private static final int LANG_SETTINGS_SIZE = 27;
    // Writable booki do wyboru języka (w grze sloty 12/16)
    public static final int LANG_POLISH_SLOT  = 11; // „Język polski"
    public static final int LANG_ENGLISH_SLOT = 15; // „Język angielski"

    // Klucz ustawienia języka (per gracz, zapis w players.yml) i jego wartości
    public static final String LANG_KEY     = "language";
    public static final String LANG_POLISH  = "polski";
    public static final String LANG_ENGLISH = "angielski";

    // Podmenu ustawiania SLOTÓW KITU: 6x9 = 54 sloty, powrót (nether star na
    // środku dołu, slot 49) do panelu USTAWIEŃ, bez emeralda punktów.
    private static final int KIT_SLOTS_SETTINGS_SIZE = 54;

    // Podmenu KOMPASU: 6x9 = 54 sloty, powrót (nether star na środku dołu,
    // slot 49) do panelu USTAWIEŃ, bez emeralda punktów.
    private static final int COMPASS_SETTINGS_SIZE = 54;
    // Kompasy w panelu kompasu (w grze sloty 12/14/16/30/32/34) - na razie bez nazw
    public static final int[] COMPASS_SETTINGS_SLOTS = {11, 13, 15, 29, 31, 33}; // w grze 12/14/16/30/32/34
    // Tabliczka z instrukcją (w grze slot 5) i ikony klas (w grze sloty 21-25 i 30-31)
    public static final int KIT_SLOTS_INFO_SLOT = 4; // „Wybierz klasę, aby ustawić własne ustawienie przedmiotów"
    public static final int[] KIT_SLOTS_CLASS_SLOTS = {20, 21, 22, 23, 24, 29, 30}; // w grze 21-25, 30-31
    // Kolejność klas w panelu slotów kitu (wg życzenia: 21-25, 30-31 w grze)
    public static final List<String> KIT_SLOTS_ORDER = List.of("wojownik", "tank", "lucznik", "magik", "medyk", "inzynier", "assassin");

    // GUI układania przedmiotów dla danej klasy (3x9 = 27 slotów): tytuł to
    // nazwa klasy, rząd 1 (0-8) i boki dolnego rzędu to szara szyba, środek
    // (rząd 2, sloty 9-17) to pasek przedmiotów klasy przy starcie areny;
    // na dole Zapisz (zielony beton), powrót (nether star) i Reset (czerwony
    // beton). Gracz może WZIĄĆ przedmiot na myszkę i postawić go w innym
    // miejscu paska (tylko sloty 9-17), a Zapisz zapamiętuje układ per gracz.
    private static final int KIT_SLOTS_EDIT_SIZE = 27;
    public static final int[] KIT_SLOTS_EDIT_GLASS_SLOTS = {0, 1, 2, 3, 4, 5, 6, 7, 8, 18, 19, 21, 23, 25, 26};
    public static final int   KIT_SLOTS_EDIT_FIRST_SLOT = 9;  // pierwszy slot paska przedmiotów
    public static final int   KIT_SLOTS_EDIT_LAST_SLOT  = 17; // ostatni slot paska przedmiotów
    public static final int   KIT_SLOTS_EDIT_SAVE_SLOT  = 20;
    public static final int   KIT_SLOTS_EDIT_BACK_SLOT  = 22;
    public static final int   KIT_SLOTS_EDIT_RESET_SLOT = 24;

    /** Klucz w players.yml z zapisanym układem paska klasy (sloty 9-17 z GUI
     *  „Ustawienia klas" -> edytor klasy). */
    public static String kitSlotsSettingKey(String kitId) {
        return "kit-slots-" + kitId;
    }

    /** Sygnatura przedmiotu (Materiał|nazwa) używana do zapisu układu paska -
     *  po niej znajdziemy przedmiot przy wydawaniu kitu na arenie. Pusty slot
     *  daje pusty string. */
    public static String kitSlotSignature(ItemStack it) {
        if (it == null || it.getType() == Material.AIR) return "";
        ItemMeta meta = it.getItemMeta();
        String name = meta != null && meta.hasDisplayName() ? meta.getDisplayName() : "";
        return it.getType().name() + "|" + name;
    }

    // Panel ulepszeń wieżyczki: 4x9 = 36 slotów (wyjście na dole na środku,
    // punkty w prawym dolnym rogu). Przyciski to WARIANTY wieżyczki (lód/ogień/
    // trucizna/elektryk/magia): klik PODMIENIA postawioną wieżyczkę na wybrany
    // zapis stojaka w tym samym miejscu (z pełnym życiem).
    private static final int TURRET_UPGRADE_SIZE = 36;
    private static final int TURRET_STAR_SLOT    = 31; // ostatni rząd, środek (4x9)
    public static final int TURRET_POINTS_SLOT   = 35; // ostatni rząd, prawy dolny róg (nieklikalny)

    /** Definicja jednego ulepszenia wieżyczki: slot przycisku + żywioł, z którego
     *  biorą się ikona, nazwa, zapis stojaka i cena (dane w TurretElement). */
    public record TurretUpgrade(int slot, TurretElement element) {
        public Material material() { return element.getMaterial(); }
        public String name()       { return element.getName(); }
        public String preset()     { return element.getPreset(); }
        public int price()         { return element.getPrice(); }

        /** Nazwa wieżyczki w języku gracza (GUI ulepszeń i komunikaty). */
        public String localizedName(Player player) {
            return Lang.get(player, "turret-" + element.name().toLowerCase(Locale.ROOT));
        }
    }

    /** Dostępne ulepszenia wieżyczki - nazwa zapisu (preset) = plik w stojaki/*.yml.
     *  Przycisk pokazuje się tylko, gdy zapis istnieje na serwerze.
     *  4 sloty (w grze 11/13/15/17) = 4 ulepszenia podstawowej wieżyczki. */
    private static final TurretUpgrade[] TURRET_UPGRADES = {
            new TurretUpgrade(10, TurretElement.ICE),
            new TurretUpgrade(12, TurretElement.FIRE),
            new TurretUpgrade(14, TurretElement.POISON),
            new TurretUpgrade(16, TurretElement.ELECTRIC),
    };

    /** Definicja ulepszenia dla slota przycisku albo null, gdy to nie przycisk ulepszenia. */
    public static TurretUpgrade turretUpgradeForSlot(int slot) {
        for (TurretUpgrade u : TURRET_UPGRADES) {
            if (u.slot == slot) return u;
        }
        return null;
    }

    // Podmenu kategorii - strzałka powrotu i szmaragd w ostatnim rzędzie (size-5 i size-1)

    // Przedmioty w podmenu kategorii: 45 na stronę (5 wierszy x 9 slotów, sloty 0-44).
    // Dolny rząd (45-53) to nawigacja: strzałki stron, powrót (nether star na 49), punkty.
    private static final int ITEMS_PER_PAGE = 45;
    private static final int PAGE_PREV_SLOT = 45;
    private static final int PAGE_INFO_SLOT = 47;
    private static final int PAGE_NEXT_SLOT = 51;
    // Kategorie Bronie i Inne: strzałki stron stoją tuż przy nether starze (49) -
    // „wstecz" na 48 (o 1 mniej niż nether star), „dalej" na 50 (o 1 w prawo od 51).
    private static final int PAGE_PREV_SLOT_NEAR_STAR = 48;
    private static final int PAGE_NEXT_SLOT_NEAR_STAR = 50;
    /** Nowa strzała otwierająca podmenu „Strzały - Niestandardowe Strzały" w
     *  kategorii Strzały: slot 50 (o 1 więcej niż nether star na slocie 49). */
    private static final int BOWS_CUSTOM_ARROWS_SLOT = 50;
    /** Sloty (w kodzie) zwykłej i widmowej strzały w podmenu „Niestandardowe strzały"
     *  (PIERWSZA i jedyna strona: slot 20/21 = pozycje 20/21 - bez pustej strony
     *  na starcie). */
    private static final int CUSTOM_ARROWS_SLOT_PLAIN = 20;
    private static final int CUSTOM_ARROWS_SLOT_SPECTRAL = 21;

    // GUI wyboru części zbroi/broni dla zaklęć - 3x9 slotów (27).
    // Zbroja ustawiona w lewą stronę, po kolei bez przerw: hełm, napierśnik,
    // spodnie, buty, a zaraz za nimi broń: miecz, siekiera, łuk, kusza (proca
    // to kusza, więc trafia do slotu kuszy i dostaje własne reguły przy zakupie),
    // a trójząb stoi na początku rzędu (slot 9).
    private static final int ENCHANT_TARGET_SIZE = 27;
    private static final int ENCHANT_BOOK_SLOT   = 4;
    private static final int ENCHANT_TRIDENT_SLOT = 9;
    private static final int ENCHANT_HELMET_SLOT = 10;
    private static final int ENCHANT_CHEST_SLOT  = 11;
    private static final int ENCHANT_LEGS_SLOT   = 12;
    private static final int ENCHANT_BOOTS_SLOT  = 13;
    private static final int ENCHANT_SWORD_SLOT  = 14;
    private static final int ENCHANT_AXE_SLOT    = 15;
    private static final int ENCHANT_BOW_SLOT    = 16;
    private static final int ENCHANT_CROSSBOW_SLOT = 17;

    // Cena zaklęć w GUI wyboru elementu zbroi: 250 pkt za nowy (świeży) enchant na
    // danym elemencie. Gdy dany element ma już to zaklęcie, cena rośnie o 250 pkt
    // za KAŻDY poziom na nim (czyli tylko tam, gdzie enchant jest nałożony - reszta
    // elementów zawsze kosztuje podstawowe 250).
    private static final int ENCHANT_PRICE_BASE = 250;
    private static final int ENCHANT_PRICE_PER_LEVEL = 250;

    // Maksymalny poziom zaklęcia w sklepie: każdy enchant można kupować
    // wielokrotnie (+poziom za zakup), ale nie powyżej tego limitu.
    // Konkretne zaklęcia mogą mieć WŁASNY limit przez pole max-level w configu
    // (np. max-level: 3) - patrz enchantMaxLevel(item).
    private static final int MAX_ENCHANT_LEVEL = 10;

    /** Limit poziomu zaklęcia: własny z configu (max-level) albo globalny domyślny. */
    private static int enchantMaxLevel(ShopItem item) {
        return item != null && item.getMaxLevel() > 0 ? item.getMaxLevel() : MAX_ENCHANT_LEVEL;
    }

    // Rodzaje broni rozpoznawane w GUI zaklęć (szukane w rękach, potem w ekwipunku)
    // trzyma teraz enum WeaponType (SWORD / AXE / RANGED z listami materiałów).
    // Powrót (slot 22 = size-5) i punkty (slot 26 = size-1) - jak w kategorii

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

    // Podmenu „Strzały - Niestandardowe Strzały" (otwierane nową strzałą w kategorii
    // Strzały): przedmioty po slocie w kodzie (20/21 = pierwsza strona podmenu).
    private final Map<Integer, ShopItem> customArrowsItems = new java.util.HashMap<>();

    // Arena „podglądu" sklepu otwartego komendą /def shop_arena (operator stojący na arenie,
    // ale niegrający). Używana, gdy gracz nie gra aktualnie na żadnej arenie.
    private final Map<UUID, Arena> previewArenas = new HashMap<>();

    // Czy panel ustawień został otwarty ZE SKLEPU (true) czy komendą /ustawienia (false).
    // Ustawiane tylko przy WEJŚCIU (sklep -> true, komenda -> false); nawigacja wewnątrz
    // ustawień (podmenu, kategorie) tej flagi nie rusza - dzięki temu nether star w głównym
    // panelu wie, czy ma WRÓCIĆ DO SKLEPU (wejście ze sklepu) czy ZAMKNĄĆ GUI (komenda).
    private final Map<UUID, Boolean> settingsFromShop = new HashMap<>();

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

            // Najpierw umieść przedmioty z jawnie podanym slotem, potem resztę w wolnych miejscach po kolei.
            // Slot >= 45 oznacza następną stronę kategorii (slot 45 = pozycja 0 na stronie 2).
            Map<Integer, ShopItem> slots = new java.util.HashMap<>();
            List<ShopItem> autoPlace = new ArrayList<>();
            for (ShopItem item : parsed) {
                if (item.getSlot() >= 0 && !slots.containsKey(item.getSlot())) {
                    slots.put(item.getSlot(), item);
                } else {
                    autoPlace.add(item);
                }
            }
            int nextFree = 0;
            for (ShopItem item : autoPlace) {
                while (slots.containsKey(nextFree)) nextFree++;
                slots.put(nextFree, item);
                nextFree++;
            }

            // Zabezpieczenie: kategoria „Inne" ZAWSZE ma wieżyczki w sklepie (strona 2,
            // obok TNT). Jeśli config na serwerze jest stary (powstał zanim dodano wpis
            // wiezyczka / wiezyczka_maga), dokładamy je automatycznie - żeby przedmiot
            // zawsze był widoczny.
            if (category == ShopCategory.OTHER) {
                boolean hasTurret = false;
                for (ShopItem item : slots.values()) {
                    if (item.getType() == ShopItemType.TURRET) {
                        hasTurret = true;
                        break;
                    }
                }
                if (!hasTurret && plugin.getStandPresetManager() != null
                        && plugin.getStandPresetManager().loadPreset("wiezyczka") != null
                        && !slots.containsKey(46)) {
                    ShopItem turret = new ShopItem("wiezyczka", ShopItemType.TURRET, 10);
                    turret.setDisplayName("Zwykła Wieżyczka Strzelnicza");
                    turret.setSlot(46);
                    slots.put(46, turret);
                }
                // Wieżyczka Magii (wiezyczka_maga.yml) - obok zwykłej wieżyczki.
                boolean hasMageTurret = false;
                for (ShopItem item : slots.values()) {
                    if (item.getType() == ShopItemType.TURRET
                            && "wiezyczka_maga".equals(item.getId())) {
                        hasMageTurret = true;
                        break;
                    }
                }
                if (!hasMageTurret && plugin.getStandPresetManager() != null
                        && plugin.getStandPresetManager().loadPreset("wiezyczka_maga") != null
                        && !slots.containsKey(47)) {
                    ShopItem mage = new ShopItem("wiezyczka_maga", ShopItemType.TURRET, 10);
                    mage.setDisplayName("Wieżyczka Magii");
                    mage.setSlot(47);
                    slots.put(47, mage);
                }
            }

            categoryItems.put(category, slots);
        }

        // Podmenu „Strzały - Niestandardowe Strzały" (kategoria Strzały): przedmioty
        // z sekcji shop.categories.bows.custom-arrows.items (sloty 20/21 w kodzie =
        // pierwsza strona podmenu - jedna, bez pustej strony).
        customArrowsItems.clear();
        ConfigurationSection bowsSection = categoriesSection.getConfigurationSection(ShopCategory.BOWS.getConfigKey());
        if (bowsSection != null) {
            ConfigurationSection subItems = bowsSection.getConfigurationSection("custom-arrows.items");
            if (subItems != null) {
                for (String id : subItems.getKeys(false)) {
                    ConfigurationSection s = subItems.getConfigurationSection(id);
                    if (s == null) continue;
                    ShopItem item = parseItem(id, s);
                    if (item != null && item.getSlot() >= 0) customArrowsItems.put(item.getSlot(), item);
                }
            }
        }
        // Zabezpieczenie: gdy config na serwerze jest stary (bez sekcji custom-arrows),
        // dokładamy zwykłą i widmową strzałę automatycznie - żeby podmenu nie było puste.
        if (!customArrowsItems.containsKey(CUSTOM_ARROWS_SLOT_PLAIN)) {
            ShopItem arrow = new ShopItem("arrow", ShopItemType.ITEM, 2);
            arrow.setMaterial(Material.ARROW);
            arrow.setAmount(1);
            arrow.setSlot(CUSTOM_ARROWS_SLOT_PLAIN);
            customArrowsItems.put(CUSTOM_ARROWS_SLOT_PLAIN, arrow);
        }
        if (!customArrowsItems.containsKey(CUSTOM_ARROWS_SLOT_SPECTRAL)) {
            ShopItem spectral = new ShopItem("spectral_arrow", ShopItemType.ITEM, 5);
            spectral.setMaterial(Material.SPECTRAL_ARROW);
            spectral.setAmount(1);
            spectral.setSlot(CUSTOM_ARROWS_SLOT_SPECTRAL);
            customArrowsItems.put(CUSTOM_ARROWS_SLOT_SPECTRAL, spectral);
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
        item.setDisplayName(s.getString("name", null));

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
                item.setMaxLevel(s.getInt("max-level", 0)); // 0 = globalny domyślny limit
            }
            case MINE -> {
                // Mina nie potrzebuje dodatkowych pól - tylko cenę (już ustawioną wyżej)
            }
            case TURRET -> {
                // Wieżyczka: id w configu = nazwa zapisu stojaka (np. wiezyczka).
                // Ikona = patyk (przedmiot postawienia); reszta przy zakupie.
            }
            case POTION -> {
                // Zwykła, waniliowa mikstura: bazowy typ (np. HEALING, SWIFTNESS_STRONG),
                // opcjonalnie splash: true (miotana) albo material: TIPPED_ARROW (strzała
                // z miksturą - nazwa i czas trwania jak w vanilla).
                String potionName = s.getString("potion", "HEALING").toUpperCase();
                PotionType pt = null;
                try {
                    pt = PotionType.valueOf(potionName);
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("Nieznany typ mikstury w configu: " + potionName
                            + " (fallback: HEALING). Popraw nazwe, np. STRONG_HEALING zamiast HEALING_STRONG.");
                }
                item.setPotionType(pt != null ? pt : PotionType.HEALING);
                item.setSplash(s.getBoolean("splash", false));
                String matName = s.getString("material", null);
                if (matName != null) {
                    Material m = Material.matchMaterial(matName);
                    if (m != null) item.setMaterial(m);
                }
            }
            case FIREWORK -> {
                // Fajerwerk: power = wysokość lotu (1-3).
                item.setPower(s.getInt("power", 1));
            }
        }
        return item;
    }

    // ---------- Podgląd sklepu z komendy /def shop_arena ----------

    public void setPreviewArena(UUID uuid, Arena arena) {
        if (arena == null) {
            previewArenas.remove(uuid);
        } else {
            previewArenas.put(uuid, arena);
        }
    }

    public void clearPreviewArena(UUID uuid) {
        previewArenas.remove(uuid);
    }

    /**
     * Zwraca arenę, której sklep ogląda gracz: najpierw arenę, na której faktycznie
     * gra, a jeśli nie gra - arenę z podglądu otwartego komendą /def shop_arena,
     * a na końcu pierwszą wczytaną arenę (żeby /ustawienia działało też poza grą).
     */
    public Arena resolveArena(Player player) {
        for (Arena arena : plugin.getArenaManager().getArenas().values()) {
            if (arena.isPlaying(player)) return arena;
        }
        Arena preview = previewArenas.get(player.getUniqueId());
        if (preview != null) return preview;
        if (!plugin.getArenaManager().getArenas().isEmpty()) {
            return plugin.getArenaManager().getArenas().values().iterator().next();
        }
        return null;
    }

    // ---------- Otwieranie GUI ----------

    public void openMain(Player player, Arena arena) {
        ShopHolder holder = new ShopHolder(ShopHolder.ViewType.MAIN, null);
        Inventory inv = Bukkit.createInventory(holder, SIZE, Component.text(Lang.get(player, "shop-title-main")));
        holder.setInventory(inv);

        fillGlass(inv);
        inv.setItem(STAR_SLOT, createStar(player));
        inv.setItem(POINTS_SLOT, createPointsItem(player, arena.getSessionPoints(player.getUniqueId())));
        inv.setItem(SETTINGS_SLOT, createSettingsIcon(player));
        inv.setItem(ACHIEVEMENTS_SLOT, createAchievementsIcon(player));

        for (Map.Entry<ShopCategory, Integer> entry : CATEGORY_SLOTS.entrySet()) {
            inv.setItem(entry.getValue(), createCategoryIcon(player, entry.getKey()));
        }

        player.openInventory(inv);
    }

    /**
     * Otwiera panel USTAWIEŃ (54 sloty): szkło + nether star na środku dołu.
     * Zachowuje obecne źródło (ze sklepu / z komendy) - używane przy powrotach
     * z podmenu ustawień.
     */
    public void openSettings(Player player, Arena arena) {
        openSettings(player, arena, settingsFromShop.getOrDefault(player.getUniqueId(), false));
    }

    /**
     * Otwiera panel USTAWIEŃ z jawnym źródłem: fromShop = true (wejście ze sklepu)
     * pokazuje nether star „Powrót", który WRACA do menu sklepu; false (komenda
     * /ustawienia) - gwiazdę „Wyjdź z ustawień", która zamyka GUI.
     */
    public void openSettings(Player player, Arena arena, boolean fromShop) {
        if (fromShop) settingsFromShop.put(player.getUniqueId(), true);
        else settingsFromShop.remove(player.getUniqueId());
        ShopHolder holder = new ShopHolder(ShopHolder.ViewType.SETTINGS, null);
        Inventory inv = Bukkit.createInventory(holder, SETTINGS_SIZE, Component.text(Lang.get(player, "set-title")));
        holder.setInventory(inv);

        fillGlass(inv);
        // ikony opcji (na razie tylko wizualne - funkcje dodawane po kolei)
        inv.setItem(SETTINGS_PARTICLES_SLOT, new ItemBuilder(Material.FIREWORK_STAR, 1).name(Lang.get(player, "set-particles")).build());
        inv.setItem(SETTINGS_SOUNDS_SLOT, new ItemBuilder(Material.NOTE_BLOCK, 1).name(Lang.get(player, "set-sounds")).build());
        inv.setItem(SETTINGS_NOTIF_SLOT, new ItemBuilder(Material.BELL, 1).name(Lang.get(player, "set-notifications")).build());
        inv.setItem(SETTINGS_VISIBILITY_SLOT, new ItemBuilder(Material.REDSTONE_LAMP, 1).name(Lang.get(player, "set-visibility")).build());
        inv.setItem(SETTINGS_FLOOR_SLOT, new ItemBuilder(Material.CLOCK, 1).name(Lang.get(player, "set-weather")).build());
        inv.setItem(SETTINGS_KIT_SLOTS_SLOT, new ItemBuilder(Material.NETHERITE_SWORD, 1).name(Lang.get(player, "set-kit-slots")).build());
        inv.setItem(SETTINGS_LANG_SLOT, new ItemBuilder(Material.WRITABLE_BOOK, 1).name(Lang.get(player, "set-language")).build());
        inv.setItem(SETTINGS_COMPASS_SLOT, new ItemBuilder(Material.COMPASS, 1)
                .name(Lang.get(player, "set-compass"))
                .lore(List.of(Lang.get(player, "set-click-to-open")))
                .build());
        // nether star: ze sklepu = „Powrót" do menu sklepu, z komendy = „Wyjdź z ustawień"
        inv.setItem(SETTINGS_SIZE - 5, fromShop ? createBackButton(player) : createExitSettingsButton(player));

        player.openInventory(inv);
    }

    /**
     * Otwiera panel DŹWIĘKÓW (podmenu ustawień, 54 sloty): szkło + powrót
     * (nether star na środku dołu) do USTAWIEŃ. Przełączniki dźwięków dodawane
     * będą w kolejnych krokach.
     */
    public void openSoundSettings(Player player, Arena arena) {
        ShopHolder holder = new ShopHolder(ShopHolder.ViewType.SOUND_SETTINGS, null);
        Inventory inv = Bukkit.createInventory(holder, SOUND_SETTINGS_SIZE, Component.text(Lang.get(player, "set-sounds-title")));
        holder.setInventory(inv);

        fillGlass(inv);
        // przełączniki dźwięków - działają jako włącz/wyłącz per gracz (zapis w
        // players.yml): note block = ikona kategorii, betonowy wskaźnik pod spodem
        // (kolor = % głośności). „Dźwięki gui" (10), „Dźwięki mobów z areny" (12),
        // „Dźwięki mobów po stronie gracza" (14), „Dźwięki klas" (16, suwak 25),
        // „Dźwięki wieżyczek" (28), „Dźwięki niestandardowych przedmiotów" (30).
        // „Dźwięki zrzutu zaopatrzenia" (43) to skrzynia otwierająca podmenu faz zrzutu.
        inv.setItem(SOUND_MOBS_SLOT, createSoundToggle(player, SOUND_MOBS_KEY, Lang.get(player, "set-sound-gui")));
        inv.setItem(SOUND_MOBS_INDICATOR_SLOT, createSoundIndicator(player, SOUND_MOBS_KEY, Lang.get(player, "set-sound-gui")));
        inv.setItem(SOUND_TURRETS_SLOT, createSoundToggle(player, SOUND_TURRETS_KEY, Lang.get(player, "set-sound-arena-mobs")));
        inv.setItem(SOUND_TURRETS_INDICATOR_SLOT, createSoundIndicator(player, SOUND_TURRETS_KEY, Lang.get(player, "set-sound-arena-mobs")));
        inv.setItem(SOUND_EXPLOSIONS_SLOT, createSoundToggle(player, SOUND_EXPLOSIONS_KEY, Lang.get(player, "set-sound-player-mobs")));
        inv.setItem(SOUND_EXPLOSIONS_INDICATOR_SLOT, createSoundIndicator(player, SOUND_EXPLOSIONS_KEY, Lang.get(player, "set-sound-player-mobs")));
        inv.setItem(SOUND_ALLIES_SLOT, createSoundToggle(player, SOUND_ALLIES_KEY, Lang.get(player, "set-sound-turrets")));
        inv.setItem(SOUND_ALLIES_INDICATOR_SLOT, createSoundIndicator(player, SOUND_ALLIES_KEY, Lang.get(player, "set-sound-turrets")));
        inv.setItem(SOUND_ABILITIES_SLOT, createSoundToggle(player, SOUND_ABILITIES_KEY, Lang.get(player, "set-sound-custom-items")));
        inv.setItem(SOUND_ABILITIES_INDICATOR_SLOT, createSoundIndicator(player, SOUND_ABILITIES_KEY, Lang.get(player, "set-sound-custom-items")));
        inv.setItem(SOUND_CLASSES_SLOT, createSoundToggle(player, SOUND_CLASSES_KEY, Lang.get(player, "set-sound-classes")));
        inv.setItem(SOUND_CLASSES_INDICATOR_SLOT, createSoundIndicator(player, SOUND_CLASSES_KEY, Lang.get(player, "set-sound-classes")));
        inv.setItem(SOUND_SUPPLY_LABEL_SLOT, new ItemBuilder(Material.COPPER_CHEST, 1)
                .name("§e" + Lang.get(player, "set-sound-supply"))
                .lore(List.of(
                        Lang.get(player, "set-sound-supply-lore-1"),
                        Lang.get(player, "set-sound-supply-lore-2")
                ))
                .build());
        inv.setItem(SOUND_SETTINGS_SIZE - 5, createSettingsBackButton(player)); // nether star „Powrót" do ustawień
        inv.setItem(SOUND_SETTINGS_SIZE - 6, createPrevCategoryButton(player)); // lewa strzałka „Poprzednia kategoria"
        inv.setItem(SOUND_SETTINGS_SIZE - 4, createNextCategoryButton(player)); // prawa strzałka „Następna kategoria"

        player.openInventory(inv);
    }

    /**
     * Otwiera panel CZĄSTECZEK (podmenu ustawień, 54 sloty): szkło + powrót
     * (nether star na środku dołu) do USTAWIEŃ. Firework star = kategoria
     * cząsteczek, betonowy wskaźnik pod spodem pokazuje ILOŚĆ cząsteczek w % -
     * czerwony 0%, pomarańczowy 25%, żółty 50%, jasnozielony 75%, zielony 100%.
     * Kliknięcie betonu zmniejsza ilość o jeden próg (100->75->...->0->100).
     */
    public void openParticleSettings(Player player, Arena arena) {
        ShopHolder holder = new ShopHolder(ShopHolder.ViewType.PARTICLE_SETTINGS, null);
        Inventory inv = Bukkit.createInventory(holder, PARTICLE_SETTINGS_SIZE, Component.text(Lang.get(player, "set-particles-title")));
        holder.setInventory(inv);

        fillGlass(inv);
        inv.setItem(PARTICLE_GENERAL_SLOT, createParticleToggle(Lang.get(player, "set-particle-general")));
        inv.setItem(PARTICLE_GENERAL_INDICATOR_SLOT, createParticleIndicator(player, PARTICLE_GENERAL_KEY, Lang.get(player, "set-particle-general")));
        inv.setItem(PARTICLE_TURRETS_SLOT, createParticleToggle(Lang.get(player, "set-particle-turrets")));
        inv.setItem(PARTICLE_TURRETS_INDICATOR_SLOT, createParticleIndicator(player, PARTICLE_TURRETS_KEY, Lang.get(player, "set-particle-turrets")));
        inv.setItem(PARTICLE_CLASSES_SLOT, createParticleToggle(Lang.get(player, "set-particle-classes")));
        inv.setItem(PARTICLE_CLASSES_INDICATOR_SLOT, createParticleIndicator(player, PARTICLE_CLASSES_KEY, Lang.get(player, "set-particle-classes")));
        inv.setItem(PARTICLE_PLAYERS_SLOT, createParticleToggle(Lang.get(player, "set-particle-players")));
        inv.setItem(PARTICLE_PLAYERS_INDICATOR_SLOT, createParticleIndicator(player, PARTICLE_PLAYERS_KEY, Lang.get(player, "set-particle-players")));
        inv.setItem(PARTICLE_CUSTOM_SLOT, createParticleToggle(Lang.get(player, "set-particle-custom-abilities")));
        inv.setItem(PARTICLE_CUSTOM_INDICATOR_SLOT, createParticleIndicator(player, PARTICLE_CUSTOM_KEY, Lang.get(player, "set-particle-custom-abilities")));
        inv.setItem(PARTICLE_FIREWORKS_SLOT, createParticleToggle(Lang.get(player, "set-particle-fireworks")));
        inv.setItem(PARTICLE_FIREWORKS_INDICATOR_SLOT, createParticleIndicator(player, PARTICLE_FIREWORKS_KEY, Lang.get(player, "set-particle-fireworks")));
        inv.setItem(PARTICLE_SUPPLY_SLOT, createParticleToggle(Lang.get(player, "set-particle-supply")));
        inv.setItem(PARTICLE_SUPPLY_INDICATOR_SLOT, createParticleIndicator(player, PARTICLE_SUPPLY_KEY, Lang.get(player, "set-particle-supply")));
        inv.setItem(PARTICLE_SETTINGS_SIZE - 5, createSettingsBackButton(player)); // nether star „Powrót" do ustawień
        inv.setItem(PARTICLE_SETTINGS_SIZE - 6, createPrevCategoryButton(player)); // lewa strzałka „Poprzednia kategoria"
        inv.setItem(PARTICLE_SETTINGS_SIZE - 4, createNextCategoryButton(player)); // prawa strzałka „Następna kategoria"

        player.openInventory(inv);
    }

    /**
     * Otwiera podmenu dźwięków ZRZUTU ZAOPATRZENIA (4x9 = 36 slotów): szkło +
     * powrót (nether star na środku dołu, slot 31) do panelu DŹWIĘKÓW. Miedziane
     * skrzynie na slotach 11/13/15/17 (w grze) to fazy zrzutu, a betonowe wskaźniki
     * pod nimi (slot + 9) - ich głośność per gracz: kliknięcie cykluje
     * 0 -> 25 -> 50 -> 75 -> 100 -> 0 (zapis w players.yml).
     */
    public void openSupplySoundSettings(Player player, Arena arena) {
        ShopHolder holder = new ShopHolder(ShopHolder.ViewType.SUPPLY_SOUNDS, null);
        Inventory inv = Bukkit.createInventory(holder, SUPPLY_SOUNDS_SIZE, Component.text(Lang.get(player, "set-supply-sounds-title")));
        holder.setInventory(inv);

        fillGlass(inv);
        inv.setItem(SUPPLY_LANDING_SLOT, createSupplyChest(Lang.get(player, "set-supply-landing")));
        inv.setItem(SUPPLY_LANDED_SLOT, createSupplyChest(Lang.get(player, "set-supply-landed")));
        inv.setItem(SUPPLY_DESPAWN_SLOT, createSupplyChest(Lang.get(player, "set-supply-despawn")));
        inv.setItem(SUPPLY_SLOTMACHINE_SLOT, createSupplyChest(Lang.get(player, "set-supply-slotmachine")));
        inv.setItem(SUPPLY_LANDING_INDICATOR_SLOT, createSupplyIndicator(player, SOUND_SUPPLY_LANDING_KEY, Lang.get(player, "set-supply-landing")));
        inv.setItem(SUPPLY_LANDED_INDICATOR_SLOT, createSupplyIndicator(player, SOUND_SUPPLY_LANDED_KEY, Lang.get(player, "set-supply-landed")));
        inv.setItem(SUPPLY_DESPAWN_INDICATOR_SLOT, createSupplyIndicator(player, SOUND_SUPPLY_DESPAWN_KEY, Lang.get(player, "set-supply-despawn")));
        inv.setItem(SUPPLY_SLOTMACHINE_INDICATOR_SLOT, createSupplyToggle(player, SOUND_SUPPLY_SLOTMACHINE_KEY));
        inv.setItem(SUPPLY_SOUNDS_SIZE - 5, createSettingsBackButton(player)); // nether star „Powrót" do dźwięków

        player.openInventory(inv);
    }

    /**
     * Otwiera podmenu POWIADOMIEŃ (4x9 = 36 slotów): szkło + powrót (nether star
     * na środku dołu, slot 31) do panelu USTAWIEŃ. Dzwony na slotach 12/14/16
     * (w grze) to kanały powiadomień, a betonowe wskaźniki pod nimi (slot + 9) -
     * ich stan: zielony 100% = kanał włączony, czerwony 0% = wyłączony.
     * Kliknięcie wskaźnika przełącza kanał (100% <-> 0%, zapis w players.yml).
     */
    public void openNotificationSettings(Player player, Arena arena) {
        ShopHolder holder = new ShopHolder(ShopHolder.ViewType.NOTIFICATION_SETTINGS, null);
        Inventory inv = Bukkit.createInventory(holder, NOTIF_SETTINGS_SIZE, Component.text(Lang.get(player, "set-notifications-title")));
        holder.setInventory(inv);

        fillGlass(inv);
        inv.setItem(NOTIF_CHAT_SLOT, createNotificationBell(Lang.get(player, "set-notif-chat")));
        inv.setItem(NOTIF_ACTIONBAR_SLOT, createNotificationBell(Lang.get(player, "set-notif-actionbar")));
        inv.setItem(NOTIF_SCREEN_SLOT, createNotificationBell(Lang.get(player, "set-notif-screen")));
        inv.setItem(NOTIF_CHAT_INDICATOR_SLOT, createNotificationIndicator(player, NOTIF_CHAT_KEY, Lang.get(player, "set-notif-chat")));
        inv.setItem(NOTIF_ACTIONBAR_INDICATOR_SLOT, createNotificationIndicator(player, NOTIF_ACTIONBAR_KEY, Lang.get(player, "set-notif-actionbar")));
        inv.setItem(NOTIF_SCREEN_INDICATOR_SLOT, createNotificationIndicator(player, NOTIF_SCREEN_KEY, Lang.get(player, "set-notif-screen")));
        inv.setItem(NOTIF_SETTINGS_SIZE - 5, createSettingsBackButton(player)); // nether star „Powrót" do ustawień
        inv.setItem(NOTIF_SETTINGS_SIZE - 6, createPrevCategoryButton(player)); // lewa strzałka „Poprzednia kategoria"
        inv.setItem(NOTIF_SETTINGS_SIZE - 4, createNextCategoryButton(player)); // prawa strzałka „Następna kategoria"

        player.openInventory(inv);
    }

    /**
     * Otwiera panel WIDOCZNOŚCI (podmenu ustawień, 6x9 = 54 sloty): szkło +
     * powrót (nether star na środku dołu, slot 49) do USTAWIEŃ. Redstone lampy
     * (w grze sloty 12/14/16/30/32/34) to kategorie widoczności, a betonowe
     * wskaźniki pod nimi (slot + 9) pokazują % widoczności - czerwony 0%,
     * pomarańczowy 25%, żółty 50%, jasnozielony 75%, zielony 100%. Kliknięcie
     * betonu cykluje progi (0 -> 25 -> ... -> 100 -> 0, zapis w players.yml).
     */
    public void openVisibilitySettings(Player player, Arena arena) {
        ShopHolder holder = new ShopHolder(ShopHolder.ViewType.VISIBILITY_SETTINGS, null);
        Inventory inv = Bukkit.createInventory(holder, VISIBILITY_SETTINGS_SIZE, Component.text(Lang.get(player, "set-visibility-title")));
        holder.setInventory(inv);

        fillGlass(inv);
        inv.setItem(VISIBILITY_PARTICLES_SLOT, createVisibilityToggle(Lang.get(player, "set-vis-particles")));
        inv.setItem(VISIBILITY_PARTICLES_INDICATOR_SLOT, createVisibilityIndicator(player, VISIBILITY_PARTICLES_KEY, Lang.get(player, "set-vis-particles")));
        inv.setItem(VISIBILITY_ACTIONBAR_SLOT, createVisibilityToggle(Lang.get(player, "set-vis-actionbar")));
        inv.setItem(VISIBILITY_ACTIONBAR_INDICATOR_SLOT, createVisibilityIndicator(player, VISIBILITY_ACTIONBAR_KEY, Lang.get(player, "set-vis-actionbar")));
        inv.setItem(VISIBILITY_BOSSBAR_SLOT, createVisibilityToggle(Lang.get(player, "set-vis-bossbar")));
        inv.setItem(VISIBILITY_BOSSBAR_INDICATOR_SLOT, createVisibilityIndicator(player, VISIBILITY_BOSSBAR_KEY, Lang.get(player, "set-vis-bossbar")));
        inv.setItem(VISIBILITY_MOB_HP_SLOT, createVisibilityToggle(Lang.get(player, "set-vis-mob-hp")));
        inv.setItem(VISIBILITY_MOB_HP_INDICATOR_SLOT, createVisibilityIndicator(player, VISIBILITY_MOB_HP_KEY, Lang.get(player, "set-vis-mob-hp")));
        inv.setItem(VISIBILITY_TURRET_HP_SLOT, createVisibilityToggle(Lang.get(player, "set-vis-turret-hp")));
        inv.setItem(VISIBILITY_TURRET_HP_INDICATOR_SLOT, createVisibilityIndicator(player, VISIBILITY_TURRET_HP_KEY, Lang.get(player, "set-vis-turret-hp")));
        inv.setItem(VISIBILITY_VILLAGER_HP_SLOT, createVisibilityToggle(Lang.get(player, "set-vis-villager-hp")));
        inv.setItem(VISIBILITY_VILLAGER_HP_INDICATOR_SLOT, createVisibilityIndicator(player, VISIBILITY_VILLAGER_HP_KEY, Lang.get(player, "set-vis-villager-hp")));
        inv.setItem(VISIBILITY_SETTINGS_SIZE - 5, createSettingsBackButton(player)); // nether star „Powrót" do ustawień
        inv.setItem(VISIBILITY_SETTINGS_SIZE - 6, createPrevCategoryButton(player)); // lewa strzałka „Poprzednia kategoria"
        inv.setItem(VISIBILITY_SETTINGS_SIZE - 4, createNextCategoryButton(player)); // prawa strzałka „Następna kategoria"

        player.openInventory(inv);
    }

    /**
     * Otwiera podmenu ustawień POGODY (27 slotów): szkło + powrót (nether star
     * na środku dołu) do panelu USTAWIEŃ. Zegary na slotach 11/15 (w grze 12/16)
     * to „Pora dnia" i „Zmiana pogody" - w lore mają listę opcji z zaznaczoną
     * wybraną; kliknięcie cykluje opcję (zapis w players.yml, działa od razu).
     */
    public void openFloorSettings(Player player, Arena arena) {
        ShopHolder holder = new ShopHolder(ShopHolder.ViewType.FLOOR_SETTINGS, null);
        Inventory inv = Bukkit.createInventory(holder, FLOOR_SETTINGS_SIZE, Component.text(Lang.get(player, "set-weather-title")));
        holder.setInventory(inv);

        fillGlass(inv);
        inv.setItem(FLOOR_CLOCK_SLOT_1, createTimeClock(player));
        inv.setItem(FLOOR_CLOCK_SLOT_2, createWeatherClock(player));
        inv.setItem(FLOOR_SETTINGS_SIZE - 5, createSettingsBackButton(player)); // nether star „Powrót" do ustawień
        inv.setItem(FLOOR_SETTINGS_SIZE - 6, createPrevCategoryButton(player)); // lewa strzałka „Poprzednia kategoria"
        inv.setItem(FLOOR_SETTINGS_SIZE - 4, createNextCategoryButton(player)); // prawa strzałka „Następna kategoria"

        player.openInventory(inv);
    }

    /** Zegar „Pora dnia": nazwa + lore z opcjami (wybrana podświetlona) + podpowiedź
     *  przesuwania (LPM = następna, PPM = poprzednia). */
    private ItemStack createTimeClock(Player player) {
        var data = plugin.getPlayerDataManager().get(player.getUniqueId(), player.getName());
        String current = data.getSetting(WeatherUtil.TIME_KEY, "normalna");
        List<String> lore = weatherOptionLore(player, WeatherUtil.TIME_OPTIONS, current);
        lore.add("");
        lore.add(Lang.get(player, "set-time-hint"));
        return new ItemBuilder(Material.CLOCK, 1)
                .name(Lang.get(player, "set-time-clock"))
                .lore(lore)
                .build();
    }

    /** Writable book wyboru języka: wybrany język ma zieloną nazwę i „✓ Wybrany",
     *  nie wybrany - żółtą nazwę i zachętę do kliknięcia. */
    private ItemStack createLanguageBook(Player player, String lang, String name) {
        var data = plugin.getPlayerDataManager().get(player.getUniqueId(), player.getName());
        String current = data.getSetting(LANG_KEY, LANG_POLISH);
        ItemBuilder b = new ItemBuilder(Material.WRITABLE_BOOK, 1);
        if (lang.equals(current)) {
            b.name("§a" + name).lore(List.of("", Lang.get(player, "set-lang-selected")));
        } else {
            b.name("§6" + name).lore(List.of(Lang.get(player, "set-lang-click")));
        }
        return b.build();
    }

    /** Zegar „Zmiana pogody": nazwa + lore z opcjami (wybrana podświetlona) +
     *  podpowiedź przesuwania (LPM = następna, PPM = poprzednia). */
    private ItemStack createWeatherClock(Player player) {
        var data = plugin.getPlayerDataManager().get(player.getUniqueId(), player.getName());
        String current = data.getSetting(WeatherUtil.WEATHER_KEY, "normalna");
        List<String> lore = weatherOptionLore(player, WeatherUtil.WEATHER_OPTIONS, current);
        lore.add("");
        lore.add(Lang.get(player, "set-weather-hint"));
        return new ItemBuilder(Material.CLOCK, 1)
                .name(Lang.get(player, "set-weather-clock"))
                .lore(lore)
                .build();
    }

    /** Linie lore z opcjami pory dnia/pogody; wybrana jest podświetlona. */
    private static List<String> weatherOptionLore(Player player, String[] options, String current) {
        List<String> lore = new ArrayList<>();
        for (String opt : options) {
            if (opt.equals(current)) {
                lore.add("§a» " + WeatherUtil.label(player, opt) + " " + Lang.get(player, "set-current"));
            } else {
                lore.add("§7" + WeatherUtil.label(player, opt));
            }
        }
        return lore;
    }

    /**
     * Otwiera podmenu ustawień JĘZYKA (3x9 = 27 slotów): szkło + powrót
     * (nether star na środku dołu, slot 22) do panelu USTAWIEŃ. Writable booki
     * na slotach 12/16 (w grze) wybierają język: kliknięcie zapisuje wybór per
     * gracz (players.yml), a wybrany język ma zieloną nazwę i „✓ Wybrany".
     */
    public void openLanguageSettings(Player player, Arena arena) {
        ShopHolder holder = new ShopHolder(ShopHolder.ViewType.LANG_SETTINGS, null);
        Inventory inv = Bukkit.createInventory(holder, LANG_SETTINGS_SIZE, Component.text(Lang.get(player, "set-language-title")));
        holder.setInventory(inv);

        fillGlass(inv);
        inv.setItem(LANG_POLISH_SLOT, createLanguageBook(player, LANG_POLISH, Lang.get(player, "set-lang-polish")));
        inv.setItem(LANG_ENGLISH_SLOT, createLanguageBook(player, LANG_ENGLISH, Lang.get(player, "set-lang-english")));
        inv.setItem(LANG_SETTINGS_SIZE - 5, createSettingsBackButton(player)); // nether star „Powrót" do ustawień
        inv.setItem(LANG_SETTINGS_SIZE - 6, createPrevCategoryButton(player)); // lewa strzałka „Poprzednia kategoria"
        inv.setItem(LANG_SETTINGS_SIZE - 4, createNextCategoryButton(player)); // prawa strzałka „Następna kategoria"

        player.openInventory(inv);
    }

    /**
     * Otwiera panel ustawiania SLOTÓW KITU (6x9 = 54 sloty): szkło + powrót
     * (nether star na środku dołu, slot 49) do panelu USTAWIEŃ. Tabliczka na
     * slocie 5 (w grze) to instrukcja, a ikony klas (w grze sloty 21-25 i 30-31)
     * pokazują klasy, dla których można ustawić własne sloty przedmiotów.
     */
    public void openKitSlotsSettings(Player player, Arena arena) {
        ShopHolder holder = new ShopHolder(ShopHolder.ViewType.KIT_SLOTS_SETTINGS, null);
        Inventory inv = Bukkit.createInventory(holder, KIT_SLOTS_SETTINGS_SIZE, Component.text(Lang.get(player, "set-kit-slots-title")));
        holder.setInventory(inv);

        fillGlass(inv);
        inv.setItem(KIT_SLOTS_INFO_SLOT, new ItemBuilder(Material.OAK_SIGN, 1)
                .name(Lang.get(player, "set-kit-slots-sign"))
                .lore(List.of(
                        Lang.get(player, "set-kit-slots-sign-lore-1"),
                        Lang.get(player, "set-kit-slots-sign-lore-2"),
                        Lang.get(player, "set-kit-slots-sign-lore-3")
                ))
                .build());
        for (int i = 0; i < KIT_SLOTS_CLASS_SLOTS.length && i < KIT_SLOTS_ORDER.size(); i++) {
            inv.setItem(KIT_SLOTS_CLASS_SLOTS[i], createKitClassIcon(player, KIT_SLOTS_ORDER.get(i)));
        }
        inv.setItem(KIT_SLOTS_SETTINGS_SIZE - 5, createSettingsBackButton(player)); // nether star „Powrót" do ustawień
        inv.setItem(KIT_SLOTS_SETTINGS_SIZE - 6, createPrevCategoryButton(player)); // lewa strzałka „Poprzednia kategoria"
        inv.setItem(KIT_SLOTS_SETTINGS_SIZE - 4, createNextCategoryButton(player)); // prawa strzałka „Następna kategoria"

        player.openInventory(inv);
    }

    /**
     * Otwiera GUI układania przedmiotów dla danej klasy (3x9 = 27 slotów):
     * tytuł to nazwa klasy (np. „Łucznik"), rząd 1 to szara szyba (slot 4 to
     * tabliczka z instrukcją), środek (sloty 9-17) to pasek przedmiotów klasy
     * przy starcie areny, a na dole Zapisz (zielony beton), powrót (nether
     * star) i Reset (czerwony beton). Otwierane kliknięciem klasy w panelu
     * „Ustawienia klas". Gracz bierze przedmiot na myszkę i przestawia go
     * w obrębie slotów 9-17; Zapisz zapamiętuje układ per gracz (players.yml),
     * a giveKit układa tak pasek na arenie.
     */
    public void openKitSlotsEdit(Player player, Arena arena, String kitId) {
        Kit kit = plugin.getKitManager().getKit(kitId);
        if (kit == null) return;
        ShopHolder holder = new ShopHolder(ShopHolder.ViewType.KIT_SLOTS_EDIT, null);
        holder.setKitId(kitId);
        Inventory inv = Bukkit.createInventory(holder, KIT_SLOTS_EDIT_SIZE,
                Component.text(kit.localizedName(player))); // tytuł = nazwa klasy
        holder.setInventory(inv);

        ItemStack glass = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE, 1).name(" ").build();
        for (int s : KIT_SLOTS_EDIT_GLASS_SLOTS) inv.setItem(s, glass);
        inv.setItem(4, new ItemBuilder(Material.OAK_SIGN, 1)
                .name(Lang.get(player, "set-kit-edit-hint"))
                .lore(List.of(
                        Lang.get(player, "set-kit-edit-hint-lore-1"),
                        Lang.get(player, "set-kit-edit-hint-lore-2"),
                        Lang.get(player, "set-kit-edit-hint-lore-3"),
                        Lang.get(player, "set-kit-edit-hint-lore-4")
                ))
                .build());
        // środek (sloty 9-17) = układ paska tej klasy przy starcie areny (bez zbroi);
        // jeśli gracz zapisał własny układ, pokazujemy go zamiast domyślnego
        ItemStack[] layout = loadKitSlotsLayout(player, kitId);
        for (int i = 0; i < 9; i++) {
            if (layout[i] != null) inv.setItem(9 + i, layout[i]);
        }
        inv.setItem(KIT_SLOTS_EDIT_SAVE_SLOT, new ItemBuilder(Material.GREEN_CONCRETE, 1)
                .name(Lang.get(player, "set-kit-save"))
                .build());
        inv.setItem(KIT_SLOTS_EDIT_BACK_SLOT, createSettingsBackButton(player)); // nether star „Powrót"
        inv.setItem(KIT_SLOTS_EDIT_RESET_SLOT, new ItemBuilder(Material.RED_CONCRETE, 1)
                .name(Lang.get(player, "set-kit-reset"))
                .build());

        player.openInventory(inv);
    }

    /** Układ paska klasy (9 slotów) do pokazania w edytorze: zapisany przez
     *  gracza układ (jeśli istnieje i da się go odtworzyć) albo domyślny pasek
     *  kitu (getHotbarPreview). */
    private ItemStack[] loadKitSlotsLayout(Player player, String kitId) {
        ItemStack[] hotbar = plugin.getKitManager().getHotbarPreview(kitId);
        String saved = plugin.getPlayerDataManager().get(player.getUniqueId(), player.getName())
                .getSetting(kitSlotsSettingKey(kitId));
        if (saved == null) return hotbar;
        String[] sigs = saved.split(",", -1);
        if (sigs.length != 9) return hotbar;
        ItemStack[] layout = new ItemStack[9];
        List<ItemStack> pool = new ArrayList<>();
        for (ItemStack it : hotbar) {
            if (it != null) pool.add(it.clone());
        }
        for (int i = 0; i < 9; i++) {
            if (sigs[i].isEmpty()) continue;
            for (int p = 0; p < pool.size(); p++) {
                ItemStack cand = pool.get(p);
                if (cand != null && sigs[i].equals(kitSlotSignature(cand))) {
                    layout[i] = cand;
                    pool.set(p, null);
                    break;
                }
            }
        }
        boolean any = false;
        for (ItemStack it : layout) {
            if (it != null) { any = true; break; }
        }
        return any ? layout : hotbar;
    }

    /**
     * Otwiera panel KOMPASU (6x9 = 54 sloty): szkło + powrót (nether star na
     * środku dołu, slot 49) do panelu USTAWIEŃ. Na razie puste miejsce pod
     * opcje - dopiszesz je w kolejnych krokach.
     */
    public void openCompassSettings(Player player, Arena arena) {
        ShopHolder holder = new ShopHolder(ShopHolder.ViewType.COMPASS_SETTINGS, null);
        Inventory inv = Bukkit.createInventory(holder, COMPASS_SETTINGS_SIZE, Component.text(Lang.get(player, "set-compass")));
        holder.setInventory(inv);

        fillGlass(inv);
        for (int slot : COMPASS_SETTINGS_SLOTS) {
            inv.setItem(slot, new ItemBuilder(Material.COMPASS, 1).build()); // bez nazwy na razie
        }
        inv.setItem(COMPASS_SETTINGS_SIZE - 5, createSettingsBackButton(player)); // nether star „Powrót" do ustawień
        inv.setItem(COMPASS_SETTINGS_SIZE - 6, createPrevCategoryButton(player)); // lewa strzałka „Poprzednia kategoria"
        inv.setItem(COMPASS_SETTINGS_SIZE - 4, createNextCategoryButton(player)); // prawa strzałka „Następna kategoria"

        player.openInventory(inv);
    }

    /** Ikona klasy w panelu slotów kitu: materiał z ikony kitu (np. wojownik =
     *  miedziany miecz), z nazwą klasy. Kliknięcia obsługuje ShopListener. */
    private ItemStack createKitClassIcon(Player player, String kitId) {
        Kit kit = plugin.getKitManager().getKit(kitId);
        if (kit == null || kit.getIcon() == null) {
            return new ItemBuilder(Material.BARRIER, 1).name(Lang.get(player, "set-kit-missing-icon")).build();
        }
        ItemStack icon = kit.getIcon().clone();
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(kit.localizedName(player));
            // usuwamy „Niezniszczalne" z tooltipa ikony klasy (tylko tu)
            meta.setUnbreakable(false);
            icon.setItemMeta(meta);
        }
        return icon;
    }

    /** Note block - tylko ikona kategorii dźwięku (nieklikalny). Stan pokazuje
     *  betonowy wskaźnik pod spodem (zielony = włączone, czerwony = wyciszone),
     *  a przełącza się WYŁĄCZNIE kliknięciem na ten beton. */
    private ItemStack createSoundToggle(Player player, String key, String label) {
        return new ItemBuilder(Material.NOTE_BLOCK, 1)
                .name("§e" + label)
                .build();
    }

    /** Wskaźnik GŁOŚNOŚCI dźwięku (beton pod note blockiem): kolor betonu i nazwa
     *  pokazują % słyszalności - 0% czerwony, 25% pomarańczowy, 50% żółty,
     *  75% jasnozielony, 100% zielony. Kliknięcie cykluje progi głośności. */
    private ItemStack createSoundIndicator(Player player, String key, String label) {
        int vol = plugin.getGameManager().soundVolume(player, key);
        Material mat;
        String name;
        switch (vol) {
            case 0   -> { mat = Material.RED_CONCRETE;    name = "§c0%"; }
            case 25  -> { mat = Material.ORANGE_CONCRETE; name = "§625%"; }
            case 50  -> { mat = Material.YELLOW_CONCRETE; name = "§e50%"; }
            case 75  -> { mat = Material.LIME_CONCRETE;   name = "§a75%"; }
            default  -> { mat = Material.GREEN_CONCRETE;  name = "§2100%"; }
        }
        return new ItemBuilder(mat, 1)
                .name(name)
                .lore(List.of(Lang.get(player, "set-click-to-change")))
                .build();
    }

    /** Firework star - tylko ikona kategorii cząsteczek (nieklikalny). Ilość
     *  pokazuje betonowy wskaźnik pod spodem, a zmniejsza się WYŁĄCZNIE
     *  kliknięciem na ten beton. */
    private ItemStack createParticleToggle(String label) {
        return new ItemBuilder(Material.FIREWORK_STAR, 1)
                .name("§e" + label)
                .build();
    }

    /** Wskaźnik ILOŚCI cząsteczek (beton pod firework starem): kolor betonu i
     *  nazwa pokazują % ilości - 0% czerwony, 25% pomarańczowy, 50% żółty,
     *  75% jasnozielony, 100% zielony. Kliknięcie zmienia ilość o jeden próg.
     *  JEDYNY wyjątek: globalny suwak „Ogólne" (slot 19) ma dodatkowo opcję
     *  NIESTANDARDOWE (różowy beton) - gdy kategorie mają własne wartości.
     *  Pozostałe suwaki pokazują tylko 0/25/50/75/100%, bez NIESTANDARDOWE. */
    private ItemStack createParticleIndicator(Player player, String key, String label) {
        int lvl = plugin.getGameManager().particleAmount(player, key);
        if (key.equals(PARTICLE_GENERAL_KEY) && lvl == PARTICLE_GENERAL_CUSTOM) {
            return new ItemBuilder(Material.PINK_CONCRETE, 1)
                    .name(Lang.get(player, "set-custom"))
                    .lore(List.of(Lang.get(player, "set-click-to-change")))
                    .build();
        }
        Material mat;
        String name;
        switch (lvl) {
            case 0   -> { mat = Material.RED_CONCRETE;    name = "§c0%"; }
            case 25  -> { mat = Material.ORANGE_CONCRETE; name = "§625%"; }
            case 50  -> { mat = Material.YELLOW_CONCRETE; name = "§e50%"; }
            case 75  -> { mat = Material.LIME_CONCRETE;   name = "§a75%"; }
            default  -> { mat = Material.GREEN_CONCRETE;  name = "§2100%"; }
        }
        return new ItemBuilder(mat, 1)
                .name(name)
                .lore(List.of(Lang.get(player, "set-click-to-change")))
                .build();
    }

    /** Miedziana skrzynia - ikona dźwięku fazy zrzutu zaopatrzenia (nieklikalna,
     *  stan pokazuje betonowy wskaźnik pod spodem). */
    private ItemStack createSupplyChest(String label) {
        return new ItemBuilder(Material.COPPER_CHEST, 1)
                .name("§e" + label)
                .build();
    }

    /** Betonowy wskaźnik GŁOŚNOŚCI pod skrzynią zrzutu: kolor betonu i nazwa
     *  pokazują % słyszalności danej fazy zrzutu - 0% czerwony, 25% pomarańczowy,
     *  50% żółty, 75% jasnozielony, 100% zielony. Kliknięcie cykluje progi. */
    private ItemStack createSupplyIndicator(Player player, String key, String label) {
        int vol = plugin.getGameManager().soundVolume(player, key);
        Material mat;
        String name;
        switch (vol) {
            case 0   -> { mat = Material.RED_CONCRETE;    name = "§c0%"; }
            case 25  -> { mat = Material.ORANGE_CONCRETE; name = "§625%"; }
            case 50  -> { mat = Material.YELLOW_CONCRETE; name = "§e50%"; }
            case 75  -> { mat = Material.LIME_CONCRETE;   name = "§a75%"; }
            default  -> { mat = Material.GREEN_CONCRETE;  name = "§2100%"; }
        }
        return new ItemBuilder(mat, 1)
                .name(name)
                .lore(List.of(Lang.get(player, "set-click-to-change")))
                .build();
    }

    /** Betonowy wskaźnik ON/OFF dla „Automat łupu" (zielony beton „Włącz" /
     *  czerwony „Wyłącz") zamiast suwaka głośności. Kliknięcie przełącza 100% <-> 0%. */
    private ItemStack createSupplyToggle(Player player, String key) {
        boolean on = plugin.getGameManager().soundVolume(player, key) > 0;
        Material mat = on ? Material.GREEN_CONCRETE : Material.RED_CONCRETE;
        String name = on ? Lang.get(player, "set-on") : Lang.get(player, "set-off");
        return new ItemBuilder(mat, 1)
                .name(name)
                .lore(List.of(Lang.get(player, "set-click-to-change")))
                .build();
    }

    /** Dzwon - ikona kanału powiadomień (nieklikalna, stan pokazuje betonowy
     *  wskaźnik pod spodem). */
    private ItemStack createNotificationBell(String label) {
        return new ItemBuilder(Material.BELL, 1)
                .name("§e" + label)
                .build();
    }

    /** Betonowy wskaźnik STANU kanału powiadomień pod dzwonem: zielony „Włącz" =
     *  kanał włączony, czerwony „Wyłącz" = wyłączony. Kliknięcie przełącza. */
    private ItemStack createNotificationIndicator(Player player, String key, String label) {
        boolean on = plugin.getGameManager().soundVolume(player, key) > 0;
        Material mat = on ? Material.GREEN_CONCRETE : Material.RED_CONCRETE;
        String name = on ? Lang.get(player, "set-on") : Lang.get(player, "set-off");
        return new ItemBuilder(mat, 1)
                .name(name)
                .lore(List.of(Lang.get(player, "set-click-to-change")))
                .build();
    }

    /** Redstone lampa - ikona kategorii widoczności (nieklikalna). Stan pokazuje
     *  betonowy wskaźnik pod spodem, a zmienia się WYŁĄCZNIE kliknięciem na beton. */
    private ItemStack createVisibilityToggle(String label) {
        return new ItemBuilder(Material.REDSTONE_LAMP, 1)
                .name("§e" + label)
                .build();
    }

    /** Betonowy wskaźnik WIDOCZNOŚCI (beton pod redstone lampą): zielony
     *  „Włączone" (widoczność > 0) albo czerwony „Wyłączone" (0%). Kliknięcie
     *  przełącza między 100% a 0%. */
    private ItemStack createVisibilityIndicator(Player player, String key, String label) {
        boolean on = plugin.getGameManager().visibilityLevel(player, key) > 0;
        Material mat = on ? Material.GREEN_CONCRETE : Material.RED_CONCRETE;
        String name = on ? Lang.get(player, "set-vis-on") : Lang.get(player, "set-vis-off");
        return new ItemBuilder(mat, 1)
                .name(name)
                .lore(List.of(Lang.get(player, "set-click-to-change")))
                .build();
    }

    public void openCategory(Player player, Arena arena, ShopCategory category) {
        openCategory(player, arena, category, 0);
    }

    /**
     * Otwiera panel ULEPSZEŃ wieżyczki: przyciski wariantów (lód/ogień/trucizna/
     * elektryk/magia). Kliknięcie PODMIENIA postawioną wieżyczkę na wybrany zapis
     * stojaka w tym samym miejscu. Przyciski pokazują się tylko dla zapisów, które
     * istnieją na serwerze; wyjście (gwiazda) i punkty (szmaragd) jak w sklepie.
     */
    public void openTurretUpgrade(Player player, ArmorStand stand) {
        if (stand == null || stand.isDead()) return;
        ShopHolder holder = new ShopHolder(ShopHolder.ViewType.TURRET_UPGRADE, null);
        holder.setTurretUuid(stand.getUniqueId().toString());
        Inventory inv = Bukkit.createInventory(holder, TURRET_UPGRADE_SIZE,
                Component.text(Lang.get(player, "turret-upgrade-title")));
        holder.setInventory(inv);

        fillGlass(inv);
        for (TurretUpgrade u : TURRET_UPGRADES) {
            if (plugin.getStandPresetManager().loadPreset(u.preset()) != null) {
                inv.setItem(u.slot, createUpgradeButton(player, u.material(), u.localizedName(player), u.price()));
            }
        }
        inv.setItem(TURRET_STAR_SLOT, createTurretExit(player));
        Arena arena = resolveArena(player);
        int points = arena != null ? arena.getSessionPoints(player.getUniqueId()) : 0;
        inv.setItem(TURRET_POINTS_SLOT, createPointsItem(player, points));

        player.openInventory(inv);
    }

    /**
     * Wykonuje ulepszenie wieżyczki: usuwa postawioną wieżyczkę (razem z barierą
     * i paskiem HP) i stawia w tym samym miejscu zapis stojaka o nazwie z definicji
     * ulepszenia, z pełnym życiem (jeśli zapis ma hp:). Kierunek (yaw) zostaje.
     */
    public void upgradeTurret(Player player, ArmorStand stand, TurretUpgrade upgrade) {
        if (stand == null || stand.isDead()) {
            Lang.sendActionBar(player, "turret-gone");
            return;
        }
        if (plugin.getStandPresetManager().loadPreset(upgrade.preset()) == null) {
            Lang.sendActionBar(player, "turret-preset-missing", upgrade.preset());
            return;
        }
        Arena arena = resolveArena(player);
        if (arena == null || arena.getSessionPoints(player.getUniqueId()) < upgrade.price()) {
            Lang.sendActionBar(player, "not-enough-points-turret", upgrade.price());
            return;
        }
        Location loc = stand.getLocation().clone(); // pozycja + yaw/pitch starej wieżyczki
        plugin.getArmorStandTuner().remove(stand, player, false);
        ArmorStand fresh = plugin.getStandPresetManager().place(upgrade.preset(), player, null, null);
        if (fresh == null) {
            return;
        }
        fresh.teleport(loc);
        arena.addSessionPoints(player.getUniqueId(), -upgrade.price());
        Lang.sendActionBar(player, "turret-upgraded", upgrade.localizedName(player), upgrade.price());
    }

    public void openCategory(Player player, Arena arena, ShopCategory category, int page) {
        int size = SIZE;
        ShopHolder holder = new ShopHolder(ShopHolder.ViewType.CATEGORY, category, null, page);
        String title = category == ShopCategory.BOWS
                ? Lang.get(player, "shop-title-custom-arrows")  // kategoria Strzały ma własny tytuł
                : Lang.get(player, "shop-title", category.localizedName(player));
        Inventory inv = Bukkit.createInventory(holder, size, Component.text(title));
        holder.setInventory(inv);

        fillGlass(inv);
        inv.setItem(size - 5, createBackButton(player));
        if (category == ShopCategory.BOWS) {
            // Kategoria Strzały: nowa strzała (slot 50, o 1 więcej niż nether star na 49)
            // otwiera podmenu „Strzały - Niestandardowe Strzały".
            inv.setItem(BOWS_CUSTOM_ARROWS_SLOT, createCustomArrowsButton(player));
        }
        inv.setItem(size - 1, createPointsItem(player, arena.getSessionPoints(player.getUniqueId())));

        Map<Integer, ShopItem> items = categoryItems.getOrDefault(category, Map.of());
        int pages = pageCount(items);
        if (pages == 0) pages = 1; // pusta kategoria i tak pokazuje „stronę 1"
        if (page < 0) page = 0;
        if (page >= pages) page = pages - 1;

        int pageStart = page * ITEMS_PER_PAGE;
        int pageEnd = pageStart + ITEMS_PER_PAGE;
        for (Map.Entry<Integer, ShopItem> entry : items.entrySet()) {
            int slot = entry.getKey();
            if (slot >= pageStart && slot < pageEnd) {
                inv.setItem(slot - pageStart, createShopItemIcon(entry.getValue(), player));
            }
        }

        // Nawigacja stron: strzałki tylko gdy jest ich więcej niż jedna. Kategorie
        // Bronie i Inne mają strzałki przy nether starze (48/50) - patrz prevPageSlot/nextPageSlot
        if (page > 0) inv.setItem(prevPageSlot(category), createPageArrow(player, false));
        if (page + 1 < pages) inv.setItem(nextPageSlot(category), createPageArrow(player, true));
        if (pages > 1) inv.setItem(PAGE_INFO_SLOT, createPageInfo(player, page, pages));

        player.openInventory(inv);
    }

    /** Slot strzałki „wstecz" dla kategorii: 48 (obok nether star) dla Bronie i Inne, 45 dla reszty. */
    public int prevPageSlot(ShopCategory category) {
        return (category == ShopCategory.SWORDS || category == ShopCategory.OTHER)
                ? PAGE_PREV_SLOT_NEAR_STAR : PAGE_PREV_SLOT;
    }

    /** Slot strzałki „dalej" dla kategorii: 50 (obok nether star) dla Bronie i Inne, 51 dla reszty. */
    public int nextPageSlot(ShopCategory category) {
        return (category == ShopCategory.SWORDS || category == ShopCategory.OTHER)
                ? PAGE_NEXT_SLOT_NEAR_STAR : PAGE_NEXT_SLOT;
    }

    /** Otwiera podmenu „Strzały - Niestandardowe Strzały" (kategoria Strzały). */
    public void openCustomArrows(Player player, Arena arena) {
        openCustomArrows(player, arena, 0);
    }

    /**
     * Otwiera podmenu „Strzały - Niestandardowe Strzały" (54 sloty, jak kategoria):
     * zwykła i widmowa strzała leżą na PIERWSZEJ (i jedynej) stronie (sloty 20/21
     * w kodzie = pozycje 20/21); nawigacja stron niepotrzebna, powrót (nether star)
     * jak w kategoriach - gwiazda wraca do kategorii Strzały.
     */
    public void openCustomArrows(Player player, Arena arena, int page) {
        int size = SIZE;
        ShopHolder holder = new ShopHolder(ShopHolder.ViewType.CUSTOM_ARROWS, ShopCategory.BOWS, null, page);
        Inventory inv = Bukkit.createInventory(holder, size,
                Component.text(Lang.get(player, "shop-title-custom-arrows-sub")));
        holder.setInventory(inv);

        fillGlass(inv);
        inv.setItem(size - 5, createBackButton(player));
        inv.setItem(size - 1, createPointsItem(player, arena.getSessionPoints(player.getUniqueId())));

        Map<Integer, ShopItem> items = customArrowsItems;
        int pages = pageCount(items);
        if (pages == 0) pages = 1; // puste podmenu i tak pokazuje „stronę 1"
        if (page < 0) page = 0;
        if (page >= pages) page = pages - 1;

        int pageStart = page * ITEMS_PER_PAGE;
        int pageEnd = pageStart + ITEMS_PER_PAGE;
        for (Map.Entry<Integer, ShopItem> entry : items.entrySet()) {
            int slot = entry.getKey();
            if (slot >= pageStart && slot < pageEnd) {
                inv.setItem(slot - pageStart, createShopItemIcon(entry.getValue(), player));
            }
        }

        // Nawigacja stron: strzałki tylko gdy jest ich więcej niż jedna. W podmenu
        // strzałka „wstecz" stoi PO LEWEJ nether stara (slot 48, przycisk dalej na 51).
        if (page > 0) inv.setItem(PAGE_PREV_SLOT_NEAR_STAR, createPageArrow(player, false));
        if (page + 1 < pages) inv.setItem(PAGE_NEXT_SLOT, createPageArrow(player, true));
        if (pages > 1) inv.setItem(PAGE_INFO_SLOT, createPageInfo(player, page, pages));

        player.openInventory(inv);
    }

    /** Liczba stron kategorii (45 przedmiotów na stronę, liczone po najwyższym slocie). */
    private int pageCount(Map<Integer, ShopItem> items) {
        int maxSlot = -1;
        for (int slot : items.keySet()) {
            if (slot > maxSlot) maxSlot = slot;
        }
        return (maxSlot + ITEMS_PER_PAGE) / ITEMS_PER_PAGE;
    }

    /**
     * Otwiera GUI (3x9 slotów) z aktualnie założoną zbroją i trzymanym narzędziem,
     * na które gracz może nałożyć wybrane zaklęcie.
     */
    public void openEnchantTarget(Player player, Arena arena, ShopCategory category, ShopItem item, int page) {
        // zaklęcie zastrzeżone dla innej klasy - nie otwieramy GUI zakupu
        // (kategoria Zaklęcia jest wyłączona z zasad klas)
        if (!isClassRulesExempt(item)
                && !plugin.getClassRulesManager().allowsPurchase(player, item.getId())) return;
        ShopHolder holder = new ShopHolder(ShopHolder.ViewType.ENCHANT_TARGET, category, item, page);
        Inventory inv = Bukkit.createInventory(holder, ENCHANT_TARGET_SIZE,
                Component.text(Lang.get(player, "enchant-target-title")));
        holder.setInventory(inv);

        fillGlass(inv, Material.GRAY_STAINED_GLASS_PANE); // szare tło; białe szyby = sloty zbroi/narzędzi
        inv.setItem(ENCHANT_BOOK_SLOT, createEnchantBookInfo(player, item));
        inv.setItem(ENCHANT_TARGET_SIZE - 5, createBackButton(player));
        inv.setItem(ENCHANT_TARGET_SIZE - 1, createPointsItem(player, arena.getSessionPoints(player.getUniqueId())));

        if (isWeaponRestrictedEnchant(item)) {
            // zaklęcia tylko na broń (Szczęście/Fortune): sloty zbroi ZABLOKOWANE -
            // czerwone szyby z podpowiedzią, żeby książka nie marnowała się na zbroję
            ItemStack blocked = new ItemBuilder(Material.RED_STAINED_GLASS_PANE, 1)
                    .name(Lang.get(player, "enchant-weapons-only")).build();
            inv.setItem(ENCHANT_HELMET_SLOT, blocked);
            inv.setItem(ENCHANT_CHEST_SLOT, blocked);
            inv.setItem(ENCHANT_LEGS_SLOT, blocked);
            inv.setItem(ENCHANT_BOOTS_SLOT, blocked);
        } else {
            inv.setItem(ENCHANT_HELMET_SLOT, createEquipmentSlotIcon(player, player.getInventory().getHelmet(), item, Lang.get(player, "shop-slot-helmet")));
            inv.setItem(ENCHANT_CHEST_SLOT, createEquipmentSlotIcon(player, player.getInventory().getChestplate(), item, Lang.get(player, "shop-slot-chestplate")));
            inv.setItem(ENCHANT_LEGS_SLOT, createEquipmentSlotIcon(player, player.getInventory().getLeggings(), item, Lang.get(player, "shop-slot-leggings")));
            inv.setItem(ENCHANT_BOOTS_SLOT, createEquipmentSlotIcon(player, player.getInventory().getBoots(), item, Lang.get(player, "shop-slot-boots")));
        }
        if (isArmorOnlyEnchant(item)) {
            // zaklęcia tylko na zbroję (Ochrona/Ciernie): sloty broni ZABLOKOWANE -
            // czerwone szyby z podpowiedzią, żeby książka nie marnowała się na broń
            ItemStack blocked = new ItemBuilder(Material.RED_STAINED_GLASS_PANE, 1)
                    .name(Lang.get(player, "enchant-armor-only")).build();
            inv.setItem(ENCHANT_TRIDENT_SLOT, blocked);
            inv.setItem(ENCHANT_SWORD_SLOT, blocked);
            inv.setItem(ENCHANT_AXE_SLOT, blocked);
            inv.setItem(ENCHANT_BOW_SLOT, blocked);
            inv.setItem(ENCHANT_CROSSBOW_SLOT, blocked);
        } else {
            inv.setItem(ENCHANT_TRIDENT_SLOT, createEquipmentSlotIcon(player, getItemAt(player, findWeaponSlot(player, Material.TRIDENT)), item, Lang.get(player, "shop-slot-trident")));
            inv.setItem(ENCHANT_SWORD_SLOT, createEquipmentSlotIcon(player, getItemAt(player, findWeaponSlot(player, WeaponType.SWORD)), item, Lang.get(player, "shop-slot-sword")));
            inv.setItem(ENCHANT_AXE_SLOT, createEquipmentSlotIcon(player, getItemAt(player, findWeaponSlot(player, WeaponType.AXE)), item, Lang.get(player, "shop-slot-axe")));
            inv.setItem(ENCHANT_BOW_SLOT, createEquipmentSlotIcon(player, getItemAt(player, findWeaponSlot(player, Material.BOW)), item, Lang.get(player, "shop-slot-bow")));
            inv.setItem(ENCHANT_CROSSBOW_SLOT, createEquipmentSlotIcon(player, getItemAt(player, findWeaponSlot(player, Material.CROSSBOW)), item, Lang.get(player, "shop-slot-crossbow")));
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

    public ShopItem getItemBySlot(ShopCategory category, int page, int slot) {
        if (slot < 0 || slot >= ITEMS_PER_PAGE) return null;
        return categoryItems.getOrDefault(category, Map.of()).get(page * ITEMS_PER_PAGE + slot);
    }

    /** Przedmiot podmenu „Strzały - Niestandardowe Strzały" na danej stronie (slot 65/66 = strona 2). */
    public ShopItem getCustomArrowItemBySlot(int page, int slot) {
        if (slot < 0 || slot >= ITEMS_PER_PAGE) return null;
        return customArrowsItems.get(page * ITEMS_PER_PAGE + slot);
    }

    public boolean isStarSlot(int slot)          { return slot == STAR_SLOT; }
    public boolean isSettingsSlot(int slot)      { return slot == SETTINGS_SLOT; }
    public boolean isBackSlot(int slot, int size)   { return slot == size - 5; }

    /** Czy panel ustawień otwarto ZE SKLEPU (nether star ma wracać do menu sklepu)? */
    public boolean isSettingsFromShop(Player player) {
        return settingsFromShop.getOrDefault(player.getUniqueId(), false);
    }

    /** Strzałka „Poprzednia kategoria" (na lewo od nether stara w panelach kategorii ustawień). */
    public boolean isPrevCategorySlot(int slot, int size) { return slot == size - 6; }

    /** Strzałka „Następna kategoria" (na prawo od nether stara w panelach kategorii ustawień). */
    public boolean isNextCategorySlot(int slot, int size) { return slot == size - 4; }

    /** Czy to slot nowej strzały (50) otwierającej podmenu „Niestandardowe strzały"? */
    public boolean isCustomArrowsSlot(int slot)     { return slot == BOWS_CUSTOM_ARROWS_SLOT; }
    public boolean isPointsSlot(int slot, int size) { return slot == size - 1; }
    public boolean isPrevPageSlot(int slot)      { return slot == PAGE_PREV_SLOT; }
    public boolean isNextPageSlot(int slot)      { return slot == PAGE_NEXT_SLOT; }

    /** Strzałka „wstecz" w podmenu niestandardowych strzał: slot 48 (po lewej nether stara). */
    public boolean isCustomArrowsPrevPageSlot(int slot) { return slot == PAGE_PREV_SLOT_NEAR_STAR; }

    /** Kolejność kategorii ustawień dla strzałek (od lewej do prawej na panelu). */
    private static final List<ShopHolder.ViewType> SETTINGS_CATEGORY_ORDER = List.of(
            ShopHolder.ViewType.FLOOR_SETTINGS,        // 1. Pogoda
            ShopHolder.ViewType.PARTICLE_SETTINGS,     // 2. Ustawienia cząsteczek
            ShopHolder.ViewType.SOUND_SETTINGS,        // 3. Dźwięki
            ShopHolder.ViewType.NOTIFICATION_SETTINGS, // 4. Powiadomienia
            ShopHolder.ViewType.VISIBILITY_SETTINGS,   // 5. Widoczności
            ShopHolder.ViewType.COMPASS_SETTINGS,      // 6. Ustawienia kupowania/sklepu
            ShopHolder.ViewType.KIT_SLOTS_SETTINGS,    // 7. Ustawienia klas
            ShopHolder.ViewType.LANG_SETTINGS          // 8. Język
    );

    /** Przechodzi do POPRZEDNIEJ kategorii ustawień (cyklicznie, „w kółko").
     *  Z panelu głównego ustawień poprzednia = ostatnia kategoria (Język). */
    public void openPrevCategory(Player player, Arena arena, ShopHolder.ViewType current) {
        int idx = SETTINGS_CATEGORY_ORDER.indexOf(current);
        if (idx < 0) {
            openCategoryView(player, arena, SETTINGS_CATEGORY_ORDER.get(SETTINGS_CATEGORY_ORDER.size() - 1));
        } else {
            openCategoryView(player, arena,
                    SETTINGS_CATEGORY_ORDER.get((idx - 1 + SETTINGS_CATEGORY_ORDER.size()) % SETTINGS_CATEGORY_ORDER.size()));
        }
    }

    /** Przechodzi do NASTĘPNEJ kategorii ustawień (cyklicznie, „w kółko").
     *  Z panelu głównego ustawień następna = pierwsza kategoria (Pogoda). */
    public void openNextCategory(Player player, Arena arena, ShopHolder.ViewType current) {
        int idx = SETTINGS_CATEGORY_ORDER.indexOf(current);
        if (idx < 0) {
            openCategoryView(player, arena, SETTINGS_CATEGORY_ORDER.get(0));
        } else {
            openCategoryView(player, arena,
                    SETTINGS_CATEGORY_ORDER.get((idx + 1) % SETTINGS_CATEGORY_ORDER.size()));
        }
    }

    /** Otwiera panel wybranej kategorii ustawień. */
    private void openCategoryView(Player player, Arena arena, ShopHolder.ViewType type) {
        switch (type) {
            case FLOOR_SETTINGS -> openFloorSettings(player, arena);
            case PARTICLE_SETTINGS -> openParticleSettings(player, arena);
            case SOUND_SETTINGS -> openSoundSettings(player, arena);
            case NOTIFICATION_SETTINGS -> openNotificationSettings(player, arena);
            case VISIBILITY_SETTINGS -> openVisibilitySettings(player, arena);
            case COMPASS_SETTINGS -> openCompassSettings(player, arena);
            case KIT_SLOTS_SETTINGS -> openKitSlotsSettings(player, arena);
            case LANG_SETTINGS -> openLanguageSettings(player, arena);
            default -> openSettings(player, arena);
        }
    }

    // ---------- Zakup ----------

    public void purchase(Player player, Arena arena, ShopItem item) {
        if (!arena.isPlaying(player)) {
            return; // podgląd sklepu (/def shop_arena) - bez zakupów, bez komunikatu
        }
        // Zasady klas (class-rules.yml): przedmiot zastrzeżony dla innej klasy = brak zakupu.
        // Wyjątek: kategoria Jedzenie - każda klasa kupuje je bez ograniczeń.
        if (!isClassRulesExempt(item)
                && !plugin.getClassRulesManager().allowsPurchase(player, item.getId())) {
            return;
        }
        int points = arena.getSessionPoints(player.getUniqueId());
        if (points < item.getPrice()) {
            Lang.sendActionBar(player, "not-enough-points");
            return;
        }

        boolean specificMessage = false;
        switch (item.getType()) {
            case ITEM -> {
                // Jajka „Przywołaj...": kliknięcie w sklepie PRZYWOŁUJE stwora obok
                // gracza (dokładnie 1 blok) - zamiast dawać przedmiot.
                if ("wolf_spawn_egg".equals(item.getId())) {
                    spawnArmoredWolf(player);
                } else if ("snow_golem_spawn_egg".equals(item.getId())) {
                    spawnThornySnowman(player);
                } else if ("vindicator_spawn_egg".equals(item.getId())) {
                    plugin.getDefenderManager().spawnDefender(player);
                } else if ("iron_golem_spawn_egg".equals(item.getId())) {
                    plugin.getGolemManager().spawnGolem(player);
                } else if ("warden_spawn_egg".equals(item.getId())) {
                    plugin.getWardenManager().spawnWarden(player);
                } else {
                    ItemStack given;
                    // Proca (kusza) - przedmiot z własną logiką (szybkie ładowanie I + strzały brukiem)
                    if ("proca".equals(item.getId())) {
                        given = pl.vd.listeners.ProcaListener.createProcaItem();
                    } else if ("karabin".equals(item.getId())) {
                        // Karabin snajperski - customowa broń (przybliżenie jak lupa + strzał)
                        given = plugin.getSniperRifleManager().createRifle(player);
                    } else {
                        given = new ItemStack(item.getMaterial(), Math.max(1, item.getAmount()));
                        ItemMeta meta = given.getItemMeta();
                        if (meta != null) {
                            // Przedmioty z trwałością (broń, zbroje, narzędzia) są niezniszczalne
                            if (meta instanceof org.bukkit.inventory.meta.Damageable) {
                                meta.setUnbreakable(true);
                            }
                            // Własna nazwa z configu (name:) - np. nazwy klas golemów;
                            // tłumaczona po id przedmiotu (słownik PL/EN)
                            if (item.getDisplayName() != null) {
                                meta.setDisplayName(Lang.getOr(player, "shop-item-" + item.getId(), item.getDisplayName()));
                            }
                            given.setItemMeta(meta);
                        }
                    }
                    giveItem(player, given);
                }
            }
            case EFFECT -> {
                // Stałe efekty: każdy zakup podnosi poziom efektu o 1 (Speed I -> Speed II -> ...)
                PotionEffectType type = item.getEffectType();
                PotionEffect current = player.getPotionEffect(type);
                int nextAmplifier = (current != null && current.getAmplifier() >= item.getAmplifier())
                        ? current.getAmplifier() + 1
                        : item.getAmplifier();
                player.addPotionEffect(new PotionEffect(type, Integer.MAX_VALUE, nextAmplifier, true, false));
                String effectLabel = item.getDisplayName() != null
                        ? Lang.getOr(player, "shop-item-" + item.getId(), item.getDisplayName())
                        : "§f" + prettify(type.getName());
                Lang.sendActionBar(player, "effect-gained", effectLabel, toRoman(nextAmplifier + 1));
                specificMessage = true;
            }
            case ENCHANT -> {
                ItemStack held = player.getInventory().getItemInMainHand();
                if (held.getType() == Material.AIR) {
                    return;
                }
                if (item.getEnchantment() == null) {
                    return;
                }
                // Szybkie ładowanie Procy jest stałe - nie można go ulepszać
                if (pl.vd.listeners.ProcaListener.isProca(held)
                        && item.getEnchantment().getKey().getKey().equals("quick_charge")) {
                    Lang.sendActionBar(player, "cannot-upgrade-more");
                    return;
                }
                // Limit poziomu zaklęcia (jak w GUI wyboru części): nigdy powyżej max.
                held.addUnsafeEnchantment(item.getEnchantment(),
                        Math.min(item.getEnchantLevel(), enchantMaxLevel(item)));
            }
            case MINE -> {
                player.getInventory().addItem(plugin.getMineManager().createMineItem());
            }
            case POTION -> {
                // Zwykła, waniliowa mikstura (bez podtytułów): bazowy typ mikstury nadaje
                // nazwę i efekty jak w vanilla (np. „Mikstura natychmiastowego leczenia",
                // a dla material: TIPPED_ARROW - „Strzała szybkości").
                Material mat = item.getMaterial() != null ? item.getMaterial()
                        : (item.isSplash() ? Material.SPLASH_POTION : Material.POTION);
                ItemStack potion = new ItemStack(mat, Math.max(1, item.getAmount()));
                PotionMeta pm = (PotionMeta) potion.getItemMeta();
                if (pm != null && item.getPotionType() != null) {
                    pm.setBasePotionType(item.getPotionType());
                    potion.setItemMeta(pm);
                }
                giveItem(player, potion);
            }
            case FIREWORK -> {
                // Fajerwerk z wysokością lotu (power) - reszta zwykła, jak w vanilla.
                ItemStack fw = new ItemStack(Material.FIREWORK_ROCKET, Math.max(1, item.getAmount()));
                FireworkMeta fm = (FireworkMeta) fw.getItemMeta();
                if (fm != null) {
                    fm.setPower(Math.max(1, Math.min(3, item.getPower())));
                    fw.setItemMeta(fm);
                }
                giveItem(player, fw);
            }
            case TURRET -> {
                // Wieżyczka: przedmiot-stojak (prawy klik = postaw). id w configu = nazwa
                // zapisu stojaka, np. wiezyczka -> plugins/.../stojaki/wiezyczka.yml.
                // Własna nazwa (name:) z configu, np. „Wieżyczka Magii" dla wiezyczka_maga.
                String turretName = item.getDisplayName() != null
                        ? Lang.getOr(player, "shop-item-" + item.getId(), item.getDisplayName())
                        : Lang.get(player, "shop-item-wiezyczka");
                ItemStack turret = plugin.getStandPresetManager()
                        .bindItem(item.getId(), turretName, true);
                if (turret == null) {
                    // Brak zapisu stojaka: ciche odrzucenie (bez komunikatu), punkty nie są pobierane.
                    return;
                }
                giveItem(player, turret);
            }
        }

        arena.addSessionPoints(player.getUniqueId(), -item.getPrice());
        if (!specificMessage) {
            Lang.sendActionBar(player, "bought");
        }
    }

    /**
     * Przywołuje wilka DOKŁADNIE 1 blok od gracza (losowy kierunek) z założoną
     * zbroją wilka. Wilk jest OSWOJONY przez gracza (zbroja wilka działa tylko
     * na oswojonych) i oznaczony tagiem sojuszniczym vd_sojusz - wieżyczki i
     * systemy pluginu nie traktują go jako wroga.
     */
    private void spawnArmoredWolf(Player player) {
        World world = player.getWorld();
        Location loc = pl.vd.summon.DefenderManager.summonSpot(player);
        Wolf wolf = world.spawn(loc, Wolf.class);
        if (wolf == null) return;
        wolf.setOwner(player); // oswojony - inaczej zbroja wilka nie działa
        wolf.setTamed(true);
        // zbroja wilka - w MC 26.2 to slot BODY w ekwipunku wilka
        wolf.getEquipment().setItem(EquipmentSlot.BODY, new ItemStack(Material.WOLF_ARMOR));
        wolf.addScoreboardTag(ArmorStandTuner.TAG_ALLIED); // po stronie gracza
        plugin.getGameManager().registerPlayerSummon(wolf, player); // zniknie po grze
        Lang.sendActionBar(player, "wolf-summoned");
    }

    /**
     * Przywołuje BAŁWANA DOKŁADNIE 1 blok od gracza (losowy kierunek) z
     * niewidoczną kolczugą (na bałwanie zbroja się nie renderuje - służy tylko
     * jako ochrona). Zamiast Cierni: mob, który zaatakuje bałwana, zostaje
     * ZAMROŻONY na 1,5 s (handler w EntityListener). Bałwan jest oznaczony
     * tagiem sojuszniczym vd_sojusz.
     */
    private void spawnThornySnowman(Player player) {
        World world = player.getWorld();
        Location loc = pl.vd.summon.DefenderManager.summonSpot(player);
        Snowman snowman = world.spawn(loc, Snowman.class);
        if (snowman == null) return;
        // niewidoczna kolczuga (bez zaklęć) - czysta ochrona
        ItemStack[] pieces = {
                new ItemStack(Material.CHAINMAIL_HELMET),
                new ItemStack(Material.CHAINMAIL_CHESTPLATE),
                new ItemStack(Material.CHAINMAIL_LEGGINGS),
                new ItemStack(Material.CHAINMAIL_BOOTS)
        };
        org.bukkit.inventory.EntityEquipment eq = snowman.getEquipment();
        eq.setItem(EquipmentSlot.HEAD, pieces[0], false);
        eq.setItem(EquipmentSlot.CHEST, pieces[1], false);
        eq.setItem(EquipmentSlot.LEGS, pieces[2], false);
        eq.setItem(EquipmentSlot.FEET, pieces[3], false);
        // nic nie wypada po śmierci
        for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            eq.setDropChance(s, 0f);
        }
        snowman.addScoreboardTag(ArmorStandTuner.TAG_ALLIED); // po stronie gracza
        plugin.getGameManager().registerPlayerSummon(snowman, player); // zniknie po grze
        Lang.sendActionBar(player, "snowman-summoned");
    }

    /**
     * Wręcza kupiony przedmiot:
     * - zbroja trafia od razu na swój slot (stara część znika),
     * - broń zastępuje trzymaną w głównej ręce broń tego samego typu (stara znika),
     * - tarcza zastępuje tarczę w drugiej ręce,
     * - reszta przedmiotów ląduje w ekwipunku jak dotychczas.
     */
    private void giveItem(Player player, ItemStack given) {
        Material mat = given.getType();
        String name = mat.name();
        if (name.endsWith("_HELMET")) { player.getInventory().setHelmet(given); return; }
        if (name.endsWith("_CHESTPLATE")) { player.getInventory().setChestplate(given); return; }
        if (name.endsWith("_LEGGINGS")) { player.getInventory().setLeggings(given); return; }
        if (name.endsWith("_BOOTS")) { player.getInventory().setBoots(given); return; }
        if (mat == Material.SHIELD) {
            // tarcza - zastąp tarczę w drugiej ręce, jeśli jest
            ItemStack off = player.getInventory().getItemInOffHand();
            if (off != null && off.getType() == Material.SHIELD) {
                player.getInventory().setItemInOffHand(given);
            } else {
                player.getInventory().addItem(given);
            }
            return;
        }
        WeaponType weapon = WeaponType.of(mat);
        if (weapon != null && isHeldWeapon(player, weapon)) {
            player.getInventory().setItemInMainHand(given);
            return;
        }
        player.getInventory().addItem(given);
    }

    /** Czy gracz trzyma w głównej ręce broń danego typu. */
    private boolean isHeldWeapon(Player player, WeaponType type) {
        ItemStack main = player.getInventory().getItemInMainHand();
        return main != null && type.matches(main.getType());
    }

    /**
     * Nakłada zaklęcie z przedmiotu sklepowego na część zbroi/narzędzie gracza.
     * Jeśli przedmiot ma już to zaklęcie, poziom jest podnoszony (ulepszenie).
     *
     * @return true jeśli ulepszenie się powiodło (punkty odjęte)
     */
    /** Cena ulepszenia zaklęcia na danym elemencie zbroi/narzędziu: 250 pkt za
     *  świeży enchant, a gdy dany element ma już to zaklęcie - +250 pkt za każdy
     *  poziom na nim (cena rośnie TYLKO tam, gdzie enchant jest nałożony). */
    public int enchantPrice(ShopItem item, ItemStack target) {
        if (item == null || item.getEnchantment() == null || target == null) {
            return ENCHANT_PRICE_BASE;
        }
        int level = target.getEnchantmentLevel(item.getEnchantment());
        return ENCHANT_PRICE_BASE + level * ENCHANT_PRICE_PER_LEVEL;
    }

    /** Ograniczenia zaklęć per broń: zwraca maksymalny poziom zaklęcia NA TEJ broni
     *  albo -1, gdy zaklęcia w ogóle nie można nałożyć; 0 = bez dodatkowego limitu
     *  (obowiązuje domyślny z configu). Zbroja i inne cele - bez ograniczeń.
     *  Reguły:
     *  - miecz: ostrość, pogromca nieumarłych, zmora stawonogów, odrzut, szczęście,
     *    szerokie ostrze, zaklęty ogień (domyślne limity),
     *  - siekiera: ostrość, szczęście; zaklęty ogień max 2, odrzut max 1,
     *  - trójząb: szczęście; ostrość max 5, zaklęty ogień max 3, zmora max 3, pogromca max 3,
     *  - łuk: moc, uderzenie, szczęście; przeszycie max 3, wielostrzał max 2,
     *  - kusza: szybkie ładowanie, przeszycie, wielostrzał, szczęście,
     *  - proca (kusza): moc, uderzenie, szczęście; wielostrzał max 3, szybkie ładowanie
     *    max 2 (proca ma bazowe I, więc można ulepszyć tylko do II). */
    private int weaponEnchantRestriction(ItemStack target, Enchantment ench) {
        Material t = target.getType();
        String key = ench.getKey().getKey();
        WeaponType wt = WeaponType.of(t);
        if (wt == WeaponType.SWORD) {
            return switch (key) {
                case "sharpness", "smite", "bane_of_arthropods", "knockback",
                     "fortune", "sweeping_edge", "fire_aspect" -> 0;
                default -> -1;
            };
        }
        if (wt == WeaponType.AXE) {
            return switch (key) {
                case "sharpness", "fortune" -> 0;
                case "fire_aspect" -> 2;
                case "knockback" -> 1;
                default -> -1;
            };
        }
        if (t == Material.TRIDENT) {
            return switch (key) {
                case "fortune" -> 0;
                case "sharpness" -> 5;
                case "fire_aspect" -> 3;
                case "bane_of_arthropods", "smite" -> 3;
                default -> -1;
            };
        }
        if (t == Material.BOW) {
            return switch (key) {
                case "power", "punch", "fortune" -> 0;
                case "piercing" -> 3;
                case "multishot" -> 2;
                default -> -1;
            };
        }
        if (t == Material.CROSSBOW) {
            if (pl.vd.listeners.ProcaListener.isProca(target)) {
                return switch (key) {
                    case "power", "punch", "fortune" -> 0;
                    case "multishot" -> 3;
                    case "quick_charge" -> 2; // bazowe I -> można ulepszyć do II
                    default -> -1;
                };
            }
            return switch (key) {
                case "quick_charge", "piercing", "multishot", "fortune" -> 0;
                default -> -1;
            };
        }
        // zbroja i inne cele - bez ograniczeń
        return 0;
    }

    public boolean enchantEquipment(Player player, Arena arena, ShopItem item, int guiSlot) {
        if (!arena.isPlaying(player)) {
            return false; // podgląd sklepu (/def shop_arena) - bez zakupów, bez komunikatu
        }
        if (!isClassRulesExempt(item)
                && !plugin.getClassRulesManager().allowsPurchase(player, item.getId())) {
            return false; // zaklęcie zastrzeżone dla innej klasy - bez pobierania punktów
        }
        int targetSlot = resolveTargetSlot(player, guiSlot);
        if (targetSlot == NOT_FOUND) {
            // puste miejsce na broń - komunikat; tło GUI (szyby/nawigacja) - nic
            if (guiSlot == ENCHANT_TRIDENT_SLOT || guiSlot == ENCHANT_SWORD_SLOT
                    || guiSlot == ENCHANT_AXE_SLOT || guiSlot == ENCHANT_BOW_SLOT
                    || guiSlot == ENCHANT_CROSSBOW_SLOT) {
                Lang.sendActionBar(player, "no-weapon");
            }
            return false;
        }
        ItemStack target = getItemAt(player, targetSlot);
        if (target == null || target.getType() == Material.AIR) {
            return false;
        }

        int price = enchantPrice(item, target);
        int points = arena.getSessionPoints(player.getUniqueId());
        if (points < price) {
            Lang.sendActionBar(player, "not-enough-points");
            return false;
        }
        Enchantment ench = item.getEnchantment();
        if (ench == null) {
            return false;
        }
        // Ograniczenia zaklęć per broń (miecz/siekiera/trójząb/łuk/kusza/proca):
        // -1 = zaklęcie niedostępne na tej broni (bez pobierania punktów),
        // >0 = dodatkowy limit poziomu NA TEJ broni (np. Przeszycie na łuku max 3).
        int weaponMax = weaponEnchantRestriction(target, ench);
        if (weaponMax < 0) {
            Lang.sendActionBar(player, "enchant-not-for-weapon");
            return false;
        }

        int maxLevel = enchantMaxLevel(item);
        if (weaponMax > 0) maxLevel = Math.min(maxLevel, weaponMax);
        int currentLevel = target.getEnchantmentLevel(ench);
        if (currentLevel >= maxLevel) {
            Lang.sendActionBar(player, "max-enchant-level", maxLevel);
            return false;
        }
        int newLevel = currentLevel + item.getEnchantLevel();
        if (newLevel > maxLevel) {
            Lang.sendActionBar(player, "max-level", maxLevel);
            return false;
        }
        target.addUnsafeEnchantment(ench, newLevel);

        setItemAt(player, targetSlot, target);

        arena.addSessionPoints(player.getUniqueId(), -price);
        Lang.sendActionBar(player, "enchant-upgraded", prettify(ench.getKey().getKey()), toRoman(newLevel));
        return true;
    }

    /** Zaklęcia, które można nałożyć TYLKO na broń (Szczęście/Fortune, Uderzenie/Punch):
     *  w GUI wyboru celu sloty zbroi są zablokowane, a kliknięcia w nie są ignorowane -
     *  książka nie może się zmarnować na zbroję (Fortune na zbroi nic nie daje). */
    public boolean isWeaponRestrictedEnchant(ShopItem item) {
        if (item == null || item.getType() != ShopItemType.ENCHANT || item.getEnchantment() == null) {
            return false;
        }
        String key = item.getEnchantment().getKey().getKey();
        return key.equals("fortune") || key.equals("punch");
    }

    /** Czy slot w GUI wyboru celu zaklęcia to zbroja (helmet/klata/spodnie/buty)? */
    public boolean isEnchantArmorSlot(int slot) {
        return slot == ENCHANT_HELMET_SLOT || slot == ENCHANT_CHEST_SLOT
                || slot == ENCHANT_LEGS_SLOT || slot == ENCHANT_BOOTS_SLOT;
    }

    /** Czy slot w GUI wyboru celu zaklęcia to broń (trójząb/miecz/siekiera/łuk/kusza)? */
    public boolean isEnchantWeaponSlot(int slot) {
        return slot == ENCHANT_TRIDENT_SLOT || slot == ENCHANT_SWORD_SLOT
                || slot == ENCHANT_AXE_SLOT || slot == ENCHANT_BOW_SLOT
                || slot == ENCHANT_CROSSBOW_SLOT;
    }

    /** Zaklęcia, które można nałożyć TYLKO na zbroję (Ochrona, Ochrona przed ogniem,
     *  Ochrona przed pociskami, Ciernie): w GUI wyboru celu sloty broni są
     *  zablokowane, a kliknięcia w nie są ignorowane. */
    public boolean isArmorOnlyEnchant(ShopItem item) {
        if (item == null || item.getType() != ShopItemType.ENCHANT || item.getEnchantment() == null) {
            return false;
        }
        String key = item.getEnchantment().getKey().getKey();
        return key.equals("protection") || key.equals("fire_protection")
                || key.equals("projectile_protection") || key.equals("thorns");
    }

    /**
     * Zaklęcia wymagające konkretnej broni (Sweeping Edge -> miecz): kupują się bez
     * GUI wyboru części - zaklęcie nakłada się od razu na właściwą broń gracza.
     * (Punch jest tu celowo NIE - obsługuje go GUI, bo da się go nałożyć na łuk
     * i procę, a reguły per broń decydują, gdzie wolno.) Gdy gracz nie ma wymaganej
     * broni, dostaje tylko komunikat (bez zakupu i bez punktów).
     */
    public boolean isWeaponOnlyEnchant(ShopItem item) {
        if (item == null || item.getType() != ShopItemType.ENCHANT || item.getEnchantment() == null) {
            return false;
        }
        return item.getEnchantment().getKey().getKey().equals("sweeping_edge");
    }

    /** Komunikat o braku wymaganej broni dla zaklęcia wymagającego broni (albo null). */
    public String missingWeaponMessage(Player player, ShopItem item) {
        if (item == null || item.getType() != ShopItemType.ENCHANT || item.getEnchantment() == null) {
            return null;
        }
        String key = item.getEnchantment().getKey().getKey();
        if (key.equals("sweeping_edge")) {
            return Lang.get(player, "need-sword");
        }
        return null;
    }

    /**
     * Kupuje zaklęcie wymagające broni i nakłada je bezpośrednio na właściwą broń
     * (Sweeping Edge -> miecz). Gdy gracz nie ma wymaganej broni - komunikat
     * i false (punkty nie pobrane). Zwraca true, gdy zakup się powiódł.
     */
    public boolean purchaseWeaponEnchant(Player player, Arena arena, ShopItem item) {
        if (item == null || item.getType() != ShopItemType.ENCHANT || item.getEnchantment() == null) {
            return false;
        }
        String key = item.getEnchantment().getKey().getKey();
        int guiSlot;
        if (key.equals("sweeping_edge")) {
            guiSlot = ENCHANT_SWORD_SLOT;
        } else {
            return false;
        }
        if (resolveTargetSlot(player, guiSlot) == NOT_FOUND) {
            pl.vd.util.AntiSpam.sendActionBar(player, missingWeaponMessage(player, item));
            return false;
        }
        return enchantEquipment(player, arena, item, guiSlot);
    }

    /**
     * Czy zaklęcie ma tylko jeden możliwy cel (łagodny upadek -> buty) i jest
     * kupowane bez otwierania GUI wyboru części.
     */
    public boolean isBootsOnlyEnchant(ShopItem item) {
        return item.getType() == ShopItemType.ENCHANT
                && item.getEnchantment() != null
                && item.getEnchantment().getKey().getKey().equals("feather_falling");
    }

    /**
     * Kupuje zaklęcie i nakłada je bezpośrednio na buty (łagodny upadek),
     * bez otwierania GUI wyboru części.
     */
    public void purchaseEnchantToBoots(Player player, Arena arena, ShopItem item) {
        ItemStack boots = player.getInventory().getBoots();
        if (boots == null || boots.getType() == Material.AIR) {
            Lang.sendActionBar(player, "must-wear-boots");
            return;
        }
        enchantEquipment(player, arena, item, ENCHANT_BOOTS_SLOT);
    }

    /**
     * Slot ekwipunku wskazany slotem GUI zaklęć: zbroja = raw slot (36-39),
     * broń = miejsce znalezione w rękach/ekwipunku, tło = NOT_FOUND.
     */
    private int resolveTargetSlot(Player player, int guiSlot) {
        return switch (guiSlot) {
            case ENCHANT_HELMET_SLOT -> 39;
            case ENCHANT_CHEST_SLOT  -> 38;
            case ENCHANT_LEGS_SLOT   -> 37;
            case ENCHANT_BOOTS_SLOT  -> 36;
            case ENCHANT_TRIDENT_SLOT  -> findWeaponSlot(player, Material.TRIDENT);
            case ENCHANT_SWORD_SLOT    -> findWeaponSlot(player, WeaponType.SWORD);
            case ENCHANT_AXE_SLOT      -> findWeaponSlot(player, WeaponType.AXE);
            case ENCHANT_BOW_SLOT      -> findWeaponSlot(player, Material.BOW);
            case ENCHANT_CROSSBOW_SLOT -> findWeaponSlot(player, Material.CROSSBOW);
            default -> NOT_FOUND;
        };
    }

    // Miejsca w ekwipunku używane przez findWeaponSlot/getItemAt/setItemAt
    private static final int MAIN_HAND = -1;  // główna ręka
    private static final int OFF_HAND  = -2;  // druga ręka
    private static final int NOT_FOUND = -3;  // brak przedmiotu

    /** Szuka broni danego typu: najpierw ręce, potem sloty ekwipunku 0-35. */
    private int findWeaponSlot(Player player, WeaponType type) {
        ItemStack main = player.getInventory().getItemInMainHand();
        if (main != null && type.matches(main.getType())) return MAIN_HAND;
        ItemStack off = player.getInventory().getItemInOffHand();
        if (off != null && type.matches(off.getType())) return OFF_HAND;
        for (int i = 0; i < 36; i++) {
            ItemStack it = player.getInventory().getItem(i);
            if (it != null && type.matches(it.getType())) return i;
        }
        return NOT_FOUND;
    }

    /** Szuka przedmiotu danego materiału (np. samego łuku albo samej kuszy):
     *  najpierw ręce, potem sloty ekwipunku 0-35. */
    private int findWeaponSlot(Player player, Material material) {
        ItemStack main = player.getInventory().getItemInMainHand();
        if (main != null && main.getType() == material) return MAIN_HAND;
        ItemStack off = player.getInventory().getItemInOffHand();
        if (off != null && off.getType() == material) return OFF_HAND;
        for (int i = 0; i < 36; i++) {
            ItemStack it = player.getInventory().getItem(i);
            if (it != null && it.getType() == material) return i;
        }
        return NOT_FOUND;
    }

    private ItemStack getItemAt(Player player, int slot) {
        if (slot == MAIN_HAND) return player.getInventory().getItemInMainHand();
        if (slot == OFF_HAND) return player.getInventory().getItemInOffHand();
        if (slot < 0) return null;
        return player.getInventory().getItem(slot);
    }

    private void setItemAt(Player player, int slot, ItemStack item) {
        if (slot == MAIN_HAND) player.getInventory().setItemInMainHand(item);
        else if (slot == OFF_HAND) player.getInventory().setItemInOffHand(item);
        else player.getInventory().setItem(slot, item);
    }

    // ---------- Budowanie przedmiotów GUI ----------

    private void fillGlass(Inventory inv) {
        fillGlass(inv, Material.GRAY_STAINED_GLASS_PANE);
    }

    private void fillGlass(Inventory inv, Material glassMaterial) {
        ItemStack glass = createGlass(glassMaterial);
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, glass);
        }
    }

    private ItemStack createGlass() {
        return createGlass(Material.GRAY_STAINED_GLASS_PANE);
    }

    private ItemStack createGlass(Material glassMaterial) {
        return new ItemBuilder(glassMaterial, 1).name(" ").build();
    }

    private ItemStack createStar(Player player) {
        return new ItemBuilder(Material.NETHER_STAR, 1)
                .name(Lang.get(player, "set-exit-shop"))
                .lore(List.of(Lang.get(player, "set-exit-shop-lore")))
                .build();
    }

    private ItemStack createSettingsIcon(Player player) {
        return new ItemBuilder(Material.ENDER_EYE, 1)
                .name(Lang.get(player, "set-settings-icon"))
                .lore(List.of(Lang.get(player, "set-settings-icon-lore")))
                .build();
    }

    private ItemStack createTurretExit(Player player) {
        return new ItemBuilder(Material.NETHER_STAR, 1)
                .name(Lang.get(player, "shop-exit"))
                .lore(List.of(Lang.get(player, "shop-exit-lore")))
                .build();
    }

    private ItemStack createUpgradeButton(Player player, Material material, String name, int price) {
        return new ItemBuilder(material, 1)
                .name(name)
                .lore(List.of(Lang.get(player, "shop-price", price), "", Lang.get(player, "shop-click-upgrade")))
                .build();
    }

    private ItemStack createBackButton(Player player) {
        // Powrót w każdej kategorii: nether star (jak gwiazda wyjścia w menu sklepu);
        // strzałki służą teraz wyłącznie do przewijania stron.
        return new ItemBuilder(Material.NETHER_STAR, 1)
                .name(Lang.get(player, "set-back"))
                .lore(List.of(Lang.get(player, "set-back-shop-lore")))
                .build();
    }

    private ItemStack createCustomArrowsButton(Player player) {
        // Nowa strzała (slot 50, o 1 więcej niż nether star) w kategorii Strzały -
        // otwiera podmenu „Strzały - Niestandardowe Strzały" (zwykła i widmowa
        // strzała na jego drugiej stronie).
        return new ItemBuilder(Material.ARROW, 1)
                .name(Lang.get(player, "shop-next-page"))
                .lore(List.of(Lang.get(player, "shop-scroll-lore")))
                .build();
    }

    private ItemStack createSettingsBackButton(Player player) {
        // Powrót z podmenu ustawień (np. dźwięki): nether star jak w kategoriach,
        // ale wraca do panelu USTAWIENIA.
        return new ItemBuilder(Material.NETHER_STAR, 1)
                .name(Lang.get(player, "set-back"))
                .lore(List.of(Lang.get(player, "set-back-settings-lore")))
                .build();
    }

    private ItemStack createExitSettingsButton(Player player) {
        // Wyjście z panelu USTAWIENIA: nether star zamyka ustawienia (nie wraca do sklepu).
        return new ItemBuilder(Material.NETHER_STAR, 1)
                .name(Lang.get(player, "set-exit"))
                .lore(List.of(Lang.get(player, "set-exit-lore")))
                .build();
    }

    private ItemStack createPrevCategoryButton(Player player) {
        // Strzałka na lewo od nether stara: przechodzi do poprzedniej kategorii ustawień.
        return new ItemBuilder(Material.ARROW, 1)
                .name(Lang.get(player, "set-prev-category"))
                .lore(List.of(Lang.get(player, "set-prev-category-lore")))
                .build();
    }

    private ItemStack createNextCategoryButton(Player player) {
        // Strzałka na prawo od nether stara: przechodzi do następnej kategorii ustawień.
        return new ItemBuilder(Material.ARROW, 1)
                .name(Lang.get(player, "set-next-category"))
                .lore(List.of(Lang.get(player, "set-next-category-lore")))
                .build();
    }

    private ItemStack createAchievementsIcon(Player player) {
        // Ikona osiągnięć w głównym menu sklepu (obok Ustawień). GUI nie jest
        // jeszcze zbudowane - kliknięcie pokazuje zapowiedź (achievements-soon).
        return new ItemBuilder(Material.EXPERIENCE_BOTTLE, 1)
                .name(Lang.get(player, "achievements-title"))
                .build();
    }

    private ItemStack createPageArrow(Player player, boolean next) {
        return new ItemBuilder(Material.ARROW, 1)
                .name(Lang.get(player, next ? "shop-next-page" : "shop-prev-page"))
                .lore(List.of(Lang.get(player, "shop-scroll-lore")))
                .build();
    }

    /** Wskaźnik strony (slot 47): szara szyba z PUSTĄ nazwą („ ", jak tło), żeby
     *  NIE pokazywała domyślnej nazwy „Gray Stained Glass Pane" jak zwykły
     *  przedmiot - ma wyglądać jak reszta tła. */
    private ItemStack createPageInfo(Player player, int page, int pages) {
        return new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE, 1).name(" ").build();
    }

    private ItemStack createPointsItem(Player player, int points) {
        return new ItemBuilder(Material.EMERALD, 1)
                .name(Lang.get(player, "shop-points-name"))
                .lore(List.of(Lang.get(player, "shop-points-lore", points)))
                .build();
    }

    private ItemStack createCategoryIcon(Player player, ShopCategory category) {
        if (category == ShopCategory.POTIONS) {
            // Ikona kategorii = KOLOROWA mikstura (czerwona - leczenie), a nie „butelka
            // z wodą" (zwykły Potion bez koloru).
            ItemStack potion = new ItemStack(Material.POTION, 1);
            PotionMeta pm = (PotionMeta) potion.getItemMeta();
            if (pm != null) {
                pm.setBasePotionType(PotionType.HEALING);
                pm.setDisplayName(category.localizedName(player));
                pm.setLore(List.of(Lang.get(player, "shop-click-category")));
                potion.setItemMeta(pm);
            }
            return potion;
        }
        return new ItemBuilder(category.getIcon(), 1)
                .name(category.localizedName(player))
                .lore(List.of(Lang.get(player, "shop-click-category")))
                .build();
    }

    /** Numer znaku, w którym zaczyna się ✔/✖ (wspólna kolumna dla wszystkich klas). */
    private static final int CLASS_LORE_COLUMN = 16;

    /**
     * Wiersze „klasa + ✔/✖” dla przedmiotu. Pusta lista, gdy żadna klasa nie ma zakazu
     * (przedmiot jest dla wszystkich i nie warto rozwijać opisu).
     */
    private List<String> createClassAvailabilityLore(Player player, ShopItem item) {
        List<String> rows = new ArrayList<>();
        boolean anyDenied = false;
        // Poza areną nie podkreślamy żadnej klasy: wybór z poprzedniej gry nie ma
        // znaczenia w publicznym podglądzie sklepu (/vd shop_arena).
        String viewerKit = isPlaying(player)
                ? plugin.getKitManager().getChosenKitId(player)
                : null;

        for (String kitId : KIT_SLOTS_ORDER) {
            var kit = plugin.getKitManager().getKit(kitId);
            if (kit == null) continue; // klasa w pliku, ale nie ma jej w kodzie
            boolean allowed = plugin.getClassRulesManager()
                    .ruleFor(kitId).allowsPurchase(item.getId());
            if (!allowed) anyDenied = true;

            // localizedName ma własne kolory klas (np. §cWojownik) - nazwa dostaje
            // kolor nagłówka (§8, ciemny szary), a własna klasa jest pogrubiona.
            String name = kit.localizedName(player).replaceAll("§.", "");
            String nameColor = kitId.equalsIgnoreCase(viewerKit) ? "§8§l" : "§8";
            // Znacznik zawsze startuje w tej samej kolumnie: krótsza nazwa dostaje
            // więcej spacji, więc ✔/✖ stoją równo nad i pod sobą. Spacje po §r
            // nie są pogrubione, żeby własna klasa nie przesuwała kolumny.
            int gap = Math.max(2, CLASS_LORE_COLUMN - name.length());
            rows.add(nameColor + name + "§r" + " ".repeat(gap)
                    + (allowed ? "§a✔" : "§c✖"));
        }
        return anyDenied ? rows : List.of();
    }

    /**
     * Kategorie, w których klasy nie ograniczają zakupów: Jedzenie, Inne i Zaklęcia.
     * Tam każda klasa kupuje bez listy klas i bez blokady - także przyszłe pozycje.
     */
    private boolean isClassRulesExempt(ShopItem item) {
        return inCategory(item, ShopCategory.FOOD)
                || inCategory(item, ShopCategory.OTHER)
                || inCategory(item, ShopCategory.ENCHANTS);
    }

    private boolean inCategory(ShopItem item, ShopCategory category) {
        return item != null && categoryItems.getOrDefault(category, Map.of()).containsValue(item);
    }

    /** Czy gracz aktualnie gra na dowolnej arenie (a nie tylko ogląda sklep)? */
    private boolean isPlaying(Player player) {
        for (Arena arena : plugin.getArenaManager().getArenas().values()) {
            if (arena.isPlaying(player)) return true;
        }
        return false;
    }

    /** Nazwa klasy gracza w jego języku (opis przedmiotu zastrzeżonego dla klasy). */
    private String className(Player player) {
        String kitId = plugin.getKitManager().getChosenKitId(player);
        var kit = plugin.getKitManager().getKit(kitId);
        return kit != null ? kit.localizedName(player) : kitId;
    }

    private ItemStack createShopItemIcon(ShopItem item, Player player) {
        List<String> lore = new ArrayList<>();
        // Zaklęcia NIE pokazują ceny na książce - cena jest dopiero w GUI wyboru
        // elementu zbroi (250 pkt, +250 za każdy poziom już na danym elemencie).
        if (item.getType() != ShopItemType.ENCHANT) {
            lore.add(Lang.get(player, "shop-price", item.getPrice()));
        }
        switch (item.getType()) {
            case ITEM    -> lore.add(Lang.get(player, "shop-amount", item.getAmount()));
            case EFFECT  -> {
                // Bez podtytułów poziomów - tylko cena i "Kliknij, aby kupić".
            }
            case ENCHANT -> lore.add(Lang.get(player, "shop-max-level", enchantMaxLevel(item)));
            case MINE    -> lore.add(Lang.get(player, "shop-mine-lore"));
            case TURRET  -> {
                // Statystyki z pliku presetu (hp/damage/range); brak wpisu =
                // domyślne (250 HP, 3 obrażeń, zasięg 8). Jednolity podtytuł dla
                // zwykłej wieżyczki i wieżyczki maga: Zasięg / Obrażenia / Życie.
                StandPreset preset = plugin.getStandPresetManager() != null
                        ? plugin.getStandPresetManager().loadPreset(item.getId())
                        : null;
                double hp = preset != null && preset.hp > 0 ? preset.hp : 250;
                double dmg = preset != null && preset.damage > 0 ? preset.damage : 3;
                double range = preset != null && preset.range > 0 ? preset.range : 8;
                lore.add(Lang.get(player, "shop-turret-range", fmt(range)));
                lore.add(Lang.get(player, "shop-turret-damage", fmt(dmg)));
                lore.add(Lang.get(player, "shop-turret-hp", fmt(hp)));
            }
            case POTION  -> {
                // Bez podtytułów - tylko cena i „Kliknij, aby kupić" (jak EFFECT).
            }
            case FIREWORK -> {
                lore.add(Lang.get(player, "shop-amount", item.getAmount()));
            }
        }
        // Pod przedmiotem: która klasa może go kupić (✔ zielony / ✖ czerwony),
        // nazwy klas szare, własna klasa pogrubiona. Tylko gdy ktoś ma zakaz -
        // przy przedmiotach dla wszystkich nie zaśmiecamy opisu siedmioma liniami.
        boolean classRulesApply = !isClassRulesExempt(item);
        boolean playing = isPlaying(player);
        List<String> classLore = classRulesApply
                ? createClassAvailabilityLore(player, item)
                : List.of();
        if (!classLore.isEmpty()) {
            lore.add("");
            lore.addAll(classLore);
        }
        if (playing && classRulesApply
                && !plugin.getClassRulesManager().allowsPurchase(player, item.getId())) {
            // Widoczne, ale jasno oznaczone - gracz wie, że to pozycja innej klasy.
            lore.add("");
            lore.add(Lang.get(player, "shop-class-locked", className(player)));
        } else {
            lore.add("");
            lore.add(Lang.get(player, "shop-click-buy"));
        }

        String name = switch (item.getType()) {
            case ITEM    -> item.getDisplayName() != null ? item.getDisplayName()
                    : ("proca".equals(item.getId()) ? "§fProca" : "§f" + prettify(item.getMaterial().name()));
            case EFFECT  -> item.getDisplayName() != null ? item.getDisplayName()
                    : "§f" + prettify(item.getEffectType().getName());
            case ENCHANT -> "§f" + prettify(item.getEnchantment().getKey().getKey());
            case MINE    -> Lang.get(player, "shop-mine");
            case TURRET  -> item.getDisplayName() != null ? item.getDisplayName()
                    : Lang.get(player, "shop-item-wiezyczka");
            case POTION  -> ""; // nazwa z bazowego typu mikstury (waniliowa)
            case FIREWORK -> Lang.get(player, "shop-firework");
        };
        // Własne nazwy z config.yml (name:) i specjalne przedmioty (np. proca) -
        // tłumaczone po id przedmiotu; brak wpisu w słowniku = nazwa z configu.
        if (item.getId() != null) {
            name = Lang.getOr(player, "shop-item-" + item.getId(), name);
        }

        if (item.getType() == ShopItemType.EFFECT) {
            // Mikstura w kolorze efektu (jak w vanilla Minecraft) zamiast bezbarwnej "wody":
            // kolor z PotionEffectType.getColor() - te same kolory co cząsteczki efektu w grze.
            ItemStack potion = new ItemStack(Material.POTION, 1);
            PotionMeta pm = (PotionMeta) potion.getItemMeta();
            if (pm != null) {
                Color c = item.getEffectType() != null ? item.getEffectType().getColor() : null;
                if (c != null) pm.setColor(c);
                pm.setDisplayName(name);
                pm.setLore(lore);
                potion.setItemMeta(pm);
            }
            return potion;
        }
        if (item.getType() == ShopItemType.POTION) {
            // Ikona = ta sama waniliowa mikstura/strzała, co dostanie gracz (efekty
            // jak w vanilla, bez podtytułów); splash/material z configu. Nazwa z
            // tłumaczeń (shop-item-<id>) - waniliowa nazwa nie reaguje na język
            // ustawiony w pluginie, tylko na język klienta.
            Material mat = item.getMaterial() != null ? item.getMaterial()
                    : (item.isSplash() ? Material.SPLASH_POTION : Material.POTION);
            ItemStack potion = new ItemStack(mat, 1);
            PotionMeta pm = (PotionMeta) potion.getItemMeta();
            if (pm != null) {
                if (item.getPotionType() != null) pm.setBasePotionType(item.getPotionType());
                if (!name.isEmpty()) pm.setDisplayName(name);
                pm.setLore(lore);
                potion.setItemMeta(pm);
            }
            return potion;
        }
        if (item.getType() == ShopItemType.FIREWORK) {
            // Ikona = zwykły fajerwerk BEZ FireworkMeta: dzięki temu nie ma waniliowego
            // podtytułu „czas lotu: X" - wysokość lotu i tak widać w lore („Lot: X").
            return new ItemBuilder(Material.FIREWORK_ROCKET, 1).name(name).lore(lore).build();
        }
        ItemBuilder icon = new ItemBuilder(item.getIconMaterial(), 1).name(name).lore(lore);
        // Karabin snajperski: custom_model_data = tekstura z resource packa
        if ("karabin".equals(item.getId())) {
            icon.customModelData(pl.vd.weapons.SniperRifleManager.CUSTOM_MODEL_DATA);
        }
        return icon.build();
    }

    private ItemStack createEnchantBookInfo(Player player, ShopItem item) {
        // nazwa zaklęcia w języku gracza (shop-item-<id> w Lang) - odcinamy kod
        // koloru, bo jasnoniebieski (§b) doklejamy niżej (kolor jak w vanilla)
        String enchName = item.getEnchantment() != null
                ? Lang.getOr(player, "shop-item-" + item.getId(),
                        prettify(item.getEnchantment().getKey().getKey())).replaceFirst("§[0-9a-fk-or]", "")
                : "???";
        // Cena NIE jest pokazywana na książkach - widać ją dopiero w GUI wyboru
        // elementu zbroi (na każdym elemencie: 250 pkt, +250 za każdy poziom już na nim).
        return new ItemBuilder(Material.ENCHANTED_BOOK, 1)
                .name("§b" + enchName + " " + toRoman(item.getEnchantLevel()))
                .lore(List.of(
                        Lang.get(player, "shop-enchant-hint1"),
                        Lang.get(player, "shop-enchant-hint2"),
                        "",
                        Lang.get(player, "shop-max-level", enchantMaxLevel(item))))
                .build();
    }

    private ItemStack createEquipmentSlotIcon(Player player, ItemStack item, ShopItem enchantItem, String label) {
        if (item == null || item.getType() == Material.AIR) {
            // puste miejsce na zbroję/narzędzie - BIAŁA szyba z nazwą (odróżnia się od szarego tła)
            return new ItemBuilder(Material.WHITE_STAINED_GLASS_PANE, 1)
                    .name("§8" + label)
                    .lore(List.of(Lang.get(player, "shop-no-item")))
                    .build();
        }
        ItemStack copy = item.clone();
        ItemMeta meta = copy.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add("");
            // cena ulepszenia TEGO elementu: 250 pkt, a gdy ma już to zaklęcie -
            // rośnie o 250 pkt za każdy poziom na nim (reszta elementów: 250)
            lore.add(Lang.get(player, "shop-price", enchantPrice(enchantItem, item)));
            lore.add(Lang.get(player, "shop-click-enchant"));
            meta.setLore(lore);
            copy.setItemMeta(meta);
        }
        return copy;
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

    /** 250.0 -> "250", 3.2 -> "3,2" (polski przecinek dziesiętny). */
    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v)
                : String.format(Locale.ROOT, "%.1f", v).replace('.', ',');
    }

}
