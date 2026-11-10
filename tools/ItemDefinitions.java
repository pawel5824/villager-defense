import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.ZipFile;

/**
 * Generator definicji przedmiotów (`assets/<ns>/items/*.json`) dla waniliowych przedmiotów,
 * którym plugin nadaje CustomModelData.
 *
 * Po co: od 1.21.4 o wyglądzie przedmiotu decyduje item definition, a `overrides`
 * w `models/item/*.json` jest **po cichu ignorowane** — przedmiot wygląda waniliowo i nic nie ma
 * w logu.
 *
 * **Skąd bierze się tabela: WYŁĄCZNIE z pluginu** (`ItemManager.java` — kto dostaje jaki CMD
 * i na jakim Materiale). Stare pliki `overrides` w paczce to pozostałość po liście przedmiotów,
 * której już nie ma w grze — branie z nich mapowania daje definicje dla CMD, których plugin
 * nigdy nie ustawia, czyli „customowe" przedmioty, których nikt nie ma. Tak było z 18 plikami
 * (ziemia, moneta, wiadro, rudy, sztabki, diament…): w kodzie istnieje tylko **Lina** (LEAD,
 * 1002), **Odpadki** (PRISMARINE_SHARD, 1007) i **Gwoździe** (IRON_NUGGET, 7012).
 *
 * **Model bez tekstury nie jest podłączany.** Model wskazujący nieistniejący PNG renderuje się
 * jako szachownica „missing texture" — czyli podłączenie go pogorszyłoby wygląd przedmiotu
 * względem wanilii. Dlatego para z `LIVE`, której model nie ma wszystkich tekstur, jest
 * pomijana (a jej plik definicji — jeśli leży w paczce — usuwany), z wypisaniem czego brakuje.
 * Gdy wrzucisz PNG, następne uruchomienie podłączy model samo.
 *
 * **Tabele są jedynym miejscem z tym mapowaniem.** `PREVIOUSLY_GENERATED` jest po to, żeby
 * narzędzie umiało samo posprzątać definicje, których już nie generuje (bez ruszania plików
 * pisanych ręcznie — `HANDMADE`).
 *
 * Uruchamianie: tools/gen-item-definitions.sh [klient.jar] [katalog_paczki ...]
 * (domyślnie klient z cache Loom 26.3 i obie paczki: `pack` + `modpack/resourcepacks/raft-hud`).
 */
public final class ItemDefinitions {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /**
     * Żywe pary: przedmiot z pluginu -> CMD -> model z paczki. Źródło każdego wiersza jest
     * w komentarzu (`ItemManager.java`, metoda tworząca przedmiot). Nowy wiersz dopisuj TYLKO
     * wtedy, gdy w pluginie naprawdę stoi `setCustomModelData` z tym numerem na tym Materiale.
     */
    private static final Map<String, Map<Integer, String>> LIVE = new LinkedHashMap<>();

    static {
        LIVE.put("lead", Map.of(1002, "raft:item/lina_custom"));               // createRope    "§eLina"     (LEAD)
        LIVE.put("prismarine_shard", Map.of(1007, "raft:item/odpadki_custom")); // createOdpadki "§7Odpadki"  (PRISMARINE_SHARD)
        LIVE.put("iron_nugget", Map.of(7012, "raft:item/gwozdz_custom"));      // createNail    "§7Gwoździe" (IRON_NUGGET)
    }

    /**
     * Modele leżące w paczce, do których w pluginie **nie ma przedmiotu** (albo jest, ale pod
     * innym materiałem/CMD — wtedy wiersz należy do `LIVE`). Trzymamy je tu jako listę
     * „zaparkowanych", żeby nic nie zginęło: gdy plugin dostanie taki przedmiot, przepisujesz
     * wiersz do `LIVE` i definicja powstaje sama.
     */
    private static final Map<String, String> PARKED_MODELS = new LinkedHashMap<>();

    static {
        PARKED_MODELS.put("raft:item/sunflower_moneta", "moneta (w pluginie waluta to licznik `coins`, nie przedmiot)");
        PARKED_MODELS.put("raft:item/ziemia_custom", "ziemia — przedmiotu nie ma w ItemManager");
        PARKED_MODELS.put("raft:item/kostka_odpadow_custom", "kostka odpadów — brak przedmiotu");
        PARKED_MODELS.put("raft:item/maz_algowa_custom", "maź algowa — brak przedmiotu (jest „Wybuchowa maź”, CYAN_DYE 7033)");
        PARKED_MODELS.put("raft:item/szklo_custom", "szkło — brak przedmiotu");
        PARKED_MODELS.put("raft:item/wiadro_custom", "wiadro — brak przedmiotu (źródło: wiadro.bbmodel)");
        PARKED_MODELS.put("raft:item/ruda_metalu_custom", "ruda metalu — brak przedmiotu");
        PARKED_MODELS.put("raft:item/metal_custom", "metal — brak przedmiotu");
        PARKED_MODELS.put("raft:item/ruda_miedzi_custom", "ruda miedzi — brak przedmiotu");
        PARKED_MODELS.put("raft:item/sztabka_miedzi_custom", "sztabka miedzi — brak przedmiotu");
        PARKED_MODELS.put("raft:item/ruda_tytanu_custom", "ruda tytanu — brak przedmiotu");
        PARKED_MODELS.put("raft:item/sztabka_tytanu_custom", "sztabka tytanu — brak przedmiotu");
        PARKED_MODELS.put("raft:item/diamond_custom", "diament — brak przedmiotu");
        PARKED_MODELS.put("raft:item/wosk_pszczeli_custom", "wosk pszczeli — brak przedmiotu");
        PARKED_MODELS.put("raft:item/wiatrak", "wiatrak jest URZĄDZENIEM (2×2×6), nie przedmiotem");
    }

    /** Definicje pisane ręcznie — generator ich nie tworzy i nie kasuje. */
    private static final Set<String> HANDMADE = Set.of("heavy_core", "spyglass", "lantern");

    /**
     * Nazwy przedmiotów, dla których definicje generowały poprzednie wersje tego narzędzia
     * (mapowanie wzięte z martwych `overrides`). Służy tylko do sprzątania: cokolwiek z tej listy
     * nie jest w tym uruchomieniu wygenerowane, zostanie usunięte jako nieaktualne.
     */
    private static final Set<String> PREVIOUSLY_GENERATED = Set.of(
            "sunflower", "lead", "dirt", "exposed_lightning_rod", "dead_tube_coral_block",
            "slime_ball", "glass_pane", "bucket", "raw_gold", "gold_ingot", "raw_copper",
            "copper_ingot", "raw_iron", "iron_ingot", "diamond", "honeycomb", "stick",
            "prismarine_shard", "iron_nugget");

    private static int problems = 0;

    public static void main(String[] args) throws Exception {
        Path jar = Path.of(args[0]);
        for (int i = 1; i < args.length; i++) {
            Path pack = Path.of(args[i]);
            System.out.println("=== " + args[i]);
            try (ZipFile zip = new ZipFile(jar.toFile())) {
                Set<String> written = generate(zip, pack);
                cleanUp(pack, written);
                verifyAll(zip, pack);
            }
        }
        reportParked();
        System.out.println(problems == 0 ? "OK — definicje zgodne z pluginem."
                : "PROBLEMY: " + problems);
        if (problems > 0) System.exit(1);
    }

    private static Set<String> generate(ZipFile jar, Path pack) throws Exception {
        Set<String> written = new LinkedHashSet<>();
        for (Map.Entry<String, Map<Integer, String>> entry : LIVE.entrySet()) {
            String item = entry.getKey();
            JsonElement fallback = vanillaModel(jar, item);
            if (fallback == null) {
                System.out.println("  BLAD   wanilia nie ma definicji items/" + item + ".json w jarze");
                problems++;
                continue;
            }

            boolean usable = true;
            for (String model : entry.getValue().values()) {
                if (!Files.isRegularFile(modelPath(pack, model))) {
                    System.out.println("  BLAD   brakuje modelu " + model + " (przedmiot " + item + ")");
                    problems++;
                    usable = false;
                    continue;
                }
                List<String> missing = missingTextures(jar, pack, model);
                if (!missing.isEmpty()) {
                    System.out.println("  POMIN  " + item + " (CMD " + entry.getValue().entrySet().iterator().next().getKey()
                            + ") — model " + model + " nie ma tekstur: " + missing
                            + " → zostawiam waniliowy wygląd (szachownica byłaby gorsza)");
                    usable = false;
                }
            }
            if (!usable) continue;

            JsonObject root = new JsonObject();
            root.addProperty("__comment", "CustomModelData dla waniliowego przedmiotu " + item
                    + ". Wczesniej robily to 'overrides' w models/item/*.json, ale od 1.21.4"
                    + " wanilia tego nie czyta (AGENTS.md §5). Fallback = waniliowa definicja.");
            JsonObject dispatch = new JsonObject();
            dispatch.addProperty("type", "minecraft:range_dispatch");
            dispatch.addProperty("property", "minecraft:custom_model_data");
            dispatch.add("fallback", fallback.deepCopy());
            JsonArray list = new JsonArray();
            for (Map.Entry<Integer, String> single : new TreeMap<>(entry.getValue()).entrySet()) {
                JsonObject model = new JsonObject();
                model.addProperty("threshold", single.getKey());
                JsonObject ref = new JsonObject();
                ref.addProperty("type", "minecraft:model");
                ref.addProperty("model", single.getValue());
                model.add("model", ref);
                list.add(model);
            }
            dispatch.add("entries", list);
            root.add("model", dispatch);

            Path target = definitionPath(pack, item);
            Files.createDirectories(target.getParent());
            Files.writeString(target, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);
            written.add(item);
            System.out.println("  ok     " + item + "  fallback=" + modelId(fallback)
                    + "  -> " + entry.getValue().values());
        }
        return written;
    }

    /** Usuwa definicje, których to uruchomienie nie wygenerowało, a które robiło poprzednie. */
    private static void cleanUp(Path pack, Set<String> written) throws Exception {
        for (String item : PREVIOUSLY_GENERATED) {
            if (written.contains(item) || HANDMADE.contains(item)) continue;
            Path file = definitionPath(pack, item);
            if (Files.deleteIfExists(file)) {
                System.out.println("  usun   " + item + ".json (CMD nieuzywany przez plugin"
                        + " albo model bez tekstury)");
            }
        }
    }

    /** Czy model ma wszystkie tekstury? Bez tego w grze byłaby szachownica. */
    private static List<String> missingTextures(ZipFile jar, Path pack, String model) throws Exception {
        List<String> missing = new ArrayList<>();
        Path file = modelPath(pack, model);
        JsonElement textures = read(file).get("textures");
        if (textures == null || !textures.isJsonObject()) return missing;
        for (Map.Entry<String, JsonElement> texture : textures.getAsJsonObject().entrySet()) {
            if (!texture.getValue().isJsonPrimitive()) continue;
            String id = texture.getValue().getAsString();
            if (id.startsWith("#")) continue; // referencja do innej tekstury tego modelu
            // Bez namespace wanilia rozwiazuje teksture w namespace modelu (tak robi
            // np. raft:item/duza_beczka z "block/beczka_2"), wiec sprawdzamy oba warianty.
            String ownNamespace = id.contains(":") ? id : namespaceOf(model) + ":" + id;
            if (textureExists(jar, pack, id) || textureExists(jar, pack, ownNamespace)) continue;
            missing.add(id);
        }
        return missing;
    }

    /**
     * Kontrola CAŁEJ paczki: każdy `model` wskazany w item definitions musi istnieć — w paczce
     * albo w wanilii. Tego nie złapie żaden test parsowania (nieistniejący model to nie błąd
     * składni), a efekt w grze to brakujący przedmiot.
     */
    private static void verifyAll(ZipFile jar, Path pack) throws Exception {
        Path dir = pack.resolve("assets/minecraft/items");
        if (!Files.isDirectory(dir)) return;
        Set<String> resolved = new LinkedHashSet<>();
        try (var files = Files.list(dir)) {
            for (Path file : files.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList()) {
                String item = file.getFileName().toString().replace(".json", "");
                visitModels(read(file), id -> {
                    if (exists(pack, jar, id)) {
                        if (id.startsWith("raft:")) resolved.add(id);
                        return;
                    }
                    System.out.println("  BLAD   " + item + ".json wskazuje nieistniejacy model " + id);
                    problems++;
                });
            }
        }
        Set<String> missing = new TreeSet<>();
        for (String model : resolved) {
            for (String texture : missingTextures(jar, pack, model)) missing.add(model + " -> " + texture);
        }
        if (!missing.isEmpty()) {
            System.out.println("  OSTRZEZENIE: podlaczone modele bez tekstury (szachownica w grze):");
            missing.forEach(entry -> System.out.println("    - " + entry));
        }
    }

    /** Wypisuje modele, ktore czekaja na przedmiot w pluginie — zeby nie zginely z pola widzenia. */
    private static void reportParked() {
        System.out.println();
        System.out.println("Modele BEZ przedmiotu w pluginie (zaparkowane, definicji nie generujemy):");
        PARKED_MODELS.forEach((model, why) -> System.out.println("  - " + model + "  — " + why));
    }

    private static String namespaceOf(String id) {
        int colon = id.indexOf(':');
        return colon < 0 ? "minecraft" : id.substring(0, colon);
    }

    private static boolean textureExists(ZipFile jar, Path pack, String id) {
        int colon = id.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : id.substring(0, colon);
        String path = colon < 0 ? id : id.substring(colon + 1);
        if (Files.isRegularFile(pack.resolve("assets").resolve(namespace)
                .resolve("textures").resolve(path + ".png"))) {
            return true;
        }
        return "minecraft".equals(namespace)
                && jar.getEntry("assets/minecraft/textures/" + path + ".png") != null;
    }

    /** Iteruje wszystkie wartości pól `model`/`base` (identyfikatory modeli) w definicji. */
    private static void visitModels(JsonElement element, java.util.function.Consumer<String> visit) {
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) visitModels(child, visit);
            return;
        }
        if (!element.isJsonObject()) return;
        JsonElement type = element.getAsJsonObject().get("type");
        if (type != null && type.isJsonPrimitive() && type.getAsString().startsWith("raft:")) {
            return; // typ rejestrowany przez mod — jego 'model' to siatka moda, nie model wanilii
        }
        for (Map.Entry<String, JsonElement> field : element.getAsJsonObject().entrySet()) {
            JsonElement value = field.getValue();
            boolean reference = ("model".equals(field.getKey()) || "base".equals(field.getKey()))
                    && value.isJsonPrimitive() && value.getAsString().contains(":");
            if (reference) {
                visit.accept(value.getAsString());
            } else {
                visitModels(value, visit);
            }
        }
    }

    /** Model spoza `minecraft:` musi byc w paczce; waniliowy moze byc w paczce albo w jarze. */
    private static boolean exists(Path pack, ZipFile jar, String id) {
        if (Files.isRegularFile(modelPath(pack, id))) return true;
        int colon = id.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : id.substring(0, colon);
        if (!"minecraft".equals(namespace)) return false;
        String path = colon < 0 ? id : id.substring(colon + 1);
        return jar.getEntry("assets/minecraft/models/" + path + ".json") != null;
    }

    /** `model` z waniliowej definicji przedmiotu — czyli to, co przedmiot robi bez CMD. */
    private static JsonElement vanillaModel(ZipFile jar, String item) throws Exception {
        var entry = jar.getEntry("assets/minecraft/items/" + item + ".json");
        if (entry == null) return null;
        try (InputStream in = jar.getInputStream(entry)) {
            JsonObject root = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject();
            return root.get("model");
        }
    }

    private static String modelId(JsonElement model) {
        JsonObject object = model.getAsJsonObject();
        JsonElement id = object.get("model");
        return id != null && id.isJsonPrimitive() ? id.getAsString() : object.get("type").getAsString();
    }

    private static Path definitionPath(Path pack, String item) {
        return pack.resolve("assets/minecraft/items/" + item + ".json");
    }

    /** `raft:item/wiadro_custom` -> `assets/raft/models/item/wiadro_custom.json`. */
    private static Path modelPath(Path pack, String id) {
        int colon = id.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : id.substring(0, colon);
        String path = colon < 0 ? id : id.substring(colon + 1);
        return pack.resolve("assets").resolve(namespace).resolve("models").resolve(path + ".json");
    }

    private static JsonObject read(Path file) throws Exception {
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
