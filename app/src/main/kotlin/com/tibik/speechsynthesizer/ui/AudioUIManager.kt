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
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
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

    inner class AudioItemAdapter : ListAdapter<AudioIdentifier, AudioItemAdapter.ViewHolder>(DiffCallback()) {
        private lateinit var itemTouchHelper: ItemTouchHelper

        init {
            setHasStableIds(true)
        }

        override fun getItemId(position: Int): Long {
            return when (val item = getItem(position)) {
                is AudioIdentifier.ResourceId -> item.id.toLong()
                is AudioIdentifier.AssetFilename -> item.filename.hashCode().toLong()
                is AudioIdentifier.FilePath -> item.path.hashCode().toLong()
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(context).inflate(R.layout.audio_item, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val audioIdentifier = getItem(position)
            holder.bind(audioIdentifier)
        }

        fun setItemTouchHelper(touchHelper: ItemTouchHelper) {
            itemTouchHelper = touchHelper
        }

        fun onItemMove(fromPosition: Int, toPosition: Int) {
            val currentList = currentList.toMutableList()
            val movedItem = currentList.removeAt(fromPosition)
            currentList.add(toPosition, movedItem)
            submitList(currentList) {
                audioQueueChangeListener?.onAudioQueueChanged(currentList)
            }
        }

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val audioItemView: TextView = itemView.findViewById(R.id.audioItemTextView)
            private val removeButton: MaterialButton = itemView.findViewById(R.id.removeAudioItemButton)
            private val dragHandle: View = itemView.findViewById(R.id.dragHandle)

            fun bind(audioIdentifier: AudioIdentifier) {
                audioItemView.text = getAudioItemName(audioIdentifier)
                removeButton.setOnClickListener {
                    if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                        val updatedList = currentList.toMutableList()
                        updatedList.removeAt(bindingAdapterPosition)
                        submitList(updatedList) {
                            audioQueueChangeListener?.onAudioQueueChanged(updatedList)
                        }
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
            adapter.onItemMove(viewHolder.getBindingAdapterPosition(), target.getBindingAdapterPosition())
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            // Not used in this implementation
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<AudioIdentifier>() {
        override fun areItemsTheSame(oldItem: AudioIdentifier, newItem: AudioIdentifier): Boolean {
            return when {
                oldItem is AudioIdentifier.ResourceId && newItem is AudioIdentifier.ResourceId ->
                    oldItem.id == newItem.id
                oldItem is AudioIdentifier.AssetFilename && newItem is AudioIdentifier.AssetFilename ->
                    oldItem.filename == newItem.filename
                oldItem is AudioIdentifier.FilePath && newItem is AudioIdentifier.FilePath ->
                    oldItem.path == newItem.path
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: AudioIdentifier, newItem: AudioIdentifier): Boolean {
            return oldItem == newItem
        }
    }
}
