package com.example.restock

// Android - para navegação entre activities e mensagens ao utilizador
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast

// AndroidX - para o lançador de resultados e a Activity base
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

// Binding - para aceder às vistas do layout de forma segura
import com.example.restock.databinding.ActivityLoginBinding

// Modelos internos da aplicação
import com.example.restock.model.Family
import com.example.restock.model.User

// Google Sign-In - para autenticação com conta Google
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

// Firebase - autenticação, base de dados e operações em lote
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.WriteBatch

/** HUGO MOREIRA - a22402246
 * Activity responsável pelo ecrã de login da aplicação.
 * Suporta autenticação com email/palavra-passe e com conta Google.
 * No login com email, verifica se o endereço foi confirmado antes de permitir o acesso.
 * No primeiro login com Google, cria automaticamente uma família e o perfil do utilizador.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var googleSignInClient: GoogleSignInClient

    // Lançador para o fluxo de autenticação com Google
    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            if (account != null) {
                firebaseAuthWithGoogle(account.idToken!!)
            } else {
                hideLoading()
            }
        } catch (e: ApiException) {
            hideLoading()
            Toast.makeText(this, "Google Sign-In failed: ${e.statusCode}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Configura o cliente de autenticação Google com o ID do projeto Firebase
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("559445056454-5ucdor4jsgcbo41hsk5040magfskkdj4.apps.googleusercontent.com")
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // Login com email e palavra-passe
        binding.loginBtn.setOnClickListener {
            val email = binding.emailInput.text.toString().trim()
            val password = binding.passwordInput.text.toString().trim()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                showLoading()
                auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this) { task ->
                        hideLoading()
                        if (task.isSuccessful) {
                            val user = auth.currentUser
                            if (user != null && user.isEmailVerified) {
                                Toast.makeText(this, getString(R.string.login_success), Toast.LENGTH_SHORT).show()
                                navigateToHome()
                            } else {
                                // Envia novo email de verificação e impede o acesso até ser confirmado
                                Toast.makeText(this, getString(R.string.verify_email_notice), Toast.LENGTH_LONG).show()
                                user?.sendEmailVerification()
                                auth.signOut()
                            }
                        } else {
                            handleLoginError(task.exception)
                        }
                    }
            } else {
                Toast.makeText(this, getString(R.string.fill_all_fields), Toast.LENGTH_SHORT).show()
            }
        }

        // Navega para o ecrã de registo
        binding.goRegisterBtn.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        // Inicia o fluxo de autenticação com Google
        binding.googleSignInButton.setOnClickListener {
            showLoading()
            googleSignInLauncher.launch(googleSignInClient.signInIntent)
        }
    }

    /**
     * Trata os erros de login e apresenta mensagens amigáveis ao utilizador.
     */
    private fun handleLoginError(exception: Exception?) {
        when (exception) {
            is FirebaseAuthMultiFactorException -> {
                handleMfaRequirement(exception)
            }
            is FirebaseAuthInvalidUserException -> {
                val errorCode = exception.errorCode
                if (errorCode == "ERROR_USER_NOT_FOUND") {
                    Toast.makeText(this, getString(R.string.error_user_not_found), Toast.LENGTH_LONG).show()
                } else if (errorCode == "ERROR_USER_DISABLED") {
                    Toast.makeText(this, getString(R.string.error_user_disabled), Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, exception.localizedMessage, Toast.LENGTH_LONG).show()
                }
            }
            is FirebaseAuthInvalidCredentialsException -> {
                // Pode ser email mal formatado ou password errada
                Toast.makeText(this, getString(R.string.error_wrong_password), Toast.LENGTH_LONG).show()
            }
            is FirebaseTooManyRequestsException -> {
                Toast.makeText(this, getString(R.string.error_too_many_requests), Toast.LENGTH_LONG).show()
            }
            else -> {
                val errorMessage = exception?.localizedMessage ?: getString(R.string.login_fail)
                Toast.makeText(baseContext, errorMessage, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun handleMfaRequirement(exception: FirebaseAuthMultiFactorException) {
        val resolver = exception.resolver
        val hints = resolver.hints
        
        // Verifica se há hints de telefone disponíveis
        if (hints.isNotEmpty() && hints[0] is PhoneMultiFactorInfo) {
            val phoneHint = hints[0] as PhoneMultiFactorInfo
            
            // Passamos o resolver através de uma variável estática para evitar problemas de serialização
            MfaActivity.multiFactorResolver = resolver
            
            val intent = Intent(this, MfaActivity::class.java).apply {
                putExtra("MFA_HINT", phoneHint)
            }
            startActivity(intent)
        } else {
            Toast.makeText(this, "Nenhum fator de autenticação secundário encontrado.", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Conclui a autenticação com o token do Google no Firebase.
     * Se for um novo utilizador, cria automaticamente a família e o perfil.
     */
    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val firebaseUser = auth.currentUser!!
                    val isNewUser = task.result.additionalUserInfo?.isNewUser ?: false

                    if (isNewUser) {
                        // Primeiro login com Google: cria família e perfil do utilizador
                        createNewFamilyForGoogleUser(firebaseUser)
                    } else {
                        hideLoading()
                        navigateToHome()
                    }
                } else {
                    handleLoginError(task.exception)
                }
            }
    }

    /**
     * Cria uma nova família e o perfil do utilizador no Firestore usando um batch.
     * O utilizador é definido automaticamente como administrador da família criada.
     */
    private fun createNewFamilyForGoogleUser(firebaseUser: FirebaseUser) {
        val batch = firestore.batch()
        val newFamilyRef = firestore.collection("families").document()

        // Cria a família com o utilizador como único membro e administrador
        val newFamily = Family(
            id = newFamilyRef.id,
            name = "Família ${firebaseUser.displayName}",
            members = listOf(firebaseUser.uid),
            roles = mapOf(firebaseUser.uid to "Admin")
        )
        batch.set(newFamilyRef, newFamily)

        // Cria o documento do utilizador associado à família criada
        val userRef = firestore.collection("users").document(firebaseUser.uid)
        val user = User(
            uid = firebaseUser.uid,
            name = firebaseUser.displayName ?: "",
            email = firebaseUser.email ?: "",
            familyId = newFamily.id,
            photoUrl = firebaseUser.photoUrl?.toString()
        )
        batch.set(userRef, user)

        commitBatch(batch)
    }

    /**
     * Executa o batch de operações no Firestore.
     * Em caso de falha, elimina a conta criada para evitar dados inconsistentes.
     */
    private fun commitBatch(batch: WriteBatch) {
        batch.commit().addOnCompleteListener { batchTask ->
            hideLoading()
            if (batchTask.isSuccessful) {
                Toast.makeText(this, getString(R.string.register_success), Toast.LENGTH_SHORT).show()
                navigateToHome()
            } else {
                // Elimina a conta do Firebase Authentication se os dados não forem guardados
                auth.currentUser?.delete()
                Toast.makeText(
                    baseContext,
                    getString(R.string.error_saving_data, batchTask.exception?.message),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * Mostra o indicador de carregamento e desativa todos os controlos de interação
     * para evitar ações duplicadas durante a autenticação.
     */
    private fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
        binding.emailInputLayout.isEnabled = false
        binding.passwordInputLayout.isEnabled = false
        binding.loginBtn.isEnabled = false
        binding.goRegisterBtn.isEnabled = false
        binding.googleSignInButton.isEnabled = false
    }

    /**
     * Oculta o indicador de carregamento e reativa todos os controlos de interação.
     */
    private fun hideLoading() {
        binding.progressBar.visibility = View.GONE
        binding.emailInputLayout.isEnabled = true
        binding.passwordInputLayout.isEnabled = true
        binding.loginBtn.isEnabled = true
        binding.goRegisterBtn.isEnabled = true
        binding.googleSignInButton.isEnabled = true
    }

    /**
     * Navega para a HomeActivity após o login bem-sucedido.
     * Limpa a pilha de activities para impedir o regresso ao ecrã de login.
     */
    private fun navigateToHome() {
        val intent = Intent(this, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}
