package pl.vd.raft.net;

import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import pl.vd.raft.entity.RaftEntity;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * Wysyla pakiety z sub-pixel offsetami blokow tratwy do klientow Fabric.
 *
 * Format: "raft:offsets" kanal
 *   int blockCount
 *   per block: int x, int y, int z, double offsetX, double offsetY, double offsetZ
 *
 * Klient (RaftOffsetsPayload + RaftOffsetStorage + BlockOffsetMixin) odbiera
 * i renderuje bloki na sub-pixelowych pozycjach.
 *
 * Paper 26.2: CLIENTBOUND_CUSTOM_PAYLOAD zmienil strukture (Record z jednym polem
 * "payload" typu CustomPacketPayload). Uzywamy refleksji NMS zamiast ProtocolLib.
 */
public class RaftOffsetPacketSender {

    private static RaftOffsetPacketSender instance;
    private boolean enabled = false;

    private static final String NAMESPACE = "raft";
    private static final String PATH = "offsets";

    private RaftOffsetPacketSender() {}

    public static RaftOffsetPacketSender getInstance() {
        if (instance == null) instance = new RaftOffsetPacketSender();
        return instance;
    }

    public void init() {
        // Inicjalizuj NMS refleksje (jednorazowo)
        NMSPacketSender.init();
        enabled = NMSPacketSender.isAvailable();
        if (!enabled) {
            // Fallback: sprobuj ProtocolLib jesli NMS niedostepne
            try {
                Class.forName("com.comphenix.protocol.ProtocolLibrary");
                enabled = true;
            } catch (ClassNotFoundException e) {
                enabled = false;
            }
        }
    }

    public boolean isEnabled() { return enabled; }

    /**
     * Wysyla pakiety offsetow do podanych graczy.
     */
    public void sendOffsets(RaftEntity raft, Collection<Player> recipients) {
        if (!enabled) return;
        if (recipients == null || recipients.isEmpty()) return;

        Vector pos = raft.getState().getPosition();
        List<Vector> offsets = raft.getBlockOffsets();
        if (offsets.isEmpty()) return;

        // Zbuduj bajty pakietu
        byte[] payload;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            out.writeInt(offsets.size());
            for (Vector offset : offsets) {
                int bx = (int) Math.floor(pos.getX() + offset.getX());
                int by = (int) Math.floor(pos.getY() + offset.getY());
                int bz = (int) Math.floor(pos.getZ() + offset.getZ());
                double subX = (pos.getX() + offset.getX()) - bx;
                double subY = (pos.getY() + offset.getY()) - by;
                double subZ = (pos.getZ() + offset.getZ()) - bz;
                out.writeInt(bx);
                out.writeInt(by);
                out.writeInt(bz);
                out.writeDouble(subX);
                out.writeDouble(subY);
                out.writeDouble(subZ);
            }
            payload = baos.toByteArray();
        } catch (IOException e) {
            return;
        }

        // Utworz NMS pakiet (DiscardedPayload = custom channel + byte[] data)
        Object nmsPacket = null;
        if (NMSPacketSender.isAvailable()) {
            Object discPayload = NMSPacketSender.createDiscardedPayload(NAMESPACE, PATH, payload);
            nmsPacket = NMSPacketSender.createCustomPayloadPacket(discPayload);
        }

        // Wyslij
        for (Player player : recipients) {
            if (player.isOnline()
                    && player.getWorld().equals(raft.getState().getWorld())
                    && player.getLocation().distance(raft.getCenter()) < 64) {
                try {
                    if (nmsPacket != null) {
                        NMSPacketSender.send(player, nmsPacket);
                    } else {
                        sendViaProtocolLib(player, payload);
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Fallback: wysyla pakiet przez ProtocolLib (wymaga go na classpath).
     */
    private void sendViaProtocolLib(Player player, byte[] payload) {
        try {
            var protocolManager = com.comphenix.protocol.ProtocolLibrary.getProtocolManager();
            var packet = protocolManager.createPacket(com.comphenix.protocol.PacketType.Play.Server.CUSTOM_PAYLOAD);
            packet.getModifier().writeSafely(0, new org.bukkit.NamespacedKey(NAMESPACE, PATH));
            packet.getByteArrays().writeSafely(0, payload);
            protocolManager.sendServerPacket(player, packet);
        } catch (Exception ignored) {}
    }
}
