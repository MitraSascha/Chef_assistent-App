package de.chefassistent.app.monteur.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import de.chefassistent.app.R
import de.chefassistent.app.data.AudioAufnahme
import de.chefassistent.app.ui.theme.hauptflaeche
import java.io.File

/**
 * Haelt eine laufende Aufnahme ueber Neuzeichnen hinweg. Format wie in der
 * Buero-App (m4a/AAC, mono), der Server nimmt es fuer Bericht, Diktat und Verstehen.
 */
class AufnahmeSteuerung(kontext: Context) {
    private val aufnahme = AudioAufnahme(kontext.applicationContext)

    var nimmtAuf by mutableStateOf(false)
        private set

    /** `false`, wenn das Mikrofon belegt ist. */
    fun starten(): Boolean = try {
        aufnahme.starten()
        nimmtAuf = true
        true
    } catch (_: Exception) {
        false
    }

    /** Fertige Datei, `null` bei zu kurzer Aufnahme. */
    fun beenden(): File? {
        nimmtAuf = false
        return aufnahme.beenden()
    }

    fun abbrechen() {
        nimmtAuf = false
        aufnahme.abbrechen()
    }
}

/** Bricht die Aufnahme ab, wenn der Bildschirm verschwindet. */
@Composable
fun rememberAufnahme(): AufnahmeSteuerung {
    val kontext = LocalContext.current
    val steuerung = remember { AufnahmeSteuerung(kontext) }
    DisposableEffect(steuerung) { onDispose { steuerung.abbrechen() } }
    return steuerung
}

/**
 * Grosser Mikrofon-Knopf (Auftrag 1.22, 3.): **antippen** startet, nochmal
 * antippen beendet; **gedrueckt halten** nimmt auf, bis man loslaesst.
 * Waehrend der Aufnahme rot mit Stopp-Symbol, und der Bildschirm bleibt an -
 * gesperrt liefert das Mikrofon ab Android 11 nur Stille.
 *
 * [hinweis] steht klein darunter, z. B. „Was, wie lange, welches Material …“.
 */
@Composable
fun MikrofonKnopf(
    steuerung: AufnahmeSteuerung,
    beiFertig: (File) -> Unit,
    modifier: Modifier = Modifier,
    aktiv: Boolean = true,
    groesse: Dp = 76.dp,
    beschriftung: String = stringResource(R.string.m_mikro_frage),
    hinweis: String? = null,
) {
    val kontext = LocalContext.current
    val fertig by rememberUpdatedState(beiFertig)
    val erlaubnis = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) Toast.makeText(kontext, R.string.m_mikro_keine_erlaubnis, Toast.LENGTH_LONG).show()
    }
    val fenster = remember(kontext) { kontext.aktivitaet()?.window }
    DisposableEffect(steuerung.nimmtAuf, fenster) {
        if (steuerung.nimmtAuf) fenster?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else fenster?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { fenster?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    fun darf(): Boolean {
        val ok = ContextCompat.checkSelfPermission(kontext, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!ok) erlaubnis.launch(Manifest.permission.RECORD_AUDIO)
        return ok
    }

    fun abschliessen() {
        steuerung.beenden()?.let(fertig)
    }

    val farbe = if (steuerung.nimmtAuf) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.hauptflaeche
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(groesse)
                .shadow(6.dp, CircleShape)
                .background(if (aktiv) farbe else farbe.copy(alpha = 0.4f), CircleShape)
                .semantics {
                    role = Role.Button
                    contentDescription = beschriftung
                }
                .pointerInput(aktiv) {
                    if (!aktiv) return@pointerInput
                    detectTapGestures(
                        onPress = {
                            if (steuerung.nimmtAuf) {
                                // Zweiter Tipp beendet.
                                tryAwaitRelease()
                                abschliessen()
                                return@detectTapGestures
                            }
                            if (!darf()) return@detectTapGestures
                            if (!steuerung.starten()) {
                                Toast.makeText(kontext, R.string.m_mikro_belegt, Toast.LENGTH_LONG).show()
                                return@detectTapGestures
                            }
                            val beginn = System.currentTimeMillis()
                            tryAwaitRelease()
                            // Lange gehalten = Funkgeraet-Art: Loslassen beendet.
                            if (System.currentTimeMillis() - beginn > HALTEN_MS) abschliessen()
                        },
                    )
                },
        ) {
            Icon(
                if (steuerung.nimmtAuf) Icons.Default.Stop else Icons.Outlined.Mic,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(groesse * 0.45f),
            )
        }
        Text(
            text = if (steuerung.nimmtAuf) stringResource(R.string.m_mikro_laeuft) else beschriftung,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (hinweis != null && !steuerung.nimmtAuf) {
            Text(
                text = hinweis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
    }
}

/** Ab so langem Druck gilt es als Halten statt Tippen. */
private const val HALTEN_MS = 400L

internal tailrec fun Context.aktivitaet(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.aktivitaet()
    else -> null
}
