package com.adnanearrassen.ytarchiver.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/** Confirmation before permanently deleting an archived item or playlist. */
@Composable
fun ConfirmDeleteDialog(
    title: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    heading: String = "Delete download?",
    message: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(heading) },
        text = {
            Text(
                message
                    ?: "\"$title\" and its file will be permanently removed from this device."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
