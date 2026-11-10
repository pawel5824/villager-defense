package pl.vd.raft.story;

/**
 * Rozdział fabuły.
 */
public class Chapter {

    private final String title;
    private final String content;
    private boolean completed;

    public Chapter(String title, String content) {
        this.title = title;
        this.content = content;
        this.completed = false;
    }

    public String getTitle() { return title; }
    public String getContent() { return content; }
    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }
}
