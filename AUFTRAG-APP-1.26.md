# Auftrag an den App-Agenten – Server 1.26.0: Monteur-App, Vorgeschichte

Anlass (User 16.10.2026): Bevor der Monteur losfährt, soll er wissen, was an der Baustelle schon war – welches Gerät
verbaut ist, wann zuletzt gewartet wurde, was noch offen ist, wo der Schlüssel liegt. Bisher musste er dafür alte
Berichte in der Projektmappe aufmachen, und ältere Projekte (z. B. die Wartung im Vorjahr) sah er gar nicht. **Keine
neuen Knöpfe:** die Vorgeschichte steht einfach im Termin und oben in der Projektmappe.

Vertrag: [`openapi.json`](openapi.json) 1.26.0 (sha256 `6105f5c0…ff2e668`), Beschreibung in
[`API.md` 5c, Abschnitt „Vorgeschichte“](API.md#vorgeschichte--neu-in-1260). Es gibt nur Ergänzungen; alles aus
[`AUFTRAG-APP-1.25.md`](AUFTRAG-APP-1.25.md), [`AUFTRAG-APP-1.24.md`](AUFTRAG-APP-1.24.md),
[`AUFTRAG-APP-1.23.md`](AUFTRAG-APP-1.23.md) und [`AUFTRAG-APP-1.22.md`](AUFTRAG-APP-1.22.md) gilt weiter. Betrifft
nur den Flavor `monteur`; die Büro-App ist nicht betroffen.

## Server (fertig)
- **Quellen** (nur lesen): frühere Baustellenberichte desselben Kunden an derselben Adresse – auch aus älteren
  Projekten, Text aus dem PDF –, Protokolle aus der Monteur-App (Gerät, Typ, Messwerte), Kommentare im
  HERO-Projektverlauf und das Notizfeld des Projekts. Die KI fasst zusammen und **erfindet nichts**.
- **`GET /v1/monteur/tage`**: jeder Termin hat neu `vorgeschichte` = **nur der Text** der Zusammenfassung
  (höchstens 4 Sätze) oder `null`.
- **`GET /v1/monteur/projekte/{id}`**: neu `vorgeschichte` = `{zusammenfassung, anlage: {geraet, hersteller, typ,
  baujahr}|null, letzte_wartung: "TT.MM.JJJJ"|null, letzte_arbeiten: [{datum, text}] (max. 3), offen: [], hinweise: [],
  quellen: {berichte, protokolle, kommentare, projekte}, stand}` oder `null`. Einzelne Felder in `anlage` können `null`
  sein.
- **Vorausberechnet** alle 30 Min. für die Termine von heute und morgen aller Monteure; neu gerechnet nur, wenn sich
  die Quellen ändern. Projektmappe ohne gespeicherte Vorgeschichte → jetzt `null`, Berechnung startet im Hintergrund,
  **der nächste Abruf** hat sie (meist nach 10–30 s). `tage` startet keine Berechnung.
- `null` = keine Unterlagen, noch nicht berechnet oder KI nicht erreichbar. Nie ein Fehlercode – der Rest von Tag und
  Projektmappe kommt wie bisher.

## Bitte bauen

**Wichtig vorweg: keine neuen Knöpfe, Menüs oder Reiter.** Kein Knopf „Vorgeschichte laden“, kein Aktualisieren-Knopf,
keine eigene Seite. Die Vorgeschichte erscheint von selbst – oder gar nicht.

### 1. Heute: Kurzfassung auf der Termin-Karte
- `vorgeschichte` (Text) **unter der Adresse** in 1–2 Zeilen, **grau**, kleinere Schrift, am Ende mit „…“ abgeschnitten
  (`maxLines = 2`, `TextOverflow.Ellipsis`).
- Kein Aufklappen auf der Karte – ganz lesen geht in der Projektmappe (Karte antippen wie bisher).
- `null` oder leer → nichts zeigen, kein Platzhalter, keine Leerzeile.

### 2. Projektmappe: Block „Vorgeschichte“ ganz oben
- **Ganz oben** in der Projektmappe, über Notizen/Unterlagen. Überschrift „Vorgeschichte“.
- Inhalt in dieser Reihenfolge, leere Teile weglassen:
  1. `zusammenfassung` als Fließtext.
  2. **Gerät:** eine Zeile aus `anlage`, vorhandene Teile mit „·“ getrennt, Hersteller und Typ zusammen:
     „Gas-Brennwerttherme · Vaillant ecoTEC plus VC 206/5-5 · Baujahr 2016“. `anlage: null` oder alles `null` → Zeile weglassen.
  3. **Letzte Wartung:** `letzte_wartung` (nur wenn nicht `null`).
  4. **Letzte Einsätze:** je Eintrag aus `letzte_arbeiten` eine Zeile „`datum` – `text`“.
  5. **Offen:** `offen` als Aufzählung (Punkte).
  6. **Hinweise:** `hinweise` **hervorgehoben** (Warnfarbe/Hintergrund wie die Hinweise auf den Karten, kleines
     Hinweis-Symbol), je Hinweis eine Zeile. Das ist das Wichtigste für vor Ort – nicht in grau.
- `quellen` und `stand` nicht anzeigen (höchstens klein und grau „aus 3 Berichten, 1 Protokoll“, wenn es ohne Aufwand
  geht – kein Muss).
- `vorgeschichte: null` → **Block ganz weglassen**, keine Überschrift, kein „Keine Vorgeschichte“.
- Unbekannte zusätzliche Felder (spätere Server-Version) ignorieren, nicht abstürzen.

### 3. Nachladen, wenn noch nichts da ist
- Ist `vorgeschichte` `null` und die Projektmappe ist noch offen, **einmal nach etwa 20 s** die Projektmappe still neu
  laden (`GET /v1/monteur/projekte/{id}`) und den Block einblenden, wenn er jetzt kommt.
- **Nur einmal** je Öffnen der Projektmappe; kommt wieder `null`, bleibt es dabei (es gibt dann meist keine Unterlagen).
- Kein Spinner, kein Knopf, keine Meldung. Beim Nachladen Scrollposition und offene Eingaben nicht verlieren – am
  einfachsten nur den Vorgeschichte-Block aus der neuen Antwort übernehmen.
- Projektmappe vorher geschlossen → Timer abbrechen. Offline → nichts tun.
- Die Termin-Karten in „Heute“ zeigen die Kurzfassung beim nächsten normalen Neuladen der Tagesansicht.

### Testen
Mit einem **Monteur-Zugang** und einem Termin für heute bei einem Kunden mit früheren Berichten (Testprojekt bzw.
bekannter Wartungskunde):
1. „Heute“ öffnen → unter der Adresse 1–2 graue Zeilen Vorgeschichte, abgeschnitten mit „…“.
2. Termin antippen → Block „Vorgeschichte“ ganz oben: Zusammenfassung, Gerät, letzte Wartung, letzte Einsätze, offene
   Punkte als Aufzählung, Hinweise hervorgehoben. Inhalte mit den alten Berichten in HERO vergleichen – nichts darf
   dazuerfunden sein.
3. Projekt ohne Vorausberechnung öffnen (z. B. Termin übermorgen) → kein Block; nach ~20 s erscheint er ohne Zutun.
4. Projekt ohne frühere Unterlagen → kein Block, auch nach 20 s nicht, keine Fehlermeldung; Termin-Karte ohne graue
   Zeilen.
