package com.jntuh.capfit.ui.home

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.jntuh.capfit.R
import com.jntuh.capfit.databinding.ActivityDashBoardBinding
import com.jntuh.capfit.databinding.ActivityEditProfileBinding

class DashBoard : AppCompatActivity() {
    private lateinit var binding : ActivityDashBoardBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityDashBoardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, _ -> WindowInsetsCompat.CONSUMED }

        binding.backBtn.setOnClickListener {
            startActivity(Intent(this@DashBoard, HomePage::class.java))
            finish()
        }
    }
}