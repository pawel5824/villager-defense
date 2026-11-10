package pl.vd.reports;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Menedżer raportów: zapisuje zgłoszenia do pliku <folder danych pluginu>/raporty.log
 * i umożliwia odczyt ostatnich raportów (w grze lub z pliku).
 *
 * Format linii: [yyyy-MM-dd HH:mm:ss] TYP | ZGŁASZający | CEL | TREŚĆ
 * TYP: gracz / błąd / inne
 */
public class ReportManager {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int PER_PAGE = 10;

    /** Plik w katalogu danych pluginu (plugins/ServerPlugin), nie pod stara nazwa. */
    private final File file;

    public ReportManager(JavaPlugin plugin) {
        this.file = new File(plugin.getDataFolder(), "raporty.log");
    }

    /** Parsuje typ z argumentu komendy (aliasy). */
    public static String parseType(String input) {
        if (input == null) return null;
        String low = input.toLowerCase();
        return switch (low) {
            case "gracz", "player", "cheater", "oszust" -> "gracz";
            case "błąd", "blad", "bug", "error" -> "błąd";
            case "inne", "other" -> "inne";
            default -> null; // nie jest typem - to nick gracza lub treść
        };
    }

    /** Zapisuje raport do pliku i informuje online'owych adminów. */
    public void addReport(String type, String reporter, String target, String message) {
        String timestamp = LocalDateTime.now().format(FMT);
        String line = "[" + timestamp + "] " + type + " | " + reporter + " | " + target + " | " + message;

        // zapis do pliku
        try {
            File dir = file.getParentFile();
            if (!dir.exists()) dir.mkdirs();
            Files.write(file.toPath(),
                    (line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            Bukkit.getLogger().warning("[VillagerDefense] Nie udało się zapisać raportu: " + e.getMessage());
        }

        // powiadom adminów online
        String colored = colorize(type, reporter, target, message);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("vd.admin")) {
                p.sendMessage(LegacyComponentSerializer.legacySection().deserialize(colored));
            }
        }
    }

    /** Formatuje raport kolorami dla czatu. */
    private String colorize(String type, String reporter, String target, String message) {
        String icon = switch (type) {
            case "gracz" -> "§c⚠ GRACZ";
            case "błąd" -> "§6🐛 BŁĄD";
            default -> "§7📝 INNE";
        };
        String targetPart = (target != null && !target.isEmpty()) ? " §7→ §e" + target : "";
        return icon + " §7od §f" + reporter + targetPart + " §8| §f" + message;
    }

    /** Odczytuje ostatnie N linii z pliku raportów. */
    public List<String> getRecent(int count) {
        if (!file.exists()) return List.of();
        try {
            List<String> all = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            int from = Math.max(0, all.size() - count);
            return new ArrayList<>(all.subList(from, all.size()));
        } catch (IOException e) {
            return List.of();
        }
    }

    /** Zwraca liczbę wszystkich raportów w pliku. */
    public int totalCount() {
        if (!file.exists()) return 0;
        try {
            return (int) Files.lines(file.toPath()).count();
        } catch (IOException e) {
            return 0;
        }
    }

    /** Wypisuje raporty na czacie gracza/konsoli (paginacja). */
    public void showReports(CommandSender sender, int page) {
        if (sender == null) return;
        List<String> all = getRecent(100);
        int total = all.size();
        int totalPages = Math.max(1, (total + PER_PAGE - 1) / PER_PAGE);
        page = Math.max(1, Math.min(page, totalPages));

        String header = "§6=== Raporty §7(strona §e" + page + "§7/" + totalPages + "§7, łącznie §e" + total + "§7) ===";
        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize(header));

        if (total == 0) {
            sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§7Brak raportów."));
            return;
        }

        int from = total - (page * PER_PAGE);
        int to = Math.min(from + PER_PAGE, total);
        if (from < 0) from = 0;

        for (int i = from; i < to; i++) {
            sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize(
                    "§8" + (i + 1) + ". §f" + all.get(i)));
        }

        // nawigacja (paginacja)
        if (totalPages > 1) {
            String nav = "§7[";
            nav += page > 1 ? "§a<< " + (page - 1) : "§8<<";
            nav += "§7] §7[";
            nav += page < totalPages ? "§a>> " + (page + 1) : "§8>>";
            nav += "§7]";
            sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize(nav));
        }
    }
}
