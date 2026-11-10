package pl.vd.game;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.PiglinAbstract;
import org.bukkit.entity.Zombie;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import pl.vd.VillagerDefensePlugin;
import pl.vd.arena.Arena;
import pl.vd.util.HealthDisplay;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MobSpawner {

    public static final String METADATA_KEY = "vd-arena";

    // Szansa (w %) że w fali segmentu mobów przybędzie +2 zamiast +1 (reszta to +1).
    // 50 = dokładnie 50/50: albo 1, albo 2 moby więcej z każdą falą.
    private static final int GROWTH_TWO_CHANCE_PERCENT = 50;

    private final VillagerDefensePlugin plugin;
    private final Random random = new Random();

    private static final EntityType BOSS = EntityType.PIGLIN_BRUTE; // boss at wave 10

    // ================================================================
    //  ULEPSZENIA MOBÓW CO 5 FAL  (TU WPISUJESZ SWOJE LICZBY)
    // ================================================================
    // Ulepszenia wchodzą co UPGRADE_STEP_ROUNDS fal (domyślnie co 5):
    //   fale 1-5   = 100% (bez ulepszeń)
    //   fale 6-10  = +1 poziom (mnożnik 1 + wartosc * 1)
    //   fale 11-15 = +2 poziomy (mnożnik 1 + wartosc * 2)
    //   ...
    // Każda wartość to PROCENT, o jaki mob rośnie w siłę za KAŻDY POZIOM (czyli co 5 fal).
    // 0.0 = wyłączone (na razie nic nie zmieniają).
    //
    // Przykład: HP_PERCENT_PER_WAVE = 0.10 oznacza:
    //   fale 1-5 = 100% HP, fale 6-10 = 110% HP, fale 11-15 = 120% HP ...
    //
    // Liczby działają na WSZYSTKIE moby z areny (teraz: zombie i husk).
    // Jeśli chcesz, żeby boss (PIGLIN_BRUTE) nie dostawał ulepszeń - powiedz, dodamy warunek.
    private static final double HP_PERCENT_PER_WAVE = 0.0;     // np. 0.10 = +10% HP co 5 fal
    private static final double DAMAGE_PERCENT_PER_WAVE = 0.0; // np. 0.05 = +5% obrażeń co 5 fal
    private static final double SPEED_PERCENT_PER_WAVE = 0.0;  // np. 0.02 = +2% szybkości co 5 fal

    // Co ile fal moby dostają kolejny poziom ulepszeń (zmień na np. 3, żeby było co 3 fale)
    private static final int UPGRADE_STEP_ROUNDS = 5;

    // ================================================================
    //  EKWIPUNEK MOBÓW OD KONKRETNYCH FAL  (TU WPISUJESZ SWOJE LICZBY)
    // ================================================================
    // Każdy wiersz = EquipmentTier(minFala, coIleFal, procentMobow, broń, hełm, klata, nogi, buty):
    //   - minFala      - od której fali wiersz jest aktywny,
    //   - coIleFal     - co ile fal wiersz działa (1 = co falę od minFala; np. 2 = co 2 fale:
    //                    20, 22, 24... - w przerwach moby dostają sprzęt z niższego wiersza),
    //   - procentMobow - ile PROCENT mobów z fali nosi ten sprzęt (reszta zostaje goła);
    //                    100 = wszystkie moby. Im wyższa fala, tym więcej uzbrojonych,
    //                    więc zwiększaj procent w kolejnych wierszach.
    //   - null w jakimś polu = mob NIE dostaje tej części.
    // Sprzęt dostają tylko zombie i huski (husk dziedziczy po Zombie).
    //
    // Gdy w fali aktywnych jest kilka wierszy, NAJWYŻSZY wygrywa - mob losuje sprzęt
    // tylko z niego (np. przy fali 25 aktywne są wiersze 10, 15 i 20 -> wiersz 20).
    //
    // EQUIP_ENABLED = false  wyłącza cały ekwipunek (szybki wyłącznik).
    private static final boolean EQUIP_ENABLED = true;

    // Domyślne wiersze pasują do segmentów fal z config.yml (waves.mob-count-segments):
    //   fale 1-15  - spokojne, moby bez sprzętu;
    //   fale 16-25 - ogonek: drewniany miecz u części mobów od fali 20;
    //   fale 26-40 - "lepsza zbroja": kamień+skóra od 26, żelazo od 31;
    //   fale 41+   - "ulepszone moby": pełne żelazo od 41, diament od 51, pełny diament od 61.
    private static final EquipmentTier[] EQUIPMENT_TIERS = {
            //  minFala | co ile fal | % mobów | broń                | hełm               | klata                     | nogi                | buty
            new EquipmentTier(20, 1, 25, Material.WOODEN_SWORD, null, null, null, null),
            new EquipmentTier(26, 1, 35, Material.STONE_SWORD, Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, null, null),
            new EquipmentTier(31, 1, 45, Material.IRON_SWORD, Material.IRON_HELMET, Material.IRON_CHESTPLATE, null, null),
            new EquipmentTier(41, 1, 55, Material.IRON_SWORD, Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS),
            new EquipmentTier(51, 1, 65, Material.DIAMOND_SWORD, Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS),
            new EquipmentTier(61, 1, 75, Material.DIAMOND_SWORD, Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS),
    };

    /** Pojedyncza "tura" ekwipunku: od której fali, co ile fal, jaki % mobów i co dokładnie mob ma dostać. */
    private record EquipmentTier(int minWave, int everyWaves, int percent, Material weapon, Material helmet, Material chestplate, Material leggings, Material boots) {}

    // ================================================================
    //  ZAKLĘCIA NA SPRZĘCIE MOBÓW  (TU WPISUJESZ SWOJE LICZBY)
    // ================================================================
    // Zaklęcia dostają TYLKO moby, które dostały broń/zbroję z EQUIPMENT_TIERS
    // (gołe moby nie mają czego zaczarować).
    //   - OSTRość na broni: poziom 1 od SHARPNESS_START_WAVE, potem +1 poziom co
    //     SHARPNESS_STEP_WAVES fal (domyślnie: fale 41-70 = Ostra I, 71-100 = Ostra II,
    //     101-130 = Ostra III, ...).
    //   - OCHRONA na zbroi: poziom 1 od PROTECTION_START_WAVE, potem +1 poziom co
    //     PROTECTION_STEP_WAVES fal (domyślnie: fale 60-89 = Ochrona I, 90-119 = Ochrona II,
    //     120-149 = Ochrona III, 150+ = Ochrona IV).
    private static final int SHARPNESS_START_WAVE = 41;
    private static final int SHARPNESS_STEP_WAVES = 30;
    private static final int PROTECTION_START_WAVE = 60;
    private static final int PROTECTION_STEP_WAVES = 30;
    //   - ZAKLĘTY OGIEN (fire aspect) na broni: od FIRE_ASPECT_START_WAVE moby mają
    //     FIRE_ASPECT_BASE_PERCENT szans na Ognisty miecz, a każda kolejna runda
    //     podnosi szansę o FIRE_ASPECT_STEP_PERCENT (0.5%). Po dojściu do 100%
    //     szansa zostaje na stałe - każde uzbrojony mob ma wtedy Ognisty miecz.
    private static final int FIRE_ASPECT_START_WAVE = 41;
    private static final double FIRE_ASPECT_BASE_PERCENT = 35.0;
    private static final double FIRE_ASPECT_STEP_PERCENT = 0.5;

    public MobSpawner(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Oblicza liczbę mobów w danej rundzie na podstawie config.yml (sekcja waves).
     * Gra podzielona jest na SEGMENTY fal (waves.mob-count-segments): na początku
     * każdego segmentu liczba mobów resetuje się do base-count, a potem rośnie
     * losowo - w każdej fali mob ma 50% szans na +1 i 50% na +2 moby.
     * Po ostatnim zdefiniowanym segmencie fale idą automatycznie dalej:
     * segmenty po waves.mob-count-tail-length fal, a base-count rośnie o 1 w każdym
     * kolejnym segmencie (o 1 zombiaka więcej niż wcześniej).
     */
    public int calculateMobCount(int round, int playerCount) {
        List<int[]> segments = loadMobCountSegments();
        if (segments.isEmpty()) {
            // brak segmentów w config.yml - prosty, bezpieczny wzrost
            return 3 + Math.max(0, round - 1);
        }

        // ostatni segment, który zaczyna się nie później niż ta fala
        int[] seg = segments.get(0);
        for (int[] s : segments) {
            if (s[0] <= round) seg = s;
        }

        int segmentEnd = seg[0] + seg[1] - 1;
        if (round <= segmentEnd) {
            // fala jest w zdefiniowanym segmencie
            int k = round - seg[0] + 1; // 1 = pierwsza fala segmentu
            return countInSegment(seg[2], k);
        }

        // poza zdefiniowanymi segmentami: powtarzaj ostatni co tail-length fal,
        // a base-count rośnie o 1 w każdym kolejnym segmencie
        int[] last = segments.get(segments.size() - 1);
        int tailLength = Math.max(1, plugin.getConfig().getInt("waves.mob-count-tail-length", 10));
        int beyond = round - segmentEnd; // 1 = pierwsza fala za ostatnim segmentem
        int segmentIndex = (beyond - 1) / tailLength; // 0 = pierwszy powtarzany segment
        int posInSegment = (beyond - 1) % tailLength + 1;
        int base = last[2] + 1 + segmentIndex; // +1 zombiak więcej niż w poprzednim segmencie
        return countInSegment(base, posInSegment);
    }

    /** Czyta segmenty z config.yml: {fromWave, length, baseCount}. */
    private List<int[]> loadMobCountSegments() {
        List<int[]> segments = new ArrayList<>();
        for (var m : plugin.getConfig().getMapList("waves.mob-count-segments")) {
            Number from = (Number) m.get("from-wave");
            Number length = (Number) m.get("length");
            Number base = (Number) m.get("base-count");
            if (from == null || length == null || base == null) continue;
            int fromWave = Math.max(1, from.intValue());
            int len = Math.max(1, length.intValue());
            int baseCount = Math.max(1, base.intValue());
            segments.add(new int[]{fromWave, len, baseCount});
        }
        return segments;
    }

    /**
     * Wzrost w segmencie: każda fala losuje GROWTH_TWO_CHANCE_PERCENT szans na +2
     * moby zamiast +1 (domyślnie 50/50 - czyli albo 1, albo 2 moby więcej co falę).
     */
    private int countInSegment(int base, int k) {
        int added = 0;
        for (int i = 1; i < k; i++) {
            added += random.nextInt(100) < GROWTH_TWO_CHANCE_PERCENT ? 2 : 1;
        }
        return base + added;
    }

    public boolean isBossRound(int round) {
        // Boss on wave 10 specifically (also allow config override)
        int bossWave = plugin.getConfig().getInt("waves.boss-wave", 10);
        return bossWave > 0 && round == bossWave;
    }

    public void spawnWave(Arena arena, int round) {
        // moby spawnują się z OSOBNYCH punktów mobów (jeśli ustawione komendą
        // /def addzombiespawn); fallback: punkty wieśniaków, a jak ich brak - punkty graczy
        List<Location> spawns = !arena.getMobSpawns().isEmpty() ? arena.getMobSpawns()
                : !arena.getVillagerSpawns().isEmpty() ? arena.getVillagerSpawns()
                : arena.effectivePlayerSpawns();
        if (spawns.isEmpty()) return;

        int count = calculateMobCount(round, arena.getPlayers().size());
        boolean boss = isBossRound(round);
        // miejsca pojawienia się mobów w tej fali (wg trybu z config.yml)
        List<Location> waveSpawns = pickWaveSpawns(spawns, count);

        for (int i = 0; i < count; i++) {
            Location loc = waveSpawns.get(i);
            EntityType type = pickType(round, boss && i == 0);
            LivingEntity entity = (LivingEntity) loc.getWorld().spawnEntity(loc, type);
            // tag entity as arena mob and prevent normal behaviours
            entity.setMetadata(METADATA_KEY, new FixedMetadataValue(plugin, arena.getName()));
            entity.setRemoveWhenFarAway(false);
            entity.setCanPickupItems(false);
            // wyciszenie dźwięków mobów z fal: z ProtocolLib działa per gracz
            // (filtrowanie pakietów w SoundMuteListener), bez niego fallback =
            // moby milczą dla całej areny, gdy ktoś to wyłączył
            if (!plugin.hasProtocolLib()) {
                entity.setSilent(plugin.getGameManager().mobSoundsMuted(arena));
            }

            // Zombie (i wszystkie jego podtypy: husk, drowned, zombie villager, zombified piglin)
            // nigdy nie mają LOSOWEJ zbroi ani broni (Paper losuje np. złote miecze).
            // Broń/zbroję dajemy tylko CELOWO, w applyEquipment, zależnie od fali.
            if (entity instanceof Zombie zombie && zombie.getEquipment() != null) {
                zombie.getEquipment().clear();
            }

            // Przywróć domyślne zdrowie moba (bez modyfikatorów, które Paper dodaje przy spawnie
            // np. od losowego ekwipunku czy trudności regionu) - zombie zawsze ma 20 HP (10 serc)
            normalizeHealth(entity);

            // Nałóż ulepszenia zależne od numeru fali (HP / obrażenia / szybkość)
            applyWaveUpgrades(entity, round);

            // Daj broń/zbroję od odpowiednich fal (np. drewniany miecz od fali 10)
            applyEquipment(entity, round);

            // Pigliny (w tym boss PIGLIN_BRUTE) nie mogą zamienić się w zombie piglina
            // w overworldzie (inaczej boss zniknąłby po ~15 sekundach od spawna)
            if (entity instanceof PiglinAbstract piglin) {
                piglin.setImmuneToZombification(true);
            }

            // ensure mob does not carry or spawn riders (chicken jockeys)
            try {
                for (org.bukkit.entity.Entity passenger : entity.getPassengers()) {
                    passenger.remove();
                    entity.removePassenger(passenger);
                }
            } catch (Throwable ignored) {}
            // safety net: na niektórych wersjach Paper kurczak dosiada się dopiero tick
            // później, więc sprawdzamy pasażerów jeszcze raz w następnym ticku
            final LivingEntity spawned = entity;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!spawned.isValid() || spawned.isDead()) return;
                for (org.bukkit.entity.Entity passenger : new ArrayList<>(spawned.getPassengers())) {
                    passenger.remove();
                    spawned.removePassenger(passenger);
                }
            });
            HealthDisplay.update(entity);
            arena.getSpawnedMobs().add(entity);
        }
    }

    private EntityType pickType(int round, boolean forceBoss) {
        if (forceBoss) return BOSS;
        return random.nextInt(100) < 30 ? EntityType.HUSK : EntityType.ZOMBIE;
    }

    /**
     * Wybiera miejsca pojawienia się mobów fali wg trybu z config.yml (waves.mob-spawn-mode):
     *  point  - dokładnie w ustawionych punktach,
     *  random - losowy rozrzut w obrębie waves.mob-spawn-radius,
     *  grid   - idealna siatka: moby obok siebie, każdy dokładnie 1 blok od drugiego
     *           (spirala wokół wylosowanego punktu), ale dopiero od waves.grid-min-mobs
     *           mobów w fali; przy mniejszej liczbie - losowy rozrzut jak w trybie random.
     */
    private List<Location> pickWaveSpawns(List<Location> spawns, int count) {
        List<Location> result = new ArrayList<>(count);
        String mode = plugin.getConfig().getString("waves.mob-spawn-mode", "random").toLowerCase();
        int spawnRadius = plugin.getConfig().getInt("waves.mob-spawn-radius", 5);

        switch (mode) {
            case "point" -> {
                for (int i = 0; i < count; i++) {
                    result.add(spawns.get(random.nextInt(spawns.size())));
                }
            }
            case "grid" -> {
                // dopiero od pewnej liczby mobów fala ustawia się w idealną siatkę;
                // przy mniejszej liczbie moby pojawiają się losowo (jak w trybie random)
                int gridMin = plugin.getConfig().getInt("waves.grid-min-mobs", 11);
                if (count < gridMin) {
                    for (int i = 0; i < count; i++) {
                        Location base = spawns.get(random.nextInt(spawns.size()));
                        result.add(spawnRadius > 0 ? scatterAround(base, spawnRadius) : base);
                    }
                    break;
                }
                // cała fala wokół jednego punktu: pierwszy mob w samym punkcie,
                // kolejne w komórkach spirali - każda 1 blok od poprzedniej
                Location anchor = spawns.get(random.nextInt(spawns.size()));
                for (int[] cell : gridSpiral(count)) {
                    Location c = anchor.clone().add(cell[0], 0, cell[1]);
                    Location safe = findSafeY(c);
                    result.add(safe != null ? safe : anchor);
                }
            }
            default -> { // random
                for (int i = 0; i < count; i++) {
                    Location base = spawns.get(random.nextInt(spawns.size()));
                    result.add(spawnRadius > 0 ? scatterAround(base, spawnRadius) : base);
                }
            }
        }
        return result;
    }

    /**
     * Kolejne komórki siatki (dx, dz) w spirali wokół (0,0): (0,0), (1,0), (1,1), (0,1),
     * (-1,1), ... - każda komórka jest dokładnie 1 blok od poprzedniej.
     */
    private List<int[]> gridSpiral(int count) {
        List<int[]> cells = new ArrayList<>(count);
        int x = 0, z = 0;
        int dx = 0, dz = -1;
        for (int i = 0; i < count; i++) {
            cells.add(new int[]{x, z});
            if (x == z || (x < 0 && x == -z) || (x > 0 && x == 1 - z)) {
                int t = dx;
                dx = -dz;
                dz = t; // skręć w prawo
            }
            x += dx;
            z += dz;
        }
        return cells;
    }

    /**
     * Losowy punkt w obrębie promienia wokół base (równomiernie w kole), ustawiony na
     * bezpiecznym bloku (grunt pod spodem, wolne miejsce na moba). Fallback: sam punkt.
     */
    private Location scatterAround(Location base, int radius) {
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double dist = radius * Math.sqrt(random.nextDouble());
            double dx = Math.cos(angle) * dist;
            double dz = Math.sin(angle) * dist;
            Location candidate = base.clone().add(dx, 0, dz);
            Location safe = findSafeY(candidate);
            if (safe != null) return safe;
        }
        return base;
    }

    /**
     * Szuka dla punktu bezpiecznej wysokości: grunt pod spodem i wolne 2 bloki na moba.
     * Zwraca punkt środka bloku, albo null gdy w okolicy brak wolnego miejsca.
     */
    private Location findSafeY(Location loc) {
        World world = loc.getWorld();
        if (world == null) return null;
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        for (int dy = -2; dy <= 3; dy++) {
            int cy = y + dy;
            if (cy - 1 < world.getMinHeight() || cy + 1 >= world.getMaxHeight()) continue;
            if (!world.getBlockAt(x, cy - 1, z).getType().isSolid()) continue; // grunt pod spodem
            if (world.getBlockAt(x, cy, z).getType().isSolid()) continue;       // blok w miejscu
            if (world.getBlockAt(x, cy + 1, z).getType().isSolid()) continue;   // sufit nad mobem
            return new Location(world, x + 0.5, cy, z + 0.5);
        }
        return null;
    }

    /**
     * Ustawia mobowi standardowe (domyślne dla jego typu) zdrowie:
     * usuwa modyfikatory atrybutu max_health i przywraca bazową wartość.
     * Dzięki temu zombie ma zawsze 20 HP (10 serc), nie 60+.
     */
    private void normalizeHealth(LivingEntity entity) {
        try {
            Attribute attr = Bukkit.getRegistry(Attribute.class).get(NamespacedKey.minecraft("max_health"));
            if (attr == null) return;
            var attribute = entity.getAttribute(attr);
            if (attribute == null) return;

            double base = attribute.getDefaultValue();
            for (AttributeModifier modifier : new java.util.ArrayList<>(attribute.getModifiers())) {
                attribute.removeModifier(modifier);
            }
            attribute.setBaseValue(base);
            entity.setHealth(base);

            // boss PIGLIN_BRUTE ma 40 serc (80 HP) zamiast domyślnych 25 serc
            if (entity.getType() == EntityType.PIGLIN_BRUTE) {
                attribute.setBaseValue(80.0);
                entity.setHealth(80.0);
            }
        } catch (Throwable ignored) {
            // brak atrybutu w API lub problem z modyfikatorem - zostaw domyślne HP
        }
    }

    /**
     * Nakłada na moba ulepszenia co UPGRADE_STEP_ROUNDS fal (domyślnie co 5):
     * max_health, attack_damage i movement_speed rosną o X% za każdy poziom.
     * Poziom = ile razy minął kolejny próg (fale 6, 11, 16, ...).
     * Fale 1-5 zawsze są bez ulepszeń (mnożnik 1.0).
     * Wywoływane PO normalizeHealth, żeby nie nadpisać ustawionego zdrowia.
     */
    private void applyWaveUpgrades(LivingEntity entity, int round) {
        // Ile poziomów ulepszeń minęło: fale 1-5 -> 0, 6-10 -> 1, 11-15 -> 2, ...
        int levels = (round - 1) / UPGRADE_STEP_ROUNDS;
        if (levels <= 0) return; // pierwsze UPGRADE_STEP_ROUNDS fal = bez ulepszeń

        // --- 1) HP: max_health x (1 + HP_PERCENT_PER_WAVE * poziomy) ---
        if (HP_PERCENT_PER_WAVE > 0) {
            Attribute hpAttr = Bukkit.getRegistry(Attribute.class).get(NamespacedKey.minecraft("max_health"));
            if (hpAttr != null && entity.getAttribute(hpAttr) != null) {
                double base = entity.getAttribute(hpAttr).getBaseValue(); // 20 dla zombie, 80 dla bossa
                double newMax = base * (1.0 + HP_PERCENT_PER_WAVE * levels);
                entity.getAttribute(hpAttr).setBaseValue(newMax);
                entity.setHealth(newMax); // wypełnij HP do nowego maksimum
            }
        }

        // --- 2) Obrażenia: attack_damage x (1 + DAMAGE_PERCENT_PER_WAVE * poziomy) ---
        if (DAMAGE_PERCENT_PER_WAVE > 0) {
            Attribute dmgAttr = Bukkit.getRegistry(Attribute.class).get(NamespacedKey.minecraft("attack_damage"));
            if (dmgAttr != null && entity.getAttribute(dmgAttr) != null) {
                double base = entity.getAttribute(dmgAttr).getBaseValue(); // zombie ma 3.0 (1.5 serca)
                entity.getAttribute(dmgAttr).setBaseValue(base * (1.0 + DAMAGE_PERCENT_PER_WAVE * levels));
            }
        }

        // --- 3) Szybkość: movement_speed x (1 + SPEED_PERCENT_PER_WAVE * poziomy) ---
        if (SPEED_PERCENT_PER_WAVE > 0) {
            Attribute spdAttr = Bukkit.getRegistry(Attribute.class).get(NamespacedKey.minecraft("movement_speed"));
            if (spdAttr != null && entity.getAttribute(spdAttr) != null) {
                double base = entity.getAttribute(spdAttr).getBaseValue(); // zombie ma 0.23
                entity.getAttribute(spdAttr).setBaseValue(base * (1.0 + SPEED_PERCENT_PER_WAVE * levels));
            }
        }
    }

    /**
     * Daje zombie broń/zbroję zależnie od fali (EquipmentTier).
     * Wywoływane PO clear() ekwipunku w spawnWave, więc losowy sprzęt Papera
     * nigdy nie zostaje - tylko to, co ustawiamy tu celowo.
     * Szansa dropu jest wyzerowana, żeby moby nie zaśmiecały areny przedmiotami.
     */
    private void applyEquipment(LivingEntity entity, int round) {
        if (!EQUIP_ENABLED) return;
        if (!(entity instanceof Zombie zombie)) return; // tylko zombie i huski (husk dziedziczy po Zombie)
        var equipment = zombie.getEquipment();
        if (equipment == null) return;

        // Znajdź NAJWYŻSZY wiersz aktywny w tej fali: minFala <= fala i (fala - minFala)
        // dzieli się przez coIleFal. Np. przy fali 16 aktywny jest tylko wiersz 10,
        // przy fali 22 aktywne są wiersze 10, 15 i 20 -> wygrywa wiersz 20.
        EquipmentTier tier = null;
        for (EquipmentTier t : EQUIPMENT_TIERS) {
            if (t.minWave() <= round && (round - t.minWave()) % t.everyWaves() == 0) tier = t;
        }
        if (tier == null) return;

        // Tylko procent mobów z fali nosi sprzęt - reszta zostaje goła.
        if (random.nextInt(100) >= tier.percent()) return;

        if (tier.weapon() != null) {
            equipment.setItemInMainHand(new ItemStack(tier.weapon()));
            equipment.setItemInMainHandDropChance(0f);
        }
        if (tier.helmet() != null) {
            equipment.setHelmet(new ItemStack(tier.helmet()));
            equipment.setHelmetDropChance(0f);
        }
        if (tier.chestplate() != null) {
            equipment.setChestplate(new ItemStack(tier.chestplate()));
            equipment.setChestplateDropChance(0f);
        }
        if (tier.leggings() != null) {
            equipment.setLeggings(new ItemStack(tier.leggings()));
            equipment.setLeggingsDropChance(0f);
        }
        if (tier.boots() != null) {
            equipment.setBoots(new ItemStack(tier.boots()));
            equipment.setBootsDropChance(0f);
        }

        // Zaklęcia (Ostrość na broni, Ochrona na zbroi) - tylko dla uzbrojonych mobów
        applyEnchantments(equipment, round);
    }

    /**
     * Nakłada zaklęcia na sprzęt moba (wywoływane PO nadaniu broń/zbroi):
     * Ostrość na broni od SHARPNESS_START_WAVE (+1 poziom co SHARPNESS_STEP_WAVES fal)
     * i Ochrona na zbroi od PROTECTION_START_WAVE (+1 poziom co PROTECTION_STEP_WAVES fal).
     * Poziomy są przypięte do waniliowego maksimum (Ostrość max 5, Ochrona max 4).
     */
    private void applyEnchantments(EntityEquipment equipment, int round) {
        ItemStack weapon = equipment.getItemInMainHand();
        boolean hasWeapon = weapon.getType() != Material.AIR;

        Enchantment sharpness = getEnchantment("sharpness");
        if (sharpness != null && hasWeapon && round >= SHARPNESS_START_WAVE) {
            int level = Math.min(5, 1 + (round - SHARPNESS_START_WAVE) / SHARPNESS_STEP_WAVES);
            weapon.addUnsafeEnchantment(sharpness, level);
        }

        // Zaklęty Ogień: szansa rośnie z każdą rundą (FIRE_ASPECT_BASE_PERCENT na
        // starcie, +FIRE_ASPECT_STEP_PERCENT/rundę) do 100% - potem zawsze na mieczu.
        Enchantment fireAspect = getEnchantment("fire_aspect");
        if (fireAspect != null && hasWeapon && round >= FIRE_ASPECT_START_WAVE) {
            double chance = Math.min(100.0, FIRE_ASPECT_BASE_PERCENT
                    + (round - FIRE_ASPECT_START_WAVE) * FIRE_ASPECT_STEP_PERCENT);
            if (random.nextDouble() * 100 < chance) {
                weapon.addUnsafeEnchantment(fireAspect, 1);
            }
        }

        Enchantment protection = getEnchantment("protection");
        if (protection != null && round >= PROTECTION_START_WAVE) {
            int level = Math.min(4, 1 + (round - PROTECTION_START_WAVE) / PROTECTION_STEP_WAVES);
            for (ItemStack piece : new ItemStack[]{
                    equipment.getHelmet(), equipment.getChestplate(),
                    equipment.getLeggings(), equipment.getBoots()}) {
                if (piece != null && piece.getType() != Material.AIR) {
                    piece.addUnsafeEnchantment(protection, level);
                }
            }
        }
    }

    /** Pobiera zaklęcie po kluczu Minecrafta (np. "sharpness") przez rejestr. */
    private Enchantment getEnchantment(String minecraftKey) {
        try {
            return Bukkit.getRegistry(Enchantment.class)
                    .get(new NamespacedKey(NamespacedKey.MINECRAFT, minecraftKey));
        } catch (Throwable ignored) {
            return null;
        }
    }
}
