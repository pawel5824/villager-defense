package pl.vd.listeners;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import pl.vd.VillagerDefensePlugin;
import pl.vd.arena.Arena;

import java.util.ArrayList;
import java.util.List;

public class SignListener implements Listener {

    private final VillagerDefensePlugin plugin;

    public SignListener(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Tworzenie tabliczek areny:
     *   linia 1: [vd]       + linia 2: nazwa areny        → zwykła tabliczka areny
     *   linia 1: [vd:map]   + linie 2-4: nazwy map        → tabliczka rotacji (ręczna pula)
     *   linia 1: [vd:map:*]                                → tabliczka rotacji (pula = wszystkie gotowe areny)
     * (wielkość liter bez znaczenia)
     */
    @EventHandler
    public void onSignChange(SignChangeEvent event) {
        String firstLine = PlainTextComponentSerializer.plainText().serialize(event.line(0)).trim();
        if (firstLine.equalsIgnoreCase("[vd]")) {
            createArenaSign(event);
        } else if (firstLine.equalsIgnoreCase("[vd:map]")) {
            createRotationSign(event, false);
        } else if (firstLine.equalsIgnoreCase("[vd:map:*]")) {
            createRotationSign(event, true);
        }
    }

    private void createArenaSign(SignChangeEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPermission("vd.admin")) {
            pl.vd.util.AntiSpam.send(player, "§cBrak uprawnień do tworzenia tabliczek areny.");
            event.setCancelled(true);
            return;
        }

        String arenaName = PlainTextComponentSerializer.plainText().serialize(event.line(1)).trim();
        Arena arena = plugin.getArenaManager().getArena(arenaName);
        if (arena == null) {
            pl.vd.util.AntiSpam.send(player, "§cNie ma areny o nazwie: " + arenaName);
            event.setCancelled(true);
            return;
        }

        Location loc = event.getBlock().getLocation();
        plugin.getSignManager().register(loc, arena.getName());
        // linie rysuje wyłącznie SignManager.updateDisplay; tick później, bo blok jest jeszcze w edycji
        plugin.getServer().getScheduler().runTask(plugin, () ->
                plugin.getSignManager().updateDisplay(loc, arena.getName()));

        pl.vd.util.AntiSpam.send(player, "§aTabliczka areny \"" + arena.getName() + "\" utworzona.");
    }

    private void createRotationSign(SignChangeEvent event, boolean auto) {
        Player player = event.getPlayer();
        if (!player.hasPermission("vd.admin")) {
            pl.vd.util.AntiSpam.send(player, "§cBrak uprawnień do tworzenia tabliczek rotacji.");
            event.setCancelled(true);
            return;
        }

        // tryb ręczny: linie 2-4 = nazwy map (puste linie pomijane); tryb auto: linie ignorowane
        List<String> maps = new ArrayList<>();
        if (!auto) {
            for (int i = 1; i <= 3; i++) {
                String map = PlainTextComponentSerializer.plainText().serialize(event.line(i)).trim();
                if (!map.isEmpty()) maps.add(map);
            }
        }

        // walidacja (tylko tryb ręczny): wszystkie mapy muszą istnieć
        List<String> unknown = new ArrayList<>();
        for (String map : maps) {
            if (plugin.getArenaManager().getArena(map) == null) unknown.add(map);
        }
        if (!unknown.isEmpty()) {
            pl.vd.util.AntiSpam.send(player, "§cNie ma takich aren: §f" + String.join(", ", unknown));
            event.setCancelled(true);
            return;
        }

        Location loc = event.getBlock().getLocation();
        String current = plugin.getSignManager().registerRotation(loc, auto ? null : maps);
        // linie rysuje wyłącznie SignManager.updateDisplay; tick później, bo blok jest jeszcze w edycji
        plugin.getServer().getScheduler().runTask(plugin, () ->
                plugin.getSignManager().updateDisplay(loc, current));

        if (auto) {
            pl.vd.util.AntiSpam.send(player, "§aTabliczka rotacji map utworzona (pula automatyczna: wszystkie gotowe areny).");
        } else {
            pl.vd.util.AntiSpam.send(player, "§aTabliczka rotacji map utworzona (pula: "
                    + String.join(", ", maps) + ").");
        }
    }

    /** Kliknięcie PPM w zarejestrowaną tabliczkę = dołączenie do areny (lub aktualnej mapy rotacji). */
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getPlayer().getGameMode() == org.bukkit.GameMode.SPECTATOR) return; // duch niczego nie używa
        Block block = event.getClickedBlock();
        if (block == null || !(block.getState() instanceof Sign)) return;

        String arenaName = plugin.getSignManager().getJoinArenaName(block.getLocation());
        if (arenaName == null) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        Arena arena = plugin.getArenaManager().getArena(arenaName);
        if (arena == null) {
            pl.vd.util.AntiSpam.send(player, "§cTa mapa już nie istnieje. Tabliczka wybierze inną.");
            plugin.getSignManager().updateAllDisplays();
            return;
        }

        plugin.getGameManager().addPlayer(player, arena);
    }
}
