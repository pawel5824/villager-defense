package pl.vd.listeners;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitTask;
import pl.vd.VillagerDefensePlugin;
import pl.vd.arena.Arena;
import pl.vd.game.MobSpawner;

public class EntityListener implements Listener {

    private final VillagerDefensePlugin plugin;

    public EntityListener(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // apply special melee bonuses (wand/ dagger) if attacker is player or projectile from player
        Player attackerPlayer = extractPlayerDamager(event.getDamager());
        if (attackerPlayer != null) {
            org.bukkit.entity.Entity victimEntity = event.getEntity();
            if (victimEntity instanceof LivingEntity victim && !(victim instanceof Player)) {
                // check main hand for ability
                org.bukkit.inventory.ItemStack hand = attackerPlayer.getInventory().getItemInMainHand();
                var type = plugin.getAbilityManager().getAbilityType(hand);
                if (type == pl.vd.ability.AbilityType.LASER_WAND) {
                    // melee bonus damage with wand
                    try { event.setDamage(event.getDamage() + 2.0); } catch (Throwable ignored) {}
                }
                // check dagger reach/name
                if (hand != null && hand.hasItemMeta() && hand.getItemMeta().hasDisplayName()) {
                    String name = hand.getItemMeta().getDisplayName();
                    if (name != null) {
                        if (name.contains("Sztylet")) {
                            double dist = attackerPlayer.getLocation().distance(victim.getLocation());
                            if (dist > 3.3) {
                                event.setCancelled(true); // out of reach
                                return;
                            } else {
                                try { event.setDamage(3.0); } catch (Throwable ignored) {}
                            }
                        } else if (name.contains("Skalpel")) {
                            double dist = attackerPlayer.getLocation().distance(victim.getLocation());
                            if (dist > 2.5) {
                                event.setCancelled(true);
                                return;
                            }
                            // damage handled by enchant (Sharpness 2)
                        }
                    }
                }
            }
        }

        if (event.getEntity() instanceof Player victim) {
            Player attacker = attackerPlayer;
            if (attacker == null) return;
            Arena arenaVictim = findArenaOf(victim);
            Arena arenaAttacker = findArenaOf(attacker);
            if (arenaVictim != null && arenaVictim == arenaAttacker) {
                event.setCancelled(true);
            }
            return;
        }

        if (event.getEntity() instanceof Villager villager && villager.hasMetadata(MobSpawner.METADATA_KEY)) {
            Player attacker = attackerPlayer;
            if (attacker != null) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onEntityPickup(EntityPickupItemEvent event) {
        Entity entity = event.getEntity();
        if (entity.hasMetadata(MobSpawner.METADATA_KEY)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityCombust(EntityCombustEvent event) {
        if (event.getEntity().hasMetadata(MobSpawner.METADATA_KEY)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        Arena arena = findArenaOf(player);
        if (arena == null || arena.getState() != pl.vd.arena.ArenaState.INGAME) return;

        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.getDrops().clear();
        event.setDroppedExp(0);

        // custom death message
        event.setDeathMessage(player.getName() + " zginął");

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            Arena currentArena = findArenaOf(player);
            if (currentArena == null || currentArena.getState() != pl.vd.arena.ArenaState.INGAME) return;
            player.spigot().respawn();
        }, 5L); // instant respawn (5 ticks)
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Arena arena = findArenaOf(player);
        if (arena == null || arena.getState() != pl.vd.arena.ArenaState.INGAME) return;

        int index = arena.getPlayers().indexOf(player.getUniqueId());
        if (index >= 0 && !arena.getPlayerSpawns().isEmpty()) {
            event.setRespawnLocation(arena.getPlayerSpawns().get(index % arena.getPlayerSpawns().size()));
        }
        player.setGameMode(GameMode.SPECTATOR);
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setAllowFlight(true);
        player.setFlying(true);

        // After 5 seconds, switch back to adventure and teleport to arena
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            Arena currentArena = findArenaOf(player);
            if (currentArena == null || currentArena.getState() != pl.vd.arena.ArenaState.INGAME) return;
            player.setGameMode(GameMode.ADVENTURE);
            player.setAllowFlight(false);
            player.setFlying(false);
            int respawnIndex = currentArena.getPlayers().indexOf(player.getUniqueId());
            if (respawnIndex >= 0 && !currentArena.getPlayerSpawns().isEmpty()) {
                Location spawn = currentArena.getPlayerSpawns().get(respawnIndex % currentArena.getPlayerSpawns().size());
                player.teleport(spawn);
            }
        }, 100L); // 5 seconds (100 ticks)
    }

    private Player extractPlayerDamager(org.bukkit.entity.Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile) {
            var shooter = projectile.getShooter();
            if (shooter instanceof Player playerShooter) return playerShooter;
        }
        return null;
    }

    private Arena findArenaOf(Player player) {
        for (Arena arena : plugin.getArenaManager().getArenas().values()) {
            if (arena.isPlaying(player)) return arena;
        }
        return null;
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!entity.hasMetadata(MobSpawner.METADATA_KEY)) return;

        // prevent any drops/exp from arena mobs
        try {
            event.getDrops().clear();
        } catch (Throwable ignored) {}
        try {
            event.setDroppedExp(0);
        } catch (Throwable ignored) {}

        String arenaName = entity.getMetadata(MobSpawner.METADATA_KEY).get(0).asString();
        Arena arena = plugin.getArenaManager().getArena(arenaName);
        if (arena == null) return;

        if (entity instanceof Villager) {
            arena.getLivingVillagers().remove(entity);
            if (arena.getLivingVillagers().stream().noneMatch(v -> v.isValid() && !v.isDead())) {
                plugin.getGameManager().endGame(arena, false);
            }
            return;
        }

        Player killer = entity.getKiller();
        if (killer == null) return;

        int points = plugin.getConfig().getInt("points-per-kill." + entity.getType().name(),
                plugin.getConfig().getInt("points-per-kill.DEFAULT", 2));

        arena.addSessionPoints(killer.getUniqueId(), points);
        arena.addSessionKill(killer.getUniqueId());
        killer.sendMessage(net.kyori.adventure.text.Component.text(
                "§7+" + points + " pkt §8(zabito " + entity.getType().name().toLowerCase() + ")"));
    }
}
