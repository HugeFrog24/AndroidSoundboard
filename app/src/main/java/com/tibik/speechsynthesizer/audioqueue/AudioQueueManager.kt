package com.tibik.speechsynthesizer.audioqueue

import com.tibik.speechsynthesizer.lib.audio.AudioIdentifier
import java.util.Collections

class AudioQueueManager {
    private val audioQueue = mutableListOf<AudioIdentifier>()

    fun addAudio(audioIdentifier: AudioIdentifier) {
        audioQueue.add(audioIdentifier)
    }

    fun removeAudio(audioIdentifier: AudioIdentifier) {
        audioQueue.remove(audioIdentifier)
    }
    
    fun addAllAudios(audioIdentifiers: Collection<AudioIdentifier>) {
        audioQueue.addAll(audioIdentifiers)
    }

    fun clearAudioQueue() {
        audioQueue.clear()
    }

    fun reorderAudioQueue(fromIndex: Int, toIndex: Int) {
        if (fromIndex < toIndex) {
            for (i in fromIndex until toIndex) {
                Collections.swap(audioQueue, i, i + 1)
            }
        } else {
            for (i in fromIndex downTo toIndex + 1) {
                Collections.swap(audioQueue, i, i - 1)
            }
        }
    }

    fun getAudioQueue(): List<AudioIdentifier> = audioQueue.toList()
}