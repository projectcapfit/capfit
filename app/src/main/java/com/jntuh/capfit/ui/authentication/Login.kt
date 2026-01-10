package com.jntuh.capfit.ui.authentication

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.jntuh.capfit.R
import com.jntuh.capfit.databinding.ActivityLoginBinding
import com.jntuh.capfit.ui.home.HomePage
import com.jntuh.capfit.ui.profile.PhoneNumber
import com.jntuh.capfit.viewmodel.UserViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.getValue

@AndroidEntryPoint
class Login : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var credentialManager: CredentialManager

    @Inject lateinit var auth: FirebaseAuth

    private val userViewModel: UserViewModel by viewModels()
    companion object { private const val TAG = "LoginTag" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            view.setPadding(
                insets.getInsets(WindowInsetsCompat.Type.systemBars()).left,
                insets.getInsets(WindowInsetsCompat.Type.systemBars()).top,
                insets.getInsets(WindowInsetsCompat.Type.systemBars()).right,
                insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            )
            insets
        }

        binding.forgotPassword.setOnClickListener {
            startActivity(Intent(this@Login , ForgotPassword::class.java))
        }

        binding.registerButton.setOnClickListener {
            startActivity(Intent(this, SignUp::class.java))
            finish()
        }

        binding.buttonLogin.setOnClickListener { signInCheck() }
        binding.loginWithGoogle.setOnClickListener { signInWithGoogle() }
    }

    private fun signInWithGoogle() {
        credentialManager = CredentialManager.create(baseContext)
        launchCredentialManager()
    }

    private fun launchCredentialManager() {
        val googleOption = GetGoogleIdOption.Builder()
            .setServerClientId(getString(R.string.default_web_client_id))
            .setFilterByAuthorizedAccounts(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleOption)
            .build()

        lifecycleScope.launch {
            try {
                val result = credentialManager.getCredential(
                    this@Login, request
                )
                handleSignIn(result.credential)
            } catch (e: GetCredentialException) {
                Log.e(TAG, "Google Sign-in failed: ${e.localizedMessage}")
            }
        }
    }

    private fun handleSignIn(credential: Credential) {
        if (credential is CustomCredential &&
            credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val googleCred = GoogleIdTokenCredential.createFrom(credential.data)

            // ⭐ Save Google profile picture
            val googlePhotoUrl = googleCred.profilePictureUri?.toString()
            getSharedPreferences("UserData", MODE_PRIVATE)
                .edit()
                .putString("googlePhoto", googlePhotoUrl)
                .apply()

            firebaseAuthWithGoogle(googleCred.idToken)

            Log.d("GOOGLE_TEST", "photoUri = ${googleCred.profilePictureUri}")
            Log.d("GOOGLE_TEST", "displayName = ${googleCred.displayName}")
            Log.d("GOOGLE_TEST", "email = ${googleCred.id}")

        }
    }
    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)

        auth.signInWithCredential(credential).addOnCompleteListener(this) { task ->
            if (task.isSuccessful) {

                val firebaseUser = auth.currentUser
                val firebasePhotoUrl = firebaseUser?.photoUrl?.toString()

                val prefs = getSharedPreferences("UserData", MODE_PRIVATE)
                prefs.edit().putString("googlePhoto", firebasePhotoUrl).apply()

                Log.d("PHOTO_SAVE", "Saved Google Photo: $firebasePhotoUrl")

                // ⭐ IMPORTANT: reload user from Firestore
                userViewModel.loadUser()

                // now safely go to UI
                updateUI()
            } else {
                Toast.makeText(this, "Google authentication failed.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateUI() {
        userViewModel.loadUser()
        lifecycleScope.launch {
            userViewModel.userState.collect { user ->

                if (user != null && user.phone != null && user.gender != null) {
                    startActivity(Intent(this@Login, HomePage::class.java))
                    finish()
                    return@collect
                }

                if (user != null) {
                    startActivity(Intent(this@Login, PhoneNumber::class.java))
                    finish()
                    return@collect
                }
            }
        }
    }


    private fun signInCheck() {
        val email = binding.editTextEmail.text.toString().trim()
        val password = binding.editTextPassword.text.toString().trim()

        if(email.isEmpty()){
            Toast.makeText(this, "Email is Required", Toast.LENGTH_SHORT).show()
            return
        }
        if (password.isEmpty()){
            Toast.makeText(this, "Password is Required", Toast.LENGTH_SHORT).show()
            return
        }
        auth.signInWithEmailAndPassword(email, password).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                userViewModel.loadUser()
                updateUI()
            }

            else Toast.makeText(this, "Wrong Credentials", Toast.LENGTH_SHORT).show()
        }
    }
}
