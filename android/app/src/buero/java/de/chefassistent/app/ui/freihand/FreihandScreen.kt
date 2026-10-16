package de.chefassistent.app.ui.freihand

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.chefassistent.app.R
import de.chefassistent.app.ui.chat.findActivity

/**
 * Freihandmodus fuers Auto und die Baustelle.
 *
 * Grundsatz: nichts, was man treffen muss. Die Runde laeuft von selbst weiter -
 * zuhoeren, senden, vorlesen, wieder zuhoeren. Nur zwei Knoepfe, beide gross:
 * "jetzt senden" fuer den Fall, dass die Stille-Erkennung zu lange braucht,
 * und "beenden".
 */
@Composable
fun FreihandScreen(
    viewModel: FreihandViewModel,
    beiZurueck: () -> Unit,
    beiAbmeldung: () -> Unit,
) {
    val zustand by viewModel.zustand.collectAsStateWithLifecycle()
    val kontext = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val activity = remember(kontext) { kontext.findActivity() }

    // Ohne das geht der Bildschirm mitten im Gespraech aus. Fensterflag statt
    // `View.keepScreenOn`: das View-Flag blieb am S25 nach Unterbrechungen haengen
    // (siehe Chat). Beim Drehen wird der Bildschirm nur neu aufgebaut - dann
    // laeuft die Runde weiter, statt die Aufnahme zu verwerfen.
    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (activity?.isChangingConfigurations != true) viewModel.beenden()
        }
    }

    // Ein/Aus-Taste, Anruf, App gewechselt: gesperrt liefert das Mikrofon nur
    // Stille. Was schon gesagt wurde, geht raus, danach haelt der Modus an.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (activity?.isChangingConfigurations != true) viewModel.hintergrund()
    }

    var mikrofonErlaubt by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(kontext, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val mikrofonAnfrage = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { erlaubt -> if (erlaubt) mikrofonErlaubt = true else beiZurueck() }

    LaunchedEffect(Unit) {
        if (!mikrofonErlaubt) mikrofonAnfrage.launch(Manifest.permission.RECORD_AUDIO)
    }

    // Sobald eine Runde durch ist, faengt die naechste an - solange nicht
    // mangels Sprache pausiert wurde. Erst mit Berechtigung, sonst meldete
    // die Aufnahme hinter dem Dialog "Mikrofon nicht verfuegbar".
    LaunchedEffect(zustand.phase, zustand.pausiert, mikrofonErlaubt) {
        if (mikrofonErlaubt && zustand.phase == Phase.BEREIT && !zustand.pausiert) viewModel.zuhoeren()
    }

    LaunchedEffect(zustand.abgemeldet) {
        if (zustand.abgemeldet) beiAbmeldung()
    }

    LaunchedEffect(zustand.fehler) {
        zustand.fehler?.let {
            snackbar.showSnackbar(it)
            viewModel.fehlerVerwerfen()
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbar) { daten ->
                Snackbar(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ) { Text(daten.visuals.message) }
            }
        },
    ) { innen ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innen)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(
                    when {
                        zustand.pausiert -> R.string.freihand_pausiert
                        zustand.phase == Phase.HOERT -> R.string.freihand_hoert
                        zustand.phase == Phase.SENDET -> R.string.freihand_sendet
                        zustand.phase == Phase.SPRICHT -> R.string.freihand_spricht
                        else -> R.string.freihand_bereit
                    }
                ),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(32.dp))

            Pegelkreis(phase = zustand.phase, pegel = zustand.pegel)

            Spacer(Modifier.height(32.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (zustand.letzteFrage.isNotBlank()) {
                    Text(
                        text = zustand.letzteFrage,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (zustand.letzteAntwort.isNotBlank()) {
                    // Bewusst als schlichter Text: im Auto liest niemand Markdown,
                    // und vorgelesen wird ohnehin.
                    Text(
                        text = zustand.letzteAntwort,
                        fontSize = 22.sp,
                        lineHeight = 30.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (zustand.pausiert) {
                Button(
                    onClick = viewModel::weiter,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                ) {
                    Icon(Icons.Default.Mic, null, Modifier.size(28.dp))
                    Spacer(Modifier.size(12.dp))
                    Text(stringResource(R.string.freihand_weiter), fontSize = 20.sp)
                }
                Spacer(Modifier.height(12.dp))
            }

            if (zustand.phase == Phase.HOERT && !zustand.pausiert) {
                Button(
                    onClick = viewModel::jetztSenden,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(28.dp))
                    Spacer(Modifier.size(12.dp))
                    Text(stringResource(R.string.freihand_jetzt_senden), fontSize = 20.sp)
                }
                Spacer(Modifier.height(12.dp))
            }

            OutlinedButton(
                onClick = {
                    viewModel.beenden()
                    beiZurueck()
                },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
            ) {
                Icon(Icons.Default.Close, null, Modifier.size(28.dp))
                Spacer(Modifier.size(12.dp))
                Text(stringResource(R.string.freihand_beenden), fontSize = 20.sp)
            }
        }
    }
}

/** Grosser Kreis, der beim Sprechen mitatmet - Rueckmeldung ohne Lesen. */
@Composable
private fun Pegelkreis(phase: Phase, pegel: Float) {
    val ziel = when (phase) {
        Phase.HOERT -> 1f + (pegel * 1.2f)
        Phase.SPRICHT -> 1.1f
        else -> 1f
    }
    val groesse by animateFloatAsState(targetValue = ziel, label = "pegel")

    Box(
        modifier = Modifier
            .size(180.dp)
            .scale(groesse.coerceIn(1f, 1.6f))
            .background(
                color = when (phase) {
                    Phase.HOERT -> MaterialTheme.colorScheme.primaryContainer
                    Phase.SPRICHT -> MaterialTheme.colorScheme.secondaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        when (phase) {
            Phase.SENDET -> CircularProgressIndicator(Modifier.size(56.dp), strokeWidth = 5.dp)

            Phase.SPRICHT -> Icon(
                Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )

            else -> Icon(
                Icons.Default.Mic,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
