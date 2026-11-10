package pl.vd.lobby;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import pl.vd.VillagerDefensePlugin;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Zarządza dzienną nagrodą — 24h cooldown, XP ze streakiem.
 *
 * Dane zapisywane w daily_reward.yml:
 *   <uuid>:
 *     lastClaim: <timestamp ms>
 *     totalXp: <suma xp>
 *     streak: <liczba kolejnych dni>
 *     lastClaimDate: <data YYYY-MM-DD>
 *
 * Bonus za streak:
 *   1-10 dni:   +1 XP  (razem: 6)
 *   11-20 dni:  +2 XP  (razem: 7)
 *   21-30 dni:  +3 XP  (razem: 8)
 *   31-50 dni:  +5 XP  (razem: 10)
 *   51-75 dni:  +10 XP (razem: 15)
 *   76-100 dni: +20 XP (razem: 25)
 *   100+ dni:   +30 XP (razem: 35)
 */
public class DailyRewardManager {

    private final VillagerDefensePlugin plugin;
    private final File file;
    private final Map<UUID, Long> lastClaims = new HashMap<>();
    private final Map<UUID, Integer> totalXp = new HashMap<>();
    private final Map<UUID, Integer> streaks = new HashMap<>();
    private final Map<UUID, String> lastClaimDates = new HashMap<>();

    /** Ile XP daje jedna nagroda dzienna (bazowa). */
    private static final int BASE_XP = 5;
    /** Cooldown w milisekundach (24h). */
    private static final long COOLDOWN_MS = 24 * 60 * 60 * 1000L;

    public DailyRewardManager(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "daily_reward.yml");
        load();
    }

    /**
     * Próbuje odebrać dzienną nagrodę.
     * @return true jeśli nagroda została odebrana, false jeśli cooldown trwa
     */
    public boolean claim(Player player) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long last = lastClaims.getOrDefault(uuid, 0L);

        if (now - last < COOLDOWN_MS) {
            return false; // cooldown trwa
        }

        // sprawdź streak
        String today = LocalDate.now().toString();
        String yesterday = LocalDate.now().minusDays(1).toString();
        String lastDate = lastClaimDates.getOrDefault(uuid, "");
        int currentStreak = streaks.getOrDefault(uuid, 0);

        if (lastDate.equals(today)) {
            // już odbierał dziś - nie powinno się zdarzyć (cooldown), ale safety
            return false;
        } else if (lastDate.equals(yesterday)) {
            // odbierał wczoraj - kontynuuj streak
            currentStreak++;
        } else if (lastDate.isEmpty()) {
            // pierwszy raz
            currentStreak = 1;
        } else {
            // przerwa - reset streak
            currentStreak = 1;
        }

        // oblicz bonus
        int bonus = getStreakBonus(currentStreak);
        int totalReward = BASE_XP + bonus;

        // zapisz
        lastClaims.put(uuid, now);
        streaks.put(uuid, currentStreak);
        lastClaimDates.put(uuid, today);

        int currentXp = totalXp.getOrDefault(uuid, 0);
        int newXp = currentXp + totalReward;
        totalXp.put(uuid, newXp);

        // zapisz do PlayerData (settings) jako backup
        var data = plugin.getPlayerDataManager().get(uuid, player.getName());
        data.setSetting("daily_reward_xp", String.valueOf(newXp));
        data.setSetting("daily_reward_last", String.valueOf(now));
        data.setSetting("daily_reward_streak", String.valueOf(currentStreak));
        plugin.getPlayerDataManager().saveAll();

        save();
        return true;
    }

    /**
     * Oblicza bonus XP za streak.
     */
    public int getStreakBonus(int streak) {
        if (streak >= 100) return 30;
        if (streak >= 76) return 20;
        if (streak >= 51) return 10;
        if (streak >= 31) return 5;
        if (streak >= 21) return 3;
        if (streak >= 11) return 2;
        if (streak >= 1) return 1;
        return 0;
    }

    /**
     * Zwraca tekst opisujący bonus za streak.
     */
    public String getStreakInfo(int streak) {
        if (streak >= 100) return "§6§lLEGENDA §7(100+ dni): §a+30 XP";
        if (streak >= 76) return "§5§lMISTRZ §7(76-99 dni): §a+20 XP";
        if (streak >= 51) return "§9§lEKSPERT §7(51-75 dni): §a+10 XP";
        if (streak >= 31) return "§b§lWETERAN §7(31-50 dni): §a+5 XP";
        if (streak >= 21) return "§a§lZAAWANSOWANY §7(21-30 dni): §a+3 XP";
        if (streak >= 11) return "§e§lŚREDNI §7(11-20 dni): §a+2 XP";
        if (streak >= 1) return "§f§lPOCZĄTKUJĄCY §7(1-10 dni): §a+1 XP";
        return "§7Brak streaka";
    }

    /**
     * Zwraca tekst na ekran (np. do GUI).
     */
    public String getStreakDisplay(Player player) {
        int streak = getStreak(player);
        int bonus = getStreakBonus(streak);
        int nextBonus = getStreakBonus(streak + 1);
        String nextRank = getNextRankName(streak);

        StringBuilder sb = new StringBuilder();
        sb.append("§7Streak: §e").append(streak).append(" §7dni\n");
        sb.append("§7Bonus: §a+").append(bonus).append(" XP\n");
        if (nextBonus > bonus && nextRank != null) {
            sb.append("§7Następny rank: §e").append(nextRank).append("\n");
            sb.append("§7Za: §f").append(getDaysToNextRank(streak)).append(" §7dni");
        }
        return sb.toString();
    }

    /**
     * Zwraca nazwę następnego ranku.
     */
    private String getNextRankName(int currentStreak) {
        if (currentStreak < 10) return "ŚREDNI (11 dni)";
        if (currentStreak < 20) return "ZAAWANSOWANY (21 dni)";
        if (currentStreak < 30) return "WETERAN (31 dni)";
        if (currentStreak < 50) return "EKSPERT (51 dni)";
        if (currentStreak < 75) return "MISTRZ (76 dni)";
        if (currentStreak < 100) return "LEGENDA (100 dni)";
        return null; // max rank
    }

    /**
     * Zwraca ile dni do następnego ranku.
     */
    private int getDaysToNextRank(int currentStreak) {
        if (currentStreak < 10) return 11 - currentStreak;
        if (currentStreak < 20) return 21 - currentStreak;
        if (currentStreak < 30) return 31 - currentStreak;
        if (currentStreak < 50) return 51 - currentStreak;
        if (currentStreak < 75) return 76 - currentStreak;
        if (currentStreak < 100) return 101 - currentStreak;
        return 0;
    }

    /**
     * Sprawdza czy gracz może odebrać nagrodę.
     */
    public boolean canClaim(Player player) {
        long now = System.currentTimeMillis();
        long last = lastClaims.getOrDefault(player.getUniqueId(), 0L);
        return (now - last) >= COOLDOWN_MS;
    }

    /**
     * Zwraca czas do następnej nagrody w milisekundach.
     */
    public long getRemainingMs(Player player) {
        long now = System.currentTimeMillis();
        long last = lastClaims.getOrDefault(player.getUniqueId(), 0L);
        long remaining = COOLDOWN_MS - (now - last);
        return Math.max(0, remaining);
    }

    /**
     * Zwraca formatted time do następnej nagrody.
     */
    public String getRemainingTime(Player player) {
        long ms = getRemainingMs(player);
        if (ms <= 0) return "§aGotowe!";
        long hours = ms / (1000 * 60 * 60);
        long minutes = (ms % (1000 * 60 * 60)) / (1000 * 60);
        long seconds = (ms % (1000 * 60)) / 1000;
        return String.format("§c%dh %dm %ds", hours, minutes, seconds);
    }

    /**
     * Zwraca łączną ilość XP gracza.
     */
    public int getTotalXp(Player player) {
        return totalXp.getOrDefault(player.getUniqueId(), 0);
    }

    /**
     * Zwraca aktualny streak gracza.
     */
    public int getStreak(Player player) {
        return streaks.getOrDefault(player.getUniqueId(), 0);
    }

    /**
     * Zwraca total XP do wyświetlenia (z bonusem za streak).
     */
    public int getTotalXpWithBonus(Player player) {
        return totalXp.getOrDefault(player.getUniqueId(), 0);
    }

    // ===== PERSISTENCE =====

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        var section = cfg.getConfigurationSection("players");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                long last = section.getLong(key + ".lastClaim", 0L);
                int xp = section.getInt(key + ".totalXp", 0);
                int streak = section.getInt(key + ".streak", 0);
                String lastDate = section.getString(key + ".lastClaimDate", "");
                lastClaims.put(uuid, last);
                totalXp.put(uuid, xp);
                streaks.put(uuid, streak);
                lastClaimDates.put(uuid, lastDate);
            } catch (Exception ignored) {}
        }
        plugin.getLogger().log(Level.INFO, "Załadowano dane daily reward dla {0} graczy.", lastClaims.size());
    }

    private void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, Long> e : lastClaims.entrySet()) {
            UUID uuid = e.getKey();
            String path = "players." + uuid.toString();
            cfg.set(path + ".lastClaim", e.getValue());
            cfg.set(path + ".totalXp", totalXp.getOrDefault(uuid, 0));
            cfg.set(path + ".streak", streaks.getOrDefault(uuid, 0));
            cfg.set(path + ".lastClaimDate", lastClaimDates.getOrDefault(uuid, ""));
        }
        try {
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Nie udało się zapisać daily_reward.yml", ex);
        }
    }
}
