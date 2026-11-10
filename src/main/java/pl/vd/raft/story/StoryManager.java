package pl.vd.raft.story;

import org.bukkit.entity.Player;
import pl.vd.raft.RaftPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Zarządza fabułą i questami w trybie Raft.
 * Własna historia o przetrwaniu na oceanie po tajemniczej katastrofie.
 */
public class StoryManager {

    private final RaftPlugin plugin;
    private final Map<UUID, PlayerProgress> playerProgress = new HashMap<>();
    private final Map<String, Quest> availableQuests = new HashMap<>();

    // Główna fabuła
    private final Story mainStory = new Story();

    public StoryManager(RaftPlugin plugin) {
        this.plugin = plugin;
        initializeStory();
        initializeQuests();
    }

    /**
     * Inicjalizuje główną fabułę gry.
     */
    private void initializeStory() {
        // Akt I: Budzenie się
        mainStory.addChapter(new Chapter("Budzenie się",
            "Budzisz się na małym pontonie pośrodku oceanu. " +
            "Nie pamiętasz, jak się tu znalazłeś. " +
            "Wokoło tylko woda i skrawki lądu w oddali. " +
            "Musisz przetrwać."
        ));

        // Akt II: Pierwsze wyspy
        mainStory.addChapter(new Chapter("Pierwsze wyspy",
            "Dopływasz do pierwszych wysp. " +
            "Odkrywasz, że nie jesteś sam - na wyspach są ślady innych rozbitków. " +
            "Niektórzy przyjaźni, inni... mniej."
        ));

        // Akt III: Tajemnica oceanu
        mainStory.addChapter(new Chapter("Tajemnica oceanu",
            "Zaczynasz odkrywać, że ocean skrywa tajemnice. " +
            "Stworzenia z głębin, starożytne ruiny, zaginione skarby. " +
            "Coś wielkiego czeka na dnie..."
        ));

        // Akt IV: Stare bóstwo
        mainStory.addChapter(new Chapter("Stare bóstwo",
            "Odkrywasz prawdę - ocean to cmentarzysko starożytnej cywilizacji. " +
            "Ich bóstwo budzi się z letargu. " +
            "Musisz znaleźć sposób, by je powstrzymać lub z nim walczyć."
        ));

        // Akt V: Ostateczna konfrontacja
        mainStory.addChapter(new Chapter("Ostateczna konfrontacja",
            "Stajesz twarzą w twarz z pradawnym złem. " +
            "Twoje przetrwanie zależy od tego, czego się nauczyłeś. " +
            "Ocean jest twoim domem - i twoim więzieniem."
        ));
    }

    /**
     * Inicjalizuje dostępne questy.
     */
    private void initializeQuests() {
        // Questy głównej fabuły
        availableQuests.put("awakening", new Quest(
            "Budzenie się",
            "Przeżyj pierwszą noc na pontonie.",
            QuestType.MAIN,
            100
        ));

        availableQuests.put("first_island", new Quest(
            "Pierwsza wyspa",
            "Dopłyń do pierwszej wyspy i ją eksploruj.",
            QuestType.MAIN,
            200
        ));

        availableQuests.put("craft_tool", new Quest(
            "Pierwsze narzędzie",
            "Stwórz swoje pierwsze narzędzie.",
            QuestType.MAIN,
            150
        ));

        availableQuests.put("build_shelter", new Quest(
            "Schronienie",
            "Zbuduj schronienie na pontonie.",
            QuestType.MAIN,
            250
        ));

        availableQuests.put("find_survivor", new Quest(
            "Inny rozbitkowie",
            "Znajdź innego rozbitka na wyspie.",
            QuestType.MAIN,
            300
        ));

        // Questy poboczne
        availableQuests.put("collect_fish", new Quest(
            "Połowy",
            "Złap 10 ryb.",
            QuestType.SIDE,
            50
        ));

        availableQuests.put("explore_5_islands", new Quest(
            "Eksplorator",
            "Eksploruj 5 różnych wysp.",
            QuestType.SIDE,
            400
        ));

        availableQuests.put("defeat_shark", new Quest(
            "Pogromca rekinów",
            "Pokonaj rekina.",
            QuestType.SIDE,
            350
        ));

        availableQuests.put("build_compound", new Quest(
            "Budowniczy",
            "Rozbuduj ponton do poziomu 3.",
            QuestType.SIDE,
            500
        ));

        // Questy bossowe
        availableQuests.put("sea_monster", new Quest(
            "Potwór morski",
            "Pokonaj potwora morskiego.",
            QuestType.BOSS,
            1000
        ));

        availableQuests.put("pirate_lord", new Quest(
            "Władca piratów",
            "Pokonaj władcę piratów.",
            QuestType.BOSS,
            1500
        ));

        availableQuests.put("elder_god", new Quest(
            "Pradawne bóstwo",
            "Pokonaj pradawne bóstwo oceanu.",
            QuestType.BOSS,
            2000
        ));
    }

    /**
     * Rozpoczyna quest dla gracza.
     */
    public boolean startQuest(Player player, String questId) {
        Quest quest = availableQuests.get(questId);
        if (quest == null) return false;

        PlayerProgress progress = getOrCreateProgress(player);
        if (progress.hasCompletedQuest(questId)) return false;
        if (progress.hasActiveQuest(questId)) return false;

        progress.addActiveQuest(questId);
        // Bez komunikatu na czacie (AGENTS.md pkt 1) — celowo.

        return true;
    }

    /**
     * Sprawdza postęp questu.
     */
    public void checkQuestProgress(Player player, String questId) {
        PlayerProgress progress = getOrCreateProgress(player);
        if (!progress.hasActiveQuest(questId)) return;

        Quest quest = availableQuests.get(questId);
        if (quest == null) return;

        // TODO: Sprawdź warunki ukończenia questu
        // Np. czy gracz ma wystarczającą ilość ryb, czy eksplorował wyspy itp.
    }

    /**
     * Kończy quest i daje nagrodę.
     */
    public void completeQuest(Player player, String questId) {
        PlayerProgress progress = getOrCreateProgress(player);
        if (!progress.hasActiveQuest(questId)) return;

        Quest quest = availableQuests.get(questId);
        if (quest == null) return;

        progress.completeQuest(questId);
        progress.addExperience(quest.getReward());

        // Bez komunikatu na czacie (AGENTS.md pkt 1) — celowo.
    }

    /**
     * Wyświetla aktualny rozdział fabuły.
     */
    public void showCurrentChapter(Player player) {
        // Bez komunikatu na czacie (AGENTS.md pkt 1) — celowo.
    }

    /**
     * Przechodzi do następnego rozdziału.
     */
    public void advanceChapter(Player player) {
        PlayerProgress progress = getOrCreateProgress(player);
        progress.advanceChapter();
        // Bez komunikatu na czacie (AGENTS.md pkt 1) — celowo.
    }

    private PlayerProgress getOrCreateProgress(Player player) {
        return playerProgress.computeIfAbsent(
            player.getUniqueId(),
            k -> new PlayerProgress(player.getUniqueId())
        );
    }

    public Map<String, Quest> getAvailableQuests() {
        return availableQuests;
    }

    public Story getMainStory() {
        return mainStory;
    }

}
