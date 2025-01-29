package com.tibik.speechsynthesizer.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.flexbox.FlexboxLayout
import com.tibik.speechsynthesizer.R

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
        }
    }
}