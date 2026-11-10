package pl.vd.commands;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import pl.vd.VillagerDefensePlugin;
import pl.vd.region.Region;
import pl.vd.region.RegionManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Komenda /region - zaznaczanie i zapisywanie terenu areny (uprawnienie vd.admin).
 *
 * /region pos1 | pos2              - zaznacz punkt w miejscu, w którym stoisz
 * /region save <nazwa>             - zapisz zaznaczone pudełko jako region
 * /region list                     - lista zapisanych regionów
 * /region info <nazwa>             - wymiary, środek i pozycje regionu
 * /region remove <nazwa>           - usuń region
 * /region daj <nazwa> <material> [ilość] - rzuć przedmiot w środku regionu
 */
public class RegionCommand implements CommandExecutor, TabCompleter {

    private final VillagerDefensePlugin plugin;

    public RegionCommand(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("vd.admin")) {
            sender.sendMessage(Component.text("§cBrak uprawnień."));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(Component.text("§7Użyj: /region <pos1|pos2|save|list|info|remove|daj>"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "pos1", "p1" -> pos(sender, 1);
            case "pos2", "p2" -> pos(sender, 2);
            case "save", "zapisz" -> save(sender, args);
            case "list", "lista" -> list(sender);
            case "info" -> info(sender, args);
            case "remove", "usun", "delete" -> remove(sender, args);
            case "daj", "give" -> give(sender, args);
            default -> sender.sendMessage(Component.text("§cNieznana podkomenda. Użyj: /region <pos1|pos2|save|list|info|remove|daj>"));
        }
        return true;
    }

    private void pos(CommandSender sender, int which) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("§cTa podkomenda działa tylko w grze."));
            return;
        }
        Location loc = player.getLocation();
        RegionManager mgr = plugin.getRegionManager();
        if (which == 1) mgr.setPos1(player, loc);
        else mgr.setPos2(player, loc);
        player.sendMessage(Component.text("§aPunkt " + which + " zaznaczony: §f"
                + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ()
                + " §7(świat: " + loc.getWorld().getName() + ")"));
    }

    private void save(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("§cTa podkomenda działa tylko w grze."));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("§cUżyj: /region save <nazwa>"));
            return;
        }
        String name = args[1];
        RegionManager mgr = plugin.getRegionManager();
        Location p1 = mgr.getPos1(player);
        Location p2 = mgr.getPos2(player);
        if (p1 == null || p2 == null) {
            player.sendMessage(Component.text("§cNajpierw zaznacz oba punkty (/region pos1 i /region pos2)."));
            return;
        }
        if (p1.getWorld() == null || !p1.getWorld().equals(p2.getWorld())) {
            player.sendMessage(Component.text("§cOba punkty muszą być w tym samym świecie."));
            return;
        }
        if (mgr.hasRegion(name)) {
            player.sendMessage(Component.text("§cRegion o nazwie \"" + name + "\" już istnieje. Usuń go najpierw (/region remove)."));
            return;
        }
        Region region = mgr.saveRegion(player, name);
        if (region == null) {
            player.sendMessage(Component.text("§cNie udało się zapisać regionu."));
            return;
        }
        player.sendMessage(Component.text("§aZapisano region §f\"" + region.getName() + "\"§a ("
                + region.sizeX() + "x" + region.sizeY() + "x" + region.sizeZ()
                + " bloków, objętość " + region.volume() + "). Znajdziesz go w regiony.yml."));
    }

    private void list(CommandSender sender) {
        List<Region> regions = plugin.getRegionManager().getRegions();
        if (regions.isEmpty()) {
            sender.sendMessage(Component.text("§7Brak zapisanych regionów. Zaznacz /region pos1 i /region pos2, potem /region save <nazwa>."));
            return;
        }
        sender.sendMessage(Component.text("§7Zapisane regiony (" + regions.size() + "):"));
        for (Region r : regions) {
            sender.sendMessage(Component.text("§f- " + r.getName() + " §7(świat: " + r.getWorld()
                    + ", " + r.sizeX() + "x" + r.sizeY() + "x" + r.sizeZ() + ")"));
        }
    }

    private void info(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("§cUżyj: /region info <nazwa>"));
            return;
        }
        Region region = plugin.getRegionManager().getRegion(args[1]);
        if (region == null) {
            sender.sendMessage(Component.text("§cNie ma regionu \"" + args[1] + "\"."));
            return;
        }
        sender.sendMessage(Component.text("§7Region: §f" + region.getName()));
        sender.sendMessage(Component.text("§7Świat: §f" + region.getWorld()));
        sender.sendMessage(Component.text("§7Punkt 1: §f" + region.getMinX() + ", " + region.getMinY() + ", " + region.getMinZ()));
        sender.sendMessage(Component.text("§7Punkt 2: §f" + region.getMaxX() + ", " + region.getMaxY() + ", " + region.getMaxZ()));
        sender.sendMessage(Component.text("§7Wymiary: §f" + region.sizeX() + "x" + region.sizeY() + "x" + region.sizeZ()
                + " §7(§f" + region.volume() + "§7 bloków)"));
        World w = plugin.getRegionManager().worldByName(region.getWorld());
        Location center = region.center(w);
        if (center != null) {
            sender.sendMessage(Component.text("§7Środek: §f" + center.getBlockX() + ", " + center.getBlockY() + ", " + center.getBlockZ()));
        }
    }

    private void remove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("§cUżyj: /region remove <nazwa>"));
            return;
        }
        if (plugin.getRegionManager().removeRegion(args[1])) {
            sender.sendMessage(Component.text("§aUsunięto region \"" + args[1] + "\"."));
        } else {
            sender.sendMessage(Component.text("§cNie ma regionu \"" + args[1] + "\"."));
        }
    }

    private void give(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("§cUżyj: /region daj <nazwa> <material> [ilość]"));
            return;
        }
        Region region = plugin.getRegionManager().getRegion(args[1]);
        if (region == null) {
            sender.sendMessage(Component.text("§cNie ma regionu \"" + args[1] + "\"."));
            return;
        }
        Material mat = Material.matchMaterial(args[2]);
        if (mat == null) {
            sender.sendMessage(Component.text("§cNie ma materiału \"" + args[2] + "\"."));
            return;
        }
        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("§cTo nie jest liczba: " + args[3]));
                return;
            }
            if (amount < 1 || amount > 64) amount = 1;
        }
        World w = plugin.getRegionManager().worldByName(region.getWorld());
        Location center = region.center(w);
        if (center == null) {
            sender.sendMessage(Component.text("§cŚwiat regionu nie jest załadowany (" + region.getWorld() + ")."));
            return;
        }
        center.getWorld().dropItemNaturally(center, new ItemStack(mat, amount));
        sender.sendMessage(Component.text("§aRzucono §f" + amount + "x " + mat.name().toLowerCase()
                + "§a w środku regionu §f\"" + region.getName() + "\"§a ("
                + center.getBlockX() + ", " + center.getBlockY() + ", " + center.getBlockZ() + ")."));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("vd.admin")) return out;
        if (args.length == 1) {
            out.addAll(List.of("pos1", "pos2", "save", "list", "info", "remove", "daj"));
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("info") || sub.equals("remove") || sub.equals("daj")) {
                for (Region r : plugin.getRegionManager().getRegions()) out.add(r.getName());
            }
        }
        return out;
    }
}
