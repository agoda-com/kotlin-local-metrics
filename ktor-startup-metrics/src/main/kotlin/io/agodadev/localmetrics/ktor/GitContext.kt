package io.agodadev.localmetrics.ktor

import java.io.File
import java.net.URI
import java.util.concurrent.TimeUnit

internal data class GitContext(
    val repository: String?,
    val repositoryName: String?,
    val branch: String?,
    val commitSha: String?,
)

internal object GitContextReader {
    fun read(workingDirectory: File): GitContext {
        val repository = git(workingDirectory, "config", "--get", "remote.origin.url")
            ?.removeCredentials()
        return GitContext(
            repository = repository,
            repositoryName = repository?.repositoryName(),
            branch = git(workingDirectory, "rev-parse", "--abbrev-ref", "HEAD"),
            commitSha = git(workingDirectory, "rev-parse", "HEAD"),
        )
    }

    private fun git(workingDirectory: File, vararg arguments: String): String? =
        runCatching {
            val process = ProcessBuilder(
                listOf("git", "-C", workingDirectory.absolutePath) + arguments,
            )
                .redirectErrorStream(true)
                .start()

            if (!process.waitFor(GIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
                return null
            }

            process.inputStream.bufferedReader().use { reader ->
                reader.readLine()?.takeIf { process.exitValue() == 0 && it.isNotBlank() }
            }
        }.getOrNull()

    private fun String.removeCredentials(): String =
        runCatching {
            val uri = URI(this)
            if (uri.userInfo == null) {
                this
            } else {
                URI(
                    uri.scheme,
                    null,
                    uri.host,
                    uri.port,
                    uri.path,
                    uri.query,
                    uri.fragment,
                ).toString()
            }
        }.getOrDefault(replace(Regex("""(https?://)[^/@]+@"""), "$1"))

    private fun String.repositoryName(): String =
        trimEnd('/')
            .substringAfterLast('/')
            .substringAfterLast(':')
            .removeSuffix(".git")

    private const val GIT_TIMEOUT_MILLIS = 500L
}
