package com.example.restock_pg_dispositivo_moveis.ui.bottomsheets

// HUGO MOREIRA - a22402246

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.example.restock_pg_dispositivo_moveis.databinding.BottomSheetEditPasswordBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.auth.FirebaseAuth

/**
 * BottomSheetDialogFragment para "alterar" a password do utilizador.
 * Utiliza o fluxo de "Recuperação de Password" do Firebase, enviando um email para o utilizador.
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

        // Listener para o botão de "Enviar Email".
        binding.saveButton.setOnClickListener {
            val confirmEmail = binding.confirmEmailEditText.text.toString().trim()
            val currentUserEmail = auth.currentUser?.email

            if (confirmEmail.isNotEmpty()) {
                // Verifica se o email introduzido é igual ao do utilizador logado.
                if (currentUserEmail != null && confirmEmail == currentUserEmail) {
                    // Envia o email de redefinição de password do Firebase.
                    auth.sendPasswordResetEmail(confirmEmail)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                Toast.makeText(context, "Email de redefinição enviado!", Toast.LENGTH_LONG).show()
                                dismiss() // Fecha o painel após sucesso.
                            } else {
                                Toast.makeText(context, "Erro ao enviar email: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                } else {
                    Toast.makeText(context, "O email introduzido não corresponde à sua conta.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "Por favor introduza o seu email.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "EditPasswordBottomSheet"
    }
}
