package com.adityadixit.applock.ui.lock

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.adityadixit.applock.R
import com.adityadixit.applock.domain.auth.CredentialRules.PATTERN_GRID_SIZE
import kotlin.math.hypot

/**
 * 3×3 pattern input. Emits ordered cell indices (row * 3 + col) on finger lift.
 * Like the system lock screen, skipping over an unused middle cell selects it automatically.
 *
 * The drawing stays visible until [resetToken] changes, so callers can show success/error.
 */
@Composable
fun PatternLock(
    enabled: Boolean,
    isError: Boolean,
    resetToken: Int,
    onPatternComplete: (List<Int>) -> Unit,
    modifier: Modifier = Modifier,
    gridSize: Dp = 280.dp,
) {
    val selected = remember { mutableStateListOf<Int>() }
    var pointer by remember { mutableStateOf<Offset?>(null) }
    val haptics = LocalHapticFeedback.current
    val currentOnComplete by rememberUpdatedState(onPatternComplete)
    val idleColor = MaterialTheme.colorScheme.onSurfaceVariant
    val activeColor =
        if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val description = stringResource(R.string.pattern_grid_description)

    LaunchedEffect(resetToken) { selected.clear() }

    Canvas(
        modifier = modifier
            .size(gridSize)
            .aspectRatio(1f)
            .semantics { contentDescription = description }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                fun select(position: Offset) {
                    val cell = cellAt(position, size.width.toFloat()) ?: return
                    if (cell in selected) return
                    selected.lastOrNull()?.let { last ->
                        middleCell(last, cell)?.takeIf { it !in selected }?.let(selected::add)
                    }
                    selected.add(cell)
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                detectDragGestures(
                    onDragStart = { start ->
                        selected.clear()
                        pointer = start
                        select(start)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        pointer = change.position
                        select(change.position)
                    },
                    onDragEnd = {
                        pointer = null
                        if (selected.isNotEmpty()) currentOnComplete(selected.toList())
                    },
                    onDragCancel = {
                        pointer = null
                        selected.clear()
                    },
                )
            },
    ) {
        val cellSize = size.width / PATTERN_GRID_SIZE
        val dotRadius = cellSize * 0.09f
        val strokeWidth = cellSize * 0.07f

        for (i in 1 until selected.size) {
            drawLine(
                activeColor,
                cellCenter(selected[i - 1], cellSize),
                cellCenter(selected[i], cellSize),
                strokeWidth,
                StrokeCap.Round,
            )
        }
        val last = selected.lastOrNull()
        val current = pointer
        if (last != null && current != null) {
            drawLine(activeColor.copy(alpha = 0.5f), cellCenter(last, cellSize), current, strokeWidth, StrokeCap.Round)
        }
        repeat(PATTERN_GRID_SIZE * PATTERN_GRID_SIZE) { cell ->
            val isSelected = cell in selected
            drawCircle(
                color = if (isSelected) activeColor else idleColor,
                radius = if (isSelected) dotRadius * 1.6f else dotRadius,
                center = cellCenter(cell, cellSize),
            )
        }
    }
}

private fun cellCenter(cell: Int, cellSize: Float) = Offset(
    x = (cell % PATTERN_GRID_SIZE + 0.5f) * cellSize,
    y = (cell / PATTERN_GRID_SIZE + 0.5f) * cellSize,
)

/** Hit test with a generous touch radius (35% of the cell) around each dot. */
private fun cellAt(position: Offset, width: Float): Int? {
    val cellSize = width / PATTERN_GRID_SIZE
    val col = (position.x / cellSize).toInt()
    val row = (position.y / cellSize).toInt()
    if (col !in 0 until PATTERN_GRID_SIZE || row !in 0 until PATTERN_GRID_SIZE) return null
    val cell = row * PATTERN_GRID_SIZE + col
    val c = cellCenter(cell, cellSize)
    return cell.takeIf { hypot(position.x - c.x, position.y - c.y) <= cellSize * 0.35f }
}

/** The cell exactly between [a] and [b] (e.g. 0 → 2 passes 1), or null if none. */
private fun middleCell(a: Int, b: Int): Int? {
    val (r1, c1) = a / PATTERN_GRID_SIZE to a % PATTERN_GRID_SIZE
    val (r2, c2) = b / PATTERN_GRID_SIZE to b % PATTERN_GRID_SIZE
    if ((r1 + r2) % 2 != 0 || (c1 + c2) % 2 != 0) return null
    return ((r1 + r2) / 2) * PATTERN_GRID_SIZE + (c1 + c2) / 2
}
