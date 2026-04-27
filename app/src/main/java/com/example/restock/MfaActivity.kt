package com.example.restock

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.restock.databinding.ActivityMfaBinding
import com.google.firebase.FirebaseException
import com.google.firebase.auth.*
import com.google.firebase.auth.PhoneAuthProvider.ForceResendingToken
import com.google.firebase.auth.PhoneAuthProvider.OnVerificationStateChangedCallbacks
import java.util.concurrent.TimeUnit

class MfaActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMfaBinding
    private var verificationId: String? = null
    private var resendingToken: ForceResendingToken? = null
    
    companion object {
        var multiFactorResolver: MultiFactorResolver? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMfaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val resolver = multiFactorResolver
        
        // Correção para ler o Parcelable de forma segura em diferentes versões do Android
        val hint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("MFA_HINT", PhoneMultiFactorInfo::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<PhoneMultiFactorInfo>("MFA_HINT")
        }

        if (resolver == null || hint == null) {
            Toast.makeText(this, getString(R.string.mfa_init_error), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.mfaDescription.text = getString(R.string.mfa_description, hint.phoneNumber)

        sendVerificationCode(resolver, hint)

        binding.verifyBtn.setOnClickListener {
            val code = binding.otpInput.text.toString().trim()
            if (code.length == 6 && verificationId != null) {
                verifyCode(code, resolver)
            } else {
                Toast.makeText(this, getString(R.string.mfa_enter_code), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendVerificationCode(resolver: MultiFactorResolver, hint: PhoneMultiFactorInfo) {
        showLoading()
        val options = PhoneAuthOptions.newBuilder()
            .setMultiFactorHint(hint)
            .setMultiFactorSession(resolver.session)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(object : OnVerificationStateChangedCallbacks() {
                override fun onCodeSent(vId: String, token: ForceResendingToken) {
                    hideLoading()
                    verificationId = vId
                    resendingToken = token
                    Toast.makeText(this@MfaActivity, getString(R.string.mfa_code_sent), Toast.LENGTH_SHORT).show()
                }

                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    hideLoading()
                    resolveSignIn(PhoneMultiFactorGenerator.getAssertion(credential), resolver)
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    hideLoading()
                    Toast.makeText(this@MfaActivity, getString(R.string.error_prefix, e.message), Toast.LENGTH_LONG).show()
                }
            })
            .build()
        FirebaseAuth.getInstance().firebaseAuthSettings
            .setAppVerificationDisabledForTesting(true)
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    private fun verifyCode(code: String, resolver: MultiFactorResolver) {
        showLoading()
        val credential = PhoneAuthProvider.getCredential(verificationId!!, code)
        val assertion = PhoneMultiFactorGenerator.getAssertion(credential)
        resolveSignIn(assertion, resolver)
    }

    private fun resolveSignIn(assertion: MultiFactorAssertion, resolver: MultiFactorResolver) {
        resolver.resolveSignIn(assertion)
            .addOnCompleteListener { task ->
                hideLoading()
                if (task.isSuccessful) {
                    multiFactorResolver = null
                    navigateToHome()
                } else {
                    Toast.makeText(this, getString(R.string.invalid_otp), Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
        binding.verifyBtn.isEnabled = false
        binding.otpInputLayout.isEnabled = false
    }

    private fun hideLoading() {
        binding.progressBar.visibility = View.GONE
        binding.verifyBtn.isEnabled = true
        binding.otpInputLayout.isEnabled = true
    }

    private fun navigateToHome() {
        val intent = Intent(this, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}
