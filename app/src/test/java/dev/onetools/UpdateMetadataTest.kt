package dev.onetools

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class UpdateMetadataTest {
    private val digest = "a".repeat(64)
    private fun release(): JSONObject = JSONObject().put("draft", false).put("prerelease", false)
        .put("tag_name", "v1.9.0").put("html_url", "$UPDATE_RELEASES_URL/tag/v1.9.0")
        .put("body", "更新内容")
        .put("assets", JSONArray().put(asset("OneTools-1.9.0.apk", 23_000_000)).put(asset("update.json", 256)))
    private fun asset(name: String, size: Long): JSONObject = JSONObject().put("name", name).put("state", "uploaded")
        .put("size", size).put("digest", "sha256:$digest")
        .put("browser_download_url", "$UPDATE_RELEASES_URL/download/v1.9.0/$name")
    private fun manifest(): JSONObject = JSONObject().put("schemaVersion", 1).put("applicationId", "dev.onetools")
        .put("versionCode", 18).put("versionName", "1.9.0").put("minSdk", 30)
        .put("apkName", "OneTools-1.9.0.apk").put("sha256", digest)
    private fun rejected(block: () -> Unit) {
        try { block(); fail("Expected update rejection") } catch (_: UpdateFailure) { }
    }

    @Test fun publishedMetadataMustAgreeWithGithubReleaseAndAsset() {
        val parsed = UpdateMetadata.parse(UpdateMetadata.release(release().toString()), manifest().toString())
        assertEquals(18L, parsed.versionCode)
        assertEquals("1.9.0", parsed.versionName)
        assertEquals(digest, parsed.apk.sha256)
        assertEquals("更新内容", parsed.notes)
    }

    @Test fun draftPrereleaseMalformedTagAndForeignReleaseAreRejected() {
        listOf(release().put("draft", true), release().put("prerelease", true),
            release().put("tag_name", "v1.9.0-beta"), release().put("html_url", "https://example.com/release")).forEach {
            rejected { UpdateMetadata.release(it.toString()) }
        }
    }

    @Test fun metadataCannotSelectAnotherApplicationAssetVersionOrDigest() {
        listOf(manifest().put("applicationId", "other.app"), manifest().put("versionCode", 0),
            manifest().put("versionName", "1.9.1"), manifest().put("apkName", "other.apk"),
            manifest().put("sha256", "b".repeat(64)), manifest().put("schemaVersion", 2)).forEach {
            rejected { UpdateMetadata.parse(release(), it.toString()) }
        }
    }

    @Test fun missingDuplicateOversizedOrForeignAssetsAreRejected() {
        val variants = listOf(
            JSONArray(),
            JSONArray().put(asset("OneTools-1.9.0.apk", 10)).put(asset("OneTools-1.9.0.apk", 10)),
            JSONArray().put(asset("OneTools-1.9.0.apk", MAX_APK_BYTES + 1)),
            JSONArray().put(asset("OneTools-1.9.0.apk", 10).put("browser_download_url", "https://example.com/app.apk")),
            JSONArray().put(asset("OneTools-1.9.0.apk", 10).put("digest", "")),
            JSONArray().put(asset("OneTools-1.9.0.apk", 10).put("state", "new"))
        )
        variants.forEach { rejected { UpdateMetadata.parse(release().put("assets", it), manifest().toString()) } }
    }

    @Test fun onlyHttpsGithubConnectionsWithoutUserinfoOrCustomPortsAreAllowed() {
        assertTrue(UpdateMetadata.allowedConnection("https://api.github.com/repos/$UPDATE_REPOSITORY/releases/latest"))
        assertTrue(UpdateMetadata.allowedConnection("$UPDATE_RELEASES_URL/download/v1.9.0/OneTools-1.9.0.apk"))
        assertTrue(UpdateMetadata.allowedConnection("https://release-assets.githubusercontent.com/path?signature=value"))
        listOf("http://github.com/$UPDATE_REPOSITORY/releases/download/a/b", "https://example.com/app.apk",
            "https://github.com.evil.test/file", "https://user@github.com/$UPDATE_REPOSITORY/releases/download/a/b",
            "https://github.com:444/$UPDATE_REPOSITORY/releases/download/a/b", "https://api.github.com/user",
            "https://github.com/another/repo/releases/download/a/b").forEach { assertFalse(it, UpdateMetadata.allowedConnection(it)) }
    }

    @Test fun truncationAndModifiedBytesAreRejected() {
        val bytes = "verified APK bytes".toByteArray()
        val asset = UpdateAsset("a.apk", "", bytes.size.toLong(), bytes.sha256().hex())
        UpdateMetadata.verifyBytes(asset, bytes.size.toLong(), bytes.sha256())
        rejected { UpdateMetadata.verifyBytes(asset, bytes.size.toLong() - 1, bytes.sha256()) }
        rejected { UpdateMetadata.verifyBytes(asset, bytes.size.toLong(), "tampered".toByteArray().sha256()) }
    }

    @Test fun onlyNewerReleaseWithExpectedAndInstalledSigningCertificateIsAccepted() {
        val release = UpdateMetadata.parse(release(), manifest().toString())
        val signers = setOf(RELEASE_CERTIFICATE_SHA256)
        UpdateMetadata.verifyIdentity("dev.onetools", 18, signers, release, 17, signers)
        rejected { UpdateMetadata.verifyIdentity("other.app", 18, signers, release, 17, signers) }
        rejected { UpdateMetadata.verifyIdentity("dev.onetools", 19, signers, release, 17, signers) }
        rejected { UpdateMetadata.verifyIdentity("dev.onetools", 18, signers, release, 18, signers) }
        rejected { UpdateMetadata.verifyIdentity("dev.onetools", 18, setOf("debug"), release, 17, signers) }
        rejected { UpdateMetadata.verifyIdentity("dev.onetools", 18, signers, release, 17, setOf("debug")) }
        rejected { UpdateMetadata.verifyIdentity("dev.onetools", 18, emptySet(), release, 17, emptySet()) }
    }
}
