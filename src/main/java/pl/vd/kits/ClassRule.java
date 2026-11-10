package pl.vd.kits;

import org.bukkit.Material;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Definicja jednej klasy: czego może używać i co może kupić.
 *
 * Etap 1 systemu klas: ten obiekt tylko przechowuje reguły i odpowiada na pytania.
 * Nie jest jeszcze nigdzie podłączony do sklepu ani do użycia przedmiotów, więc
 * samo wczytanie pliku nie zmienia rozgrywki - to fundament pod blokady (etap 2).
 *
 * Kolejność dla użycia: use-except (zawsze wolno) → use-allow (whitelist) → use-deny.
 * Dla zakupów: shop-allow (whitelist ID) → shop-deny. Wzorzec "*" = dowolny fragment.
 */
public final class ClassRule {

    private final String kitId;
    private final Set<Material> useAllow;
    private final Set<Material> useExcept;
    private final Set<Material> useDeny;
    private final List<String> shopAllow;
    private final List<String> shopDeny;

    public ClassRule(String kitId,
                     Set<Material> useAllow,
                     Set<Material> useExcept,
                     Set<Material> useDeny,
                     List<String> shopAllow,
                     List<String> shopDeny) {
        this.kitId = kitId;
        this.useAllow = new LinkedHashSet<>(useAllow);
        this.useExcept = new LinkedHashSet<>(useExcept);
        this.useDeny = new LinkedHashSet<>(useDeny);
        this.shopAllow = List.copyOf(shopAllow);
        this.shopDeny = List.copyOf(shopDeny);
    }

    /** Klasa bez żadnych reguł - wszystko dozwolone (domyślne dla nowych klas). */
    public static ClassRule allowAll(String kitId) {
        return new ClassRule(kitId, Set.of(), Set.of(), Set.of(), List.of(), List.of());
    }

    public String getKitId() { return kitId; }

    public Set<Material> getUseAllow() { return Set.copyOf(useAllow); }
    public Set<Material> getUseExcept() { return Set.copyOf(useExcept); }
    public Set<Material> getUseDeny() { return Set.copyOf(useDeny); }
    public List<String> getShopAllow() { return shopAllow; }
    public List<String> getShopDeny() { return shopDeny; }

    public boolean hasUseRules() {
        return !useAllow.isEmpty() || !useExcept.isEmpty() || !useDeny.isEmpty();
    }

    public boolean hasShopRules() {
        return !shopAllow.isEmpty() || !shopDeny.isEmpty();
    }

    /** Czy klasa może użyć przedmiotu o tym materiale. */
    public boolean allowsUse(Material material) {
        if (material == null) return true;
        if (useExcept.contains(material)) return true;
        if (!useAllow.isEmpty() && !useAllow.contains(material)) return false;
        return !useDeny.contains(material);
    }

    /** Czy klasa może kupić wpis sklepu o tym ID (klucz z config.yml). */
    public boolean allowsPurchase(String itemId) {
        if (itemId == null || itemId.isEmpty()) return true;
        if (!shopAllow.isEmpty() && shopAllow.stream().noneMatch(p -> matches(p, itemId))) {
            return false;
        }
        return shopDeny.stream().noneMatch(p -> matches(p, itemId));
    }

    /** Prosty wzorzec z "*" (dowolny fragment), bez znaczenia wielkości liter. */
    static boolean matches(String pattern, String value) {
        if (pattern == null || value == null) return false;
        StringBuilder regex = new StringBuilder();
        for (char c : pattern.toLowerCase(Locale.ROOT).toCharArray()) {
            if (c == '*') regex.append(".*");
            else regex.append(Pattern.quote(String.valueOf(c)));
        }
        return value.toLowerCase(Locale.ROOT).matches(regex.toString());
    }

    /** Jedna linia do podglądu w grze (/def klasy). */
    public String describe() {
        String use = (useAllow.isEmpty() ? "wszystko" : "tylko " + useAllow.size())
                + ", zakaz " + useDeny.size()
                + (useExcept.isEmpty() ? "" : ", wyjątki " + useExcept.size());
        String shop = (shopAllow.isEmpty() ? "wszystko" : "tylko " + shopAllow.size())
                + ", zakaz " + shopDeny.size();
        return "§e" + kitId + "§7 - użycie: " + use + " §8| §7sklep: " + shop;
    }
}
