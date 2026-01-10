package com.jntuh.capfit.ui.profile

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.jntuh.capfit.R
import com.jntuh.capfit.databinding.ActivityGenderBinding
import com.jntuh.capfit.ui.home.HomePage

class Gender : AppCompatActivity() {

    private lateinit var binding: ActivityGenderBinding
    private var selectedGender: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityGenderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.apply {

            btnMale.setOnClickListener {
                selectedGender = "Male"
                btnMale.setBackgroundResource(R.drawable.gender_selected_bg)
                btnFemale.setBackgroundResource(R.drawable.gender_unselected_bg)
            }

            btnFemale.setOnClickListener {
                selectedGender = "Female"
                btnFemale.setBackgroundResource(R.drawable.gender_selected_bg)
                btnMale.setBackgroundResource(R.drawable.gender_unselected_bg)
            }

            skip.setOnClickListener {
                startActivity(Intent(this@Gender, HomePage::class.java))
                finish()
            }

            next.setOnClickListener {
                if (selectedGender != null) {
                    val prefs = getSharedPreferences("UserData", MODE_PRIVATE)
                    prefs.edit().apply {
                        putString("gender", selectedGender)
                        apply()
                    }

                    startActivity(Intent(this@Gender, Age::class.java))
                    finish()
                } else {


                }
            }
        }
    }
}
