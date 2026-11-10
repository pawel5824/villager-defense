# Klasy: zasady w jednym pliku

## Po co to jest

Żeby odpowiedź na pytanie „co wolno wojownikowi, a czego nie” była w **jednym miejscu**,
a nie rozrzucona po kodzie sklepu i listenerów. Nowa klasa = nowa sekcja w pliku, bez
zmian w Javie.

## Stan: etap 1 (same definicje)

- Plik: `plugins/ServerPlugin/class-rules.yml` (kopiowany z JAR-a przy pierwszym starcie).
- Loader: `pl.vd.kits.ClassRulesManager`, model: `pl.vd.kits.ClassRule`.
- **Nic jeszcze nie blokuje** zakupów ani użycia przedmiotów — gra działa jak dotąd.
- Podgląd w grze: `/def klasy` (uprawnienie `vd.admin`), przeładowanie: `/def reload`.

## Format

```yaml
version: 1
groups:                 # grupy materiałów, używane jako @nazwa
  swords: [IRON_SWORD, DIAMOND_SWORD]
classes:
  wojownik:
    use-except: []      # zawsze dozwolone (np. własna broń klasy)
    use-allow: []       # puste = wszystko; niepuste = tylko te materiały
    use-deny: ["@swords"]
    shop-allow: []      # puste = wszystkie wpisy
    shop-deny: ["*_sword"]
```

Kolejność użycia: `use-except` → `use-allow` → `use-deny`. Dla sklepu: `shop-allow` →
`shop-deny`. W `shop-*` działa `*` (dowolny fragment ID), bez znaczenia wielkości liter.

Błędny wpis (nieznana klasa, grupa, materiał) = jedno ostrzeżenie w logu i pominięcie
wpisu, nigdy crash.

## Jak dodać klasę

1. Dopisz klasę w `KitManager.registerDefaults()` (ID, przedmioty, zbroja).
2. Dopisz sekcję z tym samym ID w `class-rules.yml`.
3. `/def reload` i `/def klasy` — sprawdź, czy widać nową klasę.

## Etap 2: podłączenie blokad

Miejsca, które będą pytać o reguły (już przygotowane API):

| Gdzie | Metoda |
|---|---|
| `ShopGUI.purchase` i budowa GUI kategorii | `allowsPurchase(player, itemId)` |
| `AbilityListener` / atak / picie / PPM | `allowsUse(player, material)` |

Etap 2 wymaga też oznaczenia przedmiotów klasowych (np. znacznik w `PersistentDataContainer`),
bo sam materiał nie rozróżnia np. sztyletu łucznika od zwykłego drewnianego miecza —
dlatego w pliku istnieje `use-except`.
