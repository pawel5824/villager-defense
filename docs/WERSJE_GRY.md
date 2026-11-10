# Wersja gry, wtyczki i pomiary wersji

Przeniesione z `AGENTS.md` §7 (stan 2026-09-22). Obowiązujące wersje i zasady są w `AGENTS.md`
§7; tutaj są pomiary, historia i pułapki.

## Co zmieniło się w kodzie przy 26.3 (sprawdzone w bytecode)

- `PoseStack.mulPose(Quaternionf)` **zniknęło** → `PoseStack.rotateDegrees(Axis, stopnie)`
  (matematyka identyczna: `Axis.rotateDegrees` = `rotate(rad)`). Miejsca: `DevicePartRenderer`,
  `DeviceBlockEntityRenderer`.
- `InputConstants.Type.KEYSYM` → `Type.KEYBOARD` (SDL zamiast GLFW; `SCANCODE` zniknął).
  Kody klawiszy bierz z `InputConstants`, nigdy nie hardkoduj.
- `GuiGraphicsExtractor`: `RenderPipeline` przeniósł się do `com.mojang.renderpearl.api`,
  `textWithWordWrap` zwraca `int`. Nasz HUD używa `fill(int,…)`, którego to nie dotyczy.
- Mixiny (`Hud`, `ExperienceBar`, `ContextualBar`, `SpecialModelWrapper`) — **bez zmian**,
  sygnatury w 26.3 są identyczne jak w 26.2.
- Fabric API 26.3 usuwa `FuelRegistry`, `CompostingChanceRegistry`, `Tillable/Strippable/
  FlattenableBlockRegistry`, `FabricPotionBrewingBuilder` — projekt ich nie używa.
- `fabric.mod.json` ma **`"minecraft": ">=26.3"`** (nie `26.x`). 26.2 nie ma `rotateDegrees`
  ani `Type.KEYBOARD`, więc mod zbudowany na 26.3 wywaliłby się na kliencie 26.2 twardym
  `NoSuchMethodError`/`NoSuchFieldError`; ograniczenie zamienia crash na czysty komunikat loadera.
  Predykat sprawdzony realnym parserem Fabrica (`VersionPredicateParser.parse(">=26.3")`):
  26.2 → `false`, 26.3 → `true`, 26.4 → `true`. (Stare `26.x` przepuszczało 26.2.)

Klient potrzebuje jeszcze **profilu Fabric dla 26.3** (loader 0.19.5) w launcherze — tego nie da
się zrobić z repo; samo podłożenie JAR-ów do `.minecraft/mods` nie wystarczy.

**Protokół: 26.3 = 777, 26.2 = 776.** Klient 26.2 **nie wejdzie** na serwer 26.3 (i odwrotnie).
Od 2026-09-22 to jest zamierzone: Via jest wyłączona, więc każdy gracz musi mieć 26.3.

## Stan wtyczek na Paper 26.3 (sandbox 2026-09-20, build 25)

| Wtyczka | Wynik | Szczegóły |
|---|---|---|
| ServerPlugin (nasz) | ✅ wstaje bez błędów | `api-version: '26.3'`, logi WorldGenerator działają |
| BlockShips 0.0.17 | ✅ | `api-version: 1.21` |
| **ProtocolLib** | ✅ dev-build | 5.4.0 (VIII 2025) wymieniony na dev-build **5.5.0-SNAPSHOT-f0e959a** (asset z 24 VIII 2026, `paper-plugin.yml`, `api-version: 26.2`) — repozytorium ma commity „Update to Minecraft 26.2”, „Mark 26.1 as supported”. Ostrzeżenie o nietestowanej wersji wyciszone przez `ignore version check: 26.3.0`. Pomiar: `tools/protocollib-check` |
| **voicechat 2.6.24** | ✅ natywnie 26.3 | Changelog 2.6.24: „Added support for 26.3”. Loguje `Compatibility version 20` i `Successfully initialized commodore command completion`, **bez** trybu Spigota. Na 2.6.21 było odwrotnie (tryb zgodności + `Failed to initialize commodore command completion`) |
| ~~ViaVersion 5.12.0 + ViaBackwards 5.12.0~~ | ⛔ **wyłączone 2026-09-22** | Wstawały bez błędów i wpuszczały 26.1/26.2, ale decyzja: serwer tylko na 26.3. Pliki zostały jako `plugins/ViaVersion-5.12.0.jar.wylaczone-20260922` i `plugins/ViaBackwards-5.12.0.jar.wylaczone-20260922` |
| ~~GrimAC + ViaBackwards~~ | ✅ nie dotyczy | Ostrzeżenie Grima (`installed ViaBackwards on a 1.21.2+ server`, możliwe fałszywe flagi u starszych klientów na pojazdach) **zniknęło razem z Via** |
| **GrimAC** | ⚠️ tylko **własny build** | Oficjalny alpha (2.3.74-8eb5f28, PE 2.13.1) **nie wstaje**: `Couldn't find asBukkitCopy or asCraftMirror method on CraftItemStack`. Działa build z branchu `ver/26.3` (commit 3cdab40) z PE `2.14.0+3ba02ddd5-SNAPSHOT` → `plugins/grimac-bukkit-2.3.74-3cdab40.jar`. Przepis: `server-anticheat/README.md` |

GrimAC to jedyna twarda blokada migracji — i została obejścia: PacketEvents ma 26.3 dopiero
w buildach z CI (commit `3ba02ddd`), a Grim na branchu `ver/26.3`. Składanie własnego JAR-a
(własne PE w mavenLocal + `./gradlew :bukkit:shadowJar --configure-on-demand`) daje działający
anty-cheat, dopóki nie wyjdzie oficjalny build — szczegóły w `server-anticheat/README.md`.
**Pamiętaj o `--configure-on-demand`:** bez tego Gradle konfiguruje moduł `fabric` i remapuje
źródła Minecrafta dla wszystkich wersji.

## 7.1. Sprawdzanie wtyczek po podbiciu wersji (`tools/protocollib-check`)

Ostrzeżenie wtyczki („has not yet been tested”, „fall back to … compatibility mode”) nic nie mówi
o tym, czy wtyczka **faktycznie** działa — a kilka funkcji ServerPluginu wisi właśnie na
ProtocolLib (pasek pragnienia na pasku XP, wyciszanie dźwięków mobów per gracz, filtr pakietów
tratwy, wyciszanie plusku spławika, pogoda). Dlatego zamiast zgadywać, używamy próby:

```bash
tools/protocollib-check.sh                          # zbuduj jara próby
tools/protocollib-check.sh "../VD - serwer/plugins"  # zbuduj i wgraj na serwer
```

Próba jest pluginem: przy starcie i po `/protlibcheck` sprawdza **dokładnie te 8 typów pakietów,
których plugin używa**, wypisuje klasę pakietu z serwera, buduje pakiet i czyta z niego pola,
rejestruje i odpina listener, a jeśli gracz jest online — wysyła prawdziwy pakiet do niego.
Wynik: `grep ProtLibCheck logs/latest.log`.

**Zmierzone na Paper 26.3 build 25 (2026-09-20):** rejestr ProtocolLib ma **252** typy pakietów,
wszystkie nasze typy rozwiązują się na właściwe klasy:

| Nasz typ | klasa w 26.3 | id |
|---|---|---|
| `EXPERIENCE` | `ClientboundSetExperiencePacket` | 103 |
| `UPDATE_HEALTH` | `ClientboundSetHealthPacket` | 104 |
| `ENTITY_SOUND` | `ClientboundSoundEntityPacket` | 116 |
| `NAMED_SOUND_EFFECT` | `ClientboundSoundPacket` | 117 |
| `ENTITY_TELEPORT` | `ClientboundTeleportEntityPacket` | 125 |
| `WORLD_PARTICLES` | `ClientboundLevelParticlesPacket` | 47 |
| `GAME_STATE_CHANGE` | `ClientboundGameEventPacket` | 38 |
| `CUSTOM_PAYLOAD` | `ClientboundCustomPayloadPacket` | 24 |

**Stary ProtocolLib 5.4.0 przechodzi tę samą próbę, ale z ID ze swojej epoki**
(`EXPERIENCE` = **96**, `UPDATE_HEALTH` = 97, `ENTITY_SOUND` = 109, `NAMED_SOUND_EFFECT` = 110,
`ENTITY_TELEPORT` = 118, `WORLD_PARTICLES` = 41, `GAME_STATE_CHANGE` = 34 — czyli o 6-7 mniej,
bo nie zna pakietów dodanych w 26.2/26.3). To dokładnie ten rodzaj cichego rozjazdu, przez który
warto tę wtyczkę trzymać na dev-buildzie, a nie na półrocznym wydaniu.

Dwie rzeczy, które nowy build naprawił poza mapowaniem:

- zniknęło ostrzeżenie Papera `[HorriblePlayerLoginEventHack] … re-configuration APIs to be
  unavailable: [ProtocolLib]` (Paper artifact nie wisi już na `PlayerLoginEvent`), więc API
  rekonfiguracji Papera zostaje sprawne;
- `ignore version check: 26.3.0` w `plugins/ProtocolLib/config.yml` zdejmuje ostrzeżenie
  o nietestowanej wersji — celowo **wersja-punktowo**, żeby 26.4 znów ostrzegło zamiast milczeć.

Próbę (`ProtLibCheck.jar`) wgrywa się **tylko na czas testów** — to narzędzie diagnostyczne.

## 7.2. Gracze na 26.2 na serwerze 26.3 — ścieżka ZAMKNIĘTA (2026-09-22)

> **Stan obecny: ViaVersion i ViaBackwards są wyłączone** (`*.jar.wylaczone-20260922`
> w `VD - serwer/plugins/`), więc serwer przyjmuje wyłącznie klienty 26.3. Poniżej zostaje opis,
> jak ta ścieżka działała i co zostało zmierzone — na wypadek, gdyby trzeba ją było przywrócić.

Zainstalowane (2026-09-20) w `VD - serwer/plugins/`: `ViaVersion-5.12.0.jar`
(`sha256 72c40a6a…50b0`) i `ViaBackwards-5.12.0.jar` (`194e9250…ab78d`), **pobrane z GitHub
Releases 5.12.0** (Modrinth ma ten sam build w innym opakowaniu ZIP: treść wpisów identyczna,
hash inny — nie mieszaj źródeł przy weryfikacji). Oba deklarują gry `26.1`, `26.2`, `26.3`.

**Podział ról:** ViaVersion to rama tłumaczenia („nowszy klient na starszym serwerze”),
ViaBackwards to **starszy klient na nowszym serwerze** — czyli nasz przypadek (26.2 → 26.3).
ViaBackwards **wymaga** ViaVersion, więc stoją razem; sam ViaVersion nic tu nie da. Dowód
w bytecode, nie w opisie: `viabackwards/protocol/v26_3to26_2/Protocol26_3To26_2.class`
(+ rewriter’y bloków/encji/komponentów) oraz `viaversion/protocols/v26_2to26_3/`.

**Weryfikacja A/B (kopia serwera, Paper 26.3 build 25, port 25597, offline, pełny zestaw
wtyczek).** Test = ręczny handshake + `login:hello` (`name` + `profileId`, bo 26.3 wymaga obu
pól) wysłane przez `/dev/tcp`, bez klienta gry:

| Klient (protokół) | bez Via | z Via 5.12.0 |
|---|---|---|
| 26.3 (777) | przyjęty | przyjęty — `UUID of player …` |
| 26.2 (**776**) | **`Outdated client! Please use 26.3`** | **przyjęty** — log `UUID of player …`, dalej zatrzymany tylko whitelistą sandboxa |
| 26.1 / 26.1.1 / 26.1.2 (**775**) | — | **przyjęty** — `UUID of player …` |
| 1.21.11 (774) | — | **przyjęty** — `UUID of player …` |
| 1.20.1 (763) | — | przechodzi bramkę wersji i tłumaczenie; błąd pokazuje łańcuch: `Client Protocol: 1.20-1.20.1, Server Protocol: 26.3, Source: Protocol1_20_2To1_20` |
| 1.10 (**210**) | — | **przyjęty** — ale `login start` bez UUID (starszy układ pakietu) |
| 1.8.9 (47) | — | cisza: poniżej progu Via (deklaruje klientów od 1.10), zero odpowiedzi i zero wpisu w logu |

Numery protokołów: 26.3 = 777, 26.2 = 776, **26.1 = 775** (wszystkie 26.1.x), 1.21.11 = 774.
Łańcuch dla 26.1 to `Protocol26_2To26_1` → `Protocol26_3To26_2` (oba w ViaBackwards), czyli
tłumaczenie idzie o jedną wersję w górę na raz.

Czyli wersja klienta przestaje być bramką. Start z Via jest czysty (`Done (…)!`, zero
`SEVERE`/`Exception`), a voicechat sam dodaje mapowania Via
(`Successfully added ViaVersion mappings`).

**Czego 26.2 i 26.1 nie dostaną: moda.** Paczkę zasobów dostawały od 2026-09-21 — `min_format`
ześlizgnięte do **84** (26.1: `version.json` → protocol 775, world 4786, resource 84/0, data
101/1), więc 26.1 i 26.2 wczytywały paczkę. Działały wtedy: latarenki (10 wariantów,
`items/lantern.json`), sniper (`items/spyglass.json`), radio (4001) i duża beczka (4003) —
wszystko zmierzone kodekiem 26.1/26.2 bez moda (`tools/pack-compat-check.sh`). Odbiornik
(CMD 4002) pokazywał się jako waniliowy heavy core, bo jego geometrię rysuje mod; w paczce ma
zwykły model fallback (2026-10-01), więc przedmiot wygląda poprawnie, tylko bez animacji (wtedy
pomagał overlay `legacy_pre263` — dziś już nie istnieje).

Moda nie ma nawet dla 26.2: 26.2 nie ma `PoseStack.rotateDegrees` ani
`InputConstants.Type.KEYBOARD` (są tam `mulPose(Quaternionf)`, `KEYSYM`, `SCANCODE`), więc JAR
zbudowany na 26.3 przewraca się na kliencie 26.2. Dlatego `fabric.mod.json` ma `>=26.3`: 26.2
dostaje czysty komunikat loadera zamiast crashu. Gracz z 26.2 **wejdzie**, ale bez pasków HUD,
modeli urządzeń, kabli i animacji przedmiotów — to wszystko siedzi w modzie. Paczka zasobów była
wtedy OK dla obu (`min_format 84`), a plik `items/heavy_core.json` dla formatów 84–96 podmieniał
overlay `legacy_pre263` — bez niego wanilia odrzucała cały plik z powodu typu `raft:device`.
**Dziś paczka jest 26.3-only (`min_format 97`, bez overlayów)**, bo serwer nie wpuszcza
starszych klientów.

**Pułapki:**

- **GrimAC** loguje `installed ViaBackwards on a 1.21.2+ server` — to konfiguracja, której Grim
  nie wspiera; komunikat kończy się ostrzeżeniem o **pojazdach u starszych klientów**. Nasza
  tratwa pojazdem nie jest (ruch liczony w pluginie + BlockDisplay/BlockEntityRenderer), ale
  łodzie i BlockShips już tak — jeśli 26.2 gracze zaczną zbierać fałszywe flagi, to pierwsze
  miejsce szukania.
- Modpack (`modpack/pack.toml`) pinuje `minecraft = "26.3"`, więc gracz 26.2 nie może użyć
  paczki modpacka — potrzebuje osobnego profilu Fabric 26.2.
- Pliki konfiguracyjne (`plugins/ViaVersion/config.yml`, `plugins/ViaBackwards/config.yml`)
  generują się przy pierwszym starcie i **domyślne są wystarczające** (żadnego tłumaczenia
  paczek ani kanałów pluginowych nie trzeba — nasz `raft:receivers` idzie do klienta z modem,
  którego na 26.2 i tak nie ma).
- **Rollback** = usunięcie obu JAR-ów z `plugins/`; wtedy 26.2 znów zobaczy `Outdated client`.
- Moda „na oba klienty” nie da się zrobić jednym buildem bez shimu: refleksja (`rotateDegrees`
  gdy istnieje, inaczej `mulPose(Quaternionf)`; `KEYBOARD` inaczej `KEYSYM`) i wtedy
  `fabric.mod.json` wraca na `26.x`.
