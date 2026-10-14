# Auftrag an den App-Agenten – Server 1.15.0: Angebote verständlich machen

Anlass: In der Angebotsansicht steht oben eine Liste, mit der man „ehrlicherweise nie viel anfangen kann“. Interne
Vermerke wie „Paket … ist ein Entwurf – von Mitra noch nicht geprüft“ stehen dort, die Fragen haben keinen Bezug zur
Position, und auf dem Handy muss man ständig zwischen Frage und Position hoch- und runterscrollen.

Vertrag: [`openapi.json`](openapi.json) 1.15.0, Beschreibung in [`API.md` 4e „Entscheidungen“](API.md). Es gibt nur
Ergänzungen. 1.13 (Suche) und 1.14 (Diagramme) gelten weiter.

## Bitte bauen
1. **Angebotsansicht: oben „Entscheidungen“ aus `punkte`.** Die alten Listen `fragen`, `markiert` und `hinweise`
   nicht mehr anzeigen.
   - Je Punkt eine Karte: farbige Kopfzeile (Frage/Bitte prüfen = rot, Alternative = gelb, Hinweis = grau) mit
     `ort`, darunter `text` fett.
   - Darunter die **Positions-Kacheln** (Nr. klein, „Menge × Name“, Preis rechts). Ein Tipp darauf scrollt zur
     Position und hebt sie kurz hervor.
   - Dann die `optionen` als Knöpfe untereinander, dann „Eigene Antwort …“ + Senden (nur bei `frei`) und „Passt so“
     (bei `hinweis`: „Ausblenden“).
   - Antworten per `POST /v1/angebote/{id}/antworten`; die Antwort des Servers ersetzt die Ansicht.
   - Bei mehr als 5 Punkten „weitere … anzeigen“.
2. **`beantwortet`** eingeklappt unter den Karten zeigen: „2 beantwortet – wird bei „mach weiter“ eingearbeitet“.
   Dazu ein Knopf **„Antworten einarbeiten“**. Er öffnet das Gespräch, in dem das Angebot entstanden ist (oder ein
   neues), und schickt „mach weiter, arbeite meine Antworten im Angebot <titel> ein“.
3. **Positionen auf dem Handy als Karten** statt als Tabelle: Nr. + Menge links, Name (mit Umbruch) in der Mitte,
   Gesamtpreis rechts, EK/VK klein darunter. Bemerkung nur, wenn vorhanden.
4. **Rechenwege** (`markierung.art == "info"`) nicht als blaue Kästen zeigen, sondern eingeklappt als „Rechenweg ▸“
   unter der Position.
5. Kopfzeile kompakt: Titel einzeilig mit Auslassung, dann Material-EK und Pauschalen-VK nebeneinander. Die Aktionen
   (Web-Vorschau, nach HERO) in ein Menü „⋯“.

## Zum Vergleich
Die Web-Vorschau (Link aus dem Chat oder `vorschau`) zeigt das schon so, auch auf dem Handy. Über den
Ansehen-Link kann man dort jetzt ebenfalls antworten, ändern lässt sich weiterhin nichts.

## Abnahme
- Ein Angebot mit offenen Fragen öffnen: Oben stehen Karten mit Position und Preis, ohne Scrollen zur Position.
- „Passt so“ an einer Alternative tippen: Die Karte verschwindet.
- Eine Option antippen: Die Karte wandert in „beantwortet“. „Antworten einarbeiten“ tippen: Der Assistent ändert
  die Position, und der Punkt ist danach erledigt.
- Kein Text „Entwurf – noch nicht bestätigt“ mehr zu sehen.
