package pl.vd.raft.hud;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import pl.vd.raft.RaftPlugin;
import pl.vd.raft.core.PlayerData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Waniliowy HUD trybu Raft dla graczy BEZ moda raft-hud.
 *
 * Ta klasa nie jest zrodlem zadnej statystyki — to wylacznie WIDOK. Zrodlem prawdy jest
 * {@link PlayerData} (pragnienie 0-20) oraz prawdziwe HP gracza; tutaj nic sie nie trzyma
 * i nic nie zmienia, a jedynie przerysowuje pragnienie na waniliowy pasek XP, bo gracz bez
 * moda nie ma innego sposobu, zeby zobaczyc wode.
 *
 * Historia (dlaczego to wyglada tak, a nie inaczej): klasa miala kiedys WLASNY model
 * statystyk (thirst/hunger/health w skali 0-100). Powstal wtedy drugi, rownolegly licznik
 * pragnienia — mod czytal PlayerData (0-20), a picie wody podnosilo tylko ten tutejszy,
 * wiec widoczny pasek nigdy nie rosl, a kary (spowolnienie, obrazenia) byly nieosiagalne.
 * Teraz licznik jest jeden; tutaj go tylko przeliczamy na skale starych paczek
 * (0-20 -> 0-100), zeby wyglad paska XP sie nie zmienil.
 *
 * Zycie i glod sa waniliowe — te paczki ich nie nadpisuja, wiec gracz bez moda widzi
 * prawdziwe serca i prawdziwy glod.
 *
 * Po wyjsciu z trybu Raft przywracamy pelny stan klienta (zycie, glod, pasek XP i predkosc):
 * bez tego klient zostawal z paskiem XP nadpisanym pragnieniem (np. poziom 100) az do
 * najblizszej zmiany doswiadczenia — a gracz z modem, ktory chowa pasek XP tylko w trybie
 * Raft, zobaczylby po wyjsciu wlasnie te smieciowa wartosc.
 */
public class RaftHUDManager {

    private final RaftPlugin plugin;
    private ProtocolManager protocolManager;
    /** Ostatnio wyslane pragnienie (0-100) na gracza — pakiet idzie tylko przy zmianie. */
    private final Map<UUID, Integer> lastSentThirst = new HashMap<>();
    private boolean enabled = false;

    private static final int UPDATE_INTERVAL = 10;

    public RaftHUDManager(RaftPlugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        try {
            protocolManager = ProtocolLibrary.getProtocolManager();
            enabled = true;
            startUpdateTask();
            plugin.getLogger().info("[RaftHUD] Waniliowy HUD dla graczy bez moda włączony (ProtocolLib)");
        } catch (Exception e) {
            plugin.getLogger().warning("[RaftHUD] ProtocolLib nie znaleziony - HUD wylaczony.");
            enabled = false;
        }
    }

    public void disable() {
        // Najpierw przywroc waniliowy stan wszystkim sledzonym graczom (np. przy /reload),
        // bo po wylaczeniu nikt juz nie naprawi nadpisanego paska XP.
        for (UUID uuid : lastSentThirst.keySet().toArray(new UUID[0])) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                restoreVanilla(player);
            }
        }
        enabled = false;
        lastSentThirst.clear();
    }

    public void addPlayer(Player player) {
        if (!enabled) return;
        lastSentThirst.remove(player.getUniqueId());
        updateHUD(player);
    }

    public void removePlayer(Player player) {
        if (!enabled) return;
        lastSentThirst.remove(player.getUniqueId());
        restoreVanilla(player);
    }

    public boolean isEnabled() { return enabled; }

    private void startUpdateTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!enabled) { this.cancel(); return; }
                for (UUID uuid : lastSentThirst.keySet().toArray(new UUID[0])) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null && player.isOnline()) {
                        updateHUD(player);
                    }
                }
            }
        }.runTaskTimer(plugin.getParent(), UPDATE_INTERVAL, UPDATE_INTERVAL);
    }

    /**
     * Przerysowuje pragnienie z jedynego licznika ({@link PlayerData}) na waniliowy pasek XP.
     * Pakiet idzie tylko przy zmianie — puste pakiety co 0,5 s dublowaly waniliowe i nasilaly
     * migotanie paska.
     */
    private void updateHUD(Player player) {
        if (protocolManager == null) return;
        PlayerData data = plugin.getGameManager().getPlayerData(player.getUniqueId());
        if (data == null) return;

        // PlayerData trzyma 0-20, stary pasek XP znal skale 0-100 (poziom = procent pragnienia).
        int thirst = Math.round(data.getThirst() * 5f);
        Integer last = lastSentThirst.get(player.getUniqueId());
        if (last != null && last == thirst) return;
        lastSentThirst.put(player.getUniqueId(), thirst);

        try {
            PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.EXPERIENCE);
            packet.getFloat().write(0, thirst / 100.0f); // wypelnienie paska
            packet.getIntegers().write(0, thirst);       // "poziom" = procent pragnienia
            packet.getIntegers().write(1, 0);
            protocolManager.sendServerPacket(player, packet);
        } catch (Exception e) {
            plugin.getLogger().warning("[RaftHUD] Blad: " + e.getMessage());
        }
    }

    /**
     * Przywraca waniliowe zycie, glod, doswiadczenie i predkosc po wyjsciu z trybu Raft.
     * Wszystkie wartosci sa brane z gracza (prawda serwera) — nic tu nie jest wymyslane.
     */
    private void restoreVanilla(Player player) {
        try {
            if (protocolManager != null) {
                // Zycie i glod: stan, jaki gracz ma naprawde.
                PacketContainer health = protocolManager.createPacket(PacketType.Play.Server.UPDATE_HEALTH);
                health.getFloat().write(0, (float) player.getHealth());
                health.getIntegers().write(0, player.getFoodLevel());
                health.getFloat().write(1, player.getSaturation());
                protocolManager.sendServerPacket(player, health);

                // Pasek XP: byl nadpisany pragnieniem, wiec MUSI wrocic do prawdziwych
                // wartosci gracza (progress, poziom, suma) — inaczej zostaje np. poziom 100.
                PacketContainer xp = protocolManager.createPacket(PacketType.Play.Server.EXPERIENCE);
                xp.getFloat().write(0, player.getExp());
                xp.getIntegers().write(0, player.getLevel());
                xp.getIntegers().write(1, player.getTotalExperience());
                protocolManager.sendServerPacket(player, xp);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[RaftHUD] Błąd przywracania vanillowego HUD: " + e.getMessage());
        }
        // Przywróć normalną prędkość chodzenia
        player.setWalkSpeed(0.2f);
    }
}
