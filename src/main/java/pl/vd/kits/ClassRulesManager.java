package pl.vd.kits;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import pl.vd.VillagerDefensePlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Czyta plik {@code class-rules.yml}: co każda klasa może, a czego nie.
 *
 * Etap 1 - tylko definicje i sprawdzanie pliku. Nic tu nie blokuje zakupu ani
 * użycia przedmiotu; klasy nadal grają tak jak dotąd. Blokady podłączymy w
 * etapie 2 (sklep + listenery), korzystając z {@link #allowsUse} i
 * {@link #allowsPurchase}.
 *
 * Nowa klasa nie wymaga zmian w kodzie: wystarczy sekcja w YAML. Błędny wpis
 * (nieznana klasa, grupa albo materiał) daje jedno ostrzeżenie w logu i jest
 * pomijany - zamiast wywalać start serwera.
 */
public class ClassRulesManager {

    public static final String FILE_NAME = "class-rules.yml";
    public static final int SUPPORTED_VERSION = 1;

    private final VillagerDefensePlugin plugin;
    private final Map<String, ClassRule> rules = new LinkedHashMap<>();
    private final Map<String, Set<Material>> groups = new LinkedHashMap<>();

    public ClassRulesManager(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
    }

    /** Wczytuje albo przeładowuje plik (start serwera, /def reload). */
    public void load() {
        rules.clear();
        groups.clear();

        File file = new File(plugin.getDataFolder(), FILE_NAME);
        if (!file.exists()) {
            plugin.getLogger().warning("Brak " + FILE_NAME + " - klasy działają bez ograniczeń.");
            return;
        }

        YamlConfiguration cfg = new YamlConfiguration();
        try {
            cfg.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().warning("Nie można wczytać " + FILE_NAME + ": " + e.getMessage());
            return;
        }

        int version = cfg.getInt("version", 0);
        if (version != SUPPORTED_VERSION) {
            plugin.getLogger().warning(FILE_NAME + ": wersja " + version
                    + ", oczekiwana " + SUPPORTED_VERSION + " - czytam mimo to.");
        }

        loadGroups(cfg.getConfigurationSection("groups"));
        loadClasses(cfg.getConfigurationSection("classes"));

        plugin.getLogger().info("Zasady klas: wczytano " + rules.size() + " klas z " + FILE_NAME + ".");
    }

    private void loadGroups(ConfigurationSection section) {
        if (section == null) return;
        for (String name : section.getKeys(false)) {
            Set<Material> materials = new LinkedHashSet<>();
            for (String raw : section.getStringList(name)) {
                Material material = Material.matchMaterial(raw);
                if (material == null) {
                    warn("groups." + name, "nieznany materiał '" + raw + "'");
                    continue;
                }
                materials.add(material);
            }
            groups.put(name.toLowerCase(Locale.ROOT), materials);
        }
    }

    private void loadClasses(ConfigurationSection section) {
        if (section == null) return;
        for (String kitId : section.getKeys(false)) {
            ConfigurationSection classSection = section.getConfigurationSection(kitId);
            if (classSection == null) continue;

            if (plugin.getKitManager() != null && plugin.getKitManager().getKit(kitId) == null) {
                warn("classes." + kitId, "nieznana klasa (brak w KitManager) - pomijam");
                continue;
            }

            String path = "classes." + kitId;
            ClassRule rule = new ClassRule(
                    kitId,
                    resolveMaterials(classSection.getStringList("use-allow"), path + ".use-allow"),
                    resolveMaterials(classSection.getStringList("use-except"), path + ".use-except"),
                    resolveMaterials(classSection.getStringList("use-deny"), path + ".use-deny"),
                    classSection.getStringList("shop-allow"),
                    classSection.getStringList("shop-deny"));
            rules.put(kitId.toLowerCase(Locale.ROOT), rule);
        }
    }

    /** Zamienia listę materiałów i "@grup" na zbiór materiałów. */
    private Set<Material> resolveMaterials(List<String> rawValues, String path) {
        Set<Material> out = new LinkedHashSet<>();
        for (String raw : rawValues) {
            if (raw == null || raw.isBlank()) continue;
            String value = raw.trim();
            if (value.startsWith("@")) {
                Set<Material> group = groups.get(value.substring(1).toLowerCase(Locale.ROOT));
                if (group == null) {
                    warn(path, "nie ma grupy '" + value + "'");
                    continue;
                }
                out.addAll(group);
            } else {
                Material material = Material.matchMaterial(value);
                if (material == null) {
                    warn(path, "nieznany materiał '" + value + "'");
                    continue;
                }
                out.add(material);
            }
        }
        return out;
    }

    private void warn(String path, String message) {
        plugin.getLogger().warning(FILE_NAME + ": " + path + " - " + message + ".");
    }

    /** Reguła klasy po ID; brak wpisu = wszystko dozwolone. */
    public ClassRule ruleFor(String kitId) {
        if (kitId == null) return ClassRule.allowAll("?");
        ClassRule rule = rules.get(kitId.toLowerCase(Locale.ROOT));
        return rule != null ? rule : ClassRule.allowAll(kitId);
    }

    /** Reguła klasy wybranej przez gracza (brak wyboru = domyślny wojownik). */
    public ClassRule ruleFor(Player player) {
        return ruleFor(plugin.getKitManager().getChosenKitId(player));
    }

    /** Gotowe pytanie dla etapu 2: czy gracz może użyć tego materiału. */
    public boolean allowsUse(Player player, Material material) {
        return ruleFor(player).allowsUse(material);
    }

    /** Gotowe pytanie dla etapu 2: czy gracz może kupić ten wpis sklepu. */
    public boolean allowsPurchase(Player player, String itemId) {
        return ruleFor(player).allowsPurchase(itemId);
    }

    public Map<String, ClassRule> getRules() {
        return Collections.unmodifiableMap(rules);
    }

    public Set<String> knownGroups() {
        return Collections.unmodifiableSet(groups.keySet());
    }

    /** Podgląd dla administratora (/def klasy). */
    public List<String> describe() {
        List<String> lines = new ArrayList<>();
        lines.add("§6Zasady klas (" + FILE_NAME + "): §7" + rules.size() + " klas, "
                + groups.size() + " grup");
        if (rules.isEmpty()) {
            lines.add("§7Brak wpisów - wszystko dozwolone.");
        }
        for (ClassRule rule : rules.values()) {
            lines.add(rule.describe());
        }
        lines.add("§8Etap 1: same definicje - blokady w sklepie i przy użyciu jeszcze nie działają.");
        return lines;
    }
}
