package com.tibik.speechsynthesizer.ui.compose.screens

import android.view.View
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import com.tibik.speechsynthesizer.lib.audio.AudioFile
import kotlinx.coroutines.flow.StateFlow

/**
 * Extension function to create a CustomSoundsScreen ComposeView within a Fragment.
 * Follows the project's pattern of using extension functions for Compose integration.
 *
 * @param audioFiles Flow of available custom audio files
 * @param onAddCustomSoundClick Callback invoked when add button is clicked
 * @param onAudioItemClick Callback invoked when an audio item is clicked
 * @return ComposeView containing the CustomSoundsScreen
 */
fun Fragment.createCustomSoundsScreen(
    audioFiles: StateFlow<List<AudioFile>>,
    onAddCustomSoundClick: () -> Unit,
    onAudioItemClick: (AudioFile) -> Unit
): View = ComposeView(requireContext()).apply {
    setContent {
        CustomSoundsScreen(
            audioFiles = audioFiles.collectAsState().value,
            onAddCustomSoundClick = onAddCustomSoundClick,
            onAudioItemClick = onAudioItemClick
        )
    }
}