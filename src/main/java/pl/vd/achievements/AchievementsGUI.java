package pl.vd.achievements;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import pl.vd.VillagerDefensePlugin;
import pl.vd.util.ItemBuilder;
import pl.vd.util.Lang;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI osiągnięć - 54 sloty. Układ (taki sam dla huba i każdej kategorii):
 *   rząd 1 (sloty 1-9 w grze): 9 ikon kategorii,
 *   rzędy 2-5 (sloty 10-45 w grze): osiągnięcia (36 na stronę) - przewijane strzałkami o 1 rząd,
 *   rząd 6 (sloty 46-54 w grze): strzałki przewijania w lewym dolnym rogu (sloty 46-47),
 *     nether star na środku (slot 49) i butelka XP z licznikiem w prawym dolnym rogu (slot 54).
 * Każde osiągnięcie to szyba w kolorze wg postępu: czerwona 0-33%, pomarańczowa 34-66%,
 * jasnozielona 67-99%, zielona 100% (ukończone). Hub pokazuje wszystkie osiągnięcia,
 * widok kategorii - tylko jej osiągnięcia. Strzałki przewijania znikają na krańcach
 * listy (▲ na górze, ▼ na dole).
 */
public class AchievementsGUI {

    public static final int SCROLL_UP_SLOT    = 45; // w grze 46 - „Przewiń do góry" (lewy dolny róg)
    public static final int SCROLL_DOWN_SLOT  = 46; // w grze 47 - „Przewiń w dół" (obok, po prawej)
    public static final int COUNTER_SLOT      = 53; // w grze 54 - butelka XP z licznikiem (prawy dolny róg)
    public static final int EXIT_SLOT         = 49;

    public static final int ROW = 9;                    // przewijanie o 1 rząd = 9 osiągnięć
    public static final int PER_PAGE = 36;              // rzędy 2-5 = 36 osiągnięć na stronę

    private static final int SIZE = 54;
    private static final int ACH_START = 9;             // indeks (w grze 10) - pierwszy slot osiągnięć
    private static final int ACH_END = 44;              // indeks (w grze 45) - ostatni slot osiągnięć

    private final VillagerDefensePlugin plugin;
    private final ItemStack grayPane;
    private final ItemStack redPane;

    public AchievementsGUI(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
        this.grayPane = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE, 1).name(" ").build();
        this.redPane = new ItemBuilder(Material.RED_STAINED_GLASS_PANE, 1).name(" ").build();
    }

    /** Otwiera ekran osiągnięć - od razu na pierwszej kategorii (Walka). */
    public void open(Player player, AchievementsHolder.Source source) {
        openCategory(player, AchievementCategory.WALKA, source);
    }

    /** Otwiera ekran kategorii - DOKŁADNIE to samo GUI co hub (te same strzałki,
     *  kategorie, licznik, tło i przycisk na dole); różni się tylko tym, co pokaże
     *  pole osiągnięć: osiągnięcia tej kategorii. */
    public void openCategory(Player player, AchievementCategory category, AchievementsHolder.Source source) {
        openView(player, source, category);
    }

    /** Wspólne GUI (hub i każda kategoria): kategorie w rzędzie 1, osiągnięcia
     *  w rzędach 2-5, licznik i strzałki na dole, przycisk wg źródła. Hub pokazuje
     *  wszystkie osiągnięcia, widok kategorii - tylko jej. */
    private void openView(Player player, AchievementsHolder.Source source, AchievementCategory category) {
        AchievementsHolder.View view = category != null ? AchievementsHolder.View.CATEGORY : AchievementsHolder.View.HUB;
        AchievementsHolder holder = new AchievementsHolder(view, source, category);
        // tytuł: w widoku kategorii „Osiągnięcia - <nazwa kategorii>", w hubie samo „Osiągnięcia"
        String title = category != null
                ? Lang.get(player, "ach-title-category", Lang.get(player, category.getNameKey()))
                : Lang.get(player, "ach-title");
        Inventory inv = Bukkit.createInventory(holder, SIZE, Component.text(title));
        holder.setInventory(inv);

        layout(inv, player, holder);
        player.openInventory(inv);
    }

    /** Rysuje całe GUI według stanu holdera (offset przewijania, źródło, kategoria). */
    private void layout(Inventory inv, Player player, AchievementsHolder holder) {
        fillBackground(inv);
        for (AchievementCategory cat : AchievementCategory.values()) {
            // wybrana kategoria (ta, której osiągnięcia widać) dostaje efekt błyszczenia
            inv.setItem(cat.getSlot(), categoryItem(cat, player, cat == holder.getCategory()));
        }
        inv.setItem(COUNTER_SLOT, counterItem(player));
        // przycisk na dole: ze sklepu = „Powrót" (do sklepu), z komendy = „Zamknij"
        inv.setItem(EXIT_SLOT, exitButton(player, holder.getSource() == AchievementsHolder.Source.SHOP ? "ach-back-shop" : "ach-close"));
        renderArrows(inv, player, holder);
        renderAchievements(inv, player, holder);
    }

    /** Odświeża strzałki i osiągnięcia po przewinięciu (bez zamykania GUI). */
    public void updateView(Inventory inv, Player player, AchievementsHolder holder) {
        renderArrows(inv, player, holder);
        renderAchievements(inv, player, holder);
    }

    /** Lista osiągnięć widoczna w danym widoku (hub = wszystkie, kategoria = jej). */
    public List<Achievement> getViewList(AchievementsHolder holder) {
        return holder.getCategory() == null
                ? plugin.getAchievementRegistry().getAll()
                : plugin.getAchievementRegistry().forCategory(holder.getCategory());
    }

    /** Tło GUI: szare szyby w rzędach 1 i 6, czerwone od slotu 10 do 45 (w grze). */
    private void fillBackground(Inventory inv) {
        for (int i = 0; i < SIZE; i++) {
            // sloty w grze 10-45 = indeksy 9-44 (środkowe rzędy)
            inv.setItem(i, (i >= 9 && i <= 44) ? redPane : grayPane);
        }
    }

    /** Nether star: „Wyjdź" (hub) albo „Wróć" (ekran kategorii). */
    private ItemStack exitButton(Player player, String key) {
        return new ItemBuilder(Material.NETHER_STAR, 1)
                .name(Lang.get(player, key))
                .build();
    }

    /** Strzałka przewijania: bez nazwy (pusta, żeby nie było linijki „Arrow"),
     *  a w lore tylko legenda kliknięć: „LPM: ..." (zwykły klik)
     *  i „Shift + LPM: ..." (skok na górę/dół). */
    private ItemStack arrowItem(Player player, String key) {
        return new ItemBuilder(Material.ARROW, 1)
                .name("")
                .lore(List.of(
                        Lang.get(player, key + "-lore"),
                        Lang.get(player, key + "-lore2")))
                .build();
    }

    /** Strzałki ▲/▼ w lewym dolnym rogu. Na krańcach listy strzałka ZNIKA (slot
     *  dostaje szybę tła): ▲ gdy jesteśmy na górze, ▼ gdy na dole osiągnięć.
     *  Slot jest ZAWSZE ustawiany (strzałka albo szyba), żeby po przewinięciu
     *  nie została w nim stara strzałka z poprzedniego rysowania. */
    private void renderArrows(Inventory inv, Player player, AchievementsHolder holder) {
        List<Achievement> list = getViewList(holder);
        int offset = holder.getScrollOffset();
        inv.setItem(SCROLL_UP_SLOT, offset > 0 ? arrowItem(player, "ach-scroll-up") : grayPane);
        inv.setItem(SCROLL_DOWN_SLOT, offset + PER_PAGE < list.size() ? arrowItem(player, "ach-scroll-down") : grayPane);
    }

    /** Ikona kategorii (mikstura leczenia dla Przetrwania); wybrana dostaje błyszczenie. */
    private ItemStack categoryItem(AchievementCategory cat, Player player, boolean selected) {
        if (cat == AchievementCategory.PRZETRWANIE) return healPotionItem(player, selected);
        ItemStack icon = new ItemBuilder(cat.getMaterial(), 1)
                .name(Lang.get(player, cat.getNameKey()))
                .lore(List.of(Lang.get(player, "ach-cat-click-lore")))
                .build();
        return selected ? withGlint(icon) : icon;
    }

    /** Mikstura natychmiastowego leczenia jako ikona kategorii „Przetrwanie". */
    private ItemStack healPotionItem(Player player, boolean selected) {
        ItemStack potion = new ItemStack(Material.POTION, 1);
        PotionMeta pm = (PotionMeta) potion.getItemMeta();
        if (pm != null) {
            pm.setBasePotionType(PotionType.HEALING); // natychmiastowe leczenie
            pm.setDisplayName(Lang.get(player, "ach-cat-przetrwanie"));
            pm.setLore(List.of(Lang.get(player, "ach-cat-click-lore")));
            potion.setItemMeta(pm);
        }
        return selected ? withGlint(potion) : potion;
    }

    /** Dodaje ukryte zaklęcie: sam efekt błyszczenia (glint), bez pokazywania
     *  nazwy zaklęcia w tooltipie (HIDE_ENCHANTS). */
    private ItemStack withGlint(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addEnchant(Enchantment.PROTECTION, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Butelka XP „Osiągnięcia" z licznikiem odblokowanych: „Odblokowane: X/36 (Y%)". */
    private ItemStack counterItem(Player player) {
        int unlocked = unlockedCount(player);
        int total = totalCount();
        int percent = total <= 0 ? 0 : (int) Math.round(100.0 * unlocked / total);
        return new ItemBuilder(Material.EXPERIENCE_BOTTLE, 1)
                .name(Lang.get(player, "achievements-title"))
                .lore(List.of(Lang.get(player, "ach-counter-lore", unlocked, total, percent)))
                .build();
    }

    /** Liczba odblokowanych osiągnięć gracza. */
    public int unlockedCount(Player player) {
        int n = 0;
        for (Achievement a : plugin.getAchievementRegistry().getAll()) {
            if (a.isUnlocked(player)) n++;
        }
        return n;
    }

    /** Łączna liczba osiągnięć. */
    public int totalCount() {
        return plugin.getAchievementRegistry().getAll().size();
    }

    /** Osiągnięcia w rzędach 2-5 (indeksy 9-44), zaczynając od przesunięcia z holdera.
     *  Wolne sloty na końcu listy dostają z powrotem czerwone tło (żadnych starych
     *  osiągnięć po przewinięciu). */
    private void renderAchievements(Inventory inv, Player player, AchievementsHolder holder) {
        List<Achievement> list = getViewList(holder);
        int offset = holder.getScrollOffset();
        int slot = ACH_START;
        for (int i = offset; i < list.size() && slot <= ACH_END; i++, slot++) {
            inv.setItem(slot, achievementItem(list.get(i), player));
        }
        for (; slot <= ACH_END; slot++) {
            inv.setItem(slot, redPane);
        }
    }

    /** Szyba osiągnięcia: kolor wg procentu postępu, w lore nazwa + opis + postęp.
     *  Ukryte osiągnięcia pokazują „???" dopóki nie są odblokowane. */
    private ItemStack achievementItem(Achievement a, Player player) {
        boolean unlocked = a.isUnlocked(player);
        int current = a.getCurrent(player);
        int target = a.getTarget();
        int percent = a.getPercent(player);

        boolean hidden = a.getCategory() == AchievementCategory.UKRYTE && !unlocked;
        ItemBuilder builder = new ItemBuilder(ProgressColor.paneForPercent(percent), 1);
        if (hidden) {
            builder.name(Lang.get(player, "ach-hidden-name"));
            builder.lore(List.of(Lang.get(player, "ach-hidden-desc")));
        } else {
            builder.name((unlocked ? "§a" : "§7") + achievementName(a, player));
            List<String> lore = new ArrayList<>();
            String desc = achievementDesc(a, player);
            if (!desc.isEmpty()) lore.add(desc); // pomijamy puste opisy (np. poziomy bez wymagań)
            // podtytuł ukończenia: postęp X/Y (Z%) dla śledzonych osiągnięć, sam %
            // dla nieśledzonych (na razie 0%) - pokazywany ZAWSZE, nawet przy 0%
            if (target > 0) {
                lore.add(Lang.get(player, "ach-progress-lore", current, target, percent));
            } else {
                lore.add(Lang.get(player, "ach-progress-percent", percent));
            }
            if (unlocked) {
                lore.add(Lang.get(player, "ach-unlocked-lore"));
            }
            builder.lore(lore);
        }
        return builder.build();
    }

    /** Nazwa osiągnięcia (poziomy dostają numer w {0}). */
    private String achievementName(Achievement a, Player player) {
        if (a.getTier() > 0) return Lang.get(player, a.getNameKey(), a.getTier());
        return Lang.get(player, a.getNameKey());
    }

    /** Opis osiągnięcia (poziomy dostają wymaganie w {1}). */
    private String achievementDesc(Achievement a, Player player) {
        if (a.getTier() > 0) return Lang.get(player, a.getDescKey(), a.getTier(), a.getTarget());
        return Lang.get(player, a.getDescKey());
    }
}
