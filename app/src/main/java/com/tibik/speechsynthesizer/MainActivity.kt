package com.tibik.speechsynthesizer

import android.content.res.AssetFileDescriptor
import android.media.MediaPlayer
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.flexbox.FlexboxLayout
import com.google.android.material.button.MaterialButton
import org.json.JSONArray
import java.io.IOException
import java.io.InputStream

data class AudioFile(
    val filename: String,
    val label: String,
    val internal: String
)

sealed class AudioIdentifier {
    data class ResourceId(val id: Int) : AudioIdentifier()
    data class AssetFilename(val filename: String) : AudioIdentifier()
}

class MainActivity : AppCompatActivity() {
    private val audioQueue = mutableListOf<AudioIdentifier>()
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var audioQueueFlexbox: FlexboxLayout
    private lateinit var playButton: MaterialButton
    private var isAudioPlaying = false // Corrected to 'var' to allow reassignment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        audioQueueFlexbox = findViewById(R.id.audioQueueFlexbox)
        playButton = findViewById(R.id.playButton)
        playButton.setOnClickListener {
            if (isAudioPlaying) {
                pauseAudio()
            } else {
                playAudioQueue()
            }
        }

        val jsonString = loadJsonFromAssets()
        val audioFiles = parseAudioFilesJson(jsonString)
        setupAudioButtons(audioFiles)
    }

    private fun playAudioQueue() {
        if (audioQueue.isNotEmpty() && !isAudioPlaying) {
            isAudioPlaying = true
            updatePlayButtonUI()
            val audioIdentifier = audioQueue.first() // Use first instead of removing to ensure atomicity
            when (audioIdentifier) {
                is AudioIdentifier.ResourceId -> playAudioResource(audioIdentifier.id)
                is AudioIdentifier.AssetFilename -> playAudioFromAssets(audioIdentifier.filename)
            }
        }
    }

    private fun pauseAudio() {
        mediaPlayer?.pause()
        isAudioPlaying = false
        updatePlayButtonUI()
    }

    private fun updatePlayButtonUI() {
        if (isAudioPlaying) {
            playButton.icon = getDrawable(android.R.drawable.ic_media_pause)
            playButton.text = getString(R.string.pause)
        } else {
            playButton.icon = getDrawable(android.R.drawable.ic_media_play)
            playButton.text = getString(R.string.play)
        }
    }

    private fun playAudioResource(audioResId: Int) {
        mediaPlayer?.release()

        mediaPlayer = MediaPlayer.create(this, audioResId).apply {
            setOnCompletionListener {
                audioQueue.removeAt(0) // Remove the played item
                audioQueueFlexbox.removeViewAt(0) // Remove the view from the screen
                isAudioPlaying = false
                updatePlayButtonUI()
                if (audioQueue.isNotEmpty()) {
                    playAudioQueue() // Continue playing the next item
                }
            }
            start()
        }
    }

    override fun onDestroy() {
        mediaPlayer?.release()
        mediaPlayer = null
        super.onDestroy()
    }

    private fun getAudioItemName(audioResId: Int): String {
        return "Audio Item $audioResId"
    }

    fun parseAudioFilesJson(jsonString: String): List<AudioFile> {
        val jsonArray = JSONArray(jsonString)
        val audioFiles = mutableListOf<AudioFile>()

        for (i in 0 until jsonArray.length()) {
            val jsonObject = jsonArray.getJSONObject(i)
            val audioFile = AudioFile(
                filename = jsonObject.getString("filename"),
                label = jsonObject.getString("label"),
                internal = jsonObject.getString("internal")
            )
            audioFiles.add(audioFile)
        }

        return audioFiles
    }

    private fun loadJsonFromAssets(): String {
        return try {
            val inputStream: InputStream = assets.open("voice_files.json")
            inputStream.bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            e.printStackTrace()
            ""
        }
    }

    fun setupAudioButtons(audioFiles: List<AudioFile>) {
        val buttonContainer = findViewById<FlexboxLayout>(R.id.buttonContainer)

        for (audioFile in audioFiles) {
            val button = LayoutInflater.from(this).inflate(R.layout.audio_button, buttonContainer, false) as TextView
            button.text = audioFile.label
            button.setOnClickListener {
                enqueueAudio(AudioIdentifier.AssetFilename("voice/${audioFile.filename}"))
            }
            buttonContainer.addView(button)
        }
    }

    private fun playAudioFromAssets(filename: String) {
        try {
            val assetFileDescriptor: AssetFileDescriptor = assets.openFd(filename)
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(assetFileDescriptor.fileDescriptor, assetFileDescriptor.startOffset, assetFileDescriptor.length)
                prepare()
                setOnCompletionListener {
                    audioQueue.removeAt(0) // Remove the played item
                    audioQueueFlexbox.removeViewAt(0) // Remove the view from the screen
                    isAudioPlaying = false
                    updatePlayButtonUI()
                    if (audioQueue.isNotEmpty()) {
                        playAudioQueue() // Continue playing the next item
                    }
                }
                start()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun enqueueAudio(audioIdentifier: AudioIdentifier) {
        audioQueue.add(audioIdentifier)

        val audioItemLayout = LayoutInflater.from(this).inflate(R.layout.audio_item, audioQueueFlexbox, false)
        val audioItemView = audioItemLayout.findViewById<TextView>(R.id.audioItemTextView)
        audioItemView.text = when (audioIdentifier) {
            is AudioIdentifier.ResourceId -> getAudioItemName(audioIdentifier.id)
            is AudioIdentifier.AssetFilename -> audioIdentifier.filename
        }

        val removeAudioItemButton = audioItemLayout.findViewById<MaterialButton>(R.id.removeAudioItemButton)
        removeAudioItemButton.setOnClickListener {
            // Remove this view from the FlexboxLayout
            audioQueueFlexbox.removeView(audioItemLayout)
            // Remove the corresponding AudioIdentifier from the queue
            audioQueue.remove(audioIdentifier)
        }

        audioQueueFlexbox.addView(audioItemLayout)
    }
}
