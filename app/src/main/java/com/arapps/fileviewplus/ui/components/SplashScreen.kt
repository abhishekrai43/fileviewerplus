package com.arapps.fileviewplus.ui.components

import android.content.res.Configuration
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

    // Subtle, premium gradient
    val bgStart = if (isDark) Color(0xFF061018) else Color(0xFFF3F7FF)
    val bgMid = if (isDark) Color(0xFF07202A) else Color(0xFFF0F6FF)
    val bgEnd = if (isDark) Color(0xFF021018) else Color(0xFFEDEFF8)

    // gentle animated shift for the gradient direction
    val infiniteTransition = rememberInfiniteTransition()
    val gradientShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    // Lottie composition (optional)
    val composition by rememberLottieComposition(LottieCompositionSpec.Asset("splash_lottie.json"))
    val lottieProgress by animateLottieCompositionAsState(
        composition = composition,
        iterations = 1,
        isPlaying = true,
        speed = 1f
    )

    // Scale + float animation for the emblem to give premium feel
    val emblemTransition = rememberInfiniteTransition()
    val emblemScale by emblemTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(animation = tween(1800, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse)
    )
    val emblemOffsetY by emblemTransition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(animation = tween(1800, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse)
    )

    // rotate messages
    var msgIndex by remember { mutableStateOf(0) }
    LaunchedEffect(scanningMessages) {
        if (scanningMessages.isEmpty()) return@LaunchedEffect
        while (true) {
            delay(1400)
            msgIndex = (msgIndex + 1) % scanningMessages.size
        }
    }

    // finish when Lottie completes (or after minDisplayMillis if no Lottie) — with fade-out
    val isLeaving = remember { mutableStateOf(false) }
    val fadeMs = 300L
    LaunchedEffect(lottieProgress, composition) {
        if (composition != null) {
            if (lottieProgress >= 0.999f) {
                // wait a bit then start fade
                delay(minDisplayMillis)
                isLeaving.value = true
                delay(fadeMs)
                onFinish()
            }
        } else {
            // no Lottie composition — show for minimal time then fade and finish
            delay(minDisplayMillis + 400)
            isLeaving.value = true
            delay(fadeMs)
            onFinish()
        }
    }

    // accent colors
    val accentA = if (isDark) Color(0xFF66D9FF) else Color(0xFF1976D2)
    val accentB = if (isDark) Color(0xFF9C7DFF) else Color(0xFF6A1B9A)
    val textPrimary = if (isDark) Color(0xFFF3F6F9) else Color(0xFF071127)
    val textSecondary = if (isDark) Color(0xFFB9C6D6) else Color(0xFF516876)

    // animate displayed progress smoothly when lottie present
    val displayedProgress by animateFloatAsState(targetValue = (if (composition != null) lottieProgress else 0f), animationSpec = tween(600))

    // Accessibility
    val semanticsModifier = Modifier.semantics {
        contentDescription = "Splash screen: $title"
    }

    val targetAlpha by remember { derivedStateOf { if (isLeaving.value) 0f else 1f } }
    val alpha = animateFloatAsState(targetValue = targetAlpha, animationSpec = tween(durationMillis = 300))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(alpha.value)
            .background(
                Brush.linearGradient(
                    colors = listOf(bgStart, bgMid, bgEnd),
                    start = Offset(0f, gradientShift * 800f),
                    end = Offset(gradientShift * 800f, 0f)
                )
            )
            .then(semanticsModifier),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 28.dp)
        ) {
            // Emblem with subtle floating + scale
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .offset(y = emblemOffsetY.dp)
                    .then(Modifier),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .size(150.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .shadow(12.dp, RoundedCornerShape(32.dp))
                        .graphicsLayer { scaleX = emblemScale; scaleY = emblemScale },
                    color = Color.Transparent
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(accentA, accentB),
                                    start = Offset(0f, 0f),
                                    end = Offset(220f, 220f)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (composition != null) {
                            LottieAnimation(
                                composition = composition,
                                progress = { lottieProgress },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(18.dp)
                            )
                        } else {
                            Text(
                                text = "FF",
                                color = Color.White,
                                fontSize = 48.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Title + byline
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = title,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textPrimary
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Secure. Fast. Enterprise-ready.",
                    fontSize = 13.sp,
                    color = textSecondary
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // small decorative colored dots
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

            // polished progress bar — determinate if Lottie present, otherwise indeterminate
            if (composition != null) {
                // determinate custom progress bar (avoids deprecated overloads)
                DeterminateProgressBar(
                    progress = displayedProgress.coerceIn(0f, 1f),
                    modifier = Modifier
                        .width(260.dp)
                        .height(8.dp)
                        .clip(CircleShape),
                    trackColor = if (isDark) Color(0xFF04151C) else Color(0xFFEAF4FF),
                    progressColor = accentA
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "${(displayedProgress * 100).roundToInt()}%",
                    fontSize = 13.sp,
                    color = textSecondary
                )
            } else {
                CircularProgressIndicator(modifier = Modifier.size(44.dp), strokeWidth = 3.dp, color = accentA)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // subtle copyright / version line to the bottom
            val ctx = LocalContext.current
            // PackageInfo.versionName is nullable; coerce to empty string to avoid nullable-call sites
            val version = try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "" } catch (_: Exception) { "" }
            Text(
                text = "© A.R.Apps ${if (version.isNotBlank()) "• v$version" else ""}",
                fontSize = 11.sp,
                color = textSecondary,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

@Composable
private fun DeterminateProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    trackColor: Color = Color.LightGray,
    progressColor: Color = MaterialTheme.colorScheme.primary
) {
    val animated by animateFloatAsState(targetValue = progress)
    Canvas(modifier = modifier) {
        val corner = size.height / 2f
        // draw track
        drawRoundRect(color = trackColor, cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner), size = size)
        // draw progress
        val progWidth = size.width * animated
        if (progWidth > 0f) {
            drawRoundRect(color = progressColor, cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner), size = androidx.compose.ui.geometry.Size(progWidth, size.height))
        }
    }
}
