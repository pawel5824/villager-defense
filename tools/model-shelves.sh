#!/usr/bin/env bash
# Polki modelu (tools/ModelShelves.java) — wypisuje poziome powierzchnie modelu i gotowe
# pozycje shulkerow-kolizji dla kazdej z nich, plus rytm skokow miedzy stopniami.
#
# Uzycie (z katalogu glownego projektu):
#   tools/model-shelves.sh <model.json|model.bbmodel> [skala shulkera = 0.5]
#
# Przyklady:
#   tools/model-shelves.sh pack/assets/raft/rafthud/antena.json 0.5
#   tools/model-shelves.sh pack/assets/raft/rafthud/schody.bbmodel 0.5
set -euo pipefail

cd "$(dirname "$0")/.."

if [ $# -lt 1 ]; then
    echo "Uzycie: tools/model-shelves.sh <model.json|model.bbmodel> [skala = 0.5]"
    exit 1
fi

pick_latest() {
    find "$HOME/.gradle/caches/modules-2/files-2.1/$1" -name "*.jar" 2>/dev/null \
        | grep -v -e sources -e javadoc | sort -V | tail -1
}

GSON="$(pick_latest "com.google.code.gson/gson" || true)"
if [ -z "$GSON" ]; then
    echo "Nie znalazlem gson w cache Gradle — uruchom najpierw: cd pack && ./gradlew build"
    exit 1
fi

if command -v cygpath >/dev/null 2>&1; then
    SEP=';'
    CP="$(cygpath -w "$GSON")"
    OUT="$(cygpath -w "$PWD/tools/out-shelves")"
else
    SEP=':'
    CP="$GSON"
    OUT="$PWD/tools/out-shelves"
fi

rm -rf tools/out-shelves && mkdir -p tools/out-shelves
javac -nowarn -cp "$CP" -d tools/out-shelves tools/ModelShelves.java
java -cp "tools/out-shelves$SEP$CP" ModelShelves "$@"
