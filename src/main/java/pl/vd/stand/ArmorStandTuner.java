package pl.vd.stand;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.ArmorStand;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import pl.vd.VillagerDefensePlugin;
import pl.vd.type.Faction;
import pl.vd.util.AntiSpam;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Narzędzie do strojenia wyglądu stojaka (armor stand) "na żywo" w grze.
 *
 * Komenda /stojak stawia stojak oznaczony jako edytowalny. Gracz klika go
 * przedmiotami, żeby obracać części, przełączać opcje i wypisywać wartości:
 *
 *   STICK        - zmiana strojonej części (głowa/ciało/ręce/nogi/stojak/wysokość, potem doczepione części)
 *                  (SHIFT = cofanie wyboru do poprzedniej)
 *   SUGAR        - zmiana osi obrotu (X/Y/Z) albo trybu doczepionej części
 *                  (WYSOKOŚĆ/PRZESUNIĘCIE-X/PRZESUNIĘCIE-Z/SKALA/SKRĘT/OBRÓT/POCHYLENIE/LUSTRO/ODBICIE/ŚWIATŁO/GRUPA/ROZCIĄGNIJ-Y/ROZCIĄGNIJ-X/ROZCIĄGNIJ-Z)
 *                  (SHIFT = cofanie do poprzedniej opcji)
 *   BLAZE_ROD    - +10° / +skala / +wysokość (z shiftem drobniej)
 *   BONE         - -10° / -skala / -wysokość (z shiftem drobniej)
 *   FEATHER      - zmniejsz WSZYSTKIE części (prawy klik; SHIFT = drobniej), lewy klik = powiększ
 *   GUNPOWDER    - przełącz widoczność korpusu
 *   BLAZE_POWDER - przełącz ręce
 *   REDSTONE     - przełącz podświetlanie (glow) stojaka i części (SHIFT = tylko części)
 *   PAPER        - wypisuje aktualne wartości na czat
 *   BARRIER      - usuwa wybraną doczepioną część albo cały stojak
 *
 * Doczepione części (BlockDisplay/ItemDisplay jako "pasażerowie" stojaka) mają
 * własną skalę, obrót/pochylenie i przesunięcie - patrz /stojak part <blok>.
 */
public class ArmorStandTuner {

    public enum Part {
        HEAD("GŁOWA"), BODY("CIAŁO"), RIGHT_ARM("PRAWA RĘKA"), LEFT_ARM("LEWA RĘKA"),
        RIGHT_LEG("PRAWA NOGA"), LEFT_LEG("LEWA NOGA"),
        STAND_YAW("STOJAK-YAW"), STAND_PITCH("STOJAK-PITCH"), HEIGHT("WYSOKOŚĆ");

        private final String label;

        Part(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** Domyślna skala nowo doczepianego bloku (o 3/4 mniejszy od pełnego). */
    private static final double DEFAULT_PART_SCALE = 0.25;

    /** Doczepiona do stojaka część (BlockDisplay lub ItemDisplay) - "pasażer" stojaka. */
    public static class DisplayPart {
        private Display display;
        public double scaleX = 1, scaleY = 1, scaleZ = 1;
        public double rotX, rotY, rotZ; // stopnie
        public double tx, ty, tz; // przesunięcie względem stojaka
        /** Część obrotowa wieżyczki: obraca się wokół osi pionowej stojaka (górna część). */
        public boolean turret;
        /** Pełna jasność części (ŚWIATŁO w SUGAR) - część wygląda jak rozświetlona. */
        public boolean bright;

        DisplayPart(Entity display) {
            this.display = (Display) display;
            // flagi przetrwają restart: zapisane w PDC encji części
            Byte t = display.getPersistentDataContainer().get(KEY_TURRET, PersistentDataType.BYTE);
            this.turret = t != null && t == 1;
            Byte b = display.getPersistentDataContainer().get(KEY_BRIGHT, PersistentDataType.BYTE);
            this.bright = b != null && b == 1;
        }

        /** Nakłada jasność części (ŚWIATŁO): pełna jasność albo normalne światło. */
        private void applyBrightness() {
            if (display == null) return;
            display.setBrightness(bright ? new Display.Brightness(15, 15) : null);
        }

        /**
         * Nakłada transformację OBRÓCONĄ o kąt wokół pionowej osi stojaka (0,0,0),
         * nie ruszając zapisanych wartości (tx/rot...) - górna część wieżyczki.
         * Pozycja krąży wokół osi, a orientacja części składa się z obrotu o kąt
         * i własnego obrotu części: R = Ry(kąt) * R(cześć).
         */
        public void applyWithYaw(double deg) {
            applyWithYaw(deg, 0);
        }

        /** Jak {@link #applyWithYaw(double)}, ale z ODRZUTEM: część cofa się do tyłu
         *  (wzdłuż kierunku strzału) o podaną głębokość w blokach. */
        public void applyWithYaw(double deg, double recoil) {
            double a = Math.toRadians(deg);
            double cos = Math.cos(a), sin = Math.sin(a);
            // odrzut: do tyłu względem obróconego przodu (kierunek strzału)
            double rx = -recoil * sin;
            double rz = -recoil * cos;
            Vector3f translation = new Vector3f(
                    (float) (tx * cos + tz * sin + rx),
                    (float) ty,
                    (float) (-tx * sin + tz * cos + rz));
            Vector3f scale = new Vector3f((float) scaleX, (float) scaleY, (float) scaleZ);
            Quaternionf base = new Quaternionf().rotationXYZ(
                    (float) Math.toRadians(rotX), (float) Math.toRadians(rotY), (float) Math.toRadians(rotZ));
            Quaternionf q = new Quaternionf().rotationY((float) a).mul(base);
            AxisAngle4f rot = new AxisAngle4f(q);
            display.setTransformation(new Transformation(
                    translation, rot, scale, new AxisAngle4f(0f, 0f, 0f, 1f)));
            applyBrightness();
        }

        /**
         * Nakłada transformację z NAŁOŻONĄ animacją (unoszenie w górę/dół + lekki
         * obrót w lewo wokół osi Y) BEZ zmiany zapisanych wartości (tx/ty/rot...).
         * Animacja to czysta nakładka: pozycja = zapisana + odchylenie Y, obrót =
         * zapisany z pliku * obrót animacji. Dzięki temu /stojak save i restart
         * zawsze wracają do stanu bazowego z pliku, a animacja nie psuje zapisu.
         */
        public void applyAnimated(double yOffset, double degX, double degY, double degZ) {
            Vector3f translation = new Vector3f((float) tx, (float) (ty + yOffset), (float) tz);
            Vector3f scale = new Vector3f((float) scaleX, (float) scaleY, (float) scaleZ);
            Quaternionf base = new Quaternionf().rotationXYZ(
                    (float) Math.toRadians(rotX), (float) Math.toRadians(rotY), (float) Math.toRadians(rotZ));
            Quaternionf spin = new Quaternionf().rotationXYZ(
                    (float) Math.toRadians(degX), (float) Math.toRadians(degY), (float) Math.toRadians(degZ));
            Quaternionf q = spin.mul(base);
            AxisAngle4f rot = new AxisAngle4f(q);
            display.setTransformation(new Transformation(
                    translation, rot, scale, new AxisAngle4f(0f, 0f, 0f, 1f)));
            applyBrightness();
        }

        /**
         * Podmienia encję części (np. BlockDisplay <-> ItemDisplay przy zmianie
         * materiału), zachowując skalę/obrót/pozycję (transformację nakłada apply).
         */
        public void replaceDisplay(Display newDisplay) {
            this.display = newDisplay;
            apply();
        }

        /** viewRange Display: 150 bloków (domyślnie 1.0 = 64 bloki; 150/64 ≈ 2.34). */
        private static final float VIEW_RANGE_150 = 150f / 64f;

        public void apply() {
            Vector3f translation = new Vector3f((float) tx, (float) ty, (float) tz);
            Vector3f scale = new Vector3f((float) scaleX, (float) scaleY, (float) scaleZ);
            Quaternionf quat = new Quaternionf().rotationXYZ(
                    (float) Math.toRadians(rotX), (float) Math.toRadians(rotY), (float) Math.toRadians(rotZ));
            AxisAngle4f rot = new AxisAngle4f(quat);
            display.setTransformation(new Transformation(
                    translation, rot, scale, new AxisAngle4f(0f, 0f, 0f, 1f)));
            display.setViewRange(VIEW_RANGE_150);
            applyBrightness();
        }

        /**
         * Nakłada transformację ze SKALĄ POMNOŻONĄ o współczynnik (0..1) - do
         * animacji przejścia między stanami. Nie zmienia zapisanych wartości
         * (scaleX/...), więc po animacji wystarczy zwykłe apply().
         */
        public void applyTemporaryScale(double factor) {
            Vector3f translation = new Vector3f((float) tx, (float) ty, (float) tz);
            Vector3f scale = new Vector3f(
                    (float) (scaleX * factor), (float) (scaleY * factor), (float) (scaleZ * factor));
            Quaternionf quat = new Quaternionf().rotationXYZ(
                    (float) Math.toRadians(rotX), (float) Math.toRadians(rotY), (float) Math.toRadians(rotZ));
            AxisAngle4f rot = new AxisAngle4f(quat);
            display.setTransformation(new Transformation(
                    translation, rot, scale, new AxisAngle4f(0f, 0f, 0f, 1f)));
            applyBrightness();
        }

        public Material material() {
            if (display instanceof BlockDisplay bd) return bd.getBlock().getMaterial();
            if (display instanceof ItemDisplay id) return id.getItemStack().getType();
            return Material.AIR;
        }

        /**
         * Tworzy KLON części (ta sama encja Display: blok albo przedmiot, pozycja,
         * obrót, skala) i przypina go do stojaka jako pasażera - do trybu
         * zamiatającego zegara (/stojak zegar tryb zamiatajacy): na granicy każdej
         * ćwiartki „stara" wskazówka znika, a w tym samym miejscu pojawia się nowa
         * (klon) i obraca się dalej swobodnie parametrem SKRĘT. Klon startuje
         * UKRYTY (daleko w dół, poza zasięgiem widzenia), żeby nie było widać
         * teleportu. Zwraca null, gdy części nie da się sklonować.
         */
        public DisplayPart clonePart(ArmorStand stand) {
            if (display == null || stand == null || stand.isDead()) return null;
            Display clone;
            if (display instanceof BlockDisplay bd) {
                BlockDisplay c = stand.getWorld().spawn(stand.getLocation(), BlockDisplay.class);
                c.setBlock(bd.getBlock());
                clone = c;
            } else if (display instanceof ItemDisplay id) {
                ItemDisplay c = stand.getWorld().spawn(stand.getLocation(), ItemDisplay.class);
                ItemStack item = id.getItemStack();
                c.setItemStack(item == null ? null : item.clone());
                c.setItemDisplayTransform(id.getItemDisplayTransform());
                clone = c;
            } else {
                return null;
            }
            stand.addPassenger(clone);
            DisplayPart p = new DisplayPart(clone);
            p.scaleX = scaleX;
            p.scaleY = scaleY;
            p.scaleZ = scaleZ;
            p.rotX = rotX;
            p.rotY = rotY;
            p.rotZ = rotZ;
            p.tx = tx;
            p.ty = ty;
            p.tz = tz;
            p.bright = bright;
            p.turret = false;
            p.hidePart(); // ukryj od razu (stara/nowa podmiana dopiero na granicy ćwiartki)
            return p;
        }

        /**
         * Ukrywa część: przesuwa jej WIZUALNĄ pozycję daleko w dół (poza zasięg
         * widzenia), NIE zmieniając zapisanych wartości (tx/ty/tz, obroty, skala) -
         * do animacji „stara wskazówka znika" w trybie zamiatającym zegara.
         * Pokazanie z powrotem = zwykłe apply().
         */
        public void hidePart() {
            if (display == null) return;
            Vector3f translation = new Vector3f(0f, -1000f, 0f);
            Vector3f scale = new Vector3f((float) scaleX, (float) scaleY, (float) scaleZ);
            Quaternionf quat = new Quaternionf().rotationXYZ(
                    (float) Math.toRadians(rotX), (float) Math.toRadians(rotY), (float) Math.toRadians(rotZ));
            AxisAngle4f rot = new AxisAngle4f(quat);
            display.setTransformation(new Transformation(
                    translation, rot, scale, new AxisAngle4f(0f, 0f, 0f, 1f)));
            applyBrightness();
        }

        /** Usuwa encję części ze świata (np. klon wskazówki po zakończeniu zegara). */
        public void removePart() {
            if (display != null && !display.isDead()) display.remove();
        }

        /** Własna nazwa części (np. z nazwanego przedmiotu w PDC albo na itemie
         *  ItemDisplay) albo null, gdy część jest bezimienna. */
        public String customName() {
            if (display == null) return null;
            String pdc = display.getPersistentDataContainer().get(KEY_PART_NAME, PersistentDataType.STRING);
            if (pdc != null && !pdc.isEmpty()) return pdc;
            if (display instanceof ItemDisplay id) {
                ItemStack item = id.getItemStack();
                if (item != null && item.hasItemMeta()) {
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null && meta.hasDisplayName()) return meta.getDisplayName();
                }
            }
            return null;
        }

        /** Ustawia własną nazwę części (zapis w PDC, żeby nie psuć wyglądu). */
        public void setName(String name) {
            if (display == null) return;
            if (name == null || name.isEmpty()) {
                display.getPersistentDataContainer().remove(KEY_PART_NAME);
            } else {
                display.getPersistentDataContainer().set(KEY_PART_NAME, PersistentDataType.STRING, name);
            }
        }

        /** Nazwa części do wyświetlenia (action bar): własna nazwa albo materiał. */
        public String name() {
            String n = customName();
            return n != null ? n : material().name().toLowerCase(Locale.ROOT);
        }

        /** Zrzut stanu części do zapisu. */
        public StandPreset.PartData toData() {
            Material m = material();
            if (m == Material.AIR) return null;
            StandPreset.PartData d = new StandPreset.PartData();
            d.material = m.name();
            d.scaleX = scaleX;
            d.scaleY = scaleY;
            d.scaleZ = scaleZ;
            d.rotX = rotX;
            d.rotY = rotY;
            d.rotZ = rotZ;
            d.tx = tx;
            d.ty = ty;
            d.tz = tz;
            d.turret = turret;
            d.bright = bright;
            d.name = customName();
            return d;
        }
    }

    /** Stan edycji dla jednego stojaka: która część i która oś/tryb jest strojony. */
    private static class EditState {
        Part part = Part.HEAD;
        int axis = 0; // części stojaka: 0=X,1=Y,2=Z; doczepiona część: 0=wysokość,1=przesunięcie-X,2=przesunięcie-Z,3=skala,4=skręt,5=obrót,6=pochylenie,7=lustro,8=odbicie,9=światło,10=grupa,11=rozciągnij-Y,12=rozciągnij-X,13=rozciągnij-Z
        int partIndex = -1; // wybrana doczepiona część (indeks na liście), -1 = brak
    }

    /** Snapshot usuniętego stojaka do /stojak przywroc (wygląd + części + pozycja). */
    private static class RemovedStand {
        StandPreset preset;   // wygląd i części (bez współrzędnych)
        Location location;    // dokładna pozycja (z yaw/pitch)
        String customName;    // widoczna nazwa (jeśli była)
        boolean nameVisible;
    }

    private final VillagerDefensePlugin plugin;
    /** Losowość ambientowych iskier wokół wieżyczek (bez stałego cooldownu). */
    private final Random random = new Random();
    private final Map<UUID, EditState> states = new HashMap<>();
    private final Map<UUID, List<DisplayPart>> parts = new HashMap<>();
    /** Zapamiętany tryb (oś) SUGAR per gracz - „stały parametr cukru": po wybraniu
     *  parametru (np. WYSOKOŚĆ) zostaje on przy zmianie części/stojaka, dopóki gracz
     *  go nie zmieni (zamiast resetu do SKALA). */
    private final Map<UUID, Integer> stickyAxis = new HashMap<>();
    /** Aktywny stojak gracza (gracz -> UUID stojaka) - edycja bez patrzenia na niego. */
    private final Map<UUID, UUID> active = new HashMap<>();
    /** Zadania wyłączania glow (UUID stojaka -> zadanie) - patrz /stojak glow. */
    private final Map<UUID, BukkitTask> glowTasks = new HashMap<>();
    /** Historia zmian części (/stojak undo) - UUID stojaka -> migawki stanu sprzed operacji. */
    private final Map<UUID, List<List<StandPreset.PartData>>> undo = new HashMap<>();
    /** Kopia bezpieczeństwa: stan części PRZED ostatnim /stojak undo - do /stojak ponow.
     *  Dzięki niej cofnięcie nigdy nie jest bezpowrotne (np. gdy undo zdjęło wszystkie
     *  części, a korpus stojaka jest niewidoczny i całość wygląda jak zniknięta). */
    private final Map<UUID, List<StandPreset.PartData>> lastUndoBackup = new HashMap<>();
    /** Ostatnio usunięte stojaki (gracz -> snapshot) - do /stojak przywroc. */
    private final Map<UUID, RemovedStand> lastRemoved = new HashMap<>();
    /** Maksymalna liczba cofnięć (/stojak undo) pamiętanych na stojak. */
    private static final int UNDO_LIMIT = 20;
    /** Krok przyciągania (snap): 0.25 bloka dla move, 5° dla rotate. */
    private static final double SNAP_MOVE_STEP = 0.25;
    private static final double SNAP_ROT_STEP = 5.0;
    /** Tag encji oznaczający stojak pluginu - zapisywany w NBT, więc przetrwa restart
     *  serwera. Dzięki niemu stojaki postawione przed restartem można odzyskać
     *  (kliknięcie przedmiotem lub /stojak odzyskaj) i edytować dalej. */
    private static final String TAG_STAND = "vd_strojony";
    /** PDC encji części: flaga „część obrotowa wieżyczki" (przetrwa restart). */
    private static final NamespacedKey KEY_TURRET = NamespacedKey.fromString("villagerdefense:vd_turret");
    /** PDC encji części: nazwa części (np. z nazwanego przedmiotu; przetrwa restart). */
    private static final NamespacedKey KEY_PART_NAME = NamespacedKey.fromString("villagerdefense:vd_partname");
    /** PDC encji części: pełna jasność (ŚWIATŁO w SUGAR; przetrwa restart). */
    private static final NamespacedKey KEY_BRIGHT = NamespacedKey.fromString("villagerdefense:vd_bright");
    /** PDC stojaka: DEKORACJA mapy - nie usuwana po zakończeniu areny (przetrwa restart). */
    private static final NamespacedKey KEY_DECORATION = NamespacedKey.fromString("villagerdefense:vd_dekoracja");
    /** PDC stojaka: auto-namierzanie wieżyczki włączone (przetrwa restart). */
    private static final NamespacedKey KEY_AIM = NamespacedKey.fromString("villagerdefense:vd_aim");
    /** PDC stojaka: kierunek PRZODU wieżyczki w pozycji bazowej (0 = +Z; przetrwa restart). */
    private static final NamespacedKey KEY_FRONT = NamespacedKey.fromString("villagerdefense:vd_front");
    /** PDC stojaka: maksymalne życie wieżyczki (przetrwa restart). */
    private static final NamespacedKey KEY_HP_MAX = NamespacedKey.fromString("villagerdefense:vd_hpmax");
    /** PDC stojaka: aktualne życie wieżyczki (przetrwa restart). */
    private static final NamespacedKey KEY_HP = NamespacedKey.fromString("villagerdefense:vd_hp");
    /** PDC stojaka: UUID gracza, który POSTAWIŁ wieżyczkę (dostaje punkty za jej zabójstwa). */
    private static final NamespacedKey KEY_PLACER = NamespacedKey.fromString("villagerdefense:vd_placer");
    /** PDC stojaka: nazwa areny, na której postawiono wieżyczkę (sprzątanie po grze). */
    private static final NamespacedKey KEY_ARENA = NamespacedKey.fromString("villagerdefense:vd_arena");
    /** PDC stojaka: nazwa presetu, z którego postawiono stojak (np. wiezyczka_maga;
     *  przetrwa restart). Używana przez animację części, żeby działała też, gdy
     *  custom name stojaka jest puste/przemianowane. */
    private static final NamespacedKey KEY_PRESET = NamespacedKey.fromString("villagerdefense:vd_preset");
    /** Klik-przełącznik stanów (/stojak funkcja zmiana <A> <B>): nazwy dwóch
     *  zapisanych presetów + indeks obecnego stanu (0 = A, 1 = B). */
    // Uwaga: klucz musi być małymi literami ([a-z0-9/._-]) - inaczej fromString
    // zwraca null i każde kliknięcie stojaka wywalałoby wyjątek (klik nie działa).
    private static final NamespacedKey KEY_STATE_A = NamespacedKey.fromString("villagerdefense:vd_stan_a");
    private static final NamespacedKey KEY_STATE_B = NamespacedKey.fromString("villagerdefense:vd_stan_b");
    private static final NamespacedKey KEY_STATE_IDX = NamespacedKey.fromString("villagerdefense:vd_stan_idx");
    /** PDC stojaka: UUID tekstu HP nad wieżyczką (przetrwa restart). */
    private static final NamespacedKey KEY_HP_TEXT = NamespacedKey.fromString("villagerdefense:vd_hptext");
    /** PDC stojaka: obrażenia wieżyczki na strzał (przetrwa restart). */
    private static final NamespacedKey KEY_DMG = NamespacedKey.fromString("villagerdefense:vd_dmg");
    /** PDC stojaka: zasięg namierzania wieżyczki w blokach (przetrwa restart). */
    private static final NamespacedKey KEY_RANGE = NamespacedKey.fromString("villagerdefense:vd_range");
    /** PDC stojaka: co ile ticków wieżyczka strzela (przetrwa restart). */
    private static final NamespacedKey KEY_FIRE_INTERVAL = NamespacedKey.fromString("villagerdefense:vd_fireinterval");
    /** PDC stojaka: strzał zamraża moba (spowolnienie + szron; przetrwa restart). */
    private static final NamespacedKey KEY_FREEZE = NamespacedKey.fromString("villagerdefense:vd_freeze");
    /** PDC stojaka: strzał TRUJE moba (własny DoT; przetrwa restart). */
    private static final NamespacedKey KEY_POISON = NamespacedKey.fromString("villagerdefense:vd_poison");
    /** PDC stojaka: strzał PODPALA moba na 8 s (fire aspect; przetrwa restart). */
    private static final NamespacedKey KEY_FIRE = NamespacedKey.fromString("villagerdefense:vd_fire");
    /** PDC stojaka: strzał skacze ŁAŃCUCHEM na kolejne moby (przetrwa restart). */
    private static final NamespacedKey KEY_CHAIN = NamespacedKey.fromString("villagerdefense:vd_chain");
    /** PDC stojaka-blokady: znacznik bariery wieżyczki (przetrwa restart). */
    private static final NamespacedKey KEY_BARRIER = NamespacedKey.fromString("villagerdefense:vd_barrier");
    /** PDC stojaka-blokady: UUID wieżyczki, do której należy (przetrwa restart). */
    private static final NamespacedKey KEY_BARRIER_OWNER = NamespacedKey.fromString("villagerdefense:vd_barrier_turret");
    /** Nazwa krzesła (/stojak krzeslo <nazwa> on) - siadać można tylko na stojaku z taką nazwą. */
    public static final NamespacedKey KEY_SEAT_NAME = NamespacedKey.fromString("villagerdefense:vd_seatname");
    /** PDC stojaka: przesunięcie Y siedziska (wysokość siadania względem stojaka). */
    public static final NamespacedKey KEY_SEAT_Y_OFFSET = NamespacedKey.fromString("villagerdefense:vd_seat_yoffset");
    /** PDC MOBA: aggro na wieżyczkę (UUID wieżyczki, którą atakuje po trafieniu). */
    private static final NamespacedKey KEY_TURRET_AGGRO = NamespacedKey.fromString("villagerdefense:vd_turret_aggro");
    /** PDC MOBA: tick ostatniego trafienia od wieżyczki (wygasanie aggro). */
    private static final NamespacedKey KEY_TURRET_AGGRO_TICK = NamespacedKey.fromString("villagerdefense:vd_turret_aggro_tick");
    /** Pozycje stojaków-blokad wokół wieżyczki: na krawędzi bloku (pół bloku od środka). */
    private static final double[][] TURRET_BARRIER_OFFSETS = {
            {0.5, 0}, {-0.5, 0}, {0, 0.5}, {0, -0.5},
            {0.5, 0.5}, {-0.5, 0.5}, {0.5, -0.5}, {-0.5, -0.5}};
    /** Głębokość odrzutu górnej części przy strzale (bloki). */
    private static final double RECOIL_DEPTH = 0.15;
    /** Obniżenie punktu wystrzału w lufie (bez ruszania X/Z). */
    private static final double TURRET_BARREL_DROP = 0.113; // 0.013 + 0.1 w dół
    /** Cooldown po POSTAWIENIU wieżyczki (ticki) zanim poleci pierwszy pocisk - 10 = 0,5 s. */
    private static final int TURRET_PLACEMENT_COOLDOWN_TICKS = 10;
    /** Wysunięcie punktu strzału (dymu) w przód wzdłuż lufy - żeby dym nie rodził
     *  się wewnątrz bloków wieżyczki. Wieżyczka NIE ma żadnej widocznej strzały. */
    private static final double TURRET_ARROW_FORWARD = 0.5;
    /** Zasięg namierzania wieżyczki (bloki). */
    public static final double AIM_RANGE = 8.0;
    /** Zasięg skanowania mobów z aggro (bloki). */
    private static final double TURRET_AGGRO_RANGE = 10.0;
    /** Jak długo mob atakuje wieżyczkę po trafieniu (ticki; wieżyczka odnawia przy
     *  każdym kolejnym trafieniu, więc aggro trwa, dopóki wieżyczka strzela). */
    private static final int TURRET_AGGRO_DURATION_TICKS = 100;
    /** Zasięg ciosu wręcz moba w wieżyczkę (od środka stojaka - mob stoi przy barierze). */
    private static final double TURRET_ATTACK_RANGE = 2.3;
    /** Rytm wymuszonych ciosów moba (ticki) - ~1 atak/s, jak waniliowo. */
    private static final int TURRET_ATTACK_INTERVAL = 20;
    /** Punkt postoju moba przy barierze: tuż za pierścieniem niewidzialnych blokad
     *  (blokady stoją pół bloka od środka wieżyczki, więc mob zatrzymuje się
     *  ~1.1 bloka od niej i bije przez barierę). */
    private static final double TURRET_STANDOFF = 1.1;
    /** Co ile ticków odświeżamy ścieżkę moba do bariery (żeby mob nie drgał). */
    private static final int TURRET_NAV_INTERVAL = 10;
    /** Prędkość obrotu góry przy namierzaniu (°/s - pełny obrót w 2 s). */
    public static final double AIM_TURN_SPEED = 180.0;
    /** Obrażenia wieżyczki: 3.0 przy trafieniu strzałą (raz na 2 sekundy strzela). */
    public static final double TURRET_DAMAGE_PER_SECOND = 3.0;
    /** Cooldown strzału wieżyczki: 40 ticków = 2 sekundy między strzałami (domyślne). */
    private static final int TURRET_FIRE_INTERVAL_TICKS = 40;
    /** Efekt zamrożenia wieżyczki lodowej: spowolnienie II przez 5 sekund + szron wizualnie. */
    private static final int FREEZE_DURATION_TICKS = 100; // 5 s
    private static final int FREEZE_AMPLIFIER = 1;
    /** Trucizna wieżyczki trutki: 1 HP na sekundę przez 8 sekund (własny DoT - moby
     *  są odporne na waniliową truciznę, więc obrażenia liczymy sami). */
    private static final double POISON_DPS = 1.0;
    private static final int POISON_DURATION_TICKS = 160; // 8 s
    /** Podpalenie wieżyczki ognia: mob płonie 8 sekund (jak zaklęty ogień / fire aspect). */
    private static final int FIRE_DURATION_TICKS = 160; // 8 s
    /** Łańcuch elektryczny wieżyczki elektryka: hop na moba do 5 bloków od poprzedniego.
     *  Kolejne cele dostają ułamek obrażeń: 100% (główny) -> 65% -> 35% (max 3 moby). */
    private static final double CHAIN_HOP_RANGE = 5.0;
    private static final double[] CHAIN_DAMAGE_MULTIPLIERS = {0.65, 0.35};
    /** Tag gracza dla MOBÓW PO STRONIE GRACZA - wieżyczka ich nie namierza
     *  (i nie daje im celu po trafieniu). Nadaj go np. komendą scoreboard:
     *  /scoreboard players tag @e[type=zombie] add vd_sojusz */
    public static final String TAG_ALLIED = "vd_sojusz";

    /** Stan wieżyczki jednego stojaka: aktualny kąt, czy namierza, tick ostatniego strzału. */
    private static class TurretSpin {
        double angle;
        boolean aim;
        int lastDamageTick;
        double recoil; // 1 = świeżo po strzale, maleje do 0
        /** Tick postawienia/włączenia wieżyczki - pierwszy strzał dopiero po cooldownie. */
        int placeTick;
        /** Pozycja lufy względem stojaka (x y z, jak część ARROW przed zamianą). */
        double[] barrel = {0, -1.0, 0.43};
        double barrelRotY = -90; // obrót lufy z części ARROW (zapasowy kierunek)
        double barrelRotZ = -45;

        TurretSpin(double angle, boolean aim) {
            this.angle = angle;
            this.aim = aim;
            this.placeTick = Bukkit.getCurrentTick();
        }
    }

    // ===================== animacja części (wieżyczka maga) =====================

    /** Presety, których części dostają animację (nazwa zapisu = custom name stojaka
     *  albo nazwa presetu zapisana w PDC przy stawianiu).
     *  Wszystkie warianty wieżyczki maga: podstawowa + elektryk/lod/ogien/trutka. */
    private static final Set<String> ANIMATED_PRESETS = Set.of(
            "wiezyczka_maga", "wiezyczka_maga_elektryk", "wiezyczka_maga_lod",
            "wiezyczka_maga_ogien", "wiezyczka_maga_trutka");
    /** Materiały animowanych bloków (kryształowa kula maga: emerald, lód, glowstone, magma). */
    private static final Set<Material> FLOAT_MATERIALS = Set.of(
            Material.EMERALD_BLOCK, Material.PACKED_ICE, Material.GLOWSTONE, Material.MAGMA_BLOCK);
    /** Amplituda unoszenia bloków (bloki). 0 = bloki stoją na STAŁEJ wysokości i tylko
     *  obracają się w miejscu (jak człowiek, który stoi w miejscu i się kręci - nie
     *  wychodzi z pozycji i nie wraca). Unoszenie w górę/dół w połączeniu z obrotem
     *  wyglądało jak kółko, więc jest wyłączone. Zmiana na np. 0.12 przywraca je. */
    private static final double FLOAT_AMPLITUDE = 0.0;
    /** Czas wznoszenia / opadania (ticki) - 60 ticków = 3 s. */
    private static final int FLOAT_MOVE_TICKS = 60;
    /** Pauza na górze i na dole (ticki) - 20 ticków = 1 s. */
    private static final int FLOAT_PAUSE_TICKS = 20;
    /** Prędkość obrotu w lewo wokół osi Y (°/tick): 6°/tick = 1 pełny obrót (360°) na 3 s. */
    private static final double SPIN_Y_DEG = 6.0;

    /** Stan animacji jednego stojaka: narastający obrót w lewo wokół Y (stopnie) i faza unoszenia (ticki). */
    private static class PartAnimState {
        double ry;
        int tick;
    }

    /** Stojaki z animowanymi częściami: UUID stojaka -> stan. */
    private final Map<UUID, PartAnimState> partAnims = new HashMap<>();

    /** Stan trucizny jednego moba: ile ticków zostało i narastające obrażenia (1 HP/s). */
    private static class PoisonState {
        int ticksLeft;
        double accumulator;
        /** UUID wieżyczki, która otruła moba - kredyt za zabójstwo. */
        UUID turretUuid;
    }

    /** Zatrute moby: UUID moba -> stan trucizny (DoT działa też bez wieżyczki). */
    private final Map<UUID, PoisonState> poisons = new HashMap<>();

    /** Stojaki z włączonym obrotem górnej części (wieżyczki): UUID stojaka -> stan. */
    private final Map<UUID, TurretSpin> turrets = new HashMap<>();
    /** Stojaki-blokady wieżyczek (bariera dla mobów): UUID wieżyczki -> niewidoczne stojaki. */
    private final Map<UUID, List<ArmorStand>> barriers = new HashMap<>();
    /** Ostatni wymuszony cios aggro moba (UUID moba -> tick) - rytm ataków. */
    private final Map<UUID, Integer> lastTurretAttack = new HashMap<>();
    /** Tick ostatniego prowadzenia moba do bariery (UUID moba -> tick) - odświeżanie ścieżki. */
    private final Map<UUID, Integer> lastTurretNav = new HashMap<>();
    /** Gracze z włączonym przyciąganiem (snap) dla move/rotate - patrz /stojak snap. */
    private final Set<UUID> snap = new HashSet<>();
    /** Boss bary z tożsamością stojaka (gracz -> bar) - pokazywane podczas strojenia. */
    private final Map<UUID, BossBar> statusBars = new HashMap<>();
    /** Narzędzia /stojak (dajitemy) - trzymanie ich w ręce = gracz stroi stojak. */
    private static final Set<Material> TUNER_TOOLS = Set.of(
            Material.STICK, Material.BONE, Material.BLAZE_ROD, Material.SUGAR,
            Material.REDSTONE, Material.GUNPOWDER, Material.BARRIER,
            Material.FEATHER, Material.BLAZE_POWDER, Material.PAPER);

    public ArmorStandTuner(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
        // Stały pasek statusu strojenia: co 2 s (40 ticków) odświeża boss bar
        // z tożsamością stojaka i action bar z aktualną częścią i trybem - ale
        // TYLKO dla graczy, którzy faktycznie stroją (trzymają narzędzie /stojak
        // i mają cel). Gdy przestaną, boss bar znika, a action bar sam wygasa.
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickStatusBars, 40L, 40L);
    }

    // ===================== stały pasek statusu strojenia =====================

    /** Czy gracz jest w trakcie strojenia: trzyma narzędzie /stojak i ma cel (patrzony/albo aktywny). */
    private boolean isTuning(Player player) {
        if (!TUNER_TOOLS.contains(player.getInventory().getItemInMainHand().getType())) return false;
        return statusTarget(player) != null;
    }

    /** Cel strojenia dla paska statusu: patrzony EDYTOWALNY stojak albo AKTYWNY
     *  gracza (bez dołączania/rebuildu - task chodzi co 2 s, musi być tani). */
    private ArmorStand statusTarget(Player player) {
        ArmorStand stand = findLookedStand(player, true);
        if (stand == null) stand = getActive(player);
        return stand;
    }

    /** Tekst boss bara: „stojak - (koordynaty)", a gdy stojak ma nazwę - „stojak - (nazwa)". */
    private String statusTitle(ArmorStand stand) {
        String id;
        String custom = stand.getCustomName();
        if (custom != null && !custom.isEmpty()) {
            id = "§f" + custom;
        } else {
            Location l = stand.getLocation();
            id = "§7" + fmt(l.getX()) + " " + fmt(l.getY()) + " " + fmt(l.getZ());
        }
        return "§6stojak §7- " + id;
    }

    /** Pokazuje/aktualizuje boss bar z tożsamością stojaka (tworzy, gdy brak). */
    private void showStatusBar(Player player, ArmorStand stand) {
        BossBar bar = statusBars.get(player.getUniqueId());
        if (bar == null) {
            bar = plugin.getServer().createBossBar(statusTitle(stand), BarColor.YELLOW, BarStyle.SOLID);
            bar.setProgress(1.0);
            statusBars.put(player.getUniqueId(), bar);
        } else {
            bar.setTitle(statusTitle(stand));
        }
        bar.addPlayer(player);
    }

    /** Chowa boss bar statusu gracza (np. gdy przestał stroić). */
    private void hideStatusBar(Player player) {
        BossBar bar = statusBars.remove(player.getUniqueId());
        if (bar != null) bar.removeAll();
    }

    /**
     * Co 2 s dla każdego gracza w trakcie strojenia: boss bar z tożsamością stojaka
     * (nazwa albo koordynaty) + action bar z aktualną częścią i trybem (info) -
     * dzięki powtarzaniu action bar nie znika, dopóki gracz stroi. Gracze, którzy
     * przestali stroić, dostają boss bar schowany; action bar wygasa sam.
     */
    private void tickStatusBars() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            ArmorStand stand = isTuning(player) ? statusTarget(player) : null;
            if (stand == null) {
                hideStatusBar(player);
                continue;
            }
            showStatusBar(player, stand);
            EditState s = states.get(stand.getUniqueId());
            if (s != null) info(stand, player);
        }
        // posprzątaj boss bary graczy, którzy wyszli z serwera
        if (!statusBars.isEmpty()) {
            statusBars.entrySet().removeIf(e -> {
                if (Bukkit.getPlayer(e.getKey()) == null) {
                    e.getValue().removeAll();
                    return true;
                }
                return false;
            });
        }
    }

    /** Natychmiast odświeża boss bar statusu gracza (np. po nadaniu nazwy /stojak nazwij). */
    public void refreshStatusBar(Player player) {
        ArmorStand stand = isTuning(player) ? statusTarget(player) : null;
        if (stand == null) {
            hideStatusBar(player);
        } else {
            showStatusBar(player, stand);
        }
    }

    public boolean isEditable(ArmorStand stand) {
        return stand != null && !stand.isDead() && states.containsKey(stand.getUniqueId());
    }

    // ===================== spawn i dołączanie =====================

    /**
     * Stawia stojak do strojenia. Domyślnie: niewidoczny korpus, ręce, bez bloku
     * na głowie i bez przedmiotu w ręce, wpuszczony w blok (sink). Wszystko da
     * się nadpisać parametrami klucz:wartość (head:, hand:, small:, arms:,
     * visible:, base:, sink:, yaw:, pitch:, hpitch:/hyaw:/hroll:, bpitch:...,
     * rpitch:..., lpitch:..., rgpitch:... (prawa noga), lgpitch:... (lewa noga)).
     */
    public ArmorStand spawn(Player player, Map<String, String> params) {
        return spawn(player, params, null);
    }

    /**
     * Stawia stojak do strojenia. exactFeet != null = dokładne koordynaty stóp
     * (bez sink i centrowania - jak /stojak place <nazwa> x y z); w przeciwnym
     * razie na bloku, na który gracz patrzy (z sink i wyśrodkowaniem).
     */
    public ArmorStand spawn(Player player, Map<String, String> params, Location exactFeet) {
        Location loc;
        if (exactFeet != null) {
            // x y z = współrzędne BLOKU: wyśrodkuj w XZ (inaczej stojak stawałby
            // na rogu czterech bloków); y = pozycja stóp (bez sink)
            loc = exactFeet.clone().add(0.5, 0, 0.5);
        } else {
            Location target = player.getTargetBlockExact(100) != null
                    ? player.getTargetBlockExact(100).getLocation()
                    : player.getLocation();
            // domyślnie stojak staje NA WIERZCHU patrzonego bloku (sink:-1.0);
            // sink:0 = 1 blok niżej, sink:1 = 2 bloki niżej itd.
            double sink = paramDouble(params, "sink", -1.0);
            double ox = paramDouble(params, "ox", 0);
            double oy = paramDouble(params, "oy", 0);
            double oz = paramDouble(params, "oz", 0);
            loc = target.add(0.5 + ox, -sink + oy, 0.5 + oz);
        }
        loc.setYaw((float) paramDouble(params, "yaw", 0));
        loc.setPitch((float) paramDouble(params, "pitch", 0));

        ArmorStand stand = loc.getWorld().spawn(loc, ArmorStand.class);
        stand.setSmall(paramBool(params, "small", false));
        stand.setArms(paramBool(params, "arms", true));
        stand.setVisible(paramBool(params, "visible", false));
        stand.setBasePlate(paramBool(params, "base", false));
        applySafety(stand);

        stand.getEquipment().setHelmet(new ItemStack(paramMaterial(params, "head", Material.AIR)));
        stand.getEquipment().setItemInMainHand(new ItemStack(paramMaterial(params, "hand", Material.AIR)));
        if (params.containsKey("offhand")) {
            stand.getEquipment().setItemInOffHand(new ItemStack(paramMaterial(params, "offhand", Material.AIR)));
        }

        stand.setHeadPose(pose(params, "h", 0, 0, 0));
        stand.setBodyPose(pose(params, "b", 0, 0, 0));
        stand.setRightArmPose(pose(params, "r", 0, 0, 0));
        stand.setLeftArmPose(pose(params, "l", 0, 0, 0));
        stand.setRightLegPose(pose(params, "rg", 0, 0, 0));
        stand.setLeftLegPose(pose(params, "lg", 0, 0, 0));

        states.put(stand.getUniqueId(), new EditState());
        return stand;
    }

    /**
     * Dołącza już stojący stojak (np. postawiony wcześniej, przed restartem
     * serwera) do strojenia i zabezpiecza go przed zniszczeniem.
     */
    public boolean bind(ArmorStand stand) {
        if (stand == null || stand.isDead()) return false;
        markEditable(stand);
        return true;
    }

    /** Oznacza stojak jako edytowalny (do strojenia) i zabezpiecza go. */
    public void markEditable(ArmorStand stand) {
        if (stand == null || stand.isDead()) return;
        applySafety(stand);
        states.put(stand.getUniqueId(), new EditState());
        rebuildParts(stand);
        resumeTurret(stand); // wznow namierzanie wieżyczki, jeśli było włączone (po restarcie)
    }

    /**
     * Odzyskuje stojak pluginu po restarcie serwera: edytowalny już albo
     * oznaczony tagiem pluginu (przetrwał restart) lub mający doczepione części.
     * Przywraca śledzenie części i status edytowalny. Zwraca true, gdy stojak
     * można edytować.
     */
    public boolean recover(ArmorStand stand) {
        if (stand == null || stand.isDead()) return false;
        if (isEditable(stand)) return true;
        if (!stand.getScoreboardTags().contains(TAG_STAND) && !hasParts(stand)) return false;
        markEditable(stand);
        return true;
    }

    /** Czy stojak ma doczepione części (pasażerów Display) - niezależnie od śledzenia. */
    public boolean hasParts(ArmorStand stand) {
        if (stand == null) return false;
        for (Entity e : stand.getPassengers()) {
            if (e instanceof Display) return true;
        }
        return false;
    }

    /** Czy to stojak pluginu (tag przetrwał restart albo ma doczepione części) - do autosave. */
    public boolean isPluginStand(ArmorStand stand) {
        if (stand == null || stand.isDead()) return false;
        if (stand.getScoreboardTags().contains(TAG_STAND)) return true;
        return hasParts(stand);
    }

    /** Czy stojak jest krzesłem (do siadania): ma tag krzesła, a gdy oznaczono go
     *  z nazwą (/stojak krzeslo <nazwa> on) - jego widoczna nazwa musi się z nią
     *  zgadzać (inaczej siadanie nie działa). Stojaki bez nazwy (gotowe siodła
     *  z /stojak funkcja siedzenie) działają jak dawniej. Tag = StandPreset.SEAT_TAG. */
    public boolean isSeat(ArmorStand stand) {
        if (stand == null || stand.isDead() || !stand.getScoreboardTags().contains(StandPreset.SEAT_TAG)) return false;
        String seatName = seatNameOf(stand);
        if (seatName == null || seatName.isEmpty()) return true;
        String customName = stand.getCustomName();
        return customName != null && ChatColor.stripColor(customName).equals(seatName);
    }

    /** Zapisana nazwa krzesła (/stojak krzeslo <nazwa> on) albo null, gdy stojak
     *  oznaczono bez nazwy (działa jak dawniej). */
    public String seatNameOf(ArmorStand stand) {
        if (stand == null) return null;
        return stand.getPersistentDataContainer().get(KEY_SEAT_NAME, PersistentDataType.STRING);
    }

    /** Ustawia przesunięcie Y siedziska (wysokość siadania względem stojaka). */
    public void setSeatYOffset(ArmorStand stand, double offset) {
        if (stand == null || stand.isDead()) return;
        stand.getPersistentDataContainer().set(KEY_SEAT_Y_OFFSET, PersistentDataType.DOUBLE, offset);
    }

    /** Zwraca przesunięcie Y siedziska (domyślnie 0). */
    public double getSeatYOffset(ArmorStand stand) {
        if (stand == null || stand.isDead()) return 0.0;
        Double val = stand.getPersistentDataContainer().get(KEY_SEAT_Y_OFFSET, PersistentDataType.DOUBLE);
        return val != null ? val : 0.0;
    }

    /** Oznacza / zdejmuje oznaczenie krzesła (/stojak krzeslo [on|off] - bez nazwy). */
    public void setSeat(ArmorStand stand, Player player, boolean on) {
        setSeat(stand, player, on, null);
    }

    /** Oznacza / zdejmuje oznaczenie krzesła (/stojak krzeslo [<nazwa>] [on|off]).
     *  Gdy podano <nazwa>, stojak od razu dostaje tę widoczną nazwę i siadanie
     *  działa TYLKO, dopóki ma nazwę równą zapisanej (zmiana nazwy /stojak nazwij
     *  albo usunięcie jej wyłącza krzesło). */
    public void setSeat(ArmorStand stand, Player player, boolean on, String seatName) {
        if (stand == null || stand.isDead()) return;
        if (on) {
            stand.addScoreboardTag(StandPreset.SEAT_TAG);
            if (seatName != null && !seatName.isEmpty()) {
                stand.setCustomName(seatName);
                stand.getPersistentDataContainer().set(KEY_SEAT_NAME, PersistentDataType.STRING, seatName);
                AntiSpam.send(player, "§aStojak oznaczony jako krzesło dla nazwy „§e" + seatName
                        + "§a” - siadać można TYLKO na stojaku z taką nazwą (klik = siadanie, Shift = wstań). Zapisz: §e/stojak save <nazwa>§a.");
            } else {
                AntiSpam.send(player, "§aStojak oznaczony jako krzesło - kliknięcie = siadanie (Shift = wstań). Zapisz jak stojak: §e/stojak save <nazwa>§a.");
            }
        } else {
            stand.removeScoreboardTag(StandPreset.SEAT_TAG);
            stand.getPersistentDataContainer().remove(KEY_SEAT_NAME);
            AntiSpam.send(player, "§7Zdjęto oznaczenie krzesła.");
        }
    }

    /**
     * Spawnuje GOTOWE krzesło: mini (small) NIEWIDOCZNY stojak z SIODŁEM na głowie -
     * widać tylko siodło, czyli siedzisko. Oznaczony jako krzesło i zabezpieczony;
     * kliknięcie siodła = siadanie. Patrz /stojak funkcja siedzenie [x y z].
     */
    public ArmorStand spawnSeat(Location loc, Player player) {
        ArmorStand stand = loc.getWorld().spawn(loc, ArmorStand.class);
        stand.setSmall(true);
        stand.setVisible(false);
        stand.setArms(false);
        stand.setBasePlate(false);
        stand.setGravity(false);
        stand.setInvulnerable(true);
        stand.setCollidable(false);
        stand.setSilent(true);
        stand.setCanPickupItems(false);
        stand.setPersistent(true);
        stand.setRemoveWhenFarAway(false);
        stand.getEquipment().setHelmet(new ItemStack(Material.AIR));
        stand.addScoreboardTag(StandPreset.SEAT_TAG);
        // od razu edytowalne i AKTYWNE - żeby /stojak save <nazwa> i inne komendy
        // działały bez patrzenia; zapis/place/paste zachowują tag krzesła
        markEditable(stand);
        if (player != null) setActive(player, stand);
        return stand;
    }

    /**
     * Spawnuje GOTOWĄ dekorację: czarny ekran "telewizora" z czarnych banerów
     * ustawionych obok siebie (siatka szerokość x wysokość) - patrz
     * /stojak funkcja tv [szerokość] [wysokość]. Każdy baner to osobna,
     * edytowalna część (ROZCIĄGNIJ/SKALA/PRZESUNIĘCIE/OBRÓT); stojak jest
     * zapisywalny jak normalny (/stojak save <nazwa>) i aktywny od razu.
     */
    public ArmorStand spawnTv(Location loc, Player player, int width, int height) {
        ArmorStand stand = loc.getWorld().spawn(loc, ArmorStand.class);
        stand.setSmall(true);
        stand.setVisible(false);
        stand.setArms(false);
        stand.setBasePlate(false);
        stand.setGravity(false);
        stand.setInvulnerable(true);
        stand.setCollidable(false);
        stand.setSilent(true);
        stand.setCanPickupItems(false);
        stand.setPersistent(true);
        stand.setRemoveWhenFarAway(false);
        markEditable(stand);
        if (player != null) setActive(player, stand);
        // ekran: siatka czarnych banerów - każdy to osobna część w odległości 1 blok
        Material banner = Material.BLACK_BANNER;
        List<DisplayPart> list = parts.computeIfAbsent(stand.getUniqueId(), k -> new ArrayList<>());
        for (int col = 0; col < width; col++) {
            for (int row = 0; row < height; row++) {
                ItemDisplay id = stand.getWorld().spawn(stand.getLocation(), ItemDisplay.class);
                id.setItemStack(new ItemStack(banner));
                id.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                stand.addPassenger(id);
                DisplayPart part = new DisplayPart(id);
                part.tx = (col - (width - 1) / 2.0);
                part.ty = 0.5 + (row - (height - 1) / 2.0);
                part.tz = 0;
                part.apply();
                list.add(part);
            }
        }
        return stand;
    }

    /**
     * Spawnuje GOTOWĄ dekorację: wizualną LAMPĘ na niewidocznym stojaku - patrz
     * /stojak funkcja lampa [wysokość]. Wysokość 1 = sam świecący blok (glowstone),
     * 2 = ciemny cienki słup + głowa na górze (3, 4 = wyższe słupy). Głowa ma
     * świecącą teksturę, więc wygląda jak zapalona latarnia. Edytowalna i zapisywalna
     * jak normalny stojak (/stojak save <nazwa>).
     */
    public ArmorStand spawnLamp(Location loc, Player player, int height) {
        ArmorStand stand = loc.getWorld().spawn(loc, ArmorStand.class);
        stand.setSmall(true);
        stand.setVisible(false);
        stand.setArms(false);
        stand.setBasePlate(false);
        stand.setGravity(false);
        stand.setInvulnerable(true);
        stand.setCollidable(false);
        stand.setSilent(true);
        stand.setCanPickupItems(false);
        stand.setPersistent(true);
        stand.setRemoveWhenFarAway(false);
        markEditable(stand);
        if (player != null) setActive(player, stand);
        List<DisplayPart> list = parts.computeIfAbsent(stand.getUniqueId(), k -> new ArrayList<>());
        // słup: ciemny, cienki post (0.35 w X/Z, pełny w Y) - wysokość pomniejszona o głowę
        for (int i = 0; i < height - 1; i++) {
            BlockDisplay bd = stand.getWorld().spawn(stand.getLocation(), BlockDisplay.class);
            bd.setBlock(Bukkit.createBlockData(Material.DARK_OAK_PLANKS));
            stand.addPassenger(bd);
            DisplayPart part = new DisplayPart(bd);
            part.ty = 0.5 + i;
            part.scaleX = 0.35;
            part.scaleY = 1.0;
            part.scaleZ = 0.35;
            part.apply();
            list.add(part);
        }
        // głowa lampy: glowstone (świecąca tekstura), lekko mniejsza od pełnego bloku
        BlockDisplay head = stand.getWorld().spawn(stand.getLocation(), BlockDisplay.class);
        head.setBlock(Bukkit.createBlockData(Material.GLOWSTONE));
        stand.addPassenger(head);
        DisplayPart hp = new DisplayPart(head);
        hp.ty = height - 0.5;
        hp.scaleX = 0.85;
        hp.scaleY = 0.85;
        hp.scaleZ = 0.85;
        hp.apply();
        list.add(hp);
        return stand;
    }

    /**
     * Sadza gracza na oznaczonym stojaku-krześle (gracz staje się pasażerem stojaka,
     * więc wizualne części zostają na miejscu). Zwraca true, gdy siadł.
     */
    public boolean sitOn(Player player, ArmorStand stand) {
        if (stand == null || stand.isDead() || !isSeat(stand)) return false;
        if (player.getVehicle() != null) return false; // już gdzieś siedzi
        stand.addPassenger(player);
        return true;
    }

    /**
     * Wszystkie stojaki pluginu w świecie gracza (tag pluginu albo części),
     * posortowane od najbliższego - do /stojak znajdz. Duchy-podglądy pomijane.
     */
    public List<ArmorStand> findPluginStands(Player player) {
        List<ArmorStand> out = new ArrayList<>();
        for (Entity e : player.getWorld().getEntities()) {
            if (!(e instanceof ArmorStand stand) || stand.isDead()) continue;
            if (plugin.getStandPresetManager().isPreview(stand)) continue;
            if (stand.getScoreboardTags().contains(TAG_STAND) || hasParts(stand)) {
                out.add(stand);
            }
        }
        Location loc = player.getLocation();
        out.sort(Comparator.comparingDouble(s -> s.getLocation().distanceSquared(loc)));
        return out;
    }

    /**
     * Odtwarza śledzenie doczepionych części z istniejących pasażerów stojaka
     * (np. po restarcie serwera albo po /stojak bind). Bez tego save/saveitem/
     * part nie widziałyby części doczepionych przed restartem i zapis gubiłby
     * wszystkie bloki (np. wieżyczki).
     */
    public void rebuildParts(ArmorStand stand) {
        parts.remove(stand.getUniqueId());
        List<DisplayPart> list = new ArrayList<>();
        for (Entity e : stand.getPassengers()) {
            if (!(e instanceof Display display)) continue;
            DisplayPart p = new DisplayPart(display);
            Transformation t = display.getTransformation();
            Vector3f tr = t.getTranslation();
            Vector3f sc = t.getScale();
            p.tx = tr.x();
            p.ty = tr.y();
            p.tz = tr.z();
            p.scaleX = sc.x();
            p.scaleY = sc.y();
            p.scaleZ = sc.z();
            Quaternionf q = new Quaternionf(t.getLeftRotation());
            p.rotX = Math.toDegrees(Math.atan2(2 * (q.w() * q.x() + q.y() * q.z()),
                    1 - 2 * (q.x() * q.x() + q.y() * q.y())));
            p.rotY = Math.toDegrees(Math.asin(clamp(2 * (q.w() * q.y() - q.z() * q.x()), -1, 1)));
            p.rotZ = Math.toDegrees(Math.atan2(2 * (q.w() * q.z() + q.x() * q.y()),
                    1 - 2 * (q.y() * q.y() + q.z() * q.z())));
            list.add(p);
        }
        if (!list.isEmpty()) parts.put(stand.getUniqueId(), list);
    }

    public void remove(ArmorStand stand, Player player) {
        remove(stand, player, true);
    }

    /** Jak {@link #remove(ArmorStand, Player)}, ale bez potwierdzenia w action barze
     *  (dla operacji zbiorczych, np. /stojak delete <nazwa> usuwającego kilka stojaków). */
    public void remove(ArmorStand stand, Player player, boolean notify) {
        // snapshot do /stojak przywroc - PRZED odpięciem/usunięciem części;
        // rebuildParts gwarantuje, że części są śledzone (też dla stojaka spoza sesji)
        if (player != null && stand != null && !stand.isDead()) {
            rebuildParts(stand);
            RemovedStand r = new RemovedStand();
            r.preset = StandPreset.fromStand("przywrocony", stand, 1.0, partsOf(stand));
            r.location = stand.getLocation().clone();
            r.customName = stand.getCustomName();
            r.nameVisible = stand.isCustomNameVisible();
            lastRemoved.put(player.getUniqueId(), r);
        }
        forgetParts(stand);
        // defensywnie odepnij i usuń WSZYSTKIE pozostałe pasażery (nieśledzone części,
        // np. sprzed restartu) - żeby po usunięciu stojaka nic nie zostało w świecie
        for (Entity e : new ArrayList<>(stand.getPassengers())) {
            if (e instanceof Display) detachAndRemove(stand, e);
        }
        states.remove(stand.getUniqueId());
        undo.remove(stand.getUniqueId());
        lastUndoBackup.remove(stand.getUniqueId());
        turrets.remove(stand.getUniqueId());
        removeTurretBarrier(stand); // usuń też stojaki-blokady bariery (wszystkie wokół)
        active.entrySet().removeIf(en -> en.getValue().equals(stand.getUniqueId()));
        BukkitTask gt = glowTasks.remove(stand.getUniqueId());
        if (gt != null) gt.cancel();
        stand.remove();
        if (notify) AntiSpam.sendActionBar(player, "§aUsunięto stojak.");
    }

    /**
     * Przywraca ostatnio usunięty przez gracza stojak (razem z doczepionymi
     * częściami) dokładnie tam, gdzie stał - /stojak przywroc. Zwraca true,
     * gdy coś przywrócono.
     */
    public boolean restoreRemoved(Player player) {
        RemovedStand r = lastRemoved.remove(player.getUniqueId());
        if (r == null || r.location == null || r.location.getWorld() == null) return false;
        ArmorStand stand = r.location.getWorld().spawn(r.location, ArmorStand.class);
        r.preset.applyTo(stand);
        if (r.customName != null && !r.customName.isEmpty()) {
            stand.setCustomName(r.customName);
            stand.setCustomNameVisible(r.nameVisible);
        } else {
            stand.setCustomName(null);
            stand.setCustomNameVisible(false);
        }
        markEditable(stand);
        for (StandPreset.PartData part : r.preset.parts) {
            attachPart(stand, part);
        }
        setActive(player, stand);
        return true;
    }

    /**
     * Znajduje stojak o podanej nazwie (custom name) w załadowanych chunkach
     * świata gracza - do komend /stojak <nazwa> <operacja> bez patrzenia
     * (np. /stojak glowa_wiezyczki duplikuj czesc 5). Duchy-podglądy są pomijane.
     */
    public ArmorStand findNamedStand(Player player, String name) {
        if (name == null || name.isEmpty()) return null;
        String target = name.toLowerCase(Locale.ROOT);
        for (Entity e : player.getWorld().getEntities()) {
            if (!(e instanceof ArmorStand stand) || stand.isDead()) continue;
            if (plugin.getStandPresetManager().isPreview(stand)) continue;
            String n = stand.getCustomName();
            if (n != null && !n.isEmpty() && n.toLowerCase(Locale.ROOT).equals(target)) return stand;
        }
        return null;
    }

    /**
     * Podświetla stojak (i wszystkie jego części) efektem glowing na podaną
     * liczbę sekund. Ponowne wywołanie odświeża czas.
     */
    public void glow(ArmorStand stand, Player player, int seconds) {
        if (stand == null || stand.isDead()) return;
        BukkitTask prev = glowTasks.remove(stand.getUniqueId());
        if (prev != null) prev.cancel();
        setGlowing(stand, true);
        glowTasks.put(stand.getUniqueId(), Bukkit.getScheduler().runTaskLater(plugin, () -> {
            glowTasks.remove(stand.getUniqueId());
            setGlowing(stand, false);
        }, Math.max(1, seconds) * 20L));
        AntiSpam.send(player, "§aPodświetlono stojak na " + Math.max(1, seconds) + " s.");
    }

    /** Wyłącza podświetlanie stojaka (od razu). */
    public void glowOff(ArmorStand stand, Player player) {
        if (stand == null || stand.isDead()) return;
        BukkitTask prev = glowTasks.remove(stand.getUniqueId());
        if (prev != null) prev.cancel();
        setGlowing(stand, false);
        AntiSpam.send(player, "§aWyłączono podświetlanie stojaka.");
    }

    /**
     * Podświetla tylko doczepione części (bez korpusu stojaka) - SHIFT+REDSTONE.
     * Odwołuje też ewentualne zaplanowane wyłączenie glow (/stojak glow <sekundy>).
     */
    public void toggleGlowParts(ArmorStand stand, Player player) {
        if (stand == null || stand.isDead()) return;
        BukkitTask prev = glowTasks.remove(stand.getUniqueId());
        if (prev != null) prev.cancel();
        List<DisplayPart> list = partsOf(stand);
        boolean any = false;
        for (DisplayPart p : list) {
            if (p.display.isGlowing()) {
                any = true;
                break;
            }
        }
        boolean on = !any;
        for (DisplayPart p : list) p.display.setGlowing(on);
        AntiSpam.sendActionBar(player, on
                ? "§aPodświetlono TYLKO części (strukturę). §7(SHIFT+REDSTONE = wyłącz)"
                : "§7Wyłączono podświetlanie części.");
    }

    /**
     * Przełącza podświetlanie stojaka (i wszystkich części) - kliknięcie REDSTONE.
     * Działa też na niewidzialnych stojakach (łatwo znaleźć, co się stroi).
     * Zwraca nowy stan (true = świeci).
     */
    public boolean toggleGlow(ArmorStand stand, Player player) {
        if (stand == null || stand.isDead()) return false;
        BukkitTask prev = glowTasks.remove(stand.getUniqueId());
        if (prev != null) prev.cancel();
        boolean on = !stand.isGlowing();
        setGlowing(stand, on);
        AntiSpam.sendActionBar(player, on
                ? "§aPodświetlono stojak (i części). §7(kolejny klik REDSTONE = wyłącz)"
                : "§7Wyłączono podświetlanie stojaka.");
        return on;
    }

    private void setGlowing(ArmorStand stand, boolean glow) {
        if (stand == null || stand.isDead()) return;
        stand.setGlowing(glow);
        for (Entity e : stand.getPassengers()) {
            if (e instanceof Display d) d.setGlowing(glow);
        }
    }

    /** Zapamiętuje stojak jako "aktywny" dla gracza (do edycji bez patrzenia). */
    public void setActive(Player player, ArmorStand stand) {
        if (stand != null && !stand.isDead()) {
            active.put(player.getUniqueId(), stand.getUniqueId());
            // stały parametr cukru: przy przejściu na inny stojak tryb wraca do
            // zapamiętanego dla gracza (np. WYSOKOŚĆ), a nie do SKALA
            EditState s = states.get(stand.getUniqueId());
            if (s != null) applyStickyAxis(player, s);
        }
    }

    /** Aktywny stojak gracza albo null (jeśli zniknął). */
    public ArmorStand getActive(Player player) {
        UUID id = active.get(player.getUniqueId());
        if (id == null) return null;
        Entity e = Bukkit.getEntity(id);
        if (!(e instanceof ArmorStand stand) || stand.isDead()) {
            active.remove(player.getUniqueId());
            return null;
        }
        return stand;
    }

    /**
     * Bezpieczne usuwanie doczepionej encji: najpierw odpina ją od stojaka,
     * potem usuwa. Samo display.remove() na pasażerze potrafi zostawić encję
     * w świecie (widoczny „duch” po usunięciu stojaka).
     */
    private void detachAndRemove(ArmorStand stand, Entity display) {
        if (display == null) return;
        try {
            if (display.getVehicle() != null && stand.isValid()) {
                stand.removePassenger(display);
            }
        } catch (Throwable ignored) {
        }
        display.remove();
    }

    /** Usuwa doczepione części stojaka (encje + wpis w mapie). */
    public void forgetParts(ArmorStand stand) {
        turrets.remove(stand.getUniqueId());
        // usuń też pasek HP nad wieżyczką (osobna encja, nie pasażer stojaka)
        if (KEY_HP_TEXT != null) {
            String tid = stand.getPersistentDataContainer().get(KEY_HP_TEXT, PersistentDataType.STRING);
            if (tid != null) {
                try {
                    Entity te = Bukkit.getEntity(java.util.UUID.fromString(tid));
                    if (te instanceof TextDisplay td && !td.isDead()) td.remove();
                } catch (Throwable ignored) {
                }
            }
        }
        List<DisplayPart> list = parts.remove(stand.getUniqueId());
        if (list != null) {
            for (DisplayPart p : list) detachAndRemove(stand, p.display);
        }
    }

    /**
     * Usuwa osierocone części w promieniu: BlockDisplay/ItemDisplay bez pojazdu,
     * które zostały w świecie po usunięciu stojaka w starszych wersjach pluginu.
     * Pomija elementy /vslab i wizualne TNT min. Zwraca liczbę usuniętych encji.
     */
    public int cleanupOrphans(Player player, double radius) {
        int count = 0;
        for (Entity e : player.getWorld().getNearbyEntities(player.getLocation(), radius, radius, radius)) {
            if (!(e instanceof Display display)) continue;
            if (display.getVehicle() != null) continue; // pasażer stojaka - nie ruszamy
            if (e instanceof BlockDisplay bd) {
                if (plugin.getVerticalSlabManager().isManaged(bd)) continue; // /vslab
                if (plugin.getMineManager().isMineVisual(bd)) continue; // TNT min
            }
            display.remove();
            count++;
        }
        return count;
    }

    /**
     * Znajduje stojak, na który gracz patrzy (w zasięgu 20 bloków). Celowanie
     * liczone jest jako odległość osi wzroku od korpusu stojaka ORAZ od każdej
     * jego doczepionej części - dzięki temu działa też dla niewidzialnych
     * stojaków z widocznymi blokami (np. wieżyczka), na których bloki patrzysz.
     */
    public ArmorStand findLookedStand(Player player, boolean editableOnly) {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        ArmorStand best = null;
        double bestDist = 2.0;
        for (Entity e : player.getWorld().getNearbyEntities(eye, 20, 20, 20)) {
            if (!(e instanceof ArmorStand stand) || stand.isDead()) continue;
            if (editableOnly && !isEditable(stand)) continue;
            if (plugin.getStandPresetManager().isPreview(stand)) continue; // pomiń duchy-podglądy
            if (isBarrierStand(stand)) continue; // pomiń niewidoczne blokady bariery
            double d = rayDistance(eye.toVector(), dir, stand);
            if (d < bestDist) {
                bestDist = d;
                best = stand;
            }
        }
        return best;
    }

    /**
     * Odległość osi wzroku od stojaka: minimum z korpusu i WSZYSTKICH pasażerów
     * (nie tylko śledzonych) - dzięki temu bind/remove działają też po restarcie,
     * gdy śledzenie części nie zostało jeszcze odtworzone.
     */
    private double rayDistance(Vector origin, Vector dir, ArmorStand stand) {
        double min = pointRayDistance(origin, dir, stand.getBoundingBox().getCenter());
        for (Entity e : stand.getPassengers()) {
            if (!(e instanceof Display d)) continue;
            Vector3f t = d.getTransformation().getTranslation();
            Vector pos = d.getLocation().add(t.x(), t.y(), t.z()).toVector();
            min = Math.min(min, pointRayDistance(origin, dir, pos));
        }
        return min;
    }

    /** Odległość punktu od promienia (linii wzroku gracza). */
    private static double pointRayDistance(Vector origin, Vector dir, Vector point) {
        Vector to = point.clone().subtract(origin);
        double t = to.dot(dir);
        if (t < 0) return to.length();
        return origin.clone().add(dir.clone().multiply(t)).distance(point);
    }

    // ===================== doczepianie części =====================

    /**
     * Domyślne przesunięcie części z /stojak part <materiał> (bez x y z).
     * Pasażer stojaka renderuje się u góry (na wysokości głowy, ~2 bloki nad
     * stopami), więc ujemny offset Y obniża część. Domyślnie nowa część jest
     * WYŚRODKOWANA na stojaku (X/Z = 0 = środek bloku, na którym stoi stojak)
     * i leży dokładnie NA bloku (Y = -2.0 = poziom stóp stojaka).
     */
    private static final double DEFAULT_PART_TX = 0;
    private static final double DEFAULT_PART_TY = -2.0;
    private static final double DEFAULT_PART_TZ = 0;

    /**
     * Doczepia blok (BlockDisplay) albo przedmiot (ItemDisplay) do stojaka -
     * na domyślnym przesunięciu (patrz DEFAULT_PART_TX/TY/TZ).
     */
    public void attachPart(ArmorStand stand, Player player, Material material) {
        attachPart(stand, player, material, DEFAULT_PART_TX, DEFAULT_PART_TY, DEFAULT_PART_TZ);
    }

    /**
     * Doczepia przedmiot z ręki gracza (z CustomModelData, display properties,
     *enchantami itp.) jako ItemDisplay na stojaku - patrz /stojak part reka [x y z].
     *_texts/textures się zachowują dzięki pełnemu ItemStack.
     */
    public void attachPartFromHand(ArmorStand stand, Player player) {
        attachPartFromHand(stand, player, DEFAULT_PART_TX, DEFAULT_PART_TY, DEFAULT_PART_TZ);
    }

    /**
     * Doczepia przedmiot z ręki gracza z dokładnym przesunięciem względem stojaka.
     */
    public void attachPartFromHand(ArmorStand stand, Player player,
                                    double tx, double ty, double tz) {
        if (!isEditable(stand)) {
            AntiSpam.send(player, "§cNajpierw postaw/dołącz stojak do strojenia.");
            return;
        }
        if (Math.abs(tx) > 50 || Math.abs(ty) > 50 || Math.abs(tz) > 50) {
            AntiSpam.send(player, "§cTo wygląda na współrzędne świata, a nie przesunięcie względem stojaka - część "
                    + "wylądowałaby ponad 50 bloków od stojaka.");
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType() == Material.AIR) {
            AntiSpam.send(player, "§cTrzymaj przedmiot w ręce (prawy klik = doczepienie na stojak).");
            return;
        }
        pushUndoSnapshot(stand); // undo = cofnij doczepienie
        ItemStack item = held.clone(); // klon, żeby nie zużyć oryginału
        ItemDisplay id = stand.getWorld().spawn(stand.getLocation(), ItemDisplay.class);
        id.setItemStack(item);
        id.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        stand.addPassenger(id);
        DisplayPart part = new DisplayPart(id);
        part.tx = tx;
        part.ty = ty;
        part.tz = tz;
        // nazwa części: jeśli przedmiot ma displayName, użyj go
        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasDisplayName()) part.setName(meta.getDisplayName());
        }
        part.apply();
        List<DisplayPart> list = parts.computeIfAbsent(stand.getUniqueId(), k -> new ArrayList<>());
        list.add(part);
        EditState s = state(stand);
        if (s != null) {
            s.partIndex = list.size() - 1;
            applyStickyAxis(player, s);
        }
        info(stand, player);
        AntiSpam.sendActionBar(player, "§aDoczepiono część z ręki: §e"
                + item.getType().name().toLowerCase(Locale.ROOT));
    }

    /**
     * Doczepia blok/przedmiot do stojaka z dokładnym przesunięciem względem
     * stojaka (x y z w blokach) - patrz /stojak part <blok> x y z.
     */
    public void attachPart(ArmorStand stand, Player player, Material material,
                           double tx, double ty, double tz) {
        if (!isEditable(stand)) {
            AntiSpam.send(player, "§cNajpierw postaw/dołącz stojak do strojenia.");
            return;
        }
        if (Math.abs(tx) > 50 || Math.abs(ty) > 50 || Math.abs(tz) > 50) {
            AntiSpam.send(player, "§cTo wygląda na współrzędne świata, a nie przesunięcie względem stojaka - część "
                    + "wylądowałaby ponad 50 bloków od stojaka. x y z w /stojak part to OFFSET od stojaka "
                    + "(np. 0 2 0 = 2 bloki nad nim); żeby ustawić część na blok, na który patrzysz: §e/stojak tam§c.");
            return;
        }
        pushUndoSnapshot(stand); // undo = cofnij doczepienie (usuń tę część)
        Entity display;
        if (material.isBlock()) {
            BlockDisplay bd = stand.getWorld().spawn(stand.getLocation(), BlockDisplay.class);
            bd.setBlock(Bukkit.createBlockData(material));
            display = bd;
        } else {
            ItemDisplay id = stand.getWorld().spawn(stand.getLocation(), ItemDisplay.class);
            id.setItemStack(new ItemStack(material));
            id.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            display = id;
        }        stand.addPassenger(display);
        DisplayPart part = new DisplayPart(display);
        part.tx = tx;
        part.ty = ty;
        part.tz = tz;
        if (material.isBlock()) { // nowy blok od razu o 3/4 mniejszy
            part.scaleX = DEFAULT_PART_SCALE;
            part.scaleY = DEFAULT_PART_SCALE;
            part.scaleZ = DEFAULT_PART_SCALE;
        }
        // nazwa części: jeśli trzymasz w ręce nazwany przedmiot tego materiału,
        // część dostaje jego nazwę (potem w action barze: CZĘŚĆ N (nazwa, ...))
        try {
            ItemStack held = player.getInventory().getItemInMainHand();
            if (held != null && held.getType() == material && held.hasItemMeta()) {
                ItemMeta meta = held.getItemMeta();
                if (meta != null && meta.hasDisplayName()) part.setName(meta.getDisplayName());
            }
        } catch (Throwable ignored) {}

        part.apply();
        List<DisplayPart> list = parts.computeIfAbsent(stand.getUniqueId(), k -> new ArrayList<>());
        list.add(part);
        EditState s = state(stand);
        if (s != null) {
            s.partIndex = list.size() - 1;
            applyStickyAxis(player, s);
        }
        info(stand, player);
        AntiSpam.sendActionBar(player, "§aDoczepiono część: §e" + material.name().toLowerCase(Locale.ROOT));
    }

    /** Usuwa wybraną doczepioną część. Zwraca true, jeśli coś usunięto. */
    public boolean removeSelectedPart(ArmorStand stand, Player player) {
        EditState s = state(stand);
        List<DisplayPart> list = partsOf(stand);
        if (s == null || s.partIndex < 0 || s.partIndex >= list.size()) return false;
        pushUndoSnapshot(stand); // stan sprzed usunięcia
        DisplayPart p = list.remove(s.partIndex);
        detachAndRemove(stand, p.display);
        if (list.isEmpty()) {
            s.partIndex = -1;
            s.part = Part.HEAD;
        } else {
            s.partIndex = Math.min(s.partIndex, list.size() - 1);
        }
        applyStickyAxis(player, s);
        info(stand, player);
        AntiSpam.sendActionBar(player, "§aUsunięto część: §e" + p.material().name().toLowerCase(Locale.ROOT));
        return true;
    }

    /**
     * Usuwa część o numerze z action bara (CZĘŚĆ 1, CZĘŚĆ 2, ...) - patrz
     * /stojak usun czesc <liczba> [materiał]. Opcjonalnie tylko część z danego
     * materiału. Działa na stojaku, na który patrzysz, albo AKTYWNYM
     * (także po nazwie: /stojak <nazwa> usun czesc <liczba>). Zwraca true, gdy
     * coś usunięto.
     */
    public boolean removePartNumber(Player player, int number, Material material) {
        ArmorStand stand = tunedStand(player);
        if (stand == null) {
            AntiSpam.send(player, "§cNie patrzysz na żaden stojak i nie masz aktywnego.");
            return false;
        }
        List<DisplayPart> list = partsOf(stand);
        int idx = number - 1;
        if (idx < 0 || idx >= list.size()) {
            AntiSpam.send(player, "§cTen stojak ma " + list.size() + " " + (list.size() == 1 ? "część" : "części")
                    + " - nie ma części nr " + number + ".");
            return false;
        }
        DisplayPart p = list.get(idx);
        if (material != null && p.material() != material) {
            AntiSpam.send(player, "§cCzęść nr " + number + " to §e"
                    + p.material().name().toLowerCase(Locale.ROOT) + "§c, a nie "
                    + material.name().toLowerCase(Locale.ROOT) + ".");
            return false;
        }
        pushUndoSnapshot(stand); // undo = cofnij usunięcie (przywróć część)
        list.remove(idx);
        detachAndRemove(stand, p.display);
        EditState s = state(stand);
        if (s != null) {
            if (list.isEmpty()) {
                s.partIndex = -1;
                s.part = Part.HEAD;
            } else {
                s.partIndex = Math.min(s.partIndex, list.size() - 1);
            }
            applyStickyAxis(player, s);
        }
        info(stand, player);
        AntiSpam.sendActionBar(player, "§aUsunięto część: §e"
                + p.material().name().toLowerCase(Locale.ROOT) + " §7(nr " + number + ")");
        return true;
    }

    /**
     * Usuwa WSZYSTKIE części z danego materiału na stojaku (patrzonym albo
     * AKTYWNYM) - patrz /stojak usun all czesc <materiał>. Jedno cofnięcie
     * (/stojak undo) przywraca wszystkie naraz. Zwraca liczbę usuniętych.
     */
    public int removePartsByMaterial(Player player, Material material) {
        ArmorStand stand = tunedStand(player);
        if (stand == null) {
            AntiSpam.send(player, "§cNie patrzysz na żaden stojak i nie masz aktywnego.");
            return 0;
        }
        List<DisplayPart> list = partsOf(stand);
        int removed = 0;
        for (int i = list.size() - 1; i >= 0; i--) {
            DisplayPart p = list.get(i);
            if (p.material() != material) continue;
            if (removed == 0) pushUndoSnapshot(stand); // undo = cofnij wszystkie naraz
            list.remove(i);
            detachAndRemove(stand, p.display);
            removed++;
        }
        if (removed == 0) {
            AntiSpam.send(player, "§cTen stojak nie ma żadnej części z materiału §e"
                    + material.name().toLowerCase(Locale.ROOT) + "§c.");
            return 0;
        }
        EditState s = state(stand);
        if (s != null) {
            if (list.isEmpty()) {
                s.partIndex = -1;
                s.part = Part.HEAD;
            } else {
                s.partIndex = Math.min(s.partIndex, list.size() - 1);
            }
            applyStickyAxis(player, s);
        }
        info(stand, player);
        AntiSpam.sendActionBar(player, "§aUsunięto " + removed + " "
                + (removed == 1 ? "część" : "części") + " z materiału: §e"
                + material.name().toLowerCase(Locale.ROOT));
        return removed;
    }

    /**
     * Unikalne nazwy materiałów części na patrzonym/albo AKTYWNYM stojaku -
     * do tab-complete /stojak usun all czesc <materiał>.
     */
    public List<String> partMaterials(Player player) {
        ArmorStand stand = findLookedStand(player, false);
        if (stand == null) stand = getActive(player);
        if (stand == null) return List.of();
        List<String> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (DisplayPart p : partsOf(stand)) {
            String n = p.material().name().toLowerCase(Locale.ROOT);
            if (seen.add(n)) out.add(n);
        }
        return out;
    }

    /**
     * Usuwa doczepioną część (wizualny blok/przedmiot), na którą gracz patrzy
     * (promień wzroku vs. pozycje wszystkich pasażerów stojaków w pobliżu).
     * Działa też na stojakach spoza sesji (np. po restarcie) - wtedy bez undo.
     * Zwraca true, jeśli coś usunięto.
     */
    public boolean removeLookedPart(Player player) {
        return removeLookedPart(player, null);
    }

    /**
     * Jak {@link #removeLookedPart(Player)}, ale usuwa tylko część z podanego
     * materiału (blok albo przedmiot), na którą gracz patrzy - np. /stojak usun stone.
     */
    public boolean removeLookedPart(Player player, Material material) {
        LookedPart found = findLookedPart(player, material);
        if (found == null) {
            if (material != null) {
                AntiSpam.send(player, "§cNie patrzysz na żadną część z materiału §e"
                        + material.name().toLowerCase(Locale.ROOT) + "§c.");
            } else {
                AntiSpam.send(player, "§cNie patrzysz na żadną doczepioną część (blok/przedmiot).");
            }
            return false;
        }
        ArmorStand bestStand = found.stand;
        Display bestDisplay = found.display;
        List<DisplayPart> list = partsOf(bestStand);
        pushUndoSnapshot(bestStand); // stan sprzed usunięcia
        for (int i = 0; i < list.size(); i++) {
            if (!list.get(i).display.getUniqueId().equals(bestDisplay.getUniqueId())) continue;
            DisplayPart p = list.remove(i);
            detachAndRemove(bestStand, p.display);
            EditState s = state(bestStand);
            if (s != null) {
                if (list.isEmpty()) {
                    s.partIndex = -1;
                    s.part = Part.HEAD;
                } else {
                    s.partIndex = Math.min(s.partIndex, list.size() - 1);
                }
                applyStickyAxis(player, s);
            }
            if (s != null) info(bestStand, player);
            AntiSpam.sendActionBar(player, "§aUsunięto część: §e" + p.material().name().toLowerCase(Locale.ROOT));
            return true;
        }
        // część nieśledzona (np. sprzed restartu serwera) - usuń bezpośrednio
        detachAndRemove(bestStand, bestDisplay);
        AntiSpam.sendActionBar(player, "§aUsunięto część: §e" + materialOf(bestDisplay).name().toLowerCase(Locale.ROOT));
        return true;
    }

    // ===================== duplikowanie i statystyki części =====================

    /** Wynik wyszukiwania części po wzroku gracza. */
    private static class LookedPart {
        ArmorStand stand;
        Display display;

        LookedPart(ArmorStand stand, Display display) {
            this.stand = stand;
            this.display = display;
        }
    }

    /** Wynik wyszukiwania części do edycji: stojak + DisplayPart. */
    private static class PartTarget {
        ArmorStand stand;
        DisplayPart part;

        PartTarget(ArmorStand stand, DisplayPart part) {
            this.stand = stand;
            this.part = part;
        }
    }

    /**
     * Znajduje doczepioną część (wizualny blok/przedmiot), na którą gracz patrzy
     * (promień wzroku vs. pozycje pasażerów stojaków w pobliżu; material != null
     * zawęża do części z tego materiału). Zwraca null, gdy brak.
     */
    private LookedPart findLookedPart(Player player, Material material) {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        ArmorStand bestStand = null;
        Display bestDisplay = null;
        // Celowanie musi być PRECYZYJNE (0.75 bloka od środka części), a nie "w promieniu
        // 2 bloków" - przy gęstych kłębach części (np. wiele patyków) szeroki próg łapał
        // inny patyk niż ten, na który gracz patrzy, i LUSTRO kopiowało właśnie jego
        // ("patyk, którego nie było"). 0.75 pokrywa celowanie w środek i krawędzie
        // części o domyślnej skali (róg bloku 0.75-skali ~0.65 od środka).
        double bestDist = 0.75;
        for (Entity e : player.getWorld().getNearbyEntities(eye, 20, 20, 20)) {
            if (!(e instanceof ArmorStand stand) || stand.isDead()) continue;
            if (plugin.getStandPresetManager().isPreview(stand)) continue; // pomiń duchy-podglądy
            if (isBarrierStand(stand)) continue; // pomiń niewidoczne blokady bariery
            for (Entity p : stand.getPassengers()) {
                if (!(p instanceof Display d)) continue;
                if (material != null && materialOf(d) != material) continue;
                Vector3f t = d.getTransformation().getTranslation();
                Vector pos = d.getLocation().add(t.x(), t.y(), t.z()).toVector();
                double dist = pointRayDistance(eye.toVector(), dir, pos);
                if (dist < bestDist) {
                    bestDist = dist;
                    bestStand = stand;
                    bestDisplay = d;
                }
            }
        }
        if (bestStand == null || bestDisplay == null) return null;
        return new LookedPart(bestStand, bestDisplay);
    }

    /**
     * Znajduje część do edycji (duplikuj/stats): po materiale (materialName != null)
     * albo aktualnie wybranej (STICKiem); gdy nie ma - po wzroku (jak /stojak usun).
     */
    private PartTarget findPartTarget(Player player, String materialName) {
        ArmorStand stand = tunedStand(player);
        if (stand != null) {
            int idx = partIndexFor(player, stand, materialName);
            if (idx >= 0 && idx < partsOf(stand).size()) {
                return new PartTarget(stand, partsOf(stand).get(idx));
            }
        }
        Material m = materialName != null ? Material.matchMaterial(materialName) : null;
        if (materialName != null && m == null) return null; // nieznany materiał
        LookedPart looked = findLookedPart(player, m);
        if (looked == null) return null;
        List<DisplayPart> list = partsOf(looked.stand);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).display.getUniqueId().equals(looked.display.getUniqueId())) {
                setActive(player, looked.stand);
                return new PartTarget(looked.stand, list.get(i));
            }
        }
        return null; // część nieśledzona (np. sprzed restartu serwera)
    }

    /**
     * Duplikuje część (blok/przedmiot): kopiuje materiał, skalę, obrót i pozycję
     * (offset), a kopię stawia w miejscu oryginału przesuniętym o dx dy dz
     * (domyślnie 0 0 0 = dokładnie tam, gdzie oryginał). Kopia jest od razu
     * wybrana. Patrz /stojak duplikuj. Zwraca true, gdy zduplikowano.
     */
    public boolean duplicatePart(Player player, String materialName, double dx, double dy, double dz) {
        PartTarget target = findPartTarget(player, materialName);
        if (target == null) {
            AntiSpam.send(player, materialName != null
                    ? "§cNie ma części z materiału §e" + materialName + "§c (nie patrzysz na żadną ani nie masz wybranej)."
                    : "§cNie patrzysz na żadną część i żadna nie jest wybrana - kliknij stojak STICKiem albo podaj materiał.");
            return false;
        }
        return duplicateSource(player, target.stand, target.part, dx, dy, dz);
    }

    /**
     * Duplikuje część o podanym numerze (1 = pierwsza, zgodnie z numeracją
     * w action barze CZĘŚĆ 1, CZĘŚĆ 2, ... przy przewijaniu STICKiem).
     * Patrz /stojak duplikuj czesc <liczba>. Zwraca true, gdy zduplikowano.
     */
    public boolean duplicatePartNumber(Player player, int number, double dx, double dy, double dz) {
        ArmorStand stand = tunedStand(player);
        if (stand == null) {
            AntiSpam.send(player, "§cNie patrzysz na żaden stojak i nie masz aktywnego.");
            return false;
        }
        List<DisplayPart> list = partsOf(stand);
        int idx = number - 1;
        if (idx < 0 || idx >= list.size()) {
            AntiSpam.send(player, "§cTen stojak ma " + list.size() + " " + (list.size() == 1 ? "część" : "części")
                    + " - nie ma części nr " + number + ".");
            return false;
        }
        return duplicateSource(player, stand, list.get(idx), dx, dy, dz);
    }

    /** Wspólna część duplikowania: kopia z identycznymi statystykami + delta, wybór kopii. */
    private boolean duplicateSource(Player player, ArmorStand stand, DisplayPart src,
                                    double dx, double dy, double dz) {
        pushUndoSnapshot(stand); // undo = cofnij duplikację (usuń kopię)
        StandPreset.PartData d = src.toData();
        if (d == null) return false;
        d.tx += dx;
        d.ty += dy;
        d.tz += dz;
        attachPart(stand, d);
        EditState s = state(stand);
        if (s != null) {
            s.partIndex = partsOf(stand).size() - 1; // wybierz kopię od razu
            applyStickyAxis(player, s);
        }
        info(stand, player);
        AntiSpam.sendActionBar(player, "§aZduplikowano część: §e" + src.material().name().toLowerCase(Locale.ROOT)
                + " §7(offset: " + fmt(d.tx) + " " + fmt(d.ty) + " " + fmt(d.tz) + ")");
        return true;
    }

    /**
     * Lustrzana KOPIA części po drugiej stronie stojaka (idealna symetria):
     * /stojak lustro czesc [x|z] albo tryb LUSTRO w SUGAR. ŹRÓDŁO = część, na którą
     * gracz patrzy (a gdy nie patrzy na żadną - wybrana STICKiem). Kopia ma odbity
     * offset (tx lub tz), ujemną skalę na tej osi (jak ODBICIE) i odbite obroty -
     * dokładnie ta sama matematyka co /stojak lustro, ale dla JEDNEJ części, jako
     * NOWA część. Zwraca true, gdy skopiowano.
     */
    public boolean mirrorDuplicateSelected(Player player, String axis) {
        ArmorStand stand = tunedStand(player);
        if (stand == null) {
            AntiSpam.send(player, "§cNie patrzysz na żaden stojak i nie masz aktywnego.");
            return false;
        }
        EditState s = state(stand);
        List<DisplayPart> list = partsOf(stand);
        DisplayPart src = partUnderCrosshair(stand, player, list);
        if (src == null && s != null && s.partIndex >= 0 && s.partIndex < list.size()) {
            src = list.get(s.partIndex);
        }
        if (src == null) {
            AntiSpam.send(player, "§cNie patrzysz na żadną część tego stojaka i żadna nie jest wybrana - "
                    + "popatrz na część albo kliknij stojak STICKiem, żeby wybrać.");
            return false;
        }
        pushUndoSnapshot(stand); // undo = cofnij kopię
        DisplayPart copy = duplicateMirrored(stand, src, axis);
        if (copy != null && s != null) {
            s.partIndex = partsOf(stand).size() - 1; // wybierz kopię od razu
            s.axis = 7; // zostań w trybie LUSTRO
        }
        info(stand, player);
        AntiSpam.sendActionBar(player, "§aOdbicie lustrzane: skopiowano część w osi §e" + axis.toUpperCase()
                + "§a po drugiej stronie (wybrana, §7/stojak undo§a cofa).");
        return true;
    }

    /**
     * Część stojaka pod celownikiem gracza (jeśli to część TEGO stojaka), wg UUID -
     * dla LUSTRO. Zwraca null, gdy gracz nie patrzy na żadną część tego stojaka.
     */
    private DisplayPart partUnderCrosshair(ArmorStand stand, Player player, List<DisplayPart> list) {
        LookedPart looked = findLookedPart(player, null);
        if (looked == null || !looked.stand.equals(stand)) return null;
        for (DisplayPart p : list) {
            if (p.display.getUniqueId().equals(looked.display.getUniqueId())) return p;
        }
        return null;
    }

    /** Tworzy lustrzaną KOPIĘ części (nowa encja) - odbity offset/skala/obroty w osi X albo Z. */
    private DisplayPart duplicateMirrored(ArmorStand stand, DisplayPart src, String axis) {
        boolean mirrorX = axis.equalsIgnoreCase("x");
        StandPreset.PartData d = src.toData();
        if (d == null) return null;
        if (mirrorX) {
            d.tx = -d.tx;
            d.scaleX = -d.scaleX;
            d.rotY = -d.rotY;
            d.rotZ = -d.rotZ;
        } else {
            d.tz = -d.tz;
            d.scaleZ = -d.scaleZ;
            d.rotX = -d.rotX;
            d.rotY = -d.rotY;
        }
        attachPart(stand, d);
        List<DisplayPart> list = partsOf(stand);
        return list.isEmpty() ? null : list.get(list.size() - 1);
    }

    /**
     * Pokazuje w action barze statystyki części: offset (x y z względem stojaka),
     * skala i obrót - na wybranej, po materiale albo na tej, na którą gracz patrzy.
     * Patrz /stojak stats. Zwraca true, gdy coś pokazano.
     */
    public boolean partStats(Player player, String materialName) {
        PartTarget target = findPartTarget(player, materialName);
        if (target == null) {
            AntiSpam.send(player, materialName != null
                    ? "§cNie ma części z materiału §e" + materialName + "§c."
                    : "§cNie patrzysz na żadną część i żadna nie jest wybrana - kliknij stojak STICKiem albo podaj materiał.");
            return false;
        }
        DisplayPart p = target.part;
        List<DisplayPart> list = partsOf(target.stand);
        int idx = -1;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) == p) {
                idx = i + 1;
                break;
            }
        }
        AntiSpam.sendActionBar(player, "§aCZĘŚĆ " + Math.max(1, idx) + " §e" + p.name()
                + " §7| offset: " + fmt(p.tx) + " " + fmt(p.ty) + " " + fmt(p.tz)
                + " | skala: " + fmt(p.scaleX) + " " + fmt(p.scaleY) + " " + fmt(p.scaleZ)
                + " | obrót: " + fmt(p.rotX) + "° " + fmt(p.rotY) + "° " + fmt(p.rotZ) + "°");
        return true;
    }

    /**
     * Informacje o części w liczbach (skala, kordynaty, obrót, skręt, pochylenie) -
     * dokładnie tak, jak pokazują tryby edycji STICKiem/SUGARem: /stojak informacja.
     * Część: podana materiałem (materialName != null), aktualnie wybrana (STICK),
     * a gdy żadna nie jest wybrana - ta, na którą gracz patrzy. Zwraca true, gdy pokazano.
     */
    public boolean partInformation(Player player, String materialName) {
        PartTarget target = findPartTarget(player, materialName);
        if (target == null) {
            AntiSpam.send(player, materialName != null
                    ? "§cNie ma części z materiału §e" + materialName + "§c."
                    : "§cNie patrzysz na żadną część i żadna nie jest wybrana - kliknij stojak STICKiem albo podaj materiał.");
            return false;
        }
        DisplayPart p = target.part;
        List<DisplayPart> list = partsOf(target.stand);
        int idx = -1;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) == p) {
                idx = i + 1;
                break;
            }
        }
        String mat = p.material().name().toLowerCase(Locale.ROOT);
        AntiSpam.send(player, "§6=== CZĘŚĆ " + (idx > 0 ? idx + " " : "") + "§e" + mat + " §6===");
        AntiSpam.send(player, "§7Skala: §fX: §e" + fmt(p.scaleX) + "§7  Y: §e" + fmt(p.scaleY) + "§7  Z: §e" + fmt(p.scaleZ));
        AntiSpam.send(player, "§7Kordynaty: §fX: §e" + fmt(p.tx) + "§7  Y: §e" + fmt(p.ty) + "§7  Z: §e" + fmt(p.tz)
                + " §7(offset względem stojaka)");
        AntiSpam.send(player, "§7Obrót: §e" + fmt(p.rotY) + "°§7  Skręt: §e" + fmt(p.rotZ) + "°§7  Pochylenie: §e" + fmt(p.rotX) + "°");
        return true;
    }

    /** Materiał wizualny encji Display (blok BlockDisplay albo przedmiot ItemDisplay). */
    private static Material materialOf(Display d) {
        if (d instanceof BlockDisplay bd) return bd.getBlock().getMaterial();
        if (d instanceof ItemDisplay id) return id.getItemStack().getType();
        return Material.AIR;
    }

    /** Czy stojak ma wybraną doczepioną część? */
    public boolean hasSelectedPart(ArmorStand stand) {
        EditState s = state(stand);
        List<DisplayPart> list = partsOf(stand);
        return s != null && s.partIndex >= 0 && s.partIndex < list.size();
    }

    /** Wybiera pierwszą część z danego materiału. Zwraca false, gdy brak takiej części. */
    public boolean selectPartByMaterial(ArmorStand stand, Material material) {
        List<DisplayPart> list = partsOf(stand);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).material() == material) {
                EditState s = state(stand);
                if (s != null) {
                    s.partIndex = i;
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Wybiera KONKRETNĄ część po numerze z action bara (CZĘŚĆ 1, CZĘŚĆ 2, ...),
     * opcjonalnie tylko z danego materiału - patrz /stojak move czesc <liczba>
     * <x> <y> <z> oraz /stojak move <materiał>_<liczba> <x> <y> <z> (np. stone_2
     * = druga część z kamienia). Dzięki temu da się przesuwać konkretny przedmiot,
     * a nie losowy z pośród części tego samego typu. Zwraca false, gdy nie ma
     * takiej części (albo numer nie pasuje do materiału).
     */
    public boolean selectPartNumber(ArmorStand stand, int number, Material material) {
        List<DisplayPart> list = partsOf(stand);
        int idx = number - 1;
        if (idx < 0 || idx >= list.size()) return false;
        DisplayPart p = list.get(idx);
        if (material != null && p.material() != material) return false;
        EditState s = state(stand);
        if (s != null) {
            s.partIndex = idx;
        }
        return true;
    }

    /**
     * Wybiera część do operacji po materiale (move/scale/rotate): najpierw tę,
     * NA KTÓRĄ GRACZ PATRZY (jeśli pasuje do materiału) - dopiero potem pierwszą
     * część z tego materiału. Bez materiału: aktualnie wybraną (STICK).
     * Dzięki temu /stojak move stone 1 1 1 rusza kamień pod celownikiem, a nie
     * ukrytą pierwszą część z tego materiału (np. podstawę w ziemi).
     * Zwraca true, gdy coś wybrano.
     */
    public boolean selectPartForOperation(Player player, ArmorStand stand, String materialName) {
        if (materialName == null) return hasSelectedPart(stand);
        Material m = Material.matchMaterial(materialName);
        if (m == null) return false;
        // najpierw część, na którą gracz patrzy (jeśli pasuje do materiału)
        LookedPart looked = findLookedPart(player, m);
        if (looked != null) {
            List<DisplayPart> list = partsOf(stand);
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).display.getUniqueId().equals(looked.display.getUniqueId())) {
                    EditState s = state(stand);
                    if (s != null) {
                        s.partIndex = i;
                        applyStickyAxis(player, s);
                    }
                    return true;
                }
            }
        }
        return selectPartByMaterial(stand, m);
    }

    /**
     * Lustrzane odbicie CAŁEGO stojaka (wszystkie doczepione części naraz) względem
     * osi X albo Z - /stojak lustro [x|z]. Odbicie = odwrócenie przesunięcia na tej
     * osi oraz znaków obrotów (dla X: rotY i rotZ; dla Z: rotX i rotY) + yaw stojaka.
     * Jedno /stojak undo cofa odbicie. Zwraca liczbę odbitych części.
     */
    public int mirrorStand(ArmorStand stand, Player player, String axis) {
        if (!isEditable(stand)) {
            AntiSpam.send(player, "§cNajpierw postaw/dołącz stojak do strojenia.");
            return 0;
        }
        boolean mirrorX = axis == null || axis.equalsIgnoreCase("x");
        pushUndoSnapshot(stand); // undo = cofnij odbicie
        List<DisplayPart> list = partsOf(stand);
        for (DisplayPart p : list) {
            if (mirrorX) {
                p.tx = -p.tx;
                p.rotY = -p.rotY;
                p.rotZ = -p.rotZ;
            } else {
                p.tz = -p.tz;
                p.rotX = -p.rotX;
                p.rotY = -p.rotY;
            }
            p.apply();
        }
        // kierunek patrzenia stojaka też się odbija (yaw -> -yaw)
        Location l = stand.getLocation();
        l.setYaw(-l.getYaw());
        stand.teleport(l);
        info(stand, player);
        AntiSpam.sendActionBar(player, "§aOdbito lustrzanie stojak §7(oś " + (mirrorX ? "X" : "Z")
                + ", " + list.size() + " " + (list.size() == 1 ? "część" : "części")
                + "). §7/stojak undo cofa");
        return list.size();
    }

    /**
     * Przesuwa wybraną część o zadaną deltę (x y z w blokach) - patrz
     * /stojak move <x> <y> <z> oraz /stojak <materiał> move <x> <y> <z>.
     */
    public void moveSelectedPart(ArmorStand stand, Player player, double dx, double dy, double dz) {
        EditState s = state(stand);
        List<DisplayPart> list = partsOf(stand);
        if (s == null || s.partIndex < 0 || s.partIndex >= list.size()) return;
        pushUndoSnapshot(stand); // undo = cofnij przesunięcie
        DisplayPart p = list.get(s.partIndex);
        p.tx += dx;
        p.ty += dy;
        p.tz += dz;
        boolean snapped = isSnap(player);
        if (snapped) {
            p.tx = snap(p.tx, SNAP_MOVE_STEP);
            p.ty = snap(p.ty, SNAP_MOVE_STEP);
            p.tz = snap(p.tz, SNAP_MOVE_STEP);
        }
        p.apply();
        info(stand, player);
        AntiSpam.sendActionBar(player, "§aPrzesunięto: §eCZĘŚĆ " + (s.partIndex + 1) + " ("
                + p.material().name().toLowerCase(Locale.ROOT) + ")§a | offset: "
                + fmt(p.tx) + " " + fmt(p.ty) + " " + fmt(p.tz));
    }

    /**
     * Znajduje stojak do edycji (jak komendy): patrzony edytowalny, albo patrzony
     * (z automatycznym dołączeniem), albo aktywny gracza.
     */
    private ArmorStand tunedStand(Player player) {
        ArmorStand stand = findLookedStand(player, true);
        if (stand == null) {
            stand = findLookedStand(player, false);
            if (stand != null) bind(stand);
        }
        if (stand == null) {
            stand = getActive(player);
            if (stand != null && !isEditable(stand)) bind(stand);
        }
        if (stand != null) setActive(player, stand);
        return stand;
    }

    /** Indeks części: po materiale (materialName != null) albo aktualnie wybranej. */
    private int partIndexFor(Player player, ArmorStand stand, String materialName) {
        List<DisplayPart> list = partsOf(stand);
        if (materialName != null) {
            Material m = Material.matchMaterial(materialName);
            if (m == null) return -1;
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).material() == m) return i;
            }
            return -1;
        }
        EditState s = state(stand);
        if (s != null && s.partIndex >= 0 && s.partIndex < list.size()) return s.partIndex;
        return -1;
    }

    /**
     * Przesuwa wybraną część (albo część z materiału) tak, żeby jej środek trafił
     * w środek górnej powierzchni bloku, na który gracz patrzy - /stojak tam
     * [materiał]. Zwraca true, gdy przesunięto.
     */
    public boolean moveSelectedPartToLookedBlock(Player player, String materialName) {
        org.bukkit.block.Block block = player.getTargetBlockExact(100);
        if (block == null) {
            AntiSpam.send(player, "§cNie patrzysz na żaden blok (za daleko?).");
            return false;
        }
        ArmorStand stand = tunedStand(player);
        if (stand == null) {
            AntiSpam.send(player, "§cNie patrzysz na żaden stojak i nie masz aktywnego.");
            return false;
        }
        int idx = partIndexFor(player, stand, materialName);
        if (idx < 0) {
            AntiSpam.send(player, materialName != null
                    ? "§cTen stojak nie ma części z materiału: §e" + materialName + "§c."
                    : "§cNie wybrano części. Kliknij stojak STICKiem albo podaj materiał: §e/stojak tam stone§c.");
            return false;
        }
        DisplayPart p = partsOf(stand).get(idx);
        Location l = stand.getLocation();
        double dx = (block.getX() + 0.5) - (l.getX() + p.tx);
        double dy = (block.getY() + 1.0) - (l.getY() + p.ty);
        double dz = (block.getZ() + 0.5) - (l.getZ() + p.tz);
        if (Math.abs(dx) > 50 || Math.abs(dy) > 50 || Math.abs(dz) > 50) {
            AntiSpam.send(player, "§cBlok, na który patrzysz, jest ponad 50 bloków od części - część by tam "
                    + "poleciała (pewnie celujesz w zły blok). Zamiast tego: /stojak move <x> <y> <z>.");
            return false;
        }
        pushUndoSnapshot(stand); // undo = cofnij ustawienie na blok
        p.tx += dx;
        p.ty += dy;
        p.tz += dz;
        p.apply();
        info(stand, player);
        AntiSpam.sendActionBar(player, "§aCzęść ustawiona na blok, na który patrzysz.");
        return true;
    }

    /**
     * Dla tab-complete /stojak move: delta (dx, dy, dz), o jaką trzeba przesunąć
     * część, żeby jej środek trafił w blok pod celownikiem gracza. materialName =
     * część po materiale (null = wybrana). Zwraca null, gdy brak bloku/stołaka/części.
     */
    public String[] moveDeltaToLookedBlockText(Player player, String materialName) {
        org.bukkit.block.Block block = player.getTargetBlockExact(100);
        if (block == null) return null;
        ArmorStand stand = tunedStand(player);
        if (stand == null) return null;
        int idx = partIndexFor(player, stand, materialName);
        if (idx < 0) return null;
        DisplayPart p = partsOf(stand).get(idx);
        Location l = stand.getLocation();
        double dx = (block.getX() + 0.5) - (l.getX() + p.tx);
        double dy = (block.getY() + 1.0) - (l.getY() + p.ty);
        double dz = (block.getZ() + 0.5) - (l.getZ() + p.tz);
        return new String[]{num(dx), num(dy), num(dz)};
    }

    /**
     * Jak {@link #moveDeltaToLookedBlockText(Player, String)}, ale dla KONKRETNEJ
     * części po numerze z action bara (CZĘŚĆ 1, CZĘŚĆ 2, ...) - bez wybierania jej.
     * Dla tab-complete /stojak move czesc <liczba> <x|y|z>. Zwraca null, gdy brak
     * bloku/stojaka/części o tym numerze.
     */
    public String[] moveDeltaToLookedBlockText(Player player, int partNumber) {
        org.bukkit.block.Block block = player.getTargetBlockExact(100);
        if (block == null) return null;
        ArmorStand stand = tunedStand(player);
        if (stand == null) return null;
        List<DisplayPart> list = partsOf(stand);
        int idx = partNumber - 1;
        if (idx < 0 || idx >= list.size()) return null;
        DisplayPart p = list.get(idx);
        Location l = stand.getLocation();
        double dx = (block.getX() + 0.5) - (l.getX() + p.tx);
        double dy = (block.getY() + 1.0) - (l.getY() + p.ty);
        double dz = (block.getZ() + 0.5) - (l.getZ() + p.tz);
        return new String[]{num(dx), num(dy), num(dz)};
    }

    /**
     * Dla tab-complete /stojak part <materiał> x y z: OFFSET (względem stojaka),
     * przy którym część trafi w środek górnej powierzchni bloku pod celownikiem
     * gracza. x y z w /stojak part to przesunięcie od stojaka, więc podpowiadamy
     * różnicę (blok - stojak), a NIE światowe współrzędne (te potrafiły wrzucić
     * część kilkadziesiąt bloków nad stojak). Zwraca null, gdy brak bloku/stojaka.
     */
    public String[] partOffsetToLookedBlockText(Player player) {
        org.bukkit.block.Block block = player.getTargetBlockExact(100);
        if (block == null) return null;
        ArmorStand stand = tunedStand(player);
        if (stand == null) return null;
        Location l = stand.getLocation();
        double ox = (block.getX() + 0.5) - l.getX();
        double oy = (block.getY() + 1.0) - l.getY();
        double oz = (block.getZ() + 0.5) - l.getZ();
        return new String[]{num(ox), num(oy), num(oz)};
    }

    private static String num(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    /** Ustawia skalę wybranej części (wartości bezwzględne, zakres 0.01-20). */
    public void scaleSelectedPart(ArmorStand stand, Player player, double sx, double sy, double sz) {
        EditState s = state(stand);
        List<DisplayPart> list = partsOf(stand);
        if (s == null || s.partIndex < 0 || s.partIndex >= list.size()) return;
        pushUndoSnapshot(stand); // undo = cofnij zmianę skali
        DisplayPart p = list.get(s.partIndex);
        p.scaleX = clamp(sx, 0.01, 20);
        p.scaleY = clamp(sy, 0.01, 20);
        p.scaleZ = clamp(sz, 0.01, 20);
        p.apply();
        info(stand, player);
        AntiSpam.sendActionBar(player, "§aSkala części: " + fmt(p.scaleX) + " " + fmt(p.scaleY) + " " + fmt(p.scaleZ));
    }

    /**
     * Mnoży skalę WSZYSTKICH części przez podany współczynnik (np. 0.8 = cała
     * wieżyczka mniejsza o 20%) - patrz /stojak scale all <współczynnik>.
     * Zwraca liczbę przeskalowanych części.
     */
    public int scaleAllParts(ArmorStand stand, Player player, double factor) {
        List<DisplayPart> list = partsOf(stand);
        if (list.isEmpty()) return 0;
        pushUndoSnapshot(stand); // undo = cofnij przeskalowanie całości
        for (DisplayPart p : list) {
            p.scaleX = clamp(p.scaleX * factor, 0.01, 20);
            p.scaleY = clamp(p.scaleY * factor, 0.01, 20);
            p.scaleZ = clamp(p.scaleZ * factor, 0.01, 20);
            p.apply();
        }
        if (!list.isEmpty()) {
            info(stand, player);
        }
        AntiSpam.sendActionBar(player, "§aPrzeskalowano " + list.size() + " " + (list.size() == 1 ? "część" : "części")
                + " (×" + fmt(factor) + ").");
        return list.size();
    }

    /** Przywraca skalę WSZYSTKICH części do 1 (patrz /stojak scale all reset). */
    public int resetAllPartsScale(ArmorStand stand, Player player) {
        List<DisplayPart> list = partsOf(stand);
        if (list.isEmpty()) return 0;
        pushUndoSnapshot(stand); // undo = cofnij reset skali
        for (DisplayPart p : list) {
            p.scaleX = 1;
            p.scaleY = 1;
            p.scaleZ = 1;
            p.apply();
        }
        if (!list.isEmpty()) {
            info(stand, player);
        }
        AntiSpam.sendActionBar(player, "§aPrzywrócono skalę " + list.size() + " " + (list.size() == 1 ? "część" : "części") + ".");
        return list.size();
    }

    /**
     * Podmienia materiał wybranej części, zachowując skalę/obrót/pozycję
     * (tworzy nową encję BlockDisplay/ItemDisplay i podmienia pasażera).
     */
    public void changeSelectedPartMaterial(ArmorStand stand, Player player, Material material) {
        EditState s = state(stand);
        List<DisplayPart> list = partsOf(stand);
        if (s == null || s.partIndex < 0 || s.partIndex >= list.size()) return;
        pushUndoSnapshot(stand); // undo = przywróć poprzedni materiał
        DisplayPart p = list.get(s.partIndex);
        Display old = p.display;
        boolean wasGlowing = old.isGlowing();
        Entity replacement;
        if (material.isBlock()) {
            BlockDisplay bd = stand.getWorld().spawn(stand.getLocation(), BlockDisplay.class);
            bd.setBlock(Bukkit.createBlockData(material));
            replacement = bd;
        } else {
            ItemDisplay id = stand.getWorld().spawn(stand.getLocation(), ItemDisplay.class);
            id.setItemStack(new ItemStack(material));
            id.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            replacement = id;
        }
        // nazwa części zostaje przy zmianie materiału (jeśli była)
        String oldName = p.customName();
        if (oldName != null && KEY_PART_NAME != null) {
            replacement.getPersistentDataContainer().set(KEY_PART_NAME, PersistentDataType.STRING, oldName);
        }
        // jasność (ŚWIATŁO) też zostaje - flagę z PDC kopiujemy, żeby przetrwała restart
        if (p.bright && KEY_BRIGHT != null) {
            replacement.getPersistentDataContainer().set(KEY_BRIGHT, PersistentDataType.BYTE, (byte) 1);
        }
        if (wasGlowing) replacement.setGlowing(true);
        if (stand.isValid()) stand.removePassenger(old);
        stand.addPassenger(replacement);
        p.replaceDisplay((Display) replacement);
        old.remove();
        info(stand, player);
        AntiSpam.sendActionBar(player, "§aMateriał części: §e" + material.name().toLowerCase(Locale.ROOT));
    }

    /**
     * Obraca wybraną część o zadaną deltę w stopniach (albo zeruje kąty,
     * gdy reset=true) - patrz /stojak rotate <x> <y> <z>.
     */
    public void rotateSelectedPart(ArmorStand stand, Player player,
                                   double dx, double dy, double dz, boolean reset) {
        EditState s = state(stand);
        List<DisplayPart> list = partsOf(stand);
        if (s == null || s.partIndex < 0 || s.partIndex >= list.size()) return;
        pushUndoSnapshot(stand); // undo = cofnij obrót
        DisplayPart p = list.get(s.partIndex);
        boolean snapped = isSnap(player);
        if (reset) {
            p.rotX = 0;
            p.rotY = 0;
            p.rotZ = 0;
        } else {
            p.rotX += dx;
            p.rotY += dy;
            p.rotZ += dz;
            if (snapped) {
                p.rotX = snap(p.rotX, SNAP_ROT_STEP);
                p.rotY = snap(p.rotY, SNAP_ROT_STEP);
                p.rotZ = snap(p.rotZ, SNAP_ROT_STEP);
            }
        }
        p.apply();
        info(stand, player);
        AntiSpam.sendActionBar(player, reset
                ? "§aWyzerowano obrót części."
                : "§aObrót części: " + fmt(p.rotX) + "° " + fmt(p.rotY) + "° " + fmt(p.rotZ) + "°");
    }

    /**
     * Obraca CAŁY stojak (korpus + wszystkie doczepione części) o zadane kąty
     * w stopniach - patrz /stojak rotate <x> <y> <z>. Części NIE podążają za
     * yaw stojaka automatycznie (transformacja display jest w przestrzeni świata),
     * więc obracamy ręcznie każdą część: pozycję wokół środka stojaka i orientację
     * (R = R_obrotu * R_części), a do tego yaw/pitch samego stojaka. Roll (oś Z)
     * nie jest wspierany przez armor standa - jest pomijany. Jedno /stojak undo cofa.
     * Zwraca liczbę obróconych części (0 = sam stojak).
     */
    public int rotateStand(ArmorStand stand, Player player,
                           double dx, double dy, double dz, boolean reset) {
        if (!isEditable(stand)) {
            AntiSpam.send(player, "§cNajpierw postaw/dołącz stojak do strojenia.");
            return 0;
        }
        pushUndoSnapshot(stand); // undo = cofnij obrót
        List<DisplayPart> list = partsOf(stand);
        if (reset) {
            Location l = stand.getLocation();
            l.setYaw(0);
            l.setPitch(0);
            stand.teleport(l);
            for (DisplayPart p : list) {
                p.rotX = 0;
                p.rotY = 0;
                p.rotZ = 0;
                p.apply();
            }
            info(stand, player);
            AntiSpam.sendActionBar(player, "§aWyzerowano obrót stojaka (yaw/pitch i części).");
            return list.size();
        }
        double aX = Math.toRadians(dx);
        double aY = Math.toRadians(dy);
        double aZ = Math.toRadians(dz);
        Quaternionf rot = new Quaternionf().rotationXYZ((float) aX, (float) aY, (float) aZ);
        for (DisplayPart p : list) {
            // pozycja części wokół środka stojaka (ten sam obrót co kwaternion orientacji)
            Vector3f pos = rot.transform(new Vector3f((float) p.tx, (float) p.ty, (float) p.tz));
            p.tx = pos.x;
            p.ty = pos.y;
            p.tz = pos.z;
            // orientacja części: R = R_obrotu * R_części
            Quaternionf base = new Quaternionf().rotationXYZ(
                    (float) Math.toRadians(p.rotX), (float) Math.toRadians(p.rotY), (float) Math.toRadians(p.rotZ));
            Vector3f euler = rot.mul(base, new Quaternionf()).getEulerAnglesXYZ(new Vector3f());
            p.rotX = Math.toDegrees(euler.x);
            p.rotY = Math.toDegrees(euler.y);
            p.rotZ = Math.toDegrees(euler.z);
            p.apply();
        }
        // sam stojak: yaw += dy, pitch += dx (roll - oś Z - nie istnieje dla armor standa)
        Location l = stand.getLocation();
        l.setYaw(l.getYaw() + (float) dy);
        l.setPitch(l.getPitch() + (float) dx);
        stand.teleport(l);
        // po teleportcie parts muszą dostać nowe apply() bo display passenger
        // nie dziedziczy rotacji parenta automatycznie
        for (DisplayPart p : list) {
            p.apply();
        }
        info(stand, player);
        boolean roll = dz != 0;
        AntiSpam.sendActionBar(player, "§aObrót stojaka: " + fmt(dx) + "° " + fmt(dy) + "° " + fmt(dz) + "°"
                + (roll ? " §7(oś Z/roll nie działa na stojaku - pominięto)" : "")
                + (list.isEmpty() ? " §7(bez części)" : " §7(" + list.size() + " "
                + (list.size() == 1 ? "część obrócona)" : "części obróconych)")));
        return list.size();
    }

    public void clearParts(ArmorStand stand, Player player) {
        List<DisplayPart> list = parts.get(stand.getUniqueId());
        boolean any = list != null && !list.isEmpty();
        if (any) {
            pushUndoSnapshot(stand); // jedna partia - jedno /stojak undo przywraca wszystko
            parts.remove(stand.getUniqueId());
            for (DisplayPart p : list) {
                detachAndRemove(stand, p.display);
            }
        }
        EditState s = state(stand);
        if (s != null && s.partIndex >= 0) {
            s.partIndex = -1;
            s.part = Part.HEAD;
            applyStickyAxis(player, s);
        }
        if (any) AntiSpam.sendActionBar(player, "§aUsunięto wszystkie części.");
    }

    // ===================== cofanie zmian części (/stojak undo) =====================

    /**
     * Zapamiętuje pełny stan doczepionych części PRZED operacją - jedno
     * /stojak undo cofa jedną zmianę (doczepienie, usunięcie, move/scale/
     * rotate, zmianę materiału).
     */
    /** Zrzut obecnych części do zapisu (wspólny dla undo i /stojak ponow). */
    private List<StandPreset.PartData> snapshotParts(ArmorStand stand) {
        List<StandPreset.PartData> snapshot = new ArrayList<>();
        for (DisplayPart p : partsOf(stand)) {
            StandPreset.PartData d = p.toData();
            if (d != null) snapshot.add(d);
        }
        return snapshot;
    }

    private void pushUndoSnapshot(ArmorStand stand) {
        List<List<StandPreset.PartData>> stack = undo.computeIfAbsent(stand.getUniqueId(), k -> new ArrayList<>());
        stack.add(snapshotParts(stand));
        while (stack.size() > UNDO_LIMIT) stack.remove(0);
    }

    /**
     * Cofa ostatnią zmianę części (doczepienie, usunięcie, move/scale/rotate,
     * zmianę materiału) - odtwarza stan sprzed operacji. Zwraca liczbę
     * przywróconych części, -1 gdy cofnięto doczepienie (stan sprzed operacji
     * był pusty), albo 0, gdy nie ma czego cofać.
     */
    public int undoParts(ArmorStand stand, Player player) {
        if (!isEditable(stand)) return 0;
        List<List<StandPreset.PartData>> stack = undo.get(stand.getUniqueId());
        if (stack == null || stack.isEmpty()) return 0;
        List<StandPreset.PartData> batch = stack.remove(stack.size() - 1);
        // kopia bezpieczeństwa PRZED zmianą: /stojak ponow przywróci dokładnie ten stan
        lastUndoBackup.put(stand.getUniqueId(), snapshotParts(stand));
        // usuń obecne części i odtwórz stan sprzed operacji
        List<DisplayPart> current = parts.remove(stand.getUniqueId());
        if (current != null) {
            for (DisplayPart p : current) detachAndRemove(stand, p.display);
        }
        for (StandPreset.PartData d : batch) {
            attachPart(stand, d);
        }
        EditState s = state(stand);
        if (s != null) {
            List<DisplayPart> list = partsOf(stand);
            if (list.isEmpty()) {
                s.partIndex = -1;
                s.part = Part.HEAD;
            } else {
                s.partIndex = list.size() - 1; // zaznacz ostatnią przywróconą część
            }
            applyStickyAxis(player, s);
        }
        info(stand, player);
        return batch.isEmpty() ? -1 : batch.size();
    }

    /**
     * /stojak ponow (redo): przywraca stan części sprzed ostatniego /stojak undo -
     * np. gdy undo zdjęło wszystkie części (korpus niewidoczny = stojak „zniknął”).
     * Zwraca liczbę przywróconych części, -1 gdy stan sprzed undo był pusty,
     * 0 gdy nie ma czego przywrócić.
     */
    public int redoParts(ArmorStand stand, Player player) {
        if (!isEditable(stand)) return 0;
        List<StandPreset.PartData> backup = lastUndoBackup.remove(stand.getUniqueId());
        if (backup == null) return 0;
        // usuń obecne części i przywróć stan sprzed ostatniego undo
        List<DisplayPart> current = parts.remove(stand.getUniqueId());
        if (current != null) {
            for (DisplayPart p : current) detachAndRemove(stand, p.display);
        }
        for (StandPreset.PartData d : backup) {
            attachPart(stand, d);
        }
        EditState s = state(stand);
        if (s != null) {
            List<DisplayPart> list = partsOf(stand);
            if (list.isEmpty()) {
                s.partIndex = -1;
                s.part = Part.HEAD;
            } else {
                s.partIndex = list.size() - 1;
            }
            applyStickyAxis(player, s);
        }
        info(stand, player);
        return backup.isEmpty() ? -1 : backup.size();
    }

    /** Doczepione części stojaka (do zapisu/odczytu). */
    public List<DisplayPart> getParts(ArmorStand stand) {
        return partsOf(stand);
    }

    // ===================== przyciąganie (snap) =====================

    /** Czy gracz ma włączone przyciąganie dla move/rotate (/stojak snap). */
    public boolean isSnap(Player player) {
        return snap.contains(player.getUniqueId());
    }

    /** Włącza/wyłącza przyciąganie dla gracza (/stojak snap [on|off]). */
    public void setSnap(Player player, boolean enabled) {
        if (enabled) {
            snap.add(player.getUniqueId());
        } else {
            snap.remove(player.getUniqueId());
        }
    }

    /** Zaokrągla wartość do najbliższej wielokrotności kroku (0.25 bloka / 5°). */
    private static double snap(double v, double step) {
        return Math.round(v / step) * step;
    }

    /** Odtwarza część z zapisanych danych (bez zaznaczania jej w trybie edycji). */
    public void attachPart(ArmorStand stand, StandPreset.PartData data) {
        Material m = Material.matchMaterial(data.material);
        if (m == null) return;
        Entity display;
        if (m.isBlock()) {
            BlockDisplay bd = stand.getWorld().spawn(stand.getLocation(), BlockDisplay.class);
            bd.setBlock(Bukkit.createBlockData(m));
            display = bd;
        } else {
            ItemDisplay id = stand.getWorld().spawn(stand.getLocation(), ItemDisplay.class);
            id.setItemStack(new ItemStack(m));
            id.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            display = id;
        }
        stand.addPassenger(display);
        DisplayPart part = new DisplayPart(display);
        part.scaleX = data.scaleX;
        part.scaleY = data.scaleY;
        part.scaleZ = data.scaleZ;
        part.rotX = data.rotX;
        part.rotY = data.rotY;
        part.rotZ = data.rotZ;
        part.tx = data.tx;
        part.ty = data.ty;
        part.tz = data.tz;
        part.turret = data.turret;
        if (data.turret && KEY_TURRET != null) {
            display.getPersistentDataContainer().set(KEY_TURRET, PersistentDataType.BYTE, (byte) 1);
        }
        part.bright = data.bright;
        if (data.bright && KEY_BRIGHT != null) {
            display.getPersistentDataContainer().set(KEY_BRIGHT, PersistentDataType.BYTE, (byte) 1);
        }
        if (data.name != null && !data.name.isEmpty()) part.setName(data.name);
        part.apply();
        parts.computeIfAbsent(stand.getUniqueId(), k -> new ArrayList<>()).add(part);
    }

    // ===================== wieżyczka: obrót górnej części =====================

    /** Czy stojak ma część obrotową wieżyczki (flagę turret na którejś z części)? */
    public boolean hasTurretParts(ArmorStand stand) {
        if (stand == null || stand.isDead()) return false;
        for (Entity e : stand.getPassengers()) {
            if (e instanceof Display display) {
                Byte t = display.getPersistentDataContainer().get(KEY_TURRET, PersistentDataType.BYTE);
                if (t != null && t == 1) return true;
            }
        }
        return false;
    }

    /**
     * Włącza AUTO-NAMIERZANIE wieżyczki: górna część sama obraca się (pełne
     * 360°, płynnie, najkrótszą drogą) w stronę najbliższego wrogiego moba w
     * zasięgu i podąża za nim. Bez moba stoi w miejscu - nie kręci się bez
     * celu. Stan zapisuje się w PDC stojaka, więc przetrwa restart serwera.
     * Zwraca true, gdy stojak jest wieżyczką.
     */
    public boolean enableTurretAim(ArmorStand stand) {
        if (stand == null || stand.isDead()) return false;
        if (!hasTurretParts(stand)) return false;
        TurretSpin ts = turrets.get(stand.getUniqueId());
        ts = new TurretSpin(ts != null ? ts.angle : 0, true);
        turrets.put(stand.getUniqueId(), ts);
        if (KEY_AIM != null) stand.getPersistentDataContainer().set(KEY_AIM, PersistentDataType.BYTE, (byte) 1);
        initTurretVisuals(stand, ts); // część ARROW -> prawdziwa strzała 3D w lufie
        ensureTurretBarrier(stand); // niewidzialne blokady, żeby moby nie wchodziły w wieżyczkę
        return true;
    }

    /** Zapamiętany kierunek przodu wieżyczki (0 = +Z / południe). */
    public double turretFront(ArmorStand stand) {
        if (stand == null || stand.isDead()) return 0;
        Double f = stand.getPersistentDataContainer().get(KEY_FRONT, PersistentDataType.DOUBLE);
        return f != null ? f : 0;
    }

    /** Wznowienie po restarcie / dołączeniu stojaka (jeśli namierzanie było włączone). */
    public void resumeTurret(ArmorStand stand) {
        if (stand == null || stand.isDead()) return;
        if (!hasTurretParts(stand)) return;
        // zniszczalna wieżyczka (ma HP) musi być podatna na ataki mobów - markEditable
        // ustawia niewrażliwość, więc po restarcie zdejmujemy ją z powrotem
        if (turretMaxHp(stand) > 0) {
            stand.setInvulnerable(false);
            ensureTurretHpDisplay(stand); // odtwórz/odśwież pasek HP po restarcie
        }
        ensureTurretBarrier(stand); // odtwórz/odśwież barierę (po restarcie lub dołączeniu)
        Byte aim = stand.getPersistentDataContainer().get(KEY_AIM, PersistentDataType.BYTE);
        if (aim != null && aim == 1) {
            TurretSpin ts = new TurretSpin(0, true);
            turrets.put(stand.getUniqueId(), ts);
            initTurretVisuals(stand, ts); // część ARROW -> prawdziwa strzała 3D w lufie
        }
    }

    // ===================== bariera wieżyczki (blokowanie mobów) =====================

    /** Czy stojak to blokada bariery wieżyczki (niewidoczny stojak z kolizją)? */
    public boolean isBarrierStand(ArmorStand stand) {
        if (stand == null || stand.isDead()) return false;
        Byte t = stand.getPersistentDataContainer().get(KEY_BARRIER, PersistentDataType.BYTE);
        return t != null && t == 1;
    }

    /**
     * Bariera wieżyczki: 8 niewidocznych MAŁYCH stojaków na krawędzi bloku
     * (pół bloku od środka - 4 na bokach i 4 na rogach), z kolizją. Moby nie
     * wchodzą w wieżyczkę, tylko zatrzymują się przy niej. Blokady są
     * niewrażliwe (nie da się ich zniszczyć), a co tick wracają na miejsce,
     * gdyby mob je odepchnął. Link do wieżyczki siedzi w PDC, więc przetrwa
     * restart serwera (po restarcie blokady się odzyskują).
     */
    private void ensureTurretBarrier(ArmorStand stand) {
        if (stand == null || stand.isDead() || !hasTurretParts(stand)) return;
        // szybka ścieżka: bariera już stoi
        List<ArmorStand> existing = barriers.get(stand.getUniqueId());
        if (existing != null) {
            existing.removeIf(b -> b == null || b.isDead());
            if (!existing.isEmpty()) return;
        }
        // po restarcie odzyskaj blokady połączone z tą wieżyczką (PDC)
        List<ArmorStand> found = new ArrayList<>();
        for (Entity e : stand.getWorld().getNearbyEntities(stand.getLocation(), 2, 3, 2)) {
            if (!(e instanceof ArmorStand b) || b.isDead() || b == stand) continue;
            if (plugin.getStandPresetManager().isPreview(b)) continue;
            if (!isBarrierStand(b)) continue;
            String owner = b.getPersistentDataContainer().get(KEY_BARRIER_OWNER, PersistentDataType.STRING);
            if (owner != null && owner.equals(stand.getUniqueId().toString())) found.add(b);
        }
        if (!found.isEmpty()) {
            barriers.put(stand.getUniqueId(), found);
            return;
        }
        // nowe blokady: 8 małych stojaków na krawędzi bloku
        List<ArmorStand> blockers = new ArrayList<>();
        for (double[] o : TURRET_BARRIER_OFFSETS) {
            Location loc = stand.getLocation().clone().add(o[0], 0, o[1]);
            ArmorStand b = stand.getWorld().spawn(loc, ArmorStand.class);
            b.setVisible(false);
            b.setBasePlate(false);
            b.setArms(false);
            b.setSmall(true);
            b.setMarker(false); // marker nie ma hitboxu - blokada musi mieć kolizję
            b.setInvulnerable(true); // moby i gracze jej nie zniszczą
            b.setGravity(false);
            b.setSilent(true);
            b.setCanPickupItems(false);
            b.setCollidable(true);
            b.setRemoveWhenFarAway(false);
            b.setPersistent(true);
            b.addScoreboardTag("vd_bariera");
            if (KEY_BARRIER != null) {
                b.getPersistentDataContainer().set(KEY_BARRIER, PersistentDataType.BYTE, (byte) 1);
            }
            if (KEY_BARRIER_OWNER != null) {
                b.getPersistentDataContainer().set(
                        KEY_BARRIER_OWNER, PersistentDataType.STRING, stand.getUniqueId().toString());
            }
            blockers.add(b);
        }
        barriers.put(stand.getUniqueId(), blockers);
    }

    /** Przyciąga stojaki-blokady z powrotem na miejsce (gdyby mob je odepchnął). */
    private void snapTurretBarrier(ArmorStand stand) {
        List<ArmorStand> list = barriers.get(stand.getUniqueId());
        if (list == null || list.isEmpty()) return;
        list.removeIf(b -> b == null || b.isDead());
        Location base = stand.getLocation();
        for (int i = 0; i < list.size() && i < TURRET_BARRIER_OFFSETS.length; i++) {
            ArmorStand b = list.get(i);
            Location want = base.clone().add(TURRET_BARRIER_OFFSETS[i][0], 0, TURRET_BARRIER_OFFSETS[i][1]);
            if (b.getLocation().distanceSquared(want) > 0.0001) b.teleport(want);
        }
    }

    /**
     * Usuwa stojaki-blokady wieżyczki (przy usunięciu/zniszczeniu wieżyczki).
     * Najpierw stojaki śledzone w mapie, a potem - defensywnie - WSZYSTKIE
     * stojaki-blokady wokół stojaka po PDC właściciela, nawet gdyby nie było
     * ich w mapie (np. po restarcie, gdy mapa jest pusta, albo przy częściowym
     * sprzątaniu). Dzięki temu po zniszczeniu wieżyczki przez moba albo po
     * /stojak remove żaden stojak-blokada nie zostanie w świecie.
     */
    private void removeTurretBarrier(ArmorStand stand) {
        if (stand == null || stand.isDead()) return;
        UUID id = stand.getUniqueId();
        List<ArmorStand> list = barriers.remove(id);
        if (list != null) {
            for (ArmorStand b : list) {
                if (b != null && !b.isDead()) b.remove();
            }
        }
        // defensywnie usuń też blokady po PDC właściciela (sprzątanie po mapie)
        for (Entity e : stand.getWorld().getNearbyEntities(stand.getLocation(), 2, 4, 2)) {
            if (!(e instanceof ArmorStand b) || b.isDead() || b == stand) continue;
            if (!isBarrierStand(b)) continue;
            String owner = b.getPersistentDataContainer().get(KEY_BARRIER_OWNER, PersistentDataType.STRING);
            if (owner != null && owner.equals(id.toString())) b.remove();
        }
    }

    /** Usuwa stojaki-blokady z mapy (gdy wieżyczki już nie ma w świecie). */
    private void removeTurretBarrier(UUID turretId) {
        List<ArmorStand> list = barriers.remove(turretId);
        if (list == null) return;
        for (ArmorStand b : list) {
            if (b != null && !b.isDead()) b.remove();
        }
    }

    // ===================== pasek HP nad wieżyczką =====================

    /** Tekst paska: [obecne/max ♥]. */
    private static String hpText(ArmorStand stand) {
        Double cur = stand.getPersistentDataContainer().get(KEY_HP, PersistentDataType.DOUBLE);
        Double max = stand.getPersistentDataContainer().get(KEY_HP_MAX, PersistentDataType.DOUBLE);
        int c = cur != null ? (int) Math.ceil(cur) : 0;
        int m = max != null ? (int) Math.ceil(max) : 0;
        return "§7[§f" + c + "§7/§f" + m + " §c♥§7]";
    }

    /** Wysokość tekstu nad wieżyczką: nad najwyższą częścią górną + zapas, minus 1 blok. */
    private double turretTextHeight(ArmorStand stand) {
        double h = 2.6;
        for (DisplayPart p : partsOf(stand)) {
            if (!p.turret) continue;
            Vector3f tr = p.display.getTransformation().getTranslation();
            double top = p.display.getLocation().getY() - stand.getLocation().getY() + tr.y();
            h = Math.max(h, top + 0.5);
        }
        return Math.max(0, h - 1.0); // pasek HP 1 blok niżej (bliżej wieżyczki)
    }

    /**
     * Gwarantuje, że nad wieżyczką wisi pasek [obecne/max ♥] (TextDisplay,
     * nie jak u wieśniaków - sam tekst). Przywołuje istniejący (po restarcie)
     * albo tworzy nowy; zawsze odświeża tekst.
     */
    private void ensureTurretHpDisplay(ArmorStand stand) {
        if (stand == null || stand.isDead()) return;
        if (KEY_HP_TEXT != null) {
            String id = stand.getPersistentDataContainer().get(KEY_HP_TEXT, PersistentDataType.STRING);
            if (id != null) {
                try {
                    Entity e = Bukkit.getEntity(java.util.UUID.fromString(id));
                    if (e instanceof TextDisplay td && !td.isDead()) {
                        td.text(Component.text(hpText(stand)));
                        return;
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        Location loc = stand.getLocation().clone().add(0, turretTextHeight(stand), 0);
        TextDisplay td = stand.getWorld().spawn(loc, TextDisplay.class);
        td.text(Component.text(hpText(stand)));
        td.setBillboard(Display.Billboard.CENTER);
        td.setAlignment(TextDisplay.TextAlignment.CENTER);
        td.setDefaultBackground(true);
        td.setSeeThrough(false);
        td.setGravity(false);
        td.setInvulnerable(true);
        td.setSilent(true);
        td.setPersistent(true);
        td.setTransformation(new Transformation(
                new Vector3f(0, 0, 0), new AxisAngle4f(0f, 0f, 0f, 1f),
                new Vector3f(0.8f, 0.8f, 0.8f), new AxisAngle4f(0f, 0f, 0f, 1f)));
        if (KEY_HP_TEXT != null) {
            stand.getPersistentDataContainer().set(
                    KEY_HP_TEXT, PersistentDataType.STRING, td.getUniqueId().toString());
        }
    }

    // ===================== życie i zniszczenie wieżyczki =====================

    /** Maksymalne życie wieżyczki z PDC (0 = brak życia / niezniszczalna). */
    public static double turretMaxHp(ArmorStand stand) {
        if (stand == null || stand.isDead()) return 0;
        Double max = stand.getPersistentDataContainer().get(KEY_HP_MAX, PersistentDataType.DOUBLE);
        return max != null ? max : 0;
    }

    /** Aktualne życie wieżyczki z PDC (0 = brak wpisu). Zmienia się, gdy moby
     *  ją ranią - statystyki na czacie pokazują żywą wartość, nie z pliku. */
    public static double turretCurrentHp(ArmorStand stand) {
        if (stand == null || stand.isDead()) return 0;
        Double cur = stand.getPersistentDataContainer().get(KEY_HP, PersistentDataType.DOUBLE);
        return cur != null ? cur : 0;
    }

    /** Czy to wieżyczka z życiem (zniszczalna tylko przez moby)? */
    public boolean isDestructibleTurret(ArmorStand stand) {
        if (stand == null || stand.isDead()) return false;
        return turretMaxHp(stand) > 0 && hasTurretParts(stand);
    }

    /**
     * Nadaje wieżyczce życie (przy stawianiu): maksymalne i aktualne HP = maxHp.
     * Zdejmuje niewrażliwość stojaka - dzięki temu ataki mobów dochodzą do
     * skutku (zdarzenia obrażeń), a całą resztę blokuje listener.
     */
    public void initTurretHp(ArmorStand stand, double maxHp) {
        if (stand == null || stand.isDead() || maxHp <= 0) return;
        stand.setInvulnerable(false);
        if (KEY_HP_MAX != null) {
            stand.getPersistentDataContainer().set(KEY_HP_MAX, PersistentDataType.DOUBLE, maxHp);
        }
        if (KEY_HP != null) {
            stand.getPersistentDataContainer().set(KEY_HP, PersistentDataType.DOUBLE, maxHp);
        }
        ensureTurretHpDisplay(stand); // pasek [max/max ♥] nad wieżyczką
    }

    /**
     * Zapisuje nazwę presetu stojaka w PDC (np. wiezyczka_maga). Dzięki temu
     * animacja części i inne funkcje po nazwie działają też, gdy custom name
     * stojaka jest puste albo zostało zmienione (/stojak nazwa).
     */
    public void initPresetName(ArmorStand stand, String name) {
        if (stand == null || stand.isDead() || name == null) return;
        stand.getPersistentDataContainer().set(KEY_PRESET, PersistentDataType.STRING, name);
    }

    /** Nazwa presetu, z którego postawiono stojak (z PDC; null, gdy nieznana). */
    public String turretPresetName(ArmorStand stand) {
        if (stand == null || stand.isDead()) return null;
        return stand.getPersistentDataContainer().get(KEY_PRESET, PersistentDataType.STRING);
    }

    /**
     * Zapisuje statystyki wieżyczki z presetu (obrażenia, zasięg, interwał strzału,
     * zamrożenie) w PDC stojaka - przetrwają restart. Wartości <= 0 / brak w pliku
     * oznaczają domyślne (3.0 obrażeń, 8 bloków, co 40 ticków, bez efektu).
     */
    public void initTurretStats(ArmorStand stand, StandPreset preset) {
        if (stand == null || stand.isDead() || preset == null) return;
        PersistentDataContainer pdc = stand.getPersistentDataContainer();
        if (preset.damage > 0 && KEY_DMG != null) {
            pdc.set(KEY_DMG, PersistentDataType.DOUBLE, preset.damage);
        }
        if (preset.range > 0 && KEY_RANGE != null) {
            pdc.set(KEY_RANGE, PersistentDataType.DOUBLE, preset.range);
        }
        if (preset.fireInterval > 0 && KEY_FIRE_INTERVAL != null) {
            pdc.set(KEY_FIRE_INTERVAL, PersistentDataType.INTEGER, preset.fireInterval);
        }
        if (preset.freeze && KEY_FREEZE != null) {
            pdc.set(KEY_FREEZE, PersistentDataType.BYTE, (byte) 1);
        }
        if (preset.poison && KEY_POISON != null) {
            pdc.set(KEY_POISON, PersistentDataType.BYTE, (byte) 1);
        }
        if (preset.fire && KEY_FIRE != null) {
            pdc.set(KEY_FIRE, PersistentDataType.BYTE, (byte) 1);
        }
        if (preset.chain && KEY_CHAIN != null) {
            pdc.set(KEY_CHAIN, PersistentDataType.BYTE, (byte) 1);
        }
    }

    /** Obrażenia wieżyczki na strzał (z PDC albo domyślne 3.0). */
    public double turretDamage(ArmorStand stand) {
        Double v = stand.getPersistentDataContainer().get(KEY_DMG, PersistentDataType.DOUBLE);
        return v != null ? v : TURRET_DAMAGE_PER_SECOND;
    }

    /** Zasięg namierzania wieżyczki w blokach (z PDC albo domyślne 8). */
    public double turretRange(ArmorStand stand) {
        Double v = stand.getPersistentDataContainer().get(KEY_RANGE, PersistentDataType.DOUBLE);
        return v != null ? v : AIM_RANGE;
    }

    /** Co ile ticków wieżyczka strzela (z PDC albo domyślne 40). */
    public int turretFireInterval(ArmorStand stand) {
        Integer v = stand.getPersistentDataContainer().get(KEY_FIRE_INTERVAL, PersistentDataType.INTEGER);
        return v != null ? v : TURRET_FIRE_INTERVAL_TICKS;
    }

    /** Czy strzał wieżyczki zamraża moba (spowolnienie + szron)? */
    public boolean turretFreeze(ArmorStand stand) {
        Byte v = stand.getPersistentDataContainer().get(KEY_FREEZE, PersistentDataType.BYTE);
        return v != null && v == 1;
    }

    /** Czy strzał wieżyczki TRUJE moba (własny DoT 1 HP/s przez 8 s)? */
    public boolean turretPoison(ArmorStand stand) {
        Byte v = stand.getPersistentDataContainer().get(KEY_POISON, PersistentDataType.BYTE);
        return v != null && v == 1;
    }

    /** Czy strzał wieżyczki PODPALA moba na 8 s (jak zaklęty ogień)? */
    public boolean turretFire(ArmorStand stand) {
        Byte v = stand.getPersistentDataContainer().get(KEY_FIRE, PersistentDataType.BYTE);
        return v != null && v == 1;
    }

    /** Czy strzał wieżyczki skacze ŁAŃCUCHEM na kolejne moby (100% -> 65% -> 35%)? */
    public boolean turretChain(ArmorStand stand) {
        Byte v = stand.getPersistentDataContainer().get(KEY_CHAIN, PersistentDataType.BYTE);
        return v != null && v == 1;
    }

    /** Czy to wieżyczka MAGA (wariant z kryształową kulą)? Nazwa presetu z custom
     *  name albo z PDC (zapisana przy stawianiu) - ta sama lista co animacja kuli. */
    private boolean isMageTurret(ArmorStand stand) {
        String name = stand.getCustomName();
        if (name == null || name.isEmpty()) {
            name = stand.getPersistentDataContainer().get(KEY_PRESET, PersistentDataType.STRING);
        }
        return name != null && ANIMATED_PRESETS.contains(name.toLowerCase(Locale.ROOT));
    }

    /** Czy to PODSTAWOWA wieżyczka maga (wiezyczka_maga - ta, którą można ulepszyć
     *  do 4 żywiołów)? Ulepszone warianty (lód/ogień/trutka/elektryk) zwracają false. */
    public boolean isBaseMageTurret(ArmorStand stand) {
        String name = stand.getCustomName();
        if (name == null || name.isEmpty()) {
            name = stand.getPersistentDataContainer().get(KEY_PRESET, PersistentDataType.STRING);
        }
        return name != null && name.equalsIgnoreCase("wiezyczka_maga");
    }

    /** Zapisuje, który gracz POSTAWIŁ stojak (dostaje punkty za zabójstwa wieżyczki). */
    public void markPlacer(ArmorStand stand, Player player) {
        if (stand == null || stand.isDead() || player == null) return;
        if (KEY_PLACER != null) {
            stand.getPersistentDataContainer().set(KEY_PLACER, PersistentDataType.STRING,
                    player.getUniqueId().toString());
        }
    }

    /** Zaznacza wieżyczkę nazwą areny (sprzątanie po zakończeniu gry). */
    public void markArena(ArmorStand stand, String arenaName) {
        if (stand == null || stand.isDead() || arenaName == null) return;
        if (KEY_ARENA != null) {
            stand.getPersistentDataContainer().set(KEY_ARENA, PersistentDataType.STRING, arenaName);
        }
    }

    /**
     * Usuwa WSZYSTKIE wieżyczki z areny (oznaczone nazwą areny) w danym świecie -
     * wywoływane po zakończeniu gry. Działa też po restarcie (tag w PDC).
     * Stojaki oznaczone jako DEKORACJA mapy są pomijane.
     */
    public void destroyAllArenaTurrets(World world, String arenaName) {
        if (world == null || arenaName == null) return;
        for (Chunk chunk : world.getLoadedChunks()) {
            for (Entity e : chunk.getEntities()) {
                if (!(e instanceof ArmorStand stand)) continue;
                if (isDecoration(stand)) continue; // dekoracje mapy NIE są sprzątane po grze
                String tag = stand.getPersistentDataContainer().get(KEY_ARENA, PersistentDataType.STRING);
                if (tag != null && tag.equals(arenaName)) {
                    destroyTurret(stand);
                }
            }
        }
    }

    /** Wstrzymuje wszystkie wieżyczki (zatrzymuje namierzanie i strzały). */
    public void pauseAllTurrets() {
        for (Map.Entry<UUID, TurretSpin> entry : turrets.entrySet()) {
            entry.getValue().aim = false;
        }
    }

    /** Wznawia wszystkie wieżyczki (przywraca namierzanie). */
    public void resumeAllTurrets() {
        for (Map.Entry<UUID, TurretSpin> entry : turrets.entrySet()) {
            entry.getValue().aim = true;
        }
    }

    /** Czy stojak to DEKORACJA mapy (nie usuwana po zakończeniu areny)? */
    public boolean isDecoration(ArmorStand stand) {
        if (stand == null || stand.isDead()) return false;
        Byte t = stand.getPersistentDataContainer().get(KEY_DECORATION, PersistentDataType.BYTE);
        return t != null && t == 1;
    }

    /** Oznacza/odznacza stojak jako DEKORACJĘ mapy. Dekoracje nie są usuwane po
     *  zakończeniu areny (sprzątanie wieżyczek je pomija). Oznaczenie dekoracji
     *  wyłącza też mechanikę wieżyczki: namierzanie/strzały, barierę i pasek HP. */
    public void setDecoration(ArmorStand stand, boolean on) {
        if (stand == null || stand.isDead()) return;
        if (on) {
            stand.getPersistentDataContainer().set(KEY_DECORATION, PersistentDataType.BYTE, (byte) 1);
            // dekoracja nie strzela: wyrejestruj wieżyczkę i posprzątaj jej widoki
            turrets.remove(stand.getUniqueId());
            stand.getPersistentDataContainer().remove(KEY_AIM);
            removeTurretBarrier(stand);
            removeTurretHpText(stand);
        } else {
            stand.getPersistentDataContainer().remove(KEY_DECORATION);
        }
    }

    // ===================== klik-przełącznik stanów =====================

    /** Długość jednej fazy przejścia (ticki): 8 ticków = 0.4 s; całość ~0.8 s. */
    private static final int STATE_TRANSITION_TICKS = 8;

    /**
     * Animacja przejścia między stanami (klik-przełącznik): najpierw obecne
     * części MALEJĄ do skali 0, w połowie podmieniane na części stanu docelowego
     * (od skali 0), potem ROSNĄ do pełnej skali. Płynne, choć stany mogą mieć
     * zupełnie inne zestawy części.
     */
    private static class StateTransition {
        final UUID standId;
        List<DisplayPart> shrink;        // części malejące (obecny stan)
        List<DisplayPart> growParts;     // części rosnące (stan docelowy, po podmianie)
        final List<StandPreset.PartData> growData; // dane części docelowych
        int phase; // 0 = maleją, 1 = rosną
        int tick;

        StateTransition(UUID standId, List<DisplayPart> shrink,
                        List<StandPreset.PartData> growData) {
            this.standId = standId;
            this.shrink = shrink;
            this.growData = growData;
        }
    }

    /** Trwające przejścia stanów: UUID stojaka -> stan animacji. */
    private final Map<UUID, StateTransition> transitions = new HashMap<>();

    /** Czy stojak to klik-przełącznik stanów (/stojak funkcja zmiana <A> <B>)? */
    public boolean isStateToggle(ArmorStand stand) {
        if (stand == null || stand.isDead()) return false;
        PersistentDataContainer c = stand.getPersistentDataContainer();
        return c.has(KEY_STATE_A) && c.has(KEY_STATE_B);
    }

    /** Nazwa stanu A (albo null, gdy stojak nie jest przełącznikiem). */
    public String getStateA(ArmorStand stand) {
        return stand != null && !stand.isDead()
                ? stand.getPersistentDataContainer().get(KEY_STATE_A, PersistentDataType.STRING) : null;
    }

    /** Nazwa stanu B (albo null, gdy stojak nie jest przełącznikiem). */
    public String getStateB(ArmorStand stand) {
        return stand != null && !stand.isDead()
                ? stand.getPersistentDataContainer().get(KEY_STATE_B, PersistentDataType.STRING) : null;
    }

    /**
     * Łączy stojak z dwoma zapisanymi presetami (klik-przełącznik): od razu
     * ustawia stan A jako początkowy (podmienia części) i zapamiętuje powiązanie
     * w PDC, więc przetrwa restart serwera. Stojak zaczyna działać po zapisie:
     * /stojak save <nazwa> (powiązanie trafia do pliku presetu).
     */
    public void setStates(ArmorStand stand, String stateA, String stateB) {
        if (stand == null || stand.isDead()) return;
        PersistentDataContainer c = stand.getPersistentDataContainer();
        c.set(KEY_STATE_A, PersistentDataType.STRING, stateA);
        c.set(KEY_STATE_B, PersistentDataType.STRING, stateB);
        c.set(KEY_STATE_IDX, PersistentDataType.BYTE, (byte) 0);
        transitions.remove(stand.getUniqueId());
        // postaw stan A (początkowy) od razu - podmiana części bez animacji
        StandPreset preset = plugin.getStandPresetManager().loadPreset(stateA);
        if (preset != null) {
            applyPresetParts(stand, preset);
        }
    }

    /** Usuwa powiązanie stanów (/stojak funkcja zmiana off) - stojak wraca do zwykłego. */
    public void clearStates(ArmorStand stand) {
        if (stand == null || stand.isDead()) return;
        PersistentDataContainer c = stand.getPersistentDataContainer();
        c.remove(KEY_STATE_A);
        c.remove(KEY_STATE_B);
        c.remove(KEY_STATE_IDX);
        transitions.remove(stand.getUniqueId());
    }

    /**
     * Kliknięcie na stojak-przełącznik: podmienia części między stanami (A <-> B)
     * z płynnym przejściem (maleją, podmiana, rosną). Zwraca true, gdy przełączono.
     */
    public boolean toggleState(ArmorStand stand, Player player) {
        if (stand == null || stand.isDead() || !isStateToggle(stand)) return false;
        if (transitions.containsKey(stand.getUniqueId())) return false; // trwa przejście
        PersistentDataContainer c = stand.getPersistentDataContainer();
        Byte idx = c.get(KEY_STATE_IDX, PersistentDataType.BYTE);
        boolean atA = idx == null || idx != 1;
        String target = atA ? getStateB(stand) : getStateA(stand);
        StandPreset preset = plugin.getStandPresetManager().loadPreset(target);
        if (preset == null) {
            AntiSpam.sendActionBar(player, "§cBrak zapisu stanu §e" + target
                    + "§c - usuń powiązanie: §e/stojak funkcja zmiana off§c.");
            return false;
        }
        c.set(KEY_STATE_IDX, PersistentDataType.BYTE, (byte) (atA ? 1 : 0));
        List<DisplayPart> shrink = new ArrayList<>(partsOf(stand));
        transitions.put(stand.getUniqueId(),
                new StateTransition(stand.getUniqueId(), shrink, new ArrayList<>(preset.parts)));
        return true;
    }

    /** Podmienia części stojaka na części presetu NATYCHMIAST (bez animacji). */
    private void applyPresetParts(ArmorStand stand, StandPreset preset) {
        if (stand == null || stand.isDead()) return;
        List<DisplayPart> old = parts.remove(stand.getUniqueId());
        if (old != null) {
            for (DisplayPart p : old) detachAndRemove(stand, p.display);
        }
        for (StandPreset.PartData d : preset.parts) {
            attachPart(stand, d);
        }
        EditState s = state(stand);
        if (s != null) s.partIndex = -1; // nie zostawiaj zaznaczenia usuniętej części
    }

    /** Tick przejść stanów: maleją -> podmiana -> rosną. Wołany co tick (plugin). */
    public void tickStateTransitions() {
        if (transitions.isEmpty()) return;
        Iterator<Map.Entry<UUID, StateTransition>> it = transitions.entrySet().iterator();
        while (it.hasNext()) {
            StateTransition t = it.next().getValue();
            ArmorStand stand = (ArmorStand) Bukkit.getEntity(t.standId);
            if (stand == null || stand.isDead() || !stand.isValid()) {
                t.shrink = null;
                it.remove();
                continue;
            }
            t.tick++;
            if (t.phase == 0) {
                // maleją do skali 0
                double f = Math.max(0.0, 1.0 - (double) t.tick / STATE_TRANSITION_TICKS);
                if (t.shrink != null) {
                    for (DisplayPart p : t.shrink) {
                        if (p != null && p.display != null && !p.display.isDead()) p.applyTemporaryScale(f);
                    }
                }
                if (t.tick >= STATE_TRANSITION_TICKS) {
                    // podmiana: usuń stare, podepnij nowe (na razie w skali 0)
                    if (t.shrink != null) {
                        for (DisplayPart p : t.shrink) {
                            if (p != null && p.display != null && !p.display.isDead()) {
                                detachAndRemove(stand, p.display);
                            }
                        }
                    }
                    parts.remove(stand.getUniqueId());
                    t.shrink = null;
                    for (StandPreset.PartData d : t.growData) {
                        attachPart(stand, d);
                    }
                    t.growParts = new ArrayList<>(partsOf(stand));
                    for (DisplayPart p : t.growParts) {
                        if (p != null && p.display != null && !p.display.isDead()) p.applyTemporaryScale(0.0);
                    }
                    EditState s = state(stand);
                    if (s != null) s.partIndex = -1;
                    t.tick = 0;
                    t.phase = 1;
                }
            } else {
                // rosną do pełnej skali
                double f = Math.min(1.0, (double) t.tick / STATE_TRANSITION_TICKS);
                if (t.growParts != null) {
                    for (DisplayPart p : t.growParts) {
                        if (p != null && p.display != null && !p.display.isDead()) p.applyTemporaryScale(f);
                    }
                }
                if (t.tick >= STATE_TRANSITION_TICKS) {
                    for (DisplayPart p : t.growParts) {
                        if (p != null && p.display != null && !p.display.isDead()) p.apply();
                    }
                    t.growParts = null;
                    it.remove();
                }
            }
        }
    }

    /** Usuwa pasek HP nad wieżyczką (tekst nad stojakiem). */
    private void removeTurretHpText(ArmorStand stand) {
        try {
            String tid = stand.getPersistentDataContainer().get(KEY_HP_TEXT, PersistentDataType.STRING);
            if (tid != null) {
                Entity te = Bukkit.getEntity(java.util.UUID.fromString(tid));
                if (te instanceof TextDisplay td && !td.isDead()) td.remove();
            }
        } catch (Throwable ignored) {
        }
    }

    /** UUID gracza, który postawił stojak (null, gdy brak - np. postawione przez admina). */
    public UUID placerOf(ArmorStand stand) {
        if (stand == null || stand.isDead()) return null;
        if (KEY_PLACER == null) return null;
        String s = stand.getPersistentDataContainer().get(KEY_PLACER, PersistentDataType.STRING);
        if (s == null) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Odejmuje wieżyczce życie. Przy 0 wieżyczka zostaje zniszczona: przestaje
     * strzelać i po prostu znika (bez animacji). Zwraca true, gdy zniszczona.
     */
    public boolean damageTurret(ArmorStand stand, double dmg) {
        if (stand == null || stand.isDead()) return false;
        Double cur = stand.getPersistentDataContainer().get(KEY_HP, PersistentDataType.DOUBLE);
        Double max = stand.getPersistentDataContainer().get(KEY_HP_MAX, PersistentDataType.DOUBLE);
        if (cur == null || max == null || max <= 0) return false;
        double next = cur - dmg;
        if (next <= 0) {
            Location loc = stand.getLocation();
            org.bukkit.World w = stand.getWorld();
            destroyTurret(stand);
            for (Entity e : w.getNearbyEntities(loc, 20, 20, 20)) {
                if (e instanceof Player p) {
                    AntiSpam.sendActionBar(p, "§cWieżyczka została zniszczona!");
                }
            }
            return true;
        }
        if (KEY_HP != null) {
            stand.getPersistentDataContainer().set(KEY_HP, PersistentDataType.DOUBLE, next);
        }
        ensureTurretHpDisplay(stand); // odśwież pasek [obecne/max ♥]
        return false;
    }

    /**
     * Zniszczenie wieżyczki: po prostu znika (bez animacji) - wszystkie części,
     * bariera i pasek HP są usuwane razem ze stojakiem.
     */
    private void destroyTurret(ArmorStand stand) {
        if (stand == null || stand.isDead()) return;
        // wyrejestruj wieżyczkę i posprzątaj stan edycji; części zapamiętujemy
        // PRZED wyczyszczeniem mapy (potem partsOf zwróci już pustą listę)
        List<DisplayPart> all = new ArrayList<>(partsOf(stand));
        turrets.remove(stand.getUniqueId());
        removeTurretBarrier(stand); // usuń też stojaki-blokady bariery (wszystkie wokół)
        parts.remove(stand.getUniqueId());
        states.remove(stand.getUniqueId());
        undo.remove(stand.getUniqueId());
        lastUndoBackup.remove(stand.getUniqueId());
        active.entrySet().removeIf(en -> en.getValue().equals(stand.getUniqueId()));
        BukkitTask gt = glowTasks.remove(stand.getUniqueId());
        if (gt != null) gt.cancel();
        // usuń pasek HP nad wieżyczką
        String tid = stand.getPersistentDataContainer().get(KEY_HP_TEXT, PersistentDataType.STRING);
        if (tid != null) {
            try {
                Entity te = Bukkit.getEntity(java.util.UUID.fromString(tid));
                if (te instanceof TextDisplay td && !td.isDead()) td.remove();
            } catch (Throwable ignored) {
            }
        }
        // usuń wszystkie doczepione części (śledzone i nieśledzone) oraz stojak -
        // wieżyczka po prostu znika, bez animacji
        for (DisplayPart p : all) {
            try {
                if (p.display != null && !p.display.isDead()) p.display.remove();
            } catch (Throwable ignored) {
            }
        }
        for (Entity e : new java.util.ArrayList<>(stand.getPassengers())) {
            if (e instanceof Display) {
                try {
                    if (!e.isDead()) e.remove();
                } catch (Throwable ignored) {
                }
            }
        }
        stand.eject(); // ewentualne resztki pasażerów (gdyby coś zostało)
        stand.remove();
    }

    /**
     * Czy wieżyczka może namierzać ten byt? Namierza WYŁĄCZNIE moby z areny -
     * te, które pojawiły się z fali (oznaczone przez MobSpawner metadana
     * vd-arena). Moby przywołane przez gracza (jajka, /summon) NIE są atakowane.
     * Do tego pomija: graczy, wieśniaków (i wędrownych kupców), moby po stronie
     * gracza (oswojone albo oznaczone tagiem vd_sojusz) oraz stojaki pluginu.
     */
    private static boolean isTargetable(LivingEntity e) {
        if (e == null || e.isDead()) return false;
        // Faction rozstrzyga wszystko: gracze, wieśniacy, sojusznicy (tag
        // vd_sojusz), oswojone, stojaki i byty spoza areny -> nie WAVE.
        return Faction.of(e).isWave();
    }

    /** Najbliższy namierzalny mob w zasięgu, albo null. */
    private static LivingEntity nearestTarget(ArmorStand stand, double range) {
        LivingEntity best = null;
        double bestSq = range * range;
        for (Entity e : stand.getWorld().getNearbyEntities(stand.getLocation(), range, range, range)) {
            if (!(e instanceof LivingEntity le) || !isTargetable(le)) continue;
            double d = e.getLocation().distanceSquared(stand.getLocation());
            if (d < bestSq) {
                bestSq = d;
                best = le;
            }
        }
        return best;
    }

    /**
     * Znacznik aggro moba: po trafieniu strzałą wieżyczki mob dostaje PDC
     * (UUID wieżyczki + tick trafienia). Waniliowe AI nie potrafi trzymać
     * armor standa jako celu (moby go ignorują), a bariera odcina ścieżkę,
     * więc aggro jest wymuszane przez tickTurrets (cel + przyciąganie + cios).
     */
    public static void markTurretAggro(Monster m, ArmorStand turret) {
        if (m == null || m.isDead() || turret == null || turret.isDead()) return;
        if (KEY_TURRET_AGGRO != null) {
            m.getPersistentDataContainer().set(KEY_TURRET_AGGRO, PersistentDataType.STRING,
                    turret.getUniqueId().toString());
        }
        if (KEY_TURRET_AGGRO_TICK != null) {
            m.getPersistentDataContainer().set(KEY_TURRET_AGGRO_TICK, PersistentDataType.INTEGER,
                    Bukkit.getCurrentTick());
        }
    }

    /**
     * Wymuszone aggro: moby oznaczone po trafieniu atakują wieżyczkę, nawet
     * jeśli waniliowe AI by je ignorowało (moby same gubią cel, bo waniliowe
     * cele AI - gracze, wieśniacy - nadpisują stojak). Co tick: przypominamy
     * cel ORAZ PROWADZIMY moba do bariery - gdy jest daleko, nawigujemy go do
     * punktu postoju tuż za pierścieniem niewidzialnych blokad, a gdy już tam
     * stoi, zatrzymujemy go (żeby nie wchodził w wieżyczkę) i wymuszamy cios
     * wręcz (~1 atak/s). Cios idzie przez EntityDamageByEntityEvent, który
     * listener wieżyczki zamienia na obrażenia HP. Gdy aggro wygaśnie (mob nie
     * był już trafiany), mob wraca do normalnego zachowania.
     */
    private void aggroTurretAttackers(ArmorStand stand) {
        if (stand == null || stand.isDead()) return;
        int now = Bukkit.getCurrentTick();
        String id = stand.getUniqueId().toString();
        Location sl = stand.getLocation();
        for (Entity e : stand.getWorld().getNearbyEntities(sl,
                TURRET_AGGRO_RANGE, TURRET_AGGRO_RANGE, TURRET_AGGRO_RANGE)) {
            if (!(e instanceof Monster m) || m.isDead()) continue;
            if (m.getScoreboardTags().contains(TAG_ALLIED)) continue; // moby po stronie gracza
            PersistentDataContainer pdc = m.getPersistentDataContainer();
            if (KEY_TURRET_AGGRO == null || !pdc.has(KEY_TURRET_AGGRO, PersistentDataType.STRING)) continue;
            String owner = pdc.get(KEY_TURRET_AGGRO, PersistentDataType.STRING);
            if (!id.equals(owner)) continue; // aggro na inną wieżyczkę
            Integer hitTick = KEY_TURRET_AGGRO_TICK != null
                    ? pdc.get(KEY_TURRET_AGGRO_TICK, PersistentDataType.INTEGER) : null;
            if (hitTick == null || now - hitTick > TURRET_AGGRO_DURATION_TICKS) {
                // aggro wygasło (mob nie był już trafiany) - zdejmij znacznik i posprzątaj
                pdc.remove(KEY_TURRET_AGGRO);
                if (KEY_TURRET_AGGRO_TICK != null) pdc.remove(KEY_TURRET_AGGRO_TICK);
                lastTurretAttack.remove(m.getUniqueId());
                lastTurretNav.remove(m.getUniqueId());
                continue;
            }
            m.setTarget(stand); // przypomnij cel - inne cele AI (gracze) go nadpisują
            // PROWADZENIE DO BARIERY: kierunek od wieżyczki do moba (w poziomie)
            Vector toMob = m.getLocation().toVector().subtract(sl.toVector());
            toMob.setY(0);
            double horiz = toMob.length();
            if (horiz < 1e-4) {
                toMob = new Vector(1, 0, 0); // mob dokładnie na wieżyczce - wypchnij w bok
                horiz = 1.0;
            } else {
                toMob.normalize();
            }
            if (horiz > TURRET_STANDOFF) {
                // mob poza barierą - nawiguj do punktu postoju tuż za pierścieniem blokad
                // (odświeżaj ścieżkę rzadko, żeby mob szedł płynnie, a nie drgał)
                Location stop = sl.clone().add(toMob.clone().multiply(TURRET_STANDOFF));
                stop.setY(m.getLocation().getY());
                Integer lastNav = lastTurretNav.get(m.getUniqueId());
                if (lastNav == null || now - lastNav >= TURRET_NAV_INTERVAL
                        || !m.getPathfinder().hasPath()) {
                    lastTurretNav.put(m.getUniqueId(), now);
                    m.getPathfinder().moveTo(stop, 1.0);
                }
            } else if (m.getPathfinder().hasPath()) {
                // mob już stoi przy barierze - zatrzymaj go, żeby nie wchodził w wieżyczkę
                m.getPathfinder().stopPathfinding();
            }
            // wymuś cios, gdy mob jest już w zasięgu ręki (stoi przy barierze)
            double dist = m.getLocation().distance(sl);
            if (dist <= TURRET_ATTACK_RANGE) {
                Integer last = lastTurretAttack.get(m.getUniqueId());
                if (last == null || now - last >= TURRET_ATTACK_INTERVAL) {
                    lastTurretAttack.put(m.getUniqueId(), now);
                    try {
                        m.swingMainHand(); // animacja zamachu - bez niej mob "bije" niewidzialnie
                        m.attack(stand);   // cios wręcz moba -> obrażenia wieżyczki
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
    }

    /**
     * Interwał odświeżania wieżyczek (ticki) wynikający z ich aktualnej liczby
     * i ustawień config.yml (stojak.turret-dynamic-tick). Przy dużej liczbie
     * wieżyczek ich obsługa działa rzadziej, żeby nie obciążać serwera:
     *   <= threshold-2 wieżyczek -> co 1 tick (jak zawsze)
     *   >  threshold-2           -> co 2 ticki
     *   >  threshold-4           -> co 4 ticki
     * Próg ustawiony na 0 wyłącza dany stopień (zostaje interwał niższego).
     * Wartości czytane z config.yml przy każdym wywołaniu, więc /def reload
     * działa bez restartu serwera.
     */
    public int turretTickInterval() {
        if (!plugin.getConfig().getBoolean("stojak.turret-dynamic-tick.enabled", true)) return 1;
        int count = turrets.size();
        int threshold4 = Math.max(0, plugin.getConfig().getInt("stojak.turret-dynamic-tick.threshold-4", 50));
        int threshold2 = Math.max(0, plugin.getConfig().getInt("stojak.turret-dynamic-tick.threshold-2", 25));
        if (threshold4 > 0 && count > threshold4) return 4;
        if (threshold2 > 0 && count > threshold2) return 2;
        return 1;
    }

    /**
     * Tick wieżyczek: namierzanie obraca górę płynnie (najkrótszą drogą, pełne
     * 360°) w stronę najbliższego moba w zasięgu - śledzi go wzrokiem nawet za
     * blokiem, a strzela (3.0 obrażeń, raz na 2 sekundy) tylko z linią widzenia.
     * Bez moba kąt się nie zmienia i nic nie strzela. Przy dużej liczbie
     * wieżyczek (config stojak.turret-dynamic-tick) tick wykonuje się rzadziej
     * (co 2 lub 4 ticki), a limit obrotu jest skalowany przez interwał, żeby
     * prędkość obrotu pozostała ta sama. Kąt jest nakładany co tick, żeby
     * przetrwał edycję części.
     */
    public void tickTurrets() {
        if (turrets.isEmpty()) return;
        // dynamiczny tick: pomiń ticki, gdy wieżyczek jest dużo - obciążenie
        // spada proporcjonalnie, a cooldowny strzałów (w tickach gry) pozostają
        // poprawne, bo porównujemy ticki z Bukkit.getCurrentTick()
        int interval = turretTickInterval();
        if (interval > 1 && Bukkit.getCurrentTick() % interval != 0) return;
        // limit obrotu na aktualizację: 9°/tick, skalowany przez interwał, żeby
        // rzadsze odświeżanie nie spowalniało obracania góry wieżyczki
        double maxTurn = AIM_TURN_SPEED / 20.0 * interval;
        for (Map.Entry<UUID, TurretSpin> e : new ArrayList<>(turrets.entrySet())) {
            Entity ent = Bukkit.getEntity(e.getKey());
            if (!(ent instanceof ArmorStand stand) || stand.isDead()) {
                turrets.remove(e.getKey());
                removeTurretBarrier(e.getKey()); // sprzątnij barierę martwej wieżyczki
                continue;
            }
            TurretSpin ts = e.getValue();
            ensureTurretBarrier(stand); // bariera zawsze stoi (szybka ścieżka)
            snapTurretBarrier(stand);   // mob nie odepchnie blokad

            LivingEntity target = ts.aim ? nearestTarget(stand, turretRange(stand)) : null;
            // ZWYKŁA wieżyczka: górna część płynnie OBRACA SIĘ w stronę najbliższego
            // moba (najkrótszą drogą, max 9°/tick = 180°/s, pełne 360°) i podąża za
            // nim nawet za blokiem. Wieżyczki MAGA stoją nieruchomo - namierza
            // tylko pocisk. Przy rzadszym odświeżaniu (dynamiczny tick) limit kąta
            // rośnie proporcjonalnie (maxTurn), więc prędkość obrotu się nie zmienia.
            if (target != null && !isMageTurret(stand)) {
                Location sl = stand.getLocation();
                Location tl = target.getLocation();
                double dx = tl.getX() - sl.getX();
                double dz = tl.getZ() - sl.getZ();
                double targetAngle = turretFront(stand) + Math.toDegrees(Math.atan2(dx, dz));
                targetAngle = (targetAngle % 360 + 360) % 360;
                double delta = (targetAngle - ts.angle + 540) % 360 - 180;
                ts.angle = ts.angle + Math.copySign(Math.min(Math.abs(delta), maxTurn), delta);
                ts.angle = (ts.angle % 360 + 360) % 360;
            }
            if (target != null) {
                // Strzał tylko z linią widzenia, co turretFireInterval ticków; ostrzelany
                // mob dostaje wieżyczkę jako cel i zaczyna ją atakować (cel + aggro
                // wymuszane w spawnTurretShot).
                int now = Bukkit.getCurrentTick();
                // pierwszy strzał dopiero po krótkim cooldownie od POSTAWIENIA (0,5 s) -
                // żeby wieżyczka nie strzelała natychmiast po pojawieniu się
                if (now - ts.placeTick >= TURRET_PLACEMENT_COOLDOWN_TICKS
                        && now - ts.lastDamageTick >= turretFireInterval(stand)
                        && stand.hasLineOfSight(target)) {
                    ts.lastDamageTick = now;
                    spawnTurretShot(stand, target, ts);
                    ts.recoil = 1.0; // odrzut górnej części po strzale
                }
            }
            // odrzut wygasa stopniowo po strzale (1.0 -> 0.0 w 4 ticki = 0,2 s)
            ts.recoil = Math.max(0.0, ts.recoil - 0.25);
            // ZWYKŁA wieżyczka: obrót za mobem (ts.angle) + odrzut po strzale.
            // Wieżyczki maga: konstrukcja stoi NIERUCHOMO (kąt 0), bez odrzutu.
            double recoilDepth = isMageTurret(stand) ? 0.0 : ts.recoil * RECOIL_DEPTH;
            for (DisplayPart p : partsOf(stand)) {
                if (p.turret) p.applyWithYaw(isMageTurret(stand) ? 0 : ts.angle, recoilDepth);
            }
            aggroTurretAttackers(stand); // moby po trafieniu atakują wieżyczkę
            syncTurretHpDisplay(stand); // pasek HP podąża za stojakiem (np. /stojak move)

            // ambientowe iskry elektryczne: TYLKO wieżyczka ELEKTRYKA (30% szans na
            // tick, 2 sztuki, delikatnie rozrzucone wokół środka, 1/3 bloka niżej).
            // Podstawowa wieżyczka magii NIE ma już żadnych ambientowych partiklesów,
            // a zwykła wieżyczka strzelnicza nigdy ich nie miała.
            double cy = turretCenterOffset(stand);
            if (turretChain(stand) && random.nextInt(100) < 30) {
                Location sl = stand.getLocation();
                stand.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,
                        sl.clone().add(0, cy + 2.0 / 3.0, 0),
                        2, 0.15, 0.15, 0.15, 0.004);
            }

            // wieżyczka ognia: rzadkie, losowe drobiny małego płomienia w środku
            // wieżyczki - ~5% szans na tick, bez stałego cooldownu
            if (turretFire(stand) && random.nextInt(100) < 5) {
                Location sl = stand.getLocation();
                stand.getWorld().spawnParticle(Particle.SMALL_FLAME,
                        sl.clone().add(0, cy, 0), 1, 0, 0, 0, 0);
            }

            // wieżyczka trutki: rzadkie, losowe drobiny sneeze (trująca zielonawa mgiełka)
            // w środku wieżyczki - ~5% szans na tick, bez stałego cooldownu.
            // O 1 blok WYŻEJ (ustalone z graczem).
            if (turretPoison(stand) && random.nextInt(100) < 5) {
                Location sl = stand.getLocation();
                stand.getWorld().spawnParticle(Particle.SNEEZE,
                        sl.clone().add(0, cy + 1.0, 0), 1, 0, 0, 0, 0);
            }

        }
    }

    /**
     * Animacja części stojaka (co tick): bloki kryształowej kuli wieżyczki maga
     * (emerald, lód, glowstone, magma) unoszą się delikatnie w rytmie: w górę ->
     * krótka pauza -> w dół -> krótka pauza (pętla) i jednocześnie kręcą się
     * lekko w lewo wokół osi Y - 1 pełny obrót (360°) na 3 sekundy.
     * Odchylenie i obrót to czysta nakładka (applyAnimated) - zapisane wartości
     * części nie ruszają się, więc save/restart zawsze wracają do stanu z pliku.
     * Części obrotowe wieżyczki (turret: true) są pomijane - ich obrót steruje
     * celowanie, więc bloki kuli nie powinny dostawać tej flagi.
     */
    public void tickPartAnimations() {
        if (parts.isEmpty()) {
            partAnims.clear();
            return;
        }
        Set<UUID> alive = new HashSet<>();
        for (Map.Entry<UUID, List<DisplayPart>> e : new ArrayList<>(parts.entrySet())) {
            Entity ent = Bukkit.getEntity(e.getKey());
            if (!(ent instanceof ArmorStand stand) || stand.isDead()) {
                partAnims.remove(e.getKey());
                continue;
            }
            String name = stand.getCustomName();
            if (name == null || name.isEmpty()) {
                // nazwa presetu z PDC (zapisana przy stawianiu) - animacja działa też,
                // gdy custom name jest puste albo przemianowane
                name = stand.getPersistentDataContainer().get(KEY_PRESET, PersistentDataType.STRING);
            }
            if (name == null || !ANIMATED_PRESETS.contains(name.toLowerCase(Locale.ROOT))) {
                partAnims.remove(e.getKey());
                continue;
            }
            alive.add(e.getKey());
            PartAnimState st = partAnims.computeIfAbsent(e.getKey(), k -> new PartAnimState());
            st.ry = (st.ry + SPIN_Y_DEG) % 360;
            double offset = floatOffset(st.tick++);
            for (DisplayPart p : e.getValue()) {
                if (p.turret) continue; // część obrotowa wieżyczki - jej obrót steruje celowanie
                if (!FLOAT_MATERIALS.contains(p.material())) continue;
                p.applyAnimated(offset, 0, st.ry, 0);
            }
        }
        partAnims.keySet().retainAll(alive);
    }

    /** Odchylenie Y w danym ticku fazy: płynny wzrost, pauza, płynny spadek, pauza. */
    private static double floatOffset(int tick) {
        int cycle = 2 * (FLOAT_MOVE_TICKS + FLOAT_PAUSE_TICKS);
        int phase = Math.floorMod(tick, cycle);
        if (phase < FLOAT_MOVE_TICKS) {
            return FLOAT_AMPLITUDE * smooth((double) phase / FLOAT_MOVE_TICKS);
        }
        phase -= FLOAT_MOVE_TICKS;
        if (phase < FLOAT_PAUSE_TICKS) return FLOAT_AMPLITUDE;
        phase -= FLOAT_PAUSE_TICKS;
        if (phase < FLOAT_MOVE_TICKS) {
            return FLOAT_AMPLITUDE * (1 - smooth((double) phase / FLOAT_MOVE_TICKS));
        }
        return 0;
    }

    /** Wygładzanie ease-in-out (smoothstep) dla delikatnego startu/końca ruchu. */
    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    // ===================== trucizna wieżyczki (DoT) =====================

    /**
     * Truje moba (wieżyczka trutki): odświeża 8-sekundowy czas trucizny. Obrażenia
     * 1 HP/s zadaje tickPoisons - moby są odporne na waniliową truciznę, więc nie
     * używamy efektu POISON (poza wizualizacją zielonych drobin przy trafieniu).
     */
    private void applyPoison(LivingEntity mob, ArmorStand turret) {
        PoisonState st = poisons.computeIfAbsent(mob.getUniqueId(), k -> new PoisonState());
        st.ticksLeft = POISON_DURATION_TICKS;
        if (turret != null) st.turretUuid = turret.getUniqueId();
    }

    /**
     * Tick trucizny (co tick): zadaje 1 HP na sekundę zatrutym mobom przez 8 sekund.
     * Obrażenia idą OD WIERZYCZKI (killer = stojak), więc zabójstwo daje punkty
     * graczowi, który ją postawił. Po wygaśnięciu mob przestaje być truty.
     */
    public void tickPoisons() {
        if (poisons.isEmpty()) return;
        poisons.entrySet().removeIf(e -> {
            Entity ent = Bukkit.getEntity(e.getKey());
            if (!(ent instanceof LivingEntity mob) || mob.isDead()) return true;
            PoisonState st = e.getValue();
            st.ticksLeft--;
            if (st.ticksLeft <= 0) return true;
            st.accumulator += POISON_DPS / 20.0;
            if (st.accumulator >= 1) {
                st.accumulator -= 1;
                if (mob.isDead()) return true;
                // źródło obrażeń = wieżyczka (kredyt za zabójstwo); gdy zniknęła - obrażenia bez właściciela
                ArmorStand turret = st.turretUuid != null
                        ? (Bukkit.getEntity(st.turretUuid) instanceof ArmorStand s && !s.isDead() ? s : null)
                        : null;
                // TRUCIZNA NIE ODPYCHA moba (odpychać może tylko samo trafienie
                // wieżyczki): zapamiętujemy prędkość przed obrażeniami i przywracamy
                // ją po nich, żeby DoT nie popychał moba od wieżyczki.
                org.bukkit.util.Vector vel = mob.getVelocity();
                if (turret != null) {
                    mob.damage(1.0, turret);
                } else {
                    mob.damage(1.0);
                }
                mob.setVelocity(vel);
                mob.getWorld().spawnParticle(Particle.DUST,
                        mob.getLocation().clone().add(0, mob.getHeight() * 0.7, 0),
                        6, 0.3, 0.3, 0.3, 0,
                        new Particle.DustOptions(Color.fromRGB(60, 190, 60), 0.8f));
            }
            return false;
        });
    }

    // ===================== łańcuch elektryczny wieżyczki =====================

    /** Najbliższy namierzalny mob wokół punktu (poza wykluczonymi), albo null. */
    private static LivingEntity nearestTargetableFrom(World world, Location loc, double range, Set<UUID> exclude) {
        LivingEntity best = null;
        double bestSq = range * range;
        for (Entity e : world.getNearbyEntities(loc, range, range, range)) {
            if (!(e instanceof LivingEntity le) || !isTargetable(le)) continue;
            if (exclude.contains(le.getUniqueId())) continue;
            double d = le.getLocation().distanceSquared(loc);
            if (d < bestSq) {
                bestSq = d;
                best = le;
            }
        }
        return best;
    }

    /** Rysuje wiązkę partikli między dwoma punktami (jak laser różdżki magika). */
    private static void drawParticleBeam(World world, Location from, Location to, Particle particle, Object data) {
        Vector dir = to.toVector().subtract(from.toVector());
        double length = dir.length();
        if (length < 0.01) return;
        Vector dirN = dir.normalize();
        for (double d = 0; d < length; d += 0.3) {
            Location point = from.clone().add(dirN.clone().multiply(d));
            world.spawnParticle(particle, point, 1, 0, 0, 0, 0, data);
        }
    }

    /**
     * Łańcuch elektryczny wieżyczki elektryka: po trafieniu głównego moba wiązka
     * skacze na najbliższe moby wokół (hop do 5 bloków od poprzedniego, max 3 cele):
     * 100% -> 65% -> 35% obrażeń. Elektryczne iskry (ELECTRIC_SPARK) łączą cele.
     * Obrażenia idą OD WIERZYCZKI - każdy trafiony mob dostaje aggro, a punkty za
     * zabójstwo trafiają do gracza, który postawił wieżyczkę.
     */
    private void chainLightning(ArmorStand stand, Monster first, double baseDamage) {
        try {
            World world = stand.getWorld();
            Set<UUID> hit = new HashSet<>();
            hit.add(first.getUniqueId());
            Location prev = first.getLocation().clone().add(0, first.getHeight() * 0.5, 0);
            for (double mult : CHAIN_DAMAGE_MULTIPLIERS) {
                LivingEntity next = nearestTargetableFrom(world, prev, CHAIN_HOP_RANGE, hit);
                if (!(next instanceof Monster nm) || nm.isDead()) break;
                hit.add(nm.getUniqueId());
                nm.damage(baseDamage * mult, stand);
                nm.setTarget(stand);
                markTurretAggro(nm, stand);
                Location nextLoc = nm.getLocation().clone().add(0, nm.getHeight() * 0.5, 0);
                drawParticleBeam(world, prev, nextLoc, Particle.ELECTRIC_SPARK, null);
                nm.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, nextLoc, 4, 0.2, 0.2, 0.2, 0.01);
                prev = nextLoc;
            }
        } catch (Throwable ignored) {
        }
    }

    /** Przestawia pasek HP nad wieżyczką, jeśli istnieje (podąża za stojakiem). */
    private void syncTurretHpDisplay(ArmorStand stand) {
        if (stand == null || stand.isDead()) return;
        if (KEY_HP_TEXT == null) return;
        String id = stand.getPersistentDataContainer().get(KEY_HP_TEXT, PersistentDataType.STRING);
        if (id == null) return;
        try {
            Entity e = Bukkit.getEntity(java.util.UUID.fromString(id));
            if (e instanceof TextDisplay td && !td.isDead()) {
                Location l = stand.getLocation().clone().add(0, turretTextHeight(stand), 0);
                td.teleport(l);
            } else {
                ensureTurretHpDisplay(stand); // zginął - odtwórz
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Strzał wieżyczki w stronę celu: BEZ widocznej strzały - żadna strzała nie
     * rodzi się, nie leci ani nie może utknąć w wieżyczce. Efekt wystrzału to
     * biały dym z lufy w kierunku spojrzenia (kłąb + smuga) i natychmiastowe
     * trafienie: 3.0 obrażeń (bez odrzutu moba) i aggro (cel + znacznik).
     */
    private void spawnTurretShot(ArmorStand stand, LivingEntity target, TurretSpin ts) {
        try {
            // punkt startu dymu: lufa wysunięta w przód (poza bloki wieżyczki);
            // jeśli nadal wypada w bloku, wysuwamy go wzdłuż lufy, aż trafi w powietrze
            Location from = turretBarrelLocation(stand, ts, TURRET_ARROW_FORWARD);
            double aRad = Math.toRadians(ts.angle);
            Vector fwd = new Vector(Math.sin(aRad), 0, Math.cos(aRad));
            for (int i = 0; i < 8 && !from.getBlock().isPassable(); i++) {
                from.add(fwd);
            }
            Location to = target.getLocation().clone().add(0, target.getHeight() * 0.5, 0);
            Vector dir = to.toVector().subtract(from.toVector());
            double dist = dir.length();
            if (dist < 0.01) return;
            Vector dirN = dir.normalize();
            // biały dym przy wystrzale (zmniejszony o 3/4): mały kłąb przy lufie + krótka smuga
            stand.getWorld().spawnParticle(Particle.WHITE_SMOKE, from, 3, 0.08, 0.08, 0.08, 0.015);
            for (int i = 1; i <= 2; i++) {
                stand.getWorld().spawnParticle(Particle.WHITE_SMOKE,
                        from.clone().add(dirN.clone().multiply(i * 0.35)),
                        2, 0.06, 0.06, 0.06, 0.01);
            }
            // wiązka od lufy do celu w kolorze wieżyczki (jak laser różdżki magika):
            // lód - jasnoniebieska, trutka - zielona, ogień - czerwona, elektryk - żółta,
            // a podstawowa (bez efektu) - jasnoszara
            Color beamColor = null;
            if (turretFreeze(stand)) beamColor = Color.fromRGB(125, 190, 255);
            else if (turretPoison(stand)) beamColor = Color.fromRGB(60, 190, 60);
            else if (turretFire(stand)) beamColor = Color.fromRGB(255, 50, 50);
            else if (turretChain(stand)) beamColor = Color.fromRGB(255, 230, 60);
            if (beamColor == null) beamColor = Color.fromRGB(200, 200, 200); // jasnoszary
            drawParticleBeam(stand.getWorld(), from, to, Particle.DUST,
                    new Particle.DustOptions(beamColor, 0.8f));
            // natychmiastowe trafienie (bez fizycznej strzały): obrażenia, odrzut, aggro
            if (target instanceof Monster m) {
                if (m.getScoreboardTags().contains(TAG_ALLIED)) return;
                m.damage(turretDamage(stand), stand); // bez odrzutu - sami moby decydują, czy uciekać
                if (turretFreeze(stand)) {
                    // wieżyczka lodu: spowolnienie + szron na ciele moba
                    m.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                            FREEZE_DURATION_TICKS, FREEZE_AMPLIFIER, true, true));
                    m.setFreezeTicks(FREEZE_DURATION_TICKS);
                    m.getWorld().spawnParticle(Particle.SNOWFLAKE, to, 10, 0.3, 0.3, 0.3, 0.01);
                }
                if (turretPoison(stand)) {
                    // wieżyczka trutki: 1 HP/s przez 8 s (DoT) + zielony pył przy trafieniu
                    applyPoison(m, stand);
                    m.getWorld().spawnParticle(Particle.DUST, to, 10, 0.3, 0.3, 0.3, 0,
                            new Particle.DustOptions(Color.fromRGB(60, 190, 60), 0.9f));
                }
                if (turretFire(stand)) {
                    // wieżyczka ognia: podpala moba na 8 s (jak zaklęty ogień) + płomień przy trafieniu
                    m.setFireTicks(FIRE_DURATION_TICKS);
                    m.getWorld().spawnParticle(Particle.FLAME, to, 10, 0.3, 0.3, 0.3, 0.015);
                }
                if (turretChain(stand)) {
                    // wieżyczka elektryka: łańcuch na kolejne moby (65% i 35% obrażeń)
                    chainLightning(stand, m, turretDamage(stand));
                }
                m.setTarget(stand);
                markTurretAggro(m, stand);
                m.getWorld().spawnParticle(Particle.CRIT, to, 8, 0.25, 0.25, 0.25, 0.05);
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Przygotowanie lufy wieżyczki: zapamiętuje pozycję/obrót lufy z części ARROW
     * (do punktu wystrzału/dymu) i USUWA płaską część ARROW na stałe - wieżyczka
     * nie ma i nie pokazuje ŻADNEJ strzały.
     */
    private void initTurretVisuals(ArmorStand stand, TurretSpin ts) {
        if (stand == null || stand.isDead() || ts == null) return;
        List<DisplayPart> list = partsOf(stand);
        for (int i = 0; i < list.size(); i++) {
            DisplayPart p = list.get(i);
            if (!p.turret || p.material() != Material.ARROW) continue;
            ts.barrel[0] = p.tx;
            ts.barrel[1] = p.ty;
            ts.barrel[2] = p.tz;
            ts.barrelRotY = p.rotY;
            ts.barrelRotZ = p.rotZ;
            detachAndRemove(stand, p.display); // płaska część znika NA STAŁE
            list.remove(i);
            break;
        }
    }

    /**
     * Pozycja lufy w świecie: środek stojaka + obrót o kąt wieżyczki + wysokość
     * pasażera (~2.0 nad stopami) + przesunięcie lufy z części ARROW.
     */
    /** Pozycja lufy w świecie (z wysunięciem w przód wzdłuż kierunku namierzania,
     *  żeby punkt strzału/dymu nie wypadał wewnątrz bloków wieżyczki). */
    private Location turretBarrelLocation(ArmorStand stand, TurretSpin ts, double forward) {
        double a = Math.toRadians(ts.angle);
        double cos = Math.cos(a), sin = Math.sin(a);
        // lufa skierowana w przód (kierunek namierzania): przesunięcie + wysunięcie
        double wx = ts.barrel[0] * cos + ts.barrel[2] * sin + forward * sin;
        double wz = -ts.barrel[0] * sin + ts.barrel[2] * cos + forward * cos;
        return stand.getLocation().clone().add(wx, 2.0 + ts.barrel[1] - TURRET_BARREL_DROP, wz);
    }

    /** Pionowy środek konstrukcji wieżyczki (offset od stojaka): części wiszą od
     *  ~2.0 + min_ty do ~2.0 + max_ty nad stopami (pasażer siedzi ~2.0 nad stopami),
     *  więc środek = 2.0 + (min_ty + max_ty) / 2. Fallback 0.5, gdy brak części. */
    private double turretCenterOffset(ArmorStand stand) {
        List<DisplayPart> list = partsOf(stand);
        if (list.isEmpty()) return 0.5;
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (DisplayPart p : list) {
            if (p.ty < min) min = p.ty;
            if (p.ty > max) max = p.ty;
        }
        return 2.0 + (min + max) / 2.0;
    }

    private List<DisplayPart> partsOf(ArmorStand stand) {
        return parts.getOrDefault(stand.getUniqueId(), Collections.emptyList());
    }

    // ===================== interaktywna edycja =====================

    /**
     * Zmienia strojoną część (STICK). backward=true (SHIFT) cofa wybór:
     * głowa -> ciało -> prawa ręka -> lewa ręka -> prawa noga -> lewa noga ->
     * yaw -> pitch -> wysokość -> doczepione części, a ze shiftem w drugą stronę (wstecz).
     */
    public void cyclePart(ArmorStand stand, Player player, boolean backward) {
        EditState s = state(stand);
        if (s == null) return;
        List<DisplayPart> list = partsOf(stand);
        if (s.partIndex >= 0) {
            if (backward) {
                s.partIndex--;
                if (s.partIndex < 0) {
                    s.partIndex = -1;
                    s.part = Part.HEAD; // z pierwszej części wracamy do głowy
                }
            } else if (s.partIndex + 1 < list.size()) {
                s.partIndex++;
            } else {
                s.partIndex = -1;
                s.part = Part.HEAD; // z ostatniej części wracamy do głowy
            }
        } else if (backward) {
            if (s.part.ordinal() > 0) {
                s.part = Part.values()[s.part.ordinal() - 1];
            } else if (list.isEmpty()) {
                s.part = Part.HEIGHT; // wstecz z głowy = koniec cyklu (WYSOKOŚĆ)
            } else {
                s.part = Part.HEAD;
                s.partIndex = list.size() - 1; // ostatnia doczepiona część
            }
        } else if (s.part.ordinal() + 1 < Part.values().length) {
            s.part = Part.values()[s.part.ordinal() + 1];
        } else {
            // po HEIGHT wchodzimy w doczepione części (jeśli są)
            s.part = Part.HEAD;
            s.partIndex = list.isEmpty() ? -1 : 0;
        }
        applyStickyAxis(player, s);
        info(stand, player);
    }

    /**
     * Przywraca zapamiętany tryb (oś) cukru dla gracza - „stały parametr": po wybraniu
     * parametru SUGARem (np. WYSOKOŚĆ) zostaje on przy zmianie części/stojaka, dopóki
     * gracz go nie zmieni. Ograniczany do zakresu celu: doczepiona część 0..13,
     * reszta (głowa/ciało/...) 0..2.
     */
    private void applyStickyAxis(Player player, EditState s) {
        s.axis = stickyAxis.getOrDefault(player.getUniqueId(), 0);
        int max = s.partIndex >= 0 ? 13 : 2;
        if (s.axis > max) s.axis = max;
        if (s.axis < 0) s.axis = 0;
    }

    /** Zmienia oś/tryb (SUGAR); backward=true (SHIFT) cofa do poprzedniej opcji. */
    public void cycleAxis(ArmorStand stand, Player player, boolean backward) {
        EditState s = state(stand);
        if (s == null) return;
        int max = s.partIndex >= 0 ? 14 : 3; // części: 0..13 (13 = ŚWIATŁO)
        s.axis = backward ? (s.axis - 1 + max) % max : (s.axis + 1) % max;
        stickyAxis.put(player.getUniqueId(), s.axis); // stały parametr cukru
        info(stand, player);
    }

    /** Obraca/skaluje aktualnie strojoną część. deltaDeg niesie znak, fine = tryb drobny (shift). */
    public void rotate(ArmorStand stand, Player player, double deltaDeg, boolean fine) {
        EditState s = state(stand);
        if (s == null) return;
        if (s.partIndex >= 0) {
            List<DisplayPart> list = partsOf(stand);
            if (s.partIndex >= list.size()) {
                s.partIndex = -1;
                s.part = Part.HEAD;
                return;
            }
            if (s.axis == 10) { // GRUPA: BLAZE_ROD/BONE = następna/poprzednia część z tego samego materiału
                cyclePartInGroup(s, list, deltaDeg >= 0);
                info(stand, player);
                return;
            }
            if (s.axis == 7) { // LUSTRO: lustrzana KOPIA części po drugiej stronie (bez SHIFT)
                // BLAZE_ROD = kopia w X, BONE = kopia w Z. ŹRÓDŁO = część, na którą patrzysz
                // (nie ukryta "wybrana" z listy - żeby nie kopiować patyka, którego nie widzisz)
                DisplayPart src = partUnderCrosshair(stand, player, list);
                if (src == null) {
                    AntiSpam.sendActionBar(player, "§cNie patrzysz na żadną część tego stojaka - popatrz na część, którą chcesz odbić.");
                    return;
                }
                pushUndoSnapshot(stand); // undo = cofnij kopię
                DisplayPart copy = duplicateMirrored(stand, src, deltaDeg >= 0 ? "x" : "z");
                if (copy != null) {
                    s.partIndex = partsOf(stand).size() - 1; // wybierz kopię od razu
                }
                info(stand, player);
                AntiSpam.sendActionBar(player, "§aOdbicie lustrzane: nowa część po drugiej stronie "
                        + (deltaDeg >= 0 ? "X" : "Z") + " (wybrana, /stojak undo cofa).");
                return;
            }
            if (s.axis == 9) { // ŚWIATŁO: BLAZE_ROD = część JASNA (pełna jasność), BONE = normalna
                pushUndoSnapshot(stand); // undo = cofnij zmianę jasności
                DisplayPart p = list.get(s.partIndex);
                p.bright = deltaDeg >= 0;
                if (p.display != null) {
                    if (p.bright) {
                        p.display.getPersistentDataContainer().set(KEY_BRIGHT, PersistentDataType.BYTE, (byte) 1);
                    } else {
                        p.display.getPersistentDataContainer().remove(KEY_BRIGHT);
                    }
                }
                p.apply();
                info(stand, player);
                AntiSpam.sendActionBar(player, "§aŚWIATŁO: §e" + (p.bright ? "JASNE (pełna jasność)" : "normalne")
                        + "§7 - część " + (s.partIndex + 1) + ". §7(/stojak undo cofa)");
                return;
            }
            pushUndoSnapshot(stand); // undo = cofnij kliknięcie BLAZE_ROD/BONE na części
            modifyPart(list.get(s.partIndex), s.axis, deltaDeg, fine);
            info(stand, player);
            return;
        }
        switch (s.part) {
            case HEAD -> stand.setHeadPose(rotated(stand.getHeadPose(), s.axis, deltaDeg));
            case BODY -> stand.setBodyPose(rotated(stand.getBodyPose(), s.axis, deltaDeg));
            case RIGHT_ARM -> stand.setRightArmPose(rotated(stand.getRightArmPose(), s.axis, deltaDeg));
            case LEFT_ARM -> stand.setLeftArmPose(rotated(stand.getLeftArmPose(), s.axis, deltaDeg));
            case RIGHT_LEG -> stand.setRightLegPose(rotated(stand.getRightLegPose(), s.axis, deltaDeg));
            case LEFT_LEG -> stand.setLeftLegPose(rotated(stand.getLeftLegPose(), s.axis, deltaDeg));
            case STAND_YAW -> {
                Location l = stand.getLocation();
                l.setYaw(l.getYaw() + (float) deltaDeg);
                stand.teleport(l);
            }
            case STAND_PITCH -> {
                Location l = stand.getLocation();
                l.setPitch(l.getPitch() + (float) deltaDeg);
                stand.teleport(l);
            }
            case HEIGHT -> stand.teleport(stand.getLocation().add(0, deltaDeg * 0.02, 0));
        }
        info(stand, player);
    }

    /** GRUPA: przełącza na następną (forward=true) albo poprzednią część z tego samego materiału. */
    private void cyclePartInGroup(EditState s, List<DisplayPart> list, boolean forward) {
        if (s.partIndex < 0 || s.partIndex >= list.size()) return;
        Material m = list.get(s.partIndex).material();
        int n = list.size();
        for (int step = 1; step <= n; step++) {
            int idx = ((s.partIndex + (forward ? step : -step)) % n + n) % n;
            if (list.get(idx).material() == m) {
                s.partIndex = idx;
                return;
            }
        }
    }

    private void modifyPart(DisplayPart p, int mode, double deltaDeg, boolean fine) {
        double sign = deltaDeg >= 0 ? 1 : -1;
        switch (mode) {
            case 0 -> p.ty = clamp(p.ty + sign * (fine ? 0.01 : 0.10), -20, 20); // wysokość (Y), bez shiftu 10%, shift = 1%
            case 1 -> p.tx = clamp(p.tx + sign * (fine ? 0.01 : 0.10), -20, 20); // przesunięcie X, bez shiftu 10%, shift = 1%
            case 2 -> p.tz = clamp(p.tz + sign * (fine ? 0.01 : 0.10), -20, 20); // przesunięcie Z, bez shiftu 10%, shift = 1%
            case 3 -> { // skala (wszystkie osie razem)
                double step = fine ? 0.01 : 0.10; // bez shiftu 10%, shift = 1%
                p.scaleX = clamp(p.scaleX + sign * step, 0.01, 20);
                p.scaleY = clamp(p.scaleY + sign * step, 0.01, 20);
                p.scaleZ = clamp(p.scaleZ + sign * step, 0.01, 20);
            }
            case 4 -> p.rotZ += sign * (fine ? 1 : 10);   // skręt (roll) wokół własnej osi, bez shiftu 10°, shift = 1°
            case 5 -> p.rotY += sign * (fine ? 1 : 10);   // obrót (yaw), bez shiftu 10°, shift = 1°
            case 6 -> p.rotX += sign * (fine ? 1 : 10);   // pochylenie (pitch), bez shiftu 10°, shift = 1°
            case 8 -> { // ODBICIE (flip): BLAZE_ROD = odbij w X, BONE = odbij w Z -
                // szybsze niż obrót o 180° (1 klik zamiast 18); ujemna skala lustruje
                if (sign > 0) p.scaleX = -p.scaleX;
                else p.scaleZ = -p.scaleZ;
            }
            case 11 -> { // ROZCIĄGNIJ-Y: rozciągaj TYLKO w górę (wysoki blok/kolumna)
                double step = fine ? 0.01 : 0.10; // bez shiftu 10%, shift = 1%
                p.scaleY = clamp(p.scaleY + sign * step, 0.01, 20);
            }
            case 12 -> { // ROZCIĄGNIJ-X: rozciągaj TYLKO w osi X (podłużny blok w bok)
                double step = fine ? 0.01 : 0.10; // bez shiftu 10%, shift = 1%
                p.scaleX = clamp(p.scaleX + sign * step, 0.01, 20);
            }
            case 13 -> { // ROZCIĄGNIJ-Z: rozciągaj TYLKO w osi Z (podłużny blok w przód/tył)
                double step = fine ? 0.01 : 0.10; // bez shiftu 10%, shift = 1%
                p.scaleZ = clamp(p.scaleZ + sign * step, 0.01, 20);
            }
        }
        p.apply();
    }

    public void toggleVisible(ArmorStand stand, Player player) {
        stand.setVisible(!stand.isVisible());
        info(stand, player);
    }

    public void toggleArms(ArmorStand stand, Player player) {
        stand.setArms(!stand.hasArms());
        info(stand, player);
    }

    /** Wypisuje na czat aktualne wartości stojaka (do dalszego strojenia w kodzie). */
    public void printValues(ArmorStand stand, Player player) {
        player.sendMessage(Component.text("§6=== Stojak - aktualne wartości ==="));
        player.sendMessage(Component.text("§fGłowa:      " + poseText(stand.getHeadPose())));
        player.sendMessage(Component.text("§fCiało:      " + poseText(stand.getBodyPose())));
        player.sendMessage(Component.text("§fPrawa ręka: " + poseText(stand.getRightArmPose())));
        player.sendMessage(Component.text("§fLewa ręka:  " + poseText(stand.getLeftArmPose())));
        player.sendMessage(Component.text("§fPrawa noga: " + poseText(stand.getRightLegPose())));
        player.sendMessage(Component.text("§fLewa noga:  " + poseText(stand.getLeftLegPose())));
        Location l = stand.getLocation();
        player.sendMessage(Component.text("§fStojak: yaw=" + deg(l.getYaw()) + "° pitch=" + deg(l.getPitch()) + "°"
                + " | small=" + stand.isSmall() + " arms=" + stand.hasArms()
                + " visible=" + stand.isVisible() + " base=" + stand.hasBasePlate()));
        player.sendMessage(Component.text("§fPozycja: x=" + fmt(l.getX()) + " y=" + fmt(l.getY()) + " z=" + fmt(l.getZ())));
        String head = stand.getEquipment().getHelmet() == null ? "brak" : stand.getEquipment().getHelmet().getType().name();
        String hand = stand.getEquipment().getItemInMainHand() == null ? "brak" : stand.getEquipment().getItemInMainHand().getType().name();
        player.sendMessage(Component.text("§fWyposażenie: głowa=" + head + " ręka=" + hand));
        List<DisplayPart> list = partsOf(stand);
        if (!list.isEmpty()) {
            player.sendMessage(Component.text("§fCzęści: " + list.size()
                    + " (skala/obrót/pochylenie/wysokość przez STICK + SUGAR + BLAZE_ROD/BONE)"));
        }
        AntiSpam.send(player, "§aWartości wypisane na czacie.");
    }

    // ===================== pomoc =====================

    /** Liczba linii pomocy na jedną stronę (paginacja /stojak help). */
    private static final int HELP_PER_PAGE = 5;

    /** Liczba stron /stojak help - do tab-complete /stojak help <nr>. */
    public int helpPages() {
        return (helpLines().size() + HELP_PER_PAGE - 1) / HELP_PER_PAGE;
    }

    /**
     * Wszystkie linie pomocy /stojak help (bez nagłówka) - do paginacji
     * (strony przełączane klikalnym tekstem na czacie: /stojak help <nr>).
     */
    private static List<String> helpLines() {
        return List.of(
                "§f/stojak §7- pokazuje pomoc; §f/stojak stworz [x y z] §7- postaw stojak na bloku, na który patrzysz, albo na współrzędnych bloku",
                "§f/stojak stworz head:stone hand:bow sink:-1.0 rpitch:-60 §7- postaw z parametrami (sink:-1.0 = na wierzchu bloku)",
                "§f/stojak part <blok|przedmiot> [x y z] §7- doczep część",
                "§f/stojak head|hand|offhand <materiał|brak> §7- ustaw wyposażenie",
                "§f/stojak move <x> <y> <z> §7- przesuń wybraną część o zadaną deltę",
                "§f/stojak <materiał> move <x> <y> <z> §7- wybierz część z materiału i przesuń",
                "§f/stojak move czesc <liczba> <x> <y> <z> §7- przesuń KONKRETNĄ część (numer z action bara)",
                "§f/stojak move <materiał>_<liczba> <x> <y> <z> §7- przesuń N-tą część z materiału (np. stone_2)",
                "§f/stojak scale <wartość|x y z|reset> §7- skala wybranej części; §fscale all <współczynnik>§7 - skala wszystkich części naraz",
                "§f/stojak rotate <x> <y> <z> §7- obrót CAŁEGO stojaka o stopnie (yaw/pitch + wszystkie części; pojedyncza liczba = wokół Y); §f/stojak <materiał> rotate <x> <y> <z>§7 - obrót tylko części",
                "§f/stojak zamien <nowy> | czesc <stary> <nowy> §7- zmień materiał części (czesc = KONKRETNA część, ta, na którą patrzysz), zachowując skalę/obrót/pozycję",
                "§f/stojak zamien czesc <liczba> <nowy> §7- zmień KONKRETNĄ część po numerze z action bara; §f/stojak zamien <materiał>_<liczba> <nowy>§7 - N-ta część z materiału (np. stone_2)",
                "§f/stojak lustro [x|z] §7- lustrzane odbicie CAŁEGO stojaka (wszystkie części naraz) - do symetrycznych budowli; §7/stojak undo cofa",
                "§f/stojak lustro czesc [x|z] §7- lustrzana KOPIA wybranej części po drugiej stronie stojaka (offset/skala/obroty odbite) - w SUGAR też: tryb LUSTRO + BLAZE_ROD/BONE",
                "§f/stojak funkcja siedzenie [x y z] §7- postaw gotowe krzesło (mini stojak z siodłem) - klik = siadanie, Shift = wstań; §f/stojak krzeslo [<nazwa>] [on|off] §7- oznacz stojak jako siedzisko (z nazwą = siadać można tylko na tym stojaku)",
                "§f/stojak funkcja tv [szer] [wys] §7- postaw czarny ekran TV z czarnych banerów ustawionych obok siebie (domyślnie 3x2); [x y z] = dokładne miejsce - edytujesz i zapisujesz jak normalny stojak",
                "§f/stojak funkcja lampa [wysokość] §7- postaw wizualną lampę: 1 = sam świecący blok, 2 = ciemny słup + głowa; działa do 4 - edytowalna i zapisywalna jak normalny stojak",
                "§f/stojak zegar test §7- podgląd zegara ze stojaka (nazwa: zegar_wskazowki): przez 1 minutę wskazówki kręcą się do przodu 2.5× (24 minuty = godzina), żeby sprawdzić kierunek i pozycję bez areny; §f/stojak zegar anuluj §7- anuluj test; §f/stojak zegar tryb <normalny|zamiatajacy> §7- styl obrotu wskazówek (pełne koło albo pełne koło z odświeżeniem na każdej ćwiartce 90°)",
                "§f/stojak <materiał> scale/rotate ... §7- to samo, ale po materiale",
                "§f/stojak usun czesc <liczba> [materiał] §7- usuń część o numerze z action bara; §f/stojak usun all czesc <materiał> §7- usuń wszystkie części z tego materiału; §f/stojak usun [materiał] §7- usuń część, na którą patrzysz",
                "§f/stojak duplikuj [materiał] [dx dy dz] §7- skopiuj część z identycznymi statystykami - kopia staje w miejscu oryginału, dx dy dz przesuwa ją dalej",
                "§f/stojak duplikuj czesc <liczba> [dx dy dz] §7- to samo, ale po numerze części z action bara",
                "§f/stojak <nazwa> duplikuj czesc <liczba> §7- to samo bez patrzenia, na stojaku o nazwie",
                "§f/stojak stats [materiał] §7- pokaż statystyki części: offset, skala, obrót - na patrzonej albo wybranej",
                "§f/stojak informacja [materiał] §7- informacje o części w liczbach: skala, kordynaty, obrót, skręt, pochylenie (jak w edycji STICKiem)",
                "§f/stojak partclear §7- usuń wszystkie doczepione części",
                "§f/stojak undo §7- cofnij ostatnią zmianę części; §f/stojak ponow §7- przywróć stan sprzed cofnięcia (ratuje po przypadkowym undo)",
                "§f/stojak autosave §7- lista stojaków z kopii zapasowej; §fautosave place <nazwa> §7- przywróć w oryginalne miejsce (kopia co minutę, jeden plik)",
                "§f/stojak bind | odzyskaj §7- dołącz stojak do strojenia",
                "§f/stojak dajitemy §7- daj itemy do edycji stojaka: STICK, BONE, BLAZE_ROD, SUGAR, REDSTONE, GUNPOWDER, BARRIER (po kolei do ekwipunku)",
                "§f/stojak znajdz §7- lista stojaków pluginu w świecie - po restarcie, gdy nie wiesz, gdzie stoi Twój stojak",
                "§f/stojak liczba [promień] §7- ile stojaków i części pluginu stoi w promieniu bloków (domyślnie 100) + wszystkie encje - do oceny lagów",
                "§f/stojak remove §7- usuń stojak, na który patrzysz",
                "§f/stojak przywroc §7- przywróć ostatnio usunięty stojak tam, gdzie stał",
                "§f/stojak save <nazwa> [sink:-1.0] §7- zapisz strojony stojak do pliku (sink:-1.0 = stoi na wierzchu bloku)",
                "§f/stojak saveitem [sink:-1.0] §7- zapisz stojak na przedmiot w ręce",
                "§f/stojak place <nazwa> [x y z] §7- postaw zapisany stojak; koordynaty = dokładne miejsce",
                "§f/stojak offset <x> <y> <z> §7- przesunięcie stawiania",
                "§f/stojak item <nazwa> §7- daj przedmiot postawiający zapisany stojak",
                "§f/stojak copy §7- skopiuj stojak, na który patrzysz, do schowka",
                "§f/stojak paste [x y z] §7- postaw kopię ze schowka",
                "§f/stojak aktywny §7- pokaż pozycję aktywnego stojaka",
                "§f/stojak nazwij <nazwa> §7- nadaj nazwę stojakowi, na który patrzysz (od razu widoczna)",
                "§f/stojak nazwa on|off §7- pokaż / ukryj nazwę stojaka (sama nazwa zostaje); usun = usuń",
                "§f/stojak glow [sekundy] §7- podświetl stojak na czas; glow off = wyłącz",
                "§f/stojak visible [on|off] §7- pokaż / ukryj korpus stojaka",
                "§f/stojak snap [on|off] §7- przyciąganie: move zaokrągla do 0.25 bloka, rotate do 5°",
                "§f/stojak sprzataj [promień] §7- usuń osierocone części w promieniu, domyślnie 10",
                "§f/stojak list §7- lista zapisanych stojaków",
                "§f/stojak delete <nazwa> §7- usuń zapis ORAZ postawione stojaki o tej nazwie",

                "§7Kliknij stojak trzymając:",
                "§fSTICK §7- zmiana części; §7SHIFT = cofnij",
                "§fSUGAR §7- oś X/Y/Z; dla części: WYSOKOŚĆ/PRZESUNIĘCIE-X/Z/SKALA/SKRĘT/OBRÓT/POCHYLENIE/LUSTRO/ODBICIE/ŚWIATŁO/GRUPA/ROZCIĄGNIJ-Y/X/Z (BLAZE_ROD = jasne, BONE = normalne); §7wybrany parametr zostaje (stały, do zmiany SUGARem); SHIFT = cofnij",
                "§fBLAZE_ROD / BONE §7- + / -",
                "§fFEATHER §7- prawy: zmniejsz, lewy: powiększ wszystkie części (SHIFT = drobniej); §fGUNPOWDER §7- widoczność; §fBLAZE_POWDER §7- ręce; §fREDSTONE §7- podświetlanie",
                "§fPAPER §7- wypisz wartości na czat",
                "§fBARRIER §7- usuń wybraną część albo cały stojak"
        );
    }

    public void sendHelp(Player player) {
        sendHelp(player, 1);
    }

    /**
     * Pomoc /stojak w stronach (5 linii na stronę): nagłówek ze stroną, linie
     * strony i klikalna nawigacja na dole - « poprzednia [1] [2] [3] następna ».
     * Kliknięcie numeru (albo poprzednia/następna) uruchamia /stojak help <nr>
     * bez pisania - strony przełączają się wprost na czacie.
     */
    public void sendHelp(Player player, int page) {
        List<String> lines = helpLines();
        int total = Math.max(1, (lines.size() + HELP_PER_PAGE - 1) / HELP_PER_PAGE);
        int p = Math.max(1, Math.min(page, total));
        player.sendMessage(txt("§6=== /stojak - pomoc §7(strona §e" + p + "§7/" + total + ") §6==="));
        int from = (p - 1) * HELP_PER_PAGE;
        for (int i = from; i < lines.size() && i < from + HELP_PER_PAGE; i++) {
            player.sendMessage(txt(lines.get(i)));
        }
        player.sendMessage(helpNav(p, total));
    }

    /** Klikalna nawigacja stron pomocy: « poprzednia [1] [2] [3] następna ». */
    private static Component helpNav(int page, int total) {
        Component prev = page > 1
                ? txt("§a« poprzednia").clickEvent(ClickEvent.runCommand("/stojak help " + (page - 1)))
                        .hoverEvent(HoverEvent.showText(txt("§7Kliknij: strona " + (page - 1))))
                : txt("§8« poprzednia");
        Component next = page < total
                ? txt("§anastępna »").clickEvent(ClickEvent.runCommand("/stojak help " + (page + 1)))
                        .hoverEvent(HoverEvent.showText(txt("§7Kliknij: strona " + (page + 1))))
                : txt("§8następna »");
        net.kyori.adventure.text.TextComponent.Builder b = Component.text();
        b.append(txt("§7[")).append(prev).append(txt("§7]  "));
        for (int i = 1; i <= total; i++) {
            if (i == page) {
                b.append(txt("§e[" + i + "]")); // aktualna strona - podświetlona, nieklikalna
            } else {
                b.append(txt("§7[" + i + "]")
                        .clickEvent(ClickEvent.runCommand("/stojak help " + i))
                        .hoverEvent(HoverEvent.showText(txt("§7Kliknij: strona " + i))));
            }
            b.append(txt(" "));
        }
        b.append(txt("§7[")).append(next).append(txt("§7]"));
        return b.build();
    }

    /** Parsuje legacy kolory § na komponent (klikalne elementy też mają kolory). */
    private static Component txt(String legacy) {
        return LegacyComponentSerializer.legacySection().deserialize(legacy);
    }


    // ===================== szczegóły =====================

    private EditState state(ArmorStand stand) {
        EditState s = states.get(stand.getUniqueId());
        return s != null ? s : null;
    }

    private void info(ArmorStand stand, Player player) {
        EditState s = state(stand);
        if (s == null) return;
        List<DisplayPart> list = partsOf(stand);
        if (s.partIndex >= 0 && s.partIndex < list.size()) {
            infoPart(player, s, list);
            return;
        }
        String axisText = switch (s.part) {
            case STAND_YAW -> "yaw";
            case STAND_PITCH -> "pitch";
            case HEIGHT -> "wysokość";
            default -> "oś " + "XYZ".charAt(s.axis);
        };
        AntiSpam.sendActionBar(player, "§a" + s.part.label() + " §7(" + axisText + ")");
    }

    /**
     * Action bar z informacją o wybranej części (tryb + wartość). Komunikat jest
     * SKRACANY, gdy nie mieści się w action barze: najpierw znika nazwa materiału
     * (długie nazwy bloków, np. polished_blackstone_bricks, przekraczałyby limit
     * i AntiSpam gubiłby komunikat - przez co przewijanie SUGARem/BLAZE_ROD
     * wyglądało na nie działające).
     */
    private void infoPart(Player player, EditState s, List<DisplayPart> list) {
        DisplayPart cur = list.get(s.partIndex);
        // nazwa części: własna (np. z nazwanego przedmiotu) albo materiał (np. stone)
        String mat = cur.name();
        // pozycja w grupie tego samego materiału (np. stone 2/4 = 2. z 4 kamieni)
        int groupIdx = 0, groupCount = 0;
        for (DisplayPart p : list) {
            if (p.material() == cur.material()) {
                groupCount++;
                if (p == cur) groupIdx = groupCount;
            }
        }
        String mode = switch (s.axis) {
            case 0 -> "WYSOKOŚĆ";
            case 1 -> "PRZESUNIĘCIE-X";
            case 2 -> "PRZESUNIĘCIE-Z";
            case 3 -> "SKALA";
            case 4 -> "SKRĘT";
            case 5 -> "OBRÓT";
            case 6 -> "POCHYLENIE";
            case 7 -> "LUSTRO";
            case 8 -> "ODBICIE";
            case 9 -> "ŚWIATŁO";
            case 10 -> "GRUPA";
            case 11 -> "ROZCIĄGNIJ-Y";
            case 12 -> "ROZCIĄGNIJ-X";
            case 13 -> "ROZCIĄGNIJ-Z";
            default -> "SKALA";
        };
        // wartość bieżąca (żółta): SKALA = skale X/Y/Z, ROZCIĄGNIJ = skala osi,
        // OBRÓT/POCHYLENIE/SKRĘT = kąt w stopniach, WYSOKOŚĆ/PRZESUNIĘCIE = bloki
        String val = switch (s.axis) {
            case 0 -> fmt(cur.ty);
            case 1 -> fmt(cur.tx);
            case 2 -> fmt(cur.tz);
            case 3 -> "X:" + fmt(cur.scaleX) + " Y:" + fmt(cur.scaleY) + " Z:" + fmt(cur.scaleZ);
            case 4 -> fmt(cur.rotZ) + "°";
            case 5 -> fmt(cur.rotY) + "°";
            case 6 -> fmt(cur.rotX) + "°";
            case 9 -> cur.bright ? "ON" : "OFF";
            case 11 -> fmt(cur.scaleY);
            case 12 -> fmt(cur.scaleX);
            case 13 -> fmt(cur.scaleZ);
            default -> "";
        };        // Licznik grupy (np. stick 69/69) pokazujemy TYLKO w trybie GRUPA - w pozostałych
        // trybach X/Y wyglądał jak limit części, a nim nie jest (plugin nie ma limitu części).
        String flipX = cur.scaleX < 0 ? "§c-§7" : "§a+§7";
        String flipZ = cur.scaleZ < 0 ? "§c-§7" : "§a+§7";
        String body = switch (s.axis) {
            case 10 -> "(" + mat + " " + groupIdx + "/" + groupCount + ")";
            case 7 -> "(" + mat + ", LUSTRO)";
            case 8 -> "(" + mat + ", ODBICIE X:" + flipX + " Z:" + flipZ + ")";
            default -> "(" + mat + ", " + mode + " §e" + val + "§7)";
        };

        String msg = "§aCZĘŚĆ " + (s.partIndex + 1) + " §7" + body;
        if (AntiSpam.visibleLength(msg) > AntiSpam.ACTION_BAR_MAX_LENGTH) {
            // długi materiał - wersja bez nazwy materiału (zawsze się mieści)
            String shortBody = switch (s.axis) {
                case 10 -> "(" + groupIdx + "/" + groupCount + ")";
                case 7 -> "(LUSTRO)";
                case 8 -> "(ODBICIE X:" + flipX + " Z:" + flipZ + ")";
                default -> "(" + mode + " §e" + val + "§7)";
            };
            msg = "§aCZĘŚĆ " + (s.partIndex + 1) + " §7" + shortBody;
        }
        AntiSpam.sendActionBar(player, msg);
    }

    private EulerAngle rotated(EulerAngle pose, int axis, double deltaDeg) {
        double rad = Math.toRadians(deltaDeg);
        double x = pose.getX();
        double y = pose.getY();
        double z = pose.getZ();
        return switch (axis) {
            case 1 -> new EulerAngle(x, y + rad, z);
            case 2 -> new EulerAngle(x, y, z + rad);
            default -> new EulerAngle(x + rad, y, z);
        };
    }

    private EulerAngle pose(Map<String, String> p, String prefix, double defPitchDeg, double defYawDeg, double defRollDeg) {
        double x = paramDouble(p, prefix + "pitch", defPitchDeg);
        double y = paramDouble(p, prefix + "yaw", defYawDeg);
        double z = paramDouble(p, prefix + "roll", defRollDeg);
        return new EulerAngle(Math.toRadians(x), Math.toRadians(y), Math.toRadians(z));
    }

    private String poseText(EulerAngle pose) {
        return "x=" + deg(pose.getX()) + "° y=" + deg(pose.getY()) + "° z=" + deg(pose.getZ()) + "°";
    }

    private void applySafety(ArmorStand stand) {
        stand.setGravity(false);
        stand.setInvulnerable(true);
        stand.setCollidable(false);
        stand.setSilent(true);
        stand.setCanPickupItems(false);
        stand.setPersistent(true);
        stand.setRemoveWhenFarAway(false);
        // trwały znacznik stojaka pluginu (przetrwa restart serwera)
        stand.addScoreboardTag(TAG_STAND);
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static boolean paramBool(Map<String, String> p, String key, boolean def) {
        if (!p.containsKey(key)) return def;
        return Boolean.parseBoolean(p.get(key));
    }

    private static double paramDouble(Map<String, String> p, String key, double def) {
        if (!p.containsKey(key)) return def;
        try {
            return Double.parseDouble(p.get(key).replace(',', '.')); // akceptuj 0,5 zamiast 0.5
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static Material paramMaterial(Map<String, String> p, String key, Material def) {
        if (!p.containsKey(key)) return def;
        Material m = Material.matchMaterial(p.get(key));
        return m != null ? m : def;
    }

    private static String deg(double rad) {
        return String.format(Locale.ROOT, "%.1f", Math.toDegrees(rad));
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }
}
