package com.tibik.speechsynthesizer.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.tibik.speechsynthesizer.R
import com.tibik.speechsynthesizer.VoiceAssetManager
import com.tibik.speechsynthesizer.VoiceAssetManager.MetadataState
import com.tibik.speechsynthesizer.lib.audio.AudioFile
import com.tibik.speechsynthesizer.lib.audio.AudioIdentifier
import com.tibik.speechsynthesizer.lib.audio.AudioPlaybackViewModel
import com.tibik.speechsynthesizer.ui.compose.screens.createHomeScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HomeFragment : BaseSoundFragment() {
    private val _audioFiles = MutableStateFlow<List<AudioFile>>(emptyList())
    private val audioFiles: StateFlow<List<AudioFile>> = _audioFiles
    
    private val _downloadState = MutableStateFlow<VoiceAssetManager.DownloadState>(VoiceAssetManager.DownloadState.NotStarted)
    private val downloadState: StateFlow<VoiceAssetManager.DownloadState> = _downloadState
    
    private val _categories = MutableStateFlow<List<BaseSoundFragment.Category>>(emptyList())
    private val categories: StateFlow<List<BaseSoundFragment.Category>> = _categories
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = createHomeScreen(
        downloadState = downloadState,
        audioFiles = audioFiles,
        categories = categories,
        showCustomOnly = false,
        onAudioItemClick = { audioFile ->
            val audioIdentifier = if (audioFile.isCustom) {
                AudioIdentifier.FilePath(audioFile.filename)
            } else {
                AudioIdentifier.AssetFilename("voice/${audioFile.filename}")
            }
            val currentQueue = viewModel.uiState.value.queue.toMutableList()
            currentQueue.add(audioIdentifier)
            viewModel.setQueue(currentQueue)
        },
        onDownloadClick = {
            viewLifecycleOwner.lifecycleScope.launch {
                viewModel.ensureVoiceAssetsAvailable()
            }
        },
        onRetryClick = {
            viewLifecycleOwner.lifecycleScope.launch {
                viewModel.ensureVoiceAssetsAvailable()
            }
        }
    )

    private fun getCategoriesFromAudioFiles(): List<BaseSoundFragment.Category> {
        // Use audio files from the service instead of fragment's VoiceAssetManager
        return _audioFiles.value
            .mapNotNull { it.cat }
            .distinct()
            .map { categoryId ->
                BaseSoundFragment.Category(
                    id = categoryId,
                    name = when (categoryId) {
                        "numbers" -> getString(R.string.category_numbers)
                        "stations" -> getString(R.string.category_stations)
                        "directions" -> getString(R.string.category_directions)
                        "greetings" -> getString(R.string.category_greetings)
                        "units" -> getString(R.string.category_units)
                        "trains" -> getString(R.string.category_trains)
                        "names" -> getString(R.string.category_names)
                        "other" -> getString(R.string.category_other)
                        "custom" -> getString(R.string.category_custom)
                        else -> categoryId
                    }
                )
            }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        viewModel = ViewModelProvider(
            requireActivity(),
            AudioPlaybackViewModel.Factory(requireContext())
        )[AudioPlaybackViewModel::class.java]
        
        // Observe ViewModel state for download progress
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                _downloadState.emit(state.downloadState)
            }
        }

        // Log initial state
        android.util.Log.d("HomeFragment", "=== HomeFragment onViewCreated ===")
        android.util.Log.d("HomeFragment", "Service audio files available: ${viewModel.getAudioFiles() != null}")
        android.util.Log.d("HomeFragment", "Service metadata state available: ${viewModel.getMetadataState() != null}")
        android.util.Log.d("HomeFragment", "Local VoiceAssetManager audio files: ${voiceManager.audioFiles.value.size}")
        android.util.Log.d("HomeFragment", "Local VoiceAssetManager metadata state: ${voiceManager.metadataState.value}")

        // Wait for service connection, then observe audio files
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.serviceConnected.collect { isConnected ->
                android.util.Log.d("HomeFragment", "Service connection state: $isConnected")
                if (isConnected) {
                    android.util.Log.d("HomeFragment", "Service connected, starting to observe audio files")
                    viewModel.getAudioFiles()?.collect { files ->
                        android.util.Log.d("HomeFragment", "Service VoiceAssetManager emitted ${files.size} audio files")
                        _audioFiles.emit(files)
                        _categories.emit(getCategoriesFromAudioFiles())
                        android.util.Log.d("HomeFragment", "Updated HomeFragment state: ${files.size} files, ${getCategoriesFromAudioFiles().size} categories")
                    }
                }
            }
        }

        // Observe download state from ViewModel
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                android.util.Log.d("HomeFragment", "ViewModel download state: ${state.downloadState}")
                _downloadState.emit(state.downloadState)
            }
        }

        // Initial load - trigger metadata loading through service
        viewLifecycleOwner.lifecycleScope.launch {
            android.util.Log.d("HomeFragment", "Triggering initial metadata load through service")
            viewModel.ensureVoiceAssetsAvailable()
        }
    }
}
