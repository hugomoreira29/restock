package com.example.restock_pg_dispositivo_moveis.ui.family

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.restock_pg_dispositivo_moveis.databinding.FragmentFamilyBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class FamilyFragment : Fragment() {

    private var _binding: FragmentFamilyBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FamilyViewModel by viewModels()
    private lateinit var memberAdapter: FamilyMemberAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFamilyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        memberAdapter = FamilyMemberAdapter()
        binding.familyMembersRecyclerView.apply {
            adapter = memberAdapter
            layoutManager = LinearLayoutManager(context)
        }
    }

    private fun setupClickListeners() {
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        binding.addMemberButton.setOnClickListener {
            viewModel.generateInviteCode { code ->
                showInviteCodeDialog(code)
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.family.collect { family ->
                family?.let { binding.familyNameTextView.text = it.name }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.members.collect { members ->
                memberAdapter.submitList(members)
            }
        }
    }

    private fun showInviteCodeDialog(code: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Código de Convite")
            .setMessage("Partilhe este código com quem quer convidar para a sua família:\n\n$code")
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
