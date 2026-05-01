package com.example.restock

// Android - para gerir o ciclo de vida e apresentar mensagens ao utilizador
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.google.android.material.snackbar.Snackbar

// AndroidX - para o fragmento, ViewModels partilhados, coroutines e navegação
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager

// Glide - para carregar a fotografia de perfil do utilizador
import com.bumptech.glide.Glide

// Classes internas da aplicação - recursos, binding, modelos e adaptador
import com.example.restock.databinding.FragmentListBinding
import com.example.restock.model.Product
import com.example.restock.model.ShoppingListItem
import com.example.restock.ui.inventario.InventarioViewModel
import com.example.restock.ui.lista.ShoppingListAdapter
import com.example.restock.ui.lista.ShoppingListViewModel

// Firebase - autenticação para obter os dados do utilizador atual
import com.google.firebase.auth.FirebaseAuth

// Coroutines - para observar o fluxo de dados da lista de compras
import kotlinx.coroutines.launch

// Java - para gerar identificadores únicos para os produtos criados
import java.util.UUID

/** HUGO MOREIRA - a22402246
 * Fragmento responsável pela gestão da lista de compras familiar.
 * Apresenta os itens da lista e permite adicioná-los, eliminá-los
 * e marcá-los como comprados, movendo-os automaticamente para o inventário.
 */
class ListFragment : Fragment() {

    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!

    // ViewModels partilhados com a Activity para aceder aos dados em tempo real
    private val shoppingListViewModel: ShoppingListViewModel by activityViewModels()
    private val inventoryViewModel: InventarioViewModel by activityViewModels()
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private lateinit var shoppingListAdapter: ShoppingListAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        observeViewModel()
        setupClickListeners()
        loadUserData()
    }

    /**
     * Carrega a fotografia de perfil do utilizador autenticado.
     */
    private fun loadUserData() {
        auth.currentUser?.let {
            Glide.with(this)
                .load(it.photoUrl)
                .placeholder(R.drawable.ic_avatar)
                .circleCrop()
                .into(binding.profileImageView)
        }
    }

    /**
     * Inicializa o adaptador da lista de compras com as ações disponíveis.
     * Ao marcar um item como comprado, este é movido automaticamente para o inventário.
     */
    private fun setupRecyclerView() {
        shoppingListAdapter = ShoppingListAdapter(
            onCheckChanged = { item, isChecked ->
                // Move o item para o inventário ao ser assinalado como comprado
                if (isChecked) moveItemToInventory(item)
            },
            onDelete = { item ->
                shoppingListViewModel.deleteItem(item.id)
                Snackbar.make(requireView(), getString(R.string.item_removed, item.name), Snackbar.LENGTH_LONG)
                    .setAction(getString(R.string.undo)) { shoppingListViewModel.addItem(item) }
                    .show()
            },
            onItemClick = {}
        )

        binding.shoppingListRecyclerView.apply {
            adapter = shoppingListAdapter
            layoutManager = LinearLayoutManager(context)
        }
    }

    /**
     * Move um item da lista de compras para o inventário da família.
     * Cria um novo produto com os dados do item e elimina-o da lista de compras.
     */
    private fun moveItemToInventory(item: ShoppingListItem) {
        // Cria um produto com quantidade e unidade padrão a partir do item da lista
        val newProduct = Product(
            id = UUID.randomUUID().toString(),
            nome = item.name,
            quantidade = 1.0,
            unidade = "un"
        )

        inventoryViewModel.addProduct(newProduct)
        shoppingListViewModel.deleteItem(item.id)

        Toast.makeText(context, getString(R.string.item_added_to_inventory, item.name), Toast.LENGTH_SHORT).show()
    }

    /**
     * Observa o fluxo de itens da lista de compras e atualiza o adaptador.
     * Usa repeatOnLifecycle para pausar a recolha quando o fragmento está em background.
     */
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                shoppingListViewModel.items.collect { items ->
                    shoppingListAdapter.submitList(items)
                    binding.emptyListTextView.visibility =
                        if (items.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            shoppingListViewModel.error.collect { message ->
                if (message != null) {
                    Snackbar.make(requireView(), getString(R.string.error_operation_failed), Snackbar.LENGTH_SHORT).show()
                    shoppingListViewModel.clearError()
                }
            }
        }
    }

    /**
     * Configura os listeners de clique para adicionar itens à lista
     * e navegar para o ecrã de conta ao clicar na fotografia de perfil.
     */
    private fun setupClickListeners() {
        binding.addShoppingItemButton.setOnClickListener {
            findNavController().navigate(
                ListFragmentDirections.actionListFragmentToAddShoppingItemFragment()
            )
        }
        binding.profileImageView.setOnClickListener {
            findNavController().navigate(
                ListFragmentDirections.actionListFragmentToAccountFragment()
            )
        }
    }

    /**
     * Limpa o binding quando a vista é destruída para evitar fugas de memória.
     */
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}