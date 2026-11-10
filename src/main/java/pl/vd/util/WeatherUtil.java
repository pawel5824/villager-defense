package pl.vd.util;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import org.bukkit.WeatherType;
import org.bukkit.entity.Player;

/**
 * Per-graczowa pora dnia i pogoda (ustawienia z panelu „Pogoda" w sklepie).
 * Wartości zapisywane są w players.yml pod kluczami {@link #TIME_KEY} i
 * {@link #WEATHER_KEY}; zastosowanie jest natychmiastowe po kliknięciu i trwałe
 * (po wejściu na serwer / zmianie świata ustawienie wraca do gracza).
 */
public final class WeatherUtil {

    /** Klucze ustawień w players.yml. */
    public static final String TIME_KEY = "time-of-day";
    public static final String WEATHER_KEY = "weather";

    /** Dostępne pory dnia (kolejność cyklu po kliknięciu). */
    public static final String[] TIME_OPTIONS = {"ranek", "poludnie", "wieczor", "noc", "normalna"};

    /** Dostępne pogody (kolejność cyklu po kliknięciu). */
    public static final String[] WEATHER_OPTIONS = {"slonecznie", "deszcz", "burza", "normalna"};

    private WeatherUtil() {
    }

    /** Następna opcja w cyklu po bieżącej (zawija do pierwszej). */
    public static String next(String[] options, String current) {
        for (int i = 0; i < options.length; i++) {
            if (options[i].equals(current)) {
                return options[(i + 1) % options.length];
            }
        }
        return options[0];
    }

    /** Poprzednia opcja w cyklu przed bieżącą (zawija do ostatniej). */
    public static String previous(String[] options, String current) {
        for (int i = 0; i < options.length; i++) {
            if (options[i].equals(current)) {
                return options[(i - 1 + options.length) % options.length];
            }
        }
        return options[options.length - 1];
    }

    /** Ładna nazwa opcji (z polskimi znakami) do lore w GUI. */
    public static String label(String option) {
        return switch (option) {
            case "ranek" -> "Ranek";
            case "poludnie" -> "Południe";
            case "wieczor" -> "Wieczór";
            case "noc" -> "Noc";
            case "slonecznie" -> "Słonecznie";
            case "deszcz" -> "Deszcz";
            case "burza" -> "Burza";
            default -> "Normalna";
        };
    }

    /** Ładna nazwa opcji w języku gracza (panel „Pogoda" w ustawieniach). */
    public static String label(Player player, String option) {
        return switch (option) {
            case "ranek" -> Lang.get(player, "set-weather-ranek");
            case "poludnie" -> Lang.get(player, "set-weather-poludnie");
            case "wieczor" -> Lang.get(player, "set-weather-wieczor");
            case "noc" -> Lang.get(player, "set-weather-noc");
            case "slonecznie" -> Lang.get(player, "set-weather-slonecznie");
            case "deszcz" -> Lang.get(player, "set-weather-deszcz");
            case "burza" -> Lang.get(player, "set-weather-burza");
            default -> Lang.get(player, "set-weather-normalna");
        };
    }

    /** Stosuje porę dnia: „normalna" = podąża za światem (reset per-graczowego czasu).
     *  Stałe wartości ticków: ranek 23700 (tuż przed wschodem), południe 6000,
     *  wieczór 12500 (zachód), noc 18000 (północ). */
    public static void applyTimeOfDay(Player player, String option) {
        switch (option == null ? "normalna" : option) {
            case "ranek" -> player.setPlayerTime(23700, false); // ranek - tuż przed wschodem słońca
            case "poludnie" -> player.setPlayerTime(6000, false); // południe
            case "wieczor" -> player.setPlayerTime(12500, false); // wieczór - zachód słońca
            case "noc" -> player.setPlayerTime(18000, false);     // noc - północ
            default -> player.resetPlayerTime();
        }
    }

    /** Stosuje pogodę: „normalna" = jak na serwerze (reset per-graczowej pogody). */
    public static void applyWeather(Player player, String option) {
        switch (option == null ? "normalna" : option) {
            case "slonecznie" -> player.setPlayerWeather(WeatherType.CLEAR);
            case "deszcz" -> player.setPlayerWeather(WeatherType.DOWNFALL);
            case "burza" -> applyThunderstorm(player);
            default -> player.resetPlayerWeather();
        }
    }

    /** Burza per gracz: deszcz + grzmot. Bukkit nie ma per-graczowego grzmotu,
     *  więc poziom burzy (GameStateChange, reason 8) wysyłamy pakietem (ProtocolLib);
     *  bez ProtocolLib gracz zobaczy sam deszcz - ustawienie nadal działa. */
    private static void applyThunderstorm(Player player) {
        player.setPlayerWeather(WeatherType.DOWNFALL);
        try {
            PacketContainer rain = new PacketContainer(PacketType.Play.Server.GAME_STATE_CHANGE);
            rain.getIntegers().write(0, 7); // 7 = poziom deszczu
            rain.getFloat().write(0, 1.0f);
            ProtocolLibrary.getProtocolManager().sendServerPacket(player, rain);

            PacketContainer thunder = new PacketContainer(PacketType.Play.Server.GAME_STATE_CHANGE);
            thunder.getIntegers().write(0, 8); // 8 = poziom grzmotu
            thunder.getFloat().write(0, 1.0f);
            ProtocolLibrary.getProtocolManager().sendServerPacket(player, thunder);
        } catch (Exception ignored) {
            // brak ProtocolLib / inna wersja - zostaje sam deszcz
        }
    }
}
