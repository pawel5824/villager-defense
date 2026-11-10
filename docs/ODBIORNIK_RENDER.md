# Duży odbiornik w grze — renderer w modzie (BER)

Stan: **działa end-to-end** (2026-09-18): geometria, rejestracja, odbiór danych i narzędzia
podglądu po stronie moda + warstwa pluginu (`RaftReceiversPacketSender` wysyła
`raft:receivers`, nośnik-beczka stawiany z `DeviceBlock2x2.spawn()`).
Szczegóły implementacji pluginu: `docs/ANTENY_ODBIORNIK.md` (stan fazy 1).

## 1. Analiza — co jest, co blokuje

**Ograniczenie, które trzeba obejść.** Waniliowy klient odrzuca model, jeśli `from`/`to`
wychodzi poza **−16…32 px**. Sprawdzone w kliencie, z którym buduje się projekt (26.3):

```
net.minecraft.client.resources.model.cuboid.CuboidModelElement$Deserializer#getPosition
  fcmpg/fcmpl z float -16.0f i 32.0f
  → JsonParseException: 'from' specifier exceeds the allowed boundaries: [...]
```

Limit siedzi **w deserializerze JSON-a**, czyli odpala się przy wczytywaniu resource packa —
zanim jakikolwiek renderer zobaczy model. Dlatego „podniesienie limitu" w packu, w configu
albo w modzie jest niemożliwe. Obejście: **nie ładować geometrii przez waniliowy loader**.

**Wasz obecny odbiornik jest już na sufcie.** `pack/assets/raft/models/item/odbiornik.json`:
152 elementy, `from [1, 0, 0]` … `to [31, 32, 29]` → **30×31×29 px**. Oś Y dochodzi dokładnie
do 32, czyli do górnej granicy okna. Każde powiększenie = odrzucony plik.

**Model jest już wykończony (2026-09-23).** Do tego dnia plik był szkicem: **905 z 912 ścian
nie miało przypisanej tekstury** (`"texture": "#missing"`), a jedyna konkretna tekstura
(`block/licznik`) **nie istniała w packu** — w grze widać było szachownicę „missing texture”.
Dziś każda ściana ma materiał z istniejącego PNG-a, a brakujących tekstur jest **zero**
(pilnuje tego `tools/check-item-model.sh`).

Materiały i UV nadaje **`pack/tools/OdbiornikUvPaint.java`**, nie ręczna praca w Blockbenchu:
geometrię robi artysta, a o tym, co jest drewnem, co metalem i gdzie jest ekran, decyduje reguła
w kodzie (grupa modelu + kilka wyjątków po numerze elementu). Siedem materiałów: stolik → drewno,
ekran (szyba) → radar, tarcza zegara i płyta odczytu → licznik, korpus ogniwa → bateria,
antenka → tekstury anteny (`antena_metal`, `antena_lamp`), cała reszta → obudowa.
Ten sam plik poprawia UV: w szkicu **16 ścian wychodziło poza sprite** (u do 30 przy teksturze
16 px), a atlas **nie powtarza** sprite'a — taka ściana pokazuje sąsiedni sprite. Teraz każde UV
to okno wewnątrz `0,5…15,5` (pół piksela marginesu na mipmapy) o proporcjach ściany.

**Mod jest client-only** (`"environment": "client"` w `fabric.mod.json`), więc **nie może
zarejestrować własnego bloku** — rejestry bloków są synchronizowane z serwerem. Dlatego
obiekt trzeba zaczepić na **waniliowym bloku-nośniku**, który stawia plugin.

**W 26.3 nadal nie ma `WorldRenderEvents`** (moduł `fabric-rendering-v1-27.0.14` w bundlu
`fabric-api-0.161.0+26.3` go nie zawiera — w 0.159.0+26.2 było tak samo). Czyli BER jest tu
właściwym i praktycznie jedynym wygodnym miejscem na własne rysowanie w świecie.

## 2. Architektura

```
plugin (Paper)                              mod (Fabric, client)
──────────────                              ────────────────────
stawia blok-nośnik (beczka)     ───────►    BlockEntityRenderer dla BlockEntityTypes.BARREL
raft:offsets  (już jest)        ───────►    RaftOffsetStorage  → sub-pikselowy ruch tratwy
raft:receivers (nowy)           ───────►    RaftReceiverStorage → pozycja, obrót, typ, stan
                                            DeviceMesh (JSON → kwadry, bez limitu -16..32)
                                            submitCustomGeometry → rysowanie
```

**Nośnik: beczka (`minecraft:barrel`).** Uzasadnienie: ma BlockEntity, ale **nie ma
waniliowego renderera** (sprawdzone: lista rejestracji w `BlockEntityRenderers`), więc nasza
rejestracja niczego nie nadpisuje ani nie psuje. Drugi warunek jest równie ważny:
`BlockEntityRenderer` jest wołany **tylko dla pozycji, na której faktycznie stoi blok
z BlockEntity** — bez nośnika klient w ogóle nie zapyta renderera o to miejsce.

**Nośnik jest niewidzialny w jeden konkretny sposób.** `pack/assets/minecraft/blockstates/barrel.json`
nadpisuje **tylko jeden z 12 stanów** beczki — `facing=down, open=true` — kierując go na
`minecraft:block/air` (pusty model). Wszystkie pozostałe warianty są przepisane 1:1 z wanilii,
więc zwykłe beczki wyglądają i działają jak zawsze. Cena: beczka postawiona **patrząc w sufit**
i **otwarta jednocześnie** zniknie — stan praktycznie nieosiągalny (normalne stawianie daje
`open=false`, a `open` ustawia się dopiero, gdy gracz ma otwarte GUI).
Wniosek dla pluginu: **stawiaj nośnik jako `facing=down, open=true` i blokuj jego otwieranie**
(`PlayerInteractEvent` → `setCancelled`), bo otwarcie i zamknięcie kontenera przestawiłoby
stan z powrotem na widoczny.

**Geometria budowana w kodzie, ale z pliku.** Ręczne przepisywanie 152 kostek do Javy nie ma
sensu, więc mod czyta **ten sam JSON z Blockbencana** — tylko własnym parserem, jako dane
(nie przez `ModelManager`). Limit −16…32 nie istnieje w tej ścieżce, bo nigdzie nie ma
waniliowego deserializera. Czyli: artysta dalej robi model w Blockbenchu, a mod go rysuje
w dowolnym rozmiarze.

Ścieżka modelu w packu: **`assets/raft/rafthud/odbiornik.json`** (ten sam plik jest w
`pack/assets/…` i w `modpack/resourcepacks/raft-hud/assets/…`). Zmiana modelu = reload packa
(F3+T albo `/rafthud reload`), **bez przebudowy moda**.

## 3. UV i kolejność wierzchołków — skopiowane z wanilii, nie zgadnięte

Dwie rzeczy, które w własnym parserze modeli najłatwiej zepsuć:

**Normalizacja UV.** Wanilia **zawsze** dzieli UV przez **16**, niezależnie od rozmiaru PNG-a:

```
CuboidFace.getU(uvs, quadrant, i)  →  uvs.getVertexU(i) / 16.0f
```

a potem `TextureAtlasSprite.getU(u)` rozciąga przedział [0,1] na całą teksturę.
Pole `texture_size` **nie jest przez grę używane** (sprawdzone w kliencie 26.2: żadna klasa
się do niego nie odwołuje; w 26.3 parser dzieli tak samo przez 16 i nie czyta PNG-ów). Dlatego parser dzieli przez 16 i nie czyta PNG-ów — dzięki temu model
renderowany tą ścieżką wygląda **identycznie**, jakby był zwykłym modelem bloku.
(Sprawdzone na Waszych modelach: `duza_beczka.json` ma `texture_size: [64,64]`, ale UV w
siatce 0…16 — dzielenie przez rozmiar PNG-a dałoby zupełnie inny kadr tekstury.)

**Kolejność wierzchołków i przypisanie UV.** Skopiowane z `net.minecraft.client.renderer.FaceInfo`
i `CuboidFace$UVs`. Dla każdej ściany 4 wierzchołki w kolejności waniliowej, a UV idzie
kanonicznie: **(0,1)→minU, (2,3)→maxU, (0,3)→minV, (1,2)→maxV**. `"rotation"` ściany obraca
tylko przypisanie UV (`(i + angle/90) % 4`), obrót elementu obraca wierzchołki i normalną.

To nie jest kosmetyka: zła kolejność = ściany widoczne tylko od środka, złe przypisanie UV =
tekstura obrócona o 180° albo lustrzana. Weryfikacja jest automatyczna (patrz §6).

## 4. Pliki

| plik | rola |
|---|---|
| `pack/src/…/device/RaftReceiversPayload.java` | pakiet `raft:receivers` (clientbound) |
| `pack/src/…/device/RaftReceiverStorage.java` | pozycje urządzeń po stronie klienta |
| `pack/src/…/device/DeviceMesh.java` | kwadry + parser JSON-a + tabele waniliowe |
| `pack/src/…/device/DeviceMeshCache.java` | cache meshy + czytelne ostrzeżenia |
| `pack/src/…/device/DeviceBlockEntityRenderer.java` | BER: `submit` → `submitCustomGeometry` |
| `pack/src/…/device/DeviceCables.java` | kable anten (faza 4): trasa + rurka z przekrojem |
| `pack/src/…/device/DeviceAnchors.java` | kotwice w px (gniazdo, wtyk, pokład) + most model↔świat |
| `pack/assets/raft/rafthud/antena.json` | model anteny — finalny (faza 5), 9 elementów, 54 ściany, 12×23×12 px |
| `pack/assets/raft/rafthud/antena.anim.json` | ruch anteny: obrót `ramiona` + pulsowanie `swiatelko` |
| `pack/assets/raft/rafthud/antena.bbmodel` | źródło Blockbencna anteny (do paczki **nie** jedzie — zip i kopie modpacka je pomijają) |
| `pack/assets/raft/models/item/antena_base.json` | model bazowy przedmiotu antena (tylko `display`) |
| `pack/assets/raft/textures/block/antena_metal.png` | tekstura anteny: stal (16×16) |
| `pack/assets/raft/textures/block/antena_lamp.png` | tekstura anteny: czerwona lampka (16×16) |
| `pack/tools/AntennaTextureGen.java` | generator obu tekstur anteny (16×16) |
| `pack/tools/CableTextureGen.java` | generator `raft:textures/block/kabel.png` (16×16) |
| `pack/src/…/device/DeviceDebugCommand.java` | `/rafthud …` — podgląd i przeładowanie |
| `pack/src/…/RaftHUDMod.java` | rejestracja: payload + BER dla `BlockEntityTypes.BARREL` |
| `pack/assets/raft/rafthud/odbiornik.json` | geometria i grupy urządzenia (eksport z Blockbencana); **tekstury i UV nadpisuje `OdbiornikUvPaint`** |
| `pack/assets/raft/textures/block/odbiornik_drewno.png` | materiał: drewno stolika (16×16) |
| `pack/assets/raft/textures/block/odbiornik_obudowa.png` | materiał: ciemny metal z nitami (16×16) |
| `pack/assets/raft/textures/block/odbiornik_ekran.png` | materiał: szyba ekranu z radarem (16×16) |
| `pack/assets/raft/textures/block/odbiornik_licznik.png` | materiał: bursztynowe słupki odczytu (16×16) |
| `pack/assets/raft/textures/block/odbiornik_bateria.png` | materiał: ogniwo żółto-czarne (16×16) |
| `pack/tools/OdbiornikTextureGen.java` | generator tych pięciu tekstur |
| `pack/tools/OdbiornikUvPaint.java` | malarz modelu: materiał + UV dla każdej z 912 ścian |
| `pack/src/main/resources/assets/raft/rafthud/odbiornik.json` | **kopia w JARZE moda** — żeby urządzenie działało także bez serwerowej paczki (wchodzi do jara przy najbliższym buildzie moda) |
| `pack/src/main/resources/assets/raft/textures/block/odbiornik_*.png` | kopia tych pięciu tekstur w JARZE moda |

To już **czwarta** kopia tych samych plików (`pack/` → `modpack` → `.minecraft` → `src/main/resources`),
a jedyna, której nie wyrównuje `tools/publish-pack.sh` (nie dotyka zasobów moda). Rozjechanie jest
ciche — w paczce nowy model, w jarze stary — więc `tools/check-item-model.sh` sprawdza
**identyczność co do bajtu** każdej kopii, która istnieje.
| `pack/assets/minecraft/blockstates/barrel.json` | ukrycie stanu nośnika (`facing=down, open=true`) |

## 5. Kontrakt dla pluginu (`raft:receivers`)

```
int   count
count × {
  int   x, y, z               // pozycja bloku-nośnika (beczki)
  float yaw                   // obrót w stopniach, 0 = model tak, jak w Blockbenchu
  int   kind                  // numer typu urządzenia (0 = odbiornik, 1 = antena)
  int   flags                 // bity stanu (bit 0 = włączony; wyłączony = przygaszony)
  int   linkX, linkY, linkZ   // tylko antena: nośnik jej odbiornika (0,0,0 = brak linku)
}
```

Wpis ma **28 bajtów** (był 20 — doszedł link w fazie 4). Klient liczy rozmiar wpisu
ze stałej, więc pakiet ze starego pluginu jest odrzucany w całości, a nie czytany w połowie.
Anteny (`kind=1`) rysują swój model i domykają kabel w gnieździe odbiornika — szczegóły
w [ANTENY_ODBIORNIK.md](ANTENY_ODBIORNIK.md) (stan fazy 4).

Wysyłane po postawieniu/usunięciu/zmianie urządzenia oraz przy wejściu gracza na serwer
(pakiet jest **pełny** — klient podmienia całą listę). Klient ignoruje pozycje bez nośnika.

## 6. Weryfikacja bez uruchamiania gry

`tools/check-device-mesh.sh` uruchamia bezgłowy test parsera (`tools/DeviceMeshCheck.java`)
na modelach z packa i sprawdza:

- **winding** — czy kolejność wierzchołków daje normalną zgodną z zadeklarowaną,
- **orientację tekstury** — czy górna krawędź ściany dostaje górny wiersz tekstury i czy oś
  pozioma UV idzie po właściwej osi świata (te dwa testy tylko dla modeli bez obrotów — przy
  elementach obróconych UV obraca się razem z elementem, więc pomiar w układzie świata nie ma sensu),
- **rotacje** — test syntetyczny z ręcznie policzonymi wierzchołkami (sześcian 16×16×16
  i ten sam obrócony o 90° wokół Z),
- ściany bez tekstury i rozmiar modelu w px.

Ostatni wynik: **0 problemów** — `odbiornik` 912 ścian, `duza_beczka` 42, `radio` 162,
plus oba testy syntetyczne.

`tools/check-item-model.sh` (patrz niżej) dokłada do tego dwie kontrole, które złapały realne
błędy przy wykańczaniu odbiornika:

- **UV w granicach sprite'a** — UV poza `0..16` nie powtarza tekstury, tylko sięga po sąsiedni
  sprite z atlasu. W szkicu odbiornika dotyczyło to 16 ścian (blat stołu pokazywał obce piksele).
- **UV o proporcjach ściany** — rozciągnięcie w jednej osi widać w grze jako rozmazany pas,
  a w pliku modelu nie rzuca się w oczy. Tolerancja to 2% + błąd siatki UV (`0,01` na oś),
  bo na ścianie grubości 0,05 px samo zaokrąglenie brzegów zmienia proporcję o kilka procent.

## 7. Jak to zobaczyć bez pluginu (`/rafthud`)

Renderer rysuje tylko to, co każe mu serwer, więc dopóki plugin nie wysyła `raft:receivers`,
nie byłoby czego oglądać. Dlatego mod ma klienckie narzędzie (nic nie wysyła na serwer):

```
/setblock ~ ~ ~ minecraft:barrel[facing=down,open=true]   # nośnik (niewidzialny w packu)
/rafthud odbiornik                                        # postaw podgląd na blok pod celownikiem
/rafthud odbiornik 90                                     # to samo, z obrotem w stopniach (0 = jak w Blockbenchu)
/rafthud antena                                           # antena podglądowa (sama szuka odbiornika ≤ 8 bloków)
/rafthud kable                                            # liczniki kabli + błąd matematyki gniazda/wtyku
/rafthud usun                                             # usuń podgląd z bloku pod celownikiem
/rafthud wyczysc                                          # usuń wszystkie podglądy
/rafthud reload                                           # wczytaj model z packa od nowa (bez restartu gry)
```

Bez nośnika gra w ogóle nie pyta renderera o to miejsce, więc komenda wypisze gotową
komendę `/setblock` do wklejenia. Typowy obieg pracy nad modelem: podmieniasz plik w packu →
F3+T (albo `/rafthud reload`) → widzisz nowy model na tym samym stanowisku, bez wychodzenia z gry.

## 8. Czego jeszcze nie ma

- **wygląd odbiornika nie był jeszcze oceniony w grze.** Materiały i UV są gotowe i przechodzą
  testy bezgłowe, ale to nadal ocena z plików — czy stolik, licznik i ogniwo czytają się dobrze
  obok siebie, sprawdza się dopiero patrząc na urządzenie na tratwie (`/rafthud odbiornik`).
  Tekstury są celowo proste (16×16, jeden wzór na całą powierzchnię), żeby w każdej chwili dało
  się je podmienić bez ruszania modelu — wystarczy zmienić kolor w `OdbiornikTextureGen`.
- **kable i anteny są zrobione (fazy 4–5)**: antena = `assets/raft/rafthud/antena.json`
  (finalny model z Blockbencna + animacja; szczegóły w `ANTENY_ODBIORNIK.md`, „Stan fazy 5"),
  kable rysuje `DeviceCables`
  w układzie modelu odbiornika (`DeviceAnchors` — gniazdo/wtyk/pokład w px). Podgląd:
  `/rafthud antena` i `/rafthud kable` (patrz §7).
- ekran radaru jako dynamiczna tekstura na modelu — **plan w [ANIMACJE_I_RADAR.md](ANIMACJE_I_RADAR.md)**,
- **animacje części są już zrobione**: grupy modelu = części (`DeviceMesh.Part`), ruch opisuje
  `<model>.anim.json` czytany przez `DeviceAnimations`, a podglądem w grze sterują
  `/rafthud anim on|off|info|time <t>|speed <x>`. Autorem animacji jest panel „7 · Animacje”
  w `model-editor.html`; szczegóły i to, co jeszcze zostało (radar), w `ANIMACJE_I_RADAR.md`,
- **ten sam model działa też jako przedmiot** (`DeviceSpecialRenderer`, typ `raft:device`):
  `/give @s minecraft:heavy_core[minecraft:custom_model_data=4002]` pokazuje animowany
  odbiornik w ręce i w ekwipunku, a `…=4004` antenę (faza 5, model bazowy `raft:item/antena_base`). Geometria i animacja są te same, co w świecie — różni się
  tylko przestrzeń rysowania i zegar (szczegóły w `ANIMACJE_I_RADAR.md` §3.8).
  **Stan od 2026-10-01:** wpisy CMD 4002/4004/4005 w `heavy_core.json` to ZWYKŁE modele
  (`raft:item/odbiornik_fallback`, `antena_fallback`, `zagiel_fallback`) — przedmiot wygląda
  poprawnie też u gracza BEZ modu (wcześniej typ `raft:device` dawał u niego szachownicę),
  ale bez modu nie jest animowany. Geometria w świecie i tak zawsze rysuje mod na bloku-nośniku.
  Panel 7 edytora nadal generuje wpis `minecraft:special`; gdy przedmiot ma działać bez modu,
  podmień go na zwykły model (wzorzec: `raft:item/odbiornik_fallback`).
