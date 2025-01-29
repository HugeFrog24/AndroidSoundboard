package com.tibik.speechsynthesizer.ui.compose.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding

/**
 * A reusable button component for audio playback functionality.
 * Provides high contrast in both light and dark themes.
 *
 * @param text The text to display on the button
 * @param onClick Callback invoked when the button is clicked
 * @param modifier Optional modifier for customizing the button's layout
 */
@Composable
fun AudioButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = isSystemInDarkTheme()
    
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.padding(4.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (isDarkTheme) {
                Color.Black.copy(alpha = 0.3f)
            } else {
                Color.White.copy(alpha = 0.3f)
            },
            contentColor = if (isDarkTheme) {
                Color.White
            } else {
                Color.Black
            }
        ),
        border = BorderStroke(
            width = 2.dp,
            color = if (isDarkTheme) {
                Color.White
            } else {
                Color.Black
            }
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (isDarkTheme) {
                Color.White
            } else {
                Color.Black
            }
        )
    }
}