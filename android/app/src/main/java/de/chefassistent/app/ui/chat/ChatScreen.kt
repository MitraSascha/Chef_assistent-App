package de.chefassistent.app.ui.chat

import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import de.chefassistent.app.ui.komponenten.EINZUG_MIT_SYMBOL
import de.chefassistent.app.ui.komponenten.FehlerSnackbar
import de.chefassistent.app.ui.komponenten.GruppenBlock
import de.chefassistent.app.ui.komponenten.GruppenKarte
import de.chefassistent.app.ui.komponenten.GruppenTitel
import de.chefassistent.app.ui.komponenten.LeerHinweis
import de.chefassistent.app.ui.komponenten.RundesSymbol
import android.Manifest
import android.app.Activity
import android.content.ContextWrapper
import android.view.WindowManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material.icons.filled.Share
import de.chefassistent.app.data.DateiLink
import de.chefassistent.app.ui.ablage.dateiSymbol
import de.chefassistent.app.ui.dateiTeilen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.chefassistent.app.R
import de.chefassistent.app.data.ERLAUBTE_STIMMEN
import de.chefassistent.app.data.ABSCHREIBEN_WERTE
import de.chefassistent.app.data.FotoAufbereitung
import de.chefassistent.app.data.Rueckmeldung
import de.chefassistent.app.data.VideoAufbereitung
import de.chefassistent.app.data.GespraechKurz
import de.chefassistent.app.data.GU_PRAEFIX
import de.chefassistent.app.data.Notizen
import de.chefassistent.app.data.NotizOrdner
import de.chefassistent.app.data.SuchTreffer
import de.chefassistent.app.data.HeroMappe
import de.chefassistent.app.data.HERO_STATUS_KOPIERT
import de.chefassistent.app.data.HERO_STATUS_SCHON_IN_HERO
import de.chefassistent.app.data.HERO_STATUS_VIDEO
import de.chefassistent.app.data.HERO_STATUS_ZU_GROSS
import androidx.compose.material.icons.outlined.Info
import de.chefassistent.app.data.Quelle
import de.chefassistent.app.ui.MarkdownText
import de.chefassistent.app.ui.antwort.AntwortInhalt
import kotlinx.coroutines.delay
import de.chefassistent.app.ui.antwort.kopieren
import de.chefassistent.app.ui.antwort.textZumKopieren
import androidx.compose.material.icons.filled.ContentCopy
import de.chefassistent.app.ui.dateiOeffnen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val LISTEN_DATUM = DateTimeFormatter.ofPattern("dd.MM. HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    beiAbmeldung: () -> Unit,
    beiTermine: () -> Unit,
    beiVerbrauch: () -> Unit,
    beiAuftraege: () -> Unit,
    beiAufgaben: () -> Unit,
    beiFreihand: () -> Unit,
    beiAblage: (String) -> Unit,
    beiPlantafel: () -> Unit,
    beiAngebote: () -> Unit,
    beiGeraete: () -> Unit,
    geteilt: Geteilt? = null,
    geteiltErledigt: () -> Unit = {},
) {
    val zustand by viewModel.zustand.collectAsStateWithLifecycle()
    val kontext = LocalContext.current
    val listenZustand = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    val schublade = rememberDrawerState(DrawerValue.Closed)
    val bereich = rememberCoroutineScope()
    var kontextDialogOffen by remember { mutableStateOf(false) }
    var heroInfoOffen by remember { mutableStateOf(false) }

    // Hintergrundauftraege nur abfragen, solange der Chat zu sehen ist.
    LifecycleResumeEffect(Unit) {
        viewModel.auftraegeBeobachten(true)
        onPauseOrDispose { viewModel.auftraegeBeobachten(false) }
    }

    // Waehrend der Aufnahme darf der Bildschirm nicht ausgehen: gesperrt
    // bekommt die App vom Mikrofon nur noch Stille.
    //
    // Bewusst das Fensterflag statt `View.keepScreenOn`: Am S25 blieb das
    // View-Flag nach einer Unterbrechung im Hintergrund am Fenster haengen
    // (2026-10-13 nachgestellt) - das Display waere bis zum Schliessen der App
    // angeblieben.
    val fenster = remember(kontext) { kontext.findActivity()?.window }
    DisposableEffect(zustand.nimmtAuf, fenster) {
        if (zustand.nimmtAuf) {
            fenster?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            fenster?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose { fenster?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    // Geht die App trotzdem in den Hintergrund (Ein/Aus-Taste, Anruf, App
    // gewechselt), wird die Aufnahme sichtbar beendet statt still weiterzulaufen.
    // Beim Drehen kommt ON_STOP ebenfalls - dann wird nur der Bildschirm neu
    // aufgebaut, die Aufnahme im ViewModel laeuft weiter.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (kontext.findActivity()?.isChangingConfigurations == true) return@LifecycleEventEffect
        viewModel.aufnahmeUnterbrochen()
        fenster?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    if (kontextDialogOffen) {
        LaunchedEffect(Unit) { viewModel.zieleLaden() }
        val schliessen = {
            viewModel.heroSucheBeenden()
            kontextDialogOffen = false
        }
        KontextDialog(
            aktuell = zustand.kontext,
            zuletzt = zustand.letzteKontexte,
            ordner = zustand.ziele?.ordner.orEmpty(),
            heroTreffer = zustand.heroTreffer,
            heroSucheLaeuft = zustand.heroSucheLaeuft,
            heroAnheften = zustand.heroAnheften,
            heroFehler = zustand.heroFehler,
            beiEingabe = viewModel::heroSuchen,
            beiHeroWahl = { treffer -> viewModel.heroAnheften(treffer) { schliessen() } },
            beiWahl = { text, ziel ->
                viewModel.kontextSetzen(text, ziel)
                schliessen()
            },
            beiAbbruch = schliessen,
        )
    }

    if (heroInfoOffen) {
        val projekt = zustand.kontextHero
        if (projekt == null) {
            heroInfoOffen = false
        } else {
            LaunchedEffect(projekt) { viewModel.heroStandLaden() }
            HeroInfoDialog(
                projekt = projekt,
                stand = zustand.heroStand?.takeIf { it.projekt == projekt },
                laedt = zustand.heroStandLaeuft,
                beiSchliessen = { heroInfoOffen = false },
            )
        }
    }

    zustand.medium?.let { medium ->
        LaunchedEffect(Unit) { viewModel.zieleLaden() }
        MedienDialog(
            medium = medium,
            notizen = zustand.medienNotizen,
            ziele = zustand.ziele,
            zieleLaden = zustand.zieleLaden,
            beiText = viewModel::medienTextGeaendert,
            beiNotiz = viewModel::medienNotizWaehlen,
            beiAbschreiben = viewModel::medienAbschreibenWaehlen,
            beiFragen = viewModel::medienFragenSetzen,
            beiAblegen = viewModel::medienAblegen,
            beiVerwerfen = viewModel::medienVerwerfen,
        )
    }

    // Ueberlebt, dass Android die App waehrend der Kamera beendet - sonst
    // kaeme die Aufnahme zurueck, ohne dass jemand weiss, wo sie liegt.
    var aufnahmePfad by rememberSaveable { mutableStateOf<String?>(null) }
    var aufnahmeFragen by rememberSaveable { mutableStateOf(false) }
    val aufnahmeErhalten: (Boolean, Boolean) -> Unit = { ok, istVideo ->
        val datei = aufnahmePfad?.let(::File)
        aufnahmePfad = null
        if (datei != null) {
            if (ok && datei.length() > 0) {
                viewModel.medienAufgenommen(datei, istVideo, aufnahmeFragen)
            } else {
                datei.delete()
            }
        }
    }
    val fotoKamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) {
        aufnahmeErhalten(it, false)
    }
    val videoKamera = rememberLauncherForActivityResult(VideoMitGrenze()) {
        aufnahmeErhalten(it, true)
    }
    // Die Android-Fotoauswahl braucht keine Speicher-Berechtigung.
    val galerie = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::ausGalerie)
    }
    val beiGalerie: () -> Unit = {
        galerie.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
    }
    // Systemdateiauswahl: sieht Downloads, Mail-Anhaenge, Drive - ohne Speicher-Berechtigung.
    val dokumentAuswahl = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::dokumentGewaehlt)
    }
    val beiDokument: () -> Unit = {
        try {
            dokumentAuswahl.launch(DOKUMENT_TYPEN)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(kontext, R.string.dokument_unlesbar, Toast.LENGTH_LONG).show()
        }
    }

    // Aus "Teilen" einer anderen App: einmal uebernehmen, dann abhaken.
    LaunchedEffect(geteilt) {
        when (val inhalt = geteilt) {
            is Geteilt.Dokument -> viewModel.dokumentGewaehlt(inhalt.uri)
            is Geteilt.Text -> viewModel.geteilterText(inhalt.text)
            is Geteilt.Senden -> viewModel.vonAussenSenden(inhalt.text)
            null -> return@LaunchedEffect
        }
        geteiltErledigt()
    }
    val beiKamera: (Boolean, Boolean) -> Unit = { istVideo, fragen ->
        aufnahmeFragen = fragen
        val ordner = File(kontext.cacheDir, if (istVideo) "videos" else "fotos").apply { mkdirs() }
        val datei = File(ordner, if (istVideo) "video_${System.currentTimeMillis()}.mp4" else "foto_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(kontext, "${kontext.packageName}.dateien", datei)
        aufnahmePfad = datei.path
        try {
            if (istVideo) videoKamera.launch(uri) else fotoKamera.launch(uri)
        } catch (_: ActivityNotFoundException) {
            aufnahmePfad = null
            Toast.makeText(kontext, R.string.foto_keine_kamera, Toast.LENGTH_LONG).show()
        }
    }

    val mikrofonAnfrage = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { erlaubt ->
        if (erlaubt) {
            viewModel.aufnahmeUmschalten()
        } else {
            Toast.makeText(kontext, R.string.sprache_keine_berechtigung, Toast.LENGTH_LONG).show()
        }
    }

    // Ohne das verlaesst die Zurueck-Taste bei offener Schublade die ganze App,
    // statt nur die Schublade zu schliessen.
    BackHandler(enabled = schublade.isOpen) {
        bereich.launch { schublade.close() }
    }

    LaunchedEffect(zustand.abgemeldet) {
        if (zustand.abgemeldet) beiAbmeldung()
    }

    // Neue Zeilen sollen sichtbar sein, ohne dass der Nutzer scrollen muss.
    LaunchedEffect(zustand.eintraege.size) {
        if (zustand.eintraege.isNotEmpty()) {
            listenZustand.animateScrollToItem(zustand.eintraege.lastIndex)
        }
    }
    LaunchedEffect(zustand.laeuft) {
        if (zustand.laeuft && zustand.eintraege.isNotEmpty()) {
            listenZustand.animateScrollToItem(zustand.eintraege.size)
        }
    }

    // Erst abholen, dann zeigen: kaeme dieselbe Meldung ein zweites Mal, waehrend
    // die erste noch steht, aenderte sich der Zustand sonst nicht - und sie ginge
    // verloren. Die Snackbar reiht mehrere Meldungen selbst hintereinander.
    LaunchedEffect(zustand.fehler) {
        zustand.fehler?.let {
            viewModel.fehlerVerwerfen()
            bereich.launch { snackbar.showSnackbar(it) }
        }
    }

    ModalNavigationDrawer(
        drawerState = schublade,
        drawerContent = {
            GespraechSchublade(
                gespraeche = zustand.gespraeche,
                aktuellesGespraech = zustand.gespraechId,
                laedt = zustand.gespraecheLaden,
                serverStimme = zustand.serverStimme,
                stimme = zustand.stimme,
                diktatPruefen = zustand.diktatPruefen,
                wartendeNotizen = zustand.wartendeNotizen,
                wartendeMedien = zustand.wartendeMedien,
                beiNotizenSenden = viewModel::wartendeNotizenSenden,
                beiStimmeWaehlen = viewModel::stimmeWaehlen,
                beiOpenAiStimme = viewModel::openAiStimmeWaehlen,
                beiDiktatPruefen = viewModel::diktatPruefenSetzen,
                beiNeu = {
                    viewModel.neuesGespraech()
                    bereich.launch { schublade.close() }
                },
                beiOeffnen = { id ->
                    viewModel.gespraechOeffnen(id)
                    bereich.launch { schublade.close() }
                },
                beiLoeschen = viewModel::gespraechLoeschen,
                beiTermine = {
                    bereich.launch { schublade.close() }
                    beiTermine()
                },
                beiVerbrauch = {
                    bereich.launch { schublade.close() }
                    beiVerbrauch()
                },
                auftraegeOffen = zustand.auftraegeOffen,
                beiAuftraege = {
                    bereich.launch { schublade.close() }
                    beiAuftraege()
                },
                aufgabenOffen = zustand.aufgabenOffen,
                beiAufgaben = {
                    bereich.launch { schublade.close() }
                    beiAufgaben()
                },
                beiAblage = {
                    bereich.launch { schublade.close() }
                    beiAblage("")
                },
                beiPlantafel = {
                    bereich.launch { schublade.close() }
                    beiPlantafel()
                },
                beiAngebote = if (zustand.angeboteEingerichtet) {
                    {
                        bereich.launch { schublade.close() }
                        beiAngebote()
                    }
                } else null,
                beiGeraete = {
                    bereich.launch { schublade.close() }
                    beiGeraete()
                },
            )
        },
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                    title = {
                        Text(
                            stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            viewModel.notizenZaehlen()
                            bereich.launch { schublade.open() }
                        }) {
                            Icon(Icons.Default.Menu, stringResource(R.string.chat_gespraeche))
                        }
                    },
                    actions = {
                        // Nur wenn es etwas zu sehen gibt - sonst reicht die Schublade.
                        if (zustand.auftraegeOffen > 0) {
                            IconButton(onClick = beiAuftraege) {
                                BadgedBox(badge = { Badge { Text(zustand.auftraegeOffen.toString()) } }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Assignment,
                                        pluralStringResource(
                                            R.plurals.auftraege_zaehler,
                                            zustand.auftraegeOffen,
                                            zustand.auftraegeOffen,
                                        ),
                                    )
                                }
                            }
                        }
                        IconButton(onClick = beiFreihand) {
                            Icon(
                                Icons.Default.RecordVoiceOver,
                                stringResource(R.string.freihand_titel),
                            )
                        }
                        IconButton(onClick = viewModel::neuesGespraech) {
                            Icon(Icons.Default.Add, stringResource(R.string.chat_neues_gespraech))
                        }
                        // Abmelden ist selten und wirft das Token weg - deshalb
                        // nicht als Symbol neben "Neu", sondern im Menue wie bei Samsung.
                        Box {
                            var menue by remember { mutableStateOf(false) }
                            IconButton(onClick = { menue = true }) {
                                Icon(Icons.Default.MoreVert, stringResource(R.string.chat_mehr))
                            }
                            DropdownMenu(
                                expanded = menue,
                                onDismissRequest = { menue = false },
                                shape = RoundedCornerShape(20.dp),
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.chat_abmelden)) },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Logout, null) },
                                    onClick = {
                                        menue = false
                                        viewModel.abmelden()
                                    },
                                )
                            }
                        }
                    },
                )
            },
            snackbarHost = { FehlerSnackbar(snackbar) },
            bottomBar = {
                Column {
                    Kontextleiste(
                        kontext = zustand.kontext,
                        hero = zustand.kontextHero,
                        beiOeffnen = { kontextDialogOffen = true },
                        beiInfo = { heroInfoOffen = true },
                        beiLoesen = { viewModel.kontextSetzen(null) },
                    )
                    Eingabeleiste(
                        entwurf = zustand.entwurf,
                        kannSenden = zustand.kannSenden,
                        kannAufnehmen = zustand.kannAufnehmen,
                        kannFotografieren = zustand.kannFotografieren,
                        nimmtAuf = zustand.nimmtAuf,
                        laeuft = zustand.laeuft,
                        beiAenderung = viewModel::entwurfGeaendert,
                        beiSenden = viewModel::senden,
                        beiKamera = beiKamera,
                        beiGalerie = beiGalerie,
                        beiDokument = beiDokument,
                        beiMikrofon = {
                            val erlaubt = ContextCompat.checkSelfPermission(
                                kontext,
                                Manifest.permission.RECORD_AUDIO,
                            ) == PackageManager.PERMISSION_GRANTED

                            if (erlaubt) {
                                viewModel.aufnahmeUmschalten()
                            } else {
                                mikrofonAnfrage.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                    )
                }
            },
        ) { innen ->
            Column(modifier = Modifier.fillMaxSize().padding(innen)) {
                // Mit Verlauf zeigt die Blase "Assistent arbeitet" den Stand, sonst der Balken.
                if (zustand.laeuft && zustand.eintraege.isEmpty()) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                zustand.hochladen?.let { prozent ->
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Text(
                            text = stringResource(R.string.video_laedt_hoch, prozent),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LinearProgressIndicator(
                            progress = { prozent / 100f },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        )
                    }
                }

                if (zustand.rueckmeldungen.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        zustand.rueckmeldungen.forEach { rueckmeldung ->
                            key(rueckmeldung.id) {
                                RueckmeldungKarte(
                                    rueckmeldung = rueckmeldung,
                                    beiUebernehmen = { viewModel.rueckmeldungUebernehmen(rueckmeldung.id) },
                                    beiErledigt = { viewModel.rueckmeldungErledigt(rueckmeldung.id) },
                                )
                            }
                        }
                    }
                }

                if (zustand.eintraege.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        RundesSymbol(
                            Icons.Default.AutoAwesome,
                            farbe = MaterialTheme.colorScheme.primaryContainer,
                            symbolFarbe = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(64.dp),
                        )
                        Text(
                            text = stringResource(R.string.chat_leer),
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                } else {
                    val beiDatei: (String) -> Unit = { pfad ->
                        viewModel.dateiOeffnen(pfad) { datei -> dateiOeffnen(kontext, datei) }
                    }
                    val teilenTitel = stringResource(R.string.link_teilen)
                    val beiTeilen: (String) -> Unit = { pfad ->
                        viewModel.dateiOeffnen(pfad) { datei -> dateiTeilen(kontext, datei, teilenTitel) }
                    }
                    LazyColumn(
                        state = listenZustand,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(zustand.eintraege, key = { it.schluessel }) { eintrag ->
                            when (eintrag) {
                                is NutzerEintrag -> NutzerBlase(
                                    eintrag = eintrag,
                                    laden = viewModel::vorschauLaden,
                                    beiDatei = beiDatei,
                                    beiOrdner = beiAblage,
                                )

                                is AssistentEintrag -> AssistentBlase(
                                    eintrag = eintrag,
                                    liestVor = zustand.liestVor == eintrag.schluessel,
                                    beiVorlesen = { viewModel.vorlesenUmschalten(eintrag) },
                                    beiDatei = beiDatei,
                                    beiOrdner = beiAblage,
                                    beiTeilen = beiTeilen,
                                )

                                is HinweisEintrag -> HinweisZeile(eintrag, beiDatei, beiAblage)

                                is FrageEintrag -> FrageKarte(
                                    eintrag = eintrag,
                                    beiJa = { viewModel.entscheiden(eintrag.id, true) },
                                    beiNein = { viewModel.entscheiden(eintrag.id, false) },
                                )
                            }
                        }
                        if (zustand.laeuft) {
                            // Eigener Schluessel je Anfrage, sonst erbte die Blase die Startzeit der vorigen.
                            item(key = "arbeitet-${zustand.eintraege.size}") { ArbeitetBlase() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GespraechSchublade(
    gespraeche: List<GespraechKurz>,
    aktuellesGespraech: String?,
    laedt: Boolean,
    serverStimme: Boolean,
    stimme: String?,
    diktatPruefen: Boolean,
    wartendeNotizen: Int,
    wartendeMedien: Int,
    beiNotizenSenden: () -> Unit,
    beiStimmeWaehlen: (Boolean) -> Unit,
    beiOpenAiStimme: (String?) -> Unit,
    beiDiktatPruefen: (Boolean) -> Unit,
    beiNeu: () -> Unit,
    beiOeffnen: (String) -> Unit,
    beiLoeschen: (String) -> Unit,
    beiTermine: () -> Unit,
    beiVerbrauch: () -> Unit,
    auftraegeOffen: Int,
    beiAuftraege: () -> Unit,
    aufgabenOffen: Int,
    beiAufgaben: () -> Unit,
    beiAblage: () -> Unit,
    beiPlantafel: () -> Unit,
    /** `null` = Angebote auf diesem Server nicht eingerichtet, Eintrag fehlt. */
    beiAngebote: (() -> Unit)?,
    beiGeraete: () -> Unit,
) {
    var zuLoeschen by remember { mutableStateOf<GespraechKurz?>(null) }

    zuLoeschen?.let { gespraech ->
        val titel = gespraech.titel?.takeIf { it.isNotBlank() }
            ?: stringResource(R.string.chat_ohne_titel)
        AlertDialog(
            onDismissRequest = { zuLoeschen = null },
            title = { Text(stringResource(R.string.loeschen_titel)) },
            text = { Text(stringResource(R.string.loeschen_frage, titel)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        beiLoeschen(gespraech.id)
                        zuLoeschen = null
                    }
                ) {
                    Text(
                        text = stringResource(R.string.loeschen_ja),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { zuLoeschen = null }) {
                    Text(stringResource(R.string.loeschen_abbrechen))
                }
            },
        )
    }

    // Aufbau wie die Samsung-Einstellungen: Gruppen als runde Karten, farbige
    // Kreise vor den Eintraegen. Alles in einer Liste, damit die Schublade auch
    // mit vielen Gespraechen komplett scrollt.
    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
    ) {
        val ziele = listOfNotNull(
            SchubladenZiel(Icons.Default.Checklist, FARBE_AUFGABEN, stringResource(R.string.aufgaben_titel), aufgabenOffen, beiAufgaben),
            SchubladenZiel(Icons.Default.CalendarMonth, FARBE_TERMINE, stringResource(R.string.kalender_titel), 0, beiTermine),
            SchubladenZiel(Icons.Default.Engineering, FARBE_PLANTAFEL, stringResource(R.string.schublade_plantafel), 0, beiPlantafel),
            beiAngebote?.let { SchubladenZiel(Icons.Default.RequestQuote, FARBE_ANGEBOTE, stringResource(R.string.angebote_titel), 0, it) },
            SchubladenZiel(Icons.Default.Folder, FARBE_ABLAGE, stringResource(R.string.ablage_titel), 0, beiAblage),
            SchubladenZiel(Icons.AutoMirrored.Filled.Assignment, FARBE_AUFTRAEGE, stringResource(R.string.auftraege_titel), auftraegeOffen, beiAuftraege),
            SchubladenZiel(Icons.Default.QueryStats, FARBE_VERBRAUCH, stringResource(R.string.verbrauch_titel), 0, beiVerbrauch),
        )
        // Wartende Aufnahmen stehen ganz oben: wer im Keller aufgenommen hat,
        // will beim naechsten Oeffnen sofort sehen, dass noch etwas liegt.
        val wartend = buildList {
            if (wartendeNotizen > 0) {
                add(pluralStringResource(R.plurals.ausgang_wartet, wartendeNotizen, wartendeNotizen) to Icons.Default.CloudUpload)
            }
            if (wartendeMedien > 0) {
                add(pluralStringResource(R.plurals.medienausgang_wartet, wartendeMedien, wartendeMedien) to Icons.Default.PhotoCamera)
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 24.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "kopf") {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(start = 12.dp, top = 24.dp, bottom = 20.dp),
                )
            }

            item(key = "neu") {
                GruppenKarte {
                    SchubladenZeile(
                        symbol = Icons.Default.Add,
                        farbe = MaterialTheme.colorScheme.primary,
                        symbolFarbe = MaterialTheme.colorScheme.onPrimary,
                        titel = stringResource(R.string.chat_neues_gespraech),
                        beiKlick = beiNeu,
                    )
                }
            }

            if (wartend.isNotEmpty()) {
                item(key = "wartend_luft") { Spacer(Modifier.height(12.dp)) }
                itemsIndexed(wartend, key = { i, _ -> "wartend$i" }) { index, (text, symbol) ->
                    GruppenBlock(
                        index = index,
                        anzahl = wartend.size,
                        einzug = EINZUG_MIT_SYMBOL,
                        farbe = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        // Derselbe Arbeiter schickt Sprachnotizen, Fotos und Videos.
                        SchubladenZeile(
                            symbol = symbol,
                            farbe = MaterialTheme.colorScheme.error,
                            symbolFarbe = MaterialTheme.colorScheme.onError,
                            titel = text,
                            beiKlick = beiNotizenSenden,
                        )
                    }
                }
            }

            item(key = "ziele_luft") { Spacer(Modifier.height(12.dp)) }
            itemsIndexed(ziele, key = { i, _ -> "ziel$i" }) { index, ziel ->
                GruppenBlock(index = index, anzahl = ziele.size, einzug = EINZUG_MIT_SYMBOL) {
                    SchubladenZeile(
                        symbol = ziel.symbol,
                        farbe = ziel.farbe,
                        titel = ziel.titel,
                        zaehler = ziel.zaehler,
                        beiKlick = ziel.beiKlick,
                    )
                }
            }

            item(key = "einstellungen") {
                Column {
                    GruppenTitel(stringResource(R.string.schublade_einstellungen))
                    GruppenKarte {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { beiDiktatPruefen(!diktatPruefen) }
                                .padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.diktat_titel),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    text = stringResource(
                                        if (diktatPruefen) R.string.diktat_an else R.string.diktat_aus
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(checked = diktatPruefen, onCheckedChange = beiDiktatPruefen)
                        }
                    }

                    // Seit v1.10.0: Sitzungen je Geraet, z. B. ein verlorenes Handy abmelden.
                    Spacer(Modifier.height(12.dp))
                    GruppenKarte {
                        SchubladenZeile(
                            symbol = Icons.Default.Devices,
                            farbe = FARBE_GERAETE,
                            titel = stringResource(R.string.geraete_titel),
                            beiKlick = beiGeraete,
                        )
                    }

                    // Beide Moeglichkeiten stehen sichtbar untereinander. Ein Schalter zeigt
                    // immer nur den aktuellen Zustand - hier soll erkennbar sein, wofuer man
                    // sich entscheidet und was es kostet.
                    GruppenTitel(stringResource(R.string.stimme_titel))
                    GruppenKarte {
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            StimmWahl(
                                gewaehlt = serverStimme,
                                titel = stringResource(R.string.stimme_openai),
                                erklaerung = stringResource(R.string.stimme_openai_erklaerung),
                                beiWahl = { beiStimmeWaehlen(true) },
                            )
                            // Die konkrete Stimme ist nur bei OpenAI waehlbar - die Geraetestimme
                            // richtet sich nach den Systemeinstellungen des Handys.
                            if (serverStimme) {
                                StimmenAuswahl(stimme = stimme, beiWahl = beiOpenAiStimme)
                            }
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.padding(start = 60.dp, end = 20.dp, top = 4.dp, bottom = 4.dp),
                            )
                            StimmWahl(
                                gewaehlt = !serverStimme,
                                titel = stringResource(R.string.stimme_geraet),
                                erklaerung = stringResource(R.string.stimme_geraet_erklaerung),
                                beiWahl = { beiStimmeWaehlen(false) },
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GruppenTitel(stringResource(R.string.chat_gespraeche), modifier = Modifier.weight(1f))
                        if (laedt) {
                            CircularProgressIndicator(Modifier.padding(end = 20.dp, top = 12.dp).size(16.dp), strokeWidth = 2.dp)
                        }
                    }
                    if (gespraeche.isEmpty() && !laedt) {
                        LeerHinweis(stringResource(R.string.chat_keine_gespraeche))
                    }
                }
            }

            itemsIndexed(gespraeche, key = { _, g -> g.id }) { index, gespraech ->
                val aktiv = gespraech.id == aktuellesGespraech
                GruppenBlock(
                    index = index,
                    anzahl = gespraeche.size,
                    farbe = if (aktiv) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { beiOeffnen(gespraech.id) }
                            .padding(start = 20.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = gespraech.titel?.takeIf { it.isNotBlank() }
                                    ?: stringResource(R.string.chat_ohne_titel),
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            geaendertText(gespraech.geaendert)?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        // Seit v1.17.0: der Assistent arbeitet dort gerade (API.md 1b).
                        if (gespraech.laeuft) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(horizontal = 8.dp).size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                        // Loeschen geht an den Server und ist nicht umkehrbar.
                        // Ein Fehlgriff in einer Liste ist zu leicht - deshalb
                        // immer erst nachfragen.
                        IconButton(onClick = { zuLoeschen = gespraech }) {
                            Icon(
                                Icons.Default.Delete,
                                stringResource(R.string.chat_gespraech_loeschen),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Feste Farben der Kreise in der Schublade, wie bei Samsung je Bereich eine. */
private val FARBE_AUFGABEN = Color(0xFFE8833A)
private val FARBE_TERMINE = Color(0xFF3E7BFA)
private val FARBE_PLANTAFEL = Color(0xFFB7873D)
private val FARBE_ABLAGE = Color(0xFF2E9B8F)
private val FARBE_ANGEBOTE = Color(0xFF3A9D5D)
private val FARBE_AUFTRAEGE = Color(0xFF7C5CD6)
private val FARBE_VERBRAUCH = Color(0xFF6B7785)
private val FARBE_GERAETE = Color(0xFF4A8FC9)

private class SchubladenZiel(
    val symbol: ImageVector,
    val farbe: Color,
    val titel: String,
    val zaehler: Int,
    val beiKlick: () -> Unit,
)

@Composable
private fun SchubladenZeile(
    symbol: ImageVector,
    farbe: Color,
    titel: String,
    beiKlick: () -> Unit,
    symbolFarbe: Color = Color.White,
    zaehler: Int = 0,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = beiKlick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RundesSymbol(symbol, farbe = farbe, symbolFarbe = symbolFarbe)
        Text(
            text = titel,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f).padding(start = 18.dp),
        )
        if (zaehler > 0) {
            Badge(containerColor = MaterialTheme.colorScheme.primary) {
                Text(zaehler.toString(), modifier = Modifier.padding(horizontal = 2.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StimmenAuswahl(stimme: String?, beiWahl: (String?) -> Unit) {
    var offen by remember { mutableStateOf(false) }
    val servervorgabe = stringResource(R.string.stimme_servervorgabe)

    Box(modifier = Modifier.padding(start = 68.dp, end = 16.dp, bottom = 8.dp)) {
        AssistChip(
            onClick = { offen = true },
            label = { Text(stimme ?: servervorgabe) },
            trailingIcon = {
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(AssistChipDefaults.IconSize),
                )
            },
        )

        DropdownMenu(expanded = offen, onDismissRequest = { offen = false }) {
            DropdownMenuItem(
                text = { Text(servervorgabe) },
                onClick = { beiWahl(null); offen = false },
                trailingIcon = {
                    if (stimme == null) Icon(Icons.Default.Check, contentDescription = null)
                },
            )
            ERLAUBTE_STIMMEN.forEach { name ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = { beiWahl(name); offen = false },
                    trailingIcon = {
                        if (stimme == name) Icon(Icons.Default.Check, contentDescription = null)
                    },
                )
            }
        }
    }
}

/**
 * Zeigt den angehefteten Projektkontext direkt ueber dem Eingabefeld.
 *
 * Bewusst dauerhaft sichtbar und nicht in einem Menue versteckt: ein Kontext,
 * den man vergisst, verfaelscht spaetere Fragen. Wer ihn sieht, loest ihn.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Kontextleiste(
    kontext: String?,
    /** HERO-Projektnummer, wenn eine HERO-Mappe angeheftet ist. */
    hero: String?,
    beiOeffnen: () -> Unit,
    beiInfo: () -> Unit,
    beiLoesen: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (kontext == null) {
            AssistChip(
                onClick = beiOeffnen,
                shape = CircleShape,
                label = { Text(stringResource(R.string.kontext_setzen)) },
                leadingIcon = {
                    Icon(
                        Icons.Default.PushPin,
                        contentDescription = null,
                        modifier = Modifier.size(AssistChipDefaults.IconSize),
                    )
                },
            )
        } else {
            InputChip(
                selected = true,
                onClick = beiOeffnen,
                shape = CircleShape,
                modifier = Modifier.weight(1f, fill = false),
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (hero != null) {
                            HeroAbzeichen()
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            stringResource(R.string.kontext_angeheftet, kontext),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.PushPin,
                        contentDescription = null,
                        modifier = Modifier.size(AssistChipDefaults.IconSize),
                    )
                },
                trailingIcon = {
                    IconButton(onClick = beiLoesen, modifier = Modifier.size(24.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.kontext_loesen),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
            )
            // Wohin geht es? Bei einer HERO-Mappe ist das nicht offensichtlich.
            if (hero != null) {
                IconButton(onClick = beiInfo) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = stringResource(R.string.hero_info_knopf),
                    )
                }
            }
        }
    }
}

/** Kleines Abzeichen "HERO" vor HERO-Projekten und HERO-Mappen (Auftrag 1.19). */
@Composable
private fun HeroAbzeichen() {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.tertiary,
        contentColor = MaterialTheme.colorScheme.onTertiary,
    ) {
        Text(
            text = stringResource(R.string.hero_abzeichen),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}

/** Wohin die Anhaenge gehen, dazu der Stand aus `GET /v1/hero-mappe` (optional laut Auftrag). */
@Composable
private fun HeroInfoDialog(
    projekt: String,
    stand: HeroMappe?,
    laedt: Boolean,
    beiSchliessen: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = beiSchliessen,
        title = { Text(stringResource(R.string.hero_info_text, projekt)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.hero_info_details))
                when {
                    stand != null -> Text(
                        text = heroStandText(stand),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    laedt -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(onClick = beiSchliessen) { Text(stringResource(R.string.hero_info_ok)) }
        },
    )
}

/** "In HERO: 12 Dateien · 2 Videos nur auf dem Server" - unbekannte Status zaehlen nicht mit. */
@Composable
private fun heroStandText(stand: HeroMappe): String {
    val anzahl = stand.dateien.groupingBy { it.status }.eachCount()
    val inHero = (anzahl[HERO_STATUS_KOPIERT] ?: 0) + (anzahl[HERO_STATUS_SCHON_IN_HERO] ?: 0)
    val videos = anzahl[HERO_STATUS_VIDEO] ?: 0
    val zuGross = anzahl[HERO_STATUS_ZU_GROSS] ?: 0
    if (inHero + videos + zuGross == 0) return stringResource(R.string.hero_stand_leer)
    return buildList {
        add(pluralStringResource(R.plurals.hero_stand_in_hero, inHero, inHero))
        if (videos > 0) add(pluralStringResource(R.plurals.hero_stand_videos, videos, videos))
        if (zuGross > 0) add(pluralStringResource(R.plurals.hero_stand_zu_gross, zuGross, zuGross))
    }.joinToString(" · ")
}

/**
 * Kontext eintippen, ein HERO-Projekt suchen (seit v1.19.0) oder aus den
 * eigenen Ordnern und den zuletzt benutzten waehlen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KontextDialog(
    aktuell: String?,
    zuletzt: List<String>,
    /** Eigene Ordner, zuletzt geaendert zuerst. */
    ordner: List<NotizOrdner>,
    heroTreffer: List<SuchTreffer>,
    heroSucheLaeuft: Boolean,
    heroAnheften: Boolean,
    heroFehler: String?,
    beiEingabe: (String) -> Unit,
    beiHeroWahl: (SuchTreffer) -> Unit,
    beiWahl: (String?, String?) -> Unit,
    beiAbbruch: () -> Unit,
) {
    var eingabe by remember { mutableStateOf(aktuell.orEmpty()) }
    // Pfad des gewaehlten Ordners. Tippt man danach etwas anderes, passt er nicht mehr.
    var ordnerPfad by remember { mutableStateOf<String?>(null) }
    val passenderPfad = ordnerPfad?.takeIf { pfad -> ordner.any { it.pfad == pfad && it.name == eingabe.trim() } }
    // Die Eingabe ist zugleich die Suche: HERO-Projekte beim Server, eigene Ordner hier.
    LaunchedEffect(eingabe) { beiEingabe(eingabe) }
    val filter = eingabe.trim()
    val passendeOrdner = ordner.filter {
        filter.isEmpty() || it.pfad == ordnerPfad || it.name.contains(filter, ignoreCase = true)
    }

    AlertDialog(
        onDismissRequest = beiAbbruch,
        title = { Text(stringResource(R.string.kontext_titel)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = eingabe,
                    onValueChange = { eingabe = it },
                    label = { Text(stringResource(R.string.kontext_feld)) },
                    supportingText = { Text(stringResource(R.string.kontext_hinweis)) },
                    singleLine = true,
                    enabled = !heroAnheften,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (heroSucheLaeuft || heroAnheften) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                heroFehler?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (heroTreffer.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.kontext_hero_projekte),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column {
                        heroTreffer.forEach { treffer ->
                            HeroTrefferZeile(
                                treffer = treffer,
                                frei = !heroAnheften,
                                beiKlick = { beiHeroWahl(treffer) },
                            )
                        }
                    }
                }

                if (passendeOrdner.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.kontext_ordner),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        passendeOrdner.take(MAX_ORDNER_VORSCHLAEGE).forEach { o ->
                            val name = o.name
                            val pfad = o.pfad
                            AssistChip(
                                onClick = { eingabe = name; ordnerPfad = pfad },
                                label = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (o.hero_projekt != null) {
                                            HeroAbzeichen()
                                            Spacer(Modifier.width(6.dp))
                                        }
                                        Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Folder,
                                        contentDescription = null,
                                        modifier = Modifier.size(AssistChipDefaults.IconSize),
                                    )
                                },
                            )
                        }
                    }
                    if (passenderPfad != null) {
                        val heroNr = ordner.firstOrNull { it.pfad == passenderPfad }?.hero_projekt
                        Text(
                            text = if (heroNr != null) {
                                stringResource(R.string.hero_info_text, heroNr)
                            } else {
                                stringResource(R.string.kontext_ordner_ziel)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (zuletzt.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.kontext_zuletzt),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        zuletzt.forEach { frueher ->
                            AssistChip(
                                onClick = { eingabe = frueher },
                                label = { Text(frueher, maxLines = 1) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { beiWahl(eingabe, passenderPfad) },
                enabled = eingabe.isNotBlank() && !heroAnheften,
            ) {
                Text(stringResource(R.string.kontext_uebernehmen))
            }
        },
        dismissButton = {
            TextButton(onClick = if (aktuell == null) beiAbbruch else ({ beiWahl(null, null) })) {
                Text(
                    stringResource(
                        if (aktuell == null) R.string.kontext_abbrechen else R.string.kontext_loesen
                    )
                )
            }
        },
    )
}

@Composable
private fun StimmWahl(
    gewaehlt: Boolean,
    titel: String,
    erklaerung: String,
    beiWahl: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = gewaehlt, role = Role.RadioButton, onClick = beiWahl)
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = gewaehlt, onClick = null)
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(text = titel, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = erklaerung,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NutzerBlase(
    eintrag: NutzerEintrag,
    laden: suspend (String) -> File?,
    beiDatei: (String) -> Unit,
    beiOrdner: (String) -> Unit,
) {
    // Blase nur so breit wie der Text, hoechstens 85 % - wie in Google Messages.
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Spacer(Modifier.weight(0.15f))
        Card(
            shape = RoundedCornerShape(22.dp, 6.dp, 22.dp, 22.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
            modifier = Modifier.weight(0.85f, fill = false),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Fotos, die der Assistent zu dieser Frage ansehen sollte (v1.5.0).
                val (bilder, andere) = eintrag.dateien.partition(::istBild)
                bilder.forEach { pfad -> Vorschaubild(pfad, laden, beiDatei, beiOrdner) }
                if (andere.isNotEmpty()) DateiChips(andere, beiDatei, beiOrdner)
                Text(
                    text = eintrag.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                if (eintrag.wartet != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CloudUpload,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        )
                        Text(
                            text = stringResource(R.string.ausgang_wartet_marke),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Was der Versand im Hintergrund zurueckgemeldet hat. Bleibt stehen, bis der
 * Nutzer es uebernimmt oder verwirft - ein Diktat aus dem Funkloch darf nicht
 * still in einer Snackbar verschwinden.
 */
@Composable
private fun RueckmeldungKarte(
    rueckmeldung: Rueckmeldung,
    beiUebernehmen: () -> Unit,
    beiErledigt: () -> Unit,
) {
    val zeit = remember(rueckmeldung.zeit) {
        java.text.SimpleDateFormat("dd.MM. HH:mm", java.util.Locale.GERMANY).format(java.util.Date(rueckmeldung.zeit))
    }
    val zumUebernehmen = rueckmeldung.art != Rueckmeldung.ART_HINWEIS
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (rueckmeldung.art == Rueckmeldung.ART_NICHT_GESENDET) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Text(
                text = when (rueckmeldung.art) {
                    Rueckmeldung.ART_DIKTAT -> stringResource(R.string.rueckmeldung_diktat, zeit)
                    Rueckmeldung.ART_NICHT_GESENDET -> stringResource(R.string.rueckmeldung_nicht_gesendet, zeit)
                    else -> stringResource(R.string.rueckmeldung_hinweis, zeit)
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = rueckmeldung.text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp, end = 8.dp),
            )
            rueckmeldung.detail?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp, end = 8.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                if (zumUebernehmen) {
                    TextButton(onClick = beiErledigt) { Text(stringResource(R.string.rueckmeldung_verwerfen)) }
                    TextButton(onClick = beiUebernehmen) { Text(stringResource(R.string.rueckmeldung_uebernehmen)) }
                } else {
                    TextButton(onClick = beiErledigt) { Text(stringResource(R.string.rueckmeldung_ok)) }
                }
            }
        }
    }
}

/** Die Bildformate, die der Server laut Vertrag in `bilder` annimmt. */
private fun istBild(pfad: String) =
    pfad.substringAfterLast('.').lowercase() in setOf("jpg", "jpeg", "png", "webp")

/**
 * Vorschaubild aus der Ablage. Tippen oeffnet das Foto, langer Druck zeigt es
 * im Ordner - wie bei den Datei-Chips. Laesst es sich nicht laden, steht dort
 * eben der Chip.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Vorschaubild(
    pfad: String,
    laden: suspend (String) -> File?,
    beiDatei: (String) -> Unit,
    beiOrdner: (String) -> Unit,
) {
    // Unit = laedt noch, null = gescheitert
    val bild by produceState<Any?>(Unit, pfad) {
        value = laden(pfad)?.let { datei ->
            withContext(Dispatchers.Default) { FotoAufbereitung.vorschau(datei, 480) }
        }
    }
    when (val b = bild) {
        is android.graphics.Bitmap -> Image(
            bitmap = b.asImageBitmap(),
            contentDescription = pfad.substringAfterLast('/'),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 240.dp)
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(
                    onClick = { beiDatei(pfad) },
                    onLongClick = { beiOrdner(pfad) },
                    onLongClickLabel = stringResource(R.string.ablage_im_ordner),
                ),
        )

        Unit -> Box(
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        }

        else -> DateiChips(listOf(pfad), beiDatei, beiOrdner)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssistentBlase(
    eintrag: AssistentEintrag,
    liestVor: Boolean,
    beiVorlesen: () -> Unit,
    beiDatei: (String) -> Unit,
    beiOrdner: (String) -> Unit,
    beiTeilen: (String) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(6.dp, 22.dp, 22.dp, 22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Text, Tabellen, Codebloecke und Diagramme (API.md 1a).
            AntwortInhalt(
                markdown = eintrag.text,
                hintergrund = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            )

            // Seit v1.13.0 kommen die Pfade als `links` mit Typ und Projekt.
            // Aeltere Gespraeche und Server haben nur `dateien` - dann die Chips.
            if (eintrag.links.isNotEmpty()) {
                eintrag.links.forEach { link -> LinkKarte(link, beiDatei, beiOrdner, beiTeilen) }
            } else if (eintrag.dateien.isNotEmpty()) {
                DateiChips(eintrag.dateien, beiDatei, beiOrdner)
            }

            if (eintrag.quellen.isNotEmpty()) {
                QuellenListe(eintrag.quellen)
            }

            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                val kontext = LocalContext.current
                val teilenTitel = stringResource(R.string.antwort_teilen)
                IconButton(
                    onClick = { kopieren(kontext, textZumKopieren(eintrag.text)) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        stringResource(R.string.antwort_kopieren),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = { textTeilen(kontext, textZumKopieren(eintrag.text), teilenTitel) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Default.Share,
                        teilenTitel,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = beiVorlesen, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = if (liestVor) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = stringResource(
                            if (liestVor) R.string.sprache_stopp else R.string.sprache_vorlesen
                        ),
                        modifier = Modifier.size(18.dp),
                        tint = if (liestVor) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

/**
 * Platzhalter, solange eine Anfrage laeuft. Anfragen dauern 5-60 s; ein duenner
 * Balken oben reichte nicht, um zu sehen, dass noch etwas kommt - und wo.
 * Die Sekunden zeigen, dass es nicht haengt.
 */
@Composable
private fun ArbeitetBlase() {
    // Startzeit statt Zaehler, und gesichert: nach Weg- und Zurueckscrollen
    // zaehlt es weiter statt wieder bei 0 anzufangen.
    val beginn = rememberSaveable { System.currentTimeMillis() }
    var sekunden by remember { mutableStateOf(((System.currentTimeMillis() - beginn) / 1000).toInt()) }
    LaunchedEffect(beginn) {
        while (true) {
            delay(1000)
            sekunden = ((System.currentTimeMillis() - beginn) / 1000).toInt()
        }
    }
    Card(
        shape = RoundedCornerShape(6.dp, 22.dp, 22.dp, 22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Text(
                text = stringResource(R.string.antwort_arbeitet) + if (sekunden >= 3) " $sekunden s" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

/**
 * Tippen oeffnet die Datei, langer Druck zeigt sie in ihrem Ordner (`API.md`:
 * "im Ordner landen, die Datei markiert"). Tippen bleibt beim Oeffnen, weil
 * das der haeufigere Fall ist.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DateiChips(dateien: List<String>, beiDatei: (String) -> Unit, beiOrdner: (String) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        dateien.forEach { pfad ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .combinedClickable(
                        onClick = { beiDatei(pfad) },
                        onLongClick = { beiOrdner(pfad) },
                        onLongClickLabel = stringResource(R.string.ablage_im_ordner),
                    ),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.AttachFile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(AssistChipDefaults.IconSize),
                    )
                    Text(
                        text = pfad.substringAfterLast('/'),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

/**
 * Datei oder Ordner aus `links` (seit v1.13.0) als Karte mit Knoepfen, wie
 * `API.md` sie beschreibt: Symbol, Name fett, Projekt darunter. Fragt man nach
 * "der Rechnung von Boese", soll ein Tipp reichen.
 */
@Composable
private fun LinkKarte(
    link: DateiLink,
    beiDatei: (String) -> Unit,
    beiOrdner: (String) -> Unit,
    beiTeilen: (String) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (link.istOrdner) {
                    RundesSymbol(
                        Icons.Default.Folder,
                        farbe = MaterialTheme.colorScheme.primaryContainer,
                        symbolFarbe = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                } else {
                    RundesSymbol(
                        dateiSymbol(link.name),
                        farbe = MaterialTheme.colorScheme.surfaceContainerHighest,
                        symbolFarbe = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(
                        text = link.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val unter = link.projekt?.takeIf { it.isNotBlank() }
                        ?: link.ordner.substringAfterLast('/').takeIf { it.isNotBlank() }
                    val zeile = listOfNotNull(unter, link.groesse).joinToString(" · ")
                    if (zeile.isNotBlank()) {
                        Text(
                            text = zeile,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (link.istOrdner) {
                    FilledTonalButton(onClick = { beiOrdner(link.pfad) }) {
                        Text(stringResource(R.string.link_ordner_oeffnen))
                    }
                } else {
                    IconButton(onClick = { beiTeilen(link.pfad) }) {
                        Icon(Icons.Default.Share, stringResource(R.string.link_teilen))
                    }
                    TextButton(onClick = { beiOrdner(link.pfad) }) {
                        Text(stringResource(R.string.ablage_im_ordner))
                    }
                    FilledTonalButton(onClick = { beiDatei(link.pfad) }) {
                        Text(stringResource(R.string.link_oeffnen))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuellenListe(quellen: List<Quelle>) {
    val kontext = LocalContext.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        quellen.forEach { quelle ->
            AssistChip(
                onClick = { webseiteOeffnen(kontext, quelle.url) },
                label = { Text(quelle.titel, maxLines = 1) },
                leadingIcon = {
                    Icon(
                        Icons.Default.Link,
                        contentDescription = null,
                        modifier = Modifier.size(AssistChipDefaults.IconSize),
                    )
                },
            )
        }
    }
}

@Composable
private fun HinweisZeile(eintrag: HinweisEintrag, beiDatei: (String) -> Unit, beiOrdner: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Text(
            text = eintrag.text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Abgelegte Fotos: antippen laedt sie ueber GET /v1/datei und zeigt sie an.
        if (eintrag.dateien.isNotEmpty()) {
            DateiChips(eintrag.dateien, beiDatei, beiOrdner)
        }
    }
}

/**
 * Unterschrift und Ziel-Notiz fuer ein frisch aufgenommenes Foto oder Video.
 *
 * Bewusst ein Zwischenschritt statt sofortigem Hochladen: was in einer
 * Kundennotiz landet, soll man vorher gesehen haben.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.media3.common.util.UnstableApi::class)
@Composable
private fun MedienDialog(
    medium: MedienEntwurf,
    notizen: List<String>,
    ziele: Notizen?,
    zieleLaden: Boolean,
    beiText: (String) -> Unit,
    beiNotiz: (String?) -> Unit,
    beiAbschreiben: (String) -> Unit,
    beiFragen: (Boolean) -> Unit,
    beiAblegen: () -> Unit,
    beiVerwerfen: () -> Unit,
) {
    val vorschau by produceState<android.graphics.Bitmap?>(null, medium.datei) {
        value = withContext(Dispatchers.Default) {
            when {
                medium.istDokument -> null
                medium.istVideo -> VideoAufbereitung.vorschau(medium.datei)
                else -> FotoAufbereitung.vorschau(medium.datei)
            }
        }
    }
    val frei = !medium.arbeitet
    var zielAuswahl by remember { mutableStateOf(false) }

    if (zielAuswahl) {
        ZielDialog(
            ziele = ziele,
            laedt = zieleLaden,
            gewaehlt = medium.notiz,
            beiWahl = { beiNotiz(it); zielAuswahl = false },
            beiAbbruch = { zielAuswahl = false },
        )
    }

    AlertDialog(
        // Nur ueber die Knoepfe - ein Fehltipp daneben wuerde sonst die Aufnahme verwerfen.
        onDismissRequest = {},
        title = {
            Text(
                stringResource(
                    when {
                        medium.istDokument -> R.string.dokument_titel
                        medium.istVideo -> R.string.video_titel
                        medium.fragen -> R.string.foto_titel_fragen
                        else -> R.string.foto_titel
                    }
                )
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                vorschau?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                }

                medium.dokument?.let { name ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null)
                        Column {
                            Text(name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = Formatter.formatShortFileSize(LocalContext.current, medium.datei.length()),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.dokument_hinweis),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Seit v1.5.0 kann der Assistent Fotos ansehen. Die Wahl steht
                // oben, weil sie bestimmt, wofuer das Textfeld darunter ist.
                if (!medium.istVideo && !medium.istDokument) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        InputChip(
                            selected = !medium.fragen,
                            onClick = { beiFragen(false) },
                            label = { Text(stringResource(R.string.foto_modus_ablegen)) },
                            enabled = frei,
                        )
                        InputChip(
                            selected = medium.fragen,
                            onClick = { beiFragen(true) },
                            label = { Text(stringResource(R.string.foto_modus_fragen)) },
                            leadingIcon = {
                                Icon(Icons.Default.ImageSearch, null, Modifier.size(AssistChipDefaults.IconSize))
                            },
                            enabled = frei,
                        )
                    }
                    if (medium.fragen) {
                        Text(
                            text = stringResource(R.string.foto_frage_hinweis),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                OutlinedTextField(
                    value = medium.text,
                    onValueChange = beiText,
                    label = {
                        Text(
                            stringResource(
                                when {
                                    medium.fragen -> R.string.foto_frage_text
                                    medium.istDokument -> R.string.dokument_text
                                    else -> R.string.foto_text
                                }
                            )
                        )
                    },
                    placeholder = if (medium.fragen) {
                        { Text(stringResource(R.string.foto_frage_vorgabe)) }
                    } else {
                        null
                    },
                    enabled = frei,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = stringResource(R.string.foto_ziel),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InputChip(
                        selected = medium.notiz == null,
                        onClick = { beiNotiz(null) },
                        label = { Text(stringResource(R.string.foto_ziel_heute)) },
                        enabled = frei,
                    )
                    notizen.forEach { pfad ->
                        InputChip(
                            selected = medium.notiz == pfad,
                            onClick = { beiNotiz(pfad) },
                            label = { Text(zielName(pfad), maxLines = 1) },
                            leadingIcon = if (istOrdnerPfad(pfad)) {
                                { Icon(Icons.Default.Folder, null, Modifier.size(AssistChipDefaults.IconSize)) }
                            } else {
                                null
                            },
                            enabled = frei,
                        )
                    }
                    AssistChip(
                        onClick = { zielAuswahl = true },
                        label = { Text(stringResource(R.string.ziel_andere)) },
                        leadingIcon = {
                            Icon(Icons.Default.FolderOpen, null, Modifier.size(AssistChipDefaults.IconSize))
                        },
                        enabled = frei,
                    )
                }
                Text(
                    text = stringResource(
                        when {
                            medium.notiz == null -> R.string.foto_ziel_hinweis
                            istOrdnerPfad(medium.notiz) -> R.string.ziel_ordner_hinweis
                            else -> R.string.ziel_notiz_hinweis
                        }
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (medium.istVideo) {
                    Text(
                        text = stringResource(R.string.video_abschreiben),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ABSCHREIBEN_WERTE.forEach { wert ->
                            InputChip(
                                selected = medium.abschreiben == wert,
                                onClick = { beiAbschreiben(wert) },
                                label = { Text(abschreibenText(wert)) },
                                enabled = frei,
                            )
                        }
                    }
                }

                if (medium.arbeitet) {
                    val prozent = medium.fortschritt
                    if ((medium.istVideo || medium.istDokument) && prozent != null) {
                        Text(
                            text = stringResource(
                                if (medium.istDokument) R.string.dokument_laedt_hoch else R.string.video_verkleinert,
                                prozent,
                            ),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        LinearProgressIndicator(
                            progress = { prozent / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
                medium.fehler?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = beiAblegen, enabled = frei) {
                Text(stringResource(if (medium.fragen) R.string.foto_fragen_knopf else R.string.foto_hochladen))
            }
        },
        dismissButton = {
            TextButton(onClick = beiVerwerfen, enabled = frei) {
                Text(stringResource(R.string.foto_verwerfen))
            }
        },
    )
}

/**
 * HERO-Treffer im Anheften-Dialog: Abzeichen, Name fett, darunter
 * Bauvorhaben · Titel · Status (Auftrag 1.19).
 */
@Composable
private fun HeroTrefferZeile(treffer: SuchTreffer, frei: Boolean, beiKlick: () -> Unit) {
    val p = treffer.projekt
    val unter = listOfNotNull(p?.bauvorhaben, p?.titel, p?.status)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
        .ifBlank { stringResource(R.string.hero_projekt_ohne_angaben) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = frei, onClick = beiKlick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.padding(top = 2.dp)) { HeroAbzeichen() }
        Column(modifier = Modifier.padding(start = 10.dp)) {
            Text(treffer.name, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                text = unter,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Ein Pfad ohne `.md` ist ein Ordner - so unterscheidet der Server es auch. */
private fun istOrdnerPfad(pfad: String) = !pfad.endsWith(".md", ignoreCase = true)

private fun zielName(pfad: String) = pfad.substringAfterLast('/').removeSuffix(".md")

private const val MAX_ORDNER_VORSCHLAEGE = 8

/**
 * Alle eigenen Ordner und Notizen aus `GET /v1/notizen`, mit Suchfeld.
 * Ordner zuerst: das ist der uebliche Weg ("Projektmappe"), die Notiz darin
 * sucht der Server selbst aus.
 */
@Composable
private fun ZielDialog(
    ziele: Notizen?,
    laedt: Boolean,
    gewaehlt: String?,
    beiWahl: (String?) -> Unit,
    beiAbbruch: () -> Unit,
) {
    var suche by remember { mutableStateOf("") }
    val passt: (String) -> Boolean = { it.contains(suche.trim(), ignoreCase = true) }
    val (guOrdner, ordner) = ziele?.ordner.orEmpty().filter { passt(it.name) }.partition { it.istGuProjekt }
    val notizen = ziele?.notizen.orEmpty().filter { passt(it.titel) || passt(it.ordner) }

    AlertDialog(
        onDismissRequest = beiAbbruch,
        title = { Text(stringResource(R.string.ziel_titel)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = suche,
                    onValueChange = { suche = it },
                    label = { Text(stringResource(R.string.ziel_suche)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (laedt && ziele == null) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    item {
                        ZielZeile(
                            icon = Icons.Default.Today,
                            titel = stringResource(R.string.foto_ziel_heute),
                            unter = null,
                            gewaehlt = gewaehlt == null,
                            beiKlick = { beiWahl(null) },
                        )
                    }
                    // Gemeinsame GU-Projektordner als eigene Gruppe (API.md 4d).
                    if (guOrdner.isNotEmpty()) {
                        item { ZielUeberschrift(stringResource(R.string.ziel_gu_projekte)) }
                        items(guOrdner, key = { "g:" + it.pfad }) { o ->
                            ZielZeile(
                                icon = Icons.Default.Engineering,
                                titel = o.name,
                                unter = pluralStringResource(R.plurals.ziel_notizen_anzahl, o.notizen, o.notizen),
                                gewaehlt = gewaehlt == o.pfad,
                                beiKlick = { beiWahl(o.pfad) },
                            )
                        }
                    }
                    if (ordner.isNotEmpty()) {
                        item { ZielUeberschrift(stringResource(R.string.ziel_ordner)) }
                        items(ordner, key = { "o:" + it.pfad }) { o ->
                            ZielZeile(
                                icon = Icons.Default.Folder,
                                titel = o.name,
                                unter = pluralStringResource(R.plurals.ziel_notizen_anzahl, o.notizen, o.notizen),
                                gewaehlt = gewaehlt == o.pfad,
                                beiKlick = { beiWahl(o.pfad) },
                                hero = o.hero_projekt != null,
                            )
                        }
                    }
                    if (notizen.isNotEmpty()) {
                        item { ZielUeberschrift(stringResource(R.string.ziel_notizen)) }
                        items(notizen, key = { "n:" + it.pfad }) { n ->
                            ZielZeile(
                                icon = Icons.AutoMirrored.Filled.Notes,
                                titel = n.titel,
                                unter = n.ordner.removePrefix(GU_PRAEFIX).ifBlank { null },
                                gewaehlt = gewaehlt == n.pfad,
                                beiKlick = { beiWahl(n.pfad) },
                            )
                        }
                    }
                    if (ziele != null && ordner.isEmpty() && guOrdner.isEmpty() && notizen.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.ziel_nichts),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = beiAbbruch) { Text(stringResource(R.string.kontext_abbrechen)) }
        },
    )
}

@Composable
private fun ZielUeberschrift(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun ZielZeile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    titel: String,
    unter: String?,
    gewaehlt: Boolean,
    beiKlick: () -> Unit,
    /** HERO-Mappe: Abzeichen vor dem Namen. */
    hero: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .then(if (gewaehlt) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier)
            .clickable(onClick = beiKlick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hero) {
                    HeroAbzeichen()
                    Spacer(Modifier.width(6.dp))
                }
                Text(titel, maxLines = 2)
            }
            if (unter != null) {
                Text(
                    text = unter,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun abschreibenText(wert: String): String = when (wert) {
    "auto" -> stringResource(R.string.abschreiben_auto)
    "immer" -> stringResource(R.string.abschreiben_immer)
    "nie" -> stringResource(R.string.abschreiben_nie)
    // Neuer Wert vom Server, den die App noch nicht kennt: roh anzeigen.
    else -> wert
}

/**
 * Videoaufnahme mit der 30-Minuten-Grenze des Servers. Die Kamera-App haelt
 * dann selbst an, statt eine Aufnahme zu liefern, die der Server ablehnt.
 */
private class VideoMitGrenze : ActivityResultContracts.CaptureVideo() {
    override fun createIntent(context: Context, input: Uri): Intent =
        super.createIntent(context, input).putExtra(MediaStore.EXTRA_DURATION_LIMIT, 30 * 60)
}

@Composable
private fun FrageKarte(eintrag: FrageEintrag, beiJa: () -> Unit, beiNein: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = eintrag.beschreibung,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.Medium,
            )

            when (val z = eintrag.zustand) {
                is FrageZustand.Offen -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(onClick = beiJa) {
                        Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.bestaetigung_ja))
                    }
                    OutlinedButton(onClick = beiNein) {
                        Icon(Icons.Default.Close, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.bestaetigung_nein))
                    }
                }

                is FrageZustand.Laeuft -> CircularProgressIndicator(
                    Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )

                is FrageZustand.Erledigt -> Text(
                    text = z.text,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (z.erfolgreich) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
        }
    }
}

@Composable
private fun Eingabeleiste(
    entwurf: String,
    kannSenden: Boolean,
    kannAufnehmen: Boolean,
    kannFotografieren: Boolean,
    nimmtAuf: Boolean,
    laeuft: Boolean,
    beiAenderung: (String) -> Unit,
    beiSenden: () -> Unit,
    beiKamera: (istVideo: Boolean, fragen: Boolean) -> Unit,
    beiGalerie: () -> Unit,
    beiDokument: () -> Unit,
    beiMikrofon: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(horizontal = 4.dp)) {
                // Foto, Video und Galerie hinter einem Knopf, damit das Eingabefeld
                // nicht noch schmaler wird.
                Box(modifier = Modifier.padding(bottom = 4.dp)) {
                    var auswahl by remember { mutableStateOf(false) }
                    IconButton(onClick = { auswahl = true }, enabled = kannFotografieren) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = stringResource(R.string.foto_aufnehmen))
                    }
                    DropdownMenu(
                        expanded = auswahl,
                        onDismissRequest = { auswahl = false },
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.kamera_foto)) },
                            leadingIcon = { Icon(Icons.Default.PhotoCamera, null) },
                            onClick = { auswahl = false; beiKamera(false, false) },
                        )
                        // Typenschild, Fehleranzeige: Foto machen und direkt fragen (v1.5.0).
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.kamera_fragen)) },
                            leadingIcon = { Icon(Icons.Default.ImageSearch, null) },
                            onClick = { auswahl = false; beiKamera(false, true) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.kamera_video)) },
                            leadingIcon = { Icon(Icons.Default.Videocam, null) },
                            onClick = { auswahl = false; beiKamera(true, false) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.kamera_galerie)) },
                            leadingIcon = { Icon(Icons.Default.PhotoLibrary, null) },
                            onClick = { auswahl = false; beiGalerie() },
                        )
                        // LV, Plan, Kundenmail als Grundlage fuers Angebot (v1.12.0).
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.kamera_dokument)) },
                            leadingIcon = { Icon(Icons.Default.Description, null) },
                            onClick = { auswahl = false; beiDokument() },
                        )
                    }
                }

                TextField(
                    value = entwurf,
                    onValueChange = beiAenderung,
                    modifier = Modifier.weight(1f),
                    enabled = !nimmtAuf,
                    placeholder = {
                        Text(
                            when {
                                nimmtAuf -> stringResource(R.string.sprache_nimmt_auf)
                                laeuft -> stringResource(R.string.chat_denkt)
                                else -> stringResource(R.string.chat_eingabe)
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    maxLines = 5,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                )

                IconButton(
                    onClick = beiMikrofon,
                    enabled = kannAufnehmen,
                    modifier = Modifier.padding(bottom = 4.dp),
                ) {
                    Icon(
                        imageVector = if (nimmtAuf) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = stringResource(
                            if (nimmtAuf) R.string.sprache_aufnahme_stopp else R.string.sprache_aufnahme_start
                        ),
                        tint = if (nimmtAuf) MaterialTheme.colorScheme.error else LocalContentColor.current,
                    )
                }
            }
        }

        FilledIconButton(
            onClick = beiSenden,
            enabled = kannSenden,
            modifier = Modifier.size(56.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = stringResource(R.string.chat_senden),
            )
        }
    }
}

/**
 * `geaendert` kommt als ISO 8601 mit Offset - teils in UTC. Ohne Umrechnung in
 * die Zone des Geraets stuenden im Sommer zwei Stunden zu wenig in der Liste.
 * Unlesbares wird weggelassen statt zu stuerzen.
 */
private fun geaendertText(iso: String): String? = runCatching {
    OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).format(LISTEN_DATUM)
}.getOrNull()

private fun webseiteOeffnen(kontext: Context, url: String) {
    try {
        kontext.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(kontext, R.string.datei_keine_app, Toast.LENGTH_LONG).show()
    }
}

/** Die Activity hinter einem Compose-Kontext, fuer Fensterflags. */
internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Antworttext an Mail, WhatsApp & Co. - etwa um eine Auskunft an einen Kunden weiterzugeben. */
private fun textTeilen(kontext: Context, text: String, titel: String) {
    val absicht = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    try {
        kontext.startActivity(Intent.createChooser(absicht, titel))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(kontext, R.string.datei_keine_app, Toast.LENGTH_LONG).show()
    }
}

/**
 * Was die Dateiauswahl anbietet - die Typen aus `API.md` (v1.12.0). Die
 * eigentliche Pruefung macht der Server. Alle Textarten decken TXT, MD, CSV und TSV ab.
 */
private val DOKUMENT_TYPEN = arrayOf(
    "application/pdf",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.ms-excel.sheet.macroEnabled.12",
    "text/*",
    "message/rfc822",
)
