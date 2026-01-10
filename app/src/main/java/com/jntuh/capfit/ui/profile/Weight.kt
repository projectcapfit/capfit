package com.jntuh.capfit.ui.profile

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.jntuh.capfit.R
import com.jntuh.capfit.databinding.ActivityWeightBinding
import com.jntuh.capfit.ui.home.HomePage

class Weight : AppCompatActivity() {

    private lateinit var binding : ActivityWeightBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityWeightBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.apply {
            tvLbs.setOnClickListener {
                tvLbs.setBackgroundResource(R.drawable.rounded_bg)
                tvKg.setBackgroundResource( android.R.color.transparent)
                weightUnit.setText("LBS")
            }

            tvKg.setOnClickListener {
                tvKg.setBackgroundResource(R.drawable.rounded_bg)
                tvLbs.setBackgroundResource(android.R.color.transparent)
                weightUnit.setText("KG")
            }

            skip.setOnClickListener {
                startActivity(Intent(this@Weight, HomePage::class.java))
                finish()
            }
            next.setOnClickListener {
                val weightValue = weightInput.text.toString().trim()
                val weightUnitValue = weightUnit.text.toString().trim()

                if (weightValue.isNotEmpty()) {
                    val prefs = getSharedPreferences("UserData", MODE_PRIVATE)
                    prefs.edit().apply {
                        putString("weight", weightValue)
                        putString("weightUnit", weightUnitValue)
                        apply()
                    }
                    startActivity(Intent(this@Weight, Height::class.java))
                    finish()
                } else {
                    // optional: show error
                }
            }

        }
    }
}