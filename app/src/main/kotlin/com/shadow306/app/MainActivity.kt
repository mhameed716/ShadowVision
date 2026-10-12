package com.shadow306.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import com.shadow306.app.di.ServiceLocator
import com.shadow306.app.ui.nav.Routes
import com.shadow306.app.ui.nav.ShadowNavGraph
import com.shadow306.app.ui.theme.Shadow306Theme
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        var keepSplash = true
        splashScreen.setKeepOnScreenCondition { keepSplash }

        setContent {
            Shadow306Theme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val context = LocalContext.current
                    val navController = rememberNavController()
                    var startDestination by remember { mutableStateOf<String?>(null) }

                    LaunchedEffect(Unit) {
                        val prefs = ServiceLocator.providePrefs(context)
                        val repo = ServiceLocator.provideModelRepository(context)
                        val setupDone = prefs.setupDoneFlow.first()
                        repo.loadBundledManifest()
                        repo.refreshVerification()
                        val state = repo.state.value
                        startDestination = when {
                            !setupDone && state.installedCount == 0 -> Routes.SETUP
                            else -> Routes.HOME
                        }
                        keepSplash = false
                    }

                    if (startDestination != null) {
                        ShadowNavGraph(navController = navController, startDestination = startDestination!!)
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}
