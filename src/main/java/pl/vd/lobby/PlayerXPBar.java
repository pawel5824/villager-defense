package pl.vd.lobby;

import org.bukkit.entity.Player;
import pl.vd.VillagerDefensePlugin;

/**
 * Stały pasek XP gracza (vanilla XP bar na dole ekranu).
 * Zmienia się TYLKO przez daily reward.
 *
 * Level: 0-100 (co 10 XP = 1 poziom)
 * Progress: 0.0 - 1.0 (wypełnienie paska)
 */
public class PlayerXPBar {

    private final VillagerDefensePlugin plugin;

    /** Maksymalny poziom XP. */
    private static final int MAX_LEVEL = 100;
    /** Ile XP na poziom. */
    private static final int XP_PER_LEVEL = 10;

    public PlayerXPBar(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Pokazuje/odświeża pasek XP dla gracza.
     */
    public void show(Player player) {
        int xp = plugin.getDailyRewardManager().getTotalXp(player);
        int level = Math.min(MAX_LEVEL, xp / XP_PER_LEVEL);
        double progress = (double) (xp % XP_PER_LEVEL) / XP_PER_LEVEL;

        player.setLevel(level);
        player.setExp((float) Math.max(0.0, Math.min(1.0, progress)));
    }

    /**
     * Ukrywa pasek XP gracza.
     */
    public void hide(Player player) {
        player.setLevel(0);
        player.setExp(0f);
    }

    /**
     * Odświeża pasek (np. po odebraniu nagrody).
     */
    public void refresh(Player player) {
        show(player);
    }
}
