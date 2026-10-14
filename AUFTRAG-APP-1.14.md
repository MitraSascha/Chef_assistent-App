# Auftrag an den App-Agenten – Server 1.14.0: Diagramme im Chat

Baut auf 1.13.0 auf (Suche, Sortierung, `links`). Der Vertrag ändert sich nur bei der Version und in der
Beschreibung von `antwort`. Die Diagramme stecken als Markdown-Codeblock in `antwort`. Das Format ist **so, wie du
es vorgeschlagen hast** (`art`, `titel`, `einheit`, `x`, `reihen`). Dazu kommt `gestapelt`, und die Grenzen sind
festgelegt.

Genaue Beschreibung: [`API.md` Abschnitt 1a](API.md#1a-diagramme-im-chat--neu-in-1140).

## Was der Server garantiert
- Ein ```` ```diagramm ````-Block enthält **genau eine Zeile gültiges JSON** im beschriebenen Format. Der Server prüft
  jeden Block und entfernt ungültige.
- Direkt darunter steht **immer eine Markdown-Tabelle** mit denselben Zahlen. Ältere App-Stände zeigen also
  mindestens die Tabelle.
- Die KI schreibt Diagramme ab 3 Werten, die man vergleichen soll. Verschiedene Einheiten kommen in getrennte
  Diagramme, es gibt also keine zweite y-Achse.
- `POST /v1/vorlesen` lässt den Block weg.

## Bitte bauen
1. **Natives Diagramm in Compose** für `balken` (nebeneinander/gestapelt), `linie` und `torte` (als Ring). Farben,
   Maße, Legende und Infobox wie in API.md 1a. Die Farbreihenfolge ist auf Farbsehschwäche geprüft, bitte nicht
   umsortieren. Hell/Dunkel über die angegebenen Paare.
   Antippen öffnet die Infobox. Ein Tipp auf den Titel öffnet das Diagramm im Vollbild, gern im Querformat.
   **Animiert, nicht 08/15:** Säulen wachsen gestaffelt aus der Nulllinie, Linien zeichnen sich, der Ring läuft
   auf und die Summe zählt hoch. Beim Antippen tritt der Rest zurück. Zeiten und Kurven stehen in API.md 1a
   unter „Bewegung“, in der Weboberfläche sind sie zum Vergleich schon zu sehen. Systemeinstellung „Animationen
   entfernen“ beachten.
2. **Tabellen** seitlich scrollbar mit Vollbild, Zahlen rechtsbündig. Das steht ohnehin auf deiner Liste.
   Die Tabelle direkt unter einem Diagramm darf eingeklappt starten („Tabelle zeigen“).
3. Beim **Vorlesen auf dem Handy** (TextToSpeech) ebenfalls den `diagramm`-Block weglassen.
4. Deine übrigen Punkte (Platzhalter-Blase beim Warten, Kopieren/Teilen, Uhrzeit, kleinere Überschriften) passen
   gut. Sie brauchen keinen Server.

## Vergleich
Die Weboberfläche zeichnet die Diagramme schon so (`assistent/web/diagramm.js`). Zum Vergleich auf dem Server
öffnen und im Chat z.B. fragen: „Zeig mir die offenen Rechnungen je Kunde als Diagramm.“
