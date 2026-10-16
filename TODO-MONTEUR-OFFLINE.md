# TODO – Monteur-App: Projektmappe ohne Netz (Stand 2026-10-16)

**Vorgabe des Nutzers:** „Auch ohne Netz muss die Projektmappe öffnen!“ Dazu gilt weiter: Es darf nie etwas verloren gehen, und der Monteur darf nie warten müssen.

**Heute:** Die Projektmappe (`GET /v1/monteur/projekte/{id}`) öffnet sich nur mit Netz. Im Keller ohne Empfang sieht der Monteur nichts: keine Adresse, keine Notizen des Büros, keine Vorgeschichte, keine Pläne und PDFs, keine Fotos. Die Kamera auf „Heute“ geht trotzdem, Fotos warten in der Eingabe.

## Teil App (`android/`, Flavor `monteur`)

1. **Projektmappe zwischenspeichern:** jede erfolgreich geladene Mappe als JSON auf dem Gerät (verschlüsselt wie der Korb, Kundendaten), je Projekt-ID.
2. **Öffnen ohne Netz:** sofort aus dem Speicher anzeigen, mit kleiner grauer Zeile „Stand: heute 07:42 – ohne Netz“. Mit Netz ebenfalls sofort aus dem Speicher zeigen und im Hintergrund still aktualisieren (nie ein Ladekreisel, wenn schon etwas da ist).
3. **Vorausladen:** Beim Öffnen der App und wenn Netz da ist, die Mappen aller Termine von heute und morgen im Hintergrund laden (WorkManager, nur WLAN oder mobil nach Einstellung, siehe Server-Teil).
4. **Dateien:** PDFs der Unterlagen (Pläne, Angebote, Berichte) und Foto-Vorschauen (480 px) für diese Termine mit vorausladen. Originalfotos nur auf Abruf. Speichergrenze (z. B. 300 MB, älteste zuerst raus) und Aufräumen nach dem Termin (z. B. 7 Tage).
5. **Termine selbst:** „Heute“ (`GET /v1/monteur/tage`) ebenso zwischenspeichern, sonst kommt man ohne Netz gar nicht bis zur Mappe.
6. **Ohne Netz sichtbar machen:** Was nur online geht (Video abspielen, Originalfoto, PDF, das nicht vorausgeladen wurde), zeigt „Braucht Netz“, statt zu hängen.
7. **Fotos, Sprache, Eingaben** in der Mappe ohne Netz: wie heute über den Korb, die Leiste „gerade aufgenommen“ funktioniert ohne Netz bereits.

## Teil Server (bitte heute Abend mitnehmen)

1. **Vorausladen in einem Abruf:** z. B. `GET /v1/monteur/mappen?von=heute&bis=morgen` liefert die Projektmappen aller eigenen Termine des Zeitraums auf einmal (gleiche Form wie `GET /v1/monteur/projekte/{id}`). Spart viele Einzelabrufe im schwachen Netz.
2. **Ändert sich etwas?** `ETag` bzw. `Last-Modified` an `GET /v1/monteur/projekte/{id}`, `…/tage` und `…/dateien/{uuid}`, damit die App mit `If-None-Match` nur Geändertes neu lädt (`304`).
3. **Dateigrößen in der Mappe:** `groesse` (Bytes) an `unterlagen[]` und `fotos[]`, damit die App entscheiden kann, was sie vorausladen darf.
4. Optional: `stand` (Zeitpunkt) in der Mappe, damit die App „Stand: …“ ehrlich anzeigen kann.

Ohne den Server-Teil geht es auch (die App lädt dann Mappe für Mappe und Datei für Datei), nur langsamer und mit mehr Datenverbrauch.

## Nebenbei aufgefallen (Server, 2026-10-16)

- Kopf der Projektmappe und Protokoll-PDF zeigen bei UNB-795 als Titel „-795 | --, --, --“ bzw. „UNB-795 – -795 | --, --, --“. Sieht nach einem leeren HERO-Feld aus, das der Server ungefiltert übernimmt.
- Testdaten von heute in UNB-795 (alle freigegeben): Fotos „App-Test …“, „Vorher Fassade …“, „Hausdurchgang …“, „Lagerregal …“, 6 Fotos „2026-10-16 13-36 Sascha (…)“ (darunter App-Bildschirmfotos), ein Foto 14-14. Offene Testkarte „App-Test ohne Netz“. Bei Gelegenheit in HERO löschen bzw. verwerfen.
