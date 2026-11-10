# Raft Mode — sterowanie i stałe balansowe

Zebrane z kodu (wrzesień 2026). Wszystkie wartości są stałymi w kodzie — zmiana wymaga rekompilacji (`mvn package`), chyba że wskazano inaczej. Kluczowe pliki: `HookManager.java` (hak), `OceanDebrisManager.java` (ocean/beczki), `CollectionNetListener.java` (sieć).

## 1. Hak (`HookManager`)

| Parametr | Stała | Wartość | Opis |
|---|---|---|---|
| Maks. zasięg rzutu | `MAX_RANGE` | **~13.3 bloka** (40/3) | Spławik zatrzymuje się i opada po przekroczeniu (dystans XZ od miejsca rzutu). |
| Prędkość zwijania | `REEL_SPEED` | 1.4 bloka/tick | Zwykłe zwijanie. |
| Prędkość zwijania szybkiego | `REEL_FAST_SPEED` | 4.0 bloka/tick | Shift+PPM (natychmiastowe). |
| Pojemność haka | `HOOK_CAPACITY` | **8 sztuk** | Suma sztuk nabitych na hak. |
| Promień łapania — poziom | `CATCH_RADIUS_XZ` | **~0.73 bloka** (1.6/3 + 0.2) | Od środka spławika; dotyczy każdego przedmiotu. |
| Promień łapania — pion | `CATCH_RADIUS_Y` | **0.6 bloka** (1.2/3 + 0.2) | J.w. |
| Rozmiar wizualu przedmiotu | `HOOK_VISUAL_SCALE` | 0.25 bloka | Beczka na haku = pełny blok 1×1×1. |
| Uznany za „spoczywający" | `SETTLE_SPEED` | 0.08 bloka/tick | Przez `SETTLE_TICKS` (5) — wtedy hak siada. |

### Sterowanie hakiem

| Akcja | Gracz z modem raft-hud | Gracz BEZ moda |
|---|---|---|
| Rzut | PPM | PPM |
| Przyciąganie płynne | **trzymany PPM** (puszczenie = stop) | tap PPM = **start**, drugi tap = **stop w miejscu** |
| Natychmiastowe zwinięcie | Shift+PPM | Shift+PPM |
| Tap PPM (hak w wodzie) | nic (celowo) | start/stop przyciągania (toggle) |

Uwaga: wykrywanie moda naprawione — Paper 26.2+ nazywa akcesor `DiscardedPayload.id()`
(było `channel()`), bez tego serwer widział wszystkich jako „bez moda". W kliencie 26.3
rekord `DiscardedPayload` nadal ma pole i akcesor `id()`, więc wykrywanie działa bez zmian.

## 1a. Obrót budowli (`RaftBuildListener`, build `v40`)

Kierunek dotyczy komórek OBROTOWYCH (`RaftEntity.isOrientable`): skrzynie (obrót
przodu/latcha) i **drewniane schody** (obrót modelu schodów); cienkie ściany
(`wooden_wall`) czekają na swój przedmiot — kod jest na nie gotowy.
Podłoga i sieć kierunek ignorują. Stan jest per gracz (`Map<UUID, BlockFace>`
w `RaftBuildListener`); brak wyboru = AUTO, czyli kierunek z patrzenia
(dominująca oś pozioma wektora patrzenia; patrzenie pionowo = północ).

| Gest | Efekt | Kto |
|---|---|---|
| kółko w górę / w dół (tryb budowy: aktywny duch z GUI młota) | 90° w prawo / w lewo | każdy |
| klawisze **1–4** (tryb budowy) | preset kierunku N / E / S / W | każdy |
| **Shift + kółko** (tryb budowy) | powrót do kierunku z patrzenia (AUTO) | każdy |
| **lewy Alt + kółko** (poza trybem budowy, z przedmiotem obrotowym w ręce) | 90° w prawo / w lewo bez otwierania GUI | tylko z modem raft-hud (bit2 kanału `raft:key_state`) |
| **Shift + kółko** (poza trybem budowy, z przedmiotem obrotowym w ręce) | 90° w prawo / w lewo | każdy (to samo bez moda) |

### Przedmioty obrotowe

| Przedmiot | ID / CMD | Komórka | Gdzie staje | Kategoria w młocie |
|---|---|---|---|---|
| Mała skrzynka | `chest_small` / 7035 | `CHEST_18` | na pokładzie (nad deską) | Tratwy |
| Skrzynia | `chest_medium` (bez CMD) | `CHEST_36` | na pokładzie (nad deską) | Tratwy |
| Duża skrzynia | `chest_large` / 7036 | `CHEST_54` | na pokładzie (nad deską) | Tratwy |
| **Drewniane schody** | `wooden_stairs` / 7038 | `STAIRS` | na pokładzie (nad deską) | **Inne** |

Schody rysuje `RaftEntity.dataFor` (zawsze `OAK_STAIRS` + kierunek), więc
postawiona komórka, podgląd-duch i podświetlenie siekiery pokazują ten sam
obrót. Kolizja komórki to nadal pełna kostka 1×1×1 (system komórek nie ma
kształtów poza cienką ścianą), więc po schodach nie da się „wejść" jak
po waniliowych — trzeba na nie wskoczyć.

Poza trybem budowy zwykłe przewijanie ekwipunku działa bez zmian — obrót wymaga
Alt/Shift. W trybie budowy (duch) hotbar jest zablokowany celowo: klawisze 1–4
ustawiają kierunek, a nie zmieniają slot.

Shift+kółko znaczy więc raz „przywróć kierunek z patrzenia" (w trybie budowy, gdzie
samo kółko już obraca), a raz „obróć" (poza trybem, gdzie samo kółko musi dalej
przewijać ekwipunek).

Kierunek jest zapisywany razem z komórką w `raft-saves.yml` (6. pole wpisu
komórki, `version: 6` — format bez zmian) i wraca po restarcie serwera.
Alt nie istnieje w zdarzeniach Bukkita (sprawdzone `javap` na paper-api 26.3),
dlatego jest wyłącznie dodatkiem moda — bez moda obrót działa kółkiem w trybie
budowy i Shiftem (§11: mod = ułatwienie, nie wymóg).

## 1b. Podpora piętra, belki podpiętrza i runięcie piętra (`wooden_support`, build `v41`–`v44`)

Podpora to element NAROŻNY: staje w narożniku 4 komórek („na końcu 4 bloków"),
nie zajmuje komórki siatki i **nie ma kolizji** — gracz przechodzi przez słupek.
Świadomie **1 encja na podporę** (wizual: BlockDisplay albo ItemDisplay),
zero markerów i shulkerów (komórka pokładu to 3 encje).

| Co | Wartość |
|---|---|
| Wysokość słupka | `raft-support.height-blocks` (domyślnie **2**); model płotka ma 1.5 bloka, więc skala Y = height / 1.5 |
| Materiał wizualu | `raft-support.material` (domyślnie `OAK_FENCE`) |
| Custom model z Blockbencha | `raft-support.custom-model-data` > 0 → wizual to ItemDisplay z przedmiotem `model-item` o tym CMD (wzorzec jak latarenki); model liczony od środka podstawy kratki, wysokość 1 blok |
| Poziom piętra | podstawa słupka (0.5) + wysokość + 0.5 → **Y=3** przy wysokości 2 (`RaftEntity.floorLevel()` — jedno źródło prawdy) |
| Limit podpór | `RaftEntity.MAX_SUPPORTS` = 300 na tratwę |
| Przedmiot piętra | `wooden_floor` (nazwa **Podłoga**, CMD **7040**, OAK_PLANKS ×9) — GUI młota → „Inne" slot 12, `/raft przedmiot wooden_floor` albo przepis w `/wytwarzanie` → „Materiały / Zasoby" (4× deska + 1× lina) |
| Belki podpiętrza | `raft-support.beams` (domyślnie `true`) + `raft-support.beam-material` (domyślnie `DARK_OAK_PLANKS`) |
| Grubość/szerokość belki | stałe `RaftSupportRenderer.BEAM_HEIGHT` / `BEAM_WIDTH` = **0.25** bloka |
| Opóźnienie runięcia | `raft-support.collapse-delay-ticks` (domyślnie **12** = 0,6 s; 0 = od razu) |

**Stawianie:** PPM przedmiotem `wooden_support` (jest w GUI młota → „Inne").
Podpora staje w tym z czterech narożników trafionej komórki, który jest
**najbliżej gracza**; węzeł musi dotykać pokładu (choć jedna z 4 komórek wokół
niego istnieje), a drugiej podpory w tym samym węźle nie postawisz.

**Dwa rodzaje podłogi:** pokład tratwy (`wooden_foundation` /
`stone_foundation` — można nimi rozbudowywać tratwę po wodzie i piętro) oraz
**piętrowa `Podłoga`** (`wooden_floor`, v45) — **TYLKO** na piętro: na wodzie,
jako pokład tratwy, nie da się jej postawić i podgląd-duch w ogóle się nie
pokazuje (`RaftBuildListener.isDeckBuild` = false, komunikat zostaje tylko
w `Placement.message`). Odwrotnie: oba fundamenty nadal robią i pokład, i piętro
(`isDeckBuild`/`isFloorBuild`).

**Piętro — reguła „tylko 4 komórki wokół podpory** (na 3. kratce)":** platforma
(`wooden_floor` / `wooden_foundation` / `stone_foundation`) na poziomie Y=3
powstaje wyłącznie wtedy, gdy komórka leży **wokół słupka** — węzeł podpory jest
jednym z jej czterech narożników — czyli z 4 stron podpory po jednej kratce.
Wejścia:

- celujesz w **słupek podpory** — komórka piętra staje w tej z 4 kratek wokół
  węzła, która jest najbliżej gracza (musi być nad pokładem, nie nad wodą), albo
- celujesz w **istniejące piętro** — rośnie w bok jak pokład, ale każda nowa
  komórka musi przejść `RaftEntity.holdsCell` (narożnik + słupek sięgający jej
  spodu).

**Dobudowa DALEJ niż podpora jest zablokowana** — druga kratka od słupka nie ma
węzła w swoim narożniku, więc `isCellSupported` zwraca `false` i podgląd-duch
gaśnie (nic się nie stawia). Węzły podpór stawia się **co 2 kratki** — dalej
trzeba postawić kolejną podporę.

**Belki podpiętrza (v42):** pod każdą komórką piętra leci rama z belek — „żeby
platforma nie wyglądała na wiszącą w powietrzu". Belka to **1 cienki
BlockDisplay** (spłaszczony pełny blok `beam-material` do 1 × 0.25 × 0.25) na
krawędzi komórki, tuż pod jej spodem: górna płaszczyzna belki (lokalne Y = 2.25 →
2.5) styka się ze spodem platformy, czyli z wierzchem słupka. Krawędź dzielona
przez dwie komórki daje **JEDNĄ** belkę (`RaftSupportRenderer.Beam.key()`), więc
platforma 2×2 (jedna podpora w środku) to 12 belek, a nie 16 — belki sąsiadów
stykają się i tworzą ciągłą kratownicę rozchodzącą się od słupka na 4 komórki
wokół niego. Geometria jest czystą funkcją (`beamsFor`), a wizual powstaje
z komórek piętra w każdym `syncAllVisuals()`, więc `beams: false` w configu
usuwa belki bez restartu tratw.

**Zdejmowanie (v43/v44) — podpora zabiera piętro ze sobą:** LPM siekierą w słupek
(promień minął komórki, więc trafia `raycastSupport`). Podpora nie ma
wytrzymałości — schodzi od razu (z dźwiękiem rozbijanego bloku), a **razem z nią
runą komórki piętra, które trzymała** (te, które po jej zniknięciu nie mają już
żadnej podpory w narożniku) — razem z belkami podpiętrza, bo te są pochodną
komórek. Nic nie zostaje wiszące w powietrzu:

- komórka PIĘTRA (Y = `floorLevel`) bez podpory w narożniku → znika,
- komórka NAD pokładem (Y > 0 — skrzynie, schody) bez komórki dokładnie pod
  sobą → znika (zawartość skrzyni wypada do wody, tak samo jak przy rozbiórce
  siekierą),
- pokład (Y = 0) niczego nie wymaga — trzyma go woda,

czyli zdjęcie słupka zabiera też to, co stało na zawalonym piętrze (sprzątanie
idzie warstwami od góry, aż nic już nie wisi — `RaftEntity.pruneUnsupportedCells`,
czysta reguła w `RaftEntity.cellIsAnchored`).

**To samo sprzątanie działa przy ZWYKŁEJ rozbiórce komórki (v44):** rozbicie
deski siekierą nie zostawia już skrzyni na pustce — wszystko, co straciło
podstawę, runie po chwili (ten sam `pruneUnsupportedCells`).

**Krótkie opóźnienie i odzysk materiału (v44):** runięcie nie jest natychmiastowe
— komórki bez podparcia znikają po `raft-support.collapse-delay-ticks` tickach
(domyślnie **12** = 0,6 s, 0 = od razu), więc najpierw widać brak słupka, a potem
spadającą platformę. W chwili wykonania tratwa liczy wszystko OD NOWA, więc gdy
gracz zdąży wstawić nową podporę, piętro zostaje (da się je „uratować”). Każda
zawalona komórka:

- gra dźwięk rozbijanego bloku + cząsteczki ze swojego materiału,
- **wypada do wody jako przedmiot do odzyskania** (1× materiał komórki:
  deski/cobblestone/schody/... — zawartość sieci i skrzyń wypada osobno, tak jak
  przy zwykłej rozbiórce),

ale podstawa tratwy (komórki `RAFT`) i pokład nigdy nie znikają. Belki schodzą
też same po zmianie configu (`beams: false`).

**Uwaga:** komórka piętra znika tylko wtedy, gdy straciła WSZYSTKIE podpory —
stojąca w narożniku drugiego słupka zostaje (i dalej trzyma to, co na niej stoi).

**Trwałość:** `raft-saves.yml` → `supports: ["x;y;z", ...]` (węzły lokalne,
`version: 6`). Belki NIE są zapisywane — to pochodna komórek piętra i podpór
(odtwarza je sync). Format komórek bez zmian — nowe pole jest opcjonalne i stare
zapisy czytają się jak dotąd.

## 2. Ocean — śmieci i beczki (`OceanDebrisManager`)

| Parametr | Stała / config | Wartość | Opis |
|---|---|---|---|
| Generowanie śmieci | `debrisEnabled` | config `raft-debris` | Włącznik pola śmieci. |
| Liczba obiektów przed tratwą | `minItemsAhead`/`maxItemsAhead` | config (13–28) | Uzupełniane pojedynczo, losowo. |
| Dystans spawnu od dziobu | `nearestDist`/`farthestDist` | config (40–70) | Bloki. |
| Rozrzut boczny | `lateralSpread` | config (14) | Od osi dryfu. |
| Szansa beczki | `barrelChance` | config (0.15) | Prawdopodobieństwo, że obiekt = beczka. |
| Wagi śmieci | `DEBRIS_WEIGHTS` | deska 30, liście 30, plastik 25, odpadki 15 | Suma 100. |
| Żywotność beczki | `BARREL_LIFETIME_MS` | 10 minut | Potem znika. |
| Zebranie beczki wejściem | `BARREL_WALK_PICKUP_XZ`/`Y` | 0.9 / 2.0 | Zasięg kolizji gracza (cicho, bez efektów). |
| Wysokość spoczynku śmiecia | `DEBRIS_REST_Y` | 0.15 bloka nad wodą | Bez grawitacji — nie odbijają się. |
| Zasięg pickupu śmieci przez gracza | `OCEAN_PICKUP_RANGE_XZ` | **0.33 bloka** | Wanilia ma ~1.0; zmniejszone o 2/3. |

### Loot beczki (`BARREL_LOOT`)

Szansa bazowa (brama) → przy trafieniu ilość losowana wg wag; max 5 przedmiotów (`MAX_BARREL_DROPS`).

| Przedmiot | Szansa bazowa | Wagi ilości (1/2/3/4/5/6 szt.) |
|---|---|---|
| Deska | 40% | 1→26, 2→33, 3→22, 4→8, 5→2, 6→0.7 |
| Liście | 33% | 1→40, 2→28, 3→15, 4→7, 5→1, 6→0.3 |
| Plastik | 27% | 1→38, 2→21, 3→10, 4→3, 5→0.5 |
| Odpadki | 23% | 1→36, 2→20, 3→8, 4→2, 5→0.4 |
| Kamień | 18% | 1→25, 2→11, 3→4, 4→0.6 |
| Lina | 8% | 1→25, 2→12, 3→2, 4→0.8 |
| Ziemniak | 6% | 1→25, 2→12, 3→2, 4→0.8 |
| Burak | 6% | 1→25, 2→12, 3→2, 4→0.8 |
| Kokos | 5% | 1→25, 2→12, 3→2, 4→0.8 |

Loot jest wspólny dla wszystkich trzech metod zbierania: prawy klik (drop na ziemię), wejście w beczkę (prosto do eq), hak (beczka wisi na haku, loot przy zwinięciu do eq).

### Beczka na haku

- Beczka nabija się na hak jako pełnowymiarowy wizual (facing=up), loot losowany dopiero przy zwinięciu (`deliverBarrelLoot`).
- Nabijanie beczki: bez dźwięku; przy złapaniu plusk `ENTITY_PLAYER_SPLASH` (0.9/1.3); otwieranie przy zwinięciu — bez dźwięku.
- Beczka zużywa 1 z 8 slotów pojemności haka.

## 3. Sieć zbierająca (`CollectionNetListener`)

| Parametr | Stała | Wartość | Opis |
|---|---|---|---|
| Promień łapania — poziom | `CATCH_RADIUS_XZ` | 1.15 bloka | Łapie tylko małe śmieci (nie beczki). |
| Promień łapania — pion | `CATCH_RADIUS_Y` | 1.5 bloka | |

## 4. GUI wytwarzania

- `/wytwarzanie` (i klawisz Z z moda) — tylko świat Raft, GUI 9×5 (`CraftingCommand`, tytuł „Wytwarzanie").
- Górny rząd = kategorie (Jedzenie/woda, Narzędzia, Bronie, Ekwipunek, Materiały/Zasoby, Nawigacja, Inne, Dekoracje); klik w kategorię pokazuje jej przepisy z poświatą na aktywnej.
- Treść = przedmioty do wytworzenia (`CATEGORY_RECIPES`, sloty 9–16 / 18–25 / 27–34). Klik w przedmiot pobiera składniki z ekwipunku gracza i wydaje wynik; podpowiedź pokazuje na bieżąco ✔/✘ (bez komunikatów na czacie).
- Waniliowy stół rzemieślniczy niczego nie wytwarza — `RecipeManager` ma receptury celowo wyłączone (zostawione jako wzorce).
- Zaimplementowane przepisy:
  - **Nawigacja → Żagiel** (CMD 4005) = 3× biała wełna + 3× deska + 1× lina,
  - **Materiały / Zasoby → Podłoga** (piętrowa, CMD 7040) = 4× deska + 1× lina
    (v46) — jedna sztuka = 9 kratek piętra, więc piętro zdobywa się bez komendy.

## 5. GUI ustawień

- `/ustawienia` w świecie Raft otwiera panel Rafta (GUI 4×9, `UstawieniaGUI`, tytuł „§8Ustawienia"); poza Raftem — panel VD (54 sloty). Szczegóły: `docs/USTAWIENIA.md`.
- Sloty (w grze): 11 Dźwięki (przełącznik), 13 Powiadomienia (przełącznik), 15 Muzyka (otwiera GUI `/muzyka`), 17 Język (polski/angielski, zapis globalny do players.yml VD), 32 Wyjdź.

## 6. Efekty haka — stan po zmianach

- **Dźwięki**: hak całkowicie cichy — własne (rzut/plusk/zwinięcie) usunięte; waniliowe `entity.fishing_bobber.*` anulowane pakietowo (`RaftHookSoundMuter`, ProtocolLib, tylko świat Raft).
- **Particles**: waniliowe „rybki" (`FISHING`) anulowane pakietowo; zostają tylko własne SPLASH (woda) i CRIT (ląd) przy siadaniu haka.
- **Wizualy przedmiotów na haku**: zamrożone, gdy hak zakotwiczony; podążają za spławikiem w locie/zwijaniu/przyciąganiu; przedmioty idealnie na środku (nachodzą na siebie); beczka = pełny blok.
