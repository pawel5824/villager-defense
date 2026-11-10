# Raft — historia wdrożeń i szczegóły techniczne

Przeniesione z `AGENTS.md` (stan 2026-10-05). Zasady są w `AGENTS.md` §1–§12, tutaj jest
„jak to zostało zrobione” i konkretne klasy/pliki.

## Odbiornik, anteny i kable

Projekt zaakceptowany w `docs/ANTENY_ODBIORNIK.md` — decyzje właściciela (stawianie przedmiotem
PPM, zasięg 8 bloków w XZ i wyłącznie na tym samym Y, zerwanie natychmiast, kable tylko w modzie,
bez komunikatów) nie podlegają zmianie przy implementacji.

Stan: **fazy 0–5 zrobione** — persistencja urządzeń, nośnik + `raft:receivers`, anteny + linki,
gating „Mapy wysp”/kompasu przy 3/3, `kind=1` anten + kable rysowane przez mod, finalny model
anteny z Blockbencha razem z przedmiotem.

**Model anteny (faza 5, 2026-09-23):** jedna komórka tratwy, 12×23×12 px, dwie tekstury 16×16
(`antena_metal`, `antena_lamp`) z generatora `pack/tools/AntennaTextureGen.java`, źródło
Blockbencha `antena.bbmodel` (nie jedzie do paczki). Ruch: `ramiona` obracają się wokół osi
masztu, `swiatelko` pulsuje (`antena.anim.json` — usunięcie pliku wyłącza ruch bez zmiany kodu).

Antena jest też **przedmiotem** (CMD 4004, `heavy_core` → zwykły model `raft:item/antena_fallback`,
bez zależności od modu), tą samą drogą co odbiornik.

**Kabel to geometria w kodzie moda (`DeviceCables`)**, nie display-encje ani bloki — rysowana
w pozie modelu odbiornika, więc płynie z tratwą bez kosztu i przechodzi przez ściany. Kotwice
(gniazdo/wtyk/pokład w px) siedzą w `DeviceAnchors`, a kontrolę matematyki daje `/rafthud kable`.
Wpis `raft:receivers` ma 28 B (link anteny) — mod i plugin muszą iść w parze. Wyspy pokazuje
**wyłącznie lore w GUI urządzenia** (czat wykluczony — `AGENTS.md` §1).

## Żagiel — model, fizyka, stawianie

**Miejsce w paczce przygotowane 2026-09-24, model jeszcze nie istnieje.** Wpis w
`heavy_core.json` (zwykły model `raft:item/zagiel_fallback` — placeholder, działa bez modu;
stan 2026-10-01), model bazowy `raft:item/zagiel_base` (bez elementów, display skopiowane
z anteny — skalę korygować po powstaniu modelu), stub siatki `rafthud/zagiel.json` (płótno 1 px
grubości jako dwie ściany north/south — do zastąpienia eksportem z Blockbencha) i stub animacji
`zagiel.anim.json` (tor `plotno`/`rotation x` trzyma 0; docelowo obrót wokół zawiasu przy pokładzie
= rozwijanie, sterowany stanem z serwera jak anteny w `raft:receivers`). Docelowe grupy modelu:
`zagiel` (rodzic, origin 8,0,8) i `plotno` (dziecko). W `ItemModelCheck` wpis z flagą
`finished=false` (brak tekstur = UWAGA); po dorobieniu modelu i tekstur
(`raft:block/zagiel_plotno.png` — plik zastępczy już leży) przestawić na `true`. Tekstura
zastępcza: jednolita beżowa 16×16. Stare CMD 8003/8004 (maszt/żagiel waniliowo) usunięte
2026-09-25 — zastąpił je przedmiot `zagiel` (CMD 4005, `HEAVY_CORE`) w `ItemManager`, a jego
geometrię rysuje mod z paczki.

**Fizyka (2026-09-25): żagiel rozwinięty = napęd z wiatru, zwinięty = z prądu wody.**
`RaftState.isSailing()` = komponent `SAIL` ORAZ flaga `sailOpen`; wtedy
`RaftPhysicsEngine.computeDrive()` oddaje napęd z `globalWind × windForceScale × sailEfficiency`,
a bez żagla z `globalCurrent × currentForceScale` (bez dodawania obu sił naraz, jak wcześniej).
`RaftManager` liczy prawdziwy wiatr (losowy kierunek, prędkość 1.4–1.9 bl/s, zmiana co 2 min
niezależnie od prądu co 8 min) i co tick podaje go silnikowi; `windForceScale` = 1.0, bo wiatr
jest wektorem prędkości docelowej (bl/s), tak samo jak prąd. Kolizja ześlizgowa
(`RaftEntity.stepDrift`) działa na `physicsEngine.getDriveVector()` (kierunek napędu) i zadaje
ześlizg przez `setDriveOverride` (jednorazowo — zerowane po kroku fizyki), więc tratwa ślizga się
po brzegu tak samo pod żaglem, jak na prądzie; `getDirection()` (pole śmieci przed dziobem,
`/raft debug`) pokazuje kierunek napędu. Stan żagla przechodzi przez zapis tratwy (`sail-open`
w `raft-saves.yml`, pole nieobecne = zwinięty) i jest widoczny w `/raft debug`
(żagiel / wiatr / napęd). Do testów: `/raft zagiel <otwarty|zamkniety>` (bez argumentu przełącza;
rozwinięcie tratwy bez żagla stawia go na środkowej komórce pokładu — normalnie robi to PPM
przedmiotem).

**Stawianie z przedmiotu (2026-09-25, wzorzec anteny).** Item `zagiel` (CMD 4005, `HEAVY_CORE`,
`/raft przedmiot zagiel`) + `SailPlaceListener`:

- PPM w POKŁAD tratwy (komórka RAFT/FLOOR, raycastem `RaftBuildListener.raycastCell`) →
  komponenty `MAST` + `SAIL` na tej komórce (`RaftEntity.mountSail`, Y = 1.0 / 1.5 w konwencji
  komponentów, pokład = 0.5), przedmiot zużyty, od razu `RaftPersistence.save`,
- PPM w KOMÓRKĘ stojącego żagla (dowolnym przedmiotem albo pustą ręką) → rozwiń/zwiń
  (napęd z wiatru ↔ z prądu). Obsługa klikiem w żagiel, a nie trzymanym przedmiotem — inaczej
  postawienie żagla ostatnią sztuką zostawiłoby gracza bez sposobu, żeby go rozwinąć. Komórkę
  żagla rozpoznajemy po komponencie SAIL (`RaftEntity.getSailCell`) i raycaście komórek, więc
  model geometrii nie jest do tego potrzebny,
- Shift + PPM przedmiotem „żagiel” → `demountSail()` (komponenty znikają, bez zwrotu przedmiotu —
  jak przy rozbiórce komórek siekierą),
- jeden żagiel na tratwę (komponenty w `RaftState` są kluczowane nazwą),
- `RaftManager.getNearestRaft` pomija tratwy z innego świata (wcześniej `distance()` między
  światami rzucało wyjątek — dotyczyło też budowania i skrzyń).

Zero komunikatów na czacie (`AGENTS.md` §1) — stan widać w `/raft debug`.

## Komórki i skrzynie NA tratwie (2026-10-05)

Dwie pułapki, które razem blokowały budowanie fundamentów mimo zielonego ducha i przedmiotów
w ekwipunku:

- **`PlayerInteractEvent.isCancelled()` kłamie dla PPM w powietrze.** `isCancelled()` to
  `useInteractedBlock() == DENY`, a konstruktor ustawia
  `useClickedBlock = (block == null ? DENY : ALLOW)` — dla `RIGHT_CLICK_AIR` (block == null)
  `isCancelled()` jest ZAWSZE `true`. Stare `if (event.isCancelled()) return;` zjadało każdy PPM
  w powietrze (i w wodę). Poprawny wzorzec (jak w `RaftHammerListener`): sprawdzać rozłącznie
  `useInteractedBlock()` (klik w blok) i `useItemInHand()` (powietrze) — helper
  `RaftBuildListener.isClickConsumed(PlayerInteractEvent)` używany w `RaftBuildListener`
  i `RaftBuildPreview`. `setCancelled(true)` ustawia oba na DENY, `setCancelled(false)` tylko
  wraca z DENY do DEFAULT (nie zdejmie cudzego anulowania). Zmierzone refleksyjnie
  (`/tmp/dur-test/ClickTest`): `RIGHT_CLICK_AIR` → `isCancelled=true`, `useItemInHand=DEFAULT`,
  `helper=false`; po `setCancelled(true)` → `helper=true`.
- **Pokład tratwy to encje, nie bloki.** Shulker-kolizje (`TAG_COLLIDER`, środek −0.5 od komórki)
  i BlockDisplay (`TAG_VISUAL`) sprawiają, że z bliska klient wysyła `PlayerInteractEntityEvent`,
  nie PPM w powietrze — listenery oparte wyłącznie o `PlayerInteractEvent` milczały z bliska.
  Ścieżka encji (dopasowanie encja→komórka jak w `CollectionNetListener`, collider Y −0.5):
  `RaftBuildListener.onInteractEntity` (stawianie przedmiotem — młot pomijany),
  `RaftBuildPreview.onInteractEntity` (PPM młotem = potwierdzenie ducha bez shiftu) +
  `onEntityDamage` (LPM w kolizję = anulowanie ducha), `RaftHammerListener.onInteractEntity`
  (shift+PPM = GUI młota), `RaftChestListener.onInteractEntity` (otwieranie skrzyni);
  `CollectionNetListener` pomija kliki z przedmiotem budowlanym w ręce (tryb budowania,
  nie GUI sieci).
- **Skrzynie stoją NA pokładzie, nie wzdłuż tratwy.** To komórka w wierszu pokładu
  (deski/podłoga), a nie nowa komórka-przedłużenie: w `computePlacement` cel skrzyni to trafiona
  komórka `+ (0, 1, 0)` i wymaganie `below == RAFT || FLOOR`. Y-offsety komórek: pokład = 0,
  skrzynia = 1; konsumenci `getBlockOffsets()` (renderer, persistencja, mod `RaftOffsetStorage`,
  siekiera, sieć, śmieci, antena) używają `pos + offset`, więc działa bez zmian. Wdrożone jako
  build `v34`.

## Hak a niewidzialne kolizje tratwy (2026-10-05, build `v35`)

Waniliowy spławik przypina się do pierwszej żywej encji z hitboxem na drodze
(`FishingHook.canHitEntity` = żywe encje + przedmioty), a tratwa jest CAŁA zbudowana
z niewidzialnych shulkerów-kolizji — niski rzut trafiał w taki hitbox, wanilia zerowała prędkość
i snapowała spławik do jego środka, więc hak wisiał „zahaczony o niewidzialną ścianę”
(wysoki rzut, ponad hitboxy, leciał normalnie).

`HookManager.releaseRaftAttach` odczepia teraz spławik od każdej encji tratwy
(`RaftEntity.isRaftEntity`) od razu po zaczepie: `setHookedEntity(null)` (wanilia wraca do
FLYING), przywrócenie ostatniej prędkości lotu (`lastVel`) i krok 0.35 bl wzdłuż lotu (żeby nie
zaczepił się o tę samą kolizję co tick) — hak leci swobodnie jak wanilia, aż wleci do wody.
Encje spoza tratwy (moby, przedmioty) zostają waniliowe.

Przy okazji naprawiony pomiar spoczynku (`updateSettle`): liczy prawdziwą zmianę pozycji między
tickami (`prevPos`), bo stary porównywał pozycję z samą sobą — prędkość wychodziła zawsze 0
i „spoczynek” łapał spławik jeszcze w locie.

## GUI skrzyni tratwy (2026-10-05, build `v36`/`v37`)

Klikanie jest W PEŁNI WANILIOWE — nic nie anulujemy i nie przenosimy przedmiotów za gracza:
klik bierze stack na kursor, klik w slot go odkłada, działają drag, swap i shift-klik. Wcześniej
każde kliknięcie było anulowane, a listener robił własny transfer „od razu do ekwipunku/skrzynki”,
więc przy PUSTYM kursorze przedmiot wpadał do ekwipunku sam (jak shift-klik), a przy zajętym
kursorze nie działo się nic.

Stan GUI jest źródłem prawdy sesji: zadanie lustra (`RaftChestListener.start()`, co 2 ticki)
przenosi go do `RaftEntity.setChestContents` (wpisy gęste — null/air pomijane, stacki większe niż
max stack dzielone, limit wpisów = sloty typu) i odświeża GUI innych graczy otwarte na tej samej
skrzynce; zamknięcie GUI robi ostatni zapis, a zniknięcie skrzynki zamyka okno.

Od `v37` jedna skrzynka = JEDEN współdzielony kontener (`RaftChestListener.sharedGuis`, klucz
`raftId|komórka`, wypisywany gdy nikt nie patrzy — `pruneShared`): wszyscy widzowie klikają w tę
samą tablicę slotów, więc wanilia sama rozsyła zmiany i dwóch graczy nie może wyjąć tego samego
przedmiotu w tej samej chwili. `refreshOthers` został jako zapas (no-op przy współdzielonym
kontenerze).

## Render żagla na tratwie — kanał `raft:sails` (2026-09-25)

Żagiel NIE jest BER-em jak odbiornik/antena: tamten tor rysuje tylko na bloku-nośniku (beczka
z BlockEntity), a tratwa PŁYWA — beczka na pokładzie to blok w ciele gracza i ryzyko uduszenia
shulkerów-kolizji. Pozycja idzie wprost z pakietu do renderera świata:

- serwer `RaftSailPacketSender` (`raft:sails`, co 2 ticki = 10 Hz, gracze w świecie Raft
  w zasięgu 128 bl od pokładu, tratwy bez żagla pomijane) wysyła PEŁNĄ listę: `int count`
  + per wpis 48 B = `long uuidMost, long uuidLeast` (klucz: UUID tratwy — tratwa dryfuje, więc
  klient musi dopasować wpisy między pakietami), `double x/y/z` (światowa PODSTAWA MASZTU,
  `RaftEntity.getSailAnchorWorld()`), `float yaw` (obrót tratwy), `int flags` (bit 0 = rozwinięty),
- klient `RaftSailsPayload` + `RaftSailStorage` (podmiana całości, wygładzanie pozycji i czasu
  animacji) + `RaftSailWorldRenderer` na `LevelRenderEvents.AFTER_SOLID_FEATURES` — w Fabric
  0.161/26.3 `WorldRenderEvents` już NIE istnieje, jest nowy tor `LevelRenderEvents`
  z `SubmitNodeCollector`, a `AFTER_SOLID_FEATURES` niesie `LevelRenderContext`
  (`submitNodeCollector()`, `poseStack()`),
- geometrię rysuje ten sam `DevicePartRenderer` co urządzenia i przedmiot w ręce, więc model
  i `<model>.anim.json` z paczki działają identycznie; czas animacji = 0 (zwinięty) albo długość
  pętli z pliku animacji (rozwinięty), więc rozwijanie jest przejściem — artysta opisuje je
  klatkami od 0 do `length`, a `RaftSailStorage` wygładza je w czasie,
- pose poziomu jest WZGLĘDEM KAMERY (`Camera.position()`, jak gizmos debugowe), a kotwicą modelu
  jest podstawa masztu: model powinien mieć origin w środku kolumny masztu (grupa `zagiel`,
  origin 8,0,8 — jak w stubie).

**NIE SPRAWDZONE W GRZE:** tor rysowania i konwencja pose (`AFTER_SOLID_FEATURES` +
`submitCustomGeometry`) są odczytane z bajtkodu 26.3 i API Fabric 0.161, a nie widziane oczyma —
jeśli żagiel wyląduje w dziwnym miejscu (np. przy zerze świata), najpierw sprawdzić, czy pose
poziomu naprawdę jest camera-relative. Siatka i animacja w paczce to nadal stub, więc
„rozwijanie” nie ma jeszcze czego animować (długość pętli = 0).

Pełny opis kanału i toru rysowania: `docs/ZAGIEL_RENDER.md`.

## Fallback żagla dla graczy BEZ moda (2026-10-02)

Serwer dokłada w świecie dwie display-encje na TEJ SAMEJ kotwicy/yaw/rozwinięciu, co pakiet
`raft:sails` (`RaftEntity.getSailAnchorWorld()`, `getYawDegrees()`, `isSailOpen()`):

- `ItemDisplay` — MASZT (`STRIPPED_OAK_LOG` przeskalowany do słupa 0,25 × 2,5 bloka),
- `BlockDisplay` — PŁÓTNO (cienka płyta `WHITE_WOOL` 1,4 × 1,9 × 0,0625, środek 1,3 bloka nad
  pokładem; zwinięte = wąska rolka, przejście interpolowane 4 ticki).

Proporcje: model paczki `zagiel_fallback` przeskalowany ×2,5, grubość płótna 1 px jak w siatce
moda. Kotwica = GÓRNA POWIERZCHNIA DESKI: `MAST_BASE_Y` poprawione 1.0 → 0.5 (konwencja
komponentów: pokład = 0.5), bo mesh moda kotwiczy tam origin grupy `zagiel` (8,0,8), a pivot
animacji rozwijania leży „przy pokładzie” — stała porusza jednocześnie mesh i fallback, a stare
zapisy tratw są wyrównywane przy wczytaniu.

Encje startują z `setVisibleByDefault(false)`, a pokazujemy je WYŁĄCZNIE graczom bez moda (mod
wykrywany po `KeyStateReceiver.has(uuid)` + `getListeningPluginChannels()` z kanałami `raft:*` —
gracz z modem ma mesh klienta i nie może zobaczyć podwójnego żagla). Cykl życia jak deski tratwy:
sync w `syncAllVisuals()`, cull razem z deskami w FROZEN, cleanup przy rozbiórce tratwy
(`RaftSailFallbackRenderer`).
