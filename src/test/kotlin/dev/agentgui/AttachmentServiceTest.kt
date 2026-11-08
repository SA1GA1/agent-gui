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
    @Test fun `rejects binary and oversized text files without reading the whole project`() {
        val service = AttachmentService(dir.resolve("cache"))
        val binary = dir.resolve("binary.pdf");Files.write(binary,byteArrayOf(0,1,2))
        assertThrows(IllegalArgumentException::class.java) { service.snapshot(binary) }
        val large = dir.resolve("big.txt");Files.write(large,ByteArray(131073) { 65 })
        assertThrows(IllegalArgumentException::class.java) { service.snapshot(large) }
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
