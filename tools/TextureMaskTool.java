import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Narzędzie deweloperskie: wypisuje mapę nieprzezroczystych pikseli tekstury,
 * żeby porównać kształt dwóch PNG (np. czy baza to sprite itemu czy tekstura bloku).
 *
 * Użycie: java tools/TextureMaskTool.java <plik1> <plik2> ...
 */
public class TextureMaskTool {
    public static void main(String[] args) throws Exception {
        for (String p : args) {
            BufferedImage img = ImageIO.read(new File(p));
            if (img == null) { System.out.println(p + " -> NIE WCZYTANO"); continue; }
            System.out.println("===== " + p + "  " + img.getWidth() + "x" + img.getHeight());
            int opaque = 0;
            for (int y = 0; y < img.getHeight(); y++) {
                StringBuilder sb = new StringBuilder();
                for (int x = 0; x < img.getWidth(); x++) {
                    int a = (img.getRGB(x, y) >>> 24) & 0xFF;
                    if (a > 0) opaque++;
                    sb.append(a > 0 ? '#' : '.');
                }
                System.out.println("  " + sb);
            }
            System.out.println("  nieprzezroczystych: " + opaque);
        }
    }
}
