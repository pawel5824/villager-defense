import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.server.packs.OverlayMetadataSection;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackFormat;
import net.minecraft.util.InclusiveRange;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;

/**
 * Sprawdza `pack.mcmeta` kodekiem KONKRETNEJ wersji klienta: czy deklaracja formatu się parsuje,
 * czy format tej wersji mieści się w zakresie i KTÓRE katalogi overlaya klient wybierze.
 *
 * Odpowiada bez uruchamiania gry na dwa pytania, na których łatwo się przejechać:
 *   1. czy `min_format`/`max_format` (i sekcja `overlays`) są w tej wersji w ogóle akceptowane,
 *   2. czy overlay dla moda trafia w 26.3, a nie trafia w 26.1/26.2 — czyli czy starsi gracze
 *      dostaną plik bez typu modelu rejestrowanego przez mod.
 *
 * Uruchamianie: tools/pack-metadata-check.sh
 */
public final class PackMetadataCheck {

    public static void main(String[] args) throws Exception {
        Path clientJar = Path.of(args[0]);
        Path metadata = Path.of(args[1]);

        PackFormat version = resourceFormat(clientJar);
        System.out.println("klient " + clientJar.getFileName() + "  format zasobow " + version);

        JsonObject root = JsonParser.parseString(
                Files.readString(metadata, StandardCharsets.UTF_8)).getAsJsonObject();

        DataResult<InclusiveRange<PackFormat>> range =
                PackFormat.packCodec(PackType.CLIENT_RESOURCES).codec().parse(JsonOps.INSTANCE, root.get("pack"));
        if (range.result().isEmpty()) {
            System.out.println("  BLAD    sekcja 'pack' nie parsuje sie: " + range.error().get().message());
            System.exit(1);
        }
        InclusiveRange<PackFormat> accepted = range.result().get();
        boolean inside = accepted.minInclusive().compareTo(version) <= 0
                && accepted.maxInclusive().compareTo(version) >= 0;
        System.out.println("  " + (inside ? "ok    " : "BLAD  ") + "zakres packa " + accepted.minInclusive()
                + ".." + accepted.maxInclusive() + " obejmuje te wersje: " + inside);

        JsonElement overlays = root.get("overlays");
        if (overlays == null) {
            System.out.println("  brak sekcji 'overlays' — klient nie dolozy zadnego katalogu");
            return;
        }
        DataResult<OverlayMetadataSection> parsed = OverlayMetadataSection
                .codecForPackType(PackType.CLIENT_RESOURCES)
                .parse(JsonOps.INSTANCE, overlays);
        if (parsed.result().isEmpty()) {
            System.out.println("  BLAD    sekcja 'overlays' nie parsuje sie: " + parsed.error().get().message());
            System.exit(1);
        }
        OverlayMetadataSection section = parsed.result().get();
        List<String> chosen = section.overlaysForVersion(version);
        System.out.println("  " + (chosen.isEmpty() ? "brak   " : "ok     ")
                + "overlaye dla " + version + ": " + chosen);
        section.overlays().forEach(entry -> System.out.println("           wpis: " + entry.format()
                + " -> " + entry.overlay()));
    }

    /** Format zasobow tej wersji z `version.json` w jarze klienta. */
    private static PackFormat resourceFormat(Path clientJar) throws Exception {
        try (ZipFile zip = new ZipFile(clientJar.toFile())) {
            JsonObject version = JsonParser.parseString(
                    new String(zip.getInputStream(zip.getEntry("version.json")).readAllBytes(),
                            StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject pack = version.getAsJsonObject("pack_version");
            return PackFormat.of(pack.get("resource_major").getAsInt(), pack.get("resource_minor").getAsInt());
        }
    }
}
