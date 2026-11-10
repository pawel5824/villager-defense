# Paczka zasobów — format, kopie i publikacja

Przeniesione z `AGENTS.md` §9 (stan 2026-09-22). Zasady są w `AGENTS.md` §9; tutaj są
szczegóły skryptów, hostingów i pomiarów.

## Format

Zmierzone w `version.json` w jarach klientów: 26.1 = zasoby **84.0**, 26.2 = **88.0**,
26.3 = **97.1**. Paczka deklaruje **`min_format 97` / `max_format 97`** — wczyta ją wyłącznie
26.3. `min_format` to twarda bramka (klient z niższym formatem odrzuca paczkę, a przy
`require-resource-pack=true` zostaje wyrzucony z serwera), `max_format` w praktyce nie blokuje.
Starego klucza `pack_format` nie używamy — w 26.x liczy się para `min_format`/`max_format`.

## Overlaye — historia

Paczka **nie ma już overlayów (stan 2026-09-22)**. Do tego dnia istniał `legacy_pre263`
(formaty 84–96) dla graczy 26.1/26.2 bez moda. Powód był konkretny: item definition może
wskazywać typ modelu rejestrowany przez mod (`minecraft:special` + `raft:device` = animowany
odbiornik), a wanilia takiego typu nie zna i **odrzuca CAŁY plik**, nie sam wpis — zmierzone
kodekiem każdej wersji: `Unknown element id: raft:device`. Bez overlaya przepadłyby radio (4001)
i duża beczka (4003), a waniliowy heavy core zostałby bez modelu. Overlay wybierał sam klient po
formacie zasobów (`OverlayMetadataSection.overlaysForVersion`: 84.0 i 88.0 → `legacy_pre263`,
97.1 → baza).

**Jeśli kiedyś wróci wpuszczanie starszych klientów, overlay musi wrócić razem z nim** — inaczej
radio, beczka i waniliowy heavy core zostaną bez modelu. Gdyby ktoś go przywrócił, kontrolę
pilnuje `tools/check-item-model.sh`: żadna definicja w overlayu nie może wskazywać typu z moda.

## Czego nie pakujemy

**Do paczki i do kopii nie jadą źródła Blockbencha** (`*.bbmodel`, np. `antena.bbmodel` obok
finalnego modelu, `wiadro.bbmodel`) — `pack.zip` je odsiewa, a `apply_sync`
w `tools/publish-pack.sh` usuwa je z kopii po skopiowaniu (inaczej zostawały tam po cichu, bo
manifest porównania je ignoruje, więc rozjazd nie był widoczny w raporcie).

Pliki deweloperskie `pack/` (`build.gradle`, `gradle/`, `run/`, `tools/`) też nigdy nie jadą do
graczy — kontrola w środku skryptu tego pilnuje.

## Trzy kopie paczki — wszystkie muszą być aktualne

1. `pack/` (dev; z niej korzysta `pack/run` i testy),
2. `modpack/resourcepacks/raft-hud/` — to dostają gracze modpacka; po każdej zmianie
   `packwiz refresh`,
3. `pack.zip` — **to dostaje każdy, kto nie używa modpacka.** Serwer ma
   `require-resource-pack=true` i wysyła `resource-pack=<URL>` + `resource-pack-sha1=<sha1>`,
   więc klientem rządzi hash: pod URL-em musi leżeć **dokładnie** ten plik, inaczej klient
   zostaje odrzucony.

**Robota jednym poleceniem:** `tools/publish-all.sh` (cała publikacja: mod + plugin + paczka)
albo `tools/publish-pack.sh` (tylko paczka).

**Czwarta kopia** to zasoby w jarze moda (`pack/src/main/resources`) — `publish-pack.sh` jej nie
dotyka, więc `tools/check-item-model.sh` porównuje ją bajt w bajt z paczką (model i każda użyta
tekstura). Sprawdzone testem negatywnym: podmiana pliku w tej kopii daje `BLAD`.

## `tools/publish-pack.sh` — publikacja paczki wszędzie

Kolejność: wybór hostingu → porównanie i dogranie obu kopii
(`modpack/resourcepacks/raft-hud`, `.minecraft/resourcepacks/raft-hud`) + `packwiz refresh` →
build `pack.zip` **tylko z `pack.mcmeta`/`assets`/`overlays`** → sha1 → upload przez API →
**pobranie pliku z URL-a i porównanie sha1** → wpis do `server.properties` (z kopią zapasową
i stylem escapowania takim, jak w pliku) → zapis stanu w `tools/.publish-pack-state`
(hash źródeł, sha1, hosting, URL).

Hosting — API, **bez CAPTCHY i bez ręcznego przeciągania pliku**:

| `--host` | Kiedy wybierany | Uwagi |
|---|---|---|
| `catbox` | domyślny, gdy nie ma GitHub-a | `https://catbox.moe` — API bez konta i bez tokenu. URL jest **losowy** (nie adresuje treści), więc skrypt pamięta go w stanie publikacji i nie wgrywa tej samej paczki dwa razy |
| `github` | jest token i **publiczne** repo | GitHub Releases: jeden release (`--tag resourcepack`), każda wersja jako asset `pack-<sha1>.zip`, więc URL jest niezmienny: `…/releases/download/resourcepack/pack-<sha1>.zip` |
| `mcpacks` | awaryjnie, z `--host mcpacks` | `download.mc-packs.net`, formularz z CAPTCHĄ → upload ręcznie + `--finish` |

- `auto` (domyślne) bierze GitHub **tylko** przy tokenie i publicznym repo, inaczej catbox — i za
  każdym razem wypisuje powód wyboru. Repo prywatne **nie zadziała** (klient Minecraft pobiera
  paczkę bez logowania), więc skrypt to wykrywa i odmawia. Token: `$GITHUB_TOKEN`, `$GH_TOKEN`
  albo `tools/.pack-host-token` (w `.gitignore`); repo z `remote origin` albo `--repo owner/name`.
- Zip buduje się **deterministycznie** (stała data wpisów), więc ten sam zestaw plików = ten sam
  sha1. Drugie uruchomienie bez zmian kończy się na „nic do zrobienia”, a rozjazd między źródłami,
  zipem, hostingiem i `server.properties` jest wykrywany, nie zgadywany.
- **Żaden backend nie jest wiarygodny sam z siebie:** po uploadzie skrypt pobiera plik z URL-a
  (5 prób, z odstępami — GitHub asset nie zawsze jest widoczny od razu) i porównuje sha1, a dopiero
  potem dotyka `server.properties`.
- `--dry-run` (podgląd, nic nie wysyła i nie zapisuje), `--force` (publikuj mimo braku zmian),
  `--finish` (nie wysyłaj — plik już jest na hostingu), `--host`/`--repo`/`--tag`/`--token-file`,
  `--no-sync` (nie ruszaj kopii), `--prune` (usuń z kopii pliki, których nie ma w `pack/`),
  `--no-props`, `--server <katalog>`, `--open`.

Stan (2026-09-22, **po zdjęciu warstwy 26.1/26.2**): paczka jest na catboxie —
`https://files.catbox.moe/mnrx1c.zip`, sha1 `6345b5eb…`, **139 plików, 66 KB** (wcześniej 145
plików / 69 KB z overlayem) — i **`server.properties` już na nią wskazuje**
(`resource-pack=https\://files.catbox.moe/mnrx1c.zip`, `resource-pack-sha1=6345b5eb…`; sprawdzone
parserem Javy: `Properties.load` daje dokładnie ten URL i ten hash). Historią są wcześniejsze
wersje (`hmqnc0.zip` / `78872401…`, a przed nimi `24ed474a…` z 31 sierpnia) oraz kopie
`server.properties.bak.2026…` i `pack.zip.bak.20260921`. W zipie zostają tylko `assets`
i `pack.mcmeta` — kontrola w `publish-pack.sh` nadal to sprawdza.

## `tools/publish-all.sh` — jedna publikacja całego projektu

Zastępuje trzy osobne ścieżki: `build-and-deploy.ps1` (plugin),
`modpack/build-and-publish.bat` (mod) i `tools/publish-pack.sh` (paczka) — tamte zostają jako
narzędzia częściowe. Kolejność jest celowa: **najpierw wszystkie buildy** (nie dotykają niczego
na zewnątrz), **dopiero potem publikacje**, więc nieudana kompilacja nie zostawia połowicznie
wysłanej wersji.

| Etap | Co robi |
|---|---|
| 1/5 build moda | `pack/gradlew build` → `pack/build/libs/raft-hud-<wersja>.jar` (log `tools/.publish-all-gradle.log`, zostaje tylko po błędzie) |
| 2/5 build pluginu | `mvn clean package` → `target/ServerPlugin.jar` (log `build.log`) |
| 3/5 mod do graczy | jar → `modpack/mods/` → `packwiz refresh` → kontrola, że hashe jara i `index.toml` są w indeksie → `git commit --only` (3 pliki) → `git push` |
| 4/5 plugin na serwer | kopia `ServerPlugin.jar.bak.<data>` (2 najnowsze) → podmiana → kontrola sha256 |
| 5/5 paczka zasobów | `tools/publish-pack.sh --server <serwer>` |

- `--skip-mod` / `--skip-plugin` / `--skip-pack` (etapy są niezależne — preflight sprawdza tylko
  to, czego wymaga uruchamiany etap), `--no-push` (modpack przygotowany, bez commita i pusha),
  `--dry-run` (kontrole i plan; brakujące warunki są tam ostrzeżeniami `!!`, ale kod wyjścia to 1,
  bo plan jest niekompletny), `--server <katalog>`.
- Kontrole wstępne lecą **przed pierwszym buildem**: brak `mvn`/`packwiz`/`gradlew`, brak remote,
  detached HEAD, duplikat `modpack/mods/raft-hud.pw.toml`. Typowa pomyłka nie psuje połowy
  publikacji.
- Commit obejmuje **wyłącznie** `modpack/index.toml`, `modpack/pack.toml` i
  `modpack/mods/raft-hud-<wersja>.jar`, a skrypt czyta potem `git show --name-only HEAD` i **nie
  pushuje**, jeśli w commicie jest cokolwiek spoza tej listy. Sprawdzone (klon w `/tmp` z bare-repo
  jako remote): commit zawiera dokładnie 3 pliki, cudza robota przygotowana w indeksie zostaje
  nietknięta, a `pre-commit`, który dorzuca obcy plik, kończy się na „obcy plik w commicie: …”
  (kod 1, remote pusty).
- Push idzie z `GIT_TERMINAL_PROMPT=0`: brak danych logowania to błąd, a nie zawieszenie na pytaniu.
- Zmierzone na tej maszynie: pełny przebieg `--no-push` (oba buildy + modpack + deploy + paczka)
  to ~41 s (gradle ~15 s, maven ~14 s).
- Pułapka Windows: `cmd //c gradlew.bat` **nie działa** z Git Basha (cmd nie szuka `.bat`
  w katalogu bieżącym) — skrypt woła `./gradlew`, a `.bat` tylko jako fallback, ze ścieżką
  `".\gradlew.bat"`.

## `server.properties` i budowa awaryjna

- **Zapis sprawdzaj parserem, nie okiem**: linie przepisuje pętla w bashu (nie `awk -v`, który
  zjada `\:` w wartości i wypisuje ostrzeżenie), a wartość idzie tak, jak zapisuje ją Java
  (`:` → `\:`). Kontrola: `java` + `Properties.load` na pliku serwera musi dać
  `resource-pack = https://files.catbox.moe/<hash>.zip` i zgodny `resource-pack-sha1`.
  Zmiana jest czytana przy STARCIE serwera, więc gracze na działającym serwerze dostaną nową
  paczkę dopiero po restarcie.
- Budowa awaryjna (bez skryptu):
  `cd pack && jar --create --file ../pack.zip -M pack.mcmeta assets overlays` → `sha1sum pack.zip`
  → hash do `server.properties` i do URL-a. Kolejność jest **obowiązkowa** (najpierw plik na CDN,
  potem hash), bo `require-resource-pack=true` odrzuci klienta przy niezgodności.
