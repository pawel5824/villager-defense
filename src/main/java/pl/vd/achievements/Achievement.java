package pl.vd.achievements;

import org.bukkit.entity.Player;
import pl.vd.VillagerDefensePlugin;
import pl.vd.player.PlayerData;

/** Pojedyncze osiągnięcie: definicja + postęp liczony ze statystyk gracza. */
public class Achievement {

    /** Statystyka, z której liczony jest postęp osiągnięcia. */
    public enum Stat {
        KILLS,        // łączne zabójstwa (PlayerData.getTotalKills)
        GAMES,        // liczba rozegranych gier (PlayerData.getGamesPlayed)
        HIGHEST_WAVE, // najwyższa przetrwana fala (PlayerData.getHighestWave)
        POINTS,       // łączne punkty (PlayerData.getTotalPoints)
        NONE          // statystyka jeszcze nie śledzona (postęp 0, do podpięcia później)
    }

    private final String id;
    private final AchievementCategory category;
    private final String nameKey;
    private final String descKey;
    private final Stat stat;
    private final int target;
    private final int tier; // poziom (0 = brak; nazwa/opis formatowane z {0}=tier, {1}=target)
    private final VillagerDefensePlugin plugin;

    public Achievement(VillagerDefensePlugin plugin, String id, AchievementCategory category,
                       String nameKey, String descKey, Stat stat, int target) {
        this(plugin, id, category, nameKey, descKey, stat, target, 0);
    }

    public Achievement(VillagerDefensePlugin plugin, String id, AchievementCategory category,
                       String nameKey, String descKey, Stat stat, int target, int tier) {
        this.plugin = plugin;
        this.id = id;
        this.category = category;
        this.nameKey = nameKey;
        this.descKey = descKey;
        this.stat = stat;
        this.target = target;
        this.tier = tier;
    }

    public String getId() { return id; }
    public AchievementCategory getCategory() { return category; }
    public String getNameKey() { return nameKey; }
    public String getDescKey() { return descKey; }
    public int getTarget() { return target; }
    public int getTier() { return tier; }

    /** Aktualna wartość postępu gracza (np. łączna liczba zabójstw). */
    public int getCurrent(Player player) {
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId(), player.getName());
        switch (stat) {
            case KILLS:        return data.getTotalKills();
            case GAMES:        return data.getGamesPlayed();
            case HIGHEST_WAVE: return data.getHighestWave();
            case POINTS:       return data.getTotalPoints();
            default:           return 0;
        }
    }

    /** Procent postępu 0-100 (osiągnięcia bez śledzonej statystyki mają 0%). */
    public int getPercent(Player player) {
        if (target <= 0) return 0;
        return Math.min(100, (int) Math.round(100.0 * getCurrent(player) / target));
    }

    /** Czy osiągnięcie zostało ukończone (100%)? */
    public boolean isUnlocked(Player player) {
        return getPercent(player) >= 100;
    }
}
