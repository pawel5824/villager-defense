import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Szuka literałów tekstowych w klasach JAR-a bez dekompilacji — czyli odpowiada na pytanie
 * „czy ta wersja Minecrafta w ogóle zna ten klucz JSON / tę nazwę typu”, zamiast zgadywać.
 *
 * Użycie:
 *   java -cp tools/out-jarstrings JarStrings &lt;jar&gt; &lt;literal&gt; [&lt;literal&gt; ...]
 *   java -cp tools/out-jarstrings JarStrings --pack &lt;jar&gt; &lt;plik.json&gt; ...
 *
 * Tryb --pack czyta z plików JSON wszystkie literały w cudzysłowach (klucze, nazwy typów,
 * identyfikatory) i raportuje, których dana wersja klienta nie zna — najszybsza odpowiedź
 * na pytanie „czy ta paczka nie używa funkcji nowszego formatu”. Dla identyfikatorów z
 * namespace (np. {@code minecraft:range_dispatch}) sprawdza też wariant bez prefiksu,
 * bo wanilia buduje nazwy typów przez {@code Identifier.withDefaultNamespace(...)}.
 *
 * Kody wyjścia: 0 = wszystko znane, 1 = czegoś brakuje (tylko w trybie --pack).
 */
public final class JarStrings {

    /** Ile klas pokazać przy trafieniu (dłuższe listy tylko zaśmiecają). */
    private static final int MAX_CLASSES = 12;
    /** Ile „luźnych” trafień (podciąg zamiast dokładnego literału) pokazać. */
    private static final int MAX_LOOSE = 12;

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Uzycie: JarStrings <jar> <literal>... | JarStrings --pack <jar> <plik.json>...");
            System.exit(2);
        }
        boolean packMode = "--pack".equals(args[0]);
        int first = packMode ? 2 : 1;
        String jarPath = args[first - 1];
        Set<String> wanted = new LinkedHashSet<>();
        for (int i = first; i < args.length; i++) {
            if (packMode) {
                wanted.addAll(literalsOfJson(Files.readString(Path.of(args[i]))));
            } else {
                wanted.add(args[i]);
            }
        }

        Map<String, List<String>> exact = new LinkedHashMap<>();
        Map<String, List<String>> loose = new LinkedHashMap<>();
        for (String literal : wanted) {
            exact.put(literal, new ArrayList<>());
            loose.put(literal, new ArrayList<>());
        }

        int classes = 0;
        Set<String> pool = new LinkedHashSet<>();
        try (ZipFile zip = new ZipFile(jarPath)) {
            for (Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements(); ) {
                ZipEntry entry = e.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) continue;
                Set<String> strings = new LinkedHashSet<>();
                try (InputStream in = zip.getInputStream(entry)) {
                    collectStrings(in, strings);
                }
                if (strings.isEmpty()) continue;
                classes++;
                pool.addAll(strings);
                String className = entry.getName().replace('/', '.').replaceAll("\\.class$", "");
                for (String literal : wanted) {
                    if (strings.contains(literal) || strings.contains(stripped(literal))) {
                        add(exact.get(literal), className, MAX_CLASSES);
                    } else {
                        for (String s : strings) {
                            if (s.contains(literal)) {
                                add(loose.get(literal), className + " \"" + s + "\"", MAX_LOOSE);
                                break;
                            }
                        }
                    }
                }
            }
        }

        System.out.println("JAR: " + jarPath);
        System.out.println("Klas: " + classes + ", literałów w puli: " + pool.size());
        int missing = 0;
        for (String literal : wanted) {
            List<String> hits = exact.get(literal);
            if (!hits.isEmpty()) {
                System.out.println("  jest   " + literal + "  -> " + String.join(", ", hits)
                        + (strippedKey(literal) ? "  [wariant bez prefiksu]" : ""));
            } else if (!loose.get(literal).isEmpty()) {
                System.out.println("  ~podob " + literal + "  -> " + String.join(", ", loose.get(literal)));
                missing++;
            } else {
                System.out.println("  BRAK   " + literal);
                missing++;
            }
        }
        if (packMode) {
            System.out.println(missing == 0
                    ? "OK — kazdy literal z paczki wystepuje w klasach tej wersji."
                    : "BRAKI: " + missing + " (typy rejestrowane przez mod sa oczekiwane tutaj)");
            if (missing > 0) System.exit(1);
        }
    }

    private static void add(List<String> list, String value, int max) {
        if (list.size() < max && !list.contains(value)) list.add(value);
    }

    /** {@code minecraft:range_dispatch} -> {@code range_dispatch} (tak wanilia buduje nazwy typów). */
    private static String stripped(String literal) {
        int colon = literal.indexOf(':');
        return colon < 0 ? literal : literal.substring(colon + 1);
    }

    private static boolean strippedKey(String literal) {
        return literal.indexOf(':') >= 0;
    }

    /** Literały w cudzysłowach z pliku JSON (klucze i wartości), bez duplikatów. */
    private static Set<String> literalsOfJson(String json) {
        Set<String> out = new LinkedHashSet<>();
        Matcher m = Pattern.compile("\"([^\"]+)\"").matcher(json);
        while (m.find()) {
            String value = m.group(1);
            if (!value.isBlank()) out.add(value);
        }
        return out;
    }

    /** Wszystkie stałe CONSTANT_Utf8 z class file. */
    private static void collectStrings(InputStream raw, Set<String> out) throws IOException {
        DataInputStream in = new DataInputStream(raw);
        if (in.readInt() != 0xCAFEBABE) return;
        in.readUnsignedShort(); // minor
        in.readUnsignedShort(); // major
        int poolCount = in.readUnsignedShort();
        String[] entries = new String[poolCount];
        for (int i = 1; i < poolCount; i++) {
            int tag = in.readUnsignedByte();
            switch (tag) {
                case 1 -> entries[i] = in.readUTF();
                case 7, 8, 16, 19, 20 -> in.skipBytes(2);
                case 15 -> in.skipBytes(3);
                case 3, 4, 9, 10, 11, 12, 17, 18 -> in.skipBytes(4);
                case 5, 6 -> { in.skipBytes(8); i++; }
                default -> throw new IOException("Nieznany tag constant pool: " + tag);
            }
        }
        for (int i = 1; i < poolCount; i++) {
            if (entries[i] != null && !entries[i].isBlank()) out.add(entries[i]);
        }
    }
}
