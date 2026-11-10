# Komunikaty dla gracza — co usunięto i czego nie wolno przywracać

Przeniesione z `AGENTS.md` §1 (stan 2026-09-18). Zasada jest w `AGENTS.md` §1; tutaj jest pełna
lista, żeby nikt nie wracał do usuniętych rzeczy „przy okazji”.

## Usunięte komunikaty (nie przywracać)

Wyjście z trybu, schowanie ekwipunku przy teleporcie do lobby, sam tekst o teleporcie do lobby,
błąd braku lobby, licznik dni przy wejściu do trybu, PPM kompasem, PPM wędką/haczykiem,
napełnianie wiadra, całe budowanie tratwy (powody porażki też), pusta sieć zbierająca, klik
w slot baterii (urządzenie i wiatrak), użycie urządzenia, raport modułu odbiorczego,
rozładowanie baterii, przyciski GUI urządzenia (kompas/ustawienia/statystyki), **PPM latarenką
(wszystkie trzy)**, błąd otwarcia GUI urządzenia, zbliżenie do wyspy, cała fabuła/dialogi,
statystyki gracza, strefa ciemnej wody.

## Co zostało — i czego też nie wolno dodawać

Dwa powitania, zostawione świadomie na życzenie właściciela:

- „🌊 Witaj z powrotem!” przy wejściu na serwer,
- „Witaj z powrotem na oceanie!” przy wejściu w tryb Raft.

Poza tym zostają tylko odpowiedzi komend administracyjnych (`/raft`, `/urzadzenie`, `/latarenka`,
crafting).

Nowych komunikatów (czat, action bar, title/subtitle, boss bar, scoreboard, toasty,
dźwięki-informacje) nie dodajemy nigdzie — informacja zwrotna to paski HUD moda `raft-hud`.

## Zamierzone skutki uboczne

- budowanie na tratwie nie pokazuje powodu porażki (ani sukcesu),
- moduł odbiorczy po PPM nie pokazuje nic — raport odległości do wysp usunięty; jeśli ma wrócić,
  to przez HUD moda/pakiet, nie przez czat,
- teleporter, kompas w GUI urządzenia i zużycie baterii nadal działają — tylko po cichu.
