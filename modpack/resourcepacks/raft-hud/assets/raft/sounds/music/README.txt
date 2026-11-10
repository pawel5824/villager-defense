Muzyka trybu Raft — tu wrzucaj pliki .ogg (OGG Vorbis).

Nazwa pliku = nazwa z sounds.json (o jeden poziom wyżej), np.:
  music.utwor_1  ->  utwor_1.ogg
  music.utwor_2  ->  utwor_2.ogg

Zasady:
- format OGG Vorbis (mono lub stereo), nazwy bez polskich znaków i spacji,
- dlugie utwory: "stream": true w sounds.json jest juz ustawione,
- po dodaniu plikow: publish paczki (tools/publish-pack.sh) + RESTART serwera,
- identyfikatory utworow wpisuj w config.yml serwera -> sekcja raft-music.tracks
  (np. "raft:music.utwor_1").

W paczce sa TERAZ placeholdery: utwor_1.ogg i utwor_2.ogg — krotki (50 s),
delikatny ambient (morze + cieply pad), zeby muzyke dalo sie przetestowac.
Podmien je wlasnymi plikami o tych samych nazwach (nadpisanie) albo dodaj nowe:
plik + wpis w assets/raft/sounds.json + identyfikator w raft-music.tracks.

Uwaga na przyszlosc: gdyby w tym folderze znowu nie bylo ZADNYCH plikow .ogg,
trzymaj w config.yml serwera raft-music.enabled: false — inaczej klient loguje
"Unable to play empty soundEvent" (i tak nic nie uslyszy).
