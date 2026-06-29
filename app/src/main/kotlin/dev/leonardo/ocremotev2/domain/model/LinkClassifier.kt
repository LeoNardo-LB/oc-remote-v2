package dev.leonardo.ocremotev2.domain.model

/**
 * Classifies a URL string from a markdown `[text](url)` link into one of three types.
 * Pure Kotlin — no Android dependencies.
 */
sealed interface LinkTarget {
    /** Web URL: http://, https://, ftp://, mailto: */
    data class Web(val url: String) : LinkTarget

    /** Path relative to the session working directory: src/Foo.kt, ../docs/api.md */
    data class RelativePath(val path: String) : LinkTarget

    /** Absolute path: /home/user/Foo.kt or C:\Users\Foo.kt */
    data class AbsolutePath(val path: String) : LinkTarget
}

object LinkClassifier {
    private val windowsAbsoluteRegex = Regex("[A-Za-z]:[\\\\/].*")

    fun classify(url: String): LinkTarget = when {
        url.startsWith("http://", ignoreCase = true) ||
        url.startsWith("https://", ignoreCase = true) ||
        url.startsWith("ftp://", ignoreCase = true) ||
        url.startsWith("mailto:", ignoreCase = true) -> LinkTarget.Web(url)

        url.startsWith("/") -> LinkTarget.AbsolutePath(url)

        url.startsWith("file://", ignoreCase = true) -> {
            // file:///home/user/foo → AbsolutePath("/home/user/foo")
            val afterScheme = url.substringAfter("file://")
            LinkTarget.AbsolutePath(afterScheme)
        }

        windowsAbsoluteRegex.matches(url) -> LinkTarget.AbsolutePath(url)

        else -> LinkTarget.RelativePath(url)
    }

    private val fileExtensionRegex = Regex("\\.\\w{1,10}$")

    /**
     * Heuristic: does this inline code content look like a file path or filename?
     * Returns true if it contains a path separator (/ or \) or has a file extension.
     */
    fun isLikelyFilePath(text: String): Boolean {
        if (text.contains('/') || text.contains('\\')) return true
        return fileExtensionRegex.containsMatchIn(text)
    }
}
