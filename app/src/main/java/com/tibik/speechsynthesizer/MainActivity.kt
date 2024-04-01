package com.tibik.speechsynthesizer

import android.graphics.Color
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
import com.tibik.speechsynthesizer.lib.audio.AudioFile
import com.tibik.speechsynthesizer.lib.audio.AudioFileParser
import com.tibik.speechsynthesizer.lib.audio.AudioIdentifier
import com.tibik.speechsynthesizer.lib.audio.MediaPlayerCallback
import com.tibik.speechsynthesizer.lib.audio.MediaPlayerWrapper
import com.tibik.speechsynthesizer.lib.audio.MediaPlayerWrapperImpl
import java.io.IOException
import java.util.Collections

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
        // Apply dynamic color theme overlays if available
        DynamicColors.applyToActivityIfAvailable(this)
        setContentView(R.layout.activity_main)

        mediaPlayerWrapper = MediaPlayerWrapperImpl(this, this)
        audioQueueFlexbox = findViewById(R.id.audioQueueFlexbox)
        buttonContainerFlexbox = findViewById(R.id.buttonContainer)
        playButton = findViewById<MaterialButton>(R.id.playButton).apply {
            setOnClickListener {
                togglePlayback()
            }
        }
        updateDynamicColors()
        loadAudioFiles()
        setupDragAndDrop()
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
                    reorderAudioQueue(fromIndex, toIndex)
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

    private fun reorderAudioQueue(fromIndex: Int, toIndex: Int) {
        if (fromIndex < toIndex) {
            for (i in fromIndex until toIndex) {
                Collections.swap(audioQueue, i, i + 1)
            }
        } else {
            for (i in fromIndex downTo toIndex + 1) {
                Collections.swap(audioQueue, i, i - 1)
            }
        }
        // Update the UI to reflect the new order
        updateAudioQueueUI()
    }

    private fun updateAudioQueueUI() {
        audioQueueFlexbox.removeAllViews()
        audioQueue.forEach { audioIdentifier ->
            val layout = LayoutInflater.from(this).inflate(R.layout.audio_item, audioQueueFlexbox, false)
            val audioItemView = layout.findViewById<TextView>(R.id.audioItemTextView)
            audioItemView.text = getAudioItemName(audioIdentifier)
            layout.findViewById<MaterialButton>(R.id.removeAudioItemButton).setOnClickListener {
                audioQueueFlexbox.removeView(layout)
                audioQueue.remove(audioIdentifier)
            }
            layout.setOnLongClickListener { view ->
                val dragShadowBuilder = View.DragShadowBuilder(view)
                view.startDragAndDrop(null, dragShadowBuilder, view, 0)
                true
            }
            audioQueueFlexbox.addView(layout)
        }
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
        LayoutInflater.from(this).inflate(R.layout.audio_item, audioQueueFlexbox, false).also { layout ->
            val audioItemView = layout.findViewById<TextView>(R.id.audioItemTextView)
            audioItemView.text = getAudioItemName(audioIdentifier)
            layout.findViewById<MaterialButton>(R.id.removeAudioItemButton).setOnClickListener {
                audioQueueFlexbox.removeView(layout)
                audioQueue.remove(audioIdentifier)
            }
            layout.setOnLongClickListener { view ->
                val dragShadowBuilder = View.DragShadowBuilder(view)
                view.startDragAndDrop(null, dragShadowBuilder, view, 0)
                true
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
            if (audioQueueFlexbox.childCount > 0) {
                audioQueueFlexbox.removeViewAt(0) // Ensure there's a view to remove
            }
            if (audioQueue.isEmpty()) {
                isAudioPlaying = false
                updatePlayButtonUI()
            } else {
                playAudioQueue()
            }
        }
    }
}
