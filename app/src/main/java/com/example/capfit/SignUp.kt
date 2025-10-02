package com.example.capfit

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.databinding.DataBindingUtil
import com.example.capfit.databinding.ActivitySignUpBinding
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

class SignUp : AppCompatActivity() {
    lateinit var binding: ActivitySignUpBinding
    private lateinit var auth: FirebaseAuth
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        binding = DataBindingUtil.setContentView(this , R.layout.activity_sign_up)

        // Initialize Firebase Auth
        auth = Firebase.auth
        binding.apply {

            createAccount.setOnClickListener {
                create()
            }

            signUpToMain.setOnClickListener {
                val i : Intent = Intent(this@SignUp, MainActivity::class.java)
                startActivity(i)
                finish()
            }
        }


    }

    private fun create(){
        auth.createUserWithEmailAndPassword(
            binding.email.text.toString().trim(),
            binding.password.text.toString().trim())
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Sign in success, update UI with the signed-in user's information
                    Log.d("TAG", "createUserWithEmail:success")
                    val user = auth.currentUser
                    updateUI()
                } else {
                    // If sign in fails, display a message to the user.
                    Log.w("TAG", "createUserWithEmail:failure", task.exception)
                    Toast.makeText(
                        baseContext,
                        "Authentication failed.",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
    }


    private fun updateUI(){
        val intent = Intent(this, SecondActivity::class.java)
        startActivity(intent)
        finish()
    }
}