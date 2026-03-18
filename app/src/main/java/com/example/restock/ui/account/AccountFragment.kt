package com.example.restock.ui.account

// Android - para aceder a funcionalidades do sistema e navegação
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast

// AndroidX - para registar o lançador de seleção de imagens e navegação entre fragmentos
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController

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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

/** HUGO MOREIRA - a22402246
 * AccountFragment.kt
 * Responsável por gerir a conta do utilizador.
*/
class AccountFragment : Fragment() {

    // Binding para aceder às vistas do fragmento de forma segura
    private var _binding: FragmentAccountBinding? = null
    private val binding get() = _binding!!

    // Instâncias do Firebase inicializadas apenas quando necessário (lazy)
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val storage: FirebaseStorage by lazy { FirebaseStorage.getInstance() }

    // Lançador para abrir a galeria e selecionar uma imagem de perfil
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            result ->
        // Se o utilizador selecionou uma imagem, inicia o upload para o Firebase Storage
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                uploadProfileImage(uri)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Infla o layout do fragmento e inicializa o binding
        _binding = FragmentAccountBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Carrega os dados do utilizador e configura os listeners ao criar a vista
        loadUserData()
        setupClickListeners()
    }

    /**
     * Carrega os dados do utilizador autenticado e apresenta-os no ecrã.
     * Verifica também se o utilizador entrou com o Google para ocultar
     * as opções de palavra-passe caso não sejam aplicáveis.
     */
    private fun loadUserData() {
        val user = auth.currentUser
        if (user == null) { return }

        // Preenche o nome e o email com os dados do Firebase Authentication
        binding.nameTextView.text = user.displayName
        binding.emailTextView.text = user.email

        // Carrega a fotografia de perfil usando o Glide, com um avatar por defeito
        Glide.with(this)
            .load(user.photoUrl)
            .placeholder(R.drawable.ic_avatar)
            .circleCrop()
            .into(binding.profileImageView)

        // Verifica se o utilizador entrou com o Google
        val isGoogleUser = user.providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID }

        // Oculta as opções de palavra-passe se o utilizador usar o Google Sign-In,
        // pois a palavra-passe é gerida pelo Google e não pela aplicação
        if (isGoogleUser) {
            binding.passwordLabel.visibility = View.GONE
            binding.passwordCard.visibility = View.GONE
        } else {
            binding.passwordLabel.visibility = View.VISIBLE
            binding.passwordCard.visibility = View.VISIBLE
        }
    }

    /**
     * Configura todos os listeners de clique do ecrã de conta.
     * Trata da navegação, edição de perfil, alteração de palavra-passe
     * e término de sessão.
     */
    private fun setupClickListeners() {

        // Navega para o ecrã anterior ao clicar na seta de retrocesso da toolbar
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        // Abre a galeria do dispositivo para selecionar uma nova fotografia de perfil
        binding.editProfileImageButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            pickImageLauncher.launch(intent)
        }

        // Abre o bottom sheet para editar o nome do utilizador
        binding.nameTextView.setOnClickListener {
            val editNameSheet = EditNameBottomSheet()
            // Atualiza os dados apresentados no ecrã após o nome ser alterado
            editNameSheet.onNameUpdated = {
                loadUserData()
            }
            editNameSheet.show(parentFragmentManager, EditNameBottomSheet.TAG)
        }

        // Abre o bottom sheet para alterar a palavra-passe do utilizador
        binding.passwordTextView.setOnClickListener {
            EditPasswordBottomSheet().show(parentFragmentManager, EditPasswordBottomSheet.TAG)
        }

        // Termina a sessão do utilizador e redireciona para o ecrã de login
        binding.logoutButton.setOnClickListener {
            auth.signOut()
            val intent = Intent(requireActivity(), LoginActivity::class.java)
            // Limpa a pilha de atividades para que o utilizador não consiga voltar atrás
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            requireActivity().finish()
        }
    }

    /**
     * Faz o upload da fotografia de perfil selecionada para o Firebase Storage.
     * Após o upload, atualiza o perfil no Firebase Authentication e no Firestore
     * para manter os dados consistentes em toda a aplicação.
     */
    private fun uploadProfileImage(uri: Uri) {
        val user = auth.currentUser ?: return

        // Referência ao ficheiro no Firebase Storage, organizado pela pasta do utilizador
        val storageRef = storage.reference.child("profile_images/${user.uid}")

        storageRef.putFile(uri).addOnSuccessListener {
            // Após o upload, obtém o URL público da imagem
            storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                val photoUrl = downloadUri.toString()

                // Atualiza a fotografia de perfil no Firebase Authentication
                val profileUpdates = UserProfileChangeRequest.Builder().setPhotoUri(downloadUri).build()
                user.updateProfile(profileUpdates).addOnCompleteListener { authTask ->
                    if (authTask.isSuccessful) {
                        // Atualiza também o campo photoUrl no documento do utilizador no Firestore
                        // para manter a consistência dos dados em toda a aplicação
                        firestore.collection("users").document(user.uid)
                            .update("photoUrl", photoUrl)
                            .addOnSuccessListener {
                                Toast.makeText(context, "Imagem de perfil atualizada.", Toast.LENGTH_SHORT).show()
                                // Recarrega os dados do utilizador para refletir a nova imagem
                                loadUserData()
                            }
                    }
                }
            }
        }.addOnFailureListener {
            // Informa o utilizador caso o upload falhe
            Toast.makeText(context, "Falha no upload da imagem.", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Limpa o binding quando a vista é destruída para evitar fugas de memória.
     */
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
