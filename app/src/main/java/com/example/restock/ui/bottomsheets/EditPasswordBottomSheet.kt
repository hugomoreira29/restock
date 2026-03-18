package com.example.restock.ui.bottomsheets

// Android - para gerir o ciclo de vida e apresentar mensagens ao utilizador
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast

// Binding - para aceder às vistas do layout de forma segura
import com.example.restock.databinding.BottomSheetEditPasswordBinding

// Material Design - componente base para o bottom sheet
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

// Firebase - autenticação
import com.google.firebase.auth.FirebaseAuth

/** HUGO MOREIRA - a22402246
 * Bottom sheet para alterar a palavra-passe do utilizador.
 * Utiliza o fluxo de recuperação de palavra-passe do Firebase,
 * enviando um email de redefinição para o utilizador.
 */
class EditPasswordBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetEditPasswordBinding? = null
    private val binding get() = _binding!!

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetEditPasswordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.closeButton.setOnClickListener { dismiss() }

        binding.saveButton.setOnClickListener {
            val confirmEmail = binding.confirmEmailEditText.text.toString().trim()
            val currentUserEmail = auth.currentUser?.email

            if (confirmEmail.isNotEmpty()) {
                // Verifica se o email introduzido corresponde ao email da conta autenticada
                if (currentUserEmail != null && confirmEmail == currentUserEmail) {
                    // Envia o email de redefinição de palavra-passe através do Firebase
                    auth.sendPasswordResetEmail(confirmEmail)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                Toast.makeText(context, "Email de redefinição enviado!", Toast.LENGTH_LONG).show()
                                dismiss()
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

    /**
     * Limpa o binding quando a vista é destruída para evitar fugas de memória.
     */
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "EditPasswordBottomSheet"
    }
}
