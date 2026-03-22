package com.jntuh.capfit.ui.home

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import androidx.recyclerview.widget.LinearLayoutManager
import com.jntuh.capfit.R
import com.jntuh.capfit.adapter.WorkoutAdapter
import com.jntuh.capfit.data.TrackingSession
import com.jntuh.capfit.databinding.ActivityHomeChildBinding
import com.jntuh.capfit.ui.Account.MyAccount
import com.jntuh.capfit.ui.AchievementsActivity
import com.jntuh.capfit.ui.authentication.Login
import com.jntuh.capfit.ui.BaseActivity
import com.jntuh.capfit.ui.friends.Friends
import com.jntuh.capfit.ui.notification.Notification
import com.jntuh.capfit.ui.profile.ProfileActivity
import com.jntuh.capfit.ui.tracking.MapsActivity
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

    private lateinit var workoutAdapter: WorkoutAdapter

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
            userGameViewModel.clearCache()
            seasonViewModel.clearCache()
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

        baseBinding.gotoFriends.setOnClickListener {
            startActivity(Intent(this , Friends::class.java))
        }

        binding.imgNotification.setOnClickListener {
            startActivity(Intent(this, Notification::class.java))
        }

        setupGreeting()
        observeUserProfile()
        observeUserGameData()
        observeSeasonData()
        checkStreakOnOpen()
        listenUnreadNotifications()

        requestAllPermissions()
        temp()
        setupRecentActivity()

    }

    private fun temp(){
        binding.mapButton.setOnClickListener {
            startActivity(Intent(this, MapsActivity::class.java))
        }
    }

    private fun observeUserProfile() {
        lifecycleScope.launchWhenStarted {
            userViewModel.userState.collect { user ->
                if (user != null) {
                    // Photo only — name comes from UserGameData
                    val firestorePhoto = user.profilePicture
                        ?.takeIf { it.isNotBlank() && it != "null" }

                    val prefPhoto = getSharedPreferences("UserData", MODE_PRIVATE)
                        .getString("googlePhoto", null)
                        ?.takeIf { it.isNotBlank() && it != "null" }

                    val finalPhoto = firestorePhoto ?: prefPhoto

                    // Use gameUserName for letter avatar initial too
                    val displayName = userGameViewModel.userGameData.value
                        ?.userName?.takeIf { it.isNotBlank() }
                        ?: user.name
                        ?: auth.currentUser?.displayName
                        ?: "User"

                    Log.d("asasas", "Profile photo — firestorePhoto=$firestorePhoto prefPhoto=$prefPhoto → using=$finalPhoto displayName=$displayName")

                    setProfilePhoto(displayName, finalPhoto)
                }
            }
        }
    }

    private fun observeUserGameData() {
        lifecycleScope.launchWhenStarted {
            userGameViewModel.userGameData.collect { gameData ->
                if (gameData == null) return@collect

                // Game username — set by the player in profile screen
                val gameUserName = gameData.userName.takeIf { it.isNotBlank() }
                    ?: auth.currentUser?.displayName
                    ?: "User"

                Log.d("asasas", "UserGameData loaded — userName=$gameUserName")

                binding.userName.text = gameUserName
                baseBinding.userName2.text = gameUserName
            }
        }
    }


    @RequiresApi(Build.VERSION_CODES.O)
    private fun observeSeasonData() {
        lifecycleScope.launchWhenStarted {
            seasonViewModel.currentSeason.collect { season ->
                if (season == null) return@collect

                val distanceM = season.distanceCoveredInThisSeason
                binding.txtDistance.text = if (distanceM >= 1000)
                    String.format("%.1f km", distanceM / 1000.0)
                else
                    "$distanceM m"

                binding.txtArea.text = "${season.areaCoveredInThisSeason} m²"
                binding.txtWorkouts.text = season.numberOfWorkouts.toString()
                binding.txtActiveTime.text = season.totalTimePlayed.ifBlank { "0s" }
            }
        }
    }

    // ─── Recent Activity ──────────────────────────────────────────────────────

    private fun setupRecentActivity() {
        workoutAdapter = WorkoutAdapter()
        binding.RecentActivity.apply {
            layoutManager = LinearLayoutManager(this@HomePage)
            adapter = workoutAdapter
        }
        loadRecentSessions()
    }

    private fun loadRecentSessions() {
        val uid = auth.currentUser?.uid ?: return

        // Step 1: get session IDs from userSessionState/data
        firestore.collection("users")
            .document(uid)
            .collection("userSessionState")
            .document("data")
            .get()
            .addOnSuccessListener { stateDoc ->
                @Suppress("UNCHECKED_CAST")
                val sessionIds = stateDoc.get("sessions") as? List<String> ?: emptyList()

                if (sessionIds.isEmpty()) {
                    showNoActivity()
                    return@addOnSuccessListener
                }

                // Step 2: fetch each session doc
                // Firestore whereIn supports max 30 per query — chunk if needed
                val sessions = mutableListOf<TrackingSession>()
                val chunks = sessionIds.chunked(30)
                var completedChunks = 0

                for (chunk in chunks) {
                    firestore.collection("sessions")
                        .whereIn("sessionId", chunk)
                        .get()
                        .addOnSuccessListener { snap ->
                            val fetched = snap.documents.mapNotNull { doc ->
                                try {
                                    val sessionId = doc.getString("sessionId") ?: return@mapNotNull null
                                    val area      = doc.getDouble("area")      ?: 0.0
                                    val distance  = doc.getDouble("distance")  ?: 0.0
                                    val startTime = doc.getLong("startTime")   ?: 0L
                                    val endTime   = doc.getLong("endTime")     ?: 0L
                                    val date      = doc.getString("date")      ?: ""
                                    val userName  = doc.getString("userName")  ?: ""
                                    TrackingSession(
                                        sessionId = sessionId,
                                        userId    = uid,
                                        userName  = userName,
                                        startTime = startTime,
                                        endTime   = endTime,
                                        distance  = distance,
                                        area      = area,
                                        date      = date
                                    )
                                } catch (e: Exception) {
                                    null
                                }
                            }
                            sessions.addAll(fetched)
                            completedChunks++

                            if (completedChunks == chunks.size) {
                                // All chunks done — update UI
                                if (sessions.isEmpty()) {
                                    showNoActivity()
                                } else {
                                    showSessions(sessions)
                                }
                            }
                        }
                        .addOnFailureListener {
                            completedChunks++
                            if (completedChunks == chunks.size && sessions.isEmpty()) {
                                showNoActivity()
                            }
                        }
                }
            }
            .addOnFailureListener {
                showNoActivity()
            }
    }

    private fun showSessions(sessions: List<TrackingSession>) {
        binding.txtNoRecentActivity.visibility = View.GONE
        binding.RecentActivity.visibility = View.VISIBLE
        workoutAdapter.submitList(sessions)
    }

    private fun showNoActivity() {
        binding.txtNoRecentActivity.visibility = View.VISIBLE
        binding.RecentActivity.visibility = View.GONE
    }

    // Re-load recent sessions every time user comes back to HomePage
    // (e.g. after completing a workout in MapsActivity)
    override fun onResume() {
        super.onResume()

        // Refresh userGameData so userName + stats update immediately
        userGameViewModel.clearCache()
        userGameViewModel.loadUserGameData()

        // Refresh current season directly — bypasses list cache
        // ensures card stats update right after returning from a workout
        seasonViewModel.refreshCurrentSeason()

        if (::workoutAdapter.isInitialized) {
            loadRecentSessions()
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

    private fun checkStreakOnOpen() {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date())
        lifecycleScope.launchWhenStarted {
            userGameViewModel.checkAndResetStreak(today)
        }
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

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->

            var allGranted = true

            permissions.entries.forEach {
                if (!it.value) {
                    allGranted = false
                    Toast.makeText(this, "${it.key} denied", Toast.LENGTH_SHORT).show()
                }
            }

            if (allGranted) {
                Toast.makeText(this, "All permissions granted", Toast.LENGTH_SHORT).show()
            }
        }

    private fun requestAllPermissions() {

        val permissionsToRequest = mutableListOf<String>()

        if (!isPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (!isPermissionGranted(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (!isPermissionGranted(Manifest.permission.ACTIVITY_RECOGNITION)) {
                permissionsToRequest.add(Manifest.permission.ACTIVITY_RECOGNITION)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!isPermissionGranted(Manifest.permission.POST_NOTIFICATIONS)) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            Toast.makeText(this, "Permissions already granted", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isPermissionGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }
}