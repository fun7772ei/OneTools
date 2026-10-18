package dev.onetools

import org.json.JSONObject
import java.io.IOException
import java.net.URI
import java.security.MessageDigest

internal const val UPDATE_REPOSITORY = "fun7772ei/OneTools"
internal const val UPDATE_RELEASES_URL = "https://github.com/$UPDATE_REPOSITORY/releases"
internal const val RELEASE_CERTIFICATE_SHA256 = "32eaacc7bafdac2673fa36c9102115ab0251030de5ca421dd6a6baa33e81b68d"
internal const val MAX_APK_BYTES = 150L * 1024 * 1024

internal class UpdateFailure(message: String) : IOException(message)

internal data class UpdateAsset(val name: String, val url: String, val size: Long, val sha256: String)
internal data class UpdateRelease(
    val versionCode: Long, val versionName: String, val minSdk: Int,
    val pageUrl: String, val notes: String, val apk: UpdateAsset
)

/** The repository chooses the release; metadata and the actual APK must agree. */
internal object UpdateMetadata {
    fun release(json: String): JSONObject {
        val release = JSONObject(json)
        requireUpdate(!release.getBoolean("draft") && !release.getBoolean("prerelease"))
        requireUpdate(release.getString("tag_name").matches(Regex("v[0-9]+\\.[0-9]+\\.[0-9]+")))
        requireUpdate(release.getString("html_url") == "$UPDATE_RELEASES_URL/tag/${release.getString("tag_name")}")
        return release
    }

    fun asset(release: JSONObject, name: String, limit: Long): UpdateAsset {
        val assets = release.getJSONArray("assets")
        val matches = (0 until assets.length()).map { assets.getJSONObject(it) }.filter { it.getString("name") == name }
        requireUpdate(matches.size == 1)
        val asset = matches.single()
        val tag = release.getString("tag_name")
        val url = "$UPDATE_RELEASES_URL/download/$tag/$name"
        val size = asset.getLong("size")
        val digest = asset.getString("digest")
        requireUpdate(asset.getString("state") == "uploaded" && asset.getString("browser_download_url") == url)
        requireUpdate(size in 1..limit && digest.matches(Regex("sha256:[a-f0-9]{64}")))
        return UpdateAsset(name, url, size, digest.removePrefix("sha256:"))
    }

    fun parse(release: JSONObject, json: String): UpdateRelease {
        val metadata = JSONObject(json)
        requireUpdate(metadata.getInt("schemaVersion") == 1 && metadata.getString("applicationId") == "dev.onetools")
        val code = metadata.getLong("versionCode")
        val version = metadata.getString("versionName")
        val minSdk = metadata.getInt("minSdk")
        requireUpdate(code in 1..Int.MAX_VALUE.toLong() && minSdk in 30..100)
        requireUpdate(release.getString("tag_name") == "v$version")
        val apk = asset(release, "OneTools-$version.apk", MAX_APK_BYTES)
        requireUpdate(metadata.getString("apkName") == apk.name && metadata.getString("sha256") == apk.sha256)
        return UpdateRelease(code, version, minSdk, release.getString("html_url"), release.optString("body").take(8_000), apk)
    }

    fun verifyBytes(asset: UpdateAsset, size: Long, digest: ByteArray) {
        if (size != asset.size || digest.hex() != asset.sha256) throw UpdateFailure("ダウンロードしたファイルの検証に失敗しました。もう一度お試しください。")
    }

    fun verifyIdentity(packageName: String, versionCode: Long, signers: Set<String>,
        expected: UpdateRelease, installedVersion: Long, installedSigners: Set<String>) {
        if (packageName != "dev.onetools" || versionCode != expected.versionCode || versionCode <= installedVersion ||
            signers != setOf(RELEASE_CERTIFICATE_SHA256) || signers != installedSigners) {
            throw UpdateFailure("アプリID・バージョン・署名が公開版と一致しません。このファイルはインストールできません。")
        }
    }

    /** Every redirect is checked before opening a connection. Never downgrade to HTTP. */
    fun allowedConnection(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.userInfo == null && uri.port == -1 && uri.fragment == null && when (uri.host) {
            "api.github.com" -> uri.path == "/repos/$UPDATE_REPOSITORY/releases/latest" && uri.query == null
            "github.com" -> uri.path.startsWith("/$UPDATE_REPOSITORY/releases/download/") && uri.query == null
            "release-assets.githubusercontent.com", "objects.githubusercontent.com" -> true
            else -> false
        }
    }.getOrDefault(false)

    private fun requireUpdate(condition: Boolean) {
        if (!condition) throw UpdateFailure("GitHubの更新情報を確認できませんでした。公開ページをご確認ください。")
    }
}

internal fun ByteArray.hex(): String = joinToString("") { "%02x".format(it.toInt() and 255) }
internal fun ByteArray.sha256(): ByteArray = MessageDigest.getInstance("SHA-256").digest(this)
