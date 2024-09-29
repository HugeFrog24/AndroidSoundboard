package com.tibik.speechsynthesizer.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.flexbox.FlexboxLayout
import com.google.android.material.button.MaterialButton
import com.tibik.speechsynthesizer.R
import com.tibik.speechsynthesizer.lib.audio.AudioIdentifier

class AudioUIManager(private val context: Context, private val audioQueueFlexbox: FrameLayout) {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AudioItemAdapter

    interface OnAudioQueueChangeListener {
        fun onAudioQueueChanged(newQueue: List<AudioIdentifier>)
    }

    private var audioQueueChangeListener: OnAudioQueueChangeListener? = null

    fun setOnAudioQueueChangeListener(listener: OnAudioQueueChangeListener) {
        audioQueueChangeListener = listener
    }

    init {
        setupRecyclerView()
    }

    private fun setupRecyclerView() {
        adapter = AudioItemAdapter()
        recyclerView = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter = this@AudioUIManager.adapter
            layoutParams = FlexboxLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val itemTouchHelper = ItemTouchHelper(ItemTouchCallback(adapter))
        itemTouchHelper.attachToRecyclerView(recyclerView)
        adapter.setItemTouchHelper(itemTouchHelper)

        audioQueueFlexbox.removeAllViews()
        audioQueueFlexbox.addView(recyclerView)
    }

    fun updateAudioQueueUI(audioQueue: List<AudioIdentifier>) {
        adapter.submitList(audioQueue)
    }

    private fun getAudioItemName(audioIdentifier: AudioIdentifier): String =
        when (audioIdentifier) {
            is AudioIdentifier.ResourceId -> "Audio Item ${audioIdentifier.id}"
            is AudioIdentifier.AssetFilename -> audioIdentifier.filename.substringAfterLast("/")
            is AudioIdentifier.FilePath -> audioIdentifier.path.substringAfterLast("/")
        }

    inner class AudioItemAdapter : RecyclerView.Adapter<AudioItemAdapter.ViewHolder>() {
        private val audioItems = mutableListOf<AudioIdentifier>()
        private lateinit var itemTouchHelper: ItemTouchHelper

        fun submitList(newList: List<AudioIdentifier>) {
            audioItems.clear()
            audioItems.addAll(newList)
            notifyDataSetChanged()
        }

        fun setItemTouchHelper(touchHelper: ItemTouchHelper) {
            itemTouchHelper = touchHelper
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(context).inflate(R.layout.audio_item, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val audioIdentifier = audioItems[position]
            holder.bind(audioIdentifier)
        }

        override fun getItemCount() = audioItems.size

        fun onItemMove(fromPosition: Int, toPosition: Int) {
            val item = audioItems.removeAt(fromPosition)
            audioItems.add(toPosition, item)
            notifyItemMoved(fromPosition, toPosition)
            audioQueueChangeListener?.onAudioQueueChanged(audioItems.toList())
        }

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val audioItemView: TextView = itemView.findViewById(R.id.audioItemTextView)
            private val removeButton: MaterialButton = itemView.findViewById(R.id.removeAudioItemButton)
            private val dragHandle: View = itemView.findViewById(R.id.dragHandle)

            fun bind(audioIdentifier: AudioIdentifier) {
                audioItemView.text = getAudioItemName(audioIdentifier)
                removeButton.setOnClickListener {
                    val position = adapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        audioItems.removeAt(position)
                        notifyItemRemoved(position)
                        // Notify the listener about the change
                        audioQueueChangeListener?.onAudioQueueChanged(audioItems.toList())
                    }
                }
                dragHandle.setOnTouchListener { v, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            itemTouchHelper.startDrag(this)
                            v.performClick()
                            true
                        }
                        else -> false
                    }
                }
            }
        }
    }

    private class ItemTouchCallback(private val adapter: AudioItemAdapter) : ItemTouchHelper.Callback() {
        override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
            val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
            return makeMovementFlags(dragFlags, 0)
        }

        override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
            adapter.onItemMove(viewHolder.adapterPosition, target.adapterPosition)
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            // Not used in this implementation
        }
    }
}