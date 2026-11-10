package pl.vd.listeners;

import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.inventory.ItemStack;
import pl.vd.VillagerDefensePlugin;
import pl.vd.stand.ArmorStandTuner;
import pl.vd.util.AntiSpam;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class StandListener implements Listener {

    private final VillagerDefensePlugin plugin;

    public StandListener(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof ArmorStand stand)) return;
        ArmorStandTuner tuner = plugin.getArmorStandTuner();
        Player player = event.getPlayer();

        // Krzesło: kliknięcie OZNACZONEGO stojaka (mini stojak z siodłem z /stojak funkcja
        // siedzenie) = siadanie. Działa dla KAŻDEGO gracza (funkcja dekoracji/areny),
        // bez żadnego przedmiotu - sam klik = siadanie, Shift = wstanie.
        if (tuner.isSeat(stand)) {
            event.setCancelled(true);
            if (!tuner.sitOn(player, stand)) {
                AntiSpam.sendActionBar(player, "§cNie możesz teraz usiąść (już gdzieś siedzisz?).");
            }
            return;
        }

        // Klik-przełącznik stanów (/stojak funkcja zmiana <A> <B>): kliknięcie podmienia
        // części między dwoma zapisanymi stanami (płynne przejście), kolejne kliknięcie
        // wraca do stanu początkowego. Działa dla KAŻDEGO gracza, z dowolnym przedmiotem
        // w ręce - oprócz narzędzi /stojak (dajitemy), przedmiotów do stawiania
        // (shop/saved) i różdżki teleportacji, które robią swoje (admin nadal stroi).
        if (tuner.isStateToggle(stand)) {
            ItemStack handItem = player.getInventory().getItemInMainHand();
            boolean placing = plugin.getStandPresetManager().isShopItem(handItem)
                    || plugin.getStandPresetManager().isSavedItem(handItem)
                    || plugin.getTeleportWandManager().isWand(handItem);
            if (!placing && !TUNER_TOOLS.contains(handItem.getType())) {
                event.setCancelled(true); // zawsze blokuj vanillowy edytor stojaka
                tuner.toggleState(stand, player);
                return;
            }
        }

        // Wieżyczka (ta, która strzela): prawy klik pokazuje jej statystyki na czacie,
        // a dla PODSTAWOWEJ wieżyczki maga otwiera też panel ulepszeń. Działa dla
        // KAŻDEGO gracza, z dowolnym przedmiotem w ręce - oprócz broni dystansowych
        // (łuk/kusza/trójząb), narzędzi /stojak (dajitemy) i różdżki magika (BLAZE_ROD),
        // które robią swoje, oraz przedmiotów do stawiania (shop/saved) i różdżki
        // teleportacji.
        if (tuner.isDestructibleTurret(stand)) {
            ItemStack handItem = player.getInventory().getItemInMainHand();
            boolean placing = plugin.getStandPresetManager().isShopItem(handItem)
                    || plugin.getStandPresetManager().isSavedItem(handItem)
                    || plugin.getTeleportWandManager().isWand(handItem);
            if (!placing) {
                event.setCancelled(true); // zawsze blokuj vanillowy edytor stojaka
                Material hand = handItem.getType();
                boolean interactive = !RANGED_WEAPONS.contains(hand) && !TUNER_TOOLS.contains(hand);
                // Zwykłym prawym klikiem (pusta ręka itd.) KAŻDA wieżyczka pokazuje
                // na czacie swoje AKTUALNE statystyki - wartości czyta się z PDC
                // stojaka (nie z pliku presetu), więc po ulepszeniach wieżyczki
                // pokażą się od razu nowe liczby. Narzędziami /stojak (dajitemy)
                // i bronią dystansową tego nie robimy - admin nadal stroi nimi
                // wieżyczkę, a broń robi swoje.
                if (interactive) {
                    sendTurretStats(player, stand);
                }
                // GUI ulepszeń otwiera się TYLKO dla PODSTAWOWEJ wieżyczki maga
                // (wiezyczka_maga); warianty żywiołów (lód/ogień/trutka/elektryk)
                // i zwykła wieżyczka już go nie otwierają.
                if (interactive && tuner.isBaseMageTurret(stand)) {
                    plugin.getShopGUI().openTurretUpgrade(player, stand);
                    return;
                }
            }
            // przedmiot do stawiania albo narzędzie/broń: lecimy dalej jak zwykle
        }

        if (!player.hasPermission("vd.admin")) {
            // Gracze bez uprawnień mogą tylko STAWIAĆ wieżyczkę kupioną w sklepie
            // areny (prawy klik przedmiotem "Wieżyczka" na stojaku) - nic więcej.
            if (plugin.getStandPresetManager().isShopItem(player.getInventory().getItemInMainHand())) {
                event.setCancelled(true);
                plugin.getStandPresetManager().removePreview(player);
                placeFromItem(player, null);
            }
            return;
        }

        // Różdżka teleportacji nie stroi stojaków - ignoruj kliknięcie.
        if (plugin.getTeleportWandManager().isWand(player.getInventory().getItemInMainHand())) {
            event.setCancelled(true);
            return;
        }

        // Kliknięcie podglądu-ducha: przedmiotem-zapisem postaw (zamiast stroić), inaczej nic.
        if (plugin.getStandPresetManager().isPreview(stand)) {
            event.setCancelled(true);
            if (plugin.getStandPresetManager().isSavedItem(player.getInventory().getItemInMainHand())) {
                plugin.getStandPresetManager().removePreview(player);
                placeFromItem(player, null);
            }
            return;
        }

        // Przedmiot-zapisany (prawy klik na stojaku) stawia nowy stojak zamiast stroić.
        if (plugin.getStandPresetManager().isSavedItem(player.getInventory().getItemInMainHand())) {
            event.setCancelled(true);
            plugin.getStandPresetManager().removePreview(player);
            placeFromItem(player, null);
            return;
        }

        // Stojak po restarcie serwera nie jest w pamięci "edytowalny" - odzyskaj go
        // automatycznie (tag pluginu albo doczepione części), żeby edycja działała
        // dalej jak przed restartem. Zwykłe (vanilla) stojaki nie są ruszane.
        if (!tuner.isEditable(stand)) {
            if (!tuner.recover(stand)) return;
            AntiSpam.sendActionBar(player, "§7Odzyskano stojak po restarcie - edytujesz go dalej.");
        }

        event.setCancelled(true);
        tuner.setActive(player, stand);

        Material item = player.getInventory().getItemInMainHand().getType();
        boolean sneak = player.isSneaking();
        double step = sneak ? 5 : 15;

        switch (item) {
            case STICK -> tuner.cyclePart(stand, player, sneak);
            case SUGAR -> tuner.cycleAxis(stand, player, sneak);
            case BLAZE_ROD -> tuner.rotate(stand, player, step, sneak);
            case BONE -> tuner.rotate(stand, player, -step, sneak);
            case FEATHER -> // zmniejsz CAŁĄ wieżyczkę (wszystkie części): ×0.9, ze shiftem 1% (×0.99)
                    tuner.scaleAllParts(stand, player, sneak ? 0.99 : 0.9);
            case GUNPOWDER -> tuner.toggleVisible(stand, player);
            case BLAZE_POWDER -> tuner.toggleArms(stand, player);
            case REDSTONE -> {
                if (sneak) {
                    tuner.toggleGlowParts(stand, player); // SHIFT: podświetl tylko części
                } else {
                    tuner.toggleGlow(stand, player);
                }
            }
            case PAPER -> tuner.printValues(stand, player);
            case BARRIER -> {
                if (sneak) {
                    tuner.remove(stand, player); // SHIFT+BARRIER = usuń cały stojak od razu
                } else if (!tuner.removeSelectedPart(stand, player)) {
                    tuner.remove(stand, player);
                }
            }
            // Nieznany przedmiot: nic nie rób (bez spamowania parametrów w action barze).
        }
    }

    /** Nazwy wieżyczek po nazwie presetu (z PDC) - nagłówek statystyk na czacie. */
    private static final Map<String, String> TURRET_NAMES = Map.of(
            "wiezyczka", "§6Zwykła Wieżyczka Strzelnicza",
            "wiezyczka_maga", "§6Wieżyczka Magii",
            "wiezyczka_maga_lod", "§bWieża Lodu",
            "wiezyczka_maga_ogien", "§cWieża Ognia",
            "wiezyczka_maga_trutka", "§aWieża Trucizny",
            "wiezyczka_maga_elektryk", "§eWieża Elektryczna");

    /**
     * Pokazuje na czacie AKTUALNE statystyki wieżyczki. Wartości czyta z PDC
     * stojaka (żywe dane: aktualne HP, obrażenia, zasięg, interwał strzału,
     * efekty), a nie z pliku presetu - więc gdy wieżyczka dostanie ulepszenia
     * zmieniające statystyki, ten podgląd automatycznie pokaże nowe liczby.
     */
    private void sendTurretStats(Player player, ArmorStand stand) {
        ArmorStandTuner tuner = plugin.getArmorStandTuner();
        String preset = tuner.turretPresetName(stand);
        String name = preset != null
                ? TURRET_NAMES.getOrDefault(preset.toLowerCase(Locale.ROOT), "§6Wieżyczka")
                : "§6Wieżyczka";
        player.sendMessage(net.kyori.adventure.text.Component.text(
                "§6=== " + name + " §8- statystyki §6==="));
        double max = tuner.turretMaxHp(stand);
        double cur = tuner.turretCurrentHp(stand);
        player.sendMessage(net.kyori.adventure.text.Component.text(
                "§7Życie: §f" + fmt(cur) + "§7/§f" + fmt(max) + " §c♥"));
        player.sendMessage(net.kyori.adventure.text.Component.text(
                "§7Obrażenia: §f" + fmt(tuner.turretDamage(stand))
                        + " §7na strzał (co §f" + fmtInterval(tuner.turretFireInterval(stand)) + " §7s)"));
        player.sendMessage(net.kyori.adventure.text.Component.text(
                "§7Zasięg: §f" + fmt(tuner.turretRange(stand)) + " §7bloków"));
        List<String> effects = new ArrayList<>();
        if (tuner.turretFreeze(stand)) effects.add("§bZamraża moby");
        if (tuner.turretPoison(stand)) effects.add("§aTrucizna: 1 HP/s przez 8 s");
        if (tuner.turretFire(stand)) effects.add("§cPodpala moby na 8 s");
        if (tuner.turretChain(stand)) effects.add("§eŁańcuch elektryczny (100/65/35%)");
        if (!effects.isEmpty()) {
            player.sendMessage(net.kyori.adventure.text.Component.text(
                    "§7Efekty: " + String.join("§7, ", effects)));
        }
    }

    /** 250.0 -> "250", 3.2 -> "3,2" (polski przecinek dziesiętny). */
    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v)
                : String.format(Locale.ROOT, "%.1f", v).replace('.', ',');
    }

    /** 40 ticków -> "2", 36 -> "1,8" (cooldown strzału w sekundach). */
    private static String fmtInterval(int ticks) {
        return fmt(ticks / 20.0);
    }

    /** Broń dystansowa: prawy klik nią NIE otwiera GUI ulepszeń wieżyczki. */
    private static final Set<Material> RANGED_WEAPONS =
            Set.of(Material.BOW, Material.CROSSBOW, Material.TRIDENT);
    /** Narzędzia /stojak dajitemy: blokują otwieranie GUI ulepszeń (admin stroi nimi wieżyczkę). */
    private static final Set<Material> TUNER_TOOLS =
            Set.of(Material.STICK, Material.BONE, Material.BLAZE_ROD, Material.SUGAR,
                    Material.REDSTONE, Material.GUNPOWDER, Material.BARRIER);

    /** Lewy klik PIÓREM (FEATHER) na stojaku = POWIĘKSZ całą wieżyczkę (wszystkie
     *  części): ×1.1, ze shiftem drobniej (×1.01). Prawy klik (onInteract) = zmniejszanie. */
    @EventHandler
    public void onAttack(PrePlayerAttackEntityEvent event) {
        if (!(event.getAttacked() instanceof ArmorStand stand)) return;
        ArmorStandTuner tuner = plugin.getArmorStandTuner();
        Player player = event.getPlayer();
        if (!player.hasPermission("vd.admin")) return;
        if (player.getInventory().getItemInMainHand().getType() != Material.FEATHER) return;
        // Tylko stojaki pluginu (po restarcie odzyskaj automatycznie) - vanilla zostaw.
        if (!tuner.isEditable(stand)) {
            if (!tuner.recover(stand)) return;
            AntiSpam.sendActionBar(player, "§7Odzyskano stojak po restarcie - edytujesz go dalej.");
        }
        event.setCancelled(true);
        tuner.setActive(player, stand);
        tuner.scaleAllParts(stand, player, player.isSneaking() ? 1.01 : 1.1);
    }

    /**
     * Gdy gracz przełączy slot na narzędzie /stojak (STICK, BONE, BLAZE_ROD, SUGAR,
     * REDSTONE, GUNPOWDER, BARRIER) — zwiększ zasięg interakcji z encjami do 20 bloków,
     * żeby mógł klikać i przesuwać części stojaka z daleka. Po przełączeniu na inny
     * przedmiot — przywróć domyślny zasięg.
     */
    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItem(event.getNewSlot());
        AttributeInstance attr = player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
        if (attr != null) {
            if (item != null && TUNER_TOOLS.contains(item.getType())) {
                attr.setBaseValue(20.0);
            } else {
                attr.setBaseValue(attr.getDefaultValue());
            }
        }
    }

    /** Prawy klik przedmiotem-zapisanym na bloku/powietrzu stawia stojak. */
    @EventHandler
    public void onPlaceClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) return;
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!plugin.getStandPresetManager().isSavedItem(item)) return;
        // Zwykłe przedmioty-zapisy: tylko vd.admin. Przedmioty ze sklepu areny
        // (wieżyczka) może postawić KAŻDY gracz.
        if (!player.hasPermission("vd.admin") && !plugin.getStandPresetManager().isShopItem(item)) return;

        event.setCancelled(true);
        plugin.getStandPresetManager().removePreview(player);
        placeFromItem(player, event.getClickedBlock());
    }

    private void placeFromItem(Player player, org.bukkit.block.Block clicked) {
        if (plugin.getStandPresetManager().placeFromItem(player, clicked) != null) {
            AntiSpam.send(player, "§aPostawiono stojak z przedmiotu.");
        } else {
            AntiSpam.send(player, "§cNie udało się postawić stojaka z przedmiotu.");
        }
    }
}
