package de.chefassistent.app.ui.ansehen

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import de.chefassistent.app.R
import de.chefassistent.app.data.FotoAufbereitung
import de.chefassistent.app.ui.MarkdownText
import de.chefassistent.app.ui.dateiOeffnen
import de.chefassistent.app.ui.dateiTeilen
import de.chefassistent.app.ui.komponenten.DateiArt
import de.chefassistent.app.ui.komponenten.dateiArt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File

/**
 * Datei in der App ansehen (Auftrag 1.21, B3): PDF als Seitenbilder, Fotos mit
 * Zoom, Videos im Player, Text und Notizen. Oben Name, Seitenzahl, „Öffnen mit …“
 * (ACTION_VIEW wie bisher) und Teilen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnsehenScreen(
    viewModel: AnsehenViewModel,
    beiZurueck: () -> Unit,
    beiAbmeldung: () -> Unit,
) {
    val zustand by viewModel.zustand.collectAsStateWithLifecycle()
    val kontext = LocalContext.current
    val name = viewModel.pfad.substringAfterLast('/')
    var seiten by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(zustand.abgemeldet) {
        if (zustand.abgemeldet) beiAbmeldung()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                navigationIcon = {
                    IconButton(onClick = beiZurueck) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.zurueck))
                    }
                },
                title = {
                    Column {
                        Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        seiten?.let {
                            Text(
                                if (it == 1) stringResource(R.string.ansehen_seite_eins) else stringResource(R.string.ansehen_seiten, it),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    val datei = zustand.datei
                    val teilenTitel = stringResource(R.string.link_teilen)
                    IconButton(onClick = { datei?.let { dateiOeffnen(kontext, it) } }, enabled = datei != null) {
                        Icon(Icons.Outlined.OpenInNew, stringResource(R.string.ansehen_oeffnen_mit))
                    }
                    IconButton(onClick = { datei?.let { dateiTeilen(kontext, it, teilenTitel) } }, enabled = datei != null) {
                        Icon(Icons.Outlined.Share, teilenTitel)
                    }
                },
            )
        },
    ) { innen ->
        Box(Modifier.fillMaxSize().padding(innen), contentAlignment = Alignment.Center) {
            val datei = zustand.datei
            when {
                zustand.laeuft -> CircularProgressIndicator()
                zustand.fehler != null -> Hinweis(zustand.fehler!!, viewModel::laden)
                datei == null -> Unit
                zustand.text != null -> TextAnsicht(zustand.text!!, istMarkdown = name.endsWith(".md", true))
                else -> when (dateiArt(name)) {
                    DateiArt.PDF -> PdfAnsicht(datei) { seiten = it }
                    DateiArt.VIDEO -> VideoAnsicht(datei)
                    else -> BildAnsicht(datei)
                }
            }
        }
    }
}

@Composable
private fun Hinweis(text: String, beiNochmal: (() -> Unit)? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (beiNochmal != null) {
            TextButton(onClick = beiNochmal) { Text(stringResource(R.string.ansehen_nochmal)) }
        }
    }
}

/**
 * Zoom per Pinch, Doppeltipp setzt zurueck. Verschoben wird nur, solange
 * vergroessert ist - sonst bekaeme die Seitenliste kein Wischen zum Blaettern mehr.
 */
@Composable
private fun Zoombar(inhalt: @Composable () -> Unit) {
    var skala by remember { mutableFloatStateOf(1f) }
    var versatz by remember { mutableStateOf(Offset.Zero) }
    var groesse by remember { mutableStateOf(IntSize.Zero) }
    fun begrenzt(o: Offset): Offset {
        val maxX = (skala - 1f) * groesse.width / 2f
        val maxY = (skala - 1f) * groesse.height / 2f
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }
    val gesten = rememberTransformableState { zoom, pan, _ ->
        skala = (skala * zoom).coerceIn(1f, 5f)
        versatz = if (skala <= 1f) Offset.Zero else begrenzt(versatz + pan)
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { groesse = it }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    skala = if (skala > 1f) 1f else 2.5f
                    versatz = Offset.Zero
                })
            }
            .transformable(gesten, canPan = { skala > 1f })
            .graphicsLayer {
                scaleX = skala
                scaleY = skala
                translationX = versatz.x
                translationY = versatz.y
            },
    ) { inhalt() }
}

/** Haelt den PdfRenderer. Er kann nur eine Seite zugleich offen haben, daher die Sperre. */
private class PdfDokument(datei: File) : Closeable {
    private val pfd = ParcelFileDescriptor.open(datei, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(pfd)
    private val sperre = Mutex()
    private var zu = false
    val seiten: Int = renderer.pageCount

    suspend fun seite(index: Int, breite: Int): Bitmap? = sperre.withLock {
        if (zu) return@withLock null
        withContext(Dispatchers.IO) {
            val seite = renderer.openPage(index)
            try {
                val hoehe = (breite.toLong() * seite.height / seite.width.coerceAtLeast(1)).toInt().coerceAtLeast(1)
                Bitmap.createBitmap(breite, hoehe, Bitmap.Config.ARGB_8888).also {
                    it.eraseColor(AndroidColor.WHITE)
                    seite.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            } finally {
                seite.close()
            }
        }
    }

    /** Erst schliessen, wenn keine Seite mehr gezeichnet wird. */
    override fun close() {
        CoroutineScope(Dispatchers.IO).launch {
            sperre.withLock {
                zu = true
                renderer.close()
                pfd.close()
            }
        }
    }
}

@Composable
private fun PdfAnsicht(datei: File, beiSeiten: (Int) -> Unit) {
    val dokument = remember(datei) { runCatching { PdfDokument(datei) }.getOrNull() }
    DisposableEffect(dokument) { onDispose { dokument?.close() } }
    if (dokument == null) {
        Hinweis(stringResource(R.string.ansehen_pdf_kaputt))
        return
    }
    LaunchedEffect(dokument) { beiSeiten(dokument.seiten) }
    // Doppelte Bildschirmbreite, damit es beim Zoomen nicht sofort verschwimmt.
    val breite = with(LocalDensity.current) { (LocalConfiguration.current.screenWidthDp.dp.toPx() * 2).toInt() }
        .coerceAtMost(2400)
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Zoombar {
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(dokument.seiten) { index ->
                    val bild by produceState<Bitmap?>(null, index) { value = dokument.seite(index, breite) }
                    val b = bild
                    if (b == null) {
                        Box(
                            Modifier.fillMaxWidth().aspectRatio(1f / 1.414f).background(Color.White),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator() }
                    } else {
                        Image(
                            bitmap = b.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BildAnsicht(datei: File) {
    // Unit = laedt noch, null = gescheitert
    val bild by produceState<Any?>(Unit, datei) {
        value = withContext(Dispatchers.Default) { FotoAufbereitung.vorschau(datei, 2560) }
    }
    when (val b = bild) {
        is Bitmap -> Box(Modifier.fillMaxSize().background(Color.Black)) {
            Zoombar {
                Image(
                    bitmap = b.asImageBitmap(),
                    contentDescription = datei.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Unit -> CircularProgressIndicator()
        else -> Hinweis(stringResource(R.string.ansehen_bild_kaputt))
    }
}

@Composable
private fun VideoAnsicht(datei: File) {
    val kontext = LocalContext.current
    val spieler = remember(datei) {
        ExoPlayer.Builder(kontext).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(datei)))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(spieler) { onDispose { spieler.release() } }
    AndroidView(
        factory = { PlayerView(it).apply { player = spieler } },
        modifier = Modifier.fillMaxSize().background(Color.Black),
    )
}

@Composable
private fun TextAnsicht(text: String, istMarkdown: Boolean) {
    if (istMarkdown) {
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            MarkdownText(text, modifier = Modifier.fillMaxWidth())
        }
    } else {
        // CSV und Logs: Zeilen nicht umbrechen, seitlich scrollen.
        Box(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        }
    }
}
