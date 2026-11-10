#!/usr/bin/env bash
# Bezgłowy test parsera geometrii urządzeń (tools/DeviceMeshCheck.java).
#
# Sprawdza winding, orientację UV, rotacje elementów i rozmiar modeli — bez uruchamiania gry.
# Użycie (z katalogu głównego projektu):
#   tools/check-device-mesh.sh                      # domyślne modele z packa
#   tools/check-device-mesh.sh sciezka/do/model.json ...
set -euo pipefail

cd "$(dirname "$0")/.."

MODELS=("$@")
if [ ${#MODELS[@]} -eq 0 ]; then
    MODELS=(pack/assets/raft/rafthud/odbiornik.json
            pack/assets/raft/rafthud/antena.json
            pack/assets/raft/models/item/duza_beczka.json
            pack/assets/raft/models/item/radio.json
            pack/assets/raft/models/item/wiatrak.json)
fi

MC="$HOME/.gradle/caches/fabric-loom/26.3/minecraft-client.jar"
if [ ! -f "$MC" ]; then
    echo "Nie znalazłem klienta Minecraft 26.3 w cache Fabric Loom ($MC)."
    echo "Uruchom najpierw: cd pack && ./gradlew build"
    exit 1
fi

# Moduły potrzebne do wczytania DeviceMesh poza grą (Identifier, gson, …).
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

CLASSES="$PWD/pack/build/classes/java/main"
DEPS=("$CLASSES" "$MC")
for module in "${MODULES[@]}"; do
    jar="$(pick_latest "$module" || true)"
    if [ -n "$jar" ]; then
        DEPS+=("$jar")
    fi
done

# Java na Windows wymaga ścieżek w stylu C:\... i separatora ';'.
if command -v cygpath >/dev/null 2>&1; then
    JOINED=""
    for entry in "${DEPS[@]}"; do
        if [ -n "$JOINED" ]; then JOINED="$JOINED;"; fi
        JOINED="$JOINED$(cygpath -w "$entry")"
    done
    CP="$JOINED"
    OUT="$(cygpath -w "$PWD/tools/out")"
else
    CP="$(IFS=:; echo "${DEPS[*]}")"
    OUT="$PWD/tools/out"
fi

rm -rf tools/out && mkdir -p tools/out
javac -nowarn -cp "$CP" -d tools/out tools/DeviceMeshCheck.java
java -cp "tools/out;$CP" DeviceMeshCheck "${MODELS[@]}"
