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
import com.jntuh.capfit.databinding.ActivityAgeBinding
import com.jntuh.capfit.ui.home.HomePage

class Age : AppCompatActivity() {

    private lateinit var binding: ActivityAgeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE

        binding = ActivityAgeBinding.inflate(layoutInflater)
        setContentView(binding.root)


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
