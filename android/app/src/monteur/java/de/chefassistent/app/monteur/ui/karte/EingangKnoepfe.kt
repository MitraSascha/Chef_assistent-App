package de.chefassistent.app.monteur.ui.karte

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import de.chefassistent.app.R
import de.chefassistent.app.monteur.ui.AufnahmeSteuerung
import de.chefassistent.app.monteur.ui.MikrofonKnopf
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Der eine Eingang (Auftrag 1.23, 1.): grosses Mikrofon, daneben kleiner Kamera-Knopf.
 * Auf Heute, Zeiten, Urlaub, in der Projektmappe und auf der Karte derselbe. Darunter
 * klein der Hinweis, dass man einfach sagen kann, was ist.
 */
@Composable
fun EingangKnoepfe(
    aufnahme: AufnahmeSteuerung,
    beiAufnahme: (File) -> Unit,
    beiFotos: (List<File>) -> Unit,
    modifier: Modifier = Modifier,
    aktiv: Boolean = true,
    groesse: androidx.compose.ui.unit.Dp = 76.dp,
    beschriftung: String = stringResource(R.string.k_mikro),
    hinweis: String? = stringResource(R.string.k_mikro_hinweis),
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // Links die Galerie, rechts die Kamera - das Mikrofon bleibt mittig.
            GalerieKnopf(aktiv = aktiv && !aufnahme.nimmtAuf, beiFotos = beiFotos)
            MikrofonKnopf(
                steuerung = aufnahme,
                beiFertig = beiAufnahme,
                aktiv = aktiv,
                groesse = groesse,
                beschriftung = beschriftung,
                hinweis = null,
            )
            KameraKnopf(aktiv = aktiv && !aufnahme.nimmtAuf, beiFoto = { beiFotos(listOf(it)) })
        }
        if (hinweis != null && !aufnahme.nimmtAuf) {
            Text(
                hinweis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
    }
}

/**
 * Fotos aus der Galerie (Android-Fotoauswahl, ohne Speicher-Berechtigung), mehrere auf
 * einmal. Sie werden in den Cache kopiert und danach wie eigene Aufnahmen behandelt.
 */
@Composable
fun GalerieKnopf(aktiv: Boolean, beiFotos: (List<File>) -> Unit, modifier: Modifier = Modifier) {
    val kontext = LocalContext.current
    val auswahl = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(GALERIE_MAX)
    ) { uris ->
        val dateien = uris.mapIndexedNotNull { i, uri -> galerieKopieren(kontext, uri, i) }
        if (dateien.isNotEmpty()) beiFotos(dateien)
    }
    FilledTonalIconButton(
        onClick = {
            try {
                auswahl.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(kontext, kontext.getString(R.string.m_keine_app), Toast.LENGTH_LONG).show()
            }
        },
        enabled = aktiv,
        shape = CircleShape,
        modifier = modifier.size(52.dp),
    ) {
        Icon(Icons.Outlined.PhotoLibrary, contentDescription = stringResource(R.string.k_galerie))
    }
}

/** So viele kann man in der Fotoauswahl auf einmal antippen; was der Eingang nicht nimmt, meldet das ViewModel. */
const val GALERIE_MAX = 20

/** Kopiert ein Bild aus der Fotoauswahl in den Cache (Name aus der Quelle, sonst Zeitstempel). */
fun galerieKopieren(kontext: android.content.Context, uri: android.net.Uri, nr: Int): File? = try {
    val name = kontext.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        ?: "Galerie_${SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.GERMANY).format(Date())}_$nr.jpg"
    val ordner = File(kontext.cacheDir, "fotos").apply { mkdirs() }
    val ziel = File(ordner, "${System.currentTimeMillis()}_${nr}_$name")
    kontext.contentResolver.openInputStream(uri)?.use { ein -> ziel.outputStream().use { ein.copyTo(it) } }
    ziel.takeIf { it.length() > 0 }
} catch (_: Exception) {
    null
}

/** Foto mit der Kamera-App; das Ergebnis liegt im Cache und geht unveraendert an den Server. */
@Composable
fun KameraKnopf(aktiv: Boolean, beiFoto: (File) -> Unit, modifier: Modifier = Modifier) {
    val kontext = LocalContext.current
    // Ueberlebt, dass Android die App waehrend der Kamera beendet.
    var pfad by rememberSaveable { mutableStateOf<String?>(null) }
    val keineKamera = stringResource(R.string.m_keine_app)
    val kamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val datei = pfad?.let(::File)
        pfad = null
        if (datei != null) {
            if (ok && datei.length() > 0) beiFoto(datei) else datei.delete()
        }
    }
    FilledTonalIconButton(
        onClick = {
            val ordner = File(kontext.cacheDir, "fotos").apply { mkdirs() }
            val stempel = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.GERMANY).format(Date())
            val datei = File(ordner, "Foto_$stempel.jpg")
            pfad = datei.path
            try {
                kamera.launch(FileProvider.getUriForFile(kontext, "${kontext.packageName}.dateien", datei))
            } catch (_: ActivityNotFoundException) {
                pfad = null
                Toast.makeText(kontext, keineKamera, Toast.LENGTH_LONG).show()
            }
        },
        enabled = aktiv,
        shape = CircleShape,
        modifier = modifier.size(52.dp),
    ) {
        Icon(Icons.Outlined.PhotoCamera, contentDescription = stringResource(R.string.k_foto))
    }
}
