# Datapack `raft-height` — szczegóły i pomiary

Przeniesione z `AGENTS.md` §8. Zasada jest w `AGENTS.md` §8; tutaj są dowody i pułapki.

Datapack z limitami wysokości świata Raft (Y −64..287, `height=352`) jest w JAR-ie
(`src/main/resources/datapacks/raft-height/`), a plugin wdraża go **tylko gdy**
`raft-height-datapack: true` w `config.yml` (domyślnie `false`).

## Datapacki czyta się WYŁĄCZNIE z `<level-name>/datapacks`

Nie z katalogu serwera. Dowód w bytecode 26.3:
`ServerPacksSource.createPackRepository(levelPath.resolve(LevelResource.DATAPACK_DIR))`;
potwierdzenie na żywo: w `world/datapacks/bukkit` siedzi paczka Papera, a `world/level.dat` ma
w `DataPacks.Enabled` pozycje `vanilla`, `file/bukkit`, `paper`. Wcześniejsza wersja pluginu
pisała do `<serwer>/datapacks/raft-height` — dlatego **datapack nigdy się nie wczytywał**, mimo
poprawnego `pack.mcmeta`.

## Plik dimension_type musi być zgodny z bieżącym schematem, inaczej serwer NIE WSTANIE

Stary, płaski schemat (`natural`, `piglin_safe`, `bed_works`, `has_raids`, `effects`,
`monster_spawn_light_level.value`) jest odrzucany: sprawdzone w sandboxie na Paper 26.3 build 25 —
serwer kończy start komunikatem `Registry loading errors: Failed to parse minecraft:overworld`
i `Failed to load datapacks, can't proceed with server load` (ratuje tylko `--safeMode` albo
ręczne usunięcie pliku).

Brakujące elementy nowego schematu: `attributes`, `timelines`, `has_ender_dragon_fight`,
a `monster_spawn_light_level` ma płaskie `min_inclusive`/`max_inclusive`.

Szablon: waniliowe `data/minecraft/dimension_type/overworld.json` z 26.3 (identyczne w 26.2),
zmienione wyłącznie `height` i `logical_height` na 352. **Uwaga na kolejność:** przy zepsutym
pliku plugin nie zdąży go naprawić, bo serwer nie dochodzi do włączania pluginów — poprawki
testuj najpierw na kopii serwera.

## Dwie pułapki, przez które datapack nie jest dobrym narzędziem do limitowania Raftu

- nadpisuje wymiar typu `minecraft:overworld`, a ten typ dzielą **wszystkie** światy NORMAL na
  serwerze (raft, lobby, most, pustynna, overworld, hub) — sufit Y=287 spada im wszystkim;
- zmiana `height` działa też na **istniejące** światy, nie tylko na nowe — zmierzone
  w sandboxie: świat `raft` utworzony przy `height=384`, po dołożeniu datapacka wczytuje się jako
  352 (`Y -64..287`) bez żadnego błędu, więc bloki powyżej 287 w KAŻDYM świecie NORMAL są tracone;
- `WorldCreator` w Paperze **nie ma** wyboru dimension type (`environment`/`type` to wszystko),
  więc nie da się dać Raftowi własnego typu wymiaru.

## Co robi plugin zamiast datapacka

Budowania w górę pilnuje sam plugin (`raft.build-height-limit`, domyślnie 280 — sufit ostrzejszy
niż datapackowe 287). Wysokość świata jest logowana przy każdym starcie:
`Wysokość świata 'raft': Y -64..319 (height=384) — domyślne limity wanilii…` (albo
`...limity z datapacka raft-height DZIAŁAJĄ`), czyli **widać czarno na białym, czy limity
działają**. Gdy plik jest na dysku, ale jeszcze nie w repozytorium, plugin pisze o tym wprost;
gdy format jest niezgodny lub pack wyłączony — też (Paper `DatapackManager`, `file/raft-height`).

**Zmierzone na sandboxie (Paper 26.3 build 25, kopia serwera):** flaga `true` → plugin pisze
paczke do `world/datapacks/raft-height`, a przy następnym starcie wanilia loguje
`Found new data pack file/raft-height, loading it automatically` i świat raportuje
`Y -64..287 (height=352)` — mechanika działa end-to-end, o ile plik jest w nowym schemacie.
