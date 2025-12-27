package com.example.restock_pg_dispositivo_moveis.ui.bottomsheets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.example.restock_pg_dispositivo_moveis.databinding.BottomSheetEditPasswordBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth

/**
 * BottomSheetDialogFragment para alterar a password do utilizador.
 * Implementa um fluxo seguro que requer a password antiga antes de definir uma nova.
 */
class EditPasswordBottomSheet : BottomSheetDialogFragment() {

    // Binding para aceder aos elementos do layout.
    private var _binding: BottomSheetEditPasswordBinding? = null
    private val binding get() = _binding!!

    // Instância do Firebase Auth.
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetEditPasswordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Listener para o botão de fechar.
        binding.closeButton.setOnClickListener {
            dismiss()
        }

        // Listener para o botão de guardar.
        binding.saveButton.setOnClickListener {
            val oldPassword = binding.oldPasswordEditText.text.toString()
            val newPassword = binding.newPasswordEditText.text.toString()

            // Valida se ambos os campos foram preenchidos.
            if (oldPassword.isNotEmpty() && newPassword.isNotEmpty()) {
                // Inicia o processo de reautenticação e alteração.
                reauthenticateAndChangePassword(oldPassword, newPassword)
            } else {
                Toast.makeText(context, "Preencha todos os campos.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Reautentica o utilizador com a password antiga e, se bem-sucedido, atualiza para a nova password.
     * @param oldPassword A password atual do utilizador.
     * @param newPassword A nova password desejada.
     */
    private fun reauthenticateAndChangePassword(oldPassword: String, newPassword: String) {
        val user = auth.currentUser ?: return
        // Cria uma credencial com o email do utilizador e a password antiga fornecida.
        val credential = EmailAuthProvider.getCredential(user.email!!, oldPassword)

        // Tenta reautenticar o utilizador. Isto é uma medida de segurança do Firebase.
        user.reauthenticate(credential).addOnCompleteListener { reauthTask ->
            if (reauthTask.isSuccessful) {
                // Se a reautenticação funcionar (password antiga correta), atualiza para a nova password.
                user.updatePassword(newPassword).addOnCompleteListener { updateTask ->
                    if (updateTask.isSuccessful) {
                        Toast.makeText(context, "Password atualizada com sucesso!", Toast.LENGTH_SHORT).show()
                        dismiss() // Fecha o painel.
                    } else {
                        Toast.makeText(context, "Erro ao atualizar a password.", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                // Se a reautenticação falhar (provavelmente password antiga errada).
                Toast.makeText(context, "A password antiga está incorreta.", Toast.LENGTH_SHORT).show()
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

    companion object {
        const val TAG = "EditPasswordBottomSheet"
    }
}
