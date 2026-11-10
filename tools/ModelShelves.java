import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wypisuje PÓŁKI modelu — poziome powierzchnie, na których można stanąć. To one są stopniami
 * schodów: z modelu wynikają pozycje shulkerów-kolizji (niewidzialne pudełka), więc nic nie trzeba
 * dopasowywać ręcznie ani sprawdzać w grze.
 *
 * Skąd bierze półkę: element modelu jest bryłą, a jego górna ściana (`up`) to powierzchnia
 * pozioma. Dla każdej takiej bryły narzędzie liczy:
 *   - wysokość górnej powierzchni w blokach (16 px = 1 blok, y=0 modelu = spód bloku-nośnika),
 *   - rozmiar w poziomie (czy da się na tym stanąć — potrzebny sensowny kawałek, nie 1 px),
 *   - czy półka mieści się w jednej komórce (x/z 0..16 px), bo jedna komórka tratwy = jedna para
 *     shulkerów (mogą być dwie w komórce, ale nie mogą wychodzić do sąsiada),
 *   - gdzie postawić shulkera, żeby jego pudełko kończyło się DOKŁADNIE w tej półce:
 *     encja stoi w (środek półki, góra półki − skala), bo zamknięty shulker ma pudełko
 *     `skala × skala × skala` liczone OD POZYCJI W GÓRĘ i wyśrodkowane w poziomie.
 *
 * Na końcu wypisuje RYTM: różnice wysokości między kolejnymi półkami. Gracz wchodzi bez skakania
 * na 0,6 bloka, a skok sięga ~1,25 — więc stopień, który ma wymusić skok, musi być wyżej o więcej
 * niż 0,6 i nie wyżej niż ~1,0.
 *
 * Użycie (z katalogu projektu):
 *   tools/model-shelves.sh <model.json|model.bbmodel> [skala shulkera = 0.5]
 * albo wprost:
 *   java tools/ModelShelves.java pack/assets/raft/rafthud/antena.json 0.5
 */
public final class ModelShelves {

    /** Jednostka modelu: 16 px = 1 blok (tak samo liczy mod, DevicePartRenderer.PX). */
    private static final double PX = 1.0 / 16.0;

    /** Ile bloków gracz przechodzi bez skakania (maxUpStep gracza) i jak wysoko sięga skok. */
    private static final double STEP_UP = 0.6, JUMP_REACH = 1.25;

    /** Jedna pozioma powierzchnia (górna ściana bryły) w pikselach modelu. */
    private record Shelf(String part, String element, double topY, double x0, double x1, double z0, double z1,
                         boolean hasUp, boolean flat) {

        double width() { return x1 - x0; }
        double depth() { return z1 - z0; }
        double centerX() { return (x0 + x1) / 2.0; }
        double centerZ() { return (z0 + z1) / 2.0; }
        boolean insideCell() { return x0 >= -0.001 && x1 <= 16.001 && z0 >= -0.001 && z1 <= 16.001; }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Uzycie: java tools/ModelShelves.java <model.json|model.bbmodel> [skala = 0.5]");
            return;
        }
        Path path = Path.of(args[0]);
        double scale = args.length > 1 ? Double.parseDouble(args[1]) : 0.5;

        JsonObject json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        boolean bbmodel = json.has("meta");
        Map<Integer, String> partOf = partOfElement(json, bbmodel);

        JsonArray elements = json.getAsJsonArray("elements");
        if (elements == null) {
            System.out.println("Model nie ma sekcji elements: " + path);
            return;
        }

        System.out.println("=== " + path.getFileName() + (bbmodel ? "  (zrodlo Blockbencna)" : "  (eksport Java)"));
        System.out.println("    elementow: " + elements.size() + ", skala shulkera: " + scale);

        List<Shelf> shelves = new ArrayList<>();
        for (int i = 0; i < elements.size(); i++) {
            JsonObject e = elements.get(i).getAsJsonObject();
            JsonObject faces = e.getAsJsonObject("faces");
            if (faces == null) continue;
            double[] from = xyz(e.getAsJsonArray("from"));
            double[] to = xyz(e.getAsJsonArray("to"));
            // Element obrócony ma ściany w innym układzie — wysokość liczymy tylko dla prostych brył.
            boolean flat = !e.has("rotation");
            shelves.add(new Shelf(partOf.getOrDefault(i, ""),
                    e.has("name") ? e.get("name").getAsString() : ("element " + i),
                    to[1], from[0], to[0], from[2], to[2], faces.has("up"), flat));
        }
        shelves.sort(Comparator.comparingDouble(Shelf::topY));

        System.out.println();
        System.out.printf("    %8s %8s  %-44s %-13s %-13s %s%n",
                "gora px", "bloki", "part / element", "rozmiar", "srodek XZ", "shulker x y z");
        for (Shelf s : shelves) {
            String part = s.part().isEmpty() ? "-" : s.part();
            String note = !s.flat() ? "  (obrocony — policz recznie)"
                    : !s.hasUp() ? "  (bez gornej sciany)"
                    : !s.insideCell() ? "  (wychodzi za komorke)"
                    : (s.width() < 8.0 || s.depth() < 8.0) ? "  (ponizej pol bloku — trudno stac)" : "";
            System.out.printf("    %8.2f %8.3f  %-44s %-13s %-13s (%.3f, %.3f, %.3f)%s%n",
                    s.topY(), s.topY() * PX, shorten(part + " / " + s.element(), 44),
                    fmt(s.width() * PX) + " x " + fmt(s.depth() * PX),
                    fmt(s.centerX() * PX) + ", " + fmt(s.centerZ() * PX),
                    s.centerX() * PX, s.topY() * PX - scale, s.centerZ() * PX, note);
        }

        System.out.println();
        System.out.println("    rytm (roznice wysokosci miedzy kolejnymi polkami):");
        double prev = Double.NaN;
        for (Shelf s : shelves) {
            double top = s.topY() * PX;
            if (!Double.isNaN(prev)) {
                double d = top - prev;
                String verdict = d <= 0.001 ? "ta sama wysokosc (polka dzielona)"
                        : d <= STEP_UP ? "gracz WEJDZIE bez skoku"
                        : d <= JUMP_REACH ? "skok — ok"
                        : "za wysoko na jeden skok (potrzebny stopien posredni)";
                System.out.printf("      +%.3f bloka  (%s -> %s)  %s%n", d, fmt(prev), fmt(top), verdict);
            }
            prev = top;
        }
    }

    /**
     * Przypisanie element -> grupa (part).
     *
     * Eksport Java trzyma w grupach indeksy elementów, a bbmodel — ich UUID, więc dla bbmodel
     * najpierw budujemy mapę uuid → indeks (inaczej nazwy części wyszłyby puste).
     */
    private static Map<Integer, String> partOfElement(JsonObject json, boolean bbmodel) {
        Map<Integer, String> out = new LinkedHashMap<>();
        Map<String, Integer> uuidToIndex = new LinkedHashMap<>();
        if (bbmodel) {
            JsonArray elements = json.getAsJsonArray("elements");
            for (int i = 0; elements != null && i < elements.size(); i++) {
                JsonObject e = elements.get(i).getAsJsonObject();
                if (e.has("uuid")) uuidToIndex.put(e.get("uuid").getAsString(), i);
            }
        }
        JsonArray groups = json.getAsJsonArray(bbmodel ? "outliner" : "groups");
        if (groups != null) walkGroups(groups, "", out, uuidToIndex);
        return out;
    }

    private static void walkGroups(JsonArray groups, String parent, Map<Integer, String> out,
                                   Map<String, Integer> uuidToIndex) {
        for (JsonElement g : groups) {
            if (!g.isJsonObject()) continue;
            JsonObject group = g.getAsJsonObject();
            String name = group.has("name") ? group.get("name").getAsString() : "";
            String path = parent.isEmpty() ? name : parent + "/" + name;
            JsonArray children = group.getAsJsonArray("children");
            if (children == null) continue;

            JsonArray subGroups = new JsonArray();
            for (JsonElement c : children) {
                if (c.isJsonPrimitive() && c.getAsJsonPrimitive().isString()) {
                    Integer index = uuidToIndex.get(c.getAsString());     // bbmodel: element po UUID
                    if (index != null) out.put(index, path);
                } else if (c.isJsonPrimitive() && c.getAsJsonPrimitive().isNumber()) {
                    out.put(c.getAsInt(), path);                         // eksport Java: indeks elementu
                } else if (c.isJsonObject()) {
                    subGroups.add(c.getAsJsonObject());                   // podgrupa — niżej
                }
            }
            if (!subGroups.isEmpty()) walkGroups(subGroups, path, out, uuidToIndex);
        }
    }

    private static double[] xyz(JsonArray arr) {
        double[] out = new double[3];
        for (int i = 0; i < 3 && arr != null && i < arr.size(); i++) out[i] = arr.get(i).getAsDouble();
        return out;
    }

    private static String shorten(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    private static String fmt(double v) {
        return String.format("%.3f", v);
    }
}
