package com.example.restock_pg_dispositivo_moveis

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.restock_pg_dispositivo_moveis.model.Product
import com.example.restock_pg_dispositivo_moveis.repository.FirestoreRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {

    private lateinit var recyclerProducts: RecyclerView
    private lateinit var adapter: ProductAdapter
    private val repository = FirestoreRepository()

    override fun onCreate(savedInstanceState: Bundle?) {

        ThemeUtils.applySavedTheme(this)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // --- BOTÃO DE LOGOUT ---
        val logoutBtn = findViewById<Button>(R.id.buttonLogout)
        logoutBtn.setOnClickListener {
            FirebaseAuth.getInstance().signOut()

            // Volta para o Login
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        recyclerProducts = findViewById(R.id.recyclerProducts)
        recyclerProducts.layoutManager = LinearLayoutManager(this)
        adapter = ProductAdapter(emptyList()) { product ->
            openProductDetails(product)
        }

        recyclerProducts.adapter = adapter

        loadProducts()
    }

    private fun openProductDetails(product: Product) {
        val intent = Intent(this, ProductDetailsActivity::class.java)
        intent.putExtra("productId", product.id)
        startActivity(intent)
    }

    private fun loadProducts() {

        val user = FirebaseAuth.getInstance().currentUser
        val familiaId = user?.uid ?: ""   // TEMPORÁRIO — até ter sistema de famílias

        CoroutineScope(Dispatchers.Main).launch {
            val productList: List<Product> = repository.getProductsByFamily(familiaId)
            adapter.updateList(productList)
        }
    }
}
