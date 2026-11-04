package com.example.restock

import com.example.restock.data.ImageCompressor
import org.junit.Assert.assertEquals
import org.junit.Test

class ImageCompressorTest {

    @Test
    fun `reduz o lado maior e mantem a proporcao`() {
        assertEquals(1600 to 1200, ImageCompressor.targetSize(4000, 3000))
        assertEquals(1200 to 1600, ImageCompressor.targetSize(3000, 4000))
    }

    @Test
    fun `nunca aumenta imagens pequenas`() {
        assertEquals(800 to 600, ImageCompressor.targetSize(800, 600))
        assertEquals(1600 to 1600, ImageCompressor.targetSize(1600, 1600))
    }
}
