package com.example.aicamalert

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import com.example.aicamalert.viewmodel.CameraViewModel
import com.example.aicamalert.ui.theme.AiCamAlertTheme
import org.osmdroid.config.Configuration
import com.example.aicamalert.ui.components.PermissionSetupGate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // OSMdroid Configuration - Performance Optimizations
        val osmConfig = Configuration.getInstance()
        osmConfig.load(this, getSharedPreferences("osm_pref", MODE_PRIVATE))
        
        // Respect the tile server's modest concurrency limit.
        osmConfig.tileDownloadThreads = 2
        osmConfig.tileDownloadMaxQueueSize = 100
        osmConfig.cacheMapTileCount = 256 // Increased memory cache (from 128 to 256) for instant tile retention on zoom out
        osmConfig.tileFileSystemCacheMaxBytes = 500L * 1024 * 1024 // 500MB disk cache
        
        // The custom tile source sends this value instead of OSMdroid's
        // normalized com.example.* package ID.
        @Suppress("DEPRECATION")
        val version = packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0"
        osmConfig.userAgentValue =
            "AiCamAlert/$version (+https://github.com/zannan-dev/AiCamAlert)"
        
        enableEdgeToEdge()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        setContent {
            val darkTheme = isSystemInDarkTheme()
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
            AiCamAlertTheme(darkTheme = darkTheme) {
                PermissionSetupGate(onSetupCompleted = {
                    ViewModelProvider(this)[CameraViewModel::class.java].toggleBackgroundRadar(true)
                }) {
                    CameraListScreen(darkTheme = darkTheme)
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Dark Mode")
@Composable
fun DarkPreview() {
    AiCamAlertTheme(darkTheme = true) {
        CameraListScreen(darkTheme = true)
    }
}

@Preview(showBackground = true, name = "Light Mode")
@Composable
fun LightPreview() {
    AiCamAlertTheme(darkTheme = false) {
        CameraListScreen(darkTheme = false)
    }
}
