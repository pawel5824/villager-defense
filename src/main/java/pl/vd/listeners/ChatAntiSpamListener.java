package pl.vd.listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.scheduler.BukkitTask;
import pl.vd.VillagerDefensePlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Antyspam - scalanie powtórek:
 * Jeśli gracz napisze kilka razy tę samą wiadomość, zamiast wielu linii
 * na czacie pojawia się JEDNA linia z licznikiem:
 *   <gracz> a          (napisał raz - bez licznika, natychmiast)
 *   <gracz> a (3)      (napisał 3 razy - jedna scalona linia z licznikiem)
 *
 * WAŻNE: pierwsza wiadomość (inna niż poprzednia) leci NATYCHMIAST, waniliową
 * drogą - nie jest anulowana. Okno scalania (~2 s) dotyczy WYŁĄCZNIE powtórek
 * tej samej treści: dopóki gracz powtarza to samo, wiadomości są wstrzymywane,
 * a po 2 s ciszy wysyłana jest jedna linia z licznikiem. Wiadomość innej treści
 * wysyła od razu ewentualną wstrzymaną powtórkę i sama idzie bez opóźnienia.
 * (Wcześniej KAŻDA wiadomość czekała 2 s na ewentualne powtórki - gracz widział
 * własną wiadomość dopiero po paru sekundach.)
 */
public class ChatAntiSpamListener implements Listener {

    /** Okno scalania - ile ticków czekamy, zanim wyślemy wiadomość (40 = 2 s). */
    private static final long MERGE_DELAY_TICKS = 40L;

    /** Po jakim czasie licznik tej samej wiadomości wraca do zera (60 s). */
    private static final long COUNT_RESET_MS = 60_000L;

    private final VillagerDefensePlugin plugin;

    /** Trwały stan licznika per gracz: ostatnia wiadomość + ile razy ją napisał. */
    private static class ChatState {
        String lastMessage = "";
        int count = 0;
        long lastTime = 0L;
    }

    /** Wiadomość wstrzymana, czekająca na ewentualne powtórki. */
    private static class Pending {
        final UUID uuid;
        final Player player;
        final String format;
        final String message;
        int count;
        BukkitTask task;

        Pending(Player player, String format, String message, int count) {
            this.uuid = player.getUniqueId();
            this.player = player;
            this.format = format;
            this.message = message;
            this.count = count;
        }
    }

    private final Map<UUID, ChatState> states = new ConcurrentHashMap<>();
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public ChatAntiSpamListener(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage().trim();
        if (message.isEmpty()) return;

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        // 1. aktualizuj trwały licznik dla tej wiadomości
        ChatState state = states.computeIfAbsent(uuid, k -> new ChatState());
        boolean repeat = state.lastMessage.equalsIgnoreCase(message)
                && (now - state.lastTime) < COUNT_RESET_MS;
        if (repeat) {
            state.count++;
        } else {
            state.lastMessage = message;
            state.count = 1;
        }
        state.lastTime = now;

        // 2. NOWA wiadomość (nie powtórka): leci NATYCHMIAST, bez okna scalania.
        //    Jeśli czekała scalona powtórka poprzedniej treści, wyślij ją teraz
        //    (kolejność linii na czacie zostaje zachowana).
        if (!repeat) {
            Pending p = pending.remove(uuid);
            if (p != null) {
                p.task.cancel();
                broadcast(p);
            }
            return; // event NIE jest anulowany - wanilia pokazuje linię od razu
        }

        // 3. POWTÓRKA tej samej treści: wstrzymaj na okno scalania - po 2 s ciszy
        //    gracz zobaczy jedną linię z licznikiem, zamiast wielu identycznych.
        event.setCancelled(true);

        Pending p = pending.get(uuid);
        if (p != null && p.message.equalsIgnoreCase(message)) {
            // ta sama wiadomość - licznik rośnie, okno scalania się przedłuża
            p.count = state.count;
            p.task.cancel();
            p.task = scheduleBroadcast(p);
            return;
        }
        if (p != null) {
            // wstrzymana inna treść (np. po zmianie licznika) - wyślij ją od razu
            p.task.cancel();
            pending.remove(uuid);
            broadcast(p);
        }

        // 4. wstrzymaj powtórkę
        Pending np = new Pending(player, event.getFormat(), message, state.count);
        np.task = scheduleBroadcast(np);
        pending.put(uuid, np);
    }

    private BukkitTask scheduleBroadcast(Pending p) {
        return Bukkit.getScheduler().runTaskLater(plugin, () -> broadcast(p), MERGE_DELAY_TICKS);
    }

    private void broadcast(Pending p) {
        pending.remove(p.uuid);
        if (!p.player.isOnline()) return; // gracz wyszedł - nie wysyłamy
        // pierwsza wiadomość bez licznika, powtórki z licznikiem: "a (3)"
        String text = p.count > 1 ? p.message + " (" + p.count + ")" : p.message;
        try {
            String line = String.format(p.format, p.player.getDisplayName(), text);
            Bukkit.broadcastMessage(line);
        } catch (Throwable ignored) {
            Bukkit.broadcastMessage("<" + p.player.getName() + "> " + text);
        }
    }
}
