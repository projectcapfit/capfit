package com.jntuh.capfit.ui.achievements

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.jntuh.capfit.databinding.DialogAchievementUnlockedBinding

class AchievementUnlockedDialog(private val achievementName: String) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogAchievementUnlockedBinding.inflate(layoutInflater)
        binding.title.text = achievementName
        binding.lottie.playAnimation()

        return AlertDialog.Builder(requireContext())
            .setView(binding.root)
            .create()
    }
}
