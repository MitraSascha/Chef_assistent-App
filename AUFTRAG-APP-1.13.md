# Auftrag an den App-Agenten – Server 1.13.0: Suche, Sortierung, Datei-Knöpfe im Chat

Anlass: Chef und Büro finden Dateien in der App nicht schnell genug. Im HERO-Archiv muss man durch ~1.200
Projektordner scrollen, die nach Buchstaben sortiert sind. Fragt man im Chat nach einer Datei, gibt es keinen
direkten Weg dorthin. **Ziel: jede Datei in wenigen Sekunden.**

Vertrag: [`openapi.json`](openapi.json) (Version 1.13.0), Beschreibung in [`API.md`](API.md). Alle Änderungen sind
**nur Ergänzungen**. Der bisherige App-Stand läuft unverändert weiter.

## 1. Suchfeld in der Ordneransicht → `GET /v1/suche` (API.md 6a)
- Das Suchfeld steht oben und ist immer sichtbar. Gesucht wird beim Tippen ab 2 Zeichen, entprellt mit 250–300 ms;
  eine laufende Anfrage wird abgebrochen. Der Server antwortet in Millisekunden.
- Schalter „Nur in diesem Ordner“ (`ordner=<aktueller Ordner>`). Standard ist die ganze Ablage.
- Treffer: Symbol, Name fett, Untertitel aus `projekt` („SAN-1166 · Kunde · Straße“), sonst `ordner`.
  Eine Datei öffnet sich beim Antippen, ein Ordner wird geöffnet. Bei Dateien gibt es zusätzlich „Im Ordner zeigen“.
- Hinweise für „zu viele Treffer“ (`abgeschnitten`) und „nichts gefunden“ wie in API.md 6a.

## 2. Ordneransicht verbessern → `GET /v1/ordner` (API.md 6)
- `projekt` als Untertitel unter HERO-Projektordnern zeigen (Baustelle · Status).
- Sortier-Menü: Name / Neueste Projekte zuerst (`name_ab`) / Zuletzt geändert (`geaendert`). Für den Ordner
  `HERO-Archiv` ist die Vorgabe `name_ab`. Die Wahl je Ordner merken.
- Seitenweise laden: `anzahl=100`, beim Scrollen ans Ende `ab += 100`, solange `abgeschnitten` gilt.
- Der leere Ordner `Projekte` erscheint auf Serverseite nicht mehr. Dafür ist in der App nichts zu tun.

## 3. Datei-Knöpfe im Chat → Feld `links` (API.md 1)
- Enthält eine Antwort `links`, je Eintrag eine Karte zeigen. Diese Karten ersetzen die `dateien`-Chips.
  - Datei: „Öffnen“ und „Im Ordner zeigen“, optional „Teilen“.
  - Ordner: „Ordner öffnen“.
- Auch im Verlauf (`GET /v1/gespraeche/{id}`) zeigen. Dort steht `links` ebenfalls.
- „Im Ordner zeigen“ heißt `GET /v1/ordner?pfad=<Dateipfad>`. Der Server liefert dann den Ordner, die App markiert
  die Datei und scrollt zu ihr. Bei Seitenweise-Laden so lange nachladen, bis die Datei in der Liste ist.

## Abnahme
- Suche „1270“ → SAN-1270 steht ganz oben. „Grimmstr 7“ → das Boese-Projekt. „Dreißig Rechnung“ → Rechnungen von
  Immobilien Dreißig.
- Im Chat „Schick mir die letzte Rechnung von Boese“ → eine Karte mit „Öffnen“ und „Im Ordner zeigen“; beide
  Knöpfe funktionieren.
- Das HERO-Archiv zeigt die neuesten Projekte oben und Untertitel mit Adresse.
