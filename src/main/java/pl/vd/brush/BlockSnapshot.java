package pl.vd.brush;

import org.bukkit.Location;
import org.bukkit.block.data.BlockData;

/** Zapamiętany stan pojedynczego bloku sprzed zmiany - używany do cofania (undo). */
public record BlockSnapshot(Location location, BlockData blockData) {}
