package com.tibik.speechsynthesizer.lib.audio

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.MediaPlayer
import com.tibik.speechsynthesizer.VoiceAssetManager
import java.io.IOException

class MediaPlayerWrapperImpl(
    private val context: Context,
    private val callback: MediaPlayerCallback,
    private val voiceManager: VoiceAssetManager
) : MediaPlayerWrapper {
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
            if (filename.startsWith("voice/")) {
                // Handle voice files using VoiceAssetManager
                val voiceFile = voiceManager.getVoiceFile(filename.removePrefix("voice/"))
                if (voiceFile != null) {
                    playAudioFromFile(voiceFile.absolutePath)
                } else {
                    callback.onError("Voice file not found: $filename")
                }
            } else {
                // Handle other asset files normally
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
            }
        } catch (e: IOException) {
            e.printStackTrace()
            callback.onError("Failed to play audio: ${e.message}")
        }
    }

    override fun playAudioFromFile(path: String) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(path)
                prepare()
                setOnCompletionListener {
                    callback.onAudioComplete()
                }
                setOnErrorListener { _, what, extra ->
                    callback.onError("MediaPlayer error: what=$what, extra=$extra")
                    true
                }
                start()
            }
        } catch (e: IOException) {
            e.printStackTrace()
            callback.onError("Failed to play audio file: ${e.message}")
        }
    }

    override fun pause() {
        try {
            mediaPlayer?.pause()
        } catch (e: IllegalStateException) {
            e.printStackTrace()
            callback.onError("Failed to pause: ${e.message}")
        }
    }

    override fun release() {
        try {
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            e.printStackTrace()
            callback.onError("Failed to release: ${e.message}")
        }
    }
}

interface MediaPlayerCallback {
    fun onAudioComplete()
    fun onError(message: String)
}
