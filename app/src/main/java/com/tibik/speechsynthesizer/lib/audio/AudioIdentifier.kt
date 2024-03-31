package com.tibik.speechsynthesizer.lib.audio

sealed class AudioIdentifier {
    data class ResourceId(val id: Int) : AudioIdentifier()
    data class AssetFilename(val filename: String) : AudioIdentifier()
}
