package pl.vd.game;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import pl.vd.VillagerDefensePlugin;
import pl.vd.arena.Arena;
import pl.vd.arena.ArenaState;
import pl.vd.kits.KitManager;
import pl.vd.util.HealthDisplay;
import pl.vd.util.Lang;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class GameManager {

    private final VillagerDefensePlugin plugin;
    private final MobSpawner mobSpawner;
    private final Map<String, BukkitTask> arenaTasks = new HashMap<>();

    // per-player scoreboards for sidebar
    private final Map<UUID, Scoreboard> playerScoreboards = new HashMap<>();
    private final Map<UUID, List<String>> lastScoreboardLines = new HashMap<>();

    // per-player BossBar z liczbą mobów do pokonania (pokazywana w trakcie fali)
    private final Map<UUID, BossBar> playerBossBars = new HashMap<>();
    // per-player BossBar przerwy (osobny od bossbara fali)
    private final Map<UUID, BossBar> pauseBossBars = new HashMap<>();
    // nazwa areny -> ile mobów miała ostatnia fala (do paska postępu)
    private final Map<String, Integer> arenaWaveTotals = new HashMap<>();

    // zapisany ekwipunek gracza sprzed wejścia na arenę (przywracany po wyjściu/końcu gry)
    private final Map<UUID, SavedInventory> savedInventories = new HashMap<>();

    // moby przywołane przez graczy ze sklepu areny (wilk, bałwan, obrońca, golem,
    // warden): UUID encji -> nazwa areny. Usuwane, gdy gra na tej arenie dobiegnie
    // końca (endGame) oraz przy wyłączaniu serwera (shutdownCleanup).
    private final Map<UUID, String> playerSummons = new HashMap<>();

    private static class SavedInventory {
        ItemStack[] contents;
        ItemStack[] armor;
        ItemStack offhand;
    }

    private final ScoreboardManager sbManager;

    // flaga zapobiegająca rekurencji przy auto-join party
    private boolean joiningWithParty = false;

    public GameManager(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
        this.mobSpawner = new MobSpawner(plugin);
        this.sbManager = Bukkit.getScoreboardManager();
    }

    public boolean addPlayer(Player player, Arena arena) {
        // system instancji: gracz dołączający do SZABLONU trafia do osobnej kopii mapy.
        // resolveForJoin zwraca: instancję (dołączenie), null (limit instancji - odmowa)
        // albo sam szablon (nieskonfigurowany - normalny przepływ pokaże błąd niżej).
        if (arena.isTemplate()) {
            Arena target = plugin.getInstanceManager().resolveForJoin(player, arena);
            if (target == null) {
                Lang.send(player, "instance-limit");
                return false;
            }
            if (target != arena) return addPlayer(player, target);
        }
        if (!arena.isReadyToUse()) {
            Lang.send(player, "arena-not-ready", brakujeKonfiguracji(player, arena));
            return false;
        }
        if (arena.getState() == ArenaState.INGAME || arena.getState() == ArenaState.ENDING) {
            Lang.send(player, "game-in-progress");
            return false;
        }
        if (arena.getPlayers().size() >= arena.getMaxPlayers()) {
            Lang.send(player, "arena-full");
            return false;
        }
        arena.getPlayers().add(player.getUniqueId());
        saveInventory(player);
        // lobby lub (jeśli brak) lokalizacja końcowa jako fallback
        Location lobby = arena.getLobbyLocation() != null ? arena.getLobbyLocation() : arena.getEndLocation();
        if (lobby != null) player.teleport(lobby);
        player.setGameMode(GameMode.ADVENTURE);
        player.getInventory().clear();
        player.getInventory().setArmorContents(new org.bukkit.inventory.ItemStack[4]);
        broadcast(arena, "join-broadcast", player.getName(), arena.getPlayers().size(), arena.getMaxPlayers());

        // automatycznie otwórz GUI wyboru klasy (kitu) w poczekalni - działa przy każdym
        // dołączeniu (tabliczka areny, /def join); 1 tick opóźnienia, żeby teleport
        // do lobby zdążył się dokończyć, zanim gracz zobaczy GUI
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (arena.isPlaying(player)) {
                plugin.getKitManager().openKitSelector(player);
            }
        }, 1L);

        if (arena.getState() == ArenaState.WAITING && arena.getPlayers().size() >= arena.getMinPlayers()) {
            startCountdown(arena);
        } else if (arena.getState() == ArenaState.STARTING) {
            // Nowy gracz w poczekalni skraca odliczanie do startu (np. z 20s na 10s),
            // żeby nie czekać pełnych 20 sekund, skoro gracze już są gotowi.
            int shortened = plugin.getConfig().getInt("game.countdown-shortened-seconds", 10);
            if (shortened > 0 && arena.getCountdown() > shortened) {
                arena.setCountdown(shortened);
                broadcast(arena, "join-accelerated", player.getName(), shortened);
            }
        }
        // pasek areny: pokaż wszystkim czekającym aktualny stan (liczba graczy / odliczanie)
        for (UUID uuid : arena.getPlayers()) {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p != null) updateArenaStateBar(p, arena);
        }

        // === automatyczne dołączanie członków party ===
        // Jeśli gracz jest w party, dołącz jego członków do tej samej areny.
        // joiningWithParty zapobiega rekurencji (auto-join wywołuje addPlayer,
        // które znów mogłoby triggerować auto-join).
        if (!joiningWithParty) {
            UUID partyId = plugin.getPartyManager().findPartyId(player.getUniqueId());
            if (partyId != null) {
                joiningWithParty = true;
                try {
                    for (UUID memberId : plugin.getPartyManager().getMembers(partyId)) {
                        if (memberId.equals(player.getUniqueId())) continue;
                        if (arena.getPlayers().contains(memberId)) continue; // już na tej arenie
                        Player memberPlayer = plugin.getServer().getPlayer(memberId);
                        if (memberPlayer == null || !memberPlayer.isOnline()) continue;
                        // sprawdź czy gracz nie jest już na innej arenie
                        boolean alreadyOnArena = false;
                        for (Arena a : plugin.getArenaManager().getArenas().values()) {
                            if (a.isPlaying(memberPlayer)) { alreadyOnArena = true; break; }
                        }
                        if (alreadyOnArena) continue;
                        boolean ok = addPlayer(memberPlayer, arena);
                        if (ok) {
                            player.sendMessage(Component.text(
                                    "§aDołączono Twojego znajomego §f" + memberPlayer.getName() + " §ado areny!"));
                        }
                    }
                } finally {
                    joiningWithParty = false;
                }
            }
        }

        return true;
    }

    public void removePlayer(Player player, Arena arena) {
        arena.getPlayers().remove(player.getUniqueId());
        arena.getSpectators().remove(player.getUniqueId());
        arena.unmarkOnField(player.getUniqueId()); // znak „na polu" nie dotyczy już tego gracza
        // opuszczając grę, gracz bez jawnego wyboru traci losowo nadany kit (następna gra = nowe losowanie)
        plugin.getKitManager().clearRandomChoice(player);
        // defensively clear scoreboard and restore pre-arena inventory
        try {
            removeScoreboardForPlayer(player);
            hideBossBarForPlayer(player);
            restoreInventory(player);
            player.setAllowFlight(false);
            player.setFlying(false);
            player.setGameMode(GameMode.SURVIVAL);
            player.setHealth(20.0);
            player.setFoodLevel(20);
            player.setSaturation(20f);
        } catch (Throwable ignored) {}

        Location exit = exitLocation(arena);
        if (exit != null) player.teleport(exit);

        if (arena.getState() == ArenaState.INGAME && arena.getPlayers().isEmpty()) {
            endGame(arena, false);
        } else if (arena.getState() == ArenaState.STARTING && arena.getPlayers().size() < arena.getMinPlayers()) {
            cancelCountdown(arena);
        }
        // odśwież pasek pozostałych graczy (zmieniła się liczba czekających)
        for (UUID uuid : arena.getPlayers()) {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p != null) updateArenaStateBar(p, arena);
        }
        // instancja bez graczy (lobby się opróżniło) - zamknij i usuń jej świat
        plugin.getInstanceManager().maybeDispose(arena);
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
                    broadcast(arena, "start-countdown", arena.getCountdown());
                }
                // pasek co sekundę pokazuje odliczanie do startu
                for (UUID uuid : arena.getPlayers()) {
                    Player p = plugin.getServer().getPlayer(uuid);
                    if (p != null) updateStartingBar(p, arena);
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
        // wróć do paska „czekanie na graczy"
        for (UUID uuid : arena.getPlayers()) {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p != null) updateWaitingBar(p, arena);
        }
    }

    private void startGame(Arena arena) {
        arena.setState(ArenaState.INGAME);
        arena.setRound(0);
        plugin.getBlockRestoreListener().reset();

        int i = 0;
        for (UUID uuid : arena.getPlayers()) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player == null) continue;
            // zamknij otwarte GUI wyboru klasy, żeby gracz nie miał okna w trakcie rundy
            closeKitGui(player);
            // Gracze, którzy wybrali kit wcześniej (w poczekalni), są już na arenie
            // z kitem w ekwipunku - start rundy ich nie teleportuje ponownie ani nie
            // nadpisuje ekwipunku (wybór wcześniejszy zostaje nietknięty).
            // Gracze BEZ wyboru dostają tutaj LOSOWY kit i dopiero wtedy teleport
            // na punkt spawnu (czekali w lobby do końca odliczania).
            if (!arena.isOnField(uuid)) {
                Location spawn = arena.effectivePlayerSpawns().get(i % arena.effectivePlayerSpawns().size());
                Location lookForward = spawn.clone();
                lookForward.setYaw(0.0f);
                lookForward.setPitch(0.0f);
                player.teleport(lookForward);
                // resolveKitId: wybrany kit albo LOSOWY, jeśli gracz niczego nie wybrał
                String kitId = plugin.getKitManager().resolveKitId(player);
                // giveKit daje wszystko jednorazowo na starcie areny (broń, jedzenie, mikstury, strzały)
                plugin.getKitManager().giveKit(player, kitId);
                arena.markOnField(uuid);
            }
            player.setGameMode(GameMode.SURVIVAL);
            player.setHealth(20.0);
            player.setFoodLevel(20);

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
        // zegar ze stojaka („zegar_wskazowki") startuje razem z areną
        plugin.getClockManager().startClock(arena);
        spawnVillagers(arena);
        broadcast(arena, "game-started");
        // tabliczki rotacji map wyświetlające tę mapę losują inną (gra wystartowała)
        plugin.getSignManager().onGameStarted(arena);
        startRound(arena);
    }

    /**
     * Zamyka otwarte GUI wyboru klasy/kitu (wybór, podgląd przedmiotów i podgląd operatora).
     * Inne okna (np. sklep) zostają nietknięte.
     */
    private void closeKitGui(Player player) {
        try {
            String title = player.getOpenInventory().getTitle();
            if (KitManager.isKitSelectorTitle(player, title)
                    || KitManager.isKitPreviewTitle(player, title)
                    || KitManager.isKitDetailTitle(player, title)) {
                player.closeInventory();
            }
        } catch (Throwable ignored) {
            // brak otwartego okna lub problem z API - ignoruj
        }
    }

    private void spawnVillagers(Arena arena) {
        arena.getLivingVillagers().clear();
        int count = plugin.getConfig().getInt("game.villager-count-per-arena", 3);
        for (int i = 0; i < count && !arena.effectiveVillagerSpawns().isEmpty(); i++) {
            Location loc = arena.effectiveVillagerSpawns().get(i % arena.effectiveVillagerSpawns().size());
            Villager villager = (Villager) loc.getWorld().spawnEntity(loc, org.bukkit.entity.EntityType.VILLAGER);
            villager.setMetadata(MobSpawner.METADATA_KEY,
                    new org.bukkit.metadata.FixedMetadataValue(plugin, arena.getName()));
            villager.setInvulnerable(false);
            HealthDisplay.update(villager);
            arena.getLivingVillagers().add(villager);
        }
    }

    public void startRound(Arena arena) {
        arena.setRound(arena.getRound() + 1);
        // przedmioty z kitu są jednorazowe - wydawane tylko na starcie areny (giveKit),
        // bez dolewania co 3 rundy
        broadcast(arena, "round-started", arena.getRound());
        mobSpawner.spawnWave(arena, arena.getRound());
        // BossBar z falą X/Y i liczbą mobów do pokonania: pokazany na starcie fali,
        // aktualizowany co tick przez watcher (postęp paska = ile mobów zostało z fali)
        int waveTotal = arena.getSpawnedMobs().size();
        arenaWaveTotals.put(arena.getName(), waveTotal);
        for (UUID uuid : arena.getPlayers()) {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p != null) updateMobsBar(p, arena);
        }
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
                    // BossBar: fala X/Y, liczba pozostałych mobów + postęp paska wg całej fali
                    updateMobsBar(p, arena);
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
                    broadcast(arena, "round-finished", arena.getRound());
                    // WYGRANA: pokonano ostatnią falę areny (wg poziomu trudności)
                    int maxWaves = maxWaves(arena);
                    if (maxWaves > 0 && arena.getRound() >= maxWaves) {
                        endGame(arena, true);
                        return;
                    }
                    // fala skończona - pasek zostaje i pokazuje odliczanie do następnej fali
                    updateBreakBar(arena, breakSeconds, breakSeconds);
                    scheduleNextRoundCountdown(arena, breakSeconds);
                    plugin.getServer().getScheduler().runTaskLater(plugin,
                            () -> { if (arena.getState() == ArenaState.INGAME) startRound(arena); },
                            breakSeconds * 20L);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
        arenaTasks.put(arena.getName() + "-watcher", task);
    }

    /** Głośność (0-100) dźwięków danej kategorii dla gracza (klucz ustawienia z
     *  Ustawienia -> Dźwięki, np. sound-turrets dla wieżyczek). Stary przełącznik
     *  on/off („on"/„off") jest tłumaczony na 100/0, nowe wartości to %
     *  (0, 25, 50, 75, 100). Brak wpisu = 100 (pełna głośność). */
    public int soundVolume(Player player, String key) {
        if (player == null) return 100;
        var data = plugin.getPlayerDataManager().get(player.getUniqueId(), player.getName());
        String raw = data.getSetting(key);
        if (raw != null && raw.equalsIgnoreCase("off")) return 0; // stary przełącznik
        return data.getSettingInt(key, 100);
    }

    /** Ilość (0-100) cząsteczek danej kategorii dla gracza (klucz ustawienia z
     *  Ustawienia -> Cząsteczki, np. particles-turrets dla wieżyczek). Progi jak
     *  głośność dźwięków: 0, 25, 50, 75, 100. Brak wpisu = 100 (pełna ilość). */
    public int particleAmount(Player player, String key) {
        if (player == null) return 100;
        var data = plugin.getPlayerDataManager().get(player.getUniqueId(), player.getName());
        return data.getSettingInt(key, 100);
    }

    /** Widoczność (0-100) danej kategorii dla gracza (klucz ustawienia z
     *  Ustawienia -> Widoczności, np. visibility-mob-hp dla pasków HP mobów).
     *  Progi jak głośność dźwięków: 0, 25, 50, 75, 100. Brak wpisu = 100 (pełna widoczność). */
    public int visibilityLevel(Player player, String key) {
        if (player == null) return 100;
        var data = plugin.getPlayerDataManager().get(player.getUniqueId(), player.getName());
        return data.getSettingInt(key, 100);
    }

    /** Czy gracz słyszy dźwięki kategorii w ogóle (głośność > 0)? */
    public boolean soundEnabled(Player player, String key) {
        return soundVolume(player, key) > 0;
    }

    /** Odtwarza dźwięk do graczy w pobliżu z ICH indywidualną głośnością kategorii
     *  (klucz ustawienia z Ustawienia -> Dźwięki). Zamiast world.playSound dla
     *  dźwięków, które mają działać per gracz (np. umiejętności klas): gracz z 25%
     *  usłyszy go przy 1/4 głośności, z 0% wcale. */
    public void playSoundNearby(Location loc, Sound sound, float volume, float pitch, String settingKey) {
        if (loc == null || loc.getWorld() == null) return;
        double range = Math.max(1.0, volume) * 16.0; // waniliowy zasięg słyszalności
        for (Player p : loc.getWorld().getNearbyPlayers(loc, range)) {
            int vol = soundVolume(p, settingKey);
            if (vol <= 0) continue;
            p.playSound(loc, sound, volume * vol / 100f, pitch);
        }
    }

    /** Czy dźwięki mobów z fal są wyciszone na arenie? (Ktoś z graczy na arenie
     *  ma w Ustawieniach -> Dźwięki wyłączone „Moby z fal".) Używane TYLKO jako
     *  fallback, gdy serwer nie ma ProtocolLib - wtedy wyciszenie działa na całą
     *  arenę. Z ProtocolLib dźwięki filtrowane są per gracz w SoundMuteListener.
     */
    public boolean mobSoundsMuted(Arena arena) {
        for (UUID uuid : arena.getPlayers()) {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p != null && soundVolume(p, "sound-mobs") <= 0) {
                return true;
            }
        }
        return false;
    }

    /** Ustawia „cichość" (setSilent) na mobach z fal areny wg ustawień graczy -
     *  fallback bez ProtocolLib. Z ProtocolLib per-graczowe wyciszenie działa
     *  samo (pakiety), więc tu nic nie robimy. */
    public void refreshMobSoundMuting(Arena arena) {
        if (plugin.hasProtocolLib()) return;
        boolean muted = mobSoundsMuted(arena);
        for (LivingEntity m : arena.getSpawnedMobs()) {
            if (m != null && m.isValid()) m.setSilent(muted);
        }
    }

    /** Rejestruje mob przywołany przez gracza (sklep areny) na jego arenie -
     *  zniknie automatycznie, gdy gra na tej arenie dobiegnie końca. */
    public void registerPlayerSummon(Entity entity, Player player) {
        if (entity == null || player == null) return;
        for (Arena a : plugin.getArenaManager().getArenas().values()) {
            if (a.isPlaying(player)) {
                playerSummons.put(entity.getUniqueId(), a.getName());
                return;
            }
        }
    }

    /** Usuwa wszystkie moby przywołane przez graczy na danej arenie (koniec gry). */
    public void removePlayerSummons(Arena arena) {
        playerSummons.entrySet().removeIf(entry -> {
            if (!arena.getName().equals(entry.getValue())) return false;
            Entity ent = Bukkit.getEntity(entry.getKey());
            if (ent != null && ent.isValid()) ent.remove();
            return true;
        });
    }

    /**
     * Po wyborze kitu w poczekalni: jeśli gracz czeka na arenie (WAITING/STARTING),
     * dostaje wybrany kit OD RAZU do ekwipunku i jest teleportowany na punkt spawnu
     * graczy (zamiast czekać w lobby). Gracze bez wyboru zostają w lobby i dostają
     * losowy kit dopiero na starcie rundy (startGame). Wybór kitu nie jest przy
     * starcie nadpisywany.
     */
    public void onKitChosen(Player player) {
        Arena arena = null;
        for (Arena a : plugin.getArenaManager().getArenas().values()) {
            if (a.isPlaying(player)) {
                arena = a;
                break;
            }
        }
        if (arena == null) return; // wybór poza areną - tylko zapisany wybór, bez teleportu
        if (arena.getState() != ArenaState.WAITING && arena.getState() != ArenaState.STARTING) return;

        List<Location> spawns = arena.effectivePlayerSpawns();
        if (spawns.isEmpty()) return;
        Location spawn = spawns.get((int) (Math.random() * spawns.size()));
        Location lookForward = spawn.clone();
        lookForward.setYaw(0.0f);
        lookForward.setPitch(0.0f);
        player.teleport(lookForward);
        player.setGameMode(GameMode.SURVIVAL);
        player.setHealth(20.0);
        player.setFoodLevel(20);

        String kitId = plugin.getKitManager().resolveKitId(player);
        plugin.getKitManager().giveKit(player, kitId);
        arena.markOnField(player.getUniqueId());

        pl.vd.kits.Kit kit = plugin.getKitManager().getKit(kitId);
        Lang.sendActionBar(player, "teleported-with-kit", kit != null ? kit.localizedName(player) : kitId);
    }

    public void endGame(Arena arena, boolean won) {
        arena.setState(ArenaState.ENDING);
        // zegar ze stojaka wraca do stanu początkowego (wskazówki na start)
        plugin.getClockManager().stopClock(arena);
        BukkitTask task = arenaTasks.remove(arena.getName() + "-watcher");
        if (task != null) task.cancel();
        BukkitTask countdownTask = arenaTasks.remove(arena.getName() + "-break");
        if (countdownTask != null) countdownTask.cancel();
        // koniec gry - usuń BossBary graczy i zapamiętany rozmiar fali
        hideBossBars(arena);
        arenaWaveTotals.remove(arena.getName());

        // restore advancement announcements to default (true) for arena world
        if (!arena.getPlayerSpawns().isEmpty()) {
            Location any = arena.getPlayerSpawns().get(0);
            if (any != null && any.getWorld() != null) {
                any.getWorld().setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, true);
            }
        }

        // wygrana: pojedynczy gracz dostaje liczbę pojedynczą ("Wygrałeś!"),
        // dwóch i więcej - mnogą ("Wygraliście!")
        broadcast(arena, won ? (arena.getPlayers().size() > 1 ? "win" : "win-single") : "lose", arena.getRound());

        for (UUID uuid : arena.getPlayers()) {
            Player player = plugin.getServer().getPlayer(uuid);
            // zapomnij losowo nadany kit, żeby następna gra znów losowała (jawny wybór zostaje)
            if (player != null) plugin.getKitManager().clearRandomChoice(player);
            var data = plugin.getPlayerDataManager().get(uuid, player != null ? player.getName() : "?");
            data.addPoints(arena.getSessionPoints(uuid));
            data.addKills(arena.getSessionKills(uuid));
            data.addGamePlayed();
            data.updateHighestWave(arena.getRound());

            if (player != null) {
                // remove per-player scoreboard
                removeScoreboardForPlayer(player);

                player.setGameMode(GameMode.SURVIVAL);
                restoreInventory(player);
                player.setAllowFlight(false);
                player.setFlying(false);
                player.setHealth(20.0);
                player.setFoodLevel(20);
                player.setSaturation(20f);
                Location exit = exitLocation(arena);
                if (exit != null) player.teleport(exit);
                Lang.send(player, "session-points", arena.getSessionPoints(uuid));
            }
        }
        plugin.getPlayerDataManager().saveAll();

        for (var mob : arena.getSpawnedMobs()) if (mob.isValid()) mob.remove();
        for (var v : arena.getLivingVillagers()) if (v.isValid()) v.remove();
        // usuń skrzynie zrzutu zaopatrzenia
        plugin.getSupplyDropManager().cleanupArena(arena);
        // sprzątanie wszystkiego, co postawili gracze: wieżyczki (stojaki + części +
        // bariery), pajęczyny i miny - arena wraca do stanu sprzed gry
        cleanupPlacedItems(arena);
        // przywróć wszystkie zmienione bloki do stanu początkowego (trawa itd.)
        plugin.getBlockRestoreListener().restoreAll();
        // moby przywołane przez graczy ze sklepu (wilk, bałwan, obrońca, golem, warden)
        removePlayerSummons(arena);

        arena.resetSession();
        arena.setState(ArenaState.WAITING);
        // instancja dobiegła końca - usuń ją razem ze światem (klon jest jednorazowy)
        if (arena.isInstance()) {
            plugin.getInstanceManager().dispose(arena);
        }
    }

    /**
     * Usuwa po grze wszystko, co postawili gracze na arenie: wieżyczki (stojaki
     * z częściami i barierami), pajęczyny oraz miny. Świat areny ustalany jest z
     * punktów spawnu (fallback: lokalizacja końcowa).
     */
    private void cleanupPlacedItems(Arena arena) {
        org.bukkit.World world = null;
        if (!arena.getPlayerSpawns().isEmpty() && arena.getPlayerSpawns().get(0).getWorld() != null) {
            world = arena.getPlayerSpawns().get(0).getWorld();
        } else if (!arena.getVillagerSpawns().isEmpty() && arena.getVillagerSpawns().get(0).getWorld() != null) {
            world = arena.getVillagerSpawns().get(0).getWorld();
        } else if (arena.getEndLocation() != null && arena.getEndLocation().getWorld() != null) {
            world = arena.getEndLocation().getWorld();
        }
        if (world == null) return;
        plugin.getArmorStandTuner().destroyAllArenaTurrets(world, arena.getName());
        plugin.getBlockListener().removePlacedCobwebs(world);
        plugin.getMineManager().cleanupArena(world);
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
            if (timeLeft == 10 || timeLeft == 5) {
                broadcast(arena, "next-round-in", timeLeft);
            }
            // pasek co sekundę pokazuje ile zostało do następnej fali
            updateBreakBar(arena, timeLeft, seconds);
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
        // initial entries: Fala, Punkty, Zabójstwa, Czas (w języku gracza)
        String wave = Lang.get(player, "sb-wave", arena.getRound());
        String pts = Lang.get(player, "sb-points", 0);
        String kills = Lang.get(player, "sb-kills", 0);
        String time = Lang.get(player, "sb-time", "00:00");
        obj.getScore(wave).setScore(4);
        obj.getScore(pts).setScore(3);
        obj.getScore(kills).setScore(2);
        obj.getScore(time).setScore(1);
        player.setScoreboard(board);
        playerScoreboards.put(player.getUniqueId(), board);
        lastScoreboardLines.put(player.getUniqueId(), List.of(wave, pts, kills, time));
    }

    private void updatePlayerScoreboard(Player player, Arena arena) {
        Scoreboard board = playerScoreboards.get(player.getUniqueId());
        if (board == null) return;
        List<String> previous = lastScoreboardLines.getOrDefault(player.getUniqueId(), List.of());
        // remove previous lines
        for (String s : previous) {
            board.resetScores(s);
        }
        // build new lines (w języku gracza)
        String round = Lang.get(player, "sb-wave", arena.getRound());
        String pts = Lang.get(player, "sb-points", arena.getSessionPoints(player.getUniqueId()));
        String kills = Lang.get(player, "sb-kills", arena.getSessionKills(player.getUniqueId()));
        String time = Lang.get(player, "sb-time", formatElapsed(arena));
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

    /** Liczba fal do zwycięstwa dla areny wg jej poziomu trudności
     *  (config difficulties.<poziom>.waves; domyślnie 500 dla starych configów). */
    public int maxWaves(Arena arena) {
        int configured = plugin.getConfig().getInt("difficulties." + arena.getDifficulty() + ".waves", 500);
        return configured > 0 ? configured : 500;
    }

    /** Tworzy (jeśli trzeba) i ustawia BossBar gracza: tytuł + postęp (0.0-1.0). */
    private void setBossBar(Player player, String title, double progress) {
        BossBar bar = playerBossBars.get(player.getUniqueId());
        if (bar == null) {
            bar = Bukkit.createBossBar(title, BarColor.RED, BarStyle.SOLID);
            bar.addPlayer(player);
            playerBossBars.put(player.getUniqueId(), bar);
        }
        bar.setTitle(title);
        bar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
    }

    /** BossBar w trakcie fali: "Fala: X/Y — Pozostałe moby do pokonania: N"
     *  + postęp paska wg liczby mobów z całej fali. */
    private void updateMobsBar(Player player, Arena arena) {
        int total = arenaWaveTotals.getOrDefault(arena.getName(), arena.getSpawnedMobs().size());
        int remaining = arena.getSpawnedMobs().size();
        setBossBar(player,
                Lang.get(player, "bossbar-mobs-left", arena.getRound(), maxWaves(arena), Math.max(0, remaining)),
                total <= 0 ? 1.0 : (double) remaining / total);
    }

    /** BossBar w przerwie między falami: "Fala: X/Y — Następna fala za Z sekund"
     *  (postęp paska = ile zostało z przerwy). */
    private void updateBreakBar(Arena arena, int secondsLeft, int breakSeconds) {
        for (UUID uuid : arena.getPlayers()) {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p == null) continue;
            int left = Math.max(0, secondsLeft);
            setBossBar(p,
                    Lang.get(p, "bossbar-break", arena.getRound(), maxWaves(arena), left, Lang.secondsWord(p, left)),
                    breakSeconds <= 0 ? 1.0 : (double) left / breakSeconds);
        }
    }

    /** BossBar w poczekalni: "Fala: 0/Y — Czekanie na graczy: X/M". */
    private void updateWaitingBar(Player player, Arena arena) {
        setBossBar(player,
                Lang.get(player, "bossbar-waiting", arena.getRound(), maxWaves(arena),
                        arena.getPlayers().size(), arena.getMaxPlayers()),
                arena.getMaxPlayers() <= 0 ? 0.0 : (double) arena.getPlayers().size() / arena.getMaxPlayers());
    }

    /** BossBar podczas odliczania do startu: "Fala: 0/Y — Start za Z sekund". */
    private void updateStartingBar(Player player, Arena arena) {
        int cd = Math.max(0, arena.getCountdown());
        setBossBar(player,
                Lang.get(player, "bossbar-starting", arena.getRound(), maxWaves(arena), cd, Lang.secondsWord(player, cd)),
                1.0);
    }

    /** Aktualizuje pasek gracza wg aktualnego stanu areny (czekanie/start/fala).
     *  W przerwie między falami pasek odświeża osobny licznik (updateBreakBar). */
    private void updateArenaStateBar(Player player, Arena arena) {
        switch (arena.getState()) {
            case WAITING -> updateWaitingBar(player, arena);
            case STARTING -> updateStartingBar(player, arena);
            case INGAME -> {
                if (!arena.getSpawnedMobs().isEmpty()) updateMobsBar(player, arena);
            }
            default -> hideBossBarForPlayer(player);
        }
    }

    /** Usuwa BossBar danego gracza. */
    private void hideBossBarForPlayer(Player player) {
        BossBar bar = playerBossBars.remove(player.getUniqueId());
        if (bar != null) bar.removeAll();
    }

    // ===== PAUSE BOSS BAR (osobny, nie nadpisuje bossbara fali) =====

    /** Pokazuje/aktualizuje boss bar przerwy (zolty, osobny od bossbara fali). */
    public void setPauseBossBar(Player player, String title, double progress) {
        BossBar bar = pauseBossBars.get(player.getUniqueId());
        if (bar == null) {
            bar = Bukkit.createBossBar(title, BarColor.YELLOW, BarStyle.SOLID);
            bar.addPlayer(player);
            pauseBossBars.put(player.getUniqueId(), bar);
        }
        bar.setTitle(title);
        bar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
    }

    /** Ukrywa boss bar przerwy danego gracza. */
    public void hidePauseBossBar(Player player) {
        BossBar bar = pauseBossBars.remove(player.getUniqueId());
        if (bar != null) bar.removeAll();
    }

    /** Usuwa BossBary wszystkich graczy areny. */
    private void hideBossBars(Arena arena) {
        for (UUID uuid : arena.getPlayers()) {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p != null) hideBossBarForPlayer(p);
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

    /**
     * Miejsce, do którego gracz wraca po wyjściu z areny / zakończeniu gry.
     * Dla instancji (tymczasowych klonów szablonu) NIE jest to lokacja wewnątrz
     * instancji - świat instancji zaraz zniknie, więc gracz trafia do lokalizacji
     * końcowej SZABLONU.
     */
    private Location exitLocation(Arena arena) {
        if (arena.isInstance()) {
            Arena template = plugin.getArenaManager().getArena(arena.getTemplateName());
            if (template != null && template.getEndLocation() != null) return template.getEndLocation();
            if (template != null && template.getLobbyLocation() != null) return template.getLobbyLocation();
        }
        if (arena.getEndLocation() != null) return arena.getEndLocation();
        return arena.getLobbyLocation();
    }

    /** Zwraca tekst z listą brakujących elementów konfiguracji areny (w języku gracza). */
    private String brakujeKonfiguracji(Player player, Arena arena) {
        java.util.List<String> missing = new ArrayList<>();
        if (arena.getLobbyLocation() == null && arena.getEndLocation() == null) missing.add(Lang.get(player, "missing-lobby-end"));
        if (arena.effectivePlayerSpawns().isEmpty()) missing.add(Lang.get(player, "missing-player-spawns"));
        if (arena.effectiveVillagerSpawns().isEmpty()) missing.add(Lang.get(player, "missing-villager-spawns"));
        return missing.isEmpty() ? "" : Lang.get(player, "missing-list", String.join(", ", missing));
    }

    /** Wysyła ogłoszenie areny w JĘZYKU każdego gracza (klucz komunikatu + argumenty). */
    private void broadcast(Arena arena, String key, Object... args) {
        for (UUID uuid : arena.getPlayers()) {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p == null) continue;
            // gracz z wyłączonym kanałem czatu (Ustawienia -> Powiadomienia) nie widzi ogłoszeń
            var data = plugin.getPlayerDataManager().get(uuid, p.getName());
            if (data.getSettingInt("notif-chat", 100) <= 0) continue;
            p.sendMessage(Component.text(Lang.get(p, key, args)));
        }
    }

    /** Zapisuje aktualny ekwipunek gracza (główny + zbroja + offhand) przed wejściem na arenę. */
    private void saveInventory(Player player) {
        try {
            SavedInventory saved = new SavedInventory();
            saved.contents = player.getInventory().getContents().clone();
            saved.armor = player.getInventory().getArmorContents().clone();
            saved.offhand = player.getInventory().getItemInOffHand().clone();
            savedInventories.put(player.getUniqueId(), saved);
        } catch (Throwable ignored) {}
    }

    /** Przywraca zapisany przed areną ekwipunek gracza (jeśli istnieje), potem go zapomni. */
    private void restoreInventory(Player player) {
        SavedInventory saved = savedInventories.remove(player.getUniqueId());
        try {
            player.getInventory().clear();
            player.getInventory().setArmorContents(new org.bukkit.inventory.ItemStack[4]);
            if (saved != null) {
                player.getInventory().setContents(saved.contents);
                player.getInventory().setArmorContents(saved.armor);
                player.getInventory().setItemInOffHand(saved.offhand);
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Sprzątanie przy wyłączaniu serwera (onDisable):
     * - teleportuje graczy z areny do lobby (lub lokacji końcowej)
     * - czyści ekwipunek, zbroję, scoreboard i tryb gry
     * - usuwa moby i wieśniaków z areny, żeby nie zostały zapisane w świecie
     * - resetuje sesję areny do stanu WAITING
     */
    public void shutdownCleanup() {
        for (Arena arena : plugin.getArenaManager().getArenas().values()) {
            // anuluj zaplanowane zadania areny
            for (String key : new ArrayList<>(arenaTasks.keySet())) {
                if (key.startsWith(arena.getName())) {
                    BukkitTask t = arenaTasks.remove(key);
                    if (t != null) t.cancel();
                }
            }
            arenaWaveTotals.remove(arena.getName());

            // usuń moby i wieśniaków (żeby nie zapisały się w świecie po restarcie)
            for (var mob : arena.getSpawnedMobs()) {
                if (mob != null && mob.isValid()) mob.remove();
            }
            for (var v : arena.getLivingVillagers()) {
                if (v != null && v.isValid()) v.remove();
            }
            // usuń skrzynie zrzutu zaopatrzenia
            plugin.getSupplyDropManager().cleanupArena(arena);

            // obsłuż wszystkich graczy areny (również widzów)
            Set<UUID> allPlayers = new HashSet<>(arena.getPlayers());
            allPlayers.addAll(arena.getSpectators());
            for (UUID uuid : allPlayers) {
                Player player = plugin.getServer().getPlayer(uuid);
                if (player == null) continue;
                try {
                    removeScoreboardForPlayer(player);
                    hideBossBarForPlayer(player);
                    restoreInventory(player);
                    player.setAllowFlight(false);
                    player.setFlying(false);
                    player.setGameMode(GameMode.SURVIVAL);
                    player.setHealth(20.0);
                    player.setFoodLevel(20);
                    player.setSaturation(20f);
                    Location dest = exitLocation(arena);
                    if (dest != null) player.teleport(dest);
                } catch (Throwable ignored) {}
            }

            // moby przywołane przez graczy ze sklepu znikają też przy wyłączaniu serwera
            removePlayerSummons(arena);

            // wyczyść sesję areny i przywróć stan oczekiwania
            arena.getSpawnedMobs().clear();
            arena.getLivingVillagers().clear();
            arena.resetSession();
            arena.setState(ArenaState.WAITING);
        }

        // zegary stojaków wracają do stanu początkowego (przy wyłączaniu serwera)
        plugin.getClockManager().resetAll();
    }
}
