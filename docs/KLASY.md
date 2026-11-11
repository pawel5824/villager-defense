# Klasy: zasady w jednym pliku

## Po co to jest

Żeby odpowiedź na pytanie „co wolno wojownikowi, a czego nie” była w **jednym miejscu**,
a nie rozrzucona po kodzie sklepu i listenerów. Nowa klasa = nowa sekcja w pliku, bez
zmian w Javie.

## Stan: etap 2 (sklep)

- Plik: `plugins/ServerPlugin/class-rules.yml` (kopiowany z JAR-a przy pierwszym starcie).
- Loader: `pl.vd.kits.ClassRulesManager`, model: `pl.vd.kits.ClassRule`.
- **Sklep blokuje zakup** wg `shop-allow`/`shop-deny`: pozycja zostaje w GUI, ale ma
  czerwony opis „Nie dla Twojej klasy”. Nie pobiera też punktów.
- **Użycie przedmiotów nadal nie jest blokowane** — `use-*` to na razie definicje.
- Podgląd w grze: `/def klasy` (uprawnienie `vd.admin`), przeładowanie: `/def reload`.
- Kategorie **Jedzenie**, **Inne** i **Zaklęcia** są wyłączone z zasad klas — tam każda
  klasa kupuje bez ograniczeń, nawet gdyby wzorzec `shop-deny` pasował do ID.

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
    shop-except: []     # zawsze dozwolone (nadpisuje shop-deny)
    shop-deny: ["*_sword"]
```

Kolejność użycia: `use-except` → `use-allow` → `use-deny`. Dla sklepu: `shop-allow` →
`shop-deny`. W `shop-*` działa `*` (dowolny fragment ID), bez znaczenia wielkości liter.

Błędny wpis (nieznana klasa, grupa, materiał) = jedno ostrzeżenie w logu i pominięcie
wpisu, nigdy crash.

## Aktualne przypisania (2026-11-11)

| Rzecz | Kto może kupić |
|---|---|
| Tarcza | wojownik, tank, inżynier |
| Łuk, kusza, karabin | łucznik |
| Proca | łucznik, wojownik, asasyn, inżynier |
| Miecze i topory | wszyscy poza łucznikiem, magikiem i medykiem |
| Strzały poza zwykłą i widmową | łucznik; zwykła i widmowa dla wszystkich |
| Trójząb | bez ograniczeń (świadomie pominięty) |

## Jak dodać klasę

1. Dopisz klasę w `KitManager.registerDefaults()` (ID, przedmioty, zbroja).
2. Dopisz sekcję z tym samym ID w `class-rules.yml`.
3. `/def reload` i `/def klasy` — sprawdź, czy widać nową klasę.

## Podłączenie blokad

| Gdzie | Metoda | Stan |
|---|---|---|
| `ShopGUI.purchase`, zaklęcia, ikony kategorii | `allowsPurchase(player, itemId)` | **zrobione** |
| `AbilityListener` / atak / picie / PPM | `allowsUse(player, material)` | do zrobienia |

Blokada użycia wymaga oznaczenia przedmiotów klasowych (np. znacznik w
`PersistentDataContainer`), bo sam materiał nie rozróżnia np. sztyletu łucznika od
zwykłego drewnianego miecza — dlatego w pliku istnieje `use-except`.
