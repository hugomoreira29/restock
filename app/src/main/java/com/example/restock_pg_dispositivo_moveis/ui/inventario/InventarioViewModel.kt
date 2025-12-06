package com.example.restock_pg_dispositivo_moveis.ui.inventario

import androidx.lifecycle.ViewModel
import com.example.restock_pg_dispositivo_moveis.model.Product
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class InventarioViewModel : ViewModel() {
    private val _produtos = MutableStateFlow<List<Product>>(emptyList())
    val produtos: StateFlow<List<Product>> = _produtos

    fun apagarProduto(produtoId: String) {
        // Lógica para apagar o produto
    }
}
