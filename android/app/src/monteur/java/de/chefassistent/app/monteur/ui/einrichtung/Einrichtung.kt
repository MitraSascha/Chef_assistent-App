package de.chefassistent.app.monteur.ui.einrichtung

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import de.chefassistent.app.R
import de.chefassistent.app.data.ApiFehler
import de.chefassistent.app.monteur.MonteurApp
import de.chefassistent.app.monteur.data.MonteurRepository
import de.chefassistent.app.ui.komponenten.MZeichen
import de.chefassistent.app.ui.theme.hauptflaeche
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EinrichtungZustand(
    val adresse: String,
    val laeuft: Boolean = false,
    /** Text vom Server (`detail`) bzw. eigener Hinweis, `null` = kein Fehler. */
    val fehler: String? = null,
    val fertig: Boolean = false,
)

/**
 * Einrichtung der Monteur-App (Auftrag 1.22, 1.): QR-Code `mitra-monteur:<URL>#<Code>`
 * scannen oder den Code abtippen -> `POST /v1/monteur/einrichten` -> Token.
 * Versteckt (langer Druck aufs Zeichen): Server-Adresse und Anmeldung mit Buero-Zugang
 * zum Testen. Die Rolle wird nicht geprueft.
 */
class EinrichtungViewModel(private val app: MonteurApp) : ViewModel() {

    private val _zustand = MutableStateFlow(EinrichtungZustand(adresse = app.einstellungen.basisUrl))
    val zustand: StateFlow<EinrichtungZustand> = _zustand.asStateFlow()

    fun adresseAendern(wert: String) = _zustand.update { it.copy(adresse = wert) }

    fun fehlerZeigen(text: String) = _zustand.update { it.copy(fehler = text) }

    /** Ergebnis des QR-Scanners. Ein fremder QR-Code fuehrt zu einem Hinweis statt zu einem Aufruf. */
    fun qrGelesen(inhalt: String, fremdText: String, ungueltigText: String) {
        val (url, code) = MonteurRepository.qrLesen(inhalt) ?: run {
            fehlerZeigen(fremdText)
            return
        }
        _zustand.update { it.copy(adresse = url) }
        einrichten(code, ungueltigText)
    }

    fun einrichten(code: String, ungueltigText: String) {
        if (_zustand.value.laeuft || code.isBlank()) return
        _zustand.update { it.copy(laeuft = true, fehler = null) }
        viewModelScope.launch {
            try {
                app.repository.einrichten(_zustand.value.adresse, code)
                _zustand.update { it.copy(fertig = true) }
            } catch (fehler: ApiFehler) {
                // 401 heisst hier: Code falsch, benutzt oder abgelaufen (Text aus dem Auftrag).
                val text = if (fehler is ApiFehler.NichtAngemeldet) ungueltigText else fehler.anzeigetext
                _zustand.update { it.copy(fehler = text) }
            } finally {
                _zustand.update { it.copy(laeuft = false) }
            }
        }
    }

    /** Zum Testen: Buero-Zugang mit Passwort und Zwei-Faktor-Code (API.md 5c erlaubt das). */
    fun bueroAnmelden(name: String, passwort: String, code: String) {
        if (_zustand.value.laeuft) return
        _zustand.update { it.copy(laeuft = true, fehler = null) }
        viewModelScope.launch {
            try {
                app.bueroZugang.anmeldenMitPasswort(_zustand.value.adresse, name, passwort, code)
                _zustand.update { it.copy(fertig = true) }
            } catch (fehler: ApiFehler) {
                _zustand.update { it.copy(fehler = fehler.anzeigetext) }
            } finally {
                _zustand.update { it.copy(laeuft = false) }
            }
        }
    }

    companion object {
        fun factory(app: MonteurApp) = viewModelFactory { initializer { EinrichtungViewModel(app) } }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EinrichtungScreen(viewModel: EinrichtungViewModel, beiFertig: () -> Unit) {
    val zustand by viewModel.zustand.collectAsStateWithLifecycle()
    val kontext = LocalContext.current
    var code by rememberSaveable { mutableStateOf("") }
    var erweitert by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var passwort by rememberSaveable { mutableStateOf("") }
    var zweifaktor by rememberSaveable { mutableStateOf("") }
    val fremd = stringResource(R.string.m_qr_fremd)
    val ungueltig = stringResource(R.string.m_code_ungueltig)
    val scannerFehlt = stringResource(R.string.m_scanner_fehlt)

    LaunchedEffect(zustand.fertig) { if (zustand.fertig) beiFertig() }

    val scannen: () -> Unit = {
        // Scanner der Play-Dienste: eigene Kamera-Oberflaeche, keine Kamera-Berechtigung noetig.
        val optionen = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        GmsBarcodeScanning.getClient(kontext, optionen).startScan()
            .addOnSuccessListener { treffer -> treffer.rawValue?.let { viewModel.qrGelesen(it, fremd, ungueltig) } }
            .addOnFailureListener { viewModel.fehlerZeigen(scannerFehlt) }
    }

    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
        ) {
            // Langer Druck aufs Zeichen oeffnet die versteckten Einstellungen.
            MZeichen(
                groesse = 64.dp,
                modifier = Modifier.combinedClickable(onClick = {}, onLongClick = { erweitert = !erweitert }),
            )
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.m_einrichtung_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))

            Button(
                onClick = scannen,
                enabled = !zustand.laeuft,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.hauptflaeche, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Icon(Icons.Outlined.QrCodeScanner, null, Modifier.size(24.dp))
                Spacer(Modifier.size(10.dp))
                Text(stringResource(R.string.m_qr_scannen), style = MaterialTheme.typography.titleMedium)
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(stringResource(R.string.m_code_eintippen), style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                label = { Text(stringResource(R.string.m_code_feld)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = { viewModel.einrichten(code, ungueltig) },
                enabled = !zustand.laeuft && code.isNotBlank(),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.m_einrichten)) }

            if (zustand.laeuft) CircularProgressIndicator()
            zustand.fehler?.let {
                Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }

            if (erweitert) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(stringResource(R.string.m_versteckt), style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = zustand.adresse,
                    onValueChange = viewModel::adresseAendern,
                    label = { Text(stringResource(R.string.m_server)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.m_buero_zugang), style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.m_name)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = passwort, onValueChange = { passwort = it }, label = { Text(stringResource(R.string.m_passwort)) },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = zweifaktor, onValueChange = { zweifaktor = it.filter(Char::isDigit).take(6) },
                    label = { Text(stringResource(R.string.m_zweifaktor)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    onClick = { viewModel.bueroAnmelden(name, passwort, zweifaktor) },
                    enabled = !zustand.laeuft && name.isNotBlank() && passwort.isNotEmpty() && zweifaktor.length == 6,
                ) { Text(stringResource(R.string.m_anmelden)) }
            }
        }
    }
}
