package pl.vd.raft.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import pl.vd.raft.RaftPlugin;
import pl.vd.raft.entity.RaftEntity;
import pl.vd.raft.items.ItemManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Podgląd-duch stawiania na tratwie — wzorowany na podglądzie wieżyczek w VD
 * (StandPresetManager.Preview): po wyborze bloku w GUI młota gracz widzi, gdzie
 * blok stanie, zanim go postawi.
 *
 *  - Duch = świecący BlockDisplay dokładnie takiego kształtu, jaki stanie
 *    (podłoga — pełna komórka pokładu), dopasowany do SIATKI komórek tratwy
 *    (całe komórki — „kwadratowe" przesuwanie jak w grze Raft).
 *  - Kolor świecenia: zielony = można postawić (masz materiał), czerwony =
 *    miejsce OK, ale nie masz przedmiotu budowlanego w ekwipunku.
 *  - PRAWY KLIK młotem (w powietrze, w blok albo w encję tratwy) zatwierdza
 *    postawienie (zużywa 1 materiał z ekwipunku); duch zostaje — można budować
 *    dalej wzdłuż krawędzi.
 *  - Anulowanie: zmiana przedmiotu z ręki, otwarcie GUI młota (shift+PPM),
 *    LEWY klik myszą, wyjście z gry / śmierć.
 *  - OBRÓT: kółko myszy obraca ducha o 90° w prawo (góra) / w lewo (dół),
 *    klawisze 1–4 ustawiają kierunek N/E/S/W, Shift+kółko wraca do kierunku
 *    z patrzenia — obsługę zdarzeń trzyma RaftBuildListener (tam mieszka stan
 *    kierunku, wspólny dla ducha i stawiania z ręki), a tu jest tylko
 *    natychmiastowe odświeżenie podglądu po obrocie ({@link #refresh}).
 *
 * Pozycja ducha liczona jest TYM SAMYM kodzkiem co faktyczne stawianie
 * (RaftBuildListener.computePlacement), więc duch zawsze pokazuje dokładnie to,
 * co zostanie postawione po PPM.
 */
public class RaftBuildPreview implements Listener {

    /** Zielony: można postawić. */
    private static final Color VALID = Color.fromRGB(85, 255, 85);
    /** Czerwony: miejsce wolne, ale brak materiału w ekwipunku. */
    private static final Color NO_MATERIAL = Color.fromRGB(255, 85, 85);

    /** Stan podglądu jednego gracza. */
    private static final class Ghost {
        final String buildId;
        BlockDisplay display;

        Ghost(String buildId) {
            this.buildId = buildId;
        }
    }

    private final RaftPlugin plugin;
    private final Map<UUID, Ghost> ghosts = new HashMap<>();

    public RaftBuildPreview(RaftPlugin plugin) {
        this.plugin = plugin;
        // Podgląd aktualizowany co 2 ticki — jak podgląd stojaków w VD
        Bukkit.getScheduler().runTaskTimer(plugin.getParent(), this::tick, 5L, 2L);
    }

    // ─────────────────────── API ───────────────────────────────────

    /** Wybór bloku w GUI młota: zacznij podgląd (i usuń poprzedni). */
    public void select(Player player, String buildId) {
        cancel(player);
        ghosts.put(player.getUniqueId(), new Ghost(buildId));
        update(player, ghosts.get(player.getUniqueId()));
    }

    /** Czy gracz ma aktywny podgląd-duch budowy (PPM = potwierdzenie, nie GUI). */
    public boolean hasGhost(Player player) {
        return ghosts.containsKey(player.getUniqueId());
    }

    /** Anuluje podgląd gracza (usuwa ducha ze świata). */
    public void cancel(Player player) {
        Ghost g = ghosts.remove(player.getUniqueId());
        if (g != null && g.display != null && !g.display.isDead()) {
            g.display.remove();
        }
    }

    /**
     * Natychmiastowe odświeżenie ducha (np. po obrocie kółkiem) — bez czekania
     * na najbliższy tick podglądu, żeby gracz widział nowy kierunek od razu.
     * Bez ducha nic nie robi.
     */
    public void refresh(Player player) {
        Ghost ghost = ghosts.get(player.getUniqueId());
        if (ghost != null) update(player, ghost);
    }

    /** LEWY klik anuluje budowanie — podświetlany blok (duch) znika. */
    @EventHandler
    public void onLeftClickCancel(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_AIR
                && event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (ghosts.containsKey(player.getUniqueId())) {
            cancel(player);
        }
    }

    // ─────────────────────── TICK (co 2 ticki) ────────────────────

    private void tick() {
        // Sprzątanie: gracz offline / martwy / nie trzyma już młota → usuń ducha
        ghosts.entrySet().removeIf(e -> {
            Player p = Bukkit.getPlayer(e.getKey());
            boolean stale = p == null || !p.isOnline() || p.isDead() || !holdsHammer(p);
            if (stale) {
                Ghost g = e.getValue();
                if (g.display != null && !g.display.isDead()) g.display.remove();
            }
            return stale;
        });
        for (Map.Entry<UUID, Ghost> e : ghosts.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) update(p, e.getValue());
        }
    }

    private boolean holdsHammer(Player player) {
        ItemManager im = plugin.getItemManager();
        return im != null && im.isCustomItem(player.getInventory().getItemInMainHand(), "construction_hammer");
    }

    // ─────────────────────── AKTUALIZACJA DUCHA ───────────────────

    private void update(Player player, Ghost ghost) {
        RaftBuildListener build = plugin.getRaftBuildListener();
        if (build == null) return;
        RaftBuildListener.Placement pl = build.computePlacement(player, ghost.buildId);

        if (!pl.ok || pl.raft == null) {
            // Brak ważnego miejsca — ukryj ducha (nie pokazuj „czerwonego widma")
            if (ghost.display != null) {
                ghost.display.remove();
                ghost.display = null;
            }
            return;
        }

        // Środek wizualu względem środka tratwy + kształt (grubość/wysokość)
        Vector center = pl.target;
        RaftEntity.CellType type = pl.type;
        Material material = pl.material;
        BlockFace facing = pl.facing != null ? pl.facing : BlockFace.NORTH;
        float thickness = type == RaftEntity.CellType.WALL ? RaftEntity.THIN_WALL_THICKNESS : 1f;
        int height = type == RaftEntity.CellType.WALL ? RaftEntity.TALL_WALL_HEIGHT : 1;

        Vector pos = pl.raft.getCenter().toVector();
        Location loc = new Location(player.getWorld(),
                pos.getX() + center.getX(),
                pos.getY() + center.getY(),
                pos.getZ() + center.getZ());

        BlockData data = RaftEntity.dataFor(material, type, facing);
        Transformation transform = RaftEntity.shapeTransform(type, facing,
                new RaftEntity.CellShape(thickness, height));
        boolean hasMaterial = hasItem(player, ghost.buildId);

        if (ghost.display == null || ghost.display.isDead()
                || !ghost.display.getWorld().equals(player.getWorld())) {
            spawn(player, loc, ghost, data, transform, hasMaterial);
        } else {
            ghost.display.teleport(loc);
            if (!data.equals(ghost.display.getBlock())) {
                ghost.display.setBlock(data);
            }
            ghost.display.setGlowColorOverride(hasMaterial ? VALID : NO_MATERIAL);
        }
    }

    private void spawn(Player player, Location loc, Ghost ghost,
                       BlockData data, Transformation transform, boolean hasMaterial) {
        ghost.display = player.getWorld().spawn(loc, BlockDisplay.class, d -> {
            d.setBlock(data);
            d.setTransformation(transform);
            d.setGlowing(true);
            d.setGlowColorOverride(hasMaterial ? VALID : NO_MATERIAL);
            d.setInvulnerable(true);
            d.setSilent(true);
            d.setGravity(false);
            d.setPersistent(false); // znika przy rozładowaniu chunku — odtworzymy
        });
    }

    // ─────────────────────── POTWIERDZENIE (PPM) ──────────────────

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (RaftBuildListener.isClickConsumed(event)) return; // klik obsłużony wcześniej
        Player player = event.getPlayer();
        if (player.isSneaking()) return; // shift+PPM = GUI młota (nie potwierdzenie)

        Ghost ghost = ghosts.get(player.getUniqueId());
        if (ghost == null) return;
        if (!holdsHammer(player)) return;

        event.setCancelled(true);
        confirm(player, ghost);
    }

    /**
     * Zapasowa ścieżka: PPM młotem w ENCJĘ tratwy (niewidzialną kolizję-shulker
     * albo deskę-wizual). Z bliska klient wysyła interakcję z encją, a nie PPM
     * w powietrze — bez tego potwierdzenie postawienia nie odpalało mimo
     * zielonego ducha (ten sam wzór co sieć zbierająca / budowanie przedmiotem).
     */
    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        if (player.isSneaking()) return; // shift+PPM = GUI młota
        Ghost ghost = ghosts.get(player.getUniqueId());
        if (ghost == null) return;
        if (!holdsHammer(player)) return;
        if (!RaftEntity.isRaftEntity(event.getRightClicked())) return;

        event.setCancelled(true);
        confirm(player, ghost);
    }

    /**
     * LEWY klik w ENCJĘ tratwy też anuluje podgląd-ducha — z bliska klient
     * wysyła atak na encję (EntityDamageByEntityEvent), a nie LEWY klik
     * w powietrze/blok, więc bez tego duch zostawał na ekranie.
     */
    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!RaftEntity.isRaftEntity(event.getEntity())) return;
        if (ghosts.containsKey(player.getUniqueId())) {
            cancel(player);
        }
    }

    /**
     * Potwierdza postawienie z aktywnego podglądu-ducha (PPM młotem): liczy
     * miejsce TYM SAMYM kodem co stawianie z ręki (computePlacement) i zużywa
     * 1 materiał z ekwipunku. Duch zostaje — można stawiać dalej wzdłuż krawędzi.
     */
    private void confirm(Player player, Ghost ghost) {
        RaftBuildListener build = plugin.getRaftBuildListener();
        if (build == null) return;

        RaftBuildListener.Placement pl = build.computePlacement(player, ghost.buildId);
        if (!pl.ok) {
            return;
        }
        if (!hasItem(player, ghost.buildId)) {
            return;
        }
        if (build.execute(player, pl, null)) {
            takeOne(player, ghost.buildId); // zużyj 1 materiał z ekwipunku
            update(player, ghost);
        }
    }

    // ─────────────────────── POMOCNICZE ───────────────────────────

    /** Czy gracz ma choć 1 szt. przedmiotu budowlanego w ekwipunku. */
    private boolean hasItem(Player player, String itemId) {
        ItemManager im = plugin.getItemManager();
        if (im == null) return false;
        for (ItemStack s : player.getInventory().getContents()) {
            if (s != null && im.isCustomItem(s, itemId)) return true;
        }
        return false;
    }

    /** Wyjmuje 1 szt. przedmiotu budowlanego z ekwipunku (zwraca stack lub null). */
    private ItemStack takeOne(Player player, String itemId) {
        ItemManager im = plugin.getItemManager();
        if (im == null) return null;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s == null || !im.isCustomItem(s, itemId)) continue;
            if (s.getAmount() > 1) {
                s.setAmount(s.getAmount() - 1);
                inv.setItem(i, s);
            } else {
                inv.setItem(i, null);
            }
            return s.clone();
        }
        return null;
    }

}