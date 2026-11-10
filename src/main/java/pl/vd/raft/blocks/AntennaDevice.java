package pl.vd.raft.blocks;

import org.bukkit.Location;

import java.util.UUID;

/**
 * Faza 2 projektu anten (docs/ANTENY_ODBIORNIK.md): antena — urządzenie jednoblokowe,
 * podłączane automatycznie do najbliższego odbiornika z wolnym slotem (max 3 na odbiornik).
 *
 * Celowo PROSTSZA struktura niż DeviceBlock2x2:
 *  - brak GUI, brak własnej baterii (zasilanie bierze z odbiornika — decyzja właściciela),
 *  - **tylko blok-nośnik** (beczka, ta sama konwencja co odbiornik): od fazy 4 wygląd
 *    anteny (słupek) i kable rysuje mod z modelu `assets/raft/rafthud/antena.json`,
 *    więc serwer nie trzyma tu żadnej encji display (wcześniej tymczasowy BlockDisplay
 *    z LIGHTNING_ROD — usunięty, bo dublowałby model moda),
 *  - link utrzymuje AntennaManager (walidacja co 2 s; zerwanie = natychmiast, decyzja właściciela).
 *
 * Zasięg i poziom waliduje AntennaManager przy STAWIANIU (zasięg 8 bloków XZ,
 * ΔY == 0 — „na tym samym poziomie, co odbiornik", decyzja właściciela).
 */
public class AntennaDevice {

    private final Location baseLocation;
    private final UUID ownerUuid;

    /** Odbiornik, do którego jesteśmy podłączeni (null = niepodłączona). */
    private DeviceBlock2x2 linkedReceiver;

    public AntennaDevice(Location baseLocation, UUID ownerUuid) {
        this.baseLocation = baseLocation;
        this.ownerUuid = ownerUuid;
    }

    /**
     * Spawnuje antenę: tylko nośnik. Wygląd (słupek) rysuje mod z modelu w packu
     * — tak samo, jak duży odbiornik (docs/ODBIORNIK_RENDER.md).
     */
    public void spawn() {
        if (baseLocation.getWorld() == null) return;
        // Nośnik — identyczny jak przy odbiorniku (niewidzialna beczka facing=down, open=true).
        pl.vd.raft.net.RaftReceiversPacketSender.placeCarrierBlock(baseLocation);
    }

    /** Usuwa antenę ze świata (nośnik + zerwanie linku). */
    public void remove() {
        pl.vd.raft.net.RaftReceiversPacketSender.removeCarrierBlock(baseLocation);
        setLinkedReceiver(null);
    }

    // ─────────────────────── LINK ───────────────────────

    public DeviceBlock2x2 getLinkedReceiver() { return linkedReceiver; }

    /** Przepina link (null = odłączenie). Odbiornik dostaje/nie dostaje slot. */
    public void setLinkedReceiver(DeviceBlock2x2 receiver) {
        if (this.linkedReceiver == receiver) return;
        if (this.linkedReceiver != null) {
            this.linkedReceiver.removeAntenna(this);
        }
        this.linkedReceiver = receiver;
        if (receiver != null) {
            receiver.addAntenna(this);
        }
    }

    // ─────────────────────── GETTERS ───────────────────────

    public Location getBaseLocation() { return baseLocation; }
    public UUID getOwnerUuid() { return ownerUuid; }

    /** Antena działa, gdy ma zasilony odbiornik (własnej baterii nie ma — decyzja właściciela). */
    public boolean isPowered() {
        return linkedReceiver != null && linkedReceiver.hasBattery() && linkedReceiver.getEnergy() > 0;
    }
}
