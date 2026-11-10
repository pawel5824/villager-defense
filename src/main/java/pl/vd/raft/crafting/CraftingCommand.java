package pl.vd.raft.crafting;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import pl.vd.raft.RaftPlugin;
import pl.vd.raft.items.CustomItem;
import pl.vd.raft.items.ItemManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Komenda /wytwarzanie — otwiera GUI wytwarzania (skrzynka 9×5) przy graczu,
 * działając TYLKO w świecie Raft (na tratwie i na wyspie — wszędzie tam, gdzie
 * gracz jest w świecie Raft; nie trzeba stawać przy stole rzemieślniczym).
 *
 * Górny rząd (sloty 1–8, od lewej do prawej) = KATEGORIE wytwarzania:
 *
 *   1. Pieczony kurczak      — „Jedzenie / woda"
 *   2. Drewniana łopata      — „Narzędzia"
 *   3. Drewniany miecz       — „Bronie"
 *   4. Utleniona miedziana skrzynia — „Ekwipunek"
 *   5. Piorunochron          — „Materiały / Zasoby"
 *   6. Kompas                — „Nawigacja"
 *   7. Skrzynia endu         — „Inne / różne"
 *   8. Dekorowany garnek     — „Dekoracje"
 *
 * Klik w KAŻDĄ kategorię otwiera IDENTYCZNE GUI (wszędzie ten sam układ:
 * kategorie na tych samych slotach, szare szyby jako separatory, „Wyjście"
 * na ostatnim slocie). AKTYWNA kategoria ma EFEKT ZAKLĘCIA (połysk), żeby
 * gracz zawsze wiedział, w której kategorii się znajduje; klik w aktywną
 * kategorię ponownie = powrót do menu głównego (bez poświaty).
 *
 * Szare szyby = separatory (slot 9 oraz prawa kolumna i dół menu), a OSTATNI
 * slot (prawy dolny róg) = „Wyjście" (netherowa gwiazda, zamyka GUI).
 *
 * W sekcji treści aktywnej kategorii leżą przedmioty do wytworzenia (przepisy
 * w CATEGORY_RECIPES). Klik w przedmiot = wytworzenie: surowce są liczone
 * i pobierane z ekwipunku gracza, wynik ląduje w ekwipunku (nadmiar wypada
 * pod graczem). Podpowiedź przedmiotu pokazuje na bieżąco, czego brakuje
 * (zielony ✔ / czerwony ✘) — bez żadnych komunikatów na czacie.
 *
 * Zaimplementowane przepisy:
 *   • „Nawigacja” — Żagiel (CMD 4005): 3× biała wełna + 3× deska + 1× lina,
 *   • „Materiały / Zasoby” — Podłoga (piętrowa, `wooden_floor`, CMD 7040):
 *     4× deska + 1× lina. Jedna sztuka = 9 kratek piętra (jak fundament).
 */
public class CraftingCommand implements CommandExecutor, Listener {

    /** Tytuł GUI (identyfikacja holdera przy obsłudze klików). */
    public static final String GUI_TITLE = "Wytwarzanie";

    /** Slot przycisku „Wyjście" — OSTATNI slot GUI 9×5 (prawy dolny róg).
     *  W grze to slot 45; w kodzie (od 0) = 44. */
    private static final int SLOT_EXIT = 44;

    /** Sloty szarych szyb (separatory, indeksy od 0): koniec rzędu kategorii (8),
     *  prawa kolumna sekcji treści (17/26/35) i dół menu (36–43) — obok
     *  przycisku „Wyjście" na końcu. */
    private static final int[] SEPARATOR_SLOTS = {
            8, 17, 26, 35, 36, 37, 38, 39, 40, 41, 42, 43
    };

    /**
     * Sloty sekcji treści (wiersze 2–4 pod rzędem kategorii, bez separatorów
     * i bez „Wyjścia”) — 24 miejsca na przedmioty do wytworzenia.
     */
    private static final int[] CONTENT_SLOTS = {
            9, 10, 11, 12, 13, 14, 15, 16,
            18, 19, 20, 21, 22, 23, 24, 25,
            27, 28, 29, 30, 31, 32, 33, 34
    };

    /** Kategoria w GUI: slot, materiał ikony, nazwa wyświetlana. */
    private record Category(int slot, Material icon, String name) {}

    /**
     * Składnik przepisu: materiał + ile sztuk + nazwa na podpowiedzi.
     * Gdy {@code customItemId} nie jest null, wymagany jest KONKRETNY przedmiot
     * pluginu (rozpoznawany po CustomModelData — tak jak „Lina” z pluginu).
     * Sam materiał (np. deska) akceptuje każdy wariant — także waniliowy.
     */
    private record Ingredient(Material material, int amount, String label,
                              String customItemId) {
        static Ingredient of(Material material, int amount, String label) {
            return new Ingredient(material, amount, label, null);
        }
    }

    /** Przepis w sekcji treści kategorii: kolejne miejsce + wynik + składniki. */
    private record CraftRecipe(int contentIndex, String resultItemId,
                               List<Ingredient> ingredients) {}

    /**
     * Przepisy kategorii: slot kategorii → lista przepisów (kolejność = sloty
     * treści od góry). Puste/nieobecne kategorie pokazują pustą treść.
     *
     * „Materiały / Zasoby” (slot 4) — piętrowa Podłoga (`wooden_floor`,
     * CMD 7040): 4× deska + 1× lina. Surowce są liczone i pobierane
     * z ekwipunku gracza — jedna sztuka wystarcza na 9 kratek piętra.
     *
     * „Nawigacja” (slot 5) — Żagiel: 3× biała wełna + 3× deska + 1× lina.
     */
    private static final Map<Integer, List<CraftRecipe>> CATEGORY_RECIPES = Map.of(
            4, List.of(new CraftRecipe(0, "wooden_floor", List.of(
                    Ingredient.of(Material.OAK_PLANKS, 4, "Deska"),
                    new Ingredient(Material.LEAD, 1, "Lina", "rope")))),
            5, List.of(new CraftRecipe(0, "zagiel", List.of(
                    Ingredient.of(Material.WHITE_WOOL, 3, "Biała wełna"),
                    Ingredient.of(Material.OAK_PLANKS, 3, "Deska"),
                    new Ingredient(Material.LEAD, 1, "Lina", "rope"))))
    );

    /** Kategorie w górnym rzędzie (od lewej do prawej, slot 0–7). */
    private static final List<Category> CATEGORIES = List.of(
            new Category(0, Material.COOKED_CHICKEN, "§eJedzenie / woda"),
            new Category(1, Material.WOODEN_SHOVEL, "§eNarzędzia"),
            new Category(2, Material.WOODEN_SWORD, "§eBronie"),
            new Category(3, Material.OXIDIZED_COPPER_CHEST, "§eEkwipunek"),
            new Category(4, Material.LIGHTNING_ROD, "§eMateriały / Zasoby"),
            new Category(5, Material.COMPASS, "§eNawigacja"),
            new Category(6, Material.ENDER_CHEST, "§eInne / różne"),
            new Category(7, Material.DECORATED_POT, "§eDekoracje")
    );

    private final RaftPlugin plugin;

    /** Klucz ustawienia w raft-players.yml (players.<uuid>.settings) — ostatnia
     *  kategoria w GUI wytwarzania PRZETRWA RESTART SERWERA (PlayerSession.save). */
    private static final String SETTING_LAST_CATEGORY = "crafting-category";

    /** Awaryjny fallback (UUID -> slot), gdy gracz nie ma aktywnej sesji Raft. */
    private final Map<UUID, Integer> lastCategory = new HashMap<>();

    public CraftingCommand(RaftPlugin plugin) {
        this.plugin = plugin;
    }

    /** Ostatnia kategoria gracza: z sesji (raft-players.yml) albo z pamięci. */
    private int getLastCategory(Player player) {
        pl.vd.raft.player.PlayerSession session = plugin.getPlayerManager()
                .getSession(player.getUniqueId());
        if (session != null) {
            try {
                return Integer.parseInt(
                        session.getSetting(SETTING_LAST_CATEGORY, "-1"));
            } catch (NumberFormatException ignored) {
                // uszkodzona wartość — traktuj jak brak wyboru
            }
        }
        return lastCategory.getOrDefault(player.getUniqueId(), -1);
    }

    /** Zapamiętuje kategorię gracza: w sesji (natychmiastowy zapis do pliku)
     *  albo w pamięci, gdy sesji brak. */
    private void setLastCategory(Player player, int slot) {
        pl.vd.raft.player.PlayerSession session = plugin.getPlayerManager()
                .getSession(player.getUniqueId());
        if (session != null) {
            session.setSetting(SETTING_LAST_CATEGORY, String.valueOf(slot));
        } else {
            lastCategory.put(player.getUniqueId(), slot);
        }
    }

    /**
     * Otwiera GUI wytwarzania BEZ sprawdzania świata — dla przycisku z moda
     * (raft:open_crafting), którego odbiornik (CraftingKeyReceiver) sam waliduje
     * tryb Raft i poza nim milczy. Wraca do ostatniej kategorii gracza.
     */
    public void openForPlayer(Player player) {
        openGui(player, getLastCategory(player));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Ta komenda jest tylko dla graczy.");
            return true;
        }

        // Tylko świat Raft (nazwa z configu: raft-world-name, domyślnie RAFT)
        String raftWorldName = plugin.getParent().getConfig()
                .getString("raft-world-name", "RAFT");
        if (player.getWorld().getName().equalsIgnoreCase(raftWorldName)) {
            // Wróć do OSTATNIEJ kategorii gracza (albo menu głównego, gdy nigdy
            // nie wybierał) — wybór przetrwa zamknięcie GUI i restart serwera.
            openGui(player, getLastCategory(player));
        } else {
            player.sendMessage(ChatColor.RED + "Ta komenda działa tylko w trybie Raft.");
        }
        return true;
    }

    /**
     * Otwiera GUI wytwarzania 9×5 (holder = CraftingCommandHolder). Układ jest
     * ZAWSZE identyczny; {@code activeSlot} = slot aktywnej kategorii (-1 = menu
     * główne, żadna nie jest wybrana). Aktywna kategoria dostaje efekt zaklęcia.
     */
    private void openGui(Player player, int activeSlot) {
        CraftingCommandHolder holder = new CraftingCommandHolder();
        holder.setActiveCategory(activeSlot);
        Inventory inv = plugin.getParent().getServer().createInventory(
                holder, 45, GUI_TITLE);
        holder.setInventory(inv);

        // Kategorie w górnym rzędzie (aktywna = poświata zaklęcia)
        for (Category category : CATEGORIES) {
            inv.setItem(category.slot(), categoryIcon(category, category.slot() == activeSlot));
        }
        // Szare szyby — separatory (prawa kolumna + dół menu)
        for (int slot : SEPARATOR_SLOTS) {
            inv.setItem(slot, glassPane());
        }
        // Przycisk „Wyjście" — ostatni slot (prawy dolny róg)
        inv.setItem(SLOT_EXIT, exitIcon());
        // Sekcja treści aktywnej kategorii — przedmioty do wytworzenia
        fillContent(inv, activeSlot, player);

        player.openInventory(inv);
    }

    /**
     * Ikona kategorii. Gdy aktywna — efekt zaklętego przedmiotu
     * (połysk, bez widocznych enchantów) i podpowiedź „Aktualna kategoria".
     */
    private ItemStack categoryIcon(Category category, boolean active) {
        ItemStack item = new ItemStack(category.icon());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(category.name());
        List<String> lore = new ArrayList<>();
        if (active) {
            meta.setEnchantmentGlintOverride(true); // połysk jak przedmiot zaklęty
            lore.add("§aAktualna kategoria");
            lore.add("§7Kliknij ponownie aby wrócić do menu");
        } else {
            lore.add("§7Kliknij aby otworzyć kategorię");
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /** Dekoracyjna szara szyba (separator — jak w innych GUI pluginu). */
    private ItemStack glassPane() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(" ");
        item.setItemMeta(meta);
        return item;
    }

    /** Przycisk „Wyjście" — zamyka GUI wytwarzania. */
    private ItemStack exitIcon() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§cWyjście");
        List<String> lore = new ArrayList<>();
        lore.add("§7Kliknij aby zamknąć menu wytwarzania");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Wypełnia sekcję treści przedmiotami do wytworzenia dla aktywnej
     * kategorii ({@code activeSlot} = -1 → menu główne, treść pusta).
     */
    private void fillContent(Inventory inv, int activeSlot, Player player) {
        if (activeSlot < 0) return;
        List<CraftRecipe> recipes = CATEGORY_RECIPES.get(activeSlot);
        if (recipes == null) return;
        for (CraftRecipe recipe : recipes) {
            int index = recipe.contentIndex();
            if (index < 0 || index >= CONTENT_SLOTS.length) continue;
            inv.setItem(CONTENT_SLOTS[index], recipeIcon(recipe, player));
        }
    }

    /**
     * Ikona przepisu: sam przedmiot-wynik (z jego własną podpowiedzią) plus
     * dopisana lista składników ze stanem ekwipunku (✔/✘) i akcją kliknięcia.
     */
    private ItemStack recipeIcon(CraftRecipe recipe, Player player) {
        ItemManager im = plugin.getItemManager();
        ItemStack item = im == null ? null : im.createItem(recipe.resultItemId());
        if (item == null) return new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore()
                    ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add("§7Potrzebne:");
            boolean canCraft = true;
            for (Ingredient ingredient : recipe.ingredients()) {
                int have = countInInventory(player, ingredient);
                boolean enough = have >= ingredient.amount();
                if (!enough) canCraft = false;
                lore.add((enough ? "§a✔ " : "§c✘ ") + "§7" + ingredient.label()
                        + "  §f" + have + "§7/§f" + ingredient.amount());
            }
            lore.add("");
            lore.add(canCraft
                    ? "§aKliknij, aby wytworzyć"
                    : "§cBrakuje surowców w ekwipunku");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Przepis przypisany do slotu treści w danej kategorii (albo null). */
    private CraftRecipe recipeAt(int activeSlot, int slot) {
        List<CraftRecipe> recipes = CATEGORY_RECIPES.get(activeSlot);
        if (recipes == null) return null;
        for (CraftRecipe recipe : recipes) {
            int index = recipe.contentIndex();
            if (index >= 0 && index < CONTENT_SLOTS.length
                    && CONTENT_SLOTS[index] == slot) {
                return recipe;
            }
        }
        return null;
    }

    /** Kategoria pod danym slotem (albo null, gdy slot nie jest kategorią). */
    private Category categoryAt(int slot) {
        for (Category c : CATEGORIES) {
            if (c.slot() == slot) return c;
        }
        return null;
    }

    // ─────────────────────── OBSŁUGA KLIKNIĘĆ ─────────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof CraftingCommandHolder)) return;
        event.setCancelled(true); // nic nie wyciągniesz z GUI — kliknięcie w przepis WYTWARZA
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Klik w nasze GUI (górna część)
        if (event.getClickedInventory() == null
                || !(event.getClickedInventory().getHolder() instanceof CraftingCommandHolder)) {
            return;
        }

        int slot = event.getSlot();

        // „Wyjście" (ostatni slot) — zamyka GUI
        if (slot == SLOT_EXIT) {
            player.closeInventory();
            return;
        }

        CraftingCommandHolder holder =
                (CraftingCommandHolder) event.getInventory().getHolder();

        // Klik w przedmiot w sekcji treści = wytwarzanie (pobiera surowce
        // z ekwipunku gracza i wydaje wynik).
        CraftRecipe recipe = recipeAt(holder.getActiveCategory(), slot);
        if (recipe != null) {
            craft(player, recipe);
            return;
        }

        // Klik w kategorię: otwórz IDENTYCZNE GUI z tą kategorią jako aktywną
        // (efekt zaklęcia). Klik w już aktywną kategorię = powrót do menu głównego.
        if (categoryAt(slot) != null) {
            int newActive = (holder.getActiveCategory() == slot) ? -1 : slot;
            // Zapamiętaj wybór — przy kolejnym /wytwarzanie gracz wróci tutaj
            // (zapis w raft-players.yml, przetrwa restart serwera).
            setLastCategory(player, newActive);
            openGui(player, newActive);
        }
    }

    // ─────────────────────── WYTWARZANIE ─────────────────────────

    /**
     * Wytwarza przedmiot: sprawdza i pobiera składniki z ekwipunku, wydaje
     * wynik (nadmiar wypada pod graczem) i odświeża podpowiedzi w GUI.
     * Gdy czegoś brakuje — tylko odświeża GUI (żadnych komunikatów na czacie).
     */
    private void craft(Player player, CraftRecipe recipe) {
        for (Ingredient ingredient : recipe.ingredients()) {
            if (countInInventory(player, ingredient) < ingredient.amount()) {
                refreshContent(player);
                return;
            }
        }
        for (Ingredient ingredient : recipe.ingredients()) {
            takeFromInventory(player, ingredient);
        }

        ItemManager im = plugin.getItemManager();
        ItemStack result = im == null ? null : im.createItem(recipe.resultItemId());
        if (result != null) {
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(result);
            for (ItemStack stack : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), stack);
            }
        }
        player.updateInventory();
        refreshContent(player);
    }

    /** Odświeża sekcję treści w OTWARTYM GUI gracza (stan ✔/✘ po wytworzeniu). */
    private void refreshContent(Player player) {
        InventoryView view = player.getOpenInventory();
        if (!(view.getTopInventory().getHolder() instanceof CraftingCommandHolder holder)) {
            return;
        }
        fillContent(view.getTopInventory(), holder.getActiveCategory(), player);
    }

    /** Liczy składnik w głównym ekwipunku (bez zbroi i ręki drugiej). */
    private int countInInventory(Player player, Ingredient ingredient) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (matches(stack, ingredient)) total += stack.getAmount();
        }
        return total;
    }

    /** Zabiera składniki z ekwipunku (wywołuj po sprawdzeniu, że jest dość). */
    private void takeFromInventory(Player player, Ingredient ingredient) {
        int left = ingredient.amount();
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack stack = contents[i];
            if (!matches(stack, ingredient)) continue;
            int take = Math.min(left, stack.getAmount());
            stack.setAmount(stack.getAmount() - take);
            left -= take;
            if (stack.getAmount() <= 0) {
                player.getInventory().setItem(i, null);
            }
        }
    }

    /**
     * Czy przedmiot pasuje do składnika. Sam materiał = wystarczy (każdy wariant,
     * np. waniliowa deska albo „Deska” z pluginu). Z podanym przedmiotem pluginu
     * wymagane jest także CustomModelData tego przedmiotu (rozpoznanie odporne
     * na różnice w meta — jak isConstructionHammer).
     */
    private boolean matches(ItemStack stack, Ingredient ingredient) {
        if (stack == null || stack.getType() != ingredient.material()) return false;
        if (ingredient.customItemId() == null) return true;
        ItemManager im = plugin.getItemManager();
        if (im == null) return false;
        CustomItem custom = im.getItem(ingredient.customItemId());
        if (custom == null) return false;
        ItemMeta wanted = custom.getItemStack().getItemMeta();
        ItemMeta have = stack.getItemMeta();
        if (wanted == null || have == null) return false;
        if (wanted.hasCustomModelData()
                && (!have.hasCustomModelData()
                    || have.getCustomModelData() != wanted.getCustomModelData())) {
            return false;
        }
        return true;
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        // Przeciąganie przedmiotów do menu = zablokowane
        if (event.getInventory().getHolder() instanceof CraftingCommandHolder) {
            event.setCancelled(true);
        }
    }

    /**
     * Holder GUI — pozwala rozpoznać nasze GUI w InventoryClickEvent
     * (getHolder() instanceof CraftingCommandHolder) bez porównywania tytułów
     * i przechowuje slot AKTYWNEJ kategorii (-1 = menu główne).
     */
    public static final class CraftingCommandHolder implements InventoryHolder {
        private Inventory inventory;
        private int activeCategory = -1;

        void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        int getActiveCategory() {
            return activeCategory;
        }

        void setActiveCategory(int slot) {
            this.activeCategory = slot;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
