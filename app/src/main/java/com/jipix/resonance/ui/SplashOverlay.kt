package com.jipix.resonance.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jipix.resonance.R
import com.jipix.resonance.ui.theme.WordmarkFont
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The launch animation: the mark settles in, then the wordmark writes itself out.
 *
 * The "writing" is a left-to-right clip rather than a real stroke animation —
 * getting the actual pen path would mean shipping the glyph outlines by hand. On
 * a joined script the reveal is indistinguishable from a pen at normal speed, and
 * it costs one clipRect instead of a vector timeline.
 *
 * Both the mark and the wordmark take the Material You primary, so the launch
 * picks up the wallpaper the same way the rest of the app does.
 */
@Composable
fun SplashOverlay(onFinished: () -> Unit) {
    val markAlpha = remember { Animatable(0f) }
    val markScale = remember { Animatable(0.82f) }
    val written = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { markAlpha.animateTo(1f, tween(durationMillis = 300)) }
        markScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            ),
        )
        written.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 780, easing = LinearOutSlowInEasing),
        )
        delay(280)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(R.drawable.ic_resonance_mark),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(76.dp)
                    .alpha(markAlpha.value)
                    .scale(markScale.value),
            )

            Text(
                text = "Resonance",
                fontFamily = WordmarkFont,
                fontSize = 44.sp,
                lineHeight = 56.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .drawWithContent {
                        clipRect(right = size.width * written.value) {
                            this@drawWithContent.drawContent()
                        }
                    },
            )
        }
    }
}
