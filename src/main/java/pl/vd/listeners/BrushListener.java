package pl.vd.listeners;

import net.kyori.adventure.text.Component;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import pl.vd.VillagerDefensePlugin;

public class BrushListener implements Listener {

    private final VillagerDefensePlugin plugin;

    public BrushListener(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!plugin.getBrushManager().isBrushTool(event.getItem())) return;

        Player player = event.getPlayer();
        event.setCancelled(true); // blokuje np. przekopanie ziemi motyką

        if (!player.hasPermission("vd.admin")) {
            player.sendMessage(Component.text("§cBrak uprawnień do używania pędzla budowlanego."));
            return;
        }

        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        plugin.getBrushManager().apply(player, clicked);
        player.sendMessage(Component.text("§a✓ Zastosowano brush (" +
                plugin.getBrushManager().getSettings(player).getType() + ")"));
    }
}
