package pl.vd.raft.entity;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * Pakietowe LOD tratw (Faza 2) — per-gracz kadencja pakietów ruchu encji tratwy.
 *
 * Serwer (RaftEntity.tickFull) synchronizuje encje tratwy CO TICK, gdy ktoś stoi
 * w promieniu FULL_TICK_RANGE. Vanilla rozsyła wtedy teleporty do WSZYSTKICH
 * graczy w zasięgu trackingu — także tych 20–50 bloków dalej, którzy nie potrzebują
 * 20 Hz (globalnie dostają 5 Hz dopiero, gdy nikogo blisko nie ma — tryb FAR).
 *
 * Filtr: gdy tratwa jest w trybie FULL, gracz dalej niż FULL_TICK_RANGE od obrysu
 * pokładu dostaje tylko co FAR_SYNC_INTERVAL-ty pakiet teleportu (5 Hz zamiast
 * 20 Hz). Decyzja zależy od numeru ticka (fullTime świata), dzięki czemu
 * przepuszczamy KOMPLETNE rundy syncu (wszystkie deski/kolizje jednej rundy
 * razem), a nie pojedyncze encje — inaczej klient widziałby „dziurawą\" tratwę.
 * Gracze blisko tratwy dostają wszystko, bez żadnych zmian.
 *
 * Bez ProtocolLib filtr nie działa (softdepend) — zostaje globalne LOD z Fazy 1.
 */
public class RaftPacketLOD extends PacketAdapter {

    public RaftPacketLOD(Plugin plugin) {
        super(plugin, ListenerPriority.NORMAL, PacketType.Play.Server.ENTITY_TELEPORT);
    }

    @Override
    public void onPacketSending(PacketEvent event) {
        if (event.isCancelled()) return;
        Player player = event.getPlayer();
        if (player == null || !player.isOnline()) return;

        // Tylko encje tratwy — teleporty graczy, mobów, stojaków itd. bez zmian.
        // Najpierw tani test typu (encje tratwy to wyłącznie te klasy — deski
        // BlockDisplay, wizual zawartości sieci ItemDisplay/TextDisplay oraz
        // kolizje ArmorStand/Shulker), potem dopiero test tagów (getScoreboardTags
        // kopiuje zbiór — omijamy go dla każdego obcego teleportu w grze).
        int entityId = event.getPacket().getIntegers().read(0);
        Entity entity = ProtocolLibrary.getProtocolManager()
                .getEntityFromID(player.getWorld(), entityId);
        if (!(entity instanceof org.bukkit.entity.ArmorStand
                || entity instanceof org.bukkit.entity.Shulker
                || entity instanceof org.bukkit.entity.BlockDisplay
                || entity instanceof org.bukkit.entity.ItemDisplay
                || entity instanceof org.bukkit.entity.TextDisplay)) {
            return;
        }
        if (!RaftEntity.isRaftEntity(entity)) return;

        UUID raftId = RaftEntity.raftIdOf(entity);
        if (raftId == null) return; // stara encja bez ID tratwy — przepuść

        RaftEntity raft = RaftManager.getInstance().getRaft(raftId);
        if (raft == null) return; // osierocona encja bez żywej tratwy — nie nasz problem

        // FAR (5 Hz) i FROZEN są już przycięte po stronie serwera (Faza 1) — przepuść.
        if (raft.getTickMode() != RaftEntity.TickMode.FULL) return;

        // Blisko tratwy — pełne 20 Hz, bez zmian.
        org.bukkit.Location loc = player.getLocation();
        if (raft.distanceSqToDeckXZ(loc.getX(), loc.getZ())
                <= RaftEntity.FULL_TICK_RANGE * RaftEntity.FULL_TICK_RANGE) {
            return;
        }

        // Daleki obserwator w trybie FULL: cała runda syncu przechodzi tylko co
        // FAR_SYNC_INTERVAL-ty tick (5 Hz). Numer ticka jest wspólny dla wszystkich
        // encji rundy, więc rundy są przepuszczane w całości lub wcale.
        if (player.getWorld().getFullTime() % RaftEntity.FAR_SYNC_INTERVAL != 0) {
            event.setCancelled(true);
        }
    }
}
