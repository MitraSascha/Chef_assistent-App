package de.chefassistent.app.monteur.ui.bericht

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.chefassistent.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

/**
 * Was der Unterschrift-Dialog braucht: Striche (ueberleben das Drehen), Flaeche, Name.
 * Bericht und Protokoll-Karte (1.25) nutzen denselben Dialog.
 */
interface UnterschriftFlaeche {
    val striche: List<List<Offset>>
    val zeichenflaeche: Size
    var unterschriftName: String
    fun strichBeginnen(punkt: Offset)
    fun strichWeiter(punkt: Offset)
    fun stricheLoeschen()
    fun flaecheGeaendert(neu: Size)
    fun unterschriftSetzen(png: ByteArray, name: String)
}

/** Eigenstaendige Zeichenflaeche, z. B. im `EingangViewModel` fuer Protokolle. */
class Zeichenblock(name: String, private val beiFertig: (ByteArray, String) -> Unit) : UnterschriftFlaeche {
    override val striche = androidx.compose.runtime.mutableStateListOf<List<Offset>>()
    override var zeichenflaeche by mutableStateOf(Size.Zero)
        private set
    override var unterschriftName by mutableStateOf(name)

    override fun strichBeginnen(punkt: Offset) {
        striche.add(listOf(punkt))
    }

    override fun strichWeiter(punkt: Offset) {
        val letzter = striche.lastOrNull() ?: return
        striche[striche.lastIndex] = letzter + punkt
    }

    override fun stricheLoeschen() = striche.clear()

    override fun flaecheGeaendert(neu: Size) {
        val alt = zeichenflaeche
        zeichenflaeche = neu
        if (alt == Size.Zero || alt == neu || striche.isEmpty()) return
        val f = minOf(neu.width / alt.width, neu.height / alt.height)
        val kopie = striche.map { strich -> strich.map { Offset(it.x * f, it.y * f) } }
        striche.clear()
        striche.addAll(kopie)
    }

    override fun unterschriftSetzen(png: ByteArray, name: String) = beiFertig(png, name.trim())
}

/**
 * Kundenunterschrift vollflaechig (Auftrag 1.22, 3.). Quer halten ist erlaubt:
 * die Striche liegen im ViewModel und werden beim Drehen mitskaliert. Die
 * Zeichenflaeche ist immer weiss mit schwarzer Schrift, auch im Dunkelmodus -
 * so sieht der Kunde, was in HERO landet.
 */
@Composable
fun UnterschriftDialog(viewModel: UnterschriftFlaeche, beiFertig: () -> Unit, beiAbbruch: () -> Unit) {
    val dichte = LocalDensity.current
    val strichBreite = with(dichte) { STRICH_DP.dp.toPx() }
    val scope = rememberCoroutineScope()
    var rechnet by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = beiAbbruch,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.b_unterschrift_titel),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f).padding(start = 4.dp),
                    )
                    IconButton(onClick = beiAbbruch) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.b_abbrechen))
                    }
                }
                OutlinedTextField(
                    value = viewModel.unterschriftName,
                    onValueChange = { viewModel.unterschriftName = it },
                    label = { Text(stringResource(R.string.b_unterschrift_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .background(Color.White, KARTEN_FORM)
                        .border(1.dp, MaterialTheme.colorScheme.outline, KARTEN_FORM),
                ) {
                    if (viewModel.striche.isEmpty()) {
                        Text(
                            stringResource(R.string.b_unterschrift_hier),
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    Canvas(
                        Modifier
                            .fillMaxSize()
                            .onSizeChanged { viewModel.flaecheGeaendert(Size(it.width.toFloat(), it.height.toFloat())) }
                            .pointerInput(Unit) {
                                detectTapGestures(onTap = { viewModel.strichBeginnen(it) })
                            }
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { viewModel.strichBeginnen(it) },
                                    onDrag = { aenderung, _ -> viewModel.strichWeiter(aenderung.position) },
                                )
                            },
                    ) {
                        // Grundlinie wie auf Papier.
                        val linie = size.height * 0.75f
                        drawLine(Color.LightGray, Offset(size.width * 0.06f, linie), Offset(size.width * 0.94f, linie), 2f)
                        viewModel.striche.forEach { strich ->
                            if (strich.size == 1) {
                                drawCircle(Color.Black, strichBreite / 2, strich[0])
                            } else {
                                val pfad = Path().apply {
                                    moveTo(strich[0].x, strich[0].y)
                                    strich.drop(1).forEach { lineTo(it.x, it.y) }
                                }
                                drawPath(pfad, Color.Black, style = Stroke(strichBreite, cap = StrokeCap.Round, join = StrokeJoin.Round))
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = viewModel::stricheLoeschen,
                        enabled = viewModel.striche.isNotEmpty() && !rechnet,
                        shape = KNOPF_FORM,
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    ) { Text(stringResource(R.string.b_loeschen)) }
                    Button(
                        onClick = {
                            val striche = viewModel.striche.toList()
                            val flaeche = viewModel.zeichenflaeche
                            val name = viewModel.unterschriftName
                            rechnet = true
                            scope.launch {
                                val png = withContext(Dispatchers.Default) { unterschriftPng(striche, flaeche, strichBreite) }
                                rechnet = false
                                viewModel.unterschriftSetzen(png, name)
                                beiFertig()
                            }
                        },
                        enabled = viewModel.striche.isNotEmpty() && !rechnet && viewModel.zeichenflaeche != Size.Zero,
                        colors = navyKnopf(),
                        shape = KNOPF_FORM,
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    ) { Text(stringResource(R.string.b_ok)) }
                }
            }
        }
    }
}

/**
 * Striche als PNG mit weissem Hintergrund (HERO zeigt Transparenz sonst schwarz).
 * Ueber [UNTERSCHRIFT_MAX_BYTES] wird kleiner gerechnet, bis es passt -
 * bei einer Unterschrift kommt das praktisch nie vor, ist aber Vertragsgrenze.
 */
internal fun unterschriftPng(striche: List<List<Offset>>, flaeche: Size, strichBreite: Float): ByteArray {
    var skala = 1f
    while (true) {
        val breite = (flaeche.width * skala).roundToInt().coerceAtLeast(1)
        val hoehe = (flaeche.height * skala).roundToInt().coerceAtLeast(1)
        val bild = Bitmap.createBitmap(breite, hoehe, Bitmap.Config.ARGB_8888)
        val leinwand = android.graphics.Canvas(bild)
        leinwand.drawColor(android.graphics.Color.WHITE)
        leinwand.scale(skala, skala)
        val stift = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = strichBreite
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        striche.forEach { strich ->
            if (strich.size == 1) {
                leinwand.drawPoint(strich[0].x, strich[0].y, stift)
            } else {
                val pfad = android.graphics.Path().apply {
                    moveTo(strich[0].x, strich[0].y)
                    strich.drop(1).forEach { lineTo(it.x, it.y) }
                }
                leinwand.drawPath(pfad, stift)
            }
        }
        val bytes = ByteArrayOutputStream().use { aus ->
            bild.compress(Bitmap.CompressFormat.PNG, 100, aus)
            aus.toByteArray()
        }
        bild.recycle()
        val weiter = unterschriftSkalierung(bytes.size.toLong()) ?: return bytes
        skala *= weiter
    }
}

/** Strichstaerke der Unterschrift. */
private const val STRICH_DP = 3
