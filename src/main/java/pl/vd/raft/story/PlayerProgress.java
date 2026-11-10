package pl.vd.raft.story;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Postęp gracza w fabule i questach.
 */
public class PlayerProgress {

    private final UUID uuid;
    private int currentChapter = 0;
    private final Set<String> completedQuests = new HashSet<>();
    private final List<String> activeQuests = new ArrayList<>();
    private int experience = 0;
    private int reputation = 0;

    public PlayerProgress(UUID uuid) {
        this.uuid = uuid;
    }

    public void advanceChapter() {
        currentChapter++;
    }

    public void completeQuest(String questId) {
        completedQuests.add(questId);
        activeQuests.remove(questId);
    }

    public void addActiveQuest(String questId) {
        activeQuests.add(questId);
    }

    public void addExperience(int amount) {
        experience += amount;
    }

    public void addReputation(int amount) {
        reputation += amount;
    }

    public boolean hasCompletedQuest(String questId) {
        return completedQuests.contains(questId);
    }

    public boolean hasActiveQuest(String questId) {
        return activeQuests.contains(questId);
    }

    // Gettery
    public UUID getUuid() { return uuid; }
    public int getCurrentChapter() { return currentChapter; }
    public Set<String> getCompletedQuests() { return completedQuests; }
    public List<String> getActiveQuests() { return activeQuests; }
    public int getExperience() { return experience; }
    public int getReputation() { return reputation; }
}
