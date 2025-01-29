package com.tibik.speechsynthesizer.ui

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.flexbox.FlexboxLayout
import com.tibik.speechsynthesizer.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsFragment : Fragment() {
    private sealed class DialogState {
        data object None : DialogState()
        data object Loading : DialogState()
        data object Error : DialogState()
        data class PrivacyPolicy(val content: String) : DialogState()
    }
    
    private var currentDialog: DialogState = DialogState.None
    private var currentComposeView: ComposeView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let { bundle ->
            val dialogType = bundle.getString("currentDialogType")
            currentDialog = when (dialogType) {
                "loading" -> DialogState.Loading
                "error" -> DialogState.Error
                "privacy_policy" -> {
                    val content = bundle.getString("privacyPolicyContent") ?: ""
                    DialogState.PrivacyPolicy(content)
                }
                else -> DialogState.None
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val dialogType = when (currentDialog) {
            is DialogState.Loading -> "loading"
            is DialogState.Error -> "error"
            is DialogState.PrivacyPolicy -> "privacy_policy"
            else -> "none"
        }
        outState.putString("currentDialogType", dialogType)
        if (currentDialog is DialogState.PrivacyPolicy) {
            outState.putString("privacyPolicyContent", (currentDialog as DialogState.PrivacyPolicy).content)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        currentComposeView?.let { composeView ->
            (composeView.parent as? ViewGroup)?.removeView(composeView)
        }
        currentComposeView = null
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = FlexboxLayout(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            
            flexDirection = com.google.android.flexbox.FlexDirection.COLUMN
            justifyContent = com.google.android.flexbox.JustifyContent.CENTER
            alignItems = com.google.android.flexbox.AlignItems.CENTER

            // Settings title
            addView(android.widget.TextView(context).apply {
                text = getString(R.string.settings_title)
                textSize = 28f
                gravity = android.view.Gravity.CENTER
                layoutParams = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, (32 * resources.displayMetrics.density).toInt())
                }
            })

            // App name
            addView(android.widget.TextView(context).apply {
                text = getString(R.string.app_name)
                textSize = 24f
                gravity = android.view.Gravity.CENTER
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            })

            // App description
            addView(android.widget.TextView(context).apply {
                text = getString(R.string.app_description)
                textSize = 14f
                gravity = android.view.Gravity.CENTER
                alpha = 0.7f
                layoutParams = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(
                        (32 * resources.displayMetrics.density).toInt(),
                        (8 * resources.displayMetrics.density).toInt(),
                        (32 * resources.displayMetrics.density).toInt(),
                        0
                    )
                }
            })

            // Version
            addView(android.widget.TextView(context).apply {
                text = getString(R.string.version_text,
                    requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName)
                textSize = 16f
                gravity = android.view.Gravity.CENTER
                layoutParams = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, (8 * resources.displayMetrics.density).toInt(), 0, 0)
                }
            })

            // Language selection section
            addView(android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                layoutParams = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, (24 * resources.displayMetrics.density).toInt(), 0, 0)
                }

                // Language selection title
                addView(android.widget.TextView(context).apply {
                    text = getString(R.string.language_settings)
                    textSize = 16f
                    gravity = android.view.Gravity.CENTER
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                })

                // Language selection dropdown
                addView(ComposeView(context).apply {
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = (8 * resources.displayMetrics.density).toInt()
                    }
                    
                    setContent {
                        val context = LocalContext.current
                        val dynamicColorScheme = if (isSystemInDarkTheme()) {
                            dynamicDarkColorScheme(context)
                        } else {
                            dynamicLightColorScheme(context)
                        }

                        MaterialTheme(colorScheme = dynamicColorScheme) {
                            var expanded by remember { mutableStateOf(false) }
                            var selectedLanguage by remember {
                                mutableStateOf(
                                    when (AppCompatDelegate.getApplicationLocales().toLanguageTags()) {
                                        "de" -> getString(R.string.language_german)
                                        "ru" -> getString(R.string.language_russian)
                                        else -> getString(R.string.language_english)
                                    }
                                )
                            }

                            Box(
                                modifier = Modifier.wrapContentSize(Alignment.TopStart)
                            ) {
                                androidx.compose.material3.Surface(
                                    onClick = { expanded = true },
                                    shape = MaterialTheme.shapes.small,
                                    border = BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline
                                    ),
                                    modifier = Modifier.padding(4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = selectedLanguage,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Icon(
                                            Icons.Default.ArrowDropDown,
                                            contentDescription = getString(R.string.language_settings)
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    DropdownMenuItem(
                                        onClick = {
                                            selectedLanguage = getString(R.string.language_english)
                                            setAppLanguage("en")
                                            expanded = false
                                        },
                                        text = {
                                            Text(getString(R.string.language_english))
                                        }
                                    )
                                    DropdownMenuItem(
                                        onClick = {
                                            selectedLanguage = getString(R.string.language_german)
                                            setAppLanguage("de")
                                            expanded = false
                                        },
                                        text = {
                                            Text(getString(R.string.language_german))
                                        }
                                    )
                                    DropdownMenuItem(
                                        onClick = {
                                            selectedLanguage = getString(R.string.language_russian)
                                            setAppLanguage("ru")
                                            expanded = false
                                        },
                                        text = {
                                            Text(getString(R.string.language_russian))
                                        }
                                    )
                                }
                            }
                        }
                    }
                })
            })

            // Privacy Policy section
            addView(android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                layoutParams = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, (24 * resources.displayMetrics.density).toInt(), 0, 0)
                }

                // Privacy Policy title
                addView(android.widget.TextView(context).apply {
                    text = getString(R.string.privacy_policy)
                    textSize = 16f
                    gravity = android.view.Gravity.CENTER
                    maxLines = Integer.MAX_VALUE
                    isSingleLine = false
                    ellipsize = null
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                })

                // CTA hint with link styling
                addView(android.widget.TextView(context).apply {
                    text = getString(R.string.privacy_policy_cta)
                    textSize = 14f
                    gravity = android.view.Gravity.CENTER
                    setTextColor(android.graphics.Color.parseColor("#2196F3")) // Material Blue
                    paintFlags = paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
                    maxLines = Integer.MAX_VALUE
                    isSingleLine = false
                    ellipsize = null
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = (4 * resources.displayMetrics.density).toInt()
                    }
                })

                setOnClickListener {
                    fetchPrivacyPolicy()
                }
            })
        }

        // Restore dialog state if needed
        when (currentDialog) {
            is DialogState.Loading -> showLoadingDialog()
            is DialogState.Error -> showErrorDialog()
            is DialogState.PrivacyPolicy -> showPrivacyPolicyDialog()
            else -> { /* No dialog to show */ }
        }

        return view
    }

    private fun fetchPrivacyPolicy() {
        // For older Android versions, open in browser
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
            intent.data = android.net.Uri.parse("https://github.com/HugeFrog24/soundboard-privpol/blob/main/README.md")
            startActivity(intent)
            return
        }

        // For Android S and above, use Compose dialog
        currentDialog = DialogState.Loading
        showLoadingDialog()

        lifecycleScope.launch {
            try {
                val url = "https://raw.githubusercontent.com/HugeFrog24/soundboard-privpol/main/README.md"
                withContext(Dispatchers.IO) {
                    val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000
                    
                    val content = connection.inputStream.bufferedReader().use { it.readText() }
                    withContext(Dispatchers.Main) {
                        currentDialog = DialogState.None
                        currentComposeView?.let { composeView ->
                            (composeView.parent as? ViewGroup)?.removeView(composeView)
                            currentComposeView = null
                        }
                        currentDialog = DialogState.PrivacyPolicy(content)
                        showPrivacyPolicyDialog()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    currentDialog = DialogState.None
                    currentComposeView?.let { composeView ->
                        (composeView.parent as? ViewGroup)?.removeView(composeView)
                        currentComposeView = null
                    }
                    currentDialog = DialogState.Error
                    showErrorDialog()
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun showLoadingDialog() {
        // Clean up any existing dialog
        currentComposeView?.let { composeView ->
            (composeView.parent as? ViewGroup)?.removeView(composeView)
        }

        val loadingComposeView = ComposeView(requireContext()).apply {
            setContent {
                val context = LocalContext.current
                val dynamicColorScheme = if (isSystemInDarkTheme()) {
                    dynamicDarkColorScheme(context)
                } else {
                    dynamicLightColorScheme(context)
                }

                MaterialTheme(colorScheme = dynamicColorScheme) {
                    AlertDialog(
                        onDismissRequest = { },  // Non-dismissible while loading
                        title = {
                            Text(
                                text = getString(R.string.privacy_policy),
                                style = MaterialTheme.typography.titleLarge
                            )
                        },
                        text = {
                            Column {
                                Text(
                                    text = getString(R.string.privacy_policy_loading),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )
                                Box(
                                    modifier = Modifier.padding(8.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(48.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        },
                        confirmButton = { },  // No buttons while loading
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        textContentColor = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        currentComposeView = loadingComposeView
        requireActivity().findViewById<ViewGroup>(android.R.id.content).addView(loadingComposeView)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun showErrorDialog() {
        // Clean up any existing dialog
        currentComposeView?.let { composeView ->
            (composeView.parent as? ViewGroup)?.removeView(composeView)
        }

        val errorComposeView = ComposeView(requireContext()).apply {
            setContent {
                val context = LocalContext.current
                val dynamicColorScheme = if (isSystemInDarkTheme()) {
                    dynamicDarkColorScheme(context)
                } else {
                    dynamicLightColorScheme(context)
                }

                MaterialTheme(colorScheme = dynamicColorScheme) {
                    AlertDialog(
                        onDismissRequest = { currentDialog = DialogState.None },
                        title = {
                            Text(
                                text = getString(R.string.privacy_policy),
                                style = MaterialTheme.typography.titleLarge
                            )
                        },
                        text = {
                            Text(
                                text = getString(R.string.privacy_policy_error),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    currentDialog = DialogState.None
                                    currentComposeView = null
                                    (parent as? ViewGroup)?.removeView(this@apply)
                                }
                            ) {
                                Text(
                                    text = getString(R.string.ok),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        textContentColor = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        currentComposeView = errorComposeView
        requireActivity().findViewById<ViewGroup>(android.R.id.content).addView(errorComposeView)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun showPrivacyPolicyDialog() {
        // Clean up any existing dialog
        currentComposeView?.let { composeView ->
            (composeView.parent as? ViewGroup)?.removeView(composeView)
        }

        val composeView = ComposeView(requireContext()).apply {
            setContent {
                val context = LocalContext.current
                val dynamicColorScheme = if (isSystemInDarkTheme()) {
                    dynamicDarkColorScheme(context)
                } else {
                    dynamicLightColorScheme(context)
                }

                MaterialTheme(colorScheme = dynamicColorScheme) {
                    if (currentDialog is DialogState.PrivacyPolicy) {
                        AlertDialog(
                            onDismissRequest = { currentDialog = DialogState.None },
                            title = {
                                Text(
                                    text = getString(R.string.privacy_policy),
                                    style = MaterialTheme.typography.titleLarge
                                )
                            },
                            text = {
                                Column {
                                    LazyColumn(
                                        modifier = Modifier.heightIn(max = 400.dp)
                                    ) {
                                        item {
                                            val dialogState = currentDialog as DialogState.PrivacyPolicy
                                            Text(
                                                text = dialogState.content,
                                                style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier.padding(bottom = 4.dp)
                                            )
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        currentDialog = DialogState.None
                                        currentComposeView = null
                                        (parent as? ViewGroup)?.removeView(this@apply)
                                    }
                                ) {
                                    Text(
                                        text = getString(R.string.ok),
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface,
                            textContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        currentComposeView = composeView
        requireActivity().findViewById<ViewGroup>(android.R.id.content).addView(composeView)
    }

    private fun setAppLanguage(languageCode: String) {
        val locales = LocaleListCompat.forLanguageTags(languageCode)
        AppCompatDelegate.setApplicationLocales(locales)
    }
}