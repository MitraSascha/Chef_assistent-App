# Auftrag an den App-Agenten – Server 1.16.0: Kalender mit HERO-Terminen

Anlass: Der Chef soll „immer eine Übersicht in der Tasche haben“. Jeder App-Nutzer sieht seine HERO-Termine
(Plantafel) und die eigenen Termine des Assistenten und kann Kollegen und Monteure dazuschalten.

Vertrag: [`openapi.json`](openapi.json) 1.16.0, Beschreibung in [`API.md` 5a](API.md). Es gibt nur
Ergänzungen. Die Aufträge 1.13 bis 1.15 gelten weiter.

**Wichtig:** HERO-Termine sind **nur lesbar** (`nur_lesen: true`). Die HERO-Schnittstelle kann Termine weder anlegen
noch verschieben. Also keinen Knopf „Verschieben“ an HERO-Terminen anbieten. Eigene Termine werden wie bisher im
Chat angelegt und geändert.

## Bitte bauen
1. **Neuer Bereich „Kalender“** (Navigation, z. B. Tab mit 📅):
   - `GET /v1/personen` einmal laden (5 Minuten zwischenspeichern).
   - Dann `GET /v1/kalender?von&bis&personen`, Wochenansicht als Tagesliste, beim Öffnen zu „heute“ scrollen.
   - Kopf mit ‹ Heute › und Tag/Woche. Wischen links/rechts blättert.
2. **Personen-Chips**: Ich, die anderen App-Nutzer mit Vornamen, „Monteure ▾“ (klappt auf), „Alle“.
   - Mehrfachauswahl, die Auswahl wird gemerkt.
   - Farbe je gewählter Person in fester Reihenfolge, dieselbe Palette wie die Diagramme (API.md 1a): als Punkt im
     Chip und als linker Rand der Termin-Karte.
3. **Termin-Karte** wie in API.md 5a.
   - Antippen klappt auf: Adresse (antippbar, öffnet die Karten-App), Personen, Notiz und „Projektordner“
     (Ordneransicht mit `projekt.pfad`).
   - Hinweis „Aus HERO – ändern nur in HERO“.
   - Mehrtägige Termine mit Datum: „5.10. 02:01 – 12.10. 01:59“.
4. **Ziehen zum Aktualisieren**. Ist `spiegel` false, einen Hinweis zeigen: „HERO-Termine nicht verfügbar, nur
   eigene“.
5. Optional: Widget „Heute“ mit den nächsten 3 Terminen von `ich`.

## Abnahme
- Patrick öffnet den Kalender: Seine HERO-Termine dieser Woche stehen da, heute ist oben.
- „Sascha“ und „Julian“ dazuwählen: Deren Termine erscheinen in eigenen Farben, die Punkte in den Chips passen.
- Einen Termin mit Projekt antippen: „Projektordner“ öffnet den HERO-Archiv-Ordner.
- Im Chat „Wo ist Julian morgen?“ fragen: Der Assistent antwortet aus denselben Daten (Werkzeug `kalender_anzeigen`).
