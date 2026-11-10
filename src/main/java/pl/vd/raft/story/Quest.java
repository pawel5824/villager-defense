package pl.vd.raft.story;

/**
 * Quest w trybie Raft.
 */
public class Quest {

    private final String name;
    private final String description;
    private final QuestType type;
    private final int reward;

    public Quest(String name, String description, QuestType type, int reward) {
        this.name = name;
        this.description = description;
        this.type = type;
        this.reward = reward;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public QuestType getType() { return type; }
    public int getReward() { return reward; }
}
