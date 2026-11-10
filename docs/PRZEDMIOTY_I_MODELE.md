# Przedmioty i modele — zasady, mapowanie CMD, zaparkowane modele

Przeniesione z `AGENTS.md` §5 (stan 2026-10-01). Numeracja i zasady są w `AGENTS.md` §5;
tutaj są szczegóły, historia i listy.

## Item models tylko w `assets/<ns>/items/*.json`

Stare `overrides` w `models/item/*.json` (mechanizm sprzed 1.21.4) są od 1.21.4 **po cichu
ignorowane**. Sprawdzone w bytecode: parser modelu w 26.1 i 26.3 (`CuboidModel$Deserializer`)
zna wyłącznie `parent`, `textures`, `elements`, `display`, `gui_light`, `ambientocclusion` —
`overrides` nie ma tam ani na liście kluczy, ani jako literał w klasie. Nie ma więc błędu w logu,
tylko przedmiot wyglądający waniliowo.

Przedmiot z własnym wyglądem = wpis w `items/<przedmiot>.json` (`range_dispatch` po
`custom_model_data`, `fallback` = waniliowa definicja przedmiotu), a nie `overrides`.

## Mapowanie bierze się z pluginu, nigdy z paczki

Jedyne źródło prawdy to `ItemManager.java` — `setCustomModelData` + `Material` + nazwa
przedmiotu. Stare pliki `overrides` przetrwały po liście przedmiotów, której już nie ma, więc
mapowanie z nich tworzy definicje dla CMD, których plugin **nigdy nie ustawia** (modele wiszą na
waniliowych przedmiotach, których nikt nie ma w ekwipunku).

**Zrobione 2026-09-21:** 18 takich definicji (ziemia, moneta, wiadro, rudy, sztabki, diament,
wosk, gwóźdź na `exposed_lightning_rod`…) **usunięte**; modele zostały w paczce jako
„zaparkowane” i generator wypisuje je na końcu swojego wyjścia.

W całym kodzie pluginu istnieją tylko trzy żywe pary z tego zakresu:

| Waniliowy przedmiot | CMD | Model | Przedmiot w pluginie |
|---|---|---|---|
| `lead` | 1002 | `raft:item/lina_custom` | „Lina” (`createRope`) |
| `prismarine_shard` | 1007 | `raft:item/odpadki_custom` | „Odpadki” (`createOdpadki`) |
| `iron_nugget` | 7012 | `raft:item/gwozdz_custom` | „Gwoździe” (`createNail`) |

## Model bez tekstury nie jest podłączany

Te trzy modele wskazują tekstury (`raft:item/lina_custom.png` itd.), których **nie ma nigdzie na
dysku** (nie było ich nawet w paczce z 31 sierpnia) — podłączenie dałoby szachownicę
„missing texture” na Linie, Odpadkach i Gwoździach, czyli wygląd **gorszy** niż wanilia.

Dlatego na dziś nie ma żadnej generowanej definicji: zostają tylko pisane ręcznie `heavy_core`
(radio/odbiornik/beczka), `lantern` (latarenki 9001–9010) i `spyglass`. Generator sam podłączy
model, gdy pojawi się PNG — warunek siedzi w narzędziu, nie w głowie.

**Kolejność pracy:** model **i tekstura** → wiersz w `LIVE` (`tools/ItemDefinitions.java`) →
`tools/gen-item-definitions.sh`.

## Fallback bierz z waniliowej definicji, nigdy z nazwy przedmiotu

Generator czyta `assets/minecraft/items/<item>.json` z jara klienta i wstawia jego `model` jako
`fallback`. Ręcznie wpisane `minecraft:item/<nazwa>` działa tylko dla przedmiotów niebędących
blokami: wanilia dla bloków wskazuje `minecraft:block/<nazwa>`, a np. `models/item/dirt.json`
**nie istnieje** — taki fallback daje brakujący model każdemu, kto trzyma zwykłą ziemię.

Ten błąd siedział też w `heavy_core.json` (`minecraft:item/heavy_core`), przez co **każdy zwykły
heavy core był bez modelu**, oraz w `spyglass.json` (płaski sprite zamiast `spyglass_in_hand`
w ręce). Oba naprawione: fallback to teraz kopia waniliowej definicji (dla spyglassa całego
`select` po `display_context`).

## Zaparkowane modele

Leżą w `assets/raft/models/item/`, w pluginie nie ma dla nich przedmiotu — gdy powstanie,
przenieś wiersz do `LIVE`, nic więcej nie trzeba:

`sunflower_moneta` (moneta; waluta w pluginie to licznik `coins`, nie przedmiot),
`ziemia_custom`, `szklo_custom`, `wiadro_custom` (źródło: `wiadro.bbmodel`),
`maz_algowa_custom`, `kostka_odpadow_custom`, `ruda_*`/`sztabka_*` (metalu, miedzi, tytanu),
`metal_custom`, `diamond_custom`, `wosk_pszczeli_custom`, `wiatrak` (wiatrak jest
**urządzeniem** 2×2×6, nie przedmiotem).

## Czego nadal nie ma

Przedmioty wyglądają waniliowo, bo w paczce nie ma dla nich modelu: siekiery 8006–8009, wędki
7004–7006 i pozostałe 7001–7037, 2001/2002, 3001–3003, 5001, 8010. (4004 antena ma już model
i wpis w item definition.)

Nowy taki przedmiot = najpierw model **i tekstura** w `assets/raft/`, potem wiersz
w `tools/ItemDefinitions.java`, potem generator.
