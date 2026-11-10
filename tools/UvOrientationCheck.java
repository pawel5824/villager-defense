import java.nio.file.*;
import java.util.*;

/**
 * Sprawdza, jak autor ułożył UV na ścianach modelu Java.
 *
 * Metoda: dla każdej ściany liczy aspekt prostokąta UV i aspekt samej ściany
 * (w pikselach modelu). Jeśli UV jest ciasno przycięte do ściany (tak robi
 * Blockbench przy auto-UV), to ułożenie "poprawne" daje aspekt ~równy,
 * a obrócone o 90° daje aspekt odwrotny.
 *
 * Wynik mówi, KTÓRE osie UV odpowiadają którym osiom ściany — czyli jak
 * przypisać wierzchołki (u1,v1)...(u2,v2) przy budowaniu kwadrów w kodzie.
 *
 * Użycie: java UvOrientationCheck <model.json>
 */
public final class UvOrientationCheck {

    // osie ściany: [pozioma, pionowa] w pikselach modelu
    private static double[] faceSize(String face, double[] from, double[] to, double[] n) {
        double dx = to[0] - from[0], dy = to[1] - from[1], dz = to[2] - from[2];
        switch (face) {
            case "north": case "south": return new double[]{ dx, dy };
            case "east":  case "west":  return new double[]{ dz, dy };
            case "up":    case "down":  return new double[]{ dx, dz };
            default: return new double[]{ 0, 0 };
        }
    }

    public static void main(String[] args) throws Exception {
        String text = Files.readString(Path.of(args[0]));

        String[] faces = { "north", "south", "east", "west", "up", "down" };
        Map<String, int[]> match  = new LinkedHashMap<>();   // uv zgodne z osią
        Map<String, int[]> invert = new LinkedHashMap<>();   // uv obrócone 90°
        Map<String, int[]> other  = new LinkedHashMap<>();
        for (String f : faces) { match.put(f, new int[1]); invert.put(f, new int[1]); other.put(f, new int[1]); }

        int elements = 0;
        int faceCount = 0;

        // ręczne, minimalne czytanie JSON-a: szukamy bloków "from"/"to"/"faces"
        // (model Java z Blockbencana jest płaski: elements[] -> faces{})
        int idx = 0;
        while ((idx = text.indexOf("\"from\"", idx)) >= 0) {
            double[] from = readArray(text, idx + 6);
            int toIdx = text.indexOf("\"to\"", idx);
            if (toIdx < 0) break;
            double[] to = readArray(text, toIdx + 4);
            int facesIdx = text.indexOf("\"faces\"", toIdx);
            if (facesIdx < 0) break;
            int end = text.indexOf("\"from\"", facesIdx);
            String facesBlock = text.substring(facesIdx, end < 0 ? text.length() : end);
            elements++;
            for (String f : faces) {
                int fi = facesBlock.indexOf("\"" + f + "\"");
                if (fi < 0) continue;
                int uvIdx = facesBlock.indexOf("\"uv\"", fi);
                if (uvIdx < 0) continue;
                double[] uv = readArray(facesBlock, uvIdx + 4);
                if (uv.length < 4) continue;
                faceCount++;

                double fw = faceSize(f, from, to, null)[0];
                double fh = faceSize(f, from, to, null)[1];
                if (fw <= 0 || fh <= 0) continue;
                double faceAspect = fw / fh;

                double uw = Math.abs(uv[2] - uv[0]);
                double uh = Math.abs(uv[3] - uv[1]);
                if (uw <= 0 || uh <= 0) continue;
                double uvAspect = uw / uh;

                double a = Math.abs(faceAspect - uvAspect) / faceAspect;
                double b = Math.abs(faceAspect - 1.0 / uvAspect) / faceAspect;

                if (a < 0.02 && a <= b) match.get(f)[0]++;
                else if (b < 0.02) invert.get(f)[0]++;
                else other.get(f)[0]++;
            }
            idx = facesIdx;
        }

        System.out.println("elementow: " + elements + ", scian z UV: " + faceCount);
        System.out.printf("%-7s %10s %10s %10s%n", "sciana", "zgodne", "odwrotne", "inne");
        for (String f : faces) {
            System.out.printf("%-7s %10d %10d %10d%n", f, match.get(f)[0], invert.get(f)[0], other.get(f)[0]);
        }
    }

    /** Czyta tablicę liczb [a, b, c] zaczynającą się po podanym indeksie. */
    private static double[] readArray(String s, int start) {
        int open = s.indexOf('[', start);
        int close = s.indexOf(']', open);
        if (open < 0 || close < 0) return new double[0];
        String[] parts = s.substring(open + 1, close).split(",");
        double[] out = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try { out[i] = Double.parseDouble(parts[i].trim()); }
            catch (NumberFormatException e) { return new double[0]; }
        }
        return out;
    }
}
