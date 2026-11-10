package pl.vd.commands;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import pl.vd.VillagerDefensePlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Komenda /znajomi (alias: /friend)
 *
 * Podkomendy:
 *   /znajomi dodaj <nick>   — dodaj gracza do listy znajomych
 *   /znajomi usun <nick>    — usuń gracza z listy znajomych
 *   /znajomi lista           — wyświetl listę znajomych
 */
public class FriendCommand implements CommandExecutor, TabCompleter {

    private final VillagerDefensePlugin plugin;

    public FriendCommand(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("§cTa komenda jest tylko dla graczy."));
            return true;
        }

        if (args.length == 0) {
            sendUsage(player, label);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "dodaj", "add" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("§cUżyj: /" + label + " dodaj <nick>"));
                    return true;
                }

                // nie pozwól dodać siebie
                if (player.getName().equalsIgnoreCase(args[1])) {
                    player.sendMessage(Component.text("§cNie możesz dodać siebie jako znajomego."));
                    return true;
                }

                // sprawdź czy gracz jest online (na razie tylko online)
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    player.sendMessage(Component.text("§cGracz §f" + args[1] + " §cnie jest online."));
                    return true;
                }

                boolean added = plugin.getFriendManager().addFriend(
                        player.getUniqueId(), player.getName(),
                        target.getUniqueId(), target.getName());

                if (added) {
                    player.sendMessage(Component.text("§aDodano §f" + target.getName() + " §ado listy znajomych!"));
                    // powiadom drugiego gracza, jeśli jest online
                    if (target.isOnline()) {
                        target.sendMessage(Component.text(
                                "§e" + player.getName() + " §7dodał Cię do listy znajomych."));
                    }
                } else {
                    player.sendMessage(Component.text("§7Gracz §f" + target.getName() + " §7jest już Twoim znajomym."));
                }
            }

            case "usun", "remove", "del" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("§cUżyj: /" + label + " usun <nick>"));
                    return true;
                }

                pl.vd.friend.FriendManager fm = plugin.getFriendManager();
                java.util.UUID targetUuid = null;
                String targetName = args[1];

                // sprawdź czy jest online
                Player onlineTarget = Bukkit.getPlayerExact(args[1]);
                if (onlineTarget != null) {
                    targetUuid = onlineTarget.getUniqueId();
                    targetName = onlineTarget.getName();
                } else {
                    // szukaj po nazwie w cache
                    for (java.util.UUID uuid : fm.getFriends(player.getUniqueId())) {
                        String cached = fm.getCachedName(uuid);
                        if (cached != null && cached.equalsIgnoreCase(args[1])) {
                            targetUuid = uuid;
                            targetName = cached;
                            break;
                        }
                    }
                    if (targetUuid == null) {
                        player.sendMessage(Component.text("§cGracz §f" + args[1] + " §cnie jest online i nie znaleziono go w znajomych."));
                        return true;
                    }
                }

                boolean removed = fm.removeFriend(player.getUniqueId(), targetUuid);
                if (removed) {
                    player.sendMessage(Component.text("§aUsunięto §f" + targetName + " §az listy znajomych."));
                    if (onlineTarget.isOnline()) {
                        onlineTarget.sendMessage(Component.text(
                                "§e" + player.getName() + " §7usunął Cię z listy znajomych."));
                    }
                } else {
                    player.sendMessage(Component.text("§7Gracz §f" + targetName + " §7nie jest Twoim znajomym."));
                }
            }

            case "lista", "list" -> {
                pl.vd.friend.FriendManager fm = plugin.getFriendManager();
                java.util.Set<java.util.UUID> friends = fm.getFriends(player.getUniqueId());

                player.sendMessage(Component.text("§6Znajomi:"));

                if (friends.isEmpty()) {
                    player.sendMessage(Component.text("  §7Nie masz jeszcze żadnych znajomych."));
                } else {
                    // posortuj alfabetycznie po nazwie
                    List<String> names = new ArrayList<>();
                    for (java.util.UUID uuid : friends) {
                        // preferuj nick online, potem cache
                        Player online = Bukkit.getPlayer(uuid);
                        if (online != null) {
                            names.add(online.getName());
                        } else {
                            String cached = fm.getCachedName(uuid);
                            names.add(cached != null ? cached : "??? (offline)");
                        }
                    }
                    names.sort(String.CASE_INSENSITIVE_ORDER);

                    for (String name : names) {
                        // oznacz online zieloną kropką
                        Player online = Bukkit.getPlayerExact(name);
                        if (online != null) {
                            player.sendMessage(Component.text("  §a● §f" + name + " §7(online)"));
                        } else {
                            player.sendMessage(Component.text("  §8● §7" + name));
                        }
                    }

                }
            }

            default -> sendUsage(player, label);
        }

        return true;
    }

    private void sendUsage(Player player, String label) {
        player.sendMessage(Component.text("§6Użycie:"));
        player.sendMessage(Component.text("  §f/" + label + " dodaj <nick> §7— dodaj znajomego"));
        player.sendMessage(Component.text("  §f/" + label + " usun <nick> §7— usuń znajomego"));
        player.sendMessage(Component.text("  §f/" + label + " lista §7— wyświetl listę znajomych"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("dodaj", "usun", "lista"));
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("dodaj") || sub.equals("add")) {
                // podpowiedzi nicków online
                for (Player p : sender.getServer().getOnlinePlayers()) {
                    out.add(p.getName());
                }
            } else if (sub.equals("usun") || sub.equals("remove") || sub.equals("del")) {
                // podpowiedzi nicków znajomych
                for (java.util.UUID uuid : plugin.getFriendManager().getFriends(((Player) sender).getUniqueId())) {
                    String cached = plugin.getFriendManager().getCachedName(uuid);
                    if (cached != null) out.add(cached);
                }
            }
        }
        return out;
    }
}
