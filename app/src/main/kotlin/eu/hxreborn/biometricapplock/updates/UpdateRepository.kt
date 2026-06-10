package eu.hxreborn.biometricapplock.updates

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.core.content.edit
import eu.hxreborn.biometricapplock.BuildConfig
import eu.hxreborn.biometricapplock.prefs.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI

private const val TAG = "BiometricAppLock"
private const val REPO = "hxreborn/biometric-app-lock"
private const val RELEASE_URL = "https://api.github.com/repos/$REPO/releases/latest"
private const val CHANGELOG_URL = "https://raw.githubusercontent.com/$REPO/main/CHANGELOG.json"
private const val GRADLE_PROPS_URL_FORMAT =
    "https://raw.githubusercontent.com/$REPO/%s/gradle.properties"
private const val VERSION_CODE_KEY = "version.code="
private const val AUTO_CHECK_THROTTLE_MS = 6 * 60 * 60 * 1000L
private const val CONNECT_TIMEOUT_MS = 5_000
private const val READ_TIMEOUT_MS = 8_000

class UpdateRepository(
    private val app: Application,
) {
    private val prefs = app.getSharedPreferences(Prefs.GROUP, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    val bundledChangelog: List<ChangelogEntry> by lazy {
        runCatching {
            val raw =
                app.assets
                    .open("changelog.json")
                    .bufferedReader()
                    .use { it.readText() }
            json.decodeFromString<ChangelogManifest>(raw).entries
        }.getOrElse {
            Log.w(TAG, "bundled changelog unavailable: ${it.message}")
            emptyList()
        }
    }

    private val _cachedAvailable = MutableStateFlow<UpdateState.Available?>(null)
    val cachedAvailable: StateFlow<UpdateState.Available?> = _cachedAvailable.asStateFlow()

    private val _cachedChangelog = MutableStateFlow<List<ChangelogEntry>?>(null)
    val cachedChangelog: StateFlow<List<ChangelogEntry>?> = _cachedChangelog.asStateFlow()

    private val lastCheckedEpochMs = MutableStateFlow(Prefs.LAST_UPDATE_CHECK_MS.read(prefs))

    private val _currentState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val currentState: StateFlow<UpdateState> = _currentState.asStateFlow()

    private val checkMutex = Mutex()

    init {
        val cachedRelease = Prefs.LAST_RELEASE_JSON.read(prefs)
        val cachedRemoteCode = Prefs.LAST_REMOTE_VERSION_CODE.read(prefs)
        if (cachedRelease.isNotEmpty() && cachedRemoteCode > 0) {
            runCatching { compareRelease(cachedRelease, cachedRemoteCode) }
        }
        val cachedJson = Prefs.LAST_CHANGELOG_JSON.read(prefs)
        if (cachedJson.isNotEmpty()) {
            runCatching {
                _cachedChangelog.value =
                    json.decodeFromString<ChangelogManifest>(cachedJson).entries
            }
        }
    }

    suspend fun checkNow(): UpdateState =
        withContext(Dispatchers.IO) {
            if (!checkMutex.tryLock()) return@withContext _currentState.value
            try {
                _currentState.value = UpdateState.Checking
                val result =
                    if (!isOnline()) {
                        UpdateState.Failed(FailureCause.Offline)
                    } else {
                        fetchRelease().also {
                            if (it is UpdateState.UpToDate || it is UpdateState.Available) {
                                val now = System.currentTimeMillis()
                                prefs.edit { Prefs.LAST_UPDATE_CHECK_MS.write(this, now) }
                                lastCheckedEpochMs.value = now
                            }
                        }
                    }
                _currentState.value = result
                result
            } finally {
                checkMutex.unlock()
            }
        }

    suspend fun maybeAutoCheck() {
        if (!Prefs.AUTO_CHECK_UPDATE.read(prefs)) return
        val elapsed = System.currentTimeMillis() - Prefs.LAST_UPDATE_CHECK_MS.read(prefs)
        if (elapsed < AUTO_CHECK_THROTTLE_MS) return
        checkNow()
    }

    suspend fun fetchChangelog(): List<ChangelogEntry>? =
        withContext(Dispatchers.IO) {
            val fallback = _cachedChangelog.value
            if (!isOnline()) return@withContext fallback
            runCatching {
                val etag = Prefs.CHANGELOG_ETAG.read(prefs)
                val conn = openConnection(CHANGELOG_URL, etag)
                when (conn.responseCode) {
                    304 -> {
                        fallback
                    }

                    200 -> {
                        val raw = conn.inputStream.bufferedReader().use { it.readText() }
                        val newEtag = conn.getHeaderField("ETag")
                        val entries = json.decodeFromString<ChangelogManifest>(raw).entries
                        _cachedChangelog.value = entries
                        prefs.edit {
                            Prefs.LAST_CHANGELOG_JSON.write(this, raw)
                            if (newEtag != null) Prefs.CHANGELOG_ETAG.write(this, newEtag)
                        }
                        entries
                    }

                    else -> {
                        fallback
                    }
                }
            }.getOrElse {
                Log.w(TAG, "changelog fetch failed: ${it.message}")
                fallback
            }
        }

    private fun compareRelease(
        body: String,
        remoteVersionCode: Int,
    ): UpdateState {
        val obj = JSONObject(body)
        val tag = obj.getString("tag_name").trimStart('v', 'V')
        val url = obj.getString("html_url")
        val current = BuildConfig.VERSION_NAME
        return if (remoteVersionCode > BuildConfig.VERSION_CODE) {
            UpdateState.Available(current, tag, url).also { _cachedAvailable.value = it }
        } else {
            _cachedAvailable.value = null
            UpdateState.UpToDate(current)
        }
    }

    private fun parseVersionCode(gradleProps: String): Int? =
        gradleProps
            .lineSequence()
            .firstOrNull { it.startsWith(VERSION_CODE_KEY) }
            ?.removePrefix(VERSION_CODE_KEY)
            ?.trim()
            ?.toIntOrNull()

    private fun fetchRemoteVersionCode(tag: String): Int? =
        runCatching {
            val conn = openConnection(GRADLE_PROPS_URL_FORMAT.format(tag))
            if (conn.responseCode != 200) return@runCatching null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            parseVersionCode(body)
        }.getOrNull()

    private fun fetchRelease(): UpdateState =
        try {
            val etag = Prefs.RELEASE_ETAG.read(prefs)
            val conn = openConnection(RELEASE_URL, etag)
            val code = conn.responseCode
            val isPrimaryRateLimit =
                code == 403 && conn.getHeaderField("X-RateLimit-Remaining") == "0"
            val isSecondaryRateLimit = code == 429
            when {
                code == 304 -> {
                    val cached = Prefs.LAST_RELEASE_JSON.read(prefs)
                    val cachedCode = Prefs.LAST_REMOTE_VERSION_CODE.read(prefs)
                    if (cached.isNotEmpty() && cachedCode > 0) {
                        compareRelease(cached, cachedCode)
                    } else {
                        UpdateState.UpToDate(BuildConfig.VERSION_NAME)
                    }
                }

                isPrimaryRateLimit -> {
                    val resetMs =
                        conn
                            .getHeaderField(
                                "X-RateLimit-Reset",
                            )?.toLongOrNull()
                            ?.times(1000)
                    UpdateState.RateLimited(resetMs)
                }

                isSecondaryRateLimit -> {
                    val retryAfterSecs = conn.getHeaderField("Retry-After")?.toLongOrNull()
                    val resetMs = retryAfterSecs?.let { System.currentTimeMillis() + it * 1000 }
                    UpdateState.RateLimited(resetMs)
                }

                code in 500..599 -> {
                    Log.w(TAG, "update check http=$code")
                    UpdateState.Failed(FailureCause.ServiceUnavailable)
                }

                code != 200 -> {
                    Log.w(TAG, "update check http=$code")
                    UpdateState.Failed(FailureCause.Network)
                }

                else -> {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val tag = JSONObject(body).getString("tag_name")
                    val remoteCode =
                        fetchRemoteVersionCode(tag) ?: run {
                            Log.w(TAG, "could not resolve remote version.code for tag=$tag")
                            return UpdateState.Failed(FailureCause.Parse)
                        }
                    prefs.edit {
                        Prefs.LAST_RELEASE_JSON.write(this, body)
                        Prefs.LAST_REMOTE_VERSION_CODE.write(this, remoteCode)
                        val newEtag = conn.getHeaderField("ETag")
                        if (newEtag != null) Prefs.RELEASE_ETAG.write(this, newEtag)
                    }
                    compareRelease(body, remoteCode)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "update check exception: ${e.message}")
            UpdateState.Failed(FailureCause.Network)
        }

    private fun openConnection(
        urlString: String,
        etag: String = "",
    ): HttpURLConnection =
        (URI(urlString).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("User-Agent", "biometric-app-lock/${BuildConfig.VERSION_NAME}")
            setRequestProperty("Accept", "application/vnd.github+json")
            if (etag.isNotEmpty()) setRequestProperty("If-None-Match", etag)
        }

    private fun isOnline(): Boolean {
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_VALIDATED,
            )
    }
}
