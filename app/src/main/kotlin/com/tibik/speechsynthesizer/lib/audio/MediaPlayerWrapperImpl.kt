package com.tibik.speechsynthesizer.lib.audio

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.MediaPlayer
import java.io.IOException

class MediaPlayerWrapperImpl(private val context: Context, private val callback: MediaPlayerCallback) : MediaPlayerWrapper {
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

    override fun playAudioFromFile(path: String) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(path)
                prepare()
                setOnCompletionListener {
                    callback.onAudioComplete()
                }
                start()
            }
        } catch (e: IOException) {
            e.printStackTrace()
            // Optionally, notify user of the error
        }
    }

    override fun pause() {
        mediaPlayer?.pause()
    }

    override fun release() {
        mediaPlayer?.release()
        mediaPlayer = null
    }
}

interface MediaPlayerCallback {
    fun onAudioComplete()
}
