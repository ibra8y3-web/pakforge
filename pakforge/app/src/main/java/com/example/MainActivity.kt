package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.MainViewModel
import com.example.ui.PakForgeRoot
import com.example.ui.components.ProvideLocalizedContext
import com.example.ui.theme.PakForgeTheme

class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels { MainViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val preferences by mainViewModel.userPreferences.collectAsStateWithLifecycle()

            ProvideLocalizedContext(languageCode = preferences.languageCode) {
                PakForgeTheme(themeMode = preferences.themeMode) {
                    PakForgeRoot(viewModel = mainViewModel)
                }
            }
        }
    }
}
