package de.chefassistent.app.monteur.ui.heute

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.chefassistent.app.R
import de.chefassistent.app.data.ApiFehler
import de.chefassistent.app.monteur.MonteurApp
import de.chefassistent.app.monteur.data.Geo
import de.chefassistent.app.monteur.data.KorbMeldung
import de.chefassistent.app.monteur.data.MonteurBericht
import de.chefassistent.app.monteur.data.MonteurTag
import de.chefassistent.app.monteur.data.MonteurVersand
import de.chefassistent.app.monteur.data.TagTermin
import de.chefassistent.app.monteur.data.TagProjekt
import de.chefassistent.app.monteur.ui.bericht.BerichtStart
import de.chefassistent.app.ui.komponenten.FehlerSnackbar
import de.chefassistent.app.ui.komponenten.LeerHinweis
import de.chefassistent.app.ui.theme.gold
import de.chefassistent.app.ui.theme.link
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.abs

data class HeuteZustand(
    val anker: LocalDate = LocalDate.now(),
    /** `true` = Woche Mo-So statt eines Tages. */
    val woche: Boolean = false,
    val tage: List<MonteurTag>? = null,
    val laeuft: Boolean = false,
    val fehler: String? = null,
)

/**
 * „Heute“ (Auftrag 1.22, 2.): eigene HERO-Termine je Tag mit Kunde, Adresse,
 * Telefon, Beschreibung, Kollegen und Berichtsstand (`GET /v1/monteur/tage`).
 */
class HeuteViewModel(private val app: MonteurApp) : ViewModel() {

    private val _zustand = MutableStateFlow(HeuteZustand())
    val zustand: StateFlow<HeuteZustand> = _zustand.asStateFlow()

    private val _abgemeldet = MutableStateFlow(false)
    val abgemeldet: StateFlow<Boolean> = _abgemeldet.asStateFlow()

    val wartend: StateFlow<Int> = app.korb.anzahl
    val meldungen: StateFlow<List<KorbMeldung>> = app.korb.gemeldet

    private var laden: Job? = null

    fun laden() {
        laden?.cancel()
        val z = _zustand.value
        val (von, bis) = zeitraum(z.anker, z.woche)
        _zustand.update { it.copy(laeuft = true, fehler = null) }
        laden = viewModelScope.launch {
            try {
                val tage = app.repository.tage(von.toString(), bis.toString())
                _zustand.update { it.copy(tage = tage, laeuft = false) }
            } catch (fehler: ApiFehler) {
                if (fehler is ApiFehler.NichtAngemeldet) {
                    app.repository.lokalAbmelden()
                    _abgemeldet.value = true
                }
                _zustand.update { it.copy(fehler = fehler.anzeigetext, laeuft = false) }
            }
        }
    }

    fun fehlerVerwerfen() = _zustand.update { it.copy(fehler = null) }

    /** -1 zurueck, +1 vor; je nach Ansicht um einen Tag oder eine Woche. */
    fun schritt(richtung: Int) {
        val z = _zustand.value
        _zustand.update { it.copy(anker = if (z.woche) z.anker.plusWeeks(richtung.toLong()) else z.anker.plusDays(richtung.toLong()), tage = null) }
        laden()
    }

    fun heute() {
        _zustand.update { it.copy(anker = LocalDate.now(), tage = null) }
        laden()
    }

    fun ansichtUmschalten() {
        _zustand.update { it.copy(woche = !it.woche, tage = null) }
        laden()
    }

    fun jetztSenden() = MonteurVersand.anstossen(app)

    fun meldungErledigt(meldung: KorbMeldung) = app.korb.meldungErledigt(meldung)

    companion object {
        fun factory(app: MonteurApp) = viewModelFactory { initializer { HeuteViewModel(app) } }

        /** Tag bzw. Woche Mo-So (`bis` einschliesslich, max. 14 Tage laut Vertrag). */
        fun zeitraum(anker: LocalDate, woche: Boolean): Pair<LocalDate, LocalDate> =
            if (!woche) anker to anker
            else anker.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).let { it to it.plusDays(6) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeuteReiter(
    viewModel: HeuteViewModel,
    innen: PaddingValues,
    beiTermin: (pid: Int, termin: String?, titel: String?, entwurf: String?) -> Unit,
    beiBericht: (BerichtStart) -> Unit,
) {
    val zustand by viewModel.zustand.collectAsStateWithLifecycle()
    val wartend by viewModel.wartend.collectAsStateWithLifecycle()
    val meldungen by viewModel.meldungen.collectAsStateWithLifecycle()
    val schwelle = with(LocalDensity.current) { 96.dp.toPx() }
    val snackbar = androidx.compose.runtime.remember { androidx.compose.material3.SnackbarHostState() }

    // Bei jedem Zurueckkommen neu laden: ein gerade geschriebener Bericht soll sofort als „BER-…“ stehen.
    LifecycleResumeEffect(Unit) {
        viewModel.laden()
        onPauseOrDispose { }
    }
    androidx.compose.runtime.LaunchedEffect(zustand.fehler) {
        zustand.fehler?.let {
            viewModel.fehlerVerwerfen()
            snackbar.showSnackbar(it)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(top = innen.calculateTopPadding())) {
            Kopf(zustand, viewModel::schritt, viewModel::heute, viewModel::ansichtUmschalten) {
                beiBericht(BerichtStart())
            }
            if (zustand.laeuft && zustand.tage == null) LinearProgressIndicator(Modifier.fillMaxWidth())

            PullToRefreshBox(
                isRefreshing = zustand.laeuft && zustand.tage != null,
                onRefresh = viewModel::laden,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(zustand.woche) {
                        var weg = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { weg = 0f },
                            onDragEnd = { if (abs(weg) > schwelle) viewModel.schritt(if (weg < 0) 1 else -1) },
                        ) { _, dx -> weg += dx }
                    },
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = innen.calculateBottomPadding() + 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (wartend > 0) {
                        item(key = "wartend") { WartetKarte(wartend, viewModel::jetztSenden) }
                    }
                    items(meldungen, key = { "meldung_${it.schluessel}" }) { m ->
                        MeldungKarte(
                            meldung = m,
                            beiOeffnen = m.berichtId?.let { id -> { viewModel.meldungErledigt(m); beiBericht(BerichtStart(berichtId = id, titel = m.titel)) } },
                            beiOk = { viewModel.meldungErledigt(m) },
                        )
                    }
                    val tage = zustand.tage.orEmpty()
                    tage.forEach { tag ->
                        if (zustand.woche) {
                            item(key = "kopf_${tag.datum}") {
                                Text(
                                    tagText(tag.datum),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                                )
                            }
                        }
                        if (tag.termine.isEmpty()) {
                            item(key = "leer_${tag.datum}") {
                                if (zustand.woche) {
                                    Text(
                                        stringResource(R.string.m_keine_termine),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 4.dp),
                                    )
                                } else {
                                    LeerHinweis(stringResource(R.string.m_keine_termine))
                                }
                            }
                        }
                        items(tag.termine, key = { "${tag.datum}_${it.id}" }) { termin ->
                            TerminKarte(termin) {
                                val titel = termin.projekt?.let(::projektTitel) ?: termin.titel
                                val entwurf = termin.berichte.firstOrNull { it.status != MonteurBericht.STATUS_FERTIG }?.id
                                val projekt = termin.projekt
                                if (projekt != null) beiTermin(projekt.id, termin.id, titel, entwurf)
                                else beiBericht(BerichtStart(berichtId = entwurf, terminId = termin.id, titel = titel))
                            }
                        }
                    }
                }
            }
        }
        FehlerSnackbar(snackbar)
    }
}

@Composable
private fun Kopf(
    zustand: HeuteZustand,
    beiSchritt: (Int) -> Unit,
    beiHeute: () -> Unit,
    beiAnsicht: () -> Unit,
    beiTippen: () -> Unit,
) {
    val (von, bis) = HeuteViewModel.zeitraum(zustand.anker, zustand.woche)
    val heute = LocalDate.now()
    val titel = when {
        zustand.woche -> "${von.dayOfMonth}.${von.monthValue}. – ${bis.dayOfMonth}.${bis.monthValue}."
        zustand.anker == heute -> stringResource(R.string.m_nav_heute)
        else -> tagText(zustand.anker.toString())
    }
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 56.dp, top = 8.dp)) {
        Text(titel, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
        if (!zustand.woche && zustand.anker == heute) {
            Text(
                tagText(heute.toString()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        IconButton(onClick = { beiSchritt(-1) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.m_tag_zurueck))
        }
        IconButton(onClick = beiHeute) { Icon(Icons.Outlined.Today, stringResource(R.string.m_nav_heute)) }
        IconButton(onClick = { beiSchritt(1) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.m_tag_vor))
        }
        IconButton(onClick = beiAnsicht) {
            Icon(
                Icons.Outlined.CalendarMonth,
                stringResource(if (zustand.woche) R.string.m_tag_zeigen else R.string.m_woche_zeigen),
                tint = if (zustand.woche) MaterialTheme.colorScheme.link else androidx.compose.material3.LocalContentColor.current,
            )
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = beiTippen) {
            Icon(Icons.Outlined.EditNote, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.m_bericht_tippen))
        }
    }
}

/**
 * Grosse Karte je Termin: Uhrzeit, Kunde, Titel, Adresse (Navigation), Telefon
 * (Waehlen), Beschreibung, Kollegen und der Stand des Berichts.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TerminKarte(termin: TagTermin, beiKlick: () -> Unit) {
    val kontext = LocalContext.current
    val farben = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = farben.surfaceContainer,
        border = BorderStroke(1.dp, farben.outlineVariant),
        modifier = Modifier.fillMaxWidth().clickable(onClick = beiKlick),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    zeitText(termin) ?: stringResource(R.string.m_ganztaegig),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                BerichtChip(termin)
            }
            val projekt = termin.projekt
            Text(
                projekt?.kunde?.takeIf { it.isNotBlank() } ?: termin.titel,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            val unter = listOfNotNull(
                termin.titel.takeIf { projekt?.kunde?.isNotBlank() == true && it.isNotBlank() },
                projekt?.nr?.takeIf { it.isNotBlank() },
                termin.kategorie.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (unter.isNotBlank()) {
                Text(unter, style = MaterialTheme.typography.bodyMedium, color = farben.onSurfaceVariant)
            }
            projekt?.adresse?.takeIf { it.isNotBlank() }?.let { adresse ->
                Zeile(Icons.Outlined.Place, adresse, stringResource(R.string.m_navigation, adresse)) { navigieren(kontext, adresse, projekt.geo) }
            }
            projekt?.telefon.orEmpty().forEach { tel ->
                val name = listOf(tel.name, tel.art).filter { it.isNotBlank() }.joinToString(", ")
                val text = if (name.isBlank()) tel.nummer else "${tel.nummer} · $name"
                Zeile(Icons.Outlined.Phone, text, stringResource(R.string.m_anrufen, tel.nummer)) { waehlen(kontext, tel.nummer) }
            }
            termin.beschreibung?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 6, overflow = TextOverflow.Ellipsis)
            }
            if (termin.kollegen.isNotEmpty()) {
                Text(
                    stringResource(R.string.m_kollegen, termin.kollegen.joinToString(", ")),
                    style = MaterialTheme.typography.bodySmall,
                    color = farben.onSurfaceVariant,
                )
            }
        }
    }
}

/** Antippbare Zeile mit Symbol, mindestens 48 dp hoch. */
@Composable
internal fun Zeile(symbol: androidx.compose.ui.graphics.vector.ImageVector, text: String, beschreibung: String, beiKlick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClickLabel = beschreibung, onClick = beiKlick),
    ) {
        Icon(symbol, null, tint = MaterialTheme.colorScheme.link, modifier = Modifier.size(20.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.link,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

/** „Bericht fehlt“ (gold), „Entwurf“ bzw. „BER-1330“ (gruen). */
@Composable
private fun BerichtChip(termin: TagTermin) {
    val fertig = termin.berichte.firstOrNull { it.status == MonteurBericht.STATUS_FERTIG }
    val dunkel = MaterialTheme.colorScheme.background.let { (it.red + it.green + it.blue) / 3f < 0.5f }
    val gruen = if (dunkel) Color(0xFF7FD3A8) else Color(0xFF1E7A4C)
    val (text, grund, schrift) = when {
        termin.berichte.isEmpty() -> Triple(stringResource(R.string.m_bericht_fehlt), MaterialTheme.colorScheme.gold, MaterialTheme.colorScheme.onSecondary)
        fertig != null -> Triple(fertig.hero_nr ?: "✓", gruen.copy(alpha = 0.18f), gruen)
        else -> Triple(stringResource(R.string.m_entwurf), gruen.copy(alpha = 0.12f), gruen)
    }
    Box(
        Modifier
            .background(grund, RoundedCornerShape(15.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = schrift)
    }
}

@Composable
private fun WartetKarte(anzahl: Int, beiSenden: () -> Unit) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 14.dp, end = 4.dp)) {
            Icon(Icons.Outlined.CloudUpload, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(
                stringResource(R.string.m_wartet, anzahl),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f).padding(start = 10.dp),
            )
            TextButton(onClick = beiSenden) { Text(stringResource(R.string.m_jetzt_senden)) }
        }
    }
}

@Composable
private fun MeldungKarte(meldung: KorbMeldung, beiOeffnen: (() -> Unit)?, beiOk: () -> Unit) {
    val farben = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (meldung.fertig) farben.surfaceContainer else farben.errorContainer,
        border = BorderStroke(1.dp, farben.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 14.dp, end = 4.dp, top = 10.dp)) {
            if (meldung.titel.isNotBlank()) Text(meldung.titel, style = MaterialTheme.typography.labelLarge)
            Text(meldung.text, style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = beiOk) { Text(stringResource(R.string.m_ok)) }
                if (beiOeffnen != null) TextButton(onClick = beiOeffnen) { Text(stringResource(R.string.m_meldung_oeffnen)) }
            }
        }
    }
}

fun projektTitel(p: TagProjekt): String = listOf(p.nr, p.kunde).filter { it.isNotBlank() }.joinToString(" · ")

/** „Fr 16.10.“ aus `YYYY-MM-DD`. */
fun tagText(datum: String): String = runCatching {
    val tag = LocalDate.parse(datum)
    val wt = tag.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.GERMANY).removeSuffix(".")
    "$wt ${tag.dayOfMonth}.${tag.monthValue}."
}.getOrDefault(datum)

/** „08:00 – 10:30“; `null` bei ganztaegig. Ortszeit mit oder ohne Offset. */
fun zeitText(termin: TagTermin): String? {
    if (termin.ganztag) return null
    val b = uhrzeit(termin.beginn) ?: return null
    val e = uhrzeit(termin.ende)
    return if (e == null) b else "$b – $e"
}

fun uhrzeit(iso: String): String? {
    val zeit = runCatching { OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).toLocalTime() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(iso).toLocalTime() }.getOrNull()
        ?: return null
    return "%02d:%02d".format(zeit.hour, zeit.minute)
}

/** Navigation zur Baustelle: mit Koordinaten genau, sonst ueber die Adresse. */
internal fun navigieren(kontext: Context, adresse: String, geo: Geo?) {
    val uri = if (geo != null) {
        Uri.parse("geo:${geo.lat},${geo.lng}?q=${geo.lat},${geo.lng}(${Uri.encode(adresse)})")
    } else {
        Uri.parse("geo:0,0?q=" + Uri.encode(adresse))
    }
    starten(kontext, Intent(Intent.ACTION_VIEW, uri))
}

/** Oeffnet den Waehlbildschirm, ruft nicht selbst an. */
internal fun waehlen(kontext: Context, nummer: String) {
    starten(kontext, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(nummer.filter { it.isDigit() || it == '+' }))))
}

private fun starten(kontext: Context, absicht: Intent) {
    try {
        kontext.startActivity(absicht)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(kontext, R.string.m_keine_app, Toast.LENGTH_SHORT).show()
    }
}
