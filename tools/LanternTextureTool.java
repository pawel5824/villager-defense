import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Narzędzie deweloperskie (NIE jest częścią pluginu — leży poza src/main/java).
 *
 * Generuje pokolorowane tekstury latarenki na podstawie WANILIOWEJ tekstury bloku
 * latarenki (16x48 = 3 klatki animacji). Rama latarenki (niebiesko-szary metal,
 * hue 222-225) zostaje nietknięta, a przerabiany jest tylko kolor "światła" w środku
 * (waniliowo ciepły pomarańcz/żółty, hue 18-60) — dzięki temu kolorowa latarenka
 * wygląda jak latarenka, a nie jak pomalowany klocek.
 *
 * Plik bazowy: tools/base/vanilla_lantern_block.png  (assets/minecraft/textures/block/lantern.png
 * z klienta MC; kopiujemy go tutaj, żeby wynik był odtwarzalny bez klienta).
 *
 * Wyjście (oba pakiety + .mcmeta z animacją, identyczną jak waniliowa):
 *   pack/assets/raft/textures/block/latarenka_&lt;id&gt;.png(+.mcmeta)
 *   modpack/resourcepacks/raft-hud/assets/raft/textures/block/latarenka_&lt;id&gt;.png(+.mcmeta)
 *
 * UWAGA: katalog musi być textures/block, a nie textures/item. Modele blokowe
 * (a takim jest nasza latarenka) są składane w atlas "blocks", więc tekstura
 * z katalogu textures/item nie zostałaby znaleziona (różowo-czarny placeholder).
 * Tak samo robi wasz raft:item/radio — tekstury ma w minecraft:block/.
 *
 * Użycie (z katalogu projektu):
 *   java tools/LanternTextureTool.java dump [plik]   — wypisz paletę
 *   java tools/LanternTextureTool.java gen           — wygeneruj wszystkie kolory
 *   java tools/LanternTextureTool.java check         — sprawdź gotowe pliki
 */
public class LanternTextureTool {

    private static final String BASE = "tools/base/vanilla_lantern_block.png";
    private static final String MCMETA = "{\n  \"animation\": {\n    \"frametime\": 8\n  }\n}";
    private static final String[] OUT_DIRS = {
            "pack/assets/raft/textures/block",
            "modpack/resourcepacks/raft-hud/assets/raft/textures/block"
    };

    /** id, docelowy hue (-1 = nie zmieniaj barwy), mnożnik saturacji, boost saturacji, mnożnik jasności. */
    private static final Object[][] COLORS = {
            { "fioletowa",       278, 1.50f, 0.10f, 1.00f },
            { "czerwona",          0, 1.50f, 0.10f, 1.00f },
            { "zielona",         120, 1.50f, 0.10f, 1.00f },
            { "niebieska",       210, 1.50f, 0.10f, 1.00f },
            { "zolta",            48, 1.50f, 0.10f, 1.00f },
            { "biala",            -1, 0.15f, 0.00f, 1.08f },
            { "pomaranczowa",     26, 1.50f, 0.10f, 1.00f },
            { "rozowa",          335, 1.40f, 0.10f, 1.06f },
            { "morska",          175, 1.50f, 0.10f, 1.04f },
            { "szara",            -1, 0.12f, 0.00f, 0.85f }
    };

    /** Hue ramy waniliowej latarenki mieści się w 222-225 — tak rozpoznajemy metal. */
    private static final float FRAME_HUE_MIN = 190f;
    private static final float FRAME_HUE_MAX = 260f;

    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0] : "gen";
        switch (mode) {
            case "dump" -> dump(ImageIO.read(new File(args.length > 1 ? args[1] : BASE)));
            case "gen" -> gen();
            case "check" -> check();
            default -> System.out.println("Tryby: dump [plik] | gen | check");
        }
    }

    // ─────────────────────────────── gen ───────────────────────────────

    private static void gen() throws Exception {
        BufferedImage base = ImageIO.read(new File(BASE));
        if (base == null) {
            System.err.println("Nie mogę wczytać " + BASE);
            System.exit(1);
        }
        System.out.println("Baza: " + BASE + "  " + base.getWidth() + "x" + base.getHeight()
                + " (klatek: " + (base.getHeight() / Math.max(1, base.getWidth())) + ")");

        for (Object[] c : COLORS) {
            String id = (String) c[0];
            float hue = ((Number) c[1]).floatValue();
            float satMul = ((Number) c[2]).floatValue();
            float satAdd = ((Number) c[3]).floatValue();
            float valMul = ((Number) c[4]).floatValue();

            BufferedImage out = recolor(base, hue, satMul, satAdd, valMul);
            for (String dir : OUT_DIRS) {
                File d = new File(dir);
                if (!d.exists() && !d.mkdirs()) {
                    System.err.println("Nie mogę utworzyć " + dir);
                    System.exit(1);
                }
                File png = new File(d, "latarenka_" + id + ".png");
                ImageIO.write(out, "png", png);
                Files.write(new File(d, "latarenka_" + id + ".png.mcmeta").toPath(),
                        MCMETA.getBytes(StandardCharsets.UTF_8));
            }
            System.out.printf("  %-14s hue=%6.1f  ok%n", id, hue);
        }
        System.out.println("Zapisano " + COLORS.length + " tekstur x " + OUT_DIRS.length + " lokalizacje.");
    }

    private static BufferedImage recolor(BufferedImage base, float targetHue,
                                         float satMul, float satAdd, float valMul) {
        BufferedImage out = new BufferedImage(base.getWidth(), base.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < base.getHeight(); y++) {
            for (int x = 0; x < base.getWidth(); x++) {
                int argb = base.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                if (a == 0) { out.setRGB(x, y, 0); continue; }

                int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
                float[] hsb = java.awt.Color.RGBtoHSB(r, g, b, null);
                float h = hsb[0] * 360f, s = hsb[1], v = hsb[2];

                boolean frame = h >= FRAME_HUE_MIN && h <= FRAME_HUE_MAX;
                float nh = h, ns = s, nv = v;
                if (!frame) {
                    // światło w środku — zmieniamy barwę
                    if (targetHue >= 0) nh = targetHue;
                    ns = Math.min(1f, s * satMul + satAdd);
                    nv = Math.min(1f, v * valMul);
                }
                int rgb = java.awt.Color.HSBtoRGB(nh / 360f, ns, nv);
                out.setRGB(x, y, (a << 24) | (rgb & 0xFFFFFF));
            }
        }
        return out;
    }

    // ────────────────────────────── check ──────────────────────────────

    /** Sprawdza gotowe pliki: rozmiar, obecność .mcmeta i to, że rama pozostała waniliowa. */
    private static void check() throws Exception {
        BufferedImage base = ImageIO.read(new File(BASE));
        for (String dir : OUT_DIRS) {
            System.out.println("=== " + dir);
            for (Object[] c : COLORS) {
                String id = (String) c[0];
                File png = new File(dir, "latarenka_" + id + ".png");
                File mc = new File(dir, "latarenka_" + id + ".png.mcmeta");
                if (!png.exists()) { System.out.println("  " + id + " -> BRAK PNG"); continue; }
                BufferedImage img = ImageIO.read(png);
                int frameDiff = countFrameMismatch(base, img);
                // Światło rozpoznajemy po BAZIE (w wyniku jego hue może wypaść w oknie ramy,
                // np. dla niebieskiej 210 — wtedy liczenie po wyniku dawałoby pustą listę).
                List<String> glowHues = new ArrayList<>();
                int glowPixels = 0, glowChanged = 0;
                for (int y = 0; y < base.getHeight() && y < img.getHeight(); y++) {
                    for (int x = 0; x < base.getWidth() && x < img.getWidth(); x++) {
                        int a = base.getRGB(x, y);
                        if (((a >>> 24) & 0xFF) == 0) continue;
                        float[] hsb = java.awt.Color.RGBtoHSB((a >> 16) & 0xFF, (a >> 8) & 0xFF, a & 0xFF, null);
                        float h = hsb[0] * 360f;
                        if (h >= FRAME_HUE_MIN && h <= FRAME_HUE_MAX) continue; // rama
                        glowPixels++;
                        if (a != img.getRGB(x, y)) glowChanged++;
                        int oa = img.getRGB(x, y);
                        float[] oh = java.awt.Color.RGBtoHSB((oa >> 16) & 0xFF, (oa >> 8) & 0xFF, oa & 0xFF, null);
                        String s = String.format("%.0f", oh[0] * 360f);
                        if (!glowHues.contains(s)) glowHues.add(s);
                    }
                }
                System.out.printf("  %-14s %dx%d  mcmeta=%s  rama-różni-się=%d  światło: %d/%d zmienione, odcienie=%s%n",
                        id, img.getWidth(), img.getHeight(), mc.exists() ? "ok" : "BRAK",
                        frameDiff, glowChanged, glowPixels, glowHues);
            }
        }
    }

    /** Ile pikseli ramy (niebiesko-szarych) różni się od wanilii. Powinno być 0. */
    private static int countFrameMismatch(BufferedImage base, BufferedImage img) {
        int diff = 0;
        for (int y = 0; y < base.getHeight() && y < img.getHeight(); y++) {
            for (int x = 0; x < base.getWidth() && x < img.getWidth(); x++) {
                int a = base.getRGB(x, y);
                if (((a >>> 24) & 0xFF) == 0) continue;
                float[] hsb = java.awt.Color.RGBtoHSB((a >> 16) & 0xFF, (a >> 8) & 0xFF, a & 0xFF, null);
                float h = hsb[0] * 360f;
                if (h < FRAME_HUE_MIN || h > FRAME_HUE_MAX) continue; // to światło, nie rama
                if (a != img.getRGB(x, y)) diff++;
            }
        }
        return diff;
    }

    // ─────────────────────────────── dump ──────────────────────────────

    private static void dump(BufferedImage img) {
        if (img == null) { System.out.println("Nie wczytano pliku"); return; }
        System.out.println("Rozmiar: " + img.getWidth() + "x" + img.getHeight());
        var counts = new java.util.TreeMap<Integer, Integer>();
        for (int y = 0; y < img.getHeight(); y++)
            for (int x = 0; x < img.getWidth(); x++)
                counts.merge(img.getRGB(x, y), 1, Integer::sum);
        System.out.println("Unikalnych kolorów: " + counts.size());
        counts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .forEach(e -> {
                    int argb = e.getKey();
                    int a = (argb >>> 24) & 0xFF;
                    float[] hsb = java.awt.Color.RGBtoHSB((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, null);
                    System.out.printf("#%06X a=%3d x%-4d hue=%6.1f sat=%.3f val=%.3f  %s%n",
                            argb & 0xFFFFFF, a, e.getValue(), hsb[0] * 360f, hsb[1], hsb[2],
                            (hsb[0] * 360f >= FRAME_HUE_MIN && hsb[0] * 360f <= FRAME_HUE_MAX) ? "RAMA" : "światło");
                });
    }
}
