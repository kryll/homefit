package com.kryll.homefit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.kryll.homefit.ui.HomeFitRoot
import com.kryll.homefit.ui.theme.HomeFitTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HomeFitTheme { HomeFitRoot() }
        }
    }
}
