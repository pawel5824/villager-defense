package pl.vd.raft.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import pl.vd.raft.RaftPlugin;
import pl.vd.raft.entity.RaftEntity;
import pl.vd.raft.entity.RaftManager;

/**
 * RaftCommand - komendy do zarządzania tratwami.
 */
public class RaftCommand implements CommandExecutor, TabCompleter {

    private final RaftManager raftManager;

    public RaftCommand(RaftPlugin plugin) {
        this.raftManager = RaftManager.getInstance();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;

        Player player = (Player) sender;

        if (args.length == 0) return true;

        switch (args[0].toLowerCase()) {
            case "spawn"     -> spawnRaft(player);
            case "remove"    -> removeRaft(player);
            case "list"      -> listRafts(player);
            case "przedmioty", "przedmiot", "item", "items" -> giveItem(player, args);
            case "latarenka", "latarenki", "lantern" -> giveLatarenka(player, args);
            case "zagiel", "żagiel", "sail" -> sailRaft(player, args);
            case "debug"     -> debugRaft(player);
        }
        return true;
    }

    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return java.util.List.of("spawn", "remove", "list", "przedmioty", "latarenka", "zagiel", "debug");
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("zagiel")
                || args[0].equalsIgnoreCase("żagiel") || args[0].equalsIgnoreCase("sail"))) {
            String prefix = args[1].toLowerCase();
            return java.util.List.of("otwarty", "zamkniety").stream()
                    .filter(s -> s.startsWith(prefix)).toList();
        }
        if (args.length == 2 && isPrzedmiotSub(args[0])) {
            pl.vd.raft.items.ItemManager im = pl.vd.raft.RaftPlugin.getInstance().getItemManager();
            if (im != null) {
                String prefix = args[1].toLowerCase();
                return im.getAllItems().keySet().stream()
                        .filter(id -> id.startsWith(prefix))
                        .sorted()
                        .toList();
            }
        }
        if (args.length == 2 && isLatarenkaSub(args[0])) {
            // podpowiadamy i „fioletowa”, i pełne „latarenka_fioletowa”
            java.util.List<String> out = new java.util.ArrayList<>();
            for (pl.vd.raft.items.ItemManager.LatarenkaColor c : pl.vd.raft.items.ItemManager.LATARENKA_COLORS) {
                out.add(c.id());
                out.add("latarenka_" + c.id());
            }
            String prefix = args[1].toLowerCase();
            return out.stream().filter(s -> s.toLowerCase().startsWith(prefix)).sorted().toList();
        }
        return java.util.Collections.emptyList();
    }

    private boolean isPrzedmiotSub(String arg) {
        return arg.equalsIgnoreCase("przedmioty") || arg.equalsIgnoreCase("przedmiot")
                || arg.equalsIgnoreCase("item") || arg.equalsIgnoreCase("items");
    }

    private boolean isLatarenkaSub(String arg) {
        return arg.equalsIgnoreCase("latarenka") || arg.equalsIgnoreCase("latarenki")
                || arg.equalsIgnoreCase("lantern");
    }

    /**
     * /raft latarenka [kolor] [ilość] — daje ZWYKŁĄ waniliową latarenkę z CustomModelData,
     * dzięki czemu klient z packiem raft-hud pokazuje ją w wybranym kolorze.
     * Bez argumentu: lista kolorów.
     */
    private void giveLatarenka(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.GOLD + "=== Latarenki w kolorach ===");
            for (pl.vd.raft.items.ItemManager.LatarenkaColor c : pl.vd.raft.items.ItemManager.LATARENKA_COLORS) {
                player.sendMessage(ChatColor.YELLOW + "- " + c.colorCode() + c.label()
                        + ChatColor.GRAY + "  →  /raft latarenka " + c.id());
            }
            player.sendMessage(ChatColor.GRAY + "Użycie: " + ChatColor.YELLOW + "/raft latarenka <kolor> [ilość]");
            return;
        }

        pl.vd.raft.items.ItemManager.LatarenkaColor color =
                pl.vd.raft.items.ItemManager.findLatarenkaColor(args[1]);
        if (color == null) {
            player.sendMessage(ChatColor.RED + "Nieznany kolor: " + args[1]);
            player.sendMessage(ChatColor.GRAY + "Wpisz " + ChatColor.YELLOW + "/raft latarenka"
                    + ChatColor.GRAY + ", żeby zobaczyć listę.");
            return;
        }

        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[2])));
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Ilość musi być liczbą (1-64).");
                return;
            }
        }

        pl.vd.raft.items.ItemManager im = pl.vd.raft.RaftPlugin.getInstance().getItemManager();
        if (im == null) return;
        ItemStack item = im.createItem("latarenka_" + color.id());
        if (item == null) {
            player.sendMessage(ChatColor.RED + "Nie udało się stworzyć latarenki (brak w ItemManager).");
            return;
        }
        handOver(player, item, amount,
                ChatColor.GREEN + "Otrzymałeś: " + color.colorCode() + "latarenka_" + color.id()
                        + ChatColor.GRAY + " x" + amount + " (CMD " + color.customModelData() + ")");
    }

    /**
     * /raft przedmiot <nazwa> [ilość]  (aliasy: przedmioty, item, items)
     * Działa też dla latarenek: /raft przedmiot latarenka_fioletowa 3
     */
    private void giveItem(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.GRAY + "Użycie: /raft przedmiot <nazwa> [ilość]");
            return;
        }
        pl.vd.raft.items.ItemManager im = pl.vd.raft.RaftPlugin.getInstance().getItemManager();
        if (im == null) return;
        ItemStack item = im.createItem(args[1].toLowerCase());
        if (item == null) {
            player.sendMessage(ChatColor.RED + "Nieznany przedmiot: " + args[1]);
            // podpowiedź dla latarenek, bo to najczęstsza pomyłka w nazwie
            pl.vd.raft.items.ItemManager.LatarenkaColor likely =
                    pl.vd.raft.items.ItemManager.findLatarenkaColor(args[1]);
            if (likely != null) {
                player.sendMessage(ChatColor.GRAY + "Miałeś na myśli " + ChatColor.YELLOW
                        + "latarenka_" + likely.id() + ChatColor.GRAY + "? Spróbuj: "
                        + ChatColor.YELLOW + "/raft latarenka " + likely.id());
            }
            return;
        }

        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[2])));
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Ilość musi być liczbą (1-64).");
                return;
            }
        }

        String name = item.getItemMeta() != null ? item.getItemMeta().getDisplayName() : args[1];
        handOver(player, item, amount,
                ChatColor.GREEN + "Otrzymałeś: " + name + ChatColor.GRAY + " x" + amount);
    }

    /** Wrzuca przedmiot do ekwipunku; nadmiar ląduje na ziemi. */
    private void handOver(Player player, ItemStack item, int amount, String successMessage) {
        item.setAmount(amount);
        java.util.HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        if (!leftover.isEmpty()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover.get(0));
            player.sendMessage(ChatColor.YELLOW + "Ekwipunek pełny — przedmiot wyrzucony na ziemię.");
        } else {
            player.sendMessage(successMessage);
        }
    }

    private void spawnRaft(Player player) {
        raftManager.spawnRaftForPlayer(player, 16);
    }

    private void removeRaft(Player player) {
        RaftEntity nearest = raftManager.getNearestRaft(player, 32.0);
        if (nearest != null) {
            raftManager.removeRaft(nearest.getRaftId());
            player.sendMessage(ChatColor.GREEN + "Usunięto tratwę.");
        } else {
            player.sendMessage(ChatColor.GRAY + "Brak tratwy w pobliżu (32 bloki).");
        }
    }

    private void listRafts(Player player) {
        int count = raftManager.getRaftCount();
        if (count == 0) {
            player.sendMessage(ChatColor.GRAY + "Brak aktywnych tratw.");
            return;
        }
        player.sendMessage(ChatColor.AQUA + "=== Aktywne tratwy (" + count + ") ===");
        int i = 1;
        for (RaftEntity raft : raftManager.getRafts()) {
            player.sendMessage("" + ChatColor.WHITE + i + ". " +
                ChatColor.GRAY + "ID: " + raft.getRaftId().toString().substring(0, 8) + "..." +
                ChatColor.WHITE + " | Pozycja: " +
                String.format("(%d, %d, %d)",
                    raft.getCenter().getBlockX(),
                    raft.getCenter().getBlockY(),
                    raft.getCenter().getBlockZ()));
            i++;
        }
    }

    /**
     * /raft zagiel [otwarty|zamkniety] — diagnostyka napędu tratwy.
     * Rozwinięty żagiel przełącza silnik fizyki na WIATR (kierunek i siłę bierze
     * z globalnego wiatru, RaftManager), zwinięty wraca na PRĄD WODY. Bez
     * argumentu przełącza stan. Gdy tratwa nie ma jeszcze żagla, rozwinięcie
     * stawia go na środkowej komórce pokładu — normalnie robi to PPM przedmiotem
     * „żagiel" (CMD 4005, SailPlaceListener).
     */
    private void sailRaft(Player player, String[] args) {
        RaftEntity raft = raftManager.getNearestRaft(player, 32.0);
        if (raft == null) {
            player.sendMessage(ChatColor.GRAY + "Brak tratwy w pobliżu (32 bloki).");
            return;
        }

        boolean open;
        if (args.length >= 2) {
            String a = args[1].toLowerCase();
            if (a.startsWith("otw") || a.equals("on") || a.equals("true") || a.equals("1") || a.startsWith("rozw")) {
                open = true;
            } else if (a.startsWith("zam") || a.equals("off") || a.equals("false") || a.equals("0") || a.startsWith("zw")) {
                open = false;
            } else {
                player.sendMessage(ChatColor.RED + "Użycie: /raft zagiel <otwarty|zamkniety>");
                return;
            }
        } else {
            open = !raft.isSailOpen(); // bez argumentu — przełącz
        }

        raft.setSailOpen(open);

        org.bukkit.util.Vector wind = raftManager.getGlobalWind();
        player.sendMessage(ChatColor.AQUA + "=== Żagiel tratwy ===");
        player.sendMessage(ChatColor.GRAY + "żagiel: "
                + (!raft.hasSail()
                        ? ChatColor.RED + "brak (postaw przedmiotem: PPM w pokład)"
                        : (raft.isSailOpen()
                                ? ChatColor.GREEN + "rozwinięty → napęd z wiatru"
                                : ChatColor.YELLOW + "zwinięty → napęd z prądu wody")));
        player.sendMessage(ChatColor.GRAY + "wiatr: "
                + String.format("(%.2f, %.2f) | %.2f b/s", wind.getX(), wind.getZ(), wind.length())
                + ChatColor.GRAY + " | prędkość tratwy: "
                + String.format("%.2f b/s", raft.getSpeed()));
    }

    private void debugRaft(Player player) {
        RaftEntity raft = raftManager.getNearestRaft(player, 32.0);
        if (raft == null) {
            player.sendMessage(ChatColor.GRAY + "Brak tratwy w pobliżu (32 bloki).");
            return;
        }
        player.sendMessage(ChatColor.AQUA + "=== Diagnostyka tratwy ===");
        player.sendMessage(raft.debugInfo());
        player.sendMessage(ChatColor.GRAY + "Ruch: " + (raft.isMoving() ? "TAK" : "NIE") +
                " | Prędkość: " + String.format("%.3f", raft.getSpeed()) +
                " | Kierunek: " + String.format("(%.2f, %.2f, %.2f)",
                raft.getDirection().getX(), raft.getDirection().getY(), raft.getDirection().getZ()));
    }

}
