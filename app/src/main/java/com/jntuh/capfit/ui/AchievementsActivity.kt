package com.jntuh.capfit.ui

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.recyclerview.widget.GridLayoutManager
import com.jntuh.capfit.adapter.AchievementAdapter
import com.jntuh.capfit.data.UserGameData
import com.jntuh.capfit.databinding.ActivityAchievementsBinding
import com.jntuh.capfit.repository.AchievementManager

class AchievementsActivity : BaseActivity() {

    private lateinit var binding: ActivityAchievementsBinding
    private val achievementManager = AchievementManager()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityAchievementsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
    }

    private fun setupUI() {

        binding.backBtn.setOnClickListener { finish() }

        val userData = UserGameData(
            achievements = listOf(101, 103, 201, 301),
            achievementProgress = mapOf(201 to 2)
        )

        val unlocked = achievementManager.getUnlocked(userData).size
        val total = achievementManager.all().size

        binding.progressText.text = "$unlocked of $total unlocked"
        binding.progressBar.max = total
        binding.progressBar.progress = unlocked

        binding.recyclerAchievements.layoutManager = GridLayoutManager(this, 2)
        binding.recyclerAchievements.adapter =
            AchievementAdapter(achievementManager.all(), userData)
    }
}