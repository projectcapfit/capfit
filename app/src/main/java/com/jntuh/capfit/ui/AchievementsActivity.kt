package com.jntuh.capfit.ui

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.jntuh.capfit.R
import com.jntuh.capfit.ui.achievements.AchievementsFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AchievementsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_achievements)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, AchievementsFragment())
                .commit()
        }
    }
}
