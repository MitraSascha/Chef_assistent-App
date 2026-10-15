# Auftrag an den App-Agenten – Server 1.20.0: Modellwahl (Teil A) und Fortschritt/Stopp/Zwischenruf (Teil B)

Anlass: Die Nutzer möchten wie in den Web-Versionen von ChatGPT und Claude **selbst** wählen, welches KI-Modell
antwortet und wie gründlich es nachdenkt. Bisher hat das allein der Server entschieden. Dieses Verhalten bleibt als
Standard „Automatisch“ erhalten. Die Weboberfläche (und damit die Windows-Hülle) hat die Auswahl schon.

Vertrag: [`openapi.json`](openapi.json) 1.20.0 (sha256 `c833691d…d33bc`), Beschreibung in
[`API.md` 1c (Teil A) und 1d (Teil B)](API.md). Es gibt nur Ergänzungen. Die Aufträge 1.13 bis 1.19 gelten weiter.

# Teil A – Modell und Denkaufwand wählen

**Kurz:** Jedes Gespräch hat zwei Einstellungen, `modell` und `denkaufwand`. Jede ist entweder `"auto"` (Standard)
oder ein Wert aus `GET /v1/modelle`. Die App zeigt zwei Auswahlfelder beim Eingabefeld, speichert die Wahl über die API
im Gespräch und zeigt unter jeder Antwort klein, womit sie erzeugt wurde.

## A: Bitte bauen
1. **Auswahl laden.** Nach der Anmeldung (und beim App-Start) `GET /v1/modelle` holen und merken:
   ```json
   {"modelle": [{"wert": "gpt-5.6-luna", "name": "gpt-5.6-luna", "teuer": false, "hinweis": null},
                {"wert": "gpt-5.6-sol", "name": "gpt-5.6-sol", "teuer": false, "hinweis": null},
                {"wert": "gpt-5.6-astra", "name": "gpt-5.6-astra", "teuer": true,
                 "hinweis": "Deutlich teurer pro Anfrage – nur für besonders schwierige Aufgaben"}],
    "denkstufen": [{"wert": "low", "name": "niedrig"}, {"wert": "medium", "name": "mittel"}, {"wert": "high", "name": "hoch"}],
    "automatisch": "Automatisch: Der Server wählt je Nachricht. Alltag mit gpt-5.6-luna (Denkaufwand niedrig); …",
    "standard_modell": "gpt-5.6-luna", "arbeits_modell": "gpt-5.6-sol",
    "standard_denkaufwand": {"wert": "low", "name": "niedrig"}, "arbeits_denkaufwand": {"wert": "medium", "name": "mittel"}}
   ```
   Die Listen **nicht** in der App fest einbauen, denn der Server kann sie ändern. `"auto"` steht nicht in den Listen
   und ist immer erlaubt. Schlägt der Aufruf fehl (alter Server: `404`), die Auswahlfelder ausblenden.
2. **Zwei kleine Auswahlfelder beim Eingabefeld** (z. B. als Chips/Dropdown über oder neben dem Textfeld, auch auf
   kleinen Handys bedienbar, mindestens 48 dp Tippfläche):
   - **Modell:** erster Eintrag „Automatisch“ (`auto`), danach die Modelle mit `teuer: false` (`name` anzeigen, an
     die API geht `wert`). Darunter **abgesetzt** (Trennlinie/eigener Abschnitt „Teuer“) die Modelle mit
     `teuer: true`, jeweils mit der Kennzeichnung **„teuer“ in Warnfarbe** (Orange/Bernstein, auch im Dunkelmodus gut
     lesbar) und dem `hinweis` als Untertitel.
   - **Denkaufwand:** erster Eintrag „Automatisch“ (`auto`), danach `denkstufen[].name` (an die API geht `wert`).
   - Eine Info (ⓘ oder langes Drücken auf „Automatisch“) zeigt den Text aus `automatisch`.
   - Ist etwas anderes als „Automatisch“ gewählt, dezent hervorheben (z. B. Rahmen in der Akzentfarbe).
   - **Teures Modell wählen:** vorher einmal bestätigen lassen: „gpt-5.6-astra ist deutlich teurer pro Anfrage. Für
     diesen Chat verwenden?“ (Ja / Nein). Nein = alte Auswahl bleibt, kein `PATCH`. Nicht erneut fragen, solange es
     im Gespräch eingestellt bleibt (auch nicht beim Wiederöffnen).
   - **Solange ein teures Modell eingestellt ist:** Auswahlfeld in Warnfarbe und direkt daneben bzw. über dem
     Eingabefeld „⚠ teuer pro Anfrage“ zeigen, damit es niemand versehentlich stehen lässt.
3. **Wahl je Gespräch:**
   - Neues Gespräch → beide auf „Automatisch“.
   - Gespräch öffnen → Werte aus `GET /v1/gespraeche` (`modell`, `denkaufwand` je Eintrag) übernehmen.
   - Ändern in einem **bestehenden** Gespräch → sofort `PATCH /v1/gespraeche/{id}` mit
     `{"modell": "gpt-5.6-sol"}` bzw. `{"denkaufwand": "high"}` (nicht gesetzte Felder weglassen oder `null`). Die
     Antwort ist der Gesprächseintrag mit den gespeicherten Werten. Bei einem Fehler die alte Auswahl wiederherstellen
     und `detail` zeigen.
   - Ändern in einem **neuen** Gespräch (noch keine `gespraech_id`) → nur lokal merken und mit der ersten Nachricht
     schicken (Punkt 4).
4. **Beim Senden immer mitschicken:** `POST /v1/chat` bekommt zusätzlich `"modell"` und `"denkaufwand"` mit der
   aktuellen Auswahl. Bei `POST /v1/sprache` sind es die Formularfelder `modell` und `denkaufwand`. Der Server speichert
   die Werte im Gespräch, sie gelten schon für diese Nachricht. Auch beim Nachsenden aus der Warteschlange bzw. mit
   derselben `anfrage_id` die Werte von damals mitschicken.
   ```json
   {"text": "Monatsbericht September", "gespraech_id": null, "anfrage_id": "3f2c9a0e-…",
    "modell": "gpt-5.6-sol", "denkaufwand": "high"}
   ```
5. **Anzeige unter jeder Antwort:** Jede ChatAntwort (auch über `GET /v1/chat/{anfrage_id}` und `chat` in der
   Sprach-Antwort) und jede Nachricht mit `rolle = "assistent"` in `GET /v1/gespraeche/{id}` enthält:
   ```json
   {"modell": "gpt-5.6-sol", "denkaufwand": "medium", "denkaufwand_name": "mittel", "modell_gewechselt": true}
   ```
   Darunter klein und grau anzeigen: `gpt-5.6-sol · mittel`. Bei `modell_gewechselt: true` ` · gewechselt`
   anhängen (bedeutet: Der Server hat während der Antwort automatisch auf das stärkere Modell bzw. mehr Denkaufwand
   umgeschaltet; angegeben ist das zuletzt verwendete). Bei einem teuren Modell `(teuer)` hinter den Namen. Ist
   `modell` `null` (alte Nachrichten), nichts anzeigen.

## A: Was der Server macht (zur Info, nichts zu bauen)
| Modell | Denkaufwand | Ergebnis |
|---|---|---|
| auto | auto | wie bisher: luna/niedrig, bei großer Aufgabe (Angebot, Auswertung, viele Schritte) → sol/mittel |
| fest | auto | ganze Antwort mit dem gewählten Modell, kein Wechsel; Denkaufwand niedrig, bei großer Aufgabe mittel |
| auto | fest | Modell automatisch, Denkaufwand immer der gewählte |
| fest | fest | genau diese Kombination |

Teure Modelle (Astra) nimmt die Automatik **nie**, nur auf ausdrückliche Wahl. Versicherungsberichte,
Begehungsprotokolle, Spracherkennung und Vorlesen haben eigene Modelle und bleiben gleich.

## A: Fehlerfälle
- `422` bei unbekanntem Wert, z. B. `{"detail": "Unbekanntes Modell „gpt-4“. Erlaubt: auto, gpt-5.6-luna, gpt-5.6-sol."}`
  bzw. „Unbekannter Denkaufwand „ultra“ …“. Bei `/v1/chat` und `/v1/sprache` wird dann **nichts** ausgeführt. In dem
  Fall `GET /v1/modelle` neu laden (die Liste hat sich geändert), auf „Automatisch“ zurückstellen und `detail` zeigen.
  Die Nachricht nicht still verwerfen, der Nutzer soll sie erneut senden können.
- **Modell beim Anbieter nicht verfügbar** (z. B. Astra für unseren Schlüssel noch nicht freigeschaltet): `200`,
  `antwort` beginnt mit „⚠️ Modell gpt-5.6-astra ist beim Anbieter nicht verfügbar – bitte anderes Modell wählen“.
  Normal anzeigen; optional zusätzlich die Modell-Auswahl hervorheben/öffnen.
- `404` bei `PATCH`: Das Gespräch gibt es nicht (mehr) bzw. es gehört einer anderen Person. Dann wie bei einem
  gelöschten Gespräch verfahren.
- Wurde ein gespeicherter Wert auf dem Server aus der Auswahl genommen, liefert `GET /v1/gespraeche` dafür `"auto"`.

## A: Abwärtskompatibilität
- Alte App-Versionen ohne diese Felder funktionieren unverändert: Ohne `modell`/`denkaufwand` bleibt die Einstellung
  des Gesprächs, und neue Gespräche laufen auf „Automatisch“. Das ist genau das Verhalten bis 1.19.0.
- Alle neuen Antwortfelder sind zusätzlich (`ignoreUnknownKeys = true` genügt). `modell`/`denkaufwand` der Antwort
  können `null` sein.

## A: Kotlin
```kotlin
@GET("v1/modelle") suspend fun modelle(): ModellAuswahl
@PATCH("v1/gespraeche/{id}") suspend fun gespraechEinstellen(@Path("id") id: String, @Body w: ModellWahl): GespraechKurz
@Serializable data class Denkstufe(val wert: String, val name: String)
@Serializable data class ModellEintrag(val wert: String, val name: String, val teuer: Boolean = false,
    val hinweis: String? = null)                                                                             // 1.20.0
@Serializable data class ModellAuswahl(val modelle: List<ModellEintrag>, val denkstufen: List<Denkstufe>,
    val automatisch: String, val standard_modell: String, val arbeits_modell: String? = null,
    val standard_denkaufwand: Denkstufe, val arbeits_denkaufwand: Denkstufe)
@Serializable data class ModellWahl(val modell: String? = null, val denkaufwand: String? = null)   // explicitNulls = false
// ChatAnfrage: + val modell: String? = null, val denkaufwand: String? = null
// GespraechKurz: + val modell: String = "auto", val denkaufwand: String = "auto"
// ChatAntwort und Nachricht: + val modell: String? = null, val denkaufwand: String? = null,
//                              val denkaufwand_name: String? = null, val modell_gewechselt: Boolean = false
```

## A: Abnahme
- Neues Gespräch: Beide Felder stehen auf „Automatisch“. „Hallo“ schicken → unter der Antwort `gpt-5.6-luna · niedrig`.
- „Mach den Monatsbericht für September“ (Automatisch) → unter der Antwort `gpt-5.6-sol · mittel · gewechselt`.
- Modell auf `gpt-5.6-sol`, Denkaufwand auf „hoch“ stellen und „Hallo“ schicken → `gpt-5.6-sol · hoch`.
- Anderes Gespräch öffnen und zurückwechseln → die Auswahl steht wieder auf sol/hoch (kommt vom Server, auch auf
  einem zweiten Gerät bzw. in der Weboberfläche).
- Gespräch neu laden → unter den alten Antworten steht weiter, womit sie erzeugt wurden.
- Sprachnachricht mit gewählter Einstellung → die Antwort zeigt das gewählte Modell.
- Astra wählen → Bestätigungsfrage; „Nein“ lässt die alte Auswahl stehen, „Ja“ färbt das Feld in Warnfarbe und
  zeigt „⚠ teuer pro Anfrage“. Unter der Antwort `gpt-5.6-astra (teuer) · …`.

# Teil B – Fortschritt, Denken, Stopp und Zwischenruf

Anlass: Die Nutzer wollen wie in den Konsolen der großen Modelle sehen, **was der Assistent gerade tut** (wie viele
Werkzeuge, welche Tätigkeit), sein **Denken aufklappen** und ihn **unterbrechen** oder einen **Zwischenruf** schicken
können. Bisher stand bei langen Angeboten minutenlang nur „denkt nach …“. Die Weboberfläche hat das schon.

**Kurz:** Während `POST /v1/chat` läuft, fragt die App jede Sekunde `GET /v1/chat/{anfrage_id}?seit=<n>` ab und zeigt
eine Fortschrittszeile. Der Senden-Knopf wird zu **Stopp** (`POST …/abbrechen`), und Text im Eingabefeld geht als
**Zwischenruf** (`POST …/zwischenruf`). Unter jeder fertigen Antwort hängt ein eingeklappter „Verlauf“.

Warum Polling statt Server-Sent Events: Das passt zum vorhandenen Abholen aus 1.17.0 (dieselbe Anfrage, derselbe
Endpunkt), übersteht Funklöcher und App-Neustarts ohne Sonderfälle (einfach mit `seit` weiter abfragen), geht durch
den Proxy ohne Puffer-Einstellungen und ist in Retrofit ein normaler Aufruf. Bei etwa 1 Abfrage pro Sekunde und nur
neuen Ereignissen ist das klein.

## B: Bitte bauen
1. **Fortschritt abfragen.** Die `anfrage_id` vergibt die App ohnehin (1.17.0). Direkt nach dem Absenden von
   `POST /v1/chat` (bzw. `/v1/sprache`) parallel alle **1 s** `GET /v1/chat/{anfrage_id}?seit=<naechster>` aufrufen
   (beim ersten Mal `seit=0`). Ereignisse anhängen und `naechster` für die nächste Abfrage merken. `404` am Anfang
   heißt „noch nicht angekommen“ → weiter versuchen. Das Abfragen endet, sobald `POST /v1/chat` antwortet. Bei
   Verbindungsabbruch geht es wie in 1.17.0 mit dem Abholen weiter, nur mit `seit`. Nur abfragen, solange der Chat
   sichtbar ist; im Hintergrund reicht das bisherige Abholen.
   ```json
   {"anfrage_id": "…", "status": "laeuft", "fortschritt": {"ereignisse": [
      {"nr": 3, "sekunde": 4.2, "art": "werkzeug", "text": "Sucht Projekt „Altkaulsdorf“", "werkzeug": "hero_projekt_suchen"},
      {"nr": 4, "sekunde": 4.6, "art": "werkzeug_fertig", "text": "fertig", "werkzeug": "hero_projekt_suchen", "ok": true, "dauer_ms": 380},
      {"nr": 5, "sekunde": 9.8, "art": "denken", "text": "Ich vergleiche Angebot und Schlussrechnung …"}],
    "naechster": 6, "schritte": 3, "werkzeuge": 5, "aktuell": "Denkt nach …", "dauer_s": 9.9}}
   ```
2. **Fortschrittszeile statt „denkt nach …“:** in der Antwort-Blase **„Arbeitet · Schritt 3 · 5 Werkzeuge · 0:42“**
   (Schritt = `schritte`, Werkzeuge = `werkzeuge`, die Uhr zählt lokal jede Sekunde weiter). Darunter kursiv `aktuell`
   („Liest Rechnungs-PDFs von SAN-1102“). Darunter eingeklappt **„Details“** mit der Ereignisliste:
   - je Zeile Zeit (m:ss aus `sekunde`), Symbol, `text`
   - `werkzeug_fertig` nicht als eigene Zeile, sondern an die passende Werkzeugzeile hängen: „✓ 0,4 s“ bzw. bei
     `ok: false` den Fehlertext in Rot
   - `denken` dezent (grau, kursiv, mehrzeilig), `zwischenruf` und `wechsel` als eigene Zeilen
   - unbekannte `art` als einfache Zeile
3. **Denken ist nicht die Antwort.** `denken`-Texte nur in diesem aufklappbaren Bereich zeigen, nie in der
   Antwort-Blase, nicht vorlesen und nicht kopieren/teilen. Manche Modelle liefern keine, dann fehlt der Teil einfach.
4. **Stopp:** Während eine Antwort läuft und das Eingabefeld leer ist, wird der Senden-Knopf zu **„⏹ Stopp“**
   (Warn-/Rotton). Antippen → `POST /v1/chat/{anfrage_id}/abbrechen`. Dann in der Fortschrittszeile „Wird abgebrochen …
   (der laufende Schritt wird noch beendet)“ zeigen. Der wartende `POST /v1/chat` liefert danach normal `200` mit
   `abgebrochen: true` und dem Text „⏹ Abgebrochen. …“; das wie jede Antwort anzeigen. `409` = war schon fertig,
   ignorieren. Kein Bestätigungsdialog nötig, aber die Enter-/Senden-Taste der Tastatur darf bei leerem Feld **nicht**
   stoppen.
5. **Zwischenruf:** Während eine Antwort läuft, heißt der Knopf bei Text im Feld **„Zwischenruf ➤“** (Akzentfarbe),
   der Platzhalter heißt „Zwischenruf an den Assistenten …“. Senden → `POST /v1/chat/{anfrage_id}/zwischenruf` mit
   `{"text": "…"}`. Bei `200` den Text sofort als eigene Nutzer-Blase **vor** der laufenden Antwort zeigen, mit kleiner
   Marke „Zwischenruf“ (gestrichelter Rand). Bei `409` (Antwort war gerade fertig) den Text im Feld lassen und
   „Die Antwort ist schon fertig – bitte als neue Nachricht senden.“ zeigen. Sprachnachrichten während einer laufenden
   Antwort: zuerst Text erkennen lassen (`/v1/sprache` mit `antworten=false`), dann als Zwischenruf schicken.
6. **„Verlauf“ unter fertigen Antworten:** `ChatAntwort.fortschritt` und `Nachricht.fortschritt` (Verlauf,
   `rolle = "assistent"`) enthalten den kompletten Verlauf. Wenn `werkzeuge > 0` oder ein `denken` dabei ist, unter der
   Antwort eingeklappt **„Verlauf: 7 Werkzeuge · 1:12 min“** (aus `werkzeuge` und `dauer_s`), aufgeklappt dieselbe Liste
   wie in den Details. Bei älteren Nachrichten ist `fortschritt` `null`, dann nichts anzeigen. Nutzer-Nachrichten mit
   `zwischenruf: true` bekommen die Marke „Zwischenruf“.

## B: Fehlerfälle und Grenzen
- `404` bei `abbrechen`/`zwischenruf`/Abfragen: Die Anfrage gibt es nicht (noch nicht angekommen) oder sie gehört
  einer anderen Person.
- `409` bei `abbrechen`: schon fertig. `409` bei `zwischenruf`: schon fertig oder wird gerade abgebrochen. Dann als
  normale Nachricht senden.
- **Stopp wirkt erst am nächsten Schritt.** Ein laufender Modellaufruf (bei großen Aufgaben bis etwa eine Minute)
  läuft zu Ende, sein Ergebnis wird verworfen. Ein laufendes Werkzeug wird fertig. **Schon Ausgeführtes bleibt**
  (z. B. eine angelegte Notiz), das steht auch in der Abbruch-Antwort.
- Ein Zwischenruf kommt ebenfalls erst beim nächsten Modellaufruf an. Kam er, während die Schlussantwort entstand,
  macht der Assistent einen Schritt mehr und geht darauf ein.
- Nach einem Neustart des Servers sind laufende Anfragen `fehler` (wie bisher). Ihre Ereignisse bleiben abrufbar.

## B: Abwärtskompatibilität
- Alte Apps: `fortschritt`, `abgebrochen`, `zwischenruf` sind zusätzliche Felder (`ignoreUnknownKeys = true`).
- `GET /v1/chat/{id}` ohne `seit` liefert wie bisher Status und Antwort (dazu alle Ereignisse).
- Neu ist `status: "abgebrochen"`. Das kommt nur vor, wenn jemand gestoppt hat (z. B. in der Weboberfläche). Bitte
  so behandeln: ein unbekannter Status mit gesetzter `antwort` gilt wie `fertig`.

## B: Kotlin
```kotlin
@GET("v1/chat/{id}") suspend fun chatStand(@Path("id") id: String, @Query("seit") seit: Int = 0): ChatStatus
@POST("v1/chat/{id}/abbrechen") suspend fun abbrechen(@Path("id") id: String): ChatStatus
@POST("v1/chat/{id}/zwischenruf") suspend fun zwischenruf(@Path("id") id: String, @Body z: Zwischenruf): ZwischenrufAntwort
@Serializable data class Ereignis(val nr: Int, val zeit: String, val sekunde: Double, val art: String, val text: String,
    val werkzeug: String? = null, val ok: Boolean? = null, val dauer_ms: Long? = null, val modell: String? = null,
    val denkaufwand: String? = null, val schritt: Int? = null)
@Serializable data class Fortschritt(val ereignisse: List<Ereignis> = emptyList(), val naechster: Int = 0,
    val schritte: Int = 0, val werkzeuge: Int = 0, val aktuell: String? = null, val dauer_s: Double = 0.0,
    val gestartet: String? = null)
@Serializable data class Zwischenruf(val text: String)
@Serializable data class ZwischenrufAntwort(val angenommen: Boolean, val nachricht_id: Long, val hinweis: String)
// ChatStatus: + val fortschritt: Fortschritt? = null   (status zusätzlich "abgebrochen")
// ChatAntwort und Nachricht: + val fortschritt: Fortschritt? = null, val abgebrochen: Boolean = false
// Nachricht: + val zwischenruf: Boolean = false
```

## B: Abnahme
- „Mach den Monatsbericht für September“: Die Zeile zählt Schritte, Werkzeuge und Zeit hoch, `aktuell` wechselt
  („Lädt festen Ablauf …“, „Wertet Rechnungen aus …“). „Details“ zeigt die Liste, Denken grau und eingeklappt.
- Nach dem Ende steht unter der Antwort „Verlauf: N Werkzeuge · m:ss min“, auch nach Neuladen des Gesprächs.
- Während einer langen Antwort „nimm nur Bad-Projekte“ tippen → Knopf „Zwischenruf ➤“ → Blase „Zwischenruf“
  erscheint, die Antwort geht darauf ein.
- Während einer langen Antwort „⏹ Stopp“ → nach höchstens einem Schritt kommt „⏹ Abgebrochen …“ mit der Liste des
  schon Ausgeführten.
- Funkloch während der Antwort: Die Fortschrittsanzeige setzt danach ohne doppelte Zeilen fort (`seit`).
