package com.jipix.resonance.ui.library

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp

/**
 * Adds to whatever bottom padding a Scaffold already worked out, rather than
 * replacing it — the two insets have different origins (system bars vs. the
 * mini player) and both have to be honoured.
 */
@Composable
fun PaddingValues.plusBottom(extra: Dp): PaddingValues {
    if (extra <= Dp.Hairline) return this
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(direction),
        top = calculateTopPadding(),
        end = calculateEndPadding(direction),
        bottom = calculateBottomPadding() + extra,
    )
}
