package com.example.restock.ui.account

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
import com.example.restock.ui.bottomsheets.EditNameBottomSheet
import com.example.restock.ui.bottomsheets.EditPasswordBottomSheet
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class AccountFragment : Fragment() {

    private var _binding: FragmentAccountBinding? = null
    private val binding get() = _binding!!

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val storage: FirebaseStorage by lazy { FirebaseStorage.getInstance() }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        result ->
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
        _binding = FragmentAccountBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadUserData()
        setupClickListeners()
    }

    private fun loadUserData() {
        val user = auth.currentUser
        if (user == null) { return }

        // Load data directly from FirebaseAuth
        binding.nameTextView.text = user.displayName
        binding.emailTextView.text = user.email

        Glide.with(this)
            .load(user.photoUrl)
            .placeholder(R.drawable.ic_avatar)
            .circleCrop()
            .into(binding.profileImageView)

        // Check provider and hide password fields if Google Sign-In
        val isGoogleUser = user.providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID }
        if (isGoogleUser) {
            binding.passwordLabel.visibility = View.GONE
            binding.passwordCard.visibility = View.GONE
        } else {
            binding.passwordLabel.visibility = View.VISIBLE
            binding.passwordCard.visibility = View.VISIBLE
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
            editNameSheet.onNameUpdated = { 
                loadUserData()
            }
            editNameSheet.show(parentFragmentManager, EditNameBottomSheet.TAG)
        }

        binding.passwordTextView.setOnClickListener {
            EditPasswordBottomSheet().show(parentFragmentManager, EditPasswordBottomSheet.TAG)
        }

        binding.logoutButton.setOnClickListener {
            auth.signOut()
            val intent = Intent(requireActivity(), LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            requireActivity().finish()
        }
    }

    private fun uploadProfileImage(uri: Uri) {
        val user = auth.currentUser ?: return
        val storageRef = storage.reference.child("profile_images/${user.uid}")

        storageRef.putFile(uri).addOnSuccessListener { 
            storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                val photoUrl = downloadUri.toString()
                val profileUpdates = UserProfileChangeRequest.Builder().setPhotoUri(downloadUri).build()
                user.updateProfile(profileUpdates).addOnCompleteListener { authTask ->
                    if(authTask.isSuccessful) {
                        // Also update the photoUrl in the user's document in Firestore for consistency
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
