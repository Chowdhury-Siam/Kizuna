package io.github.kakashi812.droidoss.update

import java.net.HttpURLConnection
import java.net.URI
import java.io.File
import java.io.IOException

/** Only GitHub HTTPS metadata/assets are accepted, including redirect targets. */
class ReleaseClient(private val repo: String) {
    private fun open(url: String): HttpURLConnection {
        var target = url
        repeat(6) {
            val uri = URI(target)
            require(uri.scheme == "https" && uri.host in setOf("api.github.com", "github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com") && uri.userInfo == null && uri.port in listOf(-1, 443)) { "Untrusted update host" }
            val connection = uri.toURL().openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15000; connection.readTimeout = 30000
            connection.setRequestProperty("User-Agent", "Kizuna-Updater")
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            val code = connection.responseCode
            if (code in listOf(301, 302, 303, 307, 308)) {
                val next = connection.getHeaderField("Location")
                connection.disconnect()
                target = uri.resolve(next ?: throw IOException("Invalid download redirect")).toString()
            } else {
                if (code != 200) { connection.disconnect(); throw IOException(if (code == 404) "No stable release is available yet for $repo." else "GitHub returned HTTP $code. Try again later.") }
                return connection
            }
        }
        throw IOException("Too many update redirects")
    }
    private fun text(url: String, limit: Int): String {
        val connection = open(url)
        return try { connection.inputStream.use { input ->
            val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
            while (true) { val n = input.read(buffer); if (n < 0) break; require(output.size() + n <= limit) { "Update metadata is too large" }; output.write(buffer, 0, n) }
            output.toString("UTF-8") } }
        finally { connection.disconnect() }
    }
    fun check(version: String, code: Long): AvailableUpdate? {
        UpdatePolicy.repository(repo)
        val release = UpdatePolicy.release(text("https://api.github.com/repos/$repo/releases/latest", 1024 * 1024), repo)
        return UpdatePolicy.select(text(UpdatePolicy.assetUrl(repo, release.tag_name, "update.json"), 65536), release, repo, version, code)
    }
    fun download(update: AvailableUpdate, directory: File, onProgress: (Float) -> Unit): File {
        directory.mkdirs()
        val part = File(directory, "update.part"); val result = File(directory, update.asset.name)
        val connection = open(update.url)
        try {
            connection.inputStream.use { input -> part.outputStream().use { output ->
                val buffer = ByteArray(65536); var total = 0L; var percent = -1
                while (true) {
                    val n = input.read(buffer); if (n < 0) break
                    total += n; require(total <= update.asset.size && total <= UpdatePolicy.MAX_DOWNLOAD) { "Update download exceeds expected size" }
                    output.write(buffer, 0, n)
                    val next = (total * 100 / update.asset.size).toInt()
                    if (next != percent) { percent = next; onProgress(total.toFloat() / update.asset.size) }
                }
            } }
            UpdatePolicy.verify(part, update.asset)
            java.nio.file.Files.move(part.toPath(), result.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            return result
        } finally { connection.disconnect(); part.delete() }
    }
}
