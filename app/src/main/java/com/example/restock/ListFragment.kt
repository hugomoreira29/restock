package com.example.restock

// HUGO MOREIRA - a22402246

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.restock.R
import com.example.restock.databinding.FragmentListBinding
import com.example.restock.model.Product
import com.example.restock.model.ShoppingListItem
import com.example.restock.ui.inventario.InventarioViewModel
import com.example.restock.ui.lista.ShoppingListAdapter
import com.example.restock.ui.lista.ShoppingListViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.util.UUID

class ListFragment : Fragment() {

    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!

    // Usa activityViewModels() para partilhar as instâncias dos ViewModels.
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

    private fun loadUserData() {
        val user = auth.currentUser
        user?.let {
            Glide.with(this)
                .load(it.photoUrl)
                .placeholder(R.drawable.ic_avatar)
                .circleCrop()
                .into(binding.profileImageView)
        }
    }

    private fun setupRecyclerView() {
        shoppingListAdapter = ShoppingListAdapter(
            onCheckChanged = { item, isChecked ->
                if (isChecked) {
                    moveItemToInventory(item)
                }
            },
            onDelete = { item ->
                shoppingListViewModel.deleteItem(item.id)
            },
            onItemClick = {
            }
        )

        binding.shoppingListRecyclerView.apply {
            adapter = shoppingListAdapter
            layoutManager = LinearLayoutManager(context)
        }
    }

    private fun moveItemToInventory(item: ShoppingListItem) {
        val newProduct = Product(
            id = UUID.randomUUID().toString(),
            nome = item.name,
            quantidade = 1.0,
            unidade = "un"
        )

        inventoryViewModel.addProduct(newProduct)
        shoppingListViewModel.deleteItem(item.id)

        Toast.makeText(context, getString(R.string.item_added_to_list, item.name), Toast.LENGTH_SHORT).show()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                shoppingListViewModel.items.collect {
                    shoppingListAdapter.submitList(it)
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.addShoppingItemButton.setOnClickListener {
            findNavController().navigate(ListFragmentDirections.actionListFragmentToAddShoppingItemFragment())
        }
        binding.profileImageView.setOnClickListener {
             findNavController().navigate(ListFragmentDirections.actionListFragmentToAccountFragment()) 
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
