package com.tibik.speechsynthesizer

import android.os.Bundle
import android.os.Build
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.DynamicColors
import com.tibik.speechsynthesizer.changelog.ChangelogDialog
import com.tibik.speechsynthesizer.changelog.ChangelogItem
import com.tibik.speechsynthesizer.changelog.ChangelogManager
import com.tibik.speechsynthesizer.lib.audio.AudioPlaybackViewModel
import com.tibik.speechsynthesizer.lib.audio.AudioQueueManager
import com.tibik.speechsynthesizer.ui.AudioUIManager
import com.tibik.speechsynthesizer.ui.HomeFragment
import com.tibik.speechsynthesizer.ui.CustomSoundsFragment
import com.tibik.speechsynthesizer.ui.SettingsFragment
import com.tibik.speechsynthesizer.lib.audio.AudioIdentifier
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), AudioUIManager.OnAudioQueueChangeListener {
    private lateinit var viewModel: AudioPlaybackViewModel
    private lateinit var audioQueueManager: AudioQueueManager
    private lateinit var audioUIManager: AudioUIManager
    private lateinit var playButton: MaterialButton
    private lateinit var clearQueueButton: MaterialButton
    private lateinit var bottomNavigation: BottomNavigationView
    private var isChangelogDialogVisible = false
    private lateinit var changelogManager: ChangelogManager

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DynamicColors.applyToActivityIfAvailable(this)
        setContentView(R.layout.activity_main)

        initializeComponents()
        setupUI()
        setupNavigation()
        handleChangelog(savedInstanceState)
    }

    private fun initializeComponents() {
        viewModel = ViewModelProvider(this, AudioPlaybackViewModel.Factory(this))[AudioPlaybackViewModel::class.java]
        audioQueueManager = AudioQueueManager()
        audioUIManager = AudioUIManager(this, findViewById(R.id.audioQueueContainer))
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
        
        // Trigger initial voice asset check on startup
        lifecycleScope.launch {
            viewModel.ensureVoiceAssetsAvailable()
        }
    }

    private fun setupUI() {
        setupButtons()
        updateDynamicColors()
    }

    private fun setupButtons() {
        playButton = findViewById<MaterialButton>(R.id.playButton).apply {
            text = context.getString(R.string.play)
            setOnClickListener { togglePlayback() }
        }
        clearQueueButton = findViewById<MaterialButton>(R.id.clearQueueButton).apply {
            text = context.getString(R.string.clear)
            setOnClickListener { clearAudioQueue() }
        }
    }

    private fun setupNavigation() {
        bottomNavigation = findViewById(R.id.bottomNavigation)
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    replaceFragment(HomeFragment())
                    true
                }
                R.id.navigation_custom_sounds -> {
                    replaceFragment(CustomSoundsFragment())
                    true
                }
                R.id.navigation_settings -> {
                    replaceFragment(SettingsFragment())
                    true
                }
                else -> false
            }
        }
        
        // Set initial fragment if this is the first creation
        if (supportFragmentManager.fragments.isEmpty()) {
            replaceFragment(HomeFragment())
        } else {
            // Restore layout for current fragment after configuration change
            val currentFragment = supportFragmentManager.fragments.firstOrNull()
            if (currentFragment != null) {
                updateLayoutForFragment(currentFragment)
            }
        }
    }

    private fun updateLayoutForFragment(fragment: Fragment) {
        // Update UI based on fragment type
        val isSettingsFragment = fragment is SettingsFragment
        findViewById<View>(R.id.scrollViewAudioQueue).visibility = if (isSettingsFragment) View.GONE else View.VISIBLE
        findViewById<View>(R.id.audioQueueContainer).visibility = if (isSettingsFragment) View.GONE else View.VISIBLE
        findViewById<View>(R.id.playButton).visibility = if (isSettingsFragment) View.GONE else View.VISIBLE
        findViewById<View>(R.id.clearQueueButton).visibility = if (isSettingsFragment) View.GONE else View.VISIBLE

        // Update fragment container constraints
        findViewById<View>(R.id.fragmentContainer).apply {
            val params = layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            if (isSettingsFragment) {
                params.topToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
                params.topToBottom = -1 // Clear bottom constraint
            } else {
                params.topToTop = -1 // Clear top constraint
                params.topToBottom = R.id.playButton // Normal position below controls
            }
            layoutParams = params
        }
    }

    private fun replaceFragment(fragment: Fragment) {
        // Update layout for the new fragment
        updateLayoutForFragment(fragment)

        // Replace fragment
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun handleChangelog(savedInstanceState: Bundle?) {
        changelogManager = ChangelogManager(this)

        isChangelogDialogVisible = savedInstanceState?.getBoolean("isChangelogDialogVisible", false)
            ?: changelogManager.shouldShowChangelog()

        if (isChangelogDialogVisible) {
            val changelogItems = changelogManager.parseChangelog()
            showChangelogDialog(changelogItems)
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
        outState.putBoolean("isChangelogDialogVisible", isChangelogDialogVisible)
    }

    private fun clearAudioQueue() {
        viewModel.clearQueue()
    }

    private fun updateDynamicColors() {
        val typedValue = android.util.TypedValue()
        val theme = theme

        // Use Material 3 color attributes
        theme.resolveAttribute(com.google.android.material.R.attr.colorPrimaryContainer, typedValue, true)
        val backgroundColor = typedValue.data

        theme.resolveAttribute(com.google.android.material.R.attr.colorOnPrimaryContainer, typedValue, true)
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

    override fun onAudioQueueChanged(newQueue: List<AudioIdentifier>) {
        viewModel.setQueue(newQueue)
    }
}
