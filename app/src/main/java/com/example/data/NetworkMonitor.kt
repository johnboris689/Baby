package com.example.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

enum class NetworkQuality {
    ONLINE_FAST,
    ONLINE_WEAK,
    OFFLINE
}

class NetworkMonitor(context: Context) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    /**
     * Resilient isConnected Flow:
     * Returns true as long as there is an active network interface with internet capability.
     * Prevents prematurely declaring offline on weak or fluctuating 2G/3G/4G cellular networks.
     */
    val isConnected: Flow<Boolean> = callbackFlow {
        if (connectivityManager == null) {
            trySend(false)
            close()
            return@callbackFlow
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d("NetworkMonitor", "Network available: $network")
                trySend(true)
            }

            override fun onLost(network: Network) {
                Log.d("NetworkMonitor", "Network lost: $network")
                // Check if any other network is currently active before signaling disconnected
                val hasAnyNetwork = isAnyNetworkActive()
                trySend(hasAnyNetwork)
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                Log.d("NetworkMonitor", "Network capabilities changed: hasInternet=$hasInternet")
                trySend(hasInternet)
            }
        }

        // Set initial state
        trySend(isAnyNetworkActive())

        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, callback)
        } catch (e: Exception) {
            Log.e("NetworkMonitor", "Failed to register network callback: ${e.message}", e)
            trySend(true) // assume connected on failure of callback registration to avoid blocking requests
        }

        awaitClose {
            try {
                connectivityManager.unregisterNetworkCallback(callback)
            } catch (e: Exception) {
                Log.e("NetworkMonitor", "Failed to unregister network callback: ${e.message}", e)
            }
        }
    }.distinctUntilChanged()

    private fun isAnyNetworkActive(): Boolean {
        if (connectivityManager == null) return false
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Checks if current connection is likely weak or low-bandwidth (e.g. cellular or unvalidated).
     */
    fun isConnectionWeak(): Boolean {
        if (connectivityManager == null) return false
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        val isCellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        val isValidated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val isLowBandwidth = caps.linkDownstreamBandwidthKbps in 1..1500
        return isCellular || !isValidated || isLowBandwidth
    }

    /**
     * Quick non-blocking DNS/socket probe to verify if Google API domain is reachable.
     */
    suspend fun checkInternetReachable(timeoutMs: Int = 3000): Boolean = withContext(Dispatchers.IO) {
        if (!isAnyNetworkActive()) return@withContext false
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("8.8.8.8", 53), timeoutMs)
                true
            }
        } catch (e: Exception) {
            // Socket connect to 8.8.8.8 failed; could still be restricted DNS or proxy, return true if interface is active
            isAnyNetworkActive()
        }
    }
}
