package pl.vd.game;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import pl.vd.VillagerDefensePlugin;
import pl.vd.arena.Arena;
import pl.vd.arena.ArenaState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class GameManager {

    private final VillagerDefensePlugin plugin;
    private final MobSpawner mobSpawner;
    private final Map<String, BukkitTask> arenaTasks = new HashMap<>();

    // per-player scoreboards for sidebar
    private final Map<UUID, Scoreboard> playerScoreboards = new HashMap<>();
    private final Map<UUID, List<String>> lastScoreboardLines = new HashMap<>();

    private final ScoreboardManager sbManager;

    public GameManager(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
        this.mobSpawner = new MobSpawner(plugin);
        this.sbManager = Bukkit.getScoreboardManager();
    }

    public boolean addPlayer(Player player, Arena arena) {
        if (!arena.isReadyToUse()) {
            player.sendMessage(Component.text("§cTa arena nie jest jeszcze skonfigurowana."));
            return false;
        }
        if (arena.getState() == ArenaState.INGAME || arena.getState() == ArenaState.ENDING) {
            player.sendMessage(Component.text("§cGra już trwa na tej arenie."));
            return false;
        }
        if (arena.getPlayers().size() >= arena.getMaxPlayers()) {
            player.sendMessage(Component.text("§cArena jest pełna."));
            return false;
        }
        arena.getPlayers().add(player.getUniqueId());
        player.teleport(arena.getLobbyLocation());
        player.setGameMode(GameMode.ADVENTURE);
        player.getInventory().clear();
        broadcast(arena, "§e" + player.getName() + " §7dołączył do gry (" + arena.getPlayers().size()
                + "/" + arena.getMaxPlayers() + ")");

        if (arena.getState() == ArenaState.WAITING && arena.getPlayers().size() >= arena.getMinPlayers()) {
            startCountdown(arena);
        }
        return true;
    }

    public void removePlayer(Player player, Arena arena) {
        arena.getPlayers().remove(player.getUniqueId());
        arena.getSpectators().remove(player.getUniqueId());
        // defensively clear scoreboard and inventory
        try {
            removeScoreboardForPlayer(player);
            player.getInventory().clear();
            player.getInventory().setArmorContents(new org.bukkit.inventory.ItemStack[4]);
            player.setAllowFlight(false);
            player.setFlying(false);
            player.setGameMode(GameMode.SURVIVAL);
            player.setHealth(20.0);
            player.setFoodLevel(20);
            player.setSaturation(20f);
        } catch (Throwable ignored) {}

        if (arena.getEndLocation() != null) player.teleport(arena.getEndLocation());

        if (arena.getState() == ArenaState.INGAME && arena.getPlayers().isEmpty()) {
            endGame(arena, false);
        } else if (arena.getState() == ArenaState.STARTING && arena.getPlayers().size() < arena.getMinPlayers()) {
            cancelCountdown(arena);
        }
    }

    private void startCountdown(Arena arena) {
        arena.setState(ArenaState.STARTING);
        arena.setCountdown(plugin.getConfig().getInt("game.starting-countdown-seconds", 20));

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (arena.getCountdown() <= 0) {
                    startGame(arena);
                    cancel();
                    return;
                }
                if (arena.getCountdown() % 5 == 0 || arena.getCountdown() <= 5) {
                    broadcast(arena, "§7Start za §e" + arena.getCountdown() + "s");
                }
                arena.setCountdown(arena.getCountdown() - 1);
            }
        }.runTaskTimer(plugin, 0L, 20L);

        arenaTasks.put(arena.getName(), task);
    }

    private void cancelCountdown(Arena arena) {
        BukkitTask task = arenaTasks.remove(arena.getName());
        if (task != null) task.cancel();
        arena.setState(ArenaState.WAITING);
    }

    private void startGame(Arena arena) {
        arena.setState(ArenaState.INGAME);
        arena.setRound(0);

        int i = 0;
        for (UUID uuid : arena.getPlayers()) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player == null) continue;
            Location spawn = arena.getPlayerSpawns().get(i % arena.getPlayerSpawns().size());
            Location lookForward = spawn.clone();
            lookForward.setYaw(0.0f);
            lookForward.setPitch(0.0f);
            player.teleport(lookForward);
            player.setGameMode(GameMode.SURVIVAL);
            player.setHealth(20.0);
            player.setFoodLevel(20);
            String kitId = plugin.getKitManager().getChosenKitId(player);
            plugin.getKitManager().giveKit(player, kitId);
            // give initial refill items (food/potions) at match start
            plugin.getKitManager().refillKitItems(player, kitId);

            // assign initial scoreboard for player
            createAndAssignScoreboard(player, arena);
            i++;
        }

        // disable advancement announcements for world(s) used by this arena
        if (!arena.getPlayerSpawns().isEmpty()) {
            Location any = arena.getPlayerSpawns().get(0);
            if (any != null && any.getWorld() != null) {
                any.getWorld().setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
            }
        }

        arena.setStartedAtMillis(System.currentTimeMillis());
        spawnVillagers(arena);
        broadcast(arena, "§aGra się rozpoczyna! Chrońcie wieśniaków!");
        startRound(arena);
    }

    private void spawnVillagers(Arena arena) {
        arena.getLivingVillagers().clear();
        int count = plugin.getConfig().getInt("game.villager-count-per-arena", 3);
        for (int i = 0; i < count && !arena.getVillagerSpawns().isEmpty(); i++) {
            Location loc = arena.getVillagerSpawns().get(i % arena.getVillagerSpawns().size());
            Villager villager = (Villager) loc.getWorld().spawnEntity(loc, org.bukkit.entity.EntityType.VILLAGER);
            villager.setMetadata(MobSpawner.METADATA_KEY,
                    new org.bukkit.metadata.FixedMetadataValue(plugin, arena.getName()));
            villager.setInvulnerable(false);
            arena.getLivingVillagers().add(villager);
        }
    }

    public void startRound(Arena arena) {
        arena.setRound(arena.getRound() + 1);
        // every 3rd round give refill items (3,6,9,...)
        if (arena.getRound() > 0 && arena.getRound() % 3 == 0) {
            for (UUID uuid : arena.getPlayers()) {
                Player player = plugin.getServer().getPlayer(uuid);
                if (player == null) continue;
                String kitId = plugin.getKitManager().getChosenKitId(player);
                plugin.getKitManager().refillKitItems(player, kitId);
                player.sendMessage(Component.text("§aTwoje przedmioty z kitu zostały odświeżone!"));
            }
        }
        broadcast(arena, "§6Runda " + arena.getRound() + " rozpoczęta!");
        mobSpawner.spawnWave(arena, arena.getRound());
        scheduleRoundWatcher(arena);
    }

    private void scheduleRoundWatcher(Arena arena) {
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (arena.getState() != ArenaState.INGAME) {
                    cancel();
                    return;
                }
                arena.getSpawnedMobs().removeIf(e -> !e.isValid() || e.isDead());

                if (arena.getLivingVillagers().stream().noneMatch(v -> v.isValid() && !v.isDead())) {
                    endGame(arena, false);
                    cancel();
                    return;
                }
                // update scoreboards (per player) each tick - show points, kills, elapsed time
                for (UUID uuid : arena.getPlayers()) {
                    Player p = plugin.getServer().getPlayer(uuid);
                    if (p == null) continue;
                    updatePlayerScoreboard(p, arena);
                }
                if (arena.getPlayers().isEmpty()) {
                    endGame(arena, false);
                    cancel();
                    return;
                }
                        if (arena.getSpawnedMobs().isEmpty()) {
                    // clean up any leftover spawned mobs and prepare for next round
                    for (var m : arena.getSpawnedMobs()) if (m != null && m.isValid()) m.remove();
                    arena.getSpawnedMobs().clear();
                    cancel();
                    int breakSeconds = plugin.getConfig().getInt("game.round-break-seconds", 10);
                    broadcast(arena, "§aRunda " + arena.getRound() + " zakończona!");
                    scheduleNextRoundCountdown(arena, breakSeconds);
                    plugin.getServer().getScheduler().runTaskLater(plugin,
                            () -> { if (arena.getState() == ArenaState.INGAME) startRound(arena); },
                            breakSeconds * 20L);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
        arenaTasks.put(arena.getName() + "-watcher", task);
    }

    public void endGame(Arena arena, boolean won) {
        arena.setState(ArenaState.ENDING);
        BukkitTask task = arenaTasks.remove(arena.getName() + "-watcher");
        if (task != null) task.cancel();
        BukkitTask countdownTask = arenaTasks.remove(arena.getName() + "-break");
        if (countdownTask != null) countdownTask.cancel();

        // restore advancement announcements to default (true) for arena world
        if (!arena.getPlayerSpawns().isEmpty()) {
            Location any = arena.getPlayerSpawns().get(0);
            if (any != null && any.getWorld() != null) {
                any.getWorld().setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, true);
            }
        }

        broadcast(arena, won ? "§aWygraliście! Przetrwaliście " + arena.getRound() + " rund!"
                : "§cPrzegraliście na rundzie " + arena.getRound() + ". Wieśniacy zginęli lub gracze opuścili grę.");

        for (UUID uuid : arena.getPlayers()) {
            Player player = plugin.getServer().getPlayer(uuid);
            var data = plugin.getPlayerDataManager().get(uuid, player != null ? player.getName() : "?");
            data.addPoints(arena.getSessionPoints(uuid));
            data.addKills(arena.getSessionKills(uuid));
            data.addGamePlayed();
            data.updateHighestWave(arena.getRound());

            if (player != null) {
                // remove per-player scoreboard
                removeScoreboardForPlayer(player);

                player.setGameMode(GameMode.SURVIVAL);
                player.getInventory().clear();
                player.getInventory().setArmorContents(new org.bukkit.inventory.ItemStack[4]);
                player.setAllowFlight(false);
                player.setFlying(false);
                player.setHealth(20.0);
                player.setFoodLevel(20);
                player.setSaturation(20f);
                if (arena.getEndLocation() != null) player.teleport(arena.getEndLocation());
                player.sendMessage(Component.text("§7Łącznie w tej grze zdobyto: §e" + arena.getSessionPoints(uuid) + " pkt."));
            }
        }
        plugin.getPlayerDataManager().saveAll();

        for (var mob : arena.getSpawnedMobs()) if (mob.isValid()) mob.remove();
        for (var v : arena.getLivingVillagers()) if (v.isValid()) v.remove();

        arena.resetSession();
        arena.setState(ArenaState.WAITING);
    }

    private void scheduleNextRoundCountdown(Arena arena, int seconds) {
        if (seconds < 5) return;
        String countdownKey = arena.getName() + "-break";
        int[] remaining = {seconds};
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (arena.getState() != ArenaState.INGAME) {
                    cancel();
                    return;
                }
                int timeLeft = remaining[0]--;
                if (timeLeft == 15 || timeLeft == 10 || timeLeft == 5) {
                    broadcast(arena, "§7Kolejna runda za §e" + timeLeft + " sekund");
                }
                if (timeLeft <= 0) {
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
        arenaTasks.put(countdownKey, task);
    }

    private void createAndAssignScoreboard(Player player, Arena arena) {
        if (sbManager == null) return;
        Scoreboard board = sbManager.getNewScoreboard();
        Objective obj = board.registerNewObjective("vd_sidebar", "dummy", Component.text("VillagerDefense"));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        // initial entries: Fala, Points, Kills, Time
        obj.getScore("Fala: " + arena.getRound()).setScore(4);
        obj.getScore("Punkty: 0").setScore(3);
        obj.getScore("Zabójstwa: 0").setScore(2);
        obj.getScore("Czas: 00:00").setScore(1);
        player.setScoreboard(board);
        playerScoreboards.put(player.getUniqueId(), board);
        lastScoreboardLines.put(player.getUniqueId(), List.of("Fala: " + arena.getRound(), "Punkty: 0", "Zabójstwa: 0", "Czas: 00:00"));
    }

    private void updatePlayerScoreboard(Player player, Arena arena) {
        Scoreboard board = playerScoreboards.get(player.getUniqueId());
        if (board == null) return;
        List<String> previous = lastScoreboardLines.getOrDefault(player.getUniqueId(), List.of());
        // remove previous lines
        for (String s : previous) {
            board.resetScores(s);
        }
        // build new lines
        String round = "Fala: " + arena.getRound();
        String pts = "Punkty: " + arena.getSessionPoints(player.getUniqueId());
        String kills = "Zabójstwa: " + arena.getSessionKills(player.getUniqueId());
        String time = "Czas: " + formatElapsed(arena);
        Objective obj = board.getObjective("vd_sidebar");
        if (obj == null) {
            obj = board.registerNewObjective("vd_sidebar", "dummy", Component.text("VillagerDefense"));
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
        obj.getScore(round).setScore(4);
        obj.getScore(pts).setScore(3);
        obj.getScore(kills).setScore(2);
        obj.getScore(time).setScore(1);
        lastScoreboardLines.put(player.getUniqueId(), List.of(round, pts, kills, time));
    }

    private void removeScoreboardForPlayer(Player player) {
        UUID id = player.getUniqueId();
        if (playerScoreboards.containsKey(id)) {
            // reset to main scoreboard
            Scoreboard main = sbManager != null ? sbManager.getMainScoreboard() : Bukkit.getScoreboardManager().getMainScoreboard();
            player.setScoreboard(main);
            playerScoreboards.remove(id);
            lastScoreboardLines.remove(id);
        }
    }

    private String formatElapsed(Arena arena) {
        long started = arena.getStartedAtMillis();
        if (started <= 0) return "00:00";
        long secs = (System.currentTimeMillis() - started) / 1000;
        long mm = secs / 60;
        long ss = secs % 60;
        return String.format("%02d:%02d", mm, ss);
    }

    private void broadcast(Arena arena, String msg) {
        for (UUID uuid : arena.getPlayers()) {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p != null) p.sendMessage(Component.text(msg));
        }
    }
}
