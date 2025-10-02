package com.example.capfit

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

class SecondActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_second)
        auth = Firebase.auth
        val user = auth.currentUser
        user.let{
            val email = it?.email
            val textView : TextView = findViewById(R.id.second)
            textView.text = email.toString()
        }

        findViewById<TextView>(R.id.SignOut).setOnClickListener {
            auth.signOut()
            var i : Intent = Intent(this, MainActivity::class.java)
            startActivity(i)
            finish()
        }
    }
}