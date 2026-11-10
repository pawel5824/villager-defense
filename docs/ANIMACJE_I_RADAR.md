# Animacje urządzeń i ekran radaru — plan (punkt 7)

Dokument opisuje **podział pracy**: co dokładnie robi edytor modeli, a co trzeba dopisać
w modzie, żeby animacja i radar działały w grze. Pisany po analizie kodu, który już jest
(`pack/src/main/java/com/raft/hud/device/`) i po sprawdzeniu API klienta **26.3**, na którym
buduje się Wasz mod — nie z pamięci. (Fakty zbierane pierwotnie na 26.2; przy przejściu na
26.3 sygnatury klas, których dotyka mod i jego mixiny, sprawdzone ponownie jako identyczne.)

Stan wyjściowy: renderer dużych urządzeń już działa (`DeviceBlockEntityRenderer`), ale rysuje
**statyczną bryłę** — `DeviceMesh` spłaszcza model do listy materiałów i kwadrów, bez hierarchii
i bez czasu. Animacja i ekran radaru to dwie rzeczy, których w pliku JSON modelu nie da się
zapisać: jedna potrzebuje ruchu części, druga podmiany tekstury co tick.

---

## Szybki start — jak zrobić animację (6 kroków)

Wszystko jest już zrobione po stronie kodu. Żeby zrobić animację, robisz tylko to:

1. **Blockbench** → ustaw **grupy** i każdej ustaw **origin** w miejscu, wokół którego ma się obracać
   (np. antenka: origin w jej nasadzie, nie w środku modelu). Zapisz/eksportuj model Java do packa.
2. **Edytor modeli** → wgraj ten model (przycisk „Wgraj .bbmodel / .json”).
3. Otwórz panel **„7 · Animacje”**, sprawdź na dole listę części — mają tam być twoje grupy.
4. **Dodaj tor** (albo użyj presetu „obrót części” / „miganie”): część, kanał (obrót/pozycja/skala),
   oś, i klatki w **tickach** (20 = 1 s). Kliknij „Odtwórz”, żeby zobaczyć ruch.
5. **„Zapisz do folderu packa”** (albo „Eksport anim.json”) → plik `…/<model>.anim.json` obok modelu.
6. Jeśli ten sam model ma być **przedmiotem**, w tym samym panelu 7 wypełnij sekcję
   **„Item definition animowanego przedmiotu”** — id przedmiotu, CMD, model bazowy — i kliknij
   **„Zapisz do folderu packa”**. Edytor dopisze wpis `minecraft:special` → `raft:device`
   (uwaga: taki przedmiot wymaga modu — dla graczy bez modu podmienia się go na zwykły model,
   wzorzec `raft:item/odbiornik_fallback` w `heavy_core.json`),
   wygeneruje model bazowy (sam `display` ze sceny) i **scali** progi CMD z tym, co już leży w pliku.
7. W grze: `F3+T` lub `/rafthud reload`, potem `/rafthud odbiornik` na beczce, a na przedmiocie
   `/give @s minecraft:heavy_core[minecraft:custom_model_data=4002]` (§3.8).

Ten sam plik animacji napędza więc i blok w świecie, i **przedmiot w ręce oraz w ekwipunku** —
bez osobnego eksportu i bez ręcznego pisania JSON-a.

Warunki, bez których nie zobaczysz ruchu:

- **opublikowany nowy `raft-hud-1.0.0.jar`** — gracze z modpacka biorą moda z wydania na GitHubie,
  więc bez tego kroku starszy JAR nie zna animacji,
- `packwiz refresh` w `modpack/` — żeby plik `.anim.json` trafił do graczy,
- grupa w modelu musi mieć sensowny **origin**, inaczej część kręci się wokół środka bryły.

Kontrola bez gry: `tools/check-device-mesh.sh` — pokaże części z pivotami, tory, zakres ruchu,
czy pętla się domyka, i czy animacja nie celuje w nieistniejącą część.

---

## 0. Stan wdrożenia

**Zrobione — ruch części, cała ścieżka od projektu do gry:**

| element | gdzie |
|---|---|
| oś czasu z klatkami kluczowymi, podgląd w scenie, eksport | `model-editor.html` → panel **„7 · Animacje”** |
| części z pivotami (grupy modelu) | `DeviceMesh.Part` — model bez grup daje jedną część „cały model” |
| czytanie i próbkowanie animacji | `DeviceAnimations` + `DeviceAnimationCache` |
| zegar i stan w render state | `DeviceRenderState` + `extractRenderState` |
| rysowanie części z obrotem wokół pivota | `DeviceBlockEntityRenderer.drawPart` |
| podglądowy zegar w grze | `/rafthud anim on\|off\|info\|time &lt;t&gt;\|speed &lt;x&gt;` |
| test bezgłowy (części + animacja + zgodność z edytorem) | `tools/check-device-mesh.sh` |
| przykładowa animacja w packu | `assets/raft/rafthud/odbiornik.anim.json` |
| **ten sam model i ta sama animacja jako przedmiot** (ręka, ekwipunek, ramka) | `DeviceSpecialRenderer` + typ `raft:device` (wymaga modu; przedmioty bez modu mają zwykłe modele `*_fallback`) |
| drugie urządzenie z ruchem: **antena** (3 części, obrót ramion + puls lampki, przedmiot CMD 4004) | `assets/raft/rafthud/antena.json` + `antena.anim.json` (faza 5 projektu anten) |
| przedmiot w ekwipunku odświeżany co klatkę | `SpecialModelWrapperMixin` (`setAnimated`) |
| podpięcie przedmiotu w packu | `assets/minecraft/items/heavy_core.json` (CMD 4002); od 2026-10-01 zwykły model `raft:item/odbiornik_fallback`, bez modu |
| generowanie tego podpięcia bez ręcznego JSON-a | `model-editor.html` → panel 7, „Item definition animowanego przedmiotu” (+ scalanie progów CMD) |
| test podpięcia przedmiotu bez gry | `tools/check-item-model.sh` |

**Zostaje:** ekran radaru jako dynamiczna tekstura (§3.4) oraz dane `blips/pulse` z pluginu (§3.5).

**Ustalenie z autorem (stan decyzji):** radio i odbiornik to dwie różne rzeczy — **radio** jest
elementem dekoracyjnym i **nie ma mieć animacji**, a **odbiornik** będzie blokiem fabularnym
(kieruje gracza na wyspy) i jego animacja jest jeszcze przedmiotem decyzji. Dlatego mechanizm
(§3.8) jest gotowy i podpięty pod odbiornik, a nie pod radio.

**Odkrycie, które uprościło plan:** Wasz `odbiornik.json` **już ma nazwane grupy z pivotami**
(`stolik`, `ekran`, `antenka`, `bateria`, `licznik`), więc części bierzemy z formatu Java, który mod
i tak czyta. Nie trzeba przechodzić na geometrię Bedrock, robić bliźniaczego projektu ani
wprowadzać GeckoLib. Praktyczna konsekwencja: **model Java/Item nie ma w Blockbenchu zakładki
Animate**, więc oś czasu w edytorze jest jedynym miejscem, gdzie ten ruch da się zaprojektować.
Blockbench zostaje od geometrii i grup.

---

## 1. Podział odpowiedzialności

| Rzecz | Edytor modeli (przeglądarka) | Mod (kod Javy) |
|---|---|---|
| Klatki kluczowe ruchu | projektuje, odtwarza w scenie, eksportuje dane | czyta dane, liczy interpolację, rysuje |
| Podgląd animacji na modelu | tak (widzisz ruch zanim powstanie kod) | — |
| Wygląd ekranu radaru | projektuje na canvasie, widzi na modelu | rysuje w klatce, aktualizuje co tick |
| Części modelu (pivoty) | nic — wynikają z grup w Blockbenchu | `DeviceMesh` musi je zachować |
| Czas | tylko podgląd | zegar klienta + `partialTick` |
| Dane radaru (gdzie wyspy) | nic | z sieci/logiki; edytor nie ma skąd ich wziąć |
| Kolejność przezroczystości, światło, migotanie | **nie sprawdzi** | rozstrzyga kod |
| Przedmiot w ręce | display w JSON | osobna ścieżka (`SpecialModelRenderer`) |

Wniosek: **edytor projektuje, mod wykonuje.** Punkt 7 nie kończy się plikami — zawsze zostaje kod
(jedyna różnica względem punktu 9, gdzie pliki wystarczają).

---

## 2. Część A — co robi edytor (`model-editor.html`)

Nowy panel **„7 · Animacje i ekran radaru"** (obecnie panele kończą się na 6 · Generator przedmiotu).

### 2.1 Oś czasu z klatkami kluczowymi

- **Kanały** na wybraną część modelu: obrót X/Y/Z, przesunięcie X/Y/Z, skala, widoczność,
  przezroczystość (alfa). Części = grupy z `.bbmodel`/`groups` w JSON (te same, które mod
  odczyta), więc nazwy są wspólne między edytorem a kodem.
- **Klatki kluczowe**: czas w **tickach** (20 = 1 s), wartość, interpolacja (`lin`, `step`,
  `ease-in/out`) — tick, a nie sekunda, bo tak liczy gra; bez tego faza animacji rozjeżdża się
  przy zmianie TPS.
- **Podgląd**: play/pauza, pętla, suwak czasu, prędkość 0,25–2×, scrubbing po osi.
- **Presety**: „obrót antenki" (0 → 360 w N tickach), „miganie diody" (widoczność step co 10 t),
  „wskaźnik" (skala z easingiem), „drgnięcie przy starcie".
- **Walidacja**: klatki poza zakresem, kanał bez klatek, dwa kanały na tę samą oś, część bez
  pivota (wtedy obrót wokół (0,0,0) — najczęstsze źródło „model ucieka").

### 2.2 Ekran radaru (dynamiczna tekstura)

- **Canvas** (64×64 albo 128×128, do wyboru) z warstwami: tło, okrąg, siatka, kropki wysp,
  obracający się promień, poświata, znacznik kursora.
- **Parametry**: promień, liczba kropek, tempo obrotu (obr./min → ticki), grubość promienia,
  zasięg, paleta (zielony/czerwony/amber), kolor tła.
- **Podgląd na modelu**: tekstura slotu wskazanego na ekran jest w scenie podmieniana na canvas
  i **animowana w czasie rzeczywistym** — widzisz radar obracający się na bryle.
- **Podgląd danych**: edytor nie zna prawdziwych wysp, więc kropki są **próbką** (losowe lub
  wpisane ręcznie w tabelce). To podgląd wyglądu, nie logiki.

### 2.3 Eksport

| plik | zawartość |
|---|---|
| `assets/<ns>/rafthud/<model>.anim.json` | kanały, klatki, interpolacja, pętla, identyfikatory części |
| `assets/<ns>/rafthud/<model>.ekran.json` | parametry ekranu (rozmiar, promień, tempo, paleta) |
| `assets/<ns>/textures/rafthud/ekran.png` | placeholder 1:1 z canvasu — żeby model dało się obejrzeć bez moda |
| `anim_szkielet.java.txt` | szkielet klasy renderera z gotowymi stałymi (do wklejenia) |

Eksport idzie tym samym przyciskiem co „Zbierz paczkę" z punktu 9, więc pliki trafiają do ZIP-a
albo prosto do folderu packa.

---

## 3. Część B — co trzeba dopisać w modzie

Poniżej fakty sprawdzone w kliencie (cache Gradle, `minecraft-client.jar`; pierwotnie 26.2,
przy migracji potwierdzone na 26.3).

### 3.1 Zegar animacji — dlaczego nie w `submit`

```
BlockEntityRenderer<T,S>:
  S createRenderState()
  void extractRenderState(T be, S state, float partialTick, Vec3 cameraPos, CrumblingOverlay)   // default
  void submit(S state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera)
```

**`submit` nie dostaje `partialTick`.** To decyduje o architekturze: czas i stan animacji muszą
być **włożone do render state** w `extractRenderState`, a `submit` tylko je czyta.

- `DeviceRenderState extends BlockEntityRenderState` — pola: `float time` (ticki + partialTick),
  `boolean on`, `float yaw`, `float distance`, `int flags`.
- Zegar: `level.getGameTime() + partialTick` (spójny dla wszystkich graczy — animacja nie zależy
  od tego, kto kiedy wszedł), plus stały offset z pozycji bloku, żeby sąsiednie urządzenia nie
  ruszały się idealnie w tej samej fazie.
- `BlockEntityRenderState` ma tylko `blockPos`, `blockState`, `blockEntityType`, `lightCoords` —
  więc podklasa jest konieczna, nie opcjonalna.

### 3.2 Części w meshu (zmiana rdzenia)

Dziś `DeviceMesh.parse` zwraca `materials → quads` (wszystko wypieczone, bez hierarchii). Do ruchu
części trzeba:

- nowy typ `Part(String name, float[] pivot, Map<String, float[]> staticRotation, List<Material>)`,
- `DeviceMesh` czyta `groups`/`children` z JSON-a (nazwy z Blockbencana) i przypisuje elementy do
  części, zachowując **pivot = `rotation.origin`** elementu (już czytany dla obrotu statycznego),
- części bez nazwy → jedna część `root`, żeby stare modele działały bez zmian.

**Ochrona przed regresją:** rozszerzyć `tools/check-device-mesh.sh` (bezgłowy test
`tools/DeviceMeshCheck.java`) o porównanie „przed/po": liczba kwadrów, sumy pozycji i UV muszą
być identyczne dla modeli bez animacji. Ten test już dziś daje 0 problemów na `odbiornik` (912
ścian), `duza_beczka` (42) i `radio` (162) — musi tak zostać.

### 3.3 Parser animacji i rysowanie

- `DeviceAnimations.java` — czyta `<model>.anim.json` z `ResourceManager` (te same zasoby, co
  model, więc podmiana bez przebudowy moda), trzyma klatki jako `float[]`.
- Interpolacja: `lin` (lerp), `step` (bez interpolacji), `ease` (smoothstep) — trzydzieści linii,
  bez bibliotek.
- `DeviceBlockEntityRenderer.submit`: dla każdej części `pose.pushPose()`, obrót wokół pivota
  (`translate(pivot) → mulPose → translate(-pivot)`), `submitCustomGeometry`, `pose.popPose()`.
  Pivot w pikselach modelu, tak jak geometria (skala `1/16` już jest w pose).
- Cache animacji obok `DeviceMeshCache` (wczytanie raz, ostrzeżenia zamiast cichego braku ruchu).

### 3.4 Ekran radaru jako dynamiczna tekstura

Sprawdzone API:

```
DynamicTexture(Supplier<String> label, int w, int h, boolean clear)   // com.mojang.blaze3d.platform.NativeImage
  void upload();  NativeImage getPixels();
TextureManager.register(Identifier, AbstractTexture);  AbstractTexture getTexture(Identifier);
RenderTypes.entityCutout(Identifier)                   // tym już rysujemy materiały
```

Plan:
1. **Zarezerwowana nazwa tekstury ekranu**: `raft:rafthud/ekran`. Model z Blockbencana używa jej
   na ścianach ekranu — artysta nic więcej nie robi, a mod rozpoznaje materiał po tej nazwie
   (tak samo działa podmiana „brakującej" tekstury na kamień dziś).
2. `RadarTexture.java` — jedna `DynamicTexture` na ekran (nie na urządzenie), rozmiar z
   `.ekran.json`, aktualizacja **co tick** (`upload()`), nie co klatkę. Rysowanie: okrąg i promień
   z tej samej matematyki co edytor, żeby podgląd i gra wyglądały tak samo.
3. Rejestracja: `Minecraft.getInstance().getTextureManager().register(RADAR_ID, tex)`.
   Tekstura **nie idzie przez atlas** — `entityCutout(Identifier)` wiąże ją bezpośrednio.
4. Kropki wysp: na start podgląd lokalny (te same dane próbne co w edytorze), docelowo z sieci.

### 3.5 Dane dla radaru (i dla stanu animacji)

`raft:receivers` ma dziś `flags` (bit 0 = włączony). Do animacji i radaru potrzebne są dalsze
pola — najprościej rozszerzyć kontrakt **na końcu** struktury (klient czyta po kolei, więc
starsza wersja pluginu nie wywala pakietu):

```
int flags      // bit 0 = włączony, bit 1 = tryb pracy, bit 2 = alarm
int pulse      // numer „pulsu" radaru (rośnie co obrót) — fazuje promień bez wysyłania listy wysp
short[] blips  // kąt, odległość, typ — po 3 wartości na kropkę (0 = brak)
```

Ważne: **mod nie liczy, gdzie są wyspy** — dostaje gotowe kropki (kąt + odległość). Inaczej każdy
klient musiałby znać logikę świata, a plugin i tak wie to pierwszy.

### 3.6 Wydajność i LOD

- `CameraRenderState.pos` daje odległość → **ekran aktualizujemy tylko w promieniu N bloków**
  (proponowane 48), animację części zawsze, ale bez alokacji w `submit`.
- Nadpisać `getViewDistance()` (duży obiekt widać z daleka) i rozważyć
  `shouldRenderOffScreen()`.
- Zero `new` w `submit` — kwadry i tablice są już w cache; animacja tylko modyfikuje `PoseStack`.

### 3.7 Komendy testowe (bez pluginu)

```
/rafthud odbiornik                 # jak dziś: postaw podgląd na bloku pod celownikiem
/rafthud anim on|off               # wymuś animację (stan „włączony" z flags)
/rafthud anim predkosc 0.5|1|2     # przyspiesz/spowolnij zegar podglądu
/rafthud anim skok <ticki>         # ustaw czas animacji, żeby obejrzeć klatkę
/rafthud ekran demo                # kropki próbne na ekranie
/rafthud reload                    # przeładuj model I animację z packa
```

### 3.8 Przedmiot w ręce to inna droga — **zrobione**

Jeśli animowany ma być **przedmiot** (nie blok w świecie), w 26.3 służy do tego
`net.minecraft.client.renderer.special.SpecialModelRenderer`:

```
void submit(T argument, PoseStack pose, SubmitNodeCollector collector, int light, int overlay, boolean foil, int outline)
T extractArgument(ItemStack stack)
void getExtents(Consumer<Vector3fc> out)
```

Jak to jest zrobione u nas: `DeviceSpecialRenderer` (rysuje `DeviceMesh` + `DeviceAnimations` —
dokładnie te same dane, co BER), a typ rejestrujemy jako **`raft:device`** w
`SpecialModelRenderers.ID_MAPPER` (jedno `put` w `onInitializeClient`; mapper czyta się leniwie
przy wypiekaniu modeli, więc kolejność rejestracji nie ma znaczenia). Item definition wskazuje
model tak:

```json
{
  "type": "minecraft:special",
  "base": "raft:item/odbiornik_base",
  "model": { "type": "raft:device", "model": "raft:rafthud/odbiornik" }
}
```

Cztery rzeczy, które trzeba było ustalić (wszystkie sprawdzone na kliencie 26.2/26.3, nie z pamięci):

1. **Przestrzeń współrzędnych przedmiotu.** Model przedmiotu jest wypiekany z macierzą `IDENTITY`,
   a `ItemTransform.apply` na końcu swoich transformacji przesuwa geometrię o **-0,5** (i tak samo
   dla modelu z sekcją `display`, i dla modelu bez niej). Dlatego renderer rysuje w tej samej
   przestrzeni, co plik modelu przedmiotu — piksele, 16 px = 1 blok, oś Y w górę, origin w rogu
   bryły — i **nie dodaje** żadnego wyśrodkowania (w świecie robi to BER).
2. **Model bazowy (`base`) musi być osobny i bez elementów.** Waniliowy loader odrzuca geometrię
   poza -16..32 px, więc `base` to tylko `display` (`raft:item/odbiornik_base`) — gdyby wskazywał
   plik z bryłą odbiornika (30x32x29 px, czyli na sufcie), każde powiększenie modelu wysadziłoby
   cały przedmiot. Siatka (`raft:rafthud/odbiornik.json`) nigdy nie przechodzi przez ten loader.
3. **Czas.** `submit` nie dostaje `partialTick` (tak samo jak BER), więc zegar bierze się z klienta:
   `level.getGameTime() + getDeltaTracker().getGameTimeDeltaPartialTick(false)`, a bez świata
   (ekrany bez poziomu) — zegar systemowy. Wartość przechodzi przez `DeviceAnimDebug`, więc
   `/rafthud anim time <t>` zatrzymuje też przedmiot i pokazuje konkretną klatkę.
4. **Ekwipunek nie rysuje przedmiotów co klatkę.** Slot jest wypiekany raz do wspólnej tekstury
   (`GuiItemAtlas`) i używany, dopóki wygląd się nie zmieni; przerysowanie wymaga
   `ItemStackRenderState.setAnimated()`, co wanilia robi dla kompasu, zegara i animowanych tekstur.
   Dlatego jest `SpecialModelWrapperMixin` (inject w `SpecialModelWrapper.update`, bo `clear()`
   zeruje tę flagę przy każdym odświeżeniu modelu). Bez tego w ekwipunku byłaby jedna zamrożona
   klatka, a w ręce ruch działałby normalnie — klasyczne „u mnie działa”.

`getExtents` podaje osiem rogów bryły (px/16) — wanilia używa tego do wykrycia przedmiotów
większych od slotu. Kontrola bez gry: `tools/check-item-model.sh` (item definition -> model
bazowy -> siatka + animacja, w obu packach: roboczym i tym z `modpack/`).

**Podpięcie:** odbiornik jest w `heavy_core` pod **CMD 4002** (4001 = radio, 4003 = duża beczka).
Żeby go zobaczyć: `/give @s minecraft:heavy_core[minecraft:custom_model_data=4002]`. Zmiana na
inny przedmiot/CMD to jedna linia w item definition — mod nie zna numerów CMD, bo to dane itemu.

**Tego JSON-a nie piszesz już ręcznie:** panel 7 edytora ma sekcję „Item definition animowanego
przedmiotu” (id przedmiotu, CMD, model bazowy). „Kopiuj item definition” pokazuje gotowy plik,
a „Zapisz do folderu packa” zapisuje go razem z modelem bazowym i plikiem `.anim.json`.
Dwie rzeczy, które ten generator robi za ciebie:

- **scala progi CMD** — jeden plik `items/<przedmiot>.json` obsługuje kilka mechanizmów (4001
  radio, 4002 odbiornik, 4003 beczka…), więc zapis dopisuje nasz wpis i zostawia cudze wpisy
  (i ich wcięcia) nietknięte; kolizję naszych własnych CMD zgłasza jako ostrzeżenie,
- **robi model bazowy** z modelu otwartego w scenie (tylko `display` + tekstura cząstek), bo
  siatka odbiornika ma 30×32×29 px, a waniliowy loader odrzuca modele itemów z bryłą poza
  −16..32 px — dlatego „bazowy” jest osobno, a geometrię rysuje mod.

Walidacja przed zapisem: id przedmiotu, CMD > 0, format `namespace:ścieżka` dla siatki i modelu
bazowego, ostrzeżenie gdy siatka wygląda na model z `models/` (mod szuka jej w
`assets/<ns>/<ścieżka>.json`) oraz gdy panel 7 nie ma żadnego toru.
Siatka odbiornika ma komplet tekstur od 2026-09-23 (materiały +
UV: `pack/tools/OdbiornikUvPaint.java`), więc i w ręce, i w świecie widać już docelowy wygląd —
z wyjątkiem samego radaru, który jest na razie nieruchomą teksturą (poz. 5 poniżej).

---

## 4. Kolejność wdrożenia

| # | Krok | Stan |
|---|---|---|
| 1 | Części w `DeviceMesh` + test bezgłowy | **zrobione** — 5 części, 912/912 ścian, 0 problemów |
| 2 | `DeviceRenderState` + zegar + obrót części | **zrobione** |
| 3 | Parser `anim.json` + interpolacja | **zrobione** — edytor i mod dają tę samą liczbę w 1/4 pętli |
| 4 | Panel „7" w edytorze: oś czasu + podgląd + eksport | **zrobione** |
| 5 | Ekran radaru: dynamiczna tekstura + dane próbne | do zrobienia (§3.4) |
| 6 | Plugin wysyła blips/pulse (kontrakt §3.5) | do zrobienia |
| 7 | przedmiot w ręce przez `SpecialModelRenderer` | **zrobione** (§3.8) — odbiornik pod CMD 4002, radio świadomie bez animacji |

**Obieg pracy od strony autora (gotowy):** w Blockbenchu ustawiasz **grupy z originem**
(origin = pivot obrotu), eksportujesz model Java do packa, w edytorze w panelu 7 dodajesz tory
i klatki w tickach, „Eksport anim.json” → zapis pliku obok modelu. W grze podgląd:
`/rafthud odbiornik`, a obejrzenie konkretnej klatki: `/rafthud anim time 40`.
Bez pliku `anim.json` urządzenie jest statyczne — dokładnie jak przed zmianą.

Punkty 1–2 są warte zrobienia niezależnie od reszty: dają ruch, nie ruszając wyglądu, i dopiero
na nich ma sens projektowanie czegokolwiek w edytorze.

---

## 5. Czego edytor nie sprawdzi (świadome granice)

- **Prawdziwego renderu**: kolejność przezroczystości, oświetlenie, cień, migotanie dwóch
  animowanych części w tym samym miejscu (z-fighting) — w edytorze tego nie zobaczysz.
- **Fazy między klientami**: edytor pokaże jedną fazę; w grze animacja liczy się z `gameTime`,
  więc u wszystkich graczy jest ta sama (to akurat zaleta, nie problem).
- **Logiki radaru**: skąd są wyspy, jak liczyć zasięg, czy kropka ma być za ekranem.
- **Kosztu**: narysowanie 64×64 co tick w edytorze nic nie kosztuje, w grze to upload tekstury —
  dlatego LOD z §3.6 musi być w kodzie, nie w edytorze.
- **Istnienia plików w packu**: item definition wskazuje siatkę i plik `.anim.json` jako ścieżki —
  czy faktycznie leżą w packu, sprawdza `tools/check-item-model.sh` (edytor tego nie widzi).
- **Kolizji CMD z pluginem**: generator ostrzeże o kolizji z wariantami z sekcji 6, ale numery CMD
  przydziela plugin — edytor nie wie, co kod nadaje przedmiotom.

## 6. Ryzyka

| ryzyko | co robimy |
|---|---|
| rozbicie mesha na części psuje istniejące modele | test bezgłowy przed/po (§3.2), części bez nazwy → `root` |
| z-fighting animowanych części | zakładka 0,5–1 px w modelu (ta sama zasada co przy dzieleniu palmy na klocki) |
| tekstura ekranu poza atlasem nie działa z `entityCutout` | sprawdzić na jednym urządzeniu **przed** pisaniem pełnego radaru |
| plugin starszy niż mod (brak `pulse`/`blips`) | czytanie pakietu do końca z domyślnymi wartościami |
| obciążenie przy kilkudziesięciu urządzeniach | jedna tekstura ekranu wspólna + LOD z §3.6 |
| animowany przedmiot w ekwipunku stoi w miejscu | flaga `setAnimated` z §3.8 p. 4 (bez niej atlas GUI trzyma jedną klatkę) |
| przedmiot widoczny mimo rozmiaru bryły poza -16..32 px | geometrię rysuje mod; `base` to model bez elementów (tylko `display`) |
