package pl.vd.raft.music;

import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Długości utworów (ms) — potrzebne trybowi „natychmiastowo", żeby utwór
 * zagrał DO KOŃCA, a następny ruszył od razu.
 *
 * Skąd serwer ma długość: pakiet dźwięku niesie tylko identyfikator, głośność
 * i ton — czas trwania zna wyłącznie klient (i to dopiero po pobraniu pliku
 * z paczki). Dlatego długość ustalamy po stronie serwera, w kolejności:
 *
 *   1) {@code raft-music.track-seconds.<id>} — ręczne nadpisanie (sekundy),
 *   2) plik .ogg z {@code raft-music.pack-dir} — dokładnie, z nagłówka Vorbisa;
 *      pliku szukamy TAK JAK KLIENT: najpierw {@code assets/<ns>/sounds.json},
 *      dopiero z wpisu „name" wynika ścieżka pliku,
 *   3) {@code raft-music.default-track-seconds} — gdy nie znamy utworu.
 *
 * Wartości z plików liczymy raz i trzymamy w pamięci (pytanie pada raz na
 * utwór, a pliki paczki zmieniają się rzadko); {@code /def reload} czyści cache.
 *
 * Cisza: brak paczki, brak pliku, obcy format albo błąd odczytu = po prostu
 * wartość domyślna, bez komunikatu w logu.
 */
public final class TrackDurations {

    /** Ręczne długości z configu (id → ms) — wygrywają ze wszystkim. */
    private final Map<String, Long> overrides = new HashMap<>();
    /** Długości wyliczone z plików paczki (id → ms; -1 = nie udało się). */
    private final Map<String, Long> fromPack = new HashMap<>();

    /** Katalog paczki (pusty = nie czytamy plików). */
    private String packDir = "";
    /** Długość przyjmowana dla nieznanych utworów (ms). */
    private long fallbackMs = 180_000L;

    /** Wczytuje ustawienia z sekcji raft-music (start pluginu i /def reload). */
    public void loadConfig(ConfigurationSection music) {
        overrides.clear();
        fromPack.clear();
        String dir = music.getString("pack-dir", "");
        packDir = dir == null ? "" : dir.trim();
        fallbackMs = Math.max(3_000L,
                music.getLong("default-track-seconds", 180L) * 1000L);

        ConfigurationSection seconds = music.getConfigurationSection("track-seconds");
        if (seconds == null) return;
        // UWAGA: Bukkit rozbija klucze z kropką na sekcje (id utworu ma kropkę:
        // „raft:music.utwor_1”), więc bierzemy pełne ścieżki liści — getKeys(true)
        // składa je z powrotem dokładnie w oryginalne id.
        for (String id : seconds.getKeys(true)) {
            long value = seconds.getLong(id, -1L);
            if (value > 0) overrides.put(id, value * 1000L);
        }
    }

    /**
     * Długość utworu w ms: nadpisanie z configu → plik z paczki → domyślna.
     * Zawsze zwraca sensowną wartość (min. 3 s) — nigdy nie rzuca wyjątku.
     */
    public long millis(String trackId) {
        if (trackId == null) return fallbackMs;
        Long manual = overrides.get(trackId);
        if (manual != null) return manual;
        Long cached = fromPack.get(trackId);
        if (cached == null) {
            cached = readFromPack(trackId);
            fromPack.put(trackId, cached);
        }
        return cached > 0L ? cached : fallbackMs;
    }

    /** Długość z plików paczki: sounds.json wskazuje plik .ogg dla tego id. */
    private long readFromPack(String trackId) {
        if (packDir.isBlank()) return -1L;
        int colon = trackId.indexOf(':');
        if (colon <= 0 || colon == trackId.length() - 1) return -1L;
        String namespace = trackId.substring(0, colon);
        String eventKey = trackId.substring(colon + 1);

        File soundsJson = new File(packDir, "assets/" + namespace + "/sounds.json");
        if (!soundsJson.isFile()) return -1L;
        try (FileInputStream in = new FileInputStream(soundsJson)) {
            // Surowy snakeyaml (jest na serwerze razem z Bukkitem) — NIE
            // YamlConfiguration, bo ta rozbija klucze z kropką („music.utwor_1”)
            // na zagnieżdżone sekcje i zdarzenia nie da się odnaleźć.
            Object root = new org.yaml.snakeyaml.Yaml().load(in);
            if (!(root instanceof Map<?, ?> events)) return -1L;
            Object node = events.get(eventKey);
            if (!(node instanceof Map<?, ?> event)) return -1L;
            Object sounds = event.get("sounds");
            if (!(sounds instanceof List<?> list)) return -1L;
            for (Object entry : list) {
                if (!(entry instanceof Map<?, ?> sound)) continue;
                Object name = sound.get("name");
                if (name == null) continue;
                File file = soundFile(String.valueOf(name));
                if (file == null) continue;
                long ms = vorbisMillis(file);
                if (ms > 0L) return ms;
            }
        } catch (Exception ignored) {
            // Nieczytelna paczka / inny format — zostaje wartość domyślna.
        }
        return -1L;
    }

    /** {@code "raft:music/utwor_1"} → {@code <pack-dir>/assets/raft/sounds/music/utwor_1.ogg} */
    private File soundFile(String name) {
        int colon = name.indexOf(':');
        if (colon <= 0 || colon == name.length() - 1) return null;
        String namespace = name.substring(0, colon);
        String path = name.substring(colon + 1);
        if (path.isBlank()) return null;
        return new File(packDir, "assets/" + namespace + "/sounds/" + path + ".ogg");
    }

    /**
     * Długość pliku OGG Vorbis w ms (-1 = nie udało się odczytać).
     *
     * Vorbis nie ma w nagłówku „ile to trwa" — czas wynika z liczby próbek:
     * ostatnia strona Ogg niesie granule position = liczbę próbek całego
     * strumienia, a sample rate siedzi w nagłówku identyfikacji
     * (0x01 „vorbis", wersja 4 B, kanały 1 B, sample rate 4 B). Dlatego:
     * czytamy początek (sample rate) i ogon pliku (ostatni granule).
     */
    private static long vorbisMillis(File file) {
        if (file == null || !file.isFile()) return -1L;
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long length = raf.length();
            if (length < 32L) return -1L;
            int sampleRate = readSampleRate(raf, length);
            if (sampleRate <= 0) return -1L;
            long granule = readLastGranule(raf, length);
            if (granule <= 0L) return -1L;
            return granule * 1000L / sampleRate;
        } catch (IOException ignored) {
            return -1L;
        }
    }

    /** Sample rate z nagłówka identyfikacji Vorbisa (pierwsze 4 KB pliku). */
    private static int readSampleRate(RandomAccessFile raf, long length) throws IOException {
        int window = (int) Math.min(length, 4096L);
        byte[] head = new byte[window];
        raf.seek(0L);
        raf.readFully(head);
        for (int i = 0; i + 16 <= window; i++) {
            if (head[i] != 1) continue;
            if (head[i + 1] != 'v' || head[i + 2] != 'o' || head[i + 3] != 'r'
                    || head[i + 4] != 'b' || head[i + 5] != 'i' || head[i + 6] != 's') continue;
            int rate = (head[i + 12] & 0xFF) | ((head[i + 13] & 0xFF) << 8)
                    | ((head[i + 14] & 0xFF) << 16) | ((head[i + 15] & 0xFF) << 24);
            return rate > 0 ? rate : -1;
        }
        return -1;
    }

    /** Granule ostatniej strony Ogg = liczba próbek (skan od końca pliku). */
    private static long readLastGranule(RandomAccessFile raf, long length) throws IOException {
        final int chunk = 64 * 1024;
        long end = length;
        while (end > 0L) {
            int size = (int) Math.min((long) chunk, end);
            long start = end - size;
            byte[] buf = new byte[size];
            raf.seek(start);
            raf.readFully(buf);
            for (int i = size - 4; i >= 0; i--) {
                if (buf[i] != 'O' || buf[i + 1] != 'g' || buf[i + 2] != 'g' || buf[i + 3] != 'S') {
                    continue;
                }
                if (i + 14 > size) continue;                 // granule nie mieści się w oknie
                long granule = readLongLE(buf, i + 6);       // nagłówek strony: 4B "OggS" + wersja + typ
                if (granule > 0L) return granule;            // granule -1 = strona bez liczby próbek
            }
            end = start + 3L;                                // zachodzimy na poprzedni blok
        }
        return -1L;
    }

    private static long readLongLE(byte[] buf, int offset) {
        if (offset < 0 || offset + 8 > buf.length) return -1L;
        long value = 0L;
        for (int i = 7; i >= 0; i--) {
            value = (value << 8) | (buf[offset + i] & 0xFFL);
        }
        return value;
    }
}
