package com.tibik.speechsynthesizer

import AudioIdentifierDeserializer
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.TypedValue
import android.view.DragEvent
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.platform.ComposeView
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.flexbox.FlexboxLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.DynamicColors
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.tibik.speechsynthesizer.lib.audio.AudioQueueManager
import com.tibik.speechsynthesizer.changelog.ChangelogDialog
import com.tibik.speechsynthesizer.changelog.ChangelogItem
import com.tibik.speechsynthesizer.changelog.ChangelogManager
import com.tibik.speechsynthesizer.lib.audio.AudioFile
import com.tibik.speechsynthesizer.lib.audio.AudioFileParser
import com.tibik.speechsynthesizer.lib.audio.AudioIdentifier
import com.tibik.speechsynthesizer.lib.audio.AudioPlaybackViewModel
import com.tibik.speechsynthesizer.ui.AudioUIManager
import java.io.File
import java.io.IOException
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), AudioUIManager.OnAudioQueueChangeListener {
    private lateinit var viewModel: AudioPlaybackViewModel
    private lateinit var audioQueueManager: AudioQueueManager
    private lateinit var audioUIManager: AudioUIManager
    private lateinit var audioQueueContainer: FrameLayout
    private lateinit var buttonContainerFlexbox: FlexboxLayout
    private lateinit var playButton: MaterialButton
    private lateinit var categories: List<Category>
    private lateinit var clearQueueButton: MaterialButton
    private lateinit var addCustomSoundButton: MaterialButton
    private val customSounds = mutableListOf<AudioFile>()
    private var isChangelogDialogVisible = false
    private lateinit var changelogManager: ChangelogManager

    private val categoryIdToResId = mapOf(
        "numbers" to R.string.category_numbers,
        "stations" to R.string.category_stations,
        "directions" to R.string.category_directions,
        "greetings" to R.string.category_greetings,
        "units" to R.string.category_units,
        "trains" to R.string.category_trains,
        "names" to R.string.category_names,
        "other" to R.string.category_other,
        "custom" to R.string.category_custom
    )

    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(AudioIdentifier::class.java, AudioIdentifierDeserializer())
        .create()

    private val pickAudioFile = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            try {
                val filePath = copyFileToInternalStorage(it)
                if (filePath != null) {
                    val fileName = File(filePath).name
                    val customSound = AudioFile(
                        filename = filePath,
                        label = fileName,
                        internal = fileName,
                        cat = "custom",
                        isCustom = true
                    )
                    customSounds.add(customSound)
                    saveCustomSounds()
                    updateAudioButtons()
                    showSuccessMessage("File added successfully: $fileName")
                } else {
                    showErrorMessage("Failed to copy file: ${getFileName(it)}")
                }
            } catch (e: Exception) {
                showErrorMessage("Error adding file: ${e.localizedMessage}")
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DynamicColors.applyToActivityIfAvailable(this)
        setContentView(R.layout.activity_main)

        initializeComponents()
        changelogManager = ChangelogManager(this)
        loadData()
        setupUI()
        restoreState(savedInstanceState)

        if (savedInstanceState != null) {
            isChangelogDialogVisible = savedInstanceState.getBoolean("isChangelogDialogVisible", false)
        } else {
            isChangelogDialogVisible = false
            if (changelogManager.shouldShowChangelog()) {
                isChangelogDialogVisible = true
            }
        }

        if (isChangelogDialogVisible) {
            val changelogItems = changelogManager.parseChangelog()
            showChangelogDialog(changelogItems)
        }
    }

    private fun initializeComponents() {
        viewModel = ViewModelProvider(this, AudioPlaybackViewModel.Factory(this))[AudioPlaybackViewModel::class.java]
        audioQueueManager = AudioQueueManager()
        audioQueueContainer = findViewById(R.id.audioQueueContainer)
        buttonContainerFlexbox = findViewById(R.id.buttonContainer)
        audioUIManager = AudioUIManager(this, audioQueueContainer)
        audioUIManager.setOnAudioQueueChangeListener(this)
        
        // Observe ViewModel state
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    updatePlayButtonState(state.isPlaying)
                    if (state.queue != audioQueueManager.getAudioQueue()) {
                        audioQueueManager.clearAudioQueue()
                        audioQueueManager.addAllAudios(state.queue)
                        audioUIManager.updateAudioQueueUI(state.queue)
                    }
                }
            }
        }
    }

    private fun loadData() {
        loadAudioFiles()
        loadCustomSounds()
    }

    private fun setupUI() {
        setupButtons()
        updateDynamicColors()
        updateAudioButtons()
        setupDragAndDrop()
    }

    private fun setupButtons() {
        addCustomSoundButton = findViewById<MaterialButton>(R.id.addCustomSoundButton).apply {
            setOnClickListener { launchFilePicker() }
        }
        playButton = findViewById<MaterialButton>(R.id.playButton).apply {
            text = context.getString(R.string.play)
            setOnClickListener { togglePlayback() }
        }
        clearQueueButton = findViewById<MaterialButton>(R.id.clearQueueButton).apply {
            text = context.getString(R.string.clear)
            setOnClickListener { clearAudioQueue() }
        }
    }

    private fun restoreState(savedInstanceState: Bundle?) {
        savedInstanceState?.let {
            val audioQueueJson = it.getString("audioQueue")
            if (audioQueueJson != null) {
                val type = object : TypeToken<List<AudioIdentifier>>() {}.type
                val audioQueueList: List<AudioIdentifier> = gson.fromJson(audioQueueJson, type)
                viewModel.setQueue(audioQueueList)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun showChangelogDialog(changelogItems: List<ChangelogItem>) {
        isChangelogDialogVisible = true

        val composeView = ComposeView(this).apply {
            setContent {
                val dynamicColorScheme = if (isSystemInDarkTheme()) {
                    dynamicDarkColorScheme(LocalContext.current)
                } else {
                    dynamicLightColorScheme(LocalContext.current)
                }

                MaterialTheme(colorScheme = dynamicColorScheme) {
                    ChangelogDialog(
                        changelogItems = changelogItems,
                        onDismiss = {
                            this@apply.visibility = View.GONE
                            (parent as? ViewGroup)?.removeView(this)
                            isChangelogDialogVisible = false
                        },
                        onDontShowAgain = { dontShow ->
                            changelogManager.setDontShowChangelog(dontShow)
                        }
                    )
                }
            }
        }

        findViewById<ViewGroup>(android.R.id.content).addView(composeView)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val audioQueueJson = gson.toJson(audioQueueManager.getAudioQueue())
        outState.putString("audioQueue", audioQueueJson)
        outState.putBoolean("isChangelogDialogVisible", isChangelogDialogVisible)
    }

    private fun clearAudioQueue() {
        viewModel.clearQueue()
    }

    private fun setupDragAndDrop() {
        audioQueueContainer.setOnDragListener { view, dragEvent ->
            when (dragEvent.action) {
                DragEvent.ACTION_DRAG_STARTED -> true
                DragEvent.ACTION_DRAG_ENTERED -> {
                    view.setBackgroundColor(Color.LTGRAY)
                    true
                }
                DragEvent.ACTION_DRAG_LOCATION -> true
                DragEvent.ACTION_DROP -> {
                    val item = dragEvent.localState as View
                    val fromIndex = audioQueueContainer.indexOfChild(item)
                    val toIndex = calculateTargetIndex(dragEvent.y)
                    audioQueueManager.reorderAudioQueue(fromIndex, toIndex)
                    val newQueue = audioQueueManager.getAudioQueue()
                    viewModel.setQueue(newQueue)
                    true
                }
                DragEvent.ACTION_DRAG_EXITED -> {
                    view.setBackgroundColor(Color.TRANSPARENT)
                    true
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                    view.setBackgroundColor(Color.TRANSPARENT)
                    true
                }
                else -> false
            }
        }
    }

    private fun calculateTargetIndex(y: Float): Int {
        for (i in 0 until audioQueueContainer.childCount) {
            val child = audioQueueContainer.getChildAt(i)
            if (y < child.bottom) {
                return i
            }
        }
        return audioQueueContainer.childCount - 1
    }

    private fun updateDynamicColors() {
        val typedValue = TypedValue()
        val theme = theme

        theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true)
        val backgroundColor = typedValue.data

        theme.resolveAttribute(com.google.android.material.R.attr.colorOnPrimary, typedValue, true)
        val onPrimaryColor = typedValue.data

        playButton.setBackgroundColor(backgroundColor)
        playButton.setTextColor(onPrimaryColor)
    }

    private fun updatePlayButtonState(isPlaying: Boolean) {
        val (textResId, iconResId) = if (isPlaying) {
            Pair(R.string.pause, android.R.drawable.ic_media_pause)
        } else {
            Pair(R.string.play, android.R.drawable.ic_media_play)
        }

        runOnUiThread {
            playButton.text = getString(textResId)
            playButton.icon = AppCompatResources.getDrawable(this, iconResId)
        }
    }

    private fun togglePlayback() {
        viewModel.togglePlayback()
    }

    private fun loadJsonFromAssets(filename: String): String = try {
        assets.open(filename).bufferedReader().use { it.readText() }
    } catch (e: IOException) {
        e.printStackTrace()
        ""
    }

    private fun loadAudioFiles() {
        loadCategories()
        loadCustomSounds()
        val jsonString = loadJsonFromAssets("voice_files.json")
        val audioFiles = AudioFileParser().parse(jsonString, customSounds)
        setupAudioButtons(audioFiles)
    }

    private fun loadCategories() {
        val jsonString = loadJsonFromAssets("categories.json")
        val type = object : TypeToken<List<Category>>() {}.type
        val tempCategories: List<Category> = Gson().fromJson(jsonString, type)
        categories = tempCategories.map { category ->
            val resId = categoryIdToResId[category.id]
            category.name = if (resId != null) getString(resId) else category.id
            category
        }
    }

    private fun setupAudioButtons(audioFiles: List<AudioFile>) {
        val categorizedFiles = audioFiles.groupBy { it.cat ?: "other" }
        categories.forEach { category ->
            val filesForCategory = categorizedFiles[category.id] ?: emptyList()
            if (filesForCategory.isNotEmpty() || category.id == "other") {
                addCategoryTitle(category.name)
                filesForCategory.forEach { audioFile ->
                    addButtonForAudioFile(audioFile)
                }
            }
        }
    }

    private fun addCategoryTitle(title: String) {
        val titleView = TextView(this).apply {
            text = title
            textSize = 24f
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = FlexboxLayout.LayoutParams(
                FlexboxLayout.LayoutParams.MATCH_PARENT,
                FlexboxLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 8, 0, 8)
            }
        }
        buttonContainerFlexbox.addView(titleView)
    }

    private fun addButtonForAudioFile(audioFile: AudioFile) {
        LayoutInflater.from(this).inflate(R.layout.audio_button, buttonContainerFlexbox, false)
            .also { view ->
                val button = view as MaterialButton
                button.text = audioFile.label
                button.setOnClickListener {
                    val audioIdentifier = if (audioFile.isCustom) {
                        AudioIdentifier.FilePath(audioFile.filename)
                    } else {
                        AudioIdentifier.AssetFilename("voice/${audioFile.filename}")
                    }
                    enqueueAudio(audioIdentifier)
                }
                buttonContainerFlexbox.addView(button)
            }
    }

    private fun enqueueAudio(audioIdentifier: AudioIdentifier) {
        val currentQueue = audioQueueManager.getAudioQueue().toMutableList()
        currentQueue.add(audioIdentifier)
        viewModel.setQueue(currentQueue)
    }

    private fun launchFilePicker() {
        pickAudioFile.launch("audio/*")
    }

    private fun copyFileToInternalStorage(uri: Uri): String? {
        val inputStream = contentResolver.openInputStream(uri) ?: return null
        val fileName = getFileName(uri)
        val outputFile = File(getExternalFilesDir(null), "custom_sounds/$fileName")
        outputFile.parentFile?.mkdirs()
        inputStream.use { input ->
            outputFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return outputFile.absolutePath
    }

    private fun getFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        result = it.getString(index)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != -1) {
                result = result?.substring(cut!! + 1)
            }
        }
        return result ?: "unknown_file"
    }

    private fun saveCustomSounds() {
        val sharedPrefs = getSharedPreferences("CustomSounds", Context.MODE_PRIVATE)
        val editor = sharedPrefs.edit()
        editor.putString("customSounds", gson.toJson(customSounds))
        editor.apply()
    }

    private fun loadCustomSounds() {
        val sharedPrefs = getSharedPreferences("CustomSounds", Context.MODE_PRIVATE)
        val customSoundsJson = sharedPrefs.getString("customSounds", null)
        if (customSoundsJson != null) {
            val type = object : TypeToken<List<AudioFile>>() {}.type
            customSounds.clear()
            customSounds.addAll(gson.fromJson(customSoundsJson, type))
        }
    }

    private fun showErrorMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun showSuccessMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun updateAudioButtons() {
        buttonContainerFlexbox.removeAllViews()
        val audioFiles = AudioFileParser().parse(loadJsonFromAssets("voice_files.json"), customSounds)
        setupAudioButtons(audioFiles)
    }

    override fun onAudioQueueChanged(newQueue: List<AudioIdentifier>) {
        viewModel.setQueue(newQueue)
    }
}
