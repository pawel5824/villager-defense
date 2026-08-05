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
/vd create arena1
/vd setlobby arena1        <- stań w miejscu lobby (oczekiwania) i wpisz
/vd addspawn arena1        <- stań w miejscu, gdzie mają się teleportować gracze na start rundy
                               (powtórz kilka razy w różnych miejscach dla większej liczby graczy)
/vd addvillager arena1     <- stań w miejscu, gdzie mają spawnować się wieśniacy do obrony
                               (powtórz kilka razy — te same punkty służą też jako miejsca spawnu mobów)
/vd setend arena1          <- miejsce, do którego gracze wracają po zakończeniu gry
/vd setminmax arena1 2 8   <- (opcjonalnie) min/max graczy
```

Powtórz dla drugiej mapy, np. `arena2`.

Arena jest gotowa do gry, gdy ma ustawione: lobby, przynajmniej 1 punkt spawnu gracza
i przynajmniej 1 punkt spawnu wieśniaka.

## 5. Granie
```
/vd join arena1     - dołącz do gry
/vd kit             - wybierz kit (Wojownik / Łucznik / Tank) przed startem
/vd leave           - opuść grę
/vd top             - ranking TOP 10 graczy
/vd stats           - Twoje statystyki
```

W trakcie gry: kliknij PPM kompasem w ekwipunku, aby otworzyć sklep za punkty zdobyte
za zabijanie mobów.

## 6. Konfiguracja (`plugins/VillagerDefense/config.yml`)
Możesz zmienić: liczbę mobów na falę, punkty za każdy typ moba, ceny w sklepie,
liczbę wieśniaków, długość odliczania i przerw między rundami. Po zmianach:
`/vd reload` (jako OP) albo restart serwera.

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
pl.vd.kits.*                  - 3 startowe kity + GUI wyboru
pl.vd.shop.*                  - sklep w grze (GUI + zakupy za punkty z rundy)
pl.vd.player.*                - trwałe statystyki graczy (players.yml) do rankingu /vd top
pl.vd.commands.VDCommand      - cała komenda /vd
pl.vd.commands.BrushCommand   - komenda /brush (konfiguracja pędzla)
pl.vd.brush.*                 - logika brushy: kula, losowy, nakładka, wygładzanie
pl.vd.listeners.*             - obsługa zabójstw (punkty), opuszczenia gry, kliknięć w GUI,
                                 blokady niszczenia/stawiania bloków w arenie, kliknięć pędzlem
```

## 8. Klasy z umiejętnościami (np. Magik)

Do kitów dorzuciłem system umiejętności. Na start jest **Magik** — różdżka (Blaze Rod)
strzelająca laserem: kliknij PPM trzymając ją w ręce, zada 2.5 obrażeń pierwszemu trafionemu
mobowi/graczowi w linii wzroku (zasięg 25 bloków), z 1.5s odnowieniem. Działa tylko w trakcie gry.

Żeby dodać kolejną klasę z inną umiejętnością (np. Tank z tarczą odbijającą, Łucznik z podwójną
strzałą itd.), rozbuduj:
- `pl.vd.ability.AbilityType` — dodaj nową wartość enuma
- `pl.vd.ability.AbilityManager` — dodaj obsługę w `createAbilityItem`, `displayName`, `lore`,
  `cooldownMillis` i `use()`
- `pl.vd.kits.KitManager` — dodaj nowy kit korzystający z `abilityManager.createAbilityItem(...)`

Daj znać, jeśli chcesz, żebym rozbudował któryś z tych elementów albo dodał kolejne klasy.

## Co możesz dodać/rozbudować dalej
- Więcej kitów (edytuj `KitManager.registerDefaults()`)
- Trwałe punkty na start gry (obecnie punkty w sklepie są tylko z bieżącej rundy)
- Osobne areny per-świat zamiast współdzielonego świata
- Efekty dźwiękowe/tytuły na start rundy, boss bar z licznikiem mobów
- BossBar pokazujący pozostałe moby zamiast samych wiadomości na czacie

Daj znać, jeśli chcesz, żebym rozbudował któryś element (np. trwałą walutę,
więcej kitów, systemy odrodzeń graczy w trakcie rundy, itp.).
