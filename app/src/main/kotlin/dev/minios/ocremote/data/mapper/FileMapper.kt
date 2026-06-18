package dev.minios.ocremote.data.mapper

import android.util.Log
import dev.minios.ocremote.data.dto.response.FileContentDto
import dev.minios.ocremote.data.dto.response.FileNodeDto
import dev.minios.ocremote.domain.model.ContentType
import dev.minios.ocremote.domain.model.FileContent
import dev.minios.ocremote.domain.model.FileNode
import dev.minios.ocremote.domain.model.FileType

object FileMapper {

    fun toDomain(dto: FileNodeDto): FileNode = FileNode(
        name = dto.name,
        path = dto.path,
        absolute = dto.absolute ?: "",
        type = when (dto.type) {
            "directory" -> FileType.DIRECTORY
            "file" -> FileType.FILE
            else -> {
                Log.w("FileMapper", "Unknown type='${dto.type}'")
                FileType.FILE
            }
        },
        ignored = dto.ignored,
        size = dto.size,
        modified = dto.modified
    )

    fun toDomain(dto: FileContentDto, path: String): FileContent = FileContent(
        path = path,
        type = when (dto.type) {
            "binary" -> ContentType.BINARY
            "text" -> ContentType.TEXT
            else -> {
                Log.w("FileMapper", "Unknown type='${dto.type}'")
                ContentType.TEXT
            }
        },
        content = dto.content,
        mimeType = dto.mimeType
    )
}
