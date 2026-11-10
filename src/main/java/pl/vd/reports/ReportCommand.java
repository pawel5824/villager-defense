package pl.vd.reports;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Komenda /report [typ] [nick_gracza] [treść]
 *
 * Typy:
 *   gracz  / player / cheater / oszust  → zgłoszenie oszustwa gracza
 *   błąd  / blad / bug / error          → zgłoszenie błędu
 *   inne  / other                        → inne zgłoszenie
 *
 * Przykłady:
 *   /report gracz xXGraczXx uzywa fly na arenie
 *   /report błąd nie moge kupić wieżyczki w sklepie
 *   /report inne propozycja nowego kitu
 *   /report xXGraczXx spamuje na czacie     ← automatycznie "gracz"
 */
public class ReportCommand implements CommandExecutor, TabCompleter {

    private final ReportManager manager;

    public ReportCommand(ReportManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender, label);
            return true;
        }

        String reporter;
        if (sender instanceof Player p) {
            reporter = p.getName();
        } else {
            reporter = "KONSOLA";
        }

        // ustal typ i treść
        String type;
        String target = "";
        String message;

        String firstArg = args[0];
        String parsedType = ReportManager.parseType(firstArg);

        if (parsedType != null) {
            // /report <typ> [nick] [treść]
            type = parsedType;

            if (args.length >= 3 && isPlayerName(args[1])) {
                // /report gracz NickGracza treść...
                target = args[1];
                message = joinArgs(args, 2);
            } else if (args.length >= 2) {
                // /report błąd treść...
                message = joinArgs(args, 1);
            } else {
                sendUsage(sender, label);
                return true;
            }
        } else {
            // /report <nick_gracza> treść...  → automatycznie typ "gracz"
            type = "gracz";
            if (args.length >= 2) {
                target = firstArg;
                message = joinArgs(args, 1);
            } else {
                // /report treść...  → typ "inne"
                type = "inne";
                target = "";
                message = joinArgs(args, 0);
            }
        }

        if (message == null || message.isEmpty()) {
            sendUsage(sender, label);
            return true;
        }

        manager.addReport(type, reporter, target, message);

        String icon = switch (type) {
            case "gracz" -> "§c⚠ Raport gracza";
            case "błąd" -> "§6🐛 Raport błędu";
            default -> "§7📝 Raport";
        };
        String targetPart = !target.isEmpty() ? " §7→ §e" + target : "";
        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize(
                icon + " §awysłany!" + targetPart + " §8| §f" + message));
        return true;
    }

    /** Łączy argumenty od danego indeksu spacją. */
    private String joinArgs(String[] args, int from) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < args.length; i++) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(args[i]);
        }
        return sb.toString();
    }

    /** Sprawdza czy argument wygląda jak nick gracza (alphanum + podkreślenia, 3-16 znaków). */
    private boolean isPlayerName(String s) {
        return s != null && s.matches("[a-zA-Z0-9_]{3,16}") && ReportManager.parseType(s) == null;
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize(
                "§6Użycie: §f/" + label + " <typ> [nick] <treść>"));
        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize(
                "§7Typy: §fgracz §7(oszustwo), §fbłąd §7(bug), §finne"));
        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize(
                "§7Przykłady:"));
        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize(
                "  §f/" + label + " gracz xXGraczXx uzywa fly"));
        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize(
                "  §f/" + label + " błąd nie moge kupić wieżyczki"));
        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize(
                "  §f/" + label + " inne propozycja nowego kitu"));
        sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize(
                "  §f/" + label + " xXGraczXx spamuje §7→ automatycznie typ 'gracz'"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(Arrays.asList("gracz", "błąd", "inne", "player", "bug", "error"));
        } else if (args.length == 2) {
            String type = ReportManager.parseType(args[0]);
            if ("gracz".equals(type)) {
                // tylko przy typie "gracz": podpowiedzi nicków online
                for (Player p : sender.getServer().getOnlinePlayers()) {
                    out.add(p.getName());
                }
            }
        }
        return out;
    }
}
