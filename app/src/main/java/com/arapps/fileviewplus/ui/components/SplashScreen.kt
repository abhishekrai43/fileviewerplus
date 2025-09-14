package com.arapps.fileviewplus.ui.components

import android.content.res.Configuration
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.*
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun SplashScreen(
    onFinish: () -> Unit,
    title: String = "FileFlow Plus",
    scanningMessages: List<String> = listOf("Scanning Files...", "Preparing storage", "Checking permissions"),
    minDisplayMillis: Long = 1500L
) {
    val configuration = LocalConfiguration.current
    val isDark = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    // Background gradient (soft, premium)
    val bgStart = if (isDark) Color(0xFF0B1220) else Color(0xFFF7FBFF)
    val bgEnd = if (isDark) Color(0xFF04101A) else Color(0xFFE6F0FF)

    // subtle animated shift for the gradient
    val infiniteTransition = rememberInfiniteTransition()
    val gradientShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    // Lottie composition (optional). Keep iterations = 1 so it completes.
    val composition by rememberLottieComposition(LottieCompositionSpec.Asset("splash_lottie.json"))
    val lottieProgress by animateLottieCompositionAsState(
        composition = composition,
        iterations = 1,
        isPlaying = true,
        speed = 1f
    )

    // rotate messages
    var msgIndex by remember { mutableStateOf(0) }
    LaunchedEffect(scanningMessages) {
        if (scanningMessages.isEmpty()) return@LaunchedEffect
        while (true) {
            delay(1200)
            msgIndex = (msgIndex + 1) % scanningMessages.size
        }
    }

    // finish when Lottie completes (or after minDisplayMillis if no Lottie)
    LaunchedEffect(lottieProgress, composition) {
        if (composition != null) {
            if (lottieProgress >= 0.999f) {
                // lottie finished; wait a bit to ensure smooth UX then finish
                delay(minDisplayMillis)
                onFinish()
            }
        } else {
            // no Lottie composition — show for minimal time then finish
            delay(minDisplayMillis + 400)
            onFinish()
        }
    }

    // accent colors
    val accentA = if (isDark) Color(0xFF70D6FF) else Color(0xFF1E88E5)
    val accentB = if (isDark) Color(0xFF7C5CFF) else Color(0xFF6A1B9A)
    val textPrimary = if (isDark) Color(0xFFF3F6F9) else Color(0xFF071127)
    val textSecondary = if (isDark) Color(0xFFB9C6D6) else Color(0xFF516876)

    // UI
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(bgStart, bgEnd),
                    start = Offset(0f, gradientShift * 600f),
                    end = Offset(gradientShift * 600f, 0f)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 28.dp)
        ) {
            // App mark (rounded square with gradient and initials)
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(accentA, accentB),
                            start = Offset(0f, 0f),
                            end = Offset(200f, 200f)
                        )
                    )
                    .shadow(elevation = 8.dp, shape = RoundedCornerShape(28.dp))
            ) {
                // Lottie inside mark if available, otherwise initials
                if (composition != null) {
                    LottieAnimation(
                        composition = composition,
                        progress = { lottieProgress },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    )
                } else {
                    // Big initials as fallback
                    Text(
                        text = "FF",
                        color = Color.White,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Title + byline
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = title,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textPrimary
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    // small brand pill
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(color = accentA.copy(alpha = 0.14f))
                    ) {
                        Text(
                            text = "by A.R.Apps",
                            color = textSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // small decorative colored dots to show premium feel
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(accentA))
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(accentB))
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(accentA.copy(alpha = 0.7f)))
            }

            Spacer(modifier = Modifier.height(22.dp))

            // scanning message (rotating)
            Crossfade(targetState = msgIndex) { idx ->
                Text(
                    text = scanningMessages.getOrNull(idx) ?: "",
                    fontSize = 14.sp,
                    color = textSecondary
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // progress bar (reflects lottieProgress, or indeterminate if no lottie)
            if (composition != null) {
                LinearProgressIndicator(
                    progress = lottieProgress.coerceIn(0f, 1f),
                    modifier = Modifier
                        .width(220.dp)
                        .height(6.dp)
                        .clip(CircleShape),
                    trackColor = if (isDark) Color(0xFF03111A) else Color(0xFFE9F2FF),
                    color = accentA
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "${(lottieProgress * 100).roundToInt()}%",
                    fontSize = 13.sp,
                    color = textSecondary
                )
            } else {
                // fallback spinner + indeterminate progress
                CircularProgressIndicator(modifier = Modifier.size(48.dp), strokeWidth = 3.dp)
            }
        }
    }
}
