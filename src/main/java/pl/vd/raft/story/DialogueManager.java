package pl.vd.raft.story;

import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import pl.vd.raft.RaftPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Zarządza dialogami fabularnymi — pierwotnie wyświetlanymi w chacie.
 * Obsuguje sekwencje tekstu z opóźnieniami.
 *
 * Uwaga: od czasu zasady „żadnych komunikatów" (AGENTS.md pkt 1) nic tu nie
 * trafia do gracza — sekwencje nadal istnieją, ale są wyciszone. Fabuła jest
 * zresztą nieosiągalna (nikt nie woła playDialogue poza tym pakietem).
 */
public class DialogueManager {

    private final RaftPlugin plugin;
    private final Map<String, DialogueSequence> sequences = new HashMap<>();
    private final Map<java.util.UUID, Boolean> activeDialogues = new HashMap<>();

    public DialogueManager(RaftPlugin plugin) {
        this.plugin = plugin;
        initializeSequences();
    }

    /**
     * Inicjalizuje wszystkie sekwencje dialogowe.
     */
    private void initializeSequences() {
        // === PROLOG ===
        DialogueSequence prologue = new DialogueSequence("prologue");
        prologue.addLine("", 20);
        prologue.addLine("§6TAJEMNICA ZAGUBIONEGO OCEANU", 10);
        prologue.addLine("", 10);
        prologue.addLine("§7\"Ocean to cmentarzysko starożytnej cywilizacji...\"", 20);
        prologue.addLine("§7\"Ich bóstwo budzi się z letargu...\"", 20);
        prologue.addLine("§7\"Musisz znaleźć sposób, by je powstrzymać.\"", 20);
        prologue.addLine("", 0);
        sequences.put("prologue", prologue);

        // === ROZDZIAŁ 1 ===
        DialogueSequence chapter1 = new DialogueSequence("chapter1");
        chapter1.addLine("", 20);
        chapter1.addLine("§6ROZDZIAŁ I: Budzenie się", 10);
        chapter1.addLine("", 10);
        chapter1.addLine("§7Budzisz się na małym pontonie pośrodku oceanu.", 20);
        chapter1.addLine("§7Nie pamiętasz, jak się tu znalazłeś.", 20);
        chapter1.addLine("§7Wokoło tylko woda i skrawki lądu w oddali.", 20);
        chapter1.addLine("", 10);
        chapter1.addLine("§eTwój cel: Przetrwać.", 15);
        chapter1.addLine("", 0);
        sequences.put("chapter1", chapter1);

        // === SPOTKANIE Z NPC ===
        DialogueSequence firstNPC = new DialogueSequence("firstNPC");
        firstNPC.addLine("", 15);
        firstNPC.addLine("§6Spotkanie na plaży", 10);
        firstNPC.addLine("", 10);
        firstNPC.addLine("§eNieznajomy: §fHej! Kim jesteś? Jak się tu znalazłeś?", 20);
        firstNPC.addLine("§eNieznajomy: §fTeż straciłeś pamięć?", 20);
        firstNPC.addLine("§eNieznajomy: §fMusimy współpracować, jeśli chcemy przetrwać.", 20);
        firstNPC.addLine("", 0);
        sequences.put("firstNPC", firstNPC);

        // === ODKRYCIE TAJEMNICY ===
        DialogueSequence mystery = new DialogueSequence("mystery");
        mystery.addLine("", 15);
        mystery.addLine("§6Tajemnica oceanu", 10);
        mystery.addLine("", 10);
        mystery.addLine("§7Zaczynasz odkrywać, że ocean skrywa tajemnice.", 20);
        mystery.addLine("§7Stworzenia z głębin, starożytne ruiny, zaginione skarby.", 20);
        mystery.addLine("§7Coś wielkiego czeka na dnie...", 20);
        mystery.addLine("", 0);
        sequences.put("mystery", mystery);

        // === POJAWIENIE SIĘ BOSSA ===
        DialogueSequence bossIntro = new DialogueSequence("bossIntro");
        bossIntro.addLine("", 15);
        bossIntro.addLine("§4UWAŻAJ!", 10);
        bossIntro.addLine("", 10);
        bossIntro.addLine("§7Z głębin wypływa potworne stworzenie...", 20);
        bossIntro.addLine("§7To strażnik oceanicznych tajemnic!", 20);
        bossIntro.addLine("", 0);
        sequences.put("bossIntro", bossIntro);

        // === POKONANIE BOSSA ===
        DialogueSequence bossDefeated = new DialogueSequence("bossDefeated");
        bossDefeated.addLine("", 15);
        bossDefeated.addLine("§6Zwycięstwo!", 10);
        bossDefeated.addLine("", 10);
        bossDefeated.addLine("§7Pokonałeś strażnika oceanu!", 20);
        bossDefeated.addLine("§7Z jego szczątków wypływają starożytne artefakty...", 20);
        bossDefeated.addLine("", 0);
        sequences.put("bossDefeated", bossDefeated);

        // === KOŃCE GRY ===
        DialogueSequence ending = new DialogueSequence("ending");
        ending.addLine("", 15);
        ending.addLine("§6KONIEC PODRÓŻY", 10);
        ending.addLine("", 10);
        ending.addLine("§7Przetrwałeś wszystkie trudności oceanu.", 20);
        ending.addLine("§7Odkryłeś prawdę o starożytnej cywilizacji.", 20);
        ending.addLine("§7Twoja podróż dobiega końca...", 20);
        ending.addLine("", 10);
        ending.addLine("§6Dziękujemy za grę!", 0);
        sequences.put("ending", ending);
    }

    /**
     * Wyświetla sekwencję dialogową dla gracza.
     */
    public void playDialogue(Player player, String sequenceId) {
        DialogueSequence sequence = sequences.get(sequenceId);
        if (sequence == null) {
            plugin.getLogger().warning("Nie znaleziono sekwencji dialogowej: " + sequenceId);
            return;
        }

        // Sprawdź czy gracz nie ma już aktywnego dialogu
        if (activeDialogues.getOrDefault(player.getUniqueId(), false)) {
            return;
        }

        activeDialogues.put(player.getUniqueId(), true);
        playSequence(player, sequence, 0);
    }

    /**
     * Odtwarza sekwencję linia po linii z opóźnieniami.
     */
    private void playSequence(Player player, DialogueSequence sequence, int lineIndex) {
        if (lineIndex >= sequence.getLines().size()) {
            // Koniec dialogu
            activeDialogues.put(player.getUniqueId(), false);
            return;
        }

        DialogueLine line = sequence.getLines().get(lineIndex);
        int delay = line.getDelay();

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    activeDialogues.put(player.getUniqueId(), false);
                    cancel();
                    return;
                }

                // Bez wypisywania linii (AGENTS.md pkt 1) — celowo.

                // Następna linia
                playSequence(player, sequence, lineIndex + 1);
            }
        }.runTaskLater(plugin.getParent(), delay);
    }

    /**
     * Wyświetla prosty dialog (pojedyncza linia).
     */
    public void sendDialogueLine(Player player, String speaker, String text) {
        // Bez komunikatu na czacie (AGENTS.md pkt 1) — celowo.
    }

    /**
     * Wyświetla nagłówek w chacie.
     */
    public void sendChatTitle(Player player, String title, String subtitle) {
        // Bez komunikatu na czacie (AGENTS.md pkt 1) — celowo.
    }

    /**
     * Wyświetla systemową wiadomość fabularną.
     */
    public void sendStoryMessage(Player player, String message) {
        // Bez komunikatu na czacie (AGENTS.md pkt 1) — celowo.
    }

    /**
     * Wyświetla wiadomość o nowym questcie.
     */
    public void sendQuestNotification(Player player, String questName) {
        // Bez komunikatu na czacie (AGENTS.md pkt 1) — celowo.
    }

    /**
     * Wyświetla wiadomość o odblokowaniu.
     */
    public void sendUnlockMessage(Player player, String content) {
        // Bez komunikatu na czacie (AGENTS.md pkt 1) — celowo.
    }

    /**
     * Sprawdza czy gracz ma aktywny dialog.
     */
    public boolean hasActiveDialogue(Player player) {
        return activeDialogues.getOrDefault(player.getUniqueId(), false);
    }

    /**
     * Przerywa aktywny dialog gracza.
     */
    public void cancelDialogue(Player player) {
        activeDialogues.put(player.getUniqueId(), false);
    }

    /**
     * Czyści dane gracza (przy wyjściu z gry).
     */
    public void clearPlayerData(java.util.UUID uuid) {
        activeDialogues.remove(uuid);
    }

    /**
     * Pobiera wszystkie dostępne sekwencje.
     */
    public Map<String, DialogueSequence> getSequences() {
        return sequences;
    }

    /**
     * Rejestruje nową sekwencję dialogową.
     */
    public void registerSequence(String id, DialogueSequence sequence) {
        sequences.put(id, sequence);
    }

    // === Klasy wewnętrzne ===

    /**
     * Sekwencja dialogowa składająca się z wielu linii.
     */
    public static class DialogueSequence {
        private final String id;
        private final List<DialogueLine> lines = new ArrayList<>();

        public DialogueSequence(String id) {
            this.id = id;
        }

        public void addLine(String text, int delay) {
            lines.add(new DialogueLine(text, delay));
        }

        public String getId() { return id; }
        public List<DialogueLine> getLines() { return lines; }
    }

    /**
     * Pojedyncza linia dialogu.
     */
    public static class DialogueLine {
        private final String text;
        private final int delay; // w tickach

        public DialogueLine(String text, int delay) {
            this.text = text;
            this.delay = delay;
        }

        public String getText() { return text; }
        public int getDelay() { return delay; }
    }
}
