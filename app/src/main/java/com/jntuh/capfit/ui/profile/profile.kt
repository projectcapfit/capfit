package com.jntuh.capfit.ui.profile

import android.graphics.Color
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.jntuh.capfit.data.SeasonData
import com.jntuh.capfit.data.UserGameData
import com.jntuh.capfit.databinding.ActivityProfileBinding
import com.jntuh.capfit.adapter.SeasonAdapter
import com.jntuh.capfit.helper.ColorUtils
import androidx.appcompat.app.AlertDialog
import com.jntuh.capfit.databinding.DialogEditUsernameBinding
import com.jntuh.capfit.databinding.DialogColorPickerBinding


class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private lateinit var seasonAdapter: SeasonAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Handle edge-to-edge properly
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupDummyData() // REMOVE later – only for testing UI
    }

    /**
     * Temporary method just to test UI.
     * Replace this with ViewModel / Repository data later.
     */
    private fun setupDummyData() {

        // ---------- USER GAME DATA ----------
        val userGameData = UserGameData(
            uid = "test_uid",
            userName = "Sathvik",
            favoriteColor = "#3F51B5",
            highestDistanceCovered = 1200,
            highestAreaCovered = 450,
            highestScore = 3200,
            currentStreak = 5,
            achievements = listOf(1, 2, 3, 4),
            friendsList = listOf("u1", "u2")
        )

        bindUserGameData(userGameData)

        // ---------- SEASON DATA ----------
        val seasons = listOf(
            SeasonData(
                uid = "test_uid",
                seasonYear = "2025",
                seasonMonth = "12",
                distanceCoveredInThisSeason = 900,
                areaCoveredInThisSeason = 300,
                seasonScore = 2500,
                seasonRank = 8
            ),
            SeasonData(
                uid = "test_uid",
                seasonYear = "2026",
                seasonMonth = "01",
                distanceCoveredInThisSeason = 1200,
                areaCoveredInThisSeason = 450,
                seasonScore = 3200,
                seasonRank = -1
            )
        )

        setupSeasonList(seasons)
    }

    // ---------------- USER GAME DATA BINDING ----------------

    private fun bindUserGameData(data: UserGameData) {

        binding.tvUserName.text =
            if (data.userName.isBlank()) "Set your username" else data.userName

        // Favorite color
        val colorInt = Color.parseColor(data.favoriteColor)
        binding.viewFavoriteColor.setBackgroundColor(colorInt)
        binding.cardProfileHeader.setCardBackgroundColor(
            ColorUtils.withAlpha(colorInt, 0.12f)
        )

        // Stats
        binding.tvHighestDistance.text = data.highestDistanceCovered.toString()
        binding.tvHighestArea.text = data.highestAreaCovered.toString()
        binding.tvHighestScore.text = data.highestScore.toString()
        binding.tvCurrentStreak.text = data.currentStreak.toString()
        binding.tvAchievementsCount.text = data.achievements.size.toString()
        binding.tvFriendsCount.text = data.friendsList.size.toString()

        binding.btnEditUsername.setOnClickListener {
            showEditUsernameDialog(binding.tvUserName.text.toString())
        }
        binding.viewFavoriteColor.setOnClickListener {
            showColorPickerDialog()
        }

    }


    private fun setupSeasonList(seasons: List<SeasonData>) {

        // 🔥 IMPORTANT: reverse so latest season is on top
        val reversedSeasons = seasons
            .sortedWith(compareByDescending<SeasonData> {
                "${it.seasonYear}${it.seasonMonth}"
            })

        seasonAdapter = SeasonAdapter(reversedSeasons)

        binding.rvSeasons.apply {
            layoutManager = LinearLayoutManager(this@ProfileActivity)
            adapter = seasonAdapter
            setHasFixedSize(true)
        }
    }

    private fun showEditUsernameDialog(currentName: String) {

        val dialogBinding = DialogEditUsernameBinding.inflate(layoutInflater)
        dialogBinding.etUsername.setText(currentName)

        AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setPositiveButton("Save") { _, _ ->
                val newName = dialogBinding.etUsername.text.toString().trim()
                if (newName.isNotEmpty()) {
                    binding.tvUserName.text = newName
                    // TODO: save to Firestore
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    private fun showColorPickerDialog() {

        val dialogBinding = DialogColorPickerBinding.inflate(layoutInflater)

        val colors = listOf(
            "#3F51B5", "#2196F3", "#4CAF50", "#FF9800"
        )

        val colorViews = listOf(
            dialogBinding.color1,
            dialogBinding.color2,
            dialogBinding.color3,
            dialogBinding.color4
        )

        colorViews.forEachIndexed { index, view ->
            val color = Color.parseColor(colors[index])
            view.setBackgroundColor(color)

            view.setOnClickListener {
                applyFavoriteColor(color)
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Choose Favorite Color")
            .setView(dialogBinding.root)
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun applyFavoriteColor(color: Int) {

        binding.viewFavoriteColor.setBackgroundColor(color)

        binding.cardProfileHeader.setCardBackgroundColor(
            Color.argb(30, Color.red(color), Color.green(color), Color.blue(color))
        )

        // TODO: save hex color to Firestore
    }


}
