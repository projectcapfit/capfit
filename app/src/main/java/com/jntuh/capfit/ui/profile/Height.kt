package com.jntuh.capfit.ui.profile

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.jntuh.capfit.R
import com.jntuh.capfit.databinding.ActivityHeightBinding
import com.jntuh.capfit.ui.home.HomePage
import com.jntuh.capfit.viewmodel.UserViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
class Height : AppCompatActivity() {

    private lateinit var binding: ActivityHeightBinding
    private val userViewModel: UserViewModel by viewModels()
    private val scope = CoroutineScope(Dispatchers.Main)

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

        var heightUnitSelected = "cm"

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
                goToHomePageAfterSaving()
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

                    goToHomePageAfterSaving()
                }
            }
        }
    }

    private fun goToHomePageAfterSaving() {
        scope.launch {

            val prefs = getSharedPreferences("UserData", MODE_PRIVATE)
            val googlePhoto = prefs.getString("googlePhoto", null)

            val user = userViewModel.getUser()
            if (user != null) {

                val updatedUser = user.copy(
                    phone = prefs.getString("phoneNumber", user.phone),
                    gender = prefs.getString("gender", user.gender),
                    age = prefs.getInt("age", user.age ?: 0),
                    height = prefs.getString("height", user.height?.toString())?.toFloatOrNull(),
                    weight = prefs.getString("weight", user.weight?.toString())?.toIntOrNull(),
                    heightUnit = prefs.getString("heightUnit", user.heightUnit),
                    weightUnit = prefs.getString("weightUnit", user.weightUnit),
                    profilePicture = googlePhoto
                )

                userViewModel.updateUser(updatedUser)

                prefs.edit().clear().apply()

                startActivity(Intent(this@Height, HomePage::class.java))
                finish()
            }
        }
    }
}
