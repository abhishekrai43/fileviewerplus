package com.arapps.fileviewplus.ui.components


import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*

import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.*
import com.arapps.fileviewplus.R


@Composable
fun SplashScreen(onFinish: () -> Unit) {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.Asset("splash_lottie.json")
    )

    val progress by animateLottieCompositionAsState(
        composition = composition,
        isPlaying = true,
        iterations = 1,
        speed = 1f
    )

    // Check when the animation is complete
    LaunchedEffect(progress) {
        if (progress == 1f) {
            onFinish()
        }
    }

    LottieAnimation(
        composition = composition,
        progress = { progress },
        modifier = Modifier.size(200.dp)
    )
}

