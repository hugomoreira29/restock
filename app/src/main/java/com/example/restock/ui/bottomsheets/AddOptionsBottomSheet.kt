package com.example.restock.ui.bottomsheets

// Android - para gerir o ciclo de vida e inflar as vistas do bottom sheet
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

// Binding - para aceder às vistas do layout de forma segura
import com.example.restock.databinding.BottomSheetAddOptionsBinding

// Material Design - componente base para o bottom sheet
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * HUGO MOREIRA - a22402246
 * AddOptionsBottomSheet.kt
 * Bottom sheet que apresenta as opções de adição disponíveis.
 * Permite ao utilizador escolher entre adicionar um produto
 * ao inventário ou à lista de compras.
 */
class AddOptionsBottomSheet(private val onOptionSelected: (Option) -> Unit) : BottomSheetDialogFragment() {

    // Binding para aceder às vistas do bottom sheet de forma segura
    private var _binding: BottomSheetAddOptionsBinding? = null
    private val binding get() = _binding!!

    /**
     * Enumeração com as opções disponíveis no bottom sheet.
     * ADD_TO_INVENTORY - adiciona o produto ao inventário da família.
     * ADD_TO_SHOPPING_LIST - adiciona o produto à lista de compras.
     */
    enum class Option {
        ADD_TO_INVENTORY,
        ADD_TO_SHOPPING_LIST
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        // Infla o layout do bottom sheet e inicializa o binding
        _binding = BottomSheetAddOptionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    /**
     * Configura os listeners de clique para cada opção disponível.
     * Após a seleção, invoca o callback com a opção escolhida e fecha o bottom sheet.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Ao clicar em "Adicionar ao Inventário", invoca o callback e fecha o bottom sheet
        binding.optionAddToInventory.setOnClickListener {
            onOptionSelected(Option.ADD_TO_INVENTORY)
            dismiss()
        }

        // Ao clicar em "Adicionar à Lista de Compras", invoca o callback e fecha o bottom sheet
        binding.optionAddToList.setOnClickListener {
            onOptionSelected(Option.ADD_TO_SHOPPING_LIST)
            dismiss()
        }
    }

    /**
     * Limpa o binding quando a vista é destruída para evitar fugas de memória.
     */
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        // Tag utilizada para identificar o bottom sheet ao ser apresentado
        const val TAG = "AddOptionsBottomSheet"
    }
}
