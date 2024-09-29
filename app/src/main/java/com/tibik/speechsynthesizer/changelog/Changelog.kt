package com.tibik.speechsynthesizer.changelog

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.xmlpull.v1.XmlPullParser
import com.tibik.speechsynthesizer.R
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.material3.MaterialTheme

class ChangelogManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    
    // Get the current version code
    private val currentVersionCode = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()

    // Remove checkForNewVersion() and updateSavedVersionCode() methods

    fun shouldShowChangelog(): Boolean {
        // Use a version-specific preference key
        return !prefs.getBoolean("dont_show_changelog_$currentVersionCode", false)
    }

    fun setDontShowChangelog(dontShow: Boolean) {
        // Use a version-specific preference key
        prefs.edit().putBoolean("dont_show_changelog_$currentVersionCode", dontShow).apply()
    }

    fun parseChangelog(): List<ChangelogItem> {
        val parser = context.resources.getXml(R.xml.changelog)
        val changelogItems = mutableListOf<ChangelogItem>()
        var currentVersion = ""
        var eventType = parser.eventType

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "release" -> currentVersion = parser.getAttributeValue(null, "version")
                        "change" -> {
                            val change = parser.nextText()
                            changelogItems.add(ChangelogItem(currentVersion, change))
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        return changelogItems
    }
}

data class ChangelogItem(val version: String, val change: String)

@Composable
fun ChangelogDialog(
    changelogItems: List<ChangelogItem>,
    onDismiss: () -> Unit,
    onDontShowAgain: (Boolean) -> Unit
) {
    var dontShowAgain by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.whats_new),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column {
                // **Group changelog items by version**
                val groupedItems = changelogItems.groupBy { it.version }

                LazyColumn {
                    // **Iterate over each version group**
                    groupedItems.forEach { (version, items) ->
                        // **Display the version only once**
                        item {
                            Text(
                                text = version,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        // **Display the list of changes under the version**
                        items(items) { item ->
                            Text(
                                text = "• ${item.change}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)
                            )
                        }
                    }
                }
                // **Existing checkbox and text**
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = dontShowAgain,
                        onCheckedChange = { newValue ->
                            dontShowAgain = newValue
                            onDontShowAgain(newValue)
                        },
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                            uncheckedColor = MaterialTheme.colorScheme.onSurface,
                            checkmarkColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                    Text(
                        text = stringResource(R.string.dont_show_again),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.ok),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface
    )
}