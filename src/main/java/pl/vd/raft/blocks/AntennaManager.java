package pl.vd.raft.blocks;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Faza 2 projektu anten (docs/ANTENY_ODBIORNIK.md): manager anten — stawianie
 * (walidacja zasięgu i poziomu), link do odbiorników, walidacja co 2 s
 * (zerwanie = natychmiast, decyzja właściciela), persistencja przez DevicePersistence.
 *
 * Decyzje właściciela zapisane w docs (nie zmieniać „przy okazji"):
 *  - zasięg: 8 bloków w poziomie (XZ) od odbiornika,
 *  - poziom: ΔY == 0 — antena tylko na tym samym poziomie co odbiornik
 *    (gdy kiedyś powstanie piętro tratwy, anten nie da się wystawić wyżej),
 *  - zerwanie: natychmiast — odbiornik wraca do „2/3", wyspy znikają od razu,
 *  - zero komunikatów (AGENTS.md pkt 1).
 */
public class AntennaManager {

    /** Zasięg stawiania anteny od odbiornika, w blokach (poziom XZ). */
    public static final double MAX_RANGE_XZ = 8.0;

    /** Walidacja linków co 2 s (40 ticków). */
    private static final long VALIDATE_INTERVAL_TICKS = 40L;

    private final pl.vd.raft.RaftPlugin plugin;
    private final List<AntennaDevice> antennas = new ArrayList<>();

    public AntennaManager(pl.vd.raft.RaftPlugin plugin) {
        this.plugin = plugin;
    }

    // ─────────────────────── STAWIANIE ───────────────────────

    /**
     * Próba postawienia anteny na klikniętym bloku. Zwraca wynik z powodem
     * (powód NIE jest pokazywany graczowi — AGENTS.md pkt 1; tu tylko diagnostyka).
     */
    public PlaceResult place(Location target, java.util.UUID owner) {
        PlaceResult result = new PlaceResult();

        World world = target.getWorld();
        if (world == null) {
            result.reason = "brak świata";
            return result;
        }

        // Na czym stoi antena: kliknięty blok musi być pokładem tratwy (komórka FLOOR).
        pl.vd.raft.entity.RaftEntity raft = findRaftCellAt(target);
        if (raft == null) {
            result.reason = "antena stoi tylko na pokładzie tratwy";
            return result;
        }
        // ΔY anteny = Y komórki pokładu; antena stoi NA komórce.
        Location base = target.getBlock().getLocation().add(0, 1, 0);

        // Duplikat na tej pozycji?
        if (isOccupied(base)) {
            result.reason = "pozycja zajęta przez inną antenę";
            return result;
        }

        // Najbliższy odbiornik z wolnym slotem, spełniający zasięg i poziom.
        DeviceBlock2x2 receiver = findLinkableReceiver(base);
        if (receiver == null) {
            result.reason = "brak odbiornika z wolnym slotem w zasięgu 8 (ten sam poziom)";
            return result;
        }

        AntennaDevice antenna = new AntennaDevice(base, owner);
        antenna.spawn();
        antenna.setLinkedReceiver(receiver);
        antennas.add(antenna);

        result.ok = true;
        result.antenna = antenna;
        return result;
    }

    /** Wynik stawiania: ok + obiekt albo reason (diagnostyka, nie dla gracza). */
    public static class PlaceResult {
        public boolean ok;
        public AntennaDevice antenna;
        public String reason;
    }

    /** Najbliższy odbiornik z wolnym slotem spełniający zasięg (8 XZ) i poziom (ΔY=0). */
    private DeviceBlock2x2 findLinkableReceiver(Location base) {
        DeviceRegistry registry = plugin.getDeviceRegistry();
        if (registry == null) return null;

        DeviceBlock2x2 nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (DeviceBlock2x2 device : registry.getActiveDevices()) {
            // Tylko odbiorniki (po kluczu rejestru — displayName ma kody kolorów).
            if (device.getDeviceType() != registry.getType("modul_odbiorczy")) continue;
            if (!device.hasFreeAntennaSlot()) continue;

            Location receiverBase = device.getBaseLocation();
            World receiverWorld = receiverBase.getWorld();
            if (receiverWorld == null || receiverWorld != base.getWorld()) continue;

            // Poziom: ΔY == 0 (baza anteny = pokład + 1, więc porównujemy Y bazy).
            if (receiverBase.getBlockY() != base.getBlockY()) continue;

            // Zasięg: odległość w XZ między środkami bloków.
            double dx = (receiverBase.getBlockX() + 0.5) - (base.getBlockX() + 0.5);
            double dz = (receiverBase.getBlockZ() + 0.5) - (base.getBlockZ() + 0.5);
            double distXZ = Math.sqrt(dx * dx + dz * dz);
            if (distXZ > MAX_RANGE_XZ) continue;

            if (distXZ < nearestDist) {
                nearestDist = distXZ;
                nearest = device;
            }
        }
        return nearest;
    }

    // ─────────────────────── WALIDACJA / USUWANIE ───────────────────────

    /** Startuje pętlę walidacji (raz, przy starcie pluginu). */
    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin.getParent(), this::validate,
                VALIDATE_INTERVAL_TICKS, VALIDATE_INTERVAL_TICKS);
    }

    /**
     * Co 2 s: antena traci link, gdy odbiornik zniknął albo wyszedł z warunków
     * (zasięg/poziom — np. przebudowa tratwy). Antena próbuje się przepiąć
     * na najbliższy wolny odbiornik. Wszystko po cichu (AGENTS.md pkt 1).
     */
    private void validate() {
        Iterator<AntennaDevice> it = antennas.iterator();
        while (it.hasNext()) {
            AntennaDevice antenna = it.next();
            Location base = antenna.getBaseLocation();
            World world = base.getWorld();
            if (world == null) continue; // świat niezaładowany — pomiń

            DeviceBlock2x2 link = antenna.getLinkedReceiver();
            if (link != null && !isLinkValid(antenna, link)) {
                antenna.setLinkedReceiver(null); // natychmiastowe zerwanie (decyzja właściciela)
            }
            if (antenna.getLinkedReceiver() == null) {
                DeviceBlock2x2 newLink = findLinkableReceiver(base);
                if (newLink != null) {
                    antenna.setLinkedReceiver(newLink);
                }
            }
        }
    }

    /** Czy link antena→odbiornik nadal spełnia zasięg i poziom? */
    private boolean isLinkValid(AntennaDevice antenna, DeviceBlock2x2 receiver) {
        DeviceRegistry registry = plugin.getDeviceRegistry();
        if (registry == null || receiver.getDeviceType() != registry.getType("modul_odbiorczy")) {
            return false; // odbiornik usunięty z rejestru
        }
        Location base = antenna.getBaseLocation();
        Location receiverBase = receiver.getBaseLocation();
        World world = base.getWorld();
        if (world == null || receiverBase.getWorld() != world) return false;
        if (receiverBase.getBlockY() != base.getBlockY()) return false;

        double dx = (receiverBase.getBlockX() + 0.5) - (base.getBlockX() + 0.5);
        double dz = (receiverBase.getBlockZ() + 0.5) - (base.getBlockZ() + 0.5);
        return Math.sqrt(dx * dx + dz * dz) <= MAX_RANGE_XZ;
    }

    /** Usuwa jedną antenę (np. przy rozbieraniu). */
    public void removeAntenna(AntennaDevice antenna) {
        if (antenna == null) return;
        antenna.remove();
        antennas.remove(antenna);
    }

    /** Usuwa wszystkie anteny (czyszczenie rejestru przy wyłączaniu nie usuwa pliku devices.yml). */
    public void clearAll() {
        for (AntennaDevice antenna : new ArrayList<>(antennas)) {
            antenna.remove();
        }
        antennas.clear();
    }

    public List<AntennaDevice> getAntennas() {
        return new ArrayList<>(antennas);
    }

    // ─────────────────────── POMOCNICZE ───────────────────────

    /**
     * Zwraca tratwę, gdy kliknięty blok JEST komórką tratwy (raycast przy putiu:
     * porównanie bloku z pozycją komórki — wzorzec z RaftAxeListener). Null = nie tratwa.
     */
    private pl.vd.raft.entity.RaftEntity findRaftCellAt(Location clicked) {
        pl.vd.raft.entity.RaftManager manager = pl.vd.raft.entity.RaftManager.getInstance();
        for (pl.vd.raft.entity.RaftEntity raft : manager.getRafts()) {
            if (raft.getCenter().getWorld() != clicked.getWorld()) continue;
            org.bukkit.util.Vector pos = raft.getCenter().toVector();
            for (org.bukkit.util.Vector off : raft.getBlockOffsets()) {
                // Komórka zajmuje blok na pozycji center+offset (y w górę od poziomu wody tratwy).
                int bx = (int) Math.floor(pos.getX() + off.getX());
                int by = (int) Math.floor(pos.getY() + off.getY());
                int bz = (int) Math.floor(pos.getZ() + off.getZ());
                if (bx == clicked.getBlockX() && by == clicked.getBlockY() && bz == clicked.getBlockZ()) {
                    return raft;
                }
            }
        }
        return null;
    }

    private boolean isOccupied(Location base) {
        for (AntennaDevice antenna : antennas) {
            Location other = antenna.getBaseLocation();
            if (other.getWorld() == base.getWorld()
                    && other.getBlockX() == base.getBlockX()
                    && other.getBlockY() == base.getBlockY()
                    && other.getBlockZ() == base.getBlockZ()) return true;
        }
        return false;
    }

    /** Dodaje istniejącą antenę (persistencja) — bez place()/walidacji zasięgu. */
    public void registerExisting(AntennaDevice antenna) {
        if (!antennas.contains(antenna)) antennas.add(antenna);
    }
}
