package com.tibik.speechsynthesizer.lib.audio

import org.json.JSONArray

class AudioFileParser {
    fun parse(jsonString: String): List<AudioFile> {
        val jsonArray = JSONArray(jsonString)
        val audioFiles = mutableListOf<AudioFile>()

        for (i in 0 until jsonArray.length()) {
            val jsonObject = jsonArray.getJSONObject(i)
            val audioFile = AudioFile(
                filename = jsonObject.getString("filename"),
                label = jsonObject.getString("label"),
                internal = jsonObject.getString("internal"),
                cat = jsonObject.optString("cat", null) // Use optString to allow for null if "cat" is not present
            )
            audioFiles.add(audioFile)
        }

        return audioFiles
    }
}