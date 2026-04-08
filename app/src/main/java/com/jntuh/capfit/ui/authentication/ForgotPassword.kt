package com.jntuh.capfit.ui.authentication

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.firebase.auth.FirebaseAuth
import com.jntuh.capfit.R
import android.view.View
import com.jntuh.capfit.databinding.ActivityForgotPasswordBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ForgotPassword : AppCompatActivity() {


    @Inject
    lateinit var auth: FirebaseAuth

    private lateinit var binding: ActivityForgotPasswordBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE


        binding = ActivityForgotPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.apply {
            buttonResetPassword.setOnClickListener {
                auth.sendPasswordResetEmail(binding.editTextEmail.text.toString().trim())
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Toast.makeText(this@ForgotPassword, "Password reset email sent", Toast.LENGTH_SHORT).show()
                            startActivity(Intent(this@ForgotPassword, Login::class.java))
                            finish()
                        }
                        else {
                            Toast.makeText(this@ForgotPassword, "Failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                        }
                    }

            }
            backButton.setOnClickListener {
                finish()
            }
        }
    }
}