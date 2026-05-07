package com.example.restock

// Android - para navegação entre activities e mensagens ao utilizador
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

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
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.WriteBatch

/** HUGO MOREIRA - a22402246
 * Activity responsável pelo ecrã de login da aplicação.
 * Suporta autenticação com email/palavra-passe e com conta Google.
 * No login com email, verifica se o endereço foi confirmado antes de permitir o acesso.
 * No login com Google, verifica se o utilizador já tem uma família configurada.
 */
@Suppress("DEPRECATION")
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
            val message = when (e.statusCode) {
                7    -> getString(R.string.google_signin_no_internet)   // NETWORK_ERROR
                12501 -> getString(R.string.google_signin_cancelled)    // SIGN_IN_CANCELLED
                else -> getString(R.string.google_signin_error)
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
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

        // Recuperação de palavra-passe
        binding.forgotPasswordBtn.setOnClickListener {
            showForgotPasswordDialog()
        }

        // Navega para o ecrã de registo
        binding.goRegisterBtn.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        // Inicia o fluxo de autenticação com Google
        // signOut() limpa a conta em cache para que o seletor de contas apareça sempre
        binding.googleSignInButton.setOnClickListener {
            showLoading()
            googleSignInClient.signOut().addOnCompleteListener {
                googleSignInLauncher.launch(googleSignInClient.signInIntent)
            }
        }
    }

    private fun showForgotPasswordDialog() {
        val layout = TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
            hint = getString(R.string.email)
        }
        val input = TextInputEditText(layout.context).apply {
            inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setText(binding.emailInput.text)
        }
        layout.addView(input)

        val container = FrameLayout(this).apply {
            val horizontal = (24 * resources.displayMetrics.density).toInt()
            setPadding(horizontal, 0, horizontal, 0)
            addView(layout)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.forgot_password_title)
            .setView(container)
            .setPositiveButton(R.string.button_send_reset_email) { _, _ ->
                val email = input.text.toString().trim()
                if (email.isNotEmpty()) {
                    auth.sendPasswordResetEmail(email)
                        .addOnSuccessListener {
                            Toast.makeText(this, getString(R.string.reset_email_sent), Toast.LENGTH_LONG).show()
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, getString(R.string.reset_email_error), Toast.LENGTH_LONG).show()
                        }
                } else {
                    Toast.makeText(this, getString(R.string.fill_all_fields), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
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
            hideLoading()
            Toast.makeText(this, getString(R.string.no_mfa_factor), Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Conclui a autenticação com o token do Google no Firebase.
     * Verifica se o utilizador já possui um perfil configurado no Firestore.
     */
    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val firebaseUser = auth.currentUser!!
                    checkUserSetup(firebaseUser)
                } else {
                    hideLoading()
                    handleLoginError(task.exception)
                }
            }
    }

    /**
     * Verifica se o utilizador já tem um documento no Firestore.
     * Se não tiver, inicia o fluxo de configuração de família.
     */
    private fun checkUserSetup(firebaseUser: FirebaseUser) {
        firestore.collection("users").document(firebaseUser.uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    hideLoading()
                    navigateToHome()
                } else {
                    hideLoading()
                    showFamilySetupDialog(firebaseUser)
                }
            }
            .addOnFailureListener {
                hideLoading()
                handleLoginError(it)
            }
    }

    /**
     * Mostra um diálogo para o utilizador escolher entre criar uma nova família
     * ou entrar numa existente através de um código.
     */
    private fun showFamilySetupDialog(firebaseUser: FirebaseUser) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.family_setup_title)
            .setMessage(R.string.family_setup_desc)
            .setPositiveButton(R.string.family_create_title) { _, _ ->
                showCreateFamilyDialog(firebaseUser)
            }
            .setNegativeButton(R.string.family_join_another_title) { _, _ ->
                showJoinFamilyDialog(firebaseUser)
            }
            .setCancelable(false)
            .show()
    }

    /**
     * Diálogo para definir o nome da nova família.
     */
    private fun showCreateFamilyDialog(firebaseUser: FirebaseUser) {
        val layout = TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
            hint = getString(R.string.family_name_hint)
        }
        val input = TextInputEditText(layout.context).apply {
            setText("Família ${firebaseUser.displayName}")
        }
        layout.addView(input)

        val container = FrameLayout(this).apply {
            val horizontal = (24 * resources.displayMetrics.density).toInt()
            setPadding(horizontal, 16, horizontal, 0)
            addView(layout)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.family_create_title)
            .setView(container)
            .setPositiveButton(R.string.create) { _, _ ->
                val familyName = input.text.toString().trim()
                if (familyName.isNotEmpty()) {
                    createFamilyForGoogleUser(firebaseUser, familyName)
                } else {
                    Toast.makeText(this, getString(R.string.family_name_empty), Toast.LENGTH_SHORT).show()
                    showCreateFamilyDialog(firebaseUser)
                }
            }
            .setNegativeButton(R.string.cancel) { _, _ -> showFamilySetupDialog(firebaseUser) }
            .setCancelable(false)
            .show()
    }

    /**
     * Diálogo para introduzir o código de convite de uma família existente.
     */
    private fun showJoinFamilyDialog(firebaseUser: FirebaseUser) {
        val layout = TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
            hint = getString(R.string.family_join_code_hint)
        }
        val input = TextInputEditText(layout.context)
        layout.addView(input)

        val container = FrameLayout(this).apply {
            val horizontal = (24 * resources.displayMetrics.density).toInt()
            setPadding(horizontal, 16, horizontal, 0)
            addView(layout)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.family_join_another_title)
            .setView(container)
            .setPositiveButton(R.string.family_join_button) { _, _ ->
                val inviteCode = input.text.toString().trim()
                if (inviteCode.isNotEmpty()) {
                    joinFamilyForGoogleUser(firebaseUser, inviteCode)
                } else {
                    Toast.makeText(this, getString(R.string.family_enter_code), Toast.LENGTH_SHORT).show()
                    showJoinFamilyDialog(firebaseUser)
                }
            }
            .setNegativeButton(R.string.cancel) { _, _ -> showFamilySetupDialog(firebaseUser) }
            .setCancelable(false)
            .show()
    }

    /**
     * Cria uma nova família com o nome fornecido e associa o utilizador Google a ela.
     */
    private fun createFamilyForGoogleUser(firebaseUser: FirebaseUser, familyName: String) {
        showLoading()
        val batch = firestore.batch()
        val newFamilyRef = firestore.collection("families").document()

        val newFamily = Family(
            id = newFamilyRef.id,
            name = familyName,
            members = listOf(firebaseUser.uid),
            roles = mapOf(firebaseUser.uid to "Admin")
        )
        batch.set(newFamilyRef, newFamily)

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
     * Tenta juntar o utilizador Google a uma família existente através do código de convite.
     */
    private fun joinFamilyForGoogleUser(firebaseUser: FirebaseUser, inviteCode: String) {
        showLoading()
        firestore.collection("families").whereEqualTo("inviteCode", inviteCode).get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    hideLoading()
                    Toast.makeText(this, getString(R.string.invalid_invite_code), Toast.LENGTH_LONG).show()
                    showJoinFamilyDialog(firebaseUser)
                    return@addOnSuccessListener
                }

                val familyDoc = documents.first()
                val familyId = familyDoc.id
                val batch = firestore.batch()

                val userRef = firestore.collection("users").document(firebaseUser.uid)
                val user = User(
                    uid = firebaseUser.uid,
                    name = firebaseUser.displayName ?: "",
                    email = firebaseUser.email ?: "",
                    familyId = familyId,
                    photoUrl = firebaseUser.photoUrl?.toString()
                )
                batch.set(userRef, user)

                val familyRef = firestore.collection("families").document(familyId)
                batch.update(familyRef, "members", FieldValue.arrayUnion(firebaseUser.uid))
                batch.update(familyRef, "roles.${firebaseUser.uid}", "Membro")
                batch.update(familyRef, "inviteCode", null)

                commitBatch(batch)
            }
            .addOnFailureListener {
                hideLoading()
                Toast.makeText(this, getString(R.string.family_search_error), Toast.LENGTH_LONG).show()
                showJoinFamilyDialog(firebaseUser)
            }
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
        binding.forgotPasswordBtn.isEnabled = false
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
        binding.forgotPasswordBtn.isEnabled = true
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
