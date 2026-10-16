# Auftrag an den App-Agenten – Server 1.29.0: Fotos sofort ablegen, beliebig viele, Sprechen bezieht sich darauf

Anlass (User 16.10.2026): „Es müssen grundsätzlich mehr als 5 angenommen werden, egal über welchen Weg – das ist ein
Muss!“ Dazu der Ablauf auf der Baustelle: Monteur lädt vorher gemachte Bilder hoch, läuft weiter, fotografiert noch
etwas, und zu einem Foto will er nur eine Frage stellen. Er darf dabei **nie entscheiden müssen**, ob es ein Doku-Foto
oder ein KI-Foto ist. Dein Vorschlag (Kamera legt sofort ab, Mikrofon bezieht sich auf das letzte/markierte Foto) ist
genau so umgesetzt – der Server kann es jetzt.

Vertrag: [`openapi.json`](openapi.json) 1.29.0 (sha256 `a6c2ee82…be405b9`), Beschreibung in [`API.md` 5c
„Fotos, Dokumente, Videos vom Handy“ und 5d](API.md) (Felder `foto_id`, `foto_ids`). Nur Ergänzungen; 1.22–1.28 gelten
weiter.

## Server (fertig)
1. **Keine Grenze von 5** mehr: `POST /v1/monteur/projekte/{id}/dateien` war schon unbegrenzt (eine Datei je Aufruf);
   im Eingang gehen jetzt bis 50 neue `fotos` und **beliebig viele `foto_ids`**. Die KI sieht davon die letzten 5.
2. `POST /v1/monteur/projekte/{id}/dateien` liefert bei Fotos sofort **`foto_id`** (`m…`) – im Eingang als `foto_ids`
   nutzbar, auch bevor HERO das Foto hat.
3. `foto_ids` werden **nie nochmal abgelegt** (auch nicht bei Meldung, Bericht, Bestellung) – sie liegen ja schon im
   Projekt. Beim Protokoll kommen sie als Anlage ins PDF.
4. **Titel nachträglich:** Karte `foto` bzw. Frage-Karte mit `foto` → „Passt“ benennt die Datei um, solange sie noch
   im Upload-Ordner liegt (die ersten Minuten); ist sie schon in HERO, steht der Titel als Kommentar im Projektverlauf.
   Ohne Titel ist nichts verloren: das Foto liegt mit Datum, Uhrzeit und Name im Projekt.

## Bitte bauen (dein Vorschlag)
- **Kamera antippen = Foto**, geht sofort über den Ausgangskorb ins Projekt (`…/projekte/{id}/dateien`, Projekt aus
  Termin/Projektmappe bzw. laufendem Termin), auch im Funkloch. `foto_id` aus der Antwort merken.
- **Kamera lange drücken = Galerie** (Photo Picker), beliebig viele – ebenfalls alles sofort ins Projekt.
- Die Fotos erscheinen als Leiste **„gerade aufgenommen“** (dieser Termin/dieses Projekt, z. B. letzte 2 Stunden).
- **Mikrofon** (sprechen oder tippen) bezieht sich automatisch auf das **letzte** Foto der Leiste; andere antippen =
  markieren (mehrere möglich). Gesendet wird `foto_ids` der markierten bzw. des letzten Fotos – keine Bilddaten mehr.
  Solange ein Foto noch im Ausgangskorb hängt (keine `foto_id`), wartet die Spracheingabe kurz bzw. schickt es als
  `fotos` mit (Server legt es dann einmal ab).
- Der Knopf „Foto/Video/Datei“ ist für Fotos nicht mehr nötig; Videos und Dokumente ins ⋮-Menü der Projektmappe.
- Ohne Projekt (kein Termin, keine Projektmappe offen): Foto erst beim Senden mit der Spracheingabe als `fotos`
  schicken (der Server findet den Termin bzw. fragt nach).

## Testen
- In SAN-1243 bzw. UNB-795: 8 Fotos aus der Galerie → alle im Projekt (HERO nach ein paar Minuten).
- Ein Foto aufnehmen, Mikrofon: „Zählerstand Wasser“ → Karte `foto`, „Passt“ → Datei heißt „… Zählerstand Wasser.jpg“.
- Drei Fotos markieren: „Leitung undicht, Büro soll Angebot machen“ → Meldung, „Passt“ → Kommentar im Verlauf, keine
  doppelten Fotos im Projekt.
