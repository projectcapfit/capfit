package com.jntuh.capfit.ui.introduction

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.jntuh.capfit.databinding.ActivityIntroductionBinding

class Introduction1 : AppCompatActivity() {
    private lateinit var binding : ActivityIntroductionBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityIntroductionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonLetsStart.setOnClickListener {
            startActivity(Intent(this, Introduction2::class.java))
            finish()
        }
    }
}