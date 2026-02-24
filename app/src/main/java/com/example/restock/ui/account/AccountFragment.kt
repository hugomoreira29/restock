package com.example.restock.ui.account

// HUGO MOREIRA - a22402246

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.example.restock.LoginActivity
import com.example.restock.R
import com.example.restock.databinding.FragmentAccountBinding
import com.example.restock.model.User
import com.example.restock.ui.bottomsheets.EditNameBottomSheet
import com.example.restock.ui.bottomsheets.EditPasswordBottomSheet
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

/**
 * Fragment que representa o ecrã do Perfil do Utilizador (Conta).
 * Permite ao utilizador ver os seus dados, alterar a foto de perfil, nome, password e fazer logout.
 */
class AccountFragment : Fragment() {

    // Binding para aceder aos elementos do layout de forma segura.
    private var _binding: FragmentAccountBinding? = null
    private val binding get() = _binding!!

    // Instâncias dos serviços Firebase.
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val storage: FirebaseStorage by lazy { FirebaseStorage.getInstance() }

    // ActivityResultLauncher para lidar com o resultado da seleção de imagem da galeria.
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Se uma imagem for escolhida com sucesso, faz o upload.
            result.data?.data?.let { uri ->
                uploadProfileImage(uri)
            }
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
        loadUserDataFromFirestore()
        setupClickListeners()
    }

    /**
     * Carrega os dados do perfil do utilizador a partir do seu documento no Firestore.
     */
    private fun loadUserDataFromFirestore() {
        val user = auth.currentUser
        if (user == null) { return } // Sai se não houver utilizador.

        // Acede ao documento do utilizador na coleção "users".
        firestore.collection("users").document(user.uid).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    // Converte o documento num objeto User e preenche a UI.
                    val userProfile = document.toObject(User::class.java)
                    userProfile?.let {
                        binding.nameTextView.text = it.name
                        binding.emailTextView.text = it.email

                        // Carrega a imagem de perfil com o Glide.
                        Glide.with(this)
                            .load(it.photoUrl)
                            .placeholder(R.drawable.ic_avatar)
                            .circleCrop()
                            .into(binding.profileImageView)
                    }
                } else {
                    Toast.makeText(context, "Perfil não encontrado.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Erro ao carregar perfil: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * Configura todos os listeners de clique do ecrã.
     */
    private fun setupClickListeners() {
        // Botão de "Voltar" na toolbar.
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        // Botão "Edit" para alterar a foto de perfil.
        binding.editProfileImageButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*" // Abre a galeria para escolher uma imagem.
            pickImageLauncher.launch(intent)
        }

        // Abre o BottomSheet para editar o nome.
        binding.nameTextView.setOnClickListener {
            val editNameSheet = EditNameBottomSheet()
            // Define um listener para saber quando o nome foi atualizado, para recarregar os dados.
            editNameSheet.onNameUpdated = { 
                loadUserDataFromFirestore()
            }
            editNameSheet.show(parentFragmentManager, EditNameBottomSheet.TAG)
        }

        // Abre o BottomSheet para editar a password.
        binding.passwordTextView.setOnClickListener {
            EditPasswordBottomSheet().show(parentFragmentManager, EditPasswordBottomSheet.TAG)
        }

        // Botão de Logout.
        binding.logoutButton.setOnClickListener {
            auth.signOut() // Termina a sessão do Firebase.
            // Navega para o ecrã de Login, limpando todas as activities anteriores.
            val intent = Intent(requireActivity(), LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            requireActivity().finish()
        }
    }

    /**
     * Faz o upload da nova imagem de perfil para o Firebase Storage e atualiza os dados do utilizador.
     * @param uri A URI da imagem selecionada.
     */
    private fun uploadProfileImage(uri: Uri) {
        val user = auth.currentUser ?: return
        val storageRef = storage.reference.child("profile_images/${user.uid}")

        // 1. Faz o upload do ficheiro.
        storageRef.putFile(uri).addOnSuccessListener { 
            // 2. Após o upload, obtém a URL de download da imagem.
            storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                val photoUrl = downloadUri.toString()
                // 3. Atualiza o perfil no Firebase Authentication.
                val profileUpdates = UserProfileChangeRequest.Builder().setPhotoUri(downloadUri).build()
                user.updateProfile(profileUpdates).addOnCompleteListener { authTask ->
                    if(authTask.isSuccessful) {
                        // 4. Atualiza a URL da foto no documento do utilizador no Firestore.
                        firestore.collection("users").document(user.uid)
                            .update("photoUrl", photoUrl)
                            .addOnSuccessListener {
                                Toast.makeText(context, "Imagem de perfil atualizada.", Toast.LENGTH_SHORT).show()
                                loadUserDataFromFirestore() // Recarrega os dados para mostrar a nova imagem.
                            }
                    }
                }
            }
        }.addOnFailureListener { 
            Toast.makeText(context, "Falha no upload da imagem.", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Limpa o binding para evitar memory leaks.
     */
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
