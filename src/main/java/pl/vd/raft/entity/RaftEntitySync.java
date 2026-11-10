package pl.vd.raft.entity;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

/**
 * RaftEntitySync - przesuwanie entity tratwy (BlockDisplay / Shulker).
 *
 * WAŻNE: uzywamy zwyklego teleportu Bukkit (entity.teleport), a NIE surowego
 * NMS Entity#setPos. Teleport poprawnie aktualizuje rejestracje entity w
 * sekcjach chunku — bez tego kolizje gracza (liczone przez wyszukiwanie entity
 * w sekcjach) NIE znajduja shulkerow, ktore odplynely od miejsca spawnu:
 * gracz przechodzil przez deski mimo zywych kolizji.
 *
 * Kroki ruchu sa male (~0.03-0.06 bloka/tick), wiec teleport co tick wyglada
 * plynnie (dokladnie tak robi BlockShips: per-tick teleporty kolizji).
 */
public final class RaftEntitySync {

    private RaftEntitySync() {}

    /**
     * Przesuwa entity do podanej pozycji (poprawny teleport z aktualizacja sekcji).
     * Pusta operacja gdy odleglosc jest pomijalna.
     */
    public static boolean moveSmoothly(Entity entity, double targetX, double targetY, double targetZ) {
        Location loc = entity.getLocation();
        if (loc.getWorld() == null) return false;

        double dx = targetX - loc.getX();
        double dy = targetY - loc.getY();
        double dz = targetZ - loc.getZ();

        if (Math.abs(dx) < 1e-4 && Math.abs(dy) < 1e-4 && Math.abs(dz) < 1e-4) {
            return true; // brak ruchu — nie teleportuj bez potrzeby
        }

        return entity.teleport(new Location(entity.getWorld(), targetX, targetY, targetZ));
    }
}
