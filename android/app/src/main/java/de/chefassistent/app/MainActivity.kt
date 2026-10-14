package de.chefassistent.app

import android.content.ContentResolver
import android.content.Intent
import androidx.core.content.IntentCompat
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
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

    NavHost(navController = navController, startDestination = start) {

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
                beiTermine = { navController.navigate(ZIEL_TERMINE) },
                beiVerbrauch = { navController.navigate(ZIEL_VERBRAUCH) },
                beiAuftraege = { navController.navigate(ZIEL_AUFTRAEGE) },
                beiAufgaben = { navController.navigate(ZIEL_AUFGABEN) },
                beiFreihand = { navController.navigate(ZIEL_FREIHAND) },
                beiAblage = { pfad -> navController.navigate("ablage?pfad=${Uri.encode(pfad)}") },
                beiPlantafel = { navController.navigate(ZIEL_GU_PROJEKTE) },
                beiAngebote = { navController.navigate(ZIEL_ANGEBOTE) },
                beiGeraete = { navController.navigate(ZIEL_GERAETE) },
                geteilt = geteilt,
                geteiltErledigt = geteiltErledigt,
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
                beiZurueck = { navController.popBackStack() },
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
                beiZurueck = { navController.popBackStack() },
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
