#!/usr/bin/env bash
# Publikacja paczki zasobow: build pack.zip -> sha1 -> hosting z API -> server.properties
# (+ pilnowanie trzech kopii paczki: pack/, modpack, kopia testowa .minecraft).
#
# Po co: zapomniany upload albo stary hash w server.properties oznaczaja, ze gracze graja na
# wersji sprzed miesiaca (tak bylo 2026-09-21: server.properties wskazywal paczke z 31 sierpnia).
# Skrypt przechodzi cala sciezke i weryfikuje POBIERANIEM, ze pod URL-em lezy dokladnie ten plik.
#
# Hosting (bez CAPTCHY, w pelni automatyczny):
#   catbox  — https://catbox.moe, API bez konta i bez tokenu. Domyslny, gdy nie ma GitHub-a.
#             Catbox nie adresuje plikow trescia (URL jest losowy), wiec URL pamietamy
#             w stanie publikacji — dzieki temu ta sama tresc nie jedzie tam dwa razy.
#   github  — GitHub Releases przez REST API. Wymaga tokenu i repozytorium PUBLICZNEGO
#             (klient Minecraft pobiera paczke bez logowania). URL jest niezmienny, bo nazwa
#             assetu zawiera sha1:  https://github.com/<repo>/releases/download/<tag>/pack-<sha1>.zip
#   mcpacks — stary, reczny sposob (download.mc-packs.net): formularz ma CAPTCHE, wiec automat
#             go nie wysle. Zostaje jako awaryjna sciezka (upload recznie + --finish).
# Wybor hostingu: --host. Bez niego automat: github, jesli jest token i publiczne repo,
# inaczej catbox — a skrypt mowi, na jakiej podstawie wybral.
#
# Token GitHub — brane pierwsze znalezione: $GITHUB_TOKEN, $GH_TOKEN, tools/.pack-host-token
# (ten plik jest w .gitignore; nie commituj go). Repo: --repo owner/name albo remote origin.
#
# Uzycie:
#   tools/publish-pack.sh [--dry-run] [--force] [--finish] [--open]
#                         [--host auto|github|catbox|mcpacks] [--repo <owner/name>] [--tag <tag>]
#                         [--token-file <plik>] [--no-sync] [--prune] [--no-props]
#                         [--server <katalog>] [--cdn <url>] [--download <url>]
#
#   --dry-run   wszystko poza uploadem i zapisem server.properties (do podejrzenia)
#   --force     publikuj nawet gdy zrodla paczki sie nie zmienily od ostatniego razu
#   --finish    nie wysylaj — plik juz jest na hostingu; tylko kontrola pobrania + wpis do
#               server.properties (przy catbox potrzebny zapisany URL w stanie publikacji)
#   --open      otworz strone hostingu w przegladarce
#   --host      hosting (domyslnie auto)
#   --repo      repozytorium GitHub owner/name (domyslnie z remote origin)
#   --tag       tag release z paczka (domyslnie resourcepack)
#   --no-sync   nie dotykaj kopii paczki (modpack / .minecraft), tylko je raportuj
#   --prune     usun z kopii takze pliki, ktorych nie ma w pack/ (domyslnie ich nie ruszam)
#   --no-props  nie dotykaj server.properties (tylko zbuduj + wgraj)
#   --server    katalog serwera (domyslnie $VD_SERVER albo "<rodzic repo>/VD - serwer")
#
# Stan ostatniej publikacji: tools/.publish-pack-state (hash zrodel, sha1, hosting, URL).
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PACK="$ROOT/pack"
ZIP="$ROOT/pack.zip"
STATE="$ROOT/tools/.publish-pack-state"
CDN="https://mc-packs.net"
DOWNLOAD="https://download.mc-packs.net/pack"
CATBOX_API="https://catbox.moe/user/api.php"
GH_API="https://api.github.com"
GH_UPLOADS="https://uploads.github.com"
UA="vd-publish-pack/1.0 (Minecraft resource pack publisher)"
SERVER="${VD_SERVER:-$(cd "$ROOT/.." && pwd)/VD - serwer}"
MODPACK_COPY="$ROOT/modpack/resourcepacks/raft-hud"
TEST_COPY="$HOME/AppData/Roaming/.minecraft/resourcepacks/raft-hud"
TOKEN_FILE="${PACK_HOST_TOKEN_FILE:-$ROOT/tools/.pack-host-token}"

DRY=0
FORCE=0
FINISH=0
OPEN=0
NOSYNC=0
PRUNE=0
PATCH_PROPS=1
HOST=auto
REPO="${PACK_GH_REPO:-}"
TAG="${PACK_GH_TAG:-resourcepack}"

# Wszystko, co moze byc czytane przed ustawieniem: `set -u` nie wybaczy pustej zmiennej.
HOST_REASON=""; HOST_HINT=""; GH_MSG=""; GH_RELEASE_ID=""
HOSTED_URL=""; NEW_URL=""; LAST_VERIFY_ERR=""
PREV_SRC=""; PREV_ZIP=""; PREV_HOST=""; PREV_URL=""
TOKEN=""

while [ $# -gt 0 ]; do
    case "$1" in
        --dry-run) DRY=1 ;;
        --force) FORCE=1 ;;
        --finish) FINISH=1 ;;
        --open) OPEN=1 ;;
        --no-sync) NOSYNC=1 ;;
        --prune) PRUNE=1 ;;
        --no-props) PATCH_PROPS=0 ;;
        --host) HOST="$2"; shift ;;
        --repo) REPO="$2"; shift ;;
        --tag) TAG="$2"; shift ;;
        --token-file) TOKEN_FILE="$2"; shift ;;
        --server) SERVER="$2"; shift ;;
        --cdn) CDN="$2"; shift ;;
        --download) DOWNLOAD="$2"; shift ;;
        -h|--help) awk 'NR>1 && /^set / { exit } NR>1 { print }' "$0"; exit 0 ;;
        *) echo "Nieznany argument: $1"; exit 2 ;;
    esac
    shift
done

PROPS="$SERVER/server.properties"

fail() { echo "BLAD: $*"; exit 1; }

# ── Dokumentacja (tylko ostrzezenie) ─────────────────────────────────────────────────
# Linki docs/*.md nie maja wplywu na zawartosc paczki, wiec martwy link NIE blokuje
# publikacji — paczka ma wyjsc nawet przy zepsutej dokumentacji. Ostrzezenie ma tylko
# zwrocic uwage (twarda bramka jest w tools/publish-all.sh, przed buildami).
DOCCHECK="$ROOT/tools/check-docs-links.sh"
if [ -f "$DOCCHECK" ]; then
    if ! bash "$DOCCHECK" >/dev/null 2>&1; then
        echo "  UWAGA  martwy link w dokumentacji (AGENTS.md/README.md) — uruchom tools/check-docs-links.sh"
    fi
else
    echo "  UWAGA  brak $DOCCHECK — nie sprawdzilem linkow dokumentacji"
fi
ok()   { echo "  ok     $*"; }

# curl w Git Bash na Windows to program natywny (mingw64) i nie rozumie sciezek MSYS
# ("/c/Users/...", "/tmp/...") — kazda sciezke pliku podajemy mu jako "C:/...".
native() { if command -v cygpath >/dev/null 2>&1; then cygpath -m "$1"; else printf '%s' "$1"; fi; }

STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT

# ── Hosting: wspolna umowa trzech backendow ──────────────────────────────────────────────
# Kazdy backend umie dwie rzeczy:
#   *_probe <sha1>   -> wypisuje URL, jesli hosting JUZ ma te tresc (nic nie wysyla)
#   *_upload <sha1>  -> wysyla plik i wypisuje URL
# Zadna z nich nie musi byc prawdomowna: po uploadzie skrypt sam pobiera plik z URL-a
# i porownuje sha1 (sekcja 5), wiec bledny albo nieaktualny URL nie trafi do server.properties.
find_token() {
    TOKEN="${GITHUB_TOKEN:-${GH_TOKEN:-}}"
    if [ -z "$TOKEN" ] && [ -r "$TOKEN_FILE" ]; then
        TOKEN="$(tr -d '[:space:]' < "$TOKEN_FILE")"
    fi
}

detect_repo() {
    local url
    url="$(git -C "$ROOT" remote get-url origin 2>/dev/null || true)"
    case "$url" in
        git@github.com:*)       printf '%s' "${url#git@github.com:}" | sed 's/\.git$//' ;;
        ssh://git@github.com/*) printf '%s' "${url#ssh://git@github.com/}" | sed 's/\.git$//' ;;
        https://github.com/*)   printf '%s' "${url#https://github.com/}" | sed 's/\.git$//' ;;
    esac
}

# Bez jq (nie ma go na tej maszynie) — stringi z JSON-a wyciagamy grepem.
json_str() { # $1 = plik json, $2 = klucz
    grep -o "\"$2\"[[:space:]]*:[[:space:]]*\"[^\"]*\"" "$1" 2>/dev/null \
        | head -1 | sed 's/^[^:]*:[[:space:]]*"\(.*\)"$/\1/'
}

gh_api() { # $1 = metoda, $2 = sciezka, [$3 = cialo json] -> kod HTTP, odpowiedz w $STAGE/gh.json
    local args
    args=(-sS -m 120 -A "$UA" -X "$1"
          -H 'Accept: application/vnd.github+json'
          -H 'X-GitHub-Api-Version: 2022-11-28'
          -o "$(native "$STAGE/gh.json")" -w '%{http_code}')
    [ -n "$TOKEN" ] && args+=(-H "Authorization: Bearer $TOKEN")
    [ -n "${3:-}" ] && args+=(-d "$3")
    curl "${args[@]}" "$GH_API$2" 2>/dev/null
}

gh_check_repo() { # 0 = repo istnieje, jest publiczne i widoczne dla tokenu; powod w GH_MSG
    local code
    code="$(gh_api GET "/repos/$REPO")"
    case "$code" in
        200) if grep -q '"private":[[:space:]]*true' "$STAGE/gh.json"; then
                 GH_MSG="repozytorium $REPO jest prywatne — klient Minecraft pobiera paczke bez logowania, wiec taki URL nie zadziala"
                 return 1
             fi
             return 0 ;;
        401) GH_MSG="GitHub odrzucil token (HTTP 401)"; return 1 ;;
        403) GH_MSG="token nie ma uprawnien do $REPO (HTTP 403) — potrzebny zakres pozwalajacy tworzyc releases"; return 1 ;;
        404) GH_MSG="repo $REPO nie istnieje albo token go nie widzi (HTTP 404)"; return 1 ;;
        *)   GH_MSG="GET /repos/$REPO -> HTTP $code"; return 1 ;;
    esac
}

gh_release() { # ustawia GH_RELEASE_ID (puste, gdy takiego release nie ma)
    local code
    code="$(gh_api GET "/repos/$REPO/releases/tags/$TAG")"
    GH_RELEASE_ID=""
    if [ "$code" = 200 ]; then
        GH_RELEASE_ID="$(grep -o '"id":[[:space:]]*[0-9]*' "$STAGE/gh.json" | head -1 | grep -o '[0-9]*')"
    fi
}

gh_probe() { # $1 = sha1
    local url
    gh_release
    [ -n "$GH_RELEASE_ID" ] || return 0
    url="$(grep -o '"browser_download_url"[[:space:]]*:[[:space:]]*"[^"]*"' "$STAGE/gh.json" \
            | sed 's/.*"\(https[^"]*\)"$/\1/' | grep -F "pack-$1.zip" | head -1)"
    printf '%s' "$url"
}

gh_upload() { # $1 = sha1
    local code name="pack-$1.zip" url
    gh_release
    if [ -z "$GH_RELEASE_ID" ]; then
        echo "  nie ma release '$TAG' — tworze je (jeden release, do ktorego dopinaja sie kolejne wersje)"
        code="$(gh_api POST "/repos/$REPO/releases" \
            "{\"tag_name\":\"$TAG\",\"name\":\"Resource pack (Raft)\",\"body\":\"Paczka zasobow wysylana graczom przez server.properties (tools/publish-pack.sh). Kazda wersja to asset pack-<sha1>.zip.\"}")"
        [ "$code" = 201 ] || fail "nie udalo sie utworzyc release '$TAG' (HTTP $code): $(head -c 300 "$STAGE/gh.json")"
        gh_release
    fi
    [ -n "$GH_RELEASE_ID" ] || fail "nie udalo sie ustalic id release '$TAG'"
    echo "  wysylam $name ($SIZE B)..."
    code="$(curl -sS -m 600 -A "$UA" -X POST \
        -H "Authorization: Bearer $TOKEN" \
        -H 'Accept: application/vnd.github+json' \
        -H 'Content-Type: application/zip' \
        --data-binary "@$(native "$ZIP")" \
        -o "$(native "$STAGE/gh.json")" -w '%{http_code}' \
        "$GH_UPLOADS/repos/$REPO/releases/$GH_RELEASE_ID/assets?name=$name")"
    case "$code" in
        201) ok "asset przyjety (HTTP 201)" ;;
        422) echo "  asset $name juz jest na release — korzystam z niego" ;;
        *)   fail "upload assetu odrzucony (HTTP $code): $(head -c 300 "$STAGE/gh.json")" ;;
    esac
    url="$(json_str "$STAGE/gh.json" browser_download_url)"
    [ -n "$url" ] || url="https://github.com/$REPO/releases/download/$TAG/$name"
    printf '%s' "$url"
}

# Catbox nie indeksuje plikow po tresci (URL jest losowy), wiec na pytanie "czy juz wgrane"
# odpowiada stan publikacji: URL zapamietany dla tego samego sha1 i tego samego hostingu.
catbox_probe() { # $1 = sha1
    if [ -n "$PREV_URL" ] && [ "$PREV_ZIP" = "$1" ] && [ "$PREV_HOST" = catbox ]; then
        printf '%s' "$PREV_URL"
    fi
}

catbox_upload() { # $1 = sha1
    local try resp
    for try in 1 2 3; do
        resp="$(curl -sS -m 600 -A "$UA" -F 'reqtype=fileupload' \
                -F "fileToUpload=@$(native "$ZIP");type=application/zip" "$CATBOX_API" 2>&1)"
        case "$resp" in
            https://files.catbox.moe/*) printf '%s' "$resp"; return 0 ;;
        esac
        echo "  proba $try nieudana: $(printf '%s' "$resp" | tr -d '\r\n' | head -c 140)" >&2
        [ "$try" -lt 3 ] && sleep 6
    done
    return 1
}

mcpacks_probe() { # $1 = sha1
    hosted "$1" && printf '%s' "$DOWNLOAD/$1.zip"
}

mcpacks_upload() { # $1 = sha1
    local i
    echo "  probuje uploadu automatycznego ($SIZE B)..."
    HTTP="$(curl -sS -m 300 -o "$(native "$STAGE/upload.html")" -w '%{http_code}' \
        -A 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/140.0 Safari/537.36' \
        -H "Referer: $CDN/" -H "Origin: $CDN" \
        -F "file=@$(native "$ZIP");type=application/zip" "$CDN/" 2>&1)"
    if grep -qi "captcha" "$STAGE/upload.html" 2>/dev/null; then
        echo "  odrzucone: formularz wymaga CAPTCHY (HTTP $HTTP)"
        manual_upload_hint
        exit 3
    fi
    case "$HTTP" in
        2*) ok "upload przyjety (HTTP $HTTP)" ;;
        *)  fail "upload odrzucony (HTTP $HTTP): $(head -c 200 "$STAGE/upload.html" | tr -d '\n')" ;;
    esac
    for i in 1 2 3 4 5 6 7 8 9 10; do
        hosted "$1" && break
        sleep 3
    done
    hosted "$1" || { manual_upload_hint; fail "po uploadzie CDN nadal nie zna $1"; }
    ok "CDN widzi nowa wersje"
    printf '%s' "$DOWNLOAD/$1.zip"
}

host_probe()  { case "$HOST" in github) gh_probe "$1" ;; catbox) catbox_probe "$1" ;; mcpacks) mcpacks_probe "$1" ;; esac; }
host_upload() { case "$HOST" in github) gh_upload "$1" ;; catbox) catbox_upload "$1" ;; mcpacks) mcpacks_upload "$1" ;; esac; }

# Jedno miejsce, w ktorym sprawdzamy, ze pod URL-em lezy NASZ plik — dla kazdego backendu tak samo.
verify_download() { # $1 = URL, $2 = oczekiwany sha1
    local try code served
    for try in 1 2 3 4 5; do
        code="$(curl -sL -o "$(native "$STAGE/served.zip")" -w '%{http_code}' -m 180 "$1")"
        if [ "$code" = "200" ]; then
            served="$(sha1sum "$STAGE/served.zip" | cut -d' ' -f1)"
            if [ "$served" = "$2" ]; then
                ok "pobrane z $1 zgadza sie co do bajtu ($SIZE B)"
                return 0
            fi
            LAST_VERIFY_ERR="pod URL-em lezy plik o sha1 $served, a powinien $2"
        else
            LAST_VERIFY_ERR="URL $1 zwraca HTTP $code"
        fi
        [ "$try" -lt 5 ] && sleep 4
    done
    fail "$LAST_VERIFY_ERR"
}

setup_host() {
    find_token
    [ -n "$REPO" ] || REPO="$(detect_repo)"
    if [ -z "$TOKEN" ]; then
        HOST_HINT="brak tokenu GitHub (szukalem \$GITHUB_TOKEN, \$GH_TOKEN i $TOKEN_FILE)"
    elif [ -z "$REPO" ]; then
        HOST_HINT="jest token, ale nie wiem ktore repo (--repo owner/name albo remote origin)"
    else
        HOST_HINT=""
    fi
    case "$HOST" in
        auto)
            if [ -n "$HOST_HINT" ]; then
                HOST=catbox; HOST_REASON="auto: $HOST_HINT"
            elif gh_check_repo; then
                HOST=github; HOST_REASON="auto: token + publiczne repo $REPO"
            else
                HOST=catbox; HOST_REASON="auto: $GH_MSG"
            fi ;;
        github)
            [ -n "$TOKEN" ] || fail "host github: $HOST_HINT"
            [ -n "$REPO" ]  || fail "host github: nie podano repozytorium (--repo owner/name)"
            gh_check_repo || fail "host github: $GH_MSG"
            HOST_REASON="token + repo $REPO" ;;
        catbox)  HOST_REASON="wymuszony (--host catbox)" ;;
        mcpacks) HOST_REASON="wymuszony (--host mcpacks), upload reczny (formularz ma CAPTCHE)" ;;
        *) fail "nieznany hosting: $HOST (auto|github|catbox|mcpacks)" ;;
    esac
    case "$HOST" in
        github)  echo "Hosting: GitHub Releases — repo $REPO, tag '$TAG' ($HOST_REASON)" ;;
        catbox)  echo "Hosting: catbox.moe ($HOST_REASON)" ;;
        mcpacks) echo "Hosting: mc-packs.net ($HOST_REASON)" ;;
    esac
}

open_cdn() {
    [ "$OPEN" -eq 1 ] || return 0
    local page
    case "$HOST" in
        github) page="https://github.com/$REPO/releases/tag/$TAG" ;;
        catbox) page="https://catbox.moe/" ;;
        *)      page="$CDN/" ;;
    esac
    case "$(uname -s)" in
        MINGW*|MSYS*|CYGWIN*) cmd //c start "" "$page" >/dev/null 2>&1 || true ;;
        Darwin) open "$page" >/dev/null 2>&1 || true ;;
        *) xdg-open "$page" >/dev/null 2>&1 || true ;;
    esac
}

manual_upload_hint() {
    echo
    echo "  Ta wersja paczki NIE jest jeszcze na $CDN (hash $SHA1)."
    echo "  Formularz ma CAPTCHA, wiec nie da sie jej wgrac automatem — zrob to raz recznie:"
    echo "    1. otworz $CDN/"
    echo "    2. przeciagnij plik: $ZIP"
    echo "    3. na stronie sprawdz, ze SHA-1 to dokladnie: $SHA1"
    echo "    4. uruchom: tools/publish-pack.sh --finish"
    echo "       (skrypt sprawdzi pobranie i wpisze hash do server.properties)"
    open_cdn
}

hosted() { # $1 = sha1
    curl -s -m 30 "$CDN/inc/check.php?hash=$1" 2>/dev/null | grep -q '"file":"true"'
}

for tool in jar sha1sum curl unzip; do
    command -v "$tool" >/dev/null || fail "brak narzedzia: $tool"
done
[ -d "$PACK" ] || fail "nie ma katalogu paczki: $PACK"

# Co wchodzi do paczki: TYLKO to. `pack/` zawiera tez pliki deweloperskie (build.gradle,
# gradle/, run/, tools/) — one nie moga trafic ani do zipa, ani do kopii dla graczy.
CONTENT=(pack.mcmeta assets)
[ -d "$PACK/overlays" ] && CONTENT+=(overlays)

echo "Paczka:  $PACK"
echo "Zawartosc: ${CONTENT[*]}"

# ── 0. Hosting ───────────────────────────────────────────────────────────────────────
echo
echo "=== Hosting paczki ==="
setup_host

# ── 1. Kopie paczki: modpack + lokalna kopia testowa ─────────────────────────────────
# Trzy kopie rozjezdzaja sie latwo: pracujesz w pack/, a gracze dostaja modpack/resourcepacks
# albo pack.zip. Ten krok pilnuje, zeby zaden z nich nie zostal na starej wersji.
# Hash zawartosci CONTENT w danym katalogu. Liczony od wewnatrz (sciezki wzgledne), wiec dwa
# katalogi z identyczna zawartoscia daja ten sam hash — tak porownujemy kopie z pack/.
# Wykluczone: zrodla Blockbencna (*.bbmodel) i smieci — te nie jada do graczy.
content_hash_root() {
    local root="$1" entry
    (
        cd "$root" || exit 1
        for entry in "${CONTENT[@]}"; do
            [ -e "$entry" ] || continue
            find "$entry" -type f \
                ! -name '*.bbmodel' ! -name '*.bak*' ! -name '*.tmp' ! -name '.DS_Store' -print
        done | LC_ALL=C sort | xargs -d '\n' sha1sum | sha1sum
    ) | cut -d' ' -f1
}

# Lista "sciezka sha1" dla CONTENT w danym katalogu — do porownan miedzy kopiami.
manifest() {
    local root="$1" entry file
    (
        cd "$root" || exit 1
        for entry in "${CONTENT[@]}"; do
            [ -e "$entry" ] || continue
            find "$entry" -type f \
                ! -name '*.bbmodel' ! -name '*.bak*' ! -name '*.tmp' ! -name '.DS_Store' -print
        done | LC_ALL=C sort | while IFS= read -r file; do
            printf '%s %s\n' "$file" "$(sha1sum "$file" | cut -d' ' -f1)"
        done
    )
}

DRIFT=0
SYNCED=0

# Porownanie kopii z pack/. Wypisuje roznice; nic nie zmienia (od tego jest apply_sync).
# Rozrozniamy trzy przypadki, bo znacza co innego: brakujaca/inna tresc = gracze maja stare,
# "tylko w kopii" = plik, ktorego nie ma w zrodle (usuwamy go dopiero z --prune).
report_copy() { # $1 = katalog docelowy
    local dst="$1" a b out
    LAST_COPY_DRIFT=0
    if [ ! -d "$dst" ]; then
        echo "  (pomijam, nie ma katalogu: $dst)"
        return 0
    fi
    a="$(mktemp)"; b="$(mktemp)"
    manifest "$PACK" > "$a"; manifest "$dst" > "$b"
    out="$(join -a1 -a2 -e '-' -o 0,1.2,2.2 "$a" "$b" | awk '
        $2==$3 { next }
        $3=="-" { print "brakuje       " $1; next }
        $2=="-" { print "tylko w kopii " $1; next }
        { print "inna tresc    " $1 }')"
    if [ -z "$out" ]; then
        LAST_COPY_DRIFT=0
        ok "zgodne z pack/: $dst"
    else
        LAST_COPY_DRIFT=1
        DRIFT=1
        echo "  ROZJAZD z pack/: $dst"
        echo "$out" | sed 's/^/    /'
    fi
    rm -f "$a" "$b"
}

# Kopiuje CONTENT z pack/ do kopii. Bezpieczne: nadpisuje nowszym, niczego nie kasuje.
# Kasowanie plikow, ktorych w zrodle nie ma, robi dopiero --prune (swiadoma decyzja).
apply_sync() { # $1 = katalog docelowy
    local dst="$1" entry
    [ -d "$dst" ] || return 0
    if [ "$DRY" -eq 1 ]; then
        echo "  --dry-run: skopiowalbym CONTENT z pack/ do $dst"
        return 0
    fi
    for entry in "${CONTENT[@]}"; do
        cp -rp "$PACK/$entry" "$dst/" 2>/dev/null || cp -r "$PACK/$entry" "$dst/"
    done
    # Zrodla Blockbencna (*.bbmodel) i kopie zapasowe nie jada do graczy — manifest je pomija,
    # wiec gdyby zostaly w kopii, rozjazdu nie byloby widac w raporcie. Kasujemy je po skopiowaniu
    # (cp -rp bierze caly katalog, wiec samo pomijanie w manifescie nie wystarczy).
    find "$dst" -type f \( -name '*.bbmodel' -o -name '*.bak*' -o -name '*.tmp' -o -name '.DS_Store' \) -delete
    ok "zaktualizowane z pack/: $dst"
}

# Usuwa z kopii pliki, ktorych nie ma w pack/ (i puste katalogi po nich).
apply_prune() { # $1 = katalog docelowy
    local dst="$1" file removed=0
    [ -d "$dst" ] || return 0
    if [ "$DRY" -eq 1 ]; then
        echo "  --dry-run: usunalbym z $dst pliki, ktorych nie ma w pack/"
        return 0
    fi
    while IFS= read -r file; do
        [ -n "$file" ] || continue
        [ -e "$PACK/$file" ] && continue
        rm -f "$dst/$file"
        echo "    usuniete z kopii: $file"
        removed=$((removed + 1))
    done < <( cd "$dst" && for entry in "${CONTENT[@]}"; do
                  [ -e "$entry" ] || continue
                  find "$entry" -type f \
                      ! -name '*.bbmodel' ! -name '*.bak*' ! -name '*.tmp' ! -name '.DS_Store' -print
              done )
    find "$dst/assets" -type d -empty -delete 2>/dev/null || true
    [ "$removed" -gt 0 ] && ok "usunieto $removed nadmiarowych plikow z $dst"
}

echo
echo "=== Kopie paczki ==="
report_copy "$MODPACK_COPY"
MODPACK_DRIFT=$LAST_COPY_DRIFT
report_copy "$TEST_COPY"
TEST_DRIFT=$LAST_COPY_DRIFT
if [ "$DRIFT" -eq 1 ]; then
    if [ "$NOSYNC" -eq 1 ]; then
        echo "  (--no-sync: zostawiam kopie jak sa)"
    else
        [ "$DRY" -eq 0 ] && SYNCED=1
        [ "$MODPACK_DRIFT" -eq 1 ] && apply_sync "$MODPACK_COPY"
        [ "$TEST_DRIFT" -eq 1 ] && apply_sync "$TEST_COPY"
        if [ "$PRUNE" -eq 1 ]; then
            [ "$MODPACK_DRIFT" -eq 1 ] && apply_prune "$MODPACK_COPY"
            [ "$TEST_DRIFT" -eq 1 ] && apply_prune "$TEST_COPY"
        fi
        if [ "$DRY" -eq 0 ] && command -v packwiz >/dev/null 2>&1 && [ -f "$ROOT/modpack/pack.toml" ]; then
            if ( cd "$ROOT/modpack" && packwiz refresh >/dev/null 2>&1 ); then
                ok "packwiz refresh (index.toml modpacka)"
            else
                echo "  UWAGA: packwiz refresh nie udalo sie — odswiez recznie"
            fi
        fi
    fi
fi

# ── 2. Czy w ogole jest co publikowac ────────────────────────────────────────────────
# Hash zawartosci zrodel (bez zrodel Blockbencna i smieci — te nie jada do graczy).
SRC_HASH="$(content_hash_root "$PACK")"
if [ -f "$STATE" ]; then
    PREV_SRC="$(sed -n 's/^src=//p' "$STATE" | head -1)"
    PREV_ZIP="$(sed -n 's/^zip=//p' "$STATE" | head -1)"
    PREV_HOST="$(sed -n 's/^host=//p' "$STATE" | head -1)"
    PREV_URL="$(sed -n 's/^url=//p' "$STATE" | head -1)"
fi

echo
echo "Zrodla:  sha1=$SRC_HASH"
[ -n "$PREV_SRC" ] && echo "Poprzednio: sha1=$PREV_SRC (zip ${PREV_ZIP:-?})"

if [ "$FORCE" -eq 0 ] && [ -n "$PREV_SRC" ] && [ "$SRC_HASH" = "$PREV_SRC" ] && [ -f "$ZIP" ]; then
    if [ "$PATCH_PROPS" -eq 1 ] && [ -f "$PROPS" ]; then
        current="$(sed -n 's/^resource-pack-sha1=\(.*\)$/\1/p' "$PROPS" | head -1)"
        current_url="$(sed -n 's/^resource-pack=\(.*\)$/\1/p' "$PROPS" | head -1 | sed 's/\\:/:/g')"
        if [ "$current" != "$PREV_ZIP" ] || { [ -n "$PREV_URL" ] && [ "$current_url" != "$PREV_URL" ]; }; then
            echo
            echo "UWAGA: server.properties wskazuje ${current_url:-?} (sha1 $current),"
            echo "       a ostatnia publikacja to ${PREV_URL:-?} (sha1 $PREV_ZIP)."
            echo "       Uruchom z --force, zeby doprowadzic to do porzadku."
            exit 1
        fi
    fi
    echo "Nic sie nie zmienilo w zrodlach paczki od ostatniej publikacji — nic do zrobienia."
    if [ "$DRIFT" -eq 1 ] && [ "$SYNCED" -eq 0 ]; then
        echo "UWAGA: kopie paczki sie roznialy (lista wyzej) — uruchom z --sync, zeby je zrownac."
    fi
    exit 0
fi

# ── 3. Build (bez META-INF, bez .bbmodel, bez plikow deweloperskich) ─────────────────
echo
echo "=== Buduje paczke ==="
for entry in "${CONTENT[@]}"; do
    cp -rp "$PACK/$entry" "$STAGE/"
done
find "$STAGE" -type f \( -name '*.bbmodel' -o -name '*.bak*' -o -name '*.tmp' -o -name '.DS_Store' \) -delete
find "$STAGE" -type d -empty -delete
# Stala data wszystkich wpisow: ten sam zestaw plikow daje BAJT W BAJT ten sam zip (i sha1),
# wiec "nic sie nie zmienilo" da sie rozpoznac bez zgadywania, a nie po przypadkowym hashu.
find "$STAGE" -print0 | xargs -0 touch -t 202601010000

rm -f "$ZIP"
# Budujemy z wnetrza katalogu roboczego i na sciezkach WZGLEDNYCH: jar to program Javy,
# a ten nie rozumie sciezek MSYS ("C:/tmp/..." zamiast "/tmp/...").
# -M = bez MANIFEST.MF (klient go ignoruje, wiec lepiej nie wysylac smieci)
( cd "$STAGE" && jar --create --file pack.zip -M "${CONTENT[@]}" ) || fail "jar nie zbudowal paczki"
mv "$STAGE/pack.zip" "$ZIP" || fail "nie udalo sie przeniesc pack.zip na miejsce"

SHA1="$(sha1sum "$ZIP" | cut -d' ' -f1)"
FILES="$(unzip -l "$ZIP" | tail -1 | awk '{print $2}')"
SIZE="$(wc -c < "$ZIP")"
ok "pack.zip: $FILES plikow, $SIZE B, sha1=$SHA1"

# Kontrola wnetrza: nic poza pack.mcmeta/assets/overlays (zaden build.gradle, gradle/, run/).
top="$(unzip -l "$ZIP" | awk 'NR>3 && $4 != "" {print $4}' | cut -d/ -f1 | sort -u | tr '\n' ' ')"
case "$top" in
    "assets overlays pack.mcmeta "|"assets pack.mcmeta ") ok "wnetrze zipa: $top" ;;
    *) fail "w zipie sa nieoczekiwane wpisy: $top" ;;
esac
unzip -l "$ZIP" | grep -q "assets/minecraft/items/" || fail "w zipie nie ma item definitions"

# ── 4. Hosting: sprawdz / wgraj ──────────────────────────────────────────────────────
echo
echo "=== Wysylka paczki ==="
HOSTED_URL="$(host_probe "$SHA1")"
if [ -n "$HOSTED_URL" ]; then
    ok "ta wersja jest juz na hostingu: $HOSTED_URL"
elif [ "$DRY" -eq 1 ]; then
    echo "  --dry-run: pomijam upload (hosting nie ma jeszcze $SHA1)"
elif [ "$FINISH" -eq 1 ]; then
    [ "$HOST" = mcpacks ] && manual_upload_hint
    fail "--finish, ale hosting nie zna $SHA1 (uzyj --force, zeby wgrac)"
else
    HOSTED_URL="$(host_upload "$SHA1")"
    [ -n "$HOSTED_URL" ] || fail "hosting nie zwrocil URL-a"
    ok "nowy URL: $HOSTED_URL"
fi

# ── 5. Kontrola: czy to, co lezy pod URL-em, ma nasz hash ────────────────────────────
NEW_URL="$HOSTED_URL"
if [ "$DRY" -eq 0 ]; then
    echo
    echo "=== Kontrola pobrania ==="
    [ -n "$NEW_URL" ] || fail "hosting nie zwrocil URL-a dla tej wersji"
    verify_download "$NEW_URL" "$SHA1"
fi

# ── 6. server.properties ─────────────────────────────────────────────────────────────
if [ "$PATCH_PROPS" -eq 1 ] && [ -n "$NEW_URL" ]; then
    echo
    echo "=== server.properties ($PROPS) ==="
    if [ ! -f "$PROPS" ]; then
        echo "  (brak pliku — pomijam; podaj katalog serwera przez --server)"
    else
        OLD_URL="$(sed -n 's/^resource-pack=\(.*\)$/\1/p' "$PROPS" | head -1)"
        OLD_URL="${OLD_URL//\\:/:}"
        OLD_SHA="$(sed -n 's/^resource-pack-sha1=\(.*\)$/\1/p' "$PROPS" | head -1)"
        echo "  bylo:  $OLD_URL"
        echo "         sha1=$OLD_SHA"
        # Zapisywane ZAWSZE jak Java/Bukkit: ':' w wartosci jako '\:'. Oba warianty parsuja sie
        # tak samo, ale ten jest odporny na parsery traktujace ':' jako separator klucza
        # (a tak wlasnie robi java.util.Properties dla PIERWSZEGO ':' w linii).
        VALUE="${NEW_URL//:/\\:}"
        if [ "$DRY" -eq 1 ]; then
            echo "  --dry-run: wpisalby  resource-pack=$VALUE"
            echo "                        resource-pack-sha1=$SHA1"
        else
            BAK="$PROPS.bak.$(date +%Y%m%d%H%M%S)"
            cp -p "$PROPS" "$BAK"
            # Przepisujemy linie w bashu, nie w awk-u: awk interpretuje "\:" w argumencie jako
            # ":" (i ostrzega), a Bukkit zapisuje ':' w wartosciach jako "\:" — chcemy zachowac
            # ten styl, bo tak jest zapisana reszta pliku (np. resource-pack-prompt).
            while IFS= read -r line || [ -n "$line" ]; do
                case "$line" in
                    resource-pack=*)      printf 'resource-pack=%s\n' "$VALUE" ;;
                    resource-pack-sha1=*) printf 'resource-pack-sha1=%s\n' "$SHA1" ;;
                    *)                    printf '%s\n' "$line" ;;
                esac
            done < "$BAK" > "$PROPS"
            grep -q "^resource-pack-sha1=$SHA1$" "$PROPS" || fail "nie udalo sie wpisac hasha do $PROPS"
            ok "wpisane; kopia starego: $(basename "$BAK")"
        fi
    fi
elif [ "$PATCH_PROPS" -eq 1 ]; then
    echo
    echo "=== server.properties ($PROPS) ==="
    echo "  (pomijam: nie znam URL-a — ta wersja nie jest jeszcze na hostingu)"
fi

# ── 7. Stan + podsumowanie ───────────────────────────────────────────────────────────
if [ "$DRY" -eq 0 ]; then
    printf 'src=%s\nzip=%s\nhost=%s\nurl=%s\ndate=%s\n' \
        "$SRC_HASH" "$SHA1" "$HOST" "$HOSTED_URL" "$(date +%Y-%m-%dT%H:%M:%S)" > "$STATE"
fi

echo
echo "=== Podsumowanie ==="
echo "  paczka:   $ZIP ($FILES plikow, $SIZE B)"
echo "  sha1:     $SHA1"
echo "  URL:      ${NEW_URL:-(brak — ta wersja nie jest jeszcze na hostingu)}"
echo "  hosting:  $HOST ($HOST_REASON)"
echo "  kopie:    pack/ + ${MODPACK_COPY#$ROOT/} + .minecraft/resourcepacks/raft-hud"
if [ "$DRY" -eq 1 ]; then
    [ "$DRIFT" -eq 1 ] && echo "            --dry-run: kopie NIE zostaly zmienione"
elif [ "$DRIFT" -eq 0 ]; then
    echo "            wszystkie zgodne z pack/"
elif [ "$SYNCED" -eq 1 ]; then
    echo "            zaktualizowane z pack/; pliki 'tylko w kopii' zostawione (--prune je usunie)"
else
    echo "            UWAGA: kopie sie roznialy — lista wyzej (uzyj --sync/--prune)"
fi
echo "  serwer:   server.properties jest czytane przy STARCIE — jesli serwer dziala,"
echo "            nowa paczka wejdzie dopiero po restarcie (klienci pobiora ja przy wejsciu)"
[ "$DRY" -eq 1 ] && echo "  (--dry-run: nic nie zostalo wyslane ani zapisane)"
echo "  zrodla Blockbencna (*.bbmodel) i pliki deweloperskie pack/ nie trafiaja do paczki"
exit 0
