package com.example.myfirstapk.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

/**
 * NetworkChangeReceiver monitors network connectivity changes
 * Can be used to trigger reconnection when network becomes available
 */
public class NetworkChangeReceiver extends BroadcastReceiver {
    private static final String TAG = "NetworkChangeReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction() != null && intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            
            if (cm != null) {
                NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();
                
                if (isConnected) {
                    Log.d(TAG, "📡 Network connected: " + activeNetwork.getTypeName());
                    // Network is available - service will auto-reconnect via NetworkCallback
                } else {
                    Log.d(TAG, "📡 Network disconnected");
                }
            }
        }
    }
}
