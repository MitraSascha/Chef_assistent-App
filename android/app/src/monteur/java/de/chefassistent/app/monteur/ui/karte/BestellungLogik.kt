package de.chefassistent.app.monteur.ui.karte

import de.chefassistent.app.R
import de.chefassistent.app.monteur.data.Artikel
import de.chefassistent.app.monteur.data.ArtikelWahl
import de.chefassistent.app.monteur.data.KarteProjekt
import de.chefassistent.app.monteur.data.Material
import de.chefassistent.app.monteur.data.MaterialAenderung
import de.chefassistent.app.monteur.ui.bericht.materialEinheit
import de.chefassistent.app.monteur.ui.bericht.materialName
import kotlinx.serialization.Serializable

/*
 * Reine Logik der Karten „Material bestellen“ und „Info ans Buero“, ohne Android -
 * damit sie sich in Unit-Tests pruefen laesst (BestellungTest).
 *
 * Warum lokal: Fuer Bestellungen gibt es keinen PATCH. Alles, was der Monteur
 * aendert, bleibt auf dem Geraet und geht erst mit „Passt“ als vollstaendige
 * Liste raus (API.md 5d). Die App muss also selbst wissen, was sich geaendert hat.
 */

/** Grenze des Vertrags fuer `text` bei „Passt“ einer Meldung. */
const val MELDUNG_MAX_ZEICHEN = 2000

/**
 * Eine Position, wie sie gerade auf dem Bildschirm steht.
 *
 * [index] = Position in `karte.bestellung.material`, `null` = mit „+ Material“
 * neu angehaengt. [artikel] haelt den gewaehlten Vorschlag bzw. Suchtreffer nur
 * fuer die Anzeige - an den Server geht davon nur die Nummer.
 *
 * Serialisierbar, damit der Stand Drehen und das Beenden der App durch Android
 * ueberlebt (rememberSaveable als JSON).
 */
@Serializable
data class BestellZeile(
    val index: Int?,
    val menge: Double,
    /** behalten | frei | waehlen */
    val wahl: String = WAHL_BEHALTEN,
    val artikel: Artikel? = null,
    /** Nur bei neuen freien Positionen: Bezeichnung und Einheit. */
    val text: String? = null,
    val einheit: String? = null,
) {
    val artikelWahl: ArtikelWahl
        get() = when (wahl) {
            WAHL_FREI -> ArtikelWahl.Frei
            WAHL_WAEHLEN -> artikel?.let { ArtikelWahl.Waehlen(it.nr) } ?: ArtikelWahl.Behalten
            else -> ArtikelWahl.Behalten
        }

    companion object {
        const val WAHL_BEHALTEN = "behalten"
        const val WAHL_FREI = "frei"
        const val WAHL_WAEHLEN = "waehlen"
    }
}

/** Was eine Zeile zeigt. [rueckfrage] ist `null`, sobald sie geloest ist. */
data class BestellAnzeige(
    val name: String,
    val einheit: String,
    val menge: Double,
    /** Nummer des gewaehlten Artikels, `null` = freie Position. */
    val artikelNr: String?,
    /** Diktierter Text, wenn er vom Artikelnamen abweicht - zeigt, was gemeint war. */
    val diktat: String?,
    val rueckfrage: String?,
    val vorschlaege: List<Artikel>,
    val wahl: ArtikelWahl,
    val neu: Boolean,
    val geaendert: Boolean,
)

/** Ausgangsstand: jede Position der Karte mit ihrer Menge, nichts gewaehlt. */
fun bestellStart(material: List<Material>): List<BestellZeile> =
    material.mapIndexed { i, m -> BestellZeile(index = i, menge = m.menge) }

/**
 * Was bei „Passt“ als `material` mitgeht: die **vollstaendige** Liste (API.md 5d),
 * entfernte Positionen fehlen einfach. `null`, solange nichts geaendert wurde -
 * dann schickt die App das Feld gar nicht und der Server nimmt seine Liste.
 */
fun bestellAenderungen(material: List<Material>, zeilen: List<BestellZeile>): List<MaterialAenderung>? {
    if (zeilen == bestellStart(material)) return null
    return zeilen.map { z ->
        MaterialAenderung(
            index = z.index,
            menge = z.menge,
            text = if (z.index == null) z.text else null,
            einheit = if (z.index == null) z.einheit else null,
            artikel = z.artikelWahl,
        )
    }
}

/** Anzeige aller Zeilen aus Originalliste und lokalem Stand. */
fun bestellAnzeige(material: List<Material>, zeilen: List<BestellZeile>): List<BestellAnzeige> =
    zeilen.mapNotNull { z -> bestellAnzeige(material, z) }

private fun bestellAnzeige(material: List<Material>, z: BestellZeile): BestellAnzeige? {
    if (z.index == null) {
        val a = z.artikel
        return BestellAnzeige(
            name = a?.name ?: z.text.orEmpty(),
            einheit = a?.einheit?.takeIf { it.isNotBlank() } ?: z.einheit.orEmpty(),
            menge = z.menge,
            artikelNr = a?.nr,
            diktat = null,
            rueckfrage = null,
            vorschlaege = emptyList(),
            wahl = z.artikelWahl,
            neu = true,
            geaendert = true,
        )
    }
    val m = material.getOrNull(z.index) ?: return null
    val wahl = z.artikelWahl
    val (name, einheit, nr) = when (wahl) {
        ArtikelWahl.Behalten -> Triple(materialName(m), materialEinheit(m), m.artikel?.nr)
        // Frei lassen: der Diktat-Text bleibt, der Katalog wird nicht abgeglichen.
        ArtikelWahl.Frei -> Triple(m.text, m.einheit, null)
        is ArtikelWahl.Waehlen -> {
            val a = z.artikel ?: m.vorschlaege.firstOrNull { it.nr == wahl.nr }
            Triple(a?.name ?: m.text, a?.einheit?.takeIf { it.isNotBlank() } ?: m.einheit, wahl.nr)
        }
    }
    return BestellAnzeige(
        name = name,
        einheit = einheit,
        menge = z.menge,
        artikelNr = nr,
        diktat = m.text.takeIf { it.isNotBlank() && it != name },
        // Geloest ist die Rueckfrage, sobald ein Vorschlag gewaehlt oder frei gelassen wurde.
        rueckfrage = m.rueckfrage?.takeIf { wahl == ArtikelWahl.Behalten },
        vorschlaege = m.vorschlaege,
        wahl = wahl,
        neu = false,
        geaendert = wahl != ArtikelWahl.Behalten || z.menge != m.menge,
    )
}

/** Menge der Zeile [pos] setzen. */
fun mengeSetzen(zeilen: List<BestellZeile>, pos: Int, menge: Double): List<BestellZeile> =
    zeilen.mapIndexed { i, z -> if (i == pos) z.copy(menge = menge) else z }

/** Vorschlag waehlen (mit [artikel]) oder frei lassen (`null`). */
fun wahlSetzen(zeilen: List<BestellZeile>, pos: Int, artikel: Artikel?): List<BestellZeile> =
    zeilen.mapIndexed { i, z ->
        when {
            i != pos -> z
            artikel != null -> z.copy(wahl = BestellZeile.WAHL_WAEHLEN, artikel = artikel)
            else -> z.copy(wahl = BestellZeile.WAHL_FREI, artikel = null)
        }
    }

fun zeileEntfernen(zeilen: List<BestellZeile>, pos: Int): List<BestellZeile> =
    zeilen.filterIndexed { i, _ -> i != pos }

/**
 * Neue Position aus dem Dialog „+ Material“ anhaengen. [artikel] ist der
 * Suchtreffer (fuer Name und Einheit in der Anzeige), sonst freie Position.
 */
fun zeileAnhaengen(zeilen: List<BestellZeile>, neu: MaterialAenderung, artikel: Artikel?): List<BestellZeile> =
    zeilen + if (artikel != null) {
        BestellZeile(index = null, menge = neu.menge, wahl = BestellZeile.WAHL_WAEHLEN, artikel = artikel)
    } else {
        BestellZeile(index = null, menge = neu.menge, wahl = BestellZeile.WAHL_FREI, text = neu.text, einheit = neu.einheit)
    }

/**
 * Meldungstext fuer „Passt“: nur, wenn er sich vom Vorschlag der KI unterscheidet.
 * Ein leeres Feld zaehlt nicht als Aenderung - einen leeren Kommentar soll der
 * Server nicht in den Projektverlauf schreiben.
 */
fun meldungText(original: String, neu: String): String? =
    neu.take(MELDUNG_MAX_ZEICHEN).takeIf { it != original && it.isNotBlank() }

/** Anzeigename der Meldungsart; `null` = unbekannt, dann zeigt die App den Wert roh. */
fun meldungArtText(art: String): Int? = when (art) {
    "nicht_angetroffen" -> R.string.bs_art_nicht_angetroffen
    "verspaetung" -> R.string.bs_art_verspaetung
    "mangel" -> R.string.bs_art_mangel
    "nachtrag" -> R.string.bs_art_nachtrag
    "sonstiges" -> R.string.bs_art_sonstiges
    else -> null
}

/** „SAN-660 · Schulz“, leer ohne Projekt. */
fun projektText(projekt: KarteProjekt?): String =
    listOfNotNull(projekt?.nr, projekt?.kunde).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" · ")
