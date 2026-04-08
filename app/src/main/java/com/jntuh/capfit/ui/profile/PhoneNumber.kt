package com.jntuh.capfit.ui.profile

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.jntuh.capfit.R
import android.view.View
import com.jntuh.capfit.databinding.ActivityPhoneNumberBinding
import com.jntuh.capfit.ui.home.HomePage

class PhoneNumber : AppCompatActivity() {

    private lateinit var binding: ActivityPhoneNumberBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        window.statusBarColor = Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE

        binding = ActivityPhoneNumberBinding.inflate(layoutInflater)
        setContentView(binding.root)


        binding.apply {

            skip.setOnClickListener {
                startActivity(Intent(this@PhoneNumber, HomePage::class.java))
                finish()
            }

            next.setOnClickListener {
                val phone = phoneInput.text.toString().trim()

                if (phone.isNotEmpty()) {
                    // Save phone number to SharedPreferences
                    val prefs = getSharedPreferences("UserData", MODE_PRIVATE)
                    prefs.edit().apply {
                        putString("phoneNumber", phone)
                        apply()
                    }

                    // Navigate to Gender activity
                    startActivity(Intent(this@PhoneNumber, Gender::class.java))
                    finish()
                } else {
                    // Show error if phone number is empty
//                    tvError.text = "Please enter your phone number"
//                    tvError.visibility = android.view.View.VISIBLE
                }
            }
        }
    }
}
