package io.s2qtech.shenk

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class ShenkStateTone {
    NEUTRAL,
    PROGRESS,
    OFFLINE,
    WARNING,
    ERROR,
    SUCCESS,
}

/** One visual and accessibility language for non-content states across Shenk. */
@Composable
fun ShenkStatePanel(
    title: String,
    message: String,
    tone: ShenkStateTone,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    compact: Boolean = false,
    contained: Boolean = true,
) {
    val colors = stateColors(tone)
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 14.dp else 18.dp,
                vertical = if (compact) 13.dp else 17.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 11.dp else 14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(
                color = colors.iconContainer,
                contentColor = colors.icon,
                shape = RoundedCornerShape(14.dp),
            ) {
                if (tone == ShenkStateTone.PROGRESS) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(9.dp).size(if (compact) 18.dp else 21.dp),
                        strokeWidth = 2.dp,
                        color = colors.icon,
                    )
                } else {
                    Icon(
                        imageVector = stateIcon(tone),
                        contentDescription = null,
                        modifier = Modifier.padding(8.dp).size(if (compact) 20.dp else 23.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    title,
                    style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.title,
                )
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.body,
                )
                if (actionLabel != null && onAction != null) {
                    TextButton(onClick = onAction, modifier = Modifier.align(Alignment.End)) {
                        Text(actionLabel)
                    }
                }
            }
        }
    }

    val stateModifier = modifier.semantics {
        if (tone in setOf(ShenkStateTone.PROGRESS, ShenkStateTone.OFFLINE, ShenkStateTone.ERROR)) {
            liveRegion = LiveRegionMode.Polite
        }
    }
    if (contained) {
        Surface(
            modifier = stateModifier,
            color = colors.container,
            contentColor = colors.title,
            shape = RoundedCornerShape(if (compact) 18.dp else 22.dp),
            border = BorderStroke(1.dp, colors.border),
        ) { content() }
    } else {
        Surface(
            modifier = stateModifier,
            color = Color.Transparent,
            contentColor = colors.title,
        ) { content() }
    }
}

private data class StateColors(
    val container: Color,
    val iconContainer: Color,
    val icon: Color,
    val title: Color,
    val body: Color,
    val border: Color,
)

@Composable
private fun stateColors(tone: ShenkStateTone): StateColors {
    val scheme = MaterialTheme.colorScheme
    val base = when (tone) {
        ShenkStateTone.NEUTRAL -> Triple(scheme.surfaceContainerHigh, scheme.secondaryContainer, scheme.onSecondaryContainer)
        ShenkStateTone.PROGRESS -> Triple(scheme.primaryContainer.copy(alpha = 0.58f), scheme.primaryContainer, scheme.primary)
        ShenkStateTone.OFFLINE -> Triple(scheme.tertiaryContainer.copy(alpha = 0.62f), scheme.tertiaryContainer, scheme.tertiary)
        ShenkStateTone.WARNING -> Triple(scheme.tertiaryContainer.copy(alpha = 0.72f), scheme.tertiaryContainer, scheme.onTertiaryContainer)
        ShenkStateTone.ERROR -> Triple(scheme.errorContainer.copy(alpha = 0.72f), scheme.errorContainer, scheme.error)
        ShenkStateTone.SUCCESS -> Triple(scheme.primaryContainer.copy(alpha = 0.72f), scheme.primaryContainer, scheme.primary)
    }
    return StateColors(
        container = base.first,
        iconContainer = base.second,
        icon = base.third,
        title = if (tone == ShenkStateTone.ERROR) scheme.onErrorContainer else scheme.onSurface,
        body = if (tone == ShenkStateTone.ERROR) scheme.onErrorContainer.copy(alpha = 0.78f) else scheme.secondary,
        border = when (tone) {
            ShenkStateTone.ERROR -> scheme.error.copy(alpha = 0.22f)
            ShenkStateTone.WARNING, ShenkStateTone.OFFLINE -> scheme.tertiary.copy(alpha = 0.2f)
            else -> scheme.outlineVariant
        },
    )
}

private fun stateIcon(tone: ShenkStateTone): ImageVector = when (tone) {
    ShenkStateTone.NEUTRAL -> Icons.Rounded.Inbox
    ShenkStateTone.PROGRESS -> Icons.Rounded.HourglassTop
    ShenkStateTone.OFFLINE -> Icons.Rounded.CloudOff
    ShenkStateTone.WARNING -> Icons.Rounded.WarningAmber
    ShenkStateTone.ERROR -> Icons.Rounded.ErrorOutline
    ShenkStateTone.SUCCESS -> Icons.Rounded.CheckCircleOutline
}
