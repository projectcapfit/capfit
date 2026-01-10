package com.jntuh.capfit.ui.authentication

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
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
import com.google.firebase.auth.ActionCodeSettings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.jntuh.capfit.R
import com.jntuh.capfit.databinding.ActivityLoginBinding
import com.jntuh.capfit.ui.home.HomePage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class Login : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var credentialManager: CredentialManager

    @Inject
    lateinit var auth: FirebaseAuth

    companion object { private const val TAG = "LoginTag" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        savedInstanceState?.let {
            binding.editTextEmail.setText(it.getString("email"))
            binding.editTextPassword.setText(it.getString("password"))
        }

        binding.apply {
            registerButton.setOnClickListener {
                startActivity(Intent(this@Login, SignUp::class.java))
            }

            forgotPassword.setOnClickListener {
                startActivity(Intent(this@Login, ForgotPassword::class.java))
            }

            buttonLogin.setOnClickListener {
                signInCheck()
            }

            loginWithGoogle.setOnClickListener {
                signInWithGoogle()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("email", binding.editTextEmail.text.toString())
        outState.putString("password", binding.editTextPassword.text.toString())
    }

    private fun signInWithGoogle() {
        credentialManager = CredentialManager.create(baseContext)
        launchCredentialManager()
        Toast.makeText(this, "pressed", Toast.LENGTH_SHORT).show()
    }

    private fun launchCredentialManager() {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(getString(R.string.default_web_client_id))
            .setFilterByAuthorizedAccounts(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        lifecycleScope.launch {
            try {
                val result = credentialManager.getCredential(
                    context = this@Login,
                    request = request
                )
                handleSignIn(result.credential)
            } catch (e: GetCredentialException) {
                Log.e(TAG, "Couldn't retrieve user's credentials: ${e.localizedMessage}")
            }
        }
    }

    private fun handleSignIn(credential: Credential) {
        if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            firebaseAuthWithGoogle(googleIdTokenCredential.idToken)
        } else {
            Log.w(TAG, "Credential is not of type Google ID!")
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) updateUI()
                else Toast.makeText(baseContext, "Authentication failed.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateUI() {
        startActivity(Intent(this, HomePage::class.java))
        finish()
    }

    private fun signInCheck() {
        val email = binding.editTextEmail.text.toString().trim()
        val password = binding.editTextPassword.text.toString().trim()

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null && user.isEmailVerified) {
                        updateUI()
                    } else {
                        Toast.makeText(
                            this,
                            "Please verify your email before logging in.",
                            Toast.LENGTH_LONG
                        ).show()
                        sendVerificationEmail(user)
                        auth.signOut()
                    }
                } else {
                    Toast.makeText(
                        this,
                        "Authentication failed: Wrong Credentials",
                        Toast.LENGTH_SHORT
                    ).show()
                    binding.editTextEmail.setText("")
                    binding.editTextPassword.setText("")
                }
            }
    }

    private fun sendVerificationEmail(user: FirebaseUser? = auth.currentUser) {
        user?.let {
            val actionCodeSettings = ActionCodeSettings.newBuilder()
                .setUrl("https://capfit-635ff.web.app")
                .setHandleCodeInApp(true)
                .setAndroidPackageName("com.jntuh.capfit", true, null)
                .build()

            it.sendEmailVerification(actionCodeSettings)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(
                            this,
                            "Verification email sent. Check your inbox.",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(
                            this,
                            "Failed to send verification email: ${task.exception?.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
        }
    }
}
