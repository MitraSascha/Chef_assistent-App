# Auftrag an den App-Agenten – Server 1.34.0: 👎 an Antworten

Anlass (User 04.11.2026, Review Chef-Assi): Schlechte Antworten sollen sich mit einem Tipp markieren lassen – damit
die KI es im Gespräch merkt und die Geschäftsführung sieht, wo der Assistent danebenliegt.

Vertrag: [`openapi.json`](openapi.json) 1.34.0 (sha256 `408a39ba…719ba6c`), Beschreibung in [`API.md` 4g](API.md).
Nur Ergänzungen; [`AUFTRAG-APP-1.32.md`](AUFTRAG-APP-1.32.md) (Fotos mit `gespraech_id`/angeheftetem Projekt,
Vorlieben-Seite) gilt weiter – falls noch offen, zuerst Teil A dort (behebt einen echten Fehler).

## Bitte bauen
- Unter bzw. neben jeder Antwort des Assistenten ein 👎 (neben „Vorlesen“). ID: `nachricht_id` aus der Chat-Antwort,
  im Verlauf `id`.
- Antippen → kleines Feld „Was war falsch? (optional)“ mit „Senden“ und „Ohne Grund“ →
  `POST /v1/nachrichten/{id}/bewertung` `{"wert": "schlecht", "grund": …}`. Danach Knopf rot/„an“, mit Grund die
  Zeile „👎 Rückmeldung zur Antwort: …“ wie andere Hinweise anzeigen.
- Nochmal antippen = zurücknehmen (`{"wert": null}`).
- Beim Laden des Verlaufs `bewertung` beachten (Knopf „an“).
- Kein 👍 nötig.

## Testen
Antwort 👎 mit Grund „falsches Gerät“ → Hinweis erscheint; nächste Frage im selben Gespräch → die KI geht darauf ein.
Als Geschäftsführer `GET /v1/bewertungen` → Eintrag mit Frage und Antwort.
