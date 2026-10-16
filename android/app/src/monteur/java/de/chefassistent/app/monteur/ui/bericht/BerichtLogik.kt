package de.chefassistent.app.monteur.ui.bericht

import de.chefassistent.app.monteur.data.ArtikelWahl
import de.chefassistent.app.monteur.data.Material
import de.chefassistent.app.monteur.data.MaterialAenderung
import de.chefassistent.app.monteur.data.ZeitAbschnitt
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.ceil
import kotlin.math.floor

/*
 * Reine Logik des Bericht-Bildschirms, ohne Android - damit sie sich in
 * Unit-Tests pruefen laesst (BerichtTest).
 */

/** Stunden-Stepper: Viertelstunden (Auftrag 1.22, 3.). */
const val STUNDEN_SCHRITT = 0.25

/** Mehr als ein Tag ist sicher ein Vertipper. */
const val STUNDEN_MAX = 24.0

/** Groesste Unterschrift, die an den Server geht (Auftrag 1.22, 3.). */
const val UNTERSCHRIFT_MAX_BYTES = 2L * 1024 * 1024

/**
 * Naechster Stunden-Wert fuer +/− ([richtung] > 0 = mehr). Krumme Werte der KI
 * (z. B. 1,3 h) springen auf die naechste Viertelstunde in Druckrichtung, statt
 * um 0,25 weiterzulaufen und krumm zu bleiben. Bleibt in 0 … [STUNDEN_MAX].
 */
fun stundenSchritt(wert: Double, richtung: Int): Double {
    val viertel = wert / STUNDEN_SCHRITT
    val neu = if (richtung > 0) (floor(viertel + EPS) + 1) * STUNDEN_SCHRITT
    else (ceil(viertel - EPS) - 1) * STUNDEN_SCHRITT
    return neu.coerceIn(0.0, STUNDEN_MAX)
}

/**
 * Naechste Materialmenge fuer +/−: ganze Stueck. Krumme Mengen (2,5 m) springen
 * auf die naechste ganze Zahl. Unter 1 geht es nicht - weg kommt eine Position
 * nur ueber „Entfernen“, damit ein Vertipper sie nicht verschwinden laesst.
 */
fun mengeSchritt(menge: Double, richtung: Int): Double =
    if (richtung > 0) floor(menge + EPS) + 1
    else {
        val kleiner = ceil(menge - EPS) - 1
        if (kleiner < 1) menge else kleiner
    }

/** Zahl auf Deutsch ohne ueberfluessige Nullen: 2.0 -> „2“, 2.5 -> „2,5“, 0.75 -> „0,75“. */
fun zahlText(wert: Double): String {
    val gerundet = BigDecimal.valueOf(wert).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros()
    val text = if (gerundet.scale() < 0) gerundet.setScale(0).toPlainString() else gerundet.toPlainString()
    return text.replace('.', ',')
}

/**
 * Vollstaendige Materialliste fuer `PATCH` (API.md 5c: `material` ersetzt die ganze
 * Liste). Jede Position behaelt ueber `index` ihre Vorschlaege und ihre Wahl,
 * nur [aenderungIndex] bekommt [wahl]; [entfernen] laesst sie weg.
 *
 * [mengen] sind die Mengen, wie sie gerade auf dem Bildschirm stehen (der
 * Stepper ist entprellt, der Server kennt sie womoeglich noch nicht).
 */
fun materialAenderungen(
    material: List<Material>,
    mengen: Map<Int, Double> = emptyMap(),
    aenderungIndex: Int? = null,
    wahl: ArtikelWahl = ArtikelWahl.Behalten,
    entfernen: Boolean = false,
): List<MaterialAenderung> = material.mapIndexedNotNull { i, m ->
    if (entfernen && i == aenderungIndex) return@mapIndexedNotNull null
    MaterialAenderung(
        index = i,
        menge = mengen[i] ?: m.menge,
        artikel = if (i == aenderungIndex) wahl else ArtikelWahl.Behalten,
    )
}

/** Anzeigename einer Position: der gewaehlte Katalogartikel, sonst der diktierte Text. */
fun materialName(m: Material): String = m.artikel?.name?.takeIf { it.isNotBlank() } ?: m.text

/** Einheit: die des Artikels, sonst die diktierte. */
fun materialEinheit(m: Material): String = m.artikel?.einheit?.takeIf { it.isNotBlank() } ?: m.einheit

/** Zeile der Tageszeiten: „07:30–10:15 · Arbeit“. [artName] uebersetzt den Vertragswert. */
fun zeitZeile(zeit: ZeitAbschnitt, artName: (String) -> String): String =
    "${zeit.beginn}–${zeit.ende} · ${artName(zeit.art)}"

/** `2026-10-16` -> `16.10.2026`; alles andere bleibt, wie es kam. */
fun datumText(iso: String): String {
    val teile = iso.take(10).split('-')
    return if (teile.size == 3 && teile[0].length == 4) "${teile[2]}.${teile[1]}.${teile[0]}" else iso
}

/**
 * Kunde aus dem Anzeigetitel (`„SAN-1243 · Böse“`) - Vorbelegung fuer den Namen
 * unter der Unterschrift. Ohne Trenner leer: lieber selbst tippen lassen als
 * eine Projektnummer als Namen vorzuschlagen.
 */
fun kundeAusTitel(titel: String?): String =
    titel?.substringAfter(" · ", missingDelimiterValue = "")?.trim().orEmpty()

/**
 * Skalierung fuer den naechsten Versuch, wenn das PNG [bytes] gross ist. `null`
 * = passt. Verkleinert anteilig zur Ueberschreitung (Flaeche ~ Bytes), mit
 * etwas Luft, damit es meist beim ersten Nachrechnen passt.
 */
fun unterschriftSkalierung(bytes: Long, grenze: Long = UNTERSCHRIFT_MAX_BYTES): Float? {
    if (bytes <= grenze) return null
    val faktor = kotlin.math.sqrt(grenze.toDouble() / bytes) * 0.9
    return faktor.toFloat().coerceIn(0.1f, 0.9f)
}

private const val EPS = 1e-6
