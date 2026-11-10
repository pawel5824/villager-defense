#!/usr/bin/env bash
# Buduje probe diagnostyczna ProtocolLib (tools/protocollib-check/src/pl/vd/tools/ProtLibCheck.java).
#
# Po co: ProtocolLib ostrzega "Version (MC: X) has not yet been tested", a my od niego
# zalezymy w kilku funkcjach (pasek pragnienia na pasku XP, wyciszanie dzwiekow per gracz,
# filtr pakietow tratwy). Proba mowi czarno na bialym, czy te typy pakietow dzialaja
# na BIEZACEJ wersji serwera — zamiast zgadywania z ostrzezenia.
#
# Uzycie (z katalogu glownego projektu):
#   tools/protocollib-check.sh                          # zbuduj tools/protocollib-check/build/ProtLibCheck.jar
#   tools/protocollib-check.sh /tmp/viatest/plugins      # zbuduj i wrzuc do wskazanego plugins/
#   PLIB=/sciezka/ProtocolLib.jar tools/protocollib-check.sh /sciezka/plugins
#
# Po wstawieniu na serwer: uruchom go i zobacz log ("grep ProtLibCheck logs/latest.log")
# albo wpisz w grze /protlibcheck (probe mozna powtarzac bez restartu).
set -euo pipefail

cd "$(dirname "$0")/.."

PLUGINS_DIR="${1:-}"
OUT="tools/protocollib-check/build/ProtLibCheck.jar"
CLASSES="tools/protocollib-check/build/classes"

# Windows (Git Bash): classpath musi miec separator ";" i sciezki windowsowe.
# Bez tego javac nie widzi ZADNEJ klasy i sypie bledami typu "package PacketType.Play does not exist".
case "$(uname -s)" in
    MINGW*|MSYS*|CYGWIN*)
        SEP=';'
        win() { cygpath -w "$1"; }
        ;;
    *)
        SEP=':'
        win() { printf '%s' "$1"; }
        ;;
esac

# paper-api: dokladnie ta wersja, na ktorej stoi plugin (pom.xml), z fallbackiem na najnowsza
PAPER_API="${PAPER_API:-}"
if [ -z "$PAPER_API" ]; then
    PAPER_API=$(ls "$HOME"/.m2/repository/io/papermc/paper/paper-api/26.3.build.25-alpha/paper-api-*.jar 2>/dev/null | head -1 || true)
fi
if [ -z "$PAPER_API" ]; then
    PAPER_API=$(ls -t "$HOME"/.m2/repository/io/papermc/paper/paper-api/*/paper-api-*.jar 2>/dev/null | head -1 || true)
fi
if [ -z "$PAPER_API" ]; then
    echo "Nie znalazłem paper-api w ~/.m2. Zbuduj najpierw plugin: mvn clean package"
    exit 1
fi

# ServerPlugin lezy w katalogu obok projektu ("../VD - serwer"), wiec ProtocolLib tez tam
PLIB="${PLIB:-}"
if [ -z "$PLIB" ]; then
    PLIB=$(ls "../VD - serwer/plugins/ProtocolLib.jar" "VD - serwer/plugins/ProtocolLib.jar" 2>/dev/null | head -1 || true)
fi
if [ -z "$PLIB" ]; then
    echo "Nie znalazłem ProtocolLib.jar (ustaw PLIB=/sciezka/ProtocolLib.jar)"
    exit 1
fi

CP_PARTS=()
add_cp() {
    [ -e "$1" ] || return 0
    CP_PARTS+=("$(win "$1")")
}
add_cp "$PAPER_API"
add_cp "$PLIB"
# Transitywne zaleznosci paper-api (Adventure, gson, adnotacje) — bez nich javac nie skompiluje
# niczego, co dotyka Player/Bukkit (bledy typu "cannot access BossBarViewer").
while IFS= read -r j; do add_cp "$j"; done < <(
    find "$HOME/.m2/repository/net/kyori" \
         "$HOME/.m2/repository/org/jetbrains/annotations" \
         "$HOME/.m2/repository/com/google/code/gson" \
         "$HOME/.m2/repository/com/google/guava/guava" \
         "$HOME/.m2/repository/net/md-5" \
         -name '*.jar' 2>/dev/null | sort -u
)
CP=$(IFS="$SEP"; printf '%s' "${CP_PARTS[*]}")

echo "paper-api:   $PAPER_API"
echo "ProtocolLib: $PLIB ($(unzip -p "$PLIB" paper-plugin.yml plugin.yml 2>/dev/null | tr -d '\r' | grep -m1 '^version:' || echo 'wersja ?'))"

rm -rf "$CLASSES"
mkdir -p "$CLASSES"
javac -nowarn --release 21 -cp "$CP" -d "$(win "$CLASSES")" \
    tools/protocollib-check/src/pl/vd/tools/ProtLibCheck.java
cp tools/protocollib-check/src/plugin.yml "$CLASSES/"
(cd "$CLASSES" && jar cf "$OLDPWD/$OUT" .)

echo "Zbudowane: $OUT ($(wc -c < "$OUT") B)"

if [ -n "$PLUGINS_DIR" ]; then
    mkdir -p "$PLUGINS_DIR"
    cp "$OUT" "$PLUGINS_DIR/ProtLibCheck.jar"
    echo "Wgrane do: $PLUGINS_DIR/ProtLibCheck.jar"
    echo "Uruchom serwer i sprawdz log: grep ProtLibCheck logs/latest.log"
    echo "Pamietaj, zeby probe usunac po testach (to narzedzie diagnostyczne, nie produkcja)."
fi
