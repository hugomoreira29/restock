package com.example.restock.ui.bottomsheets

// Android - para gerir o ciclo de vida e apresentar mensagens ao utilizador
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast

// Binding - para aceder às vistas do layout de forma segura
import com.example.restock.databinding.BottomSheetEditNameBinding

// Material Design - componente base para o bottom sheet
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

// Firebase - autenticação e base de dados
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore

/** HUGO MOREIRA - a22402246
 * BottomSheetDialogFragment para editar o nome do utilizador.
 * Apresenta um campo de texto para o novo nome e um botão para guardar.
 */
class EditNameBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetEditNameBinding? = null
    private val binding get() = _binding!!

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    // Callback invocado após o nome ser atualizado com sucesso, para notificar o fragmento anterior
    var onNameUpdated: (() -> Unit)? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetEditNameBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Preenche o campo com o nome atual do utilizador
        binding.nameEditText.setText(auth.currentUser?.displayName)

        binding.closeButton.setOnClickListener { dismiss() }

        binding.saveButton.setOnClickListener {
            val newName = binding.nameEditText.text.toString().trim()
            if (newName.isNotEmpty()) {
                updateProfileName(newName)
            }
        }
    }

    /**
     * Atualiza o nome do utilizador no Firebase Authentication e no Firestore.
     * Após a atualização, invoca o callback e fecha o bottom sheet.
     */
    private fun updateProfileName(newName: String) {
        val user = auth.currentUser ?: return

        val profileUpdates = UserProfileChangeRequest.Builder()
            .setDisplayName(newName)
            .build()

        // Atualiza primeiro no Firebase Authentication
        user.updateProfile(profileUpdates).addOnCompleteListener { authTask ->
            if (authTask.isSuccessful) {
                // Se for bem-sucedido, atualiza também no Firestore
                firestore.collection("users").document(user.uid)
                    .update("name", newName)
                    .addOnSuccessListener {
                        Toast.makeText(context, "Nome atualizado com sucesso!", Toast.LENGTH_SHORT).show()
                        onNameUpdated?.invoke()
                        dismiss()
                    }
                    .addOnFailureListener {
                        Toast.makeText(context, "Falha ao guardar na base de dados.", Toast.LENGTH_SHORT).show()
                    }
            } else {
                Toast.makeText(context, "Falha ao atualizar o perfil.", Toast.LENGTH_SHORT).show()
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

    companion object {
        const val TAG = "EditNameBottomSheet"
    }
}
