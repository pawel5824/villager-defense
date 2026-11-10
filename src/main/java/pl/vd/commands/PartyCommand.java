package pl.vd.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import pl.vd.VillagerDefensePlugin;
import pl.vd.friend.PartyManager;
import pl.vd.friend.PartyManager.Invite;
import pl.vd.friend.PartyManager.InviteResult;

import java.util.*;

/**
 * Komenda /druzyna (alias: /party)
 *
 * Podkomendy:
 *   /druzyna zapros <nick>     — zaproś gracza do swojego party
 *   /druzyna dolacz <nick>     — akceptuj zaproszenie od gracza
 *   /druzyna odrzuc <nick>     — odrzuć zaproszenie od gracza
 *   /druzyna opusc             — opuść party
 *   /druzyna lista             — pokaż członków party
 *   /druzyna rozwiaz           — rozwiąż party (lider)
 */
public class PartyCommand implements CommandExecutor, TabCompleter {

    private final VillagerDefensePlugin plugin;

    public PartyCommand(VillagerDefensePlugin plugin) {
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
            case "zapros", "invite" -> handleInvite(player, label, args);
            case "dolacz", "join" -> handleAccept(player, label, args);
            case "odrzuc", "decline" -> handleDecline(player, label, args);
            case "opusc", "leave" -> handleLeave(player, label);
            case "lista", "list" -> handleList(player, label);
            case "rozwiaz", "disband" -> handleDisband(player, label);
            default -> sendUsage(player, label);
        }

        return true;
    }

    private void handleInvite(Player player, String label, String[] args) {
        PartyManager pm = plugin.getPartyManager();

        UUID partyId = pm.findPartyId(player.getUniqueId());
        if (partyId == null) {
            partyId = pm.createParty(player.getUniqueId(), player.getName());
            pl.vd.util.AntiSpam.send(player, "§7Utworzyłeś nowe party.");
        }

        if (args.length < 2) {
            pl.vd.util.AntiSpam.send(player, "§cUżyj: /" + label + " zapros <nick>");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            pl.vd.util.AntiSpam.send(player, "§cGracz §f" + args[1] + " §cnie jest online.");
            return;
        }

        InviteResult result = pm.invite(player.getUniqueId(), player.getName(),
                target.getUniqueId(), target.getName());

        switch (result) {
            case OK -> {
                pl.vd.util.AntiSpam.send(player, "§aWysłano zaproszenie do §f" + target.getName() + "§a!");

                // wiersz 1: info
                target.sendMessage(Component.text(
                        "§e" + player.getName() + " §7zaprasza Cię do party!"));

                // wiersz 2: klikalne przyciski [✓ Dołącz] [✗ Odrzuć]
                Component joinBtn = Component.text("§a§l[Dołącz]")
                        .clickEvent(ClickEvent.runCommand("/" + label + " dolacz " + player.getName()))
                        .hoverEvent(HoverEvent.showText(Component.text("§aKliknij, aby dołączyć do party")));
                Component declineBtn = Component.text("§c§l[Odrzuć]")
                        .clickEvent(ClickEvent.runCommand("/" + label + " odrzuc " + player.getName()))
                        .hoverEvent(HoverEvent.showText(Component.text("§cKliknij, aby odrzucić zaproszenie")));
                Component spacer = Component.text("  ");

                target.sendMessage(joinBtn.append(spacer).append(declineBtn));
            }
            case ALREADY_INVITED -> {
                pl.vd.util.AntiSpam.send(player, "§7Gracz §f" + target.getName()
                        + " §7ma już aktywne zaproszenie do Twojego party.");
            }
            case ALREADY_IN_PARTY -> {
                pl.vd.util.AntiSpam.send(player, "§cGracz §f" + target.getName()
                        + " §cjest już w jakimś party.");
            }
            case SELF -> {
                pl.vd.util.AntiSpam.send(player, "§cNie możesz zaprosić siebie.");
            }
            default -> {
                pl.vd.util.AntiSpam.send(player, "§cNie udało się wysłać zaproszenia.");
            }
        }
    }

    private void handleAccept(Player player, String label, String[] args) {
        PartyManager pm = plugin.getPartyManager();

        if (!pm.hasPendingInvite(player.getUniqueId())) {
            pl.vd.util.AntiSpam.send(player, "§cNie masz aktywnych zaproszeń do party.");
            return;
        }

        Map<UUID, Invite> invites = pm.getPendingInvites(player.getUniqueId());

        // bez nicku: jeśli jest jedno zaproszenie — akceptuj je; jeśli więcej — pokaż listę
        if (args.length < 2) {
            if (invites.size() == 1) {
                UUID inviterUuid = invites.keySet().iterator().next();
                acceptSpecific(player, label, pm, inviterUuid);
                return;
            }
            pl.vd.util.AntiSpam.send(player, "§6Masz kilka zaproszeń — podaj nick:");
            for (Map.Entry<UUID, Invite> entry : invites.entrySet()) {
                pl.vd.util.AntiSpam.send(player, "  §f/" + label + " dolacz " + entry.getValue().inviterName());
            }
            return;
        }

        // podano nick: znajdź zaproszenie od tego gracza
        Player inviter = Bukkit.getPlayerExact(args[1]);
        if (inviter == null) {
            pl.vd.util.AntiSpam.send(player, "§cGracz §f" + args[1] + " §cnie jest online.");
            return;
        }
        acceptSpecific(player, label, pm, inviter.getUniqueId());
    }

    private void acceptSpecific(Player player, String label, PartyManager pm, UUID inviterUuid) {
        Invite invite = pm.getPendingInvite(player.getUniqueId(), inviterUuid);
        if (invite == null) {
            pl.vd.util.AntiSpam.send(player, "§cNie masz zaproszenia od tego gracza.");
            return;
        }

        UUID newPartyId = pm.acceptInvite(player.getUniqueId(), inviterUuid);
        if (newPartyId == null) {
            pl.vd.util.AntiSpam.send(player, "§cZaproszenie wygasło lub jest nieaktualne.");
            return;
        }

        pl.vd.util.AntiSpam.send(player, "§aDołączyłeś do party §f" + invite.inviterName() + "§a!");

        Set<UUID> members = pm.getMembers(newPartyId);
        for (UUID memberId : members) {
            if (memberId.equals(player.getUniqueId())) continue;
            Player member = Bukkit.getPlayer(memberId);
            if (member != null && member.isOnline()) {
                member.sendMessage(Component.text(
                        "§e" + player.getName() + " §7dołączył do party!"));
            }
        }
    }

    private void handleDecline(Player player, String label, String[] args) {
        PartyManager pm = plugin.getPartyManager();

        if (!pm.hasPendingInvite(player.getUniqueId())) {
            pl.vd.util.AntiSpam.send(player, "§cNie masz aktywnych zaproszeń do party.");
            return;
        }

        Map<UUID, Invite> invites = pm.getPendingInvites(player.getUniqueId());

        // bez nicku: jeśli jedno — odrzuć; jeśli więcej — pokaż listę
        if (args.length < 2) {
            if (invites.size() == 1) {
                UUID inviterUuid = invites.keySet().iterator().next();
                pm.declineInvite(player.getUniqueId(), inviterUuid);
                pl.vd.util.AntiSpam.send(player, "§7Odrzucono zaproszenie.");
                return;
            }
            pl.vd.util.AntiSpam.send(player, "§6Masz kilka zaproszeń — podaj nick:");
            for (Map.Entry<UUID, Invite> entry : invites.entrySet()) {
                pl.vd.util.AntiSpam.send(player, "  §f/" + label + " odrzuc " + entry.getValue().inviterName());
            }
            return;
        }

        Player inviter = Bukkit.getPlayerExact(args[1]);
        if (inviter == null) {
            pl.vd.util.AntiSpam.send(player, "§cGracz §f" + args[1] + " §cnie jest online.");
            return;
        }
        pm.declineInvite(player.getUniqueId(), inviter.getUniqueId());
        pl.vd.util.AntiSpam.send(player, "§7Odrzucono zaproszenie od §f" + inviter.getName() + "§7.");
    }

    private void handleLeave(Player player, String label) {
        PartyManager pm = plugin.getPartyManager();
        if (!pm.isInParty(player.getUniqueId())) {
            pl.vd.util.AntiSpam.send(player, "§cNie jesteś w żadnym party.");
            return;
        }
        UUID partyId = pm.leaveParty(player.getUniqueId());
        pl.vd.util.AntiSpam.send(player, "§7Opuściłeś party.");
        if (partyId != null) {
            Set<UUID> members = pm.getMembers(partyId);
            if (members != null) {
                for (UUID memberId : members) {
                    Player member = Bukkit.getPlayer(memberId);
                    if (member != null && member.isOnline()) {
                        member.sendMessage(Component.text(
                                "§e" + player.getName() + " §7opuścił party."));
                    }
                }
            }
        }
    }

    private void handleList(Player player, String label) {
        PartyManager pm = plugin.getPartyManager();
        UUID partyId = pm.findPartyId(player.getUniqueId());
        if (partyId == null) {
            pl.vd.util.AntiSpam.send(player, "§cNie jesteś w żadnym party. Utwórz je: /" + label + " zapros <nick>");
            return;
        }

        Set<UUID> members = pm.getMembers(partyId);
        boolean isLeader = pm.isLeader(player.getUniqueId());
        pl.vd.util.AntiSpam.send(player, "§6=== Party"
                + (isLeader ? " §7(jesteś liderem)" : "") + " ===");
        for (UUID memberId : members) {
            String name = plugin.getFriendManager().getCachedName(memberId);
            if (name == null) name = "???";
            if (memberId.equals(partyId)) name += " §7★";
            pl.vd.util.AntiSpam.send(player, "  §f" + name);
        }
        pl.vd.util.AntiSpam.send(player, "§7Członków: §f" + members.size());
    }

    private void handleDisband(Player player, String label) {
        PartyManager pm = plugin.getPartyManager();
        UUID partyId = pm.findPartyId(player.getUniqueId());
        if (partyId == null) {
            pl.vd.util.AntiSpam.send(player, "§cNie jesteś w żadnym party.");
            return;
        }
        if (!pm.isLeader(player.getUniqueId())) {
            pl.vd.util.AntiSpam.send(player, "§cTylko lider może rozwiązać party.");
            return;
        }

        Set<UUID> members = pm.getMembers(partyId);
        pm.disbandParty(partyId);
        pl.vd.util.AntiSpam.send(player, "§7Rozwiązano party.");

        for (UUID memberId : members) {
            if (memberId.equals(player.getUniqueId())) continue;
            Player member = Bukkit.getPlayer(memberId);
            if (member != null && member.isOnline()) {
                member.sendMessage(Component.text(
                        "§7Party zostało rozwiązane przez §f" + player.getName() + "§7."));
            }
        }
    }

    private void sendUsage(Player player, String label) {
        pl.vd.util.AntiSpam.send(player, "§6Użycie:");
        pl.vd.util.AntiSpam.send(player, "  §f/" + label + " zapros <nick> §7— zaproś do party");
        pl.vd.util.AntiSpam.send(player, "  §f/" + label + " dolacz <nick> §7— akceptuj zaproszenie");
        pl.vd.util.AntiSpam.send(player, "  §f/" + label + " odrzuc <nick> §7— odrzuć zaproszenie");
        pl.vd.util.AntiSpam.send(player, "  §f/" + label + " lista §7— pokaż członków");
        pl.vd.util.AntiSpam.send(player, "  §f/" + label + " opusc §7— opuść party");
        pl.vd.util.AntiSpam.send(player, "  §f/" + label + " rozwiaz §7— rozwiąż party (lider)");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("zapros", "dolacz", "odrzuc", "opusc", "lista", "rozwiaz"));
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("zapros") || sub.equals("invite")) {
                // podpowiedzi nicków online (oprócz siebie)
                UUID selfId = sender instanceof Player p ? p.getUniqueId() : null;
                for (Player p : sender.getServer().getOnlinePlayers()) {
                    if (!p.getUniqueId().equals(selfId)) {
                        out.add(p.getName());
                    }
                }
            } else if (sub.equals("dolacz") || sub.equals("join") || sub.equals("odrzuc") || sub.equals("decline")) {
                // podpowiedzi nicków zapraszających (tylko ci co wysłali invite)
                if (sender instanceof Player p) {
                    Map<UUID, Invite> invites = plugin.getPartyManager().getPendingInvites(p.getUniqueId());
                    for (Invite inv : invites.values()) {
                        out.add(inv.inviterName());
                    }
                }
            }
        }
        return out;
    }
}
