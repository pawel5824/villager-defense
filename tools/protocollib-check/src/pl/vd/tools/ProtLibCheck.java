package pl.vd.tools;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Proba diagnostyczna ProtocolLib.
 *
 * Po co to jest: ProtocolLib wypisuje przy starcie "Version (MC: X) has not yet been tested",
 * gdy wersja serwera jest nowsza niz lista znanych mu wersji. To ostrzezenie nic nie mowi
 * o tym, czy biblioteka FAKTYCZNIE dziala — a od tego zalezy kilka funkcji ServerPluginu
 * (pasek pragnienia na pasku XP, wyciszanie dzwiekow mobow per gracz, filtr pakietow tratwy,
 * wyciszanie plusku splawika, pogoda).
 *
 * Ta proba sprawdza dokladnie te typy pakietow, ktorych plugin uzywa:
 *   1. czy typ pakietu sie rozwiazuje i wskazuje klase pakietu obecna na tym serwerze,
 *   2. czy da sie zbudowac i wypelnic pakiet (czyli czy mapowanie pol dziala),
 *   3. czy rejestracja i usuniecie listenera dziala,
 *   4. jesli jakis gracz jest online — czy realna wysylka pakietu przechodzi.
 *
 * Wynik: log serwera (grep ProtLibCheck logs/latest.log) i czat dla wpisujacego /protlibcheck.
 *
 * Uzycie (z katalogu glownego projektu):
 *   tools/protocollib-check.sh                        # tylko zbuduj jara
 *   tools/protocollib-check.sh "VD - serwer/plugins"  # zbuduj i wrzuc do plugins/
 */
public class ProtLibCheck extends JavaPlugin {

    /** Gdy probe uruchamia komenda, wynik idzie tez na czat (inaczej tylko do logu). */
    private CommandSender feedback;

    @Override
    public void onEnable() {
        var cmd = getCommand("protlibcheck");
        if (cmd != null) {
            cmd.setExecutor((sender, command, label, args) -> {
                feedback = sender;
                try {
                    run();
                } finally {
                    feedback = null;
                }
                return true;
            });
        }
        run();
    }

    private void log(String s) {
        getLogger().info("[ProtLibCheck] " + s);
        if (feedback != null) {
            feedback.sendMessage(Component.text("[ProtLibCheck] ").color(NamedTextColor.GRAY)
                    .append(Component.text(s)));
        }
    }

    private void run() {
        ProtocolManager pm;
        try {
            pm = ProtocolLibrary.getProtocolManager();
        } catch (Throwable t) {
            log("BLAD: brak ProtocolManagera — ProtocolLib nie dziala: " + t);
            return;
        }

        log("ProtocolLib: " + ProtocolLibrary.getPlugin().getDescription().getVersion());
        log("Serwer: " + Bukkit.getVersion());
        // PacketType.values() w ProtocolLib 5.x nie jest tablica — liczymy recznie
        int ileTypow = 0;
        for (PacketType ignored : PacketType.values()) {
            ileTypow++;
        }
        log("Typow pakietow w rejestrze ProtocolLib: " + ileTypow);

        // Dokladnie te typy, ktorych uzywa ServerPlugin.
        PacketType[] nasze = {
                PacketType.Play.Server.EXPERIENCE,         // RaftHUDManager: pragnienie na pasku XP
                PacketType.Play.Server.UPDATE_HEALTH,      // RaftHUDManager: przywrocenie zycia/glodu
                PacketType.Play.Server.ENTITY_SOUND,       // SoundMuteListener: wyciszanie mobow
                PacketType.Play.Server.NAMED_SOUND_EFFECT, // SoundMuteListener + splawik
                PacketType.Play.Server.ENTITY_TELEPORT,    // RaftPacketLOD: filtr pakietow tratwy
                PacketType.Play.Server.WORLD_PARTICLES,    // RaftHookSoundMuter: plusk splawika
                PacketType.Play.Server.GAME_STATE_CHANGE,  // WeatherUtil: pogoda na tratwie
                PacketType.Play.Server.CUSTOM_PAYLOAD      // fallback wysylek (raft:receivers itd.)
        };

        List<String> bledy = new ArrayList<>();
        for (PacketType pt : nasze) {
            try {
                log(String.format("  ok    %-42s -> %s", pt, pt.getPacketClass().getName()));
            } catch (Throwable t) {
                bledy.add(pt + " (" + t + ")");
                log("  BLAD  " + pt + " -> " + t);
            }
        }
        log("Nasze typy pakietow: " + (nasze.length - bledy.size()) + "/" + nasze.length + " OK");

        // 1) budowa i wypelnienie pakietu — cwiczy mapowanie pol (to samo robi RaftHUDManager)
        try {
            PacketContainer xp = pm.createPacket(PacketType.Play.Server.EXPERIENCE);
            xp.getFloat().write(0, 0.55f);
            xp.getIntegers().write(0, 55);
            xp.getIntegers().write(1, 0);
            log("  ok    EXPERIENCE zbudowany, odczyt pol: "
                    + xp.getFloat().read(0) + " / " + xp.getIntegers().read(0) + " / " + xp.getIntegers().read(1));
        } catch (Throwable t) {
            bledy.add("budowa EXPERIENCE: " + t);
            log("  BLAD  budowa EXPERIENCE: " + t);
        }

        // 2) wpiecie i wypiecie listenera — bez tego wyciszanie dzwiekow nie zadziala
        try {
            int przed = pm.getPacketListeners().size();
            PacketAdapter proba = new PacketAdapter(this, ListenerPriority.NORMAL,
                    PacketType.Play.Server.EXPERIENCE) {
                @Override
                public void onPacketSending(PacketEvent event) {
                    // nic nie robimy — sprawdzamy tylko, czy potok sie nie wywala
                }
            };
            pm.addPacketListener(proba);
            int po = pm.getPacketListeners().size();
            pm.removePacketListener(proba);
            log("  ok    listener: " + przed + " -> " + po + " -> " + pm.getPacketListeners().size());
        } catch (Throwable t) {
            bledy.add("listener: " + t);
            log("  BLAD  listener: " + t);
        }

        // 3) realna wysylka — tylko gdy jakis gracz jest online
        Player gracz = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (gracz == null) {
            log("Bez gracza online realnej wysylki nie da sie sprawdzic (na swiezo wstawionym "
                    + "serwerze to normalne — wejdz do gry i wpisz /protlibcheck).");
        } else {
            try {
                PacketContainer health = pm.createPacket(PacketType.Play.Server.UPDATE_HEALTH);
                health.getFloat().write(0, (float) gracz.getHealth());
                health.getIntegers().write(0, gracz.getFoodLevel());
                health.getFloat().write(1, gracz.getSaturation());
                pm.sendServerPacket(gracz, health);

                PacketContainer xp = pm.createPacket(PacketType.Play.Server.EXPERIENCE);
                xp.getFloat().write(0, gracz.getExp());
                xp.getIntegers().write(0, gracz.getLevel());
                xp.getIntegers().write(1, gracz.getTotalExperience());
                pm.sendServerPacket(gracz, xp);

                log("  ok    wyslane UPDATE_HEALTH + EXPERIENCE do " + gracz.getName()
                        + " (pasek XP gracza mogl mrugnac — to normalne)");
            } catch (Throwable t) {
                bledy.add("wysylka: " + t);
                log("  BLAD  wysylka: " + t);
            }
        }

        log(bledy.isEmpty()
                ? "WYNIK: OK — ProtocolLib dziala na tej wersji serwera"
                : "WYNIK: BLEDY (" + bledy.size() + "): " + bledy);
    }
}
