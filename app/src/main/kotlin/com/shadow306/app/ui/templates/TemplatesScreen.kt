package com.shadow306.app.ui.templates

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.shadow306.app.R
import com.shadow306.app.core.data.db.TemplateItem
import com.shadow306.app.di.ServiceLocator
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesScreen(
    onBack: () -> Unit,
    onUseTemplate: (TemplateItem) -> Unit
) {
    val context = LocalContext.current
    val store = remember { ServiceLocator.provideTemplateStore(context) }
    val templates by store.templatesFlow.collectAsState(emptyList())
    val scope = rememberCoroutineScope()

    var pendingDelete by remember { mutableStateOf<TemplateItem?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                store.addFromFile(
                    // copy uri to temp first inside store.addFromFile expects File, but we have Uri path
                    // We'll reuse TemplateUseCase logic quickly
                    run {
                        val temp = java.io.File(context.cacheDir, "tmpl_${System.currentTimeMillis()}.jpg")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            temp.outputStream().use { out -> input.copyTo(out) }
                        }
                        temp
                    },
                    name = "Template ${templates.size + 1}",
                    category = TemplateItem.Category.OTHER
                )
            }
        }
    }

    LaunchedEffect(Unit) { store.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.templates_title)) }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(stringResource(R.string.templates_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.templates_add))
            }
            Spacer(Modifier.height(12.dp))

            if (templates.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.templates_empty), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.templates_empty_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(templates) { item ->
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onUseTemplate(item) }
                        ) {
                            Column {
                                AsyncImage(
                                    model = item.thumbFile,
                                    contentDescription = item.name,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(3f / 4f)
                                        .clip(RoundedCornerShape(16.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(item.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1)
                                    if (!item.builtin) {
                                        TextButton(onClick = { pendingDelete = item }) {
                                            Text(stringResource(R.string.action_delete), style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.templates_delete_title)) },
            text = { Text(stringResource(R.string.templates_delete_body, item.name)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { store.delete(item.id) }
                    pendingDelete = null
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}
