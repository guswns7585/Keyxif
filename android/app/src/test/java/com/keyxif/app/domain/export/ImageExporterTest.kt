package com.keyxif.app.domain.export

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageExporterTest {
    @Test
    fun `replaces the requested output extension`() {
        assertEquals("Keyxif_01.png", "Keyxif_01.webp".withExtension("png"))
        assertEquals("Keyxif_01.webp", "Keyxif_01".withExtension("webp"))
    }

    @Test
    fun `only replaces the final extension`() {
        assertEquals("my.keyboard.photo.png", "my.keyboard.photo.webp".withExtension("png"))
    }
}
