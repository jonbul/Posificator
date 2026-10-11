package com.posificator.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.posificator.ui.App

class MainActivity : ComponentActivity() {
    private lateinit var platform: AndroidLocationPlatform

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        platform = AndroidLocationPlatform(this)
        val repository = placeRepository(this)
        setContent { App(repository, platform) }
    }

    override fun onResume() {
        super.onResume()
        // Picks up changes the user made in system settings.
        platform.refreshPermissions()
    }
}
