package dev.agentgui.attachments

import com.intellij.openapi.application.PathManager
import dev.agentgui.codex.obj
import kotlinx.serialization.json.*
import java.nio.file.*
import java.util.Base64
import java.util.UUID
import java.io.ByteArrayOutputStream
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

class AttachmentService(private val root: Path = Path.of(PathManager.getSystemPath(), "agent-gui", "attachments")) {
    private val previews = object : LinkedHashMap<String, String>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 16
    }
    companion object {
        const val MAX_ATTACHMENTS = 10
        const val MAX_BYTES = 10 * 1024 * 1024
        const val MAX_PREVIEW_BYTES = 128 * 1024
        const val MAX_BASE64_CHARS = ((MAX_BYTES + 2) / 3) * 4
    }
    private val textExtensions = setOf(
        "txt", "md", "markdown", "csv", "tsv", "json", "jsonl", "xml", "yaml", "yml", "toml", "ini", "cfg", "conf", "log",
        "kt", "kts", "java", "js", "jsx", "ts", "tsx", "css", "scss", "html", "htm", "svg", "py", "rb", "rs", "go",
        "c", "h", "cpp", "hpp", "cs", "swift", "sh", "bash", "zsh", "sql", "gradle", "properties", "gitignore", "env"
    )
    fun snapshot(path: Path): JsonObject {
        require(Files.isRegularFile(path)) { "File was removed or is unavailable" }
        require(Files.size(path) <= MAX_BYTES) { "File exceeds 10 MB" }
        return store(path.fileName.toString(), readBounded(path), originalPath = path.toString())
    }
    fun text(name: String, text: String): JsonObject {
        val bytes = text.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BYTES) { "File exceeds 10 MB" }
        return store(name, bytes, mime = "text/plain")
    }
    fun binary(name: String, mime: String, data: String): JsonObject {
        require(data.length <= MAX_BASE64_CHARS) { "File exceeds 10 MB" }
        val bytes = Base64.getDecoder().decode(data)
        require(bytes.size <= MAX_BYTES) { "File exceeds 10 MB" }
        return store(name, bytes, mime)
    }
    fun paste(name: String, mime: String, data: String): JsonObject {
        require(mime in setOf("image/png", "image/jpeg", "image/webp", "image/gif")) { "Unsupported image format" }
        require(data.length <= MAX_BASE64_CHARS) { "Image exceeds 10 MB" }
        val bytes = Base64.getDecoder().decode(data)
        require(bytes.size <= MAX_BYTES) { "Image exceeds 10 MB" }
        require(isImage(bytes)) { "Invalid image data" }
        return store(name, bytes, mime)
    }
    private fun store(name: String, bytes: ByteArray, mime: String = "", originalPath: String? = null): JsonObject {
        Files.createDirectories(root)
        val safeName = Path.of(name).fileName?.toString()?.take(180)?.ifBlank { "File" } ?: "File"
        val copy = root.resolve(UUID.randomUUID().toString() + "-" + safeName)
        val kind = when {
            isImage(bytes) -> "image"
            !isBinaryContainer(bytes) && isTextCandidate(safeName, mime) && decodeText(bytes) != null -> "text"
            else -> "binary"
        }
        Files.write(copy, bytes, StandardOpenOption.CREATE_NEW)
        return obj {
            put("id", copy.fileName.toString()); put("name", safeName); put("image", kind == "image"); put("kind", kind)
            originalPath?.let { put("originalPath", it) }
        }
    }
    private fun readBounded(path: Path): ByteArray = Files.newInputStream(path).use {
        it.readNBytes(MAX_BYTES + 1).also { bytes -> require(bytes.size <= MAX_BYTES) { "File exceeds 10 MB" } }
    }
    private fun isTextCandidate(name: String, mime: String): Boolean {
        val type = mime.substringBefore(';').lowercase()
        return name.substringAfterLast('.', "").lowercase() in textExtensions ||
            name.lowercase() in setOf("dockerfile", "makefile", "license", "readme") ||
            type.startsWith("text/") || type in setOf("application/json", "application/javascript", "application/xml", "application/x-yaml", "image/svg+xml")
    }
    private fun decodeText(bytes: ByteArray): String? = runCatching {
        Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString()
            .takeIf { text -> text.none { it < ' ' && it !in "\n\r\t" } && !text.contains('\ufffd') }
    }.getOrNull()
    private fun isBinaryContainer(bytes: ByteArray): Boolean {
        // Printable headers do not make PDFs or ZIP-based Office documents text.
        return bytes.size >= 4 && (String(bytes, 0, 4, Charsets.US_ASCII) == "%PDF" ||
            (bytes[0] == 80.toByte() && bytes[1] == 75.toByte()))
    }
    private fun isImage(bytes: ByteArray): Boolean {
        fun prefix(vararg signature: Int) = bytes.size >= signature.size && signature.indices.all { (bytes[it].toInt() and 255) == signature[it] }
        return prefix(137, 80, 78, 71, 13, 10, 26, 10) || prefix(255, 216, 255) ||
            prefix(71, 73, 70, 56, 55, 97) || prefix(71, 73, 70, 56, 57, 97) ||
            (bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP")
    }
    fun input(attachment: JsonObject): JsonObject {
        val path = resolve(attachment["id"]!!.jsonPrimitive.content)
        require(Files.isRegularFile(path)) { "Attachment snapshot unavailable. Attach the file again." }
        val bytes = readBounded(path)
        if (isImage(bytes)) return obj { put("type", "localImage"); put("path", path.toString()) }
        val name = attachment["name"]?.jsonPrimitive?.content ?: path.fileName.toString()
        val kind = attachment["kind"]?.jsonPrimitive?.content
        val text = if (!isBinaryContainer(bytes) && (kind == "text" || (kind == null && isTextCandidate(name, "")))) decodeText(bytes) else null
        val reference = "Attached file: $name\nImmutable local snapshot: $path\nUse local file tools to read this snapshot."
        val content = if (text == null) "$reference\nBinary file; content is not included inline." else {
            val preview = if (bytes.size <= MAX_PREVIEW_BYTES) text else {
                // Streaming decode avoids cutting a UTF-8 character at the preview boundary.
                val output = java.nio.CharBuffer.allocate(MAX_PREVIEW_BYTES)
                Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes, 0, MAX_PREVIEW_BYTES), output, false)
                output.flip().toString()
            }
            "$reference\nText preview${if (bytes.size > MAX_PREVIEW_BYTES) " (truncated at 128 KB; full content is in the snapshot)" else ""}:\n```\n$preview\n```"
        }
        return obj { put("type", "text"); put("text", content) }
    }
    fun preview(attachment: JsonObject): String? {
        if (attachment["image"]?.jsonPrimitive?.booleanOrNull != true) return null
        val path = resolve(attachment["id"]!!.jsonPrimitive.content)
        if (!Files.exists(path)) return null
        return previews.getOrPut(path.toString()) {
            val image = ImageIO.createImageInputStream(path.toFile()).use { source ->
                val reader = ImageIO.getImageReaders(source).asSequence().firstOrNull() ?: return null
                try {
                    reader.input = source
                    val width = reader.getWidth(0); val height = reader.getHeight(0)
                    require(width.toLong() * height <= 100_000_000) { "Image resolution is too large" }
                    val sample = maxOf(1, maxOf(width, height) / 192)
                    val params = reader.defaultReadParam.apply { setSourceSubsampling(sample, sample, 0, 0) }
                    reader.read(0, params)
                } finally { reader.dispose() }
            }
            val scale = minOf(1.0, 192.0 / maxOf(image.width, image.height))
            val thumb = BufferedImage(maxOf(1, (image.width * scale).toInt()), maxOf(1, (image.height * scale).toInt()), BufferedImage.TYPE_INT_ARGB)
            val graphics = thumb.createGraphics()
            try { graphics.drawImage(image, 0, 0, thumb.width, thumb.height, null) } finally { graphics.dispose() }
            val output = ByteArrayOutputStream()
            ImageIO.write(thumb, "png", output)
            "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray())
        }
    }
    fun remove(id: String) { val path = resolve(id); previews.remove(path.toString()); Files.deleteIfExists(path) }

    private fun resolve(id: String): Path {
        require(id.isNotBlank() && Path.of(id).fileName.toString() == id && id != "." && id != "..")
        return root.resolve(id)
    }
}
