package com.tibik.speechsynthesizer.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.tibik.speechsynthesizer.lib.audio.AudioFile
import com.tibik.speechsynthesizer.lib.audio.AudioFileParser
import com.tibik.speechsynthesizer.lib.audio.AudioIdentifier
import com.tibik.speechsynthesizer.lib.audio.AudioPlaybackViewModel
import com.tibik.speechsynthesizer.ui.compose.screens.createHomeScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HomeFragment : BaseSoundFragment() {
    private val _audioFiles = MutableStateFlow<List<AudioFile>>(emptyList())
    private val audioFiles: StateFlow<List<AudioFile>> = _audioFiles
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = createHomeScreen(
        downloadState = voiceManager.downloadProgress,
        audioFiles = audioFiles,
        categories = loadCategories(),
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
                voiceManager.ensureVoiceAssetsAvailable()
            }
        },
        onRetryClick = {
            viewLifecycleOwner.lifecycleScope.launch {
                voiceManager.ensureVoiceAssetsAvailable()
            }
        }
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        viewModel = ViewModelProvider(
            requireActivity(),
            AudioPlaybackViewModel.Factory(requireContext())
        )[AudioPlaybackViewModel::class.java]
        
        // Load audio files
        val jsonString = loadJsonFromAssets("voice_files.json")
        val parsedFiles = AudioFileParser().parse(jsonString, customSounds)
        viewLifecycleOwner.lifecycleScope.launch {
            _audioFiles.emit(parsedFiles)
        }
    }
}
