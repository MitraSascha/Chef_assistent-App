# Chef-Assistent – Windows-Hülle (Auftrag für die Windows-App)

Die Windows-App ist **nur eine Hülle**: ein Fenster mit WebView2, in dem die Weboberfläche vom Server läuft.
Alles, was man sieht und bedient (Chat, Sprechen, Fotos/Videos, Ordneransicht), kommt vom Server und wird dort
aktualisiert. Die Hülle macht nur, was ein Browser nicht darf: **Dateien und Ordner im Netzlaufwerk öffnen.**

Ziel der Geschäftsführung: *Klick auf eine Datei im Chat → Explorer öffnet sich im Netzlaufwerk (M:), Datei markiert.*
Ohne Nachfrage, ohne Einrichtung.

## Technik

- **.NET 8, WinForms oder WPF + `Microsoft.Web.WebView2`** (WebView2-Laufzeit ist in Windows 11 enthalten).
  Alternativ Tauri – Hauptsache WebView2 und `postMessage`.
- Ausgabe: eine `.exe` (self-contained, single-file) oder ein kleines Setup. Ablage für die Verteilung:
  `M:\Intern\Software\Chef-Assistent\`.
- Die Nutzer surfen sonst mit Chrome/Brave – egal, WebView2 ist davon unabhängig.

## 1. Fenster und Laden

| | |
|---|---|
| Start-URL | `https://mitraserver-aspire-xc-605.tail8bd028.ts.net/` (einstellbar in `%APPDATA%\ChefAssistent\einstellungen.json`) |
| WebView2-Datenordner | `%LOCALAPPDATA%\ChefAssistent\WebView2` – dort bleibt die Anmeldung gespeichert |
| Fenster | Titel „Chef-Assistent“, Symbol wie `/web/icon.svg`, Größe/Position merken, **nur eine Instanz** (zweiter Start holt das Fenster nach vorn) |
| Offline | Server nicht erreichbar → eigene Seite „Keine Verbindung – Tailscale bzw. Büronetz prüfen“ mit „Nochmal versuchen“ |
| Entwicklertools | in der Release-Version aus (`AreDevToolsEnabled = false`) |

Anmeldung macht die Weboberfläche selbst (Zugangsschlüssel `ca_…`, gespeichert im localStorage der WebView).
**Die Hülle braucht und kennt keinen Schlüssel.**

## 2. Navigation und Berechtigungen

- `NavigationStarting`: nur Adressen **auf dem Server-Origin** in der WebView erlauben. Alles andere mit `http(s)://`
  → abbrechen und im **Standardbrowser** öffnen (`Process.Start(new ProcessStartInfo(url) { UseShellExecute = true })`,
  vorher prüfen: Schema genau `http` oder `https`). Andere Schemata (`file:`, `javascript:` usw.) → abbrechen.
- `NewWindowRequested`: `blob:`-Adressen vom Server-Origin (PDF-/Bildvorschau) in einem zweiten Hüllen-Fenster oder im
  Standardbrowser öffnen; `http(s)` wie oben; alles andere ablehnen.
- Downloads (`DownloadStarting`): Standardverhalten von WebView2 ist ok.
- `PermissionRequested`: **Mikrofon erlauben, aber nur für den Server-Origin** (zum Sprechen). Alles andere
  (Kamera, Standort, Benachrichtigungen …) → Standard/ablehnen.
- **Kein** `AddHostObjectToScript` – die einzige Verbindung Web ↔ Windows sind die Nachrichten unten.

## 3. Nachrichten Web → Hülle (`WebMessageReceived`)

Nur annehmen, wenn `e.Source` mit dem Server-Origin beginnt. Inhalt ist JSON (`e.WebMessageAsJson`):

| `typ` | Felder | Wann | Was die Hülle tut |
|---|---|---|---|
| `hallo` | `wurzeln: string[]` | nach jeder Anmeldung | Netzwerkpfade der Ablage merken, z.B. `["\\\\192.168.178.155\\Firma", "\\\\100.106.160.80\\Firma"]` |
| `zeigen` | `pfad: string` | Klick auf Datei/Ordner | **Explorer öffnen**, Datei markiert |
| `oeffnen` | `pfad: string` | Knopf „Öffnen“ bzw. Klick in der Ordneransicht | Datei im zugehörigen Programm öffnen (Word, Excel, PDF …) – nur Erlaubnisliste |

`pfad` ist immer **relativ zur Ablage** mit `/`, z.B. `Notizen/Sascha/2026-09-24 Objektbegehung Hotel.md`.

### 3.1 Pfad prüfen – vor allem anderen

Ablehnen (→ Antwort `ok: false`), wenn `pfad`:
- leer, länger als 1000 Zeichen, oder Steuerzeichen enthält,
- mit `/` oder `\` beginnt, `\`, `:` oder `..` enthält, oder ein leeres Segment hat (`a//b`),
- mit einem gesperrten Ordner beginnt: `Geschaeftsfuehrung`, `.papierkorb`, `.recycle` (Groß/klein egal).

`wurzeln` aus `hallo` nur übernehmen, wenn jeder Eintrag die Form `\\<rechner>\<freigabe>` hat
(Regex `^\\\\[A-Za-z0-9.\-]+\\[A-Za-z0-9 _.\-]+$`). Vorgabe, solange kein `hallo` kam:
`\\192.168.178.155\Firma`, dann `\\100.106.160.80\Firma`.

### 3.2 Wurzel wählen – Laufwerksbuchstabe bevorzugt

Die Büro-PCs haben die Ablage als **M:** verbunden (`\\192.168.178.155\Firma`). Im Explorer soll man dort landen,
nicht bei einer IP-Adresse:

1. Alle Netzlaufwerke durchgehen (`DriveInfo.GetDrives()` mit `DriveType.Network`) und je Buchstabe den
   Netzwerkpfad holen (`WNetGetConnection` aus `mpr.dll`). Passt einer (Groß/klein egal, ohne abschließendes `\`)
   zu einer Wurzel → diese Wurzel ist `M:\` (bzw. der gefundene Buchstabe).
2. Sonst die Wurzeln der Reihe nach prüfen: `Directory.Exists(wurzel)` **mit Zeitlimit 3 s** (in einem Task, sonst
   hängt die Oberfläche, wenn eine Adresse nicht erreichbar ist). Erste erreichbare nehmen.
3. Ergebnis für 5 Minuten merken; bei Fehlschlag sofort neu ermitteln.

Dann: `voll = Path.GetFullPath(Path.Combine(wurzel, pfad.Replace('/', '\\')))` und **prüfen, dass `voll` mit
`wurzel + "\"` beginnt** (Groß/klein egal). Sonst ablehnen.

### 3.3 Öffnen

| Aktion | Ziel ist Datei | Ziel ist Ordner | Existiert nicht |
|---|---|---|---|
| `zeigen` | `explorer.exe /select,"<voll>"` | `explorer.exe "<voll>"` | übergeordneten Ordner zeigen, falls vorhanden; sonst Fehler |
| `oeffnen` | Endung auf Erlaubnisliste → `Process.Start(new ProcessStartInfo(voll) { UseShellExecute = true })`; sonst wie `zeigen` + Hinweis | `explorer.exe "<voll>"` | Fehler |

**Erlaubnisliste für `oeffnen`:** `.pdf .doc .docx .xls .xlsx .xlsm .csv .txt .md .jpg .jpeg .png .webp .heic .mp4 .mov .dwg .dxf .odt .ods`.
Alles andere (insbesondere `.exe .bat .cmd .ps1 .vbs .js .lnk .url .msi .scr .hta`) **nie ausführen**, nur im Explorer
markieren. Grund: Pfade kommen letztlich von der KI – die Hülle darf daraus nie ein Programm starten.

`explorer.exe` immer mit Argumentliste starten (`ProcessStartInfo.ArgumentList`), nicht per zusammengesetzter
Kommandozeile.

Ist die Freigabe noch nicht angemeldet, fragt Windows beim ersten Zugriff selbst nach dem Samba-Passwort – das ist ok.

### 3.4 Antwort Hülle → Web

Nach jeder `zeigen`/`oeffnen`-Nachricht: `PostWebMessageAsJson({"typ":"ergebnis","ok":true|false,"meldung":"…"})`.
Die Weboberfläche zeigt `meldung` bei `ok: false` als Fehlerhinweis. Meldungen auf Deutsch, z.B.:
„Netzlaufwerk nicht erreichbar – Büronetz oder Tailscale prüfen.“ · „Datei nicht gefunden (verschoben oder gelöscht?).“ ·
„Diese Datei wird aus Sicherheitsgründen nicht direkt geöffnet – sie ist im Explorer markiert.“

## 4. Nachrichten Hülle → Web (optional, aber gewünscht)

| `typ` | Wann | Wirkung in der Weboberfläche |
|---|---|---|
| `sprechen` | globale Tastenkombination **Strg+Alt+Leertaste** (einstellbar), auch wenn das Fenster hinten ist | Fenster nach vorn holen, dann Nachricht senden: startet bzw. beendet die Sprachaufnahme |
| `ergebnis` | siehe 3.4 | Fehlerhinweis |

Symbol im Infobereich (neben der Uhr) mit „Öffnen“ / „Beenden“; Schließen des Fensters minimiert dorthin (einstellbar).

## 5. Updates

Die Oberfläche aktualisiert sich über den Server von selbst. Die Hülle selbst ändert sich selten: Beim Start
`M:\Intern\Software\Chef-Assistent\version.txt` lesen; ist die Nummer höher als die eigene, Hinweis
„Neue Version – jetzt aktualisieren?“ und das Setup daneben starten. (Kein Zugriff → ignorieren.)

## 6. Abnahme (bitte auf einem Büro-PC mit M: testen)

1. Start → Anmeldung mit `ca_…` → Neustart der App → noch angemeldet.
2. Chat: „Zeig mir die Notizen von Sascha“ → Klick auf eine Datei → **Explorer auf M:, Datei markiert**.
3. Knopf „Öffnen“ an einer `.pdf`/`.docx` → öffnet im Programm, **direkt vom Server** (Speichern landet auf M:).
4. Ordneransicht (📁 Ablage) → Ordner blättern, Datei anklicken → öffnet; 📂 → Explorer markiert.
5. Ohne M: (Laufwerk trennen) → Klick öffnet `\\192.168.178.155\Firma\…`.
6. Büronetz aus, nur Tailscale → Klick öffnet `\\100.106.160.80\Firma\…`.
7. Link auf eine Webseite in einer Antwort (Quellen) → öffnet in Chrome/Brave, **nicht** in der Hülle.
8. Mikrofon: 🎤 → Browser-Nachfrage kommt **nicht**, Aufnahme läuft; Strg+Alt+Leertaste aus einem anderen Programm startet/stoppt.
9. Sicherheit (Entwicklerwerkzeuge in einer Test-Version): `postMessage` mit `{"typ":"oeffnen","pfad":"../Windows/notepad.exe"}`,
   `{"typ":"oeffnen","pfad":"Eingang/test.bat"}`, `{"typ":"zeigen","pfad":"Geschaeftsfuehrung"}` → alle abgelehnt bzw. nur markiert.
10. Foto/Video per Ziehen & Ablegen ins Fenster → „Foto gespeichert“ mit Datei-Knöpfen.
