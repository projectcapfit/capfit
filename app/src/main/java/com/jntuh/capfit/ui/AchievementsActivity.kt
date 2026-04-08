package com.jntuh.capfit.ui

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.ImageView
import com.jntuh.capfit.R
import com.jntuh.capfit.ui.achievements.AchievementsFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AchievementsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        window.decorView.systemUiVisibility =
            android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE


        setContentView(R.layout.activity_achievements)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, AchievementsFragment())
                .commit()
        }
        val backbtn = findViewById<ImageView>(R.id.btnBack)

        backbtn.setOnClickListener {
            finish()
        }

    }
}
