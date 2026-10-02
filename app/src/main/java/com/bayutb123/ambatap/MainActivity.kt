package com.bayutb123.ambatap

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.bayutb123.ambatap.ui.navigation.AmbaTapNavHost
import com.bayutb123.ambatap.ui.theme.AmbaTapTheme
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
