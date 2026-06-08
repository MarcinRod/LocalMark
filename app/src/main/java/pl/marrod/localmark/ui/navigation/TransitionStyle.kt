package pl.marrod.localmark.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideIn
import androidx.compose.animation.slideOut
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry

/** Sealed interface that defines different styles of screen transition animations for navigation.
 * Each style specifies the duration and the enter/exit transitions for both forward navigation (push) and backward navigation (pop).
 */
sealed interface TransitionStyle {

    val duration: Int


    fun enter(): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition

    fun exit(): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition

    fun popEnter(): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition

    fun popExit(): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition

    // ── Style przejść ─────────────────────────────────────────────────────────

    object Default : TransitionStyle {
        override val duration: Int get() = 700

        override fun enter(): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
            {
                fadeIn(tween(duration))
            }

        override fun exit(): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
            {
                fadeOut(tween(duration))
            }

        override fun popEnter(): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
            {
                fadeIn(tween(duration))
            }

        override fun popExit(): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
            {
                fadeOut(tween(duration))
            }
    }


    object HorizontalSlide : TransitionStyle {
        override val duration: Int get() = 350

        override fun enter(): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
            {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    tween(duration)
                ) +
                        fadeIn(tween(duration))
            }

        override fun exit(): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
            {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    tween(duration)
                ) +
                        fadeOut(tween(duration))
            }

        override fun popEnter(): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
            {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    tween(duration)
                ) +
                        fadeIn(tween(duration))
            }

        override fun popExit(): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
            {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    tween(duration)
                ) +
                        fadeOut(tween(duration))
            }
    }


    object VerticalSlide : TransitionStyle {
        override val duration: Int get() = 500

        override fun enter(): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
            {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Up,
                    tween(duration)
                ) +
                        fadeIn(tween(duration))
            }

        override fun exit(): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
            {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Up,
                    tween(duration)
                ) +
                        fadeOut(tween(duration))
            }

        override fun popEnter(): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
            {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Down,
                    tween(duration)
                ) +
                        fadeIn(tween(duration))
            }

        override fun popExit(): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
            {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Down,
                    tween(duration)
                ) +
                        fadeOut(tween(duration))
            }
    }


    object HingeDoor : TransitionStyle {
        override val duration: Int get() = 750
        val springSpec: FiniteAnimationSpec<Float> =
            spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)

        override fun enter(): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
            {
                scaleIn(
                    animationSpec = tween(duration),
                    initialScale = 0f,
                    transformOrigin = TransformOrigin(0f, 0.5f)   // zawias na lewej krawędzi
                ) + fadeIn(tween(duration))
            }

        override fun exit(): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
            {
                scaleOut(
                    animationSpec = tween(duration),
                    targetScale = 0f,
                    transformOrigin = TransformOrigin(1f, 0.5f)   // składanie do prawej krawędzi
                ) + fadeOut(tween(duration))
            }

        override fun popEnter(): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
            {

                scaleIn(
                    animationSpec = springSpec,
                    initialScale = 0f,
                    transformOrigin = TransformOrigin(1f, 0.5f)   // zawias na prawej krawędzi
                ) + fadeIn(springSpec)
            }

        override fun popExit(): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
            {
                scaleOut(
                    animationSpec = springSpec,
                    targetScale = 0f,
                    transformOrigin = TransformOrigin(0f, 0.5f)   // składanie do lewej krawędzi
                ) + fadeOut(springSpec)
            }
    }


    object CornerSwing : TransitionStyle {
        override val duration: Int get() = 600

        override fun enter(): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
            {
                scaleIn(
                    animationSpec = tween(duration),
                    initialScale = 0f,
                    transformOrigin = TransformOrigin(1f, 1f)
                ) + slideIn(
                    animationSpec = tween(duration)
                ) { fullSize -> IntOffset(fullSize.width, 0) } +
                        fadeIn(tween(duration))
            }

        override fun exit(): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
            {
                scaleOut(
                    animationSpec = tween(duration),
                    targetScale = 0f,
                    transformOrigin = TransformOrigin(1f, 1f)
                ) + slideOut(
                    animationSpec = tween(duration)
                ) { fullSize -> IntOffset(-fullSize.width, 0) } +
                        fadeOut(tween(duration))
            }

        override fun popEnter(): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
            {
                scaleIn(
                    animationSpec = tween(duration),
                    initialScale = 0f,
                    transformOrigin = TransformOrigin(1f, 1f)
                ) + slideIn(
                    animationSpec = tween(duration)
                ) { fullSize -> IntOffset(-fullSize.width, 0) } +
                        fadeIn(tween(duration))
            }

        override fun popExit(): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
            {
                scaleOut(
                    animationSpec = tween(duration),
                    targetScale = 0f,
                    transformOrigin = TransformOrigin(1f, 1f)
                ) + slideOut(
                    animationSpec = tween(duration)
                ) { fullSize -> IntOffset(fullSize.width, 0) } +
                        fadeOut(tween(duration))
            }
    }
}

