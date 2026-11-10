package pl.vd.raft.blocks;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import pl.vd.raft.RaftPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Komenda /urzadzenie postaw <nazwa_urzadzenia> - stawia urządzenie
 * /urzadzenie usun <nazwa_urzadzenia> - usuwa urządzenie
 * /urzadzenie lista - lista dostępnych urządzeń
 * /urzadzenie help - pomoc
 */
public class UrzadzenieCommand implements CommandExecutor, TabCompleter {

    private final RaftPlugin raftPlugin;
    private final DeviceRegistry registry;

    public UrzadzenieCommand(RaftPlugin raftPlugin, DeviceRegistry registry) {
        this.raftPlugin = raftPlugin;
        this.registry = registry;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cTylko gracz może użyć tej komendy!");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "postaw" -> {
                if (args.length < 2) {
                    sendHelp(player);
                    return true;
                }
                return placeDevice(player, args[1]);
            }
            case "usun" -> {
                if (args.length < 2) {
                    sendHelp(player);
                    return true;
                }
                return removeDevice(player, args[1]);
            }
            case "lista" -> {
                return listDevices(player);
            }
            case "help" -> {
                sendHelp(player);
                return true;
            }
            default -> {
                sendHelp(player);
                return true;
            }
        }
    }

    private boolean placeDevice(Player player, String deviceName) {
        DeviceRegistry.DeviceType type = registry.getType(deviceName);
        if (type == null) {
            player.sendMessage("§cNieznane urządzenie: §e" + deviceName);
            return true;
        }

        // Stawia urządzenie przed graczem (2 bloki ahead)
        org.bukkit.Location loc = player.getLocation().add(
            player.getLocation().getDirection().multiply(2)
        ).getBlock().getLocation();

        Object device = registry.placeDevice(deviceName, loc, player.getUniqueId(), player.getLocation().getYaw());
        if (device == null) {
            player.sendMessage("§cNie udało się postawić urządzenia!");
            return true;
        }

        // Zarejestruj w listenerze
        if (device instanceof WindmillDevice windmill) {
            raftPlugin.getDeviceBlockListener().registerWindmill(windmill);
        } else if (device instanceof DeviceBlock2x2 block) {
            raftPlugin.getDeviceBlockListener().registerDevice(block);
        }

        // Faza 1 projektu anten: natychmiastowy broadcast — gracz z modem widzi
        // urządzenie w tej samej sekundzie, bez czekania na pętlę 1 s.
        pl.vd.raft.net.RaftReceiversPacketSender.getInstance().broadcastAll(null);

        player.sendMessage("§aPostawiono urządzenie: §e" + type.getDisplayName());
        return true;
    }

    private boolean removeDevice(Player player, String deviceName) {
        // Faza 2/3 projektu anten: anteny nie są typem w rejestrze (trzyma je
        // AntennaManager), więc "usun antena" obsługujemy osobno — inaczej gracz
        // nie miałby jak zdjąć postawionej anteny (nośnik jest chroniony).
        if ("antena".equalsIgnoreCase(deviceName)) {
            pl.vd.raft.blocks.AntennaManager antennaManager = raftPlugin.getAntennaManager();
            int count = antennaManager != null ? antennaManager.getAntennas().size() : 0;
            if (count == 0) {
                player.sendMessage("§cBrak urządzeń typu: §e" + deviceName);
                return true;
            }
            antennaManager.clearAll();
            pl.vd.raft.blocks.DevicePersistence.saveAll(registry);
            pl.vd.raft.net.RaftReceiversPacketSender.getInstance().broadcastAll(null);
            player.sendMessage("§aUsunięto §e" + count + " §aurządzeń typu §e" + deviceName);
            return true;
        }

        List<Object> devices = registry.getActiveDevicesByType(deviceName);
        if (devices.isEmpty()) {
            player.sendMessage("§cBrak urządzeń typu: §e" + deviceName);
            return true;
        }

        // Usuń wszystkie urządzenia tego typu
        boolean removedAny = false;
        for (Object device : devices) {
            if (device instanceof WindmillDevice windmill) {
                raftPlugin.getDeviceBlockListener().unregisterWindmill(windmill);
                registry.removeWindmill(windmill);
                removedAny = true;
            } else if (device instanceof DeviceBlock2x2 block) {
                raftPlugin.getDeviceBlockListener().unregisterDevice(block);
                registry.removeDevice(block);
                removedAny = true;
            }
        }
        // Faza 0 projektu anten: odśwież devices.yml, żeby świadomie usunięte
        // urządzenie nie wskrzesiło się przy restarcie. Faza 1: natychmiastowy
        // broadcast — mod klienta podmienia całą listę, więc zniknie od razu.
        if (removedAny) {
            pl.vd.raft.blocks.DevicePersistence.saveAll(registry);
            pl.vd.raft.net.RaftReceiversPacketSender.getInstance().broadcastAll(null);
        }

        player.sendMessage("§aUsunięto §e" + devices.size() + " §aurządzeń typu §e" + deviceName);
        return true;
    }

    private boolean listDevices(Player player) {
        if (registry.getRegisteredNames().isEmpty()) {
            player.sendMessage("§cBrak zarejestrowanych urządzeń.");
            return true;
        }

        player.sendMessage("§6Dostępne urządzenia");
        for (String name : registry.getRegisteredNames()) {
            player.sendMessage("§e" + name);
        }
        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage("§6/urzadzenia - komendy");
        player.sendMessage("§e/urzadzenie postaw <nazwa>");
        player.sendMessage("§e/urzadzenie usun <nazwa>");
        player.sendMessage("§e/urzadzenie usun antena  §7(anteny nie są typem w rejestrze)");
        player.sendMessage("§e/urzadzenie lista");
        player.sendMessage("§e/urzadzenie help");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("postaw");
            completions.add("usun");
            completions.add("lista");
            completions.add("help");
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("postaw") || args[0].equalsIgnoreCase("usun"))) {
            completions.addAll(registry.getRegisteredNames());
            // Faza 2: antena jest osobnym bytem (AntennaManager), więc nie ma jej w rejestrze.
            if (args[0].equalsIgnoreCase("usun")) completions.add("antena");
        }

        // Filtrowanie po wpisanym tekście
        String lastArg = args[args.length - 1].toLowerCase();
        completions.removeIf(s -> !s.toLowerCase().startsWith(lastArg));

        return completions;
    }
}
