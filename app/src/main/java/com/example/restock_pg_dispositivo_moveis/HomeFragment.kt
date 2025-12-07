package com.example.restock_pg_dispositivo_moveis

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.example.restock_pg_dispositivo_moveis.databinding.FragmentHomeBinding
import com.example.restock_pg_dispositivo_moveis.ui.inventario.InventarioViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val inventoryViewModel: InventarioViewModel by activityViewModels()
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUserData()
        setupInventorySummary()
        setupClickListeners()
    }

    private fun setupUserData() {
        val user = auth.currentUser
        if (user != null) {
            val userName = user.displayName?.split(" ")?.get(0) ?: "Utilizador"
            binding.greetingTextView.text = "Olá, $userName 👋"

            Glide.with(this)
                 .load(user.photoUrl)
                 .placeholder(R.drawable.ic_avatar)
                 .circleCrop()
                 .into(binding.profileImageView)
        } 
    }

    private fun setupInventorySummary() {
        viewLifecycleOwner.lifecycleScope.launch {
            inventoryViewModel.produtos.collect { productList ->
                val totalItems = productList.sumOf { it.quantidade }.toInt()
                binding.inventorySummaryTextView.text = "$totalItems itens no inventário"

                val currentTime = System.currentTimeMillis()
                val sevenDaysInMillis = TimeUnit.DAYS.toMillis(7)
                val expiringSoonCount = productList.count { product ->
                    product.validade != null && product.validade >= currentTime && (product.validade - currentTime) <= sevenDaysInMillis
                }
                binding.expiringSummaryTextView.text = "$expiringSoonCount produtos prestes a expirar"
            }
        }
    }

    private fun setupClickListeners() {
        binding.profileImageView.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_account)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
