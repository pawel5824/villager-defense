import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.renderer.item.ClientItem;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Sprawdza, czy PLIKI item definitions z paczki sparsują się kodekiem KONKRETNEJ wersji klienta
 * (uruchamiane raz na wersję, bo klas nie da się przeładować). Odpowiada na pytanie, którego
 * nie widać w JSON-ie: czy odwołanie do typu rejestrowanego przez mod (np. {@code raft:device})
 * wywala tylko ten jeden wpis, czy CAŁY plik item definition — a więc i wszystkie pozostałe
 * CustomModelData z tego pliku.
 *
 * Uwaga: to celowo klient BEZ moda — dokładnie taki, jakiego ma gracz na 26.1/26.2.
 *
 * Uruchamianie: tools/pack-compat-check.sh
 */
public final class PackCompatCheck {

    public static void main(String[] args) throws Exception {
        // Dostępne typy modeli przedmiotów (bez moda) — do raportu.
        bootstrap();

        int files = 0;
        int failed = 0;
        for (String pack : args) {
            System.out.println("=== paczka: " + pack);
            for (Path file : itemDefinitions(Path.of(pack))) {
                files++;
                String json = Files.readString(file, StandardCharsets.UTF_8);
                String id = file.getParent().getFileName() + "/" + file.getFileName();
                DataResult<ClientItem> result;
                try {
                    JsonElement parsed = JsonParser.parseString(json);
                    result = ClientItem.CODEC.parse(JsonOps.INSTANCE, parsed);
                } catch (Throwable t) {
                    System.out.println("  WYJATEK " + id + ": " + t);
                    failed++;
                    continue;
                }
                if (result.result().isPresent()) {
                    System.out.println("  ok     " + id);
                } else {
                    failed++;
                    System.out.println("  BLAD   " + id + "  ->  " + result.error().get().message());
                }
            }
        }
        System.out.println(failed == 0
                ? "OK — wszystkie pliki (" + files + ") sparsowaly sie kodekiem tej wersji."
                : "NIE SPARSOWALO SIE: " + failed + " z " + files);
        if (failed > 0) System.exit(1);
    }

    /** Rejestracja wbudowanych typów (mod robi to samo ze swoim {@code raft:device}). */
    private static void bootstrap() {
        try {
            Class.forName("net.minecraft.SharedConstants").getMethod("tryDetectVersion").invoke(null);
        } catch (Throwable t) {
            System.out.println("tryDetectVersion: " + rootCause(t));
        }
        for (String className : List.of(
                "net.minecraft.client.renderer.item.ItemModels",
                "net.minecraft.client.renderer.special.SpecialModelRenderers",
                "net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperties",
                "net.minecraft.client.renderer.item.properties.select.SelectItemModelProperties",
                "net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperties",
                "net.minecraft.client.color.item.ItemTintSources")) {
            try {
                Class.forName(className).getMethod("bootstrap").invoke(null);
                System.out.println("zbootstrapowane: " + className);
            } catch (NoSuchMethodException e) {
                System.out.println("bez metody bootstrap(): " + className);
            } catch (Throwable t) {
                System.out.println("bez bootstrapu " + className + ": " + rootCause(t));
            }
        }
    }

    private static Throwable rootCause(Throwable t) {
        while (t.getCause() != null) t = t.getCause();
        return t;
    }

    private static List<Path> itemDefinitions(Path pack) throws Exception {
        Path items = pack.resolve("assets");
        List<Path> out = new ArrayList<>();
        if (!Files.isDirectory(items)) return out;
        try (Stream<Path> walk = Files.walk(items)) {
            walk.filter(p -> p.getFileName().toString().endsWith(".json"))
                .filter(p -> p.getParent() != null && p.getParent().getFileName().toString().equals("items"))
                .sorted()
                .forEach(out::add);
        }
        return out;
    }
}
