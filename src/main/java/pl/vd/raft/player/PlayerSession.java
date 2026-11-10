package pl.vd.raft.player;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import pl.vd.raft.RaftPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Sesja pojedynczego gracza w trybie Raft.
 * Dane zapisywane do raft-players.yml — przetrwają restart serwera.
 */
public class PlayerSession {

    private final RaftPlugin plugin;
    private final java.util.UUID uuid;

    // Statystyki
    private int daysSurvived = 0;
    private int experience = 0;
    private int level = 1;
    private int coins = 0;
    private int kills = 0;
    private int islandsExplored = 0;

    // Czas gry
    private long joinTime;
    private long totalPlayTime = 0;

    // Flaga: gracz opuscil serwer ESC — po ponownym wejsciu powinien wrocic do lobby
    private boolean returnToLobby = false;

    // Ustawienia gracza (dzwieki, czasteczki, powiadomienia itd.)
    private final Map<String, String> settings = new HashMap<>();

    // Zapisany ekwipunek raft (slot -> przedmiot) — schowany przy wyjściu do
    // lobby, przywracany na te same sloty po ponownym dołączeniu do Raft.
    private final Map<Integer, ItemStack> savedInventory = new HashMap<>();

    public PlayerSession(RaftPlugin plugin, java.util.UUID uuid) {
        this.plugin = plugin;
        this.uuid = uuid;
        load();
    }

    /**
     * Gracz dolacza do gry.
     */
    public void onJoin(Player player) {
        joinTime = System.currentTimeMillis();
        player.sendMessage("§aWitaj z powrotem na oceanie!");
        // Licznik dni nie leci na czat (zasada "wszędzie cicho", patrz AGENTS.md);
        // daysSurvived żyje dalej — /raft fabula oraz statystyki gracza.
        // Przywróć ekwipunek z poprzedniej sesji Raft (te same sloty)
        restoreRaftInventory(player);
    }

    /**
     * Gracz opuszcza gre.
     */
    public void onLeave(Player player) {
        totalPlayTime += System.currentTimeMillis() - joinTime;
        save();
        // Bez komunikatu na czacie (AGENTS.md pkt 1) — celowo.
    }

    /**
     * Dodaj dzien przetrwania.
     */
    public void addDaySurvived() {
        daysSurvived++;
    }

    /**
     * Dodaj doswiadczenie.
     */
    public void addExperience(int amount) {
        experience += amount;
        while (experience >= getExpForNextLevel()) {
            experience -= getExpForNextLevel();
            level++;
        }
    }

    /**
     * Dodaj monety.
     */
    public void addCoins(int amount) {
        coins += amount;
    }

    /**
     * Dodaj zabojstwo.
     */
    public void addKill() {
        kills++;
    }

    /**
     * Dodaj odkryta wyspe.
     */
    public void addIslandExplored() {
        islandsExplored++;
    }

    /**
     * Oblicz exp do nastepnego poziomu.
     */
    private int getExpForNextLevel() {
        return level * 100 + 50;
    }

    // ==================== USTAWIENIA ====================

    /**
     * Pobierz wartosc ustawienia.
     */
    public String getSetting(String key, String defaultValue) {
        return settings.getOrDefault(key, defaultValue);
    }

    /**
     * Ustaw wartosc ustawienia.
     */
    public void setSetting(String key, String value) {
        settings.put(key, value);
        save(); // zapisz od razu
    }

    /**
     * Pobierz wszystkie ustawienia.
     */
    public Map<String, String> getSettings() {
        return settings;
    }

    // ==================== SAVE/LOAD ====================

    private File getDataFile() {
        return new File(plugin.getDataFolder(), "raft-players.yml");
    }

    /**
     * Wczytaj dane z pliku raft-players.yml.
     */
    private void load() {
        File file = getDataFile();
        if (!file.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        String path = "players." + uuid.toString();
        if (!cfg.contains(path)) return;

        daysSurvived = cfg.getInt(path + ".days-survived", 0);
        experience = cfg.getInt(path + ".experience", 0);
        level = cfg.getInt(path + ".level", 1);
        coins = cfg.getInt(path + ".coins", 0);
        kills = cfg.getInt(path + ".kills", 0);
        islandsExplored = cfg.getInt(path + ".islands-explored", 0);
        totalPlayTime = cfg.getLong(path + ".total-play-time", 0);
        returnToLobby = cfg.getBoolean(path + ".return-to-lobby", false);

        // Ustawienia gracza
        var settingsSection = cfg.getConfigurationSection(path + ".settings");
        if (settingsSection != null) {
            for (String key : settingsSection.getKeys(false)) {
                settings.put(key, settingsSection.getString(key, ""));
            }
        }

        // Zapisany ekwipunek raft
        var invSection = cfg.getConfigurationSection(path + ".saved-inventory");
        if (invSection != null) {
            for (String key : invSection.getKeys(false)) {
                try {
                    ItemStack stack = invSection.getItemStack(key);
                    if (stack != null) {
                        savedInventory.put(Integer.parseInt(key), stack);
                    }
                } catch (NumberFormatException ignored) {
                    // niepoprawny klucz slotu — pomiń
                }
            }
        }
    }

    /**
     * Zapisz dane do pliku raft-players.yml.
     */
    public void save() {
        File file = getDataFile();
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        String path = "players." + uuid.toString();

        cfg.set(path + ".days-survived", daysSurvived);
        cfg.set(path + ".experience", experience);
        cfg.set(path + ".level", level);
        cfg.set(path + ".coins", coins);
        cfg.set(path + ".kills", kills);
        cfg.set(path + ".islands-explored", islandsExplored);
        cfg.set(path + ".total-play-time", totalPlayTime);
        cfg.set(path + ".return-to-lobby", returnToLobby);

        // Ustawienia gracza
        for (Map.Entry<String, String> entry : settings.entrySet()) {
            cfg.set(path + ".settings." + entry.getKey(), entry.getValue());
        }

        // Zapisany ekwipunek raft (nadpisz całą sekcję)
        cfg.set(path + ".saved-inventory", null);
        for (Map.Entry<Integer, ItemStack> entry : savedInventory.entrySet()) {
            cfg.set(path + ".saved-inventory." + entry.getKey(), entry.getValue());
        }

        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "[Raft] Nie udało się zapisać danych gracza " + uuid, e);
        }
    }

    // ==================== EKWIPUNEK RAFT (save/restore) ====================

    /**
     * Chowa ekwipunek gracza (zawartość + zbroja + offhand) do zapisu na te
     * same sloty i czyści mu ekwipunek — gracz wychodzi do lobby bez
     * przedmiotów z Raft. Po ponownym dołączeniu do trybu Raft ekwipunek
     * wraca na te same sloty (restoreRaftInventory w onJoin).
     */
    public void saveRaftInventory(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        savedInventory.clear();
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null && contents[i].getType() != Material.AIR) {
                savedInventory.put(i, contents[i].clone());
            }
        }
        player.getInventory().clear();
        save();
    }

    /** Czy gracz ma zapisany ekwipunek z Raft. */
    public boolean hasSavedInventory() {
        return !savedInventory.isEmpty();
    }

    /**
     * Przywraca zapisany ekwipunek na te same sloty (czyści najpierw
     * aktualny) i usuwa zapis — gracz dostaje swoje przedmioty z powrotem.
     */
    public void restoreRaftInventory(Player player) {
        if (savedInventory.isEmpty()) return;
        player.getInventory().clear();
        for (Map.Entry<Integer, ItemStack> entry : savedInventory.entrySet()) {
            player.getInventory().setItem(entry.getKey(), entry.getValue());
        }
        savedInventory.clear();
        save();
    }

    // ==================== RETURN TO LOBBY ====================

    public boolean isReturnToLobby() { return returnToLobby; }
    public void setReturnToLobby(boolean value) { this.returnToLobby = value; }

    // Gettery
    public java.util.UUID getUuid() { return uuid; }
    public int getDaysSurvived() { return daysSurvived; }
    public int getExperience() { return experience; }
    public int getLevel() { return level; }
    public int getCoins() { return coins; }
    public int getKills() { return kills; }
    public int getIslandsExplored() { return islandsExplored; }
    public long getTotalPlayTime() { return totalPlayTime; }
}
