package pl.marrod.localmark.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Spacing tokens derived from the Tailwind `spacing` config.
 *
 * | Tailwind token | Value  | Usage hint                          |
 * |----------------|--------|-------------------------------------|
 * | unit           |  4 dp  | Base grid unit                      |
 * | stack-gap      |  8 dp  | Vertical gap between stacked items  |
 * | gutter         | 12 dp  | Internal padding / column gutter    |
 * | edge-margin    | 16 dp  | Screen-edge horizontal margin       |
 * | fab-offset     | 24 dp  | FAB distance from screen edges      |
 */
object Spacing {
    /** 4 dp – base grid unit */
    val unit        = 4.dp

    /** 8 dp – vertical gap between stacked items */
    val stackGap    = 8.dp

    /** 12 dp – internal padding / column gutter */
    val gutter      = 12.dp

    /** 16 dp – horizontal margin from screen edges */
    val edgeMargin  = 16.dp

    /** 24 dp – FAB distance from screen edges */
    val fabOffset   = 24.dp
}

