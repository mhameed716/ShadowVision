package com.shadow306.app.ui.tryon

import android.graphics.Bitmap
import android.net.Uri
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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.shadow306.app.core.data.prefs.AppPreferences
import com.shadow306.app.core.engine.onnx.EngineMode
import com.shadow306.app.core.util.FileIo
import com.shadow306.app.di.ServiceLocator
import com.shadow306.app.ui.common.FallbackBanner
import com.shadow306.app.ui.common.ImagePickerSlot
import com.shadow306.app.ui.common.rememberBitmapFromUri
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TryOnScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { ServiceLocator.providePrefs(context) }
    val engine = remember { ServiceLocator.provideTryOnEngine(context) }
    val repo = remember { ServiceLocator.provideModelRepository(context) }
    val packState by repo.state.collectAsState()

    var personUri by remember { mutableStateOf<Uri?>(null) }
    var garmentUri by remember { mutableStateOf<Uri?>(null) }
    var fit by remember { mutableFloatStateOf(50f) }
    var matchShadow by remember { mutableStateOf(true) }
    var resultBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var processing by remember { mutableStateOf(false) }
    var usedFallback by remember { mutableStateOf(false) }

    val personBitmap = rememberBitmapFromUri(personUri)
    val garmentBitmap = rememberBitmapFromUri(garmentUri)

    val isFallback = packState.perModel.values.any { it.status != com.shadow306.app.core.modeldownload.ModelDownloadState.Status.INSTALLED && it.spec.features.contains(com.shadow306.app.core.modeldownload.ModelSpec.Feature.TRY_ON) }

    LaunchedEffect(Unit) {
        fit = prefs.tryOnFitFlow.first().toFloat()
        matchShadow = prefs.tryOnShadowFlow.first()
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.tryon_title)) })
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isFallback) FallbackBanner()

            Text(stringResource(R.string.tryon_person), style = MaterialTheme.typography.titleMedium)
            ImagePickerSlot(
                bitmap = personBitmap,
                hint = stringResource(R.string.tryon_person_hint),
                onPick = { personUri = it }
            )

            Text(stringResource(R.string.tryon_garment), style = MaterialTheme.typography.titleMedium)
            ImagePickerSlot(
                bitmap = garmentBitmap,
                hint = stringResource(R.string.tryon_garment_hint),
                onPick = { garmentUri = it }
            )

            Text(stringResource(R.string.tryon_options), style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.tryon_fit), modifier = Modifier.weight(1f))
                Text("${fit.toInt()}%")
            }
            Slider(value = fit, onValueChange = { fit = it }, valueRange = 0f..100f)

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.tryon_shadow), modifier = Modifier.weight(1f))
                Switch(checked = matchShadow, onCheckedChange = { matchShadow = it })
            }

            Button(
                onClick = {
                    val p = personBitmap ?: return@Button
                    val g = garmentBitmap ?: return@Button
                    scope.launch {
                        processing = true
                        val mode = if (isFallback) EngineMode.FALLBACK else EngineMode.AUTO
                        val res = engine.tryOn(p, g, com.shadow306.app.core.engine.body.TryOnEngine.Params(fit.toInt(), matchShadow), mode)
                        resultBitmap = res.bitmap
                        usedFallback = res.usedFallback
                        processing = false
                        prefs.setTryOnFit(fit.toInt())
                        prefs.setTryOnShadow(matchShadow)
                    }
                },
                enabled = personBitmap != null && garmentBitmap != null && !processing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (processing) stringResource(R.string.state_processing) else stringResource(R.string.tryon_generate))
            }

            resultBitmap?.let { bmp ->
                Text(stringResource(R.string.tryon_result), style = MaterialTheme.typography.titleMedium)
                ImagePickerSlot(bitmap = bmp, hint = "", onPick = {})
                if (usedFallback) {
                    Text("Preview mode result", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        scope.launch {
                            val file = File(context.filesDir, "exports/tryon_${System.currentTimeMillis()}.jpg")
                            com.shadow306.app.core.engine.imaging.BitmapUtils.saveJpeg(bmp, file)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.action_save))
                }
            }
        }
    }
}
