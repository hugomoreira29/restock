package com.example.restock_pg_dispositivo_moveis

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.restock_pg_dispositivo_moveis.model.Family
import com.example.restock_pg_dispositivo_moveis.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.WriteBatch

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        val nameInput = findViewById<EditText>(R.id.nameInput)
        val emailInput = findViewById<EditText>(R.id.emailInput)
        val passwordInput = findViewById<EditText>(R.id.passwordInput)
        val inviteCodeInput = findViewById<EditText>(R.id.inviteCodeInput)
        val registerBtn = findViewById<Button>(R.id.registerBtn)

        registerBtn.setOnClickListener {
            val name = nameInput.text.toString().trim()
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()
            val inviteCode = inviteCodeInput.text.toString().trim()

            if (name.isNotEmpty() && email.isNotEmpty() && password.isNotEmpty()) {
                auth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this) { task ->
                        if (task.isSuccessful) {
                            val firebaseUser = auth.currentUser!!
                            val profileUpdates = UserProfileChangeRequest.Builder().setDisplayName(name).build()
                            firebaseUser.updateProfile(profileUpdates)

                            if (inviteCode.isNotEmpty()) {
                                joinFamily(inviteCode, firebaseUser.uid, name, email)
                            } else {
                                createNewFamily(firebaseUser.uid, name, email)
                            }
                        } else {
                            Toast.makeText(baseContext, getString(R.string.register_fail, task.exception?.message), Toast.LENGTH_SHORT).show()
                        }
                    }
            } else {
                Toast.makeText(this, getString(R.string.fill_all_fields), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun joinFamily(inviteCode: String, userId: String, name: String, email: String) {
        firestore.collection("families").whereEqualTo("inviteCode", inviteCode).get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    Toast.makeText(this, getString(R.string.invalid_invite_code), Toast.LENGTH_SHORT).show()
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
                batch.update(familyRef, "inviteCode", null)

                commitBatch(batch)
            }
            .addOnFailureListener {
                 Toast.makeText(this, "Error searching for family.", Toast.LENGTH_SHORT).show() // TODO: Add to strings
            }
    }

    private fun createNewFamily(userId: String, name: String, email: String) {
        val batch = firestore.batch()

        val newFamilyRef = firestore.collection("families").document()
        val newFamily = Family(id = newFamilyRef.id, name = "Família $name", members = listOf(userId))
        batch.set(newFamilyRef, newFamily)

        val userRef = firestore.collection("users").document(userId)
        val user = User(uid = userId, name = name, email = email, familyId = newFamily.id)
        batch.set(userRef, user)

        commitBatch(batch)
    }

    private fun commitBatch(batch: WriteBatch) {
        batch.commit().addOnCompleteListener { batchTask ->
            if (batchTask.isSuccessful) {
                Toast.makeText(this, getString(R.string.register_success), Toast.LENGTH_SHORT).show()
                val intent = Intent(this, HomeActivity::class.java)
                startActivity(intent)
                finish()
            } else {
                Toast.makeText(this, getString(R.string.error_saving_data, batchTask.exception?.message), Toast.LENGTH_LONG).show()
            }
        }
    }
}
