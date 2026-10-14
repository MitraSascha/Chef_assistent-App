# server_assi

Client-Projekt für den **Chef-Assistenten**, einen selbstgehosteten KI-Assistenten. Ziel sind **zwei Clients**:

1. **Android-App**
2. **Windows-11-App**

Das Backend liegt **nicht** in diesem Repo.

**Repos (seit 2026-10-14):** Projektstamm mit `android/`, Verträgen und Aufträgen → `MitraSascha/Chef_assistent-App` (**privat**, enthält Kundenadressen und Namen). `windows/` ist ein eigenes Repo → `MitraSascha/Chef_assistent-WinApp` (öffentlich) und steht im Stamm in `.gitignore`. Bis zum 2026-10-14 war die Android-App in **keinem** Repo. Nach größeren Änderungen committen und pushen. Es läuft auf einem Server im Netz des Nutzers und wird dort parallel entwickelt.

## Sprache

Antworten und Kommunikation auf **Deutsch**. Code und Commit-Messages auf Englisch — **aber**: die API-Felder sind deutsch (`gespraech_id`, `antwort`, `bestaetigungen`). Diese Namen **unverändert** übernehmen, nicht ins Englische übersetzen. Das gilt für DTOs; eigene Klassen darüber dürfen englisch heißen.

## Verträge (verbindlich)

| Datei | Rolle |
|---|---|
| `openapi.json` | **Quelle der Wahrheit**, OpenAPI 3.1, `Chef-Assistent` v1.18.0 |
| `API.md` | Erläuterung + Kotlin/Retrofit-Gerüst, für Android geschrieben |
| `WINDOWS-HUELLE.md` | Auftrag für die Windows-App (WebView2-Hülle, Nachrichten `hallo`/`zeigen`/`oeffnen`/`ergebnis`/`sprechen`) |

Beides kommt vom Server (`/openapi.json`, interaktiv `/docs`) und wird **nicht in diesem Repo bearbeitet** — bei Abweichungen neu vom Server ziehen. Die Windows-App nutzt dieselbe API; `API.md` ist nur in den Beispielen Android-spezifisch.

Alles unter `/v1/` bleibt rückwärtskompatibel: neue Felder können dazukommen, bestehende werden nicht umbenannt oder entfernt. Brechende Änderungen kämen unter `/v2/`.
→ **JSON-Parser tolerant konfigurieren** (`ignoreUnknownKeys = true` bzw. `JsonSerializerOptions` ohne strikte Prüfung). Unbekannte Felder dürfen nie einen Absturz auslösen.

## Netzwerk: Internet und Tailscale

Seit v1.10.0 (2026-09-30) hat der Dienst zwei Adressen:

| Adresse | Weg | Anmeldung |
|---|---|---|
| `https://assistent.mitra-sanitaer.de` | Internet, ohne VPN, **Vorgabe** in beiden Clients | nur Sitzungs-Token `cs_…` |
| `https://mitraserver-aspire-xc-605.tail8bd028.ts.net` | nur Tailscale bzw. Büro | `cs_…` **und** alte Schlüssel `ca_…` |

**Stand 2026-09-30:** Beide Adressen laufen mit 1.10.0. Die Internet-Adresse ist vom Dev-PC und vom Handy (Büro-WLAN, ohne VPN) erreichbar. `/openapi.json` gibt es dort nicht (404), nur über Tailscale.

- Die Adresse gehört in eine **Einstellung**, nicht in den Quelltext.
- Kein `localhost`-Denken: der Dev-PC erreicht das Backend über denselben Weg wie das Handy.
- Android: Tailscale ist ein VPN-Profil. Verbindet die App nicht, ist „VPN aktiv?" die erste Prüffrage, nicht der Code.

### ⚠ Der Dev-PC hängt im falschen Tailnet

> **Erledigt (2026-09-28):** `tailscale status` auf `saschaarbeit` zeigt `mitraserver-aspire-xc-605` (100.106.160.80) als `active; direct`, und die Vertrags-URL antwortet vom Dev-PC aus mit 200. Der Dev-PC kann die API also direkt erreichen. Der Abschnitt unten ist der alte Stand vom 2026-09-23.

Geprüft am 2026-09-23:

- **Das Testhandy erreicht den Server.** Galaxy S25 (`SM-S931B`, Android 16), Tailscale aktiv, Backend antwortet unter `100.106.160.80`. Die App funktioniert dort end-to-end.
- **Der Dev-PC (`saschaarbeit`) erreicht ihn nicht.** Er hängt im Tailnet `tail4868dd.ts.net`, die Vertrags-URL liegt in `tail8bd028.ts.net`. `server3070` in diesem Tailnet antwortet zwar mit Caddy auf Port 80, bricht TLS aber mit Alert 80 ab (kein Zertifikat für diesen Namen) — das ist ein anderer Rechner.

**Folge für die Arbeit hier:** Von diesem PC aus lässt sich die API nicht testen. Jede Prüfung gegen den echten Server läuft über `adb` auf dem angeschlossenen Handy. Wenn der Dev-PC direkt testen soll, muss er in das Tailnet `tail8bd028.ts.net` aufgenommen werden.

## API-Grundlagen

| | |
|---|---|
| Anmeldung | Header `Authorization: Bearer cs_…` aus `POST /v1/anmelden` (Name + Passwort + TOTP-Code). Alte `ca_…`-Schlüssel nur noch über Tailscale |
| Format | JSON, UTF-8; Zeiten ISO 8601 mit Offset (`2026-09-24T09:30:00+02:00`) |
| Lese-Timeout | 150 s; **Chat und Sprache seit v1.17.0 ohne** (API.md 1b, Ergebnis per `anfrage_id` abholen) |
| Fehler | `{"detail": "Klartext auf Deutsch"}` |

**Token sicher ablegen:** Android → EncryptedSharedPreferences / Keystore. Windows → DPAPI (`ProtectedData`) oder Credential Manager, **niemals** Klartext in einer Config-Datei.

**`detail` ist bereits deutscher Klartext** und wird direkt angezeigt — keine eigenen Fehlertexte erfinden, keine englischen Meldungen durchreichen.

### Statuscodes mit UI-Bedeutung

| Code | Bedeutung | UI |
|---|---|---|
| 401 | Token fehlt/falsch/gesperrt | Login-Bildschirm |
| 409 | Gespräch beschäftigt **oder** Bestätigung schon erledigt/abgelaufen | kein Fehler-Popup, Zustand neu laden |
| 413 | Aufnahme oder Foto zu groß (> 25 MB) | vorher abfangen |
| 415 | Foto nicht lesbar (z. B. HEIC) | Meldung im Foto-Dialog |
| 422 | Eingabe ungültig / keine Sprache erkannt | freundlicher Hinweis |
| 429 | Zu viele Fehlversuche bei `/v1/anmelden`, 15 min gesperrt | `detail` anzeigen |
| 502 | KI nicht erreichbar | „Nochmal versuchen" anbieten |

Beim App-Start `GET /v1/ich` → 200 heißt angemeldet.

## Verhalten, das die Clients abbilden müssen

- **Ein Request pro Gespräch.** Eine zweite parallele Anfrage bekommt `409`. Sendeknopf sperren, solange eine läuft.
- **Bestätigungen.** Verschieben, Löschen, Überschreiben, Bearbeiten und Terminänderungen führt der Server erst nach einem Ja aus. Die App zeigt `beschreibung` mit Ja/Nein und antwortet per `POST /v1/bestaetigungen/{id}`.
  - Der Nutzer kann stattdessen im Chat „ja, mach" sagen — dann steht die Ausführung in `ausgefuehrt`, und die Knöpfe zu dieser `id` **müssen verschwinden**.
  - Bestätigungen **laufen nach 24 h ab** (`409`).
  - Beim App-Start `GET /v1/bestaetigungen` holen, damit offene Fragen wieder auftauchen.
- **`antwort` ist Markdown** (`**fett**`, `- Listen`, `` `Pfad` ``). Android: Markwon. Vor dem Vorlesen die Markdown-Zeichen entfernen.
- **`dateien`** als antippbare Chips → `GET /v1/datei?pfad=…` laden und mit der Systemanwendung öffnen (Android: `Intent.ACTION_VIEW` über FileProvider).
- **`quellen`** als Links anzeigen.
- **`rolle: "hinweis"`** im Verlauf ist eine Systemzeile („Ausgeführt: … verschoben") — klein und grau darstellen, nicht wie eine Assistentennachricht.
- **Termine anlegen/ändern läuft über den Chat**, nicht über einen eigenen Endpunkt. `GET /v1/termine` ist nur lesend.

## Sprache (Audio)

- Aufnahme: `MPEG_4` + `AAC`, 16–44,1 kHz, mono reicht, **max. 25 MB** → `POST /v1/sprache` als `multipart/form-data`.
- `antworten=false` erkennt nur den Text (zum Korrigieren vor dem Senden).
- Ausgabe wahlweise System-TTS (Android `TextToSpeech` mit `Locale.GERMANY`; Windows `SpeechSynthesizer`) oder `POST /v1/vorlesen` → `audio/mpeg`, max. 4000 Zeichen und kostenpflichtig.

## Android-Client (`android/`)

**Stack:** nativ Kotlin + Jetpack Compose. Die Windows-App wird später ein eigenes Projekt und teilt keinen Code.

| | |
|---|---|
| Toolchain | AGP 9.4.1, Gradle 9.7.1, Kotlin 2.2.20, JDK 21 (Android-Studio-JBR) |
| Video | Media3 Transformer 1.11.1 (720p vor dem Upload) |
| SDK | `compileSdk 37`, `targetSdk 36`, `minSdk 26` |
| Paket | `de.chefassistent.app` |
| Netz | Retrofit 2.12 + OkHttp 4.12 + kotlinx.serialization |
| Markdown | Markwon 4.6.2 in einem `TextView` (Compose hat keinen Renderer) |
| Audio | `MediaRecorder` (MPEG_4/AAC, mono) rein, `TextToSpeech` mit `Locale.GERMANY` raus |

Bauen (PowerShell, aus `android/`):

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew assembleDebug
```

Die APK landet unter `android/app/build/outputs/apk/debug/app-debug.apk`.

**AGP 9 bringt Kotlin selbst mit.** Das Plugin `org.jetbrains.kotlin.android` darf **nicht** in `app/build.gradle.kts` stehen — der Build bricht sonst ab. `kotlin.plugin.compose` und `kotlin.plugin.serialization` bleiben.

### Aufbau

```
data/    Vertrag.kt (DTOs), AssistentApi.kt (Retrofit), Netzwerk.kt (OkHttp/Retrofit-Aufbau),
         ApiFehler.kt (Statuscode -> Bedeutung), Einstellungen.kt (Token verschlüsselt),
         AudioAufnahme.kt (MediaRecorder), Sprachausgabe.kt (Android-TTS),
         AssistentRepository.kt  <- einzige Stelle, die mit dem Server spricht
ui/      theme/, komponenten/ (One-UI-Bausteine), MarkdownText.kt, anmeldung/, chat/, termine/,
         verbrauch/, ablage/, auftraege/, aufgaben/, plantafel/, angebote/, freihand/
```

`AssistentRepository` ist die austauschbare Schicht: ViewModels kennen nur diese Fassade und nie Retrofit, HTTP-Codes oder `IOException`. Fehler kommen dort bereits als `ApiFehler` mit fertigem `anzeigetext` heraus.

Navigation: `MainActivity` hält u. a. die Ziele Anmeldung, Chat, Termine, Verbrauch und Geräte. Der Chat trägt eine Schublade mit der Gesprächsliste und den Verweisen auf Termine und Verbrauch. Ein 401 wirft von jedem Bildschirm aus zurück zur Anmeldung und räumt den Verlauf ab.

### Angebundene Endpunkte

Bewusst nicht: `GET /v1/freigabe` (Netzwerkpfade, nur für die Windows-Hülle) und `/v1/sicherheit*` (Passwort und Zwei-Faktor richtet jede Person in der Weboberfläche ein).


Anmeldung mit Name/Passwort/Code (`POST /v1/anmelden`, Token verschlüsselt, danach `GET /v1/ich`) · Abmelden (`POST /v1/abmelden`) · Angemeldete Geräte (`/v1/geraete`) · Chat mit Markdown · Datei-Chips mit Download und Öffnen per FileProvider · Quellen-Chips · Bestätigungen mit Ja/Nein inkl. Nachladen beim Start und Ausblenden bei `ausgefuehrt` · Sperre gegen parallele Anfragen · Sprachaufnahme (`POST /v1/sprache`) · Fotos und Videos zur Notiz (`POST /v1/fotos`, `POST /v1/videos`), auch aus der Galerie, Ziel aus `GET /v1/notizen` · Ordneransicht der Ablage (`GET /v1/ordner`, seitenweise und sortierbar) mit Suche (`GET /v1/suche`) · Link-Karten aus `links` · Vorlesen über Android-TTS · Gesprächsliste, Verlauf laden, Gespräch löschen · Hintergrundaufträge (`/v1/auftraege`) · Aufgaben „Offen“ (`/v1/aufgaben`) · Kalender mit HERO-Terminen und Personenfilter (`/v1/kalender`, `/v1/personen`), HERO-Termine anlegen und verschieben (`/v1/kalender/kategorien`, `/v1/kalender/hero`) · Verbrauch · Angebote mit Vorschau und UGL (`/v1/angebote`) · Dokumente zur Notiz (`POST /v1/dokumente`), aus der Dateiauswahl oder per „Teilen“.

### Auf dem Gerät geprüft (2026-09-23)

Auf dem Galaxy S25 installiert und mit echtem Token gegen den Server gefahren:

| Geprüft | Ergebnis |
|---|---|
| Anmeldung, Chat, Markdown | Antwort korrekt gerendert, Fettschrift greift |
| Sprachaufnahme | Raumton aufgenommen, transkribiert, Antwort kam zurück |
| Gesprächsliste | zeigt die echten Gespräche mit Zeitstempel |
| Kalender | leerer Zeitraum korrekt gemeldet, per Chat-Gegenfrage bestätigt |
| Verbrauch | zeigt die echten Zahlen |

| Vorlesen, OpenAI-Stimme | MP3 im Cache nachgewiesen, Audio aktiv, Datei danach gelöscht |
| Vorlesen, Gerätestimme | kein Server-Aufruf (Cache leer), Google-TTS-Prozess aktiv |
| Verlauf laden | früheres Gespräch geöffnet, Markdown-Listen korrekt |
| Stimmwahl | überlebt `force-stop` und Neustart |

| Datei-Chips | Notiz diktiert → Chip erschien → angetippt → heruntergeladen → in Chrome geöffnet |

| Bestätigungen Ja/Nein | Notiz-Löschung vorgemerkt → Karte mit Pfad → Ja → "Ausgeführt", Knöpfe verschwunden |

**Ungeprüft:** Quellen-Chips (`quellen` kam bisher leer, auch bei erkennbarer Websuche — offene Frage ans Backend) und 409 bei paralleler Anfrage. Letzteres ist aus der App heraus **nicht auslösbar**, weil der Sendeknopf waehrend einer laufenden Anfrage gesperrt ist; es kaeme nur zum Tragen, wenn ein zweiter Client gleichzeitig sendet.

Nebenbei belegt: `shared_prefs/chef_assistent_zugang.xml` enthält nur verschlüsselte Schlüssel und Werte — das Token liegt nicht im Klartext.

117 Unit-Tests laufen (`./gradlew testDebugUnitTest`) — sie sichern vor allem die Zusage aus `API.md`, dass neue Serverfelder die App nicht umbringen.

### Anmeldung (`ui/anmeldung/`, `ui/geraete/`, seit v1.10.0)

Umgebaut am 2026-09-30 nach API.md Abschnitt 0.

- **Felder:** Name, Passwort (mit Anzeigen-Knopf), Code (nur Ziffern, max. 6, „Anmelden“ erst bei 6 aktiv). Autofill-Hinweise per `semantics { contentType = Username / Password / SmsOtpCode }`. `autofillManager.commit()` wird bewusst nicht aufgerufen, damit kein „Passwort speichern?“ erscheint.
- **Passwort wird nie gespeichert**, nur im ViewModel gehalten und nach Erfolg geleert. Der **Name** liegt verschlüsselt unter eigenem Schlüssel und überlebt Abmelden und 401.
- **`/v1/anmelden` geht immer ohne Bearer raus** (`Netzwerk.istAnmeldung()`). Ein schon gesetzter `Authorization`-Header wird nicht überschrieben, damit ein verspätetes Abmelden nicht die neue Sitzung beendet.
- **Fehler:** 401/403/429 zeigen `detail`, bei 401 wird das Code-Feld geleert. Ein 401 hier löst **nicht** den globalen Rücksprung aus. 429 ist `ApiFehler.ZuVieleVersuche`.
- **Gerätename** für `geraet`: `Settings.Global.DEVICE_NAME` („Galaxy S25“), sonst `Build.MODEL`, plus „ – App“, max. 80 Zeichen (Vertrag).
- **Adresse:** Feld unter dem Knopf mit Chips „Internet“ und „Tailscale“. Bestehende Installationen behalten ihre gespeicherte Adresse.
- **Übergang:** Textknopf „Mit Zugangsschlüssel anmelden (nur Tailscale)“ für `ca_`-Schlüssel. Ein gespeicherter `ca_`-Token bleibt gültig, kein Zwangs-Logout.
- **Abmelden:** lokal sofort, danach `POST /v1/abmelden` mit dem alten Token im Hintergrund (max. 15 s, Fehler verschluckt).
- **Geräteliste** (Schublade → Einstellungen → „Angemeldete Geräte“): eigenes Gerät oben, Zeiten in Gerätezone, „Abmelden“ fragt immer nach, beim eigenen Gerät (`dieses`) ist es das normale Abmelden. 404 beim Abmelden heißt „schon weg“. Mit `ca_`-Schlüssel führt ein 401 dieser Liste nur zu einem Hinweis statt zur Abmeldung.

**Offen:** Ob `POST /v1/abmelden` mit einem `ca_`-Schlüssel diesen dauerhaft sperrt (dann wäre er auch auf anderen Geräten tot). Beim Backend klären, ggf. den Aufruf bei `ca_` weglassen (eine Zeile in `abmeldenUndSitzungBeenden()`).

Am Gerät geprüft (2026-09-30), **über die Internet-Adresse ohne VPN**:

| | Ergebnis |
|---|---|
| Update per `install -r` | alter `ca_`-Token blieb erhalten, App startete in den Chat |
| Abmelden (⋮) | Anmeldebildschirm mit Name/Passwort/Code, Chip „Tailscale“ war vorgewählt (gespeicherte Adresse) |
| Chip „Internet“ | Adresse wechselt auf `https://assistent.mitra-sanitaer.de/` |
| Anmeldung mit Passwort + Code | erfolgreich, Ablage lädt die echten Ordner |
| Angemeldete Geräte | „Galaxy S25 – App“ als „Dieses Gerät“ oben, darunter „Browser – Windows“, Zeiten in Ortszeit, IP sichtbar |

**Ungeprüft:** 401/403/429 live, Autofill, „Abmelden“ eines fremden Geräts, Vorbelegung des Namens nach erneutem Abmelden. Schönheitsfehler: Datum und Uhrzeit in der Geräteliste brechen um („30.09.2026 / 09:56“), weil der Abmelden-Knopf rechts Platz nimmt.

Nebenbei: Tailscale am Handy ließ die Namensauflösung komplett hängen (auch `google.com`). Ohne Tailscale lief alles, die Internet-Adresse braucht es nicht mehr.

### Freihandmodus (`ui/freihand/`)

Vollbild-Gespraech ohne Hinsehen, fuers Auto und die Baustelle.

**Drei Wege hinein**, weil der Nutzen gerade darin liegt, die App *nicht* erst suchen zu muessen:

| Weg | Wie |
|---|---|
| Langer Druck aufs App-Symbol | statischer Kurzbefehl, `res/xml/kurzbefehle.xml` |
| Schnelleinstellungs-Kachel | `FreihandKachel : TileService` — **der Nutzer muss sie einmalig selbst hinzufuegen** |
| Sprecher-Symbol in der Chat-Leiste | fuer den Fall, dass man schon in der App ist |

Alle drei schicken `MainActivity.AKTION_FREIHAND`. Das Ziel ist bewusst **nicht** das Startziel der Navigation: so fuehrt die Zurueck-Taste in den Chat und nicht aus der App heraus. `onNewIntent` faengt den Fall ab, dass die App schon laeuft.

Ab Android 14 nimmt `startActivityAndCollapse` nur noch einen `PendingIntent`; die Variante mit `Intent` wirft dort eine Ausnahme — beide Pfade stehen in `FreihandKachel`. Die Runde laeuft von selbst: **zuhoeren → senden → vorlesen → wieder zuhoeren**.

Das Ende einer Aeusserung erkennt die App an der Lautstaerke (`AudioAufnahme.maximalerAusschlag()`), nicht an einem Knopfdruck. Die Stellschrauben stehen als Konstanten in `FreihandViewModel`:

| Konstante | Wert | Wofuer |
|---|---|---|
| `SCHWELLE` | 2000 von 32767 | ab hier gilt es als Sprache |
| `STILLE_ENDE_MS` | 1500 | so lange Stille beendet die Aufnahme |
| `WARTEN_MAX_MS` | 8000 | sagt niemand etwas, wird die Runde verworfen statt Stille hochzuladen |
| `AUFNAHME_MAX_MS` | 60000 | Notbremse gegen Daueraufnahme in der Hosentasche |
| `STILLE_RUNDEN_MAX` | 3 | danach **haelt der Modus an** und wartet auf "Weiter zuhören" |

Die Pause nach drei stillen Runden ist kein Komfort, sondern Absicht: sonst laege das Handy in der Tasche und hoerte stundenlang zu. Der Bildschirm bleibt waehrend des Modus an (`keepScreenOn`).

**Kosten im Blick behalten:** Jede Runde mit Sprache kostet eine Transkription, und bei gewaehlter OpenAI-Stimme zusaetzlich die Ausgabe. Stille Runden laden nichts hoch und kosten nichts.

### Dateien oeffnen: Endung zu Typ

`.md` war auf dem Testgeraet **nicht zu oeffnen**. Gemessen am 2026-09-23:

| Typ | Apps auf dem Geraet |
|---|---|
| `text/plain` | 3 |
| `text/markdown` | **0** |
| `application/octet-stream` | **0** |

Android kennt die Endung `md` nicht, `MimeTypeMap` liefert nichts, und der Rueckfall auf `octet-stream` findet keinen Empfaenger — der Nutzer haette nur "Fuer diesen Dateityp ist keine App installiert" gesehen. Seit der Notizfunktion des Servers sind `.md`-Dateien der Regelfall.

Deshalb bildet `dateityp()` in `ChatScreen.kt` textartige Endungen (`md, markdown, txt, log, csv, ini, cfg, conf, yml, yaml`) fest auf `text/plain` ab. Kommt eine neue Textendung dazu, gehoert sie in diese Liste.

### Ausgangskorb fuer Funkloecher

**Grundsatz (Vorgabe des Nutzers, 2026-10-13): Ohne Netz darf nichts verloren gehen. Prioritaet 1, gilt fuer die ganze App.**

`Ausgangskorb` hebt **Sprachaufnahmen und getippte Texte** auf, die nicht rausgehen konnten. Greift im Chat (Text, Sprache, Frage zum Foto) und im Freihandmodus bei `KeineVerbindung` und `NichtAngemeldet`. Sprache auch bei 502/5xx/409, also bei jedem `ApiFehler.istVoruebergehend`. Getippter Text kommt bei diesen Fehlern dagegen zurueck ins Eingabefeld, weil der Nutzer gerade davorsitzt.

- Je Eintrag `<zeit>.<fassung>.json.enc` mit `AusgangAuftrag` (Gespraech, `faden`, `antworten`, `vorspann`, `anfrage_id`, `bilder`), bei Sprache zusaetzlich `<zeit>.m4a.enc`. **`EncryptedFile` bindet den Dateinamen ein**, Umbenennen macht die Datei unlesbar. Abgesichert wird deshalb ueber die Reihenfolge (grosse Datei zuerst, Begleitdaten zuletzt als „fertig“-Zeichen) und Fassungsnummern, siehe `Tresor.kt`.
- **Nur eine Instanz** je Korb (`ChefAssistentApp`): die `@Synchronized`-Sperren gelten pro Objekt. Frueher hatten Chat und Freihand eigene Instanzen, und der Versand konnte eine halb geschriebene Aufnahme als „unlesbar“ loeschen.
- **Diktat pruefen gilt auch offline:** die Aufnahme wird spaeter nur abgeschrieben (`antworten=false`), der Text kommt als `Rueckmeldung` (Karte ueber dem Chat, „Ins Eingabefeld“) zurueck. Projektkontext geht als `vorspann` mit.
- **`faden`:** Nachrichten aus einem neuen Chat ohne `gespraech_id` landen beim Nachsenden im selben Gespraech, nicht in je einem eigenen. `gespraechZuFaden()` sagt dem offenen Chat, wohin sie gingen.
- **Reihenfolge:** Wartet in einem Gespraech schon etwas, geht auch die naechste Nachricht in den Korb (`wartetIn`).
- **Nichts wird geloescht, was nicht nachweislich leer ist:** verworfen werden nur 422/413 bei Sprache (mit Hinweis). Abgelehnter Text kommt als `Rueckmeldung` zurueck. Unlesbares und andere endgueltige Fehler werden nach `ausgang_beiseite`/`medienausgang_beiseite` verschoben, nicht geloescht. Kein Platz im Cache heisst `retry`.
- Versand: `VersandArbeiter`, `anstossen()` ersetzt einen nur wartenden Auftrag (sonst hinge „Jetzt senden“ hinter Stunden Backoff) und haengt an einen laufenden an. Der Chat stoesst beim Start an, also auch nach erneuter Anmeldung. Videos und Dokumente laufen als Vordergrunddienst (`dataSync`), damit das 10-Minuten-Limit von WorkManager sie nicht endlos neu starten laesst.
- Wird der Chat abgeraeumt, waehrend eine Anfrage laeuft (Abmeldung von anderswo), wird sie in `NonCancellable` eingereiht.
- Entwurf, offenes Gespraech und Foto-Dialog ueberleben das Beenden der App durch Android (`SavedStateHandle`).

**`CONNECTED` heisst nicht Tailscale.** Die Bedingung sagt nur, dass irgendeine Verbindung besteht; ob der Server erreichbar ist, weiss erst der Aufruf. Darum fuehrt ein Verbindungsfehler zu `Result.retry()` und nicht zum Wegwerfen.

**Seit v1.17.0 nie doppelt (API.md 1b):** Jede Nachricht bekommt vor dem ersten Versuch eine `anfrage_id`, die beim Einreihen mitgeht. Der Versand fragt erst `GET /v1/chat/{anfrage_id}` (fertig, laeuft oder fehler, 404 heisst senden). Im Chat holt `chatSicher`/`spracheSicher` bei Abbruch oder 502–504 das Ergebnis alle 4 s ab, statt neu zu senden. Erst wenn auch das 4-mal scheitert, geht die Nachricht mit derselben Kennung in den Korb. Chat und Sprache haben **keinen Lese-Timeout** mehr, dafuer einen Ping alle 30 s.

Am Geraet geprueft (2026-10-13): Text im Flugmodus → „Wartet auf Netz“, im Korb verschluesselt (Klartext nicht auffindbar) → Flugmodus aus → nach etwa 30 s von selbst gesendet, Chat laedt das Gespraech mit Antwort neu, Korb leer. **Ungeprueft:** Diktat offline mit Rueckmeldung, Faden aus mehreren Offline-Nachrichten, Abholen nach Abbruch, Vordergrunddienst bei grossem Video.

Am Geraet geprueft (2026-09-23), zweimal mit Flugmodus:

| | Ergebnis |
|---|---|
| Stille aufgenommen | landet im Korb, wird nach Netzrueckkehr gesendet und als 422 verworfen |
| **Satz gesprochen** | 185 KB verschluesselt abgelegt (keine `ftyp`-Signatur, nur Zufallsbytes), nach 60 s von selbst gesendet, erscheint als Gespraech mit Antwort |

Der zweite Fall ist der eigentliche Beleg: **gesprochenes Wort ueberlebt das Funkloch.**

### Projektkontext anheften

Ueber dem Eingabefeld sitzt eine Leiste: ohne Kontext ein Chip "Projekt anheften", mit Kontext "Projekt: HEZ-2003 Schulz" samt Kreuz zum Loesen. Gesetzt wird er per Dialog, mit den fuenf zuletzt benutzten als Vorschlagschips.

Ist ein Kontext gesetzt, bekommt jede Nachricht die Zeile `Bezieht sich auf Projekt <Name>.` vorangestellt — **als Satz, nicht als eigenes Feld**, damit der Vertrag unveraendert bleibt. Der Nutzer sieht im Chat weiterhin nur seinen eigenen Wortlaut.

Zwei Entscheidungen, die nicht offensichtlich sind:

- **Der Kontext wird nicht dauerhaft gespeichert**, nur die Liste der zuletzt benutzten. Ein Kontext von gestern, der heute unbemerkt an jeder Frage haengt, richtet mehr Schaden an als das erneute Auswaehlen Muehe kostet. Deshalb ist er nach einem Neustart weg, aber mit einem Tipp wieder da.
- **Sprache geht bei gesetztem Kontext immer ueber die Erkennung**, auch wenn die Diktat-Pruefung aus ist: `/v1/sprache` hat kein Textfeld, dem sich etwas voranstellen liesse. Die App holt den erkannten Text, setzt die Bezugszeile davor und schickt ihn an `/v1/chat` — ohne Zutun des Nutzers, der Ablauf fuehlt sich unveraendert an. Ohne das waere der angeheftete Kontext bei jeder Sprachnachricht wirkungslos.

Am Geraet geprueft (2026-09-23): Kontext "HEZ-2003 Schulz" angeheftet, Frage "Worauf bezieht sich unsere Unterhaltung gerade?" → Antwort **"Unsere Unterhaltung bezieht sich gerade auf das Projekt HEZ-2003 Schulz."** Die Bezugszeile kommt also an, und in der Nutzerblase steht weiterhin nur der eigene Wortlaut. Merkliste, Vorbelegung des Feldes und "Loesen" ebenfalls geprueft.

### Diktat wird vor dem Senden geprueft

Vorgabe: Das Mikrofon schickt die Aufnahme mit **`antworten=false`** an `/v1/sprache`. Der Server transkribiert nur, der erkannte Text landet im Eingabefeld, der Nutzer korrigiert und sendet selbst. Abschaltbar in der Schublade (`Einstellungen.diktatPruefen`).

Der Grund ist nicht Bequemlichkeit: Solange der Assistent nicht antwortet, kann er auch **nichts Falschverstandenes ausfuehren**. Auf der Baustelle verhoert sich die Erkennung bei Eigennamen und Projektnummern; aus "Checkliste Schulz" wird schnell "Checkliste Schmitz" und daraus eine falsche Ablageaktion. Im Test am 2026-09-23 wurden sechs Sekunden Raumton zu "Bist du weg? Bist du weg?" — und blieben im Eingabefeld stehen, statt als Frage rauszugehen.

Ein bereits getippter Entwurf wird **nicht ueberschrieben**, der erkannte Text haengt sich an.

**Bildschirm bleibt waehrend der Aufnahme an** (`FLAG_KEEP_SCREEN_ON` am Fenster, gekoppelt an `nimmtAuf` in `ChatScreen`). Am 2026-10-13 ging das Handy mitten in einer Aufnahme aus. Gesperrt liefert das Mikrofon ab Android 11 nur Stille, und der Nutzer sprach ins Leere, ohne es zu merken. Geht die App trotzdem in den Hintergrund (Ein/Aus-Taste, Anruf), beendet `ON_STOP` die Aufnahme (`aufnahmeUnterbrochen()`). Das Gesagte landet **immer im Eingabefeld**, auch ohne Diktat-Pruefung, dazu eine Hinweiszeile.

**Nicht `View.keepScreenOn` nehmen.** Damit blieb das Flag am S25 nach einer Unterbrechung am Fenster haengen (`dumpsys window`: `fl=81810180`, Bit 0x80), das Display waere bis zum Schliessen der App angeblieben. Deshalb `window.addFlags`/`clearFlags`, und `ON_STOP` loescht das Flag zusaetzlich.

Am Geraet geprueft (2026-10-13), per `dumpsys power`: Wach-Lock nur waehrend der Aufnahme, nach Stopp und nach Home + Rueckkehr wieder frei. Nach Home: Hinweiszeile da, Erkanntes im Eingabefeld, nichts gesendet. Die Ein/Aus-Taste selbst ist ungeprueft (Geraet laesst sich per adb nicht entsperren), sie loest aber dasselbe `ON_STOP` aus.

### Antworten: Diagramme, Tabellen, Code (`ui/antwort/`, seit v1.14.0)

`AntwortInhalt` zerlegt `antwort` mit `abschnitte()` in Text, Tabelle, Code und Diagramm. **Nur Text geht an Markwon**, der Rest wird in Compose gezeichnet. Was der Zerleger nicht erkennt, bleibt Text, sieht also schlimmstenfalls aus wie vorher.

- **Diagramme** (`DiagrammAnsicht.kt`, API.md 1a): Balken (auch gestapelt), Linie, Torte als Ring. Farben, Maße, Animationen und Infobox folgen API.md 1a wörtlich, damit App und Weboberfläche gleich aussehen. Die Animation läuft einmal, sobald ein Viertel sichtbar ist (`rememberSaveable`, also nicht erneut nach Drehen oder Zurückscrollen). Der Block wird in der App **nochmals geprüft** (`istGueltig`, Grenzen aus API.md). Ungültige Blöcke fallen weg, die Tabelle darunter trägt die Zahlen. Das Format steht nicht in `openapi.json` (dort ist `antwort` nur ein String), deshalb liegen die DTOs in `ui/antwort/DiagrammDaten.kt` und nicht in `Vertrag.kt`.
- **Tabellen** (`TabellenAnsicht`): Spaltenbreite nach Inhalt (max. 220 dp, im Vollbild 360 dp), seitlich scrollbar, Zahlenspalten rechtsbündig (Trennzeile `--:` oder Erkennung per `istZahl`). Antippen öffnet das Vollbild.
- **Codeblöcke** mit Kopierknopf, seitlich scrollbar.
- **Unter jeder Antwort:** Kopieren, Teilen, Vorlesen. Kopiert wird `textZumKopieren()`: ohne Diagrammblock und Sternchen, Listen und Tabellen bleiben.
- **„Assistent arbeitet … N s“** als Blase am Listenende, solange `laeuft`. Der Balken oben erscheint nur noch, wenn der Chat leer ist.
- **Ist die Einheit `%`**, zeigt die Torte die Prozentzahl nur einmal.

Am Gerät geprüft (2026-10-13), Testfragen mit erfundenen Zahlen: Balken mit Infobox und Abdunkeln, Linie mit Fläche und Endpunkt, Torte mit Legende, Tabelle im Vollbild, „Assistent arbeitet … 5 s“, Knöpfe unter der Antwort. **Ungeprüft:** gestapelte Balken, mehrere Linien, negative Werte, Hell-Modus, Tipp auf Legende, Teilen-Ziel. Auf dem Server liegen dazu zwei Testgespräche („App-Test ohne Ablage …“), die gelöscht werden können.

### Markdown-Anzeige: was geht, was nicht

Am 2026-09-23 mit einer Formatprobe des Assistenten am Geraet geprueft. **Markwon in der Grundausstattung reichte nicht** — Tabellen kamen als Strichwueste an, nackte URLs waren toter Text. Deshalb drei Zusatzmodule in `MarkdownText.kt`:

| | Modul | Stand |
|---|---|---|
| Nummerierte und verschachtelte Listen | core | ✓ |
| Fett, kursiv, Code, Codeblock, Zitat | core | ✓ |
| `[Text](url)` | core | ✓ |
| Tabellen | `ext-tables` | ✓ |
| Nackte URLs `https://…` | `linkify` | ✓ |
| `~~durchgestrichen~~` | `ext-strikethrough` | ✓ |

**Linkify laeuft ohne `PHONE_NUMBERS`.** Mit der Voreinstellung `Linkify.ALL` hielt die Erkennung deutsche Datumsangaben wie `23.09.2026` fuer Telefonnummern und machte daraus Links, die die Telefon-App oeffnen. Datumsangaben kommen in den Antworten weit haeufiger vor als Rufnummern — der Tausch ist gewollt.

**Dateinamen werden nicht verlinkt.** `WEB_URLS` hielt „2026-09-24 App-Test Fotos.md“ für die Adresse `http://Fotos.md` (`.md` ist Moldau), am Gerät am 2026-09-24 gesehen. `dateinamenEntlinken()` nimmt solche Links wieder weg, aber nur ohne `http`/`www` im sichtbaren Text und nur bei Endungen, die zugleich Domains sind (`md, zip, mov, sh, py, pl, rs`). Echte Adressen und `[Text](url)` bleiben Links.

**Linkfarbe ist `primary`, nicht die Textfarbe.** Vorher waren Links nur an der Unterstreichung zu erkennen.

`markdownEntfernen()` fuer die Sprachausgabe behandelt Tabellen gesondert: Trennzeile weg, Striche werden Kommas. Sonst liest die Stimme zwischen jeder Zelle einen senkrechten Strich vor. **Vorsicht bei den regulaeren Ausdruecken:** `\s` schliesst den Zeilenumbruch ein und klebt Tabellenzeilen zusammen — dort gehoert `[ 	]` hin. Ein Test sichert das ab.

### Sprache: was wo entschieden wird

Beide Sprachfunktionen laufen im Backend über **denselben OpenAI-Key** (bestätigt vom Betreiber am 2026-09-23):

| | Endpunkt | Modell | Wer entscheidet |
|---|---|---|---|
| Spracherkennung | `POST /v1/sprache` (`assistent/server.py:212`) | `gpt-4o-mini-transcribe` | **nur der Server.** Die App lädt die Aufnahme hoch, mehr nicht. |
| Vorlesen | `POST /v1/vorlesen` (`assistent/server.py:236`) | `gpt-4o-mini-tts`, Stimme einstellbar | **App wählt den Weg, Server die Stimme.** |

Serverseitige Konfiguration in `/etc/chef-assistent/assistent.env`, danach Dienst neu starten:

| Schlüssel | Bedeutung |
|---|---|
| `SPRACHMODELL` | Modell der Spracherkennung |
| `STIMMENMODELL` | **nur das TTS-Modell** (`gpt-4o-mini-tts`), gilt für alle |
| `STIMME` | Standardstimme, z. B. `alloy` — greift, wenn die App keine mitschickt |

Die **Modelle** sind von der App aus nicht beeinflussbar. Die **Stimme** schon: seit der Vertragserweiterung vom 2026-09-23 nimmt `POST /v1/vorlesen` ein optionales Feld `stimme` entgegen.

Der Nutzer wählt in der Schublade zwischen zwei Wegen (`Einstellungen.serverStimme`, Vorgabe `true`, überlebt den Neustart):

1. **„OpenAI-Stimme"** → `POST /v1/vorlesen` → MP3, abgespielt von `Tonwiedergabe`. Natürlicher, kostet pro Zeichen, braucht Netz und eine kurze Wartezeit.
2. **„Gerätestimme"** → Android-TTS (`Sprachausgabe`), Google Speech Services. Sofort, offline, kostenlos, klingt roboterhafter.

Bewusst **zwei sichtbare Optionen statt eines Schalters**: ein Schalter zeigt nur den aktuellen Zustand, und „Vom Server" sagt niemandem, dass OpenAI spricht und Geld kostet. Beide Zeilen nennen Anbieter und Preis.

Scheitert der Server, fällt die App **automatisch auf die Gerätestimme zurück** statt stumm zu bleiben. Ein 401 ist davon ausgenommen und führt zur Anmeldung.

Vor beiden Wegen läuft der Text durch `markdownEntfernen()`, sonst werden Sternchen mitgesprochen. Texte über 4000 Zeichen werden gekürzt (Grenze aus `API.md`).

### Stimmenwahl pro Nutzer

`VorlesenAnfrage` hat seit dem 2026-09-23 ein optionales `stimme`. Erlaubt sind elf Werte (`alloy, ash, ballad, coral, echo, fable, nova, onyx, sage, shimmer, verse`); weglassen bedeutet Servervorgabe, ein unbekannter Wert ergibt 422.

In der App: Auswahlfeld unter „OpenAI-Stimme“ in der Schublade, Wahl in `Einstellungen.stimme` (verschlüsselt auf dem Gerät, `null` = Servervorgabe). Der Server speichert dazu nichts pro Nutzer.

Die erlaubten Werte stehen **an genau einer Stelle** im Client: `ERLAUBTE_STIMMEN` in `Vertrag.kt`. Erweitert der Server das `enum`, gehört die Änderung dorthin — ein Test vergleicht die Liste mit dem Vertrag.

Bei **422** zeigt die App die Servermeldung **und** liest trotzdem mit der Gerätestimme vor: ein Konfigurationsfehler soll sichtbar sein, aber nicht dazu führen, dass gar nichts gesprochen wird.

**Belegt am 2026-09-23, nach dem Serverneustart:** derselbe Text („Guten Morgen.“) mit zwei Stimmen vorgelesen ergibt unterschiedliche MP3s — `onyx` 37.074 Bytes, `nova` 27.410 Bytes, verschiedene Prüfsummen. Die Stimme wird also wirklich angewendet.

**Reihenfolge beim Ausrollen ist egal:** vorher gegen den noch alten Server getestet, der das unbekannte Feld anstandslos angenommen hat. Die App darf vor dem Server ausgerollt werden; bis dahin wird die Wahl nur ignoriert.

### Fotos und Videos zur Notiz (`POST /v1/fotos` seit v1.1.0, `POST /v1/videos` seit v1.2.0)

Kamera-Knopf in der Eingabeleiste mit Menü: **Foto aufnehmen · Video aufnehmen · Aus der Galerie**. Die Kamera-App liefert per `TakePicture` bzw. `CaptureVideo` in `cacheDir/fotos` bzw. `cacheDir/videos` (FileProvider-Pfade). Die Galerie läuft über die Android-Fotoauswahl (`PickVisualMedia`), **ohne Speicher-Berechtigung**. Die Datei wird sofort in den Cache kopiert und danach wie eine eigene Aufnahme behandelt. HEIC aus der Galerie ist kein Problem: `FotoAufbereitung` rechnet ohnehin in JPEG um (ab Android 9). Eine `CAMERA`-Berechtigung braucht die App dafür nicht, solange das Manifest sie nicht deklariert. Danach öffnet sich ein Dialog mit Vorschau, Bildunterschrift und Ziel-Notiz.

- **Bildunterschrift**: Ein Entwurf im Eingabefeld wird zur Unterschrift. So lässt sie sich vorher per Mikrofon diktieren. Beim Verwerfen wandert der Text zurück ins Feld.
- **Ziel-Notiz**: Vorgabe ist „Neueste von heute“ (`notiz` weglassen). Zur Wahl stehen außerdem die Notiz des letzten Fotos und alle `.md`-Dateien aus dem Gespräch. Folgefotos landen automatisch in derselben Notiz wie das vorige. Ob die Notiz dem Nutzer gehört, prüft der Server (403).
- **Verkleinern vor dem Upload** (`FotoAufbereitung`): lange Seite 2560 px, JPEG 85 %. Die EXIF-Drehung wird dabei ins Bild übernommen, weil das Neukodieren die EXIF-Daten verwirft.
- Nach dem Ablegen erscheint eine Hinweiszeile mit dem Foto als Chip. Auch `hinweis`-Zeilen aus dem Verlauf zeigen jetzt ihre `dateien`.
- **Fehler bleiben im Dialog**, das Foto wird nicht gelöscht. „Ablegen“ versucht es erneut.

#### Ziel wählen: Notiz oder Ordner (seit v1.4.0)

`GET /v1/notizen` liefert die eigenen Ordner und Notizen. Jeder `pfad` geht direkt als `notiz` raus. Ein **Ordner** heißt: an die neueste Notiz darin, sonst legt der Server `<Datum> <Ordnername>.md` an. Pfade ohne `.md` behandelt die App als Ordner.

- **Mediendialog:** Schnellauswahl als Chips („Neueste von heute“, gewähltes Ziel, Ordner des Projekts, letzte Notiz, `.md` aus dem Gespräch), dazu **„Andere …“** mit der ganzen Liste und Suchfeld (`ZielDialog`, Ordner zuerst).
- **Anheften:** Der Dialog zeigt „Deine Ordner“ als Vorschläge. Ein gewählter Ordner, oder ein getippter bzw. zuletzt benutzter Name, der genau so heißt wie ein Ordner, wird zum **`kontextZiel`**: Fotos und Videos gehen dann ohne weiteres Zutun dorthin. Wechselt das Projekt, verfallen letzte Notiz und Serie, damit das nächste Foto nicht unbemerkt im vorigen Projekt landet.
- Geladen wird beim Start des Chats und bei jedem Öffnen der beiden Dialoge.

#### Ordneransicht (`ui/ablage/`, seit v1.4.0)

`GET /v1/ordner?pfad=`. Ist `pfad` eine Datei, kommt ihr Ordner, und die App markiert die Datei und scrollt dorthin. Einstieg: **langer Druck auf einen Datei-Chip** („Im Ordner zeigen“) oder „Ablage“ in der Schublade. Tippen auf einen Chip öffnet die Datei weiterhin direkt, weil das der häufigere Fall ist. Zurück führt erst durch die besuchten Ordner, dann in den Chat. `geaendert` kommt hier schon als Ortszeit `TT.MM.JJJJ HH:MM` und wird **nicht** umgerechnet. `dateiOeffnen()` liegt dafür jetzt gemeinsam in `ui/DateiOeffnen.kt`.

#### Suche, Sortierung, Seiten und Links (seit v1.13.0)

- **Suchfeld** oben in der Ablage, immer sichtbar. Sucht beim Tippen ab 2 Zeichen, 300 ms entprellt, die vorige Anfrage wird abgebrochen (`AblageViewModel.suchen()`). Vorgabe ist die ganze Ablage, der Chip „Nur in diesem Ordner“ schickt `ordner=` mit (auf der obersten Ebene ausgeblendet). Treffer: Tippen öffnet Datei bzw. springt in den Ordner, **langer Druck auf eine Datei** = „Im Ordner zeigen“. Zurück schließt erst die Suche.
- **Sortierung** im Kopf (Name A–Z / Neueste Projekte zuerst / Zuletzt geändert). Die Werte stehen in `ORDNER_SORTIERUNGEN`, ein Test vergleicht sie mit dem Vertrag. Die Wahl gilt nur, solange die Ansicht offen ist.
- **Seitenweise** zu 100 Einträgen, nachgeladen kurz vor dem Listenende. Soll eine Datei markiert werden, die nicht auf Seite 1 steht, holt die App bis zu 10 Seiten nach, bis sie auftaucht.
- **`projekt`** erscheint als Untertitel: im Ordner „Baustelle · Status“, in Treffern „Nr · Kunde · Baustelle“.
- **Chat:** Hat eine Antwort `links`, zeigt die Blase je Link eine Karte statt der `dateien`-Chips. Datei: „Öffnen“, „Im Ordner zeigen“, Teilen. Ordner: „Ordner öffnen“. Ohne `links` (ältere Gespräche) bleiben die Chips.

**Am Gerät geprüft (2026-10-13):** Suche „Boese Rechnung“ mit Projekt-Untertiteln, langer Druck → Ordner „Rechnungen“ mit markierter Datei, der alte Ordner „Projekte“ ist ausgeblendet, Link-Karte im Chat → „Öffnen“ zeigt das PDF in Acrobat. **Ungeprüft:** Sortierung, Nachladen weiterer Seiten, „Nur in diesem Ordner“, Teilen aus der Karte.

Am Gerät geprüft (2026-09-24): Anheften zeigt den Ordner „Begehung Simon-Dach-Straße 46“, das Foto hat ihn danach vorgewählt, „Andere …“ listet Ordner und Notizen, ein Foto per Liste an die Testnotiz gehängt, langer Druck → Ordner mit markiertem Foto (232,4 KB), „Nach oben“ und die Zurück-Taste funktionieren. In den echten Projektordner wurde **nichts** hochgeladen.

#### Medienkorb: Funklöcher und Hintergrund-Upload

`Medienkorb` ist das Gegenstück zum `Ausgangskorb`, verschlüsselt in `filesDir/medienausgang`: je Aufnahme ein bereits aufbereitetes `<zeit>.medium.enc` und ein `<zeit>.json.enc` (`MedienAuftrag`: Art, Notiz, Unterschrift, `gespraech_id`, Serie, `abschreiben`). Fotos und Videos teilen sich **bewusst einen Korb**: Was bei einer Begehung entsteht, gehört in dieselbe Notiz und in Aufnahme-Reihenfolge. Den Versand übernimmt derselbe `VersandArbeiter`, erst die Sprachnotizen, dann die Medien, **ältestes zuerst**. Er wird mit `APPEND_OR_REPLACE` angestoßen. `REPLACE` würde einen laufenden Video-Upload abbrechen, sobald ein Foto dazukommt.

- **Wann es in den Korb geht:** Videos **immer**, wie `API.md` es verlangt: Hintergrund-Upload über WorkManager. Fotos bei `KeineVerbindung`, und immer dann, wenn schon etwas wartet. Ein neues Foto darf die wartenden nicht überholen, sonst stimmt die Reihenfolge in der Notiz nicht.
- **Serie:** Fotos ohne feste Notiz, die hintereinander im Korb landen, teilen sich eine Serien-ID. Sobald das erste davon angekommen ist, schreibt `serieFestlegen()` dessen `notiz` in die übrigen. Ohne das landeten sie in der Notiz, die *beim Nachsenden* die neueste ist (Hinweis aus `API.md`).
- **404/403 beim Nachsenden** (Gespräch inzwischen gelöscht, Notiz weg oder fremd): erst ohne `gespraech_id`, dann ohne `notiz` erneut versuchen. Lieber in der falschen Notiz als verloren.
- **Wiederholt** wird bei fehlender Verbindung, 401, 502 und 5xx. **Verworfen** wird nur bei 413, 415 und 422, weil sich das bei jedem Versuch wiederholt.

Der Zeitstempel im Dateinamen des Fotos ist der **Zeitpunkt des Hochladens**, nicht der Aufnahme. Das legt der Server fest. Bei langen Funklöchern steht also eine spätere Uhrzeit drin. Behebbar wäre das nur mit einem neuen Feld im Vertrag, etwa `aufgenommen`.

#### Am Gerät geprüft (2026-09-24)

| | Ergebnis |
|---|---|
| Kamera → Dialog | Vorschau richtig gedreht, Testnotiz aus dem Gespräch als Ziel angeboten |
| Ablegen online | „Foto abgelegt in „2026-09-24 App-Test Fotos““ mit Foto-Chip |
| Folgefoto | Ziel-Notiz des vorigen Fotos vorausgewählt |
| **Flugmodus** | 648 KB im Korb, **keine JPEG-Signatur**, Begleitdaten ohne lesbaren Text, Kamera-Cache leer |
| Netz zurück | Korb nach etwa 25 s von selbst leer, laut Assistent 2 Fotos an der Notiz (07:37:31, 07:38:53) |

Ungeprüft: die Serienlogik (mehrere Fotos ohne feste Notiz im Funkloch) und der 404/403-Rückfall.

Testdaten auf dem Server: `Notizen/Sascha/2026-09-24 App-Test Fotos.md` mit zwei Fotos. Kann gelöscht werden.

#### Videos

- **Verkleinern auf 720p** (kurze Seite, H.264, 2,5 Mbit/s) mit Media3 `Transformer` 1.11.1 in `VideoAufbereitung`. Der Server rechnet nicht um. Hochkant bleibt hochkant. Ist das Video schon ≤ 720p, bleibt es unangetastet. Scheitert die Umrechnung, geht das Original raus, sofern es unter 1 GB liegt.
- **Grenzen vorab:** über 30 min meldet der Dialog einen Fehler (`MediaMetadataRetriever`), über 1 GB ebenso. Die Kamera-App bekommt `EXTRA_DURATION_LIMIT` = 30 min und zeigt das an („30 m“).
- **Abschreiben:** Chips „Automatisch / Immer / Nie“ im Dialog, Vorgabe `auto`. Die Werte stehen einmalig in `ABSCHREIBEN_WERTE`, ein Test vergleicht sie mit dem Vertrag.
- **Timeout:** `VideoTimeoutInterceptor` gibt nur `/v1/videos` 300 s Lese- und Schreib-Timeout (`API.md`: mindestens 300 s).
- **Fortschritt:** Der Upload zählt mit (`FortschrittsKoerper`) und meldet über `setProgressAsync`. Das ViewModel beobachtet den Arbeiter und zeigt oben im Chat „Video wird hochgeladen … N %“. Nach dem Ende kommt eine Hinweiszeile, bei laufender Abschrift mit „Gesprochenes wird abgeschrieben und erscheint in der Notiz“.
- **Grenze von WorkManager:** Ohne Vordergrunddienst etwa 10 min. Videos und Dokumente laufen deshalb seit 2026-10-13 mit `setForeground` (Typ `dataSync`, Benachrichtigung „wird hochgeladen“). Lehnt Android das ab (aus dem Hintergrund ab Android 12), geht es ohne weiter.

#### Am Gerät geprüft: Video und Galerie (2026-09-24)

| | Ergebnis |
|---|---|
| Kamera-Menü | Foto / Video / Galerie erscheinen |
| Video aufnehmen | Kamera zeigt die 30-min-Grenze, Vorschau im Dialog hochkant |
| **Verkleinern** | 6 s FHD/60 = **35 MB → 5,2 MB** im Korb, nach etwa 2,5 s |
| Hintergrund-Upload | Korb und Cache danach leer, Hinweis „Video abgelegt in … – Gesprochenes wird abgeschrieben …“ |
| Galerie | Testbild ausgewählt → Dialog mit Vorschau. Hochladen bewusst nicht getestet (keine Testnotiz zur Wahl, sonst Gefahr einer echten Notiz). Derselbe Weg wie Kamerafotos. |
| Verlauf | Server-Hinweise zu Fotos zeigen ihre Foto-Chips |

Ungeprüft: Fortschrittsbalken beim Verkleinern und beim Upload (5 MB waren zu schnell, um ihn zu sehen), Video im Funkloch, MOV aus der Galerie.

### Dokumente zur Notiz (`POST /v1/dokumente`, seit v1.12.0)

Grundlage für Angebote: LV, Pläne, Kundenmail, Excel-Listen. **Ein Dokument pro Anfrage**, max. 50 MB, Typ erkennt der Server **am Dateinamen** (PDF, DOCX, XLSX/XLSM, TXT, MD, CSV, TSV, EML).

- **Zwei Wege hinein:** „Dokument“ im Kamera-Menü (`OpenDocument`, ohne Speicher-Berechtigung) und **„Teilen → Chef-Assistent“** (`ACTION_SEND` in `MainActivity`, gleiche Typen). Geteiltes springt von jeder Ansicht in den Chat, ohne Anmeldung wartet es bis danach. Nach einer Drehung wird der Intent nicht erneut verarbeitet. Teilen von reinem Text (Link, Mailausschnitt) hängt ihn ans Eingabefeld.
- **Name:** `OpenableColumns.DISPLAY_NAME`, fehlt die Endung, kommt sie aus dem Mime-Typ. Der Name geht unverändert als Dateiname des Multipart-Teils raus.
- **Dialog** wie bei Fotos: Name und Größe, „Beschreibung (optional)“ (`text`), Ziel-Notiz. Ist schon ein Medien-Dialog offen, wird ein geteiltes Dokument abgelehnt statt ihn zu ersetzen.
- **Versand wie Fotos:** mit Netz sofort (Fortschritt im Dialog), damit `lesbar`/`hinweis` gleich sichtbar sind. Ohne Netz oder wenn schon etwas wartet in den **Medienkorb** (`ART_DOKUMENT`, `dateiname` im `MedienAuftrag`). `/v1/dokumente` bekommt wie Videos 300 s Timeout.
- **`lesbar: false`** → zweite Hinweiszeile mit `hinweis` (Scan, Passwort). **415** (z. B. alte `.doc`) bleibt mit `detail` im Dialog.
- **Ohne Gespräch:** Wird ein Dokument abgelegt, bevor es eine `gespraech_id` gibt, erfährt der Assistent nichts davon. Die nächste Nachricht bekommt dann die Zeile ``Ich habe dir das Dokument `<pfad>` geschickt.`` vorangestellt, wie beim Projektkontext als Satz. Nach der ersten Antwort fällt sie weg. Aus dem Korb nachgesendete Dokumente ohne Gespräch werden **nicht** so gemeldet.
- Nebenbei behoben: `VersandArbeiter` zählte Fotos schon vor dem Upload, ein gescheiterter Versuch erschien als „nachgesendet“.

Am Gerät geprüft (2026-10-12): Menüpunkt „Dokument“, Systemauswahl zeigt PDF/TXT/MD, Dialog mit „Zeichnung_EFH_120qm.pdf · 67 kB“, Verwerfen. Teilen-Ziel für `application/pdf` und `message/rfc822` registriert (`cmd package query-activities`). **Ungeprüft:** echter Upload (legt eine Notiz auf dem Server an), `lesbar: false`, 415, Funkloch, Teilen aus Gmail.

### Hintergrundaufträge (`ui/auftraege/`, seit v1.6.0)

Versicherungsberichte laufen 5–20 min **auf dem Server**. Gestartet werden sie im Chat, die App zeigt nur den Stand.

- **Ansicht „Aufträge“** in der Schublade: Status, `schritt` mit Balken aus `fortschritt`, bei `fertig` `zusammenfassung`, `dateien` als Chips (Tippen öffnet, „Im Ordner zeigen“) und `offene_punkte` als Prüfliste „Vor dem Versand prüfen“. Die Haken leben nur auf dem Bildschirm, der Vertrag hat kein Feld dafür.
- **Abbrechen** fragt nach (ein versehentlicher Tipp würde bis zu 20 min Arbeit wegwerfen). **Neu starten** bei `fehler`/`abgebrochen`. Ein `409` lädt still neu.
- `fehler` bei `wartet` heißt, dass der Server es selbst erneut versucht. Es wird grau als „Neuer Versuch folgt: …“ angezeigt, nicht rot.
- **Abfragen nur, solange sichtbar** (`LifecycleResumeEffect`): Ansicht alle 20 s, Chat alle 30 s, und jeweils nur, solange etwas `wartet`/`laeuft`. Nach jeder Chat-Antwort fragt die App einmal nach, damit ein gerade gestarteter Auftrag sofort auftaucht.
- **„Gesehen“** merkt sich `Einstellungen.geseheneAuftraege` (höchstens 200 IDs). Fertige, noch nicht gesehene Aufträge werden hervorgehoben und zählen im Chat mit. Das Symbol mit Zähler in der Chat-Leiste erscheint nur, wenn der Zähler > 0 ist.
- Wird ein Auftrag fertig, der zum offenen Gespräch gehört, lädt der Chat den Verlauf neu (das Ergebnis steht dort als `hinweis`). Das passiert aber nicht, während eine Anfrage läuft, aufgenommen oder vorgelesen wird.
- **Auftragsart** (seit v1.8.0): über dem Titel steht „Versicherungsbericht“ (Schild) oder „Begehungsprotokoll“ (Checkliste), unbekannte Werte als „Auftrag“. Die Werte stehen in `AUFTRAG_ARTEN`, ein Test vergleicht sie mit dem Vertrag. Beim Begehungsprotokoll heißt die Liste „Offene Punkte“ statt „Vor dem Versand prüfen“: es ist Angebotsgrundlage und wird nicht versandt. Die Punkte stehen zusätzlich in `GET /v1/aufgaben`.

Am Gerät geprüft (2026-09-26): Eintrag in der Schublade, Ansicht lädt, der Server liefert eine leere Liste. Am 2026-09-28 mit v1.8.0 erneut: leere Liste, neuer Leertext mit Begehungs-Beispiel. **Ungeprüft:** alles mit einem echten Auftrag (Fortschritt, Fertig-Karte, Abbrechen, Neu starten, Verlauf-Nachladen). Dafür müsste ein echter Bericht gestartet werden.

### Aufgaben „Offen“ (`ui/aufgaben/`, seit v1.7.0)

Aufgaben sind Häkchen-Zeilen in Notizen. **Angelegt wird im Chat**, die App zeigt und hakt nur ab.

- **Ansicht „Offen“** in der Schublade (oberhalb von Termine), Zähler aus `offen`. Gruppen in der Reihenfolge aus `API.md` (`AufgabenUebersicht.gruppen`), leere fallen weg, „Überfällig“ rot.
- Je Aufgabe: Häkchen (`erledigt`/`offen`), Text, `bis` als TT.MM., „für X“ bei `an_andere`, sonst „von X“, wenn es nicht die angezeigte Person ist. Der Notizname öffnet die Notiz (`dateiLaden`, ohne `anzeigen`, das ist laut `API.md` für Android egal).
- `nachfragen` → „Noch aktuell?“ mit **Bleibt** (`noch_aktuell`) und **Weg damit** (`gestrichen`). Streichen fragt nach, weil die Aufgabe danach aus jeder Gruppe verschwindet und in der App nicht zurückzuholen ist. „Neue Frist“ gibt es an jeder offenen Aufgabe, per Kalender, samt „Frist entfernen“ (`bis: ""`).
- **Nach jedem POST neu laden**, ein 404 dabei ist kein Fehler (die ID hängt am Text). Die Ansicht lädt bei jedem Resume neu, der Chat-Zähler bei Resume und nach jeder Antwort.
- **Andere Personen** (`?person=`): Es gibt keinen Endpunkt mit allen Namen. Die App sammelt die Namen aus `von`/`fuer` der geladenen Aufgaben und bietet sie als Chips an („Meine“ + Namen).
- `AufgabeAenderung` verlässt sich auf `explicitNulls = false`, ein Test sichert, dass nur gesetzte Felder rausgehen.

Am Gerät geprüft (2026-09-28): Eintrag in der Schublade, Ansicht lädt, der Server liefert eine leere Übersicht. **Ungeprüft:** alles mit echten Aufgaben (Abhaken, Nachfragen, Frist, Personenwahl). Dafür müsste im Chat eine Aufgabe angelegt werden, und die landet in einer echten Notiz.

### GU-Plantafel (`ui/plantafel/`, seit v1.9.0)

GU-Projekte sind **gemeinsame** Ordner `GU-Projekte/<Projekt>` mit je einer Plantafel: Zeilen = Gewerke, Balken = Einsätze (ganztägig). Einstieg „GU-Plantafel“ in der Schublade.

- **Projektliste** (`GET /v1/bauablauf`) mit „Neues GU-Projekt“ (`POST`, Name max. 100 Zeichen).
- **Tafel** (`GET /v1/bauablauf/{id}`): Gewerknamen links fest, Tage waagerecht scrollbar, Wochenenden abgesetzt, Heute-Linie. **`bis` ist einschließlich.** Status: `geplant` blass/gestreift, `bestaetigt` voll, `fertig` gedämpft mit ✓, `abgesagt` ausgeblendet. Überlappende Einsätze eines Gewerks liegen auf eigenen Spuren.
- **Warnungen** oben als sandfarbene Karten, betroffene Balken rot umrandet.
- **Balken antippen** → Sheet mit Details (Telefon antippbar), Bearbeiten per `PATCH` **nur mit geänderten Feldern**. Nur verschoben heißt: `von` geht raus, `bis` nicht, die Dauer bleibt. Hat der Einsatz Nachfolger (`nach` = seine ID), fragt die App „Nachfolgende mitverschieben?“. Löschen fragt nach.
- **Gewerk antippen** → Firma, Kontakt, Telefon, Farbe (`PUT`). Löschen nur ohne Einsätze, mit Rückfrage.
- **PDF** in der Kopfleiste → `POST …/pdf` → Datei öffnen. **Ordner** → Ablage.
- Nach jeder Änderung und bei jedem Resume neu laden.
- `GET /v1/bauablauf/alle` ist in Repository und API vorhanden, eine Gesamtansicht gibt es noch nicht (laut `API.md` optional).
- **Ziel-Dialog für Fotos/Videos:** GU-Projektordner als eigene Gruppe „GU-Projekte“ (`NotizOrdner.istGuProjekt`).

Am Gerät geprüft (2026-09-28): Eintrag in der Schublade, Projektliste lädt, der Server liefert keine Projekte. **Ungeprüft:** alles mit einem echten Projekt (Tafel, Sheets, PDF, Mitverschieben). Ein Testprojekt legt einen **gemeinsamen** Ordner an, den alle sehen. Deshalb nicht ohne Rückfrage anlegen.

### Kalender (`ui/kalender/`, seit v1.16.0)

Ersetzt die alte Termine-Ansicht (`ui/termine/` ist gelöscht). Der Schubladeneintrag heißt jetzt „Kalender“, die Route ist weiter `ZIEL_TERMINE`. `GET /v1/termine` steht noch in Repository und API, wird aber nicht mehr angezeigt. Auftrag: `AUFTRAG-APP-1.16.md`, API.md 5a.

- **Lesen und seit v1.18.0 auch HERO-Termine anlegen und verschieben** (siehe unten). Eigene Termine ändert der Chat. Die aufgeklappte Karte zeigt „Verschieben“ oder den Hinweis „Aus HERO – ändern nur in HERO“ bzw. „ändern im Chat“.
- **Kopf und Chips stehen fest**, nur die Tagesliste scrollt. Vorher sprang die Liste zu „heute“ und schob ‹ Heute › und die Chips aus dem Bild.
- **Woche** = Montag bis Sonntag, **Tag** = ein Tag. ‹ › und Wischen blättern, Ziehen aktualisiert (holt auch `personen` neu). Die Wochenansicht springt nach dem Laden zu „heute“.
- **Chips:** Ich (immer der Schlüssel `ich`, damit die eigenen Termine mitkommen), die anderen App-Nutzer mit Vornamen, „Monteure ▾“ und „Alle“. Mehrfachauswahl, nie leer, „Alle“ schließt die anderen aus. Gemerkt in `Einstellungen.kalenderPersonen`.
- **Farben:** Palette der Diagramme (`FARBEN_HELL`/`FARBEN_DUNKEL`, API.md 1a) in fester Reihenfolge (ich, App-Nutzer, Monteure). Zugeordnet wird über `hero_id` oder `app_benutzer`. Termine ohne gewählte Person bekommen einen neutralen Rand.
- **Mehrtägige Termine** stehen an jedem Tag, den sie berühren, mit Datum („5.10. 02:01 – 14.10. 01:59“). Die App legt doppelte IDs zusammen. Damit ist egal, ob der Server sie je Tag wiederholt. Ein Ende um Mitternacht zählt den Folgetag nicht mit.
- `personen` wird 5 Minuten im Repository zwischengespeichert, bei Zugangswechsel verworfen. **503** dort heißt kein HERO-Spiegel, dann gibt es nur „Ich“. `spiegel: false` zeigt den Hinweis „HERO-Termine nicht verfügbar, nur eigene“.
- Adresse antippen öffnet die Karten-App (`geo:`), „Projektordner“ öffnet die Ablage mit `projekt.pfad`.

Am Gerät geprüft (2026-10-13), als Sascha: Woche mit echten HERO-Terminen, heute oben, Patrick dazugewählt (orange, Punkt im Chip passt), Termin aufgeklappt mit Adresse, Namen und Hinweis, „Projektordner“ öffnet SAN-1296 im HERO-Archiv, Wischen zur nächsten Woche, „Monteure ▾“ klappt 14 Namen auf, „Heute“ springt zurück. Auswahl danach wieder auf „Ich“ gestellt. **Ungeprüft:** Tag-Ansicht, Ziehen zum Aktualisieren, Karten-App, `spiegel: false`, Hell-Modus. **Nicht gebaut:** das optionale Widget „Heute“.

#### HERO-Termine anlegen und verschieben (seit v1.18.0)

Auftrag `AUFTRAG-APP-1.18.md`, API.md 5b. Code: `HeroTerminLogik.kt` (Formular, Anfragen, Prüfregeln, testbar), `HeroTerminSheet.kt` (Oberfläche), `KalenderViewModel` (Abschicken).

- **Nur bei `hero_schreiben: true`.** Dann gibt es „＋ HERO-Termin“ in der Kopfleiste und „Verschieben“ in der aufgeklappten Karte jedes HERO-Termins mit `nur_lesen: false`. Ein alter Server ohne das Feld bedeutet: nur lesen.
- **Hinweis unter der Karte nach `quelle`, nicht nach `nur_lesen`.** Seit 1.18 sind HERO-Termine meist `nur_lesen: false`. Mit der alten Abfrage hätten sie „Eigener Termin – ändern im Chat“ gezeigt.
- **Neu:** Für (Mehrfachauswahl, „Ich“ vorbelegt, wenn `personen.ich` gesetzt ist; Monteure aufklappbar), Titel, Kategorie aus `GET /v1/kalender/kategorien` (Pflicht, ohne Vorauswahl, Farbpunkt), Schalter „Ganztägig“ (dann Datum und Bis Datum, höchstens 14 Tage), Projekt, Notiz. In der Tagesansicht ist der angezeigte Tag vorbelegt, sonst heute ab der nächsten vollen Stunde.
- **Verschieben:** Es gibt nur Datum, Von und Bis, dazu oben „bisher …“. Ein mehrtägiger Termin bekommt zusätzlich „Bis Datum“. **Ein neuer Beginn schiebt das Ende mit**, die Dauer bleibt (10:00 → 10:00–11:00, wie in der Abnahme). Ein neues Datum verschiebt auch das Bis-Datum. Das `ende` geht immer ausdrücklich mit.
- **Zwei Schritte:** „Prüfen“ (`nur_pruefen=true`) zeigt `beschreibung`, die Überschneidungen mit ⚠ in Fehlerfarbe bzw. „Keine Überschneidungen“ und darunter `hinweis`. Danach heißt der Knopf „In HERO eintragen“ bzw. „In HERO verschieben“. **Jede Änderung löscht die Prüfung** (`HeroFormular.aendern`). Erfolg: Formular zu, Snackbar, Kalender neu laden.
- **Fehler** (422 und alles außer 401) zeigen `detail` im Formular, die Eingaben bleiben stehen. 401 führt zur Anmeldung.
- **Doppelt senden:** Der Knopf ist gesperrt, solange `laeuft`, und `formularAbschicken` prüft das zusätzlich. Schließt man das Formular während des Eintragens, wird die Anfrage **nicht** abgebrochen, sie kann schon in HERO angekommen sein. Eine laufende Prüfung wird dagegen abgebrochen.
- Zeiten gehen als Ortszeit ohne Zone (`YYYY-MM-DDTHH:MM`) raus. Die App nimmt an, dass die Gerätezone der Ortszeit des Servers entspricht.
- Der Chat braucht nichts Neues: HERO-Termine aus dem Chat laufen über die bekannten Ja/Nein-Bestätigungen.

Am Gerät geprüft (2026-10-14), als Sascha, **ohne etwas in HERO zu schreiben**: „＋ HERO-Termin“ erscheint, „Verschieben“ an „Badezimmer Besichtigung“, Formular mit „bisher Mi., 14.10.2026 13:00–16:00“, „Prüfen“ liefert die Beschreibung und „Keine Überschneidungen“, der Knopf wechselt auf „In HERO verschieben“ (nicht angetippt, abgebrochen, Termin unverändert). Neues Formular mit „Ich“ vorbelegt, alle 11 Kategorien mit Farbe. **Vom Nutzer am Gerät geprüft (2026-10-14):** einen echten eigenen Termin von heute verschoben, in HERO korrekt angekommen. Einen neuen Termin in eine belegte Zeit gelegt: Die ⚠-Überschneidung erscheint, „In HERO eintragen“ geht trotzdem, der Termin wird angelegt (gewollt, wie im CRM). **Ungeprüft:** 422 (Dublette), ganztägig, Termin für andere Personen. **Abnahme nur zwischen Sascha und Patrick**, nicht bei Monteuren (Vorgabe des Nutzers, 2026-10-14). Den Testtermin danach in HERO löschen, die App kann nicht löschen.

### Angebote (`ui/angebote/`, seit v1.11.0)

Angebote entstehen und ändern sich **im Chat** (Angebotsgenerator auf dem Server). Die App liest nur und gibt die UGL weiter.

- **Liste** (`GET /v1/angebote`) in der Schublade unter „Angebote“. Der Eintrag **verschwindet**, wenn der Server beim Chat-Start 503 meldet (nicht eingerichtet). Netzfehler lassen ihn stehen.
- **Vorschau** (`GET /v1/angebote/{id}`): Kopf mit Version, Bearbeiter, Zeit. Darunter eine Karte mit `ek_summe` (Material EK) und `pauschalen_summe` (VK), `hinweise` als Karten, „Zu prüfen“ (offene `fragen` mit Positions-Chips, `markiert`) und danach die `gruppen` (`ebene` 2 eingerückt) mit Positionen. Ein Tipp auf einen Chip oder einen markierten Eintrag springt zur Position und hebt sie hervor.
- **Beträge und Mengen sind Strings** und laufen über `BigDecimal` (`ui/angebote/Format.kt`), nie über `Double`. Pauschalen (`pauschale: true`) zeigen `vk`/`vk_gesamt`, alles andere `ek`/`ek_gesamt`.
- **Markierungen:** `pruefen` rot, `alternative` gelb, `info` blau, Unbekanntes grau.
- **UGL:** Teilen-Knopf → `GET …/ugl` → `cache/dateien/<Name aus Content-Disposition>` → Android-Teilen-Menü (`dateiTeilen()`). `dateinameAusDisposition()` nimmt `filename*` vor `filename` und wirft Pfadanteile weg.
- **Web-Vorschau** als Knopf, nur wenn `vorschau` gesetzt ist.
- Fehler: 404 und 503 als Hinweis in der Liste, 502 mit „Nochmal versuchen“.
- Der Vertrag hat für die Antworten **kein Schema** (`{}`). Die DTOs folgen `API.md` 4e und haben fast überall Vorgaben.

**Entscheidungen (seit v1.15.0, Auftrag `AUFTRAG-APP-1.15.md`):** `fragen`, `markiert` und `hinweise` werden **nicht mehr angezeigt** (DTOs bleiben lesbar), stattdessen oben „Entscheidungen (N)“ aus `punkte`.
- Je Punkt eine Karte: farbige Kopfzeile (`frage`/`pruefen` rot, `alternative` gelb, `hinweis` grau) mit `ort`, Text fett, Positions-Kacheln (Tippen springt zur Position, Hervorhebung 2,5 s), `optionen` als Knöpfe, bei `frei` „Eigene Antwort …“, dazu „Passt so“ (bei `hinweis` „Ausblenden“) = `ANTWORT_PASST`.
- `POST …/antworten` gibt das ganze Angebot zurück, das ersetzt die Ansicht. Fehler kommen als Meldung, der Entwurf bleibt im Feld. Bei 404 lädt die App neu.
- Ab 6 Punkten erst 5 (`PUNKTE_SICHTBAR`), dann „N weitere anzeigen“.
- `beantwortet` eingeklappt, darunter **„Antworten einarbeiten“**. Das Angebot hat **keine `gespraech_id`**, deshalb öffnet der Knopf ein **neues Gespräch** und schickt „mach weiter, arbeite meine Antworten im Angebot <titel> ein“ (`Geteilt.Senden` → `ChatViewModel.vonAussenSenden`). Läuft im Chat gerade eine Anfrage, landet der Text nur im Eingabefeld.
- Positionen als Karten: Nr. + Menge links, Name mit Umbruch, Gesamtpreis rechts, darunter EK/VK (bei Menge ≠ 1 mit Einzelpreis). Rechenwege (`info`) eingeklappt als „Rechenweg ▸“.
- Kopf: Titel einzeilig (`GrosseKopfleiste(titelZeilen = 1)`), Summen nebeneinander, Web-Vorschau und UGL im ⋮-Menü.

Am Gerät geprüft (2026-10-13), Angebot „EFH ca. 120 m²“: 7 Punkte → 5 Karten + „2 weitere anzeigen“, Rechenweg aufklappbar, Positionskarten. **Nicht angetippt:** Optionen, „Passt so“, eigene Antwort und „Antworten einarbeiten“. Das ändert echte Angebote. Ungeprüft sind deshalb auch die Positions-Kacheln (das Angebot hatte keine Punkte mit Positionen) und der Sprung.

Am Gerät geprüft (2026-10-09): Eintrag in der Schublade, Liste mit den zwei echten Angeboten (PET44, GRB10), Vorschau PET44 v25 mit Summen, Hinweisen und Fragen, Sprung über „Pos. 6.2.001“, UGL geladen (13 KB, Name aus dem Header) und das Teilen-Menü geöffnet. **Nichts verschickt.** **Ungeprüft:** 503/502/404 live, Web-Vorschau-Link. Offen ist auch, ob `geaendert` ohne Offset wirklich Ortszeit ist (die App nimmt das an).

### Gestaltung: One UI 8 + Material 3 Expressive

Am 2026-09-28 abgeglichen mit Einstellungen, Eigene Dateien und Reminder (Samsung) sowie Google Play auf dem S25. Bausteine in `ui/komponenten/Gestaltung.kt`, **neue Ansichten bitte damit bauen**:

| Baustein | Wofür |
|---|---|
| `GrosseKopfleiste` | Samsung-Kopf: großer **zentrierter** Titel im oberen Drittel (30 % der Höhe), schrumpft beim Scrollen auf 64 dp. Knöpfe unten im Kopf, näher am Daumen. Braucht scrollbaren Inhalt. |
| `GruppenBlock` / `gruppenForm` | Eintrag in einer Gruppe: zusammen eine Karte mit 26 dp Rundung, **eingerückte** Trennlinien dazwischen (`einzug`, `EINZUG_MIT_SYMBOL`). Für LazyColumns. |
| `GruppenKarte`, `GruppenTitel` | Ganze Gruppe als Karte bzw. kleine Überschrift darüber |
| `RundesSymbol` | Symbol im farbigen Kreis (Schublade, Ablage) |
| `LeerHinweis`, `FehlerSnackbar`, `listenAbstand` | Leerzustand als Karte, Serverfehler, Innenabstand unter der Kopfleiste |

- **Farben** (`theme/Theme.kt`): Dunkel ist der Hintergrund **schwarz** wie bei Samsung (OLED), die Karten `surfaceContainer` liegen darüber. Hell: grauer Grund, weiße Karten. Die `surfaceContainer*`-Stufen **müssen** im Schema stehen, sonst nimmt Material die violette Vorgabe.
- **Chat:** Eingabe als Pille (Kamera, Feld, Mikrofon) mit rundem Senden-Knopf daneben, Sprechblasen nur so breit wie der Text (max. 85 %), **Abmelden im ⋮-Menü** statt als Symbol neben „Neu“.
- **Schublade** wie die Samsung-Einstellungen: eine Liste, Gruppen als Karten, farbige Kreise je Bereich, darunter Einstellungen und Gespräche.
- Hell und dunkel am Gerät geprüft. `cmd uimode night` wurde dafür kurz umgestellt und zurückgesetzt.

### Zwei Stellen, die bewusst offen gebaut sind

- **`GET /v1/verbrauch` hat im Vertrag kein Schema** (`{}`). Deshalb `JsonElement` statt eines erfundenen Datentyps, und der Bildschirm stellt generisch dar, was ankommt. Beobachtete Form am 2026-09-23: Liste von Objekten mit `monat`, `benutzer`, `eingabe`, `ausgabe`, `anfragen`. **Nicht** hart hineincodieren, solange der Server kein Schema liefert.
- **Zeiten immer in die Gerätezone umrechnen.** Der Server liefert ISO 8601 mit Offset, teils in UTC (`geaendert` kam als UTC). Ohne `atZoneSameInstant(ZoneId.systemDefault())` steht im Sommer zwei Stunden zu wenig auf dem Bildschirm — das war schon einmal ein Fehler.

### Fehlt noch

`POST /v1/vorlesen` (Server-Stimme; im Repository vorhanden, in der Oberfläche nicht verdrahtet — es läuft Android-TTS) · Widget „Heute“ (optional laut Auftrag 1.16) · Release-Signierung · UI-Tests.

### Testen vom Dev-PC aus

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew assembleDebug
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell am start -n de.chefassistent.app/.MainActivity
```

Screenshot zum Nachsehen: `adb exec-out screencap -p > bild.png`.

## Windows-Client (`windows/`)

Eine **Hülle**, keine eigene Oberfläche: WinForms-Fenster mit WebView2, darin die Weboberfläche des Servers (`https://<server>/`). Auftrag und Abnahme stehen in `WINDOWS-HUELLE.md`. Die Hülle kennt **keinen Zugangsschlüssel**, die Anmeldung liegt im localStorage der WebView (`%LOCALAPPDATA%\ChefAssistent\WebView2`).

| | |
|---|---|
| Stack | .NET 10 (LTS), WinForms, `Microsoft.Web.WebView2` 1.0.4191.47 |
| Ausgabe | `windows\dist\ChefAssistent.exe`, self-contained, eine Datei (~50 MB) + `version.txt` |
| Einstellungen | `%APPDATA%\ChefAssistent\einstellungen.json`: `startUrl`, `schliessenMinimiert`, `tastenkombination`, Fensterlage, zuletzt gemeldete `wurzeln` |

**.NET 10 statt 8** (Abweichung vom Auftrag): .NET 8 läuft im November 2026 aus, 10 ist LTS bis 2028 und auf dem Dev-PC installiert. Self-contained, also braucht kein Büro-PC eine .NET-Installation.

Bauen (PowerShell, aus `windows/`):

```powershell
dotnet test ChefAssistent.Tests      # 66 Tests: Pfadprüfung, Wurzeln, Tastenkombination, Einstellungen
.eroeffentlichen.ps1               # -> dist\ChefAssistent.exe + distersion.txt
```

| Datei | Aufgabe |
|---|---|
| `Ablage.cs` | **sicherheitskritisch**: Pfad prüfen (3.1), Wurzel wählen, M: bevorzugt (3.2), Explorer/Programm starten (3.3) |
| `HauptFenster.cs` | WebView2, Navigation nur auf den Server-Origin, Mikrofon nur für ihn, Nachrichten, Offline-Seite, Infobereich, globale Tastenkombination |
| `VorschauFenster.cs` | zweites Fenster für `window.open` mit `blob:` (PDF-/Bildvorschau). `blob:` gehört zur WebView und lässt sich nicht an Chrome weitergeben |
| `Aktualisierung.cs` | `version.txt` im Update-Ordner lesen. Gibt es dort `*Setup*.exe`, wird sie gestartet, sonst wird die EXE getauscht (laufende EXE umbenennen, neue daneben, Neustart) |

Über den Auftrag hinaus, jeweils mit Grund:
- **Pfadprüfung strenger:** Segmente mit Punkt oder Leerzeichen am Ende werden abgelehnt, weil Windows sie abschneidet und `Geschaeftsfuehrung./x` sonst in den gesperrten Ordner führt. Ebenso `~` im ersten Segment (8.3-Kurzname `GESCHA~1`) und `* ? " < > |`.
- **Regex mit `\z` statt `$`:** `$` lässt in .NET ein abschließendes `
` durch.
- **Quelle der Nachricht** wird per `Uri` verglichen, nicht mit `StartsWith`. Sonst ginge auch `https://<server>.evil.com`.
- **M: nur, wenn erreichbar.** Ein verbundenes M: ohne Büronetz (nur Tailscale, Abnahme Punkt 6) würde sonst hängen. Dann geht es mit der nächsten Wurzel weiter.
- **Offline** heißt: keine HTTP-Antwort. Antwortet der Server mit einem Fehlercode, bleibt seine eigene Seite stehen. Die Offline-Seite fragt alle 15 s mit `HEAD` nach und lädt von selbst neu.
- **Fenstersymbol** kommt zur Laufzeit aus dem Favicon der Weboberfläche. `chef-assistent.ico` ist nur ein Platzhalter für die EXE, `/web/icon.svg` lag nicht vor.

**Server 1.10.0 (2026-09-30, Version 1.1.0, auf `main` gepusht, Commit `26f0ef9`, Repo öffentlich):** `StandardAdresse` ist `https://assistent.mitra-sanitaer.de/`, die alte steht als `TailscaleAdresse` daneben. Die Anmeldung mit Passwort und Code macht die Weboberfläche, die Hülle braucht dafür nichts. **Bestehende Installationen bleiben auf Tailscale:** die Hülle schreibt `einstellungen.json` schon beim ersten Start vollständig, eine gesetzte `startUrl` wird nie umgeschrieben. Wechsel von Hand, siehe README „Server-Adresse wechseln“ (localStorage hängt am Origin, also neu anmelden). Die Offline-Seite rät bei `*.ts.net` zu Tailscale/Büronetz, sonst zur Internetverbindung.

Geprüft am 2026-09-24 auf dem Dev-PC: Start lädt die Anmeldeseite, das Server-Symbol wird übernommen, ein Zweitstart beendet sich sofort, eine falsche `startUrl` zeigt die Offline-Seite.
**Gegen v1.9.0 abgeglichen am 2026-09-28: keine Änderung nötig.** Die Hülle hängt nicht an der API-Version, sondern nur an `WINDOWS-HUELLE.md` (auf dem Server unverändert seit 2026-09-24) und an den Nachrichten, die `app.js` schickt. Neue Funktionen (Aufgaben, Plantafel, Aufträge …) kommen mit der Weboberfläche. `app.js`/`plantafel.js` schicken weiterhin nur `hallo` (Wurzeln aus `GET /v1/freigabe`), `zeigen`, `oeffnen` und empfangen `ergebnis`, `sprechen`. Das Plantafel-PDF und die Notizlinks der Aufgaben laufen über `zeigen`. **Erst wenn `app.js` einen neuen `typ` schickt oder `WINDOWS-HUELLE.md` sich ändert, muss die Hülle nachziehen.** Erneut geprüft am 2026-10-14 gegen v1.18.0: Dazu kam `/web/kalender.js` (Kalender mit HERO-Terminen anlegen/verschieben), die nicht mit der Hülle spricht. Alle vier Skripte (`app.js`, `plantafel.js`, `kalender.js`, `diagramm.js`) senden weiterhin nur `hallo`/`zeigen`/`oeffnen`. Keine Änderung nötig.

**Ungeprüft:** alles mit M:, also Abnahme 2–6 und 9. Dafür braucht es einen Büro-PC. Ungeprüft sind außerdem Mikrofon, Tastenkombination, Drag & Drop und das Update. Punkt 9 ist per Unit-Test abgedeckt, am Gerät braucht es dafür den Debug-Build (`dotnet build`): nur dort sind die Entwicklertools an.

## Vertragsdateien holen

Stand 2026-10-14, v1.18.0 (neu: HERO-Termine anlegen und verschieben, `GET /v1/kalender/kategorien`, `POST /v1/kalender/hero`, `PATCH /v1/kalender/hero/{id}` mit `nur_pruefen`, `hero_schreiben` im Kalender, API.md 5b, Auftrag `AUFTRAG-APP-1.18.md`. Davor v1.17.0 mit langen Anfragen sicher, `anfrage_id` in Chat und Sprache, `GET /v1/chat/{anfrage_id}`, `laeuft` in der Gesprächsliste, kein Lese-Timeout für Chat/Sprache, API.md 1b. Davor v1.16.0 mit Kalender mit HERO-Terminen, `GET /v1/kalender` und `GET /v1/personen`, API.md 5a. Davor v1.15.0 mit Entscheidungen im Angebot, `punkte`, `beantwortet` und `POST /v1/angebote/{aid}/antworten`, API.md 4e „Entscheidungen“. Davor v1.14.0 mit Diagrammen im Chat als ```diagramm-Block in `antwort`, API.md Abschnitt 1a. Davor v1.13.0 mit Suche über die ganze Ablage `GET /v1/suche`, API.md Abschnitt 6a, `links` in Chat und Verlauf, Ordner mit `sortierung`, `ab`/`anzahl`, `gesamt` und `projekt`. Davor v1.12.0 mit Dokumenten zur Notiz, `POST /v1/dokumente`, API.md Abschnitt „Dokumente“ unter 4a. Davor v1.11.0 mit Angeboten aus dem Angebotsgenerator, `/v1/angebote`, API.md Abschnitt 4e. Davor v1.10.0 mit Internet-Adresse und Anmeldung mit Passwort + Zwei-Faktor, `/v1/anmelden`, `/v1/abmelden`, `/v1/geraete`, `/v1/sicherheit*`, API.md Abschnitt 0. Davor v1.9.0 mit GU-Plantafel `/v1/bauablauf`, API.md Abschnitt 4d. Davor v1.8.0 mit Begehungsprotokollen als zweite Auftragsart `art: "begehungsprotokoll"`, API.md Abschnitt 4b-2. Davor v1.7.0 mit Aufgaben „Was ist offen?“ `/v1/aufgaben`, API.md Abschnitt 4c. Davor v1.6.0 mit Hintergrundaufträgen `/v1/auftraege`, API.md Abschnitt 4b. Davor v1.5.0 mit `bilder` in `ChatAnfrage`, bis zu 4 Fotopfade, die der Assistent ansieht, z. B. Typenschild oder Fehleranzeige. Dazu API.md Abschnitt 4a „Foto an den Assistenten“. v1.4.0 brachte `GET /v1/notizen`, `/v1/ordner`, `/v1/freigabe`, v1.2.0 Videos, v1.1.0 Fotos):

```
6b3bda6ac242721361510eec963af4e88db219c64239d19844ba67eb3b5940c8  openapi.json
b89738ad90a2f9da166526bb44fb2af3f95de03d22ffa953902b93fae125cb58  API.md
```

`WINDOWS-HUELLE.md` kommt ebenfalls vom Server, ist aber der Auftrag für den Agenten, der die Windows-App baut, und gehört nicht in dieses Projekt.

### Wie die Dateien herkommen

Per `scp` vom Server (Benutzer `mitraserver`). Seit dem 2026-09-26 klappt das auch **über Tailscale**, dann auch außerhalb des Büros. Der Host-Schlüssel ist derselbe wie unter der LAN-IP (ED25519 `SHA256:d40aDzw4…G3Ic`):

```powershell
scp mitraserver@mitraserver-aspire-xc-605.tail8bd028.ts.net:~/chef-assistent/docs/openapi.json mitraserver@mitraserver-aspire-xc-605.tail8bd028.ts.net:~/chef-assistent/docs/API.md C:/Dev/server_assi/
# im Büro-LAN geht auch: mitraserver@192.168.178.155:...
```

Den Zielpfad **mit Schrägstrichen** schreiben. Über `!` im Claude-Prompt läuft der Befehl in Bash, und die verschluckt Backslashes.

Ohne Zielpfad landen sie im Benutzerordner statt im Projekt — das ist schon passiert.

Über LAN ist nur **SSH** offen: Ping antwortet in 1 ms, aber HTTP auf 80, 443 und 8000 kommt nicht durch. Die API hängt ausschliesslich am Tailscale-Interface. Zum Testen der API bleibt es also beim Handy (siehe Tailnet-Abschnitt oben).

## Erledigte Vertragsänderung: Stimmenwahl pro Nutzer

Backend-seitig gebaut und app-seitig umgesetzt (Details oben unter „Stimmenwahl pro Nutzer“).

**Noch nicht live:** Der Betreiber hat keine sudo-Rechte und kann den Dienst nicht selbst neu starten. Bis das eingespielt ist, läuft weiterhin die alte Serverversion — die App funktioniert dagegen, die gewählte Stimme bleibt aber wirkungslos.

## Gelernt: Gefährliche Knöpfe brauchen eine Rückfrage

Am 2026-09-23 sind beim automatisierten Testen über `adb` **zwei echte Gespräche des Nutzers gelöscht** worden: die Schublade war noch offen, und die Tipp-Koordinate für den Vorlese-Knopf lag auf einem Papierkorb-Symbol in der Gesprächsliste. `DELETE /v1/gespraeche/{gid}` ging sofort raus, ohne Rückfrage.

Zwei Konsequenzen, beide umgesetzt:

- **Löschen fragt nach.** `AlertDialog` mit Gesprächstitel und dem Hinweis, dass es nicht umkehrbar ist. Der Server verlangt für Verschieben und Löschen eine Bestätigung — ein ganzes Gespräch zu löschen darf nicht harmloser sein.
- **Zurück schließt die Schublade.** Vorher verließ die Zurück-Taste bei offener Schublade die ganze App (`BackHandler` in `ChatScreen`).

**Regel fürs Testen am Gerät:** vor jedem Tippen in die Nähe einer Liste erst einen Screenshot prüfen. Blind auf gemerkte Koordinaten zu tippen ist auf einem Gerät mit echten Daten nicht zulässig.

## Offene Entscheidungen (nicht eigenmächtig festlegen)

- ~~**Dev-PC ins richtige Tailnet?**~~ — erledigt, siehe oben (Stand 2026-09-28).
- **Einrichtung beim Nutzer:** wie Basis-URL und Token in die App kommen (aktuell manuelle Eingabe; QR-Code oder Deeplink wären bequemer).
- ~~**Windows-Stack**~~ — entschieden durch `WINDOWS-HUELLE.md`: WinForms + WebView2, siehe „Windows-Client“.

## Arbeitsregeln

- **Nichts erfinden, was nicht im Vertrag steht.** Kein Endpunkt, kein Feld, kein Statuscode aus dem Bauch. Im Zweifel `openapi.json` lesen — nicht `API.md`, das ist die Erläuterung.
- **Backend-Anbindung hinter einer austauschbaren Schicht.** Ein Interface pro Plattform, Implementierung dahinter.
- **Icons nie mit `tint = Color.Unspecified`.** Dann zeichnet Compose die Grundfarbe der Vektorgrafik, also Schwarz. Im Dunkelmodus war das Mikrofon so kaum zu sehen (behoben am 2026-09-24). Richtig ist `LocalContentColor.current`, das greift auch beim Ausgrauen.
- Zielplattform Windows: **Windows 11**. Entwicklungsrechner ist Windows, Shell ist PowerShell — Build-/Setup-Befehle entsprechend angeben.

## Geplante Struktur (Vorschlag, noch nicht angelegt)

```
android/    Android-Client
windows/    Windows-11-Client
docs/       Verträge (API.md, openapi.json), Notizen, Architekturentscheidungen
```
