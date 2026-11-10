package pl.vd.raft.core;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import pl.vd.raft.RaftPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Dane pojedynczego gracza w trybie Raft.
 */
public class PlayerData {

    /**
     * Utrata pragnienia na sekunde. 20 / 0,02 = 1000 s ≈ 16 min 40 s od pelnego paska do zera.
     * Tempo jest NA SEKUNDE (nie na wywolanie) — faktyczny okres petli podaje GameManager.
     */
    private static final float THIRST_PER_SECOND = 0.02f;

    /**
     * Co ile sekund gracz BEZ WODY traci 1 HP.
     * Wanilia gloduje 1 HP na 80 tickow = 4 s (0,25 HP/s); chcemy ok. 2/3 wolniej,
     * czyli 3x rzadziej: 12 s (0,083 HP/s).
     */
    private static final double DEHYDRATION_DAMAGE_SECONDS = 12.0;

    private final RaftPlugin plugin;
    private final UUID uuid;

    // Statystyki
    private int health = 20;
    private int hunger = 20;
    private float thirst = 20f;

    /** Ile sekund gracz jest juz bez wody (licznik do kolejnego punktu zycia). */
    private double dehydratedSeconds = 0.0;

    // Ekonomia
    private int coins = 0;
    private int reputation = 0;

    // Postęp
    private int level = 1;
    private int experience = 0;
    private int daysSurvived = 0;

    //RAFT
    private int raftLevel = 1;
    private Map<String, Integer> inventory = new HashMap<>();

    public PlayerData(RaftPlugin plugin, UUID uuid) {
        this.plugin = plugin;
        this.uuid = uuid;
        load();
    }

    public void onJoin(Player player) {
        // Przywróć statystyki
        restoreStats(player);
    }

    public void onLeave(Player player) {
        // Zapisz statystyki przed wyjściem
        saveStats(player);
        save();
    }

    /**
     * Jedyne miejsce, w ktorym zmieniaja sie statystyki trybu Raft.
     *
     * @param elapsedSeconds ile sekund gry minelo od poprzedniego wywolania — podaje je
     *        GameManager (faktyczny okres petli). Dzieki temu tempo ponizej jest naprawde
     *        "na sekunde" i zmiana okresu petli nie zmienia po cichu balansu. Wczesniej
     *        bylo "-0.02f" na wywolanie, wiec przestawienie petli na co tick przyspieszylo
     *        by pragnienie 20x (ten sam blad co animacja tlenu w modzie, tylko po stronie serwera).
     */
    public void updateStats(Player player, double elapsedSeconds) {
        // Glod: sync z prawdziwym stanem serwera (efekty, jedzenie, glodzenie wanilii).
        hunger = player.getFoodLevel();

        // Pragnienie: jedyny licznik pragnienia w grze. Picie wody podnosi go w RaftListener
        // (Czysta Woda +20, Brudna Woda -10), a ten drenaz jest jedynym miejscem, w ktorym spada.
        //
        // Wczesniej istnial DRUGI model (RaftHUDManager, skala 0-100), ktory woda rosl
        // i nigdy nie spadal — wiec pasek, ktory widzi gracz (mod, raft:stats), nie reagowal
        // na picie, a kary z tamtego modelu byly nieosiagalne.
        thirst = Math.max(0f, thirst - (float) (THIRST_PER_SECOND * elapsedSeconds));

        // Brak wody = powolna utrata zycia (patrz DEHYDRATION_DAMAGE_SECONDS).
        // CELOWO BEZ ZADNEGO KOMUNIKATU: gracz nie dostaje zadnej informacji na czacie,
        // action barze ani nigdzie indziej — efekt widac wylacznie na paskach HUD.
        if (thirst <= 0f) {
            dehydratedSeconds += elapsedSeconds;
            // while, a nie if: po dluzszej przerwie (lag, /reload) nadrob zalegle punkty.
            while (dehydratedSeconds >= DEHYDRATION_DAMAGE_SECONDS) {
                dehydratedSeconds -= DEHYDRATION_DAMAGE_SECONDS;
                player.damage(1.0);
            }
        } else {
            dehydratedSeconds = 0.0;
        }
    }

    @SuppressWarnings("deprecation")
    private void restoreStats(Player player) {
        // Zabezpieczenie: zapis wykonany po śmierci (health: 0, thirst: 0.0)
        // zabijał gracza NATYCHMIAST po wejściu do trybu Raft (nawet w
        // kreatywnym) — setHealth(0) = natychmiastowa śmierć. Wartości
        // zerowe/nieprawidłowe traktujemy jak świeży start.
        if (health <= 0) health = 20;
        // Pragnienia NIE uzupelniamy do pelna: jest zapisane miedzy sesjami, a darmowe
        // nawodnienie po przelogowaniu kasowaloby cala mechanike (gracz bez wody
        // moglby po prostu wyjsc i wejsc). Poprawiamy tylko wartosci niemozliwe.
        if (Float.isNaN(thirst)) thirst = 20f;
        thirst = Math.max(0f, Math.min(20f, thirst));
        player.setHealth(Math.max(1, Math.min(20, health)));
        player.setFoodLevel(Math.max(0, Math.min(20, hunger)));
        // Inne statystyki...
    }

    private void saveStats(Player player) {
        // Nigdy nie zapisuj 0 HP (stan po śmierci) — odtworzone przy
        // następnym wejściu zabijało gracza w momencie dołączenia.
        health = Math.max(1, (int) player.getHealth());
        hunger = player.getFoodLevel();
    }



    private File getDataFile() {
        return new File(plugin.getParent().getDataFolder(), "raft-players.yml");
    }

    private void load() {
        File file = getDataFile();
        if (!file.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        String path = "players." + uuid.toString();
        if (!cfg.contains(path)) return;

        health = cfg.getInt(path + ".health", 20);
        hunger = cfg.getInt(path + ".hunger", 20);
        thirst = (float) cfg.getDouble(path + ".thirst", 20.0);
        coins = cfg.getInt(path + ".coins", 0);
        reputation = cfg.getInt(path + ".reputation", 0);
        level = cfg.getInt(path + ".level", 1);
        experience = cfg.getInt(path + ".experience", 0);
        daysSurvived = cfg.getInt(path + ".days-survived", 0);
        raftLevel = cfg.getInt(path + ".raft-level", 1);
    }

    public void save() {
        File file = getDataFile();
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        String path = "players." + uuid.toString();

        cfg.set(path + ".health", health);
        cfg.set(path + ".hunger", hunger);
        cfg.set(path + ".thirst", thirst);
        cfg.set(path + ".coins", coins);
        cfg.set(path + ".reputation", reputation);
        cfg.set(path + ".level", level);
        cfg.set(path + ".experience", experience);
        cfg.set(path + ".days-survived", daysSurvived);
        cfg.set(path + ".raft-level", raftLevel);

        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "[Raft] Nie udało się zapisać danych gracza " + uuid, e);
        }
    }

    // Gettery i settery
    public int getHealth() { return health; }
    public void setHealth(int health) { this.health = Math.max(0, Math.min(20, health)); }

    public int getHunger() { return hunger; }
    public void setHunger(int hunger) { this.hunger = Math.max(0, Math.min(20, hunger)); }

    public float getThirst() { return thirst; }
    public void setThirst(float thirst) { this.thirst = Math.max(0, Math.min(20f, thirst)); }

    /**
     * Picie: dodatnia wartosc nawadnia, ujemna odwadnia (brudna woda). Zakres 0-20.
     * Uzywane przez RaftListener — to jest JEDYNA droga, ktora podnosi pragnienie.
     */
    public void addThirst(float delta) {
        setThirst(this.thirst + delta);
    }

    public int getCoins() { return coins; }
    public void addCoins(int amount) { this.coins += amount; }

    public int getReputation() { return reputation; }
    public void addReputation(int amount) { this.reputation += amount; }

    public int getLevel() { return level; }
    public int getExperience() { return experience; }
    public void addExperience(int amount) {
        this.experience += amount;
        while (this.experience >= getExpForNextLevel()) {
            this.experience -= getExpForNextLevel();
            this.level++;
        }
    }

    public int getDaysSurvived() { return daysSurvived; }
    public void addDaySurvived() { this.daysSurvived++; }

    public int getRaftLevel() { return raftLevel; }
    public void upgradeRaft() { this.raftLevel++; }

    private int getExpForNextLevel() {
        return level * 100 + 50;
    }
}
