package pl.vd.raft.music;

import org.bukkit.entity.Player;
import pl.vd.raft.net.NMSPacketSender;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Wysyłka `raft:musicvol` — serwerowa głośność muzyki Raft per gracz.
 *
 * Mod kliencki (com.raft.hud) dokłada ją w SoundEngine.calculateVolume dla
 * utworów raft:music.* i przelicza grające kanały — dzięki temu zmiana suwaka
 * z GUI /muzyka słychać NATYCHMIAST (płynnie, bez restartu utworu), czego
 * waniliowe playSound nie umie (głośność zapieczętowana na starcie pakietu).
 *
 * Payload: float 0.0–1.0 (4 bajty). Wzorzec wysyłki: RaftStatsPacketSender
 * (NMS DiscardedPayload + fallback ProtocolLib). Gracz bez moda ignoruje
 * kanał (DiscardedPayload), plugin niczego nie musi wiedzieć o modzie.
 *
 * Kiedy wysyłamy: zmiana głośności (GUI/komenda), wejście do świata Raft
 * (aktualny stan ustawienia), /def reload nie zmienia per-gracza (pomijamy).
 */
public class RaftMusicVolumeSender {

    private static final String NAMESPACE = "raft";
    private static final String PATH = "musicvol";

    private RaftMusicVolumeSender() {}

    /** Wysyła głośność (0.0–1.0) do gracza z modem; bez moda pakiet ginie cicho. */
    public static void send(Player player, float volume) {
        if (player == null || !player.isOnline()) return;
        float v = Math.max(0f, Math.min(1f, volume));

        byte[] payload;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            out.writeFloat(v);
            payload = baos.toByteArray();
        } catch (IOException e) {
            return;
        }

        if (NMSPacketSender.isAvailable()) {
            Object discPayload = NMSPacketSender.createDiscardedPayload(NAMESPACE, PATH, payload);
            Object nmsPacket = NMSPacketSender.createCustomPayloadPacket(discPayload);
            if (nmsPacket != null) {
                NMSPacketSender.send(player, nmsPacket);
                return;
            }
        }

        // Fallback: ProtocolLib (starsze MC) — ten sam wzorzec co raft:stats.
        try {
            var protocolManager = com.comphenix.protocol.ProtocolLibrary.getProtocolManager();
            var packet = protocolManager.createPacket(
                    com.comphenix.protocol.PacketType.Play.Server.CUSTOM_PAYLOAD);
            packet.getModifier().writeSafely(0, new org.bukkit.NamespacedKey(NAMESPACE, PATH));
            packet.getByteArrays().writeSafely(0, payload);
            protocolManager.sendServerPacket(player, packet);
        } catch (Exception ignored) {
            // Bez moda i bez ProtocolLib — cisza (zasada ciszy: żadnych logów).
        }
    }
}
