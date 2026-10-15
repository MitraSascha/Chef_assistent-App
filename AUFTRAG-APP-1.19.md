# Auftrag an den App-Agenten – Server 1.19.0: HERO-Projekt anheften (Begehung geht automatisch nach HERO)

Anlass: Bei Begehungen zu Projekten, die es in HERO schon gibt, sollen **alle Notizen, Fotos, Videos und Dokumente
automatisch im HERO-Projekt landen**. Bisher konnte die App nur eigene Notizordner und GU-Projekte anheften. Am
15.10.2026 hat der Assistent deshalb einen Ordner im HERO-Archiv angelegt, und was dort lag, kam nie in HERO an.

Vertrag: [`openapi.json`](openapi.json) 1.19.0 (sha256 `f5f0de4e…886dc`), Beschreibung in
[`API.md` 4 → „HERO-Projekt anheften“](API.md). Es gibt nur Ergänzungen. Die Aufträge 1.13 bis 1.18 gelten weiter.

**Kurz:** Ein HERO-Projekt anheften heißt: `POST /v1/hero-mappe` → man bekommt einen normalen Notizordner (die
„HERO-Mappe“) mit `name` und `pfad`. Danach funktioniert alles wie beim bisherigen Anheften: Nachrichten bekommen
`Bezieht sich auf Projekt <name>.`, und Anhänge gehen mit `notiz = <pfad>` an den Server. Den Rest erledigt der Server:
Fotos und Dokumente gehen nach ca. 1 Minute ins HERO-Projekt, Notizen als PDF nach 15 Minuten Ruhe. **Videos gehen
nicht nach HERO** (HERO weist sie ab). Sie bleiben in der Mappe auf dem Server; die Abschrift steht in der Notiz.

## Bitte bauen
1. **Anheften-Auswahl um HERO-Projekte erweitern.** Im Dialog, in dem man heute einen Ordner aus `GET /v1/notizen`
   anheftet, kommt oben ein Suchfeld dazu:
   - Ab 2 Zeichen `GET /v1/suche?q=<eingabe>&typ=ordner&ordner=HERO-Archiv&max=30` (250 ms entprellen, alte Anfrage
     abbrechen). Nur Treffer anzeigen, deren `pfad` genau zwei Teile hat (`HERO-Archiv/<Projekt>`), also nicht
     `Fotos/`, `Rechnungen/` usw.
   - Zeile: Abzeichen **HERO**, `name` fett, darunter `projekt.bauvorhaben · projekt.titel · projekt.status`
     (wenn `projekt` fehlt: „HERO-Projekt“).
   - Darunter wie bisher die eigenen Ordner aus `/v1/notizen`, gefiltert nach der Eingabe. Ordner mit
     `hero_projekt != null` bekommen ebenfalls das Abzeichen **HERO**.
2. **HERO-Treffer antippen** → `POST /v1/hero-mappe` mit `{"projekt": "<pfad des Treffers>"}`. Mit der Antwort
   anheften, genau wie einen Notizordner: Anzeigename = `name`, Ziel für Anhänge = `pfad`. Kurz melden:
   „SAN-660 angeheftet – Notizen, Fotos und Dokumente gehen automatisch ins HERO-Projekt (Videos nicht).“ Bei `404` den Text aus
   `detail` zeigen.
3. **Angeheftet-Leiste:** Bei HERO-Mappen das Abzeichen **HERO** vor dem Namen zeigen. Langes Drücken oder ⓘ zeigt
   „Geht automatisch ins HERO-Projekt SAN-660“.
4. **Optional: Stand anzeigen.** `GET /v1/hero-mappe?projekt=<projekt>` liefert `dateien` mit `status`
   (`kopiert` / `zu_gross` / `schon_in_hero` / `video`). Zum Beispiel als Info-Blatt „In HERO: 12 Dateien ·
   2 Videos nur auf dem Server“. Nicht nötig für die Abnahme.

Wichtig: `name` immer **unverändert** in `Bezieht sich auf Projekt <name>.` einsetzen. Für Anhänge immer den `pfad`
der Mappe als `notiz` mitschicken, auch beim Nachsenden aus der Warteschlange.

## Abnahme
- In der App „SAN-660“ suchen → HERO-Treffer „SAN-660 Firma WEG Trelleborger Straße 14 …“ anheften.
- Im Chat „Keller: Zähler links, Steigleitung Kupfer“ diktieren und 2 Fotos aufnehmen. Alles liegt unter
  `Notizen/<Name>/SAN-660 …`.
- Nach ca. 6 Minuten sind die Fotos im HERO-Projekt (HERO-App/Web, Dokumente/Fotos). Rund 20 Minuten nach der
  letzten Änderung ist auch die Notiz als PDF da.
- `GET /v1/notizen` zeigt die Mappe mit `hero_projekt: "SAN-660"`, und das Abzeichen ist zu sehen.
- Ein Projekt, das es in HERO nicht gibt (z. B. „SAN-99999“), zeigt keinen HERO-Treffer.
