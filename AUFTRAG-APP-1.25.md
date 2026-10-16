# Auftrag an den App-Agenten – Server 1.25.0: Monteur-App, Protokolle

Anlass (User 16.10.2026): Monteure sollen Druckproben, Dichtheitsprüfungen, Inbetriebnahmen und Wartungen direkt auf
der Baustelle protokollieren – mit Kundenunterschrift, fertig als PDF im HERO-Projekt. **Keine neuen Knöpfe:** kein
Menüpunkt „Protokoll“, keine Formularseite je Vorlage. Der Monteur sagt einfach „Druckprobe Trinkwasser 10 bar,
30 Minuten, dicht“ oder fotografiert Manometer bzw. Messausdruck – es kommt eine Karte wie bei allem anderen.

Vertrag: [`openapi.json`](openapi.json) 1.25.0 (sha256 `5e08ea7b…8fa4e1a`), Beschreibung in
[`API.md` 5d, Abschnitt „Protokolle“](API.md#protokolle--neu-in-1250). Es gibt nur Ergänzungen; alles aus
[`AUFTRAG-APP-1.24.md`](AUFTRAG-APP-1.24.md), [`AUFTRAG-APP-1.23.md`](AUFTRAG-APP-1.23.md) und
[`AUFTRAG-APP-1.22.md`](AUFTRAG-APP-1.22.md) gilt weiter. Betrifft nur den Flavor `monteur`; die Büro-App ist nicht
betroffen.

## Server (fertig)
- **Neue Absicht `protokoll`** im Eingang (`POST /v1/monteur/eingang`). Die KI wählt eine von 5 Vorlagen
  (`druckpruefung_trinkwasser`, `druckpruefung_heizung`, `gasleitung`, `inbetriebnahme_waermeerzeuger`,
  `wartung_gastherme`), trägt die Werte aus Sprache und Fotos ein (Manometer, Display, Messausdruck, Typenschild) und
  fragt fehlende Pflichtwerte in `rueckfrage` nach. Projekt wie bei `meldung`/`bestellung` – keins erkennbar → `422`.
- **Karte:** `art: "protokoll"`, Feld `protokoll` = `{vorlage, titel, grundlage, felder:[{k, l, t, e, o, p, wert}],
  fehlend:[…]}`. `t` ist `text`, `zahl`, `janein` oder `auswahl`; `e` = Einheit, `o` = Auswahlwerte, `p` = Pflicht,
  `wert` immer Text oder `null`. `fehlend` = Beschriftungen der leeren Pflichtfelder.
- **„Passt“** (`POST /v1/monteur/eingang/{id}/passt`) mit `werte` `{k: wert}` (nur Geändertes, `null` leert),
  optional `vorlage` (andere Vorlage, Werte gleicher Schlüssel bleiben), `unterschrift_png`, `unterschrift_name`.
  Fehlt noch ein Pflichtwert → **`409` „Es fehlen noch: …“** – die mitgeschickten Werte sind trotzdem gespeichert.
  Unbekannte Vorlage → `422`.
- Sonst: **PDF mit Mitra-Briefkopf** (alle Felder mit Einheit, Unterschrift, Fotos als Anlage) → als Dokument
  **„Protokolle“** ins HERO-Projekt. `ergebnis` = `{hero: "Protokoll im Projekt", hero_id, datei, hinweise[]}`.
  HERO lehnt ab → `502`, Karte bleibt offen.
- **`GET /v1/monteur/eingang/{id}/protokoll.pdf`** → vor „Passt“ **Vorschau** (aktueller Stand, ohne Unterschrift),
  danach das an HERO geschickte PDF.
- Die Vorlagen sind ein Startvorschlag; der Chef prüft Felder und Normbezüge. Änderungen kommen nur vom Server –
  die App muss dafür nicht neu gebaut werden, solange sie die Karte generisch aus `felder` zeichnet.

## Bitte bauen

**Wichtig vorweg: keine neuen Knöpfe, Menüs oder Reiter.** Kein Knopf „Protokoll“, keine Vorlagen-Auswahlseite,
keine eigene Ansicht je Vorlage. Alles passiert auf der Karte im Eingang.

### 1. Protokoll-Karte = generisches Formular aus `felder`
- Kopf: `titel` fett, darunter `grundlage` klein und grau, Projekt deutlich wie bei den anderen Karten.
- Je Eintrag in `felder` eine Zeile mit `l` als Beschriftung, Eingabe nach `t`:
  - `text` → einzeiliges Textfeld (bei `k` = `bemerkung`/`maengel` mehrzeilig, max. 500 Zeichen).
  - `zahl` → Zahlenfeld (Tastatur mit Komma), Einheit `e` fest dahinter („bar“, „min“ …). Komma schicken, wie
    eingegeben – der Server normalisiert.
  - `janein` → Schalter oder zwei Chips „ja“/„nein“; **leer ist erlaubt** (dann weder noch), nicht stillschweigend
    „nein“ vorbelegen.
  - `auswahl` → Auswahl-Chips mit den Werten aus `o` (einer wählbar, nochmal tippen = leer).
- Wert aus `wert` vorbelegen; `null` = leer.
- **Pflichtfelder** (`p: true`) mit kleinem Stern. Felder, deren `l` in `fehlend` steht, **farbig hervorheben**
  (Rahmen/Hintergrund wie Warnung) und oben auf der Karte eine Zeile „Es fehlen noch: …“. Nach jeder Eingabe lokal
  neu prüfen (leer + Pflicht = fehlt).
- `rueckfrage` wie bisher über der Karte zeigen. Fehlende Werte darf der Monteur auch **sagen** – Mikrofon wie immer;
  das Ergebnis ist dann allerdings eine neue Karte. Einfacher: Eintippen in der Karte.
- **Fotos** zeigen wie bei den anderen Karten (Vorschaubilder); sie kommen als Anlage ins PDF.
- Unbekanntes `t` (spätere Server-Version) → als Textfeld zeigen, nicht abstürzen.

### 2. Vorlage wechseln
- Kleines **Dropdown im Kartenkopf** (am Titel, z. B. „Druckprüfung Trinkwasserinstallation ▾“) mit den 5 Vorlagen
  (`vorlage` → Titel siehe [API.md](API.md#protokolle--neu-in-1250)). Kein eigener Knopf.
- Liste der Vorlagen (leer): `GET /v1/monteur/protokolle/vorlagen`. Auswahl → sofort
  `POST …/passt {"vorlage": "<neu>", "nur_speichern": true}` → Antwort = Karte mit den Feldern der neuen Vorlage
  (Werte mit gleichem `k` bleiben). Es geht dabei **nichts** nach HERO.

### 3. Vorschau
- Kleiner **Textlink „Vorschau“** in der Karte (unten neben den Fotos, kein großer Knopf) → PDF-Ansicht von
  `GET /v1/monteur/eingang/{id}/protokoll.pdf` mit Token im Header (gleicher PDF-Viewer wie bei `…/anleitung` aus
  1.23).
- Vor dem Öffnen der Vorschau geänderte Felder mit `POST …/passt {"werte": {…}, "nur_speichern": true}` speichern
  (geht nicht nach HERO), dann das PDF laden – so zeigt die Vorschau immer den Stand in der Karte.
- Nach „Passt“ heißt der Link „PDF“ und zeigt das an HERO geschickte PDF.

### 4. Unterschrift und „Passt“
- Unterschrift **wie beim Bericht** (gleiches Unterschriftsfeld, Name des Kunden vorbelegt mit `projekt.kunde`,
  änderbar). Optional – ohne Unterschrift geht es auch.
- „Passt“ → `POST …/passt` mit `werte` (nur geänderte Felder), ggf. `vorlage`, `unterschrift_png`,
  `unterschrift_name`. Über den Ausgangskorb, wiederholbar wie bisher.
- **`409` „Es fehlen noch: …“** → Text zeigen, Karte mit `GET /v1/monteur/eingang/{id}` neu laden und die Felder aus
  `fehlend` hervorheben, erstes fehlendes Feld anspringen. Nichts geht verloren.
- `422` (Unterschrift/Vorlage ungültig) → `detail` zeigen. `502` → „HERO hat das Protokoll nicht angenommen“ +
  „Nochmal versuchen“ (gleiche Karte, gleiche Werte).
- Erfolg → Karte „erledigt“ mit Häkchen und „Protokoll im Projekt“ (`ergebnis.hero`), `ergebnis.datei` klein darunter,
  `ergebnis.hinweise` zeigen, wenn nicht leer. Der Link „Vorschau“ heißt dann „PDF“.

### Testen
Mit einem **Monteur-Zugang** und einem Termin mit Projekt für heute (Testprojekt, nicht beim Kunden):
1. Sagen „Druckprobe Trinkwasser 10 bar, 30 Minuten, dicht“ → Karte `protokoll`, Vorlage Trinkwasser, Prüfdruck 10,
   Dauer 30, dicht = ja; `fehlend` u. a. Abschnitt, Werkstoff, Prüfmedium, Druckabfall – hervorgehoben.
2. „Passt“ sofort → `409` mit der Liste. Werte eintragen, „Vorschau“ → PDF mit Briefkopf, ohne Unterschrift.
3. Vorlage auf „Belastungs- und Dichtheitsprüfung Gasleitung“ wechseln, ohne Unterschrift „Passt“ → `409`, Karte
   zeigt die Gas-Felder, `abschnitt` ist übernommen.
4. Unterschreiben, „Passt“ → in HERO im Projekt ein Dokument „Protokolle“ mit dem PDF, Fotos als Anlage.
5. Foto vom Manometer + „Protokoll“ → Prüfdruck von der Anzeige abgelesen.
Testdokumente in HERO danach wieder löschen.
