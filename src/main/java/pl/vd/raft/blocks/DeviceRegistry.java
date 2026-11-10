package pl.vd.raft.blocks;

import org.bukkit.Location;

import java.util.*;
import java.util.logging.Logger;

/**
 * Rejestr typów urządzeń z bateriami.
 * Pozwala rejestrować różne urządzenia (np. "kompresor", "generador", "warsztat").
 */
public class DeviceRegistry {

    // Mapa zarejestrowanych typów: nazwa -> DeviceType
    private final Map<String, DeviceType> registeredDevices = new LinkedHashMap<>();

    // Lista aktywnych urządzeń DeviceBlock2x2 (2×2)
    private final List<DeviceBlock2x2> activeDevices = new ArrayList<>();

    // Lista aktywnych wiatraków WindmillDevice (2×2×6)
    private final List<WindmillDevice> activeWindmills = new ArrayList<>();

    /**
     * Rejestruje nowy typ urządzenia.
     */
    public void register(String name, DeviceType type) {
        registeredDevices.put(name.toLowerCase(), type);
    }

    /**
     * Pobiera typ urządzenia po nazwie.
     */
    public DeviceType getType(String name) {
        return registeredDevices.get(name.toLowerCase());
    }

    /**
     * Lista nazw zarejestrowanych urządzeń.
     */
    public Set<String> getRegisteredNames() {
        return registeredDevices.keySet();
    }

    /**
     * Tworzy i stawia urządzenie o danej nazwie w świecie.
     * Automatycznie wybiera DeviceBlock2x2 lub WindmillDevice na podstawie flagi isWindmill.
     * @param yaw kąt patrzenia gracza (rotacja urządzenia)
     */
    public Object placeDevice(String name, Location location, UUID ownerUuid, float yaw) {
        DeviceType type = getType(name);
        if (type == null) return null;

        if (type.isWindmill()) {
            WindmillDevice device = new WindmillDevice(location, ownerUuid, type, yaw);
            device.spawn();
            activeWindmills.add(device);
            return device;
        } else {
            DeviceBlock2x2 device = new DeviceBlock2x2(location, ownerUuid, type, yaw);
            device.spawn();
            activeDevices.add(device);
            return device;
        }
    }

    /**
     * Zwraca wszystkie aktywne urządzenia DeviceBlock2x2.
     */
    public List<DeviceBlock2x2> getActiveDevices() {
        return new ArrayList<>(activeDevices);
    }

    /**
     * Zwraca wszystkie aktywne wiatraki WindmillDevice.
     */
    public List<WindmillDevice> getActiveWindmills() {
        return new ArrayList<>(activeWindmills);
    }

    /**
     * Zwraca wszystkie aktywne urządzenia danego typu (zarówno DeviceBlock2x2 jak i WindmillDevice).
     * Porównuje po zarejestrowanej nazwie klucza ("modul_odbiorczy", "wiatrak")
     * LUB po displayName ("§bModuł", "§6Wiatrak").
     */
    public List<Object> getActiveDevicesByType(String name) {
        List<Object> result = new ArrayList<>();
        DeviceType targetType = getType(name); // szukaj po kluczu rejestru
        for (DeviceBlock2x2 device : activeDevices) {
            if (device.getDeviceType() == targetType
                || device.getDeviceType().getDisplayName().equalsIgnoreCase(name)) {
                result.add(device);
            }
        }
        for (WindmillDevice windmill : activeWindmills) {
            if (windmill.getDeviceType() == targetType
                || windmill.getDeviceType().getDisplayName().equalsIgnoreCase(name)) {
                result.add(windmill);
            }
        }
        return result;
    }

    /**
     * Usuwa urządzenie DeviceBlock2x2 ze świata i z rejestru.
     */
    public void removeDevice(DeviceBlock2x2 device) {
        device.remove();
        activeDevices.remove(device);
    }

    /**
     * Usuwa wiatrak WindmillDevice ze świata i z rejestru.
     */
    public void removeWindmill(WindmillDevice windmill) {
        windmill.remove();
        activeWindmills.remove(windmill);
    }

    /**
     * Czyści wszystkie urządzenia (np. przy wyłączonym serwerze).
     */
    public void clearAll() {
        for (DeviceBlock2x2 device : activeDevices) {
            device.remove();
        }
        activeDevices.clear();
        for (WindmillDevice windmill : activeWindmills) {
            windmill.remove();
        }
        activeWindmills.clear();
    }

    /**
     * Pobiera logger (potrzebne do logowania informacji o rejestracji).
     */
    public Logger getLogger() {
        return Logger.getLogger("DeviceRegistry");
    }

    /**
     * Definicja typu urządzenia.
     */
    public static class DeviceType {
        private final String displayName;
        private final String description;
        private final int defaultMaxEnergy;
        private final int energyPerUse;
        private final boolean windmill;

        public DeviceType(String displayName, String description, int defaultMaxEnergy, int energyPerUse) {
            this(displayName, description, defaultMaxEnergy, energyPerUse, false);
        }

        public DeviceType(String displayName, String description, int defaultMaxEnergy, int energyPerUse, boolean windmill) {
            this.displayName = displayName;
            this.description = description;
            this.defaultMaxEnergy = defaultMaxEnergy;
            this.energyPerUse = energyPerUse;
            this.windmill = windmill;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
        public int getDefaultMaxEnergy() { return defaultMaxEnergy; }
        public int getEnergyPerUse() { return energyPerUse; }
        public boolean isWindmill() { return windmill; }
    }
}
