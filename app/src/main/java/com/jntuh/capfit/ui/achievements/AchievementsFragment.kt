package com.jntuh.capfit.ui.achievements

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.jntuh.capfit.adapter.AchievementAdapter
import com.jntuh.capfit.databinding.FragmentAchievementsBinding
import com.jntuh.capfit.viewmodel.AchievementViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AchievementsFragment : Fragment() {

    private lateinit var binding: FragmentAchievementsBinding
    private val viewModel: AchievementViewModel by viewModels()
    private lateinit var adapter: AchievementAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentAchievementsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        binding.recyclerView.layoutManager =
            GridLayoutManager(requireContext(), 2)

        adapter = AchievementAdapter(emptyList())
        binding.recyclerView.adapter = adapter

        lifecycleScope.launch {
            viewModel.achievements.collect { list ->
                adapter.updateList(list)

                val unlocked = list.count { it.isUnlocked }
                binding.progressCount.text = "$unlocked of ${list.size} unlocked"

                val progressPercent =
                    if (list.isEmpty()) 0 else (unlocked * 100) / list.size

                binding.progressBar.progress = progressPercent

                Log.d("ACH_FRAG", "Achievements received = ${list.size}")
            }
        }

        lifecycleScope.launch {
            viewModel.newlyUnlocked.collect {
                if (it.isNotEmpty()) showUnlockedDialog(it.first().title)
            }
        }
    }

    private fun showUnlockedDialog(name: String) {
        val dialog = AchievementUnlockedDialog(name)
        dialog.show(parentFragmentManager, "ach_dialog")
    }
}