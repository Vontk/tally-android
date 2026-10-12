package io.github.vontk.tally

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun DeleteSubjectConfirmation(
    name: String,
    tallyCount: Int,
    busy: Boolean = false,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    DestructiveConfirmation(
        "Delete this subject?",
        "$name and its $tallyCount tallies will be permanently deleted.",
        "Your other subjects will stay intact.",
        "Delete subject and tallies",
        busy,
        onCancel,
        onConfirm,
    )
}

@Composable
fun DeleteTallyConfirmation(
    subjectName: String,
    timestampLabel: String,
    busy: Boolean = false,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    DestructiveConfirmation(
        "Delete this tally?",
        "$subjectName · $timestampLabel",
        "Only this moment will be removed.",
        "Delete tally",
        busy,
        onCancel,
        onConfirm,
    )
}

@Composable
private fun DestructiveConfirmation(
    title: String,
    description: String,
    explanation: String,
    action: String,
    busy: Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onCancel() },
        icon = { MinimalTrashIcon() },
        title = { Text(title) },
        text = {
            Column {
                Text(description)
                Spacer(Modifier.height(16.dp))
                Text(explanation, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !busy,
                shape = RoundedCornerShape(12.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(action)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
            }
        },
    )
}

@Composable
internal fun MinimalTrashIcon() {
    val tint = MaterialTheme.colorScheme.error
    Canvas(Modifier.size(20.dp).semantics { contentDescription = "Delete" }) {
        fun line(x: Float, y: Float, xx: Float, yy: Float) {
            drawLine(
                tint,
                Offset(size.width * x / 24, size.height * y / 24),
                Offset(size.width * xx / 24, size.height * yy / 24),
                strokeWidth = 1.7.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        line(4f, 6f, 20f, 6f)
        line(9f, 6f, 9f, 3f)
        line(9f, 3f, 15f, 3f)
        line(15f, 3f, 15f, 6f)
        line(6f, 6f, 7f, 21f)
        line(7f, 21f, 17f, 21f)
        line(17f, 21f, 18f, 6f)
        line(10f, 10f, 10f, 17f)
        line(14f, 10f, 14f, 17f)
    }
}
