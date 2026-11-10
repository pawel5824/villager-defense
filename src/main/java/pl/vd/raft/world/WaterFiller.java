package pl.vd.raft.world;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.scheduler.BukkitRunnable;
import pl.vd.raft.RaftPlugin;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.logging.Logger;

/**
 * Asynchroniczne wypełnianie wody + piasku w oceanie.
 *
 * Naprawia PUSTE kolumny (void) — np. chunki, które wygenerowały się bez
 * generatora oceanu (VoidGenerator). Dla każdej kolumny bez wody na poziomie
 * morza kładzie: bedrock, piasek (z wybrzuszeniami), a nad nim wodę.
 * Kolumny z wodą lub lądem na poziomie morza pomija — naprawa jest
 * bezpieczna dla istniejących wysp i idempotentna (można ją uruchamiać
 * przy każdym starcie serwera).
 *
 *   • createWorld() kończy się natychmiast
 *   • serwer się nie zacina (max 50 chunków/tick)
 *   • plik świata jest lżejszy (paleta kompresuje jednolite sekcje wody)
 */
public class WaterFiller {

    private final RaftPlugin plugin;

    /** Ile chunków przetwarzać na jeden tick serwera. */
    private static final int CHUNKS_PER_TICK = 50;

    /** Piasek tuż nad bedrockiem. */
    private static final int SAND_BASE = DeepOceanGenerator.BEDROCK_LEVEL + 1;

    /** Szansa na piasek na Y=-59 (jeden nad bazą). */
    private static final double CHANCE_LAYER1 = 0.58;

    /** Szansa na piasek na Y=-58 (dwa nad bazą). */
    private static final double CHANCE_LAYER2 = 0.27;

    /** Szansa na trawę morską nad najwyższym piaskiem. */
    private static final double SEAGRASS_CHANCE = DeepOceanGenerator.SEAGRASS_CHANCE;

    /** Poziom morza. */
    private static final int WATER_END = DeepOceanGenerator.SEA_LEVEL;

    /** Callback wywoływany po zakończeniu wypełniania. */
    public interface FillCallback {
        void onComplete();
    }

    private FillCallback callback;

    public WaterFiller(RaftPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Ustawia callback wywoływany po zakończeniu wypełniania wody.
     */
    public WaterFiller onComplete(FillCallback callback) {
        this.callback = callback;
        return this;
    }

    /**
     * Uruchamia asynchroniczne wypełnianie wody na podanym świecie.
     * Wywołaj zaraz po generateWorld().
     */
    public void fillAsync(World world) {
        // Promień mapy w chunkach — z world border (raft.map-size), np. 1000 bloków
        // = 500/16 + 1 ≈ 32 chunki w każdą stronę.
        int mapSize = plugin.getConfig().getInt("raft.map-size", 1000);
        int mapRadiusChunks = Math.max(16, (mapSize / 2) / 16 + 1);

        // Zbierz wszystkie chunki do przetworzenia
        Queue<int[]> chunks = new ArrayDeque<>();
        int totalChunks = 0;
        for (int cx = -mapRadiusChunks; cx <= mapRadiusChunks; cx++) {
            for (int cz = -mapRadiusChunks; cz <= mapRadiusChunks; cz++) {
                chunks.add(new int[]{cx, cz});
                totalChunks++;
            }
        }

        final int total = totalChunks;
        final Logger log = plugin.getLogger();
        log.info("[WaterFiller] Rozpoczynam wypełnianie wody: "
                + total + " chunków (" + CHUNKS_PER_TICK + "/tick)...");

        new BukkitRunnable() {
            int processed = 0;

            @Override
            public void run() {
                int batchCount = 0;

                while (!chunks.isEmpty() && batchCount < CHUNKS_PER_TICK) {
                    int[] coords = chunks.poll();
                    fillChunkColumn(world, coords[0], coords[1]);
                    batchCount++;
                    processed++;
                }

                // Loguj postęp co 10%
                if (processed % (total / 10 + 1) == 0 || chunks.isEmpty()) {
                    int percent = (int) ((processed * 100.0) / total);
                    log.info("[WaterFiller] Postęp: " + processed + "/" + total
                            + " chunków (" + percent + "%)");
                }

                if (chunks.isEmpty()) {
                    log.info("[WaterFiller] Wypełnianie wody zakończone! Przetworzono "
                            + processed + " chunków.");
                    this.cancel();

                    // Wywołaj callback po zakończeniu
                    if (callback != null) {
                        // Uruchom na głównym wątku (bezpieczne dla Bukkit API)
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                callback.onComplete();
                            }
                        }.runTask(plugin.getParent());
                    }
                }
            }
        }.runTaskTimer(plugin.getParent(), 1L, 1L); // Co tick
    }

    /**
     * Wypełnia jedną kolumnę chunka: bedrock + piasek + woda.
     * Kolumny, które już mają cokolwiek na poziomie morza (wodę lub ląd),
     * pomija — żeby nie nadpisywać istniejących wysp i wody.
     */
    private void fillChunkColumn(World world, int chunkX, int chunkZ) {
        int worldX = chunkX << 4;
        int worldZ = chunkZ << 4;
        Chunk chunk = world.getChunkAt(chunkX, chunkZ);
        java.util.Random rng = new java.util.Random();

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int x = worldX + localX;
                int z = worldZ + localZ;

                // Pusta kolumna (void) = brak wody na poziomie morza (Y=WATER_END).
                // Kolumny z wodą lub lądem na poziomie morza zostawiamy w spokoju.
                if (world.getBlockAt(x, WATER_END, z).getType() != Material.AIR) {
                    continue;
                }

                // Bedrock na Y=-61 (jeśli nie ma)
                world.getBlockAt(x, DeepOceanGenerator.BEDROCK_LEVEL, z)
                        .setType(Material.BEDROCK, false);

                // Piasek na Y=-60 (wszędzie)
                world.getBlockAt(x, SAND_BASE, z).setType(Material.SAND, false);
                int topY = SAND_BASE;

                // 45% szans na piasek na Y=-59
                if (rng.nextDouble() < CHANCE_LAYER1) {
                    world.getBlockAt(x, SAND_BASE + 1, z).setType(Material.SAND, false);
                    topY = SAND_BASE + 1;
                    // 18% szans na piasek na Y=-58
                    if (rng.nextDouble() < CHANCE_LAYER2) {
                        world.getBlockAt(x, SAND_BASE + 2, z).setType(Material.SAND, false);
                        topY = SAND_BASE + 2;
                    }
                }

                // Woda od najwyższego piasku+1 do sea-level
                for (int y = topY + 1; y <= WATER_END; y++) {
                    world.getBlockAt(x, y, z).setType(Material.WATER, false);
                }
                // Trawa morska
                if (topY + 1 <= WATER_END && rng.nextDouble() < SEAGRASS_CHANCE) {
                    world.getBlockAt(x, topY + 1, z).setType(Material.SEAGRASS, false);
                }
            }
        }
    }

    /**
     * Synchroniczne wypełnianie — do użycia z małych areas (np. testowo).
     * UWAGA: Blokuje serwer! Używaj tylko na małych obszarach.
     */
    public void fillSync(World world, int centerX, int centerZ, int radiusChunks) {
        for (int cx = centerX - radiusChunks; cx <= centerX + radiusChunks; cx++) {
            for (int cz = centerZ - radiusChunks; cz <= centerZ + radiusChunks; cz++) {
                fillChunkColumn(world, cx, cz);
            }
        }
    }
}
