package de.chefassistent.app.monteur

import android.app.Application
import de.chefassistent.app.data.AssistentRepository
import de.chefassistent.app.data.Einstellungen
import de.chefassistent.app.monteur.data.MonteurKorb
import de.chefassistent.app.monteur.data.MonteurRepository

/**
 * Anwendung der Monteur-App. Teilt Netz, Token-Speicher und Fehleruebersetzung
 * mit der Buero-App, hat aber eigene Oberflaeche und einen eigenen Ausgangskorb.
 */
class MonteurApp : Application() {

    /** Eine Instanz fuer beide Repositories: dasselbe Token, dieselbe Adresse. */
    val einstellungen: Einstellungen by lazy { Einstellungen(applicationContext) }

    val repository: MonteurRepository by lazy { MonteurRepository(applicationContext, einstellungen) }

    /** Berichte, Zeiten und Antraege, die auf Netz warten. Nur eine Instanz. */
    val korb: MonteurKorb by lazy {
        MonteurKorb(applicationContext).also { k ->
            k.beiFotoId = { anfrage, fotoId -> gerade.fotoIdSetzen(anfrage, fotoId) }
            k.fotoIdFuer = gerade::fotoIdFuer
        }
    }

    /** Leiste „gerade aufgenommen“ (1.29.0). */
    val gerade: de.chefassistent.app.monteur.data.GeradeAufgenommen by lazy {
        de.chefassistent.app.monteur.data.GeradeAufgenommen(applicationContext)
    }

    /**
     * Fotos sofort ins Projekt (Auftrag 1.29): je Foto ein stiller Upload ueber den Korb,
     * dazu ein Eintrag in „gerade aufgenommen“. Kein Limit, auch im Funkloch.
     */
    suspend fun fotosAblegen(projektId: Int, dateien: List<java.io.File>) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        dateien.forEach { datei ->
            val id = MonteurRepository.neueAnfrageId()
            val vorschau = gerade.vorschauSchreiben(datei, id)
            korb.dateiEinreihen(
                de.chefassistent.app.monteur.data.DateiAuftrag(id, projektId, datei.name, still = true),
                datei,
            )
            gerade.hinzu(de.chefassistent.app.monteur.data.GeradeFoto(id, projektId, System.currentTimeMillis(), vorschau))
        }
        de.chefassistent.app.monteur.data.MonteurVersand.anstossen(this@MonteurApp)
    }

    /** Merkzettel fuer Benachrichtigungen (1.24.0). */
    val nachrichtenSpeicher: de.chefassistent.app.monteur.push.NachrichtenSpeicher by lazy {
        de.chefassistent.app.monteur.push.NachrichtenSpeicher(applicationContext)
    }

    private val _nachrichtenSignal = kotlinx.coroutines.flow.MutableStateFlow(0L)

    /** Zaehlt hoch, wenn eine Nachricht per Push oder Abholen kam - die Leiste laedt dann neu. */
    val nachrichtenSignal: kotlinx.coroutines.flow.StateFlow<Long> = _nachrichtenSignal

    fun nachrichtenGeaendert() {
        _nachrichtenSignal.value = System.currentTimeMillis()
    }

    override fun onCreate() {
        super.onCreate()
        de.chefassistent.app.monteur.push.Benachrichtigungen.kanaeleAnlegen(this)
    }

    /** Abmelden (1.24.0): Push-Token beim Server entfernen, abmelden, Token loeschen, Abholen stoppen. */
    suspend fun abmelden() {
        repository.abmelden(nachrichtenSpeicher.token)
        de.chefassistent.app.monteur.push.Nachrichtendienst.tokenLoeschen(this)
        de.chefassistent.app.monteur.push.Nachrichtendienst.abholenAbbestellen(this)
        nachrichtenSpeicher.leeren()
        gerade.leeren()
    }

    /** Nur fuer die versteckte Anmeldung mit Buero-Zugang (Passwort + Code) zum Testen. */
    val bueroZugang: AssistentRepository by lazy { AssistentRepository(applicationContext, einstellungen) }
}
