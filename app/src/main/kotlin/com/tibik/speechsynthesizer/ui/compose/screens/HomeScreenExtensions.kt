package com.tibik.speechsynthesizer.ui.compose.screens

import android.view.View
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import com.tibik.speechsynthesizer.VoiceAssetManager.DownloadState
import com.tibik.speechsynthesizer.lib.audio.AudioFile
import com.tibik.speechsynthesizer.ui.BaseSoundFragment
import kotlinx.coroutines.flow.StateFlow

/**
 * Extension function to create a HomeScreen ComposeView within a Fragment.
 * Follows the project's pattern of using extension functions for Compose integration.
 *
 * @param downloadState Flow of download states for voice assets
 * @param audioFiles Flow of available audio files
 * @param categories List of available categories
 * @param showCustomOnly Whether to show only custom sounds
 * @param onAudioItemClick Callback invoked when an audio item is clicked
 * @param onDownloadClick Callback invoked when download button is clicked
 * @param onRetryClick Callback invoked when retry button is clicked
 * @return ComposeView containing the HomeScreen
 */
fun Fragment.createHomeScreen(
    downloadState: StateFlow<DownloadState>,
    audioFiles: StateFlow<List<AudioFile>>,
    categories: List<BaseSoundFragment.Category>,
    showCustomOnly: Boolean,
    onAudioItemClick: (AudioFile) -> Unit,
    onDownloadClick: () -> Unit,
    onRetryClick: () -> Unit
): View = ComposeView(requireContext()).apply {
    setContent {
        HomeScreen(
            downloadState = downloadState.collectAsState().value,
            audioFiles = audioFiles.collectAsState().value,
            categories = categories,
            showCustomOnly = showCustomOnly,
            onAudioItemClick = onAudioItemClick,
            onDownloadClick = onDownloadClick,
            onRetryClick = onRetryClick
        )
    }
}