# Żagiel tratwy — render w świecie (kanał `raft:sails`)

Stan: **2026-09-25, nie sprawdzone w grze** (patrz „Czego brakuje").

## Dlaczego nie BER, jak odbiornik i antena

Odbiornik i antena stoją w świecie na **bloku-nośniku**: beczka z `facing=down, open=true`
jest w paczce niewidzialna, ale ma BlockEntity, więc wanilia pyta BlockEntityRenderer
o tę pozycję — mod rysuje tam dużą geometrię z `raft:receivers`.

Tratwa **pływa**, więc taki nośnik na jej pokładzie odpada:

- beczka na pokładzie to prawdziwy blok — stoi w przestrzeni, w której chodzą gracze
  (blok w ciele = wypychanie/uduszenie),
- beczka w komórce pokładu weszłaby w przestrzeń shulkera-kolizji, a shulker w bloku
  może zginąć od uduszenia i rozbić kolizję tratwy,
- nośnik trzeba by przesuwać co tick (block update + przebudowa sekcji chunka).

Dlatego żagiel rysuje **renderer świata**, a pozycję i stan dostaje pakietem.

## Kontrakt pakietu (plugin ↔ mod)

Kanał `raft:sails`, wysyłka co 2 ticki (10 Hz) do graczy w świecie Raft w zasięgu
128 bloków od pokładu. Lista jest ZAWSZE PEŁNA — klient podmienia całość, więc tratwa
bez żagla po prostu nie ma wpisu.

```
int count
count × {
  long   uuidMost, uuidLeast   // klucz: UUID tratwy (tratwa dryfuje — klient dopasowuje wpisy)
  double x, y, z               // światowa PODSTAWA MASZTU (RaftEntity.getSailAnchorWorld)
  float  yaw                   // obrót tratwy w stopniach
  int    flags                 // bit 0 = żagiel rozwinięty
}
```

Wpis = 48 bajtów. Serwer: `src/main/java/pl/vd/raft/net/RaftSailPacketSender.java`.
Klient: `pack/src/main/java/com/raft/hud/device/RaftSailsPayload.java`,
`RaftSailStorage.java`, `RaftSailWorldRenderer.java`.

## Tor rysowania po stronie moda

- `LevelRenderEvents.AFTER_SOLID_FEATURES` (Fabric 0.161 / MC 26.3). W tej wersji
  **`WorldRenderEvents` już nie istnieje** — nowy tor to `LevelRenderEvents`
  z `LevelRenderContext`, który daje `submitNodeCollector()` i `poseStack()`.
- Geometrię rysuje ten sam `DevicePartRenderer`, co urządzenia w świecie i przedmiot
  w ręce, więc model z Blockbencna (`assets/raft/rafthud/zagiel.json`) i plik
  `zagiel.anim.json` działają tu identycznie (żadnej drugiej matematyki).
- Pose poziomu jest liczony **względem kamery** (`Camera.position()`), tak jak gizmos
  debugowe; model kotwiczymy na podstawie masztu.
- Pozycja i czas animacji są wygładzane w `RaftSailStorage` (serwer wysyła 10 Hz,
  oko widzi płynny ruch; bez tego żagiel skakałby co 100 ms).

## Rozwijanie i zwijanie (co ma zrobić model)

Czas animacji, jaki dostaje `DevicePartRenderer`, to:

- **0** — żagiel zwinięty,
- `length` z pliku `zagiel.anim.json` — żagiel rozwinięty.

`RaftSailStorage` przechodzi między tymi wartościami płynnie (~kilka klatek), więc
**rozwijanie jest animacją, a nie podmianą modelu**: wystarczy, że artysta opisze
w `.anim.json` ruch płótna klatkami od `t = 0` (płótno zwinięte przy maszcie)
do `t = length` (płótno rozwinięte). Przykład toru:

```json
{ "part": "plotno", "channel": "rotation", "axis": "x",
  "keys": [ { "t": 0, "v": 0, "e": "ease" }, { "t": 20, "v": -90, "e": "ease" } ] }
```

Wymagania wobec modelu (grupy jak w stubie):

- grupa `zagiel` — rodzic, origin w **środku kolumny masztu** (8,0,8 w pikselach
  modelu), bo kotwicą w świecie jest podstawa masztu,
- grupa `plotno` — dziecko, animowane rozwijanie,
- jak wszystkie siatki urządzeń: tekstury 16×16, elementy 0 px grubości jako dwie
  ściany, nazwy części po polsku.

## Stan serwera (skąd bierze się wpis)

- `RaftEntity.hasSail()` — jest komponent `SAIL`,
- `RaftEntity.isSailOpen()` — flaga `sailOpen` (przetrwa restart w `raft-saves.yml`),
- `isSailing()` = jedno i drugie → **napęd silnika fizyki z wiatru** zamiast z prądu
  (patrz AGENTS.md §5, „Fizyka żagla"),
- stawianie/rozkładanie: PPM przedmiotem CMD 4005 w pokład, PPM w komórkę żagla
  (nawet pustą ręką) rozwija/zwija, Shift + PPM demontuje (`SailPlaceListener`).

## Fallback dla graczy bez moda (2026-10-02)

Gracz z modem widzi mesh (`RaftSailWorldRenderer`), ale gracz bez moda nie ma czym go
narysować — dlatego serwer stawia mu w świecie dwie display-encje
(`RaftSailFallbackRenderer`, `src/main/java/pl/vd/raft/entity/`):

| Element | Encja | Wygląd |
|---|---|---|
| maszt | `ItemDisplay` | `STRIPPED_OAK_LOG` przeskalowany transformacją do słupa 0,25 × 2,5 bloka, podstawa na pokładzie |
| płótno | `BlockDisplay` | `WHITE_WOOL` jako cienka płyta (1,4 szer. × 1,9 wys. × 0,0625 gr.) za masztem, środek 1,3 bloka nad pokładem; zwinięte = wąska rolka 0,16 |

Proporcje są wzięte z modelu paczki `raft:item/zagiel_fallback` (maszt 2×14 px, płótno
12 × 9 px) przeskalowanego ×2,5, żeby żagiel miał rozmiar świata, a nie rozmiar trzymany
w ręce; grubość płótna 1 px = 0,0625 bloka, dokładnie jak w siatce moda (`zagiel.json`).
Wszystko liczone jest OD PODSTAWY MASZTU, czyli od kotwicy pakietu `raft:sails`:

- Kotwica, yaw i stan rozwinięcia są DOKŁADNIE te same, co w pakiecie `raft:sails`
  (`getSailAnchorWorld()`, `getYawDegrees()`, `isSailOpen()`) — fallback stoi tam, gdzie mesh,
  i tak samo się rozkłada (płótno interpoluje przejście przez 4 ticki).
- Kotwica = **górna powierzchnia deski** (`MAST_BASE_Y = 0.5` w komponentach tratwy;
  pokład to 0.5, bo kostka deski i box shulkera pokrywają ±0,5 od środka tratwy). Mesh moda
  kotwiczy na tym punkcie origin grupy `zagiel` (8,0,8 px), a pivot animacji rozwijania leży
  „przy pokładzie” (`zagiel.anim.json`), więc podstawa masztu musi stać na deskach — wcześniejsze
  1.0 (pół bloka nad pokładem) wieszało i mesh, i fallback w powietrzu. Zmiana stałej w
  `RaftEntity` porusza JEDNOCZEŚNIE mesh i fallback (ten sam pakiet), a stare zapisy tratw są
  wyrównywane przy wczytaniu (`raft-saves.yml` ma Y komponentów nadpisywane stałymi).
- Rozpoznanie moda: `KeyStateReceiver.has(uuid)` (kanał `raft:key_state`; mod wysyła stan od razu
  po wejściu). Encje startują z `setVisibleByDefault(false)`, więc gracz z modem nigdy nie dostaje
  ich w pakiecie spawnu; gracz bez moda dostaje `showEntity`. Ukrycie u graczy z modem jest co
  sekundę wymuszane ponownie (bezpiecznik, gdyby tracker ponownie zarejestrował encję).
- Cykl życia: sync w `RaftEntity.syncAllVisuals()` (co tick w FULL, co 4 ticki w FAR), cull razem
  z deskami w trybie FROZEN, `cleanup()` przy rozbiórce tratwy; encje `persistent=false`, więc po
  rozładowaniu chunku wracają przy najbliższym sync. Zasięg pokazywania:
  `RaftEntity.DISPLAY_SPAWN_RANGE` (128 bloków od kotwicy).
- Gdy powstanie docelowy model żagla, maszt najprościej podmienić na przedmiot z paczki (nowy
  CMD w `heavy_core.json`), a płótno zostawić jako `BlockDisplay` — paczka nie zmieni wyglądu
  jednego bloku bez globalnego override'u blockstate.

## Czego brakuje

- **Weryfikacji w grze** — tor rysowania i konwencja pose są odczytane z bajtkodu
  26.3 i API Fabric, nikt tego jeszcze nie widział na ekranie. Jeśli żagiel pojawi się
  w złym miejscu (np. przy zerze świata), pierwsze podejrzenie: pose poziomu jest
  bezwzględny, a nie camera-relative (`RaftSailWorldRenderer.render`).
- **Modelu** — siatka w paczce to stub (2 ściany, 1 px grubości), a `zagiel_plotno.png`
  to jednolity beż; przedmiot CMD 4005 ma zwykły model `raft:item/zagiel_fallback`
  (placeholder bez modu, 2026-10-01). Po dorobieniu modelu podmienić wpis w `heavy_core.json`
  i plik modelu fallback.
- Świecenia: żagiel rysuje się w pełnym świetle (0xF000F0); jeśli w nocy będzie
  wyglądał nienaturalnie, doliczyć światło z `LevelRenderState`/pozycji.
