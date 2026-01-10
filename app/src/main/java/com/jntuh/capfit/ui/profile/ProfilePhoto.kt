package com.jntuh.capfit.ui.profile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.snackbar.Snackbar
import com.jntuh.capfit.R
import com.jntuh.capfit.databinding.ActivityProfilePhotoBinding
import com.jntuh.capfit.ui.home.HomePage
import com.jntuh.capfit.viewmodel.UserViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ProfilePhoto : AppCompatActivity() {

    private lateinit var binding: ActivityProfilePhotoBinding
    private var selectedImageUri: Uri? = null

    private val userViewModel: UserViewModel by viewModels()
    private val scope = CoroutineScope(Dispatchers.Main)

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            binding.profilePhoto.setImageURI(it)
            binding.photoBorder.setBackgroundResource(R.drawable.circle_border_selected)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) openGallery()
        else Snackbar.make(binding.root, "Permission denied", Snackbar.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityProfilePhotoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.changePhotoButton.setOnClickListener { checkPermissionAndOpenGallery() }
        binding.profilePhoto.setOnClickListener { checkPermissionAndOpenGallery() }

        binding.next.setOnClickListener {
            if (selectedImageUri != null) {
                updateUserProfile()
            } else {
                Snackbar.make(binding.root, "Please select a photo", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateUserProfile() {
        val photoUri = selectedImageUri.toString()
        val prefs = getSharedPreferences("UserData", MODE_PRIVATE)

        scope.launch {
            val user = userViewModel.getUser()
            if (user != null) {
                val phone = prefs.getString("phoneNumber", user.phone)
                val gender = prefs.getString("gender", user.gender)
                val age = prefs.getInt("age", user.age ?: 0)
                val height = prefs.getString("height", user.height?.toString())
                val weight = prefs.getString("weight", user.weight?.toString())

                val updatedUser = user.copy(
                    phone = phone,
                    gender = gender,
                    age = if (age == 0) null else age,
                    height = height?.toIntOrNull(),
                    weight = weight?.toIntOrNull(),
                    profile_picture = photoUri
                )


                val success = userViewModel.updateUser(updatedUser)
                if (success) {
                    prefs.edit().clear().apply()
                    Snackbar.make(binding.root, "Profile updated successfully", Snackbar.LENGTH_SHORT).show()
                    startActivity(Intent(this@ProfilePhoto, HomePage::class.java))
                    finish()
                } else {
                    Snackbar.make(binding.root, "Failed to update profile", Snackbar.LENGTH_SHORT).show()
                }

            } else {
                Snackbar.make(binding.root, "Failed to load user", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkPermissionAndOpenGallery() {
        when {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED -> openGallery()
            else -> requestPermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
        }
    }

    private fun openGallery() {
        pickImageLauncher.launch("image/*")
    }

    private fun enableEdgeToEdge() {
        window.statusBarColor = ContextCompat.getColor(this, android.R.color.transparent)
        window.navigationBarColor = ContextCompat.getColor(this, android.R.color.transparent)
    }
}
