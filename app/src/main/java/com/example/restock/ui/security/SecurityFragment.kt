package com.example.restock.ui.security

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.restock.R
import com.example.restock.databinding.FragmentSecurityBinding
import com.google.firebase.FirebaseException
import com.google.firebase.auth.*
import java.util.concurrent.TimeUnit

class SecurityFragment : Fragment() {

    private var _binding: FragmentSecurityBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth
    
    private var verificationId: String? = null
    private var resendingToken: PhoneAuthProvider.ForceResendingToken? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSecurityBinding.inflate(inflater, container, false)
        auth = FirebaseAuth.getInstance()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        updateUi()

        binding.btnEnrollMfa.setOnClickListener {
            val phoneNumber = binding.phoneInput.text.toString().trim()
            if (phoneNumber.isNotEmpty()) {
                startMfaEnrollment(phoneNumber)
            } else {
                Toast.makeText(requireContext(), getString(R.string.invalid_phone_error), Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnVerifyCode.setOnClickListener {
            val code = binding.smsCodeInput.text.toString().trim()
            if (code.length == 6 && verificationId != null) {
                finalizeMfaEnrollment(code)
            }
        }
    }

    private fun updateUi() {
        val user = auth.currentUser
        val mfaFactors = user?.multiFactor?.enrolledFactors
        
        if (mfaFactors != null && mfaFactors.isNotEmpty()) {
            binding.mfaStatusText.text = getString(R.string.mfa_status_enabled)
            binding.mfaStatusText.setTextColor(android.graphics.Color.parseColor("#388E3C"))
            binding.enrollSection.visibility = View.GONE
        } else {
            binding.mfaStatusText.text = getString(R.string.mfa_status_disabled)
            binding.mfaStatusText.setTextColor(android.graphics.Color.parseColor("#D32F2F"))
            binding.enrollSection.visibility = View.VISIBLE
        }
    }

    private fun startMfaEnrollment(phoneNumber: String) {
        showLoading(true)
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
                            showLoading(false)
                            verificationId = vId
                            resendingToken = token
                            binding.enrollSection.visibility = View.GONE
                            binding.verifySection.visibility = View.VISIBLE
                            Toast.makeText(requireContext(), getString(R.string.sms_sent_success, phoneNumber), Toast.LENGTH_SHORT).show()
                        }

                        override fun onVerificationCompleted(p0: PhoneAuthCredential) {
                            // Instant verification
                        }

                        override fun onVerificationFailed(e: FirebaseException) {
                            showLoading(false)
                            Toast.makeText(requireContext(), getString(R.string.error_prefix, e.message), Toast.LENGTH_LONG).show()
                        }
                    })
                    .build()
                PhoneAuthProvider.verifyPhoneNumber(options)
            } else {
                showLoading(false)
                Toast.makeText(requireContext(), getString(R.string.mfa_session_error), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun finalizeMfaEnrollment(code: String) {
        showLoading(true)
        val credential = PhoneAuthProvider.getCredential(verificationId!!, code)
        val assertion = PhoneMultiFactorGenerator.getAssertion(credential)

        auth.currentUser?.multiFactor?.enroll(assertion, "Telemóvel Pessoal")
            ?.addOnCompleteListener { task ->
                showLoading(false)
                if (task.isSuccessful) {
                    Toast.makeText(requireContext(), getString(R.string.mfa_enroll_success), Toast.LENGTH_LONG).show()
                    binding.verifySection.visibility = View.GONE
                    updateUi()
                } else {
                    Toast.makeText(requireContext(), getString(R.string.mfa_enroll_fail, task.exception?.message), Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnEnrollMfa.isEnabled = !isLoading
        binding.btnVerifyCode.isEnabled = !isLoading
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
