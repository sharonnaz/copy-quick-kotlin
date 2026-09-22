package com.example.easybackup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Design tokens — mirrors iOS Theme.swift. Light teal + navy (mockup). */
object Theme {
    val base = Color(0xFFF3F5F8)
    val card = Color(0xFFFFFFFF)
    val cardLift = Color(0xFFFFFFFF)
    val gaugeWell = Color(0xFFE7F4F3)
    val shadowLight = Color(0xFFFFFFFF)
    val shadowDark = Color(0x1A1A2340)
    val accentBlue = Color(0xFF3DBAB2)
    val accentPurple = Color(0xFF3B4A9A)
    val accentGreen = Color(0xFF2EBF9A)
    val action = Color(0xFF3B4A9A)
    val onAction = Color(0xFFFFFFFF)
    val textPrimary = Color(0xFF2A3354)
    val textSecondary = Color(0xFF8A93A8)
    val hairline = Color(0xFFFFFFFF).copy(alpha = 0.95f)
    val hairlineDark = Color(0xFF2A3354).copy(alpha = 0.06f)
    val depth = Color(0xFF1A2340)
}

/**
 * Soft raised card: bright fill, top highlight edge, gentle depth shadow.
 * Avoids dual blur offset layers (those caused square shadows on Android).
 */
@Composable
fun NeumorphicSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 22.dp,
    inset: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape: Shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .then(
                if (inset) Modifier
                else Modifier.shadow(
                    elevation = 8.dp,
                    shape = shape,
                    clip = false,
                    ambientColor = Theme.depth.copy(alpha = 0.07f),
                    spotColor = Theme.depth.copy(alpha = 0.10f),
                )
            )
            .clip(shape)
            .then(
                if (inset) {
                    Modifier.background(Theme.base)
                } else {
                    Modifier.background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Theme.cardLift,
                                Theme.card,
                            ),
                        ),
                    )
                }
            )
            .border(
                width = 1.dp,
                brush = if (inset) {
                    Brush.verticalGradient(
                        listOf(Theme.hairlineDark, Theme.hairlineDark),
                    )
                } else {
                    Brush.verticalGradient(
                        colors = listOf(
                            Theme.hairline,
                            Theme.hairlineDark,
                        ),
                    )
                },
                shape = shape,
            ),
        content = content,
    )
}

/** Centralized haptic feedback so the copy flow feels responsive at every step. */
object Haptics {
    fun tap(haptics: HapticFeedback) = haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    fun tick(haptics: HapticFeedback) = haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    fun success(haptics: HapticFeedback) = haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    fun warning(haptics: HapticFeedback) = haptics.performHapticFeedback(HapticFeedbackType.LongPress)
}
