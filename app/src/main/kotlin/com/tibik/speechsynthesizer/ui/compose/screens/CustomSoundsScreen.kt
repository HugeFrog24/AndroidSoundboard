package com.tibik.speechsynthesizer.ui.compose.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tibik.speechsynthesizer.R
import com.tibik.speechsynthesizer.lib.audio.AudioFile
import com.tibik.speechsynthesizer.ui.compose.components.AudioButton

/**
 * Custom sounds screen that displays a grid of custom audio buttons with an add button.
 * Follows the same pattern as HomeScreen but simplified for custom sounds.
 */
@Composable
fun CustomSoundsScreen(
    audioFiles: List<AudioFile>,
    onAddCustomSoundClick: () -> Unit,
    onAudioItemClick: (AudioFile) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 120.dp),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.fillMaxSize()
    ) {
        // Add button at the top
        item {
            Button(
                onClick = onAddCustomSoundClick,
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.add))
            }
        }

        // Custom sound buttons
        items(audioFiles) { audioFile ->
            AudioButton(
                text = audioFile.label,
                onClick = { onAudioItemClick(audioFile) }
            )
        }
    }
}