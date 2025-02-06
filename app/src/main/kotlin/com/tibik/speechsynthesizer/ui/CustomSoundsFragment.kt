package com.tibik.speechsynthesizer.ui

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.tibik.speechsynthesizer.R
import com.tibik.speechsynthesizer.VoiceAssetManager
import com.tibik.speechsynthesizer.lib.audio.AudioFile
import com.tibik.speechsynthesizer.lib.audio.AudioFileParser
import com.tibik.speechsynthesizer.lib.audio.AudioIdentifier
import com.tibik.speechsynthesizer.lib.audio.AudioPlaybackViewModel
import com.tibik.speechsynthesizer.ui.compose.screens.createCustomSoundsScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

class CustomSoundsFragment : BaseSoundFragment() {
    private val gson: Gson = GsonBuilder().create()
    private val _audioFiles = MutableStateFlow<List<AudioFile>>(emptyList())
    private val audioFiles: StateFlow<List<AudioFile>> = _audioFiles

    private val pickAudioFile = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            try {
                val filePath = copyFileToInternalStorage(it)
                if (filePath != null) {
                    val fileName = File(filePath).name
                    val customSound = AudioFile(
                        filename = filePath,
                        label = fileName,
                        internal = fileName,
                        cat = "custom",
                        isCustom = true
                    )
                    customSounds.add(customSound)
                    saveCustomSounds()
                    updateAudioButtons()
                    showSuccessMessage("File added successfully: $fileName")
                } else {
                    showErrorMessage("Failed to copy file: ${getFileName(it)}")
                }
            } catch (e: Exception) {
                showErrorMessage("Error adding file: ${e.localizedMessage}")
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        viewModel = ViewModelProvider(
            requireActivity(),
            AudioPlaybackViewModel.Factory(requireContext())
        )[AudioPlaybackViewModel::class.java]

        loadCustomSounds()
        updateAudioButtons()

        return createCustomSoundsScreen(
            audioFiles = audioFiles,
            onAddCustomSoundClick = {
                pickAudioFile.launch("audio/*")
            },
            onAudioItemClick = { audioFile ->
                val audioIdentifier = if (audioFile.isCustom) {
                    AudioIdentifier.FilePath(audioFile.filename)
                } else {
                    AudioIdentifier.AssetFilename("voice/${audioFile.filename}")
                }
                enqueueAudio(audioIdentifier)
            }
        )
    }

    private fun copyFileToInternalStorage(uri: Uri): String? {
        val inputStream = requireContext().contentResolver.openInputStream(uri) ?: return null
        val fileName = getFileName(uri)
        val outputFile = File(requireContext().getExternalFilesDir(null), "custom_sounds/$fileName")
        outputFile.parentFile?.mkdirs()
        inputStream.use { input ->
            outputFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return outputFile.absolutePath
    }

    private fun getFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = requireContext().contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        result = it.getString(index)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != -1) {
                result = result?.substring(cut!! + 1)
            }
        }
        return result ?: "unknown_file"
    }

    private fun saveCustomSounds() {
        val sharedPrefs = requireContext().getSharedPreferences("CustomSounds", Context.MODE_PRIVATE)
        val editor = sharedPrefs.edit()
        editor.putString("customSounds", gson.toJson(customSounds))
        editor.apply()
    }

    private fun loadCustomSounds() {
        val sharedPrefs = requireContext().getSharedPreferences("CustomSounds", Context.MODE_PRIVATE)
        val customSoundsJson = sharedPrefs.getString("customSounds", null)
        if (customSoundsJson != null) {
            val type = object : TypeToken<List<AudioFile>>() {}.type
            customSounds.clear()
            customSounds.addAll(gson.fromJson(customSoundsJson, type))
        }
    }

    private fun updateAudioButtons() {
        lifecycleScope.launch {
            // Only show custom sounds in this fragment
            _audioFiles.emit(customSounds)
            
            // Observe voice manager metadata state for potential updates
            voiceManager.metadataState.collect { state ->
                when (state) {
                    is VoiceAssetManager.MetadataState.Error -> {
                        showErrorMessage(getString(R.string.error_loading_categories))
                    }
                    else -> {} // Other states don't affect custom sounds
                }
            }
        }
    }

    private fun showErrorMessage(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun showSuccessMessage(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}
