package pl.vd.copy;

import org.bukkit.block.data.BlockData;

/** Pojedynczy blok w schowku kopiuj-wklej, jako pozycja względna od punktu odniesienia. */
public record RelativeBlock(int dx, int dy, int dz, BlockData data) {}
