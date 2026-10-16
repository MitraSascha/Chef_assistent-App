# Chef-Assistent – Schnittstelle für die Android-App (v1)

Maschinenlesbarer Vertrag: [`openapi.json`](openapi.json) (OpenAPI 3.1).
Live abrufbar unter `https://<server>/openapi.json`, interaktiv unter `https://<server>/docs`.

## Grundlagen

| | |
|---|---|
| Basis-URL | **`https://assistent.mitra-sanitaer.de`** (Internet, ohne VPN – seit 1.10.0). Weiterhin möglich: `https://mitraserver-aspire-xc-605.tail8bd028.ts.net` (nur mit Tailscale) |
| Anmeldung | Header `Authorization: Bearer <token>`. Token `cs_…` bekommt die App über **`POST /v1/anmelden`** (Name + Passwort + 6-stelliger Code aus der Authenticator-App), siehe [Abschnitt 0](#0-anmeldung--neu-in-1100). Alte Zugangsschlüssel `ca_…` gelten **nur** noch über Tailscale/Büro, über die Internet-Adresse gibt es dafür `401`. |
| Format | JSON, UTF-8. Zeiten ISO 8601 (`2026-09-24T09:30:00+02:00`) |
| Timeouts | Chat und Sprache: **kein Lese-Timeout** (seit 1.17.0) – ein Angebot mit HERO-Übertragung kann viele Minuten dauern. Reißt die Verbindung trotzdem ab, arbeitet der Assistent weiter; Ergebnis abholen, siehe [1b](#1b-lange-anfragen-sicher--neu-in-1170) |
| Fehler | JSON `{"detail": "Klartext auf Deutsch"}` – `detail` kann direkt angezeigt werden |

Statuscodes: `401` Token fehlt/falsch/gesperrt → Login-Bildschirm · `404` nicht gefunden ·
`409` Gespräch ist noch beschäftigt bzw. Bestätigung schon erledigt · `413` Aufnahme/Foto zu groß ·
`415` kein lesbares Foto/Video/Dokument ·
`422` Eingabe ungültig / keine Sprache erkannt · `502` KI nicht erreichbar → „Nochmal versuchen“ anbieten.
Bei Chat/Sprache: `502`/`503`/`504` oder Netzwerkfehler heißt NICHT „verloren“ → Ergebnis mit `GET /v1/chat/{anfrage_id}` abholen (1b).

Token sicher speichern (EncryptedSharedPreferences / Android Keystore). Beim App-Start `GET /v1/ich` → 200 = angemeldet,
401 = Token gelöscht/abgelaufen → Anmeldebildschirm.

## Endpunkte

| Methode | Pfad | Zweck |
|---|---|---|
| GET | `/v1/gesund` | Erreichbarkeit (ohne Anmeldung) |
| POST | `/v1/anmelden` | Name + Passwort + Code → Sitzungs-Token `cs_…` (ohne Anmeldung aufrufbar) |
| POST | `/v1/abmelden` | Diese Sitzung beenden (Token wird ungültig) |
| GET | `/v1/sicherheit` | Passwort/Zwei-Faktor eingerichtet? |
| POST | `/v1/sicherheit/passwort` | Passwort ändern |
| POST | `/v1/sicherheit/zwei-faktor` | Zwei-Faktor einrichten: QR-Code |
| POST | `/v1/sicherheit/zwei-faktor/bestaetigen` | … mit erstem Code bestätigen |
| GET | `/v1/geraete` | Angemeldete Geräte |
| DELETE | `/v1/geraete/{id}` | Gerät abmelden (z. B. verlorenes Handy) |
| GET | `/v1/ich` | Wer bin ich, welches Modell |
| GET | `/v1/modelle` | **Modellwahl**: wählbare Modelle und Denkstufen, was „Automatisch“ heißt (neu in 1.20.0) |
| PATCH | `/v1/gespraeche/{id}` | Modell/Denkaufwand eines Gesprächs setzen (neu in 1.20.0), Gespräch **anheften** (neu in 1.21.0) |
| POST | `/v1/chat` | Textnachricht → Antwort |
| GET | `/v1/chat/{anfrage_id}?seit=` | Stand/Ergebnis einer Chat-Anfrage abholen (neu in 1.17.0), mit **Fortschritt** (neu in 1.20.0) |
| POST | `/v1/chat/{anfrage_id}/abbrechen` | Laufende Antwort stoppen (neu in 1.20.0) |
| POST | `/v1/chat/{anfrage_id}/zwischenruf` | Während der Bearbeitung etwas ergänzen (neu in 1.20.0) |
| POST | `/v1/sprache` | Sprachaufnahme → erkannter Text (+ Antwort) |
| POST | `/v1/vorlesen` | Text → MP3 (optional; Alternative zu Android-TTS) |
| GET | `/v1/notizen` | Eigene Notizen und Ordner (Auswahl fürs Ziel von Fotos/Videos) |
| POST | `/v1/fotos` | Foto an eine Notiz hängen |
| POST | `/v1/dokumente` | Dokument (PDF, Word, Excel …) an eine Notiz hängen – neu in 1.12.0 |
| POST | `/v1/videos` | Video an eine Notiz hängen, Gesprochenes wird abgeschrieben |
| POST | `/v1/hero-mappe` | **HERO-Projekt anheften**: HERO-Mappe holen/anlegen – Inhalt geht automatisch nach HERO (neu in 1.19.0) |
| GET | `/v1/hero-mappe?projekt=` | Stand der HERO-Mappe: was schon Richtung HERO ging (neu in 1.19.0) |
| GET | `/v1/auftraege` | Hintergrundaufträge (Versicherungsberichte) mit Stand |
| GET | `/v1/auftraege/{id}` | Ein Auftrag |
| POST | `/v1/auftraege/{id}/abbrechen` | Auftrag abbrechen |
| POST | `/v1/auftraege/{id}/wiederholen` | Fehlgeschlagenen/abgebrochenen Auftrag neu starten |
| GET | `/v1/aufgaben?person=` | „Was ist offen?“ – Aufgaben aus den Notizen, gruppiert |
| POST | `/v1/aufgaben/{id}` | Aufgabe abhaken, zurücknehmen, streichen, „noch aktuell“, Frist ändern |
| GET | `/v1/bauablauf` | GU-Projekte (je eine eigene Plantafel) |
| POST | `/v1/bauablauf` | GU-Projekt anlegen (Ordner) |
| GET | `/v1/bauablauf/alle` | Alle Einsätze aller GU-Projekte |
| GET | `/v1/bauablauf/{id}` | Plantafel eines Projekts: Gewerke, Einsätze, Warnungen |
| POST | `/v1/bauablauf/{id}/einsaetze` | Einsatz anlegen |
| PATCH | `/v1/bauablauf/einsaetze/{id}` | Einsatz ändern (Ziehen, Länge, Status …) |
| DELETE | `/v1/bauablauf/einsaetze/{id}` | Einsatz löschen |
| PUT | `/v1/bauablauf/{id}/gewerke` | Gewerk anlegen/ändern (Firma, Kontakt, Telefon, Farbe) |
| DELETE | `/v1/bauablauf/gewerke/{id}` | Gewerk löschen (nur ohne Einsätze) |
| POST | `/v1/bauablauf/{id}/pdf` | Bauzeitenplan als PDF in den Projektordner |
| GET | `/v1/angebote` | Angebote aus dem Angebotsgenerator (neueste zuerst) |
| GET | `/v1/angebote/{id}` | Vorschau eines Angebots: Positionen, Summen, offene Fragen, Markierungen |
| GET | `/v1/angebote/{id}/ugl?titelzeilen=` | UGL-Datei für den CRM-Import herunterladen |
| GET | `/v1/bestaetigungen` | Alle offenen Ja/Nein-Fragen |
| POST | `/v1/bestaetigungen/{id}` | Ja/Nein beantworten |
| GET | `/v1/gespraeche?q=` | Liste der Gespräche (angeheftete zuerst, dann neueste); `q` = **Suche im Titel** über alle (neu in 1.21.0) |
| GET | `/v1/gespraeche/{id}` | Verlauf eines Gesprächs |
| DELETE | `/v1/gespraeche/{id}` | Gespräch löschen |
| GET | `/v1/termine?von=&bis=&person=&alle=` | Kalender direkt (z.B. für eine Tagesansicht) |
| GET | `/v1/kalender?von=&bis=&personen=` | **Kalender mit HERO-Terminen** + eigene, Personenfilter (neu in 1.16.0) |
| GET | `/v1/personen` | Personen für den Kalenderfilter (neu in 1.16.0) |
| GET | `/v1/kalender/kategorien` | HERO-Terminkategorien für „Neuer HERO-Termin“ (neu in 1.18.0) |
| POST | `/v1/kalender/hero?nur_pruefen=` | **HERO-Termin anlegen** bzw. vorher prüfen (neu in 1.18.0) |
| PATCH | `/v1/kalender/hero/{id}?nur_pruefen=` | **HERO-Termin verschieben** bzw. vorher prüfen (neu in 1.18.0) |
| GET | `/v1/datei?pfad=&anzeigen=` | Datei herunterladen bzw. anzeigen (Links aus `dateien`) |
| GET | `/v1/ordner?pfad=&sortierung=&ab=&anzahl=` | Ordnerinhalt für die Ordneransicht (Sortierung/Seiten neu in 1.13.0) |
| GET | `/v1/suche?q=&ordner=&typ=&max=` | **Suche über die ganze Ablage** (neu in 1.13.0) |
| GET | `/v1/freigabe` | Netzwerkpfade der Ablage für Windows |
| GET | `/v1/verbrauch` | Token-Verbrauch je Monat |
| POST | `/v1/monteur/einrichten` | **Monteur-App:** Einrichtungscode (QR) → Sitzung `cs_…` (ohne Anmeldung, neu in 1.22.0) |
| GET | `/v1/monteur/tage?von=&bis=` | Eigene Termine je Tag mit Kunde, Adresse, Telefon, Beschreibung, Berichtsstand (1.22.0) |
| GET | `/v1/monteur/projekte/{id}` | Projektmappe: Notizen, Unterlagen (Angebote, Pläne, Aufmaße, Berichte – keine Rechnungen), Fotos (1.22.0), **Verlauf** = Kommentare im HERO-Projektverlauf (neu in 1.23.0) |
| GET | `/v1/monteur/dateien/{uuid}?vorschau=` | PDF/Foto aus der Projektmappe (Vorschau 480 px) (1.22.0) |
| POST | `/v1/monteur/projekte/{id}/dateien` | Foto/Dokument vom Handy → HERO-Projekt; Video → nur Server (1.22.0) |
| GET | `/v1/monteur/projekte/{id}/videos/{name}` | Video aus dem Projektordner abspielen (1.22.0) |
| POST | `/v1/monteur/berichte` | Diktat/Text → Berichtsentwurf (Termin wird selbst erkannt) (1.22.0) |
| GET | `/v1/monteur/berichte`, `/v1/monteur/berichte/{id}` | Eigene Berichte (1.22.0) |
| POST | `/v1/monteur/berichte/{id}/diktat` | Entwurf per Sprache ergänzen/korrigieren (1.22.0) |
| PATCH / DELETE | `/v1/monteur/berichte/{id}` | Entwurf korrigieren (Artikel wählen) / verwerfen (1.22.0) |
| GET | `/v1/monteur/artikel?q=&max=` | Artikelstamm durchsuchen (ohne Preise) für „+ Material“ von Hand (1.22.0) |
| POST | `/v1/monteur/berichte/{id}/abschliessen` | „Passt“ → **fertiger Baustellenbericht in HERO** + Unterschrift + Zeiten (1.22.0) |
| GET / POST | `/v1/monteur/zeiten` | Zeiten des Tages (aus HERO) / Zeitabschnitt eintragen (1.22.0) |
| PATCH / DELETE | `/v1/monteur/zeiten/{id}` | Zeit ändern / löschen, solange nicht eingereicht (1.22.0) |
| POST | `/v1/monteur/zeiten/{datum}/einreichen` | Tag bestätigen → in HERO eingereicht (1.22.0) |
| POST | `/v1/monteur/stempel` | Stempeluhr start / stopp / wechsel (1.22.0) |
| GET | `/v1/monteur/stundenkonto` | Soll, bestätigt, Saldo (1.22.0) |
| GET / POST / DELETE | `/v1/monteur/abwesenheiten` | Urlaubsstand + Anträge / beantragen / zurückziehen (1.22.0) |
| POST | `/v1/monteur/verstehen` | Sprache → Vorschläge für Zeiten oder Urlaub (bucht nichts) (1.22.0) |
| POST | `/v1/monteur/eingang` | **Ein Eingang für alles**: Sprache/Text/Fotos → KI erkennt Bericht, Frage, Bestellung, Meldung, Zeiten, Urlaub → **eine Karte** (neu in 1.23.0) |
| GET | `/v1/monteur/eingang` | Eigene Karten der letzten 7 Tage, neueste zuerst (z. B. offene nach App-Neustart) (1.23.0) |
| GET | `/v1/monteur/eingang/{id}` | Eine Karte (1.23.0) |
| POST | `/v1/monteur/eingang/{id}/passt` | **„Passt“** → je nach Art: Bericht nach HERO, fertiger Bestellschein, Kommentar im Projektverlauf, Zeiten buchen, Urlaub beantragen (1.23.0) |
| DELETE | `/v1/monteur/eingang/{id}` | Karte verwerfen (1.23.0) |
| GET | `/v1/monteur/anleitung?pfad=` | Herstelleranleitung (PDF) aus unserer Bibliothek – Treffer einer Antwortkarte (1.23.0) |
| GET / POST | `/v1/monteure`, `/v1/monteure/{name}/einrichtungscode` | Büro: Monteur-Zugänge anlegen, QR-Code erzeugen (1.22.0) |

## 0. Anmeldung – neu in 1.10.0

Der Server ist aus dem Internet erreichbar. Deshalb meldet sich die App mit **Name, Passwort und Code aus der
Authenticator-App** an (Microsoft/Google Authenticator, TOTP 30 s, 6 Stellen). Passwort und Zwei-Faktor richtet jede
Person einmal in der Weboberfläche ein (🔒 unten links) – die App muss das **nicht** können.

**Auftrag für die App:**
1. Basis-URL auf `https://assistent.mitra-sanitaer.de` umstellen (in den Einstellungen änderbar lassen).
2. Anmeldebildschirm mit drei Feldern: Name, Passwort, Code (`inputType=number`, 6 Stellen, Autofill
   `AUTOFILL_HINT_SMS_OTP` bzw. `one-time-code`). Name nach Erfolg merken und vorbelegen.
3. `POST /v1/anmelden` → `token` sicher speichern (Keystore) und als Bearer nutzen – **ersetzt** den `ca_`-Schlüssel.
4. `401` bei irgendeinem Aufruf → Token löschen, Anmeldebildschirm. Die Sitzung verfällt nach 60 Tagen ohne Nutzung,
   bei Passwortwechsel und wenn das Gerät in der Weboberfläche abgemeldet wird.
5. Menüpunkt „Abmelden“ → `POST /v1/abmelden`, dann Token löschen.
6. Das Passwort nie speichern, nur das Token.

```
POST /v1/anmelden
{"name": "sascha", "passwort": "…", "code": "123456", "geraet": "Galaxy S25 – App"}
→ 200 {"token": "cs_…", "geraet_id": 7, "name": "sascha", "anzeigename": "Sascha", "gueltig_tage": 60}
```

| Status | Bedeutung → Anzeige |
|---|---|
| `401` | „Name, Passwort oder Code falsch.“ (`detail` anzeigen, Code-Feld leeren) |
| `403` | Zwei-Faktor noch nicht eingerichtet → `detail` anzeigen |
| `429` | Zu viele Fehlversuche, 15 Minuten gesperrt → `detail` anzeigen |

Jeder Code gilt nur **einmal**: Zwei Anmeldungen direkt hintereinander brauchen den nächsten Code (≤ 30 s warten).

Optional in der App: Liste `GET /v1/geraete` (`[{"id","bezeichnung","ip","erstellt","zuletzt","dieses"}]`) mit
„Abmelden“ je Gerät (`DELETE /v1/geraete/{id}`).

## 1. Chat

```http
POST /v1/chat
{"text": "Was steht im Projekt Schulz an?", "gespraech_id": null, "anfrage_id": "3f2c9a0e-…"}
```
`gespraech_id` weglassen = neues Gespräch. Die Antwort enthält die ID; für Folgefragen mitschicken.
`anfrage_id` (neu in 1.17.0, dringend empfohlen): je Nachricht eine neue UUID der App – siehe [1b](#1b-lange-anfragen-sicher--neu-in-1170).
Optional `"bilder": ["<pfad>", …]` (max. 4) – Fotos, die der Assistent zu dieser Nachricht **ansieht**, siehe Abschnitt 4a.
Optional `"modell"` / `"denkaufwand"` (neu in 1.20.0) – Modellwahl des Gesprächs, siehe [1c](#1c-modell-und-denkaufwand-wählen--neu-in-1200).
Die Antwort sagt in `modell`, `denkaufwand`, `denkaufwand_name`, `modell_gewechselt`, womit sie erzeugt wurde (1c).

```json
{
  "gespraech_id": "c8719cc52c8f4d0a",
  "anfrage_id": "3f2c9a0e-…",
  "antwort": "Im Projekt **SAN-1270 Angelov** …",
  "dateien": ["HERO-Archiv/SAN-1270 Angelov/Nach HERO hochladen/Checkliste.pdf"],
  "bestaetigungen": [],
  "ausgefuehrt": [],
  "quellen": [{"titel": "BMWE | Förderung …", "url": "https://…"}]
}
```
- `antwort`: einfaches Markdown (`**fett**`, `- Listen`, `` `Pfad` ``). Z.B. mit Markwon anzeigen. Vor dem Vorlesen Markdown-Zeichen entfernen.
- `dateien`: gelesene/erstellte/verschobene Dateien → als antippbare Chips; Tippen = `GET /v1/datei?pfad=…` herunterladen und per `Intent.ACTION_VIEW` (FileProvider) öffnen.
- **`links` (neu in 1.13.0)** – dieselben Pfade wie `dateien`, aber fertig für Knöpfe. **Statt der `dateien`-Chips
  anzeigen**, sobald vorhanden. Fragt man im Chat nach einer Datei („schick mir die Rechnung von Boese“), gibt der
  Assistent die Treffer genau hier mit:
  ```json
  "links": [{"pfad": "HERO-Archiv/SAN-1166 Herr Mark Boese/Rechnungen/Rechnung-RE-1410-Boese.pdf",
             "name": "Rechnung-RE-1410-Boese.pdf", "typ": "datei", "groesse": "84.2 KB",
             "ordner": "HERO-Archiv/SAN-1166 Herr Mark Boese/Rechnungen",
             "projekt": "SAN-1166 · Herr Mark Boese · Grimmstraße 7, 10967 Berlin"},
            {"pfad": "HERO-Archiv/SAN-1166 Herr Mark Boese", "name": "SAN-1166 Herr Mark Boese", "typ": "ordner",
             "ordner": "HERO-Archiv", "groesse": null, "projekt": "SAN-1166 · Herr Mark Boese · Grimmstraße 7, 10967 Berlin"}]
  ```
  Je Link eine Karte: Symbol nach Dateiendung bzw. Ordner, `name` fett, `projekt` als Untertitel.
  - `typ: "datei"` → Hauptknopf **„Öffnen“** (`GET /v1/datei?pfad=…`, öffnen wie in Abschnitt 6) und Nebenknopf
    **„Im Ordner zeigen“** → Ordneransicht mit `GET /v1/ordner?pfad=<pfad>` (Datei markiert, siehe Abschnitt 6).
    Optional „Teilen“ (Datei laden, `ACTION_SEND`).
  - `typ: "ordner"` → ein Knopf **„Ordner öffnen“** → `GET /v1/ordner?pfad=<pfad>`.
  - Inzwischen gelöschte/verschobene Dateien fehlen in `links` (stehen aber noch in `dateien`).
- `quellen`: Web-Quellen als Links.
- Pro Gespräch läuft immer nur eine Anfrage; eine zweite parallele bekommt `409`.
- Bricht ein Zug mittendrin ab (KI-Dienst weg, interner Fehler), kommt trotzdem `200`: `antwort` beginnt mit
  „⚠️ Die Anfrage ist mittendrin abgebrochen …“ und listet, was bis dahin ausgeführt wurde (seit 1.17.0). Ganz normal
  anzeigen – der Nutzer schreibt „mach weiter“.

## 1c. Modell und Denkaufwand wählen – neu in 1.20.0

Wie in den Web-Versionen von ChatGPT/Claude kann der Nutzer **je Gespräch** Modell und Denkaufwand wählen. Standard
ist „Automatisch“ (`"auto"`): Der Server nimmt für den Alltag `MODELL` (gpt-5.6-luna, Denkaufwand niedrig) und wechselt
bei großen Aufgaben (Angebote, Auswertungen, feste Abläufe, mehr als 6 Werkzeugaufrufe) für den Rest der Antwort auf
das Arbeitsmodell (gpt-5.6-sol, mittel) – genau wie bis 1.19.0.

```http
GET /v1/modelle
```
```json
{
  "modelle": [
    {"wert": "gpt-5.6-luna", "name": "gpt-5.6-luna", "teuer": false, "hinweis": null},
    {"wert": "gpt-5.6-sol", "name": "gpt-5.6-sol", "teuer": false, "hinweis": null},
    {"wert": "gpt-5.6-astra", "name": "gpt-5.6-astra", "teuer": true,
     "hinweis": "Deutlich teurer pro Anfrage – nur für besonders schwierige Aufgaben"}
  ],
  "denkstufen": [{"wert": "low", "name": "niedrig"}, {"wert": "medium", "name": "mittel"}, {"wert": "high", "name": "hoch"}],
  "automatisch": "Automatisch: Der Server wählt je Nachricht. Alltag mit gpt-5.6-luna (Denkaufwand niedrig); bei großen Aufgaben (Angebote, Auswertungen, feste Abläufe, viele Schritte) Wechsel auf gpt-5.6-sol (Denkaufwand mittel). Teure Modelle (gpt-5.6-astra) nimmt die Automatik nie.",
  "standard_modell": "gpt-5.6-luna", "arbeits_modell": "gpt-5.6-sol",
  "standard_denkaufwand": {"wert": "low", "name": "niedrig"}, "arbeits_denkaufwand": {"wert": "medium", "name": "mittel"}
}
```
Die Listen kommen aus der Server-Einstellung (`MODELLE_AUSWAHL`, `MODELLE_TEUER`, `DENKSTUFEN_AUSWAHL`) – nicht in der
App fest einbauen. An die API geht immer `wert`.

**Teure Modelle** (`teuer: true`, derzeit Astra): kosten pro Anfrage deutlich mehr. Die Automatik nimmt sie **nie**,
nur auf ausdrückliche Wahl. Darstellung: in der Liste **abgesetzt** (eigener Abschnitt/Trennlinie unter den normalen
Modellen) und mit „teuer“ in **Warnfarbe** gekennzeichnet; beim Auswählen **einmal bestätigen** lassen („gpt-5.6-astra
ist deutlich teurer pro Anfrage. Für diesen Chat verwenden?“ – Nein = alte Auswahl bleibt); solange es im Gespräch
eingestellt ist, das Auswahlfeld in Warnfarbe und daneben „⚠ teuer pro Anfrage“ (`hinweis` als Info). `"auto"` ist immer zusätzlich erlaubt und steht nicht in den Listen. Mögliche Denkstufen-Namen:
minimal, niedrig, mittel, hoch, „sehr hoch“ (xhigh).

**Setzen** – zwei gleichwertige Wege, beide speichern die Wahl im Gespräch:
- `PATCH /v1/gespraeche/{id}` mit `{"modell": "gpt-5.6-sol", "denkaufwand": "auto"}` (Felder einzeln weglassbar =
  unverändert) → Antwort = Eintrag wie in `GET /v1/gespraeche`. Für ein **bestehendes** Gespräch, sobald der Nutzer die
  Auswahl ändert. Gilt ab der nächsten Nachricht.
- Optionale Felder `modell` / `denkaufwand` in `POST /v1/chat` (bei `POST /v1/sprache` als Formularfelder). Nötig für
  ein **neues** Gespräch (es hat noch keine ID); darf aber immer mitgeschickt werden. Gilt schon für diese Nachricht.

Neues Gespräch ohne Felder = `auto`/`auto`. Ungültiger Wert → `422`, z. B.
`{"detail": "Unbekanntes Modell „gpt-4“. Erlaubt: auto, gpt-5.6-luna, gpt-5.6-sol."}` bzw. „Unbekannter Denkaufwand …“
(bei Chat/Sprache wird dann nichts ausgeführt). Fremdes/unbekanntes Gespräch → `404`.

**Wie der Server entscheidet:**

| Modell | Denkaufwand | Ergebnis |
|---|---|---|
| auto | auto | wie bisher: luna/niedrig, bei großer Aufgabe → sol/mittel |
| fest (z. B. sol) | auto | ganze Antwort mit sol, kein Modellwechsel; Denkaufwand niedrig, bei großer Aufgabe mittel |
| auto | fest (z. B. hoch) | luna, bei großer Aufgabe → sol; Denkaufwand immer hoch |
| fest | fest | genau diese Kombination, kein Wechsel |

Sonderaufgaben (Versicherungsbericht, Begehungsprotokoll, Spracherkennung, Vorlesen) haben eigene Modelle und
bleiben davon unberührt. Steht ein gespeicherter Wert nicht mehr in der Auswahl (Server-Einstellung geändert), gilt
er als `auto`.

**Modell beim Anbieter nicht verfügbar** (OpenAI kennt die ID nicht bzw. gibt sie nicht frei): Es kommt `200` mit
`antwort` „⚠️ Modell gpt-5.6-astra ist beim Anbieter nicht verfügbar – bitte anderes Modell wählen. Es wurde nichts
ausgeführt …“. Normal anzeigen; der Nutzer stellt um und sendet erneut.

**Anzeige:** `GET /v1/gespraeche` liefert je Gespräch `modell` und `denkaufwand` (gewählt, `auto` oder Wert).
Jede Antwort (`POST /v1/chat`, `GET /v1/chat/{anfrage_id}`, `chat` in `/v1/sprache`) und jede `assistent`-Nachricht
in `GET /v1/gespraeche/{id}` sagt, womit sie **tatsächlich** erzeugt wurde:
```json
{"modell": "gpt-5.6-sol", "denkaufwand": "medium", "denkaufwand_name": "mittel", "modell_gewechselt": true}
```
`modell_gewechselt: true` = während der Antwort automatisch auf das Arbeitsmodell bzw. mehr Denkaufwand gewechselt
(angegeben ist das zuletzt verwendete). Bei Nachrichten von vor 1.20.0 sind die Felder `null`.

## 1d. Fortschritt, Denken, Stopp und Zwischenruf – neu in 1.20.0

Wie in den Konsolen der großen Modelle sieht der Nutzer, **was der Assistent gerade tut**, kann sein „Denken“
aufklappen, ihn **stoppen** oder ihm einen **Zwischenruf** schicken.

**Fortschritt abfragen.** Die App vergibt die `anfrage_id` selbst (1b) und fragt **während `POST /v1/chat` noch läuft**
etwa **jede Sekunde** `GET /v1/chat/{anfrage_id}?seit=<n>` ab (parallel, auf einer zweiten Verbindung). `seit` = das
`naechster` der vorigen Antwort (beim ersten Mal 0) – so kommen nur neue Ereignisse. Solange die Anfrage noch nicht
beim Server ist, gibt es `404` → einfach weiter versuchen.
```json
{"anfrage_id": "3f2c9a0e-…", "status": "laeuft", "gespraech_id": "c8719cc52c8f4d0a", "antwort": null, "fehler": null,
 "fortschritt": {
   "ereignisse": [
     {"nr": 3, "zeit": "2026-10-15T09:12:04.120+00:00", "sekunde": 4.2, "art": "werkzeug", "text": "Sucht Projekt „Altkaulsdorf“", "werkzeug": "hero_projekt_suchen"},
     {"nr": 4, "zeit": "…", "sekunde": 4.6, "art": "werkzeug_fertig", "text": "fertig", "werkzeug": "hero_projekt_suchen", "ok": true, "dauer_ms": 380},
     {"nr": 5, "zeit": "…", "sekunde": 4.7, "art": "schritt", "text": "Schritt 3 · gpt-5.6-sol · mittel", "schritt": 3, "modell": "gpt-5.6-sol", "denkaufwand": "medium"},
     {"nr": 6, "zeit": "…", "sekunde": 9.8, "art": "denken", "text": "**Rechnungen prüfen** Ich vergleiche Angebot und Schlussrechnung …"}
   ],
   "naechster": 7, "schritte": 3, "werkzeuge": 5, "aktuell": "Denkt nach …", "dauer_s": 9.9,
   "gestartet": "2026-10-15T09:12:00+00:00"}}
```
Ereignis-Arten: `schritt` (ein Modellaufruf beginnt), `werkzeug` (Werkzeug startet, `text` sagt auf Deutsch was –
ohne vollständige Argumente), `werkzeug_fertig` (`ok`, `dauer_ms`), `wechsel` (Modellwechsel, 1c), `denken`
(Denk-Zusammenfassung des Modells), `zwischenruf`, `abbruch` (Stopp angefordert), `abgebrochen`, `fehler`, `fertig`.
Unbekannte Arten als einfache Zeile zeigen. `schritte`/`werkzeuge` = Zähler (Websuchen zählen als Werkzeug),
`aktuell` = was gerade passiert (nur solange `laeuft`).

**Denken:** Die Denk-Zusammenfassungen (`art: "denken"`) sind **nicht** Teil der Antwort und kommen nie in `antwort`.
Dezent und standardmäßig eingeklappt zeigen. Manche Modelle liefern keine – dann gibt es keine `denken`-Ereignisse.

**Darstellung (App und Web):** Statt „denkt nach …“ eine Zeile **„Arbeitet · Schritt 3 · 5 Werkzeuge · 0:42“**
(Uhr lokal weiterzählen) und darunter `aktuell`; aufklappbar „Details“ mit der Ereignisliste (Werkzeug-Zeile mit
„✓ 0,4 s“ bzw. Fehler, Denken kursiv/grau). Nach dem Ende hängt der gleiche Verlauf an der Antwort: `fortschritt` in
der ChatAntwort und an jeder `assistent`-Nachricht in `GET /v1/gespraeche/{id}` (bei älteren Nachrichten `null`) –
eingeklappt als **„Verlauf: 7 Werkzeuge · 1:12 min“** (nur zeigen, wenn Werkzeuge oder Denken dabei waren).

**Stopp:** `POST /v1/chat/{anfrage_id}/abbrechen` → `200` mit dem aktuellen Stand (`status` noch `laeuft`). Der
Assistent hört am **nächsten Schritt** auf: ein gerade laufender Modellaufruf läuft zu Ende, sein Ergebnis wird
verworfen; ein gerade laufendes Werkzeug wird fertig. Danach liefert der wartende `POST /v1/chat` (bzw. das Abholen)
`200` mit `abgebrochen: true` und `antwort` „⏹ Abgebrochen. …“ samt Liste, was bis dahin ausgeführt wurde – **das bleibt
bestehen, nichts wird rückgängig gemacht**. `GET /v1/chat/{id}` hat dann `status: "abgebrochen"` (mit `antwort`).
Schon fertig → `409`.

**Zwischenruf:** `POST /v1/chat/{anfrage_id}/zwischenruf` mit `{"text": "nimm lieber die Rechnung von 2025"}` →
`{"angenommen": true, "nachricht_id": 812, "hinweis": "Kommt beim nächsten Schritt beim Assistenten an."}`. Der Text geht
beim nächsten Modellaufruf als zusätzliche Nutzernachricht mit, steht sofort im Verlauf (`rolle: "nutzer"`,
`zwischenruf: true`) und als Ereignis `zwischenruf`. Kommt er, während das Modell gerade die Schlussantwort schreibt,
arbeitet der Assistent noch einen Schritt weiter. Anfrage schon fertig (oder wird abgebrochen) → `409` „… bitte als
normale Nachricht schreiben“ – dann den Text als neue Nachricht senden.

Fremde oder unbekannte `anfrage_id` → `404` (wie beim Abholen). Abwärtskompatibel: Wer `fortschritt` nicht liest und
nie abbricht, merkt nichts. `status: "abgebrochen"` kommt nur vor, wenn jemand (auch in der Weboberfläche) gestoppt
hat – unbekannten Status mit gesetzter `antwort` wie `fertig` behandeln.

## 1e. Gesprächsliste: anheften, suchen, nach Zeit gruppieren – neu in 1.21.0

Anlass: Die Liste der Gespräche wird lang. Wie bei ChatGPT/Claude gibt es jetzt **angeheftete** Gespräche oben, eine
**Suche** über alle Titel und die Liste wird in der Oberfläche **nach Zeit gruppiert**.

- `GET /v1/gespraeche` liefert je Gespräch zusätzlich `angeheftet` (bool). Reihenfolge: **angeheftete zuerst**, dann
  `geaendert` absteigend, höchstens 100 Einträge. Angeheftete sind also immer dabei, auch wenn sie alt sind.
- `GET /v1/gespraeche?q=heizung keller` → nur Gespräche, deren Titel **alle Wörter** enthält (Groß/klein egal bei
  a–z), über **alle** Gespräche, nicht nur die neuesten 100. `%` und `_` gelten als normale Zeichen. Leeres `q` = normale Liste.
- `PATCH /v1/gespraeche/{id}` mit `{"angeheftet": true}` bzw. `false` → Antwort = Eintrag wie in der Liste. Ändert
  `geaendert` **nicht** (das Gespräch rutscht in seiner Zeitgruppe nicht nach oben). Lässt sich mit `modell`/`denkaufwand`
  kombinieren; weggelassen = unverändert. Fremdes Gespräch → `404`.

**Gruppen (macht die Oberfläche selbst, nach `geaendert` in Ortszeit):** Angeheftet · Heute · Gestern · Letzte 7 Tage ·
Letzte 30 Tage · danach je Monat („September 2026“). Monatsgruppen starten eingeklappt; auf-/zugeklappt wird je Gruppe
gemerkt. Unter jedem Titel klein: Uhrzeit (heute/gestern), Wochentag + Datum (7 Tage) bzw. Datum.

## 1b. Lange Anfragen sicher – neu in 1.17.0

Ein Chat-Zug läuft auf dem Server **unabhängig von der Verbindung**: Reißt sie ab (Funkloch, App im Hintergrund,
Proxy), arbeitet der Assistent fertig und speichert das Ergebnis. Nichts wird doppelt ausgeführt.

1. Je Nachricht eine neue `anfrage_id` erzeugen (UUID; erlaubt `A–Z a–z 0–9 _ -`, 8–64 Zeichen) und **vor** dem
   Senden zusammen mit dem Text lokal merken. Bei `POST /v1/sprache` als Formularfeld `anfrage_id`.
2. HTTP-Client für Chat/Sprache **ohne Lese-Timeout** (OkHttp `readTimeout(0)` bzw. `callTimeout(0)`).
3. Kommt keine Antwort (Netzwerkfehler, `502`, `503`, `504`, App wurde beendet): alle 3–5 s
   `GET /v1/chat/{anfrage_id}`:
   ```json
   {"anfrage_id": "3f2c9a0e-…", "status": "laeuft", "gespraech_id": "c8719cc52c8f4d0a", "antwort": null, "fehler": null}
   ```
   - `laeuft` → weiter warten, Anzeige „arbeitet noch …“
   - `fertig` → `antwort` ist genau die ChatAntwort von `POST /v1/chat` → normal anzeigen
   - `fehler` → `fehler` anzeigen („Nochmal versuchen“)
   - `404` → die Nachricht kam nie an → **mit derselben `anfrage_id`** erneut senden
4. Dieselbe `anfrage_id` noch einmal per `POST /v1/chat` zu schicken ist ebenfalls sicher: läuft sie noch, wartet
   der Aufruf; ist sie fertig, kommt das gespeicherte Ergebnis – ausgeführt wird nur einmal.
5. Beim App-Start: gemerkte, unbeantwortete `anfrage_id`s abholen (Schritt 3). `GET /v1/gespraeche` hat dafür
   `laeuft: true` bei Gesprächen, in denen der Assistent gerade arbeitet (Lade-Symbol in der Liste).

Wird der Server während einer Anfrage neu gestartet: `status: "fehler"`, und im Gespräch steht ein Hinweis
„… neu gestartet … schreib „mach weiter““. Die Nachricht des Nutzers steht seit 1.17.0 **sofort** im Verlauf
(`GET /v1/gespraeche/{id}`), nicht erst mit der Antwort.

## 1a. Diagramme im Chat – neu in 1.14.0

Sollen Zahlen verglichen werden, schreibt der Assistent einen **Codeblock mit der Sprache `diagramm`** in `antwort`:
eine Zeile JSON. **Direkt darunter steht immer eine Markdown-Tabelle mit denselben Zahlen**, notfalls ergänzt der
Server sie. Eine App, die den Block nicht kennt, zeigt also mindestens die Tabelle.

````
Boese ist mit 1.820 € der größte offene Posten.

```diagramm
{"art": "balken", "titel": "Offene Rechnungen je Kunde", "einheit": "€", "x": ["Boese", "Dreißig", "Valovo"], "reihen": [{"name": "Offen", "werte": [1820.25, 830, 455]}]}
```

|  | Offen |
|---|---|
| Boese | 1.820,25 € |
…
````

**Format.** Der Server prüft jeden Block, bevor die Antwort gespeichert wird. Ungültige Blöcke entfernt er, die App
bekommt also nur gültige Blöcke in dieser Form:

| Feld | | |
|---|---|---|
| `art` | immer | `balken` (Vergleich, senkrechte Säulen) · `linie` (Verlauf über die Zeit) · `torte` (Anteile an einem Ganzen) |
| `titel` | immer | Überschrift über dem Diagramm (max. 80 Zeichen) |
| `einheit` | optional | z.B. `€`, `h`, `%` – an Werte in Tooltip/Achse anhängen (max. 12 Zeichen) |
| `x` | immer | Beschriftungen der x-Achse bzw. der Tortenstücke (Text, max. 40 Zeichen) |
| `reihen` | immer | `[{"name": "…", "werte": [Zahl \| null, …]}]`, `werte` genau so lang wie `x`; `null` = Wert fehlt (Lücke in der Linie, keine Säule) |
| `gestapelt` | optional | nur `balken` mit ≥ 2 Reihen: `true` = Säulen übereinander statt nebeneinander (keine negativen Werte) |

Grenzen: `balken` 1–24 x-Werte und 1–6 Reihen · `linie` 2–60 x-Werte und 1–6 Reihen · `torte` genau 1 Reihe mit
2–6 Stücken, alle Werte > 0. Werte sind reine Zahlen ohne Tausenderpunkt; negative Werte gibt es bei `balken` und
`linie`. Unbekannte Felder ignorieren, denn es können später welche dazukommen.

**Darstellung** (so macht es die Weboberfläche, bitte gleich halten):
- Farben der Reihen in **fester Reihenfolge**, geprüft auf Farbsehschwäche (hell / dunkel):
  1 `#2a78d6`/`#3987e5` · 2 `#eb6834`/`#d95926` · 3 `#1baf7a`/`#199e70` · 4 `#eda100`/`#c98500` ·
  5 `#e87ba4`/`#d55181` · 6 `#008300`/`#008300`. Bei `torte` gehört jedem Stück eine Farbe, in derselben Reihenfolge.
- Säulen höchstens 24 dp breit, oben 4 dp abgerundet, unten gerade. Nebeneinanderliegende Säulen und gestapelte Teile
  trennt eine 2-dp-Lücke in der Hintergrundfarbe. Linien sind 2 dp dick, am Ende ein Punkt (r 4 dp) mit 2-dp-Ring in
  der Hintergrundfarbe. Die Torte wird als Ring gezeichnet, in der Mitte steht die Summe.
- Hilfslinien haarfein und durchgezogen (nicht gestrichelt), in einem Grau knapp neben dem Hintergrund. Die y-Achse
  beginnt bei 0 und hat runde Schritte. Große Zahlen werden gekürzt: `25 Tsd.`, `1,2 Mio.`.
- **Legende** nur bei ≥ 2 Reihen bzw. bei der Torte (dort mit Wert und Prozent). Text steht immer in Textfarbe, nie
  in der Reihenfarbe; die Farbe trägt ein kleines Quadrat davor.
- Antippen/Hover zeigt eine Infobox mit x-Wert und allen Reihenwerten mit Einheit. Eine Linie zeigt dabei eine
  senkrechte Hilfslinie.
- Passen die x-Beschriftungen nicht nebeneinander, nur jede n-te zeigen. Nie überlappen lassen, nie drehen.
- Keine Zahl an jede Säule schreiben. Die Werte stehen in Infobox und Tabelle.
- **Vorlesen:** den `diagramm`-Block weglassen. `POST /v1/vorlesen` entfernt ihn auch selbst.

**Bewegung** (so läuft es in der Weboberfläche, bitte gleich anfühlen lassen). Die Animation läuft **einmal**, wenn das
Diagramm zum ersten Mal zu ≥ 25 % sichtbar wird, nicht bei jedem Scrollen und nicht beim Drehen/Größe ändern.
Kurve überall „schnell los, weich aus“ (`cubic-bezier(.22, 1, .36, 1)`, in Compose z.B. `CubicBezierEasing(.22f, 1f, .36f, 1f)`).
- Raster und Achsen blenden in 400 ms ein.
- **Säulen** wachsen aus der Nulllinie (negative nach unten), 700 ms, gestaffelt von links nach rechts: Start 120 ms,
  dann je Säule `min(60, 500 / Anzahl)` ms später. Gestapelte Säulen wachsen als Ganzes.
- **Linien** zeichnen sich von links nach rechts, 900 ms, je weitere Reihe 120 ms später. Danach springt der
  Endpunkt auf (Skalierung 0 → 1,5 → 1, 450 ms). Bei nur einer Reihe blendet danach die zarte Fläche darunter ein
  (Reihenfarbe mit 10 % Deckkraft, 600 ms).
- **Torte/Ring** läuft im Uhrzeigersinn ab 12 Uhr auf, 1000 ms, gleichzeitig zählt die Summe in der Mitte hoch.
- **Antippen/Hover:** Was nicht gemeint ist, tritt auf 30 % Deckkraft zurück (200 ms). Das berührte Tortenstück
  rückt 5 dp nach außen. Bei Linien steht eine senkrechte Hilfslinie mit Punkten auf jeder Reihe. Die Infobox blendet
  in 150 ms ein (dabei 4 dp nach oben), mit Schatten und runden Ecken. Ein Tipp auf einen Legendeneintrag hebt
  diese Reihe hervor, die anderen gehen auf 20 %.
- **„Animationen entfernen“** in den Android-Bedienungshilfen (`Settings.Global.ANIMATOR_DURATION_SCALE == 0`)
  heißt: alles sofort im Endzustand, ohne Übergänge.

Auch **Tabellen** stehen jetzt öfter in Antworten. Sie sollen seitlich scrollen statt umzubrechen, Zahlenspalten
rechtsbündig.

## 2. Bestätigungen (Ja/Nein)

Verschieben, Löschen, Überschreiben, Bearbeiten und Terminänderungen führt der Server **erst nach Bestätigung** aus.
Dann steht in der Chat-Antwort:

```json
"bestaetigungen": [{"id": "c22204aa60b9", "werkzeug": "verschieben",
  "beschreibung": "„Projekte/…/Checkliste.docx“ verschieben nach „Projekte/…/Dokumente“"}]
```
App zeigt `beschreibung` mit Knöpfen **Ja / Nein**:

```http
POST /v1/bestaetigungen/c22204aa60b9
{"annehmen": true}
```
```json
{"status": "ausgefuehrt", "beschreibung": "…", "fehler": null, "dateien": ["Projekte/…/Dokumente/Checkliste.docx"]}
```
`status`: `ausgefuehrt` | `abgelehnt` | `fehlgeschlagen` (dann steht der Grund in `fehler`). `409` = war schon erledigt oder abgelaufen (nach 24 h).

Alternativ per Sprache/Text: Sagt der Nutzer in der nächsten Nachricht „ja, mach“, führt der Assistent es selbst aus.
Das steht dann in `ausgefuehrt` der Chat-Antwort, und die App blendet die Knöpfe dieser `id` aus.
Beim Öffnen der App `GET /v1/bestaetigungen` holen, damit noch offene Fragen wieder sichtbar werden.

## 3. Sprache

**Eingabe** – Aufnahme als Datei hochladen (MediaRecorder: `MPEG_4` + `AAC`, 16–44,1 kHz, mono reicht; max. 25 MB):

```http
POST /v1/sprache   (multipart/form-data)
audio=@aufnahme.m4a ; type=audio/mp4
gespraech_id=c8719cc52c8f4d0a   (optional)
antworten=true                   (Standard; false = nur Text erkennen, z.B. zum Korrigieren vor dem Senden)
```
```json
{"text": "Welche Termine habe ich diese Woche noch?", "chat": { …wie POST /v1/chat… }}
```

**Ausgabe** – zwei Möglichkeiten:
1. Android `TextToSpeech` mit `Locale.GERMANY` – kostenlos, sofort, offline.
2. `POST /v1/vorlesen {"text": "…"}` → `audio/mpeg` (natürlichere Stimme, kostet ein wenig, max. 4000 Zeichen).
   Optional `"stimme"`: `alloy`, `ash`, `ballad`, `coral`, `echo`, `fable`, `nova`, `onyx`, `sage`, `shimmer`, `verse`.
   Die App speichert die Wahl des Nutzers selbst und schickt sie bei jedem Aufruf mit; ohne Feld gilt `STIMME` vom Server.
   Unbekannte Stimme → 422.

## 4. Fotos zur Notiz

Kamera-Knopf im Chat: Foto aufnehmen → `POST /v1/fotos` (multipart, **ein Foto pro Anfrage**).

| Feld | Pflicht | Bedeutung |
|---|---|---|
| `foto` | ja | JPEG, PNG oder WebP, max. 25 MB. **Kein HEIC** → 415 |
| `notiz` | nein | Pfad einer **eigenen** Notiz (`.md`) **oder eines eigenen Ordners** – aus `GET /v1/notizen`, `dateien` der Chat-Antwort oder `notiz` des letzten Uploads. Ordner = neueste Notiz darin, gibt es keine, legt der Server `<Datum> <Ordnername>.md` darin an. Weglassen = neueste eigene Notiz von heute (auch in Unterordnern); gibt es keine, legt der Server die Notiz „Fotos“ an |
| `text` | nein | Bildunterschrift, max. 4000 Zeichen (z.B. per Spracherkennung `/v1/sprache` mit `antworten=false` diktiert) |
| `gespraech_id` | nein | Aktuelles Gespräch. Dann steht im Verlauf eine `hinweis`-Zeile mit dem Foto in `dateien`, und der Assistent weiß beim nächsten Chat-Zug davon |

```json
{"foto": "Notizen/Sascha/2026-09-24 Objektbegehung Hotel/2026-09-24 13-24-05.jpg",
 "notiz": "Notizen/Sascha/2026-09-24 Objektbegehung Hotel.md", "notiz_neu": false}
```

Der Server dreht das Bild nach EXIF, verkleinert es auf 2560 px (lange Seite), speichert es als JPEG **ohne Metadaten
(kein GPS)** und hängt an die Notiz `## Foto <Zeit>` mit Bildverweis und Unterschrift an. Wohin die Datei kommt:
- Notiz direkt im Notizordner (`Notizen/Sascha/2026-09-24 X.md`) → eigener Ordner daneben (`Notizen/Sascha/2026-09-24 X/`).
- Notiz in einem Unterordner (`Notizen/Sascha/Begehung Simon-Dach-Straße 46/…md`) → **direkt in diesen Unterordner**
  (Projektmappe: Checkliste, Notiz, Fotos, Videos zusammen).

## 4a. Foto an den Assistenten (Typenschild, Fehleranzeige) – neu in 1.5.0

Der Assistent kann Fotos jetzt **ansehen**: Typenschild ablesen (Hersteller, Typ, Seriennummer, Baujahr, Leistung,
Gasart …), Fehlercodes im Display erkennen und dazu in der **Anleitungsbibliothek** des Servers (Hersteller-PDFs,
v.a. Vaillant und Junkers/Bosch) Ursache und Maßnahmen mit Seitenangabe nachschlagen. Serverseitig ist dafür nichts
weiter nötig; die App braucht nur einen Weg, ein Foto **mit einer Frage** zu schicken.

Ablauf (zwei Aufrufe, beide gibt es schon):
1. `POST /v1/fotos` mit `foto`, `gespraech_id` (und wie gewohnt optional `notiz`) → Antwort enthält `foto` (Pfad).
2. `POST /v1/chat` mit `text`, derselben `gespraech_id` und `"bilder": [<foto aus Schritt 1>]`.

```http
POST /v1/chat
{"text": "Was für eine Therme ist das, und was heißt F.28?", "gespraech_id": "c8719cc52c8f4d0a",
 "bilder": ["Notizen/Sascha/2026-09-26 Kundendienst Müller/2026-09-26 10-14-03.jpg"]}
```
Antwort wie immer (`ChatAntwort`). Das Foto steht in `dateien`; die Anleitungsseiten, aus denen zitiert wurde, ebenfalls
(als PDF antippbar, `GET /v1/datei`). Die Typenschilddaten schreibt der Assistent selbst in die Notiz
(„Anlage: <Hersteller> <Typ>“).

Empfehlung UI: im Chat neben dem Kamera-Knopf eine Aktion **„Foto fragen“** (bzw. nach dem Aufnehmen die Wahl
„nur ablegen“ / „Assistent fragen“). Bei „Assistent fragen“ Schritt 1+2 hintereinander, Eingabefeld/Sprachaufnahme
für die Frage; ohne eigene Frage als `text` z.B. „Bitte Typenschild ablesen.“ schicken. Die Antwort kann bei
Fehlercodes 10–40 s dauern (Lese-Timeout wie beim Chat).

- Bis zu 4 Bilder je Nachricht (z.B. Typenschild + Display). Nur JPEG/PNG/WebP aus der Ablage.
- Fehler: `404` Bildpfad nicht gefunden, `422` kein Foto / gesperrter Bereich / mehr als 4 Bilder – `detail` anzeigen.
- Ohne `bilder` geht es auch: Wurde das Foto mit `gespraech_id` hochgeladen, genügt „Was steht auf dem Typenschild?“ –
  der Assistent holt sich das zuletzt gemeldete Foto selbst. `bilder` ist aber eindeutiger und schneller.
- Im Verlauf (`GET /v1/gespraeche/{id}`) trägt die `nutzer`-Nachricht die Bilder in `dateien` → als Vorschaubild anzeigen.
- Neue Ja/Nein-Frage mit `werkzeug = "anleitung_herunterladen"`: Der Assistent schlägt vor, ein Hersteller-PDF in die
  Bibliothek zu übernehmen. Wie jede andere Bestätigung behandeln (Abschnitt 2).

### Ziel auswählen: `GET /v1/notizen`

Eigene Notizen und Ordner, jeweils zuletzt geändert zuerst – für einen Auswahldialog „Wohin?“ beim Foto/Video
(und passend dazu beim Anheften eines Projekts):

```json
{"wurzel": "Notizen/Sascha",
 "ordner": [{"name": "Begehung Simon-Dach-Straße 46", "pfad": "Notizen/Sascha/Begehung Simon-Dach-Straße 46",
             "notizen": 1, "geaendert": "24.09.2026 13:40"}],
 "notizen": [{"titel": "2026-09-24 Begehung Simon-Dach-Straße 46",
              "pfad": "Notizen/Sascha/Begehung Simon-Dach-Straße 46/2026-09-24 Begehung Simon-Dach-Straße 46.md",
              "ordner": "Begehung Simon-Dach-Straße 46", "geaendert": "24.09.2026 13:40"}]}
```

Jeder `pfad` (Ordner oder Notiz) geht direkt als `notiz` an `/v1/fotos` bzw. `/v1/videos`. Die Ordner, die der
Server selbst für die Fotos einer Notiz anlegt, stehen nicht in `ordner`. Neue Ordner/Notizen entstehen per Chat
(„Leg im Ordner Hotel eine Notiz Bäder an“) oder im Netzlaufwerk.

Empfehlung für die App:
- Vor dem Hochladen selbst auf ~2560 px / JPEG 85 % verkleinern (spart Datenvolumen auf der Baustelle).
- Bei Funkloch **Warteschlange** statt Fehlermeldung: Fotos lokal halten und nachsenden (z.B. WorkManager mit
  Netzwerk-Bedingung), Reihenfolge beibehalten. Achtung: Ohne `notiz` gilt die neueste Notiz *zum Zeitpunkt des
  Hochladens* – für nachgesendete Fotos also `notiz` aus der ersten Antwort mitschicken.
- Vorschau: Foto über `GET /v1/datei?pfad=<foto>` laden.
- `403` = fremde Notiz oder gesperrter Bereich, `404` = Notiz/Gespräch nicht gefunden, `415` = kein lesbares Bild.

### Videos

`POST /v1/videos` (multipart, **ein Video pro Anfrage**). Felder wie bei Fotos (`notiz`, `text`, `gespraech_id`), dazu:

| Feld | Pflicht | Bedeutung |
|---|---|---|
| `video` | ja | MP4 (H.264/HEVC) oder MOV, **max. 1 GB, max. 30 Minuten** |
| `abschreiben` | nein | `auto` (Standard): der Server prüft kostenlos, ob gesprochen wird, und schreibt nur dann ab · `immer` · `nie` (nur ablegen) |

```json
{"video": "Notizen/Sascha/2026-09-24 Objektbegehung Hotel/2026-09-24 13-30-12.mp4",
 "notiz": "Notizen/Sascha/2026-09-24 Objektbegehung Hotel.md", "notiz_neu": false,
 "dauer_s": 292.4, "abschrift": "laeuft"}
```

Der Server übernimmt Bild und Ton **unverändert** (kein Umrechnen, Hochkant-Drehung bleibt), entfernt Metadaten
(GPS, Aufnahmezeit) und Zusatzspuren und hängt an die Notiz `## Video <Zeit> (<Länge>)` mit Link an.
Die Abschrift läuft **im Hintergrund** (Sekunden bis wenige Minuten): Bis dahin steht in der Notiz
„*Gesprochenes wird abgeschrieben …*“, danach „**Gesprochen:**“ mit dem Text – oder nichts, wenn niemand spricht.
Mit `gespraech_id` kommt die Abschrift zusätzlich als `hinweis` ins Gespräch (per `GET /v1/gespraeche/{id}` sichtbar).

Empfehlung für die App:
- **Vor dem Hochladen auf 720p verkleinern** (Android Media3 `Transformer`, H.264, ~2–3 Mbit/s → 5 Min ≈ 60–100 MB
  statt 600 MB). Der Server rechnet bewusst nicht um (keine Grafikkarte).
- Upload im Hintergrund (WorkManager, Netzwerk-Bedingung) mit Fortschrittsanzeige; `notiz` aus dem ersten Upload
  mitschicken, damit nachgesendete Videos an der richtigen Notiz landen. Lese-Timeout großzügig (≥ 300 s).
- Schalter in der Aufnahme-Ansicht: „Gesprochenes abschreiben: automatisch / immer / nie“ (Standard automatisch).

### Dokumente – neu in 1.12.0

Bisher konnten nur Fotos und Videos geschickt werden. Für Angebote braucht der Assistent aber oft
**Leistungsverzeichnis, Pläne, Kundenmail oder Excel-Listen** – dafür gibt es jetzt `POST /v1/dokumente`
(multipart, **ein Dokument pro Anfrage**). Felder wie bei Fotos (`notiz`, `text`, `gespraech_id`), dazu:

| Feld | Pflicht | Bedeutung |
|---|---|---|
| `dokument` | ja | PDF, Word `.docx`, Excel `.xlsx`/`.xlsm`, `.txt`, `.md`, `.csv`, `.tsv`, E-Mail `.eml` – **max. 50 MB**. Der **Dateiname mit Endung** muss mitkommen (daran erkennt der Server den Typ) |

```json
{"dokument": "Notizen/Sascha/2026-10-12 Angebot Petersburger 44/LV Bad.pdf",
 "notiz": "Notizen/Sascha/2026-10-12 Angebot Petersburger 44.md", "notiz_neu": false,
 "lesbar": true, "hinweis": ""}
```

Der Server speichert die Datei **unverändert unter ihrem Originalnamen** neben der Notiz (gleicher Name → „(2)“) und
hängt an die Notiz `## Dokument <Zeit>` mit Link an. Mit `gespraech_id` erfährt der Assistent davon und liest das
Dokument selbst – danach z. B. im Chat: „Mach mir daraus ein Angebot“.
`lesbar: false` = abgelegt, aber ohne lesbaren Text (Scan, Passwortschutz) → `hinweis` anzeigen; Scans besser als
Foto schicken (Abschnitt 4a), die kann der Assistent ansehen.
Fehler: `415` falscher Typ (alte `.doc`/`.xls` → `detail` sagt „bitte als PDF/.docx/.xlsx speichern“) · `413` zu groß.
**Neu in 1.14.0:** Ohne `gespraech_id` (Dokument in einen *neuen* Chat geworfen) bekommt die **nächste Chat-Nachricht
dieser Person innerhalb von 30 Minuten** das Dokument automatisch mitgeteilt – „mach Angebot“ direkt danach funktioniert
also auch ohne Gespräch. Steht die App in einem Gespräch, trotzdem immer `gespraech_id` mitschicken.

Empfehlung für die App: Im Anhang-Menü neben Kamera/Video **„Dokument“** (Android `ACTION_OPEN_DOCUMENT`,
MIME-Typen `application/pdf`, `application/vnd.openxmlformats-officedocument.*`, `text/*`, `message/rfc822`) und
**Teilen-Ziel** („Teilen → Chef-Assistent“ aus Mail-App/Dateien) für dieselben Typen. Dateiname aus
`OpenableColumns.DISPLAY_NAME` übernehmen. Upload wie bei Videos mit Fortschritt; Typ-Prüfung macht der Server.

### HERO-Projekt anheften (HERO-Mappe) – neu in 1.19.0

Anlass (15.10.2026): Bei einer Begehung zu einem Projekt, das es in HERO schon gibt, sollen **alle Daten und Bilder
automatisch ins HERO-Projekt** – nicht erst von Hand hochgeladen werden. Bisher ließen sich nur eigene Notizordner und
GU-Projekte anheften; ein selbst erdachter Ordner im HERO-Archiv wurde nie nach HERO übertragen.

**So funktioniert es:** Zu jedem HERO-Projekt bekommt jede Person eine **HERO-Mappe** in ihrem Notizordner,
z. B. `Notizen/Sascha/SAN-660 Firma WEG Trelleborger Straße 14 in 13189 Berlin` (der Name beginnt mit der
Projektnummer). Die Mappe ist ein ganz normaler Notizordner – Notizen, Fotos, Videos, Dokumente, Begehungsprotokolle,
auch Unterordner wie „Keller“. Der Server kopiert **alles, was darin neu entsteht**, in
`HERO-Archiv/<Projekt>/Nach HERO hochladen`; der HERO-Spiegel lädt es von dort ins HERO-Projekt (werktags alle 5 Minuten):

| Was | Wann nach HERO |
|---|---|
| Fotos, Dokumente ≤ 25 MB | nach 1 Minute Ruhe (fertig geschrieben) |
| Notizen (`.md`) | **als PDF**, sobald sie **15 Minuten unverändert** sind; spätere Nachträge kommen als neuer Stand „… (Stand 15.10.2026 14-32).pdf“ |
| **Videos** | **gehen nicht nach HERO** – HERO weist Videos ab. Sie bleiben in der Mappe auf dem Server (`status: video`); die Abschrift steht in der Notiz und kommt mit deren PDF nach HERO |
| Andere Dateien > 25 MB | bleiben nur auf dem Server (`status: zu_gross`) |
| Datei liegt schon im HERO-Projekt | wird nicht doppelt hochgeladen (`status: schon_in_hero`) |

Was schon vor dem Verknüpfen in einem Ordner mit Projektnummer lag, bleibt, wo es ist (kein Altbestand nach HERO).

`POST /v1/hero-mappe` – Mappe holen bzw. beim ersten Mal anlegen:

```json
{"projekt": "SAN-660"}
```
`projekt` darf die Nummer (`SAN-660`, `san 660`), der Ordnername oder der Pfad im HERO-Archiv sein (z. B.
`projekt.pfad` aus `/v1/suche`). Antwort:

```json
{"name": "SAN-660 Firma WEG Trelleborger Straße 14 in 13189 Berlin",
 "pfad": "Notizen/Sascha/SAN-660 Firma WEG Trelleborger Straße 14 in 13189 Berlin",
 "projekt": "SAN-660", "hero_ordner": "HERO-Archiv/SAN-660 Firma WEG Trelleborger Straße 14 in 13189 Berlin",
 "dateien": [{"quelle": "Notizen/Sascha/SAN-660 …/2026-10-15 09-12-04.jpg", "ziel": "2026-10-15 09-12-04.jpg",
              "status": "kopiert", "zeit": "2026-10-15T07:13:10+00:00"}]}
```
`404` = kein (eindeutiges) HERO-Projekt zu der Angabe. `GET /v1/hero-mappe?projekt=SAN-660` liefert denselben Stand,
legt aber nichts an (`404`, solange es keine Mappe gibt).

Anheften wie bei Notizordnern:
- Chat-Nachrichten beginnen mit `Bezieht sich auf Projekt <name>.` (`name` aus der Antwort). Der Server erkennt
  dabei auch HERO-Ordnernamen und die bloße Nummer und legt die Mappe notfalls selbst an.
- Fotos/Videos/Dokumente mit `notiz = <pfad>` schicken. Auch ein Pfad im HERO-Archiv
  (`HERO-Archiv/SAN-660 …`) oder die Nummer als `notiz` landet in der Mappe – nie im Spiegel selbst.
- `GET /v1/notizen` liefert die Mappen wie andere Ordner, mit neuem Feld **`hero_projekt`** (`"SAN-660"`, sonst
  `null`) → Abzeichen „HERO“ zeigen.
- Ohne App: Im Chat „Begehung zu SAN-660, Keller: …“ – der Assistent legt die Notiz in die Mappe (`SAN-660/Keller`).

## 4b. Versicherungsberichte (Hintergrundaufträge) – neu in 1.6.0

Der Assistent erstellt professionelle Schadenschilderungen für Versicherungen/Makler (Was/Wo/Wann/Warum, Chronologie,
Ursache, Maßnahmen, Kosten, Fotos, Anlagen). Er wertet dafür alle Unterlagen eines Projekts aus (Baustellenberichte,
Angebote, Rechnungen, Fremdbelege, Fotos) – das dauert **5–20 Minuten** und läuft deshalb **auf dem Server im
Hintergrund**. Handy aus, kein Empfang, App geschlossen: egal. Auch ein Neustart des Servers setzt an derselben Stelle
fort; bei KI-/Netzfehlern versucht der Server es selbst erneut (nach 0,5 / 2 / 10 / 30 Min).

**Gestartet wird im Chat** (keine neue Eingabemaske nötig), z.B. „Mach für SAN-653 einen Versicherungsbericht an
Ricardo von der AXA, eigener Schaden“ – oder ohne Projekt: Fotos mit `/v1/fotos` an eine Notiz hängen, im Chat den
Hergang erzählen und „daraus einen Versicherungsbericht“ sagen. Die Chat-Antwort kommt sofort („läuft …“).

Was die App dafür braucht:
1. **Aufträge-Ansicht** (z.B. Menüpunkt oder Symbol mit Zähler): `GET /v1/auftraege` → Liste.
2. Solange ein Auftrag `wartet`/`laeuft` und die Ansicht (oder der Chat) offen ist: alle 15–30 s neu abfragen.
   Beim App-Start einmal abfragen und fertige, noch nicht gesehene Aufträge hervorheben (IDs lokal merken).
3. Fertig → `dateien` als antippbare Einträge (wie im Chat, `GET /v1/datei`), `offene_punkte` als Checkliste
   anzeigen („Vor dem Versand prüfen“), `zusammenfassung` als Text.
4. Knöpfe: `abbrechen` (bei wartet/laeuft), `wiederholen` (bei fehler/abgebrochen).
5. Ist `gespraech_id` gesetzt, steht das Ergebnis zusätzlich als `hinweis` im Gesprächsverlauf.

```json
GET /v1/auftraege
[{"id": "3f9c0a1b22de", "art": "versicherungsbericht", "titel": "SAN-653 Wasserschaden Hilbert",
  "status": "laeuft", "schritt": "Unterlagen auswerten (7/12)", "fortschritt": 31,
  "erstellt": "2026-09-26T13:02:11+00:00", "geaendert": "2026-09-26T13:04:40+00:00",
  "dateien": [], "zusammenfassung": null, "offene_punkte": [], "fehler": null, "gespraech_id": "c8719cc52c8f4d0a"}]
```
`status`: `wartet` (Warteschlange oder neuer Versuch nach Fehler – dann steht der Fehler in `fehler`) · `laeuft` ·
`fertig` · `fehler` (endgültig, `fehler` anzeigen, „Neu starten“ anbieten) · `abgebrochen`.
`schritt` ist fertiger Text zum Anzeigen, `fortschritt` grob in Prozent (für einen Balken).

Ergebnis (`dateien`, in dieser Reihenfolge): Bericht-PDF · Bericht mit allen Anlagen (Belege + weitere Fotos) als ein
PDF · Arbeitsnotizen (.md, nur intern: offene Punkte, ausgewertete Unterlagen). Abgelegt in
`Notizen/<Name>/Versicherungsberichte/<Datum> <Titel>/`. Änderungswünsche einfach im Chat sagen („im Bericht die
Schadennummer AX-123 eintragen“) → neuer Auftrag, Fassung `v2` im selben Ordner.

## 4b-2. Begehungsprotokolle – neu in 1.8.0

Zweite Auftragsart in derselben Aufträge-Ansicht (`GET /v1/auftraege`): `art: "begehungsprotokoll"`. Vor Ort wird wie
bisher frei aufgenommen (Videos mit Gesprochenem, Fotos mit Bildunterschrift, Notizen im Begehungsordner). Im Chat:
„Fass die Begehung Petersburger zusammen“ → der Server erstellt im Hintergrund (einige Minuten) ein kurzes,
tabellarisches PDF als Angebotsgrundlage und legt es **in denselben Ordner**.

Für die App ändert sich fast nichts:
- `art` kann jetzt auch `begehungsprotokoll` sein → als Titel/Symbol z.B. „Begehung“ statt „Versicherung“ anzeigen
  (unbekannte Werte bitte generisch anzeigen, nicht abstürzen).
- `dateien` bei fertig: `[PDF, „… – offene Punkte.md“]`. `offene_punkte` wie gehabt als Checkliste anzeigen.
  Die offenen Punkte erscheinen zusätzlich als Aufgaben in `GET /v1/aufgaben`.
- Abbrechen/Wiederholen wie beim Versicherungsbericht.

## 4c. Aufgaben – „Was ist offen?“ – neu in 1.7.0

Notizen können Aufgaben enthalten. Der Assistent erkennt sie beim Notieren („… und ich muss noch Pico klären“) und
schreibt sie als Häkchen-Zeilen in die Notiz (`- [ ] Pico klären (bis 30.09.2026)`). Aufgaben können an andere
vergeben sein („Patrick soll das Angebot freigeben“ → steht in Patricks Übersicht). **Anlegen läuft über den Chat**,
die App braucht dafür nichts Neues. Abhaken geht per Chat („Pico ist geklärt“), in der App (unten) oder von Hand in
der .md-Datei im Netzlaufwerk – alles zählt gleich.

Es gibt bewusst **keine Push-Erinnerung**. Stattdessen fragt der Assistent einmal am Tag im ersten neuen Gespräch nach,
was lange liegt, und die Übersicht markiert solche Aufgaben mit `nachfragen: true`. Notizen, deren Aufgaben alle
erledigt sind, wandern 14 Tage nach der letzten Änderung nach `Notizen/<Name>/Archiv/<Jahr>/` – sie tauchen dann in
`GET /v1/notizen` nicht mehr auf (die Suche im Chat findet sie weiter).

Was die App dafür braucht:
1. **Ansicht „Offen“** (Menüpunkt, gern mit Zähler `offen` als Badge): `GET /v1/aufgaben` → Gruppen in dieser
   Reihenfolge anzeigen, leere weglassen: `ueberfaellig` (rot) · `diese_woche` · `spaeter` · `ohne_datum` ·
   `an_andere` („An andere vergeben“) · `heute_erledigt` (durchgestrichen, Haken gesetzt).
2. Je Aufgabe: Häkchen, `text`, darunter klein `bis` (TT.MM.), bei `an_andere` „für `fuer`“, sonst wenn `von` ≠ ich
   „von `von`“, und die Notiz (Dateiname ohne .md) antippbar (`GET /v1/datei?pfad=<notiz>&anzeigen=true`).
3. Häkchen → `POST /v1/aufgaben/{id}` mit `{"status": "erledigt"}` bzw. `{"status": "offen"}` zum Zurücknehmen.
4. `nachfragen: true` → Hinweis „Noch aktuell?“ mit zwei Knöpfen: **Bleibt** = `{"noch_aktuell": true}` (14 Tage Ruhe),
   **Weg damit** = `{"status": "gestrichen"}`. Optional „Neue Frist“ = `{"bis": "2026-10-02"}` (`""` entfernt sie).
5. Nach jedem POST und nach jeder Chat-Antwort die Übersicht neu laden – **die ID kann sich ändern**, wenn jemand den
   Text ändert (dann 404 → einfach neu laden).
6. Optional `?person=patrick`: Übersicht einer anderen Person (alle sehen alles, jeder darf abhaken – in der Notiz
   steht dann „erledigt 28.09.2026 von Sascha“).

```json
GET /v1/aufgaben
{"person": "Sascha", "stand": "2026-09-28", "offen": 3,
 "ueberfaellig": [{"id": "08959e5d50", "text": "Pico klären: Trocknungsfirma schon drin?", "status": "offen",
   "bis": "2026-09-26", "fuer": null, "von": "Sascha", "notiz": "Notizen/Sascha/2026-09-24 SAN-1191.md",
   "seit": "2026-09-24", "erledigt_am": null, "erledigt_von": null, "nachfragen": true}],
 "diese_woche": [], "spaeter": [], "ohne_datum": [ … ],
 "an_andere": [{"id": "5b0e…", "text": "Angebot ANG-797 freigeben", "fuer": "Patrick", "von": "Sascha", … }],
 "heute_erledigt": [{"id": "…", "status": "erledigt", "erledigt_am": "2026-09-28", "erledigt_von": "Patrick", … }]}

POST /v1/aufgaben/08959e5d50   {"status": "erledigt"}
→ 200 {"status": "geändert", "aufgabe": { …wie oben, "status": "erledigt"… }}
```
Fehler: 404 = Aufgabe nicht (mehr) gefunden bzw. unbekannte Person · 422 = ungültige Eingabe (z.B. Datum).

## 4d. GU-Plantafel – neu in 1.9.0

Für Baustellen, die wir als Generalunternehmer betreuen. Jedes GU-Projekt ist ein **gemeinsamer Ordner**
`GU-Projekte/<Projekt>` (alle Nutzer dürfen dort notieren, Fotos/Videos anhängen) und hat eine **eigene Plantafel**:
Zeilen = Gewerke (je eigene Farbe), Balken = Einsätze (ganztägig, von–bis). Es wird nichts nach HERO oder in die
Mitra-Plantafel geschrieben.

**Fotos/Videos in ein GU-Projekt:** `GET /v1/notizen` liefert die GU-Projektordner jetzt mit in `ordner`
(`pfad` beginnt mit `GU-Projekte/`) und deren Notizen in `notizen` (dort ist `ordner` der volle Pfad des Ordners).
Als `notiz` bei `/v1/fotos` bzw. `/v1/videos` einfach den Pfad übergeben – wie bei eigenen Ordnern.
Tipp für die Auswahl: GU-Projekte als eigene Gruppe „GU-Projekte“ anzeigen.

**Plantafel in der App** (Vorschlag; die Weboberfläche hat sie schon, „🏗 GU-Plantafel“):
1. Liste `GET /v1/bauablauf` → Projekt wählen → `GET /v1/bauablauf/{id}`.
2. Zeitleiste: je Gewerk eine Zeile (`gewerke`, Reihenfolge beibehalten), Balken aus `einsaetze` in `farbe`.
   **`bis` ist einschließlich** (Kalender-Bibliotheken wollen meist `bis + 1 Tag` als Ende).
   `status`: `geplant` (gestreift/blasser) · `bestaetigt` (voll) · `fertig` (gedämpft, ✓) · `abgesagt` (ausblenden).
3. `warnungen` oben als gelbe Hinweise, betroffene Balken (`einsaetze` = IDs) markieren.
4. Antippen eines Balkens → Details (Firma/Telefon aus dem Gewerk – Telefon antippbar) und Bearbeiten
   (`PATCH`, nur geänderte Felder). Verschieben: neues `von` genügt, die Dauer bleibt. Hat der Einsatz Nachfolger
   (andere Einsätze mit `nach` = seine ID), fragen „Nachfolgende mitverschieben?“ → `folgende_mitverschieben: true`.
5. Nach jeder Änderung die Tafel neu laden. Gesamtansicht optional über `GET /v1/bauablauf/alle`.
6. „PDF“ → `POST …/pdf` → `pfad` öffnen (`GET /v1/datei`).

Im Chat geht alles auch per Sprache („Trockenbauer bei Grünberger vom 12. bis 16.10., danach eine Woche Fliese“).

```json
GET /v1/bauablauf/3
{"projekt": {"id": 3, "name": "Grünberger Str. 10", "ordner": "GU-Projekte/Grünberger Str. 10",
             "von": "2026-10-09", "bis": "2026-10-20", "einsaetze": 2},
 "gewerke": [{"id": 7, "name": "Trockenbau", "firma": "Bau Nowak", "kontakt": "Herr Nowak", "telefon": "0170 1234567",
              "farbe": "#718096"}],
 "einsaetze": [{"id": 12, "gewerk": "Trockenbau", "gewerk_id": 7, "farbe": "#718096", "firma": "Bau Nowak",
                "von": "2026-10-09", "bis": "2026-10-13", "was": "Vorwände", "bereich": "WE 1 Bad",
                "status": "bestaetigt", "nach": null}],
 "warnungen": [{"art": "reihenfolge", "einsaetze": [13, 12], "text": "Fliesen beginnt Mo 12.10., aber Trockenbau ist erst Di 13.10. fertig."}]}

PATCH /v1/bauablauf/einsaetze/12   {"von": "2026-10-12", "folgende_mitverschieben": true}
→ {"status": "geändert", "einsatz": {…}, "warnungen": […], "mitverschoben": [{…}]}
```
`warnungen[].art`: `reihenfolge` (beginnt vor Ende des Vorgängers) · `ueberschneidung` (zwei Gewerke gleichzeitig im
selben Bereich) · `unbestaetigt` (beginnt in ≤ 7 Tagen, noch „geplant“) · `ueberfaellig` (vorbei, nicht „fertig“).
Fehler: 404 = nicht gefunden · 422 = ungültig (Datum, bis vor von, Gewerk mit Einsätzen löschen …).

## 4e. Angebote (Angebotsgenerator) – neu in 1.11.0

Angebote entstehen **im Chat**: „Erstell mir aus den Unterlagen im Ordner … ein Angebot“ (Plan, Leistungsverzeichnis,
Kundenwünsche). Der Assistent nutzt dafür den Mitra-Angebotsgenerator auf dem Server: Standardpakete (WC, Waschtisch,
Dusche …) mit unseren meistverbauten Großhändler-Artikeln, Heizkörperauslegung nach Fläche und Fensternische,
Pauschalen (Monteur, Material, Demontage) aus dem Leistungsverzeichnis. Ergebnis ist eine **UGL-Datei** für den
CRM-Import. Ändern geht ebenfalls im Chat („Gäste-WC bitte clivia statt one“, „Heizkörper Zimmer 2 doch Typ 33“) –
dieselbe `gespraech_id` weiterverwenden, dann weiß der Assistent, um welches Angebot es geht.

Die App braucht dafür nur **lesen + herunterladen**:
1. Liste `GET /v1/angebote` → antippen → `GET /v1/angebote/{id}`.
2. Darstellung wie ein Angebot: je Eintrag in `gruppen` eine Überschrift (`nr` + `titel`; `ebene` 1 = Bereich,
   2 = Unterbereich eingerückt), darunter die `positionen`. **Beträge und Mengen sind Strings** mit Punkt als
   Dezimaltrenner (`"109.91"`) – zum Rechnen in `BigDecimal` umwandeln.
3. **Markierungen** (`markierung` an der Position, `null` = keine): `pruefen` rot · `alternative` gelb
   (Ersatz für einen LV-Artikel, den der Großhändler nicht führt) · `info` blau (z. B. Rechenweg einer Pauschale).
4. **„Zu prüfen“** oben oder als eigener Reiter: offene `fragen` (`erledigt=false`; `positionen_nr` = betroffene
   Positionen → antippen springt zur Position) und `markiert` (alle markierten Positionen).
5. Kopf: `ek_summe` = Material im Einkauf (der Aufschlag wird nach dem Import im CRM gesetzt),
   `pauschalen_summe` = Pauschalen als Verkaufspreis.
6. „UGL“ → `GET /v1/angebote/{id}/ugl` → Datei speichern bzw. über das Android-Teilen-Menü weitergeben
   (Dateiname aus `Content-Disposition`). Alternativ im Chat „leg die UGL in den Projektordner …“.
7. `vorschau` ist der Link zur Web-Vorschau (Bearbeiten am PC); nur anzeigen, wenn gesetzt.

```json
GET /v1/angebote
[{"id": "b35a08287b405d8d", "titel": "PET44 WE 01 – Petersburger Str. 44", "positionen": 55, "geaendert": "2026-10-09T13:30:12"}]

GET /v1/angebote/b35a08287b405d8d
{"id": "b35a08287b405d8d", "titel": "PET44 WE 01 – Petersburger Str. 44", "bearbeiter": "Sascha Richter", "version": 21,
 "geaendert": "2026-10-09T13:30:12", "ek_summe": "9195.69", "pauschalen_summe": "4468.00",
 "vorschau": "https://angebote.mitra-sanitaer.de/a/b35a08287b405d8d",
 "gruppen": [
  {"nr": "1", "titel": "Arbeits- und Beschaffungskosten", "ebene": 1, "bereich": "Arbeits- und Beschaffungskosten", "unterbereich": "",
   "positionen": [{"id": "p52", "nr": "1._.001", "art": "ANF", "menge": "1", "name": "Anfahrtskosten", "ek": "0", "lp": "0",
                   "ek_gesamt": "0", "vk": "250.00", "vk_gesamt": "250.00", "pauschale": true, "bemerkung": "",
                   "quelle": "pauschale/anfahrt", "genutzt": 0, "markierung": {"art": "info", "text": "Rechenweg: …"}}]},
  {"nr": "2.3", "titel": "Waschtisch", "ebene": 2, "bereich": "Badezimmer", "unterbereich": "Waschtisch",
   "positionen": [{"id": "p17", "nr": "2.3.004", "art": "SEVEN", "menge": "1", "name": "Einhand Waschtischbatterie Seven …",
                   "ek": "114.5", "lp": "173.5", "ek_gesamt": "114.50", "bemerkung": "Alt. zu LV: Duravit DU C.1",
                   "quelle": "waschtisch_up/standard:vorgabe set", "genutzt": 16,
                   "markierung": {"art": "alternative", "text": "Alternative zu LV: Duravit DU C.1"}}]}],
 "fragen": [{"id": "f2", "text": "Ablauf und Füße … passen sie zur Alternative?", "erledigt": false,
             "positionen": ["p23"], "positionen_nr": ["2.4.002"], "gruppe_nr": ""}],
 "markiert": [{"id": "p17", "nr": "2.3.004", "art_nr": "SEVEN", "name": "Einhand Waschtischbatterie Seven …",
               "art": "alternative", "text": "Alternative zu LV: Duravit DU C.1"}],
 "hinweise": ["Paket 'Dusche mit Duschwanne' ist ein Entwurf – Artikelauswahl von Mitra noch nicht bestätigt."]}
```
Pauschal-Positionen erkennt man an `"pauschale": true` – dort `vk`/`vk_gesamt` statt `ek` anzeigen.

### Entscheidungen – neu in 1.15.0 (ersetzt die Liste „Zu prüfen“)

Bisher kamen `fragen`, `markiert` und `hinweise` als drei Listen ohne Zusammenhang – man musste zwischen Frage und
Position hin- und herscrollen, und interne Vermerke („Paket … ist ein Entwurf“) störten. Neu liefert
`GET /v1/angebote/{id}` dazu **`punkte`**: genau die Dinge, die ein Mensch entscheiden muss, **mit den betroffenen
Positionen eingebettet** und mit Antwortvorschlägen. Die alten Felder bleiben (Rückwärtskompatibilität), die App
soll aber nur noch `punkte` zeigen.

```json
"punkte": [
 {"id": "f3", "art": "frage", "ort": "Badezimmer / Dusche",
  "text": "Duscharmatur: Unterputz-Thermostat oder Aufputz?",
  "positionen": [{"id": "p22", "nr": "5.4.002", "art_nr": "D-CODE-AB", "name": "Duravit Duschwannenablauf …",
                  "menge": "1", "ek": "42.75", "gesamt": "42.75", "pauschale": false, "bemerkung": "", "ort": "Dusche"}],
  "optionen": ["UP-Thermostat hansgrohe ShowerSelect (EK 312 €)", "AP-Thermostat Grohtherm 800 (EK 145 €)"],
  "frei": true},
 {"id": "m:p21", "art": "alternative", "ort": "Dusche", "text": "Alternative zu LV: Duravit D-Code 1200x900",
  "positionen": [ … ], "optionen": [], "frei": true},
 {"id": "ek:p29", "art": "pruefen", "ort": "Dusche",
  "text": "Kein Einkaufspreis beim Großhändler – Artikel und Preis in HERO prüfen (evtl. Direktbezug).", … },
 {"id": "h:Heizpatrone: Elektroanschluss durch Elektriker.", "art": "hinweis", "text": "…", "positionen": [],
  "optionen": [], "frei": false}],
"beantwortet": [{"id": "f2", "text": "Ablauf und Füße …?", "antwort": "Füße tauschen", "positionen": ["p22"]}]
```

| Feld | |
|---|---|
| `art` | `frage` (rot) · `pruefen` (rot, unsichere Position) · `alternative` (gelb, Ersatz für LV-Artikel) · `hinweis` (grau, nur zur Kenntnis) |
| `ort` | Bereich/Unterbereich als Unterzeile |
| `positionen` | betroffene Positionen **schon mit Name, Menge, Preis** – als kleine Kachel in der Karte zeigen; Tippen springt zur Position. `pauschale=true`: `gesamt` ist VK, sonst EK |
| `optionen` | Antworten zum Antippen (je ein Knopf, untereinander). Kann leer sein |
| `frei` | `true` = zusätzlich ein Feld „Eigene Antwort …“ |

**Antworten:** `POST /v1/angebote/{id}/antworten` mit `{"punkt": "<punkte[].id>", "antwort": "<Text>"}`.
`antwort` ist eine der `optionen`, eigener Text oder **`"PASST"`** (Knopf „Passt so“, bei `hinweis` „Ausblenden“).
Antwort ist das aktualisierte Angebot (wie GET) – der Punkt ist dann aus `punkte` verschwunden; mit Text-Antwort
steht er in `beantwortet`, bis der Assistent ihn eingearbeitet hat. **Einarbeiten** passiert im Chat bei
„mach weiter“ (dieselbe `gespraech_id`) – die App kann nach dem Antworten einen Knopf **„Antworten einarbeiten“**
zeigen, der genau diesen Text in den Chat schickt. Fehler: `404` Angebot/Punkt unbekannt, `422` leere Antwort.

**Darstellung (Handy):** Entscheidungen **oben**, Positionen darunter. Je Punkt eine Karte: Kopfzeile `art` (farbig) +
`ort`, dann `text` fett, dann die Positions-Kacheln, dann die Optionen-Knöpfe, dann „Eigene Antwort“ + „Passt so“.
Ab 6 Punkten nur die ersten 5 zeigen und „weitere … anzeigen“. `beantwortet` eingeklappt als „wird eingearbeitet“.
Rechenwege (`markierung.art == "info"`) gehören **nicht** in die Entscheidungen, sondern eingeklappt („Rechenweg ▸“)
unter die Position. Positionen auf dem Handy als Karten, nicht als breite Tabelle.
Fehler: 404 = Angebot gibt es nicht · 502 = Angebotsgenerator nicht erreichbar („Nochmal versuchen“) ·
503 = auf diesem Server nicht eingerichtet (Menüpunkt ausblenden).

## 5. Kalender direkt

`GET /v1/termine?von=2026-09-24` → Termine dieses Tages. `bis=2026-09-30` (einschließlich). `person=patrick` oder `alle=true` für andere Kalender.

```json
{"von": "…", "bis": "…", "termine": [{"id": 1, "titel": "Besprechung Wärmepumpe – Familie Schulz",
  "person": "sascha", "wochentag": "Do", "beginn": "2026-09-24T09:30:00+02:00",
  "ende": "2026-09-24T10:30:00+02:00", "ganztag": false, "ort": "Lindenstr. 12", "notiz": null}]}
```
Anlegen/Ändern läuft über den Chat („Trag mir morgen 9:30 … ein“).

## 5a. Kalender mit HERO-Terminen – neu in 1.16.0

**Ziel:** Jeder hat seinen Kalender in der Tasche – die eigenen HERO-Termine (Plantafel) plus die Termine des
Assistenten – und kann Kollegen und Monteure dazuschalten. Quelle ist der HERO-Spiegel (werktags alle 5 Minuten
aktuell). Seit 1.18.0 lassen sich HERO-Termine **anlegen und verschieben** (Abschnitt 5b), wenn `hero_schreiben`
true ist. Eigene Termine des Assistenten werden wie bisher im Chat angelegt/geändert.

`GET /v1/personen` – für den Filter:
```json
{"ich": "hero:94314",
 "personen": [{"schluessel": "hero:94314", "name": "Patrick van Dalen", "hero_id": 94314, "app_benutzer": "patrick", "ich": true, "termine": 473},
              {"schluessel": "hero:130793", "name": "Sascha Richter", "hero_id": 130793, "app_benutzer": "sascha", "ich": false, "termine": 113},
              {"schluessel": "hero:106018", "name": "Julian Hoffmann", "hero_id": 106018, "app_benutzer": null, "ich": false, "termine": 519}]}
```
Reihenfolge: ich, andere App-Nutzer, dann Monteure nach Anzahl Termine. `ich: null` = keine HERO-Zuordnung (dann zeigt
„ich“ nur die eigenen Termine). `503` = HERO-Spiegel fehlt.

`GET /v1/kalender?von=2026-10-12&bis=2026-10-18&personen=ich,hero:106018`
- `von`/`bis` als `YYYY-MM-DD` (bis einschließlich, max. 62 Tage). `personen`: `ich`, `hero:<id>` (aus `/v1/personen`),
  kommagetrennt, oder `alle`. Standard `ich`.
```json
{"von": "2026-10-12", "bis": "2026-10-18", "personen": ["ich", "hero:106018"], "spiegel": true, "hero_schreiben": true,
 "termine": [{"id": "hero:616233015", "quelle": "hero", "titel": "Badsanierung Herr Böse",
   "beginn": "2026-10-12T14:30+02:00", "ende": "2026-10-12T15:30+02:00", "ganztag": false,
   "kategorie": "Umsetzung", "farbe": "#0B8043",
   "projekt": {"nr": "SAN-1166", "kunde": "Herr Dipl.-Kfm. Mark Boese", "titel": "Badsanierung 2. OG & 4. OG",
               "adresse": "Grimmstraße 7, 10967 Berlin", "pfad": "HERO-Archiv/SAN-1166 Herr Dipl.-Kfm. Mark Boese"},
   "personen": [{"hero_id": 94314, "name": "Patrick van Dalen", "app_benutzer": null}],
   "ort": null, "notiz": null, "nur_lesen": false},
  {"id": "eigen:12", "quelle": "eigen", "titel": "Zahnarzt", "beginn": "2026-10-13T16:00:00+02:00", "ende": "…",
   "ganztag": false, "kategorie": "Eigener Termin", "farbe": null, "projekt": null,
   "personen": [{"hero_id": null, "name": "Patrick", "app_benutzer": "patrick"}], "nur_lesen": false}]}
```
- Nach `beginn` sortiert. **Mehrtägige** Termine (z. B. Rufbereitschaft) erscheinen an jedem Tag, den sie berühren –
  dort Beginn und Ende mit Datum zeigen („5.10. 02:01 – 12.10. 01:59“). Ganztägig: `beginn`/`ende` nur Datum.
- `projekt.pfad` → Knopf „Projektordner“ (`GET /v1/ordner?pfad=…`).

**So soll es in der App aussehen** (Weboberfläche „📅 Kalender“ zeigt es schon so):
- Kopf: ‹ Heute › und Umschalter Tag/Woche; Wochenansicht als **Tagesliste** (Tagesüberschrift „Di 13.10. · heute“),
  beim Öffnen zu „heute“ scrollen.
- Darunter Chips: **Ich**, die anderen App-Nutzer mit Vornamen, „Monteure ▾“ (klappt alle übrigen auf), „Alle“.
  Mehrfachauswahl; Auswahl merken. Jede gewählte Person bekommt eine Farbe in fester Reihenfolge (dieselbe
  geprüfte Palette wie die Diagramme, API.md 1a) – als Punkt im Chip und als linker Rand ihrer Termine.
- Termin-Karte: links Zeit, rechts Titel fett, darunter „Kategorie · Projektnr Kunde“, darunter die Personen (Vornamen).
  `farbe` der HERO-Kategorie nur als dünner Strich über der Zeit. Antippen klappt auf: Adresse, alle Personen, Notiz,
  „📁 Projektordner“, bei HERO-Terminen mit `nur_lesen: false` der Knopf „🕑 Verschieben“ (5b), sonst Hinweis
  „Aus HERO – ändern nur in HERO“; bei eigenen „ändern im Chat“.
- Optional Android-Widget „Heute“: die nächsten 3 Termine von „ich“.

## 5b. HERO-Termine anlegen und verschieben – neu in 1.18.0

**Ziel:** Termine für sich und die Monteure direkt in HERO eintragen und verschieben – sie stehen sofort in der
HERO-Plantafel und in der HERO-App der Monteure. Nur wenn `GET /v1/kalender` → `hero_schreiben: true` (sonst `503`).

**Immer zwei Schritte** – erst prüfen, dann eintragen (der Server prüft beim Eintragen trotzdem alles noch einmal):
1. Anfrage mit `?nur_pruefen=true` → `{"status": "geprueft", "beschreibung": "…", "ueberschneidungen": […], "hinweis": null}`.
   Nichts wird geschrieben. Bestätigungsdialog zeigen: `beschreibung` und die **Überschneidungen** (Termine derselben
   Personen zur selben Zeit, live aus HERO). Überschneidungen sind ein Hinweis, kein Hindernis.
2. Dieselbe Anfrage ohne `nur_pruefen` → `{"status": "angelegt"|"verschoben", "termin": {…wie in /v1/kalender…},
   "ueberschneidungen": […]}`. Der Termin steht sofort in `/v1/kalender` (auch bevor der Spiegel ihn holt).

`GET /v1/kalender/kategorien` → `[{"id": 226842, "name": "Umsetzung", "farbe": "#009688"}, {"id": 359681, "name": "Kundendienst", …}, …]`
(nach Name sortiert). HERO verlangt eine Kategorie – im Formular Pflichtfeld ohne Vorauswahl.

**Anlegen** `POST /v1/kalender/hero`
```json
{"personen": ["hero:106018"], "titel": "Gasthermenwartung – Pappelallee 25",
 "beginn": "2026-10-20T08:00", "ende": "2026-10-20T09:00", "ganztag": false,
 "kategorie_id": 359681, "projekt": "SAN-1270", "notiz": "Kunde Müller, 0170 …, 3. OG"}
```
- `personen`: Schlüssel aus `/v1/personen` (`hero:<id>`) oder `ich`; mehrere = ein gemeinsamer Termin.
- Zeiten in **Ortszeit ohne Zone** (`YYYY-MM-DDTHH:MM`). `ende` leer = 1 Stunde.
- `ganztag: true` (Urlaub, Schule): `beginn`/`ende` nur Datum, `ende` = letzter Tag einschließlich; wird in HERO
  als 00:00–23:59 eingetragen (so macht es HERO selbst). Höchstens 14 Tage.
- `projekt`: HERO-Projektnummer (optional) – der Termin hängt dann am Projekt (Adresse, Projektordner).
- Dublettenschutz: Steht derselbe Titel zur selben Zeit schon bei der Person, kommt `422` statt eines zweiten Termins.

**Verschieben** `PATCH /v1/kalender/hero/{id}` (`id` = Zahl aus `"hero:<id>"`)
```json
{"beginn": "2026-10-21T10:00", "ende": null, "ganztag": false}
```
- Nur die Zeit ändert sich; Titel, Personen, Kategorie, Projekt bleiben. `ende` leer = Dauer bleibt.

**Fehler:** `422` mit verständlichem `detail` (Kategorie unklar, Projekt unbekannt, Ende vor Beginn, HERO-Fehler …) –
im Dialog anzeigen. `503` = auf diesem Server nicht eingerichtet.

**Im Chat** geht es ohne App-Änderung: „Trag Julian Montag 8 Uhr Wartung bei Müller ein“ → der Assistent schlägt den
Termin vor (mit Überschneidungen) → die bekannten Ja/Nein-Knöpfe (Abschnitt 2).

## 5c. Monteur-App – neu in 1.22.0

Eigene, schlanke App für die Monteure (Gradle-Flavor `monteur` mit eigener Paket-ID; Netz, Ausgangskorb und
Bausteine wie in der Büro-App). Grundsatz: **Handy raus, sagen oder tippen, was gemacht wurde – den Rest macht die KI.**

**Rolle.** Monteur-Zugänge (`GET /v1/ich` → `"rolle": "monteur"`) erreichen **nur** `/v1/monteur/…`, `/v1/ich`,
`/v1/abmelden` und `/v1/gesund`; alles andere → `403`. Büro-Zugänge (`"rolle": "buero"`) dürfen die
Monteur-Endpunkte zum Testen ebenfalls nutzen (mit ihrer eigenen HERO-Zuordnung; ohne Zuordnung `409`).

**Einrichtung (einmal je Handy).** Das Büro legt den Monteur an (`POST /v1/monteure` oder
`chef-assistent-benutzer monteur-anlegen igor "Igor Gregorczuk"` – der HERO-Mitarbeiter wird über den Namen gefunden,
nicht eindeutig → Liste mit IDs; `hero_partner` bzw. die ID als 3. Angabe ist optional) und zeigt den QR-Code. Inhalt:
`mitra-monteur:<Server-URL>#<Code>` (Code `ABCD-EFGH-JKLM`, einmal gültig, 7 Tage). Die App scannt, nimmt die
Server-URL vor dem `#` und ruft `POST /v1/monteur/einrichten {"code","geraet"}` → `{"token":"cs_…",…}`. Abtippen
des Codes muss auch gehen (Groß-/Kleinschreibung und Bindestriche egal). Falscher/benutzter/abgelaufener Code → `401`,
zu viele Versuche → `429`. Sitzung verfällt nach 60 Tagen ohne Nutzung → `401` → „Bitte im Büro neuen QR-Code holen“.

**Heute.** `GET /v1/monteur/tage?von=YYYY-MM-DD&bis=…` (max. 14 Tage, Standard heute) →
`[{"datum","wochentag","termine":[{"id":"hero:…","titel","beginn","ende","ganztag","kategorie","beschreibung",
"kollegen":[…],"projekt":{"id","nr","titel","kunde","adresse","geo":{"lat","lng"}|null,"telefon":[{"nummer","name","art"}]}|null,
"berichte":[{"id","status","hero_nr"}]}]}]`. `berichte` leer → „Bericht fehlt“. Adresse → Navigation (`geo:` bzw.
Maps-Intent), Telefon → Wählen.

**Projektmappe** (Termin antippen → Projekt, wie in HERO). `GET /v1/monteur/projekte/{projekt.id}` →
`{"id","nr","titel","kunde","adresse","geo","telefon",[…],"notizen": "<Notizfeld des Projekts in HERO>",
"unterlagen":[{"art","nr","name","datum","status","datei"}],"fotos":[{"uuid","name","datum"}],
"verlauf":[{"datum","titel","text"}],"videos":[…]}` (`verlauf` neu in 1.23.0, siehe [5d](#5d-monteur-app-ein-eingang-für-alles--neu-in-1230)). `unterlagen` =
alles außer Finanzen: Angebot (nur aktuelle Fassung), Auftragsbestätigung, Aufmaß, Unterlage (Pläne, hochgeladene
PDFs), Dokument, Bericht, Reparaturauftrag, Bestellschein, Lieferschein, Brief – nach Art, je Art neueste zuerst.
**Rechnungen, Stornos, Mahnungen und Kalkulationen sind für Monteure gesperrt** (auch beim direkten Dateiabruf).
Dateien: `GET /v1/monteur/dateien/{uuid}` (PDF/Bild inline), Fotos-Liste mit `?vorschau=true` (JPEG, 480 px).
Monteure sehen nur Projekte, auf denen sie eingeteilt sind (Termine 90 Tage zurück bis 30 voraus) oder zu denen sie
einen Bericht geschrieben haben – sonst `404`. `datei: null` = PDF ist noch nicht im Archiv (Spiegel lädt nach).

**Fotos, Dokumente, Videos vom Handy.** `POST /v1/monteur/projekte/{id}/dateien` (multipart `datei`, optional `text`
kurz → Dateiname, `anfrage_id`) → `{"art":"foto|dokument|video","name","ziel":"hero|server","hinweis"}`.
Fotos (JPEG/PNG/WebP, max. 25 MB; verkleinert auf 2560 px, ohne GPS) und Dokumente (PDF, Word, Excel, Text, max.
50 MB) kommen über „Nach HERO hochladen“ in **wenigen Minuten ins HERO-Projekt** (danach auch in `fotos`/`unterlagen`).
**Videos** (mp4, mov, …, max. 1 GB) bleiben **auf dem Server** im Projektordner `Videos` – HERO nimmt keine Videos –
und stehen in der Projektmappe unter `videos:[{"name","groesse_mb","datum"}]`, abspielen mit
`GET /v1/monteur/projekte/{id}/videos/{name}` (Spulen geht). Anderes → `415`, Projekt ohne Ordner → `409`.

**Bericht.** Ablauf wie Angebote, aber für den Monteur so kurz wie möglich:
1. `POST /v1/monteur/berichte` (multipart): `audio` **oder** `text`, optional `termin_id` / `projekt_nr` / `datum`,
   `anfrage_id` (= Berichts-ID, Ausgangskorb). **Ohne Termin und Projekt sucht der Server selbst** den Termin, bei
   dem der Monteur laut Plan gerade ist (läuft jetzt ±1 Std. > zuletzt begonnen > nächster); nachgetragene Tage → der
   letzte Termin ohne fertigen Bericht. Kein Termin mit Projekt an dem Tag → `422`.
   Diktat in **jeder Sprache**, auch Stichworte („WC demontiert weil undicht, Waschtisch kaputt deswegen neu, 3 Std“):
   die KI schreibt den Text „Ausgeführte Arbeiten“ in sauberem Deutsch (Fachsprache, vorlegbar bei Hausverwaltung/
   Versicherung), rechnet Stunden, legt bei nur genannter Dauer die Zeiten ab Terminbeginn, gleicht Material mit dem
   Katalog (Gut Mollenhauer) ab und fragt bei Unklarem nach. Antwort `MonteurBericht` mit `entwurf`:
   `taetigkeit, anstehend[], monteur_stunden, helfer_stunden, anfahrt, kleinmaterial, material[{text,menge,einheit,
   artikel{nr,name,einheit}|null, vorschlaege[], rueckfrage}], zeiten[{beginn,ende,art}], hinweise_buero[], rueckfragen[]`.
2. Korrigieren: per Sprache `POST …/{id}/diktat` (z. B. Antwort auf „Welches Eckventil, 1/2 oder 3/8?“) oder von
   Hand `PATCH …/{id}` (Felder wie im Entwurf; `material` = vollständige Liste `[{index, menge, text?, einheit?,
   artikel_nr?}]` – `artikel_nr` aus `vorschlaege` wählt, `null` = freie Position ohne Katalogabgleich).
   **Material von Hand:** `GET /v1/monteur/artikel?q=eckventil 1/2&max=20` → `[{"nr","name","einheit"}]` (Artikelstamm
   Gut Mollenhauer, bei uns oft genutzte zuerst, alle Wörter müssen vorkommen, exakte Artikelnummer trifft direkt,
   **keine Preise**; unter 2 Zeichen `[]`). Übernehmen als neue Position: `{"index": null, "menge": 2,
   "artikel_nr": "<nr>"}` in der `material`-Liste – geht in HERO als Katalogartikel (per IDS bestellbar).
3. Kunde unterschreibt (optional) → `POST …/{id}/abschliessen {"unterschrift_png": base64|data-URL|null,
   "unterschrift_name", "zeiten_buchen": true}`. Der Server legt einen **fertigen Baustellenbericht** in HERO an
   (BER-Nummer, PDF, Kunde als Empfänger, Positionen Kundendienst/Baustellenmonteur + Monteurhelfer in Std,
   Anfahrtskosten, Klein- & Dichtmaterial, Material als Katalogartikel → im Büro **per Klick in die Rechnung**),
   setzt die Unterschrift, hängt `hinweise_buero` als Notiz an und bucht `zeiten` in die Zeiterfassung (Status neu).
   Wiederholbar: nie ein zweiter Bericht. Offene Material-Rückfrage → `409`. Danach `status: "fertig"`, `hero_nr`;
   `hinweise[]` nennt, was nicht ganz geklappt hat (z. B. „Preis fehlt: …“) – anzeigen.

**Zeiten** (in HERO geführt). `GET /v1/monteur/zeiten?datum=` → `{"eintraege":[{"id","beginn","ende","art",
"status":"neu|eingereicht|bestaetigt","projekt_nr","kommentar","minuten"}],"arbeit_minuten","pause_minuten",
"eingereicht","stempel":{…}|null}`. Eintragen `POST /v1/monteur/zeiten {beginn,ende (Ortszeit),art,projekt_nr?,
kommentar?,anfrage_id?}`, ändern/löschen nur mit Status `neu` (sonst `409`). **Abends** „Tag bestätigen“ →
`POST /v1/monteur/zeiten/{datum}/einreichen` (danach nur noch im Büro änderbar). Stempeluhr:
`POST /v1/monteur/stempel {"aktion":"start|stopp|wechsel","art","projekt_nr?"}` (Uhrzeit nimmt der Server).
`GET /v1/monteur/stundenkonto?von=&bis=` → Minuten Soll/bestätigt/eingereicht/Saldo.

**Arten der Zeitabschnitte** (`art`; Regeln des Betriebs, stehen auch in der KI – seit 1.23.0 mit `lager`):

| `art` | Was | Projekt | Arbeitszeit | Dem Kunden berechnet |
|---|---|---|---|---|
| `lager` (neu in 1.23.0) | Laden/Beladen, Lager, Firma, Werkstatt – z. B. 07:00–07:45 | **ohne** | ja | nein |
| `fahrt` | Fahrt zum Kunden bzw. zwischen Kunden | – | ja | nein (steckt in der Anfahrtspauschale) |
| `arbeit` | Arbeit beim Kunden | mit | ja | ja |
| `material` | während des Einsatzes extra zum Händler und zurück | mit | ja | ja (als Arbeitszeit) |
| `pause` | Pause | – | nein | nein |

Fahrt von zu Hause zur Firma bzw. zur ersten Baustelle und die Heimfahrt zählen **nicht** (gar nicht eintragen).
Notdienst: von Motorstart zu Hause bis Ankunft zu Hause komplett Arbeitszeit (Fahrten `fahrt`, vor Ort `arbeit`).
Die Berichtsstunden (`monteur_stunden`) sind nur `arbeit` + `material`. In HERO ist `lager` dieselbe Kategorie wie
`arbeit`, nur ohne Projekt – `GET /v1/monteur/zeiten` liefert Arbeit ohne Projekt daher als `lager`. `buero`, `vor_ort`
(und beim Lesen `sonstige`) gibt es weiterhin für Einträge aus HERO bzw. von Hand.

**Urlaub.** `GET /v1/monteur/abwesenheiten?jahr=` → `{"stand":{"jahresurlaub","genehmigt","beantragt","verfuegbar"},
"eintraege":[{"id","art","status":"beantragt|genehmigt|abgelehnt","von","bis","halber_tag_anfang","halber_tag_ende",
"kommentar","tage"}]}`. Beantragen `POST {art: urlaub|ueberstunden|krank|kind_krank|sonderurlaub|unbezahlt, von, bis,
halber_tag_anfang?, halber_tag_ende?, kommentar?, anfrage_id?}`; zurückziehen `DELETE …/{id}?jahr=` (nur
unentschieden). Genehmigt wird im HERO-Web; HERO mailt die Entscheidung selbst.

**Sprache für Zeiten/Urlaub.** `POST /v1/monteur/verstehen` (multipart `audio`|`text`, `datum?`) →
`{"text","datum","zeiten":[…],"abwesenheit":{…}|null,"rueckfrage"}` – nur Vorschläge, die App zeigt sie zum
Bestätigen und bucht dann mit den Endpunkten oben („Feierabend, halbe Stunde Pause“, „nächste Woche Do und Fr frei“).
Seit 1.23.0 nimmt die App für den Mikrofon-Knopf den **Eingang** ([5d](#5d-monteur-app-ein-eingang-für-alles--neu-in-1230));
`verstehen`, `berichte`, `zeiten` und `abwesenheiten` bleiben unverändert für Änderungen von Hand.

## 5d. Monteur-App: ein Eingang für alles – neu in 1.23.0

Grundsatz (User 16.10.2026): **keine neuen Knöpfe.** Monteure haben es eilig – was erst überlegen lässt, welcher Knopf
der richtige ist, wird nicht benutzt. Es gibt **einen** Mikrofon-Knopf und die Kamera. Der Monteur sagt, tippt oder
fotografiert; die KI erkennt aus Inhalt, Uhrzeit und Tagesplan, was er will, und zeigt **eine Karte**. Bestätigt wird
immer mit **„Passt“**.

| Absicht (`art`) | Beispiel | Was bei „Passt“ passiert |
|---|---|---|
| `bericht` | „WC getauscht, 2 Std, zwei Eckventile“ | Fertiger Baustellenbericht in HERO (wie 5c), Fotos hängen am Bericht |
| `frage` | Foto vom Typenschild + „was heißt F.28?“ | Nichts – die Antwort ist das Ergebnis (Karte ist sofort `erledigt`) |
| `bestellung` | „Mir fehlen noch 3 Eckventile 1/2 Zoll“ | **fertiger Bestellschein** (BS-Nummer, Empfänger Gut Mollenhauer) im HERO-Projekt – Positionen per Klick ins Angebot/die Rechnung, Büro bestellt per IDS – + Kommentar im Projektverlauf |
| `meldung` | „Kunde nicht da“ (+ Foto der Tür), „komme 20 Min später“, „Wasserschaden entdeckt, Angebot nötig“ | **Kommentar im HERO-Projektverlauf**, Fotos ins HERO-Projekt |
| `zeiten` | „Feierabend, halbe Stunde Pause“ | Zeitabschnitte in HERO gebucht (Status neu) |
| `urlaub` | „Nächste Woche Donnerstag und Freitag frei“ | Antrag in HERO |

### Senden: `POST /v1/monteur/eingang` (multipart)

| Feld | | |
|---|---|---|
| `audio` | optional | Aufnahme (m4a/aac …, max. 25 MB), jede Sprache |
| `text` | optional | Getipptes; mit `audio` zusammen wird beides aneinandergehängt |
| `fotos` | optional, 0–5 | Feld mehrfach schicken; je max. 25 MB, nicht selbst verkleinern |
| `termin_id` | optional | Wenn der Monteur gerade in einem Termin (Projektmappe) ist |
| `projekt_nr` | optional | Statt Termin |
| `datum` | optional | `YYYY-MM-DD`, Standard heute (Nachträge) |
| `bericht_id` | optional | Berichtsentwurf, der gerade offen ist → Ergänzungen landen in **diesem** Entwurf |
| `bezug_id` | optional | Karten-ID einer Antwort (`frage`) → **Nachfrage** dazu |
| `anfrage_id` | empfohlen | 8–64 Zeichen `[A-Za-z0-9_-]`; wird zur **Karten-ID** (Ausgangskorb) |

Mindestens `audio`, `text` oder ein Foto, sonst `422`. Mehr als 5 Fotos → `422`, kein lesbares Foto → `415`,
zu groß → `413`, KI nicht erreichbar → `502`. Dauer: meist 5–30 s, eine Frage mit Internetsuche auch bis ~60 s.

**Absicht.** Die KI wählt genau eine Art. Ist `bericht_id` ein offener Entwurf und klingt es nach Ergänzung/Korrektur,
ist es `bericht`. Mit `bezug_id` auf eine Frage-Karte ist es immer `frage` (Nachfrage, die vorige Frage und Antwort
gehen mit). **Nur Fotos ohne Text** → `frage` („Was ist das, was muss ich wissen?“); `meldung` nur, wenn die KI das am
Foto eindeutig erkennt.

**Projekt.** Für `bericht`, `bestellung` und `meldung` (bei `frage` nur, wenn `termin_id`/`projekt_nr` mitkommt):
offener Bericht (`bericht_id`) > `projekt_nr` > `termin_id` > **der Termin, bei dem der Monteur laut Plan gerade ist**
(wie bei Berichten in 5c). Kein Termin mit Projekt an dem Tag → `422` „bitte Termin oder Projekt wählen“ (eine Frage
geht dann ohne Projekt). Fremdes Projekt → `404`. Das erkannte Projekt steht in `projekt` – die App zeigt es deutlich.

### Antwort: die Karte

```json
{
  "id": "3f9c…", "art": "bestellung", "status": "offen", "datum": "2026-10-16",
  "text": "mir fehlen noch drei Eckventile halb Zoll",
  "termin_id": "hero:…", "projekt": {"id": 4711, "nr": "SAN-660", "kunde": "Müller"},
  "fotos": 0,
  "bericht": null, "antwort": null,
  "bestellung": {"material": [{"text": "Eckventil 1/2", "menge": 3, "einheit": "Stk", "suchtext": "Eckventil 1/2 3/8",
                               "artikel": {"nr": "…", "name": "…", "einheit": "Stk"}, "vorschlaege": […], "rueckfrage": null}],
                 "hinweis": "bis morgen früh"},
  "meldung": null, "zeiten": null, "abwesenheit": null,
  "rueckfrage": null, "ergebnis": null
}
```

Immer da: `id`, `art`, `status` (`offen`, `erledigt`, `verworfen`), `datum`, `text` (erkannter bzw. gesendeter Text),
`termin_id`, `projekt` (`{id, nr, kunde}` oder `null`), `fotos` (Anzahl), `rueckfrage` (Frage der KI an den Monteur
oder `null`), `ergebnis` (`null` bis „Passt“). Je nach `art` ist **genau eines** dieser Felder gefüllt:

| `art` | Feld | Inhalt |
|---|---|---|
| `bericht` | `bericht` | `MonteurBericht` wie in 5c (`id`, `status`, `entwurf` …). Korrekturen von Hand weiter mit `PATCH /v1/monteur/berichte/{bericht.id}`, per Sprache einfach wieder in den Eingang mit `bericht_id` |
| `frage` | `antwort` | `{text, quellen:[{titel, url}], anleitungen:[{pfad, seite, titel}], artikel:[{nr, name, einheit}]}` – `text` kurz, Schritte nummeriert, in der Sprache der Frage; `quellen` = Internet (Herstellerseiten, Datenblätter, **YouTube**); `anleitungen` = Treffer in unserer Anleitungsbibliothek; `artikel` = Katalog (Gut Mollenhauer) mit Artikelnummer, **keine Preise** |
| `bestellung` | `bestellung` | `{material:[Material wie im Bericht], hinweis}` – `hinweis` = Termin/Dringlichkeit/Lieferort, wenn gesagt |
| `meldung` | `meldung` | `{art, text}` – `art`: `nicht_angetroffen`, `verspaetung`, `mangel`, `nachtrag`, `sonstiges`; `text` von der KI sauber formuliert (Deutsch, Uhrzeit), kommt so in den Projektverlauf |
| `zeiten` | `zeiten` | `[{beginn: "HH:MM", ende, art}]`, `art`: `lager`, `fahrt`, `arbeit`, `material`, `pause` (Regeln in 5c) |
| `urlaub` | `abwesenheit` | `{art, von, bis, halber_tag_anfang, halber_tag_ende, kommentar}` |

Bei `bericht` gilt: Ist mit `bericht_id` ein Entwurf offen, wird er ergänzt (gleiche `bericht.id`, neue Karten-ID);
sonst entsteht ein neuer Entwurf. Mitgeschickte Fotos hängen am Bericht.

### Bestätigen: `POST /v1/monteur/eingang/{id}/passt` (JSON)

Alle Felder optional – nur schicken, was der Monteur auf der Karte geändert hat:

| `art` | Felder | `ergebnis` |
|---|---|---|
| `bericht` | `unterschrift_png` (PNG base64/Data-URL), `unterschrift_name`, `zeiten_buchen` (Standard `true`) | `{hero_nr, hinweise[]}` – wie `…/abschliessen` in 5c |
| `frage` | – (kein „Passt“ nötig; Aufruf schadet nicht, liefert die Karte) | – |
| `bestellung` | `material`: **vollständige** neue Liste `[{index, menge, text?, einheit?, artikel_nr?}]` wie beim Bericht (`index` = Position in der Karte, `null` = neu; `artikel_nr` aus `vorschlaege` oder `GET /v1/monteur/artikel`, `null` = freie Position) | `{hero: "Bestellschein BS-… im Projekt", hero_id, hero_nr, hinweise[]}` |
| `meldung` | `text` (max. 2000 Zeichen): korrigierter Text | `{hero: "Kommentar im Projektverlauf", hinweise[]}` |
| `zeiten` | `zeiten`: korrigierte Abschnitte `[{beginn, ende, art}]` | `{gebucht: <Anzahl>, hinweise[]}` |
| `urlaub` | `abwesenheit`: korrigierter Antrag | `{abwesenheit_id, hinweise[]}` |

Was der Server macht:
- **bestellung:** legt einen **fertigen Bestellschein** (BS-Nummer, Empfänger Gut Mollenhauer – wie BS-2) im
  HERO-Projekt an: Katalogartikel (per IDS bestellbar), sonst freie Position. Nur fertige Belege lassen sich in HERO
  per „Positionen übernehmen“ ins Angebot/die Rechnung holen. Dazu Projektverlauf „Material angefordert – <Name>
  (TT.MM.JJJJ HH:MM):“ mit Liste, Hinweis und „Bestellschein BS-… liegt im Projekt.“ Fotos zur Bestellung gehen ins
  Projekt. `ergebnis`: `{hero, hero_id, hero_nr, hinweise[]}`. Bestellt (IDS) wird weiter im Büro. Leere Liste → `422`,
  Material mit offener `rueckfrage` → `409` „Erst die Rückfragen klären: …“ (mit `material` die Wahl mitschicken,
  dann ist die Rückfrage erledigt). Klappt nur der Kommentar nicht, steht das in `hinweise`.
- **meldung / bestellung ohne erkennbares Projekt** (kein Termin mit Projekt im Plan, keine `projekt_nr`) → schon
  beim Eingang `422` „Zu welchem Projekt? …“ – App zeigt den Text, Monteur tippt den Termin an bzw. sagt die Nummer.
- **meldung:** Kommentar im HERO-Projektverlauf (Logbuch-Typ 9210 „Kommentar von …“, wie „Waren vor Ort, Frau X hat
  abgelehnt“): „<Titel> – <Name> (TT.MM.JJJJ HH:MM):“ + Text. Titel je Art: Kunde nicht angetroffen, Verspätung,
  Mangel/Schaden, Nachtrag/Zusatzwunsch, Meldung. Fotos gehen über „Nach HERO hochladen“ in **wenigen Minuten ins
  HERO-Projekt**; klappt ein Foto nicht, steht es in `hinweise`. HERO lehnt ab → `502` (Karte bleibt `offen`).
- **zeiten:** bucht jeden Abschnitt in HERO (Status neu). `arbeit`/`material` mit dem Projekt der Karte, `lager`
  immer ohne Projekt.
- **urlaub:** beantragt in HERO (wie `POST /v1/monteur/abwesenheiten`). Kein Zeitraum → `422`.

Antwort ist wieder die Karte, jetzt `status: "erledigt"` und `ergebnis` gefüllt. **`ergebnis.hinweise` immer
anzeigen**, wenn nicht leer. „Passt“ ist **wiederholbar**: eine erledigte Karte wird unverändert zurückgegeben, nie
doppelt gebucht (Zeiten/Urlaub zusätzlich mit eigener `anfrage_id` je Eintrag). Verworfene Karte → `409`.

### Weitere Aufrufe

- `DELETE /v1/monteur/eingang/{id}` → `204`: Karte verwerfen; bei `bericht` wird der Entwurf mit gelöscht. Schon
  erledigt (außer `frage`) → `409`.
- `GET /v1/monteur/eingang` → eigene Karten der letzten 7 Tage (ohne verworfene, max. 50, neueste zuerst) – nach dem
  App-Start offene Karten (`status: "offen"`) wieder zeigen.
- `GET /v1/monteur/eingang/{id}` → eine Karte.
- `GET /v1/monteur/anleitung?pfad=<antwort.anleitungen[].pfad>` → PDF (inline) aus unserer Anleitungsbibliothek
  (nur `Intern/Anleitungen`, sonst `404`). Seite `seite` im Viewer anspringen (`#page=<seite>`).

### Ausgangskorb und Wiederholung

`anfrage_id` = Karten-ID. Kommt dieselbe `anfrage_id` noch einmal (Netz weg, App neu gestartet), liefert der Server
die **schon erstellte Karte** – keine zweite KI-Runde, kein zweiter Entwurf. Gehört die ID einem anderen Zugang →
`409`. Bei `502`/`503`/Netzfehler also einfach mit derselben `anfrage_id` wiederholen; danach `…/passt` ebenso.

### Nachfrage (`bezug_id`)

Auf einer Antwortkarte spricht der Monteur einfach weiter („und wo sitzt der Sensor?“) → `POST /v1/monteur/eingang`
mit `bezug_id` = ID der Antwortkarte. Die KI bekommt vorige Frage und Antwort mit; Ergebnis ist eine neue Frage-Karte.

### Projektmappe: Verlauf

`GET /v1/monteur/projekte/{id}` hat neu `verlauf: [{datum, titel, text}]` – die letzten 15 Kommentare im
HERO-Projektverlauf (Büro und Monteure, neueste zuerst; `titel` oft leer, die Überschrift steht dann in der ersten
Zeile von `text`). Eigene Meldungen und Materialanforderungen tauchen dort nach dem nächsten Abgleich des HERO-Spiegels auf.

## 6. Ablage durchblättern (Ordneransicht)

Tippt man in der App auf einen Pfad (aus `dateien`, `foto`, `video`, `notiz`), soll man **im Ordner landen, die Datei
markiert** – wie am PC im Explorer.

`GET /v1/ordner?pfad=Notizen/Sascha/2026-09-24 Objektbegehung Hotel.md` – ist `pfad` eine Datei, kommt ihr Ordner:

```json
{"pfad": "Notizen/Sascha", "eltern": "Notizen", "abgeschnitten": false,
 "eintraege": [
   {"name": "2026-09-24 Objektbegehung Hotel", "pfad": "Notizen/Sascha/2026-09-24 Objektbegehung Hotel",
    "typ": "ordner", "groesse": null, "geaendert": "24.09.2026 13:31"},
   {"name": "2026-09-24 Objektbegehung Hotel.md", "pfad": "Notizen/Sascha/2026-09-24 Objektbegehung Hotel.md",
    "typ": "datei", "groesse": "2.1 KB", "geaendert": "24.09.2026 13:31"}]}
```

- `pfad=""` bzw. weglassen = oberste Ebene. `eltern: null` = schon ganz oben (kein „Zurück nach oben“ zeigen).
- Reihenfolge: erst Ordner, dann Dateien. **`sortierung`** (neu in 1.13.0):
  `name` (Standard) = A→Z, Zahlen als Zahlen (`Bild 2` vor `Bild 10`), **HERO-Projektordner nach Projektnummer**
  über alle Kürzel hinweg (HEZ-99, HEZ-1000, SAN-1166, SAN-1270 – die Nummern sind eine gemeinsame Folge) ·
  `name_ab` = umgekehrt, d.h. **neueste Projekte zuerst** · `geaendert` = zuletzt geändert zuerst.
- **Seitenweise laden** (neu in 1.13.0): `anzahl` (1–1000, Standard 1000) und `ab` (Start, Standard 0). `gesamt` =
  Einträge im Ordner, `abgeschnitten: true` = es kommt noch mehr → beim Scrollen ans Ende mit `ab = ab + anzahl`
  nachladen. Empfehlung: `anzahl=100`.
- **`projekt`** (neu in 1.13.0): bei HERO-Projektordnern `{"nr", "kunde", "bauvorhaben", "titel", "status"}`, sonst
  `null`. Als Untertitel zeigen, z.B. „Grimmstraße 7, 10967 Berlin · Abgeschlossen“ – dann sieht man im HERO-Archiv
  sofort, welches Projekt welches ist.
- Geschäftsführung und versteckte Dateien erscheinen nie. Das alte, leere Ordnergerüst `Projekte` der früheren
  Firma-App ist seit 1.13.0 **ausgeblendet** (alte Pfade dahin funktionieren noch). `403` = gesperrt/außerhalb,
  `404` = nicht (mehr) da.
- Markieren macht die App: den Eintrag mit `pfad ==` angetipptem Pfad hervorheben und dorthin scrollen.

**Datei öffnen (Android):** `GET /v1/datei?pfad=…` in den App-Cache laden, dann `Intent.ACTION_VIEW` über einen
`FileProvider` mit dem Mime-Typ aus `Content-Type` (`FLAG_GRANT_READ_URI_PERMISSION`). So öffnet sich PDF, Bild, Video,
Word/Excel in der passenden App. Große Dateien mit Fortschritt laden; bei `ActivityNotFoundException` „Keine App für
diesen Dateityp“ anzeigen.
`&anzeigen=true` liefert `Content-Disposition: inline` statt `attachment` (für Browser-Vorschau, Android egal).

`GET /v1/freigabe` → `{"wurzeln": ["\\\\192.168.178.155\\Firma", "\\\\100.106.160.80\\Firma"]}` – wo die Ablage in
Windows liegt. Nur für die Windows-Hülle ([WINDOWS-HUELLE.md](WINDOWS-HUELLE.md)); Android braucht das nicht.

## 6a. Suche – neu in 1.13.0

**Ziel: in Sekunden die richtige Datei.** Der Server hält einen Index der **ganzen Ablage** (HERO-Archiv, Notizen,
GU-Projekte, Intern, Kunden, Eingang …) im Speicher; eine Suche dauert **wenige Millisekunden**. Der Index wird jede
Minute erneuert, nach Änderungen über den Assistenten (Fotos, neue Dateien, Verschieben) schon nach ~3 s.

`GET /v1/suche?q=Grimmstr 7 Rechnung`

| Parameter | | |
|---|---|---|
| `q` | ja | Suchwörter (1–200 Zeichen) |
| `ordner` | nein | Nur in diesem Ordner samt Unterordnern, z.B. `HERO-Archiv` oder der gerade offene Ordner. Leer = ganze Ablage |
| `typ` | nein | `alle` (Standard) · `ordner` · `datei` |
| `max` | nein | 1–200, Standard 50 |

```json
{"anzahl": 3, "abgeschnitten": false, "stand": "13.10.2026 11:42:07",
 "treffer": [
   {"name": "Rechnung-RE-1410-Boese-07-08-2026.pdf",
    "pfad": "HERO-Archiv/SAN-1166 Herr Mark Boese/Rechnungen/Rechnung-RE-1410-Boese-07-08-2026.pdf",
    "typ": "datei", "ordner": "HERO-Archiv/SAN-1166 Herr Mark Boese/Rechnungen", "groesse": "84.2 KB",
    "geaendert": "07.08.2026 10:12",
    "projekt": {"nr": "SAN-1166", "kunde": "Herr Mark Boese", "bauvorhaben": "Grimmstraße 7, 10967 Berlin",
                "titel": "Strang", "status": "Abgeschlossen", "pfad": "HERO-Archiv/SAN-1166 Herr Mark Boese"}}]}
```

**Was gefunden wird** (präzise statt viel):
- Ordner- und Dateinamen überall; **HERO-Projekte zusätzlich über Kunde, Baustellen-Adresse und Projekttitel** –
  „Grimmstr 7“ findet das Projekt, auch wenn die Straße nicht im Ordnernamen steht.
- Projektnummer: `1270`, `SAN-1270` oder `san 1270` → das Projekt steht **ganz oben**.
- Mehrere Wörter = alle müssen passen. Ein Wort darf im übergeordneten Ordner stehen: „Dreißig Rechnung“ = Rechnungen
  in den Projekten von Immobilien Dreißig; „1270 Angebot“ = Angebote im Projekt 1270.
- Wortanfänge reichen (`grimm`, `vaill`), ab 4 Buchstaben auch mitten im Wort (`rechnung` → „Schlussrechnung“).
  Hausnummern nur als ganze Zahl (`7` ≠ PLZ 10967, `7a` passt). Umlaute/ß und Straße/Str./Strasse sind egal.
- Ein Projektordner, der passt, bringt nicht seinen ganzen Inhalt mit – erst mit einem zweiten Wort („1270 Foto“).

**Fehler:** `422` leerer Suchbegriff · `403` gesperrter Bereich in `ordner` · `404` `ordner` gibt es nicht.

**So soll es in der App aussehen:**
- In der Ordneransicht **oben ein Suchfeld** (immer sichtbar, auch auf der obersten Ebene). Suchen **während des
  Tippens** (ab 2 Zeichen, 250–300 ms entprellen, ältere Anfrage abbrechen). Schalter „Nur in diesem Ordner“ →
  `ordner=<aktueller Ordner>`; Standard = ganze Ablage.
- Trefferliste wie die Ordneransicht: Symbol, `name` fett; Untertitel bei `projekt` „SAN-1166 · Herr Mark Boese ·
  Grimmstraße 7“, sonst `ordner`. Tippen auf eine Datei = öffnen; Tippen auf einen Ordner = dorthin springen.
  Lange drücken bzw. Nebenknopf bei Dateien = **„Im Ordner zeigen“** (`GET /v1/ordner?pfad=<pfad>`, Datei markiert).
- `abgeschnitten: true` → unter der Liste „Mehr als 50 Treffer – Suche genauer fassen“.
- Keine Treffer → „Nichts gefunden. Tipp: Projektnummer, Kunde oder Straße eingeben.“

## 7. Weboberfläche

`https://<server>/` liefert eine fertige Weboberfläche (Chat, Sprechen, Bestätigungen, Fotos/Videos/Dokumente per Ziehen &
Ablegen, Ordneransicht). Im Browser nutzbar und in Chrome über „App installieren“ als eigenes Fenster. In der
**Windows-Hülle** öffnen „Im Programm öffnen“/„Im Explorer zeigen“ direkt Word, Acrobat & Co. bzw. den Explorer auf M: –
Auftrag dafür: [WINDOWS-HUELLE.md](WINDOWS-HUELLE.md).

Aufbau seit 1.21.0 (Neugestaltung):
- **Links** Seitenleiste (ausblendbar): „Neues Gespräch“, Suche (Strg+K), Bereiche Chat · Kalender · GU-Plantafel,
  darunter Ablage · Offen (öffnen das Panel rechts), dann die gruppierte Gesprächsliste (1e) mit Anheften/Löschen beim
  Überfahren.
- **Mitte** die gewählte Ansicht: Chat (mittige Spalte, Eingabe als Karte mit Anhang, Projekt anheften, Modellwahl,
  Mikrofon), **Kalender** (Woche als Spalten, Tag als Liste) oder GU-Plantafel.
- **Vorschau neben dem Chat**: „Ansehen“ an einer Datei (oder Klick in der Ablage) zeigt PDF (pdf.js, Seiten als
  Bilder, Zoom), Fotos, Videos, Text und Notizen (Markdown) neben dem Chat; Breite ziehbar, Esc schließt.
- **Rechts** Panel mit Reitern Ablage · Offen · Heute (eigene Termine von heute), Breite ziehbar (Maus, Pfeiltasten,
  Doppelklick = Standard), Strg+B blendet es ein/aus. Breiten, Reiter und eingeklappte Gruppen merkt sich das Gerät.
- Linien-Symbole statt Emojis, hell/dunkel nach Systemeinstellung, Schrift Segoe UI Variable (Windows 11).

## 8. Verlauf

`GET /v1/gespraeche?q=` → `[{"id","titel","erstellt","geaendert","laeuft","modell","denkaufwand","angeheftet"}]` (`laeuft` seit 1.17.0, siehe 1b; `modell`/`denkaufwand` = Wahl des Gesprächs seit 1.20.0, siehe 1c; `angeheftet` und `q` seit 1.21.0, siehe 1e)
`GET /v1/gespraeche/{id}` → `[{"id","rolle","text","zeit","dateien","links","bestaetigungen","quellen","modell","denkaufwand","denkaufwand_name","modell_gewechselt"}]` (`links` wie im Chat, seit 1.13.0; Modellfelder bei `assistent` seit 1.20.0)
`PATCH /v1/gespraeche/{id}` → Modell/Denkaufwand setzen (1c), anheften (1e)
Seit 1.20.0 außerdem: `fortschritt` und `abgebrochen` bei `assistent`-Nachrichten, `zwischenruf` bei `nutzer`-Nachrichten (1d).
`dateien` einer `nutzer`-Nachricht = mitgeschickte Bilder (seit 1.5.0).
`rolle`: `nutzer` | `assistent` | `hinweis` (Systemzeile wie „Ausgeführt: … verschoben“ – klein/grau anzeigen).

## Kotlin / Retrofit – Gerüst

```kotlin
interface AssistentApi {
    @GET("v1/ich") suspend fun ich(): Ich
    @POST("v1/chat") suspend fun chat(@Body a: ChatAnfrage): ChatAntwort      // ChatAnfrage mit anfrageId (1.17.0)
    @GET("v1/chat/{anfrageId}") suspend fun chatStand(@Path("anfrageId") id: String,
        @Query("seit") seit: Int = 0): ChatStatus                                                             // seit: 1.20.0
    @POST("v1/chat/{anfrageId}/abbrechen") suspend fun abbrechen(@Path("anfrageId") id: String): ChatStatus      // 1.20.0
    @POST("v1/chat/{anfrageId}/zwischenruf") suspend fun zwischenruf(@Path("anfrageId") id: String,
        @Body z: Zwischenruf): ZwischenrufAntwort                                                            // 1.20.0
    @Multipart @POST("v1/sprache")
    suspend fun sprache(@Part audio: MultipartBody.Part,
                        @Part("gespraech_id") gespraechId: RequestBody?,
                        @Part("antworten") antworten: RequestBody,
                        @Part("anfrage_id") anfrageId: RequestBody? = null,
                        @Part("modell") modell: RequestBody? = null,              // 1.20.0
                        @Part("denkaufwand") denkaufwand: RequestBody? = null): SpracheAntwort
    @GET("v1/notizen") suspend fun notizen(): Notizen
    @Multipart @POST("v1/fotos")
    suspend fun foto(@Part foto: MultipartBody.Part,
                     @Part("notiz") notiz: RequestBody? = null,
                     @Part("text") text: RequestBody? = null,
                     @Part("gespraech_id") gespraechId: RequestBody? = null): FotoAntwort
    @Multipart @POST("v1/dokumente")
    suspend fun dokument(@Part dokument: MultipartBody.Part,        // Dateiname mit Endung!
                         @Part("notiz") notiz: RequestBody? = null,
                         @Part("text") text: RequestBody? = null,
                         @Part("gespraech_id") gespraechId: RequestBody? = null): DokumentAntwort
    @Multipart @POST("v1/videos")
    suspend fun video(@Part video: MultipartBody.Part,
                      @Part("notiz") notiz: RequestBody? = null,
                      @Part("text") text: RequestBody? = null,
                      @Part("abschreiben") abschreiben: RequestBody? = null,   // "auto" | "immer" | "nie"
                      @Part("gespraech_id") gespraechId: RequestBody? = null): VideoAntwort
    @POST("v1/bestaetigungen/{id}") suspend fun entscheiden(@Path("id") id: String, @Body e: Entscheidung): EntscheidungAntwort
    @GET("v1/bestaetigungen") suspend fun offene(): List<OffeneBestaetigung>
    @GET("v1/gespraeche") suspend fun gespraeche(): List<GespraechKurz>
    @GET("v1/gespraeche/{id}") suspend fun verlauf(@Path("id") id: String): List<Nachricht>
    @GET("v1/modelle") suspend fun modelle(): ModellAuswahl                                                  // 1.20.0
    @PATCH("v1/gespraeche/{id}") suspend fun gespraechEinstellen(@Path("id") id: String, @Body w: ModellWahl): GespraechKurz  // 1.20.0
    @GET("v1/termine") suspend fun termine(@Query("von") von: String, @Query("bis") bis: String? = null): Termine
    @GET("v1/kalender/kategorien") suspend fun kategorien(): List<TerminKategorie>                        // 1.18.0
    @POST("v1/kalender/hero") suspend fun heroTerminAnlegen(@Body t: HeroTerminNeu,
        @Query("nur_pruefen") nurPruefen: Boolean = false): HeroTerminErgebnis                               // 1.18.0
    @PATCH("v1/kalender/hero/{id}") suspend fun heroTerminVerschieben(@Path("id") id: Long, @Body t: HeroTerminVerschieben,
        @Query("nur_pruefen") nurPruefen: Boolean = false): HeroTerminErgebnis                               // 1.18.0
    @Streaming @GET("v1/datei") suspend fun datei(@Query("pfad") pfad: String): ResponseBody
    @GET("v1/angebote") suspend fun angebote(): List<AngebotKurz>
    @GET("v1/angebote/{id}") suspend fun angebot(@Path("id") id: String): Angebot
    @Streaming @GET("v1/angebote/{id}/ugl")
    suspend fun angebotUgl(@Path("id") id: String, @Query("titelzeilen") titelzeilen: Boolean = true): ResponseBody
    @GET("v1/ordner") suspend fun ordner(@Query("pfad") pfad: String = ""): Ordner
    @GET("v1/auftraege") suspend fun auftraege(): List<Auftrag>
    @GET("v1/auftraege/{id}") suspend fun auftrag(@Path("id") id: String): Auftrag
    @POST("v1/auftraege/{id}/abbrechen") suspend fun abbrechen(@Path("id") id: String): Auftrag
    @POST("v1/auftraege/{id}/wiederholen") suspend fun wiederholen(@Path("id") id: String): Auftrag
    @GET("v1/bauablauf") suspend fun guProjekte(): GuProjekte
    @GET("v1/bauablauf/{id}") suspend fun guPlan(@Path("id") id: Int): GuPlan
    @POST("v1/bauablauf/{id}/einsaetze") suspend fun einsatzNeu(@Path("id") id: Int, @Body e: EinsatzNeu): EinsatzAntwort
    @PATCH("v1/bauablauf/einsaetze/{id}") suspend fun einsatzAendern(@Path("id") id: Int, @Body a: EinsatzAenderung): EinsatzAntwort
    @DELETE("v1/bauablauf/einsaetze/{id}") suspend fun einsatzLoeschen(@Path("id") id: Int)
    @POST("v1/bauablauf/{id}/pdf") suspend fun bauzeitenplan(@Path("id") id: Int): PfadAntwort
    @GET("v1/aufgaben") suspend fun aufgaben(@Query("person") person: String? = null): AufgabenUebersicht
    @POST("v1/aufgaben/{id}") suspend fun aufgabeAendern(@Path("id") id: String, @Body a: AufgabeAenderung): AufgabeAntwort
}

@Serializable data class ChatAnfrage(val text: String, val gespraech_id: String? = null,
    val bilder: List<String> = emptyList(),   // max. 4 Foto-Pfade (aus FotoAntwort.foto)
    val modell: String? = null, val denkaufwand: String? = null)   // 1.20.0: "auto" oder Wert aus /v1/modelle
@Serializable data class Denkstufe(val wert: String, val name: String)                                     // 1.20.0
@Serializable data class ModellEintrag(val wert: String, val name: String, val teuer: Boolean = false,
    val hinweis: String? = null)                                                                             // 1.20.0
@Serializable data class ModellAuswahl(val modelle: List<ModellEintrag>, val denkstufen: List<Denkstufe>,
    val automatisch: String, val standard_modell: String, val arbeits_modell: String? = null,
    val standard_denkaufwand: Denkstufe, val arbeits_denkaufwand: Denkstufe)
@Serializable data class ModellWahl(val modell: String? = null, val denkaufwand: String? = null)   // explicitNulls = false
@Serializable data class Ereignis(val nr: Int, val zeit: String, val sekunde: Double, val art: String, val text: String,
    val werkzeug: String? = null, val ok: Boolean? = null, val dauer_ms: Long? = null, val modell: String? = null,
    val denkaufwand: String? = null, val schritt: Int? = null)                                              // 1.20.0
@Serializable data class Fortschritt(val ereignisse: List<Ereignis> = emptyList(), val naechster: Int = 0,
    val schritte: Int = 0, val werkzeuge: Int = 0, val aktuell: String? = null, val dauer_s: Double = 0.0,
    val gestartet: String? = null)
@Serializable data class Zwischenruf(val text: String)
@Serializable data class ZwischenrufAntwort(val angenommen: Boolean, val nachricht_id: Long, val hinweis: String)
@Serializable data class GespraechKurz(val id: String, val titel: String? = null, val erstellt: String,
    val geaendert: String, val laeuft: Boolean = false, val modell: String = "auto", val denkaufwand: String = "auto")
@Serializable data class Bestaetigung(val id: String, val beschreibung: String, val werkzeug: String)
@Serializable data class Ausgefuehrt(val id: String, val status: String, val beschreibung: String)
@Serializable data class Quelle(val titel: String, val url: String)
@Serializable data class ChatAntwort(val gespraech_id: String, val antwort: String,
    val dateien: List<String> = emptyList(), val bestaetigungen: List<Bestaetigung> = emptyList(),
    val ausgefuehrt: List<Ausgefuehrt> = emptyList(), val quellen: List<Quelle> = emptyList(),
    val modell: String? = null, val denkaufwand: String? = null, val denkaufwand_name: String? = null,   // 1.20.0
    val modell_gewechselt: Boolean = false,
    val fortschritt: Fortschritt? = null, val abgebrochen: Boolean = false)                                  // 1.20.0
@Serializable data class SpracheAntwort(val text: String, val chat: ChatAntwort? = null)
@Serializable data class NotizOrdner(val name: String, val pfad: String, val notizen: Int, val geaendert: String)
@Serializable data class NotizKurz(val titel: String, val pfad: String, val ordner: String, val geaendert: String)
@Serializable data class Notizen(val wurzel: String, val ordner: List<NotizOrdner>, val notizen: List<NotizKurz>)
@Serializable data class FotoAntwort(val foto: String, val notiz: String, val notiz_neu: Boolean)
@Serializable data class DokumentAntwort(val dokument: String, val notiz: String, val notiz_neu: Boolean,
    val lesbar: Boolean, val hinweis: String = "")
@Serializable data class VideoAntwort(val video: String, val notiz: String, val notiz_neu: Boolean,
    val dauer_s: Double, val abschrift: String)   // "laeuft" | "aus"
@Serializable data class OrdnerEintrag(val name: String, val pfad: String, val typ: String,   // "ordner" | "datei"
    val groesse: String? = null, val geaendert: String)
@Serializable data class Ordner(val pfad: String, val eltern: String? = null, val eintraege: List<OrdnerEintrag>,
    val abgeschnitten: Boolean = false)
@Serializable data class Auftrag(val id: String, val art: String, val titel: String,
    val status: String,            // "wartet" | "laeuft" | "fertig" | "fehler" | "abgebrochen"
    val schritt: String, val fortschritt: Int, val erstellt: String, val geaendert: String,
    val dateien: List<String> = emptyList(), val zusammenfassung: String? = null,
    val offene_punkte: List<String> = emptyList(), val fehler: String? = null, val gespraech_id: String? = null)
@Serializable data class Aufgabe(val id: String, val text: String,
    val status: String,            // "offen" | "erledigt" | "gestrichen"
    val bis: String? = null, val fuer: String? = null, val von: String, val notiz: String, val seit: String? = null,
    val erledigt_am: String? = null, val erledigt_von: String? = null, val nachfragen: Boolean)
@Serializable data class AufgabenUebersicht(val person: String, val stand: String, val offen: Int,
    val ueberfaellig: List<Aufgabe>, val diese_woche: List<Aufgabe>, val spaeter: List<Aufgabe>,
    val ohne_datum: List<Aufgabe>, val an_andere: List<Aufgabe>, val heute_erledigt: List<Aufgabe>)
@Serializable data class AufgabeAenderung(val status: String? = null, val noch_aktuell: Boolean? = null,
    val bis: String? = null)       // explicitNulls = false, damit nicht gesetzte Felder wegfallen
@Serializable data class AufgabeAntwort(val status: String, val aufgabe: Aufgabe)
@Serializable data class GuProjekt(val id: Int, val name: String, val ordner: String, val von: String? = null,
    val bis: String? = null, val einsaetze: Int)
@Serializable data class GuProjekte(val projekte: List<GuProjekt>)
@Serializable data class Gewerk(val id: Int, val name: String, val firma: String, val kontakt: String,
    val telefon: String, val farbe: String)
@Serializable data class Einsatz(val id: Int, val gewerk: String, val gewerk_id: Int, val farbe: String,
    val firma: String, val von: String, val bis: String, val was: String, val bereich: String,
    val status: String,            // "geplant" | "bestaetigt" | "fertig" | "abgesagt"
    val nach: Int? = null)
@Serializable data class GuWarnung(val art: String, val einsaetze: List<Int>, val text: String)
@Serializable data class GuPlan(val projekt: GuProjekt, val gewerke: List<Gewerk>, val einsaetze: List<Einsatz>,
    val warnungen: List<GuWarnung>)
@Serializable data class EinsatzNeu(val gewerk: String, val von: String, val bis: String? = null,
    val was: String = "", val bereich: String = "", val status: String = "geplant", val nach: Int? = null)
@Serializable data class EinsatzAenderung(val gewerk: String? = null, val von: String? = null,
    val bis: String? = null, val was: String? = null, val bereich: String? = null, val status: String? = null,
    val nach: Int? = null, val folgende_mitverschieben: Boolean = false)   // explicitNulls = false
@Serializable data class EinsatzAntwort(val status: String, val einsatz: Einsatz, val warnungen: List<GuWarnung>,
    val mitverschoben: List<Einsatz> = emptyList())
@Serializable data class PfadAntwort(val pfad: String)
@Serializable data class Entscheidung(val annehmen: Boolean)
@Serializable data class EntscheidungAntwort(val status: String, val beschreibung: String,
    val fehler: String? = null, val dateien: List<String> = emptyList())
```
OkHttp: `readTimeout(150, SECONDS)`, Interceptor setzt `Authorization: Bearer <cs_-Token>`; bei 401 Token löschen → Anmeldebildschirm.
```kotlin
@Serializable data class Anmeldung(val name: String, val passwort: String, val code: String, val geraet: String)
@Serializable data class AnmeldeAntwort(val token: String, val geraet_id: Int, val name: String, val anzeigename: String, val gueltig_tage: Int)
@POST("v1/anmelden") suspend fun anmelden(@Body a: Anmeldung): AnmeldeAntwort   // ohne Bearer
@POST("v1/abmelden") suspend fun abmelden(): Response<Unit>
```
```kotlin
// Angebote (1.11.0) – Beträge/Mengen kommen als String, in BigDecimal umwandeln
@Serializable data class AngebotKurz(val id: String, val titel: String, val positionen: Int, val geaendert: String)
@Serializable data class Markierung(val art: String, val text: String)            // pruefen | alternative | info
@Serializable data class AngebotPosition(val id: String, val nr: String, val art: String, val menge: String,
    val name: String, val ek: String, val ek_gesamt: String, val lp: String = "0", val bemerkung: String = "",
    val vk: String? = null, val vk_gesamt: String? = null, val pauschale: Boolean = false,
    val markierung: Markierung? = null)
@Serializable data class AngebotGruppe(val nr: String, val titel: String, val ebene: Int, val positionen: List<AngebotPosition>)
@Serializable data class AngebotFrage(val id: String, val text: String, val erledigt: Boolean,
    val positionen: List<String> = emptyList(), val positionen_nr: List<String> = emptyList(), val gruppe_nr: String = "")
@Serializable data class Angebot(val id: String, val titel: String, val version: Int, val geaendert: String,
    val ek_summe: String, val pauschalen_summe: String = "0", val vorschau: String? = null,
    val gruppen: List<AngebotGruppe>, val fragen: List<AngebotFrage> = emptyList(), val hinweise: List<String> = emptyList())
```

Alternativ Klassen generieren: `openapi-generator-cli generate -i openapi.json -g kotlin -o client --additional-properties=library=jvm-retrofit2,serializationLibrary=kotlinx_serialization`.

## Verbindlichkeit

Alles unter `/v1/` bleibt rückwärtskompatibel: Neue Felder können dazukommen (Parser mit `ignoreUnknownKeys = true` konfigurieren), bestehende werden nicht umbenannt oder entfernt. Brechende Änderungen kommen unter `/v2/`.
