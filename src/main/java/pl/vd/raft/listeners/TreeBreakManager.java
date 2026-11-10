package pl.vd.raft.listeners;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Mechanika strącania drzew — Valheim / Rust style.
 *
 * Zasady:
 *  - Klikasz LEWYM przyciskiem na pień (log) trzymając siekierę
 *  - Flood-fill szuka połączonych logów tego samego typu (max 30 bloków)
 *  - Wooden axe = 7 klików, stone = 5, iron = 4, diamond = 3, netherite = 2
 *  - Stały cooldown0.9s między kliknięciami
 *  - Po osiągnięciu progu → drewno leci naraz, liście cascade 1-2s
 */
public class TreeBreakManager implements Listener {

    private final pl.vd.raft.RaftPlugin plugin;
    private final ItemPickupManager itemPickupManager;

    public TreeBreakManager(pl.vd.raft.RaftPlugin plugin, ItemPickupManager itemPickupManager) {
        this.plugin = plugin;
        this.itemPickupManager = itemPickupManager;
    }

    /** Maksymalna liczba logów w flood-fill (zabezpieczenie). */
    private static final int MAX_LOGS = 30;

    /** Cooldown między kliknięciami (w ms). */
    private static final long HIT_COOLDOWN_MS = 900;

    /** Czas cascade liści po zniszczeniu drewna (w tickach). */
    private static final int LEAF_CASCADE_DELAY_TICKS = 10;

    /** Promień szukania liści wokół drewna. */
    private static final int LEAF_RADIUS = 4;

    /** Mapa: "world: x y z" → postęp bicia tego drzewa. */
    private final Map<String, TreeHitData> hitMap = new HashMap<>();

    /** Mapa cooldownów: UUID gracza → timestamp ostatniego uderzenia (ms). */
    private final Map<UUID, Long> cooldownMap = new HashMap<>();

    /** Po jakim czasie (ms) nieaktywny wpis w hitMap jest usuwany. */
    private static final long HIT_DATA_TTL_MS = 480_000; // 8 minut

    /** Wytrzymałość siekier (ile uderzeń mogą przyjąć). */
    private static final Map<Material, Integer> AXE_DURABILITY = Map.of(
            Material.WOODEN_AXE,  36,
            Material.STONE_AXE,   45,
            Material.IRON_AXE,    60,
            Material.DIAMOND_AXE, 75
    );

    // ────────────────────────────────────────────────────────────
    //  Event handler
    // ────────────────────────────────────────────────────────────

    @EventHandler
    public void onLeftClickBlock(PlayerInteractEvent event) {
        // Tylko lewy klik bloku, tylko main hand
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!isAxe(item)) return;

        Block clicked = event.getClickedBlock();
        if (clicked == null) return;
        if (!isLog(clicked.getType())) return;

        // Cooldown
        long now = System.currentTimeMillis();
        Long lastHit = cooldownMap.get(player.getUniqueId());
        if (lastHit != null && (now - lastHit) < HIT_COOLDOWN_MS) {

            event.setCancelled(true);
            return;
        }

        // Znajdź klucz tego drzewa (najniższy log w strukturze = "korzeń")
        String treeKey = getTreeKey(clicked);
        TreeHitData data = hitMap.computeIfAbsent(treeKey, k -> {
            // Flood-fill startuje ZAWSZE od korzenia — nie od klikniętego bloku
            // dzięki temu każde kliknięcie w dowolny pień widzi ten sam zestaw logów
            Block root = findRoot(clicked);
            List<Block> connected = floodFillLogs(root);
            int requiredHits = calculateRequiredHits(item.getType(), connected.size());
            return new TreeHitData(connected, requiredHits, player.getUniqueId());
        });

        // Sprawdź czy którykolwiek log z tego drzewa już nie istnieje
        if (data.logs.stream().anyMatch(b -> b.getType() == Material.AIR)) {
            hitMap.remove(treeKey);
            data = hitMap.computeIfAbsent(treeKey, k -> {
                Block root = findRoot(clicked);
                List<Block> connected = floodFillLogs(root);
                int requiredHits = calculateRequiredHits(item.getType(), connected.size());
                return new TreeHitData(connected, requiredHits, player.getUniqueId());
            });
        }

        // Uderzenie
        data.hits++;
        data.lastHitMs = now; // aktualizuj timestamp aktywności
        cooldownMap.put(player.getUniqueId(), now);

        // Zużycie wytrzymałości siekiery
        damageAxe(player, item);

        // Cleanup starych wpisów (co 50 uderzeń, żeby nie lagować)
        if (hitMap.size() > 50 || Math.random() < 0.02) {
            hitMap.entrySet().removeIf(e -> (now - e.getValue().lastHitMs) > HIT_DATA_TTL_MS);
        }

        // Feedback — dźwięk + bar postępu
        float progress = (float) data.hits / data.requiredHits;
        clicked.getWorld().playSound(
            clicked.getLocation(),
            Sound.BLOCK_WOOD_HIT,
            0.8f,
            0.8f + progress * 0.6f
        );
        spawnHitParticles(clicked, progress);



        // Drzewo ścięte!
        if (data.hits >= data.requiredHits) {
            fellTree(data.logs, clicked);
            hitMap.remove(treeKey);
        }

        event.setCancelled(true);
    }

    // ────────────────────────────────────────────────────────────
    //  Flood fill — szukanie połączonych logów
    // ────────────────────────────────────────────────────────────

    /**
     * Flood-fill od danego bloku — szuka połączonych logów tego samego Material.
     * Przeszuka: x ±2, y 0→+12, z ±2 (darees are taller than wide).
     */
    private List<Block> floodFillLogs(Block start) {
        Material type = start.getType();
        List<Block> found = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Deque<Block> queue = new ArrayDeque<>();

        queue.add(start);
        visited.add(locKey(start));

        while (!queue.isEmpty() && found.size() < MAX_LOGS) {
            Block current = queue.poll();
            found.add(current);

            // Sprawdź sąsiadów: x ±1, z ±1, y 0..+3 (w górę více, w dół mniej)
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dy = 0; dy <= 2; dy++) {
                        if (dx == 0 && dz == 0 && dy == 0) continue;
                        Block neighbor = current.getRelative(dx, dy, dz);
                        String key = locKey(neighbor);
                        if (!visited.add(key)) continue;
                        if (neighbor.getType() == type) {
                            queue.add(neighbor);
                        }
                    }
                }
            }
            // też w dół 1 (pień może mieć korzenie)
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    Block below = current.getRelative(dx, -1, dz);
                    String key = locKey(below);
                    if (!visited.add(key)) continue;
                    if (below.getType() == type) {
                        queue.add(below);
                    }
                }
            }
        }

        return found;
    }

    // ────────────────────────────────────────────────────────────
    //  Strącanie drzewa
    // ────────────────────────────────────────────────────────────

    private void fellTree(List<Block> logs, Block origin) {
        World world = origin.getWorld();

        // 1. Usuń wszystkie logi — auto-pickup przez ItemPickupManager
        Player nearestPlayer = findNearestPlayer(origin);
        for (Block log : logs) {
            log.setType(Material.AIR, false);
            if (nearestPlayer != null) {
                itemPickupManager.tryPickup(nearestPlayer, new ItemStack(log.getType()));
            } else {
                world.dropItemNaturally(log.getLocation(), new ItemStack(log.getType()));
            }
        }

        // 2. Zbierz wszystkie liście w promieniu
        List<Block> leaves = findNearbyLeaves(logs, LEAF_RADIUS);

        // 3. Cascade — liście lecą po kolei z losowym opóźnieniem
        Collections.shuffle(leaves);
        for (int i = 0; i < leaves.size(); i++) {
            Block leaf = leaves.get(i);
            int delay = (i / 3) + new Random().nextInt(LEAF_CASCADE_DELAY_TICKS); // cascade co ~3 liście
            Bukkit.getScheduler().runTaskLater(
                plugin.getParent(),
                () -> {
                    if (leaf.getType() != Material.AIR) {
                        leaf.setType(Material.AIR, false);
                        // Particle przy każdym liściu
                        leaf.getWorld().spawnParticle(
                            Particle.BLOCK,
                            leaf.getLocation().add(0.5, 0.5, 0.5),
                            5, 0.2, 0.2, 0.2,
                            leaf.getType().createBlockData()
                        );
                    }
                },
                delay
            );
        }

        // 4. Duże drzewo = dużo liści — particle + dźwięk na starcie
        world.playSound(
            origin.getLocation(),
            Sound.BLOCK_WOOD_BREAK,
            1.2f, 0.6f
        );
        world.spawnParticle(
            Particle.BLOCK,
            origin.getLocation().add(0.5, 2, 0.5),
            40, 1.0, 1.0, 1.0,
            Material.OAK_LOG.createBlockData()
        );
    }

    // ────────────────────────────────────────────────────────────
    //  Szukanie liści
    // ────────────────────────────────────────────────────────────

    private List<Block> findNearbyLeaves(List<Block> logs, int radius) {
        Set<String> visited = new HashSet<>();
        List<Block> leaves = new ArrayList<>();

        for (Block log : logs) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -2; dy <= radius; dy++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        Block block = log.getRelative(dx, dy, dz);
                        String key = locKey(block);
                        if (visited.add(key) && isLeaf(block.getType())) {
                            leaves.add(block);
                        }
                    }
                }
            }
        }
        return leaves;
    }

    // ────────────────────────────────────────────────────────────
    //  Narzędzia
    // ────────────────────────────────────────────────────────────

    /** Znajduje najbliższego gracza do bloku (do auto-pickup). */
    private Player findNearestPlayer(Block block) {
        Player nearest = null;
        double minDist = Double.MAX_VALUE;
        for (Player p : block.getWorld().getPlayers()) {
            double dist = p.getLocation().distanceSquared(block.getLocation());
            if (dist < minDist) {
                minDist = dist;
                nearest = p;
            }
        }
        // Tylko jeśli gracz jest w rozsądnym zasięgu (16 bloków)
        return (nearest != null && minDist <= 256) ? nearest : null;
    }

    /** Klucz drzewa = pozycja najniższego loga (korzeń). */
    private String getTreeKey(Block block) {
        return locKey(findRoot(block));
    }

    /** Znajduje korzeń drzewa — schodzi w dół po logach tego samego typu. */
    private Block findRoot(Block block) {
        Material type = block.getType();
        Block lowest = block;
        while (lowest.getRelative(0, -1, 0).getType() == type) {
            lowest = lowest.getRelative(0, -1, 0);
        }
        return lowest;
    }

    private String locKey(Block b) {
        return b.getWorld().getName() + ":" + b.getX() + ":" + b.getY() + ":" + b.getZ();
    }

    private boolean isLog(Material mat) {
        return mat.name().endsWith("_LOG") || mat.name().endsWith("_WOOD");
    }

    private boolean isLeaf(Material mat) {
        return mat.name().endsWith("_LEAVES") || mat.name().endsWith("_LEAVES_PERSISTENT");
    }

    private boolean isAxe(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        return item.getType().name().endsWith("_AXE");
    }

    /**
     * Oblicza ile uderzeń potrzeba do strącenia drzewa.
     * Wooden = 6, Stone = 5, Iron = 4, Diamond = 3.
     */
    private int calculateRequiredHits(Material axeType, int logCount) {
        int baseHits;
        switch (axeType) {
            case WOODEN_AXE  -> baseHits = 6;
            case STONE_AXE   -> baseHits = 5;
            case IRON_AXE    -> baseHits = 4;
            case DIAMOND_AXE -> baseHits = 3;
            default          -> baseHits = 6;
        }
        // Duże drzewa (>10 logów) = +2 hity więcej
        if (logCount > 10) baseHits += 2;
        return baseHits;
    }

    /**
     * Zużywa wytrzymałość siekiery o 1. Gdy = 0 → łamie siekierę.
     * Publiczny statyczny — współdzielony z RaftAxeListener (rozbiórka tratwy).
     */
    public static void damageAxe(Player player, ItemStack item) {
        Material axeType = item.getType();
        Integer maxDurability = AXE_DURABILITY.get(axeType);
        if (maxDurability == null) return;

        // Pobierz obecną wytrzymałość
        short currentDurability = item.getDurability();
        int newDurability = currentDurability + 1;

        if (newDurability >= maxDurability) {
            // Siekiera się łamie
            player.getInventory().setItemInMainHand(null);
            player.getWorld().playSound(
                player.getLocation(),
                Sound.ENTITY_ITEM_BREAK,
                1.0f, 1.0f
            );
        } else {
            item.setDurability((short) newDurability);
        }
    }

    private void spawnHitParticles(Block block, float progress) {
        World world = block.getWorld();
        Location loc = block.getLocation().add(0.5, 0.5, 0.5);

        // Crack particles — im więcej postępu, tym więcej
        int count = (int) (progress * 8) + 2;
        world.spawnParticle(
            Particle.BLOCK,
            loc,
            count, 0.3, 0.3, 0.3,
            block.getType().createBlockData()
        );

        // Przy pełnym postępie — extra
        if (progress > 0.8f) {
            world.spawnParticle(
                Particle.ENCHANTED_HIT,
                loc,
                6, 0.4, 0.4, 0.4
            );
        }
    }

    // ────────────────────────────────────────────────────────────
    //  Dane drzewa
    // ────────────────────────────────────────────────────────────

    // ────────────────────────────────────────────────────────────
    //  Czyszczenie gdy gracz oddali się 2 chunki od drzewa
    // ────────────────────────────────────────────────────────────

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        Location to = event.getTo();
        if (to == null) return;

        // Sprawdź tylko ruch między chunkami (optymalizacja)
        Location from = event.getFrom();
        if (from.getBlockX() >> 4 == to.getBlockX() >> 4
                && from.getBlockZ() >> 4 == to.getBlockZ() >> 4) {
            return; // w tym samym chuniku — nie sprawdzaj
        }

        // Znajdź wpisy w hitMap dla tego gracza i sprawdź dystans
        hitMap.entrySet().removeIf(entry -> {
            TreeHitData data = entry.getValue();
            if (!uuid.equals(data.playerUUID)) return false;
            Location treeLoc = data.treeLocation;
            if (treeLoc == null) return false;
            // 2 chunki = 32 bloki
            double distSq = to.distanceSquared(treeLoc);
            return distSq > (32 * 32);
        });
    }

    // ────────────────────────────────────────────────────────────
    //  Dane drzewa
    // ────────────────────────────────────────────────────────────

    private static class TreeHitData {
        final List<Block> logs;
        final int requiredHits;
        final UUID playerUUID;
        final Location treeLocation;
        int hits = 0;
        long lastHitMs = System.currentTimeMillis();

        TreeHitData(List<Block> logs, int requiredHits, UUID playerUUID) {
            this.logs = logs;
            this.requiredHits = requiredHits;
            this.playerUUID = playerUUID;
            this.treeLocation = logs.isEmpty() ? null : logs.get(0).getLocation();
        }
    }
}
