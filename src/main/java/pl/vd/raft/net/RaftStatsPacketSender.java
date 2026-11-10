package pl.vd.raft.net;

import org.bukkit.entity.Player;
import pl.vd.raft.core.PlayerData;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Wysyla pakiety ze statystykami gracza Raft do klienta Fabric.
 *
 * Kanal: "raft:stats"
 * Format: float health, float hunger, float thirst, int daysSurvived
 *
 * Klient (RaftStatsPayload + RaftStatsStorage) odbiera i uzywa
 * do rysowania customowych paskow HUD inGameHudMixin.
 *
 * Paper 26.2: CLIENTBOUND_CUSTOM_PAYLOAD zmienil strukture (Record z jednym polem
 * "payload" typu CustomPacketPayload). Uzywamy refleksji NMS zamiast ProtocolLib.
 */
public class RaftStatsPacketSender {

    private static RaftStatsPacketSender instance;
    private boolean enabled = false;

    private static final String NAMESPACE = "raft";
    private static final String PATH = "stats";
    /** Kanal "zakonczenia sesji": pusty sygnal "opuszczasz tryb Raft, wroc do wanilii". */
    private static final String PATH_SESSION_END = "session_end";

    private RaftStatsPacketSender() {}

    public static RaftStatsPacketSender getInstance() {
        if (instance == null) instance = new RaftStatsPacketSender();
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
     * Wysyla pakiet ze statystykami do danego gracza.
     */
    public void sendStats(Player player, PlayerData data) {
        if (!enabled) return;
        if (player == null || !player.isOnline()) return;

        byte[] payload;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            // Zycie: PRAWDZIWE HP gracza, nie zapisany licznik. Pasek ma spadac, gdy gracz
            // naprawde traci zycie — glod (wanilia), moby, utoniecie i odwodnienie.
            // Wczesniej szedl z PlayerData i reagowal wylacznie na brak jedzenia, wiec
            // obrazenia w walce nie byly na nim widoczne.
            out.writeFloat((float) player.getHealth());
            out.writeFloat((float) data.getHunger());
            out.writeFloat(data.getThirst());
            out.writeInt(data.getDaysSurvived());
            payload = baos.toByteArray();
        } catch (IOException e) {
            return;
        }

        // NMS direct path (Paper 26.2+)
        if (NMSPacketSender.isAvailable()) {
            Object discPayload = NMSPacketSender.createDiscardedPayload(NAMESPACE, PATH, payload);
            Object nmsPacket = NMSPacketSender.createCustomPayloadPacket(discPayload);
            if (nmsPacket != null) {
                NMSPacketSender.send(player, nmsPacket);
                return;
            }
        }

        // Fallback: ProtocolLib (dziala na starszych wersjach MC)
        sendViaProtocolLib(player, PATH, payload);
    }

    /**
     * Sygnal "opuszczasz tryb Raft": klient natychmiast gasi paski i odkrywa
     * waniliowy HUD, bez czekania na wygasniecie TTL po ostatnim raft:stats.
     * Wysylany z GameManager.leaveGame, czyli z jedynego punktu wyjscia z trybu.
     * Pusty payload — znaczenie ma sam fakt otrzymania pakietu.
     */
    public void sendSessionEnd(Player player) {
        if (!enabled) return;
        if (player == null || !player.isOnline()) return;

        // NMS direct path (Paper 26.2+)
        if (NMSPacketSender.isAvailable()) {
            Object discPayload = NMSPacketSender.createDiscardedPayload(NAMESPACE, PATH_SESSION_END, new byte[0]);
            Object nmsPacket = NMSPacketSender.createCustomPayloadPacket(discPayload);
            if (nmsPacket != null) {
                NMSPacketSender.send(player, nmsPacket);
                return;
            }
        }

        // Fallback: ProtocolLib (starsze MC)
        sendViaProtocolLib(player, PATH_SESSION_END, new byte[0]);
    }

    /**
     * Fallback: wysyla pakiet przez ProtocolLib (wymaga go na classpath).
     * Ta sciezka dziala na starszych MC (< 26.2) gdzie struktura pakietu
     * miala osobne pola channel i data.
     */
    private void sendViaProtocolLib(Player player, String path, byte[] payload) {
        try {
            var protocolManager = com.comphenix.protocol.ProtocolLibrary.getProtocolManager();
            var packet = protocolManager.createPacket(com.comphenix.protocol.PacketType.Play.Server.CUSTOM_PAYLOAD);
            packet.getModifier().writeSafely(0, new org.bukkit.NamespacedKey(NAMESPACE, path));
            packet.getByteArrays().writeSafely(0, payload);
            protocolManager.sendServerPacket(player, packet);
        } catch (Exception ignored) {}
    }
}
