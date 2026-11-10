package pl.vd.raft.physics;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * System wiosłowania — zarządza cooldownem, kumulatywnym boostem i dekayem.
 *
 * Zasady:
 *  - Cooldown 1s między uderzeniami
 *  - Każde uderzenie = +1% boosta (max 15%)
 *  - Gdy gracz przestaje wiosłować, boost spada -1% co 2.5s do 0%
 *  - Wiosłowanie działa tylko gdy gracz patrzy na wodę (kąt ≤ 35° w dół)
 */
public final class RowingSystem {

    /** Cooldown między uderzeniami (w tickach = 20 = 1 sekunda). */
    private static final long COOLDOWN_TICKS = 20;

    /** Maksymalny boost w procentach (0.0 - 0.15). */
    private static final double MAX_BOOST = 0.15;

    /** O ile boost spada za dekay tick (0.01 = 1%). */
    private static final double DECAY_AMOUNT = 0.01;

    /** Co ile ticków następuje dekay (50 = 2.5 sekundy). */
    private static final long DECAY_INTERVAL_TICKS = 50;

    /** Maksymalny kąt patrzenia w dół (w stopniach). */
    private static final double MAX_PITCH_DEGREES = 35.0;

    /** Dodatkowa tolerancja — czy gracz musi patrzeć na wodę directly. */
    private static final boolean REQUIRE_WATER_SIGHT = true;

    /** Zasięg sprawdzania wody przed graczem (w blokach). */
    private static final int WATER_CHECK_RANGE = 5;

    private final Map<UUID, RowingPlayerState> playerStates = new HashMap<>();

    // ─────────────────────── STAN GRACZA ───────────────────────

    private static final class RowingPlayerState {
        long lastStrokeTick;       // Tick ostatniego uderzenia
        long lastStrokeTime;       // System time ostatniego uderzenia
        double currentBoost;       // Aktualny boost (0.0 - 0.15)
        long lastDecayTick;        // Tick ostatniego dekaya
        boolean wasRowing;         // Czy w poprzednim ticku wiosłował
    }

    // ─────────────────────── GŁÓWNA METODA ─────────────────────

    /**
     * Wywoływana gdy gracz klika lewym przyciskiem z wiosłem.
     * Zwraca true jeśli wiosłowanie się powiodło (boost nałożony).
     */
    public boolean handleRowStroke(Player player, long currentTick) {
        UUID uuid = player.getUniqueId();
        RowingPlayerState state = playerStates.computeIfAbsent(uuid, k -> createDefault());

        // 1. Cooldown
        if (currentTick - state.lastStrokeTick < COOLDOWN_TICKS) {
            return false; // Za wcześnie
        }

        // 2. Sprawdź kąt kamery (musi patrzeć w dół ≤ 35°)
        float pitch = player.getLocation().getPitch(); // pozytywny = w dół
        if (pitch < MAX_PITCH_DEGREES) {
            return false; // Patrzy za wysoko
        }

        // 3. Sprawdź czy patrzy na wodę
        if (REQUIRE_WATER_SIGHT && !isLookingAtWater(player)) {
            return false; // Nie patrzy na wodę
        }

        // 4. Zwiększ boost
        state.currentBoost = Math.min(MAX_BOOST, state.currentBoost + DECAY_AMOUNT);
        state.lastStrokeTick = currentTick;
        state.lastStrokeTime = System.currentTimeMillis();
        state.wasRowing = true;

        return true;
    }

    /**
     * Wywoływana co tick — aktualizuje dekay boosta.
     * Zwraca aktualny boost (0.0 - 0.15).
     */
    public double tickDecay(UUID uuid, long currentTick) {
        RowingPlayerState state = playerStates.get(uuid);
        if (state == null || state.currentBoost <= 0) return 0;

        // Jeśli gracz nie wiosłował w tym ticku i minęło 2.5s od ostatniego uderzenia
        if (!state.wasRowing && (currentTick - state.lastStrokeTick) >= DECAY_INTERVAL_TICKS) {
            state.currentBoost = Math.max(0, state.currentBoost - DECAY_AMOUNT);
            state.lastDecayTick = currentTick;
        }

        state.wasRowing = false; // Reset na koniec ticka
        return state.currentBoost;
    }

    /**
     * Pobiera aktualny boost gracza (do wyświetlenia w HUD itp.)
     */
    public double getBoost(UUID uuid) {
        RowingPlayerState state = playerStates.get(uuid);
        return state != null ? state.currentBoost : 0;
    }

    /**
     * Zwraca boost jako string procentowy (np. "7%")
     */
    public String getBoostPercent(UUID uuid) {
        double boost = getBoost(uuid);
        int percent = (int) Math.round(boost * 100);
        return percent + "%";
    }

    /**
     * Sprawdza czy gracz może wiosłować (cooldown + kąt + woda).
     * Do wyświetlania feedbacku.
     */
    public boolean canRow(Player player, long currentTick) {
        UUID uuid = player.getUniqueId();
        RowingPlayerState state = playerStates.getOrDefault(uuid, createDefault());

        if (currentTick - state.lastStrokeTick < COOLDOWN_TICKS) return false;
        if (player.getLocation().getPitch() < MAX_PITCH_DEGREES) return false;
        return isLookingAtWater(player);
    }

    /**
     * Usuwa stan gracza (przy wyjściu z serwera).
     */
    public void removePlayer(UUID uuid) {
        playerStates.remove(uuid);
    }

    // ─────────────────────── POMOCNICZE ─────────────────────────

    private RowingPlayerState createDefault() {
        RowingPlayerState state = new RowingPlayerState();
        state.lastStrokeTick = 0;
        state.lastStrokeTime = 0;
        state.currentBoost = 0;
        state.lastDecayTick = 0;
        state.wasRowing = false;
        return state;
    }

    /**
     * Sprawdza czy gracz patrzy na blok wody w zasięgu.
     * Raycast w kierunku patrzenia gracza.
     */
    private boolean isLookingAtWater(Player player) {
        Location eyeLoc = player.getEyeLocation();
        Vector direction = eyeLoc.getDirection();

        for (int i = 1; i <= WATER_CHECK_RANGE; i++) {
            double x = eyeLoc.getX() + direction.getX() * i;
            double y = eyeLoc.getY() + direction.getY() * i;
            double z = eyeLoc.getZ() + direction.getZ() * i;

            Block block = player.getWorld().getBlockAt(
                    (int) Math.floor(x),
                    (int) Math.floor(y),
                    (int) Math.floor(z)
            );

            if (block.getType() == Material.WATER) {
                return true;
            }

            // Jeśli trafiliśmy w solidny blok — przerywamy
            if (block.getType().isSolid()) {
                return false;
            }
        }

        return false; // Nie znaleziono wody w zasięgu
    }
}
