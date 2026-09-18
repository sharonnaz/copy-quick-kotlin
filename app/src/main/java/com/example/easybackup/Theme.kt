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

/** Neumorphic design tokens — mirrors the iOS app's Theme.swift. */
object Theme {
    val base = Color(0xFFE6E7EE)
    /** Lifted card fill — a touch brighter than the page so surfaces read clearly. */
    val card = Color(0xFFF8F9FC)
    val shadowLight = Color(0xFFFFFFFF)
    val shadowDark = Color(0x33000000)
    val accentBlue = Color(0xFF4F8EF7)
    val accentPurple = Color(0xFF6A6ADB)
    val accentGreen = Color(0xFF4ABF80)
    val textPrimary = Color(0xFF3A3A45)
    val textSecondary = Color(0xFF8A8A99)
    val hairline = Color(0xFFFFFFFF).copy(alpha = 0.85f)
    val hairlineDark = Color(0xFF000000).copy(alpha = 0.045f)
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
                    ambientColor = Color(0xFF1A1A2E).copy(alpha = 0.07f),
                    spotColor = Color(0xFF1A1A2E).copy(alpha = 0.10f),
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
                                Color(0xFFFFFFFF),
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
