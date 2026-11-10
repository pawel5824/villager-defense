package pl.vd.raft.blocks;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * Urządzenie 2×2 bloki:
 * 
 *   [BATERIA]  [FUNKCJA]
 *   [WIZUALNY] [WIZUALNY]
 * 
 * - BATERIA: slot na baterię (PPM = włóż/wyjmij)
 * - FUNKCJA: blok funkcjonalny (PPM = użyj urządzenia)
 * - WIZUALNY: dekoracyjne bloki (brak interakcji)
 */
public class DeviceBlock2x2 {

    private final Location baseLocation; // lewy dolny róg
    private final UUID ownerUuid;
    private final DeviceRegistry.DeviceType deviceType;
    private final int rotation; // 0=South, 1=West, 2=North, 3=East

    // Display entities
    private BlockDisplay[][] displays = new BlockDisplay[2][2]; // [x][y]
    private Interaction batterySlot;
    private ItemDisplay batteryRender;
    private Interaction functionalSlot;

    // Stan baterii
    private ItemStack batteryItem = null;
    private int energy = 0;
    private pl.vd.raft.items.BateriaItem bateriaItem = null;

    // Faza 2 projektu anten: podłączone anteny (max 3 — decyzja właściciela)
    private final java.util.List<pl.vd.raft.blocks.AntennaDevice> linkedAntennas = new java.util.ArrayList<>();
    public static final int MAX_ANTENNAS = 3;
    
    // Timer degradacji baterii (co 10 sekund - 1 ładunek)
    private int decayTaskId = -1;
    private static final int DECAY_INTERVAL_TICKS = 200; // 10 sekund = 200 ticków
    private static final int DECAY_AMOUNT = 1; // ile energii zużywa co interval

    // Offsets bloków (względem baseLocation)
    // [x][y] -> {dx, dy, dz}
    private static final float[][][] OFFSETS = {
        {{0, 2, 0}, {0, 3, 0}},  // x=0 (lewy): dolny=2, górny=3
        {{1, 2, 0}, {1, 3, 0}}   // x=1 (prawy): dolny=2, górny=3
    };

    // Offset slotu baterii (lewy dolny róg)
    private static final float BATTERY_SLOT_X = 0.5f;
    private static final float BATTERY_SLOT_Y = 2.5f;
    private static final float BATTERY_SLOT_Z = 0.5f;

    // Offset bloku funkcjonalnego (prawy dolny róg)
    private static final float FUNC_SLOT_X = 1.5f;
    private static final float FUNC_SLOT_Y = 2.5f;
    private static final float FUNC_SLOT_Z = 0.5f;

    public DeviceBlock2x2(Location baseLocation, UUID ownerUuid, DeviceRegistry.DeviceType deviceType, float yaw) {
        this.baseLocation = baseLocation;
        this.ownerUuid = ownerUuid;
        this.deviceType = deviceType;
        this.rotation = yawToRotation(yaw);
    }

    /** Konwertuje yaw gracza na rotację (0-3) */
    private static int yawToRotation(float yaw) {
        // Normalizuj yaw do 0-360
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

    /** Spawnuje cały blok 2×2 z slotem na baterię */
    public void spawn() {
        World world = baseLocation.getWorld();
        if (world == null) return;

        // Faza 1 projektu anten: blok-nośnik (niewidzialna beczka facing=down,
        // open=true) — bez niego klient z modem nie renderuje tu urządzenia,
        // bo BER woła się tylko dla pozycji z BlockEntity.
        pl.vd.raft.net.RaftReceiversPacketSender.placeCarrierBlock(baseLocation);

        // 1. Spawn 4 BlockDisplay (wizualne bloki)
        spawnVisualBlocks(world);

        // 2. Slot na baterię (lewy górny)
        spawnBatterySlot(world);

        // 3. Blok funkcjonalny (prawy górny)
        spawnFunctionalSlot(world);

        // 4. Renderowanie baterii
        spawnBatteryRender(world);
    }

    private void spawnVisualBlocks(World world) {
        for (int x = 0; x < 2; x++) {
            for (int y = 0; y < 2; y++) {
                float[] rot = rotateOffset(OFFSETS[x][y][0], OFFSETS[x][y][1], OFFSETS[x][y][2]);
                Location loc = baseLocation.clone().add(rot[0], rot[1], rot[2]);

                displays[x][y] = world.spawn(loc, BlockDisplay.class, d -> {
                    d.setInvulnerable(true);
                    d.setPersistent(false);
                    d.setBlock(Material.BARRIER.createBlockData());
                });
            }
        }
    }

    private void spawnBatterySlot(World world) {
        float[] rot = rotateOffset(BATTERY_SLOT_X, BATTERY_SLOT_Y, BATTERY_SLOT_Z);
        Location slotLoc = baseLocation.clone().add(rot[0], rot[1], rot[2]);

        batterySlot = world.spawn(slotLoc, Interaction.class, i -> {
            i.setInteractionWidth(0.4f);
            i.setInteractionHeight(0.3f);
            i.setInvulnerable(true);
            i.setPersistent(false);
            i.setCustomName("§7[1]");
            i.setCustomNameVisible(true);
        });
    }

    private void spawnFunctionalSlot(World world) {
        float[] rot = rotateOffset(FUNC_SLOT_X, FUNC_SLOT_Y, FUNC_SLOT_Z);
        Location funcLoc = baseLocation.clone().add(rot[0], rot[1], rot[2]);

        functionalSlot = world.spawn(funcLoc, Interaction.class, i -> {
            i.setInteractionWidth(0.4f);
            i.setInteractionHeight(0.3f);
            i.setInvulnerable(true);
            i.setPersistent(false);
            i.setCustomName("§7[" + deviceType.getDisplayName() + "]");
            i.setCustomNameVisible(true);
        });
    }

    private void spawnBatteryRender(World world) {
        float[] rot = rotateOffset(BATTERY_SLOT_X, BATTERY_SLOT_Y, BATTERY_SLOT_Z);
        Location renderLoc = baseLocation.clone().add(rot[0], rot[1], rot[2]);

        batteryRender = world.spawn(renderLoc, ItemDisplay.class, d -> {
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

    // ========== OBSŁUGA KLIKNIĘĆ ==========

    /** PPM na slot baterii - włożenie/wyjęcie */
    public boolean onBatterySlotClick(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();

        if (batteryItem == null) {
            // Slot pusty - spróbuj włożyć baterię
            if (isBattery(hand)) {
                batteryItem = hand.clone();
                batteryItem.setAmount(1);
                hand.setAmount(hand.getAmount() - 1);
                
                bateriaItem = pl.vd.raft.items.BateriaItem.fromItemStack(batteryItem);
                energy = bateriaItem != null ? bateriaItem.getCurrentEnergy() : getMaxEnergyForType(batteryItem);
                updateBatteryDisplay();
                startDecayTimer(); // Uruchom timer degradacji

                // Bez komunikatu na czacie (AGENTS.md pkt 1) — celowo.
                return true;
            } else {
                return false;
            }
        } else {
            // Slot zajęty - wyjmij baterię
            stopDecayTimer(); // Zatrzymaj timer
            
            // Zapisz aktualny stan energii w itemie przed wyjęciem
            if (bateriaItem != null) {
                bateriaItem.setCurrentEnergy(energy);
                batteryItem = bateriaItem.toItemStack();
            }
            ItemStack oldBattery = batteryItem.clone();
            batteryItem = null;
            bateriaItem = null;
            energy = 0;

            if (player.getInventory().firstEmpty() != -1) {
                player.getInventory().addItem(oldBattery);
            } else {
                baseLocation.getWorld().dropItemNaturally(baseLocation, oldBattery);
            }

            updateBatteryDisplay();
            return true;
        }
    }

    /** PPM na blok funkcjonalny - otwiera GUI urządzenia */
    public boolean onFunctionalSlotClick(Player player) {
        if (energy <= 0 && batteryItem == null) {
            return false;
        }
        
        // Otwórz GUI urządzenia
        pl.vd.raft.RaftPlugin raftPlugin = pl.vd.raft.RaftPlugin.getInstance();
        if (raftPlugin != null) {
            DeviceGUI gui = raftPlugin.getDeviceGUI();
            if (gui != null) {
                gui.openGUI(player, this);
                return true;
            }
        }
        
        // Fallback jeśli brak GUI — bez komunikatu (zasada "wszędzie cicho", patrz AGENTS.md)
        return false;
    }
    
    /**
     * Wykonuje akcję urządzenia (z zużyciem energii).
     * Wywoływane z GUI po kliknięciu w konkretny przycisk.
     */
    public boolean executeDeviceAction(Player player) {
        if (energy <= 0) {

            return false;
        }
        
        // Zużycie energii
        energy -= deviceType.getEnergyPerUse();
        
        if (energy <= 0) {
            batteryItem = null;
            bateriaItem = null;
            energy = 0;
            stopDecayTimer();
        } else if (bateriaItem != null) {
            bateriaItem.setCurrentEnergy(energy);
            batteryItem = bateriaItem.toItemStack();
        }
        
        // Wywołaj logikę urządzenia
        onDeviceUse(player);
        
        updateBatteryDisplay();
        return true;
    }

    /**
     * Logika urządzenia - wywoływana przy każdym użyciu.
     * Sprawdza typ urządzenia i wywołuje odpowiednią akcję.
     */
    private void onDeviceUse(Player player) {
        // Bez komunikatu na czacie (AGENTS.md pkt 1) — celowo. To jest miejsce,
        // w które wejdzie HUD moda, gdy użycie urządzenia ma dać widoczny efekt.
        if (deviceType.getDisplayName().contains("Odbiorczy")) {
            onReceiverModuleUse(player);
        }
    }
    
    /**
     * Logika Modułu Odbiorczego — pierwotnie wypisywała w czacie mini-mapę z
     * odległościami do wysp. Zgodnie z zasadą „żadnych komunikatów" (AGENTS.md
     * pkt 1) raport został usunięty, więc metoda nie ma już żadnego wyjścia.
     * Jeśli odbiornik ma znów coś pokazywać, to przez HUD moda (pakiet), a nie
     * przez czat — starego kodu nie ma po co wskrzeszać.
     */
    private void onReceiverModuleUse(Player player) {
        // Celowo puste: użycie modułu nadal zużywa energię (executeDeviceAction).
    }

    // ========== AKTUALIZACJA WIZUALNA ==========

    private void updateBatteryDisplay() {
        if (batteryRender == null) return;

        if (batteryItem != null) {
            batteryRender.setItemStack(batteryItem.clone());
            // Kolor/efekt zależy od poziomu energii
            float ratio = (float) energy / deviceType.getDefaultMaxEnergy();
            if (ratio > 0.6f) {
                batteryRender.setViewRange(1.0f);
            } else if (ratio > 0.2f) {
                batteryRender.setViewRange(0.8f);
            } else {
                batteryRender.setViewRange(0.5f);
            }
        } else {
            batteryRender.setItemStack(null);
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

    /** Usuwa cały blok 2×2 i zatrzymuje timer */
    public void remove() {
        stopDecayTimer();
        // Faza 3 (spójność linków): odbiornik znika = anteny tracą link i od razu szukają
        // nowego w AntennaManager.validate (co 2 s). Bez tego wisiałyby na martwym obiekcie
        // i nigdy nie przepięłyby się do innego odbiornika.
        for (pl.vd.raft.blocks.AntennaDevice antenna : new java.util.ArrayList<>(linkedAntennas)) {
            antenna.setLinkedReceiver(null);
        }
        linkedAntennas.clear();
        // Faza 1: nośnik znika razem z urządzeniem (tylko jeśli to nadal nasza beczka).
        pl.vd.raft.net.RaftReceiversPacketSender.removeCarrierBlock(baseLocation);
        for (int x = 0; x < 2; x++) {
            for (int y = 0; y < 2; y++) {
                if (displays[x][y] != null) displays[x][y].remove();
            }
        }
        if (batterySlot != null) batterySlot.remove();
        if (batteryRender != null) batteryRender.remove();
        if (functionalSlot != null) functionalSlot.remove();
    }
    
    // ========== TIMER DEGRADACJI BATERII ==========
    
    /** Uruchamia timer degradacji baterii */
    private void startDecayTimer() {
        if (decayTaskId != -1) return; // już uruchomiony
        
        org.bukkit.plugin.java.JavaPlugin plugin = (org.bukkit.plugin.java.JavaPlugin) org.bukkit.Bukkit.getPluginManager().getPlugins()[0];
        // Szukamy naszego pluginu
        for (org.bukkit.plugin.Plugin p : org.bukkit.Bukkit.getPluginManager().getPlugins()) {
            if (p instanceof org.bukkit.plugin.java.JavaPlugin javaPlugin) {
                if (javaPlugin instanceof pl.vd.VillagerDefensePlugin) {
                    plugin = javaPlugin;
                    break;
                }
            }
        }
        
        if (plugin == null) return;
        
        decayTaskId = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            decayBattery();
        }, DECAY_INTERVAL_TICKS, DECAY_INTERVAL_TICKS).getTaskId();
    }
    
    /** Zatrzymuje timer degradacji */
    private void stopDecayTimer() {
        if (decayTaskId == -1) return;
        
        org.bukkit.plugin.java.JavaPlugin plugin = (org.bukkit.plugin.java.JavaPlugin) org.bukkit.Bukkit.getPluginManager().getPlugins()[0];
        for (org.bukkit.plugin.Plugin p : org.bukkit.Bukkit.getPluginManager().getPlugins()) {
            if (p instanceof org.bukkit.plugin.java.JavaPlugin javaPlugin) {
                if (javaPlugin instanceof pl.vd.VillagerDefensePlugin) {
                    plugin = javaPlugin;
                    break;
                }
            }
        }
        
        if (plugin != null) {
            plugin.getServer().getScheduler().cancelTask(decayTaskId);
        }
        decayTaskId = -1;
    }
    
    /** Dekrementuje energię baterii o 1 ładunek */
    private void decayBattery() {
        if (batteryItem == null || energy <= 0) {
            stopDecayTimer();
            return;
        }
        
        energy -= DECAY_AMOUNT;
        
        if (energy <= 0) {
            // Bateria wyczerpana
            energy = 0;
            batteryItem = null;
            bateriaItem = null;
            stopDecayTimer();
            updateBatteryDisplay();
            
            // Brak powiadomienia o rozładowaniu baterii (AGENTS.md pkt 1) — celowo.
        } else {
            // Aktualizuj stan baterii w itemie
            if (bateriaItem != null) {
                bateriaItem.setCurrentEnergy(energy);
                batteryItem = bateriaItem.toItemStack();
            }
            updateBatteryDisplay();
        }
    }

    /** Pobiera UUID wszystkich entity (do rejestracji w listenerze) */
    public UUID getEntityUUID() {
        return batterySlot != null ? batterySlot.getUniqueId() : null;
    }

    /**
     * Odtwarza baterię z persistencji (DevicePersistence) — bez timera degradacji,
     * który ruszy w decayBattery po pierwszym przebiegu; energię ustawiamy wprost,
     * bo item może być innego typu niż maxEnergy zakładałbym z PDC.
     */
    public void restoreBattery(ItemStack battery, int savedEnergy) {
        this.batteryItem = battery.clone();
        this.bateriaItem = pl.vd.raft.items.BateriaItem.fromItemStack(batteryItem);
        this.energy = Math.max(0, savedEnergy);
        updateBatteryDisplay();
        startDecayTimer();
    }

    // ========== GETTERS ==========

    public ItemStack getBatteryItem() { return batteryItem; }
    public int getEnergy() { return energy; }
    public boolean hasBattery() { return batteryItem != null; }
    public Location getBaseLocation() { return baseLocation; }
    public Interaction getBatterySlot() { return batterySlot; }
    public Interaction getFunctionalSlot() { return functionalSlot; }
    public DeviceRegistry.DeviceType getDeviceType() { return deviceType; }
    /** Rotacja urządzenia 0-3 (0=South, 1=West, 2=North, 3=East) — potrzebna pakietowi raft:receivers. */
    public int getRotation() { return rotation; }

    // ========== ANTYNY (faza 2 projektu anten) ==========

    /** Podłączone anteny (lista zarządzana przez AntennaDevice.setLinkedReceiver). */
    public java.util.List<pl.vd.raft.blocks.AntennaDevice> getLinkedAntennas() {
        return java.util.Collections.unmodifiableList(linkedAntennas);
    }

    /** Czy odbiornik ma wolny slot na antenę? */
    public boolean hasFreeAntennaSlot() {
        return linkedAntennas.size() < MAX_ANTENNAS;
    }

    /** Liczba podłączonych anten (do lore w GUI). */
    public int getAntennaCount() {
        return linkedAntennas.size();
    }

    /** Rejestracja anteny — woła AntennaDevice.setLinkedReceiver, nie wprost. */
    public void addAntenna(pl.vd.raft.blocks.AntennaDevice antenna) {
        if (!linkedAntennas.contains(antenna) && linkedAntennas.size() < MAX_ANTENNAS) {
            linkedAntennas.add(antenna);
        }
    }

    /** Wyrejestrowanie anteny (zerwanie / usunięcie anteny). */
    public void removeAntenna(pl.vd.raft.blocks.AntennaDevice antenna) {
        linkedAntennas.remove(antenna);
    }

    /**
     * Czy odbiornik „widzi" wyspy: 3/3 anten i zasilanie — decyzja właściciela
     * (zerwanie = natychmiast, bez karencji). Faktycznego gateingu używa DeviceGUI.
     */
    public boolean canSeeIslands() {
        return linkedAntennas.size() >= MAX_ANTENNAS && hasBattery() && energy > 0;
    }
}
