import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.raft.hud.device.DeviceAnimations;
import com.raft.hud.device.DeviceMesh;
import com.raft.hud.device.DeviceMesh.Material;
import com.raft.hud.device.DeviceMesh.Quad;

import java.io.FileReader;
import java.util.ArrayList;

/**
 * Bezgłowy test parsera geometrii urządzeń (DeviceMesh) — bez uruchamiania gry.
 *
 * Sprawdza to, czego nie widać po samym "skompilowało się":
 *   1. winding — czy kolejność wierzchołków daje normalną zgodną z zadeklarowaną
 *      (od tego zależy, czy ściany są widoczne z zewnątrz, a nie od wewnątrz),
 *   2. orientacja tekstury — czy górna krawędź ściany dostaje górny wiersz tekstury (v = minV),
 *      a oś pozioma UV idzie po właściwej osi świata (bez lustra),
 *   3. rotacje elementów — osobny test syntetyczny z policzonymi ręcznie oczekiwaniami,
 *   4. ściany bez tekstury (#missing) i rozmiar modelu.
 *
 * Uruchamianie: tools/check-device-mesh.sh [pliki.json ...]
 */
public final class DeviceMeshCheck {

    private static int problems = 0;

    public static void main(String[] args) throws Exception {
        for (String path : args) {
            checkFile(path);
        }
        checkSyntheticRotation();
        System.out.println();
        System.out.println(problems == 0
                ? "OK — wszystkie sprawdzenia przeszly."
                : "UWAGA — problemow: " + problems);
    }

    private static void checkFile(String path) throws Exception {
        JsonObject json;
        try (FileReader reader = new FileReader(path)) {
            json = JsonParser.parseReader(reader).getAsJsonObject();
        }

        DeviceMesh mesh = DeviceMesh.parse(json);
        float[] b = DeviceMesh.bounds(json);

        System.out.println("=== " + path);
        System.out.println("    sciany: " + mesh.quadCount()
                + ", grupy tekstur: " + mesh.materials().size()
                + ", bez tekstury (#missing): " + mesh.fallbackFaces()
                + (mesh.fallbackTexture() != null ? "  -> " + mesh.fallbackTexture() : ""));
        if (b.length == 6) {
            System.out.printf("    rozmiar: %dx%dx%d px (limit gry -16..32 px = %s)%n",
                    (int) (b[3] - b[0]), (int) (b[4] - b[1]), (int) (b[5] - b[2]),
                    insideVanillaLimit(b) ? "miesci sie" : "PRZEKROCZONY (ta sciezka go nie sprawdza)");
        }

        checkParts(mesh);
        checkAnimation(mesh, path);

        // Heurystyki orientacji UV ("tekstura do góry", "oś pozioma po właściwej osi") zakładają,
        // że ściana jest w świecie pionowa/pozioma. Dla elementów obróconych w Blockbenchu
        // tekstura jest przypisywana w układzie LOKALNYM elementu i razem z nim się obraca —
        // więc pomiar w układzie świata nie ma sensu. Winding sprawdzamy zawsze, bo to
        // niezależny warunek geometryczny, który obrót musi zachować.
        int rotatedElements = countRotatedElements(json);
        boolean orientationChecks = rotatedElements == 0;
        if (!orientationChecks) {
            System.out.println("    orientacja UV: pominięta (" + rotatedElements
                    + " elementow obroconych — UV obraca sie razem z elementem; patrz test syntetyczny)");
        }

        int windings = 0, upright = 0, uAxis = 0, outOfRange = 0, degenerate = 0, rotated = 0;
        int firstPrinted = 0;

        for (Material material : mesh.materials()) {
            for (Quad q : material.quads()) {
                float[] p = q.positions();
                float[] uv = q.uvs();

                for (float v : uv) {
                    if (v < -0.001f || v > 2.0f) outOfRange++;
                }

                // winding: normalna z kolejności wierzchołków musi zgadzać się z zadeklarowaną
                float[] cross = cross(sub(p, 3, 0), sub(p, 6, 0));
                float len = (float) Math.sqrt(cross[0] * cross[0] + cross[1] * cross[1] + cross[2] * cross[2]);
                if (len < 1e-6f) {
                    degenerate++;
                } else if ((cross[0] * q.nx() + cross[1] * q.ny() + cross[2] * q.nz()) / len < 0.9f) {
                    windings++;
                }

                boolean axisAligned = maxAbs(q.nx(), q.ny(), q.nz()) > 0.999f;
                if (!axisAligned) {
                    rotated++;
                    continue;
                }
                if (!orientationChecks) continue;
                if (Math.abs(q.ny()) > 0.5f) continue;   // góra/dół: nie mają "górnej krawędzi"

                int top = 0, minV = 0;
                for (int i = 1; i < 4; i++) {
                    if (p[i * 3 + 1] > p[top * 3 + 1] + 1e-6f) top = i;
                    if (uv[i * 2 + 1] < uv[minV * 2 + 1]) minV = i;
                }
                if (top != minV) {
                    upright++;
                    if (upright <= 3) {
                        System.out.println("    UV: gorny wierzcholek sciany nie ma gornej krawedzi tekstury"
                                + " (v=minV) — tekstura mogłaby być obrócona o 180 stopni.");
                        System.out.println("        " + dump(q));
                    }
                }

                int minU = 0, maxU = 0;
                for (int i = 1; i < 4; i++) {
                    if (uv[i * 2] < uv[minU * 2]) minU = i;
                    if (uv[i * 2] > uv[maxU * 2]) maxU = i;
                }
                // Waniliowo: patrząc na ścianę "prawo" to +u. Dla każdej ściany to inna oś świata.
                boolean ok;
                if (q.nz() < -0.5f) {          // north: u rośnie, gdy x maleje
                    ok = p[maxU * 3] <= p[minU * 3];
                } else if (q.nz() > 0.5f) {    // south: u rośnie z x
                    ok = p[maxU * 3] >= p[minU * 3];
                } else if (q.nx() < -0.5f) {   // west: u rośnie z z
                    ok = p[maxU * 3 + 2] >= p[minU * 3 + 2];
                } else if (q.nx() > 0.5f) {    // east: u rośnie, gdy z maleje
                    ok = p[maxU * 3 + 2] <= p[minU * 3 + 2];
                } else {
                    ok = true;
                }
                if (!ok) {
                    uAxis++;
                    if (uAxis <= 3) {
                        System.out.println("    UV: os pozioma nie idzie po wlasciwej osi swiata");
                        System.out.println("        " + dump(q));
                    }
                }
            }
        }

        problems += windings + upright + uAxis + outOfRange + degenerate;
        System.out.println("    winding zle: " + windings
                + ", tekstura odwrocona: " + upright
                + ", UV zle osiowane: " + uAxis
                + ", UV poza zakresem: " + outOfRange
                + ", zdegenerowane: " + degenerate
                + ", scian obroconych (pominiete): " + rotated);
    }

    /**
     * Syntetyczny test rotacji elementu: sześcian obrócony o 90 stopni wokół Z ma wierzchołki
     * policzone ręcznie, więc niezależnie od modeli z packa sprawdza, czy rotacja, UV i winding
     * są spójne.
     */
    private static void checkSyntheticRotation() {
        System.out.println("=== test syntetyczny: szescian 16x16x16, rotacja z 90 stopni");

        String plain = """
                {"textures":{"t":"raft:block/test"},
                 "elements":[{"from":[0,0,0],"to":[16,16,16],
                   "faces":{"north":{"uv":[0,0,16,16],"texture":"#t"}}}]}
                """;
        // north bez rotacji: v0=(x1,y1,z0)=(16,16,0) v1=(16,0,0) v2=(0,0,0) v3=(0,16,0)
        expectQuad("bez rotacji", plain, 0,
                new float[]{ 16, 16, 0 }, new float[]{ 0, 0 },
                new float[]{ 16, 0, 0 }, new float[]{ 0, 1 },
                new float[]{ 0, 0, 0 }, new float[]{ 1, 1 },
                new float[]{ 0, 16, 0 }, new float[]{ 1, 0 });

        String rotated = """
                {"textures":{"t":"raft:block/test"},
                 "elements":[{"from":[0,0,0],"to":[16,16,16],
                   "rotation":{"axis":"z","angle":90,"origin":[0,0,0]},
                   "faces":{"north":{"uv":[0,0,16,16],"texture":"#t"}}}]}
                """;
        // obrót o +90 wokół Z (prawoskrętny): (x,y,z) -> (-y,x,z)
        expectQuad("rotacja z 90", rotated, 0,
                new float[]{ -16, 16, 0 }, new float[]{ 0, 0 },
                new float[]{ 0, 16, 0 }, new float[]{ 0, 1 },
                new float[]{ 0, 0, 0 }, new float[]{ 1, 1 },
                new float[]{ -16, 0, 0 }, new float[]{ 1, 0 });
    }

    private static String dump(Quad q) {
        StringBuilder sb = new StringBuilder();
        float[] p = q.positions(), uv = q.uvs();
        sb.append(String.format("n=(%.2f,%.2f,%.2f)  ", q.nx(), q.ny(), q.nz()));
        for (int i = 0; i < 4; i++) {
            sb.append(String.format("v%d=(%.2f,%.2f,%.2f) uv=(%.3f,%.3f)  ",
                    i, p[i * 3], p[i * 3 + 1], p[i * 3 + 2], uv[i * 2], uv[i * 2 + 1]));
        }
        return sb.toString();
    }

    private static void expectQuad(String label, String json, int quadIndex,
                                   float[] p0, float[] uv0, float[] p1, float[] uv1,
                                   float[] p2, float[] uv2, float[] p3, float[] uv3) {
        DeviceMesh mesh = DeviceMesh.parse(JsonParser.parseString(json).getAsJsonObject());
        if (mesh.quadCount() != 1) {
            System.out.println("    FAIL " + label + ": oczekiwano 1 sciany, jest " + mesh.quadCount());
            problems++;
            return;
        }
        Quad q = mesh.materials().get(0).quads().get(quadIndex);
        boolean ok = near(q.positions(), 0, p0) && near(q.positions(), 3, p1)
                && near(q.positions(), 6, p2) && near(q.positions(), 9, p3)
                && near(q.uvs(), 0, uv0) && near(q.uvs(), 2, uv1)
                && near(q.uvs(), 4, uv2) && near(q.uvs(), 6, uv3);

        float[] cross = cross(sub(q.positions(), 3, 0), sub(q.positions(), 6, 0));
        float len = (float) Math.sqrt(cross[0] * cross[0] + cross[1] * cross[1] + cross[2] * cross[2]);
        boolean windingOk = len > 1e-6f
                && (cross[0] * q.nx() + cross[1] * q.ny() + cross[2] * q.nz()) / len > 0.9f;

        System.out.println("    " + label + ": " + (ok && windingOk ? "OK" : "FAIL")
                + " (wierzcholki " + (ok ? "zgodne" : "NIEZGODNE")
                + ", winding " + (windingOk ? "ok" : "ZLY") + ")");
        if (!ok || !windingOk) {
            problems++;
            System.out.println("      jest: " + dump(q));
        }
    }

    /**
     * Części modelu (sekcja "groups") — to one są celem animacji.
     *
     * Sprawdzamy przede wszystkim to, że rozbicie na części nic nie zgubiło: suma ścian
     * wszystkich części musi być równa liczbie ścian całego modelu. Bez tego testu zmiana
     * w parserze potrafi cicho zgubić fragment modelu.
     */
    private static void checkParts(DeviceMesh mesh) {
        int sum = 0;
        var names = new ArrayList<String>();
        for (var part : mesh.parts()) sum += collectParts(part, names, "");

        System.out.println("    czesci: " + names.size() + " (" + String.join(", ", names) + ")");
        for (var part : mesh.parts()) printPart(part, 0);

        if (sum != mesh.quadCount()) {
            problems++;
            System.out.println("    BLAD: czesci maja " + sum + " scian, a model " + mesh.quadCount()
                    + " — rozbicie na czesci zgubilo geometrie.");
        }
    }

    private static int collectParts(DeviceMesh.Part part, java.util.List<String> names, String indent) {
        names.add(part.name().isEmpty() ? "(caly model)" : indent + part.name());
        int n = quadCount(part.materials());
        for (var child : part.children()) n += collectParts(child, names, "  ");
        return n;
    }

    private static void printPart(DeviceMesh.Part part, int depth) {
        String pad = "      " + "  ".repeat(depth);
        int quads = quadCount(part.materials());
        System.out.printf("      %s- %-14s scian: %-5d pivot: [%.3f, %.3f, %.3f]%s%n",
                "  ".repeat(depth),
                part.name().isEmpty() ? "(caly model)" : part.name(),
                quads,
                part.pivot()[0], part.pivot()[1], part.pivot()[2],
                part.children().isEmpty() ? "" : "  + " + part.children().size() + " podczesc(i)");
        for (var child : part.children()) printPart(child, depth + 1);
        if (pad.isEmpty()) return;   // nic, tylko dla czytelności
    }

    private static int quadCount(java.util.List<Material> materials) {
        int n = 0;
        for (Material m : materials) n += m.quads().size();
        return n;
    }

    /**
     * Sprawdza animację leżącą obok modelu ("<model>.anim.json") parserem z moda.
     *
     * Trzy rzeczy, których nie widać inaczej niż dopiero w grze:
     *   1. tor wskazuje część, której w modelu NIE MA (literówka = brak ruchu, bez błędu),
     *   2. wartości poza zakresem klatek (interpolacja nie może wyjść z widełek),
     *   3. pętla: wartość na końcu pętli musi zgadzać się z wartością na jej początku.
     */
    private static void checkAnimation(DeviceMesh mesh, String modelPath) {
        String animPath = modelPath.endsWith(".json")
                ? modelPath.substring(0, modelPath.length() - ".json".length()) + ".anim.json"
                : modelPath + ".anim.json";
        java.io.File file = new java.io.File(animPath);
        if (!file.isFile()) {
            System.out.println("    animacja: brak pliku " + animPath + " (urzadzenie bedzie statyczne)");
            return;
        }

        JsonObject json;
        try (FileReader reader = new FileReader(file)) {
            json = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            problems++;
            System.out.println("    BLAD: nie moge wczytac " + animPath + ": " + e);
            return;
        }

        DeviceAnimations anim = DeviceAnimations.parse(json);
        System.out.println("    animacja: " + anim.trackCount() + " torow, dlugosc "
                + (int) anim.length() + " tickow, czesci: "
                + String.join(", ", anim.animatedParts()));

        if (anim.isEmpty()) {
            problems++;
            System.out.println("    BLAD: plik animacji nie ma zadnego poprawnego toru.");
            return;
        }

        for (String part : anim.animatedParts()) {
            if (!mesh.hasPart(part)) {
                problems++;
                System.out.println("    BLAD: tor animuje czesc \"" + part
                        + "\", ktorej nie ma w modelu — ruch nie bedzie widoczny.");
            }
        }

        float length = anim.length();
        float[] io = new float[9];
        float[] atStart = new float[9];
        for (String part : anim.animatedParts()) {
            float minR = Float.MAX_VALUE, maxR = -Float.MAX_VALUE;
            float minS = Float.MAX_VALUE, maxS = -Float.MAX_VALUE;
            float minP = Float.MAX_VALUE, maxP = -Float.MAX_VALUE;
            for (float t = 0f; t <= Math.max(length, 1f) + 0.001f; t += 0.5f) {
                anim.fill(part, t, io);
                for (int i = 0; i < 3; i++) {
                    minR = Math.min(minR, io[i]);
                    maxR = Math.max(maxR, io[i]);
                    minP = Math.min(minP, io[i + 3]);
                    maxP = Math.max(maxP, io[i + 3]);
                    minS = Math.min(minS, io[i + 6]);
                    maxS = Math.max(maxS, io[i + 6]);
                }
            }
            anim.fill(part, 0f, atStart);
            anim.fill(part, length, io);
            boolean loopOk = true;
            if (length > 0f) {
                // Koniec pętli musi wracać do początku — inaczej animacja "skacze" co cykl.
                for (int i = 0; i < 9; i++) {
                    if (Math.abs(io[i] - atStart[i]) > 0.01f) loopOk = false;
                }
            }
            // Wartość w 1/4 pętli — jedna liczba, którą można porównać z podglądem w edytorze.
            // To najprostszy sposób wyłapania różnicy w interpolacji albo w obsłudze pętli
            // między edytorem a modem (obie strony liczą to samo, więc liczby muszą się zgadzać).
            float quarter = length > 0f ? length / 4f : 0f;
            anim.fill(part, quarter, io);
            float quarterY = io[1];

            System.out.printf("      - %-14s obrot %6.1f..%6.1f st | pozycja %5.2f..%5.2f px | skala %5.3f..%5.3f | 1/4 petli: Y=%6.1f | petla: %s%n",
                    part, minR, maxR, minP, maxP, minS, maxS, quarterY,
                    length > 0f ? (loopOk ? "domyka sie" : "NIE DOMYKA SIE") : "brak");
            if (length > 0f && !loopOk) {
                problems++;
                System.out.println("        UWAGA: koniec petli nie wraca do wartosci poczatkowej"
                        + " — animacja bedzie skakac co cykl.");
            }
        }
    }

    /** Ile elementów ma realny obrót (oś + kąt różny od zera). */
    private static int countRotatedElements(JsonObject json) {
        var elements = json.getAsJsonArray("elements");
        if (elements == null) return 0;
        int n = 0;
        for (var el : elements) {
            JsonObject rot = el.getAsJsonObject().getAsJsonObject("rotation");
            if (rot == null || !rot.has("axis")) continue;
            if (rot.has("angle") && Math.abs(rot.get("angle").getAsFloat()) > 1e-4f) n++;
        }
        return n;
    }

    private static boolean near(float[] arr, int offset, float[] expected) {
        for (int i = 0; i < expected.length; i++) {
            if (Math.abs(arr[offset + i] - expected[i]) > 1e-4f) return false;
        }
        return true;
    }

    private static float maxAbs(float a, float b, float c) {
        return Math.max(Math.abs(a), Math.max(Math.abs(b), Math.abs(c)));
    }

    private static boolean insideVanillaLimit(float[] b) {
        return b[0] >= -16f && b[1] >= -16f && b[2] >= -16f
                && b[3] <= 32f && b[4] <= 32f && b[5] <= 32f;
    }

    private static float[] sub(float[] p, int a, int b) {
        return new float[]{ p[a] - p[b], p[a + 1] - p[b + 1], p[a + 2] - p[b + 2] };
    }

    private static float[] cross(float[] a, float[] b) {
        return new float[]{
                a[1] * b[2] - a[2] * b[1],
                a[2] * b[0] - a[0] * b[2],
                a[0] * b[1] - a[1] * b[0]
        };
    }
}
