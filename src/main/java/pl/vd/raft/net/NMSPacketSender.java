package pl.vd.raft.net;

import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Wysylanie customowych pakietow NMS bezposrednio (refleksja).
 *
 * Paper 26.2: ClientboundCustomPayloadPacket to Record z jednym polem
 * "payload" typu CustomPacketPayload. ProtocolLib 5.4.0 nie radzi sobie
 * z ta zmiana, wiec uzywamy refleksji do NMS.
 *
 * Jedyne zaleznosci: paper-api (compile-time). NMS klasy sa dostepne
 * tylko w runtime na serwerze.
 */
public final class NMSPacketSender {

    private static final Logger logger = Logger.getLogger("NMSPacketSender");

    // Cached reflection objects
    private static Method getHandleMethod;
    private static Method sendMethod;
    private static java.lang.reflect.Field connectionField;
    private static boolean initialized = false;
    private static boolean available = false;

    private NMSPacketSender() {}

    /**
     * Inicjalizuje refleksje (wywolaj raz po starcie serwera).
     */
    public static void init() {
        if (initialized) return;
        initialized = true;
        try {
            // org.bukkit.craftbukkit.entity.CraftPlayer#getHandle()
            Class<?> craftPlayerClass = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer");
            getHandleMethod = craftPlayerClass.getMethod("getHandle");

            // net.minecraft.server.level.ServerPlayer#connection (public field)
            // (zwraca ServerGamePacketListenerImpl)
            Class<?> serverPlayerClass = Class.forName("net.minecraft.server.level.ServerPlayer");
            connectionField = serverPlayerClass.getField("connection");

            // ServerCommonPacketListenerImpl#send(Packet)
            Class<?> connectionClass = Class.forName("net.minecraft.server.network.ServerCommonPacketListenerImpl");
            sendMethod = connectionClass.getMethod("send", Class.forName("net.minecraft.network.protocol.Packet"));

            available = true;
            logger.info("[NMSPacketSender] NMS packet sending OK (reflection)");
        } catch (Exception e) {
            available = false;
            logger.log(Level.WARNING, "[NMSPacketSender] NMS not available, custom packets disabled", e);
        }
    }

    /**
     * Wysyla NMS pakiet do gracza.
     * @param player gracz Bukkit
     * @param nmsPacket obiekt NMS implementujacy Packet<?>
     */
    public static void send(Player player, Object nmsPacket) {
        if (!available || player == null || !player.isOnline()) return;
        try {
            Object craftPlayer = player.getClass().cast(player); // safety
            Object handle = getHandleMethod.invoke(player);
            Object connection = connectionField.get(handle);
            sendMethod.invoke(connection, nmsPacket);
        } catch (Exception e) {
            // Ignore - gracefully degrade
        }
    }

    /**
     * Tworzy DiscardedPayload (NMS) z kanalem i danymi.
     * @param namespace np. "raft"
     * @param path np. "stats"
     * @param data bajty pakietu
     * @return obiekt DiscardedPayload lub null jesli NMS niedostepne
     */
    public static Object createDiscardedPayload(String namespace, String path, byte[] data) {
        if (!available) return null;
        try {
            Class<?> identifierClass = Class.forName("net.minecraft.resources.Identifier");
            Class<?> discardedPayloadClass = Class.forName("net.minecraft.network.protocol.common.custom.DiscardedPayload");

            // Identifier.fromNamespaceAndPath(String, String)
            Method fromNsAndPath = identifierClass.getMethod("fromNamespaceAndPath", String.class, String.class);
            Object identifier = fromNsAndPath.invoke(null, namespace, path);

            // new DiscardedPayload(Identifier, byte[])
            var constructor = discardedPayloadClass.getConstructor(identifierClass, byte[].class);
            return constructor.newInstance(identifier, data);
        } catch (Exception e) {
            logger.log(Level.WARNING, "[NMSPacketSender] Failed to create DiscardedPayload", e);
            return null;
        }
    }

    /**
     * Tworzy ClientboundCustomPayloadPacket (NMS) z payload.
     * @param discardedPayload obiekt DiscardedPayload
     * @return obiekt ClientboundCustomPayloadPacket lub null
     */
    public static Object createCustomPayloadPacket(Object discardedPayload) {
        if (!available || discardedPayload == null) return null;
        try {
            Class<?> packetClass = Class.forName("net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket");
            Class<?> payloadClass = Class.forName("net.minecraft.network.protocol.common.custom.CustomPacketPayload");
            var constructor = packetClass.getConstructor(payloadClass);
            return constructor.newInstance(discardedPayload);
        } catch (Exception e) {
            logger.log(Level.WARNING, "[NMSPacketSender] Failed to create CustomPayloadPacket", e);
            return null;
        }
    }

    public static boolean isAvailable() {
        return available;
    }
}
