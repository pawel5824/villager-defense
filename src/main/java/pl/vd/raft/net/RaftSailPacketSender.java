package pl.vd.raft.net;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import pl.vd.raft.entity.RaftEntity;
import pl.vd.raft.entity.RaftManager;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Wysyłka `raft:sails` — pozycja i stan żagla każdej tratwy, żeby mod (Fabric,
 * client-only) rysował maszt z płótnem na tratwie i animował rozwijanie.
 *
 * Wzorzec jest ten sam, co `raft:receivers` (RaftReceiversPacketSender):
 * klient NIE zgaduje, gdzie stoi żagiel — dowiaduje się tego od serwera, a lista
 * jest ZAWSZE PEŁNA (klient podmienia całość, więc zniknięcie żagla = brak wpisu).
 *
 * Kontrakt (strona klienta: RaftSailsPayload + RaftSailStorage + RaftSailWorldRenderer):
 *
 *   int count
 *   count × {
 *     long  raftIdMost, raftIdLeast  // UUID tratwy — klucz wpisu (wygładzanie ruchu)
 *     double x, y, z                 // światowa pozycja PODSTAWY MASZTU (kotwica modelu)
 *     float yaw                      // obrót tratwy w stopniach (0 = jak w Blockbenchu)
 *     int   flags                    // bit 0 = żagiel rozwinięty (napęd z wiatru)
 *   }
 *
 * Wpis ma 48 bajtów. Tratwy bez żagla nie mają wpisu.
 *
 * Dlaczego osobny kanał, a nie kind w `raft:receivers`: tamten kanał opisuje
 * urządzenia stojące w świecie na nośniku-beczce (BlockEntityRenderer pyta
 * o nie TYLKO na pozycji bloku-nośnika), a tratwa pływa — żagiel nie ma
 * i nie może mieć bloku-nośnika (beczka na pokładzie to blok w przestrzeni
 * gracza). Tutaj pozycję rysowania bierze wprost z pakietu renderer świata,
 * więc tratwa może dryfować, a żagiel płynie razem z nią.
 *
 * Kadencja: co 2 ticki (10 Hz) + zawsze cała lista, bo tratwa się rusza.
 * Odbiorcy: gracze w świecie Raft w zasięgu 128 bloków od pokładu tratwy.
 */
public class RaftSailPacketSender {

    private static RaftSailPacketSender instance;
    private boolean enabled = false;

    private static final String NAMESPACE = "raft";
    private static final String PATH = "sails";

    /** Kadencja broadcastu w tickach (2 = 10 Hz — tratwa dryfuje ~1.4 bl/s). */
    private static final long BROADCAST_INTERVAL_TICKS = 2L;

    /** Zasięg, w którym tratwa w ogóle trafia do pakietu (bloki). */
    private static final double SEND_RANGE = 128.0;

    private RaftSailPacketSender() {}

    public static RaftSailPacketSender getInstance() {
        if (instance == null) instance = new RaftSailPacketSender();
        return instance;
    }

    public void init() {
        // Ta sama infrastruktura, co raft:stats / raft:offsets / raft:receivers.
        NMSPacketSender.init();
        enabled = NMSPacketSender.isAvailable();
        if (!enabled) {
            try {
                Class.forName("com.comphenix.protocol.ProtocolLibrary");
                enabled = true;
            } catch (ClassNotFoundException e) {
                enabled = false;
            }
        }
    }

    public boolean isEnabled() { return enabled; }

    /** Startuje pętlę broadcastu (raz, przy starcie pluginu). */
    public void start(org.bukkit.plugin.java.JavaPlugin plugin) {
        if (!enabled) return;
        plugin.getServer().getScheduler().runTaskTimer(plugin,
                () -> broadcastAll(null), BROADCAST_INTERVAL_TICKS, BROADCAST_INTERVAL_TICKS);
    }

    /**
     * Wysyła pełną listę żagli. Adresaci:
     *  - specificRecipient != null → tylko on (gracz wszedł na serwer),
     *  - w przeciwnym razie wszyscy online w świecie Raft.
     */
    public void broadcastAll(Player specificRecipient) {
        if (!enabled) return;
        pl.vd.raft.RaftPlugin raft = pl.vd.raft.RaftPlugin.getInstance();
        if (raft == null) return;

        String worldName = raft.getConfig().getString("raft-world-name", "RAFT");
        List<Object[]> raw = new ArrayList<>(); // {raftId, x, y, z, yaw, flags}
        World raftWorld = Bukkit.getWorld(worldName);
        if (raftWorld == null) return;

        for (RaftEntity entity : RaftManager.getInstance().getRafts()) {
            if (!entity.hasSail()) continue;
            if (entity.getState().getWorld() != raftWorld) continue;

            Vector anchor = entity.getSailAnchorWorld();
            if (anchor == null) continue;

            // Tylko tratwy, które ma kto oglądać (128 bloków od pokładu).
            if (!hasViewer(entity, raftWorld)) continue;

            int flags = entity.isSailOpen() ? 1 : 0;
            raw.add(new Object[]{ entity.getRaftId(), anchor.getX(), anchor.getY(), anchor.getZ(),
                    (float) entity.getYawDegrees(), flags });
        }

        byte[] payload = encode(raw);
        if (payload == null) return;

        if (specificRecipient != null) {
            sendTo(specificRecipient, payload);
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld() != raftWorld) continue;
            sendTo(player, payload);
        }
    }

    /** Czy w świecie jest choć jeden gracz w zasięgu tej tratwy? */
    private boolean hasViewer(RaftEntity entity, World world) {
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(entity.getCenter())
                    <= SEND_RANGE * SEND_RANGE) {
                return true;
            }
        }
        return false;
    }

    private byte[] encode(List<Object[]> raw) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            out.writeInt(raw.size());
            for (Object[] e : raw) {
                UUID id = (UUID) e[0];
                out.writeLong(id.getMostSignificantBits());
                out.writeLong(id.getLeastSignificantBits());
                out.writeDouble((Double) e[1]);
                out.writeDouble((Double) e[2]);
                out.writeDouble((Double) e[3]);
                out.writeFloat((Float) e[4]);
                out.writeInt((Integer) e[5]);
            }
            return baos.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    private void sendTo(Player player, byte[] payload) {
        if (player == null || !player.isOnline()) return;

        if (NMSPacketSender.isAvailable()) {
            Object discPayload = NMSPacketSender.createDiscardedPayload(NAMESPACE, PATH, payload);
            Object nmsPacket = NMSPacketSender.createCustomPayloadPacket(discPayload);
            if (nmsPacket != null) {
                NMSPacketSender.send(player, nmsPacket);
                return;
            }
        }
        sendViaProtocolLib(player, payload);
    }

    private void sendViaProtocolLib(Player player, byte[] payload) {
        try {
            var protocolManager = com.comphenix.protocol.ProtocolLibrary.getProtocolManager();
            var packet = protocolManager.createPacket(com.comphenix.protocol.PacketType.Play.Server.CUSTOM_PAYLOAD);
            packet.getModifier().writeSafely(0, new org.bukkit.NamespacedKey(NAMESPACE, PATH));
            packet.getByteArrays().writeSafely(0, payload);
            protocolManager.sendServerPacket(player, packet);
        } catch (Exception ignored) {
            // Bez ProtocolLib nic nie wysyłamy — mod po prostu nie narysuje żagla.
        }
    }
}
