package com.example.aicamalert

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import com.example.aicamalert.alert.AlertSoundManager
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aicamalert.ui.theme.AiCamAlertTheme

class FullScreenAlertActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Allow Activity to display over Keyguard / Lock Screen and Turn Screen ON
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(KEYGUARD_SERVICE) as? android.app.KeyguardManager
            keyguardManager?.requestDismissKeyguard(this, null)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )

        enableEdgeToEdge()
        setContent {
            val camera by AlertSoundManager.activeAlert.collectAsState()
            androidx.compose.runtime.LaunchedEffect(camera) {
                if (camera == null) finish()
            }
            androidx.activity.compose.BackHandler {
                CameraProximityService.acknowledgeAlarm(this)
                finish()
            }
            val darkTheme = androidx.compose.foundation.isSystemInDarkTheme()
            androidx.compose.runtime.SideEffect {
                androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
            AiCamAlertTheme(darkTheme = darkTheme) {
                camera?.let { alert -> FullScreenAlertContent(
                    cameraName = alert.name,
                    district = alert.district,
                    distance = alert.distance,
                    darkTheme = darkTheme,
                    onDismiss = {
                        CameraProximityService.acknowledgeAlarm(this)
                        finish()
                    }
                ) }
            }
        }
    }
}

@Composable
fun FullScreenAlertContent(
    cameraName: String,
    district: String,
    distance: String,
    darkTheme: Boolean,
    onDismiss: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val amber = if (darkTheme) Color(0xFFFFB86B) else Color(0xFF9E4800)
    val amberSurface = if (darkTheme) Color(0xFF332418) else Color(0xFFFFEAD6)
    val background = if (darkTheme) Color(0xFF0C0E12) else Color(0xFFF8F9FC)
    val cardColor = if (darkTheme) Color(0xFF191C22) else Color.White
    val transition = rememberInfiniteTransition(label = "Camera warning halo")
    val haloProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
        label = "Expanding halo",
    )

    BoxWithConstraints(
        Modifier.fillMaxSize().background(background).safeDrawingPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        val compact = maxHeight < 600.dp
        Column(
            Modifier.widthIn(max = 520.dp).fillMaxSize().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("AiCam", color = scheme.onSurface, fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp, letterSpacing = (-0.5).sp)
                Surface(shape = CircleShape, color = amberSurface) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).background(amber, CircleShape))
                        Spacer(Modifier.width(7.dp))
                        Text("CAMERA AHEAD", color = amber, fontSize = 10.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                }
            }

            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(vertical = if (compact) 12.dp else 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // The symbol stays still and legible; only the surrounding halo moves.
                if (!compact) Box(
                    Modifier.size(172.dp)
                        .background(Brush.radialGradient(listOf(amber.copy(alpha = 0.10f), Color.Transparent))),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.matchParentSize().graphicsLayer {
                        scaleX = 0.72f + haloProgress * 0.28f
                        scaleY = scaleX
                        alpha = (1f - haloProgress) * 0.55f
                    }.border(1.dp, amber.copy(alpha = 0.35f), CircleShape))
                    Box(Modifier.fillMaxSize(0.82f).border(1.dp, amber.copy(alpha = 0.12f), CircleShape))
                    Surface(shape = RoundedCornerShape(if (compact) 24.dp else 32.dp),
                        color = amberSurface, modifier = Modifier.size(if (compact) 72.dp else 100.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null,
                                tint = amber, modifier = Modifier.size(if (compact) 32.dp else 44.dp))
                        }
                    }
                }
                Spacer(Modifier.height(if (compact) 12.dp else 20.dp))
                Text("Speed camera ahead", color = scheme.onSurface,
                    style = MaterialTheme.typography.headlineSmall, fontSize = if (compact) 18.sp else 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center, letterSpacing = (-0.6).sp)
                if (!compact) {
                    Spacer(Modifier.height(6.dp))
                    Text("Ease off. Drive safely.", color = scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(if (compact) 16.dp else 28.dp))
                AnimatedContent(
                    targetState = distance,
                    transitionSpec = { fadeIn(tween(280)) togetherWith fadeOut(tween(180)) },
                    label = "Distance update",
                ) { currentDistance ->
                    Text(currentDistance, color = scheme.onSurface,
                        fontSize = if (compact) 44.sp else 64.sp, lineHeight = if (compact) 52.sp else 72.sp,
                        fontWeight = FontWeight.Light, letterSpacing = (-2).sp,
                        textAlign = TextAlign.Center)
                }
                Text("away from your location", color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                Spacer(Modifier.height(if (compact) 20.dp else 32.dp))
                Surface(
                    Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = cardColor,
                    border = androidx.compose.foundation.BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.45f)),
                ) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(cameraName, color = scheme.onSurface, style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(6.dp))
                        Text("$district District", color = scheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                    }
                }
            }

            Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = scheme.onSurface,
                        contentColor = scheme.surface),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 18.dp),
                ) {
                    Icon(Icons.Default.CheckCircle, null, Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Stop alarm", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center) {
                    Icon(Icons.Default.PowerSettingsNew, null, Modifier.size(14.dp), tint = scheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    Text("Or press power to turn off the screen", color = scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f, fill = false))
                }
                Spacer(Modifier.height(6.dp))
                Text("Radar stays on for the next camera", color = scheme.onSurfaceVariant,
                    fontSize = 11.sp, textAlign = TextAlign.Center)
            }
        }
    }
}
