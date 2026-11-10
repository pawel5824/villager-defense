# Odbiornik + 3 anteny + kable — projekt mechaniki (zaakceptowany 2026-09-18)

Stan: **fazy 0–5 zrobione** (fazy 0–4: 2026-09-19, faza 5: 2026-09-23 — model anteny z Blockbencna).
Ten dokument jest specyfikacją —
przy implementacji trzymać się decyzji z sekcji „Decyzje" (są potwierdzone przez
właściciela, nie zmieniać ich „przy okazji").

### Stan fazy 5 (model anteny + przedmiot) — ZROBIONE (2026-09-23)

**Model** (`pack/assets/raft/rafthud/antena.json`, źródło Blockbencna: `antena.bbmodel` obok):
9 elementów, 54 ściany, **12×23×12 px** — mieści się w jednej komórce tratwy (x/z 2..14), więc
nie wystaje na sąsiednie bloki; w górę wychodzi poza komórkę (23 px = 1,44 bloku), co jest zgodne
z założeniem (renderer moda nie zna limitu −16..32 px).

| element | px | rola |
|---|---|---|
| `plyta` | 4..12 × 0..2 × 4..12 | podstawa na pokładzie (nity w narożnikach) |
| `skrzynka` | 6..10 × 2..5 × 6..10 | puszka z wtykiem kabla — kotwica `ANTENA_PLUG_PX = {8, 2, 8}` siedzi w jej dolnej ścianie |
| `maszt` | 7..9 × 5..17 × 7..9 | słup |
| `obejma` | 6..10 × 17..18 × 6..10 | opaska trzymająca ramiona |
| `poprzeczka` | 2..14 × 18..19 × 7..9 | pozioma belka |
| `pret_*` | środek/lewy/prawy, 19..23 | trzy pręty (środkowy 2×4×2, boczne 1×3×2) |
| `swiatelko` | 10..12 × 2..4 × 10..12 | czerwona lampka kontrolna na płycie |

**Tekstury — dwie, po 16×16 px** (generator `pack/tools/AntennaTextureGen.java`, jak kabel):
`raft:block/antena_metal` (stal: płyta/skrzynka/maszt/obejma/ramiona — jedno 16×16 pełni rolę
„tablicy próbek”: nity, szczelina wentylacyjna, miedziany oplot ramion) i `raft:block/antena_lamp`
(czerwony klosz, gradient promieniowy). **Dlaczego 16×16, a nie 64×64:** `DeviceMesh` dzieli UV
przez 16 i rozciąga [0,1] na **całą** teksturę (tak jak wanilia), więc przy PNG 16×16 jedna
jednostka UV = jeden piksel — UV wpisane w modelu (prostokąty o rozmiarach ścian) daje piksel
w piksel. Przy 64×64 każdy modelowy piksel zajmowałby 4 px tekstury i wyglądałby identycznie,
za to trudniej było trafić w region.

**Grupy (części animacji)** — `antena` → `podstawa` (pivot 8,0,8) + `swiatelko` (11,3,11)
+ `ramiona` (8,17,8, oś masztu). Ruch: `antena.anim.json` obraca `ramiona` o 360° na 240 ticków
(12 s na obrót — spokojny obchód horyzontu) i pulsuje `swiatelko` (skala 1 → 1,35 → 1, dwa razy
na pętlę). **Bez tego pliku antena jest statyczna** — ruch to dane, nie kod.

**Antena jest też przedmiotem:** item definition `heavy_core` wpis **CMD 4004** → od 2026-10-01
zwykły model `raft:item/antena_fallback` (placeholder bez modu; dawny wpis `minecraft:special`
z `raft:device` pokazywał u gracza bez modu szachownicę). Geometrię w świecie i tak rysuje mod
na siatce `raft:rafthud/antena`. W grze:
`/give @s minecraft:heavy_core[minecraft:custom_model_data=4004]`.

**Kotwice bez zmian:** kabel wchodzi w `ANTENA_PLUG_PX = {8, 2, 8}` (dolna ściana skrzynki) i tam
ginie — model nie przesunął tego punktu, więc faza 4 działa bez modyfikacji.

**Kontrola bez gry:** `tools/check-device-mesh.sh` (54 ściany, 0 braków tekstury, winding/UV 0 błędów)
i `tools/check-item-model.sh` — ten drugi został rozszerzony: sprawdza **oba** urządzenia-przedmioty
(4002 odbiornik, 4004 antena), istnienie **plików tekstur** użytych przez siatkę i brak ścian
`#missing`, a od 2026-09-23 także **UV w granicach sprite'a i o proporcjach ściany**. Oba modele są
skończone, więc brak tekstury jest wszędzie **błędem** (odbiornik dostał materiały z
`pack/tools/OdbiornikUvPaint.java` — szczegóły w `ODBIORNIK_RENDER.md`).

### Stan fazy 4 (kable + kind=1) — ZROBIONE

**Rodzaj kabla: geometria rysowana w kodzie przez mod (BER), nie display-encje ani bloki.**
Uzasadnienie (decyzja właściciela z projektu: „kabel rysuje wyłącznie mod"):
- kabel jest przywiązany do dwóch urządzeń, a tratwa płynie — geometria liczona w układzie
  **modelu odbiornika** jedzie z tratwą za darmo: obrót i pozycja wychodzą z tej samej pozy,
  którą renderer już ustawia dla modelu (`translate(0.5,0,0.5) · rotY(-yaw) · translate(-0.5,0,-0.5)
  · scale(1/16)`), bez przeliczania na serwerze i bez żadnej encji w świecie,
- kształt: **rurka o przekroju kwadratowym** (4 ściany na odcinek, promień 0.9 px ≈ 1/8 bloku),
  więc kabel wygląda jak kabel także z boku (płaska wstęga znikałaby pod kątem),
- trasa: gniazdo odbiornika → w dół na pokład → przebieg po pokładzie (8 odcinków z łukiem
  0.8 px, żeby nie było z-fightingu z deskami) → w górę do wtyku anteny,
- tekstura: `raft:textures/block/kabel.png` (16×16, spiralny oplot miedziany), generowana
  przez `pack/tools/CableTextureGen.java`; **leży też w jarze moda**, więc kabel działa
  nawet bez świeżej paczki,
- zero kolizji → świadome „przez ściany" wychodzi naturalnie; **bez fallbacku** dla graczy
  bez moda (decyzja właściciela: bez moda nie widać i tak niczego).

**Kotwice** (`DeviceAnchors`, piksele modelu — jedyne stałe do przesunięcia razem z modelem):
- `ODBIORNIK_SOCKET_PX = {16, 6, 16}` — jedno gniazdo, do którego wpadają wszystkie kable,
- `ANTENA_PLUG_PX = {8, 2, 8}` — wtyk na dole słupka anteny,
- `DECK_PX = 1` — poziom pokładu (y=0 modelu = dół nośnika = płaszczyzna pokładu),
- `modelToWorld`/`worldToModel` to para wzajemnie odwrotnych przekształceń (odwrotność pozy
  renderera). Kontrola bez uruchamiania gry: `/rafthud kable` wypisuje błąd obrotu
  z testu round-trip (oczekiwane 0.000000 px).

**Wysyłka (plugin, `RaftReceiversPacketSender`)**: anteny jako `kind=1` z yaw 0,
flagą „zasilona z odbiornika" i **linkiem** na nośnik odbiornika. Kontrakt wpisu urósł
z 20 do **28 bajtów** (`linkX, linkY, linkZ` na końcu) — mod liczy rozmiar wpisu ze stałej,
więc stary plugin (20 B) jest odrzucany w całości zamiast rozjechać odczyt. Antena bez linku
wysyła 0,0,0 i mod nie rysuje do niej kabla.

**Zmiany przy okazji:** antena nie ma już `BlockDisplay` z `LIGHTNING_ROD` (tymczasowy
znacznik z fazy 2) — wygląd robi model `assets/raft/rafthud/antena.json`, więc serwer nie
trzyma przy niej żadnej encji. Model anteny był tu wtedy **placeholderem** (8×16×8 px, 24 ściany,
tekstury `raft:block/raft_mast` + `raft:block/raft_plank`, grupa „antena" z pivotem [8,0,8]);
w fazie 5 zastąpił go finalny model z Blockbencna (patrz „Stan fazy 5" wyżej) — kotwica wtyku
została ta sama, więc kable nie wymagały żadnej zmiany.

**Podgląd bez serwera**: `/rafthud antena` stawia lokalny podgląd anteny (i sam szuka
najbliższego podglądu odbiornika ≤ 8 bloków, ten sam Y), `/rafthud kable` pokazuje liczniki
i błąd matematyki, `/rafthud reload` przeładowuje model po podmianie pliku w packu.

**Dostawa modelu**: plik leży w `pack/assets/raft/rafthud/` (edycja artysty) **oraz** w
`pack/src/main/resources/assets/raft/rafthud/` (domyślny w jarze — dzięki temu antena i kabel
działają od razu, bez publikacji paczki). Resource pack ma priorytet nad jar-em, więc podmiana
pliku w packu dalej działa bez przebudowy moda.

### Stan fazy 3 (gating wysp) — ZROBIONE

`DeviceGUI` (`pl.vd.raft.blocks`): „Mapa wysp" i „Kierunek do wysp" działają wyłącznie
gdy `DeviceBlock2x2.canSeeIslands()` (3/3 anten **i** bateria z energią):
- bez gate'u mapa to **szara ikona** (`Material.MAP`) z licznikiem w nazwie i lore
  („Anteny: X / 3", ile brakuje, „Włóż naładowaną baterię", instrukcja stawiania anten),
- z gate'em: lista do 5 najbliższych wysp w lore (`islandsByDistance`, sortowane po
  odległości od **odbiornika**), dystans w metrach + 8-stronny kierunek (`compassDirection`,
  `atan2` z 0° = północ = -Z), ✔ dla odkrytych, „…i N dalszych",
- klik w mapę = odświeżenie odległości **bez zamykania GUI**; przy zablokowanej mapie
  klik nie robi nic (nawet nie zamyka okna — zero komunikatów, AGENTS.md §1),
- kompas ma ten sam gate (celowanie w wyspę bez anten byłoby obejściem mechaniki),
  gate sprawdzany **przed** zamknięciem GUI,
- `renderItems(inventory, device)` = jedno miejsce renderowania slotów, używane przez
  `openGUI` i `refreshGUI` (po włożeniu/wyjęciu baterii licznik i gate odświeżają się),
- licznik „Anteny: X / 3" dodany też do „📊 Statystyki".

Spójność linków przy usuwaniu (dorzucone razem z fazą 3):
- `DeviceBlock2x2.remove()` odcina wszystkie podłączone anteny (każda dostaje
  `setLinkedReceiver(null)`, potem `AntennaManager.validate` przepina ją do innego
  odbiornika) — bez tego anteny wisiałyby na martwym obiekcie i nigdy się nie przepięły,
- **`/urzadzenie usun antena`** — anteny nie są typem w `DeviceRegistry`, więc komenda
  obsługuje je osobno (`AntennaManager.clearAll` + zapis + broadcast). To jedyny sposób
  zdjęcia anteny: nośnik-beczka jest chroniony przed zniszczeniem (faza 1), więc bez tej
  komendy postawionej anteny nie dałoby się ruszyć.

Pytanie otwarte z projektu rozstrzygnięte: **szara ikona z licznikiem** (nie zablokowany slot).
Wyspy są teraz pokazywane **tylko w GUI** — raport na czacie został usunięty wcześniej
(zasada „wszędzie cicho"), więc lore mapy jest jedynym źródłem odległości.

### Stan fazy 2 (antena) — ZROBIONE (bez modelu w modzie)

`AntennaDevice` + `AntennaManager` + `AntennaPlaceListener` (`pl.vd.raft.blocks`/`listeners`):
- przedmiot **„Antena"** (CMD 4004, `ItemManager.createAntena`), PPM w pokład tratwy stawia
  antenę i zużywa 1 sztukę (`isCustomItem(item, "antena")`, ręka główna),
- walidacja stawiania: kliknięty blok musi być komórką tratwy (FLOOR), pozycja wolna,
  najbliższy odbiornik z wolnym slotem w zasięgu **euklides XZ ≤ 8** i **ΔY == 0**
  (pytanie otwarte rozstrzygnięte: euklides, nie Chebyshev),
- link: `AntennaDevice.setLinkedReceiver` ↔ `DeviceBlock2x2.addAntenna/removeAntenna`
  (max 3 sloty, `MAX_ANTENNAS`), zerwanie **natychmiastowe**,
- `AntennaManager` waliduje co 2 s (40 ticków): martwy link (odbiornik zniknął / wyszedł
  z zasięgu albo poziomu) znika, antena próbuje się przepiąć na najbliższy wolny odbiornik,
- wygląd w świecie **bez modelu moda**: nośnik-beczka (jak odbiornik) + `BlockDisplay`
  z `LIGHTNING_ROD` (1,5 bloku wysokości, `setPersistent(false)`) — widoczne dla wszystkich,
  więc fazę 2 było widać w grze już wtedy (model z Blockbencna doszedł w fazie 5),
- persistencja: `DevicePersistence` zapisuje anteny w tym samym `devices.yml`
  (`type: antena`, `link: "x,y,z"`), a przy odczycie **linki są podpinane po wczytaniu
  wszystkich wpisów** (`linkJobs`) — odbiornik może być dalszy w pliku niż antena;
  zapis idzie też po postawieniu anteny (`AntennaPlaceListener`).

**Faza 4 domknęła to, czego tu brakowało:** anteny jadą w `raft:receivers` jako `kind=1`
(`DeviceModels.BY_KIND` ma teraz dwa wpisy), a wtyk anteny i gniazdo odbiornika łączy kabel
rysowany przez mod. Antena w grze = model z packa/jara, nie słupek z display-encji.

### Stan fazy 1 (nośnik + raft:receivers) — ZROBIONE

`RaftReceiversPacketSender` (`pl.vd.raft.net`):
- **nośnik**: `placeCarrierBlock`/`removeCarrierBlock` — beczka `facing=down, open=true`
  (jedyne niewidzialne w packu stany, docs/ODBIORNIK_RENDER.md §2). Wołane z
  `DeviceBlock2x2.spawn()`/`remove()` — idempotentne (nie nadpisuje istniejącej beczki;
  usuwa tylko własny stan, nie czyją beczkę),
- **wysyłka**: pełna lista co 1 s (`start()`, pętla jak statystyki) + natychmiast po
  `/urzadzenie postaw|usun`. Pętla buforuje join gracza — mod czyści listę przy JOIN,
  adresaci: gracze online w świecie Raft,
- **format** dokładnie wg kontraktu §5: count × (x,y,z int, yaw float, kind int, flags int);
  kind z klucza rejestru (`modul_odbiorczy` → 0), wiatrak na razie NIE jest wysyłany
  (nie ma modelu w `DeviceModels.BY_KIND`), flags bit 0 = „ma baterię z energią",
- **yaw → stopnie**: rotacja 0=South→180°, 1=West→90°, 2=North→0°, 3=East→270°
  (wyrównanie do układu Blockbencana; do weryfikacji wzrokowej w grze),
- getter `DeviceBlock2x2.getRotation()` zamiast refleksji.

Weryfikacja w grze: postaw odbiornik przez `/urzadzenie postaw modul_odbiorczy` —
gracz z modem (odbiornik 1.0.0 + model w packu) zobaczy duży model na pozycji urządzenia.
Jeśli model stoi tyłem/przodem do świata — poprawić mapowanie rotacji w `collectEntries`.

### Stan fazy 0 (persistencja urządzeń) — ZROBIONE

`DevicePersistence` (`pl.vd.raft.blocks`), plik `plugins/ServerPlugin/devices.yml`,
wzorzec 1:1 z `RaftPersistence`:
- **zapis** (pełny snapshot): `RaftPlugin.onDisable`, autosave co 1200 ticków (60 s,
  w `RaftManager`, razem z autosave tratw), po `/urzadzenie usun` (żeby usunięte
  urządzenie nie wskrzesiło się przy restarcie),
- **odczyt**: `RaftPlugin.onEnable` po `registerDefaultDevices()` (placeDevice wymaga
  zarejestrowanych typów), z ochroną przed duplikatami na tej samej pozycji,
- baterie: base64 `ItemStack.serializeAsBytes` (ten sam format co net-contents w
  raft-saves.yml), energia odtwarzana wprost przez nowe `restoreBattery` w
  `DeviceBlock2x2` (1 slot) i `WindmillDevice` (4 sloty, timer startuje gdy był pusty),
- typ urządzenia zapisywany **kluczem rejestru** (`modul_odbiorczy`/`wiatrak`), nie
  displayName (ten ma kody kolorów); rotacja derivowana z yaw w konstruktorze jak przy
  ręcznym stawianiu.

Uwaga na fazę 2 (anteny): pozycje anten i linki trafią do tego samego pliku
(sekcja `antenna:` / pole `link:`), gdy powstanie `AntennaDevice` — format jest
rozszerzalny bez migracji (licznik `version` jest już w wpisie).

## Cel

Odbiornik (istniejące urządzenie `modul_odbiorczy`) pokazuje wyspy dopiero, gdy gracz
postawi **3 anteny** wokół niego. Anteny łączą się z odbiornikiem **kablami** widocznymi
na podłodze (mogą przechodzić przez ściany — celowo, to geometria bez kolizji).
Kable zawsze wpadają w **jedno gniazdo** na modelu odbiornika, niezależnie gdzie stoi antena.

## Decyzje (potwierdzone przez właściciela)

1. **Stawianie anteny:** przedmiot w ręce + PPM w pokład tratwy (jak budowanie).
2. **Zasięg:** **8 bloków w poziomie** od odbiornika **i wyłącznie na tym samym poziomie Y**
   (ΔY = 0). Cel: gdy kiedyś powstanie piętro tratwy, nie da się anten „wystawić wyżej".
   Antena musi stać na podporze (reguły jak przy budowaniu).
3. **Zerwanie:** natychmiast. Zniszczenie anteny (albo wyjście z warunków) = odbiornik
   wraca do „2/3", wyspy znikają od razu. Bez karencji.
4. **Kable rysuje wyłącznie mod** (BER), brak fallbacku dla graczy bez moda — bez modu
   i tak nie widać odbiornika ani anten (modele renderowane przez mod na nośnikach-beczkach).
5. **Antena nie ma własnej baterii** — zasilanie bierze z odbiornika (wymaga włożonej
   baterii, jak dziś).
6. **Zero komunikatów** (AGENTS.md §1): stan „X/3" tylko w lore przedmiotu w GUI,
   zerwanie bez tekstu i bez dźwięku-informacji.

## Zasada działania (perspektywa gracza)

1. Stawia odbiornik (jak dziś). „Mapa wysp" w GUI: „Wymaga anten (0/3)".
2. Trzyma przedmiot „Antena", PPM w pokład ≤ 8 bloków od odbiornika i na tym samym Y →
   antena staje, sama łączy się z najbliższym odbiornikiem z wolnym slotem. Kabel rysuje się.
3. Przy 3/3 „Mapa wysp" działa (odległości, kompas — kod już jest w `DeviceGUI`).
4. Zniszczenie anteny → natychmiast „2/3", wyspy znikają.

## Architektura (co już jest, co brakuje)

```
plugin (Paper)                                  mod (Fabric, client)
─────────────                                   ───────────────────
persistencja urządzeń (NOWE, faza 0)            raft:receivers — odbiór JEST
nośnik-beczka pod odbiornik (NOWE, faza 1)      RaftReceiverStorage — JEST
wysyłka raft:receivers (NOWE, faza 1)           DeviceBlockEntityRenderer — JEST
AntenaDevice + linki (NOWE, faza 2)             kable w BER (NOWE, faza 4)
gating wysp 3/3 (NOWE, faza 3)                  model anteny (NOWE, faza 5)
```

Kluczowe fakty z kodu (stan 2026-09-18):
- `DeviceRegistry` trzyma urządzenia **tylko w pamięci** — `onDisable` zapisuje wyłącznie
  tratwy (`RaftPersistence`). Urządzenia giną przy restarcie. Faza 0 naprawia to **zanim**
  dołożymy anteny (inaczej linki i tak przepadałyby co restart).
- Payload `raft:receivers`, `RaftReceiverStorage` i renderer odbiornika **już istnieją**
  po stronie moda; brakuje strony pluginu („następny krok" z `docs/ODBIORNIK_RENDER.md`).
- Kabel podąża za ruchem tratwy **za darmo**: końce kabla = pozycja bloku nośnika +
  sub-pikselowy offset z `raft:offsets`, które mod już dostaje co klatkę.
- Sub-pozycje urządzeń serwer nie wysyła osobno — kadencja pakietu „na zmianę stanu",
  zgodnie z filozofią `RaftReceiverStorage` („pakiet zawsze pełny").

## Format danych

`raft:receivers` — rozszerzenie obecnego formatu (nie zmieniać tego, co klient już czyta):

```
int count
count × {
  int x, y, z      // pozycja bloku-nośnika (beczka)
  float yaw
  int kind         // 0 = odbiornik, 1 = antena
  int flags        // bity: 0=włączony
  int linkX, linkY, linkZ  // tylko antena: pozycja nośnika odbiornika (inaczej 0,0,0)
}
```

Persistencja (faza 0): `plugins/VillagerDefense/raft/devices.yml`:

```yaml
devices:
  - kind: odbiornik
    x: 100; y: 64; z: -30; yaw: 90; energy: 740; battery: <ItemStack base64>
    antennas: ["100,64,-22", "92,64,-30", "100,64,-38"]   # pozycje nośników anten
  - kind: antena
    x: 100; y: 64; z: -22; yaw: 180; link: "100,64,-30"
```

## Reguły linku (serwer, walidacja co ~2 s)

- Antena ma link tylko, gdy: nośnik odbiornika istnieje, **euklides XZ ≤ 8**
  (`AntennaManager.MAX_RANGE_XZ`, zaimplementowane), **ΔY == 0**.
- Antena bez linka próbuje się przepiąć na najbliższy odbiornik z wolnym slotem
  (max 3 anteny na odbiornik) spełniający warunki — automatycznie, po cichu.
- Odbiornik „działa" (wyspy) gdy: ma baterię z energią **i** dokładnie 3 ważne linki.

## Fazy implementacji (każda kończy się testowalnym efektem)

| Faza | Zakres | Efekt |
|---|---|---|
| 0 | Persistencja urządzeń (`devices.yml`, save/load, `onEnable`/`onDisable`) | urządzenia przeżywają restart — dziś giną |
| 1 | Plugin stawia nośnik-beczkę + wysyła `raft:receivers` | odbiornik pierwszy raz widoczny w grze |
| 2 | Przedmiot „Antena" + `AntennaDevice` (nośnik, `kind=1`), stawianie PPM, zasięg 8/XZ + ΔY=0, linki, walidacja, zapis | anteny stają, łączą się, przeżywają restart |
| 3 | Gating wysp: „Mapa wysp" działa przy 3/3, licznik w lore | zysk z mechaniki odczuwalny |
| 4 | Kable w BER: kotwice (gniazdo odbiornika + podstawa anteny), polilinia antena→pokład→gniazdo | kable widoczne, płyną z tratwą, przez ściany |
| 5 | Model anteny w Blockbencnu + item definition (model-editor) + tekstury | wygląd finalny |

Uwaga do fazy 4: kotwice definiować **lokalnie względem nośnika** (piksele modelu), nie
współrzędnymi świata — wtedy rotacja urządzenia (yaw) i ruch tratwy działają bez przeliczeń.

## Pytania otwarte (rozstrzygnięte przy implementacji)

- Metryka zasięgu poziomego: **euklides XZ ≤ 8** (zaimplementowane w `AntennaManager`).
- Co widzi gracz bez 3/3 w GUI „Mapy wysp": **szara ikona z licznikiem** (zaimplementowane).
- Format kotwic kabli w modelu (piksele lokalne) — rozstrzygnięty w fazie 4, kotwice bez zmian
  przeszły do finalnego modelu z fazy 5.
- Model anteny — zrobiony w fazie 5 (2026-09-23), w tym przedmiot pod CMD 4004 i animacja.
