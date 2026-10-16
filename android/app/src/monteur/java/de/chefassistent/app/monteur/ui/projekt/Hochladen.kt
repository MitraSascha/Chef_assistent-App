package de.chefassistent.app.monteur.ui.projekt

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import de.chefassistent.app.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Knopf „Foto/Video/Datei“ der Projektmappe (Auftrag 1.22, 2a): Kamera fuer Foto
 * oder Video, oder eine Datei aus der Systemauswahl (ohne Speicher-Berechtigung).
 * Danach fragt ein Dialog nach einer kurzen Beschreibung; [beiDatei] bekommt die
 * Datei im Cache samt Namen. Fotos werden **nicht** verkleinert, das macht der Server.
 */
@Composable
fun HochladenKnopf(aktiv: Boolean, beiDatei: (datei: File, name: String, text: String) -> Unit, modifier: Modifier = Modifier) {
    val kontext = LocalContext.current
    var menue by remember { mutableStateOf(false) }
    // Ueberlebt, dass Android die App waehrend der Kamera beendet.
    var kameraPfad by rememberSaveable { mutableStateOf<String?>(null) }
    var gewaehlt by rememberSaveable { mutableStateOf<String?>(null) }
    val keineKamera = stringResource(R.string.m_keine_app)

    val aufgenommen: (Boolean) -> Unit = { ok ->
        val datei = kameraPfad?.let(::File)
        kameraPfad = null
        if (datei != null) {
            if (ok && datei.length() > 0) gewaehlt = datei.path else datei.delete()
        }
    }
    val foto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture(), aufgenommen)
    val video = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo(), aufgenommen)
    val auswahl = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { kopieren(kontext, it) }?.let { gewaehlt = it.path }
    }

    fun kamera(istVideo: Boolean) {
        val ordner = File(kontext.cacheDir, if (istVideo) "videos" else "fotos").apply { mkdirs() }
        val stempel = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.GERMANY).format(Date())
        val datei = File(ordner, if (istVideo) "Video_$stempel.mp4" else "Foto_$stempel.jpg")
        val uri = FileProvider.getUriForFile(kontext, "${kontext.packageName}.dateien", datei)
        kameraPfad = datei.path
        try {
            if (istVideo) video.launch(uri) else foto.launch(uri)
        } catch (_: ActivityNotFoundException) {
            kameraPfad = null
            Toast.makeText(kontext, keineKamera, Toast.LENGTH_LONG).show()
        }
    }

    Box(modifier) {
        OutlinedButton(
            onClick = { menue = true },
            enabled = aktiv,
            shape = RoundedCornerShape(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) {
            Icon(Icons.Outlined.AddAPhoto, null, Modifier.size(20.dp))
            Spacer(Modifier.size(6.dp))
            Text(
                stringResource(R.string.m_hochladen_knopf),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DropdownMenu(expanded = menue, onDismissRequest = { menue = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.m_hochladen_foto)) },
                leadingIcon = { Icon(Icons.Outlined.PhotoCamera, null) },
                onClick = { menue = false; kamera(false) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.m_hochladen_video)) },
                leadingIcon = { Icon(Icons.Outlined.Videocam, null) },
                onClick = { menue = false; kamera(true) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.m_hochladen_datei)) },
                leadingIcon = { Icon(Icons.Outlined.AttachFile, null) },
                onClick = {
                    menue = false
                    try {
                        auswahl.launch(DATEI_TYPEN)
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(kontext, keineKamera, Toast.LENGTH_LONG).show()
                    }
                },
            )
        }
    }

    gewaehlt?.let { pfad ->
        val datei = File(pfad)
        var text by rememberSaveable(pfad) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.m_hochladen_titel)) },
            text = {
                Column {
                    Text(
                        "${datei.name} · ${groesseText(datei.length())}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it.take(200) },
                        label = { Text(stringResource(R.string.m_hochladen_beschreibung)) },
                        singleLine = true,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    gewaehlt = null
                    beiDatei(datei, datei.name, text)
                }) { Text(stringResource(R.string.m_hochladen_los)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    gewaehlt = null
                    datei.delete()
                }) { Text(stringResource(R.string.m_abbrechen)) }
            },
        )
    }
}

/** Fotos, Videos, PDF, Word, Excel, Text - was der Server laut Vertrag annimmt. */
private val DATEI_TYPEN = arrayOf(
    "image/*", "video/*", "application/pdf",
    "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "text/*",
)

/** Kopiert eine gewaehlte Datei in den Cache, mit ihrem Anzeigenamen (Endung = Art fuer den Server). */
private fun kopieren(kontext: Context, uri: Uri): File? = try {
    val name = kontext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "Datei_${System.currentTimeMillis()}"
    val ordner = File(kontext.cacheDir, "monteur_hochladen").apply { mkdirs() }
    val ziel = File(ordner, name)
    kontext.contentResolver.openInputStream(uri)?.use { ein -> ziel.outputStream().use { ein.copyTo(it) } }
    ziel.takeIf { it.length() > 0 }
} catch (_: Exception) {
    null
}

fun groesseText(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(Locale.GERMANY, bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024 -> "%.1f MB".format(Locale.GERMANY, bytes / (1024.0 * 1024))
    else -> "%d kB".format(Locale.GERMANY, (bytes / 1024).coerceAtLeast(1))
}
