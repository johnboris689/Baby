package com.example.data.model

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import java.io.InputStream

data class Attachment(
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val base64Data: String? = null,
    val extractedText: String? = null
) {
    val isImage: Boolean
        get() = mimeType.startsWith("image/")

    val isPdf: Boolean
        get() = mimeType == "application/pdf" || name.endsWith(".pdf", ignoreCase = true)

    val isAudio: Boolean
        get() = mimeType.startsWith("audio/")

    val isVideo: Boolean
        get() = mimeType.startsWith("video/")

    val isTextOrDoc: Boolean
        get() = mimeType.startsWith("text/") || 
                name.endsWith(".txt", ignoreCase = true) ||
                name.endsWith(".json", ignoreCase = true) ||
                name.endsWith(".xml", ignoreCase = true) ||
                name.endsWith(".csv", ignoreCase = true) ||
                name.endsWith(".md", ignoreCase = true) ||
                name.endsWith(".doc", ignoreCase = true) ||
                name.endsWith(".docx", ignoreCase = true)

    val isZip: Boolean
        get() = mimeType == "application/zip" || name.endsWith(".zip", ignoreCase = true)
}

object AttachmentHandler {

    fun processUri(context: Context, uri: Uri): Attachment? {
        val contentResolver = context.contentResolver
        val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"

        var name = "attachment"
        var size = 0L

        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex != -1) name = cursor.getString(nameIndex) ?: "attachment"
                    if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        var base64Data: String? = null
        var extractedText: String? = null

        try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val bytes = inputStream?.readBytes()
            inputStream?.close()

            if (bytes != null) {
                if (mimeType.startsWith("image/")) {
                    base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP)
                } else if (mimeType.startsWith("text/") || name.endsWith(".txt") || name.endsWith(".json") || name.endsWith(".csv") || name.endsWith(".md") || name.endsWith(".xml")) {
                    extractedText = String(bytes, Charsets.UTF_8)
                } else if (name.endsWith(".docx") || name.endsWith(".doc") || name.endsWith(".pdf")) {
                    // Simple text extraction from readable document streams
                    val textContent = String(bytes.filter { it in 32..126 || it == 10.toByte() || it == 13.toByte() }.toByteArray(), Charsets.UTF_8)
                    extractedText = if (textContent.length > 50) textContent.take(4000) else "Document $name (${bytes.size} bytes attached)"
                } else {
                    extractedText = "File attached: $name ($mimeType, ${bytes.size} bytes)"
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return Attachment(
            uri = uri,
            name = name,
            mimeType = mimeType,
            sizeBytes = size,
            base64Data = base64Data,
            extractedText = extractedText
        )
    }
}
