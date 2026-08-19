package com.example.data.model

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/** A user-selected attachment plus any locally indexed content discovered inside it. */
data class Attachment(
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val base64Data: String? = null,
    val extractedText: String? = null,
    val extractedMedia: List<ExtractedMedia> = emptyList()
) {
    val extension: String get() = name.substringAfterLast('.', "").lowercase()
    val isImage: Boolean get() = mimeType.startsWith("image/")
    val isPdf: Boolean get() = mimeType == "application/pdf" || extension == "pdf"
    val isAudio: Boolean get() = mimeType.startsWith("audio/")
    val isVideo: Boolean get() = mimeType.startsWith("video/")
    val isZip: Boolean get() = mimeType == "application/zip" || mimeType == "application/x-zip-compressed" || extension == "zip"
    val isTextOrDoc: Boolean get() = mimeType.startsWith("text/") || extension in TEXT_EXTENSIONS || extension in DOCUMENT_EXTENSIONS
    val isGeminiMedia: Boolean get() = isImage || isPdf || isAudio || isVideo

    companion object {
        private val TEXT_EXTENSIONS = setOf(
            "txt", "md", "markdown", "json", "csv", "tsv", "xml", "html", "htm", "css",
            "js", "jsx", "ts", "tsx", "kt", "kts", "java", "gradle", "properties", "yaml", "yml",
            "toml", "ini", "cfg", "conf", "log", "sql", "sh", "bash", "c", "h", "cpp", "hpp",
            "cc", "cs", "go", "rs", "swift", "dart", "py", "rb", "php", "vue", "svelte"
        )
        private val DOCUMENT_EXTENSIONS = setOf("docx", "pdf", "rtf", "odt", "xlsx", "xls", "pptx", "ppt")
    }
}

data class ExtractedMedia(
    val file: File,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long
)

object AttachmentHandler {
    private const val MAX_INLINE_BYTES = 15L * 1024L * 1024L
    private const val MAX_TEXT_BYTES = 4L * 1024L * 1024L
    private const val MAX_ZIP_EXTRACTED_BYTES = 12L * 1024L * 1024L
    private const val MAX_ZIP_ENTRY_BYTES = 4L * 1024L * 1024L
    private const val MAX_ZIP_ENTRIES = 750
    private const val MAX_ZIP_MEDIA_FILES = 20
    private const val MAX_ZIP_MEDIA_BYTES = 30L * 1024L * 1024L
    private const val MAX_ZIP_SCANNED_BYTES = 64L * 1024L * 1024L

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
                    val result = resolver.openInputStream(uri)?.use { extractZip(context, it) }
                    Attachment(uri, name, mimeType, effectiveSize,
                        extractedText = result?.text,
                        extractedMedia = result?.media.orEmpty())
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
                else -> Attachment(uri, name, mimeType, effectiveSize,
                    extractedText = "[Attachment indexed: $name, type=$mimeType, size=${effectiveSize.coerceAtLeast(0)} bytes]")
            }
        } catch (e: Exception) {
            Attachment(uri, name, mimeType, effectiveSize,
                extractedText = "Local inspection failed for $name: ${e.message ?: "unknown error"}. Baby will still try the original attachment.")
        }
    }

    fun processBitmap(context: Context, bitmap: Bitmap, fileName: String = "photo_${System.currentTimeMillis()}.jpg"): Attachment? {
        return try {
            val cacheFile = File(context.cacheDir, fileName)
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, stream)
            val bytes = stream.toByteArray()
            FileOutputStream(cacheFile).use { it.write(bytes) }
            val uri = Uri.fromFile(cacheFile)
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            Attachment(
                uri = uri,
                name = fileName,
                mimeType = "image/jpeg",
                sizeBytes = bytes.size.toLong(),
                base64Data = base64
            )
        } catch (e: Exception) {
            null
        }
    }

    private data class ZipInspection(val text: String, val media: List<ExtractedMedia>)

    private fun extractZip(context: Context, input: InputStream): ZipInspection {
        val out = StringBuilder()
        val media = mutableListOf<ExtractedMedia>()
        var entryCount = 0
        var textBytes = 0L
        var mediaBytes = 0L
        var scannedBytes = 0L
        val mediaDir = File(context.cacheDir, "baby_zip_media").apply { mkdirs() }

        ZipInputStream(input.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null && entryCount < MAX_ZIP_ENTRIES && scannedBytes < MAX_ZIP_SCANNED_BYTES && (textBytes < MAX_ZIP_EXTRACTED_BYTES || media.size < MAX_ZIP_MEDIA_FILES)) {
                entryCount++
                if (!entry.isDirectory) {
                    val entryName = entry.name.replace('\\', '/')
                    val ext = entryName.substringAfterLast('.', "").lowercase()
                    val entryBudget = minOf(MAX_ZIP_ENTRY_BYTES, MAX_ZIP_SCANNED_BYTES - scannedBytes)
                    val bytes = readLimitedBytes(zip, entryBudget)
                    scannedBytes += bytes.size
                    out.append("\n--- ZIP ENTRY: ").append(entryName).append(" (up to ").append(bytes.size).append(" bytes) ---\n")

                    when {
                        isText("", ext) -> {
                            val remaining = (MAX_ZIP_EXTRACTED_BYTES - textBytes).toInt().coerceAtLeast(0)
                            val safe = bytes.copyOf(minOf(bytes.size, remaining))
                            out.append(String(safe, Charsets.UTF_8)).append('\n')
                            textBytes += safe.size
                        }
                        ext == "docx" -> {
                            val temp = File.createTempFile("baby_docx_", ".docx", mediaDir)
                            temp.writeBytes(bytes)
                            val text = temp.inputStream().use { extractDocxText(it) }
                            out.append(text).append('\n')
                            temp.delete()
                        }
                        isUploadableDocument(ext) || isMediaName(entryName) -> {
                            if (media.size < MAX_ZIP_MEDIA_FILES && mediaBytes + bytes.size <= MAX_ZIP_MEDIA_BYTES) {
                                val safeName = entryName.substringAfterLast('/').ifBlank { "entry_$entryCount" }
                                val temp = File(mediaDir, "${System.nanoTime()}_$safeName").also { it.parentFile?.mkdirs() }
                                temp.writeBytes(bytes)
                                val mime = mimeTypeFromExtension(ext)
                                media += ExtractedMedia(temp, entryName, mime, bytes.size.toLong())
                                mediaBytes += bytes.size
                                out.append("[Binary/document indexed for direct analysis by Baby.]").append('\n')
                            } else {
                                out.append("[Binary entry detected but media extraction limit was reached.]").append('\n')
                            }
                        }
                        else -> out.append("[Binary entry indexed by filename and size.]").append('\n')
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        if (entryCount >= MAX_ZIP_ENTRIES) out.append("\n[ZIP entry limit reached.]\n")
        if (textBytes >= MAX_ZIP_EXTRACTED_BYTES) out.append("\n[ZIP text extraction limit reached.]\n")
        if (scannedBytes >= MAX_ZIP_SCANNED_BYTES) out.append("\n[ZIP scan limit reached for safety.]\n")
        return ZipInspection(out.toString().take(180_000), media)
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
                        .trim().take(180_000)
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return "DOCX detected, but document text could not be extracted locally."
    }

    private fun readTextLimited(input: InputStream, maxBytes: Long): String =
        String(readLimitedBytes(input, maxBytes), Charsets.UTF_8).replace("\u0000", "").take(180_000)

    private fun readLimitedBytes(input: InputStream, maxBytes: Long): ByteArray {
        val out = ByteArrayOutputStream(minOf(maxBytes, 64 * 1024L).toInt())
        val buffer = ByteArray(32 * 1024)
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
    private fun isUploadableDocument(ext: String) = ext in setOf("pdf", "doc", "docx", "rtf", "odt", "xlsx", "xls", "pptx", "ppt")

    private fun mimeTypeFromExtension(ext: String) = when (ext) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "mp4" -> "video/mp4"
        "mov" -> "video/quicktime"
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        "avi" -> "video/x-msvideo"
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "m4a" -> "audio/mp4"
        "aac" -> "audio/aac"
        "ogg" -> "audio/ogg"
        "pdf" -> "application/pdf"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "json" -> "application/json"
        "xml" -> "application/xml"
        else -> "application/octet-stream"
    }
}
