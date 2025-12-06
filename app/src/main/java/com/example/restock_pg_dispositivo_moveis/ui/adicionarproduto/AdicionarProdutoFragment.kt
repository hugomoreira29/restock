package com.example.restock_pg_dispositivo_moveis.ui.adicionarproduto

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.restock_pg_dispositivo_moveis.R

class AdicionarProdutoFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_adicionar_produto, container, false)
    }
}
