package pl.vd.listeners;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.PiglinAbstract;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.VillagerCareerChangeEvent;
import org.bukkit.potion.PotionEffectType;
import pl.vd.VillagerDefensePlugin;
import pl.vd.arena.Arena;
import pl.vd.arena.ArenaState;
import pl.vd.game.MobSpawner;

public class MobRestrictionListener implements Listener {

    private final VillagerDefensePlugin plugin;

    public MobRestrictionListener(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
    }

    /** Blokuje naturalne pojawianie się mobów (np. na trawie) oraz fantomy w każdych okolicznościach. */
    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getEntityType() == EntityType.PHANTOM) {
            event.setCancelled(true);
            return;
        }
        // Zombie na kurczaku (chicken jockey) i inne jockey (np. szkielet na pająku):
        // pasażera tworzy sam mechanizm spawna zombie (5% szans) - niezależnie od kodu
        // pluginu i często z opóźnieniem, dlatego blokujemy jego spawn u źródła
        // GLOBALNIE (we wszystkich światach), żeby w tym trybie nigdy się nie pojawił.
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.JOCKEY) {
            event.setCancelled(true);
            return;
        }
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL) {
            event.setCancelled(true);
        }
    }

    /** Blokuje wieśniakom przyjmowanie PRACY (zmianę zawodu) - globalnie, we
     *  wszystkich światach. Wieśniak nie może zostać zatrudniony przy żadnym
     *  bloku pracy (stoł rzemieślniczy, pulpit, kartograf, beczka itd.) - zawsze
     *  zostaje bez zawodu. Tylko zdarzenie EMPLOYED (zatrudnienie); utrata pracy
     *  (LOSING_JOB, powrót do braku zawodu) jest nieszkodliwa i zostaje. */
    @EventHandler
    public void onVillagerCareer(VillagerCareerChangeEvent event) {
        if (event.getReason() == VillagerCareerChangeEvent.ChangeReason.EMPLOYED) {
            event.setCancelled(true);
        }
    }

    /** Blokuje przemianę wieśniaka w zombie wieśniaka (infekcja od zombie)
     *  oraz zombifikację piglinów z areny (np. bossa PIGLIN_BRUTE w overworldzie). */
    @EventHandler
    public void onTransform(EntityTransformEvent event) {
        if (event.getEntity() instanceof Villager) {
            event.setCancelled(true);
            return;
        }
        // Pigliny spawnowane przez plugin (boss) nie mogą zamienić się w zombie piglina
        if (event.getEntity().hasMetadata(MobSpawner.METADATA_KEY)
                && event.getEntity() instanceof PiglinAbstract) {
            event.setCancelled(true);
        }
    }

    /** Zeruje expa i czyści dropy z każdego moba/wieśniaka zaspawnowanego przez plugin. */
    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        if (!event.getEntity().hasMetadata(MobSpawner.METADATA_KEY)) return;
        event.setDroppedExp(0);
        event.getDrops().clear();
    }

    /** Blokuje efekt głodu, który husk normalnie nakłada po trafieniu gracza. */
    @EventHandler
    public void onPotionEffect(EntityPotionEffectEvent event) {
        // Przywołany Nadzorca (warden) nie nakłada na graczy efektu CIEMNOŚCI
        if (event.getModifiedType() == PotionEffectType.DARKNESS
                && event.getCause() == EntityPotionEffectEvent.Cause.WARDEN
                && event.getEntity() instanceof Player) {
            event.setCancelled(true);
            return;
        }
        if (event.getModifiedType() != PotionEffectType.HUNGER) return;
        if (event.getCause() != EntityPotionEffectEvent.Cause.ATTACK) return;
        if (!(event.getEntity() instanceof Player player)) return;

        if (isInActiveArena(player)) {
            event.setCancelled(true);
        }
    }

    private boolean isInActiveArena(Player player) {
        for (Arena arena : plugin.getArenaManager().getArenas().values()) {
            if (arena.isPlaying(player) && arena.getState() == ArenaState.INGAME) return true;
        }
        return false;
    }
}
