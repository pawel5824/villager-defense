# Villager Defense — plugin serwera (Paper 26.3)

Jeden plugin (`name: ServerPlugin`, wersja 1.0.0, `pl.vd.VillagerDefensePlugin`) obsługuje cały
serwer: minigrę **Villager Defense**, tryb przetrwania **Raft** oraz narzędzia administracyjne
(stojaki, pędzel, kopiowanie, regiony). Ten plik to mapa projektu — szczegóły siedzą w kodzie,
`docs/` i `AGENTS.md`.

| Moduł | Co robi | Wejście |
|---|---|---|
| Villager Defense | fale mobów, obrona wieśniaków, kity, sklep, ranking | `/def`, tabliczki `[vd]` |
| Raft | przetrwanie na oceanie: tratwa, wyspy, fabuła, urządzenia | `/raft`, NPC w lobby |
| Stojak | dekoracje i wieżyczki z armor standów | `/stojak` |
| Pędzel | szybkie formowanie terenu aren | `/brush` |

## 1. Wymagania i build

| Element | Wartość |
|---|---|
| Java | **JDK 25** (`pom.xml`: `release 25`) |
| Build | `mvn clean package` (albo `build.bat`) |
| Wynik | **`target/ServerPlugin.jar`** (`finalName` w `pom.xml`) |
| Serwer | Paper **26.3**, build 25 alpha — przypięty w `pom.xml` i `start.bat` |
| Klient | tylko **26.3** (ViaVersion/ViaBackwards wyłączone, patrz `AGENTS.md` §7) |

Instalacja: wrzuć `ServerPlugin.jar` do `plugins/`, odpal serwer. Dane pluginu lądują w
**`plugins/ServerPlugin/`** (`plugin.yml` → `name`), m.in. `config.yml`, `arenas.yml`,
`players.yml`, `signs.yml`, `stojaki/`, `stojak-info.txt`.

## 2. Publikacja

```bash
tools/publish-all.sh            # mod + plugin + paczka zasobów, bez pytań
tools/publish-all.sh --dry-run  # plan i kontrole, nic nie wysyła
```

Etapy wyłączasz osobno: `--skip-mod`, `--skip-plugin`, `--skip-pack`; `--no-push` przygotuje
modpack bez commita. Sama paczka: `tools/publish-pack.sh` (upload, weryfikacja sha1, wpis do
`server.properties`). Nowa paczka działa graczom po **restarcie serwera**.

## 3. Mapa repozytorium

| Ścieżka | Zawartość |
|---|---|
| `src/main/java/pl/vd/arena`, `game`, `kits`, `shop`, `ability` | minigra: areny, przebieg fal, klasy, sklep, umiejętności |
| `src/main/java/pl/vd/instance` | klonowanie map (osobne gry na jednym szablonie) |
| `src/main/java/pl/vd/stand`, `brush`, `copy`, `vslab`, `region`, `tpwand` | narzędzia budowania i administracji |
| `src/main/java/pl/vd/lobby`, `achievements`, `friend`, `reports`, `restart` | lobby (NPC + wybór trybu), osiągnięcia, znajomi/drużyny, raporty, restart |
| `src/main/java/pl/vd/raft/**` | cały tryb Raft (opis: `AGENTS.md` §1–§12 i `docs/`) |
| `src/main/resources/config.yml` | konfiguracja VD; `raft/config.yml` — konfiguracja Rafta |
| `src/main/resources/plugin.yml` | lista komend i uprawnień |
| `docs/` | szczegóły mechanik (anteny, żagiel, muzyka, balans tratwy, animacje…) |
| `pack/`, `modpack/` | paczka zasobów i mod Fabric (`raft-hud`) |
| `tools/` | publikacja, generatory modeli/tekstur, kontrole paczki |

## 4. Komendy

### `/def` — minigra

| Grupa | Komendy |
|---|---|
| Budowa areny | `create <arena>`, `setlobby`, `setend`, `setminmax <min> <max>`, `addplayerspawn`, `addvillagerspawn`, `addzombiespawn` (+ `remove…`), `delete <arena>` |
| Poziom | `trudnosc <arena> <latwy\|normalny\|trudny>` (250/500/750 fal) |
| Gra | `join <arena>`, `leave`, `kit`, `kitpreview`, `top`, `stats` |
| Instancje | `instancja <arena> on\|off` |
| Zrzut | `miejsce_zrzutu [arena] …`, `supplydrop <arena>` (alias `zrzutdrop`), `animacja_zrzutu` |
| Techniczne | `reload`, `restart`, `ustawlobby`, `tpworld`, `tparena`, `create_world`, `delete_world`, `bariera`, `ogien_staly`, `shop_arena`, `spawninfo`, `list` |

`/def reload` przeładowuje `config.yml`, sklep, areny i konfigurację tratwy — działa bez restartu.
Arena jest gotowa do gry (`Arena.isReadyToUse`) przy lobby **albo** punkcie końcowym, minimum
1 spawnie gracza i 1 spawnie wieśniaka.

### Pozostałe komendy

| Komenda | Do czego |
|---|---|
| `/arena addpoints <liczba> [gracz]` | punkty sesji do testów sklepu (OP) |
| `/brush give\|sphere\|random\|overlay\|smooth\|radius\|info\|undo` | pędzel budowlany (`vd.admin`) |
| `/stojak …` | strojenie i stawianie stojaków; pełna lista w `plugins/ServerPlugin/stojak-info.txt` |
| `/hub`, `/lobby` | powrót do lobby serwera |
| `/codzienna` | dzienna nagroda |
| `/osiagniecia` | osiągnięcia gracza |
| `/znajomi`, `/druzyna` | znajomi i drużyny |
| `/ustawienia` | panel ustawień |
| `/report`, `/raporty` | zgłoszenia i ich przegląd |
| `/copy`, `/vslab` | kopiuj/wklej budowle, pionowe płytki |
| `/tpwand`, `/region` | różdżka teleportacji, regiony |
| `/raft`, `/wytwarzanie`, `/urzadzenie`, `/latarenka`, `/muzyka` | tryb Raft (opis w `AGENTS.md` i `docs/`) |

## 5. Arena w praktyce

**Instancje:** `/def instancja <arena> on` robi z areny szablon — każda grupa dostaje własną kopię
świata (`<arena>_inst1`, `…2`), kasowaną po grze. Limit: `instances.max-per-template` (5).
Klonowanie jest synchroniczne, więc duża mapa może na chwilę zamrozić serwer.

**Tabliczki** (pierwszy wiersz decyduje, reszta renderowana przez plugin):

| Wiersz 1 | Działanie |
|---|---|
| `[vd]` | dołączanie do jednej areny (nazwa w wierszu 2) |
| `[vd:map]` | rotacja z ręcznej puli (wiersze 2–4) |
| `[vd:map:*]` | rotacja z puli automatycznej — gotowe szablony, zawężana przez `rotation.auto-maps` |

**Zrzut zaopatrzenia:** co `supply-drop.interval-seconds` (180 s) z szansą
`chance-percent` (**25%**), skrzynia znika po `lifetime-seconds` (90 s). Teren lądowania
(wielokąt + „dziury”) jest **osobny dla każdej areny** (`arenas.yml`), a wartości w `config.yml`
to tylko fallback. Woda i lawa są zawsze pomijane.

## 6. Gra: kity, punkty, sklep

- **7 kitów** (`KitManager.registerDefaults`): Wojownik, Tank, Łucznik, Inżynier, Asasyn, Magik, Medyk.
  W GUI: **LPM = wybór kitu**, **PPM = podgląd przedmiotów**.
- **Umiejętności** (`pl.vd.ability`): Magik ma różdżkę strzelającą laserem (6 bloków, 2,5 obrażeń,
  1 s odnowienia). Nowa klasa = `AbilityType` + `AbilityManager` + wpis w `KitManager`.
- **Punkty** tylko z bieżącej rundy (`points-per-kill`, `turret-kill-points-percent`, bonus z `luck`).
  Sklep otwiera się PPM kompasem na arenie.
- **Lobby:** NPC otwiera GUI wyboru trybu (Villager Defense / Raft / „Wkrótce”) z dzienną skrzynią.

## 7. Konfiguracja (`plugins/ServerPlugin/config.yml`)

| Sekcja | Co ustawia |
|---|---|
| `game`, `difficulties`, `waves` | graczy min/max, odliczanie, przerwy, fale do zwycięstwa, segmenty `mob-count-segments`, tryb spawnu |
| `points-per-kill`, `turret-kill-points-percent`, `luck` | punkty i bonusy szczęścia |
| `shop.categories` | cały sklep: `food`, `swords`, `bows` (+ `custom-arrows`), `armor`, `potions`, `other`, `effects`, `enchants` |
| `supply-drop` | zrzut: interwał, szansa, czas życia, balon, prostokąt/wielokąt |
| `crits`, `thorns`, `bow-damage`, `health-display-style` | walka i wyświetlanie HP |
| `stojak`, `zegar` | wieżyczki, zegar ze stojaka |
| `lobby` | punkt powrotu po `/hub` i restarcie |
| `leaderboard`, `rotation`, `instances` | TOP, pula tabliczek, limit instancji |
| `raft-*` | tratwa, wyspy, muzyka, debug (szczegóły: `AGENTS.md` §7–§12) |

Sprzęt i zaklęcia mobów nie są w YAML — tabela `EQUIPMENT_TIERS` i stałe `SHARPNESS_*` /
`PROTECTION_*` / `FIRE_ASPECT_*` w `MobSpawner.java`. Po zmianach: `/def reload` lub restart.

## 8. Stojak (skrót)

`/stojak` stroi części na żywo przedmiotami w ręce (STICK = wybór części, SUGAR = tryb: skala,
obrót, skręt, przesunięcie, odbicie, lustro, grupa; BLAZE_ROD/BONE = plus/minus; GUNPOWDER =
widoczność; REDSTONE = podświetlenie; BARRIER = usuwanie). Zapis: `stojaki/<nazwa>.yml`,
stawianie: `place` / `item` / `saveitem`.

Gotowce: wieżyczka (250 HP, sama namierza i strzela co 2 s, bije ją tylko mob, w którego
strzeliła; do kupienia w sklepie), zegar (`zegar_wskazowki`, 3 wskazówki), krzesło, TV, lampa.
Pełna lista komend i zachowań: `plugins/ServerPlugin/stojak-info.txt`; kod: `pl.vd.stand.*`
i `pl.vd.listeners.TurretListener`.

## 9. Zanim coś zmienisz — pułapki

- **JAR nazywa się `ServerPlugin.jar`.** Stare skrypty (`build.bat`, `build-and-deploy.ps1`)
  wciąż mówią o `VillagerDefense.jar`; prawdziwa ścieżka to `target/ServerPlugin.jar`.
- **Folder danych to `plugins/ServerPlugin/`**, nie `plugins/VillagerDefense/` (ten drugi to
  pozostałość i nie jest już używany przez `getDataFolder()`).
- **Zmiana JAR-a w `plugins/` nic nie robi w działającym procesie** — potrzebny restart serwera.
- **`AGENTS.md` obowiązuje każdego agenta:** zasady zapisu (prosty język), brak komunikatów
  dla gracza w trybie Raft (§1), jeden model statystyk (§2), tempo w jednostkach czasu (§3),
  trwałość po restarcie (§12), numeracja `CustomModelData` (§5).
- Szczegóły mechanik: `docs/` (anteny, żagiel, muzyka, balans tratwy, schody, ustawienia);
  tryb Raft: `AGENTS.md` §1–§12.
