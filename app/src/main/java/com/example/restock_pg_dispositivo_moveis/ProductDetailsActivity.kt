package com.example.restock_pg_dispositivo_moveis

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class ProductDetailsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_product_details)

        val productId = intent.getStringExtra("productId")

        // Aqui depois vais buscar o produto ao Firestore
        // E preencher a UI
    }
}
