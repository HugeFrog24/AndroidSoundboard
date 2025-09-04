package com.tibik.speechsynthesizer

import android.content.Context
import android.content.pm.ApplicationInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.tibik.speechsynthesizer.lib.audio.AudioFile
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
        private const val METADATA_DIR = "metadata"
        private const val CATEGORIES_JSON = "categories.json"
        private const val VOICE_FILES_JSON = "voice_files.json"
        private const val CACHE_DURATION_MS = 24 * 60 * 60 * 1000 // 24 hours
    }

    private data class MetadataFiles(
        val categories: List<Category>,
        val voiceFiles: List<VoiceFile>
    )

    data class Category(
        val id: String
    )

    data class VoiceFile(
        val filename: String,
        val label: String,
        val internal: String,
        val cat: String?
    )

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

    private val _metadataState = MutableStateFlow<MetadataState>(MetadataState.NotLoaded)
    val metadataState: StateFlow<MetadataState> = _metadataState.asStateFlow()

    private val _audioFiles = MutableStateFlow<List<AudioFile>>(emptyList())
    val audioFiles: StateFlow<List<AudioFile>> = _audioFiles.asStateFlow()

    sealed class MetadataState {
        data object NotLoaded : MetadataState()
        data object Loading : MetadataState()
        data class Loaded(val source: Source) : MetadataState()
        data class Error(val message: String) : MetadataState()

        enum class Source {
            SERVER, CACHE, DEBUG_ASSETS
        }
    }

    private val voiceDir: File
        get() = File(context.getExternalFilesDir(null), VOICE_ASSETS_DIR)

    private val metadataDir: File
        get() = File(context.getExternalFilesDir(null), METADATA_DIR)

    private val tempZipFile: File
        get() = File(context.cacheDir, TEMP_ZIP_FILE)

    private val isDebugBuild: Boolean
        get() = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    private var cachedMetadata: MetadataFiles? = null
    private var lastMetadataFetch: Long = 0

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
        // Check if voice directory exists and has files
        val hasVoiceFiles = voiceDir.exists() && voiceDir.list()?.isNotEmpty() == true
        
        // Check if metadata files exist
        val categoriesFile = File(metadataDir, CATEGORIES_JSON)
        val voiceFilesFile = File(metadataDir, VOICE_FILES_JSON)
        val hasMetadataFiles = categoriesFile.exists() && voiceFilesFile.exists()
        
        return hasVoiceFiles && hasMetadataFiles
    }

    private fun verifyZipContents(zipFile: File): Boolean {
        return try {
            var hasMetadataDir = false
            var hasVoiceDir = false
            var hasCategoriesJson = false
            var hasVoiceFilesJson = false
            val entries = mutableListOf<String>()

            ZipInputStream(zipFile.inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    entries.add(entry.name)
                    Log.d(TAG, "Processing zip entry: '${entry.name}' (length: ${entry.name.length})")
                    
                    // Check specific files first, then directories
                    when (entry.name) {
                        "metadata/categories.json" -> {
                            hasCategoriesJson = true
                            hasMetadataDir = true
                            Log.d(TAG, "Found categories.json!")
                        }
                        "metadata/voice_files.json" -> {
                            hasVoiceFilesJson = true
                            hasMetadataDir = true
                            Log.d(TAG, "Found voice_files.json!")
                        }
                        else -> {
                            when {
                                entry.name.startsWith("metadata/") -> {
                                    hasMetadataDir = true
                                    Log.d(TAG, "Found metadata directory entry")
                                }
                                entry.name.startsWith("voice/") -> {
                                    hasVoiceDir = true
                                    Log.d(TAG, "Found voice directory entry")
                                }
                            }
                        }
                    }
                    entry = zip.nextEntry
                }
            }

            // Log detailed verification results
            Log.d(TAG, "Zip contents verification:")
            Log.d(TAG, "- Has metadata directory: $hasMetadataDir")
            Log.d(TAG, "- Has voice directory: $hasVoiceDir")
            Log.d(TAG, "- Has categories.json: $hasCategoriesJson")
            Log.d(TAG, "- Has voice_files.json: $hasVoiceFilesJson")

            val isValid = hasMetadataDir && hasVoiceDir && hasCategoriesJson && hasVoiceFilesJson
            if (!isValid) {
                Log.e(TAG, "Missing required files/directories:")
                if (!hasMetadataDir) Log.e(TAG, "- metadata/ directory")
                if (!hasVoiceDir) Log.e(TAG, "- voice/ directory")
                if (!hasCategoriesJson) Log.e(TAG, "- metadata/categories.json")
                if (!hasVoiceFilesJson) Log.e(TAG, "- metadata/voice_files.json")
            }

            isValid
        } catch (e: Exception) {
            Log.e(TAG, "Failed to verify zip contents: ${e.message}")
            false
        }
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
            val totalBytes: Long
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

            // Verify zip contents before extraction
            if (!verifyZipContents(tempZipFile)) {
                Log.e(TAG, "Invalid zip file: missing required files")
                _downloadProgress.value = DownloadState.Error(context.getString(R.string.error_invalid_zip))
                tempZipFile.delete()
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
                            // Extract to the base external files directory, preserving the zip structure
                            val outputFile = File(context.getExternalFilesDir(null), entry.name)
                            outputFile.parentFile?.mkdirs()
                            FileOutputStream(outputFile).use { output ->
                                zip.copyTo(output)
                            }
                            Log.d(TAG, "Extracted: ${entry.name} to ${outputFile.absolutePath}")
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
            
            // Automatically load metadata from the extracted files
            Log.d(TAG, "Download completed, loading metadata from extracted files...")
            val metadata = loadMetadataFromDisk()
            if (metadata != null) {
                cachedMetadata = metadata
                lastMetadataFetch = System.currentTimeMillis()
                _metadataState.value = MetadataState.Loaded(MetadataState.Source.SERVER)
                updateAudioFiles(metadata)
            } else {
                Log.e(TAG, "Failed to load metadata from extracted files")
                _metadataState.value = MetadataState.Error("Failed to load extracted metadata")
            }
            
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

    private suspend fun fetchMetadata(): MetadataFiles? = withContext(Dispatchers.IO) {
        try {
            _metadataState.value = MetadataState.Loading

            if (isDebugBuild) {
                // In debug mode, always use bundled assets
                return@withContext loadMetadataFromAssets()?.also {
                    _metadataState.value = MetadataState.Loaded(MetadataState.Source.DEBUG_ASSETS)
                }
            }

            // Check if we have valid cached metadata
            if (cachedMetadata != null && System.currentTimeMillis() - lastMetadataFetch < CACHE_DURATION_MS) {
                _metadataState.value = MetadataState.Loaded(MetadataState.Source.CACHE)
                return@withContext cachedMetadata
            }

            // Check network availability
            if (!isNetworkAvailable()) {
                return@withContext loadMetadataFromDisk()?.also {
                    _metadataState.value = MetadataState.Loaded(MetadataState.Source.CACHE)
                }
            }

            // Get latest release URL
            getLatestReleaseUrl() ?: return@withContext loadMetadataFromDisk()?.also {
                _metadataState.value = MetadataState.Loaded(MetadataState.Source.CACHE)
            }

            // Create metadata directory if it doesn't exist
            metadataDir.mkdirs()

            // Download the zip file first
            if (!downloadAndExtractVoiceAssets()) {
                return@withContext loadMetadataFromDisk() ?: loadMetadataFromAssets()
            }

            // Now read the extracted JSON files
            val categoriesFile = File(metadataDir, CATEGORIES_JSON)
            val categories = if (categoriesFile.exists()) {
                parseCategories(categoriesFile.readText())
            } else {
                Log.e(TAG, "categories.json not found in extracted files")
                return@withContext loadMetadataFromDisk() ?: loadMetadataFromAssets()
            }

            val voiceFilesFile = File(metadataDir, VOICE_FILES_JSON)
            val voiceFiles = if (voiceFilesFile.exists()) {
                parseVoiceFiles(voiceFilesFile.readText())
            } else {
                Log.e(TAG, "voice_files.json not found in extracted files")
                return@withContext loadMetadataFromDisk() ?: loadMetadataFromAssets()
            }

            MetadataFiles(categories, voiceFiles).also {
                cachedMetadata = it
                lastMetadataFetch = System.currentTimeMillis()
                _metadataState.value = MetadataState.Loaded(MetadataState.Source.SERVER)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch metadata: ${e.message}")
            _metadataState.value = MetadataState.Error(e.message ?: "Unknown error")
            // Try fallback to disk cache
            loadMetadataFromDisk()?.also {
                _metadataState.value = MetadataState.Loaded(MetadataState.Source.CACHE)
            }
        }
    }

    private fun loadMetadataFromDisk(): MetadataFiles? {
        return try {
            val categoriesFile = File(metadataDir, CATEGORIES_JSON)
            val voiceFilesFile = File(metadataDir, VOICE_FILES_JSON)

            if (!categoriesFile.exists() || !voiceFilesFile.exists()) {
                return null
            }

            val categories = parseCategories(categoriesFile.readText())
            val voiceFiles = parseVoiceFiles(voiceFilesFile.readText())

            MetadataFiles(categories, voiceFiles)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load metadata from disk: ${e.message}")
            null
        }
    }

    private fun loadMetadataFromAssets(): MetadataFiles? {
        // Only use assets in debug builds
        if (!isDebugBuild) {
            Log.d(TAG, "Not a debug build, skipping asset loading")
            return null
        }
        
        return try {
            Log.d(TAG, "Loading metadata from assets...")
            val categories = context.assets.open("metadata/$CATEGORIES_JSON").bufferedReader().use { reader ->
                parseCategories(reader.readText())
            }

            val voiceFiles = context.assets.open("metadata/$VOICE_FILES_JSON").bufferedReader().use { reader ->
                parseVoiceFiles(reader.readText())
            }

            Log.d(TAG, "Loaded ${categories.size} categories and ${voiceFiles.size} voice files from assets")
            MetadataFiles(categories, voiceFiles)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load metadata from assets: ${e.message}")
            null
        }
    }

    private fun parseCategories(json: String): List<Category> {
        return try {
            android.util.JsonReader(java.io.StringReader(json)).use { reader ->
                reader.beginArray()
                val categories = mutableListOf<Category>()
                while (reader.hasNext()) {
                    reader.beginObject()
                    var id = ""
                    while (reader.hasNext()) {
                        if (reader.nextName() == "id") {
                            id = reader.nextString()
                        } else {
                            reader.skipValue()
                        }
                    }
                    reader.endObject()
                    categories.add(Category(id))
                }
                reader.endArray()
                categories
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse categories: ${e.message}")
            emptyList()
        }
    }

    private fun parseVoiceFiles(json: String): List<VoiceFile> {
        return try {
            android.util.JsonReader(java.io.StringReader(json)).use { reader ->
                reader.beginArray()
                val voiceFiles = mutableListOf<VoiceFile>()
                while (reader.hasNext()) {
                    reader.beginObject()
                    var filename = ""
                    var label = ""
                    var internal = ""
                    var cat: String? = null
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "filename" -> filename = reader.nextString()
                            "label" -> label = reader.nextString()
                            "internal" -> internal = reader.nextString()
                            "cat" -> cat = reader.nextString()
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                    voiceFiles.add(VoiceFile(filename, label, internal, cat))
                }
                reader.endArray()
                voiceFiles
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse voice files: ${e.message}")
            emptyList()
        }
    }

    private suspend fun updateAudioFiles(metadata: MetadataFiles?) {
        val files = metadata?.voiceFiles?.map { voiceFile ->
            AudioFile(
                filename = voiceFile.filename,
                label = voiceFile.label,
                internal = voiceFile.internal,
                cat = voiceFile.cat?.lowercase(), // Normalize category names to lowercase
                isCustom = false
            )
        } ?: emptyList()
        
        Log.d(TAG, "Updated audio files: ${files.size} files loaded")
        _audioFiles.emit(files)
    }

    suspend fun loadMetadata() {
        val metadata = fetchMetadata()
        updateAudioFiles(metadata)
    }
}