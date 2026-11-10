package pl.vd.listeners;

import org.bukkit.entity.SulfurCube;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import pl.vd.VillagerDefensePlugin;

public class MineSpawnListener implements Listener {

    private final VillagerDefensePlugin plugin;

    public MineSpawnListener(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof SulfurCube cube)) return;
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.SPAWNER_EGG) return;

        plugin.getMineManager().activateAsMine(cube);
    }
}
