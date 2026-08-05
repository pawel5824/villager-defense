package pl.vd;

import org.bukkit.plugin.java.JavaPlugin;
import pl.vd.arena.ArenaManager;
import pl.vd.ability.AbilityManager;
import pl.vd.brush.BrushManager;
import pl.vd.commands.BrushCommand;
import pl.vd.commands.VDCommand;
import pl.vd.game.GameManager;
import pl.vd.kits.KitManager;
import pl.vd.listeners.AbilityListener;
import pl.vd.listeners.BlockListener;
import pl.vd.listeners.BrushListener;
import pl.vd.listeners.EntityListener;
import pl.vd.listeners.PlayerListener;
import pl.vd.player.PlayerDataManager;
import pl.vd.shield.ShieldManager;
import pl.vd.listeners.ShieldListener;
import pl.vd.listeners.ShopListener;
import pl.vd.shop.ShopGUI;

public class VillagerDefensePlugin extends JavaPlugin {

    private ArenaManager arenaManager;
    private PlayerDataManager playerDataManager;
    private KitManager kitManager;
    private ShopGUI shopGUI;
    private GameManager gameManager;
    private BrushManager brushManager;
    private AbilityManager abilityManager;
    private ShieldManager shieldManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.arenaManager = new ArenaManager(this);
        this.playerDataManager = new PlayerDataManager(this);
        this.abilityManager = new AbilityManager(this);
        this.shieldManager = new ShieldManager(this);
        this.kitManager = new KitManager(abilityManager, shieldManager);
        this.shopGUI = new ShopGUI(this);
        this.gameManager = new GameManager(this);
        this.brushManager = new BrushManager(this);

        VDCommand command = new VDCommand(this);
        getCommand("vd").setExecutor(command);
        getCommand("vd").setTabCompleter(command);

        BrushCommand brushCommand = new BrushCommand(this);
        getCommand("brush").setExecutor(brushCommand);
        getCommand("brush").setTabCompleter(brushCommand);

        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new EntityListener(this), this);
        getServer().getPluginManager().registerEvents(new BlockListener(this), this);
        getServer().getPluginManager().registerEvents(new BrushListener(this), this);
        getServer().getPluginManager().registerEvents(new AbilityListener(this), this);
        getServer().getPluginManager().registerEvents(new ShieldListener(this), this);
        getServer().getPluginManager().registerEvents(new ShopListener(this), this);

        getLogger().info("VillagerDefense włączony! Areny wczytane: " + arenaManager.getArenas().size());
    }

    @Override
    public void onDisable() {
        if (playerDataManager != null) playerDataManager.saveAll();
        if (arenaManager != null) arenaManager.save();
        getLogger().info("VillagerDefense wyłączony, dane zapisane.");
    }

    public ArenaManager getArenaManager() { return arenaManager; }
    public PlayerDataManager getPlayerDataManager() { return playerDataManager; }
    public KitManager getKitManager() { return kitManager; }
    public ShopGUI getShopGUI() { return shopGUI; }
    public GameManager getGameManager() { return gameManager; }
    public BrushManager getBrushManager() { return brushManager; }
    public AbilityManager getAbilityManager() { return abilityManager; }
    public ShieldManager getShieldManager() { return shieldManager; }
}
