package pl.vd;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;
import pl.vd.arena.ArenaManager;
import pl.vd.instance.InstanceManager;
import pl.vd.ability.AbilityManager;
import pl.vd.achievements.AchievementsGUI;
import pl.vd.achievements.AchievementsListener;
import pl.vd.brush.BrushManager;
import pl.vd.copy.CopyPasteManager;
import pl.vd.stand.ArmorStandTuner;
import pl.vd.stand.ClockManager;
import pl.vd.stand.StandPresetManager;
import pl.vd.restart.RestartBrigadierCommand;
import pl.vd.tpwand.TeleportWandManager;
import pl.vd.vslab.VerticalSlabManager;
import pl.vd.commands.AchievementsCommand;
import pl.vd.commands.ArenaCommand;
import pl.vd.commands.BrushCommand;
import pl.vd.commands.RegionCommand;
import pl.vd.commands.CopyCommand;
import pl.vd.commands.HubCommand;
import pl.vd.commands.DailyRewardCommand;
import pl.vd.commands.SettingsCommand;
import pl.vd.commands.StandCommand;
import pl.vd.commands.RaportyCommand;
import pl.vd.commands.FriendCommand;
import pl.vd.commands.PartyCommand;
import pl.vd.commands.TpWandCommand;
import pl.vd.commands.VSlabCommand;
import pl.vd.commands.VDCommand;
import pl.vd.game.GameManager;
import pl.vd.kits.KitManager;
import pl.vd.listeners.AbilityListener;
import pl.vd.listeners.AssassinListener;
import pl.vd.listeners.BlockListener;
import pl.vd.listeners.BowDamageListener;
import pl.vd.listeners.BrushListener;
import pl.vd.listeners.ChatAntiSpamListener;
import pl.vd.listeners.CommandRestrictionListener;
import pl.vd.listeners.CritListener;
import pl.vd.listeners.EntityListener;
import pl.vd.listeners.HealthDisplayListener;
import pl.vd.listeners.ItemDropListener;
import pl.vd.listeners.MineSpawnListener;
import pl.vd.mine.MineManager;
import pl.vd.summon.DefenderManager;
import pl.vd.summon.GolemManager;
import pl.vd.summon.WardenManager;
import pl.vd.listeners.MobRestrictionListener;
import pl.vd.listeners.NoDamageTicksListener;
import pl.vd.listeners.ThornsListener;
import pl.vd.listeners.ChickenEggListener;
import pl.vd.listeners.SignListener;
import pl.vd.sign.SignManager;
import pl.vd.supply.SupplyDropManager;
import pl.vd.listeners.PlayerListener;
import pl.vd.listeners.ProcaListener;
import pl.vd.player.PlayerDataManager;
import pl.vd.shield.ShieldManager;
import pl.vd.listeners.ShieldListener;
import pl.vd.listeners.ShopListener;
import pl.vd.listeners.SpectatorGuardListener;
import pl.vd.listeners.SoundMuteListener;
import pl.vd.listeners.TurretListener;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import pl.vd.listeners.StandListener;
import pl.vd.listeners.TeleportWandListener;
import pl.vd.shop.ShopGUI;
import pl.vd.world.VoidGenerator;

import java.io.File;
import java.util.Set;

public class VillagerDefensePlugin extends JavaPlugin {

    private ArenaManager arenaManager;
    private PlayerDataManager playerDataManager;
    private KitManager kitManager;
    private ShopGUI shopGUI;
    private GameManager gameManager;
    private BrushManager brushManager;
    private BlockListener blockListener;
    private pl.vd.region.RegionManager regionManager;
    private AbilityManager abilityManager;
    private ShieldManager shieldManager;
    private SignManager signManager;
    private MineManager mineManager;
    private DefenderManager defenderManager;
    private GolemManager golemManager;
    private WardenManager wardenManager;
    private CopyPasteManager copyPasteManager;
    private SupplyDropManager supplyDropManager;
    private VerticalSlabManager verticalSlabManager;
    private ArmorStandTuner armorStandTuner;
    private StandPresetManager standPresetManager;
    private ClockManager clockManager;
    private TeleportWandManager teleportWandManager;
    private ProcaListener procaListener;
    private InstanceManager instanceManager;
    private AchievementsGUI achievementsGUI;
    private pl.vd.achievements.AchievementRegistry achievementRegistry;
    private pl.vd.weapons.SniperRifleManager sniperRifleManager;
    private pl.vd.restart.RestartManager restartManager;
    private pl.vd.reports.ReportManager reportManager;
    private pl.vd.friend.FriendManager friendManager;
    private pl.vd.friend.PartyManager partyManager;
    private pl.vd.game.PauseManager pauseManager;
    private pl.vd.barrier.BarrierManager barrierManager;
    private pl.vd.listeners.PermanentFireListener permanentFireListener;
    private pl.vd.listeners.BlockRestoreListener blockRestoreListener;
    private pl.vd.stand.HeadsManager headsManager;
    private pl.vd.lobby.LobbyNPCManager lobbyNPCManager;
    private pl.vd.lobby.GameModeGUI gameModeGUI;
    private pl.vd.lobby.DailyRewardManager dailyRewardManager;
    private pl.vd.lobby.PlayerXPBar playerXPBar;
    private pl.vd.listeners.PlayerListener playerListener;
    private pl.vd.raft.RaftPlugin raftPlugin;
    private pl.vd.raft.latarenka.LatarenkaManager latarenkaManager;

    /**
     * Kopiuje zasób z JAR-a do folderu pluginu TYLKO wtedy, gdy pliku jeszcze nie ma.
     *
     * Po co własna metoda: {@code JavaPlugin#saveResource(String, false)} przy istniejącym pliku
     * nie nadpisuje go (i to jest w porządku), ale loguje `Could not save X to Y because X already
     * exists` na poziomie WARNING — czyli przy KAŻDYM starcie serwera. Log startu wyglądał wtedy
     * jak ostrzeżenie o błędzie, choć działo się dokładnie to, co powinno.
     */
    private void saveResourceIfMissing(String resource) {
        if (new File(getDataFolder(), resource).exists()) return;
        saveResource(resource, false);
    }

    @Override
    public void onEnable() {
        // usuń foldery pozostałe po instancjach (awaria serwera) - MUSI być PRZED
        // loadCustomWorlds(), żeby pozostałości nie zostały wczytane jako zwykłe światy
        InstanceManager.cleanupLeftovers(this);
        saveDefaultConfig();
        // instrukcja /stojak i gotowy preset wieżyczki do sklepu (/stojak place wiezyczka,
        // sklep „Inne") — kopiowane tylko wtedy, gdy plików jeszcze nie ma
        saveResourceIfMissing("stojak-info.txt");
        saveResourceIfMissing("stojaki/wiezyczka.yml");
        pl.vd.util.HealthDisplay.setStyle(getConfig().getString("health-display-style", "bar"));
        pl.vd.util.AntiSpam.init(this);
        pl.vd.util.Lang.init(this);

        // Wczytaj własne światy (mapy) PRZED wczytaniem aren — inaczej lokacje zapisane w tych
        // światach (spawny graczy/wieśniaków) nie wczytałyby się, bo świat jeszcze nie istnieje.
        loadCustomWorlds();

        this.arenaManager = new ArenaManager(this);
        // ponawia wczytywanie spawnów/lokacji aren, gdy świat załaduje się później (WorldLoadEvent)
        getServer().getPluginManager().registerEvents(arenaManager, this);
        this.instanceManager = new InstanceManager(this);
        this.playerDataManager = new PlayerDataManager(this);
        this.abilityManager = new AbilityManager(this);
        this.shieldManager = new ShieldManager(this);
        this.kitManager = new KitManager(this, abilityManager, shieldManager);
        this.gameManager = new GameManager(this);
        this.restartManager = new pl.vd.restart.RestartManager(this);
        this.brushManager = new BrushManager(this);
        this.signManager = new SignManager(this);
        this.mineManager = new MineManager(this);
        this.defenderManager = new DefenderManager(this);
        this.golemManager = new GolemManager(this);
        this.wardenManager = new WardenManager(this);
        this.copyPasteManager = new CopyPasteManager(brushManager);
        this.verticalSlabManager = new VerticalSlabManager();
        this.armorStandTuner = new ArmorStandTuner(this);
        this.standPresetManager = new StandPresetManager(this, armorStandTuner);
        this.clockManager = new ClockManager(this, armorStandTuner);
        // Sklep musi być zbudowany PO menedżerze stojaków - loadFromConfig sprawdza,
        // czy preset wieżyczki istnieje (fallback dla starych configów).
        this.shopGUI = new ShopGUI(this);
        // Preload klas sklepu (w tym enuma ShopHolder.ViewType) już przy starcie serwera.
        // Bez tego pierwsza komenda /def shop_arena ładuje je leniwie — gdyby jar pluginu
        // został podmieniony podczas działania serwera, taka lazy inicjalizacja mogłaby
        // trafić na niespójny stan pliku i skończyć się NoClassDefFoundError (przypadek
        // z 02:20), którego JVM nie odzyskuje do restartu.
        pl.vd.shop.ShopHolder.ViewType.values();
        this.teleportWandManager = new TeleportWandManager(this);
        this.sniperRifleManager = new pl.vd.weapons.SniperRifleManager(this);
        this.achievementsGUI = new AchievementsGUI(this);
        this.achievementRegistry = new pl.vd.achievements.AchievementRegistry(this);
        this.regionManager = new pl.vd.region.RegionManager(this);
        this.supplyDropManager = new SupplyDropManager(this);
        this.supplyDropManager.start();
        this.reportManager = new pl.vd.reports.ReportManager(this);
        this.friendManager = new pl.vd.friend.FriendManager(this);
        this.partyManager = new pl.vd.friend.PartyManager(this);
        this.pauseManager = new pl.vd.game.PauseManager(this);
        this.barrierManager = new pl.vd.barrier.BarrierManager(this);
        this.permanentFireListener = new pl.vd.listeners.PermanentFireListener(this);
        this.blockRestoreListener = new pl.vd.listeners.BlockRestoreListener();
        this.headsManager = new pl.vd.stand.HeadsManager(this);
        this.lobbyNPCManager = new pl.vd.lobby.LobbyNPCManager(this);
        this.gameModeGUI = new pl.vd.lobby.GameModeGUI(this);
        this.dailyRewardManager = new pl.vd.lobby.DailyRewardManager(this);
        this.playerXPBar = new pl.vd.lobby.PlayerXPBar(this);
        lobbyNPCManager.loadAll();
        getServer().getPluginManager().registerEvents(new pl.vd.lobby.LobbyNPCListener(this), this);
        getServer().getPluginManager().registerEvents(new pl.vd.lobby.GameModeGUIListener(this), this);
        getServer().getPluginManager().registerEvents(new pl.vd.lobby.DailyRewardListener(this), this);

        getServer().getScheduler().runTaskTimer(this, () -> signManager.updateAllDisplays(), 20L, 20L);
        // autosave stojaków: co 60 sekund nadpisuje stojaki/autosave.yml (jeden plik,
        // wszystkie stojaki pluginu z pozycją) - kopia zapasowa na wypadek crasha/restartu
        getServer().getScheduler().runTaskTimer(this,
                () -> standPresetManager.writeAutosave(), 1200L, 1200L);
        // obrót górnej części wieżyczek: co tick (każdy stojak z prędkością > 0)
        getServer().getScheduler().runTaskTimer(this,
                () -> armorStandTuner.tickTurrets(), 1L, 1L);
        // wskazówki zegara ze stojaka („zegar_wskazowki"): co tick, skok dokładnie
        // na granicy sekundy/minuty/godziny (kąty liczone z czasu, nie z ticków)
        getServer().getScheduler().runTaskTimer(this,
                () -> clockManager.tick(), 1L, 1L);
        // animacja części (np. kryształowa kula wieżyczki maga): co tick
        getServer().getScheduler().runTaskTimer(this,
                () -> armorStandTuner.tickPartAnimations(), 1L, 1L);
        // trucizna wieżyczek (DoT 1 HP/s przez 8 s): co tick
        getServer().getScheduler().runTaskTimer(this,
                () -> armorStandTuner.tickPoisons(), 1L, 1L);
        // klik-przełączniki stanów stojaków (animacja przejścia A <-> B): co tick
        getServer().getScheduler().runTaskTimer(this,
                () -> armorStandTuner.tickStateTransitions(), 1L, 1L);
        // po restarcie: odzyskaj stojaki pluginu (tag/ części) i wznów obrót wieżyczek
        // z zapisanej prędkości - bez czekania na kliknięcie gracza
        getServer().getScheduler().runTaskLater(this, () -> {
            for (World w : Bukkit.getWorlds()) {
                for (Entity e : w.getEntities()) {
                    if (e instanceof ArmorStand stand) armorStandTuner.recover(stand);
                }
            }
        }, 60L);

        VDCommand command = new VDCommand(this);
        getCommand("def").setExecutor(command);
        getCommand("def").setTabCompleter(command);
        // /vd + aliasy /vdefense, /villagerdefense: plugin.yml deklaruje komendę "vd",
        // ale wykonawca był podłączony TYLKO do "def" - bez tego Bukkit nie miał
        // executora i pokazywał sam komunikat "Usage: /vd <...>" (domyślny
        // JavaPlugin#onCommand zwraca false), a ŻADNA komenda /vd, /vdefense
        // nie działała (mimo że jest na liście komend). Ten sam VDCommand co /def.
        if (getCommand("vd") != null) {
            getCommand("vd").setExecutor(command);
            getCommand("vd").setTabCompleter(command);
        }

        // /restart - NADPISANIE przez Brigadier (LifecycleEvents.COMMANDS), bo rejestracja
        // przez plugin.yml nie działała: Bukkit przy konflikcie z komendą Spigota chował
        // naszą pod villagerdefense:restart, a /restart zostawało komendą Spigota z
        // podpowiadaniem nicków. Brigadier podmienia węzeł w dispatcherze - bez nicków,
        // a TAB podpowiada "3" (albo "anuluj").
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register("restart", "Restart serwera", java.util.List.of(),
                    new RestartBrigadierCommand(this));
            getLogger().info("Komenda /restart nadpisana przez Brigadier (TAB = 3 s, bez nicków).");
        });

        ArenaCommand arenaCommand = new ArenaCommand(this);
        getCommand("arena").setExecutor(arenaCommand);
        getCommand("arena").setTabCompleter(arenaCommand);

        BrushCommand brushCommand = new BrushCommand(this);
        getCommand("brush").setExecutor(brushCommand);
        getCommand("brush").setTabCompleter(brushCommand);

        HubCommand hubCommand = new HubCommand(this);
        getCommand("hub").setExecutor(hubCommand);
        getCommand("lobby").setExecutor(hubCommand);
        getCommand("przerwa").setExecutor(hubCommand);

        DailyRewardCommand dailyRewardCommand = new DailyRewardCommand(this);
        getCommand("codzienna").setExecutor(dailyRewardCommand);

        CopyCommand copyCommand = new CopyCommand(this);
        getCommand("copy").setExecutor(copyCommand);
        getCommand("copy").setTabCompleter(copyCommand);

        VSlabCommand vslabCommand = new VSlabCommand(this);
        getCommand("vslab").setExecutor(vslabCommand);
        getCommand("vslab").setTabCompleter(vslabCommand);

        StandCommand standCommand = new StandCommand(this);
        getCommand("stojak").setExecutor(standCommand);
        getCommand("stojak").setTabCompleter(standCommand);

        TpWandCommand tpWandCommand = new TpWandCommand(this);
        getCommand("tpwand").setExecutor(tpWandCommand);
        getCommand("tpwand").setTabCompleter(tpWandCommand);

        RegionCommand regionCommand = new RegionCommand(this);
        getCommand("region").setExecutor(regionCommand);
        getCommand("region").setTabCompleter(regionCommand);

        SettingsCommand settingsCommand = new SettingsCommand(this);
        getCommand("ustawienia").setExecutor(settingsCommand);

        AchievementsCommand achievementsCommand = new AchievementsCommand(this);
        getCommand("osiagniecia").setExecutor(achievementsCommand);

        pl.vd.reports.ReportCommand reportCommand = new pl.vd.reports.ReportCommand(reportManager);
        getCommand("report").setExecutor(reportCommand);
        getCommand("report").setTabCompleter(reportCommand);

        RaportyCommand raportyCommand = new RaportyCommand(reportManager);
        getCommand("raporty").setExecutor(raportyCommand);

        FriendCommand friendCommand = new FriendCommand(this);
        getCommand("znajomi").setExecutor(friendCommand);
        getCommand("znajomi").setTabCompleter(friendCommand);

        PartyCommand partyCommand = new PartyCommand(this);
        getCommand("druzyna").setExecutor(partyCommand);
        getCommand("druzyna").setTabCompleter(partyCommand);

        getServer().getPluginManager().registerEvents(new CommandRestrictionListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatAntiSpamListener(this), this);
        playerListener = new PlayerListener(this);
        getServer().getPluginManager().registerEvents(playerListener, this);
        getServer().getPluginManager().registerEvents(new EntityListener(this), this);
        getServer().getPluginManager().registerEvents(new TurretListener(this), this);
        // po EntityListener, żeby isCancelled() łapało anulowania (np. zasięg sztyletu)
        getServer().getPluginManager().registerEvents(new CritListener(this), this);
        getServer().getPluginManager().registerEvents(new HealthDisplayListener(this), this);
        getServer().getPluginManager().registerEvents(new ItemDropListener(), this);
        getServer().getPluginManager().registerEvents(new ChickenEggListener(), this);
        getServer().getPluginManager().registerEvents(new MobRestrictionListener(this), this);
        getServer().getPluginManager().registerEvents(new NoDamageTicksListener(), this);
        getServer().getPluginManager().registerEvents(new ThornsListener(this), this);
        getServer().getPluginManager().registerEvents(new SignListener(this), this);
        blockListener = new BlockListener(this);
        getServer().getPluginManager().registerEvents(blockListener, this);
        getServer().getPluginManager().registerEvents(new BowDamageListener(this), this);
        getServer().getPluginManager().registerEvents(new BrushListener(this), this);
        getServer().getPluginManager().registerEvents(new AbilityListener(this), this);
        getServer().getPluginManager().registerEvents(sniperRifleManager, this);
        getServer().getPluginManager().registerEvents(new AssassinListener(this), this);
        getServer().getPluginManager().registerEvents(new pl.vd.listeners.BarrierListener(this), this);
        getServer().getPluginManager().registerEvents(permanentFireListener, this);
        getServer().getPluginManager().registerEvents(blockRestoreListener, this);
        getServer().getPluginManager().registerEvents(new ShieldListener(this), this);
        getServer().getPluginManager().registerEvents(new ShopListener(this), this);
        getServer().getPluginManager().registerEvents(new MineSpawnListener(this), this);
        getServer().getPluginManager().registerEvents(new StandListener(this), this);
        getServer().getPluginManager().registerEvents(new TeleportWandListener(this), this);
        getServer().getPluginManager().registerEvents(new SpectatorGuardListener(this), this);
        this.procaListener = new ProcaListener(this);
        getServer().getPluginManager().registerEvents(procaListener, this);
        getServer().getPluginManager().registerEvents(new AchievementsListener(this), this);

        // ProtocolLib (softdepend): per-graczowe wyciszanie dźwięków mobów z fal.
        // Bez ProtocolLib plugin działa dalej - wyciszenie mobów spada na tryb „cała arena".
        // Ważne: całość w try/catch - jeżeli ProtocolLib jest na serwerze, ale jest zepsuty
        // (np. brakuje mu zależności ByteBuddy i nie zarejestrował swoich klas), to nigdy
        // nie może to wyłączyć całego pluginu i zabić komend /vd.
        if (hasProtocolLib()) {
            try {
                ProtocolManager pm = ProtocolLibrary.getProtocolManager();
                pm.addPacketListener(new SoundMuteListener(this));
                getLogger().info("ProtocolLib wykryty - wyciszanie dźwięków mobów działa per gracz.");
            } catch (Throwable ex) {
                getLogger().warning("ProtocolLib jest obecny, ale nie udało się go użyć ("
                        + ex.getClass().getSimpleName() + ": " + ex.getMessage()
                        + ") - wyciszanie dzwiekow mobow dziala w trybie cala-arena.");
            }
        }

        // === Raft Plugin (sub-plugin) ===
        this.raftPlugin = new pl.vd.raft.RaftPlugin(this);
        raftPlugin.onEnable();
        pl.vd.raft.commands.RaftCommand raftCommand = new pl.vd.raft.commands.RaftCommand(raftPlugin);
        if (getCommand("raft") != null) {
            getCommand("raft").setExecutor(raftCommand);
            getCommand("raft").setTabCompleter(raftCommand);
        }

        // /wytwarzanie - GUI wytwarzania (tylko świat Raft); listener = kliknięcia
        // w kategorie GUI (Wyjście zamyka menu, kliknięcia anulowane)
        if (getCommand("wytwarzanie") != null) {
            pl.vd.raft.crafting.CraftingCommand craftingCommand =
                    new pl.vd.raft.crafting.CraftingCommand(raftPlugin);
            getCommand("wytwarzanie").setExecutor(craftingCommand);
            getServer().getPluginManager().registerEvents(craftingCommand, this);
            // Udostępnij GUI odbiornikowi przycisku moda (raft:open_crafting).
            raftPlugin.setCraftingCommand(craftingCommand);
        }

        // /muzyka - ustawienia muzyki trybu Raft per gracz (+ GUI 6×9: płyty
        // utworów, suwak głośności, częstotliwość, zapętlenie, wyjście)
        if (getCommand("muzyka") != null && raftPlugin.getMusicManager() != null) {
            pl.vd.raft.music.MuzykaCommand muzykaCommand =
                    new pl.vd.raft.music.MuzykaCommand(raftPlugin, raftPlugin.getMusicManager());
            getCommand("muzyka").setExecutor(muzykaCommand);
            getCommand("muzyka").setTabCompleter(muzykaCommand);
            getServer().getPluginManager().registerEvents(muzykaCommand, this);
        }

        // /latarenka - customowe latarenki z kolorowym światłem (tylko świat Raft)
        this.latarenkaManager = new pl.vd.raft.latarenka.LatarenkaManager(this);
        pl.vd.raft.latarenka.LatarenkaCommand latarenkaCommand = new pl.vd.raft.latarenka.LatarenkaCommand(this, latarenkaManager);
        if (getCommand("latarenka") != null) {
            getCommand("latarenka").setExecutor(latarenkaCommand);
            getCommand("latarenka").setTabCompleter(latarenkaCommand);
        }
        getServer().getPluginManager().registerEvents(new pl.vd.raft.latarenka.LatarenkaListener(this, latarenkaManager), this);

        getLogger().info("VillagerDefense włączony! Areny wczytane: " + arenaManager.getArenas().size());
    }

    @Override
    public void onDisable() {
        // wyłącz ewentualne odliczanie restartu (bez komunikatu - serwer i tak gaśnie)
        if (restartManager != null) restartManager.shutdown();
        // teleportuj graczy do lobby, wyczyść areny (moby/wieśniacy) i ekwipunki
        if (supplyDropManager != null) supplyDropManager.shutdown();
        if (gameManager != null) gameManager.shutdownCleanup();
        // zamknij aktywne instancje (usuń światy klonów) - pozostałości i tak
        // sprzątnie cleanupLeftovers przy następnym starcie
        if (instanceManager != null) instanceManager.disposeAll();
        if (playerDataManager != null) playerDataManager.saveAll();
        if (arenaManager != null) arenaManager.save();
        if (partyManager != null) partyManager.save();
        if (raftPlugin != null) raftPlugin.onDisable();
        getLogger().info("VillagerDefense wyłączony, areny posprzątane, dane zapisane.");
    }

    /**
     * Wczytuje przy starcie wszystkie własne światy (mapy), żeby po restarcie serwera
     * były od razu widoczne (np. w /def tpworld, na tabliczkach i w arenach) bez ręcznego
     * ładowania. Skanuje DWA miejsca:
     *  1) główne foldery światów w katalogu serwera (np. <serwer>/pustynna/),
     *  2) dimensje wewnątrz wczytanych światów (nowy format Paper: <world>/dimensions/<ns>/<nazwa>).
     */
    private void loadCustomWorlds() {
        Set<String> builtin = Set.of("world", "world_nether", "world_the_end",
                "overworld", "the_nether", "the_end");
        // Świat RAFT ładuje RaftPlugin (WorldGenerator.loadOrCreateWorld) z generatorem
        // oceanu (DeepOceanGenerator). Nie wolno go wczytać tu z VoidGenerator-em —
        // inaczej nowe chunki generowałyby się jako pustka (woda „urywała się").
        String raftWorldName = getConfig().getString("raft-world-name", "RAFT");

        // 1) top-level foldery światów w katalogu serwera
        File container = Bukkit.getWorldContainer();
        File[] rootFolders = container.listFiles();
        if (rootFolders != null) {
            for (File f : rootFolders) {
                if (!f.isDirectory()) continue;
                String name = f.getName();
                if (builtin.contains(name) || Bukkit.getWorld(name) != null) continue;
                if (name.equalsIgnoreCase(raftWorldName)) continue;
                if (!isWorldFolder(f)) continue;
                loadWorld(name);
            }
        }

        // 2) dimensje wewnątrz wczytanych światów (nowy format Paper).
        // Uwaga: w nowym formacie World#getWorldFolder() wskazuje folder dimensji
        // (np. world/dimensions/minecraft/overworld), więc szukamy folderu z "dimensions".
        for (World w : Bukkit.getWorlds()) {
            File dimsRoot = pl.vd.util.WorldUtil.findDimensionsRoot(w);
            if (dimsRoot == null) continue;
            File dims = new File(dimsRoot, "dimensions");
            File[] namespaces = dims.listFiles();
            if (namespaces == null) continue;
            for (File ns : namespaces) {
                if (!ns.isDirectory()) continue;
                File[] worlds = ns.listFiles();
                if (worlds == null) continue;
                for (File f : worlds) {
                    if (!f.isDirectory()) continue;
                    String name = f.getName();
                    if (builtin.contains(name) || Bukkit.getWorld(name) != null) continue;
                    if (name.equalsIgnoreCase(raftWorldName)) continue;
                    loadWorld(name);
                }
            }
        }
    }

    /** Czy folder wygląda jak zapisany świat (ma level.dat, region/ lub data/). */
    private boolean isWorldFolder(File folder) {
        return new File(folder, "level.dat").exists()
                || new File(folder, "region").isDirectory()
                || new File(folder, "data").isDirectory(); // dimensje nowego formatu mają folder data/
    }

    /** Wczytuje świat o podanej nazwie (jeśli dane już istnieją, generator jest ignorowany). */
    private void loadWorld(String name) {
        try {
            World created = new WorldCreator(name)
                    .generator(new VoidGenerator())
                    .environment(World.Environment.NORMAL)
                    .createWorld();
            if (created != null) {
                getLogger().info("Wczytano świat: " + name);
            } else {
                getLogger().warning("Nie udało się wczytać świata: " + name);
            }
        } catch (Exception ex) {
            getLogger().warning("Błąd podczas wczytywania świata " + name + ": " + ex.getMessage());
        }
    }

    public ArenaManager getArenaManager() { return arenaManager; }
    public InstanceManager getInstanceManager() { return instanceManager; }
    public PlayerDataManager getPlayerDataManager() { return playerDataManager; }
    public KitManager getKitManager() { return kitManager; }
    public ShopGUI getShopGUI() { return shopGUI; }
    public GameManager getGameManager() { return gameManager; }

    /**
     * Czy ProtocolLib jest zainstalowany I poprawnie włączony na serwerze (softdepend)?
     * Sama obecność pluginu nie wystarczy - jeśli ProtocolLib nie zarejestrował swoich
     * klas (np. brak ByteBuddy), to jego obiekt istnieje w managerze, ale użycie
     * ProtocolLibrary i tak rzuci NoClassDefFoundError i wyłączy cały nasz plugin.
     */
    public boolean hasProtocolLib() {
        org.bukkit.plugin.Plugin p = getServer().getPluginManager().getPlugin("ProtocolLib");
        return p != null && p.isEnabled();
    }
    public BrushManager getBrushManager() { return brushManager; }
    public AbilityManager getAbilityManager() { return abilityManager; }
    public ShieldManager getShieldManager() { return shieldManager; }
    public SignManager getSignManager() { return signManager; }
    public MineManager getMineManager() { return mineManager; }
    public DefenderManager getDefenderManager() { return defenderManager; }
    public GolemManager getGolemManager() { return golemManager; }
    public WardenManager getWardenManager() { return wardenManager; }

    public BlockListener getBlockListener() { return blockListener; }

    public pl.vd.region.RegionManager getRegionManager() { return regionManager; }
    public CopyPasteManager getCopyPasteManager() { return copyPasteManager; }

    public pl.vd.restart.RestartManager getRestartManager() { return restartManager; }

    /** Lokalizacja lobby serwera z config.yml (sekcja lobby). Gdy świat nie istnieje
     *  lub brak wpisów - pierwszy świat, 0 80 0. Używają jej /hub, /lobby i restart. */
    public Location getLobbyLocation() {
        String worldName = getConfig().getString("lobby.world", "");
        World world = worldName == null || worldName.isEmpty() ? null : Bukkit.getWorld(worldName);
        if (world == null) {
            world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        }
        if (world == null) return null;
        double x = getConfig().getDouble("lobby.x", 0.0);
        double y = getConfig().getDouble("lobby.y", 80.0);
        double z = getConfig().getDouble("lobby.z", 0.0);
        float yaw = (float) getConfig().getDouble("lobby.yaw", 0.0);
        float pitch = (float) getConfig().getDouble("lobby.pitch", 0.0);
        return new Location(world, x, y, z, yaw, pitch);
    }
    public SupplyDropManager getSupplyDropManager() { return supplyDropManager; }
    public ProcaListener getProcaListener() { return procaListener; }
    public VerticalSlabManager getVerticalSlabManager() { return verticalSlabManager; }
    public ArmorStandTuner getArmorStandTuner() { return armorStandTuner; }

    public ClockManager getClockManager() { return clockManager; }
    public StandPresetManager getStandPresetManager() { return standPresetManager; }
    public TeleportWandManager getTeleportWandManager() { return teleportWandManager; }
    public AchievementsGUI getAchievementsGUI() { return achievementsGUI; }
    public pl.vd.achievements.AchievementRegistry getAchievementRegistry() { return achievementRegistry; }
    public pl.vd.weapons.SniperRifleManager getSniperRifleManager() { return sniperRifleManager; }
    public pl.vd.reports.ReportManager getReportManager() { return reportManager; }
    public pl.vd.friend.FriendManager getFriendManager() { return friendManager; }
    public pl.vd.friend.PartyManager getPartyManager() { return partyManager; }
    public pl.vd.game.PauseManager getPauseManager() { return pauseManager; }
    public pl.vd.barrier.BarrierManager getBarrierManager() { return barrierManager; }
    public pl.vd.listeners.PermanentFireListener getPermanentFireListener() { return permanentFireListener; }
    public pl.vd.listeners.BlockRestoreListener getBlockRestoreListener() { return blockRestoreListener; }
    public pl.vd.stand.HeadsManager getHeadsManager() { return headsManager; }
    public pl.vd.lobby.LobbyNPCManager getLobbyNPCManager() { return lobbyNPCManager; }
    public pl.vd.lobby.GameModeGUI getGameModeGUI() { return gameModeGUI; }
    public pl.vd.lobby.DailyRewardManager getDailyRewardManager() { return dailyRewardManager; }
    public pl.vd.lobby.PlayerXPBar getPlayerXPBar() { return playerXPBar; }
    public pl.vd.listeners.PlayerListener getPlayerListener() { return playerListener; }
    public pl.vd.raft.RaftPlugin getRaftPlugin() { return raftPlugin; }
    public pl.vd.raft.latarenka.LatarenkaManager getLatarenkaManager() { return latarenkaManager; }
}
