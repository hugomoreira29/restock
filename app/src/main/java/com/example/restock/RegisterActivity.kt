package com.example.restock

// Android - para navegação entre activities e mensagens ao utilizador
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast

// AndroidX - para a Activity base e operações assíncronas ligadas ao seu ciclo de vida
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope

// Binding - para aceder às vistas do layout de forma segura
import com.example.restock.databinding.ActivityRegisterBinding
import com.example.restock.ui.openPrivacyPolicy

// Modelos e dados internos da aplicação
import com.example.restock.data.FamilyRepository
import com.example.restock.model.Family
import com.example.restock.model.User

// Firebase - autenticação, atualização de perfil, base de dados e operações em lote
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.WriteBatch

// Coroutines - para consultar o convite sem bloquear a interface
import kotlinx.coroutines.launch

/** HUGO MOREIRA - a22402246
 * Activity responsável pelo registo de novos utilizadores.
 * Cria a conta com email/palavra-passe e envia um email de verificação.
 * Se for fornecido um código de convite, envia um pedido de adesão a essa família.
 * Caso contrário, cria automaticamente uma nova família com o utilizador como administrador.
 */
class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private val familyRepository by lazy { FamilyRepository(firestore) }

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
                                        Toast.makeText(baseContext, getString(R.string.register_success), Toast.LENGTH_LONG).show()
                                    }
                                    .addOnFailureListener { e ->
                                        Toast.makeText(baseContext, getString(R.string.email_verification_error, e.message), Toast.LENGTH_SHORT).show()
                                    }

                                // Junta-se à família existente ou cria uma nova
                                if (inviteCode.isNotEmpty()) {
                                    requestToJoinFamily(inviteCode, firebaseUser.uid, name)
                                } else {
                                    createNewFamily(firebaseUser.uid, name)
                                }
                            } else {
                                hideLoading()
                                Toast.makeText(baseContext, getString(R.string.register_fail, task.exception?.message), Toast.LENGTH_SHORT).show()
                            }
                        }
                } else {
                    Toast.makeText(this, getString(R.string.emails_do_not_match), Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, getString(R.string.fill_all_fields), Toast.LENGTH_SHORT).show()
            }
        }

        binding.goLoginBtn.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        binding.privacyNotice.setOnClickListener { openPrivacyPolicy() }
    }

    /**
     * Cria o perfil do utilizador e envia um pedido de adesão à família do convite,
     * que fica à espera da aprovação de um administrador.
     * Em caso de código inválido ou erro, elimina a conta criada para evitar inconsistências.
     */
    private fun requestToJoinFamily(inviteCode: String, userId: String, name: String) {
        lifecycleScope.launch {
            val familyId = try {
                familyRepository.findFamilyIdByInvite(inviteCode)
            } catch (e: FirebaseFirestoreException) {
                deleteAccountAndShowError(getString(R.string.family_search_error))
                return@launch
            }
            if (familyId == null) {
                deleteAccountAndShowError(getString(R.string.invalid_invite_code))
                return@launch
            }

            val batch = firestore.batch()
            val userRef = firestore.collection("users").document(userId)
            batch.set(userRef, User(uid = userId, name = name, pendingFamilyId = familyId))
            familyRepository.addJoinRequest(batch, familyId, userId)

            commitBatch(batch, successMessage = R.string.family_request_sent)
        }
    }

    /**
     * Cria uma nova família e o perfil do utilizador no Firestore usando um batch.
     * O utilizador é definido automaticamente como administrador da família criada.
     */
    private fun createNewFamily(userId: String, name: String) {
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
        batch.set(userRef, User(uid = userId, name = name, familyId = newFamily.id))

        commitBatch(batch)
    }

    /**
     * Executa o batch de operações no Firestore.
     * Em caso de sucesso, faz logout e navega para a LoginActivity para verificação de email.
     */
    private fun commitBatch(batch: WriteBatch, @StringRes successMessage: Int? = null) {
        batch.commit().addOnCompleteListener { batchTask ->
            hideLoading()
            if (batchTask.isSuccessful) {
                successMessage?.let { Toast.makeText(baseContext, getString(it), Toast.LENGTH_LONG).show() }
                // Força o logout para garantir que o utilizador valide o email antes de entrar
                auth.signOut()
                val intent = Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
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
