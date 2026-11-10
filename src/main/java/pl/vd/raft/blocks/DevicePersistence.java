package pl.vd.raft.blocks;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.Base64;
import java.util.UUID;

/**
 * Faza 0 projektu anten (docs/ANTENY_ODBIORNIK.md) — persistencja urządzeń.
 *
 * Urządzenia (odbiornik 2×2, wiatrak 2×2×6) żyły dotąd wyłącznie w pamięci
 * {@link DeviceRegistry}: po restarcie serwera znikały, mimo że były niezniszczalne
 * i stawiane z myślą o trwałym świata. Ten plik naprawia to tym samym wzorcem,
 * co RaftPersistence (raft-saves.yml):
 *
 *  - plik: plugins/ServerPlugin/devices.yml
 *  - zapis: pełny snapshot wszystkich urządzeń,
 *    * wyłączanie serwera (RaftPlugin.onDisable),
 *    * autosave co 1200 ticków = 60 s (jak autosave tratw w RaftManager),
 *    * przy usuwaniu urządzenia (/urzadzenie usun) — żeby restart nie wskrzesił
 *      świadomie usuniętego urządzenia.
 *  - odczyt: przy starcie pluginu (RaftPlugin.onEnable, po rejestracji typów),
 *    z odtworzeniem baterii (base64 ItemStack.serializeAsBytes — ten sam format
 *    co net-contents w raft-saves.yml) i poziomu energii.
 *
 * Format (wersja 1):
 *   devices:
 *     <index>:
 *       version: 1
 *       type: modul_odbiorczy | wiatrak        # klucz z DeviceRegistry
 *       world: RAFT *      x/y/z: <int>                           # lewy dolny róg (baseLocation)
 *      rotation: <0-3>                        # (v2) obrót urządzenia: 0=South 1=West 2=North 3=East
 *      yaw: <float>                           # rotation * 90 (zgodność ze starymi zapisami)
 *       battery: <base64|pusty>                # tylko odbiornik (1 slot)
 *       energy: <int>                          # tylko odbiornik
 *       batteries: [ "<base64>", ... ]         # tylko wiatrak, 4 wpisy (puste = "")
 *       energies: [ <int>, <int>, <int>, <int> ]
 *
 * Uwaga o rotacji: konstruktory urządzeń przyjmują yaw gracza i same redukują go
 * do rotacji 0-3, więc zapisujemy rotację (a obok yaw = rotation * 90, żeby plik
 * był czytelny i zgodny ze starym formatem). Wersja 1 zawsze zapisywała yaw = 0,
 * więc po restarcie urządzenia stawały obrócone na South (0) — teraz obrót
 * przeżywa restart (stare zapisy bez `rotation` czytają po prostu swój yaw,
 * czyli tak jak dotąd 0).
 * Nie zapisujemy UUID encji display: encje mają setPersistent(false) i po
 * restarcie powstają od zera w spawn().
 */
public final class DevicePersistence {

    private static final String FILE_NAME = "devices.yml";
    private static final int VERSION = 2;

    private DevicePersistence() {}

    // ───────────────────────────── PLIK ─────────────────────────────

    private static File file() {
        org.bukkit.plugin.Plugin p = Bukkit.getPluginManager().getPlugin("ServerPlugin");
        if (p == null) return null;
        return new File(p.getDataFolder(), FILE_NAME);
    }

    // ───────────────────────────── ZAPIS ────────────────────────────

    /** Zapisuje wszystkie aktywne urządzenia z rejestru (snapshot) + anteny (faza 2). */
    public static void saveAll(DeviceRegistry registry) {
        if (registry == null) return;
        File f = file();
        if (f == null) return;
        try {
            FileConfiguration cfg = new YamlConfiguration();
            int index = 0;
            for (DeviceBlock2x2 device : registry.getActiveDevices()) {
                writeDevice(cfg, "devices." + index + ".", device);
                index++;
            }
            for (WindmillDevice windmill : registry.getActiveWindmills()) {
                writeWindmill(cfg, "devices." + index + ".", windmill);
                index++;
            }
            // Faza 2: anteny — pozycja + link na odbiornik (pozycja nośnika).
            pl.vd.raft.RaftPlugin raft = pl.vd.raft.RaftPlugin.getInstance();
            if (raft != null && raft.getAntennaManager() != null) {
                for (AntennaDevice antenna : raft.getAntennaManager().getAntennas()) {
                    writeAntenna(cfg, "devices." + index + ".", antenna);
                    index++;
                }
            }
            cfg.save(f);
        } catch (IOException e) {
            warn("Błąd zapisu urządzeń (devices.yml): " + e.getMessage());
        }
    }

    private static void writeAntenna(FileConfiguration cfg, String path, AntennaDevice antenna) {
        Location base = antenna.getBaseLocation();
        cfg.set(path + "version", VERSION);
        cfg.set(path + "type", "antena");
        cfg.set(path + "world", base.getWorld() != null ? base.getWorld().getName() : "RAFT");
        cfg.set(path + "x", base.getBlockX());
        cfg.set(path + "y", base.getBlockY());
        cfg.set(path + "z", base.getBlockZ());
        DeviceBlock2x2 link = antenna.getLinkedReceiver();
        if (link != null) {
            Location l = link.getBaseLocation();
            cfg.set(path + "link", l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ());
        }
    }

    private static void writeDevice(FileConfiguration cfg, String path, DeviceBlock2x2 device) {
        Location base = device.getBaseLocation();
        cfg.set(path + "version", VERSION);
        cfg.set(path + "type", registryKeyOf(device));
        cfg.set(path + "world", base.getWorld() != null ? base.getWorld().getName() : "RAFT");
        cfg.set(path + "x", base.getBlockX());
        cfg.set(path + "y", base.getBlockY());
        cfg.set(path + "z", base.getBlockZ());
        writeRotation(cfg, path, device.getRotation());

        ItemStack battery = device.getBatteryItem();
        if (battery != null) {
            cfg.set(path + "battery", Base64.getEncoder().encodeToString(battery.serializeAsBytes()));
            cfg.set(path + "energy", device.getEnergy());
        } else {
            cfg.set(path + "battery", "");
            cfg.set(path + "energy", 0);
        }
    }

    private static void writeWindmill(FileConfiguration cfg, String path, WindmillDevice windmill) {
        Location base = windmill.getBaseLocation();
        cfg.set(path + "version", VERSION);
        cfg.set(path + "type", registryKeyOf(windmill));
        cfg.set(path + "world", base.getWorld() != null ? base.getWorld().getName() : "RAFT");
        cfg.set(path + "x", base.getBlockX());
        cfg.set(path + "y", base.getBlockY());
        cfg.set(path + "z", base.getBlockZ());
        writeRotation(cfg, path, windmill.getRotation());

        java.util.List<String> b64 = new java.util.ArrayList<>(4);
        java.util.List<Integer> energy = new java.util.ArrayList<>(4);
        for (int i = 0; i < 4; i++) {
            ItemStack b = windmill.getBatteryItem(i);
            b64.add(b == null ? "" : Base64.getEncoder().encodeToString(b.serializeAsBytes()));
            energy.add(windmill.getEnergy(i));
        }
        cfg.set(path + "batteries", b64);
        cfg.set(path + "energies", energy);
    }

    /**
     * Zapisuje obrót urządzenia: `rotation` (0-3, prawdziwa wartość) + `yaw`
     * (rotation * 90) dla czytelności i zgodności ze starym formatem.
     */
    private static void writeRotation(FileConfiguration cfg, String path, int rotation) {
        cfg.set(path + "rotation", rotation);
        cfg.set(path + "yaw", rotation * 90f);
    }

    /**
     * Yaw do odtworzenia urządzenia: nowe zapisy mają `rotation` (0-3), stare
     * tylko `yaw` (zapis wersji 1 trzymał tam zawsze 0 → South, jak dotąd).
     */
    private static float yawFor(FileConfiguration cfg, String path) {
        if (cfg.contains(path + "rotation")) {
            return cfg.getInt(path + "rotation", 0) * 90f;
        }
        return (float) cfg.getDouble(path + "yaw", 0.0);
    }

    /**
     * Klucz rejestru dla urządzenia — potrzebny, żeby przy odczycie trafić z powrotem
     * na ten sam DeviceType (nazwa displayName zawiera kody kolorów, więc nie nadaje
     * się jako klucz). Szukamy po identyczności instancji typu.
     */
    private static String registryKeyOf(DeviceBlock2x2 device) {
        return keyOfType(device.getDeviceType());
    }

    private static String registryKeyOf(WindmillDevice device) {
        return keyOfType(device.getDeviceType());
    }

    private static String keyOfType(DeviceRegistry.DeviceType type) {
        DeviceRegistry registry = raftPluginRegistry();
        if (registry != null) {
            for (String name : registry.getRegisteredNames()) {
                if (registry.getType(name) == type) return name;
            }
        }
        // Fallback: nie powinno się zdarzyć (typy są rejestrowane tylko w RaftPlugin),
        // ale bez klucza urządzenie i tak się nie odtworzy — zapisujemy coś czytelnego.
        return type.getDisplayName();
    }

    private static DeviceRegistry raftPluginRegistry() {
        pl.vd.raft.RaftPlugin raft = pl.vd.raft.RaftPlugin.getInstance();
        return raft != null ? raft.getDeviceRegistry() : null;
    }

    // ───────────────────────────── ODCZYT ───────────────────────────

    /**
     * Odtwarza urządzenia z devices.yml do rejestru. Idempotentne wywołanie
     * przy starcie — urządzenia nie istnieją jeszcze w pamięci.
     */
    public static void loadAll(DeviceRegistry registry) {
        if (registry == null) return;
        File f = file();
        if (f == null || !f.exists()) return;

        FileConfiguration cfg = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection devices = cfg.getConfigurationSection("devices");
        if (devices == null) return;

        int restored = 0;
        int skipped = 0;
        java.util.List<Runnable> linkJobs = new java.util.ArrayList<>(); // faza 2: re-link po wczytaniu wszystkiego
        for (String key : devices.getKeys(false)) {
            String path = "devices." + key + ".";
            try {
                String typeName = cfg.getString(path + "type", "");
                DeviceRegistry.DeviceType type = registry.getType(typeName);
                if (type == null && !"antena".equals(typeName)) {
                    skipped++;
                    continue;
                }

                World world = Bukkit.getWorld(cfg.getString(path + "world", "RAFT"));
                if (world == null) {
                    skipped++;
                    continue;
                }

                Location base = new Location(world,
                        cfg.getInt(path + "x"),
                        cfg.getInt(path + "y"),
                        cfg.getInt(path + "z"));

                boolean isWindmill = cfg.contains(path + "batteries");
                boolean isAntenna = "antena".equals(typeName);

                // Unikaj duplikatów (np. po ręcznym zgraniu pliku): jedno urządzenie na pozycję.
                if (isOccupied(registry, base) || isAntennaOccupied(base)) {
                    skipped++;
                    continue;
                }

                if (isAntenna) {
                    // Faza 2: antena — spawn teraz, link po wczytaniu wszystkich
                    // (odbiornik w pliku może być dalszym wpisem niż antena).
                    AntennaDevice antenna = new AntennaDevice(base, null);
                    antenna.spawn();
                    pl.vd.raft.RaftPlugin raft = pl.vd.raft.RaftPlugin.getInstance();
                    if (raft != null && raft.getAntennaManager() != null) {
                        raft.getAntennaManager().registerExisting(antenna);
                    }
                    String link = cfg.getString(path + "link", "");
                    if (link != null && !link.isEmpty()) {
                        linkJobs.add(() -> {
                            String[] parts = link.split(",");
                            if (parts.length != 3) return;
                            try {
                                Location linkBase = new Location(world,
                                        Integer.parseInt(parts[0].trim()),
                                        Integer.parseInt(parts[1].trim()),
                                        Integer.parseInt(parts[2].trim()));
                                DeviceBlock2x2 receiver = findDeviceAt(registry, linkBase);
                                if (receiver != null) antenna.setLinkedReceiver(receiver);
                            } catch (NumberFormatException ignored) {
                            }
                        });
                    }
                    restored++;
                    continue;
                }

                Object device = registry.placeDevice(typeName, base, null, yawFor(cfg, path));
                if (device == null) {
                    skipped++;
                    continue;
                }

                if (isWindmill) {
                    WindmillDevice windmill = (WindmillDevice) device;
                    java.util.List<String> b64 = cfg.getStringList(path + "batteries");
                    java.util.List<Integer> energy = cfg.getIntegerList(path + "energies");
                    for (int i = 0; i < 4 && i < b64.size(); i++) {
                        ItemStack battery = deserialize(b64.get(i));
                        if (battery != null) {
                            windmill.restoreBattery(i, battery,
                                    i < energy.size() ? energy.get(i) : 0);
                        }
                    }
                    // Timer ładowania rusza tylko, gdy jest jakaś bateria (restoreBattery go startuje).
                } else {
                    DeviceBlock2x2 block = (DeviceBlock2x2) device;
                    ItemStack battery = deserialize(cfg.getString(path + "battery", ""));
                    if (battery != null) {
                        block.restoreBattery(battery, cfg.getInt(path + "energy", 0));
                    }
                }
                restored++;
            } catch (Exception e) {
                skipped++;
                warn("Pominięto urządzenie '" + key + "': " + e.getMessage());
            }
        }

        // Faza 2: linki anten dopiero teraz — odbiornik mógł być dalszym wpisem
        // w pliku niż antena, więc wszystkie urządzenia są już w rejestrze.
        for (Runnable job : linkJobs) {
            try {
                job.run();
            } catch (Exception e) {
                warn("Nie udało się odtworzyć linku anteny: " + e.getMessage());
            }
        }

        if (restored > 0 || skipped > 0) {
            String msg = "[Raft] Odtworzono urządzenia z devices.yml: " + restored;
            if (skipped > 0) msg += " (pominięto: " + skipped + ")";
            Bukkit.getLogger().info(msg);
        }
    }

    /** Czy na pozycji stoi już urządzenie (odbicie nośników 2×2)? */
    private static boolean isOccupied(DeviceRegistry registry, Location base) {
        for (DeviceBlock2x2 d : registry.getActiveDevices()) {
            Location other = d.getBaseLocation();
            if (other.getWorld() == base.getWorld()
                    && other.getBlockX() == base.getBlockX()
                    && other.getBlockY() == base.getBlockY()
                    && other.getBlockZ() == base.getBlockZ()) return true;
        }
        for (WindmillDevice d : registry.getActiveWindmills()) {
            Location other = d.getBaseLocation();
            if (other.getWorld() == base.getWorld()
                    && other.getBlockX() == base.getBlockX()
                    && other.getBlockY() == base.getBlockY()
                    && other.getBlockZ() == base.getBlockZ()) return true;
        }
        return false;
    }

    /** Czy na pozycji stoi już antena (faza 2 — anteny trzyma AntennaManager, nie rejestr 2×2). */
    private static boolean isAntennaOccupied(Location base) {
        pl.vd.raft.RaftPlugin raft = pl.vd.raft.RaftPlugin.getInstance();
        if (raft == null || raft.getAntennaManager() == null) return false;
        for (AntennaDevice antenna : raft.getAntennaManager().getAntennas()) {
            Location other = antenna.getBaseLocation();
            if (other.getWorld() == base.getWorld()
                    && other.getBlockX() == base.getBlockX()
                    && other.getBlockY() == base.getBlockY()
                    && other.getBlockZ() == base.getBlockZ()) return true;
        }
        return false;
    }

    /** Urządzenie 2×2, którego baza stoi dokładnie na podanej pozycji (dla linku anteny). */
    private static DeviceBlock2x2 findDeviceAt(DeviceRegistry registry, Location base) {
        for (DeviceBlock2x2 device : registry.getActiveDevices()) {
            Location other = device.getBaseLocation();
            if (other.getWorld() == base.getWorld()
                    && other.getBlockX() == base.getBlockX()
                    && other.getBlockY() == base.getBlockY()
                    && other.getBlockZ() == base.getBlockZ()) return device;
        }
        return null;
    }

    private static ItemStack deserialize(String b64) {
        if (b64 == null || b64.isEmpty()) return null;
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(b64));
        } catch (Exception e) {
            return null;
        }
    }

    private static void warn(String message) {
        Bukkit.getLogger().warning("[Raft] " + message);
    }
}
