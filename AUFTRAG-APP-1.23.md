# Auftrag an den App-Agenten – Server 1.23.0: Monteur-App, ein Eingang für alles

Anlass (User 16.10.2026): **Keine neuen Knöpfe.** Monteure haben es immer eilig – alles, wo sie erst überlegen müssen,
welcher Knopf der richtige ist, wird nicht benutzt. Deshalb gibt es **einen großen Mikrofon-Knopf und die Kamera**.
Der Monteur sagt, tippt oder fotografiert einfach; die KI erkennt aus Inhalt, Uhrzeit und Tagesplan, was er will
(Bericht, Frage, Bestellung, Meldung ans Büro, Zeiten, Urlaub), und zeigt **eine Karte**. Bestätigt wird immer mit
**„Passt“**. Die App behält ihre drei Reiter (Heute, Zeiten, Urlaub) und den schwebenden Mikrofon-/Kamera-Knopf.

Vertrag: [`openapi.json`](openapi.json) 1.23.0 (sha256 `7f7d8630…a77eda0`), Beschreibung in [`API.md` 5d](API.md)
(Zeitarten in 5c). Es gibt nur Ergänzungen; alles aus [`AUFTRAG-APP-1.22.md`](AUFTRAG-APP-1.22.md) gilt weiter.
Die Endpunkte aus 1.22 bleiben – der Mikrofon-Knopf geht aber ab jetzt **immer** an den Eingang; die einzelnen
Endpunkte bleiben für Änderungen von Hand („+ Material“, Zeitenliste, Urlaubsliste). Die Büro-App ist nicht betroffen.

## Server (fertig)
- **`POST /v1/monteur/eingang`** (multipart: `audio` und/oder `text`, 0–5 `fotos`, optional `termin_id`,
  `projekt_nr`, `datum`, `bericht_id`, `bezug_id`, `anfrage_id`) → **eine Karte** mit `art`:
  - `bericht` – Berichtsentwurf wie bisher (Fotos hängen am Bericht).
  - `frage` – Foto vom Typenschild / Fehlercode + Frage → Antwort aus unserer **Anleitungsbibliothek**, dem
    **Internet** (Hersteller, Datenblätter, **YouTube**-Links) und dem **Katalog** (Artikelnummern, keine Preise).
    Nichts zu bestätigen. Nachfragen mit `bezug_id`.
  - `bestellung` – „Mir fehlen noch 3 Eckventile“ → Materialliste; bei „Passt“ **fertiger Bestellschein**
    (BS-Nummer) im HERO-Projekt – Positionen im Büro per Klick ins Angebot/die Rechnung, bestellt wird per IDS –
    + Kommentar im Projektverlauf; Fotos dazu gehen ins Projekt.
  - `meldung` – Kunde nicht angetroffen, Verspätung, Mangel/Schaden, Nachtrag/Zusatzwunsch, Sonstiges → bei „Passt“
    **Kommentar im HERO-Projektverlauf** („Kommentar von …“, wie „Waren vor Ort, Frau X hat abgelehnt“), Fotos ins
    HERO-Projekt.
  - `zeiten` – Vorschläge, bei „Passt“ in HERO gebucht. `urlaub` – Vorschlag, bei „Passt“ beantragt.
- **`POST /v1/monteur/eingang/{id}/passt`** – einziger Bestätigungsweg für alle Arten, wiederholbar (nie doppelt).
- `DELETE /v1/monteur/eingang/{id}` verwirft, `GET /v1/monteur/eingang` = Karten der letzten 7 Tage.
- `GET /v1/monteur/anleitung?pfad=` → Herstelleranleitung als PDF.
- Projektmappe hat neu **`verlauf`** = Kommentare im HERO-Projektverlauf.
- Neue Zeitart **`lager`** (Laden/Lager/Firma, Arbeitszeit **ohne** Projekt, z. B. 07:00–07:45).
- Ohne Termin/Projekt erkennt der Server selbst, wo der Monteur laut Plan gerade ist.

## Bitte bauen

**Wichtig vorweg: keine neuen Knöpfe, Menüs, Auswahllisten oder Reiter.** Kein „Was möchtest du tun?“-Dialog, keine
Auswahl „Frage / Bestellung / Meldung“. Der Monteur drückt das Mikrofon (oder die Kamera) und bekommt eine Karte.
Alles Neue steckt in den Karten, die nach dem Sprechen erscheinen.

### 1. Mikrofon- und Kamera-Knopf (auf jedem Reiter gleich)
- Der schwebende Knopf ist auf **Heute, Zeiten, Urlaub und in der Projektmappe** derselbe und geht **immer** an
  `POST /v1/monteur/eingang` (nicht mehr an `…/berichte` bzw. `…/verstehen`). Großes Mikrofon, daneben kleiner
  Kamera-Knopf. Aufnahme gedrückt halten oder antippen-start/antippen-stopp wie bisher; Textfeld als Alternative.
- **Kamera:** Foto(s) aufnehmen (bis 5), danach optional sprechen oder tippen; ohne Sprache reicht „Senden“ – nur
  ein Foto ist für den Server eine Frage („Was ist das?“). Fotos nicht selbst verkleinern.
- Kontext automatisch mitschicken, nie fragen:
  - `termin_id`, wenn der Monteur gerade im Termindetail/der Projektmappe ist;
  - `bericht_id`, wenn gerade ein Berichtsentwurf (Bericht-Karte) offen ist → Ergänzungen landen im selben Entwurf;
  - `bezug_id` = Karten-ID, wenn gerade eine Antwortkarte (`frage`) offen ist → Nachfrage;
  - `datum`, wenn auf „Heute“/„Zeiten“ ein anderer Tag gewählt ist;
  - `anfrage_id` = neue UUID ohne Bindestriche, im Ausgangskorb merken (wird die Karten-ID).
- Während der Server arbeitet (meist 5–30 s, eine Frage mit Internetsuche bis ~60 s): „KI denkt mit …“ mit
  Fortschrittsanzeige; Monteur darf die App verlassen – Ergebnis kommt aus dem Ausgangskorb.
- Kleiner Hinweis unter dem Knopf: „Sag einfach, was ist – Bericht, Frage, Material, Info ans Büro, Zeiten, Urlaub.“

### 2. Eine Karte je Art
Eine gemeinsame Karten-Hülle: oben Art-Symbol + kurze Überschrift („Bericht“, „Antwort“, „Material bestellen“,
„Info ans Büro“, „Zeiten“, „Urlaub“), darunter **Projekt/Kunde** aus `projekt` (deutlich, damit der Monteur merkt,
wenn der Server das falsche Projekt erkannt hat), der erkannte `text` klein und grau, `rueckfrage` (wenn gesetzt)
als gold markierter Hinweis. Unten immer dieselben zwei Knöpfe (siehe 3).

- **bericht** → die bestehende Bestätigungskarte aus 1.22 (`karte.bericht`), unverändert: Text, Stunden, Material
  mit Steppern/Vorschlägen/„+ Material“, Zeiten, Unterschrift. Änderungen von Hand weiter per
  `PATCH /v1/monteur/berichte/{bericht.id}`; Ergänzen per Sprache = einfach wieder das Mikrofon (mit `bericht_id`).
  Die Antwort ist dann eine neue Karte mit derselben `bericht.id` – die alte Karte ersetzen, nicht stapeln.
- **frage** → Antwortkarte (`karte.antwort`):
  - `text` gut lesbar (nummerierte Schritte, Absätze), groß genug für die Baustelle.
  - `quellen` als Link-Zeilen mit Titel; antippen öffnet **extern** (Browser bzw. **YouTube-App** bei youtube.com/
    youtu.be). YouTube-Links mit Play-Symbol hervorheben.
  - `anleitungen` als Zeilen „Titel · S. <seite>“; antippen öffnet die PDF im **eingebauten Viewer** über
    `GET /v1/monteur/anleitung?pfad=<pfad>` (mit Token) **direkt auf der Seite `seite`**.
  - `artikel` als Chips „Name · Nr.“ (keine Preise); antippen = Artikelnummer kopieren (Toast „Kopiert“).
  - Kein „Passt“ nötig – die Karte ist sofort `erledigt`. Statt „Passt“ steht hier „Fertig“ (schließt nur die Karte).
    Spricht der Monteur weiter, während die Karte offen ist → Nachfrage mit `bezug_id`; die neue Antwort erscheint
    darunter wie ein kurzer Verlauf.
- **bestellung** → Materialkarte (`karte.bestellung`): dieselbe Materialliste wie im Bericht – Menge per Stepper,
  Name des gewählten Artikels bzw. Diktat-Text, bei `rueckfrage` gold markiert mit den `vorschlaege` als Chips
  (oder „Frei lassen“), am Ende **„+ Material“** (Suche `GET /v1/monteur/artikel?q=…` wie im Bericht). `hinweis`
  (z. B. „bis morgen früh“) darunter. Keine Preise. Bei „Passt“ die **vollständige** Liste als `material` mitschicken
  (`[{index, menge, text?, einheit?, artikel_nr?}]`, wie `PATCH` beim Bericht), wenn etwas geändert wurde.
- **meldung** → Karte „Info ans Büro“ (`karte.meldung`): Art als Chip (Kunde nicht angetroffen, Verspätung,
  Mangel/Schaden, Nachtrag, Sonstiges – nur Anzeige), `text` als **bearbeitbares** Textfeld (vorausgefüllt, so geht er
  in HERO), darunter die Fotos als Vorschaubilder (die lokal aufgenommenen; `karte.fotos` = Anzahl). Kleiner Satz:
  „Kommt in den HERO-Projektverlauf von <Projekt>.“ Bei „Passt“ geänderten Text als `text` mitschicken.
- **zeiten** → Liste der Abschnitte (`karte.zeiten`): je Zeile Art-Symbol, von–bis, Art-Name. Antippen = Uhrzeit/Art
  ändern (kleiner Zeitwähler, Art aus den fünf Arten unten). Geänderte Liste als `zeiten` mitschicken.
- **urlaub** → Zeitraum-Karte (`karte.abwesenheit`): Art, von–bis (Datumsbereich, antippen = ändern), halbe Tage,
  Kommentar. Geändert → als `abwesenheit` mitschicken.

### 3. „Passt“ und „Verwerfen“ – sonst nichts
- Ein großer Knopf **„Passt“** → `POST /v1/monteur/eingang/{id}/passt` mit nur den geänderten Feldern
  (bei `bericht`: `unterschrift_png`, `unterschrift_name`, `zeiten_buchen`; ohne Unterschrift wie bisher fragen
  „Ohne Unterschrift abschließen?“).
- Ein kleiner Text-Knopf **„Verwerfen“** → `DELETE /v1/monteur/eingang/{id}` (bei Bericht ist der Entwurf dann weg –
  kurz nachfragen). Nach Erfolg Karte schließen.
- Nach „Passt“: grüner Haken + was passiert ist, aus `ergebnis`: Bericht → `hero_nr`; Bestellung → „Bestellschein
  <hero_nr> liegt im Projekt, das Büro bestellt“; Meldung → „Steht im Projektverlauf“; Zeiten → „<gebucht> Zeiten
  gebucht“; Urlaub → „Beantragt“. **`ergebnis.hinweise` immer als Liste anzeigen, wenn nicht leer** (z. B. „Foto 2
  nicht ins Projekt gelegt …“).
- `409` mit Text (z. B. „Erst die Rückfragen klären: …“) → Karte bleibt offen, Text zeigen, betroffene Zeile markieren.
  `422` → Text zeigen. `502`/Netz → siehe 4.

### 4. Ausgangskorb
- Senden an den Eingang **und** „Passt“ laufen über den Ausgangskorb (WorkManager, wie 1.22). Gleiche
  `anfrage_id` = gleiche Karte: der Server rechnet nicht doppelt und legt nichts doppelt an. „Passt“ ist ebenfalls
  wiederholbar (erledigte Karte kommt unverändert zurück).
- Fotos und Aufnahme bis zum Erfolg lokal behalten; danach löschen.
- Beim App-Start `GET /v1/monteur/eingang`: Karten mit `status: "offen"` als kleine Leiste „Noch zu bestätigen (2)“
  oben auf „Heute“ zeigen; antippen = Karte öffnen. Das ist kein neuer Knopf, sondern eine Erinnerung.

### 5. Projektmappe: „Verlauf“
- In der Projektmappe (Termindetail) neuer Abschnitt **„Verlauf“** unter den Notizen: `verlauf[]` aus
  `GET /v1/monteur/projekte/{id}` – je Eintrag Datum/Uhrzeit, `titel` (oft leer – dann ist die erste Zeile von `text`
  die Überschrift, fett), `text`. Neueste zuerst, die ersten 3 sichtbar, „Mehr“ klappt auf. Leer → Abschnitt
  ausblenden.
- Eigene Meldungen/Materialanforderungen erscheinen dort erst nach dem nächsten HERO-Abgleich (Minuten) – nach
  „Passt“ den Eintrag lokal vorläufig oben anzeigen (grau, „wird übertragen“), bis er im `verlauf` auftaucht.

### 6. Zeiten: Art `lager`
- Neue Art **`lager`** überall anzeigen (Zeitenliste, Zeiten-Karte, „+“-Dialog): Symbol Kiste/Lager, Name „Lager/Laden“,
  **ohne** Projekt (Projektfeld ausblenden).
- Arten und Bedeutung (als kleine Hilfe im „+“-Dialog, ein Satz je Art):
  - `lager` – Laden, Lager, Firma (z. B. 07:00–07:45) – Arbeitszeit ohne Projekt
  - `fahrt` – Fahrt zum Kunden bzw. zwischen Kunden – Arbeitszeit, wird nicht berechnet (Anfahrtspauschale)
  - `arbeit` – beim Kunden – mit Projekt, wird berechnet
  - `material` – während des Einsatzes zum Händler – mit Projekt, wird berechnet
  - `pause`
  - Fahrt von zu Hause zur ersten Baustelle und Heimfahrt: **nicht** eintragen.
- `buero`, `vor_ort`, `sonstige` können aus HERO kommen – anzeigen, aber im „+“-Dialog nicht anbieten.

### Navigation (unverändert)
Unten drei Reiter: **Heute** · **Zeiten** · **Urlaub**. Darüber schwebend Mikrofon + Kamera – auf allen Reitern
derselbe Eingang. Der Knopf „Bericht“ im Termindetail aus 1.22 öffnet nur noch die Aufnahme (gleicher Eingang mit
`termin_id`); einen eigenen Knopf für Frage, Bestellung oder Meldung gibt es **nicht**.

### Testen
Mit einem Büro-Zugang mit HERO-Zuordnung (z. B. sascha), Projekt **`UNB-795`** (Testprojekt) – dort per
`projekt_nr`/Termin testen. **Achtung:** „Passt“ legt echte Einträge an (fertiger Bestellschein, Kommentar im
Projektverlauf, Zeiten, Urlaubsantrag). Sätze zum Ausprobieren: „WC getauscht, 2 Stunden, zwei Eckventile“ ·
Foto eines Typenschilds ohne Text · „Was heißt F.28 bei der Vaillant?“ und danach „und wo sitzt der Sensor?“ ·
„Mir fehlen noch drei Eckventile halb Zoll“ · „Kunde nicht da, 9:10 geklingelt“ · „Komme 20 Minuten später“ ·
„Heute 7 bis Viertel vor 8 geladen, dann zum Kunden“ · „Nächste Woche Donnerstag und Freitag frei“.
