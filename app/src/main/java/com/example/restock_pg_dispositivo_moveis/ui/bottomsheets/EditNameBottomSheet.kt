package com.example.restock_pg_dispositivo_moveis.ui.bottomsheets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.example.restock_pg_dispositivo_moveis.databinding.BottomSheetEditNameBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore

/**
 * BottomSheetDialogFragment para editar o nome do utilizador.
 * Apresenta um campo de texto para o novo nome e um botão para guardar.
 */
class EditNameBottomSheet : BottomSheetDialogFragment() {

    // Binding para aceder aos elementos do layout.
    private var _binding: BottomSheetEditNameBinding? = null
    private val binding get() = _binding!!

    // Instâncias dos serviços Firebase.
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    // Callback para notificar o fragmento anterior (AccountFragment) que o nome foi atualizado.
    var onNameUpdated: (() -> Unit)? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetEditNameBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Preenche o campo de texto com o nome atual do utilizador.
        binding.nameEditText.setText(auth.currentUser?.displayName)

        // Listener para o botão de fechar.
        binding.closeButton.setOnClickListener {
            dismiss() // Fecha o BottomSheet.
        }

        // Listener para o botão de guardar.
        binding.saveButton.setOnClickListener {
            val newName = binding.nameEditText.text.toString().trim()
            if (newName.isNotEmpty()) {
                updateProfileName(newName)
            }
        }
    }

    /**
     * Atualiza o nome do utilizador no Firebase Authentication e no Firestore.
     * @param newName O novo nome a ser guardado.
     */
    private fun updateProfileName(newName: String) {
        val user = auth.currentUser ?: return

        // 1. Atualiza o perfil no Firebase Authentication.
        val profileUpdates = UserProfileChangeRequest.Builder()
            .setDisplayName(newName)
            .build()
        user.updateProfile(profileUpdates).addOnCompleteListener { authTask ->
            if (authTask.isSuccessful) {
                // 2. Se for bem-sucedido, atualiza também o campo "name" no Firestore.
                firestore.collection("users").document(user.uid)
                    .update("name", newName)
                    .addOnSuccessListener {
                        Toast.makeText(context, "Nome atualizado com sucesso!", Toast.LENGTH_SHORT).show()
                        onNameUpdated?.invoke() // Chama o callback para o AccountFragment saber que pode recarregar os dados.
                        dismiss() // Fecha o BottomSheet.
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(context, "Falha ao guardar na base de dados.", Toast.LENGTH_SHORT).show()
                    }
            } else {
                Toast.makeText(context, "Falha ao atualizar o perfil.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Limpa o binding para evitar memory leaks.
     */
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // Companion object para definir uma TAG, útil para encontrar o fragmento no FragmentManager.
    companion object {
        const val TAG = "EditNameBottomSheet"
    }
}
