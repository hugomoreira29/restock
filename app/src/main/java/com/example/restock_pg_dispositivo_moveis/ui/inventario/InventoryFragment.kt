package com.example.restock_pg_dispositivo_moveis.ui.inventario

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.restock_pg_dispositivo_moveis.databinding.FragmentInventarioBinding
import com.example.restock_pg_dispositivo_moveis.model.Product
import com.example.restock_pg_dispositivo_moveis.ui.ProductAdapter
import kotlinx.coroutines.launch

class InventoryFragment : Fragment() {

    // Usamos activityViewModels para partilhar a mesma instância com AdicionarProdutoFragment
    private val viewModel: InventarioViewModel by activityViewModels()

    private var _binding: FragmentInventarioBinding? = null
    // O binding facilita a referência aos componentes do layout fragment_inventario.xml
    private val binding get() = _binding!!

    private lateinit var produtoAdapter: ProductAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInventarioBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        observeInventarioData()
        setupFab()
    }

    /**
     * Configura o RecyclerView e inicializa o Adapter com os callbacks CRUD.
     */
    private fun setupRecyclerView() {
        // Inicializar com uma lista vazia. Será preenchida pelo observer.
        produtoAdapter = ProductAdapter(
            productList = emptyList(),

            // Callback para Edição/Detalhe (CRUD: Update)
            onEdit = { produto -> navigateToEdit(produto.id) },

            // Callback para Apagar (CRUD: Delete)
            onDelete = { produto -> confirmDelete(produto) }
        )
        // O layout manager (LinearLayoutManager) está definido no XML
        binding.inventarioRecyclerView.adapter = produtoAdapter
    }

    /**
     * Observa o StateFlow de produtos do ViewModel e atualiza o Adapter.
     * (CRUD: Read em tempo real)
     */
    private fun observeInventarioData() {
        viewLifecycleOwner.lifecycleScope.launch {
            // Coleta os dados do StateFlow do ViewModel
            viewModel.produtos.collect { produtosList ->
                // Atualiza o Adapter com a nova lista recebida do Firestore
                produtoAdapter.updateList(produtosList)

                // Mostra/Esconde o Empty State
                binding.emptyStateText.visibility =
                    if (produtosList.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    /**
     * Configura a ação do FAB (Floating Action Button).
     */
    private fun setupFab() {
        // FAB para adicionar novo produto (CRUD: Create)
        binding.fabAddProduct.setOnClickListener {
            // Navega para o ecrã de adicionar/editar. Passamos um ID nulo para 'Criar'.
            navigateToEdit(null)
        }
    }

    /**
     * Lógica para confirmar e executar a exclusão de um produto.
     */
    private fun confirmDelete(produto: Product) {
        // Pode adicionar um Diálogo de Confirmação aqui (AlertDialog)

        // Por agora, executamos diretamente a ação de apagar
        viewModel.apagarProduto(produto.id) // Chamada ao CRUD Delete no ViewModel
        Toast.makeText(context, "${produto.nome} apagado.", Toast.LENGTH_SHORT).show()
    }

    /**
     * Navega para o AdicionarProdutoFragment, passando o ID do produto para edição (UPDATE).
     */
    private fun navigateToEdit(produtoId: String?) {
        val action = InventoryFragmentDirections.actionInventoryFragmentToAdicionarProdutoFragment(produtoId)
        findNavController().navigate(action)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}