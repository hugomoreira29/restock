package com.example.restock

// Android - para navegação entre activities e mensagens ao utilizador
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast

// AndroidX - para a Activity base
import androidx.appcompat.app.AppCompatActivity

// Binding - para aceder às vistas do layout de forma segura
import com.example.restock.databinding.ActivityRegisterBinding

// Modelos internos da aplicação
import com.example.restock.model.Family
import com.example.restock.model.User

// Firebase - autenticação, atualização de perfil, base de dados e operações em lote
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.WriteBatch

/** HUGO MOREIRA - a22402246
 * Activity responsável pelo registo de novos utilizadores.
 * Cria a conta com email/palavra-passe e envia um email de verificação.
 * Se for fornecido um código de convite, junta o utilizador à família existente.
 * Caso contrário, cria automaticamente uma nova família com o utilizador como administrador.
 */
class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        binding.registerBtn.setOnClickListener {
            val name = binding.nameInput.text.toString().trim()
            val email = binding.emailInput.text.toString().trim()
            val confirmEmail = binding.confirmEmailInput.text.toString().trim()
            val password = binding.passwordInput.text.toString().trim()
            val inviteCode = binding.inviteCodeInput.text.toString().trim()

            if (name.isNotEmpty() && email.isNotEmpty() && confirmEmail.isNotEmpty() && password.isNotEmpty()) {
                if (email == confirmEmail) {
                    showLoading()
                    auth.createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener(this) { task ->
                            if (task.isSuccessful) {
                                val firebaseUser = auth.currentUser!!

                                // Atualiza o nome de perfil no Firebase Authentication
                                val profileUpdates = UserProfileChangeRequest.Builder()
                                    .setDisplayName(name).build()
                                firebaseUser.updateProfile(profileUpdates)

                                // Envia o email de verificação antes de permitir o acesso
                                firebaseUser.sendEmailVerification()
                                    .addOnSuccessListener {
                                        Toast.makeText(baseContext, "Email de verificação enviado para $email", Toast.LENGTH_LONG).show()
                                    }
                                    .addOnFailureListener { e ->
                                        Toast.makeText(baseContext, "Erro ao enviar email de verificação: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }

                                // Junta-se à família existente ou cria uma nova
                                if (inviteCode.isNotEmpty()) {
                                    joinFamily(inviteCode, firebaseUser.uid, name, email)
                                } else {
                                    createNewFamily(firebaseUser.uid, name, email)
                                }
                            } else {
                                hideLoading()
                                Toast.makeText(baseContext, getString(R.string.register_fail, task.exception?.message), Toast.LENGTH_SHORT).show()
                            }
                        }
                } else {
                    Toast.makeText(this, "Os emails não coincidem.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, getString(R.string.fill_all_fields), Toast.LENGTH_SHORT).show()
            }
        }

        binding.goLoginBtn.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }
    }

    /**
     * Tenta juntar o utilizador a uma família existente através do código de convite.
     * Adiciona o utilizador à lista de membros e invalida o código após a utilização.
     * Em caso de código inválido ou erro, elimina a conta criada para evitar inconsistências.
     */
    private fun joinFamily(inviteCode: String, userId: String, name: String, email: String) {
        firestore.collection("families").whereEqualTo("inviteCode", inviteCode).get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    deleteAccountAndShowError(getString(R.string.invalid_invite_code))
                    return@addOnSuccessListener
                }

                val familyDoc = documents.first()
                val familyId = familyDoc.id
                val batch = firestore.batch()

                // Cria o documento do utilizador associado à família
                val userRef = firestore.collection("users").document(userId)
                batch.set(userRef, User(uid = userId, name = name, email = email, familyId = familyId))

                // Adiciona o utilizador à família e invalida o código de convite
                val familyRef = firestore.collection("families").document(familyId)
                batch.update(familyRef, "members", FieldValue.arrayUnion(userId))
                batch.update(familyRef, "roles.$userId", "Membro")
                batch.update(familyRef, "inviteCode", null)

                commitBatch(batch)
            }
            .addOnFailureListener {
                deleteAccountAndShowError("Erro ao procurar família. Tente novamente.")
            }
    }

    /**
     * Cria uma nova família e o perfil do utilizador no Firestore usando um batch.
     * O utilizador é definido automaticamente como administrador da família criada.
     */
    private fun createNewFamily(userId: String, name: String, email: String) {
        val batch = firestore.batch()
        val newFamilyRef = firestore.collection("families").document()

        // Cria a família com o utilizador como único membro e administrador
        val newFamily = Family(
            id = newFamilyRef.id,
            name = "Família $name",
            members = listOf(userId),
            roles = mapOf(userId to "Admin")
        )
        batch.set(newFamilyRef, newFamily)

        // Cria o documento do utilizador associado à nova família
        val userRef = firestore.collection("users").document(userId)
        batch.set(userRef, User(uid = userId, name = name, email = email, familyId = newFamily.id))

        commitBatch(batch)
    }

    /**
     * Executa o batch de operações no Firestore.
     * Em caso de sucesso, navega para a HomeActivity.
     * Em caso de falha, elimina a conta criada para evitar dados inconsistentes.
     */
    private fun commitBatch(batch: WriteBatch) {
        batch.commit().addOnCompleteListener { batchTask ->
            hideLoading()
            if (batchTask.isSuccessful) {
                Toast.makeText(this, getString(R.string.register_success), Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, HomeActivity::class.java))
                finish()
            } else {
                deleteAccountAndShowError(
                    getString(R.string.error_saving_data, batchTask.exception?.message)
                )
            }
        }
    }

    /**
     * Elimina a conta do Firebase Authentication em caso de erro
     * e apresenta a mensagem de erro ao utilizador.
     */
    private fun deleteAccountAndShowError(errorMessage: String) {
        auth.currentUser?.delete()?.addOnCompleteListener {
            hideLoading()
            Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Mostra o indicador de carregamento e desativa todos os controlos de interação
     * para evitar ações duplicadas durante o registo.
     */
    private fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
        binding.nameInputLayout.isEnabled = false
        binding.emailInputLayout.isEnabled = false
        binding.confirmEmailInputLayout.isEnabled = false
        binding.passwordInputLayout.isEnabled = false
        binding.inviteCodeInputLayout.isEnabled = false
        binding.registerBtn.isEnabled = false
        binding.goLoginBtn.isEnabled = false
    }

    /**
     * Oculta o indicador de carregamento e reativa todos os controlos de interação.
     */
    private fun hideLoading() {
        binding.progressBar.visibility = View.GONE
        binding.nameInputLayout.isEnabled = true
        binding.emailInputLayout.isEnabled = true
        binding.confirmEmailInputLayout.isEnabled = true
        binding.passwordInputLayout.isEnabled = true
        binding.inviteCodeInputLayout.isEnabled = true
        binding.registerBtn.isEnabled = true
        binding.goLoginBtn.isEnabled = true
    }
}
