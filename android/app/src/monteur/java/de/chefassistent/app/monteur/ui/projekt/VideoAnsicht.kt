package de.chefassistent.app.monteur.ui.projekt

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import de.chefassistent.app.R

/**
 * Video aus dem Projektordner (Auftrag 1.22, 2a), direkt vom Server gestreamt -
 * Videos sind bis 1 GB gross, Herunterladen vorab waere zu viel. Der Server kann
 * Range-Anfragen, also geht Spulen. Das Token kommt als Kopfzeile mit.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoAnsicht(adresse: String, token: String?, beiZurueck: () -> Unit) {
    val kontext = LocalContext.current
    val spieler = remember(adresse) {
        val quelle = DefaultHttpDataSource.Factory().apply {
            if (!token.isNullOrBlank()) setDefaultRequestProperties(mapOf("Authorization" to "Bearer $token"))
        }
        ExoPlayer.Builder(kontext)
            .setMediaSourceFactory(DefaultMediaSourceFactory(quelle))
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(adresse))
                prepare()
                playWhenReady = true
            }
    }
    DisposableEffect(spieler) { onDispose { spieler.release() } }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { PlayerView(it).apply { player = spieler } }, modifier = Modifier.fillMaxSize())
        IconButton(onClick = beiZurueck, modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(4.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.m_zurueck), tint = Color.White)
        }
    }
}
