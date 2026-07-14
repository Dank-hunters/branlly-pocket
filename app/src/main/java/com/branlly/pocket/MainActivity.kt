package com.branlly.pocket

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.branlly.pocket.ui.BranllyPocketApp
import com.branlly.pocket.ui.theme.BranllyPocketTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BranllyPocketTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BranllyPocketApp()
                }
            }
        }
    }
}
