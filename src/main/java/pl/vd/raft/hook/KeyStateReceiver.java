package pl.vd.raft.hook;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import pl.vd.raft.RaftPlugin;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Odbiera stan przycisków (PPM, Shift) z klienta Fabric (mod raft-hud).
 *
 * Mod wysyła serwerbound payload "raft:key_state" (1 bajt: bit0 = trzymany
 * prawy przycisk, bit1 = trzymany Shift, bit2 = trzymany LEWY Alt) ZAWSZE,
 * gdy stan się zmieni. Dzięki temu serwer wie, że gracz trzyma przycisk, BEZ
 * waniliowego stanu "używania" lornetki — można więc wyłączyć zoom/dźwięk/
 * animację lunety.
 *
 * Bit Altu jest jedyną drogą do Alt w grze: zdarzenia Bukkita nie mają
 * żadnego klawisza Alt/Ctrl (sprawdzone javap na paper-api 26.3), więc
 * "trzymany lewy Alt" widzi wyłącznie gracz z modem raft-hud. Bez moda obrót
 * budowli działa kółkiem w trybie budowy i Shiftem (patrz RaftBuildListener).
 *
 * Implementacja: własny ChannelDuplexHandler na kanale gracza (NMS reflection),
 * bo ProtocolLib nie radzi sobie z record'owymi payloadami w Paper 26.2.
 * Jeśli NMS/netty się zmienią — handler po prostu się nie podepnie, a gracze
 * wracają do awaryjnego wykrywania przez isHandRaised() (zoom lunety).
 */
public final class KeyStateReceiver {

    private static final String CHANNEL_ID = "raft:key_state";
    private static final String PIPELINE_NAME = "raft_key_state";
    private static final String PACKET_CLASS = "net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket";
    private static final String DISCARDED_PAYLOAD_CLASS = "net.minecraft.network.protocol.common.custom.DiscardedPayload";

    private final RaftPlugin plugin;
    private final Map<UUID, KeyState> states = new ConcurrentHashMap<>();
    private boolean available = false;

    /** Stan przycisków gracza (aktualizowany z payloadu moda). */
    public static final class KeyState {
        public boolean use;   // trzymany prawy przycisk myszy
        public boolean shift; // trzymany Shift
        public boolean alt;   // trzymany LEWY Alt (obrót budowli)

        /**
         * Rozkłada bajt flag z moda na stany przycisków (bit0 PPM, bit1 Shift,
         * bit2 lewy Alt). Czysta funkcja — testowalna bez serwera, i jedyne
         * miejsce, w którym znamy układ bitów (kontrakt z RaftKeyStatePayload).
         */
        public static KeyState of(int flags) {
            KeyState state = new KeyState();
            state.use = (flags & 1) != 0;
            state.shift = (flags & 2) != 0;
            state.alt = (flags & 4) != 0;
            return state;
        }
    }

    public KeyStateReceiver(RaftPlugin plugin) {
        this.plugin = plugin;
        try {
            Class.forName(PACKET_CLASS);
            available = true;
        } catch (ClassNotFoundException e) {
            plugin.getParent().getLogger().warning(
                    "[Raft] KeyStateReceiver niedostępny (NMS) — gracze bez moda: zoom lunety.");
        }
    }

    /** Podpina handler na kanale gracza (wywołaj przy dołączeniu). */
    public void onJoin(Player player) {
        if (!available) return;
        try {
            Object handle = getHandle(player);
            Object connection = readField(handle, "connection");          // ServerGamePacketListenerImpl
            Object networkManager = readField(connection, "connection");  // Connection
            Channel channel = (Channel) readField(networkManager, "channel");
            channel.eventLoop().submit(() -> {
                try {
                    ChannelPipeline pipeline = channel.pipeline();
                    if (pipeline.get(PIPELINE_NAME) == null) {
                        pipeline.addBefore("packet_handler", PIPELINE_NAME,
                                new KeyStateHandler(player.getUniqueId()));
                    }
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
            // Nie udało się podpiąć — gracz działa w trybie awaryjnym (zoom).
        }
    }

    public void onQuit(UUID playerId) {
        states.remove(playerId);
    }

    /** Stan przycisków gracza z modem; null = gracz bez moda (tryb awaryjny). */
    public KeyState get(UUID playerId) {
        return states.get(playerId);
    }

    /** Czy gracz ma aktywnego moda raft-hud (przesyła stan przycisków). */
    public boolean has(UUID playerId) {
        return states.containsKey(playerId);
    }

    /** Nowy stan przycisków z moda (bajt flag → stan, patrz KeyState.of). */
    private void handleState(UUID playerId, int flags) {
        states.put(playerId, KeyState.of(flags));
    }

    // ─────────────────────── NETTY HANDLER ─────────────────────

    private final class KeyStateHandler extends ChannelDuplexHandler {

        private final UUID playerId;

        KeyStateHandler(UUID playerId) {
            this.playerId = playerId;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg != null && msg.getClass().getName().equals(PACKET_CLASS)) {
                try {
                    Object payload = msg.getClass().getMethod("payload").invoke(msg);
                    if (payload != null && payload.getClass().getName().equals(DISCARDED_PAYLOAD_CLASS)) {
                        // Identyfikator kanału: Paper 26.2 ma akcesor id() (rekord
                        // DiscardedPayload(Identifier id, byte[] data)); starsze
                        // wersje miały channel() — próbujemy obu, bo literówka
                        // tutaj PO CICHU gubiła payload moda (serwer widział
                        // gracza jako "bez moda" i tap PPM zwijał hak od razu).
                        Object channelId = invokeAccessor(payload, "id", "channel");
                        if (channelId != null && CHANNEL_ID.equals(String.valueOf(channelId))) {
                            byte[] data = (byte[]) payload.getClass().getMethod("data").invoke(payload);
                            if (data != null && data.length >= 1) {
                                final int flags = data[0];
                                // Netty wątek → główny wątek serwera.
                                Bukkit.getScheduler().runTask(plugin.getParent(), () ->
                                        handleState(playerId, flags));
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            }
            super.channelRead(ctx, msg);
        }

        /** Woła pierwszy istniejący akcesor (rekord NMS zmieniał nazwy między wersjami). */
        private Object invokeAccessor(Object target, String... methodNames) throws Exception {
            NoSuchMethodException last = null;
            for (String name : methodNames) {
                try {
                    return target.getClass().getMethod(name).invoke(target);
                } catch (NoSuchMethodException e) {
                    last = e;
                }
            }
            throw last;
        }
    }

    // ─────────────────────── REFLEKSJA NMS ─────────────────────

    private static Object getHandle(Player player) throws Exception {
        Class<?> craftPlayer = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer");
        return craftPlayer.getMethod("getHandle").invoke(player);
    }

    /** Czyta pole (publiczne, a jak nie — prywatne z setAccessible). */
    private static Object readField(Object obj, String name) throws Exception {
        Class<?> type = obj.getClass();
        try {
            Field f = type.getField(name);
            return f.get(obj);
        } catch (NoSuchFieldException e) {
            Field f = type.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(obj);
        }
    }
}