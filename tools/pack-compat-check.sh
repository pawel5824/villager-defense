#!/usr/bin/env bash
# Czy pliki item definitions z paczki parsuja sie kodekiem KONKRETNEJ wersji klienta (bez moda)?
#
# Uzycie:
#   tools/pack-compat-check.sh [klient.jar] [katalog_paczki ...]
# Domyslnie: klient z cache Loom 26.3 i obie paczki (robocza + modpack).
# Przyklady:
#   tools/pack-compat-check.sh /tmp/client261.jar           # sprawdz 26.1
#   tools/pack-compat-check.sh /tmp/client263.jar pack      # tylko paczka robocza
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
    "com.mojang/datafixerupper"
    "com.mojang/brigadier"
    "com.mojang/authlib"
    "org.apache.commons/commons-lang3"
    "com.google.guava/guava"
    "it.unimi.dsi/fastutil"
    "org.joml/joml"
    "io.netty/netty-buffer"
    "io.netty/netty-codec"
    "io.netty/netty-common"
    "org.slf4j/slf4j-api"
)

pick_latest() {
    find "$HOME/.gradle/caches/modules-2/files-2.1/$1" -name "*.jar" 2>/dev/null \
        | grep -v -e sources -e javadoc | sort -V | tail -1
}

DEPS=("$CLIENT")
for module in "${MODULES[@]}"; do
    jar="$(pick_latest "$module" || true)"
    if [ -n "$jar" ]; then DEPS+=("$jar"); fi
done

# Katalog paczki moze byc wzgledny (repo) albo absolutny (/tmp/...).
ABS_PACKS=()
for pack in "${PACKS[@]}"; do
    case "$pack" in
        /*) ABS_PACKS+=("$pack") ;;
        *)  ABS_PACKS+=("$PWD/$pack") ;;
    esac
done
PACKS=("${ABS_PACKS[@]}")

if command -v cygpath >/dev/null 2>&1; then
    SEP=';'
    JOINED=""
    for entry in "${DEPS[@]}"; do
        if [ -n "$JOINED" ]; then JOINED="$JOINED$SEP"; fi
        JOINED="$JOINED$(cygpath -w "$entry")"
    done
    CP="$JOINED"
    OUT="$(cygpath -w "$PWD/tools/out-compat")"
    PACK_ARGS=()
    for pack in "${PACKS[@]}"; do PACK_ARGS+=("$(cygpath -w "$pack")"); done
else
    SEP=':'
    CP="$(IFS=:; echo "${DEPS[*]}")"
    OUT="$PWD/tools/out-compat"
    PACK_ARGS=("${PACKS[@]}")
fi

echo "Klient: $CLIENT"
rm -rf tools/out-compat && mkdir -p tools/out-compat
javac -nowarn -cp "$CP" -d tools/out-compat tools/PackCompatCheck.java
java -cp "tools/out-compat$SEP$CP" PackCompatCheck "${PACK_ARGS[@]}"
