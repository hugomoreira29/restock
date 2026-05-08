package com.example.restock.ui.account

// Android - para aceder a funcionalidades do sistema e navegação
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast

// AndroidX - para registar o lançador de seleção de imagens e navegação entre fragmentos
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController

// Google Sign-In - para reautenticação de utilizadores Google
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

// Glide - para carregar e apresentar imagens de forma eficiente
import com.bumptech.glide.Glide

// Classes internas da aplicação - atividade de login, recursos e binding
import com.example.restock.LoginActivity
import com.example.restock.R
import com.example.restock.databinding.FragmentAccountBinding

// Bottom sheets - para editar o nome e a palavra-passe do utilizador
import com.example.restock.ui.bottomsheets.EditNameBottomSheet
import com.example.restock.ui.bottomsheets.EditPasswordBottomSheet

// Firebase - autenticação, base de dados e armazenamento de ficheiros
import com.google.firebase.FirebaseException
import com.google.firebase.auth.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.concurrent.TimeUnit

/** HUGO MOREIRA - a22402246
 * AccountFragment.kt
 * Responsável por gerir a conta do utilizador, incluindo perfil e segurança (MFA).
*/
class AccountFragment : Fragment() {

    // Binding para aceder às vistas do fragmento de forma segura
    private var _binding: FragmentAccountBinding? = null
    private val binding get() = _binding!!

    // Instâncias do Firebase inicializadas apenas quando necessário (lazy)
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val storage: FirebaseStorage by lazy { FirebaseStorage.getInstance() }

    // Variáveis para o fluxo de MFA
    private var verificationId: String? = null
    private var resendingToken: PhoneAuthProvider.ForceResendingToken? = null

    // Callback pendente após reautenticação bem-sucedida
    private var pendingReauthCallback: (() -> Unit)? = null

    // Lançador para abrir a galeria e selecionar uma imagem de perfil
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                uploadProfileImage(uri)
            }
        }
    }

    // Lançador para reautenticação via Google Sign-In
    private val googleReauthLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val credential = GoogleAuthProvider.getCredential(account.idToken!!, null)
            val user = auth.currentUser ?: return@registerForActivityResult
            showMfaLoading(true)
            user.reauthenticate(credential).addOnCompleteListener { reauth ->
                showMfaLoading(false)
                if (reauth.isSuccessful) {
                    Toast.makeText(requireContext(), getString(R.string.reauth_success), Toast.LENGTH_SHORT).show()
                    pendingReauthCallback?.invoke()
                    pendingReauthCallback = null
                } else {
                    Toast.makeText(requireContext(), getString(R.string.reauth_fail), Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: ApiException) {
            Toast.makeText(requireContext(), getString(R.string.reauth_fail), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAccountBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadUserData()
        setupClickListeners()
        updateMfaUi()
    }

    /**
     * Carrega os dados do utilizador autenticado e apresenta-os no ecrã.
     */
    private fun loadUserData() {
        val user = auth.currentUser ?: return

        binding.nameTextView.text = user.displayName
        binding.emailTextView.text = user.email

        Glide.with(this)
            .load(user.photoUrl)
            .placeholder(R.drawable.ic_avatar)
            .circleCrop()
            .into(binding.profileImageView)

        val isGoogleUser = user.providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID }

        if (isGoogleUser) {
            binding.passwordLabel.visibility = View.GONE
            binding.passwordCard.visibility = View.GONE
        } else {
            binding.passwordLabel.visibility = View.VISIBLE
            binding.passwordCard.visibility = View.VISIBLE
        }
    }

    /**
     * Atualiza a interface relativa ao estado do MFA.
     */
    private fun updateMfaUi() {
        val user = auth.currentUser
        val mfaFactors = user?.multiFactor?.enrolledFactors

        if (mfaFactors != null && mfaFactors.isNotEmpty()) {
            binding.mfaStatusText.text = getString(R.string.mfa_status_enabled)
            binding.mfaStatusText.setTextColor(android.graphics.Color.parseColor("#388E3C"))
            binding.mfaEnrollContainer.visibility = View.GONE
        } else {
            binding.mfaStatusText.text = getString(R.string.mfa_status_disabled)
            binding.mfaStatusText.setTextColor(android.graphics.Color.parseColor("#D32F2F"))
            binding.mfaEnrollContainer.visibility = View.VISIBLE
        }
    }

    private fun setupClickListeners() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        binding.editProfileImageButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            pickImageLauncher.launch(intent)
        }

        binding.nameTextView.setOnClickListener {
            val editNameSheet = EditNameBottomSheet()
            editNameSheet.onNameUpdated = { loadUserData() }
            editNameSheet.show(parentFragmentManager, EditNameBottomSheet.TAG)
        }

        binding.passwordTextView.setOnClickListener {
            EditPasswordBottomSheet().show(parentFragmentManager, EditPasswordBottomSheet.TAG)
        }

        // Lógica do botão de ativar MFA
        binding.btnEnrollMfa.setOnClickListener {
            val phoneNumber = binding.phoneInput.text.toString().trim()
            if (phoneNumber.isNotEmpty()) {
                startMfaEnrollment(phoneNumber)
            } else {
                Toast.makeText(requireContext(), getString(R.string.invalid_phone_error), Toast.LENGTH_SHORT).show()
            }
        }

        // Lógica do botão de confirmar código SMS
        binding.btnVerifyCode.setOnClickListener {
            val code = binding.smsCodeInput.text.toString().trim()
            if (code.length == 6 && verificationId != null) {
                finalizeMfaEnrollment(code, binding.phoneInput.text.toString().trim())
            }
        }

        binding.logoutButton.setOnClickListener {
            auth.signOut()
            val intent = Intent(requireActivity(), LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            requireActivity().finish()
        }

        binding.deleteAccountButton.setOnClickListener {
            showDeleteAccountConfirmation()
        }
    }

    private fun showDeleteAccountConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Apagar conta")
            .setMessage("Tens a certeza que queres apagar a tua conta? Esta ação é irreversível.")
            .setPositiveButton("Apagar") { _, _ ->
                checkFamilyRoleAndDelete()
            }
            .setNegativeButton(getString(R.string.close), null)
            .show()
    }

    private fun checkFamilyRoleAndDelete() {
        val uid = auth.currentUser?.uid ?: return

        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { userDoc ->
                val familyId = userDoc.getString("familyId")

                if (familyId == null) {
                    // Utilizador sem família — apagar conta diretamente
                    confirmAndDeleteAccount(familyId = null)
                    return@addOnSuccessListener
                }

                firestore.collection("families").document(familyId).get()
                    .addOnSuccessListener { familyDoc ->
                        @Suppress("UNCHECKED_CAST")
                        val roles = familyDoc.get("roles") as? Map<String, String> ?: emptyMap()
                        val members = familyDoc.get("members") as? List<String> ?: emptyList()
                        val isAdmin = roles[uid] == "Admin"
                        val otherMembers = members.filter { it != uid }

                        if (isAdmin && otherMembers.isNotEmpty()) {
                            // Admin com outros membros — bloquear e pedir transferência
                            MaterialAlertDialogBuilder(requireContext())
                                .setTitle("Não é possível apagar a conta")
                                .setMessage("És o Admin da tua família e ainda existem outros membros.\n\nTransfere o papel de Admin a outro membro na página da família antes de apagar a conta.")
                                .setPositiveButton("OK", null)
                                .show()
                        } else {
                            // Admin sem outros membros (família será apagada) ou membro normal
                            val message = if (isAdmin) {
                                "Tens a certeza? A tua família e todos os dados associados (inventário, lista de compras, histórico) serão apagados permanentemente."
                            } else {
                                "Tens a certeza? Serás removido da família e a tua conta será apagada permanentemente."
                            }
                            MaterialAlertDialogBuilder(requireContext())
                                .setTitle("Confirmar eliminação")
                                .setMessage(message)
                                .setPositiveButton("Apagar tudo") { _, _ ->
                                    showReauthDialog { confirmAndDeleteAccount(familyId) }
                                }
                                .setNegativeButton(getString(R.string.close), null)
                                .show()
                        }
                    }
                    .addOnFailureListener {
                        Toast.makeText(requireContext(), "Erro ao verificar família. Tenta novamente.", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Erro ao carregar dados. Tenta novamente.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun confirmAndDeleteAccount(familyId: String?) {
        val uid = auth.currentUser?.uid ?: return
        val user = auth.currentUser ?: return

        if (familyId != null) {
            firestore.collection("families").document(familyId).get()
                .addOnSuccessListener { familyDoc ->
                    val members = familyDoc.get("members") as? List<String> ?: emptyList()
                    val otherMembers = members.filter { it != uid }

                    if (otherMembers.isEmpty()) {
                        // Último membro — apagar família inteira
                        deleteFamilyAndAccount(familyId, uid, user)
                    } else {
                        // Membro normal — apenas remover da família
                        removeFromFamilyAndDeleteAccount(familyId, uid, user)
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(requireContext(), "Erro ao apagar conta. Tenta novamente.", Toast.LENGTH_SHORT).show()
                }
        } else {
            deleteUserDocumentAndAuthAccount(uid, user)
        }
    }

    private fun deleteFamilyAndAccount(familyId: String, uid: String, user: FirebaseUser) {
        val familyRef = firestore.collection("families").document(familyId)
        val subcollections = listOf("products", "shopping_list", "history", "budgetHistory")

        // Apagar todas as subcoleções e depois o documento da família
        var pending = subcollections.size
        if (pending == 0) {
            familyRef.delete().addOnSuccessListener {
                deleteUserDocumentAndAuthAccount(uid, user)
            }
            return
        }

        for (sub in subcollections) {
            familyRef.collection(sub).get().addOnSuccessListener { snap ->
                val batch = firestore.batch()
                snap.documents.forEach { batch.delete(it.reference) }
                batch.commit().addOnCompleteListener {
                    pending--
                    if (pending == 0) {
                        familyRef.delete().addOnSuccessListener {
                            deleteUserDocumentAndAuthAccount(uid, user)
                        }
                    }
                }
            }.addOnFailureListener {
                pending--
                if (pending == 0) {
                    familyRef.delete().addOnSuccessListener {
                        deleteUserDocumentAndAuthAccount(uid, user)
                    }
                }
            }
        }
    }

    private fun removeFromFamilyAndDeleteAccount(familyId: String, uid: String, user: FirebaseUser) {
        val familyRef = firestore.collection("families").document(familyId)
        firestore.runTransaction { transaction ->
            val familyDoc = transaction.get(familyRef)
            @Suppress("UNCHECKED_CAST")
            val members = (familyDoc.get("members") as? List<String> ?: emptyList()).toMutableList()
            @Suppress("UNCHECKED_CAST")
            val roles = (familyDoc.get("roles") as? Map<String, String> ?: emptyMap()).toMutableMap()
            @Suppress("UNCHECKED_CAST")
            val pending = (familyDoc.get("pendingMembers") as? List<String> ?: emptyList()).toMutableList()

            members.remove(uid)
            roles.remove(uid)
            pending.remove(uid)

            transaction.update(familyRef, mapOf(
                "members" to members,
                "roles" to roles,
                "pendingMembers" to pending
            ))
        }.addOnSuccessListener {
            deleteUserDocumentAndAuthAccount(uid, user)
        }.addOnFailureListener {
            Toast.makeText(requireContext(), "Erro ao remover da família. Tenta novamente.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteUserDocumentAndAuthAccount(uid: String, user: FirebaseUser) {
        firestore.collection("users").document(uid).delete()
            .addOnCompleteListener {
                user.delete().addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val intent = Intent(requireActivity(), LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        requireActivity().finish()
                    } else {
                        val exception = task.exception
                        if (exception is FirebaseAuthRecentLoginRequiredException) {
                            showReauthDialog { deleteUserDocumentAndAuthAccount(uid, user) }
                        } else {
                            Toast.makeText(requireContext(), "Erro ao apagar conta: ${exception?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
    }

    private fun startMfaEnrollment(phoneNumber: String) {
        showMfaLoading(true)
        val user = auth.currentUser ?: return

        user.multiFactor.session.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val session = task.result
                val options = PhoneAuthOptions.newBuilder()
                    .setPhoneNumber(phoneNumber)
                    .setTimeout(60L, TimeUnit.SECONDS)
                    .setActivity(requireActivity())
                    .setMultiFactorSession(session)
                    .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                        override fun onCodeSent(vId: String, token: PhoneAuthProvider.ForceResendingToken) {
                            showMfaLoading(false)
                            verificationId = vId
                            resendingToken = token
                            binding.mfaEnrollContainer.visibility = View.GONE
                            binding.mfaVerifyContainer.visibility = View.VISIBLE
                            Toast.makeText(requireContext(), getString(R.string.sms_sent_success, phoneNumber), Toast.LENGTH_SHORT).show()
                        }

                        override fun onVerificationCompleted(p0: PhoneAuthCredential) {}

                        override fun onVerificationFailed(e: FirebaseException) {
                            showMfaLoading(false)
                            if (e is FirebaseAuthRecentLoginRequiredException) {
                                showReauthDialog { startMfaEnrollment(phoneNumber) }
                            } else {
                                Toast.makeText(requireContext(), "Erro: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    })
                    .build()
                PhoneAuthProvider.verifyPhoneNumber(options)
            } else {
                showMfaLoading(false)
                val exception = task.exception
                if (exception is FirebaseAuthRecentLoginRequiredException) {
                    showReauthDialog { startMfaEnrollment(phoneNumber) }
                } else {
                    Toast.makeText(requireContext(), getString(R.string.mfa_session_error), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun finalizeMfaEnrollment(code: String, phoneNumber: String) {
        showMfaLoading(true)
        val credential = PhoneAuthProvider.getCredential(verificationId!!, code)
        val assertion = PhoneMultiFactorGenerator.getAssertion(credential)

        auth.currentUser?.multiFactor?.enroll(assertion, "Telemóvel Pessoal")
            ?.addOnCompleteListener { task ->
                showMfaLoading(false)
                if (task.isSuccessful) {
                    Toast.makeText(requireContext(), getString(R.string.mfa_enroll_success), Toast.LENGTH_LONG).show()
                    updatePhoneNumberInFirestore(phoneNumber)
                    binding.mfaVerifyContainer.visibility = View.GONE
                    updateMfaUi()
                } else {
                    val exception = task.exception
                    if (exception is FirebaseAuthRecentLoginRequiredException) {
                        showReauthDialog { finalizeMfaEnrollment(code, phoneNumber) }
                    } else {
                        Toast.makeText(requireContext(), getString(R.string.mfa_enroll_fail, exception?.message), Toast.LENGTH_LONG).show()
                    }
                }
            }
    }

    /**
     * Reautentica o utilizador. Para utilizadores Google usa o Google Sign-In;
     * para utilizadores email/password mostra um diálogo de password.
     */
    private fun showReauthDialog(onSuccess: () -> Unit) {
        val user = auth.currentUser ?: return
        val isGoogleUser = user.providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID }

        if (isGoogleUser) {
            pendingReauthCallback = onSuccess
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken("559445056454-5ucdor4jsgcbo41hsk5040magfskkdj4.apps.googleusercontent.com")
                .requestEmail()
                .build()
            val googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso)
            googleReauthLauncher.launch(googleSignInClient.signInIntent)
        } else {
            val input = EditText(requireContext()).apply {
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                hint = getString(R.string.reauth_password_hint)
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.reauth_title))
                .setMessage(getString(R.string.reauth_desc))
                .setView(input)
                .setPositiveButton(getString(R.string.btn_confirm)) { _, _ ->
                    val password = input.text.toString()
                    if (password.isNotEmpty()) {
                        reauthenticateWithPassword(password, onSuccess)
                    }
                }
                .setNegativeButton(getString(R.string.close), null)
                .show()
        }
    }

    private fun reauthenticateWithPassword(password: String, onSuccess: () -> Unit) {
        val user = auth.currentUser ?: return
        val email = user.email ?: return
        val credential = EmailAuthProvider.getCredential(email, password)

        showMfaLoading(true)
        user.reauthenticate(credential).addOnCompleteListener { task ->
            showMfaLoading(false)
            if (task.isSuccessful) {
                Toast.makeText(requireContext(), getString(R.string.reauth_success), Toast.LENGTH_SHORT).show()
                onSuccess()
            } else {
                Toast.makeText(requireContext(), getString(R.string.reauth_fail), Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Guarda o número de telemóvel no documento do utilizador no Firestore
     */
    private fun updatePhoneNumberInFirestore(phoneNumber: String) {
        val userId = auth.currentUser?.uid ?: return
        firestore.collection("users").document(userId)
            .update("phoneNumber", phoneNumber)
            .addOnFailureListener {
                // Silencioso ou logar erro
            }
    }

    private fun showMfaLoading(isLoading: Boolean) {
        binding.mfaProgressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnEnrollMfa.isEnabled = !isLoading
        binding.btnVerifyCode.isEnabled = !isLoading
    }

    private fun uploadProfileImage(uri: Uri) {
        val user = auth.currentUser ?: return
        val storageRef = storage.reference.child("profile_images/${user.uid}")

        storageRef.putFile(uri).addOnSuccessListener {
            storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                val photoUrl = downloadUri.toString()
                val profileUpdates = UserProfileChangeRequest.Builder().setPhotoUri(downloadUri).build()
                user.updateProfile(profileUpdates).addOnCompleteListener { authTask ->
                    if (authTask.isSuccessful) {
                        firestore.collection("users").document(user.uid)
                            .update("photoUrl", photoUrl)
                            .addOnSuccessListener {
                                Toast.makeText(context, "Imagem de perfil atualizada.", Toast.LENGTH_SHORT).show()
                                loadUserData()
                            }
                    }
                }
            }
        }.addOnFailureListener {
            Toast.makeText(context, "Falha no upload da imagem.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
