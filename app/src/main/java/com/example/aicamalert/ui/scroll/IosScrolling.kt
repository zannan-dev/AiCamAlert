package com.example.aicamalert.ui.scroll

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Resist pulls beyond an edge, then spring back without changing the scroll position. */
@Composable
fun rememberScrollBounce(horizontal: Boolean = false): Modifier {
    val scope = rememberCoroutineScope()
    val limit = with(LocalDensity.current) { 76.dp.toPx() }
    var displacement by remember { mutableFloatStateOf(0f) }
    var rebound by remember { mutableStateOf<Job?>(null) }
    val connection = remember(horizontal, limit) {
        object : NestedScrollConnection {
            fun Offset.scrollAxis() = if (horizontal) x else y
            fun Velocity.flingAxis() = if (horizontal) x else y
            fun offset(amount: Float) = if (horizontal) Offset(amount, 0f) else Offset(0f, amount)
            fun velocity(amount: Float) = if (horizontal) Velocity(amount, 0f) else Velocity(0f, amount)
            fun pull(movement: Float) {
                val raw = displacement * limit / (limit - abs(displacement)).coerceAtLeast(1f)
                val next = raw + movement * .55f
                displacement = limit * next / (limit + abs(next))
            }
            fun settle(releaseVelocity: Float = 0f) {
                rebound?.cancel()
                rebound = scope.launch {
                    val carriedVelocity = (releaseVelocity * .22f).coerceIn(-limit * 9f, limit * 9f)
                    val outwardLimit = (limit - abs(displacement)).coerceAtLeast(0f) * 8f
                    val initialVelocity = if (displacement * carriedVelocity > 0f)
                        carriedVelocity.coerceIn(-outwardLimit, outwardLimit) else carriedVelocity
                    animate(displacement, 0f, initialVelocity = initialVelocity,
                        animationSpec = spring(dampingRatio = .86f, stiffness = 155f, visibilityThreshold = .25f)) {
                        value, _ -> displacement = value
                    }
                    displacement = 0f
                }
            }
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val movement = available.scrollAxis()
                if (movement == 0f) return Offset.Zero
                rebound?.cancel()
                if (displacement * movement < 0f) {
                    val raw = displacement * limit / (limit - abs(displacement)).coerceAtLeast(1f)
                    val consumed = movement.coerceIn(-abs(raw) / .55f, abs(raw) / .55f)
                    pull(consumed)
                    return offset(consumed)
                }
                return Offset.Zero
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val movement = available.scrollAxis()
                if (movement == 0f) return Offset.Zero
                rebound?.cancel()
                pull(movement)
                return offset(movement)
            }
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (abs(displacement) < .5f) return Velocity.Zero
                settle(available.flingAxis())
                return velocity(available.flingAxis())
            }
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (abs(available.flingAxis()) > 100f) {
                    settle(available.flingAxis())
                    return velocity(available.flingAxis())
                }
                if (abs(displacement) > .5f && rebound?.isActive != true) settle()
                return Velocity.Zero
            }
        }
    }
    return Modifier.nestedScroll(connection).graphicsLayer {
        if (horizontal) translationX = displacement else translationY = displacement
    }
}

/** Consistent gradual deceleration with a small, quiet tail rather than an abrupt stop. */
@Composable
fun rememberSmoothFlingBehavior(): FlingBehavior {
    val decay = remember { exponentialDecay<Float>(frictionMultiplier = .82f, absVelocityThreshold = 18f) }
    return remember(decay) {
        object : FlingBehavior {
            override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
                if (abs(initialVelocity) < 18f) return 0f
                var previous = 0f
                var remaining = 0f
                AnimationState(initialValue = 0f, initialVelocity = initialVelocity).animateDecay(decay) {
                    val distance = value - previous
                    val consumed = scrollBy(distance)
                    previous = value
                    if (abs(distance - consumed) > .5f) {
                        remaining = velocity
                        cancelAnimation()
                    }
                }
                return remaining
            }
        }
    }
}
