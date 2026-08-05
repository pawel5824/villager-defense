package pl.vd.player;

import java.util.UUID;

public class PlayerData {

    private final UUID uuid;
    private String lastKnownName;
    private int totalPoints;
    private int totalKills;
    private int gamesPlayed;
    private int highestWave;

    // punkty "do wydania" w sklepie w trakcie obecnej gry - obsługiwane w Arena (sessionPoints),
    // to pole to punkty skumulowane na koncie gracza (do rankingu /vd top)
    public PlayerData(UUID uuid, String lastKnownName) {
        this.uuid = uuid;
        this.lastKnownName = lastKnownName;
    }

    public UUID getUuid() { return uuid; }

    public String getLastKnownName() { return lastKnownName; }
    public void setLastKnownName(String lastKnownName) { this.lastKnownName = lastKnownName; }

    public int getTotalPoints() { return totalPoints; }
    public int getTotalKills() { return totalKills; }
    public int getGamesPlayed() { return gamesPlayed; }
    public int getHighestWave() { return highestWave; }

    public void addPoints(int amount) { totalPoints += amount; }
    public void addKills(int amount) { totalKills += amount; }
    public void addGamePlayed() { gamesPlayed++; }

    public void updateHighestWave(int wave) {
        if (wave > highestWave) highestWave = wave;
    }
}
