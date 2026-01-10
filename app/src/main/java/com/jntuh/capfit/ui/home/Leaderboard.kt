package com.jntuh.capfit.ui.home

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.jntuh.capfit.R
import com.jntuh.capfit.databinding.ActivityDashBoardBinding
import com.jntuh.capfit.databinding.ActivityLeaderboardBinding

class Leaderboard : AppCompatActivity(){
    private lateinit var binding : ActivityLeaderboardBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityLeaderboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, _ -> WindowInsetsCompat.CONSUMED }

        binding.backBtn.setOnClickListener {
            startActivity(Intent(this@Leaderboard, HomePage::class.java))
            finish()
        }
    }
}