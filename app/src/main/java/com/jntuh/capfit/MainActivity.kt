package com.jntuh.capfit

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.jntuh.capfit.databinding.ActivityMainBinding
import com.jntuh.capfit.repository.SeasonDataManager
import com.jntuh.capfit.repository.UserGameDataManager
import com.jntuh.capfit.repository.UserManager
import com.jntuh.capfit.ui.authentication.Login
import com.jntuh.capfit.ui.home.HomePage
import com.jntuh.capfit.ui.introduction.Introduction1
import com.jntuh.capfit.viewmodel.UserViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.getValue

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    @Inject lateinit var sharedPreferences: SharedPreferences
    @Inject lateinit var auth: FirebaseAuth

    private val userViewModel: UserViewModel by viewModels()
    @Inject lateinit var seasonDataManager: SeasonDataManager
    @Inject lateinit var userGameDataManager: UserGameDataManager
    @Inject lateinit var userManager : UserManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val savedWidth = sharedPreferences.getInt("drawer_width", -1)
        if (savedWidth == -1) {
            val screenWidth = resources.displayMetrics.widthPixels
            val drawerWidth = (screenWidth * 0.56).toInt()
            sharedPreferences.edit().putInt("drawer_width", drawerWidth).apply()
        }

        if (!sharedPreferences.contains("first_time")) {
            sharedPreferences.edit().putBoolean("first_time", false).apply()
            startActivity(Intent(this, Introduction1::class.java))
            finish()
            return
        }

        if (auth.currentUser == null) {
            startActivity(Intent(this, Login::class.java))
            finish()
            return
        }
        lifecycleScope.launch {
            try {
                val seasonDeferred = async {
                    seasonDataManager.getOrCreateCurrentSeason()
                }

                val userDeferred = async {
                    userGameDataManager.getUserGameData()
                }

                val user =  async{ userManager.getCurrentUser() }

                seasonDeferred.await()
                userDeferred.await()
                user.await()

            } catch (e : Exception){
                e.printStackTrace()
            }
        }
        startActivity(Intent(this@MainActivity, HomePage::class.java))
        finish()
    }
}
