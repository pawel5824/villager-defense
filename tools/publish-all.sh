#!/usr/bin/env bash
# Jedna publikacja calego projektu: mod (gracze) + plugin (serwer) + paczka zasobow (wszyscy).
#
# Po co: do tej pory byly trzy osobne sciezki, kazda z innym zwyczajem i wlasnym pytaniem
# "na pewno?":
#   mod    -> modpack/build-and-publish.bat  (gradle build + modpack + commit + push)
#   plugin -> build-and-deploy.ps1           (mvn clean package + kopia do plugins/)
#   paczka -> tools/publish-pack.sh          (zip + upload przez API + server.properties)
# Ten skrypt robi wszystkie trzy w jednej kolejnosci i NIE PYTA O NIC.
#
# Kolejnosc jest celowa: NAJPIERW wszystkie buildy (nie dotykaja niczego na zewnatrz),
# DOPIERO POTEM publikacje. Nieudana kompilacja nie zostawia polowicznie wyslanej wersji.
#
#   1/5 build moda       pack/gradlew build                 -> pack/build/libs/raft-hud-<w>.jar
#   2/5 build pluginu    mvn clean package                 -> target/ServerPlugin.jar
#   3/5 mod do graczy    jar -> modpack/mods + packwiz refresh + kontrole hashy
#                        + git commit --only (3 pliki) + git push
#   4/5 plugin na serwer kopia zapasowa + plugins/ServerPlugin.jar (+ kontrola sha256)
#   5/5 paczka zasobow   tools/publish-pack.sh --server <serwer>
#
# Uzycie:
#   tools/publish-all.sh [--skip-mod] [--skip-plugin] [--skip-pack]
#                        [--no-push] [--dry-run] [--server <katalog>]
#
#   --skip-mod     nie buduj i nie publikuj moda (gracze)
#   --skip-plugin  nie buduj i nie deployuj pluginu na serwer
#   --skip-pack    nie publikuj paczki zasobow
#   --no-push      zbuduj moda i przygotuj modpack, ale nie commituj i nie pushuj
#   --dry-run      tylko kontrole i plan; nic nie buduje, nie wysyla, nie zapisuje
#   --server       katalog serwera (domyslnie $VD_SERVER albo "<rodzic repo>/VD - serwer")
#
# Bezpieczenstwo: commit obejmuje WYLACZNIE modpack/index.toml, modpack/pack.toml
# i modpack/mods/raft-hud-<wersja>.jar (git commit --only), a skrypt sprawdza potem, co
# naprawde weszlo do commita i nie pushuje, jesli trafilo tam cos spoza tej listy.
# Cudza robota przygotowana w indeksie (git add) zostaje nietknieta.
# Push idzie z GIT_TERMINAL_PROMPT=0, wiec skrypt nigdy nie stanie i nie zapyta o haslo —
# brak danych logowania to blad, nie zawieszenie.
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MOD="$ROOT/pack"
MODPACK="$ROOT/modpack"
PACKPUB="$ROOT/tools/publish-pack.sh"
PLUGIN_JAR="ServerPlugin.jar"
SERVER="${VD_SERVER:-$(cd "$ROOT/.." && pwd)/VD - serwer}"
PLUGINS="$SERVER/plugins"
KEEP_BACKUPS=2

SKIP_MOD=0
SKIP_PLUGIN=0
SKIP_PACK=0
NOPUSH=0
DRY=0

while [ $# -gt 0 ]; do
    case "$1" in
        --skip-mod)    SKIP_MOD=1 ;;
        --skip-plugin) SKIP_PLUGIN=1 ;;
        --skip-pack)   SKIP_PACK=1 ;;
        --no-push)     NOPUSH=1 ;;
        --dry-run)     DRY=1 ;;
        --server)      SERVER="$2"; PLUGINS="$SERVER/plugins"; shift ;;
        -h|--help)     awk 'NR>1 && /^set / { exit } NR>1 { print }' "$0"; exit 0 ;;
        *) echo "Nieznany argument: $1"; exit 2 ;;
    esac
    shift
done

step() { echo; echo "=== $* ==="; }
ok()   { echo "  ok     $*"; }
info() { echo "  ..     $*"; }
skip() { echo "  (pominiete) $*"; }
fail() { echo; echo "BLAD: $*"; exit 1; }

# Brakujacy warunek: na powaznie przerywa (fail), a w --dry-run tylko ostrzega i leci dalej,
# zeby dalo sie zobaczyc CALY plan nawet gdy cos nie jest jeszcze skonfigurowane.
MISSING=0
need() {
    if [ "$DRY" -eq 1 ]; then
        printf '  !!     %s\n' "$1"
        MISSING=1
    else
        fail "$1"
    fi
}

REMOTE=""
BRANCH=""
MODJAR=""

mod_version() {
    local v
    v="$(sed -n 's/^mod_version=//p' "$MOD/gradle.properties" 2>/dev/null | head -1)"
    [ -n "$v" ] || fail "nie umiem odczytac mod_version z $MOD/gradle.properties"
    printf '%s' "$v"
}

sha_of() { sha256sum "$1" 2>/dev/null | cut -d' ' -f1; }

stage_line() { # $1 = numer/opis, $2 = stan
    printf '  %-22s %s\n' "$1" "$2"
}

# ── Kontrole wstepne ─────────────────────────────────────────────────────────────────
# Wszystko, co moze wywalic publikacje w polowie, sprawdzamy PRZED pierwszym buildem.
preflight() {
    step "Kontrole wstepne"

    # Dokumentacja: linki docs/*.md w AGENTS.md i README.md musza istniec, inaczej nowy
    # agent dostaje martwe odnosniki. Kontrola jest zawsze (nie zalezy od --skip-*),
    # bo dokumentacja nie jest czescia zadnego etapu publikacji.
    local doccheck="$ROOT/tools/check-docs-links.sh"
    if [ ! -f "$doccheck" ]; then
        need "brak $doccheck"
    elif ! bash "$doccheck" >/dev/null 2>&1; then
        bash "$doccheck" 2>&1 | sed -n 's/^BRAK  /    /p'
        need "martwy link w dokumentacji — uruchom tools/check-docs-links.sh"
    else
        ok "linki dokumentacji (AGENTS.md, README.md)"
    fi

    # Sprawdzamy TYLKO to, czego potrzebuje uruchamiany etap — inaczej `--skip-pack`
    # wywalalby sie na braku skryptu paczki, ktorego w tym przebiegu nikt nie wola.
    [ "$SKIP_PACK" -eq 0 ] && { [ -f "$PACKPUB" ] || need "brak $PACKPUB"; }
    info "serwer: $SERVER"

    if [ "$SKIP_MOD" -eq 0 ]; then
        [ -d "$MOD" ] || need "brak katalogu moda: $MOD"
        [ -d "$MODPACK" ] || need "brak katalogu modpacka: $MODPACK"
        if [ -f "$MOD/gradle.properties" ]; then
            MODJAR="raft-hud-$(mod_version).jar"
        else
            MODJAR="raft-hud-<wersja>.jar"
            need "brak $MOD/gradle.properties — nie umiem ustalic nazwy jara moda"
        fi
        info "mod:    $MODJAR"
        command -v packwiz >/dev/null 2>&1 || need "brak packwiz w PATH — bez niego mod nie trafi do modpacka"
        if [ ! -f "$MOD/gradlew.bat" ] && [ ! -f "$MOD/gradlew" ]; then
            need "brak gradlew w $MOD (nie ma czym zbudowac moda)"
        fi
        if [ -e "$MODPACK/mods/raft-hud.pw.toml" ]; then
            need "istnieje modpack/mods/raft-hud.pw.toml — ten plik ciagnie moda z wydania GitHub i dubluje wpis dla tego samego jara; usun go"
        fi
    fi

    if [ "$SKIP_PLUGIN" -eq 0 ]; then
        command -v mvn >/dev/null 2>&1 || need "brak mvn w PATH"
        [ -d "$PLUGINS" ] || need "brak katalogu wtyczek serwera: $PLUGINS"
    fi

    # Mod trafia do graczy przez repo (packwiz), wiec bez remote nie ma czego wypchnac.
    if [ "$SKIP_MOD" -eq 0 ] && [ "$NOPUSH" -eq 0 ]; then
        git -C "$ROOT" rev-parse --git-dir >/dev/null 2>&1 || need "to nie jest repozytorium git"
        REMOTE="$(git -C "$ROOT" remote 2>/dev/null | head -1)"
        if [ -z "$REMOTE" ]; then
            need "repo nie ma zadnego remote, wiec mod nie ma jak trafic do graczy.
       Dodaj go raz:  git remote add origin <adres-repo>
       Albo uruchom z --no-push (zbuduje i przygotuje modpack bez commita i pusha)."
        fi
        BRANCH="$(git -C "$ROOT" rev-parse --abbrev-ref HEAD 2>/dev/null)"
        [ "$BRANCH" != "HEAD" ] || need "repo jest w detached HEAD — nie wiadomo, co wypchnac"
        [ -n "$REMOTE" ] && info "git:    $REMOTE${BRANCH:+ / $BRANCH}"
        local staged
        staged="$(git -C "$ROOT" diff --cached --name-only 2>/dev/null)"
        if [ -n "$staged" ]; then
            info "uwaga: w indeksie gita sa juz przygotowane zmiany — commit tego skryptu ich NIE dotknie:"
            printf '%s\n' "$staged" | sed 's/^/           /'
        fi
    fi

    if [ "$DRY" -eq 1 ]; then
        info "tryb --dry-run: bez buildow, bez wysylania, bez zapisow"
    fi
}

# ── 1/5 Build moda ───────────────────────────────────────────────────────────────────
build_mod() {
    step "1/5 build moda (klient)"
    if [ "$SKIP_MOD" -eq 1 ]; then skip "mod (--skip-mod)"; return 0; fi
    if [ "$DRY" -eq 1 ]; then info "--dry-run: pominieto gradle build"; return 0; fi
    local log="$ROOT/tools/.publish-all-gradle.log" rc
    info "gradle build (log: tools/.publish-all-gradle.log)"
    if [ -x "$MOD/gradlew" ]; then
        # Skrypt powloki (dziala w Git Bash). Uwaga: `cmd //c gradlew.bat` NIE dziala —
        # cmd nie szuka .bat w katalogu biezacym, trzeba podac sciezke ("\.\gradlew.bat").
        ( cd "$MOD" && ./gradlew build --console=plain ) >"$log" 2>&1
    elif [ -f "$MOD/gradlew.bat" ]; then
        ( cd "$MOD" && cmd //c ".\\gradlew.bat" build --console=plain ) >"$log" 2>&1
    else
        fail "brak gradlew w $MOD (nie ma czym zbudowac moda)"
    fi
    rc=$?
    if [ "$rc" -ne 0 ]; then
        tail -15 "$log" | sed 's/^/    /'
        fail "gradle build nie powiodl sie (kod $rc) — pelny log: tools/.publish-all-gradle.log"
    fi
    [ -f "$MOD/build/libs/$MODJAR" ] || fail "gradle nie wyprodukowal $MOD/build/libs/$MODJAR"
    ok "zbudowany $MODJAR ($(wc -c < "$MOD/build/libs/$MODJAR") B, sha256 $(sha_of "$MOD/build/libs/$MODJAR" | cut -c1-12)…)"
    rm -f "$log"
}

# ── 2/5 Build pluginu ────────────────────────────────────────────────────────────────
build_plugin() {
    step "2/5 build pluginu (serwer)"
    if [ "$SKIP_PLUGIN" -eq 1 ]; then skip "plugin (--skip-plugin)"; return 0; fi
    if [ "$DRY" -eq 1 ]; then info "--dry-run: pominieto mvn clean package"; return 0; fi
    info "mvn clean package (log: build.log)"
    ( cd "$ROOT" && mvn clean package ) >"$ROOT/build.log" 2>&1
    local rc=$?
    if [ "$rc" -ne 0 ]; then
        tail -25 "$ROOT/build.log" | sed 's/^/    /'
        fail "mvn clean package nie powiodl sie (kod $rc) — pelny log: build.log"
    fi
    [ -f "$ROOT/target/$PLUGIN_JAR" ] || fail "brak target/$PLUGIN_JAR — sprawdz <finalName> w pom.xml"
    ok "zbudowany target/$PLUGIN_JAR ($(wc -c < "$ROOT/target/$PLUGIN_JAR") B, sha256 $(sha_of "$ROOT/target/$PLUGIN_JAR" | cut -c1-12)…)"
}

# ── 3/5 Mod do graczy ────────────────────────────────────────────────────────────────
publish_mod() {
    step "3/5 mod do graczy (modpack + git)"
    if [ "$SKIP_MOD" -eq 1 ]; then skip "mod (--skip-mod)"; return 0; fi
    if [ "$DRY" -eq 1 ]; then
        info "--dry-run: skopiowalbym build/libs/$MODJAR -> modpack/mods/ i zrobil packwiz refresh"
        info "--dry-run: stan modpacka w git (co poszloby w commit):"
        git -C "$ROOT" status --short -- "modpack/index.toml" "modpack/pack.toml" "modpack/mods/$MODJAR" 2>/dev/null | sed 's/^/           /'
        return 0
    fi

    cp -f "$MOD/build/libs/$MODJAR" "$MODPACK/mods/$MODJAR" || fail "nie udalo sie skopiowac jara do modpacka"
    local jar_sha index_sha
    jar_sha="$(sha_of "$MODPACK/mods/$MODJAR")"
    ok "modpack/mods/$MODJAR (sha256 $(printf '%s' "$jar_sha" | cut -c1-12)…)"

    ( cd "$MODPACK" && packwiz refresh >/dev/null 2>&1 ) || fail "packwiz refresh nie powiodl sie (czy packwiz jest w PATH?)"
    index_sha="$(sha_of "$MODPACK/index.toml")"
    grep -q "$index_sha" "$MODPACK/pack.toml" || fail "hash index.toml nie trafil do pack.toml — modpack jest niespojny"
    grep -q "$jar_sha" "$MODPACK/index.toml" || fail "hash jara nie trafil do index.toml — modpack jest niespojny"
    ok "indeks packwiz spojny (jar i index.toml zapięte hashami)"

    if [ "$NOPUSH" -eq 1 ]; then
        info "--no-push: modpack przygotowany, ale bez commita i bez pusha"
        return 0
    fi

    local paths=( "modpack/index.toml" "modpack/pack.toml" "modpack/mods/$MODJAR" )
    if [ -z "$(git -C "$ROOT" status --porcelain -- "${paths[@]}")" ]; then
        info "mod w repo jest juz aktualny (jar i indeks takie same jak w HEAD) — nie ma czego commitowac"
        return 0
    fi
    info "commit tylko tych plikow: ${paths[*]}"
    git -C "$ROOT" commit --only -m "raft-hud $(mod_version): aktualizacja moda [packwiz]" -- "${paths[@]}" >/dev/null \
        || fail "git commit nie powiodl sie"

    # Kontrola: w commicie ma byc WYLACZNIE to, co ten skrypt generuje.
    local f foreign=0
    while IFS= read -r f; do
        [ -n "$f" ] || continue
        case " ${paths[*]} " in
            *" $f "*) ;;
            *) foreign=1; echo "    obcy plik w commicie: $f" ;;
        esac
    done < <( git -C "$ROOT" show --pretty=format: --name-only HEAD )
    [ "$foreign" -eq 0 ] || fail "do commita trafilo cos spoza listy — nie pushuje (cofnij: git reset --soft HEAD~1)"
    ok "commit zawiera tylko: ${paths[*]}"

    # GIT_TERMINAL_PROMPT=0: brak danych logowania = blad, a nie zawieszenie na pytaniu.
    GIT_TERMINAL_PROMPT=0 git -C "$ROOT" push -u "$REMOTE" "$BRANCH" || fail "git push nie powiodl sie (remote $REMOTE, galaz $BRANCH)"
    ok "wypchniete na $REMOTE/$BRANCH — gracze dostana moda przy nastepnym starcie launchera"
}

# ── 4/5 Plugin na serwer ─────────────────────────────────────────────────────────────
deploy_plugin() {
    step "4/5 plugin na serwer"
    if [ "$SKIP_PLUGIN" -eq 1 ]; then skip "plugin (--skip-plugin)"; return 0; fi
    local src="$ROOT/target/$PLUGIN_JAR" dest="$PLUGINS/$PLUGIN_JAR"
    if [ "$DRY" -eq 1 ]; then
        info "--dry-run: skopiowalbym target/$PLUGIN_JAR -> $dest (z kopia zapasowa, ${KEEP_BACKUPS} najnowsze)"
        return 0
    fi
    [ -f "$src" ] || fail "brak $src"

    if [ -f "$dest" ]; then
        local stamp
        stamp="$(date +%Y%m%d%H%M%S)"
        cp -p "$dest" "$dest.bak.$stamp" || fail "nie udalo sie zrobic kopii zapasowej $dest (czy serwer trzyma plik otwarty?)"
        ok "kopia starego: $PLUGIN_JAR.bak.$stamp"
        local old
        while IFS= read -r old; do
            [ -n "$old" ] || continue
            rm -f "$old" && echo "    usuniety stary backup: $(basename "$old")"
        done < <( ls -1t "$dest".bak.* 2>/dev/null | tail -n +$((KEEP_BACKUPS + 1)) )
    fi

    cp -f "$src" "$dest" || fail "nie udalo sie podmienic $dest — zatrzymaj serwer (trzyma plik otwarty) i uruchom ponownie"
    local a b
    a="$(sha_of "$src")"; b="$(sha_of "$dest")"
    [ "$a" = "$b" ] || fail "skopiowany plik ma inny sha256 niz zrodlo ($b != $a)"
    ok "podmieniony $dest (sha256 $(printf '%s' "$b" | cut -c1-12)…, zgodny z target/)"
    info "plugin zacznie dzialac po restarcie serwera"
}

# ── 5/5 Paczka zasobow ───────────────────────────────────────────────────────────────
publish_pack() {
    step "5/5 paczka zasobow (wszyscy gracze)"
    if [ "$SKIP_PACK" -eq 1 ]; then skip "paczka (--skip-pack)"; return 0; fi
    local args=( --server "$SERVER" )
    [ "$DRY" -eq 1 ] && args+=( --dry-run )
    bash "$PACKPUB" "${args[@]}"
    local rc=$?
    [ "$rc" -eq 0 ] || fail "publikacja paczki nie powiodla sie (kod $rc)"
}

# ── Przebieg ─────────────────────────────────────────────────────────────────────────
if [ "$SKIP_MOD" -eq 1 ] && [ "$SKIP_PLUGIN" -eq 1 ] && [ "$SKIP_PACK" -eq 1 ]; then
    echo "Wszystkie etapy pominiete (--skip-mod --skip-plugin --skip-pack) — nie ma nic do zrobienia."
    exit 0
fi

echo "Publikacja:  $ROOT"
preflight
build_mod
build_plugin
publish_mod
deploy_plugin
publish_pack

step "Gotowe"
if [ "$DRY" -eq 1 ]; then
    info "--dry-run: nic nie zostalo zbudowane, wyslane ani zapisane"
    if [ "$MISSING" -eq 1 ]; then
        info "PLAN NIEKOMPLETNY: brakuje warunkow z listy '!!' powyzej (na powaznie skrypt by tam przerwal)"
    fi
fi
stage_line "mod (gracze):"  "$([ "$SKIP_MOD" -eq 1 ] && echo 'pominiete (--skip-mod)' || { [ "$NOPUSH" -eq 1 ] && echo 'przygotowany, bez pusha (--no-push)' || { [ "$DRY" -eq 1 ] && echo 'plan' || echo "wypchniete: $REMOTE/$BRANCH"; }; })"
stage_line "plugin (serwer):" "$([ "$SKIP_PLUGIN" -eq 1 ] && echo 'pominiete (--skip-plugin)' || { [ "$DRY" -eq 1 ] && echo 'plan' || echo "target/$PLUGIN_JAR -> $PLUGINS"; })"
stage_line "paczka (gracze):" "$([ "$SKIP_PACK" -eq 1 ] && echo 'pominiete (--skip-pack)' || echo 'tools/publish-pack.sh (zip + upload + server.properties)')"
echo
echo "  PAMIETAJ: serwer czyta server.properties i wtyczki przy STARCIE —"
echo "            restart jest potrzebny, zeby gracze i serwer zobaczyli nowa wersje."
# W podgladzie brakujace warunki sa tylko ostrzezeniem, ale plan jest wtedy niekompletny —
# kod 1 mowi o tym skryptom (i checkom), ktore byc moze wolaja to narzedzie.
if [ "$DRY" -eq 1 ] && [ "$MISSING" -eq 1 ]; then exit 1; fi
exit 0
