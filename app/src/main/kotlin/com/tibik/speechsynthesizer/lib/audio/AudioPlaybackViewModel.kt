package com.tibik.speechsynthesizer.lib.audio

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tibik.speechsynthesizer.VoiceAssetManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AudioPlaybackViewModel(private val context: Context) : ViewModel() {
    private var playbackService: AudioPlaybackService? = null
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    data class UiState(
        val isPlaying: Boolean = false,
        val currentIndex: Int = 0,
        val queue: List<AudioIdentifier> = emptyList(),
        val downloadState: VoiceAssetManager.DownloadState = VoiceAssetManager.DownloadState.NotStarted,
        val error: String? = null
    )

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as AudioPlaybackService.LocalBinder
            playbackService = binder.getService()
            observeServiceState()
            // Notify that service is connected
            _serviceConnected.value = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            playbackService = null
            _serviceConnected.value = false
        }
    }

    private val _serviceConnected = MutableStateFlow(false)
    val serviceConnected: StateFlow<Boolean> = _serviceConnected.asStateFlow()

    init {
        bindService()
    }

    private fun bindService() {
        Intent(context, AudioPlaybackService::class.java).also { intent ->
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun observeServiceState() {
        viewModelScope.launch {
            playbackService?.playbackState?.collect { serviceState ->
                _uiState.value = UiState(
                    isPlaying = serviceState.isPlaying,
                    currentIndex = serviceState.currentIndex,
                    queue = serviceState.queue,
                    downloadState = serviceState.downloadState,
                    error = serviceState.error
                )
            }
        }
    }

    fun togglePlayback() {
        playbackService?.togglePlayback()
    }

    fun setQueue(queue: List<AudioIdentifier>) {
        playbackService?.setQueue(queue)
    }

    fun clearQueue() {
        playbackService?.clearQueue()
    }

    fun ensureVoiceAssetsAvailable() {
        playbackService?.ensureVoiceAssetsAvailable()
    }

    fun getAudioFiles() = playbackService?.getAudioFiles()
    fun getMetadataState() = playbackService?.getMetadataState()

    override fun onCleared() {
        context.unbindService(serviceConnection)
        super.onCleared()
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AudioPlaybackViewModel::class.java)) {
                return AudioPlaybackViewModel(context.applicationContext) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
