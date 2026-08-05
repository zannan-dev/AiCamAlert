package com.example.aicamalert

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.ui.tooling.preview.Preview
import com.example.aicamalert.ui.theme.AiCamAlertTheme
import org.osmdroid.config.Configuration

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // OSMdroid Configuration - Performance Optimizations
        val osmConfig = Configuration.getInstance()
        osmConfig.load(this, getSharedPreferences("osm_pref", MODE_PRIVATE))
        
        // Increase performance
        osmConfig.tileDownloadThreads = 8 // Default is 2, 8 makes loading much faster
        osmConfig.tileDownloadMaxQueueSize = 80 // Larger download queue for fast panning
        osmConfig.cacheMapTileCount = 128 // Increased memory cache (from 20 to 128) for smooth panning & instant tile lookup
        osmConfig.tileFileSystemCacheMaxBytes = 500L * 1024 * 1024 // 500MB disk cache
        
        // Set User-Agent AFTER load to ensure it's not overwritten
        osmConfig.userAgentValue = "AiCamAlertProject/${packageName}"
        
        enableEdgeToEdge()
        setContent {
            var darkTheme by remember { mutableStateOf(true) }
            AiCamAlertTheme(darkTheme = darkTheme) {
                CameraListScreen(
                    darkTheme = darkTheme,
                    onThemeToggle = { darkTheme = !darkTheme }
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Dark Mode")
@Composable
fun DarkPreview() {
    AiCamAlertTheme(darkTheme = true) {
        CameraListScreen(darkTheme = true, onThemeToggle = {})
    }
}

@Preview(showBackground = true, name = "Light Mode")
@Composable
fun LightPreview() {
    AiCamAlertTheme(darkTheme = false) {
        CameraListScreen(darkTheme = false, onThemeToggle = {})
    }
}
