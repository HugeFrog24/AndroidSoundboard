package com.tibik.speechsynthesizer.ui

import android.os.Bundle
import androidx.fragment.app.Fragment
import com.tibik.speechsynthesizer.VoiceAssetManager
import com.tibik.speechsynthesizer.lib.audio.AudioFile
import com.tibik.speechsynthesizer.lib.audio.AudioIdentifier
import com.tibik.speechsynthesizer.lib.audio.AudioPlaybackViewModel

abstract class BaseSoundFragment : Fragment() {
    protected lateinit var viewModel: AudioPlaybackViewModel
    protected lateinit var voiceManager: VoiceAssetManager
    protected val customSounds = mutableListOf<AudioFile>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        voiceManager = VoiceAssetManager(requireContext())
    }

    data class Category(
        val id: String,
        var name: String
    )

    fun enqueueAudio(audioIdentifier: AudioIdentifier) {
        val currentQueue = viewModel.uiState.value.queue.toMutableList()
        currentQueue.add(audioIdentifier)
        viewModel.setQueue(currentQueue)
    }
}
