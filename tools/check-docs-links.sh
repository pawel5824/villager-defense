#!/usr/bin/env bash
# Kontrola linków do dokumentacji: każdy wpis docs/*.md w podanych plikach
# musi wskazywać istniejący plik. Uruchamiaj z katalogu projektu:
#
#   tools/check-docs-links.sh                 # domyślnie AGENTS.md i README.md
#   tools/check-docs-links.sh plik.md [...]   # własna lista plików
#
# Kod wyjścia: 0 = wszystkie linki istnieją, 1 = czegoś brakuje.

set -u

files=("$@")
if [ ${#files[@]} -eq 0 ]; then
  files=(AGENTS.md README.md)
fi

missing=0
checked=0

for src in "${files[@]}"; do
  if [ ! -f "$src" ]; then
    echo "BRAK PLIKU ZRODLOWEGO: $src"
    missing=$((missing + 1))
    continue
  fi

  # Unikalne odwołania docs/....md w jednym pliku (kolejność alfabetyczna)
  while IFS= read -r link; do
    [ -z "$link" ] && continue
    checked=$((checked + 1))
    if [ -f "$link" ]; then
      echo "OK    $src -> $link"
    else
      echo "BRAK  $src -> $link"
      missing=$((missing + 1))
    fi
  done < <(grep -ohE 'docs/[A-Za-z0-9_./-]+\.md' "$src" | sort -u)
done

echo
if [ "$missing" -gt 0 ]; then
  echo "Wynik: $missing brakujacych linkow (sprawdzono $checked)."
  exit 1
fi

echo "Wynik: OK, wszystkie linki istnieja (sprawdzone $checked)."
