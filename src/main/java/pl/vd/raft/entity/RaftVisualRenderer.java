package pl.vd.raft.entity;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

/**
 * RaftVisualRenderer - renderuje kazda komorke tratwy jako OSOBNY BlockDisplay.
 *
 * Architektura wzorowana na BlockShips (display per blok, a nie 1 rozciagnieta
 * deska): kazda deska/poklad to osobny, prawdziwy blok 3D plywajacy na wodzie
 * na sub-pixelowej pozycji. Dzieki temu:
 *  - tratwa wyglada jak tratwa (osobne deski), a nie jeden rozciagniety block
 *  - dobudowa/rozbudowa dodaje/usuwa naturalnie kolejne bloki
 *  - gracz vanilla widzi prawdziwe bloki 3D (BlockDisplay), nie "obrazki"
 *
 * Ruch: NMS delta move packety (RaftEntitySync) — klient interpoluje plynnie.
 */
public final class RaftVisualRenderer {

    private final RaftState raft;

    /** ID tratwy — doklejane do tagów BlockDisplay (atrybucja przy sprzątaniu orphanów). */
    private final UUID raftId;

    /** key komorki (RaftEntity.keyOf) -> BlockDisplay */
    private final Map<String, BlockDisplay> displays = new HashMap<>();

    /**
     * Wizual zawartości sieci zbierających (CellType.NET):
     *  - key komorki -> ItemDisplay: 1 ikona najliczniejszego złapanego przedmiotu,
     *    unosząca się w ŚRODKU komórki sieci (żeby z daleka było widać, że sieć
     *    coś złapała — bez otwierania GUI),
     *  - key komorki -> TextDisplay: mały licznik "[n]" z łączną liczbą sztuk,
     *    przy samej DOLNEJ krawędzi bloku sieci (najniżej nad podłożem).
     * Obie żyją dokładnie tak jak deski: spawn w sync(), ruch z tratwą
     * (moveSmoothly), znikanie w cull(), odtwarzanie po rozładowaniu chunku
     * (persistent=false) i sprzątanie przy rozbiórce komórki.
     */
    private final Map<String, ItemDisplay> netIcons = new HashMap<>();
    private final Map<String, TextDisplay> netCounters = new HashMap<>();

    /** Ikona przedmiotu: przesunięcie Y od środka komórki sieci (środek bloku). */
    private static final double NET_ICON_Y = 0.1;
    /** Licznik: przesunięcie Y od środka komórki sieci (dolna krawędź bloku). */
    private static final double NET_COUNTER_Y = -0.42;
    /** Skala licznika (mały napis, nie zasłania sieci). */
    private static final float NET_COUNTER_SCALE = 0.4f;

    public RaftVisualRenderer(RaftState raft, UUID raftId) {
        this.raft = raft;
        this.raftId = raftId;
    }

    // ─────────────────────── SYNC CO TICK ───────────────────────────

    /**
     * Synchronizuje wyswietlacze do listy komorek tratwy:
     *  - tworzy brakujace BlockDisplay (takze po rozladowaniu chunku)
     *  - przesuwa kazdy do aktualnej pozycji komorki (plynny sub-pixel ruch)
     *  - zmienia BlockData jesli komorka go zmienila (material / schody z kierunkiem)
     *  - usuwa wyswietlacze komorek, ktore zniknely
     */
    /** Transformacja pełnej komórki (jak zawsze: blok [−0.5, 0.5]³). */
    private static final Transformation FULL_BLOCK = new Transformation(
            new Vector3f(-0.5f, -0.5f, -0.5f),
            new Quaternionf(),
            new Vector3f(1f, 1f, 1f),
            new Quaternionf());

    /**
     * Synchronizuje wyswietlacze do listy komorek tratwy:
     *  - tworzy brakujace BlockDisplay (takze po rozladowaniu chunku)
     *  - przesuwa kazdy do aktualnej pozycji komorki (plynny sub-pixel ruch)
     *  - zmienia BlockData jesli komorka go zmienila (material / schody z kierunkiem)
     *  - stosuje transformacje (cienkie ściany: spłaszczony/rozciągnięty blok)
     *  - usuwa wyswietlacze komorek, ktore zniknely
     */
    public void sync(List<Vector> offsets, Map<String, org.bukkit.block.data.BlockData> cellData,
                     Map<String, Transformation> transforms,
                     Map<String, ItemStack> netIcons,
                     Map<String, Integer> netCounts) {
        World world = raft.getWorld();
        if (world == null) return;

        Vector pos = raft.getPosition();
        Set<String> needed = new HashSet<>();

        for (Vector offset : offsets) {
            String key = RaftEntity.keyOf(offset);
            needed.add(key);

            org.bukkit.block.data.BlockData data = cellData.get(key);
            if (data == null) data = Material.OAK_PLANKS.createBlockData();

            Transformation transform = transforms != null ? transforms.get(key) : null;
            if (transform == null) transform = FULL_BLOCK;

            double x = pos.getX() + offset.getX();
            double y = pos.getY() + offset.getY();
            double z = pos.getZ() + offset.getZ();

            BlockDisplay display = displays.get(key);
            if (display == null || display.isDead() || !display.isValid() || !display.getWorld().equals(world)) {
                display = spawn(world, x, y, z, data, transform);
                displays.put(key, display);
            } else {
                if (!data.equals(display.getBlock())) {
                    display.setBlock(data);
                }
                RaftEntitySync.moveSmoothly(display, x, y, z);
            }

            // Wizual zawartości sieci zbierających — ikona + licznik nad/we komórce
            ItemStack netIcon = netIcons != null ? netIcons.get(key) : null;
            int netCount = netCounts != null ? netCounts.getOrDefault(key, 0) : 0;
            if (netIcon != null && netCount > 0) {
                syncNetVisuals(world, key, x, y, z, netIcon, netCount);
            } else {
                removeNetVisuals(key);
            }
        }

        // Usun wyswietlacze nieistniejacych juz komorek (rozbudowa w druga strone)
        Iterator<Map.Entry<String, BlockDisplay>> it = displays.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, BlockDisplay> entry = it.next();
            if (!needed.contains(entry.getKey())) {
                BlockDisplay display = entry.getValue();
                if (display != null && !display.isDead()) {
                    display.remove();
                }
                it.remove();
                removeNetVisuals(entry.getKey());
            }
        }
    }

    // ───────────── WIZUAL ZAWARTOŚCI SIECI (ikona + licznik) ─────────────

    /**
     * Utrzymuje ikonę (ItemDisplay) i licznik (TextDisplay) komórki sieci:
     * tworzy brakujące, aktualizuje zawartość tylko przy zmianie i przesuwa
     * razem z tratwą. Gdy sieć jest pusta — encje są usuwane.
     */
    private void syncNetVisuals(World world, String key, double x, double y, double z,
                                ItemStack iconStack, int total) {
        // Ikona najliczniejszego przedmiotu — w środku bloku sieci
        ItemDisplay icon = netIcons.get(key);
        if (icon == null || icon.isDead() || !icon.isValid() || !icon.getWorld().equals(world)) {
            icon = spawnNetIcon(world, x, y + NET_ICON_Y, z, iconStack);
            netIcons.put(key, icon);
        } else {
            ItemStack current = icon.getItemStack();
            if (current == null || !current.equals(iconStack)) {
                icon.setItemStack(iconStack);
            }
            RaftEntitySync.moveSmoothly(icon, x, y + NET_ICON_Y, z);
        }

        // Licznik "[n]" — przy dolnej krawędzi bloku sieci
        String text = "[" + total + "]";
        TextDisplay counter = netCounters.get(key);
        if (counter == null || counter.isDead() || !counter.isValid() || !counter.getWorld().equals(world)) {
            counter = spawnNetCounter(world, x, y + NET_COUNTER_Y, z, text);
            netCounters.put(key, counter);
        } else {
            if (!text.equals(counter.getText())) {
                counter.setText(text);
            }
            RaftEntitySync.moveSmoothly(counter, x, y + NET_COUNTER_Y, z);
        }
    }

    /** Ikona przedmiotu w środku komórki sieci (jak przedmiot leżący w sieci). */
    private ItemDisplay spawnNetIcon(World world, double x, double y, double z, ItemStack stack) {
        return world.spawn(new Location(world, x, y, z), ItemDisplay.class, d -> {
            d.setItemStack(stack);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
            d.setInvulnerable(true);
            d.setSilent(true);
            d.setPersistent(false); // nie zapisuj do chunku — przy rozładowaniu znika, odtworzymy
            d.addScoreboardTag(RaftEntity.TAG_VISUAL);
            d.addScoreboardTag("raft_" + raftId);
        });
    }

    /** Mały licznik "[n]" przy dolnej krawędzi bloku sieci (czyta się jak napis). */
    private TextDisplay spawnNetCounter(World world, double x, double y, double z, String text) {
        return world.spawn(new Location(world, x, y, z), TextDisplay.class, d -> {
            d.setText(text);
            d.setBillboard(Display.Billboard.CENTER); // zawsze przodem do gracza
            d.setAlignment(TextDisplay.TextAlignment.CENTER);
            d.setTransformation(new Transformation(
                    new Vector3f(), new Quaternionf(),
                    new Vector3f(NET_COUNTER_SCALE, NET_COUNTER_SCALE, NET_COUNTER_SCALE),
                    new Quaternionf()));
            d.setInvulnerable(true);
            d.setSilent(true);
            d.setPersistent(false);
            d.addScoreboardTag(RaftEntity.TAG_VISUAL);
            d.addScoreboardTag("raft_" + raftId);
        });
    }

    /** Usuwa ikonę i licznik komórki (pusta sieć / rozebrana komórka / cull). */
    private void removeNetVisuals(String key) {
        ItemDisplay icon = netIcons.remove(key);
        if (icon != null && !icon.isDead()) icon.remove();
        TextDisplay counter = netCounters.remove(key);
        if (counter != null && !counter.isDead()) counter.remove();
    }

    // ─────────────────────── SPAWN ───────────────────────────────────

    /**
     * Blok BlockDisplay renderuje sie OD swojej pozycji (sześcian [pos, pos+1]),
     * wiec spawn w srodku komorki MUSI byc polaczony z translacja (-0.5, -0.5, -0.5)
     * — wtedy wizualna deska zajmuje dokladnie [srodek-0.5, srodek+0.5], czyli ten
     * sam szescian, co kolizja (shulker zakotwiczony COLLIDER_Y_OFFSET=0.5 pod
     * srodkiem komorki). Bez tego deska byla przesunieta o pol bloka w kazda os
     * wzgledem kolizji („shulkery nie sa w tym samym miejscu co deski").
     */
    private BlockDisplay spawn(World world, double x, double y, double z,
                               org.bukkit.block.data.BlockData data, Transformation transform) {
        return world.spawn(new Location(world, x, y, z), BlockDisplay.class, d -> {
            d.setBlock(data);
            // Wyrownaj szescian bloku [pos, pos+1] do srodka komorki: [pos-0.5, pos+0.5]
            // (cienka ściana dostaje spłaszczenie/rozciągnięcie — transform jest
            // ustawiany tylko przy spawnie; sync przesuwa same pozycje pakietami)
            d.setTransformation(transform != null ? transform : FULL_BLOCK);
            d.setInvulnerable(true);
            d.setSilent(true);
            d.setPersistent(false); // nie zapisuj do chunku — przy rozladowaniu znika, odtworzymy
            d.addScoreboardTag(RaftEntity.TAG_VISUAL);
            // Tag z ID tratwy — żeby sprzątanie orphanów mogło odróżnić nasze deski
            // od cudzych BlockDisplay i usunąć te po nieistniejącej już tratwie.
            d.addScoreboardTag("raft_" + raftId);
        });
    }

    // ─────────────────────── STATUS / CLEANUP ────────────────────────

    /** Czy jakikolwiek wyswietlacz tratwy zyje. */
    public boolean isAlive() {
        for (BlockDisplay d : displays.values()) {
            if (d != null && !d.isDead() && d.isValid()) return true;
        }
        return false;
    }

    public int getDisplayCount() {
        return displays.size();
    }

    /**
     * Wygasza WSZYSTKIE deski (BlockDisplay) — Faza 2: gdy nikt nie jest w
     * zasięgu wyświetlania, czysty wizual znikamy ze świata (mniej encji po
     * stronie serwera i klientów). Kolizje (shulkery/markery) NIE są ruszane.
     * Deski wracają automatycznie przy najbliższym sync() po pojawieniu się
     * gracza w zasięgu.
     */
    public void cull() {
        for (BlockDisplay d : displays.values()) {
            if (d != null && !d.isDead()) d.remove();
        }
        displays.clear();
        for (ItemDisplay d : netIcons.values()) {
            if (d != null && !d.isDead()) d.remove();
        }
        netIcons.clear();
        for (TextDisplay d : netCounters.values()) {
            if (d != null && !d.isDead()) d.remove();
        }
        netCounters.clear();
    }

    public void cleanup() {
        cull();
    }
}
