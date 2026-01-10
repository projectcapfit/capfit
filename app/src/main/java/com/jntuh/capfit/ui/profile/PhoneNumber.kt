package com.jntuh.capfit.ui.profile

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.jntuh.capfit.R
import com.jntuh.capfit.databinding.ActivityPhoneNumberBinding
import com.jntuh.capfit.ui.home.HomePage

class PhoneNumber : AppCompatActivity() {

    private lateinit var binding: ActivityPhoneNumberBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityPhoneNumberBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

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
