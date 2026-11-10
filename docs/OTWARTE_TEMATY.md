# Otwarte tematy — pełna lista z kryteriami

Przeniesione z `AGENTS.md` §10 (stan 2026-10-05). Skrócona lista zadań jest w `AGENTS.md` §10;
tutaj są kryteria odbioru i szczegóły, żeby temat nie ginął między wiadomościami.

## Wymaga wejścia do gry — nic nie zostało sprawdzone u gracza

- paski życia/głodu/pragnienia na tratwie i ich zniknięcie po wyjściu z trybu,
- animacja przedmiotu w ręce i w ekwipunku,
- custom modele przedmiotów,
- anteny, kable i odbiornik (ocena wzrokowa),
- pobranie nowej paczki przez klienta,
- fundamenty i skrzynie na pokładzie po fixie 2026-10-05 (PPM w powietrze i w kolizję; skrzynie =
  komórka nad deską, GUI klikiem w skrzynię, sieć z przedmiotem budowlanym w ręce nadal działa),
- hak po fixie 2026-10-05: niski rzut (blisko tratwy, przez pokład) ma lecieć swobodnie i wpaść
  do wody — nie może wisieć w powietrzu; wysoki rzut i łapanie przedmiotów bez zmian,
- skrzynie po fixie 2026-10-05 (v36/v37): klik z pustym kursorem ma brać przedmiot NA KURSOR
  (nie wrzucać go do ekwipunku), odkładanie do skrzyni i shift-klik waniliowo, a zawartość ma
  przetrwać zamknięcie GUI i restart serwera (`raft-saves.yml` → `chest-contents`); przy dwóch
  graczach na tej samej skrzyni oba GUI mają pokazywać ten sam kontener. Druga osoba nie jest
  jeszcze dostępna na serwerze, więc ten punkt zostaje na później,
- **restart serwera dla każdej nowej rzeczy z §12** (trwałość): zrobić w grze → wyjść z gry →
  zrestartować serwer → wrócić i sprawdzić, że stan jest ten sam. Pierwsza okazja: zawartość
  skrzyni (v36/v37) — bez tego fix nie jest potwierdzony.

### Obrót budowli (v39)

Kółko w górę/w dół w trybie budowy (aktywny duch z GUI młota) ma obracać skrzynię o 90° w prawo
/lewo, klawisze 1–4 mają ustawiać N/E/S/W, Shift+kółko ma wracać do kierunku z patrzenia, a
Alt+kółko (mod) ma obracać bez otwierania GUI. Po postawieniu obróconej skrzyni kierunek ma wrócić
po restarcie serwera (`raft-saves.yml`, 6. pole komórki) — bez tego fix nie jest potwierdzony.
Sterowanie: `docs/RAFT_BALANCE.md` §1a.

### Drewniane schody (v40)

W GUI młota (shift+PPM) kategoria „Inne” ma pokazywać Drewniane schody (`wooden_stairs`, CMD 7038);
po wyborze duch ma stać NA pokładzie (nad deską, jak skrzynia), a kółko i presety 1–4 mają obracać
model schodów. Po postawieniu kierunek ma wrócić po restarcie serwera (`raft-saves.yml`), a siekiera
ma rozbierać komórkę `STAIRS` tak samo jak skrzynie. Bez tego fix nie jest potwierdzony.

### Podpora piętra (v41)

Kategoria „Inne” w GUI młota ma pokazywać Podporę piętra (`wooden_support`, CMD 7039); PPM na
pokładzie ma stawiać słupek w narożniku 4 komórek (1 encja, BEZ kolizji — da się przez niego
przejść), rozciągnięty w pionie do 2 kratek. Celując w słupek podłogą ma stanąć komórka piętra
(Y=3), a dalej piętro rośnie maks. 1 blok od słupka (kolejna podpora co 2 kratki). Po restarcie
serwera podpory i piętro mają wrócić (`raft-saves.yml` → `supports`). Sterowanie/config:
`docs/RAFT_BALANCE.md` §1b.

### Belki podpiętrza (v42)

Pod każdą komórką piętra ma być widoczna rama z belek (ciemne deski 1 × 0.25 × 0.25) tuż pod
platformą — belki sąsiadujących komórek mają się stykać i razem z wierzchem słupka tworzyć
kratownicę rozchodzącą się od podpory na 4 komórki wokół niej (platforma nie może wyglądać na
wiszącą w powietrzu). Wspólna krawędź dwóch komórek = JEDNA belka (platforma 2×2 wokół jednej
podpory = 12 belek). Do tego reguła pięter ma być twarda: piętro staje TYLKO w 4 komórkach wokół
słupka, a próba dobudowy dalej niż podpora ma nie stawiać nic (duch gaśnie).
`raft-support.beams: false` ma usuwać belki bez restartu. Config/geometria:
`docs/RAFT_BALANCE.md` §1b.

### Piętrowa Podłoga (v45)

Kategoria „Inne” w GUI młota ma pokazywać trzeci przedmiot — Podłogę (`wooden_floor`, CMD 7040,
9× deski, `/raft przedmiot wooden_floor`). Ma się nią dać postawić WYŁĄCZNIE piętro (celowanie
w słupek podpory albo w gotowe piętro), a przy próbie położenia jej na wodzie / jako pokładu
tratwy NIE może się pokazać podgląd-duch (żadnej możliwości budowy), bo to przedmiot piętrowy
(`RaftBuildListener.isDeckBuild` = false). Drewniany i kamienny fundament mają dalej działać
i jako pokład, i jako piętro, a komórka postawiona Podłogą ma być zwykłą podłogą piętra
(OAK_PLANKS, w 4 komórkach wokół podpory, z belkami podpiętrza). Sprawdzić też, że Podłoga nie da
się postawić na samym pokładzie tratwy, a po restarcie serwera piętro z niej zbudowane wraca
(`raft-saves.yml`). Do tego przepis w GUI `/wytwarzanie` → kategoria „Materiały / Zasoby” →
Podłoga (4× deska + 1× lina, jak Żagiel) ma pozwalać zdobyć ją BEZ komendy: po kliknięciu
składniki mają zniknąć z ekwipunku, a wynik (9× Podłoga) trafić do plecaka, z ✔/✘ odświeżanym
na bieżąco.

### Zawalanie bez podparcia + odzysk materiału (v43/v44)

LPM siekierą w słupek ma zdjąć też komórki piętra, które trzymał (plus wszystko, co na nich
stało — skrzynie/schody), razem z ich belkami, więc po rozbiórce nic nie może zostać wiszące
w powietrzu; komórka stojąca jeszcze w narożniku INNEGO słupka ma zostać. To samo sprzątanie ma
działać przy ZWYKŁEJ rozbiórce komórki — rozbicie deski nie może zostawiać skrzyni na pustce.
Runięcie ma być OPÓŹNIONE o `raft-support.collapse-delay-ticks` (domyślnie 12 ticków = 0,6 s:
widać brak słupka, a platforma jeszcze wisi) i ma grać dźwięk + cząsteczki za każdą komórkę.
Każda zawalona komórka ma WYPADAĆ DO WODY jako przedmiot do odzyskania (1× jej materiał;
zawartość skrzyni osobno, jak przy zwykłej rozbiórce), a podstawa tratwy i pokład mają nie
znikać. Sprawdzić też, że podstawa wstawiona w trakcie opóźnienia ratuje piętro, że po restarcie
serwera stan jest spójny (piętro + podpory z `raft-saves.yml`) i że przy `collapse-delay-ticks: 0`
runięcie jest natychmiastowe. Bez tego fix nie jest potwierdzony.

## Co blokuje resztę

- repo nie ma `remote` → mod nie trafia do graczy (`tools/publish-all.sh` działa tylko z `--no-push`),
- brak commitów od 2026-09-16 — cała praca jest niezacommitowana.

## Porównanie mechanik z oryginalnym Raftem (2026-09-24, przegląd kodu)

**Działa:** dryf z prądem (zmiana co 8 min), przenośnik graczy, budowanie (podłogi/ściany/
skrzynie 18–54), sieć zbierająca, hak (8 szt. + beczki), pływające śmieci i beczki z lootem,
pragnienie (jeden licznik + picie), HUD moda, tlen (butla 30 s/maska), wiosłowanie, odbiornik
+ anteny + kable + wiatrak + baterie, drzewa, crafting (GUI `/wytwarzanie`: kategorie → przepisy,
składniki pobierane z ekwipunku — pierwszy przepis: Nawigacja → Żagiel), muzyka (`MusicManager`:
losowe utwory z paczki co 3–8 min, ustawienia per gracz `/muzyka` — włączona/głośność/ton/
częstotliwość/zapętlenie/wybór utworu w `raft-players.yml`; identyfikatory w `raft-music.tracks`
MUSZĄ mieć kropkę i zgadzać się z kluczem w `sounds.json` paczki (`raft:music.utwor_1` — wanilia
też pisze `music.game`; forma z ukośnikiem = klient nie zna zdarzenia i NIC nie gra); głośność
NA ŻYWO przez kanał `raft:musicvol` → mod (`SoundEngineMixin` podstawia ją za zapieczętowaną
w `calculateVolume`, tylko namespace `raft` + ścieżka `music.`; `SoundManager.refreshCategoryVolume`
przelicza grające kanały — zmiana suwaka słychać natychmiast, bez restartu utworu;
`RaftMusicVolumeSender` po stronie pluginu, stan per gracz czyszczony przy wyjściu), GUI 6×9:
rzędy 2–5 = płyty utworów z `raft-music.tracks` — każda płyta to PAPIER z własnym CMD (6100+i,
lore wypisuje CMD pod własną teksturę), LPM = zagraj utwór RAZ (jednorazowy wybór
`music-selected-track`, czyści się po odtworzeniu; cugla potem może go zapętlić), Shift+LPM wraca
do losowania; dolny rząd: BETONOWY suwak głośności (LPM +1/Shift+LPM +10, PPM −1/Shift+PPM −10,
slot gry 54; beton = poziom: zielony 100%, jasnozielony 75%, żółty 50%, pomarańczowy 25%, czerwony
0%), lejek z częstotliwością (LPM w górę/PPM w dół po liście natychmiastowo→często→normalnie→
rzadko→nigdy, slot gry 47, wybrana opcja pogrubiona), cugle = zapętlenie ostatniego utworu (klik
przełącza, stan w nazwie/lore, slot gry 48) + Wyjdź (nether star, slot gry 50); config
`raft-music`, `docs/MUZYKA.md`, tracki .ogg do dorzucenia), ustawienia Rafta (ZROBIONE 2026-09-27:
`/ustawienia` w świecie Raft otwiera własny panel — GUI 4×9, totalnie inny niż panel VD 54:
Dźwięki = otwiera PODMENU dźwięków (`DzwiekiGUI` 6×9: master `sounds-enabled` + 7 kategorii
`sounds-hook/net/debris/trees/devices/building/pickup`, slot gry 50 = Wyjdź), Powiadomienia =
przełącznik w `raft-players.yml` `notifications-enabled` (na razie przechowują stan; realne
wyciszanie `playSound` — do podłączenia), Muzyka = otwiera TO SAMO GUI co `/muzyka`, Język =
polski/angielski zapisywany globalnie do `players.yml` VD (klucz `language`, działa od razu w obu
trybach); klasa `pl.vd.raft.settings.UstawieniaGUI`, gałąź Rafta w `SettingsCommand`),
zaktualizowana 2026-10-05: tryb „natychmiastowo” gra utwór DO KOŃCA, a następny startuje od razu —
długość serwer bierze z `raft-music.track-seconds` (ręcznie) albo z pliku .ogg
w `raft-music.pack-dir` (nagłówek Vorbisa: sample rate + granule ostatniej strony; klasa
`TrackDurations`), z `default-track-seconds` w odwodzie; przerwa = długość ÷ ton, min. 3 s (patrz
`docs/MUZYKA.md`), głęboki ocean bez mobów.

**W połowie:** wyspy (rejestr `islands.yml` + radar, ale brak generowania/lootu/eksploracji),
radio i lornetka (przedmioty są, PPM = TODO), żagiel (fizyka + stawianie z przedmiotu ZROBIONE
2026-09-25: rozwinięty → napęd z wiatru, zwinięty → z prądu, PPM żaglem stawia/rozkłada-zwija,
Shift + PPM demontuje, pozycja i stan lecą do klienta kanałem `raft:sails` i mod rysuje żagiel
w świecie — zostało: geometria modelu (stub; animacja rozwijania ma już długość 20 ticków),
WERYFIKACJA RYSOWANIA W GRZE, ster/kierunek). Źródło przedmiotu bez `/raft` ZROBIONE 2026-09-26:
GUI `/wytwarzanie` → kategoria „Nawigacja” → Żagiel (CMD 4005) = 3× biała wełna + 3× deska +
1× lina; składniki liczone i pobierane z ekwipunku gracza), schody (czeka model od właściciela),
questy (definicje bez sprawdzania postępu — `checkQuestProgress` TODO), głód na tratwie (kod czyta
martwe `IslandManager.isOnRaft` — `createRaft` nie jest wołane, więc głód działa waniliowo).

**Brak:** rekin, oczyszczacz wody, gotowanie/ryby, uprawy, zwierzęta/mewa, piec do metalu,
ster/kotwica, radar dynamiczny (blips — statyczny ekran już jest), łup z głębin.

**Usunięte 2026-09-24:** cykl pogody Rafta (`WeatherState`, pętla w `GameManager`, sekcja
`weather` w `config.yml`, wpisy `weather-*` w `lang.yml`) — był tylko stanem w pamięci, nic nie
robił; waniliowy pogodowy game rule świata zostaje nietknięty.

**Proponowana kolejność:** rekin → oczyszczacz wody (jak wiatrak, 2×2) → wyspy (loot +
`markExplored` z dotarcia) → radio (PPM) → żagiel (geometria modelu + animacja rozwijania + ster).

**Niedokończone w kodzie (`RaftListener`):** PPM radio, PPM lornetka, picie w wodzie (w Raft słonej
wody nie wolno pić — decyzja, czy zostaje TODO, czy znika), „czy gracz jest na wyspie” (ruch /
stawianie / niszczenie bloków), topienie się. Łowienie jest ZROBIONE (`HookManager` przejął
spławik — TODO w kodzie jest nieaktualne).

## Schody tratwy na modelu + dwóch shulkerach

(`docs/SCHODY_TRATWY.md`) Ustalone, że to komórka `STAIRS` i że **model jest źródłem prawdy** dla
kolizji; czekamy na model od właściciela (razem z informacją, które półki mają dostać kolizję).
Przygotowane: `tools/model-shelves.sh`.

## Treść i grafika

- ekran radaru **jako dynamiczna tekstura** (`docs/ANIMACJE_I_RADAR.md` poz. 5 — nieruchomy radar
  w teksturze jest już zrobiony),
- tekstury dla liny/odpadków/gwoździ (modele już są, brak PNG),
- brakujące modele przedmiotów (lista w `docs/PRZEDMIOTY_I_MODELE.md`).

Faza 5 anten (model z Blockbencha) jest zrobiona; zostaje ocena wyglądu **w grze**.

## Modele urządzeń mają komplet materiałów (2026-09-23)

Odbiornik dostał pięć tekstur i przypisanie materiałów na wszystkich 912 ścianach
(`pack/tools/OdbiornikTextureGen.java` + `pack/tools/OdbiornikUvPaint.java`, szczegóły
w `docs/ODBIORNIK_RENDER.md` §1 i §4). Zasada na przyszłość: **tekstury i UV nadaje narzędzie,
nie ręka w Blockbenchu** — przy 152 elementach ręczne klikanie jest nie do powtórzenia, a przy
następnym eksporcie modelu zmiany by zniknęły. Nowe urządzenie zaczyna z `finished = false`
w `tools/ItemModelCheck.java` (brakujące tekstury są wtedy UWAGAMI), a po dorobieniu materiałów
przestawia się tę flagę na `true` (wtedy brak tekstury to BŁĄD). Od 2026-10-01 flaga nie istnieje —
walidator pilnuje zwykłych modeli fallback przedmiotów (tekstura brakująca to zawsze BŁĄD); siatki
urządzeń w świecie pilnuje osobno `check-device-mesh.sh`.

**Cztery kopie zasobów, jedna niespilnowana automatycznie** (`pack/` → `modpack` → `.minecraft` →
`pack/src/main/resources`, ta ostatnia to kopia w jarze moda). Trzy pierwsze wyrównuje
`tools/publish-pack.sh`, czwartej nie dotyka — dlatego `tools/check-item-model.sh` porównuje ją
bajt w bajt z paczką (model i każda użyta tekstura), gdy tylko istnieje. Sprawdzone testem
negatywnym: podmiana pliku w kopii moda daje `BLAD`.

**Drobiazgi:** Paper 10 buildów do tyłu (`pom.xml` i `start.bat` zmieniać razem), puste
`resource-pack-id` (2 ostrzeżenia Papera), legacy folder `plugins/VillagerDefense` ze zdublowanym
kluczem w YAML, `assets/raft/lang/pl_pl.json` w kopii modpacka (nie ma go w `pack/`).
