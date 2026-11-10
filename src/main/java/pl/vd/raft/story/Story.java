package pl.vd.raft.story;

import java.util.ArrayList;
import java.util.List;

/**
 * Główna fabuła gry Raft.
 */
public class Story {

    private final List<Chapter> chapters = new ArrayList<>();
    private int currentChapter = 0;

    public void addChapter(Chapter chapter) {
        chapters.add(chapter);
    }

    public Chapter getCurrentChapter() {
        if (currentChapter < chapters.size()) {
            return chapters.get(currentChapter);
        }
        return null;
    }

    public void advanceChapter() {
        if (currentChapter < chapters.size() - 1) {
            currentChapter++;
        }
    }

    public List<Chapter> getChapters() {
        return chapters;
    }

    public int getCurrentChapterIndex() {
        return currentChapter;
    }

    public boolean isComplete() {
        return currentChapter >= chapters.size() - 1;
    }
}
