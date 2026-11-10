package pl.vd.commands;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import pl.vd.VillagerDefensePlugin;
import pl.vd.brush.BrushSettings;
import pl.vd.brush.BrushType;

import java.util.ArrayList;
import java.util.List;

public class BrushCommand implements CommandExecutor, TabCompleter {

    private final VillagerDefensePlugin plugin;

    public BrushCommand(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("§cTa komenda działa tylko w grze."));
            return true;
        }
        if (!player.hasPermission("vd.admin")) {
            pl.vd.util.AntiSpam.send(player, "§cBrak uprawnień.");
            return true;
        }
        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        BrushSettings s = plugin.getBrushManager().getSettings(player);
        String sub = args[0].toLowerCase();

        switch (sub) {
            case "give" -> {
                plugin.getBrushManager().giveTool(player);
                pl.vd.util.AntiSpam.send(player, "§aOtrzymujesz Pędzel Budowlany.");
            }
            case "sphere" -> {
                if (args.length < 2) { pl.vd.util.AntiSpam.send(player, "§cUżyj: /brush sphere <material> [promień]"); return true; }
                Material mat = matchMaterial(player, args[1]);
                if (mat == null) return true;
                s.setType(BrushType.SPHERE);
                s.setPrimaryMaterial(mat);
                if (args.length >= 3) s.setRadius(parseIntOr(args[2], s.getRadius()));
                pl.vd.util.AntiSpam.send(player, "§aBrush: §fKula §7(" + mat + ", promień " + s.getRadius() + ")");
            }
            case "smooth" -> {
                s.setType(BrushType.SMOOTH);
                if (args.length >= 2) s.setRadius(parseIntOr(args[1], s.getRadius()));
                if (args.length >= 3) s.setSmoothIterations(parseIntOr(args[2], s.getSmoothIterations()));
                pl.vd.util.AntiSpam.send(player, "§aBrush: §fWygładzanie §7(promień " + s.getRadius()
                        + ", iteracje " + s.getSmoothIterations() + ")");
            }
            case "random" -> {
                if (args.length < 2) { pl.vd.util.AntiSpam.send(player, "§cUżyj: /brush random <mat1,mat2,...> [promień]"); return true; }
                List<Material> mats = new ArrayList<>();
                for (String part : args[1].split(",")) {
                    Material m = matchMaterial(player, part);
                    if (m == null) return true;
                    mats.add(m);
                }
                s.setType(BrushType.RANDOM);
                s.setRandomMaterials(mats);
                if (args.length >= 3) s.setRadius(parseIntOr(args[2], s.getRadius()));
                pl.vd.util.AntiSpam.send(player, "§aBrush: §fLosowy §7(" + mats.size() + " materiałów, promień " + s.getRadius() + ")");
            }
            case "overlay" -> {
                if (args.length < 3) { pl.vd.util.AntiSpam.send(player, "§cUżyj: /brush overlay <rdzeń> <warstwa_wierzchnia> [promień] [grubość]"); return true; }
                Material core = matchMaterial(player, args[1]);
                Material top = matchMaterial(player, args[2]);
                if (core == null || top == null) return true;
                s.setType(BrushType.OVERLAY);
                s.setPrimaryMaterial(core);
                s.setTopMaterial(top);
                if (args.length >= 4) s.setRadius(parseIntOr(args[3], s.getRadius()));
                if (args.length >= 5) s.setOverlayThickness(parseIntOr(args[4], s.getOverlayThickness()));
                pl.vd.util.AntiSpam.send(player, "§aBrush: §fNakładka §7(rdzeń " + core + ", wierzch " + top
                        + ", promień " + s.getRadius() + ", grubość " + s.getOverlayThickness() + ")");
            }
            case "radius" -> {
                if (args.length < 2) { pl.vd.util.AntiSpam.send(player, "§cUżyj: /brush radius <liczba>"); return true; }
                s.setRadius(parseIntOr(args[1], s.getRadius()));
                pl.vd.util.AntiSpam.send(player, "§aPromień ustawiony na: " + s.getRadius());
            }
            case "info" -> {
                player.sendMessage(Component.text("§6=== Ustawienia pędzla ==="));
                player.sendMessage(Component.text("§7Typ: §f" + s.getType()));
                player.sendMessage(Component.text("§7Promień: §f" + s.getRadius()));
                player.sendMessage(Component.text("§7Materiał główny: §f" + s.getPrimaryMaterial()));
                player.sendMessage(Component.text("§7Materiał wierzchni: §f" + s.getTopMaterial()));
            }
            case "undo" -> {
                if (!plugin.getBrushManager().canUndo(player)) {
                    pl.vd.util.AntiSpam.send(player, "§cBrak akcji do cofnięcia.");
                } else {
                    int count = plugin.getBrushManager().undo(player);
                    int remaining = plugin.getBrushManager().undoCount(player);
                    pl.vd.util.AntiSpam.send(player, "§aCofnięto akcję (" + count + " bloków). Pozostało cofnięć: "
                            + remaining + "/20");
                }
            }
            default -> sendHelp(player);
        }
        return true;
    }

    private Material matchMaterial(Player player, String name) {
        Material mat = Material.matchMaterial(name);
        if (mat == null) {
            pl.vd.util.AntiSpam.send(player, "§cNieznany materiał: " + name);
        }
        return mat;
    }

    private int parseIntOr(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("§6=== /brush - Pędzel Budowlany ==="));
        player.sendMessage(Component.text("§f/brush give §7- otrzymaj narzędzie (drewniana motyka)"));
        player.sendMessage(Component.text("§f/brush sphere <materiał> [promień] §7- pełna kula"));
        player.sendMessage(Component.text("§f/brush smooth [promień] [iteracje] §7- wygładzanie terenu"));
        player.sendMessage(Component.text("§f/brush random <mat1,mat2,...> [promień] §7- losowy rozkład bloków"));
        player.sendMessage(Component.text("§f/brush overlay <rdzeń> <wierzch> [promień] [grubość] §7- kula z warstwą wierzchnią"));
        player.sendMessage(Component.text("§f/brush radius <liczba> §7- szybka zmiana promienia"));
        player.sendMessage(Component.text("§f/brush undo §7- cofnij ostatnią akcję pędzla"));
        player.sendMessage(Component.text("§f/brush info §7- pokaż aktualne ustawienia"));
        player.sendMessage(Component.text("§7Kliknij PPM w blok trzymając pędzel, aby go użyć."));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(List.of("give", "sphere", "smooth", "random", "overlay", "radius", "undo", "info"));
        }
        return options;
    }
}
