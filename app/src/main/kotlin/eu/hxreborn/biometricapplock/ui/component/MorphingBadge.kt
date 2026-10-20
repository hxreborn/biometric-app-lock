package eu.hxreborn.biometricapplock.ui.component

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.graphics.shapes.Morph
import eu.hxreborn.biometricapplock.R
import eu.hxreborn.biometricapplock.ui.util.rememberReducedMotion
import kotlin.math.floor

private const val SPIN_MS = 8_000
private const val FULL_TURN = 360f
private const val SHAPE_MILLIS = 3_200
private const val MORPH_SHARE = 0.55f

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val LogoPolygons =
    listOf(
        MaterialShapes.Cookie9Sided,
        MaterialShapes.Sunny,
        MaterialShapes.Cookie12Sided,
        MaterialShapes.Clover8Leaf,
    )

private class LogoMotion(
    val rotation: State<Float>,
    val cycle: State<Float>,
) {
    val morphs = LogoPolygons.indices.map { Morph(LogoPolygons[it].normalized(), LogoPolygons[(it + 1) % LogoPolygons.size].normalized()) }
    val scaleFactor = rotationSafeScale(LogoPolygons)
    private val path = Path()
    private val scaleMatrix = Matrix()
    private val spinMatrix = Matrix()
    private val easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    fun DrawScope.currentPath(): Path {
        val progress = cycle.value
        val whole = floor(progress)
        val index = Math.floorMod(whole.toInt(), morphs.size)
        val segment = ((progress - whole) / MORPH_SHARE).coerceIn(0f, 1f)
        val shape = fitted(morphs[index].toPath(easing.transform(segment), path), size, scaleFactor, scaleMatrix)
        spinMatrix.reset()
        spinMatrix.translate(center.x, center.y)
        spinMatrix.rotateZ(rotation.value)
        spinMatrix.translate(-center.x, -center.y)
        shape.transform(spinMatrix)
        return shape
    }
}

@Composable
private fun rememberLogoMotion(): LogoMotion {
    val rotation: State<Float>
    val cycle: State<Float>
    if (rememberReducedMotion()) {
        rotation = remember { mutableFloatStateOf(0f) }
        cycle = remember { mutableFloatStateOf(0f) }
    } else {
        val infinite = rememberInfiniteTransition(label = "logo")
        rotation =
            infinite.animateFloat(
                initialValue = 0f,
                targetValue = FULL_TURN,
                animationSpec = infiniteRepeatable(tween(SPIN_MS, easing = LinearEasing)),
                label = "rotation",
            )
        cycle =
            infinite.animateFloat(
                initialValue = 0f,
                targetValue = LogoPolygons.size.toFloat(),
                animationSpec = infiniteRepeatable(tween(SHAPE_MILLIS * LogoPolygons.size, easing = LinearEasing)),
                label = "morph",
            )
    }
    return remember(rotation, cycle) { LogoMotion(rotation, cycle) }
}

@Composable
fun MorphingLogo(modifier: Modifier = Modifier) {
    val motion = rememberLogoMotion()
    Box(
        modifier
            .graphicsLayer {}
            .drawWithCache {
                val bounds = size.toRect()
                val layerPaint = Paint()
                onDrawWithContent {
                    val outside = with(motion) { currentPath() }
                    outside.fillType = PathFillType.EvenOdd
                    outside.addRect(bounds)
                    drawIntoCanvas { it.saveLayer(bounds, layerPaint) }
                    drawContent()
                    drawPath(outside, Color.Black, blendMode = BlendMode.Clear)
                    drawIntoCanvas { it.restore() }
                }
            },
    ) {
        Box(Modifier.fillMaxSize().background(colorResource(R.color.ic_launcher_background)))
        Image(
            painterResource(R.mipmap.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
fun MorphingBadge(
    size: Dp,
    color: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val motion = rememberLogoMotion()
    Box(
        modifier =
            modifier.size(size).drawWithCache {
                onDrawBehind { drawPath(with(motion) { currentPath() }, color) }
            },
        contentAlignment = Alignment.Center,
        content = content,
    )
}
