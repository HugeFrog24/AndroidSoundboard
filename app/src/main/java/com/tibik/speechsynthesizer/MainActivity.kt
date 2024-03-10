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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        audioQueueFlexbox = findViewById(R.id.audioQueueFlexbox)

        val jsonString = loadJsonFromAssets()
        val audioFiles = parseAudioFilesJson(jsonString)

        setupAudioButtons(audioFiles)

        // Change this line to use MaterialButton instead of FloatingActionButton
        val playButton: MaterialButton = findViewById(R.id.playButton)
        playButton.setOnClickListener {
            playAudioQueue()
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

        audioQueueFlexbox.addView(audioItemLayout)
    }

    private fun playAudioQueue() {
        if (audioQueue.isNotEmpty()) {
            val audioIdentifier = audioQueue.removeAt(0)
            when (audioIdentifier) {
                is AudioIdentifier.ResourceId -> playAudioResource(audioIdentifier.id)
                is AudioIdentifier.AssetFilename -> playAudioFromAssets(audioIdentifier.filename)
            }
        }
    }

    private fun playAudioResource(audioResId: Int) {
        mediaPlayer?.release()

        mediaPlayer = MediaPlayer.create(this, audioResId).apply {
            setOnCompletionListener {
                audioQueueFlexbox.removeViewAt(0)
                if (audioQueue.isNotEmpty()) {
                    playAudioQueue()
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
                    audioQueueFlexbox.removeViewAt(0)
                    if (audioQueue.isNotEmpty()) {
                        playAudioQueue()
                    }
                }
                start()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}
