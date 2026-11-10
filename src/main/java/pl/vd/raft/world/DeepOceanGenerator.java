package pl.vd.raft.world;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Random;

/**
 * Generator chunków z głębokim oceanem.
 * Generuje:
 * - Bedrock na Y=-61
 * - Piasek na Y=-60 (jedna warstwa wszędzie)
 * - 58% szans na piasek na Y=-59 (wybrzuszenia)
 * - 18% szans na piasek na Y=-58 (wyższe wybrzuszenia)
 * - Trawa morska 65% szans na różnych wysokościach
 * - Sea pickle z różną liczbą sztuk (2-4) na dnie
 * - Woda wypełnia resztę do sea-level (Y=80)
 *
 * Dzięki temu createWorld() jest szybkie, a świat nie waży dużo.
 */
public class DeepOceanGenerator extends ChunkGenerator {

    /** Poziom dna oceanu (bedrock). */
    public static final int BEDROCK_LEVEL = -61;

    /** Poziom morza (water surface). */
    public static final int SEA_LEVEL = 80;

    /** Szansa na trawę morską nad najwyższym piaskiem (0-1). */
    public static final double SEAGRASS_CHANCE = 0.47;

    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }

    @Override
    public @Nullable ChunkData generateChunkData(@NotNull World world, @NotNull Random random,
                                                  int chunkX, int chunkZ, @NotNull BiomeGrid biome) {
        ChunkData data = createChunkData(world);
        int minWorld = world.getMinHeight();
        int maxWorld = world.getMaxHeight();

        // Wymuś DEEP_OCEAN na całym chunku — bez tego Minecraft
        // przypisuje losowe biomy z seeda (lasy, pustynie itp.)
        for (int bx = 0; bx < 16; bx++) {
            for (int bz = 0; bz < 16; bz++) {
                for (int y = minWorld; y < maxWorld; y++) {
                    biome.setBiome(bx, y, bz, Biome.DEEP_OCEAN);
                }
            }
        }

        // Wypełnij chunk: bedrock + piasek + woda
        // Bez tego nowe chunki miałyby pustkę — WaterFiller działa tylko
        // na chunkach istniejących przy starcie, więc eksploracja dalej
        // od centrum generowała puste chunki.
        for (int bx = 0; bx < 16; bx++) {
            for (int bz = 0; bz < 16; bz++) {
                // 1. Bedrock na dnie (Y=-61)
                if (BEDROCK_LEVEL >= minWorld) {
                    data.setBlock(bx, BEDROCK_LEVEL, bz, Material.BEDROCK);
                }
                // 2. Piasek na Y=-60 (jedna warstwa wszędzie)
                int sandY = BEDROCK_LEVEL + 1;
                data.setBlock(bx, sandY, bz, Material.SAND);
                int topY = sandY;
                // 3. 58% szans na piasek na Y=-59
                if (random.nextDouble() < 0.58) {
                    data.setBlock(bx, sandY + 1, bz, Material.SAND);
                    topY = sandY + 1;
                    // 4. 27% szans na piasek na Y=-58
                    if (random.nextDouble() < 0.27) {
                        data.setBlock(bx, sandY + 2, bz, Material.SAND);
                        topY = sandY + 2;
                    }
                }
                // 5. Woda od najwyższego piasku+1 do sea-level
                for (int y = topY + 1; y <= SEA_LEVEL; y++) {
                    data.setBlock(bx, y, bz, Material.WATER);
                }
                // 7. Trawa morska
                if (topY + 1 <= SEA_LEVEL && random.nextDouble() < SEAGRASS_CHANCE) {
                    data.setBlock(bx, topY + 1, bz, Material.SEAGRASS);
                }
            }
        }

        return data;
    }
}
