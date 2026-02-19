package com.example.myfirstapk.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.util.Log;

/**
 * NetworkChangeReceiver monitors network connectivity changes
 * Note: This receiver is kept for compatibility but SocketService
 * uses NetworkCallback which is the recommended modern approach.
 * 
 * For API 21+, NetworkCallback in SocketService provides better
 * real-time network monitoring.
 */
public class NetworkChangeReceiver extends BroadcastReceiver {
    private static final String TAG = "NetworkChangeReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction() != null && intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            
            if (cm != null) {
                boolean isConnected = false;
                
                // Use modern API for Android M+ (API 23+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Network network = cm.getActiveNetwork();
                    if (network != null) {
                        NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
                        isConnected = capabilities != null && 
                                     capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
                    }
                } else {
                    // Fallback for older versions
                    android.net.NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                    isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();
                }
                
                if (isConnected) {
                    Log.d(TAG, "📡 Network connected");
                } else {
                    Log.d(TAG, "📡 Network disconnected");
                }
            }
        }
    }
}
