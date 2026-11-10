package pl.vd.raft.entity;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.Base64;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Faza 3 — persistencja tratw: zapis/odczyt stanu tratwy gracza do pliku
 * raft-saves.yml w folderze pluginu. Dzięki temu tratwa (rozmiar, materiały
 * desek, maszt/żagiel oraz pozycja na oceanie) przetrwa wyjście z gry i restart
 * serwera — gracz może wrócić później do DOKŁADNIE tej samej tratwy, w tym
 * samym miejscu świata.
 *
 * Momenty zapisu:
 *  - wyjście gracza z gry (RaftListener.onPlayerQuit) — tratwa zostaje na oceanie,
 *  - autosave co 60 s (RaftManager), żeby awaria serwera nie cofała postępu,
 *  - wyłączanie serwera (RaftPlugin.onDisable).
 * Usunięcie zapisu: świadome usunięcie tratwy (/raft remove itd.).
 *
 * Format (wersja 5):
 *   rafts:
 *     <owner-uuid>:
 *       version: 5
 *       raft-id: <uuid>          # ten sam UUID = adopcja kolizji przetrwałych w chunkach
 *       world: RAFT
 *       x/y/z: <pozycja środka>
 *       cells: [ "x;y;z;material;typ;kierunek[;grubość;wysokość][;uszkodzenie]", ... ]
 *                                 # typ: RAFT/FLOOR/WALL/STAIRS/NET
 *                                 # kierunek (schody/ściany): NORTH/...
 *                                 # grubość/wysokość tylko dla WALL (0.125/2 = cienka 2-wysoka)
 *                                 # uszkodzenie (v4): uderzenia siekierą przyjęte przez komórkę,
 *                                 #   pomijane gdy 0 (komórka pełna wytrzymałość)
 *       net-contents:             # (v5) zawartość sieci NET: key komórki -> lista base64 ItemStacków
 *         "x,y,z": [ "<base64>", ... ]
 *       chest-contents:           # (v6) zawartość skrzyń CHEST_18/36/54: key komórki -> lista base64
 *         "x,y,z": [ "<base64>", ... ]
 *       components: [ {name,x,y,z}, ... ]  # pokład/maszt/żagiel z offsetami
 *       supports: [ "x;y;z", ... ]         # podpory pięter (narożniki 4 komórek)
 *       sail-open: <true|false>            # czy żagiel był rozwinięty (napęd z wiatru)
 *       saved-at: <millis>
 * Stare zapisy (4-6 pól na komórkę) są czytane — brak typu = FLOOR,
 * brak grubości/wysokości = pełna komórka (1×1), brak uszkodzenia = 0,
 * brak net-contents = pusta sieć, brak chest-contents = puste skrzynie.
 */
public final class RaftPersistence {

    private static final String FILE_NAME = "raft-saves.yml";
    private static final int VERSION = 6;

    private RaftPersistence() {}

    // ───────────────────────────── PLIK ─────────────────────────────

    private static File file() {
        org.bukkit.plugin.Plugin p = Bukkit.getPluginManager().getPlugin("ServerPlugin");
        if (p == null) return null;
        return new File(p.getDataFolder(), FILE_NAME);
    }

    // ───────────────────────────── ZAPIS ────────────────────────────

    /** Zapisuje stan jednej tratwy (tylko tratwy z właścicielem). */
    public static void save(RaftEntity raft) {
        if (raft == null || raft.getOwner() == null) return;
        File f = file();
        if (f == null) return;
        try {
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(f);
            writeRaft(cfg, raft);
            cfg.save(f);
        } catch (IOException e) {
            warn("Błąd zapisu tratwy " + raft.getRaftId() + ": " + e.getMessage());
        }
    }

    /** Zapisuje stan wszystkich podanych tratw (autosave / wyłączanie serwera). */
    public static void saveAll(Collection<RaftEntity> rafts) {
        if (rafts == null || rafts.isEmpty()) return;
        File f = file();
        if (f == null) return;
        try {
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(f);
            for (RaftEntity raft : rafts) {
                if (raft.getOwner() != null) writeRaft(cfg, raft);
            }
            cfg.save(f);
        } catch (IOException e) {
            warn("Błąd zapisu stanu tratw (saveAll): " + e.getMessage());
        }
    }

    private static void writeRaft(FileConfiguration cfg, RaftEntity raft) {
        String path = "rafts." + raft.getOwner().toString();
        cfg.set(path + ".version", VERSION);
        cfg.set(path + ".raft-id", raft.getRaftId().toString());
        cfg.set(path + ".world", raft.getState().getWorld().getName());

        Vector pos = raft.getState().getPosition();
        cfg.set(path + ".x", round3(pos.getX()));
        cfg.set(path + ".y", round3(pos.getY()));
        cfg.set(path + ".z", round3(pos.getZ()));

        List<String> cellEntries = new ArrayList<>();
        for (Vector offset : raft.getBlockOffsets()) {
            Material mat = raft.getCellMaterial(offset);
            RaftEntity.CellType type = raft.getCellType(offset);
            org.bukkit.block.BlockFace facing = raft.getCellFacing(offset);
            String entry = offset.getX() + ";" + offset.getY() + ";" + offset.getZ() + ";" + mat.name()
                    + ";" + (type == null ? RaftEntity.CellType.FLOOR.name() : type.name())
                    + ";" + (facing == null ? org.bukkit.block.BlockFace.NORTH.name() : facing.name());
            // Grubość/wysokość tylko dla ścian (stare zapisy bez tych pól = pełna komórka)
            if (type == RaftEntity.CellType.WALL) {
                entry += ";" + raft.getCellThickness(offset) + ";" + raft.getCellHeight(offset);
            }
            // Uszkodzenie (uderzenia siekierą) — zapisujemy tylko, gdy > 0,
            // żeby niepęknięte komórki zachowały stary, krótszy format
            int damage = raft.getCellDamage(offset);
            if (damage > 0) {
                entry += ";" + damage;
            }
            cellEntries.add(entry);
        }
        cfg.set(path + ".cells", cellEntries);

        List<String> compEntries = new ArrayList<>();
        for (RaftState.Component comp : raft.getState().getComponents().values()) {
            Vector off = comp.getLocalOffset();
            compEntries.add(comp.getType() + ";" + round3(off.getX()) + ";" + round3(off.getY()) + ";" + round3(off.getZ()));
        }
        cfg.set(path + ".components", compEntries);

        // Żagiel rozwinięty → napęd tratwy z wiatru zamiast z prądu wody
        // (patrz RaftState.isSailing / RaftPhysicsEngine.computeDrive).
        cfg.set(path + ".sail-open", raft.isSailOpen());

        // Podpory pięter (item wooden_support) — węzły siatki trzymane osobno od
        // komórek: podpora stoi na NAROŻNIKU 4 komórek, więc nie jest komórką.
        List<String> supportEntries = new ArrayList<>();
        for (Vector node : raft.getSupports()) {
            supportEntries.add(round3(node.getX()) + ";" + round3(node.getY())
                    + ";" + round3(node.getZ()));
        }
        cfg.set(path + ".supports", supportEntries);

        // Zawartość sieci zbierających (CellType.NET) — base64 ItemStacków
        for (Vector offset : raft.getBlockOffsets()) {
            if (!raft.isNetCell(offset)) continue;
            List<ItemStack> contents = raft.getNetContents(offset);
            if (contents.isEmpty()) continue;
            List<String> enc = new ArrayList<>(contents.size());
            for (ItemStack s : contents) {
                enc.add(Base64.getEncoder().encodeToString(s.serializeAsBytes()));
            }
            cfg.set(path + ".net-contents." + RaftEntity.keyOf(offset), enc);
        }

        // Zawartość skrzyń (CellType.CHEST_18/36/54, v6) — bez tego przedmioty
        // schowane w skrzyni ginęły przy restarcie serwera.
        for (Vector offset : raft.getBlockOffsets()) {
            if (!raft.isChestCell(offset)) continue;
            List<ItemStack> contents = raft.getChestContents(offset);
            if (contents.isEmpty()) continue;
            List<String> enc = new ArrayList<>(contents.size());
            for (ItemStack s : contents) {
                enc.add(Base64.getEncoder().encodeToString(s.serializeAsBytes()));
            }
            cfg.set(path + ".chest-contents." + RaftEntity.keyOf(offset), enc);
        }

        cfg.set(path + ".saved-at", System.currentTimeMillis());
    }

    // ───────────────────────────── ODCZYT ───────────────────────────

    /** Czy gracz ma zapisaną tratwę? */
    public static boolean hasSave(UUID owner) {
        return load(owner) != null;
    }

    /** Pozycja zapisanej tratwy (środek) lub null. */
    public static Location savedLocation(UUID owner) {
        SaveData data = load(owner);
        if (data == null) return null;
        org.bukkit.World world = Bukkit.getWorld(data.world);
        return world == null ? null : new Location(world, data.x, data.y, data.z);
    }

    /** Wczytuje zapis tratwy gracza lub null, gdy nie ma zapisu. */
    public static SaveData load(UUID owner) {
        if (owner == null) return null;
        File f = file();
        if (f == null || !f.exists()) return null;
        try {
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(f);
            String path = "rafts." + owner.toString();
            if (!cfg.contains(path + ".raft-id")) return null;

            SaveData data = new SaveData();
            data.raftId = UUID.fromString(cfg.getString(path + ".raft-id"));
            data.world = cfg.getString(path + ".world", "RAFT");
            data.x = cfg.getDouble(path + ".x");
            data.y = cfg.getDouble(path + ".y");
            data.z = cfg.getDouble(path + ".z");
            data.savedAt = cfg.getLong(path + ".saved-at", 0L);

            for (String entry : cfg.getStringList(path + ".cells")) {
                String[] parts = entry.split(";");
                if (parts.length < 4) continue;
                try {
                    data.cells.add(new Cell(Double.parseDouble(parts[0]),
                            Double.parseDouble(parts[1]),
                            Double.parseDouble(parts[2]),
                            parts[3],
                            parts.length >= 5 ? parts[4] : null,
                            parts.length >= 6 ? parts[5] : null,
                            parts.length >= 7 ? parts[6] : null,
                            parts.length >= 8 ? parts[7] : null,
                            parts.length >= 9 ? parts[8] : null));
                } catch (NumberFormatException ignored) {
                }
            }
            for (String entry : cfg.getStringList(path + ".components")) {
                String[] parts = entry.split(";");
                if (parts.length != 4) continue;
                try {
                    data.components.add(new ComponentData(parts[0],
                            Double.parseDouble(parts[1]),
                            Double.parseDouble(parts[2]),
                            Double.parseDouble(parts[3])));
                } catch (NumberFormatException ignored) {
                }
            }
            // Podpory pięter (brak sekcji = brak podpór — stare zapisy)
            for (String entry : cfg.getStringList(path + ".supports")) {
                String[] parts = entry.split(";");
                if (parts.length != 3) continue;
                try {
                    data.supports.add(new Vector(Double.parseDouble(parts[0]),
                            Double.parseDouble(parts[1]),
                            Double.parseDouble(parts[2])));
                } catch (NumberFormatException ignored) {
                }
            }

            // Żagiel rozwinięty (brak pola = zwinięty) — stan napędu tratwy
            data.sailOpen = cfg.getBoolean(path + ".sail-open", false);

            // Zawartość sieci (v5) — sekcja net-contents.<key> = lista base64
            org.bukkit.configuration.ConfigurationSection nets =
                    cfg.getConfigurationSection(path + ".net-contents");
            if (nets != null) {
                for (String key : nets.getKeys(false)) {
                    List<String> list = cfg.getStringList(path + ".net-contents." + key);
                    if (!list.isEmpty()) data.netContents.put(key, list);
                }
            }
            // Zawartość skrzyń (v6) — sekcja chest-contents.<key> = lista base64
            org.bukkit.configuration.ConfigurationSection chests =
                    cfg.getConfigurationSection(path + ".chest-contents");
            if (chests != null) {
                for (String key : chests.getKeys(false)) {
                    List<String> list = cfg.getStringList(path + ".chest-contents." + key);
                    if (!list.isEmpty()) data.chestContents.put(key, list);
                }
            }
            return data;
        } catch (Exception e) {
            warn("Błąd odczytu zapisu tratwy " + owner + ": " + e.getMessage());
            return null;
        }
    }

    /** Usuwa zapis tratwy gracza (świadome usunięcie tratwy). */
    public static void delete(UUID owner) {
        if (owner == null) return;
        File f = file();
        if (f == null || !f.exists()) return;
        try {
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(f);
            cfg.set("rafts." + owner.toString(), null);
            cfg.save(f);
        } catch (IOException e) {
            warn("Błąd usuwania zapisu tratwy " + owner + ": " + e.getMessage());
        }
    }

    // ───────────────────────────── POMOCNICZE ───────────────────────

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    private static void warn(String msg) {
        org.bukkit.Bukkit.getLogger().warning("[Raft] " + msg);
    }

    // ───────────────────────────── DTO ──────────────────────────────

    /** Pełny zapis tratwy gracza. */
    public static final class SaveData {
        public UUID raftId;
        public String world = "RAFT";
        public double x, y, z;
        public long savedAt;
        public final List<Cell> cells = new ArrayList<>();
        public final List<ComponentData> components = new ArrayList<>();
        /** Podpory pięter (węzły narożne) — brak = stary zapis bez podpór. */
        public final List<Vector> supports = new ArrayList<>();
        /** Czy żagiel był rozwinięty (napęd z wiatru) w chwili zapisu. */
        public boolean sailOpen;
        /** Zawartość sieci (v5): key komórki -> lista base64 ItemStacków. */
        public final Map<String, List<String>> netContents = new HashMap<>();
        /** Zawartość skrzyń (v6): key komórki -> lista base64 ItemStacków. */
        public final Map<String, List<String>> chestContents = new HashMap<>();
    }

    /**
     * Komórka tratwy: offset względem środka + materiał + typ (RAFT/FLOOR/WALL/
     * STAIRS) + kierunek (schody/ściany) + grubość/wysokość (tylko ściany)
     * + uszkodzenie (uderzenia siekierą). type/facing/thickness/height/damage
     * mogą być null (stare zapisy).
     */
    public static final class Cell {
        public final double x, y, z;
        public final String material;
        public final String type;
        public final String facing;
        public final String thickness;
        public final String height;
        public final String damage;

        Cell(double x, double y, double z, String material, String type, String facing,
             String thickness, String height, String damage) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.material = material;
            this.type = type;
            this.facing = facing;
            this.thickness = thickness;
            this.height = height;
            this.damage = damage;
        }
    }

    /** Komponent tratwy (pokład/maszt/żagiel) z offsetem lokalnym. */
    public static final class ComponentData {
        public final String name;
        public final double x, y, z;

        ComponentData(String name, double x, double y, double z) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
