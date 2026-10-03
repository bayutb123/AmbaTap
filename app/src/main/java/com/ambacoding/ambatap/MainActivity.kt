package com.ambacoding.ambatap

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ambacoding.ambatap.ui.navigation.AmbaTapNavHost
import com.ambacoding.ambatap.ui.theme.AmbaTapTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AmbaTapTheme {
                AmbaTapNavHost()
            }
        }
    }
}
