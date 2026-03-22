package com.jntuh.capfit.ui.introduction

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.jntuh.capfit.databinding.ActivityIntroduction4Binding
import com.jntuh.capfit.ui.authentication.Login

class Introduction4 : AppCompatActivity() {
    private lateinit var binding : ActivityIntroduction4Binding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityIntroduction4Binding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.next.setOnClickListener {
            startActivity(Intent(this, Login::class.java))
            finish()
        }
    }
}