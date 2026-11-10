package pl.vd.stand;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import pl.vd.VillagerDefensePlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Zapis i odtwarzanie wyglądu stojaka:
 * - pliki w folderze plugins/ServerPlugin/stojaki/<nazwa>.yml,
 * - zapis "na przedmiot" (PersistentDataContainer) - prawy klik przedmiotem stawia stojak,
 * - przedmiot "wiązany" z zapisem plikowym (trzyma tylko nazwę).
 */
public class StandPresetManager {

    /** Podgląd-duch (ghost): stojak pokazujący, gdzie wyląduje postawienie przedmiotem. */
    private static class Preview {
        ArmorStand ghost;
        double x, y, z;
    }

    private final VillagerDefensePlugin plugin;
    private final ArmorStandTuner tuner;

    /** Domyślne przesunięcie stawiania (nudge) - patrz /stojak offset. */
    private double offX, offY, offZ;

    /** Aktywne podglądy per gracz. */
    private final Map<UUID, Preview> previews = new HashMap<>();

    /** Schowek kopiowania (patrz /stojak copy i /stojak paste). */
    private StandPreset clipboard;
    /** Wysokość stóp skopiowanego stojaka względem wierzchu bloku pod spodem. */
    private double clipRel;

    private final NamespacedKey keyData;
    private final NamespacedKey keyName;
    private final NamespacedKey keyShop;
    private final NamespacedKey keySmall;
    private final NamespacedKey keyArms;
    private final NamespacedKey keyVisible;
    private final NamespacedKey keyBase;
    private final NamespacedKey keyHead;
    private final NamespacedKey keyHand;
    private final NamespacedKey keyOffhand;
    private final NamespacedKey keyHX, keyHY, keyHZ;
    private final NamespacedKey keyBX, keyBY, keyBZ;
    private final NamespacedKey keyRX, keyRY, keyRZ;
    private final NamespacedKey keyLX, keyLY, keyLZ;
    private final NamespacedKey keyYaw;
    private final NamespacedKey keyPitch;
    private final NamespacedKey keySink;
    private final NamespacedKey keyHp;
    private final NamespacedKey keyDekoracja;
    private final NamespacedKey keyStanA;
    private final NamespacedKey keyStanB;
    private final NamespacedKey keyParts;

    public StandPresetManager(VillagerDefensePlugin plugin, ArmorStandTuner tuner) {
        this.plugin = plugin;
        this.tuner = tuner;
        // trwałe przesunięcie stawiania (/stojak offset) - zapisane w config.yml
        offX = plugin.getConfig().getDouble("stojak.offset.x", 0);
        offY = plugin.getConfig().getDouble("stojak.offset.y", 0);
        offZ = plugin.getConfig().getDouble("stojak.offset.z", 0);
        keyData = key("stojak.data");
        keyName = key("stojak.name");
        keyShop = key("stojak.shop");
        keySmall = key("stojak.small");
        keyArms = key("stojak.arms");
        keyVisible = key("stojak.visible");
        keyBase = key("stojak.base");
        keyHead = key("stojak.head");
        keyHand = key("stojak.hand");
        keyOffhand = key("stojak.offhand");
        keyHX = key("stojak.hx");
        keyHY = key("stojak.hy");
        keyHZ = key("stojak.hz");
        keyBX = key("stojak.bx");
        keyBY = key("stojak.by");
        keyBZ = key("stojak.bz");
        keyRX = key("stojak.rx");
        keyRY = key("stojak.ry");
        keyRZ = key("stojak.rz");
        keyLX = key("stojak.lx");
        keyLY = key("stojak.ly");
        keyLZ = key("stojak.lz");
        keyYaw = key("stojak.yaw");
        keyPitch = key("stojak.pitch");
        keySink = key("stojak.sink");
        keyHp = key("stojak.hp");
        keyDekoracja = key("stojak.dekoracja");
        keyStanA = key("stojak.stanA");
        keyStanB = key("stojak.stanB");
        keyParts = key("stojak.parts");

        // podgląd-duch: aktualizowany co 2 ticki dla graczy trzymających przedmiot-zapis
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickPreviews, 5L, 2L);
    }

    private NamespacedKey key(String k) {
        return new NamespacedKey(plugin, k);
    }

    // ===================== zapis =====================

    /** Zapisuje wygląd stojaka (razem z doczepionymi częściami) do pliku. */
    public boolean save(ArmorStand stand, String name, double sink) {
        File folder = presetsFolder();
        if (!folder.exists() && !folder.mkdirs()) return false;
        StandPreset preset = StandPreset.fromStand(name, stand, sink, tuner.getParts(stand));
        preset.dekoracja = tuner.isDecoration(stand); // flaga dekoracji wraca do pliku
        preset.stanA = tuner.getStateA(stand); // powiązanie klik-przełącznika wraca do pliku
        preset.stanB = tuner.getStateB(stand);
        return preset.save(new File(folder, sanitize(name) + ".yml"));
    }

    /** Zapisuje wygląd stojaka na przedmiot trzymany w ręce (pełne dane w PDC). */
    public boolean saveToItem(Player player, ArmorStand stand, double sink) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) return false;
        StandPreset preset = StandPreset.fromStand(
                stand.getCustomName() != null ? stand.getCustomName() : "stojak", stand, sink,
                tuner.getParts(stand));
        preset.dekoracja = tuner.isDecoration(stand); // flaga dekoracji wraca do PDC przedmiotu
        preset.stanA = tuner.getStateA(stand); // powiązanie klik-przełącznika wraca do PDC przedmiotu
        preset.stanB = tuner.getStateB(stand);
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keyData, PersistentDataType.BYTE, (byte) 1);
        pdc.set(keyName, PersistentDataType.STRING, preset.name);
        writePreset(pdc, preset);
        meta.displayName(Component.text("§6Stojak: " + preset.name + " §7(prawy klik = postaw)"));
        item.setItemMeta(meta);
        return true;
    }

    // ===================== odczyt i stawianie =====================

    public StandPreset loadPreset(String name) {
        return StandPreset.load(new File(presetsFolder(), sanitize(name) + ".yml"));
    }

    public void setPlacementOffset(double x, double y, double z) {
        offX = x;
        offY = y;
        offZ = z;
        // zapisz trwale (config.yml), żeby offset przetrwał restart serwera
        plugin.getConfig().set("stojak.offset.x", x);
        plugin.getConfig().set("stojak.offset.y", y);
        plugin.getConfig().set("stojak.offset.z", z);
        plugin.saveConfig();
    }

    public double[] getPlacementOffset() {
        return new double[]{offX, offY, offZ};
    }

    /** Stawia zapisany stojak z pliku na bloku, na który gracz patrzy (albo na klikniętym). */
    public ArmorStand place(String name, Player player, Block clicked) {
        return place(name, player, clicked, null);
    }

    /** Stawia zapisany stojak; exact != null = dokładne koordynaty (stawianie "wszędzie"). */
    public ArmorStand place(String name, Player player, Block clicked, Location exact) {
        StandPreset preset = loadPreset(name);
        if (preset == null) return null;
        return place(preset, player, clicked, exact);
    }

    /** Stawia stojak z przedmiotu (zapis pełny albo wiązany po nazwie).
     *  Po UDANYM postawieniu zużywa 1 przedmiot - jeden przedmiot = jeden stojak,
     *  więc nie da się go ponownie użyć (np. wieżyczki ze sklepu areny). */
    public ArmorStand placeFromItem(Player player, Block clicked) {
        ItemStack item = player.getInventory().getItemInMainHand();
        StandPreset preset = presetFromItem(item);
        if (preset == null) return null;
        ArmorStand stand = place(preset, player, clicked, null);
        if (stand != null) {
            consumeOne(item, player);
        }
        return stand;
    }

    /** Zdejmuje 1 przedmiot z ręki gracza (0 sztuk = puste miejsce w ekwipunku). */
    private static void consumeOne(ItemStack item, Player player) {
        if (item == null) return;
        int amount = item.getAmount() - 1;
        if (amount <= 0) {
            player.getInventory().setItemInMainHand(null);
        } else {
            item.setAmount(amount);
        }
    }

    public ArmorStand place(StandPreset preset, Player player, Block clicked, Location exact) {
        Location loc = placementLocation(player, preset, clicked, exact);
        ArmorStand stand = loc.getWorld().spawn(loc, ArmorStand.class);
        preset.applyTo(stand);
        tuner.markEditable(stand);
        tuner.initPresetName(stand, preset.name); // nazwa presetu w PDC - animacja działa też bez custom name
        for (StandPreset.PartData part : preset.parts) {
            tuner.attachPart(stand, part);
        }
        boolean states = preset.stanA != null && preset.stanB != null;
        if (preset.dekoracja) {
            // dekoracja mapy: bez wieżyczki (strzały/bariera/HP) i bez sprzątania po grze
            tuner.setDecoration(stand, true);
        } else if (!states) {
            // klik-przełącznik stanów zachowuje się jak dekoracja: bez wieżyczki
            startTurretIfPresent(stand, preset);
            if (preset.hp > 0) {
                tuner.initTurretHp(stand, preset.hp);
                tuner.initTurretStats(stand, preset); // obrażenia/zasięg/interwał/efekt z pliku
            }
            if (preset.hp > 0) {
                tuner.markPlacer(stand, player); // właściciel wieżyczki = punkty za jej zabójstwa
                // sprzątanie po grze: zapamiętaj arenę, na której postawiono wieżyczkę
                for (pl.vd.arena.Arena arena : plugin.getArenaManager().getArenas().values()) {
                    if (arena.isPlaying(player)) {
                        tuner.markArena(stand, arena.getName());
                        break;
                    }
                }
            }
        }
        if (states) {
            // postaw stan A (początkowy) i włącz klik-przełącznik
            tuner.setStates(stand, preset.stanA, preset.stanB);
        }
        return stand;
    }

    /** Jeśli preset ma część obrotową (wieżyczka) - włącza auto-namierzanie mobów od razu. */
    private void startTurretIfPresent(ArmorStand stand, StandPreset preset) {
        if (preset == null || preset.parts.isEmpty()) return;
        boolean turret = false;
        for (StandPreset.PartData p : preset.parts) {
            if (p.turret) {
                turret = true;
                break;
            }
        }
        if (turret) tuner.enableTurretAim(stand);
    }

    // ===================== przedmioty =====================

    /** Czy przedmiot to "stojak-zapisany" (można nim postawić stojak). */
    public boolean isSavedItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(keyData);
    }

    /** Przedmiot wiązany z zapisem plikowym (trzyma tylko nazwę zapisu). */
    public ItemStack bindItem(String name) {
        return bindItem(name, "§6Stojak: " + name + " §7(prawy klik = postaw)", false);
    }

    /**
     * Przedmiot wiązany z zapisem plikowym z WŁASNĄ nazwą wyświetlaną.
     * shop=true oznacza przedmiot ze sklepu areny (wieżyczka) - może go
     * postawić KAŻDY gracz, nie tylko vd.admin.
     */
    public ItemStack bindItem(String name, String displayName, boolean shop) {
        return bindItem(name, displayName, shop, Material.ARMOR_STAND);
    }

    /** Jak wyżej, ale z wybranym materiałem przedmiotu. */
    public ItemStack bindItem(String name, String displayName, boolean shop, Material material) {
        StandPreset preset = loadPreset(name);
        if (preset == null) return null;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keyData, PersistentDataType.BYTE, (byte) 1);
        pdc.set(keyName, PersistentDataType.STRING, name);
        if (shop) pdc.set(keyShop, PersistentDataType.BYTE, (byte) 1);
        meta.displayName(Component.text(displayName));
        item.setItemMeta(meta);
        return item;
    }

    /** Czy przedmiot pochodzi ze sklepu areny (każdy gracz może go postawić)? */
    public boolean isShopItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(keyShop);
    }

    public StandPreset presetFromItem(ItemStack item) {
        if (!isSavedItem(item)) return null;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        if (pdc.has(keyName)) {
            String name = pdc.get(keyName, PersistentDataType.STRING);
            StandPreset fromFile = loadPreset(name);
            if (fromFile != null) return fromFile;
        }
        return readPreset(pdc);
    }

    // ===================== kopiowanie / wklejanie =====================

    /** Kopiuje stojak (wraz ze wszystkimi częściami) do schowka. */
    public boolean copyStand(ArmorStand stand) {
        if (stand == null || stand.isDead()) return false;
        clipboard = StandPreset.fromStand("kopia", stand, 1.0, tuner.getParts(stand));
        Location feet = stand.getLocation();
        Block below = stand.getWorld().getBlockAt(
                feet.getBlockX(), (int) Math.floor(feet.getY() - 0.01), feet.getBlockZ());
        clipRel = feet.getY() - (below.getY() + 1);
        return true;
    }

    /**
     * Stawia skopiowany stojak: na bloku, na który gracz patrzy (zachowując
     * wysokość względem podłoża jak w oryginale), albo na dokładnych
     * koordynatach stóp (exact != null). Schowek zostaje - można stawiać
     * wiele kopii, aż do następnego /stojak copy.
     */
    public ArmorStand pasteStand(Player player, Location exact) {
        if (clipboard == null) return null;
        Location feet;
        if (exact != null) {
            feet = exact.clone();
        } else {
            Location target = targetLocation(player, null);
            feet = target.add(0.5, 1 + clipRel, 0.5);
        }
        // place() traktuje x y z jako BLOK i odejmuje sink od y, a paste zna
        // STÓPY - przeliczamy z powrotem na blok, żeby kopia wylądowała tam,
        // gdzie gracz wskazał stopy
        return place(clipboard, player, null, feet.clone().add(0, clipboard.sink, 0));
    }

    // ===================== lista / usuwanie =====================

    public List<String> listPresets() {
        File folder = presetsFolder();
        List<String> names = new ArrayList<>();
        if (!folder.isDirectory()) return names;
        File[] files = folder.listFiles((dir, n) -> n.endsWith(".yml"));
        if (files == null) return names;
        for (File f : files) {
            names.add(f.getName().replaceFirst("\\.yml$", ""));
        }
        Collections.sort(names);
        return names;
    }

    /**
     * Usuwa zapis (plik presetu) z folderu. Komenda /stojak delete woła to
     * DOPIERO po podwójnym potwierdzeniu (/stojak delete <nazwa> confirm,
     * ważnym 20 s) - patrz StandCommand, żeby nie skasować presetu przez
     * przypadek.
     */
    public boolean delete(String name) {
        return new File(presetsFolder(), sanitize(name) + ".yml").delete();
    }

    // ===================== autosave (co minutę, JEDEN plik nadpisywany) =====================

    /** Plik autosave: stojaki/autosave.yml - nadpisywany w całości, wszystkie stojaki w środku. */
    private File autosaveFile() {
        return new File(presetsFolder(), "autosave.yml");
    }

    /** Zbiera wszystkie stojaki pluginu we wszystkich światach jako presety z pozycją. */
    private List<StandPreset> autosaveSnapshot() {
        List<StandPreset> out = new ArrayList<>();
        for (org.bukkit.World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                if (!(e instanceof ArmorStand stand) || stand.isDead()) continue;
                if (isPreview(stand)) continue;
                if (!tuner.isPluginStand(stand)) continue;
                // nazwane stojaki mają swój trwały zapis (/stojak save <nazwa>) -
                // autosave to tylko siatka bezpieczeństwa dla nieukończonych stojaków
                // bez nazwy (np. krzesła, szkice), żeby nie nadpisać dobrego stanu zepsutym
                String customName = stand.getCustomName();
                if (customName != null && !customName.isEmpty()) continue;
                // po restarcie śledzenie części może być puste, choć stojak ma pasażerów -
                // odbuduj je, żeby autosave zapisał stojak RAZEM z częściami
                if (tuner.getParts(stand).isEmpty() && tuner.hasParts(stand)) {
                    tuner.rebuildParts(stand);
                }
                StandPreset p = StandPreset.fromStand(autosaveName(stand), stand, 1.0, tuner.getParts(stand));
                p.dekoracja = tuner.isDecoration(stand); // flaga dekoracji wraca do autosave
                p.stanA = tuner.getStateA(stand); // powiązanie klik-przełącznika wraca do autosave
                p.stanB = tuner.getStateB(stand);
                p.world = stand.getWorld().getName();
                Location l = stand.getLocation();
                p.locX = l.getX();
                p.locY = l.getY();
                p.locZ = l.getZ();
                out.add(p);
            }
        }
        return out;
    }

    /** Nazwa wpisu autosave: custom name albo bez_nazwy_<x>_<y>_<z>. */
    private String autosaveName(ArmorStand stand) {
        String n = stand.getCustomName();
        if (n != null && !n.isEmpty()) return n;
        Location l = stand.getLocation();
        return "bez_nazwy_" + l.getBlockX() + "_" + l.getBlockY() + "_" + l.getBlockZ();
    }

    /**
     * Zapisuje autosave do stojaki/autosave.yml (nadpisuje całość). Wywoływane co minutę
     * z taska w onEnable. Zwraca liczbę zapisanych stojaków albo -1 przy błędzie zapisu.
     */
    public int writeAutosave() {
        List<StandPreset> all = autosaveSnapshot();
        if (all.isEmpty()) return 0;
        File f = autosaveFile();
        if (!f.getParentFile().exists() && !f.getParentFile().mkdirs()) return -1;
        return StandPreset.saveAll(all, f) ? all.size() : -1;
    }

    /** Stojaki z pliku autosave (pusta lista, gdy pliku nie ma). */
    public List<StandPreset> readAutosave() {
        return StandPreset.loadAll(autosaveFile());
    }

    /**
     * Przywraca stojak z autosave: na ORYGINALNEJ pozycji (gdy świat istnieje), a gdy
     * świat zniknął - na bloku, na który gracz patrzy. Zwraca true, gdy postawiono.
     */
    public boolean restoreAutosave(Player player, String name) {
        StandPreset found = null;
        for (StandPreset p : readAutosave()) {
            if (p.name.equalsIgnoreCase(name)) {
                found = p;
                break;
            }
        }
        if (found == null) return false;
        org.bukkit.World world = found.world != null ? Bukkit.getWorld(found.world) : null;
        Location loc;
        if (world != null) {
            // oryginalne miejsce - dokładne współrzędne stóp (bez offsetu i centrowania)
            loc = new Location(world, found.locX, found.locY, found.locZ);
        } else {
            world = player.getWorld();
            Block looked = player.getTargetBlockExact(100);
            loc = (looked != null ? looked.getLocation() : player.getLocation()).add(0.5, 1, 0.5);
        }
        loc.setYaw((float) found.yaw);
        loc.setPitch((float) found.pitch);
        ArmorStand stand = world.spawn(loc, ArmorStand.class);
        found.applyTo(stand);
        tuner.markEditable(stand);
        tuner.initPresetName(stand, found.name); // nazwa presetu w PDC - animacja działa też bez custom name
        for (StandPreset.PartData part : found.parts) {
            tuner.attachPart(stand, part);
        }
        boolean states = found.stanA != null && found.stanB != null;
        if (found.dekoracja) {
            tuner.setDecoration(stand, true); // dekoracja mapy - bez wieżyczki i sprzątania
        } else if (!states) {
            startTurretIfPresent(stand, found);
            if (found.hp > 0) {
                tuner.initTurretHp(stand, found.hp);
                tuner.initTurretStats(stand, found); // obrażenia/zasięg/interwał/efekt z pliku
            }
        }
        if (states) {
            tuner.setStates(stand, found.stanA, found.stanB); // klik-przełącznik wraca
        }
        return true;
    }

    // ===================== szczegóły =====================

    private File presetsFolder() {
        return new File(plugin.getDataFolder(), "stojaki");
    }

    private Location targetLocation(Player player, Block clicked) {
        if (clicked != null) return clicked.getLocation();
        Block looked = player.getTargetBlockExact(100);
        if (looked != null) return looked.getLocation();
        return player.getLocation();
    }

    /** Miejsce, w którym stanąłby stojak (blok + środek + sink + offset, albo dokładne koordynaty). */
    private Location placementLocation(Player player, StandPreset preset, Block clicked, Location exact) {
        Location loc;
        if (exact != null) {
            // x y z = współrzędne BLOKU, na którym ma stanąć stojak (tak samo,
            // jakby patrzeć na ten blok): wyśrodkuj w XZ (inaczej stojak stawałby
            // na rogu czterech bloków), a y = y tego bloku - stopy = y - sink
            loc = exact.clone().add(0.5 + offX, -preset.sink + offY, 0.5 + offZ);
        } else if (preset.hp > 0 && plugin.getConfig().getBoolean("stojak.turret-place-at-aim", true)) {
            // WIEŻYCZKA staje DOKŁADNIE tam, gdzie gracz celuje (nie na środku bloku):
            // X/Z = punkt trafienia celownika (może być ułamkiem bloku, np. krawędź),
            // Y = na wierzchu trafionego bloku (jak dotąd). Gdy ray trace nie trafi -
            // zwykłe centrowanie do środka bloku.
            loc = aimPlacementLocation(player, preset, clicked);
            if (loc == null) {
                Location target = targetLocation(player, clicked);
                loc = target.add(0.5 + offX, -preset.sink + offY, 0.5 + offZ);
            }
        } else {
            Location target = targetLocation(player, clicked);
            loc = target.add(0.5 + offX, -preset.sink + offY, 0.5 + offZ);
        }
        loc.setYaw((float) preset.yaw);
        loc.setPitch((float) preset.pitch);
        return loc;
    }

    /**
     * Dokładny punkt stawiania wieżyczki wg celownika gracza: X/Z = miejsce, w które
     * celuje (może być poza środkiem bloku), Y = wierzch trafionego bloku - sink.
     * Zwraca null, gdy promień celownika niczego nie trafił.
     */
    private Location aimPlacementLocation(Player player, StandPreset preset, Block clicked) {
        try {
            org.bukkit.util.RayTraceResult ray = player.rayTraceBlocks(100);
            if (ray == null || ray.getHitBlock() == null || ray.getHitPosition() == null) return null;
            org.bukkit.util.Vector hit = ray.getHitPosition();
            Block hitBlock = ray.getHitBlock();
            return new Location(hitBlock.getWorld(),
                    hit.getX() + offX,
                    hitBlock.getY() - preset.sink + offY,
                    hit.getZ() + offZ);
        } catch (Throwable ignored) {
            // brak ray trace w API albo problem - wróć do centrowania
            return null;
        }
    }

    // ===================== podgląd-duch =====================

    private void tickPreviews() {
        // posprzątaj podglądy graczy offline / z martwym duchem (usuń też ducha ze świata)
        previews.entrySet().removeIf(e -> {
            Player p = Bukkit.getPlayer(e.getKey());
            boolean stale = p == null || !p.isOnline()
                    || e.getValue().ghost == null || e.getValue().ghost.isDead();
            if (stale) {
                Preview prev = e.getValue();
                if (prev.ghost != null && !prev.ghost.isDead()) {
                    tuner.forgetParts(prev.ghost);
                    prev.ghost.remove();
                }
            }
            return stale;
        });
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            ItemStack item = player.getInventory().getItemInMainHand();
            if (!isSavedItem(item)) {
                if (previews.containsKey(uuid)) removePreview(player);
                continue;
            }
            StandPreset preset = presetFromItem(item);
            if (preset == null) {
                if (previews.containsKey(uuid)) removePreview(player);
                continue;
            }
            Location loc = placementLocation(player, preset, null, null);
            Preview prev = previews.get(uuid);
            if (prev != null) {
                if (Math.abs(prev.x - loc.getX()) < 0.001
                        && Math.abs(prev.y - loc.getY()) < 0.001
                        && Math.abs(prev.z - loc.getZ()) < 0.001) {
                    continue; // już stoi w tym miejscu
                }
                prev.ghost.teleport(loc);
                prev.x = loc.getX();
                prev.y = loc.getY();
                prev.z = loc.getZ();
                continue;
            }
            spawnPreview(player, preset, loc);
        }
    }

    private void spawnPreview(Player player, StandPreset preset, Location loc) {
        // Obiekt śledzenia tworzymy NAJPIERW - jeśli jego klasa się nie załaduje,
        // ducha jeszcze nie ma i nic nie wycieka (kiedyś brak klasy Preview w jarze
        // zostawiał w świecie NIEPILNOWANEGO ducha co 2 ticki, a te się nawarstwiały).
        Preview p = new Preview();
        ArmorStand ghost = null;
        try {
            ghost = loc.getWorld().spawn(loc, ArmorStand.class);
            preset.applyTo(ghost);
            // Wieżyczki (zwykła I wszystkie warianty maga) mają WYŁĄCZONE podświetlenie
            // obrysu na podglądzie - widać tylko ich bloki; pozostałe stojaki świecą
            // jak dotąd, żeby było widać, gdzie wylądują.
            if (!isTurretPreset(preset.name)) {
                ghost.setGlowing(true);
            }
            ghost.setGravity(false);
            ghost.setInvulnerable(true);
            ghost.setCollidable(false);
            ghost.setCanPickupItems(false);
            ghost.setSilent(true);
            ghost.setRemoveWhenFarAway(false);
            for (StandPreset.PartData part : preset.parts) {
                tuner.attachPart(ghost, part);
            }
            p.ghost = ghost;
            p.x = loc.getX();
            p.y = loc.getY();
            p.z = loc.getZ();
            previews.put(player.getUniqueId(), p);
        } catch (Throwable t) {
            // Awaria przy budowaniu ducha: posprzątaj części i usuń ducha, żeby nie
            // został w świecie bez pilnowania (to on narastał przy awarii klasy).
            if (ghost != null && !ghost.isDead()) {
                tuner.forgetParts(ghost);
                ghost.remove();
            }
        }
    }

    /** Czy preset to wieżyczka (zwykła albo którykolwiek wariant maga)?
     *  Wieżyczki nie mają podświetlenia obrysu na duchu podglądu. */
    private static boolean isTurretPreset(String name) {
        if (name == null) return false;
        String n = name.toLowerCase(Locale.ROOT);
        return n.equals("wiezyczka") || n.startsWith("wiezyczka_maga");
    }

    /** Usuwa podgląd-duch gracza (przed postawieniem). */
    public void removePreview(Player player) {
        Preview prev = previews.remove(player.getUniqueId());
        if (prev == null) return;
        if (prev.ghost != null && !prev.ghost.isDead()) {
            tuner.forgetParts(prev.ghost);
            prev.ghost.remove();
        }
    }

    /** Czy ten stojak to podgląd-duch (a nie prawdziwy)? */
    public boolean isPreview(ArmorStand stand) {
        for (Preview p : previews.values()) {
            if (p.ghost == stand) return true;
        }
        return false;
    }

    private void writePreset(PersistentDataContainer pdc, StandPreset p) {
        pdc.set(keySmall, PersistentDataType.BYTE, b(p.small));
        pdc.set(keyArms, PersistentDataType.BYTE, b(p.arms));
        pdc.set(keyVisible, PersistentDataType.BYTE, b(p.visible));
        pdc.set(keyBase, PersistentDataType.BYTE, b(p.base));
        pdc.set(keyHead, PersistentDataType.STRING, p.head);
        pdc.set(keyHand, PersistentDataType.STRING, p.hand);
        pdc.set(keyOffhand, PersistentDataType.STRING, p.offhand);
        pdc.set(keyHX, PersistentDataType.DOUBLE, p.headX);
        pdc.set(keyHY, PersistentDataType.DOUBLE, p.headY);
        pdc.set(keyHZ, PersistentDataType.DOUBLE, p.headZ);
        pdc.set(keyBX, PersistentDataType.DOUBLE, p.bodyX);
        pdc.set(keyBY, PersistentDataType.DOUBLE, p.bodyY);
        pdc.set(keyBZ, PersistentDataType.DOUBLE, p.bodyZ);
        pdc.set(keyRX, PersistentDataType.DOUBLE, p.rightX);
        pdc.set(keyRY, PersistentDataType.DOUBLE, p.rightY);
        pdc.set(keyRZ, PersistentDataType.DOUBLE, p.rightZ);
        pdc.set(keyLX, PersistentDataType.DOUBLE, p.leftX);
        pdc.set(keyLY, PersistentDataType.DOUBLE, p.leftY);
        pdc.set(keyLZ, PersistentDataType.DOUBLE, p.leftZ);
        pdc.set(keyYaw, PersistentDataType.DOUBLE, p.yaw);
        pdc.set(keyPitch, PersistentDataType.DOUBLE, p.pitch);
        pdc.set(keySink, PersistentDataType.DOUBLE, p.sink);
        if (p.dekoracja) pdc.set(keyDekoracja, PersistentDataType.BYTE, (byte) 1);
        if (p.stanA != null) pdc.set(keyStanA, PersistentDataType.STRING, p.stanA);
        if (p.stanB != null) pdc.set(keyStanB, PersistentDataType.STRING, p.stanB);
        if (p.hp > 0) pdc.set(keyHp, PersistentDataType.DOUBLE, p.hp);
        if (!p.parts.isEmpty()) {
            List<String> serialized = new ArrayList<>();
            for (StandPreset.PartData part : p.parts) {
                serialized.add(part.serialize());
            }
            pdc.set(keyParts, PersistentDataType.STRING, String.join(";", serialized));
        }
    }

    private StandPreset readPreset(PersistentDataContainer pdc) {
        if (!pdc.has(keyHead)) return null;
        StandPreset p = new StandPreset();
        p.name = pdc.get(keyName, PersistentDataType.STRING) != null
                ? pdc.get(keyName, PersistentDataType.STRING) : "stojak";
        p.small = bool(pdc.get(keySmall, PersistentDataType.BYTE));
        p.arms = bool(pdc.get(keyArms, PersistentDataType.BYTE));
        p.visible = bool(pdc.get(keyVisible, PersistentDataType.BYTE));
        p.base = bool(pdc.get(keyBase, PersistentDataType.BYTE));
        p.head = str(pdc.get(keyHead, PersistentDataType.STRING), "AIR");
        p.hand = str(pdc.get(keyHand, PersistentDataType.STRING), "AIR");
        p.offhand = str(pdc.get(keyOffhand, PersistentDataType.STRING), "AIR");
        p.headX = dbl(pdc.get(keyHX, PersistentDataType.DOUBLE), 0);
        p.headY = dbl(pdc.get(keyHY, PersistentDataType.DOUBLE), 0);
        p.headZ = dbl(pdc.get(keyHZ, PersistentDataType.DOUBLE), 0);
        p.bodyX = dbl(pdc.get(keyBX, PersistentDataType.DOUBLE), 0);
        p.bodyY = dbl(pdc.get(keyBY, PersistentDataType.DOUBLE), 0);
        p.bodyZ = dbl(pdc.get(keyBZ, PersistentDataType.DOUBLE), 0);
        p.rightX = dbl(pdc.get(keyRX, PersistentDataType.DOUBLE), 0);
        p.rightY = dbl(pdc.get(keyRY, PersistentDataType.DOUBLE), 0);
        p.rightZ = dbl(pdc.get(keyRZ, PersistentDataType.DOUBLE), 0);
        p.leftX = dbl(pdc.get(keyLX, PersistentDataType.DOUBLE), 0);
        p.leftY = dbl(pdc.get(keyLY, PersistentDataType.DOUBLE), 0);
        p.leftZ = dbl(pdc.get(keyLZ, PersistentDataType.DOUBLE), 0);
        p.yaw = dbl(pdc.get(keyYaw, PersistentDataType.DOUBLE), 0);
        p.pitch = dbl(pdc.get(keyPitch, PersistentDataType.DOUBLE), 0);
        p.sink = StandPreset.normalizeSink(dbl(pdc.get(keySink, PersistentDataType.DOUBLE), -1.0));
        p.dekoracja = bool(pdc.get(keyDekoracja, PersistentDataType.BYTE));
        p.stanA = pdc.get(keyStanA, PersistentDataType.STRING);
        p.stanB = pdc.get(keyStanB, PersistentDataType.STRING);
        p.hp = dbl(pdc.get(keyHp, PersistentDataType.DOUBLE), 0);
        String partsStr = pdc.get(keyParts, PersistentDataType.STRING);
        if (partsStr != null) {
            for (String s : partsStr.split(";")) {
                StandPreset.PartData part = StandPreset.PartData.deserialize(s);
                if (part != null) p.parts.add(part);
            }
        }
        return p;
    }

    private static byte b(boolean v) {
        return (byte) (v ? 1 : 0);
    }

    private static boolean bool(Byte v) {
        return v != null && v == 1;
    }

    private static String str(String v, String def) {
        return v != null ? v : def;
    }

    private static double dbl(Double v, double def) {
        return v != null ? v : def;
    }

    private static String sanitize(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
    }
}
