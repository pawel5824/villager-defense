package pl.vd.type;

import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Villager;
import org.bukkit.entity.WanderingTrader;
import pl.vd.game.MobSpawner;
import pl.vd.stand.ArmorStandTuner;

/**
 * Przynależność bytu na arenie. Zamiast powtarzanych wszędzie sprawdzeń
 * (instanceof wieśniaka, metadane vd-arena, tag vd_sojusz) pytamy raz:
 * {@code Faction.of(encja)} - a cała logika celowania/obrażeń/dźwięków
 * rozgałęzia się po jednym enumie.
 *
 * Kolejność sprawdzeń w {@link #of} jest ważna: wieśniacy areny mają te same
 * metadane vd-arena co moby z fal, więc sprawdzamy ich PRZED metadanymi.
 */
public enum Faction {

    /** Gracze - nigdy cel. */
    PLAYER,
    /** Moby z fal areny (metadane vd-arena) - wrogowie. */
    WAVE,
    /** Przywołane stwory po stronie gracza (tag vd_sojusz): warden, bałwan,
     *  wilk, golem, obrońca. */
    FRIENDLY,
    /** Bronieni wieśniacy i wędrowni kupcy. */
    VILLAGER,
    /** Wszystko inne (zwierzęta, stojaki, obce moby spoza areny...). */
    OTHER;

    /** Przynależność bytu, albo {@link #OTHER} dla null / nieznanych bytów. */
    public static Faction of(Entity e) {
        if (e == null) return OTHER;
        if (e instanceof Player) return PLAYER;
        if (e instanceof Villager || e instanceof WanderingTrader) return VILLAGER;
        if (e.getScoreboardTags().contains(ArmorStandTuner.TAG_ALLIED)) return FRIENDLY;
        // stojaki (ArmorStand to LivingEntity w Bukkit) i inne byty bez metadanych
        if (e instanceof ArmorStand) return OTHER;
        if (e.hasMetadata(MobSpawner.METADATA_KEY)) return WAVE;
        // oswojone moby gracza (bez tagu pluginu) też nie są wrogami
        if (e instanceof Tameable t && t.isTamed()) return OTHER;
        return OTHER;
    }

    /** Czy to wróg z fali (jedyny byt, w który celują wieżyczki i przywołania). */
    public boolean isWave() {
        return this == WAVE;
    }
}
