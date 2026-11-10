# Muzyka trybu Raft

## Jak to działa

Muzyka gra **tylko w świecie Raft** i **dla jednego gracza naraz** (każdy
słyszy swój utwór — serwer wysyła pakiet dźwięku per gracz, więc dwaj gracze
na tej samej tratwie mogą mieć różne utwory i różne ustawienia).
`MusicManager` (plugin) kilka razy na sekundę (4 Hz — żeby tryb
„natychmiastowo” nie spóźniał startu następnego utworu) sprawdza graczy
w świecie Raft i po wylosowanej przerwie gra następny utwór:

```java
player.playSound(loc, "raft:music.utwor_1", MUSIC, głośność, ton)
```

Przerwa między utworami: **3–8 min** (losowo; config `raft-music`).
Pierwszy utwór pada ~45 s po wejściu do świata. Wyjście ze świata ucina
utwór i zapomina stan. Waniliowa muzyka Minecrafta gra niezależnie (ta sama
kategoria `music`, więc suwak „Muzyka” w ustawieniach klienta steruje obiema).

## Pliki paczki (tracki dorzuca właściciel)

- `assets/raft/sounds.json` — definicje zdarzeń dźwiękowych (już jest:
  `raft:music.utwor_1`, `raft:music.utwor_2`, oba z `"stream": true`),
- `assets/raft/sounds/music/*.ogg` — pliki OGG Vorbis; nazwa pliku =
  ostatni człon identyfikatora (`music.utwor_1` → `utwor_1.ogg`).

Nowy utwór = (1) wrzuć `xxx.ogg` do `sounds/music/`, (2) dopisz wpis
`"music.xxx"` w `sounds.json`, (3) dopisz `"raft:music.xxx"` do
`raft-music.tracks` w `config.yml` serwera, (4) publish paczki
(`tools/publish-pack.sh`) + **restart serwera**.

**Uwaga na separator:** klucz w `sounds.json` i identyfikator w configu muszą
być IDENTYCZNE i mieć **kropkę** (`music.utwor_1` → `raft:music.utwor_1`) —
tak, jak wanilia pisze `minecraft:music.game`. Forma z ukośnikiem
(`raft:music/utwor_1`) jest poprawnym identyfikatorem, ale **nie wskazuje
żadnego zdarzenia z paczki** — klient nie zagra nic (w jego logu pojawi się
`Unable to play unknown soundEvent`), a system wygląda na zepsuty. Nazwa pliku
`.ogg` (`sounds/music/utwor_1.ogg`) to osobna sprawa i ukośnik tam jest OK.

## Config serwera (config.yml → raft-music)

| Klucz | Domyślnie | Znaczenie |
|---|---|---|
| `enabled` | `true` | Globalny włącznik (fałsz = cisza dla wszystkich). |
| `interval-min-seconds` | `180` | Krótsza przerwa między utworami. |
| `interval-max-seconds` | `480` | Dłuższa przerwa. |
| `start-delay-seconds` | `45` | Pierwszy utwór po wejściu do świata. |
| `default-volume` | `1.0` | Głośność gracza bez własnego ustawienia. |
| `default-pitch` | `1.0` | Ton/prędkość gracza bez własnego ustawienia. |
| `tracks` | 2 przykłady | Identyfikatory utworów (`raft:music.xxx`). |
| `pack-dir` | `""` (puste) | Katalog paczki (z `pack.mcmeta` i `assets/`) — z niej serwer czyta długości utworów. Puste = nie czyta plików. |
| `default-track-seconds` | `180` | Długość przyjmowana, gdy nie da się jej ustalić. |
| `track-seconds` | (puste) | Ręczne długości (sekundy) per utwór; wygrywają z odczytem z plików. |

Zmiany w tej sekcji działają po `/def reload` (i po starcie).

## Tryb „natychmiastowo” — utwór gra DO KOŃCA

Przy częstotliwości **natychmiastowo** utwór gra w całości, a następny rusza
od razu po nim (wcześniej serwer ucinał go po ~3 s). Serwer nie zna długości
dźwięku — zna ją tylko klient — więc bierze ją w tej kolejności:

1. `raft-music.track-seconds.<id>` z configu (ręcznie, w sekundach),
2. plik `.ogg` z `raft-music.pack-dir` — dokładnie, z nagłówka Vorbisa
   (sample rate + granule ostatniej strony = liczba próbek),
3. `raft-music.default-track-seconds` — gdy utworu nie ma w powyższych.

Przerwa = długość ÷ ton gracza (ton to prędkość — szybszy utwór kończy się
wcześniej), z podłogą 3 s (chroni przed zerem/błędną długością). Ścieżka pliku
wynika z `sounds.json` (klucz zdarzenia → `name`), tak samo jak czyta klient.
`/def reload` czyści zapamiętane długości (podmienione pliki .ogg są czytane
od nowa). Gdy długości nie znasz, wpisz ją ręcznie w `track-seconds` —
usunie to 3-sekundowy odstęp dla tego utworu.

## Jak połączyć muzykę z przedmiotem (płyty w GUI)

Każdy utwór z `raft-music.tracks` w configu dostaje w GUI swoją płytę:
**i-ty utwór z listy = PAPIER z CMD 6101+i** (utwór 1 → CMD 6101, utwór 2 →
CMD 6102, …; max 36; płyty leżą w rzędach 2–5 GUI, sloty w grze 10–45).
Bez modelu papier wygląda jak zwykły papier z nazwą utworu; w lore jest
wypisany jego CMD (`cmd: 6101`), żeby wiedzieć, którą teksturę robić.
Dodanie utworu = dopisanie go do `tracks` w configu (+ plik .ogg w paczce,
patrz wyżej) — GUI podbiera go automatycznie.

**Własna tekstura płyty (obrazek z Painta):** pliki są już PRZYGOTOWANE —
`assets/minecraft/items/paper.json` (range_dispatch CMD 6101–6110 → modele
`raft:item/plyta_1..10`, fallback = waniliowy papier), modele
`assets/raft/models/item/plyta_1..10.json` i placeholderowe PNG
`assets/raft/textures/item/plyta_1..10.png` (jednolite kolory 32×32 — bez
własnej tekstury papier wygląda kolorowo, nigdy „missing texture”).
Podmieniasz TYLKO `plyta_N.png` na swój obrazek (zalecane 32×32 albo 64×64,
zapisany jako PNG), potem `tools/publish-pack.sh` + **restart serwera**
(paczka u graczy wchodzi przy restarcie). Kopia w jarze moda
(`pack/src/main/resources`) też dostaje te pliki przy publish —
`tools/check-item-model.sh` pilnuje zgodności.

Nowe CMD (6111+, utwory 11+) = dopisz threshold do `items/paper.json`,
dodaj `plyta_N.json` (parent `minecraft:item/generated`, layer0 =
`raft:item/plyta_N`) i PNG.


Klik w płytę: **LPM = zagraj ten utwór TERAZ (jednorazowo)** — gra raz do
końca, po czym wraca normalny tryb (losowanie; przy włączonej cugli zapętli
się ten utwór, bo staje się „ostatnio granym”). **Shift+LPM = wróć do
losowania** bez grania. Wybór jest per gracz (`music-selected-track` w
raft-players.yml) i czyści się sam po odtworzeniu; usunięcie utworu z configu
= brak efektu. Klik w płytę widać w GUI (zielony + „▶ ZAGRA TERAZ (raz)”) i
w `/muzyka status` („Następny utwór”).

## Ustawienia per gracz (/muzyka)

Działają tylko w świecie Raft; zapis w `raft-players.yml`
(`players.<uuid>.settings`) — osobne dla każdego gracza, przetrwają restart.
GUI z komendy (`/muzyka` bez argumentów) to **6×9 (54 sloty)** — rzędy 2–4
puste (rezerwa na płyty/utwory). Ustawienia obejmują także `music-loop`
(on/off — zapętlenie ostatnio granego utworu; bez GUI-podkomendy, tylko
przycisk w GUI). To samo GUI otwiera slot „Muzyka”
w panelu ustawień Rafta (`/ustawienia` → płyta; `MuzykaCommand.openGui` jest
publiczne, patrz `docs/USTAWIENIA.md`).

Zawartość GUI (sloty w grze):

| Slot | Ikona | Działanie |
|---|---|---|
| 10–45 | płyty utworów z configu (PAPIER z CMD 6101+) | LPM = wybierz utwór (zagra za ~3 s, potem zawsze ten), Shift+LPM = wróć do losowania; i-ty utwór z listy `raft-music.tracks` = papier CMD 6101+i; WYBRANY utwór świeci połyskiem zaklęcia (bez enchantu w tooltipie) |
| 46–49 | (puste) | — |
| 54 | BETON — suwak głośności | LPM +1, Shift+LPM +10, PPM −1, Shift+PPM −10 (0–100); beton = poziom: zielony 100%, jasnozielony 75%, żółty 50%, pomarańczowy 25%, czerwony 0% (wyłączona); pasek 10 segmentów w podpowiedzi |
| 47 | lejek — częstotliwość | LPM = wybór w górę, PPM = w dół: natychmiastowo → często → normalnie → rzadko → nigdy; wybrana opcja pogrubiona (nazwa + lista) |
| 48 | cugle — zapętlenie | klik = włącz/wyłącz; stan widoczny w nazwie i podpowiedzi (zielone „§l✓ ZAPĘTLONE” / szare „✗ Nie zapętlone”); zapętlenie = kolejny utwór to TEN SAM co ostatnio (płyta zagra raz, cugla odpowiada za powtarzanie) |
| 50 | nether star „Wyjdź” | zamyka GUI |

Pozostałe kliknięcia (rzędy 2–4, ekwipunek gracza) są anulowane — nic nie
wyciągniesz i nic nie włożysz. Zmiany widać na żywo (nazwa ikony i pasek).

| Podkomenda | Ustawienie | Zakres | Uwagi |
|---|---|---|---|
| `/muzyka wlacz` / `wylacz` | `music-enabled` | on/off | Włączenie planuje utwór za ~3 s. |
| `/muzyka glosnosc <0-100>` | `music-volume` | 0.0–1.0 | Działa od następnego utworu. |
| `/muzyka ton <50-200>` | `music-pitch` | 0.5–2.0 | **Ton = prędkość** (patrz niżej). |
| `/muzyka czestotliwosc [w]` | `music-frequency` | natychmiastowo/często/normalnie/rzadko/nigdy | Utwór do końca → następny od razu / ×0.5 / ×1 / ×2 / cisza. |
| `/muzyka status` | — | — | Podgląd ustawień. |

**Głośność jest zawsze dokładnie taka, jak ustawił gracz**, ale sposób jej
wdrożenia zależy od tego, czy gracz ma moda:

- **Gracz z modem (raft-hud):** zmiana suwaka leci kanałem `raft:musicvol`
  i działa **natychmiast, na żywo** — bez restartu utworu, bez utraty pozycji
  odtwarzania (drugi akapit niżej). Kolejne utwory startują już z nową
  głośnością w pakiecie, więc nic się nie sumuje.
- **Gracz bez moda:** waniliowy pakiet dźwięku ma głośność „zapieczętowaną”
  na starcie utworu, więc nowa głośność obowiązuje **od następnego utworu**
  (bieżący dogrywa ze starą; plan cyklu bez zmian).

**Wyciszenie (0%, czerwony beton) ucina bieżący utwór od razu** (u wszystkich),
ale cykl gra dalej: kolejne utwory startują bezgłośnie (gracz „wie, że leci
muzyka”, tylko jej nie słyszy; wanilia traktuje dokładne 0 jak pełną głośność,
stąd technicznie 0.0001). Powrót z 0 na głośność > 0 słychać od razu u gracza
z modem, a u gracza bez moda — od następnego utworu.

Mnożniki częstotliwości skracają/wydłużają losową przerwę z configu
(często = pół przerwy, rzadko = podwójna). **natychmiastowo** = utwór gra do
końca, a następny startuje od razu (długość z configu/pliku .ogg — patrz
„Tryb natychmiastowo” wyżej; nieznana długość = 3 s przerwy). „nigdy” ucina aktualny utwór
i blokuje też planowanie po wejściu do świata, włączeniu muzyki i kliku w
płytę. GUI (lejek, slot gry 47) i komenda korzystają z tej samej listy
wartości (`MusicManager.FREQ_VALUES`) — kolejność: natychmiastowo → często →
normalnie → rzadko → nigdy.

**Przed każdym nowym utworem serwer ucina bieżące** — przy „natychmiastowo”
poprzedni utwór właśnie się skończył (planowany po długości), a przy
zapętleniu/wybranym utworze ucina resztkę, żeby nie dołożyć kolejnej instancji
tego samego strumienia (kakofonia).
Klik w płytę przycina bieżący utwór od razu (wybrany zagra za ~3 s); powrót
do losowania (Shift+LPM) dogrywa bieżący utwór do końca. Wyjście ze świata
zapomina „pętlę” — po powrocie pętla zaczyna od nowego losowania.

## Ton a prędkość (ważne)

Pakiet dźwięku ma JEDEN parametr `pitch` — podniesienie tonu przyspiesza
utwór (jak winyl na wyższych obrotach): `1.2` = wyżej i szybciej, `0.8`
niżej i wolniej. Rozdzielenie tonu od prędkości wymagałoby moda klienckiego
z własnym mikserem audio — poza zakresem. `50–200` w komendzie = procenty
oryginalnego tempa.

## Głośność na żywo (mod kliencki, kanał `raft:musicvol`)

Waniliowy pakiet playSound „zapieka” głośność na starcie utworu — serwer nie
może zmienić głośności dźwięku, który już leci. Dlatego głośność z GUI/komendy
leci osobnym kanałem **`raft:musicvol`** (float 0–1, 4 bajty) do MODA
klienckiego (raft-hud ≥ z mixinem SoundEngineMixin):

- `RaftMusicVolumeSender` (plugin) wysyła wartość (float 0–1) przy zmianie
  suwaka i przy pierwszym ticku gracza w świecie Raft (stan ustawienia po
  powrocie); przy wyjściu ze świata plugin tylko czyści cache, więc po powrocie
  wartość leci ponownie,
- `SoundEngineMixin` (mod) traktuje ją jako głośność **obowiązującą** i podstawia
  ją za tę zapieczętowaną w pakiecie: w `SoundEngine.calculateVolume(instance)`
  mnoży wynik przez `serwerowa / zapieczętowana` — filtr obejmuje wyłącznie
  identyfikatory `raft:music.*` (namespace `raft` + ścieżka `music.`, kropka —
  waniliowe `music.game` zostaje nietknięte),
- po odebraniu pakietu mod woła waniliowe `SoundManager.refreshCategoryVolume(MUSIC)`,
  które przelicza KAŻDY grający kanał (potwierdzone w bytecode 26.3:
  refreshCategoryVolume → calculateVolume → Channel.setVolume) — **efekt:
  zmiana słychać natychmiast, bez restartu utworu**, plan cyklu bez zmian,
  a utwór startuje z głośnością z pakietu (ścieżka startu w wanilii liczy
  głośność inną metodą, więc nasza korekta jej nie dubluje),
- **czego mod NIE robi:** nie woła `updateCategoryVolume(MUSIC, suwak)` — to
  ustawia `SoundEngine.gainBySource[MUSIC]`, czyli waniliowy gain wyciszania
  muzyki z `MusicManager.fadePlaying`, więc nadpisałoby go suwakiem i ściszyło
  też waniliową muzykę (podwójny suwak na kategorii MUSIC),
- waniliowy suwak „Muzyka” klienta mnoży się z serwerową głośnością (dwa
  niezależne pokrętła),
- gracz BEZ moda: kanał ignorowany (payload nieznany klientowi); głośność 0.0001
  przy wyciszeniu daje ciszę, zmiany głośności działają od następnego utworu.

## Wyciszanie i uwagi

- Brak plików .ogg w paczce = klient milczy (identyfikator bez dźwięku nie
  rzuca błędów w grze ani w logu serwera) — dopóki właściciel nie doda tracków,
  system jest niewidoczny.
- Zasada ciszy: system nie pisze nic na czacie poza odpowiedziami komendy
  `/muzyka` i nic do logu serwera.
- Rozmiar paczki: muzyka to jedyne, co realnie ją napuchnie (2–4 MB/utwór);
  wpływa na czas dołączania graczy.
- Mod (`pack/src/main/resources/assets/raft/`) NIE wymaga zmian — gra plugin
  zwykłym pakietem dźwięku; kopiowanie `sounds.json` do jara moda nie jest
  potrzebne (mod nie używa tych dźwięków).
