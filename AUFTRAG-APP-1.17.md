# Auftrag an den App-Agenten – Server 1.17.0: Lange Anfragen sicher

Anlass: Ein Angebot mit HERO-Übertragung kann viele Minuten dauern. Bisher brach die Verbindung nach gut 3 Minuten
ab: Die App zeigte einen Fehler, der Assistent arbeitete aber weiter. Schickte der Nutzer die Nachricht dann noch
einmal, lief die Arbeit doppelt. Vorgabe des Chefs: **Das muss sicher sein, egal wie lange die Anfrage dauert.**

Vertrag: [`openapi.json`](openapi.json) 1.17.0, Beschreibung in [`API.md` 1b](API.md). Es gibt nur Ergänzungen.
Die Aufträge 1.13 bis 1.16 gelten weiter.

## Bitte bauen
1. **Kein Lese-Timeout für Chat und Sprache**: einen eigenen OkHttp-Client für `POST /v1/chat` und
   `POST /v1/sprache` mit `readTimeout(0)` und `callTimeout(0)`. Der Connect-Timeout bleibt normal.
2. **`anfrage_id` je Nachricht**:
   - Vor dem Senden `UUID.randomUUID().toString()` erzeugen.
   - Mit dem Text lokal speichern (Room/DataStore, Status „gesendet“).
   - Mitschicken: im JSON bei `/v1/chat`, als Formularfeld `anfrage_id` bei `/v1/sprache`.
3. **Abholen statt Fehler**: Bei Netzwerkfehler (IOException), `502`, `503` oder `504` NICHT „Fehler“ zeigen.
   - Stattdessen „arbeitet noch …“ zeigen und alle 4 s `GET /v1/chat/{anfrage_id}` abfragen, bis `status` `fertig`
     oder `fehler` ist.
   - Die Abfrage läuft weiter, wenn der Nutzer die App wechselt. WorkManager mit Benachrichtigung „Antwort ist da“,
     wenn die App im Hintergrund ist.
   - Bei `404` (Nachricht kam nie an) dieselbe Nachricht **mit derselben `anfrage_id`** erneut senden. Das ist sicher,
     der Server führt sie höchstens einmal aus.
   - Erst `status: "fehler"` zeigt den Fehlertext mit „Nochmal versuchen“. Dieser Knopf erzeugt eine neue `anfrage_id`.
4. **App-Start**: Gespeicherte Nachrichten ohne Antwort holen (Punkt 3). In der Gesprächsliste bei `laeuft: true`
   ein Lade-Symbol zeigen.
5. **Abbruch-Antworten** („⚠️ Die Anfrage ist mittendrin abgebrochen …“) sind normale Antworten (`200`). Wie jede
   Antwort anzeigen, inklusive Knöpfen und Links. Nichts Besonderes nötig.

## Abnahme
- Den Assistenten ein großes Angebot bauen lassen, nach 10 Sekunden Flugmodus an, nach 1 Minute wieder aus: Die
  Antwort erscheint ohne erneutes Senden, und das Angebot existiert nur **einmal**.
- Während er arbeitet, die App beenden und neu öffnen: Lade-Symbol am Gespräch, danach erscheint die Antwort.
- Eine Sprachnachricht mit Funkloch: Die Antwort kommt trotzdem.
