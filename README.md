# Villager Defense — plugin dla Paper 26.2

Gotowy projekt Maven. Ja nie mogłem go skompilować w swoim środowisku (brak dostępu do
`repo.papermc.io`), więc zrób to Ty — zajmuje to dosłownie kilka sekund.

## 1. Wymagania
- **JDK 25** (`java -version`) — Minecraft/Paper 26.x wymaga teraz Javy 25, starsza (np. 21) nie zadziała
- **Maven** (`mvn -version`) — jeśli nie masz, pobierz z https://maven.apache.org/download.cgi
  (albo `apt install maven` / `brew install maven`)
- Serwer **Paper 26.2** (https://papermc.io/downloads) — pobierz `paper-26.2-*.jar`

## 2. Kompilacja
W folderze `villager-defense/` (tam gdzie jest `pom.xml`):

```bash
mvn clean package
```

Gotowy plik pojawi się jako:
```
target/VillagerDefense.jar
```

## 3. Instalacja na serwerze
1. Uruchom serwer Paper 26.2 przynajmniej raz (żeby powstał folder `plugins/`).
2. Zatrzymaj serwer.
3. Wrzuć `VillagerDefense.jar` do folderu `plugins/`.
4. Odpal serwer ponownie — powstanie folder `plugins/VillagerDefense/` z plikami
   `config.yml`, `arenas.yml`, `players.yml`.

## 4. Budowa areny (jako OP, w grze)
```
/def create arena1
/def setlobby arena1        <- stań w miejscu lobby (oczekiwania) i wpisz
/def addplayerspawn arena1  <- stań w miejscu, gdzie mają się teleportować gracze na start rundy
                               (powtórz kilka razy w różnych miejscach dla większej liczby graczy)
/def addvillagerspawn arena1 <- stań w miejscu, gdzie mają spawnować się wieśniacy do obrony
                               (powtórz kilka razy)
/def addzombiespawn arena1  <- (opcjonalnie) osobne punkty spawnu mobów/zombie;
                               bez nich moby spawnują się przy wieśniakach
                               (powtórz kilka razy w różnych miejscach)
/def setend arena1          <- miejsce, do którego gracze wracają po zakończeniu gry
/def setminmax arena1 2 8   <- (opcjonalnie) min/max graczy
```

Powtórz dla drugiej mapy, np. `arena2`.

Arena jest gotowa do gry, gdy ma ustawione: lobby, przynajmniej 1 punkt spawnu gracza
i przynajmniej 1 punkt spawnu wieśniaka.

## 4b. System instancji — osobne gry na tej samej mapie

Chcesz, żeby dwie grupy graczy mogły grać równolegle na tej samej mapie (np.
pustynnej), ale każda w OSOBNEJ kopii, nie widząc się nawzajem? Oznacz arenę jako
**szablon** — od tej pory każda grupa dołączająca dostaje własną, tymczasową kopię
mapy, tworzoną w locie i usuwana po zakończeniu gry:

```
/def instancja desert on      <- (jako OP) arena desert staje się szablonem
/def instancja desert off     <- wyłącz szablon (wraca zwykłe dołączanie)
```

Jak to działa:
- Gracze nadal piszą `/def join desert` (albo klikają tabliczkę) — plugin sam
  kieruje ich do instancji `desert_inst1`, `desert_inst2`, ... (nazwa widoczna
  w komunikacie przy dołączeniu).
- Instancja = sklonowany świat (Paper `WorldCreator.copy`) + kopia konfiguracji
  areny (te same spawny, min/max graczy, teren zrzutu). Mapa szablonu zostaje
  nietknięta.
- Po zakończeniu gry instancja znika: świat jest wyładowywany, a folder kasowany.
  Gdy ktoś dołączy ponownie, dostaje świeżą kopię.
- Limit równoczesnych instancji jednego szablonu: `instances.max-per-template`
  w config.yml (domyślnie 5, `0` = bez limitu). Po przekroczeniu nowi gracze
  dostają komunikat „zbyt wiele gier na tej mapie".
- Pozostałości po awarii serwera (foldery `*_inst<numer>`) są kasowane
  automatycznie przy starcie.

Uwaga: klonowanie świata jest synchroniczne — duża mapa może na chwilę
„zamrozić" serwer przy tworzeniu instancji. Trzymaj mapy małe.

## 4c. Tabliczki dołączania i rotacji map

Tabliczki stawiasz w grze (wymagane `vd.admin`); pierwszy wiersz decyduje o typie
(wielkość liter bez znaczenia). Całość renderuje plugin — po utworzeniu tabliczki
nie edytuj jej ręcznie.

| Pierwszy wiersz | Reszta tabliczki | Działanie |
|---|---|---|
| `[vd]` | linia 2: nazwa areny | klasyczna tabliczka dołączania do JEDNEJ areny |
| `[vd:map]` | linie 2-4: nazwy map (max 3, puste pomijane) | rotacja z RĘCZNĄ pulą - wszystkie wpisane mapy muszą istnieć |
| `[vd:map:*]` | linie 2-4 puste (ignorowane) | rotacja z AUTOMATYCZNĄ pulą - opis niżej |

Wspólny wygląd (4 linie): `1: [Arena]` albo `[Mapa]`, `2: nazwa mapy`,
`3: (x/y) graczy`, `4: Kliknij`. Dla szablonu licznik x SUMUJE graczy ze
wszystkich działających instancji, więc pokazuje realne zapełnienie.
Prawy klik = dołączenie do wyświetlanej areny (przy szablonie plugin sam
kieruje do instancji).

**Rotacja:** gdy na mapie wyświetlanej na tabliczce STARTUJE gra, tabliczka
losuje inną mapę z puli (nowe grupy trafiają na wolną). Wyświetlana mapa jest
zapisywana w `plugins/VillagerDefense/signs.yml` i przeżywa restart.
Gdy pula się opróżni (mapy usunięte), tabliczka pokaże `[Mapa]` / `?` /
`Brak map w puli` - pojawi się sama, gdy któraś mapa z powrotem będzie gotowa.

**Pula automatyczna `[vd:map:*]`** buduje się sama z aren, które SĄ szablonami
(`/def instancja <arena> on`) i są GOTOWE do gry (`isReadyToUse()`: lobby albo
punkt końcowy + min. 1 spawn gracza + min. 1 spawn wieśniaka, ustawiane
komendami z pkt. 4). Klony instancji są pomijane. Białą listę `rotation.auto-maps`
w `config.yml` zawężasz kandydatów (pusta = wszystkie). **Ważne:** tabliczka
auto pokazuje WYŁĄCZNIE gotowe szablony - świat, w którym nie zdefiniowano
areny komendami `/def`, nigdy się na niej nie pojawi. Usunięcie szablonu,
wyłączenie trybu instancji albo usunięcie spawnów = mapa znika z puli i
w jej miejsce od razu losuje się inna.

## 5. Granie
```
/def join arena1     - dołącz do gry
/def kit             - otwórz wybór kitu (Wojownik / Tank / Łucznik / Inżynier / Asasyn / Magik / Medyk)
                       LPM na klasie = podgląd jej przedmiotów (GUI 3x9), PPM = wybór kitu
                       (podgląd działa też poza areną, dla każdego gracza)
/def leave           - opuść grę
/def top             - ranking TOP 10 graczy
/def stats           - Twoje statystyki

/arena addpoints <liczba> [gracz] - (tylko OP, na arenie) dodaje punkty sesji (te za
                       zabijanie mobów, do wydania w sklepie) - do testów bez zmieniania cen;
                       bez podanego gracza punkty dostaje operator (musi grać na arenie)
```

W trakcie gry: kliknij PPM kompasem w ekwipunku, aby otworzyć sklep za punkty zdobyte
za zabijanie mobów.

W sklepie jest też panel **Ustawienia** (lewy dolny róg, oko endera) z per-graczowymi
preferencjami zapisywanymi w `players.yml`:
- **Cząsteczki** — ilość cząsteczek kategorii (ogólne, wieżyczki, klasy, gracze,
  umiejętności, fajerwerki, zrzut): klikaj betonowy wskaźnik, aby zmniejszać 100→0%.
- **Dźwięki** — głośność kategorii (GUI, moby z fal, moby po stronie gracza, wieżyczki,
  niestandardowe przedmioty) oraz podmenu **Zrzut zaopatrzenia** z głośnością dźwięków
  faz zrzutu (nadchodzi / wylądował / znika). Klikaj betonowy wskaźnik, aby cyklicznie
  zmieniać 0→25→50→75→100%.
- **Powiadomienia** — kanały: czat (ogłoszenia rund, zrzuty, długie komunikaty),
  action bar (krótkie komunikaty) i ekran (zarezerwowane). Wskaźnik przełącza kanał
  (100% włączony / 0% wyłączony); przy wyłączonym action barze krótkie komunikaty
  trafiają na czat, żeby nic nie zginęło.

## 6. Konfiguracja (`plugins/VillagerDefense/config.yml`)
Możesz zmienić: liczbę mobów na falę (sekcja `waves` - gra działa w SEGMENTACH fal
`mob-count-segments`: każdy segment ma swoją bazę `base-count` i długość `length`;
wewnątrz segmentu każda fala losuje 50/50: +1 albo +2 moby; po ostatnim segmencie
fale idą dalej automatycznie - segmenty po `mob-count-tail-length` fal, a baza
rośnie o 1), punkty za każdy typ moba, ceny w sklepie, liczbę wieśniaków,
długość odliczania i przerw między rundami. Sprzęt mobów (broń/zbroja + % mobów
z fali, które go noszą) ustawiasz w `MobSpawner.java` (tabela `EQUIPMENT_TIERS`),
a zaklęcia na nim - w stałych `SHARPNESS_*`/`PROTECTION_*`/`FIRE_ASPECT_*`:
Ostrość od fali 41, Ochrona od fali 60 (+1 poziom co 30 fal), a Ognisty miecz
(40% szans od fali 41, +0,5%/rundę, max 100% = zawsze) - mają je tylko moby
ze sprzętem. Po zmianach:
`/def reload` (jako OP) albo restart serwera.

**Zrzut zaopatrzenia** (`supply-drop`): co `interval-seconds` (180 s = 3 min) jest
`chance-percent` (10%) szansy, że na arenie wyląduje skrzynia z łupami (znika po
`lifetime-seconds`). Zasięg lądowania ustawiasz kordynatami `min-x/max-x/min-z/max-z`
(powinny pokrywać całą mapę areny; `world` - świat areny, puste = świat spawnów).
Dla NIEquadratowych map możesz zamiast tego wyznaczyć wielokąt (dowolny kształt) -
chodzisz po granicy terenu i dodajesz punkty: `/def miejsce_zrzutu dodaj`, podgląd
`/def miejsce_zrzutu lista`, wizualny podgląd w grze `/def miejsce_zrzutu podglad`
(biała chmura przy każdym wierzchołku, na Twojej wysokości, 20 s), czyszczenie
`/def miejsce_zrzutu czysc`. Możesz też wyciąć DZIURĘ w terenie (góra, jezioro, wioska -
obszar, w którym zrzut nie wyląduje): `/def miejsce_zrzutu dziura nowa`, potem
`dziura dodaj` wokół każdego miejsca - dziur może być WIELE (np. każde jezioro osobno).
Podgląd w grze: `/def miejsce_zrzutu podglad` (białe chmury przy wierzchołkach terenu)
i `/def miejsce_zrzutu dziura podglad [numer]` (czerwone partikle wykluczonego obszaru).
Woda i lawa są zawsze pomijane - zrzut nigdy nie ląduje na wodzie.

**Teren zrzutu jest WŁASNOŚCIĄ KAŻDEJ ARENY.** Wielokąt i dziury zapisują się per
arena w `arenas.yml` (`arenas.<nazwa>.supply-drop`), więc każda arena może mieć inny
kształt terenu. Komendy przyjmują opcjonalną nazwę areny na początku:
`/def miejsce_zrzutu <arena> dodaj` - bez nazwy użyta jest arena, na której stoisz
(z konsoli trzeba podać nazwę). Wartości `supply-drop.polygon` / `supply-drop.excluded`
w `config.yml` to tylko GLOBALNY fallback dla aren bez własnego terenu.
Test: `/def supplydrop <arena>` (jako OP) wymusza zrzut, żeby sprawdzić kordynaty.
Każdy gracz ustawia głośność dźwięków poszczególnych faz zrzutu (nadchodzi / wylądował /
znika) w sklepie: Ustawienia -> Dźwięki -> Dźwięki zrzutu zaopatrzenia.

## 7. Pędzel Budowlany (brush) - szybkie tworzenie terenu aren

```
/brush give                                  - dostajesz drewnianą motykę-pędzel
/brush sphere <materiał> [promień]           - pełna kula z jednego materiału
/brush random <mat1,mat2,mat3> [promień]     - kula z losowym rozkładem podanych materiałów
/brush overlay <rdzeń> <wierzch> [promień] [grubość]
                                              - kula z "rdzeniem" i cieńszą warstwą wierzchnią
                                                (np. rdzeń DIRT, wierzch GRASS_BLOCK -
                                                wygląda jak naturalne wzgórze)
/brush smooth [promień] [iteracje]           - wygładza teren wokół klikniętego miejsca
                                                (uśrednia wysokość, usuwa nierówności)
/brush radius <liczba>                       - szybka zmiana promienia bieżącego brusha
/brush info                                  - pokazuje aktualne ustawienia
```

Trzymając pędzel (drewnianą motykę oznaczoną jako "Pędzel Budowlany"), kliknij **PPM w blok**,
aby zastosować aktualnie wybrany brush wyśrodkowany na tym bloku. Wymaga uprawnienia `vd.admin`.

Przykład: chcesz zrobić naturalne wzgórze pod areną:
```
/brush overlay dirt grass_block 5 1
```
potem kliknij PPM w ziemię — dostaniesz kulę z ziemi (środek) i trawy (wierzchnia warstwa),
promień 5, grubość warstwy wierzchniej 1 blok.

Nazwy materiałów podajesz jak w grze/komendach vanilla, np.: `stone`, `dirt`, `grass_block`,
`sand`, `oak_planks`, `cobblestone`, `mossy_cobblestone` itd. (bez `minecraft:` na początku).

```
pl.vd.VillagerDefensePlugin   - główna klasa, spina wszystko
pl.vd.arena.*                 - model areny (Arena, ArenaState) + zapisywanie do arenas.yml
pl.vd.game.*                  - GameManager (cały przebieg gry: countdown, fale, koniec),
                                 MobSpawner (dobór i spawn mobów w falach)
pl.vd.kits.*                  - kity (Wojownik, Tank, Łucznik, Inżynier, Asasyn, Magik, Medyk) + GUI wyboru
pl.vd.shop.*                  - sklep w grze (GUI + zakupy za punkty z rundy)
pl.vd.player.*                - trwałe statystyki graczy (players.yml) do rankingu /def top
pl.vd.commands.VDCommand      - cała komenda /def
pl.vd.commands.BrushCommand   - komenda /brush (konfiguracja pędzla)
pl.vd.brush.*                 - logika brushy: kula, losowy, nakładka, wygładzanie
pl.vd.listeners.*             - obsługa zabójstw (punkty), opuszczenia gry, kliknięć w GUI,
                                 blokady niszczenia/stawiania bloków w arenie, kliknięć pędzlem
```

## 8. Klasy z umiejętnościami (np. Magik)

Do kitów dorzuciłem system umiejętności:
- **Magik** — różdżka (Blaze Rod) strzelająca laserem: kliknij PPM trzymając ją w ręce, zada 2.5 obrażeń
  pierwszemu trafionemu mobowi w linii wzroku (zasięg 6 bloków), z 1s odnowieniem. Działa tylko w trakcie gry.
- **Asasyn** — dash: naciśnij 2 razy W w odstępie 0.1–0.5 s, a przeskoczysz 2.5 kratki do przodu
  (cooldown 5 s). Brak drugiego naciśnięcia w ciągu 0.5 s anuluje umiejętność. Działa tylko w trakcie gry.

Żeby dodać kolejną klasę z inną umiejętnością (np. Tank z tarczą odbijającą, Łucznik z podwójną
strzałą itd.), rozbuduj:
- `pl.vd.ability.AbilityType` — dodaj nową wartość enuma
- `pl.vd.ability.AbilityManager` — dodaj obsługę w `createAbilityItem`, `displayName`, `lore`,
  `cooldownMillis` i `use()`
- `pl.vd.kits.KitManager` — dodaj nowy kit korzystający z `abilityManager.createAbilityItem(...)`

Daj znać, jeśli chcesz, żebym rozbudował któryś z tych elementów albo dodał kolejne klasy.

## 9. Stojak (armor stand) — budowanie obiektów wizualnych

`/stojak` to narzędzie do budowania dekoracji z armor standów (np. wieżyczek, postaci,
statków): strojenie na żywo kliknięciami w grze, doczepianie bloków/przedmiotów
(BlockDisplay/ItemDisplay ze skalą, obrotem i przesunięciem), zapis całości do pliku
i stawianie jej w dowolnym miejscu — także przedmiotem w ręce. Wymaga `vd.admin`.

Najważniejsze komendy:
```
/stojak stworz [parametry]       - postaw stojak do strojenia (head:stone hand:bow sink:-1.0 ...; sink:-1.0 = stoi na wierzchu bloku)
/stojak part <blok|przedmiot> [x y z] - doczep część do stojaka, na który patrzysz
                                       (bez x y z: część pojawia się wyśrodkowana na stojaku/bloku, na bloku)
/stojak save <nazwa>             - zapisz całość (stojak + części) do pliku
/stojak saveitem                 - zapisz na przedmiot w ręce (prawy klik = postaw)
/stojak place <nazwa> [x y z]    - postaw zapisany stojak (x y z = blok, na którym ma stanąć - jak patrzenie na ten blok)
/stojak item <nazwa>             - przedmiot wiązany z zapisem (prawy klik = postaw)
/stojak copy / paste [x y z]     - kopiuj-wklej stojaka (z wysokością względem podłoża)
/stojak offset <x> <y> <z>       - trwałe przesunięcie stawiania (zapisane w config.yml)
/stojak bind / odzyskaj / remove / przywroc / list / delete / undo / snap / sprzataj / dajitemy / help / info
/stojak dajitemy                  - daj do ekwipunku itemy do edycji stojaków (po kolei, pierwszy = pierwszy wolny slot):
                                   STICK „Zmiana części stojaka", BONE „Obracanie części (dół)", BLAZE_ROD „Obracanie części (góra)",
                                   SUGAR „Przewijanie funkcji itemu/stojaka", REDSTONE „Podświetlenie stojaka",
                                   GUNPOWDER „Widoczność stojaka", BARRIER „Usuwanie części/całości stojaka"
/stojak help [nr]                  - pomoc podzielona na strony (5 komend/stronę); klikalna nawigacja na czacie: « poprzednia [1] [2] [3] następna »
/stojak znajdz                   - lista stojaków pluginu w świecie (po restarcie: nazwa, koordynaty, części)
/stojak liczba [promień]         - licznik encji (tylko operator): stojaki i części pluginu w promieniu (domyślnie 100 bloków) + wszystkie encje - ocena lagów
/stojak remove                   - usuń stojak, NA KTÓRY PATRZYSZ (celowo nie działa z nazwą)
/stojak delete <nazwa>           - usuwa zapis (plik presetu + postawione stojaki) po potwierdzeniu /stojak delete <nazwa> confirm (działa OD RAZU - celowo wpisane „confirm" jest potwierdzeniem; samo /stojak delete <nazwa> tylko pokazuje ostrzeżenie ważne 20 s). Bez confirm nic się nie dzieje; stojak usuniesz też patrząc na niego (/stojak remove)
/stojak przywroc                 - przywróć przypadkowo usunięty stojak (z częściami)
/stojak ponow                     - przywróć stan części sprzed ostatniego /stojak undo (ratuje po przypadkowym cofnięciu)
/stojak autosave                   - kopia zapasowa co minutę do JEDNEGO pliku (stojaki/autosave.yml, nadpisywany); zapisuje tylko stojaki BEZ nazwy (nazwane mają własny /stojak save <nazwa>); /stojak autosave place <nazwa> przywraca w oryginalne miejsce
/stojak nazwij <nazwa>           - nadaj nazwę stojakowi, na który patrzysz (od razu widoczna nad stojakiem); działa potem /stojak <nazwa> ... bez patrzenia; place stawia na arenie bez napisu
/stojak nazwa on|off              - pokaż / ukryj widoczność nazwy stojaka (sama nazwa zostaje); usun = usuń nazwę
/stojak duplikuj [materiał] [dx dy dz] - skopiuj część z identycznymi statystykami (skala/obrót/pozycja), kopia przesunięta o dx dy dz
/stojak duplikuj czesc <liczba> [dx dy dz] - kopia części o numerze z action bara (przewijanie STICKiem)
/stojak usun czesc <liczba> [materiał]    - usuń część o numerze z action bara (opcjonalnie tylko z danego materiału)
/stojak usun all czesc <materiał>        - usuń wszystkie części z danego materiału naraz (jedno undo je przywraca)
/stojak <nazwa stojaka> duplikuj czesc <liczba> - to samo bez patrzenia, po nazwie stojaka (/stojak nazwij <nazwa>; Tab podpowiada numery części)
/stojak stats [materiał]        - pokaż statystyki części (offset/skala/obrót)
/stojak informacja [materiał]    - informacje o części w liczbach (patrząc na stojak, na wybranej albo po materiale): skala X/Y/Z, kordynaty, obrót, skręt, pochylenie - jak w edycji STICKiem/SUGARem
/stojak lustro [x|z]             - lustrzane odbicie CAŁEGO stojaka (wszystkie części naraz + kierunek) - symetryczne wieżyczki; /stojak undo cofa
/stojak lustro czesc [x|z]        - lustrzana KOPIA wybranej części po drugiej stronie stojaka (offset/skala/obroty odbite - idealna symetria; część musi być odsunięta od środka); w SUGAR: tryb ODBICIE + SHIFT+BLAZE_ROD/BONE; /stojak undo cofa; aliasy: odbicie/odbij/mirrorpart
Wieżyczka (zapis z częściami obrotowymi, np. `wiezyczka.yml`) działa w pełni automatycznie, bez dodatkowych komend: `/stojak place wiezyczka` stawia ją, a góra SAMA obraca się (360°, płynnie) w stronę najbliższego moba w zasięgu 8 bloków i podąża za nim — **śledzi go wzrokiem nawet za blokiem**, a strzela (raz na 2 sekundy, 3.0 obrażeń + delikatny odrzut moba; nie rani graczy ani wieśniaków) **tylko wtedy, gdy ma do niego linię widzenia**: mob chowa się za ścianą → wieżyczka czeka, śledząc; mob się odsłania → natychmiast strzela; gdy zabije najbliższego, przechodzi do następnego. Wieżyczka NIE strzela żadną widoczną strzałą - wystrzał to tylko biały dym z lufy w kierunku spojrzenia + iskry przy trafieniu, więc nic nie może utknąć ani wystawać z wieżyczki. Namierza KAŻDEGO moba (zombie, szkielety, golemy, zwierzęta) oprócz graczy, wieśniaków i mobów po stronie gracza (oswojonych lub z tagiem `vd_sojusz`). Bez moba stoi. Przy każdym strzale górna część dostaje odrzut (lekko cofa się wzdłuż lufy i wraca po chwili), a nad wieżyczką wisi pasek HP `[250/250 ♥]` (osobny tekst, nie jak u wieśniaków - podąża za wieżyczką i odświeża się przy trafieniu). Wieżyczka ma 250 HP: zniszczyć ją może WYŁĄCZNIE mob, do którego sama strzeliła - TRAFIONY mob dostaje wieżyczkę jako cel i zaczyna ją atakować (waniliowe AI nie potrafi celować w stojaki, więc aggro jest wymuszane: cel + przyciąganie do blokady + cios wręcz co ~1 s, dzięki czemu mob faktycznie bije wieżyczkę przez barierę); gracze/wybuchy/ogień jej nie ranią, a krytyczne obrażenia w ogóle jej nie dotyczą. Przy 0 HP wieżyczka po prostu znika (bez animacji - wszystkie części i blokady sprzątają się razem z nią). HP przetrwa restart. Wieżyczka stawia się NA bloku, na który patrzysz (albo na wpisanym bloku: `/stojak place wiezyczka <x> <y> <z>` - podstawa ląduje na wierzchu tego bloku). Zapis usuniesz komendą `/stojak delete wiezyczka confirm` (działa od razu); stojącą wieżyczkę usuniesz też `/stojak remove` na patrzonym stojaku. Wokół wieżyczki stoją niewidzialne blokady (małe stojaki na krawędzi bloku) - moby zatrzymują się pół bloku od niej, zamiast wchodzić w wieżyczkę; sprzątają się same przy zniszczeniu. Wieżyczka staje DOKŁADNIE tam, gdzie celujesz (punkt trafienia celownika, np. krawędź bloku) — nie na środku bloku; stawianie wg celownika wyłączysz w config.yml (`stojak.turret-place-at-aim: false`). Przy DUŻEJ liczbie wieżyczek naraz obsługa odświeża się rzadziej, żeby nie obciążać serwera (config `stojak.turret-dynamic-tick`: >25 wieżyczek → co 2 ticki, >50 → co 4 ticki; prędkość obrotu zostaje ta sama).

Wieżyczkę można też kupić w sklepie areny (kategoria „Inne", **strona 2, obok TNT**, 10 pkt): dostajesz przedmiot-stojak „Wieżyczka" - prawy klik stawia wieżyczkę, a **przedmiot znika po postawieniu** (1 sztuka = 1 wieżyczka - nie da się go ponownie użyć). Może ją postawić KAŻDY gracz (nie tylko admin); admini stawiają ją też komendą `/stojak place wiezyczka`. Jeśli nie widzisz jej w sklepie, zaktualizuj wpis `shop.categories.other.items.wiezyczka` w config.yml (od najnowszej wersji plugin dokłada ją sam, gdy brakuje wpisu).
/stojak funkcja siedzenie [x y z] - postaw gotowe krzesło (mini niewidoczny stojak z siodłem; klik = siadanie, Shift = wstań - działa dla każdego gracza); zapisujesz i stawiasz jak normalny stojak: save/place/item/copy/paste zachowują działanie krzesła
/stojak funkcja tv [szer] [wys]    - postaw czarny ekran TV z czarnych banerów obok siebie (domyślnie 3x2; [x y z] na końcu = dokładne miejsce); każdy baner to osobna część - edytujesz i zapisujesz jak normalny stojak
/stojak funkcja lampa [wysokość]    - postaw wizualną lampę: 1 = świecący blok, 2 = ciemny słup + głowa (do 4); edytowalna (zamien czesc glowstone sea_lantern...) i zapisywalna jak normalny stojak
/stojak krzeslo [on|off]          - oznacz istniejący stojak jako siedzisko
```

**Zegar ze stojaka** — nazwij stojak `zegar_wskazowki` (`/stojak nazwij zegar_wskazowki`)
i doczep do niego 3 części-wskazówki w tej kolejności: **część 1 = godzinna**
(obraca się 30° dokładnie co godzinę), **część 2 = minutowa** (6° co minutę),
**część 3 = sekundowa** (6° co dokładną sekundę; np. `red_concrete`). Numeracja
części = ta z action bara `/stojak` (CZĘŚĆ 1, 2, 3...). Zegar startuje, gdy
rozpocznie się arena (w której świecie stoi), a po jej zakończeniu wskazówki
wracają do stanu początkowego.

Wskazówki obracają się parametrem **SKRĘT** (tryb SUGAR, `rotZ`) — dokładnie tym
samym, którym stroisz je w `/stojak` (BLAZE_ROD/BONE w trybie SKRĘT). Dzięki temu
zegar działa tak samo dla tarczy na ścianie, jak i na ziemi: ustaw każdą
wskazówkę skrętem na pozycję „12", a plugin od tej pozycji będzie ją obracał
(30°/godzinę, 6°/minutę, 6°/sekundę). Jeśli wskazówki kręcą się w złą stronę,
odwróć kierunek w config.yml (`zegar.reverse` — domyślnie `true`, czyli obrót
jest już odwrócony; ustaw `false`, żeby wrócić do poprzedniego kierunku).

**Ważne (plugin NIE rusza pozycji wskazówek):** przy obrocie skrętem (`rotZ`)
część kręci się wokół **własnego środka**, a wskazówki mogą być celowo oddalone
od stojaka (ustawiasz je w `/stojak` przez PRZESUNIĘCIE-X/Z). Dlatego domyślnie
plugin **nie przenosi** wskazówek — przy teście (`/stojak zegar test`) i starcie
areny każda kręci się dokładnie tam, gdzie ją ustawiłeś. Jedyna opcja, która
przesuwa wskazówki, to `zegar.center: true` (domyślnie wyłączona) — wyśrodkowuje
ich środek na osi stojaka (X = 0); włącz ją tylko, gdy wskazówki stoją blisko
stojaka i chcesz je automatycznie wycentrować.

**Alternatywny styl — „zamiatający" (`zegar.sweep: true`):** wszystkie
wskazówki idą prawdziwymi kątami zegara, bez przycinania do 10° — sekundowa 6°
co sekundę, minutowa 6° co minutę, godzinna 30° co godzinę (pełne koło
12 → 3 → 6 → 9 → 12); na każdej ćwiartce (90°) stara wskazówka „znika", a w tym
samym miejscu pojawia się
NOWA — druga kopia części (tworzona automatycznie, bez doczepiania czegokolwiek),
która obraca się dalej swobodnie parametrem SKRĘT. W tym samym momencie,
w którym wskazówka się obraca, część dostaje drobne przesunięcie w kierunku
chodu (rośnie równo od 0 do 0.05 na ćwiartkę): 12→3 idzie SKOSEM
(+X i +Y, np. wysokość −1 → −0.95 i przesunięcie X +4.47 → +4.52), 3→6 tylko
−Y (−0.95 → −1), 6→9 tylko −X (+4.52 → +4.47), 9→12 tylko +Y (−1 → −0.95).
Kolejne ćwiartki ciągną się po sobie — nowa wskazówka pojawia się tam, gdzie
poprzednia skończyła (ciągły chód wokół tarczy); dopiero na powrocie do 12 cykl
zaczyna się od nowa od pozycji bazowej (wysokość i przesunięcie X ustawione
w /stojak). Dzięki temu wskazówka trzyma się tarczy, nawet gdy jej środek jest
przesunięty względem osi stojaka. Sekundowa odświeża się co 15 s, minutowa co
15 min, godzinna co 3 h. Domyślnie `false` (zwykły, płynny zegar).

Podgląd bez areny: `/stojak zegar test` — przez 1 minutę wskazówki kręcą się do
przodu z prędkością z config.yml (`zegar.test-speed`, domyślnie 1.0 = normalna,
prawdziwe tempo 1:1), a `/stojak zegar anuluj` kończy test i przywraca
wskazówki do pozycji startowej. Gdy arena rusza na tym stojaku, test jest
przerywany automatycznie.

Styl obrotu przełączysz w grze bez config.yml: `/stojak zegar tryb normalny`
(pełne koło, prawdziwy czas) albo `/stojak zegar tryb zamiatajacy` (pełne koło
z odświeżeniem na każdej ćwiartce — patrz `zegar.sweep`). Wybór zapisuje się na
stojaku (przetrwa restart) i działa od razu na bieżącym teście.
```

Po postawieniu klikaj stojak trzymając przedmioty: **STICK** = zmiana części,
**SUGAR** = zmiana osi/trybu, **BLAZE_ROD/BONE** = plus/minus, **GUNPOWDER** = widoczność
korpusu, **REDSTONE** = podświetlenie, **PAPER** = wypisanie wartości, **BARRIER** = usuń
część/stojak (SHIFT zmienia tryb drobny albo cofa wybór). Tryby SUGAR dla części:
**SKALA** (całość naraz), **ROZCIĄGNIJ-X/Y/Z** (rozciąganie bloku tylko w jedną oś -
podłużny blok w bok albo wysoki w górę), OBRÓT, POCHYLENIE, **SKRĘT** (obrót wokół
własnej długiej osi - np. przekręcenie patyka, żeby szersza powierzchnia patrzyła
w bok), WYSOKOŚĆ, PRZESUNIĘCIE-X/Z, **ODBICIE**
(SUGAR na części: BLAZE_ROD = odbicie X, BONE = odbicie Z - przewraca teksturę części
w miejscu), **LUSTRO** (BLAZE_ROD = lustrzana KOPIA części po drugiej stronie w X,
BONE = w Z - odbity offset/skala/obroty, idealna symetria bez SHIFT; część musi być
odsunięta od środka) oraz **GRUPA**: BLAZE_ROD/BONE przełączają
między częściami z tego samego bloku, a w action
barze widać pozycję w grupie, np. `CZĘŚĆ 5 (stone 2/4, SKALA)` — 2. kamień z 4.

Pełną instrukcję znajdziesz w pliku
`plugins/VillagerDefense/stojak-info.txt`. Zapisane stojaki to
`plugins/VillagerDefense/stojaki/<nazwa>.yml`.

Kod: `pl.vd.stand.*` — `ArmorStandTuner` (strojenie i części), `StandPreset` (format
zapisu), `StandPresetManager` (pliki/przedmioty), `pl.vd.commands.StandCommand`
(komenda), `pl.vd.listeners.StandListener` (kliknięcia).

## Co możesz dodać/rozbudować dalej
- Więcej kitów (edytuj `KitManager.registerDefaults()`)
- Trwałe punkty na start gry (obecnie punkty w sklepie są tylko z bieżącej rundy)
- Efekty dźwiękowe/tytuły na start rundy, boss bar z licznikiem mobów
- BossBar pokazujący pozostałe moby zamiast samych wiadomości na czacie

Kod systemu instancji: `pl.vd.instance.InstanceManager` (klonowanie światów,
usuwanie instancji, sprzątanie po restarcie), routing w `GameManager.addPlayer`,
komenda `/def instancja <arena> on|off` w `VDCommand`.

Daj znać, jeśli chcesz, żebym rozbudował któryś element (np. trwałą walutę,
więcej kitów, systemy odrodzeń graczy w trakcie rundy, itp.).
