package pl.vd.listeners;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import pl.vd.VillagerDefensePlugin;
import pl.vd.type.Faction;

/**
 * Per-graczowe wyciszanie dźwięków (wymaga ProtocolLib).
 *
 * 1) Dźwięki mobów z fal: dźwięki mobów (ambient, obrażenia, śmierć, atak)
 *    idą do graczy jako pakiety „entity sound" (Play.Server.ENTITY_SOUND)
 *    z ID encji, która je wydaje. Anulujemy te, których źródłem jest mob z fali
 *    (metadane vd-arena), gdy ODBIORCA ma wyłączone „Moby z fal".
 * 2) Wybuchy (miny lądowe, TNT itd.): to pakiety „named sound"
 *    (Play.Server.NAMED_SOUND_EFFECT) z kluczem dźwięku. Anulujemy wybuchowe
 *    dźwięki (entity/block.generic.explode), gdy odbiorca ma wyłączone „Wybuchy".
 *
 * Wszystko działa TYLKO dla wybranego gracza - pozostali słyszą dźwięki normalnie.
 */
public class SoundMuteListener extends PacketAdapter {

    private static final String SOUND_MOBS_KEY = "sound-mobs";
    private static final String SOUND_EXPLOSIONS_KEY = "sound-explosions";
    private static final String SOUND_ALLIES_KEY = "sound-allies";
    private final VillagerDefensePlugin vdPlugin;

    public SoundMuteListener(VillagerDefensePlugin plugin) {
        super(plugin, ListenerPriority.NORMAL,
                PacketType.Play.Server.ENTITY_SOUND,
                PacketType.Play.Server.NAMED_SOUND_EFFECT);
        this.vdPlugin = plugin;
    }

    @Override
    public void onPacketSending(PacketEvent event) {
        if (event.isCancelled()) return;
        Player player = event.getPlayer();
        if (player == null || !player.isOnline()) return;

        if (event.getPacketType() == PacketType.Play.Server.ENTITY_SOUND) {
            handleEntitySound(event, player);
        } else if (event.getPacketType() == PacketType.Play.Server.NAMED_SOUND_EFFECT) {
            handleNamedSound(event, player);
        }
    }

    /** Dźwięki encji: głośność % mobów z fal („Moby z fal") i sojuszników
     *  („Sojusznicy" - warden, bałwan, wilk, golem, obrońca). 0% = pakiet
     *  anulowany, 25/50/75% = głośność przytłumiona, 100% = bez zmian. */
    private void handleEntitySound(PacketEvent event, Player player) {
        int mobsVol = vdPlugin.getGameManager().soundVolume(player, SOUND_MOBS_KEY);
        int alliesVol = vdPlugin.getGameManager().soundVolume(player, SOUND_ALLIES_KEY);
        if (mobsVol >= 100 && alliesVol >= 100) return; // wszystko słyszalne - przepuść

        // ENTITY_SOUND (ClientboundSoundEntityPacket): pole int [0] = ID encji
        // wydającej dźwięk
        int entityId = event.getPacket().getIntegers().read(0);
        Entity entity = ProtocolLibrary.getProtocolManager()
                .getEntityFromID(player.getWorld(), entityId);
        if (!(entity instanceof LivingEntity le)) return;

        int vol = -1;
        if (Faction.of(le).isWave()) {
            vol = mobsVol; // mob z fali areny (wieśniacy mają te same metadane, więc Faction ich nie dotyka)
        } else if (Faction.of(le) == Faction.FRIENDLY) {
            vol = alliesVol; // sojusznik przywołany przez gracza (tag vd_sojusz)
        }
        if (vol < 0) return; // inna encja - nie dotykamy
        if (vol <= 0) {
            event.setCancelled(true);
            return;
        }
        // przytłumij do X%: float [0] = głośność pakietu
        PacketContainer packet = event.getPacket();
        packet.getFloat().write(0, packet.getFloat().read(0) * vol / 100f);
    }

    /** Dźwięki pozycyjne: głośność % wybuchów (ustawienie „Wybuchy"). */
    private void handleNamedSound(PacketEvent event, Player player) {
        int vol = vdPlugin.getGameManager().soundVolume(player, SOUND_EXPLOSIONS_KEY);
        if (vol >= 100) return; // pełna głośność - przepuść
        PacketContainer packet = event.getPacket();
        Sound sound = packet.getSoundEffects().read(0);
        // w tej wersji Paper wybuchy grają jako entity.generic.explode
        if (sound != Sound.ENTITY_GENERIC_EXPLODE) return;
        if (vol <= 0) {
            event.setCancelled(true);
            return;
        }
        // przytłumij do X%: float [0] = głośność pakietu
        packet.getFloat().write(0, packet.getFloat().read(0) * vol / 100f);
    }
}
