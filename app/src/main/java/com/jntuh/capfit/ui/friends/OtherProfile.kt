package com.jntuh.capfit.ui.friends

import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.jntuh.capfit.R
import com.jntuh.capfit.adapter.SeasonAdapter
import com.jntuh.capfit.data.SeasonData
import com.jntuh.capfit.data.UserGameData
import com.jntuh.capfit.databinding.ActivityOtherProfileBinding
import com.jntuh.capfit.viewmodel.UserGameDataViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class OtherProfile : AppCompatActivity() {

    private lateinit var binding: ActivityOtherProfileBinding
    private val userGameDataViewModel: UserGameDataViewModel by viewModels()
    private lateinit var seasonAdapter: SeasonAdapter
    @Inject lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityOtherProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        val userGameData =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra("user_game_data", UserGameData::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<UserGameData>("user_game_data")
            }

        if (userGameData != null) {

            val name = userGameData.userName ?: "User"
            binding.txtUserName.text = name

            val seasonsQuery = firestore
                .collection("users")
                .document(userGameData.uid)
                .collection("seasons")
                .orderBy("seasonYear", Query.Direction.DESCENDING)
                .orderBy("seasonMonth", Query.Direction.DESCENDING)

            seasonsQuery.get().addOnSuccessListener { result ->
                val seasonsList = result.documents.mapNotNull {
                    it.toObject(SeasonData::class.java)
                }
                bindSeasonData(seasonsList)
            }

            val userDocRef = firestore
                .collection("users")
                .document(userGameData.uid)

            userDocRef.get().addOnSuccessListener { document ->

                val photo = document.data?.get("profilePicture")

                if (photo != null) {
                    binding.profileImage.visibility = View.VISIBLE
                    binding.profileLetter.visibility = View.GONE

                    Glide.with(this@OtherProfile)
                        .load(photo)
                        .placeholder(R.drawable.profile_circle_bg)
                        .error(R.drawable.profile_circle_bg)
                        .circleCrop()
                        .into(binding.profileImage)

                } else {
                    binding.profileLetter.visibility = View.VISIBLE

                    binding.profileLetter.text = name.first().uppercase()

                    binding.profileLetter.setBackgroundResource(R.drawable.profile_circle_bg)

                    val colors = listOf(
                        "#F28B82", // soft red
                        "#FBBC04", // yellow
                        "#FFF475", // light yellow
                        "#CCFF90", // light green
                        "#A7FFEB", // teal
                        "#CBF0F8", // sky blue
                        "#AECBFA", // soft blue
                        "#D7AEFB", // purple
                        "#FDCFE8", // pink
                        "#E6C9A8", // brown
                        "#E8EAED", // grey
                        "#D1C4E9"  // lavender
                    )

                    val index = kotlin.math.abs(name.hashCode()) % colors.size
                    val color = colors[index]

                    binding.profileLetter.backgroundTintList =
                        ColorStateList.valueOf(color.toColorInt())
                }
            }
            bindUserGameData(userGameData)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                userGameDataViewModel.userGameData.collect { data ->

                    if (userGameData != null) {

                        val isFriend =
                            data?.friendsList?.contains(userGameData.uid) ?: false

                        if (isFriend) {
                            DeleteButtonSetup(userGameData)
                        } else {
                            AddButtonSetup(userGameData)
                        }
                    }
                }
            }
        }

        binding.apply {
            btnBack.setOnClickListener {
                finish()
            }
        }
    }

    private fun bindUserGameData(data: UserGameData){
        binding.apply {
            if (data.userName != ""){
                txtUserName.setText(data.userName)
            } else{

                txtUserName.setText("Unknown")
            }

            binding.tvHighestDistance.text = data.highestDistanceCovered.toString()
            binding.tvHighestArea.text = data.highestAreaCovered.toString()
            binding.tvCurrentStreak.text = "${data.highestStreak}"
            binding.tvAchievementsCount.text = data.achievements.size.toString()

        }
    }

    private fun bindSeasonData(data: List<SeasonData>){
        setupSeasonList(data ?: emptyList())
    }

    private fun setupSeasonList(seasons: List<SeasonData>) {
        seasonAdapter = SeasonAdapter(seasons)

        binding.rvSeasons.apply {
            layoutManager = LinearLayoutManager(this@OtherProfile)
            adapter = seasonAdapter
            setHasFixedSize(true)
        }
    }

    private fun AddButtonSetup(userGameData: UserGameData) {
        binding.apply {

            friendButton.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this@OtherProfile, R.color.green) // darker green
            )

            friendButton.setTextColor(
                ContextCompat.getColor(this@OtherProfile, android.R.color.white)
            )
            friendButton.text = "Add Friend"

            friendButton.setOnClickListener {
                userGameDataViewModel.sendFriendRequest(userGameData.uid)
                friendButton.isEnabled = false
                Toast.makeText(
                    this@OtherProfile,
                    "Friend Request Sent",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun DeleteButtonSetup(userGameData: UserGameData) {
        binding.apply {
            friendButton.backgroundTintList = ColorStateList.valueOf(
                "#E53935".toColorInt() // strong red
            )

            friendButton.setTextColor(
                "#FFFFFF".toColorInt()
            )
            friendButton.text = "Remove Friend"

            friendButton.setOnClickListener {
                userGameDataViewModel.removeFriend(userGameData.uid)
                AddButtonSetup(userGameData)
            }
        }
    }
}