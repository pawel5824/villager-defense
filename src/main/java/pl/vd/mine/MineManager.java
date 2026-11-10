package pl.vd.mine;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.SulfurCube;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import pl.vd.VillagerDefensePlugin;
import pl.vd.game.MobSpawner;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MineManager {

    /** Promień, w którym mina wykrywa moba i detonuje. */
    private static final double TRIGGER_RADIUS = 1.5;
    private static final float EXPLOSION_POWER = 3.0f;
    /** Wpuszczenie kostki miny w blok podłogi (żeby TNT wystawało tylko odrobinę). */
    private static final double MINE_SINK = 0.15;
    /** Wysokość wizualnej kostki TNT względem pozycji kostki: przy 0.15 TNT (0.3 bloka)
     *  jest dokładnie w połowie schowane w podłodze — połowa wystaje, połowa w bloku. */
    private static final double TNT_EMBED_Y = 0.15;

    private final VillagerDefensePlugin plugin;
    private final Map<UUID, SulfurCube> activeMines = new ConcurrentHashMap<>();
    // wizualne kostki TNT „w środku” min (BlockDisplay jako pasażer kostki siarki)
    private final Map<UUID, BlockDisplay> mineVisuals = new ConcurrentHashMap<>();

    public MineManager(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
        startWatcher();
    }

    /** Czy ten BlockDisplay to wizualne TNT miny (pomijane przez /stojak sprzataj). */
    public boolean isMineVisual(BlockDisplay display) {
        return mineVisuals.containsValue(display);
    }

    // ---------- Item miny (kupowany w sklepie) - jajko spawnujące kostkę siarki ----------

    public ItemStack createMineItem() {
        ItemStack item = new ItemStack(Material.SULFUR_CUBE_SPAWN_EGG, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("Mina lądowa");
        meta.setLore(List.of(
                "§7Kliknij PPM na ziemi, aby postawić.",
                "§7Aktywuje się od razu i wybuchnie,",
                "§7gdy mob podejdzie blisko."
        ));
        item.setItemMeta(meta);
        return item;
    }

    // ---------- Aktywacja (wywoływane po naturalnym zaspawnowaniu jajkiem) ----------

    /** Zamienia świeżo zaspawnowaną (jajkiem) kostkę siarki w aktywną minę. */
    public void activateAsMine(SulfurCube cube) {
        // Kostka detonuje RĘCZNIE w watcherze (detonate), gdy mob podejdzie blisko.
        // Nie wywołujemy ignite(), żeby nie wybuchła sama po fuzie, zanim mob podejdzie.
        // setAI(false) wyłącza waniliowe AI kostki siarki (to wrogi mob, który sam się
        // odpala przy graczu jak creeper) - inaczej mina mogłaby wybuchnąć od razu.
        // Kostka ma być „posągiem”: AI wyłączone (nie chodzi, nie odpala się sama),
        // a setAware(false) dodatkowo wyłącza waniliową aktywność moba.
        cube.setAI(false);
        cube.setAware(false);
        cube.setMetadata("vd-mine", new FixedMetadataValue(plugin, true));
        cube.setInvulnerable(true);
        cube.setCustomName("Mina lądowa");
        cube.setCustomNameVisible(false);
        // niewidzialna kostka (bez cząsteczek) + bez kolizji: widać tylko TNT wystające
        // z podłogi, a gracze nie „zawieszają się” na niewidzialnym bloku
        cube.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));
        cube.setCollidable(false);
        // mniejsza kostka (skala 0.5, jak mały zombiak), żeby mina nie była wielkim blokiem
        Attribute scaleAttr = Bukkit.getRegistry(Attribute.class).get(NamespacedKey.minecraft("scale"));
        if (scaleAttr != null && cube.getAttribute(scaleAttr) != null) {
            cube.getAttribute(scaleAttr).setBaseValue(0.5);
        }

        // wpuść kostkę delikatnie w blok podłogi — mina siedzi „w” bloku, a z podłogi
        // wystaje tylko wierzch wizualnego TNT
        cube.teleport(cube.getLocation().add(0, -MINE_SINK, 0));

        // Wizualna kostka TNT W ŚRODKU miny: BlockDisplay wpuszczony w kostkę (bez pasażera,
        // żeby nie siedział na wierzchu). Pozycja jest synchronizowana z kostką w watcherze,
        // więc TNT podąża za nią, gdziekolwiek stoi.
        BlockDisplay tnt = cube.getWorld().spawn(cube.getLocation().add(0, TNT_EMBED_Y, 0), BlockDisplay.class, d -> {
            d.setBlock(Bukkit.createBlockData(Material.TNT));
            d.setTransformation(new Transformation(
                    new Vector3f(0f, 0f, 0f),
                    new Quaternionf(),
                    new Vector3f(0.3f, 0.3f, 0.3f),
                    new Quaternionf()));
            d.setBrightness(new Display.Brightness(15, 15)); // zawsze jasna jak prawdziwe TNT
        });
        mineVisuals.put(cube.getUniqueId(), tnt);

        activeMines.put(cube.getUniqueId(), cube);
    }

    // ---------- Detekcja i detonacja ----------

    private void startWatcher() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 5L, 5L);
    }

    private void tick() {
        if (activeMines.isEmpty()) return;

        for (SulfurCube mine : List.copyOf(activeMines.values())) {
            if (!mine.isValid() || mine.isDead()) {
                activeMines.remove(mine.getUniqueId());
                removeVisual(mine.getUniqueId());
                continue;
            }

            // synchronizuj wizualne TNT z kostką (gdyby kostka się przesunęła)
            BlockDisplay visual = mineVisuals.get(mine.getUniqueId());
            if (visual != null && visual.isValid()) {
                visual.teleport(mine.getLocation().add(0, TNT_EMBED_Y, 0));
            }

            for (org.bukkit.entity.Entity nearby : mine.getNearbyEntities(TRIGGER_RADIUS, TRIGGER_RADIUS, TRIGGER_RADIUS)) {
                if (nearby instanceof LivingEntity living && living.hasMetadata(MobSpawner.METADATA_KEY)) {
                    detonate(mine);
                    break;
                }
            }
        }
    }

    /**
     * Sprząta po grze: usuwa WSZYSTKIE aktywne miny w danym świecie (razem z
     * wizualnym TNT) - wywoływane po zakończeniu areny.
     */
    public void cleanupArena(org.bukkit.World world) {
        if (world == null) return;
        for (SulfurCube mine : List.copyOf(activeMines.values())) {
            if (mine.isValid() && !mine.isDead() && mine.getWorld().equals(world)) {
                activeMines.remove(mine.getUniqueId());
                removeVisual(mine.getUniqueId());
                mine.remove();
            }
        }
    }

    private void detonate(SulfurCube mine) {
        Location loc = mine.getLocation();
        activeMines.remove(mine.getUniqueId());
        removeVisual(mine.getUniqueId());
        mine.remove();
        loc.getWorld().createExplosion(loc, EXPLOSION_POWER, false, false);
    }

    private void removeVisual(UUID id) {
        BlockDisplay display = mineVisuals.remove(id);
        if (display != null && display.isValid()) display.remove();
    }
}
