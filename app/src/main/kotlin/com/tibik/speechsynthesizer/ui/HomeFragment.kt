package com.tibik.speechsynthesizer.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import com.tibik.speechsynthesizer.R
import com.tibik.speechsynthesizer.lib.audio.AudioFileParser
import com.tibik.speechsynthesizer.lib.audio.AudioPlaybackViewModel

class HomeFragment : BaseSoundFragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        viewModel = ViewModelProvider(
            requireActivity(),
            AudioPlaybackViewModel.Factory(requireContext())
        )[AudioPlaybackViewModel::class.java]
        
        buttonContainer = view.findViewById(R.id.buttonContainer)
        
        // Load and display built-in sounds only
        val jsonString = loadJsonFromAssets("voice_files.json")
        val audioFiles = AudioFileParser().parse(jsonString, customSounds)
        setupAudioButtons(audioFiles, showCustomOnly = false)
    }
}
