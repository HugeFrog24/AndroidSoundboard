package com.tibik.speechsynthesizer

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import com.google.android.flexbox.FlexboxLayout
import com.google.android.material.button.MaterialButton
import com.google.gson.Gson
import com.tibik.speechsynthesizer.lib.audio.AudioFile
import com.tibik.speechsynthesizer.lib.audio.AudioFileParser
import com.tibik.speechsynthesizer.lib.audio.AudioIdentifier
import com.tibik.speechsynthesizer.lib.audio.MediaPlayerCallback
import com.tibik.speechsynthesizer.lib.audio.MediaPlayerWrapper
import com.tibik.speechsynthesizer.lib.audio.MediaPlayerWrapperImpl
import java.io.IOException

class MainActivity : AppCompatActivity(), MediaPlayerCallback {
    private lateinit var mediaPlayerWrapper: MediaPlayerWrapper
    private val audioQueue = mutableListOf<AudioIdentifier>()
    private var isAudioPlaying = false
    private lateinit var audioQueueFlexbox: FlexboxLayout
    private lateinit var buttonContainerFlexbox: FlexboxLayout
    private lateinit var playButton: MaterialButton
    private lateinit var categories: List<Category>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mediaPlayerWrapper = MediaPlayerWrapperImpl(this, this)
        audioQueueFlexbox = findViewById(R.id.audioQueueFlexbox)
        buttonContainerFlexbox = findViewById(R.id.buttonContainer)
        playButton = findViewById<MaterialButton>(R.id.playButton).apply {
            setOnClickListener {
                togglePlayback()
            }
        }

        loadAudioFiles()
    }

    private fun togglePlayback() {
        if (audioQueue.isNotEmpty()) {
            isAudioPlaying = !isAudioPlaying
            if (isAudioPlaying) {
                playAudioQueue()
            } else {
                mediaPlayerWrapper.pause()
            }
        } else {
            isAudioPlaying = false
        }
        updatePlayButtonUI()
    }

    private fun playAudioQueue() {
        if (audioQueue.isNotEmpty() && isAudioPlaying) {
            when (val audioIdentifier = audioQueue.first()) {
                is AudioIdentifier.ResourceId -> mediaPlayerWrapper.playAudioResource(
                    audioIdentifier.id
                )

                is AudioIdentifier.AssetFilename -> mediaPlayerWrapper.playAudioFromAssets(
                    audioIdentifier.filename
                )
            }
        }
    }

    private fun updatePlayButtonUI() {
        runOnUiThread {
            playButton.icon = if (isAudioPlaying) AppCompatResources.getDrawable(
                this,
                android.R.drawable.ic_media_pause
            ) else AppCompatResources.getDrawable(this, android.R.drawable.ic_media_play)
            playButton.text =
                if (isAudioPlaying) getString(R.string.pause) else getString(R.string.play)
        }
    }

    override fun onDestroy() {
        mediaPlayerWrapper.release()
        super.onDestroy()
    }

    private fun loadJsonFromAssets(filename: String): String = try {
        assets.open(filename).bufferedReader().use { it.readText() }
    } catch (e: IOException) {
        e.printStackTrace()
        ""
    }

    private fun loadAudioFiles() {
        loadCategories() // Load categories first
        val jsonString = loadJsonFromAssets("voice_files.json")
        val audioFiles = AudioFileParser().parse(jsonString)
        setupAudioButtons(audioFiles)
    }

    private fun loadCategories() {
        val jsonString = loadJsonFromAssets("categories.json")
        categories = Gson().fromJson(jsonString, Array<Category>::class.java).toList()
    }

    private fun setupAudioButtons(audioFiles: List<AudioFile>) {
        val categorizedFiles = audioFiles.groupBy { it.cat ?: "uncategorized" }
        categories.forEach { category ->
            addCategoryTitle(category.name)
            categorizedFiles[category.id]?.forEach { audioFile ->
                addButtonForAudioFile(audioFile)
            }
        }
        // Handle uncategorized items
        categorizedFiles["uncategorized"]?.forEach { audioFile ->
            addButtonForAudioFile(audioFile)
        }
    }

    private fun addCategoryTitle(title: String) {
        val titleView = TextView(this).apply {
            text = title
            textSize = 24f
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = FlexboxLayout.LayoutParams(
                FlexboxLayout.LayoutParams.MATCH_PARENT,
                FlexboxLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                // Set margins if necessary, for example:
                setMargins(0, 8, 0, 8)
            }
            // Additional styling can be applied here if needed
        }
        buttonContainerFlexbox.addView(titleView)
    }

    private fun addButtonForAudioFile(audioFile: AudioFile) {
        LayoutInflater.from(this).inflate(R.layout.audio_button, buttonContainerFlexbox, false)
            .also { view ->
                val button = view as MaterialButton
                button.text = audioFile.label
                button.setOnClickListener {
                    enqueueAudio(AudioIdentifier.AssetFilename("voice/${audioFile.filename}"))
                }
                buttonContainerFlexbox.addView(button)
            }
    }

    private fun enqueueAudio(audioIdentifier: AudioIdentifier) {
        audioQueue.add(audioIdentifier)
        LayoutInflater.from(this).inflate(R.layout.audio_item, audioQueueFlexbox, false)
            .also { layout ->
                val audioItemView = layout.findViewById<TextView>(R.id.audioItemTextView)
                audioItemView.text = getAudioItemName(audioIdentifier)
                layout.findViewById<MaterialButton>(R.id.removeAudioItemButton).setOnClickListener {
                    audioQueueFlexbox.removeView(layout)
                    audioQueue.remove(audioIdentifier)
                }
                audioQueueFlexbox.addView(layout)
            }
    }

    private fun getAudioItemName(audioIdentifier: AudioIdentifier): String =
        when (audioIdentifier) {
            is AudioIdentifier.ResourceId -> "Audio Item ${audioIdentifier.id}"
            is AudioIdentifier.AssetFilename -> audioIdentifier.filename
        }

    override fun onAudioComplete() {
        audioQueue.removeAt(0) // Remove the played item
        runOnUiThread {
            audioQueueFlexbox.removeViewAt(0) // Remove the view from the screen
            // Only set isAudioPlaying to false if the queue is empty
            if (audioQueue.isEmpty()) {
                isAudioPlaying = false
                updatePlayButtonUI()
            } else {
                // Continue playing the next item
                playAudioQueue()
            }
        }
    }
}
