package pl.vd.raft;

import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import pl.vd.raft.core.GameManager;
import pl.vd.raft.world.IslandManager;
import pl.vd.raft.world.WaterFiller;
import pl.vd.raft.world.WorldGenerator;
import pl.vd.raft.story.StoryManager;
import pl.vd.raft.story.DialogueManager;
import pl.vd.raft.items.ItemManager;
import pl.vd.raft.crafting.RecipeManager;
import pl.vd.raft.player.PlayerManager;
import pl.vd.raft.listeners.RaftListener;
import pl.vd.raft.config.ConfigManager;
import pl.vd.raft.hud.RaftHUDManager;
import pl.vd.raft.world.WaterDarknessManager;
import pl.vd.raft.blocks.DeviceBlock2x2;
import pl.vd.raft.blocks.DeviceBlockListener;
import pl.vd.raft.blocks.DeviceGUI;
import pl.vd.raft.blocks.DeviceRegistry;
import pl.vd.raft.blocks.UrzadzenieCommand;

/**
 * Wrapper trybu Raft — inicjalizowany przez VillagerDefensePlugin.
 * Nie jest osobnym pluginem, tylko sub-systemem w VillagerDefense.
 */
public class RaftPlugin {

    private static RaftPlugin instance;

    private final JavaPlugin parent;
    private GameManager gameManager;
    private IslandManager islandManager;
    private WorldGenerator worldGenerator;
    private StoryManager storyManager;
    private DialogueManager dialogueManager;
    private ItemManager itemManager;
    private RecipeManager recipeManager;
    private PlayerManager playerManager;
    private ConfigManager configManager;
    private RaftHUDManager hudManager;
    private WaterDarknessManager waterDarknessManager;
    private pl.vd.raft.listeners.RaftBuildListener raftBuildListener;
    private pl.vd.raft.listeners.RaftBuildPreview raftBuildPreview;
    private DeviceBlockListener deviceBlockListener;
    private DeviceRegistry deviceRegistry;
    private DeviceGUI deviceGUI;
    private pl.vd.raft.blocks.AntennaManager antennaManager;
    private pl.vd.raft.hook.HookManager hookManager;
    private pl.vd.raft.world.OceanDebrisManager oceanDebrisManager;
    private pl.vd.raft.crafting.CraftingKeyReceiver craftingKeyReceiver;
    private pl.vd.raft.crafting.CraftingCommand craftingCommand;
    private pl.vd.raft.music.MusicManager musicManager;
    private pl.vd.raft.settings.UstawieniaGUI ustawieniaGui;
    private pl.vd.raft.settings.DzwiekiGUI dzwiekiGui;

    public RaftPlugin(JavaPlugin parent) {
        this.parent = parent;
    }

    public void onEnable() {
        instance = this;

        // Inicjalizacja managerow
        this.configManager = new ConfigManager(this);
        this.islandManager = new IslandManager(this);
        this.worldGenerator = new WorldGenerator(this);
        this.storyManager = new StoryManager(this);
        this.dialogueManager = new DialogueManager(this);
        this.itemManager = new ItemManager(this);
        this.recipeManager = new RecipeManager(this);
        this.playerManager = new PlayerManager(this);
        this.gameManager = new GameManager(this);
        this.hudManager = new RaftHUDManager(this);
        this.hudManager.enable();
        this.waterDarknessManager = new WaterDarknessManager(this);
        this.deviceBlockListener = new DeviceBlockListener();
        parent.getServer().getPluginManager().registerEvents(deviceBlockListener, parent);
        this.deviceGUI = new DeviceGUI();
        parent.getServer().getPluginManager().registerEvents(deviceGUI, parent);

        // Faza 2 projektu anten: manager + listener stawiania przedmiotem PPM.
        this.antennaManager = new pl.vd.raft.blocks.AntennaManager(this);
        parent.getServer().getPluginManager().registerEvents(
                new pl.vd.raft.listeners.AntennaPlaceListener(this, antennaManager), parent);

        // Żagiel tratwy (CMD 4005): PPM stawia maszt z żaglem na pokładzie,
        // rozwija/zwija go i demontuje (komponenty MAST + SAIL w zapisie tratwy).
        parent.getServer().getPluginManager().registerEvents(
                new pl.vd.raft.listeners.SailPlaceListener(this), parent);
        
        // Inicjalizacja rejestru urządzeń
        this.deviceRegistry = new DeviceRegistry();
        registerDefaultDevices();
        // Faza 0 projektu anten: urządzenia przeżywają restart — odtwórz z devices.yml
        // (po rejestracji typów; placeDevice wymaga zarejestrowanych DeviceType).
        pl.vd.raft.blocks.DevicePersistence.loadAll(deviceRegistry);
        
        // Rejestracja komendy /urzadzenie
        UrzadzenieCommand urzadzenieCommand = new UrzadzenieCommand(this, deviceRegistry);
        parent.getCommand("urzadzenie").setExecutor(urzadzenieCommand);
        parent.getCommand("urzadzenie").setTabCompleter(urzadzenieCommand);

        // Rejestracja listenerow
        parent.getServer().getPluginManager().registerEvents(new RaftListener(this), parent);
        // Ochrona entity tratwy (shulkery-kolizje + BlockDisplay deski) przed zniszczeniem
        parent.getServer().getPluginManager().registerEvents(new pl.vd.raft.entity.RaftListener(), parent);
        pl.vd.raft.listeners.ItemPickupManager itemPickupManager = new pl.vd.raft.listeners.ItemPickupManager(this);
        parent.getServer().getPluginManager().registerEvents(itemPickupManager, parent);
        parent.getServer().getPluginManager().registerEvents(new pl.vd.raft.listeners.TreeBreakManager(this, itemPickupManager), parent);
        parent.getServer().getPluginManager().registerEvents(new pl.vd.raft.listeners.FlowerPickupManager(this), parent);
        pl.vd.raft.listeners.OxygenBottleListener oxygenBottleListener = new pl.vd.raft.listeners.OxygenBottleListener(this);
        parent.getServer().getPluginManager().registerEvents(oxygenBottleListener, parent);
        oxygenBottleListener.start();
        parent.getServer().getPluginManager().registerEvents(new pl.vd.raft.listeners.NetLauncherListener(), parent);
        parent.getServer().getPluginManager().registerEvents(new pl.vd.raft.listeners.ShearsListener(), parent);
        parent.getServer().getPluginManager().registerEvents(new pl.vd.raft.listeners.SpearBlockListener(), parent);
        parent.getServer().getPluginManager().registerEvents(new pl.vd.raft.listeners.CustomArrowListener(), parent);
        parent.getServer().getPluginManager().registerEvents(new pl.vd.raft.listeners.ConstructionHammerListener(), parent);

        // Faza 4 — budowanie tratwy: podłoga/ściana/schody prawym klikiem
        // (komórki podstawowej tratwy są chronione). Przy okazji wczytujemy
        // ustawienia kolizji ześlizgowej tratwy (raft-collision w config.yml).
        reloadRaftConfig();
        this.raftBuildListener = new pl.vd.raft.listeners.RaftBuildListener(this);
        parent.getServer().getPluginManager().registerEvents(raftBuildListener, parent);

        // Faza 4 — młot budowlany: shift+PPM otwiera GUI młota (kategorie →
        // strony 9×4), wybór bloku pokazuje podgląd-ducha (PPM zatwierdza).
        parent.getServer().getPluginManager().registerEvents(
                new pl.vd.raft.listeners.RaftHammerListener(this), parent);
        this.raftBuildPreview = new pl.vd.raft.listeners.RaftBuildPreview(this);
        parent.getServer().getPluginManager().registerEvents(raftBuildPreview, parent);
        parent.getServer().getPluginManager().registerEvents(new pl.vd.raft.listeners.BucketListener(this), parent);

        // Faza 5 — siekiera: rozbiórka tratwy. Siekiera w ręce podświetla
        // rozbieralne komórki na zielono (jak podgląd-duch młota), a lewy klik
        // w komórkę zadaje uderzenia (drewniana/kamienna 3, żelazna/diamentowa 2).
        parent.getServer().getPluginManager().registerEvents(
                new pl.vd.raft.listeners.RaftAxeListener(this), parent);

        // Faza 6 — sieć zbierająca (CellType.NET): łapie pływające przedmioty
        // (do 10 sztuk), PPM (obojętnie czym) = GUI 9×1, shift+PPM = zabierz wszystko.
        parent.getServer().getPluginManager().registerEvents(
                new pl.vd.raft.listeners.CollectionNetListener(this), parent);

        // Skrzynki na tratwie (CellType.CHEST_18/36/54): PPM otwiera GUI o
        // pojemności 18/36/54 slotów; zawartość zapisywana z tratwą. Listener
        // trzyma też lustro GUI → skrzynka (co 2 ticki), więc klikanie jest
        // waniliowe (kursor), a stan GUI ląduje w skrzyni na bieżąco.
        pl.vd.raft.listeners.RaftChestListener chestListener =
                new pl.vd.raft.listeners.RaftChestListener(this);
        chestListener.start();
        parent.getServer().getPluginManager().registerEvents(chestListener, parent);

        // Faza 7 — pole śmieci oceanicznych: regularne rzędy pływających
        // przedmiotów i beczek przed dziobem tratwy (w kierunku dryfu), do
        // złapania siecią lub ręcznie. Ustawienia: config.yml → raft-debris.
        this.oceanDebrisManager = new pl.vd.raft.world.OceanDebrisManager(this);
        parent.getServer().getPluginManager().registerEvents(oceanDebrisManager, parent);
        oceanDebrisManager.start();

        // Faza 8 — Hak (baza: wędka FISHING_ROD): PPM rzuca linkę z hakiem,
        // TRZYMANIE PPM (gracze z modem) przyciąga tratwę; bez moda wbity hak
        // ciągnie automatycznie, aż tratwa dopłynie.
        this.hookManager = new pl.vd.raft.hook.HookManager(this);
        this.hookManager.start();
        parent.getServer().getPluginManager().registerEvents(hookManager, parent);

        // Przycisk moda (raft:open_crafting, domyślnie Z) → GUI wytwarzania.
        // Walidacja: działa TYLKO w trybie Raft, poza nim całkowita cisza.
        this.craftingKeyReceiver = new pl.vd.raft.crafting.CraftingKeyReceiver(this);
        parent.getServer().getPluginManager().registerEvents(craftingKeyReceiver, parent);

        // Muzyka trybu Raft: losowe utwory z paczki (raft-music.tracks) z losowymi
        // przerwami, ustawienia PER GRACZ (raft-players.yml). Pętla 1 Hz sama wykrywa
        // wejścia/wyjścia ze świata Raft — bez osobnych listenerów.
        this.musicManager = new pl.vd.raft.music.MusicManager(this);
        this.musicManager.start();

        // Panel USTAWIEŃ Rafta (GUI 4x9: Dźwięki, Powiadomienia, Muzyka, Język).
        // Otwiera go /ustawienia w świecie Raft (SettingsCommand); listener
        // obsługuje kliknięcia (przełączniki + otwarcie GUI z /muzyka).
        this.ustawieniaGui = new pl.vd.raft.settings.UstawieniaGUI(this);
        parent.getServer().getPluginManager().registerEvents(ustawieniaGui, parent);

        // Podmenu DŹWIĘKI (GUI 6x9: master + kategorie dźwięków + Wyjdź) —
        // otwierane kliknięciem „Dźwięki” w panelu ustawień; wspólny magazyn
        // ustawień (sesja/pamięć) z panelem.
        this.dzwiekiGui = new pl.vd.raft.settings.DzwiekiGUI(this, ustawieniaGui);
        ustawieniaGui.setDzwiekiGui(dzwiekiGui);
        parent.getServer().getPluginManager().registerEvents(dzwiekiGui, parent);

        // Załaduj lub wygeneruj świat Raft
        // Jeśli folder świata istnieje (np. wrzucony ręcznie z gotowym oceanem)
        // — ładuje go. Jeśli nie — generuje ocean fallback.
        String worldName = parent.getConfig().getString("raft-world-name", "RAFT");
        parent.getServer().getScheduler().runTaskLater(parent, () -> {
            World world = worldGenerator.loadOrCreateWorld(worldName);
            if (world == null) {
                parent.getLogger().severe("[Raft] Nie udało się załadować/generować świata!");
            } else {
                // Usuń stare elementy tratw z poprzednich wersji pluginu
                // (pływające OAK_SLAB, BARRIER, orphan BlockDisplay/FallingBlock)
                cleanupLeftoverRaftBlocks(world);
                // Napraw puste chunki (void) z poprzednich wersji — woda + piasek.
                // Pomija kolumny, które już mają wodę lub wyspę (idempotentne),
                // więc można to uruchamiać przy każdym starcie serwera.
                new WaterFiller(this).fillAsync(world);
            }
        }, 1L);

        // Zadanie co tick — przedmioty w wodzie: NIE pchamy ich ciągle do góry.
        // Wanillowa fizyka MC sama utrzymuje itemy na powierzchni (kołyszą się),
        // a wymuszanie velocity 0.15 co tick robiło z nich „skaczące" przedmioty:
        // pchnięcie wybijało je nad wodę, grawitacja ściągała z powrotem, pchnięcie
        // znowu… w nieskończoność.
        // Interweniujemy TYLKO, gdy przedmiot wyraźnie TONIE (co najmniej ~1.2
        // bloka poniżej linii pływania i nie wznosi się) — wtedy delikatnie
        // niwelujemy opadanie. W strefie kołysania przy powierzchni velocity
        // nie jest ruszane.
        parent.getServer().getScheduler().runTaskTimer(parent, () -> {
            String raftWorldName = parent.getConfig().getString("raft-world-name", "RAFT");
            for (World w : parent.getServer().getWorlds()) {
                if (!w.getName().equalsIgnoreCase(raftWorldName)) continue;
                for (Item item : w.getEntitiesByClass(Item.class)) {
                    Block block = item.getLocation().getBlock();
                    if (!block.isLiquid()) continue;

                    // Powierzchnia wody = pierwszy nie-płynny blok powyżej itemu
                    int surfaceY = block.getY();
                    for (int y = block.getY(); y <= w.getMaxHeight(); y++) {
                        if (!w.getBlockAt(block.getX(), y, block.getZ()).isLiquid()) {
                            surfaceY = y;
                            break;
                        }
                    }

                    // Tonie? — głęboko pod powierzchnią i bez własnego wznoszenia
                    Vector vel = item.getVelocity();
                    if (item.getLocation().getY() < surfaceY - 1.2 && vel.getY() < 0.05) {
                        // Zgaś opadanie + lekki powrót ku powierzchni (zależny od
                        // prędkości opadania — bez podskakiwania przy wynurzaniu)
                        double push = vel.getY() >= 0 ? 0.02 : Math.min(0.10, 0.06 - vel.getY() * 0.4);
                        item.setVelocity(new Vector(vel.getX(), push, vel.getZ()));
                    }
                }
            }
        }, 1L, 1L);

        // Inicjalizacja systemu pakietow offsetow (ProtocolLib)
        pl.vd.raft.net.RaftOffsetPacketSender.getInstance().init();
        pl.vd.raft.net.RaftStatsPacketSender.getInstance().init();

        // Faza 1 projektu anten: pakiet raft:receivers — mod renderuje odbiornik
        // na nośniku-beczce. Pętla 1 s buforuje join gracza (mod czyści listę przy JOIN),
        // broadcast od razu po postawieniu/usunięciu idzie z UrzadzenieCommand.
        pl.vd.raft.net.RaftReceiversPacketSender receiversSender = pl.vd.raft.net.RaftReceiversPacketSender.getInstance();
        receiversSender.init();
        receiversSender.start(parent);

        // Żagiel tratwy: pakiet `raft:sails` — pozycja podstawy masztu, obrót
        // tratwy i stan (rozwinięty/zwinięty). Tratwa PŁYWA, więc pozycja leci
        // co 2 ticki (10 Hz) i klient wygładza ruch między pakietami.
        pl.vd.raft.net.RaftSailPacketSender sailsSender = pl.vd.raft.net.RaftSailPacketSender.getInstance();
        sailsSender.init();
        sailsSender.start(parent);

        // Faza 2: pętla walidacji anten (zerwanie/przepinanie, co 2 s).
        antennaManager.start();

        // Faza 2 — pakietowe LOD tratw: per-gracz kadencja pakietów ruchu encji
        // tratwy (softdepend ProtocolLib; bez niego zostaje globalne LOD z Fazy 1).
        try {
            com.comphenix.protocol.ProtocolLibrary.getProtocolManager()
                    .addPacketListener(new pl.vd.raft.entity.RaftPacketLOD(parent));
        } catch (Throwable ex) {
            parent.getLogger().warning("[Raft] ProtocolLib niedostępny — pakietowe LOD tratw wyłączone ("
                    + ex.getClass().getSimpleName() + ": " + ex.getMessage() + ")");
        }

        parent.getLogger().info("Raft Plugin wlaczony!");
    }

    /**
     * Usuwa stare elementy tratw zostawione w świecie RAFT przez poprzednie
     * wersje pluginu: pływające OAK_SLAB (stare kolizje), BARRIER,
     * orphan BlockDisplay (dawne wizualizacje) i FallingBlock.
     * Uruchamiane raz przy starcie, tylko w pobliżu spawna.
     */
    /**
     * Czy encja to wizual lub kolizja ŻYWEJ tratwy (tag {@code raft_<uuid>} zarejestrowanej
     * w RaftManagerze). Chroni deski i podpory pięter przed sprzątaniem orphanów.
     */
    private static boolean belongsToLiveRaft(org.bukkit.entity.Entity entity) {
        for (String tag : entity.getScoreboardTags()) {
            if (!tag.startsWith("raft_")) continue;
            try {
                java.util.UUID id = java.util.UUID.fromString(tag.substring("raft_".length()));
                pl.vd.raft.entity.RaftManager manager = pl.vd.raft.entity.RaftManager.getInstance();
                if (manager != null && manager.getRaft(id) != null) return true;
            } catch (IllegalArgumentException ignored) {
                // tag nie-UUID (np. raft_collider / raft_visual) — nie jest to tag tratwy
            }
        }
        return false;
    }

    private void cleanupLeftoverRaftBlocks(World world) {
        int removed = 0;

        // 1. Orphan BlockDisplay entities (dawne wizualizacje raftu — OAK_PLANKS).
        // UWAGA: wizuale ŻYWEJ tratwy (deski i podpory pięter) mają tag
        // raft_<uuid> — te pomijamy, żeby sprzątanie nie zdjęło podpór.
        for (org.bukkit.entity.BlockDisplay d : world.getEntitiesByClass(org.bukkit.entity.BlockDisplay.class)) {
            if (belongsToLiveRaft(d)) continue;
            org.bukkit.Material mat = d.getBlock().getMaterial();
            if (mat == org.bukkit.Material.OAK_PLANKS || mat == org.bukkit.Material.OAK_SLAB
                    || d.getScoreboardTags().contains(pl.vd.raft.entity.RaftEntity.TAG_VISUAL)) {
                d.remove();
                removed++;
            }
        }

        // 1b. ItemDisplay z dębowych desek (stare „obrazki" desek) — sprzątamy tez te
        for (org.bukkit.entity.ItemDisplay id : world.getEntitiesByClass(org.bukkit.entity.ItemDisplay.class)) {
            if (belongsToLiveRaft(id)) continue;
            var item = id.getItemStack();
            if (item != null && item.getType() == org.bukkit.Material.OAK_PLANKS) {
                id.remove();
                removed++;
            }
        }

        // 2. Orphan FallingBlock entities (dawne kolizje — BARRIER + NoGravity)
        for (org.bukkit.entity.FallingBlock fb : world.getEntitiesByClass(org.bukkit.entity.FallingBlock.class)) {
            if (!fb.hasGravity() && fb.getBlockData().getMaterial() == org.bukkit.Material.BARRIER) {
                fb.remove();
                removed++;
            }
        }

        // 2b. Orphan Shulker-kolizje tratw (nowa architektura, po awarii/restarcie bez cleanupu)
        for (org.bukkit.entity.Shulker s : world.getEntitiesByClass(org.bukkit.entity.Shulker.class)) {
            if (s.getScoreboardTags().contains(pl.vd.raft.entity.RaftEntity.TAG_COLLIDER)) {
                s.remove();
                removed++;
            }
        }

        // 2c. Orphan marker-ArmorStand carriers tratw (nowa architektura)
        for (org.bukkit.entity.ArmorStand as : world.getEntitiesByClass(org.bukkit.entity.ArmorStand.class)) {
            if (as.getScoreboardTags().contains(pl.vd.raft.entity.RaftEntity.TAG_COLLIDER)) {
                as.remove();
                removed++;
            }
        }

        // 3. Bloki w pobliżu spawna: BARRIER + pływające OAK_SLAB nad wodą
        org.bukkit.Location spawn = world.getSpawnLocation();
        int seaLevel = world.getSeaLevel();
        int radius = 150;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = seaLevel - 3; y <= seaLevel + 6; y++) {
                    org.bukkit.block.Block b = world.getBlockAt(spawn.getBlockX() + x, y, spawn.getBlockZ() + z);
                    org.bukkit.Material t = b.getType();
                    if (t == org.bukkit.Material.BARRIER) {
                        b.setType(org.bukkit.Material.AIR, false);
                        removed++;
                    } else if (t == org.bukkit.Material.OAK_SLAB
                            && b.getBlockData() instanceof org.bukkit.block.data.type.Slab slab
                            && slab.getType() == org.bukkit.block.data.type.Slab.Type.TOP
                            && slab.isWaterlogged()) {
                        // Pływający TOP slab nad wodą = stara tratwa
                        org.bukkit.block.Block below = world.getBlockAt(b.getX(), b.getY() - 1, b.getZ());
                        if (below.getType() == org.bukkit.Material.WATER) {
                            b.setType(org.bukkit.Material.AIR, false);
                            removed++;
                        }
                    }
                }
            }
        }

        if (removed > 0) {
            parent.getLogger().info("[Raft] Posprzątano " + removed + " starych elementów tratw.");
        }
    }

    /** Pobiera listener urządzeń 2×2 */
    public DeviceBlockListener getDeviceBlockListener() {
        return deviceBlockListener;
    }
    
    /** Pobiera rejestr urządzeń */
    public DeviceRegistry getDeviceRegistry() {
        return deviceRegistry;
    }

    /** Manager anten (faza 2 projektu anten). */
    public pl.vd.raft.blocks.AntennaManager getAntennaManager() {
        return antennaManager;
    }
    
    /** Pobiera GUI urządzeń */
    public DeviceGUI getDeviceGUI() {
        return deviceGUI;
    }
    
    /** Rejestruje domyślne urządzenia */
    private void registerDefaultDevices() {
        // Moduł - pokazuje mapę z odległościami do wysp (2×2, z GUI)
        deviceRegistry.register("modul_odbiorczy", new DeviceRegistry.DeviceType(
            "§bModuł",
            "Pokazuje mapę z odległościami do wysp",
            1000,
            10,
            false // deviceBlock2x2 - stary layout z GUI
        ));

        // Wiatrak - 4 sloty na baterie, bez GUI (2×2×6)
        deviceRegistry.register("wiatrak", new DeviceRegistry.DeviceType(
            "§6Wiatrak",
            "Ładuje baterie automatycznie",
            1000,
            0,
            true // windmillDevice - nowy layout 2×2×6
        ));
        
        deviceRegistry.getLogger().info("Zarejestrowano " + deviceRegistry.getRegisteredNames().size() + " urządzeń.");
    }

    public pl.vd.raft.crafting.CraftingKeyReceiver getCraftingKeyReceiver() {
        return craftingKeyReceiver;
    }

    /** Dostęp do managera haków — zawiera KeyStateReceiver do wykrywania moda. */
    public pl.vd.raft.hook.HookManager getHookManager() {
        return hookManager;
    }

    public void setCraftingCommand(pl.vd.raft.crafting.CraftingCommand command) {
        this.craftingCommand = command;
    }

    public pl.vd.raft.crafting.CraftingCommand getCraftingCommand() {
        return craftingCommand;
    }

    public pl.vd.raft.settings.UstawieniaGUI getUstawieniaGui() {
        return ustawieniaGui;
    }

    public pl.vd.raft.settings.DzwiekiGUI getDzwiekiGui() {
        return dzwiekiGui;
    }

    public void onDisable() {
        // Faza 3: zapisz stan wszystkich tratw przed wyłączeniem serwera — dzięki
        // temu tratwy przetrwają restart (awaria pokryta autosave'em co 60 s).
        try {
            pl.vd.raft.entity.RaftManager raftManager = pl.vd.raft.entity.RaftManager.getInstance();
            pl.vd.raft.entity.RaftPersistence.saveAll(raftManager.getRafts());
        } catch (Throwable ignored) {
            // Zapis przy wyłączaniu nie może wywrócić wyłączania pluginu
        }
        // Faza 0 projektu anten: zapis urządzeń — bez tego znikały przy restarcie.
        try {
            if (deviceRegistry != null) {
                pl.vd.raft.blocks.DevicePersistence.saveAll(deviceRegistry);
            }
        } catch (Throwable ignored) {
            // Zapis przy wyłączaniu nie może wywrócić wyłączania pluginu
        }
        if (hudManager != null) {
            hudManager.disable();
        }
        if (musicManager != null) {
            musicManager.stop();
        }
        if (gameManager != null) {
            gameManager.shutdown();
        }
    }

    /** Muzyka trybu Raft (losowe utwory + ustawienia per gracz). */
    public pl.vd.raft.music.MusicManager getMusicManager() {
        return musicManager;
    }

    /** Delegate do rodzica — potrzebne dla managerow. */
    public JavaPlugin getParent() {
        return parent;
    }

    public Server getServer() {
        return parent.getServer();
    }

    public java.util.logging.Logger getLogger() {
        return parent.getLogger();
    }

    public FileConfiguration getConfig() {
        return parent.getConfig();
    }

    public void saveDefaultConfig() {
        parent.saveDefaultConfig();
    }

    public java.io.File getDataFolder() {
        return parent.getDataFolder();
    }

    public void saveResource(String resource, boolean replace) {
        parent.saveResource(resource, replace);
    }

    public java.io.InputStream getResource(String resource) {
        return parent.getResource(resource);
    }

    public void reloadConfig() {
        parent.reloadConfig();
    }

    public void saveConfig() {
        parent.saveConfig();
    }

    public static RaftPlugin getInstance() {
        return instance;
    }

    public GameManager getGameManager() {
        return gameManager;
    }

    public IslandManager getIslandManager() {
        return islandManager;
    }

    public WorldGenerator getWorldGenerator() {
        return worldGenerator;
    }

    public StoryManager getStoryManager() {
        return storyManager;
    }

    public DialogueManager getDialogueManager() {
        return dialogueManager;
    }

    public ItemManager getItemManager() {
        return itemManager;
    }

    public pl.vd.raft.world.OceanDebrisManager getOceanDebrisManager() {
        return oceanDebrisManager;
    }

    public RecipeManager getRecipeManager() {
        return recipeManager;
    }

    public PlayerManager getPlayerManager() {
        return playerManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    /**
     * Wczytuje ustawienia tratwy z configu rodzica (config.yml): limit komórek
     * na tratwę (raft-max-cells-per-raft), kolizję ześlizgową z lądem (sekcja
     * raft-collision — odległości próbek + tabela kąt → % prędkości) oraz pole
     * śmieci oceanicznych (sekcja raft-debris) oraz flagi diagnostyki tratwy
     * (raft-debug-diag). Wołane przy starcie pluginu oraz po /def reload,
     * więc zmiany w config.yml działają bez rekompilacji.
     */
    public void reloadRaftConfig() {
        pl.vd.raft.entity.RaftEntity.MAX_CELLS = Math.max(1,
                parent.getConfig().getInt("raft-max-cells-per-raft", 150));
        pl.vd.raft.entity.RaftEntity.loadCollisionConfig(parent.getConfig());
        pl.vd.raft.entity.RaftEntity.loadDiagConfig(parent.getConfig());

        // Podpora piętra (item wooden_support, sekcja raft-support): wizual
        // (materiał / wysokość / custom model z Blockbencha) oraz wysokość
        // słupka — od niej zależy reguła pięter (RaftEntity.isCellSupported).
        org.bukkit.configuration.ConfigurationSection supportSection =
                parent.getConfig().getConfigurationSection("raft-support");
        String supportMaterial = supportSection != null
                ? supportSection.getString("material", "OAK_FENCE") : "OAK_FENCE";
        String supportModelItem = supportSection != null
                ? supportSection.getString("model-item", "OAK_FENCE") : "OAK_FENCE";
        double supportHeight = supportSection != null
                ? supportSection.getDouble("height-blocks", 2.0) : 2.0;
        int supportCmd = supportSection != null
                ? supportSection.getInt("custom-model-data", 0) : 0;
        pl.vd.raft.entity.RaftSupportRenderer.configure(
                org.bukkit.Material.matchMaterial(supportMaterial),
                supportHeight,
                supportCmd,
                org.bukkit.Material.matchMaterial(supportModelItem));
        // Belki podpiętrza (rama pod platformą piętra) — materiał i wyłącznik:
        // beams: false = samo piętro bez belek (czysty podgląd/wydajność).
        String beamMaterial = supportSection != null
                ? supportSection.getString("beam-material", "DARK_OAK_PLANKS") : "DARK_OAK_PLANKS";
        boolean beamsEnabled = supportSection == null || supportSection.getBoolean("beams", true);
        pl.vd.raft.entity.RaftSupportRenderer.configureBeams(
                org.bukkit.Material.matchMaterial(beamMaterial), beamsEnabled);
        // Runięcie piętra bez podparcia (komórki + belki + materiał do
        // odzyskania) jest opóźnione o tyle ticków, ile w configu — gracz widzi
        // najpierw brak słupka, a dopiero potem spadającą platformę (0 = od razu).
        pl.vd.raft.entity.RaftEntity.COLLAPSE_DELAY_TICKS = Math.max(0, supportSection != null
                ? supportSection.getInt("collapse-delay-ticks", 12) : 12);
        // Jedno źródło prawdy: wysokość z renderera, nie z osobnej zmiennej
        pl.vd.raft.entity.RaftEntity.SUPPORT_HEIGHT =
                pl.vd.raft.entity.RaftSupportRenderer.visualHeight();
        if (oceanDebrisManager != null) oceanDebrisManager.loadConfig();
        if (musicManager != null) musicManager.loadConfig();
    }

    public RaftHUDManager getHudManager() {
        return hudManager;
    }

    /** Listener stawiania elementów tratwy (wspólna logika dla przedmiotu i podglądu). */
    public pl.vd.raft.listeners.RaftBuildListener getRaftBuildListener() {
        return raftBuildListener;
    }

    /** Podgląd-duch stawiania (wybór bloku w GUI młota). */
    public pl.vd.raft.listeners.RaftBuildPreview getRaftBuildPreview() {
        return raftBuildPreview;
    }

    public WaterDarknessManager getWaterDarknessManager() {
        return waterDarknessManager;
    }
}
