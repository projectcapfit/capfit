package com.jntuh.capfit.ui.introduction

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.jntuh.capfit.databinding.ActivityIntroduction3Binding
import com.jntuh.capfit.ui.authentication.Login

class Introduction3 : AppCompatActivity() {
    private lateinit var binding : ActivityIntroduction3Binding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityIntroduction3Binding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.apply {
            next.setOnClickListener {
                startActivity(Intent(this@Introduction3, Introduction4::class.java))
                finish()
            }

            skip.setOnClickListener {
                startActivity(Intent(this@Introduction3, Login::class.java))
            }
        }
    }
}