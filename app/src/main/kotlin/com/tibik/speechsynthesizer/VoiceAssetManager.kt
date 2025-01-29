package com.tibik.speechsynthesizer

import android.content.Context
import android.content.pm.ApplicationInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.zip.ZipInputStream

class VoiceAssetManager(private val context: Context) {
    companion object {
        private const val TAG = "VoiceAssetManager"
        private const val GITHUB_API_URL = "https://api.github.com/repos/HugeFrog24/AndroidSoundboard-Assets/releases/latest"
        private const val VOICE_ASSETS_DIR = "voice"
        private const val TEMP_ZIP_FILE = "voice_assets.zip"
    }

    private suspend fun getLatestReleaseUrl(): String? = withContext(Dispatchers.IO) {
        try {
            val connection = URL(GITHUB_API_URL).openConnection() as java.net.HttpURLConnection
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            
            try {
                if (connection.responseCode == java.net.HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    // Parse JSON to get assets URL
                    val assetsPattern = "\"browser_download_url\":\"(.*?voice_assets\\.zip)\""
                    val matcher = Regex(assetsPattern).find(response)
                    return@withContext matcher?.groupValues?.get(1)
                }
                Log.e(TAG, "GitHub API error: ${connection.responseCode}")
                return@withContext null
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get latest release URL: ${e.message}")
            return@withContext null
        }
    }

    private val _downloadProgress = MutableStateFlow<DownloadState>(DownloadState.NotStarted)
    val downloadProgress: StateFlow<DownloadState> = _downloadProgress.asStateFlow()

    private val voiceDir: File
        get() = File(context.getExternalFilesDir(null), VOICE_ASSETS_DIR)

    private val tempZipFile: File
        get() = File(context.cacheDir, TEMP_ZIP_FILE)

    private val isDebugBuild: Boolean
        get() = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    sealed class DownloadState {
        data object NotStarted : DownloadState()
        data object Checking : DownloadState()
        data class Downloading(val progress: Float) : DownloadState() // 0.0f to 1.0f
        data class Extracting(val progress: Float) : DownloadState() // 0.0f to 1.0f
        data object Completed : DownloadState()
        data class Error(val message: String) : DownloadState()
    }

    init {
        // Check initial state
        if (isDebugBuild || areVoiceAssetsDownloaded()) {
            _downloadProgress.value = DownloadState.Completed
        }
    }

    suspend fun ensureVoiceAssetsAvailable(): Boolean {
        return try {
            if (isDebugBuild) {
                // In debug mode, assets are always available in the APK
                _downloadProgress.value = DownloadState.Completed
                true
            } else {
                when (_downloadProgress.value) {
                    is DownloadState.Completed -> true
                    else -> {
                        _downloadProgress.value = DownloadState.Checking
                        if (areVoiceAssetsDownloaded()) {
                            Log.d(TAG, "Voice assets already downloaded")
                            _downloadProgress.value = DownloadState.Completed
                            true
                        } else {
                            Log.d(TAG, "Downloading voice assets...")
                            downloadAndExtractVoiceAssets()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            val errorMsg = "Failed to ensure voice assets: ${e.message}"
            Log.e(TAG, errorMsg)
            _downloadProgress.value = DownloadState.Error(errorMsg)
            false
        }
    }

    private fun areVoiceAssetsDownloaded(): Boolean {
        return voiceDir.exists() && voiceDir.list()?.isNotEmpty() == true
    }

    private suspend fun downloadAndExtractVoiceAssets(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Check network connectivity
            if (!isNetworkAvailable()) {
                _downloadProgress.value = DownloadState.Error(context.getString(R.string.error_no_internet))
                return@withContext false
            }

            // Get latest release URL
            val downloadUrl = getLatestReleaseUrl()
            if (downloadUrl == null) {
                _downloadProgress.value = DownloadState.Error(context.getString(R.string.error_latest_release))
                return@withContext false
            }

            // Download zip file
            var totalBytes: Long
            var downloadedBytes = 0L
            
            try {
                val connection = URL(downloadUrl).openConnection() as java.net.HttpURLConnection
                try {
                    when (connection.responseCode) {
                        java.net.HttpURLConnection.HTTP_OK -> {
                            totalBytes = connection.contentLength.toLong()
                            connection.inputStream.use { input ->
                                FileOutputStream(tempZipFile).use { output ->
                                    val buffer = ByteArray(8192)
                                    var bytes: Int
                                    while (input.read(buffer).also { bytes = it } >= 0) {
                                        output.write(buffer, 0, bytes)
                                        downloadedBytes += bytes
                                        _downloadProgress.value = DownloadState.Downloading(downloadedBytes.toFloat() / totalBytes)
                                    }
                                }
                            }
                        }
                        else -> {
                            Log.e(TAG, "Download failed with status code: ${connection.responseCode}")
                            _downloadProgress.value = DownloadState.Error(
                                context.getString(R.string.error_download_failed, connection.responseCode)
                            )
                            return@withContext false
                        }
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download failed: ${e.message}")
                _downloadProgress.value = DownloadState.Error(
                    when (e) {
                        is java.net.UnknownHostException -> context.getString(R.string.error_no_internet)
                        else -> context.getString(R.string.error_download_failed, 0)
                    }
                )
                return@withContext false
            }

            // Extract zip file
            try {
                var totalEntries = 0
                var processedEntries = 0
                
                ZipInputStream(tempZipFile.inputStream()).use { zip ->
                    while (zip.nextEntry != null) totalEntries++
                }

                ZipInputStream(tempZipFile.inputStream()).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val outputFile = File(voiceDir, entry.name)
                            outputFile.parentFile?.mkdirs()
                            FileOutputStream(outputFile).use { output ->
                                zip.copyTo(output)
                            }
                        }
                        processedEntries++
                        _downloadProgress.value = DownloadState.Extracting(processedEntries.toFloat() / totalEntries)
                        entry = zip.nextEntry
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Extraction failed: ${e.message}")
                _downloadProgress.value = DownloadState.Error(context.getString(R.string.error_extraction_failed))
                return@withContext false
            }

            // Cleanup temp file
            tempZipFile.delete()
            _downloadProgress.value = DownloadState.Completed
            true
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error: ${e.message}")
            _downloadProgress.value = DownloadState.Error(e.message ?: "Unknown error")
            false
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun getVoiceFile(fileName: String): File? {
        return try {
            if (isDebugBuild) {
                // In debug mode, always load from assets
                loadFromAssets(fileName)
            } else {
                // In release mode, try downloaded file first, then fallback to assets
                val file = File(voiceDir, fileName)
                if (file.exists()) {
                    file
                } else {
                    loadFromAssets(fileName)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get voice file $fileName: ${e.message}")
            null
        }
    }

    private fun loadFromAssets(fileName: String): File? {
        return try {
            val outputFile = File(context.cacheDir, fileName)
            context.assets.open("voice/$fileName").use { input ->
                FileOutputStream(outputFile).use { output ->
                    input.copyTo(output)
                }
            }
            outputFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load $fileName from assets: ${e.message}")
            null
        }
    }
}