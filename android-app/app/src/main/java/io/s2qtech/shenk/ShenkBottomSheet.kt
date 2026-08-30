package io.s2qtech.shenk

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Shared sizing contract for every phone bottom sheet.
 *
 * Short content wraps naturally. Long content is capped at two thirds of the
 * screen and must provide its own vertical scrolling inside this bounded area.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShenkModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable () -> Unit,
) {
    val maxContentHeight = LocalConfiguration.current.screenHeightDp.dp * SHEET_MAX_HEIGHT_FRACTION
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier,
        containerColor = containerColor,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxContentHeight)
                .testTag("shenk-sheet-content"),
        ) {
            content()
        }
    }
}

private const val SHEET_MAX_HEIGHT_FRACTION = 2f / 3f
