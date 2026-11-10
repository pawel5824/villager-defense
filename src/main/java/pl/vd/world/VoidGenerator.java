package pl.vd.world;

import org.bukkit.generator.ChunkGenerator;

/**
 * Generator pustego świata - nie generuje absolutnie nic (żadnego terenu, jaskiń,
 * struktur, dekoracji ani mobów podczas generacji chunka). Idealny pod areny,
 * które i tak buduje się ręcznie pędzlem/platformami.
 */
public class VoidGenerator extends ChunkGenerator {

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
}
