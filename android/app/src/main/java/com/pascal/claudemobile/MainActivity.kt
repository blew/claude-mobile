package com.pascal.claudemobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.pascal.claudemobile.data.Logger
import com.pascal.claudemobile.ui.ChatScreen
import com.pascal.claudemobile.ui.LogScreen
import com.pascal.claudemobile.ui.SettingsScreen
import com.pascal.claudemobile.ui.theme.ClaudeMobileTheme

private enum class Screen { Chat, Settings, Log }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Logger.init(applicationContext)
        Logger.log("APP", "MainActivity onCreate")
        enableEdgeToEdge()
        setContent {
            ClaudeMobileTheme {
                var screen by remember { mutableStateOf(Screen.Chat) }
                when (screen) {
                    Screen.Chat -> ChatScreen(onOpenSettings = { screen = Screen.Settings })
                    Screen.Settings -> SettingsScreen(
                        onBack = { screen = Screen.Chat },
                        onOpenLog = { screen = Screen.Log },
                    )
                    Screen.Log -> LogScreen(onBack = { screen = Screen.Settings })
                }
            }
        }
    }
}
