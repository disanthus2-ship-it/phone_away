package com.phoneaway.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.phoneaway.core.AwayMode
import com.phoneaway.ui.theme.PhoneAwayTheme

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            PhoneAwayTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    PhoneAwayApp(
                        onKeepScreenOn = ::setKeepScreenOn,
                    )
                }
            }
        }
    }

    /**
     * App Open mode needs the display to stay awake -- the session is defined by
     * this screen staying up, so letting the phone sleep would be a trap.
     */
    private fun setKeepScreenOn(keepOn: Boolean) {
        if (keepOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

private enum class Screen { HOME, HISTORY }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneAwayApp(
    onKeepScreenOn: (Boolean) -> Unit,
    viewModel: MainViewModel = viewModel(),
) {
    val context = LocalContext.current
    val active by viewModel.activeSession.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val result by viewModel.lastResult.collectAsStateWithLifecycle()

    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    var selectedMode by rememberSaveable { mutableStateOf(AwayMode.SCREEN_LOCKED) }
    var selectedMinutes by rememberSaveable { mutableStateOf<Long?>(30L) }

    // Only App Open mode depends on the screen staying lit.
    LaunchedEffect(active.isActive, active.mode) {
        onKeepScreenOn(active.isActive && active.mode == AwayMode.APP_OPEN)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            active.isActive -> "Session in progress"
                            screen == Screen.HISTORY -> "Your history"
                            else -> "Phone Away"
                        },
                    )
                },
                navigationIcon = {
                    if (screen == Screen.HISTORY && !active.isActive) {
                        IconButton(onClick = { screen = Screen.HOME }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
            )
        },
    ) { padding ->
        val content = Modifier
            .fillMaxSize()
            .padding(padding)

        when {
            active.isActive -> ActiveSessionScreen(
                state = active,
                onStop = { viewModel.stopSession(context) },
                modifier = content,
            )

            screen == Screen.HISTORY -> HistoryScreen(
                stats = stats,
                sessions = sessions,
                modifier = content,
            )

            else -> HomeScreen(
                stats = stats,
                selectedMode = selectedMode,
                selectedMinutes = selectedMinutes,
                onModeChange = { selectedMode = it },
                onMinutesChange = { selectedMinutes = it },
                onStart = {
                    viewModel.startSession(context, selectedMode, selectedMinutes?.times(60))
                },
                onShowHistory = { screen = Screen.HISTORY },
                modifier = content,
            )
        }
    }

    result?.let { session ->
        ResultDialog(session = session, onDismiss = viewModel::dismissResult)
    }
}
