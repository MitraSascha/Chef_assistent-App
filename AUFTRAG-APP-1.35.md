# Auftrag an den App-Agenten – Server 1.35.0: Fotos/Videos ohne Empfang (Ausgang + Hochladen in Stücken)

Anlass (Chef 11.11.2026): „Wenn ich ein Foto oder Video mache und abschicke und keinen Empfang habe, kann ich nichts
mehr machen, bis das Fenster weg ist. Bei einem 15-Min-Video dauert das ewig. Im Keller muss ich jedes Mal raus.“

Vertrag: [`openapi.json`](openapi.json) 1.35.0 (sha256 `15a77c28…801d29`), Beschreibung in [`API.md` 4h](API.md).
Nur Ergänzungen – alle bisherigen Endpunkte bleiben. Gilt für **beide Apps** (Chef-Assi und Monteur-App).

## Ziel
Die App ist nach dem Tippen auf „Senden“ **sofort wieder bedienbar**. Hochgeladen wird im Hintergrund, auch wenn die
App zu ist, und nach einem Abbruch geht es an der letzten Stelle weiter – nie wieder bei 0 %.

## Bitte bauen
1. **Ausgang (Warteschlange auf dem Gerät)**
   - Foto/Video/Dokument + Formularfelder + eine neue `upload_id` (UUID ohne Bindestriche) sofort lokal speichern
     (App-Speicher, nicht nur Cache), Fenster schließen, weiterarbeiten lassen.
   - Oben bzw. an der Nachricht ein kleiner Hinweis: „1 Video wartet auf Empfang“ / „wird gesendet 42 %“ / ✓.
     Antippen zeigt den Ausgang (Abbrechen/Löschen möglich).
2. **Senden im Hintergrund** (Android WorkManager, Constraint „Netz verbunden“, als Expedited/Foreground-Work mit
   Benachrichtigung bei großen Dateien, Backoff exponentiell):
   - `POST /v1/uploads` (Monteur-App: `/v1/monteur/uploads`) mit `upload_id`, `ziel`, `dateiname`, `groesse`, `felder`.
     Die Antwort sagt, wie viel schon `empfangen` ist – **immer dort weitermachen** (auch nach App-Neustart).
   - Stücke mit `PUT …/{upload_id}?offset=<empfangen>` als `application/octet-stream`, 2–4 MB im Mobilnetz,
     bis `teil_max` (8 MB) im WLAN. Bei **409** den Header `X-Empfangen` nehmen und ab dort weiter.
   - Wenn `empfangen == groesse`: `POST …/{upload_id}/abschliessen`. `status: "fertig"` → `ergebnis` ist dieselbe
     Antwort wie früher vom direkten Endpunkt (Foto/Dokument/Video bzw. Monteur `Hochgeladen`) – wie bisher anzeigen.
     `status: "fehler"` (z. B. kein Bild) → dem Nutzer zeigen, nicht wiederholen. 5xx/kein Netz → später erneut
     `abschliessen` (liefert nie doppelt).
3. **Videos vor dem Senden verkleinern**, wenn größer als 1080p (Media3 Transformer, H.264/HEVC, ~6–8 Mbit/s):
   ein 15-Min-Video wird so etwa ein Drittel. Ton unverändert lassen (wird abgeschrieben).
4. **Text zur Datei wartet auf die Datei**: Tippt/spricht der Nutzer zum Foto etwas („schau dir das Typenschild an“),
   geht diese Chat-Nachricht erst los, wenn der Upload `fertig` ist (Foto zuerst, dann `POST /v1/chat` wie bisher mit
   `gespraech_id`). Im Chat sofort als „wartet auf Foto…“ anzeigen.
5. **Monteur-App**: Der bestehende Ausgangskorb (`anfrage_id`) bleibt; große Dateien (alles über 5 MB, immer Videos)
   gehen über `/v1/monteur/uploads` mit `ziel: "projekt"` und `felder: {pid, text, anfrage_id}`.
6. Kleine Fotos dürfen weiter direkt über `/v1/fotos` gehen – aber auch dann **über den Ausgang** (nie blockierend).

## Testen
- Flugmodus an → Video (5 Min.) aufnehmen und senden → App sofort bedienbar, Hinweis „wartet auf Empfang“.
- Flugmodus aus → Upload startet von selbst; bei ~50 % wieder Flugmodus → danach geht es bei ~50 % weiter
  (`GET /v1/uploads/{id}` zeigt `empfangen`).
- App beenden während des Uploads → nach dem Öffnen geht es weiter, nichts doppelt in der Notiz.
- Foto + gesprochene Frage ohne Netz → nach Empfang kommt erst das Foto, dann die Antwort der KI dazu.
