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

                // Name
                val finalName = user.name
                    ?: auth.currentUser?.displayName
                    ?: auth.currentUser?.email?.substringBefore("@")
                        ?.replaceFirstChar { it.uppercase() }
                    ?: "User"
                binding.userName.text = finalName

                // Profile photo — use user.profilePicture (Firestore) first,
                // fall back to SharedPrefs, then letter avatar
                val firestorePhoto = user.profilePicture
                    ?.takeIf { it.isNotBlank() && it != "null" }

                val prefPhoto = getSharedPreferences("UserData", MODE_PRIVATE)
                    .getString("googlePhoto", null)
                    ?.takeIf { it.isNotBlank() && it != "null" }

                val finalPhoto = firestorePhoto ?: prefPhoto

                if (finalPhoto != null) {
                    binding.profileImage.visibility = View.VISIBLE
                    binding.profileLetter.visibility = View.GONE
                    Glide.with(this@MyAccount)
                        .load(finalPhoto)
                        .placeholder(com.jntuh.capfit.R.drawable.profile_circle_bg)
                        .error(com.jntuh.capfit.R.drawable.profile_circle_bg)
                        .circleCrop()
                        .into(binding.profileImage)
                } else {
                    binding.profileImage.visibility = View.GONE
                    binding.profileLetter.visibility = View.VISIBLE
                    binding.profileLetter.text = finalName.first().uppercase()
                }

                // Weight
                val weight = user.weight ?: 0
                binding.weightValue.text = if (weight == 0) "-" else weight.toString()
                binding.weightUnit.text  = " ${user.weightUnit ?: "kg"}"

                // Height
                val height = user.height ?: 0f
                binding.heightValue.text = if (height == 0f) "-" else height.toString()
                binding.heightUnit.text  = " ${user.heightUnit ?: "cm"}"

                // Age
                val age = user.age ?: 0
                binding.ageValue.text = if (age == 0) "-" else age.toString()
                binding.ageUnit.text  = " year"

                // Phone
                binding.phoneValue.text = user.phone ?: "-"

                // Email
                binding.emailValue.text = user.email
                    ?: auth.currentUser?.email
                            ?: "-"

                // Gender
                binding.genderValue.text = user.gender ?: "Not set"
            }
        }
    }
}