package com.example.restock

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.restock.databinding.ActivityRegisterBinding
import com.example.restock.model.Family
import com.example.restock.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.WriteBatch

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
                                val profileUpdates = UserProfileChangeRequest.Builder().setDisplayName(name).build()
                                firebaseUser.updateProfile(profileUpdates)

                                firebaseUser.sendEmailVerification()
                                    .addOnSuccessListener {
                                        Toast.makeText(baseContext, "Email de verificação enviado para $email", Toast.LENGTH_LONG).show()
                                    }
                                    .addOnFailureListener { e ->
                                        Toast.makeText(baseContext, "Erro ao enviar email de verificação: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }

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
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }
    }

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

                val userRef = firestore.collection("users").document(userId)
                val user = User(uid = userId, name = name, email = email, familyId = familyId)
                batch.set(userRef, user)

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

    private fun createNewFamily(userId: String, name: String, email: String) {
        val batch = firestore.batch()

        val newFamilyRef = firestore.collection("families").document()

        val newFamily = Family(
            id = newFamilyRef.id,
            name = "Família $name",
            members = listOf(userId),
            roles = mapOf(userId to "Admin")
        )
        batch.set(newFamilyRef, newFamily)

        val userRef = firestore.collection("users").document(userId)
        val user = User(uid = userId, name = name, email = email, familyId = newFamily.id)
        batch.set(userRef, user)

        commitBatch(batch)
    }

    private fun commitBatch(batch: WriteBatch) {
        batch.commit().addOnCompleteListener { batchTask ->
            hideLoading()
            if (batchTask.isSuccessful) {
                Toast.makeText(this, getString(R.string.register_success), Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, HomeActivity::class.java))
                finish()
            } else {
                deleteAccountAndShowError(getString(R.string.error_saving_data, batchTask.exception?.message))
            }
        }
    }

    private fun deleteAccountAndShowError(errorMessage: String) {
        val user = auth.currentUser
        user?.delete()?.addOnCompleteListener { 
            hideLoading()
            Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
        }
    }

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
