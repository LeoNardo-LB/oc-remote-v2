package dev.leonardo.ocremotev2.domain.model

/**
 * Builds structured prompt text for submitting annotations.
 *
 * Format:
 *   对于 <filePath> 文件，用户提出了如下备注：
 *
 *   总体备注：
 *   <overallNote>
 *
 *   具体备注：注格式为[<行号开始:行内字号>,<行号结束:行内字号>] <具体备注内容>，下面是用户的具体备注：
 *   1. [x1:y1,x2:y2] <note>
 *   2. ...
 *
 *   请按照用户备注与指示来做出回答、修改内容或执行任务！
 */
object AnnotationPromptBuilder {

    fun build(
        annotations: List<Annotation>,
        overallNote: String,
        filePath: String,
        directory: String
    ): String {
        require(annotations.isNotEmpty()) { "Cannot submit empty annotation list" }

        val resolvedPath = resolvePath(filePath, directory)
        val noteText = overallNote.ifBlank { "无" }

        val sb = StringBuilder()
        sb.append("对于 $resolvedPath 文件，用户提出了如下备注：\n\n")
        sb.append("总体备注：\n")
        sb.append(noteText).append("\n\n")
        sb.append("具体备注：注格式为[<行号开始:行内字号>,<行号结束:行内字号>] <具体备注内容>，下面是用户的具体备注：\n")
        annotations.sortedBy { it.index }.forEach { ann ->
            sb.append("${ann.index + 1}. ")
              .append("[${ann.startLine}:${ann.startCol},${ann.endLine}:${ann.endCol}] ")
              .append(ann.note).append("\n")
        }
        sb.append("\n请按照用户备注与指示来做出回答、修改内容或执行任务！")
        return sb.toString().trimEnd()
    }

    /**
     * Resolve [filePath] against [directory].
     * - Absolute Unix path (leading '/') and Windows drive letter (e.g. "D:/") used as-is.
     * - Relative path joined with '/' separator (forward-slash, cross-platform safe).
     */
    private fun resolvePath(filePath: String, directory: String): String {
        if (filePath.isBlank()) return directory
        if (filePath.startsWith("/")) return filePath
        if (filePath.length >= 2 && filePath[1] == ':') return filePath
        if (directory.isBlank()) return filePath
        val sep = if (directory.endsWith("/")) "" else "/"
        return "$directory$sep$filePath"
    }
}
