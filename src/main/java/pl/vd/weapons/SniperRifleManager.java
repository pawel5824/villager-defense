package pl.vd.weapons;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import pl.vd.VillagerDefensePlugin;
import pl.vd.arena.Arena;
import pl.vd.arena.ArenaState;
import pl.vd.type.Faction;
import pl.vd.util.AntiSpam;
import pl.vd.util.Lang;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Karabin snajperski - customowa broń kupowana w sklepie (kategoria Bronie,
 * strona 2, obok łuku/kuszy/procy).
 *
 * Przedmiot to ZAWSZE lornetka (SPYGLASS) - nigdy nie zmienia się na inny item.
 * Trzymanie PPM daje natywne przybliżenie jak lupa. LPM = strzał (hitscan,
 * 9 obrażeń, zasięg „nieograniczony"), ale NA PRZYBLIŻENIU nie strzelisz -
 * najpierw zdejmij palec z PPM. Po strzale gracz dostaje odrzut, a kolejny strzał
 * jest zablokowany przez 3 s (w tym czasie można nadal przybliżać).
 *
 * Anulowanie zoomu bez zmiany itemu: podmieniamy tylko NIEWIDOCZNY znacznik
 * (wartość taga 1 -> 2) w danych przedmiotu i wysyłamy aktualizację ekwipunku.
 * Klient widzi „zmieniony" przedmiot w ręce i natychmiast przerywa używanie
 * lornetki (zoom znika), a dla gracza item wygląda dokładnie tak samo.
 */
public class SniperRifleManager implements Listener {

    /** Wartość taga: 1 = aktywny (zwykły), 2 = po strzale (zoom anulowany). */
    private static final byte STATE_ACTIVE = 1;
    private static final byte STATE_BLOCKED = 2;

    private final VillagerDefensePlugin plugin;
    private final NamespacedKey rifleKey;

    /** Do kiedy gracz ma zablokowany strzał po ostatnim strzale (ms). */
    private final Map<UUID, Long> zoomBlockedUntil = new HashMap<>();

    /** custom_model_data karabinu - tekstura z resource packa (items/spyglass.json). */
    public static final int CUSTOM_MODEL_DATA = 1;

    public static final double DAMAGE = 9.0;
    /** „Nieograniczony" - dalej niż zasięg renderowania klienta. */
    public static final double RANGE = 1000.0;
    /** Kolejny strzał możliwy dopiero po 3 s (60 ticków). */
    public static final long BLOCK_MILLIS = 3000L;
    /** Siła odrzutu (pozioma, przeciwnie do kierunku patrzenia). */
    private static final double RECOIL = 0.8;
    /** Lekkie uniesienie przy odrzucie (żeby gracz nie utknął w terenie). */
    private static final double RECOIL_UP = 0.12;

    public SniperRifleManager(VillagerDefensePlugin plugin) {
        this.plugin = plugin;
        this.rifleKey = new NamespacedKey(plugin, "sniper_rifle");
    }

    /** Tworzy karabin - zawsze lornetka (SPYGLASS), nigdy nie zmienia się na inny item. */
    public ItemStack createRifle(Player player) {
        ItemStack item = new ItemStack(Material.SPYGLASS);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Lang.get(player, "weapon-sniper-name"));
            meta.setLore(List.of(
                    Lang.get(player, "weapon-sniper-lore-zoom"),
                    Lang.get(player, "weapon-sniper-lore-shoot"),
                    Lang.get(player, "weapon-sniper-lore-damage"),
                    Lang.get(player, "weapon-sniper-lore-range"),
                    Lang.get(player, "weapon-sniper-lore-cooldown")));
            meta.getPersistentDataContainer().set(rifleKey, PersistentDataType.BYTE, STATE_ACTIVE);
            meta.setCustomModelData(CUSTOM_MODEL_DATA); // tekstura z resource packa
            meta.setUnbreakable(true);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Czy przedmiot to karabin snajperski (zawsze SPYGLASS, dowolny stan)? */
    public boolean isRifle(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(rifleKey, PersistentDataType.BYTE);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        ItemStack item = event.getItem();
        if (!isRifle(item)) return;

        Player player = event.getPlayer();
        // PPM: nic nie anulujemy - lornetka sama robi przybliżenie (klient),
        // a item zawsze wygląda tak samo.
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true); // karabin nie niszczy bloków
            if (System.currentTimeMillis() < zoomBlockedUntil.getOrDefault(player.getUniqueId(), 0L)) {
                AntiSpam.send(player, Lang.get(player, "weapon-sniper-reloading"));
                return;
            }
            // na przybliżeniu nie strzelisz - zdejmij palec z PPM (gracz „używa" lornetki)
            if (player.isHandRaised()) {
                AntiSpam.send(player, Lang.get(player, "weapon-sniper-no-shoot-zoomed"));
                return;
            }
            // strzał tylko w trakcie gry (jak umiejętności klas)
            Arena arena = findArenaOf(player);
            if (arena == null || arena.getState() != ArenaState.INGAME) {
                AntiSpam.send(player, Lang.get(player, "weapon-sniper-only-ingame"));
                return;
            }
            shoot(player);
        }
    }

    /** Strzał: hitscan po linii wzroku (bloki zatrzymują pocisk), 9 obrażeń,
     *  huk + odrzut + 3 s blokady strzału. */
    private void shoot(Player player) {
        World world = player.getWorld();
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();

        // pierwszy wróg z fali w linii strzału; blok przed wrogiem = pudło
        RayTraceResult entityHit = world.rayTraceEntities(eye, dir, RANGE, 0.4,
                e -> Faction.of(e).isWave());
        RayTraceResult blockHit = world.rayTraceBlocks(eye, dir, RANGE);
        if (entityHit != null && entityHit.getHitEntity() instanceof LivingEntity target) {
            boolean clear = blockHit == null
                    || blockHit.getHitPosition().distanceSquared(eye.toVector())
                    >= entityHit.getHitPosition().distanceSquared(eye.toVector());
            if (clear) {
                target.damage(DAMAGE, player);
            }
        }

        // huk karabinu: głuchy huk + ostry trzask (głośność per gracz z Ustawień)
        plugin.getGameManager().playSoundNearby(eye, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.8f,
                pl.vd.shop.ShopGUI.SOUND_ABILITIES_KEY);
        plugin.getGameManager().playSoundNearby(eye, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.5f, 1.1f,
                pl.vd.shop.ShopGUI.SOUND_ABILITIES_KEY);

        // odrzut: odpycha gracza w tył (przeciwnie do kierunku patrzenia)
        Vector recoil = new Vector(-dir.getX() * RECOIL, RECOIL_UP, -dir.getZ() * RECOIL);
        player.setVelocity(player.getVelocity().add(recoil));

        // blokada strzału na 3 s + (na wszelki wypadek) anulowanie ewentualnego
        // aktywnego przybliżenia: podmiana niewidocznego znacznika (ta sama lornetka)
        blockZoom(player);
    }

    /** Po strzale: trzymany karabin dostaje znacznik „po strzale" (wygląd bez
     *  zmian), co zmusza klienta do przerwania ewentualnego przybliżania; po
     *  3 s znacznik wraca i karabin jest znów w pełni aktywny. */
    private void blockZoom(Player player) {
        zoomBlockedUntil.put(player.getUniqueId(), System.currentTimeMillis() + BLOCK_MILLIS);
        ItemStack held = player.getInventory().getItemInMainHand();
        if (isRifle(held) && held.getType() == Material.SPYGLASS) {
            // kopia ze znacznikiem „po strzale" - podmiana wysyła klientowi
            // aktualizację ekwipunku i ten natychmiast przerywa zoom
            ItemStack copy = held.clone();
            setState(copy, STATE_BLOCKED);
            player.getInventory().setItemInMainHand(copy);
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            zoomBlockedUntil.remove(player.getUniqueId());
            // przywróć WSZYSTKIE karabiny w ekwipunku (gracz mógł przestawić sloty)
            for (int i = 0; i < player.getInventory().getSize(); i++) {
                ItemStack item = player.getInventory().getItem(i);
                if (isRifle(item) && item.getType() == Material.SPYGLASS
                        && getState(item) == STATE_BLOCKED) {
                    ItemStack copy = item.clone();
                    setState(copy, STATE_ACTIVE);
                    player.getInventory().setItem(i, copy);
                }
            }
            player.updateInventory();
        }, BLOCK_MILLIS / 50L);
    }

    /** Zmienia stan taga na kopii przedmiotu (bez dotykania wyglądu). */
    private void setState(ItemStack item, byte state) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(rifleKey, PersistentDataType.BYTE, state);
        item.setItemMeta(meta);
    }

    private byte getState(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        Byte b = item.getItemMeta().getPersistentDataContainer().get(rifleKey, PersistentDataType.BYTE);
        return b == null ? 0 : b;
    }

    private Arena findArenaOf(Player player) {
        for (Arena arena : plugin.getArenaManager().getArenas().values()) {
            if (arena.isPlaying(player)) return arena;
        }
        return null;
    }
}
