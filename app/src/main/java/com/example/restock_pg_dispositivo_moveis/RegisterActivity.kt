package com.example.restock_pg_dispositivo_moveis

// HUGO MOREIRA - a22402246

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

/**
 * Activity responsável pelo registo de novos utilizadores.
 * Lida com a criação da conta no Firebase Authentication e com a criação ou adesão a uma família no Firestore.
 */
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
        val confirmEmailInput = findViewById<EditText>(R.id.confirmEmailInput)
        val passwordInput = findViewById<EditText>(R.id.passwordInput)
        val inviteCodeInput = findViewById<EditText>(R.id.inviteCodeInput)
        val registerBtn = findViewById<Button>(R.id.registerBtn)

        // Listener para o botão de registo.
        registerBtn.setOnClickListener {
            val name = nameInput.text.toString().trim()
            val email = emailInput.text.toString().trim()
            val confirmEmail = confirmEmailInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()
            val inviteCode = inviteCodeInput.text.toString().trim()

            if (name.isNotEmpty() && email.isNotEmpty() && confirmEmail.isNotEmpty() && password.isNotEmpty()) {
                
                if (email == confirmEmail) {
                    // 1. Cria o utilizador no Firebase Authentication.
                    auth.createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener(this) { task ->
                            if (task.isSuccessful) {
                                val firebaseUser = auth.currentUser!!
                                val profileUpdates = UserProfileChangeRequest.Builder().setDisplayName(name).build()
                                firebaseUser.updateProfile(profileUpdates)

                                // ENVIA O EMAIL DE VERIFICAÇÃO DO FIREBASE
                                firebaseUser.sendEmailVerification()
                                    .addOnSuccessListener {
                                        Toast.makeText(baseContext, "Email de verificação enviado para $email", Toast.LENGTH_LONG).show()
                                    }
                                    .addOnFailureListener { e ->
                                        // Apenas loga o erro, não impede o registo pois pode ser reenviado.
                                        Toast.makeText(baseContext, "Erro ao enviar email de verificação: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }

                                // 2. Decide se cria uma nova família ou se junta a uma existente.
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
                    Toast.makeText(this, "Os emails não coincidem.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, getString(R.string.fill_all_fields), Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Adiciona o utilizador a uma família existente, usando um código de convite.
     */
    private fun joinFamily(inviteCode: String, userId: String, name: String, email: String) {
        // Procura a família pelo código de convite.
        firestore.collection("families").whereEqualTo("inviteCode", inviteCode).get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    // SE FALHAR (Código Inválido): Apaga a conta criada e mostra erro.
                    deleteAccountAndShowError(getString(R.string.invalid_invite_code))
                    return@addOnSuccessListener
                }
                
                val familyDoc = documents.first()
                val familyId = familyDoc.id

                val batch = firestore.batch()

                // Cria o documento do utilizador no Firestore.
                val userRef = firestore.collection("users").document(userId)
                val user = User(uid = userId, name = name, email = email, familyId = familyId)
                batch.set(userRef, user)

                // Adiciona o novo membro à família e define-o como "Membro".
                val familyRef = firestore.collection("families").document(familyId)
                batch.update(familyRef, "members", FieldValue.arrayUnion(userId))
                batch.update(familyRef, "roles.$userId", "Membro")
                batch.update(familyRef, "inviteCode", null) // Invalida o código após o uso.

                commitBatch(batch)
            }
            .addOnFailureListener {
                 // SE FALHAR (Erro Database): Apaga a conta criada e mostra erro.
                 deleteAccountAndShowError("Erro ao procurar família. Tente novamente.")
            }
    }

    /**
     * Cria uma nova família para o utilizador, definindo-o como "Admin".
     */
    private fun createNewFamily(userId: String, name: String, email: String) {
        val batch = firestore.batch()

        val newFamilyRef = firestore.collection("families").document()
        
        val newFamily = Family(
            id = newFamilyRef.id, 
            name = "Família $name", 
            members = listOf(userId),
            roles = mapOf(userId to "Admin") // O criador é sempre o Admin.
        )
        batch.set(newFamilyRef, newFamily)

        val userRef = firestore.collection("users").document(userId)
        val user = User(uid = userId, name = name, email = email, familyId = newFamily.id)
        batch.set(userRef, user)

        commitBatch(batch)
    }

    /**
     * Executa as operações em batch no Firestore e navega para a HomeActivity.
     */
    private fun commitBatch(batch: WriteBatch) {
        batch.commit().addOnCompleteListener { batchTask ->
            if (batchTask.isSuccessful) {
                Toast.makeText(this, getString(R.string.register_success), Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, HomeActivity::class.java))
                finish()
            } else {
                // SE FALHAR (Erro Database): Apaga a conta criada e mostra erro.
                deleteAccountAndShowError(getString(R.string.error_saving_data, batchTask.exception?.message))
            }
        }
    }

    /**
     * Helper para apagar o utilizador atual em caso de erro no processo de registo.
     */
    private fun deleteAccountAndShowError(errorMessage: String) {
        val user = auth.currentUser
        user?.delete()?.addOnCompleteListener { 
            Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
        }
    }
}
