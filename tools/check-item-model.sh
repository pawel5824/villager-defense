#!/usr/bin/env bash
# Bezgłowy test podpięcia animowanego przedmiotu (tools/ItemModelCheck.java).
#
# Sprawdza item definition (heavy_core CMD 4002 -> typ raft:device), model bazowy, siatkę
# i to, czy każdy tor animacji trafia w istniejącą część — bez uruchamiania gry.
# Sprawdza OBA packi: roboczy (pack/assets) i ten, który dostają gracze (modpack/resourcepacks).
#
# Użycie (z katalogu głównego projektu):
#   tools/check-item-model.sh
set -euo pipefail

cd "$(dirname "$0")/.."

MC="$HOME/.gradle/caches/fabric-loom/26.3/minecraft-client.jar"
if [ ! -f "$MC" ]; then
    echo "Nie znalazłem klienta Minecraft 26.3 w cache Fabric Loom ($MC)."
    echo "Uruchom najpierw: cd pack && ./gradlew build"
    exit 1
fi

CLASSES="$PWD/pack/build/classes/java/main"
if [ ! -d "$CLASSES" ]; then
    echo "Brak skompilowanych klas moda ($CLASSES)."
    echo "Uruchom najpierw: cd pack && ./gradlew build"
    exit 1
fi

# Moduły potrzebne do wczytania DeviceMesh/DeviceAnimations poza grą (Identifier, gson, …).
MODULES=(
    "com.google.code.gson/gson"
    "com.mojang/brigadier"
    "com.mojang/datafixerupper"
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
    # Najnowsza wersja modułu (bez -sources / -javadoc).
    find "$HOME/.gradle/caches/modules-2/files-2.1/$1" -name "*.jar" 2>/dev/null \
        | grep -v -e sources -e javadoc \
        | sort -V | tail -1
}

DEPS=("$CLASSES" "$MC")
for module in "${MODULES[@]}"; do
    jar="$(pick_latest "$module" || true)"
    if [ -n "$jar" ]; then
        DEPS+=("$jar")
    fi
done

# Java na Windows wymaga ścieżek w stylu C:\... i separatora ';'.
if command -v cygpath >/dev/null 2>&1; then
    SEP=';'
    JOINED=""
    for entry in "${DEPS[@]}"; do
        if [ -n "$JOINED" ]; then JOINED="$JOINED$SEP"; fi
        JOINED="$JOINED$(cygpath -w "$entry")"
    done
    CP="$JOINED"
    OUT="$(cygpath -w "$PWD/tools/out-item")"
else
    SEP=':'
    CP="$(IFS=:; echo "${DEPS[*]}")"
    OUT="$PWD/tools/out-item"
fi

rm -rf tools/out-item && mkdir -p tools/out-item
javac -nowarn -cp "$CP" -d tools/out-item tools/ItemModelCheck.java
java -cp "tools/out-item$SEP$CP" ItemModelCheck
