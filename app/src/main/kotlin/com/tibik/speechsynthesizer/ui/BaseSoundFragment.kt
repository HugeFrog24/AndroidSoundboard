package com.tibik.speechsynthesizer.ui

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.flexbox.FlexboxLayout
import com.google.android.material.button.MaterialButton
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tibik.speechsynthesizer.R
import com.tibik.speechsynthesizer.VoiceAssetManager
import com.tibik.speechsynthesizer.lib.audio.AudioFile
import com.tibik.speechsynthesizer.lib.audio.AudioIdentifier
import com.tibik.speechsynthesizer.lib.audio.AudioPlaybackViewModel
import kotlinx.coroutines.launch

abstract class BaseSoundFragment : Fragment() {
    protected lateinit var viewModel: AudioPlaybackViewModel
    protected lateinit var buttonContainer: FlexboxLayout
    protected lateinit var voiceManager: VoiceAssetManager
    protected val customSounds = mutableListOf<AudioFile>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        voiceManager = VoiceAssetManager(requireContext())
    }

    protected val categoryIdToResId = mapOf(
        "numbers" to R.string.category_numbers,
        "stations" to R.string.category_stations,
        "directions" to R.string.category_directions,
        "greetings" to R.string.category_greetings,
        "units" to R.string.category_units,
        "trains" to R.string.category_trains,
        "names" to R.string.category_names,
        "other" to R.string.category_other,
        "custom" to R.string.category_custom
    )

    protected data class Category(
        val id: String,
        var name: String
    )

    protected fun loadJsonFromAssets(filename: String): String = try {
        requireContext().assets.open(filename).bufferedReader().use { it.readText() }
    } catch (e: Exception) {
        e.printStackTrace()
        ""
    }

    protected fun loadCategories(): List<Category> {
        val jsonString = loadJsonFromAssets("categories.json")
        val type = object : TypeToken<List<Category>>() {}.type
        val tempCategories: List<Category> = Gson().fromJson(jsonString, type)
        return tempCategories.map { category ->
            val resId = categoryIdToResId[category.id]
            category.name = if (resId != null) getString(resId) else category.id
            category
        }
    }

    protected fun setupAudioButtons(audioFiles: List<AudioFile>, showCustomOnly: Boolean = false) {
        buttonContainer.removeAllViews()

        if (showCustomOnly) {
            setupCustomSoundsUI(audioFiles)
            return
        }

        // Observe voice assets state
        viewLifecycleOwner.lifecycleScope.launch {
            voiceManager.downloadProgress.collect { state ->
                buttonContainer.removeAllViews()
                when (state) {
                    is VoiceAssetManager.DownloadState.NotStarted -> {
                        addDownloadButton()
                    }
                    is VoiceAssetManager.DownloadState.Checking -> {
                        addMessage(getString(R.string.checking_voice_files))
                    }
                    is VoiceAssetManager.DownloadState.Downloading -> {
                        addProgressIndicator(state.progress, getString(R.string.downloading_voice_files))
                    }
                    is VoiceAssetManager.DownloadState.Extracting -> {
                        addProgressIndicator(state.progress, getString(R.string.extracting_voice_files))
                    }
                    is VoiceAssetManager.DownloadState.Completed -> {
                        setupCategoriesUI(audioFiles)
                    }
                    is VoiceAssetManager.DownloadState.Error -> {
                        addErrorMessage(state.message)
                    }
                }
            }
        }
    }

    private fun setupCustomSoundsUI(audioFiles: List<AudioFile>) {
        val filteredFiles = audioFiles.filter { it.isCustom }
        if (filteredFiles.isEmpty()) {
            addMessage(getString(R.string.no_custom_sounds))
            return
        }
        setupCategoriesUI(filteredFiles)
    }

    private fun setupCategoriesUI(audioFiles: List<AudioFile>) {
        val categories = loadCategories()
        val categorizedFiles = audioFiles.filter { !it.isCustom }.groupBy { it.cat ?: "other" }
        
        if (categorizedFiles.isEmpty()) {
            addMessage(getString(R.string.no_sounds_available))
            return
        }

        categories.forEach { category ->
            val filesForCategory = categorizedFiles[category.id] ?: emptyList()
            if (filesForCategory.isNotEmpty()) {
                addCategoryTitle(category.name)
                filesForCategory.forEach { audioFile ->
                    addButtonForAudioFile(audioFile)
                }
            }
        }
    }

    private fun addDownloadButton() {
        val button = MaterialButton(requireContext()).apply {
            text = getString(R.string.download_voice_files)
            layoutParams = FlexboxLayout.LayoutParams(
                FlexboxLayout.LayoutParams.WRAP_CONTENT,
                FlexboxLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(16, 16, 16, 16)
            }
            setOnClickListener {
                viewLifecycleOwner.lifecycleScope.launch {
                    voiceManager.ensureVoiceAssetsAvailable()
                }
            }
        }
        buttonContainer.addView(button)
    }

    private fun addProgressIndicator(progress: Float, message: String) {
        val progressBar = android.widget.ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = FlexboxLayout.LayoutParams(
                FlexboxLayout.LayoutParams.MATCH_PARENT,
                FlexboxLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(32, 16, 32, 16)
            }
            max = 100
            setProgress((progress * 100).toInt())
        }
        buttonContainer.addView(progressBar)
        addMessage(message)
    }

    private fun addErrorMessage(message: String) {
        // Error container with vertical layout
        val container = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = FlexboxLayout.LayoutParams(
                FlexboxLayout.LayoutParams.MATCH_PARENT,
                FlexboxLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Error message
        val errorView = TextView(requireContext()).apply {
            text = message
            setTextColor(resources.getColor(android.R.color.holo_red_dark, null))
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(32, 16, 32, 16)
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        container.addView(errorView)

        // Retry button
        val retryButton = MaterialButton(requireContext()).apply {
            text = getString(R.string.retry_download)
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (16 * resources.displayMetrics.density).toInt()
            }
            setOnClickListener {
                viewLifecycleOwner.lifecycleScope.launch {
                    voiceManager.ensureVoiceAssetsAvailable()
                }
            }
        }
        container.addView(retryButton)

        buttonContainer.addView(container)
    }

    private fun addMessage(message: String) {
        val messageView = TextView(requireContext()).apply {
            text = message
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
            layoutParams = FlexboxLayout.LayoutParams(
                FlexboxLayout.LayoutParams.MATCH_PARENT,
                FlexboxLayout.LayoutParams.WRAP_CONTENT
            )
        }
        buttonContainer.addView(messageView)
    }

    private fun addCategoryTitle(title: String) {
        val titleView = TextView(requireContext()).apply {
            text = title
            textSize = 24f
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = FlexboxLayout.LayoutParams(
                FlexboxLayout.LayoutParams.MATCH_PARENT,
                FlexboxLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 8, 0, 8)
            }
        }
        buttonContainer.addView(titleView)
    }

    private fun addButtonForAudioFile(audioFile: AudioFile) {
        LayoutInflater.from(requireContext())
            .inflate(R.layout.audio_button, buttonContainer, false)
            .also { view ->
                val button = view as MaterialButton
                button.text = audioFile.label
                button.setOnClickListener {
                    val audioIdentifier = if (audioFile.isCustom) {
                        AudioIdentifier.FilePath(audioFile.filename)
                    } else {
                        AudioIdentifier.AssetFilename("voice/${audioFile.filename}")
                    }
                    enqueueAudio(audioIdentifier)
                }
                buttonContainer.addView(button)
            }
    }

    private fun enqueueAudio(audioIdentifier: AudioIdentifier) {
        val currentQueue = viewModel.uiState.value.queue.toMutableList()
        currentQueue.add(audioIdentifier)
        viewModel.setQueue(currentQueue)
    }
}
