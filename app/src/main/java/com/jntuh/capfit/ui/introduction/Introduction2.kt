package com.jntuh.capfit.ui.introduction

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.jntuh.capfit.databinding.ActivityIntroduction2Binding
import com.jntuh.capfit.ui.authentication.Login


class Introduction2 : AppCompatActivity() {
    private lateinit var binding : ActivityIntroduction2Binding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityIntroduction2Binding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.apply {
            next.setOnClickListener {
                startActivity(Intent(this@Introduction2, Introduction3::class.java))
                finish()
            }

            skip.setOnClickListener {
                startActivity(Intent(this@Introduction2, Login::class.java))
                finish()
            }
        }
    }
}