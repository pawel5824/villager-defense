# Klasy: zasady w jednym pliku

## Po co to jest

Żeby odpowiedź na pytanie „co wolno wojownikowi, a czego nie” była w **jednym miejscu**,
a nie rozrzucona po kodzie sklepu i listenerów. Nowa klasa = nowa sekcja w pliku, bez
zmian w Javie.

## Stan: etap 2 (sklep)

- Plik: `plugins/ServerPlugin/class-rules.yml` (kopiowany z JAR-a przy pierwszym starcie).
- Loader: `pl.vd.kits.ClassRulesManager`, model: `pl.vd.kits.ClassRule`.
- **Sklep blokuje zakup** wg `shop-allow`/`shop-deny` oraz limitu zbroi `armor-max`:
  pozycja zostaje w GUI, ale przy klasie bez dostępu ma czerwony znacznik ✖. Nie pobiera też punktów.
- **Nie kupisz części słabszej** od tej, którą masz już na tym samym slocie. Ten sam tier
  można kupić; puste miejsce i skóra nie blokują zakupu.
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
    armor-max: iron     # najwyższy tier zbroi, jaki klasa może kupić
    use-except: []      # zawsze dozwolone (np. własna broń klasy)
    use-allow: []       # puste = wszystko; niepuste = tylko te materiały
    use-deny: ["@swords"]
    shop-allow: []      # puste = wszystkie wpisy
    shop-except: []     # zawsze dozwolone (nadpisuje shop-deny)
    shop-deny: ["*_sword"]
```

Kolejność użycia: `use-except` → `use-allow` → `use-deny`. Dla sklepu: `shop-allow` →
`shop-deny`. W `shop-*` działa `*` (dowolny fragment ID), bez znaczenia wielkości liter.

`armor-max` to osobny górny próg. Kolejność tierów:

```text
miedź < złoto < chainmail < żelazo < diament < netheryt
```

Klasa może kupić swoją maksymalną zbroję i wszystkie słabsze. Brak `armor-max` w starym
pliku = brak limitu (zgodność wstecz). Skóra nie ma tieru i nigdy nie blokuje zakupu.

Błędny wpis (nieznana klasa, grupa, materiał) = jedno ostrzeżenie w logu i pominięcie
wpisu, nigdy crash.

## Aktualne przypisania (2026-11-11)

| Rzecz | Kto może kupić |
|---|---|
| Tarcza | wojownik, tank, inżynier |
| Łuk, kusza, karabin | łucznik |
| Proca | łucznik, wojownik, asasyn, inżynier |
| Broń dystansowa (tank, magik, medyk) | brak dostępu |
| Miecze i topory | wszyscy poza łucznikiem, magikiem i medykiem |
| Strzały poza zwykłą i widmową | łucznik; zwykła i widmowa dla wszystkich |
| Trójząb | bez ograniczeń (świadomie pominięty) |

## Limity zbroi (2026-11-11)

| Klasa | Najmocniejsza zbroja | Może kupić |
|---|---|---|
| Wojownik | netheryt | miedź, złoto, chainmail, żelazo, diament, netheryt |
| Tank | netheryt | miedź, złoto, chainmail, żelazo, diament, netheryt |
| Magik | żelazo | miedź, złoto, chainmail, żelazo |
| Łucznik | chainmail | miedź, złoto, chainmail |
| Medyk | złoto | miedź, złoto (może mieszać części) |
| Asasyn | żelazo | miedź, złoto, chainmail, żelazo |
| Inżynier | złoto | miedź, złoto |

Kontrola jest przy zakupie, nie przy zakładaniu: posiadana mocniejsza zbroja nie jest
zabierana, ale nie da się jej wymienić na słabszą część z tego samego slotu.

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
