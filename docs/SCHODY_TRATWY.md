# Schody tratwy na modelu 3D + niewidzialnych shulkerach

Stan: **projekt przyjęty 2026-09-23, czekam na model od właściciela**. Ustalone: schody to
**komórka tratwy `STAIRS`** (typ już istnieje w kodzie), a **wysokości stopni wynikają z modelu**
(źródło prawdy = model, nie ręcznie wpisane liczby). Narzędzie do czytania modelu jest gotowe
i przetestowane: `tools/model-shelves.sh`.

## Cel

Schody, które nie są blokami schodowymi, a **modelem 3D** — i po których da się chodzić.
Tratwa już ma ten wzorzec kolizji (niewidzialne shulkery), więc nowa jest tylko warstwa wyglądu
i **pomniejszenie** pudełek.

## Jak działa kolizja (to już jest w kodzie tratwy)

Każda komórka tratwy ma parę: **ArmorStand `marker(true)`** (niewidzialny nośnik bez kolizji)
+ **shulker jako pasażer**. Pasażera nie da się odepchnąć, a teleportuje się tylko nośnik
(`RaftEntity.spawnCarrier` / `spawnShulker`). Trzy fakty, na których to stoi:

- **Zamknięty shulker ma pudełko `skala × skala × skala` liczone OD POZYCJI W GÓRĘ** (pozycja
  encji = spód pudełka), wyśrodkowane w poziomie na encji. Dlatego para jest kotwiczona
  `COLLIDER_Y_OFFSET = −0.5` pod środkiem deski — wtedy pudełko pokrywa się z deską.
- **Pudełko skaluje się atrybutem**: `Shulker.makeBoundingBox` mnoży wymiary przez `getScale()`
  (sprawdzone w bytecode 26.3), a Paper 26.3 ma `org.bukkit.attribute.Attribute.SCALE`.
  Skala jest **jedna dla wszystkich osi**, więc schodek to kostka (nie „płaski naleśnik").
  `0.5` daje pudełko `0.5×0.5×0.5` — czyli dwa takie shulkery mieszczą się w jednej komórce,
  jeden po lewej, drugi po prawej.
- **O tym, czy gracz musi skoczyć, decyduje różnica wysokości między górnymi powierzchniami
  stopni**, nie wysokość samego pudełka: gracz sam wchodzi na **0,6 bloka**, a skok sięga
  **~1,25**. Stopień, który ma wymusić skok, musi być wyżej o **więcej niż 0,6** i **nie wyżej
  niż ~1,0**. Stopnie 0,5 od siebie = gracz wejdzie na górę bez ani jednego skoku.

## Gdzie stoi wygląd, a gdzie kolizja

| Warstwa | Kto rysuje/trzyma | Dlaczego tak |
|---|---|---|
| model 3D (stopnie, barierka) | **paczka zasobów** — komórka tratwy nie ma BlockEntity, więc renderer moda (BER) tam nie sięgnie | tak samo działa duża beczka (CMD 4003): `ItemDisplay` + model z paczki, bez zmian w modzie |
| kolizja (2 pudełka na komórkę) | **plugin** — shulkery jak przy pokładzie, plus skala i przesunięcia ułamkowe | pudełka muszą istnieć tam, gdzie model ma półki; plugin i tak trzyma tratwę |

Model dla `ItemDisplay` przechodzi przez **waniliowy loader (limit −16…32 px na element)**, więc
schody muszą się zmieścić w ~2 blokach na oś. Większe = trzeba by rysować przez mod (nowy rodzaj
danych w pakiecie tratwy i renderer) — do decyzji dopiero, gdy okaże się to potrzebne.

## Jak dopasować kolizje do modelu (workflow)

1. Właściciel wgrywa model (`.bbmodel` **albo** eksport `.json`) — najlepiej, żeby **każdy stopień
   był osobną grupą z nazwą** (`stopien1_lewa`, `stopien1_prawa`, …), i podaje, które półki mają
   dostać kolizję (reszta to dekoracja).
2. `tools/model-shelves.sh <model> [skala]` wypisuje dla każdej półki: wysokość w blokach, rozmiar
   w poziomie, czy mieści się w komórce i **gotową pozycję shulkera** (`środek półki`,
   `góra półki − skala`), plus rytm: co gracz przejdzie bez skoku, a co wymaga skoku.
   Przykład na modelu testowym (pokład + 2 stopnie po 0,75 bloka, w połówkach lewa/prawa):

   ```
   gora bloki  part / element                 rozmiar       shulker x y z
       0,125   schody/poklad / poklad         1,000 x 1,000 (0,500, -0,375, 0,500)
       0,875   schody/stopnie / stopien1_lewa 0,438 x 0,500 (0,281,  0,375, 0,313)
       0,875   schody/stopnie / stopien1_prawa 0,438 x 0,500 (0,719, 0,375, 0,313)
       1,625   schody/stopnie / stopien2_lewa 0,438 x 0,375 (0,281,  1,125, 0,750)
   rytm: +0,750 → skok ok;  +0,000 → półka dzielona …
   ```
3. Te dwie liczby na komórkę idą do tabeli komórek `STAIRS` (pozycja + skala + najwyższa półka).
   Nic nie trzeba trzymać w zapisie tratwy — tabela jest stała, a pudełka odtwarzają się z niej
   po restarcie tak samo jak pokład.
4. Kontrola bez gry: rozszerzyć `tools/model-shelves.sh` w test (albo dołożyć osobny skrypt),
   który **porównuje tabelę z modelem z paczki** i wywala się, gdy górna ściana pudełka nie leży
   w płaszczyźnie półki albo gdy stopnie są ≤ 0,6 od siebie (wtedy „schody" nie wymuszają skoku).
   To ten sam wzorzec, co `tools/check-item-model.sh` dla przedmiotów.

## Co zostaje do zrobienia

- [ ] model schodów (właściciel) + lista półek z kolizją,
- [ ] tabela kolizji dla `STAIRS`: 2 pudełka na komórkę, skala `Attribute.SCALE`, przesunięcia
      ułamkowe (dziś kolizje tratwy są liczone tylko na pełnych offsetach blokowych),
- [ ] wygląd komórki `STAIRS`: `BlockDisplay` z `OAK_STAIRS` → `ItemDisplay` z modelem z paczki
      (CMD do nadania w `ItemManager` + wpis w item definition),
- [ ] podgląd: komórka schodów w podglądzie stawiania (`RaftBuildPreview`) i w grze,
- [ ] kontrola zgodności model ↔ kolizje (punkt 4 wyżej).
