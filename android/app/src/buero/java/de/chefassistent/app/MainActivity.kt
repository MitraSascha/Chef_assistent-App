package de.chefassistent.app

import android.content.ContentResolver
import android.content.Intent
import androidx.core.content.IntentCompat
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.currentBackStackEntryAsState
import de.chefassistent.app.data.ApiFehler
import de.chefassistent.app.ui.theme.auswahl
import de.chefassistent.app.ui.theme.gold
import de.chefassistent.app.ui.theme.navigationsflaeche
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import de.chefassistent.app.data.AssistentRepository
import android.net.Uri
import androidx.navigation.NavType
import androidx.navigation.navArgument
import de.chefassistent.app.ui.ablage.AblageScreen
import de.chefassistent.app.ui.ansehen.AnsehenScreen
import de.chefassistent.app.ui.ansehen.AnsehenViewModel
import de.chefassistent.app.ui.angebote.AngebotScreen
import de.chefassistent.app.ui.angebote.AngeboteScreen
import de.chefassistent.app.ui.angebote.AngeboteViewModel
import de.chefassistent.app.ui.ablage.AblageViewModel
import de.chefassistent.app.ui.aufgaben.AufgabenScreen
import de.chefassistent.app.ui.aufgaben.AufgabenViewModel
import de.chefassistent.app.ui.auftraege.AuftraegeScreen
import de.chefassistent.app.ui.auftraege.AuftraegeViewModel
import de.chefassistent.app.ui.anmeldung.AnmeldungScreen
import de.chefassistent.app.ui.anmeldung.AnmeldungViewModel
import de.chefassistent.app.ui.chat.ChatScreen
import de.chefassistent.app.ui.chat.ChatViewModel
import de.chefassistent.app.ui.chat.Geteilt
import de.chefassistent.app.ui.freihand.FreihandScreen
import de.chefassistent.app.ui.freihand.FreihandViewModel
import de.chefassistent.app.ui.geraete.GeraeteScreen
import de.chefassistent.app.ui.geraete.GeraeteViewModel
import de.chefassistent.app.ui.plantafel.GuProjekteScreen
import de.chefassistent.app.ui.plantafel.PlantafelScreen
import de.chefassistent.app.ui.plantafel.PlantafelViewModel
import de.chefassistent.app.ui.kalender.KalenderScreen
import de.chefassistent.app.ui.kalender.KalenderViewModel
import de.chefassistent.app.ui.theme.ChefAssistentTheme
import de.chefassistent.app.ui.verbrauch.VerbrauchScreen
import de.chefassistent.app.ui.verbrauch.VerbrauchViewModel

private const val ZIEL_ANMELDUNG = "anmeldung"
private const val ZIEL_CHAT = "chat"
private const val ZIEL_TERMINE = "termine"
private const val ZIEL_VERBRAUCH = "verbrauch"
private const val ZIEL_FREIHAND = "freihand"
private const val ZIEL_ABLAGE = "ablage?pfad={pfad}"
private const val ZIEL_AUFTRAEGE = "auftraege"
private const val ZIEL_AUFGABEN = "aufgaben"
private const val ZIEL_GU_PROJEKTE = "gu"
private const val ZIEL_PLANTAFEL = "gu/{projekt}"
private const val ZIEL_GERAETE = "geraete"
private const val ZIEL_ANGEBOTE = "angebote"
private const val ZIEL_ANGEBOT = "angebote/{angebot}"
private const val ZIEL_ANSEHEN = "ansehen?pfad={pfad}"

class MainActivity : ComponentActivity() {

    /**
     * Wurde die App ueber Kurzbefehl oder Kachel gestartet, soll sie direkt in
     * den Freihandmodus springen. Als Zustand, damit auch ein zweiter Start bei
     * schon laufender App ankommt.
     */
    private val freihandGewuenscht = mutableStateOf(false)

    /** Dokument oder Text aus "Teilen" einer anderen App, bis der Chat es uebernimmt. */
    private val geteilt = mutableStateOf<Geteilt?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Nach einer Drehung liefert Android denselben Intent erneut - das
        // Dokument soll dann nicht ein zweites Mal im Dialog landen, und der
        // Freihandmodus nicht erneut aufgehen (samt Mikrofon).
        if (savedInstanceState == null) {
            freihandGewuenscht.value = intent?.action == AKTION_FREIHAND
            geteilt.value = geteiltAus(intent)
        }
        val repository = (application as ChefAssistentApp).repository

        setContent {
            ChefAssistentTheme {
                AppNavigation(
                    repository = repository,
                    freihandGewuenscht = freihandGewuenscht.value,
                    freihandErledigt = { freihandGewuenscht.value = false },
                    geteilt = geteilt.value,
                    geteiltErledigt = { geteilt.value = null },
                    anChat = { geteilt.value = it },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == AKTION_FREIHAND) freihandGewuenscht.value = true
        geteiltAus(intent)?.let { geteilt.value = it }
    }

    private fun geteiltAus(intent: Intent?): Geteilt? {
        if (intent?.action != Intent.ACTION_SEND) return null
        IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            ?.let { uri ->
                // Nur fremde Inhalte: ein `file://` oder ein Uri des eigenen
                // FileProviders liesse eine andere App unsere privaten Dateien
                // (Downloads, Korb) in den Upload-Dialog schieben.
                if (uri.scheme != ContentResolver.SCHEME_CONTENT || uri.authority == "$packageName.dateien") return null
                return Geteilt.Dokument(uri)
            }
        return intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }?.let { Geteilt.Text(it) }
    }

    companion object {
        const val AKTION_FREIHAND = "de.chefassistent.app.FREIHAND"
    }
}

@Composable
private fun AppNavigation(
    repository: AssistentRepository,
    freihandGewuenscht: Boolean,
    freihandErledigt: () -> Unit,
    geteilt: Geteilt?,
    geteiltErledigt: () -> Unit,
    /** Etwas aus einer Ansicht in den Chat geben, wie beim Teilen. */
    anChat: (Geteilt) -> Unit,
) {
    val navController = rememberNavController()
    val kontext = LocalContext.current
    val aktuell by navController.currentBackStackEntryAsState()
    val route = aktuell?.destination?.route

    // Seit v1.21.0 ist die Gespraechsliste die Startseite des Reiters Chat.
    // Der Zustand liegt hier, weil die untere Leiste nur ueber der Liste steht,
    // nicht im offenen Gespraech (dort braucht die Eingabe den Platz).
    var listeOffen by rememberSaveable { mutableStateOf(true) }
    val reiter = when (route) {
        ZIEL_CHAT -> if (listeOffen) Reiter.CHAT else null
        ZIEL_TERMINE -> Reiter.KALENDER
        ZIEL_ABLAGE -> Reiter.ABLAGE
        ZIEL_AUFGABEN -> Reiter.OFFEN
        else -> null
    }

    // Zaehler fuer "Offen": bei jedem Reiterwechsel neu. Fehler bleiben still,
    // 401 und Funkloch entscheidet der jeweilige Bildschirm.
    var offen by remember { mutableIntStateOf(0) }
    LaunchedEffect(route, listeOffen) {
        if (reiter != null && repository.einstellungen.istAngemeldet) {
            offen = try {
                repository.aufgaben().offen
            } catch (_: ApiFehler) {
                offen
            }
        }
    }

    val ansehen: (String) -> Unit = { pfad -> navController.navigate("ansehen?pfad=${Uri.encode(pfad)}") }

    // Reiter wie in Material vorgesehen: der Chat bleibt unten im Stapel,
    // jeder andere Reiter liegt einzeln darueber und behaelt seinen Zustand.
    val zuReiter: (Reiter) -> Unit = { ziel ->
        if (ziel == Reiter.CHAT) {
            listeOffen = true
            navController.popBackStack(ZIEL_CHAT, inclusive = false)
        } else {
            val ziel2 = when (ziel) {
                Reiter.KALENDER -> ZIEL_TERMINE
                Reiter.ABLAGE -> "ablage?pfad="
                else -> ZIEL_AUFGABEN
            }
            navController.navigate(ziel2) {
                popUpTo(ZIEL_CHAT) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    // Geteiltes landet im Chat, egal welche Ansicht gerade offen ist. Ohne
    // Anmeldung wartet es, bis der Chat nach dem Anmelden erscheint.
    LaunchedEffect(geteilt) {
        if (geteilt != null && repository.einstellungen.istAngemeldet) {
            navController.popBackStack(ZIEL_CHAT, inclusive = false)
        }
    }

    // Bewusst nicht als Startziel: so fuehrt die Zurueck-Taste aus dem
    // Freihandmodus in den Chat und nicht aus der App heraus.
    LaunchedEffect(freihandGewuenscht) {
        if (freihandGewuenscht && repository.einstellungen.istAngemeldet) {
            navController.navigate(ZIEL_FREIHAND)
            freihandErledigt()
        } else if (freihandGewuenscht) {
            // Ohne Anmeldung gibt es kein Gespraech. Der Wunsch verfaellt, statt
            // nach dem Anmelden unvermittelt das Mikrofon zu oeffnen.
            freihandErledigt()
        }
    }

    // Ein hinterlegtes Token heisst: direkt in den Chat. Ist es abgelaufen oder
    // gesperrt, wirft der erste Aufruf 401 und der Bildschirm schickt zurueck.
    val start = if (repository.einstellungen.istAngemeldet) ZIEL_CHAT else ZIEL_ANMELDUNG

    Scaffold(
        // Die Bildschirme regeln Status- und Navigationsleiste selbst.
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (reiter != null) HauptNavigation(aktiv = reiter, offen = offen, beiWahl = zuReiter)
        },
    ) { innen ->
    NavHost(
        navController = navController,
        startDestination = start,
        modifier = Modifier
            .padding(innen)
            // Mit Leiste ist der Platz der Systemnavigation schon belegt.
            .then(if (reiter != null) Modifier.consumeWindowInsets(WindowInsets.navigationBars) else Modifier),
    ) {

        composable(ZIEL_ANMELDUNG) {
            val viewModel: AnmeldungViewModel = viewModel(
                factory = AnmeldungViewModel.factory(repository)
            )
            AnmeldungScreen(
                viewModel = viewModel,
                beiErfolg = {
                    navController.navigate(ZIEL_CHAT) {
                        popUpTo(ZIEL_ANMELDUNG) { inclusive = true }
                    }
                },
            )
        }

        composable(ZIEL_CHAT) {
            val viewModel: ChatViewModel = viewModel(
                factory = ChatViewModel.factory(repository, kontext)
            )
            ChatScreen(
                viewModel = viewModel,
                beiAbmeldung = { navController.zurueckZurAnmeldung() },
                beiTermine = { zuReiter(Reiter.KALENDER) },
                beiVerbrauch = { navController.navigate(ZIEL_VERBRAUCH) },
                beiAuftraege = { navController.navigate(ZIEL_AUFTRAEGE) },
                beiAufgaben = { zuReiter(Reiter.OFFEN) },
                beiFreihand = { navController.navigate(ZIEL_FREIHAND) },
                beiAblage = { pfad ->
                    if (pfad.isEmpty()) zuReiter(Reiter.ABLAGE)
                    else navController.navigate("ablage?pfad=${Uri.encode(pfad)}")
                },
                beiPlantafel = { navController.navigate(ZIEL_GU_PROJEKTE) },
                beiAngebote = { navController.navigate(ZIEL_ANGEBOTE) },
                beiGeraete = { navController.navigate(ZIEL_GERAETE) },
                geteilt = geteilt,
                geteiltErledigt = geteiltErledigt,
                listeOffen = listeOffen,
                beiListeOffen = { listeOffen = it },
                beiAnsehen = ansehen,
            )
        }

        composable(ZIEL_GERAETE) {
            val viewModel: GeraeteViewModel = viewModel(
                factory = GeraeteViewModel.factory(repository)
            )
            GeraeteScreen(
                viewModel = viewModel,
                beiZurueck = { navController.popBackStack() },
                beiAbmeldung = { navController.zurueckZurAnmeldung() },
            )
        }

        composable(
            ZIEL_ABLAGE,
            arguments = listOf(navArgument("pfad") { type = NavType.StringType; defaultValue = "" }),
        ) { eintrag ->
            val pfad = eintrag.arguments?.getString("pfad").orEmpty()
            val viewModel: AblageViewModel = viewModel(
                factory = AblageViewModel.factory(repository, pfad)
            )
            AblageScreen(
                viewModel = viewModel,
                beiZurueck = { navController.popBackStack() },
                beiAbmeldung = { navController.zurueckZurAnmeldung() },
                beiAnsehen = ansehen,
            )
        }

        composable(
            ZIEL_ANSEHEN,
            arguments = listOf(navArgument("pfad") { type = NavType.StringType; defaultValue = "" }),
        ) { eintrag ->
            val pfad = eintrag.arguments?.getString("pfad").orEmpty()
            val viewModel: AnsehenViewModel = viewModel(factory = AnsehenViewModel.factory(repository, pfad))
            AnsehenScreen(
                viewModel = viewModel,
                beiZurueck = { navController.popBackStack() },
                beiAbmeldung = { navController.zurueckZurAnmeldung() },
            )
        }

        composable(ZIEL_FREIHAND) {
            val viewModel: FreihandViewModel = viewModel(
                factory = FreihandViewModel.factory(repository, kontext)
            )
            FreihandScreen(
                viewModel = viewModel,
                beiZurueck = { navController.popBackStack() },
                beiAbmeldung = { navController.zurueckZurAnmeldung() },
            )
        }

        composable(ZIEL_TERMINE) {
            val viewModel: KalenderViewModel = viewModel(
                factory = KalenderViewModel.factory(repository)
            )
            KalenderScreen(
                viewModel = viewModel,
                beiZurueck = null,
                beiAbmeldung = { navController.zurueckZurAnmeldung() },
                beiOrdner = { pfad -> navController.navigate("ablage?pfad=${Uri.encode(pfad)}") },
            )
        }

        composable(ZIEL_AUFTRAEGE) {
            val viewModel: AuftraegeViewModel = viewModel(
                factory = AuftraegeViewModel.factory(repository)
            )
            AuftraegeScreen(
                viewModel = viewModel,
                beiZurueck = { navController.popBackStack() },
                beiAbmeldung = { navController.zurueckZurAnmeldung() },
                beiOrdner = { pfad -> navController.navigate("ablage?pfad=${Uri.encode(pfad)}") },
            )
        }

        composable(ZIEL_AUFGABEN) {
            val viewModel: AufgabenViewModel = viewModel(
                factory = AufgabenViewModel.factory(repository)
            )
            AufgabenScreen(
                viewModel = viewModel,
                beiZurueck = null,
                beiAbmeldung = { navController.zurueckZurAnmeldung() },
            )
        }

        composable(ZIEL_GU_PROJEKTE) {
            val viewModel: PlantafelViewModel = viewModel(
                factory = PlantafelViewModel.factory(repository)
            )
            GuProjekteScreen(
                viewModel = viewModel,
                beiZurueck = { navController.popBackStack() },
                beiAbmeldung = { navController.zurueckZurAnmeldung() },
                beiProjekt = { id -> navController.navigate("gu/$id") },
            )
        }

        composable(
            ZIEL_PLANTAFEL,
            arguments = listOf(navArgument("projekt") { type = NavType.IntType }),
        ) { eintrag ->
            val projektId = eintrag.arguments?.getInt("projekt") ?: return@composable
            val viewModel: PlantafelViewModel = viewModel(
                factory = PlantafelViewModel.factory(repository)
            )
            PlantafelScreen(
                viewModel = viewModel,
                projektId = projektId,
                beiZurueck = { navController.popBackStack() },
                beiAbmeldung = { navController.zurueckZurAnmeldung() },
                beiOrdner = { pfad -> navController.navigate("ablage?pfad=${Uri.encode(pfad)}") },
            )
        }

        composable(ZIEL_ANGEBOTE) {
            val viewModel: AngeboteViewModel = viewModel(
                factory = AngeboteViewModel.factory(repository)
            )
            AngeboteScreen(
                viewModel = viewModel,
                beiZurueck = { navController.popBackStack() },
                beiAbmeldung = { navController.zurueckZurAnmeldung() },
                beiAngebot = { id -> navController.navigate("angebote/${Uri.encode(id)}") },
            )
        }

        composable(
            ZIEL_ANGEBOT,
            arguments = listOf(navArgument("angebot") { type = NavType.StringType }),
        ) { eintrag ->
            val angebotId = eintrag.arguments?.getString("angebot") ?: return@composable
            val viewModel: AngeboteViewModel = viewModel(
                factory = AngeboteViewModel.factory(repository)
            )
            AngebotScreen(
                viewModel = viewModel,
                angebotId = angebotId,
                beiZurueck = { navController.popBackStack() },
                beiAbmeldung = { navController.zurueckZurAnmeldung() },
                beiEinarbeiten = { text -> anChat(Geteilt.Senden(text)) },
            )
        }

        composable(ZIEL_VERBRAUCH) {
            val viewModel: VerbrauchViewModel = viewModel(
                factory = VerbrauchViewModel.factory(repository)
            )
            VerbrauchScreen(
                viewModel = viewModel,
                beiZurueck = { navController.popBackStack() },
                beiAbmeldung = { navController.zurueckZurAnmeldung() },
            )
        }
    }
    }
}

private enum class Reiter { CHAT, KALENDER, ABLAGE, OFFEN }

/**
 * Untere Navigation mit vier Zielen (Auftrag 1.21, B3). Plantafel, Angebote,
 * Auftraege und Einstellungen bleiben in der Schublade der Gespraechsliste.
 */
@Composable
private fun HauptNavigation(aktiv: Reiter, offen: Int, beiWahl: (Reiter) -> Unit) {
    val farben = MaterialTheme.colorScheme
    NavigationBar(containerColor = farben.navigationsflaeche) {
        val eintraege = listOf(
            Triple(Reiter.CHAT, Icons.Outlined.ChatBubbleOutline, R.string.nav_chat),
            Triple(Reiter.KALENDER, Icons.Outlined.CalendarMonth, R.string.nav_kalender),
            Triple(Reiter.ABLAGE, Icons.Outlined.Folder, R.string.nav_ablage),
            Triple(Reiter.OFFEN, Icons.Outlined.TaskAlt, R.string.nav_offen),
        )
        for ((reiter, symbol, text) in eintraege) {
            NavigationBarItem(
                selected = reiter == aktiv,
                onClick = { if (reiter != aktiv) beiWahl(reiter) },
                icon = {
                    if (reiter == Reiter.OFFEN && offen > 0) {
                        BadgedBox(badge = {
                            // Gold als Flaeche, Navy als Schrift (Auftrag 1.21, B1).
                            Badge(containerColor = farben.gold, contentColor = farben.onSecondary) {
                                Text(if (offen > 99) "99+" else offen.toString())
                            }
                        }) { Icon(symbol, null) }
                    } else {
                        Icon(symbol, null)
                    }
                },
                label = { Text(stringResource(text)) },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = farben.auswahl,
                    selectedIconColor = farben.onSurface,
                    selectedTextColor = farben.onSurface,
                    unselectedIconColor = farben.onSurfaceVariant,
                    unselectedTextColor = farben.onSurfaceVariant,
                ),
            )
        }
    }
}

/**
 * Nach einem 401 zurueck zur Anmeldung - und zwar von jedem Bildschirm aus.
 * Der gesamte bisherige Verlauf faellt weg, damit die Zurueck-Taste nicht in
 * eine Ansicht fuehrt, die ohne Token ohnehin nichts laden kann.
 */
private fun NavHostController.zurueckZurAnmeldung() {
    navigate(ZIEL_ANMELDUNG) {
        popUpTo(graph.startDestinationId) { inclusive = true }
        launchSingleTop = true
    }
}
