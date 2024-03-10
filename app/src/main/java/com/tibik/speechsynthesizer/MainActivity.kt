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

interface MediaPlayerWrapper {
    fun playAudioResource(audioResId: Int)
    fun playAudioFromAssets(filename: String)
    fun pause()
    fun release()
}

class MediaPlayerWrapperImpl(private val context: AppCompatActivity, private val callback: MediaPlayerCallback) : MediaPlayerWrapper {
    private var mediaPlayer: MediaPlayer? = null

    override fun playAudioResource(audioResId: Int) {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer.create(context, audioResId).apply {
            setOnCompletionListener {
                callback.onAudioComplete()
            }
            start()
        }
    }

    override fun playAudioFromAssets(filename: String) {
        try {
            val assetFileDescriptor: AssetFileDescriptor = context.assets.openFd(filename)
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(assetFileDescriptor.fileDescriptor, assetFileDescriptor.startOffset, assetFileDescriptor.length)
                prepare()
                setOnCompletionListener {
                    callback.onAudioComplete()
                }
                start()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun pause() {
        mediaPlayer?.pause()
    }

    override fun release() {
        mediaPlayer?.release()
    }
}

interface MediaPlayerCallback {
    fun onAudioComplete()
}

class AudioFileParser {
    fun parse(jsonString: String): List<AudioFile> {
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
}

class MainActivity : AppCompatActivity(), MediaPlayerCallback {
    private lateinit var mediaPlayerWrapper: MediaPlayerWrapper
    val audioQueue = mutableListOf<AudioIdentifier>()
    var isAudioPlaying = false // Corrected to 'var' to allow reassignment
    lateinit var audioQueueFlexbox: FlexboxLayout
    lateinit var playButton: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mediaPlayerWrapper = MediaPlayerWrapperImpl(this, this)
        audioQueueFlexbox = findViewById(R.id.audioQueueFlexbox)
        playButton = findViewById(R.id.playButton)
        playButton.setOnClickListener {
            if (isAudioPlaying) {
                mediaPlayerWrapper.pause()
                isAudioPlaying = false
                updatePlayButtonUI()
            } else {
                playAudioQueue()
            }
        }

        val parser = AudioFileParser()
        val jsonString = loadJsonFromAssets()
        val audioFiles = parser.parse(jsonString)
        setupAudioButtons(audioFiles)
    }

    private fun playAudioQueue() {
        if (audioQueue.isNotEmpty() && !isAudioPlaying) {
            isAudioPlaying = true
            updatePlayButtonUI()
            val audioIdentifier = audioQueue.first() // Use first instead of removing to ensure atomicity
            when (audioIdentifier) {
                is AudioIdentifier.ResourceId -> mediaPlayerWrapper.playAudioResource(audioIdentifier.id)
                is AudioIdentifier.AssetFilename -> mediaPlayerWrapper.playAudioFromAssets(audioIdentifier.filename)
            }
        }
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

    override fun onDestroy() {
        mediaPlayerWrapper.release()
        super.onDestroy()
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

    private fun getAudioItemName(audioResId: Int): String {
        return "Audio Item $audioResId"
    }

    override fun onAudioComplete() {
        audioQueue.removeAt(0) // Remove the played item
        audioQueueFlexbox.removeViewAt(0) // Remove the view from the screen
        isAudioPlaying = false
        updatePlayButtonUI()
        if (audioQueue.isNotEmpty()) {
            playAudioQueue() // Continue playing the next item
        }
    }
}