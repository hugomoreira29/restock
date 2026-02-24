package com.example.restock

// HUGO MOREIRA - a22402246

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth

/**
 * Activity responsável pelo ecrã de Login.
 * Permite que um utilizador existente entre na aplicação com o seu email e password.
 */
class LoginActivity : AppCompatActivity() {

    // Instância do Firebase Auth para gerir a autenticação.
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Inicializa o Firebase Auth.
        auth = FirebaseAuth.getInstance()

        // Obtém referências para os elementos da UI a partir do layout.
        val emailInput = findViewById<EditText>(R.id.emailInput)
        val passwordInput = findViewById<EditText>(R.id.passwordInput)
        val loginBtn = findViewById<Button>(R.id.loginBtn)
        val goRegisterBtn = findViewById<Button>(R.id.goRegisterBtn)

        // Configura o listener de clique para o botão de login.
        loginBtn.setOnClickListener {
            val email = emailInput.text.toString()
            val password = passwordInput.text.toString()

            // Validação simples para garantir que os campos não estão vazios.
            if (email.isNotEmpty() && password.isNotEmpty()) {
                // Tenta fazer o login com o email e password fornecidos.
                auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this) { task ->
                        if (task.isSuccessful) {
                            val user = auth.currentUser
                            
                            // Verifica se o email foi validado (opcional, mas recomendado para o seu caso).
                            if (user != null && user.isEmailVerified) {
                                // Se o login for bem-sucedido e o email verificado.
                                Toast.makeText(this, getString(R.string.login_success), Toast.LENGTH_SHORT).show()
                                navigateToHome()
                            } else {
                                // Se o email não estiver verificado, avisa o utilizador.
                                // Nota: Para testar mais facilmente, pode comentar este bloco 'else' e chamar navigateToHome() diretamente.
                                Toast.makeText(this, "Por favor verifique o seu email antes de entrar.", Toast.LENGTH_LONG).show()
                                user?.sendEmailVerification() // Reenvia o email se necessário.
                                auth.signOut() // Impede o login até verificar.
                            }

                        } else {
                            // Se o login falhar, mostra uma mensagem de erro ao utilizador.
                            Toast.makeText(baseContext, getString(R.string.login_fail),
                                Toast.LENGTH_SHORT).show()
                        }
                    }
            } else {
                // Se algum campo estiver vazio, avisa o utilizador.
                Toast.makeText(this, getString(R.string.fill_all_fields), Toast.LENGTH_SHORT).show()
            }
        }

        // Configura o listener para o botão que leva ao ecrã de registo.
        goRegisterBtn.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }
    }

    private fun navigateToHome() {
        val intent = Intent(this, HomeActivity::class.java)
        startActivity(intent)
        finish() // Finaliza a LoginActivity para não se poder voltar a ela.
    }
}
