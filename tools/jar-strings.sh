#!/usr/bin/env bash
# Weryfikacja, czy dana wersja Minecrafta zna dany klucz JSON / nazwę typu — w bytecode, nie z pamięci.
#
# Uzycie:
#   tools/jar-strings.sh <jar> <literal>...
#   tools/jar-strings.sh --pack <client.jar> <plik.json>...   # sprawdza WSZYSTKIE literalne klucze z JSON-ow
set -euo pipefail

cd "$(dirname "$0")/.."

OUT="tools/out-jarstrings"
rm -rf "$OUT" && mkdir -p "$OUT"
javac -nowarn -d "$OUT" tools/JarStrings.java
java -cp "$OUT" JarStrings "$@"
