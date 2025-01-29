package com.tibik.speechsynthesizer.lib.audio

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.tibik.speechsynthesizer.VoiceAssetManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AudioPlaybackService : Service(), MediaPlayerCallback {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val binder = LocalBinder()
    private lateinit var voiceManager: VoiceAssetManager
    private lateinit var mediaPlayer: MediaPlayerWrapper
    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    data class PlaybackState(
        val isPlaying: Boolean = false,
        val currentIndex: Int = 0,
        val queue: List<AudioIdentifier> = emptyList(),
        val error: String? = null,
        val downloadState: VoiceAssetManager.DownloadState = VoiceAssetManager.DownloadState.NotStarted
    )

    inner class LocalBinder : Binder() {
        fun getService(): AudioPlaybackService = this@AudioPlaybackService
    }

    override fun onCreate() {
        super.onCreate()
        voiceManager = VoiceAssetManager(this)
        mediaPlayer = MediaPlayerWrapperImpl(this, this, voiceManager)
        
        // Monitor download progress
        serviceScope.launch {
            voiceManager.downloadProgress.collect { state ->
                _playbackState.value = _playbackState.value.copy(
                    downloadState = state,
                    error = if (state is VoiceAssetManager.DownloadState.Error) state.message else null
                )
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        mediaPlayer.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onError(message: String) {
        _playbackState.value = _playbackState.value.copy(
            isPlaying = false,
            error = message
        )
    }

    fun setQueue(queue: List<AudioIdentifier>) {
        _playbackState.value = _playbackState.value.copy(error = null)
        _playbackState.value = _playbackState.value.copy(queue = queue)
    }

    fun togglePlayback() {
        val currentState = _playbackState.value
        if (currentState.queue.isNotEmpty()) {
            if (currentState.isPlaying) {
                pause()
            } else {
                serviceScope.launch { play() }
            }
        }
    }

    private suspend fun play() {
        val currentState = _playbackState.value
        if (currentState.currentIndex < currentState.queue.size) {
            val audio = currentState.queue[currentState.currentIndex]
            
            // Check if we need to handle voice assets
            if (audio is AudioIdentifier.AssetFilename && audio.filename.startsWith("voice/")) {
                when (currentState.downloadState) {
                    is VoiceAssetManager.DownloadState.NotStarted,
                    is VoiceAssetManager.DownloadState.Error -> {
                        // Start download if not already downloaded
                        if (voiceManager.ensureVoiceAssetsAvailable()) {
                            playAudio(audio)
                        }
                    }
                    is VoiceAssetManager.DownloadState.Completed -> {
                        playAudio(audio)
                    }
                    else -> {
                        // Download in progress, do nothing
                        _playbackState.value = currentState.copy(
                            error = "Please wait for voice assets to download"
                        )
                    }
                }
            } else {
                playAudio(audio)
            }
        }
    }

    private fun playAudio(audio: AudioIdentifier) {
        when (audio) {
            is AudioIdentifier.ResourceId -> mediaPlayer.playAudioResource(audio.id)
            is AudioIdentifier.AssetFilename -> mediaPlayer.playAudioFromAssets(audio.filename)
            is AudioIdentifier.FilePath -> mediaPlayer.playAudioFromFile(audio.path)
        }
        _playbackState.value = _playbackState.value.copy(
            isPlaying = true,
            error = null
        )
    }

    private fun pause() {
        mediaPlayer.pause()
        _playbackState.value = _playbackState.value.copy(isPlaying = false)
    }

    override fun onAudioComplete() {
        val currentState = _playbackState.value
        if (currentState.currentIndex + 1 < currentState.queue.size) {
            _playbackState.value = currentState.copy(currentIndex = currentState.currentIndex + 1)
            serviceScope.launch { play() }
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
