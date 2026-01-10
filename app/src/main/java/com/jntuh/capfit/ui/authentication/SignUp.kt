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
import com.google.firebase.auth.userProfileChangeRequest
import com.jntuh.capfit.R
import com.jntuh.capfit.databinding.ActivitySignUpBinding
import com.jntuh.capfit.ui.home.HomePage
import com.jntuh.capfit.ui.profile.PhoneNumber
import com.jntuh.capfit.viewmodel.UserViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.core.content.edit

@AndroidEntryPoint
class SignUp : AppCompatActivity() {

    private lateinit var binding: ActivitySignUpBinding

    @Inject lateinit var auth: FirebaseAuth
    private lateinit var credentialManager: CredentialManager

    private val userViewModel: UserViewModel by viewModels()

    companion object { private const val TAG = "SignUpTag" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivitySignUpBinding.inflate(layoutInflater)
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

        binding.goToLogin.setOnClickListener {
            startActivity(Intent(this, Login::class.java))
            finish()
        }

        binding.signUpWithGoogle.setOnClickListener { signInWithGoogle() }

        binding.buttonCreateAccount.setOnClickListener {
            if (binding.editTextName.text.toString().trim().isEmpty()){
                Toast.makeText(this, "Name is Required", Toast.LENGTH_SHORT).show()
            } else if (binding.editTextEmail.text.toString().trim().isEmpty()){
                Toast.makeText(this, "Email is Required", Toast.LENGTH_SHORT).show()
            } else if (binding.editTextPassword.text.toString().trim().isEmpty()){
                Toast.makeText(this, "Password is Required", Toast.LENGTH_SHORT).show()
            } else {
            registerUser(
                binding.editTextName.text.toString().trim(),
                binding.editTextEmail.text.toString().trim(),
                binding.editTextPassword.text.toString().trim()
            )
            }
        }
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
                    this@SignUp, request
                )
                handleSignIn(result.credential)
            } catch (e: GetCredentialException) {
                Log.e(TAG, "Google signup failed: ${e.localizedMessage}")
            }
        }
    }

    private fun handleSignIn(credential: Credential) {
        if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
            val googlePhotoUrl = googleCred.profilePictureUri?.toString()
            getSharedPreferences("UserData", MODE_PRIVATE)
                .edit {
                    putString("googlePhoto", googlePhotoUrl)
                }
            firebaseAuthWithGoogle(googleCred.idToken)
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val firebaseCred = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(firebaseCred).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val firebaseUser = auth.currentUser
                val firebasePhotoUrl = firebaseUser?.photoUrl?.toString()

                val prefs = getSharedPreferences("UserData", MODE_PRIVATE)
                prefs.edit().putString("googlePhoto", firebasePhotoUrl).apply()
                Log.d("PHOTO_SAVE", "Saved Google Photo: $firebasePhotoUrl")
                userViewModel.loadUser()
                updateUI()
            } else {
                Toast.makeText(this, "Google signup failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateUI() {
        lifecycleScope.launch {
            userViewModel.userState.collect { user ->

                if (user != null && user.phone != null && user.gender != null) {
                    startActivity(Intent(this@SignUp, HomePage::class.java))
                    finish()
                    return@collect
                }

                if (user != null) {
                    startActivity(Intent(this@SignUp, PhoneNumber::class.java))
                    finish()
                    return@collect
                }
            }
        }
    }

    private fun registerUser(name: String, email: String, password: String) {
        auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val update = userProfileChangeRequest {
                    displayName = name
                }
                auth.currentUser?.updateProfile(update)
                userViewModel.loadUser()
                updateUI()
            } else {
                Toast.makeText(this, task.exception?.message, Toast.LENGTH_LONG).show()
            }
        }
    }
}
