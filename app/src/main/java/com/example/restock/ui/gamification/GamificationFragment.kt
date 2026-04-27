package com.example.restock.ui.gamification

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.restock.R
import com.example.restock.databinding.FragmentGamificationBinding
import kotlinx.coroutines.launch

/** HUGO MOREIRA - a22402246 */
class GamificationFragment : Fragment() {

    private var _binding: FragmentGamificationBinding? = null
    private val binding get() = _binding!!

    private val viewModel: GamificationViewModel by viewModels()
    private lateinit var badgeAdapter: BadgeAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGamificationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnBack.setOnClickListener { findNavController().navigateUp() }
        setupRecyclerView()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        badgeAdapter = BadgeAdapter()
        binding.badgesRecyclerView.apply {
            adapter = badgeAdapter
            layoutManager = LinearLayoutManager(context)
            isNestedScrollingEnabled = false
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.user.collect { user ->
                user?.let {
                    binding.levelTextView.text = getString(R.string.your_level, it.level)
                    binding.pointsTextView.text = getString(R.string.points_total, it.points)

                    val progress = it.points % 100
                    val pointsToNext = 100 - progress
                    binding.levelProgressBar.progress = progress
                    binding.progressPercentTextView.text = "$progress%"
                    binding.pointsToNextLevelTextView.text =
                        getString(R.string.points_to_next_level, pointsToNext)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.badges.collect { badges ->
                badgeAdapter.submitList(badges)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
