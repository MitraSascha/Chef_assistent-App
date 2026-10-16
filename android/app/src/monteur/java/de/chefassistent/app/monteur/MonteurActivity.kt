package de.chefassistent.app.monteur

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.BeachAccess
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import de.chefassistent.app.R
import de.chefassistent.app.monteur.data.MonteurVersand
import de.chefassistent.app.monteur.ui.MikrofonKnopf
import de.chefassistent.app.monteur.ui.bericht.BerichtScreen
import de.chefassistent.app.monteur.ui.bericht.BerichtStart
import de.chefassistent.app.monteur.ui.bericht.BerichtViewModel
import de.chefassistent.app.monteur.ui.einrichtung.EinrichtungScreen
import de.chefassistent.app.monteur.ui.einrichtung.EinrichtungViewModel
import de.chefassistent.app.monteur.ui.heute.HeuteReiter
import de.chefassistent.app.monteur.ui.heute.HeuteViewModel
import de.chefassistent.app.monteur.ui.projekt.ProjektScreen
import de.chefassistent.app.monteur.ui.projekt.ProjektViewModel
import de.chefassistent.app.monteur.ui.rememberAufnahme
import de.chefassistent.app.monteur.ui.urlaub.UrlaubReiter
import de.chefassistent.app.monteur.ui.urlaub.UrlaubViewModel
import de.chefassistent.app.monteur.ui.zeiten.ZeitenReiter
import de.chefassistent.app.monteur.ui.zeiten.ZeitenViewModel
import de.chefassistent.app.ui.ansehen.AnsehenScreen
import de.chefassistent.app.ui.ansehen.AnsehenViewModel
import de.chefassistent.app.ui.theme.ChefAssistentTheme
import de.chefassistent.app.ui.theme.auswahl
import de.chefassistent.app.ui.theme.gold
import de.chefassistent.app.ui.theme.navigationsflaeche
import kotlinx.coroutines.launch

private const val ZIEL_EINRICHTUNG = "einrichtung"
private const val ZIEL_START = "start"
private const val ZIEL_PROJEKT = "projekt/{pid}?termin={termin}&titel={titel}&entwurf={entwurf}"
private const val ZIEL_BERICHT = "bericht?id={id}&termin={termin}&projekt={projekt}&titel={titel}&aufnahme={aufnahme}"
private const val ZIEL_DATEI = "datei/{uuid}?name={name}"

/**
 * Monteur-App (Auftrag 1.22): Einrichtung per QR-Code, dann unten drei Reiter
 * **Heute · Zeiten · Urlaub** mit schwebendem Mikrofon. Auf „Heute“ wird daraus ein
 * Bericht, auf „Zeiten“/„Urlaub“ ein Vorschlag (`/v1/monteur/verstehen`).
 */
class MonteurActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as MonteurApp
        // Was im Korb wartet, beim Start anstossen - auch nach erneuter Einrichtung.
        if (app.einstellungen.istAngemeldet) MonteurVersand.anstossen(this)
        setContent {
            ChefAssistentTheme { MonteurNavigation(app) }
        }
    }
}

@Composable
private fun MonteurNavigation(app: MonteurApp) {
    val nav = rememberNavController()
    val start = if (app.einstellungen.istAngemeldet) ZIEL_START else ZIEL_EINRICHTUNG
    val zurEinrichtung: () -> Unit = { nav.zurEinrichtung() }
    val zuBericht: (BerichtStart) -> Unit = { s ->
        nav.navigate(
            "bericht?id=${s.berichtId.q()}&termin=${s.terminId.q()}&projekt=${s.projektNr.q()}" +
                "&titel=${s.titel.q()}&aufnahme=${s.aufnahme.q()}"
        )
    }

    NavHost(navController = nav, startDestination = start) {
        composable(ZIEL_EINRICHTUNG) {
            val vm: EinrichtungViewModel = viewModel(factory = EinrichtungViewModel.factory(app))
            EinrichtungScreen(vm, beiFertig = {
                MonteurVersand.anstossen(app)
                nav.navigate(ZIEL_START) { popUpTo(ZIEL_EINRICHTUNG) { inclusive = true } }
            })
        }

        composable(ZIEL_START) {
            StartReiter(
                app = app,
                beiAbmeldung = zurEinrichtung,
                beiProjekt = { pid, termin, titel, entwurf ->
                    nav.navigate("projekt/$pid?termin=${termin.q()}&titel=${titel.q()}&entwurf=${entwurf.q()}")
                },
                beiBericht = zuBericht,
            )
        }

        composable(
            ZIEL_PROJEKT,
            arguments = listOf(
                navArgument("pid") { type = NavType.IntType },
                textArgument("termin"), textArgument("titel"), textArgument("entwurf"),
            ),
        ) { e ->
            val a = e.arguments
            val pid = a?.getInt("pid") ?: return@composable
            val termin = a.getString("termin").leerAlsNull()
            val vm: ProjektViewModel = viewModel(factory = ProjektViewModel.factory(app, pid))
            ProjektScreen(
                viewModel = vm,
                entwurfId = a.getString("entwurf").leerAlsNull(),
                beiZurueck = { nav.popBackStack() },
                beiAbmeldung = zurEinrichtung,
                beiDatei = { uuid, name -> nav.navigate("datei/${Uri.encode(uuid)}?name=${Uri.encode(name)}") },
                beiBericht = { berichtId, projektNr ->
                    zuBericht(
                        BerichtStart(
                            berichtId = berichtId,
                            terminId = termin,
                            projektNr = if (termin == null) projektNr else null,
                            titel = a.getString("titel").leerAlsNull(),
                        )
                    )
                },
            )
        }

        composable(
            ZIEL_BERICHT,
            arguments = listOf(
                textArgument("id"), textArgument("termin"), textArgument("projekt"),
                textArgument("titel"), textArgument("aufnahme"),
            ),
        ) { e ->
            val a = e.arguments
            val start = BerichtStart(
                berichtId = a?.getString("id").leerAlsNull(),
                terminId = a?.getString("termin").leerAlsNull(),
                projektNr = a?.getString("projekt").leerAlsNull(),
                titel = a?.getString("titel").leerAlsNull(),
                aufnahme = a?.getString("aufnahme").leerAlsNull(),
            )
            val vm: BerichtViewModel = viewModel(factory = BerichtViewModel.factory(app, start))
            BerichtScreen(vm, beiZurueck = { nav.popBackStack() }, beiAbmeldung = zurEinrichtung)
        }

        composable(
            ZIEL_DATEI,
            arguments = listOf(navArgument("uuid") { type = NavType.StringType }, textArgument("name")),
        ) { e ->
            val uuid = e.arguments?.getString("uuid") ?: return@composable
            val name = e.arguments?.getString("name").leerAlsNull() ?: uuid
            val vm: AnsehenViewModel = viewModel(
                factory = AnsehenViewModel.factory(name, { app.repository.datei(uuid, name) }, app.repository::lokalAbmelden)
            )
            AnsehenScreen(vm, beiZurueck = { nav.popBackStack() }, beiAbmeldung = zurEinrichtung)
        }
    }
}

private enum class Reiter { HEUTE, ZEITEN, URLAUB }

/**
 * Die drei Reiter mit gemeinsamem Kopf (Menue oben rechts), schwebendem Mikrofon
 * und unterer Leiste. Die Reiter selbst bekommen den freien Platz als [PaddingValues].
 */
@Composable
private fun StartReiter(
    app: MonteurApp,
    beiAbmeldung: () -> Unit,
    beiProjekt: (pid: Int, termin: String?, titel: String?, entwurf: String?) -> Unit,
    beiBericht: (BerichtStart) -> Unit,
) {
    var reiter by rememberSaveable { mutableStateOf(Reiter.HEUTE) }
    val heute: HeuteViewModel = viewModel(factory = HeuteViewModel.factory(app))
    val zeiten: ZeitenViewModel = viewModel(factory = ZeitenViewModel.factory(app))
    val urlaub: UrlaubViewModel = viewModel(factory = UrlaubViewModel.factory(app))
    val aufnahme = rememberAufnahme()
    val bereich = rememberCoroutineScope()
    var menue by remember { mutableStateOf(false) }
    var abmeldenFrage by remember { mutableStateOf(false) }
    val wartend by app.korb.anzahl.collectAsState()

    // Jeder 401 fuehrt zurueck zur Einrichtung.
    val abgemeldet = listOf(
        heute.abgemeldet.collectAsState().value,
        zeiten.abgemeldet.collectAsState().value,
        urlaub.abgemeldet.collectAsState().value,
    ).any { it }
    LaunchedEffect(abgemeldet) { if (abgemeldet) beiAbmeldung() }

    if (abmeldenFrage) {
        AlertDialog(
            onDismissRequest = { abmeldenFrage = false },
            text = { Text(stringResource(R.string.m_abmelden_frage)) },
            confirmButton = {
                TextButton(onClick = {
                    abmeldenFrage = false
                    bereich.launch {
                        app.repository.abmelden()
                        beiAbmeldung()
                    }
                }) { Text(stringResource(R.string.m_abmelden), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { abmeldenFrage = false }) { Text(stringResource(R.string.m_abbrechen)) } },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.navigationsflaeche) {
                val farben = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.auswahl,
                    selectedIconColor = MaterialTheme.colorScheme.onSurface,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                listOf(
                    Triple(Reiter.HEUTE, Icons.Outlined.Today, R.string.m_nav_heute),
                    Triple(Reiter.ZEITEN, Icons.Outlined.Schedule, R.string.m_nav_zeiten),
                    Triple(Reiter.URLAUB, Icons.Outlined.BeachAccess, R.string.m_nav_urlaub),
                ).forEach { (r, symbol, text) ->
                    NavigationBarItem(
                        selected = reiter == r,
                        onClick = { reiter = r },
                        icon = {
                            if (r == Reiter.HEUTE && wartend > 0) {
                                BadgedBox(badge = {
                                    Badge(containerColor = MaterialTheme.colorScheme.gold, contentColor = MaterialTheme.colorScheme.onSecondary) {
                                        Text(wartend.toString())
                                    }
                                }) { Icon(symbol, null) }
                            } else {
                                Icon(symbol, null)
                            }
                        },
                        label = { Text(stringResource(text)) },
                        colors = farben,
                    )
                }
            }
        },
    ) { innen ->
        Box(Modifier.fillMaxSize()) {
            // Platz fuer das schwebende Mikrofon unten.
            val inhalt = PaddingValues(
                top = innen.calculateTopPadding(),
                bottom = innen.calculateBottomPadding() + MIKRO_PLATZ,
            )
            when (reiter) {
                Reiter.HEUTE -> HeuteReiter(
                    viewModel = heute,
                    innen = inhalt,
                    beiTermin = beiProjekt,
                    beiBericht = beiBericht,
                )
                Reiter.ZEITEN -> ZeitenReiter(zeiten, inhalt)
                Reiter.URLAUB -> UrlaubReiter(urlaub, inhalt)
            }

            // Feste Flaeche unter der Statusleiste: gescrollter Inhalt laeuft sonst unter die Uhr.
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(innen.calculateTopPadding())
                    .background(MaterialTheme.colorScheme.surface),
            )

            // Menue oben rechts: Abmelden. Die Server-Adresse steht in der Einrichtung.
            Box(Modifier.align(Alignment.TopEnd).padding(top = innen.calculateTopPadding())) {
                IconButton(onClick = { menue = true }) {
                    Icon(Icons.Default.MoreVert, stringResource(R.string.m_menue))
                }
                DropdownMenu(expanded = menue, onDismissRequest = { menue = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.m_abmelden)) },
                        onClick = {
                            menue = false
                            abmeldenFrage = true
                        },
                    )
                }
            }

            // Verlauf zur Hintergrundfarbe hinter dem Mikrofon: Karten darunter blenden aus,
            // statt mit Beschriftung und Hinweis zusammenzustossen.
            val grund = MaterialTheme.colorScheme.surface
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(innen.calculateBottomPadding() + MIKRO_PLATZ + 24.dp)
                    .background(Brush.verticalGradient(0f to grund.copy(alpha = 0f), 0.25f to grund, 1f to grund)),
            )
            MikrofonKnopf(
                steuerung = aufnahme,
                // Je Reiter eine eigene Frage: Bericht, Zeiten oder Urlaub.
                beschriftung = stringResource(
                    when (reiter) {
                        Reiter.HEUTE -> R.string.m_mikro_frage
                        Reiter.ZEITEN -> R.string.m_mikro_zeiten
                        Reiter.URLAUB -> R.string.m_mikro_urlaub
                    }
                ),
                hinweis = stringResource(
                    when (reiter) {
                        Reiter.HEUTE -> R.string.m_mikro_hinweis
                        Reiter.ZEITEN -> R.string.m_mikro_zeiten_hinweis
                        Reiter.URLAUB -> R.string.m_mikro_urlaub_hinweis
                    }
                ),
                beiFertig = { datei ->
                    when (reiter) {
                        // Ohne Termin: der Server findet ihn selbst (Uhrzeit + Plan).
                        Reiter.HEUTE -> beiBericht(BerichtStart(aufnahme = datei.absolutePath))
                        Reiter.ZEITEN -> zeiten.verstehen(datei, null)
                        Reiter.URLAUB -> urlaub.verstehen(datei, null)
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = innen.calculateBottomPadding() + 8.dp),
            )
        }
    }
}

/** Hoehe, die Mikrofon, Beschriftung und Hinweis unten belegen. */
private val MIKRO_PLATZ = 150.dp

private fun NavHostController.zurEinrichtung() {
    navigate(ZIEL_EINRICHTUNG) {
        popUpTo(graph.startDestinationId) { inclusive = true }
        launchSingleTop = true
    }
}

private fun textArgument(name: String) = navArgument(name) {
    type = NavType.StringType
    defaultValue = ""
}

private fun String?.q(): String = Uri.encode(this.orEmpty())

private fun String?.leerAlsNull(): String? = this?.takeIf { it.isNotBlank() }
