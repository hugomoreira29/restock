package com.example.restock_pg_dispositivo_moveis.ui.lista

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.restock_pg_dispositivo_moveis.R
import com.example.restock_pg_dispositivo_moveis.databinding.FragmentAddShoppingItemBinding
import com.example.restock_pg_dispositivo_moveis.model.ShoppingListItem
import java.util.UUID

class AddShoppingItemFragment : Fragment() {

    private var _binding: FragmentAddShoppingItemBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ShoppingListViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddShoppingItemBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.toolbar.setNavigationOnClickListener { 
            findNavController().navigateUp()
        }

        binding.addItemButton.setOnClickListener {
            val name = binding.itemNameEditText.text.toString().trim()
            val quantity = binding.quantityEditText.text.toString().trim()

            if (name.isNotEmpty() && quantity.isNotEmpty()) {
                val newItem = ShoppingListItem(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    quantity = quantity,
                    isChecked = false
                )
                viewModel.addItem(newItem)
                findNavController().navigateUp()
            } else {
                Toast.makeText(context, getString(R.string.fill_all_fields), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
