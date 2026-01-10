package com.jntuh.capfit.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.jntuh.capfit.databinding.ActivityBaseBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
abstract class BaseActivity : AppCompatActivity() {

    protected lateinit var baseBinding: ActivityBaseBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        baseBinding = ActivityBaseBinding.inflate(layoutInflater)
        setContentView(baseBinding.root)

        setupDrawer()
    }

    private fun setupDrawer() {
        val drawer = baseBinding.drawerLayout
        drawer.setScrimColor(Color.TRANSPARENT)
        drawer.setDrawerElevation(0f)

        drawer.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                baseBinding.childContainer.translationX =
                    drawerView.width * slideOffset
            }
            override fun onDrawerOpened(drawerView: View) {}
            override fun onDrawerClosed(drawerView: View) {}
            override fun onDrawerStateChanged(newState: Int) {}
        })

        baseBinding.menuCloseButtonInMenu.setOnClickListener {
            drawer.closeDrawer(GravityCompat.START)
        }
    }

    fun openDrawer() {
        baseBinding.drawerLayout.openDrawer(GravityCompat.START)
    }

    fun setChildLayout(layoutId: Int) {
        val view = LayoutInflater.from(this)
            .inflate(layoutId, baseBinding.childContainer, false)
        baseBinding.childContainer.addView(view)
    }
}
