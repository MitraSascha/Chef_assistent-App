package de.chefassistent.app.monteur

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import de.chefassistent.app.ui.theme.ChefAssistentTheme

class MonteurActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChefAssistentTheme {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Mitra Monteur") }
                }
            }
        }
    }
}
