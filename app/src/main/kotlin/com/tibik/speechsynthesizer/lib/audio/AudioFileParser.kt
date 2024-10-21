package com.tibik.speechsynthesizer.lib.audio

import org.json.JSONArray

class AudioFileParser {
    fun parse(jsonString: String, customSounds: List<AudioFile>): List<AudioFile> {
        val jsonArray = JSONArray(jsonString)
        val audioFiles = mutableListOf<AudioFile>()

        for (i in 0 until jsonArray.length()) {
            val jsonObject = jsonArray.getJSONObject(i)
            val audioFile = AudioFile(
                filename = jsonObject.getString("filename"),
                label = jsonObject.getString("label"),
                internal = jsonObject.getString("internal"),
                cat = if (jsonObject.has("cat")) jsonObject.getString("cat") else null,
                isCustom = false
            )
            audioFiles.add(audioFile)
        }

        audioFiles.addAll(customSounds)
        return audioFiles
    }
}