package pl.vd.game;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.metadata.FixedMetadataValue;
import pl.vd.VillagerDefensePlugin;
import pl.vd.arena.Arena;

import java.util.List;
import java.util.Random;

public class MobSpawner {

    public static final String METADATA_KEY = "vd-arena";

    private final VillagerDefensePlugin plugin;
    private final Random random = new Random();

    private static final EntityType[] NORMAL_POOL = {
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER,
            EntityType.HUSK, EntityType.CAVE_SPIDER
    };
    private static final EntityType[] HARD_POOL = {
            EntityType.WITCH, EntityType.PILLAGER, EntityType.VINDICATOR
    };
    private static final EntityType BOSS = EntityType.PIGLIN_BRUTE; // boss at wave 10

    public MobSpawner(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
    }

    /** Oblicza liczbę mobów w danej rundzie na podstawie config.yml */
    public int calculateMobCount(int round, int playerCount) {
        int base = plugin.getConfig().getInt("game.first-round-count", 3);
        if (round <= 1) return base;
        if (round <= 10) {
            // increase by 1 each wave up to wave 10
            return base + (round - 1) * 1;
        } else {
            // after wave 10, increase by 2 each wave
            return base + 9 * 1 + (round - 10) * 2;
        }
    }

    public boolean isBossRound(int round) {
        // Boss on wave 10 specifically (also allow config override)
        int bossWave = plugin.getConfig().getInt("waves.boss-wave", 10);
        return bossWave > 0 && round == bossWave;
    }

    public void spawnWave(Arena arena, int round) {
        List<Location> spawns = arena.getVillagerSpawns().isEmpty()
                ? arena.getPlayerSpawns()
                : arena.getVillagerSpawns();
        if (spawns.isEmpty()) return;

        int count = calculateMobCount(round, arena.getPlayers().size());
        boolean boss = isBossRound(round);

        for (int i = 0; i < count; i++) {
            Location loc = spawns.get(random.nextInt(spawns.size()));
            EntityType type = pickType(round, boss && i == 0);
            LivingEntity entity = (LivingEntity) loc.getWorld().spawnEntity(loc, type);
            // tag entity as arena mob and prevent normal behaviours
            entity.setMetadata(METADATA_KEY, new FixedMetadataValue(plugin, arena.getName()));
            entity.setRemoveWhenFarAway(false);
            entity.setCanPickupItems(false);
            // ensure mob does not carry or spawn riders (chicken jockeys)
            try {
                for (org.bukkit.entity.Entity passenger : entity.getPassengers()) {
                    // remove any passenger (e.g., chicken)
                    passenger.remove();
                }
            } catch (Throwable ignored) {}
            arena.getSpawnedMobs().add(entity);
        }
    }

    private EntityType pickType(int round, boolean forceBoss) {
        if (forceBoss) return BOSS;
        return random.nextInt(100) < 30 ? EntityType.HUSK : EntityType.ZOMBIE;
    }
}
