package pl.vd.raft.core;

/**
 * Fazy dnia w trybie Raft.
 */
public enum GamePhase {
    DAWN,
    DAY,
    DUSK,
    NIGHT;

    public GamePhase next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public boolean isNight() {
        return this == NIGHT;
    }

    public boolean isDangerous() {
        return this == NIGHT || this == DUSK;
    }
}
