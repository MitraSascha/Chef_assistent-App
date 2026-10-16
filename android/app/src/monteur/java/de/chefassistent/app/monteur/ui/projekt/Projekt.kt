package de.chefassistent.app.monteur.ui.projekt

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.chefassistent.app.R
import de.chefassistent.app.data.ApiFehler
import de.chefassistent.app.data.FotoAufbereitung
import de.chefassistent.app.monteur.MonteurApp
import de.chefassistent.app.monteur.data.Foto
import de.chefassistent.app.monteur.data.Projektmappe
import de.chefassistent.app.monteur.data.Unterlage
import de.chefassistent.app.monteur.ui.heute.Zeile
import de.chefassistent.app.monteur.ui.heute.navigieren
import de.chefassistent.app.monteur.ui.heute.waehlen
import de.chefassistent.app.ui.komponenten.TypMarke
import de.chefassistent.app.ui.theme.hauptflaeche
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

data class ProjektZustand(
    val mappe: Projektmappe? = null,
    val laeuft: Boolean = true,
    val fehler: String? = null,
    val abgemeldet: Boolean = false,
)

/**
 * Projektmappe (Auftrag 1.22, 2a): Termin antippen = Projekt wie in HERO.
 * Kunde, Adresse, Telefon, Notizen des Bueros, Unterlagen (ohne Rechnungen -
 * die sperrt der Server) und Fotos. Unten der grosse Knopf „Bericht“.
 */
class ProjektViewModel(val app: MonteurApp, val projektId: Int) : ViewModel() {

    private val _zustand = MutableStateFlow(ProjektZustand())
    val zustand: StateFlow<ProjektZustand> = _zustand.asStateFlow()

    init {
        laden()
    }

    fun laden() {
        _zustand.update { it.copy(laeuft = true, fehler = null) }
        viewModelScope.launch {
            try {
                val mappe = app.repository.projekt(projektId)
                _zustand.update { it.copy(mappe = mappe, laeuft = false) }
            } catch (fehler: ApiFehler) {
                if (fehler is ApiFehler.NichtAngemeldet) {
                    app.repository.lokalAbmelden()
                    _zustand.update { it.copy(abgemeldet = true) }
                }
                _zustand.update { it.copy(fehler = fehler.anzeigetext, laeuft = false) }
            }
        }
    }

    /** Foto laden, `null` bei Fehler (dann bleibt die Kachel grau). */
    suspend fun foto(foto: Foto, vorschau: Boolean): Bitmap? = try {
        val datei = app.repository.datei(foto.uuid, foto.name.ifBlank { "${foto.uuid}.jpg" }, vorschau)
        withContext(Dispatchers.Default) { bild(datei, if (vorschau) 480 else 2560) }
    } catch (_: ApiFehler) {
        null
    }

    private fun bild(datei: File, seite: Int): Bitmap? =
        FotoAufbereitung.vorschau(datei, seite) ?: BitmapFactory.decodeFile(datei.path)

    companion object {
        fun factory(app: MonteurApp, projektId: Int) = viewModelFactory { initializer { ProjektViewModel(app, projektId) } }

        /** Unterlagen nach Art, in der Reihenfolge des Servers (je Art neueste zuerst). */
        fun nachArt(unterlagen: List<Unterlage>): List<Pair<String, List<Unterlage>>> =
            unterlagen.groupBy { it.art }.toList()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjektScreen(
    viewModel: ProjektViewModel,
    entwurfId: String?,
    beiZurueck: () -> Unit,
    beiAbmeldung: () -> Unit,
    beiDatei: (uuid: String, name: String) -> Unit,
    beiBericht: (berichtId: String?, projektNr: String?) -> Unit,
) {
    val zustand by viewModel.zustand.collectAsStateWithLifecycle()
    val kontext = LocalContext.current
    var fotoIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    LaunchedEffect(zustand.abgemeldet) { if (zustand.abgemeldet) beiAbmeldung() }
    val mappe = zustand.mappe

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                navigationIcon = {
                    IconButton(onClick = beiZurueck) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.m_zurueck)) }
                },
                title = {
                    Column {
                        Text(mappe?.kunde?.ifBlank { null } ?: mappe?.titel.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (mappe != null) {
                            Text(
                                listOf(mappe.nr, mappe.titel).filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            // Grosser Knopf unten, wie in HERO: Termin antippen, dort alles erledigen.
            // Eigene Flaeche, sonst scheint die Liste darunter durch.
            Surface(color = MaterialTheme.colorScheme.surface) {
            Button(
                onClick = { beiBericht(entwurfId, mappe?.nr) },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.hauptflaeche, contentColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .heightIn(min = 56.dp),
            ) {
                Icon(Icons.Outlined.Description, null)
                Spacer(Modifier.size(10.dp))
                Text(
                    stringResource(if (entwurfId != null) R.string.m_entwurf_oeffnen else R.string.m_bericht),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            }
        },
    ) { innen ->
        when {
            mappe == null && zustand.laeuft -> Box(Modifier.fillMaxSize().padding(innen), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            mappe == null -> Column(
                Modifier.fillMaxSize().padding(innen).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(zustand.fehler.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = viewModel::laden) { Text(stringResource(R.string.m_nochmal)) }
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = innen.calculateTopPadding(), bottom = innen.calculateBottomPadding() + 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "kontakt") {
                    Column {
                        mappe.adresse.takeIf { it.isNotBlank() }?.let { a ->
                            Zeile(Icons.Outlined.Place, a, stringResource(R.string.m_navigation, a)) { navigieren(kontext, a, mappe.geo) }
                        }
                        mappe.telefon.forEach { t ->
                            val name = listOf(t.name, t.art).filter { it.isNotBlank() }.joinToString(", ")
                            Zeile(Icons.Outlined.Phone, if (name.isBlank()) t.nummer else "${t.nummer} · $name", stringResource(R.string.m_anrufen, t.nummer)) {
                                waehlen(kontext, t.nummer)
                            }
                        }
                    }
                }
                if (mappe.notizen.isNotBlank()) {
                    item(key = "notizen") {
                        // Hervorgehoben: was das Buero hier eintraegt, soll der Monteur nicht uebersehen.
                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp)) {
                                Text(stringResource(R.string.m_notizen), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                Text(mappe.notizen, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
                item(key = "unterlagen_titel") { Ueberschrift(stringResource(R.string.m_unterlagen)) }
                if (mappe.unterlagen.isEmpty()) {
                    item(key = "keine_unterlagen") {
                        Text(stringResource(R.string.m_keine_unterlagen), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                ProjektViewModel.nachArt(mappe.unterlagen).forEach { (art, liste) ->
                    item(key = "art_$art") {
                        Text(art, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    }
                    items(liste, key = { "u_${art}_${it.datei ?: it.name}_${it.nr}" }) { u -> UnterlageZeile(u, beiDatei) }
                }
                if (mappe.fotos.isNotEmpty()) {
                    item(key = "fotos_titel") { Ueberschrift(stringResource(R.string.m_fotos)) }
                    val reihen = mappe.fotos.withIndex().chunked(3)
                    items(reihen.size, key = { "fotos_$it" }) { r ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            reihen[r].forEach { (i, f) ->
                                FotoKachel(f, viewModel, Modifier.weight(1f)) { fotoIndex = i }
                            }
                            repeat(3 - reihen[r].size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }

    val index = fotoIndex
    if (index != null && mappe != null && mappe.fotos.isNotEmpty()) {
        FotoVollbild(mappe.fotos, index.coerceIn(0, mappe.fotos.lastIndex), viewModel) { fotoIndex = null }
    }
}

@Composable
private fun Ueberschrift(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 10.dp))
}

/** Nummer/Name, Datum, Status. Ohne Datei grau „PDF folgt“ und nicht antippbar. */
@Composable
private fun UnterlageZeile(u: Unterlage, beiDatei: (String, String) -> Unit) {
    val farben = MaterialTheme.colorScheme
    val uuid = u.datei
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = farben.surfaceContainer,
        border = BorderStroke(1.dp, farben.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (uuid != null) Modifier.clickable { beiDatei(uuid, u.name) } else Modifier),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(10.dp).heightIn(min = 40.dp)) {
            TypMarke(u.name)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(
                    u.nr?.takeIf { it.isNotBlank() } ?: u.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val unter = listOfNotNull(datumText(u.datum), u.status.takeIf { it.isNotBlank() }).joinToString(" · ")
                if (unter.isNotBlank()) Text(unter, style = MaterialTheme.typography.bodySmall, color = farben.onSurfaceVariant)
            }
            if (uuid == null) {
                Text(stringResource(R.string.m_pdf_folgt), style = MaterialTheme.typography.labelMedium, color = farben.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun FotoKachel(foto: Foto, viewModel: ProjektViewModel, modifier: Modifier, beiKlick: () -> Unit) {
    val bild by produceState<Bitmap?>(null, foto.uuid) { value = viewModel.foto(foto, vorschau = true) }
    Box(
        modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = beiKlick),
        contentAlignment = Alignment.Center,
    ) {
        bild?.let { Image(it.asImageBitmap(), foto.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}

/** Vollbild, wischen zum naechsten Foto. Geladen wird das Original (ohne `vorschau`). */
@Composable
private fun FotoVollbild(fotos: List<Foto>, start: Int, viewModel: ProjektViewModel, beiSchliessen: () -> Unit) {
    Dialog(onDismissRequest = beiSchliessen, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val pager = rememberPagerState(initialPage = start) { fotos.size }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { seite ->
                val foto = fotos[seite]
                val bild by produceState<Bitmap?>(null, foto.uuid) { value = viewModel.foto(foto, vorschau = false) }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val b = bild
                    if (b == null) CircularProgressIndicator(color = Color.White)
                    else Image(b.asImageBitmap(), foto.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                }
            }
            IconButton(onClick = beiSchliessen, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                Icon(Icons.Outlined.Close, stringResource(R.string.m_zurueck), tint = Color.White)
            }
            Text(
                stringResource(R.string.m_foto_von, pager.currentPage + 1, fotos.size),
                color = Color.White,
                modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
            )
        }
    }
}

/** `YYYY-MM-DD` (auch mit Uhrzeit) -> „16.10.2026“, Unlesbares unveraendert. */
fun datumText(datum: String?): String? {
    if (datum.isNullOrBlank()) return null
    return runCatching {
        val tag = LocalDate.parse(datum.take(10))
        "%02d.%02d.%d".format(tag.dayOfMonth, tag.monthValue, tag.year)
    }.getOrDefault(datum)
}
