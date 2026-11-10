package dev.agentgui
import dev.agentgui.attachments.AttachmentService
import dev.agentgui.codex.string
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import javax.imageio.ImageIO

class AttachmentServiceTest {
    @TempDir lateinit var dir: Path
    @Test fun `copies Unicode paths and preserves the selected version after file changes`() {
        val service = AttachmentService(dir.resolve("cache"))
        val source = dir.resolve("мой файл.kt"); Files.writeString(source,"val привет = 1")
        val file = service.snapshot(source); Files.writeString(source,"changed"); Files.delete(source)
        assertTrue(service.input(file).string("text")!!.contains("val привет = 1"))
        assertEquals("мой файл.kt",file.string("name"))
    }
    @Test fun `binary files retain an immutable snapshot and are never inlined as text`() {
        val service = AttachmentService(dir.resolve("cache"))
        val binary = dir.resolve("document.pdf")
        val bytes = "%PDF-1.7 binary data".toByteArray()
        Files.write(binary, bytes)
        val file = service.snapshot(binary)
        Files.delete(binary)
        val input = service.input(file)
        assertEquals("text", input.string("type"))
        val reference = input.string("text")!!
        assertTrue(reference.contains("snapshot"))
        assertTrue(reference.contains(dir.resolve("cache").toString()))
        assertFalse(reference.contains("%PDF-1.7"))
        assertArrayEquals(bytes, Files.readAllBytes(dir.resolve("cache").resolve(file.string("id")!!)))
    }
    @Test fun `large text gets a bounded preview with a full snapshot reference`() {
        val service = AttachmentService(dir.resolve("cache"))
        val source = dir.resolve("large.txt")
        Files.writeString(source, "A".repeat(200_000) + "END_MARKER")
        val file = service.snapshot(source)
        val text = service.input(file).string("text")!!
        assertTrue(text.contains("truncated"))
        assertTrue(text.contains(dir.resolve("cache").toString()))
        assertFalse(text.contains("END_MARKER"))
        assertTrue(text.length < 135_000)
    }
    @Test fun `unknown binary and invalid UTF8 text do not decode to inline content`() {
        val service = AttachmentService(dir.resolve("cache"))
        for (name in listOf("data.bin", "bad.txt")) {
            val source = dir.resolve(name)
            Files.write(source, byteArrayOf(0, 1, -1, 42))
            val file = service.snapshot(source)
            assertFalse(service.input(file).string("text")!!.contains("```"))
        }
    }
    @Test fun `rejects every attachment above ten MB`() {
        val service = AttachmentService(dir.resolve("cache"))
        val large = dir.resolve("big.bin")
        Files.write(large, ByteArray(10 * 1024 * 1024 + 1))
        assertThrows(IllegalArgumentException::class.java) { service.snapshot(large) }
    }
    @Test fun `binary uploads preserve bytes and sniff image content instead of the mime hint`() {
        val service = AttachmentService(dir.resolve("cache"))
        val bytes = byteArrayOf(0, 1, 2, -1)
        val file = service.binary("file.txt", "text/plain", Base64.getEncoder().encodeToString(bytes))
        assertFalse(service.input(file).string("text")!!.contains("```"))
        assertArrayEquals(bytes, Files.readAllBytes(dir.resolve("cache").resolve(file.string("id")!!)))
        val image = dir.resolve("misnamed.bin")
        ImageIO.write(BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB), "png", image.toFile())
        assertEquals("localImage", service.input(service.snapshot(image)).string("type"))
    }
    @Test fun `printable PDF header and fake image extension stay binary`() {
        val service = AttachmentService(dir.resolve("cache"))
        for (name in listOf("misnamed.txt", "document.png")) {
            val source = dir.resolve(name)
            Files.writeString(source, "%PDF-1.7 printable but not a text document")
            val file = service.snapshot(source)
            val input = service.input(file)
            assertEquals("text", input.string("type"))
            assertFalse(input.string("text")!!.contains("%PDF-1.7"))
            assertNull(service.preview(file))
        }
    }
    @Test fun `UTF8 preview does not split a multibyte character`() {
        val service = AttachmentService(dir.resolve("cache"))
        val source = dir.resolve("unicode.txt")
        Files.writeString(source, "A".repeat(AttachmentService.MAX_PREVIEW_BYTES - 1) + "界".repeat(20))
        val input = service.input(service.snapshot(source)).string("text")!!
        assertTrue(input.contains("truncated"))
        assertFalse(input.contains('\ufffd'))
    }
    @Test fun `uploads enforce decoded size and reject malformed base64`() {
        val service = AttachmentService(dir.resolve("cache"))
        assertThrows(IllegalArgumentException::class.java) { service.binary("file", "", "not base64!") }
        val bytes = ByteArray(AttachmentService.MAX_BYTES + 1)
        assertThrows(IllegalArgumentException::class.java) { service.binary("file", "", Base64.getEncoder().encodeToString(bytes)) }
    }
    @Test fun `thumbnail stays small while input retains the full original image`() {
        val service = AttachmentService(dir.resolve("cache"))
        val original = dir.resolve("image.png")
        ImageIO.write(BufferedImage(1600,900,BufferedImage.TYPE_INT_RGB),"png",original.toFile())
        val file=service.snapshot(original)
        val thumb=service.preview(file)!!
        val image=ImageIO.read(ByteArrayInputStream(Base64.getDecoder().decode(thumb.substringAfter(','))))
        assertTrue(image.width<=192 && image.height<=192)
        val stored=Path.of(service.input(file).string("path")!!)
        assertEquals(1600,ImageIO.read(stored.toFile()).width)
    }
    @Test fun `rejects traversal identifiers and fails clearly after an attachment is deleted`() {
        val service=AttachmentService(dir.resolve("cache"))
        assertThrows(IllegalArgumentException::class.java) {service.remove("../outside")}
        val file=service.text("sample.kt","val x=1");service.remove(file.string("id")!!)
        assertThrows(IllegalArgumentException::class.java) {service.input(file)}
    }
}
