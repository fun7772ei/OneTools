package dev.onetools

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest

internal class GithubUpdateClient {
    suspend fun latest(): UpdateRelease {
        val release = UpdateMetadata.release(String(read("https://api.github.com/repos/$UPDATE_REPOSITORY/releases/latest", 512 * 1024), Charsets.UTF_8))
        val metadata = UpdateMetadata.asset(release, "update.json", 32 * 1024)
        val bytes = read(metadata.url, metadata.size)
        UpdateMetadata.verifyBytes(metadata, bytes.size.toLong(), bytes.sha256())
        return UpdateMetadata.parse(release, String(bytes, Charsets.UTF_8))
    }

    suspend fun download(release: UpdateRelease, directory: File, progress: (Float) -> Unit): File {
        directory.mkdirs()
        if (directory.usableSpace < release.apk.size + 8 * 1024 * 1024) throw UpdateFailure("保存容量が不足しています。空き容量を確保してください。")
        val partial = File(directory, "update.part")
        val target = File(directory, "update.apk")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val size = partial.outputStream().use { output ->
                transfer(release.apk.url, release.apk.size, output) { bytes, count, total ->
                    digest.update(bytes, 0, count)
                    progress(total.toFloat() / release.apk.size)
                }
            }
            UpdateMetadata.verifyBytes(release.apk, size, digest.digest())
            if (target.exists() && !target.delete()) throw UpdateFailure("更新ファイルを準備できませんでした。")
            if (!partial.renameTo(target)) throw UpdateFailure("更新ファイルを準備できませんでした。")
            return target
        } finally {
            partial.delete()
        }
    }

    private suspend fun read(url: String, limit: Long): ByteArray = ByteArrayOutputStream().use {
        transfer(url, limit, it)
        it.toByteArray()
    }

    private suspend fun transfer(url: String, limit: Long, output: OutputStream,
        onBytes: (ByteArray, Int, Long) -> Unit = { _, _, _ -> }): Long {
        var destination = url
        val deadline = System.nanoTime() + 180_000_000_000L
        repeat(6) { hop ->
            currentCoroutineContext().ensureActive()
            if (!UpdateMetadata.allowedConnection(destination)) throw UpdateFailure("安全なダウンロード先を確認できませんでした。")
            val connection = URL(destination).openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 15_000
                connection.readTimeout = 20_000
                connection.setRequestProperty("User-Agent", "OneTools/${BuildConfig.VERSION_NAME}")
                connection.setRequestProperty("Accept", if (URI(destination).host == "api.github.com") "application/vnd.github+json" else "application/octet-stream")
                connection.setRequestProperty("Accept-Encoding", "identity")
                val status = connection.responseCode
                if (status in listOf(301, 302, 303, 307, 308)) {
                    val location = connection.getHeaderField("Location") ?: throw UpdateFailure("ダウンロード先を確認できませんでした。")
                    if (hop == 5) throw UpdateFailure("ダウンロード先の転送が多すぎます。")
                    destination = URI(destination).resolve(location).toString()
                } else {
                    when (status) {
                        200 -> Unit
                        403, 429 -> throw UpdateFailure("GitHubの利用制限に達しました。しばらく待ってから再確認してください。")
                        404 -> throw UpdateFailure("公開された更新情報が見つかりません。公開ページをご確認ください。")
                        else -> throw UpdateFailure("GitHubに接続できませんでした（HTTP $status）。時間をおいてお試しください。")
                    }
                    if (connection.contentLengthLong > limit) throw UpdateFailure("更新ファイルのサイズが想定と異なります。")
                    var total = 0L
                    connection.inputStream.use { input ->
                        val buffer = ByteArray(32 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            if (System.nanoTime() > deadline) throw UpdateFailure("通信がタイムアウトしました。もう一度お試しください。")
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > limit) throw UpdateFailure("更新ファイルのサイズが想定と異なります。")
                            output.write(buffer, 0, count)
                            // Hash every byte; the caller throttles state updates.
                            onBytes(buffer, count, total)
                        }
                    }
                    return total
                }
            } finally { connection.disconnect() }
        }
        throw UpdateFailure("ダウンロード先を確認できませんでした。")
    }
}
