package pl.vd.player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerData {

    private final UUID uuid;
    private String lastKnownName;
    private int totalPoints;
    private int totalKills;
    private int gamesPlayed;
    private int highestWave;

    // osobne ustawienia gracza (np. dźwięki, cząsteczki, powiadomienia) -
    // klucz -> wartość, zapisywane w players.yml i odczytywane w grze
    private final Map<String, String> settings = new HashMap<>();

    // punkty "do wydania" w sklepie w trakcie obecnej gry - obsługiwane w Arena (sessionPoints),
    // to pole to punkty skumulowane na koncie gracza (do rankingu /def top)
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

    // ---------- osobne ustawienia gracza ----------

    /** Wartość ustawienia po kluczu (null, gdy nie ustawiono). */
    public String getSetting(String key) { return settings.get(key); }

    /** Wartość ustawienia po kluczu z domyślną, gdy brak wpisu. */
    public String getSetting(String key, String def) { return settings.getOrDefault(key, def); }

    /** Czy ustawienie ma wartość „on" (włączone)? Brak wpisu = false. */
    public boolean isSettingOn(String key) { return "on".equalsIgnoreCase(settings.get(key)); }

    /** Czy ustawienie ma wartość „on"? Brak wpisu = wartość domyślna def. */
    public boolean isSettingOn(String key, boolean def) {
        String v = settings.get(key);
        return v == null ? def : "on".equalsIgnoreCase(v);
    }

    /** Włącza/wyłącza ustawienie („on"/„off"). */
    public void setSettingOn(String key, boolean on) { settings.put(key, on ? "on" : "off"); }

    /** Wartość liczbowa ustawienia (np. % głośności dźwięków), albo def przy
     *  braku wpisu lub niepoprawnej wartości. */
    public int getSettingInt(String key, int def) {
        String v = settings.get(key);
        if (v == null) return def;
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** Ustawia liczbową wartość ustawienia. */
    public void setSettingInt(String key, int value) { settings.put(key, String.valueOf(value)); }

    /** Ustawia dowolną wartość ustawienia. */
    public void setSetting(String key, String value) { settings.put(key, value); }

    /** Wszystkie ustawienia gracza (do zapisu w players.yml). */
    public Map<String, String> getSettings() { return settings; }
}
