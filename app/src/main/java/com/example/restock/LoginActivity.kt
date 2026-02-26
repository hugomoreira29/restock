package com.example.restock

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.restock.databinding.ActivityLoginBinding
import com.example.restock.model.Family
import com.example.restock.model.User
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.WriteBatch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var googleSignInClient: GoogleSignInClient

    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            if (account != null) {
                firebaseAuthWithGoogle(account.idToken!!)
            }
        } catch (e: ApiException) {
            binding.progressBar.visibility = View.GONE
            Toast.makeText(this, "Google Sign-In failed: ${e.statusCode}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("559445056454-5ucdor4jsgcbo41hsk5040magfskkdj4.apps.googleusercontent.com")
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        binding.loginBtn.setOnClickListener {
            val email = binding.emailInput.text.toString()
            val password = binding.passwordInput.text.toString()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                binding.progressBar.visibility = View.VISIBLE
                auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this) { task ->
                        binding.progressBar.visibility = View.GONE
                        if (task.isSuccessful) {
                            val user = auth.currentUser
                            if (user != null && user.isEmailVerified) {
                                Toast.makeText(this, getString(R.string.login_success), Toast.LENGTH_SHORT).show()
                                navigateToHome()
                            } else {
                                Toast.makeText(this, "Por favor verifique o seu email antes de entrar.", Toast.LENGTH_LONG).show()
                                user?.sendEmailVerification()
                                auth.signOut()
                            }
                        } else {
                            Toast.makeText(baseContext, getString(R.string.login_fail), Toast.LENGTH_SHORT).show()
                        }
                    }
            } else {
                Toast.makeText(this, getString(R.string.fill_all_fields), Toast.LENGTH_SHORT).show()
            }
        }

        binding.goRegisterBtn.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }

        binding.googleSignInButton.setOnClickListener {
            binding.progressBar.visibility = View.VISIBLE
            val signInIntent = googleSignInClient.signInIntent
            googleSignInLauncher.launch(signInIntent)
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val firebaseUser = auth.currentUser!!
                    val isNewUser = task.result.additionalUserInfo?.isNewUser ?: false

                    if (isNewUser) {
                        createNewFamilyForGoogleUser(firebaseUser)
                    } else {
                        binding.progressBar.visibility = View.GONE
                        navigateToHome()
                    }
                } else {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(baseContext, getString(R.string.login_fail), Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun createNewFamilyForGoogleUser(firebaseUser: FirebaseUser) {
        val batch = firestore.batch()
        val newFamilyRef = firestore.collection("families").document()

        val newFamily = Family(
            id = newFamilyRef.id,
            name = "Família ${firebaseUser.displayName}",
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

    private fun commitBatch(batch: WriteBatch) {
        batch.commit().addOnCompleteListener { batchTask ->
            binding.progressBar.visibility = View.GONE
            if (batchTask.isSuccessful) {
                Toast.makeText(this, getString(R.string.register_success), Toast.LENGTH_SHORT).show()
                navigateToHome()
            } else {
                auth.currentUser?.delete()
                Toast.makeText(baseContext, getString(R.string.error_saving_data, batchTask.exception?.message), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun navigateToHome() {
        val intent = Intent(this, HomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
