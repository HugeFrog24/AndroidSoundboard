package com.tibik.speechsynthesizer

import AudioIdentifierDeserializer
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.DragEvent
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import com.google.android.flexbox.FlexboxLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.DynamicColors
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.tibik.speechsynthesizer.audioqueue.AudioQueueManager
import com.tibik.speechsynthesizer.lib.audio.AudioFile
import com.tibik.speechsynthesizer.lib.audio.AudioFileParser
import com.tibik.speechsynthesizer.lib.audio.AudioIdentifier
import com.tibik.speechsynthesizer.lib.audio.MediaPlayerCallback
import com.tibik.speechsynthesizer.lib.audio.MediaPlayerWrapper
import com.tibik.speechsynthesizer.lib.audio.MediaPlayerWrapperImpl
import com.tibik.speechsynthesizer.ui.AudioUIManager
import java.io.IOException

class MainActivity : AppCompatActivity(), MediaPlayerCallback {
    private lateinit var mediaPlayerWrapper: MediaPlayerWrapper
    private lateinit var audioQueueManager: AudioQueueManager
    private lateinit var audioUIManager: AudioUIManager
    private var isAudioPlaying = false
    private var currentIndex = 0
    private lateinit var audioQueueFlexbox: FlexboxLayout
    private lateinit var buttonContainerFlexbox: FlexboxLayout
    private lateinit var playButton: MaterialButton
    private lateinit var categories: List<Category>
    private lateinit var clearQueueButton: MaterialButton

    // Define the mapping from category IDs to resource IDs
    private val categoryIdToResId = mapOf(
        "numbers" to R.string.category_numbers,
        "stations" to R.string.category_stations,
        "directions" to R.string.category_directions,
        "greetings" to R.string.category_greetings,
        "units" to R.string.category_units,
        "trains" to R.string.category_trains,
        "names" to R.string.category_names
        // Add other categories as needed
    )

    // Initialize the Gson instance with the custom deserializer
    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(AudioIdentifier::class.java, AudioIdentifierDeserializer())
        .create()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Apply dynamic color theme
        DynamicColors.applyToActivityIfAvailable(this)
        setContentView(R.layout.activity_main)

        mediaPlayerWrapper = MediaPlayerWrapperImpl(this, this)
        audioQueueFlexbox = findViewById(R.id.audioQueueFlexbox)
        buttonContainerFlexbox = findViewById(R.id.buttonContainer)
        playButton = findViewById<MaterialButton>(R.id.playButton).apply {
            text = context.getString(R.string.play).uppercase() // Capitalize the text immediately
            setOnClickListener {
                togglePlayback()
            }
        }
        clearQueueButton = findViewById<MaterialButton>(R.id.clearQueueButton).apply {
            text = context.getString(R.string.clear).uppercase()
            setOnClickListener {
                clearAudioQueue()
            }
        }

        audioQueueManager = AudioQueueManager()
        audioUIManager = AudioUIManager(this, audioQueueFlexbox)

        // Now that playButton is initialized, you can safely call updatePlayButtonState
        updatePlayButtonState()

        updateDynamicColors()
        loadAudioFiles()
        savedInstanceState?.let {
        val audioQueueJson = it.getString("audioQueue")
        if (audioQueueJson != null) {
                val type = object : TypeToken<List<AudioIdentifier>>() {}.type
                val audioQueueList: List<AudioIdentifier> = gson.fromJson(audioQueueJson, type)
                audioQueueManager.clearAudioQueue()
                audioQueueManager.addAllAudios(audioQueueList)
            }
        }
        audioUIManager.updateAudioQueueUI(audioQueueManager.getAudioQueue())
        setupDragAndDrop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Convert your audio queue to a JSON string or another suitable format
        val audioQueueJson = gson.toJson(audioQueueManager.getAudioQueue())
        // Save the JSON string to the outState Bundle
        outState.putString("audioQueue", audioQueueJson)
    }

    private fun clearAudioQueue() {
        audioQueueManager.clearAudioQueue() // Clear the audio queue list
        audioUIManager.updateAudioQueueUI(audioQueueManager.getAudioQueue()) // Update the UI to reflect the changes
        if (isAudioPlaying) {
            mediaPlayerWrapper.pause() // Pause any currently playing audio
            isAudioPlaying = false
            updatePlayButtonState()
        }
    }

    private fun setupDragAndDrop() {
        audioQueueFlexbox.setOnDragListener { view, dragEvent ->
            when (dragEvent.action) {
                DragEvent.ACTION_DRAG_STARTED -> true
                DragEvent.ACTION_DRAG_ENTERED -> {
                    view.setBackgroundColor(Color.LTGRAY) // Example highlight
                    true
                }
                DragEvent.ACTION_DRAG_LOCATION -> true // Handle reordering here
                DragEvent.ACTION_DROP -> {
                    // Perform the actual drop action, updating your audioQueue and views
                    val item = dragEvent.localState as View
                    val fromIndex = audioQueueFlexbox.indexOfChild(item)
                    val toIndex = calculateTargetIndex(dragEvent.x, dragEvent.y)
                    audioQueueManager.reorderAudioQueue(fromIndex, toIndex)
                    audioUIManager.updateAudioQueueUI(audioQueueManager.getAudioQueue())
                    true
                }
                DragEvent.ACTION_DRAG_EXITED -> {
                    // Optional: Remove any highlighting
                    view.setBackgroundColor(Color.TRANSPARENT) // Example remove highlight
                    true
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                    // Optional: Remove any highlighting
                    view.setBackgroundColor(Color.TRANSPARENT) // Example remove highlight
                    true
                }
                else -> false
            }
        }
    }

    private fun calculateTargetIndex(x: Float, y: Float): Int {
        for (i in 0 until audioQueueFlexbox.childCount) {
            val child = audioQueueFlexbox.getChildAt(i)
            if (y < child.bottom) {
                return i
            }
        }
        return audioQueueFlexbox.childCount - 1
    }

    private fun updateDynamicColors() {
        val typedValue = TypedValue()
        val theme = theme

        theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true)
        val backgroundColor = typedValue.data

        theme.resolveAttribute(com.google.android.material.R.attr.colorOnPrimary, typedValue, true)
        val onPrimaryColor = typedValue.data

        playButton.setBackgroundColor(backgroundColor)
        playButton.setTextColor(onPrimaryColor)
    }

    private fun updatePlayButtonState() {
        val (textResId, iconResId) = if (isAudioPlaying) {
            Pair(R.string.pause, android.R.drawable.ic_media_pause)
        } else {
            Pair(R.string.play, android.R.drawable.ic_media_play)
        }

        runOnUiThread {
            playButton.text = getString(textResId).uppercase()
            playButton.icon = AppCompatResources.getDrawable(this, iconResId)
        }
    }

    private fun togglePlayback() {
        if (audioQueueManager.getAudioQueue().isNotEmpty()) {
            isAudioPlaying = !isAudioPlaying
            if (isAudioPlaying) {
                playAudioQueue()
            } else {
                mediaPlayerWrapper.pause()
            }
        } else {
            isAudioPlaying = false
        }
        updatePlayButtonState()
    }

    private fun playAudioQueue() {
        val audioQueue = audioQueueManager.getAudioQueue()
        if (audioQueue.isNotEmpty() && isAudioPlaying && currentIndex >= 0 && currentIndex < audioQueue.size) {
            when (val audioIdentifier = audioQueue[currentIndex]) {
                is AudioIdentifier.ResourceId -> mediaPlayerWrapper.playAudioResource(audioIdentifier.id)
                is AudioIdentifier.AssetFilename -> mediaPlayerWrapper.playAudioFromAssets(audioIdentifier.filename)
            }
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
        val type = object : TypeToken<List<Category>>() {}.type
        val tempCategories: List<Category> = Gson().fromJson(jsonString, type)
        categories = tempCategories.map { category ->
            val resId = categoryIdToResId[category.id]
            category.name = if (resId != null) getString(resId) else category.id
            category
        }
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
        audioQueueManager.addAudio(audioIdentifier)
        audioUIManager.updateAudioQueueUI(audioQueueManager.getAudioQueue())
    }

    override fun onAudioComplete() {
        runOnUiThread {
            // Move to the next item or reset if at the end of the queue
            if (currentIndex + 1 < audioQueueManager.getAudioQueue().size) {
                currentIndex++ // Move to the next item
                playAudioQueue() // Start playing the next item
            } else {
                // Reset the playhead to the start of the queue
                currentIndex = 0 // Reset index
                isAudioPlaying = false
                updatePlayButtonState()
                // Optionally, if you want to automatically start playing from the beginning, uncomment the following lines:
                // isAudioPlaying = true
                // playAudioQueue()
            }
        }
    }
}