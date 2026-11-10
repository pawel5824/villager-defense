package pl.vd.raft.entity;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * RaftMovementController - niesie graczy stojacych/biegajacych na tratwie razem
 * z nia, dzieki czemu moga swobodnie chodzic po pokladzie i robic inne rzeczy
 * (dokladnie tak, jak w oryginalnej grze Raft: tratwa to po prostu podloga,
 * ktora sie przesuwa pod stopami gracza).
 *
 * JAK TO DZIALA (niewidzialny "przenosnik", NIE teleport i NIE pchanie):
 *  - Co tick mierzymy poziome przesuniecie tratwy (delta XZ).
 *  - Na PODLODZE tratwy nadajemy graczowi velocity dokladnie rowne tej delcie
 *    (tylko XZ, Y nietkniete). Z punktu widzenia fizyki MC to jest dokladnie
 *    to samo, co stanie na ruchomej podlodze/przenosniku: zero teleportow,
 *    zero walki z kamera i wlasnym ruchem — gracz po prostu dryfuje z tratwa.
 *
 * SKOKI / SPRINT-SKOKI (v10): w powietrzu NIE nadpisujemy velocity XZ —
 * inaczej zerowalibysmy co tick poziomy rozped gracza ze skoku i sterowanie
 * w locie (skok "przycinal" i spadal w miejscu). Zamiast tego:
 *  - w chwili ODBICIA jednorazowo DODAJEMY delte tratwy do jego predkosci
 *    (bezwladnosc — jak skok z jadacej platformy: gracz zachowuje swoj rozped
 *    + pęd tratwy, wiec ląduje tam, gdzie skakal wzgledem pokladu),
 *  - w POWIETRZU nad pokladem nie ruszamy predkosci XZ w ogole.
 *  Po wyladowaniu przenosnik wraca do zwyklego carry.
 *
 * WCHODZENIE Z WODY (v11): dopoki gracz plywa (stopy ponizej pokladu), vanilla
 * nie niesie go, wiec dryfujaca tratwa uciekala spod niego w trakcie wspinaczki.
 * Dlatego gracz w WODZIE, ktory WZNOSI SIE (trzyma spacje / wyskakuje z wody)
 * i jest przy samej krawedzi tratwy (w zasiegu footprintu + margines), dostaje
 * delte tratwy DODANĄ do predkosci — plynie bokiem razem z tratwa i moze sie na
 * nia wspiac, zamiast gonic ja po wodzie. Plywacy bezczynnie (nie wznosza sie)
 * albo nurkujacy NIE sa ruszani — zmiana dotyczy tylko chwili wspinania.
 */
public final class RaftMovementController {

    private final RaftEntity raft;
    private Vector lastRaftPosition;

    /**
     * Czy gracz w poprzednim ticku stal na pokladzie (do wykrycia chwili odbicia).
     * Tylko gracze, ktorzy sa/ byli na tratwie — wpisy sprzata sie co tick.
     */
    private final Map<UUID, Boolean> groundedState = new HashMap<>();

    /**
     * Maksymalne przesuniecie, jakie przenosnik moze nadac graczowi w jednym
     * ticku (bloki/tick). Zabezpieczenie przed skokiem pozycji tratwy (np. po
     * respawnie/restarcie chunku) — gracz nigdy nie zostanie "wystrzelony".
     */
    private static final double MAX_CARRY_PER_TICK = 0.2;

    /** Prog wznoszenia (velocity Y), powyzej ktorego gracz jest w powietrzu. */
    private static final double RISING_VELOCITY_THRESHOLD = 0.05;

    /** Prog wznoszenia w wodzie, od ktorego liczymy "probe wejscia na tratwe". */
    private static final double SWIM_RISE_THRESHOLD = 0.1;

    /** Maksymalna odleglosc stop nad deckiem, ktora liczymy jako "stanie". */
    private static final double GROUND_SLACK = 0.12;

    /**
     * Jak gleboko (w blokach) pod pokladem moze byc gracz w wodzie, zeby proba
     * wejscia na tratwe liczyla sie jako "wspinaczka przy krawedzi".
     */
    private static final double BOARDING_DEPTH_BELOW_DECK = 1.8;

    public RaftMovementController(RaftEntity raft) {
        if (raft == null) throw new IllegalArgumentException("raft cannot be null");
        this.raft = raft;
    }

    public void tick() {
        Vector currentPos = raft.getState().getPosition();

        if (lastRaftPosition == null) {
            lastRaftPosition = currentPos.clone();
            return;
        }

        double dx = currentPos.getX() - lastRaftPosition.getX();
        double dz = currentPos.getZ() - lastRaftPosition.getZ();
        lastRaftPosition = currentPos.clone();

        // Zabezpieczenie przed naglymi skokami pozycji (nie wplywaj na graczy)
        boolean raftMoving = dx * dx + dz * dz >= 1e-8;
        if (raftMoving) {
            if (Math.abs(dx) > MAX_CARRY_PER_TICK) dx = Math.copySign(MAX_CARRY_PER_TICK, dx);
            if (Math.abs(dz) > MAX_CARRY_PER_TICK) dz = Math.copySign(MAX_CARRY_PER_TICK, dz);
        }

        // Footprint tratwy (poziome granice komorek + margines) i wysokosc decku —
        // liczone RAZ na tick i uzywane przez wszystkie testy ponizej.
        double[] bounds = raftBounds();
        if (bounds == null) return;
        double margin = 0.3;
        double minWorldX = bounds[0] - margin, maxWorldX = bounds[1] + margin;
        double minWorldZ = bounds[2] - margin, maxWorldZ = bounds[3] + margin;
        double deckTop = raft.getState().getPosition().getY() + 0.5;

        List<Player> players = raft.getState().getWorld().getPlayers();
        for (Player player : players) {
            UUID uuid = player.getUniqueId();
            if (player == null || !player.isOnline() || player.isDead()
                    || player.isInsideVehicle() || player.isFlying()) {
                groundedState.remove(uuid);
                continue;
            }

            org.bukkit.Location loc = player.getLocation();
            double feetY = loc.getY();

            if (isWithinFootprint(loc, minWorldX, maxWorldX, minWorldZ, maxWorldZ)
                    && feetY >= deckTop - 0.1 && feetY <= deckTop + 3.2) {
                // (footprint juz zawiera margines 0.3 — gracz na samej krawedzi
                // pokladu tez jest przenoszony, zeby tratwa nie wysunela mu sie spod stop)
                // ── NA / NAD TRATWA ────────────────────────────────────────────
                boolean grounded = feetY <= deckTop + GROUND_SLACK
                        && player.getVelocity().getY() <= RISING_VELOCITY_THRESHOLD;
                boolean wasGrounded = groundedState.getOrDefault(uuid, true);
                groundedState.put(uuid, grounded);

                if (!raftMoving) continue;

                Vector vel = player.getVelocity();
                if (grounded) {
                    // Przenosnik na podlodze: velocity = delta tratwy (tylko XZ).
                    player.setVelocity(new Vector(dx, vel.getY(), dz));
                } else if (wasGrounded) {
                    // Chwila odbicia — dodaj bezwladnosc tratwy DO predkosci gracza
                    // (nie nadpisuj!): gracz zachowuje rozped ze sprint-skoku i
                    // sterowanie w locie, a jednoczesnie niesie pęd tratwy.
                    player.setVelocity(new Vector(vel.getX() + dx, vel.getY(), vel.getZ() + dz));
                }
                // W powietrzu (bez odbicia w tym ticku) NIE ruszamy velocity XZ.
                continue;
            }

            // ── W WODZIE PRZY TRATWIE — WSPINACZKA NA POKLAD ─────────────────
            // Tylko gdy gracz: jest w wodzie, wznosi sie (trzyma spacje / wyskakuje),
            // jest w zasiegu footprintu + marginesu i ma stopy ponizej pokladu.
            groundedState.remove(uuid);
            if (!raftMoving) continue;
            if (!isWithinFootprint(loc, minWorldX, maxWorldX, minWorldZ, maxWorldZ)) continue;
            if (!player.isInWater()) continue;
            if (player.getVelocity().getY() < SWIM_RISE_THRESHOLD) continue;
            if (feetY < deckTop - BOARDING_DEPTH_BELOW_DECK || feetY >= deckTop - 0.05) continue;

            // Plynie bokiem razem z tratwa (delta DODANA, nie nadpisana — wiosla
            // i skok w gore dzialaja normalnie), wiec tratwa nie ucieka spod gracza.
            Vector vel = player.getVelocity();
            player.setVelocity(new Vector(vel.getX() + dx, vel.getY(), vel.getZ() + dz));
        }

        // Sprzatanie: gracze offline albo poza tratwa znikaja ze stanu
        if (groundedState.size() > players.size()) {
            groundedState.entrySet().removeIf(e -> {
                Player p = Bukkit.getPlayer(e.getKey());
                return p == null || !p.isOnline() || !isOnRaft(p);
            });
        }
    }

    /**
     * Poziome granice pokladu (XZ) — zewnetrzne krawedzie komorek tratwy.
     * Zwraca [minX, maxX, minZ, maxZ] lub null, gdy tratwa nie ma komorek.
     */
    private double[] raftBounds() {
        List<Vector> offsets = raft.getBlockOffsets();
        if (offsets.isEmpty()) return null;
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (Vector o : offsets) {
            minX = Math.min(minX, o.getX() - 0.5);
            maxX = Math.max(maxX, o.getX() + 0.5);
            minZ = Math.min(minZ, o.getZ() - 0.5);
            maxZ = Math.max(maxZ, o.getZ() + 0.5);
        }
        Vector raftPos = raft.getState().getPosition();
        return new double[]{
                raftPos.getX() + minX, raftPos.getX() + maxX,
                raftPos.getZ() + minZ, raftPos.getZ() + maxZ
        };
    }

    private boolean isWithinFootprint(org.bukkit.Location loc,
                                      double minX, double maxX, double minZ, double maxZ) {
        return loc.getX() >= minX && loc.getX() <= maxX
                && loc.getZ() >= minZ && loc.getZ() <= maxZ;
    }

    /**
     * Zeruje bazę pomiaru przesunięcia tratwy i stan „na podłodze" graczy.
     * Wołane przez RaftEntity przy wejściu w tryb FULL po FAR/FROZEN (Faza 1
     * LOD), gdy przenośnik przez jakiś czas nie działał — bez tego pierwsze
     * ticki po powrocie gracza nadawałyby mu deltę za cały okres nieobecności
     * (przyciętą do MAX_CARRY_PER_TICK, czyli sztuczne „pchnięcie").
     */
    public void recalibrate() {
        lastRaftPosition = raft.getState().getPosition().clone();
        groundedState.clear();
    }

    /** Czy gracz stoi/skacze na pokladzie tratwy (pelny test, do sprzatania). */
    private boolean isOnRaft(Player player) {
        if (player == null || !player.isOnline()) return false;
        if (player.isDead() || player.isInsideVehicle() || player.isFlying()) return false;
        if (!player.getWorld().equals(raft.getState().getWorld())) return false;

        double[] bounds = raftBounds();
        if (bounds == null) return false;
        double margin = 0.3;
        if (!isWithinFootprint(player.getLocation(),
                bounds[0] - margin, bounds[1] + margin,
                bounds[2] - margin, bounds[3] + margin)) return false;

        double deckTop = raft.getState().getPosition().getY() + 0.5;
        double feetY = player.getLocation().getY();
        return feetY >= deckTop - 0.1 && feetY <= deckTop + 3.2;
    }
}
