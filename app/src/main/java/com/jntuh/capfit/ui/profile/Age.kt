package com.jntuh.capfit.ui.profile

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.jntuh.capfit.databinding.ActivityAgeBinding
import com.jntuh.capfit.ui.home.HomePage

class Age : AppCompatActivity() {

    private lateinit var binding: ActivityAgeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityAgeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.apply {

            agePicker.minValue = 5
            agePicker.maxValue = 100
            agePicker.value = 21
            agePicker.wrapSelectorWheel = true

            skip.setOnClickListener {
                startActivity(Intent(this@Age, HomePage::class.java))
                finish()
            }

            next.setOnClickListener {
                val prefs = getSharedPreferences("UserData", MODE_PRIVATE)
                prefs.edit().apply {
                    putInt("age", agePicker.value)
                    apply()
                }

                startActivity(Intent(this@Age, Weight::class.java))
                finish()
            }
        }
    }

}
