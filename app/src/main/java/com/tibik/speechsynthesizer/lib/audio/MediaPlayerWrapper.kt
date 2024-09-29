package com.tibik.speechsynthesizer.lib.audio

interface MediaPlayerWrapper {
    fun playAudioResource(audioResId: Int)
    fun playAudioFromAssets(filename: String)
    // Add this:
    fun playAudioFromFile(path: String)
    fun pause()
    fun release()
}
