package pl.vd.raft.physics;

import org.bukkit.util.Vector;
import pl.vd.raft.entity.RaftState;    /**
     * Silnik fizyki tratwy — port z plugin.jar (RaftPhysicsEngine).
     *
     * Odpowiada za:
     * - Akumulator delta-time (fixed-step 0.05s)
     * - Siły: wiatr (żagiel rozwinięty), prąd wodny (żagiel zwinięty), wiosłowanie
 * - Drag liniowy i kątowy
 * - Ograniczenie prędkości max
 * - Rotację (yaw/pitch/roll)
 */
public final class RaftPhysicsEngine {

    private static final double STEP_SECONDS = 0.05;

    private final RaftState raft;

    // Skala siły wiatru: RaftManager podaje globalWind jako WEKTOR PRĘDKOŚCI
    // docelowej (kierunek * bloki/sekundę) — tak samo jak prąd, więc skala = 1.0.
    // Silnik płynnie rozpędza tratwę do prędkości wiatru (drag + akumulator).
    private double windForceScale = 1.0;
    // Skala siły prądu: RaftManager podaje globalCurrent jako WEKTOR PRĘDKOŚCI
    // docelowej (kierunek * bloki/sekundę), wiec skala = 1.0 — silnik plynnie
    // przyspiesza tratwe do predkosci pradu (drag + akumulator).
    private double currentForceScale = 1.0;
    private double sailEfficiency = 1.0;
    private double accelerationResponse = 1.0;
    private double angularResponse = 0.08;
    private double rowingScale = 1.0;

    private double accumulator;
    private long lastTick = Long.MIN_VALUE;
    private long localTick;

    // Globalny wiatr i prąd (ustawiane z RaftManager)
    private Vector globalWind = new Vector(0, 0, 0);
    private Vector globalCurrent = new Vector(0, 0, 0);

    /** Jednorazowe nadpisanie napędu na najbliższy krok fizyki (kolizja ześlizgowa). */
    private Vector driveOverride;

    public RaftPhysicsEngine(RaftState raft) {
        if (raft == null) throw new IllegalArgumentException("raft cannot be null");
        this.raft = raft;
    }

    // ─────────────────────── TICK ───────────────────────────────

    public void tick() {
        tick(++localTick);
    }

    public void tick(long tick) {
        if (lastTick == tick) return;

        if (lastTick != Long.MIN_VALUE) {
            double dt = Math.min(0.25, Math.max(0, (tick - lastTick) * 0.05));
            accumulator += dt;
        } else {
            accumulator += 0.05;
        }
        lastTick = tick;

        while (accumulator >= STEP_SECONDS) {
            step(STEP_SECONDS);
            accumulator -= STEP_SECONDS;
        }
    }

    public void step() {
        step(STEP_SECONDS);
    }

    // ─────────────────────── STEP (główna pętla fizyki) ──────────

    private void step(double dt) {
        // 1. Zbierz siły zewnętrzne. Źródło napędu zależy od żagla:
        //    żagiel rozwinięty → WIATR (globalWind), zwinięty/brak → PRĄD WODY
        //    (globalCurrent). Nadpisanie z kolizji ześlizgowej ma pierwszeństwo
        //    i działa tylko przez jeden krok fizyki.
        Vector drive = driveOverride != null ? driveOverride : computeDrive();
        driveOverride = null;
        Vector force = new Vector(0, 0, 0);
        force.add(drive);

        // 2. Zbierz siły z activeForces (wiosłowanie, itp.)
        for (var entry : raft.getActiveForces().entrySet()) {
            Vector f = entry.getValue();
            String key = entry.getKey().toLowerCase();
            double scale = key.contains("row") ? rowingScale : 1.0;
            force.add(f.multiply(scale));
        }

        // 3. Ustaw przyspieszenie
        raft.setAcceleration(force);

        // 4. Zaktualizuj prędkość liniową
        Vector vel = raft.getLinearVelocity();
        vel.add(force.multiply(accelerationResponse * dt));

        // Drag: drag^(dt*20)
        double dragFactor = Math.pow(
                Math.min(1, Math.max(0, raft.getLinearDrag())),
                dt * 20
        );
        vel.multiply(dragFactor);

        // Ogranicz max prędkość
        double maxSpeed = raft.getMaximumSpeed();
        if (vel.lengthSquared() > maxSpeed * maxSpeed && maxSpeed >= 0) {
            vel.normalize().multiply(maxSpeed);
        }

        raft.setLinearVelocity(vel);

        // 5. Zaktualizuj pozycję
        raft.setPosition(raft.getPosition().add(vel.clone().multiply(dt)));

        // 6. Zaktualizuj rotację (angular velocity)
        Vector angVel = raft.getAngularVelocity();
        Vector angularForce = raft.getActiveForces().get("angular");
        if (angularForce != null) {
            angVel.add(angularForce.multiply(angularResponse * dt));
        }

        // Angular drag
        double angularDragFactor = Math.pow(
                Math.min(1, Math.max(0, raft.getAngularDrag())),
                dt * 20
        );
        angVel.multiply(angularDragFactor);

        // Ogranicz max angular speed
        double maxAngSpeed = raft.getMaximumAngularSpeed();
        if (angVel.lengthSquared() > maxAngSpeed * maxAngSpeed && maxAngSpeed >= 0) {
            angVel.normalize().multiply(maxAngSpeed);
        }

        raft.setAngularVelocity(angVel);

        // 7. Zaktualizuj orientację (yaw, pitch, roll)
        raft.setOrientation(
                raft.getYaw() + angVel.getY() * dt,
                raft.getPitch() + angVel.getX() * dt,
                raft.getRoll() + angVel.getZ() * dt
        );
    }

    // ─────────────────────── NAPĘD (wiatr / prąd) ──────────────

    /**
     * Napęd tratwy na ten tick — już przeskalowany wektor siły:
     *  - żagiel rozwinięty (RaftState.isSailing) → wiatr: kierunek i moc
     *    bierzemy z globalWind (RaftManager podaje go jako prędkość docelową,
     *    bloki/sekundę), więc tratwa płynie z wiatrem,
     *  - żagiel zwinięty albo brak żagla → prąd wody (globalCurrent),
     *    czyli dotychczasowe zachowanie dryfu.
     */
    private Vector computeDrive() {
        if (raft.isSailing()) {
            return globalWind.clone().multiply(windForceScale * sailEfficiency);
        }
        return globalCurrent.clone().multiply(currentForceScale);
    }

    /**
     * Wektor napędu tratwy (kierunek i prędkość dryfu): wiatr przy rozwiniętym
     * żaglu, prąd wody bez niego. Używany przez RaftEntity do wykrywania kolizji
     * z lądem (próbki przed tratwą) i do kierunku dryfu (pole śmieci, komendy).
     * Długość wektora = prędkość docelowa w blokach/sekundę.
     */
    public Vector getDriveVector() { return computeDrive(); }

    /**
     * Nadpisuje napęd na najbliższy krok fizyki (kolizja ześlizgowa zadaje
     * prąd/wiatr wyłącznie wzdłuż brzegu). Zerowane po wykorzystaniu w step(),
     * więc nie zostaje na kolejne ticki — RaftManager co tick odświeża i tak
     * wiatr oraz prąd.
     */
    public void setDriveOverride(Vector drive) {
        this.driveOverride = drive != null ? drive.clone() : null;
    }

    // ─────────────────────── GETTERS / SETTERS ─────────────────

    public Vector getGlobalWind() { return globalWind.clone(); }
    public void setGlobalWind(Vector wind) { this.globalWind = wind != null ? wind.clone() : new Vector(0, 0, 0); }

    public Vector getGlobalCurrent() { return globalCurrent.clone(); }
    public void setGlobalCurrent(Vector current) { this.globalCurrent = current != null ? current.clone() : new Vector(0, 0, 0); }

    public double getWindForceScale() { return windForceScale; }
    public void setWindForceScale(double v) { this.windForceScale = Math.max(0, v); }

    public double getCurrentForceScale() { return currentForceScale; }
    public void setCurrentForceScale(double v) { this.currentForceScale = Math.max(0, v); }

    public double getSailEfficiency() { return sailEfficiency; }
    public void setSailEfficiency(double v) { this.sailEfficiency = Math.max(0, v); }

    public double getAccelerationResponse() { return accelerationResponse; }
    public void setAccelerationResponse(double v) { this.accelerationResponse = Math.max(0, v); }

    public double getAngularResponse() { return angularResponse; }
    public void setAngularResponse(double v) { this.angularResponse = Math.max(0, v); }

    public double getRowingScale() { return rowingScale; }
    public void setRowingScale(double v) { this.rowingScale = Math.max(0, v); }
}
