package com.example.myapplication.services

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import com.example.myapplication.utils.UserPreferences
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkMonitor @Inject constructor(
    private val context: Context,
    private val userPreferences: UserPreferences,
    private val webSocketService: WebSocketService,
    private val syncManager: SyncManager
) {
    private val TAG = "NetworkMonitor"
    private var monitoringJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    private var wasOnline = false

    @Volatile
    private var isMonitoring = false

    @Volatile
    private var reconnectInProgress = false

    init {
        startMonitoring()
    }

    private fun startMonitoring() {
        if (isMonitoring) return

        monitoringJob?.cancel()
        isMonitoring = true
        Log.d(TAG, "🟢 Network monitoring started")

        monitoringJob = scope.launch {
            var consecutiveErrors = 0

            while (isMonitoring && isActive) {
                try {
                    val isOnline = hasInternetConnection()

                    if (!wasOnline && isOnline) {
                        Log.d(TAG, "🌐 Network recovered!")
                        handleNetworkRecovery()
                        consecutiveErrors = 0
                    } else if (wasOnline && !isOnline) {
                        Log.d(TAG, "⚠️ Network lost!")
                        handleNetworkLost()
                    }

                    wasOnline = isOnline
                    delay(3000L)

                } catch (e: CancellationException) {
                    Log.d(TAG, "Monitoring loop cancelled")
                    break
                } catch (e: Exception) {
                    consecutiveErrors++
                    Log.e(TAG, "Error in monitoring loop (${consecutiveErrors}x): ${e.message}")

                    val delayMs = minOf(consecutiveErrors * 2000L, 30000L)
                    delay(delayMs)
                }
            }
        }
    }

    private suspend fun handleNetworkRecovery() {
        if (reconnectInProgress) {
            Log.d(TAG, "Reconnect already in progress, skipping")
            return
        }

        reconnectInProgress = true

        try {
            val userId = userPreferences.getUserId()
            if (userId == null) {
                Log.d(TAG, "User not logged in, skipping network recovery")
                return
            }

            // ✅ МНОГОКРАТНЫЕ ПОПЫТКИ RECONNECT
            var attempt = 0
            val maxAttempts = 3

            while (attempt < maxAttempts && !webSocketService.isConnected()) {
                attempt++
                Log.d(TAG, "🔌 Reconnect attempt $attempt/$maxAttempts...")

                webSocketService.disconnect()
                webSocketService.connect(userId)

                delay(1000L * attempt)

                if (webSocketService.isConnected()) {
                    Log.d(TAG, "✅ WebSocket reconnected successfully on attempt $attempt")
                    break
                } else {
                    Log.w(TAG, "⚠️ Reconnect attempt $attempt failed")
                }
            }

            if (!webSocketService.isConnected()) {
                Log.e(TAG, "❌ All reconnect attempts failed, will retry later")
            }

\            Log.d(TAG, "📡 Starting data sync...")
            syncManager.handleOnlineRecovery()

        } finally {
            reconnectInProgress = false
        }

        Log.d(TAG, "✅ Network recovery completed")
    }

    private fun handleNetworkLost() {
        Log.d(TAG, "🔴 Network lost")
    }

    fun hasInternetConnection(): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork ?: return false
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } else {
                @Suppress("DEPRECATION")
                val activeNetworkInfo = connectivityManager.activeNetworkInfo
                activeNetworkInfo != null && activeNetworkInfo.isConnected
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking internet connection: ${e.message}")
            false
        }
    }

    fun stopMonitoring() {
        Log.d(TAG, "Stopping network monitoring...")
        isMonitoring = false
        monitoringJob?.cancel()
        monitoringJob = null
        wasOnline = false
        reconnectInProgress = false
        Log.d(TAG, "🔴 Network monitoring stopped")
    }

    fun resumeMonitoring() {
        if (isMonitoring) return
        Log.d(TAG, "Resuming network monitoring...")
        wasOnline = false
        startMonitoring()
        Log.d(TAG, "🟢 Network monitoring resumed")
    }

    fun destroy() {
        stopMonitoring()
        scope.cancel()
    }

    fun isMonitoringActive(): Boolean = isMonitoring && monitoringJob?.isActive == true
}
