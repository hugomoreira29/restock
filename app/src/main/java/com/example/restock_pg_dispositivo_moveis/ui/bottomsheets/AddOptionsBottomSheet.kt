package com.example.restock_pg_dispositivo_moveis.ui.bottomsheets

// HUGO MOREIRA - a22402246

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.restock_pg_dispositivo_moveis.databinding.BottomSheetAddOptionsBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class AddOptionsBottomSheet(private val onOptionSelected: (Option) -> Unit) : BottomSheetDialogFragment() {

    private var _binding: BottomSheetAddOptionsBinding? = null
    private val binding get() = _binding!!

    enum class Option {
        ADD_TO_INVENTORY,
        ADD_TO_SHOPPING_LIST
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetAddOptionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.optionAddToInventory.setOnClickListener {
            onOptionSelected(Option.ADD_TO_INVENTORY)
            dismiss()
        }

        binding.optionAddToList.setOnClickListener {
            onOptionSelected(Option.ADD_TO_SHOPPING_LIST)
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "AddOptionsBottomSheet"
    }
}
