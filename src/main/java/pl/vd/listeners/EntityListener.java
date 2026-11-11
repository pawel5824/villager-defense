package pl.vd.listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowman;
import org.bukkit.entity.Villager;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
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

    /** Domyślne wartości efektu Szczęścia dla poziomów I-V (config może je nadpisać). */
    private static final int[] LUCK_CHANCES = {12, 24, 36, 48, 60};
    private static final int[] LUCK_BONUSES = {1, 2, 4, 6, 9};

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

    /** Gracz (i jego strzały) NIE może zranić przywołanych SOJUSZNIKÓW (tag
     *  vd_sojusz): Obrońcy, wilka, bałwana itd. */
    @EventHandler
    public void onPlayerHitsAlly(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        if (!victim.getScoreboardTags().contains(pl.vd.stand.ArmorStandTuner.TAG_ALLIED)) return;
        if (extractPlayerDamager(event.getDamager()) != null) {
            event.setCancelled(true);
        }
    }

    /** Sojusznicze stworzenia (tag vd_sojusz - Obrońca, Golem itd.) nigdy nie ranią
     *  gracza, wieśniaka ani innego sojusznika - nawet jeśli zdążą zamachnąć się,
     *  zanim menedżer wyczyści im cel. */
    @EventHandler
    public void onAlliedMobHitsAlly(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof LivingEntity damager)) return;
        if (!damager.getScoreboardTags().contains(pl.vd.stand.ArmorStandTuner.TAG_ALLIED)) return;
        Entity victim = event.getEntity();
        if (victim instanceof Player || victim instanceof Villager) {
            event.setCancelled(true);
            return;
        }
        if (victim instanceof LivingEntity lv
                && lv.getScoreboardTags().contains(pl.vd.stand.ArmorStandTuner.TAG_ALLIED)) {
            event.setCancelled(true);
        }
    }

    /** Bałwan (przywołany z „Przywołaj Bałwana") nie zostawia za sobą śniegu:
     *  całkowicie wyłącza waniliową trajektorię śniegu (EntityBlockFormEvent -
     *  to samo zdarzenie co Frost Walker). */
    @EventHandler
    public void onSnowmanFormSnow(EntityBlockFormEvent event) {
        if (event.getEntity() instanceof Snowman) {
            event.setCancelled(true);
        }
    }

    /** Bałwan przywołany z „Przywołaj Bałwana": mob, który go zaatakuje, zostaje
     *  ZAMROŻONY na 1 s (spowolnienie I + szron) - 20 ticków. Gracze nie są
     *  zamrażani. */
    @EventHandler
    public void onSnowmanHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Snowman snowman)) return;
        if (!snowman.getScoreboardTags().contains(pl.vd.stand.ArmorStandTuner.TAG_ALLIED)) return;
        if (!(event.getDamager() instanceof LivingEntity attacker) || attacker instanceof Player) return;
        attacker.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 0, true, true));
        attacker.setFreezeTicks(20);
        attacker.getWorld().spawnParticle(org.bukkit.Particle.SNOWFLAKE,
                attacker.getLocation().clone().add(0, attacker.getHeight() * 0.5, 0),
                10, 0.3, 0.3, 0.3, 0.01);
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
        }, 3L); // instant respawn (3 ticks)
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Arena arena = findArenaOf(player);
        if (arena == null || arena.getState() != pl.vd.arena.ArenaState.INGAME) return;

        int index = arena.getPlayers().indexOf(player.getUniqueId());
        if (index >= 0 && !arena.effectivePlayerSpawns().isEmpty()) {
            event.setRespawnLocation(arena.effectivePlayerSpawns().get(index % arena.effectivePlayerSpawns().size()));
        }
        player.setGameMode(GameMode.SPECTATOR);
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setAllowFlight(true);
        player.setFlying(true);

        // After 5 seconds, switch back to survival (żeby dało się stawiać miny - jajko
        // spawnujące nie działa w adventure) and teleport to arena. Arena jest i tak
        // chroniona przez BlockListener (brak niszczenia bloków, tylko pajęczyna).
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            Arena currentArena = findArenaOf(player);
            if (currentArena == null || currentArena.getState() != pl.vd.arena.ArenaState.INGAME) return;
            player.setGameMode(GameMode.SURVIVAL);
            player.setAllowFlight(false);
            player.setFlying(false);
            int respawnIndex = currentArena.getPlayers().indexOf(player.getUniqueId());
            if (respawnIndex >= 0 && !currentArena.effectivePlayerSpawns().isEmpty()) {
                Location spawn = currentArena.effectivePlayerSpawns().get(respawnIndex % currentArena.effectivePlayerSpawns().size());
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

        // Zabójstwo przez WIERZYCZKĘ (stojak): punkty dostaje gracz, który ją postawił
        // (zaniżone o procent z configu, zaokrąglone w dół do pełnej liczby).
        if (entity.getKiller() instanceof ArmorStand stand) {
            UUID placerUuid = plugin.getArmorStandTuner().placerOf(stand);
            Player owner = placerUuid != null ? Bukkit.getPlayer(placerUuid) : null;
            if (owner == null) return;
            int base = plugin.getConfig().getInt("points-per-kill." + entity.getType().name(),
                    plugin.getConfig().getInt("points-per-kill.DEFAULT", 2));
            int percent = plugin.getConfig().getInt("turret-kill-points-percent", 70);
            int points = (int) Math.floor(base * (percent / 100.0)); // pełne liczby, w dół
            if (points < 1) points = 1; // zawsze przynajmniej 1 pkt za zabójstwo wieżyczki
            PotionEffect luck = owner.getPotionEffect(PotionEffectType.LUCK);
            if (luck != null) points = applyLuckPointsBonus(points, luck.getAmplifier() + 1);
            arena.addSessionPoints(owner.getUniqueId(), points);
            arena.addSessionKill(owner.getUniqueId());
            pl.vd.util.Lang.sendActionBar(owner, "kill-points-turret", points);
            return;
        }

        Player killer = entity.getKiller();
        if (killer == null) return;

        int points = plugin.getConfig().getInt("points-per-kill." + entity.getType().name(),
                plugin.getConfig().getInt("points-per-kill.DEFAULT", 2));
        PotionEffect luck = killer.getPotionEffect(PotionEffectType.LUCK);
        if (luck != null) points = applyLuckPointsBonus(points, luck.getAmplifier() + 1);

        arena.addSessionPoints(killer.getUniqueId(), points);
        arena.addSessionKill(killer.getUniqueId());
        pl.vd.util.Lang.sendActionBar(killer, "kill-points", points);
    }

    /** Efekt szczęścia (LUCK): na poziomie I-V daje 12/24/36/48/60% szansy
     *  na bonusowe punkty za zabójstwo. Bonusy sumują się z poziomem:
     *  I +1, II +2, III +4, IV +6, V +9 punktów (tabele z configu). */
    private int applyLuckPointsBonus(int points, int level) {
        int index = Math.min(level, LUCK_CHANCES.length) - 1;
        if (index < 0) return points;

        var chances = plugin.getConfig().getIntegerList("luck.points-chance-percent-by-level");
        var bonuses = plugin.getConfig().getIntegerList("luck.points-bonus-by-level");
        int chance = index < chances.size() ? chances.get(index) : LUCK_CHANCES[index];
        int bonus = index < bonuses.size() ? bonuses.get(index) : LUCK_BONUSES[index];
        if (chance <= 0 || Math.random() * 100.0 >= chance) return points;
        return points + Math.max(0, bonus);
    }

    /** Mikstura szczęścia (efekt LUCK): gdy gracz na arenie dostanie obrażenia
     *  (z dowolnego źródła), ma szansę na Regenerację II na 3 s (wartości z configu
     *  luck.regen-*). Działa tylko, dopóki trwa efekt szczęścia. */
    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (findArenaOf(player) == null) return; // bonus tylko na arenie
        if (!player.hasPotionEffect(PotionEffectType.LUCK)) return;

        int chance = plugin.getConfig().getInt("luck.regen-chance-percent", 8);
        if (chance <= 0 || Math.random() * 100.0 >= chance) return;

        int level = Math.max(0, plugin.getConfig().getInt("luck.regen-level", 1));
        int seconds = Math.max(1, plugin.getConfig().getInt("luck.regen-duration-seconds", 3));
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, seconds * 20, level, true, true));
    }
}
