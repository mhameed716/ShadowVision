package com.shadow306.app.ui.faceswap

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
fun FaceSwapScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engine = remember { ServiceLocator.provideFaceEngine(context) }
    val repo = remember { ServiceLocator.provideModelRepository(context) }
    val packState by repo.state.collectAsState()

    var targetUri by remember { mutableStateOf<Uri?>(null) }
    var sourceUri by remember { mutableStateOf<Uri?>(null) }
    var colorMatch by remember { mutableStateOf(true) }
    var feather by remember { mutableFloatStateOf(40f) }
    var resultBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var processing by remember { mutableStateOf(false) }

    val targetBitmap = rememberBitmapFromUri(targetUri)
    val sourceBitmap = rememberBitmapFromUri(sourceUri)

    val isFallback = packState.perModel.values.any {
        it.spec.features.contains(com.shadow306.app.core.modeldownload.ModelSpec.Feature.FACE_SWAP) &&
                it.status != com.shadow306.app.core.modeldownload.ModelDownloadState.Status.INSTALLED
    }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.faceswap_title)) }) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isFallback) FallbackBanner()

            Text(stringResource(R.string.faceswap_target), style = MaterialTheme.typography.titleMedium)
            ImagePickerSlot(bitmap = targetBitmap, hint = stringResource(R.string.faceswap_target_hint), onPick = { targetUri = it })

            Text(stringResource(R.string.faceswap_source), style = MaterialTheme.typography.titleMedium)
            ImagePickerSlot(bitmap = sourceBitmap, hint = stringResource(R.string.faceswap_source_hint), onPick = { sourceUri = it })

            Text(stringResource(R.string.faceswap_options), style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.faceswap_color_match), modifier = Modifier.weight(1f))
                Switch(checked = colorMatch, onCheckedChange = { colorMatch = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.faceswap_feather), modifier = Modifier.weight(1f))
                Text("${feather.toInt()}%")
            }
            Slider(value = feather, onValueChange = { feather = it }, valueRange = 0f..100f)

            Button(
                onClick = {
                    val t = targetBitmap ?: return@Button
                    val s = sourceBitmap ?: return@Button
                    scope.launch {
                        processing = true
                        val res = engine.swap(t, s, com.shadow306.app.core.engine.face.FaceEngine.Params(colorMatch, feather.toInt()), if (isFallback) EngineMode.FALLBACK else EngineMode.AUTO)
                        resultBitmap = res.bitmap
                        processing = false
                    }
                },
                enabled = targetBitmap != null && sourceBitmap != null && !processing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (processing) stringResource(R.string.state_processing) else stringResource(R.string.faceswap_swap))
            }

            resultBitmap?.let { bmp ->
                ImagePickerSlot(bitmap = bmp, hint = "", onPick = {})
                Button(
                    onClick = {
                        scope.launch {
                            val file = java.io.File(context.filesDir, "exports/faceswap_${System.currentTimeMillis()}.jpg")
                            com.shadow306.app.core.engine.imaging.BitmapUtils.saveJpeg(bmp, file)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.action_save)) }
            }
        }
    }
}
