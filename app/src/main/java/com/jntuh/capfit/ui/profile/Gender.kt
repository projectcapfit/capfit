package com.jntuh.capfit.ui.profile

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.view.View
import androidx.core.view.WindowInsetsControllerCompat
import com.jntuh.capfit.R
import com.jntuh.capfit.databinding.ActivityGenderBinding
import com.jntuh.capfit.ui.home.HomePage

class Gender : AppCompatActivity() {

    private lateinit var binding: ActivityGenderBinding
    private var selectedGender: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        window.statusBarColor = Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE



        binding = ActivityGenderBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
