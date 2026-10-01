package com.tuan.mylauncher

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.mutableIntStateOf

class MainActivity : ComponentActivity() {

    // Tăng lên mỗi lần người dùng bấm nút Home khi đang ở launcher
    private val homePressed = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        // Launcher không được thoát khi bấm Back
        onBackPressedDispatcher.addCallback(this) { }

        val repo = AppRepository(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                LauncherScreen(repo = repo, homeSignal = homePressed.intValue)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        homePressed.intValue++
    }
}
