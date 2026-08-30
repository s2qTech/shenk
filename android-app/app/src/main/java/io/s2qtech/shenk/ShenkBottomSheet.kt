package io.s2qtech.shenk

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
        sheetGesturesEnabled = true,
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

@Composable
private fun ExplicitDismissDragHandle(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .semantics {
                contentDescription = "下滑关闭"
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
        Surface(
            modifier = Modifier.width(46.dp).height(5.dp),
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
            content = {},
        )
    }
}

private const val SHEET_MAX_HEIGHT_FRACTION = 2f / 3f
