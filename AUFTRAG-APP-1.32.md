# Auftrag an den App-Agenten – Server 1.32.0: Vorlieben + Fotos im Chat richtig zuordnen

Anlass (User 04.11.2026, Review Chef-Assi): „Ich muss zu oft erklären.“ Beim Typenschild-Chat kam das Foto bei der KI
nicht an, sie fragte nach statt nachzusehen. Server-Seite ist erledigt (1.30.2 + 1.32.0), die Chef-Assi-App muss
zwei Dinge nachziehen.

Vertrag: [`openapi.json`](openapi.json) 1.32.0 (sha256 `d94a9c86…9624064`), Beschreibung in [`API.md` 4, 4a und 4f](API.md).
Nur Ergänzungen; alles bis 1.31 gilt weiter.

## Teil A – Fotos und Dokumente im Chat: immer `gespraech_id` und angeheftetes Projekt mitschicken (Fehlerbehebung)

Heute schickt die App bei `POST /v1/fotos` / `POST /v1/dokumente` aus dem Chat **weder `gespraech_id` noch `notiz`**.
Folge: Das Foto landet in irgendeiner Notiz von heute, die KI sieht es erst über Umwege, und beim angehefteten Projekt
landet es nicht in der Projektmappe (→ nicht in HERO).

Bitte so:
- **Im offenen Gespräch:** `gespraech_id` des Gesprächs mitschicken (API.md 4a). Dann steht das Foto im Verlauf und
  die KI bekommt es beim nächsten Zug als Bild.
- **Neues Gespräch (noch keine ID):** ohne `gespraech_id` hochladen ist seit 1.30.2 in Ordnung – der Server hält es
  fest und gibt es dem **nächsten** Chat-Zug dieses Benutzers mit. Also: Foto hochladen, dann `POST /v1/chat` wie
  gewohnt. Nicht auf die ID warten, nichts zwischenspeichern.
- **Projekt angeheftet:** zusätzlich `notiz` = `pfad` der HERO-Mappe (aus `POST /v1/hero-mappe`; Nummer wie
  `SAN-660` oder Pfad im HERO-Archiv gehen auch – API.md 4a, Teil „HERO-Mappe“ → „Anheften wie bei Notizordnern“), damit das Foto in
  die Projektmappe und von dort nach HERO geht.
- Gilt für Kamera, Galerie (mehrere) und Teilen-Ziel gleichermaßen.

Testen: Neues Gespräch, Typenschild fotografieren, dazu sagen „brauche ein Austauschgerät“ → die KI liest das
Typenschild ab, ohne zu fragen „welches Gerät?“. Mit angeheftetem Projekt: Foto taucht in `GET /v1/hero-mappe` auf.

## Teil B – Vorlieben (neu)

Der Assistent merkt sich dauerhafte Vorlieben – persönlich oder für die ganze Firma (API.md 4f). Im Chat braucht die
App nichts Neues (die KI fragt „Soll ich mir das merken?“, der Nutzer sagt Ja). Bitte eine **Seite „Vorlieben“** in
den Einstellungen/im Menü, wie in der Weboberfläche:

- `GET /v1/vorlieben` → zwei Listen: „Nur für mich“ (`eigene`) und „Für die ganze Firma“ (`firma`).
- Jede Zeile: Text, Stift (Ändern → `PATCH /v1/vorlieben/{id}`), Mülleimer (Vergessen mit Rückfrage →
  `DELETE /v1/vorlieben/{id}`). Bei `firma` nur, wenn `firma_aendern` = true.
- Unten Eingabefeld + Knopf „Merken“ (`POST /v1/vorlieben`); Schalter „für die ganze Firma“ nur bei `firma_aendern`.
- Leere Liste: „Noch nichts gemerkt.“ Kurzer Erklärtext oben: „Was sich der Assistent dauerhaft merkt – gilt in
  jedem Gespräch. Im Chat genügt „merk dir …“.“
- Fehlertexte (`detail`) unverändert anzeigen (403 = nur Geschäftsführung, 422 = zu lang).

Testen: Im Chat „merk dir: Zusammenfassungen immer als PDF“ → erscheint in der Liste; in neuem Gespräch „mach mir eine
Zusammenfassung zu …“ → PDF ohne Nachfrage. Löschen in der App → KI hält sich im nächsten Gespräch nicht mehr daran.
