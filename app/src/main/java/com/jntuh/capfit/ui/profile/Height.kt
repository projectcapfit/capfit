package com.jntuh.capfit.ui.profile

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.jntuh.capfit.R
import com.jntuh.capfit.databinding.ActivityHeightBinding
import com.jntuh.capfit.ui.home.HomePage

class Height : AppCompatActivity() {

    private lateinit var binding: ActivityHeightBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityHeightBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        var heightUnitSelected = "ft"

        binding.apply {

            tvFeet.setOnClickListener {
                heightUnitSelected = "ft"
                tvFeet.setBackgroundResource(R.drawable.rounded_bg)
                tvCm.setBackgroundResource(android.R.color.transparent)
                heightUnit.setText(heightUnitSelected)
            }

            tvCm.setOnClickListener {
                heightUnitSelected = "cm"
                tvCm.setBackgroundResource(R.drawable.rounded_bg)
                tvFeet.setBackgroundResource(android.R.color.transparent)
                heightUnit.setText(heightUnitSelected)
            }

            skip.setOnClickListener {
                startActivity(Intent(this@Height, HomePage::class.java))
                finish()
            }

            next.setOnClickListener {

                val heightValue = heightInput.text.toString().trim()

                if (heightValue.isNotEmpty()) {
                    val prefs = getSharedPreferences("UserData", MODE_PRIVATE)
                    prefs.edit().apply {
                        putString("height", heightValue)
                        putString("heightUnit", heightUnitSelected)
                        apply()
                    }

                    startActivity(Intent(this@Height, ProfilePhoto::class.java))
                    finish()
                } else {
                    // show error if height is empty
//                    tvError.text = "Please enter your height"
//                    tvError.visibility = android.view.View.VISIBLE
                }
            }
        }
    }
}
