package com.kryll.homefit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.kryll.homefit.data.AppSettings
import com.kryll.homefit.data.ThemeMode
import com.kryll.homefit.ui.HomeFitRoot
import com.kryll.homefit.ui.theme.HomeFitTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val app = application as HomeFitApp
            val settings by app.container.settings.settings.collectAsState(initial = AppSettings())
            val dark = when (settings.theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            HomeFitTheme(darkTheme = dark) { HomeFitRoot() }
        }
    }
}
