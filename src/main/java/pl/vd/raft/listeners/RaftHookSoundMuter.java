package pl.vd.raft.listeners;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Wyciszenie i odcząsteczkowanie waniliowej wędki dla graczy w świecie Raft:
 *  - dźwięki entity.fishing_bobber.* (rzut, plusk, zwinięcie) — anulowane,
 *  - particles FISHING („rybki" pływające do spławika) — anulowane.
 * Hak ma być całkowicie cichy i bez waniliowych efektów łowienia.
 * Działa przez ProtocolLib, tylko u graczy w świecie Raft.
 *
 * Bez ProtocolLib plugin działa normalnie (plusk zostaje słyszalny) — rejestracja
 * jest owinięta w try/catch (softdepend, jak SoundMuteListener).
 *
 * Uwaga: nasz WŁASNY dźwięk plusku przy nabijaniu beczki na hak używa innego
 * klucza (ENTITY_PLAYER_SPLASH), więc nie jest przez to wyciszany.
 */
public class RaftHookSoundMuter extends PacketAdapter {

    /** Zabezpieczenie przed podwójną rejestracją (np. po /def reload). */
    private static boolean registered = false;

    private final String raftWorldName;

    private RaftHookSoundMuter(org.bukkit.plugin.Plugin plugin, String raftWorldName) {
        super(plugin, ListenerPriority.NORMAL,
                PacketType.Play.Server.NAMED_SOUND_EFFECT,
                PacketType.Play.Server.WORLD_PARTICLES);
        this.raftWorldName = raftWorldName;
    }

    /** Rejestruje wyciszacz (idempotentnie — maks. raz na działanie serwera). */
    public static void register(org.bukkit.plugin.Plugin plugin, String raftWorldName) {
        if (registered) return;
        try {
            ProtocolManager pm = ProtocolLibrary.getProtocolManager();
            pm.addPacketListener(new RaftHookSoundMuter(plugin, raftWorldName));
            registered = true;
        } catch (Throwable ignored) {
            // Brak ProtocolLib / niekompatybilna wersja — plusk zostaje (fallback).
        }
    }

    @Override
    public void onPacketSending(PacketEvent event) {
        if (event.isCancelled()) return;
        Player player = event.getPlayer();
        if (player == null || !player.isOnline()) return;

        World expected = Bukkit.getWorld(raftWorldName);
        if (expected == null || player.getWorld() != expected) return;

        // Particles: waniliowe „rybki" spławika (FISHING) — serwer wysyła je
        // pakietowo do wędkującego; w świecie Raft je anulujemy (hak nie łowi).
        if (event.getPacketType() == PacketType.Play.Server.WORLD_PARTICLES) {
            try {
                var wrapper = event.getPacket().getNewParticles().read(0);
                if (wrapper != null && wrapper.getParticle() == org.bukkit.Particle.FISHING) {
                    event.setCancelled(true);
                }
            } catch (Throwable ignored) {
                // Nietypowy pakiet particles — przepuść (nie blokuj innych).
            }
            return;
        }

        PacketContainer packet = event.getPacket();
        Sound sound;
        try {
            sound = packet.getSoundEffects().read(0);
        } catch (Throwable t) {
            return; // nietypowy holder dźwięku — przepuść
        }
        // Hak ma być CAŁKOWICIE cichy: wyciszamy WSZYSTKIE waniliowe dźwięki
        // spławika — rzut (throw), plusk o wodę (splash) i zwinięcie (retrieve).
        if (sound == Sound.ENTITY_FISHING_BOBBER_SPLASH
                || sound == Sound.ENTITY_FISHING_BOBBER_THROW
                || sound == Sound.ENTITY_FISHING_BOBBER_RETRIEVE) {
            event.setCancelled(true);
        }
    }
}
