package pl.vd.raft.music;

import org.bukkit.Bukkit;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import pl.vd.raft.RaftPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Muzyka trybu Raft — losowe utwory z paczki zasobów z losowymi przerwami.
 *
 * Utwory definiuje paczka (raft-hud): assets/raft/sounds.json → identyfikatory
 * typu {@code raft:music.temat} (klucz paczki "music.temat", kropka — tak jak
 * wanilia pisze minecraft:music.game), a lista identyfikatorów siedzi w config.yml
 * (sekcja raft-music.tracks). Plugin kilka razy na sekundę (4 Hz) sprawdza
 * graczy w świecie Raft i po wylosowanej przerwie gra im UTWÓR TYLKO DLA NICH:
 * {@code player.playSound(loc, track, MUSIC, głośność, ton)}.
 *
 * Ustawienia PER GRACZ (raft-players.yml → settings, przetrwają restart):
 *  - music-enabled   (on/off)         — czy w ogóle ma grać,
 *  - music-volume    (0.0–1.0)        — głośność utworu (pakiet playSound
 *    + kanał raft:musicvol, którym mod zmienia ją NA ŻYWO),
 *  - music-pitch     (0.5–2.0)        — ton JEDNOCZEŚNIE z prędkością (winyl),
 *  - music-frequency (natychmiastowo/czesto/normalnie/rzadko/nigdy) — sterownik
 *    przerw: natychmiastowo = utwór gra DO KOŃCA, a następny rusza od razu
 *    (długość z TrackDurations), dalej mnożniki z configu, nigdy = cisza,
 *  - music-loop (on/off) — zapętlenie: kolejny utwór = TEN SAM co ostatnio
 *    (zamiast losowania); wybór utworu do zapętlenia = ostatnio grany,
 *  - music-selected-track (id utworu albo puste) — gracz WYBIERA konkretny
 *    utwór płytą w GUI: JEDNO odtworzenie, potem wybór czyści się sam;
 *    puste = losowanie/zapętlenie jak wyżej.
 *
 * Bez żadnych komunikatów w grze i w logu (zasada ciszy) — brak plików .ogg
 * w paczce po prostu milczy po stronie klienta, dopóki właściciel ich nie doda.
 *
 * Waniliowa muzyka Minecrafta gra niezależnie (inny identyfikator, ta sama
 * kategoria MUSIC — gracz z suwakiem muzyki na 0 nie usłyszy ani tamtej, ani tej).
 */
public class MusicManager {

    // ─── Klucze ustawień gracza (raft-players.yml → settings) ───

    public static final String SETTING_ENABLED = "music-enabled";   // on/off
    public static final String SETTING_VOLUME = "music-volume";     // 0.0–1.0
    public static final String SETTING_PITCH = "music-pitch";       // 0.5–2.0
    public static final String SETTING_FREQ = "music-frequency";    // natychmiastowo|czesto|normalnie|rzadko|nigdy
    public static final String SETTING_LOOP = "music-loop";         // on/off (powtarzaj ostatni utwór)
    public static final String SETTING_SELECTED = "music-selected-track"; // id wybranego utworu lub ""

    /** Dozwolone wartości częstotliwości (kolejność = kolejność przełączania;
     *  „natychmiastowo" = utwór gra do końca i od razu rusza następny). */
    public static final String[] FREQ_VALUES = {"natychmiastowo", "czesto", "normalnie", "rzadko", "nigdy"};
    /** Mnożniki przerw z configu dla kolejnych wartości częstotliwości
     *  („natychmiastowo" i „nigdy" mają własną obsługę w scheduleNext). */
    private static final double[] FREQ_MULTIPLIERS = {0.0, 0.5, 1.0, 2.0, 0.0};
    /** Etykiety wyświetlane graczowi (z polskimi znakami). */
    public static final String[] FREQ_LABELS = {"natychmiastowo", "często", "normalnie", "rzadko", "nigdy"};

    // ─── Ustawienia z config.yml (sekcja raft-music) — wczytuje loadConfig() ───

    private boolean masterEnabled = true;
    private long intervalMinMs = 180_000;
    private long intervalMaxMs = 480_000;
    private long startDelayMs = 45_000;
    private float defaultVolume = 1.0f;
    private float defaultPitch = 1.0f;
    private final List<String> tracks = new ArrayList<>();
    /** Długości utworów — tryb „natychmiastowo" planuje po nich następny. */
    private final TrackDurations durations = new TrackDurations();

    // ─── Stan graczy w świecie Raft ───

    /** Kiedy graczowi zagrać następny utwór (millis; tylko gracze w świecie Raft). */
    private final Map<UUID, Long> nextPlayAt = new HashMap<>();
    /** Ostatnio zagrany utwór — żeby nie powtarzać tego samego pod rząd. */
    private final Map<UUID, String> lastTrack = new HashMap<>();
    /** Utworku grający w TEJ CHWILI (per gracz) — czyszczony w stopTracks.
     *  Zapisywany, żeby /muzyka status mógł pokazać stan „co teraz gra”. */
    private final Map<UUID, String> currentTrack = new HashMap<>();
    /** Ostatnio WYSŁANA korekta głośności (raft:musicvol) — wysyłka tylko przy zmianie. */
    private final Map<UUID, Float> lastSentVolume = new HashMap<>();

    private final RaftPlugin plugin;
    private int taskId = -1;

    public MusicManager(RaftPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    /** Wczytuje sekcję raft-music z configu rodzica (start i /def reload). */
    public void loadConfig() {
        org.bukkit.configuration.ConfigurationSection s =
                plugin.getConfig().getConfigurationSection("raft-music");
        if (s == null) return;
        masterEnabled = s.getBoolean("enabled", true);
        intervalMinMs = Math.max(5_000L, s.getLong("interval-min-seconds", 180) * 1000L);
        intervalMaxMs = Math.max(intervalMinMs, s.getLong("interval-max-seconds", 480) * 1000L);
        startDelayMs = Math.max(3_000L, s.getLong("start-delay-seconds", 45) * 1000L);
        defaultVolume = (float) s.getDouble("default-volume", 1.0);
        defaultPitch = (float) s.getDouble("default-pitch", 1.0);
        tracks.clear();
        for (String t : s.getStringList("tracks")) {
            if (t != null && !t.isBlank()) tracks.add(t);
        }
        durations.loadConfig(s);
    }

    /** Startuje pętlę co 0,25 s (4 Hz). Częściej niż sekunda, bo tryb
     *  „natychmiastowo” startuje następny utwór TUŻ po końcu poprzedniego —
     *  przy 1 Hz następny utwór mógłby się spóźnić nawet o sekundę. */
    public void start() {
        stop();
        taskId = Bukkit.getScheduler().runTaskTimer(
                plugin.getParent(), this::tick, 5L, 5L).getTaskId();
    }

    /** Zatrzymuje pętlę i czyta stan (wywoływane przy wyłączeniu pluginu). */
    public void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        nextPlayAt.clear();
        lastTrack.clear();
        currentTrack.clear();
        lastSentVolume.clear();
    }

    /**
     * Zapomina cały stan gracza — wywoływane przy wyjściu z serwera (bez tego
     * mapy rosną z każdym graczem, a nieaktualny lastSentVolume tłumi ponowną
     * wysyłkę korekty po restarcie klienta, gdy mod wraca do wartości domyślnej).
     */
    public void forget(Player player) {
        UUID id = player.getUniqueId();
        nextPlayAt.remove(id);
        lastTrack.remove(id);
        currentTrack.remove(id);
        lastSentVolume.remove(id);
    }

    /** Pętla 4 Hz: wykrywa wejścia/wyjścia ze świata Raft i gra utwory. */
    private void tick() {
        if (!masterEnabled) return;
        String raftWorldName = plugin.getConfig().getString("raft-world-name", "RAFT");
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            boolean inRaft = player.getWorld().getName().equalsIgnoreCase(raftWorldName);

            if (inRaft) {
                // Mod kliencki (raft:musicvol): po wejściu do Rafta aktualizuj
                // korektę głośności (stan ustawienia może się zmienić od wizyty).
                if (id != null && !java.util.Objects.equals(
                        lastSentVolume.get(id), volumeOf(player))) {
                    lastSentVolume.put(id, volumeOf(player));
                    RaftMusicVolumeSender.send(player, volumeOf(player));
                }
                // Częstotliwość „nigdy” wygrywa z wejściem do świata — bez tego
                // computeIfAbsent poniżej zaplanowałby JEDEN utwór po startDelay
                // (zapętlenie w scheduleNext usuwałoby wpis dopiero po graniu).
                if ("nigdy".equals(frequencyOf(player))) {
                    nextPlayAt.remove(id);
                    continue;
                }
                // Wejście do świata (albo świeży start): zaplanuj pierwszy utwór.
                nextPlayAt.computeIfAbsent(id, k -> now() + startDelayMs);
                if (shouldPlayNow(player)) {
                    playOne(player);
                }
            } else {
                // Wyjście ze świata: ucina aktualny utwór i zapomnij stan.
                // UWAGA: oba remove() muszą się wykonać — przy
                // `a != null || b != null` Java ucięłaby drugie wywołanie
                // (short-circuit), więc korekta głośności zostałaby w cache
                // i po powrocie do świata NIE poszłaby ponownie.
                boolean wasTracked = nextPlayAt.remove(id) != null;
                boolean hadVolume = lastSentVolume.remove(id) != null;
                if (wasTracked || hadVolume) {
                    stopFor(player);
                }
            }
        }
    }

    /** Czy gracz ma ustawione „włączona” i przyszedł czas na utwór. */
    private boolean shouldPlayNow(Player player) {
        if (!isEnabled(player)) return false;
        Long at = nextPlayAt.get(player.getUniqueId());
        return at != null && now() >= at;
    }

    /** Gra jeden utwór tylko dla tego gracza i planuje następny.
     *  Przed graniem UCINA bieżące utwory — przy „natychmiastowo” poprzedni
     *  utwór właśnie się skończył (planujemy po jego długości), a przy
     *  zapętleniu/wybranym utworze ucinamy resztkę, żeby playSound nie dołożył
     *  kolejnej instancji tego samego strumienia na stertę (kakofonia). */
    private void playOne(Player player) {
        String track = pickTrack(player);
        if (track == null) {
            scheduleNext(player, null); // brak tracków — po prostu czekaj na kolejny raz
            return;
        }
        stopTracks(player);
        float volume = volumeOf(player);
        // Wyciszenie (0%): utwór I TAK startuje — klient dostaje strumień, ale
        // bezgłośny. Dokładne 0 wanilia traktuje jak PEŁNĄ głośność (znany quirk
        // /playsound), więc idziemy tuż nad zero (0.0001 = niesłyszalne).
        if (volume <= 0f) volume = 0.0001f;
        currentTrack.put(player.getUniqueId(), track);
        player.playSound(player.getLocation(), track,
                SoundCategory.MUSIC, volume, pitchOf(player));
        scheduleNext(player, track);
    }

    /** Wybiera utwór: przy ZAPĘTLENIU zawsze ostatnio grany (a gdy go nie ma —
     *  losuje i zapamiętuje jako „pętlę”); bez zapętlenia losuje z configu,
     *  unikając powtórki ostatniego (przy >1 tracku). */
    private String pickTrack(Player player) {
        if (tracks.isEmpty()) return null;
        // Wybrany utwór (płyta w GUI) = JEDNORAZOWY: gra teraz i RAZ, wybór
        // czyści się natychmiast i wraca normalne odtwarzanie (losowanie albo
        // pętla z cugli — cugle zapętli tę płytę, bo staje się „ostatnio graną”).
        String selected = getSelectedTrack(player);
        if (selected != null) {
            plugin.getPlayerManager().setSetting(player, SETTING_SELECTED, "");
            lastTrack.put(player.getUniqueId(), selected);
            return selected;
        }
        String last = lastTrack.get(player.getUniqueId());
        if (isLoopEnabled(player)) {
            if (last != null && tracks.contains(last)) return last; // ten sam utwór
            String chosen = tracks.get(ThreadLocalRandom.current().nextInt(tracks.size()));
            lastTrack.put(player.getUniqueId(), chosen);
            return chosen;
        }
        if (tracks.size() == 1) return tracks.get(0);
        String chosen;
        do {
            chosen = tracks.get(ThreadLocalRandom.current().nextInt(tracks.size()));
        } while (chosen.equals(last));
        lastTrack.put(player.getUniqueId(), chosen);
        return chosen;
    }

    /** Planuje następny utwór: przerwa z configu × mnożnik częstotliwości gracza.
     *
     *  „natychmiastowo” = utwór gra DO KOŃCA, a następny startuje od razu:
     *  przerwa to długość świeżo puszczonego utworu (TrackDurations) podzielona
     *  przez ton gracza — ton jest tu prędkością (patrz docs/MUZYKA.md), więc
     *  szybszy utwór kończy się wcześniej. Podłoga 3 s chroni przed błędną
     *  długością (0 s = cykl zapętlający się co tick) i jest przerwą, gdy
     *  długości nie znamy.
     *
     *  „nigdy” = nic nie planuj (aktualny utwór ucina dopiero tick przy wyjściu
     *  ze świata albo zmianie ustawienia).
     *
     *  @param track utwór, który właśnie ruszył (null = brak utworów) */
    private void scheduleNext(Player player, String track) {
        UUID id = player.getUniqueId();
        String freq = frequencyOf(player);
        if (freq.equals("nigdy")) {
            nextPlayAt.remove(id);
            return;
        }
        if (freq.equals("natychmiastowo")) {
            long length = track == null ? 3_000L : durations.millis(track);
            long scaled = (long) (length / pitchOf(player));
            nextPlayAt.put(id, now() + Math.max(3_000L, scaled));
            return;
        }
        double multiplier = frequencyMultiplier(player);
        long window = intervalMaxMs - intervalMinMs;
        long gap = intervalMinMs + (window > 0
                ? (long) (ThreadLocalRandom.current().nextDouble() * window) : 0);
        nextPlayAt.put(id, now() + (long) (gap * multiplier));
    }

    /** Czy zapętlenie (powtarzanie ostatniego utworu) jest włączone. */
    public boolean isLoopEnabled(Player player) {
        return "on".equalsIgnoreCase(plugin.getPlayerManager()
                .getSetting(player, SETTING_LOOP, "off"));
    }

    /** Włącza/wyłącza zapętlenie (kolejny utwór = ostatnio grany). */
    public void setLoop(Player player, boolean loop) {
        plugin.getPlayerManager().setSetting(player, SETTING_LOOP, loop ? "on" : "off");
    }

    /** Lista utworów z configu (dla GUI — płyty do wyboru). */
    public java.util.List<String> getTracks() {
        return java.util.Collections.unmodifiableList(tracks);
    }

    /** Utwór wybrany przez gracza (płyta w GUI) albo null = losowanie/pętla. */
    public String getSelectedTrack(Player player) {
        String value = plugin.getPlayerManager()
                .getSetting(player, SETTING_SELECTED, "");
        if (value.isBlank()) return null;
        return tracks.contains(value) ? value : null; // utwór usunięty z configu = brak
    }

    /**
     * Wybiera utwór (klik w płytę w GUI); null = wróć do losowania.
     * Wybór jest JEDNORAZOWY: utwór zagra raz (za ~3 s, chyba że muzyka
     * wyłączona albo częstotliwość = „nigdy”), po czym wraca normalny tryb
     * (losowanie; z włączoną cuglą zapętli się ten utwór).
     */
    public void setSelectedTrack(Player player, String track) {
        plugin.getPlayerManager().setSetting(player, SETTING_SELECTED,
                track == null ? "" : track);
        UUID id = player.getUniqueId();
        if (track != null) {
            // Klik w płytę = wybór NOWEJ muzyki: bieżący utwór cichnie od razu,
            // wybrany zagra za ~3 s (jeśli muzyka w ogóle ma grać).
            stopTracks(player);
            if (masterEnabled && isEnabled(player)
                    && !"nigdy".equals(frequencyOf(player)) && inRaftWorld(player)) {
                nextPlayAt.put(id, now() + 3_000L);
            }
        }
        // track == null (powrót do losowania): bieżący utwór dogrywa do końca.
    }

    /** Ucina utwór gracza (po wyjściu ze świata albo po wyłączeniu muzyki),
     *  zapomina ostatnio grany („pętla” od zera) i to, co grało w tej chwili. */
    private void stopFor(Player player) {
        stopTracks(player);
        lastTrack.remove(player.getUniqueId());
        currentTrack.remove(player.getUniqueId());
    }

    /** Cisza: ucina wszystkie utwory z configu, NIE ruszając pamięci pętli. */
    private void stopTracks(Player player) {
        for (String track : tracks) {
            player.stopSound(track, SoundCategory.MUSIC);
        }
    }

    // ─── Ustawienia per gracz (wywołuje /muzyka) ───

    /** Włącza muzykę: natychmiast planuje utwór (potwierdzenie = usłyszy go za chwilę).
     *  Przy częstotliwości „nigdy” nie planuje nic (jeden tick zdążyłby zagrać
     *  jeden utwór, zanim scheduleNext by go odwołał). */
    public void setEnabled(Player player, boolean enabled) {
        plugin.getPlayerManager().setSetting(player, SETTING_ENABLED,
                enabled ? "on" : "off");
        UUID id = player.getUniqueId();
        if (enabled && masterEnabled && inRaftWorld(player)
                && !"nigdy".equals(frequencyOf(player))) {
            nextPlayAt.put(id, now() + 3_000L);
        } else {
            nextPlayAt.remove(id);
            if (!enabled) stopFor(player);
        }
    }

    /**
     * Głośność (0.0–1.0), per gracz.
     *
     * Gracz Z MODEM: korekta leci kanałem raft:musicvol, a mod podstawia ją
     * w calculateVolume i przelicza grające kanały — zmiana słyszalna NA ŻYWO,
     * bez restartu utworu i dokładnie taka, jak ustawił gracz (kolejne utwory
     * startują już z nową głośnością w pakiecie).
     *
     * Gracz BEZ MODA: waniliowy pakiet dźwięku ma głośność „zapieczętowaną” na
     * starcie utworu, więc nowa głośność działa od NASTĘPNEGO utworu. Wyciszenie
     * (0) UCINA bieżący utwór od razu, ale cykl gra dalej: kolejne utwory
     * startują bezgłośnie (0.0001 — dokładne 0 wanilia liczy jak pełną głośność).
     */
    public void setVolume(Player player, float volume) {
        float clamped = Math.max(0f, Math.min(1f, volume));
        float previous = volumeOf(player);
        if (previous == clamped) return; // bez faktycznej zmiany — nic nie ruszaj
        plugin.getPlayerManager().setSetting(player, SETTING_VOLUME,
                String.valueOf(clamped));

        // Korekta głośności leci do MODA KLIENTA (raft:musicvol): SoundEngineMixin
        // dokłada ją w calculateVolume, a updateCategoryVolume przelicza grające
        // kanały NA ŻYWO — utwór gra dalej bez restartu, efekt słychać natychmiast.
        lastSentVolume.put(player.getUniqueId(), clamped);
        RaftMusicVolumeSender.send(player, clamped);

        if (clamped <= 0f) {
            // Utnij bieżący utwór od razu (natychmiastowa cisza), cykl żyje:
            // z modem kolejne utwory grają bezgłośnie (korekta 0.0 na żywo),
            // bez moda grają z 0.0001 (niesłyszalne). Oba przypadki ciche.
            stopTracks(player);
        }
    }

    /** Ton/prędkość (0.5–2.0) — zadziała od następnego utworu. */
    public void setPitch(Player player, float pitch) {
        plugin.getPlayerManager().setSetting(player, SETTING_PITCH,
                String.valueOf(pitch));
    }

    /** Przesuwa częstotliwość na następną wartość (często → … → nigdy → często). */
    public String cycleFrequency(Player player) {
        String current = frequencyOf(player);
        int index = 0;
        for (int i = 0; i < FREQ_VALUES.length; i++) {
            if (FREQ_VALUES[i].equals(current)) index = i;
        }
        String next = FREQ_VALUES[(index + 1) % FREQ_VALUES.length];
        plugin.getPlayerManager().setSetting(player, SETTING_FREQ, next);
        UUID id = player.getUniqueId();
        if (next.equals("nigdy")) {
            nextPlayAt.remove(id);
            stopFor(player);
        } else {
            nextPlayAt.put(id, now() + 3_000L);
        }
        return next;
    }

    /** Ustawia konkretną częstotliwość (z komendy /muzyka czestotliwosc). */
    public void setFrequency(Player player, String value) {
        String normalized = value.toLowerCase(Locale.ROOT).trim();
        for (String candidate : FREQ_VALUES) {
            if (candidate.equals(normalized)) {
                plugin.getPlayerManager().setSetting(player, SETTING_FREQ, normalized);
                UUID id = player.getUniqueId();
                if (normalized.equals("nigdy")) {
                    nextPlayAt.remove(id);
                    stopFor(player);
                } else if (inRaftWorld(player)) {
                    nextPlayAt.put(id, now() + 3_000L);
                }
                return;
            }
        }
    }

    // ─── Odczyt ustawień (z fallbackiem z configu) ───

    public boolean isEnabled(Player player) {
        return "on".equalsIgnoreCase(plugin.getPlayerManager()
                .getSetting(player, SETTING_ENABLED, "on"));
    }

    /** Głośność gracza (0.0–1.0). Wartości spoza zakresu (ręczna edycja pliku)
     *  ścinamy do 1.0 — pakiet dźwięku i korekta moda liczą i tak tylko 0–1. */
    public float volumeOf(Player player) {
        try {
            return Math.max(0f, Math.min(1f, Float.parseFloat(plugin.getPlayerManager()
                    .getSetting(player, SETTING_VOLUME, String.valueOf(defaultVolume)))));
        } catch (NumberFormatException ignored) {
            return defaultVolume;
        }
    }

    public float pitchOf(Player player) {
        try {
            return Math.max(0.5f, Math.min(2f, Float.parseFloat(plugin.getPlayerManager()
                    .getSetting(player, SETTING_PITCH, String.valueOf(defaultPitch)))));
        } catch (NumberFormatException ignored) {
            return defaultPitch;
        }
    }

    public String frequencyOf(Player player) {
        String value = plugin.getPlayerManager()
                .getSetting(player, SETTING_FREQ, "normalnie");
        for (String candidate : FREQ_VALUES) {
            if (candidate.equals(value)) return candidate;
        }
        return "normalnie";
    }

    private double frequencyMultiplier(Player player) {
        String value = frequencyOf(player);
        for (int i = 0; i < FREQ_VALUES.length; i++) {
            if (FREQ_VALUES[i].equals(value)) return FREQ_MULTIPLIERS[i];
        }
        return 1.0;
    }

    public boolean isMasterEnabled() {
        return masterEnabled;
    }

    private boolean inRaftWorld(Player player) {
        String raftWorldName = plugin.getConfig().getString("raft-world-name", "RAFT");
        return player.getWorld().getName().equalsIgnoreCase(raftWorldName);
    }

    private static long now() {
        return System.currentTimeMillis();
    }
}
