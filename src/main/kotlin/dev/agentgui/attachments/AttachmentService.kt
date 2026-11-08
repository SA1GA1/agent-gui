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
    private val images = setOf("png", "jpg", "jpeg", "webp", "gif")
    fun snapshot(path: Path): JsonObject {
        require(Files.isRegularFile(path)) { "File was removed or is unavailable" }
        val image = path.fileName.toString().substringAfterLast('.', "").lowercase() in images
        require(Files.size(path) <= if (image) 10L * 1024 * 1024 else 128L * 1024) { "Maximum size: 10 MB per image, 128 KB per text file" }
        Files.createDirectories(root)
        val copy = root.resolve(UUID.randomUUID().toString() + "-" + path.fileName)
        Files.copy(path, copy)
        if (!image) {
            try { require(!Files.readString(copy).contains('\u0000')) { "Text files and images are supported" } }
            catch (e: Exception) { Files.deleteIfExists(copy); throw IllegalArgumentException("Text files and images are supported", e) }
        }
        return obj { put("id", copy.fileName.toString()); put("name", path.fileName.toString()); put("originalPath", path.toString()); put("image", image) }
    }
    fun text(name: String, text: String): JsonObject {
        require(text.toByteArray(Charsets.UTF_8).size <= 128 * 1024 && !text.contains('\u0000') && !text.contains('\ufffd')) { "UTF-8 text up to 128 KB is supported" }
        Files.createDirectories(root)
        val safeName = Path.of(name).fileName.toString().take(180)
        val copy = root.resolve(UUID.randomUUID().toString() + "-" + safeName)
        Files.writeString(copy, text)
        return obj { put("id", copy.fileName.toString()); put("name", safeName); put("image", false) }
    }
    fun paste(name: String, mime: String, data: String): JsonObject {
        require(mime in setOf("image/png", "image/jpeg", "image/webp", "image/gif")) { "Unsupported image format" }
        require(data.length <= 14 * 1024 * 1024) { "Image is too large" }
        val bytes = Base64.getDecoder().decode(data)
        require(bytes.size <= 10 * 1024 * 1024) { "Image exceeds 10 MB" }
        Files.createDirectories(root)
        val temp = root.resolve(UUID.randomUUID().toString() + "." + mime.substringAfter('/'))
        Files.write(temp, bytes)
        return obj { put("id", temp.fileName.toString()); put("name", name.take(180)); put("image", true) }
    }
    fun input(attachment: JsonObject): JsonObject {
        val path = resolve(attachment["id"]!!.jsonPrimitive.content)
        require(Files.exists(path)) { "Attachment snapshot unavailable. Attach the file again." }
        return if (attachment["image"]?.jsonPrimitive?.booleanOrNull == true)
            obj { put("type", "localImage"); put("path", path.toString()) }
        else obj { put("type", "text"); put("text", "File: ${attachment["originalPath"]?.jsonPrimitive?.content ?: attachment["name"]}\n```\n${Files.readString(path)}\n```") }
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
