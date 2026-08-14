package com.example.data.model

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

data class Attachment(
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val base64Data: String? = null,
    val extractedText: String? = null
) {
    val extension: String get() = name.substringAfterLast('.', "").lowercase()
    val isImage: Boolean get() = mimeType.startsWith("image/")
    val isPdf: Boolean get() = mimeType == "application/pdf" || extension == "pdf"
    val isAudio: Boolean get() = mimeType.startsWith("audio/")
    val isVideo: Boolean get() = mimeType.startsWith("video/")
    val isZip: Boolean get() = mimeType == "application/zip" || mimeType == "application/x-zip-compressed" || extension == "zip"
    val isTextOrDoc: Boolean get() = mimeType.startsWith("text/") || extension in TEXT_EXTENSIONS
    val isGeminiMedia: Boolean get() = isImage || isPdf || isAudio || isVideo

    companion object {
        private val TEXT_EXTENSIONS = setOf(
            "txt", "md", "markdown", "json", "csv", "tsv", "xml", "html", "htm", "css",
            "js", "jsx", "ts", "tsx", "kt", "kts", "java", "gradle", "properties", "yaml", "yml",
            "toml", "ini", "cfg", "conf", "log", "sql", "sh", "bash", "c", "h", "cpp", "hpp",
            "cc", "cs", "go", "rs", "swift", "dart", "py", "rb", "php", "vue", "svelte"
        )
    }
}

object AttachmentHandler {
    private const val MAX_INLINE_BYTES = 15L * 1024L * 1024L
    private const val MAX_TEXT_BYTES = 2L * 1024L * 1024L
    private const val MAX_ZIP_EXTRACTED_BYTES = 5L * 1024L * 1024L
    private const val MAX_ZIP_ENTRY_BYTES = 512L * 1024L
    private const val MAX_ZIP_ENTRIES = 500

    fun processUri(context: Context, uri: Uri): Attachment? {
        val resolver = context.contentResolver
        var name = "attachment"
        var size = -1L
        try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex >= 0) name = cursor.getString(nameIndex) ?: name
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                }
            }
        } catch (_: Exception) { }

        val extension = name.substringAfterLast('.', "").lowercase()
        val mimeType = resolver.getType(uri)?.lowercase().orEmpty().ifBlank { mimeTypeFromExtension(extension) }
        val effectiveSize = size.coerceAtLeast(0L)

        return try {
            when {
                isZip(mimeType, extension) -> {
                    val text = resolver.openInputStream(uri)?.use { extractZip(it) }
                    Attachment(uri, name, mimeType, effectiveSize, extractedText = text)
                }
                isText(mimeType, extension) -> {
                    val text = resolver.openInputStream(uri)?.use { readTextLimited(it, MAX_TEXT_BYTES) }
                    Attachment(uri, name, mimeType, effectiveSize, extractedText = text)
                }
                extension == "docx" -> {
                    val text = resolver.openInputStream(uri)?.use { extractDocxText(it) }
                    Attachment(uri, name, mimeType, effectiveSize, extractedText = text)
                }
                isInlineType(mimeType, extension) && effectiveSize in 1..MAX_INLINE_BYTES -> {
                    val bytes = resolver.openInputStream(uri)?.use { readLimitedBytes(it, MAX_INLINE_BYTES) }
                    Attachment(
                        uri, name, mimeType,
                        if (size >= 0) size else bytes?.size?.toLong() ?: 0L,
                        base64Data = bytes?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
                    )
                }
                else -> Attachment(uri, name, mimeType, effectiveSize)
            }
        } catch (e: Exception) {
            Attachment(uri, name, mimeType, effectiveSize,
                extractedText = "Local file inspection failed for $name: ${e.message ?: "unknown error"}. Baby will try the original file.")
        }
    }

    private fun extractZip(input: InputStream): String {
        val out = StringBuilder()
        var entryCount = 0
        var textBytes = 0L
        ZipInputStream(input.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null && entryCount < MAX_ZIP_ENTRIES && textBytes < MAX_ZIP_EXTRACTED_BYTES) {
                entryCount++
                if (!entry.isDirectory) {
                    val bytes = readLimitedBytes(zip, MAX_ZIP_ENTRY_BYTES)
                    val entryName = entry.name
                    out.append("\n--- ZIP ENTRY: ").append(entryName).append(" (approximately ").append(bytes.size).append(" bytes) ---\n")
                    when {
                        isText("", entryName.substringAfterLast('.', "").lowercase()) -> {
                            val remaining = (MAX_ZIP_EXTRACTED_BYTES - textBytes).toInt().coerceAtLeast(0)
                            val safe = bytes.copyOf(minOf(bytes.size, remaining))
                            out.append(String(safe, Charsets.UTF_8)).append('\n')
                            textBytes += safe.size
                        }
                        entryName.endsWith(".docx", true) -> out.append("[DOCX binary entry; attach separately for full extraction]\n")
                        entryName.endsWith(".pdf", true) -> out.append("[PDF entry detected; attach separately for direct PDF inspection]\n")
                        isMediaName(entryName) -> out.append("[Media entry detected; attach this media separately if Baby should see/hear it]\n")
                        else -> out.append("[Binary entry indexed by filename and size]\n")
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        if (entryCount >= MAX_ZIP_ENTRIES) out.append("\n[ZIP entry limit reached.]\n")
        if (textBytes >= MAX_ZIP_EXTRACTED_BYTES) out.append("\n[ZIP text extraction limit reached.]\n")
        return out.toString().take(120_000)
    }

    private fun extractDocxText(input: InputStream): String {
        ZipInputStream(input.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name == "word/document.xml") {
                    val xml = String(readLimitedBytes(zip, MAX_TEXT_BYTES), Charsets.UTF_8)
                    return xml
                        .replace(Regex("<w:tab[^>]*/>"), "\t")
                        .replace(Regex("</w:p>"), "\n")
                        .replace(Regex("<[^>]+>"), "")
                        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                        .replace(Regex("\\n{3,}"), "\n\n")
                        .trim().take(120_000)
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return "DOCX file detected, but its document text could not be extracted locally."
    }

    private fun readTextLimited(input: InputStream, maxBytes: Long): String =
        String(readLimitedBytes(input, maxBytes), Charsets.UTF_8).replace("\u0000", "").take(120_000)

    private fun readLimitedBytes(input: InputStream, maxBytes: Long): ByteArray {
        val out = ByteArrayOutputStream(minOf(maxBytes, 64 * 1024L).toInt())
        val buffer = ByteArray(16 * 1024)
        var remaining = maxBytes
        while (remaining > 0) {
            val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (count <= 0) break
            out.write(buffer, 0, count)
            remaining -= count
        }
        return out.toByteArray()
    }

    private fun isZip(mime: String, ext: String) = mime == "application/zip" || mime == "application/x-zip-compressed" || ext == "zip"
    private fun isText(mime: String, ext: String) = mime.startsWith("text/") || ext in setOf("txt", "md", "markdown", "json", "csv", "tsv", "xml", "html", "htm", "css", "js", "jsx", "ts", "tsx", "kt", "kts", "java", "gradle", "properties", "yaml", "yml", "toml", "ini", "cfg", "conf", "log", "sql", "sh", "bash", "c", "h", "cpp", "hpp", "cc", "cs", "go", "rs", "swift", "dart", "py", "rb", "php", "vue", "svelte")
    private fun isInlineType(mime: String, ext: String) = mime.startsWith("image/") || mime.startsWith("video/") || mime.startsWith("audio/") || mime == "application/pdf" || ext == "pdf"
    private fun isMediaName(name: String) = name.substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif", "mp4", "mov", "webm", "mkv", "avi", "mp3", "wav", "m4a", "aac", "ogg")

    private fun mimeTypeFromExtension(ext: String) = when (ext) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "mp4" -> "video/mp4"
        "mov" -> "video/quicktime"
        "webm" -> "video/webm"
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "m4a" -> "audio/mp4"
        "pdf" -> "application/pdf"
        "zip" -> "application/zip"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "json" -> "application/json"
        else -> "application/octet-stream"
    }
}
