# Ideen für die Android-App

## Verabredet für die nächste Sitzung (2026-09-23 abends)

1. **Auftrag für `POST /v1/ablage`** an den Server-Agenten formulieren — inklusive der Frage, ob die KI Bildinhalte lesen soll. Vorschlag steht unten unter Punkt 3.
2. **App-Seite dazu**: Teilen-Ziel und Dokumentenscanner. Geht erst, wenn der Endpunkt steht.
3. **Projektkontext bis zum Tageswechsel behalten** statt beim Neustart zu verwerfen. Empfehlung ausgesprochen, **Entscheidung des Nutzers steht aus**.
4. **Lücke:** Der Projektkontext gilt nicht im Freihandmodus — `FreihandViewModel` kennt ihn nicht. Ausgerechnet dort, wo man nicht hinschaut.

Ergebnis einer Recherche vom 2026-09-23. **Nichts davon ist beschlossen** — die Liste dient als Entscheidungsgrundlage. Stand der App und Vertrag: siehe `CLAUDE.md`.

## Drei Lücken im Vertrag, die die Prioritäten bestimmen

1. **Kein Upload außer `/v1/sprache`.** Vom Handy kann nichts *in* die Firmenablage — kein Foto, kein Lieferschein, kein PDF. Der Ordner `Eingang` wartet praktisch darauf.
2. **Kein Streaming.** `/v1/chat` antwortet nach 5–60 s am Stück. Der Nutzer sieht in dieser Zeit nichts.
3. **Kein Push.** Bestätigungen und Termine müssten gepollt werden.

## Top 3

### 1. Diktat prüfen, bevor es abgeschickt wird — **umgesetzt am 2026-09-23**

Nach der Aufnahme `antworten=false` schicken, den erkannten Text editierbar anzeigen, erst dann senden.

**Der Vertrag bietet das bereits an, und die App nutzt es nicht** (`Body_sprache_v1_sprache_post`). Auf der Baustelle entscheidet die Erkennungsqualität über alles Weitere — ein falsch verstandener Projektname führt zu einer falschen Ablageaktion. Abschaltbar machen, sonst kostet es bei jedem Sprachbefehl einen Extra-Tipp.

### 2. Sprach-Schnellerfassung mit Offline-Ausgangskorb — **umgesetzt am 2026-09-23**

Aufnehmen ohne die App zu öffnen (Widget, Quick-Settings-Kachel, App-Shortcut). Ohne Verbindung wandert die Aufnahme in einen Ausgangskorb und geht per WorkManager raus, sobald wieder Netz da ist.

Der Moment, in dem der Assistent am dringendsten gebraucht wird — Heizungskeller, Tiefgarage, Landbaustelle — ist heute genau der, in dem die App nichts kann. Verschärft dadurch, dass Tailscale auf Android im Hintergrund nicht immer durchhält (Akkusparer, Prozesskiller).

Zu beachten: ab Android 14 `foregroundServiceType="microphone"`; harte Maximaldauer gegen Daueraufnahme in der Hosentasche; unversandte Aufnahmen gehören in `EncryptedFile` und nach dem Senden gelöscht.

### 3. Teilen-Ziel und Dokumentenscan in die Ablage — mittel, **braucht Backend**

App wird System-Teilen-Ziel für Bilder/PDFs/Text, dazu ein Scan-Knopf mit dem ML-Kit-Dokumentenscanner (Kantenerkennung, Zuschnitt, PDF).

Lieferschein vom Großhändler, Typenschild der Altanlage, Abnahmeprotokoll, Rechnung fürs Förder-Nachweisverfahren — landet heute im Fotoalbum und abends nirgends.

Vorschlag für den Endpunkt, rückwärtskompatibel unter `/v1/`:

```
POST /v1/ablage        (multipart/form-data)
  datei        : Binärdatei (Bild/PDF)
  dateiname    : "Lieferschein_Schulz.pdf"   (optional)
  text         : "gehört zu HEZ-2003 Schulz"
  gespraech_id : "…"                          (optional)
  ordner       : "Eingang"                    (optional, Standard "Eingang")
→ Antwort wie ChatAntwort
```

Antwortet er wie `ChatAntwort`, greift die bestehende Bestätigungsmechanik automatisch — die App braucht dafür keine neue Logik.

## Weitere Vorschläge, nach Nutzen pro Aufwand

| # | Was | Aufwand | Wo |
|---|---|---|---|
| 4 | Datei-Chips: Langdruck → Teilen, Pfad kopieren, Vorschaubild | klein | App |
| 5 | Projektkontext anheften | — | **umgesetzt 2026-09-23**, Geraetetest offen |
| 6 | Offene Bestätigungen als Benachrichtigung mit Ja/Nein darin | klein–mittel | App (Polling) |
| 7 | Antwort strömen lassen statt 5–60 s Leere | mittel | **Backend** `POST /v1/chat/stream` (SSE) |
| 8 | Freihand-Gesprächsmodus für Auto und Baustelle | — | **umgesetzt 2026-09-23** |
| 9 | Tagesbriefing als Widget | mittel | App, besser mit **Backend** `GET /v1/briefing` |
| 10 | Fristen und Wiedervorlagen (Förderfristen!) | mittel | **Backend** `GET/POST /v1/merker` |
| 11 | Verbindungszustand ehrlich zeigen („Tailscale aus") | klein | App |
| 12 | Antwortlänge steuern + Vorlagenleiste | klein | App |

Zu **9**: größtes Floprisiko der Liste. Nutzer vertragen etwa drei bis fünf ungefragte Meldungen pro Tag über *alle* Quellen zusammen. Deshalb Widget zuerst, Push höchstens einmal täglich und abschaltbar.

Zu **6**: Polling funktioniert nur, solange Tailscale steht. FCM wäre zuverlässig, aber dann **ausschließlich ein inhaltsleerer Weckruf** — keine Beschreibungen, keine Kundennamen. Sonst verlassen Metadaten den Tailscale-Perimeter, was der ganzen Architektur widerspricht.

## Bewusst nicht empfohlen

- **Android-Auto-App.** Google lässt für Dritte nur Navigation, Audio, Messaging und Spiele zu; ein Assistent passt in keine Kategorie. Dazu schließt Tailscale Android Auto per Split-Tunneling standardmäßig aus. Der Freihandmodus mit Handyhalterung liefert fast denselben Nutzen.
- **Google-Assistant-/Gemini-Integration.** Belege widersprechen sich (Assistant abgeschaltet, App Actions offiziell noch dokumentiert, Entwickler berichten von nicht mehr aufgerufenen Shortcuts). Zu unklar für investierte Arbeit.
- **Weckwort mit Dauerlauschen.** Dauerhafter Mikrofon-Service, Akku, und ein permanent lauschendes Handy in Kundenwohnungen. Ein-Druck-Erfassung deckt denselben Bedarf.
- **Lokales KI-Modell für offline.** Der ganze Wert liegt im Zugriff auf Ablage, Kalender und Websuche — alles serverseitig. Ein Offline-Modell hätte nichts, worauf es zugreifen könnte.
- **Offline-Spiegelung der Firmenablage.** Synchronisationskonflikte gegen Samba, und bei Geräteverlust liegen Kundendaten auf einem verlorenen Telefon.
- **Eigene Oberfläche zum Anlegen von Terminen.** Der Vertrag sagt ausdrücklich: Anlegen und Ändern läuft über den Chat. Eine Formularoberfläche umginge die Bestätigungslogik.
- **Zeiterfassung / Monteur-Funktionen.** Anderes Produkt, andere Zielgruppe, eigene Anforderungen an Rechte und Manipulationssicherheit. Das hier ist die App des Chefs.

## Offene Frage aus der Recherche nicht beantwortet

Zu Nutzerkritik an einzelnen SHK-Apps gibt es **keine belastbaren Rezensionsdaten** — die Vergleichsseiten sind fast durchweg Anbieter-Marketing. Belastbar ist nur die wiederkehrende Aussage mehrerer unabhängiger Quellen, dass fehlender Empfang das strukturelle Kernproblem mobiler Baustellensoftware ist.
