# Ustawienia trybu Raft

## Jak to działa

`/ustawienia` (alias `/settings`) zachowuje się zależnie od trybu:

- **Świat Raft** → otwiera **własny panel Rafta** (`pl.vd.raft.settings.UstawieniaGUI`) —
  GUI 4×9 (36 slotów), totalnie inny niż panel VD (54 sloty),
- **poza Raftem** → klasyczny panel VD (`ShopGUI.openSettings`).

Gałąź siedzi w `SettingsCommand` (sprawdza `raft-world-name` z configu); listener
klików rejestruje `RaftPlugin.onEnable`. Tytuł GUI: `§8Ustawienia`.

## Układ GUI (4×9)

Tło = szare szyby (jak w VD), kliknięcia i przeciągania anulowane (nic nie
wypadnie, nic nie da się włożyć). Sloty podane **w grze** (w kodzie −1):

| Slot w grze | Ikona | Opcja | Klik |
|---|---|---|---|
| 11 | note block | **Dźwięki** | otwiera PODMENU dźwięków (GUI 6×9, patrz niżej) |
| 13 | dzwonek | **Powiadomienia** | przełącznik włączony/wyłączony (stan w podpowiedzi) |
| 15 | płyta `MUSIC_DISC_CAT` | **Muzyka** | otwiera **TO SAMO GUI co `/muzyka`** |
| 17 | książka | **Język** | polski ↔ angielski, ✓ przy aktualnym |
| 32 | nether star | **Wyjdź** | zamyka GUI |

## Podmenu Dźwięki (GUI 6×9, `DzwiekiGUI`, tytuł „§8Dźwięki”)

Otwierane kliknięciem „Dźwięki” w panelu; tło z szarych szyb, kliki anulowane.
Globalny przełącznik + 7 kategorii + Wyjdź:

| Slot w grze | Ikona | Opcja | Klucz (raft-players.yml) |
|---|---|---|---|
| 5 | note block | **Wszystkie dźwięki** (master) | `sounds-enabled` |
| 20 | wędka | Hak | `sounds-hook` |
| 21 | sznur | Sieć zbierająca | `sounds-net` |
| 22 | beczka | Śmieci i beczki | `sounds-debris` |
| 23 | pień dębu | Drzewa | `sounds-trees` |
| 24 | piorunochron | Urządzenia | `sounds-devices` |
| 25 | żelazna siekiera | Budowanie | `sounds-building` |
| 26 | lejek | Zebrane przedmioty | `sounds-pickup` |
| 50 | nether star | **Wyjdź** (wraca do gry) | — |

Klik = przełącznik on/off (stan w podpowiedzi, odświeżanie na żywo).
Wspólny magazyn ustawień z panelem: przy aktywnej sesji Raft zapis do
`raft-players.yml`, bez sesji — pamięć (nigdy nie tworzy sesji).
**Na razie przechowalnia stanu** — realne wyciszanie `playSound` w managerach
będzie podłączane po kolei.

Zmiany dzieją się na żywo w GUI (podmiana ikony/podpowiedzi), bez czatu i bez
logu (zasada ciszy).

## Zapis ustawień

- **Dźwięki / Powiadomienia** → `raft-players.yml` (`players.<uuid>.settings`):
  klucze `sounds-enabled` i `notifications-enabled` (`on`/`off`, domyślnie `on`).
  Zapis natychmiastowy przez `PlayerSession.setSetting`, ale **tylko gdy gracz ma
  aktywną sesję Raft** — bez sesji stan trzyma mapa w pamięci (tworzenie sesji
  odpalałoby powitanie na czacie i podmianę ekwipunku; wzorzec z
  `CraftingCommand.setLastCategory`).
  **Uwaga:** na razie to przechowalnia stanu — realne wyciszanie dźwięków
  pluginu (guardy przy dziesiątkach `playSound`: hak, sieć, śmieci, skrzynie…)
  będzie podłączane po kolei w kolejnych krokach.
- **Muzyka** → panel nie zmienia nic sam; otwiera GUI `/muzyka`
  (`MuzykaCommand.openGui`, upublicznione) — 6×9 z suwakiem głośności
  (LPM/PPM ±1, Shift ±10), lejkiem częstotliwości (LPM w górę / PPM w dół) i
  „Wyjdź”; pozostałe ustawienia per gracz (`music-enabled/pitch`) zmienia się
  podkomendami (szczegóły: `docs/MUZYKA.md`).
- **Język** → `players.yml` **VD** (`PlayerDataManager`), klucz `language`,
  wartości `polski`/`angielski` — **te same co panel VD** (`ShopGUI.LANG_KEY`).
  Dzięki temu `Lang.isEnglish()` widzi zmianę od razu i **w obu trybach**:
  jedno kliknięcie zmienia język globalnie. GUI odświeża się na żywo.

## Pliki

- `src/main/java/pl/vd/raft/settings/UstawieniaGUI.java` — panel + holder + listener,
- `src/main/java/pl/vd/raft/settings/DzwiekiGUI.java` — podmenu dźwięków (6×9),
- `src/main/java/pl/vd/commands/SettingsCommand.java` — gałąź świata Raft,
- `src/main/java/pl/vd/raft/RaftPlugin.java` — pole `ustawieniaGui`, rejestracja, getter,
- `src/main/java/pl/vd/raft/music/MuzykaCommand.java` — `openGui` publiczne.
