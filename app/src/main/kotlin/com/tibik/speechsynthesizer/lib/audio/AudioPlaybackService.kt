package com.tibik.speechsynthesizer.lib.audio

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AudioPlaybackService : Service(), MediaPlayerCallback {
    private val binder = LocalBinder()
    private lateinit var mediaPlayer: MediaPlayerWrapper
    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    data class PlaybackState(
        val isPlaying: Boolean = false,
        val currentIndex: Int = 0,
        val queue: List<AudioIdentifier> = emptyList()
    )

    inner class LocalBinder : Binder() {
        fun getService(): AudioPlaybackService = this@AudioPlaybackService
    }

    override fun onCreate() {
        super.onCreate()
        mediaPlayer = MediaPlayerWrapperImpl(this, this)
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onDestroy() {
        mediaPlayer.release()
        super.onDestroy()
    }

    fun setQueue(queue: List<AudioIdentifier>) {
        _playbackState.value = _playbackState.value.copy(queue = queue)
    }

    fun togglePlayback() {
        val currentState = _playbackState.value
        if (currentState.queue.isNotEmpty()) {
            if (currentState.isPlaying) {
                pause()
            } else {
                play()
            }
        }
    }

    private fun play() {
        val currentState = _playbackState.value
        if (currentState.currentIndex < currentState.queue.size) {
            when (val audio = currentState.queue[currentState.currentIndex]) {
                is AudioIdentifier.ResourceId -> mediaPlayer.playAudioResource(audio.id)
                is AudioIdentifier.AssetFilename -> mediaPlayer.playAudioFromAssets(audio.filename)
                is AudioIdentifier.FilePath -> mediaPlayer.playAudioFromFile(audio.path)
            }
            _playbackState.value = currentState.copy(isPlaying = true)
        }
    }

    private fun pause() {
        mediaPlayer.pause()
        _playbackState.value = _playbackState.value.copy(isPlaying = false)
    }

    override fun onAudioComplete() {
        val currentState = _playbackState.value
        if (currentState.currentIndex + 1 < currentState.queue.size) {
            _playbackState.value = currentState.copy(currentIndex = currentState.currentIndex + 1)
            play()
        } else {
            _playbackState.value = currentState.copy(
                isPlaying = false,
                currentIndex = 0
            )
        }
    }

    fun clearQueue() {
        if (_playbackState.value.isPlaying) {
            mediaPlayer.pause()
        }
        _playbackState.value = PlaybackState()
    }
}
