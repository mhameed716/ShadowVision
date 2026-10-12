package com.shadow306.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.shadow306.app.R
import com.shadow306.app.di.ServiceLocator
import com.shadow306.app.ui.common.ModelPackCard
import com.shadow306.app.ui.common.ToolCard
import com.shadow306.app.ui.nav.Routes
import com.shadow306.app.ui.theme.Shadow306Theme

data class HomeTool(
    val route: String,
    val titleRes: Int,
    val descRes: Int,
    val icon: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val repo = remember { ServiceLocator.provideModelRepository(context) }
    val packState by repo.state.collectAsState()

    val tools = listOf(
        HomeTool(Routes.TRYON, R.string.home_tool_tryon, R.string.home_tool_tryon_desc, "👗"),
        HomeTool(Routes.FACESWAP, R.string.home_tool_faceswap, R.string.home_tool_faceswap_desc, "👥"),
        HomeTool(Routes.VIDEO, R.string.home_tool_video, R.string.home_tool_video_desc, "🎬"),
        HomeTool(Routes.TEMPLATES, R.string.home_tool_templates, R.string.home_tool_templates_desc, "📂"),
        HomeTool(Routes.REMOVAL, R.string.home_tool_removal, R.string.home_tool_removal_desc, "✂️"),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.home_title), style = MaterialTheme.typography.titleLarge)
                        Text(
                            stringResource(R.string.home_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(span = { GridItemSpan(2) }) {
                ModelPackCard(
                    state = packState,
                    onClick = { onNavigate(Routes.SETUP) }
                )
            }

            item(span = { GridItemSpan(2) }) {
                Spacer(Modifier.height(4.dp))
            }

            items(tools) { tool ->
                ToolCard(
                    title = stringResource(tool.titleRes),
                    desc = stringResource(tool.descRes),
                    icon = tool.icon,
                    onClick = { onNavigate(tool.route) }
                )
            }

            item(span = { GridItemSpan(2) }) {
                Column(Modifier.padding(top = 12.dp)) {
                    Text(
                        stringResource(R.string.home_privacy_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "100% offline • ONNX Runtime • No cloud",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}
