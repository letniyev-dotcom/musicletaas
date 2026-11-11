package com.moyavolna.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.moyavolna.app.ui.MoyaVolnaRoot

class MainActivity : ComponentActivity() {

    private val permissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        askPermissions()
        setContent { MoyaVolnaRoot() }
    }

    private fun askPermissions() {
        val want = mutableListOf<String>()
        // RECORD_AUDIO lets the Visualizer drive the rhythm-reactive «волна».
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) want += Manifest.permission.RECORD_AUDIO
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) want += Manifest.permission.POST_NOTIFICATIONS
        if (want.isNotEmpty()) permissions.launch(want.toTypedArray())
    }
}
