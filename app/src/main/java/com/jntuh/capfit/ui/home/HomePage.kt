package com.jntuh.capfit.ui.home

import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.core.graphics.toColorInt
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jntuh.capfit.R
import com.jntuh.capfit.data.Achievement
import com.jntuh.capfit.databinding.ActivityHomeChildBinding
import com.jntuh.capfit.ui.Account.MyAccount
import com.jntuh.capfit.ui.AchievementsActivity
import com.jntuh.capfit.ui.authentication.Login
import com.jntuh.capfit.ui.BaseActivity
import com.jntuh.capfit.ui.notification.Notification
import com.jntuh.capfit.ui.profile.ProfileActivity
import com.jntuh.capfit.viewmodel.SeasonViewModel
import com.jntuh.capfit.viewmodel.UserGameDataViewModel
import com.jntuh.capfit.viewmodel.UserViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.math.absoluteValue

@AndroidEntryPoint
class HomePage : BaseActivity() {

    private lateinit var binding: ActivityHomeChildBinding
    @Inject lateinit var firestore: FirebaseFirestore
    @Inject lateinit var auth: FirebaseAuth
    @Inject lateinit var sharedPreferences: SharedPreferences

    private val userViewModel: UserViewModel by viewModels()
    private val userGameViewModel: UserGameDataViewModel by viewModels()
    private val seasonViewModel: SeasonViewModel by viewModels()

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setChildLayout(R.layout.activity_home_child)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE

        binding = ActivityHomeChildBinding.bind(baseBinding.childContainer.getChildAt(0))
        binding.menuButton.setOnClickListener { openDrawer() }

        baseBinding.signOut.setOnClickListener {
            userViewModel.signOut()
            startActivity(Intent(this, Login::class.java))
            finish()
        }

        baseBinding.myAccount.setOnClickListener {
            startActivity(Intent(this, MyAccount::class.java))
        }

        baseBinding.gotoProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        baseBinding.goToDashboard.setOnClickListener {
            startActivity(Intent(this, DashBoard::class.java))
        }

        baseBinding.goToLeaderboard.setOnClickListener {
            startActivity(Intent(this, Leaderboard::class.java))
        }

        baseBinding.goToAchievements.setOnClickListener {
            startActivity(Intent(this , AchievementsActivity::class.java))
        }

        binding.imgNotification.setOnClickListener {
            startActivity(Intent(this, Notification::class.java))
        }

        setupGreeting()
        observeUserProfile()
        observeUserGameData()
        observeSeasonData()
        listenUnreadNotifications()
    }

    private fun observeUserProfile() {
        lifecycleScope.launchWhenStarted {
            userViewModel.userState.collect { user ->
                if (user != null) {
                    val photo = getSharedPreferences("UserData", MODE_PRIVATE)
                        .getString("googlePhoto", null)

                    val name = user.name
                        ?: auth.currentUser?.displayName
                        ?: "User"

                    binding.userName.text = name
                    baseBinding.userName2.text = name

                    setProfilePhoto(name, photo)
                }
            }
        }
    }

    private fun observeUserGameData() {
        lifecycleScope.launchWhenStarted {
            userGameViewModel.userGameData.collect { }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun observeSeasonData() {
        lifecycleScope.launchWhenStarted {
            seasonViewModel.currentSeason.collect { }
        }
    }

    private fun setProfilePhoto(name: String?, url: String?) {
        val initial = name.orEmpty().first().uppercase()

        if (url != null) {
            binding.profileImage.visibility = View.VISIBLE
            binding.profileLetter.visibility = View.GONE
            Glide.with(this).load(url).circleCrop().into(binding.profileImage)

            baseBinding.profileImage1.visibility = View.VISIBLE
            baseBinding.profileLetter2.visibility = View.GONE
            Glide.with(this).load(url).circleCrop().into(baseBinding.profileImage1)
            return
        }

        val colors = listOf(
            "#F44336","#E91E63","#9C27B0","#673AB7","#3F51B5","#2196F3",
            "#03A9F4","#00BCD4","#009688","#4CAF50","#8BC34A","#CDDC39",
            "#FFC107","#FF9800","#FF5722"
        )

        val chosen = colors[name.hashCode().absoluteValue % colors.size]

        binding.profileLetter.text = initial.toString()
        baseBinding.profileLetter2.text = initial.toString()

        (binding.profileLetter.background as android.graphics.drawable.GradientDrawable)
            .setColor(chosen.toColorInt())
        (baseBinding.profileLetter2.background as android.graphics.drawable.GradientDrawable)
            .setColor(chosen.toColorInt())
    }

    private fun setupGreeting() {
        val hr = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val msg = when (hr) {
            in 3..11 -> "Good Morning"
            in 12..16 -> "Good Afternoon"
            in 17..20 -> "Good Evening"
            else -> "Time to sleep"
        }
        binding.greetings.text = "Hello, $msg"
    }

    private fun listenUnreadNotifications() {
        val uid = auth.currentUser?.uid ?: return

        firestore.collection("users")
            .document(uid)
            .collection("notifications")
            .whereEqualTo("isRead", false)
            .addSnapshotListener { snap, error ->

                if (error != null || snap == null) return@addSnapshotListener

                val hasUnread = snap.size() > 0

                binding.badgeDot.visibility =
                    if (hasUnread) View.VISIBLE else View.GONE
            }
    }
}
