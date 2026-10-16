package de.chefassistent.app.monteur.ui.karte

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.chefassistent.app.R
import de.chefassistent.app.monteur.data.GeradeFoto
import de.chefassistent.app.ui.theme.gold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Leiste „gerade aufgenommen“ (Auftrag 1.29): Fotos, die schon ins Projekt gehen. Das
 * gemeinte (markierte bzw. automatisch das letzte) hat einen goldenen Rahmen mit Haken -
 * darauf bezieht sich Sprechen oder Tippen. Antippen = markieren/abwaehlen.
 * Wolke = noch im Ausgangskorb.
 */
@Composable
fun GeradeLeiste(
    fotos: List<GeradeFoto>,
    gemeint: Set<String>,
    beiTippen: ((GeradeFoto) -> Unit)?,
    modifier: Modifier = Modifier,
    groesse: Dp = 60.dp,
) {
    if (fotos.isEmpty()) return
    val zustand = rememberLazyListState()
    // Neuestes rechts und sichtbar.
    LaunchedEffect(fotos.size) { zustand.scrollToItem(fotos.lastIndex) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            if (gemeint.isEmpty()) stringResource(R.string.k_gerade, fotos.size)
            else stringResource(R.string.k_gerade_gemeint, fotos.size, gemeint.size),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(state = zustand, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            items(fotos, key = { it.id }) { f -> GeradeBild(f, f.id in gemeint, groesse, beiTippen) }
        }
    }
}

@Composable
private fun GeradeBild(f: GeradeFoto, gemeint: Boolean, groesse: Dp, beiTippen: ((GeradeFoto) -> Unit)?) {
    val bild by produceState<Bitmap?>(null, f.vorschau) {
        value = withContext(Dispatchers.Default) { runCatching { BitmapFactory.decodeFile(f.vorschau) }.getOrNull() }
    }
    val gold = MaterialTheme.colorScheme.gold
    Box(
        Modifier
            .size(groesse)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .then(if (gemeint) Modifier.border(BorderStroke(3.dp, gold), RoundedCornerShape(8.dp)) else Modifier)
            .then(if (beiTippen != null) Modifier.clickable { beiTippen(f) } else Modifier),
    ) {
        bild?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        if (gemeint) {
            Icon(
                Icons.Outlined.CheckCircle, null, tint = gold,
                modifier = Modifier.align(Alignment.TopEnd).padding(3.dp).size(18.dp).background(Color.Black.copy(alpha = 0.55f), CircleShape),
            )
        }
        if (f.fotoId == null) {
            Icon(
                Icons.Outlined.CloudUpload, stringResource(R.string.k_gerade_wartet), tint = Color.White,
                modifier = Modifier.align(Alignment.BottomStart).padding(3.dp).size(16.dp).background(Color.Black.copy(alpha = 0.55f), CircleShape),
            )
        }
    }
}
