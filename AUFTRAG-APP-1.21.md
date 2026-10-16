# Auftrag an den App-Agenten – Server 1.21.0: Gesprächsliste (Teil A) und Neugestaltung (Teil B)

Anlass: Die Chefs sind viel in der Windows- und der Android-App. Die Oberfläche war „weder schön noch besonders
hässlich – eine Standardmaske“, und die Gesprächsliste ist inzwischen „ein elend langer Chatverlauf“. Die
Weboberfläche (und damit die Windows-Hülle) ist neu gestaltet. Die Android-App soll **dieselbe Handschrift** bekommen,
angepasst an Android-Gewohnheiten.

Vertrag: [`openapi.json`](openapi.json) 1.21.0 (sha256 `30c08862…659221`), Beschreibung in [`API.md` 1e und 7](API.md).
Es gibt nur Ergänzungen. Die Aufträge 1.13 bis 1.20 gelten weiter.

# Teil A – Gesprächsliste: anheften, suchen, nach Zeit gruppieren

## A: Server (fertig)
- `GET /v1/gespraeche` liefert je Gespräch zusätzlich **`angeheftet`** (bool). Reihenfolge: angeheftete zuerst, dann
  `geaendert` absteigend (max. 100).
- `GET /v1/gespraeche?q=<text>` sucht im **Titel über alle Gespräche** (alle Wörter müssen vorkommen).
- `PATCH /v1/gespraeche/{id}` mit `{"angeheftet": true|false}` heftet an bzw. löst. Antwort = Listeneintrag.
  `geaendert` bleibt unverändert. Kombinierbar mit `modell`/`denkaufwand` aus 1.20.

## A: Bitte bauen
1. **Gruppen** in der Gesprächsliste (Abschnittsüberschriften, klein, grau, mit Anzahl), nach `geaendert` in Ortszeit:
   **Angeheftet** · **Heute** · **Gestern** · **Letzte 7 Tage** · **Letzte 30 Tage** · danach je **Monat**
   („September 2026“). Monatsgruppen zunächst eingeklappt, Antippen klappt auf/zu, der Zustand wird gemerkt.
2. Unter jedem Titel eine **zweite, graue Zeile**: Uhrzeit (Heute/Gestern), „Mo 12.10.“ (7 Tage) bzw. „28.9.“.
   Läuft gerade eine Antwort (`laeuft`), einen kleinen goldenen Punkt rechts zeigen.
3. **Anheften:** langes Drücken auf ein Gespräch → Menü „Oben anheften“ / „Nicht mehr anheften“ und „Löschen“
   (Löschen mit Rückfrage wie bisher). Im geöffneten Chat zusätzlich ein Pin-Symbol in der oberen Leiste
   (gedrückt = angeheftet). Nach dem `PATCH` die Liste neu laden.
4. **Suche:** Lupe oben in der Liste → Suchfeld; ab 2 Zeichen nach 250 ms `GET /v1/gespraeche?q=…`, Treffer als eine
   Gruppe „Treffer für „…““; leeres Feld bzw. Zurück = normale Liste.
5. Alter Server (kein Feld `angeheftet`): Feld als `false` lesen, Anheften-Menüpunkt ausblenden.

# Teil B – Neugestaltung (gleiche Handschrift wie Web/Windows)

Vorbild ist die Weboberfläche ab 1.21.0 (`https://assistent.mitra-sanitaer.de/`, gern selbst ansehen). Keine neuen
Endpunkte nötig. Bitte mit **Material 3** (Jetpack Compose) umsetzen, ohne die Bedienlogik aus 1.13–1.20 zu ändern.

## B1. Farben und Schrift (hell und dunkel, nach Systemeinstellung)
| Rolle | hell | dunkel |
|---|---|---|
| Hintergrund | `#F6F5F2` | `#12181D` |
| Fläche (Karten, Leisten) | `#FFFFFF` | `#1B232A` |
| Seitenleiste / Navigationsfläche | `#EFEDE8` | `#0F1418` |
| Text | `#1D2327` | `#E6E8EA` |
| Text leise | `#5B6570` | `#9AA4AD` |
| Linien | `#E3E0D9` | `#2A343D` |
| Auswahl (aktiver Eintrag) | `#E2E7EC` | `#26323C` |
| Hauptfarbe (Knöpfe, Senden) | Navy `#1B3244` | `#2F5878` |
| Akzent (Zähler, Fokus, „läuft“) | Gold `#DA9C4D` | `#DA9C4D` |
| eigene Nachricht (Blase) | `#E8EEF3` | `#24323E` |
| Hinweis-/Fragekarte | `#FBF0DF`, Text `#8A5A17` | `#33291B`, Text `#E8B673` |
| Fehler | `#B3261E` | `#F2A49C` |

Schrift: Systemschrift (Roboto/Google Sans), Text 15 sp, leise Zeilen 12–13 sp. Abrundungen: Karten 12 dp, Eingabe 18 dp,
Knöpfe 8–10 dp, Chips 15 dp. Gold nie als Textfarbe auf Weiß (zu wenig Kontrast), nur als Fläche/Punkt.

## B2. Symbole statt Emojis
Überall **Material Symbols (Outlined)** statt Emojis: Chat `chat_bubble`, Kalender `calendar_month`, Plantafel
`view_timeline`, Ablage `folder`, Offen `task_alt`, Anheften `keep`, Anhang `attach_file`, Mikrofon `mic`, Senden
`arrow_upward`, Vorlesen `volume_up`, Explorer/Ordner `folder_open`, extern öffnen `open_in_new`, Löschen `delete`.
Dateitypen als **kleine Marke mit Kürzel** (PDF rot getönt, JPG/PNG blau, XLSX grün, Ordner gold) statt 📄/🖼️.

## B3. Aufbau
- **Untere Navigation** (NavigationBar, 4 Ziele): **Chat** · **Kalender** · **Ablage** · **Offen** (mit Zähler-Badge
  in Gold). Die GU-Plantafel bleibt über den Chat/Menü erreichbar wie bisher.
- **Gesprächsliste** als Startseite des Reiters Chat (Teil A), schwebender Knopf „Neues Gespräch“ (Navy, abgerundetes
  Quadrat) unten rechts.
- **Chat:** Antworten des Assistenten **ohne Blase** mit kleinem „M“-Zeichen (Navy-Quadrat, goldenes M) links;
  eigene Nachrichten als ruhige Blase rechts (`#E8EEF3`). Systemzeilen klein, grau, mittig.
- **Eingabe** als Karte (18 dp Ecken, leichter Schatten): oben das Textfeld, darunter eine Zeile mit Anhang, Chip
  „Projekt anheften“ (angeheftet: goldener Chip mit ✕), Modell-/Denk-Chips (1.20), rechts Mikrofon und runder
  Senden-Knopf. Während einer Antwort wird Senden zu „Stopp“ (rot) bzw. „Zwischenruf“ (gold) wie in 1.20.
- **Datei-Karten im Chat** (statt Textknöpfen): Typmarke · Name (fett) · Ordnerpfad (grau, „ › “ getrennt) · rechts
  „Ansehen“ (Navy) und ein Ordner-Symbol. Ordner-Links: „Ordner öffnen“.
- **Ansehen in der App:** PDF mit `android.graphics.pdf.PdfRenderer` als Seitenbilder (Zoom per Pinch), Fotos mit
  Zoom, Videos mit dem Player, `.txt/.md/.csv` als Text (Markdown einfach formatiert). Datei über
  `GET /v1/datei?pfad=…&anzeigen=true` laden (mit Token). Oben: Name, Seitenzahl, „Öffnen mit …“ (ACTION_VIEW wie
  bisher), Teilen. Andere Typen (Word/Excel) wie bisher direkt per ACTION_VIEW.
- **Kalender:** Wochenansicht als Liste je Tag, Kopf „12.–18. Oktober 2026 · KW 42“, Umschalter Tag/Woche als
  Segmented Button; Termine als getönte Karten in der Personenfarbe (Palette `--d1…d6` aus 1.16) statt Randstrich.
  Oben auf dem Handy zusätzlich eine kleine Karte **„Heute“** mit den eigenen Terminen des Tages
  (`GET /v1/kalender?von=<heute>&bis=<heute>&personen=ich`), laufender Termin mit goldenem Rand.
- **Leerer Chat:** „M“-Zeichen, „Hallo <Name>!“, kurzer Hinweis und drei Vorschlags-Chips („Was steht heute an?“,
  „Was ist noch offen?“, „Welche Termine habe ich diese Woche?“) – Antippen schickt den Text ab.

## B4. Bitte beachten
- Tippflächen mindestens 48 dp, Kontrast Text ≥ 4,5 : 1 (mit den Farben oben erfüllt).
- Nichts an Anmeldung, Fotos/Videos/Dokumente, HERO-Mappe, Bestätigungen, langen Anfragen (1.17) oder Fortschritt
  (1.20) ändern – nur Aussehen und Anordnung.
- Rückmeldung bitte mit Bildschirmfotos (hell + dunkel) von: Gesprächsliste, Chat mit Datei-Karte, PDF-Ansicht, Kalender.
