package pl.vd.raft.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import pl.vd.raft.core.PlayerData;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.HashSet;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import pl.vd.VillagerDefensePlugin;
import pl.vd.raft.RaftPlugin;
import pl.vd.raft.core.GamePhase;
import pl.vd.raft.player.PlayerSession;

import java.io.File;

/**
 * Listener obsługujący zdarzenia w trybie Raft.
 */
public class RaftListener implements Listener {

    private final RaftPlugin plugin;

    /**
     * Blokuje pojawianie się WSZYSTKICH mobów w świecie Raft.
     * Obejmuje: naturalny spawn, jajka spawn, /summon, spawny, itp.
     */
    /** Moby wodne które nie mogą się pojawiać w świecie RAFT. */
    private static final Set<org.bukkit.entity.EntityType> BLOCKED_WATER_MOBS = Set.of(
            org.bukkit.entity.EntityType.DOLPHIN,
            org.bukkit.entity.EntityType.SQUID,
            org.bukkit.entity.EntityType.GLOW_SQUID,
            org.bukkit.entity.EntityType.DROWNED,
            org.bukkit.entity.EntityType.NAUTILUS,
            org.bukkit.entity.EntityType.COD,
            org.bukkit.entity.EntityType.SALMON,
            org.bukkit.entity.EntityType.PUFFERFISH,
            org.bukkit.entity.EntityType.TROPICAL_FISH,
            org.bukkit.entity.EntityType.TURTLE
    );

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        World spawnWorld = event.getLocation().getWorld();
        if (spawnWorld == null) return;
        String raftWorldName = plugin.getConfig().getString("raft-world-name", "RAFT");
        if (!spawnWorld.getName().equalsIgnoreCase(raftWorldName)) return;

        // Anuluj tylko zablokowane moby wodne
        if (BLOCKED_WATER_MOBS.contains(event.getEntityType())) {
            event.setCancelled(true);
        }
    }

    /** Co ile ticków zmniejszać pragnienie (co 20 ticków = 1 sekunda). */
    private static final int THIRST_TICK_INTERVAL = 20;
    /** O ile zmniejszać pragnienie za każdym razem. */
    private static final int THIRST_DECREASE = 2;

    public RaftListener(RaftPlugin plugin) {
        this.plugin = plugin;
        // Start decay task co tick
        plugin.getServer().getScheduler().runTaskTimer(plugin.getParent(), this::tickRowingDecay, 1L, 1L);
    }

    /**
     * Gracz dołącza do serwera — jeśli wychodził ESC z Raft, teleportuj do lobby.
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Sprawdź czy gracz opuścił serwer ESC z trybu Raft
        if (hasReturnToLobbyFlag(player)) {
            clearReturnToLobbyFlag(player);
            // Teleportuj do lobby po krótkim opóźnieniu (żeby światy się załadowały)
            plugin.getServer().getScheduler().runTaskLater(plugin.getParent(), () -> {
                Location lobby = ((VillagerDefensePlugin) plugin.getParent()).getLobbyLocation();
                if (lobby != null) {
                    player.teleport(lobby);
                    player.sendMessage("§a§l🌊 Witaj z powrotem!");
                    // daj kompas lobby po teleportacji (onJoin zdążył przed teleportacją)
                    pl.vd.VillagerDefensePlugin mainPlugin = (pl.vd.VillagerDefensePlugin) plugin.getParent();
                    mainPlugin.getPlayerListener().giveLobbyCompass(player);
                }
                // Brak lobby = brak komunikatu (zasada "wszędzie cicho", patrz AGENTS.md).
            }, 5L);
        }

        // HUD jest dodawany dopiero przy faktycznym dołączeniu do gry Raft
        // (joinGame), NIE przy dołączeniu do serwera — żeby nie obciążać
        // wszystkich graczy pakietami ProtocolLib co 10 ticków.
    }

    /**
     * Gracz zmienia świat — jeśli WYCHODZI ze świata Raft (do lobby, areny),
     * chowamy jego ekwipunek raft (zapis slotów) i czyścimy go, żeby nie
     * zabierał przedmiotów do lobby. Po ponownym dołączeniu do trybu Raft
     * ekwipunek wraca na te same sloty (PlayerSession.restoreRaftInventory).
     */
    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (!isRaftWorld(event.getFrom())) return;

        // Wyjście ze świata Raft = wyjście z trybu. Zamyka sesję i zestaw
        // statystyk, więc pakiet raft:stats przestaje iść, a klient z modem
        // dostaje pakiet raft:session_end (wysyła go leaveGame) i od razu
        // wraca do waniliowego HUD.
        if (plugin.getPlayerManager().hasActiveSession(player)) {
            PlayerSession session = plugin.getPlayerManager().getSession(player);
            if (session != null) {
                session.saveRaftInventory(player);
            }
            plugin.getPlayerManager().endSession(player);
        }
        if (plugin.getGameManager().getPlayerData(player.getUniqueId()) != null) {
            plugin.getGameManager().leaveGame(player);
        }
    }

    /**
     * Gracz opuszcza serwer (ESC) — zapisz świat Raft i oznacz powrót do lobby.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Jeśli gracz miał aktywną sesję Raft — zapisz świat i oznacz powrót do lobby
        if (plugin.getPlayerManager().hasActiveSession(player)) {
            // Zapisz sesję gracza z flagą returnToLobby
            PlayerSession session = plugin.getPlayerManager().getSession(player);
            if (session != null) {
                session.setReturnToLobby(true);
                session.save();
                // Schowaj ekwipunek raft, jeśli gracz wychodzi ze świata Raft
                // (wróci na te same sloty po ponownym dołączeniu do trybu).
                if (isRaftWorld(player.getWorld())) {
                    session.saveRaftInventory(player);
                }
            }

            // Faza 3 — NIE niszcz tratwy przy wyjściu: zapisz jej stan, żeby gracz
            // mógł później wrócić do dokładnie tej samej tratwy (w tym samym miejscu
            // oceanu). Tratwa zostaje na wodzie; po powrocie gracz jest na nią
            // teleportowany (GameModeGUI / raft spawn).
            pl.vd.raft.entity.RaftManager raftManager = pl.vd.raft.entity.RaftManager.getInstance();
            pl.vd.raft.entity.RaftEntity playerRaft = raftManager.getRaftForPlayer(player.getUniqueId());
            if (playerRaft != null) {
                pl.vd.raft.entity.RaftPersistence.save(playerRaft);
            }

            // Wymuś zapis całego świata Raft (chunki, bloki, etc.)
            World raftWorld = getRaftWorld();
            if (raftWorld != null) {
                raftWorld.save();
                plugin.getLogger().info("[Raft] Świat Raft zapisany po wyjściu gracza " + player.getName());
            }
        }

        // Zamknij tryb Raft również po wyjściu z serwera (statystyki, ProtocolLib HUD,
        // zapis PlayerData) — inaczej po ponownym wejściu klient od razu dostawałby
        // pakiet raft:stats i customowy HUD zamiast wanilii w lobby.
        plugin.getGameManager().leaveGame(player);
        plugin.getPlayerManager().endSession(player);
    }

    /**
     * Gracz się porusza - sprawdzenie czy jest na raftcie.
     */
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // Sprawdź czy gracz jest na wodzie — zwiększ pragnienie (pije wodę)
        if (player.getLocation().getBlock().getType() == org.bukkit.Material.WATER) {
            var session = plugin.getPlayerManager().getSession(player);
            if (session != null) {
                // Gracz jest w wodzie — może pić? (TODO: dodać mechanikę picia)
            }
        }

        // Sprawdź czy gracz opuścił ponton
        if (!plugin.getIslandManager().isOnRaft(player)) {
            // Gracz jest poza pontonem - może być na wyspie lub w wodzie
            // TODO: Sprawdź czy jest na wyspie
        }
    }

    // ─────────────────────── WIOSŁOWANIE (LEWY CLICK) ─────────────────────

    private static final long ROW_COOLDOWN_TICKS = 20; // 1 sekunda
    private final Map<java.util.UUID, Long> lastRowTick = new HashMap<>();
    private final Map<java.util.UUID, Double> rowingBoosts = new HashMap<>();
    private final Map<java.util.UUID, Long> lastDecayTick = new HashMap<>();

    private static final double ROW_FORCE = 0.15;
    private static final double ROW_BOOST_PER_STROKE = 0.01;  // +1% per uderzenie
    private static final double ROW_MAX_BOOST = 0.15;         // max 15%
    private static final double ROW_DECAY_AMOUNT = 0.01;      // -1% per dekay
    private static final long ROW_DECAY_INTERVAL_TICKS = 50;  // co 2.5s
    private static final double MAX_PITCH_DEGREES = 35.0;     // max kąt patrzenia w dół
    private static final int WATER_CHECK_RANGE = 5;            // zasięg raycastu do wody

    /**
     * Obsługuje lewy click wiosłem — sprawdza cooldown, kąt kamery, wodę,
     * zwiększa kumulatywny boost i nakłada siłę na tratwę.
     */
    private void handleRowing(Player player) {
        pl.vd.raft.entity.RaftEntity nearest = pl.vd.raft.entity.RaftManager.getInstance()
                .getNearestRaft(player, 5.0);
        if (nearest == null) return;

        UUID uuid = player.getUniqueId();
        long currentTick = player.getWorld().getFullTime();

        // 1. Cooldown
        Long lastTick = lastRowTick.get(uuid);
        if (lastTick != null && (currentTick - lastTick) < ROW_COOLDOWN_TICKS) {
            return; // Za wcześnie — cooldown 1s
        }

        // 2. Kąt kamery — gracz musi patrzeć w dół (pitch ≥ 35°)
        float pitch = player.getLocation().getPitch();
        if (pitch < MAX_PITCH_DEGREES) return;

        // 3. Sprawdź czy patrzy na wodę (raycast)
        if (!isLookingAtWater(player)) return;

        // 4. Sukces — zwiększ boost
        double boost = rowingBoosts.getOrDefault(uuid, 0.0);
        boost = Math.min(ROW_MAX_BOOST, boost + ROW_BOOST_PER_STROKE);
        rowingBoosts.put(uuid, boost);
        lastRowTick.put(uuid, currentTick);
        lastDecayTick.put(uuid, currentTick);

        // 5. Nakładaj siłę na tratwę
        Vector look = player.getLocation().getDirection().setY(0).normalize();
        double forceMultiplier = 1.0 + boost; // np. 1.07 = +7%
        nearest.getState().applyForce(
                "row_" + uuid,
                look.multiply(ROW_FORCE * forceMultiplier)
        );

        // 6. Efekt wizualny
        player.getWorld().spawnParticle(org.bukkit.Particle.SPLASH,
                player.getLocation().clone().add(0, 0.2, 0),
                5, 0.3, 0.1, 0.3);


    }

    /**
     * Dekay boosta — wywoływany co tick.
     * Gdy gracz nie wiosłuje przez 2.5s, boost spada o 1%.
     */
    private void tickRowingDecay() {
        long currentTick = Bukkit.getWorlds().get(0).getFullTime();

        for (UUID uuid : new HashSet<>(rowingBoosts.keySet())) {
            double boost = rowingBoosts.get(uuid);
            if (boost <= 0) {
                rowingBoosts.remove(uuid);
                lastDecayTick.remove(uuid);
                continue;
            }

            Long lastTick = lastRowTick.get(uuid);
            Long decayTick = lastDecayTick.get(uuid);

            // Dekay tylko gdy gracz nie wiosłował przez 2.5s
            if (lastTick != null && decayTick != null
                    && (currentTick - lastTick) >= ROW_DECAY_INTERVAL_TICKS) {
                boost = Math.max(0, boost - ROW_DECAY_AMOUNT);
                rowingBoosts.put(uuid, boost);
                lastDecayTick.put(uuid, currentTick);


            }
        }
    }

    /**
     * Sprawdza czy gracz patrzy na blok wody (raycast w kierunku patrzenia).
     */
    private boolean isLookingAtWater(Player player) {
        Location eyeLoc = player.getEyeLocation();
        Vector direction = eyeLoc.getDirection();

        for (int i = 1; i <= WATER_CHECK_RANGE; i++) {
            double x = eyeLoc.getX() + direction.getX() * i;
            double y = eyeLoc.getY() + direction.getY() * i;
            double z = eyeLoc.getZ() + direction.getZ() * i;

            org.bukkit.block.Block block = player.getWorld().getBlockAt(
                    (int) Math.floor(x),
                    (int) Math.floor(y),
                    (int) Math.floor(z)
            );

            if (block.getType() == org.bukkit.Material.WATER) return true;
            if (block.getType().isSolid()) return false; // Trafiliśmy w ścianę
        }
        return false;
    }

    // ─────────────────────── INTERAKCJA (PRAWY + LEWY) ────────

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.isCancelled()) return; // klik obsłużony wcześniej (np. GUI sieci zbierającej)
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || !item.hasItemMeta()) return;

        String displayName = item.getItemMeta().getDisplayName();
        Action action = event.getAction();

        // ─── Wiosłowanie — LEWY CLICK ───
        if (displayName.contains("Wiosło")) {
            if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
                handleRowing(player);
                return;
            }
        }

        // ─── Reszta przedmiotów — PRAWY CLICK ───

        // Haczyk - łowienie
        if (displayName.contains("Kamenny Haczyk") || displayName.contains("Wędka")) {
            if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
                // TODO: Implementacja łowienia (bez komunikatu na czacie — AGENTS.md pkt 1)
            }
        }

        // Kompas
        if (displayName.contains("Kompas")) {
            if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
                // Bez komunikatu na czacie (AGENTS.md pkt 1) — celowo.
            }
        }

        // Lornetka
        if (displayName.contains("Lornetka")) {
            if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
                // TODO: Powiększ widok
            }
        }

        // Radio
        if (displayName.contains("Radio")) {
            if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
                // TODO: Odbierz sygnał
            }
        }

        // Czysta Woda — wypij: +20 pragnienia (podstawowe zrodlo nawodnienia)
        if (displayName.contains("Czysta Woda")) {
            if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
                if (!drink(player, +20f)) return;

                if (item.getAmount() > 1) {
                    item.setAmount(item.getAmount() - 1);
                } else {
                    player.getInventory().setItemInMainHand(new ItemStack(org.bukkit.Material.GLASS_BOTTLE));
                }
            }
        }

        // Brudna Woda — odwadnia (-10 pragnienia) i do tego 50% szans na 2 serca
        if (displayName.contains("Brudna Woda")) {
            if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
                if (!drink(player, -10f)) return;

                if (Math.random() < 0.5) {
                    player.damage(2.0);
                }

                if (item.getAmount() > 1) {
                    item.setAmount(item.getAmount() - 1);
                } else {
                    player.getInventory().setItemInMainHand(new ItemStack(org.bukkit.Material.GLASS_BOTTLE));
                }
            }
        }
    }

    /**
     * Picie wody — zmienia pragnienie w JEDYNYM liczniku (PlayerData, zakres 0-20)
     * i od razu wysyla statystyki, zeby pasek w HUD reakowal w tej samej chwili
     * (pakiety raft:stats ida standardowo co 1 s).
     *
     * Wczesniej ten kod siegal do RaftHUDManager, czyli do DRUGIEGO modelu pragnienia
     * (skala 0-100), ktorego gracz nie widzial — pasek w modzie szedl tylko w dol
     * i nie reagowal na picie. To jest wlasnie ta pomyłka, ktora zostala usunieta.
     *
     * @param thirstDelta +20 dla czystej wody, -10 dla brudnej.
     * @return false, gdy gracz nie jest w trybie Raft — wtedy nie zuzywamy butelki
     *         (pragnienie istnieje tylko na oceanie).
     */
    private boolean drink(Player player, float thirstDelta) {
        PlayerData data = plugin.getGameManager().getPlayerData(player.getUniqueId());
        if (data == null) return false;
        data.addThirst(thirstDelta);
        pl.vd.raft.net.RaftStatsPacketSender.getInstance().sendStats(player, data);
        return true;
    }

    /**
     * Gracz placeduje blok — limit budowania + sprawdzenie raftu.
     */
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        World world = player.getWorld();

        // Limit budowania na świecie Raft
        String raftWorldName = plugin.getConfig().getString("raft-world-name", "RAFT");
        if (world.getName().equalsIgnoreCase(raftWorldName)) {
            int buildLimit = plugin.getConfig().getInt("raft.build-height-limit", 280);
            if (event.getBlock().getY() >= buildLimit) {
                event.setCancelled(true);
                return;
            }
        }

        // Sprawdź czy gracz jest na raftcie
        if (!plugin.getIslandManager().isOnRaft(player)) {
            // Gracz jest poza rafttem - pozwól na budowę tylko na wyspach
            // TODO: Sprawdź czy jest na wyspie
        }
    }

    /**
     * Gracz niszczy blok - sprawdzenie czy jest na raftcie.
     */
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        // Sprawdź czy gracz jest na raftcie
        if (!plugin.getIslandManager().isOnRaft(player)) {
            // Gracz jest poza rafttem - pozwól na niszczenie tylko na wyspach
            // TODO: Sprawdź czy jest na wyspie
        }
    }

    /**
     * Gracz otrzymuje obrażenia - sprawdzenie źródła.
     */
    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        // Sprawdź źródło obrażeń
        if (event.getCause() == EntityDamageEvent.DamageCause.DROWNING) {
            // Gracz się topi - sprawdź pragnienie
            // TODO: Odejmij pragnienie zamiast obrażeń
        }

        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            // Gracz spada - sprawdź czy jest na raftcie
            if (plugin.getIslandManager().isOnRaft(player)) {
                // Gracz spada na raft - zmniejsz obrażenia
                event.setDamage(event.getDamage() * 0.5);
            }
        }
    }

    /**
     * Gracz traci głód - sprawdzenie warunków.
     */
    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        // Sprawdź czy gracz jest na raftcie
        if (plugin.getIslandManager().isOnRaft(player)) {
            // Na raftcie głód spiera wolniej
            if (event.getFoodLevel() < player.getFoodLevel()) {
                event.setCancelled(true);
            }
        }
    }

    // ==================== POMOCNICZE ====================

    /** Sprawdza czy gracz ma zapisaną flagę powrotu do lobby (w YAML). */
    private boolean hasReturnToLobbyFlag(Player player) {
        File file = new File(plugin.getDataFolder(), "raft-players.yml");
        if (!file.exists()) return false;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        String path = "players." + player.getUniqueId().toString() + ".return-to-lobby";
        return cfg.getBoolean(path, false);
    }

    /** Czyści flagę powrotu do lobby po teleportacji. */
    private void clearReturnToLobbyFlag(Player player) {
        File file = new File(plugin.getDataFolder(), "raft-players.yml");
        if (!file.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        String path = "players." + player.getUniqueId().toString() + ".return-to-lobby";
        cfg.set(path, false);
        try {
            cfg.save(file);
        } catch (Exception e) {
            plugin.getLogger().warning("[Raft] Nie udało się wyczyścić flagi lobby dla " + player.getName() + ": " + e.getMessage());
        }
    }

    /** Czy świat to świat Raft (po nazwie z configu). */
    private boolean isRaftWorld(World world) {
        if (world == null) return false;
        String raftWorldName = plugin.getConfig().getString("raft-world-name", "RAFT");
        return world.getName().equalsIgnoreCase(raftWorldName);
    }

    /** Pobiera świat Raft (z WorldGenerator). */
    private World getRaftWorld() {
        if (plugin.getWorldGenerator() != null && plugin.getWorldGenerator().getRaftWorld() != null) {
            return plugin.getWorldGenerator().getRaftWorld();
        }
        // Fallback — szukaj świata po nazwie z configa
        String worldName = plugin.getConfig().getString("raft-world-name", "RAFT");
        return Bukkit.getWorld(worldName);
    }
}
