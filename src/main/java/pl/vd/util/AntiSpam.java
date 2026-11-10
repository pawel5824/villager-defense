package pl.vd.util;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import pl.vd.VillagerDefensePlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Antyspam dla powiadomień od serwera do gracza - wersja "na żywo".
 *
 * Krótkie powiadomienia (ostrzeżenia, punkty za zabójstwa, potwierdzenia)
 * pokazywane są w ACTION BARZE - jednej linijce nad paskiem ekwipunku,
 * którą można stale nadpisywać. Dzięki temu każda powtórka tej samej
 * treści "edytuje" tę samą linijkę, rosnąc w nawiasie:
 *   <gracz rozwala bloki>   Na arenie możesz stawiać tylko pajęczynę.
 *   <kolejne bloki>          Na arenie możesz stawiać tylko pajęczynę. (2)
 *   <kolejne bloki>          Na arenie możesz stawiać tylko pajęczynę. (3)
 *
 * Czat w Minecrafcie nie pozwala edytować wysłanych wiadomości, więc
 * "żywa" edycja możliwa jest tylko w action barze. Licznik wraca do 1,
 * gdy ta sama treść nie pojawi się przez ~4 s.
 *
 * Długie komunikaty (np. pomoc /brush) trafiają na czat - action bar
 * przyciąłby je, a na czacie są w całości i pozostają trwale.
 */
public final class AntiSpam {

    /** Po jakim czasie licznik tej samej wiadomości wraca do 1 (4 s - dłużej niż czas wyświetlania action bara). */
    private static final long COUNT_RESET_MS = 4_000L;

    /** Komunikaty dłuższe niż tyle znaków idą na czat (action bar by je przyciął). */
    private static final int CHAT_MAX_LENGTH = 55;

    /** Maksymalna długość WIDOCZNEGO tekstu w action barze - dłuższe wiadomości są przycinane przez grę,
     *  więc w ogóle ich nie wysyłamy (bez uciętego tekstu). Publiczna, żeby nadawcy
     *  mogli skracać komunikaty (np. info części stojaka) zanim je wyślą. */
    public static final int ACTION_BAR_MAX_LENGTH = 55;

    private static VillagerDefensePlugin plugin;

    /** Stan licznika per gracz: ostatnia wiadomość + ile razy pojawiła się w oknie. */
    private static class State {
        String lastMessage = "";
        int count = 0;
        long lastTime = 0L;
    }

    private static final Map<UUID, State> states = new ConcurrentHashMap<>();

    private AntiSpam() {}

    /** Wywoływane przy starcie pluginu. */
    public static void init(VillagerDefensePlugin plugin) {
        AntiSpam.plugin = plugin;
    }

    /**
     * Pokazuje wiadomość ZAWSZE w action barze (nad ekwipunkiem gracza) - nigdy
     * na czacie, bez licznika powtórzeń. Używane do powiadomień edycji stojaka
     * (aktualna część/tryb oraz potwierdzenia operacji), które mają być widoczne
     * tylko nad ekwipunkiem - bez zaśmiecania czatu.
     */
    public static void sendActionBar(Player player, String message) {
        if (player == null || message == null || message.isEmpty()) return;
        // kanał action bara per gracz (Ustawienia -> Powiadomienia): 0% = kanał
        // wyłączony, wtedy komunikat jest pomijany (bez zastępczego wysyłania na
        // czat - te wiadomości są z definicji action-barowe)
        if (!channelEnabled(player, "notif-actionbar")) return;
        // Wiadomości dłuższe niż limit action bara są przycinane przez grę -
        // nie wysyłamy ich wcale, żeby nie pokazywać uciętego tekstu.
        if (visibleLength(message) > ACTION_BAR_MAX_LENGTH) return;
        player.sendActionBar(message);
    }

    /** Długość widocznego tekstu - bez kodów kolorów (§x nie zajmuje miejsca).
     *  Publiczna, żeby nadawcy mogli sprawdzić długość przed wysłaniem. */
    public static int visibleLength(String message) {
        int len = 0;
        for (int i = 0; i < message.length(); i++) {
            char c = message.charAt(i);
            if (c == '§' && i + 1 < message.length()) {
                i++; // kod koloru: § + znak
                continue;
            }
            len++;
        }
        return len;
    }

    /**
     * Pokazuje TYTUŁ na środku ekranu (kanal „Powiadomienia na ekranie"):
     * przy wyłączonym kanale (0%) tytuł jest pomijany. Na razie gra nie wysyła
     * tytułów - helper gotowy, gdy dodasz powiadomienia ekranowe.
     */
    public static void sendTitle(Player player, String title, String subtitle) {
        if (player == null) return;
        if (!channelEnabled(player, "notif-screen")) return;
        player.sendTitle(title == null ? "" : title,
                subtitle == null ? "" : subtitle, 10, 80, 20); // 80 ticków = 4 s wyświetlania
    }

    /**
     * Pokazuje wiadomość graczowi:
     * - krótkie powiadomienia -> action bar (jedna linijka edytowana na żywo z licznikiem),
     * - długie komunikaty -> czat (w całości, trwale).
     */
    public static void send(Player player, String message) {
        if (player == null || message == null || message.isEmpty()) return;

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        // aktualizuj licznik dla tej treści (reset po ~4 s przerwy)
        State state = states.computeIfAbsent(uuid, k -> new State());
        if (state.lastMessage.equals(message) && (now - state.lastTime) < COUNT_RESET_MS) {
            state.count++;
        } else {
            state.lastMessage = message;
            state.count = 1;
        }
        state.lastTime = now;

        // Zawsze na czat
        String text = state.count > 1 ? message + " (" + state.count + ")" : message;
        player.sendMessage(Component.text(text));
    }

    /** Czy dany kanał powiadomień jest włączony dla gracza (poziom > 0)? Brak
     *  ustawienia = włączony (domyślnie 100%). */
    private static boolean channelEnabled(Player player, String key) {
        if (plugin == null) return true;
        var data = plugin.getPlayerDataManager().get(player.getUniqueId(), player.getName());
        return data.getSettingInt(key, 100) > 0;
    }
}
