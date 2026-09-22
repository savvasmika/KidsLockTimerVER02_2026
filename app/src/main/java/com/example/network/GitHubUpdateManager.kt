package com.example.network

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed class UpdateCheckState {
    object Idle : UpdateCheckState()
    object Checking : UpdateCheckState()
    data class UpdateAvailable(
        val latestVersion: String,
        val releaseName: String,
        val changelog: String,
        val apkDownloadUrl: String?,
        val htmlUrl: String,
        val publishedAt: String
    ) : UpdateCheckState()
    data class UpToDate(val currentVersion: String) : UpdateCheckState()
    data class Error(val message: String, val fallbackUrl: String? = null) : UpdateCheckState()
}

class GitHubUpdateManager(
    private val context: Context,
    private val defaultRepoOwner: String = "savvasmika",
    private val defaultRepoName: String = "KidsLockTimerVER02_2026"
) {
    companion object {
        private const val TAG = "GitHubUpdateManager"
        const val CURRENT_APP_VERSION = "1.0.0"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    private val _updateState = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)
    val updateState: StateFlow<UpdateCheckState> = _updateState.asStateFlow()

    suspend fun checkForUpdates(
        owner: String = defaultRepoOwner,
        repo: String = defaultRepoName
    ): UpdateCheckState = withContext(Dispatchers.IO) {
        _updateState.value = UpdateCheckState.Checking
        val cleanOwner = owner.trim().ifBlank { defaultRepoOwner }
        val cleanRepo = repo.trim().ifBlank { defaultRepoName }
        val fallbackReleasesUrl = "https://github.com/$cleanOwner/$cleanRepo/releases"

        try {
            Log.d(TAG, "Checking for GitHub updates at $cleanOwner/$cleanRepo...")
            
            // 1. Try Releases API
            val releaseUrl = "https://api.github.com/repos/$cleanOwner/$cleanRepo/releases"
            val request = Request.Builder()
                .url(releaseUrl)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "KidLock-Android-App")
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val bodyString = response.body?.string() ?: "[]"
                val releasesArray = JSONArray(bodyString)
                if (releasesArray.length() > 0) {
                    val latestRelease = releasesArray.getJSONObject(0)
                    val tagName = latestRelease.optString("tag_name", "").replace("v", "").trim()
                    val releaseName = latestRelease.optString("name", "Release $tagName")
                    val changelog = latestRelease.optString("body", "Official KidLock release update.")
                    val htmlUrl = latestRelease.optString("html_url", fallbackReleasesUrl)
                    val publishedAt = latestRelease.optString("published_at", "")

                    var apkDownloadUrl: String? = null
                    val assetsArray = latestRelease.optJSONArray("assets")
                    if (assetsArray != null) {
                        for (i in 0 until assetsArray.length()) {
                            val asset = assetsArray.getJSONObject(i)
                            val name = asset.optString("name", "")
                            if (name.endsWith(".apk", ignoreCase = true)) {
                                apkDownloadUrl = asset.optString("browser_download_url", null)
                                break
                            }
                        }
                    }

                    val hasNewer = isVersionNewer(tagName, CURRENT_APP_VERSION)
                    val result = if (hasNewer || apkDownloadUrl != null) {
                        UpdateCheckState.UpdateAvailable(
                            latestVersion = tagName.ifBlank { "Latest" },
                            releaseName = releaseName,
                            changelog = changelog,
                            apkDownloadUrl = apkDownloadUrl ?: htmlUrl,
                            htmlUrl = htmlUrl,
                            publishedAt = publishedAt
                        )
                    } else {
                        UpdateCheckState.UpToDate(CURRENT_APP_VERSION)
                    }
                    _updateState.value = result
                    return@withContext result
                }
            }

            // 2. Fallback: Check Commits
            Log.d(TAG, "No releases found. Checking latest commits for $cleanOwner/$cleanRepo...")
            val commitsUrl = "https://api.github.com/repos/$cleanOwner/$cleanRepo/commits"
            val commitReq = Request.Builder()
                .url(commitsUrl)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "KidLock-Android-App")
                .build()

            val commitResp = httpClient.newCall(commitReq).execute()
            if (commitResp.isSuccessful) {
                val commitBody = commitResp.body?.string() ?: "[]"
                val commitArray = JSONArray(commitBody)
                if (commitArray.length() > 0) {
                    val latestCommit = commitArray.getJSONObject(0)
                    val commitObj = latestCommit.optJSONObject("commit")
                    val message = commitObj?.optString("message", "Latest GitHub commit") ?: "Latest changes"
                    val htmlUrl = latestCommit.optString("html_url", "https://github.com/$cleanOwner/$cleanRepo")
                    val date = commitObj?.optJSONObject("author")?.optString("date", "") ?: ""

                    val result = UpdateCheckState.UpdateAvailable(
                        latestVersion = "Git-Latest",
                        releaseName = "New GitHub Build / Commits",
                        changelog = message,
                        apkDownloadUrl = fallbackReleasesUrl,
                        htmlUrl = fallbackReleasesUrl,
                        publishedAt = date
                    )
                    _updateState.value = result
                    return@withContext result
                }
            }

            val errorState = UpdateCheckState.Error(
                message = "Could not find releases on GitHub for $cleanOwner/$cleanRepo.",
                fallbackUrl = fallbackReleasesUrl
            )
            _updateState.value = errorState
            return@withContext errorState
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for GitHub updates", e)
            val errorState = UpdateCheckState.Error(
                message = e.localizedMessage ?: "Network connection error while checking GitHub.",
                fallbackUrl = fallbackReleasesUrl
            )
            _updateState.value = errorState
            return@withContext errorState
        }
    }

    fun openDownloadUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open update URL", e)
        }
    }

    private fun isVersionNewer(remoteVersion: String, localVersion: String): Boolean {
        if (remoteVersion.isBlank()) return false
        val remoteParts = remoteVersion.split(".").mapNotNull { it.toIntOrNull() }
        val localParts = localVersion.split(".").mapNotNull { it.toIntOrNull() }

        val maxLength = maxOf(remoteParts.size, localParts.size)
        for (i in 0 until maxLength) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }

    fun resetState() {
        _updateState.value = UpdateCheckState.Idle
    }
}
