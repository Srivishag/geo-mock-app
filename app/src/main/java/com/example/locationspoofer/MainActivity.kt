package com.example.locationspoofer

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.locationspoofer.theme.LocationSpooferTheme
import com.example.locationspoofer.ui.MainScreen
import org.osmdroid.config.Configuration

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure osmdroid globally BEFORE inflating or composing MapView
        // OpenStreetMap tile servers require an identifiable User-Agent in accordance with their Tile Usage Policy.
        val osmConfig = Configuration.getInstance()
        val prefs = getSharedPreferences("osmdroid_prefs", Context.MODE_PRIVATE)
        osmConfig.load(this, prefs)

        val validUserAgent = "LocationSpoofer/1.0 (Android; dev@locationspoofer.org)"
        osmConfig.userAgentValue = validUserAgent
        osmConfig.save(this, prefs)

        enableEdgeToEdge()
        setContent {
            LocationSpooferTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }
}
