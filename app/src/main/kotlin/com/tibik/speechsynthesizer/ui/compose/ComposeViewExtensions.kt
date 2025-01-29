package com.tibik.speechsynthesizer.ui.compose

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.material3.MaterialTheme

/**
 * Creates a ComposeView that wraps a Composable with Material theming.
 * This helps integrate Compose components into existing View-based layouts
 * during the migration process.
 *
 * @param content The Composable content to display
 * @return ComposeView that can be added to traditional View hierarchies
 */
fun Context.createThemedComposeView(
    content: @Composable () -> Unit
): ComposeView = ComposeView(this).apply {
    setContent {
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme,
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes
        ) {
            content()
        }
    }
}