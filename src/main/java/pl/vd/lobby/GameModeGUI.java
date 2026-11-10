package pl.vd.lobby;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pl.vd.VillagerDefensePlugin;
import pl.vd.util.ItemBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI z wyborem trybu gry — 54 sloty (6 rzędów).
 * Otwierane po kliknięciu NPC w lobby.
 *
 * Sloty:
 *   10, 13, 16 — przyciski trybów (rząd 2)
 *   31 — kompas (centrum)
 *   49 — wyjście (X)
 */
public class GameModeGUI {

    private static final String TITLE = "§8Wybierz tryb gry";
    private static final int SIZE = 54; // 6 rzędów

    private final VillagerDefensePlugin plugin;

    public GameModeGUI(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Otwiera GUI z wyborem trybu gry dla gracza.
     */
    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, SIZE, TITLE);

        // szkło dekoracyjne — wypełnienie pustych slotów
        ItemStack glass = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE, 1)
                .name(" ")
                .build();
        for (int i = 0; i < SIZE; i++) {
            inv.setItem(i, glass);
        }

        // === PRZYCISKI TRYGÓW ===

        // Villager Defense (slot 10 — rząd 2, kolumna 2)
        ItemStack vd = new ItemBuilder(Material.NETHERITE_SWORD, 1)
                .name("§c⚔ Villager Defense")
                .lore(java.util.List.of(
                        "§7Obroniaj wieśniaków przed falami mobów!",
                        "",
                        "§7Gracze: §f2-20",
                        "§7Fale: §f10+",
                        "",
                        "§eKliknij aby dołączyć!"
                ))
                .build();
        inv.setItem(10, vd);

        // Raft (slot 13 — rząd 2, kolumna 5)
        ItemStack raft = new ItemBuilder(Material.COMPASS, 1)
                .name("§b🌊 Raft")
                .lore(java.util.List.of(
                        "§7Przetrwaj na oceanie!",
                        "",
                        "§7Gracze: §f1-4",
                        "§7Fabuła: §fTak",
                        "",
                        "§eKliknij aby dołączyć!"
                ))
                .build();
        inv.setItem(13, raft);

        // Wkrótce (slot 16 — rząd 2, kolumna 8)
        ItemStack soon = new ItemBuilder(Material.BARRIER, 1)
                .name("§8🔒 Wkrótce")
                .lore(java.util.List.of(
                        "§7Kolejne tryby wkrótce!",
                        "",
                        "§7Śledź nasze social media",
                        "§7aby być na bieżąco."
                ))
                .build();
        inv.setItem(16, soon);

        // === WYJŚCIE (slot 49) ===
        ItemStack exit = new ItemBuilder(Material.ARROW, 1)
                .name("§c§lWyjście")
                .lore(java.util.List.of("§7Zamknij menu"))
                .build();
        inv.setItem(49, exit);

        // === CODZIENNA SKRZYNIA (slot 53 — ostatni slot) ===
        DailyRewardManager drm = plugin.getDailyRewardManager();
        boolean canClaim = drm.canClaim(player);
        int xp = drm.getTotalXp(player);
        int level = Math.min(100, xp / 10);
        int streak = drm.getStreak(player);
        int bonus = drm.getStreakBonus(streak);
        int nextBonus = drm.getStreakBonus(streak + 1);

        // oblicz ile do następnego ranku
        String nextRankInfo = "";
        if (streak < 100) {
            int daysToNext = 0;
            String nextRankName = "";
            if (streak < 10) { daysToNext = 11 - streak; nextRankName = "ŚREDNI"; }
            else if (streak < 20) { daysToNext = 21 - streak; nextRankName = "ZAAWANSOWANY"; }
            else if (streak < 30) { daysToNext = 31 - streak; nextRankName = "WETERAN"; }
            else if (streak < 50) { daysToNext = 51 - streak; nextRankName = "EKSPERT"; }
            else if (streak < 75) { daysToNext = 76 - streak; nextRankName = "MISTRZ"; }
            else { daysToNext = 101 - streak; nextRankName = "LEGENDA"; }
            nextRankInfo = "§7Następny rank: §e" + nextRankName + " §7za §f" + daysToNext + " §7dni";
        }

        ItemStack daily = new ItemBuilder(Material.CHEST, 1)
                .name(canClaim ? "§6⭐ §eCodzienna skrzynia" : "§8⭐ §7Codzienna skrzynia")
                .lore(java.util.List.of(
                        canClaim ? "§aKliknij aby odebrać!" : "§cJuż odebrana dzisiaj!",
                        "",
                        "§7Streak: §e" + streak + " §7dni",
                        "§7Bonus: §a+" + bonus + " §7XP",
                        "",
                        nextRankInfo,
                        "",
                        "§7Twój poziom: §e" + level,
                        "§7Następna nagroda: " + (canClaim ? "§aGotowe!" : drm.getRemainingTime(player))
                ))
                .build();
        inv.setItem(53, daily);

        player.openInventory(inv);
    }

    /**
     * Sprawdza czy to nasze GUI (po tytule).
     */
    public boolean isOurGUI(String title) {
        return TITLE.equals(title);
    }

    /**
     * Przetwarza kliknięcie w GUI.
     * @return true jeśli kliknięcie zostało obsłużone
     */
    public boolean handleClick(Player player, int slot) {
        switch (slot) {
            case 10 -> {
                // Villager Defense
                joinVillagerDefense(player);
                return true;
            }
            case 13 -> {
                // Raft
                joinRaft(player);
                return true;
            }
            case 53 -> {
                // Codzienna skrzynia
                DailyRewardManager drm = plugin.getDailyRewardManager();
                if (drm.canClaim(player)) {
                    int oldStreak = drm.getStreak(player);
                    if (drm.claim(player)) {
                        int newStreak = drm.getStreak(player);
                        int bonus = drm.getStreakBonus(newStreak);
                        int total = 5 + bonus;
                        player.closeInventory();
                        StringBuilder msg = new StringBuilder();
                        msg.append("§6⭐ §eOdebrano dzienną nagrodę! §7+§a").append(total).append(" XP");
                        if (newStreak > 1) {
                            msg.append("\n§7Streak: §e").append(newStreak).append(" §7dni §7(§a+").append(bonus).append(" §7bonus)");
                        }
                        player.sendMessage(net.kyori.adventure.text.Component.text(msg.toString()));
                        plugin.getPlayerXPBar().refresh(player);
                    }
                } else {
                    String remaining = drm.getRemainingTime(player);
                    player.sendMessage(net.kyori.adventure.text.Component.text("§cOdebrałeś/aś już codzienną skrzynię! §7Następna: " + remaining));
                }
                return true;
            }
            case 49 -> {
                // Wyjście
                player.closeInventory();
                return true;
            }
            default -> {
                // Klik w szkło lub inne sloty — ignoruj
                return false;
            }
        }
    }

    /**
     * Dołącza gracza do Villager Defense.
     * Teleportuje do świata villagerdefense_hub (0 81 0) i czyści ekwipunek lobby.
     */
    private void joinVillagerDefense(Player player) {
        player.closeInventory();

        org.bukkit.World world = org.bukkit.Bukkit.getWorld("villagerdefense_hub");
        if (world == null) {
            pl.vd.util.AntiSpam.send(player, "§cŚwiat villagerdefense_hub nie istnieje. Użyj: /def create_world villagerdefense_hub");
            return;
        }

        // usuń cały ekwipunek lobby (na razie tylko kompas trybów)
        player.getInventory().clear();

        // teleport na kordynaty 0 81 0 (środek bloku)
        player.teleport(new org.bukkit.Location(world, 0.5, 81, 0.5));
    }

    /**
     * Dołącza gracza do trybu Raft.
     * Przenosi gracza do świata Raft i uruchamia sesję.
     */
    private void joinRaft(Player player) {
        player.closeInventory();
        plugin.getPlayerListener().removeLobbyCompass(player);

        pl.vd.raft.RaftPlugin raft = pl.vd.raft.RaftPlugin.getInstance();
        if (raft == null) {
            pl.vd.util.AntiSpam.send(player, "§cSystem Raft nie jest gotowy.");
            return;
        }

        // Świat Raft
        org.bukkit.World raftWorld = null;
        if (raft.getWorldGenerator() != null) {
            raftWorld = raft.getWorldGenerator().getRaftWorld();
        }
        if (raftWorld == null) {
            String worldName = plugin.getConfig().getString("raft-world-name", "RAFT");
            raftWorld = org.bukkit.Bukkit.getWorld(worldName);
        }
        if (raftWorld == null) {
            pl.vd.util.AntiSpam.send(player, "§cŚwiat Raft nie istnieje.");
            return;
        }

        // Rozpocznij sesję i dołącz do gry
        raft.getPlayerManager().startSession(player);
        raft.getGameManager().joinGame(player);

        // Faza 3 — cel podróży: żywa tratwa gracza (jeśli pływa) → zapisana tratwa
        // (wraca tam, gdzie została) → spawn świata Raft (nowa gra).
        pl.vd.raft.entity.RaftManager raftManager = pl.vd.raft.entity.RaftManager.getInstance();
        java.util.UUID playerId = player.getUniqueId();
        org.bukkit.Location target = null;
        pl.vd.raft.entity.RaftEntity liveRaft = raftManager.getRaftForPlayer(playerId);
        if (liveRaft != null) {
            target = liveRaft.getCenter(); // nadal pływa na oceanie — wróć na nią
        } else if (pl.vd.raft.entity.RaftPersistence.hasSave(playerId)) {
            target = pl.vd.raft.entity.RaftPersistence.savedLocation(playerId);
        }
        if (target == null) {
            target = raftWorld.getSpawnLocation();
        }
        player.teleport(target.clone().add(0, 1.5, 0));

        // Spawn/przywrócenie tratwy po załadowaniu chunków i postawienie gracza
        // na pokładzie (deck top = środek tratwy + 0.5)
        raft.getServer().getScheduler().runTaskLater(raft.getParent(), () -> {
            pl.vd.raft.entity.RaftEntity raftEntity = raftManager.spawnRaftForPlayer(player, 64);
            if (raftEntity == null) {
                // Cichy fallback: szerszy promień poszukiwania wody (bez komunikatów).
                raftEntity = raftManager.spawnRaftForPlayer(player, 128);
            }
            if (raftEntity == null) return; // bez powiadomienia — gracz zostaje przy spawnie
            org.bukkit.Location deck = raftEntity.getCenter().clone();
            deck.setY(deck.getY() + 0.6); // lekko nad pokładem — grawitacja dosadzi
            deck.setPitch(0f);
            player.teleport(deck);
        }, 20L); // 1 sekunda — chunki muszą się załadować
    }
}
