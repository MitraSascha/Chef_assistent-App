# Auftrag an den App-Agenten – Server 1.22.0: Monteur-App

Anlass (User 16.10.2026): Die Büro-App ist für Monteure zu viel. Monteure brauchen: **Was habe ich heute zu tun**
(Telefon, Ort, Beschreibung), **einfache Berichte** zu Terminen/Projekten (heute schreiben sie sie in der HERO-App:
Zeit, Material, Tätigkeit) und **Zeit-/Urlaubsbuchung**. Die KI soll den größten Teil der Arbeit machen:
„Handy raus, sagen oder schreiben, was ich gemacht habe, wie lange und welches Material – den Rest macht die KI.“
Mehrere Kollegen schreiben nur Stichworte oder einfaches Deutsch – der Bericht muss trotzdem einer Hausverwaltung
vorgelegt werden können. Das erledigt der Server; die App muss nur Aufnahme/Text schicken.

Vertrag: [`openapi.json`](openapi.json) 1.22.0 (sha256 `6470912c…2b80ed8`), Beschreibung in [`API.md` 5c](API.md).
Es gibt nur Ergänzungen; die Büro-App bleibt unverändert (Aufträge 1.13–1.21 gelten weiter). Neu in `GET /v1/ich`:
Feld `rolle` (`buero` | `monteur`).

## Server (fertig)
- Rolle `monteur`: diese Zugänge erreichen nur `/v1/monteur/…`, `/v1/ich`, `/v1/abmelden`, `/v1/gesund` (sonst 403).
- Einrichtung per QR-Code: `mitra-monteur:<Server-URL>#<Code>` → `POST /v1/monteur/einrichten` → `cs_`-Token.
- Tagesansicht, Bericht (Diktat → Entwurf → „Passt“ → fertiger **Baustellenbericht in HERO** mit Positionen, die das
  Büro per Klick in die Rechnung übernimmt, Kundenunterschrift, Zeiten), Zeiten (HERO), Stempeluhr, Urlaub (HERO),
  Sprache → Vorschläge für Zeiten/Urlaub. Alles mit `anfrage_id` wiederholbar (Ausgangskorb).
- Ohne Termin/Projekt erkennt der Server selbst, wo der Monteur laut Plan gerade ist.

## Bitte bauen: zweite App im selben Projekt
- Gradle-Flavor **`monteur`** mit eigener Paket-ID (z. B. `de.mitra.monteur`), eigenem Namen „Mitra Monteur“ und Icon.
  Netz/Retrofit, Token-Speicher (Keystore), Ausgangskorb und Design (Farben/Symbole aus 1.21) mit der Büro-App teilen;
  Oberfläche eigen. Beide Apps getrennt verteilbar.
- **Keine** Chat-, Ablage-, Angebots-, Plantafel-, Modellwahl-, Auftrags- oder Videofunktionen in dieser App.

### 1. Einrichtung (erster Start)
- Bildschirm „Mit QR-Code einrichten“ (Kamera, ML Kit Barcode) + „Code eintippen“ (Server-URL vorbelegt
  `https://assistent.mitra-sanitaer.de`, in versteckten Einstellungen änderbar).
- `POST /v1/monteur/einrichten {"code","geraet": "<Gerätename>"}` → Token speichern. `401` → „Code ungültig oder
  abgelaufen – bitte im Büro einen neuen holen“. Später jeder `401` → zurück auf diesen Bildschirm.
- Nach der Einrichtung `GET /v1/ich`; ist `rolle` nicht `monteur`, trotzdem zulassen (Büro testet).

### 2. Heute (Startbildschirm)
- `GET /v1/monteur/tage` (heute); Wischen nach links/rechts = nächster/voriger Tag, Kalendersymbol = Woche
  (`von`/`bis`, max. 14 Tage). Ziehen zum Aktualisieren.
- Je Termin eine große Karte: Uhrzeit, Kunde, Titel, Adresse (antippen → Navigation über `geo`/Adresse),
  Telefon(e) (antippen → Wählen), Beschreibung aus HERO, Kollegen. Status-Chip: **„Bericht fehlt“** (gold) bzw.
  „Entwurf“ / „BER-1330“ (grün) aus `berichte`.
- Karte antippen → Termindetail = Projektmappe (2a) mit großem Knopf **„Bericht“** (wie in HERO: Termin antippen → dort alles erledigen).

### 2a. Projektmappe (Termindetail)
- Termindetail = Projekt wie in HERO: `GET /v1/monteur/projekte/{projekt.id}`. Oben Kunde, Adresse (Navigation),
  Telefon(e), darunter **Notizen** aus HERO (Notizfeld des Büros – hervorgehoben, wenn nicht leer).
- **Unterlagen** gruppiert nach `art` (Angebot, Auftragsbestätigung, Aufmaß, Unterlage = Pläne …, Bericht …), Zeile
  mit Nummer/Name, Datum, Status; antippen → PDF im eingebauten Viewer (`GET /v1/monteur/dateien/{uuid}`, mit Token).
  `datei: null` → grau „PDF folgt“.
- **Fotos** als Raster mit `?vorschau=true`, antippen → Vollbild (ohne `vorschau`), wischen.
- Rechnungen gibt es hier bewusst nicht (Server sperrt sie).
- Großer Knopf **„Bericht“** unten.

### 3. Bericht – so wenig Tippen wie möglich
- **Großer Mikrofon-Knopf unten auf jedem Bildschirm** („Was hast du gemacht?“): Aufnahme gedrückt halten oder
  antippen-start/antippen-stopp. Alternativ Textfeld. Hinweis darunter klein: „Was, wie lange, welches Material –
  in deiner Sprache, Stichworte reichen.“
- Vom Termindetail aus mit `termin_id`; vom Mikrofon auf „Heute“ **ohne** Termin (der Server findet den Termin).
  `POST /v1/monteur/berichte` multipart (`audio` m4a/aac oder `text`, `anfrage_id` = neue UUID ohne Bindestriche,
  im Ausgangskorb merken). Läuft 10–40 s → Fortschritt „KI schreibt den Bericht …“.
- **Bestätigungskarte** (eine Seite, scrollbar):
  - Projekt/Kunde/Datum oben (bei automatisch erkanntem Termin deutlich zeigen, damit der Monteur es merkt).
  - „Ausgeführte Arbeiten“ (Text, antippen = bearbeiten), „Noch offen“ (Punkte).
  - Stunden Monteur / Helfer als +/− Stepper (0,25 h), Schalter Anfahrt, Klein- & Dichtmaterial.
  - Material-Liste: Menge (Stepper), Name des gewählten Artikels bzw. Diktat-Text. Hat ein Eintrag `rueckfrage`:
    gold markiert, Frage anzeigen, darunter die `vorschlaege` als Chips zum Antippen (→ `PATCH` mit `artikel_nr`) oder
    „Frei lassen“ (`artikel_nr: null`) oder per Mikrofon antworten (`POST …/diktat`).
  - Zeiten des Tages (aus dem Entwurf) als Zeilen von–bis/Art.
  - „Fürs Büro“ (hinweise_buero) und offene `rueckfragen` als Hinweiskarte.
  - Unten: Mikrofon „Ergänzen/Korrigieren“ (`POST …/diktat`), **„Kunde unterschreibt“** (Unterschriftsfeld
    vollflächig, quer erlaubt, Name vorbelegt mit Kunde, Löschen/OK → PNG, weißer Hintergrund, max. 2 MB) und
    **„Passt – nach HERO“** (`POST …/abschliessen`). Ohne Unterschrift fragen „Ohne Unterschrift abschließen?“.
- Nach Erfolg: grüner Haken + `hero_nr`; `hinweise` (falls vorhanden) als Liste zeigen. `409` mit Rückfrage-Text →
  zurück zur Karte. `502`/Netz → im Ausgangskorb lassen und später wiederholen (gleiche Bericht-ID, der Server legt
  nie doppelt an).
- Entwurf verwerfen: `DELETE …/{id}` (nur Entwurf).

### 4. Zeiten
- Reiter „Zeiten“: `GET /v1/monteur/zeiten?datum=` – Tagesleiste mit Einträgen (Art-Symbol, von–bis, Projekt),
  Summen Arbeit/Pause, oben **Stempeluhr** (Start/Wechsel/Stopp, laufende Dauer aus `stempel.beginn`).
- „+“ → Zeitabschnitt (`POST /v1/monteur/zeiten`, Ortszeit `YYYY-MM-DDTHH:MM`), Wischen = löschen, antippen = ändern
  (nur Status `neu`; sonst Schloss-Symbol „eingereicht“).
- Mikrofon auf diesem Reiter → `POST /v1/monteur/verstehen` → Vorschläge anzeigen → „Übernehmen“ bucht jeden mit
  `POST /v1/monteur/zeiten` (eigene `anfrage_id` je Vorschlag).
- Abends (ab 15 Uhr oder letzter Termin vorbei): Banner **„Tag bestätigen“** mit Tagesübersicht →
  `POST /v1/monteur/zeiten/{datum}/einreichen`. Danach Tag gesperrt.
- Unten klein: Stundenkonto des Monats (`GET /v1/monteur/stundenkonto`, Minuten → „+1:20 h“).

### 5. Urlaub
- Reiter „Urlaub“: `GET /v1/monteur/abwesenheiten` – oben Stand (verfügbar groß, genehmigt/beantragt klein), darunter
  Liste mit Status-Chips (beantragt gold, genehmigt grün, abgelehnt rot).
- „Urlaub beantragen“: Zeitraum (Datumsbereich), Art (Urlaub, Überstundenausgleich, Sonderurlaub, unbezahlt; Krank/
  Kind krank nur als eigener Knopf „Krank melden“), halbe Tage, Kommentar → `POST /v1/monteur/abwesenheiten`.
  Oder Mikrofon („nächste Woche Donnerstag und Freitag frei“) → `POST /v1/monteur/verstehen` → Vorschlag bestätigen.
- Zurückziehen: Wischen auf „beantragt“ → `DELETE …/{id}?jahr=`.

### Navigation
Unten drei Reiter: **Heute** · **Zeiten** · **Urlaub**. Der Mikrofon-Knopf schwebt über allen drei (auf „Heute“ =
Bericht, auf „Zeiten“/„Urlaub“ = verstehen). Abmelden und Server-URL in einem kleinen Menü oben rechts.

### Testen
Ein Büro-Zugang mit HERO-Zuordnung (z. B. sascha) darf alle Monteur-Endpunkte benutzen – zum Testen in der
Monteur-App mit Passwort+Code anmelden lassen (versteckte Option „Mit Büro-Zugang anmelden“). **Achtung:**
„Passt – nach HERO“ legt echte Berichte an; zum Testen ein Projekt `UNB-795` (Testprojekt) verwenden.
