package pl.vd.raft.crafting;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import pl.vd.raft.RaftPlugin;

import java.lang.reflect.Field;
import java.util.UUID;

/**
 * Odbiera payload "raft:open_crafting" z moda raft-hud — prośbę o otwarcie
 * GUI wytwarzania (gracz nacisnął dedykowany przycisk, domyślnie „Z").
 *
 * Działa TYLKO i wyłącznie w trybie Raft (świat z configu raft-world-name).
 * Poza trybem Raft pakiet jest IGNOROWANY CAŁKOWICIE — zero powiadomień,
 * zero komunikatów (wymaganie: „poza trybem nic nie robi").
 *
 * Mechanizm odbioru: własny ChannelDuplexHandler na kanale gracza (NMS
 * refleksja), identyczny jak w KeyStateReceiver (ProtocolLib nie radzi sobie
 * z record'owymi payloadami w Paper 26.2). Jeśli podpięcie się nie uda,
 * przycisk po prostu nie działa — komenda /wytwarzanie pozostaje.
 */
public final class CraftingKeyReceiver implements Listener {

    private static final String CHANNEL_ID = "raft:open_crafting";
    private static final String PIPELINE_NAME = "raft_open_crafting";
    private static final String PACKET_CLASS =
            "net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket";
    private static final String DISCARDED_PAYLOAD_CLASS =
            "net.minecraft.network.protocol.common.custom.DiscardedPayload";

    private final RaftPlugin plugin;
    private boolean available = false;

    public CraftingKeyReceiver(RaftPlugin plugin) {
        this.plugin = plugin;
        try {
            Class.forName(PACKET_CLASS);
            available = true;
        } catch (ClassNotFoundException e) {
            plugin.getParent().getLogger().warning(
                    "[Raft] CraftingKeyReceiver niedostępny (NMS) — przycisk moda nie działa.");
        }
    }

    /** Podpina handler na kanale gracza (rejestrowany jako Listener). */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        onJoin(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        onQuit(event.getPlayer().getUniqueId());
    }

    /** Podpina handler na kanale gracza (wywołaj przy dołączeniu gracza). */
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
                                new CraftingKeyHandler(player.getUniqueId()));
                    }
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
            // Nie udało się podpiąć — przycisk u tego gracza nie działa (cisza).
        }
    }

    public void onQuit(UUID playerId) {
        // handler znika razem z kanałem gracza; nic do sprzątania
    }

    private void handleRequest(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) return;

        // TYLKO tryb Raft — poza nim całkowita cisza (bez komunikatów).
        String raftWorldName = plugin.getParent().getConfig()
                .getString("raft-world-name", "RAFT");
        if (!player.getWorld().getName().equalsIgnoreCase(raftWorldName)) return;

        CraftingCommand crafting = plugin.getCraftingCommand();
        if (crafting != null) {
            crafting.openForPlayer(player);
        }
    }

    /**
     * Fallback za klawiszem Z (payload raft:open_crafting) dla graczy BEZ moda.
     *
     * Z działa tylko z modem (mod wysyła payload). Bez moda vanilla nie słyszy o Z,
     * więc używamy F — jedyne vaniliowe zdarzenie dla tego klawisza (swapOffhand).
     *
     * - Gracz Z modem  → F = normalny swap (oni mają dedykowany Z)
     * - Gracz BEZ moda → F = otwórz GUI wytwarzania (tylko w świecie Raft)
     * - Poza światem Raft → F = vanilla swap (zero interwencji)
     */
    @EventHandler
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Gracze z modem mają Z — F zostaje vanillowym swapem rąk.
        pl.vd.raft.hook.KeyStateReceiver keyStates = plugin.getHookManager().getKeyStateReceiver();
        if (keyStates != null && keyStates.has(playerId)) return;

        // TYLKO tryb Raft — poza nim całkowita cisza (F działa jak zawsze).
        String raftWorldName = plugin.getParent().getConfig()
                .getString("raft-world-name", "RAFT");
        if (!player.getWorld().getName().equalsIgnoreCase(raftWorldName)) return;

        // PlayerSwapHandItemsEvent fired zawsze, nawet gdy otwarty jest inny GUI
        // (np. skrzynia). Nie zakłóćmy istniejącej interakcji — pozwól vanilla
        // wykonać swap rąk, gdy coś już jest otwarte.
        if (player.getOpenInventory().getTopInventory() != player.getInventory()) return;

        // Anulujemy swap i otwieramy GUI — tylko gdy CraftingCommand jest
        // dostępny. Gdyby był null, pozwalamy vanilla na swap (defensywny fallback).
        CraftingCommand crafting = plugin.getCraftingCommand();
        if (crafting != null) {
            event.setCancelled(true);
            crafting.openForPlayer(player);
        }
    }

    // ─────────────────────── NETTY HANDLER ─────────────────────

    private final class CraftingKeyHandler extends ChannelDuplexHandler {

        private final UUID playerId;

        CraftingKeyHandler(UUID playerId) {
            this.playerId = playerId;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg != null && msg.getClass().getName().equals(PACKET_CLASS)) {
                try {
                    Object payload = msg.getClass().getMethod("payload").invoke(msg);
                    if (payload != null && payload.getClass().getName().equals(DISCARDED_PAYLOAD_CLASS)) {
                        // Identyfikator kanału: oba akcesory (Paper 26.2 = id(),
                        // starsze = channel()) — jak w KeyStateReceiver.
                        Object channelId = invokeAccessor(payload, "id", "channel");
                        if (channelId != null && CHANNEL_ID.equals(String.valueOf(channelId))) {
                            // Netty wątek → główny wątek serwera.
                            Bukkit.getScheduler().runTask(plugin.getParent(),
                                    () -> handleRequest(playerId));
                        }
                    }
                } catch (Exception ignored) {
                }
            }
            super.channelRead(ctx, msg);
        }

        /** Woła pierwszy istniejący akcesor (nazwy zmieniały się między wersjami). */
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
