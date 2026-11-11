package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.BrandAccent
import com.example.ui.theme.BrandPrimary
import com.example.ui.theme.BrandSecondary
import kotlin.math.cos
import kotlin.math.sin

/**
 * An animated 3D Isometric Holographic AI Sphere
 * with orbital rings, depth shadows, and pulsing neural core.
 */
@Composable
fun ThreeDAiOrb(
    isThinking: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 90.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "3d_orb")

    // Rotation angle for 3D orbital rings
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isThinking) 2400 else 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbital_rotation"
    )

    // Breathing pulse for core
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isThinking) 600 else 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "core_pulse"
    )

    // Wave ring radius expansion
    val waveProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_expansion"
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = pulseScale
                scaleY = pulseScale
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val baseRadius = this.size.minDimension * 0.32f

            // Outer Depth Glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        BrandPrimary.copy(alpha = if (isThinking) 0.5f else 0.25f),
                        BrandSecondary.copy(alpha = if (isThinking) 0.3f else 0.1f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius * 1.8f
                ),
                radius = baseRadius * 1.8f,
                center = center
            )

            // Expanding ripple wave when thinking
            if (isThinking) {
                val rippleRadius = baseRadius * (1f + waveProgress * 0.7f)
                val rippleAlpha = (1f - waveProgress) * 0.6f
                drawCircle(
                    color = BrandSecondary.copy(alpha = rippleAlpha),
                    radius = rippleRadius,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            // 3D Orbital Ring 1 (tilted)
            val ringRadiusX = baseRadius * 1.45f
            val ringRadiusY = baseRadius * 0.55f
            val rad1 = Math.toRadians(rotationAngle.toDouble())

            val ring1Path = Path().apply {
                for (deg in 0..360 step 8) {
                    val angle = Math.toRadians(deg.toDouble())
                    val x = center.x + (ringRadiusX * cos(angle) * cos(rad1) - ringRadiusY * sin(angle) * sin(rad1)).toFloat()
                    val y = center.y + (ringRadiusX * cos(angle) * sin(rad1) + ringRadiusY * sin(angle) * cos(rad1)).toFloat()
                    if (deg == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
            drawPath(
                path = ring1Path,
                brush = Brush.sweepGradient(
                    colors = listOf(BrandPrimary, BrandSecondary, BrandAccent, BrandPrimary),
                    center = center
                ),
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )

            // 3D Orbital Ring 2 (counter-tilted)
            val rad2 = Math.toRadians((-rotationAngle * 1.4).toDouble())
            val ring2Path = Path().apply {
                for (deg in 0..360 step 8) {
                    val angle = Math.toRadians(deg.toDouble())
                    val x = center.x + (ringRadiusX * cos(angle) * cos(rad2) + ringRadiusY * sin(angle) * sin(rad2)).toFloat()
                    val y = center.y + (-ringRadiusX * cos(angle) * sin(rad2) + ringRadiusY * sin(angle) * cos(rad2)).toFloat()
                    if (deg == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
            drawPath(
                path = ring2Path,
                brush = Brush.sweepGradient(
                    colors = listOf(BrandSecondary, AccentEmerald, BrandPrimary, BrandSecondary),
                    center = center
                ),
                style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
            )

            // 3D Volumetric Core Sphere with directional lighting
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.95f),
                        BrandSecondary,
                        BrandPrimary,
                        Color(0xFF1E1B4B)
                    ),
                    center = Offset(center.x - baseRadius * 0.35f, center.y - baseRadius * 0.35f),
                    radius = baseRadius
                ),
                radius = baseRadius,
                center = center
            )

            // Specular Highlight Reflection (3D Glass Look)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.85f), Color.Transparent),
                    center = Offset(center.x - baseRadius * 0.38f, center.y - baseRadius * 0.38f),
                    radius = baseRadius * 0.45f
                ),
                radius = baseRadius * 0.45f,
                center = Offset(center.x - baseRadius * 0.38f, center.y - baseRadius * 0.38f)
            )
        }
    }
}

/**
 * 3D Isometric Document Page Stack Visualizer
 * Gives documents an authentic physical layered paper depth look.
 */
@Composable
fun ThreeDPageStack(
    pageCount: Long,
    modifier: Modifier = Modifier
) {
    val displayLayers = minOf(4, maxOf(2, (pageCount / 3).toInt()))

    Box(
        modifier = modifier
            .width(54.dp)
            .height(60.dp),
        contentAlignment = Alignment.Center
    ) {
        for (i in displayLayers downTo 1) {
            val offsetVal = (displayLayers - i) * 3
            Surface(
                modifier = Modifier
                    .offset(x = offsetVal.dp, y = (-offsetVal).dp)
                    .width(42.dp)
                    .height(52.dp)
                    .shadow(
                        elevation = (i * 2).dp,
                        shape = RoundedCornerShape(8.dp),
                        spotColor = BrandPrimary.copy(alpha = 0.4f)
                    ),
                shape = RoundedCornerShape(8.dp),
                color = if (i == 1) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (i == 1) 0.6f else 0.3f),
                            BrandPrimary.copy(alpha = 0.4f)
                        )
                    )
                )
            ) {
                if (i == 1) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = BrandPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Premium 3D HD Layered Card Container
 * Features multi-layered gradient border, specular 3D light reflection, and deep elevation.
 */
@Composable
fun ThreeDCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    highlightColor: Color = BrandPrimary,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 6.dp,
                shape = shape,
                ambientColor = highlightColor.copy(alpha = 0.15f),
                spotColor = highlightColor.copy(alpha = 0.35f)
            )
            .border(
                width = 1.2.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.4f),
                        highlightColor.copy(alpha = 0.6f),
                        BrandSecondary.copy(alpha = 0.3f),
                        Color.Transparent
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(400f, 400f)
                ),
                shape = shape
            )
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        content()
    }
}
