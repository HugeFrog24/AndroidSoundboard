package com.tibik.speechsynthesizer.ui

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.google.android.flexbox.FlexboxLayout
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tibik.speechsynthesizer.R
import com.tibik.speechsynthesizer.lib.audio.AudioFile
import com.tibik.speechsynthesizer.lib.audio.AudioFileParser
import com.tibik.speechsynthesizer.lib.audio.AudioIdentifier
import com.tibik.speechsynthesizer.lib.audio.AudioPlaybackViewModel
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.TextView
import com.google.android.material.button.MaterialButton

abstract class BaseSoundFragment : Fragment() {
    protected lateinit var viewModel: AudioPlaybackViewModel
    protected lateinit var buttonContainer: FlexboxLayout
    protected val customSounds = mutableListOf<AudioFile>()
    
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
        val categories = loadCategories()
        val filteredFiles = audioFiles.filter { if (showCustomOnly) it.isCustom else !it.isCustom }
        
        if (filteredFiles.isEmpty()) {
            // Show appropriate message when no sounds are available
            val messageResId = if (showCustomOnly) {
                R.string.no_custom_sounds
            } else {
                R.string.no_sounds_available
            }
            addMessage(getString(messageResId))
            return
        }

        val categorizedFiles = filteredFiles.groupBy { it.cat ?: "other" }
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
