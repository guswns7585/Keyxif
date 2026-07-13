package com.keyxif.app

import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keyxif.app.ui.KeyxifApp
import com.keyxif.app.ui.KeyxifViewModel
import com.keyxif.app.ui.theme.KeyxifTheme
import com.keyxif.app.util.IntentShareUtils

class MainActivity : ComponentActivity() {
    private lateinit var keyxifViewModel: KeyxifViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        keyxifViewModel = ViewModelProvider(
            this,
            KeyxifViewModelFactory(application),
        )[KeyxifViewModel::class.java]
        handleShareIntent(intent)
        setContent {
            val state by keyxifViewModel.uiState.collectAsStateWithLifecycle()
            KeyxifTheme(themeMode = state.settings.themeMode) {
                KeyxifApp(viewModel = keyxifViewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        val uris = IntentShareUtils.extractImageUris(intent, contentResolver)
        if (intent?.action == Intent.ACTION_SEND || intent?.action == Intent.ACTION_SEND_MULTIPLE) {
            keyxifViewModel.addSharedImages(uris)
        }
    }
}

private class KeyxifViewModelFactory(
    private val application: Application,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return KeyxifViewModel(application) as T
    }
}
