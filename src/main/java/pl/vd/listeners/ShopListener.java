package pl.vd.listeners;

import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import pl.vd.VillagerDefensePlugin;
import pl.vd.arena.Arena;
import pl.vd.kits.Kit;
import pl.vd.shop.ShopCategory;
import pl.vd.shop.ShopGUI;
import pl.vd.shop.ShopHolder;
import pl.vd.shop.ShopItem;
import pl.vd.shop.ShopItemType;
import pl.vd.util.AntiSpam;
import pl.vd.util.Lang;
import pl.vd.util.WeatherUtil;

import java.util.ArrayList;
import java.util.List;

public class ShopListener implements Listener {

    private final VillagerDefensePlugin plugin;

    public ShopListener(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
    }

    /** Czy to panel KATEGORII ustawień (nie główny panel ustawień)? */
    private boolean isSettingsCategoryView(ShopHolder.ViewType type) {
        return type == ShopHolder.ViewType.FLOOR_SETTINGS
                || type == ShopHolder.ViewType.PARTICLE_SETTINGS
                || type == ShopHolder.ViewType.SOUND_SETTINGS
                || type == ShopHolder.ViewType.NOTIFICATION_SETTINGS
                || type == ShopHolder.ViewType.VISIBILITY_SETTINGS
                || type == ShopHolder.ViewType.COMPASS_SETTINGS
                || type == ShopHolder.ViewType.KIT_SLOTS_SETTINGS
                || type == ShopHolder.ViewType.LANG_SETTINGS;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof ShopHolder holder)) return;

        event.setCancelled(true);
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(top)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
            player.closeInventory(); // duch nie kupuje w sklepie
            return;
        }

        int slot = event.getSlot();
        var shopGUI = plugin.getShopGUI();

        // Panel ulepszeń wieżyczki: przyciski wariantów (sloty 10/12/14/16/22),
        // wyjście (gwiazda, slot 31) i punkty (szmaragd, slot 35, nieklikalny).
        // Klik przycisku PODMIENIA postawioną wieżyczkę na wybrany wariant.
        if (holder.getViewType() == ShopHolder.ViewType.TURRET_UPGRADE) {
            if (slot == 31) {
                player.closeInventory(); // wyjście
            } else if (slot == ShopGUI.TURRET_POINTS_SLOT) {
                // tylko podgląd punktów - nieklikalne
            } else {
                ShopGUI.TurretUpgrade upgrade = ShopGUI.turretUpgradeForSlot(slot);
                if (upgrade != null) {
                    ArmorStand stand = holder.getTurretStand();
                    player.closeInventory();
                    shopGUI.upgradeTurret(player, stand, upgrade);
                }
            }
            return;
        }

        Arena arena = plugin.getShopGUI().resolveArena(player);
        // Panele ustawień DZIAŁAJĄ BEZ areny — gracz może zmieniać
        // ustawienia z /ustawienia niezależnie od gry.
        if (arena == null && !isSettingsCategoryView(holder.getViewType())
                && holder.getViewType() != ShopHolder.ViewType.SETTINGS
                && holder.getViewType() != ShopHolder.ViewType.SUPPLY_SOUNDS
                && holder.getViewType() != ShopHolder.ViewType.FOOD_BULK) {
            // FOOD_BULK obsługujemy niżej: gwiazda ma wrócić do jedzenia nawet
            // wtedy, gdy podgląd areny nie jest już dostępny (bez zamykania GUI).
            player.closeInventory();
            return;
        }

        if (holder.getViewType() == ShopHolder.ViewType.ARMOR_DOWNGRADE) {
            handleArmorDowngradeClick(player, arena, holder, slot);
            return;
        }

        if (holder.getViewType() == ShopHolder.ViewType.ENCHANT_TARGET) {
            handleEnchantTargetClick(player, arena, holder, slot, top);
            return;
        }

        if (holder.getViewType() == ShopHolder.ViewType.FOOD_BULK) {
            if (slot == ShopGUI.FOOD_BULK_BACK_SLOT) {
                // Nether star nie zamyka sklepu - wraca do zakładki jedzenia.
                shopGUI.openCategory(player, arena, holder.getCategory(), holder.getPage());
                return;
            }
            if (slot == ShopGUI.FOOD_BULK_POINTS_SLOT) return; // tylko podgląd punktów
            ShopItem food = holder.getSelectedItem();
            int quantity = shopGUI.foodBulkQuantityForSlot(slot);
            if (food != null && quantity > 0) {
                shopGUI.purchaseFoodBulk(player, arena, food, quantity);
                shopGUI.openFoodBulk(player, arena, food); // odśwież aktualne punkty
            }
            return;
        }

        if (holder.getViewType() == ShopHolder.ViewType.MAGE_UPGRADES) {
            if (shopGUI.isBackSlot(slot, top.getSize())) {
                shopGUI.openMain(player, arena); // gwiazda wraca do menu sklepu
            }
            return; // na razie nie ma innych przycisków
        }

        if (shopGUI.isPointsSlot(slot, top.getSize())) return; // tylko podgląd, nieklikalne

        // strzałki „Poprzednia/Następna kategoria" w panelach KATEGORII ustawień
        // (nie w głównym panelu ustawień): cykliczne przełączanie kategorii
        // (Pogoda -> Cząsteczki -> Dźwięki -> Powiadomienia -> Widoczności ->
        // Sklep -> Klasy -> Język -> ... w kółko)
        if (isSettingsCategoryView(holder.getViewType())) {
            if (shopGUI.isPrevCategorySlot(slot, top.getSize())) {
                shopGUI.openPrevCategory(player, arena, holder.getViewType());
                return;
            }
            if (shopGUI.isNextCategorySlot(slot, top.getSize())) {
                shopGUI.openNextCategory(player, arena, holder.getViewType());
                return;
            }
        }

        if (holder.getViewType() == ShopHolder.ViewType.SUPPLY_SOUNDS) {
            // podmenu dźwięków zrzutu zaopatrzenia (4x9 = 36 slotów): powrót
            // (nether star na środku dołu, slot 31) do panelu DŹWIĘKÓW; betonowe
            // wskaźniki pod skrzyniami faz cyklują głośność danej fazy zrzutu -
            // LPM = głośniej, PPM = ciszej
            if (shopGUI.isBackSlot(slot, top.getSize())) {
                shopGUI.openSoundSettings(player, arena);
            } else {
                String key = shopGUI.supplySoundKeyForIndicatorSlot(slot);
                if (key != null) {
                    if (slot == ShopGUI.SUPPLY_SLOTMACHINE_INDICATOR_SLOT) {
                        // „Automat łupu": ON/OFF zamiast suwaka głośności (0% <-> 100%)
                        toggleSoundOnOff(player, arena, key, () -> shopGUI.openSupplySoundSettings(player, arena));
                    } else {
                        cycleSoundVolume(player, arena, key, !event.isRightClick(), () -> shopGUI.openSupplySoundSettings(player, arena));
                    }
                }
            }
            return;
        }

        if (holder.getViewType() == ShopHolder.ViewType.NOTIFICATION_SETTINGS) {
            // podmenu powiadomień (4x9 = 36 slotów): powrót (nether star na środku
            // dołu, slot 31) do panelu USTAWIEŃ; betonowe wskaźniki pod dzwonami
            // przełączają dany kanał powiadomień (czat / action bar / ekran)
            if (shopGUI.isBackSlot(slot, top.getSize())) {
                shopGUI.openSettings(player, arena);
            } else {
                String key = shopGUI.notifKeyForIndicatorSlot(slot);
                if (key != null) {
                    toggleNotificationChannel(player, arena, key);
                }
            }
            return;
        }

        if (holder.getViewType() == ShopHolder.ViewType.VISIBILITY_SETTINGS) {
            // panel widoczności (6x9 = 54 sloty): powrót (nether star na środku
            // dołu, slot 49) do panelu USTAWIEŃ; betonowe wskaźniki pod redstone
            // lampami przełączają daną widoczność Włączone/Wyłączone (100%/0%)
            if (shopGUI.isBackSlot(slot, top.getSize())) {
                shopGUI.openSettings(player, arena);
            } else {
                String key = shopGUI.visibilityKeyForIndicatorSlot(slot);
                if (key != null) {
                    var data = plugin.getPlayerDataManager().get(player.getUniqueId(), player.getName());
                    int current = data.getSettingInt(key, 100);
                    data.setSettingInt(key, ShopGUI.toggleNotificationLevel(current));
                    plugin.getPlayerDataManager().saveAll();
                    plugin.getShopGUI().openVisibilitySettings(player, arena); // odśwież widok
                }
            }
            return;
        }

        if (holder.getViewType() == ShopHolder.ViewType.FLOOR_SETTINGS) {
            // podmenu ustawień pogody (3x9 = 27 slotów): powrót (nether star na
            // środku dołu, slot 22) do panelu USTAWIEŃ; zegar „Pora dnia" (slot 13
            // w grze) przesuwa porę dnia - LPM = następna, PPM = poprzednia;
            // zegar „Zmiana pogody" (slot 17 w grze) cykluje pogodę - oba
            // zapisują wybór per gracz i stosują od razu
            if (shopGUI.isBackSlot(slot, top.getSize())) {
                shopGUI.openSettings(player, arena);
            } else if (slot == ShopGUI.FLOOR_CLOCK_SLOT_1) {
                cycleTimeOfDay(player, arena, !event.isRightClick());
            } else if (slot == ShopGUI.FLOOR_CLOCK_SLOT_2) {
                cycleWeather(player, arena, !event.isRightClick());
            }
            return;
        }

        if (holder.getViewType() == ShopHolder.ViewType.LANG_SETTINGS) {
            // podmenu ustawień języka (3x9 = 27 slotów): powrót (nether star na
            // środku dołu, slot 22) do panelu USTAWIEŃ; kliknięcie książki wybiera
            // dany język per gracz (zapis w players.yml)
            if (shopGUI.isBackSlot(slot, top.getSize())) {
                shopGUI.openSettings(player, arena);
            } else if (slot == ShopGUI.LANG_POLISH_SLOT) {
                selectLanguage(player, arena, ShopGUI.LANG_POLISH);
            } else if (slot == ShopGUI.LANG_ENGLISH_SLOT) {
                selectLanguage(player, arena, ShopGUI.LANG_ENGLISH);
            }
            return;
        }

        if (holder.getViewType() == ShopHolder.ViewType.KIT_SLOTS_SETTINGS) {
            // panel ustawiania slotów kitu (6x9 = 54 sloty): powrót (nether star
            // na środku dołu, slot 49) do panelu USTAWIEŃ; kliknięcie klasy
            // otwiera GUI układania przedmiotów dla tej klasy
            if (shopGUI.isBackSlot(slot, top.getSize())) {
                shopGUI.openSettings(player, arena);
            } else {
                String kitId = findKitBySlotIcon(event.getCurrentItem());
                if (kitId != null) {
                    shopGUI.openKitSlotsEdit(player, arena, kitId);
                }
            }
            return;
        }

        if (holder.getViewType() == ShopHolder.ViewType.KIT_SLOTS_EDIT) {
            // GUI układania przedmiotów klasy (3x9): środek (sloty 9-17) to pasek
            // klasy przy starcie areny. Gracz może WZIĄĆ przedmiot na myszkę
            // (LPM/PPM) i postawić go w innym miejscu paska; Zapisz (zielony
            // beton) zapamiętuje układ per gracz, Reset (czerwony) przywraca
            // domyślny pasek klasy.
            if (slot == ShopGUI.KIT_SLOTS_EDIT_BACK_SLOT) {
                shopGUI.openKitSlotsSettings(player, arena);
            } else if (slot == ShopGUI.KIT_SLOTS_EDIT_SAVE_SLOT) {
                saveKitSlots(player, top, holder.getKitId());
            } else if (slot == ShopGUI.KIT_SLOTS_EDIT_RESET_SLOT) {
                resetKitSlots(player, arena, holder.getKitId());
            } else if (slot >= ShopGUI.KIT_SLOTS_EDIT_FIRST_SLOT && slot <= ShopGUI.KIT_SLOTS_EDIT_LAST_SLOT) {
                // przestawianie przedmiotów: tylko zwykłe kliknięcia myszką
                // (podnieś / odłóż / zamień); shift, klawisze 1-9 i wyrzucanie
                // są anulowane, żeby przedmioty nie opuszczały paska 9-17
                ClickType click = event.getClick();
                if (click == ClickType.LEFT || click == ClickType.RIGHT) {
                    event.setCancelled(false);
                }
            }
            return;
        }

        if (holder.getViewType() == ShopHolder.ViewType.COMPASS_SETTINGS) {
            // panel kompasu (6x9 = 54 sloty): powrót (nether star na środku dołu,
            // slot 49) do panelu USTAWIEŃ, reszta to szkło/placeholdery
            if (shopGUI.isBackSlot(slot, top.getSize())) {
                shopGUI.openSettings(player, arena);
            }
            return;
        }

        if (holder.getViewType() == ShopHolder.ViewType.PARTICLE_SETTINGS) {
            // panel cząsteczek: powrót (nether star na środku dołu) do USTAWIEŃ;
            // betonowe wskaźniki pod firework starami zmieniają ilość cząsteczek
            // danej kategorii o jeden próg - LPM = więcej, PPM = mniej.
            if (shopGUI.isBackSlot(slot, top.getSize())) {
                shopGUI.openSettings(player, arena);
            } else {
                String key = shopGUI.particleKeyForIndicatorSlot(slot);
                if (key == null) return;
                if (slot == ShopGUI.PARTICLE_GENERAL_INDICATOR_SLOT) {
                    // globalny suwak „Ogólne" (slot 19) - JEDYNY z opcją
                    // NIESTANDARDOWE (różowy beton, między 100% a 0%): cykl
                    // 0 -> 25 -> 50 -> 75 -> 100 -> NIESTANDARDOWE -> 0.
                    // Wartość liczbowa ustawia się na WSZYSTKICH kategoriach,
                    // ale wejście w NIESTANDARDOWE NIE rusza pozostałych - każda
                    // kategoria ma wtedy własną wartość.
                    var data = plugin.getPlayerDataManager().get(player.getUniqueId(), player.getName());
                    int current = data.getSettingInt(ShopGUI.PARTICLE_GENERAL_KEY, 100);
                    boolean custom = current == ShopGUI.PARTICLE_GENERAL_CUSTOM;
                    int next = !event.isRightClick()
                            ? ShopGUI.nextGeneralParticleState(current, custom)
                            : ShopGUI.previousGeneralParticleState(current, custom);
                    if (next == ShopGUI.PARTICLE_GENERAL_CUSTOM) {
                        data.setSettingInt(ShopGUI.PARTICLE_GENERAL_KEY, next);
                    } else {
                        for (String k : ShopGUI.PARTICLE_KEYS) {
                            data.setSettingInt(k, next);
                        }
                    }
                    plugin.getPlayerDataManager().saveAll();
                    plugin.getShopGUI().openParticleSettings(player, arena);
                } else {
                    changeParticles(player, arena, key, !event.isRightClick());
                }
            }
            return;
        }

        if (holder.getViewType() == ShopHolder.ViewType.SOUND_SETTINGS) {
            // panel dźwięków: powrót (nether star na środku dołu) do USTAWIEŃ;
            // note blocki + betonowe wskaźniki pod nimi to przełączniki włącz/wyłącz
            // per gracz (klik na którykolwiek z pary przełącza dźwięk)
            if (shopGUI.isBackSlot(slot, top.getSize())) {
                shopGUI.openSettings(player, arena);
            } else if (slot == ShopGUI.SOUND_MOBS_INDICATOR_SLOT) {
                // LPM = głośniej, PPM = ciszej
                cycleSoundVolume(player, arena, ShopGUI.SOUND_MOBS_KEY, !event.isRightClick(), () -> shopGUI.openSoundSettings(player, arena));
                plugin.getGameManager().refreshMobSoundMuting(arena); // zastosuj na arenie od razu
            } else if (slot == ShopGUI.SOUND_TURRETS_INDICATOR_SLOT) {
                cycleSoundVolume(player, arena, ShopGUI.SOUND_TURRETS_KEY, !event.isRightClick(), () -> shopGUI.openSoundSettings(player, arena));
                // wieżyczki nie mają jeszcze dźwięków - ustawienie tylko zapisane;
                // gdy dodamy strzały, będą je sprawdzać (GameManager.soundVolume)
            } else if (slot == ShopGUI.SOUND_EXPLOSIONS_INDICATOR_SLOT) {
                cycleSoundVolume(player, arena, ShopGUI.SOUND_EXPLOSIONS_KEY, !event.isRightClick(), () -> shopGUI.openSoundSettings(player, arena));
                // wybuchy (miny, TNT) przytłumiane per gracz w SoundMuteListener
            } else if (slot == ShopGUI.SOUND_ABILITIES_INDICATOR_SLOT) {
                cycleSoundVolume(player, arena, ShopGUI.SOUND_ABILITIES_KEY, !event.isRightClick(), () -> shopGUI.openSoundSettings(player, arena));
                // dźwięki klas odtwarzane per gracz z jego głośnością (playSoundNearby)
            } else if (slot == ShopGUI.SOUND_ALLIES_INDICATOR_SLOT) {
                cycleSoundVolume(player, arena, ShopGUI.SOUND_ALLIES_KEY, !event.isRightClick(), () -> shopGUI.openSoundSettings(player, arena));
                // dźwięki sojuszników (warden, bałwan, wilk...) przytłumiane per gracz
            } else if (slot == ShopGUI.SOUND_CLASSES_INDICATOR_SLOT) {
                cycleSoundVolume(player, arena, ShopGUI.SOUND_CLASSES_KEY, !event.isRightClick(), () -> shopGUI.openSoundSettings(player, arena));
                // dźwięki klas (umiejętności klas) - ustawienie tylko zapisane na razie
            } else if (slot == ShopGUI.SOUND_SUPPLY_LABEL_SLOT) {
                // „Dźwięki zrzutu zaopatrzenia" (slot 44 w grze) otwiera podmenu zrzutu
                shopGUI.openSupplySoundSettings(player, arena);
            }
            return;
        }

        if (holder.getViewType() == ShopHolder.ViewType.SETTINGS) {
            // panel ustawień: nether star na środku dołu - ze sklepu WRACA do menu
            // sklepu, z komendy /ustawienia zamyka GUI; ikony otwierają podmenu
            if (shopGUI.isBackSlot(slot, top.getSize())) {
                if (shopGUI.isSettingsFromShop(player)) {
                    shopGUI.openMain(player, arena); // wejście ze sklepu: wróć do sklepu
                } else {
                    player.closeInventory(); // /ustawienia: zamknij GUI
                }
            } else if (slot == ShopGUI.SETTINGS_PARTICLES_SLOT) {
                shopGUI.openParticleSettings(player, arena);
            } else if (slot == ShopGUI.SETTINGS_SOUNDS_SLOT) {
                shopGUI.openSoundSettings(player, arena);
            } else if (slot == ShopGUI.SETTINGS_NOTIF_SLOT) {
                // dzwon „Powiadomienia" (slot 17 w grze) otwiera podmenu powiadomień
                shopGUI.openNotificationSettings(player, arena);
            } else if (slot == ShopGUI.SETTINGS_VISIBILITY_SLOT) {
                // redstone_lamp „Widoczności" (slot 29 w grze) otwiera panel widoczności
                shopGUI.openVisibilitySettings(player, arena);
            } else if (slot == ShopGUI.SETTINGS_FLOOR_SLOT) {
                shopGUI.openFloorSettings(player, arena);
            } else if (slot == ShopGUI.SETTINGS_LANG_SLOT) {
                // writable book „Język" otwiera podmenu wyboru języka
                shopGUI.openLanguageSettings(player, arena);
            } else if (slot == ShopGUI.SETTINGS_KIT_SLOTS_SLOT) {
                // netherite sword „Ustawianie slotów kitu" otwiera panel slotów kitu
                shopGUI.openKitSlotsSettings(player, arena);
            } else if (slot == ShopGUI.SETTINGS_COMPASS_SLOT) {
                // kompas otwiera panel kompasu
                shopGUI.openCompassSettings(player, arena);
            }
            return;
        }

        if (holder.getViewType() == ShopHolder.ViewType.MAIN) {
            if (shopGUI.isStarSlot(slot)) {
                player.closeInventory();
                return;
            }
            if (shopGUI.isSettingsSlot(slot)) {
                // oko endera „Ustawienia" w lewym dolnym rogu - wejście ZE SKLEPU,
                // więc nether star w ustawieniach ma wracać do menu sklepu
                shopGUI.openSettings(player, arena, true);
                return;
            }
            if (slot == ShopGUI.ACHIEVEMENTS_SLOT) {
                // butelka XP „Osiągnięcia" obok ustawień - otwiera GUI osiągnięć
                // (ze sklepu, więc „Powrót" na dole wraca do menu sklepu)
                plugin.getAchievementsGUI().open(player, pl.vd.achievements.AchievementsHolder.Source.SHOP);
                return;
            }
            ShopCategory category = shopGUI.getCategoryBySlot(slot);
            if (category != null) {
                shopGUI.openCategory(player, arena, category);
            }
            return;
        }

        // CATEGORY view (widok CUSTOM_ARROWS ma własną nawigację niżej - bez tego
        // blok kategorii przechwytywałby strzałki i nether star podmenu)
        if (holder.getViewType() == ShopHolder.ViewType.CATEGORY) {
        ShopCategory category = holder.getCategory();
        int page = holder.getPage();
        if (shopGUI.isBackSlot(slot, top.getSize())) {
            shopGUI.openMain(player, arena);
            return;
        }
        if (category == ShopCategory.BOWS && shopGUI.isCustomArrowsSlot(slot)) {
            // Nowa strzała (slot 50, o 1 więcej niż nether star na 49) otwiera podmenu
            // „Strzały - Niestandardowe Strzały" (zwykła i widmowa strzała na stronie 2).
            shopGUI.openCustomArrows(player, arena);
            return;
        }
        if (slot == shopGUI.prevPageSlot(category)) {
            shopGUI.openCategory(player, arena, category, page - 1);
            return;
        }
        if (slot == shopGUI.nextPageSlot(category)) {
            shopGUI.openCategory(player, arena, category, page + 1);
            return;
        }
        ShopCategory navTarget = shopGUI.categoryNavigationTarget(category, page, slot);
        if (navTarget != null) {
            shopGUI.openCategory(player, arena, navTarget, 0);
            return;
        }
        if (shopGUI.isMageUpgradesSlot(player, category, page, slot)) {
            shopGUI.openMageUpgrades(player, arena);
            return;
        }

        ShopItem item = shopGUI.getItemBySlot(category, page, slot);
        if (item != null) {
            // PPM na jedzeniu otwiera zakup hurtowy; LPM kupuje 1 sztukę jak dotąd.
            if (event.isRightClick() && shopGUI.isBulkFoodItem(item)) {
                shopGUI.openFoodBulk(player, arena, item);
                return;
            }
            if (item.getType() == ShopItemType.ENCHANT) {
                // Punch -> łuk, Sweeping Edge -> miecz: bez GUI wyboru, zaklęcie prosto
                // na właściwą broń gracza. Gdy gracz nie ma wymaganej broni - komunikat
                // z wymaganiem (bez zakupu, bez pobierania punktów).
                if (shopGUI.isWeaponOnlyEnchant(item)) {
                    shopGUI.purchaseWeaponEnchant(player, arena, item);
                    shopGUI.openCategory(player, arena, category, page); // odśwież widok (punkty)
                    return;
                }
                if (shopGUI.isBootsOnlyEnchant(item)) {
                    // łagodny upadek działa tylko na buty - zakup bez GUI wyboru części
                    shopGUI.purchaseEnchantToBoots(player, arena, item);
                    shopGUI.openCategory(player, arena, category, page); // odśwież widok (punkty)
                } else {
                    // pozostałe zaklęcia otwierają GUI z wyborem części zbroi / narzędzia
                    shopGUI.openEnchantTarget(player, arena, category, item, page);
                }
            } else {
                // Słabsza zbroja: najpierw 1x9 z „Akceptuj”/„Anuluj”.
                if (shopGUI.openArmorDowngradeConfirm(player, arena, item, page)) {
                    return;
                }
                shopGUI.purchase(player, arena, item);
                shopGUI.openCategory(player, arena, category, page); // odśwież widok (zaktualizowane punkty)
            }
        }
        }

        // Podmenu „Strzały - Niestandardowe Strzały" (otwierane nową strzałą obok
        // nether staru w kategorii Strzały): powrót (nether star) do kategorii Strzały,
        // nawigacja stron jak w kategoriach, a zwykła/widmowa strzała (druga strona)
        // kupuje się jak zwykłe przedmioty kategorii.
        if (holder.getViewType() == ShopHolder.ViewType.CUSTOM_ARROWS) {
            int customPage = holder.getPage();
            if (shopGUI.isBackSlot(slot, top.getSize())) {
                shopGUI.openCategory(player, arena, ShopCategory.BOWS, 0);
                return;
            }
            if (shopGUI.isCustomArrowsPrevPageSlot(slot)) {
                shopGUI.openCustomArrows(player, arena, customPage - 1);
                return;
            }
            if (shopGUI.isNextPageSlot(slot)) {
                shopGUI.openCustomArrows(player, arena, customPage + 1);
                return;
            }
            ShopItem customItem = shopGUI.getCustomArrowItemBySlot(customPage, slot);
            if (customItem != null) {
                shopGUI.purchase(player, arena, customItem);
                shopGUI.openCustomArrows(player, arena, customPage); // odśwież widok (zaktualizowane punkty)
            }
        }
    }

    /** Zmienia per-graczową ILOŚĆ cząsteczek kategorii o jeden próg (LPM:
     *  0 -> 25 -> ... -> 100, PPM: w drugą stronę), zapisuje do players.yml
     *  i odświeża panel cząsteczek. Zmiana DOWOLNEJ kategorii (nie slotu 19)
     *  przełącza globalny suwak „Ogólne" (slot 19) w tryb NIESTANDARDOWE
     *  (różowy beton) - pozostałe kategorie zostają na swoich wartościach. */
    private void changeParticles(Player player, Arena arena, String key, boolean forward) {
        var data = plugin.getPlayerDataManager().get(player.getUniqueId(), player.getName());
        int current = data.getSettingInt(key, 100);
        data.setSettingInt(key, forward
                ? ShopGUI.nextParticleLevel(current)
                : ShopGUI.previousParticleLevel(current));
        data.setSettingInt(ShopGUI.PARTICLE_GENERAL_KEY, ShopGUI.PARTICLE_GENERAL_CUSTOM);
        plugin.getPlayerDataManager().saveAll();
        plugin.getShopGUI().openParticleSettings(player, arena); // odśwież widok
    }

    /** Przesuwa per-graczową GŁOŚNOŚĆ dźwięku o jeden próg (LPM: 0 -> 25 -> ...
     *  -> 100, PPM: w drugą stronę), zapisuje do players.yml i odświeża panel,
     *  z którego przyszło kliknięcie. */
    private void cycleSoundVolume(Player player, Arena arena, String key, boolean forward, Runnable refresh) {
        var data = plugin.getPlayerDataManager().get(player.getUniqueId(), player.getName());
        int current = data.getSettingInt(key, 100);
        data.setSettingInt(key, forward
                ? ShopGUI.nextSoundVolume(current)
                : ShopGUI.previousSoundVolume(current));
        plugin.getPlayerDataManager().saveAll();
        refresh.run(); // odśwież widok
    }

    /** Przełącza dźwięk ON/OFF (100% <-> 0%) zamiast cyklicznego suwaka głośności
     *  (dla „Automat łupu" w zrzucie zaopatrzenia). */
    private void toggleSoundOnOff(Player player, Arena arena, String key, Runnable refresh) {
        var data = plugin.getPlayerDataManager().get(player.getUniqueId(), player.getName());
        int current = data.getSettingInt(key, 100);
        data.setSettingInt(key, current > 0 ? 0 : 100);
        plugin.getPlayerDataManager().saveAll();
        refresh.run(); // odśwież widok
    }

    /** Przełącza per-graczowy KANAŁ powiadomień (100% włączony <-> 0% wyłączony),
     *  zapisuje do players.yml i odświeża panel powiadomień. */
    private void toggleNotificationChannel(Player player, Arena arena, String key) {
        var data = plugin.getPlayerDataManager().get(player.getUniqueId(), player.getName());
        int current = data.getSettingInt(key, 100);
        data.setSettingInt(key, ShopGUI.toggleNotificationLevel(current));
        plugin.getPlayerDataManager().saveAll();
        plugin.getShopGUI().openNotificationSettings(player, arena); // odśwież widok
    }

    /** Przesuwa per-graczową PORĘ DNIA (LPM: ranek -> południe -> wieczór -> noc
     *  -> normalna, PPM: w drugą stronę), zapisuje do players.yml, stosuje od
     *  razu i odświeża panel pogody. */
    private void cycleTimeOfDay(Player player, Arena arena, boolean forward) {
        var data = plugin.getPlayerDataManager().get(player.getUniqueId(), player.getName());
        String current = data.getSetting(WeatherUtil.TIME_KEY, "normalna");
        String next = forward
                ? WeatherUtil.next(WeatherUtil.TIME_OPTIONS, current)
                : WeatherUtil.previous(WeatherUtil.TIME_OPTIONS, current);
        data.setSetting(WeatherUtil.TIME_KEY, next);
        plugin.getPlayerDataManager().saveAll();
        WeatherUtil.applyTimeOfDay(player, next);
        plugin.getShopGUI().openFloorSettings(player, arena); // odśwież widok
        AntiSpam.send(player, Lang.get(player, "set-time-changed", WeatherUtil.label(player, next)));
    }

    /** Wybiera per-graczowy JĘZYK (polski/angielski), zapisuje do players.yml
     *  i odświeża panel języka. Komunikat potwierdzający w wybranym języku. */
    private void selectLanguage(Player player, Arena arena, String lang) {
        var data = plugin.getPlayerDataManager().get(player.getUniqueId(), player.getName());
        data.setSetting(ShopGUI.LANG_KEY, lang);
        plugin.getPlayerDataManager().saveAll();
        plugin.getShopGUI().openLanguageSettings(player, arena); // odśwież widok
        AntiSpam.send(player, Lang.get(player, "set-language-changed",
                ShopGUI.LANG_ENGLISH.equals(lang) ? "English" : "Polski"));
    }

    /** Przesuwa per-graczową POGODĘ (LPM: słonecznie -> deszcz -> burza -> normalna,
     *  PPM: w drugą stronę), zapisuje do players.yml, stosuje od razu i odświeża
     *  panel pogody. */
    private void cycleWeather(Player player, Arena arena, boolean forward) {
        var data = plugin.getPlayerDataManager().get(player.getUniqueId(), player.getName());
        String current = data.getSetting(WeatherUtil.WEATHER_KEY, "normalna");
        String next = forward
                ? WeatherUtil.next(WeatherUtil.WEATHER_OPTIONS, current)
                : WeatherUtil.previous(WeatherUtil.WEATHER_OPTIONS, current);
        data.setSetting(WeatherUtil.WEATHER_KEY, next);
        plugin.getPlayerDataManager().saveAll();
        WeatherUtil.applyWeather(player, next);
        plugin.getShopGUI().openFloorSettings(player, arena); // odśwież widok
        AntiSpam.send(player, Lang.get(player, "set-weather-changed", WeatherUtil.label(player, next)));
    }

    /**
     * Obsługa potwierdzenia zamiany zbroi na słabszą (1x9). Zielona szyba
     * kupuje i zakłada wybraną część, czerwona tylko wraca do kategorii Zbroje.
     */
    private void handleArmorDowngradeClick(Player player, Arena arena, ShopHolder holder, int slot) {
        var shopGUI = plugin.getShopGUI();
        ShopItem item = holder.getSelectedItem();
        if (item == null) {
            shopGUI.openCategory(player, arena, ShopCategory.ARMOR, holder.getPage());
            return;
        }
        if (slot == ShopGUI.ARMOR_CONFIRM_ACCEPT_SLOT) {
            shopGUI.purchaseConfirmedArmorDowngrade(player, arena, item);
            shopGUI.openCategory(player, arena, ShopCategory.ARMOR, holder.getPage());
        } else if (slot == ShopGUI.ARMOR_CONFIRM_CANCEL_SLOT) {
            shopGUI.openCategory(player, arena, ShopCategory.ARMOR, holder.getPage());
        }
    }

    /**
     * Obsługa GUI wyboru części zbroi/narzędzia dla zaklęć (3x9 slotów).
     * Kliknięcie w część zbroi ulepsza ją i wraca do kategorii zaklęć.
     */
    private void handleEnchantTargetClick(Player player, Arena arena, ShopHolder holder, int slot, Inventory top) {
        var shopGUI = plugin.getShopGUI();
        ShopCategory category = holder.getCategory();
        ShopItem item = holder.getSelectedItem();
        int page = holder.getPage();
        if (item == null || item.getType() != ShopItemType.ENCHANT) {
            shopGUI.openCategory(player, arena, category, page);
            return;
        }

        if (shopGUI.isPointsSlot(slot, top.getSize())) return; // tylko podgląd
        // czerwona szyba = zaklęcie nie pasuje do tej części; kliknięcie nic nie robi
        ItemStack clicked = top.getItem(slot);
        if (clicked != null && clicked.getType() == Material.RED_STAINED_GLASS_PANE) return;
        if (shopGUI.isBackSlot(slot, top.getSize())) {
            shopGUI.openCategory(player, arena, category, page);
            return;
        }

        // zaklęcie tylko na broń (Uderzenie/Punch): sloty zbroi są zablokowane -
        // kliknięcie w nie nic nie robi (i nie pobiera punktów)
        if (shopGUI.isWeaponRestrictedEnchant(item) && shopGUI.isEnchantArmorSlot(slot)) {
            AntiSpam.sendActionBar(player, Lang.get(player, "enchant-not-for-item"));
            return;
        }
        // zaklęcia tylko na zbroję (Ochrona/Ciernie): sloty broni są zablokowane
        if (shopGUI.isArmorOnlyEnchant(item) && shopGUI.isEnchantWeaponSlot(slot)) {
            AntiSpam.sendActionBar(player, Lang.get(player, "enchant-not-for-item"));
            return;
        }

        if (shopGUI.enchantEquipment(player, arena, item, slot)) {
            // po udanym ulepszeniu zamknij GUI i wróć do kategorii zaklęć (ta sama strona)
            shopGUI.openCategory(player, arena, category, page);
        }
    }

    /** Zapisuje układ przedmiotów (sloty 9-17 z edytora klasy) do players.yml
     *  jako lista sygnatur oddzielonych przecinkami. */
    private void saveKitSlots(Player player, Inventory top, String kitId) {
        if (kitId == null) return;
        List<String> sigs = new ArrayList<>(9);
        for (int s = ShopGUI.KIT_SLOTS_EDIT_FIRST_SLOT; s <= ShopGUI.KIT_SLOTS_EDIT_LAST_SLOT; s++) {
            sigs.add(ShopGUI.kitSlotSignature(top.getItem(s)));
        }
        var data = plugin.getPlayerDataManager().get(player.getUniqueId(), player.getName());
        data.setSetting(ShopGUI.kitSlotsSettingKey(kitId), String.join(",", sigs));
        plugin.getPlayerDataManager().saveAll();
        Kit kit = plugin.getKitManager().getKit(kitId);
        AntiSpam.send(player, Lang.get(player, "set-kit-slots-saved",
                kit != null ? kit.localizedName(player) : kitId));
    }

    /** Usuwa zapisany układ przedmiotów klasy i otwiera edytor z domyślnym paskiem. */
    private void resetKitSlots(Player player, Arena arena, String kitId) {
        if (kitId == null) return;
        var data = plugin.getPlayerDataManager().get(player.getUniqueId(), player.getName());
        data.getSettings().remove(ShopGUI.kitSlotsSettingKey(kitId));
        plugin.getPlayerDataManager().saveAll();
        Kit kit = plugin.getKitManager().getKit(kitId);
        AntiSpam.send(player, Lang.get(player, "set-kit-slots-reset",
                kit != null ? kit.localizedName(player) : kitId));
        plugin.getShopGUI().openKitSlotsEdit(player, arena, kitId); // odśwież edytor
    }

    /** Znajduje ID klasy po ikonie klikniętej w panelu „Ustawienia klas" (typ + nazwa). */
    private String findKitBySlotIcon(ItemStack clicked) {
        if (clicked == null || clicked.getType() == Material.AIR) return null;
        for (String id : ShopGUI.KIT_SLOTS_ORDER) {
            Kit kit = plugin.getKitManager().getKit(id);
            if (kit == null || kit.getIcon() == null) continue;
            if (clicked.getType() == kit.getIcon().getType()) return id;
        }
        return null;
    }

}
