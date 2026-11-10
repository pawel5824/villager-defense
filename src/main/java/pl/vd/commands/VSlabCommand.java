package pl.vd.commands;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.joml.Vector3f;
import pl.vd.VillagerDefensePlugin;

import java.util.ArrayList;
import java.util.List;

public class VSlabCommand implements CommandExecutor, TabCompleter {

    private final VillagerDefensePlugin plugin;

    public VSlabCommand(VillagerDefensePlugin plugin) {
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
        if (args.length < 1) {
            sendHelp(player);
            return true;
        }

        // --- podkomendy usuwania wizualnych płytek ---
        String sub = args[0].toLowerCase();
        if (sub.equals("undo")) {
            if (plugin.getVerticalSlabManager().undo()) {
                pl.vd.util.AntiSpam.send(player, "§aUsunięto ostatnią wizualną płytkę.");
            } else {
                pl.vd.util.AntiSpam.send(player, "§cNie ma żadnej wizualnej płytki do usunięcia.");
            }
            return true;
        }
        if (sub.equals("clear")) {
            int count = plugin.getVerticalSlabManager().clearAll();
            pl.vd.util.AntiSpam.send(player, "§aUsunięto " + count + " wizualnych płytek.");
            return true;
        }
        // /vslab clearall [blok] - usuwa wszystkie płytki (opcjonalnie tylko z danego bloku)
        if (sub.equals("clearall") || sub.equals("czyscwszystko")) {
            Material mat = null;
            if (args.length >= 2) {
                mat = Material.matchMaterial(args[1]);
                if (mat == null || !mat.isBlock()) {
                    pl.vd.util.AntiSpam.send(player, "§cNieznany blok: " + args[1] + " §7(użyj: /vslab clearall [blok])");
                    return true;
                }
            }
            int count = mat != null
                    ? plugin.getVerticalSlabManager().clearAll(mat)
                    : plugin.getVerticalSlabManager().clearAll();
            if (mat != null) {
                pl.vd.util.AntiSpam.send(player, "§aUsunięto " + count + " płytek z bloku " + mat + ".");
            } else {
                pl.vd.util.AntiSpam.send(player, "§aUsunięto " + count + " wizualnych płytek.");
            }
            return true;
        }
        if (sub.equals("offset")) {
            if (args.length >= 4) {
                Double x = parseDoubleOr(args[1], null);
                Double y = parseDoubleOr(args[2], null);
                Double z = parseDoubleOr(args[3], null);
                if (x == null || y == null || z == null) {
                    pl.vd.util.AntiSpam.send(player, "§cUżyj: /vslab offset <x> <y> <z>");
                    return true;
                }
                plugin.getVerticalSlabManager().setOffset(x, y, z);
                pl.vd.util.AntiSpam.send(player, "§aOffset stawiania ustawiony: " + x + " " + y + " " + z);
                return true;
            }
            if (args.length == 2 && args[1].equalsIgnoreCase("reset")) {
                plugin.getVerticalSlabManager().setOffset(0, 0, 0);
                pl.vd.util.AntiSpam.send(player, "§aOffset stawiania wyzerowany.");
                return true;
            }
            Vector3f o = plugin.getVerticalSlabManager().getOffset();
            pl.vd.util.AntiSpam.send(player, "§aObecny offset: " + o.x + " " + o.y + " " + o.z
                    + " §7(użyj: /vslab offset <x> <y> <z> albo /vslab offset reset)");
            return true;
        }
        if (sub.equals("remove") || sub.equals("usun")) {
            Location target;
            Material mat = null;
            int idx = 1;
            // opcjonalna nazwa bloku na początku: /vslab remove stone [x y z]
            if (args.length >= 2) {
                Material maybe = Material.matchMaterial(args[1]);
                if (maybe != null && maybe.isBlock()) {
                    mat = maybe;
                    idx = 2;
                }
            }
            if (args.length - idx >= 3) {
                // /vslab remove [blok] <x> <y> <z> - konkretne koordynaty, bez patrzenia
                Double x = parseDoubleOr(args[idx], null);
                Double y = parseDoubleOr(args[idx + 1], null);
                Double z = parseDoubleOr(args[idx + 2], null);
                if (x == null || y == null || z == null) {
                    pl.vd.util.AntiSpam.send(player, "§cUżyj: /vslab remove [blok] [x y z]");
                    return true;
                }
                target = new Location(player.getWorld(), x, y, z);
            } else {
                // /vslab remove [blok] - usuwa płytkę w bloku, na który gracz patrzy
                Block looked = player.getTargetBlockExact(100);
                if (looked == null) {
                    pl.vd.util.AntiSpam.send(player, "§cNie patrzysz na żaden blok w zasięgu - podaj koordynaty: /vslab remove [blok] <x> <y> <z>");
                    return true;
                }
                target = looked.getLocation();
            }
            boolean removed = mat != null
                    ? plugin.getVerticalSlabManager().removeAt(target, mat)
                    : plugin.getVerticalSlabManager().removeAt(target);
            if (removed) {
                pl.vd.util.AntiSpam.send(player, "§aUsunięto wizualną płytkę"
                        + (mat != null ? " (" + mat + ")" : "") + " w "
                        + target.getBlockX() + ", " + target.getBlockY() + ", " + target.getBlockZ());
            } else {
                pl.vd.util.AntiSpam.send(player, "§cW tym miejscu nie ma wizualnej płytki"
                        + (mat != null ? " z bloku " + mat : "") + ".");
            }
            return true;
        }

        // --- /vslab add <blok> [x y z] [obrót] - jawna komenda stawiania ---
        if (sub.equals("add") || sub.equals("dodaj")) {
            if (args.length < 2) {
                pl.vd.util.AntiSpam.send(player, "§cUżyj: /vslab add <blok> [x y z] [obrót]");
                return true;
            }
            Material mat = Material.matchMaterial(args[1]);
            if (mat == null || !mat.isBlock()) {
                pl.vd.util.AntiSpam.send(player, "§cNieznany blok: " + args[1]);
                return true;
            }
            Location target;
            String rotArg;
            if (args.length >= 5) {
                // add <blok> <x> <y> <z> [obrót] - konkretne koordynaty, bez patrzenia
                Double x = parseDoubleOr(args[2], null);
                Double y = parseDoubleOr(args[3], null);
                Double z = parseDoubleOr(args[4], null);
                if (x == null || y == null || z == null) {
                    pl.vd.util.AntiSpam.send(player, "§cUżyj: /vslab add <blok> [x y z] [obrót]");
                    return true;
                }
                target = new Location(player.getWorld(), x, y, z);
                rotArg = args.length >= 6 ? args[5] : null;
            } else {
                // add <blok> [obrót] - blok, na który gracz patrzy
                Block looked = player.getTargetBlockExact(100);
                if (looked == null) {
                    pl.vd.util.AntiSpam.send(player, "§cNie patrzysz na żaden blok w zasięgu - podaj koordynaty: /vslab add <blok> <x> <y> <z>");
                    return true;
                }
                target = looked.getLocation();
                rotArg = args.length >= 3 ? args[2] : null;
            }
            Float rotation = parseFloatOr(rotArg, 0f);
            rotation = ((rotation % 360f) + 360f) % 360f;

            // jednorazowe ox:/oy:/oz: albo domyślny offset z /vslab offset
            Vector3f base = plugin.getVerticalSlabManager().getOffset();
            Float ox = parseFloatOr(valueOf(args, "ox:"), null);
            Float oy = parseFloatOr(valueOf(args, "oy:"), null);
            Float oz = parseFloatOr(valueOf(args, "oz:"), null);
            Vector3f effective = (ox != null || oy != null || oz != null)
                    ? new Vector3f(base) : base;
            if (ox != null) effective.x = ox;
            if (oy != null) effective.y = oy;
            if (oz != null) effective.z = oz;

            plugin.getVerticalSlabManager().place(target, mat, BlockFace.SOUTH, rotation, effective);
            String rotText = rotation != 0f ? ", obrót: " + rotation + "°" : "";
            String offText = (effective.x != 0 || effective.y != 0 || effective.z != 0)
                    ? ", offset: " + effective.x + "/" + effective.y + "/" + effective.z : "";
            pl.vd.util.AntiSpam.send(player, "§aPostawiono wizualną płytkę (" + mat
                    + rotText + offText + ") w " + target.getBlockX() + ", " + target.getBlockY() + ", " + target.getBlockZ());
            return true;
        }

        Material material = Material.matchMaterial(args[0]);
        if (material == null || !material.isBlock()) {
            pl.vd.util.AntiSpam.send(player, "§cNieznany blok: " + args[0]);
            return true;
        }

        Location target;
        String faceArg;
        String rotArg;

        if (args.length >= 4) {
            // /vslab <blok> <x> <y> <z> [kierunek] [obrót] - konkretne koordynaty, bez patrzenia
            Double x = parseDoubleOr(args[1], null);
            Double y = parseDoubleOr(args[2], null);
            Double z = parseDoubleOr(args[3], null);
            if (x == null || y == null || z == null) {
                pl.vd.util.AntiSpam.send(player, "§cUżyj: /vslab <blok> [x y z] [kierunek] [obrót]");
                return true;
            }
            target = new Location(player.getWorld(), x, y, z);
            faceArg = args.length >= 5 ? args[4] : null;
            rotArg = args.length >= 6 ? args[5] : null;
        } else {
            // /vslab <blok> [kierunek] [obrót] - bez koordynatów, bierze blok na który gracz patrzy
            Block looked = player.getTargetBlockExact(100);
            if (looked == null) {
                pl.vd.util.AntiSpam.send(player, "§cNie patrzysz na żaden blok w zasięgu - podaj koordynaty: /vslab <blok> <x> <y> <z>");
                return true;
            }
            target = looked.getLocation();
            faceArg = args.length >= 2 ? args[1] : null;
            rotArg = args.length >= 3 ? args[2] : null;
        }

        // Obrót w stopniach. Może stać w miejscu kierunku, np. /vslab stone 45 (bez kierunku).
        float rotation = 0f;
        Float rotFromFace = parseFloatOr(faceArg, null);
        if (rotFromFace != null) {
            rotation = rotFromFace;
            faceArg = null;
        }
        Float rotFromArg = parseFloatOr(rotArg, null);
        if (rotFromArg != null) {
            rotation = rotFromArg;
        }
        rotation = ((rotation % 360f) + 360f) % 360f;

        // Przesunięcie stawiania: jednorazowe ox:/oy:/oz: (np. /vslab stone ox:0.5 oy:0.25)
        // albo domyślny offset z /vslab offset. Dzięki temu można stawiać wszędzie,
        // nie tylko na środku bloku - także na krawędzi.
        Vector3f base = plugin.getVerticalSlabManager().getOffset();
        Float ox = parseFloatOr(valueOf(args, "ox:"), null);
        Float oy = parseFloatOr(valueOf(args, "oy:"), null);
        Float oz = parseFloatOr(valueOf(args, "oz:"), null);
        boolean hasOverride = ox != null || oy != null || oz != null;
        Vector3f effective = hasOverride ? new Vector3f(base) : base;
        if (ox != null) effective.x = ox;
        if (oy != null) effective.y = oy;
        if (oz != null) effective.z = oz;

        BlockFace face = plugin.getVerticalSlabManager().parseFace(faceArg, BlockFace.SOUTH);
        plugin.getVerticalSlabManager().place(target, material, face, rotation, effective);

        String rotText = rotation != 0f ? ", obrót: " + rotation + "°" : "";
        String offText = (effective.x != 0 || effective.y != 0 || effective.z != 0)
                ? ", offset: " + effective.x + "/" + effective.y + "/" + effective.z : "";
        pl.vd.util.AntiSpam.send(player, "§aPostawiono wizualną pionową płytkę (" + material + ", strona: " + face
                + rotText + offText + ") w " + target.getBlockX() + ", " + target.getBlockY() + ", " + target.getBlockZ());
        return true;
    }

    private String valueOf(String[] args, String prefix) {
        for (String arg : args) {
            if (arg.startsWith(prefix)) {
                return arg.substring(prefix.length());
            }
        }
        return null;
    }

    private Float parseFloatOr(String raw, Float fallback) {
        if (raw == null) return fallback;
        try {
            return Float.parseFloat(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private Double parseDoubleOr(String raw, Double fallback) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("§6=== /vslab - Wizualna pionowa płytka ==="));
        player.sendMessage(Component.text("§f/vslab <blok> §7- postaw na bloku, na który patrzysz"));
        player.sendMessage(Component.text("§f/vslab <blok> <kierunek> §7- jw. + wybrana strona: north/south/east/west"));
        player.sendMessage(Component.text("§f/vslab <blok> <kierunek> <obrót> §7- jw. + obrót w stopniach 0-360"));
        player.sendMessage(Component.text("§f/vslab add <blok> [x y z] [obrót] §7- postaw wizualną płytkę"));
        player.sendMessage(Component.text("§f/vslab offset <x> <y> <z> §7- ustaw przesunięcie stawiania"));
        player.sendMessage(Component.text("§f/vslab <blok> ... ox:0.5 oy:0.25 §7- jednorazowe przesunięcie przy stawianiu"));
        player.sendMessage(Component.text("§f/vslab undo §7- cofnij ostatnią postawioną płytkę"));
        player.sendMessage(Component.text("§f/vslab remove [blok] [x y z] §7- usuń płytkę: bez podawania bierze blok, na który patrzysz; możesz podać blok i/lub koordynaty"));
        player.sendMessage(Component.text("§f/vslab clear §7- usuń wszystkie wizualne płytki we wszystkich światach"));
        player.sendMessage(Component.text("§f/vslab clearall [blok] §7- usuń wszystkie płytki; z podanym blokiem tylko płytki z tego bloku (np. /vslab clearall stone)"));
        player.sendMessage(Component.text("§7To czysto wizualny element - bez prawdziwej kolizji."));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        // Podpowiedzi współrzędnych bloku, na który patrzy gracz (Y = wierzch bloku).
        if (args.length > 0 && (args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("usun"))) {
            String[] coords = targetCoords(sender);
            if (coords != null) {
                boolean hasMat = args.length >= 2
                        && Material.matchMaterial(args[1]) != null
                        && Material.matchMaterial(args[1]).isBlock();
                if (hasMat) {
                    // /vslab remove <blok> x y z
                    if (args.length == 3) return filter(coords[0], args[2]);
                    if (args.length == 4) return filter(coords[1], args[3]);
                    if (args.length == 5) return filter(coords[2], args[4]);
                } else {
                    // /vslab remove x y z
                    if (args.length == 2) return filter(coords[0], args[1]);
                    if (args.length == 3) return filter(coords[1], args[2]);
                    if (args.length == 4) return filter(coords[2], args[3]);
                }
            }
        }

        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(List.of("add", "undo", "remove", "clear", "clearall", "offset"));
        }
        boolean isSub = args.length > 0 && (args[0].equalsIgnoreCase("undo")
                || args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("usun")
                || args[0].equalsIgnoreCase("clear") || args[0].equalsIgnoreCase("clearall")
                || args[0].equalsIgnoreCase("czyscwszystko")
                || args[0].equalsIgnoreCase("offset"));
        boolean isAdd = args.length > 0 && (args[0].equalsIgnoreCase("add")
                || args[0].equalsIgnoreCase("dodaj"));
        if ((args.length == 2 || args.length == 5) && !isSub && !isAdd) {
            options.addAll(List.of("north", "south", "east", "west"));
        }
        if ((args.length == 3 || args.length == 6) && !isSub) {
            options.addAll(List.of("0", "45", "90", "135", "180", "225", "270", "315"));
        }
        return options;
    }

    /** Współrzędne bloku, na który patrzy gracz (X, wierzch bloku Y, Z). */
    private String[] targetCoords(CommandSender sender) {
        if (!(sender instanceof Player player)) return null;
        Block block = player.getTargetBlockExact(100);
        if (block == null) return null;
        return new String[]{
                String.valueOf(block.getX()),
                String.valueOf(block.getY() + 1),
                String.valueOf(block.getZ())
        };
    }

    /** Podpowiedź pojedynczej współrzędnej (z filtrowaniem po wpisanym prefiksie). */
    private List<String> filter(String coord, String partial) {
        if (partial == null || partial.isEmpty() || coord.startsWith(partial)) {
            return java.util.Collections.singletonList(coord);
        }
        return java.util.Collections.emptyList();
    }
}
