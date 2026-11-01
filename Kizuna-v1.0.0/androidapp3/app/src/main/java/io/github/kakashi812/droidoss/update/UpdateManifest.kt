package io.github.kakashi812.droidoss.update

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import java.security.MessageDigest
import java.io.File

@Serializable
data class UpdateAsset(val name: String, val sha256: String, val size: Long)
@Serializable
data class UpdateManifest(val schema: Int, val repository: String, val version: String, val androidVersionCode: Long, val assets: Map<String, UpdateAsset>)
@Serializable
data class GithubAsset(val name: String, val browser_download_url: String)
@Serializable
data class GithubRelease(val tag_name: String, val draft: Boolean, val prerelease: Boolean, val assets: List<GithubAsset>)
data class AvailableUpdate(val manifest: UpdateManifest, val asset: UpdateAsset, val url: String)

object UpdatePolicy {
    private val json = Json { ignoreUnknownKeys = true }
    const val MAX_DOWNLOAD = 512L * 1024 * 1024
    fun version(value: String): List<Int> {
        require(Regex("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)").matches(value)) { "Invalid stable version" }
        return value.split('.').map { it.toInt() }
    }
    fun newer(candidate: String, current: String): Boolean {
        val a = version(candidate); val b = version(current)
        return a.zip(b).firstOrNull { it.first != it.second }?.let { it.first > it.second } ?: false
    }
    fun repository(value: String): String {
        require(Regex("[A-Za-z0-9_-]+/[A-Za-z0-9_.-]+").matches(value) && value.substringAfter('/') !in listOf(".", "..")) { "Invalid release repository" }
        return value
    }
    fun assetUrl(repo: String, tag: String, name: String): String {
        repository(repo); require(tag.startsWith("v")); version(tag.drop(1))
        require(Regex("[A-Za-z0-9._-]+").matches(name) && name !in listOf(".", ".."))
        return "https://github.com/$repo/releases/download/$tag/$name"
    }
    fun release(raw: String, repo: String): GithubRelease {
        repository(repo)
        val release = json.decodeFromString<GithubRelease>(raw)
        require(!release.draft && !release.prerelease && release.tag_name.startsWith("v")) { "Only stable releases can update Kizuna" }
        version(release.tag_name.drop(1))
        require(release.assets.count { it.name == "update.json" } == 1) { "This release has no update manifest" }
        require(release.assets.single { it.name == "update.json" }.browser_download_url == assetUrl(repo, release.tag_name, "update.json")) { "Untrusted update manifest URL" }
        return release
    }
    fun select(raw: String, release: GithubRelease, repo: String, currentVersion: String, currentCode: Long): AvailableUpdate? {
        val manifest = json.decodeFromString<UpdateManifest>(raw)
        require(manifest.schema == 1 && manifest.repository == repository(repo)) { "Update repository/schema mismatch" }
        require(release.tag_name == "v${manifest.version}" && !release.draft && !release.prerelease)
        version(manifest.version)
        if (!newer(manifest.version, currentVersion)) return null
        require(manifest.androidVersionCode > currentCode) { "Update version code must increase" }
        val asset = requireNotNull(manifest.assets["android"]) { "No Android APK in this release" }
        require(asset.name == "Kizuna-v${manifest.version}-android.apk") { "Unexpected APK name" }
        require(asset.size in 1..MAX_DOWNLOAD && Regex("[a-f0-9]{64}").matches(asset.sha256)) { "Invalid APK integrity data" }
        val url = assetUrl(repo, release.tag_name, asset.name)
        require(release.assets.count { it.name == asset.name && it.browser_download_url == url } == 1) { "APK does not belong to this release" }
        return AvailableUpdate(manifest, asset, url)
    }
    fun verify(file: File, asset: UpdateAsset) {
        require(file.length() == asset.size) { "Incomplete update download" }
        val hash = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buffer = ByteArray(65536); while (true) { val n = input.read(buffer); if (n < 0) break; hash.update(buffer, 0, n) } }
        require(hash.digest().joinToString("") { "%02x".format(it) } == asset.sha256) { "Update checksum does not match" }
    }
}
