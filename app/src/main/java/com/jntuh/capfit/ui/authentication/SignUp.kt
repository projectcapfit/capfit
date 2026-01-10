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
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.userProfileChangeRequest
import com.jntuh.capfit.R
import com.jntuh.capfit.databinding.ActivitySignUpBinding
import com.jntuh.capfit.ui.home.HomePage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SignUp : AppCompatActivity() {

    private lateinit var binding: ActivitySignUpBinding

    @Inject
    lateinit var auth : FirebaseAuth

    private lateinit var credentialManager: CredentialManager

    override fun onStart() {
        super.onStart()
        val data = intent?.data
        if (data != null) {
            val oobCode = data.getQueryParameter("oobCode")
            if (oobCode != null) {
                FirebaseAuth.getInstance().applyActionCode(oobCode)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Toast.makeText(this, "Email verified!", Toast.LENGTH_SHORT).show()
                            startActivity(Intent(this, Login::class.java))
                            finish()
                        } else {
                            Toast.makeText(
                                this,
                                "Verification failed: ${task.exception?.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySignUpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        if (savedInstanceState != null) {
            val savedEmail = savedInstanceState.getString("email_text")
            val savedPassword = savedInstanceState.getString("password_text")

            binding.editTextEmail.setText(savedEmail)
            binding.editTextPassword.setText(savedPassword)
            binding.editTextName.setText(savedEmail)
            binding.editTextPhone.setText(savedPassword)
        }

        binding.backButton.setOnClickListener { finish() }
        binding.signUpWithGoogle.setOnClickListener { signInWithGoogle() }
        binding.goToLogin.setOnClickListener {
            startActivity(Intent(this, Login::class.java))
            finish()
        }
        binding.buttonCreateAccount.setOnClickListener {
            registerUser(
                binding.editTextName.text.toString().trim(),
                binding.editTextEmail.text.toString().trim(),
                binding.editTextPassword.text.toString().trim()
            )
        }

        binding.backButton.setOnClickListener {
            finish()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("email", binding.editTextEmail.text.toString())
        outState.putString("password", binding.editTextPassword.text.toString())
        outState.putString("name", binding.editTextName.text.toString())
        outState.putString("phone", binding.editTextPhone.text.toString())
    }

    private fun signInWithGoogle() {
        credentialManager = CredentialManager.create(baseContext)
        launchCredentialManager()
        Toast.makeText(this , "pressed" , Toast.LENGTH_SHORT).show()
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
                    context = this@SignUp,
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
                if (task.isSuccessful) {
                    Log.d(TAG, "signInWithCredential:success")
                    updateUI()
                } else {
                    Log.w(TAG, "signInWithCredential:failure", task.exception)
                    Toast.makeText(baseContext, "Authentication failed.", Toast.LENGTH_SHORT).show()
                }
            }
    }

    companion object { private const val TAG = "tagy" }

    private fun updateUI(){
        startActivity(Intent(this, HomePage::class.java))
        finish()
    }

    private fun registerUser(name: String, email: String, password: String) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    val profileUpdates = userProfileChangeRequest { displayName = name }
                    user?.updateProfile(profileUpdates)?.addOnCompleteListener { updateTask ->
                        if (updateTask.isSuccessful) {
                            sendEmailVerification(auth)
                        }
                        else Toast.makeText(
                            this,
                            "Failed to update profile: ${updateTask.exception?.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    Toast.makeText(
                        this,
                        "Registration failed: ${task.exception?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    private fun sendEmailVerification(auth: FirebaseAuth) {
        val user = auth.currentUser
        user?.let {
            val actionCodeSettings = ActionCodeSettings.newBuilder()
                .setUrl("https://capfit-635ff.web.app") // Use a valid HTTPS URL
                .setHandleCodeInApp(true)
                .setAndroidPackageName(
                    "com.jntuh.capfit",
                    true,
                    null
                )
                .build()

            user.sendEmailVerification(actionCodeSettings)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(this, "Verification email sent. Check your inbox.", Toast.LENGTH_LONG).show()
                        auth.signOut()
                        startActivity(Intent(this, Login::class.java))
                        finish()
                    } else {
                        Toast.makeText(this, "Failed to send verification email: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
        }
    }
}
