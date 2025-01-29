package com.tibik.speechsynthesizer.ui.compose.screens

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tibik.speechsynthesizer.R
import com.tibik.speechsynthesizer.VoiceAssetManager.DownloadState
import com.tibik.speechsynthesizer.lib.audio.AudioFile
import com.tibik.speechsynthesizer.ui.BaseSoundFragment
import com.tibik.speechsynthesizer.ui.compose.components.AudioButton

/**
 * Home screen that displays a grid of audio buttons organized by categories.
 * Handles various states including downloads, errors, and empty states.
 */
@Composable
fun HomeScreen(
    downloadState: DownloadState,
    audioFiles: List<AudioFile>,
    categories: List<BaseSoundFragment.Category>,
    showCustomOnly: Boolean,
    onAudioItemClick: (AudioFile) -> Unit,
    onDownloadClick: () -> Unit,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = isSystemInDarkTheme()
    val textColor = if (isDarkTheme) Color.White else Color.Black
    
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 120.dp),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.fillMaxSize()
    ) {
        when (downloadState) {
            is DownloadState.NotStarted -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Button(
                        onClick = onDownloadClick,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(text = stringResource(R.string.download_voice_files))
                    }
                }
            }
            
            is DownloadState.Checking -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = stringResource(R.string.checking_voice_files),
                        modifier = Modifier.padding(32.dp),
                        textAlign = TextAlign.Center,
                        color = textColor
                    )
                }
            }
            
            is DownloadState.Downloading -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LinearProgressIndicator(
                            progress = { downloadState.progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = stringResource(R.string.downloading_voice_files),
                            modifier = Modifier.padding(top = 8.dp),
                            textAlign = TextAlign.Center,
                            color = textColor
                        )
                    }
                }
            }
            
            is DownloadState.Extracting -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LinearProgressIndicator(
                            progress = { downloadState.progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = stringResource(R.string.extracting_voice_files),
                            modifier = Modifier.padding(top = 8.dp),
                            textAlign = TextAlign.Center,
                            color = textColor
                        )
                    }
                }
            }
            
            is DownloadState.Error -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = downloadState.message,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        Button(onClick = onRetryClick) {
                            Text(text = stringResource(R.string.retry_download))
                        }
                    }
                }
            }
            
            is DownloadState.Completed -> {
                val filteredFiles = if (showCustomOnly) {
                    audioFiles.filter { it.isCustom }
                } else {
                    audioFiles.filter { !it.isCustom }
                }
                
                if (filteredFiles.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = stringResource(
                                if (showCustomOnly) R.string.no_custom_sounds
                                else R.string.no_sounds_available
                            ),
                            modifier = Modifier.padding(32.dp),
                            textAlign = TextAlign.Center,
                            color = textColor
                        )
                    }
                } else {
                    val categorizedFiles = filteredFiles.groupBy { it.cat ?: "other" }
                    categories.forEach { category ->
                        val filesForCategory = categorizedFiles[category.id] ?: emptyList()
                        if (filesForCategory.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    text = category.name,
                                    fontSize = 24.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    color = textColor
                                )
                            }
                            
                            items(filesForCategory) { audioFile ->
                                AudioButton(
                                    text = audioFile.label,
                                    onClick = { onAudioItemClick(audioFile) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}