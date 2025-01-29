package com.tibik.speechsynthesizer.ui

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.flexbox.FlexboxLayout
import com.tibik.speechsynthesizer.R
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return FlexboxLayout(requireContext()).apply {
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

        requireActivity().findViewById<ViewGroup>(android.R.id.content).addView(loadingComposeView)

        lifecycleScope.launch {
            try {
                val url = "https://raw.githubusercontent.com/HugeFrog24/soundboard-privpol/main/README.md"
                withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000
                    
                    val content = connection.inputStream.bufferedReader().use { it.readText() }
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        requireActivity().findViewById<ViewGroup>(android.R.id.content).removeView(loadingComposeView)
                        showPrivacyPolicyDialog(content)
                    }
                }
            } catch (e: Exception) {
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    requireActivity().findViewById<ViewGroup>(android.R.id.content).removeView(loadingComposeView)
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
                                    onDismissRequest = { requireActivity().findViewById<ViewGroup>(android.R.id.content).removeView(this) },
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
                                            onClick = { requireActivity().findViewById<ViewGroup>(android.R.id.content).removeView(this@apply) }
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
                    requireActivity().findViewById<ViewGroup>(android.R.id.content).addView(errorComposeView)
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun showPrivacyPolicyDialog(content: String) {
        val composeView = ComposeView(requireContext()).apply {
            setContent {
                val context = LocalContext.current
                val dynamicColorScheme = if (isSystemInDarkTheme()) {
                    dynamicDarkColorScheme(context)
                } else {
                    dynamicLightColorScheme(context)
                }

                MaterialTheme(colorScheme = dynamicColorScheme) {
                    AlertDialog(
                        onDismissRequest = { (parent as? ViewGroup)?.removeView(this) },
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
                                        Text(
                                            text = content,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.padding(bottom = 4.dp)
                                        )
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = { (parent as? ViewGroup)?.removeView(this@apply) }
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

        requireActivity().findViewById<ViewGroup>(android.R.id.content).addView(composeView)
    }
}