package com.jntuh.capfit.ui.Account

import android.content.Intent
import android.os.Bundle
import android.widget.PopupMenu
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.jntuh.capfit.R
import com.jntuh.capfit.databinding.ActivityEditProfileBinding
import com.jntuh.capfit.viewmodel.UserViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.google.firebase.auth.FirebaseAuth
import android.content.SharedPreferences
import android.widget.Toast
import com.jntuh.capfit.ui.home.HomePage

@AndroidEntryPoint
class EditProfile : AppCompatActivity() {

    private lateinit var binding: ActivityEditProfileBinding

    @Inject lateinit var auth: FirebaseAuth
    @Inject lateinit var sharedPreferences: SharedPreferences

    private val userViewModel: UserViewModel by viewModels()

    private var selectedWeightUnit = "kg"
    private var selectedHeightUnit = "cm"
    private var selectedGender = "Female"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityEditProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, _ -> WindowInsetsCompat.CONSUMED }

        loadExistingUserData()
        setupGenderDropdown()
        setupWeightToggle()
        setupHeightToggle()
        setupSaveButton()

        binding.backButton.setOnClickListener {
            startActivity(Intent(this@EditProfile , HomePage::class.java))
            finish() }
    }

    // ----------------------------------------------------------------------
    // 1. LOAD USER DETAILS INTO UI
    // ----------------------------------------------------------------------
    private fun loadExistingUserData() {
        lifecycleScope.launch {
            userViewModel.userState.collect { user ->
                if (user == null) return@collect

                // Full name
                binding.inputFullName.setText(user.name ?: "")

                // Phone
                binding.inputPhone.setText(user.phone ?: "")

                // Weight + unit
                binding.inputWeight.setText(
                    if (user.weight == null || user.weight == 0) "" else user.weight.toString()
                )
                selectedWeightUnit = user.weightUnit ?: "kg"
                highlightWeightUnit()

                // Height + unit
                binding.inputHeight.setText(
                    if (user.height == null || user.height == 0f) "" else user.height.toString()
                )
                selectedHeightUnit = user.heightUnit ?: "cm"
                highlightHeightUnit()

                // Gender
                selectedGender = user.gender ?: "Female"
                binding.inputGender.text = selectedGender

                // Age
                binding.inputAge.setText(
                    if (user.age == null || user.age == 0) "" else user.age.toString()
                )
            }
        }
    }

    // ----------------------------------------------------------------------
    // 2. GENDER DROPDOWN
    // ----------------------------------------------------------------------
    private fun setupGenderDropdown() {
        val popup = PopupMenu(this, binding.genderDropdown)
        val genderList = listOf("Male", "Female", "Other")

        genderList.forEach { popup.menu.add(it) }

        popup.setOnMenuItemClickListener {
            selectedGender = it.title.toString()
            binding.inputGender.text = selectedGender
            true
        }

        binding.genderDropdown.setOnClickListener { popup.show() }
        binding.inputGender.setOnClickListener { popup.show() }
        binding.genderIcon.setOnClickListener { popup.show() }
    }

    // ----------------------------------------------------------------------
    // 3. WEIGHT UNIT TOGGLE
    // ----------------------------------------------------------------------
    private fun setupWeightToggle() {
        binding.tvKg.setOnClickListener {
            selectedWeightUnit = "kg"
            highlightWeightUnit()
        }

        binding.tvLbs.setOnClickListener {
            selectedWeightUnit = "lbs"
            highlightWeightUnit()
        }
    }

    private fun highlightWeightUnit() {
        if (selectedWeightUnit == "kg") {
            binding.tvKg.setBackgroundResource(R.drawable.rounded_bg)
            binding.tvLbs.setBackgroundResource(android.R.color.transparent)
        } else {
            binding.tvLbs.setBackgroundResource(R.drawable.rounded_bg)
            binding.tvKg.setBackgroundResource(android.R.color.transparent)
        }
    }

    // ----------------------------------------------------------------------
    // 4. HEIGHT UNIT TOGGLE
    // ----------------------------------------------------------------------
    private fun setupHeightToggle() {
        binding.tvCm.setOnClickListener {
            selectedHeightUnit = "cm"
            highlightHeightUnit()
        }

        binding.tvFeet.setOnClickListener {
            selectedHeightUnit = "ft"
            highlightHeightUnit()
        }
    }

    private fun highlightHeightUnit() {
        if (selectedHeightUnit == "cm") {
            binding.tvCm.setBackgroundResource(R.drawable.rounded_bg)
            binding.tvFeet.setBackgroundResource(android.R.color.transparent)
        } else {
            binding.tvFeet.setBackgroundResource(R.drawable.rounded_bg)
            binding.tvCm.setBackgroundResource(android.R.color.transparent)
        }
    }
    private fun setupSaveButton() {
        binding.btnSave.setOnClickListener {

            val name = binding.inputFullName.text.toString().trim()
            val phone = binding.inputPhone.text.toString().trim()
            val weight = binding.inputWeight.text.toString().toIntOrNull()
            val height = binding.inputHeight.text.toString().toFloatOrNull()
            val age = binding.inputAge.text.toString().toIntOrNull()

            val currentUser = userViewModel.userState.value
            if (currentUser == null) {
                Toast.makeText(this, "Error: User not loaded", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // CREATE UPDATED USER OBJECT
            val updatedUser = currentUser.copy(
                name = name.ifBlank { currentUser.name },
                phone = phone.ifBlank { currentUser.phone },
                gender = selectedGender,
                age = age ?: currentUser.age,
                weight = weight ?: currentUser.weight,
                weightUnit = selectedWeightUnit,
                height = height ?: currentUser.height,
                heightUnit = selectedHeightUnit
            )

            lifecycleScope.launch {
                val success = userViewModel.updateUser(updatedUser)

                if (success) {
                    Toast.makeText(this@EditProfile, "Updated Successfully!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@EditProfile , MyAccount::class.java))
                    finish()
                } else {
                    startActivity(Intent(this@EditProfile , MyAccount::class.java))
                    Toast.makeText(this@EditProfile, "Failed to update", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

}
