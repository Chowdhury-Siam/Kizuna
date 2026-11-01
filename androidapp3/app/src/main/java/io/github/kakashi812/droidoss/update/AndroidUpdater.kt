package io.github.kakashi812.droidoss.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import io.github.kakashi812.droidoss.BuildConfig
import kotlinx.coroutines.*
import java.io.File
import java.security.MessageDigest

class AndroidUpdater(private val context: Context, private val scope: CoroutineScope) {
    var status by mutableStateOf(if (BuildConfig.UPDATE_ENABLED) "Stable updates from GitHub" else "Preview build · install stable releases separately")
        private set
    var busy by mutableStateOf(false)
        private set
    var progress by mutableStateOf<Float?>(null)
        private set
    var available by mutableStateOf<AvailableUpdate?>(null)
        private set
    var ready by mutableStateOf<File?>(null)
        private set
    private val client = ReleaseClient(BuildConfig.RELEASE_REPOSITORY)
    fun check() {
        if (busy || !BuildConfig.UPDATE_ENABLED) return
        scope.launch {
            busy = true; status = "Checking stable releases…"
            try {
                val update = withContext(Dispatchers.IO) { client.check(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE.toLong()) }
                available = update; ready = null
                status = if (update == null) "You're up to date · ${BuildConfig.VERSION_NAME}" else "Kizuna ${update.manifest.version} is available"
            } catch (e: Exception) { if (e is CancellationException) throw e; status = e.message ?: "Could not check for updates. Try again." }
            finally { busy = false }
        }
    }
    fun download() {
        val update = available ?: return
        if (busy) return
        scope.launch {
            busy = true; progress = 0f; status = "Downloading ${update.manifest.version}…"
            try {
                ready = withContext(Dispatchers.IO) {
                    client.download(update, File(context.cacheDir, "updates")) { value -> scope.launch { progress = value } }.also { validateApk(it, update) }
                }
                status = "Verified update ready to install"
            } catch (e: Exception) { if (e is CancellationException) throw e; ready = null; status = e.message ?: "Download failed. Retry." }
            finally { busy = false; progress = null }
        }
    }
    @Suppress("DEPRECATION")
    private fun validateApk(file: File, update: AvailableUpdate) {
        UpdatePolicy.verify(file, update.asset)
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val apk = requireNotNull(pm.getPackageArchiveInfo(file.path, flags)) { "Downloaded file is not an APK" }
        val installed = pm.getPackageInfo(context.packageName, flags)
        require(apk.packageName == context.packageName) { "APK belongs to another app" }
        val versionCode = if (Build.VERSION.SDK_INT >= 28) apk.longVersionCode else apk.versionCode.toLong()
        require(versionCode == update.manifest.androidVersionCode && versionCode > BuildConfig.VERSION_CODE && apk.versionName == update.manifest.version) { "APK version mismatch" }
        fun signatures(info: android.content.pm.PackageInfo): Set<String> =
            (if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures).orEmpty().map {
                MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { byte -> "%02x".format(byte) }
            }.toSet()
        val current = signatures(installed)
        require(current.isNotEmpty() && current == signatures(apk)) { "APK signing key differs from this installation. Install a build signed with the same permanent key." }
    }
    /** Revalidate immediately before handing the APK to Android's installer. */
    suspend fun installerIntent(): Intent? {
        val file = ready ?: return null
        val update = available ?: return null
        return try {
            withContext(Dispatchers.IO) { validateApk(file, update) }
            Intent(Intent.ACTION_VIEW).setDataAndType(FileProvider.getUriForFile(context, "${context.packageName}.files", file), "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) { if (e is CancellationException) throw e; status = e.message ?: "Could not install update"; ready = null; null }
    }
    fun reportInstallFailure() { status = "Could not open Android's installer. Try again." }
}
