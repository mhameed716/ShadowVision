package com.shadow306.app.ui.video

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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import com.shadow306.app.core.engine.video.VideoEngine
import com.shadow306.app.di.ServiceLocator
import com.shadow306.app.ui.common.FallbackBanner
import com.shadow306.app.ui.common.ImagePickerSlot
import com.shadow306.app.ui.common.rememberBitmapFromUri
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engine = remember { ServiceLocator.provideVideoEngine(context) }
    val repo = remember { ServiceLocator.provideModelRepository(context) }
    val packState by repo.state.collectAsState()

    var sourceUri by remember { mutableStateOf<Uri?>(null) }
    var prompt by remember { mutableStateOf("") }
    var duration by remember { mutableFloatStateOf(60f) }
    var motion by remember { mutableFloatStateOf(50f) }
    var progress by remember { mutableStateOf<VideoEngine.Progress?>(null) }
    var resultFile by remember { mutableStateOf<java.io.File?>(null) }
    var processing by remember { mutableStateOf(false) }

    val sourceBitmap = rememberBitmapFromUri(sourceUri)

    val isFallback = packState.perModel.values.any {
        it.spec.features.contains(com.shadow306.app.core.modeldownload.ModelSpec.Feature.VIDEO_GEN) &&
                it.status != com.shadow306.app.core.modeldownload.ModelDownloadState.Status.INSTALLED
    }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.video_title)) }) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isFallback) FallbackBanner()

            Text(stringResource(R.string.video_source), style = MaterialTheme.typography.titleMedium)
            ImagePickerSlot(bitmap = sourceBitmap, hint = stringResource(R.string.video_need_image), onPick = { sourceUri = it })

            Text(stringResource(R.string.video_prompt), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                placeholder = { Text(stringResource(R.string.video_prompt_hint)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            Text(stringResource(R.string.video_duration) + ": ${duration.toInt()}s", style = MaterialTheme.typography.titleSmall)
            Slider(value = duration, onValueChange = { duration = it }, valueRange = 5f..60f, steps = 10)

            Text(stringResource(R.string.video_motion) + ": ${motion.toInt()}%", style = MaterialTheme.typography.titleSmall)
            Slider(value = motion, onValueChange = { motion = it }, valueRange = 0f..100f)

            Text(stringResource(R.string.video_trick_explainer, 4, duration.toInt()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            progress?.let { p ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    when (p) {
                        is VideoEngine.Progress.Translating -> Text(stringResource(R.string.video_stage_prepare))
                        is VideoEngine.Progress.Translated -> Text(stringResource(R.string.video_translated, p.english), style = MaterialTheme.typography.bodySmall)
                        is VideoEngine.Progress.Preparing -> Text(p.step)
                        is VideoEngine.Progress.Generating -> {
                            Text(stringResource(R.string.video_stage_generate, p.total))
                            LinearProgressIndicator(progress = { p.generated / p.total.toFloat().coerceAtLeast(1f) }, modifier = Modifier.fillMaxWidth())
                        }
                        is VideoEngine.Progress.Interpolating -> {
                            Text(stringResource(R.string.video_stage_interpolate, 24))
                            LinearProgressIndicator(progress = { p.current / p.total.toFloat().coerceAtLeast(1f) }, modifier = Modifier.fillMaxWidth())
                        }
                        is VideoEngine.Progress.Extending -> Text(stringResource(R.string.video_stage_extend, p.targetSec))
                        is VideoEngine.Progress.Encoding -> {
                            Text(stringResource(R.string.video_stage_encode))
                            LinearProgressIndicator(progress = { p.percent / 100f }, modifier = Modifier.fillMaxWidth())
                        }
                        is VideoEngine.Progress.Done -> Text(stringResource(R.string.video_result_ready))
                        is VideoEngine.Progress.Error -> Text(p.message, color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Button(
                onClick = {
                    val src = sourceBitmap ?: return@Button
                    if (prompt.isBlank()) return@Button
                    scope.launch {
                        processing = true
                        resultFile = null
                        engine.generate(
                            src,
                            VideoEngine.Params(prompt = prompt, durationSec = duration.toInt(), motionStrength = motion.toInt()),
                            if (isFallback) EngineMode.FALLBACK else EngineMode.AUTO
                        ).collect { prog ->
                            progress = prog
                            if (prog is VideoEngine.Progress.Done) {
                                resultFile = prog.file
                                processing = false
                            }
                            if (prog is VideoEngine.Progress.Error) {
                                processing = false
                            }
                        }
                    }
                },
                enabled = sourceBitmap != null && prompt.isNotBlank() && !processing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (processing) stringResource(R.string.state_processing) else stringResource(R.string.video_generate))
            }

            resultFile?.let { file ->
                Text(stringResource(R.string.video_result_meta, file.name, 24, com.shadow306.app.core.util.Format.bytes(file.length())), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
