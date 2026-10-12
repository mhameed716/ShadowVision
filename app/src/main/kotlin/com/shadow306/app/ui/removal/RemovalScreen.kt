package com.shadow306.app.ui.removal

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.shadow306.app.core.engine.onnx.EngineMode
import com.shadow306.app.di.ServiceLocator
import com.shadow306.app.ui.common.FallbackBanner
import com.shadow306.app.ui.common.ImagePickerSlot
import com.shadow306.app.ui.common.rememberBitmapFromUri
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemovalScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engine = remember { ServiceLocator.provideRemovalEngine(context) }
    val repo = remember { ServiceLocator.provideModelRepository(context) }
    val packState by repo.state.collectAsState()

    var sourceUri by remember { mutableStateOf<Uri?>(null) }
    var sensitivity by remember { mutableFloatStateOf(50f) }
    var skinMatch by remember { mutableStateOf(true) }
    var resultBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var maskBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var processing by remember { mutableStateOf(false) }

    val sourceBitmap = rememberBitmapFromUri(sourceUri)

    val isFallback = packState.perModel.values.any {
        it.spec.features.contains(com.shadow306.app.core.modeldownload.ModelSpec.Feature.INPAINT) &&
                it.status != com.shadow306.app.core.modeldownload.ModelDownloadState.Status.INSTALLED
    }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.removal_title)) }) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isFallback) FallbackBanner()

            Text(stringResource(R.string.removal_source), style = MaterialTheme.typography.titleMedium)
            ImagePickerSlot(bitmap = sourceBitmap, hint = stringResource(R.string.removal_need_image), onPick = { sourceUri = it })

            Text(stringResource(R.string.removal_auto), style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.removal_mask_strength), modifier = Modifier.weight(1f))
                Text("${sensitivity.toInt()}%")
            }
            Slider(value = sensitivity, onValueChange = { sensitivity = it }, valueRange = 0f..100f)

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.removal_skin_match), modifier = Modifier.weight(1f))
                Switch(checked = skinMatch, onCheckedChange = { skinMatch = it })
            }

            Button(
                onClick = {
                    val src = sourceBitmap ?: return@Button
                    scope.launch {
                        processing = true
                        val res = engine.remove(src, null, com.shadow306.app.core.engine.body.RemovalEngine.Params(sensitivity.toInt(), skinMatch), if (isFallback) EngineMode.FALLBACK else EngineMode.AUTO)
                        resultBitmap = res.bitmap
                        maskBitmap = res.mask
                        processing = false
                    }
                },
                enabled = sourceBitmap != null && !processing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (processing) stringResource(R.string.state_processing) else stringResource(R.string.removal_run))
            }

            maskBitmap?.let { mask ->
                Text(stringResource(R.string.removal_mask_preview), style = MaterialTheme.typography.titleSmall)
                ImagePickerSlot(bitmap = mask, hint = "", onPick = {})
            }

            resultBitmap?.let { bmp ->
                ImagePickerSlot(bitmap = bmp, hint = "", onPick = {})
                Button(
                    onClick = {
                        scope.launch {
                            val file = java.io.File(context.filesDir, "exports/removal_${System.currentTimeMillis()}.jpg")
                            com.shadow306.app.core.engine.imaging.BitmapUtils.saveJpeg(bmp, file)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.action_save)) }
            }
        }
    }
}
