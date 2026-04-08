package com.jntuh.capfit.ui.profile

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import android.view.View
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.widget.ImageViewCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.jntuh.capfit.adapter.SeasonAdapter
import com.jntuh.capfit.data.SeasonData
import com.jntuh.capfit.data.UserGameData
import com.jntuh.capfit.databinding.ActivityProfileBinding
import com.jntuh.capfit.databinding.DialogColorPickerBinding
import com.jntuh.capfit.databinding.DialogEditUsernameBinding
import com.jntuh.capfit.helper.ColorUtils
import com.jntuh.capfit.viewmodel.UserGameDataViewModel
import com.jntuh.capfit.viewmodel.SeasonViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlin.getValue

@AndroidEntryPoint
class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private lateinit var seasonAdapter: SeasonAdapter

    private val viewModel: UserGameDataViewModel by viewModels()
    private val seasonViewModel: SeasonViewModel by viewModels()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE

        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)


        binding.btnBack.setOnClickListener {
            finish()
        }

        observeViewModel()
    }

    @SuppressLint("NewApi")
    private fun observeViewModel() {

        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {

                launch {
                    viewModel.userGameData.collect { data ->
                        data?.let { bindUserGameData(it) }
                    }
                }

                launch {
                    viewModel.loading.collect { isLoading ->
                        // optional loader UI later
                    }
                }


                launch {
                    seasonViewModel.seasons.collect { data ->
                        data?.let { bindSeasonData(it) }
                    }
                }
            }
        }
    }


    private fun bindUserGameData(data: UserGameData) {

        if (data.userName.isBlank()) {
            binding.tvUserName.text ="Set your username"
        } else {
            binding.tvUserName.text = data.userName
        }

        val colorInt = Color.parseColor(data.favoriteColor)

        binding.viewFavoriteColor.setBackgroundColor(colorInt)

        binding.cardProfileHeader.setCardBackgroundColor(
            ColorUtils.withAlpha(colorInt, 0.12f)
        )

        binding.tvHighestDistance.text = data.highestDistanceCovered.toString()
        binding.tvHighestArea.text = data.highestAreaCovered.toString()
        binding.tvCurrentStreak.text = "${data.highestStreak}"
        binding.tvAchievementsCount.text = data.achievements.size.toString()

        binding.rowEditUsername.setOnClickListener {
            showEditUsernameDialog(data)
        }

        binding.rowEditColor.setOnClickListener {
            showColorPickerDialog(data)

        }
    }


    private fun bindSeasonData(data: List<SeasonData>){
        setupSeasonList(data ?: emptyList())
    }

    private fun setupSeasonList(seasons: List<SeasonData>) {
        seasonAdapter = SeasonAdapter(seasons)

        binding.rvSeasons.apply {

            isNestedScrollingEnabled = false
            setHasFixedSize(false)
            layoutManager = LinearLayoutManager(this@ProfileActivity)
            adapter = seasonAdapter

        }

        binding.rvSeasons.post {
            binding.rvSeasons.requestLayout()
        }
    }

    private fun showEditUsernameDialog(currentData: UserGameData) {

        val dialogBinding = DialogEditUsernameBinding.inflate(layoutInflater)
        dialogBinding.etUsername.setText(currentData.userName)

        AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setPositiveButton("Save") { _, _ ->

                val newName = dialogBinding.etUsername.text.toString().trim()

                if (newName.isNotEmpty() && newName != currentData.userName) {

                    val updated = currentData.copy(userName = newName)

                    viewModel.updateUserGameData(updated)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showColorPickerDialog(currentData: UserGameData) {

        val dialogBinding = DialogColorPickerBinding.inflate(layoutInflater)

        val colors = listOf(
            "#3F51B5", "#2196F3", "#03A9F4", "#00BCD4",
            "#4CAF50", "#8BC34A", "#CDDC39", "#FFEB3B",
            "#FF9800", "#FF5722", "#E91E63", "#9C27B0"
        )

        val views = listOf(
            dialogBinding.color1,  dialogBinding.color2,
            dialogBinding.color3,  dialogBinding.color4,
            dialogBinding.color5,  dialogBinding.color6,
            dialogBinding.color7,  dialogBinding.color8,
            dialogBinding.color9,  dialogBinding.color10,
            dialogBinding.color11, dialogBinding.color12
        )

        views.forEachIndexed { index, view ->
            val colorHex = colors[index]
            view.setBackgroundColor(Color.parseColor(colorHex))
            view.setOnClickListener {
                val updated = currentData.copy(favoriteColor = colorHex)
                viewModel.updateUserGameData(updated)

                // Update accent color on profile header immediately
                val colorInt = Color.parseColor(colorHex)
                binding.viewFavoriteColor.setBackgroundColor(colorInt)
                binding.cardProfileHeader.setCardBackgroundColor(
                    ColorUtils.withAlpha(colorInt, 0.12f)
                )
                ImageViewCompat.setImageTintList(
                    binding.imageMenuEdit,
                    ColorStateList.valueOf(colorInt)
                )
                ImageViewCompat.setImageTintList(
                    binding.imageMenuEditt,
                    ColorStateList.valueOf(colorInt)
                )
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Choose Favorite Color")
            .setView(dialogBinding.root)
            .setNegativeButton("Cancel", null)
            .show()
    }
}