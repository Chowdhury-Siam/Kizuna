package io.github.kakashi812.droidoss.update

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class UpdateTest {
    private val repo = "Chowdhury-Siam/Kizuna"
    private fun fixture(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()
    private fun release(raw: String = fixture("github-release.json")) = UpdatePolicy.release(raw, repo)
    private fun rejected(block: () -> Unit) { try { block(); fail("Untrusted metadata accepted") } catch (_: IllegalArgumentException) {} }
    @Test fun sharedManifestSelectsOnlyNewerStableApk() {
        val update = UpdatePolicy.select(fixture("update.json"), release(), repo, "1.0.0", 7)!!
        assertEquals("Kizuna-v1.1.0-android.apk", update.asset.name)
        assertNull(UpdatePolicy.select(fixture("update.json"), release(), repo, "1.1.0", 8))
        assertNull(UpdatePolicy.select(fixture("update.json"), release(), repo, "2.0.0", 9))
        rejected { UpdatePolicy.select(fixture("update.json"), release(), repo, "1.0.0", 8) }
    }
    @Test fun stableVersionOrderingIsNumericAndRejectsPrereleases() {
        assertTrue(UpdatePolicy.newer("1.10.0", "1.9.9"))
        assertFalse(UpdatePolicy.newer("1.0.0", "1.0.0"))
        for (v in listOf("1.2.3-beta", "v1.2.3", "01.2.3", "1.2", "1.2.3.4", "99999999999999.0.0")) rejected { UpdatePolicy.version(v) }
    }
    @Test fun prereleaseDraftAndExternalManifestUrlsAreRejected() {
        for (raw in listOf(fixture("github-release.json").replace("\"prerelease\": false", "\"prerelease\": true"), fixture("github-release.json").replace("\"draft\": false", "\"draft\": true"), fixture("github-release.json").replace("https://github.com", "https://evil.example"))) rejected { release(raw) }
    }
    @Test fun mismatchedManifestAndInstallerDataAreRejected() {
        val raw = fixture("update.json")
        for (changed in listOf(raw.replace("\"schema\": 1", "\"schema\": 2"), raw.replace("Chowdhury-Siam/Kizuna", "Other/Repo"), raw.replace("1.1.0", "1.2.0"), raw.replace("\"size\": 3", "\"size\": -1"), raw.replace("android.apk", "../android.apk"))) rejected { UpdatePolicy.select(changed, release(), repo, "1.0.0", 7) }
    }
    @Test fun truncatedOrTamperedDownloadIsRejected() {
        val update = UpdatePolicy.select(fixture("update.json"), release(), repo, "1.0.0", 7)!!
        val file = File.createTempFile("kizuna", ".apk")
        try {
            file.writeText("APK"); UpdatePolicy.verify(file, update.asset)
            file.writeText("AP"); rejected { UpdatePolicy.verify(file, update.asset) }
            file.writeText("BAD"); rejected { UpdatePolicy.verify(file, update.asset) }
        } finally { file.delete() }
    }
    @Test fun repositoryAndAssetPathsCannotEscapeRelease() {
        for (v in listOf("bad", "owner/../x", "owner/..", "owner/repo?x", "https://github.com/a/b")) rejected { UpdatePolicy.repository(v) }
        rejected { UpdatePolicy.assetUrl(repo, "v1.1.0", "../update.json") }
    }
}
