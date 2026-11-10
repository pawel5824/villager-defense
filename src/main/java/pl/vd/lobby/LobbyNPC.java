package pl.vd.lobby;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.EulerAngle;
import pl.vd.VillagerDefensePlugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.*;

/**
 * NPC w lobby — ArmorStand wyglądający jak gracz stojący nieruchomo.
 * Ma lekką animację rąk (cyklicznechange RightArmPose).
 *
 * Pola:
 *   uniqueId   — ID NPC (losowe UUID)
 *   name       — nazwaNPC (wyświetlana nad głową)
 *   location   — pozycja w lobby
 *   skinTexture — base64 tekstury skóra gracza
 *   gameMode   — tryb gry przypisany do tego NPC (null =brak)
 */
public class LobbyNPC {

    private final UUID uniqueId;
    private final String name;
    private final Location location;
    private final String skinTexture;
    private String gameMode; // "villagerdefense", "raft", itp.

    private ArmorStand stand;
    private BukkitTask animationTask;

    // lekka animacja rąk — prawadło obraca się co 20 ticków
    private static final EulerAngle ARM_UP = new EulerAngle(Math.toRadians(-30), 0, 0);
    private static final EulerAngle ARM_DOWN = new EulerAngle(Math.toRadians(0), 0, 0);
    private static final int ANIMATION_PERIOD_TICKS = 40; // 2 sekundy cykl

    public LobbyNPC(String name, Location location, String skinTexture, String gameMode) {
        this.uniqueId = UUID.randomUUID();
        this.name = name;
        this.location = location.clone();
        this.skinTexture = skinTexture;
        this.gameMode = gameMode;
    }

    /**
     * Spawnuje NPC w świecie (ArmorStand z custom skórą).
     */
    public void spawn(VillagerDefensePlugin plugin) {
        if (stand != null && !stand.isDead()) return;

        stand = location.getWorld().spawn(location, ArmorStand.class);
        stand.setVisible(true);
        stand.setSmall(false);
        stand.setArms(true);
        stand.setBasePlate(false);
        stand.setGravity(false);
        stand.setInvulnerable(true);
        stand.setCollidable(false);
        stand.setSilent(true);
        stand.setCanPickupItems(false);
        stand.setCustomName("§e§l" + name);
        stand.setCustomNameVisible(true);

        // skóra gracza na głowie
        if (skinTexture != null && !skinTexture.isEmpty()) {
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                try {
                    Object profile = createGameProfile(skinTexture);
                    if (profile != null) {
                        Field profileField = meta.getClass().getDeclaredField("profile");
                        profileField.setAccessible(true);
                        profileField.set(meta, profile);
                    }
                } catch (Exception ignored) {}
                head.setItemMeta(meta);
            }
            stand.getEquipment().setHelmet(head);
        }

        stand.addScoreboardTag("vd_lobby_npc");
        startAnimation(plugin);
    }

    /**
     * Usuwa NPC ze świata.
     */
    public void despawn() {
        stopAnimation();
        if (stand != null && !stand.isDead()) {
            stand.remove();
        }
        stand = null;
    }

    /**
     * Starts subtle hand animation (right arm raises/lowers).
     */
    private void startAnimation(VillagerDefensePlugin plugin) {
        if (animationTask != null) animationTask.cancel();
        animationTask = new BukkitRunnable() {
            boolean up = false;
            @Override
            public void run() {
                if (stand == null || stand.isDead()) { cancel(); return; }
                up = !up;
                stand.setRightArmPose(up ? ARM_UP : ARM_DOWN);
            }
        }.runTaskTimer(plugin, ANIMATION_PERIOD_TICKS, ANIMATION_PERIOD_TICKS);
    }

    private void stopAnimation() {
        if (animationTask != null) { animationTask.cancel(); animationTask = null; }
    }

    // --- Getters ---

    public UUID getUniqueId() { return uniqueId; }
    public String getName() { return name; }
    public Location getLocation() { return location.clone(); }
    public String getSkinTexture() { return skinTexture; }
    public String getGameMode() { return gameMode; }
    public void setGameMode(String gameMode) { this.gameMode = gameMode; }
    public ArmorStand getStand() { return stand; }
    public boolean isSpawned() { return stand != null && !stand.isDead(); }

    // --- Persistence ---

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", name);
        map.put("world", location.getWorld().getName());
        map.put("x", location.getX());
        map.put("y", location.getY());
        map.put("z", location.getZ());
        map.put("yaw", (double) location.getYaw());
        map.put("pitch", (double) location.getPitch());
        map.put("skin", skinTexture != null ? skinTexture : "");
        map.put("gamemode", gameMode != null ? gameMode : "");
        return map;
    }

    public static LobbyNPC fromMap(Map<String, Object> map, VillagerDefensePlugin plugin) {
        String name = (String) map.getOrDefault("name", "NPC");
        String worldName = (String) map.getOrDefault("world", "world");
        org.bukkit.World world = plugin.getServer().getWorld(worldName);
        if (world == null) return null;
        double x = toDouble(map.get("x"));
        double y = toDouble(map.get("y"));
        double z = toDouble(map.get("z"));
        float yaw = (float) toDouble(map.get("yaw"));
        float pitch = (float) toDouble(map.get("pitch"));
        Location loc = new Location(world, x, y, z, yaw, pitch);
        String skin = (String) map.getOrDefault("skin", "");
        String gm = (String) map.getOrDefault("gamemode", "");
        return new LobbyNPC(name, loc, skin.isEmpty() ? null : skin, gm.isEmpty() ? null : gm);
    }

    private static double toDouble(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        if (o instanceof String s) { try { return Double.parseDouble(s); } catch (Exception e) {} }
        return 0;
    }

    // --- reflection: GameProfile ---

    private Object createGameProfile(String base64Texture) throws Exception {
        Class<?> gpClass = Class.forName("com.mojang.authlib.GameProfile");
        Constructor<?> gpCtor = gpClass.getConstructor(UUID.class, String.class);
        Object profile = gpCtor.newInstance(UUID.randomUUID(), name);

        Class<?> propClass = Class.forName("com.mojang.authlib.properties.Property");
        Constructor<?> propCtor = propClass.getConstructor(String.class, String.class, String.class);
        Object property = propCtor.newInstance("textures", base64Texture, "");

        Field propsField = gpClass.getDeclaredField("properties");
        propsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) propsField.get(profile);
        props.put("textures", property);
        return profile;
    }
}
