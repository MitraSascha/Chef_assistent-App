package de.chefassistent.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.serialization.json.JsonObject
import java.io.IOException
import java.io.InputStream

/**
 * Die vier Aufrufe aus API.md 4h. Buero- und Monteur-App haben je eigene Pfade
 * (`/v1/uploads` bzw. `/v1/monteur/uploads`), der Ablauf ist derselbe.
 * Fehler kommen als [ApiFehler] heraus, wie aus jedem Repository-Aufruf.
 */
interface UploadWeg {
    suspend fun starten(start: UploadStart): UploadStand
    suspend fun teil(id: String, offset: Long, daten: ByteArray): UploadStand
    suspend fun stand(id: String): UploadStand
    suspend fun abschliessen(id: String): UploadStand
}

/** Wie ein Upload in Stuecken ausging. Voruebergehende Fehler kommen nicht hier an, sondern als [ApiFehler]. */
sealed interface UploadAusgang {
    /** Verarbeitet; [ergebnis] ist die Antwort des direkten Endpunkts. */
    data class Fertig(val ergebnis: JsonObject) : UploadAusgang

    /**
     * Fachlich abgelehnt (`status: "fehler"`, 413, 415, 422 …) - nicht wiederholen,
     * dem Nutzer zeigen. 403/404 vom Abschliessen heissen bei Notiz/Gespraech: die Angabe passt nicht mehr.
     */
    data class Abgelehnt(val fehler: ApiFehler) : UploadAusgang

    /** Die `upload_id` taugt nicht mehr (verfallen nach 7 Tagen, oder vergeben). Mit neuer ID von vorn. */
    data object NeueIdNoetig : UploadAusgang

    /** Der Server kennt `…/uploads` nicht (vor 1.35.0): direkt hochladen wie bisher. */
    data object AlterServer : UploadAusgang

    /** Der Nutzer hat den Eintrag aus dem Ausgang geloescht. Abgeschlossen wird nicht. */
    data object Abgebrochen : UploadAusgang

    /** Die lokale Datei ist kuerzer als angekuendigt oder nicht lesbar. */
    data class Defekt(val ursache: Exception) : UploadAusgang
}

/**
 * Hochladen in Stuecken, fortsetzbar (seit 1.35.0, API.md 4h).
 *
 * Der Stand liegt beim Server: `POST …/uploads` mit derselben `upload_id` sagt nach
 * jedem Abbruch, wie viel schon `empfangen` ist, und dort geht es weiter - auch nach
 * einem Neustart der App. Die Datei wird dafuer als Strom gelesen (bei verschluesselten
 * Koerben ueber [Tresor.lesen]) und bis zur Stelle uebersprungen; im Cache liegt
 * nichts im Klartext.
 */
object StueckUpload {

    /** Mobilnetz: API.md empfiehlt 2-4 MB je Stueck. */
    const val TEIL_MOBIL = 4 * 1024 * 1024

    /** Wie oft ein 409 (Stand passt nicht) hintereinander hingenommen wird, bevor es als voruebergehend gilt. */
    private const val SPRUENGE_MAX = 5

    /**
     * @param oeffnen liefert die Datei als frischen Strom ab Byte 0. Wird nach einem
     *   Sprung zurueck (409) erneut aufgerufen.
     * @param teilGroesse Bytes je Stueck bei gegebenem `teil_max` ([teilGroesse]).
     * @param fortschritt gesendete und gesamte Bytes.
     * @param nochGewollt vor jedem Stueck gefragt: `false` = aus dem Ausgang geloescht.
     */
    suspend fun hochladen(
        weg: UploadWeg,
        start: UploadStart,
        oeffnen: () -> InputStream,
        teilGroesse: (Long) -> Int,
        fortschritt: (Long, Long) -> Unit = { _, _ -> },
        nochGewollt: () -> Boolean = { true },
    ): UploadAusgang {
        val id = start.upload_id
        val gesamt = start.groesse

        var stand = try {
            weg.starten(start)
        } catch (fehler: ApiFehler) {
            return when (fehler) {
                // Die Route gibt es nicht: alter Server.
                is ApiFehler.NichtGefunden -> UploadAusgang.AlterServer
                // upload_id gehoert jemand anderem.
                is ApiFehler.Beschaeftigt -> UploadAusgang.NeueIdNoetig
                is ApiFehler.ZuGross, is ApiFehler.Ungueltig, is ApiFehler.Verboten -> UploadAusgang.Abgelehnt(fehler)
                else -> throw fehler
            }
        }

        var spruenge = 0
        while (true) {
            when (stand.status) {
                UploadStand.STATUS_FERTIG -> return UploadAusgang.Fertig(stand.ergebnis ?: JsonObject(emptyMap()))
                UploadStand.STATUS_FEHLER -> return UploadAusgang.Abgelehnt(ApiFehler.AnfrageFehlgeschlagen(stand.fehler))
            }

            // --- Stuecke ab `empfangen` ---
            var empfangen = stand.empfangen.coerceIn(0, gesamt)
            fortschritt(empfangen, gesamt)
            if (empfangen < gesamt) {
                val ein = try {
                    oeffnen()
                } catch (fehler: Exception) {
                    return UploadAusgang.Defekt(fehler)
                }
                ein.use { strom ->
                    try {
                        ueberspringen(strom, empfangen)
                    } catch (fehler: IOException) {
                        return UploadAusgang.Defekt(fehler)
                    }
                    while (empfangen < gesamt) {
                        if (!nochGewollt()) return UploadAusgang.Abgebrochen
                        val laenge = minOf(teilGroesse(stand.teil_max).toLong(), stand.teil_max, gesamt - empfangen).toInt()
                        val daten = try {
                            genauLesen(strom, laenge)
                        } catch (fehler: IOException) {
                            return UploadAusgang.Defekt(fehler)
                        }
                        stand = try {
                            weg.teil(id, empfangen, daten)
                        } catch (fehler: ApiFehler.Beschaeftigt) {
                            // Stand passt nicht (z. B. das vorige Stueck kam an, die Antwort nicht).
                            if (++spruenge > SPRUENGE_MAX) throw fehler
                            stand.copy(empfangen = fehler.empfangen ?: weg.stand(id).empfangen)
                        } catch (fehler: ApiFehler.NichtGefunden) {
                            return UploadAusgang.NeueIdNoetig
                        } catch (fehler: ApiFehler) {
                            if (fehler.istVoruebergehend) throw fehler
                            return UploadAusgang.Abgelehnt(fehler)
                        }
                        if (stand.status != UploadStand.STATUS_OFFEN) break
                        if (stand.empfangen != empfangen + daten.size) {
                            // Sprung: Strom neu oeffnen und ab dem Stand des Servers weiter.
                            break
                        }
                        empfangen = stand.empfangen
                        fortschritt(empfangen, gesamt)
                    }
                }
                if (stand.status == UploadStand.STATUS_OFFEN && stand.empfangen < gesamt) continue
            }

            // --- Abschliessen ---
            if (!nochGewollt()) return UploadAusgang.Abgebrochen
            stand = try {
                weg.abschliessen(id)
            } catch (fehler: ApiFehler.Beschaeftigt) {
                // Noch nicht vollstaendig: ab dem Stand des Servers weiter.
                if (++spruenge > SPRUENGE_MAX) throw fehler
                val e = fehler.empfangen
                if (e != null) stand.copy(empfangen = e) else weg.stand(id)
            } catch (fehler: ApiFehler) {
                if (fehler.istVoruebergehend) throw fehler
                // 404 kann heissen: Upload verfallen - oder Notiz/Gespraech weg. Der Stand sagt es.
                val nachher = try {
                    weg.stand(id)
                } catch (_: ApiFehler.NichtGefunden) {
                    return UploadAusgang.NeueIdNoetig
                }
                if (nachher.status == UploadStand.STATUS_FERTIG) nachher else return UploadAusgang.Abgelehnt(fehler)
            }
        }
    }

    /** Mobilnetz 4 MB, unbegrenztes Netz (WLAN) bis `teil_max`. */
    fun teilGroesse(kontext: Context, teilMax: Long): Int {
        val manager = kontext.getSystemService(ConnectivityManager::class.java)
        val faehig = runCatching { manager?.getNetworkCapabilities(manager.activeNetwork) }.getOrNull()
        val wlan = faehig?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true
        val obergrenze = teilMax.coerceIn(64L * 1024, UploadStand.TEIL_MAX_VORGABE).toInt()
        return if (wlan) obergrenze else minOf(TEIL_MOBIL, obergrenze)
    }

    /**
     * Bewusst durch Lesen statt `skip()`: der entschluesselnde Strom von EncryptedFile
     * erbt von FileInputStream, dessen `skip` die verschluesselte Datei vorspulen wuerde.
     */
    internal fun ueberspringen(ein: InputStream, anzahl: Long) {
        val puffer = ByteArray(256 * 1024)
        var rest = anzahl
        while (rest > 0) {
            val n = ein.read(puffer, 0, minOf(puffer.size.toLong(), rest).toInt())
            if (n < 0) throw IOException("Datei ist kürzer als erwartet")
            rest -= n
        }
    }

    internal fun genauLesen(ein: InputStream, laenge: Int): ByteArray {
        val daten = ByteArray(laenge)
        var gelesen = 0
        while (gelesen < laenge) {
            val n = ein.read(daten, gelesen, laenge - gelesen)
            if (n < 0) throw IOException("Datei ist kürzer als erwartet")
            gelesen += n
        }
        return daten
    }
}
