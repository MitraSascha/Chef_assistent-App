package de.chefassistent.app.monteur.ui.karte

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
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
            // Platzhalter links, damit das Mikrofon mittig bleibt. Galerie = langer Druck auf die Kamera (1.28).
            Box(Modifier.size(52.dp))
            MikrofonKnopf(
                steuerung = aufnahme,
                beiFertig = beiAufnahme,
                aktiv = aktiv,
                groesse = groesse,
                beschriftung = beschriftung,
                hinweis = null,
            )
            KameraKnopf(aktiv = aktiv && !aufnahme.nimmtAuf, beiFoto = { beiFotos(listOf(it)) }, beiGalerie = beiFotos)
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

/** So viele kann man in der Fotoauswahl auf einmal antippen (seit 1.29.0 bis 50). */
const val GALERIE_MAX = 50

/** Kopiert ein Bild aus der Fotoauswahl in den Cache (Name aus der Quelle, sonst Zeitstempel). */
fun galerieKopieren(kontext: android.content.Context, uri: android.net.Uri, nr: Int): File? = try {
    val name = kontext.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        ?: "Galerie_${SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.GERMANY).format(Date())}_$nr.jpg"
    // Eigener Unterordner je Bild: der Originalname bleibt (sichtbar beim Hochladen), Doppelte stoeren nicht.
    val ordner = File(kontext.cacheDir, "fotos/${System.currentTimeMillis()}_$nr").apply { mkdirs() }
    val ziel = File(ordner, name)
    kontext.contentResolver.openInputStream(uri)?.use { ein -> ziel.outputStream().use { ein.copyTo(it) } }
    ziel.takeIf { it.length() > 0 }
} catch (_: Exception) {
    null
}

/**
 * Foto mit der Kamera-App; das Ergebnis liegt im Cache und geht unveraendert an den Server.
 * **Langer Druck** oeffnet die Android-Fotoauswahl (Auftrag 1.28, 2.) - kein eigener Knopf.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KameraKnopf(
    aktiv: Boolean,
    beiFoto: (File) -> Unit,
    modifier: Modifier = Modifier,
    beiGalerie: ((List<File>) -> Unit)? = null,
) {
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
    val galerie = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(GALERIE_MAX)) { uris ->
        val dateien = uris.mapIndexedNotNull { i, uri -> galerieKopieren(kontext, uri, i) }
        if (dateien.isNotEmpty()) beiGalerie?.invoke(dateien)
    }
    val ausloesen: () -> Unit = {
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
    }
    // Wie FilledTonalIconButton, aber mit langem Druck.
    val farben = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(if (aktiv) farben.secondaryContainer else farben.onSurface.copy(alpha = 0.12f))
            .combinedClickable(
                enabled = aktiv,
                onClickLabel = stringResource(R.string.k_foto),
                onLongClickLabel = if (beiGalerie != null) stringResource(R.string.k_galerie) else null,
                onClick = ausloesen,
                onLongClick = if (beiGalerie == null) null else {
                    {
                        try {
                            galerie.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        } catch (_: ActivityNotFoundException) {
                            Toast.makeText(kontext, keineKamera, Toast.LENGTH_LONG).show()
                        }
                    }
                },
            ),
    ) {
        Icon(
            Icons.Outlined.PhotoCamera,
            contentDescription = stringResource(R.string.k_foto),
            tint = if (aktiv) farben.onSecondaryContainer else farben.onSurface.copy(alpha = 0.38f),
        )
    }
}
