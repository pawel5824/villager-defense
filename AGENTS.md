# Zasady projektu (obowiązują każdego agenta i każdą sesję)

Ten plik istnieje, bo ustalenia z rozmów giną razem z sesją. Trzymamy tu **zasady i twarde fakty**;
pomiary, historia wdrożeń i listy siedzą w `docs/` i są podlinkowane przy sekcjach.
**Przeczytaj go, zanim dodasz cokolwiek związanego z Raftem albo HUD-em.**

Gdzie szukać szczegółów: `docs/HISTORIA_RAFT.md` (wdrożenia), `docs/PRZEDMIOTY_I_MODELE.md`
(modele, CMD), `docs/WERSJE_GRY.md` (wersje, wtyczki, pomiary), `docs/PACZKA_ZASOBOW.md`
(paczka, publikacja), `docs/DATAPACK_RAFT_HEIGHT.md`, `docs/OTWARTE_TEMATY.md`,
`docs/TRWALOSC.md`, `docs/KOMUNIKATY.md`.

## 0. Jak pisze agent (styl każdej wiadomości)

Ta zasada jest tak samo ważna jak §1: **każda wiadomość do użytkownika ma być zrozumiała
dla trzynastolatka.** Dotyczy odpowiedzi w rozmowie, podsumowań, opisów commitów i PR oraz tych
fragmentów dokumentacji, które czyta człowiek.

- **Najpierw sens, potem szczegóły.** Pierwsze zdanie mówi, co się stało albo co to znaczy.
  Dopiero potem techniczne mięso.
- **Krótko i konkretnie.** Streszczaj, nie przepisuj. Jedna myśl = jedno zdanie.
  Bez wypełniaczy w rodzaju „warto zauważyć”, „jak wiadomo”, „w związku z powyższym”.
- **Proste słowa.** Termin techniczny (API, rejestr, hash, registry) wyjaśnij w kilku słowach
  przy pierwszym użyciu — a jeśli da się go zastąpić zwykłym słowem, zastąp.
- **Logika widoczna: przyczyna → skutek.** Pisz „X, dlatego Y”, a nie listę faktów do poskładania.
- **Porównania, stany i listy rzeczy — tabelą albo punktami.** Ściana tekstu nic nie wnosi.
- **Krótkie pytanie = krótka odpowiedź** (dwa–cztery zdania). Dłuższa odpowiedź tylko, gdy zadanie
  naprawdę tego wymaga.
- **Nie zgaduj i nie strasz.** Zamiast „może nie działać” — sprawdź i napisz, co wyszło (§6).
  Czego nie dało się sprawdzić, powiedz wprost i wyjaśnij, czego brakuje.
- **Pisz po polsku, normalnym zdaniem.** Nazwy własne i terminy zostają w oryginale, ale zdania
  mają być polskie i naturalne.

### 0.1. Pilnuj niedokończonych tematów (przypominaj)

Zdarza się, że zaczynamy jedną rzecz (funkcja, blok, przedmiot, model), rozmowa przechodzi na inny
temat, a tamta zostaje niedokończona. Agent ma tego pilnować i **przypominać**, nawet gdy nikt
o to nie pyta.

- **Trzymaj listę otwartych tematów** — co zaczęte, czego brakuje, co czeka na sprawdzenie w grze.
  Trwałe punkty zapisuj w §10 (rozmowa ginie, plik zostaje).
- **Przypominaj krótko i rzeczowo**, gdy temat ucichnie: jedno zdanie w stylu „wracając do X —
  zostało zrobić Y”. Bez moralizowania i bez wyliczania całej listy.
- **Nie zmieniaj tematu po cichu.** Jeśli zaczynasz coś nowego, a poprzednia rzecz jest
  niedokończona, powiedz to w jednym zdaniu.
- **Dopytuj o stan w grze**, gdy to jedyny sposób, żeby wiedzieć, czy działa („co widziałeś na
  tratwie?”, „czy paczka się pobrała?”).
- **Nie zrzędź.** Jedno przypomnienie na wiadomość i nie powtarzaj punktu, w którym nic się nie
  zmieniło.

## 1. Zero komunikatów dla gracza (twarda zasada)

**Nie dodawaj żadnych komunikatów: czat, action bar, title/subtitle, boss bar, scoreboard,
toasty, dźwięki-informacje.** Wszędzie ma być cicho. Informacja zwrotna dla gracza to
**wyłącznie paski HUD** (mod `raft-hud`) — one są od tego, żeby gracz widział stan, a nie czat.

Dotyczy to także ostrzeżeń typu „kończy Ci się woda”, „tracisz życie”, „jesteś spragniony”.
Jeśli efekt ma być odczuwalny, ma być odczuwalny, a nie opisany.

Lista komunikacji usuniętej 2026-09-18 (nie przywracaj jej „przy okazji”) jest w
`docs/KOMUNIKATY.md` — razem z zamierzonymi skutkami ubocznymi. Zostają tylko dwa powitania
(wejście na serwer i wejście w tryb Raft, świadoma decyzja właściciela) oraz odpowiedzi komend
administracyjnych (`/raft`, `/urzadzenie`, `/latarenka`, crafting).

**Ten sam rygor dotyczy logu serwera, nie tylko gracza.** Log startu ma być czysty — żadnych
ostrzeżeń o rzeczach, które są normalnym działaniem, bo prawdziwy problem ginie wśród nich.
Przykład (2026-09-22): `JavaPlugin#saveResource(…, false)` przy istniejącym pliku nie nadpisuje go
(dobrze), ale loguje `Could not save X to Y because X already exists` na poziomie WARNING przy
**każdym** starcie. Dlatego zasoby z JAR-a kopiujemy przez `saveResourceIfMissing(...)`
(`VillagerDefensePlugin`), a nie wprost. Zostawiając w logu ostrzeżenie „bo tak wyszło”, albo je
usuń, albo udowodnij w tym pliku, że jest niezależne od nas.

## 2. Jeden licznik, jedno źródło prawdy

Statystyki trybu Raft (życie, głód, pragnienie) mają **jeden** model: `pl.vd.raft.core.PlayerData`.
Pakiety `raft:stats` i cały HUD czytają wyłącznie z niego. `RaftHUDManager` jest **widokiem**
dla graczy bez moda (przerysowuje pragnienie na pasek XP) i nie może trzymać własnych statystyk.

Historia: przez pewien czas istniały dwa równoległe modele pragnienia — `PlayerData` (0–20,
spadał, ale nic go nie podnosiło) i `RaftHUDManager` (0–100, rósł od picia, ale nigdy nie
spadał). Efekt: widoczny pasek tylko schodził, a picie wody nic na nim nie zmieniało.
Takie rozjazdy są najdroższym rodzajem błędu w tym projekcie — **nie wprowadzaj drugiego modelu.**

## 3. Tempo zawsze w jednostkach czasu, nigdy „na klatkę” ani „na wywołanie”

- Klient: animacje liczone z `DeltaTracker` / `gameTime + partialTick`, nigdy `+= stała` na klatkę.
  (Tlen odnawiał się `0.003f` na klatkę: 60 FPS = 5,6 s, ale 144 FPS = 2,3 s.)
- Serwer: stałe podawane jako „na sekundę”, a `GameManager` przekazuje faktyczny okres pętli
  (`STATS_INTERVAL_TICKS`). (Pragnienie miało `-0.02f` na wywołanie, więc zmiana okresu pętli
  zmieniłaby balans po cichu.)

Jeśli piszesz stałą tempa, w komentarzu podaj jednostkę i wynik w sekundach.

## 4. HUD tylko w trybie Raft

Mod podmienia HUD **wyłącznie**, gdy `RaftStatsStorage.isActive()` (czyli gdy przyszedł pakiet
`raft:stats`). Poza trybem Raft wanilia musi wyglądać normalnie, bez śladu moda. Wyjście z trybu
zamyka serwer pakietem `raft:session_end`, a TTL 1,5 s jest tylko awaryjny.

## 5. Numeracja CustomModelData

`4001` radio, `4002` odbiornik, `4003` duża beczka, `4004` antena, `4005` żagiel. Nowe wartości
wybieraj świadomie — numeracja jest ręczna w trzech miejscach (item definition, edytor modeli,
plugin) i kolizje nie są wykrywane automatycznie. Rezerwacje pasów: `6001–6003` baterie,
`6101–6110` PAPIER — płyty utworów w GUI `/muzyka` (`DISC_CMD_BASE=6100 + indeks`; CMD 6111–6136
wolne, wymagają progu w `items/paper.json`), `9001–9010` latarenki.

- **Item models tylko w `assets/<ns>/items/*.json`.** `overrides` w `models/item/*.json` są od
  1.21.4 po cichu ignorowane — przedmiot wygląda waniliowo bez żadnego błędu w logu.
- **Mapowanie bierze się z pluginu, nigdy z paczki.** Jedyne źródło prawdy to `ItemManager.java`
  (`setCustomModelData` + `Material` + nazwa).
- **Model bez tekstury nie jest podłączany.** Kolejność: model **i tekstura** → wiersz `LIVE`
  w `tools/ItemDefinitions.java` → `tools/gen-item-definitions.sh`.
- **Fallback bierz z waniliowej definicji, nigdy z nazwy przedmiotu.** Ręczne
  `minecraft:item/<nazwa>` psuje bloki (`minecraft:block/<nazwa>`) i zwykły `heavy_core`.
- **Tekstury i UV nadaje narzędzie, nie ręka w Blockbenchu** — przy 152 elementach eksport
  skasowałby ręczną robotę.

Lista zaparkowanych modeli, trzy żywe pary CMD, historia błędów `heavy_core`/`spyglass`
i brakujące modele: `docs/PRZEDMIOTY_I_MODELE.md`.

## 6. Weryfikacja

Nazwy metod i klasy w MC 26.3 sprawdzaj w bytecode
(`~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged-deobf/26.3/minecraft-merged-deobf-26.3.jar`,
klient: `~/.gradle/caches/fabric-loom/26.3/minecraft-client.jar`, serwer NMS: `minecraft-server.jar`),
a nie z pamięci — to już raz uratowało przed crashem (`Hud.extractExperienceLevel` nie istnieje;
pasek XP to `ExperienceBar` + statyczne `ContextualBar.extractExperienceLevel`).

Przed twierdzeniem „działa” sprawdź, czy gracz nie potrzebuje **restartu klienta/serwera**:
podmiana JAR-a w `mods/` lub `plugins/` nic nie zmienia w już uruchomionym procesie.

Bezgłowe kontrole moda (po każdej zmianie w geometrii/animacjach): `tools/check-device-mesh.sh`
(parser siatek i animacji) oraz `tools/check-item-model.sh` (item definition **bez typów z modu**,
istnienie tekstur, brak ścian `#missing`, UV w granicach 0..16). Oba patrzą na klienta z cache Loom
`26.3` — po podbiciu wersji gry zsynchronizuj tam ścieżkę.

Kontrole **paczki** (na surowych jarach klienta z `/tmp/client26*.jar`, bez gry):

| Narzędzie | Odpowiada na pytanie |
|---|---|
| `tools/jar-strings.sh <jar> <literal>…` | czy ta wersja Minecrafta zna ten klucz JSON / nazwę typu |
| `tools/pack-metadata-check.sh [pack.mcmeta]` | czy `min_format`/`max_format` i `overlays` się parsują i który overlay wybierze dana wersja |
| `tools/pack-compat-check.sh <client.jar> <katalog>` | czy item definitions parsują się kodekiem tej wersji (klient **bez moda**) |
| `tools/gen-item-definitions.sh [klient.jar] [katalog…]` | generator: pisze item definitions z CMD i sprawdza, czy każdy model istnieje — jedyny test łapiący `minecraft:item/dirt` zamiast `minecraft:block/dirt` |
| `tools/publish-all.sh [--dry-run]` | **publikacja całego projektu** (mod + plugin + paczka), bez pytań; `--dry-run` to plan i kontrole |
| `tools/publish-pack.sh [--dry-run]` | czy paczka u graczy jest aktualna; dotyka hostingu i `server.properties` |
| `pack/tools/AntennaTextureGen.java`, `pack/tools/OdbiornikTextureGen.java` | generatory tekstur urządzeń (16×16) do `pack/assets/raft/textures/block` |
| `pack/tools/OdbiornikUvPaint.java` | malarz modelu odbiornika (materiał + UV po grupach); `--dry-run` pokazuje zmiany |
| `tools/model-shelves.sh <model> [skala]` | gdzie w modelu są półki i gdzie postawić shulker-kolizję; używane przy schodach |

`pack-compat-check.sh` domyślnie sprawdza obie paczki — jedyny test parsujący pliki **kodekiem
wanilii**, czyli takim, jakiego użyje gracz bez moda (łapie typ modelu z moda, który wanilia
odrzuci w całości).

## 7. Wersja gry: Minecraft 26.3

Cały projekt stoi na **26.3** — protokół 26.3 = **777**, 26.2 = **776**, więc klienty nie
wchodzą na siebie. **Serwer przyjmuje WYŁĄCZNIE 26.3**: ViaVersion i ViaBackwards są wyłączone
(`*.jar.wylaczone-20260922` w `VD - serwer/plugins/`), więc każdy tryb może używać bloków i encji
z 26.3 bez bramek wersji.

| Element | Wartość | Gdzie |
|---|---|---|
| Klient / mod | Minecraft `26.3`, Fabric Loader `0.19.5`, Fabric API `0.161.0+26.3` | `pack/gradle.properties` |
| Serwer | Paper `26.3` build **25 (alpha)** — `paper-26.3-25.jar` | `VD - serwer/start.bat` |
| API pluginu | `paper-api:26.3.build.25-alpha` — **przypięte do builda serwera** | `pom.xml` |
| `api-version` | `'26.3'` | `src/main/resources/plugin.yml` |
| Paczka zasobów | `min_format 97`, `max_format 97` (26.3 = zasoby **97.1**), bez overlayów | `pack/pack.mcmeta` |
| Datapack `raft-height` | `min_format 121`, `max_format 121` | `src/main/resources/datapacks/raft-height/pack.mcmeta` |
| Modpack | `minecraft = "26.3"`, `fabric = "0.19.5"` | `modpack/pack.toml` |

**Nie używaj zakresów wersji w `pom.xml`.** `[26.2.build,)` ciągnęło API z linii 26.3, a
`[26.3.build,26.4)` wybiera `26.3-pre-2.build.0-alpha`, bo Maven sortuje `build` przed `pre`.
Przy zmianie Papera zmień **oba** miejsca naraz: `pom.xml` i `start.bat`.

Najważniejsze zmiany API w 26.3: `PoseStack.rotateDegrees` zamiast `mulPose(Quaternionf)`,
`InputConstants.Type.KEYBOARD` zamiast `KEYSYM`; `fabric.mod.json` ma `"minecraft": ">=26.3"`,
żeby 26.2 dostał komunikat loadera zamiast crashu. Pełna lista, stan wtyczek (ProtocolLib
dev-build, voicechat 2.6.24, własny build GrimAC) i pomiary:
`docs/WERSJE_GRY.md`.

### 7.1. Sprawdzanie wtyczek po podbiciu wersji

Używamy próby `tools/protocollib-check.sh` — sprawdza dokładnie te 8 typów pakietów, których
plugin używa. Pełny opis i tabela ID pakietów: `docs/WERSJE_GRY.md` (§7.1).

### 7.2. Gracze na 26.2 na serwerze 26.3 — ścieżka ZAMKNIĘTA

Via jest wyłączona, więc starsi gracze nie wchodzą. Historia pomiarów i rollback:
`docs/WERSJE_GRY.md` (§7.2).

## 8. Datapack `raft-height`

Datapack z limitami wysokości Raft (Y −64..287) leży w JAR-ie pluginu i wdraża się **tylko gdy**
`raft-height-datapack: true` (domyślnie `false`). Datapacki czyta się **wyłącznie**
z `<level-name>/datapacks`, a plik `dimension_type` w złym schemacie potrafi zatrzymać start
serwera. Datapack nadpisuje `minecraft:overworld` (wszystkie światy NORMAL) i działa też na
istniejące światy, więc **budowania w górę pilnuje plugin** (`raft.build-height-limit`, 280),
a wysokość świata jest logowana przy starcie. Szczegóły i pomiary:
`docs/DATAPACK_RAFT_HEIGHT.md`.

## 9. Paczka zasobów

- **Format:** `min_format 97` / `max_format 97` — tylko 26.3; `min_format` to twarda bramka,
  a przy `require-resource-pack=true` klient z niższym formatem zostaje wyrzucony.
- **Bez overlayów.** Overlay `legacy_pre263` istniał dla 26.1/26.2, ale item definition
  z typem z moda (`raft:device`) powoduje odrzucenie całego pliku przez wanilię. Jeśli wróci
  wpuszczanie starszych klientów, overlay musi wrócić razem z nim.
- **Źródła Blockbencha (`*.bbmodel`) i pliki deweloperskie `pack/` nie jadą do graczy.**
- **Cztery kopie zasobów:** `pack/` → `modpack/resourcepacks/raft-hud` → `.minecraft` →
  kopia w jarze moda (`pack/src/main/resources`). Trzy pierwsze wyrównuje
  `tools/publish-pack.sh`; czwartą porównuje bajt w bajt `tools/check-item-model.sh`.
- **Publikacja:** `tools/publish-all.sh` (mod + plugin + paczka) albo `tools/publish-pack.sh`
  (sama paczka: deterministyczny zip, upload, weryfikacja sha1, wpis do `server.properties`).
  Nowa paczka działa graczom po **restarcie serwera**.
- `pack.zip` to plik dla graczy bez modpacka; klientem rządzi sha1, więc pod URL-em musi leżeć
  dokładnie ten sam plik.

Hosting, flagi skryptów, kolejność etapów i zapis `server.properties`:
`docs/PACZKA_ZASOBOW.md`.

## 10. Do zrobienia (otwarte tematy)

Lista istnieje, żeby tematy nie ginęły (§0.1). Punkt zamknięty usuń; kryteria odbioru
i szczegóły: `docs/OTWARTE_TEMATY.md`.

**Czeka na sprawdzenie w grze:** paski HUD i ich zniknięcie po wyjściu z trybu, animacja
przedmiotu w ręce, custom modele, anteny/kable/odbiornik, pobranie paczki przez klienta,
fundamenty i skrzynie na pokładzie (PPM w powietrze i w kolizję), hak (niski rzut), GUI skrzyni
przy dwóch graczach, restart serwera dla każdej nowej rzeczy z §12.

**Fixy do potwierdzenia:** obrót budowli (v39), drewniane schody (v40), podpora piętra (v41),
belki podpiętrza (v42), zawalanie bez podparcia + odzysk materiału (v43/v44), piętrowa Podłoga
(v45). Każdy ma kryteria w `docs/OTWARTE_TEMATY.md`.

**Repo:** `origin` → `https://github.com/pawel5824/villager-defense` (publiczne, branch `master`).
Musi być publiczne, bo klient Minecraft pobiera modpack i paczkę bez logowania. Kod i modpack
są wypchnięte (2026-11-10), więc mod może trafiać do graczy.

**Raft względem oryginału:** działa m.in. prąd, budowanie, sieć, hak, pragnienie, tlen, wiosłowanie,
urządzenia, crafting, muzyka i ustawienia; w połowie wyspy, radio, lornetka, żagiel (brak modelu
i weryfikacji w grze), schody, questy; brak rekina, oczyszczacza wody, gotowania, upraw, pieca,
steru i łupu z głębin. Szczegóły: `docs/OTWARTE_TEMATY.md`.

**Schody tratwy:** czekamy na model od właściciela — model jest źródłem prawdy dla kolizji
(`docs/SCHODY_TRATWY.md`).

## 11. Zasada projektowa: Mod = ułatwienie, nie wymóg

> **Każda funkcja musi działać z modem i bez moda.** Mod nie jest warunkiem konieczności
> do grywalności — jest warstwą wygody nad silnikiem gry.

Skutki tej zasady wynikające z kodu:

- **Logika siedzi w pluginie na serwerze**, nigdzie nie zależy od moda. Mod może rysować,
  ale nie decyduje o stanie gry.
- **Estetyka moda jest nadpisem** — animacje, kable, modele wyświetlane w świecie. Bez moda
  gra wygląda jak wanilia, ale gra. Mod podmienia fallback dodatkowym renderowaniem (BER,
  display-encje), co jest `mod-only` ulepszeniem, nie zastępnikiem definicji.
- **HUD moda jest jedynie widokiem** — paski `raft-hud`. Bez moda nie ma pasków, ale
  gra działa (stan dostępny przez `/raft debug` albo waniliowe paski).
- **Fallbacki trzymać w jednym miejscu** — w paczce zasobów jako zwykłe modele
  `assets/raft/models/item/*_fallback.json`, wskazywane jako `fallback` w
  `assets/minecraft/items/*.json`. Wpis musi być **zwykłym modelem**, nigdy nie
  `minecraft:special` ani żadnym typem z moda — wanilia w przeciwnym razie odrzuca cały plik
  (`Unknown element id: raft:device`, patrz §9).
- **Znaczniki w kodzie:**
  - `#fallback-ok` — działa z modem i bez. Nie wymaga fallbacku w paczce.
  - `#mod-only` — wymaga moda. Obowiązkowy opis fallbacku: co widzi gracz bez moda.

Przykłady (stan 2026-10-06):
- **Obrót budowli** — z modem: kółko w trybie budowy ORAZ Alt+kółko (bit2 kanału `raft:key_state`);
  bez moda: to samo kółko w trybie budowy i Shift+kółko przy przedmiocie obrotowym w ręce. Alt
  jest wyłącznie wygodą (obrót bez wchodzenia w tryb budowy) — bez moda funkcja działa w całości
  (`RaftBuildListener.onItemHeld`, `docs/RAFT_BALANCE.md` §1a).
- CMD 4002/4004/4005 (odbiornik/antena/żagiel) — były `minecraft:special` + `raft:device`,
  wanilia odrzucała cały `heavy_core.json`. Teraz to zwykłe modele `raft:item/*_fallback` z
  fallbackiem `minecraft:item/heavy_core`. Mod rysuje pełną geometrię w świecie.
- Żagiel w świecie: fallback zrobiony (ItemDisplay + BlockDisplay, `RaftSailFallbackRenderer`).
  Klawisz Z → fallback na stół rzemieślniczy — propozycja, nie zrobione.

Parafraza: jeśli usuniemy moda z paczki, a gra nadal działa — to projektowane prawidłowo.

## 12. Trwałość: wszystko, co jest w grze, przeżywa restart serwera (twarda zasada)

> **Każda rzecz, którą gracz widzi albo zrobi w grze — blok, GUI, przedmiot, urządzenie,
> zawartość skrzyni, ustawienie, postęp — ma wrócić po restarcie serwera w takim samym
> stanie. Funkcja, która ginie przy restarcie, jest NIEUKOŃCZONA, a nie „działa”.**

Dotyczy każdego nowego pluginu, mechaniki i zmiany w istniejącej: przy skrzyni na tratwie
zawartość ginęła przy restarcie, bo `RaftPersistence` zapisywał tylko `net-contents`
(teraz jest `chest-contents`, v6). Taka strata to błąd do naprawy, nie szczegół do dopisania
„kiedyś”.

**Jak to robić (kolejność prac):**

1. **Stan trzymaj w pluginie, nie w świecie.** Encje (BlockDisplay, shulker-kolizje)
   i bloki są tylko wizualizacją — rozładują się albo znikną, a świat nie pamięta, że
   „tu była tratwa/skrzynia”. Jeśli coś istnieje w grze, musi mieć lustro w pliku.
2. **Zapis do pliku w `plugins/ServerPlugin/`** (YAML, jak reszta projektu) **z polem `version`**
   — po nim widać, czy stary zapis da się jeszcze przeczytać.
3. **Odczyt przy starcie pluginu albo wejściu gracza** (`RaftPlugin.onEnable`,
   `RaftManager.restoreRaftForPlayer`, `DevicePersistence.loadAll`, `LatarenkaManager`).
4. **Zapis w kilku momentach:** zmiana stanu (gdy tania), wyjście gracza z gry, autosave
   (np. co 60 s) i `onDisable()` — awaria serwera nie może cofać całej sesji.
5. **Stare zapisy mają się dalej czytać** — brak nowego pola = wartość domyślna, nigdy crash
   (wzór: `RaftPersistence` czyta komórki bez typu/grubości/uszkodzenia, a tratwy bez
   `chest-contents` jako puste skrzynie).
6. **Dopisz nową rzecz do tabeli niżej i do listy testów w §10**, a potem sprawdź restartem:
   zrobić w grze → wyjść z gry → zrestartować serwer → wrócić i porównać stan.

| Co ma przetrwać | Plik | Kiedy zapis (sprawdzone w kodzie) |
|---|---|---|
| tratwa: komórki, żagiel, sieć, **skrzynie**, **kierunek komórek obrotowych** (6. pole wpisu komórki), **podpory pięter** (`supports`) | `raft-saves.yml` (`version: 6`, `chest-contents`) | wyjście gracza, autosave 60 s, `onDisable` |
| gracz tratwy (nauka, questy, ustawienia) | `raft-players.yml` | wyjście gracza / `onDisable` |
| urządzenia na tratwie (odbiornik, antena…) | `devices.yml` (`version: 2` — z obrotem) | postawienie/zdjęcie urządzenia, autosave tratwy, `onDisable` |
| latarenki | `latarenki/<nazwa>.yml` | przy zmianie latarenki |
| wyspy, questy, fabuła | `islands.yml`, `quests.yml`, `story.yml` | `ConfigManager` |
| gracze (statystyki, ustawienia, **wybrany kit**), areny, drużyny, znajomi, dzienna nagroda | `players.yml` (`settings.chosen-kit`), `arenas.yml`, `parties.yml`, `friends.yml`, `daily_reward.yml` | przy zmianie / wyjściu gracza |
| ciemność wody | `water-darkness.yml` | przy zmianie ustawienia |

**Znacznik w kodzie:** nowa rzecz w grze bez zapisu dostaje `#TODO-persist` z krótkim opisem,
czego brakuje — to dług do spłaty w tej samej sesji, nie na później.

### 12.1. Audyt trwałości (2026-10-06, build `v38`)

Znalezione i naprawione luki (obrót urządzeń, wybrany kit) oraz świadome wyjątki:
`docs/TRWALOSC.md`.
