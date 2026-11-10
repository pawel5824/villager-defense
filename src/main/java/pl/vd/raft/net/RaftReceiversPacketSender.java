package pl.vd.raft.net;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Barrel;
import org.bukkit.entity.Player;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Faza 1 projektu anten (docs/ANTENY_ODBIORNIK.md) — wysyłka `raft:receivers`:
 * lista urządzeń stojących w świecie, którą mod (Fabric, client-only) rysuje
 * jako duże modele na nośnikach-beczkach.
 *
 * Kontrakt (docs/ODBIORNIK_RENDER.md §5, strona klienta: RaftReceiversPayload
 * + RaftReceiverStorage):
 *
 *   int count
 *   count × {
 *     int   x, y, z   // pozycja bloku-nośnika (beczka facing=down, open=true)
 *     float yaw       // obrót modelu w stopniach (0 = jak w Blockbenchu)
 *     int   kind      // 0 = odbiornik, 1 = antena
 *     int   flags     // bit 0 = włączony (odbiornik: bateria z energią; antena: zasilanie z odbiornika)
 *     int   linkX, linkY, linkZ   // tylko antena: pozycja nośnika odbiornika, inaczej 0,0,0
 *   }
 *
 * UWAGA: wpis ma teraz 28 bajtów (było 20 — bez linku). Klient liczy rozmiar wpisu,
 * więc mod i plugin muszą iść w parze (oba są w tej samej paczce, patrz modpack/).
 *
 * Klient podmienia CAŁĄ listę przy każdym pakiecie, więc wysyłamy rzadko
 * i zawsze wszystko: co 1 s (pętla, buforuje join gracza — mod czyści listę
 * przy JOIN, więc bez tego urządzenia byłyby niewidoczne do pierwszej zmiany),
 * a także od razu po postawieniu/usunięciu urządzenia (/urzadzenie).
 *
 * Nośnik: beczka z paperem `facing=down, open=true`. Ten jeden stan jest
 * w packu nadpisany na `minecraft:block/air` (niewidzialny), wszystkie inne
 * stany wyglądają jak wanilia. Beczka ma BlockEntity, a wanilia nie ma dla niej
 * BER-a — dlatego właśnie ją wybrano jako nośnik (docs/ODBIORNIK_RENDER.md §2).
 * Bez fizycznego bloku nośnika klient w ogóle nie pyta renderera o pozycję.
 */
public class RaftReceiversPacketSender {

    private static RaftReceiversPacketSender instance;
    private boolean enabled = false;

    private static final String NAMESPACE = "raft";
    private static final String PATH = "receivers";

    /** Kadencja broadcastu w tickach (20 = 1 s) — jak pętla statystyk w GameManager. */
    private static final long BROADCAST_INTERVAL_TICKS = 20L;

    private RaftReceiversPacketSender() {}

    public static RaftReceiversPacketSender getInstance() {
        if (instance == null) instance = new RaftReceiversPacketSender();
        return instance;
    }

    public void init() {
        // Ta sama infrastruktura, co raft:stats / raft:offsets (NMS Paper 26.2,
        // fallback ProtocolLib na starszych wersjach).
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

    // ─────────────────────── NOŚNIK (beczka) ───────────────────────

    /**
     * Stawia blok-nośnik (niewidzialna beczka) na pozycji urządzenia.
     * Idempotentne: istniejący nośnik zostaje bez zmian.
     */
    public static void placeCarrierBlock(Location base) {
        World world = base.getWorld();
        if (world == null) return;
        Block block = base.getBlock();
        if (block.getType() == org.bukkit.Material.BARREL) return;
        block.setBlockData(createCarrierData(), false);
    }

    /** Usuwa blok-nośnik, jeśli wciąż jest naszą beczką (facing=down, open=true). */
    public static void removeCarrierBlock(Location base) {
        World world = base.getWorld();
        if (world == null) return;
        Block block = base.getBlock();
        if (!(block.getBlockData() instanceof Barrel barrel)) return;
        if (barrel.getFacing() != org.bukkit.block.BlockFace.DOWN || !barrel.isOpen()) return;
        block.setType(org.bukkit.Material.AIR, false);
    }

    private static Barrel createCarrierData() {
        Barrel barrel = (Barrel) org.bukkit.Material.BARREL.createBlockData();
        barrel.setFacing(org.bukkit.block.BlockFace.DOWN);
        barrel.setOpen(true); // ten stan jest w packach niewidzialny (model air)
        return barrel;
    }

    // ─────────────────────── WYSYŁKA ───────────────────────

    /** Startuje pętlę broadcastu (raz, przy starcie pluginu). */
    public void start(org.bukkit.plugin.java.JavaPlugin plugin) {
        if (!enabled) return;
        plugin.getServer().getScheduler().runTaskTimer(plugin,
                () -> broadcastAll(null), BROADCAST_INTERVAL_TICKS, BROADCAST_INTERVAL_TICKS);
    }

    /**
     * Wysyła pełną listę urządzeń. Adresaci:
     *  - specificRecipient != null → tylko on (gracz wszedł na serwer),
     *  - w przeciwnym razie wszyscy online w świecie Raft.
     * Gracze poza światem Raft nie potrzebują danych (urządzenia nie są tam renderowane).
     */
    public void broadcastAll(Player specificRecipient) {
        if (!enabled) return;
        pl.vd.raft.RaftPlugin raft = pl.vd.raft.RaftPlugin.getInstance();
        if (raft == null) return;

        String worldName = raft.getConfig().getString("raft-world-name", "RAFT");
        List<DeviceEntry> entries = collectEntries(worldName);
        byte[] payload = encode(entries);
        if (payload == null) return;

        if (specificRecipient != null) {
            sendTo(specificRecipient, payload);
            return;
        }

        World raftWorld = Bukkit.getWorld(worldName);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (raftWorld != null && player.getWorld() != raftWorld) continue;
            sendTo(player, payload);
        }
    }

    /** kind 0 = odbiornik (indeks w DeviceModels.BY_KIND po stronie moda). */
    private static final int KIND_ODBIORNIK = 0;
    /** kind 1 = antena (faza 4 projektu anten — model + kable rysuje mod). */
    private static final int KIND_ANTENA = 1;

    private record DeviceEntry(int x, int y, int z, float yaw, int kind, int flags,
                               int linkX, int linkY, int linkZ) {
        /** Wpis bez linku (odbiornik, albo antena niepodłączona) — link to 0,0,0. */
        private DeviceEntry(int x, int y, int z, float yaw, int kind, int flags) {
            this(x, y, z, yaw, kind, flags, 0, 0, 0);
        }
    }

    /** Zbiera urządzenia z rejestru (odbiorniki, kind=0) i anteny (kind=1, faza 4). */
    private List<DeviceEntry> collectEntries(String worldName) {
        List<DeviceEntry> entries = new ArrayList<>();
        pl.vd.raft.RaftPlugin raft = pl.vd.raft.RaftPlugin.getInstance();
        if (raft == null) return entries;

        for (pl.vd.raft.blocks.DeviceBlock2x2 device : raft.getDeviceRegistry().getActiveDevices()) {
            Location base = device.getBaseLocation();
            World world = base.getWorld();
            if (world == null || !world.getName().equalsIgnoreCase(worldName)) continue;

            // kind: kolejność MUSI zgadzać się z DeviceModels.BY_KIND po stronie moda
            // (0 = odbiornik). Wykrywamy po kluczu rejestru, nie po displayName (kody kolorów).
            int kind = kindOf(raft, device.getDeviceType());
            if (kind < 0) continue;

            // flags bit 0 = włączony: odbiornik działa, gdy ma baterię z energią.
            int flags = device.hasBattery() && device.getEnergy() > 0 ? 1 : 0;

            // yaw: model odwracamy tak, by "front" urządzenia zgadzał się z rotacją
            // (DeviceBlock2x2: 0=South/180°, 1=West/90°, 2=North/0°, 3=East/270°).
            float yaw = switch (device.getRotation()) {
                case 0 -> 180f;
                case 1 -> 90f;
                case 3 -> 270f;
                default -> 0f;
            };

            entries.add(new DeviceEntry(base.getBlockX(), base.getBlockY(), base.getBlockZ(), yaw, kind, flags));
        }

        // Faza 4: anteny. Mod rysuje z nich słupek, a kable prowadzi od wtyku anteny
        // do gniazda odbiornika, więc musi znać pozycję tego odbiornika (link).
        if (raft.getAntennaManager() != null) {
            for (pl.vd.raft.blocks.AntennaDevice antenna : raft.getAntennaManager().getAntennas()) {
                Location base = antenna.getBaseLocation();
                World world = base.getWorld();
                if (world == null || !world.getName().equalsIgnoreCase(worldName)) continue;

                // Antena nie ma własnego obrotu (model symetryczny) — yaw zawsze 0.
                int flags = antenna.isPowered() ? 1 : 0;

                // Link: pozycja nośnika odbiornika (0,0,0 = antena niepodłączona).
                int linkX = 0;
                int linkY = 0;
                int linkZ = 0;
                pl.vd.raft.blocks.DeviceBlock2x2 receiver = antenna.getLinkedReceiver();
                if (receiver != null) {
                    Location l = receiver.getBaseLocation();
                    linkX = l.getBlockX();
                    linkY = l.getBlockY();
                    linkZ = l.getBlockZ();
                }

                entries.add(new DeviceEntry(base.getBlockX(), base.getBlockY(), base.getBlockZ(),
                        0f, KIND_ANTENA, flags, linkX, linkY, linkZ));
            }
        }
        return entries;
    }

    /**
     * Mapuje DeviceType → kind. Zwraca -1 dla typów bez modelu w modzie
     * (np. przyszłych nowych urządzeń) — wtedy ich nie wysyłamy, zamiast
     * ryzykować niezgodność indeksów z DeviceModels.BY_KIND.
     */
    private static int kindOf(pl.vd.raft.RaftPlugin raft, pl.vd.raft.blocks.DeviceRegistry.DeviceType type) {
        // Na razie jedyny model w modzie: odbiornik. Klucz rejestru bierzemy przez
        // porównanie instancji DeviceType (displayName zawiera kody kolorów).
        pl.vd.raft.blocks.DeviceRegistry.DeviceType odbiornik =
                raft.getDeviceRegistry().getType("modul_odbiorczy");
        if (type == odbiornik) return KIND_ODBIORNIK;
        return -1; // wiatrak i przyszłe typy: brak modelu, nie wysyłamy
    }

    private byte[] encode(List<DeviceEntry> entries) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            out.writeInt(entries.size());
            for (DeviceEntry e : entries) {
                out.writeInt(e.x());
                out.writeInt(e.y());
                out.writeInt(e.z());
                out.writeFloat(e.yaw());
                out.writeInt(e.kind());
                out.writeInt(e.flags());
                out.writeInt(e.linkX());
                out.writeInt(e.linkY());
                out.writeInt(e.linkZ());
            }
            return baos.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    private void sendTo(Player player, byte[] payload) {
        if (player == null || !player.isOnline()) return;

        // NMS direct path (Paper 26.2+) — ta sama ścieżka co raft:stats
        if (NMSPacketSender.isAvailable()) {
            Object discPayload = NMSPacketSender.createDiscardedPayload(NAMESPACE, PATH, payload);
            Object nmsPacket = NMSPacketSender.createCustomPayloadPacket(discPayload);
            if (nmsPacket != null) {
                NMSPacketSender.send(player, nmsPacket);
                return;
            }
        }

        // Fallback: ProtocolLib (starsze MC)
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
            // Bez ProtocolLib nic nie wysyłamy — mod po prostu nie narysuje urządzeń.
        }
    }
}
