package pl.vd.listeners;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import pl.vd.VillagerDefensePlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BrushListener implements Listener {

    private final VillagerDefensePlugin plugin;

    /** Ostatni moment użycia pędzla przez gracza (ms) - debounce przeciw PODWÓJNEMU
     *  wywołaniu PlayerInteractEvent przy jednym kliknięciu (klient wysyła osobno
     *  „use item" i „use block", przez co czasem lecą dwa zdarzenia z tą samą ręką
     *  i akcją). Drugie kliknięcie w odstępie COOLDOWN_MILLIS jest ignorowane, więc
     *  jednym kliknięciem zawsze powstaje DOKŁADNIE jedna kula. */
    private static final long COOLDOWN_MILLIS = 150;
    private final Map<UUID, Long> lastUse = new HashMap<>();

    public BrushListener(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!plugin.getBrushManager().isBrushTool(event.getItem())) return;
        if (event.getPlayer().getGameMode() == org.bukkit.GameMode.SPECTATOR) return; // duch niczego nie używa

        Player player = event.getPlayer();
        event.setCancelled(true); // blokuje np. przekopanie ziemi motyką

        if (!player.hasPermission("vd.admin")) {
            pl.vd.util.AntiSpam.send(player, "§cBrak uprawnień do używania pędzla budowlanego.");
            return;
        }

        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        // Debounce: drugie zdarzenie tego samego kliknięcia (podwójny PlayerInteractEvent)
        // jest ignorowane - jeden klik = dokładnie jedna kula.
        long now = System.currentTimeMillis();
        Long last = lastUse.get(player.getUniqueId());
        if (last != null && now - last < COOLDOWN_MILLIS) return;
        lastUse.put(player.getUniqueId(), now);

        plugin.getBrushManager().apply(player, clicked);
        pl.vd.util.AntiSpam.send(player, "§a✓ Zastosowano brush (" +
                plugin.getBrushManager().getSettings(player).getType() + ")");
    }

    /** Sprząta wpis debounce'a po wyjściu gracza, żeby mapa nie rosła w nieskończoność. */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastUse.remove(event.getPlayer().getUniqueId());
    }
}
