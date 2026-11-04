package com.example.restock

import com.example.restock.data.StoredImages
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoredImagesTest {

    @Test
    fun `reconhece imagens guardadas no Firebase Storage`() {
        val url = "https://firebasestorage.googleapis.com/v0/b/restock-b9983.firebasestorage.app/o/product_images%2Fabc?alt=media"
        assertTrue(StoredImages.isStoredInFirebase(url))
    }

    @Test
    fun `nunca considera nossas as imagens externas`() {
        assertFalse(StoredImages.isStoredInFirebase("https://images.openfoodfacts.org/images/products/123/front.jpg"))
    }

    @Test
    fun `ignora produtos sem imagem`() {
        assertFalse(StoredImages.isStoredInFirebase(null))
        assertFalse(StoredImages.isStoredInFirebase(""))
    }
}
