package pl.vd.raft.listeners;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import pl.vd.raft.RaftPlugin;
import pl.vd.raft.entity.RaftEntity;
import pl.vd.raft.entity.RaftManager;
import pl.vd.raft.hook.KeyStateReceiver;
import pl.vd.raft.items.ItemManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Faza 4 — budowanie tratwy (elementy strukturalne jak w grze Raft).
 *
 * Zasady:
 *  - PRAWY KLIK przedmiotem budowlanym (wooden_foundation / stone_foundation)
 *    stawia komórkę podłogi obok istniejącej komórki pokładu (sąsiad w kierunku
 *    patrzenia), tylko nad wodą — tratwa poszerza się na boki.
 *  - PRAWY KLIK skrzynką (chest_small / chest_medium / chest_large) stawia ją
 *    NA pokładzie — komórka dokładnie NAD deską, w którą celujesz (skrzynia
 *    stoi na tratwie, a nie rozbudowuje jej wzdłuż jak fundament).
 *  - PRAWY KLIK drewnianymi schodami (wooden_stairs, kategoria „Inne" w GUI
 *    młota) stawia je tak samo NA pokładzie; schody są komórką OBROTOWĄ —
 *    kierunek ustawia się kółkiem/presetami, a bez wyboru bierze się z patrzenia.
 *  - PRAWY KLIK podporą piętra (wooden_support, kategoria „Inne") stawia SŁUPEK
 *    w narożniku 4 komórek (węzeł siatki). Podpora nie zajmuje komórki i nie ma
 *    kolizji, ale TRZYMA piętro: platformę (Y=3) można dobudować maks. 1 blok od
 *    słupka — dalej trzeba postawić kolejną podporę (węzły co 2 kratki).
 *  - Młot budowlany (RaftHammerListener + RaftBuildPreview): wybór bloku w GUI
 *    pokazuje podgląd-ducha, a PPM młotem zatwierdza postawienie.
 *  - Każda postawiona komórka kosztuje 1 przedmiot.
 *  - Zdarzenie BlockPlace jest anulowane — przedmioty budowlane NIGDY nie
 *    stawiają waniliowych bloków (to by zostawiło statyczne bloki w oceanie).
 *
 * computePlacement() to JEDNO źródło prawdy o tym, gdzie/co stanie — używane
 * zarówno przy stawianiu przedmiotem z ręki, jak i przez podgląd-ducha, więc
 * duch zawsze pokazuje dokładnie to, co zostanie postawione.
 *
 * OBRÓT BUDOWLI (kierunek komórek obrotowych: skrzynie, a z czasem schody
 * i ściany): stan trzymany per gracz w tej klasie — patrz {@link #facingFor}.
 *  - kółko myszy w trybie budowy (aktywny duch z GUI młota): góra = 90° w prawo,
 *    dół = 90° w lewo (presety kierunku: klawisze 1–4 = N/E/S/W),
 *  - poza trybem budowy kółko obraca z trzymanym lewym Altem (mod raft-hud)
 *    albo Shiftem, i tylko z przedmiotem obrotowym w ręce,
 *  - brak wybranego kierunku = kierunek z patrzenia gracza (auto).
 *
 * Uwaga na klik Z BLISKA: pokład tratwy to encje (niewidzialne kolizje-shulkery
 * + deski BlockDisplay), więc klient wysyła interakcję z encją, a NIE PPM
 * w powietrze. Dlatego budowanie obsługują DWIE ścieżki: onInteract (powietrze/
 * blok) i onInteractEntity (klik w kolizję/deskę) — obie liczą to samo przez
 * computePlacement.
 */
public class RaftBuildListener implements Listener {

    /** Zasięg budowania od tratwy. */
    public static final double BUILD_RANGE = 6.0;

    /** Okno trafienia raycasta po siatce komórek (± bloki od środka komórki). */
    private static final double HIT_WINDOW = 0.6;

    /**
     * Wynik rozstrzygnięcia miejsca stawiania (computePlacement): gdzie stanie
     * komórka, z jakiego materiału/typu i czy to wydłużenie ściany pod spodem.
     */
    public static final class Placement {
        public boolean ok;
        public String message = "";
        public RaftEntity raft;
        /** Docelowy offset komórki (względem środka tratwy). */
        public Vector target;
        public Material material;
        public RaftEntity.CellType type;
        public BlockFace facing;
        /** Czy to PODPORA piętra (węzeł narożny) — wtedy stawiamy węzeł, nie komórkę. */
        public boolean support;
        /** Węzeł podpory (narożnik 4 komórek); tylko gdy {@link #support}. */
        public Vector supportNode;
    }

    /**
     * Poziom PIĘTRA w osi Y (środek komórki platformy): podstawa słupka + jego
     * wysokość + pół komórki. Domyślnie 0.5 + 2 + 0.5 = 3 — dwie kratki pokoju
     * między pokładem a piętrem. 0 = podpory/piętra wyłączone w configu.
     * Jedno źródło prawdy: {@link RaftEntity#floorLevel()} (tego samego poziomu
     * używa renderer belek podpiętrza).
     */
    public static double floorLevel() {
        return RaftEntity.floorLevel();
    }

    private final RaftPlugin plugin;

    /**
     * Cztery kierunki poziome w kolejności obrotu W PRAWO (zgodnie
     * ze wskazówkami zegara): N → E → S → W.
     */
    private static final BlockFace[] ROTATION_CYCLE = {
            BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST
    };

    /**
     * Wybrany kierunek budowli per gracz. Brak wpisu = AUTO: kierunek z patrzenia
     * (liczony przy każdym rozstrzygnięciu miejsca), dzięki czemu gracz, który
     * nigdy nie kręcił kółkiem, buduje „tak jak patrzy".
     */
    private final Map<UUID, BlockFace> buildFacings = new HashMap<>();

    public RaftBuildListener(RaftPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Czy klik PPM został już obsłużony przez inny listener? NIE używamy do
     * tego isCancelled(): dla kliknięć W POWIETRZE wanilia startuje z
     * useInteractedBlock = DENY (bo nie ma bloku), więc isCancelled() jest dla
     * nich ZAWSZE true i „zjadało" każdy taki klik — a tratwa pływa po oceanie,
     * więc PPM w powietrze to najczęstszy przypadek. Sprawdzamy wyniki
     * rozdzielnie (wzór z RaftHammerListener): klik w blok — czy inny listener
     * zjadł klik w blok; powietrze — czy zjadł użycie przedmiotu.
     */
    public static boolean isClickConsumed(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            return event.useInteractedBlock() == Event.Result.DENY;
        }
        return event.useItemInHand() == Event.Result.DENY;
    }

    // ─────────────────────── INTERAKCJA (PRZEDMIOT W RĘCE) ────────

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (isClickConsumed(event)) return; // klik obsłużony wcześniej (np. GUI sieci zbierającej)
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) return;

        ItemManager im = plugin.getItemManager();
        if (im == null) return;

        // Przedmioty budowlane — stawianie komórek (młot budowlany ma własny
        // listener: shift+PPM otwiera GUI młota, RaftHammerListener)
        String buildId = buildItemId(im, item);
        if (buildId == null) return;
        event.setCancelled(true); // nigdy nie stawiaj waniliowego bloku

        Placement p = computePlacement(player, buildId);
        if (!p.ok) {
            // Bez komunikatu na czacie (AGENTS.md pkt 1) — powód zostaje w p.message.
            return;
        }
        execute(player, p, item);
    }

    /**
     * Zapasowa ścieżka: PPM w ENCJĘ tratwy (niewidzialną kolizję-shulker albo
     * deskę-wizual). Z bliska klient wysyła interakcję z encją, a nie PPM
     * w powietrze — bez tej ścieżki stawianie „w pokład" nie odpalało mimo
     * zielonego ducha.
     */
    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!RaftEntity.isRaftEntity(event.getRightClicked())) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!item.hasItemMeta()) return;

        ItemManager im = plugin.getItemManager();
        if (im == null) return;
        String buildId = buildItemId(im, item);
        if (buildId == null) return; // młot budowlany ma własne listenery

        event.setCancelled(true); // nigdy nie interaguj z kolizją tratwy
        Placement p = computePlacement(player, buildId);
        if (!p.ok) return;
        execute(player, p, item);
    }

    /**
     * Zabezpieczenie: gdyby przedmiot budowlany został użyty w sposób, który
     * Bukkit zamieniłby na postawienie bloku (np. kliknięcie w ścianę wyspy),
     * anuluj — komórki tratwy stawia tylko tryBuild.
     */
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemManager im = plugin.getItemManager();
        if (im == null) return;
        if (buildItemId(im, event.getItemInHand()) != null) {
            event.setCancelled(true);
        }
    }

    // ─────────────────────── ROZSTRZYGNIĘCIE MIEJSCA ──────────────

    /**
     * Oblicza, gdzie i co stanie się po potwierdzeniu budowy (wspólne dla
     * stawiania przedmiotem i podglądu-ducha). Niczego nie zmienia — tylko
     * rachunek. Gdy !ok, message zawiera powód (wyłącznie diagnostyka —
     * odkąd komunikaty budowania zostały usunięte, nie jest nigdzie pokazywany).
     */
    public Placement computePlacement(Player player, String buildId) {
        Placement p = new Placement();

        RaftEntity raft = RaftManager.getInstance().getNearestRaft(player, BUILD_RANGE);
        if (raft == null) {
            p.message = "§cNie ma tratwy w pobliżu (6 bloków).";
            return p;
        }
        p.raft = raft;

        Vector eye = player.getEyeLocation().toVector();
        Vector dir = player.getEyeLocation().getDirection();
        Vector hit = raycastCell(raft, eye, dir);
        if (hit == null) {
            hit = nearestCellTo(raft, player.getLocation().toVector());
        }
        if (hit == null) {
            p.message = "§cWyceluj w tratwę.";
            return p;
        }

        // ── PIĘTRO: podpora trzyma platformę nad sobą ────────────────────
        // Dwie drogi wejścia: celowanie w SŁUPEK podpory (pierwsza komórka
        // piętra) oraz celowanie w ISTNIEJĄCE piętro (dobudowa w bok).
        if (isFloorBuild(buildId) && floorLevel() > 0) {
            Vector node = raycastSupport(raft, eye, dir);
            if (node != null) {
                Vector cell = floorCellNearNode(raft, node,
                        player.getLocation().toVector().subtract(raft.getCenter().toVector()));
                if (cell != null) {
                    p.target = cell;
                    p.material = foundationMaterial(buildId);
                    p.type = RaftEntity.CellType.FLOOR;
                    p.ok = true;
                    return p;
                }
            }
            if (Math.abs(hit.getY() - floorLevel()) < 0.01) {
                // Piętro rośnie w bok jak pokład, ale KAŻDA komórka musi stać
                // WOKÓŁ podpory (podpora w jednym z jej narożników) — dalej niż
                // jedna kratka od słupka gracz NIE dobuduje, musi postawić
                // kolejną podporę (węzły co 2 kratki).
                Vector target = neighborToward(hit, dir);
                if (raft.getCellType(target) != null) {
                    p.message = "§cTo miejsce jest już zajęte.";
                    return p;
                }
                if (!raft.isCellSupported(target)) {
                    p.message = "§cPiętro musi trzymać się podpory (4 komórki wokół słupka).";
                    return p;
                }
                p.target = target;
                p.material = foundationMaterial(buildId);
                p.type = RaftEntity.CellType.FLOOR;
                p.ok = true;
                return p;
            }
        }

        // Przedmioty PIĘTROWE (Podłoga, wooden_floor): tylko platforma piętra — na wodzie,
        // jako pokład tratwy, postawić się NIE da. Duch budowy w ogóle się nie
        // pokazuje (p.ok = false), więc gracz nie widzi możliwości budowy.
        if (!isDeckBuild(buildId)) {
            p.message = "§cPodłoga staje tylko na piętrze (podpora + poziom piętra).";
            return p;
        }

        switch (buildId) {
            case "wooden_support" -> {
                if (hit.getY() != 0) {
                    p.message = "§cPodporę stawiaj na pokładzie (narożnik 4 bloków).";
                    return p;
                }
                Vector node = nearestCorner(raft, hit, player);
                if (node == null || !touchesDeck(raft, node)) {
                    p.message = "§cPodporę stawiaj na pokładzie (narożnik 4 bloków).";
                    return p;
                }
                if (raft.hasSupportAt(node)) {
                    p.message = "§cW tym narożniku stoi już podpora.";
                    return p;
                }
                // Węzeł nie jest komórką: podgląd-duch rysuje blok w miejscu
                // słupka (display bloku startuje w narożniku min), a execute()
                // stawia węzeł przez addSupport.
                p.support = true;
                p.supportNode = node;
                p.material = pl.vd.raft.entity.RaftSupportRenderer.visualMaterial();
                p.target = node.clone().subtract(new Vector(0.5, 0, 0.5));
                p.ok = true;
                return p;
            }
            case "wooden_foundation", "stone_foundation", "collection_net" -> {
                if (hit.getY() != 0) {
                    p.message = "§cPodłogę stawiaj obok pokładu, nie na ścianie.";
                    return p;
                }
                p.target = neighborToward(hit, dir);
                p.material = switch (buildId) {
                    case "stone_foundation" -> Material.COBBLESTONE;
                    case "collection_net" -> Material.EXPOSED_COPPER_GRATE;
                    default -> Material.OAK_PLANKS;
                };
                p.type = buildId.equals("collection_net")
                        ? RaftEntity.CellType.NET : RaftEntity.CellType.FLOOR;
            }
            // Skrzynie i schody STOJĄ NA pokładzie (jak w grze Raft): komórka
            // dokładnie NAD deską, w którą celujesz — nie rozbudowuje tratwy
            // wzdłuż (tak robią fundamenty: sąsiad w kierunku patrzenia).
            case "chest_small", "chest_medium", "chest_large", "wooden_stairs" -> {
                if (hit.getY() != 0) {
                    p.message = "§cTo stawiaj na pokładzie, nie na ścianie.";
                    return p;
                }
                RaftEntity.CellType below = raft.getCellType(hit);
                if (below != RaftEntity.CellType.RAFT && below != RaftEntity.CellType.FLOOR) {
                    p.message = "§cMożna stawiać tylko na pokładzie (deski/podłoga).";
                    return p;
                }
                p.target = hit.clone().add(new Vector(0, 1, 0));
                p.material = switch (buildId) {
                    case "chest_small" -> Material.TRAPPED_CHEST;
                    case "chest_large" -> Material.WAXED_COPPER_CHEST;
                    case "wooden_stairs" -> Material.OAK_STAIRS;
                    default -> Material.CHEST;
                };
                p.type = switch (buildId) {
                    case "chest_small" -> RaftEntity.CellType.CHEST_18;
                    case "chest_large" -> RaftEntity.CellType.CHEST_54;
                    case "wooden_stairs" -> RaftEntity.CellType.STAIRS;
                    default -> RaftEntity.CellType.CHEST_36;
                };
            }
            default -> {
                p.message = "§cNieznany element budowlany.";
                return p;
            }
        }

        if (raft.getCellType(p.target) != null) {
            p.message = "§cTo miejsce jest już zajęte.";
            return p;
        }
        if (p.type == RaftEntity.CellType.FLOOR && !hasWaterBelow(raft, p.target)) {
            p.message = "§cPod tym miejscem nie ma wody.";
            return p;
        }
        // Kierunek budowli: komórki obrotowe (skrzynie, schody, ściany) dostają
        // kierunek wybrany kółkiem/presetem, a bez wyboru — z patrzenia gracza.
        // Reszta typów (podłoga, sieć) kierunek ignoruje.
        p.facing = RaftEntity.isOrientable(p.type) ? facingFor(player) : null;
        p.ok = true;
        return p;
    }

    // ─────────────────────── OBRÓT BUDOWLI ────────────────────────

    /**
     * Kierunek budowli dla gracza: wybrany kółkiem/presetem, a gdy gracz nic
     * nie wybrał — domyślny z patrzenia (dominująca oś pozioma).
     */
    public BlockFace facingFor(Player player) {
        BlockFace chosen = buildFacings.get(player.getUniqueId());
        return chosen != null ? chosen : lookFacing(player.getEyeLocation().getDirection());
    }

    /** Obrót o kroki × 90° w prawo (ujemne = w lewo) i zwrócenie nowego kierunku. */
    public BlockFace rotateFacing(Player player, int steps) {
        BlockFace next = rotate(facingFor(player), steps);
        buildFacings.put(player.getUniqueId(), next);
        return next;
    }

    /** Ustawia kierunek (preset 1–4: N/E/S/W) i zwraca go. */
    public BlockFace setFacing(Player player, BlockFace face) {
        buildFacings.put(player.getUniqueId(), face);
        return face;
    }

    /** Kasuje wybór — budowla znów bierze kierunek z patrzenia. */
    public BlockFace clearFacing(Player player) {
        buildFacings.remove(player.getUniqueId());
        return lookFacing(player.getEyeLocation().getDirection());
    }

    /**
     * Kierunek przewinięcia kółkiem → kroki obrotu: W GÓRĘ to 90° w prawo.
     * Wanilia (sprawdzone w bytecode 26.3,
     * ScrollWheelHandler.getNextScrollWheelSelection) robi kółko w górę jako
     * slot −1 z zawinięciem 0 → 8, więc "w górę" to (prev − next == 1) także
     * przy zawinięciu. Każdy inny ruch (w dół, skok klawiszem) = w lewo.
     */
    public static int wheelSteps(int previousSlot, int newSlot) {
        return (newSlot - previousSlot == -1 || (previousSlot == 0 && newSlot == 8)) ? 1 : -1;
    }

    /** Czysta matematyka obrotu: kroki × 90° w prawo (N → E → S → W). */
    public static BlockFace rotate(BlockFace from, int steps) {
        int index = 0;
        for (int i = 0; i < ROTATION_CYCLE.length; i++) {
            if (ROTATION_CYCLE[i] == from) {
                index = i;
                break;
            }
        }
        return ROTATION_CYCLE[Math.floorMod(index + steps, ROTATION_CYCLE.length)];
    }

    /**
     * Domyślny kierunek z patrzenia: oś XZ o większej wartości bezwzględnej
     * (wektor (0,0,1) to południe, (0,0,-1) północ — konwencja Bukkit).
     * Patrzenie pionowo (brak osi poziomej) to brak wskazówki → północ, tak
     * samo jak brak wektora.
     */
    public static BlockFace lookFacing(Vector direction) {
        if (direction == null) return BlockFace.NORTH;
        double ax = Math.abs(direction.getX());
        double az = Math.abs(direction.getZ());
        if (ax < 1.0e-4 && az < 1.0e-4) return BlockFace.NORTH;
        if (ax >= az) {
            return direction.getX() >= 0 ? BlockFace.EAST : BlockFace.WEST;
        }
        return direction.getZ() >= 0 ? BlockFace.SOUTH : BlockFace.NORTH;
    }

    /**
     * Kółko myszy i klawisze 1–4 = obrót budowli:
     *  - tryb budowy (aktywny duch z GUI młota): kółko w górę = 90° w prawo,
     *    w dół = 90° w lewo; klawisze 1–4 = kierunek N/E/S/W (presety);
     *    Shift+kółko = powrót do kierunku z patrzenia,
     *  - poza trybem budowy zwykłe przewijanie ekwipunku działa jak zawsze;
     *    obrót tylko świadomym gestem — kółko z trzymanym lewym Altem (wymaga
     *    moda raft-hud; bez moda serwer nie widzi Alt) albo z Shiftem (to samo
     *    działanie bez moda) — i tylko z przedmiotem obrotowym w ręce.
     */
    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        int prev = event.getPreviousSlot();
        int next = event.getNewSlot();
        if (prev == next) return;

        int steps = wheelSteps(prev, next);

        RaftBuildPreview preview = plugin.getRaftBuildPreview();
        boolean building = preview != null && preview.hasGhost(player);

        if (building) {
            event.setCancelled(true);
            if (next >= 0 && next <= 3) {
                // Presety kierunku 1–4 = N/E/S/W (celowo pod klawiszami hotbara:
                // w trybie budowy gracz ustawia kierunek, nie zmienia slotu).
                setFacing(player, ROTATION_CYCLE[next]);
            } else if (player.isSneaking()) {
                clearFacing(player); // Shift+kółko = kierunek z patrzenia
            } else {
                rotateFacing(player, steps);
            }
            preview.refresh(player); // duch pokazuje nowy kierunek od razu
            return;
        }

        // Poza trybem budowy: obracamy tylko wtedy, gdy gracz TRZYMA przedmiot
        // obrotowy i świadomie użyje Alt/Shift — inaczej zjedlibyśmy zwykłe
        // przewijanie ekwipunku.
        ItemManager im = plugin.getItemManager();
        if (im == null || !isRotatableBuildItem(im, player.getInventory().getItemInMainHand())) {
            return;
        }
        boolean alt = altHeld(player);
        if (!alt && !player.isSneaking()) return;

        // Bez moda Alt nie istnieje — Shift+kółko daje TEN SAM obrót.
        event.setCancelled(true);
        rotateFacing(player, steps);
    }

    /** Czy przedmiot to budowlany element o kierunku (schody, skrzynie, z czasem ściany). */
    private boolean isRotatableBuildItem(ItemManager im, ItemStack item) {
        String id = buildItemId(im, item);
        if (id == null) return false;
        RaftEntity.CellType type = switch (id) {
            case "wooden_stairs" -> RaftEntity.CellType.STAIRS;
            case "chest_small" -> RaftEntity.CellType.CHEST_18;
            case "chest_large" -> RaftEntity.CellType.CHEST_54;
            case "chest_medium" -> RaftEntity.CellType.CHEST_36;
            default -> null;
        };
        return type != null && RaftEntity.isOrientable(type);
    }

    /**
     * Czy gracz trzyma lewy Alt. Alt nie istnieje w zdarzeniach Bukkita, więc
     * informację przynosi TYLKO mod raft-hud (kanał raft:key_state, bit2) —
     * gracz bez moda obraca kółkiem w trybie budowy albo Shiftem.
     */
    private boolean altHeld(Player player) {
        pl.vd.raft.hook.HookManager hooks = plugin.getHookManager();
        if (hooks == null) return false;
        KeyStateReceiver states = hooks.getKeyStateReceiver();
        if (states == null) return false;
        KeyStateReceiver.KeyState keys = states.get(player.getUniqueId());
        return keys != null && keys.alt;
    }

    /** Stan obrotu nie przeżywa wyjścia z gry — po powrocie znów auto z patrzenia. */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        buildFacings.remove(event.getPlayer().getUniqueId());
    }

    /**
     * Wykonuje postawienie z rozstrzygnięcia computePlacement. Po udanej akcji
     * zużywa toConsume (stack z ręki gracza). Zwraca true po sukcesie, false gdy
     * przekroczono limit komórek. Zgodnie z AGENTS.md pkt 1 nic nie wysyła do
     * gracza — powód porażki zostaje w Placement.message (tylko diagnostyka).
     */
    public boolean execute(Player player, Placement p, ItemStack toConsume) {
        boolean placed = p.support
                ? (p.supportNode != null && p.raft.addSupport(p.supportNode))
                : p.raft.addCell(p.target, p.material, p.type, p.facing);
        if (!placed) {
            // Limit komórek przekroczony — bez komunikatu (AGENTS.md pkt 1).
            return false;
        }
        if (toConsume != null) consume(player, toConsume);
        return true;
    }

    // ─────────────────────── POMOCNICZE ───────────────────────────

    /** Zwraca id przedmiotu budowlanego albo null (nie budowlany). */
    public String buildItemId(ItemManager im, ItemStack item) {
        if (im.isCustomItem(item, "wooden_support")) return "wooden_support";
        if (im.isCustomItem(item, "wooden_foundation")) return "wooden_foundation";
        if (im.isCustomItem(item, "stone_foundation")) return "stone_foundation";
        if (im.isCustomItem(item, "collection_net")) return "collection_net";
        if (im.isCustomItem(item, "wooden_stairs")) return "wooden_stairs";
        if (im.isCustomItem(item, "wooden_floor")) return "wooden_floor";
        if (im.isCustomItem(item, "chest_small")) return "chest_small";
        if (im.isCustomItem(item, "chest_medium")) return "chest_medium";
        if (im.isCustomItem(item, "chest_large")) return "chest_large";
        return null;
    }

    /**
     * Raycast po siatce komórek tratwy: punkt na promieniu patrzenia trafia w
     * komórkę, gdy odległość od jej środka < HIT_WINDOW w każdej osi (offsety
     * względem środka tratwy). Zwraca offset trafionej komórki lub null.
     */
    public Vector raycastCell(RaftEntity raft, Vector eye, Vector dir) {
        Vector pos = raft.getCenter().toVector();
        for (double t = 0.25; t <= BUILD_RANGE; t += 0.25) {
            double ox = eye.getX() + dir.getX() * t - pos.getX();
            double oy = eye.getY() + dir.getY() * t - pos.getY();
            double oz = eye.getZ() + dir.getZ() * t - pos.getZ();
            for (Vector off : raft.getBlockOffsets()) {
                // Wysoka ściana (h > 1) zajmuje cały pion [off.y−0.5, off.y−0.5+h] —
                // celowanie w górną połowę też musi trafiać w komórkę.
                int h = raft.getCellHeight(off);
                double yCenter = h > 1 ? off.getY() - 0.5 + h / 2.0 : off.getY();
                double yWindow = h > 1 ? h / 2.0 + 0.05 : HIT_WINDOW;
                if (Math.abs(ox - off.getX()) < HIT_WINDOW
                        && Math.abs(oz - off.getZ()) < HIT_WINDOW
                        && Math.abs(oy - yCenter) < yWindow) {
                    return off.clone();
                }
            }
        }
        return null;
    }

    /** Najbliższa komórka tratwy do punktu (fallback, gdy raycast nie trafił). */
    public Vector nearestCellTo(RaftEntity raft, Vector point) {
        Vector pos = raft.getCenter().toVector();
        Vector best = null;
        double bestD = Double.MAX_VALUE;
        for (Vector off : raft.getBlockOffsets()) {
            double dx = point.getX() - pos.getX() - off.getX();
            double dy = point.getY() - pos.getY() - off.getY();
            double dz = point.getZ() - pos.getZ() - off.getZ();
            double d = dx * dx + dy * dy + dz * dz;
            if (d < bestD) {
                bestD = d;
                best = off;
            }
        }
        return best;
    }

    /** Sąsiad komórki w kierunku patrzenia (dominująca oś XZ). */
    public Vector neighborToward(Vector hit, Vector dir) {
        Vector n = hit.clone();
        if (Math.abs(dir.getX()) >= Math.abs(dir.getZ())) {
            n.setX(n.getX() + (dir.getX() >= 0 ? 1 : -1));
        } else {
            n.setZ(n.getZ() + (dir.getZ() >= 0 ? 1 : -1));
        }
        return n;
    }

    /** Czy pod docelową komórką pokładu jest woda (tratwa musi pływać). */
    public boolean hasWaterBelow(RaftEntity raft, Vector target) {
        Vector pos = raft.getCenter().toVector();
        int bx = (int) Math.floor(pos.getX() + target.getX());
        int by = (int) Math.floor(pos.getY() + target.getY() - 1);
        int bz = (int) Math.floor(pos.getZ() + target.getZ());
        return raft.getState().getWorld().getBlockAt(bx, by, bz).getType() == Material.WATER;
    }

    // ─────────────────────── PODPORY PIĘTER (POMOCNICZE) ──────────

    /** Czy id przedmiotu to podłoga (fundament) — jedyny element, który rośnie w piętro. */
    private static boolean isFoundation(String buildId) {
        return "wooden_foundation".equals(buildId) || "stone_foundation".equals(buildId);
    }

    /**
     * Czy tym przedmiotem wolno postawić PODŁOGĘ PIĘTRA (platforma na poziomie
     * {@link #floorLevel()}). Obie podłogi (drewniana/kamienna) mogą być też
     * pokładem, a „Podłoga" (wooden_floor) — wyłącznie piętrem.
     */
    public static boolean isFloorBuild(String buildId) {
        return isFoundation(buildId) || "wooden_floor".equals(buildId);
    }

    /**
     * Czy tym przedmiotem wolno postawić POKŁAD tratwy (poziom 0, na wodzie).
     * „Podłoga" (wooden_floor) jest przedmiotem piętrowym — jej tu nie ma, więc
     * na wodzie nie da się jej postawić i podgląd-duch gaśnie.
     */
    public static boolean isDeckBuild(String buildId) {
        return !"wooden_floor".equals(buildId);
    }

    /** Materiał podłogi dla id (drewniana/kamienna/piętrowa; sieć ma własny materiał). */
    private static Material foundationMaterial(String buildId) {
        return "stone_foundation".equals(buildId) ? Material.COBBLESTONE : Material.OAK_PLANKS;
    }

    /**
     * Narożnik komórki najbliższy graczowi (4 kandydaci: x±0.5, z±0.5) — tym
     * sposobem gracz trafia „na koniec 4 bloków" bez celowania w punkt: podpora
     * staje w rogu bloku, który ma przed sobą.
     */
    public Vector nearestCorner(RaftEntity raft, Vector cell, Player player) {
        if (cell == null) return null;
        Vector local = player.getLocation().toVector().subtract(raft.getCenter().toVector());
        double[] xs = {cell.getX() - 0.5, cell.getX() + 0.5};
        double[] zs = {cell.getZ() - 0.5, cell.getZ() + 0.5};
        Vector best = null;
        double bestD = Double.MAX_VALUE;
        for (double x : xs) {
            for (double z : zs) {
                double d = (x - local.getX()) * (x - local.getX())
                        + (z - local.getZ()) * (z - local.getZ());
                if (d < bestD) {
                    bestD = d;
                    best = new Vector(x, RaftEntity.SUPPORT_BASE_Y, z);
                }
            }
        }
        return best;
    }

    /** Czy węzeł dotyka pokładu — co najmniej 1 z 4 komórek wokół niego istnieje. */
    public boolean touchesDeck(RaftEntity raft, Vector node) {
        for (double dx = -0.5; dx <= 0.5; dx += 1.0) {
            for (double dz = -0.5; dz <= 0.5; dz += 1.0) {
                if (raft.getCellType(new Vector(node.getX() + dx, 0, node.getZ() + dz)) != null) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Raycast po PODPORACH pięter (są cienkie i stoją na narożnikach, więc
     * raycast komórek ich nie łapie): punkt na promieniu patrzenia trafia
     * w słupek, gdy jest bliżej niż okno od jego osi X/Z i mieści się w pionie
     * słupka. Zwraca węzeł podpory albo null.
     */
    public Vector raycastSupport(RaftEntity raft, Vector eye, Vector dir) {
        if (raft == null || eye == null || dir == null) return null;
        Vector pos = raft.getCenter().toVector();
        final double window = 0.35;
        for (double t = 0.25; t <= BUILD_RANGE; t += 0.25) {
            double px = eye.getX() + dir.getX() * t;
            double py = eye.getY() + dir.getY() * t;
            double pz = eye.getZ() + dir.getZ() * t;
            for (Vector node : raft.getSupports()) {
                double cx = pos.getX() + node.getX();
                double cz = pos.getZ() + node.getZ();
                if (Math.abs(px - cx) >= window || Math.abs(pz - cz) >= window) continue;
                double bottom = pos.getY() + node.getY();
                double top = bottom + RaftEntity.SUPPORT_HEIGHT;
                if (py >= bottom - 0.25 && py <= top + 0.25) return node;
            }
        }
        return null;
    }

    /**
     * Komórka piętra wokół węzła podpory: z 4 komórek dotykających narożnika
     * wybieramy tę najbliższą graczowi (przewidywalne, gdy słupek stoi na
     * przecięciu czterech kratek). Zwraca null, gdy brak miejsca/wody pod spodem
     * albo gdy komórka nie spełnia reguły podparcia.
     */
    public Vector floorCellNearNode(RaftEntity raft, Vector node, Vector playerLocal) {
        double level = floorLevel();
        if (level <= 0) return null;
        double[] xs = {node.getX() - 0.5, node.getX() + 0.5};
        double[] zs = {node.getZ() - 0.5, node.getZ() + 0.5};
        Vector best = null;
        double bestD = Double.MAX_VALUE;
        for (double x : xs) {
            for (double z : zs) {
                Vector cell = new Vector(x, level, z);
                if (raft.getCellType(cell) != null) continue;
                if (!raft.isCellSupported(cell)) continue;
                // Piętro ma sens tylko nad pokładem (nie nad wodą za burtą)
                if (raft.getCellType(new Vector(x, 0, z)) == null) continue;
                double d = (x - playerLocal.getX()) * (x - playerLocal.getX())
                        + (z - playerLocal.getZ()) * (z - playerLocal.getZ());
                if (d < bestD) {
                    bestD = d;
                    best = cell;
                }
            }
        }
        return best;
    }

    /** Zużywa 1 sztukę przedmiotu z ręki gracza. */
    public void consume(Player player, ItemStack item) {
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }
    }

    public RaftPlugin getPlugin() {
        return plugin;
    }
}