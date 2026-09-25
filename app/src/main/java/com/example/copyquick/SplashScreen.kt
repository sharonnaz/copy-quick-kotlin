package com.example.copyquick

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Cinematic splash matching iOS — holographic media transfer + single brand reveal.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val boot = remember { Animatable(0f) }
    val transfer = remember { Animatable(0f) }
    val driveFill = remember { Animatable(0f) }
    val brandProgress = remember { Animatable(0f) }
    val taglineOpacity = remember { Animatable(0f) }

    val flights = List(6) { remember { Animatable(0f) } }

    val infinite = rememberInfiniteTransition(label = "splashAmbient")
    val ambient by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3400), RepeatMode.Reverse),
        label = "ambient",
    )
    val pulse by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "pulse",
    )
    val filmShift by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 50f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "film",
    )
    val chevronTime by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "chevrons",
    )

    LaunchedEffect(Unit) {
        launch { boot.animateTo(1f, tween(750)) }
        delay(280)
        launch { transfer.animateTo(1f, tween(550)) }

        val delays = listOf(450L, 680L, 900L, 1120L, 1340L, 1560L)
        delays.forEachIndexed { i, d ->
            launch {
                delay(d)
                flights[i].animateTo(1f, tween(850))
            }
        }

        delay(900)
        launch { driveFill.animateTo(1f, tween(1550)) }

        delay(850 - 280) // ~0.85s from start relative to first delay already used — align to iOS ~0.85s total
        // Brand starts ~0.85s from appear; we've already waited 280+partial — schedule from start:
    }

    // Dedicated brand / exit timing from t=0
    LaunchedEffect(Unit) {
        delay(850)
        launch { brandProgress.animateTo(1f, tween(1050)) }
        delay(450)
        launch { taglineOpacity.animateTo(1f, tween(700)) }
    }

    // Cover with home base color, then hard-cut — never dissolve dark
    // splash over home (that made white card edges show before teal).
    val handoffCover = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(3000)
        handoffCover.animateTo(1f, tween(280))
        delay(70)
        onFinished()
    }

    val brand = "Copy Quick".toList()

    BoxWithConstraints(
        Modifier.fillMaxSize(),
    ) {
        val w = maxWidth
        val h = maxHeight
        val stageW = minOf(w * 0.92f, 380.dp)
        val stageWPx = with(LocalDensity.current) { stageW.toPx() }

        SplashBackdrop(w, h, ambient)

        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(h * 0.12f))

            Box(
                Modifier
                    .width(stageW)
                    .height(230.dp),
                contentAlignment = Alignment.Center,
            ) {
                // Transfer rail
                TransferRail(
                    width = stageW * 0.58f,
                    filmShift = filmShift,
                    chevronTime = chevronTime,
                    modifier = Modifier
                        .alpha(transfer.value)
                        .graphicsLayer {
                            scaleX = 0.2f + 0.8f * transfer.value
                        },
                )

                // Phone
                PhoneDevice(
                    boot = boot.value,
                    flights = flights.map { it.value },
                    modifier = Modifier
                        .offset(x = -stageW * 0.31f)
                        .alpha(boot.value)
                        .scale(0.78f + 0.22f * boot.value),
                )

                // Drive
                DriveDevice(
                    boot = boot.value,
                    driveFill = driveFill.value,
                    pulse = pulse,
                    modifier = Modifier
                        .offset(x = stageW * 0.31f)
                        .alpha(boot.value)
                        .scale((0.78f + 0.22f * boot.value) * (1f + pulse * 0.03f))
                        .shadow(
                            30.dp,
                            RoundedCornerShape(20.dp),
                            ambientColor = Theme.accentPurple.copy(alpha = 0.45f * driveFill.value),
                            spotColor = Theme.accentPurple.copy(alpha = 0.45f * driveFill.value),
                        ),
                )

                // Peel sheets
                flights.forEachIndexed { index, anim ->
                    PeelSheet(
                        progress = anim.value,
                        index = index,
                        stageWPx = stageWPx,
                    )
                }
            }

            Spacer(Modifier.height(44.dp))

            // Single brand — letter stagger
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 28.dp),
            ) {
                Row(horizontalArrangement = Arrangement.Center) {
                    brand.forEachIndexed { index, char ->
                        val threshold = index / max(brand.size - 1, 1).toFloat()
                        val local = ((brandProgress.value - threshold * 0.55f) / 0.45f).coerceIn(0f, 1f)
                        Text(
                            text = char.toString(),
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Bold,
                            color = Theme.textPrimary,
                            modifier = Modifier
                                .alpha(local)
                                .offset(y = 22.dp * (1f - local))
                                .scale(0.88f + 0.12f * local),
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                Box(
                    Modifier
                        .width(120.dp * brandProgress.value)
                        .height(2.dp)
                        .alpha(brandProgress.value)
                        .clip(CircleShape)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Theme.accentBlue.copy(alpha = 0f),
                                    Theme.accentBlue,
                                    Theme.accentPurple,
                                    Theme.accentPurple.copy(alpha = 0f),
                                ),
                            ),
                        ),
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    "Your library, kept off-device",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Theme.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .alpha(taglineOpacity.value)
                        .offset(y = 8.dp * (1f - taglineOpacity.value)),
                )
            }

            Spacer(Modifier.weight(1f))
        }

        Box(
            Modifier
                .fillMaxSize()
                .alpha(handoffCover.value)
                .background(Theme.base),
        )
    }
}

@Composable
private fun SplashBackdrop(w: Dp, h: Dp, ambient: Float) {
    Box(Modifier.fillMaxSize()) {
        // Light cinematic stage — same family as home mesh, richer wash
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(
                            Theme.base,
                            Color(0xFFEDF2F8),
                            Color(0xFFE6F0F5),
                        ),
                    ),
                ),
        )
        Box(
            Modifier
                .size(w * 0.95f)
                .offset(x = -w * 0.30f, y = -h * 0.24f + 18.dp * ambient)
                .blur(90.dp)
                .background(Theme.accentBlue.copy(alpha = 0.34f), CircleShape),
        )
        Box(
            Modifier
                .size(w * 0.88f)
                .align(Alignment.CenterEnd)
                .offset(x = w * 0.18f, y = h * 0.06f - 14.dp * ambient)
                .blur(100.dp)
                .background(Theme.accentPurple.copy(alpha = 0.22f), CircleShape),
        )
        Box(
            Modifier
                .size(w * 0.55f)
                .align(Alignment.BottomCenter)
                .offset(y = h * 0.06f)
                .blur(70.dp)
                .background(Theme.accentBlue.copy(alpha = 0.14f), CircleShape),
        )
    }
}

@Composable
private fun TransferRail(
    width: Dp,
    filmShift: Float,
    chevronTime: Float,
    modifier: Modifier = Modifier,
) {
    Box(modifier.width(width).height(54.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(54.dp)
                .blur(16.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Theme.accentBlue.copy(alpha = 0f),
                            Theme.accentBlue.copy(alpha = 0.35f),
                            Theme.accentPurple.copy(alpha = 0.40f),
                            Theme.accentPurple.copy(alpha = 0f),
                        ),
                    ),
                    CircleShape,
                ),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.06f))
                .border(
                    1.1.dp,
                    Brush.horizontalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.35f),
                            Theme.accentBlue.copy(alpha = 0.55f),
                            Theme.accentPurple.copy(alpha = 0.55f),
                            Color.White.copy(alpha = 0.15f),
                        ),
                    ),
                    CircleShape,
                ),
        ) {
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp)
                    .offset(x = (-filmShift).dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                repeat(12) { i ->
                    Box(
                        Modifier
                            .size(18.dp, 14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(tileColor(i)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (i % 2 == 0) Icons.Filled.Photo else Icons.Filled.Videocam,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(9.dp),
                        )
                    }
                }
            }
        }
        Canvas(Modifier.fillMaxWidth().height(28.dp)) {
            val w = size.width
            val midY = size.height / 2f
            for (i in 0 until 5) {
                var u = (chevronTime + i * 0.18f) % 1f
                if (u < 0f) u += 1f
                val x = w * u
                val alpha = sin(u * PI).toFloat()
                val path = Path().apply {
                    moveTo(x - 7f, midY - 5f)
                    lineTo(x, midY)
                    lineTo(x - 7f, midY + 5f)
                }
                drawPath(
                    path,
                    color = Color.White.copy(alpha = 0.15f + 0.65f * alpha),
                    style = Stroke(width = 1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
    }
}

@Composable
private fun PeelSheet(progress: Float, index: Int, stageWPx: Float) {
    val t = progress
    if (t <= 0.001f) return

    val startX = -stageWPx * 0.31f
    val midX = 0f
    val endX = stageWPx * 0.31f
    val midY = if (index % 2 == 0) -36f else 36f

    val pos = if (t < 0.35f) {
        val u = min(1f, t * 1.35f)
        Offset(startX + (midX - startX) * u, 0f + midY * u)
    } else {
        val u = max(0f, (t - 0.35f) / 0.65f)
        Offset(midX + (endX - midX) * u, midY + (0f - midY) * u)
    }

    val stretch = when {
        t < 0.2f -> 1f
        t < 0.55f -> 1f + (t - 0.2f) / 0.35f * 1.8f
        t < 0.8f -> 2.8f - (t - 0.55f) / 0.25f * 1.5f
        else -> max(0.35f, 1.3f - (t - 0.8f) / 0.2f * 0.95f)
    }
    val sy = if (t < 0.55f) 1f - t * 0.35f else 0.55f + (t - 0.55f) * 0.2f
    val opacity = when {
        t < 0.08f -> t / 0.08f
        t > 0.92f -> (1f - t) / 0.08f
        else -> 1f
    }
    val colors = cardColors(index)

    Box(
        Modifier
            .offset { androidx.compose.ui.unit.IntOffset(pos.x.toInt(), pos.y.toInt()) }
            .zIndex(50f + index)
            .graphicsLayer {
                scaleX = stretch
                scaleY = sy
                rotationZ = (1f - t) * (if (index % 2 == 0) -18f else 16f)
                alpha = opacity
            }
            .size(40.dp, 50.dp)
            .shadow(12.dp, RoundedCornerShape(8.dp), ambientColor = colors[0].copy(alpha = 0.55f), spotColor = colors[0].copy(alpha = 0.55f))
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.linearGradient(colors))
            .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (index % 2 == 0) Icons.Filled.Photo else Icons.Filled.Videocam,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(13.dp),
        )
    }
}

@Composable
private fun PhoneDevice(boot: Float, flights: List<Float>, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(86.dp, 150.dp)
            .shadow(16.dp, RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(listOf(Color(0xFF29304D), Color(0xFF141A29))),
            )
            .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(22.dp)),
    ) {
        Column(
            Modifier.fillMaxSize().padding(top = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .width(28.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f)),
            )
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                for (row in 0 until 3) {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        for (col in 0 until 2) {
                            val i = row * 2 + col
                            val flown = flights.getOrElse(i) { 0f }
                            val op = max(0.18f, 1f - flown * 0.85f)
                            Box(
                                Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(tileColor(i).copy(alpha = op)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    if (i % 2 == 0) Icons.Filled.Photo else Icons.Filled.Videocam,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.85f * op),
                                    modifier = Modifier.size(10.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DriveDevice(
    boot: Float,
    driveFill: Float,
    pulse: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(92.dp, 130.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(listOf(Color(0xFF2E385F), Color(0xFF171C33))),
            )
            .border(
                1.2.dp,
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.35f),
                        Theme.accentPurple.copy(alpha = 0.45f + 0.45f * driveFill),
                        Color.White.copy(alpha = 0.08f),
                    ),
                ),
                RoundedCornerShape(20.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.SdStorage,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(4) { row ->
                    val lit = driveFill * 4f > row
                    Box(
                        Modifier
                            .width(52.dp)
                            .height(7.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f)),
                    ) {
                        if (lit) {
                            Box(
                                Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth()
                                    .clip(CircleShape)
                                    .background(
                                        Brush.horizontalGradient(listOf(Theme.accentBlue, Theme.accentPurple)),
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun tileColor(i: Int): Color = when (i % 3) {
    0 -> Theme.accentBlue
    1 -> Theme.accentPurple
    else -> Theme.accentGreen
}

private fun cardColors(i: Int): List<Color> = when (i % 4) {
    0 -> listOf(Theme.accentBlue, Theme.accentBlue.copy(alpha = 0.55f))
    1 -> listOf(Theme.accentPurple, Theme.accentPurple.copy(alpha = 0.55f))
    2 -> listOf(Theme.accentGreen, Theme.accentBlue.copy(alpha = 0.55f))
    else -> listOf(Color(0xFF596BB8), Theme.accentPurple.copy(alpha = 0.6f))
}
