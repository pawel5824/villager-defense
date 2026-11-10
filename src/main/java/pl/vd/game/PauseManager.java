package pl.vd.game;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import pl.vd.VillagerDefensePlugin;
import pl.vd.arena.Arena;

import java.util.*;

/**
 * Zarządza systemem przerw w grze.
 *
 * Mechanika:
 *  - Gracz wpisuje /przerwa <czas> → startsuje głosowanie
 *  - Większość graczy musi zagłosować „tak" (głosujący TAK >= połowa graczy)
 *  - Jeśli na arenie jest 1 gracz → automatyczna aktywacja bez głosowania
 *  - Maks. 3 przerwy na całą grę na arenie
 *  - Dopuszczalne czasy: 1, 5, 10, 20, 30 minut
 *  - Podczas przerwy: moby zamrożone (bez AI/ruchu), gracze zamrożeni (slow 255),
 *    wieżyczki nie strzelają
 *  - Po upływie czasu gra wznawia się automatycznie
 */
public class PauseManager {

    private static final int MAX_PAUSES_PER_GAME = 3;

    /** Dopuszczalne czasy przerwy w sekundach. */
    private static final Set<Integer> ALLOWED_DURATIONS = Set.of(60, 300, 600, 1200, 1800);

    /** Mapowanie czasu (sekundy) na czytelny label. */
    private static String durationLabel(int seconds) {
        return switch (seconds) {
            case 60 -> "1 minuta";
            case 300 -> "5 minut";
            case 600 -> "10 minut";
            case 1200 -> "20 minut";
            case 1800 -> "30 minut";
            default -> seconds + "s";
        };
    }

    private final VillagerDefensePlugin plugin;

    // === Stan per arena ===
    /** Liczba użytych przerw w trwającej grze (resetowane po endGame). */
    private final Map<String, Integer> usedPauses = new HashMap<>();

    /** Czy głosowanie jest aktywne dla areny. */
    private final Map<String, VoteState> activeVotes = new HashMap<>();

    /** Czy przerwa jest aktywna dla areny. */
    private final Map<String, PauseState> activePauses = new HashMap<>();

    /** Gracze, którzy zagłosowali TAK w bieżącym głosowaniu. */
    private final Map<String, Set<UUID>> voteYes = new HashMap<>();

    /** Gracze, którzy zagłosowali NIE w bieżącym głosowaniu. */
    private final Map<String, Set<UUID>> voteNo = new HashMap<>();

    // === Głosowanie na ANULOWANIE przerwy ===
    private final Map<String, Boolean> activeCancelVotes = new HashMap<>();
    private final Map<String, Set<UUID>> cancelVoteYes = new HashMap<>();
    private final Map<String, Set<UUID>> cancelVoteNo = new HashMap<>();

    /** Zapisana walk speed graczy PRZED zamrożeniem (float). */
    private final Map<UUID, Float> savedWalkSpeed = new HashMap<>();

    /** Zapisana Boolean flaga latania graczy PRZED zamrożeniem. */
    private final Map<UUID, Boolean> savedFlying = new HashMap<>();

    public PauseManager(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
    }

    // ====================== GŁOSOWANIE ======================

    /**
     * Inicjuje głosowanie na przerwę. Zwraca komunikat o wyniku.
     * @return true jeśli głosowanie się rozpoczęło
     */
    public boolean startVote(Player player, Arena arena, int durationSeconds) {
        String arenaName = arena.getName();

        // sprawdź limit
        int used = usedPauses.getOrDefault(arenaName, 0);
        if (used >= MAX_PAUSES_PER_GAME) {
            player.sendMessage(Component.text("§cArena wykorzystała już limit " + MAX_PAUSES_PER_GAME + " przerw w tej grze."));
            return false;
        }

        // sprawdź czy już jest przerwa lub głosowanie
        if (activePauses.containsKey(arenaName)) {
            player.sendMessage(Component.text("§cPrzerwa jest już aktywna na tej arenie."));
            return false;
        }
        if (activeVotes.containsKey(arenaName)) {
            player.sendMessage(Component.text("§cGłosowanie na przerwę już trwa."));
            return false;
        }

        // sprawdź czas
        if (!ALLOWED_DURATIONS.contains(durationSeconds)) {
            player.sendMessage(Component.text("§cDopuszczalne czasy: §f1, 5, 10, 20, 30 §cminut."));
            return false;
        }

        List<UUID> players = arena.getPlayers();

        // 1 gracz = automatyczna przerwa
        if (players.size() <= 1) {
            activatePause(arena, durationSeconds, player.getName());
            return true;
        }

        // start głosowania
        VoteState vote = new VoteState(durationSeconds);
        activeVotes.put(arenaName, vote);
        Set<UUID> yes = new HashSet<>();
        Set<UUID> no = new HashSet<>();
        yes.add(player.getUniqueId()); // inicjujący automatycznie głosuje TAK
        voteYes.put(arenaName, yes);
        voteNo.put(arenaName, no);

        broadcastVote(arena, player.getName(), durationSeconds);
        broadcastVoteProgress(arena);
        return true;
    }

    /**
     * Głosuje TAK lub NIE w aktywnym głosowaniu na arenie.
     */
    public boolean vote(Player player, Arena arena, boolean accept) {
        String arenaName = arena.getName();
        VoteState vote = activeVotes.get(arenaName);
        if (vote == null) {
            player.sendMessage(Component.text("§cNie ma aktywnego głosowania na przerwę."));
            return false;
        }

        Set<UUID> yes = voteYes.get(arenaName);
        Set<UUID> no = voteNo.get(arenaName);

        if (yes.contains(player.getUniqueId()) || no.contains(player.getUniqueId())) {
            player.sendMessage(Component.text("§cJuż zagłosowałeś."));
            return false;
        }

        if (accept) {
            yes.add(player.getUniqueId());
            player.sendMessage(Component.text("§aZagłosowałeś za przerwą."));
        } else {
            no.add(player.getUniqueId());
            player.sendMessage(Component.text("§cZagłosowałeś przeciwko przerwie."));
        }

        broadcastVoteProgress(arena);

        // sprawdź czy majority już osiągnięto
        int totalPlayers = arena.getPlayers().size();
        int majority = (totalPlayers / 2) + 1;

        if (yes.size() >= majority) {
            // głosowanie przeszło
            activeVotes.remove(arenaName);
            voteYes.remove(arenaName);
            voteNo.remove(arenaName);
            activatePause(arena, vote.durationSeconds, null);
        } else if (no.size() >= majority) {
            // głosowanie odrzucone (nie da się osiągnąć majority TAK)
            activeVotes.remove(arenaName);
            voteYes.remove(arenaName);
            voteNo.remove(arenaName);
            broadcastChat(arena, "§cGłosowanie na przerwę zostało odrzucone.");
        }

        return true;
    }

    // ====================== GŁOSOWANIE NA ANULOWANIE ======================

    /**
     * Startuje głosowanie na anulowanie aktywnej przerwy.
     */
    public boolean startCancelVote(Player player, Arena arena) {
        String arenaName = arena.getName();

        if (!activePauses.containsKey(arenaName)) {
            player.sendMessage(Component.text("§cNie ma aktywnej przerwy do anulowania."));
            return false;
        }

        if (activeCancelVotes.containsKey(arenaName)) {
            player.sendMessage(Component.text("§cGłosowanie na anulowanie już trwa."));
            return false;
        }

        List<UUID> players = arena.getPlayers();

        // 1 gracz = automatyczne anulowanie
        if (players.size() <= 1) {
            endPause(arena);
            broadcastChat(arena, "§6⏵ §ePrzerwa anulowana automatycznie (1 gracz na arenie).");
            return true;
        }

        // start głosowania
        activeCancelVotes.put(arenaName, true);
        Set<UUID> yes = new HashSet<>();
        Set<UUID> no = new HashSet<>();
        yes.add(player.getUniqueId()); // inicjujący automatycznie głosuje TAK
        cancelVoteYes.put(arenaName, yes);
        cancelVoteNo.put(arenaName, no);

        broadcastCancelVote(arena, player.getName());
        broadcastCancelVoteProgress(arena);
        return true;
    }

    /**
     * Głosuje TAK lub NIE w głosowaniu na anulowanie przerwy.
     */
    public boolean voteCancel(Player player, Arena arena, boolean accept) {
        String arenaName = arena.getName();
        if (!activeCancelVotes.containsKey(arenaName)) {
            player.sendMessage(Component.text("§cNie ma aktywnego głosowania na anulowanie."));
            return false;
        }

        Set<UUID> yes = cancelVoteYes.get(arenaName);
        Set<UUID> no = cancelVoteNo.get(arenaName);

        if (yes.contains(player.getUniqueId()) || no.contains(player.getUniqueId())) {
            player.sendMessage(Component.text("§cJuż zagłosowałeś."));
            return false;
        }

        if (accept) {
            yes.add(player.getUniqueId());
            player.sendMessage(Component.text("§aZagłosowałeś za anulowaniem przerwy."));
        } else {
            no.add(player.getUniqueId());
            player.sendMessage(Component.text("§cZagłosowałeś przeciwko anulowaniu."));
        }

        broadcastCancelVoteProgress(arena);

        int totalPlayers = arena.getPlayers().size();
        int majority = (totalPlayers / 2) + 1;

        if (yes.size() >= majority) {
            activeCancelVotes.remove(arenaName);
            cancelVoteYes.remove(arenaName);
            cancelVoteNo.remove(arenaName);
            endPause(arena);
            broadcastChat(arena, "§6⏵ §ePrzerwa anulowana przez głosowanie!");
        } else if (no.size() >= majority) {
            activeCancelVotes.remove(arenaName);
            cancelVoteYes.remove(arenaName);
            cancelVoteNo.remove(arenaName);
            broadcastChat(arena, "§cGłosowanie na anulowanie przerwy zostało odrzucone.");
        }

        return true;
    }

    private void broadcastCancelVote(Arena arena, String initiatorName) {
        broadcastChat(arena, "§6🗳 §e" + initiatorName + " §7głosuje za anulowaniem przerwy!");

        Component yesBtn = Component.text("§a§l✓")
                .clickEvent(ClickEvent.runCommand("/przerwa anuluj tak"))
                .hoverEvent(HoverEvent.showText(Component.text("§aTak - anuluj przerwę")));
        Component noBtn = Component.text("§c§l✗")
                .clickEvent(ClickEvent.runCommand("/przerwa anuluj nie"))
                .hoverEvent(HoverEvent.showText(Component.text("§cNie - zostaw przerwę")));
        Component spacer = Component.text("  ");

        for (UUID uuid : arena.getPlayers()) {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p != null) p.sendMessage(yesBtn.append(spacer).append(noBtn));
        }
    }

    private void broadcastCancelVoteProgress(Arena arena) {
        String arenaName = arena.getName();
        Set<UUID> yes = cancelVoteYes.getOrDefault(arenaName, Set.of());
        int totalPlayers = arena.getPlayers().size();
        int majority = (totalPlayers / 2) + 1;
        broadcastChat(arena, "§7Głosy na anulowanie: §aTAK §f" + yes.size() + "§7/§f" + majority
                + " §7potrzebnych (graczy: §f" + totalPlayers + "§7)");
    }

    // ====================== AKTYWACJA PRZERWY ======================

    private void activatePause(Arena arena, int durationSeconds, String initiatorName) {
        String arenaName = arena.getName();

        // zwiększ licznik
        usedPauses.merge(arenaName, 1, Integer::sum);
        int remaining = MAX_PAUSES_PER_GAME - usedPauses.getOrDefault(arenaName, 0);

        // zapisz stan i aktywuj
        PauseState state = new PauseState(durationSeconds, System.currentTimeMillis());
        activePauses.put(arenaName, state);

        // zamroź
        freezeArena(arena);

        // broadcast
        String initiator = initiatorName != null ? initiatorName : "System";
        broadcastChat(arena, "§6⏸ §ePrzerwa §f" + durationLabel(durationSeconds)
                + " §eaktywowana przez §f" + initiator + "§e!");
        broadcastChat(arena, "§7Pozostałe przerwy: §f" + remaining + "§7/§f" + MAX_PAUSES_PER_GAME);

        // title licznik (wymuszony na ekranie gracza)
        updatePauseTitle(arena, durationSeconds);

        // countdown timer
        startPauseTimer(arena, durationSeconds);
    }

    // ====================== TIMER ======================

    private void startPauseTimer(Arena arena, int totalSeconds) {
        String arenaName = arena.getName();
        int[] remaining = {totalSeconds};

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (arena.getState() != pl.vd.arena.ArenaState.INGAME || !activePauses.containsKey(arenaName)) {
                    cancel();
                    return;
                }

                int left = remaining[0];
                // licznik TYLKO na ekranie gracza (wymuszony title, nie do wyłączenia)
                updatePauseTitle(arena, left);

                remaining[0]--;
                if (remaining[0] <= 0) {
                    cancel();
                    endPause(arena);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);

        state().timerTask = task;
    }

    private PauseState state() {
        return null; // helper placeholder - see actual usage
    }

    // ====================== KONIEC PRZERWY ======================

    /**
     * Kończy aktywną przerwę i wznawia grę.
     */
    public void endPause(Arena arena) {
        String arenaName = arena.getName();
        PauseState state = activePauses.remove(arenaName);
        if (state == null) return;

        // anuluj timer
        if (state.timerTask != null) state.timerTask.cancel();

        // odmroź
        unfreezeArena(arena);

        broadcastChat(arena, "§a▶ §ePrzerwa zakończona! Gra wznawia się!");
    }

    // ====================== ZAMRAŻANIE / ODMRAŻANIE ======================

    private void freezeArena(Arena arena) {
        // zamroź graczy
        for (UUID uuid : arena.getPlayers()) {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p == null) continue;
            freezePlayer(p);
        }

        // zamroź moby (slowness 255 + no AI)
        for (LivingEntity mob : arena.getSpawnedMobs()) {
            if (mob == null || !mob.isValid() || mob.isDead()) continue;
            freezeMob(mob);
        }

        // zamroź wieśniaków
        for (Villager v : arena.getLivingVillagers()) {
            if (v == null || !v.isValid() || v.isDead()) continue;
            freezeMob(v);
        }

        // zatrzymaj wieżyczki (ArmorStandTuner)
        plugin.getArmorStandTuner().pauseAllTurrets();
    }

    private void unfreezeArena(Arena arena) {
        // odmroź graczy
        for (UUID uuid : arena.getPlayers()) {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p == null) continue;
            unfreezePlayer(p);
        }

        // odmroź moby
        for (LivingEntity mob : arena.getSpawnedMobs()) {
            if (mob == null || !mob.isValid() || mob.isDead()) continue;
            unfreezeMob(mob);
        }

        // odmroź wieśniaków
        for (Villager v : arena.getLivingVillagers()) {
            if (v == null || !v.isValid() || v.isDead()) continue;
            unfreezeMob(v);
        }

        // wznow wieżyczki
        plugin.getArmorStandTuner().resumeAllTurrets();
    }

    private void freezePlayer(Player player) {
        // zapisz stan
        savedWalkSpeed.put(player.getUniqueId(), player.getWalkSpeed());
        savedFlying.put(player.getUniqueId(), player.isFlying());

        // zamroź ruch
        player.setWalkSpeed(0.0f);
        player.setFlySpeed(0.0f);
        player.setAllowFlight(true);
        player.setFlying(true);
        // camera lock - spowolnienie (Slowness 255 + Jump Disable)
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, Integer.MAX_VALUE, 255, false, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, Integer.MAX_VALUE, 254, false, false, false));
    }

    private void unfreezePlayer(Player player) {
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.removePotionEffect(PotionEffectType.JUMP_BOOST);

        // przywróć zapisany stan
        Float savedSpeed = savedWalkSpeed.remove(player.getUniqueId());
        Boolean wasFlying = savedFlying.remove(player.getUniqueId());

        player.setWalkSpeed(savedSpeed != null ? savedSpeed : 0.2f);
        player.setFlySpeed(0.1f);
        if (wasFlying != null && wasFlying) {
            player.setAllowFlight(true);
            player.setFlying(true);
        } else {
            player.setAllowFlight(false);
            player.setFlying(false);
        }
    }

    private void freezeMob(LivingEntity mob) {
        mob.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, Integer.MAX_VALUE, 255, false, false, false));
        mob.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, Integer.MAX_VALUE, 254, false, false, false));
        mob.setAI(false);
        mob.setGravity(false);
        mob.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
    }

    private void unfreezeMob(LivingEntity mob) {
        mob.removePotionEffect(PotionEffectType.SLOWNESS);
        mob.removePotionEffect(PotionEffectType.JUMP_BOOST);
        mob.setAI(true);
        mob.setGravity(true);
    }

    // ====================== ACTION BAR (licznik przerwy) ======================

    /** Wymuszony tytuł na ekranie — sam licznik odliczania przerwy. */
    private void updatePauseTitle(Arena arena, int secondsLeft) {
        int left = Math.max(0, secondsLeft);
        String timeStr = formatTime(left);
        for (UUID uuid : arena.getPlayers()) {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p != null) {
                p.sendTitle("§f" + timeStr, "", 0, 25, 0);
            }
        }
    }

    // ====================== BROADCAST ======================

    private void broadcastChat(Arena arena, String message) {
        for (UUID uuid : arena.getPlayers()) {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p != null) p.sendMessage(Component.text(message));
        }
    }

    private void broadcastVote(Arena arena, String initiatorName, int durationSeconds) {
        broadcastChat(arena, "§6🗳 §e" + initiatorName + " §7głosuje za przerwą §f" + durationLabel(durationSeconds) + "§7!");

        // klikalne przyciski: ✓ (zielony bold) i ✗ (czerwony bold)
        Component yesBtn = Component.text("§a§l✓")
                .clickEvent(ClickEvent.runCommand("/przerwa tak"))
                .hoverEvent(HoverEvent.showText(Component.text("§aZagłosuj za przerwą")));
        Component noBtn = Component.text("§c§l✗")
                .clickEvent(ClickEvent.runCommand("/przerwa nie"))
                .hoverEvent(HoverEvent.showText(Component.text("§cZagłosuj przeciwko przerwie")));
        Component spacer = Component.text("  ");

        for (UUID uuid : arena.getPlayers()) {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p != null) p.sendMessage(yesBtn.append(spacer).append(noBtn));
        }
    }

    private void broadcastVoteProgress(Arena arena) {
        String arenaName = arena.getName();
        Set<UUID> yes = voteYes.getOrDefault(arenaName, Set.of());
        int totalPlayers = arena.getPlayers().size();
        int majority = (totalPlayers / 2) + 1;
        broadcastChat(arena, "§7Głosy: §aTAK §f" + yes.size() + "§7/§f" + majority
                + " §7potrzebnych (graczy: §f" + totalPlayers + "§7)");
    }

    // ====================== UTILITY ======================

    private String formatTime(int totalSeconds) {
        int m = totalSeconds / 60;
        int s = totalSeconds % 60;
        return String.format("%02d:%02d", m, s);
    }

    /** Czy arena jest teraz spauzowana. */
    public boolean isPaused(Arena arena) {
        return activePauses.containsKey(arena.getName());
    }

    /** Czy trwa głosowanie na arenie (przerwa lub anulowanie). */
    public boolean isVoting(Arena arena) {
        return activeVotes.containsKey(arena.getName()) || activeCancelVotes.containsKey(arena.getName());
    }

    /** Czy trwa głosowanie na ANULOWANIE przerwy. */
    public boolean isCancelVoting(Arena arena) {
        return activeCancelVotes.containsKey(arena.getName());
    }

    /** Ile przerw zostało (max 3 na grę). */
    public int remainingPauses(Arena arena) {
        return MAX_PAUSES_PER_GAME - usedPauses.getOrDefault(arena.getName(), 0);
    }

    /** Resetuje licznik przerw (wołane z endGame). */
    public void resetArena(String arenaName) {
        usedPauses.remove(arenaName);
        // anuluj aktywne głosowanie / przerwę
        VoteState vote = activeVotes.remove(arenaName);
        if (vote != null) {
            activeVotes.remove(arenaName);
        }
        activeVotes.remove(arenaName);
        voteYes.remove(arenaName);
        voteNo.remove(arenaName);
        PauseState pause = activePauses.remove(arenaName);
        if (pause != null && pause.timerTask != null) {
            pause.timerTask.cancel();
        }
    }

    // ====================== INNER CLASSES ======================

    private static class VoteState {
        final int durationSeconds;
        VoteState(int durationSeconds) {
            this.durationSeconds = durationSeconds;
        }
    }

    private static class PauseState {
        final int durationSeconds;
        final long startedAt;
        BukkitTask timerTask;

        PauseState(int durationSeconds, long startedAt) {
            this.durationSeconds = durationSeconds;
            this.startedAt = startedAt;
        }
    }
}
