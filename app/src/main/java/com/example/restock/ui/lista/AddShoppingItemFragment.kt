package com.example.restock.ui.lista

// Android - para gerir o ciclo de vida e apresentar mensagens ao utilizador
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast

// AndroidX - para o fragmento, ViewModel partilhado e navegação
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController

// Classes internas da aplicação - recursos, binding e modelo do item
import com.example.restock.R
import com.example.restock.databinding.FragmentAddShoppingItemBinding
import com.example.restock.model.ShoppingListItem

// Java - para gerar um identificador único para cada item
import java.util.UUID

/** HUGO MOREIRA - a22402246
 * Fragmento para adicionar um novo item à lista de compras.
 * Valida o nome e a quantidade antes de guardar o item,
 * utilizando o ViewModel partilhado com o fragmento da lista de compras.
 */
class AddShoppingItemFragment : Fragment() {

    private var _binding: FragmentAddShoppingItemBinding? = null
    private val binding get() = _binding!!

    // ViewModel partilhado com a Activity para manter o estado entre fragmentos
    private val viewModel: ShoppingListViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddShoppingItemBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        binding.addItemButton.setOnClickListener {
            val name = binding.itemNameEditText.text.toString().trim()
            val quantity = binding.quantityEditText.text.toString().trim()

            if (name.isNotEmpty() && quantity.isNotEmpty()) {
                // Cria um novo item com ID único e estado não comprado por defeito
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

    /**
     * Limpa o binding quando a vista é destruída para evitar fugas de memória.
     */
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
