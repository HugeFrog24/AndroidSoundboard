package com.tibik.speechsynthesizer.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.google.android.flexbox.FlexboxLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.tibik.speechsynthesizer.R
import com.tibik.speechsynthesizer.lib.audio.AudioIdentifier

class AudioUIManager(private val context: Context, private val audioQueueFlexbox: FlexboxLayout) {
    fun updateAudioQueueUI(audioQueue: List<AudioIdentifier>) {
        audioQueueFlexbox.removeAllViews()
        audioQueue.forEach { audioIdentifier ->
            val layout = LayoutInflater.from(context).inflate(R.layout.audio_item, audioQueueFlexbox, false) as MaterialCardView
            val audioItemView = layout.findViewById<TextView>(R.id.audioItemTextView)
            audioItemView.text = getAudioItemName(audioIdentifier)
            layout.findViewById<MaterialButton>(R.id.removeAudioItemButton).setOnClickListener {
                audioQueueFlexbox.removeView(layout)
                // Notify AudioQueueManager to remove this item
            }
            layout.setOnLongClickListener { view ->
                val dragShadowBuilder = View.DragShadowBuilder(view)
                view.startDragAndDrop(null, dragShadowBuilder, view, 0)
                true
            }
            audioQueueFlexbox.addView(layout)
        }
    }

    private fun getAudioItemName(audioIdentifier: AudioIdentifier): String =
        when (audioIdentifier) {
            is AudioIdentifier.ResourceId -> "Audio Item ${audioIdentifier.id}"
            is AudioIdentifier.AssetFilename -> audioIdentifier.filename.substringAfterLast("/")
        }

    // Include other UI update methods here...
}