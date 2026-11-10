package pl.vd.raft.entity;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Stan fizyczny tratwy — port z plugin.jar (RaftState).
 *
 * Przechowuje: pozycję, velocity, kąty (yaw/pitch/roll), drag, komponenty.
 * Oferuje transformacje local ↔ world.
 */
public final class RaftState {

    // Nazwy komponentów
    public static final String PLANK = "plank";
    public static final String DECK = "deck";
    public static final String MAST = "mast";
    public static final String SAIL = "sail";

    private final World world;
    private final Map<String, Component> components = new LinkedHashMap<>();
    private final Map<String, Vector> activeForces = new LinkedHashMap<>();
    private final Set<UUID> trackedPlayers = new LinkedHashSet<>();
    private final Set<UUID> physicsEntities = new LinkedHashSet<>();
    private final Set<UUID> visualEntities = new LinkedHashSet<>();

    private final Vector position;
    private final Vector linearVelocity = new Vector(0, 0, 0);
    private final Vector angularVelocity = new Vector(0, 0, 0);
    private final Vector acceleration = new Vector(0, 0, 0);

    private double linearDrag = 0.94;
    private double angularDrag = 0.9;
    private double maximumSpeed = 1.5;
    private double maximumAngularSpeed = 0.12;
    private double yaw;
    private double pitch;
    private double roll;

    /** Czy żagiel jest ROZWINIĘTY (otwarty). Ma znaczenie tylko razem z hasSail(). */
    private boolean sailOpen;

    // ─────────────────────── KONSTRUKTOR ───────────────────────

    public RaftState(World world, Location location) {
        if (world == null || location == null || location.getWorld() != world) {
            throw new IllegalArgumentException("A raft needs an origin in its world");
        }
        this.world = world;
        this.position = new Vector(location.getX(), location.getY(), location.getZ());
        this.yaw = Math.toRadians(location.getYaw());
        this.pitch = Math.toRadians(location.getPitch());
    }

    // ─────────────────────── GETTERS / SETTERS ─────────────────

    public World getWorld() { return world; }

    public Vector getPosition() { return position.clone(); }

    public Vector getOrigin() { return position.clone(); }

    public void setPosition(Vector pos) {
        if (pos == null || !Double.isFinite(pos.getX()) || !Double.isFinite(pos.getY()) || !Double.isFinite(pos.getZ())) {
            throw new IllegalArgumentException("position must contain finite coordinates");
        }
        position.copy(pos);
    }

    public Vector getLinearVelocity() { return linearVelocity.clone(); }
    public void setLinearVelocity(Vector v) { linearVelocity.copy(v); }

    public Vector getAngularVelocity() { return angularVelocity.clone(); }
    public void setAngularVelocity(Vector v) { angularVelocity.copy(v); }

    public Vector getAcceleration() { return acceleration.clone(); }
    public void setAcceleration(Vector a) { acceleration.copy(a); }

    public double getLinearDrag() { return linearDrag; }
    public void setLinearDrag(double v) { this.linearDrag = Math.min(1, Math.max(0, v)); }

    public double getAngularDrag() { return angularDrag; }
    public void setAngularDrag(double v) { this.angularDrag = Math.min(1, Math.max(0, v)); }

    public double getMaximumSpeed() { return maximumSpeed; }
    public void setMaximumSpeed(double v) { this.maximumSpeed = Math.max(0, v); }

    public double getMaximumAngularSpeed() { return maximumAngularSpeed; }
    public void setMaximumAngularSpeed(double v) { this.maximumAngularSpeed = Math.max(0, v); }

    public double getYaw() { return yaw; }
    public double getPitch() { return pitch; }
    public double getRoll() { return roll; }

    public void setOrientation(double yaw, double pitch, double roll) {
        this.yaw = yaw;
        this.pitch = pitch;
        this.roll = roll;
    }

    // ─────────────────────── KOMPONENTY ───────────────────────

    public void addComponent(String type, Component comp) {
        components.put(type, comp);
    }

    public Component getComponent(String type) {
        return components.get(type);
    }

    /** Usuwa komponent (np. zdjęty maszt/żagiel) — zwraca usunięty albo null. */
    public Component removeComponent(String type) {
        return components.remove(type);
    }

    public boolean hasSail() {
        return components.containsKey(SAIL);
    }

    public boolean hasMast() {
        return components.containsKey(MAST);
    }

    /** Czy żagiel jest rozwinięty (otwarty) — stan przełączany przy żaglu/maszcie. */
    public boolean isSailOpen() { return sailOpen; }

    public void setSailOpen(boolean open) { this.sailOpen = open; }

    /**
     * Czy tratwa płynie pod żaglem: MA żagiel (komponent SAIL) ORAZ jest on
     * rozwinięty. Wtedy silnik fizyki bierze napęd z WIATRU (globalWind),
     * a nie z prądu wody — patrz RaftPhysicsEngine.computeDrive().
     */
    public boolean isSailing() { return hasSail() && sailOpen; }

    public Map<String, Component> getComponents() {
        return Collections.unmodifiableMap(components);
    }

    // ─────────────────────── AKTYWNE SIŁY ─────────────────────

    public Map<String, Vector> getActiveForces() {
        return activeForces;
    }

    public void applyForce(String name, Vector force) {
        activeForces.put(name, force);
    }

    public void removeForce(String name) {
        activeForces.remove(name);
    }

    public void clearForces() {
        activeForces.clear();
    }

    // ─────────────────────── TRACKING GRACZY ──────────────────

    public void trackPlayer(org.bukkit.entity.Player player) {
        trackedPlayers.add(player.getUniqueId());
    }

    public void untrackPlayer(UUID uuid) {
        trackedPlayers.remove(uuid);
    }

    public Set<UUID> getTrackedPlayers() {
        return Collections.unmodifiableSet(trackedPlayers);
    }

    // ─────────────────────── TRACKING ENTITIES ────────────────

    public void trackPhysicsEntity(UUID uuid) {
        physicsEntities.add(uuid);
    }

    public void trackVisualEntity(UUID uuid) {
        visualEntities.add(uuid);
    }

    public void clearEntityTracking() {
        physicsEntities.clear();
        visualEntities.clear();
    }

    // ─────────────────────── TRANSFORMACJE LOCAL ↔ WORLD ──────

    /**
     * Konwertuje wektor z lokalnego systemu raftu do światowego.
     * Lokalny: X = do przodu, Y = góra, Z = w bok.
     */
    public Vector localToWorld(Vector local) {
        double cosYaw = Math.cos(yaw);
        double sinYaw = Math.sin(yaw);
        double cosPitch = Math.cos(pitch);
        double sinPitch = Math.sin(pitch);
        double cosRoll = Math.cos(roll);
        double sinRoll = Math.sin(roll);

        // Macierz rotacji (yaw → pitch → roll)
        double x = local.getX();
        double y = local.getY();
        double z = local.getZ();

        // Yaw (obrót w poziomie — wokół osi Y)
        double x1 = x * cosYaw + z * sinYaw;
        double y1 = y;
        double z1 = -x * sinYaw + z * cosYaw;

        // Pitch (przechylenie wzdłuż osi Z)
        double x2 = x1;
        double y2 = y1 * cosPitch - z1 * sinPitch;
        double z2 = y1 * sinPitch + z1 * cosPitch;

        // Roll (przechylenie wzdłuż osi X)
        double x3 = x2 * cosRoll + y2 * sinRoll;
        double y3 = -x2 * sinRoll + y2 * cosRoll;
        double z3 = z2;

        return new Vector(
                position.getX() + x3,
                position.getY() + y3,
                position.getZ() + z3
        );
    }

    /**
     * Konwertuje wektor ze światowego do lokalnego systemu raftu.
     */
    public Vector worldToLocal(Vector worldVec) {
        Vector rel = worldVec.clone().subtract(position);
        double cosYaw = Math.cos(-yaw);
        double sinYaw = Math.sin(-yaw);
        double cosPitch = Math.cos(-pitch);
        double sinPitch = Math.sin(-pitch);
        double cosRoll = Math.cos(-roll);
        double sinRoll = Math.sin(-roll);

        double x = rel.getX();
        double y = rel.getY();
        double z = rel.getZ();

        // Roll
        double x1 = x * cosRoll + y * sinRoll;
        double y1 = -x * sinRoll + y * cosRoll;
        double z1 = z;

        // Pitch
        double x2 = x1;
        double y2 = y1 * cosPitch - z1 * sinPitch;
        double z2 = y1 * sinPitch + z1 * cosPitch;

        // Yaw
        double x3 = x2 * cosYaw + z2 * sinYaw;
        double y3 = y2;
        double z3 = -x2 * sinYaw + z2 * cosYaw;

        return new Vector(x3, y3, z3);
    }

    /**
     * Sprawdza czy dany wektor (w world coords) jest w obszarze decku raftu.
     */
    public boolean isDeckOrSupportArea(Vector worldVec) {
        Vector local = worldToLocal(worldVec);
        return Math.abs(local.getX()) <= 2.0 && Math.abs(local.getZ()) <= 2.0
                && local.getY() >= -1.0 && local.getY() <= 2.0;
    }

    // ─────────────────────── INNER CLASS: COMPONENT ───────────

    public static final class Component {
        private final String type;
        private Vector localOffset;

        public Component(String type, Vector localOffset) {
            this.type = type;
            this.localOffset = localOffset != null ? localOffset.clone() : new Vector(0, 0, 0);
        }

        public String getType() { return type; }
        public Vector getLocalOffset() { return localOffset.clone(); }
        public void setLocalOffset(Vector offset) { this.localOffset = offset.clone(); }

        public Vector getWorldPosition(RaftState state) {
            return state.localToWorld(localOffset);
        }
    }
}
