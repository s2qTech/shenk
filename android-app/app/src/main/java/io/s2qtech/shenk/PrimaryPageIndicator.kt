package io.s2qtech.shenk

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

/** Continuous position hint for the Calendar <- Today -> Training horizontal pager. */
@Composable
fun PagerDrivenPrimaryPageIndicator(
    pagerState: PagerState,
    modifier: Modifier = Modifier,
) {
    PrimaryPageIndicator(
        pagePosition = pagerState.currentPage + pagerState.currentPageOffsetFraction,
        modifier = modifier,
    )
}

@Composable
fun PrimaryPageIndicator(
    pagePosition: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
                .width(49.dp)
                .height(15.dp),
        ) {
            Row(
                modifier = Modifier.align(Alignment.Center).width(35.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(3) {
                    Box(
                        Modifier
                            .width(5.dp)
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    )
                }
            }
            Box(
                Modifier
                    .offset(x = 2.dp + primaryPageIndicatorTravelDp(pagePosition).dp, y = 5.dp)
                    .width(15.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.74f)),
            )
        }
    }
}

internal fun primaryPageIndicatorTravelDp(pagePosition: Float): Float =
    pagePosition.coerceIn(0f, 2f) * 15f
