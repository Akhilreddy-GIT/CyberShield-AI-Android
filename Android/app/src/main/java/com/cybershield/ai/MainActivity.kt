package com.cybershield.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.cybershield.ai.presentation.navigation.CyberShieldNavHost
import com.cybershield.ai.presentation.theme.CyberShieldTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CyberShieldTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CyberShieldNavHost()
                }
            }
        }
    }
}
