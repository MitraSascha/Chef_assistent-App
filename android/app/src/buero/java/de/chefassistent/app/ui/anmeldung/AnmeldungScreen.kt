package de.chefassistent.app.ui.anmeldung

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.chefassistent.app.R
import de.chefassistent.app.data.Einstellungen

@Composable
fun AnmeldungScreen(
    viewModel: AnmeldungViewModel,
    beiErfolg: () -> Unit,
) {
    val zustand by viewModel.zustand.collectAsStateWithLifecycle()
    val mitPasswort = zustand.art == AnmeldeArt.PASSWORT
    val verbinden = { viewModel.verbinden(beiErfolg) }

    Scaffold { innen ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innen)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = stringResource(R.string.anmeldung_titel),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))

            if (mitPasswort) {
                PasswortFelder(zustand, viewModel, verbinden)
            } else {
                OutlinedTextField(
                    value = zustand.token,
                    onValueChange = viewModel::tokenGeaendert,
                    label = { Text(stringResource(R.string.anmeldung_token)) },
                    supportingText = { Text(stringResource(R.string.anmeldung_token_hinweis)) },
                    singleLine = true,
                    enabled = !zustand.laeuft,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Go,
                    ),
                    keyboardActions = KeyboardActions(onGo = { verbinden() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            zustand.fehler?.let { text ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = text,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            Button(
                onClick = verbinden,
                enabled = zustand.kannVerbinden,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (zustand.laeuft) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.anmeldung_laeuft))
                } else {
                    Text(stringResource(if (mitPasswort) R.string.anmeldung_anmelden else R.string.anmeldung_pruefen))
                }
            }

            Spacer(Modifier.height(8.dp))

            // Die Adresse steht unter dem Knopf: meist passt die Vorgabe, und wer
            // sie aendern muss, soll es trotzdem ohne Umweg koennen.
            OutlinedTextField(
                value = zustand.url,
                onValueChange = viewModel::urlGeaendert,
                label = { Text(stringResource(R.string.anmeldung_server)) },
                singleLine = true,
                enabled = !zustand.laeuft,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = zustand.istInternet,
                    onClick = { viewModel.urlGeaendert(Einstellungen.STANDARD_BASIS_URL) },
                    label = { Text(stringResource(R.string.anmeldung_server_internet)) },
                    enabled = !zustand.laeuft,
                )
                FilterChip(
                    selected = zustand.istTailscale,
                    onClick = { viewModel.urlGeaendert(Einstellungen.TAILSCALE_BASIS_URL) },
                    label = { Text(stringResource(R.string.anmeldung_server_tailscale)) },
                    enabled = !zustand.laeuft,
                )
            }

            // Der haeufigste Grund fuer eine fehlgeschlagene Verbindung ist nicht
            // die App - beim Passwort die fehlende Einrichtung, beim Schluessel
            // ein inaktives VPN. Deshalb steht der Hinweis fest auf dem Bildschirm.
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(
                        if (mitPasswort) R.string.anmeldung_hinweis else R.string.anmeldung_hinweis_schluessel
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }

            // Uebergang: bewusst unauffaellig, der Regelfall ist die Anmeldung mit Passwort.
            TextButton(
                onClick = {
                    viewModel.artWechseln(if (mitPasswort) AnmeldeArt.SCHLUESSEL else AnmeldeArt.PASSWORT)
                },
                enabled = !zustand.laeuft,
            ) {
                Text(
                    text = stringResource(
                        if (mitPasswort) R.string.anmeldung_mit_schluessel else R.string.anmeldung_mit_passwort
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/**
 * Name, Passwort, Code. Die `contentType`-Semantik sagt dem Android-Autofill,
 * was in welches Feld gehoert - beim Code als Einmalcode (entspricht
 * `AUTOFILL_HINT_SMS_OTP`), damit Tastatur oder Passwortmanager ihn anbieten koennen.
 */
@Composable
private fun PasswortFelder(
    zustand: AnmeldungZustand,
    viewModel: AnmeldungViewModel,
    verbinden: () -> Unit,
) {
    var passwortSichtbar by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = zustand.name,
        onValueChange = viewModel::nameGeaendert,
        label = { Text(stringResource(R.string.anmeldung_name)) },
        singleLine = true,
        enabled = !zustand.laeuft,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Next,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentType = ContentType.Username },
    )

    OutlinedTextField(
        value = zustand.passwort,
        onValueChange = viewModel::passwortGeaendert,
        label = { Text(stringResource(R.string.anmeldung_passwort)) },
        singleLine = true,
        enabled = !zustand.laeuft,
        visualTransformation = if (passwortSichtbar) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { passwortSichtbar = !passwortSichtbar }) {
                Icon(
                    if (passwortSichtbar) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    stringResource(
                        if (passwortSichtbar) R.string.anmeldung_passwort_verbergen else R.string.anmeldung_passwort_zeigen
                    ),
                )
            }
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Next,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentType = ContentType.Password },
    )

    OutlinedTextField(
        value = zustand.code,
        onValueChange = viewModel::codeGeaendert,
        label = { Text(stringResource(R.string.anmeldung_code)) },
        supportingText = { Text(stringResource(R.string.anmeldung_code_hinweis)) },
        singleLine = true,
        enabled = !zustand.laeuft,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Go,
        ),
        keyboardActions = KeyboardActions(onGo = { verbinden() }),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentType = ContentType.SmsOtpCode },
    )
}
