# Handoff: Cookbook Android — Design „Küchentisch 1.0"

## Überblick
Redesign der nativen Android-App (`tosh-hamburg/cookbookApp`, Branch `main`, Kotlin + XML-Layouts
+ Material 3) im Design-System **Küchentisch**, das Cookbook Web und Android teilen. Ziel ist eine
App für Privatnutzer: warmes Papier, ein Terrakotta-Akzent, Rezepttitel in Serif, alle Zahlen in
Monospace. Material-Muster bleiben erhalten — MaterialToolbar, FAB, Chip-Group, BottomSheet,
CollapsingToolbar —, sie werden nur neu eingekleidet.

Fünf Screens: **Rezepte**, **Rezeptdetail**, **Kochmodus** (neu), **Wochenplaner**,
**Rezept hinzufügen**.

## Zu den Design-Dateien
`Cookbook App Android.dc.html` und `Kuechentisch Design System.dc.html` sind **Design-Referenzen in
HTML** — Prototypen, die Aussehen und Verhalten zeigen, **kein Produktionscode zum Portieren**.
Aufgabe ist, das Design in den bestehenden XML-Layouts und Activities umzusetzen. Im Browser
öffnen; `support.js`, `image-slot.js` und `android-frame.jsx` liegen daneben, damit die Dateien
offline laufen (Prototyp-Infrastruktur, nicht übernehmen). Die Screens stecken im Prototyp in
einem 412×892-Gerätrahmen — das ist die Designbreite, entspricht 412 dp.

Alle Maße im Prototyp sind px und werden **1:1 als dp** übernommen, Schriftgrößen als **sp**.

## Fidelity
**High-fidelity.** Farben, Typografie, Abstände, Radien und Zustände sind final und unten exakt
dokumentiert. Bitte genau nachbauen — über Themes/Styles in `res/values`, nicht mit Einzelwerten
in den Layouts.

---

## Design Tokens

### `res/values/colors.xml` — vollständig ersetzen
Die Palette ist eine **Schärfung der vorhandenen** (mehr Kontrast, wärmeres Papier), keine neue
Richtung. Links neu, rechts der bisherige Wert.

```xml
<!-- Akzent -->
<color name="primary">#C8522F</color>          <!-- war #D4654A -->
<color name="primary_variant">#A94123</color>  <!-- war #B84C33 -->
<color name="primary_light">#E08A6C</color>    <!-- war #E8866F -->

<!-- Kräutergrün / Salbei -->
<color name="secondary">#4E7A56</color>        <!-- war #7D9B76 -->
<color name="secondary_variant">#3C6144</color>
<color name="secondary_light">#D6DCA8</color>  <!-- Salbei, Flächen/Notiz -->

<!-- Gold -->
<color name="accent">#D9AE55</color>           <!-- war #E6A756 -->
<color name="accent_dark">#B8913F</color>

<!-- Flächen -->
<color name="background">#F7F1E6</color>       <!-- war #FDF8F3 (wärmer) -->
<color name="surface">#FFFFFF</color>
<color name="surface_variant">#F2EADD</color>  <!-- war #F5EDE6 -->
<color name="card_background">#FFFFFF</color>
<color name="card_stroke">#F1EAE0</color>
<color name="divider">#DCD4C6</color>          <!-- war #E0D8D0 -->

<!-- Schrift -->
<color name="text_primary">#3A2A22</color>     <!-- war #2D2926 (wärmer) -->
<color name="text_secondary">#6B564C</color>   <!-- war #5C5652 -->
<color name="text_hint">#8E7666</color>        <!-- war #9A928B -->
<color name="text_on_primary">#FFFFFF</color>
<color name="text_on_accent">#3A2A18</color>   <!-- neu: Tinte auf Gold -->

<!-- Kochmodus (neu) -->
<color name="cook_bg">#332419</color>
<color name="cook_fg">#EADFCD</color>
<color name="cook_fg_dim">#B9A88C</color>

<!-- Chips -->
<color name="chip_background">#00000000</color>       <!-- transparent, 1dp Rahmen -->
<color name="chip_stroke">#DCD4C6</color>
<color name="chip_text">#6B564C</color>
<color name="chip_background_selected">#3A2A22</color><!-- aktiv = Tinte, NICHT Terrakotta -->
<color name="chip_text_selected">#FFFFFF</color>

<!-- Slots -->
<color name="slot_selected_bg">#FAF0DD</color>

<!-- Status -->
<color name="favorite_active">#C8522F</color>  <!-- war #E53935 -->
<color name="favorite_inactive">#B5A492</color>
<color name="success">#4E7A56</color>
<color name="warning">#B8913F</color>
<color name="error">#B4402C</color>
```

**Sammlungsfarben** (Marker-Punkt, Badge-Text, Icon-Tile mit 13 % Deckung — *niemals* als Fläche):
```xml
<color name="coll_auflaeufe">#C2793A</color>
<color name="coll_fleisch">#C55340</color>
<color name="coll_nudeln">#B8862F</color>
<color name="coll_leicht">#468F5E</color>
<color name="coll_suppen">#B96A43</color>
<color name="coll_fisch">#4C8DA6</color>
<color name="coll_wok">#9A5D9C</color>
```
Weitere Sammlungen: Farbton aus dem Namen hashen, Helligkeit/Sättigung dieser Reihe halten
(oklch L 0.60–0.66, C 0.10–0.17).

### Typografie
Drei Schriften als `res/font` einbetten (beide OFL, unproblematisch):
- **Instrument Serif Regular** → Rezepttitel, Screen-Titel, Sektionsüberschriften
- **Figtree** 400/500/600/700 → alle UI-Texte, Buttons, Fließtext
- **JetBrains Mono** 500/700 → jede Zahl (Zeiten, Mengen, Portionen, kcal, Timer, Datum)

Ohne Einbettung: Roboto Serif / Roboto Flex als Ersatz, Serif-Titel dann eine Stufe kleiner.

| Rolle | Größe (sp) | Schrift |
| --- | --- | --- |
| Screen-Titel (Toolbar) | 22–24 | Instrument Serif 400 |
| Rezepttitel (Detail, über Foto) | 28 | Instrument Serif 400 |
| Sektionstitel („Zutaten") | 24 | Instrument Serif 400 |
| Kartentitel (Liste) | 19 | Instrument Serif 400 |
| Slot-Titel (Planer) | 16 | Instrument Serif 400 |
| Schritttext Kochmodus | 24 | Figtree 500 |
| Fließtext | 15 | Figtree 400 |
| Button / UI | 14–15 | Figtree 600 |
| Label (Versalien, `letterSpacing` 0.12) | 9.5–11 | Figtree 700 |
| Zahlen | 12–34 | JetBrains Mono 500/700 |

`letterSpacing`: Serif-Titel −0.015em (Android: `android:letterSpacing="-0.015"`), Versal-Labels
+0.1 bis +0.14.

### `res/values/dimens.xml` — Ergänzungen
```xml
<dimen name="card_corner_radius">20dp</dimen>   <!-- war 16dp -->
<dimen name="slot_corner_radius">14dp</dimen>
<dimen name="sheet_corner_radius">24dp</dimen>
<dimen name="pill_height">48dp</dimen>          <!-- Buttons: Pill, volle Höhe -->
<dimen name="chip_height">36dp</dimen>
<dimen name="touch_target">48dp</dimen>
<dimen name="recipe_row_thumb">104dp</dimen>
<dimen name="detail_header_height">268dp</dimen>
```
Radien: 999dp (Buttons, Chips, runde Icon-Buttons) · 14dp (Slots, Thumbnails) · 16dp (Tiles) ·
20dp (Karten) · 24dp (BottomSheet oben).
Abstände (4-dp-Raster): 4 · 8 · 12 · 16 (Bildschirmrand) · 24 · 28.
Elevation: Karten 2dp, gedrückt 6dp, FAB 6dp, BottomSheet 16dp. Keine Schatten über 40 dp Radius.

### `res/values/themes.xml` — Änderungen
- **Toolbar trägt Papier**, nicht Terrakotta: `Theme.Cookbook.Toolbar` → `android:background`
  `@color/background`, `titleTextColor` `@color/text_primary`, Icons `@color/text_secondary`;
  `ThemeOverlay.Cookbook.Toolbar` entsprechend auf Tinte umstellen.
- `android:statusBarColor` → `@color/background`, `android:windowLightStatusBar` → `true`
  (war `false` bei terrakottafarbener Bar).
- `Theme.Cookbook.Button`: `cornerRadius` 24dp bei 48dp Höhe (Pill), `backgroundTint`
  `@color/primary`.
- `Theme.Cookbook.Button.Outlined`: `strokeColor` `@color/text_primary`, `strokeWidth` 1.5dp,
  `android:textColor` `@color/text_primary` (Sekundäraktionen sind Tinte, nicht Terrakotta).
- `Theme.Cookbook.Chip`: `chipBackgroundColor` `@color/chip_background`, `chipStrokeColor`
  `@color/chip_stroke`, `chipStrokeWidth` 1dp, `chipCornerRadius` 18dp, Selektor-Farben für
  `chip_background_selected` / `chip_text_selected`.
- `Theme.Cookbook.Card`: `cardCornerRadius` 20dp, `cardElevation` 2dp, `strokeWidth` 0dp.
- Neu: `Theme.Cookbook.CookMode` — Vollbild, `android:colorBackground` `@color/cook_bg`,
  `windowLightStatusBar` `false`.

---

## Screen 1 — Rezepte (`MainActivity`)
**Zweck:** stöbern, filtern, sich für heute entscheiden.

**Toolbar (56dp, `@color/background`, unten 1dp `@color/divider`):**
- Wortmarke „Kochbuch" (Instrument Serif 24sp, `text_primary`) + 6dp Punkt in `primary`
  (4dp Abstand) — kein `app:title`, eigenes TextView im Toolbar-Slot.
- Rechts: Such-Icon (22dp, `text_secondary`, 48dp Touch-Target) und Avatar-Kreis 34dp in
  `secondary` mit weißen Initialen (Figtree 600 13sp).

**Filterleiste:** die beiden Outlined-Buttons (`btnFilterCategory`, `btnFilterCollections`) und
das Filter-Icon **entfallen**. Stattdessen eine horizontal scrollende `ChipGroup`
(`app:singleLine="true"` in einer `HorizontalScrollView`), `paddingHorizontal` 16dp,
`paddingBottom` 12dp, Chips 36dp, Abstand 8dp:
- **Mehrfachauswahl** (`app:singleSelection="false"`, OR-Logik über Sammlungen/Kategorien).
- Erster Chip „Alle" leert die Auswahl und ist aktiv, solange nichts gewählt ist.
- Aktiv = Tinte-Fläche, weißer Text; inaktiv = transparent mit 1dp Rahmen.
- Sammlungen weiterhin über `CollectionsBottomSheet` verwaltbar (Overflow-Menü).

**„Rezept der Woche" (neu, über der Liste):**
- Label „REZEPT DER WOCHE" (Figtree 700 10.5sp, `letterSpacing` 0.14, `primary`).
- Karte (radius 20dp, elevation 2dp): Foto 172dp `centerCrop`, darüber unten ein Gradient
  (`transparent → #D12D1F18`, Höhe 108dp) mit dem Titel in Instrument Serif 24sp weiß.
- Darunter Satz „Schon **7×** gekocht — dein Familienfavorit." (Figtree 15sp `text_secondary`,
  Zahl in Mono), dann Aktionsreihe: Primärbutton „Jetzt kochen" (48dp, Pill, `primary`,
  `ic_cook_time`/Löffel-Icon) + 48dp runder Outlined-Button mit `ic_calendar`.
- **Auswahl automatisch:** Rezept mit der höchsten Kochhäufigkeit; Tie-Break zuletzt gekocht,
  dann neuestes. Kein manuelles Markieren. Fehlt die Historie (0×), zeigt der Aufmacher das
  zuletzt angesehene Rezept ohne den Häufigkeits-Satz.

**Sektion „Meine Sammlung":** Instrument Serif 24sp + „**{n}** Rezepte" (Figtree 13sp,
`text_hint`, Zahl Mono). `{n}` ist die gefilterte Trefferzahl.

**Listenkarte (`item_recipe.xml` — von Grid-Karte auf Querformat-Zeile umbauen):**
- `MaterialCardView`, radius 20dp, elevation 2dp, `padding` 12dp, Abstand 14dp zur nächsten,
  `RecyclerView` mit `LinearLayoutManager` (statt Grid).
- Links Thumbnail 104×104dp, radius 14dp, `centerCrop`.
- Rechts:
  - Sammlungs-Marker: 7dp Quadrat (radius 2dp) + Name in Versalien (Figtree 700 10sp,
    `letterSpacing` 0.1) in der Sammlungsfarbe.
  - Titel Instrument Serif 19sp, max. 2 Zeilen, `ellipsize="end"`.
  - Sachzeile 13sp `text_hint` (aus Kategorien/Notiz, z. B. „Hokkaido · Kokosmilch").
  - Fußzeile: Uhr-Icon 14dp `primary` + „**{totalTime}** Min"; Schüssel-Icon 14dp `secondary`
    + „**{servings}**"; rechts Herz 18dp (aktiv gefüllt `favorite_active`, sonst Kontur
      `favorite_inactive`), Touch-Target 48dp.
- **Keine Null-Werte:** ist `totalTime` 0, entfällt die Zeitangabe; ist `caloriesPerUnit` 0,
  erscheint keine kcal-Angabe (auf der Karte ohnehin nur optional).
- Eintritts-Animation: `rise` (12dp nach oben, 450 ms), 55 ms Versatz pro Zeile — nur beim
  ersten Laden, nicht beim Scrollen.

**FAB:** von rund auf **Extended FAB** („Rezept" + `ic_add`), 56dp hoch, radius 18dp, `primary`,
`margin` 16dp, `bottom|end`. Öffnet Screen 5.

**Leerzustand:** das 72sp-Emoji entfällt (Emoji sind nicht Teil des Systems). Stattdessen
Instrument Serif 24sp „Bereit für das erste Rezept" + Satz 15sp `text_secondary` + Primärbutton
„Rezept importieren".

---

## Screen 2 — Rezeptdetail (`RecipeDetailActivity`)
**Header (268dp, CollapsingToolbar):**
- Foto `centerCrop`, Bild-Carousel (`ImagePagerAdapter`) bleibt; Indikator-Punkte 6dp,
  aktiv weiß, inaktiv weiß 40 %.
- Drei runde 44dp Buttons auf `#EBFFFFFF` (weiß 92 %): zurück, Herz (gefüllt `primary`),
  Overflow.
- Unten Gradient (`transparent → #CC2D1F18`, 108dp), darauf Sammlungs-Badge (26dp Pill, weiß
  92 %, Text in Sammlungsfarbe, Figtree 700 10.5sp) und Titel Instrument Serif 28sp weiß.
- Collapsed: Toolbar auf `background`, Titel Instrument Serif 20sp `text_primary`.

**Metrik-Karte** (weiß, radius 16dp, elevation 2dp, 4 Spalten gleich breit): Wert Mono 700 17sp,
Label Figtree 600 9.5sp Versalien `text_hint`. Werte: Gesamt, Arbeit (prep + cook), kcal/Port.,
Gekocht. **Spalten mit Wert 0 werden weggelassen**, die übrigen verteilen sich neu.

**Notizblock** (falls Notiz vorhanden): Fläche `secondary_light` 44 %, 1dp Rahmen `#99BEC88C`,
radius 16dp, Label „DEINE NOTIZ" (Figtree 700 10sp, `#5C7038`), Text 14.5sp `#3E3020`.
Tap öffnet die Bearbeitung.

**Zutaten:**
- Kopf: „Zutaten" Instrument Serif 24sp; rechts Stepper — weiße Pill, 1dp `divider`, radius
  999dp, zwei 42dp Icon-Buttons (`ic_minus`/`ic_plus`), Wert Mono 700 16sp, Breite min. 40dp.
  Grenzen 1–12.
- Hinweiszeile 13sp `text_hint`: „Portionen ändern rechnet die Mengen sofort um."
- Liste in einer weißen Karte (radius 16dp, `paddingHorizontal` 16dp): Zeile min. **48dp**,
  Trennlinie 1dp `card_stroke`; links Checkbox 20dp (radius 6dp, 1.5dp `#CFC3B2`; abgehakt
  Füllung `primary` + weißer Haken), Name 15sp, rechts Menge Mono 13.5sp. Abgehakt:
  `strikeThrough` + `#A8998A`. Ganze Zeile ist das Touch-Target.
- **Mengenrechnung:** `Menge × (servings / recipe.servings)`, auf 2 Dezimalstellen gerundet,
  **Komma** als Trennzeichen (`0,75 Bund`). `Ingredient.amount` ist ein String: führende Zahl
  parsen (auch „1/2" → 0,5), Rest als Einheit behalten; nicht parsebar → unverändert anzeigen.
  kcal/Portion bleibt konstant.
- Darunter Outlined-Button „Auf den Einkaufszettel" (48dp, Pill, `ic_shopping`/Warenkorb).

**Zubereitung:** „Zubereitung" Instrument Serif 24sp; Schritte als Liste,
`grid 38dp | 1fr`, je unten 1dp gestrichelt `divider`:
- Ziffer Instrument Serif 30sp in `#D3A184` (zweistellig: `01`)
- Kopfzeile: Schrittname Figtree 600 15sp + Teilzeit Mono 12sp `primary`
- Text Figtree 15sp/1.6 `text_secondary`

`instructions` ist heute ein Textblock — beim Anzeigen in Schritte splitten (Leerzeile oder
führende Nummerierung); Schrittnamen und Teilzeiten sind optional und entfallen, wenn nicht
vorhanden. Perspektivisch ein Schritt-Array im Datenmodell.

**Feste Aktionsleiste unten** (`background` 96 %, oben 1dp `divider`, `padding` 12/16dp):
Primärbutton „Kochmodus" (52dp, Pill, `primary`, Play-Icon) + zwei runde 52dp Outlined-Buttons
(Kalender → `AddToWeekPlannerBottomSheet`, Stift → `RecipeEditActivity`).

---

## Screen 3 — Kochmodus (`CookModeActivity`, neu)
**Zweck:** am Herd kochen. Vollbild, dunkel, ein Schritt pro Seite.

- Rahmen: `cook_bg` (#332419), Text `cook_fg`, `Theme.Cookbook.CookMode`.
- Kopfzeile: 44dp Schließen-Icon; Titel Instrument Serif 18sp weiß (einzeilig, `ellipsize`),
  darunter „{servings} Portionen" Mono 12sp `cook_fg_dim`; rechts Status-Pill „An"
  (32dp, weiß 8 %, Figtree 500 11.5sp) — zeigt den aktiven **Wake Lock**
  (`window.addFlags(FLAG_KEEP_SCREEN_ON)`).
- Fortschritt: ein 4dp-Balken **pro Schritt** in einer Reihe (`gap` 6dp), erledigt/aktuell
  `accent`, offen weiß 18 %; tippbar. Darunter „SCHRITT 03 VON 05" (Figtree 700 11sp,
  `letterSpacing` 0.16, `accent`).
- Mitte (vertikal zentriert, `padding` 20dp): Schrittname Instrument Serif 26sp `accent`;
  Schritttext **Figtree 500 24sp/1.42** weiß.
- Zutatenbox für den Schritt: weiß 6 %, radius 18dp, Label „FÜR DIESEN SCHRITT" (Figtree 700
  10sp Versalien `cook_fg_dim`), Zeilen Name 15sp / Menge Mono 13.5sp `accent` — mit der
  Portionszahl gerechnet. Zuordnung Schritt→Zutat: Zutatennamen im Schritttext matchen
  (case-insensitive, erstes Wort), sonst Box weglassen.
- Fuß: Timer-Block (schwarz 24 %, radius 18dp): Zeit **Mono 700 34sp** (`MM:SS`, vorbelegt mit
  der Schrittzeit), rechts „Timer starten" (44dp Pill, `accent`, Text `text_on_accent`).
  Läuft der Timer, zählt er sekündlich runter, läuft im Vordergrund weiter und meldet sich bei
  0 mit Ton + Vibration (Notification, wenn die App im Hintergrund ist).
- Navigation: 96×56dp Outlined-Button „zurück" (Pfeil, Rahmen weiß 28 %) + `flex` Primärbutton
  „Weiter" (56dp, `primary`); letzter Schritt → Label **„Fertig"**: schließt den Modus und
  erhöht den Kochzähler des Rezepts (füttert „Rezept der Woche").
- **Wischen** blättert (`ViewPager2`, horizontal), Zurück-Taste verlässt den Modus mit
  Bestätigung, wenn ein Timer läuft.

---

## Screen 4 — Wochenplaner (`WeeklyPlannerActivity`)
- Toolbar: zurück; „Wochenplan" Instrument Serif 22sp; rechts Wochennavigation — 44dp Chevrons
  + „KW 39" (Mono 600 14sp). Darunter Zeile „22.–28. September · **8** von 21 Mahlzeiten belegt"
  (Figtree 13sp `text_hint`, Zahlen Mono). Wochenwechsel als `ViewPager2` (wischbar).
- **Tageskarte** (`item_day_plan.xml`): weiß, radius 18dp, elevation 2dp, Abstand 14dp.
  - Kopf auf `surface_variant`, `padding` 12/16dp: Wochentag Figtree 600 15sp, Datum Mono 12.5sp
    `text_hint`, rechts Status: „geplant" (`secondary`), „{n} offen" (`text_hint`) oder
    „ganz offen" (`#B96A43`), Figtree 500 12sp.
  - Slots `padding` 10/12dp, Abstand 8dp.
- **Slot** (`item_meal_slot.xml`, kompakter als heute — eine Zeile statt Block):
  - min. **56dp** hoch, radius 14dp, `padding` 8/10dp.
  - **belegt:** weiß, 1.5dp `card_stroke`; links 42dp Tile (radius 11dp) in der Sammlungsfarbe
    mit 13 % Deckung + Icon 18dp in der Sammlungsfarbe (alternativ das Rezeptfoto);
    Mahlzeit-Label (Figtree 600 9.5sp Versalien `text_hint`), Titel Instrument Serif 16sp
    einzeilig; rechts Zeit Mono 12sp + Chevron.
  - **leer:** transparent, 1.5dp **gestrichelt** `divider`, Tile ohne Füllung mit Plus-Icon,
    Titel „Rezept wählen" (Figtree 500 14sp `text_hint`), rechts Plus statt Chevron.
  - **angetippt:** Fläche `slot_selected_bg`, Rahmen `primary` — danach öffnet
    `RecipeSearchBottomSheet` zur Auswahl.
  - Die Portions-Steuerung pro Slot wandert in das Slot-Detail (Tap auf einen belegten Slot
    öffnet ein BottomSheet mit Portionen ±, „Rezept öffnen", „Entfernen") — die Zeile bleibt
    dadurch ruhig und scanbar.
- **Einkaufszettel-Leiste** fest unten (`background` 96 %, oben 1dp `divider`): dunkle Karte
  (`text_primary`, radius 16dp) mit Label „EINKAUFSZETTEL" (Figtree 700 9.5sp `accent`),
  Zeile „**18** Positionen aus 8 Mahlzeiten" (Figtree 14sp `cook_fg`, Zahlen Mono) und Button
  „Öffnen" (44dp Pill, `accent`, Text `text_on_accent`). Aggregation wie bisher
  (`AggregatedIngredient`, `sent-ingredients` bleibt unverändert).

---

## Screen 5 — Rezept hinzufügen (BottomSheet nach dem FAB)
Ersetzt den direkten Sprung in `RecipeImportActivity` — Import per Link ist der häufigste Fall
und steht deshalb zuerst.

- `BottomSheetDialogFragment`, Grund `background`, radius oben 24dp, Griff 36×4dp `#D9CFBE`,
  Scrim `#6B2D1F18`.
- Titel „Rezept hinzufügen" Instrument Serif 26sp, Satz 14.5sp `text_secondary`:
  „Am schnellsten geht es mit dem Link zur Rezeptseite."
- **Link-Feld** (fokussiert): 56dp, radius 16dp, weiß, 1.5dp `primary`, Ketten-Icon 19dp
  `primary`, Eingabe in **Mono 15sp**, rechts 44dp runder Senden-Button in `primary`.
  Enthält die Zwischenablage eine URL, ist sie vorbelegt (Hinweis „Aus der Zwischenablage").
- Drei Optionszeilen (weiß, radius 16dp, min. 64dp, Abstand 10dp), je 44dp Tile (radius 13dp,
  Farbe mit 14 % Deckung) + Titel Figtree 600 15.5sp + Satz 13sp `text_hint` + Chevron:
  1. **Foto abfotografieren** — „Seite aus dem Kochbuch, Text wird erkannt" (`secondary`)
  2. **Aus der Galerie** — „Screenshot oder gespeichertes Bild" (`coll_nudeln`)
  3. **Selbst eintippen** — „Leeres Rezept anlegen" (`primary`)
- Import-Fortschritt im Sheet selbst (Balken unter dem Feld), Fehler als Zeile darunter
  („Seite konnte nicht gelesen werden — selbst eintippen?" mit Link auf Option 3).

---

## Querschnittliche Regeln
- **Sprache:** Deutsch, „du", knapp. Buttons sind Verben ohne Artikel („Jetzt kochen",
  „Einplanen", „Importieren", „Öffnen"). Keine Ausrufezeichen, **keine Emoji** (betrifft
  `strings.xml`: `empty_state_icon_*` entfernen). Neue Strings in `values/strings.xml` **und**
  `values-de/strings.xml`: „Wonach ist dir heute?", „Rezept der Woche", „Kochmodus", „Weiter",
  „Fertig", „Timer starten", „Auf den Einkaufszettel", „Rezept wählen", „ganz offen",
  „Bildschirm bleibt an", „Bereit für das erste Rezept".
- **Zahlen:** Datum `22.09.`, Dezimalkomma, Zeiten mit Einheit („55 Min"), Timer `MM:SS`,
  alle Zahlen in JetBrains Mono.
- **Leere Werte nie als „0"** — Feld ausblenden.
- **Touch-Targets** min. 48dp, auch wenn das Icon 18–22dp groß ist.
- **Bewegung:** Listen-Eintritt 450 ms mit 55 ms Versatz (nur Erstladung), Press-States
  140–160 ms, Fortschritt 200 ms. `prefers-reduced-motion`-Äquivalent: bei
  `Settings.Global.ANIMATOR_DURATION_SCALE == 0` alle Animationen überspringen.
- **Platzhalterbild:** nicht mehr ein identisches Foto für alle Rezepte — Fläche `#EDE6D9` mit
  Sammlungs-Icon in der Sammlungsfarbe (`RecipeAdapter`, `placeholder_recipe` ersetzen).

## State
| State | Typ | Auslöser |
| --- | --- | --- |
| `activeFilters` | Set<String> | Chip-Tap (Mehrfachauswahl, OR) |
| `query` | String | Such-Icon → `RecipeSearchBottomSheet` |
| `featured` | Recipe | automatisch: höchste Kochhäufigkeit (Server: `GET /recipes/featured`) |
| `cookCount` | Int pro Rezept | „Fertig" im Kochmodus → `POST /recipes/:id/cooked` |
| `favorites` | Set<recipeId> | Herz → `PUT/DELETE /recipes/:id/favorite`, optimistisch |
| `servings` | Int (1–12) | Stepper im Detail, Start `recipe.servings` |
| `checkedIngredients` | Set<Int> | Zutat abgehakt (nur Sitzungsdauer) |
| `step` | Int | Kochmodus, `ViewPager2` |
| `timerRemaining` | Long | Timer im Kochmodus |
| `selectedSlot` | day+mealType | leerer Slot im Planer |

## Dateien
- `Cookbook App Android.dc.html` — Prototyp der fünf Screens (im Browser öffnen).
- `Kuechentisch Design System.dc.html` — das Design-System: Tokens, Typo, Bausteine, Regeln,
  Web↔Android-Mapping. Referenz bei allem, was dieses Dokument nicht nennt.
- `support.js`, `image-slot.js`, `android-frame.jsx` — nur Prototyp-Infrastruktur.

### Betroffene Repo-Dateien
- Tokens/Themes: `res/values/colors.xml`, `dimens.xml`, `themes.xml`, neu `res/font/*`
- Liste: `res/layout/activity_main.xml`, `res/layout/item_recipe.xml`,
  `ui/MainActivity.kt`, `ui/adapter/RecipeAdapter.kt`
- Detail: `res/layout/activity_recipe_detail.xml`, `ui/RecipeDetailActivity.kt`,
  `ui/adapter/ImagePagerAdapter.kt`
- Kochmodus (neu): `ui/CookModeActivity.kt`, `res/layout/activity_cook_mode.xml`,
  `res/layout/item_cook_step.xml`, Eintrag in `AndroidManifest.xml`
- Planer: `res/layout/activity_weekly_planner.xml`, `item_day_plan.xml`, `item_meal_slot.xml`,
  `ui/WeeklyPlannerActivity.kt`, `ui/AddToWeekPlannerBottomSheet.kt`
- Hinzufügen: neu `ui/AddRecipeBottomSheet.kt`, `res/layout/bottom_sheet_add_recipe.xml`,
  bestehend `ui/RecipeImportActivity.kt`
- Filter: `ui/FilterSelectionBottomSheet.kt`, `ui/CollectionsBottomSheet.kt`
- Texte: `res/values/strings.xml`, `res/values-de/strings.xml`
- API-Anbindung: `data/api/CookbookApi.kt`, `data/models/Recipe.kt`,
  `data/repository/RecipeRepository.kt` (neue Felder + Endpunkte, siehe unten)

---

## Backend-Erweiterung (Voraussetzung)
Kochhistorie und Favoriten gehören auf den Server — sie tragen „Rezept der Woche", die
Planer-Vorschläge und müssen zwischen Web und App synchron sein. Backend-Repo:
`tosh-hamburg/cookbook` (Fastify + libSQL).

**Migration** — zwei Spalten und eine Tabelle:
```sql
ALTER TABLE recipes ADD COLUMN cook_count   INTEGER NOT NULL DEFAULT 0;
ALTER TABLE recipes ADD COLUMN last_cooked_at TEXT;          -- ISO 8601, NULL = nie gekocht

CREATE TABLE recipe_favorites (
  user_id    TEXT NOT NULL,
  recipe_id  TEXT NOT NULL,
  created_at TEXT NOT NULL,
  PRIMARY KEY (user_id, recipe_id)
);

CREATE TABLE recipe_cook_log (                                -- Historie, für „7× gekocht"
  id         TEXT PRIMARY KEY,
  user_id    TEXT NOT NULL,
  recipe_id  TEXT NOT NULL,
  cooked_at  TEXT NOT NULL,
  servings   INTEGER
);
```
`cook_count` und `last_cooked_at` sind denormalisierte Werte aus `recipe_cook_log` (billiges
Sortieren); beim Insert mitschreiben. Mandantentrennung wie bei den übrigen Tabellen.

**Endpunkte:**
| Methode | Pfad | Verhalten |
| --- | --- | --- |
| `POST` | `/recipes/:id/cooked` | Body `{ "servings": 4 }` (optional). Legt einen `recipe_cook_log`-Eintrag an, erhöht `cook_count`, setzt `last_cooked_at`. Antwort: `{ cookCount, lastCookedAt }`. Wird von „Fertig" im Kochmodus gerufen. |
| `DELETE` | `/recipes/:id/cooked/last` | Nimmt den letzten Eintrag zurück (Fehlbedienung am Herd). Antwort wie oben. |
| `PUT` | `/recipes/:id/favorite` | Setzt das Herz. Idempotent. Antwort `{ isFavorite: true }`. |
| `DELETE` | `/recipes/:id/favorite` | Entfernt es. Antwort `{ isFavorite: false }`. |
| `GET` | `/recipes/featured` | Ein Rezept: höchstes `cook_count`, Tie-Break `last_cooked_at` DESC, dann `created_at` DESC. Bei `cook_count = 0` überall: neuestes Rezept, Antwort mit `"reason": "newest"` statt `"most_cooked"`. |
| `GET` | `/recipes?favorite=true` | Bestehende Liste um den Filter erweitern (für einen späteren „Favoriten"-Chip). |
| `GET` | `/recipes?sort=most_cooked` | Sortierung für die Planer-Vorschläge. |

**Bestehende Antworten erweitern** — `GET /recipes`, `GET /recipes/:id` und die paginierte
Liste liefern zusätzlich:
```json
{ "cookCount": 7, "lastCookedAt": "2026-09-15T18:20:00Z", "isFavorite": true }
```
`isFavorite` ist nutzerbezogen (aus `recipe_favorites` für den Token-Nutzer), die beiden
anderen Felder sind rezeptbezogen. Alle drei sind additiv — bestehende Clients bleiben
funktionsfähig.

**Android-Seite:** `Recipe` und `RecipeListItem` um `cookCount: Int = 0`,
`lastCookedAt: String? = null`, `isFavorite: Boolean = false` ergänzen; in `CookbookApi.kt`
die fünf Routen aufnehmen; im `RecipeRepository` Herz und „Fertig" optimistisch schalten und
bei Fehler zurückrollen. Offline: fehlgeschlagene `cooked`/`favorite`-Aufrufe in einer kleinen
DataStore-Queue puffern und beim nächsten erfolgreichen Request nachschicken.

**Web-Seite:** dieselben Felder in `frontend/src/app/types/recipe.ts` und
`services/api.ts`; das Web-Konzept 2a nutzt `GET /recipes/featured` für „Rezept der Woche" und
dieselben Favorit-Routen für das Herz auf der Karte.
