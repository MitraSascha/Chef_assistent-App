# Auftrag an den App-Agenten – Server 1.18.0: HERO-Termine anlegen und verschieben

Anlass: Termine sollen nicht nur angezeigt, sondern direkt aus der App **in HERO eingetragen und verschoben** werden.
Sie erscheinen dann sofort in der HERO-Plantafel und in der HERO-App der Monteure.
Am 14.10.2026 wurde mit HERO getestet: Anlegen, Verschieben und Löschen funktionieren.

Vertrag: [`openapi.json`](openapi.json) 1.18.0 (sha256 `6b3bda6a…40c8`), Beschreibung in [`API.md` 5b](API.md).
Es gibt nur Ergänzungen. Die Aufträge 1.13 bis 1.17 gelten weiter.

**Wichtig – das ändert sich gegenüber 1.16:**
- HERO-Termine haben jetzt `nur_lesen: false`, wenn der Server schreiben darf. Der alte Hinweis „Aus HERO – ändern
  nur in HERO“ erscheint nur noch bei `nur_lesen: true`.
- Neues Feld `hero_schreiben` in `GET /v1/kalender`. Nur wenn es `true` ist, gibt es „＋ HERO-Termin“ und „Verschieben“.
- Im Chat ist **nichts** zu tun: Der Assistent schlägt Termine vor, bestätigt wird mit den bekannten Ja/Nein-Knöpfen.

## Bitte bauen
1. **Knopf „＋ HERO-Termin“** im Kalenderkopf, nur bei `hero_schreiben: true`. Er öffnet ein Formular:
   - **Für**: Personen aus `/v1/personen`, Mehrfachauswahl erlaubt, vorbelegt mit „ich“ (falls zugeordnet).
   - **Titel** (Pflicht).
   - **Kategorie** (Pflicht, ohne Vorauswahl) aus `GET /v1/kalender/kategorien`. Die Farbe als Punkt anzeigen.
   - **Datum, Von, Bis** mit Android-Pickern. Schalter **ganztägig** (Urlaub, Schule): dann „Datum“ und „Bis Datum“,
     keine Uhrzeiten.
   - **Projekt** (optional, HERO-Nummer wie SAN-1270) und **Notiz** (optional: Kunde, Telefon, Etage …).
   - Ist die Tagesansicht offen, das Datum mit diesem Tag vorbelegen.
2. **„Verschieben“** an jeder HERO-Termin-Karte mit `nur_lesen: false`, im aufgeklappten Bereich. Das Formular hat nur
   Datum, Von und Bis, vorbelegt mit der alten Zeit. Oben steht „bisher …“.
3. **Immer zwei Schritte**, wie in API.md 5b:
   - „Prüfen“ sendet `?nur_pruefen=true`. Dann zeigen: `beschreibung` und die Liste `ueberschneidungen`, bei
     Überschneidungen mit ⚠ in Warnfarbe, sonst „Keine Überschneidungen“. Gibt es einen `hinweis`, ihn klein darunter
     zeigen.
   - Der Knopf wird zu **„In HERO eintragen“** bzw. **„In HERO verschieben“**. Ändert der Nutzer danach ein Feld, muss
     er wieder prüfen.
   - Eintragen: dieselbe Anfrage ohne `nur_pruefen`. Bei Erfolg Formular schließen, kurz „In HERO eingetragen“ melden
     und den Kalender neu laden. Der Termin ist sofort da.
   - Bei `422` den Text aus `detail` im Formular zeigen, z. B. „Kategorie unklar“ oder „steht schon im Kalender“.
4. **Doppelt senden verhindern:** Den Knopf sperren, solange die Anfrage läuft. Der Server schützt zusätzlich vor
   Dubletten.

## Abnahme
- Patrick legt für Julian morgen 08:00–09:00 „Test App – bitte löschen“ an, Kategorie Büro. Beim Prüfen erscheinen
  Julians Überschneidungen. Nach dem Eintragen steht der Termin in der App **und in HERO** bei Julian.
- Diesen Termin auf 10:00 verschieben: In App und HERO steht 10:00–11:00.
- Gleichen Termin noch einmal anlegen: Es erscheint die Meldung „steht … schon im HERO-Kalender“, und es entsteht keine Dublette.
- Ohne Kategorie lässt sich nicht prüfen.
- Danach den Testtermin in HERO löschen. Die App kann nicht löschen, das ist so gewollt.
