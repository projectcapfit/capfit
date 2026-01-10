package com.jntuh.capfit.ui.Account

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.jntuh.capfit.databinding.ActivityMyAccountBinding
import com.jntuh.capfit.ui.home.HomePage
import com.jntuh.capfit.viewmodel.UserViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MyAccount : AppCompatActivity() {

    private lateinit var binding: ActivityMyAccountBinding

    @Inject lateinit var auth: FirebaseAuth
    @Inject lateinit var sharedPreferences: SharedPreferences
    private val userViewModel: UserViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMyAccountBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, _ ->
            WindowInsetsCompat.CONSUMED
        }

        loadUserDetails()

        binding.apply {
            backButton.setOnClickListener {
                startActivity(Intent(this@MyAccount, HomePage::class.java))
                finish()
            }
            editButton.setOnClickListener {
                startActivity(Intent(this@MyAccount, EditProfile::class.java))
                finish()
            }
        }
    }

    private fun loadUserDetails() {

        lifecycleScope.launchWhenStarted {

            userViewModel.userState.collect { user ->

                if (user == null) return@collect

                // -------------------------------
                // NAME (with fallback)
                // -------------------------------
                val finalName = user.name
                    ?: auth.currentUser?.displayName
                    ?: auth.currentUser?.email?.substringBefore("@")
                        ?.replaceFirstChar { it.uppercase() }
                    ?: "User"

                binding.userName.text = finalName


                // -------------------------------
                // PROFILE PHOTO
                // -------------------------------
                val googlePhotoUrl = getSharedPreferences("UserData", MODE_PRIVATE)
                    .getString("googlePhoto", null)

                if (!googlePhotoUrl.isNullOrEmpty()) {

                    Glide.with(this@MyAccount)
                        .load(googlePhotoUrl)
                        .circleCrop()
                        .into(binding.profileImage)

                } else {
                    binding.profileImage.setImageResource(0)
                    binding.profileImage.setBackgroundResource(
                        com.jntuh.capfit.R.drawable.profile_circle_bg
                    )

                    val initial = finalName.first().uppercase()
                    binding.profileLetter.text = initial
                }


                // -------------------------------
                // WEIGHT
                // -------------------------------
                val weight = user.weight ?: 0
                val weightUnit = user.weightUnit ?: "kg"

                binding.weightValue.text = if (weight == 0) "-" else weight.toString()
                binding.weightUnit.text = " $weightUnit"


                // -------------------------------
                // HEIGHT
                // -------------------------------
                val height = user.height ?: 0
                val heightUnit = user.heightUnit ?: "cm"

                binding.heightValue.text = if (height == 0) "-" else height.toString()
                binding.heightUnit.text = " $heightUnit"


                // -------------------------------
                // AGE
                // -------------------------------
                val age = user.age ?: 0

                binding.ageValue.text = if (age == 0) "-" else age.toString()
                binding.ageUnit.text = " year"


                // -------------------------------
                // PHONE NUMBER
                // -------------------------------
                val phone = user.phone ?: "-"

                binding.phoneValue.text = phone


                // -------------------------------
                // EMAIL
                // -------------------------------
                val email = user.email
                    ?: auth.currentUser?.email
                    ?: "-"

                binding.emailValue.text = email


                // -------------------------------
                // GENDER
                // -------------------------------
                val gender = user.gender ?: "Not set"

                binding.genderValue.text = gender
            }
        }
    }
}
