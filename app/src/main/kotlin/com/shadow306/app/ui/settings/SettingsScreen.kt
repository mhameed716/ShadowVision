package com.shadow306.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.shadow306.app.R
import com.shadow306.app.core.modeldownload.ModelPaths
import com.shadow306.app.core.util.Format
import com.shadow306.app.di.ServiceLocator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { ServiceLocator.providePrefs(context) }
    val repo = remember { ServiceLocator.provideModelRepository(context) }
    val verifier = remember { ServiceLocator.provideVerifier(context) }

    val lang by prefs.languageFlow.collectAsState(initial = "system")
    val airgapped by prefs.airgappedFlow.collectAsState(initial = false)
    val allowMetered by prefs.allowMeteredFlow.collectAsState(initial = false)
    val engineMode by prefs.engineModeFlow.collectAsState(initial = "auto")
    val packState by repo.state.collectAsState()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var verifyResult by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Language
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { scope.launch { prefs.setLanguage("ar"); ServiceLocator.provideLocaleManager(context).setLanguage("ar") } }) {
                            Text(stringResource(R.string.settings_language_ar))
                        }
                        OutlinedButton(onClick = { scope.launch { prefs.setLanguage("en"); ServiceLocator.provideLocaleManager(context).setLanguage("en") } }) {
                            Text(stringResource(R.string.settings_language_en))
                        }
                        OutlinedButton(onClick = { scope.launch { prefs.setLanguage("system"); ServiceLocator.provideLocaleManager(context).setLanguage("system") } }) {
                            Text("System")
                        }
                    }
                    Text("Current: $lang", style = MaterialTheme.typography.labelSmall)
                }
            }

            // Engine
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.settings_engine), style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = { scope.launch { prefs.setEngineMode("auto") } }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_engine_auto)) }
                    OutlinedButton(onClick = { scope.launch { prefs.setEngineMode("onnx") } }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_engine_onnx)) }
                    OutlinedButton(onClick = { scope.launch { prefs.setEngineMode("fallback") } }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_engine_fallback)) }
                    Text("Current: $engineMode", style = MaterialTheme.typography.labelSmall)
                }
            }

            // Model pack
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.settings_models), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.settings_storage_used, Format.bytes(ModelPaths.usedBytes(context))), style = MaterialTheme.typography.bodySmall)
                    Text("${packState.installedCount}/${packState.totalCount} models", style = MaterialTheme.typography.bodySmall)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = {
                            scope.launch {
                                val manifest = repo.loadBundledManifest()
                                val res = verifier.verify(manifest)
                                verifyResult = "OK: ${res.ok.size}, Missing: ${res.missing.size}, Corrupted: ${res.corrupted.size}"
                            }
                        }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.settings_models_verify)) }

                        OutlinedButton(onClick = { showDeleteDialog = true }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.settings_models_delete)) }
                    }

                    verifyResult?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                }
            }

            // Network / privacy
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.settings_privacy), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.settings_privacy_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_airgap), style = MaterialTheme.typography.bodyMedium)
                            Text(stringResource(R.string.settings_airgap_body), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = airgapped, onCheckedChange = { scope.launch { prefs.setAirgapped(it) } })
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.settings_metered), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = allowMetered, onCheckedChange = { scope.launch { prefs.setAllowMetered(it) } })
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.settings_version, "1.0.0", 1), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.settings_models_delete)) },
            text = { Text(stringResource(R.string.settings_models_delete_confirm, Format.bytes(ModelPaths.usedBytes(context)))) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        verifier.deleteAll()
                        repo.refreshVerification()
                    }
                    showDeleteDialog = false
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}
