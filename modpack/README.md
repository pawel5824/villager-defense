# Villager Defense — Packwiz Modpack

Modpack z auto-aktualizacja przez packwiz. Gracz instaluje raz, potem aktualizacje
sciagaja sie automatycznie przy starcie launchera.

## Struktura

```
modpack/
├── pack.toml                        # Metadane packwiz (nie edytuj recznie!)
├── index.toml                       # Indeks plikow (generowany przez packwiz)
├── mods/
│   ├── raft-hud-1.0.0.jar           # Custom client mod (raft HUD)
│   └── fabric-api.pw.toml           # Fabric API (Modrinth)
├── resourcepacks/raft-hud/          # Resource pack z custom teksturami
│   ├── pack.mcmeta
│   └── assets/...
├── packwiz-installer-bootstrap.jar  # Bootstrap do auto-update
├── INSTALL.bat                      # Instalator dla gracza
├── update-and-launch.bat            # Script auto-update + launch
└── README.md                        # Ten plik
```

## Wdrozenie (dla Ciebie — serwera)

### 1. Wypchnij na GitHub

```bash
cd villager-defense
git add modpack/
git commit -m "packwiz modpack z auto-update"
git push origin main
```

### 2. Pobierz raw URL

Po wypchnieciu, raw URL do pack.toml bedzie:
```
https://raw.githubusercontent.com/<OWNER>/<REPO>/refs/heads/main/modpack/pack.toml
```

Zamien `<OWNER>` i `<REPO>` na prawdziwe wartosci.

### 3. Zaktualizuj URL w skryptach

W `update-and-launch.bat` zamien `YOUR_USERNAME/YOUR_REPO` na prawdziwe.

## Instalacja dla gracza

### Opcja A: Zwykly launcher (Prism/MultiMC)

1. Pobierz `packwiz-installer-bootstrap.jar` z release
2. Wrzuc do folderu `.minecraft` (lub instancji)
3. Ustaw pre-launch command:
   ```
   "$INST_JAVA" -jar packwiz-installer-bootstrap.jar <URL_PACK.TOML>
   ```
4. Uruchom — modpack zainstaluje sie i zaktualizuje

### Opcja B: Wlasny launcher (Freebuff)

1. Gracz klika "Zainstaluj" w launcherze
2. Launcher sciaga `packwiz-installer-bootstrap.jar`
3. Uruchamia: `java -jar packwiz-installer-bootstrap.jar <URL>`
4. Przy kazdym starcie launchera, ten sam komend sprawdza aktualizacje

### Opcja C: Automatyczny instalator

1. Gracz pobiera zip z `modpack/`
2. Rozpakowuje do `.minecraft`
3. Klika `INSTALL.bat` (lub uruchamia `update-and-launch.bat`)

## Aktualizacja modpacka (dla Ciebie)

### Zmiana w modach (raft-hud, Fabric API)

```bash
# Zaktualizuj raft-hud
cp pack/build/libs/raft-hud-2.0.0.jar modpack/mods/
packwiz update --all

# Zaktualizuj Fabric API
packwiz modrinth update fabric-api

# Wypchnij na GitHub
git add modpack/
git commit -m "update: raft-hud 2.0.0"
git push
```

Przy nastepnym starcie launchera gracze automatycznie pobiora nowe wersje!

### Dodanie nowego moda

```bash
cd modpack
packwiz modrinth install <mod-name>
git add .
git commit -m "add: <mod-name>"
git push
```

### Usuniecie moda

```bash
cd modpack
packwiz remove <mod-name>
git add .
git commit -m "remove: <mod-name>"
git push
```

## Auto-Update — jak to dziala

1. packwiz-installer-bootstrap jest lekki (~100KB)
2. Przy starcie sprawdza `pack.toml` z GitHub raw
3. Porownuje hashe plikow w `index.toml`
4. Pobraje tylko ZMIENIONE pliki (delta update)
5. Gracz nawet nie widzi — modpack jest zawsze aktualny

## Wymagania

- Minecraft 26.3
- Fabric Loader 0.19.5
- Java 25 (JDK 25)
