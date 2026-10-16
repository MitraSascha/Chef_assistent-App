# Auftrag an den App-Agenten – Server 1.24.0: Monteur-App, Benachrichtigungen

Anlass (User 16.10.2026): Monteure sollen sofort erfahren, wenn sich ihr Plan ändert – neuer Termin, **Notdienst**,
Termin verschoben, geändert oder abgesagt. **Keine neuen Knöpfe:** keine Glocke, kein Menüpunkt „Nachrichten“. Die
Benachrichtigungen kommen einfach aufs Handy; ungelesene stehen oben auf „Heute“.

Vertrag: [`openapi.json`](openapi.json) 1.24.0 (sha256 `c47738a5…bfd0fe4`), Beschreibung in [`API.md` 5e](API.md).
Es gibt nur Ergänzungen; alles aus [`AUFTRAG-APP-1.23.md`](AUFTRAG-APP-1.23.md) und
[`AUFTRAG-APP-1.22.md`](AUFTRAG-APP-1.22.md) gilt weiter. Betrifft nur den Flavor `monteur`; die Büro-App ist nicht
betroffen.

## Server (fertig)
- Alle **3 Minuten, auch nachts**, liest der Server die Termine von **heute + 3 Tagen** jedes Monteurs **direkt aus
  HERO** (nicht aus dem Spiegel – der holt nachts erst ab 04:00) und vergleicht mit dem letzten Stand. Daraus werden
  Nachrichten mit `art`: `neu`, `notdienst` (Kategorie oder Titel „Notdienst“), `verschoben`, `geaendert`,
  `entfallen` (abgesagt, aus dem Fenster verlegt oder an jemand anderen gegeben). Der erste Abgleich je Monteur merkt
  sich nur den Stand – kein Nachrichtenschwall.
- **`GET /v1/monteur/nachrichten?seit=<id>`** → `{nachrichten:[{id, art, titel, text, termin_id, datum, erstellt,
  gelesen}], ungelesen, push_aktiv}` – neueste zuerst, max. 14 Tage / 100.
- **`POST /v1/monteur/nachrichten/gelesen`** `{"bis": <id>}` → alle eigenen bis zu dieser id gelesen.
- **`POST /v1/monteur/push`** / **`DELETE /v1/monteur/push`** `{"token", "geraet"}` → FCM-Token hinterlegen / entfernen.
- **Push** über Firebase Cloud Messaging HTTP v1 – nur, wenn auf dem Server das Firebase-Dienstkonto liegt
  (`push_aktiv: true`). Payload: `notification` (title, body) + `data` `{art, termin_id, nachricht_id}` (alles
  Strings), `android.notification.channel_id` = `notdienst` bzw. `termine`, Priorität hoch.
- **Ruhezeit 21–6 Uhr:** nachts pusht der Server nur `notdienst` und Termine, die in weniger als 3 Stunden beginnen.
  Der Rest kommt um 6 Uhr – steht aber sofort in der Liste.

## Was der Chef dafür einmal tun muss
1. <https://console.firebase.google.com> → **Projekt hinzufügen** → Name **„mitra-monteur“**, Google Analytics aus.
2. Im Projekt **Android-App hinzufügen** → Paketname = Paket-ID des Flavors `monteur` (die genaue ID nennt der
   App-Agent, z. B. `de.mitra.monteur`), Spitzname „Mitra Monteur“. SHA-1 ist nicht nötig.
3. **`google-services.json` herunterladen** und dem App-Agenten geben (kommt ins Android-Projekt, siehe 1).
4. Projekteinstellungen (Zahnrad) → **Dienstkonten** → **„Neuen privaten Schlüssel generieren“** → JSON-Datei.
   Die Datei ist ein Schlüssel – nicht per Mail verschicken, nur auf den Server kopieren (z. B. per `scp`) und dort:
   `sudo ~/server-setup/15-push-einrichten.sh <datei.json>`. Danach liefert `GET /v1/monteur/nachrichten`
   `push_aktiv: true`. Bis dahin funktioniert alles ohne Push (siehe 6).

## Bitte bauen

**Wichtig vorweg: keine neuen Knöpfe, Menüs oder Reiter.** Keine Glocke in der Kopfleiste, keine eigene
Nachrichtenseite. Nachrichten erscheinen als Android-Benachrichtigung und als Kartenleiste oben auf „Heute“.

### 1. Firebase im Android-Projekt
- `google-services.json` **nur** in den Flavor `monteur` legen (`app/src/monteur/google-services.json`); die Büro-App
  bekommt kein Firebase. Gradle-Plugin `com.google.gms.google-services` so einbinden, dass der Büro-Build ohne Datei
  baut (Plugin nur für Monteur-Tasks anwenden bzw. Task für `buero` abschalten).
- Abhängigkeit `firebase-messaging` (BoM) nur für den Monteur-Flavor (`monteurImplementation`).
- Solange die Datei fehlt (Chef hat Firebase noch nicht eingerichtet), muss der Monteur-Build trotzdem bauen und
  laufen – dann eben ohne Token, nur mit Abholen (6).

### 2. Token anmelden und abmelden
- Nach jedem App-Start (angemeldet) `FirebaseMessaging.getInstance().token` holen → **`POST /v1/monteur/push`**
  `{"token": "<token>", "geraet": "<Build.MODEL>"}`. Über den Ausgangskorb bzw. mit Wiederholung; Fehler still loggen.
- `FirebaseMessagingService.onNewToken` → dasselbe sofort.
- **Abmelden:** zuerst **`DELETE /v1/monteur/push`** mit demselben Token, dann `POST /v1/abmelden`; danach
  `FirebaseMessaging.getInstance().deleteToken()`.
- `401` → wie bisher („Bitte im Büro neuen QR-Code holen“). `503` (Server ohne Benachrichtigungen) → still ignorieren.

### 3. Zwei Benachrichtigungskanäle
Beim ersten Start anlegen (Android 8+):
- **`termine`** – Name „Termine“, Beschreibung „Neue, verschobene und abgesagte Termine“, Wichtigkeit DEFAULT.
- **`notdienst`** – Name „Notdienst“, Beschreibung „Notdienst-Einsätze – mit Ton, auch nachts“, Wichtigkeit HIGH,
  Ton + Vibration, Sperrbildschirm sichtbar. „Nicht stören“ übergehen darf nur der Monteur selbst in den
  Kanal-Einstellungen – die App setzt `setBypassDnd` nicht ungefragt.
- Kanal-IDs genau so (der Server schickt sie als `channel_id`).

### 4. Erlaubnis (Android 13+)
- `POST_NOTIFICATIONS` **einmal beim ersten Start** nach der Einrichtung abfragen, mit einem Satz davor:
  „Damit du neue Termine und Notdienste sofort bekommst.“ Abgelehnt → nicht erneut fragen; auf „Heute“ steht die
  Leiste trotzdem.

### 5. Empfangen und Antippen
- App im Hintergrund/geschlossen: Android zeigt die Benachrichtigung selbst (aus `notification`), die `data`-Werte
  kommen als Extras im Start-Intent.
- App im Vordergrund (`onMessageReceived`): selbst eine Benachrichtigung auf dem Kanal aus `data.art` zeigen
  (`notdienst` → `notdienst`, sonst `termine`) **und** die Leiste auf „Heute“ sofort aktualisieren.
- Benachrichtigungs-ID = `nachricht_id`, damit nichts doppelt erscheint (Push und eigenes Abholen).
- **Antippen** → App öffnet „Heute“ **an dem Tag des Termins** (aus `datum` der Nachricht bzw. per
  `GET /v1/monteur/tage`) und öffnet das Termindetail von `termin_id`. Bei `entfallen` gibt es den Termin nicht mehr →
  nur den Tag zeigen. Danach `POST /v1/monteur/nachrichten/gelesen {"bis": nachricht_id}`.

### 6. Abholen (immer, und ohne Firebase der einzige Weg)
- Beim App-Start und beim Zurückholen in den Vordergrund: `GET /v1/monteur/nachrichten?seit=<größte bekannte id>`
  (erster Aufruf `seit=0`). `ungelesen` und `push_aktiv` merken.
- **`push_aktiv: false`** (oder kein Firebase in der App): periodischer WorkManager-Job **alle 15 Minuten** (Netz
  nötig) holt `…?seit=<id>` und zeigt jede neue Nachricht als **lokale** Benachrichtigung auf dem passenden Kanal
  (gleiche ID = `nachricht_id`). Ruhezeit 21–6 Uhr selbst beachten: dann nur `notdienst` oder Termine mit Beginn in
  < 3 Stunden zeigen, den Rest ab 6 Uhr. `push_aktiv: true` → Job abbestellen (Push reicht).
- Beim allerersten Abruf nach Installation/Einrichtung **keine** lokalen Benachrichtigungen für alte Nachrichten
  zeigen – nur in die Leiste.

### 7. Leiste oben auf „Heute“ und Punkt am Reiter
- Ungelesene Nachrichten (`gelesen: false`) als **Kartenleiste ganz oben auf „Heute“**, über der Leiste „Noch zu
  bestätigen“ aus 1.23. Je Karte: Symbol nach `art` (Notdienst rot/auffällig, neu, verschoben, geändert, entfällt
  durchgestrichen/grau), `titel` fett, `text` eine Zeile. Mehr als 3 → die ersten 3 und „+ N weitere“ (aufklappen).
- **Karte antippen** = wie Benachrichtigung antippen (5), danach gelesen. **Wegwischen** = nur gelesen.
- **Punkt mit Zahl** (`ungelesen`) am Reiter „Heute“, solange > 0. Kein App-Icon-Badge nötig (Android zeigt den
  Punkt am Icon von selbst).
- Gelesen-Markieren über den Ausgangskorb (wiederholbar, `bis` = höchste gesehene id). Lokal sofort ausblenden.
- Bei einer Nachricht `verschoben`/`geaendert`/`entfallen`/`neu` für einen angezeigten Tag die Tagesliste neu laden
  (`GET /v1/monteur/tage`).

### Testen
Nur mit einem **Monteur-Zugang** (Rolle `monteur`, HERO-Zuordnung) – Büro-Zugänge bekommen keine Nachrichten,
der Server prüft nur Monteure. Nach der Einrichtung höchstens 3 Minuten warten (erster Abgleich merkt nur den Stand),
dann in HERO für diese Person einen Termin für morgen anlegen → nach höchstens 3 Minuten „Neuer Termin morgen …“.
Termin verschieben → „Termin verschoben …“, löschen → „Termin entfällt …“, Termin mit Titel „Notdienst Test“ für
heute → Kanal `notdienst`. Ohne Firebase auf dem Server: `push_aktiv: false`, App holt selbst ab. Testtermine in
HERO danach wieder löschen.
