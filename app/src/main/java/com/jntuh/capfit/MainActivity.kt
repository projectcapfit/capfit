package com.jntuh.capfit

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.annotation.Nullable
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.jntuh.capfit.databinding.ActivityMainBinding
import com.jntuh.capfit.ui.authentication.Login
import com.jntuh.capfit.ui.home.HomePage
import com.jntuh.capfit.ui.introduction.Introduction1
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    lateinit var binding: ActivityMainBinding

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    @Inject
    lateinit var sharedPreferencesEditor: SharedPreferences.Editor

    @Inject
    lateinit var auth: FirebaseAuth



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (sharedPreferences.contains("first_time")){
            if (auth.currentUser != null){
                startActivity(Intent(this, HomePage::class.java))
                finish()
            }
            else {
                startActivity(Intent(this, Login::class.java))
                finish()
            }
        }
        else {
            sharedPreferencesEditor.putBoolean("first_time", false).apply()
            val i = Intent(this, Introduction1::class.java)
            startActivity(i)
            finish()
        }

    }
}