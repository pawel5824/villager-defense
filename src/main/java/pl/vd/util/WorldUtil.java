package pl.vd.util;

import org.bukkit.World;

import java.io.File;

public final class WorldUtil {

    private WorldUtil() {}

    /**
     * Znajduje folder świata, który BEZPOŚREDNIO zawiera katalog "dimensions".
     * W nowym formacie Paper (1.21.5+ / 26.x) World#getWorldFolder() wskazuje folder
     * konkretnej dimensji (np. &lt;world&gt;/dimensions/minecraft/overworld), a nie folder
     * świata, w którym leżą wszystkie dimensje - dlatego wędrujemy w górę, aż trafimy
     * na folder zawierający "dimensions". Dla starego formatu (świat jako folder
     * na poziomie serwera) zwraca sam folder świata albo null, gdy nie ma dimensji.
     */
    public static File findDimensionsRoot(World w) {
        File cur = w.getWorldFolder();
        while (cur != null) {
            if (new File(cur, "dimensions").isDirectory()) {
                return cur;
            }
            cur = cur.getParentFile();
        }
        return null;
    }
}
