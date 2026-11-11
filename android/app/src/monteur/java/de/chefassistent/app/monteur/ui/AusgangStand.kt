package de.chefassistent.app.monteur.ui

import androidx.work.WorkInfo
import androidx.work.WorkManager
import de.chefassistent.app.monteur.MonteurApp
import de.chefassistent.app.monteur.data.KorbFortschritt
import de.chefassistent.app.monteur.data.MonteurKorb
import de.chefassistent.app.monteur.data.MonteurVersand
import de.chefassistent.app.ui.komponenten.AusgangZeile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Fotos, Videos und Dokumente im Ausgang mit ihrem Stand (Auftrag 1.35), fuer
 * [de.chefassistent.app.ui.komponenten.AusgangLeiste]. Mit [projektId] nur die eines Projekts.
 */
fun ausgangFluss(app: MonteurApp, projektId: Int? = null): Flow<List<AusgangZeile>> =
    combine(
        app.korb.dateien,
        WorkManager.getInstance(app).getWorkInfosForUniqueWorkFlow(MonteurVersand.NAME),
    ) { dateien, infos ->
        val lauf = infos.firstOrNull { it.state == WorkInfo.State.RUNNING }?.progress
        val laufName = lauf?.getString(MonteurVersand.MEDIUM)
        val netz = app.hatNetz()
        dateien.filter { projektId == null || it.auftrag.projekt_id == projektId }.map { d ->
            val laeuft = laufName == d.name
            val name = d.auftrag.dateiname
            AusgangZeile(
                name = d.name,
                art = when {
                    MonteurKorb.istVideo(name) -> "video"
                    name.substringAfterLast('.', "").lowercase() in FOTO_ENDUNGEN -> "foto"
                    else -> "dokument"
                },
                titel = name.substringAfterLast('/'),
                aufgenommen = d.seit,
                status = when {
                    laeuft && lauf?.getString(MonteurVersand.PHASE) == KorbFortschritt.PHASE_VERKLEINERN ->
                        AusgangZeile.Status.VERKLEINERT
                    laeuft -> AusgangZeile.Status.SENDET
                    netz -> AusgangZeile.Status.DRAN
                    else -> AusgangZeile.Status.WARTET
                },
                prozent = if (laeuft) lauf?.getInt(MonteurVersand.FORTSCHRITT, 0) else null,
            )
        }
    }

private val FOTO_ENDUNGEN = setOf("jpg", "jpeg", "png", "heic", "heif", "webp")
