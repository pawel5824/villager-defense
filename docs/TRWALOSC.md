# Audyt trwałości (2026-10-06, build `v38`) — co nie przeżywało restartu

Przeniesione z `AGENTS.md` §12.1. Zasada i tabela plików są w `AGENTS.md` §12.

Przejście po wszystkich stanowych funkcjach w grze i naprawa znalezionych luk:

| Co nie przeżywało restartu | Dlaczego | Naprawa |
|---|---|---|
| obrót urządzeń (odbiornik, wiatrak) | `DevicePersistence` zapisywał `yaw: 0f` i odtwarzał z `0f`, więc każde urządzenie wracało obrócone na South | `rotation` (0-3) w `devices.yml` + `yaw = rotation * 90`; stare zapisy bez `rotation` czytają swój `yaw` (v2 pliku) |
| jawnie wybrany kit | `KitManager.chosenKit` to mapa w pamięci (żadnego zapisu) — po restarcie gracz dostawał losowy kit | zapis do `players.yml` (`settings.chosen-kit`) + `restoreChoice` przy wejściu gracza |

## Sprawdzone i celowo NIE zmieniane (z powodem)

- **kluczowe pola `raft-players.yml` mają dwóch właścicieli** — `raft/core/PlayerData`
  i `raft/player/PlayerSession` piszą pod tym samym `players.<uuid>` te same klucze (`coins`,
  `level`, `experience`, `days-survived`). Dziś nikt tych pól nie zmienia ani nie czyta (HUD bierze
  poziom z wanilii), więc nic nie ginie — ale każdy nowy kod, który je zmieni, zacznie się
  nadpisywać nawzajem. Do posprzątania: zostawić jednego właściciela rekordu.
- **`StoryManager.playerProgress`** (postęp fabuły/questów) żyje tylko w pamięci, ale cała fabuła
  jest jeszcze niepodłączona do gry (`checkQuestProgress` ma `TODO`, brak wywołań z gameplayu) —
  najpierw dokończyć fabułę, potem dopisać zapis.
- **narzędzia administracyjne są tymczasowe, jak w WorldEdit**: ustawienia pędzla
  (`BrushManager.settings`), stos cofania i schowek kopiuj-wklej. To celowe — po restarcie
  narzędzie startuje od domyślnych ustawień.
- **przedmioty z meczu** (miny, przywołańce, zrzuty zaopatrzenia, umiejętności, punkty sesji)
  są per-rundę — mecze nie przeżywają restartu i tak ma zostać.
