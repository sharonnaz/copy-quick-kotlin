package com.example.easybackup

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

/** Soft tinted circular well for leading icons — no drop shadow. */
@Composable
fun AccentIconWell(
    icon: ImageVector,
    tint: Color,
    size: androidx.compose.ui.unit.Dp = 48.dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        tint.copy(alpha = 0.16f),
                        tint.copy(alpha = 0.08f),
                    ),
                ),
            )
            .border(1.dp, tint.copy(alpha = 0.14f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size * 0.42f),
        )
    }
}

/** Leading icon badge used across rows and sheets. */
@Composable
fun NeumorphicIconBadge(icon: ImageVector, tint: Color, size: androidx.compose.ui.unit.Dp = 44.dp) {
    AccentIconWell(icon = icon, tint = tint, size = size)
}

/** A large tappable action tile for the home screen (Backup Photos / Backup Videos). */
@Composable
fun ActionTile(title: String, subtitle: String, icon: ImageVector, tint: Color, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium),
        label = "actionTileScale",
    )
    NeumorphicSurface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interaction,
                indication = null,
            ) {
                Haptics.tap(haptics)
                onClick()
            },
        cornerRadius = 22.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AccentIconWell(icon = icon, tint = tint, size = 52.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Theme.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    subtitle,
                    fontSize = 13.sp,
                    color = Theme.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = Theme.textSecondary.copy(alpha = 0.4f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Circular device-storage gauge. */
@Composable
fun StorageGauge(usedFraction: Float, inverted: Boolean = false) {
    val target = usedFraction.coerceIn(0f, 1f)
    var introPlayed by remember { mutableStateOf(false) }
    var displayTarget by remember { mutableStateOf(0f) }
    LaunchedEffect(target) {
        if (!introPlayed && target > 0f) {
            introPlayed = true
            displayTarget = 0f
            // One frame at 0 so the ease-out starts cleanly.
            kotlinx.coroutines.yield()
            displayTarget = target
        } else if (introPlayed) {
            displayTarget = target
        }
    }
    val fill by animateFloatAsState(
        targetValue = displayTarget,
        animationSpec = tween(durationMillis = 1050, easing = FastOutSlowInEasing),
        label = "storageFill",
    )
    val well = if (inverted) {
        listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.10f))
    } else {
        listOf(Theme.gaugeWell, Theme.base)
    }
    val ring = if (inverted) Color.White.copy(alpha = 0.22f) else Theme.hairlineDark
    val track = if (inverted) Color.White.copy(alpha = 0.28f) else Theme.shadowDark.copy(alpha = 0.18f)
    val label = if (inverted) Color.White else Theme.textPrimary
    val caption = if (inverted) Color.White.copy(alpha = 0.72f) else Theme.textSecondary

    Box(
        modifier = Modifier
            .size(86.dp)
            .clip(CircleShape)
            .background(Brush.verticalGradient(well))
            .border(1.dp, ring, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(66.dp)) {
            val stroke = Stroke(width = 7.5.dp.toPx(), cap = StrokeCap.Round)
            drawArc(
                color = track,
                startAngle = -90f, sweepAngle = 360f, useCenter = false,
                style = stroke,
            )
            if (inverted) {
                drawArc(
                    color = Color.White,
                    startAngle = -90f,
                    sweepAngle = fill * 360f,
                    useCenter = false,
                    style = stroke,
                )
            } else {
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(Theme.accentBlue, Theme.accentPurple, Theme.accentBlue),
                    ),
                    startAngle = -90f,
                    sweepAngle = fill * 360f,
                    useCenter = false,
                    style = stroke,
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (target > 0f) "${(target * 100).roundToInt()}%" else "",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = label,
            )
            Text("used", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = caption)
        }
    }
}

/** Home-screen action tile — vertical, matches iOS HomeActionCard. */
@Composable
fun HomeActionCard(
    title: String,
    countText: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium),
        label = "homeActionScale",
    )
    NeumorphicSurface(
        modifier = modifier
            .scale(scale)
            .clickable(
                interactionSource = interaction,
                indication = null,
            ) {
                Haptics.tap(haptics)
                onClick()
            },
        cornerRadius = 26.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 168.dp)
                .padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                AccentIconWell(icon = icon, tint = tint, size = 48.dp)
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(tint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.OpenInNew,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(11.dp),
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Theme.textPrimary,
                )
                Text(
                    countText,
                    fontSize = 13.sp,
                    color = Theme.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "Back up",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = tint,
                    )
                    Icon(
                        Icons.Filled.ArrowForward,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun HomeMeshBackground() {
    Box(Modifier.fillMaxSize().background(Theme.base)) {
        Box(
            Modifier
                .size(320.dp)
                .offset(x = (-110).dp, y = (-220).dp)
                .blur(72.dp)
                .background(Theme.accentBlue.copy(alpha = 0.28f), CircleShape),
        )
        Box(
            Modifier
                .size(280.dp)
                .align(Alignment.TopEnd)
                .offset(x = 40.dp, y = (-80).dp)
                .blur(80.dp)
                .background(Theme.accentPurple.copy(alpha = 0.16f), CircleShape),
        )
        Box(
            Modifier
                .size(220.dp)
                .align(Alignment.Center)
                .offset(x = 40.dp, y = 180.dp)
                .blur(60.dp)
                .background(Theme.accentBlue.copy(alpha = 0.10f), CircleShape),
        )
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.3.sp,
        color = Theme.textSecondary,
    )
}

@Composable
fun SupportLinkRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable {
                Haptics.tap(haptics)
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AccentIconWell(icon = icon, tint = Theme.accentPurple, size = 36.dp)
        Text(
            title,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = Theme.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = Theme.textSecondary.copy(alpha = 0.55f),
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
fun LibraryChip(icon: ImageVector, text: String, tint: Color = Theme.textPrimary) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(Color.White.copy(alpha = 0.78f))
            .border(1.dp, Theme.hairlineDark, RoundedCornerShape(100.dp))
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(12.dp))
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = tint)
    }
}

@Composable
fun HomeInfoCard(icon: ImageVector, tint: Color, title: String, subtitle: String) {
    NeumorphicSurface(Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AccentIconWell(icon = icon, tint = tint, size = 44.dp)
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Theme.textPrimary)
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    color = Theme.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun AppearLift(delayMs: Int = 0, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        shown = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(420),
        label = "appearAlpha",
    )
    val offset by animateFloatAsState(
        targetValue = if (shown) 0f else 14f,
        animationSpec = spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMedium),
        label = "appearOffset",
    )
    Box(
        Modifier
            .alpha(alpha)
            .offset(y = offset.dp),
    ) { content() }
}

/**
 * Selectable folder card for the Android multi-select backup flow.
 */
@Composable
fun FolderSelectCard(
    folder: FolderState,
    tint: Color,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    val isCopying = !folder.done && !folder.quotaBlocked && folder.fraction > 0f
    val selectable = folder.isWaiting || folder.quotaBlocked
    val haptics = LocalHapticFeedback.current
    var wasDone by remember { mutableStateOf(folder.done) }

    LaunchedEffect(folder.done) {
        if (folder.done && !wasDone) Haptics.success(haptics)
        wasDone = folder.done
    }

    val badgeIcon = when {
        folder.done -> Icons.Filled.Check
        folder.quotaBlocked -> Icons.Filled.Lock
        isCopying -> Icons.Filled.CloudUpload
        else -> Icons.Filled.Folder
    }
    val badgeTint = when {
        folder.done -> Theme.accentGreen
        folder.quotaBlocked -> Color(0xFFFF9800)
        else -> tint
    }

    val shape = RoundedCornerShape(22.dp)

    // Order matters: shadow(shape) → clip → fill → border → clickable
    // so selection never paints a square shadow/ripple.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (selected && selectable) 10.dp else 8.dp,
                shape = shape,
                clip = false,
                ambientColor = if (selected && selectable) {
                    tint.copy(alpha = 0.16f)
                } else {
                    Theme.depth.copy(alpha = 0.07f)
                },
                spotColor = if (selected && selectable) {
                    tint.copy(alpha = 0.22f)
                } else {
                    Theme.depth.copy(alpha = 0.10f)
                },
            )
            .clip(shape)
            .background(
                if (selected && selectable) {
                    Brush.verticalGradient(
                        listOf(
                            Theme.cardLift,
                            tint.copy(alpha = 0.10f),
                        ),
                    )
                } else {
                    Brush.verticalGradient(
                        listOf(Theme.cardLift, Theme.card),
                    )
                },
            )
            .border(
                width = if (selected && selectable) 2.dp else 1.dp,
                brush = if (selected && selectable) {
                    Brush.linearGradient(listOf(tint.copy(alpha = 0.65f), tint.copy(alpha = 0.35f)))
                } else {
                    Brush.verticalGradient(listOf(Theme.hairline, Theme.hairlineDark))
                },
                shape = shape,
            )
            .then(
                if (selectable) {
                    Modifier.clickable {
                        Haptics.tap(haptics)
                        onToggle()
                    }
                } else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                AccentIconWell(icon = badgeIcon, tint = badgeTint, size = 48.dp)
                Column(Modifier.weight(1f)) {
                    Text(
                        folder.name,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Theme.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        folderStatusText(folder, isCopying),
                        fontSize = 13.sp,
                        color = when {
                            folder.done -> Theme.accentGreen
                            folder.quotaBlocked -> Color(0xFFFF9800)
                            else -> Theme.textSecondary
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                when {
                    folder.done -> {
                        Text(
                            "Done",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Theme.accentGreen,
                            modifier = Modifier
                                .clip(RoundedCornerShape(100.dp))
                                .background(Theme.accentGreen.copy(alpha = 0.12f))
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                    isCopying -> {
                        Text(
                            "${(folder.fraction * 100).toInt()}%",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = tint,
                        )
                    }
                    selectable -> {
                        SelectionCheck(selected = selected, tint = tint)
                    }
                }
            }

            if (isCopying) {
                CopyProgressBar(fraction = folder.fraction, tint = tint)
            }
        }
    }
}

@Composable
private fun SelectionCheck(selected: Boolean, tint: Color) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(if (selected) tint else Color.Transparent)
            .border(
                width = 2.dp,
                color = if (selected) tint else Theme.textSecondary.copy(alpha = 0.35f),
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Selected",
                tint = Color.White,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}



/**
 * Accent slide-to-confirm CTA.
 * Appears only when folders are selected — no idle/disabled footer chrome.
 * Gesture inspired by iPhone power-off; look is a single brand action pill.
 */
@Composable
fun SlideToPowerOffControl(
    accent: Color,
    onCompleted: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "slide to back up",
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val thumbSize = 56.dp
    val trackHeight = 64.dp
    val inset = 4.dp
    val thumbSizePx = with(density) { thumbSize.toPx() }
    val insetPx = with(density) { inset.toPx() }
    val trigger = 0.88f

    var trackWidthPx by remember { mutableStateOf(1f) }
    val dragX = remember { Animatable(0f) }
    var didThresholdHaptic by remember { mutableStateOf(false) }

    val travel = max(trackWidthPx - thumbSizePx - insetPx * 2f, 1f)
    val progress = (dragX.value / travel).coerceIn(0f, 1f)
    val thumbScale by animateFloatAsState(
        targetValue = if (dragX.value > 2f) 1.04f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
        label = "ctaThumbScale",
    )
    val labelAlpha by animateFloatAsState(
        targetValue = (1f - progress * 1.55f).coerceIn(0f, 1f),
        label = "ctaLabelAlpha",
    )

    val infinite = rememberInfiniteTransition(label = "ctaShimmer")
    val shimmer by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(trackHeight)
            .onSizeChanged { trackWidthPx = it.width.toFloat() }
            .shadow(
                elevation = 14.dp,
                shape = RoundedCornerShape(percent = 50),
                ambientColor = accent.copy(alpha = 0.25f),
                spotColor = accent.copy(alpha = 0.35f),
            )
            .clip(RoundedCornerShape(percent = 50))
            .background(
                Brush.horizontalGradient(
                    listOf(accent, accent.copy(alpha = 0.88f)),
                ),
            ),
    ) {
        // Brighter edge as thumb advances
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceAtLeast(0.02f))
                .background(Color.White.copy(alpha = 0.12f)),
        )

        Text(
            text = label,
            style = TextStyle(
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.3.sp,
                brush = Brush.linearGradient(
                    colorStops = arrayOf(
                        0f to Color.White.copy(alpha = 0.72f),
                        (shimmer - 0.08f).coerceIn(0f, 1f) to Color.White.copy(alpha = 0.72f),
                        shimmer.coerceIn(0f, 1f) to Color.White,
                        (shimmer + 0.08f).coerceIn(0f, 1f) to Color.White.copy(alpha = 0.72f),
                        1f to Color.White.copy(alpha = 0.72f),
                    ),
                    start = Offset.Zero,
                    end = Offset(Float.POSITIVE_INFINITY, 0f),
                ),
            ),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(start = 8.dp)
                .alpha(labelAlpha),
        )

        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = with(density) { (insetPx + travel * progress).toDp() })
                .size(thumbSize)
                .scale(thumbScale)
                .shadow(8.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.2f), spotColor = Color.Black.copy(alpha = 0.25f))
                .clip(CircleShape)
                .background(Color.White)
                .pointerInput(travel) {
                    var totalDx = 0f
                    detectHorizontalDragGestures(
                        onDragStart = {
                            totalDx = 0f
                            didThresholdHaptic = false
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            totalDx += dragAmount
                            val clamped = totalDx.coerceIn(0f, travel)
                            scope.launch { dragX.snapTo(clamped) }
                            val p = clamped / travel
                            if (p > trigger && !didThresholdHaptic) {
                                didThresholdHaptic = true
                                Haptics.tick(haptics)
                            } else if (p <= trigger - 0.05f) {
                                didThresholdHaptic = false
                            }
                        },
                        onDragEnd = {
                            scope.launch {
                                didThresholdHaptic = false
                                if (dragX.value / travel >= trigger) {
                                    Haptics.success(haptics)
                                    onCompleted()
                                }
                                dragX.animateTo(
                                    0f,
                                    spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMedium),
                                )
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                didThresholdHaptic = false
                                dragX.animateTo(0f, tween(220))
                            }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.ArrowForward,
                contentDescription = label,
                tint = accent,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun CopyProgressBar(fraction: Float, tint: Color) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(280),
        label = "copyBar",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(100.dp))
            .background(Theme.shadowDark.copy(alpha = 0.10f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animated.coerceAtLeast(0.04f))
                .clip(RoundedCornerShape(100.dp))
                .background(Brush.horizontalGradient(listOf(tint, tint.copy(alpha = 0.7f)))),
        )
    }
}

private fun folderStatusText(folder: FolderState, isCopying: Boolean): String {
    val n = folder.total
    return when {
        folder.done -> "${if (n == 1) "1 item" else "$n items"} · backed up"
        folder.quotaBlocked -> "${folder.copied} of $n · unlock to continue"
        isCopying -> "${folder.copied} of $n · copying…"
        n == 1 -> "1 item"
        else -> "$n items"
    }
}
