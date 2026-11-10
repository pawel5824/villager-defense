package pl.vd.raft.blocks;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;

/**
 * Wiatrak 2×2×6 bloki:
 *
 *   Widok z góry (y=2, poziom baterii):
 *   z=0: [1] [2] [3] [4]   ← 4 sloty w jednym rzędzie na przodzie
 *         x=0     x=1
 *
 *   Widok z przodu (z=0):
 *   y=6: [WIZ] [WIZ]
 *   y=5: [WIZ] [WIZ]
 *   y=4: [WIZ] [WIZ]
 *   y=3: [WIZ] [WIZ]
 *   y=2: [1][2] [3][4]  ← 4 sloty równo na 2 blokach
 *   y=1: [WIZ] [WIZ]  ← wizualne (podstawa)
 *
 * - BATERIA: slot na baterię (PPM = włóż/wyjmij)
 *   4 sloty w jednym rzędzie na osi X (z=0.5, y=2.5)
 * - WIZUALNY: dekoracyjne bloki (brak interakcji)
 * - Hitbox: 2×2×6 bloków (2 szerokości, 2 długości, 6 wysokości)
 * - Tylko ładowanie baterii (brak GUI/funcji urządzenia)
 */
public class WindmillDevice {

    private final Location baseLocation; // lewy dolny róg
    private final UUID ownerUuid;
    private final DeviceRegistry.DeviceType deviceType;
    private final int rotation; // 0=South, 1=West, 2=North, 3=East

    // Display entities
    private final List<BlockDisplay> visualBlocks = new ArrayList<>();
    private final Interaction[] batterySlots = new Interaction[4];
    private final ItemDisplay[] batteryRenders = new ItemDisplay[4];

    // Stan baterii (per slot)
    private final ItemStack[] batteryItems = new ItemStack[4];
    private final int[] energies = new int[4];
    private final pl.vd.raft.items.BateriaItem[] bateriaItems = new pl.vd.raft.items.BateriaItem[4];

    // Timer ładowania baterii (co 10 sekund +1 ładunek)
    private int chargeTaskId = -1;
    private static final int CHARGE_INTERVAL_TICKS = 200; // 10 sekund = 200 ticków
    private static final int CHARGE_AMOUNT = 1; // ile energii dodaje co interval

    // Wymiary wiatraka
    private static final int DEVICE_WIDTH = 2;   // x
    private static final int DEVICE_DEPTH = 2;   // z
    private static final int DEVICE_HEIGHT = 6;  // y

    // Pozycje slotów na baterie (y=2, 4 sloty w jednym rzędzie na osi X)
    // Równo rozłożone na szerokości 2 bloków, na przodzie (z=0.5)
    // [slotIndex] = {xOffset, y=2.5, z=0.5}
    private static final float[][] BATTERY_OFFSETS = {
        {0.25f, 2.5f, 0.5f},  // slot 0: lewy blok, lewa połówka
        {0.75f, 2.5f, 0.5f},  // slot 1: lewy blok, prawa połówka
        {1.25f, 2.5f, 0.5f},  // slot 2: prawy blok, lewa połówka
        {1.75f, 2.5f, 0.5f},  // slot 3: prawy blok, prawa połówka
    };

    public WindmillDevice(Location baseLocation, UUID ownerUuid, DeviceRegistry.DeviceType deviceType, float yaw) {
        this.baseLocation = baseLocation;
        this.ownerUuid = ownerUuid;
        this.deviceType = deviceType;
        this.rotation = yawToRotation(yaw);
        Arrays.fill(batteryItems, null);
        Arrays.fill(energies, 0);
        Arrays.fill(bateriaItems, null);
    }

    /** Konwertuje yaw gracza na rotację (0-3) */
    private static int yawToRotation(float yaw) {
        float normalized = ((yaw % 360) + 360) % 360;
        if (normalized >= 315 || normalized < 45)  return 0; // South (+Z)
        if (normalized >= 45  && normalized < 135) return 1; // West  (-X)
        if (normalized >= 135 && normalized < 225) return 2; // North (-Z)
        return 3;                                           // East  (+X)
    }

    /** Obraca offset lokalny na światowy na podstawie rotacji */
    private float[] rotateOffset(float dx, float dy, float dz) {
        return switch (rotation) {
            case 0 -> new float[]{dx, dy, dz};           // South
            case 1 -> new float[]{-dz, dy, dx};          // West
            case 2 -> new float[]{-dx, dy, -dz};         // North
            case 3 -> new float[]{dz, dy, -dx};          // East
            default -> new float[]{dx, dy, dz};
        };
    }

    /** Spawnuje cały wiatrak 2×2×6 */
    public void spawn() {
        World world = baseLocation.getWorld();
        if (world == null) return;

        // 1. Spawn bloków wizualnych (cały wiatrak 2×2×6)
        spawnVisualBlocks(world);

        // 2. Sloty na baterie (4 sztuki na y=2)
        spawnBatterySlots(world);

        // 3. Renderowanie baterii (4 sztuki)
        spawnBatteryRenders(world);
    }

    private void spawnVisualBlocks(World world) {
        for (int x = 0; x < DEVICE_WIDTH; x++) {
            for (int z = 0; z < DEVICE_DEPTH; z++) {
                for (int y = 1; y <= DEVICE_HEIGHT; y++) {
                    float[] rot = rotateOffset(x, y, z);
                    Location loc = baseLocation.clone().add(rot[0], rot[1], rot[2]);
                    BlockDisplay display = world.spawn(loc, BlockDisplay.class, d -> {
                        d.setInvulnerable(true);
                        d.setPersistent(false);
                        d.setBlock(Material.BARRIER.createBlockData());
                    });
                    visualBlocks.add(display);
                }
            }
        }
    }

    private void spawnBatterySlots(World world) {
        for (int i = 0; i < 4; i++) {
            final int slotIdx = i;
            float[] off = BATTERY_OFFSETS[slotIdx];
            float[] rot = rotateOffset(off[0], off[1], off[2]);
            Location slotLoc = baseLocation.clone().add(rot[0], rot[1], rot[2]);

            batterySlots[slotIdx] = world.spawn(slotLoc, Interaction.class, inter -> {
                inter.setInteractionWidth(0.4f);
                inter.setInteractionHeight(0.3f);
                inter.setInvulnerable(true);
                inter.setPersistent(false);
                inter.setCustomName("§7[" + (slotIdx + 1) + "]");
                inter.setCustomNameVisible(true);
            });
        }
    }

    private void spawnBatteryRenders(World world) {
        for (int i = 0; i < 4; i++) {
            final int slotIdx = i;
            float[] off = BATTERY_OFFSETS[slotIdx];
            float[] rot = rotateOffset(off[0], off[1], off[2]);
            Location renderLoc = baseLocation.clone().add(rot[0], rot[1], rot[2]);

            batteryRenders[slotIdx] = world.spawn(renderLoc, ItemDisplay.class, d -> {
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                d.setInvulnerable(true);
                d.setPersistent(false);
                d.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(0, 0, 0, 1),
                    new Vector3f(0.4f, 0.4f, 0.4f),
                    new AxisAngle4f(0, 0, 0, 1)
                ));
            });
        }
    }

    // ========== OBSŁUGA KLIKNIĘĆ ==========

    /**
     * PPM na konkretny slot baterii - włożenie/wyjęcie.
     * @param slotIndex indeks slotu (0-3)
     */
    public boolean onBatterySlotClick(Player player, int slotIndex) {
        if (slotIndex < 0 || slotIndex >= 4) return false;

        ItemStack hand = player.getInventory().getItemInMainHand();

        if (batteryItems[slotIndex] == null) {
            // Slot pusty - spróbuj włożyć baterię
            if (isBattery(hand)) {
                batteryItems[slotIndex] = hand.clone();
                batteryItems[slotIndex].setAmount(1);
                hand.setAmount(hand.getAmount() - 1);

                bateriaItems[slotIndex] = pl.vd.raft.items.BateriaItem.fromItemStack(batteryItems[slotIndex]);
                energies[slotIndex] = bateriaItems[slotIndex] != null
                    ? bateriaItems[slotIndex].getCurrentEnergy()
                    : getMaxEnergyForType(batteryItems[slotIndex]);
                updateBatteryDisplay(slotIndex);
                startChargeTimer();

                // Bez komunikatu na czacie (AGENTS.md pkt 1) — celowo.
                return true;
            } else {
                return false;
            }
        } else {
            // Slot zajęty - wyjmij baterię
            if (bateriaItems[slotIndex] != null) {
                bateriaItems[slotIndex].setCurrentEnergy(energies[slotIndex]);
                batteryItems[slotIndex] = bateriaItems[slotIndex].toItemStack();
            }
            ItemStack oldBattery = batteryItems[slotIndex].clone();
            batteryItems[slotIndex] = null;
            bateriaItems[slotIndex] = null;
            energies[slotIndex] = 0;

            if (player.getInventory().firstEmpty() != -1) {
                player.getInventory().addItem(oldBattery);
            } else {
                baseLocation.getWorld().dropItemNaturally(baseLocation, oldBattery);
            }

            updateBatteryDisplay(slotIndex);

            // Sprawdź czy są jeszcze jakieś baterie
            if (!hasAnyBattery()) {
                stopChargeTimer();
            }

            return true;
        }
    }

    /**
     * Kompatybilność wsteczna - automatycznie wybiera slot.
     * Wkładanie: pierwszy pusty slot. Wyjmowanie: ostatni zajęty slot.
     */
    public boolean onBatterySlotClick(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (isBattery(hand)) {
            // Szukaj pustego slotu
            for (int i = 0; i < 4; i++) {
                if (batteryItems[i] == null) {
                    return onBatterySlotClick(player, i);
                }
            }
            // Bez komunikatu na czacie (AGENTS.md pkt 1) — celowo.
            return false;
        } else {
            // Szukaj slotu z baterią (od końca)
            for (int i = 3; i >= 0; i--) {
                if (batteryItems[i] != null) {
                    return onBatterySlotClick(player, i);
                }
            }
            // Bez komunikatu na czacie (AGENTS.md pkt 1) — celowo.
            return false;
        }
    }

    /** Obrót urządzenia (0=South, 1=West, 2=North, 3=East) — zapisywany w devices.yml. */
    public int getRotation() { return rotation; }

    /**
     * Zwraca indeks slotu dla podanego entity UUID.
     * @return indeks slotu (0-3) lub -1 jeśli nie znaleziono
     */
    public int getSlotIndexByUUID(UUID entityUUID) {
        for (int i = 0; i < 4; i++) {
            if (batterySlots[i] != null && batterySlots[i].getUniqueId().equals(entityUUID)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * PPM na blok funkcjonalny - kompatybilność wsteczna.
     * W wiatraku nie ma slotu funkcjonalnego, wyświetla informacje o bateriach.
     */
    public boolean onFunctionalSlotClick(Player player) {
        // PPM na wiatrak nie pokazuje już stanu slotów na czacie (AGENTS.md
        // pkt 1). Stan baterii widać z wizualnych slotów w świecie.
        return true;
    }

    // ========== AKTUALIZACJA WIZUALNA ==========

    private void updateBatteryDisplay(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= 4 || batteryRenders[slotIndex] == null) return;

        if (batteryItems[slotIndex] != null) {
            batteryRenders[slotIndex].setItemStack(batteryItems[slotIndex].clone());
            float ratio = (float) energies[slotIndex] / deviceType.getDefaultMaxEnergy();
            if (ratio > 0.6f) {
                batteryRenders[slotIndex].setViewRange(1.0f);
            } else if (ratio > 0.2f) {
                batteryRenders[slotIndex].setViewRange(0.8f);
            } else {
                batteryRenders[slotIndex].setViewRange(0.5f);
            }
        } else {
            batteryRenders[slotIndex].setItemStack(null);
        }
    }

    // ========== HELPERS ==========

    private boolean isBattery(ItemStack item) {
        return pl.vd.raft.items.BateriaItem.isBateria(item);
    }

    private int getMaxEnergyForType(ItemStack battery) {
        pl.vd.raft.items.BateriaItem.BateriaType type = pl.vd.raft.items.BateriaItem.getTypeFromItem(battery);
        return type != null ? type.getMaxEnergy() : 1000;
    }

    /** Sprawdza czy jakikolwiek slot ma baterię */
    private boolean hasAnyBattery() {
        for (int i = 0; i < 4; i++) {
            if (batteryItems[i] != null) return true;
        }
        return false;
    }

    /** Usuwa cały wiatrak i zatrzymuje timer */
    public void remove() {
        stopChargeTimer();
        for (BlockDisplay display : visualBlocks) {
            if (display != null) display.remove();
        }
        visualBlocks.clear();
        for (int i = 0; i < 4; i++) {
            if (batterySlots[i] != null) batterySlots[i].remove();
            if (batteryRenders[i] != null) batteryRenders[i].remove();
        }
    }

    // ========== TIMER ŁADOWANIA BATERII ==========

    /** Uruchamia timer ładowania baterii */
    private void startChargeTimer() {
        if (chargeTaskId != -1) return;

        org.bukkit.plugin.java.JavaPlugin plugin = findPlugin();
        if (plugin == null) return;

        chargeTaskId = plugin.getServer().getScheduler().runTaskTimer(plugin, this::chargeBatteries,
            CHARGE_INTERVAL_TICKS, CHARGE_INTERVAL_TICKS).getTaskId();
    }

    /** Zatrzymuje timer ładowania */
    private void stopChargeTimer() {
        if (chargeTaskId == -1) return;

        org.bukkit.plugin.java.JavaPlugin plugin = findPlugin();
        if (plugin != null) {
            plugin.getServer().getScheduler().cancelTask(chargeTaskId);
        }
        chargeTaskId = -1;
    }

    /** Ładuje baterie o 1 ładunek */
    private void chargeBatteries() {
        boolean anyCharging = false;

        for (int i = 0; i < 4; i++) {
            if (batteryItems[i] == null) continue;

            int maxEnergy = getMaxEnergyForType(batteryItems[i]);
            if (energies[i] >= maxEnergy) continue;

            energies[i] = Math.min(energies[i] + CHARGE_AMOUNT, maxEnergy);
            anyCharging = true;

            if (bateriaItems[i] != null) {
                bateriaItems[i].setCurrentEnergy(energies[i]);
                batteryItems[i] = bateriaItems[i].toItemStack();
            }
            updateBatteryDisplay(i);
        }

        if (!anyCharging && !hasAnyBattery()) {
            stopChargeTimer();
        }
    }

    private org.bukkit.plugin.java.JavaPlugin findPlugin() {
        for (org.bukkit.plugin.Plugin p : org.bukkit.Bukkit.getPluginManager().getPlugins()) {
            if (p instanceof org.bukkit.plugin.java.JavaPlugin javaPlugin) {
                if (javaPlugin instanceof pl.vd.VillagerDefensePlugin) {
                    return javaPlugin;
                }
            }
        }
        return null;
    }

    // ========== POMOCNICZE (kompatybilność wsteczna) ==========

    /** Wykonuje akcję urządzenia - w wiatraku pokazuje info o bateriach */
    public boolean executeDeviceAction(Player player) {
        return onFunctionalSlotClick(player);
    }

    /** Pobiera UUID wszystkich battery slot entities */
    public Set<UUID> getBatterySlotUUIDs() {
        Set<UUID> uuids = new HashSet<>();
        for (Interaction slot : batterySlots) {
            if (slot != null) uuids.add(slot.getUniqueId());
        }
        return uuids;
    }

    // ========== GETTERS ==========

    public ItemStack getBatteryItem(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= 4) return null;
        return batteryItems[slotIndex];
    }

    public int getEnergy(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= 4) return 0;
        return energies[slotIndex];
    }

    public boolean hasBattery(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= 4) return false;
        return batteryItems[slotIndex] != null;
    }

    public boolean hasAnyBatteryPublic() { return hasAnyBattery(); }

    /**
     * Odtwarza baterię w slocie z persistencji (DevicePersistence) — bez timera,
     * który startuje niżej gdy slot przeszedł z pustego na zajęty.
     */
    public void restoreBattery(int slotIndex, ItemStack battery, int savedEnergy) {
        if (slotIndex < 0 || slotIndex >= 4 || battery == null) return;
        boolean wasEmpty = batteryItems[slotIndex] == null;
        batteryItems[slotIndex] = battery.clone();
        bateriaItems[slotIndex] = pl.vd.raft.items.BateriaItem.fromItemStack(batteryItems[slotIndex]);
        energies[slotIndex] = Math.max(0, savedEnergy);
        updateBatteryDisplay(slotIndex);
        if (wasEmpty) startChargeTimer();
    }

    public Location getBaseLocation() { return baseLocation; }
    public DeviceRegistry.DeviceType getDeviceType() { return deviceType; }
    public UUID getOwnerUuid() { return ownerUuid; }
}
