package de.chefassistent.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import de.chefassistent.app.R
import java.io.File

/**
 * Reicht eine heruntergeladene Datei an die passende App weiter.
 *
 * Der Weg ueber den FileProvider ist Pflicht: ein `file://`-Uri loest ab Android 7
 * eine FileUriExposedException aus.
 */
fun dateiOeffnen(kontext: Context, datei: File) {
    val uri = FileProvider.getUriForFile(kontext, "${kontext.packageName}.dateien", datei)
    val typ = dateityp(datei.extension.lowercase())

    val absicht = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, typ)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        kontext.startActivity(absicht)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(kontext, R.string.datei_keine_app, Toast.LENGTH_LONG).show()
    }
}

/**
 * Bestimmt den Typ fuer den Oeffnen-Aufruf.
 *
 * Android kennt `md` nicht, und auf `application/octet-stream` reagiert auf dem
 * Testgeraet **keine einzige App** - eine Notiz als `.md` waere damit nicht zu
 * oeffnen. Textartige Endungen werden deshalb auf `text/plain` abgebildet;
 * dafuer gibt es Anzeigeprogramme.
 */
private fun dateityp(endung: String): String = when (endung) {
    in TEXTENDUNGEN -> "text/plain"
    else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(endung)
        ?: "application/octet-stream"
}

private val TEXTENDUNGEN = setOf(
    "md", "markdown", "txt", "log", "csv", "ini", "cfg", "conf", "yml", "yaml",
)

/**
 * Gibt eine heruntergeladene Datei ueber das Teilen-Menue weiter, z. B. die
 * UGL eines Angebots an Mail, Drive oder das CRM. Zum Oeffnen gibt es fuer
 * solche Formate auf dem Handy meist keine App, zum Weiterreichen schon.
 */
fun dateiTeilen(kontext: Context, datei: File, titel: String) {
    val uri = FileProvider.getUriForFile(kontext, "${kontext.packageName}.dateien", datei)
    val absicht = Intent(Intent.ACTION_SEND).apply {
        type = "application/octet-stream"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, datei.name)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        kontext.startActivity(Intent.createChooser(absicht, titel))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(kontext, R.string.datei_keine_app, Toast.LENGTH_LONG).show()
    }
}
