package com.orderpackager

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.rememberNavController
import com.orderpackager.navigation.AppNavGraph
import com.orderpackager.repository.AppRepository
import com.orderpackager.data.sync.CloudSyncManager
import com.orderpackager.ui.PackagerTheme
import com.orderpackager.ui.ThemeMode
import com.orderpackager.ui.loadThemeMode

class OrderPackagerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppRepository.getInstance(this)
        CloudSyncManager.getInstance(this).enqueue()
        // Retry queued catalog additions (and existing sync) when connectivity returns.
        getSystemService(android.net.ConnectivityManager::class.java)
            .registerDefaultNetworkCallback(object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {
                    CloudSyncManager.getInstance(this@OrderPackagerApp).enqueue()
                }
            })
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Экран не гаснет пока приложение открыто
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            val context = LocalContext.current
            var themeMode by remember { mutableStateOf(loadThemeMode(context)) }

            PackagerTheme(themeMode = themeMode) {
                val navController = rememberNavController()
                AppNavGraph(
                    navController = navController,
                    themeMode     = themeMode,
                    onThemeChange = { themeMode = it }
                )
            }
        }
    }
}
