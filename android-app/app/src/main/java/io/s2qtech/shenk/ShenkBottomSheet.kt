package io.s2qtech.shenk

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

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
    explicitDismissOnly: Boolean = false,
    content: @Composable () -> Unit,
) {
    val maxContentHeight = LocalConfiguration.current.screenHeightDp.dp * SHEET_MAX_HEIGHT_FRACTION
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val dismissWithAnimation = remember(sheetState, onDismissRequest) {
        {
            scope.launch {
                sheetState.hide()
                if (!sheetState.isVisible) onDismissRequest()
            }
            Unit
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier,
        sheetGesturesEnabled = !explicitDismissOnly,
        containerColor = containerColor,
        properties = ModalBottomSheetProperties(
            shouldDismissOnBackPress = true,
            shouldDismissOnClickOutside = !explicitDismissOnly,
        ),
        dragHandle = {
            if (explicitDismissOnly) {
                ExplicitDismissDragHandle(onDismiss = dismissWithAnimation)
            } else {
                BottomSheetDefaults.DragHandle()
            }
        },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExplicitDismissDragHandle(onDismiss: () -> Unit) {
    val dismissThreshold = with(LocalDensity.current) { 34.dp.toPx() }
    var downwardDrag by remember { mutableFloatStateOf(0f) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .pointerInput(dismissThreshold) {
                detectVerticalDragGestures(
                    onDragStart = { downwardDrag = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        downwardDrag = (downwardDrag + dragAmount).coerceAtLeast(0f)
                    },
                    onDragCancel = { downwardDrag = 0f },
                    onDragEnd = {
                        if (downwardDrag >= dismissThreshold) onDismiss()
                        downwardDrag = 0f
                    },
                )
            }
            .semantics {
                contentDescription = "向下拖动关闭"
                customActions = listOf(
                    CustomAccessibilityAction("关闭") {
                        onDismiss()
                        true
                    },
                )
            }
            .testTag("shenk-sheet-drag-handle"),
        contentAlignment = Alignment.Center,
    ) {
        BottomSheetDefaults.DragHandle()
    }
}

private const val SHEET_MAX_HEIGHT_FRACTION = 2f / 3f
