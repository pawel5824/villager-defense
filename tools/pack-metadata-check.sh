#!/usr/bin/env bash
# Czy pack.mcmeta (zakres formatu + overlaye) jest poprawny dla danej wersji klienta i ktore
# katalogi overlaya klient faktycznie dolozy. Uruchamia sie raz na wersje, bo klas nie da sie
# przeladowac w jednym JVM.
#
# Uzycie: tools/pack-metadata-check.sh [pack.mcmeta] [klient.jar ...]
# Domyslnie: 3 x pack.mcmeta z repo i klienty 26.1/26.2/26.3 z /tmp (jesli sa).
set -euo pipefail

cd "$(dirname "$0")/.."

MCMETA="${1:-pack/pack.mcmeta}"
shift || true
CLIENTS=("$@")
if [ ${#CLIENTS[@]} -eq 0 ]; then
    for candidate in /tmp/client261.jar /tmp/client262.jar /tmp/client263.jar; do
        [ -f "$candidate" ] && CLIENTS+=("$candidate")
    done
fi
if [ ${#CLIENTS[@]} -eq 0 ]; then
    CLIENTS=("$HOME/.gradle/caches/fabric-loom/26.3/minecraft-client.jar")
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

for client in "${CLIENTS[@]}"; do
    DEPS=("$client")
    for module in "${MODULES[@]}"; do
        jar="$(pick_latest "$module" || true)"
        if [ -n "$jar" ]; then DEPS+=("$jar"); fi
    done
# Sciezka do pack.mcmeta moze byc wzgledna (repo) albo absolutna (/tmp/...).
case "$MCMETA" in
    /*) META_PATH="$MCMETA" ;;
    *)  META_PATH="$PWD/$MCMETA" ;;
esac

if command -v cygpath >/dev/null 2>&1; then
    SEP=';'
    JOINED=""
    for entry in "${DEPS[@]}"; do
        if [ -n "$JOINED" ]; then JOINED="$JOINED$SEP"; fi
        JOINED="$JOINED$(cygpath -w "$entry")"
    done
    CP="$JOINED"
    OUT="$(cygpath -w "$PWD/tools/out-meta")"
    META_ARG="$(cygpath -w "$META_PATH")"
else
    SEP=':'
    CP="$(IFS=:; echo "${DEPS[*]}")"
    OUT="$PWD/tools/out-meta"
    META_ARG="$META_PATH"
fi

    echo "##################### $MCMETA"
    rm -rf tools/out-meta && mkdir -p tools/out-meta
    javac -nowarn -cp "$CP" -d tools/out-meta tools/PackMetadataCheck.java
    java -cp "tools/out-meta$SEP$CP" PackMetadataCheck "$client" "$META_ARG" 2>&1 | grep -v "SLF4J"
done
