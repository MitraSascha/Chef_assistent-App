# Auftrag an den App-Agenten – Server 1.28.0: Fotos betiteln, Fragen zu vorhandenen Bildern

Anlass (User 16.10.2026): (a) deine Rückfrage „kann man auch zu Bildern Fragen stellen?“ – ja, zu Fotos aus der
Projektmappe und aus der Galerie; (b) „der Monteur soll Fotos, die er macht, betiteln können und/oder eine Frage dazu
stellen“. Alles **ohne neue sichtbare Knöpfe** (Grundsatz bleibt). Enthält 1.27.0 (es gab keinen eigenen Auftrag).

Vertrag: [`openapi.json`](openapi.json) 1.28.0 (sha256 `0fd1ba5d…5db0371`), Beschreibung in [`API.md` 5d](API.md)
(Felder `foto_ids`, Kartenart `foto`, `Karte.foto`, `Passt.titel`). Nur Ergänzungen; alle Aufträge 1.22–1.26 gelten weiter.

## Server (fertig)
- `POST /v1/monteur/eingang` nimmt zusätzlich **`foto_ids`** (Feld mehrfach): `uuid` aus `fotos[]` der Projektmappe.
  Der Server holt die Bilder selbst aus dem HERO-Archiv – kein Herunterladen und Wiederhochladen übers Handy (gut bei
  schlechtem Netz). Zusammen mit `fotos` höchstens 5. Ohne `termin_id`/`projekt_nr` geht das **Projekt des Fotos**
  automatisch mit. Kein Bild → `415`, kein Zugriff/unbekannt → `404`.

- **Neue Kartenart `foto`** (1.28.0): Foto(s) + „Zählerstand Wasser“ / „Vorher Bad“ → die KI macht einen sauberen
  Titel (`foto.titel`, auch aus anderer Sprache). „Passt“ (optional `{"titel": "…"}`) legt die Fotos mit diesem Titel
  ins HERO-Projekt („2026-10-16 14-05 Igor Zählerstand Wasser.jpg“, in wenigen Minuten in HERO);
  `foto.abgelegt` = Dateinamen. Nur Text ohne Foto bei dieser Absicht → `422` „Bitte ein Foto dazu machen.“
- **Frage mit Foto** (1.28.0): Die Antwort-Karte (`art: frage`) hat zusätzlich `foto: {titel, abgelegt: []}`, wenn
  ein Projekt dazu gehört (gewählt oder laut Plan). „Passt“ auf der Frage-Karte legt die Fotos mit Titel ins Projekt;
  ohne Fotos bleibt es bei `409` (nichts zu bestätigen).

## Bitte bauen
1. **Foto aus der Projektmappe = Kontext, kein Knopf.** Ist ein Projektfoto im Vollbild offen und der Monteur drückt
   den (ohnehin vorhandenen) Mikrofon- oder Kamera-Knopf, geht dieses Foto automatisch als `foto_ids` mit – genau wie
   der Termin automatisch mitgeht. Oben in der Eingabe als Vorschaubild zeigen („Frage zu diesem Foto“), mit kleinem
   ✕ zum Entfernen. Weitere Fotos per Kamera dazu wie bisher (zusammen max. 5).
2. **Galerie: langer Druck auf den Kamera-Knopf** öffnet die Android-Fotoauswahl (Photo Picker,
   `PickMultipleVisualMedia`, max. 5, keine Speicher-Berechtigung). Ausgewählte Bilder gehen als normale `fotos` mit.
   Beim ersten Öffnen der Eingabe einmalig einen kleinen Hinweis „Lange drücken: aus der Galerie“ zeigen, danach nie
   wieder. Kein zusätzlicher sichtbarer Knopf.
3. **Karte `foto`**: Vorschaubilder, darunter der Titel als einzeiliges, direkt bearbeitbares Feld, dann „Passt“ →
   `POST …/passt {"titel": "<Feld>"}`. Danach „Liegt im Projekt“ + Dateinamen klein.
4. **Frage-Karte mit `foto`**: unter der Antwort klein „Foto ins Projekt legen als: <Titel ✎>“ mit dem normalen
   „Passt“-Knopf der Karte (statt „Fertig“ heißt er dann „Passt – Foto ablegen“, „Fertig“ bleibt daneben als
   Schließen ohne Ablegen). Nach dem Ablegen: „Liegt im Projekt“.
5. Nur Foto ohne Text: wie bisher entscheidet die KI (Frage, Meldung, Bestellung, Protokoll …). In der Eingabe unter
   dem Vorschaubild steht schon „Dazu sprechen oder tippen – oder einfach senden“ – so lassen.

## Testen
- Projektmappe SAN-1243 (oder UNB-795) → Foto im Vollbild → Mikrofon: „Was ist das?“ → Antwort-Karte, Projekt stimmt.
- Langer Druck auf Kamera → zwei Bilder aus der Galerie → „Welches Ersatzteil ist das?“ → Antwort mit Artikeln.
