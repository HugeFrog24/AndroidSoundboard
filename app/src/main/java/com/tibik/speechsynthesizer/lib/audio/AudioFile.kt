package com.tibik.speechsynthesizer.lib.audio

data class AudioFile(
    val filename: String,
    val label: String,
    val internal: String,
    val cat: String? // Make category optional
)
