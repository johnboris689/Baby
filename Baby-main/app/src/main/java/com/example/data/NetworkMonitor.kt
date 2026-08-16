package com.example.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class NetworkMonitor(context: Context) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    val isConnected: Flow<Boolean> = callbackFlow {
        if (connectivityManager == null) {
            trySend(false)
            close()
            return@callbackFlow
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d("NetworkMonitor", "Network is available")
                trySend(true)
            }

            override fun onLost(network: Network) {
                Log.d("NetworkMonitor", "Network is lost")
                trySend(false)
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                Log.d("NetworkMonitor", "Network capabilities changed: hasInternet=$hasInternet")
                trySend(hasInternet)
            }
        }

        // Set initial state
        val activeNetwork = connectivityManager.activeNetwork
        val initialCapabilities = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
        val isInitiallyConnected = initialCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        trySend(isInitiallyConnected)

        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, callback)
        } catch (e: Exception) {
            Log.e("NetworkMonitor", "Failed to register network callback: ${e.message}", e)
            trySend(true) // assume connected on failure of callback registration
        }

        awaitClose {
            try {
                connectivityManager.unregisterNetworkCallback(callback)
            } catch (e: Exception) {
                Log.e("NetworkMonitor", "Failed to unregister network callback: ${e.message}", e)
            }
        }
    }
}
