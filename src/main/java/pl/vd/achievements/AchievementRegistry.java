package pl.vd.achievements;

import pl.vd.VillagerDefensePlugin;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Rejestr wszystkich osiągnięć. Na start 4 osiągnięcia na każdą z 9 kategorii
 * (= 36, dokładnie tyle ile slotów ma środkowe pole GUI) - nazwy i wymagania
 * można swobodnie podmieniać/dodawać.
 */
public class AchievementRegistry {

    private final List<Achievement> all = new ArrayList<>();
    private final Map<AchievementCategory, List<Achievement>> byCategory = new EnumMap<>(AchievementCategory.class);

    public AchievementRegistry(VillagerDefensePlugin plugin) {
        for (AchievementCategory c : AchievementCategory.values()) {
            byCategory.put(c, new ArrayList<>());
        }
        register(plugin);
    }

    private void add(Achievement a) {
        all.add(a);
        byCategory.get(a.getCategory()).add(a);
    }

    /** Dodaje 2 rzędy (18) osiągnięć-poziomów z rosnącymi wymaganiami (stat). */
    private void addTiers(VillagerDefensePlugin plugin, AchievementCategory cat, String nameKey,
                          String descKey, Achievement.Stat stat, int... targets) {
        int tier = 1;
        for (int target : targets) {
            add(new Achievement(plugin, cat.name().toLowerCase() + "-t" + tier, cat, nameKey, descKey, stat, target, tier));
            tier++;
        }
    }

    /** Dodaje 2 rzędy (18) osiągnięć-poziomów bez śledzonej statystyki (wymagania później). */
    private void addTiers(VillagerDefensePlugin plugin, AchievementCategory cat, String nameKey,
                          String descKey, Achievement.Stat stat, int count) {
        for (int tier = 1; tier <= count; tier++) {
            add(new Achievement(plugin, cat.name().toLowerCase() + "-t" + tier, cat, nameKey, descKey, stat, 0, tier));
        }
    }

    private void register(VillagerDefensePlugin plugin) {
        // ---------- Walka (zabójstwa) ----------
        add(new Achievement(plugin, "kills-1", AchievementCategory.WALKA,
                "ach-name-kills-1", "ach-desc-kills-1", Achievement.Stat.KILLS, 1));
        add(new Achievement(plugin, "kills-2", AchievementCategory.WALKA,
                "ach-name-kills-2", "ach-desc-kills-2", Achievement.Stat.KILLS, 100));
        add(new Achievement(plugin, "kills-3", AchievementCategory.WALKA,
                "ach-name-kills-3", "ach-desc-kills-3", Achievement.Stat.KILLS, 500));
        add(new Achievement(plugin, "kills-4", AchievementCategory.WALKA,
                "ach-name-kills-4", "ach-desc-kills-4", Achievement.Stat.KILLS, 1500));

        // ---------- Obrona (wieżyczki, wieśniacy) ----------
        add(new Achievement(plugin, "defend-1", AchievementCategory.OBRONA,
                "ach-name-defend-1", "ach-desc-defend-1", Achievement.Stat.NONE, 0));
        add(new Achievement(plugin, "defend-2", AchievementCategory.OBRONA,
                "ach-name-defend-2", "ach-desc-defend-2", Achievement.Stat.NONE, 0));
        add(new Achievement(plugin, "defend-3", AchievementCategory.OBRONA,
                "ach-name-defend-3", "ach-desc-defend-3", Achievement.Stat.NONE, 0));
        add(new Achievement(plugin, "defend-4", AchievementCategory.OBRONA,
                "ach-name-defend-4", "ach-desc-defend-4", Achievement.Stat.NONE, 0));

        // ---------- Przetrwanie (fale) ----------
        add(new Achievement(plugin, "wave-1", AchievementCategory.PRZETRWANIE,
                "ach-name-wave-1", "ach-desc-wave-1", Achievement.Stat.HIGHEST_WAVE, 5));
        add(new Achievement(plugin, "wave-2", AchievementCategory.PRZETRWANIE,
                "ach-name-wave-2", "ach-desc-wave-2", Achievement.Stat.HIGHEST_WAVE, 25));
        add(new Achievement(plugin, "wave-3", AchievementCategory.PRZETRWANIE,
                "ach-name-wave-3", "ach-desc-wave-3", Achievement.Stat.HIGHEST_WAVE, 50));
        add(new Achievement(plugin, "wave-4", AchievementCategory.PRZETRWANIE,
                "ach-name-wave-4", "ach-desc-wave-4", Achievement.Stat.HIGHEST_WAVE, 100));

        // ---------- Ekonomia (punkty) ----------
        add(new Achievement(plugin, "eco-1", AchievementCategory.EKONOMIA,
                "ach-name-eco-1", "ach-desc-eco-1", Achievement.Stat.POINTS, 100));
        add(new Achievement(plugin, "eco-2", AchievementCategory.EKONOMIA,
                "ach-name-eco-2", "ach-desc-eco-2", Achievement.Stat.POINTS, 1000));
        add(new Achievement(plugin, "eco-3", AchievementCategory.EKONOMIA,
                "ach-name-eco-3", "ach-desc-eco-3", Achievement.Stat.POINTS, 5000));
        add(new Achievement(plugin, "eco-4", AchievementCategory.EKONOMIA,
                "ach-name-eco-4", "ach-desc-eco-4", Achievement.Stat.POINTS, 10000));

        // ---------- Klasy (gry, kity) ----------
        add(new Achievement(plugin, "class-1", AchievementCategory.KLASY,
                "ach-name-class-1", "ach-desc-class-1", Achievement.Stat.GAMES, 1));
        add(new Achievement(plugin, "class-2", AchievementCategory.KLASY,
                "ach-name-class-2", "ach-desc-class-2", Achievement.Stat.GAMES, 25));
        add(new Achievement(plugin, "class-3", AchievementCategory.KLASY,
                "ach-name-class-3", "ach-desc-class-3", Achievement.Stat.GAMES, 100));
        add(new Achievement(plugin, "class-4", AchievementCategory.KLASY,
                "ach-name-class-4", "ach-desc-class-4", Achievement.Stat.NONE, 0));

        // ---------- Łupy (zrzuty, sklep) ----------
        add(new Achievement(plugin, "loot-1", AchievementCategory.LUPY,
                "ach-name-loot-1", "ach-desc-loot-1", Achievement.Stat.NONE, 0));
        add(new Achievement(plugin, "loot-2", AchievementCategory.LUPY,
                "ach-name-loot-2", "ach-desc-loot-2", Achievement.Stat.NONE, 0));
        add(new Achievement(plugin, "loot-3", AchievementCategory.LUPY,
                "ach-name-loot-3", "ach-desc-loot-3", Achievement.Stat.NONE, 0));
        add(new Achievement(plugin, "loot-4", AchievementCategory.LUPY,
                "ach-name-loot-4", "ach-desc-loot-4", Achievement.Stat.NONE, 0));

        // ---------- Eksploracja (areny, dystans) ----------
        add(new Achievement(plugin, "explore-1", AchievementCategory.EKSPLORACJA,
                "ach-name-explore-1", "ach-desc-explore-1", Achievement.Stat.GAMES, 1));
        add(new Achievement(plugin, "explore-2", AchievementCategory.EKSPLORACJA,
                "ach-name-explore-2", "ach-desc-explore-2", Achievement.Stat.NONE, 0));
        add(new Achievement(plugin, "explore-3", AchievementCategory.EKSPLORACJA,
                "ach-name-explore-3", "ach-desc-explore-3", Achievement.Stat.NONE, 0));
        add(new Achievement(plugin, "explore-4", AchievementCategory.EKSPLORACJA,
                "ach-name-explore-4", "ach-desc-explore-4", Achievement.Stat.NONE, 0));

        // ---------- Społeczne (drużyny, gracze) ----------
        add(new Achievement(plugin, "social-1", AchievementCategory.SPOLECZNE,
                "ach-name-social-1", "ach-desc-social-1", Achievement.Stat.NONE, 0));
        add(new Achievement(plugin, "social-2", AchievementCategory.SPOLECZNE,
                "ach-name-social-2", "ach-desc-social-2", Achievement.Stat.NONE, 0));
        add(new Achievement(plugin, "social-3", AchievementCategory.SPOLECZNE,
                "ach-name-social-3", "ach-desc-social-3", Achievement.Stat.NONE, 0));
        add(new Achievement(plugin, "social-4", AchievementCategory.SPOLECZNE,
                "ach-name-social-4", "ach-desc-social-4", Achievement.Stat.NONE, 0));

        // ---------- Poziomy (41) w 6 kategoriach: 4 + 41 = 45 > 36, więc strzałki
        // przewijania działają w kategoriach (▼ na górze, ▼ znika na dole). Społeczne,
        // Łupy i Ukryte zostają bez poziomów (po 4 osiągnięcia, bez strzałek). ----------
        addTiers(plugin, AchievementCategory.WALKA, "ach-tier-walka", "ach-tier-walka-desc", Achievement.Stat.KILLS,
                2500, 5000, 7500, 10000, 15000, 20000, 25000, 30000, 40000, 50000,
                60000, 70000, 80000, 90000, 100000, 125000, 150000, 200000,
                225000, 250000, 275000, 300000, 325000, 350000, 375000, 400000, 425000, 450000,
                475000, 500000, 550000, 600000, 650000, 700000, 750000, 800000, 850000, 900000,
                950000, 1000000, 1100000);
        addTiers(plugin, AchievementCategory.OBRONA, "ach-tier-obrona", "ach-tier-obrona-desc", Achievement.Stat.NONE, 41);
        addTiers(plugin, AchievementCategory.PRZETRWANIE, "ach-tier-przetrwanie", "ach-tier-przetrwanie-desc", Achievement.Stat.HIGHEST_WAVE,
                150, 200, 250, 300, 350, 400, 450, 500, 550, 600,
                650, 700, 750, 800, 850, 900, 950, 1000,
                1050, 1100, 1150, 1200, 1250, 1300, 1350, 1400, 1450, 1500,
                1550, 1600, 1650, 1700, 1750, 1800, 1850, 1900, 1950, 2000,
                2100, 2200, 2300);
        addTiers(plugin, AchievementCategory.EKONOMIA, "ach-tier-ekonomia", "ach-tier-ekonomia-desc", Achievement.Stat.POINTS,
                15000, 20000, 25000, 30000, 40000, 50000, 60000, 70000, 80000, 90000,
                100000, 125000, 150000, 200000, 250000, 300000, 400000, 500000,
                550000, 600000, 650000, 700000, 750000, 800000, 850000, 900000, 950000, 1000000,
                1100000, 1200000, 1300000, 1400000, 1500000, 1600000, 1700000, 1800000, 1900000, 2000000,
                2250000, 2500000, 3000000);
        addTiers(plugin, AchievementCategory.KLASY, "ach-tier-klasy", "ach-tier-klasy-desc", Achievement.Stat.GAMES,
                150, 200, 250, 300, 350, 400, 450, 500, 600, 700,
                800, 900, 1000, 1250, 1500, 2000, 2500, 3000,
                3500, 4000, 4500, 5000, 5500, 6000, 6500, 7000, 7500, 8000,
                8500, 9000, 9500, 10000, 11000, 12000, 13000, 14000, 15000, 16000,
                17000, 18000, 20000);
        addTiers(plugin, AchievementCategory.EKSPLORACJA, "ach-tier-eksploracja", "ach-tier-eksploracja-desc", Achievement.Stat.NONE, 41);
    }

    /** Wszystkie osiągnięcia (widok główny huba). */
    public List<Achievement> getAll() {
        return all;
    }

    /** Osiągnięcia danej kategorii. */
    public List<Achievement> forCategory(AchievementCategory category) {
        return byCategory.getOrDefault(category, List.of());
    }
}
