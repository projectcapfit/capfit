package com.jntuh.capfit.ui.home

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.jntuh.capfit.adapter.LeaderboardAdapter
import com.jntuh.capfit.databinding.ActivityLeaderboardBinding
import com.jntuh.capfit.viewmodel.LeaderboardViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class Leaderboard : AppCompatActivity() {

    private lateinit var binding: ActivityLeaderboardBinding
    private val viewModel: LeaderboardViewModel by viewModels()
    private lateinit var adapter: LeaderboardAdapter

    @Inject lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityLeaderboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, _ ->
            WindowInsetsCompat.CONSUMED
        }

        setupRecycler()
        setupTabs()
        setupClicks()
        observeData()

        viewModel.loadLeaderboard()
    }

    private fun setupRecycler() {
        adapter = LeaderboardAdapter(auth.currentUser?.uid ?: "")
        binding.rvLeaderboard.layoutManager = LinearLayoutManager(this)
        binding.rvLeaderboard.adapter = adapter
    }

    private fun setupTabs() {
        binding.tabFriends.setOnClickListener {
            viewModel.setTab(LeaderboardViewModel.Tab.FRIENDS)
        }
        binding.tabGlobal.setOnClickListener {
            viewModel.setTab(LeaderboardViewModel.Tab.GLOBAL)
        }
    }

    private fun setupClicks() {
        binding.backBtn.setOnClickListener {
            finish()
        }
    }

    private fun observeData() {

        viewModel.tab.observe(this) { tab ->
            // Update tab visual state
            val selectedBg  = com.jntuh.capfit.R.drawable.tab_selected_bg
            val unselectedBg = com.jntuh.capfit.R.drawable.tab_unselected_bg
            val white = Color.WHITE
            val black = Color.BLACK

            when (tab) {
                LeaderboardViewModel.Tab.FRIENDS -> {
                    binding.tabFriends.setBackgroundResource(selectedBg)
                    binding.tabFriends.setTextColor(white)
                    binding.tabGlobal.setBackgroundResource(unselectedBg)
                    binding.tabGlobal.setTextColor(black)
                }
                LeaderboardViewModel.Tab.GLOBAL -> {
                    binding.tabGlobal.setBackgroundResource(selectedBg)
                    binding.tabGlobal.setTextColor(white)
                    binding.tabFriends.setBackgroundResource(unselectedBg)
                    binding.tabFriends.setTextColor(black)
                }
                else -> {}
            }
        }

        viewModel.loading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            // Don't touch rvLeaderboard/tvEmpty here — leaderboard observer handles that
            if (isLoading) {
                binding.rvLeaderboard.visibility = View.GONE
                binding.tvEmpty.visibility = View.GONE
            }
        }

        viewModel.emptyMessage.observe(this) { message ->
            binding.tvEmpty.text = message
        }

        viewModel.leaderboard.observe(this) { rankedList ->
            if (rankedList.isNullOrEmpty()) {
                binding.tvEmpty.visibility = View.VISIBLE
                binding.rvLeaderboard.visibility = View.GONE
            } else {
                binding.tvEmpty.visibility = View.GONE
                binding.rvLeaderboard.visibility = View.VISIBLE
                adapter.submitList(rankedList)
            }
        }

        viewModel.myRank.observe(this) { (rank, area) ->
            binding.yourRankNumber.text = "#$rank"
            val areaStr = if (area >= 1_000_000)
                String.format("%.2f km²", area / 1_000_000)
            else
                String.format("%.0f m²", area)
            binding.yourRankScore.text = "   $areaStr"
        }
    }
}