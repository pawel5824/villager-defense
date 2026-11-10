import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Bezgłowy test podpięcia PRZEDMIOTÓW URZĄDZEŃ — bez uruchamiania gry.
 *
 * Kontrakt (zmieniony 2026-10-01): wpisy 4002/4004/4005 w heavy_core.json to ZWYKŁE modele
 * elementowe (*_fallback.json), żeby przedmioty miały wygląd także u graczy BEZ modu. Wcześniej
 * były typu minecraft:special + raft:device (mod musiał je rejestrować), a u gracza bez modu
 * dawały brakujący model (szachownicę).
 *
 * Sprawdzane rzeczy:
 *   1. item definition istnieje i ŻADEN wpis nie używa typu z namespace 'raft' (typ z modu
 *      odrzuciłby CAŁY plik u gracza bez modu — dokładnie dlatego nie może tu być raft:device),
 *   2. dla każdego pilnowanego CMD jest wpis, jego model ma typ minecraft:model i wskazany plik
 *      modelu istnieje,
 *   3. model fallback ma sekcję elements (to zwykły model, nie „base” bez geometrii),
 *   4. każda ściana ma teksturę (bez #missing) i każda tekstura ma plik PNG w packu,
 *   5. każde UV jest oknem W ŚRODKU swojego sprite'a (0..16) i ma proporcje ściany — UV
 *      wychodzące poza 0..16 nie powtarza tekstury, tylko pokazuje sąsiedni sprite z atlasu,
 *   6. overlaye z pack.mcmeta (dla graczy bez modu) nadal nie używają typów z modu,
 *   7. jeśli model/tekstura ma kopię w jarze moda (pack/src/main/resources), jest identyczna.
 *
 * Geometria urządzeń W ŚWIECIE i tak rysuje mod (DeviceBlockEntityRenderer na bloku-nośniku) —
 * te modele widać tylko w ręce i w ekwipunku; siatki raft:rafthud/* pilnuje tools/check-device-mesh.sh.
 *
 * Uruchamianie: tools/check-item-model.sh
 */
public final class ItemModelCheck {

    /** Katalogi packa do sprawdzenia (pack roboczy + kopia w modpacku — muszą być zgodne). */
    private static final List<String> PACKS = List.of(
            "pack/assets",
            "modpack/resourcepacks/raft-hud/assets"
    );

    /**
     * Przedmiot pilnowany per CMD: przedmiot + CMD + nazwa + OCZEKIWANY model.
     * Literówka w oczekiwanym modelu wywali test — to celowe: zmiana modelu pod CMD powinna
     * być świadomą edycją tej tabeli, nie po cichu przelatującym testem.
     */
    private record Device(String item, int cmd, String label, String model) { }

    /** Kolejnosc scian jak w plikach modeli. */
    private static final String[] FACES = { "north", "south", "east", "west", "up", "down" };

    private static final List<Device> DEVICES = List.of(
            new Device("heavy_core", 4002, "odbiornik", "raft:item/odbiornik_fallback"),
            new Device("heavy_core", 4004, "antena", "raft:item/antena_fallback"),
            new Device("heavy_core", 4005, "żagiel", "raft:item/zagiel_fallback")
    );

    private static int problems = 0;
    private static int warnings = 0;

    public static void main(String[] args) throws Exception {
        for (String pack : PACKS) {
            System.out.println("=== " + pack);
            checkPack(pack);
        }
        System.out.println(problems == 0
                ? "OK — wszystkie sprawdzenia przeszly." + (warnings > 0 ? " (uwag: " + warnings + ")" : "")
                : "PROBLEMY: " + problems + (warnings > 0 ? ", uwag: " + warnings : ""));
        if (problems > 0) System.exit(1);
    }

    private static void checkPack(String pack) throws Exception {
        for (Device device : DEVICES) {
            System.out.println("  -- " + device.label() + " (CMD " + device.cmd() + ", "
                    + device.item() + ")");
            checkDevice(pack, device);
        }
        checkDefinitionNoModTypes(pack);
        checkOverlays(pack);
    }

    private static void checkDevice(String pack, Device device) throws Exception {
        Path definitionPath = Path.of(pack, "minecraft", "items", device.item() + ".json");
        if (!Files.isRegularFile(definitionPath)) {
            check(false, "item definition istnieje: " + definitionPath);
            return;
        }
        JsonObject itemDefinition = read(definitionPath.toString());
        JsonObject entry = entryFor(itemDefinition, device.cmd());
        check(entry != null, "jest wpis dla CMD " + device.cmd());
        if (entry == null) return;

        JsonObject model = entry.getAsJsonObject("model");
        check(model != null && "minecraft:model".equals(str(model, "type")),
                "typ modelu = minecraft:model (bez modu)");
        String modelId = str(model, "model");
        check(device.model().equals(modelId),
                "model = " + device.model() + (modelId == null ? "" : " (jest: " + modelId + ")"));
        if (!device.model().equals(modelId)) return;

        Path modelPath = modelPath(pack, modelId);
        check(Files.isRegularFile(modelPath), "model istnieje: " + modelPath);
        if (!Files.isRegularFile(modelPath)) return;

        JsonObject modelJson = read(modelPath.toString());
        check(modelJson.has("elements"), "model ma elements (zwykly model elementowy)");

        JsonArray elements = modelJson.getAsJsonArray("elements");
        int missing = 0;
        if (elements != null) {
            for (var element : elements) {
                JsonObject faces = element.getAsJsonObject().getAsJsonObject("faces");
                if (faces == null) continue;
                for (String face : FACES) {
                    JsonObject f = faces.getAsJsonObject(face);
                    if (f != null && "#missing".equals(str(f, "texture"))) missing++;
                }
            }
        }
        check(missing == 0, "zadna sciana nie ma #missing (brakujacych: " + missing + ")");

        JsonObject textures = modelJson.getAsJsonObject("textures");
        if (textures != null) {
            for (var e : textures.entrySet()) {
                String id = e.getValue().getAsString();
                if (id.startsWith("#")) continue; // wewnętrzne odwołanie
                String[] parts = split(id);
                Path texturePath = Path.of(pack, parts[0], "textures", parts[1] + ".png");
                check(Files.isRegularFile(texturePath), "tekstura istnieje: " + texturePath);
                if (Files.isRegularFile(texturePath)) checkBuiltinCopy(texturePath);
            }
        }

        checkUv(modelJson, FACES);
        checkBuiltinCopy(modelPath);
    }

    /**
     * CAŁA definicja przedmiotu musi być wolna od typów z modu — jeden wpis z raft:* odrzuca
     * u gracza bez modu CAŁY plik (przepadają też wpisy obok, tak działo się z radiem, zanim
     * doszedł overlay). Sprawdzane jest każdy plik items/*.json w packu, nie tylko heavy_core.
     */
    private static void checkDefinitionNoModTypes(String pack) throws Exception {
        Path itemsDir = Path.of(pack, "minecraft", "items");
        if (!Files.isDirectory(itemsDir)) return;
        try (var files = Files.list(itemsDir)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                String modType = modRegisteredType(read(file.toString()), "");
                check(modType == null, "definicja nie uzywa typu z moda: " + file.getFileName()
                        + (modType == null ? "" : " (znaleziono " + modType + ")"));
            }
        }
    }

    /**
     * UV każdej ściany: okno w środku sprite'a (0..16) o proporcjach ściany.
     *
     * Po co: UV wychodzące poza 0..16 to najczęstszy cichy błąd modelu większego niż jedna
     * tekstura — atlas NIE powtarza sprite'a, więc taka ściana pokazuje sąsiedni sprite.
     * Drugi warunek (te same proporcje UV i ściany) łapie niejednorodne rozciągnięcie:
     * tekstura rozjechana na jednej osi wygląda w grze jak rozmazany pas.
     *
     * Tolerancja: 2% + błąd siatki UV. UV zapisuje się z dokładnością 0,01, więc na ścianie
     * grubości 0,07 px samo zaokrąglenie brzegów zmienia proporcję o 14% — na takiej ścianie
     * (cieńszej niż piksel tekstury) nie da się zmierzyć rozciągnięcia i nie ma to sensu.
     */
    private static void checkUv(JsonObject mesh, String[] faces) {
        int outside = 0, skewed = 0, walls = 0;
        for (var element : mesh.getAsJsonArray("elements")) {
            JsonObject o = element.getAsJsonObject();
            float[] from = floats(o.getAsJsonArray("from")), to = floats(o.getAsJsonArray("to"));
            float dx = to[0] - from[0], dy = to[1] - from[1], dz = to[2] - from[2];
            JsonObject faceMap = o.getAsJsonObject("faces");
            for (String face : faces) {
                JsonObject f = faceMap.getAsJsonObject(face);
                if (f == null || !f.has("uv")) continue;
                float[] uv = floats(f.getAsJsonArray("uv"));
                boolean side = face.equals("east") || face.equals("west");
                boolean flat = face.equals("up") || face.equals("down");
                float w = side ? dz : dx;
                float h = flat ? dz : dy;
                walls++;
                if (uv[0] < 0 || uv[1] < 0 || uv[2] > 16 || uv[3] > 16
                        || uv[0] >= uv[2] || uv[1] >= uv[3]) {
                    outside++;
                }
                float tiny = Math.max(Math.min(w, h), 0.01f);
                float sx = (uv[2] - uv[0]) / Math.max(w, 0.01f);
                float sy = (uv[3] - uv[1]) / Math.max(h, 0.01f);
                if (Math.abs(sx - sy) > 0.02f + 0.01f / tiny) skewed++;
            }
        }
        check(outside == 0, "UV w granicach sprite'a 0..16 (" + walls + " scian, poza: " + outside + ")");
        check(skewed == 0, "UV o proporcjach sciany (rozciagnietych: " + skewed + ")");
    }

    /**
     * Kopia modelu i jego tekstur w JARZE moda ({@code pack/src/main/resources/assets/…}).
     *
     * Mod wozi własną kopię modeli urządzeń, żeby działały także bez serwerowej paczki — ale to
     * czwarta kopia tych samych plików i jedyna, której nie pilnuje {@code publish-pack.sh}
     * (bo nie dotyka zasobów w {@code src/main/resources}). Rozjechanie jej jest ciche: w paczce
     * jest nowy model, w jarze stary, a który wygra, zależy od kolejności paczek w kliencie.
     * Dlatego jeśli kopia istnieje, musi być identyczna co do bajtu. Brak kopii = urządzenie
     * żyje tylko w paczce (fallbacki właśnie tak żyją) — test wtedy nic nie sprawdza.
     */
    private static void checkBuiltinCopy(Path packFile) throws Exception {
        String path = packFile.toString().replace('\\', '/');
        if (!path.startsWith("pack/assets/")) return;
        Path builtin = Path.of("pack/src/main/resources/" + path.substring("pack/".length()));
        if (!Files.isRegularFile(builtin)) return;
        check(Files.mismatch(packFile, builtin) < 0,
                "kopia w jarze moda zgodna z paczka: " + builtin);
    }

    private static float[] floats(JsonArray a) {
        float[] out = new float[a.size()];
        for (int i = 0; i < a.size(); i++) out[i] = a.get(i).getAsFloat();
        return out;
    }

    /**
     * Overlaye z {@code pack.mcmeta} dostają gracze, którzy NIE moga mieć moda (format < 97, czyli
     * 26.1/26.2). Dlatego zadna definicja w overlayu nie moze wskazywac typu modelu z moda:
     * wanilia nie zna np. {@code raft:device} i odrzuca CALY plik, wiec przepadlyby tez wpisy
     * obok (dokladnie tak dzialo sie z radiem i beczka, zanim doszedl overlay).
     */
    private static void checkOverlays(String pack) throws Exception {
        Path meta = Path.of(pack, "..", "pack.mcmeta").normalize();
        if (!Files.isRegularFile(meta)) return;
        JsonObject overlays = read(meta.toString()).getAsJsonObject("overlays");
        if (overlays == null) return;
        for (var element : overlays.getAsJsonArray("entries")) {
            String directory = element.getAsJsonObject().get("directory").getAsString();
            Path overlay = Path.of(pack, "..", "overlays", directory).normalize();
            check(Files.isDirectory(overlay), "overlay istnieje: " + overlay);
            if (!Files.isDirectory(overlay)) continue;
            for (Path file : Files.walk(overlay)
                    .filter(p -> p.getFileName().toString().endsWith(".json"))
                    .toList()) {
                String modType = modRegisteredType(read(file.toString()), "");
                check(modType == null, "overlay " + directory + " nie uzywa typu z moda: "
                        + file.getFileName() + (modType == null ? "" : " (znaleziono " + modType + ")"));
            }
        }
    }

    /** Pierwszy typ modelu z namespace 'raft' (rejestruje go mod), albo {@code null}. */
    private static String modRegisteredType(JsonElement element, String path) {
        if (element.isJsonArray()) {
            for (var child : element.getAsJsonArray()) {
                String found = modRegisteredType(child, path);
                if (found != null) return found;
            }
        } else if (element.isJsonObject()) {
            for (var entry : element.getAsJsonObject().entrySet()) {
                String found = modRegisteredType(entry.getValue(), path + "/" + entry.getKey());
                if (found != null) return found;
            }
            var type = element.getAsJsonObject().get("type");
            if (type != null && type.isJsonPrimitive() && type.getAsString().startsWith("raft:")) {
                return type.getAsString();
            }
        }
        return null;
    }

    /**
     * Model z item definition: {@code raft:item/odbiornik_fallback} -> {@code assets/raft/models/item/odbiornik_fallback.json}.
     * Tak samo rozwiązuje to wanilia — id przez loader modeli, a nie po surowej ścieżce.
     */
    private static Path modelPath(String pack, String id) {
        String[] parts = split(id);
        return Path.of(pack, parts[0], "models", parts[1] + ".json");
    }

    private static String[] split(String id) {
        int colon = id.indexOf(':');
        return colon < 0
                ? new String[]{ "minecraft", id }
                : new String[]{ id.substring(0, colon), id.substring(colon + 1) };
    }

    private static String str(JsonObject o, String key) {
        return o != null && o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : null;
    }

    private static JsonObject entryFor(JsonObject itemDefinition, int threshold) {
        JsonArray entries = itemDefinition.getAsJsonObject("model").getAsJsonArray("entries");
        if (entries == null) return null;
        for (var element : entries) {
            JsonObject entry = element.getAsJsonObject();
            if (entry.has("threshold") && entry.get("threshold").getAsInt() == threshold) return entry;
        }
        return null;
    }

    private static JsonObject read(String path) throws Exception {
        return JsonParser.parseString(Files.readString(Path.of(path), StandardCharsets.UTF_8))
                .getAsJsonObject();
    }

    private static void check(boolean ok, String what) {
        if (!ok) problems++;
        System.out.println((ok ? "    ok    " : "    BLAD  ") + what);
    }
}
