#!/usr/bin/env bash
# Generuje definicje przedmiotow (assets/<ns>/items/*.json) dla waniliowych przedmiotow z CMD,
# biorac `fallback` z waniliowej definicji w jarze klienta (dla przedmiotow-blokow to
# `minecraft:block/<nazwa>`, nie `minecraft:item/<nazwa>`). Na koncu sprawdza, czy KAZDY model
# wskazany przez definicje w paczce naprawde istnieje.
#
# Uzycie: tools/gen-item-definitions.sh [klient.jar] [katalog_paczki ...]
# Domyslnie: klient z cache Loom 26.3 + obie paczki (robocza i modpackowa).
set -euo pipefail

cd "$(dirname "$0")/.."

CLIENT="${1:-$HOME/.gradle/caches/fabric-loom/26.3/minecraft-client.jar}"
shift || true
PACKS=("$@")
if [ ${#PACKS[@]} -eq 0 ]; then
    PACKS=("pack" "modpack/resourcepacks/raft-hud")
fi

if [ ! -f "$CLIENT" ]; then
    echo "Nie ma klienta: $CLIENT"
    exit 1
fi

MODULES=(
    "com.google.code.gson/gson"
    "com.mojang/logging"
)

pick_latest() {
    find "$HOME/.gradle/caches/modules-2/files-2.1/$1" -name "*.jar" 2>/dev/null \
        | grep -v -e sources -e javadoc | sort -V | tail -1
}

DEPS=()
for module in "${MODULES[@]}"; do
    jar="$(pick_latest "$module" || true)"
    if [ -n "$jar" ]; then DEPS+=("$jar"); fi
done
if [ ${#DEPS[@]} -eq 0 ]; then
    echo "Nie znalazlem gson/logging w cache Gradle — uruchom najpierw: cd pack && ./gradlew build"
    exit 1
fi

if command -v cygpath >/dev/null 2>&1; then
    SEP=';'
    JOINED=""
    for entry in "${DEPS[@]}"; do
        if [ -n "$JOINED" ]; then JOINED="$JOINED$SEP"; fi
        JOINED="$JOINED$(cygpath -w "$entry")"
    done
    CP="$JOINED"
    OUT="$(cygpath -w "$PWD/tools/out-defs")"
    CLIENT_ARG="$(cygpath -w "$CLIENT")"
else
    SEP=':'
    CP="$(IFS=:; echo "${DEPS[*]}")"
    OUT="$PWD/tools/out-defs"
    CLIENT_ARG="$CLIENT"
fi

rm -rf tools/out-defs && mkdir -p tools/out-defs
javac -nowarn -cp "$CP" -d tools/out-defs tools/ItemDefinitions.java
java -cp "tools/out-defs$SEP$CP" ItemDefinitions "$CLIENT_ARG" "${PACKS[@]}"
