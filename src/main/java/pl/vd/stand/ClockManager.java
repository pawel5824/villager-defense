package pl.vd.stand;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import pl.vd.VillagerDefensePlugin;
import pl.vd.arena.Arena;
import pl.vd.stand.ArmorStandTuner.DisplayPart;
import pl.vd.util.AntiSpam;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Zegar ze stojaka (/stojak): stojak o nazwie „zegar_wskazowki" (custom name,
 * /stojak nazwij) z TRZEMA doczepionymi częściami-wskazówkami:
 *   część 1 = wskazówka GODZINNA  - obrót 30° dokładnie co godzinę,
 *   część 2 = wskazówka MINUTOWA  - obrót 6° dokładnie co minutę,
 *   część 3 (np. red_concrete)    = wskazówka SEKUNDOWA - obrót 6° co dokładną sekundę.
 *
 * Wskazówki obracają się parametrem SKRĘT (tryb SUGAR, rotZ) - dokładnie tym
 * samym, którym stroisz je w /stojak, więc zegar działa tak samo dla tarczy na
 * ścianie, jak i na ziemi (wystarczy ustawić wskazówki skrętem na pozycję 12).
 *
 * Licznik startuje, gdy arena się rozpocznie (startGame), a po jej zakończeniu
 * (endGame) wskazówki wracają do stanu początkowego. Bez areny można obejrzeć
 * ruch wskazówek testem: /stojak zegar test (przyspieszenie 2.5×, 24 minuty =
 * godzina; anuluj: /stojak zegar anuluj). Numeracja części = ta sama, którą
 * widać w action barze /stojak (CZĘŚĆ 1, CZĘŚĆ 2, ...). Kierunek obrotu
 * odwrócisz w config.yml (zegar.reverse).
 *
 * Tryb zamiatający (/stojak zegar tryb zamiatajacy albo zegar.sweep): wszystkie
 * wskazówki idą PRAWDZIWYMI kątami zegara, bez przycinania do 10° - sekundowa
 * 6° co sekundę, minutowa 6° co minutę, godzinna 30° co godzinę (pełne koło
 * 12 -> 3 -> 6 -> 9 -> 12); na granicy każdej ćwiartki (90°) „stara" wskazówka
 * znika, a w tym samym miejscu pojawia się NOWA - to druga kopia części (klon),
 * która przejmuje obrót i kręci się dalej swobodnie parametrem SKRĘT. W TYM
 * SAMYM MOMENCIE, w którym wskazówka się obraca, część dostaje drobne
 * przesunięcie w kierunku chodu (rośnie równo od 0 do 0.05 na ćwiartkę): 12->3
 * idzie SKOSEM (+X i +Y), 3->6 tylko -Y, 6->9 tylko -X, 9->12 tylko +Y. Kolejne
 * ćwiartki CIĄGNĄ się po sobie - nowa wskazówka pojawia się w miejscu, w którym
 * poprzednia skończyła (np. 12->3 kończy o +0.05 w X i +0.05 w Y, a 3->6
 * startuje z tego miejsca); dopiero na powrocie do 12 cykl zaczyna się od nowa
 * od pozycji bazowej (wysokość / przesunięcie X ustawione w /stojak). Klon jest
 * tworzony automatycznie na starcie zegara i sprzątany po zakończeniu, więc nie
 * musisz doczepiać dodatkowych części.
 */
public class ClockManager {

    private final VillagerDefensePlugin plugin;
    private final ArmorStandTuner tuner;

    /** Nazwa areny -> stan jej zegara (kiedy wystartował i które stojaki obraca). */
    private final Map<String, ClockState> clocks = new HashMap<>();
    /** Test zegara bez areny (/stojak zegar test): UUID stojaka -> stan. */
    private final Map<UUID, TestState> tests = new HashMap<>();

    /** Czas trwania testu: 1 minuta. */
    private static final long TEST_DURATION_MS = 60_000;
    /** Domyślna prędkość testu (zegar.test-speed): 1.0 = normalna, prawdziwe tempo 1:1. */
    private static final double DEFAULT_TEST_SPEED = 1.0;
    /** PDC stojaka: styl „zamiatający" włączony (przetrwa restart) -
     *  /stojak zegar tryb normalny|zamiatajacy. Brak wpisu = config zegar.sweep. */
    private static final NamespacedKey KEY_CLOCK_SWEEP = NamespacedKey.fromString("villagerdefense:vd_clock_sweep");

    private static class ClockState {
        long startMillis;
        boolean reverse;
        final List<StandClock> stands = new ArrayList<>();
    }

    /** Wspólny zapis stanu wskazówek (arena i test): stan bazowy + ostatnie kąty. */
    private static class StandClock {
        final UUID standId;
        /** Styl „zamiatający": pełne koło z podmianą wskazówki (stara znika,
         *  nowa - klon - pojawia się) na każdej ćwiartce 90° (per stojak: PDC
         *  /stojak zegar tryb, inaczej config zegar.sweep). */
        boolean sweep;
        /** Klony wskazówek (druga kopia części do podmiany w trybie zamiatającym);
         *  indeks 0/1/2 = godziny/minuty/sekundy; null, gdy się nie udało. */
        final List<DisplayPart> clones = new ArrayList<>();
        /** Która kopia jest aktualnie widoczna (true = klon, false = oryginał). */
        final boolean[] cloneActive = {false, false, false};
        /** Stan części w chwili startu (indeks 0/1/2 = godziny/minuty/sekundy):
         *  {rotX, rotY, rotZ, tx, ty, tz} - do przywrócenia po grze/testcie. */
        final List<double[]> baseState = new ArrayList<>();
        /** Ostatnio nałożone wartości rotZ (indeks 0/1/2) - pomijamy powtórzenia. */
        final double[] last = {Double.NaN, Double.NaN, Double.NaN};
        /** Tryb zamiatający „krokowy": ostatnia ćwiartka (0-3) każdej wskazówki -
         *  do wykrycia granicy 90° (na niej podmieniana jest wskazówka). */
        final int[] prevQuarter = {0, 0, 0};

        StandClock(UUID standId) {
            this.standId = standId;
        }
    }

    private static class TestState extends StandClock {
        long startMillis;
        UUID owner;

        TestState(UUID standId) {
            super(standId);
        }
    }

    public ClockManager(VillagerDefensePlugin plugin, ArmorStandTuner tuner) {
        this.plugin = plugin;
        this.tuner = tuner;
    }

    // ===================== arena =====================

    /**
     * Start zegara razem z areną: włącza licznik (od tej chwili wskazówki się
     * kręcą) i zapamiętuje stan początkowy części, do którego wrócą po grze.
     * Stojaki-zegary szukane są w świecie areny (awaryjnie - we wszystkich światach).
     */
    public void startClock(Arena arena) {
        ClockState state = new ClockState();
        state.startMillis = System.currentTimeMillis();
        state.reverse = reverse();
        World world = arenaWorld(arena);
        if (world == null) {
            // awaryjnie: poszukaj stojaka-zegara we WSZYSTKICH światach
            for (World w : Bukkit.getWorlds()) collectClocks(w, state);
        } else {
            collectClocks(world, state);
        }
        if (state.stands.isEmpty()) return;
        clocks.put(arena.getName(), state);
        plugin.getLogger().info("Zegar wystartował na arenie „" + arena.getName() + "” ("
                + state.stands.size() + " " + (state.stands.size() == 1 ? "stojak" : "stojaki") + ").");
    }

    /** Zbiera w danym świecie stojaki-zegary (custom name = zegar.name) i śledzi ich części. */
    private void collectClocks(World world, ClockState state) {
        String name = clockName();
        for (Entity e : world.getEntities()) {
            if (!(e instanceof ArmorStand stand) || stand.isDead()) continue;
            String n = stand.getCustomName();
            if (n == null || !n.toLowerCase(Locale.ROOT).equals(name)) continue;
            tuner.rebuildParts(stand); // śledzenie części (też po restarcie serwera)
            tests.remove(stand.getUniqueId()); // test nie może walczyć z zegarem areny
            StandClock sc = new StandClock(stand.getUniqueId());
            sc.sweep = sweepOf(stand);
            // zapamiętaj stan początkowy części, żeby po grze wskazówki wróciły
            // DOKŁADNIE tam, gdzie je ustawiłeś (skrętem/obrotem/pochyleniem)
            List<DisplayPart> parts = tuner.getParts(stand);
            for (DisplayPart p : parts) {
                sc.baseState.add(new double[]{p.rotX, p.rotY, p.rotZ, p.tx, p.ty, p.tz});
            }
            // wyśrodkuj wskazówki na osi obrotu (zegar.center) - WYŁĄCZONE domyślnie,
            // patrz centerHands; klony powstają PO ewentualnym wyśrodkowaniu
            centerHands(parts);
            prepareSweep(stand, sc); // klony do trybu zamiatającego (sweep)
            if (sc.baseState.size() < 3) {
                plugin.getLogger().warning("Stojak-zegar „" + n + "” ma tylko " + sc.baseState.size()
                        + " części, a potrzebne są 3 (część 1 = godziny, część 2 = minuty, część 3 = sekundy).");
            }
            state.stands.add(sc);
        }
    }

    /**
     * Koniec gry na arenie: wskazówki jej zegara wracają do stanu początkowego
     * (stan części zapamiętany przy starcie).
     */
    public void stopClock(Arena arena) {
        ClockState state = clocks.remove(arena.getName());
        if (state == null) return;
        for (StandClock sc : state.stands) {
            Entity ent = Bukkit.getEntity(sc.standId);
            if (ent instanceof ArmorStand stand && !stand.isDead()) resetStand(stand, sc);
        }
    }

    /** Resetuje WSZYSTKIE zegary i testy (np. przy wyłączaniu serwera). */
    public void resetAll() {
        for (ClockState state : clocks.values()) {
            for (StandClock sc : state.stands) {
                Entity ent = Bukkit.getEntity(sc.standId);
                if (ent instanceof ArmorStand stand && !stand.isDead()) resetStand(stand, sc);
            }
        }
        clocks.clear();
        for (TestState t : tests.values()) {
            Entity ent = Bukkit.getEntity(t.standId);
            if (ent instanceof ArmorStand stand && !stand.isDead()) resetStand(stand, t);
        }
        tests.clear();
    }

    // ===================== test bez areny (/stojak zegar test) =====================

    /**
     * Podgląd zegara bez areny: przez 1 minutę wskazówki kręcą się do przodu
     * z prędkością zegar.test-speed (domyślnie 1.0 = normalna, prawdziwe tempo
     * 1:1). Anuluj: /stojak zegar anuluj.
     */
    public void startTest(Player player) {
        ArmorStand stand = findClockStand(player);
        if (stand == null) {
            AntiSpam.send(player, "§cNie znalazłem stojaka-zegara „" + clockName() + "” w tym świecie.");
            return;
        }
        if (tests.containsKey(stand.getUniqueId()) || inArenaClock(stand.getUniqueId())) {
            AntiSpam.send(player, "§cZegar już działa (test albo arena). Najpierw anuluj: §e/stojak zegar anuluj§c.");
            return;
        }
        tuner.rebuildParts(stand); // śledzenie części (też po restarcie serwera)
        TestState t = new TestState(stand.getUniqueId());
        t.startMillis = System.currentTimeMillis();
        t.owner = player.getUniqueId();
        t.sweep = sweepOf(stand);
        List<DisplayPart> parts = tuner.getParts(stand);
        for (DisplayPart p : parts) {
            t.baseState.add(new double[]{p.rotX, p.rotY, p.rotZ, p.tx, p.ty, p.tz});
        }
        // wyśrodkuj wskazówki na osi obrotu (zegar.center) - patrz centerHands
        centerHands(parts);
        prepareSweep(stand, t); // klony do trybu zamiatającego (sweep)
        tests.put(stand.getUniqueId(), t);
        AntiSpam.send(player, "§aTest zegara. Prędkość " + testSpeed());
    }

    /** Anuluje test zegara i przywraca wskazówki do stanu początkowego. */
    public void cancelTest(Player player) {
        ArmorStand stand = findClockStand(player);
        if (stand == null) {
            AntiSpam.send(player, "§cNie znalazłem stojaka-zegara „" + clockName() + "” w tym świecie.");
            return;
        }
        TestState t = tests.remove(stand.getUniqueId());
        if (t == null) {
            AntiSpam.send(player, "§7Test zegara nie jest uruchomiony.");
            return;
        }
        resetStand(stand, t);
        AntiSpam.send(player, "§7Anulowano test");
    }

    /**
     * Przełącza styl zegara na stojaku (/stojak zegar tryb normalny|zamiatajacy):
     * zapis w PDC stojaka (przetrwa restart). Działa od razu na bieżącym teście
     * i na zegarze areny, jeśli stojak już kręci wskazówkami.
     */
    public void setSweep(Player player, boolean sweep) {
        ArmorStand stand = findClockStand(player);
        if (stand == null) {
            AntiSpam.send(player, "§cNie znalazłem stojaka-zegara „" + clockName() + "” w tym świecie.");
            return;
        }
        stand.getPersistentDataContainer().set(KEY_CLOCK_SWEEP, PersistentDataType.BYTE, (byte) (sweep ? 1 : 0));
        // zastosuj od razu na bieżącym teście / zegarze areny tego stojaka
        TestState t = tests.get(stand.getUniqueId());
        if (t != null) {
            t.sweep = sweep;
            if (sweep) prepareSweep(stand, t);
        }
        for (ClockState state : clocks.values()) {
            for (StandClock sc : state.stands) {
                if (sc.standId.equals(stand.getUniqueId())) {
                    sc.sweep = sweep;
                    if (sweep) prepareSweep(stand, sc);
                }
            }
        }
        // komunikat na CZAT (nie w action barze)
        player.sendMessage(Component.text(sweep
                ? "§aZegar: tryb zamiatający"
                : "§aZegar: tryb normalny"));
    }

    /** Czy stojak bierze udział w zegarze którejś areny? */
    private boolean inArenaClock(UUID standId) {
        for (ClockState state : clocks.values()) {
            for (StandClock sc : state.stands) {
                if (sc.standId.equals(standId)) return true;
            }
        }
        return false;
    }

    /** Najbliższy stojak-zegar (custom name = zegar.name) w świecie gracza. */
    private ArmorStand findClockStand(Player player) {
        String name = clockName();
        ArmorStand best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity e : player.getWorld().getEntities()) {
            if (!(e instanceof ArmorStand stand) || stand.isDead()) continue;
            String n = stand.getCustomName();
            if (n == null || !n.toLowerCase(Locale.ROOT).equals(name)) continue;
            double d = stand.getLocation().distanceSquared(player.getLocation());
            if (d < bestDist) {
                bestDist = d;
                best = stand;
            }
        }
        return best;
    }

    // ===================== wspólna logika obrotu =====================

    /**
     * Wyśrodkowuje wskazówki (pierwsze 3 części) na osi stojaka - WYŁĄCZONE
     * domyślnie (zegar.center: false): przy obrocie skrętem (rotZ) część kręci
     * się wokół WŁASNEGO środka, a wskazówki mogą być celowo oddalone od stojaka,
     * więc plugin NIE rusza ich pozycji (żeby nie „teleportowały" się do stojaka
     * przy teście / starcie areny). Opcja do włączenia tylko, gdy wskazówki stoją
     * blisko stojaka i chcesz automatycznie wycentrować je na jego osi (X = 0).
     * Po grze/testcie wskazówki wracają DOKŁADNIE tam, gdzie je ustawiłeś.
     */
    private void centerHands(List<DisplayPart> parts) {
        if (!plugin.getConfig().getBoolean("zegar.center", false)) return;
        for (int i = 0; i < 3 && i < parts.size(); i++) {
            DisplayPart p = parts.get(i);
            if (p.tx == 0) continue;
            p.tx = 0; // środek części dokładnie na osi obrotu stojaka
            p.apply();
        }
    }

    /**
     * Przygotowuje tryb zamiatający: tworzy KLONY wskazówek (drugie kopie części),
     * które na granicy ćwiartki przejmą obrót („stara znika, nowa się pojawia").
     * Klony startują ukryte; sprzątane w resetStand. Nie ruszają oryginalnych części.
     */
    private void prepareSweep(ArmorStand stand, StandClock sc) {
        if (!sc.sweep || !sc.clones.isEmpty() || stand == null || stand.isDead()) return;
        List<DisplayPart> parts = tuner.getParts(stand);
        for (int i = 0; i < 3 && i < parts.size(); i++) {
            sc.clones.add(parts.get(i).clonePart(stand));
        }
    }

    /** Usuwa klony wskazówek stojaka (tryb zamiatający) - także, gdy stojak
     *  zniknął w trakcie zegara (inaczej pasażerowie zostaliby w świecie). */
    private void cleanupClones(StandClock sc) {
        for (DisplayPart clone : sc.clones) {
            if (clone != null) clone.removePart();
        }
        sc.clones.clear();
        sc.cloneActive[0] = sc.cloneActive[1] = sc.cloneActive[2] = false;
    }

    /**
     * Resetuje wskazówki stojaka (arena/test): usuwa klony trybu zamiatającego
     * i przywraca oryginalne części do stanu początkowego.
     */
    private void resetStand(ArmorStand stand, StandClock sc) {
        cleanupClones(sc);
        resetStand(stand, sc.baseState);
    }

    /** Przywraca wskazówki stojaka do stanu początkowego (obroty + przesunięcia). */
    private void resetStand(ArmorStand stand, List<double[]> baseState) {
        List<DisplayPart> list = tuner.getParts(stand);
        for (int i = 0; i < baseState.size() && i < list.size(); i++) {
            double[] base = baseState.get(i);
            DisplayPart p = list.get(i);
            p.rotX = base[0];
            p.rotY = base[1];
            p.rotZ = base[2];
            p.tx = base[3];
            p.ty = base[4];
            p.tz = base[5];
            p.apply();
        }
    }

    /**
     * Kąty wskazówek dla upływającego czasu (ms) - PRAWDZIWY czas zegara:
     * sekundowa robi pełne koło co 60 s (6°/s), minutowa co 60 min (6°/min),
     * godzinna co 12 h (30°/h). W trybie zamiatającym (zegar.sweep) na granicy
     * każdej ćwiartki stara wskazówka znika, a w tym samym miejscu pojawia się
     * nowa - patrz applyHands (podmiana oryginał/klon).
     */
    private static double[] clockAngles(long elapsedMs) {
        long seconds = elapsedMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        return new double[]{
                (hours % 12) * 30.0,     // godzinna: 30° co godzinę (pełne koło co 12 h)
                (minutes % 60) * 6.0,    // minutowa: 6° co minutę (pełne koło co 60 min)
                (seconds % 60) * 6.0     // sekundowa: 6° co sekundę (pełne koło co 60 s)
        };
    }

    /**
     * Nakłada kąty na wskazówki stojaka. Każda wskazówka obraca się parametrem
     * SKRĘT (rotZ - tryb SUGAR „skręt"), czyli dokładnie tym samym, którym
     * została ustawiona w /stojak: rotZ = bazowe + kąt zegara. Część jest
     * odświeżana tylko wtedy, gdy jej rotZ faktycznie się zmienił.
     *
     * Tryb NORMALNY: płynny, dokładny obrót (godzinna 30°/h, minutowa 6°/min,
     * sekundowa 6°/s).
     *
     * Tryb ZAMIATAJĄCY (sweep): wszystkie wskazówki idą PRAWDZIWYMI kątami
     * zegara bez przycinania do 10° - sekundowa 6° co sekundę, minutowa 6° co
     * minutę, godzinna 30° co godzinę (pełne koło 12 -> 3 -> 6 -> 9 -> 12); na
     * granicy każdej ćwiartki (90° - pozycje 3, 6, 9 i z powrotem 12) „stara"
     * wskazówka znika (hidePart), a w tym samym miejscu pojawia się NOWA - druga
     * kopia części (klon) - która od tego kąta obraca się dalej swobodnie
     * skrętem; przy następnej ćwiartce podmiana wraca (klon znika, oryginał
     * wraca) itd. W TYM SAMYM MOMENCIE, w którym wskazówka się obraca, część
     * dostaje drobne przesunięcie w kierunku chodu (rośnie równo od 0 do 0.05
     * na każdą ćwiartkę): 12->3 = skos (+X i +Y), 3->6 = tylko -Y, 6->9 = tylko
     * -X, 9->12 = tylko +Y. Nowa wskazówka pojawia się w miejscu, w którym
     * poprzednia ćwiartka skończyła (ciągły chód wokół tarczy); na powrocie do
     * 12 cykl startuje od nowa z pozycji bazowej - patrz applySweepStep.
     */
    private void applyHands(ArmorStand stand, StandClock sc, double[] angles, boolean reverse) {
        List<DisplayPart> list = tuner.getParts(stand);
        for (int i = 0; i < 3; i++) {
            if (i >= list.size() || i >= sc.baseState.size()) continue;
            double[] base = sc.baseState.get(i);
            DisplayPart active = list.get(i);
            DisplayPart clone = i < sc.clones.size() ? sc.clones.get(i) : null;
            if (sc.sweep && clone != null) {
                applySweepStep(sc, i, active, clone, base, angles[i], reverse);
            } else if (sc.cloneActive[i]) {
                // tryb zamiatający wyłączony w trakcie obrotu: przywróć oryginał
                double target = base[2] + (reverse ? -angles[i] : angles[i]);
                if (sc.last[i] == target) continue;
                active.rotZ = target;
                active.apply();
                if (clone != null) clone.hidePart();
                sc.cloneActive[i] = false;
                sc.last[i] = target;
            } else {
                double target = base[2] + (reverse ? -angles[i] : angles[i]);
                if (sc.last[i] == target) continue;
                active.rotZ = target; // normalny obrót (albo brak klonu)
                active.apply();
                sc.last[i] = target;
            }
        }
    }

    /**
     * Krok trybu zamiatającego: wskazówka przesuwa się (skręt) i W TYM SAMYM
     * MOMENCIE dostaje drobne przesunięcie wg wzorca użytkownika (sweepShift),
     * żeby mimo przesuniętego środka trzymała się tarczy. WSZYSTKIE wskazówki
     * idą prawdziwymi kątami zegara, bez przycinania do 10°: sekundowa 6° co
     * sekundę, minutowa 6° co minutę, godzinna 30° co godzinę - każda skacze
     * o swój kąt dokładnie na granicy sekundy/minuty/godziny. Na granicy
     * ćwiartki (90°, 180°, 270°, 360°) stara wskazówka znika, a w tym samym
     * miejscu pojawia się nowa (klon) - ustawiana na pozycję STARTU nowej
     * ćwiartki (tam, gdzie poprzednia skończyła), więc podmiana jest niewidoczna;
     * na powrocie do 12 (ćwiartka 0) wskazówka wraca do pozycji bazowej i cykl
     * zaczyna się od nowa. Krok liczony jest z dokładnego czasu (bez dryfu),
     * w kierunku chodu.
     */
    private void applySweepStep(StandClock sc, int i, DisplayPart active, DisplayPart clone,
                                double[] base, double angleDeg, boolean reverse) {
        // postęp wskazówki wzdłuż trasy (0..360): 12 -> 3 -> 6 -> 9 -> 12
        double p = ((angleDeg % 360) + 360) % 360;
        int q = (int) (p / 90);               // ćwiartka 0-3 (granice 90°/180°/270°/360°)
        // prawdziwy kąt zegara (bez przycinania do 10°): sekundowa 6°/s,
        // minutowa 6°/min, godzinna 30°/h - skok dokładnie co sekundę/minutę/godzinę
        double target = base[2] + (reverse ? -p : p);
        double progress = (p - q * 90.0) / 90.0; // postęp w ćwiartce 0..1 (napędza przesunięcia)
        if (sc.last[i] == target && sc.prevQuarter[i] == q) return; // ten sam krok
        boolean crossing = !Double.isNaN(sc.last[i]) && sc.prevQuarter[i] != q;
        if (crossing) {
            // granica ćwiartki: stara znika, nowa (klon) pojawia się w miejscu,
            // w którym NOWA ćwiartka zaczyna swój ruch - kolejne ćwiartki ciągną
            // się po sobie (tylko na powrocie do 12 wskazówka wraca do bazy)
            DisplayPart outgoing = sc.cloneActive[i] ? clone : active;
            DisplayPart incoming = sc.cloneActive[i] ? active : clone;
            double[] shift = sweepShift(q, 0.0, reverse); // start ćwiartki (postęp 0)
            outgoing.hidePart();
            incoming.rotZ = target;
            incoming.tx = base[3] + shift[0];
            incoming.ty = base[4] + shift[1];
            incoming.apply();
            sc.cloneActive[i] = !sc.cloneActive[i];
        } else {
            double[] shift = sweepShift(q, progress, reverse);
            DisplayPart cur = sc.cloneActive[i] ? clone : active;
            cur.rotZ = target;
            cur.tx = base[3] + shift[0];
            cur.ty = base[4] + shift[1];
            cur.apply();
        }
        sc.prevQuarter[i] = q;
        sc.last[i] = target;
    }

    /**
     * Drobne przesunięcie (dx, dy) części-wskazówki w danym kroku trybu
     * zamiatającego - wzorzec użytkownika: przesunięcie rośnie RÓWNO Z POSTĘPEM
     * SKRĘTU (w tym samym momencie, w którym wskazówka się obraca): od 0 na
     * starcie ćwiartki do pełnej wartości (0.05) na jej końcu. Wskazówki idą
     * prawdziwymi kątami zegara: sekundowa 6° co sekundę i minutowa 6° co minutę
     * (po 15 kroków na ćwiartkę, po 0.0033), godzinna 30° co godzinę (po
     * 3 kroki na ćwiartkę, po 0.0167).
     *
     * Kierunki chodu (reverse=true, domyślnie): 12->3 idzie SKOSEM (+X i +Y,
     * np. wysokość -1 -> -0.95 i przesunięcie X +4.47 -> +4.52), 3->6 tylko -Y
     * (-0.95 -> -1), 6->9 tylko -X (+4.52 -> +4.47), 9->12 tylko +Y (-1 ->
     * -0.95). Kolejne ćwiartki CIĄGNĄ się po sobie: każda zaczyna w miejscu,
     * w którym poprzednia skończyła (start ćwiartki 1 = koniec ćwiartki 0 itd.),
     * a na powrocie do 12 (ćwiartka 0) cykl zaczyna się OD NOWA od pozycji
     * bazowej. Przy reverse=false lustrzane (zegar do tyłu).
     */
    private static double[] sweepShift(int quarter, double progress, boolean reverse) {
        // pełne przesunięcie ćwiartki (koniec, postęp 1) - wzorzec użytkownika (5 × 0.01)
        double full = 0.05;
        // równo z postępem skrętu: 0 na starcie ćwiartki -> 0.05 na jej końcu
        double d = full * Math.max(0.0, Math.min(progress, 1.0));
        if (reverse) {
            // start ćwiartki (względem bazy) = miejsce, w którym poprzednia skończyła
            double[] start = switch (quarter) {
                case 0 -> new double[]{0, 0};       // 12 -> 3: od nowa (baza)
                case 1 -> new double[]{full, full}; // 3 -> 6: po 12->3 (+X i +Y)
                case 2 -> new double[]{full, 0};    // 6 -> 9: po 3->6 (tylko -Y)
                default -> new double[]{0, 0};      // 9 -> 12: po 6->9 (tylko -X)
            };
            double[] move = switch (quarter) {
                case 0 -> new double[]{d, d};   // 12 -> 3: +X i +Y (skos)
                case 1 -> new double[]{0, -d};  // 3 -> 6: tylko -Y
                case 2 -> new double[]{-d, 0};  // 6 -> 9: tylko -X
                default -> new double[]{0, d};  // 9 -> 12: tylko +Y
            };
            return new double[]{start[0] + move[0], start[1] + move[1]};
        }
        // reverse=false: lustrzane (zegar do tyłu)
        double[] start = switch (quarter) {
            case 0 -> new double[]{0, 0};         // 12 -> 9: od nowa (baza)
            case 1 -> new double[]{-full, -full}; // 9 -> 6: po 12->9 (-X i -Y)
            case 2 -> new double[]{-full, 0};     // 6 -> 3: po 9->6 (tylko +Y)
            default -> new double[]{0, 0};        // 3 -> 12: po 6->3 (tylko +X)
        };
        double[] move = switch (quarter) {
            case 0 -> new double[]{-d, -d};  // 12 -> 9: -X i -Y (skos)
            case 1 -> new double[]{0, d};    // 9 -> 6: tylko +Y
            case 2 -> new double[]{d, 0};    // 6 -> 3: tylko +X
            default -> new double[]{0, -d};  // 3 -> 12: tylko -Y
        };
        return new double[]{start[0] + move[0], start[1] + move[1]};
    }

    /**
     * Co tick: przesuwa wskazówki zegarów aren i testów wg czasu, który minął
     * od startu. Kąty liczone są z milisekund, więc skok następuje dokładnie na
     * granicy sekundy/minuty/godziny (bez dryfu).
     */
    public void tick() {
        long now = System.currentTimeMillis();
        if (!tests.isEmpty()) tickTests(now);
        if (clocks.isEmpty()) return;
        for (ClockState state : new ArrayList<>(clocks.values())) {
            state.stands.removeIf(sc -> {
                Entity ent = Bukkit.getEntity(sc.standId);
                if (!(ent instanceof ArmorStand stand) || stand.isDead()) {
                    cleanupClones(sc); // stojak zniknął - posprzątaj klony
                    return true;
                }
                applyHands(stand, sc, clockAngles(now - state.startMillis), state.reverse);
                return false;
            });
        }
        clocks.values().removeIf(state -> state.stands.isEmpty());
    }

    /** Testy /stojak zegar test: przyspieszony ruch + auto-zakończenie po minucie. */
    private void tickTests(long now) {
        for (Map.Entry<UUID, TestState> e : new ArrayList<>(tests.entrySet())) {
            TestState t = e.getValue();
            long realElapsed = now - t.startMillis;
            if (realElapsed >= TEST_DURATION_MS) {
                tests.remove(e.getKey());
                Entity ent = Bukkit.getEntity(e.getKey());
                if (ent instanceof ArmorStand stand && !stand.isDead()) resetStand(stand, t);
                Player owner = Bukkit.getPlayer(t.owner);
                if (owner != null) {
                    AntiSpam.send(owner, "§7Test zegara zakończony");
                }
                continue;
            }
            Entity ent = Bukkit.getEntity(e.getKey());
            if (!(ent instanceof ArmorStand stand) || stand.isDead()) {
                cleanupClones(t); // stojak zniknął - posprzątaj klony
                tests.remove(e.getKey()); // test sam się kończy
                continue;
            }
            applyHands(stand, t, clockAngles((long) (realElapsed * testSpeed())), reverse());
        }
    }

    /** Nazwa stojaka-zegara z config.yml. */
    private String clockName() {
        return plugin.getConfig().getString("zegar.name", "zegar_wskazowki").toLowerCase(Locale.ROOT);
    }

    /** Kierunek obrotu z config.yml (zegar.reverse). Domyślnie odwrócony
     *  (true) - wskazówki kręcą się w drugą stronę; ustaw false, żeby wrócić. */
    private boolean reverse() {
        return plugin.getConfig().getBoolean("zegar.reverse", true);
    }

    /** Prędkość testu /stojak zegar test (zegar.test-speed, domyślnie 1.0 = normalna). */
    private double testSpeed() {
        return plugin.getConfig().getDouble("zegar.test-speed", DEFAULT_TEST_SPEED);
    }

    /** Styl „zamiatający" stojaka: wpis z PDC (/stojak zegar tryb), inaczej config. */
    private boolean sweepOf(ArmorStand stand) {
        if (stand != null && KEY_CLOCK_SWEEP != null) {
            Byte b = stand.getPersistentDataContainer().get(KEY_CLOCK_SWEEP, PersistentDataType.BYTE);
            if (b != null) return b == 1;
        }
        return plugin.getConfig().getBoolean("zegar.sweep", false);
    }

    /** Świat areny (z punktów spawnu graczy/wieśniaków albo lokacji końcowej/lobby). */
    private World arenaWorld(Arena arena) {
        if (!arena.getPlayerSpawns().isEmpty() && arena.getPlayerSpawns().get(0).getWorld() != null) {
            return arena.getPlayerSpawns().get(0).getWorld();
        }
        if (!arena.getVillagerSpawns().isEmpty() && arena.getVillagerSpawns().get(0).getWorld() != null) {
            return arena.getVillagerSpawns().get(0).getWorld();
        }
        if (arena.getEndLocation() != null && arena.getEndLocation().getWorld() != null) {
            return arena.getEndLocation().getWorld();
        }
        if (arena.getLobbyLocation() != null && arena.getLobbyLocation().getWorld() != null) {
            return arena.getLobbyLocation().getWorld();
        }
        return null;
    }
}
